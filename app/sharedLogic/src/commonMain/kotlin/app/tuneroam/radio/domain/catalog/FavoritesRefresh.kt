package app.tuneroam.radio.domain.catalog

import app.tuneroam.radio.core.epochMillis
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Brings saved favorites up to date with the live catalog: a station that moved its
 * stream, changed its logo, or started or stopped passing Radio Browser's check.
 *
 * A favorite is a copy taken when it was saved, and nothing else updated it. At most
 * once per [intervalMs] while the app runs; an unreachable catalog does not count, so
 * the next try comes sooner. Stations the catalog dropped keep their saved copy, which
 * may well still play.
 */
class FavoritesRefresh(
    private val favorites: FavoritesRepository,
    private val catalog: RadioCatalog,
    private val clock: () -> Long = ::epochMillis,
    private val intervalMs: Long = INTERVAL_MS,
) {
    private val lock = Mutex()
    private var refreshedAt: Long? = null

    suspend fun refreshIfDue() {
        if (lock.isLocked) return
        lock.withLock {
            val now = clock()
            refreshedAt?.let { if (now - it < intervalMs) return }
            val saved = favorites.list()
            if (saved.isEmpty()) return
            val latest = catalog.latest(saved.map { it.stationUuid })
            if (latest.isEmpty()) return
            refreshedAt = now
            favorites.update(latest.filter { it.hasStream })
        }
    }

    private companion object {
        const val INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
