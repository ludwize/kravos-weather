package net.kravos.weather.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kravos.weather.data.Bundle
import net.kravos.weather.data.Day
import net.kravos.weather.data.Forecast
import net.kravos.weather.data.Hour
import net.kravos.weather.data.LongRange
import net.kravos.weather.data.TempUnit
import net.kravos.weather.ui.components.AnomalyBars
import net.kravos.weather.ui.components.CoolBlue
import net.kravos.weather.ui.components.HourlyStrip
import net.kravos.weather.ui.components.LineChart
import net.kravos.weather.ui.components.RainBlue
import net.kravos.weather.ui.components.RangeBar
import net.kravos.weather.ui.components.Section
import net.kravos.weather.ui.components.Series
import net.kravos.weather.ui.components.WarmOrange
import net.kravos.weather.ui.components.WindArrow
import net.kravos.weather.ui.components.WxIcon
import net.kravos.weather.util.Fmt
import net.kravos.weather.util.Insights
import net.kravos.weather.util.Units
import net.kravos.weather.util.Wx
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun ForecastScreen(bundle: Bundle, u: Units, tab: Int, onTab: (Int) -> Unit) {
    val f = bundle.forecast ?: return
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            listOf("Po satu", "16 dana", "Dugoročno").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { onTab(i) }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> HourlyTab(f, u)
            1 -> DailyTab(f, u)
            else -> LongRangeTab(bundle.longRange, f, u)
        }
    }
}

// ============================================================== hourly

private enum class HourChart(val label: String) { TEMP("Temperatura"), RAIN("Padavine"), WIND("Vetar"), HUM("Vlažnost"), PRESS("Pritisak"), UV("UV") }

