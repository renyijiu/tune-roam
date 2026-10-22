package app.tuneroam.radio.domain.playback

import app.tuneroam.radio.domain.catalog.chooseNextStation
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.valueOrEmpty
import app.tuneroam.radio.domain.repository.RadioCatalog
import app.tuneroam.radio.domain.repository.ReachabilityRepository
import app.tuneroam.radio.domain.repository.StationConnectivity

/**
 * Finds a station to play after [failed] gives up. Used by the playback shell for the
 * automatic hop and by the player screen for "next similar", so both follow one rule:
 * rank the similar pool (stations this phone reached first), then take the first one
 * that answers a short probe.
 */
class SimilarStations(
    private val catalog: RadioCatalog,
    private val reachability: ReachabilityRepository,
    private val connectivity: StationConnectivity,
) {
    /**
     * Null when the pool is empty or none of the first [maxProbes] candidates answer.
     * [excluded] holds stations already tried in this run; the failed one is noted as
     * unreachable so it ranks last next time.
     */
    suspend fun findReachable(
        failed: Station,
        excluded: Set<String> = emptySet(),
        maxProbes: Int = MAX_PROBES,
    ): Station? {
        reachability.note(failed.stationUuid, ok = false)
        val pool = catalog.similar(failed).valueOrEmpty()
        val skipped = excluded.toMutableSet()
        repeat(maxProbes) {
            val next = chooseNextStation(failed, pool, skipped, reachability.scores()) ?: return null
            skipped += next.stationUuid
            val reachable = connectivity.probe(next)
            reachability.note(next.stationUuid, reachable)
            if (reachable) return next
        }
        return null
    }

    private companion object {
        const val MAX_PROBES = 4
    }
}
