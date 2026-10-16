package net.kravos.weather.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kravos.weather.App
import net.kravos.weather.data.Bundle
import net.kravos.weather.data.Http
import net.kravos.weather.data.Place
import net.kravos.weather.data.TempUnit
import net.kravos.weather.data.ThemeMode
import net.kravos.weather.data.WindUnit
import net.kravos.weather.location.Locator
import net.kravos.weather.util.Units
import net.kravos.weather.widget.WidgetUpdater

data class UiState(
    val places: List<Place> = emptyList(),
    val selectedId: String? = null,
    val bundles: Map<String, Bundle> = emptyMap(),
    val loading: Boolean = false,
    val locating: Boolean = false,
    val needsPermission: Boolean = false,
    val units: Units = Units(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val searchResults: List<Place> = emptyList(),
    val searching: Boolean = false,
    val settingsVersion: Int = 0,
) {
    val selected: Place? get() = places.firstOrNull { it.id == selectedId } ?: places.firstOrNull()
    val bundle: Bundle? get() = selected?.let { bundles[it.id] }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val prefs = (app as App).prefs
    private val repo = app.repo

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        reloadSettings()
        rebuildPlaces()
        // instant paint from disk
        val cached = _state.value.places.mapNotNull { p -> repo.cached(p)?.let { p.id to it } }.toMap()
        _state.update { it.copy(bundles = cached) }
    }

    private fun reloadSettings() = _state.update {
        it.copy(
            units = Units(prefs.tempUnit, prefs.windUnit),
            themeMode = prefs.themeMode,
            dynamicColor = prefs.dynamicColor,
            settingsVersion = it.settingsVersion + 1,
        )
    }

    private fun rebuildPlaces() {
        val list = buildList {
            if (prefs.useGps) prefs.gpsPlace?.let { add(it) }
            addAll(prefs.savedPlaces)
        }
        _state.update { s ->
            val sel = prefs.selectedId?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
            s.copy(places = list, selectedId = sel)
        }
    }

    /** Called on start / resume and on pull-to-refresh. */
    fun start(force: Boolean = false) {
        viewModelScope.launch {
            if (prefs.useGps) {
                if (Locator.hasPermission(ctx)) {
                    _state.update { it.copy(locating = true, needsPermission = false) }
                    val loc = Locator.current(ctx)
                    if (loc != null) {
                        val place = Locator.placeFor(ctx, loc.latitude, loc.longitude, prefs.gpsPlace)
                        Log.i(Http.TAG, "located ${place.name} ${place.lat},${place.lon} ${place.countryCode}")
                        prefs.gpsPlace = place
                        if (prefs.selectedId == null) prefs.selectedId = place.id
                        rebuildPlaces()
                    }
                    _state.update { it.copy(locating = false) }
                } else {
                    _state.update { it.copy(needsPermission = prefs.gpsPlace == null) }
                }
            }
            refresh(force)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) start(force = true)
        else _state.update { it.copy(needsPermission = _state.value.places.isEmpty()) }
    }

    fun refresh(force: Boolean = false) {
        val place = _state.value.selected ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val b = repo.load(place, force)
            _state.update { it.copy(loading = false, bundles = it.bundles + (place.id to b)) }
            WidgetUpdater.update(ctx)
            // pre-load the other places in the background for quick swiping
            for (p in _state.value.places) if (p.id != place.id && _state.value.bundles[p.id]?.forecast == null) {
                val other = repo.load(p, false, withLongRange = false)
                _state.update { it.copy(bundles = it.bundles + (p.id to other)) }
            }
        }
    }

    fun select(id: String) {
        prefs.selectedId = id
        _state.update { it.copy(selectedId = id) }
        refresh()
    }

    fun search(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 2) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(searching = true) }
            val r = repo.search(q.trim())
            _state.update { it.copy(searchResults = r, searching = false) }
        }
    }

    fun addPlace(p: Place) {
        val list = prefs.savedPlaces
        if (list.none { it.id == p.id }) prefs.savedPlaces = list + p
        prefs.selectedId = p.id
        rebuildPlaces()
        _state.update { it.copy(searchResults = emptyList()) }
        refresh()
    }

    fun removePlace(p: Place) {
        if (p.isGps) {
            prefs.useGps = false
        } else {
            prefs.savedPlaces = prefs.savedPlaces.filter { it.id != p.id }
        }
        if (prefs.selectedId == p.id) prefs.selectedId = null
        rebuildPlaces()
        refresh()
    }

    fun movePlace(p: Place, up: Boolean) {
        val list = prefs.savedPlaces.toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        prefs.savedPlaces = list
        rebuildPlaces()
    }

    fun enableGps() {
        prefs.useGps = true
        rebuildPlaces()
        start(force = true)
    }

    // ----------------------------------------------------- settings
    fun setTempUnit(u: TempUnit) { prefs.tempUnit = u; reloadSettings(); WidgetUpdater.update(ctx) }
    fun setWindUnit(u: WindUnit) { prefs.windUnit = u; reloadSettings() }
    fun setTheme(m: ThemeMode) { prefs.themeMode = m; reloadSettings() }
    fun setDynamic(b: Boolean) { prefs.dynamicColor = b; reloadSettings() }
    fun bumpSettings() = reloadSettings()
    val settings get() = prefs
}
