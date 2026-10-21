package app.tuneroam.radio.domain.repository

import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.model.Station
import kotlinx.coroutines.flow.Flow

interface RadioCatalog {
    fun popular(hideBroken: Boolean, limit: Int = 40): Flow<LoadResult<List<Station>>>

    fun search(query: CatalogQuery): Flow<LoadResult<List<Station>>>

    fun countries(): Flow<LoadResult<List<Country>>>

    fun stationsInCountry(country: String, hideBroken: Boolean): Flow<LoadResult<List<Station>>>

    suspend fun similar(station: Station): LoadResult<List<Station>>

    suspend fun registerClick(stationUuid: String)
}

interface FavoritesRepository {
    fun observe(): Flow<List<Station>>

    suspend fun list(): List<Station>

    suspend fun isFavorite(stationUuid: String): Boolean

    suspend fun toggle(station: Station): Boolean

    suspend fun remove(stationUuid: String)
}
