package app.tuneroam.radio.domain.playback

import app.tuneroam.radio.core.epochMillis
import app.tuneroam.radio.core.runCancellable
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.repository.HistoryRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The player a [PlaybackSession] drives: ExoPlayer on Android, AVPlayer in an iOS shell.
 * Called on the session's thread only.
 */
interface PlaybackEngine {
    /** Load [candidate] (one of [station]'s stream addresses) and start playing it. */
    fun open(station: Station, candidate: StreamCandidate)

    fun pause()

    /** What the engine is doing now, sampled by the start watchdog. */
    fun phase(): EnginePhase
}

/**
 * Everything that happens between "play this station" and sound, and after it fails:
 * the start watchdog, falling back to the station's other address, the automatic hop
 * to a similar station, next and previous, reopening at the live edge after a long
 * pause, the sleep timer, and the history, reachability and click-count bookkeeping.
 *
 * The shell owns the [engine] and reports its events here (`on…` methods); this class
 * decides what the engine does next. [PlaybackCoordinator] keeps the per-stream state
 * (which address, the start timeout, what to show); this class keeps what spans
 * streams (the hop budget, the stations already tried, the running jobs).
 *
 * Single-threaded: [scope] must run on one thread (Android: `Dispatchers.Main.immediate`),
 * and the shell calls in on that thread.
 */
