package com.potheride.app.map

import com.potheride.app.core.geo.LatLng

/**
 * Google's "encoded polyline" format, which both OpenRouteService and GraphHopper use to
 * return route geometry.
 *
 * ### Why this is worth its own file and its own tests
 * The format is a delta encoding: each vertex is stored as the *difference* from the
 * previous one, zig-zag encoded, chunked into six-bit groups. Two consequences bite in
 * practice:
 *
 * 1. **A single mis-decoded character corrupts every point after it**, not just its own.
 *    There is no framing to resynchronise on, so the failure mode is not "one waypoint is
 *    slightly off" but "the route walks off into the Bay of Bengal from the third vertex".
 * 2. **Precision is not in the payload.** ORS and GraphHopper both default to five decimal
 *    places, but ORS returns *six* when elevation is requested and GraphHopper can be
 *    configured either way. Decoding a 1e6 payload at 1e5 puts the route ten times too far
 *    from the origin — off the map entirely, and with no error to catch. [precision] is
 *    therefore explicit at every call site rather than defaulted silently.
 *
 * Both are silent, so both are pinned by unit tests.
 */
object PolylineCodec {

    /**
     * Decodes [encoded] into coordinates.
     *
     * Coordinates outside WGS-84 range are dropped rather than allowed to throw from
     * [LatLng]'s `require`. A truncated response is a routine network outcome, and it
     * should cost us the route (and therefore trigger the next provider), not the process.
     */
    fun decode(encoded: String, precision: Int = 5): List<LatLng> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            val dLat = readSignedChunk(encoded, index) ?: break
            index = dLat.second
            lat += dLat.first

            val dLng = readSignedChunk(encoded, index) ?: break
            index = dLng.second
            lng += dLng.first

            val latitude = lat / factor
            val longitude = lng / factor
            if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                out += LatLng(latitude, longitude)
            } else {
                // Out of range means we have lost sync with the stream; everything after
                // this point is noise, so stop rather than emit plausible-looking rubbish.
                return out
            }
        }
        return out
    }

    /** Encodes [points]. Present so the codec can be round-trip tested against itself. */
    fun encode(points: List<LatLng>, precision: Int = 5): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var lastLat = 0
        var lastLng = 0
        for (p in points) {
            val lat = Math.round(p.lat * factor).toInt()
            val lng = Math.round(p.lng * factor).toInt()
            writeSignedChunk(sb, lat - lastLat)
            writeSignedChunk(sb, lng - lastLng)
            lastLat = lat
            lastLng = lng
        }
        return sb.toString()
    }

    /**
     * Reads one zig-zag varint starting at [start].
     *
     * Returns the value and the index just past it, or `null` when the chunk runs off the
     * end of the string — which is what a truncated body looks like.
     */
    private fun readSignedChunk(encoded: String, start: Int): Pair<Int, Int>? {
        var index = start
        var shift = 0
        var result = 0
        var b: Int
        do {
            if (index >= encoded.length) return null
            b = encoded[index++].code - 63
            if (b < 0) return null
            result = result or ((b and 0x1f) shl shift)
            shift += 5
            // Five bits per character, so more than six characters cannot be a valid
            // 32-bit delta; treating it as one would silently wrap.
            if (shift > 30) return null
        } while (b >= 0x20)
        // Least-significant bit is the sign, hence the arithmetic shift and complement.
        val value = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
        return value to index
    }

    private fun writeSignedChunk(sb: StringBuilder, value: Int) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }
}
