package app.tuneroam.radio

import app.tuneroam.radio.data.http.installRadioBrowser
import app.tuneroam.radio.data.remote.MirrorMemory
import app.tuneroam.radio.data.remote.RadioBrowserClient
import app.tuneroam.radio.data.remote.RadioHttpException
import app.tuneroam.radio.data.remote.ServerDirectory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class RegisterClickTest {
    @Test
    fun clickMovesToAnotherMirrorWhenOneFails() = runTest {
        val clicks = mutableListOf<String>()
        val client = client { host, path ->
            when {
                path == "/json/stats" -> respond("{}")
                path.startsWith("/json/url/") -> {
                    clicks += host
                    if (host == "down.test") respondError(HttpStatusCode.BadGateway) else respond("{\"ok\":true}")
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        client.registerClick(" station-1 ")

        assertEquals(listOf("down.test", "up.test"), clicks)
    }

    @Test
    fun unknownStationIsNotRetried() = runTest {
        var clicks = 0
        val client = client { _, path ->
            when {
                path == "/json/stats" -> respond("{}")
                path.startsWith("/json/url/") -> { clicks++; respondError(HttpStatusCode.NotFound) }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        assertFailsWith<RadioHttpException> { client.registerClick("gone") }
        assertEquals(1, clicks)
    }

    private fun client(
        answer: suspend MockRequestHandleScope.(host: String, path: String) -> HttpResponseData,
    ): RadioBrowserClient {
        val engine = MockEngine { request ->
            // Both mirrors answer the probe; down.test answers first, so it is picked first.
            if (request.url.encodedPath == "/json/stats" && request.url.host == "up.test") delay(300)
            answer(request.url.host, request.url.encodedPath)
        }
        val http = HttpClient(engine) { installRadioBrowser() }
        val servers = ServerDirectory(http, NoMemory, knownHosts = listOf("down.test", "up.test"))
        return RadioBrowserClient(http, servers)
    }

    private object NoMemory : MirrorMemory {
        override fun read(): String? = null

        override fun write(host: String, latencyMs: Long) = Unit
    }
}
