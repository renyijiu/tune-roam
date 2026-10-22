package app.tuneroam.radio.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val stationUuid: String,
    val name: String,
    val url: String,
    val urlResolved: String,
    val homepage: String = "",
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val countryCode: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Boolean = false,
    val lastCheckOk: Boolean = false,
    val clickCount: Int = 0,
) {
    val tagList: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    val hasStream: Boolean
        get() = url.isNotBlank() || urlResolved.isNotBlank()

    fun technicalLabel(): String = buildList {
        if (bitrate > 0) add("$bitrate kbps")
        if (codec.isNotBlank()) add(codec.uppercase())
        if (hls) add("HLS")
    }.joinToString(" · ")
}

@Serializable
data class Country(
    val name: String,
    val isoCode: String,
    val stationCount: Int,
)

enum class SearchField {
    NAME,
    TAG,
    COUNTRY,
}

data class CatalogQuery(
    val text: String = "",
    val field: SearchField = SearchField.NAME,
    val country: String? = null,
    val hideBroken: Boolean = true,
    val limit: Int = 50,
)

fun CatalogQuery.toQueryMap(): Map<String, String> {
    val map = linkedMapOf(
        "limit" to limit.coerceAtLeast(1).toString(),
        "hidebroken" to hideBroken.toString(),
        "order" to "clickcount",
        "reverse" to "true",
    )
    val typed = text.trim()
    when (field) {
        SearchField.NAME -> if (typed.isNotEmpty()) map["name"] = typed
        SearchField.TAG -> if (typed.isNotEmpty()) map["tag"] = typed
        SearchField.COUNTRY -> when {
            typed.length == 2 && typed.all { it.isLetter() } -> map["countrycode"] = typed.uppercase()
            typed.isNotEmpty() -> map["country"] = typed
        }
    }
    country?.trim()?.takeIf { it.isNotEmpty() }?.let { map["country"] = it }
    return map
}

fun List<Station>.respecting(hideBroken: Boolean): List<Station> =
    if (hideBroken) filter { it.lastCheckOk } else this

fun similarQuery(station: Station): CatalogQuery {
    val tag = station.tagList.firstOrNull()
    return if (!tag.isNullOrBlank()) {
        CatalogQuery(text = tag, field = SearchField.TAG, hideBroken = true, limit = 30)
    } else {
        CatalogQuery(country = station.country, hideBroken = true, limit = 30)
    }
}

enum class FailureReason {
    NETWORK,
    TIMEOUT,
    SERVER,
}

enum class CatalogOrigin {
    LIVE,
    CACHE,
    BUNDLED,
}

sealed interface LoadResult<out T> {
    data class Fresh<T>(val value: T) : LoadResult<T>
    data class Cached<T>(
        val value: T,
        val stale: Boolean,
        val origin: CatalogOrigin = CatalogOrigin.CACHE,
    ) : LoadResult<T>
    data class Failed(val reason: FailureReason) : LoadResult<Nothing>
}

/** The list a result carries, or empty when it failed. */
fun <T> LoadResult<List<T>>.valueOrEmpty(): List<T> = when (this) {
    is LoadResult.Fresh -> value
    is LoadResult.Cached -> value
    is LoadResult.Failed -> emptyList()
}
