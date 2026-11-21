package app.tuneroam.radio

import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.EnginePhase
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.PlaybackEngine
import app.tuneroam.radio.domain.playback.PlaybackNotice
import app.tuneroam.radio.domain.playback.PlaybackPhase
import app.tuneroam.radio.domain.playback.PlaybackSession
import app.tuneroam.radio.domain.playback.RoamDeck
import app.tuneroam.radio.domain.playback.SimilarStations
import app.tuneroam.radio.domain.playback.SleepTimer
import app.tuneroam.radio.domain.playback.StreamCandidate
import app.tuneroam.radio.domain.playback.StreamFallback
import app.tuneroam.radio.domain.repository.HistoryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionTest {
    @Test
    fun slowStartFallsBackToTheOtherAddressThenHopsToASimilarStation() = runTest {
        val dead = testStation("dead").copy(urlResolved = "http://a.test/resolved", url = "http://a.test/original")
        val live = testStation("live")
        val rig = rig(similar = listOf(live), answering = setOf("live"))

        assertEquals("http://a.test/resolved", rig.session.prepare(dead)?.url)
        // Still buffering past the start timeout: the original address next.
        advanceTimeBy(StreamFallback.START_TIMEOUT_MS + PlaybackSession.WATCHDOG_TICK_MS + 1)
        assertEquals(listOf("dead" to "http://a.test/original"), rig.engine.opened)

        // That one hangs too: give up on the station and hop.
        advanceTimeBy(StreamFallback.START_TIMEOUT_MS + PlaybackSession.WATCHDOG_TICK_MS + 1)
        runCurrent()
        assertEquals(1, rig.engine.pauses)
        assertEquals("live", rig.engine.opened.last().first)
        assertEquals(PlaybackNotice.NEXT_SIMILAR, rig.session.snapshot.value.notice)
    }

    @Test
    fun automaticHopsStopAfterThree() = runTest {
        val pool = List(6) { testStation("s$it") }
        val rig = rig(similar = pool, answering = pool.map { it.stationUuid }.toSet())

        rig.session.prepare(testStation("first"))
        repeat(5) {
            rig.session.onStreamFailed()
            runCurrent()
        }

        assertEquals(PlaybackSession.MAX_AUTO_HOPS, rig.engine.opened.size)
        assertEquals(PlaybackPhase.FAILED, rig.session.snapshot.value.phase)
        // A station the listener picks gets a fresh budget.
        rig.session.prepare(testStation("chosen"))
        rig.session.onStreamFailed()
        runCurrent()
        assertEquals(PlaybackSession.MAX_AUTO_HOPS + 1, rig.engine.opened.size)
    }

    @Test
    fun playingStopsTheWatchdogAndIsRecordedOnce() = runTest {
        val rig = rig()
        val station = testStation("on")

        rig.session.prepare(station)
        rig.engine.phase = EnginePhase.PLAYING
        rig.session.onPlaying()
        rig.session.onPlaying()
        advanceTimeBy(StreamFallback.START_TIMEOUT_MS * 3)
        runCurrent()

        assertEquals(emptyList(), rig.engine.opened)
        assertEquals(PlaybackPhase.PLAYING, rig.session.snapshot.value.phase)
        assertEquals(listOf("on"), rig.history.recorded)
        assertEquals(listOf("on" to true), rig.reach.noted)
    }

    @Test
    fun longPauseReopensAtTheLiveEdgeAndShortOneResumes() = runTest {
        val rig = rig()
        val station = testStation("live")
        rig.session.prepare(station)
        rig.session.onPlaying()

        rig.session.onPlayWhenReadyChanged(false)
        advanceTimeBy(5_000)
        rig.session.onPlayWhenReadyChanged(true)
        runCurrent()
        assertEquals(emptyList(), rig.engine.opened)

        rig.session.onPlayWhenReadyChanged(false)
        advanceTimeBy(PlaybackSession.LIVE_RESUME_AFTER_MS)
        rig.session.onPlayWhenReadyChanged(true)
        runCurrent()
        assertEquals(listOf("live" to station.urlResolved), rig.engine.opened)
    }

    @Test
    fun sleepTimerPausesTheEngine() = runTest {
        val rig = rig()
        rig.sleepTimer.arm(minutes = 1)

        advanceTimeBy(59_000)
        assertEquals(0, rig.engine.pauses)
        advanceTimeBy(1_001)
        assertEquals(1, rig.engine.pauses)
    }

    @Test
    fun nextOpensAStationFromTheDeck() = runTest {
        val rig = rig(random = listOf(testStation("r1")), answering = setOf("r1"))

        rig.session.skipToNext()
        runCurrent()

        assertEquals(listOf("r1" to "http://example.test/r1"), rig.engine.opened)
        assertEquals("r1", rig.session.snapshot.value.station?.stationUuid)
    }

    @Test
    fun resumablePrefersTheStationOnShow() = runTest {
        val last = testStation("last")
        val rig = rig(history = RecordingHistory(latest = last))
        assertEquals(last, rig.session.resumable())

        rig.session.prepare(testStation("shown"))
        assertEquals("shown", rig.session.resumable()?.stationUuid)
    }

    private class Rig(
        val session: PlaybackSession,
        val engine: FakeEngine,
        val history: RecordingHistory,
        val reach: FakeReachability,
        val sleepTimer: SleepTimer,
    )

    private fun TestScope.rig(
        similar: List<Station> = emptyList(),
        random: List<Station> = emptyList(),
        answering: Set<String> = emptySet(),
        history: RecordingHistory = RecordingHistory(),
    ): Rig {
        val clock = { testScheduler.currentTime }
        val catalog = FakeCatalog(similar = similar, random = random)
        val reach = FakeReachability()
        val connectivity = FakeConnectivity(answering)
        val engine = FakeEngine()
        val sleepTimer = SleepTimer(clock)
        val session = PlaybackSession(
            engine = engine,
            scope = backgroundScope,
            coordinator = PlaybackCoordinator(clock),
            catalog = catalog,
            reachability = reach,
            history = history,
            similarStations = SimilarStations(catalog, reach, connectivity),
            deck = RoamDeck(catalog, reach, connectivity, FakeSettings(), backgroundScope),
            sleepTimer = sleepTimer,
            clock = clock,
        )
        runCurrent()
        // Only what happens after setup counts.
        reach.noted.clear()
        return Rig(session, engine, history, reach, sleepTimer)
    }

    private class FakeEngine : PlaybackEngine {
        val opened = mutableListOf<Pair<String, String>>()
        var pauses = 0
        var phase = EnginePhase.BUFFERING

        override fun open(station: Station, candidate: StreamCandidate) {
            opened += station.stationUuid to candidate.url
            phase = EnginePhase.BUFFERING
        }

        override fun pause() {
            pauses += 1
        }

        override fun phase(): EnginePhase = phase
    }

    private class RecordingHistory(private val latest: Station? = null) : HistoryRepository {
        val recorded = mutableListOf<String>()

        override fun observe(): Flow<List<Station>> = flowOf(emptyList())

        override suspend fun latest(): Station? = latest

        override suspend fun record(station: Station) {
            recorded += station.stationUuid
        }
    }
}
