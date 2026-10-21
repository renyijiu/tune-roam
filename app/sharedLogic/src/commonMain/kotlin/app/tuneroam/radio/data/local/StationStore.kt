package app.tuneroam.radio.data.local

import app.tuneroam.radio.core.RadioJson
import app.tuneroam.radio.data.remote.MirrorMemory
import app.tuneroam.radio.domain.catalog.ReachScore
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.HistoryRepository
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

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

class SqlMirrorMemory(
    private val database: TuneRoamDatabase,
) : MirrorMemory {
    override fun read(): String? = database.catalogQueries.selectMirror().executeAsOneOrNull()?.host

    override fun write(host: String, latencyMs: Long) {
        database.catalogQueries.upsertMirror(
            host = host,
            latencyMs = latencyMs,
            checkedAt = System.currentTimeMillis(),
        )
    }
}

class SqlReachabilityRepository(
    private val database: TuneRoamDatabase,
) : ReachabilityRepository {
    override fun observe(): Flow<Map<String, ReachScore>> =
        database.catalogQueries.selectReachability()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.associate { it.stationUuid to it.toScore() } }

    override suspend fun scores(): Map<String, ReachScore> = withContext(Dispatchers.IO) {
        database.catalogQueries.selectReachability().executeAsList().associate { it.stationUuid to it.toScore() }
    }

    override suspend fun note(stationUuid: String, ok: Boolean) {
        withContext(Dispatchers.IO) {
            val existing = database.catalogQueries.selectReachability().executeAsList()
                .firstOrNull { it.stationUuid == stationUuid }
            val successes = (existing?.successes ?: 0L) + if (ok) 1L else 0L
            val failures = (existing?.failures ?: 0L) + if (ok) 0L else 1L
            database.catalogQueries.upsertReachability(
                stationUuid = stationUuid,
                successes = successes,
                failures = failures,
                lastOk = if (ok) 1L else 0L,
                checkedAt = System.currentTimeMillis(),
            )
        }
    }
}

class SqlHistoryRepository(
    private val database: TuneRoamDatabase,
) : HistoryRepository {
    override fun observe(): Flow<List<Station>> =
        database.catalogQueries.selectHistory()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.mapNotNull { row -> row.payload.toStationOrNull() } }

    override suspend fun record(station: Station) {
        withContext(Dispatchers.IO) {
            database.transaction {
                database.catalogQueries.insertHistory(
                    stationUuid = station.stationUuid,
                    payload = RadioJson.encodeToString(station),
                    playedAt = System.currentTimeMillis(),
                )
                database.catalogQueries.trimHistory()
            }
        }
    }
}

private fun Reachability.toScore(): ReachScore = ReachScore(
    successes = successes.toInt(),
    failures = failures.toInt(),
    lastOk = lastOk != 0L,
)

private fun String.toStationOrNull(): Station? =
    runCatching { RadioJson.decodeFromString<Station>(this) }.getOrNull()

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
