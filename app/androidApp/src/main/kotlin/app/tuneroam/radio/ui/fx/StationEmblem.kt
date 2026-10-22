package app.tuneroam.radio.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * A station's own mark when it has no usable logo. Everything comes from its name: two hues,
 * one of five broadcast patterns and a monogram, so a station always looks the same and its
 * neighbours in a list look different.
 */
@Composable
fun StationEmblem(name: String, modifier: Modifier = Modifier) {
    val seed = remember(name) { EmblemSeed.of(name) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) { drawEmblem(seed) }
        val density = LocalDensity.current
        val glyph = with(density) { (maxWidth * if (seed.monogram.length > 1) 0.34f else 0.42f).toSp() }
        Text(
            text = seed.monogram,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = glyph,
                lineHeight = glyph,
                color = Color.White.copy(alpha = 0.95f),
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.5f),
                    blurRadius = with(density) { maxWidth.toPx() * 0.06f },
                ),
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** What a name decides about its emblem; [hue] and [accentHue] also tint the player's glow. */
@Immutable
internal class EmblemSeed private constructor(name: String) {
    private val random = Random(name.trim().lowercase().hashCode())
    val hue = random.nextFloat() * 360f
    val accentHue = (hue + (if (random.nextBoolean()) 1f else -1f) * (35f + random.nextFloat() * 45f) + 360f) % 360f
    val pattern = random.nextInt(PATTERNS)
    val focusX = 0.15f + random.nextFloat() * 0.7f
    val focusY = 0.15f + random.nextFloat() * 0.7f
    val angle = random.nextFloat() * 360f
    val count = random.nextInt(4)
    val phase = random.nextFloat() * 2f * PI.toFloat()
    val monogram = monogramOf(name)

    companion object {
        fun of(name: String) = EmblemSeed(name)
    }
}

private const val PATTERNS = 5

/** Words that say "this is a radio station" rather than which one. */
private val filler = setOf(
    "radio", "rádio", "radyo", "rádió", "fm", "am", "the", "la", "le", "el", "il", "die", "der", "das",
    "de", "di", "du", "web", "online", "live", "station", "stream",
)

/** The first CJK character when the name has one, otherwise the initials of its first two telling words. */
internal fun monogramOf(name: String): String {
    val trimmed = name.trim()
    val glyph = trimmed.codePoints().filter { Character.UnicodeScript.of(it) in singleGlyphScripts }.findFirst()
    if (glyph.isPresent) return String(Character.toChars(glyph.asInt))
    val words = trimmed.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    val telling = words.filter { it.lowercase() !in filler }.ifEmpty { words }
    if (telling.isEmpty()) return "♪"
    return telling.take(2).joinToString("") { String(Character.toChars(it.codePointAt(0))).uppercase() }
}

private val singleGlyphScripts = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
)

private fun DrawScope.drawEmblem(seed: EmblemSeed) {
    val r = size.minDimension / 2f
    val glow = Color.hsv(seed.hue, 0.62f, 0.95f)
    val accent = Color.hsv(seed.accentHue, 0.62f, 0.95f)
    val deep = Color.hsv(seed.hue, 0.65f, 0.5f)
    val deeper = Color.hsv(seed.accentHue, 0.7f, 0.22f)
    val dir = seed.angle * PI.toFloat() / 180f
    val reach = Offset(cos(dir), sin(dir)) * r
    drawRect(Brush.linearGradient(listOf(deep, deeper), start = center - reach, end = center + reach))

    val line = (r * 0.03f).coerceAtLeast(1f)
    val focus = Offset(size.width * seed.focusX, size.height * seed.focusY)
    when (seed.pattern) {
        0 -> ripples(focus, r, line, glow, accent, seed)
        1 -> rays(r, glow, seed)
        2 -> waves(r, line, glow, accent, seed)
        3 -> halftone(focus, r, glow, accent, seed)
        else -> orbits(r, line, glow, accent, seed)
    }

    // Clear a calm pool for the monogram, then round the disc off with a little depth.
    drawCircle(
        Brush.radialGradient(listOf(deeper.copy(alpha = 0.45f), Color.Transparent), center, r * 0.45f),
        radius = r * 0.45f,
    )
    drawCircle(
        Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), center, r),
        radius = r,
    )
    val sheen = Offset(size.width * 0.3f, size.height * 0.22f)
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), sheen, r * 0.7f),
        radius = r * 0.7f,
        center = sheen,
    )
}

