package app.tuneroam.radio.ui.fx

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.ui.theme.Amber
import app.tuneroam.radio.ui.theme.Signal
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Two glow colors taken from the station's logo, for the aurora and the orb. */
@Immutable
data class StationColors(val primary: Color, val secondary: Color) {
    companion object {
        val Default = StationColors(Signal, Amber)
    }
}

/** The station's colors, crossfading when the station changes. Falls back to the app's own. */
@Composable
fun rememberStationColors(station: Station?): Pair<State<Color>, State<Color>> {
    val context = LocalContext.current
    val url = station?.favicon?.takeIf { it.startsWith("http") }
    val extracted = produceState(StationColors.Default, url) {
        if (url == null) {
            value = StationColors.Default
            return@produceState
        }
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(64).build()
        val result = context.imageLoader.execute(request) as? SuccessResult
        val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
        value = bitmap?.let { withContext(Dispatchers.Default) { colorsOf(it) } } ?: StationColors.Default
    }
    val primary = animateColorAsState(extracted.value.primary, tween(1_200), label = "primary")
    val secondary = animateColorAsState(extracted.value.secondary, tween(1_200), label = "secondary")
    return primary to secondary
}

/**
 * The two most vivid hues in [bitmap], pushed bright enough to glow on the night background.
 * Null for a grey or nearly empty logo.
 */
private fun colorsOf(bitmap: Bitmap): StationColors? {
    val weights = FloatArray(HUES)
    val hueSums = FloatArray(HUES)
    val hsv = FloatArray(3)
    var total = 0f
    for (y in 0 until bitmap.height step 2) {
        for (x in 0 until bitmap.width step 2) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 128) continue
            android.graphics.Color.colorToHSV(pixel, hsv)
            val weight = hsv[1] * hsv[2] * hsv[2]
            if (weight < 0.08f) continue
            val bucket = (hsv[0] / 360f * HUES).toInt().coerceIn(0, HUES - 1)
            weights[bucket] += weight
            hueSums[bucket] += hsv[0] * weight
            total += weight
        }
    }
    if (total < 3f) return null
    val first = weights.indices.maxBy { weights[it] }
    val firstHue = hueSums[first] / weights[first]
    val second = weights.indices
        .filter { hueDistance(it * 360f / HUES, firstHue) >= 50f && weights[it] > weights[first] * 0.12f }
        .maxByOrNull { weights[it] }
    val secondHue = second?.let { hueSums[it] / weights[it] } ?: ((firstHue + 48f) % 360f)
    return StationColors(glow(firstHue), glow(secondHue))
}

private fun glow(hue: Float): Color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.62f, 0.95f)))

private fun hueDistance(a: Float, b: Float): Float {
    val d = kotlin.math.abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}

private const val HUES = 24
