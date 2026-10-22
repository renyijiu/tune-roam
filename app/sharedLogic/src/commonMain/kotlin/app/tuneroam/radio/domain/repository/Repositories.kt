package app.tuneroam.radio.domain.repository

import app.tuneroam.radio.domain.catalog.ReachScore
import app.tuneroam.radio.domain.model.AppSettings
import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.model.RoamFilter
import app.tuneroam.radio.domain.model.SavedTrack
import app.tuneroam.radio.domain.model.SnapshotInfo
import app.tuneroam.radio.domain.model.SnapshotResult
import app.tuneroam.radio.domain.model.Station
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface RadioCatalog {
    fun popular(hideBroken: Boolean, limit: Int = 40): Flow<LoadResult<List<Station>>>

    fun search(query: CatalogQuery): Flow<LoadResult<List<Station>>>

    fun countries(): Flow<LoadResult<List<Country>>>

    fun stationsInCountry(country: String, hideBroken: Boolean): Flow<LoadResult<List<Station>>>

    suspend fun similar(station: Station): LoadResult<List<Station>>

    /**
     * A random batch from the whole live catalog. Falls back to a shuffle of the
     * stations packed into the app when the live catalog cannot be reached.
     */
    suspend fun random(limit: Int, filter: RoamFilter = RoamFilter()): LoadResult<List<Station>>

    /** One station by uuid, for a shared link. Null when neither the live catalog nor the snapshot has it. */
    suspend fun byUuid(stationUuid: String): Station?

    suspend fun registerClick(stationUuid: String)
}

interface StationConnectivity {
    /** Short connect to the stream. True when this network got a response. */
    suspend fun probe(station: Station): Boolean
}

interface ReachabilityRepository {
    fun observe(): Flow<Map<String, ReachScore>>

    suspend fun scores(): Map<String, ReachScore>

    suspend fun note(stationUuid: String, ok: Boolean)
}

interface HistoryRepository {
    fun observe(): Flow<List<Station>>

    /** The station that last actually played, for picking up where the listener left off. */
    suspend fun latest(): Station?

    suspend fun record(station: Station)
}

interface FavoritesRepository {
    fun observe(): Flow<List<Station>>

    suspend fun list(): List<Station>

    suspend fun isFavorite(stationUuid: String): Boolean

    suspend fun toggle(station: Station): Boolean

    suspend fun remove(stationUuid: String)
}

/**
 * The offline station snapshot: downloaded now and then from the live catalog, used
 * when the live catalog cannot be reached.
 */
interface SnapshotRepository {
    fun observe(): Flow<SnapshotInfo>

    /** Downloads a new snapshot. A failed or short download keeps the current one. */
    suspend fun refresh(): SnapshotResult
}

interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface TrackRepository {
    fun observe(): Flow<List<SavedTrack>>

    /** Normalized keys of saved titles (see `TrackTitle.key`), for the heart on the player. */
    fun observeKeys(): Flow<Set<String>>

    /** Saves [title] heard on [station], or removes it when already saved. True when now saved. */
    suspend fun toggle(title: String, station: Station): Boolean

    suspend fun remove(id: Long)
}

/** Saved station ids, for the heart on every list row. */
fun FavoritesRepository.observeIds(): Flow<Set<String>> =
    observe().map { stations -> stations.mapTo(HashSet()) { it.stationUuid } }
