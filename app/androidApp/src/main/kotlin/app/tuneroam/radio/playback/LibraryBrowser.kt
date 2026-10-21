package app.tuneroam.radio.playback

import androidx.media3.common.MediaItem
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.playback.StreamFallback
import app.tuneroam.radio.domain.repository.FavoritesRepository
import app.tuneroam.radio.domain.repository.RadioCatalog
import kotlinx.coroutines.flow.Flow

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

private suspend fun <T> Flow<LoadResult<List<T>>>.awaitValue(): List<T> {
    var latest: List<T> = emptyList()
    collect { result ->
        when (result) {
            is LoadResult.Fresh -> latest = result.value
            is LoadResult.Cached -> latest = result.value
            is LoadResult.Failed -> Unit
        }
    }
    return latest
}
