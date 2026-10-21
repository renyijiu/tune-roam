package app.tuneroam.radio

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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

    private fun station(id: String, name: String) = Station(
        stationUuid = id,
        name = name,
        url = "http://origin/$id",
        urlResolved = "http://cdn/$id",
        country = "France",
        lastCheckOk = true,
    )
}
