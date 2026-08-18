package com.potheride.app.location

/**
 * What the user has actually granted, as opposed to what the manifest asks for.
 *
 * Android's permission model for location is not a single yes/no and treating it as one is
 * the single most common source of "tracking silently does nothing" bugs:
 *
 *  - **Denied** — no location at all.
 *  - **Coarse only** — from API 31 the runtime dialogue has a *Precise / Approximate*
 *    toggle, and a user who leaves it on Approximate grants `ACCESS_COARSE_LOCATION`
 *    while the request for `ACCESS_FINE_LOCATION` is *reported as granted at the manifest
 *    level but downgraded in practice*. Fixes then arrive with an accuracy of a kilometre
 *    or more, which is useless for a pin on a road but perfectly capable of looking like
 *    it is working.
 *  - **Fine, foreground only** — the normal case. This is enough for the whole feature,
 *    because a foreground service with `foregroundServiceType="location"` may keep
 *    receiving updates while the app is minimised. Background permission is *not*
 *    required for that, a point worth stating loudly since asking for it unnecessarily is
 *    a Play Store review rejection.
 *  - **Fine, with background** — only needed if tracking must survive the service being
 *    stopped, which this app does not do.
 */
enum class LocationPermission {
    DENIED,
    COARSE_ONLY,
    FINE_FOREGROUND,
    FINE_BACKGROUND
}

/**
 * Everything about the device that affects whether tracking can run, gathered into one
 * value so the decision itself can be a pure function.
 *
 * [oneTimeGrant] is the "Only this time" option. The grant is real while the app is in
 * use, but the system revokes it once the app leaves the foreground for a while — and,
 * crucially, **it revokes it without any callback**: the fused provider simply stops
 * delivering fixes. So the only way to notice is to keep re-checking, which is why this is
 * modelled explicitly rather than folded into [LocationPermission].
 */
data class LocationEnvironment(
    val permission: LocationPermission,
    val locationServicesEnabled: Boolean,
    /** Whether the *system* location toggle can be turned on from inside the app. */
    val settingsResolvable: Boolean = true,
    val oneTimeGrant: Boolean = false,
    /** False in aeroplane mode or with no usable data connection. */
    val networkAvailable: Boolean = true
)

/** A caveat worth telling the driver about, without necessarily blocking tracking. */
enum class TrackingWarning {
    /** Positions will be approximate to within roughly a kilometre. */
    APPROXIMATE_ONLY,

    /** Permission will evaporate when the app is backgrounded for long enough. */
    ONE_TIME_GRANT_WILL_EXPIRE,

    /** Fixes are being taken, but writes are queued locally until a network returns. */
    OFFLINE_QUEUEING
}

/** What the caller should do about the current [LocationEnvironment]. */
sealed interface TrackingReadiness {

    /**
     * Tracking can start. [warnings] is non-empty when it will run degraded — the UI is
     * expected to say so rather than present a confidently wrong pin.
     */
    data class Ready(val warnings: Set<TrackingWarning> = emptySet()) : TrackingReadiness

    /** Ask for the runtime permission. */
    data object NeedsPermission : TrackingReadiness

    /**
     * The system location toggle is off. When [resolvable] the caller should launch the
     * `SettingsClient` resolution dialogue — the in-app one-tap prompt — rather than
     * dumping the driver into the Settings app and hoping they come back.
     */
    data class NeedsLocationServices(val resolvable: Boolean) : TrackingReadiness
}

/**
 * The pure decision behind starting tracking.
 *
 * Kept separate from the service so the awkward combinations — coarse-only *and* location
 * services off, one-time grant *and* aeroplane mode — can be enumerated in tests instead
 * of being discovered in the field.
 */
object TrackingGate {

    fun evaluate(environment: LocationEnvironment): TrackingReadiness {
        if (environment.permission == LocationPermission.DENIED) {
            return TrackingReadiness.NeedsPermission
        }
        // Checked after permission on purpose: prompting to switch on GPS before the app
        // is even allowed to use it produces a dialogue the driver cannot make sense of.
        if (!environment.locationServicesEnabled) {
            return TrackingReadiness.NeedsLocationServices(environment.settingsResolvable)
        }

        val warnings = buildSet {
            if (environment.permission == LocationPermission.COARSE_ONLY) {
                add(TrackingWarning.APPROXIMATE_ONLY)
            }
            if (environment.oneTimeGrant) add(TrackingWarning.ONE_TIME_GRANT_WILL_EXPIRE)
            if (!environment.networkAvailable) add(TrackingWarning.OFFLINE_QUEUEING)
        }
        return TrackingReadiness.Ready(warnings)
    }

    /**
     * Whether the *foreground service* may keep running given the environment.
     *
     * Distinct from [evaluate] because the questions differ: starting asks "can we begin?",
     * whereas a running service asks "must we stop?". Losing the network mid-trip is not a
     * reason to stop — Firestore queues offline writes and flushes them on reconnect, so
     * the ride is still recorded. Losing permission is, because from that moment the
     * service is a persistent notification producing nothing, which is worse than no
     * service at all.
     */
    fun mayContinue(environment: LocationEnvironment): Boolean =
        environment.permission != LocationPermission.DENIED && environment.locationServicesEnabled
}
