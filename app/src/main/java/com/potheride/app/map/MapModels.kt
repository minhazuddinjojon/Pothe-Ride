package com.potheride.app.map

import com.potheride.app.core.geo.GeoUtils
import com.potheride.app.core.geo.LatLng

/**
 * The domain types the map layer speaks in.
 *
 * Deliberately free of any Android or OSMDroid type. Everything above this file — the
 * routing chain, the search controller, the tests — works on plain data, and only
 * [OsmRouteMap] ever converts to `GeoPoint`. That is what keeps the whole level
 * unit-testable on the JVM, the same rule `core/` already follows.
 */

/**
 * A drivable path between two points, as returned by a routing provider.
 *
 * [provider] is carried through to the UI on purpose. When the screen is showing a
 * ruler-straight line across Dhaka, the useful question is *which* provider produced it,
 * and a support conversation that starts with "it says STRAIGHT_LINE" is over in a
 * sentence. [isApproximate] is the same fact in the form the UI actually branches on.
 */
data class RouteGeometry(
    val points: List<LatLng>,
    val distanceMetres: Double,
    val durationSeconds: Double,
    val provider: RouteProvider
) {
    val isApproximate: Boolean get() = provider == RouteProvider.STRAIGHT_LINE

    init {
        require(points.size >= 2) { "a route needs at least two points" }
    }
}

enum class RouteProvider { OPEN_ROUTE_SERVICE, GRAPH_HOPPER, STRAIGHT_LINE }

/** A place, from a search result or a reverse geocode. */
data class Place(
    val name: String,
    val address: String,
    val position: LatLng
)

/**
 * Why a routing provider did not answer.
 *
 * Modelled rather than thrown because the fallback chain needs to *report* each failure
 * while continuing, and a chain built on exceptions loses every failure but the last —
 * which is exactly the one that matters least, since the last link never fails.
 */
sealed class RouteFailure {
    abstract val provider: RouteProvider

    /** No API key configured for this provider. Not an error: it is simply not set up. */
    data class NotConfigured(override val provider: RouteProvider) : RouteFailure()

    /** The provider answered, but not with a route (rate limit, bad key, 5xx). */
    data class HttpError(
        override val provider: RouteProvider,
        val status: Int,
        val body: String
    ) : RouteFailure()

    /** Transport failure — no network, DNS, timeout. */
    data class Unreachable(override val provider: RouteProvider, val cause: String) : RouteFailure()

    /** A 200 whose body we could not turn into a route. */
    data class Unusable(override val provider: RouteProvider, val reason: String) : RouteFailure()
}

/**
 * Result of asking the whole chain for a route.
 *
 * There is no failure case: the last link is a straight line, which cannot fail. The
 * attempts that came first are kept so the log (and a diagnostics screen) can say why the
 * good providers were skipped.
 */
data class RoutingOutcome(
    val route: RouteGeometry,
    val attempts: List<RouteFailure>
)

/** Builds the always-available last-resort geometry: a straight line, at a plausible speed. */
internal fun straightLineRoute(from: LatLng, to: LatLng): RouteGeometry {
    val km = GeoUtils.distanceKm(from, to)
    return RouteGeometry(
        points = listOf(from, to),
        distanceMetres = km * 1000.0,
        // Dhaka's average traffic speed is well under 20 km/h; using a motorway figure here
        // would give a straight-line fallback a *shorter* ETA than the real route, which
        // reads to the passenger as the app getting better when it has in fact given up.
        durationSeconds = km / STRAIGHT_LINE_SPEED_KMH * 3600.0,
        provider = RouteProvider.STRAIGHT_LINE
    )
}

internal const val STRAIGHT_LINE_SPEED_KMH = 18.0
