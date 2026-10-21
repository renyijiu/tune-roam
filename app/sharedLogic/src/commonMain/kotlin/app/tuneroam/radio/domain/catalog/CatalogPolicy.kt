package app.tuneroam.radio.domain.catalog

import app.tuneroam.radio.domain.model.Station

data class MirrorSample(
    val host: String,
    val latencyMs: Long?,
)

/**
 * Short probes of every mirror, then the smallest latency that actually answered.
 * A null latency is a timeout or a DNS failure and is ignored.
 */
fun fastestMirror(samples: List<MirrorSample>): String? =
    samples
        .mapNotNull { sample -> sample.latencyMs?.let { sample.host to it } }
        .minWithOrNull(compareBy<Pair<String, Long>> { it.second }.thenBy { it.first })
        ?.first

/** Remembered host first, then whatever discovery returned, then the built-in list. */
fun mirrorHosts(remembered: String?, discovered: List<String>, known: List<String>): List<String> =
    buildList {
        remembered?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        addAll(discovered)
        addAll(known)
    }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

data class ReachScore(
    val successes: Int = 0,
    val failures: Int = 0,
    val lastOk: Boolean = false,
)

/**
 * Stations this phone has actually reached, most successful first.
 * An empty result is a normal cold start, not an error.
 */
fun chinaReachable(stations: List<Station>, scores: Map<String, ReachScore>): List<Station> =
    stations
        .filter { scores[it.stationUuid]?.lastOk == true }
        .sortedWith(
            compareByDescending<Station> { scores[it.stationUuid]?.successes ?: 0 }
                .thenByDescending { it.clickCount },
        )

fun applyChinaFilter(
    stations: List<Station>,
    scores: Map<String, ReachScore>,
    enabled: Boolean,
): List<Station> = if (enabled) chinaReachable(stations, scores) else stations

/**
 * Next station after a stream gives up. Skips anything already tried, and prefers
 * a station this phone has reached before over a catalog guess.
 */
fun chooseNextStation(
    failed: Station,
    candidates: List<Station>,
    excluded: Set<String>,
    scores: Map<String, ReachScore>,
): Station? {
    val pool = candidates.filter { station ->
        station.stationUuid != failed.stationUuid &&
            station.stationUuid !in excluded &&
            (station.url.isNotBlank() || station.urlResolved.isNotBlank())
    }
    if (pool.isEmpty()) return null
    return pool.sortedWith(
        compareByDescending<Station> { scores[it.stationUuid]?.lastOk == true }
            .thenByDescending { scores[it.stationUuid]?.successes ?: 0 }
            .thenBy { (scores[it.stationUuid]?.failures ?: 0) > 0 }
            .thenByDescending { it.lastCheckOk }
            .thenByDescending { sharesLanguage(it, failed) }
            .thenByDescending { it.country.equals(failed.country, ignoreCase = true) }
            .thenByDescending { it.clickCount },
    ).first()
}

private fun sharesLanguage(station: Station, other: Station): Boolean {
    val left = station.language.trim()
    val right = other.language.trim()
    return left.isNotEmpty() && left.equals(right, ignoreCase = true)
}
