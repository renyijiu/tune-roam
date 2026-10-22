package app.tuneroam.radio.domain.playback

import app.tuneroam.radio.core.epochMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wall-clock sleep timer. The shell pauses playback when [consumeIfDue] becomes true.
 * Minutes are the choices the player offers; the deadline itself is testable with a fake clock.
 */
class SleepTimer(
    private val clock: () -> Long = ::epochMillis,
) {
    private val _deadlineMs = MutableStateFlow<Long?>(null)
    val deadlineMs: StateFlow<Long?> = _deadlineMs.asStateFlow()

    fun arm(minutes: Int) {
        require(minutes > 0) { "Sleep timer needs a positive number of minutes." }
        _deadlineMs.value = clock() + minutes * 60_000L
    }

    fun cancel() {
        _deadlineMs.value = null
    }

    /** True once, when the deadline has passed. Clears the deadline. */
    fun consumeIfDue(nowMs: Long = clock()): Boolean {
        val deadline = _deadlineMs.value ?: return false
        if (nowMs < deadline) return false
        _deadlineMs.value = null
        return true
    }
}
