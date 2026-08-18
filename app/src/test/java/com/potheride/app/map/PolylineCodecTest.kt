package com.potheride.app.map

import com.potheride.app.core.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineCodecTest {

    private val dhakaRoute = listOf(
        LatLng(23.8067, 90.3686),
        LatLng(23.7960, 90.3742),
        LatLng(23.7780, 90.3796),
        LatLng(23.8909, 90.4023)
    )

    @Test
    fun `Google's own reference example decodes correctly`() {
        // The canonical example from Google's encoded polyline algorithm documentation.
        val points = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 0.00001)
        assertEquals(-120.2, points[0].lng, 0.00001)
        assertEquals(40.7, points[1].lat, 0.00001)
        assertEquals(-120.95, points[1].lng, 0.00001)
        assertEquals(43.252, points[2].lat, 0.00001)
        assertEquals(-126.453, points[2].lng, 0.00001)
    }

    @Test
    fun `encoding then decoding at the same precision recovers the original points`() {
        val encoded = PolylineCodec.encode(dhakaRoute, precision = 5)
        val decoded = PolylineCodec.decode(encoded, precision = 5)

        assertEquals(dhakaRoute.size, decoded.size)
        dhakaRoute.zip(decoded).forEach { (expected, actual) ->
            assertEquals(expected.lat, actual.lat, 0.00001)
            assertEquals(expected.lng, actual.lng, 0.00001)
        }
    }

    @Test
    fun `decoding at the wrong precision does not recover the original points`() {
        // This is the exact silent failure the class's KDoc warns about: ORS returns
        // six-decimal precision when elevation is requested, GraphHopper defaults to
        // five. Decoding one at the other's precision must NOT look like success.
        val encodedAtSix = PolylineCodec.encode(dhakaRoute, precision = 6)
        val decodedAtFive = PolylineCodec.decode(encodedAtSix, precision = 5)

        // The values are wildly wrong — off by roughly 10x — not subtly wrong.
        val firstDecoded = decodedAtFive.first()
        val firstExpected = dhakaRoute.first()
        assertTrue(
            "decoding at the wrong precision should diverge sharply, but got $firstDecoded vs $firstExpected",
            Math.abs(firstDecoded.lat - firstExpected.lat) > 1.0
        )
    }

    @Test
    fun `a truncated payload is dropped rather than throwing`() {
        val encoded = PolylineCodec.encode(dhakaRoute, precision = 5)
        // Cut off mid-chunk.
        val truncated = encoded.dropLast(2)
        // Must not throw — a truncated body is a routine network outcome.
        PolylineCodec.decode(truncated, precision = 5)
    }

    @Test
    fun `an empty string decodes to an empty list`() {
        assertEquals(emptyList<LatLng>(), PolylineCodec.decode("", precision = 5))
    }

    @Test
    fun `garbage input does not throw`() {
        // Characters outside the encoder's valid range must not crash the decoder — a
        // corrupted response body is exactly this kind of input.
        PolylineCodec.decode("!!!not a polyline###", precision = 5)
    }

    @Test
    fun `negative coordinates round trip correctly`() {
        // The sign bit is the trickiest part of the zig-zag encoding; Dhaka is all
        // positive lat/lng, so this exercises the branch that a route confined to
        // Bangladesh never would.
        val points = listOf(LatLng(-33.8688, 151.2093), LatLng(-33.8, 151.3))
        val encoded = PolylineCodec.encode(points, precision = 5)
        val decoded = PolylineCodec.decode(encoded, precision = 5)
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (expected, actual) ->
            assertEquals(expected.lat, actual.lat, 0.00001)
            assertEquals(expected.lng, actual.lng, 0.00001)
        }
    }
}
