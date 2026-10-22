package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.PlaybackSnapshot
import app.tuneroam.radio.domain.playback.SimilarStations
import app.tuneroam.radio.domain.playback.SleepTimer
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.observeIds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PlayerHint {
    SEARCHING,
    NO_SIMILAR,
}

/**
 * Player screen state. The sleep timer is only armed here; the playback service
 * owns the countdown, so it still fires after this screen (or the whole UI) is gone.
 */
class PlayerViewModel(
    private val coordinator: PlaybackCoordinator,
    private val favorites: FavoritesRepository,
    private val commands: PlaybackCommands,
    private val similarStations: SimilarStations,
    private val sleepTimer: SleepTimer,
) : ViewModel() {
    val playback: StateFlow<PlaybackSnapshot> = coordinator.snapshot

    val sleepDeadlineMs: StateFlow<Long?> = sleepTimer.deadlineMs

    val isFavorite: StateFlow<Boolean> = combine(playback, favorites.observeIds()) { snap, saved ->
        snap.station?.stationUuid in saved
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _hint = MutableStateFlow<PlayerHint?>(null)
    val hint: StateFlow<PlayerHint?> = _hint.asStateFlow()

    private var similarJob: Job? = null

    fun togglePlay() = commands.toggle()

    fun setSleep(minutes: Int?) {
        if (minutes == null) sleepTimer.cancel() else sleepTimer.arm(minutes)
    }

    fun toggleFavorite() {
        val station = coordinator.snapshot.value.station ?: return
        viewModelScope.launch { favorites.toggle(station) }
    }

    fun playSimilar() {
        val station = coordinator.snapshot.value.station ?: return
        if (similarJob?.isActive == true) return
        similarJob = viewModelScope.launch {
            _hint.value = PlayerHint.SEARCHING
            val next = similarStations.findReachable(station)
            if (next == null) {
                _hint.value = PlayerHint.NO_SIMILAR
            } else {
                _hint.value = null
                commands.play(next)
            }
        }
    }

    fun dismissHint() {
        _hint.value = null
    }
}
