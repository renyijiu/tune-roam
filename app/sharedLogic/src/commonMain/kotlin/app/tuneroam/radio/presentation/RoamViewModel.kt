package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.catalog.WorldDial
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.valueOrEmpty
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.RoamDeck
import app.tuneroam.radio.domain.repository.RadioCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [index] is the dial stop under the needle. [station] follows whatever is playing,
 * so next, previous, or a list tap elsewhere turns the dial too. A station from a
 * country with no stop on the dial leaves the dial where it was.
 */
data class RoamUiState(
    val index: Int = WorldDial.homeIndex,
    val station: Station? = null,
    val loading: Boolean = true,
    val empty: Boolean = false,
)

class RoamViewModel(
    private val catalog: RadioCatalog,
    private val commands: PlaybackCommands,
    coordinator: PlaybackCoordinator,
    deck: RoamDeck,
    private val randomIndex: (Int) -> Int = { count -> kotlin.random.Random.nextInt(count) },
) : ViewModel() {
    private val _state = MutableStateFlow(RoamUiState())
    val state: StateFlow<RoamUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        // Have the next few stations probed before the first tap on "random".
        deck.warmUp()
        if (coordinator.snapshot.value.station == null) load(play = false)
        viewModelScope.launch {
            coordinator.snapshot
                .map { it.station }
                .filterNotNull()
                .distinctUntilChanged { old, new -> old.stationUuid == new.stationUuid }
                .collect { playing ->
                    loadJob?.cancel()
                    _state.update { current ->
                        current.copy(
                            index = WorldDial.indexOfCode(playing.countryCode) ?: current.index,
                            station = playing,
                            loading = false,
                            empty = false,
                        )
                    }
                }
        }
    }

    fun onDialSettled(index: Int) {
        val station = _state.value.station
        if (index == _state.value.index && station != null) {
            commands.play(station)
            return
        }
        _state.update { it.copy(index = index) }
        load(play = true)
    }

    fun land() {
        val station = _state.value.station
        if (station != null) commands.play(station) else load(play = true)
    }

    /** The playing station then comes back through the playback snapshot. */
    fun shuffle() {
        _state.update { it.copy(loading = true, empty = false) }
        commands.next()
    }

    private fun load(play: Boolean) {
        val stop = WorldDial.stops[_state.value.index]
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, empty = false) }
            // The catalog emits the cached list, then the live one. Keep the first pick so
            // the name on screen stays the station that is actually playing.
            var chosen: Station? = null
            catalog.stationsInCountry(stop.name, hideBroken = true).collect { result ->
                if (chosen != null) return@collect
                val playable = result.valueOrEmpty().filter { it.hasStream }
                chosen = playable.getOrNull(if (playable.isEmpty()) 0 else randomIndex(playable.size))
                _state.update { it.copy(station = chosen, loading = false, empty = chosen == null) }
                if (play) chosen?.let(commands::play)
            }
        }
    }
}
