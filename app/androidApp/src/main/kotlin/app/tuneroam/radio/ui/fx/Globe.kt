package app.tuneroam.radio.ui.fx

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import app.tuneroam.radio.domain.catalog.DialStop
import app.tuneroam.radio.ui.theme.Amber
import app.tuneroam.radio.ui.theme.Signal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Where the globe faces: the point under the reticle, and how close the camera sits. */
data class GlobeCamera(val latitude: Float, val longitude: Float, val zoom: Float = 1f)

/** Unit vectors for every land dot: x toward 90°E, y north, z toward 0°N 0°E. */
private object LandPoints {
    val x: FloatArray
    val y: FloatArray
    val z: FloatArray

    init {
        val xs = ArrayList<Float>(9000)
        val ys = ArrayList<Float>(9000)
        val zs = ArrayList<Float>(9000)
        LAND_ROWS.split(';').forEachIndexed { row, spec ->
            val lat = Math.toRadians((-90f + row * LAND_SPACING_DEGREES).toDouble())
            val count = spec.substringBefore(':').toInt()
            var k = 0
            var land = false
            spec.substringAfter(':').split(',').forEach { run ->
                val n = run.toInt()
                if (land) {
                    for (j in k until k + n) {
                        val lon = Math.toRadians(-180.0 + (j + 0.5) * 360.0 / count)
                        xs += (cos(lat) * sin(lon)).toFloat()
                        ys += sin(lat).toFloat()
                        zs += (cos(lat) * cos(lon)).toFloat()
                    }
                }
                k += n
                land = !land
            }
        }
        x = xs.toFloatArray()
        y = ys.toFloatArray()
        z = zs.toFloatArray()
    }
}

/**
 * Draws the dotted night globe. Holds its point buffers and paints so a frame allocates
 * nothing for the eight thousand land dots; remember one per screen.
 */
