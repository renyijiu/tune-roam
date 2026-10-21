package app.tuneroam.radio.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * The player the media session sees. A radio stream is a single endless item, so
 * ExoPlayer itself offers no next or previous. This wrapper offers both and turns
 * them into "another station" and "the station before", which puts the buttons on
 * the notification, the lock screen, headsets, and car head units.
 */
@OptIn(UnstableApi::class)
internal class RoamingPlayer(
    player: Player,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*STATION_COMMANDS).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in STATION_COMMANDS || super.isCommandAvailable(command)

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() = onNext()

    override fun seekToNextMediaItem() = onNext()

    override fun seekToPrevious() = onPrevious()

    override fun seekToPreviousMediaItem() = onPrevious()

    private companion object {
        val STATION_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
    }
}
