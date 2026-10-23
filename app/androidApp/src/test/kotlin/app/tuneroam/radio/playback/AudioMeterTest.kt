package app.tuneroam.radio.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioMeterTest {
    @Test
    fun looksUpTheLevelCurrentAtATime() {
        val meter = AudioMeter()
        meter.publish(0.2f, timeMs = 1_000)
        meter.publish(0.8f, timeMs = 1_050)
        meter.publish(0.5f, timeMs = 1_100)
        assertEquals(0.2f, meter.levelAt(1_049))
        assertEquals(0.8f, meter.levelAt(1_050))
        assertEquals(0.5f, meter.levelAt(1_120))
    }

    @Test
    fun readsSilenceBeforeAnythingAndAfterAStall() {
        val meter = AudioMeter()
        assertEquals(0f, meter.levelAt(5_000))
        meter.publish(0.7f, timeMs = 1_000)
        assertEquals(0f, meter.levelAt(999))
        assertEquals(0f, meter.levelAt(1_000 + AudioMeter.STALE_MS + 1))
    }

    @Test
    fun keepsWorkingAfterTheRingWrapsAround() {
        val meter = AudioMeter()
        repeat(1_000) { meter.publish(it.toFloat(), timeMs = it * 20L) }
        assertEquals(990f, meter.levelAt(990 * 20L + 5))
        // Older than the ring holds: nothing to say.
        assertEquals(0f, meter.levelAt(100))
    }

    @Test
    fun timeNeverRunsBackwards() {
        val meter = AudioMeter()
        meter.publish(0.3f, timeMs = 1_040)
        meter.publish(0.9f, timeMs = 1_010)
        assertEquals(0.9f, meter.levelAt(1_045))
    }

    @Test
    fun envelopeRisesFastAndFallsSlowly() {
        var up = 0f
        repeat(3) { up = AudioMeter.follow(up, 1f, 1 / 60f) }
        var down = 1f
        repeat(3) { down = AudioMeter.follow(down, 0f, 1 / 60f) }
        // Three frames: most of the way up a beat, but only a little way down from one.
        assertTrue(up > 0.7f, "up $up")
        assertTrue(down > 0.8f, "down $down")
    }

    @Test
    fun envelopeSettlesOnASteadyLevel() {
        var level = 0f
        repeat(120) { level = AudioMeter.follow(level, 0.6f, 1 / 60f) }
        assertEquals(0.6f, level, 0.001f)
        assertEquals(0.6f, AudioMeter.follow(0.6f, 0.6f, 1 / 60f))
        assertEquals(0.4f, AudioMeter.follow(0.4f, 1f, 0f))
    }
}
