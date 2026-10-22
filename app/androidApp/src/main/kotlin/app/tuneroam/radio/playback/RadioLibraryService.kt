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
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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
import app.tuneroam.radio.MainActivity
import app.tuneroam.radio.R
import app.tuneroam.radio.core.AppIdentity
import app.tuneroam.radio.core.epochMillis
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.EnginePhase
import app.tuneroam.radio.domain.playback.PlayDirective
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.PlaybackNotice
import app.tuneroam.radio.domain.playback.PlaybackPhase
import app.tuneroam.radio.domain.playback.RoamDeck
import app.tuneroam.radio.domain.playback.SimilarStations
import app.tuneroam.radio.domain.playback.SleepTimer
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.HistoryRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import app.tuneroam.radio.domain.repository.SettingsRepository
import app.tuneroam.radio.domain.repository.lookUp
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Media3 library service. Owns ExoPlayer, the notification, and the browsable
 * tree (favorites, popular, countries). Stream order and the start timeout live
 * in [PlaybackCoordinator], the hop to a similar station in [SimilarStations], and
 * the sleep deadline in [SleepTimer]; a future iOS shell drives the same three.
 *
 * All mutable state here is touched on the main thread only.
 */
@OptIn(UnstableApi::class)
class RadioLibraryService : MediaLibraryService() {
    private val coordinator: PlaybackCoordinator by inject()
    private val catalog: RadioCatalog by inject()
    private val favorites: FavoritesRepository by inject()
    private val reachability: ReachabilityRepository by inject()
    private val history: HistoryRepository by inject()
    private val similarStations: SimilarStations by inject()
    private val sleepTimer: SleepTimer by inject()
    private val deck: RoamDeck by inject()
    private val settings: SettingsRepository by inject()
    private val leveler = LoudnessLeveler()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var librarySession: MediaLibrarySession? = null
    private var watchJob: Job? = null
    private var hopJob: Job? = null
    private var skipJob: Job? = null
    private var failurePosted = false
    private var autoHops = 0
    private val excludedIds = mutableSetOf<String>()
    private var notedStation: String? = null
    private var publishedTitle: String? = null
    private var pausedAtMs: Long? = null

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
        player.addListener(engineListener)

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_channel)
            .build()
        provider.setSmallIcon(R.drawable.ic_stat_wave)
        setMediaNotificationProvider(provider)

        val sessionPlayer = RoamingPlayer(player, onNext = ::skipToNext, onPrevious = ::skipToPrevious)
        librarySession = MediaLibrarySession.Builder(this, sessionPlayer, LibraryCallback())
            .setSessionActivity(sessionActivity())
            .build()

        serviceScope.launch {
            settings.observe().collect { leveler.enabled = it.levelVolume }
        }
        serviceScope.launch {
            // collectLatest: arming again (say 60 → 15 minutes) drops the old wait.
            sleepTimer.deadlineMs.collectLatest { deadline ->
                if (deadline == null) return@collectLatest
                delay((deadline - epochMillis()).coerceAtLeast(0))
                if (sleepTimer.consumeIfDue()) player.pause()
            }
        }
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

    /**
     * Reports the engine phase to [PlaybackCoordinator] once a second until the stream
     * plays. The coordinator owns the start timeout; a pause while buffering is not a
     * timeout.
     */
    private fun armWatchdog() {
        watchJob?.cancel()
        watchJob = serviceScope.launch {
            while (true) {
                delay(WATCHDOG_TICK_MS)
                val phase = when {
                    player.isPlaying -> EnginePhase.PLAYING
                    player.playWhenReady -> EnginePhase.BUFFERING
                    else -> EnginePhase.PAUSED
                }
                if (phase == EnginePhase.PLAYING) return@launch
                val directive = coordinator.onClock(phase)
                if (directive != PlayDirective.Stay) {
                    handle(directive)
                    return@launch
                }
            }
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
                pausedAtMs = null
                player.play()
                armWatchdog()
            }
            PlayDirective.Exhausted -> {
                watchJob?.cancel()
                player.pause()
                offerSimilar()
            }
        }
    }

    private fun offerSimilar() {
        if (autoHops >= MAX_AUTO_HOPS || hopJob?.isActive == true) return
        val failed = coordinator.snapshot.value.station ?: return
        autoHops += 1
        excludedIds += failed.stationUuid
        hopJob = serviceScope.launch {
            val next = similarStations.findReachable(failed, excludedIds.toSet()) ?: return@launch
            // The listener may have started something else while we probed.
            if (coordinator.snapshot.value.station?.stationUuid != failed.stationUuid) return@launch
            excludedIds += next.stationUuid
            deck.played(next, replacing = true)
            handle(coordinator.begin(next, PlaybackNotice.NEXT_SIMILAR))
        }
    }

    /** Next and previous from any controller: the app, notification, headset, or car. */
    private fun skipToNext() = skipTo { deck.next() }

    private fun skipToPrevious() = skipTo { deck.previous() }

    private fun skipTo(pick: suspend () -> Station?) {
        skipJob?.cancel()
        skipJob = serviceScope.launch {
            val station = pick() ?: return@launch
            resetForNewStation(station)
            handle(coordinator.begin(station))
        }
    }

    /**
     * Item for a station a controller is about to set on the player. The coordinator
     * picks the address and the watchdog starts here, as for [handle].
     */
    private fun prepareStation(station: Station): MediaItem {
        skipJob?.cancel()
        resetForNewStation(station)
        val directive = coordinator.begin(station)
        armWatchdog()
        return mediaItemFor(station, (directive as? PlayDirective.Open)?.candidate)
    }

    /** A station the listener chose (not an automatic hop): fresh hop budget, history, click count. */
    private fun resetForNewStation(station: Station) {
        autoHops = 0
        excludedIds.clear()
        hopJob?.cancel()
        notedStation = null
        publishedTitle = null
        pausedAtMs = null
        serviceScope.launch { deck.played(station) }
        serviceScope.launch(Dispatchers.IO) {
            runCatching { catalog.registerClick(station.stationUuid) }
        }
    }

    /**
     * A live stream resumed after a long pause would first play minutes-old buffer,
     * then hit a connection the server already dropped, and that error would look like
     * a dead station and trigger a hop. Reopen the station at the live edge instead.
     */
    private fun resumeAtLiveEdge() {
        val station = coordinator.snapshot.value.station ?: return
        failurePosted = false
        notedStation = null
        publishedTitle = null
        handle(coordinator.begin(station))
    }

    private fun rememberPlay(station: Station) {
        if (notedStation == station.stationUuid) return
        notedStation = station.stationUuid
        serviceScope.launch(Dispatchers.IO) {
            runCatching { reachability.note(station.stationUuid, ok = true) }
            runCatching { history.record(station) }
        }
    }

    private fun publishMetadata(icyTitle: String?) {
        if (!::player.isInitialized) return
        val station = coordinator.snapshot.value.station ?: return
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
            requestFailureAdvance()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) requestFailureAdvance()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                watchJob?.cancel()
                coordinator.onPlaying()
                coordinator.snapshot.value.station?.let(::rememberPlay)
                publishMetadata(coordinator.snapshot.value.icyTitle)
            } else if (player.playbackState == Player.STATE_READY) {
                coordinator.onPaused()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) {
                pausedAtMs = epochMillis()
                return
            }
            val pausedAt = pausedAtMs ?: return
            pausedAtMs = null
            if (epochMillis() - pausedAt >= LIVE_RESUME_AFTER_MS) {
                serviceScope.launch { resumeAtLiveEdge() }
            } else {
                // Time spent paused while buffering is not a slow start.
                coordinator.restartTimeout()
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            coordinator.onIcyTitle(mediaMetadata.title?.toString())
            publishMetadata(coordinator.snapshot.value.icyTitle)
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
                val station = coordinator.snapshot.value.station ?: runCatching { history.latest() }.getOrNull()
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
        const val MAX_AUTO_HOPS = 3
        const val WATCHDOG_TICK_MS = 1_000L

        /** Longer than this, a paused stream is reopened rather than resumed from its buffer. */
        const val LIVE_RESUME_AFTER_MS = 30_000L
    }
}

private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize <= 0) return this
    val from = page.toLong() * pageSize.toLong()
    if (from >= size) return emptyList()
    val start = from.toInt()
    return subList(start, minOf(size, start + pageSize))
}
