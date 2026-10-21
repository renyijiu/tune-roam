package app.tuneroam.radio.domain.catalog

import kotlin.math.roundToInt

/**
 * Countries arranged west to east so a horizontal drag feels like turning a globe.
 * [name] is the Radio Browser country string. [shortName] and [nameZh] are for the dial.
 */
data class DialStop(
    val name: String,
    val shortName: String,
    val nameZh: String,
    val isoCode: String,
    val longitude: Double,
)

object WorldDial {
    val stops: List<DialStop> = listOf(
        DialStop("The United States Of America", "United States", "美国", "US", -100.0),
        DialStop("Canada", "Canada", "加拿大", "CA", -96.0),
        DialStop("Brazil", "Brazil", "巴西", "BR", -52.0),
        DialStop("Spain", "Spain", "西班牙", "ES", -3.7),
        DialStop("The United Kingdom Of Great Britain And Northern Ireland", "United Kingdom", "英国", "GB", -1.5),
        DialStop("France", "France", "法国", "FR", 2.3),
        DialStop("Germany", "Germany", "德国", "DE", 10.4),
        DialStop("Italy", "Italy", "意大利", "IT", 12.5),
        DialStop("Hungary", "Hungary", "匈牙利", "HU", 19.5),
        DialStop("Poland", "Poland", "波兰", "PL", 19.5),
        DialStop("Egypt", "Egypt", "埃及", "EG", 30.8),
        DialStop("Türkiye", "Türkiye", "土耳其", "TR", 35.2),
        DialStop("India", "India", "印度", "IN", 78.9),
        DialStop("Singapore", "Singapore", "新加坡", "SG", 103.8),
        DialStop("China", "China", "中国", "CN", 104.2),
        DialStop("Hong Kong", "Hong Kong", "香港", "HK", 114.2),
        DialStop("Taiwan, Republic Of China", "Taiwan", "台湾", "TW", 121.0),
        DialStop("The Republic Of Korea", "South Korea", "韩国", "KR", 127.8),
        DialStop("Japan", "Japan", "日本", "JP", 138.2),
        DialStop("Australia", "Australia", "澳大利亚", "AU", 134.0),
    ).sortedBy { it.longitude }

    fun indexOfCode(isoCode: String): Int {
        val found = stops.indexOfFirst { it.isoCode.equals(isoCode, ignoreCase = true) }
        if (found >= 0) return found
        return stops.indexOfFirst { it.isoCode == "CN" }.coerceAtLeast(0)
    }

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
