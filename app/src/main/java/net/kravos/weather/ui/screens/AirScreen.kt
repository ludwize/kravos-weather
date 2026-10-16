package net.kravos.weather.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kravos.weather.data.AirQuality
import net.kravos.weather.data.Bundle
import net.kravos.weather.data.Pollen
import net.kravos.weather.ui.components.Dot
import net.kravos.weather.ui.components.Gauge
import net.kravos.weather.ui.components.LineChart
import net.kravos.weather.ui.components.ScaleBar
import net.kravos.weather.ui.components.Section
import net.kravos.weather.ui.components.Series
import net.kravos.weather.util.Fmt
import net.kravos.weather.util.Insights
import java.time.LocalDate
import kotlin.math.roundToInt

private val AQI_COLORS = listOf(Color(0xFF50C878), Color(0xFFA3D65C), Color(0xFFF2C94C), Color(0xFFF2994A), Color(0xFFEB5757), Color(0xFF9B51E0))

/** Pollutant, EU index band upper limits (µg/m³) used for the bar scale. */
private data class Pollutant(val name: String, val formula: String, val value: Double?, val bands: List<Double>, val about: String)

@Composable
fun AirScreen(bundle: Bundle) {
    val air = bundle.air
    val f = bundle.forecast
    LazyColumn(Modifier.fillMaxSize()) {
        if (air == null) {
            item { Section("Kvalitet vazduha") { Text("Nema podataka o kvalitetu vazduha za ovu lokaciju.", Modifier.padding(horizontal = 16.dp)) } }
            return@LazyColumn
        }
        item { AqiCard(air) }
        item { PollutantsCard(air) }
        item { AqiTrend(air) }
        item { PollenCard(air) }
        item { PollenTrend(air, f?.let { Insights.localNow(it).toLocalDate() } ?: LocalDate.now()) }
        item {
            val uv = air.uv ?: f?.current?.uv ?: 0.0
            Section("UV zračenje", icon = Icons.Filled.WbSunny) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${uv.roundToInt()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(10.dp))
                    Text(Fmt.uv(uv).label, color = Fmt.uv(uv).color, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                ScaleBar((uv / 12).toFloat(), listOf(Color(0xFF50C878), Color(0xFFF2C94C), Color(0xFFF2994A), Color(0xFFEB5757), Color(0xFF9B51E0)), Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        uv < 3 -> "Zaštita nije potrebna."
                        uv < 6 -> "Naočare za sunce, krema SPF 30 ako si dugo napolju."
                        uv < 8 -> "Hlad između 11 i 16h, krema, kapa i naočare."
                        else -> "Izbegavaj sunce u podne, obavezna zaštita."
                    },
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Text(
                "Izvor: Copernicus Atmosphere Monitoring Service (CAMS) preko Open-Meteo. Evropski indeks kvaliteta vazduha (EAQI). " +
                    "Polen je modelska prognoza (CAMS), dostupna samo za Evropu i tokom sezone; nivoi su orijentacioni.",
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AqiCard(air: AirQuality) {
    val band = Fmt.eaqi(air.eaqi)
    Section("Kvalitet vazduha (EAQI)", icon = Icons.Filled.Air) {
        Gauge(((air.eaqi ?: 0.0) / 120).toFloat(), band.color, Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${air.eaqi?.roundToInt() ?: "—"}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                Text(band.label, color = band.color, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            Fmt.eaqiAdvice(air.eaqi), Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
        )
        air.usAqi?.let {
            Spacer(Modifier.height(4.dp))
            Text("US AQI: ${it.roundToInt()}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PollutantsCard(air: AirQuality) {
    val list = listOf(
        Pollutant("Fine čestice", "PM2.5", air.pm25, listOf(10.0, 20.0, 25.0, 50.0, 75.0, 800.0), "Najopasnije — prodiru duboko u pluća"),
        Pollutant("Čestice", "PM10", air.pm10, listOf(20.0, 40.0, 50.0, 100.0, 150.0, 1200.0), "Prašina, ložišta, saobraćaj"),
        Pollutant("Azot-dioksid", "NO₂", air.no2, listOf(40.0, 90.0, 120.0, 230.0, 340.0, 1000.0), "Pretežno od saobraćaja"),
        Pollutant("Ozon", "O₃", air.o3, listOf(50.0, 100.0, 130.0, 240.0, 380.0, 800.0), "Raste za sunčanih i toplih dana"),
        Pollutant("Sumpor-dioksid", "SO₂", air.so2, listOf(100.0, 200.0, 350.0, 500.0, 750.0, 1250.0), "Termoelektrane i industrija"),
    )
    Section("Zagađujuće materije", icon = Icons.Filled.Info) {
        list.forEach { p ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.formula, Modifier.width(60.dp), fontWeight = FontWeight.SemiBold)
                    Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(p.value?.let { "${it.roundToInt()} µg/m³" } ?: "—", fontWeight = FontWeight.Medium)
                }
                val v = p.value ?: 0.0
                val idx = p.bands.indexOfFirst { v < it }.let { if (it < 0) 5 else it }
                val frac = (idx + (v - (p.bands.getOrNull(idx - 1) ?: 0.0)) / (p.bands[idx] - (p.bands.getOrNull(idx - 1) ?: 0.0))) / 6
                Spacer(Modifier.height(4.dp))
                ScaleBar(frac.toFloat(), AQI_COLORS)
                Text(p.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        air.co?.let {
            Text("CO (ugljen-monoksid): ${it.roundToInt()} µg/m³" + (air.dust?.let { d -> " · Saharska prašina: ${d.roundToInt()} µg/m³" } ?: ""),
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AqiTrend(air: AirQuality) {
    val now = java.time.LocalDateTime.now().withMinute(0)
    val h = air.hours.filter { !it.time.isBefore(now.minusHours(1)) }
    if (h.size < 4) return
    Section("Prognoza kvaliteta vazduha", icon = Icons.Filled.ShowChart) {
        LineChart(
            listOf(
                Series(h.map { it.eaqi }, Color(0xFFF2994A), fill = true),
                Series(h.map { it.pm25 }, Color(0xFF9B9BFF), dashed = true),
            ),
            h.map { Fmt.dayShort(it.time.dayOfWeek) }, Modifier.padding(horizontal = 12.dp),
            fmt = { "${it.roundToInt()}" }, labelEvery = 24,
        )
        Text("Puna linija: EAQI · isprekidana: PM2.5 (µg/m³)", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PollenCard(air: AirQuality) {
    Section("Polen danas", icon = Icons.Filled.LocalFlorist) {
        val inSeason = air.pollen.values.any { it != null }
        if (!inSeason) {
            Text("Nema podataka o polenu (van sezone ili lokacija van Evrope).", Modifier.padding(horizontal = 16.dp))
            return@Section
        }
        Pollen.entries.forEachIndexed { i, p ->
            val v = air.pollen[p]
            // today's peak is more useful than this hour's value
            val todayMax = air.hours.filter { it.time.toLocalDate() == java.time.LocalDate.now() }.mapNotNull { it.pollen[p] }.maxOrNull()
            val shown = listOfNotNull(v, todayMax).maxOrNull()
            val lvl = p.level(shown)
            val band = Fmt.POLLEN_LEVELS[lvl]
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(band.color, 12.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.label, fontWeight = FontWeight.Medium)
                    Text(p.kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(band.label, color = if (lvl >= 3) band.color else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(shown?.let { "${it.roundToInt()} zrna/m³" } ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (i < Pollen.entries.size - 1) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun PollenLegend() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Fmt.POLLEN_LEVELS.forEach { b ->
            Dot(b.color); Spacer(Modifier.width(4.dp))
            Text(b.label, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
private fun PollenTrend(air: AirQuality, today: LocalDate) {
    val days = (0..3).map { today.plusDays(it.toLong()) }
    val any = air.hours.any { h -> h.pollen.values.any { (it ?: 0.0) >= 1.0 } }
    if (!any) return
    Section("Polen — prognoza 4 dana", icon = Icons.Filled.LocalFlorist) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("", Modifier.width(90.dp))
            days.forEach { d -> Text(Fmt.dayLabel(d, today), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
        }
        Spacer(Modifier.height(6.dp))
        Pollen.entries.forEach { p ->
            val maxes = days.map { d -> air.hours.filter { it.time.toLocalDate() == d }.mapNotNull { it.pollen[p] }.maxOrNull() }
            if (maxes.all { (it ?: 0.0) < 1.0 }) return@forEach
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.label, Modifier.width(90.dp), style = MaterialTheme.typography.bodyMedium)
                maxes.forEach { v ->
                    val band = Fmt.POLLEN_LEVELS[p.level(v)]
                    Row(Modifier.weight(1f), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Dot(band.color, 14.dp)
                    }
                }
            }
        }
        PollenLegend()
    }
}
