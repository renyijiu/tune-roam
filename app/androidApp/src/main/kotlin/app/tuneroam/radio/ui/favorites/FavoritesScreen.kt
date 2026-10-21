package app.tuneroam.radio.ui.favorites

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.presentation.FavoritesViewModel
import app.tuneroam.radio.ui.components.EmptyNote
import app.tuneroam.radio.ui.components.StationRow
import org.koin.androidx.compose.koinViewModel

@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel = koinViewModel()) {
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.tab_favorites),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                style = MaterialTheme.typography.displaySmall,
            )
        }
        if (stations.isEmpty()) {
            item { EmptyNote(stringResource(R.string.empty_favorites)) }
        }
        items(stations, key = { it.stationUuid }) { station ->
            StationRow(
                station = station,
                favorite = true,
                onPlay = { viewModel.play(station) },
                onToggleFavorite = { viewModel.remove(station) },
            )
        }
    }
}
