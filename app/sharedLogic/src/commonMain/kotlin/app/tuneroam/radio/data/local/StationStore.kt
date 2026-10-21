package app.tuneroam.radio.data.local

import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CatalogCache(
    private val database: TuneRoamDatabase,
) {
    fun readStations(bucket: String): List<Station> =
        database.catalogQueries.selectBucket(bucket).executeAsList().map { it.toStation() }

    fun writeStations(bucket: String, stations: List<Station>) {
        database.transaction {
            database.catalogQueries.deleteBucket(bucket)
            stations.forEachIndexed { index, station ->
                database.catalogQueries.insertCached(
                    bucket = bucket,
                    position = index.toLong(),
                    stationUuid = station.stationUuid,
                    name = station.name,
                    streamUrl = station.url,
                    resolvedUrl = station.urlResolved,
                    homepage = station.homepage,
                    favicon = station.favicon,
                    tags = station.tags,
                    country = station.country,
                    countryCode = station.countryCode,
                    languageName = station.language,
                    codec = station.codec,
                    bitrate = station.bitrate.toLong(),
                    hls = if (station.hls) 1L else 0L,
                    lastCheckOk = if (station.lastCheckOk) 1L else 0L,
                    clickCount = station.clickCount.toLong(),
                )
            }
        }
    }

    fun readCountries(): List<Country> =
        database.catalogQueries.selectCountries().executeAsList().map {
            Country(name = it.name, isoCode = it.isoCode, stationCount = it.stationCount.toInt())
        }

    fun writeCountries(countries: List<Country>) {
        database.transaction {
            database.catalogQueries.deleteCountries()
            countries.forEach { country ->
                database.catalogQueries.insertCountry(
                    name = country.name,
                    isoCode = country.isoCode,
                    stationCount = country.stationCount.toLong(),
                )
            }
        }
    }
}

class SqlFavoritesRepository(
    private val database: TuneRoamDatabase,
) : FavoritesRepository {
    override fun observe(): Flow<List<Station>> =
        database.catalogQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toStation() } }

    override suspend fun list(): List<Station> = withContext(Dispatchers.IO) {
        database.catalogQueries.selectAll().executeAsList().map { it.toStation() }
    }

    override suspend fun isFavorite(stationUuid: String): Boolean = withContext(Dispatchers.IO) {
        database.catalogQueries.selectById(stationUuid).executeAsOneOrNull() != null
    }

    override suspend fun toggle(station: Station): Boolean = withContext(Dispatchers.IO) {
        val existing = database.catalogQueries.selectById(station.stationUuid).executeAsOneOrNull()
        if (existing == null) {
            insert(station, addedAt = System.currentTimeMillis())
            true
        } else {
            database.catalogQueries.deleteById(station.stationUuid)
            false
        }
    }

    override suspend fun remove(stationUuid: String) {
        withContext(Dispatchers.IO) {
            database.catalogQueries.deleteById(stationUuid)
        }
    }

    private fun insert(station: Station, addedAt: Long) {
        database.catalogQueries.insertFavorite(
            stationUuid = station.stationUuid,
            name = station.name,
            streamUrl = station.url,
            resolvedUrl = station.urlResolved,
            homepage = station.homepage,
            favicon = station.favicon,
            tags = station.tags,
            country = station.country,
            countryCode = station.countryCode,
            languageName = station.language,
            codec = station.codec,
            bitrate = station.bitrate.toLong(),
            hls = if (station.hls) 1L else 0L,
            lastCheckOk = if (station.lastCheckOk) 1L else 0L,
            clickCount = station.clickCount.toLong(),
            addedAt = addedAt,
        )
    }
}

private fun Favorite.toStation(): Station = Station(
    stationUuid = stationUuid,
    name = name,
    url = streamUrl,
    urlResolved = resolvedUrl,
    homepage = homepage,
    favicon = favicon,
    tags = tags,
    country = country,
    countryCode = countryCode,
    language = languageName,
    codec = codec,
    bitrate = bitrate.toInt(),
    hls = hls != 0L,
    lastCheckOk = lastCheckOk != 0L,
    clickCount = clickCount.toInt(),
)

private fun CachedStation.toStation(): Station = Station(
    stationUuid = stationUuid,
    name = name,
    url = streamUrl,
    urlResolved = resolvedUrl,
    homepage = homepage,
    favicon = favicon,
    tags = tags,
    country = country,
    countryCode = countryCode,
    language = languageName,
    codec = codec,
    bitrate = bitrate.toInt(),
    hls = hls != 0L,
    lastCheckOk = lastCheckOk != 0L,
    clickCount = clickCount.toInt(),
)
