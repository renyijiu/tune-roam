package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.FailureReason
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.model.SearchField
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val query: String = "",
    val field: SearchField = SearchField.NAME,
    val hideBroken: Boolean = true,
    val stations: List<Station> = emptyList(),
    val loading: Boolean = true,
    val stale: Boolean = false,
    val error: FailureReason? = null,
    val showingResults: Boolean = false,
)

class HomeViewModel(
    private val catalog: RadioCatalog,
    private val favorites: FavoritesRepository,
    private val commands: PlaybackCommands,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val favoriteIds: StateFlow<Set<String>> = favorites.observe()
        .map { stations -> stations.map { it.stationUuid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var loadJob: Job? = null

    init {
        loadPopular()
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun onField(field: SearchField) {
        _state.update { it.copy(field = field) }
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
        error = null,
        showingResults = showingResults,
    )
    is LoadResult.Cached -> copy(
        stations = result.value,
        loading = false,
        stale = result.stale,
        error = null,
        showingResults = showingResults,
    )
    is LoadResult.Failed -> copy(
        loading = false,
        error = result.reason,
        showingResults = showingResults,
    )
}
