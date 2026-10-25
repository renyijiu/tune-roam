package app.tuneroam.radio.ui.player

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.presentation.PlayerViewModel
import app.tuneroam.radio.ui.theme.OkKeys
import app.tuneroam.radio.ui.theme.focusRing
import app.tuneroam.radio.ui.theme.usingRemote
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val MAX_HOURS = 24
private const val MINUTE_STEP = 5
private const val DEFAULT_MINUTES = 30
private const val WHEEL_ROWS = 5
private val WheelRowHeight = 48.dp

/**
 * One pill under the player: "Sleep timer" when off, the live countdown in amber when armed.
 * Tapping it opens a sheet with hour and minute wheels, up to [MAX_HOURS] hours.
 */
@Composable
internal fun SleepTimerControl(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val deadline by viewModel.sleepDeadlineMs.collectAsStateWithLifecycle()
    // Re-read the clock every few seconds so the minutes count down on screen.
    val now by produceState(System.currentTimeMillis(), deadline) {
        while (deadline != null) {
            value = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val remaining = deadline?.let { end ->
        ((end - now + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    val active = remaining != null
    val tint by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "sleep-tint",
    )
    Row(
        modifier = modifier
            .focusRing(CircleShape)
            .clip(CircleShape)
            .background(if (active) tint.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, tint.copy(alpha = if (active) 0.6f else 0.3f), CircleShape)
            .clickable(role = Role.Button) { picking = true }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            text = remaining?.let { remainingLabel(it) } ?: stringResource(R.string.sleep_heading),
            style = MaterialTheme.typography.labelLarge,
            color = if (active) tint else MaterialTheme.colorScheme.onSurface,
        )
    }
    if (picking) {
        SleepTimerSheet(
            initialMinutes = remaining ?: DEFAULT_MINUTES,
            active = active,
            onChoose = viewModel::setSleep,
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun remainingLabel(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.sleep_remaining, rest)
        rest == 0 -> stringResource(R.string.sleep_remaining_hours, hours)
        else -> stringResource(R.string.sleep_remaining_hours_minutes, hours, rest)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    initialMinutes: Int,
    active: Boolean,
    onChoose: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Round up to the wheel's step so a running timer reopens close to where it is.
    val start = ((initialMinutes + MINUTE_STEP - 1) / MINUTE_STEP * MINUTE_STEP).coerceAtMost(MAX_HOURS * 60)
    var hours by remember { mutableIntStateOf(start / 60) }
    var minutes by remember { mutableIntStateOf(start % 60) }
    val total = hours * 60 + minutes
    val hoursFocus = remember { FocusRequester() }
    val remote = usingRemote()
    LaunchedEffect(Unit) {
        if (remote) hoursFocus.requestFocus()
    }
    val choose = { value: Int? ->
        onChoose(value)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.sleep_heading), style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (total > 0) {
                    val endsAt = DateFormat.getTimeFormat(LocalContext.current)
                        .format(Date(System.currentTimeMillis() + total * 60_000L))
                    stringResource(R.string.sleep_ends_at, endsAt)
                } else {
                    stringResource(R.string.sleep_pick_hint)
                },
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(WheelRowHeight * WHEEL_ROWS),
                contentAlignment = Alignment.Center,
            ) {
                // The band the centred row sits in.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WheelRowHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Wheel(
                        values = (0..MAX_HOURS).toList(),
                        selected = hours,
                        onSelected = {
                            hours = it
                            if (it == MAX_HOURS) minutes = 0
                        },
                        label = { it.toString() },
                        modifier = Modifier
                            .width(72.dp)
                            .focusRequester(hoursFocus),
                    )
                    WheelUnit(stringResource(R.string.sleep_unit_hours))
                    Spacer(Modifier.width(28.dp))
                    Wheel(
                        values = (0 until 60 step MINUTE_STEP).toList(),
                        selected = minutes,
                        onSelected = { minutes = it },
                        label = { String.format(Locale.ROOT, "%02d", it) },
                        enabled = hours < MAX_HOURS,
                        modifier = Modifier.width(72.dp),
                    )
                    WheelUnit(stringResource(R.string.sleep_unit_minutes))
                }
            }
            Button(
                onClick = { choose(total) },
                enabled = total > 0,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text(stringResource(R.string.sleep_start), style = MaterialTheme.typography.titleMedium)
            }
            if (active) {
                TextButton(onClick = { choose(null) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.sleep_turn_off), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WheelUnit(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A vertical scroll wheel that snaps to one row; rows fade and shrink away from the centre. */
@Composable
private fun Wheel(
    values: List<Int>,
    selected: Int,
    onSelected: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val index = values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val rowPx = with(LocalDensity.current) { WheelRowHeight.toPx() }
    val centred by remember {
        derivedStateOf {
            val step = if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
            (state.firstVisibleItemIndex + step).coerceIn(values.indices)
        }
    }
    val report by rememberUpdatedState(onSelected)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state) {
        snapshotFlow { centred }.drop(1).collect {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            report(values[it])
        }
    }
    // Follow changes made from outside, such as minutes snapping to 00 at the 24-hour cap.
    LaunchedEffect(index) {
        if (!state.isScrollInProgress && centred != index) state.animateScrollToItem(index)
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
        userScrollEnabled = enabled,
        contentPadding = PaddingValues(vertical = WheelRowHeight * (WHEEL_ROWS / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        // A remote turns the whole wheel: up and down step it, OK moves on to the button below.
        // Its rows are left out of focus so the arrows don't walk through all of them.
        modifier = modifier
            .height(WheelRowHeight * WHEEL_ROWS)
            .focusRing(RoundedCornerShape(14.dp))
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        val step = if (event.key == Key.DirectionUp) -1 else 1
                        val target = (centred + step).coerceIn(values.indices)
                        scope.launch { state.animateScrollToItem(target) }
                        true
                    }
                    in OkKeys -> focusManager.moveFocus(FocusDirection.Down)
                    else -> false
                }
            }
            .focusable(enabled),
    ) {
        itemsIndexed(values) { i, value ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    .graphicsLayer {
                        val layout = state.layoutInfo
                        val item = layout.visibleItemsInfo.firstOrNull { it.index == i }
                        val distance = if (item == null) {
                            WHEEL_ROWS / 2f
                        } else {
                            val middle = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                            abs(item.offset + item.size / 2f - middle) / rowPx
                        }
                        alpha = (1f - 0.36f * distance).coerceAtLeast(0.1f) * if (enabled) 1f else 0.35f
                        val scale = 1f - 0.12f * distance.coerceAtMost(2f)
                        scaleX = scale
                        scaleY = scale
                    }
                    .focusProperties { canFocus = false }
                    .clickable(enabled = enabled, indication = null, interactionSource = null) {
                        scope.launch { state.animateScrollToItem(i) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(value), style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}
