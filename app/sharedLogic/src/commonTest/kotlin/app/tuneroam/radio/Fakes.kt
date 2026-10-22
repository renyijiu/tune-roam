package app.tuneroam.radio

import app.tuneroam.radio.domain.catalog.ReachScore
import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.AppSettings
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.model.RoamFilter
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.HistoryRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import app.tuneroam.radio.domain.repository.SettingsRepository
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import app.tuneroam.radio.domain.repository.StationConnectivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf

internal fun testStation(
    id: String,
    countryCode: String = "",
    country: String = "",
    tags: String = "",
) = Station(
    stationUuid = id,
    name = "Station $id",
    url = "http://example.test/$id",
    urlResolved = "http://example.test/$id",
    country = country,
    countryCode = countryCode,
    tags = tags,
    lastCheckOk = true,
)

internal class FakeCatalog(
    var similar: List<Station> = emptyList(),
    var random: List<Station> = emptyList(),
    /** Emitted in order: the first as the cached list, the rest as live lists. */
    var inCountry: List<List<Station>> = listOf(emptyList()),
) : RadioCatalog {
    var randomCalls = 0

    override fun popular(hideBroken: Boolean, limit: Int): Flow<LoadResult<List<Station>>> =
        flowOf(LoadResult.Fresh(emptyList()))

    override fun search(query: CatalogQuery): Flow<LoadResult<List<Station>>> =
        flowOf(LoadResult.Fresh(emptyList()))

    override fun countries(): Flow<LoadResult<List<Country>>> = flowOf(LoadResult.Fresh(emptyList()))

    override fun stationsInCountry(country: String, hideBroken: Boolean): Flow<LoadResult<List<Station>>> =
        inCountry.mapIndexed { index, stations ->
            if (index == 0) LoadResult.Cached(stations, stale = false) else LoadResult.Fresh(stations)
        }.asFlow()

    override suspend fun similar(station: Station): LoadResult<List<Station>> = LoadResult.Fresh(similar)

    var lastFilter = RoamFilter()

    override suspend fun random(limit: Int, filter: RoamFilter): LoadResult<List<Station>> {
        randomCalls += 1
        lastFilter = filter
        return LoadResult.Fresh(random.filter(filter::matches).take(limit))
    }

    override suspend fun byUuid(stationUuid: String): Station? =
        (similar + random + inCountry.flatten()).firstOrNull { it.stationUuid == stationUuid }

    override suspend fun registerClick(stationUuid: String) = Unit
}

internal class FakeReachability(
    initial: Map<String, ReachScore> = emptyMap(),
) : ReachabilityRepository {
    private val state = MutableStateFlow(initial)
    val noted = mutableListOf<Pair<String, Boolean>>()

    override fun observe(): Flow<Map<String, ReachScore>> = state

    override suspend fun scores(): Map<String, ReachScore> = state.value

    override suspend fun note(stationUuid: String, ok: Boolean) {
        noted += stationUuid to ok
        val old = state.value[stationUuid] ?: ReachScore()
        val updated = old.copy(
            successes = old.successes + if (ok) 1 else 0,
            failures = old.failures + if (ok) 0 else 1,
            lastOk = ok,
        )
        state.value = state.value + (stationUuid to updated)
    }
}

internal class FakeConnectivity(private val answering: Set<String>) : StationConnectivity {
    val probed = mutableListOf<String>()

    override suspend fun probe(station: Station): Boolean {
        probed += station.stationUuid
        return station.stationUuid in answering
    }
}

internal class RecordingCommands : PlaybackCommands {
    val played = mutableListOf<Station>()

    override fun play(station: Station) {
        played += station
    }

    var toggles = 0

    override fun toggle() {
        toggles += 1
    }

    override fun pause() = Unit

    var nexts = 0

    override fun next() {
        nexts += 1
    }

    override fun previous() = Unit
}

internal class FakeHistory(private val last: Station? = null) : HistoryRepository {
    override fun observe(): Flow<List<Station>> = flowOf(listOfNotNull(last))

    override suspend fun latest(): Station? = last

    override suspend fun record(station: Station) = Unit
}

internal class FakeFavorites(saved: List<Station> = emptyList()) : FavoritesRepository {
    private val state = MutableStateFlow(saved)

    override fun observe(): Flow<List<Station>> = state

    override suspend fun list(): List<Station> = state.value

    override suspend fun isFavorite(stationUuid: String): Boolean = state.value.any { it.stationUuid == stationUuid }

    override suspend fun toggle(station: Station): Boolean {
        val had = isFavorite(station.stationUuid)
        state.value = if (had) state.value.filter { it.stationUuid != station.stationUuid } else state.value + station
        return !had
    }

    override suspend fun remove(stationUuid: String) {
        state.value = state.value.filter { it.stationUuid != stationUuid }
    }
}

internal class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state

    override suspend fun current(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
