package app.tuneroam.radio.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuneroam.radio.R
import app.tuneroam.radio.domain.model.FailureReason
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.ui.fx.StationEmblem
import app.tuneroam.radio.ui.theme.Signal
import app.tuneroam.radio.ui.theme.focusRing
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun StationRow(
    station: Station,
    favorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusRing(RoundedCornerShape(12.dp), outset = (-2).dp)
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StationMark(station = station, modifier = Modifier.size(48.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                station.country.takeIf { it.isNotBlank() },
                station.technicalLabel().takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HealthDot(ok = station.lastCheckOk)
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(
                    if (favorite) R.string.favorite_remove else R.string.favorite_add,
                ),
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StationMark(station: Station, modifier: Modifier = Modifier) {
    val url = station.favicon.takeIf { it.startsWith("http") }
    val context = LocalContext.current
    val request = remember(url) { url?.let { ImageRequest.Builder(context).data(it).transformations(LogoPlate).crossfade(true).build() } }
    // The emblem stands in while the logo loads, and stays when there is none or it never arrives.
    var loaded by remember(url) { mutableStateOf(false) }
    val emblem by animateFloatAsState(if (loaded) 0f else 1f, tween(450), label = "emblem")
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (emblem > 0f) {
            StationEmblem(station.name, Modifier.matchParentSize().graphicsLayer { alpha = emblem })
        }
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun HealthDot(ok: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (ok) Signal else MaterialTheme.colorScheme.error),
    )
}

@Composable
fun FailureReason.message(): String = stringResource(
    when (this) {
        FailureReason.NETWORK -> R.string.error_network
        FailureReason.TIMEOUT -> R.string.error_timeout
        FailureReason.SERVER -> R.string.error_server
    },
)

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 28.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
    }
}
