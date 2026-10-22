package app.tuneroam.radio.domain.playback

import app.tuneroam.radio.domain.model.RoamFilter
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.valueOrEmpty
import app.tuneroam.radio.domain.repository.RadioCatalog
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import app.tuneroam.radio.domain.repository.SettingsRepository
import app.tuneroam.radio.domain.repository.StationConnectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "Next station" and "previous station" for the whole app.
 *
 * Next draws from a random batch of the live catalog. A few stations ahead are probed
 * in the background, so pressing next usually lands on a stream this network already
 * answered instead of waiting out a dead one; that is what makes switching feel
 * instant from mainland China. Previous walks back through what was played this run,
 * and next after previous returns to where the listener was.
 *
 * Next follows the listener's [RoamFilter] (bitrate, language, genres) from settings.
 *
 * Every station that starts playing should be reported with [played], whether it came
 * from here, a list, the dial, or a car browser, so previous can go back to it.
 */
class RoamDeck(
    private val catalog: RadioCatalog,
    private val reachability: ReachabilityRepository,
    private val connectivity: StationConnectivity,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val lock = Mutex()

    /** Answered a probe on this network. */
    private val ready = ArrayDeque<Station>()

    /** From the random batch, not probed yet. */
    private val unprobed = ArrayDeque<Station>()
    private val back = ArrayDeque<Station>()
    private var current: Station? = null
    private var warmJob: Job? = null

    /** The filter [ready] and [unprobed] were drawn with. */
    private var filledWith = RoamFilter()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    /**
     * A station started playing. [replacing] is for an automatic hop away from a dead
     * stream: the dead one should not come back with previous.
     */
    suspend fun played(station: Station, replacing: Boolean = false) {
        lock.withLock {
            if (station.stationUuid == current?.stationUuid) return
            if (!replacing) current?.let(::pushBack)
            current = station
            ready.removeAll { it.stationUuid == station.stationUuid }
            _canGoBack.value = back.isNotEmpty()
        }
    }

    /** Null only when neither the live catalog nor the bundled snapshot has anything. */
    suspend fun next(): Station? {
        syncFilter()
        val pick = lock.withLock { ready.removeFirstOrNull() } ?: probeInline()
        if (pick != null) {
            lock.withLock {
                current?.let(::pushBack)
                current = pick
                _canGoBack.value = back.isNotEmpty()
            }
        }
        warmUp()
        return pick
    }

    suspend fun previous(): Station? = lock.withLock {
        val prior = back.removeLastOrNull() ?: return null
        current?.let(ready::addFirst)
        current = prior
        _canGoBack.value = back.isNotEmpty()
        prior
    }

    /**
     * Probes ahead until [READY_TARGET] stations are known to answer, or [WARM_PROBES]
     * probes have run (offline, every probe fails and this must not spin). Safe to call
     * often, from one thread.
     */
    fun warmUp() {
        if (warmJob?.isActive == true) return
        warmJob = scope.launch {
            syncFilter()
            repeat(WARM_PROBES) {
                if (lock.withLock { ready.size } >= READY_TARGET) return@launch
                val candidate = nextUnprobed() ?: return@launch
                if (probe(candidate)) lock.withLock { ready.addLast(candidate) }
            }
        }
    }

    /**
     * Drops the lookahead when the listener changed the filter since it was drawn, so
     * the next station already follows the new settings. History is kept.
     */
    private suspend fun syncFilter() {
        val filter = settings.current().filter
        lock.withLock {
            if (filter == filledWith) return
            ready.clear()
            unprobed.clear()
            filledWith = filter
        }
    }

    /** Cold start, nothing probed yet: try a few now, else hand back the first one anyway. */
    private suspend fun probeInline(): Station? {
        var fallback: Station? = null
        repeat(INLINE_PROBES) {
            val candidate = nextUnprobed() ?: return fallback
            if (fallback == null) fallback = candidate
            if (probe(candidate)) return candidate
        }
        return fallback
    }

    private suspend fun probe(station: Station): Boolean {
        val ok = connectivity.probe(station)
        reachability.note(station.stationUuid, ok)
        return ok
    }

    private suspend fun nextUnprobed(): Station? {
        lock.withLock { unprobed.removeFirstOrNull() }?.let { return it }
        val scores = reachability.scores()
        val filter = lock.withLock { filledWith }
        val batch = catalog.random(BATCH_SIZE, filter).valueOrEmpty()
            .filter { it.hasStream }
            // Already random. Stations that failed on this phone go to the back.
            .sortedBy { station -> scores[station.stationUuid]?.let { !it.lastOk } == true }
        return lock.withLock {
            val seen = buildSet {
                current?.let { add(it.stationUuid) }
                back.forEach { add(it.stationUuid) }
                ready.forEach { add(it.stationUuid) }
            }
            batch.filterTo(unprobed) { it.stationUuid !in seen }
            unprobed.removeFirstOrNull()
        }
    }

    private fun pushBack(station: Station) {
        back.removeAll { it.stationUuid == station.stationUuid }
        back.addLast(station)
        while (back.size > HISTORY_LIMIT) back.removeFirst()
    }

    private companion object {
        const val READY_TARGET = 3
        const val INLINE_PROBES = 3
        const val WARM_PROBES = 12
        const val BATCH_SIZE = 30
        const val HISTORY_LIMIT = 50
    }
}
