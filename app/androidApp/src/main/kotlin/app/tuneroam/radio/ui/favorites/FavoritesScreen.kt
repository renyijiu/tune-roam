package app.tuneroam.radio.ui.favorites

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.presentation.FavoritesViewModel
import app.tuneroam.radio.ui.components.EmptyNote
import app.tuneroam.radio.ui.components.StationRow
import org.koin.androidx.compose.koinViewModel

private enum class Shelf { SAVED, RECENT }

@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel = koinViewModel()) {
    val saved by viewModel.stations.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    var shelf by rememberSaveable { mutableStateOf(Shelf.SAVED) }
    val stations = if (shelf == Shelf.SAVED) saved else recent
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                text = stringResource(if (shelf == Shelf.SAVED) R.string.tab_favorites else R.string.history_heading),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                style = MaterialTheme.typography.displaySmall,
            )
        }
        item {
            val labels = listOf(R.string.shelf_saved, R.string.shelf_recent)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                labels.forEachIndexed { index, label ->
                    val choice = if (index == 0) Shelf.SAVED else Shelf.RECENT
                    SegmentedButton(
                        selected = shelf == choice,
                        onClick = { shelf = choice },
                        shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                        icon = {},
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
        }
        if (stations.isEmpty()) {
            item {
                EmptyNote(
                    stringResource(if (shelf == Shelf.SAVED) R.string.empty_favorites else R.string.empty_history),
                )
            }
        }
        items(stations, key = { it.stationUuid }) { station ->
            StationRow(
                station = station,
                favorite = shelf == Shelf.SAVED || station.stationUuid in saved.map { it.stationUuid },
                onPlay = { viewModel.play(station) },
                onToggleFavorite = {
                    if (shelf == Shelf.SAVED) viewModel.remove(station) else viewModel.toggle(station)
                },
            )
        }
    }
}
