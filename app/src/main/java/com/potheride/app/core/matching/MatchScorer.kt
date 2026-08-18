package com.potheride.app.core.matching

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns a geometric match into a single 0–100 number for ranking.
 *
 * [RouteMatcher] answers *whether* a passenger can share a driver's route. It cannot
 * answer *which of five valid matches to show first*, and that is a different question:
 * a ride with perfect overlap that leaves in three hours is worse, to a passenger
 * standing at the roadside, than a slightly worse-fitting ride leaving now.
 *
 * The four factors below are the ones the brief names. They are weighted rather than
 * multiplied so that one weak factor degrades the score instead of annihilating it —
 * a 500 m walk to the pickup should cost a match some rank, not remove it from the list.
 */
object MatchScorer {

    /**
     * Weights, summing to 1.0.
     *
     * Overlap leads because it is the only factor that is good for *both* parties: it
     * is simultaneously the passenger's discount and the driver's smallest detour.
     * Departure time is weighted second because it is the factor a passenger feels
     * most immediately, and the one they cannot compromise on.
     */
    const val WEIGHT_OVERLAP = 0.40
    const val WEIGHT_DEPARTURE = 0.25
    const val WEIGHT_PICKUP_PROXIMITY = 0.20
    const val WEIGHT_DESTINATION_PROXIMITY = 0.15

    /**
     * Distance at which a proximity factor reaches zero.
     *
     * 1.5 km is roughly a 20-minute walk in Dhaka once footpaths, crossings and traffic
     * are accounted for. Beyond that the passenger is not really walking to this ride.
     */
    const val PROXIMITY_ZERO_KM = 1.5

    /**
     * Wait at which the departure factor reaches zero.
     *
     * 45 minutes: past that, a Dhaka commuter books something else. Departures in the
     * *past* are scored as zero rather than as negative — the ride has left, and how
     * long ago is not information that should reorder the remaining candidates.
     */
    const val DEPARTURE_ZERO_MINUTES = 45.0

    /** The ideal wait. Not zero: a ride leaving this instant cannot be caught. */
    const val IDEAL_WAIT_MINUTES = 5.0

    /**
     * Scores a candidate.
     *
     * @param result the geometry from [RouteMatcher]
     * @param departureTime when the driver leaves, epoch millis
     * @param now the reference instant, epoch millis
     */
    fun score(
        result: RouteMatchResult,
        departureTime: Long,
        now: Long
    ): MatchScore {
        val overlap = result.overlapRatio.toDouble().coerceIn(0.0, 1.0)
        val pickupProximity = proximityFactor(result.pickup.offRouteKm)
        val dropProximity = proximityFactor(result.drop.offRouteKm)
        val departure = departureFactor(departureTime, now)

        val total = overlap * WEIGHT_OVERLAP +
            departure * WEIGHT_DEPARTURE +
            pickupProximity * WEIGHT_PICKUP_PROXIMITY +
            dropProximity * WEIGHT_DESTINATION_PROXIMITY

        return MatchScore(
            value = (total * 100).roundToInt().coerceIn(0, 100),
            overlapFactor = overlap,
            departureFactor = departure,
            pickupProximityFactor = pickupProximity,
            destinationProximityFactor = dropProximity
        )
    }

    /**
     * 1.0 at the roadside, falling linearly to 0.0 at [PROXIMITY_ZERO_KM].
     *
     * Linear rather than exponential on purpose: the difference between 100 m and 300 m
     * genuinely matters to someone carrying shopping, and an exponential curve flattens
     * exactly that range into indistinguishable near-1.0 scores.
     */
    fun proximityFactor(offRouteKm: Double): Double =
        (1.0 - offRouteKm.coerceAtLeast(0.0) / PROXIMITY_ZERO_KM).coerceIn(0.0, 1.0)

    /**
     * 1.0 for a ride leaving in about [IDEAL_WAIT_MINUTES], falling away in both
     * directions, and 0.0 for anything already departed.
     */
    fun departureFactor(departureTime: Long, now: Long): Double {
        val waitMinutes = (departureTime - now) / 60_000.0
        if (waitMinutes < 0) return 0.0
        val deviation = abs(waitMinutes - IDEAL_WAIT_MINUTES)
        return (1.0 - deviation / DEPARTURE_ZERO_MINUTES).coerceIn(0.0, 1.0)
    }
}

/**
 * A ranked score with its parts kept.
 *
 * The breakdown is retained rather than discarded so the UI can explain a ranking —
 * "leaves soonest", "shortest walk" — instead of showing an unexplained number, and so
 * a surprising order can be diagnosed without re-running the search.
 */
data class MatchScore(
    val value: Int,
    val overlapFactor: Double,
    val departureFactor: Double,
    val pickupProximityFactor: Double,
    val destinationProximityFactor: Double
) : Comparable<MatchScore> {

    override fun compareTo(other: MatchScore): Int = value.compareTo(other.value)

    /** The factor that most held this match back, for a one-line explanation. */
    val weakestFactor: ScoreFactor
        get() = listOf(
            ScoreFactor.OVERLAP to overlapFactor,
            ScoreFactor.DEPARTURE to departureFactor,
            ScoreFactor.PICKUP_PROXIMITY to pickupProximityFactor,
            ScoreFactor.DESTINATION_PROXIMITY to destinationProximityFactor
        ).minBy { it.second }.first
}

enum class ScoreFactor { OVERLAP, DEPARTURE, PICKUP_PROXIMITY, DESTINATION_PROXIMITY }
