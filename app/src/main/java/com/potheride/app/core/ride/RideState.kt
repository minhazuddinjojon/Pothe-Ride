package com.potheride.app.core.ride

/**
 * The booking lifecycle from the product brief. Modelled as an explicit state
 * machine rather than a loose enum + integer index, because the legal transitions
 * differ by actor: a driver may accept or decline, only a passenger may cancel
 * before pickup, and neither side may skip payment.
 */
enum class RideState {
    REQUESTED,
    ACCEPTED,
    DECLINED,
    DRIVER_ARRIVING,
    PICKED_UP,
    COMPLETED,
    PAID,
    CANCELLED;

    val isTerminal: Boolean get() = this == PAID || this == CANCELLED || this == DECLINED

    /** Only the happy path appears in the passenger-facing progress tracker. */
    val isOnHappyPath: Boolean get() = this in HAPPY_PATH

    companion object {
        val HAPPY_PATH = listOf(REQUESTED, ACCEPTED, DRIVER_ARRIVING, PICKED_UP, COMPLETED, PAID)
    }
}

enum class Actor { DRIVER, PASSENGER, SYSTEM }

data class TransitionError(val message: String)

object RideStateMachine {

    private val allowed: Map<RideState, Set<RideState>> = mapOf(
        RideState.REQUESTED to setOf(RideState.ACCEPTED, RideState.DECLINED, RideState.CANCELLED),
        RideState.ACCEPTED to setOf(RideState.DRIVER_ARRIVING, RideState.CANCELLED),
        RideState.DRIVER_ARRIVING to setOf(RideState.PICKED_UP, RideState.CANCELLED),
        RideState.PICKED_UP to setOf(RideState.COMPLETED),
        RideState.COMPLETED to setOf(RideState.PAID),
        RideState.PAID to emptySet(),
        RideState.DECLINED to emptySet(),
        RideState.CANCELLED to emptySet()
    )

    /** Which side of the marketplace is permitted to trigger each transition. */
    private val permittedActors: Map<Pair<RideState, RideState>, Set<Actor>> = mapOf(
        (RideState.REQUESTED to RideState.ACCEPTED) to setOf(Actor.DRIVER),
        (RideState.REQUESTED to RideState.DECLINED) to setOf(Actor.DRIVER),
        (RideState.REQUESTED to RideState.CANCELLED) to setOf(Actor.PASSENGER, Actor.SYSTEM),
        (RideState.ACCEPTED to RideState.DRIVER_ARRIVING) to setOf(Actor.DRIVER),
        (RideState.ACCEPTED to RideState.CANCELLED) to setOf(Actor.PASSENGER, Actor.DRIVER, Actor.SYSTEM),
        (RideState.DRIVER_ARRIVING to RideState.PICKED_UP) to setOf(Actor.DRIVER),
        (RideState.DRIVER_ARRIVING to RideState.CANCELLED) to setOf(Actor.PASSENGER, Actor.DRIVER, Actor.SYSTEM),
        (RideState.PICKED_UP to RideState.COMPLETED) to setOf(Actor.DRIVER),
        (RideState.COMPLETED to RideState.PAID) to setOf(Actor.PASSENGER, Actor.SYSTEM)
    )

    fun canTransition(from: RideState, to: RideState): Boolean =
        allowed[from]?.contains(to) == true

    fun nextStates(from: RideState): Set<RideState> = allowed[from].orEmpty()

    /**
     * The single forward step along the happy path, or null at the end of it.
     * Drives the "advance" affordance in the ride-status UI.
     */
    fun nextHappyPathState(from: RideState): RideState? {
        val index = RideState.HAPPY_PATH.indexOf(from)
        if (index < 0 || index >= RideState.HAPPY_PATH.lastIndex) return null
        return RideState.HAPPY_PATH[index + 1]
    }

    /**
     * Validates a transition. Returns null when it is legal, or the reason it isn't —
     * callers surface that string rather than silently ignoring an illegal tap.
     */
    fun validate(from: RideState, to: RideState, actor: Actor): TransitionError? {
        if (from == to) return TransitionError("The ride is already $to.")
        if (!canTransition(from, to)) {
            return TransitionError("A ride cannot go from $from to $to.")
        }
        val actors = permittedActors[from to to].orEmpty()
        if (actor !in actors) {
            return TransitionError("A ${actor.name.lowercase()} cannot move a ride from $from to $to.")
        }
        return null
    }

    /** Seats are released back to the trip when a ride ends without being taken. */
    fun releasesSeats(from: RideState, to: RideState): Boolean =
        to == RideState.CANCELLED && from in setOf(RideState.ACCEPTED, RideState.DRIVER_ARRIVING)

    /** Seats are consumed the moment a driver accepts, not when the passenger boards. */
    fun consumesSeats(from: RideState, to: RideState): Boolean =
        from == RideState.REQUESTED && to == RideState.ACCEPTED

    /** A passenger who cancels after acceptance owes a small fee in a real deployment. */
    fun incursCancellationFee(from: RideState, to: RideState, actor: Actor): Boolean =
        to == RideState.CANCELLED && actor == Actor.PASSENGER &&
            from in setOf(RideState.ACCEPTED, RideState.DRIVER_ARRIVING)
}
