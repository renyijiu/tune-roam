package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.model.AppSettings
import app.tuneroam.radio.domain.model.RoamFilter
import app.tuneroam.radio.domain.playback.RoamDeck
import app.tuneroam.radio.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val deck: RoamDeck,
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

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
