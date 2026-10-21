package app.tuneroam.radio

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.tuneroam.radio.data.local.CatalogCache
import app.tuneroam.radio.data.local.SqlFavoritesRepository
import app.tuneroam.radio.data.local.TuneRoamDatabase
import app.tuneroam.radio.domain.model.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun migrationDropsCachedSearchesButKeepsOtherBuckets() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TuneRoamDatabase.Schema.create(driver)
        val database = TuneRoamDatabase(driver)
        val cache = CatalogCache(database)
        cache.writeStations("search:NAME:true:50:jazz:", listOf(station("1", "Jazz")))
        cache.writeStations("popular:true:40", listOf(station("2", "Top")))

        assertEquals(3L, TuneRoamDatabase.Schema.version)
        TuneRoamDatabase.Schema.migrate(driver, 2, 3)

        assertTrue(cache.readStations("search:NAME:true:50:jazz:").isEmpty())
        assertEquals(listOf("2"), cache.readStations("popular:true:40").map { it.stationUuid })
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
