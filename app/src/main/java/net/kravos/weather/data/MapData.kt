package net.kravos.weather.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.Locale

data class RadarFrame(val time: Long, val path: String)
data class RadarIndex(val host: String, val frames: List<RadarFrame>)

/** RainViewer public radar (past ~2 h, 10-min steps, max zoom 7, "Universal Blue" scheme). */
object RainViewer {
    suspend fun index(): RadarIndex {
        val o = JSONObject(Http.get("https://api.rainviewer.com/public/weather-maps.json"))
        val host = o.optString("host", "https://tilecache.rainviewer.com")
        val past = o.optJSONObject("radar")?.optJSONArray("past") ?: JSONArray()
        val nowcast = o.optJSONObject("radar")?.optJSONArray("nowcast") ?: JSONArray()
        val frames = ArrayList<RadarFrame>()
        for (a in listOf(past, nowcast)) for (i in 0 until a.length()) {
            val f = a.optJSONObject(i) ?: continue
            frames += RadarFrame(f.optLong("time"), f.optString("path"))
        }
        return RadarIndex(host, frames.sortedBy { it.time })
    }

    fun tileUrl(host: String, frame: RadarFrame, z: Int, x: Int, y: Int) =
        "$host${frame.path}/256/$z/$x/$y/2/1_1.png"
}

enum class GridLayer(val label: String) {
    PRECIP("Padavine"), TEMP("Temperatura"), WIND("Vetar"), CLOUDS("Oblačnost")
}

/** A coarse lat/lon grid of hourly model values, rendered as a colour field on the map ("future radar"). */
class ForecastGrid(
    val north: Double, val south: Double, val west: Double, val east: Double,
    val nx: Int, val ny: Int,
    val times: List<LocalDateTime>,
    /** [layer][hour][iy * nx + ix], iy = 0 at north */
    val values: Map<GridLayer, Array<DoubleArray>>,
    val windDir: Array<DoubleArray>,
) {
    fun value(layer: GridLayer, hour: Int, ix: Int, iy: Int): Double =
        values[layer]?.getOrNull(hour)?.getOrNull(iy * nx + ix) ?: Double.NaN

    companion object {
        suspend fun fetch(north: Double, south: Double, west: Double, east: Double, n: Int = 9): ForecastGrid {
            val lats = ArrayList<Double>(); val lons = ArrayList<Double>()
            for (iy in 0 until n) for (ix in 0 until n) {
                lats += north - (north - south) * iy / (n - 1)
                lons += west + (east - west) * ix / (n - 1)
            }
            fun join(l: List<Double>) = l.joinToString(",") { String.format(Locale.US, "%.3f", it) }
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${join(lats)}&longitude=${join(lons)}" +
                "&hourly=temperature_2m,precipitation,wind_speed_10m,wind_direction_10m,cloud_cover" +
                "&forecast_hours=48&timezone=auto&wind_speed_unit=kmh"
            val body = Http.get(url)
            val arr = if (body.trimStart().startsWith("[")) JSONArray(body) else JSONArray().put(JSONObject(body))
            val count = n * n
            var times: List<LocalDateTime> = emptyList()
            val hoursN = arr.optJSONObject(0)?.optJSONObject("hourly")?.optJSONArray("time")?.length() ?: 0
            val layers = GridLayer.entries.associateWith { Array(hoursN) { DoubleArray(count) { Double.NaN } } }
            val dir = Array(hoursN) { DoubleArray(count) { Double.NaN } }
            for (p in 0 until minOf(count, arr.length())) {
                val h = arr.getJSONObject(p).optJSONObject("hourly") ?: continue
                if (times.isEmpty()) {
                    val t = h.optJSONArray("time") ?: JSONArray()
                    times = List(t.length()) { OpenMeteo.parseTime(t.getString(it)) ?: LocalDateTime.now() }
                }
                fun fill(key: String, target: Array<DoubleArray>) {
                    val a = h.optJSONArray(key) ?: return
                    for (i in 0 until minOf(hoursN, a.length())) target[i][p] = if (a.isNull(i)) Double.NaN else a.optDouble(i)
                }
                fill("precipitation", layers.getValue(GridLayer.PRECIP))
                fill("temperature_2m", layers.getValue(GridLayer.TEMP))
                fill("wind_speed_10m", layers.getValue(GridLayer.WIND))
                fill("cloud_cover", layers.getValue(GridLayer.CLOUDS))
                fill("wind_direction_10m", dir)
            }
            return ForecastGrid(north, south, west, east, n, n, times, layers, dir)
        }
    }
}
