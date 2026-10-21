package app.tuneroam.radio.ui.country

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.tuneroam.radio.presentation.CountriesViewModel
import app.tuneroam.radio.ui.components.EmptyNote
import app.tuneroam.radio.ui.components.message
import org.koin.androidx.compose.koinViewModel

@Composable
fun CountriesScreen(
    onCountry: (String) -> Unit,
    viewModel: CountriesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val needle = state.query.trim()
    val visible = if (needle.isEmpty()) {
        state.countries
    } else {
        state.countries.filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.isoCode.contains(needle, ignoreCase = true)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = stringResource(R.string.tab_countries),
                    style = MaterialTheme.typography.displaySmall,
                )
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
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.field_country)) },
            )
        }
        if (state.stale) {
            item {
                Text(
                    text = stringResource(R.string.stale_cache),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
        if (state.loading && visible.isEmpty()) {
            item { EmptyNote(stringResource(R.string.loading)) }
        } else if (visible.isEmpty() && state.error == null) {
            item { EmptyNote(stringResource(R.string.empty_countries)) }
        }
        items(visible, key = { it.isoCode.ifBlank { it.name } }) { country ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCountry(country.name) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(country.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.country_count, country.stationCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (country.isoCode.isNotBlank()) {
                    Text(
                        text = country.isoCode,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
