package app.tuneroam.radio.playback

import androidx.media3.common.MediaItem
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.playback.StreamFallback
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull

internal class LibraryBrowser(
    private val catalog: RadioCatalog,
    private val favorites: FavoritesRepository,
    private val favoritesTitle: String,
    private val popularTitle: String,
    private val countriesTitle: String,
) {
    suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        LibraryTree.ROOT -> listOf(
            folderItem(LibraryTree.FAVORITES, favoritesTitle),
            folderItem(LibraryTree.POPULAR, popularTitle),
            folderItem(LibraryTree.COUNTRIES, countriesTitle),
        )
        LibraryTree.FAVORITES -> favorites.list().map { station ->
            mediaItemFor(station, StreamFallback.candidates(station.urlResolved, station.url).firstOrNull())
        }
        LibraryTree.POPULAR -> catalog.popular(hideBroken = true).awaitValue().map { station ->
            mediaItemFor(station, StreamFallback.candidates(station.urlResolved, station.url).firstOrNull())
        }
        LibraryTree.COUNTRIES -> catalog.countries().awaitValue().map { country ->
            folderItem(LibraryTree.country(country.name), country.name)
        }
        else -> {
            val country = LibraryTree.countryName(parentId)
            if (country == null) {
                emptyList()
            } else {
                catalog.stationsInCountry(country, hideBroken = true).awaitValue().map { station ->
                    mediaItemFor(station, StreamFallback.candidates(station.urlResolved, station.url).firstOrNull())
                }
            }
        }
    }
}

/**
 * The list a car head unit or the system media controls should see, without waiting
 * out the live catalog. Those browsers give up after a few seconds, and from the
 * mainland the live request often takes its full timeout (several mirrors in a row).
 *
 * A live list ends the wait. A cached list is kept and the live one gets [liveGraceMs]
 * more to replace it. Nothing at all after [totalMs] reads as empty.
 */
internal suspend fun <T> Flow<LoadResult<List<T>>>.awaitValue(
    liveGraceMs: Long = LIVE_GRACE_MS,
    totalMs: Long = TOTAL_WAIT_MS,
): List<T> = coroutineScope {
    val results = this@awaitValue.produceIn(this)
    var latest: List<T> = emptyList()
    withTimeoutOrNull(totalMs) {
        for (result in results) {
            when (result) {
                is LoadResult.Fresh -> {
                    latest = result.value
                    return@withTimeoutOrNull
                }
                is LoadResult.Cached -> {
                    latest = result.value
                    if (result.value.isEmpty()) continue
                    val live = withTimeoutOrNull(liveGraceMs) { results.receiveCatching().getOrNull() }
                    if (live is LoadResult.Fresh) latest = live.value
                    return@withTimeoutOrNull
                }
                is LoadResult.Failed -> Unit
            }
        }
    }
    results.cancel()
    latest
}

private const val LIVE_GRACE_MS = 2_000L
private const val TOTAL_WAIT_MS = 8_000L
