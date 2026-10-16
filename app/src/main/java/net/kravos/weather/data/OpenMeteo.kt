package net.kravos.weather.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** Open-Meteo: forecast, air quality (incl. pollen), seasonal/sub-seasonal, geocoding. Free, no API key. */
object OpenMeteo {

    private fun ll(v: Double) = String.format(Locale.US, "%.4f", v)

    fun forecastUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${ll(lat)}&longitude=${ll(lon)}" +
            "&timezone=auto&past_days=1&forecast_days=16&wind_speed_unit=kmh" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code," +
            "cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,dew_point_2m,visibility,uv_index" +
            "&minutely_15=precipitation&forecast_minutely_15=24" +
            "&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,relative_humidity_2m,uv_index,is_day,cloud_cover," +
            "visibility,dew_point_2m,pressure_msl" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min," +
            "sunrise,sunset,daylight_duration,sunshine_duration,uv_index_max,precipitation_sum," +
            "precipitation_probability_max,precipitation_hours,wind_speed_10m_max,wind_gusts_10m_max," +
            "wind_direction_10m_dominant,snowfall_sum"

    private val POLLEN_KEYS = Pollen.entries.joinToString(",") { it.key }

    fun airUrl(lat: Double, lon: Double): String =
        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${ll(lat)}&longitude=${ll(lon)}" +
            "&timezone=auto&forecast_days=5" +
            "&current=european_aqi,us_aqi,pm10,pm2_5,nitrogen_dioxide,ozone,sulphur_dioxide,carbon_monoxide,dust,uv_index,$POLLEN_KEYS" +
            "&hourly=european_aqi,pm2_5,pm10,$POLLEN_KEYS"

    fun seasonalDailyUrl(lat: Double, lon: Double): String =
        "https://seasonal-api.open-meteo.com/v1/seasonal?latitude=${ll(lat)}&longitude=${ll(lon)}" +
            "&timezone=auto&forecast_days=46&daily=temperature_2m_max,temperature_2m_min,precipitation_sum"

    fun seasonalWeeklyUrl(lat: Double, lon: Double): String =
        "https://seasonal-api.open-meteo.com/v1/seasonal?latitude=${ll(lat)}&longitude=${ll(lon)}" +
            "&timezone=auto&weekly=temperature_2m_mean,temperature_2m_anomaly,precipitation_mean,precipitation_anomaly"

    fun seasonalMonthlyUrl(lat: Double, lon: Double): String =
        "https://seasonal-api.open-meteo.com/v1/seasonal?latitude=${ll(lat)}&longitude=${ll(lon)}" +
            "&timezone=auto&monthly=temperature_2m_mean,temperature_2m_anomaly,precipitation_mean,precipitation_anomaly"

