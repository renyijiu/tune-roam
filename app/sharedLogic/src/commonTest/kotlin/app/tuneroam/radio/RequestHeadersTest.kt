package app.tuneroam.radio

import app.tuneroam.radio.core.AppIdentity
import app.tuneroam.radio.data.http.installRadioBrowser
import app.tuneroam.radio.data.remote.KtorStationConnectivity
import app.tuneroam.radio.data.remote.MirrorMemory
import app.tuneroam.radio.data.remote.RadioBrowserClient
import app.tuneroam.radio.data.remote.ServerDirectory
import app.tuneroam.radio.domain.model.Station
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RequestHeadersTest {
    @Test
    fun streamProbeDoesNotAskForJson() = runTest {
        val seen = mutableListOf<Headers>()
        val http = HttpClient(MockEngine { request -> seen += request.headers; respond("") }) { installRadioBrowser() }
        val station = Station(stationUuid = "a", name = "A", url = "", urlResolved = "http://stream.test/live")

        assertTrue(KtorStationConnectivity(http).probe(station))

        val headers = seen.single()
        assertTrue(headers.getAll(HttpHeaders.Accept).orEmpty().none { "json" in it }, "Accept: ${headers.getAll(HttpHeaders.Accept)}")
        assertEquals(AppIdentity.USER_AGENT, headers[HttpHeaders.UserAgent])
    }

    @Test
    fun catalogRequestsAskForJson() = runTest {
        val seen = mutableListOf<Headers>()
        val http = HttpClient(
            MockEngine { request ->
                seen += request.headers
                respond(if (request.url.encodedPath == "/json/stats") "{}" else "[]")
            },
        ) { installRadioBrowser() }
        val servers = ServerDirectory(http, NoMirrorMemory, knownHosts = listOf("api.test"))

        RadioBrowserClient(http, servers).countries()

        assertTrue(seen.isNotEmpty())
        seen.forEach { headers -> assertTrue(headers[HttpHeaders.Accept].orEmpty().contains("application/json")) }
    }

    private object NoMirrorMemory : MirrorMemory {
        override fun read(): String? = null

        override fun write(host: String, latencyMs: Long) = Unit
    }
}
