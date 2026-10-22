package app.tuneroam.radio.data.local

import app.tuneroam.radio.core.RadioJson
import app.tuneroam.radio.data.remote.BundledCatalog
import app.tuneroam.radio.domain.model.SnapshotInfo
import app.tuneroam.radio.domain.model.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * The downloaded station snapshot, kept in the station cache under its own bucket
 * with a small record of when it was taken. Until one exists, [stations] is the
 * snapshot packed into the APK.
 */
class SnapshotStore(
    private val database: TuneRoamDatabase,
    private val cache: CatalogCache,
) {
    private data class Held(val stations: List<Station>, val downloadedAtMs: Long?)

    private val held: MutableStateFlow<Held> by lazy { MutableStateFlow(load()) }

    fun stations(): List<Station> = held.value.stations

    fun observe(): Flow<SnapshotInfo> =
        flow { emitAll(held) }
            .map { SnapshotInfo(downloadedAtMs = it.downloadedAtMs, stationCount = it.stations.size) }
            .flowOn(Dispatchers.IO)

    fun replace(stations: List<Station>, downloadedAtMs: Long) {
        database.transaction {
            cache.writeStations(BUCKET, stations)
            database.catalogQueries.upsertSetting(
                META_KEY,
                RadioJson.encodeToString(Meta.serializer(), Meta(downloadedAtMs)),
            )
        }
        held.value = Held(stations, downloadedAtMs)
    }

    private fun load(): Held {
        val downloaded = cache.readStations(BUCKET)
        val meta = database.catalogQueries.selectSetting(META_KEY).executeAsOneOrNull()
            ?.let { runCatching { RadioJson.decodeFromString(Meta.serializer(), it) }.getOrNull() }
        return if (downloaded.isNotEmpty() && meta != null) {
            Held(downloaded, meta.downloadedAtMs)
        } else {
            Held(BundledCatalog.all(), null)
        }
    }

    @Serializable
    private data class Meta(val downloadedAtMs: Long)

    private companion object {
        const val BUCKET = "snapshot"
        const val META_KEY = "snapshot"
    }
}
