package net.kravos.weather.util

import androidx.compose.ui.graphics.Color
import net.kravos.weather.data.TempUnit
import net.kravos.weather.data.WindUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/** Display settings, captured once per composition. */
data class Units(val temp: TempUnit = TempUnit.C, val wind: WindUnit = WindUnit.KMH)

object Fmt {

    fun temp(c: Double, u: Units, withUnit: Boolean = false): String {
        val v = if (u.temp == TempUnit.F) c * 9 / 5 + 32 else c
        val r = v.roundToInt().let { if (it == 0) 0 else it } // avoid "-0"
        return if (withUnit) "$r°${if (u.temp == TempUnit.F) "F" else "C"}" else "$r°"
    }

    fun beaufort(kmh: Double): Int {
        val limits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
        return limits.indexOfFirst { kmh < it }.let { if (it == -1) 12 else it }
    }

    fun windValue(kmh: Double, u: Units): String = when (u.wind) {
        WindUnit.KMH -> "${kmh.roundToInt()}"
        WindUnit.MS -> String.format(Locale.US, "%.1f", kmh / 3.6).replace('.', ',')
        WindUnit.BFT -> "${beaufort(kmh)}"
    }

    fun windUnit(u: Units) = when (u.wind) {
        WindUnit.KMH -> "km/h"; WindUnit.MS -> "m/s"; WindUnit.BFT -> "Bf"
    }

    fun wind(kmh: Double, u: Units) = "${windValue(kmh, u)} ${windUnit(u)}"

    private val DIRS = arrayOf("S", "SSI", "SI", "ISI", "I", "IJI", "JI", "JJI", "J", "JJZ", "JZ", "ZJZ", "Z", "ZSZ", "SZ", "SSZ")
    fun dir(deg: Int): String = DIRS[(((deg % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16]

    private val DIR_NAMES = mapOf(
        "S" to "severac", "SI" to "severoistočnjak", "I" to "istočnjak", "JI" to "jugoistočnjak",
        "J" to "južni vetar", "JZ" to "jugozapadnjak", "Z" to "zapadnjak", "SZ" to "severozapadnjak",
    )
    fun windName(deg: Int): String = DIR_NAMES[DIRS[(((deg % 360) + 360) % 360 / 45.0 + 0.5).toInt() % 8 * 2]] ?: ""

    fun windDescription(kmh: Double): String = when (beaufort(kmh)) {
        0 -> "Tiho"; 1 -> "Lahor"; 2 -> "Povetarac"; 3 -> "Slab vetar"; 4 -> "Umeren vetar"
        5 -> "Umereno jak vetar"; 6 -> "Jak vetar"; 7 -> "Žestok vetar"; 8 -> "Olujni vetar"
        9 -> "Jak olujni vetar"; 10 -> "Orkanski vetar"; else -> "Orkan"
    }

    fun mm(v: Double): String = when {
        v <= 0.0 -> "0 mm"
        v < 0.1 -> "<0,1 mm"
        v < 10 -> String.format(Locale.US, "%.1f mm", v).replace('.', ',')
        else -> "${v.roundToInt()} mm"
    }

    fun hhmm(t: LocalDateTime?) = t?.let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) } ?: "--:--"
    fun hour(t: LocalDateTime) = String.format(Locale.US, "%02d", t.hour)

    fun duration(sec: Double): String {
        val m = (sec / 60).roundToInt()
        return "${m / 60} h ${m % 60} min"
    }

    fun dayShort(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "Pon"; DayOfWeek.TUESDAY -> "Uto"; DayOfWeek.WEDNESDAY -> "Sre"
        DayOfWeek.THURSDAY -> "Čet"; DayOfWeek.FRIDAY -> "Pet"; DayOfWeek.SATURDAY -> "Sub"; DayOfWeek.SUNDAY -> "Ned"
    }

    fun dayLong(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "Ponedeljak"; DayOfWeek.TUESDAY -> "Utorak"; DayOfWeek.WEDNESDAY -> "Sreda"
        DayOfWeek.THURSDAY -> "Četvrtak"; DayOfWeek.FRIDAY -> "Petak"; DayOfWeek.SATURDAY -> "Subota"; DayOfWeek.SUNDAY -> "Nedelja"
    }

    fun monthName(m: Month) = when (m) {
        Month.JANUARY -> "januar"; Month.FEBRUARY -> "februar"; Month.MARCH -> "mart"; Month.APRIL -> "april"
        Month.MAY -> "maj"; Month.JUNE -> "jun"; Month.JULY -> "jul"; Month.AUGUST -> "avgust"
        Month.SEPTEMBER -> "septembar"; Month.OCTOBER -> "oktobar"; Month.NOVEMBER -> "novembar"; Month.DECEMBER -> "decembar"
    }

    fun monthShort(m: Month) = monthName(m).take(3)

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Danas"
        today.plusDays(1) -> "Sutra"
        else -> dayShort(date.dayOfWeek)
    }

