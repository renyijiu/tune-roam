package app.tuneroam.radio.domain.model

import kotlinx.serialization.Serializable

/** Everything the settings screen changes. Stored as one JSON value, so new fields need only a default. */
@Serializable
data class AppSettings(
    val filter: RoamFilter = RoamFilter(),
    /** Even out loudness between stations (see the Android `LoudnessLeveler`). */
    val levelVolume: Boolean = true,
)

/**
 * What "next station" may land on. Empty sets mean "any". [languages] and [tags]
 * hold option ids from [LanguageOption] and [GenreOption].
 */
@Serializable
data class RoamFilter(
    val minBitrate: Int = 0,
    val languages: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
) {
    val isActive: Boolean
        get() = minBitrate > 0 || languages.isNotEmpty() || tags.isNotEmpty() || excludedTags.isNotEmpty()

    fun matches(station: Station): Boolean {
        // Unknown bitrate (0) is common for HLS and many good streams; only a known low one is out.
        if (minBitrate > 0 && station.bitrate in 1 until minBitrate) return false
        if (languages.isNotEmpty() && languages.none { LanguageOption.byId(it)?.matches(station) == true }) return false
        if (tags.isNotEmpty() && tags.none { GenreOption.byId(it)?.matches(station) == true }) return false
        if (excludedTags.any { GenreOption.byId(it)?.matches(station) == true }) return false
        return true
    }

    /**
     * Narrowing the live random query itself, so a strict filter still gets a full batch.
     * Only single choices can be expressed in one Radio Browser query; the rest is
     * filtered on the device with [matches].
     */
    fun queryParameters(): Map<String, String> = buildMap {
        if (minBitrate > 0) put("bitrateMin", minBitrate.toString())
        languages.singleOrNull()?.let(LanguageOption::byId)?.let { put("language", it.aliases.first()) }
        tags.singleOrNull()?.let(GenreOption::byId)?.let { put("tag", it.aliases.first()) }
    }

    companion object {
        val BITRATE_CHOICES = listOf(0, 64, 128, 192, 320)
    }
}

/** A language as Radio Browser spells it; the first alias goes into queries. */
enum class LanguageOption(val aliases: List<String>) {
    CHINESE(listOf("chinese", "mandarin", "cantonese", "中文", "普通话", "粤语")),
    ENGLISH(listOf("english")),
    JAPANESE(listOf("japanese")),
    KOREAN(listOf("korean")),
    FRENCH(listOf("french")),
    SPANISH(listOf("spanish")),
    GERMAN(listOf("german")),
    PORTUGUESE(listOf("portuguese")),
    ITALIAN(listOf("italian")),
    RUSSIAN(listOf("russian")),
    ;

    val id: String get() = name.lowercase()

    fun matches(station: Station): Boolean {
        val spoken = station.language.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        return spoken.any { language -> aliases.any { alias -> language.contains(alias) } }
    }

    companion object {
        fun byId(id: String): LanguageOption? = entries.firstOrNull { it.id == id }
    }
}

/** A genre as Radio Browser tags spell it. A tag containing any alias counts ("classic rock" is rock). */
enum class GenreOption(val aliases: List<String>) {
    POP(listOf("pop")),
    ROCK(listOf("rock")),
    JAZZ(listOf("jazz")),
    CLASSICAL(listOf("classical", "classic music", "古典")),
    ELECTRONIC(listOf("electronic", "dance", "house", "techno", "edm")),
    HIPHOP(listOf("hip hop", "hiphop", "hip-hop", "rap")),
    CHILL(listOf("chill", "lounge", "ambient", "relax")),
    OLDIES(listOf("oldies", "60s", "70s", "80s", "90s")),
    COUNTRY(listOf("country")),
    FOLK(listOf("folk")),
    NEWS(listOf("news", "新闻", "資訊", "资讯")),
    TALK(listOf("talk", "spoken")),
    SPORTS(listOf("sport")),
    RELIGIOUS(listOf("religio", "christian", "gospel", "catholic", "islam", "bible", "worship")),
    ;

    val id: String get() = name.lowercase()

    fun matches(station: Station): Boolean =
        station.tagList.any { tag -> aliases.any { alias -> tag.lowercase().contains(alias) } }

    companion object {
        fun byId(id: String): GenreOption? = entries.firstOrNull { it.id == id }
    }
}
