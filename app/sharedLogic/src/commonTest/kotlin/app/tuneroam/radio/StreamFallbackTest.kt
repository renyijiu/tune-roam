package app.tuneroam.radio

import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.SearchField
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.respecting
import app.tuneroam.radio.domain.model.similarQuery
import app.tuneroam.radio.domain.model.toQueryMap
import app.tuneroam.radio.domain.playback.EnginePhase
import app.tuneroam.radio.domain.playback.PlayDirective
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.PlaybackNotice
import app.tuneroam.radio.domain.playback.PlaybackPhase
import app.tuneroam.radio.domain.playback.StreamFallback
import app.tuneroam.radio.domain.playback.StreamSession
import app.tuneroam.radio.domain.playback.StreamStep
import app.tuneroam.radio.domain.playback.StreamUrlKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamFallbackTest {
    @Test
    fun resolvedComesBeforeOriginalWhenTheyDiffer() {
        val candidates = StreamFallback.candidates(
            urlResolved = " http://cdn.example/a ",
            url = "http://origin.example/a",
        )
        assertEquals(StreamUrlKind.RESOLVED, candidates[0].kind)
        assertEquals("http://cdn.example/a", candidates[0].url)
        assertEquals(StreamUrlKind.ORIGINAL, candidates[1].kind)
        assertEquals("http://origin.example/a", candidates[1].url)
    }

    @Test
    fun identicalAddressesAreTriedOnce() {
        val candidates = StreamFallback.candidates(
            urlResolved = "http://same.example/a",
            url = "http://same.example/a",
        )
        assertEquals(1, candidates.size)
        assertEquals(StreamUrlKind.RESOLVED, candidates.single().kind)
    }

    @Test
    fun blankResolvedFallsThroughToOriginal() {
        val candidates = StreamFallback.candidates(urlResolved = "  ", url = "http://only.example/a")
        assertEquals(listOf("http://only.example/a"), candidates.map { it.url })
        assertEquals(StreamUrlKind.ORIGINAL, candidates.single().kind)
    }

    @Test
    fun emptyAddressesProduceNoCandidate() {
        assertTrue(StreamFallback.candidates("", " ").isEmpty())
    }

    @Test
    fun sessionAdvancesThenExhausts() {
        val session = StreamSession("http://cdn", "http://origin")
        assertEquals("http://cdn", session.current?.url)
        assertIs<StreamStep.Try>(session.failAndNext())
        assertEquals("http://origin", session.current?.url)
        assertIs<StreamStep.Exhausted>(session.failAndNext())
        assertIs<StreamStep.Exhausted>(session.failAndNext())
    }

    @Test
    fun coordinatorOpensResolvedThenOriginalThenGivesUp() {
        var now = 1_000L
        val coordinator = PlaybackCoordinator(clock = { now })
        val first = coordinator.begin(station())
        val opened = assertIs<PlayDirective.Open>(first)
        assertEquals("http://cdn.example/live", opened.candidate.url)
        assertEquals(PlaybackPhase.CONNECTING, coordinator.snapshot.value.phase)
        assertEquals(1, coordinator.snapshot.value.attempt)
        assertEquals(2, coordinator.snapshot.value.attemptCount)

        val second = coordinator.fail()
        val alternate = assertIs<PlayDirective.Open>(second)
        assertEquals("http://origin.example/live", alternate.candidate.url)
        assertEquals(PlaybackNotice.TRYING_ALTERNATE, coordinator.snapshot.value.notice)
        assertEquals(StreamUrlKind.ORIGINAL, coordinator.snapshot.value.activeKind)

        assertIs<PlayDirective.Exhausted>(coordinator.fail())
        assertEquals(PlaybackPhase.FAILED, coordinator.snapshot.value.phase)
        assertEquals(PlaybackNotice.UNAVAILABLE, coordinator.snapshot.value.notice)
        assertIs<PlayDirective.Exhausted>(coordinator.fail())
    }

    @Test
    fun timeoutWhileBufferingTriesTheNextUrl() {
        var now = 0L
        val coordinator = PlaybackCoordinator(clock = { now })
        coordinator.begin(station())
        now = StreamFallback.START_TIMEOUT_MS - 1
        assertIs<PlayDirective.Stay>(coordinator.onClock(EnginePhase.BUFFERING))
        now = StreamFallback.START_TIMEOUT_MS
        val next = assertIs<PlayDirective.Open>(coordinator.onClock(EnginePhase.BUFFERING))
        assertEquals(StreamUrlKind.ORIGINAL, next.candidate.kind)
    }

    @Test
    fun playingDoesNotTripTheTimeout() {
        var now = 0L
        val coordinator = PlaybackCoordinator(clock = { now })
        coordinator.begin(station())
        now = 60_000
        assertIs<PlayDirective.Stay>(coordinator.onClock(EnginePhase.PLAYING))
        coordinator.onPlaying()
        assertEquals(PlaybackPhase.PLAYING, coordinator.snapshot.value.phase)
        assertEquals(PlaybackNotice.NONE, coordinator.snapshot.value.notice)
    }

    @Test
    fun stationWithNoUrlsFailsImmediately() {
        val coordinator = PlaybackCoordinator()
        val directive = coordinator.begin(station(url = " ", urlResolved = ""))
        assertIs<PlayDirective.Exhausted>(directive)
        assertEquals(PlaybackNotice.UNAVAILABLE, coordinator.snapshot.value.notice)
    }

    @Test
    fun icyTitleIgnoresTheStationName() {
        val coordinator = PlaybackCoordinator()
        coordinator.begin(station())
        coordinator.onIcyTitle("Night Desk")
        assertNull(coordinator.snapshot.value.icyTitle)
        coordinator.onIcyTitle("  Billie Holiday - Solitude  ")
        assertEquals("Billie Holiday - Solitude", coordinator.snapshot.value.icyTitle)
    }

    @Test
    fun brokenStationsAreHiddenByDefault() {
        val stations = listOf(station().copy(lastCheckOk = true), station().copy(stationUuid = "x", lastCheckOk = false))
        assertEquals(1, stations.respecting(hideBroken = true).size)
        assertEquals(2, stations.respecting(hideBroken = false).size)
    }

    @Test
    fun searchQueryMapsNameTagAndCountryCode() {
        assertEquals(
            "jazz",
            CatalogQuery(text = " jazz ", field = SearchField.NAME).toQueryMap()["name"],
        )
        assertEquals(
            "ambient",
            CatalogQuery(text = "ambient", field = SearchField.TAG).toQueryMap()["tag"],
        )
        val byCode = CatalogQuery(text = "jp", field = SearchField.COUNTRY, hideBroken = true).toQueryMap()
        assertEquals("JP", byCode["countrycode"])
        assertEquals("true", byCode["hidebroken"])
        assertEquals(
            "France",
            CatalogQuery(country = "France", hideBroken = false).toQueryMap()["country"],
        )
    }

    @Test
    fun similarStationPrefersTheFirstTag() {
        val query = similarQuery(station().copy(tags = "jazz, news"))
        assertEquals(SearchField.TAG, query.field)
        assertEquals("jazz", query.text)
        val byCountry = similarQuery(station().copy(tags = "", country = "Japan"))
        assertEquals("Japan", byCountry.country)
    }

    private fun station(
        url: String = "http://origin.example/live",
        urlResolved: String = "http://cdn.example/live",
    ) = Station(
        stationUuid = "uuid-1",
        name = "Night Desk",
        url = url,
        urlResolved = urlResolved,
        country = "France",
        codec = "MP3",
        bitrate = 128,
        lastCheckOk = true,
    )
}
