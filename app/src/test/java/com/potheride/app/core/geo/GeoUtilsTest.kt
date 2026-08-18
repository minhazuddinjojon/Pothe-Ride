package com.potheride.app.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    private val mirpur10 = LatLng(23.8067, 90.3686)
    private val uttara = LatLng(23.8759, 90.3795)
    private val gazipur = LatLng(23.9999, 90.4203)

    @Test
    fun distanceBetweenAPointAndItselfIsZero() {
        assertEquals(0.0, GeoUtils.distanceKm(mirpur10, mirpur10), 1e-9)
    }

    @Test
    fun haversineDistanceMatchesAKnownDhakaReferenceSpan() {
        // Mirpur-10 to Uttara is roughly 7.8 km as the crow flies.
        val d = GeoUtils.distanceKm(mirpur10, uttara)
        assertTrue("expected ~7.8 km but got $d", d in 7.0..8.6)
    }

    @Test
    fun distanceIsSymmetric() {
        assertEquals(
            GeoUtils.distanceKm(mirpur10, gazipur),
            GeoUtils.distanceKm(gazipur, mirpur10),
            1e-9
        )
    }

    @Test
    fun oneDegreeOfLatitudeIsAboutOneHundredElevenKilometres() {
        val d = GeoUtils.distanceKm(LatLng(23.0, 90.0), LatLng(24.0, 90.0))
        assertEquals(111.2, d, 1.0)
    }

    @Test
    fun aPointOnASegmentHasZeroOffset() {
        val midpoint = GeoUtils.interpolate(mirpur10, gazipur, 0.5)
        val d = GeoUtils.distanceToSegmentKm(midpoint, mirpur10, gazipur)
        assertTrue("midpoint should sit on the segment, offset was $d", d < 0.01)
    }

    @Test
    fun projectionReportsHowFarAlongASegmentAPointFalls() {
        val quarter = GeoUtils.interpolate(mirpur10, gazipur, 0.25)
        val projection = GeoUtils.projectionOnSegment(quarter, mirpur10, gazipur)
        assertEquals(0.25, projection.fractionAlong, 0.02)
    }

    @Test
    fun projectionClampsToTheSegmentEndsRatherThanExtrapolating() {
        // A point well past the far end must clamp to fraction 1.0, otherwise a
        // passenger beyond the destination would look like they were on the route.
        val beyond = LatLng(24.5, 90.5)
        val projection = GeoUtils.projectionOnSegment(beyond, mirpur10, gazipur)
        assertEquals(1.0, projection.fractionAlong, 1e-9)
    }

    @Test
    fun zeroLengthSegmentDoesNotDivideByZero() {
        val projection = GeoUtils.projectionOnSegment(uttara, mirpur10, mirpur10)
        assertEquals(0.0, projection.fractionAlong, 1e-9)
        assertTrue(projection.distanceKm > 0.0)
    }

    @Test
    fun polylineLengthIsTheSumOfItsLegs() {
        val route = listOf(mirpur10, uttara, gazipur)
        val expected = GeoUtils.distanceKm(mirpur10, uttara) + GeoUtils.distanceKm(uttara, gazipur)
        assertEquals(expected, GeoUtils.polylineLengthKm(route), 1e-9)
    }

    @Test
    fun polylineLengthOfASinglePointIsZero() {
        assertEquals(0.0, GeoUtils.polylineLengthKm(listOf(mirpur10)), 1e-9)
        assertEquals(0.0, GeoUtils.polylineLengthKm(emptyList()), 1e-9)
    }

    @Test
    fun cumulativeDistancesIncreaseMonotonicallyFromZero() {
        val cum = GeoUtils.cumulativeKm(listOf(mirpur10, uttara, gazipur))
        assertEquals(0.0, cum[0], 1e-9)
        assertTrue(cum[1] > cum[0])
        assertTrue(cum[2] > cum[1])
    }

    @Test
    fun pointAtDistanceZeroIsTheRouteStart() {
        val route = listOf(mirpur10, uttara, gazipur)
        val p = GeoUtils.pointAtDistance(route, 0.0)
        assertTrue(p.position.approximatelyEquals(mirpur10))
    }

    @Test
    fun pointAtDistanceBeyondTheRouteClampsToTheDestination() {
        val route = listOf(mirpur10, uttara, gazipur)
        val p = GeoUtils.pointAtDistance(route, 9_999.0)
        assertTrue(p.position.approximatelyEquals(gazipur, 1e-4))
    }

    @Test
    fun pointAtHalfTheRouteLengthLandsNearTheMiddle() {
        val route = listOf(mirpur10, gazipur)
        val half = GeoUtils.polylineLengthKm(route) / 2
        val p = GeoUtils.pointAtDistance(route, half)
        val expected = GeoUtils.interpolate(mirpur10, gazipur, 0.5)
        assertTrue(p.position.approximatelyEquals(expected, 1e-3))
    }

    @Test
    fun boundsContainEveryInputPoint() {
        val bounds = GeoUtils.boundsOf(listOf(mirpur10, uttara, gazipur))
        assertTrue(bounds.minLat <= mirpur10.lat)
        assertTrue(bounds.maxLat >= gazipur.lat)
        assertTrue(bounds.minLng <= mirpur10.lng)
        assertTrue(bounds.maxLng >= gazipur.lng)
    }

    @Test
    fun unitSquarePutsNorthAtTheTop() {
        val bounds = GeoUtils.boundsOf(listOf(mirpur10, gazipur))
        val southY = bounds.toUnitSquare(mirpur10).second
        val northY = bounds.toUnitSquare(gazipur).second
        assertTrue("north should have a smaller y than south", northY < southY)
    }

    @Test
    fun degenerateBoundsCollapseToTheCentreInsteadOfDividingByZero() {
        val bounds = GeoUtils.boundsOf(listOf(mirpur10, mirpur10))
        val unit = bounds.toUnitSquare(mirpur10)
        assertEquals(0.5f, unit.first, 1e-6f)
        assertEquals(0.5f, unit.second, 1e-6f)
    }

    @Test
    fun dhakaCoordinatesAreRecognisedAsInsideBangladesh() {
        assertTrue(mirpur10.isInsideBangladesh())
        assertFalse(LatLng(51.5074, -0.1278).isInsideBangladesh())
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeLatitudeIsRejectedAtConstruction() {
        LatLng(120.0, 90.0)
    }
}
