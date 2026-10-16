package net.kravos.weather.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Official national weather warnings (RHMZ Srbije, HNMS Grčka, ARSO, ...) via the EUMETNET Meteoalarm feeds. */
object Meteoalarm {

    private val SLUGS = mapOf(
        "RS" to "serbia", "GR" to "greece", "SI" to "slovenia", "HR" to "croatia", "BA" to "bosnia-herzegovina",
        "ME" to "montenegro", "MK" to "republic-of-north-macedonia", "HU" to "hungary", "RO" to "romania",
        "BG" to "bulgaria", "AT" to "austria", "DE" to "germany", "IT" to "italy", "FR" to "france",
        "ES" to "spain", "PT" to "portugal", "CH" to "switzerland", "CZ" to "czechia", "SK" to "slovakia",
        "PL" to "poland", "NL" to "netherlands", "BE" to "belgium", "LU" to "luxembourg", "DK" to "denmark",
        "SE" to "sweden", "NO" to "norway", "FI" to "finland", "EE" to "estonia", "LV" to "latvia",
        "LT" to "lithuania", "IE" to "ireland", "GB" to "united-kingdom", "CY" to "cyprus", "MT" to "malta",
        "IS" to "iceland", "MD" to "moldova", "UA" to "ukraine", "IL" to "israel",
    )

    fun url(countryCode: String): String? =
        SLUGS[countryCode.uppercase()]?.let { "https://feeds.meteoalarm.org/api/v1/warnings/feeds-$it" }

    private fun norm(s: String): String =
        Normalizer.normalize(Translit.toLatin(s).lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')

    private fun levelOf(info: JSONObject): Int {
        val params = info.optJSONArray("parameter")
        if (params != null) for (i in 0 until params.length()) {
            val p = params.optJSONObject(i) ?: continue
            if (p.optString("valueName") == "awareness_level") {
                val v = p.optString("value").lowercase()
                when {
                    "red" in v -> return 4
                    "orange" in v -> return 3
                    "yellow" in v -> return 2
                    "green" in v -> return 1
                }
                v.substringBefore(';').trim().toIntOrNull()?.let { return it.coerceIn(1, 4) }
            }
        }
        return when (info.optString("severity")) {
            "Extreme" -> 4
            "Severe" -> 3
            "Moderate" -> 2
            else -> 1
        }
    }

    private fun pickInfo(infos: JSONArray): JSONObject? {
        var en: JSONObject? = null
        var first: JSONObject? = null
        for (i in 0 until infos.length()) {
            val inf = infos.optJSONObject(i) ?: continue
            if (first == null) first = inf
            val lang = inf.optString("language").lowercase()
            if (lang.startsWith("sr") || lang.startsWith("hr") || lang.startsWith("bs")) return inf
            if (lang.startsWith("en")) en = inf
        }
        return en ?: first
    }

    /** [placeHints] = locality / region names of the followed place, used to flag warnings for its area. */
    fun parse(json: String, placeHints: List<String>): List<Warning> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("warnings") ?: return emptyList()
        val hints = placeHints.filter { it.length >= 3 }.map { norm(it) }
        val out = ArrayList<Warning>()
        for (i in 0 until arr.length()) {
            val w = arr.optJSONObject(i) ?: continue
            val alert = w.optJSONObject("alert") ?: w
            val infos = alert.optJSONArray("info") ?: continue
            val info = pickInfo(infos) ?: continue
            val areas = ArrayList<String>()
            info.optJSONArray("area")?.let { a ->
                for (j in 0 until a.length()) a.optJSONObject(j)?.optString("areaDesc")?.takeIf { it.isNotBlank() }?.let { areas += it }
            }
            val level = levelOf(info)
            if (level <= 1) continue
            val areaNorm = areas.map { norm(it) }
            val matches = hints.any { h -> areaNorm.any { a -> a.contains(h) || h.contains(a) } }
            out += Warning(
                id = w.optString("uuid").ifBlank { alert.optString("identifier") + i },
                event = info.optString("event"),
                headline = info.optString("headline"),
                description = info.optString("description"),
                instruction = info.optString("instruction"),
                level = level,
                areas = areas.map { Translit.toLatin(it) },
                onset = info.optString("onset").ifBlank { info.optString("effective") },
                expires = info.optString("expires"),
                matchesPlace = matches,
            )
        }
        val now = java.time.OffsetDateTime.now()
        return out.filter { w ->
            try { java.time.OffsetDateTime.parse(w.expires).isAfter(now) } catch (e: Exception) { true }
        }.distinctBy { listOf(it.event, it.level, it.areas, it.onset) }
            .sortedWith(compareByDescending<Warning> { it.matchesPlace }.thenByDescending { it.level }.thenBy { it.onset })
    }
}
