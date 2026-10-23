package app.tuneroam.radio.ui.fx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuneroam.radio.playback.AudioMeter
import app.tuneroam.radio.ui.theme.Signal
import app.tuneroam.radio.ui.theme.animationClock
import app.tuneroam.radio.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val LATIN_STATIC = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789#%&*+=<>/"
private const val CJK_STATIC = "電台信號頻波聲音樂夜光星海風雲山川城"

/**
 * Text that tunes in: when [text] changes, the characters run through static and lock
 * left to right, like a station name arriving on a digital tuner. The first value shows
 * as is. Screen readers get the final text only.
 */
@Composable
fun DecodeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val reduced = rememberReducedMotion()
    var shown by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (reduced || shown == text) {
            shown = text
            return@LaunchedEffect
        }
        val random = Random(text.hashCode())
        val duration = (420 + text.length * 18).coerceAtMost(900).toFloat()
        val start = withFrameMillis { it }
        var lastSwap = -1L
        while (true) {
            val now = withFrameMillis { it }
            val progress = (now - start) / duration
            if (progress >= 1f) break
            // Swap glyphs every ~45 ms; per frame reads as a smear rather than static.
            val tick = (now - start) / 45
            if (tick == lastSwap) continue
            lastSwap = tick
            val locked = (progress * text.length).toInt()
            shown = buildString(text.length) {
                text.forEachIndexed { i, c ->
                    append(if (i < locked || c.isWhitespace()) c else staticFor(c, random))
                }
            }
        }
        shown = text
    }
    Text(
        text = shown,
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun staticFor(c: Char, random: Random): Char {
    val pool = if (c.code >= 0x2E80) CJK_STATIC else LATIN_STATIC
    return pool[random.nextInt(pool.length)]
}

/** Three bars that dance while [playing] and lie flat when paused. */
@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Signal) {
    val reduced = rememberReducedMotion()
    val level by animateFloatAsState(if (playing) 1f else 0f, tween(360), label = "eq")
    val time = animationClock(running = playing && !reduced)
    Canvas(modifier) {
        val t = time.value
        val gap = size.width / 7f
        val barWidth = gap * 1.6f
        repeat(3) { i ->
            val wave = (sin(t * (7f + i * 2.3f) + i * 1.7f) * 0.5f + 0.5f)
            val height = size.height * (0.18f + 0.82f * wave * level)
            val x = gap + i * (barWidth + gap * 0.7f) + barWidth / 2f
            drawLine(
                color = color,
                start = Offset(x, size.height),
                end = Offset(x, size.height - height),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * The playing audio's level, smoothed for drawing: jumps up with a beat and settles back.
 * Updates every frame while [active] and holds still otherwise. Read it in a draw block.
 */
@Composable
fun rememberAudioLevel(active: Boolean): State<Float> {
    val level = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val target = AudioMeter.Shared.levelAt(AudioMeter.nowMs() - AudioMeter.DELAY_MS)
                level.floatValue = AudioMeter.follow(level.floatValue, target, (now - last) / 1_000_000_000f)
                last = now
            }
        }
    }
    return level
}

/**
 * How tall the carrier draws for an audio [level], blended in by [weight] (0 leaves the
 * carrier as it always was). Silence leaves a quiet, nearly flat line.
 */
fun audioScale(level: Float, weight: Float): Float {
    val scale = (0.12f + 0.8f * level).coerceAtMost(1.4f)
    return 1f + (scale - 1f) * weight
}

/**
 * An oscilloscope trace across [width] centered on [center]. [noise] 1 is pure static,
 * 0 a clean carrier wave: the dial's tuning needle drawn as a signal. [audio] scales the
 * carrier, so it can follow what is playing; static is left as is.
 */
fun DrawScope.drawSignalTrace(
    center: Offset,
    width: Float,
    amplitude: Float,
    time: Float,
    noise: Float,
    locked: Color,
    static: Color,
    audio: Float = 1f,
) {
    val points = 84
    val frame = floor(time * 28f)
    val path = Path()
    for (i in 0..points) {
        val u = i.toFloat() / points * 2f - 1f
        val envelope = (1f - u * u).pow(1.5f)
        val carrier = sin(u * PI.toFloat() * 3.5f - time * 5f) * (0.42f + 0.18f * sin(time * 1.3f))
        val hiss = (hash(i, frame) * 2f - 1f) * (0.6f + 0.4f * hash(i + 97, frame))
        val y = (carrier * audio * (1f - noise) + hiss * noise) * envelope * amplitude
        val x = center.x + u * width / 2f
        if (i == 0) path.moveTo(x, center.y + y) else path.lineTo(x, center.y + y)
    }
    val color = lerp(locked, static, noise)
    drawPath(path, color.copy(alpha = 0.18f), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun hash(i: Int, frame: Float): Float {
    val v = sin(i * 12.9898f + frame * 78.233f) * 43758.547f
    return abs(v - floor(v))
}
