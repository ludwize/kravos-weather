package net.kravos.weather.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * Fetches everything for a place and keeps the raw JSON on disk, so the app opens instantly
 * and still shows the last data offline.
 */
class Repository(private val context: Context) {

    private val dir = File(context.filesDir, "data").apply { mkdirs() }

    private fun file(kind: String, place: Place) = File(dir, "${kind}_${place.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    private fun fresh(f: File, maxAgeMs: Long) = f.exists() && System.currentTimeMillis() - f.lastModified() < maxAgeMs

    private suspend fun fetchOrCache(kind: String, place: Place, url: String, maxAgeMs: Long, force: Boolean): Pair<String, Long>? {
        val f = file(kind, place)
        if (!force && fresh(f, maxAgeMs)) return f.readText() to f.lastModified()
        return try {
            val body = Http.get(url)
            f.writeText(body)
            body to f.lastModified()
        } catch (e: Exception) {
            Log.w(Http.TAG, "$kind fetch failed: ${e.message}")
            if (f.exists()) f.readText() to f.lastModified() else null
        }
    }

    private fun <T> safe(what: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(Http.TAG, "parse $what failed", e)
        null
    }

    /** Last data on disk, without touching the network. */
    fun cached(place: Place): Bundle? {
        val fc = file("fc", place).takeIf { it.exists() } ?: return null
        val forecast = safe("forecast") { OpenMeteo.parseForecast(fc.readText(), fc.lastModified()) }
        val air = file("aq", place).takeIf { it.exists() }?.let { f -> safe("air") { OpenMeteo.parseAir(f.readText(), f.lastModified()) } }
        return Bundle(place, forecast, air, cachedLong(place), cachedWarnings(place))
    }

    private fun cachedLong(place: Place): LongRange? {
        val d = file("sd", place); val w = file("sw", place); val m = file("sm", place)
        if (!d.exists() && !w.exists() && !m.exists()) return null
        return LongRange(
            days = if (d.exists()) safe("seasonal daily") { OpenMeteo.parseSeasonalDaily(d.readText()) } ?: emptyList() else emptyList(),
            weeks = if (w.exists()) safe("seasonal weekly") { OpenMeteo.parsePeriods(w.readText(), "weekly") } ?: emptyList() else emptyList(),
            months = if (m.exists()) safe("seasonal monthly") { OpenMeteo.parsePeriods(m.readText(), "monthly") } ?: emptyList() else emptyList(),
            note = null,
            fetchedAt = maxOf(d.lastModified(), w.lastModified(), m.lastModified()),
        )
    }

    private fun hints(place: Place) = listOf(place.name) + place.region.split(',').map { it.trim() }

    private fun cachedWarnings(place: Place): List<Warning> {
        val f = file("wa", place)
        if (!f.exists()) return emptyList()
        return safe("warnings") { Meteoalarm.parse(f.readText(), hints(place)) } ?: emptyList()
    }

    suspend fun load(place: Place, force: Boolean = false, withLongRange: Boolean = true): Bundle = coroutineScope {
        val lat = place.lat; val lon = place.lon
        val fc = async { fetchOrCache("fc", place, OpenMeteo.forecastUrl(lat, lon), 10 * 60_000L, force) }
        val aq = async { fetchOrCache("aq", place, OpenMeteo.airUrl(lat, lon), 30 * 60_000L, force) }
        val wa = async {
            Meteoalarm.url(place.countryCode)?.let { fetchOrCache("wa", place, it, 15 * 60_000L, force) }
        }
        val sd = async { if (withLongRange) fetchOrCache("sd", place, OpenMeteo.seasonalDailyUrl(lat, lon), 6 * 3600_000L, false) else null }
        val sw = async { if (withLongRange) fetchOrCache("sw", place, OpenMeteo.seasonalWeeklyUrl(lat, lon), 6 * 3600_000L, false) else null }
        val sm = async { if (withLongRange) fetchOrCache("sm", place, OpenMeteo.seasonalMonthlyUrl(lat, lon), 12 * 3600_000L, false) else null }

        val forecast = fc.await()?.let { (s, t) -> safe("forecast") { OpenMeteo.parseForecast(s, t) } }
        val air = aq.await()?.let { (s, t) -> safe("air") { OpenMeteo.parseAir(s, t) } }
        val warnings = wa.await()?.let { (s, _) -> safe("warnings") { Meteoalarm.parse(s, hints(place)) } } ?: emptyList()

        val longRange = if (withLongRange) {
            val d = sd.await(); val w = sw.await(); val m = sm.await()
            val days = d?.let { (s, _) -> safe("seasonal daily") { OpenMeteo.parseSeasonalDaily(s) } } ?: emptyList()
            val weeks = w?.let { (s, _) -> safe("seasonal weekly") { OpenMeteo.parsePeriods(s, "weekly") } } ?: emptyList()
            val months = m?.let { (s, _) -> safe("seasonal monthly") { OpenMeteo.parsePeriods(s, "monthly") } } ?: emptyList()
            Log.i(Http.TAG, "long range: days=${days.size} weeks=${weeks.size} months=${months.size}")
            LongRange(
                days, weeks, months,
                note = if (days.isEmpty() && weeks.isEmpty() && months.isEmpty()) "Dugoročna prognoza trenutno nije dostupna." else null,
                fetchedAt = listOfNotNull(d?.second, w?.second, m?.second).maxOrNull() ?: 0L,
            )
        } else cachedLong(place)

        Log.i(Http.TAG, "bundle ${place.name}: fc=${forecast != null} hours=${forecast?.hours?.size} days=${forecast?.days?.size} " +
            "min15=${forecast?.minutely?.size} air=${air != null} eaqi=${air?.eaqi} pollen=${air?.pollen} warnings=${warnings.size}")

        Bundle(
            place, forecast, air, longRange, warnings,
            error = if (forecast == null) "Nema veze sa servisom za prognozu. Proveri internet." else null,
        )
    }

    suspend fun search(q: String): List<Place> = try {
        OpenMeteo.parseGeocode(Http.get(OpenMeteo.geocodeUrl(q)))
    } catch (e: Exception) {
        Log.w(Http.TAG, "geocode failed", e)
        emptyList()
    }
}