@Composable
private fun HourlyTab(f: Forecast, u: Units) {
    val now = Insights.localNow(f).withMinute(0).withSecond(0).withNano(0)
    val hours = f.hours.filter { !it.time.isBefore(now) }.take(24 * 7)
    var chart by rememberSaveable { mutableStateOf(HourChart.TEMP) }
    var expanded by remember { mutableStateOf<java.time.LocalDateTime?>(null) }
    val byDay = hours.groupBy { it.time.toLocalDate() }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Section("Narednih 48 sati") { HourlyStrip(hours.take(48), u) }
        }
        item {
            Section("Grafikon — 7 dana") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HourChart.entries.take(3).forEach { c -> FilterChip(chart == c, { chart = c }, { Text(c.label) }) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HourChart.entries.drop(3).forEach { c -> FilterChip(chart == c, { chart = c }, { Text(c.label) }) }
                }
                Spacer(Modifier.height(8.dp))
                HourChartView(chart, hours, u)
            }
        }
        byDay.forEach { (date, list) ->
            item(key = "h_$date") {
                Text(
                    "${Fmt.dayLabel(date, now.toLocalDate())}, ${Fmt.dateShort(date)}",
                    Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                )
            }
            items(list, key = { "hr_${it.time}" }) { h ->
                HourRow(h, u, expanded == h.time) { expanded = if (expanded == h.time) null else h.time }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HourChartView(chart: HourChart, hours: List<Hour>, u: Units) {
    val dayLabels = hours.map { Fmt.dayShort(it.time.dayOfWeek) }
    val conv: (Double) -> Double = { if (u.temp == TempUnit.F) it * 9 / 5 + 32 else it }
    val mod = Modifier.padding(horizontal = 12.dp)
    when (chart) {
        HourChart.TEMP -> LineChart(
            listOf(Series(hours.map { conv(it.temp) }, WarmOrange, fill = true), Series(hours.map { conv(it.feelsLike) }, CoolBlue, dashed = true)),
            dayLabels, mod, fmt = { "${it.roundToInt()}°" }, labelEvery = 24,
        )
        HourChart.RAIN -> LineChart(
            listOf(Series(hours.map { it.precipProb.toDouble() }, RainBlue, fill = true)),
            dayLabels, mod, fmt = { "${it.roundToInt()}%" }, labelEvery = 24,
        )
        HourChart.WIND -> LineChart(
            listOf(Series(hours.map { it.windGusts }, Color(0xFFEB8F5A), dashed = true), Series(hours.map { it.windSpeed }, Color(0xFF66BB6A), fill = true)),
            dayLabels, mod, fmt = { "${it.roundToInt()}" }, labelEvery = 24,
        )
        HourChart.HUM -> LineChart(listOf(Series(hours.map { it.humidity.toDouble() }, Color(0xFF4DD0E1), fill = true)), dayLabels, mod, fmt = { "${it.roundToInt()}%" }, labelEvery = 24)
        HourChart.PRESS -> LineChart(listOf(Series(hours.map { it.pressure }, Color(0xFFBA68C8))), dayLabels, mod, fmt = { "${it.roundToInt()}" }, labelEvery = 24)
        HourChart.UV -> LineChart(listOf(Series(hours.map { it.uv }, Color(0xFFFFC94D), fill = true)), dayLabels, mod, fmt = { "${it.roundToInt()}" }, labelEvery = 24)
    }
    val legend = when (chart) {
        HourChart.TEMP -> "Puna linija: temperatura · isprekidana: osećaj"
        HourChart.RAIN -> "Verovatnoća padavina po satu"
        HourChart.WIND -> "Puna: brzina vetra · isprekidana: udari (km/h)"
        HourChart.HUM -> "Relativna vlažnost"
        HourChart.PRESS -> "Pritisak na nivou mora (hPa)"
        HourChart.UV -> "UV indeks"
    }
    Text(legend, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HourRow(h: Hour, u: Units, open: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.hhmm(h.time), Modifier.width(54.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(Fmt.temp(h.temp, u), Modifier.width(44.dp), style = MaterialTheme.typography.titleMedium)
            WxIcon(h.code, h.isDay, 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(Wx.describe(h.code), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Icon(Icons.Filled.WaterDrop, null, Modifier.size(14.dp), tint = RainBlue)
            Text("${h.precipProb}%", Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium)
            WindArrow(h.windDir, 14.dp)
            Text(" ${Fmt.windValue(h.windSpeed, u)}", Modifier.width(34.dp), style = MaterialTheme.typography.labelMedium)
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)) {
                DetailLine("Osećaj", Fmt.temp(h.feelsLike, u))
                DetailLine("Padavine", "${Fmt.mm(h.precip)} (${h.precipProb}%)")
                DetailLine("Vetar", "${Fmt.dir(h.windDir)} ${Fmt.wind(h.windSpeed, u)}, udari ${Fmt.wind(h.windGusts, u)}")
                DetailLine("Vlažnost", "${h.humidity}% · tačka rose ${Fmt.temp(h.dewPoint, u)}")
                DetailLine("Oblačnost", "${h.cloudCover}%")
                DetailLine("UV indeks", "${h.uv.roundToInt()} (${Fmt.uv(h.uv).label.lowercase()})")
                h.visibility?.let { DetailLine("Vidljivost", "${(it / 1000).roundToInt()} km") }
                DetailLine("Pritisak", "${h.pressure.roundToInt()} hPa")
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ============================================================== daily

@Composable
private fun DailyTab(f: Forecast, u: Units) {
    val today = Insights.localNow(f).toLocalDate()
    val days = f.days.filter { !it.date.isBefore(today) }
    var open by remember { mutableStateOf<LocalDate?>(today) }
    if (days.isEmpty()) return
    val lo = days.minOf { it.tMin }; val hi = days.maxOf { it.tMax }
    val conv: (Double) -> Double = { if (u.temp == TempUnit.F) it * 9 / 5 + 32 else it }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Section("Trend temperature — 16 dana", icon = Icons.Filled.Thermostat) {
                LineChart(
                    listOf(Series(days.map { conv(it.tMax) }, WarmOrange), Series(days.map { conv(it.tMin) }, CoolBlue)),
                    days.map { "${it.date.dayOfMonth}." },
                    Modifier.padding(horizontal = 12.dp), fmt = { "${it.roundToInt()}°" }, labelEvery = 3,
                )
                Spacer(Modifier.height(10.dp))
                LineChart(
                    listOf(Series(days.map { it.precipSum }, RainBlue, fill = true)),
                    emptyList(), Modifier.padding(horizontal = 12.dp), fmt = { "${it.roundToInt()}" }, height = 70.dp,
                )
                Text("Gore: maks/min temperatura · dole: padavine (mm). Dani 11–16 su manje pouzdani.",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(days, key = { "d_${it.date}" }) { d ->
            DayRow(d, f, u, lo, hi, today, open == d.date) { open = if (open == d.date) null else d.date }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DayRow(d: Day, f: Forecast, u: Units, lo: Double, hi: Double, today: LocalDate, isOpen: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(78.dp)) {
                Text(Fmt.dayLabel(d.date, today), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(Fmt.dateShort(d.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WxIcon(d.code, true, 32.dp)
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (d.precipProb >= 10) Text("${d.precipProb}%", style = MaterialTheme.typography.labelMedium, color = RainBlue)
                if (d.precipSum >= 0.1) Text(Fmt.mm(d.precipSum), style = MaterialTheme.typography.labelSmall, color = RainBlue.copy(alpha = 0.8f))
            }
            Text(Fmt.temp(d.tMin, u), Modifier.width(34.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            RangeBar(d.tMin, d.tMax, lo, hi, Modifier.weight(1f).padding(horizontal = 4.dp))
            Text(Fmt.temp(d.tMax, u), Modifier.width(34.dp), fontWeight = FontWeight.Medium)
        }
        AnimatedVisibility(isOpen) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                Text(dayStory(d, f, u), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                val hrs = f.hours.filter { it.time.toLocalDate() == d.date && it.time.hour % 3 == 0 }
                if (hrs.isNotEmpty()) HourlyStrip(hrs, u, cell = 52.dp, showWind = false)
                Spacer(Modifier.height(6.dp))
                DetailLine("Osećaj", "${Fmt.temp(d.feelsMin, u)} do ${Fmt.temp(d.feelsMax, u)}")
                DetailLine("Padavine", "${Fmt.mm(d.precipSum)} · ${d.precipProb}% · ${d.precipHours.roundToInt()} h" + if (d.snowSum > 0) " · sneg ${String.format("%.1f", d.snowSum)} cm" else "")
                DetailLine("Vetar", "${Fmt.dir(d.windDir)} do ${Fmt.wind(d.windMax, u)}, udari ${Fmt.wind(d.gustMax, u)}")
                DetailLine("UV maks", "${d.uvMax.roundToInt()} (${Fmt.uv(d.uvMax).label.lowercase()})")
                DetailLine("Sunce", "${Fmt.hhmm(d.sunrise)} – ${Fmt.hhmm(d.sunset)} · sunčano ${(d.sunshineSec / 3600).roundToInt()} h")
                DetailLine("Mesec", Fmt.moonName(Fmt.moonPhase(d.date)))
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

private fun dayStory(d: Day, f: Forecast, u: Units): String {
    val hrs = f.hours.filter { it.time.toLocalDate() == d.date }
    fun part(from: Int, to: Int) = hrs.filter { it.time.hour in from until to }
    fun desc(l: List<Hour>): String {
        if (l.isEmpty()) return ""
        val wet = l.filter { it.precipProb >= 50 && it.precip >= 0.2 }
        val code = if (wet.size >= 2) wet.maxBy { it.precip }.code else l.groupingBy { it.code }.eachCount().maxBy { it.value }.key
        return Wx.describe(code).lowercase()
    }
    val morning = desc(part(6, 12)); val afternoon = desc(part(12, 18)); val evening = desc(part(18, 24)); val night = desc(part(0, 6))
    return buildString {
        if (night.isNotBlank()) append("Noću $night, ")
        if (morning.isNotBlank()) append("pre podne $morning, ")
        if (afternoon.isNotBlank()) append("popodne $afternoon")
        if (evening.isNotBlank()) append(", uveče $evening")
        append(". Temperatura od ${Fmt.temp(d.tMin, u)} do ${Fmt.temp(d.tMax, u)}")
        if (d.gustMax >= 45) append(", udari vetra do ${Fmt.wind(d.gustMax, u)}")
        append('.')
    }.replaceFirstChar { it.uppercase() }
}

// ============================================================== long range

@Composable
private fun LongRangeTab(lr: LongRange?, f: Forecast, u: Units) {
    val conv: (Double) -> Double = { if (u.temp == TempUnit.F) it * 9 / 5 + 32 else it }
    val dConv: (Double) -> Double = { if (u.temp == TempUnit.F) it * 9 / 5 else it }
    LazyColumn(Modifier.fillMaxSize()) {
        if (lr == null || (lr.days.isEmpty() && lr.weeks.isEmpty() && lr.months.isEmpty())) {
            item {
                Section("Dugoročna prognoza", icon = Icons.Filled.Info) {
                    Text(lr?.note ?: "Učitavam dugoročnu prognozu…", Modifier.padding(horizontal = 16.dp))
                }
            }
            return@LazyColumn
        }
        val today = Insights.localNow(f).toLocalDate()
        val days = lr.days.filter { !it.date.isBefore(today) }
        if (days.isNotEmpty()) item {
            Section("Narednih ${days.size} dana (ansambl ECMWF)", icon = Icons.Filled.CalendarMonth) {
                LineChart(
                    series = listOf(
                        Series(days.map { it.tMax?.mean?.let(conv) }, WarmOrange),
                        Series(days.map { it.tMin?.mean?.let(conv) }, CoolBlue),
                    ),
                    xLabels = days.map { "${it.date.dayOfMonth}.${it.date.monthValue}." },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    band = days.map { it.tMax?.high?.let(conv) } to days.map { it.tMin?.low?.let(conv) },
                    fmt = { "${it.roundToInt()}°" }, height = 170.dp, labelEvery = 7,
                )
                Text(
                    "Linije: prosek maks/min temperature svih članova ansambla. Senka: raspon (10–90%) — što je šira, to je prognoza nesigurnija.",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (days.any { it.precip != null }) {
                    LineChart(
                        listOf(Series(days.map { it.precip?.mean }, RainBlue, fill = true)), emptyList(),
                        Modifier.padding(horizontal = 12.dp), fmt = { String.format("%.1f", it) }, height = 70.dp,
                    )
                    Text("Očekivane dnevne padavine (mm)", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // weekly summary text
                val weeks = days.chunked(7).take(6)
                Spacer(Modifier.height(8.dp))
                weeks.forEachIndexed { i, w ->
                    val mx = w.mapNotNull { it.tMax?.mean }.average()
                    val mn = w.mapNotNull { it.tMin?.mean }.average()
                    val pr = w.mapNotNull { it.precip?.mean }.sum()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${Fmt.dateShort(w.first().date)} – ${Fmt.dateShort(w.last().date)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (!mn.isNaN()) Text(Fmt.temp(mn, u), Modifier.width(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!mx.isNaN()) Text(Fmt.temp(mx, u), Modifier.width(40.dp), fontWeight = FontWeight.Medium)
                        Icon(Icons.Filled.Grain, null, Modifier.size(14.dp), tint = RainBlue)
                        Text(" ${pr.roundToInt()} mm", Modifier.width(60.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    if (i < weeks.size - 1) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
        if (lr.weeks.isNotEmpty()) item {
            val w = lr.weeks.filter { !it.start.isBefore(today.minusDays(6)) }.take(6)
            Section("Nedeljni trend vs. normala", icon = Icons.Filled.Thermostat) {
                if (w.any { it.tempAnomaly != null }) {
                    AnomalyBars(
                        w.map { it.tempAnomaly?.let(dConv) }, w.map { "${it.start.dayOfMonth}.${it.start.monthValue}." },
                        WarmOrange, CoolBlue, { (if (it > 0) "+" else "") + String.format("%.1f°", it) },
                        Modifier.padding(horizontal = 12.dp),
                    )
                    Text("Odstupanje srednje nedeljne temperature od višegodišnjeg proseka za taj period.",
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (w.any { it.precipAnomaly != null }) {
                    AnomalyBars(
                        w.map { it.precipAnomaly }, w.map { "${it.start.dayOfMonth}.${it.start.monthValue}." },
                        RainBlue, Color(0xFFD9A066), { (if (it > 0) "+" else "") + String.format("%.1f", it) },
                        Modifier.padding(horizontal = 12.dp),
                    )
                    Text("Padavine vs. normala (plavo = vlažnije, smeđe = suvlje).",
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (lr.months.isNotEmpty()) item {
            val m = lr.months.filter { !it.start.isBefore(today.withDayOfMonth(1)) }.take(7)
            Section("Sezonska prognoza — po mesecima (ECMWF SEAS5)", icon = Icons.Filled.WbSunny) {
                if (m.any { it.tempAnomaly != null }) {
                    AnomalyBars(
                        m.map { it.tempAnomaly?.let(dConv) }, m.map { Fmt.monthShort(it.start.month) },
                        WarmOrange, CoolBlue, { (if (it > 0) "+" else "") + String.format("%.1f°", it) },
                        Modifier.padding(horizontal = 12.dp),
                    )
                }
                m.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                        Text(Fmt.monthName(p.start.month).replaceFirstChar { it.uppercase() } + " ${p.start.year}", Modifier.weight(1f))
                        p.temp?.let { Text("Ø ${Fmt.temp(it, u)}", Modifier.width(64.dp), fontWeight = FontWeight.Medium) }
                        Text(seasonWord(p.tempAnomaly, p.precipAnomaly), Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Sezonska prognoza pokazuje trend (toplije/hladnije, vlažnije/suvlje od proseka), ne konkretno vreme za određeni dan.",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun seasonWord(t: Double?, p: Double?): String {
    val a = when {
        t == null -> ""
        t > 1.0 -> "znatno toplije"
        t > 0.3 -> "toplije"
        t < -1.0 -> "znatno hladnije"
        t < -0.3 -> "hladnije"
        else -> "oko proseka"
    }
    val b = when {
        p == null -> ""
        p > 0.3 -> "vlažnije"
        p < -0.3 -> "suvlje"
        else -> ""
    }
    return listOf(a, b).filter { it.isNotBlank() }.joinToString(", ")
}
