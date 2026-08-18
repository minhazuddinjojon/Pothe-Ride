package com.potheride.app.map

import com.potheride.app.core.geo.LatLng
import kotlinx.coroutines.CancellationException

/**
 * The routing fallback chain: OpenRouteService, then GraphHopper, then a straight line.
 *
 * Each provider is tried in order and the first success wins. This is the class the
 * roadmap means by "the map must never render empty" — [route] cannot fail, because its
 * last link is [straightLineRoute], which needs no network and no key.
 */
class RouteRepository(
    private val transport: HttpTransport,
    private val apiKeys: MapApiKeys
) {

    /**
     * Requests a route, trying providers in priority order.
     *
     * A provider is skipped, not attempted, when its key is absent — an absent key is not
     * failure, it is a link that was never wired up. Any provider that *is* attempted and
     * fails contributes a [RouteFailure] to [RoutingOutcome.attempts] so the caller (and,
     * ultimately, a diagnostics screen) can see why the chain fell through.
     */
    suspend fun route(from: LatLng, to: LatLng): RoutingOutcome {
        val attempts = mutableListOf<RouteFailure>()

        apiKeys.openRouteService?.let { key ->
            when (val result = requestOpenRouteService(from, to, key)) {
                is ProviderResult.Success -> return RoutingOutcome(result.route, attempts)
                is ProviderResult.Failure -> attempts += result.failure
            }
        } ?: run { attempts += RouteFailure.NotConfigured(RouteProvider.OPEN_ROUTE_SERVICE) }

        apiKeys.graphHopper?.let { key ->
            when (val result = requestGraphHopper(from, to, key)) {
                is ProviderResult.Success -> return RoutingOutcome(result.route, attempts)
                is ProviderResult.Failure -> attempts += result.failure
            }
        } ?: run { attempts += RouteFailure.NotConfigured(RouteProvider.GRAPH_HOPPER) }

        // Cannot fail: no network call, no external state.
        return RoutingOutcome(straightLineRoute(from, to), attempts)
    }

    private sealed interface ProviderResult {
        data class Success(val route: RouteGeometry) : ProviderResult
        data class Failure(val failure: RouteFailure) : ProviderResult
    }

    private suspend fun requestOpenRouteService(
        from: LatLng,
        to: LatLng,
        key: String
    ): ProviderResult {
        val provider = RouteProvider.OPEN_ROUTE_SERVICE
        return try {
            val body = """{"coordinates":[[${from.lng},${from.lat}],[${to.lng},${to.lat}]]}"""
            val response = transport.execute(
                HttpRequest(
                    url = "https://api.openrouteservice.org/v2/directions/driving-car/json",
                    method = "POST",
                    headers = mapOf(
                        "Authorization" to key,
                        "Content-Type" to "application/json"
                    ),
                    body = body
                )
            )
            if (!response.isSuccessful) {
                return ProviderResult.Failure(RouteFailure.HttpError(provider, response.status, response.body))
            }
            parseOrsResponse(response.body)
                ?.let { ProviderResult.Success(it) }
                ?: ProviderResult.Failure(RouteFailure.Unusable(provider, "no route in response"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpTransportException) {
            ProviderResult.Failure(RouteFailure.Unreachable(provider, e.message ?: "unreachable"))
        } catch (e: Exception) {
            ProviderResult.Failure(RouteFailure.Unusable(provider, e.message ?: "parse error"))
        }
    }

    /**
     * ORS's JSON profile nests the route under `routes[0]`, with the geometry as an
     * encoded polyline at six-decimal precision (this profile always requests it that
     * way — the GeoJSON profile would return raw coordinates instead, at the cost of a
     * much larger response for a long Dhaka commute).
     */
    private fun parseOrsResponse(body: String): RouteGeometry? {
        val root = Json.parseObject(body)
        val routes = root["routes"] as? List<*> ?: return null
        val first = routes.firstOrNull() as? Map<*, *> ?: return null
        val geometry = first["geometry"] as? String ?: return null
        val summary = first["summary"] as? Map<*, *>
        val points = PolylineCodec.decode(geometry, precision = 6)
        if (points.size < 2) return null
        return RouteGeometry(
            points = points,
            distanceMetres = (summary?.get("distance") as? Number)?.toDouble() ?: 0.0,
            durationSeconds = (summary?.get("duration") as? Number)?.toDouble() ?: 0.0,
            provider = RouteProvider.OPEN_ROUTE_SERVICE
        )
    }

    private suspend fun requestGraphHopper(
        from: LatLng,
        to: LatLng,
        key: String
    ): ProviderResult {
        val provider = RouteProvider.GRAPH_HOPPER
        return try {
            val url = "https://graphhopper.com/api/1/route" +
                "?point=${from.lat},${from.lng}&point=${to.lat},${to.lng}" +
                "&vehicle=car&points_encoded=true&key=$key"
            val response = transport.execute(HttpRequest(url = url))
            if (!response.isSuccessful) {
                return ProviderResult.Failure(RouteFailure.HttpError(provider, response.status, response.body))
            }
            parseGraphHopperResponse(response.body)
                ?.let { ProviderResult.Success(it) }
                ?: ProviderResult.Failure(RouteFailure.Unusable(provider, "no route in response"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpTransportException) {
            ProviderResult.Failure(RouteFailure.Unreachable(provider, e.message ?: "unreachable"))
        } catch (e: Exception) {
            ProviderResult.Failure(RouteFailure.Unusable(provider, e.message ?: "parse error"))
        }
    }

    /**
     * GraphHopper's default precision is five decimal places — a different value from
     * ORS's six. Decoding one at the other's precision is the exact silent failure
     * [PolylineCodec]'s KDoc warns about, so the two providers are not allowed to share
     * a code path here even though the shapes look similar.
     */
    private fun parseGraphHopperResponse(body: String): RouteGeometry? {
        val root = Json.parseObject(body)
        val paths = root["paths"] as? List<*> ?: return null
        val first = paths.firstOrNull() as? Map<*, *> ?: return null
        val geometry = first["points"] as? String ?: return null
        val points = PolylineCodec.decode(geometry, precision = 5)
        if (points.size < 2) return null
        return RouteGeometry(
            points = points,
            distanceMetres = (first["distance"] as? Number)?.toDouble() ?: 0.0,
            // GraphHopper reports time in milliseconds; every other figure in this
            // package is in seconds, and mixing units here is an easy 1000x bug.
            durationSeconds = ((first["time"] as? Number)?.toDouble() ?: 0.0) / 1000.0,
            provider = RouteProvider.GRAPH_HOPPER
        )
    }
}
