package app.tuneroam.radio.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Evens out loudness between stations. Radio streams carry no loudness metadata, so
 * this measures as it plays: a slow running estimate of the signal's RMS level sets a
 * gain toward [TARGET_RMS], limited to ±12 dB so it levels stations without flattening
 * a song's own dynamics. Gain falls faster than it rises, never pushes a peak past
 * [CEILING], and starts from unity on every new stream (ExoPlayer flushes processors
 * when the item changes), so a loud station after a quiet one does not blast.
 *
 * 16-bit PCM only, which is what ExoPlayer decodes radio to by default; any other
 * format passes through untouched. [enabled] can change while playing: the gain then
 * glides back to unity instead of jumping.
 *
 * Each chunk's level, relative to the running estimate and measured before leveling, also
 * goes to [meter] for the visuals.
 */
@OptIn(UnstableApi::class)
class LoudnessLeveler(private val meter: AudioMeter? = null) : BaseAudioProcessor() {
    @Volatile
    var enabled: Boolean = true

    /** Mean square of the signal (full scale = 1), 0 until something audible was heard. */
    private var level = 0.0
    private var heardSeconds = 0.0
    private var gain = 1.0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val limit = inputBuffer.limit()
        var position = inputBuffer.position()
        if (position == limit) return
        val channels = inputAudioFormat.channelCount
        val sampleRate = inputAudioFormat.sampleRate
        val frameBytes = 2 * channels
        val chunkBytes = max(1, sampleRate * CHUNK_MS / 1000) * frameBytes
        val output = replaceOutputBuffer(limit - position)
        val first = position
        val now = if (meter != null) AudioMeter.nowMs() else 0L
        while (position < limit) {
            val end = min(limit, position + chunkBytes)
            val frames = (end - position) / frameBytes
            if (frames == 0) break
            var sumSquares = 0.0
            var peak = 0
            var index = position
            while (index < end) {
                val sample = inputBuffer.getShort(index).toInt()
                sumSquares += sample.toDouble() * sample
                peak = max(peak, abs(sample))
                index += 2
            }
            val seconds = frames.toDouble() / sampleRate
            val meanSquare = sumSquares / (frames * channels) / FULL_SCALE_SQUARED
            val next = nextGain(meanSquare, peak / FULL_SCALE, seconds)
            if (meter != null) {
                val relative = if (level > 0.0 && meanSquare > GATE_MEAN_SQUARE) sqrt(meanSquare / level).toFloat() else 0f
                // Chunks later in this buffer play later; spread them out instead of stamping all at once.
                meter.publish(relative, now + (position - first) / frameBytes * 1000L / sampleRate)
            }
            // Never ramp down through a peak that would clip; start this chunk at the safe gain.
            val start = if (next < gain && peak > 0 && gain * peak / FULL_SCALE > CEILING) next else gain
            index = position
            for (frame in 0 until frames) {
                val g = start + (next - start) * (frame + 1) / frames
                repeat(channels) {
                    val scaled = (inputBuffer.getShort(index) * g).roundToInt().coerceIn(-32_768, 32_767)
                    output.putShort(scaled.toShort())
                    index += 2
                }
            }
            gain = next
            position += frames * frameBytes
        }
        // A trailing partial frame cannot happen with PCM input, but never loop on one.
        while (position < limit) output.put(inputBuffer.get(position++))
        inputBuffer.position(limit)
        output.flip()
    }

    private fun nextGain(meanSquare: Double, peak: Double, seconds: Double): Double {
        if (meanSquare > GATE_MEAN_SQUARE) {
            heardSeconds += seconds
            val warming = heardSeconds < WARMUP_SECONDS
            val tau = if (warming) FAST_TAU_SECONDS else SLOW_TAU_SECONDS
            level = if (level == 0.0) meanSquare else level + min(1.0, seconds / tau) * (meanSquare - level)
        }
        val desired = if (!enabled || level == 0.0) 1.0 else (TARGET_RMS / sqrt(level)).coerceIn(MIN_GAIN, MAX_GAIN)
        val warming = heardSeconds < WARMUP_SECONDS
        val dbPerSecond = when {
            warming -> WARMUP_DB_PER_SECOND
            desired < gain -> ATTACK_DB_PER_SECOND
            else -> RELEASE_DB_PER_SECOND
        }
        val maxStep = dbPerSecond * seconds
        val wantedDb = 20 * log10(desired / gain)
        var next = gain * 10.0.pow(wantedDb.coerceIn(-maxStep, maxStep) / 20)
        // Boosting is what can clip; turning down never does.
        if (next > 1.0 && peak > 0) next = min(next, max(1.0, CEILING / peak))
        return next
    }

    override fun onFlush() {
        level = 0.0
        heardSeconds = 0.0
        gain = 1.0
    }

    override fun onReset() = onFlush()

    companion object {
        /** About −18 dBFS RMS: busy commercial stations sit above it, talk and classical below. */
        const val TARGET_RMS = 0.126
        const val CEILING = 0.97
        const val MIN_GAIN = 0.25
        const val MAX_GAIN = 4.0
        private const val CHUNK_MS = 50
        private const val FULL_SCALE = 32_768.0
        private const val FULL_SCALE_SQUARED = FULL_SCALE * FULL_SCALE

        /** Below about −50 dBFS is silence or a gap between songs; it should not pull the gain up. */
        private const val GATE_MEAN_SQUARE = 1e-5
        private const val WARMUP_SECONDS = 1.5
        private const val FAST_TAU_SECONDS = 0.5
        private const val SLOW_TAU_SECONDS = 3.0
        private const val WARMUP_DB_PER_SECOND = 18.0
        private const val ATTACK_DB_PER_SECOND = 6.0
        private const val RELEASE_DB_PER_SECOND = 2.0
    }
}
