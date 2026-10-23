package app.tuneroam.radio.ui.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * The app's motion language is a radio finding a signal: static while tuning, a clean
 * lock when a station answers, a steady broadcast while it plays.
 */
object Motion {
    /** The dial clicking into a detent, with a little overshoot. */
    val Detent = spring<Float>(dampingRatio = 0.55f, stiffness = 180f)

    /** Buttons and icons answering a tap. */
    val Bouncy = spring<Float>(dampingRatio = 0.4f, stiffness = 600f)

    val Gentle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)

    /**
     * Small ambient motion, like the mini player's signal line, steps at about 30 fps: it
     * sits on every browse screen while a station plays, and full rate would redraw the
     * whole screen each frame for a few pixels of movement.
     */
    const val AMBIENT_STEP_MS = 33L
}

/** True when the system animator scale is off; loops stop and text skips its decode. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Seconds that tick every frame while [running], or every [stepMillis] (see [steppedFrames]).
 * Read it inside a draw or layer block so only that phase repeats, not composition. Pausing
 * keeps the value, so shapes hold still.
 */
@Composable
fun animationClock(running: Boolean, stepMillis: Long = 0L): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, stepMillis) {
        if (!running) return@LaunchedEffect
        val offset = (seconds.floatValue * 1_000_000_000L).toLong()
        val start = withFrameNanos { it } - offset
        steppedFrames(stepMillis) { now -> seconds.floatValue = (now - start) / 1_000_000_000f }
    }
    return seconds
}

/**
 * Calls [onStep] with the frame time on every frame, or with [stepMillis] > 0 on the first
 * frame of each step-long slot. Slots count from the frame clock's zero, so everything on a
 * screen stepping by the same interval, or a multiple of it, moves on the same frames and the
 * screen redraws once for all of them. Between steps it sleeps instead of waking every frame.
 */
suspend fun steppedFrames(stepMillis: Long, onStep: (frameNanos: Long) -> Unit): Nothing {
    val step = stepMillis * 1_000_000L
    var slot = -1L
    while (true) {
        val now = withFrameNanos { now ->
            val current = if (step > 0) now / step else now
            if (current != slot) {
                slot = current
                onStep(now)
            }
            now
        }
        if (step > 0) {
            val untilNext = ((slot + 1) * step - now) / 1_000_000L - FRAME_SLACK_MS
            if (untilNext > 0) delay(untilNext)
        }
    }
}

/** Wake this long before the next step so its first frame is not missed. */
private const val FRAME_SLACK_MS = 12L

/** Shrinks a little while pressed and springs back on release. */
@Composable
fun Modifier.pressSquish(interactionSource: InteractionSource, pressedScale: Float = 0.9f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.Bouncy, label = "squish")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A quick pop whenever [key] changes after the first composition, e.g. a heart being filled. */
@Composable
fun Modifier.popOnChange(key: Any?): Modifier {
    val scale = remember { Animatable(1f) }
    val first = remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first.value) {
            first.value = false
            return@LaunchedEffect
        }
        scale.snapTo(0.55f)
        scale.animateTo(1f, Motion.Bouncy)
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** The layout that lets station artwork fly between the mini player and Now Playing. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enter/exit scope of whatever is showing the artwork: the mini bar or the player route. */
val LocalArtworkVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Marks station artwork so it travels to the matching artwork on the other screen. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(stationUuid: String): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    val visibility = LocalArtworkVisibility.current ?: return this
    return with(shared) {
        this@sharedArtwork.sharedElement(
            sharedContentState = rememberSharedContentState("artwork-$stationUuid"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.8f, stiffness = 380f) },
        )
    }
}
