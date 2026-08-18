package com.potheride.app.map

import com.potheride.app.core.geo.LatLng
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchControllerTest {

    private fun body() = """
        [{"lat":"23.8067","lon":"90.3686","display_name":"Mirpur-10, Dhaka, Bangladesh"}]
    """.trimIndent()

    @Test
    fun `only the query the user paused on is actually sent`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val transport = FakeHttpTransport { HttpResponse(200, body()) }
        val client = NominatimClient(transport, userAgent = "ua", minIntervalMillis = 0)
        val controller = PlaceSearchController(client, scope, debounceMillis = 400)

        // A fast typist: five keystrokes inside the debounce window.
        controller.onQueryChanged("M")
        advanceTimeBy(100)
        controller.onQueryChanged("Mi")
        advanceTimeBy(100)
        controller.onQueryChanged("Mir")
        advanceTimeBy(100)
        controller.onQueryChanged("Mirp")
        advanceTimeBy(100)
        controller.onQueryChanged("Mirpur")
        advanceTimeBy(500)

        assertEquals(1, transport.requests.size)
        assertTrue(transport.requests.single().url.contains("Mirpur"))
    }

    @Test
    fun `a blank query clears results without waiting for the debounce`() = runTest {
        val scope = this
        val transport = FakeHttpTransport { HttpResponse(200, body()) }
        val client = NominatimClient(transport, userAgent = "ua", minIntervalMillis = 0)
        val controller = PlaceSearchController(client, scope, debounceMillis = 400)

        controller.onQueryChanged("Mirpur")
        advanceTimeBy(500)
        assertTrue(controller.state.value.results.isNotEmpty())

        controller.onQueryChanged("")
        assertTrue(controller.state.value.results.isEmpty())
        assertEquals(false, controller.state.value.searching)
    }

    @Test
    fun `searching is true while debouncing and false once results land`() = runTest {
        val scope = this
        val transport = FakeHttpTransport { HttpResponse(200, body()) }
        val client = NominatimClient(transport, userAgent = "ua", minIntervalMillis = 0)
        val controller = PlaceSearchController(client, scope, debounceMillis = 400)

        controller.onQueryChanged("Mirpur")
        assertTrue(controller.state.value.searching)

        advanceTimeBy(500)
        assertEquals(false, controller.state.value.searching)
    }
}
