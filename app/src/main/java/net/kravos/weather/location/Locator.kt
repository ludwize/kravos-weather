package net.kravos.weather.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.kravos.weather.data.Http
import net.kravos.weather.data.Place
import net.kravos.weather.data.Translit
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

object Locator {

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 15 * 60_000L) return last

        for (provider in providers.filter { it != LocationManager.PASSIVE_PROVIDER }) {
            val loc = fix(ctx, lm, provider)
            if (loc != null) return loc
        }
        return last
    }

    @SuppressLint("MissingPermission")
    private suspend fun fix(ctx: Context, lm: LocationManager, provider: String): Location? {
        return withTimeoutOrNull(9_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    lm.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(ctx)) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
        }
    }

    /** Builds the "current location" Place, naming it via the system geocoder or BigDataCloud as fallback. */
    suspend fun placeFor(ctx: Context, lat: Double, lon: Double, previous: Place?): Place {
        // Don't re-geocode when we barely moved.
        if (previous != null && distanceKm(previous.lat, previous.lon, lat, lon) < 1.0 && previous.name.isNotBlank()) {
            return previous.copy(lat = lat, lon = lon)
        }
        val named = withContext(Dispatchers.IO) { systemGeocode(ctx, lat, lon) ?: webGeocode(lat, lon) }
        return Place(
            id = "gps",
            name = named?.first ?: String.format(Locale.US, "%.2f, %.2f", lat, lon),
            region = named?.second ?: "",
            countryCode = named?.third ?: previous?.countryCode ?: "",
            lat = lat, lon = lon, isGps = true,
        )
    }

    @Suppress("DEPRECATION")
    private fun systemGeocode(ctx: Context, lat: Double, lon: Double): Triple<String, String, String>? = try {
        if (!Geocoder.isPresent()) null else {
            val g = Geocoder(ctx, Locale.forLanguageTag("sr-Latn-RS"))
            val a = g.getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.let {
                val name = it.subLocality ?: it.locality ?: it.subAdminArea ?: it.adminArea ?: return@let null
                val region = listOfNotNull(it.locality?.takeIf { l -> l != name }, it.adminArea, it.countryName).distinct().joinToString(", ")
                Triple(Translit.toLatin(name), Translit.toLatin(region), it.countryCode ?: "")
            }
        }
    } catch (e: Exception) {
        Log.w(Http.TAG, "system geocoder failed: ${e.message}")
        null
    }

    private fun webGeocode(lat: Double, lon: Double): Triple<String, String, String>? = try {
        val url = String.format(
            Locale.US,
            "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.5f&longitude=%.5f&localityLanguage=sr",
            lat, lon,
        )
        val body = Http.client().newCall(okhttp3.Request.Builder().url(url).build()).execute().use { it.body?.string() }
        body?.let {
            val o = JSONObject(it)
            val name = o.optString("locality").ifBlank { o.optString("city") }.ifBlank { o.optString("principalSubdivision") }
            if (name.isBlank()) null else Triple(
                Translit.toLatin(name),
                Translit.toLatin(listOf(o.optString("city"), o.optString("principalSubdivision"), o.optString("countryName"))
                    .filter { s -> s.isNotBlank() && s != name }.distinct().joinToString(", ")),
                o.optString("countryCode"),
            )
        }
    } catch (e: Exception) {
        Log.w(Http.TAG, "web geocoder failed: ${e.message}")
        null
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, r)
        return r[0] / 1000.0
    }
}
