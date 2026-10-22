package app.tuneroam.radio.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The player the media session sees. A radio stream is a single endless item, so
 * ExoPlayer itself offers no next or previous. This wrapper offers both and turns
 * them into "another station" and "the station before", which puts the buttons on
 * the notification, the lock screen, headsets, and car head units.
 *
 * The commands go into [getState], not just a getAvailableCommands override: the
 * session pushes every command change to its controllers, and a controller that was
 * told next is gone drops the app's "random" button without a word.
 */
@OptIn(UnstableApi::class)
internal class RoamingPlayer(
    player: Player,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State {
        val state = super.getState()
        return state.buildUpon()
            .setAvailableCommands(state.availableCommands.buildUpon().addAll(*STATION_COMMANDS).build())
            .build()
    }

    // With one item there is nowhere to seek, so these arrive with no target index.
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> =
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                onNext()
                Futures.immediateVoidFuture()
            }
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                onPrevious()
                Futures.immediateVoidFuture()
            }
            else -> super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }

    private companion object {
        val STATION_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
    }
}
