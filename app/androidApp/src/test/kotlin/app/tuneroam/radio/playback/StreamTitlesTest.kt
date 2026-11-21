package app.tuneroam.radio.playback

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.PrivFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamTitlesTest {
    @Test
    fun icyStreamTitleIsTheSong() {
        assertEquals("Moon Duo - Lost Heads", streamTrackTitle(Metadata(icy("Moon Duo - Lost Heads"))))
    }

    @Test
    fun emptyIcyTitleClearsTheSong() {
        assertEquals("", streamTrackTitle(Metadata(icy(""))))
        assertEquals("", streamTrackTitle(Metadata(icy(null))))
    }

    @Test
    fun id3TitleJoinsItsArtist() {
        val metadata = Metadata(
            TextInformationFrame("TIT2", null, listOf("Lost Heads")),
            TextInformationFrame("TPE1", null, listOf("Moon Duo")),
        )
        assertEquals("Moon Duo - Lost Heads", streamTrackTitle(metadata))
    }

    @Test
    fun id3TitleWithoutArtistStandsAlone() {
        assertEquals("Lost Heads", streamTrackTitle(Metadata(TextInformationFrame("TIT2", null, listOf("Lost Heads")))))
    }

    @Test
    fun metadataThatNamesNoSongKeepsTheCurrentOne() {
        assertNull(streamTrackTitle(Metadata(PrivFrame("com.apple.streaming.transportStreamTimestamp", ByteArray(8)))))
    }

    private fun icy(title: String?) = IcyInfo(ByteArray(0), title, null)
}
