package app.tuneroam.radio.ui.components

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LogoPlateTest {
    private val grid = 48
    private val clear = 0x00000000

    private fun sample(pixel: (col: Int, row: Int) -> Int) = IntArray(grid * grid) { pixel(it % grid, it / grid) }

    @Test
    fun opaqueLogosFillTheCircle() {
        assertEquals(LogoFit.Fill, logoFitOf(sample { _, _ -> 0xFF3366CC.toInt() }, grid, 200, 200))
    }

    @Test
    fun roundLogosWithClearCornersStillFill() {
        val disc = sample { col, row ->
            if (hypot(col + 0.5 - grid / 2.0, row + 0.5 - grid / 2.0) <= grid / 2.0) 0xFFCC2222.toInt() else clear
        }
        assertEquals(LogoFit.Fill, logoFitOf(disc, grid, 200, 200))
    }

    @Test
    fun darkWordmarksGetAPlateAndKeepTheirBounds() {
        val wordmark = sample { col, row -> if (row in 20..27 && col in 4..43) 0xFF8B1A1A.toInt() else clear }
        val fit = assertIs<LogoFit.Inset>(logoFitOf(wordmark, grid, 480, 480))
        assertTrue(fit.plate)
        assertEquals(3f / grid, fit.left)
        assertEquals(45f / grid, fit.right)
        assertEquals(19f / grid, fit.top)
        assertEquals(29f / grid, fit.bottom)
    }

    @Test
    fun lightArtworkStaysOnTheNightDisc() {
        val white = sample { col, row -> if (row in 10..37 && col in 10..37 && (col + row) % 3 == 0) 0xFFFFFFFF.toInt() else clear }
        assertEquals(false, assertIs<LogoFit.Inset>(logoFitOf(white, grid, 300, 300)).plate)
    }
}
