package app.tuneroam.radio.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import app.tuneroam.radio.MainActivity
import app.tuneroam.radio.R
import app.tuneroam.radio.core.AppIdentity
import app.tuneroam.radio.domain.playback.PlayDirective
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.PlaybackPhase
import app.tuneroam.radio.domain.playback.StreamFallback
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Media3 library service. Owns ExoPlayer, the notification, and the browsable
 * tree (favorites, popular, countries). Stream order and the start timeout live
 * in [PlaybackCoordinator], which a future iOS shell can drive the same way.
 */
@OptIn(UnstableApi::class)
class RadioLibraryService : MediaLibraryService() {
    private val coordinator: PlaybackCoordinator by inject()
    private val catalog: RadioCatalog by inject()
    private val favorites: FavoritesRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var librarySession: MediaLibrarySession? = null
    private var watchJob: Job? = null
    private var watchGeneration = 0
    private var failurePosted = false

    private val browser by lazy {
        LibraryBrowser(
            catalog = catalog,
            favorites = favorites,
            favoritesTitle = getString(R.string.tab_favorites),
            popularTitle = getString(R.string.popular_heading),
            countriesTitle = getString(R.string.tab_countries),
        )
    }

    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(AppIdentity.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(engineListener)

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_channel)
            .build()
        provider.setSmallIcon(R.drawable.ic_stat_wave)
        setMediaNotificationProvider(provider)

        librarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(sessionActivity())
            .build()
    }

    /**
     * Play uses startForegroundService. Media3 posts the player notification only after
     * ExoPlayer reports a state, and a slow start (or a stream that stays buffering) blows
     * the foreground-service deadline and the system kills the process. Claim foreground
     * before Media3's own notification replaces this one.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    private fun promoteToForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.playback_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.playback_channel_description)
                },
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wave)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.playback_channel_description))
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                MEDIA_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(MEDIA_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        watchJob?.cancel()
        serviceScope.cancel()
        librarySession?.release()
        librarySession = null
        if (::player.isInitialized) {
            player.removeListener(engineListener)
            player.release()
        }
        super.onDestroy()
    }

    private fun sessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun armWatchdog() {
        val generation = ++watchGeneration
        watchJob?.cancel()
        watchJob = serviceScope.launch {
            delay(StreamFallback.START_TIMEOUT_MS)
            if (generation != watchGeneration) return@launch
            if (player.playbackState == Player.STATE_READY) return@launch
            requestFailureAdvance()
        }
    }

    private fun requestFailureAdvance() {
        if (failurePosted) return
        val phase = coordinator.snapshot.value.phase
        if (phase == PlaybackPhase.FAILED || coordinator.snapshot.value.station == null) return
        failurePosted = true
        serviceScope.launch {
            failurePosted = false
            handle(coordinator.fail())
        }
    }

    private fun handle(directive: PlayDirective) {
        when (directive) {
            PlayDirective.Stay -> Unit
            is PlayDirective.Open -> {
                val station = coordinator.snapshot.value.station ?: return
                player.setMediaItem(mediaItemFor(station, directive.candidate))
                player.prepare()
                player.play()
                armWatchdog()
            }
            PlayDirective.Exhausted -> {
                watchJob?.cancel()
                watchGeneration++
                player.pause()
            }
        }
    }

    private val engineListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            requestFailureAdvance()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) requestFailureAdvance()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                watchGeneration++
                watchJob?.cancel()
                coordinator.onPlaying()
            } else if (player.playbackState == Player.STATE_READY) {
                coordinator.onPaused()
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            coordinator.onIcyTitle(mediaMetadata.title?.toString())
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ConnectionResult {
            val accepted = super.onConnect(session, controller)
            val playerCommands = accepted.availablePlayerCommands.buildUpon()
                .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_BACK)
                .remove(Player.COMMAND_SEEK_FORWARD)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                .remove(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .build()
            return ConnectionResult.accept(accepted.availableSessionCommands, playerCommands)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val source = mediaItems.firstOrNull() ?: return Futures.immediateFuture(mediaItems)
            val station = source.readStation() ?: return Futures.immediateFuture(mediaItems)
            val directive = coordinator.begin(station)
            serviceScope.launch(Dispatchers.IO) {
                runCatching { catalog.registerClick(station.stationUuid) }
            }
            val item = when (directive) {
                is PlayDirective.Open -> mediaItemFor(station, directive.candidate)
                else -> mediaItemFor(station, null)
            }
            armWatchdog()
            return Futures.immediateFuture(mutableListOf(item))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = folderItem(LibraryTree.ROOT, getString(R.string.app_name))
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch(Dispatchers.IO) {
                val result = try {
                    val items = this@RadioLibraryService.browser.children(parentId).page(page, pageSize)
                    LibraryResult.ofItemList(items, params)
                } catch (_: Throwable) {
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO)
                }
                future.set(result)
            }
            return future
        }
    }

    private companion object {
        const val CHANNEL_ID = "tuneroam_playback"

        /** Same id Media3's DefaultMediaNotificationProvider uses, so the real player notification replaces this one. */
        const val MEDIA_NOTIFICATION_ID = 1001
    }
}

private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize <= 0) return this
    val from = page.toLong() * pageSize.toLong()
    if (from >= size) return emptyList()
    val start = from.toInt()
    return subList(start, minOf(size, start + pageSize))
}
