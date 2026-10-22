package app.tuneroam.radio

import app.tuneroam.radio.data.http.installRadioBrowser
import app.tuneroam.radio.data.remote.MirrorMemory
import app.tuneroam.radio.data.remote.ServerDirectory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest

class ServerDirectoryTest {
    @Test
    fun firstMirrorToAnswerWinsAndIsRemembered() = runTest {
        val memory = FakeMirrorMemory()
        val directory = directory(memory) { host ->
            when (host) {
                "slow.test" -> { delay(400); true }
                "fast.test" -> true
                else -> false
            }
        }
        assertEquals("https://fast.test", directory.current())
        assertEquals("fast.test", memory.host)
    }

    @Test
    fun concurrentCallersShareOneSelection() = runTest {
        // Probes run on the mock engine's threads; StateFlow.update is atomic.
        val fastProbes = MutableStateFlow(0)
        val directory = directory(FakeMirrorMemory()) { host ->
            if (host == "fast.test") fastProbes.update { it + 1 }
            host == "fast.test"
        }
        val picked = List(5) { async { directory.current() } }.awaitAll()
        assertTrue(picked.all { it == "https://fast.test" })
        assertEquals(1, fastProbes.value)
    }

    @Test
    fun invalidatedMirrorIsSkippedUntilEveryMirrorFailed() = runTest {
        val directory = directory(FakeMirrorMemory()) { true }
        val first = directory.current()
        directory.invalidate(first)
        val second = directory.current()
        assertTrue(first != second)
        directory.invalidate(second)
        // Both known mirrors failed once: start over rather than give up.
        assertTrue(directory.current() in setOf("https://fast.test", "https://slow.test"))
    }

    private fun directory(memory: MirrorMemory, answers: suspend (String) -> Boolean): ServerDirectory {
        val engine = MockEngine { request ->
            val host = request.url.host
            if (request.url.encodedPath == "/json/stats" && answers(host)) {
                respond("{}")
            } else {
                respondError(HttpStatusCode.ServiceUnavailable)
            }
        }
        val http = HttpClient(engine) { installRadioBrowser() }
        return ServerDirectory(http, memory, knownHosts = listOf("slow.test", "fast.test"))
    }

    private class FakeMirrorMemory : MirrorMemory {
        var host: String? = null

        override fun read(): String? = host

        override fun write(host: String, latencyMs: Long) {
            this.host = host
        }
    }
}
