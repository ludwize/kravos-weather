package net.kravos.weather.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import net.kravos.weather.data.Warning
import net.kravos.weather.ui.components.Dot
import net.kravos.weather.ui.screens.AirScreen
import net.kravos.weather.ui.screens.ForecastScreen
import net.kravos.weather.ui.screens.PlacesScreen
import net.kravos.weather.ui.screens.RadarScreen
import net.kravos.weather.ui.screens.SettingsScreen
import net.kravos.weather.ui.screens.TodayScreen
import net.kravos.weather.ui.theme.WeatherTheme
import net.kravos.weather.util.Fmt

enum class Screen(val title: String) { TODAY("Danas"), FORECAST("Prognoza"), AIR("Vazduh"), RADAR("Radar"), PLACES("Lokacije"), SETTINGS("Podešavanja") }

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startScreen = intent?.getStringExtra(EXTRA_SCREEN)?.let { runCatching { Screen.valueOf(it) }.getOrNull() }
        setContent {
            val s by vm.state.collectAsState()
            WeatherTheme(s.themeMode, s.dynamicColor) {
                App(vm, s, startScreen)
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: MainViewModel, s: UiState, startScreen: Screen?) {
    var screen by rememberSaveable { mutableStateOf(startScreen ?: Screen.TODAY) }
    var forecastTab by rememberSaveable { mutableIntStateOf(0) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var askedPermission by rememberSaveable { mutableStateOf(false) }

    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        vm.onPermissionResult(res.values.any { it })
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (!askedPermission && !net.kravos.weather.location.Locator.hasPermission(vm.getApplication())) {
            askedPermission = true
            locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else if (Build.VERSION.SDK_INT >= 33) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.start() }

    BackHandler(enabled = screen != Screen.TODAY) {
        screen = if (screen == Screen.SETTINGS || screen == Screen.PLACES) Screen.TODAY else Screen.TODAY
    }

    val place = s.selected
    val bundle = s.bundle
    val mainTabs = listOf(Screen.TODAY, Screen.FORECAST, Screen.AIR, Screen.RADAR)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    if (screen == Screen.PLACES || screen == Screen.SETTINGS) IconButton(onClick = { screen = Screen.TODAY }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazad")
                    }
                },
                title = {
                    if (screen == Screen.PLACES || screen == Screen.SETTINGS) Text(screen.title)
                    else Row(
                        Modifier.clickable { screen = Screen.PLACES }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (place?.isGps == true) { Icon(Icons.Filled.MyLocation, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(place?.name ?: "Kravos Vreme", fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (s.loading || s.locating) Text(if (s.locating) "Tražim lokaciju…" else "Osvežavam…", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ArrowDropDown, "Lokacije")
                    }
                },
                actions = {
                    if (screen != Screen.SETTINGS) IconButton(onClick = { screen = Screen.SETTINGS }) { Icon(Icons.Filled.Settings, "Podešavanja") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (screen in mainTabs) NavigationBar {
                val icons = listOf(Icons.Filled.WbSunny, Icons.Filled.CalendarMonth, Icons.Filled.Air, Icons.Filled.Radar)
                mainTabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = screen == t, onClick = { screen = t }, icon = { Icon(icons[i], null) }, label = { Text(t.title) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                screen == Screen.SETTINGS -> SettingsScreen(vm.settings, vm::setTempUnit, vm::setWindUnit, vm::setTheme, vm::setDynamic)
                screen == Screen.PLACES -> PlacesScreen(
                    s.places, s.selectedId, s.bundles, s.searchResults, s.searching, vm.settings.useGps, s.units,
                    onSearch = vm::search,
                    onAdd = { vm.addPlace(it); screen = Screen.TODAY },
                    onSelect = { vm.select(it.id); screen = Screen.TODAY },
                    onRemove = vm::removePlace,
                    onMove = vm::movePlace,
                    onEnableGps = {
                        vm.enableGps()
                        locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                )
                place == null -> Onboarding(
                    locating = s.locating,
                    onAllow = { locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    onManual = { screen = Screen.PLACES },
                )
                screen == Screen.RADAR -> RadarScreen(place!!, s.units)
                bundle?.forecast == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (bundle?.error != null && !s.loading) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(bundle!!.error!!, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                        Button(onClick = { vm.refresh(true) }) { Text("Pokušaj ponovo") }
                    } else CircularProgressIndicator()
                }
                else -> PullToRefreshBox(isRefreshing = s.loading, onRefresh = { vm.start(force = true) }, modifier = Modifier.fillMaxSize()) {
                    val bundle = bundle!!
                    when (screen) {
                        Screen.FORECAST -> ForecastScreen(bundle, s.units, forecastTab) { forecastTab = it }
                        Screen.AIR -> AirScreen(bundle)
                        else -> TodayScreen(
                            bundle, s.units,
                            onOpenForecast = { forecastTab = it; screen = Screen.FORECAST },
                            onOpenAir = { screen = Screen.AIR },
                            onOpenRadar = { screen = Screen.RADAR },
                            onOpenWarning = { sheet = true },
                        )
                    }
                }
            }
        }
    }

    if (sheet && bundle != null) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            WarningsSheet(bundle.warnings)
        }
    }
}

@Composable
private fun WarningsSheet(list: List<Warning>) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text("Upozorenja", style = MaterialTheme.typography.titleLarge)
        Text("Izvor: Meteoalarm / nacionalna hidrometeorološka služba", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) Text("Trenutno nema aktivnih upozorenja.")
        list.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Fmt.warningColor(w.level), 12.dp)
                Spacer(Modifier.width(8.dp))
                Text("${Fmt.warningLabel(w.level)} — ${w.event}", fontWeight = FontWeight.SemiBold)
            }
            if (w.matchesPlace) Text("Važi za tvoju oblast", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text("Područje: ${w.areas.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Važi: ${fmtTs(w.onset)} – ${fmtTs(w.expires)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (w.headline.isNotBlank()) Text(w.headline, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (w.description.isNotBlank()) Text(w.description, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
            if (w.instruction.isNotBlank()) Text(w.instruction, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun fmtTs(s: String): String = try {
    val t = java.time.OffsetDateTime.parse(s).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime()
    "${Fmt.dayShort(t.dayOfWeek)} ${t.dayOfMonth}.${t.monthValue}. ${Fmt.hhmm(t)}"
} catch (e: Exception) {
    s
}

@Composable
private fun Onboarding(locating: Boolean, onAllow: () -> Unit, onManual: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.WbSunny, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Kravos Vreme", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Za prognozu na mestu gde se nalaziš treba dozvola za lokaciju. Možeš i ručno da dodaš mesto.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (locating) CircularProgressIndicator() else {
            Button(onClick = onAllow) { Text("Dozvoli lokaciju") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onManual) { Text("Dodaj mesto ručno") }
        }
    }
}
