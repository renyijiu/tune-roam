package app.tuneroam.radio.domain.catalog

import kotlin.math.roundToInt

/**
 * Countries arranged west to east so a horizontal drag feels like turning a globe.
 * [name] is the Radio Browser country string. [shortName] and [nameZh] are for the dial.
 * [longitude] and [latitude] place the stop on the globe, near where most of its stations are.
 */
data class DialStop(
    val name: String,
    val shortName: String,
    val nameZh: String,
    val isoCode: String,
    val longitude: Double,
    val latitude: Double,
)

object WorldDial {
    val stops: List<DialStop> = listOf(
        DialStop("The United States Of America", "United States", "美国", "US", -100.0, 39.8),
        DialStop("Canada", "Canada", "加拿大", "CA", -96.0, 49.5),
        DialStop("Brazil", "Brazil", "巴西", "BR", -52.0, -14.2),
        DialStop("Spain", "Spain", "西班牙", "ES", -3.7, 40.4),
        DialStop("The United Kingdom Of Great Britain And Northern Ireland", "United Kingdom", "英国", "GB", -1.5, 52.5),
        DialStop("France", "France", "法国", "FR", 2.3, 46.6),
        DialStop("Germany", "Germany", "德国", "DE", 10.4, 51.2),
        DialStop("Italy", "Italy", "意大利", "IT", 12.5, 42.8),
        DialStop("Hungary", "Hungary", "匈牙利", "HU", 19.5, 47.2),
        DialStop("Poland", "Poland", "波兰", "PL", 19.5, 52.0),
        DialStop("Egypt", "Egypt", "埃及", "EG", 30.8, 30.0),
        DialStop("Türkiye", "Türkiye", "土耳其", "TR", 35.2, 39.0),
        DialStop("India", "India", "印度", "IN", 78.9, 22.0),
        DialStop("Singapore", "Singapore", "新加坡", "SG", 103.8, 1.35),
        DialStop("China", "China", "中国", "CN", 104.2, 35.9),
        DialStop("Hong Kong", "Hong Kong", "香港", "HK", 114.2, 22.3),
        DialStop("Taiwan, Republic Of China", "Taiwan", "台湾", "TW", 121.0, 23.7),
        DialStop("The Republic Of Korea", "South Korea", "韩国", "KR", 127.8, 36.5),
        DialStop("Japan", "Japan", "日本", "JP", 138.2, 36.2),
        DialStop("Australia", "Australia", "澳大利亚", "AU", 134.0, -25.3),
    ).sortedBy { it.longitude }

    /** Null when the country has no stop on the dial. */
    fun indexOfCode(isoCode: String): Int? =
        stops.indexOfFirst { it.isoCode.equals(isoCode, ignoreCase = true) }.takeIf { it >= 0 }

    /** Where the dial starts. */
    val homeIndex: Int
        get() = indexOfCode("CN") ?: 0

    /** Degrees that put [index] under the top needle. Inverse of [dialIndex]. */
    fun rotationFor(index: Int): Float {
        if (stops.isEmpty()) return 0f
        val step = 360f / stops.size
        return -index * step
    }

    fun dialIndex(rotationDegrees: Float, count: Int = stops.size): Int {
        if (count <= 0) return 0
        val step = 360f / count
        val raw = -rotationDegrees / step
        val wrapped = ((raw % count) + count) % count
        var index = wrapped.roundToInt() % count
        if (index < 0) index += count
        return index
    }
}
