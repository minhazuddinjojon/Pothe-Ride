package com.potheride.app.core.geo

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A WGS-84 coordinate. Pure Kotlin on purpose: nothing in [com.potheride.app.core]
 * touches the Android framework, so the whole matching/pricing engine compiles and
 * runs on a plain JVM and is covered by fast unit tests (see src/test).
 */
data class LatLng(val lat: Double, val lng: Double) {
    init {
        require(lat in -90.0..90.0) { "latitude out of range: $lat" }
        require(lng in -180.0..180.0) { "longitude out of range: $lng" }
    }

    companion object {
        /** Bounding box of Bangladesh, used to reject obviously bogus coordinates. */
        val BD_SOUTH = 20.5
        val BD_NORTH = 26.7
        val BD_WEST = 88.0
        val BD_EAST = 92.7
    }

    fun isInsideBangladesh(): Boolean =
        lat in BD_SOUTH..BD_NORTH && lng in BD_WEST..BD_EAST
}

object GeoUtils {

    const val EARTH_RADIUS_KM = 6371.0088

    /** Great-circle (haversine) distance in kilometres. */
    fun distanceKm(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
    }

    /**
     * Projects lat/lng into a local flat-earth plane measured in kilometres, using
     * [originLat] to scale longitude. Accurate to well under a metre over a city-sized
     * area, which is all the matcher needs and is far cheaper than repeated haversine.
     */
    private fun project(p: LatLng, originLat: Double): Pair<Double, Double> {
        val kmPerDegLat = 110.574
        val kmPerDegLng = 111.320 * cos(Math.toRadians(originLat))
        return (p.lng * kmPerDegLng) to (p.lat * kmPerDegLat)
    }

    /** Shortest distance in kilometres from [p] to the segment [a]-[b]. */
    fun distanceToSegmentKm(p: LatLng, a: LatLng, b: LatLng): Double =
        projectionOnSegment(p, a, b).distanceKm

    /**
     * Full result of projecting [p] onto segment [a]-[b]: how far off the segment the
     * point sits, and how far along the segment (0..1) the closest point lies. The
     * fraction is what lets the matcher order pickup before drop-off *within the same
     * leg* of a route, which a leg index alone cannot express.
     */
    fun projectionOnSegment(p: LatLng, a: LatLng, b: LatLng): SegmentProjection {
        val (ax, ay) = project(a, p.lat)
        val (bx, by) = project(b, p.lat)
        val (px, py) = project(p, p.lat)

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        val dist = sqrt((px - cx).pow(2) + (py - cy).pow(2))
        return SegmentProjection(distanceKm = dist, fractionAlong = t)
    }

    /** Linear interpolation between two coordinates; [t] is clamped to 0..1. */
    fun interpolate(a: LatLng, b: LatLng, t: Double): LatLng {
        val c = t.coerceIn(0.0, 1.0)
        return LatLng(a.lat + (b.lat - a.lat) * c, a.lng + (b.lng - a.lng) * c)
    }

    /** Total length of a polyline in kilometres. Returns 0 for fewer than two points. */
    fun polylineLengthKm(points: List<LatLng>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) total += distanceKm(points[i], points[i + 1])
        return total
    }

    /**
     * Cumulative distance from the start of the polyline to each vertex.
     * `cumulative[i]` is the distance travelled to reach `points[i]`.
     */
    fun cumulativeKm(points: List<LatLng>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + distanceKm(points[i - 1], points[i])
        return out
    }

    /**
     * Position of a vehicle that has travelled [travelledKm] along [points], plus the
     * index of the leg it is currently on. Used to render live driver progress.
     */
    fun pointAtDistance(points: List<LatLng>, travelledKm: Double): PointOnRoute {
        if (points.isEmpty()) throw IllegalArgumentException("empty polyline")
        if (points.size == 1) return PointOnRoute(points[0], 0, 0.0)
        val cum = cumulativeKm(points)
        val total = cum.last()
        val d = travelledKm.coerceIn(0.0, total)
        for (i in 0 until points.size - 1) {
            if (d <= cum[i + 1] || i == points.size - 2) {
                val legLen = cum[i + 1] - cum[i]
                val t = if (legLen <= 0.0) 0.0 else (d - cum[i]) / legLen
                return PointOnRoute(interpolate(points[i], points[i + 1], t), i, t)
            }
        }
        return PointOnRoute(points.last(), points.size - 2, 1.0)
    }

    /** Bounding box of a set of points, padded by [paddingDegrees] on every side. */
    fun boundsOf(points: List<LatLng>, paddingDegrees: Double = 0.0): Bounds {
        require(points.isNotEmpty()) { "cannot compute bounds of an empty list" }
        var minLat = points[0].lat; var maxLat = points[0].lat
        var minLng = points[0].lng; var maxLng = points[0].lng
        for (p in points) {
            minLat = min(minLat, p.lat); maxLat = max(maxLat, p.lat)
            minLng = min(minLng, p.lng); maxLng = max(maxLng, p.lng)
        }
        return Bounds(
            minLat - paddingDegrees, minLng - paddingDegrees,
            maxLat + paddingDegrees, maxLng + paddingDegrees
        )
    }
}

data class SegmentProjection(val distanceKm: Double, val fractionAlong: Double)

data class PointOnRoute(val position: LatLng, val legIndex: Int, val fractionAlongLeg: Double)

data class Bounds(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double) {
    val latSpan: Double get() = maxLat - minLat
    val lngSpan: Double get() = maxLng - minLng

    /**
     * Maps a coordinate into a 0..1 unit square, y flipped so that north is up when
     * drawn onto a canvas. Degenerate spans collapse to the centre instead of dividing
     * by zero, which is what happens when a "route" is a single repeated point.
     */
    fun toUnitSquare(p: LatLng): Pair<Float, Float> {
        val x = if (lngSpan <= 1e-12) 0.5 else (p.lng - minLng) / lngSpan
        val y = if (latSpan <= 1e-12) 0.5 else 1.0 - (p.lat - minLat) / latSpan
        return x.toFloat() to y.toFloat()
    }
}

/** Two coordinates are treated as the same place when within a few metres. */
fun LatLng.approximatelyEquals(other: LatLng, toleranceDegrees: Double = 1e-5): Boolean =
    abs(lat - other.lat) < toleranceDegrees && abs(lng - other.lng) < toleranceDegrees
