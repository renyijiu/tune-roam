package app.tuneroam.radio.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/**
 * Seats a logo drawn on a transparent background inside the round station mark. Cropping cuts
 * wide wordmarks off and dark artwork vanishes on the night disc, so such a logo is trimmed,
 * shrunk to fit the circle and, when it is dark, set on a pale plate. Opaque logos pass through.
 */
internal object LogoPlate : Transformation {
    override val cacheKey = "LogoPlate"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val samples = IntArray(GRID * GRID) { k ->
            input.getPixel(
                ((k % GRID + 0.5f) * input.width / GRID).toInt(),
                ((k / GRID + 0.5f) * input.height / GRID).toInt(),
            )
        }
        val fit = logoFitOf(samples, GRID, input.width, input.height) as? LogoFit.Inset ?: return input
        val art = Rect(
            (fit.left * input.width).toInt(),
            (fit.top * input.height).toInt(),
            ceil(fit.right * input.width).toInt(),
            ceil(fit.bottom * input.height).toInt(),
        )
        val diagonal = hypot(art.width().toFloat(), art.height().toFloat())
        val side = ceil(diagonal / ART_SPAN).toInt().coerceAtMost(MAX_SIDE)
        val scale = side * ART_SPAN / diagonal
        val w = art.width() * scale
        val h = art.height() * scale
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (fit.plate) canvas.drawColor(PLATE)
        canvas.drawBitmap(
            input,
            art,
            RectF((side - w) / 2f, (side - h) / 2f, (side + w) / 2f, (side + h) / 2f),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }
}

internal sealed interface LogoFit {
    /** Opaque across the visible disc: crop it to the circle as it is. */
    data object Fill : LogoFit

    /** Transparent: show the artwork's bounds (fractions of the image) whole, on a plate if dark. */
    data class Inset(val left: Float, val top: Float, val right: Float, val bottom: Float, val plate: Boolean) : LogoFit
}

/** Decides how a logo sits from a [grid]×[grid] sampling of its ARGB pixels. */
internal fun logoFitOf(samples: IntArray, grid: Int, width: Int, height: Int): LogoFit {
    // The mark crops the logo to a centred square and clips that to a circle; only the disc shows.
    val radius = min(width, height) / 2f
    var inside = 0
    var clear = 0
    var minCol = grid
    var minRow = grid
    var maxCol = -1
    var maxRow = -1
    var light = 0.0
    var weight = 0.0
    for (k in samples.indices) {
        val col = k % grid
        val row = k / grid
        val alpha = samples[k] ushr 24
        val x = (col + 0.5f) * width / grid - width / 2f
        val y = (row + 0.5f) * height / grid - height / 2f
        if (x * x + y * y <= radius * radius) {
            inside++
            if (alpha < 128) clear++
        }
        if (alpha >= 128) {
            minCol = min(minCol, col)
            minRow = min(minRow, row)
            maxCol = maxOf(maxCol, col)
            maxRow = maxOf(maxRow, row)
            light += luminance(samples[k]) * alpha
            weight += alpha
        }
    }
    if (weight == 0.0 || clear <= inside * 0.1f) return LogoFit.Fill
    val mean = light / weight
    // Whichever backdrop the artwork stands out from more: the night disc or a pale plate.
    val plate = (PLATE_LUMINANCE + 0.05) / (mean + 0.05) > (mean + 0.05) / (NIGHT_LUMINANCE + 0.05)
    // A sample sits mid-cell, so widen by a cell to keep artwork that only grazes the next one.
    return LogoFit.Inset(
        left = (minCol - 1).coerceAtLeast(0).toFloat() / grid,
        top = (minRow - 1).coerceAtLeast(0).toFloat() / grid,
        right = (maxCol + 2).coerceAtMost(grid).toFloat() / grid,
        bottom = (maxRow + 2).coerceAtMost(grid).toFloat() / grid,
        plate = plate,
    )
}

/** WCAG relative luminance of an ARGB color. */
private fun luminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val c = (argb shr shift and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

private const val GRID = 48

/** The artwork's diagonal as a share of the circle's diameter. */
private const val ART_SPAN = 0.82f

private const val MAX_SIDE = 1024

private const val PLATE = 0xFFE6EDF2.toInt()

private const val PLATE_LUMINANCE = 0.84

/** The mark's own background, a gradient between the theme's Panel and Ink. */
private const val NIGHT_LUMINANCE = 0.012
