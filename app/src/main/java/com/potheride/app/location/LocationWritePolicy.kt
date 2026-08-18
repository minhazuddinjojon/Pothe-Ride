package com.potheride.app.location

import com.potheride.app.core.geo.GeoUtils
import com.potheride.app.core.geo.LatLng
import kotlin.math.abs

/**
 * One raw position report from the device, stripped of every Android type so this whole
 * file compiles and runs on a plain JVM.
 *
 * [accuracyMetres] is the radius of the 68% confidence circle the platform reports. It is
 * nullable because a fix replayed from a mock provider, or one restored after process
 * death, may not carry it — and a missing accuracy must not be read as *perfect*
 * accuracy, which is what `0f` would mean.
 */
data class LocationFix(
    val position: LatLng,
    val timestampMillis: Long,
    val accuracyMetres: Double? = null
)

/** Why a fix was written through to the data source. */
enum class WriteReason {
    /** Nothing has been written for this trip yet; the passenger needs a first pin. */
    FIRST_FIX,

    /** The driver has moved far enough that the map would visibly be wrong without it. */
    MOVED,

    /**
     * The driver has not moved, but the last write is old enough that a passenger
     * watching would start to wonder whether tracking had died. See
     * [LocationWritePolicyConfig.heartbeatMillis].
     */
    HEARTBEAT,

    /** A previous write failed and the backoff window has now elapsed. */
    RETRY
}

/** Why a fix was dropped rather than written. */
enum class SkipReason {
    /** Fixes arrived faster than the configured floor — usually a burst after a tunnel. */
    TOO_SOON,

    /** Movement since the last write is below the meaningful-movement threshold. */
    NOT_MOVED,

    /** The reported accuracy is so poor that writing it would move the pin at random. */
    INACCURATE,

    /** The jump implies a speed no road vehicle reaches; almost always a bad fix. */
    IMPLAUSIBLE_JUMP,

    /** The coordinate is outside Bangladesh, so it cannot be a real trip position. */
    OUTSIDE_SERVICE_AREA,

    /** Writes are failing; we are waiting out the backoff instead of hammering. */
    BACKING_OFF,

    /** The clock went backwards (NTP correction, or a replayed cached fix). */
    OUT_OF_ORDER
}

/** The outcome of asking the policy what to do with a fix. */
sealed interface LocationWriteDecision {
    data class Write(val fix: LocationFix, val reason: WriteReason) : LocationWriteDecision
    data class Skip(val reason: SkipReason) : LocationWriteDecision
}

/**
 * The tuning knobs, all in one place so the trade-off is visible and testable.
 *
 * **Why any of this exists.** The fused provider is asked for a fix every 5 seconds
 * because that is what a smooth-looking map needs. Writing every one of those fixes to
 * Firestore is a different question entirely, and the naive answer is expensive in two
 * separate currencies:
 *
 *  - *Firestore quota.* One write per driver per 5 s is 720 writes an hour. The Spark
 *    (free) plan allows 20,000 document writes a day, so **28 driver-hours exhausts the
 *    entire project's daily budget** — about ten drivers doing a single commute. Every
 *    passenger snapshot listener then also bills a read per write, on top.
 *  - *Battery and data.* Each write is a round trip on the mobile radio. Waking the radio
 *    every 5 s for a vehicle stationary at a Dhaka traffic signal — where a driver can sit
 *    for three or four minutes — costs real battery and buys the passenger nothing,
 *    because the pin does not move.
 *
 * So the rule is: write when the position has *changed enough to matter*, plus a slow
 * heartbeat so a stationary driver still looks alive rather than crashed.
 */
