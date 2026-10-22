package app.tuneroam.radio.playback

/**
 * Browsable media ids served by [RadioLibraryService].
 * Root children are the tabs a car browser would show.
 */
object LibraryTree {
    const val ROOT = "tuneroam:root"
    const val FAVORITES = "tuneroam:favorites"
    const val POPULAR = "tuneroam:popular"
    const val COUNTRIES = "tuneroam:countries"
    private const val COUNTRY_PREFIX = "tuneroam:country:"
    private const val STATION_PREFIX = "tuneroam:station:"

    fun country(name: String): String = COUNTRY_PREFIX + name

    fun station(uuid: String): String = STATION_PREFIX + uuid

    fun countryName(mediaId: String): String? =
        mediaId.takeIf { it.startsWith(COUNTRY_PREFIX) }?.removePrefix(COUNTRY_PREFIX)

    fun stationUuid(mediaId: String): String? =
        mediaId.takeIf { it.startsWith(STATION_PREFIX) }?.removePrefix(STATION_PREFIX)?.takeIf { it.isNotBlank() }
}
