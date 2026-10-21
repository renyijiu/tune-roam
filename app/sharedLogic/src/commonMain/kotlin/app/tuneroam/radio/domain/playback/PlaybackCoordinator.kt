package app.tuneroam.radio.domain.playback

import app.tuneroam.radio.domain.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PlaybackPhase {
    IDLE,
    CONNECTING,
    PLAYING,
    PAUSED,
    FAILED,
}

enum class PlaybackNotice {
    NONE,
    TRYING_ALTERNATE,
    UNAVAILABLE,
}

data class PlaybackSnapshot(
    val station: Station? = null,
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val icyTitle: String? = null,
    val notice: PlaybackNotice = PlaybackNotice.NONE,
    val attempt: Int = 0,
    val attemptCount: Int = 0,
    val activeKind: StreamUrlKind? = null,
)

sealed interface PlayDirective {
    data class Open(val candidate: StreamCandidate) : PlayDirective
    data object Exhausted : PlayDirective
    data object Stay : PlayDirective
}

/**
 * Shared playback policy. Native shells report engine events and perform the
 * [PlayDirective] this class returns. Android's MediaLibraryService is one shell;
 * an iOS AVPlayer shell would be another.
 *
 * Callers must use a single thread (the process main thread on Android).
 */
class PlaybackCoordinator(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private var session: StreamSession? = null

    fun begin(station: Station): PlayDirective {
        val opened = StreamSession(station.urlResolved, station.url)
        opened.markAttemptStart(clock())
        session = opened
        val candidate = opened.current
        if (candidate == null) {
            _snapshot.value = PlaybackSnapshot(
                station = station,
                phase = PlaybackPhase.FAILED,
                notice = PlaybackNotice.UNAVAILABLE,
                attemptCount = 0,
            )
            return PlayDirective.Exhausted
        }
        _snapshot.value = PlaybackSnapshot(
            station = station,
            phase = PlaybackPhase.CONNECTING,
            attempt = 1,
            attemptCount = opened.candidates.size,
            activeKind = candidate.kind,
        )
        return PlayDirective.Open(candidate)
    }

    fun fail(): PlayDirective {
        if (_snapshot.value.phase == PlaybackPhase.FAILED) return PlayDirective.Exhausted
        val opened = session ?: return PlayDirective.Exhausted
        return apply(opened.failAndNext(), noticeOnNext = PlaybackNotice.TRYING_ALTERNATE)
    }

    fun onClock(phase: EnginePhase): PlayDirective {
        val opened = session ?: return PlayDirective.Stay
        if (phase != EnginePhase.BUFFERING) return PlayDirective.Stay
        if (_snapshot.value.phase == PlaybackPhase.FAILED) return PlayDirective.Stay
        val started = opened.startedAtMs
        if (started < 0L || clock() - started < StreamFallback.START_TIMEOUT_MS) {
            return PlayDirective.Stay
        }
        return apply(opened.failAndNext(), noticeOnNext = PlaybackNotice.TRYING_ALTERNATE)
    }

    fun onPlaying() {
        _snapshot.update { current ->
            if (current.station == null || current.phase == PlaybackPhase.FAILED) {
                current
            } else {
                current.copy(phase = PlaybackPhase.PLAYING, notice = PlaybackNotice.NONE)
            }
        }
    }

    fun onPaused() {
        _snapshot.update { current ->
            if (current.phase == PlaybackPhase.PLAYING) {
                current.copy(phase = PlaybackPhase.PAUSED)
            } else {
                current
            }
        }
    }

    fun onIcyTitle(title: String?) {
        val cleaned = title?.trim()?.takeIf { it.isNotEmpty() }
        val stationName = _snapshot.value.station?.name
        val icy = cleaned?.takeIf { !it.equals(stationName, ignoreCase = true) }
        _snapshot.update { it.copy(icyTitle = icy) }
    }

    private fun apply(step: StreamStep, noticeOnNext: PlaybackNotice): PlayDirective =
        when (step) {
            StreamStep.Stay -> PlayDirective.Stay
            is StreamStep.Try -> {
                session?.markAttemptStart(clock())
                _snapshot.update { current ->
                    current.copy(
                        phase = PlaybackPhase.CONNECTING,
                        notice = noticeOnNext,
                        icyTitle = null,
                        attempt = (session?.index ?: 0) + 1,
                        activeKind = step.candidate.kind,
                    )
                }
                PlayDirective.Open(step.candidate)
            }
            StreamStep.Exhausted -> {
                _snapshot.update { current ->
                    current.copy(
                        phase = PlaybackPhase.FAILED,
                        notice = PlaybackNotice.UNAVAILABLE,
                        icyTitle = null,
                    )
                }
                PlayDirective.Exhausted
            }
        }
}
