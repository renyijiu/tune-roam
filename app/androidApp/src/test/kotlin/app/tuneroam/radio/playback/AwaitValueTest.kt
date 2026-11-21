package app.tuneroam.radio.playback

import app.tuneroam.radio.domain.model.CatalogOrigin
import app.tuneroam.radio.domain.model.FailureReason
import app.tuneroam.radio.domain.model.LoadResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class AwaitValueTest {
    @Test
    fun liveListThatArrivesInTimeReplacesTheCache() = runTest {
        val result = flow {
            emit(cached("cache"))
            delay(500)
            emit(LoadResult.Fresh(listOf("live")))
        }.awaitValue(liveGraceMs = 2_000, totalMs = 8_000)
        assertEquals(listOf("live"), result)
    }

    @Test
    fun slowLiveRequestFallsBackToTheCacheAfterTheGrace() = runTest {
        val result = flow {
            emit(cached("cache"))
            delay(60_000)
            emit(LoadResult.Fresh(listOf("live")))
        }.awaitValue(liveGraceMs = 2_000, totalMs = 8_000)
        assertEquals(listOf("cache"), result)
        assertEquals(2_000, currentTime)
    }

    @Test
    fun nothingCachedWaitsForLiveButNotForever() = runTest {
        val result = flow<LoadResult<List<String>>> {
            delay(60_000)
            emit(LoadResult.Fresh(listOf("live")))
        }.awaitValue(liveGraceMs = 2_000, totalMs = 8_000)
        assertTrue(result.isEmpty())
        assertEquals(8_000, currentTime)
    }

    @Test
    fun failureAfterTheCacheKeepsTheCache() = runTest {
        val result = flow {
            emit(cached("cache"))
            emit(LoadResult.Cached(listOf("cache"), stale = true))
        }.awaitValue()
        assertEquals(listOf("cache"), result)
    }

    @Test
    fun failureWithNothingCachedIsEmpty() = runTest {
        val result = flow<LoadResult<List<String>>> { emit(LoadResult.Failed(FailureReason.NETWORK)) }.awaitValue()
        assertTrue(result.isEmpty())
    }

    private fun cached(value: String): LoadResult<List<String>> =
        LoadResult.Cached(listOf(value), stale = false, origin = CatalogOrigin.CACHE)
}
