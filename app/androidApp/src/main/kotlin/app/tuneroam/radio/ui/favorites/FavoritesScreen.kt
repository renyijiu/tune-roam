package app.tuneroam.radio.ui.favorites

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.domain.model.SavedTrack
import app.tuneroam.radio.presentation.FavoritesViewModel
import app.tuneroam.radio.presentation.TracksViewModel
import app.tuneroam.radio.ui.components.EmptyNote
import app.tuneroam.radio.ui.components.SearchTrackButton
import app.tuneroam.radio.ui.components.StationRow
import org.koin.androidx.compose.koinViewModel

private enum class Shelf(val label: Int, val heading: Int, val empty: Int) {
    SAVED(R.string.shelf_saved, R.string.tab_favorites, R.string.empty_favorites),
    RECENT(R.string.shelf_recent, R.string.history_heading, R.string.empty_history),
    TRACKS(R.string.shelf_tracks, R.string.tracks_heading, R.string.empty_tracks),
}

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = koinViewModel(),
    tracksViewModel: TracksViewModel = koinViewModel(),
) {
    val saved by viewModel.stations.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val tracks by tracksViewModel.saved.collectAsStateWithLifecycle()
    var shelf by rememberSaveable { mutableStateOf(Shelf.SAVED) }
    val stations = when (shelf) {
        Shelf.SAVED -> saved
        Shelf.RECENT -> recent
        Shelf.TRACKS -> emptyList()
    }
    val isEmpty = if (shelf == Shelf.TRACKS) tracks.isEmpty() else stations.isEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                text = stringResource(shelf.heading),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                style = MaterialTheme.typography.displaySmall,
            )
        }
        item {
            val shelves = Shelf.entries
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                shelves.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = shelf == choice,
                        onClick = { shelf = choice },
                        shape = SegmentedButtonDefaults.itemShape(index, shelves.size),
                        icon = {},
                    ) {
                        Text(stringResource(choice.label))
                    }
                }
            }
        }
        if (isEmpty) {
            item { EmptyNote(stringResource(shelf.empty)) }
        }
        items(stations, key = { it.stationUuid }) { station ->
            StationRow(
                station = station,
                favorite = station.stationUuid in savedIds,
                onPlay = { viewModel.play(station) },
                onToggleFavorite = {
                    if (shelf == Shelf.SAVED) viewModel.remove(station) else viewModel.toggle(station)
                },
            )
        }
        if (shelf == Shelf.TRACKS) {
            items(tracks, key = { it.id }) { track ->
                TrackRow(track = track, onRemove = { tracksViewModel.remove(track) })
            }
        }
    }
}

@Composable
private fun TrackRow(track: SavedTrack, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.song,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val from = stringResource(R.string.track_from, track.stationName)
            Text(
                text = listOfNotNull(track.artist, from).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SearchTrackButton(track.title)
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.track_remove))
        }
    }
}
