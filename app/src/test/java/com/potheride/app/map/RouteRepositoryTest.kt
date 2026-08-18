package com.potheride.app.map

import com.potheride.app.core.geo.LatLng
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRepositoryTest {

    private val mirpur = LatLng(23.8067, 90.3686)
    private val tongi = LatLng(23.8909, 90.4023)

    private fun orsBody(polyline: String) = """
        {"routes":[{"summary":{"distance":5000.0,"duration":900.0},"geometry":"$polyline"}]}
    """.trimIndent()

    private fun graphHopperBody(polyline: String) = """
        {"paths":[{"distance":5200.0,"time":950000,"points":"$polyline"}]}
    """.trimIndent()

    @Test
    fun `falls straight through to a straight line when no keys are configured`() = runTest {
        val repo = RouteRepository(FakeHttpTransport { error("must not call the network") }, StaticMapApiKeys())
        val outcome = repo.route(mirpur, tongi)

        assertEquals(RouteProvider.STRAIGHT_LINE, outcome.route.provider)
        assertTrue(outcome.route.isApproximate)
        assertEquals(2, outcome.attempts.size)
        assertTrue(outcome.attempts.all { it is RouteFailure.NotConfigured })
    }

    @Test
    fun `uses OpenRouteService when it answers successfully`() = runTest {
        // A short encoded polyline (5-point-precision) for two nearby points.
        val polyline = PolylineCodec.encode(listOf(mirpur, tongi), precision = 6)
        val transport = FakeHttpTransport { HttpResponse(200, orsBody(polyline)) }
        val repo = RouteRepository(transport, StaticMapApiKeys(openRouteService = "key"))

        val outcome = repo.route(mirpur, tongi)

        assertEquals(RouteProvider.OPEN_ROUTE_SERVICE, outcome.route.provider)
        assertTrue(outcome.attempts.isEmpty())
        assertEquals(5000.0, outcome.route.distanceMetres, 0.1)
    }

    @Test
    fun `falls back to GraphHopper when OpenRouteService fails`() = runTest {
        val polyline = PolylineCodec.encode(listOf(mirpur, tongi), precision = 5)
        val transport = FakeHttpTransport { request ->
            if (request.url.contains("openrouteservice")) {
                HttpResponse(429, "rate limited")
            } else {
                HttpResponse(200, graphHopperBody(polyline))
            }
        }
        val repo = RouteRepository(
            transport, StaticMapApiKeys(openRouteService = "key", graphHopper = "key2")
        )

        val outcome = repo.route(mirpur, tongi)

        assertEquals(RouteProvider.GRAPH_HOPPER, outcome.route.provider)
        assertEquals(1, outcome.attempts.size)
        assertTrue(outcome.attempts.single() is RouteFailure.HttpError)
    }

    @Test
    fun `falls back to a straight line when every configured provider fails`() = runTest {
        val transport = FakeHttpTransport { HttpResponse(500, "server error") }
        val repo = RouteRepository(
            transport, StaticMapApiKeys(openRouteService = "key", graphHopper = "key2")
        )

        val outcome = repo.route(mirpur, tongi)

        assertEquals(RouteProvider.STRAIGHT_LINE, outcome.route.provider)
        assertEquals(2, outcome.attempts.size)
    }

    @Test
    fun `an unreachable host is reported as Unreachable, not HttpError`() = runTest {
        val transport = FakeHttpTransport.offline()
        val repo = RouteRepository(transport, StaticMapApiKeys(openRouteService = "key"))

        val outcome = repo.route(mirpur, tongi)

        assertTrue(outcome.attempts.single() is RouteFailure.Unreachable)
    }

    @Test
    fun `an unparseable success response falls through rather than crashing`() = runTest {
        val transport = FakeHttpTransport { HttpResponse(200, "{not json") }
        val repo = RouteRepository(transport, StaticMapApiKeys(openRouteService = "key"))

        val outcome = repo.route(mirpur, tongi)

        assertEquals(RouteProvider.STRAIGHT_LINE, outcome.route.provider)
    }

    @Test
    fun `the straight line fallback never throws for identical origin and destination`() = runTest {
        val repo = RouteRepository(FakeHttpTransport { error("no network") }, StaticMapApiKeys())
        val outcome = repo.route(mirpur, mirpur)
        assertEquals(0.0, outcome.route.distanceMetres, 0.001)
    }
}
