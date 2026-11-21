package app.tuneroam.radio

import app.tuneroam.radio.domain.catalog.FavoritesRefresh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class FavoritesRefreshTest {
    @Test
    fun savedCopiesTakeTheCatalogsCurrentStream() = runTest {
        val favorites = FakeFavorites(listOf(testStation("a"), testStation("b")))
        val moved = testStation("a").copy(urlResolved = "https://new.test/a", name = "A, renamed")
        val catalog = FakeCatalog().apply { live = listOf(moved) }

        FavoritesRefresh(favorites, catalog).refreshIfDue()

        // "b" is gone from the catalog: its saved copy stays as it was.
        assertEquals(listOf(moved, testStation("b")), favorites.list())
        assertEquals(listOf(listOf("a", "b")), catalog.latestCalls)
    }

    @Test
    fun refreshesAtMostOncePerInterval() = runTest {
        var now = 0L
        val favorites = FakeFavorites(listOf(testStation("a")))
        val catalog = FakeCatalog().apply { live = listOf(testStation("a")) }
        val refresh = FavoritesRefresh(favorites, catalog, clock = { now }, intervalMs = 1_000)

        refresh.refreshIfDue()
        now = 999
        refresh.refreshIfDue()
        assertEquals(1, catalog.latestCalls.size)

        now = 1_000
        refresh.refreshIfDue()
        assertEquals(2, catalog.latestCalls.size)
    }

    @Test
    fun unreachableCatalogTriesAgainNextTime() = runTest {
        val favorites = FakeFavorites(listOf(testStation("a")))
        val catalog = FakeCatalog()
        val refresh = FavoritesRefresh(favorites, catalog, clock = { 0L })

        refresh.refreshIfDue()
        catalog.live = listOf(testStation("a").copy(name = "Back online"))
        refresh.refreshIfDue()

        assertEquals("Back online", favorites.list().single().name)
    }

    @Test
    fun entryWithoutAStreamDoesNotReplaceTheSavedOne() = runTest {
        val favorites = FakeFavorites(listOf(testStation("a")))
        val catalog = FakeCatalog().apply { live = listOf(testStation("a").copy(url = "", urlResolved = "")) }

        FavoritesRefresh(favorites, catalog).refreshIfDue()

        assertEquals(listOf(testStation("a")), favorites.list())
    }
}
