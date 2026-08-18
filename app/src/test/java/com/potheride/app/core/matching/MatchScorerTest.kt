package com.potheride.app.core.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchScorerTest {

    private val minute = 60_000L
    private val now = 1_700_000_000_000L

    private fun anchor(offRouteKm: Double, alongKm: Double) = RouteAnchor(
        legIndex = 0,
        fractionAlongLeg = 0.0,
        offRouteKm = offRouteKm,
        distanceAlongRouteKm = alongKm
    )

    private fun result(
        overlap: Float = 0.9f,
        pickupOffKm: Double = 0.0,
        dropOffKm: Double = 0.0
    ) = RouteMatchResult(
        pickup = anchor(pickupOffKm, 1.0),
        drop = anchor(dropOffKm, 6.0),
        overlapRatio = overlap,
        sharedDistanceKm = 5.0,
        routeLengthKm = 5.0 / overlap,
        detourKm = 2 * (pickupOffKm + dropOffKm)
    )

    // ---- bounds ----

    @Test
    fun `a perfect match scores 100`() {
        val score = MatchScorer.score(
            result(overlap = 1f, pickupOffKm = 0.0, dropOffKm = 0.0),
            departureTime = now + (MatchScorer.IDEAL_WAIT_MINUTES * minute).toLong(),
            now = now
        )
        assertEquals(100, score.value)
    }

    @Test
    fun `a match that is bad on every factor scores 0`() {
        val score = MatchScorer.score(
            result(overlap = 0f, pickupOffKm = 10.0, dropOffKm = 10.0),
            departureTime = now - 60 * minute,
            now = now
        )
        assertEquals(0, score.value)
    }

    @Test
    fun `the score never leaves 0 to 100`() {
        val cases = listOf(-5.0, 0.0, 0.4, 1.5, 40.0)
        for (off in cases) {
            for (waitMin in listOf(-500L, 0L, 5L, 45L, 10_000L)) {
                val score = MatchScorer.score(
                    result(overlap = 0.5f, pickupOffKm = off, dropOffKm = off),
                    departureTime = now + waitMin * minute,
                    now = now
                )
                assertTrue("score ${score.value} out of range", score.value in 0..100)
            }
        }
    }

    // ---- individual factors ----

    @Test
    fun `proximity is 1 at the roadside and 0 at the cutoff`() {
        assertEquals(1.0, MatchScorer.proximityFactor(0.0), 0.0001)
        assertEquals(0.0, MatchScorer.proximityFactor(MatchScorer.PROXIMITY_ZERO_KM), 0.0001)
        assertEquals(0.5, MatchScorer.proximityFactor(MatchScorer.PROXIMITY_ZERO_KM / 2), 0.0001)
    }

    @Test
    fun `proximity does not go negative beyond the cutoff`() {
        assertEquals(0.0, MatchScorer.proximityFactor(50.0), 0.0001)
    }

    @Test
    fun `a departure already in the past scores zero regardless of how long ago`() {
        val justGone = MatchScorer.departureFactor(now - 1 * minute, now)
        val longGone = MatchScorer.departureFactor(now - 600 * minute, now)
        assertEquals(0.0, justGone, 0.0001)
        assertEquals(0.0, longGone, 0.0001)
    }

    @Test
    fun `departure peaks at the ideal wait and falls off on both sides`() {
        val ideal = MatchScorer.departureFactor(now + 5 * minute, now)
        val tooSoon = MatchScorer.departureFactor(now + 1 * minute, now)
        val later = MatchScorer.departureFactor(now + 20 * minute, now)
        assertEquals(1.0, ideal, 0.0001)
        assertTrue(tooSoon < ideal)
        assertTrue(later < ideal)
        // A ride leaving in a minute still beats one leaving in twenty.
        assertTrue(tooSoon > later)
    }

    // ---- ranking behaviour: the reason this class exists ----

    @Test
    fun `a good ride leaving soon outranks a perfect ride leaving in three hours`() {
        val soon = MatchScorer.score(
            result(overlap = 0.75f), departureTime = now + 6 * minute, now = now
        )
        val perfectButLate = MatchScorer.score(
            result(overlap = 1.0f), departureTime = now + 180 * minute, now = now
        )
        assertTrue(
            "soon=${soon.value} late=${perfectButLate.value}",
            soon.value > perfectButLate.value
        )
    }

    @Test
    fun `between two identical rides the shorter walk wins`() {
        val closeBy = MatchScorer.score(
            result(overlap = 0.8f, pickupOffKm = 0.05), departureTime = now + 5 * minute, now = now
        )
        val farther = MatchScorer.score(
            result(overlap = 0.8f, pickupOffKm = 0.9), departureTime = now + 5 * minute, now = now
        )
        assertTrue(closeBy.value > farther.value)
    }

    @Test
    fun `pickup proximity counts for more than destination proximity`() {
        // Walking to the pickup happens before the ride and can make you miss it;
        // walking from the drop-off cannot.
        val awkwardPickup = MatchScorer.score(
            result(overlap = 0.8f, pickupOffKm = 1.0, dropOffKm = 0.0),
            departureTime = now + 5 * minute, now = now
        )
        val awkwardDrop = MatchScorer.score(
            result(overlap = 0.8f, pickupOffKm = 0.0, dropOffKm = 1.0),
            departureTime = now + 5 * minute, now = now
        )
        assertTrue(awkwardDrop.value > awkwardPickup.value)
    }

    @Test
    fun `overlap dominates any single other factor`() {
        val highOverlapOtherwiseAverage = MatchScorer.score(
            result(overlap = 1.0f, pickupOffKm = 0.75, dropOffKm = 0.75),
            departureTime = now + 25 * minute, now = now
        )
        val noOverlapOtherwisePerfect = MatchScorer.score(
            result(overlap = 0.05f), departureTime = now + 5 * minute, now = now
        )
        assertTrue(highOverlapOtherwiseAverage.value > noOverlapOtherwisePerfect.value)
    }

    // ---- weights and diagnostics ----

    @Test
    fun `the weights sum to one`() {
        val total = MatchScorer.WEIGHT_OVERLAP +
            MatchScorer.WEIGHT_DEPARTURE +
            MatchScorer.WEIGHT_PICKUP_PROXIMITY +
            MatchScorer.WEIGHT_DESTINATION_PROXIMITY
        assertEquals(1.0, total, 0.000001)
    }

    @Test
    fun `the breakdown names the factor that held the match back`() {
        val lateDeparture = MatchScorer.score(
            result(overlap = 1.0f), departureTime = now + 300 * minute, now = now
        )
        assertEquals(ScoreFactor.DEPARTURE, lateDeparture.weakestFactor)

        val longWalk = MatchScorer.score(
            result(overlap = 1.0f, pickupOffKm = 1.4),
            departureTime = now + 5 * minute, now = now
        )
        assertEquals(ScoreFactor.PICKUP_PROXIMITY, longWalk.weakestFactor)
    }

    @Test
    fun `scores compare by value`() {
        val better = MatchScorer.score(result(overlap = 0.9f), now + 5 * minute, now)
        val worse = MatchScorer.score(result(overlap = 0.2f), now + 5 * minute, now)
        assertTrue(better > worse)
        assertEquals(listOf(worse, better), listOf(better, worse).sorted())
    }
}
