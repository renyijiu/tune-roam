package app.tuneroam.radio.data.remote

import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.SearchField
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.respecting

/**
 * Offline copy of a Radio Browser pull: popular stations plus Chinese-language
 * stations from the mainland, Hong Kong, Taiwan, and Singapore.
 * Refreshed in the SQL cache whenever a live request succeeds.
 */
object BundledCatalog {
    private val stations: List<Station> by lazy { RadioPayloads.stations(BUNDLED_SNAPSHOT_JSON) }

    fun all(): List<Station> = stations

    fun popular(limit: Int, hideBroken: Boolean): List<Station> =
        stations.respecting(hideBroken).sortedByDescending { it.clickCount }.take(limit)

    fun search(query: CatalogQuery): List<Station> {
        val typed = query.text.trim()
        val filtered = stations.filter { station ->
            val fieldOk = when (query.field) {
                SearchField.NAME -> typed.isEmpty() || station.name.contains(typed, ignoreCase = true)
                SearchField.TAG -> typed.isEmpty() || station.tagList.any { it.equals(typed, ignoreCase = true) }
                SearchField.COUNTRY -> matchesCountry(station, typed)
            }
            val countryOk = query.country.isNullOrBlank() || matchesCountry(station, query.country)
            fieldOk && countryOk
        }
        return filtered.respecting(query.hideBroken).sortedByDescending { it.clickCount }.take(query.limit)
    }

    fun countries(): List<Country> =
        stations
            .groupBy { it.country }
            .filter { it.key.isNotBlank() }
            .map { (name, list) ->
                Country(
                    name = name,
                    isoCode = list.first().countryCode,
                    stationCount = list.size,
                )
            }
            .sortedByDescending { it.stationCount }

    fun random(limit: Int): List<Station> =
        stations.filter { it.hasStream }.shuffled().take(limit)

    fun inCountry(country: String, hideBroken: Boolean, limit: Int = 100): List<Station> =
        search(CatalogQuery(country = country, hideBroken = hideBroken, limit = limit))

    fun similar(station: Station): List<Station> {
        val tag = station.tagList.firstOrNull()
        val tagged = if (tag.isNullOrBlank()) {
            emptyList()
        } else {
            stations.filter { other ->
                other.stationUuid != station.stationUuid &&
                    other.tagList.any { it.equals(tag, ignoreCase = true) }
            }
        }
        val pool = tagged.ifEmpty {
            stations.filter { other ->
                other.stationUuid != station.stationUuid &&
                    (other.country.equals(station.country, ignoreCase = true) ||
                        (station.countryCode.isNotBlank() && other.countryCode.equals(station.countryCode, ignoreCase = true)) ||
                        sharesChinese(other, station))
            }
        }
        return pool.respecting(true).sortedByDescending { it.clickCount }.take(20)
    }

    private fun sharesChinese(station: Station, other: Station): Boolean {
        val chinese = listOf(station.language, other.language).all { it.contains("chinese", ignoreCase = true) }
        return chinese
    }

    private fun matchesCountry(station: Station, text: String): Boolean {
        val typed = text.trim()
        if (typed.isEmpty()) return true
        if (typed.length == 2 && typed.all { it.isLetter() }) {
            return station.countryCode.equals(typed, ignoreCase = true)
        }
        return station.country.equals(typed, ignoreCase = true) ||
            station.country.contains(typed, ignoreCase = true)
    }
}
