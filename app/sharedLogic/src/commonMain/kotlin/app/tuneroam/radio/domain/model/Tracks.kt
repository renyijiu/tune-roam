package app.tuneroam.radio.domain.model

import io.ktor.http.encodeURLParameter

/** A song title saved from a station's ICY metadata. */
data class SavedTrack(
    val id: Long,
    val title: String,
    val stationUuid: String,
    val stationName: String,
    val savedAt: Long,
) {
    val artist: String? get() = TrackTitle.split(title).first
    val song: String get() = TrackTitle.split(title).second
}

object TrackTitle {
    /** Whitespace collapsed, as shown and searched. */
    fun clean(raw: String): String = raw.trim().replace(WHITESPACE, " ")

    /** Identity for "already saved": case and spacing do not make a new song. */
    fun key(raw: String): String = clean(raw).lowercase()

    /**
     * Most stations send "Artist - Title". Returns (artist, title); the artist is null
     * when there is no separator, and the whole text is the title.
     */
    fun split(raw: String): Pair<String?, String> {
        val cleaned = clean(raw)
        val at = cleaned.indexOf(" - ")
        if (at <= 0 || at + 3 >= cleaned.length) return null to cleaned
        return cleaned.substring(0, at).trim() to cleaned.substring(at + 3).trim()
    }

    private val WHITESPACE = Regex("\\s+")
}

/**
 * Where a saved title can be looked up. Web search addresses: when the service's app
 * is installed and claims its domain, Android opens the app; otherwise the browser.
 */
enum class MusicService {
    NETEASE,
    QQ_MUSIC,
    SPOTIFY,
    ;

    fun searchUrl(title: String): String {
        val query = TrackTitle.split(title).let { (artist, song) -> listOfNotNull(artist, song).joinToString(" ") }
        val encoded = query.encodeURLParameter()
        return when (this) {
            NETEASE -> "https://music.163.com/#/search/m/?s=$encoded&type=1"
            QQ_MUSIC -> "https://y.qq.com/n/ryqq/search?w=$encoded&t=song"
            SPOTIFY -> "https://open.spotify.com/search/$encoded"
        }
    }
}
