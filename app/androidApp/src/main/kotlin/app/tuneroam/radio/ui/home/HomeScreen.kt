package app.tuneroam.radio.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.domain.model.CatalogOrigin
import app.tuneroam.radio.domain.model.SearchField
import app.tuneroam.radio.presentation.HomeViewModel
import app.tuneroam.radio.ui.components.EmptyNote
import app.tuneroam.radio.ui.components.SettingsButton
import app.tuneroam.radio.ui.components.StationRow
import app.tuneroam.radio.ui.components.SwitchRow
import app.tuneroam.radio.ui.components.message
import app.tuneroam.radio.ui.theme.remoteTextEntry
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.weight(1f),
                    )
                    SettingsButton(onOpenSettings)
                }
                Text(
                    text = stringResource(R.string.tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .remoteTextEntry(OutlinedTextFieldDefaults.shape),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(state.field.hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { FieldPicker(selected = state.field, onSelect = viewModel::onField) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
                trailingIcon = {
                    TextButton(onClick = viewModel::submit) {
                        Text(stringResource(R.string.search))
                    }
                },
            )
        }
        item {
            SwitchRow(
                text = stringResource(R.string.hide_broken),
                checked = state.hideBroken,
                onCheckedChange = viewModel::onHideBroken,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            SwitchRow(
                text = stringResource(R.string.china_only),
                checked = state.chinaOnly,
                onCheckedChange = viewModel::onChinaOnly,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        item {
            TextButton(
                onClick = viewModel::probeVisible,
                modifier = Modifier.padding(horizontal = 12.dp),
                enabled = !state.probing && state.stations.isNotEmpty(),
            ) {
                Text(stringResource(if (state.probing) R.string.probing else R.string.probe_list))
            }
        }
        item {
            Text(
                text = stringResource(if (state.showingResults) R.string.results_heading else R.string.popular_heading),
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (state.stale) {
            item {
                Text(
                    text = stringResource(
                        if (state.origin == CatalogOrigin.BUNDLED) R.string.bundle_catalog else R.string.stale_cache,
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        state.error?.let { reason ->
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(text = reason.message(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        if (state.loading && state.stations.isEmpty()) {
            item { EmptyNote(stringResource(R.string.loading)) }
        } else if (state.stations.isEmpty() && state.error == null) {
            item {
                EmptyNote(
                    stringResource(
                        when {
                            state.chinaOnly -> R.string.empty_china
                            state.showingResults -> R.string.empty_search
                            else -> R.string.empty_popular
                        },
                    ),
                )
            }
        }
        items(state.stations, key = { it.stationUuid }) { station ->
            StationRow(
                station = station,
                favorite = station.stationUuid in favorites,
                onPlay = { viewModel.play(station) },
                onToggleFavorite = { viewModel.toggleFavorite(station) },
            )
        }
    }
}

/** Sits inside the search box so it reads as "search by this", not as a page switch. */
@Composable
private fun FieldPicker(selected: SearchField, onSelect: (SearchField) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(selected.label)
    val description = stringResource(R.string.search_by, label)
    Box {
        TextButton(
            onClick = { open = true },
            contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(label)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SearchField.entries.forEach { field ->
                DropdownMenuItem(
                    text = { Text(stringResource(field.label)) },
                    onClick = {
                        open = false
                        onSelect(field)
                    },
                    trailingIcon = if (field == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private val SearchField.label: Int
    get() = when (this) {
        SearchField.NAME -> R.string.field_name
        SearchField.TAG -> R.string.field_tag
        SearchField.COUNTRY -> R.string.field_country
    }

private val SearchField.hint: Int
    get() = when (this) {
        SearchField.NAME -> R.string.search_hint_name
        SearchField.TAG -> R.string.search_hint_tag
        SearchField.COUNTRY -> R.string.search_hint_country
    }
