package app.tuneroam.radio.playback

import kotlin.math.exp

/**
 * How loud the playing audio is right now, for visuals. [LoudnessLeveler] publishes a level
 * per chunk from the audio thread and the UI reads it each frame, both without locks or
 * allocation. A level is relative to the station's own running loudness, so 1 is "as loud as
 * this station usually is" and a quiet station moves the trace as much as a loud one.
 *
 * The processor runs ahead of the speaker by the AudioTrack buffer, so readers ask for the
 * level [DELAY_MS] in the past. Times are [nowMs] milliseconds.
 */
class AudioMeter {
    private val times = LongArray(SLOTS)
    private val levels = FloatArray(SLOTS)

    /** Entries written so far; the volatile write publishes the slot written before it. */
    @Volatile
    private var count = 0

    /** Audio thread only. [timeMs] never goes backwards, so lookups can stop at the first hit. */
    fun publish(level: Float, timeMs: Long) {
        val n = count
        val previous = if (n == 0) Long.MIN_VALUE else times[(n - 1) % SLOTS]
        val slot = n % SLOTS
        times[slot] = maxOf(timeMs, previous)
        levels[slot] = level
        count = n + 1
    }

    /** The level that was current at [timeMs], or 0 when nothing fresh was published then. */
    fun levelAt(timeMs: Long): Float {
        val n = count
        for (back in 1..minOf(n, SLOTS)) {
            val slot = (n - back) % SLOTS
            val time = times[slot]
            if (time <= timeMs) return if (timeMs - time > STALE_MS) 0f else levels[slot]
        }
        return 0f
    }

    companion object {
        /** The one the playback service writes to; it runs in the app's process. */
        val Shared = AudioMeter()

        /** About the AudioTrack buffer plus output latency, tuned by ear on the emulator. */
        const val DELAY_MS = 300L

        /** Longer than any decoder buffer: past this the stream stalled and the meter reads silence. */
        const val STALE_MS = 250L
        private const val SLOTS = 64

        const val ATTACK_SECONDS = 0.04f
        const val RELEASE_SECONDS = 0.3f

        fun nowMs(): Long = System.nanoTime() / 1_000_000

        /** One step of an envelope follower: rises quickly to a beat, falls back slowly. */
        fun follow(current: Float, target: Float, dtSeconds: Float): Float {
            val tau = if (target > current) ATTACK_SECONDS else RELEASE_SECONDS
            return current + (target - current) * (1f - exp(-dtSeconds.coerceAtLeast(0f) / tau))
        }
    }
}
