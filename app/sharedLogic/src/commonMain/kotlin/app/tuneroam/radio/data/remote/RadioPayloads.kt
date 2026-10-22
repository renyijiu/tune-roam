package app.tuneroam.radio.data.remote

import app.tuneroam.radio.core.RadioJson
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.Station
import kotlinx.serialization.Serializable

@Serializable
data class StationDto(
    val stationuuid: String = "",
    val name: String = "",
    val url: String = "",
    val url_resolved: String = "",
    val homepage: String = "",
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val countrycode: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Int = 0,
    val lastcheckok: Int = 0,
    val clickcount: Int = 0,
)

@Serializable
data class CountryDto(
    val name: String = "",
    val iso_3166_1: String = "",
    val stationcount: Int = 0,
)

@Serializable
data class ServerDto(
    val name: String = "",
    val ip: String? = null,
)

fun StationDto.toDomain(): Station = Station(
    stationUuid = stationuuid.trim(),
    name = name.trim().ifBlank { stationuuid.trim() },
    url = url,
    urlResolved = url_resolved,
    homepage = homepage,
    favicon = favicon,
    tags = tags,
    country = country,
    countryCode = countrycode,
    language = language,
    codec = codec,
    bitrate = bitrate,
    hls = hls == 1,
    lastCheckOk = lastcheckok == 1,
    clickCount = clickcount,
)

object RadioPayloads {
    fun stations(json: String): List<Station> =
        RadioJson.decodeFromString<List<StationDto>>(json)
            .map { it.toDomain() }
            .filter { it.stationUuid.isNotBlank() }
            // Lists key rows by uuid; a repeated station would crash a LazyColumn.
            .distinctBy { it.stationUuid }

    fun countries(json: String): List<Country> =
        RadioJson.decodeFromString<List<CountryDto>>(json)
            .map {
                Country(
                    name = it.name.trim(),
                    isoCode = it.iso_3166_1.trim(),
                    stationCount = it.stationcount,
                )
            }
            .filter { it.name.isNotBlank() }
            .sortedByDescending { it.stationCount }

    fun serverNames(json: String): List<String> =
        RadioJson.decodeFromString<List<ServerDto>>(json)
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}
