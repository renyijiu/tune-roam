package app.tuneroam.radio.data.http

import app.tuneroam.radio.core.AppIdentity
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

/**
 * One client serves both the Radio Browser API and the stream probes. Nothing here
 * may assume JSON: a probe asking a stream server for `application/json` can be
 * refused by a strict server and mark a live station as unreachable. API calls ask
 * for JSON themselves with [acceptJson]. Bodies are decoded by hand (see
 * `RadioPayloads`), so there is no content negotiation either.
 */
fun HttpClientConfig<*>.installRadioBrowser() {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
    defaultRequest {
        headers.append(HttpHeaders.UserAgent, AppIdentity.USER_AGENT)
    }
}

/** For Radio Browser API requests only, never for a stream. */
fun HttpRequestBuilder.acceptJson() {
    accept(ContentType.Application.Json)
}
