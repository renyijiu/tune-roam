package app.tuneroam.radio.data.remote

import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.respecting
import app.tuneroam.radio.domain.model.toQueryMap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

class RadioHttpException(val code: Int) : RuntimeException("HTTP $code")

class ServerDirectory(
    private val http: HttpClient,
    private val randomIndex: (Int) -> Int = { count -> kotlin.random.Random.nextInt(count) },
) {
    @Volatile
    private var cachedBase: String? = null

    suspend fun current(): String {
        cachedBase?.let { return it }
        val names = discover()
        val chosen = "https://${names[randomIndex(names.size)]}"
        cachedBase = chosen
        return chosen
    }

    private suspend fun discover(): List<String> {
        for (url in BOOTSTRAP) {
            try {
                val response = http.get(url)
                if (!response.status.isSuccess()) continue
                val names = RadioPayloads.serverNames(response.bodyAsText())
                if (names.isNotEmpty()) return names
            } catch (_: Throwable) {
                // Try the next published mirror.
            }
        }
        return listOf(FALLBACK_HOST)
    }

    private companion object {
        val BOOTSTRAP = listOf(
            "https://de1.api.radio-browser.info/json/servers",
            "https://nl1.api.radio-browser.info/json/servers",
            "https://at1.api.radio-browser.info/json/servers",
        )
        const val FALLBACK_HOST = "de1.api.radio-browser.info"
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
        val response = http.get("${servers.current()}$path") {
            query.forEach { (key, value) -> parameter(key, value) }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw RadioHttpException(response.status.value)
        return decode(body)
    }
}
