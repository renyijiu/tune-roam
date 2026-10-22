package app.tuneroam.radio

import app.tuneroam.radio.domain.model.SearchField
import app.tuneroam.radio.presentation.HomeViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSearchTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun switchingFieldRerunsATypedQuery() {
        val catalog = FakeCatalog()
        val home = home(catalog)
        home.onQueryChange("jazz")
        home.submit()
        home.onField(SearchField.TAG)

        assertEquals(listOf(SearchField.NAME, SearchField.TAG), catalog.searches.map { it.field })
        assertEquals("jazz", catalog.searches.last().text)
    }

    @Test
    fun switchingFieldWithNoQueryOrToTheSameFieldDoesNotSearch() {
        val catalog = FakeCatalog()
        val home = home(catalog)
        home.onField(SearchField.COUNTRY)
        home.onQueryChange("JP")
        home.onField(SearchField.COUNTRY)

        assertTrue(catalog.searches.isEmpty())
    }

    private fun home(catalog: FakeCatalog) = HomeViewModel(
        catalog = catalog,
        favorites = FakeFavorites(),
        commands = RecordingCommands(),
        reachability = FakeReachability(),
        connectivity = FakeConnectivity(emptySet()),
    )
}
