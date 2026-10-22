package app.tuneroam.radio.ui.fx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import app.tuneroam.radio.ui.theme.Motion

/**
 * A play triangle that folds into two pause bars and back. Each shape is two four-point
 * halves, so the morph is a straight interpolation of eight corners.
 */
@Composable
fun PlayPauseGlyph(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(if (playing) 1f else 0f, Motion.Bouncy, label = "morph")
    val path = remember { Path() }
    Canvas(modifier) {
        val p = progress
        path.reset()
        for (half in 0..1) {
            for (corner in 0..3) {
                val k = (half * 4 + corner) * 2
                val x = (PLAY[k] + (PAUSE[k] - PLAY[k]) * p) * size.width
                val y = (PLAY[k + 1] + (PAUSE[k + 1] - PLAY[k + 1]) * p) * size.height
                if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        // Swing a little through the morph so it reads as one object turning over.
        rotate(degrees = p * (1f - p) * 120f) {
            drawPath(path, color)
        }
    }
}

// Corners as x,y fractions of the box: left half then right half.
private val PLAY = floatArrayOf(
    0.30f, 0.18f, 0.56f, 0.34f, 0.56f, 0.66f, 0.30f, 0.82f,
    0.56f, 0.34f, 0.82f, 0.50f, 0.82f, 0.50f, 0.56f, 0.66f,
)
private val PAUSE = floatArrayOf(
    0.26f, 0.20f, 0.42f, 0.20f, 0.42f, 0.80f, 0.26f, 0.80f,
    0.58f, 0.20f, 0.74f, 0.20f, 0.74f, 0.80f, 0.58f, 0.80f,
)
