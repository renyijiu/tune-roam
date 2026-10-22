package app.tuneroam.radio.data.repository

import app.tuneroam.radio.core.epochMillis
import app.tuneroam.radio.core.runCancellable
import app.tuneroam.radio.data.local.SnapshotStore
import app.tuneroam.radio.data.remote.RadioBrowserClient
import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.SearchField
import app.tuneroam.radio.domain.model.SnapshotInfo
import app.tuneroam.radio.domain.model.SnapshotResult
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import app.tuneroam.radio.domain.repository.SnapshotRepository
import app.tuneroam.radio.domain.repository.StationConnectivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Downloads the offline snapshot inside the app, the same selection
 * `scripts/refresh_bundled_snapshot.py` makes for the APK: Chinese-language stations
 * from the mainland, Taiwan, Hong Kong and Singapore, plus the most played stations
 * worldwide. Keep the two in step.
 *
 * Unlike the script, this runs on the listener's own network, so it also probes some
 * of the Chinese stations it found and records which ones this network can reach;
 * "next station" and the automatic hop then prefer those.
 */
class RadioBrowserSnapshot(
    private val client: RadioBrowserClient,
    private val store: SnapshotStore,
    private val connectivity: StationConnectivity,
    private val reachability: ReachabilityRepository,
    private val clock: () -> Long = ::epochMillis,
) : SnapshotRepository {
    /** A background run and a tap on "update now" must not interleave. */
    private val lock = Mutex()

    override fun observe(): Flow<SnapshotInfo> = store.observe()

    override suspend fun refresh(): SnapshotResult = lock.withLock {
        val fetched = runCancellable { fetch() }
        val stations = fetched.getOrElse { return SnapshotResult.Failed(it.toReason()) }
        if (stations.size < MIN_STATIONS) return SnapshotResult.TooFew(stations.size)
        withContext(Dispatchers.IO) { store.replace(stations, clock()) }
        probeSome(stations)
        SnapshotResult.Updated(stations.size)
    }

    private suspend fun fetch(): List<Station> = coroutineScope {
        val regions = REGIONS.map { region ->
            async {
                client.search(
                    CatalogQuery(text = region.code, field = SearchField.COUNTRY, hideBroken = true, limit = region.count * 4),
                )
                    .filter { it.usable() && (!region.chineseOnly || it.speaksChinese()) }
                    .take(region.count)
            }
        }
        val popular = async { client.topClicked(GLOBAL_COUNT * 3, hideBroken = true) }
        val chosen = LinkedHashMap<String, Station>()
        regions.awaitAll().flatten().forEach { chosen.getOrPut(it.stationUuid) { it } }
        popular.await()
            .filter { it.usable() && it.stationUuid !in chosen }
            .take(GLOBAL_COUNT)
            .forEach { chosen[it.stationUuid] = it }
        chosen.values.toList()
    }

    /** A few at a time: each probe is a short request, but a hundred at once is rude. */
    private suspend fun probeSome(stations: List<Station>) {
        val known = reachability.scores().keys
        stations.filter { it.speaksChinese() && it.stationUuid !in known }
            .take(PROBES)
            .chunked(PROBE_BATCH)
            .forEach { batch ->
                coroutineScope {
                    batch.map { station ->
                        async { reachability.note(station.stationUuid, connectivity.probe(station)) }
                    }.awaitAll()
                }
            }
    }

    private fun Station.usable(): Boolean = lastCheckOk && hasStream

    private fun Station.speaksChinese(): Boolean =
        CHINESE.any { language.contains(it, ignoreCase = true) }

    private data class Region(val code: String, val count: Int, val chineseOnly: Boolean)

    private companion object {
        val REGIONS = listOf(
            Region("CN", 50, chineseOnly = false),
            Region("TW", 25, chineseOnly = false),
            Region("HK", 20, chineseOnly = false),
            Region("SG", 15, chineseOnly = true),
        )
        const val GLOBAL_COUNT = 40
        const val MIN_STATIONS = 100
        const val PROBES = 24
        const val PROBE_BATCH = 6
        val CHINESE = listOf("chinese", "mandarin", "cantonese")
    }
}
