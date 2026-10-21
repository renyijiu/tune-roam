package app.tuneroam.radio.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

@Composable
fun Starfield(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "starfield")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )
    Canvas(modifier.fillMaxSize()) {
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
