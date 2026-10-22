package app.tuneroam.radio.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryTreeTest {
    @Test
    fun stationIdsRoundTripAndOtherIdsAreNotStations() {
        val uuid = "96062a7b-0601-11e8-ae97-52543be04c81"
        assertEquals(uuid, LibraryTree.stationUuid(LibraryTree.station(uuid)))
        assertNull(LibraryTree.stationUuid(LibraryTree.station("")))
        assertNull(LibraryTree.stationUuid(LibraryTree.country("Japan")))
        assertNull(LibraryTree.stationUuid(LibraryTree.FAVORITES))
        assertNull(LibraryTree.stationUuid(""))
    }
}
