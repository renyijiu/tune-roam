package app.tuneroam.radio.ui.country

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.presentation.CountryStationsViewModel
import app.tuneroam.radio.ui.components.EmptyNote
import app.tuneroam.radio.ui.components.StationRow
import app.tuneroam.radio.ui.components.message
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CountryStationsScreen(
    country: String,
    onBack: () -> Unit,
    viewModel: CountryStationsViewModel = koinViewModel { parametersOf(country) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Column(modifier = Modifier.padding(end = 16.dp)) {
                    Text(state.country, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.stations_heading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.hide_broken),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = state.hideBroken, onCheckedChange = viewModel::onHideBroken)
            }
        }
        if (state.stale) {
            item {
                Text(
                    text = stringResource(R.string.stale_cache),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        state.error?.let { reason ->
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(reason.message(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        if (state.loading && state.stations.isEmpty()) {
            item { EmptyNote(stringResource(R.string.loading)) }
        } else if (state.stations.isEmpty() && state.error == null) {
            item { EmptyNote(stringResource(R.string.empty_country)) }
        }
        items(state.stations, key = { "${it.stationUuid}-${it.name}" }) { station ->
            StationRow(
                station = station,
                favorite = station.stationUuid in favorites,
                onPlay = { viewModel.play(station) },
                onToggleFavorite = { viewModel.toggleFavorite(station) },
            )
        }
    }
}
