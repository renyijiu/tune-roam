package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.model.FailureReason
import app.tuneroam.radio.domain.model.LoadResult
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

data class CountryStationsUiState(
    val country: String,
    val hideBroken: Boolean = true,
    val stations: List<Station> = emptyList(),
    val loading: Boolean = true,
    val stale: Boolean = false,
    val error: FailureReason? = null,
)

class CountryStationsViewModel(
    country: String,
    private val catalog: RadioCatalog,
    private val favorites: FavoritesRepository,
    private val commands: PlaybackCommands,
) : ViewModel() {
    private val _state = MutableStateFlow(CountryStationsUiState(country = country))
    val state: StateFlow<CountryStationsUiState> = _state.asStateFlow()

    val favoriteIds: StateFlow<Set<String>> = favorites.observe()
        .map { stations -> stations.map { it.stationUuid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun onHideBroken(hide: Boolean) {
        _state.update { it.copy(hideBroken = hide) }
        refresh()
    }

    fun refresh() {
        val snapshot = _state.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.stations.isEmpty(), error = null) }
            catalog.stationsInCountry(snapshot.country, snapshot.hideBroken).collect { result ->
                _state.update { current ->
                    when (result) {
                        is LoadResult.Fresh -> current.copy(
                            stations = result.value,
                            loading = false,
                            stale = false,
                            error = null,
                        )
                        is LoadResult.Cached -> current.copy(
                            stations = result.value,
                            loading = false,
                            stale = result.stale,
                            error = null,
                        )
                        is LoadResult.Failed -> current.copy(loading = false, error = result.reason)
                    }
                }
            }
        }
    }

    fun play(station: Station) = commands.play(station)

    fun toggleFavorite(station: Station) {
        viewModelScope.launch { favorites.toggle(station) }
    }
}