data class LocationWritePolicyConfig(
    /**
     * Movement below this is not written. 25 m is roughly the width of a large junction
     * and comfortably above the noise floor of a good urban GPS fix, so a parked vehicle
     * does not "wander" its way into a stream of writes.
     */
    val minMovementMetres: Double = 25.0,

    /**
     * Hard floor between writes, whatever the movement. Guards against a provider that
     * delivers a burst of buffered fixes at once — which is exactly what happens when the
     * device comes out of a tunnel or a flyover underpass.
     */
    val minWriteIntervalMillis: Long = 4_000L,

    /**
     * A stationary driver is still written through this often. Without it the passenger's
     * "last updated 4 minutes ago" reads as a dead app rather than as a bus in traffic.
     * One write a minute per stationary driver is affordable; one every five seconds is not.
     */
    val heartbeatMillis: Long = 60_000L,

    /**
     * Fixes worse than this are dropped. Coarse (network-only) permission typically
     * reports 500–2000 m, which would jitter the pin across half the city; see
     * [LocationPermissionState] for how coarse-only access is handled at a higher level.
     */
    val maxAccuracyMetres: Double = 150.0,

    /**
     * Implausibility threshold, in metres per second. 55 m/s is just under 200 km/h — far
     * above anything on a Bangladeshi road, so anything faster is a bad fix, not a car.
     * Dropping these matters because a single wild fix would otherwise both mislead the
     * passenger and reset the movement baseline, causing a second spurious write on the
     * way back.
     */
    val maxPlausibleSpeedMetresPerSecond: Double = 55.0,

    /** First backoff step after a failed write; doubles up to [maxBackoffMillis]. */
    val initialBackoffMillis: Long = 10_000L,

    /** Ceiling on the backoff. Reached after roughly five consecutive failures. */
    val maxBackoffMillis: Long = 300_000L
) {
    init {
        require(minMovementMetres >= 0.0) { "minMovementMetres must not be negative" }
        require(heartbeatMillis >= minWriteIntervalMillis) {
            "heartbeat must not be shorter than the write interval floor"
        }
        require(initialBackoffMillis in 1..maxBackoffMillis) { "backoff window is inverted" }
    }
}

/**
 * A snapshot of the policy's memory, small enough to persist.
 *
 * This is the piece that survives process death: Android will kill and later restart a
 * foreground service, and a policy that came back with an empty memory would immediately
 * write a `FIRST_FIX` for a driver who has not moved an inch. Persist this alongside the
 * trip id (see [TrackingSessionStore]) and hand it back via [LocationWritePolicy.restore].
 */
data class LocationWriteState(
    val lastWrittenPosition: LatLng? = null,
    val lastWriteAtMillis: Long = 0L,
    val lastFixAtMillis: Long = 0L,
    val consecutiveFailures: Int = 0,
    val backoffUntilMillis: Long = 0L
)

/**
 * Decides, for each incoming fix, whether it is worth a write.
 *
 * Pure in the sense that matters: no Android types, no clock of its own, no I/O. Every
 * input is an argument, so the entire decision table is unit-testable without a device —
 * which is the only realistic way to test it, because reproducing "driver stationary at a
 * signal for four minutes, then a bad fix, then a network outage" on hardware is not
 * something anyone will do twice.
 *
 * Not thread-safe by design. The service confines it to a single coroutine; adding a lock
 * here would only hide a caller that had stopped doing that.
 */
