package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.model.AppSettings
import app.tuneroam.radio.domain.model.RefreshInterval
import app.tuneroam.radio.domain.model.RoamFilter
import app.tuneroam.radio.domain.model.SnapshotInfo
import app.tuneroam.radio.domain.model.SnapshotResult
import app.tuneroam.radio.domain.playback.RoamDeck
import app.tuneroam.radio.domain.repository.SettingsRepository
import app.tuneroam.radio.domain.repository.SnapshotRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The result of the last "update now" tap, for the line under the button. */
sealed interface SnapshotStatus {
    data object Idle : SnapshotStatus

    data object Updating : SnapshotStatus

    data class Done(val result: SnapshotResult) : SnapshotStatus
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val deck: RoamDeck,
    private val snapshot: SnapshotRepository,
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val snapshotInfo: StateFlow<SnapshotInfo?> = snapshot.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _snapshotStatus = MutableStateFlow<SnapshotStatus>(SnapshotStatus.Idle)
    val snapshotStatus: StateFlow<SnapshotStatus> = _snapshotStatus.asStateFlow()

    fun setSnapshotRefresh(interval: RefreshInterval) {
        viewModelScope.launch { settings.update { it.copy(snapshotRefresh = interval) } }
    }

    fun setSnapshotWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { settings.update { it.copy(snapshotWifiOnly = wifiOnly) } }
    }

    fun refreshSnapshotNow() {
        if (_snapshotStatus.value == SnapshotStatus.Updating) return
        viewModelScope.launch {
            _snapshotStatus.value = SnapshotStatus.Updating
            _snapshotStatus.value = SnapshotStatus.Done(snapshot.refresh())
        }
    }

    fun setMinBitrate(kbps: Int) = updateFilter { it.copy(minBitrate = kbps) }

    fun toggleLanguage(id: String) = updateFilter { it.copy(languages = it.languages.toggled(id)) }

    /** Wanting a genre and excluding it at once makes no sense; choosing one clears the other. */
    fun toggleTag(id: String) = updateFilter {
        it.copy(tags = it.tags.toggled(id), excludedTags = it.excludedTags - id)
    }

    fun toggleExcludedTag(id: String) = updateFilter {
        it.copy(excludedTags = it.excludedTags.toggled(id), tags = it.tags - id)
    }

    fun clearFilter() = updateFilter { RoamFilter() }

    fun setLevelVolume(enabled: Boolean) {
        viewModelScope.launch { settings.update { it.copy(levelVolume = enabled) } }
    }

    private fun updateFilter(transform: (RoamFilter) -> RoamFilter) {
        viewModelScope.launch {
            settings.update { it.copy(filter = transform(it.filter)) }
            // Start probing stations that match the new filter before the next tap.
            deck.warmUp()
        }
    }
}

private fun Set<String>.toggled(id: String): Set<String> = if (id in this) this - id else this + id
