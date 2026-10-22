package app.tuneroam.radio.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoudnessLevelerTest {
    private val rate = 44_100
    private val channels = 2

    @Test
    fun quietAndLoudStationsEndUpCloseTogether() {
        val quiet = levelAfter(seconds = 8, amplitude = 0.03)
        val loud = levelAfter(seconds = 8, amplitude = 0.6)
        // Input differs by 26 dB; leveled output should be within a few dB of each other.
        assertTrue(abs(quiet.rmsDb - loud.rmsDb) < 4.0, "quiet ${quiet.rmsDb} dB vs loud ${loud.rmsDb} dB")
    }

    @Test
    fun boostNeverClips() {
        val out = levelAfter(seconds = 6, amplitude = 0.2, crestEvery = 400)
        assertTrue(out.peak <= LoudnessLeveler.CEILING + 0.01, "peak ${out.peak}")
    }

    @Test
    fun disabledPassesAudioThroughUnchanged() {
        val leveler = configured().apply { enabled = false }
        val input = sine(seconds = 2, amplitude = 0.05)
        val output = run(leveler, input)
        assertEquals(input.toList(), output.toList())
    }

    @Test
    fun newStreamStartsFromUnityGain() {
        val leveler = configured()
        run(leveler, sine(seconds = 6, amplitude = 0.02))
        leveler.flush()
        // Right after the flush a loud stream must not be boosted by the old gain.
        val first = run(leveler, sine(seconds = 0, amplitude = 0.8, frames = 2_205))
        assertTrue(first.maxOf { abs(it.toInt()) } / 32_768.0 <= 0.81)
    }

    @Test
    fun floatInputIsLeftAlone() {
        val leveler = LoudnessLeveler()
        val format = AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_FLOAT)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, leveler.configure(format))
    }

    private data class Level(val rmsDb: Double, val peak: Double)

    /** Level of the last two seconds, after the leveler settled. */
    private fun levelAfter(seconds: Int, amplitude: Double, crestEvery: Int = 0): Level {
        val output = run(configured(), sine(seconds, amplitude, crestEvery = crestEvery))
        val tail = output.takeLast(2 * rate * channels)
        val rms = sqrt(tail.sumOf { (it / 32_768.0).let { s -> s * s } } / tail.size)
        val peak = tail.maxOf { abs(it.toInt()) } / 32_768.0
        return Level(20 * log10(rms), peak)
    }

    private fun configured(): LoudnessLeveler = LoudnessLeveler().apply {
        configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        flush()
    }

    /** Feeds [input] in 20 ms buffers, the way a decoder hands audio over. */
    private fun run(leveler: LoudnessLeveler, input: ShortArray): ShortArray {
        val out = ArrayList<Short>(input.size)
        val step = rate / 50 * channels
        var offset = 0
        while (offset < input.size) {
            val count = minOf(step, input.size - offset)
            val buffer = ByteBuffer.allocateDirect(count * 2).order(ByteOrder.nativeOrder())
            for (i in 0 until count) buffer.putShort(input[offset + i])
            buffer.flip()
            leveler.queueInput(buffer)
            val produced = leveler.output
            while (produced.hasRemaining()) out += produced.short
            offset += count
        }
        return out.toShortArray()
    }

    /** A 440 Hz tone; [crestEvery] adds a short full-scale spike now and then, like a drum hit. */
    private fun sine(seconds: Int, amplitude: Double, crestEvery: Int = 0, frames: Int = seconds * rate): ShortArray {
        val samples = ShortArray(frames * channels)
        for (frame in 0 until frames) {
            var value = amplitude * sin(2 * PI * 440 * frame / rate)
            if (crestEvery > 0 && frame % crestEvery == 0) value = max(value, 0.9)
            val sample = (value * 32_767).roundToInt().toShort()
            repeat(channels) { samples[frame * channels + it] = sample }
        }
        return samples
    }
}
