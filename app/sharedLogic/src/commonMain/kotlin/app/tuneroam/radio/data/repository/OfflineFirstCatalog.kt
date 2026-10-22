package app.tuneroam.radio.data.repository

import app.tuneroam.radio.core.runCancellable
import app.tuneroam.radio.data.local.CatalogCache
import app.tuneroam.radio.data.remote.FallbackCatalog
import app.tuneroam.radio.data.remote.RadioBrowserClient
import app.tuneroam.radio.data.remote.RadioHttpException
import app.tuneroam.radio.domain.model.CatalogOrigin
import app.tuneroam.radio.domain.model.CatalogQuery
import app.tuneroam.radio.domain.model.Country
import app.tuneroam.radio.domain.model.FailureReason
import app.tuneroam.radio.domain.model.LoadResult
import app.tuneroam.radio.domain.model.RoamFilter
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.model.similarQuery
import app.tuneroam.radio.domain.repository.RadioCatalog
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Cache (or the offline snapshot) first, then the live catalog. The offline snapshot is
 * the downloaded one when there is one, else the one packed into the APK.
 *
 * Popular, countries, and per-country lists are kept in SQL because they are a small,
 * bounded set. Free-text searches are not: every query would leave its own bucket
 * behind, and an offline search still has the bundled snapshot to fall back on.
 */
class OfflineFirstCatalog(
    private val client: RadioBrowserClient,
    private val cache: CatalogCache,
    private val fallback: FallbackCatalog,
) : RadioCatalog {
    override fun popular(hideBroken: Boolean, limit: Int): Flow<LoadResult<List<Station>>> =
        cached(
            read = { cache.readStations("popular:$hideBroken:$limit") },
            write = { cache.writeStations("popular:$hideBroken:$limit", it) },
            bundled = { fallback.popular(limit, hideBroken) },
            fetch = { client.topClicked(limit, hideBroken) },
        )

    override fun search(query: CatalogQuery): Flow<LoadResult<List<Station>>> =
        cached(
            read = { emptyList() },
            write = {},
            bundled = { fallback.search(query) },
            fetch = { client.search(query) },
        )

    override fun countries(): Flow<LoadResult<List<Country>>> =
        cached(
            read = { cache.readCountries() },
            write = { cache.writeCountries(it) },
            bundled = { fallback.countries() },
            fetch = { client.countries() },
        )

    override fun stationsInCountry(country: String, hideBroken: Boolean): Flow<LoadResult<List<Station>>> {
        val bucket = "country:$hideBroken:${country.trim().lowercase()}"
        return cached(
            read = { cache.readStations(bucket) },
            write = { cache.writeStations(bucket, it) },
            bundled = { fallback.inCountry(country, hideBroken) },
            fetch = {
                client.search(
                    CatalogQuery(country = country.trim(), hideBroken = hideBroken, limit = 100),
                )
            },
        )
    }

    override suspend fun similar(station: Station): LoadResult<List<Station>> {
        val live = runCancellable {
            client.search(similarQuery(station)).filter { it.stationUuid != station.stationUuid }.take(20)
        }
        live.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return LoadResult.Fresh(it) }
        val bundled = withContext(Dispatchers.IO) { fallback.similar(station) }
        val error = live.exceptionOrNull()
        return if (bundled.isEmpty() && error != null) {
            LoadResult.Failed(error.toReason())
        } else {
            LoadResult.Cached(bundled, stale = true, origin = CatalogOrigin.BUNDLED)
        }
    }

    override suspend fun random(limit: Int, filter: RoamFilter): LoadResult<List<Station>> {
        // A filter several choices wide is applied on the device, so ask for more.
        val fetch = if (filter.isActive) (limit * 3).coerceAtMost(MAX_RANDOM_FETCH) else limit
        val live = runCancellable { client.random(fetch, filter).take(limit) }
        live.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return LoadResult.Fresh(it) }
        val bundled = withContext(Dispatchers.IO) { fallback.random(fallback.all().size) }.filter(filter::matches).take(limit)
        return LoadResult.Cached(bundled, stale = true, origin = CatalogOrigin.BUNDLED)
    }

    override suspend fun byUuid(stationUuid: String): Station? =
        runCancellable { client.byUuid(stationUuid) }.getOrNull()
            ?: withContext(Dispatchers.IO) {
                fallback.all().firstOrNull { it.stationUuid.equals(stationUuid, ignoreCase = true) }
            }

    override suspend fun registerClick(stationUuid: String) {
        client.registerClick(stationUuid)
    }
}

private const val MAX_RANDOM_FETCH = 100

private inline fun <T> cached(
    crossinline read: () -> List<T>,
    crossinline write: (List<T>) -> Unit,
    crossinline bundled: () -> List<T>,
    crossinline fetch: suspend () -> List<T>,
): Flow<LoadResult<List<T>>> = flow {
    val cached = read()
    val seed = cached.ifEmpty { bundled() }
    val origin = if (cached.isNotEmpty()) CatalogOrigin.CACHE else CatalogOrigin.BUNDLED
    if (seed.isNotEmpty()) emit(LoadResult.Cached(seed, stale = false, origin = origin))
    runCancellable { fetch() }
        .onSuccess { fresh ->
            write(fresh)
            emit(LoadResult.Fresh(fresh))
        }
        .onFailure { error ->
            if (seed.isEmpty()) {
                emit(LoadResult.Failed(error.toReason()))
            } else {
                emit(LoadResult.Cached(seed, stale = true, origin = origin))
            }
        }
}.flowOn(Dispatchers.IO)

internal fun Throwable.toReason(): FailureReason = when (this) {
    is HttpRequestTimeoutException, is ConnectTimeoutException -> FailureReason.TIMEOUT
    is RadioHttpException -> if (code in 500..599) FailureReason.SERVER else FailureReason.NETWORK
    else -> FailureReason.NETWORK
}
