package com.potheride.app.core.matching

import com.potheride.app.core.geo.GeoUtils
import com.potheride.app.core.geo.LatLng

/**
 * Where a point sits relative to a driver's published route.
 *
 * [legIndex] is the segment (i -> i+1) the point projects onto, and
 * [fractionAlongLeg] is how far along that segment the projection lands. Together
 * they give a strict ordering along the route, which a leg index alone cannot:
 * a pickup and a drop-off that both fall on the same long leg still need to be
 * ordered correctly.
 */
data class RouteAnchor(
    val legIndex: Int,
    val fractionAlongLeg: Double,
    val offRouteKm: Double,
    val distanceAlongRouteKm: Double
) : Comparable<RouteAnchor> {
    override fun compareTo(other: RouteAnchor): Int =
        distanceAlongRouteKm.compareTo(other.distanceAlongRouteKm)
}

sealed interface MatchOutcome {
    data class Matched(val result: RouteMatchResult) : MatchOutcome
    data class Rejected(val reason: RejectionReason) : MatchOutcome
}

enum class RejectionReason {
    ROUTE_TOO_SHORT,
    PICKUP_OFF_ROUTE,
    DROP_OFF_ROUTE,
    WRONG_DIRECTION,
    DETOUR_EXCEEDS_DRIVER_LIMIT,
    RIDE_TOO_SHORT
}

data class RouteMatchResult(
    val pickup: RouteAnchor,
    val drop: RouteAnchor,
    /** Fraction (0..1) of the driver's own route the passenger actually rides. */
    val overlapRatio: Float,
    /** Kilometres the passenger travels along the route. */
    val sharedDistanceKm: Double,
    /** Total length of the driver's published route. */
    val routeLengthKm: Double,
    /** Extra kilometres the driver drives to collect and drop this passenger. */
    val detourKm: Double
) {
    val overlapPercent: Int get() = Math.round(overlapRatio * 100f)
}

/**
 * The route-based matching engine. A passenger matches a driver only when **both**
 * their pickup and drop-off sit within the driver's tolerance of the published path,
 * *and* the pickup comes before the drop-off in the direction the driver is actually
 * travelling. That directional check is what separates this from a proximity search:
 * a driver going Mirpur -> Gazipur should never be matched to a passenger going
 * Gazipur -> Mirpur, even though both points lie on the same road.
 */
object RouteMatcher {

    /** Below this, a "ride" is a walk; matching it wastes both parties' time. */
    const val MIN_SHARED_DISTANCE_KM = 0.5

    /** Hard ceiling on tolerance regardless of what a driver types in. */
    const val MAX_TOLERANCE_KM = 10.0

    fun match(
        route: List<LatLng>,
        pickup: LatLng,
        drop: LatLng,
        maxOffRouteKm: Double
    ): MatchOutcome {
        if (route.size < 2) return MatchOutcome.Rejected(RejectionReason.ROUTE_TOO_SHORT)

        val tolerance = maxOffRouteKm.coerceIn(0.1, MAX_TOLERANCE_KM)
        val cumulative = GeoUtils.cumulativeKm(route)
        val routeLengthKm = cumulative.last()
        if (routeLengthKm <= 0.0) return MatchOutcome.Rejected(RejectionReason.ROUTE_TOO_SHORT)

        val pickupAnchor = anchorOf(route, cumulative, pickup)
        if (pickupAnchor.offRouteKm > tolerance) {
            return MatchOutcome.Rejected(RejectionReason.PICKUP_OFF_ROUTE)
        }
        val dropAnchor = anchorOf(route, cumulative, drop)
        if (dropAnchor.offRouteKm > tolerance) {
            return MatchOutcome.Rejected(RejectionReason.DROP_OFF_ROUTE)
        }

        // Direction check. Uses distance-along-route, not leg index, so two points on
        // the same leg are still ordered correctly.
        if (dropAnchor.distanceAlongRouteKm <= pickupAnchor.distanceAlongRouteKm) {
            return MatchOutcome.Rejected(RejectionReason.WRONG_DIRECTION)
        }

        val sharedKm = dropAnchor.distanceAlongRouteKm - pickupAnchor.distanceAlongRouteKm
        if (sharedKm < MIN_SHARED_DISTANCE_KM) {
            return MatchOutcome.Rejected(RejectionReason.RIDE_TOO_SHORT)
        }

        // The detour is the *round trip* the driver makes off their own path to reach
        // each point and come back to it — not the one-way offset. A pickup 400 m off
        // the route costs the driver roughly 800 m of extra driving.
        val detourKm = 2 * (pickupAnchor.offRouteKm + dropAnchor.offRouteKm)
        if (detourKm > tolerance * 2) {
            return MatchOutcome.Rejected(RejectionReason.DETOUR_EXCEEDS_DRIVER_LIMIT)
        }

        val overlap = (sharedKm / routeLengthKm).coerceIn(0.0, 1.0).toFloat()

        return MatchOutcome.Matched(
            RouteMatchResult(
                pickup = pickupAnchor,
                drop = dropAnchor,
                overlapRatio = overlap,
                sharedDistanceKm = sharedKm,
                routeLengthKm = routeLengthKm,
                detourKm = detourKm
            )
        )
    }

    /** Convenience wrapper for callers that only care whether it matched. */
    fun matchOrNull(
        route: List<LatLng>,
        pickup: LatLng,
        drop: LatLng,
        maxOffRouteKm: Double
    ): RouteMatchResult? = (match(route, pickup, drop, maxOffRouteKm) as? MatchOutcome.Matched)?.result

    /**
     * Finds the closest point on the whole polyline to [point], expressed as a
     * [RouteAnchor]. Every leg is tested; the winner is the one with the smallest
     * perpendicular offset.
     */
    fun anchorOf(route: List<LatLng>, cumulative: DoubleArray, point: LatLng): RouteAnchor {
        var bestLeg = 0
        var bestFraction = 0.0
        var bestDistance = Double.MAX_VALUE

        for (i in 0 until route.size - 1) {
            val projection = GeoUtils.projectionOnSegment(point, route[i], route[i + 1])
            if (projection.distanceKm < bestDistance) {
                bestDistance = projection.distanceKm
                bestLeg = i
                bestFraction = projection.fractionAlong
            }
        }

        val legLength = cumulative[bestLeg + 1] - cumulative[bestLeg]
        return RouteAnchor(
            legIndex = bestLeg,
            fractionAlongLeg = bestFraction,
            offRouteKm = bestDistance,
            distanceAlongRouteKm = cumulative[bestLeg] + legLength * bestFraction
        )
    }

    /**
     * The slice of the driver's polyline the passenger actually rides, with the exact
     * boarding and alighting points spliced in at the ends. This is what the route
     * preview highlights, so the highlighted thread ends where the passenger really
     * gets on and off rather than at the nearest waypoint.
     */
    fun sharedPath(route: List<LatLng>, result: RouteMatchResult): List<LatLng> {
        val start = GeoUtils.interpolate(
            route[result.pickup.legIndex], route[result.pickup.legIndex + 1], result.pickup.fractionAlongLeg
        )
        val end = GeoUtils.interpolate(
            route[result.drop.legIndex], route[result.drop.legIndex + 1], result.drop.fractionAlongLeg
        )
        val middle = ((result.pickup.legIndex + 1)..result.drop.legIndex).map { route[it] }
        return listOf(start) + middle + listOf(end)
    }
}
