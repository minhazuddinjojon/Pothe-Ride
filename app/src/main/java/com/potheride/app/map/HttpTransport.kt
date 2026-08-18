package com.potheride.app.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one place this package touches the network.
 *
 * Every remote client below — OpenRouteService, GraphHopper, Nominatim — is written
 * against this interface, so each of them can be exercised in a JVM unit test by handing
 * it a [FakeHttpTransport] with a canned body. That is the whole point: response parsing
 * and the fallback chain are where the bugs live, and neither should need a network to
 * test.
 *
 * `HttpURLConnection` is used rather than OkHttp because it needs no new dependency (this
 * level may not edit `build.gradle.kts`) and the three calls here are plain GET/POST with
 * a JSON body. If the app later gains OkHttp for other reasons, only [UrlHttpTransport]
 * changes.
 */
interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}

data class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

/**
 * A response that was *received*. A non-2xx status is still a response; only a failure to
 * reach the server at all is an exception ([HttpTransportException]). Callers need that
 * distinction because a 429 from Nominatim means "back off", while an unreachable host
 * means "we are offline, use the fallback map".
 */
data class HttpResponse(
    val status: Int,
    val body: String
) {
    val isSuccessful: Boolean get() = status in 200..299
}

class HttpTransportException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Real implementation.
 *
 * Timeouts are short on purpose. The routing chain tries up to three providers in
 * sequence, so a generous per-request timeout multiplies: a 30-second connect timeout on
 * two failing providers is a minute of a blank screen before the straight-line fallback
 * appears, by which time the user has closed the app.
 */
class UrlHttpTransport(
    private val connectTimeoutMs: Int = 6_000,
    private val readTimeoutMs: Int = 8_000
) : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(request.url).openConnection() as HttpURLConnection)
        } catch (e: Exception) {
            throw HttpTransportException("could not open ${request.url}", e)
        }
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            request.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }

            if (request.body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            // On a non-2xx, `inputStream` throws and the useful diagnostics are on
            // `errorStream` instead — an easy way to lose the "quota exceeded" message
            // that explains the whole failure.
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResponse(status, body)
        } catch (e: HttpTransportException) {
            throw e
        } catch (e: Exception) {
            throw HttpTransportException("request to ${request.url} failed", e)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Test double. Records every request so tests can assert on rate limiting, caching and
 * which providers were actually consulted.
 *
 * Lives in `main` rather than `test` so screen previews and a future debug build can use
 * it to render the map with no network at all.
 */
class FakeHttpTransport(
    private val responder: (HttpRequest) -> HttpResponse
) : HttpTransport {

    private val _requests = mutableListOf<HttpRequest>()
    val requests: List<HttpRequest> get() = _requests.toList()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        _requests += request
        return responder(request)
    }

    companion object {
        /** Always fails to reach the host — the "aeroplane mode" double. */
        fun offline(): HttpTransport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse =
                throw HttpTransportException("offline")
        }
    }
}
