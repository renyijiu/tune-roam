package app.tuneroam.radio

import app.tuneroam.radio.domain.catalog.ReachScore
import app.tuneroam.radio.domain.playback.RoamDeck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RoamDeckTest {
    private val batch = listOf("a", "b", "c", "d", "e").map { testStation(it) }

    @Test
    fun coldNextSkipsStationsThatDoNotAnswer() = runTest {
        val connectivity = FakeConnectivity(answering = setOf("b", "c", "d", "e"))
        val deck = deck(FakeCatalog(random = batch), connectivity)
        assertEquals("b", deck.next()?.stationUuid)
        assertEquals(listOf("a", "b"), connectivity.probed.take(2))
    }

    @Test
    fun warmUpProbesAheadSoNextNeedsNoProbe() = runTest {
        val connectivity = FakeConnectivity(answering = batch.map { it.stationUuid }.toSet())
        val catalog = FakeCatalog(random = batch)
        val deck = deck(catalog, connectivity)
        deck.warmUp()
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), connectivity.probed)

        assertEquals("a", deck.next()?.stationUuid)
        // Taking one refills the lookahead in the background, from the same batch.
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c", "d"), connectivity.probed)
        assertEquals(1, catalog.randomCalls)
    }

    @Test
    fun previousWalksBackAndNextReturnsForward() = runTest {
        val deck = deck(FakeCatalog(random = batch), FakeConnectivity(batch.map { it.stationUuid }.toSet()))
        assertFalse(deck.canGoBack.value)
        deck.played(testStation("home"))
        val first = deck.next()!!
        assertTrue(deck.canGoBack.value)

        assertEquals("home", deck.previous()?.stationUuid)
        assertFalse(deck.canGoBack.value)
        assertNull(deck.previous())
        assertEquals(first.stationUuid, deck.next()?.stationUuid)
    }

    @Test
    fun automaticHopIsNotKeptInHistory() = runTest {
        val deck = deck(FakeCatalog(), FakeConnectivity(emptySet()))
        deck.played(testStation("chosen"))
        deck.played(testStation("dead"))
        deck.played(testStation("similar"), replacing = true)
        assertEquals("chosen", deck.previous()?.stationUuid)
    }

    @Test
    fun stationsThatFailedHereComeLast() = runTest {
        val reach = FakeReachability(mapOf("a" to ReachScore(failures = 2, lastOk = false)))
        val deck = RoamDeck(
            FakeCatalog(random = batch.take(2)),
            reach,
            FakeConnectivity(setOf("a", "b")),
            this,
        )
        assertEquals("b", deck.next()?.stationUuid)
    }

    @Test
    fun offlineWarmUpGivesUpInsteadOfSpinning() = runTest {
        val connectivity = FakeConnectivity(emptySet())
        val deck = deck(FakeCatalog(random = batch), connectivity)
        deck.warmUp()
        advanceUntilIdle()
        assertTrue(connectivity.probed.size <= 12)
        // Still hands back something to try; the playback fallback takes it from there.
        assertTrue(deck.next() != null)
    }

    private fun TestScope.deck(catalog: FakeCatalog, connectivity: FakeConnectivity) =
        RoamDeck(catalog, FakeReachability(), connectivity, this)
}
