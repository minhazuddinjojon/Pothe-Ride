package com.potheride.app.map

import com.potheride.app.core.geo.LatLng
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Place search and reverse geocoding via Nominatim (OpenStreetMap's free geocoder).
 *
 * ### The rate limit is not a suggestion
 * Nominatim's usage policy caps the public instance at **one request per second per
 * application**, and it is enforced by IP — not by API key, because there is no key.
 * An app that fires a request per keystroke gets the *whole app's user base* banned from
 * one shared IP range, not just the offending device. [RateLimiter] is therefore not an
 * optimisation; it is what keeps the feature usable at all. [minIntervalMillis] defaults
 * to 1100 ms rather than exactly 1000 to leave headroom for clock drift between this
 * device and the enforcing server.
 *
 * ### Why cache, separately from the rate limit
 * A passenger typing "Mirpur" then backspacing to "Mirpu" then retyping "Mirpur" should
 * not spend three of this app's precious one-per-second slots on the same string. The
 * cache is keyed on the exact trimmed, lower-cased query.
 */
class NominatimClient(
    private val transport: HttpTransport,
    private val userAgent: String,
    minIntervalMillis: Long = 1_100L,
    private val cacheSize: Int = 50
) {
    private val rateLimiter = RateLimiter(minIntervalMillis)
    private val searchCache = LruCache<String, List<Place>>(cacheSize)
    private val reverseCache = LruCache<String, Place?>(cacheSize)

    /**
     * Free-text search, scoped to Bangladesh so "Mirpur" does not compete with a
     * same-named place on another continent.
     */
    suspend fun search(query: String, limit: Int = 5): List<Place> {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return emptyList()
        searchCache.get(key)?.let { return it }

        rateLimiter.acquire()
        val url = "https://nominatim.openstreetmap.org/search" +
            "?q=${urlEncode(query)}&format=jsonv2&countrycodes=bd&limit=$limit"
        val response = transport.execute(
            HttpRequest(url = url, headers = mapOf("User-Agent" to userAgent))
        )
        if (!response.isSuccessful) return emptyList()

        val results = runCatching { parseSearchResults(response.body) }.getOrDefault(emptyList())
        searchCache.put(key, results)
        return results
    }

    /** Reverse geocodes a coordinate to a human-readable address, or `null`. */
    suspend fun reverseGeocode(point: LatLng): Place? {
        // Rounded so nearby fixes along a slowly-moving GPS track share a cache entry —
        // full double precision would make every fix a cache miss.
        val key = "%.4f,%.4f".format(point.lat, point.lng)
        if (reverseCache.containsKey(key)) return reverseCache.get(key)

        rateLimiter.acquire()
        val url = "https://nominatim.openstreetmap.org/reverse" +
            "?lat=${point.lat}&lon=${point.lng}&format=jsonv2"
        val response = transport.execute(
            HttpRequest(url = url, headers = mapOf("User-Agent" to userAgent))
        )
        val place = if (response.isSuccessful) {
            runCatching { parseReverseResult(response.body, point) }.getOrNull()
        } else null

        reverseCache.put(key, place)
        return place
    }

    private fun parseSearchResults(body: String): List<Place> =
        Json.parseArray(body).mapNotNull { entry ->
            val obj = entry as? Map<*, *> ?: return@mapNotNull null
            val lat = (obj["lat"] as? String)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = (obj["lon"] as? String)?.toDoubleOrNull() ?: return@mapNotNull null
            val displayName = obj["display_name"] as? String ?: return@mapNotNull null
            Place(
                name = displayName.substringBefore(","),
                address = displayName,
                position = LatLng(lat, lon)
            )
        }

    private fun parseReverseResult(body: String, fallbackPoint: LatLng): Place? {
        val obj = Json.parseObject(body)
        val displayName = obj["display_name"] as? String ?: return null
        val lat = (obj["lat"] as? String)?.toDoubleOrNull() ?: fallbackPoint.lat
        val lon = (obj["lon"] as? String)?.toDoubleOrNull() ?: fallbackPoint.lng
        return Place(
            name = displayName.substringBefore(","),
            address = displayName,
            position = LatLng(lat, lon)
        )
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}

/**
 * Serialises calls to at least [minIntervalMillis] apart, queueing rather than dropping.
 *
 * A [Mutex] rather than a plain "last call" timestamp check: two coroutines racing to
 * read a stale timestamp and both concluding "it's been long enough" is exactly how a
 * naive throttle leaks past its own limit under concurrent callers.
 */
internal class RateLimiter(private val minIntervalMillis: Long) {
    private val mutex = Mutex()
    private var lastCallAt = 0L

    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastCallAt
            if (elapsed < minIntervalMillis) {
                kotlinx.coroutines.delay(minIntervalMillis - elapsed)
            }
            lastCallAt = System.currentTimeMillis()
        }
    }
}

/** A small fixed-capacity LRU cache. Not thread-safe by design — callers hold one each. */
internal class LruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
            size > maxSize
    }

    fun get(key: K): V? = map[key]
    fun containsKey(key: K): Boolean = map.containsKey(key)
    fun put(key: K, value: V) { map[key] = value }
}
