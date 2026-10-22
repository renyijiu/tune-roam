package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.model.SavedTrack
import app.tuneroam.radio.domain.model.TrackTitle
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.repository.TrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Saved song titles: the heart next to the now-playing title, and the tracks shelf. */
class TracksViewModel(
    private val tracks: TrackRepository,
    private val coordinator: PlaybackCoordinator,
) : ViewModel() {
    val saved: StateFlow<List<SavedTrack>> = tracks.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the title playing right now is saved. False while the station sends no title. */
    val currentSaved: StateFlow<Boolean> = combine(coordinator.snapshot, tracks.observeKeys()) { snap, keys ->
        snap.icyTitle?.let { TrackTitle.key(it) in keys } == true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleCurrent() {
        val snap = coordinator.snapshot.value
        val title = snap.icyTitle ?: return
        val station = snap.station ?: return
        viewModelScope.launch { tracks.toggle(title, station) }
    }

    fun remove(track: SavedTrack) {
        viewModelScope.launch { tracks.remove(track.id) }
    }
}
