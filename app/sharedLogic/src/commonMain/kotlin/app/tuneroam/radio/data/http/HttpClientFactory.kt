package app.tuneroam.radio.data.http

import app.tuneroam.radio.core.AppIdentity
import app.tuneroam.radio.core.RadioJson
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

fun HttpClientConfig<*>.installRadioBrowser() {
    expectSuccess = false
    install(ContentNegotiation) {
        json(RadioJson)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
    defaultRequest {
        headers.append(HttpHeaders.UserAgent, AppIdentity.USER_AGENT)
        headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }
}
