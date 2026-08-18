package com.potheride.app.map

import com.potheride.app.core.geo.LatLng
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NominatimClientTest {

    private fun searchBody() = """
        [{"lat":"23.8067","lon":"90.3686","display_name":"Mirpur-10, Dhaka, Bangladesh"}]
    """.trimIndent()

    private fun reverseBody() = """
        {"lat":"23.8067","lon":"90.3686","display_name":"Mirpur-10, Dhaka, Bangladesh"}
    """.trimIndent()

    @Test
    fun `every request carries a user agent, never the library default`() = runTest {
        val transport = FakeHttpTransport { HttpResponse(200, searchBody()) }
        val client = NominatimClient(transport, userAgent = "com.potheride.app", minIntervalMillis = 0)

        client.search("Mirpur")

        val request = transport.requests.single()
        assertEquals("com.potheride.app", request.headers["User-Agent"])
    }

    @Test
    fun `search results are parsed into places`() = runTest {
        val transport = FakeHttpTransport { HttpResponse(200, searchBody()) }
        val client = NominatimClient(transport, userAgent = "ua", minIntervalMillis = 0)

        val results = client.search("Mirpur")

        assertEquals(1, results.size)
        assertEquals("Mirpur-10", results.single().name)
        assertEquals(23.8067, results.single().position.lat, 0.0001)
    }

    @Test
    fun `an identical query is answered from the cache without a second request`() = runTest {
        var calls = 0
        val transport = FakeHttpTransport { calls++; HttpResponse(200, searchBody()) }
        val client = NominatimClient(transport, userAgent = "ua", minIntervalMillis = 0)

        client.search("Mirpur")
        client.search("Mirpur")
        client.search("  mirpur  ") // trimmed + case-insensitive

        assertEquals(1, calls)
    }

    @Test
    fun `a blank query never reaches the network`() = runTest {
        val transport = FakeHttpTransport { error("must not be called") }
        val client = NominatimClient(transport, userAgent = "ua", minIntervalMillis = 0)

        assertTrue(client.search("   ").isEmpty())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `a failed response yields an empty list rather than throwing`() = runTest {
        val transport = FakeHttpTransport { HttpResponse(503, "unavailable") }
        val client = NominatimClient(transport, userAgent = "ua", minIntervalMillis = 0)

        assertTrue(client.search("Mirpur").isEmpty())
    }

    @Test
    fun `reverse geocoding parses a single place`() = runTest {
        val transport = FakeHttpTransport { HttpResponse(200, reverseBody()) }
        val client = NominatimClient(transport, userAgent = "ua", minIntervalMillis = 0)

        val place = client.reverseGeocode(LatLng(23.8067, 90.3686))

        assertEquals("Mirpur-10", place?.name)
    }

    @Test
    fun `reverse geocoding caches a null result too, so a repeat lookup does not retry`() = runTest {
        var calls = 0
        val transport = FakeHttpTransport { calls++; HttpResponse(200, "{not json}") }
        val client = NominatimClient(transport, userAgent = "ua", minIntervalMillis = 0)

        assertNull(client.reverseGeocode(LatLng(23.8067, 90.3686)))
        assertNull(client.reverseGeocode(LatLng(23.8067, 90.3686)))

        assertEquals(1, calls)
    }

    @Test
    fun `a second request waits out the configured interval on the test's virtual clock`() = runTest {
        val transport = FakeHttpTransport { HttpResponse(200, searchBody()) }
        val client = NominatimClient(transport, userAgent = "ua", minIntervalMillis = 1_000)

        client.search("one")
        val before = currentTime
        client.search("two")

        // The limiter's `delay()` runs on this same test dispatcher, so runTest's virtual
        // clock — not System.currentTimeMillis() — is what actually advances here.
        assertTrue("second call did not wait: advanced ${currentTime - before}ms", currentTime - before >= 1_000)
    }
}
