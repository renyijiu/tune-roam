package app.tuneroam.radio.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

/**
 * Stars drifting down behind the browse screens. They move a few pixels a second, less than
 * one per frame, so the drift steps at about 20 fps: it looks the same and spares the whole
 * screen redrawing every frame while nothing else moves.
 */
@Composable
fun Starfield(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val loop = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) {
            delay(STEP_MS)
            withFrameMillis { now -> loop.floatValue = (now - start) % LOOP_MS / LOOP_MS.toFloat() }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val drift = loop.floatValue
        repeat(STAR_COUNT) { index ->
            val x = ((index * 97 % 1000) / 1000f) * size.width
            val baseY = (index * 53 % 1000) / 1000f
            val speed = 0.06f + (index % 5) * 0.035f
            val y = ((baseY + drift * speed) % 1f) * size.height
            val radius = if (index % 13 == 0) 2.3f else 1.1f
            val alpha = 0.16f + (index % 7) * 0.08f
            drawCircle(color = Color.White.copy(alpha = alpha), radius = radius, center = Offset(x, y))
        }
    }
}

private const val STAR_COUNT = 78
private const val LOOP_MS = 32_000L

/** Plus the wait for the next frame, about 50 ms between steps. */
private const val STEP_MS = 40L
