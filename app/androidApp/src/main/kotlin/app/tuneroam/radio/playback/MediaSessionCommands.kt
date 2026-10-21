package app.tuneroam.radio.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.PlaybackCommands
import app.tuneroam.radio.domain.playback.StreamFallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android [PlaybackCommands]. Talks to [RadioLibraryService] through a Media3
 * controller so notification, lock-screen, and headset events stay on the same player.
 */
class MediaSessionCommands(
    context: Context,
) : PlaybackCommands {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller = CompletableDeferred<MediaController>()

    init {
        val token = SessionToken(appContext, ComponentName(appContext, RadioLibraryService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                try {
                    controller.complete(future.get())
                } catch (error: Throwable) {
                    controller.completeExceptionally(error)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    override fun play(station: Station) {
        scope.launch {
            val mediaController = controller.await()
            val first = StreamFallback.candidates(station.urlResolved, station.url).firstOrNull()
            mediaController.setMediaItem(mediaItemFor(station, first))
            mediaController.prepare()
            mediaController.play()
        }
    }

    override fun toggle() {
        scope.launch {
            val mediaController = controller.await()
            if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
        }
    }

    override fun pause() {
        scope.launch {
            controller.await().pause()
        }
    }

    override fun next() {
        scope.launch {
            controller.await().seekToNext()
        }
    }

    override fun previous() {
        scope.launch {
            controller.await().seekToPrevious()
        }
    }
}
