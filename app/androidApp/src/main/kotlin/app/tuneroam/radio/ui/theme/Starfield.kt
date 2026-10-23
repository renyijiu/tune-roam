package app.tuneroam.radio.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Stars drifting down behind the browse screens. They move a few pixels a second, less than
 * one per frame, so the drift steps at about 15 fps: it looks the same and spares the whole
 * screen redrawing every frame while nothing else moves. The step is twice the mini player's,
 * so while a station plays the two land on the same frames.
 */
@Composable
fun Starfield(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val time = animationClock(running = !reduced, stepMillis = 2 * Motion.AMBIENT_STEP_MS)
    Canvas(modifier.fillMaxSize()) {
        val drift = time.value % LOOP_SECONDS / LOOP_SECONDS
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
private const val LOOP_SECONDS = 32f
