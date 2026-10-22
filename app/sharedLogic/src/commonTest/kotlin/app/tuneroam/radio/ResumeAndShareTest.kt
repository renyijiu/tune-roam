package app.tuneroam.radio

import app.tuneroam.radio.domain.model.StationLink
import app.tuneroam.radio.domain.playback.PlaybackCoordinator
import app.tuneroam.radio.domain.playback.PlaybackPhase
import app.tuneroam.radio.domain.playback.RoamDeck
import app.tuneroam.radio.domain.playback.SimilarStations
import app.tuneroam.radio.domain.playback.SleepTimer
import app.tuneroam.radio.presentation.PlayerHint
import app.tuneroam.radio.presentation.PlayerViewModel
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
class ResumeAndShareTest {
    private val uuid = "96062a7b-0601-11e8-ae97-52543be04c81"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun linkRoundTripsAndRejectsOtherLinks() {
        val station = testStation(uuid, country = "Japan")
        val link = StationLink.build(station)
        assertEquals("tuneroam://station/$uuid", link)
        assertEquals(uuid, StationLink.parse(link))
        assertEquals(uuid, StationLink.parse("TuneRoam://station/${uuid.uppercase()}/?from=wechat"))
        assertTrue(StationLink.shareText(station).endsWith(link))
        assertNull(StationLink.parse("tuneroam://station/not-a-uuid"))
        assertNull(StationLink.parse("https://example.test/station/$uuid"))
    }

    @Test
    fun restoreShowsTheLastStationButNeverReplacesAPlayingOne() {
        val coordinator = PlaybackCoordinator()
        coordinator.restore(testStation("last"))
        assertEquals("last", coordinator.snapshot.value.station?.stationUuid)
        assertEquals(PlaybackPhase.IDLE, coordinator.snapshot.value.phase)

        coordinator.begin(testStation("now"))
        coordinator.restore(testStation("late"))
        assertEquals("now", coordinator.snapshot.value.station?.stationUuid)
    }

    @Test
    fun coldStartRestoresTheLastStationAndPlayOpensIt() = runTest {
        val coordinator = PlaybackCoordinator()
        val commands = RecordingCommands()
        val player = player(coordinator, commands, history = FakeHistory(testStation("last")))
        assertEquals("last", coordinator.snapshot.value.station?.stationUuid)

        player.togglePlay()
        assertEquals(listOf("last"), commands.played.map { it.stationUuid })
        assertEquals(0, commands.toggles)

        coordinator.begin(testStation("last"))
        player.togglePlay()
        assertEquals(1, commands.toggles)
    }

    @Test
    fun linkPlaysASavedStationOrLooksItUp() = runTest {
        val commands = RecordingCommands()
        val saved = testStation(uuid)
        val player = player(PlaybackCoordinator(), commands, favorites = FakeFavorites(listOf(saved)))
        player.openLink(StationLink.build(saved))
        assertEquals(listOf(uuid), commands.played.map { it.stationUuid })
        assertNull(player.hint.value)

        val missing = "00000000-0000-0000-0000-000000000000"
        player.openLink("tuneroam://station/$missing")
        assertEquals(PlayerHint.LINK_NOT_FOUND, player.hint.value)
        player.openLink("tuneroam://station/garbage")
        assertEquals(PlayerHint.LINK_NOT_FOUND, player.hint.value)
    }

    private fun player(
        coordinator: PlaybackCoordinator,
        commands: RecordingCommands,
        history: FakeHistory = FakeHistory(),
        favorites: FakeFavorites = FakeFavorites(),
    ): PlayerViewModel {
        val catalog = FakeCatalog()
        val reach = FakeReachability()
        val connectivity = FakeConnectivity(emptySet())
        return PlayerViewModel(
            coordinator = coordinator,
            favorites = favorites,
            commands = commands,
            similarStations = SimilarStations(catalog, reach, connectivity),
            sleepTimer = SleepTimer(),
            deck = RoamDeck(catalog, reach, connectivity, CoroutineScope(Dispatchers.Unconfined)),
            history = history,
            catalog = catalog,
        )
    }
}