    fun dateShort(d: LocalDate) = "${d.dayOfMonth}. ${monthShort(d.month)}"

    fun ago(ms: Long): String {
        val min = ((System.currentTimeMillis() - ms) / 60_000).toInt()
        return when {
            min < 1 -> "upravo"
            min < 60 -> "pre $min min"
            min < 24 * 60 -> "pre ${min / 60} h"
            else -> "pre ${min / 1440} d"
        }
    }

    // ------------------------------------------------ indices

    data class Band(val label: String, val color: Color)

    fun eaqi(v: Double?): Band = when {
        v == null -> Band("Nema podataka", Color(0xFF888888))
        v < 20 -> Band("Dobar", Color(0xFF50C878))
        v < 40 -> Band("Prihvatljiv", Color(0xFFA3D65C))
        v < 60 -> Band("Umeren", Color(0xFFF2C94C))
        v < 80 -> Band("Loš", Color(0xFFF2994A))
        v < 100 -> Band("Vrlo loš", Color(0xFFEB5757))
        else -> Band("Izuzetno loš", Color(0xFF9B51E0))
    }

    fun eaqiAdvice(v: Double?): String = when {
        v == null -> ""
        v < 40 -> "Vazduh je čist. Idealno za aktivnosti napolju."
        v < 60 -> "Osetljive osobe neka smanje duže napore napolju."
        v < 80 -> "Smanji napore napolju, naročito ako imaš astmu ili srčane tegobe."
        else -> "Izbegavaj boravak i vežbanje napolju, zatvori prozore."
    }

    fun uv(v: Double): Band = when {
        v < 3 -> Band("Nizak", Color(0xFF50C878))
        v < 6 -> Band("Umeren", Color(0xFFF2C94C))
        v < 8 -> Band("Visok", Color(0xFFF2994A))
        v < 11 -> Band("Vrlo visok", Color(0xFFEB5757))
        else -> Band("Ekstreman", Color(0xFF9B51E0))
    }

    val POLLEN_LEVELS = listOf(
        Band("Nema", Color(0xFF6B7280)),
        Band("Nizak", Color(0xFF50C878)),
        Band("Umeren", Color(0xFFF2C94C)),
        Band("Visok", Color(0xFFF2994A)),
        Band("Vrlo visok", Color(0xFFEB5757)),
    )

    fun warningColor(level: Int): Color = when (level) {
        4 -> Color(0xFFE53935)
        3 -> Color(0xFFFB8C00)
        2 -> Color(0xFFFDD835)
        else -> Color(0xFF43A047)
    }

    fun warningLabel(level: Int) = when (level) {
        4 -> "Crveno upozorenje"; 3 -> "Narandžasto upozorenje"; 2 -> "Žuto upozorenje"; else -> "Bez upozorenja"
    }

    // ------------------------------------------------ moon

    /** Phase 0..1 (0 = new, 0.5 = full). */
    fun moonPhase(date: LocalDate): Double {
        val known = LocalDate.of(2000, 1, 6).toEpochDay() + 0.75 // new moon 2000-01-06 18:14 UTC
        val days = date.toEpochDay() + 0.5 - known
        val synodic = 29.530588853
        val p = days / synodic
        return p - floor(p)
    }

    fun moonName(p: Double): String = when {
        p < 0.03 || p > 0.97 -> "Mlad mesec"
        p < 0.22 -> "Rastući srp"
        p < 0.28 -> "Prva četvrt"
        p < 0.47 -> "Rastući mesec"
        p < 0.53 -> "Pun mesec"
        p < 0.72 -> "Opadajući mesec"
        p < 0.78 -> "Poslednja četvrt"
        else -> "Opadajući srp"
    }

    fun moonIllumination(p: Double): Int = ((1 - kotlin.math.cos(2 * Math.PI * p)) / 2 * 100).roundToInt()
}
