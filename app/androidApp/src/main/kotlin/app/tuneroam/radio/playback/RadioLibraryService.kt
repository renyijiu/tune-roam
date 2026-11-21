package app.tuneroam.radio.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.SessionError
import app.tuneroam.radio.MainActivity
import app.tuneroam.radio.R
import app.tuneroam.radio.core.AppIdentity
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.EnginePhase
import app.tuneroam.radio.domain.playback.PlaybackEngine
import app.tuneroam.radio.domain.playback.PlaybackSession
import app.tuneroam.radio.domain.playback.StreamCandidate
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import app.tuneroam.radio.domain.repository.SettingsRepository
import app.tuneroam.radio.domain.repository.lookUp
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf

/**
 * Media3 library service: the Android shell around the shared [PlaybackSession].
 * It owns ExoPlayer, the notification, the media session and the browsable tree
 * (favorites, popular, countries), forwards ExoPlayer's events to the session, and
 * carries out what the session decides through [PlaybackEngine]. Fallback, hops,
 * next and previous, the watchdog and the sleep timer are all in the session, so an
 * iOS shell gets the same behaviour.
 *
 * All mutable state here is touched on the main thread only.
 */
@OptIn(UnstableApi::class)
class RadioLibraryService : MediaLibraryService() {
    private val catalog: RadioCatalog by inject()
    private val favorites: FavoritesRepository by inject()
    private val settings: SettingsRepository by inject()
    private val okHttpClient: OkHttpClient by inject()
    private val leveler = LoudnessLeveler(AudioMeter.Shared)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var session: PlaybackSession
    private var librarySession: MediaLibrarySession? = null

    /** Title last written into the current MediaItem, so an unchanged song is not republished. */
    private var publishedTitle: String? = null

    private val engine = object : PlaybackEngine {
        override fun open(station: Station, candidate: StreamCandidate) {
            publishedTitle = null
            player.setMediaItem(mediaItemFor(station, candidate))
            player.prepare()
            player.play()
        }

        override fun pause() = player.pause()

        override fun phase(): EnginePhase = when {
            player.isPlaying -> EnginePhase.PLAYING
            player.playWhenReady -> EnginePhase.BUFFERING
            else -> EnginePhase.PAUSED
        }
    }

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
        // The OkHttpClient the stream probes use, so playback and probes agree on what
        // answers. OkHttp follows http <-> https redirects by default.
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(AppIdentity.USER_AGENT)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(leveler))
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
        player = ExoPlayer.Builder(this, renderers)
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
        session = get { parametersOf(engine, serviceScope) }
        player.addListener(engineListener)

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_channel)
            .build()
        provider.setSmallIcon(R.drawable.ic_stat_wave)
        setMediaNotificationProvider(provider)

        val sessionPlayer = RoamingPlayer(player, onNext = session::skipToNext, onPrevious = session::skipToPrevious)
        librarySession = MediaLibrarySession.Builder(this, sessionPlayer, LibraryCallback())
            .setSessionActivity(sessionActivity())
            .build()

        serviceScope.launch {
            settings.observe().collect { leveler.enabled = it.levelVolume }
        }
    }

    /**
     * Play uses startForegroundService. Media3 posts the player notification only after
     * ExoPlayer reports a state, and a slow start (or a stream that stays buffering) blows
     * the foreground-service deadline and the system kills the process. Claim foreground
     * before Media3's own notification replaces this one.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            pauseAllPlayersAndStopSelf()
            return START_NOT_STICKY
        }
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
        serviceScope.cancel()
        librarySession?.release()
        librarySession = null
        if (::player.isInitialized) {
            player.removeListener(engineListener)
            player.release()
        }
        if (::session.isInitialized) session.close()
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

    /** Item for a station a controller is about to set on the player; the session picks the address. */
    private fun prepareStation(station: Station): MediaItem {
        publishedTitle = null
        return mediaItemFor(station, session.prepare(station))
    }

    private fun publishMetadata(icyTitle: String?) {
        if (!::player.isInitialized) return
        val station = session.snapshot.value.station ?: return
        val metadata = nowPlayingMetadata(station, icyTitle)
        val title = metadata.title?.toString()
        if (title == publishedTitle && player.mediaMetadata.artworkUri == metadata.artworkUri) return
        val index = player.currentMediaItemIndex
        val current = player.currentMediaItem ?: return
        publishedTitle = title
        player.replaceMediaItem(index, current.buildUpon().setMediaMetadata(metadata).build())
    }

    private val engineListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            session.onStreamFailed()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) session.onStreamFailed()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                session.onPlaying()
                publishMetadata(session.snapshot.value.icyTitle)
            } else if (player.playbackState == Player.STATE_READY) {
                session.onPaused()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            session.onPlayWhenReadyChanged(playWhenReady)
        }

        // The song comes from the stream's own metadata, not onMediaMetadataChanged: there
        // the MediaItem's title (the station name) hides it (see streamTrackTitle). A new
        // stream starts without one, as PlaybackCoordinator.begin clears it.
        override fun onMetadata(metadata: Metadata) {
            val title = streamTrackTitle(metadata) ?: return
            session.onStreamTitle(title)
            publishMetadata(session.snapshot.value.icyTitle)
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
            source.readStation()?.let { return Futures.immediateFuture(mutableListOf(prepareStation(it))) }
            // Legacy browsers (car head units, system media controls) send back only the
            // media id of a browsed station, without the station extras.
            val uuid = LibraryTree.stationUuid(source.mediaId) ?: return Futures.immediateFuture(mediaItems)
            val future = SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch {
                val station = runCatching { catalog.lookUp(uuid, favorites) }.getOrNull()
                if (station == null) {
                    future.setException(IllegalArgumentException("Unknown station $uuid"))
                } else {
                    future.set(mutableListOf(prepareStation(station)))
                }
            }
            return future
        }

        /**
         * Play pressed on the system media controls or a headset while the app was not
         * playing (also after a reboot): pick up the station on screen, else the last one
         * that played.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val station = session.resumable()
                if (station == null) {
                    future.setException(UnsupportedOperationException("Nothing has played yet."))
                } else {
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(listOf(prepareStation(station)), 0, C.TIME_UNSET),
                    )
                }
            }
            return future
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
                    LibraryResult.ofError(SessionError.ERROR_IO)
                }
                future.set(result)
            }
            return future
        }
    }

    companion object {
        /** Pauses and lets the service stop, notification and all: a TV leaving the app with Back. */
        const val ACTION_STOP = "app.tuneroam.radio.action.STOP"

        private const val CHANNEL_ID = "tuneroam_playback"

        /** Same id Media3's DefaultMediaNotificationProvider uses, so the real player notification replaces this one. */
        private const val MEDIA_NOTIFICATION_ID = 1001
    }
}

private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize <= 0) return this
    val from = page.toLong() * pageSize.toLong()
    if (from >= size) return emptyList()
    val start = from.toInt()
    return subList(start, minOf(size, start + pageSize))
}
