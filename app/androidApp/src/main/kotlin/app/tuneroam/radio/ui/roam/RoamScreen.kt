package app.tuneroam.radio.ui.roam

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.domain.catalog.WorldDial
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.PlaybackPhase
import app.tuneroam.radio.presentation.RoamViewModel
import app.tuneroam.radio.ui.components.SettingsButton
import app.tuneroam.radio.ui.fx.DecodeText
import app.tuneroam.radio.ui.fx.GlobeCamera
import app.tuneroam.radio.ui.fx.GlobePainter
import app.tuneroam.radio.ui.fx.drawSignalTrace
import app.tuneroam.radio.ui.theme.Amber
import app.tuneroam.radio.ui.theme.Motion
import app.tuneroam.radio.ui.theme.Signal
import app.tuneroam.radio.ui.theme.animationClock
import app.tuneroam.radio.ui.theme.rememberReducedMotion
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Degrees of dial rotation per stop; rotation -index * STEP centers that stop. */
private val STEP = 360f / WorldDial.stops.size

private enum class Status { SCANNING, CONNECTING, LOCKED, ON_AIR }

@Composable
fun RoamScreen(
    onOpenSettings: () -> Unit,
    viewModel: RoamViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by koinInject<PlaybackCoordinator>().snapshot.collectAsStateWithLifecycle()
    val chinese = LocalConfiguration.current.locales[0].language.startsWith("zh")
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()

    val rotation = remember { Animatable(WorldDial.rotationFor(state.index)) }
    var dragging by remember { mutableStateOf(false) }
    var flinging by remember { mutableStateOf(false) }
    // "Random" sends the globe spinning east like a radio's seek until a station answers.
    var seeking by remember { mutableStateOf(false) }
    val lock = remember { Animatable(1f) }
    val index by remember { derivedStateOf { WorldDial.dialIndex(rotation.value) } }

    LaunchedEffect(Unit) {
        snapshotFlow { index }.drop(1).collect {
            if (dragging || flinging) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    LaunchedEffect(seeking) {
        if (!seeking || reduced) return@LaunchedEffect
        withTimeoutOrNull(8_000) {
            while (true) rotation.animateTo(rotation.value - STEP, tween(110, easing = LinearEasing))
        }
        seeking = false
    }
    LaunchedEffect(state.loading) {
        if (!state.loading) seeking = false
    }
    // Next, previous, a shuffle, or a list tap elsewhere flies the globe to the new country.
    LaunchedEffect(state.index, seeking) {
        if (dragging || seeking) return@LaunchedEffect
        val target = nearestRotation(state.index, rotation.value)
        if (abs(target - rotation.value) > 0.01f) rotation.animateTo(target, Motion.Detent)
    }
    var lastPhase by remember { mutableStateOf(playback.phase) }
    LaunchedEffect(playback.phase) {
        if (playback.phase == PlaybackPhase.PLAYING && lastPhase != PlaybackPhase.PLAYING) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            if (!reduced) {
                lock.snapTo(0f)
                lock.animateTo(1f, tween(1_100))
            }
        }
        lastPhase = playback.phase
    }

    val station = state.station?.takeIf { state.index == index }
    val busy = seeking || state.loading || playback.phase == PlaybackPhase.CONNECTING
    val busyNoise by animateFloatAsState(
        targetValue = if (busy) 0.85f else 0f,
        animationSpec = tween(if (busy) 160 else 700),
        label = "busy-noise",
    )
    val onAir = station != null && playback.phase == PlaybackPhase.PLAYING &&
        playback.station?.stationUuid == station.stationUuid
    val broadcast by animateFloatAsState(if (onAir) 1f else 0f, tween(900), label = "broadcast")
    val pullBack by animateFloatAsState(if (seeking) 0.8f else 1f, Motion.Gentle, label = "pull-back")
    val time = animationClock(running = !reduced)
    // Off a stop is static; centered on one the carrier comes through clean.
    val noise = {
        val detune = abs(rotation.value - (rotation.value / STEP).roundToInt() * STEP) / (STEP / 2f)
        maxOf(detune, busyNoise).coerceIn(0f, 1f)
    }
    val status = when {
        seeking || dragging || flinging || state.loading -> Status.SCANNING
        playback.phase == PlaybackPhase.CONNECTING -> Status.CONNECTING
        onAir -> Status.ON_AIR
        else -> Status.LOCKED
    }
    val coordinates by remember {
        derivedStateOf {
            val camera = cameraAt(rotation.value)
            formatCoordinates(camera.latitude, camera.longitude)
        }
    }
    val name = { i: Int -> WorldDial.stops[i].let { if (chinese) it.nameZh else it.shortName } }
    val count = WorldDial.stops.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.roam_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = coordinates,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsButton(onOpenSettings)
        }
        Text(
            text = stringResource(R.string.roam_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        )
        GlobeDial(
            rotation = { rotation.value },
            selected = { index },
            noise = noise,
            broadcast = { broadcast },
            lock = { lock.value },
            pullBack = { pullBack },
            time = time,
            onDragStart = {
                dragging = true
                seeking = false
                scope.launch { rotation.stop() }
            },
            onDrag = { delta -> scope.launch { rotation.snapTo(rotation.value + delta) } },
            onRelease = { velocity ->
                dragging = false
                scope.launch {
                    val projected = exponentialDecay<Float>(frictionMultiplier = 1.6f)
                        .calculateTargetValue(rotation.value, velocity)
                    val target = (projected / STEP).roundToInt() * STEP
                    flinging = true
                    try {
                        rotation.animateTo(target, Motion.Detent, initialVelocity = velocity)
                    } finally {
                        flinging = false
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    viewModel.onDialSettled(WorldDial.dialIndex(target))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹ " + name((index - 1 + count) % count),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(88.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
            ) {
                drawSignalTrace(
                    center = center,
                    width = size.width,
                    amplitude = size.height * 0.42f,
                    time = time.value,
                    noise = noise(),
                    locked = Signal,
                    static = Color.White.copy(alpha = 0.7f),
                )
            }
            Text(
                text = name((index + 1) % count) + " ›",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.width(88.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusChip(status, time, Modifier.padding(top = 6.dp))
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                // Rolls like an odometer, in the direction the globe turned.
                val east = (targetState - initialState + count) % count <= count / 2
                val dir = if (east) 1 else -1
                (slideInVertically(tween(240)) { it * dir } + fadeIn(tween(200))) togetherWith
                    (slideOutVertically(tween(200)) { -it * dir } + fadeOut(tween(160))) using
                    SizeTransform(clip = true)
            },
            modifier = Modifier.padding(top = 4.dp),
            label = "country",
        ) { shown ->
            Text(
                text = name(shown),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        }
        // A shuffle can land outside the dial's countries; say where it actually is.
        val offDial = station
            ?.takeIf { !it.countryCode.equals(WorldDial.stops[index].isoCode, ignoreCase = true) }
            ?.country
        DecodeText(
            text = when {
                state.loading && station == null -> stringResource(R.string.roam_loading)
                station != null -> station.name
                state.empty -> stringResource(R.string.roam_empty)
                else -> stringResource(R.string.roam_loading)
            },
            modifier = Modifier.padding(top = 2.dp, start = 12.dp, end = 12.dp),
            color = if (station != null) Signal else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!offDial.isNullOrBlank()) {
            Text(
                text = offDial,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = viewModel::land,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(stringResource(R.string.roam_land))
            }
            OutlinedButton(
                onClick = {
                    seeking = true
                    viewModel.shuffle()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(stringResource(R.string.roam_shuffle))
            }
        }
    }
}

@Composable
private fun StatusChip(status: Status, time: State<Float>, modifier: Modifier = Modifier) {
    val color = when (status) {
        Status.SCANNING -> Color.White.copy(alpha = 0.7f)
        Status.CONNECTING -> Amber
        Status.LOCKED -> Signal
        Status.ON_AIR -> MaterialTheme.colorScheme.error
    }
    val label = stringResource(
        when (status) {
            Status.SCANNING -> R.string.roam_status_scanning
            Status.CONNECTING -> R.string.roam_status_connecting
            Status.LOCKED -> R.string.roam_status_locked
            Status.ON_AIR -> R.string.roam_status_on_air
        },
    )
    Row(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = color.copy(alpha = 0.12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                )
            }
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .drawBehind {
                    // Blinks while on air or connecting, steady otherwise.
                    val blink = status == Status.ON_AIR || status == Status.CONNECTING
                    val alpha = if (blink) 0.35f + 0.65f * (0.5f + 0.5f * sin(time.value * 5f)) else 1f
                    drawCircle(color.copy(alpha = alpha))
                },
        )
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = color,
        )
    }
}

@Composable
private fun GlobeDial(
    rotation: () -> Float,
    selected: () -> Int,
    noise: () -> Float,
    broadcast: () -> Float,
    lock: () -> Float,
    pullBack: () -> Float,
    time: State<Float>,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onRelease: (velocity: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val painter = remember(density) { GlobePainter(density) }
    val start = rememberUpdatedState(onDragStart)
    val dragBy = rememberUpdatedState(onDrag)
    val release = rememberUpdatedState(onRelease)
    Box(
        modifier = modifier.pointerInput(Unit) {
            val tracker = VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = {
                    tracker.resetTracking()
                    start.value()
                },
                onHorizontalDrag = { change, amount ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    dragBy.value(amount * DEGREES_PER_PX)
                },
                onDragEnd = { release.value(tracker.calculateVelocity().x * DEGREES_PER_PX) },
                onDragCancel = { release.value(0f) },
            )
        },
    ) {
        // Eight thousand dots are cached in their own layer and redraw only when the camera moves;
        // the overlay ticks every frame.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val camera = cameraAt(rotation()).let { it.copy(zoom = it.zoom * pullBack()) }
            painter.drawGlobe(this, camera, focus = 1f - noise(), dotColor = Signal)
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val camera = cameraAt(rotation()).let { it.copy(zoom = it.zoom * pullBack()) }
            painter.drawOverlay(
                scope = this,
                camera = camera,
                stops = WorldDial.stops,
                selected = selected(),
                time = time.value,
                broadcast = broadcast(),
                lock = lock(),
            )
            drawReticle(time.value, noise())
        }
    }
}

/** Four brackets around the point being tuned: wide and pale while scanning, tight and bright on lock. */
private fun DrawScope.drawReticle(time: Float, noise: Float) {
    val radius = (18f + 16f * noise).dp.toPx()
    val color = lerp(Color(0xFFFFF1D6), Color.White.copy(alpha = 0.6f), noise)
    // A dark keyline under each stroke keeps the reticle legible over the amber spotlight.
    val keyline = Color(0xFF071018).copy(alpha = 0.55f)
    rotate(degrees = time * 25f + noise * 45f, pivot = center) {
        repeat(4) { k ->
            listOf(keyline to 4.5f, color to 2f).forEach { (tone, width) ->
                drawArc(
                    color = tone,
                    startAngle = k * 90f - 18f,
                    sweepAngle = 36f,
                    useCenter = false,
                    topLeft = center - Offset(radius, radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = width.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
    val tick = 6.dp.toPx()
    val gap = radius + 4.dp.toPx()
    listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { d ->
        listOf(keyline to 3.5f, color to 1.5f).forEach { (tone, width) ->
            drawLine(
                color = tone,
                start = center + d * gap,
                end = center + d * (gap + tick),
                strokeWidth = width.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * The camera between two stops: it flies along the shorter way round from one country to
 * the next and pulls back a little mid-flight on the long hops.
 */
private fun cameraAt(rotation: Float): GlobeCamera {
    val stops = WorldDial.stops
    val count = stops.size
    val f = ((-rotation / STEP) % count + count) % count
    val i0 = floor(f).toInt() % count
    val t = f - floor(f)
    val a = stops[i0]
    val b = stops[(i0 + 1) % count]
    val dLon = wrap180((b.longitude - a.longitude).toFloat())
    val dLat = (b.latitude - a.latitude).toFloat()
    val gap = hypot(dLon, dLat)
    return GlobeCamera(
        latitude = a.latitude.toFloat() + dLat * t,
        longitude = wrap180(a.longitude.toFloat() + dLon * t),
        zoom = 1f - 0.14f * sin(PI.toFloat() * t) * min(1f, gap / 50f),
    )
}

private fun formatCoordinates(latitude: Float, longitude: Float): String = String.format(
    Locale.US,
    "%.1f°%s  %.1f°%s",
    abs(latitude),
    if (latitude >= 0) "N" else "S",
    abs(longitude),
    if (longitude >= 0) "E" else "W",
)

/** The rotation for [index] closest to [current], so the globe never unwinds a full lap. */
private fun nearestRotation(index: Int, current: Float): Float {
    val base = WorldDial.rotationFor(index)
    val laps = ((current - base) / 360f).roundToInt()
    return base + laps * 360f
}

private fun wrap180(degrees: Float): Float {
    val wrapped = ((degrees % 360f) + 360f) % 360f
    return if (wrapped > 180f) wrapped - 360f else wrapped
}

private const val DEGREES_PER_PX = 0.3f
