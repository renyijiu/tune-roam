package app.tuneroam.radio.data.remote

import app.tuneroam.radio.domain.catalog.MirrorSample
import app.tuneroam.radio.domain.catalog.fastestMirror
import app.tuneroam.radio.domain.catalog.mirrorHosts
import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.respecting
import app.tuneroam.radio.domain.model.toQueryMap
import app.tuneroam.radio.domain.repository.StationConnectivity
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class RadioHttpException(val code: Int) : RuntimeException("HTTP $code")

interface MirrorMemory {
    fun read(): String?

    fun write(host: String, latencyMs: Long)
}

class ServerDirectory(
    private val http: HttpClient,
    private val memory: MirrorMemory,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val knownHosts: List<String> = KNOWN_MIRRORS,
    private val probeTimeoutMs: Long = PROBE_TIMEOUT_MS,
) {
    @Volatile
    private var cachedHost: String? = null

    private val skipped = mutableSetOf<String>()

    suspend fun current(): String {
        cachedHost?.let { return "https://$it" }
        val remembered = memory.read()
        val discovered = discover()
        val hosts = mirrorHosts(remembered, discovered, knownHosts).filter { it !in skipped }
        val samples = coroutineScope {
            hosts.map { host ->
                async {
                    val started = clock()
                    val ok = probe(host)
                    MirrorSample(host, if (ok) (clock() - started).coerceAtLeast(1) else null)
                }
            }.awaitAll()
        }
        val best = fastestMirror(samples)
            ?: remembered?.takeIf { it !in skipped }
            ?: knownHosts.first()
        val latency = samples.firstOrNull { it.host == best }?.latencyMs ?: 0L
        memory.write(best, latency)
        cachedHost = best
        return "https://$best"
    }

    fun invalidate(base: String) {
        val host = base.removePrefix("https://").removePrefix("http://").trimEnd('/')
        skipped += host
        if (cachedHost == host) cachedHost = null
    }

    private suspend fun discover(): List<String> {
        val found = mutableListOf<String>()
        for (url in BOOTSTRAP) {
            try {
                val response = http.get(url) { shortTimeout() }
                if (!response.status.isSuccess()) continue
                found += RadioPayloads.serverNames(response.bodyAsText())
            } catch (_: Throwable) {
                // The next bootstrap host may still answer.
            }
        }
        return found.distinct()
    }

    private suspend fun probe(host: String): Boolean = try {
        val response = http.get("https://$host/json/stats") { shortTimeout() }
        response.status.isSuccess()
    } catch (_: Throwable) {
        false
    }

    private fun io.ktor.client.request.HttpRequestBuilder.shortTimeout() {
        timeout {
            requestTimeoutMillis = probeTimeoutMs
            connectTimeoutMillis = probeTimeoutMs
            socketTimeoutMillis = probeTimeoutMs
        }
    }

    companion object {
        const val PROBE_TIMEOUT_MS = 2_500L
        val KNOWN_MIRRORS = listOf(
            "de1.api.radio-browser.info",
            "de2.api.radio-browser.info",
        )
        val BOOTSTRAP = KNOWN_MIRRORS.map { "https://$it/json/servers" }
    }
}

class RadioBrowserClient(
    private val http: HttpClient,
    private val servers: ServerDirectory,
) {
    suspend fun topClicked(limit: Int, hideBroken: Boolean): List<Station> {
        val fetch = if (hideBroken) (limit * 3).coerceAtMost(200) else limit
        val raw = getStations("/json/stations/topclick/$fetch")
        return raw.respecting(hideBroken).take(limit)
    }

    suspend fun search(query: CatalogQuery): List<Station> {
        val raw = getStations("/json/stations/search", query.toQueryMap())
        return raw.respecting(query.hideBroken).take(query.limit)
    }

    suspend fun countries(): List<Country> = get("/json/countries") { body ->
        RadioPayloads.countries(body)
    }

    suspend fun registerClick(stationUuid: String) {
        val base = servers.current()
        http.get("$base/json/url/${stationUuid.trim()}")
    }

    private suspend fun getStations(path: String, query: Map<String, String> = emptyMap()): List<Station> =
        get(path, query) { RadioPayloads.stations(it) }

    private suspend fun <T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        decode: (String) -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(3) {
            val base = servers.current()
            try {
                val response = http.get("$base$path") {
                    timeout {
                        requestTimeoutMillis = ServerDirectory.PROBE_TIMEOUT_MS * 4
                        connectTimeoutMillis = ServerDirectory.PROBE_TIMEOUT_MS
                        socketTimeoutMillis = ServerDirectory.PROBE_TIMEOUT_MS * 4
                    }
                    query.forEach { (key, value) -> parameter(key, value) }
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) throw RadioHttpException(response.status.value)
                return decode(body)
            } catch (error: Throwable) {
                lastError = error
                servers.invalidate(base)
            }
        }
        throw lastError ?: RadioHttpException(0)
    }
}

class KtorStationConnectivity(
    private val http: HttpClient,
    private val timeoutMs: Long = ServerDirectory.PROBE_TIMEOUT_MS,
) : StationConnectivity {
    override suspend fun probe(station: Station): Boolean {
        val url = station.urlResolved.ifBlank { station.url }.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        return try {
            val response = http.get(url) {
                header(HttpHeaders.Range, "bytes=0-1")
                timeout {
                    requestTimeoutMillis = timeoutMs
                    connectTimeoutMillis = timeoutMs
                    socketTimeoutMillis = timeoutMs
                }
            }
            response.status.value in 200..399
        } catch (_: Throwable) {
            false
        }
    }
}
