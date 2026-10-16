package net.kravos.weather.util

import net.kravos.weather.data.AirQuality
import net.kravos.weather.data.Forecast
import net.kravos.weather.data.Hour
import net.kravos.weather.data.Pollen
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** "Smart" text the Weather Channel premium tier sells: nowcast, comparisons, activity scores. */
object Insights {

    fun localNow(f: Forecast): LocalDateTime =
        java.time.Instant.now().atOffset(java.time.ZoneOffset.ofTotalSeconds(f.utcOffsetSec)).toLocalDateTime()

    fun upcomingHours(f: Forecast, count: Int): List<Hour> {
        val now = localNow(f).withMinute(0).withSecond(0).withNano(0)
        return f.hours.filter { !it.time.isBefore(now) }.take(count)
    }

    /** Nowcast from 15-minute data: "Kiša počinje za ~30 min", "Kiša prestaje oko 14:45". */
    fun nowcast(f: Forecast): String? {
        val now = localNow(f)
        val next = f.minutely.filter { it.time.isAfter(now.minusMinutes(15)) }.take(12) // 3 h
        if (next.isEmpty()) return null
        val wetNow = next.first().precip >= 0.1
        return if (wetNow) {
            val stop = next.indexOfFirst { it.precip < 0.1 }
            if (stop == -1) "Padavine se nastavljaju naredna 3 sata."
            else "Padavine prestaju oko ${Fmt.hhmm(next[stop].time)}."
        } else {
            val start = next.indexOfFirst { it.precip >= 0.1 }
            if (start == -1) "Bez padavina naredna 3 sata."
            else {
                val mins = java.time.Duration.between(now, next[start].time).toMinutes().coerceAtLeast(5)
                val total = next.drop(start).takeWhile { it.precip >= 0.1 }.sumOf { it.precip }
                val kind = if (total > 4) "Jaka kiša" else "Kiša"
                "$kind počinje za oko ${(mins / 5 * 5).coerceAtLeast(5)} min (≈${Fmt.hhmm(next[start].time)})."
            }
        }
    }

    fun vsYesterday(f: Forecast, u: Units): String? {
        val now = localNow(f).withMinute(0).withSecond(0).withNano(0)
        val y = f.hours.firstOrNull { it.time == now.minusDays(1) } ?: return null
        val diff = f.current.temp - y.temp
        val d = if (u.temp == net.kravos.weather.data.TempUnit.F) diff * 9 / 5 else diff
        val r = abs(d).roundToInt()
        return when {
            r == 0 -> "Isto kao juče u ovo vreme"
            d > 0 -> "Toplije za $r° nego juče"
            else -> "Hladnije za $r° nego juče"
        }
    }

    /** One-line headline for today, like "Danas pretežno sunčano, popodne pljuskovi. Max 18°." */
    fun todaySummary(f: Forecast, u: Units): String {
        val now = localNow(f)
        val today = f.days.firstOrNull { it.date == now.toLocalDate() } ?: return ""
        val rest = f.hours.filter { it.time.toLocalDate() == today.date && !it.time.isBefore(now.withMinute(0)) }
        val rainy = rest.filter { it.precipProb >= 50 && it.precip >= 0.2 }
        val parts = ArrayList<String>()
        parts += Wx.describe(today.code).replaceFirstChar { it.uppercase() }
        if (rainy.isNotEmpty()) {
            val h = rainy.first().time.hour
            val when_ = when (h) { in 0..5 -> "noću"; in 6..11 -> "pre podne"; in 12..17 -> "popodne"; else -> "uveče" }
            parts += if (rainy.any { Wx.isStorm(it.code) }) "grmljavina $when_" else "padavine $when_ (od ~${h}h)"
        }
        if (today.gustMax >= 50) parts += "udari vetra do ${Fmt.wind(today.gustMax, u)}"
        return parts.joinToString(", ") + ". Maks ${Fmt.temp(today.tMax, u)}, min ${Fmt.temp(today.tMin, u)}."
    }

