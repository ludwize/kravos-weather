package net.kravos.weather.data

import java.time.LocalDate
import java.time.LocalDateTime

/** A place the user follows. [isGps] marks the "current location" entry, whose coordinates change. */
data class Place(
    val id: String,
    val name: String,
    val region: String,
    val countryCode: String,
    val lat: Double,
    val lon: Double,
    val isGps: Boolean = false,
)

data class Current(
    val time: LocalDateTime,
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val dewPoint: Double,
    val isDay: Boolean,
    val precipitation: Double,
    val code: Int,
    val cloudCover: Int,
    val pressure: Double,
    val windSpeed: Double,
    val windDir: Int,
    val windGusts: Double,
    val visibility: Double?,
    val uv: Double?,
)

data class Hour(
    val time: LocalDateTime,
    val temp: Double,
    val feelsLike: Double,
    val precipProb: Int,
    val precip: Double,
    val code: Int,
    val windSpeed: Double,
    val windDir: Int,
    val windGusts: Double,
    val humidity: Int,
    val uv: Double,
    val isDay: Boolean,
    val cloudCover: Int,
    val visibility: Double?,
    val dewPoint: Double,
    val pressure: Double,
)

data class Day(
    val date: LocalDate,
    val code: Int,
    val tMax: Double,
    val tMin: Double,
    val feelsMax: Double,
    val feelsMin: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val daylightSec: Double,
    val sunshineSec: Double,
    val uvMax: Double,
    val precipSum: Double,
    val precipProb: Int,
    val precipHours: Double,
    val windMax: Double,
    val gustMax: Double,
    val windDir: Int,
    val snowSum: Double,
)

data class Minutely(val time: LocalDateTime, val precip: Double)

data class Forecast(
    val utcOffsetSec: Int,
    val timezone: String,
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val minutely: List<Minutely>,
    val fetchedAt: Long,
)

enum class Pollen(val key: String, val label: String, val kind: String, val bands: DoubleArray) {
    ALDER("alder_pollen", "Jova", "Drveće", doubleArrayOf(10.0, 50.0, 250.0)),
    BIRCH("birch_pollen", "Breza", "Drveće", doubleArrayOf(10.0, 50.0, 250.0)),
    OLIVE("olive_pollen", "Maslina", "Drveće", doubleArrayOf(10.0, 50.0, 250.0)),
    GRASS("grass_pollen", "Trave", "Trave", doubleArrayOf(5.0, 20.0, 100.0)),
    MUGWORT("mugwort_pollen", "Pelin", "Korovi", doubleArrayOf(5.0, 20.0, 100.0)),
    RAGWEED("ragweed_pollen", "Ambrozija", "Korovi", doubleArrayOf(5.0, 20.0, 100.0));

    /** 0 = none, 1 low, 2 moderate, 3 high, 4 very high */
    fun level(v: Double?): Int = when {
        v == null || v < 1.0 -> 0
        v < bands[0] -> 1
        v < bands[1] -> 2
        v < bands[2] -> 3
        else -> 4
    }
}

data class AirHour(
    val time: LocalDateTime,
    val eaqi: Double?,
    val pm25: Double?,
    val pm10: Double?,
    val pollen: Map<Pollen, Double?>,
)

data class AirQuality(
    val eaqi: Double?,
    val usAqi: Double?,
    val pm25: Double?,
    val pm10: Double?,
    val no2: Double?,
    val o3: Double?,
    val so2: Double?,
    val co: Double?,
    val dust: Double?,
    val uv: Double?,
    val pollen: Map<Pollen, Double?>,
    val hours: List<AirHour>,
    val fetchedAt: Long,
)

/** Averaged ensemble value plus spread (10th..90th percentile of members). */
data class Spread(val mean: Double, val low: Double, val high: Double)

data class LongDay(val date: LocalDate, val tMax: Spread?, val tMin: Spread?, val precip: Spread?)
data class LongPeriod(val start: LocalDate, val temp: Double?, val tempAnomaly: Double?, val precip: Double?, val precipAnomaly: Double?)

data class LongRange(
    val days: List<LongDay>,
    val weeks: List<LongPeriod>,
    val months: List<LongPeriod>,
    val note: String?,
    val fetchedAt: Long,
)

data class Warning(
    val id: String,
    val event: String,
    val headline: String,
    val description: String,
    val instruction: String,
    val level: Int, // 1 green .. 4 red
    val areas: List<String>,
    val onset: String,
    val expires: String,
    val matchesPlace: Boolean,
)

data class Bundle(
    val place: Place,
    val forecast: Forecast?,
    val air: AirQuality?,
    val longRange: LongRange?,
    val warnings: List<Warning>,
    val error: String? = null,
)
