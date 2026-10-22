package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.catalog.ReachScore
import app.tuneroam.radio.domain.catalog.applyChinaFilter
import app.tuneroam.radio.domain.model.CatalogOrigin
import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.FailureReason
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.model.SearchField
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.observeIds
import app.tuneroam.radio.domain.repository.RadioCatalog
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import app.tuneroam.radio.domain.repository.StationConnectivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val query: String = "",
    val field: SearchField = SearchField.NAME,
    val hideBroken: Boolean = true,
    val chinaOnly: Boolean = false,
    val stations: List<Station> = emptyList(),
    val loading: Boolean = true,
    val probing: Boolean = false,
    val stale: Boolean = false,
    val origin: CatalogOrigin = CatalogOrigin.LIVE,
    val error: FailureReason? = null,
    val showingResults: Boolean = false,
)

class HomeViewModel(
    private val catalog: RadioCatalog,
    private val favorites: FavoritesRepository,
    private val commands: PlaybackCommands,
    private val reachability: ReachabilityRepository,
    private val connectivity: StationConnectivity,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    private val scores = MutableStateFlow<Map<String, ReachScore>>(emptyMap())
    val state: StateFlow<HomeUiState> = combine(_state, scores) { current, reach ->
        current.copy(stations = applyChinaFilter(current.stations, reach, current.chinaOnly))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val favoriteIds: StateFlow<Set<String>> = favorites.observeIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            reachability.observe().collect { scores.value = it }
        }
        loadPopular()
    }

    fun onChinaOnly(enabled: Boolean) {
        _state.update { it.copy(chinaOnly = enabled) }
    }

    fun probeVisible() {
        if (_state.value.probing) return
        val visible = applyChinaFilter(_state.value.stations, scores.value, _state.value.chinaOnly)
        val targets = (visible.ifEmpty { _state.value.stations }).take(PROBE_LIMIT)
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(probing = true) }
            // In parallel: one dead stream costs its full timeout, eight in a row felt stuck.
            targets.map { station ->
                async { reachability.note(station.stationUuid, connectivity.probe(station)) }
            }.awaitAll()
            _state.update { it.copy(probing = false) }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun onField(field: SearchField) {
        if (field == _state.value.field) return
        _state.update { it.copy(field = field) }
        // A typed query is already a search the listener means; rerun it under the new field.
        if (_state.value.query.isNotBlank()) search()
    }

    fun onHideBroken(hide: Boolean) {
        _state.update { it.copy(hideBroken = hide) }
        if (_state.value.showingResults) search() else loadPopular()
    }

    fun submit() {
        if (_state.value.query.isBlank()) {
            _state.update { it.copy(showingResults = false) }
            loadPopular()
        } else {
            search()
        }
    }

    fun retry() {
        if (_state.value.showingResults) search() else loadPopular()
    }

    fun play(station: Station) = commands.play(station)

    fun toggleFavorite(station: Station) {
        viewModelScope.launch { favorites.toggle(station) }
    }

    private fun loadPopular() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.stations.isEmpty(), error = null, showingResults = false) }
            catalog.popular(_state.value.hideBroken).collect { result ->
                _state.update { current -> current.withStations(result, showingResults = false) }
            }
        }
    }

    private fun search() {
        val snapshot = _state.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, showingResults = true) }
            catalog.search(
                CatalogQuery(
                    text = snapshot.query,
                    field = snapshot.field,
                    hideBroken = snapshot.hideBroken,
                ),
            ).collect { result ->
                _state.update { current -> current.withStations(result, showingResults = true) }
            }
        }
    }
}

private fun HomeUiState.withStations(
    result: LoadResult<List<Station>>,
    showingResults: Boolean,
): HomeUiState = when (result) {
    is LoadResult.Fresh -> copy(
        stations = result.value,
        loading = false,
        stale = false,
        origin = CatalogOrigin.LIVE,
        error = null,
        showingResults = showingResults,
    )
    is LoadResult.Cached -> copy(
        stations = result.value,
        loading = false,
        stale = result.stale,
        origin = result.origin,
        error = null,
        showingResults = showingResults,
    )
    is LoadResult.Failed -> copy(
        loading = false,
        error = result.reason,
        showingResults = showingResults,
    )
}

private const val PROBE_LIMIT = 8
