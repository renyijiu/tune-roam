package app.tuneroam.radio.ui.roam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuneroam.radio.R
import app.tuneroam.radio.domain.catalog.WorldDial
import app.tuneroam.radio.presentation.RoamViewModel
import app.tuneroam.radio.ui.theme.Amber
import app.tuneroam.radio.ui.theme.Signal
import kotlin.math.cos
import kotlin.math.sin
import org.koin.androidx.compose.koinViewModel

@Composable
fun RoamScreen(viewModel: RoamViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chinese = java.util.Locale.getDefault().language.startsWith("zh")
    var rotation by remember { mutableFloatStateOf(WorldDial.rotationFor(WorldDial.indexOfCode("CN"))) }
    var appliedIndex by remember { mutableIntStateOf(WorldDial.indexOfCode("CN")) }
    LaunchedEffect(state.index) {
        if (state.index != appliedIndex) {
            rotation = WorldDial.rotationFor(state.index)
            appliedIndex = state.index
        }
    }
    val index = WorldDial.dialIndex(rotation)
    val stop = WorldDial.stops[index]
    val label = if (chinese) stop.nameZh else stop.shortName
    val neighbor = { delta: Int ->
        val count = WorldDial.stops.size
        val next = WorldDial.stops[(index + delta + count) % count]
        if (chinese) next.nameZh else next.shortName
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.roam_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.roam_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
        )
        GlobeDial(
            rotation = rotation,
            onDrag = { rotation += it },
            onSettle = {
                val settled = WorldDial.dialIndex(rotation)
                appliedIndex = settled
                viewModel.onDialSettled(settled)
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .size(280.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(neighbor(-1), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(neighbor(1), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        val station = state.station?.takeIf { state.index == index }
        Text(
            text = when {
                state.loading && station == null -> stringResource(R.string.roam_loading)
                station != null -> station.name
                state.empty -> stringResource(R.string.roam_empty)
                else -> stringResource(R.string.roam_loading)
            },
            modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp),
            color = if (station != null) Signal else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
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
private fun GlobeDial(
    rotation: Float,
    onDrag: (Float) -> Unit,
    onSettle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val land = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val dragBy = rememberUpdatedState(onDrag)
    val settle = rememberUpdatedState(onSettle)
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, amount -> dragBy.value(amount * 0.45f) },
                onDragEnd = { settle.value() },
            )
        },
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(land.copy(alpha = 0.18f), land.copy(alpha = 0.02f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(color = ring, radius = radius * 0.92f, center = center, style = Stroke(width = 2.dp.toPx()))
        drawOval(
            color = ring,
            topLeft = Offset(center.x - radius * 0.42f, center.y - radius * 0.92f),
            size = Size(radius * 0.84f, radius * 1.84f),
            style = Stroke(width = 1.dp.toPx()),
        )
        val count = WorldDial.stops.size
        WorldDial.stops.forEachIndexed { stopIndex, _ ->
            val degrees = stopIndex * (360f / count) + rotation - 90f
            val radians = Math.toRadians(degrees.toDouble())
            val selected = stopIndex == WorldDial.dialIndex(rotation, count)
            val dotRadius = if (selected) 7.dp.toPx() else 3.5.dp.toPx()
            val point = Offset(
                center.x + cos(radians).toFloat() * radius * 0.78f,
                center.y + sin(radians).toFloat() * radius * 0.78f,
            )
            drawCircle(
                color = if (selected) Amber else Signal.copy(alpha = 0.8f),
                radius = dotRadius,
                center = point,
            )
        }
        val needle = Path().apply {
            moveTo(center.x, center.y - radius - 4.dp.toPx())
            lineTo(center.x - 8.dp.toPx(), center.y - radius + 14.dp.toPx())
            lineTo(center.x + 8.dp.toPx(), center.y - radius + 14.dp.toPx())
            close()
        }
        drawPath(needle, Amber)
        drawLine(
            color = Amber,
            start = Offset(center.x, center.y - radius * 0.15f),
            end = Offset(center.x, center.y - radius * 0.72f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
