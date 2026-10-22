package app.tuneroam.radio

import app.tuneroam.radio.domain.model.AppSettings
import app.tuneroam.radio.domain.model.RoamFilter
import app.tuneroam.radio.domain.playback.RoamDeck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RoamFilterTest {
    private fun station(id: String, bitrate: Int = 128, language: String = "", tags: String = "") =
        testStation(id, tags = tags).copy(bitrate = bitrate, language = language)

    @Test
    fun bitrateKeepsUnknownAndDropsKnownLow() {
        val filter = RoamFilter(minBitrate = 128)
        assertTrue(filter.matches(station("a", bitrate = 320)))
        assertTrue(filter.matches(station("hls", bitrate = 0)))
        assertFalse(filter.matches(station("low", bitrate = 64)))
    }

    @Test
    fun languagesAndGenresMatchAnyChoiceAndExclusionsWin() {
        val filter = RoamFilter(
            languages = setOf("chinese", "japanese"),
            tags = setOf("pop", "jazz"),
            excludedTags = setOf("news"),
        )
        assertTrue(filter.matches(station("hk", language = "cantonese", tags = "cantopop,hits")))
        assertTrue(filter.matches(station("jp", language = "Japanese", tags = "smooth jazz")))
        assertFalse(filter.matches(station("en", language = "english", tags = "pop")))
        assertFalse(filter.matches(station("talk", language = "chinese", tags = "pop,news")))
        assertFalse(filter.matches(station("rock", language = "chinese", tags = "rock")))
    }

    @Test
    fun onlySingleChoicesGoIntoTheQuery() {
        assertEquals(
            mapOf("bitrateMin" to "192", "language" to "chinese", "tag" to "jazz"),
            RoamFilter(minBitrate = 192, languages = setOf("chinese"), tags = setOf("jazz")).queryParameters(),
        )
        assertEquals(emptyMap(), RoamFilter(languages = setOf("chinese", "english")).queryParameters())
        assertFalse(RoamFilter().isActive)
    }

    @Test
    fun deckDropsItsLookaheadWhenTheFilterChanges() = runTest {
        val jazz = station("jazz", tags = "jazz")
        val news = station("news", tags = "news")
        val catalog = FakeCatalog(random = listOf(news, jazz))
        val settings = FakeSettings()
        val deck = RoamDeck(catalog, FakeReachability(), FakeConnectivity(setOf("jazz", "news")), settings, this)
        assertEquals("news", deck.next()?.stationUuid)

        settings.state.value = AppSettings(filter = RoamFilter(excludedTags = setOf("news")))
        assertEquals("jazz", deck.next()?.stationUuid)
        assertEquals(setOf("news"), catalog.lastFilter.excludedTags)
    }
}
