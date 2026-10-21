package app.tuneroam.radio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.FailureReason
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.repository.RadioCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CountriesUiState(
    val query: String = "",
    val countries: List<Country> = emptyList(),
    val loading: Boolean = true,
    val stale: Boolean = false,
    val error: FailureReason? = null,
)

class CountriesViewModel(
    private val catalog: RadioCatalog,
) : ViewModel() {
    private val _state = MutableStateFlow(CountriesUiState())
    val state: StateFlow<CountriesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.countries.isEmpty(), error = null) }
            catalog.countries().collect { result ->
                _state.update { current ->
                    when (result) {
                        is LoadResult.Fresh -> current.copy(
                            countries = result.value,
                            loading = false,
                            stale = false,
                            error = null,
                        )
                        is LoadResult.Cached -> current.copy(
                            countries = result.value,
                            loading = false,
                            stale = result.stale,
                            error = null,
                        )
                        is LoadResult.Failed -> current.copy(loading = false, error = result.reason)
                    }
                }
            }
        }
    }
}
