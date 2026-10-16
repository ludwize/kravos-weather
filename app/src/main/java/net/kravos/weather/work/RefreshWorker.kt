package net.kravos.weather.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.kravos.weather.App
import net.kravos.weather.R
import net.kravos.weather.data.Bundle
import net.kravos.weather.data.Http
import net.kravos.weather.data.Pollen
import net.kravos.weather.data.Prefs
import net.kravos.weather.location.Locator
import net.kravos.weather.ui.MainActivity
import net.kravos.weather.ui.Screen
import net.kravos.weather.util.Fmt
import net.kravos.weather.util.Insights
import net.kravos.weather.util.Units
import net.kravos.weather.util.Wx
import net.kravos.weather.widget.WidgetUpdater
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Every 30 min: refresh data, update the widget, raise smart notifications. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val prefs = app.prefs
        var place = run {
            val all = listOfNotNull(prefs.gpsPlace?.takeIf { prefs.useGps }) + prefs.savedPlaces
            all.firstOrNull { it.id == prefs.selectedId } ?: all.firstOrNull()
        } ?: return Result.success()

        // Refresh GPS position if the system lets us (works while the app is recently used).
        if (place.isGps && Locator.hasPermission(app)) {
            runCatching { Locator.current(app) }.getOrNull()?.let { loc ->
                place = Locator.placeFor(app, loc.latitude, loc.longitude, prefs.gpsPlace)
                prefs.gpsPlace = place
            }
        }

        val b = try {
            app.repo.load(place, force = true, withLongRange = false)
        } catch (e: Exception) {
            Log.w(Http.TAG, "worker load failed", e)
            return Result.retry()
        }
        WidgetUpdater.update(app)
        runCatching { notify(app, prefs, b) }.onFailure { Log.w(Http.TAG, "notify failed", it) }
        return Result.success()
    }

    private fun notify(ctx: Context, prefs: Prefs, b: Bundle) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val f = b.forecast ?: return
        val u = Units(prefs.tempUnit, prefs.windUnit)
        val now = Insights.localNow(f)
        val date = now.toLocalDate()
        val where = b.place.name

        // official warnings
        if (prefs.notifyWarnings) {
            b.warnings.filter { it.matchesPlace || it.level >= 3 }.forEach { w ->
                val key = "warn_${w.id}"
                if (!prefs.alreadyNotified(key)) {
                    post(ctx, key.hashCode(), App.CH_ALERTS, "${Fmt.warningLabel(w.level)}: ${w.event}",
                        (w.headline.ifBlank { w.description }).ifBlank { w.areas.joinToString(", ") }, Screen.TODAY)
                    prefs.markNotified(key)
                }
            }
        }

        // rain soon (15-minute data)
        if (prefs.notifyRain) {
            val next = f.minutely.filter { it.time.isAfter(now.minusMinutes(10)) }.take(5)
            if (next.isNotEmpty() && next.first().precip < 0.1) {
                val start = next.drop(1).firstOrNull { it.precip >= 0.2 }
                if (start != null) {
                    val key = "rain_${date}_${start.time.hour / 3}"
                    if (!prefs.alreadyNotified(key)) {
                        val amount = f.minutely.filter { !it.time.isBefore(start.time) }.take(8).sumOf { it.precip }
                        val kind = if (Wx.isSnow(f.hours.firstOrNull { it.time.hour == start.time.hour && it.time.toLocalDate() == date }?.code ?: 0)) "Sneg" else "Kiša"
                        post(ctx, 1001, App.CH_RAIN, "$kind počinje oko ${Fmt.hhmm(start.time)}",
                            "$where: očekuje se oko ${Fmt.mm(amount)} u naredna 2 sata. Ponesi kišobran.", Screen.RADAR)
                        prefs.markNotified(key)
                    }
                }
            }
        }

        // thunderstorms / strong gusts in the next 12 h
        if (prefs.notifyStorm) {
            val next = Insights.upcomingHours(f, 12)
            val storm = next.firstOrNull { Wx.isStorm(it.code) && it.precipProb >= 40 }
            val gust = next.firstOrNull { it.windGusts >= 60 }
            val ev = storm ?: gust
            if (ev != null) {
                val key = "storm_${date}_${ev.time.hour / 6}"
                if (!prefs.alreadyNotified(key)) {
                    val title = if (storm != null) "Grmljavina oko ${Fmt.hhmm(storm.time)}" else "Jak vetar oko ${Fmt.hhmm(gust!!.time)}"
                    val body = "$where: udari vetra do ${Fmt.wind(next.maxOf { it.windGusts }, u)}" +
                        (storm?.let { ", verovatnoća padavina ${it.precipProb}%" } ?: "")
                    post(ctx, 1002, App.CH_ALERTS, title, body, Screen.FORECAST)
                    prefs.markNotified(key)
                }
            }
        }

        // morning brief
        if (prefs.notifyMorning && now.hour == prefs.morningHour) {
            val key = "morning_$date"
            if (!prefs.alreadyNotified(key)) {
                val today = f.days.firstOrNull { it.date == date }
                val air = b.air
                val pollen = Insights.topPollen(air)?.takeIf { it.second >= 2 }
                val body = buildString {
                    append(Insights.todaySummary(f, u))
                    if (today != null && today.precipProb >= 30) append(" Šansa za padavine ${today.precipProb}%.")
                    air?.eaqi?.let { append(" Vazduh: ${Fmt.eaqi(it).label.lowercase()} (${it.roundToInt()}).") }
                    pollen?.let { append(" Polen (${it.first.label.lowercase()}): ${Fmt.POLLEN_LEVELS[it.second].label.lowercase()}.") }
                    if (today != null && today.uvMax >= 6) append(" UV ${today.uvMax.roundToInt()} — zaštita od sunca.")
                }
                post(ctx, 1003, App.CH_DAILY, "${Fmt.temp(f.current.temp, u)} · ${Wx.describe(f.current.code)} — $where", body, Screen.TODAY)
                prefs.markNotified(key)
            }
        }

        // frost tonight (evening check)
        if (prefs.notifyFrost && now.hour in 16..21) {
            val night = f.hours.filter { it.time.isAfter(now) && it.time.isBefore(now.plusHours(16)) }
            val min = night.minOfOrNull { it.temp }
            if (min != null && min <= 0.0) {
                val key = "frost_$date"
                if (!prefs.alreadyNotified(key)) {
                    val at = night.minBy { it.temp }.time
                    post(ctx, 1004, App.CH_HEALTH, "Mraz noćas: do ${Fmt.temp(min, u)}",
                        "$where: najhladnije oko ${Fmt.hhmm(at)}. Zaštiti biljke i vodu u bašti.", Screen.FORECAST)
                    prefs.markNotified(key)
                }
            }
        }

        // poor air
        val air = b.air
        if (prefs.notifyAir && air?.eaqi != null && air.eaqi >= 60 && now.hour in 7..21) {
            val key = "air_$date"
            if (!prefs.alreadyNotified(key)) {
                post(ctx, 1005, App.CH_HEALTH, "Kvalitet vazduha: ${Fmt.eaqi(air.eaqi).label.lowercase()} (${air.eaqi.roundToInt()})",
                    "$where: ${Fmt.eaqiAdvice(air.eaqi)}", Screen.AIR)
                prefs.markNotified(key)
            }
        }

        // high pollen
        if (prefs.notifyPollen && air != null && now.hour in 7..20) {
            val watched = Pollen.entries.filter { it.name in prefs.pollenWatch }
            val high = watched.mapNotNull { p ->
                val todayMax = air.hours.filter { it.time.toLocalDate() == date }.mapNotNull { it.pollen[p] }.maxOrNull()
                val lvl = p.level(todayMax)
                if (lvl >= 3) p to lvl else null
            }
            if (high.isNotEmpty()) {
                val key = "pollen_$date"
                if (!prefs.alreadyNotified(key)) {
                    post(ctx, 1006, App.CH_HEALTH, "Visok polen danas",
                        high.joinToString(", ") { "${it.first.label}: ${Fmt.POLLEN_LEVELS[it.second].label.lowercase()}" } + " — $where", Screen.AIR)
                    prefs.markNotified(key)
                }
            }
        }
    }

    private fun post(ctx: Context, id: Int, channel: String, title: String, text: String, screen: Screen) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SCREEN, screen.name)
        }
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (channel == App.CH_ALERTS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            Log.w(Http.TAG, "notification not allowed", e)
        }
    }

    companion object {
        private const val NAME = "refresh"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "refresh_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RefreshWorker>().build(),
            )
        }
    }
}
