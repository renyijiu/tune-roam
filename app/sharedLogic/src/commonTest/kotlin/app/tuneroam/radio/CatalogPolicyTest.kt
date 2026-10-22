package app.tuneroam.radio

import app.tuneroam.radio.data.remote.BundledCatalog
import app.tuneroam.radio.domain.catalog.ReachScore
import app.tuneroam.radio.domain.catalog.WorldDial
import app.tuneroam.radio.domain.catalog.applyChinaFilter
import app.tuneroam.radio.domain.catalog.chooseNextStation
import app.tuneroam.radio.domain.catalog.mirrorHosts
import app.tuneroam.radio.domain.model.Station
import app.tuneroam.radio.domain.playback.SleepTimer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogPolicyTest {
    @Test
    fun mirrorListPrefersTheRememberedHost() {
        val hosts = mirrorHosts(
            remembered = "de2.api.radio-browser.info",
            discovered = listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info"),
            known = listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info"),
        )
        assertEquals(listOf("de2.api.radio-browser.info", "de1.api.radio-browser.info"), hosts)
    }

    @Test
    fun chinaFilterKeepsStationsThisPhoneReached() {
        val reached = station("a", "Reached")
        val other = station("b", "Other")
        val scores = mapOf("a" to ReachScore(successes = 2, failures = 0, lastOk = true))
        assertEquals(listOf(reached), applyChinaFilter(listOf(other, reached), scores, enabled = true))
        assertEquals(listOf(other, reached), applyChinaFilter(listOf(other, reached), scores, enabled = false))
    }

    @Test
    fun nextStationSkipsFailuresAndPrefersALocalSuccess() {
        val failed = station("gone", "Gone", country = "China", language = "chinese")
        val known = station("known", "Known", country = "France")
        val guess = station("guess", "Guess", country = "China", language = "chinese")
        val scores = mapOf(
            "known" to ReachScore(successes = 1, lastOk = true),
            "guess" to ReachScore(failures = 3, lastOk = false),
        )
        val next = chooseNextStation(failed, listOf(guess, known, failed), excluded = setOf("gone"), scores)
        assertEquals("known", next?.stationUuid)
        assertNull(chooseNextStation(failed, listOf(guess), excluded = setOf("guess"), scores))
    }

    @Test
    fun bundledSnapshotIncludesChineseStationsFromEachRegion() {
        val stations = BundledCatalog.all()
        assertTrue(stations.size >= 100)
        assertTrue(stations.any { it.countryCode == "CN" && it.language.contains("chinese", true) })
        assertTrue(stations.any { it.countryCode == "HK" })
        assertTrue(stations.any { it.countryCode == "TW" })
        assertTrue(stations.any { it.countryCode == "SG" })
        assertTrue(stations.all { it.stationUuid.isNotBlank() && (it.url.isNotBlank() || it.urlResolved.isNotBlank()) })
        val sample = stations.first { it.countryCode == "CN" }
        assertTrue(BundledCatalog.similar(sample).none { it.stationUuid == sample.stationUuid })
    }

    @Test
    fun dialPutsChinaBetweenSingaporeAndHongKong() {
        val codes = WorldDial.stops.map { it.isoCode }
        assertTrue(codes.indexOf("SG") < codes.indexOf("CN"))
        assertTrue(codes.indexOf("CN") < codes.indexOf("HK"))
        assertTrue(codes.indexOf("HK") < codes.indexOf("TW"))
        assertEquals(WorldDial.stops, WorldDial.stops.sortedBy { it.longitude })
        val china = WorldDial.homeIndex
        assertEquals(china, WorldDial.indexOfCode("cn"))
        assertNull(WorldDial.indexOfCode("PE"))
        assertEquals(china, WorldDial.dialIndex(WorldDial.rotationFor(china)))
        assertEquals(0, WorldDial.dialIndex(0f))
    }

    @Test
    fun sleepTimerFiresOnce() {
        var now = 1_000L
        val timer = SleepTimer(clock = { now })
        assertFalse(timer.consumeIfDue())
        timer.arm(15)
        assertEquals(1_000L + 15 * 60_000L, timer.deadlineMs.value)
        assertFalse(timer.consumeIfDue())
        now = timer.deadlineMs.value!!
        assertTrue(timer.consumeIfDue())
        assertFalse(timer.consumeIfDue())
        timer.arm(30)
        timer.cancel()
        assertNull(timer.deadlineMs.value)
    }

    private fun station(
        id: String,
        name: String,
        country: String = "",
        language: String = "",
    ) = Station(
        stationUuid = id,
        name = name,
        url = "http://example.test/$id",
        urlResolved = "http://example.test/$id",
        country = country,
        language = language,
    )
}
