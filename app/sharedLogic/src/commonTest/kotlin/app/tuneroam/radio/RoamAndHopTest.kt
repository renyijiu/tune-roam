package app.tuneroam.radio

import app.tuneroam.radio.domain.catalog.ReachScore
import app.tuneroam.radio.domain.catalog.WorldDial
import app.tuneroam.radio.domain.playback.SimilarStations
import app.tuneroam.radio.presentation.RoamViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RoamAndHopTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun hopSkipsStationsThatDoNotAnswer() = runTest {
        val failed = testStation("gone", tags = "jazz")
        val silent = testStation("silent")
        val live = testStation("live")
        val reach = FakeReachability()
        val connectivity = FakeConnectivity(answering = setOf("live"))
        val hopper = SimilarStations(FakeCatalog(similar = listOf(silent, live)), reach, connectivity)

        assertEquals("live", hopper.findReachable(failed)?.stationUuid)
        assertEquals(listOf("silent", "live"), connectivity.probed)
        assertTrue(("gone" to false) in reach.noted)
        assertTrue(("silent" to false) in reach.noted)
    }

    @Test
    fun hopRespectsExclusionsAndGivesUp() = runTest {
        val failed = testStation("gone")
        val tried = testStation("tried")
        val hopper = SimilarStations(
            FakeCatalog(similar = listOf(tried)),
            FakeReachability(),
            FakeConnectivity(answering = setOf("tried")),
        )
        assertNull(hopper.findReachable(failed, excluded = setOf("tried")))
    }

    @Test
    fun shuffleDrawsFromTheLiveDeckAndRefillsOnlyWhenEmpty() = runTest {
        val batch = listOf(
            testStation("jp", countryCode = "JP", country = "Japan"),
            testStation("pe", countryCode = "PE", country = "Peru"),
        )
        val catalog = FakeCatalog(random = batch)
        val commands = RecordingCommands()
        val roam = RoamViewModel(catalog, commands, FakeReachability())

        roam.shuffle()
        assertEquals("jp", roam.state.value.station?.stationUuid)
        assertEquals(WorldDial.indexOfCode("JP"), roam.state.value.index)

        // Peru has no stop on the dial: the station plays, the dial stays on Japan.
        roam.shuffle()
        assertEquals("pe", roam.state.value.station?.stationUuid)
        assertEquals(WorldDial.indexOfCode("JP"), roam.state.value.index)
        assertEquals(1, catalog.randomCalls)

        roam.shuffle()
        assertEquals(2, catalog.randomCalls)
        assertEquals(listOf("jp", "pe", "jp"), commands.played.map { it.stationUuid })
    }

    @Test
    fun shuffleMovesStationsThatFailedHereToTheBack() = runTest {
        val dead = testStation("dead", countryCode = "FR")
        val fresh = testStation("fresh", countryCode = "DE")
        val reach = FakeReachability(mapOf("dead" to ReachScore(failures = 2, lastOk = false)))
        val roam = RoamViewModel(FakeCatalog(random = listOf(dead, fresh)), RecordingCommands(), reach)

        roam.shuffle()
        assertEquals("fresh", roam.state.value.station?.stationUuid)
    }

    @Test
    fun dialKeepsTheFirstPickWhileTheLiveListArrives() = runTest {
        val cached = testStation("cached")
        val live = testStation("live")
        val commands = RecordingCommands()
        val roam = RoamViewModel(
            FakeCatalog(inCountry = listOf(listOf(cached), listOf(live))),
            commands,
            FakeReachability(),
            randomIndex = { 0 },
        )
        roam.onDialSettled((WorldDial.homeIndex + 1) % WorldDial.stops.size)
        assertEquals("cached", roam.state.value.station?.stationUuid)
        assertEquals(listOf("cached"), commands.played.map { it.stationUuid })
    }
}
