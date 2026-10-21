package app.tuneroam.radio

import app.tuneroam.radio.data.remote.RadioPayloads
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioPayloadParsingTest {
    @Test
    fun parsesStationPayloadFromRadioBrowser() {
        val stations = RadioPayloads.stations(FRANCE_INFO)
        assertEquals(1, stations.size)
        val station = stations.single()
        assertEquals("1cfb151d-a341-11e9-a787-52543be04c81", station.stationUuid)
        assertEquals("France Info", station.name)
        assertEquals("http://direct.franceinfo.fr/live/franceinfo-midfi.mp3", station.url)
        assertEquals(station.url, station.urlResolved)
        assertEquals("France", station.country)
        assertEquals("FR", station.countryCode)
        assertEquals("MP3", station.codec)
        assertEquals(128, station.bitrate)
        assertTrue(station.lastCheckOk)
        assertFalse(station.hls)
        assertEquals(1633, station.clickCount)
        assertEquals("https://www.francetvinfo.fr/", station.homepage)
    }

    @Test
    fun nullsAndBrokenChecksUseDefaults() {
        val stations = RadioPayloads.stations(BROKEN)
        val station = stations.single()
        assertEquals("quiet", station.name)
        assertFalse(station.lastCheckOk)
        assertEquals(0, station.bitrate)
        assertEquals("", station.favicon)
        assertTrue(station.hls)
    }

    @Test
    fun dropsStationsWithoutAnId() {
        val stations = RadioPayloads.stations("""[{"name":"no id","url":"http://x"}]""")
        assertTrue(stations.isEmpty())
    }

    @Test
    fun parsesCountriesAndSortsByCount() {
        val countries = RadioPayloads.countries(COUNTRIES)
        assertEquals(listOf("Australia", "The United Arab Emirates", "Andorra"), countries.map { it.name })
        assertEquals("AU", countries.first().isoCode)
        assertEquals(2210, countries.first().stationCount)
    }

    @Test
    fun serverListCollapsesDuplicateNames() {
        val names = RadioPayloads.serverNames(SERVERS)
        assertEquals(listOf("de1.api.radio-browser.info", "nl1.api.radio-browser.info"), names)
    }

    private companion object {
        val FRANCE_INFO = """
            [{
              "changeuuid":"09e8e98c-6a6d-4032-a3cc-96ed0352a505",
              "stationuuid":"1cfb151d-a341-11e9-a787-52543be04c81",
              "serveruuid":null,
              "name":"France Info",
              "url":"http://direct.franceinfo.fr/live/franceinfo-midfi.mp3",
              "url_resolved":"http://direct.franceinfo.fr/live/franceinfo-midfi.mp3",
              "homepage":"https://www.francetvinfo.fr/",
              "favicon":"https://www.francetvinfo.fr/assets/common/images/pwa/ios/120-5487caf3.png",
              "tags":"",
              "country":"France",
              "countrycode":"FR",
              "iso_3166_2":"",
              "state":"",
              "language":"",
              "languagecodes":"",
              "votes":94284,
              "codec":"MP3",
              "bitrate":128,
              "hls":0,
              "lastcheckok":1,
              "clickcount":1633,
              "clicktrend":1633,
              "ssl_error":0,
              "geo_lat":null,
              "geo_long":null,
              "geo_distance":null,
              "has_extended_info":false
            }]
        """.trimIndent()

        val BROKEN = """
            [{
              "stationuuid":"abc",
              "name":"quiet",
              "url":"http://example/a",
              "url_resolved":"",
              "favicon":null,
              "bitrate":null,
              "hls":1,
              "lastcheckok":0,
              "geo_lat":null
            }]
        """.trimIndent()

        val COUNTRIES = """
            [
              {"name":"Andorra","iso_3166_1":"AD","stationcount":13},
              {"name":"The United Arab Emirates","iso_3166_1":"AE","stationcount":827},
              {"name":"Australia","iso_3166_1":"AU","stationcount":2210}
            ]
        """.trimIndent()

        val SERVERS = """
            [
              {"ip":"91.98.4.78","name":"de1.api.radio-browser.info"},
              {"ip":"2a01:4f8:1c1d:699::1","name":"de1.api.radio-browser.info"},
              {"ip":"1.2.3.4","name":"nl1.api.radio-browser.info"}
            ]
        """.trimIndent()
    }
}
