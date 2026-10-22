package app.tuneroam.radio.data.remote

import app.tuneroam.radio.core.epochMillis
import app.tuneroam.radio.core.runCancellable
import app.tuneroam.radio.domain.catalog.mirrorHosts
import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.RoamFilter
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.respecting
import app.tuneroam.radio.domain.model.toQueryMap
import app.tuneroam.radio.domain.repository.StationConnectivity
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RadioHttpException(val code: Int) : RuntimeException("HTTP $code")

interface MirrorMemory {
    fun read(): String?

    fun write(host: String, latencyMs: Long)
}

/**
 * Picks the Radio Browser mirror this network can reach.
 *
 * The remembered and built-in mirrors are probed at the same time and the first one
 * to answer wins, so a cold start waits for the fastest mirror rather than the slowest
 * timeout. `/json/servers` discovery only runs when none of those answer. Concurrent
 * callers share one selection.
 */
class ServerDirectory(
    private val http: HttpClient,
    private val memory: MirrorMemory,
    private val clock: () -> Long = ::epochMillis,
    private val knownHosts: List<String> = KNOWN_MIRRORS,
    private val probeTimeoutMs: Long = PROBE_TIMEOUT_MS,
) {
    private val lock = Mutex()
    private var cachedHost: String? = null
    private val skipped = mutableSetOf<String>()

    suspend fun current(): String {
        val host = lock.withLock { cachedHost ?: select().also { cachedHost = it } }
        return "https://$host"
    }

    /** Called after a request to [base] failed. The next [current] picks another mirror. */
    suspend fun invalidate(base: String) {
        val host = base.removePrefix("https://").removePrefix("http://").trimEnd('/')
        lock.withLock {
            skipped += host
            if (cachedHost == host) cachedHost = null
        }
    }

    private suspend fun select(): String {
        val remembered = memory.read()
        val all = mirrorHosts(remembered, knownHosts)
        // Every mirror failed once already: the network was probably down, so start over.
        if (all.all { it in skipped }) skipped.clear()
        val first = all.filter { it !in skipped }
        firstAnswering(first)?.let { return it }
        val discovered = discover().filter { it !in skipped && it !in first }
        firstAnswering(discovered)?.let { return it }
        // Nothing answered. Hand back a host anyway; the request reports the real failure.
        return first.firstOrNull() ?: knownHosts.first()
    }

    private suspend fun firstAnswering(hosts: List<String>): String? {
        if (hosts.isEmpty()) return null
        val winner = channelFlow {
            hosts.forEach { host ->
                launch {
                    val started = clock()
                    if (probe(host)) send(host to (clock() - started).coerceAtLeast(1))
                }
            }
        }.firstOrNull() ?: return null
        memory.write(winner.first, winner.second)
        return winner.first
    }

    private suspend fun discover(): List<String> {
        val found = mutableListOf<String>()
        for (url in BOOTSTRAP) {
            runCancellable {
                val response = http.get(url) { shortTimeout() }
                if (response.status.isSuccess()) found += RadioPayloads.serverNames(response.bodyAsText())
            }
        }
        return found.distinct()
    }

    private suspend fun probe(host: String): Boolean = runCancellable {
        http.get("https://$host/json/stats") { shortTimeout() }.status.isSuccess()
    }.getOrDefault(false)

    private fun HttpRequestBuilder.shortTimeout() {
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

    suspend fun random(limit: Int, filter: RoamFilter = RoamFilter()): List<Station> =
        getStations(
            "/json/stations/search",
            mapOf(
                "order" to "random",
                "limit" to limit.coerceAtLeast(1).toString(),
                "hidebroken" to "true",
            ) + filter.queryParameters(),
        ).filter(filter::matches)

    suspend fun byUuid(stationUuid: String): Station? =
        getStations("/json/stations/byuuid/${stationUuid.trim()}").firstOrNull()

    suspend fun countries(): List<Country> = get("/json/countries") { body ->
        RadioPayloads.countries(body)
    }

    suspend fun registerClick(stationUuid: String) {
        val base = servers.current()
        http.get("$base/json/url/${stationUuid.trim()}")
    }

    private suspend fun getStations(path: String, query: Map<String, String> = emptyMap()): List<Station> =
        get(path, query) { RadioPayloads.stations(it) }

    /**
     * Up to three mirrors. A network error or a 5xx moves on to the next mirror;
     * a 4xx is the request's fault and is thrown at once.
     */
    private suspend fun <T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        decode: (String) -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(3) {
            val base = servers.current()
            val outcome = runCancellable {
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
                decode(body)
            }
            outcome.onSuccess { return it }
            val error = outcome.exceptionOrNull()
            if (error is RadioHttpException && error.code in 400..499) throw error
            lastError = error
            servers.invalidate(base)
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
        // execute {} only reads the headers. A plain get() would buffer the body, and
        // most streams ignore Range and never end, so the probe would time out on a
        // station that is actually live.
        return runCancellable {
            http.prepareGet(url) {
                header(HttpHeaders.Range, "bytes=0-1")
                timeout {
                    requestTimeoutMillis = timeoutMs
                    connectTimeoutMillis = timeoutMs
                    socketTimeoutMillis = timeoutMs
                }
            }.execute { response -> response.status.value in 200..399 }
        }.getOrDefault(false)
    }
}
