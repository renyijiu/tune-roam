package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.catalog.WorldDial
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.valueOrEmpty
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.repository.RadioCatalog
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [index] is the dial stop under the needle. A shuffled [station] can come from a
 * country with no stop on the dial; the dial then stays where it was.
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
    private val reachability: ReachabilityRepository,
    private val randomIndex: (Int) -> Int = { count -> kotlin.random.Random.nextInt(count) },
) : ViewModel() {
    private val _state = MutableStateFlow(RoamUiState())
    val state: StateFlow<RoamUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    /** Upcoming shuffle picks, drawn from one random batch of the live catalog. */
    private val deck = ArrayDeque<Station>()

    init {
        load(play = false)
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

    fun shuffle() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (deck.isEmpty()) {
                _state.update { it.copy(loading = true, empty = false) }
                deck += refill()
            }
            val pick = deck.removeFirstOrNull()
            _state.update { current ->
                current.copy(
                    index = pick?.let { WorldDial.indexOfCode(it.countryCode) } ?: current.index,
                    station = pick,
                    loading = false,
                    empty = pick == null,
                )
            }
            pick?.let(commands::play)
        }
    }

    /**
     * The batch is already random. Stations that failed on this phone move to the
     * back, so a mainland network is less likely to land on a dead stream first.
     */
    private suspend fun refill(): List<Station> {
        val scores = reachability.scores()
        return catalog.random(DECK_SIZE).valueOrEmpty()
            .filter { it.hasStream }
            .sortedBy { station -> scores[station.stationUuid]?.let { !it.lastOk } == true }
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

    private companion object {
        const val DECK_SIZE = 30
    }
}