    fun geocodeUrl(q: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=12&language=sr&format=json"

    // ---------------------------------------------------------------- parsing helpers

    internal fun JSONObject.dArr(key: String): List<Double?> {
        val a = optJSONArray(key) ?: return emptyList()
        return List(a.length()) { i -> if (a.isNull(i)) null else a.optDouble(i).takeUnless { it.isNaN() } }
    }

    internal fun JSONObject.sArr(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        return List(a.length()) { i -> a.optString(i) }
    }

    internal fun JSONObject.dbl(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

    private fun <T> List<T>.at(i: Int): T? = getOrNull(i)

    fun parseTime(s: String): LocalDateTime? = try {
        if (s.length == 10) LocalDate.parse(s).atStartOfDay() else LocalDateTime.parse(s)
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- forecast

    fun parseForecast(json: String, fetchedAt: Long): Forecast {
        val o = JSONObject(json)
        val c = o.getJSONObject("current")
        val current = Current(
            time = parseTime(c.optString("time")) ?: LocalDateTime.now(),
            temp = c.dbl("temperature_2m") ?: 0.0,
            feelsLike = c.dbl("apparent_temperature") ?: c.dbl("temperature_2m") ?: 0.0,
            humidity = (c.dbl("relative_humidity_2m") ?: 0.0).toInt(),
            dewPoint = c.dbl("dew_point_2m") ?: 0.0,
            isDay = (c.dbl("is_day") ?: 1.0) > 0.5,
            precipitation = c.dbl("precipitation") ?: 0.0,
            code = (c.dbl("weather_code") ?: 0.0).toInt(),
            cloudCover = (c.dbl("cloud_cover") ?: 0.0).toInt(),
            pressure = c.dbl("pressure_msl") ?: 0.0,
            windSpeed = c.dbl("wind_speed_10m") ?: 0.0,
            windDir = (c.dbl("wind_direction_10m") ?: 0.0).toInt(),
            windGusts = c.dbl("wind_gusts_10m") ?: 0.0,
            visibility = c.dbl("visibility"),
            uv = c.dbl("uv_index"),
        )

        val h = o.getJSONObject("hourly")
        val ht = h.sArr("time")
        val temp = h.dArr("temperature_2m"); val feels = h.dArr("apparent_temperature")
        val pp = h.dArr("precipitation_probability"); val pr = h.dArr("precipitation")
        val code = h.dArr("weather_code"); val ws = h.dArr("wind_speed_10m"); val wd = h.dArr("wind_direction_10m")
        val wg = h.dArr("wind_gusts_10m"); val rh = h.dArr("relative_humidity_2m"); val uv = h.dArr("uv_index")
        val isDay = h.dArr("is_day"); val cc = h.dArr("cloud_cover"); val vis = h.dArr("visibility")
        val dp = h.dArr("dew_point_2m"); val pm = h.dArr("pressure_msl")
        val hours = ht.indices.mapNotNull { i ->
            val t = parseTime(ht[i]) ?: return@mapNotNull null
            val tv = temp.at(i) ?: return@mapNotNull null
            Hour(
                time = t, temp = tv, feelsLike = feels.at(i) ?: tv,
                precipProb = (pp.at(i) ?: 0.0).toInt(), precip = pr.at(i) ?: 0.0,
                code = (code.at(i) ?: 0.0).toInt(), windSpeed = ws.at(i) ?: 0.0,
                windDir = (wd.at(i) ?: 0.0).toInt(), windGusts = wg.at(i) ?: 0.0,
                humidity = (rh.at(i) ?: 0.0).toInt(), uv = uv.at(i) ?: 0.0,
                isDay = (isDay.at(i) ?: 1.0) > 0.5, cloudCover = (cc.at(i) ?: 0.0).toInt(),
                visibility = vis.at(i), dewPoint = dp.at(i) ?: 0.0, pressure = pm.at(i) ?: 0.0,
            )
        }

        val d = o.getJSONObject("daily")
        val dt = d.sArr("time")
        val dc = d.dArr("weather_code"); val tmax = d.dArr("temperature_2m_max"); val tmin = d.dArr("temperature_2m_min")
        val fmax = d.dArr("apparent_temperature_max"); val fmin = d.dArr("apparent_temperature_min")
        val sr = d.sArr("sunrise"); val ss = d.sArr("sunset"); val dl = d.dArr("daylight_duration")
        val sh = d.dArr("sunshine_duration"); val uvm = d.dArr("uv_index_max"); val ps = d.dArr("precipitation_sum")
        val ppm = d.dArr("precipitation_probability_max"); val ph = d.dArr("precipitation_hours")
        val wsm = d.dArr("wind_speed_10m_max"); val wgm = d.dArr("wind_gusts_10m_max")
        val wdd = d.dArr("wind_direction_10m_dominant"); val sn = d.dArr("snowfall_sum")
        val days = dt.indices.mapNotNull { i ->
            val date = try { LocalDate.parse(dt[i]) } catch (e: Exception) { return@mapNotNull null }
            val mx = tmax.at(i) ?: return@mapNotNull null
            val mn = tmin.at(i) ?: return@mapNotNull null
            Day(
                date = date, code = (dc.at(i) ?: 0.0).toInt(), tMax = mx, tMin = mn,
                feelsMax = fmax.at(i) ?: mx, feelsMin = fmin.at(i) ?: mn,
                sunrise = sr.at(i)?.let { parseTime(it) }, sunset = ss.at(i)?.let { parseTime(it) },
                daylightSec = dl.at(i) ?: 0.0, sunshineSec = sh.at(i) ?: 0.0, uvMax = uvm.at(i) ?: 0.0,
                precipSum = ps.at(i) ?: 0.0, precipProb = (ppm.at(i) ?: 0.0).toInt(),
                precipHours = ph.at(i) ?: 0.0, windMax = wsm.at(i) ?: 0.0, gustMax = wgm.at(i) ?: 0.0,
                windDir = (wdd.at(i) ?: 0.0).toInt(), snowSum = sn.at(i) ?: 0.0,
            )
        }

        val minutely = o.optJSONObject("minutely_15")?.let { m ->
            val mt = m.sArr("time"); val mp = m.dArr("precipitation")
            mt.indices.mapNotNull { i -> parseTime(mt[i])?.let { Minutely(it, mp.at(i) ?: 0.0) } }
        } ?: emptyList()

        return Forecast(
            utcOffsetSec = o.optInt("utc_offset_seconds", 0),
            timezone = o.optString("timezone", "UTC"),
            current = current, hours = hours, days = days, minutely = minutely, fetchedAt = fetchedAt,
        )
    }

    // ---------------------------------------------------------------- air quality

    fun parseAir(json: String, fetchedAt: Long): AirQuality {
        val o = JSONObject(json)
        val c = o.optJSONObject("current") ?: JSONObject()
        val h = o.optJSONObject("hourly") ?: JSONObject()
        val t = h.sArr("time")
        val eaqi = h.dArr("european_aqi"); val pm25 = h.dArr("pm2_5"); val pm10 = h.dArr("pm10")
        val pol = Pollen.entries.associateWith { h.dArr(it.key) }
        val hours = t.indices.mapNotNull { i ->
            val time = parseTime(t[i]) ?: return@mapNotNull null
            AirHour(time, eaqi.at(i), pm25.at(i), pm10.at(i), Pollen.entries.associateWith { p -> pol[p]?.at(i) })
        }
        return AirQuality(
            eaqi = c.dbl("european_aqi"), usAqi = c.dbl("us_aqi"), pm25 = c.dbl("pm2_5"), pm10 = c.dbl("pm10"),
            no2 = c.dbl("nitrogen_dioxide"), o3 = c.dbl("ozone"), so2 = c.dbl("sulphur_dioxide"),
            co = c.dbl("carbon_monoxide"), dust = c.dbl("dust"), uv = c.dbl("uv_index"),
            pollen = Pollen.entries.associateWith { c.dbl(it.key) },
            hours = hours, fetchedAt = fetchedAt,
        )
    }

    // ---------------------------------------------------------------- seasonal

    /** All arrays in [obj] belonging to variable [base]: exact key, or ensemble members / model-suffixed keys. */
    private fun members(obj: JSONObject, base: String): List<List<Double?>> {
        val out = ArrayList<List<Double?>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k == base || (k.startsWith(base + "_") && !k.removePrefix(base + "_").startsWith("anomaly"))) {
                if (obj.optJSONArray(k) != null) out += obj.dArr(k)
            }
        }
        return out
    }

    private fun spreadAt(ms: List<List<Double?>>, i: Int): Spread? {
        val v = ms.mapNotNull { it.getOrNull(i) }.sorted()
        if (v.isEmpty()) return null
        val mean = v.average()
        val lo = v[((v.size - 1) * 0.1).toInt()]
        val hi = v[((v.size - 1) * 0.9).toInt()]
        return Spread(mean, lo, hi)
    }

    private fun meanAt(ms: List<List<Double?>>, i: Int): Double? =
        ms.mapNotNull { it.getOrNull(i) }.takeIf { it.isNotEmpty() }?.average()

    private fun timeList(obj: JSONObject): List<LocalDate?> {
        val a = obj.optJSONArray("time") ?: return emptyList()
        return List(a.length()) { i ->
            val v = a.opt(i)
            when (v) {
                is String -> parseTime(v)?.toLocalDate()
                is Number -> java.time.Instant.ofEpochSecond(v.toLong()).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                else -> null
            }
        }
    }

    fun parseSeasonalDaily(json: String): List<LongDay> {
        val d = JSONObject(json).optJSONObject("daily") ?: return emptyList()
        val t = timeList(d)
        val mx = members(d, "temperature_2m_max"); val mn = members(d, "temperature_2m_min"); val pr = members(d, "precipitation_sum")
        return t.indices.mapNotNull { i ->
            val date = t[i] ?: return@mapNotNull null
            val a = spreadAt(mx, i); val b = spreadAt(mn, i)
            if (a == null && b == null) null else LongDay(date, a, b, spreadAt(pr, i))
        }
    }

    fun parsePeriods(json: String, section: String): List<LongPeriod> {
        val d = JSONObject(json).optJSONObject(section) ?: return emptyList()
        val t = timeList(d)
        val tm = members(d, "temperature_2m_mean"); val ta = members(d, "temperature_2m_anomaly")
        val pm = members(d, "precipitation_mean"); val pa = members(d, "precipitation_anomaly")
        return t.indices.mapNotNull { i ->
            val date = t[i] ?: return@mapNotNull null
            val p = LongPeriod(date, meanAt(tm, i), meanAt(ta, i), meanAt(pm, i), meanAt(pa, i))
            if (p.temp == null && p.tempAnomaly == null && p.precip == null && p.precipAnomaly == null) null else p
        }
    }

    // ---------------------------------------------------------------- geocoding

    fun parseGeocode(json: String): List<Place> {
        val arr: JSONArray = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return List(arr.length()) { i ->
            val r = arr.getJSONObject(i)
            val name = Translit.toLatin(r.optString("name"))
            val admin = listOf(r.optString("admin1"), r.optString("country"))
                .filter { it.isNotBlank() }.joinToString(", ") { Translit.toLatin(it) }
            Place(
                id = "p" + r.optLong("id", System.nanoTime()),
                name = name, region = admin, countryCode = r.optString("country_code").uppercase(),
                lat = r.optDouble("latitude"), lon = r.optDouble("longitude"),
            )
        }
    }
}
