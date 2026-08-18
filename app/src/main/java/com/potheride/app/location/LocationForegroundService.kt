package com.potheride.app.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.potheride.app.MainActivity
import com.potheride.app.R
import com.potheride.app.core.geo.LatLng
import com.potheride.app.data.repository.DataSourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Publishes the driver's live position while a trip is under way, surviving the app being
 * minimised.
 *
 * ### Why a foreground service rather than a background job
 * `WorkManager` and background location updates are both throttled or deferred by the
 * OS once the app leaves the foreground — by design, to save battery for apps that do not
 * need second-by-second accuracy. A ride-share driver's position is exactly the case that
 * throttling exists to prevent, which is precisely what `foregroundServiceType="location"`
 * is for: it tells the OS this app has an ongoing task the user asked for and can see
 * evidence of, via the persistent notification below.
 *
 * ### What this class does and does not decide
 * All *policy* — is this fix worth writing, has permission been lost, should the driver be
 * told GPS is off — lives in [LocationWritePolicy], [TrackingGate] and [LocationProvider].
 * This class is glue: it owns the Android lifecycle (the notification, the process
 * surviving minimisation, restart after being killed) and calls into that policy for every
 * decision. Keeping the split this way is what let all of the policy be unit-tested on the
 * JVM in the files beside this one.
 */
class LocationForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    private lateinit var locationProvider: LocationProvider
    private lateinit var sessionStore: TrackingSessionStore
    private val writePolicy = LocationWritePolicy()

    private var trackingJob: Job? = null
    private var session: TrackingSession? = null

    override fun onCreate() {
        super.onCreate()
        locationProvider = LocationProvider(applicationContext)
        sessionStore = SharedPreferencesTrackingSessionStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tripId = intent?.getStringExtra(EXTRA_TRIP_ID)
        val driverId = intent?.getStringExtra(EXTRA_DRIVER_ID)

        startForeground(NOTIFICATION_ID, buildNotification(idle = trackingJob == null))

        if (tripId != null && driverId != null) {
            // A genuinely new start request. Resuming after process death arrives here
            // with a null intent (see below) and must not overwrite a session already
            // recovered from disk.
            beginTracking(TrackingSession(tripId, driverId, startedAtMillis = System.currentTimeMillis()))
        } else if (trackingJob == null) {
            // The system restarted this service after it was killed. START_REDELIVER_INTENT
            // is supposed to hand the original intent back, but on some OEM skins it does
            // not — so recovery from the on-disk session is the path that actually works
            // in practice, not just the documented one.
            recoverFromDisk()
        }

        // Redeliver, not sticky: a location fix mid-write when the process dies must be
        // retried with its real intent, not restarted with a null one that has to guess.
        return START_REDELIVER_INTENT
    }

    private fun recoverFromDisk() {
        val stored = sessionStore.load()
        val environment = currentEnvironment()
        when (val decision = TrackingRecovery.decide(stored, environment, System.currentTimeMillis())) {
            is RecoveryDecision.Resume -> {
                writePolicy.restore(decision.seedState)
                beginTracking(decision.session, alreadyPersisted = true)
            }
            is RecoveryDecision.Discard -> {
                sessionStore.clear()
                stopSelf()
            }
        }
    }

    private fun beginTracking(newSession: TrackingSession, alreadyPersisted: Boolean = false) {
        session = newSession
        if (!alreadyPersisted) {
            writePolicy.reset()
            sessionStore.save(newSession)
        }
        trackingJob?.cancel()

        if (!TrackingGate.mayContinue(currentEnvironment())) {
            stopSelf()
            return
        }

        trackingJob = locationProvider.locationUpdates(intervalMillis = FIX_INTERVAL_MILLIS)
            .onEach { position -> onFix(position) }
            .catch { /* A single failed fix must not tear down the whole update stream. */ }
            .launchIn(serviceScope)
    }

    private suspend fun onFix(position: LatLng) {
        val current = session ?: return
        val fix = LocationFix(position = position, timestampMillis = System.currentTimeMillis())

        when (val decision = writePolicy.decide(fix)) {
            is LocationWriteDecision.Write -> {
                val repo = DataSourceProvider.get(applicationContext)
                try {
                    repo.recordLocation(current.tripId, decision.fix.position)
                    writePolicy.onWriteSucceeded(decision.fix)
                    val updated = current.copy(
                        lastWriteAtMillis = decision.fix.timestampMillis,
                        lastPosition = decision.fix.position
                    )
                    session = updated
                    sessionStore.save(updated)
                    updateNotification(idle = false)
                } catch (e: Exception) {
                    // Network down, Firestore rejecting the write, or the local store
                    // briefly unavailable — all recoverable, all handled by backoff.
                    writePolicy.onWriteFailed(decision.fix.timestampMillis)
                }
            }
            is LocationWriteDecision.Skip -> {
                // Nothing to do — the policy already recorded why in its own state.
            }
        }

        if (!TrackingGate.mayContinue(currentEnvironment())) {
            stopTracking()
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        session = null
        sessionStore.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------
    // Environment snapshot
    // ------------------------------------------------------------------

    private fun currentEnvironment(): LocationEnvironment {
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        val gpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

        val permission = when {
            hasFinePermission() -> LocationPermission.FINE_FOREGROUND
            hasCoarsePermission() -> LocationPermission.COARSE_ONLY
            else -> LocationPermission.DENIED
        }

        return LocationEnvironment(
            permission = permission,
            locationServicesEnabled = gpsEnabled,
            networkAvailable = true // Firestore's offline queue absorbs the rest; see SeatAccounting-adjacent docs.
        )
    }

    private fun hasFinePermission(): Boolean = ContextCompat.checkSelfPermission(
        this, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun hasCoarsePermission(): Boolean = ContextCompat.checkSelfPermission(
        this, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_notification_channel),
            NotificationManager.IMPORTANCE_LOW // Low: this is ongoing status, not an alert.
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(idle: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(
                getString(
                    if (idle) R.string.tracking_notification_body_starting
                    else R.string.tracking_notification_body_active
                )
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun updateNotification(idle: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(idle))
    }

    companion object {
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 4201
        private const val FIX_INTERVAL_MILLIS = 5_000L

        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_DRIVER_ID = "driver_id"

        /** Starts publishing [driverId]'s position for [tripId]. Idempotent. */
        fun start(context: Context, tripId: String, driverId: String) {
            val intent = Intent(context, LocationForegroundService::class.java)
                .putExtra(EXTRA_TRIP_ID, tripId)
                .putExtra(EXTRA_DRIVER_ID, driverId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationForegroundService::class.java))
        }
    }
}

/**
 * Persists the active [TrackingSession] to `SharedPreferences`.
 *
 * `SharedPreferences` rather than Room: this is one small record, read once at service
 * start and written on every successful fix, and a full database round trip for that is
 * needless weight in the hottest path this service has.
 */
class SharedPreferencesTrackingSessionStore(context: Context) : TrackingSessionStore {

    private val prefs = context.getSharedPreferences("tracking_session", Context.MODE_PRIVATE)

    override fun save(session: TrackingSession) {
        prefs.edit()
            .putString(KEY_TRIP_ID, session.tripId)
            .putString(KEY_DRIVER_ID, session.driverId)
            .putLong(KEY_STARTED_AT, session.startedAtMillis)
            .putLong(KEY_LAST_WRITE_AT, session.lastWriteAtMillis)
            .putString(KEY_LAST_LAT, session.lastPosition?.lat?.toString())
            .putString(KEY_LAST_LNG, session.lastPosition?.lng?.toString())
            .apply()
    }

    override fun load(): TrackingSession? {
        val tripId = prefs.getString(KEY_TRIP_ID, null) ?: return null
        val driverId = prefs.getString(KEY_DRIVER_ID, null) ?: return null
        val lat = prefs.getString(KEY_LAST_LAT, null)?.toDoubleOrNull()
        val lng = prefs.getString(KEY_LAST_LNG, null)?.toDoubleOrNull()
        return TrackingSession(
            tripId = tripId,
            driverId = driverId,
            startedAtMillis = prefs.getLong(KEY_STARTED_AT, 0L),
            lastWriteAtMillis = prefs.getLong(KEY_LAST_WRITE_AT, 0L),
            lastPosition = if (lat != null && lng != null) LatLng(lat, lng) else null
        )
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TRIP_ID = "trip_id"
        const val KEY_DRIVER_ID = "driver_id"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_LAST_WRITE_AT = "last_write_at"
        const val KEY_LAST_LAT = "last_lat"
        const val KEY_LAST_LNG = "last_lng"
    }
}