class PlaybackSession(
    private val engine: PlaybackEngine,
    private val scope: CoroutineScope,
    private val coordinator: PlaybackCoordinator,
    private val catalog: RadioCatalog,
    private val reachability: ReachabilityRepository,
    private val history: HistoryRepository,
    private val similarStations: SimilarStations,
    private val deck: RoamDeck,
    private val sleepTimer: SleepTimer,
    private val clock: () -> Long = ::epochMillis,
) {
    private var watchJob: Job? = null
    private var hopJob: Job? = null
    private var skipJob: Job? = null
    private var failurePosted = false
    private var autoHops = 0
    private val excludedIds = mutableSetOf<String>()
    private var notedStation: String? = null
    private var pausedAtMs: Long? = null

    val snapshot: StateFlow<PlaybackSnapshot> get() = coordinator.snapshot

    init {
        scope.launch {
            // collectLatest: arming again (say 60 → 15 minutes) drops the old wait.
            sleepTimer.deadlineMs.collectLatest { deadline ->
                if (deadline == null) return@collectLatest
                delay((deadline - clock()).coerceAtLeast(0))
                if (sleepTimer.consumeIfDue(clock())) engine.pause()
            }
        }
    }

    /** Next from any controller: the app, notification, headset, or car. */
    fun skipToNext() = skipTo { deck.next() }

    fun skipToPrevious() = skipTo { deck.previous() }

    /**
     * A controller is about to put [station] on the engine itself (Android: a MediaItem
     * set through the media session). Returns the address to load, or null when the
     * station has none. The watchdog starts here, as when the session opens a stream.
     */
    fun prepare(station: Station): StreamCandidate? {
        skipJob?.cancel()
        resetForNewStation(station)
        val directive = coordinator.begin(station)
        armWatchdog()
        return (directive as? PlayDirective.Open)?.candidate
    }

    /** What play should pick up when nothing is loaded: the station on show, else the last one played. */
    suspend fun resumable(): Station? =
        coordinator.snapshot.value.station ?: runCancellable { history.latest() }.getOrNull()

    /** The engine reported an error, or a live stream ended: try the next address or give up. */
    fun onStreamFailed() {
        if (failurePosted) return
        val current = coordinator.snapshot.value
        if (current.phase == PlaybackPhase.FAILED || current.station == null) return
        failurePosted = true
        scope.launch {
            failurePosted = false
            handle(coordinator.fail())
        }
    }

    fun onPlaying() {
        watchJob?.cancel()
        coordinator.onPlaying()
        coordinator.snapshot.value.station?.let(::rememberPlay)
    }

    /** Paused with a stream loaded and ready, not merely buffering. */
    fun onPaused() {
        coordinator.onPaused()
    }

    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (!playWhenReady) {
            pausedAtMs = clock()
            return
        }
        val pausedAt = pausedAtMs ?: return
        pausedAtMs = null
        if (clock() - pausedAt >= LIVE_RESUME_AFTER_MS) {
            scope.launch { resumeAtLiveEdge() }
        } else {
            // Time spent paused while buffering is not a slow start.
            coordinator.restartTimeout()
        }
    }

    /** The song the stream announced; "" clears it. See `PlaybackCoordinator.onIcyTitle`. */
    fun onStreamTitle(title: String) {
        coordinator.onIcyTitle(title)
    }

    /** The shell is releasing its engine. The station stays on show, idle. */
    fun close() {
        watchJob?.cancel()
        hopJob?.cancel()
        skipJob?.cancel()
        coordinator.close()
    }

    /**
     * Reports the engine phase to [PlaybackCoordinator] once a second until the stream
     * plays. The coordinator owns the start timeout; a pause while buffering is not a
     * timeout.
     */
    private fun armWatchdog() {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (true) {
                delay(WATCHDOG_TICK_MS)
                val phase = engine.phase()
                if (phase == EnginePhase.PLAYING) return@launch
                val directive = coordinator.onClock(phase)
                if (directive != PlayDirective.Stay) {
                    handle(directive)
                    return@launch
                }
            }
        }
    }

    private fun handle(directive: PlayDirective) {
        when (directive) {
            PlayDirective.Stay -> Unit
            is PlayDirective.Open -> {
                val station = coordinator.snapshot.value.station ?: return
                pausedAtMs = null
                engine.open(station, directive.candidate)
                armWatchdog()
            }
            PlayDirective.Exhausted -> {
                watchJob?.cancel()
                engine.pause()
                offerSimilar()
            }
        }
    }

    private fun offerSimilar() {
        if (autoHops >= MAX_AUTO_HOPS || hopJob?.isActive == true) return
        val failed = coordinator.snapshot.value.station ?: return
        autoHops += 1
        excludedIds += failed.stationUuid
        hopJob = scope.launch {
            val next = similarStations.findReachable(failed, excludedIds.toSet()) ?: return@launch
            // The listener may have started something else while we probed.
            if (coordinator.snapshot.value.station?.stationUuid != failed.stationUuid) return@launch
            excludedIds += next.stationUuid
            deck.played(next, replacing = true)
            handle(coordinator.begin(next, PlaybackNotice.NEXT_SIMILAR))
        }
    }

    private fun skipTo(pick: suspend () -> Station?) {
        skipJob?.cancel()
        skipJob = scope.launch {
            val station = pick() ?: return@launch
            resetForNewStation(station)
            handle(coordinator.begin(station))
        }
    }

    /** A station the listener chose (not an automatic hop): fresh hop budget, history, click count. */
    private fun resetForNewStation(station: Station) {
        autoHops = 0
        excludedIds.clear()
        hopJob?.cancel()
        notedStation = null
        pausedAtMs = null
        scope.launch { deck.played(station) }
        scope.launch { runCancellable { catalog.registerClick(station.stationUuid) } }
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
        handle(coordinator.begin(station))
    }

    private fun rememberPlay(station: Station) {
        if (notedStation == station.stationUuid) return
        notedStation = station.stationUuid
        scope.launch {
            runCancellable { reachability.note(station.stationUuid, ok = true) }
            runCancellable { history.record(station) }
        }
    }

    companion object {
        const val MAX_AUTO_HOPS = 3
        const val WATCHDOG_TICK_MS = 1_000L

        /** Longer than this, a paused stream is reopened rather than resumed from its buffer. */
        const val LIVE_RESUME_AFTER_MS = 30_000L
    }
}
