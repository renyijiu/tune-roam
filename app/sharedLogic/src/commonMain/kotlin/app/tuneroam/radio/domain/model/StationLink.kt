package app.tuneroam.radio.domain.model

/**
 * Share links for a station: `tuneroam://station/{stationuuid}`.
 *
 * The uuid is Radio Browser's, so a link keeps working after the station's stream
 * address changes. A custom scheme opens the installed app but is not clickable
 * everywhere; an https App Link needs a domain the app owns, which [build] can switch
 * to without changing [parse] for old links.
 */
object StationLink {
    const val SCHEME = "tuneroam"
    const val HOST = "station"

    fun build(station: Station): String = "$SCHEME://$HOST/${station.stationUuid}"

    /** Text for the system share sheet: name, where it is from, and the link. */
    fun shareText(station: Station): String = buildString {
        append(station.name)
        if (station.country.isNotBlank()) append(" · ").append(station.country)
        append('\n').append(build(station))
    }

    /** The station uuid in [link], or null when it is not one of ours. */
    fun parse(link: String): String? {
        val prefix = "$SCHEME://$HOST/"
        val trimmed = link.trim()
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return null
        val uuid = trimmed.substring(prefix.length).substringBefore('?').substringBefore('#').trimEnd('/')
        return uuid.lowercase().takeIf { UUID.matches(it) }
    }

    private val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
}
