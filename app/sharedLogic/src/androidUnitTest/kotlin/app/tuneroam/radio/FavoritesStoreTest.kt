package app.tuneroam.radio

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.tuneroam.radio.data.local.SqlFavoritesRepository
import app.tuneroam.radio.data.local.SqlHistoryRepository
import app.tuneroam.radio.data.local.SqlSettingsRepository
import app.tuneroam.radio.data.local.SqlTrackRepository
import app.tuneroam.radio.data.local.TuneRoamDatabase
import app.tuneroam.radio.domain.model.AppSettings
import app.tuneroam.radio.domain.model.RoamFilter
import app.tuneroam.radio.domain.model.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class FavoritesStoreTest {
    @Test
    fun togglePersistsAndRemoves() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TuneRoamDatabase.Schema.create(driver)
        val repository = SqlFavoritesRepository(TuneRoamDatabase(driver))
        val night = station("1", "Night Desk")
        val day = station("2", "Day Desk")

        assertTrue(repository.toggle(night))
        assertTrue(repository.toggle(day))
        assertTrue(repository.isFavorite("1"))
        assertEquals(listOf("2", "1"), repository.list().map { it.stationUuid })

        assertFalse(repository.toggle(night))
        assertEquals(listOf("2"), repository.list().map { it.stationUuid })
        repository.remove("2")
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun historyLatestIsTheMostRecentPlay() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TuneRoamDatabase.Schema.create(driver)
        var now = 1_000L
        val history = SqlHistoryRepository(TuneRoamDatabase(driver), clock = { now })
        assertEquals(null, history.latest())
        history.record(station("1", "Morning"))
        now = 2_000L
        history.record(station("2", "Evening"))
        assertEquals("2", history.latest()?.stationUuid)
        now = 3_000L
        history.record(station("1", "Morning"))
        assertEquals("1", history.latest()?.stationUuid)
    }

    @Test
    fun savedTracksToggleAndIgnoreCaseAndSpacing() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TuneRoamDatabase.Schema.create(driver)
        val tracks = SqlTrackRepository(TuneRoamDatabase(driver))
        val radio = station("1", "Night Desk")

        assertTrue(tracks.toggle("Daft Punk - One More Time", radio))
        // Same song from another station, different spacing and case: already saved, so this removes it.
        assertFalse(tracks.toggle(" daft punk  -  one more time", station("2", "Day Desk")))
        assertTrue(tracks.observe().first().isEmpty())

        assertTrue(tracks.toggle("周杰伦 - 晴天", radio))
        val saved = tracks.observe().first().single()
        assertEquals("晴天", saved.song)
        assertEquals("Night Desk", saved.stationName)
        assertEquals(setOf("周杰伦 - 晴天"), tracks.observeKeys().first())
        tracks.remove(saved.id)
        assertTrue(tracks.observe().first().isEmpty())
    }

    @Test
    fun settingsPersistAndSurviveAnUnreadableRow() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TuneRoamDatabase.Schema.create(driver)
        val database = TuneRoamDatabase(driver)
        val settings = SqlSettingsRepository(database)
        assertEquals(AppSettings(), settings.current())

        settings.update { it.copy(levelVolume = false, filter = RoamFilter(minBitrate = 128, languages = setOf("chinese"))) }
        assertEquals(128, settings.observe().first().filter.minBitrate)
        assertFalse(SqlSettingsRepository(database).current().levelVolume)

        database.catalogQueries.upsertSetting("app", "{not json")
        assertEquals(AppSettings(), settings.current())
    }

    private fun station(id: String, name: String) = Station(
        stationUuid = id,
        name = name,
        url = "http://origin/$id",
        urlResolved = "http://cdn/$id",
        country = "France",
        lastCheckOk = true,
    )
}
