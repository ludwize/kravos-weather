package net.kravos.weather.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class TempUnit { C, F }
enum class WindUnit(val label: String) { KMH("km/h"), MS("m/s"), BFT("Bofor") }
enum class ThemeMode(val label: String) { SYSTEM("Sistemska"), DARK("Tamna"), LIGHT("Svetla") }

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("kravos_weather", Context.MODE_PRIVATE)

    // ------------------------------------------------ places
    var gpsPlace: Place?
        get() = sp.getString("gps_place", null)?.let { fromJson(JSONObject(it)) }
        set(v) = sp.edit().putString("gps_place", v?.let { toJson(it).toString() }).apply()

    var savedPlaces: List<Place>
        get() = sp.getString("places", null)?.let { s ->
            val a = JSONArray(s)
            List(a.length()) { fromJson(a.getJSONObject(it)) }
        } ?: emptyList()
        set(v) = sp.edit().putString("places", JSONArray().also { a -> v.forEach { a.put(toJson(it)) } }.toString()).apply()

    var useGps: Boolean
        get() = sp.getBoolean("use_gps", true)
        set(v) = sp.edit().putBoolean("use_gps", v).apply()

    var selectedId: String?
        get() = sp.getString("selected", null)
        set(v) = sp.edit().putString("selected", v).apply()

    // ------------------------------------------------ units / look
    var tempUnit: TempUnit
        get() = TempUnit.valueOf(sp.getString("temp_unit", "C")!!)
        set(v) = sp.edit().putString("temp_unit", v.name).apply()

    var windUnit: WindUnit
        get() = WindUnit.valueOf(sp.getString("wind_unit", "KMH")!!)
        set(v) = sp.edit().putString("wind_unit", v.name).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!)
        set(v) = sp.edit().putString("theme", v.name).apply()

    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic_color", true)
        set(v) = sp.edit().putBoolean("dynamic_color", v).apply()

    // ------------------------------------------------ notifications
    var notifyRain: Boolean
        get() = sp.getBoolean("n_rain", true)
        set(v) = sp.edit().putBoolean("n_rain", v).apply()
    var notifyWarnings: Boolean
        get() = sp.getBoolean("n_warn", true)
        set(v) = sp.edit().putBoolean("n_warn", v).apply()
    var notifyMorning: Boolean
        get() = sp.getBoolean("n_morning", true)
        set(v) = sp.edit().putBoolean("n_morning", v).apply()
    var morningHour: Int
        get() = sp.getInt("n_morning_hour", 7)
        set(v) = sp.edit().putInt("n_morning_hour", v).apply()
    var notifyFrost: Boolean
        get() = sp.getBoolean("n_frost", true)
        set(v) = sp.edit().putBoolean("n_frost", v).apply()
    var notifyAir: Boolean
        get() = sp.getBoolean("n_air", true)
        set(v) = sp.edit().putBoolean("n_air", v).apply()
    var notifyPollen: Boolean
        get() = sp.getBoolean("n_pollen", true)
        set(v) = sp.edit().putBoolean("n_pollen", v).apply()
    var pollenWatch: Set<String>
        get() = sp.getStringSet("pollen_watch", Pollen.entries.map { it.name }.toSet())!!
        set(v) = sp.edit().putStringSet("pollen_watch", v).apply()
    var notifyStorm: Boolean
        get() = sp.getBoolean("n_storm", true)
        set(v) = sp.edit().putBoolean("n_storm", v).apply()

    /** Keys of notifications already shown, so the same event isn't announced twice. */
    fun alreadyNotified(key: String): Boolean = sp.getStringSet("notified", emptySet())!!.contains(key)
    fun markNotified(key: String) {
        val cur = sp.getStringSet("notified", emptySet())!!.toMutableSet()
        cur += key
        // keep the set small
        val trimmed = if (cur.size > 300) cur.toList().takeLast(200).toSet() else cur
        sp.edit().putStringSet("notified", trimmed).apply()
    }

    companion object {
        fun toJson(p: Place) = JSONObject()
            .put("id", p.id).put("name", p.name).put("region", p.region).put("cc", p.countryCode)
            .put("lat", p.lat).put("lon", p.lon).put("gps", p.isGps)

        fun fromJson(o: JSONObject) = Place(
            id = o.getString("id"), name = o.optString("name"), region = o.optString("region"),
            countryCode = o.optString("cc"), lat = o.getDouble("lat"), lon = o.getDouble("lon"),
            isGps = o.optBoolean("gps", false),
        )
    }
}
