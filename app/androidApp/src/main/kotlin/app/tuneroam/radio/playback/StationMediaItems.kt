package app.tuneroam.radio.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.tuneroam.radio.core.RadioJson
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.StreamCandidate
import kotlinx.serialization.encodeToString

internal const val EXTRA_STATION_JSON = "app.tuneroam.radio.station"

internal fun nowPlayingMetadata(station: Station, icyTitle: String?): MediaMetadata {
    val icy = icyTitle?.trim()?.takeIf { it.isNotEmpty() && !it.equals(station.name, ignoreCase = true) }
    val title = icy ?: station.name
    val artist = if (icy == null) {
        station.country.ifBlank { station.language.ifBlank { null } }
    } else {
        station.name
    }
    val json = RadioJson.encodeToString(station)
    val extras = Bundle().apply { putString(EXTRA_STATION_JSON, json) }
    return MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(station.name)
        .setDisplayTitle(title)
        .setSubtitle(artist)
        .setStation(station.name)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
        .setArtworkUri(station.favicon.takeIf { it.startsWith("http") }?.let(Uri::parse))
        .setExtras(extras)
        .build()
}

internal fun mediaItemFor(station: Station, candidate: StreamCandidate?): MediaItem {
    val json = RadioJson.encodeToString(station)
    val extras = Bundle().apply { putString(EXTRA_STATION_JSON, json) }
    val uri = candidate?.url?.takeIf { it.isNotBlank() }
        ?: station.urlResolved.ifBlank { station.url }
    return MediaItem.Builder()
        .setMediaId(LibraryTree.station(station.stationUuid))
        .setUri(uri)
        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1f).build())
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
        .setMediaMetadata(nowPlayingMetadata(station, icyTitle = null))
        .build()
}

internal fun folderItem(mediaId: String, title: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        )
        .build()

internal fun MediaItem.readStation(): Station? {
    val json = requestMetadata.extras?.getString(EXTRA_STATION_JSON)
        ?: mediaMetadata.extras?.getString(EXTRA_STATION_JSON)
        ?: return null
    return runCatching { RadioJson.decodeFromString<Station>(json) }.getOrNull()
}
