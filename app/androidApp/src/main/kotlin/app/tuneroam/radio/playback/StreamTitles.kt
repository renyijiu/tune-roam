package app.tuneroam.radio.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo

/**
 * The song a stream announces in one batch of timed [metadata], read from
 * `Player.Listener.onMetadata`.
 *
 * Not from `player.mediaMetadata`: ExoPlayer lays the current MediaItem's own metadata
 * over what the stream sent, and the item carries the station name as its title, so
 * the ICY title never shows through there.
 *
 * - An ICY block (`IcyInfo`) always answers: its StreamTitle, or "" when the station
 *   sent it empty (many do during ads or news), which clears the last song.
 * - Other metadata, such as ID3 frames in HLS, answers only when it names a title,
 *   joined to its artist as "artist - title" like an ICY title.
 * - Null means this batch says nothing about the song; keep the current one.
 */
@OptIn(UnstableApi::class)
internal fun streamTrackTitle(metadata: Metadata): String? {
    for (index in 0 until metadata.length()) {
        val entry = metadata.get(index)
        if (entry is IcyInfo) return entry.title?.trim().orEmpty()
    }
    val announced = MediaMetadata.Builder().populateFromMetadata(metadata).build()
    val title = announced.title?.toString()?.trim().orEmpty()
    if (title.isEmpty()) return null
    val artist = announced.artist?.toString()?.trim().orEmpty()
    return if (artist.isEmpty() || title.startsWith("$artist - ")) title else "$artist - $title"
}
