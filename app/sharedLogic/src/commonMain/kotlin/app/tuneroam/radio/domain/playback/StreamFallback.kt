package app.tuneroam.radio.domain.playback

/**
 * Order in which a shell should open a station.
 * Resolved address first, then the catalog's original address when it differs.
 */
object StreamFallback {
    const val START_TIMEOUT_MS: Long = 12_000

    fun candidates(urlResolved: String, url: String): List<StreamCandidate> {
        val resolved = urlResolved.trim()
        val original = url.trim()
        val items = mutableListOf<StreamCandidate>()
        if (resolved.isNotEmpty()) {
            items += StreamCandidate(resolved, StreamUrlKind.RESOLVED)
        }
        if (original.isNotEmpty() && original != resolved) {
            items += StreamCandidate(original, StreamUrlKind.ORIGINAL)
        }
        return items
    }
}

enum class StreamUrlKind {
    RESOLVED,
    ORIGINAL,
}

data class StreamCandidate(
    val url: String,
    val kind: StreamUrlKind,
)

enum class EnginePhase {
    BUFFERING,
    PLAYING,
    PAUSED,
}

/**
 * One station's attempt cursor. Pure and single-threaded; the Android service
 * calls it from the main thread, and a future AVPlayer shell can do the same.
 */
class StreamSession(
    urlResolved: String,
    url: String,
) {
    val candidates: List<StreamCandidate> = StreamFallback.candidates(urlResolved, url)
    var index: Int = 0
        private set
    var startedAtMs: Long = UNSET
        private set

    val current: StreamCandidate?
        get() = candidates.getOrNull(index)

    fun markAttemptStart(nowMs: Long) {
        startedAtMs = nowMs
    }

    fun failAndNext(): StreamStep {
        if (candidates.isEmpty() || index >= candidates.lastIndex) {
            index = candidates.size
            return StreamStep.Exhausted
        }
        index += 1
        return StreamStep.Try(candidates[index])
    }

    private companion object {
        const val UNSET = -1L
    }
}

sealed interface StreamStep {
    data object Stay : StreamStep
    data class Try(val candidate: StreamCandidate) : StreamStep
    data object Exhausted : StreamStep
}
