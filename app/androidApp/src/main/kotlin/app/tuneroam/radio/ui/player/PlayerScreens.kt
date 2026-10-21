package app.tuneroam.radio.ui.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.domain.playback.PlaybackNotice
import app.tuneroam.radio.domain.playback.PlaybackPhase
import app.tuneroam.radio.domain.playback.PlaybackSnapshot
import app.tuneroam.radio.domain.playback.StreamUrlKind
import app.tuneroam.radio.presentation.PlayerHint
import app.tuneroam.radio.presentation.PlayerViewModel
import app.tuneroam.radio.ui.components.HealthDot
import app.tuneroam.radio.ui.components.StationMark
import app.tuneroam.radio.ui.theme.Signal
import org.koin.androidx.compose.koinViewModel

@Composable
fun MiniPlayerBar(
    onOpen: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val station = playback.station ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StationMark(station, Modifier.size(40.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playback.icyTitle ?: station.country.ifBlank { stringResource(R.string.now_playing) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (playback.icyTitle != null) Signal else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlayPauseButton(playing = playback.phase == PlaybackPhase.PLAYING, onClick = viewModel::togglePlay)
    }
}

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val favorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val hint by viewModel.hint.collectAsStateWithLifecycle()
    val station = playback.station
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.titleMedium)
        }
        if (station == null) {
            Text(
                text = stringResource(R.string.empty_popular),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            return
        }
        val playing = playback.phase == PlaybackPhase.PLAYING
        val pulse = if (playing) pulseScale() else 1f
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = 12.dp)) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            )
            StationMark(station, Modifier.size(188.dp))
        }
        Text(
            text = station.name,
            modifier = Modifier.padding(top = 22.dp, start = 24.dp, end = 24.dp),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (station.country.isNotBlank()) {
            Text(
                text = station.country,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        playback.icyTitle?.let { title ->
            Text(
                text = title,
                modifier = Modifier.padding(top = 10.dp, start = 28.dp, end = 28.dp),
                color = Signal,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HealthDot(ok = station.lastCheckOk)
            Text(
                text = stringResource(if (station.lastCheckOk) R.string.health_ok else R.string.health_down),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val technical = station.technicalLabel()
        if (technical.isNotBlank() || station.tagList.isNotEmpty()) {
            Text(
                text = listOfNotNull(
                    technical.takeIf { it.isNotBlank() },
                    station.tagList.take(4).joinToString(" · ").takeIf { it.isNotBlank() },
                ).joinToString("   "),
                modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Notice(playback)
        if (hint == PlayerHint.NO_SIMILAR) {
            Text(
                text = stringResource(R.string.no_similar),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.padding(top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            IconButton(onClick = viewModel::toggleFavorite) {
                Icon(
                    imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(
                        if (favorite) R.string.favorite_remove else R.string.favorite_add,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            FilledIconButton(
                onClick = viewModel::togglePlay,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                    modifier = Modifier.size(36.dp),
                )
            }
            Box(Modifier.size(48.dp))
        }
        if (playback.notice == PlaybackNotice.UNAVAILABLE || playback.phase == PlaybackPhase.FAILED) {
            Button(
                onClick = viewModel::playSimilar,
                modifier = Modifier.padding(top = 18.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(stringResource(R.string.next_similar))
            }
        }
    }
}

@Composable
private fun Notice(playback: PlaybackSnapshot) {
    val text = when (playback.notice) {
        PlaybackNotice.TRYING_ALTERNATE -> stringResource(R.string.trying_alternate)
        PlaybackNotice.UNAVAILABLE -> stringResource(R.string.stream_unavailable)
        PlaybackNotice.NONE -> if (playback.activeKind == StreamUrlKind.ORIGINAL && playback.phase == PlaybackPhase.PLAYING) {
            stringResource(R.string.using_original_url)
        } else {
            null
        }
    } ?: return
    Text(
        text = text,
        modifier = Modifier.padding(top = 16.dp, start = 28.dp, end = 28.dp),
        color = if (playback.notice == PlaybackNotice.UNAVAILABLE) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.secondary
        },
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick) {
        Icon(
            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
        )
    }
}

@Composable
private fun pulseScale(): Float {
    val transition = rememberInfiniteTransition(label = "on-air")
    val scale by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "scale",
    )
    return scale
}