class LocationWritePolicy(
    private val config: LocationWritePolicyConfig = LocationWritePolicyConfig()
) {

    private var state = LocationWriteState()

    /** The current memory, for persisting ahead of a possible process death. */
    fun snapshot(): LocationWriteState = state

    /** Restores a persisted memory after a restart. */
    fun restore(restored: LocationWriteState) {
        state = restored
    }

    /** Forgets everything. Called when a trip ends, so the next trip starts clean. */
    fun reset() {
        state = LocationWriteState()
    }

    /**
     * Should [fix] be written?
     *
     * The order of the checks is deliberate and load-bearing: cheap sanity checks (service
     * area, clock, accuracy) come before anything that consults the last write, so a
     * garbage fix can never become the baseline that later movement is measured against.
     */
    fun decide(fix: LocationFix): LocationWriteDecision {
        if (!fix.position.isInsideBangladesh()) {
            return LocationWriteDecision.Skip(SkipReason.OUTSIDE_SERVICE_AREA)
        }

        // A fix older than one we have already seen is either a replayed cached fix or an
        // NTP correction landing mid-trip. Either way, acting on it would drag the pin
        // backwards along the route.
        if (state.lastFixAtMillis > 0L && fix.timestampMillis < state.lastFixAtMillis) {
            return LocationWriteDecision.Skip(SkipReason.OUT_OF_ORDER)
        }

        val accuracy = fix.accuracyMetres
        if (accuracy != null && accuracy > config.maxAccuracyMetres) {
            return LocationWriteDecision.Skip(SkipReason.INACCURATE)
        }

        val last = state.lastWrittenPosition
        val metresMoved = last?.let { GeoUtils.distanceKm(it, fix.position) * 1000.0 }

        if (last != null && metresMoved != null) {
            val elapsedSeconds = abs(fix.timestampMillis - state.lastWriteAtMillis) / 1000.0
            if (elapsedSeconds > 0.0 &&
                metresMoved / elapsedSeconds > config.maxPlausibleSpeedMetresPerSecond
            ) {
                // Note this does *not* update lastFixAtMillis below — a rejected fix must
                // leave no trace, or the next good fix would be measured from the glitch.
                return LocationWriteDecision.Skip(SkipReason.IMPLAUSIBLE_JUMP)
            }
        }

        // Backoff is checked after the sanity gates so that a bad fix during an outage is
        // still reported as bad rather than masked as BACKING_OFF, which would make the
        // logs lie about why nothing is being written.
        if (state.backoffUntilMillis > 0L && fix.timestampMillis < state.backoffUntilMillis) {
            state = state.copy(lastFixAtMillis = fix.timestampMillis)
            return LocationWriteDecision.Skip(SkipReason.BACKING_OFF)
        }

        state = state.copy(lastFixAtMillis = fix.timestampMillis)

        if (last == null) {
            return LocationWriteDecision.Write(fix, WriteReason.FIRST_FIX)
        }

        val sinceWrite = fix.timestampMillis - state.lastWriteAtMillis
        val recovering = state.consecutiveFailures > 0

        if (sinceWrite < config.minWriteIntervalMillis) {
            return LocationWriteDecision.Skip(SkipReason.TOO_SOON)
        }
        if (recovering) {
            // The backoff window has passed (checked above), so this is the retry.
            return LocationWriteDecision.Write(fix, WriteReason.RETRY)
        }
        if ((metresMoved ?: 0.0) >= config.minMovementMetres) {
            return LocationWriteDecision.Write(fix, WriteReason.MOVED)
        }
        if (sinceWrite >= config.heartbeatMillis) {
            return LocationWriteDecision.Write(fix, WriteReason.HEARTBEAT)
        }
        return LocationWriteDecision.Skip(SkipReason.NOT_MOVED)
    }

    /** Records that the write actually reached the data source. */
    fun onWriteSucceeded(fix: LocationFix) {
        state = state.copy(
            lastWrittenPosition = fix.position,
            lastWriteAtMillis = fix.timestampMillis,
            lastFixAtMillis = maxOf(state.lastFixAtMillis, fix.timestampMillis),
            consecutiveFailures = 0,
            backoffUntilMillis = 0L
        )
    }

    /**
     * Records that the write failed — typically aeroplane mode, no data, or Firestore
     * refusing the write.
     *
     * The baseline position is deliberately *not* advanced: the passenger still has the
     * older position, so the next successful write must be measured from what they can
     * actually see, not from a position that never left the handset.
     *
     * Backoff doubles from [LocationWritePolicyConfig.initialBackoffMillis]. Without it, a
     * device in aeroplane mode would queue a failing write every 5 s for the whole
     * journey, which is the worst of both worlds — no data for the passenger and a flat
     * battery for the driver.
     */
    fun onWriteFailed(atMillis: Long) {
        val failures = state.consecutiveFailures + 1
        var backoff = config.initialBackoffMillis
        repeat(failures - 1) { backoff = (backoff * 2).coerceAtMost(config.maxBackoffMillis) }
        state = state.copy(
            consecutiveFailures = failures,
            backoffUntilMillis = atMillis + backoff.coerceAtMost(config.maxBackoffMillis)
        )
    }
}
