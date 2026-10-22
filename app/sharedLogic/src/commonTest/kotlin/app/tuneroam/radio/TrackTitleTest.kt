package app.tuneroam.radio

import app.tuneroam.radio.domain.model.MusicService
import app.tuneroam.radio.domain.model.TrackTitle
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackTitleTest {
    @Test
    fun splitsArtistAndTitleOnTheUsualSeparator() {
        assertEquals("周杰伦" to "晴天", TrackTitle.split("周杰伦 - 晴天"))
        assertEquals(null to "Only a title", TrackTitle.split("  Only   a title "))
        // A leading or trailing dash is not a separator.
        assertEquals(null to "- Intro", TrackTitle.split("- Intro"))
        assertEquals("Daft Punk" to "Harder - Better", TrackTitle.split("Daft Punk - Harder - Better"))
    }

    @Test
    fun keyIgnoresCaseAndSpacing() {
        assertEquals(TrackTitle.key("Daft  Punk - One More Time"), TrackTitle.key(" daft punk - one more time"))
    }

    @Test
    fun searchUrlsEncodeTheQuery() {
        val title = "周杰伦 - 晴天"
        val query = "%E5%91%A8%E6%9D%B0%E4%BC%A6%20%E6%99%B4%E5%A4%A9"
        assertEquals("https://music.163.com/#/search/m/?s=$query&type=1", MusicService.NETEASE.searchUrl(title))
        assertEquals("https://y.qq.com/n/ryqq/search?w=$query&t=song", MusicService.QQ_MUSIC.searchUrl(title))
        assertEquals("https://open.spotify.com/search/$query", MusicService.SPOTIFY.searchUrl(title))
        assertEquals(
            "https://open.spotify.com/search/AC%2FDC%20Back%20in%20Black%20%26%20More",
            MusicService.SPOTIFY.searchUrl("AC/DC - Back in Black & More"),
        )
    }
}
