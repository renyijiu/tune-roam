package app.tuneroam.radio

import app.tuneroam.radio.data.http.installRadioBrowser
import app.tuneroam.radio.data.remote.MirrorMemory
import app.tuneroam.radio.data.remote.RadioBrowserClient
import app.tuneroam.radio.data.remote.ServerDirectory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ByUuidsTest {
    @Test
    fun asksForAHundredStationsPerRequest() = runTest {
        val asked = mutableListOf<List<String>>()
        val http = HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath == "/json/stations/byuuid") {
                    val uuids = request.url.parameters["uuids"].orEmpty().split(",")
                    asked += uuids
                    respond(uuids.joinToString(",", "[", "]") { """{"stationuuid":"$it","name":"$it","url":"http://s.test/$it"}""" })
                } else {
                    respond("{}")
                }
            },
        ) { installRadioBrowser() }
        val client = RadioBrowserClient(http, ServerDirectory(http, NoMemory, knownHosts = listOf("api.test")))
        val ids = List(150) { "id-$it" }

        val found = client.byUuids(ids + " id-0 " + "")

        assertEquals(listOf(100, 50), asked.map { it.size })
        assertEquals(ids, found.map { it.stationUuid })
    }

    private object NoMemory : MirrorMemory {
        override fun read(): String? = null

        override fun write(host: String, latencyMs: Long) = Unit
    }
}
