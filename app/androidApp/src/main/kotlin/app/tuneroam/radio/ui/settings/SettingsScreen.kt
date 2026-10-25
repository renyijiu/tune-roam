package app.tuneroam.radio.ui.settings

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.diagnostics.CrashLog
import app.tuneroam.radio.domain.model.GenreOption
import app.tuneroam.radio.domain.model.LanguageOption
import app.tuneroam.radio.domain.model.RefreshInterval
import app.tuneroam.radio.domain.model.RoamFilter
import app.tuneroam.radio.domain.model.SnapshotResult
import app.tuneroam.radio.presentation.SettingsViewModel
import app.tuneroam.radio.presentation.SnapshotStatus
import app.tuneroam.radio.ui.components.SwitchRow
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val filter = settings.filter
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
        }

        Section(R.string.settings_filter_heading)
        Note(stringResource(R.string.settings_filter_note))
        Label(R.string.settings_bitrate)
        Chips {
            RoamFilter.BITRATE_CHOICES.forEach { kbps ->
                FilterChip(
                    selected = filter.minBitrate == kbps,
                    onClick = { viewModel.setMinBitrate(kbps) },
                    label = {
                        Text(
                            if (kbps == 0) {
                                stringResource(R.string.settings_bitrate_any)
                            } else {
                                stringResource(R.string.settings_bitrate_min, kbps)
                            },
                        )
                    },
                )
            }
        }
        Label(R.string.settings_languages)
        Chips {
            LanguageOption.entries.forEach { option ->
                FilterChip(
                    selected = option.id in filter.languages,
                    onClick = { viewModel.toggleLanguage(option.id) },
                    label = { Text(stringResource(option.label())) },
                )
            }
        }
        Label(R.string.settings_genres)
        Chips {
            GenreOption.entries.forEach { option ->
                FilterChip(
                    selected = option.id in filter.tags,
                    onClick = { viewModel.toggleTag(option.id) },
                    label = { Text(stringResource(option.label())) },
                )
            }
        }
        Label(R.string.settings_excluded)
        Chips {
            GenreOption.entries.forEach { option ->
                FilterChip(
                    selected = option.id in filter.excludedTags,
                    onClick = { viewModel.toggleExcludedTag(option.id) },
                    label = { Text(stringResource(option.label())) },
                )
            }
        }
        if (filter.isActive) {
            TextButton(onClick = viewModel::clearFilter, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.settings_clear_filter))
            }
        }

        Section(R.string.settings_playback_heading)
        SwitchRow(
            text = stringResource(R.string.settings_level_volume),
            checked = settings.levelVolume,
            onCheckedChange = viewModel::setLevelVolume,
            style = MaterialTheme.typography.bodyLarge,
        )
        Note(stringResource(R.string.settings_level_volume_note))

        Section(R.string.settings_snapshot_heading)
        SnapshotSection(viewModel, settings.snapshotRefresh, settings.snapshotWifiOnly)

        Section(R.string.settings_crash_heading)
        CrashLogRow()

        Text(
            text = stringResource(R.string.settings_version, appVersion()),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SnapshotSection(viewModel: SettingsViewModel, interval: RefreshInterval, wifiOnly: Boolean) {
    val info by viewModel.snapshotInfo.collectAsStateWithLifecycle()
    val status by viewModel.snapshotStatus.collectAsStateWithLifecycle()
    Note(stringResource(R.string.settings_snapshot_note))
    info?.let { current ->
        val downloadedAt = current.downloadedAtMs
        Note(
            if (downloadedAt == null) {
                stringResource(R.string.settings_snapshot_bundled, current.stationCount)
            } else {
                stringResource(
                    R.string.settings_snapshot_downloaded,
                    DateUtils.getRelativeTimeSpanString(downloadedAt).toString(),
                    current.stationCount,
                )
            },
        )
    }
    Label(R.string.settings_snapshot_interval)
    Chips {
        RefreshInterval.entries.forEach { choice ->
            FilterChip(
                selected = interval == choice,
                onClick = { viewModel.setSnapshotRefresh(choice) },
                label = { Text(stringResource(choice.label())) },
            )
        }
    }
    SwitchRow(
        text = stringResource(R.string.settings_snapshot_wifi),
        checked = wifiOnly,
        onCheckedChange = viewModel::setSnapshotWifiOnly,
        enabled = interval != RefreshInterval.OFF,
        style = MaterialTheme.typography.bodyLarge,
    )
    val updating = status == SnapshotStatus.Updating
    TextButton(
        onClick = viewModel::refreshSnapshotNow,
        enabled = !updating,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Text(stringResource(if (updating) R.string.settings_snapshot_updating else R.string.settings_snapshot_now))
    }
    (status as? SnapshotStatus.Done)?.let { done ->
        Note(
            when (val result = done.result) {
                is SnapshotResult.Updated -> stringResource(R.string.settings_snapshot_updated, result.stationCount)
                is SnapshotResult.TooFew -> stringResource(R.string.settings_snapshot_too_few, result.stationCount)
                is SnapshotResult.Failed -> stringResource(R.string.settings_snapshot_failed)
            },
        )
    }
}

@Composable
private fun CrashLogRow() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.latest(context)) }
    val current = report
    if (current == null) {
        Note(stringResource(R.string.settings_crash_none))
        return
    }
    val shareTitle = stringResource(R.string.settings_crash_share)
    val time = DateUtils.getRelativeTimeSpanString(current.writtenAtMs).toString()
    Note(stringResource(R.string.settings_crash_last, time))
    Row(modifier = Modifier.padding(horizontal = 12.dp)) {
        TextButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, current.text)
                context.startActivity(Intent.createChooser(send, shareTitle))
            },
        ) {
            Text(shareTitle)
        }
        TextButton(
            onClick = {
                CrashLog.clear(context)
                report = null
            },
        ) {
            Text(stringResource(R.string.settings_crash_clear))
        }
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun Label(title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

private fun RefreshInterval.label(): Int = when (this) {
    RefreshInterval.OFF -> R.string.refresh_off
    RefreshInterval.DAILY -> R.string.refresh_daily
    RefreshInterval.WEEKLY -> R.string.refresh_weekly
    RefreshInterval.MONTHLY -> R.string.refresh_monthly
}

private fun LanguageOption.label(): Int = when (this) {
    LanguageOption.CHINESE -> R.string.lang_chinese
    LanguageOption.ENGLISH -> R.string.lang_english
    LanguageOption.JAPANESE -> R.string.lang_japanese
    LanguageOption.KOREAN -> R.string.lang_korean
    LanguageOption.FRENCH -> R.string.lang_french
    LanguageOption.SPANISH -> R.string.lang_spanish
    LanguageOption.GERMAN -> R.string.lang_german
    LanguageOption.PORTUGUESE -> R.string.lang_portuguese
    LanguageOption.ITALIAN -> R.string.lang_italian
    LanguageOption.RUSSIAN -> R.string.lang_russian
}

private fun GenreOption.label(): Int = when (this) {
    GenreOption.POP -> R.string.genre_pop
    GenreOption.ROCK -> R.string.genre_rock
    GenreOption.JAZZ -> R.string.genre_jazz
    GenreOption.CLASSICAL -> R.string.genre_classical
    GenreOption.ELECTRONIC -> R.string.genre_electronic
    GenreOption.HIPHOP -> R.string.genre_hiphop
    GenreOption.CHILL -> R.string.genre_chill
    GenreOption.OLDIES -> R.string.genre_oldies
    GenreOption.COUNTRY -> R.string.genre_country
    GenreOption.FOLK -> R.string.genre_folk
    GenreOption.NEWS -> R.string.genre_news
    GenreOption.TALK -> R.string.genre_talk
    GenreOption.SPORTS -> R.string.genre_sports
    GenreOption.RELIGIOUS -> R.string.genre_religious
}
