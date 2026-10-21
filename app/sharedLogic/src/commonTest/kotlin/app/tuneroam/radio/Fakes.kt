package app.tuneroam.radio

import app.tuneroam.radio.domain.catalog.ReachScore
import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.repository.RadioCatalog
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

    override suspend fun random(limit: Int): LoadResult<List<Station>> {
        randomCalls += 1
        return LoadResult.Fresh(random.take(limit))
    }

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

    override fun toggle() = Unit

    override fun pause() = Unit
}
