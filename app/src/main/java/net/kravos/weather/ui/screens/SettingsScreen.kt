package net.kravos.weather.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.kravos.weather.BuildConfig
import net.kravos.weather.data.Pollen
import net.kravos.weather.data.Prefs
import net.kravos.weather.data.TempUnit
import net.kravos.weather.data.ThemeMode
import net.kravos.weather.data.WindUnit
import net.kravos.weather.ui.components.Section
import net.kravos.weather.widget.WeatherWidgetReceiver
import net.kravos.weather.work.RefreshWorker

@Composable
fun SettingsScreen(
    prefs: Prefs,
    onTemp: (TempUnit) -> Unit,
    onWind: (WindUnit) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDynamic: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) } // recompose after toggles
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    @Composable
    fun Toggle(title: String, sub: String, value: Boolean, set: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().clickable { set(!value); tick++ }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(value, { set(it); tick++ })
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (tick < 0) Text("")
        Section("Jedinice") {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Temperatura", Modifier.width(110.dp))
                TempUnit.entries.forEach { t ->
                    FilterChip(prefs.tempUnit == t, { onTemp(t); tick++ }, { Text("°${t.name}") }, Modifier.padding(end = 6.dp))
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Vetar", Modifier.width(110.dp))
                WindUnit.entries.forEach { w ->
                    FilterChip(prefs.windUnit == w, { onWind(w); tick++ }, { Text(w.label) }, Modifier.padding(end = 6.dp))
                }
            }
        }
        Section("Izgled") {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tema", Modifier.width(110.dp))
                ThemeMode.entries.forEach { m ->
                    FilterChip(prefs.themeMode == m, { onTheme(m); tick++ }, { Text(m.label) }, Modifier.padding(end = 6.dp))
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Toggle("Material You boje", "Boje aplikacije prate pozadinu telefona", prefs.dynamicColor) { onDynamic(it) }
            }
        }
        Section("Obaveštenja") {
            if (Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(
                    onClick = { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Dozvoli obaveštenja") }
            }
            Toggle("Upozorenja na nepogode", "Zvanična upozorenja RHMZ / Meteoalarm za tvoju oblast", prefs.notifyWarnings) { prefs.notifyWarnings = it }
            Toggle("Kiša uskoro", "Kad padavine počinju u narednih ~60 minuta", prefs.notifyRain) { prefs.notifyRain = it }
            Toggle("Grmljavina i jak vetar", "Oluja ili udari vetra preko 60 km/h u narednih 12 h", prefs.notifyStorm) { prefs.notifyStorm = it }
            Toggle("Jutarnji pregled", "Svako jutro u ${prefs.morningHour}h: temperatura, kiša, vazduh, polen", prefs.notifyMorning) { prefs.notifyMorning = it }
            if (prefs.notifyMorning) Row(Modifier.padding(horizontal = 16.dp)) {
                listOf(6, 7, 8, 9).forEach { h ->
                    FilterChip(prefs.morningHour == h, { prefs.morningHour = h; tick++ }, { Text("${h}h") }, Modifier.padding(end = 6.dp))
                }
            }
            Toggle("Mraz", "Uveče, kad se noću očekuje 0° ili niže", prefs.notifyFrost) { prefs.notifyFrost = it }
            Toggle("Loš vazduh", "Kad EAQI pređe granicu „loš“", prefs.notifyAir) { prefs.notifyAir = it }
            Toggle("Visok polen", "Kad je polen koji pratiš visok", prefs.notifyPollen) { prefs.notifyPollen = it }
            if (prefs.notifyPollen) {
                Text("Pratim:", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Pollen.entries.take(3).forEach { p -> PollenChip(prefs, p) { tick++ } }
                }
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Pollen.entries.drop(3).forEach { p -> PollenChip(prefs, p) { tick++ } }
                }
            }
            OutlinedButton(onClick = { RefreshWorker.runNow(ctx) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Proveri sada")
            }
        }
        Section("Widget") {
            Text(
                "Dugo pritisni početni ekran → Vidžeti → Kravos Vreme. Widget se osvežava na svakih 30 minuta.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
            )
            if (Build.VERSION.SDK_INT >= 26) {
                OutlinedButton(
                    onClick = {
                        val mgr = ctx.getSystemService(AppWidgetManager::class.java)
                        if (mgr?.isRequestPinAppWidgetSupported == true) {
                            mgr.requestPinAppWidget(ComponentName(ctx, WeatherWidgetReceiver::class.java), null, null)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Dodaj widget na početni ekran") }
            }
        }
        Section("O aplikaciji") {
            Text(
                "Kravos Vreme ${BuildConfig.VERSION_NAME}\n" +
                    "Prognoza: Open-Meteo (ECMWF IFS, DWD ICON, Météo-France i drugi modeli, najbolji za lokaciju)\n" +
                    "Dugoročno: ECMWF EC46 i SEAS5 ansambli\n" +
                    "Vazduh i polen: Copernicus CAMS\n" +
                    "Upozorenja: EUMETNET Meteoalarm (RHMZ Srbije i dr.)\n" +
                    "Radar: RainViewer · Mapa: © OpenStreetMap, © CARTO",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PollenChip(prefs: Prefs, p: Pollen, changed: () -> Unit) {
    val on = p.name in prefs.pollenWatch
    FilterChip(on, {
        prefs.pollenWatch = if (on) prefs.pollenWatch - p.name else prefs.pollenWatch + p.name
        changed()
    }, { Text(p.label) }, Modifier.padding(end = 6.dp))
}
