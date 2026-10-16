package net.kravos.weather.util

import net.kravos.weather.R

/** WMO weather codes -> Serbian text and icons. */
object Wx {

    fun describe(code: Int): String = when (code) {
        0 -> "Vedro"
        1 -> "Pretežno vedro"
        2 -> "Delimično oblačno"
        3 -> "Oblačno"
        45 -> "Magla"
        48 -> "Magla sa injem"
        51 -> "Slaba rosulja"
        53 -> "Rosulja"
        55 -> "Jaka rosulja"
        56, 57 -> "Ledena rosulja"
        61 -> "Slaba kiša"
        63 -> "Kiša"
        65 -> "Jaka kiša"
        66, 67 -> "Ledena kiša"
        71 -> "Slab sneg"
        73 -> "Sneg"
        75 -> "Jak sneg"
        77 -> "Snežna zrnca"
        80 -> "Slabi pljuskovi"
        81 -> "Pljuskovi"
        82 -> "Jaki pljuskovi"
        85 -> "Snežni pljuskovi"
        86 -> "Jaki snežni pljuskovi"
        95 -> "Grmljavina"
        96, 99 -> "Grmljavina sa gradom"
        else -> "—"
    }

    fun icon(code: Int, isDay: Boolean): Int = when (code) {
        0 -> if (isDay) R.drawable.wx_clear_day else R.drawable.wx_clear_night
        1, 2 -> if (isDay) R.drawable.wx_partly_day else R.drawable.wx_partly_night
        3 -> R.drawable.wx_cloudy
        45, 48 -> R.drawable.wx_fog
        51, 53, 55, 61, 80 -> if (isDay) R.drawable.wx_showers_day else R.drawable.wx_rain
        63, 81 -> R.drawable.wx_rain
        65, 82 -> R.drawable.wx_heavy_rain
        56, 57, 66, 67 -> R.drawable.wx_sleet
        71, 73, 75, 77, 85, 86 -> R.drawable.wx_snow
        95, 96, 99 -> R.drawable.wx_storm
        else -> R.drawable.wx_cloudy
    }

    /** Background gradient for the hero card: top, bottom (ARGB). */
    fun gradient(code: Int, isDay: Boolean): Pair<Long, Long> = when {
        code >= 95 -> 0xFF2B2D42 to 0xFF4A3F5C
        !isDay && code <= 1 -> 0xFF0B1730 to 0xFF233B6E
        !isDay -> 0xFF161B26 to 0xFF2C3444
        code == 0 || code == 1 -> 0xFF2F80ED to 0xFF56CCF2
        code == 2 -> 0xFF4A7BC4 to 0xFF8DB3E2
        code == 3 || code == 45 || code == 48 -> 0xFF5D6D7E to 0xFF95A5B5
        code in 71..86 && code !in 80..82 -> 0xFF7D8FA8 to 0xFFC3D0E0
        else -> 0xFF3D5168 to 0xFF6E8299 // rain
    }

    fun isWet(code: Int) = code in 51..67 || code in 80..82 || code >= 95
    fun isSnow(code: Int) = code in 71..77 || code in 85..86
    fun isStorm(code: Int) = code >= 95
}
