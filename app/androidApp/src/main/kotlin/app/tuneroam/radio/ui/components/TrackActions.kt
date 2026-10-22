package app.tuneroam.radio.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.tuneroam.radio.R
import app.tuneroam.radio.domain.model.MusicService
import app.tuneroam.radio.ui.theme.Signal

/** Heart for saving the song title playing now. */
@Composable
fun SaveTrackButton(saved: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(if (saved) R.string.track_unsave else R.string.track_save),
            tint = if (saved) Signal else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Search icon with a menu of music services; picking one opens its search for [title]. */
@Composable
fun SearchTrackButton(title: String) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.track_search))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MusicService.entries.forEach { service ->
                DropdownMenuItem(
                    text = { Text(stringResource(service.label())) },
                    onClick = {
                        open = false
                        context.openUrl(service.searchUrl(title))
                    },
                )
            }
        }
    }
}

private fun MusicService.label(): Int = when (this) {
    MusicService.NETEASE -> R.string.service_netease
    MusicService.QQ_MUSIC -> R.string.service_qq
    MusicService.SPOTIFY -> R.string.service_spotify
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show()
    }
}
