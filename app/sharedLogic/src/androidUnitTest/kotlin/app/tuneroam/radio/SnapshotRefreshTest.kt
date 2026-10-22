package app.tuneroam.radio

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.tuneroam.radio.data.http.installRadioBrowser
import app.tuneroam.radio.data.local.CatalogCache
import app.tuneroam.radio.data.local.SnapshotStore
import app.tuneroam.radio.data.local.TuneRoamDatabase
import app.tuneroam.radio.data.remote.BundledCatalog
import app.tuneroam.radio.data.remote.FallbackCatalog
import app.tuneroam.radio.data.remote.MirrorMemory
import app.tuneroam.radio.data.remote.RadioBrowserClient
import app.tuneroam.radio.data.remote.ServerDirectory
import app.tuneroam.radio.data.repository.RadioBrowserSnapshot
import app.tuneroam.radio.domain.model.SnapshotResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SnapshotRefreshTest {
    private val database = TuneRoamDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { TuneRoamDatabase.Schema.create(it) },
    )

    @Test
    fun downloadReplacesTheBundledListAndSurvivesARestart() = runTest {
        val store = SnapshotStore(database, CatalogCache(database))
        assertNull(store.observe().first().downloadedAtMs)
        assertEquals(BundledCatalog.all().size, store.stations().size)

        val connectivity = FakeConnectivity(answering = setOf("CN-0"))
        val reach = FakeReachability()
        val result = refresher(store, connectivity, reach, catalogPerRegion = 60).refresh()

        // 50 + 25 + 20 + 15 (Singapore keeps only Chinese stations) + 40 worldwide.
        assertEquals(SnapshotResult.Updated(150), result)
        assertEquals(150, store.stations().size)
        assertEquals(1_234L, store.observe().first().downloadedAtMs)
        assertTrue(FallbackCatalog(store::stations).all().any { it.stationUuid == "SG-1" })
        assertTrue(store.stations().none { it.stationUuid == "SG-0" }, "SG-0 is English and should be skipped")

        // Some Chinese stations were probed on this network and the answers recorded.
        assertTrue(connectivity.probed.isNotEmpty() && connectivity.probed.size <= 24)
        assertTrue(("CN-0" to true) in reach.noted)

        val reopened = SnapshotStore(database, CatalogCache(database))
        assertEquals(150, reopened.stations().size)
        assertEquals(1_234L, reopened.observe().first().downloadedAtMs)
    }

    @Test
    fun unreachableDirectoryKeepsTheCurrentList() = runTest {
        val store = SnapshotStore(database, CatalogCache(database))
        val result = refresher(store, catalogPerRegion = 60, reachable = false).refresh()
        assertIs<SnapshotResult.Failed>(result)
        assertNull(store.observe().first().downloadedAtMs)
        assertEquals(BundledCatalog.all().size, store.stations().size)
    }

    @Test
    fun tooShortAnAnswerKeepsTheCurrentList() = runTest {
        val store = SnapshotStore(database, CatalogCache(database))
        val result = refresher(store, catalogPerRegion = 5).refresh()
        assertIs<SnapshotResult.TooFew>(result)
        assertNull(store.observe().first().downloadedAtMs)
    }

    private fun refresher(
        store: SnapshotStore,
        connectivity: FakeConnectivity = FakeConnectivity(emptySet()),
        reach: FakeReachability = FakeReachability(),
        catalogPerRegion: Int,
        reachable: Boolean = true,
    ): RadioBrowserSnapshot {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val json = """application/json"""
            when {
                !reachable -> respondError(HttpStatusCode.ServiceUnavailable)
                path == "/json/stats" -> respond("{}", headers = headersOf("Content-Type", json))
                path == "/json/stations/search" -> {
                    val code = request.url.parameters["countrycode"].orEmpty()
                    respond(stations(code, catalogPerRegion), headers = headersOf("Content-Type", json))
                }
                path.startsWith("/json/stations/topclick/") ->
                    respond(stations("WW", catalogPerRegion * 3), headers = headersOf("Content-Type", json))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val http = HttpClient(engine) { installRadioBrowser() }
        val servers = ServerDirectory(http, NoMemory, knownHosts = listOf("mirror.test"))
        return RadioBrowserSnapshot(RadioBrowserClient(http, servers), store, connectivity, reach, clock = { 1_234L })
    }

    /** Singapore's even-numbered stations are English, to check the Chinese-only rule. */
    private fun stations(code: String, count: Int): String = (0 until count).joinToString(",", "[", "]") { index ->
        val language = if (code == "SG" && index % 2 == 0) "english" else "chinese"
        """{"stationuuid":"$code-$index","name":"$code $index","url":"http://example.test/$code/$index",""" +
            """"url_resolved":"http://example.test/$code/$index","countrycode":"$code","language":"$language",""" +
            """"lastcheckok":1,"clickcount":${1_000 - index}}"""
    }

    private object NoMemory : MirrorMemory {
        override fun read(): String? = null

        override fun write(host: String, latencyMs: Long) = Unit
    }
}
