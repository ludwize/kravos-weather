package net.kravos.weather.ui.screens

import android.graphics.Color as AColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kravos.weather.data.ForecastGrid
import net.kravos.weather.data.GridLayer
import net.kravos.weather.data.Http
import net.kravos.weather.data.Place
import net.kravos.weather.data.RadarIndex
import net.kravos.weather.data.RainViewer
import net.kravos.weather.ui.radar.GridOverlay
import net.kravos.weather.ui.radar.RadarOverlay
import net.kravos.weather.ui.radar.Scales
import net.kravos.weather.util.Fmt
import net.kravos.weather.util.Units
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class MapMode(val label: String) { RADAR("Radar"), PRECIP("Padavine — prognoza"), TEMP("Temperatura"), WIND("Vetar"), CLOUDS("Oblačnost") }

private fun MapMode.grid(): GridLayer? = when (this) {
    MapMode.RADAR -> null
    MapMode.PRECIP -> GridLayer.PRECIP
    MapMode.TEMP -> GridLayer.TEMP
    MapMode.WIND -> GridLayer.WIND
    MapMode.CLOUDS -> GridLayer.CLOUDS
}

@Composable
fun RadarScreen(place: Place, u: Units) {
    val ctx = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminanceLow()
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(MapMode.RADAR) }
    var radar by remember { mutableStateOf<RadarIndex?>(null) }
    var frameIdx by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf<ForecastGrid?>(null) }
    var gridHour by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var gridJob by remember { mutableStateOf<Job?>(null) }

    val mapView = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            if (dark) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERTED_COLORS)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 12.0
            isTilesScaledToDpi = true
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(place.lat, place.lon))
            setHorizontalMapRepetitionEnabled(false)
            overlays.add(CopyrightOverlay(ctx))
        }
    }
    val radarOverlay = remember { RadarOverlay(mapView) }
    val gridOverlay = remember { GridOverlay() }
    val marker = remember {
        Marker(mapView).apply {
            icon = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(AColor.parseColor("#4FA3F7")); setStroke(6, AColor.WHITE); setSize(44, 44)
            }
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
        }
    }

    DisposableEffect(Unit) {
        mapView.overlays.add(0, gridOverlay)
        mapView.overlays.add(1, radarOverlay)
        mapView.overlays.add(marker)
        onDispose { mapView.onDetach() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(place.lat, place.lon) {
        marker.position = GeoPoint(place.lat, place.lon)
        mapView.controller.setCenter(GeoPoint(place.lat, place.lon))
        mapView.invalidate()
    }
    gridOverlay.tempLabel = { Fmt.temp(it, u) }

    fun loadGrid() {
        val layer = mode.grid() ?: return
        gridJob?.cancel()
        gridJob = scope.launch {
            delay(400)
            loading = true
            error = null
            try {
                val bb = mapView.boundingBox
                val padLat = (bb.latNorth - bb.latSouth) * 0.1
                val padLon = (bb.lonEast - bb.lonWest) * 0.1
                val g = ForecastGrid.fetch(
                    (bb.latNorth + padLat).coerceAtMost(84.0), (bb.latSouth - padLat).coerceAtLeast(-84.0),
                    (bb.lonWest - padLon).coerceAtLeast(-179.9), (bb.lonEast + padLon).coerceAtMost(179.9),
                )
                grid = g
                gridOverlay.grid = g
                gridOverlay.layer = layer
                // start at the current hour
                val nowH = g.times.indexOfFirst { !it.isBefore(java.time.LocalDateTime.now().withMinute(0).minusMinutes(1)) }
                if (gridHour == 0 && nowH > 0) gridHour = nowH
                gridOverlay.hour = gridHour.coerceIn(0, (g.times.size - 1).coerceAtLeast(0))
                mapView.invalidate()
            } catch (e: Exception) {
                android.util.Log.w(Http.TAG, "grid failed", e)
                error = "Ne mogu da učitam slojeve prognoze."
            }
            loading = false
        }
    }

    // radar index, refreshed every 5 minutes while visible
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val idx = RainViewer.index()
                radar = idx
                radarOverlay.host = idx.host
                if (!playing) frameIdx = (idx.frames.size - 1).coerceAtLeast(0)
                radarOverlay.frame = idx.frames.getOrNull(frameIdx)
                error = null
            } catch (e: Exception) {
                error = "Radar trenutno nije dostupan."
            }
            delay(5 * 60_000L)
        }
    }

    LaunchedEffect(mode) {
        val layer = mode.grid()
        if (layer == null) {
            gridOverlay.grid = null
            radarOverlay.frame = radar?.frames?.getOrNull(frameIdx)
            mapView.invalidate()
        } else {
            playing = false
            radarOverlay.frame = null
            if (grid == null) loadGrid() else {
                gridOverlay.grid = grid
                gridOverlay.layer = layer
                gridOverlay.hour = gridHour
                mapView.invalidate()
            }
        }
    }

    // reload grid after the user moves the map a lot
    DisposableEffect(mapView) {
        var lastCenter = mapView.mapCenter.let { GeoPoint(it.latitude, it.longitude) }
        var lastZoom = mapView.zoomLevelDouble
        val listener = object : MapListener {
            fun check() {
                val c = mapView.mapCenter
                val bb = mapView.boundingBox
                val moved = abs(c.latitude - lastCenter.latitude) > (bb.latNorth - bb.latSouth) * 0.35 ||
                    abs(c.longitude - lastCenter.longitude) > (bb.lonEast - bb.lonWest) * 0.35
                val zoomed = abs(mapView.zoomLevelDouble - lastZoom) >= 1.0
                if ((moved || zoomed) && mode.grid() != null) {
                    lastCenter = GeoPoint(c.latitude, c.longitude); lastZoom = mapView.zoomLevelDouble
                    loadGrid()
                }
            }
            override fun onScroll(event: ScrollEvent?): Boolean { check(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { check(); return false }
        }
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }

    // animation
    LaunchedEffect(playing, mode) {
        if (!playing) return@LaunchedEffect
        if (mode == MapMode.RADAR) {
            val frames = radar?.frames ?: return@LaunchedEffect
            radarOverlay.prefetch(frames)
            while (playing) {
                frameIdx = (frameIdx + 1) % frames.size
                radarOverlay.frame = frames[frameIdx]
                delay(if (frameIdx == frames.size - 1) 1600 else 650)
            }
        } else {
            val g = grid ?: return@LaunchedEffect
            while (playing) {
                gridHour = (gridHour + 1) % g.times.size
                gridOverlay.hour = gridHour
                mapView.invalidate()
                delay(700)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ mapView }, Modifier.fillMaxSize())

        // layer chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m, onClick = { mode = m }, label = { Text(m.label) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)),
                )
            }
        }

        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp))

        SmallFloatingActionButton(
            onClick = { mapView.controller.animateTo(GeoPoint(place.lat, place.lon), 8.0, 600L) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
        ) { Icon(Icons.Filled.MyLocation, "Moja lokacija") }

        // timeline
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (mode == MapMode.RADAR) {
                    val frames = radar?.frames.orEmpty()
                    val f = frames.getOrNull(frameIdx)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { playing = !playing }, enabled = frames.isNotEmpty()) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pauza" else "Pusti")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                f?.let {
                                    val t = Instant.ofEpochSecond(it.time).atZone(ZoneId.systemDefault()).toLocalDateTime()
                                    val mins = (System.currentTimeMillis() / 1000 - it.time) / 60
                                    "Radar ${Fmt.hhmm(t)}" + if (mins > 12) " (pre $mins min)" else " (najnovije)"
                                } ?: "Učitavam radar…",
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (frames.size > 1) Slider(
                                value = frameIdx.toFloat(), onValueChange = {
                                    playing = false
                                    frameIdx = it.roundToInt()
                                    radarOverlay.frame = frames.getOrNull(frameIdx)
                                },
                                valueRange = 0f..(frames.size - 1).toFloat(), steps = (frames.size - 2).coerceAtLeast(0),
                            )
                        }
                    }
                    Legend(Scales.PRECIP.drop(1), "slabo", "jako")
                    Text("Radar: poslednja 2 sata. Za budućnost izaberi „Padavine — prognoza“. Radar © RainViewer · Mapa © OpenStreetMap",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val g = grid
                    val layer = mode.grid()!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { playing = !playing }, enabled = g != null) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pauza" else "Pusti")
                        }
                        Column(Modifier.weight(1f)) {
                            val t = g?.times?.getOrNull(gridHour)
                            Text(
                                t?.let { "${Fmt.dayShort(it.dayOfWeek)} ${Fmt.hhmm(it)}" } ?: "Učitavam…",
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (g != null && g.times.size > 1) Slider(
                                value = gridHour.toFloat(), onValueChange = {
                                    playing = false
                                    gridHour = it.roundToInt()
                                    gridOverlay.hour = gridHour
                                    mapView.invalidate()
                                },
                                valueRange = 0f..(g.times.size - 1).toFloat(),
                            )
                        }
                    }
                    when (layer) {
                        GridLayer.PRECIP -> Legend(Scales.PRECIP.drop(1), "0,1 mm/h", "40 mm/h")
                        GridLayer.TEMP -> Legend(Scales.TEMP, Fmt.temp(-20.0, u), Fmt.temp(42.0, u))
                        GridLayer.WIND -> Legend(Scales.WIND, "0", "100 km/h")
                        GridLayer.CLOUDS -> Legend(Scales.CLOUDS, "vedro", "oblačno")
                    }
                    Text("Modelska prognoza (Open-Meteo) za narednih 48 h, osvežava se kad pomeriš mapu.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun Color.luminanceLow(): Boolean = (0.299 * red + 0.587 * green + 0.114 * blue) < 0.4

@Composable
private fun Legend(stops: List<Pair<Double, Int>>, left: String, right: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val w = size.width / stops.size
            stops.forEachIndexed { i, (_, c) ->
                drawRect(Color(c).copy(alpha = 1f), Offset(i * w, 0f), Size(w + 1, size.height))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(left, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text(right, style = MaterialTheme.typography.labelSmall)
        }
    }
}
