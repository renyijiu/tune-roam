package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.catalog.WorldDial
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.repository.RadioCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoamUiState(
    val index: Int = WorldDial.indexOfCode("CN"),
    val station: Station? = null,
    val loading: Boolean = true,
    val empty: Boolean = false,
)

class RoamViewModel(
    private val catalog: RadioCatalog,
    private val commands: PlaybackCommands,
    private val randomIndex: (Int) -> Int = { count -> kotlin.random.Random.nextInt(count) },
) : ViewModel() {
    private val _state = MutableStateFlow(RoamUiState())
    val state: StateFlow<RoamUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(play = false)
    }

    fun onDialSettled(index: Int) {
        val same = index == _state.value.index
        _state.update { it.copy(index = index) }
        if (same && _state.value.station != null) {
            commands.play(_state.value.station!!)
        } else {
            load(play = true)
        }
    }

    fun land() {
        val station = _state.value.station
        if (station != null) commands.play(station) else load(play = true)
    }

    fun shuffle() {
        val pool = catalog.bundled().filter { it.url.isNotBlank() || it.urlResolved.isNotBlank() }
        if (pool.isEmpty()) return
        val pick = pool[randomIndex(pool.size)]
        _state.update {
            it.copy(
                index = WorldDial.indexOfCode(pick.countryCode),
                station = pick,
                loading = false,
                empty = false,
            )
        }
        commands.play(pick)
    }

    private fun load(play: Boolean) {
        val stop = WorldDial.stops[_state.value.index]
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, empty = false) }
            var played = false
            catalog.stationsInCountry(stop.name, hideBroken = true).collect { result ->
                val listed = when (result) {
                    is LoadResult.Fresh -> result.value
                    is LoadResult.Cached -> result.value
                    is LoadResult.Failed -> emptyList()
                }
                val playable = listed.filter { it.url.isNotBlank() || it.urlResolved.isNotBlank() }
                val fallback = playable.ifEmpty {
                    catalog.bundled().filter { station ->
                        station.country.equals(stop.name, ignoreCase = true) ||
                            station.countryCode.equals(stop.isoCode, ignoreCase = true)
                    }
                }
                val chosen = fallback.getOrNull(if (fallback.isEmpty()) 0 else randomIndex(fallback.size))
                _state.update { it.copy(station = chosen, loading = false, empty = chosen == null) }
                if (play && !played && chosen != null) {
                    played = true
                    commands.play(chosen)
                }
            }
        }
    }
}
