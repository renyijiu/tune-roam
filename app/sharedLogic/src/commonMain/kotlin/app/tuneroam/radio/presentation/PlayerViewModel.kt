package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.model.StationLink
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.PlaybackPhase
import app.tuneroam.radio.domain.playback.PlaybackSnapshot
import app.tuneroam.radio.domain.playback.RoamDeck
import app.tuneroam.radio.domain.playback.SimilarStations
import app.tuneroam.radio.domain.playback.SleepTimer
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.HistoryRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
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
    OPENING_LINK,
    LINK_NOT_FOUND,
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
    deck: RoamDeck,
    private val history: HistoryRepository,
    private val catalog: RadioCatalog,
) : ViewModel() {
    val playback: StateFlow<PlaybackSnapshot> = coordinator.snapshot

    val sleepDeadlineMs: StateFlow<Long?> = sleepTimer.deadlineMs

    val canGoBack: StateFlow<Boolean> = deck.canGoBack

    val isFavorite: StateFlow<Boolean> = combine(playback, favorites.observeIds()) { snap, saved ->
        snap.station?.stationUuid in saved
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _hint = MutableStateFlow<PlayerHint?>(null)
    val hint: StateFlow<PlayerHint?> = _hint.asStateFlow()

    private var similarJob: Job? = null
    private var linkJob: Job? = null

    init {
        // Cold start: show the last station that played, ready to resume with one tap.
        if (coordinator.snapshot.value.station == null) {
            viewModelScope.launch { history.latest()?.let(coordinator::restore) }
        }
    }

    /** A restored station has not been opened yet, so play opens it instead of resuming. */
    fun togglePlay() {
        val snapshot = coordinator.snapshot.value
        val station = snapshot.station
        if (station != null && snapshot.phase == PlaybackPhase.IDLE) commands.play(station) else commands.toggle()
    }

    /** A `tuneroam://station/…` link. Plays the station once it is found. */
    fun openLink(link: String) {
        val uuid = StationLink.parse(link) ?: run {
            _hint.value = PlayerHint.LINK_NOT_FOUND
            return
        }
        linkJob?.cancel()
        linkJob = viewModelScope.launch {
            _hint.value = PlayerHint.OPENING_LINK
            val station = favorites.list().firstOrNull { it.stationUuid == uuid } ?: catalog.byUuid(uuid)
            if (station == null) {
                _hint.value = PlayerHint.LINK_NOT_FOUND
            } else {
                _hint.value = null
                commands.play(station)
            }
        }
    }

    fun shareText(): String? = coordinator.snapshot.value.station?.let(StationLink::shareText)

    fun next() = commands.next()

    fun previous() = commands.previous()

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
