package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.PlaybackSnapshot
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
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
) : ViewModel() {
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
            when (val result = catalog.similar(station)) {
                is LoadResult.Fresh -> {
                    val next = result.value.firstOrNull()
                    if (next == null) _hint.value = PlayerHint.NO_SIMILAR else {
                        _hint.value = null
                        commands.play(next)
                    }
                }
                is LoadResult.Cached -> {
                    val next = result.value.firstOrNull()
                    if (next == null) _hint.value = PlayerHint.NO_SIMILAR else commands.play(next)
                }
                is LoadResult.Failed -> _hint.value = PlayerHint.NO_SIMILAR
            }
        }
    }

    fun dismissHint() {
        _hint.value = null
    }
}
