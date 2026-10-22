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
 * Seconds that tick every frame while [running]. Read it inside a draw or layer block so
 * only that phase repeats, not composition. Pausing keeps the value, so shapes hold still.
 */
@Composable
fun animationClock(running: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val offset = (seconds.floatValue * 1_000_000_000L).toLong()
        val start = withFrameNanos { it } - offset
        while (true) {
            withFrameNanos { now -> seconds.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return seconds
}

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
