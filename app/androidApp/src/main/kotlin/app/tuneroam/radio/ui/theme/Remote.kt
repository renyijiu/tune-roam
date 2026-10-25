package app.tuneroam.radio.ui.theme

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True while the app is driven by a remote's arrow keys (a TV or projector, or a keyboard)
 * rather than touch. TVs start in this mode; on a phone it turns on at the first arrow key.
 */
@Composable
fun usingRemote(): Boolean = LocalInputModeManager.current.inputMode == InputMode.Keyboard

/** Landscape with room to spare: a TV, a projector, a tablet, or a phone on its side. */
@Composable
fun rememberWideLayout(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp >= 600
}

/**
 * Outlines the element in the signal color while it has focus, so the remote's selection
 * reads from across a room. Put it just before the `clickable` or `focusable` it watches.
 * Touch never moves focus onto buttons, so phones never see it. A negative [outset] draws
 * the ring inside the bounds, for rows that run to the edge of a clipped list.
 */
@Composable
fun Modifier.focusRing(shape: Shape, outset: Dp = 3.dp, color: Color = Signal): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (!focused) return@drawWithContent
            val inset = outset.toPx()
            val outline = shape.createOutline(
                Size(size.width + inset * 2, size.height + inset * 2),
                layoutDirection,
                this,
            )
            translate(-inset, -inset) {
                drawOutline(outline, color, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
}

/**
 * For a text field. Landing on a field with a remote would pop the on-screen keyboard over
 * the screen and hand it the arrow keys, trapping anyone just passing through. With this the
 * remote treats the field as one stop: OK opens it for typing, and once focus moves on it
 * becomes a stop again. While typing, Up and Down leave the field (a single line has no use
 * for them), so Back to close the keyboard and then an arrow always gets out. Touch is unchanged.
 */
@Composable
fun Modifier.remoteTextEntry(shape: Shape): Modifier {
    val remote = usingRemote()
    var editing by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(editing) {
        if (editing) field.requestFocus()
    }
    return focusRing(shape)
        .onKeyEvent { event ->
            if (editing || event.key !in OkKeys) return@onKeyEvent false
            if (event.type == KeyEventType.KeyUp) editing = true
            true
        }
        .onPreviewKeyEvent { event ->
            if (!editing || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                else -> false
            }
        }
        .focusProperties { canFocus = remote && !editing }
        .focusable()
        .focusRequester(field)
        .focusProperties { canFocus = !remote || editing }
        .onFocusChanged { if (!it.isFocused) editing = false }
}

/** The remote's select button, and the keyboard keys that act like it. */
val OkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * For the label part of a row that is tappable as a whole but holds buttons of its own,
 * like the mini player or a station row with its heart. A remote can't move from a focused
 * row into buttons inside it, so the row itself takes `focusProperties { canFocus = false }`
 * before its `clickable`, and its label takes this: the remote stops on the label, OK there
 * does the row's action, and the buttons beside it are stops of their own. Touch still taps
 * the whole row.
 */
@Composable
fun Modifier.remoteRowLabel(shape: Shape, onClick: () -> Unit): Modifier {
    val click by rememberUpdatedState(onClick)
    return focusRing(shape)
        .onKeyEvent { event ->
            if (event.key !in OkKeys) return@onKeyEvent false
            if (event.type == KeyEventType.KeyUp) click()
            true
        }
        .focusable()
}
