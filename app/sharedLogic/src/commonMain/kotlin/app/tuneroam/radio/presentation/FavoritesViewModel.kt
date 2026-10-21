package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val favorites: FavoritesRepository,
    private val commands: PlaybackCommands,
    history: HistoryRepository,
) : ViewModel() {
    val stations: StateFlow<List<Station>> = favorites.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<Station>> = history.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(station: Station) = commands.play(station)

    fun remove(station: Station) {
        viewModelScope.launch { favorites.remove(station.stationUuid) }
    }

    fun toggle(station: Station) {
        viewModelScope.launch { favorites.toggle(station) }
    }
}
