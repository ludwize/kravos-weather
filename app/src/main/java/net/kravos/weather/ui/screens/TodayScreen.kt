package net.kravos.weather.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kravos.weather.data.AirQuality
import net.kravos.weather.data.Bundle
import net.kravos.weather.data.Forecast
import net.kravos.weather.data.Warning
import net.kravos.weather.ui.components.Dot
import net.kravos.weather.ui.components.HourlyStrip
import net.kravos.weather.ui.components.MinuteCastChart
import net.kravos.weather.ui.components.RangeBar
import net.kravos.weather.ui.components.ScaleBar
import net.kravos.weather.ui.components.Section
import net.kravos.weather.ui.components.Tile
import net.kravos.weather.ui.components.TileRow
import net.kravos.weather.ui.components.WindArrow
import net.kravos.weather.ui.components.WxIcon
import net.kravos.weather.util.Fmt
import net.kravos.weather.util.Insights
import net.kravos.weather.util.Units
import net.kravos.weather.util.Wx
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TodayScreen(
    bundle: Bundle,
    u: Units,
    onOpenForecast: (tab: Int) -> Unit,
    onOpenAir: () -> Unit,
    onOpenRadar: () -> Unit,
    onOpenWarning: (Warning) -> Unit,
) {
    val f = bundle.forecast ?: return
    val now = Insights.localNow(f)
    val today = f.days.firstOrNull { it.date == now.toLocalDate() } ?: f.days.firstOrNull()
    LazyColumn(Modifier.fillMaxWidth()) {
        // ---------------------------------------------------------------- warnings
        val warnings = bundle.warnings.filter { it.matchesPlace }.ifEmpty { bundle.warnings.filter { it.level >= 3 } }
        items(warnings.size) { i -> WarningBanner(warnings[i], onOpenWarning) }
        if (bundle.warnings.isNotEmpty() && warnings.size < bundle.warnings.size) item {
            TextButton(onClick = { onOpenWarning(bundle.warnings.first()) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Sva upozorenja u zemlji (${bundle.warnings.size})")
            }
        }

        item { Hero(f, u) }

        // ---------------------------------------------------------------- minute cast
        item {
            val next = f.minutely.filter { it.time.isAfter(now.minusMinutes(15)) }.take(12)
            val cast = Insights.nowcast(f)
            if (next.any { it.precip >= 0.1 }) {
                Section("Padavine narednih 3 sata", icon = Icons.Filled.Umbrella, trailing = {
                    Text("Radar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { onOpenRadar() })
                }) {
                    if (cast != null) Text(cast, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    MinuteCastChart(next, Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        // ---------------------------------------------------------------- summary
        item {
            Section("Danas", icon = Icons.Filled.Schedule) {
                Text(Insights.todaySummary(f, u), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
                Insights.tonight(f, u)?.let {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DarkMode, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ---------------------------------------------------------------- hourly
        item {
            Section("Po satu", icon = Icons.Filled.Schedule, trailing = {
                Text("Detaljno", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { onOpenForecast(0) })
            }) {
                HourlyStrip(Insights.upcomingHours(f, 48), u)
            }
        }

        // ---------------------------------------------------------------- daily
        item {
            val days = f.days.filter { !it.date.isBefore(now.toLocalDate()) }.take(10)
            Section("10 dana", icon = Icons.Filled.WbSunny, trailing = {
                Text("Svih 16 dana", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { onOpenForecast(1) })
            }) {
                if (days.isNotEmpty()) {
                    val lo = days.minOf { it.tMin }; val hi = days.maxOf { it.tMax }
                    days.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenForecast(1) }.padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Fmt.dayLabel(d.date, now.toLocalDate()), Modifier.width(52.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            WxIcon(d.code, true, 28.dp)
                            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                                if (d.precipProb >= 20) Text("${d.precipProb}%", style = MaterialTheme.typography.labelMedium, color = net.kravos.weather.ui.components.RainBlue)
                            }
                            Text(Fmt.temp(d.tMin, u), Modifier.width(38.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            RangeBar(d.tMin, d.tMax, lo, hi, Modifier.weight(1f).padding(horizontal = 6.dp))
                            Text(Fmt.temp(d.tMax, u), Modifier.width(38.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------- air & pollen
        item { AirPollenCard(bundle.air, onOpenAir) }

        // ---------------------------------------------------------------- details
        item {
            val c = f.current
            TileRow { m ->
                Tile("Osećaj", Fmt.temp(c.feelsLike, u), feelsNote(c.temp, c.feelsLike, c.windSpeed, c.humidity), m, Icons.Filled.Thermostat)
                Tile("Vlažnost", "${c.humidity}%", "Tačka rose ${Fmt.temp(c.dewPoint, u)}", m, Icons.Filled.WaterDrop)
            }
        }
        item {
            val c = f.current
            TileRow { m ->
                Tile("Vetar", Fmt.wind(c.windSpeed, u), "${Fmt.windDescription(c.windSpeed)}, ${Fmt.windName(c.windDir)}\nUdari do ${Fmt.wind(c.windGusts, u)}", m, Icons.Filled.Air,
                    extra = { Spacer(Modifier.height(6.dp)); Row(verticalAlignment = Alignment.CenterVertically) { WindArrow(c.windDir, 22.dp, MaterialTheme.colorScheme.primary); Text(" ${Fmt.dir(c.windDir)}") } })
                val trend = pressureTrend(f)
                Tile("Pritisak", "${c.pressure.roundToInt()} hPa", trend, m, Icons.Filled.Compress)
            }
        }
        item {
            val c = f.current
            val uv = c.uv ?: today?.uvMax ?: 0.0
            TileRow { m ->
                Tile("UV indeks", "${uv.roundToInt()}", "${Fmt.uv(uv).label}. Danas maks ${today?.uvMax?.roundToInt() ?: "-"}", m, Icons.Filled.WbSunny, accent = Fmt.uv(uv).color)
                Tile("Vidljivost", c.visibility?.let { if (it >= 10000) "${(it / 1000).roundToInt()} km" else String.format("%.1f km", it / 1000).replace('.', ',') } ?: "—",
                    c.visibility?.let { visNote(it) }, m, Icons.Filled.Visibility)
            }
        }
        item {
            val c = f.current
            TileRow { m ->
                Tile("Oblačnost", "${c.cloudCover}%", Wx.describe(c.code), m, Icons.Filled.Cloud)
                Tile("Padavine", Fmt.mm(today?.precipSum ?: 0.0), today?.let { "danas · ${it.precipProb}% šansa · ${it.precipHours.roundToInt()} h" }, m, Icons.Filled.Grain)
            }
        }

        // ---------------------------------------------------------------- sun & moon
        item { if (today != null) SunMoon(f, today, now) }

        // ---------------------------------------------------------------- activities
        item {
            val acts = Insights.activities(f, bundle.air)
            if (acts.isNotEmpty()) Section("Aktivnosti narednih sati", icon = Icons.AutoMirrored.Filled.DirectionsRun) {
                acts.forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(130.dp)) {
                            Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(a.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { a.score / 10f },
                            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = scoreColor(a.score),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            drawStopIndicator = {},
                        )
                        Text("${a.score}/10", Modifier.width(48.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        item { Footer(f) }
    }
}

fun scoreColor(s: Int): Color = when {
    s >= 8 -> Color(0xFF50C878); s >= 6 -> Color(0xFFA3D65C); s >= 4 -> Color(0xFFF2C94C); else -> Color(0xFFEB5757)
}

private fun feelsNote(t: Double, feels: Double, wind: Double, rh: Int): String = when {
    feels < t - 2 && wind > 15 -> "Vetar pojačava osećaj hladnoće"
    feels > t + 2 && rh > 60 -> "Vlažnost pojačava osećaj vrućine"
    feels < t - 1 -> "Hladnije nego što pokazuje termometar"
    feels > t + 1 -> "Toplije nego što pokazuje termometar"
    else -> "Slično stvarnoj temperaturi"
}

private fun visNote(m: Double) = when {
    m < 1000 -> "Magla, vozi oprezno"
    m < 4000 -> "Smanjena vidljivost"
    m < 10000 -> "Umerena vidljivost"
    else -> "Odlična vidljivost"
}

private fun pressureTrend(f: Forecast): String {
    val now = Insights.localNow(f).withMinute(0).withSecond(0).withNano(0)
    val a = f.hours.firstOrNull { it.time == now.minusHours(3) }?.pressure
    val b = f.hours.firstOrNull { it.time == now }?.pressure ?: f.current.pressure
    val c = f.hours.firstOrNull { it.time == now.plusHours(3) }?.pressure
    val d = when {
        c != null -> c - b
        a != null -> b - a
        else -> 0.0
    }
    return when {
        d > 1.5 -> "Raste ↑ — stabilizacija vremena"
        d < -1.5 -> "Opada ↓ — moguća promena vremena"
        else -> "Stabilan →"
    }
}

@Composable
private fun WarningBanner(w: Warning, onClick: (Warning) -> Unit) {
    val c = Fmt.warningColor(w.level)
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onClick(w) },
        shape = RoundedCornerShape(18.dp), color = c.copy(alpha = 0.18f),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = c)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${Fmt.warningLabel(w.level)}: ${w.event}", fontWeight = FontWeight.SemiBold)
                Text(
                    w.areas.take(3).joinToString(", ") + if (w.areas.size > 3) "…" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}

@Composable
private fun Hero(f: Forecast, u: Units) {
    val c = f.current
    val (top, bottom) = Wx.gradient(c.code, c.isDay)
    val now = Insights.localNow(f)
    val today = f.days.firstOrNull { it.date == now.toLocalDate() }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(top), Color(bottom)))),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "${Fmt.dayLong(now.dayOfWeek)}, ${now.dayOfMonth}. ${Fmt.monthName(now.month)} · ${Fmt.hhmm(now)}",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.temp(c.temp, u), style = MaterialTheme.typography.displayLarge, color = Color.White)
                Spacer(Modifier.weight(1f))
                WxIcon(c.code, c.isDay, 104.dp)
            }
            Text(Wx.describe(c.code), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("Osećaj ${Fmt.temp(c.feelsLike, u)}")
                    if (today != null) append("  ·  ↑${Fmt.temp(today.tMax, u)}  ↓${Fmt.temp(today.tMin, u)}")
                },
                color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge,
            )
            Insights.vsYesterday(f, u)?.let {
                Text(it, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
            }
            Insights.nowcast(f)?.let {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Umbrella, null, Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AirPollenCard(air: AirQuality?, onOpen: () -> Unit) {
    if (air == null) return
    val band = Fmt.eaqi(air.eaqi)
    val top = Insights.topPollen(air)
    TileRow { m ->
        Surface(m.clickable { onOpen() }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Air, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(5.dp))
                    Text("KVALITET VAZDUHA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text("${air.eaqi?.roundToInt() ?: "—"}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(band.label, color = band.color, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ScaleBar(((air.eaqi ?: 0.0) / 120).toFloat(), listOf(Color(0xFF50C878), Color(0xFFA3D65C), Color(0xFFF2C94C), Color(0xFFF2994A), Color(0xFFEB5757), Color(0xFF9B51E0)))
            }
        }
        Surface(m.clickable { onOpen() }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFlorist, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(5.dp))
                    Text("POLEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                if (top == null) {
                    Text("Nizak", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                    Text("Van sezone ili zanemarljiv", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val lvl = Fmt.POLLEN_LEVELS[top.second]
                    Text(lvl.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(lvl.color); Spacer(Modifier.width(6.dp)); Text(top.first.label, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ScaleBar(((top?.second ?: 0) / 4f), Fmt.POLLEN_LEVELS.drop(1).map { it.color })
            }
        }
    }
}

@Composable
private fun SunMoon(f: Forecast, d: net.kravos.weather.data.Day, now: java.time.LocalDateTime) {
    Section("Sunce i Mesec", icon = Icons.Filled.WbSunny) {
        val sr = d.sunrise; val ss = d.sunset
        val arc = MaterialTheme.colorScheme.outlineVariant
        val sun = Color(0xFFFFC94D)
        if (sr != null && ss != null) {
            val total = java.time.Duration.between(sr, ss).seconds.toDouble()
            val done = java.time.Duration.between(sr, now).seconds.toDouble()
            val frac = (done / total).coerceIn(0.0, 1.0)
            Canvas(Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 32.dp)) {
                val r = size.width / 2
                val cx = size.width / 2; val cy = size.height + r * 0.15f
                val rr = minOf(r, size.height * 1.05f)
                drawArc(arc, 180f, 180f, false, Offset(cx - rr, cy - rr), Size(rr * 2, rr * 2), style = Stroke(3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                drawArc(sun.copy(alpha = 0.7f), 180f, (180 * frac).toFloat(), false, Offset(cx - rr, cy - rr), Size(rr * 2, rr * 2), style = Stroke(4f, cap = StrokeCap.Round))
                val ang = PI + PI * frac
                val p = Offset((cx + rr * cos(ang)).toFloat(), (cy + rr * sin(ang)).toFloat())
                if (now.isAfter(sr) && now.isBefore(ss)) drawCircle(sun, 10.dp.toPx(), p)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Izlazak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(Fmt.hhmm(sr), style = MaterialTheme.typography.titleMedium) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Dužina dana", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.duration(d.daylightSec), style = MaterialTheme.typography.titleMedium)
                val tomorrow = f.days.firstOrNull { it.date == d.date.plusDays(1) }
                if (tomorrow != null) {
                    val diff = ((tomorrow.daylightSec - d.daylightSec) / 60).roundToInt()
                    Text("sutra ${if (diff >= 0) "+" else ""}$diff min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) { Text("Zalazak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(Fmt.hhmm(ss), style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(14.dp))
        val phase = Fmt.moonPhase(d.date)
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            MoonGlyph(phase)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(Fmt.moonName(phase), style = MaterialTheme.typography.titleMedium)
                Text("Osvetljenost ${Fmt.moonIllumination(phase)}% · Sunčanih sati danas ${(d.sunshineSec / 3600).roundToInt()} h",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MoonGlyph(phase: Double, sizeDp: Int = 44) {
    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.size(sizeDp.dp)) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(dark, r, c)
        // lit part: draw the terminator as an ellipse half
        val lit = Color(0xFFF5F0DC)
        val waxing = phase < 0.5
        val k = cos(2 * PI * phase).toFloat() // 1 new, -1 full
        val path = androidx.compose.ui.graphics.Path()
        val steps = 48
        for (i in 0..steps) {
            val a = -PI / 2 + PI * i / steps
            val x = c.x + (if (waxing) 1 else -1) * r * cos(a).toFloat()
            val y = c.y + r * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val a = -PI / 2 + PI * i / steps
            val x = c.x + (if (waxing) 1 else -1) * r * k * cos(a).toFloat()
            val y = c.y + r * sin(a).toFloat()
            path.lineTo(x, y)
        }
        path.close()
        drawPath(path, lit)
    }
}

@Composable
private fun Footer(f: Forecast) {
    Text(
        "Ažurirano ${Fmt.ago(f.fetchedAt)} · Podaci: Open-Meteo (ECMWF, ICON, DWD…), Copernicus CAMS, Meteoalarm, RainViewer",
        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
}
