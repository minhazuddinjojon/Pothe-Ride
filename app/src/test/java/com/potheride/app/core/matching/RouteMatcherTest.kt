package com.potheride.app.core.matching

import com.potheride.app.core.geo.GeoUtils
import com.potheride.app.core.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMatcherTest {

    // The Mirpur -> Gazipur corridor used throughout the app's demo data.
    private val mirpur = LatLng(23.8067, 90.3686)
    private val uttara = LatLng(23.8759, 90.3795)
    private val airport = LatLng(23.8433, 90.3978)
    private val tongi = LatLng(23.8909, 90.4023)
    private val gazipur = LatLng(23.9999, 90.4203)

    private val route = listOf(mirpur, uttara, airport, tongi, gazipur)

    private fun matched(pickup: LatLng, drop: LatLng, tolerance: Double = 1.5): RouteMatchResult {
        val outcome = RouteMatcher.match(route, pickup, drop, tolerance)
        assertTrue("expected a match but got $outcome", outcome is MatchOutcome.Matched)
        return (outcome as MatchOutcome.Matched).result
    }

    private fun rejection(pickup: LatLng, drop: LatLng, tolerance: Double = 1.5): RejectionReason {
        val outcome = RouteMatcher.match(route, pickup, drop, tolerance)
        assertTrue("expected a rejection but got $outcome", outcome is MatchOutcome.Rejected)
        return (outcome as MatchOutcome.Rejected).reason
    }

    @Test
    fun ridingTheEntireRouteGivesFullOverlap() {
        val result = matched(mirpur, gazipur)
        assertEquals(1.0f, result.overlapRatio, 0.01f)
        assertEquals(100, result.overlapPercent)
    }

    @Test
    fun ridingTheEntireRouteHasNoDetour() {
        val result = matched(mirpur, gazipur)
        assertEquals(0.0, result.detourKm, 0.05)
    }

    @Test
    fun partialRideGivesPartialOverlap() {
        val result = matched(mirpur, tongi)
        assertTrue("overlap should be under 100%", result.overlapRatio < 1.0f)
        assertTrue("overlap should be substantial", result.overlapRatio > 0.4f)
    }

    @Test
    fun sharedDistanceNeverExceedsTheRouteLength() {
        val result = matched(mirpur, gazipur)
        assertTrue(result.sharedDistanceKm <= result.routeLengthKm + 1e-6)
    }

    @Test
    fun travellingAgainstTheDriversDirectionIsRejected() {
        // The single most important rule: a driver heading north must not be matched
        // with a passenger heading south along the same road.
        assertEquals(RejectionReason.WRONG_DIRECTION, rejection(gazipur, mirpur))
    }

    @Test
    fun pickupFarFromTheRouteIsRejected() {
        val farWest = LatLng(23.8067, 89.9000)
        assertEquals(RejectionReason.PICKUP_OFF_ROUTE, rejection(farWest, tongi))
    }

    @Test
    fun dropFarFromTheRouteIsRejected() {
        val farEast = LatLng(23.8900, 91.5000)
        assertEquals(RejectionReason.DROP_OFF_ROUTE, rejection(mirpur, farEast))
    }

    @Test
    fun aRouteWithFewerThanTwoPointsCannotMatch() {
        val outcome = RouteMatcher.match(listOf(mirpur), mirpur, gazipur, 1.5)
        assertEquals(
            RejectionReason.ROUTE_TOO_SHORT,
            (outcome as MatchOutcome.Rejected).reason
        )
    }

    @Test
    fun aTrivriallyShortHopIsRejected() {
        val almostMirpur = GeoUtils.interpolate(mirpur, uttara, 0.005)
        assertEquals(RejectionReason.RIDE_TOO_SHORT, rejection(mirpur, almostMirpur))
    }

    @Test
    fun twoPointsOnTheSameLegAreOrderedCorrectly() {
        // Both points sit on the first leg. A matcher that compares only leg indices
        // would see them as equal and either reject or mis-order this ride.
        val early = GeoUtils.interpolate(mirpur, uttara, 0.1)
        val late = GeoUtils.interpolate(mirpur, uttara, 0.9)

        val forward = RouteMatcher.match(route, early, late, 1.5)
        assertTrue("same-leg forward ride should match", forward is MatchOutcome.Matched)

        val backward = RouteMatcher.match(route, late, early, 1.5)
        assertEquals(
            RejectionReason.WRONG_DIRECTION,
            (backward as MatchOutcome.Rejected).reason
        )
    }

    @Test
    fun aPointSlightlyOffTheRouteStillMatchesWithinTolerance() {
        // ~400 m east of the corridor, comfortably inside a 1.5 km tolerance.
        val slightlyOff = LatLng(tongi.lat, tongi.lng + 0.004)
        val result = matched(mirpur, slightlyOff)
        assertTrue(result.drop.offRouteKm > 0.0)
        assertTrue(result.drop.offRouteKm < 1.5)
    }

    @Test
    fun detourCountsTheRoundTripOffTheRoute() {
        val offRoute = LatLng(tongi.lat, tongi.lng + 0.004)
        val result = matched(mirpur, offRoute)
        // Detour is twice the perpendicular offset: out and back again.
        assertEquals(2 * result.drop.offRouteKm, result.detourKm, 0.01)
    }

    @Test
    fun aTighterToleranceRejectsWhatALooserOneAccepts() {
        val offRoute = LatLng(tongi.lat, tongi.lng + 0.010)
        assertTrue(RouteMatcher.match(route, mirpur, offRoute, 2.0) is MatchOutcome.Matched)
        assertTrue(RouteMatcher.match(route, mirpur, offRoute, 0.2) is MatchOutcome.Rejected)
    }

    @Test
    fun anchorDistancesIncreaseAlongTheRoute() {
        val cumulative = GeoUtils.cumulativeKm(route)
        val first = RouteMatcher.anchorOf(route, cumulative, mirpur)
        val middle = RouteMatcher.anchorOf(route, cumulative, airport)
        val last = RouteMatcher.anchorOf(route, cumulative, gazipur)
        assertTrue(first.distanceAlongRouteKm < middle.distanceAlongRouteKm)
        assertTrue(middle.distanceAlongRouteKm < last.distanceAlongRouteKm)
    }

    @Test
    fun sharedPathStartsAtPickupAndEndsAtDrop() {
        val result = matched(uttara, tongi)
        val path = RouteMatcher.sharedPath(route, result)
        assertTrue(path.first().approximatelyEqualsUttara())
        assertTrue(path.last().approximatelyEqualsTongi())
    }

    @Test
    fun sharedPathLengthMatchesTheReportedSharedDistance() {
        val result = matched(uttara, tongi)
        val path = RouteMatcher.sharedPath(route, result)
        assertEquals(result.sharedDistanceKm, GeoUtils.polylineLengthKm(path), 0.05)
    }

    @Test
    fun matchOrNullReturnsNullOnRejection() {
        assertEquals(null, RouteMatcher.matchOrNull(route, gazipur, mirpur, 1.5))
    }

    @Test
    fun toleranceIsClampedToASaneCeiling() {
        // Even an absurd tolerance must not turn the matcher into a proximity search
        // that ignores direction.
        val outcome = RouteMatcher.match(route, gazipur, mirpur, 9_999.0)
        assertTrue(outcome is MatchOutcome.Rejected)
    }

    private fun LatLng.approximatelyEqualsUttara() =
        GeoUtils.distanceKm(this, uttara) < 0.2

    private fun LatLng.approximatelyEqualsTongi() =
        GeoUtils.distanceKm(this, tongi) < 0.2
}
