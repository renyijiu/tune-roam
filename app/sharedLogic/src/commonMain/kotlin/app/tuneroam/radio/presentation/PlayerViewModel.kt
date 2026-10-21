package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.catalog.chooseNextStation
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.PlaybackSnapshot
import app.tuneroam.radio.domain.playback.SleepTimer
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PlayerHint {
    NO_SIMILAR,
}

class PlayerViewModel(
    private val coordinator: PlaybackCoordinator,
    private val favorites: FavoritesRepository,
    private val catalog: RadioCatalog,
    private val commands: PlaybackCommands,
    private val reachability: ReachabilityRepository,
    private val sleepTimer: SleepTimer,
) : ViewModel() {
    val sleepDeadlineMs: StateFlow<Long?> = sleepTimer.deadlineMs

    init {
        viewModelScope.launch {
            sleepTimer.deadlineMs.collect { deadline ->
                if (deadline == null) return@collect
                val wait = deadline - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                if (sleepTimer.consumeIfDue()) commands.pause()
            }
        }
    }

    fun setSleep(minutes: Int?) {
        if (minutes == null) sleepTimer.cancel() else sleepTimer.arm(minutes)
    }
    val playback: StateFlow<PlaybackSnapshot> = coordinator.snapshot

    private val favoriteIds = favorites.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isFavorite: StateFlow<Boolean> = combine(playback, favoriteIds) { snap, saved ->
        val id = snap.station?.stationUuid
        id != null && saved.any { it.stationUuid == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _hint = MutableStateFlow<PlayerHint?>(null)
    val hint: StateFlow<PlayerHint?> = _hint

    fun togglePlay() = commands.toggle()

    fun toggleFavorite() {
        val station = coordinator.snapshot.value.station ?: return
        viewModelScope.launch { favorites.toggle(station) }
    }

    fun playSimilar() {
        val station = coordinator.snapshot.value.station ?: return
        viewModelScope.launch {
            val pool = when (val result = catalog.similar(station)) {
                is LoadResult.Fresh -> result.value
                is LoadResult.Cached -> result.value
                is LoadResult.Failed -> emptyList()
            }
            val next = chooseNextStation(station, pool, emptySet(), reachability.scores())
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
