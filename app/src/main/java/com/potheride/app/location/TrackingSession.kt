package com.potheride.app.location

import com.potheride.app.core.geo.LatLng

/**
 * Everything needed to pick tracking back up after the process is gone.
 *
 * A foreground service is *not* immortal. It survives the app being minimised, which is
 * the point, but the system will still kill the process under memory pressure, and some
 * manufacturer battery managers (Xiaomi, Oppo and Vivo are the notorious ones in this
 * market) will kill it far more eagerly than stock Android does. `START_REDELIVER_INTENT`
 * brings the service back with its original intent — but only if the service was killed,
 * not if the whole task was swiped away — so the trip identity has to be on disk, not
 * only in the intent.
 *
 * The last written position is persisted with it so the write policy comes back with its
 * memory intact; see [LocationWriteState].
 */
data class TrackingSession(
    val tripId: String,
    val driverId: String,
    val startedAtMillis: Long,
    val lastWriteAtMillis: Long = 0L,
    val lastPosition: LatLng? = null
)

/**
 * Persistence for the active session.
 *
 * An interface so the recovery logic can be exercised against an in-memory fake in plain
 * JVM tests. The real implementation is [SharedPreferencesTrackingSessionStore].
 */
interface TrackingSessionStore {
    fun save(session: TrackingSession)
    fun load(): TrackingSession?
    fun clear()
}

/** In-memory store. Used by tests, and as a safe default when preferences are unavailable. */
class InMemoryTrackingSessionStore(private var session: TrackingSession? = null) :
    TrackingSessionStore {
    override fun save(session: TrackingSession) {
        this.session = session
    }

    override fun load(): TrackingSession? = session

    override fun clear() {
        session = null
    }
}

/** Why a persisted session was not resumed. */
enum class DiscardReason {
    /** Nothing was persisted — the normal cold start. */
    NO_SESSION,

    /**
     * The session is older than the maximum plausible trip length. Almost always means
     * the driver finished the ride while the app was dead, or the phone was off overnight.
     * Resuming would republish a stale position as though the trip were live.
     */
    EXPIRED,

    /** Location permission was withdrawn while the app was not running. */
    PERMISSION_REVOKED
}

/** What to do with whatever was found on disk at service start. */
sealed interface RecoveryDecision {
    /**
     * Resume this trip. [seedState] pre-loads the write policy so the first fix after a
     * restart is judged against the last position the passenger actually saw, rather than
     * being written unconditionally as a `FIRST_FIX`.
     */
    data class Resume(
        val session: TrackingSession,
        val seedState: LocationWriteState
    ) : RecoveryDecision

    data class Discard(val reason: DiscardReason) : RecoveryDecision
}

/**
 * Decides whether a persisted session should be resumed. Pure, so the awkward cases —
 * a session from yesterday, a session whose permission has since been revoked — can be
 * tested without killing a real process.
 */
object TrackingRecovery {

    /**
     * Longest a session may sit unresumed. Twelve hours comfortably exceeds any single
     * intercity run out of Dhaka while still ruling out a session found the next morning.
     */
    const val MAX_SESSION_AGE_MILLIS: Long = 12L * 60L * 60L * 1000L

    fun decide(
        stored: TrackingSession?,
        environment: LocationEnvironment,
        nowMillis: Long,
        maxAgeMillis: Long = MAX_SESSION_AGE_MILLIS
    ): RecoveryDecision {
        if (stored == null) return RecoveryDecision.Discard(DiscardReason.NO_SESSION)

        // Age is measured from the last *write*, not from the start, so a long trip that
        // has been reporting all along is not thrown away at the twelve-hour mark. Falling
        // back to startedAt covers a session killed before its first successful write.
        val lastActivity = maxOf(stored.lastWriteAtMillis, stored.startedAtMillis)
        if (nowMillis - lastActivity > maxAgeMillis) {
            return RecoveryDecision.Discard(DiscardReason.EXPIRED)
        }
        if (!TrackingGate.mayContinue(environment)) {
            // Note: a location toggle switched off is *also* handled here. Resuming into a
            // permanent no-fix state would leave a persistent notification the driver
            // cannot dismiss and that never updates — the worst possible failure shape.
            return RecoveryDecision.Discard(DiscardReason.PERMISSION_REVOKED)
        }

        return RecoveryDecision.Resume(
            session = stored,
            seedState = LocationWriteState(
                lastWrittenPosition = stored.lastPosition,
                lastWriteAtMillis = stored.lastWriteAtMillis,
                lastFixAtMillis = stored.lastWriteAtMillis
            )
        )
    }
}
