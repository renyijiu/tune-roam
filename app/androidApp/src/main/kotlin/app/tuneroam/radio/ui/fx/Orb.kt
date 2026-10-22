package app.tuneroam.radio.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import app.tuneroam.radio.domain.playback.PlaybackPhase
import app.tuneroam.radio.ui.theme.animationClock
import app.tuneroam.radio.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The station as a living signal. Connecting: sparks spiral in toward the artwork while a
 * comet circles it, gathering the stream. The moment it answers: a flash, a shockwave, and
 * the sparks blow outward. Playing: a liquid ring breathes around the artwork and sparks
 * drift off it like broadcast. Paused: the ring stills to a circle and the sparks fade.
 */
@Composable
fun SignalOrb(
    phase: PlaybackPhase,
    primary: () -> Color,
    secondary: () -> Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduced = rememberReducedMotion()
    val playing = phase == PlaybackPhase.PLAYING
    val connecting = phase == PlaybackPhase.CONNECTING
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "energy")
    val gather by animateFloatAsState(if (connecting) 1f else 0f, tween(500), label = "gather")
    val failed by animateFloatAsState(if (phase == PlaybackPhase.FAILED) 1f else 0f, tween(500), label = "failed")
    val burst = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        if (playing && previous != PlaybackPhase.PLAYING && !reduced) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1_300, easing = FastOutSlowInEasing))
        }
        previous = phase
    }
    val time = animationClock(running = !reduced && (playing || connecting || burst.isRunning))
    val sparks = remember { Sparks() }
    val ring = remember { Path() }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val outer = size.minDimension / 2f
            val art = outer * 0.58f
            val b = burst.value
            val kick = if (b < 1f) sin(PI.toFloat() * b) else 0f
            val a = lerp(primary(), Color(0xFFF07178), failed)
            val c = lerp(secondary(), Color(0xFFF07178), failed)

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(a.copy(alpha = 0.38f * energy + 0.1f), c.copy(alpha = 0.12f * energy), Color.Transparent),
                    center = center,
                    radius = outer,
                ),
                radius = outer,
                center = center,
            )

            sparks.draw(this, t, art, outer, energy, gather, kick, a, c)

            // Three liquid rings; each wobbles at its own tempo so they slide across one another.
            val level = 0.15f + 0.85f * energy
            val beat = 0.75f + 0.25f * (0.5f + 0.5f * sin(t * 4.4f)).pow(3)
            listOf(a, c, Color.White).forEachIndexed { layer, color ->
                val base = art + (8f + layer * 5f).dp.toPx()
                val amp = (3f + 5f * energy).dp.toPx() * level * beat * (1f - layer * 0.25f)
                liquidPath(ring, base, amp, t, layer.toFloat())
                drawPath(ring, color.copy(alpha = 0.14f * level), style = Stroke(width = 12.dp.toPx()), blendMode = BlendMode.Plus)
                drawPath(
                    ring,
                    color.copy(alpha = if (layer == 2) 0.55f * level else 0.85f * level + 0.1f),
                    style = Stroke(width = (if (layer == 2) 1.2f else 2.2f).dp.toPx()),
                    blendMode = BlendMode.Plus,
                )
            }

            if (gather > 0.01f) comet(t, art + 4.dp.toPx(), c.copy(alpha = gather))

            if (b < 1f) {
                val eased = 1f - (1f - b).pow(3)
                drawCircle(
                    color = a.copy(alpha = (1f - b) * 0.9f),
                    radius = art + (outer * 1.1f - art) * eased,
                    center = center,
                    style = Stroke(width = 10.dp.toPx() * (1f - b) + 1.dp.toPx()),
                )
                drawCircle(
                    color = Color.White.copy(alpha = (1f - b).pow(2) * 0.5f),
                    radius = art * (1f + 0.25f * eased),
                    center = center,
                    blendMode = BlendMode.Plus,
                )
            }
        }
        Box(
            modifier = Modifier.graphicsLayer {
                val t = time.value
                // The artwork floats in 3D and pops when the stream locks.
                val pop = 1f + 0.08f * sin(PI.toFloat() * burst.value)
                val rest = 0.94f + 0.06f * energy
                scaleX = pop * rest
                scaleY = pop * rest
                rotationX = 7f * sin(t * 0.5f) * energy
                rotationY = 9f * sin(t * 0.37f) * energy
                cameraDistance = 14f * density
            },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

private fun DrawScope.liquidPath(path: Path, base: Float, amp: Float, t: Float, seed: Float) {
    path.reset()
    val points = 120
    for (i in 0..points) {
        val th = i.toFloat() / points * 2f * PI.toFloat()
        val wobble = sin(3f * th + t * 1.1f + seed) * 0.5f +
            sin(5f * th - t * 1.7f + seed * 2f) * 0.3f +
            sin(2f * th + t * 0.6f + seed * 3f) * 0.2f
        val r = base + amp * wobble
        val x = center.x + cos(th) * r
        val y = center.y + sin(th) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

private fun DrawScope.comet(t: Float, radius: Float, color: Color) {
    rotate(degrees = t * 280f, pivot = center) {
        drawArc(
            brush = Brush.sweepGradient(
                0f to Color.Transparent,
                0.32f to color,
                0.3201f to Color.Transparent,
                center = center,
            ),
            startAngle = 0f,
            sweepAngle = 115f,
            useCenter = false,
            topLeft = center - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** A fixed swarm of sparks; positions come from time, so nothing is stored per frame. */
private class Sparks {
    private val count = 140
    private val random = Random(7)
    private val angle = FloatArray(count) { random.nextFloat() * 2f * PI.toFloat() }
    private val speed = FloatArray(count) { 0.12f + random.nextFloat() * 0.28f }
    private val offset = FloatArray(count) { random.nextFloat() }
    private val dotSize = FloatArray(count) { 0.8f + random.nextFloat() * 1.6f }
    private val tint = FloatArray(count) { random.nextFloat() }

    fun draw(
        scope: DrawScope,
        t: Float,
        art: Float,
        outer: Float,
        energy: Float,
        gather: Float,
        kick: Float,
        a: Color,
        c: Color,
    ) = with(scope) {
        val start = art + 16.dp.toPx()
        val span = outer - start
        for (i in 0 until count) {
            val life = fract(t * speed[i] + offset[i])
            val color = lerp(a, c, tint[i])
            val dot = dotSize[i].dp.toPx()
            if (energy > 0.01f || kick > 0f) {
                // Broadcast: drift outward and fade, curling slightly.
                val r = start + span * life.pow(0.8f) + outer * 0.45f * kick * (0.5f + offset[i])
                val th = angle[i] + life * 0.35f
                val alpha = (1f - life) * (life * 6f).coerceAtMost(1f) * (energy * 0.85f + kick)
                drawCircle(
                    color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = dot,
                    center = Offset(center.x + cos(th) * r, center.y + sin(th) * r),
                    blendMode = BlendMode.Plus,
                )
            }
            if (gather > 0.01f) {
                // Gathering: spiral in from the edge toward the artwork.
                val r = outer - (outer - art) * life
                val th = angle[i] + life * 1.8f
                drawCircle(
                    color = color.copy(alpha = (life * gather * 0.9f).coerceIn(0f, 1f)),
                    radius = dot * (1.2f - life * 0.6f),
                    center = Offset(center.x + cos(th) * r, center.y + sin(th) * r),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}
