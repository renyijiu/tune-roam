package app.tuneroam.radio.data.repository

import app.tuneroam.radio.data.local.CatalogCache
import app.tuneroam.radio.data.remote.BundledCatalog
import app.tuneroam.radio.data.remote.RadioBrowserClient
import app.tuneroam.radio.data.remote.RadioHttpException
import app.tuneroam.radio.domain.model.CatalogOrigin
import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.FailureReason
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.similarQuery
import app.tuneroam.radio.domain.repository.RadioCatalog
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class OfflineFirstCatalog(
    private val client: RadioBrowserClient,
    private val cache: CatalogCache,
) : RadioCatalog {
    override fun popular(hideBroken: Boolean, limit: Int): Flow<LoadResult<List<Station>>> =
        cached(
            bucket = "popular:$hideBroken:$limit",
            read = { cache.readStations(it) },
            write = { bucket, value -> cache.writeStations(bucket, value) },
            bundled = { BundledCatalog.popular(limit, hideBroken) },
            fetch = { client.topClicked(limit, hideBroken) },
        )

    override fun search(query: CatalogQuery): Flow<LoadResult<List<Station>>> =
        cached(
            bucket = "search:${query.field}:${query.hideBroken}:${query.limit}:${query.text.trim().lowercase()}:${query.country.orEmpty()}",
            read = { cache.readStations(it) },
            write = { bucket, value -> cache.writeStations(bucket, value) },
            bundled = { BundledCatalog.search(query) },
            fetch = { client.search(query) },
        )

    override fun countries(): Flow<LoadResult<List<Country>>> =
        cached(
            bucket = "countries",
            read = { _ -> cache.readCountries() },
            write = { _, value -> cache.writeCountries(value) },
            bundled = { BundledCatalog.countries() },
            fetch = { client.countries() },
        )

    override fun stationsInCountry(country: String, hideBroken: Boolean): Flow<LoadResult<List<Station>>> =
        cached(
            bucket = "country:$hideBroken:${country.trim().lowercase()}",
            read = { cache.readStations(it) },
            write = { bucket, value -> cache.writeStations(bucket, value) },
            bundled = { BundledCatalog.inCountry(country, hideBroken) },
            fetch = {
                client.search(
                    CatalogQuery(country = country.trim(), hideBroken = hideBroken, limit = 100),
                )
            },
        )

    override suspend fun similar(station: Station): LoadResult<List<Station>> = try {
        val matches = client.search(similarQuery(station))
            .filter { it.stationUuid != station.stationUuid }
            .take(20)
        if (matches.isNotEmpty()) {
            LoadResult.Fresh(matches)
        } else {
            bundledSimilar(station)
        }
    } catch (error: Throwable) {
        val bundled = BundledCatalog.similar(station)
        if (bundled.isEmpty()) LoadResult.Failed(error.toReason()) else bundledSimilar(station)
    }

    private fun bundledSimilar(station: Station): LoadResult<List<Station>> =
        LoadResult.Cached(BundledCatalog.similar(station), stale = true, origin = CatalogOrigin.BUNDLED)

    override suspend fun registerClick(stationUuid: String) {
        client.registerClick(stationUuid)
    }

    override fun bundled(): List<Station> = BundledCatalog.all()
}

private inline fun <T> cached(
    bucket: String,
    crossinline read: (String) -> List<T>,
    crossinline write: (String, List<T>) -> Unit,
    crossinline bundled: () -> List<T>,
    crossinline fetch: suspend () -> List<T>,
): Flow<LoadResult<List<T>>> = flow {
    val cached = read(bucket)
    val seed = cached.ifEmpty { bundled() }
    val origin = if (cached.isNotEmpty()) CatalogOrigin.CACHE else CatalogOrigin.BUNDLED
    if (seed.isNotEmpty()) emit(LoadResult.Cached(seed, stale = false, origin = origin))
    try {
        val fresh = fetch()
        write(bucket, fresh)
        emit(LoadResult.Fresh(fresh))
    } catch (error: Throwable) {
        if (seed.isEmpty()) emit(LoadResult.Failed(error.toReason()))
        else emit(LoadResult.Cached(seed, stale = true, origin = origin))
    }
}.flowOn(Dispatchers.IO)

private fun Throwable.toReason(): FailureReason = when (this) {
    is HttpRequestTimeoutException, is ConnectTimeoutException -> FailureReason.TIMEOUT
    is RadioHttpException -> if (code in 500..599) FailureReason.SERVER else FailureReason.NETWORK
    else -> FailureReason.NETWORK
}
