package app.tuneroam.radio.ui.fx

import kotlin.test.Test
import kotlin.test.assertEquals

class StationEmblemTest {
    @Test
    fun namesWithCjkUseTheirFirstCjkCharacter() {
        assertEquals("香", monogramOf("香港電台第二台"))
        assertEquals("케", monogramOf("케이비에스 쿨FM"))
        assertEquals("新", monogramOf("CCTV-13新闻伴音"))
        assertEquals("中", monogramOf("CNR-1 中国之声"))
    }

    @Test
    fun latinNamesSkipGenericWords() {
        assertEquals("B2", monogramOf("BBC Radio 2"))
        assertEquals("P", monogramOf("Radio Paradise"))
        assertEquals("1", monogramOf("181.fm"))
    }

    @Test
    fun genericOnlyNamesStillGetALetter() {
        assertEquals("RF", monogramOf("Radio FM"))
        assertEquals("♪", monogramOf("  ★ "))
    }
}