/** Radio waves spreading from somewhere off-centre. */
private fun DrawScope.ripples(focus: Offset, r: Float, line: Float, glow: Color, accent: Color, seed: EmblemSeed) {
    val step = r * (0.17f + seed.count * 0.03f)
    val rings = (r * 2.6f / step).toInt()
    for (i in 1..rings) {
        val fade = 1f - i.toFloat() / rings
        drawCircle(
            color = (if (i % 2 == 0) glow else accent).copy(alpha = 0.12f + 0.5f * fade),
            radius = step * i,
            center = focus,
            style = Stroke(width = line * (if (i % 3 == 0) 2.2f else 1f)),
        )
    }
}

/** A sunburst rising from just past the rim. */
private fun DrawScope.rays(r: Float, glow: Color, seed: EmblemSeed) {
    val dir = seed.angle * PI.toFloat() / 180f
    val source = center + Offset(cos(dir), sin(dir)) * r * 0.95f
    val rays = 12 + seed.count * 3
    val far = r * 3f
    val path = Path()
    for (i in 0 until rays step 2) {
        val a0 = 2f * PI.toFloat() * i / rays + seed.phase
        val a1 = 2f * PI.toFloat() * (i + 1) / rays + seed.phase
        path.reset()
        path.moveTo(source.x, source.y)
        path.lineTo(source.x + cos(a0) * far, source.y + sin(a0) * far)
        path.lineTo(source.x + cos(a1) * far, source.y + sin(a1) * far)
        path.close()
        drawPath(path, glow.copy(alpha = 0.16f))
    }
    drawCircle(
        Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent), source, r * 0.8f),
        radius = r * 0.8f,
        center = source,
    )
}

/** Stacked waveforms, loudest in the middle, at a slight tilt. */
private fun DrawScope.waves(r: Float, line: Float, glow: Color, accent: Color, seed: EmblemSeed) {
    val lines = 7 + seed.count
    val cycles = 2f + seed.count
    val path = Path()
    rotate(degrees = (seed.angle % 50f) - 25f) {
        val left = center.x - r * 1.3f
        val width = r * 2.6f
        val gap = width / lines
        for (i in 0 until lines) {
            val baseline = center.y - r * 1.3f + gap * (i + 0.5f)
            val amp = gap * 0.45f
            path.reset()
            for (step in 0..64) {
                val u = step / 64f
                val envelope = sin(PI.toFloat() * u)
                val y = baseline + amp * envelope * sin(2f * PI.toFloat() * cycles * u + seed.phase + i * 0.7f)
                if (step == 0) path.moveTo(left + width * u, y) else path.lineTo(left + width * u, y)
            }
            drawPath(path, lerp(glow, accent, i.toFloat() / lines).copy(alpha = 0.55f), style = Stroke(width = line * 1.4f))
        }
    }
}

/** A halftone swell, dots growing toward one point. */
private fun DrawScope.halftone(focus: Offset, r: Float, glow: Color, accent: Color, seed: EmblemSeed) {
    val cells = 9 + seed.count * 2
    val step = size.minDimension / cells
    rotate(degrees = 45f) {
        for (row in -cells / 2..cells + cells / 2) {
            for (col in -cells / 2..cells + cells / 2) {
                val p = Offset(col * step + step / 2f, row * step + step / 2f)
                val d = (hypot(p.x - focus.x, p.y - focus.y) / (r * 1.6f)).coerceIn(0f, 1f)
                val dot = step * 0.46f * (1f - d)
                if (dot < 0.5f) continue
                drawCircle(lerp(glow, accent, d).copy(alpha = 0.6f), radius = dot, center = p)
            }
        }
    }
}

/** Tilted orbits crossing the centre, each carrying a bright satellite. */
private fun DrawScope.orbits(r: Float, line: Float, glow: Color, accent: Color, seed: EmblemSeed) {
    val orbits = 3 + seed.count % 3
    val width = r * 1.85f
    val height = r * (0.5f + seed.count * 0.08f)
    for (i in 0 until orbits) {
        val color = if (i % 2 == 0) glow else accent
        rotate(degrees = seed.angle + i * 180f / orbits) {
            drawOval(
                color = color.copy(alpha = 0.5f),
                topLeft = center - Offset(width / 2f, height / 2f),
                size = Size(width, height),
                style = Stroke(width = line),
            )
            val t = seed.phase + i * 2.1f
            drawCircle(
                color = color,
                radius = line * 2.4f,
                center = center + Offset(cos(t) * width / 2f, sin(t) * height / 2f),
            )
        }
    }
}