class GlobePainter(private val density: Float) {
    private val count = LandPoints.x.size
    private val buckets = Array(BUCKETS) { FloatArray(count * 2) }
    private val filled = IntArray(BUCKETS)
    private val paints = Array(BUCKETS) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }
    private val grid = Path()
    private val ring = Path()

    // Camera rotation, set at the top of each frame.
    private var sinLon = 0f
    private var cosLon = 1f
    private var sinLat = 0f
    private var cosLat = 1f
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var px = 0f
    private var py = 0f
    private var pz = 0f

    /**
     * The globe itself: atmosphere, graticule, and land. It only changes when the camera
     * moves or [focus] changes, so draw it in its own layer apart from [drawOverlay].
     * [focus] 0..1 widens the amber spotlight around the reticle as the station locks.
     */
    fun drawGlobe(scope: DrawScope, camera: GlobeCamera, focus: Float, dotColor: Color) = with(scope) {
        aim(camera)
        drawAtmosphere(focus)
        drawGrid()
        drawLand(focus, dotColor)
    }

    /**
     * Everything that moves every frame: beacons, and ripples from the [selected] stop.
     * [broadcast] 0..1 rolls slow ripples out while it plays; [lock] 0..1 is the one-off
     * flash when a stream answers (1 = finished).
     */
    fun drawOverlay(
        scope: DrawScope,
        camera: GlobeCamera,
        stops: List<DialStop>,
        selected: Int,
        time: Float,
        broadcast: Float,
        lock: Float,
    ) = with(scope) {
        aim(camera)
        drawBeacons(stops, selected, time, broadcast)
        drawRipples(stops, selected, time, broadcast, lock)
    }

    private fun DrawScope.aim(camera: GlobeCamera) {
        val lon = Math.toRadians(camera.longitude.toDouble())
        val lat = Math.toRadians(camera.latitude.toDouble())
        sinLon = sin(lon).toFloat()
        cosLon = cos(lon).toFloat()
        sinLat = sin(lat).toFloat()
        cosLat = cos(lat).toFloat()
        cx = size.width / 2f
        cy = size.height / 2f
        radius = size.minDimension * RADIUS * camera.zoom
    }

    private fun project(x: Float, y: Float, z: Float) {
        val x1 = x * cosLon - z * sinLon
        val z1 = x * sinLon + z * cosLon
        val y2 = y * cosLat - z1 * sinLat
        pz = y * sinLat + z1 * cosLat
        px = cx + radius * x1
        py = cy - radius * y2
    }

    private fun DrawScope.drawAtmosphere(focus: Float) {
        val center = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFF0E2230).copy(alpha = 0.9f),
                0.75f to Color(0xFF09151F).copy(alpha = 0.85f),
                1f to Color(0xFF071018).copy(alpha = 0.6f),
                center = center + Offset(-radius * 0.3f, -radius * 0.35f),
                radius = radius * 1.4f,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                0.82f to Color.Transparent,
                0.86f to Signal.copy(alpha = 0.22f + 0.1f * focus),
                0.9f to Signal.copy(alpha = 0.08f),
                1f to Color.Transparent,
                center = center,
                radius = radius * 1.18f,
            ),
            radius = radius * 1.18f,
            center = center,
        )
        drawCircle(
            color = Signal.copy(alpha = 0.28f),
            radius = radius,
            center = center,
            style = Stroke(width = 1f * density),
        )
    }

    private fun DrawScope.drawGrid() {
        grid.reset()
        for (m in 0 until 12) {
            val lon = Math.toRadians(m * 30.0)
            var pen = false
            var latDeg = -80
            while (latDeg <= 80) {
                val la = Math.toRadians(latDeg.toDouble())
                project((cos(la) * sin(lon)).toFloat(), sin(la).toFloat(), (cos(la) * cos(lon)).toFloat())
                if (pz > 0f) {
                    if (pen) grid.lineTo(px, py) else grid.moveTo(px, py)
                    pen = true
                } else {
                    pen = false
                }
                latDeg += 5
            }
        }
        for (p in -2..2) {
            val la = Math.toRadians(p * 30.0)
            var pen = false
            var lonDeg = 0
            while (lonDeg <= 360) {
                val lo = Math.toRadians(lonDeg.toDouble())
                project((cos(la) * sin(lo)).toFloat(), sin(la).toFloat(), (cos(la) * cos(lo)).toFloat())
                if (pz > 0f) {
                    if (pen) grid.lineTo(px, py) else grid.moveTo(px, py)
                    pen = true
                } else {
                    pen = false
                }
                lonDeg += 6
            }
        }
        drawPath(grid, Color.White.copy(alpha = 0.06f), style = Stroke(width = 1f * density))
    }

    private fun DrawScope.drawLand(focus: Float, dotColor: Color) {
        filled.fill(0)
        // The spotlight narrows while tuning and opens over the country once it locks.
        val inner = cos(Math.toRadians(5.0 + 6.0 * focus)).toFloat()
        val outer = cos(Math.toRadians(10.0 + 11.0 * focus)).toFloat()
        val xs = LandPoints.x
        val ys = LandPoints.y
        val zs = LandPoints.z
        for (i in 0 until count) {
            project(xs[i], ys[i], zs[i])
            val bucket = when {
                pz < 0f -> 0
                pz < 0.3f -> 1
                pz < 0.65f -> 2
                pz >= inner -> 5
                pz >= outer -> 4
                else -> 3
            }
            val at = filled[bucket] * 2
            buckets[bucket][at] = px
            buckets[bucket][at + 1] = py
            filled[bucket]++
        }
        val zoom = radius / (size.minDimension * RADIUS)
        style(0, dotColor.copy(alpha = 0.08f), 1.2f * zoom)
        style(1, dotColor.copy(alpha = 0.32f), 1.6f * zoom)
        style(2, dotColor.copy(alpha = 0.6f), 2f * zoom)
        style(3, lerp(dotColor, Color.White, 0.35f).copy(alpha = 0.85f), 2.3f * zoom)
        style(4, lerp(dotColor, Amber, 0.55f), 2.5f * zoom)
        style(5, lerp(Amber, Color.White, 0.25f), 2.7f * zoom)
        drawIntoCanvas { canvas ->
            for (b in 0 until BUCKETS) {
                if (filled[b] > 0) canvas.nativeCanvas.drawPoints(buckets[b], 0, filled[b] * 2, paints[b])
            }
        }
    }

    private fun style(bucket: Int, color: Color, widthDp: Float) {
        paints[bucket].color = color.toArgb()
        paints[bucket].strokeWidth = widthDp * density
    }

    private fun stopVector(stop: DialStop, out: FloatArray) {
        val la = Math.toRadians(stop.latitude)
        val lo = Math.toRadians(stop.longitude)
        out[0] = (cos(la) * sin(lo)).toFloat()
        out[1] = sin(la).toFloat()
        out[2] = (cos(la) * cos(lo)).toFloat()
    }

    private val vec = FloatArray(3)

    private fun DrawScope.drawBeacons(stops: List<DialStop>, selected: Int, time: Float, broadcast: Float) {
        stops.forEachIndexed { index, stop ->
            stopVector(stop, vec)
            project(vec[0], vec[1], vec[2])
            if (pz < -0.02f) return@forEachIndexed
            val base = Offset(px, py)
            val visible = (pz * 4f).coerceIn(0f, 1f)
            val chosen = index == selected
            val pulse = 0.5f + 0.5f * sin(time * 2.4f + index * 1.3f)
            val height = if (chosen) 0.1f + 0.12f * broadcast + 0.03f * pulse else 0.04f + 0.02f * pulse
            project(vec[0] * (1f + height), vec[1] * (1f + height), vec[2] * (1f + height))
            val tip = Offset(px, py)
            val color = if (chosen) Amber else Signal
            drawLine(
                brush = Brush.linearGradient(listOf(color.copy(alpha = 0.9f * visible), Color.Transparent), base, tip),
                start = base,
                end = tip,
                strokeWidth = (if (chosen) 2.4f else 1.4f) * density,
                cap = StrokeCap.Round,
            )
            val glow = (if (chosen) 14f else 7f) * density
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.55f * visible), Color.Transparent),
                    center = base,
                    radius = glow,
                ),
                radius = glow,
                center = base,
            )
            drawCircle(color = color.copy(alpha = visible), radius = (if (chosen) 3f else 2f) * density, center = base)
        }
    }

    /** Rings of growing angular radius around the stop, so they bend over the sphere. */
    private fun DrawScope.drawRipples(
        stops: List<DialStop>,
        selected: Int,
        time: Float,
        broadcast: Float,
        lock: Float,
    ) {
        val stop = stops.getOrNull(selected) ?: return
        stopVector(stop, vec)
        val p0 = vec[0]
        val p1 = vec[1]
        val p2 = vec[2]
        // Tangent basis: east (u) and north (v) at the stop.
        val ul = sqrt(p2 * p2 + p0 * p0)
        val u0 = p2 / ul
        val u2 = -p0 / ul
        val v0 = p1 * u2
        val v1 = p2 * u0 - p0 * u2
        val v2 = -p1 * u0

        fun ringAt(angleDeg: Float, color: Color, width: Float) {
            val a = Math.toRadians(angleDeg.toDouble())
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            ring.reset()
            var pen = false
            for (s in 0..SEGMENTS) {
                val th = s * 2.0 * PI / SEGMENTS
                val ct = cos(th).toFloat()
                val st = sin(th).toFloat()
                project(
                    p0 * ca + (u0 * ct + v0 * st) * sa,
                    p1 * ca + (v1 * st) * sa,
                    p2 * ca + (u2 * ct + v2 * st) * sa,
                )
                if (pz > 0f) {
                    if (pen) ring.lineTo(px, py) else ring.moveTo(px, py)
                    pen = true
                } else {
                    pen = false
                }
            }
            drawPath(ring, color, style = Stroke(width = width * density, cap = StrokeCap.Round))
        }

        if (broadcast > 0.01f) {
            repeat(3) { k ->
                val p = fract(time / 3f + k / 3f)
                ringAt(2f + 30f * p, Signal.copy(alpha = (1f - p).pow(2) * 0.6f * broadcast), 1.4f)
            }
        }
        if (lock < 1f) {
            val eased = 1f - (1f - lock).pow(3)
            ringAt(1f + 40f * eased, Amber.copy(alpha = (1f - lock) * 0.95f), 1f + 3f * (1f - lock))
            ringAt(1f + 24f * eased, Color.White.copy(alpha = (1f - lock).pow(2) * 0.7f), 1.5f)
        }
    }

    companion object {
        private const val BUCKETS = 6

        /** The globe's radius as a share of the canvas's shorter side, before zoom. */
        const val RADIUS = 0.44f
        private const val SEGMENTS = 72
    }
}

internal fun fract(v: Float): Float = v - floor(v)