    fun tonight(f: Forecast, u: Units): String? {
        val now = localNow(f)
        val night = f.hours.filter { it.time.isAfter(now) && (it.time.hour >= 21 || it.time.hour <= 6) }.take(10)
        if (night.isEmpty()) return null
        val min = night.minOf { it.temp }
        val rain = night.any { it.precipProb >= 50 && it.precip > 0.2 }
        val frost = min <= 0.5
        return buildString {
            append("Noćas ${Wx.describe(night[night.size / 2].code).lowercase()}, min ${Fmt.temp(min, u)}")
            if (rain) append(", moguće padavine")
            if (frost) append(". Mogućnost mraza")
            append('.')
        }
    }

    // ------------------------------------------------ activities

    data class Activity(val name: String, val score: Int, val note: String)

    private fun s(v: Double) = v.coerceIn(0.0, 10.0).roundToInt()

    fun activities(f: Forecast, air: AirQuality?): List<Activity> {
        val h = upcomingHours(f, 6)
        if (h.isEmpty()) return emptyList()
        val t = h.map { it.feelsLike }.average()
        val wind = h.maxOf { it.windSpeed }
        val gust = h.maxOf { it.windGusts }
        val pop = h.maxOf { it.precipProb }
        val rain = h.sumOf { it.precip }
        val uv = h.maxOf { it.uv }
        val aqi = air?.eaqi ?: 20.0
        val pollenMax = air?.pollen?.entries?.maxOfOrNull { (p, v) -> p.level(v) } ?: 0

        fun tempScore(ideal: Double, tol: Double) = 10 - abs(t - ideal) / tol * 10
        val rainPenalty = (pop / 10.0) * 0.6 + rain * 1.5
        val airPenalty = ((aqi - 40) / 10).coerceAtLeast(0.0)

        val running = s(tempScore(12.0, 15.0) - rainPenalty - wind / 12 - airPenalty - pollenMax * 0.5)
        val cycling = s(tempScore(18.0, 14.0) - rainPenalty - wind / 7 - gust / 20 - airPenalty * 0.7)
        val spraying = s(10 - wind / 2.5 - gust / 10 - rainPenalty * 1.6 - (if (t > 27 || t < 5) 4.0 else 0.0))
        val bbq = s(tempScore(23.0, 14.0) - rainPenalty * 1.2 - wind / 10)
        val walking = s(tempScore(18.0, 18.0) - rainPenalty * 0.8 - wind / 15 - airPenalty * 0.6 - pollenMax * 0.4)
        val laundry = s(7 + (t - 15) / 4 - rainPenalty * 1.5 + wind / 25 - h.map { it.humidity }.average() / 25)

        fun note(score: Int, bad: String) = when {
            score >= 8 -> "Odlični uslovi"
            score >= 6 -> "Dobri uslovi"
            score >= 4 -> "Prihvatljivo"
            else -> bad
        }
        return listOf(
            Activity("Trčanje", running, note(running, if (airPenalty > 2) "Loš vazduh" else if (pop > 50) "Kiša" else "Loši uslovi")),
            Activity("Šetnja", walking, note(walking, if (pop > 50) "Kiša" else "Loši uslovi")),
            Activity("Biciklizam", cycling, note(cycling, if (wind > 25) "Jak vetar" else if (pop > 50) "Kiša" else "Loši uslovi")),
            Activity("Prskanje / bašta", spraying, note(spraying, if (wind > 15) "Previše vetra" else "Kiša/temperatura")),
            Activity("Roštilj napolju", bbq, note(bbq, if (pop > 50) "Kiša" else "Prohladno")),
            Activity("Sušenje veša", laundry.coerceIn(0, 10), note(laundry.coerceIn(0, 10), "Vlažno/kiša")),
        ) + listOfNotNull(
            if (uv >= 6) Activity("Zaštita od sunca", s(10 - uv), "UV ${uv.roundToInt()} — krema i kapa") else null,
        )
    }

    fun topPollen(air: AirQuality?): Pair<Pollen, Int>? =
        air?.pollen?.entries?.map { (p, v) -> p to p.level(v) }?.filter { it.second > 0 }?.maxByOrNull { it.second }
}
