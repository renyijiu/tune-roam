package app.tuneroam.radio.ui.theme

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SteppedFramesTest {
    @Test
    fun everyFrameWithoutAStep() = runTest {
        val frames = record(stepMillis = 0)
        assertEquals(62, frames.size)
    }

    @Test
    fun stepsAtAboutTheRequestedRate() = runTest {
        val frames = record(stepMillis = Motion.AMBIENT_STEP_MS)
        assertTrue(frames.size in 29..31, "${frames.size} steps in a second")
        frames.zipWithNext { a, b -> assertTrue(b - a >= 2 * PERIOD_MS * MILLIS, "gap ${(b - a) / MILLIS} ms") }
    }

    @Test
    fun differentStepsShareFrames() = runTest {
        val mini = mutableListOf<Long>()
        val stars = mutableListOf<Long>()
        val clock = Vsync(testScheduler)
        val jobs = listOf(
            launch(clock) { steppedFrames(Motion.AMBIENT_STEP_MS) { mini += it } },
            launch(clock) { steppedFrames(2 * Motion.AMBIENT_STEP_MS) { stars += it } },
        )
        advanceTimeBy(1_000)
        jobs.forEach { it.cancel() }
        // The slower one only ever moves on a frame the faster one draws anyway.
        assertTrue(stars.isNotEmpty())
        assertTrue(mini.containsAll(stars), "stars ${stars.map { it / MILLIS }} vs mini ${mini.map { it / MILLIS }}")
    }

    private suspend fun kotlinx.coroutines.test.TestScope.record(stepMillis: Long): List<Long> {
        val frames = mutableListOf<Long>()
        val job = launch(Vsync(testScheduler)) { steppedFrames(stepMillis) { frames += it } }
        advanceTimeBy(1_000)
        job.cancel()
        return frames
    }

    /** A display refreshing every [PERIOD_MS] of virtual time; everyone waiting gets the same frame. */
    private class Vsync(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            val now = scheduler.currentTime
            val next = (now / PERIOD_MS + 1) * PERIOD_MS
            delay(next - now)
            return onFrame(next * MILLIS)
        }
    }

    private companion object {
        const val PERIOD_MS = 16L
        const val MILLIS = 1_000_000L
    }
}
