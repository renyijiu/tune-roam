package app.tuneroam.radio

import app.tuneroam.radio.domain.catalog.WorldDial
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.RoamDeck
import app.tuneroam.radio.domain.playback.SimilarStations
import app.tuneroam.radio.presentation.RoamViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
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
    fun dialFollowsThePlayingStationAndStaysPutOffTheDial() = runTest {
        val coordinator = PlaybackCoordinator()
        val commands = RecordingCommands()
        val roam = roam(FakeCatalog(), commands, coordinator)

        coordinator.begin(testStation("jp", countryCode = "JP"))
        assertEquals("jp", roam.state.value.station?.stationUuid)
        assertEquals(WorldDial.indexOfCode("JP"), roam.state.value.index)

        // Peru has no stop on the dial: the station shows, the dial stays on Japan.
        coordinator.begin(testStation("pe", countryCode = "PE"))
        assertEquals("pe", roam.state.value.station?.stationUuid)
        assertEquals(WorldDial.indexOfCode("JP"), roam.state.value.index)

        roam.shuffle()
        assertEquals(1, commands.nexts)
    }

    @Test
    fun dialKeepsTheFirstPickWhileTheLiveListArrives() = runTest {
        val cached = testStation("cached")
        val live = testStation("live")
        val commands = RecordingCommands()
        val roam = roam(FakeCatalog(inCountry = listOf(listOf(cached), listOf(live))), commands)
        roam.onDialSettled((WorldDial.homeIndex + 1) % WorldDial.stops.size)
        assertEquals("cached", roam.state.value.station?.stationUuid)
        assertEquals(listOf("cached"), commands.played.map { it.stationUuid })
    }

    private fun roam(
        catalog: FakeCatalog,
        commands: RecordingCommands,
        coordinator: PlaybackCoordinator = PlaybackCoordinator(),
    ) = RoamViewModel(
        catalog = catalog,
        commands = commands,
        coordinator = coordinator,
        deck = RoamDeck(catalog, FakeReachability(), FakeConnectivity(emptySet()), FakeSettings(), CoroutineScope(Dispatchers.Unconfined)),
        randomIndex = { 0 },
    )
}
