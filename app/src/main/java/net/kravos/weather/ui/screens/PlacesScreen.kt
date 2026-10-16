package net.kravos.weather.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kravos.weather.data.Bundle
import net.kravos.weather.data.Place
import net.kravos.weather.ui.components.WxIcon
import net.kravos.weather.util.Fmt
import net.kravos.weather.util.Units

@Composable
fun PlacesScreen(
    places: List<Place>,
    selectedId: String?,
    bundles: Map<String, Bundle>,
    results: List<Place>,
    searching: Boolean,
    gpsEnabled: Boolean,
    u: Units,
    onSearch: (String) -> Unit,
    onAdd: (Place) -> Unit,
    onSelect: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onMove: (Place, Boolean) -> Unit,
    onEnableGps: () -> Unit,
) {
    var q by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q, onValueChange = { q = it; onSearch(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Traži grad ili mesto…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (q.length >= 2) {
                if (results.isEmpty() && !searching) item {
                    Text("Nema rezultata", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(results, key = { "r_" + it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onAdd(p); q = ""; onSearch("") }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Medium)
                            Text(p.region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.Add, "Dodaj")
                    }
                }
                return@LazyColumn
            }
            if (!gpsEnabled) item {
                TextButton(onClick = onEnableGps, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Filled.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("Uključi trenutnu lokaciju (GPS)")
                }
            }
            items(places, key = { it.id }) { p ->
                val b = bundles[p.id]?.forecast
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable { onSelect(p) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (p.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (p.isGps) { Icon(Icons.Filled.MyLocation, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (p.isGps) "Trenutna lokacija" else p.region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (b != null) {
                            WxIcon(b.current.code, b.current.isDay, 34.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(Fmt.temp(b.current.temp, u), style = MaterialTheme.typography.headlineSmall)
                        }
                        if (!p.isGps) {
                            Column {
                                IconButton(onClick = { onMove(p, true) }, Modifier.size(28.dp)) { Icon(Icons.Filled.KeyboardArrowUp, "Gore") }
                                IconButton(onClick = { onMove(p, false) }, Modifier.size(28.dp)) { Icon(Icons.Filled.KeyboardArrowDown, "Dole") }
                            }
                        }
                        IconButton(onClick = { onRemove(p) }) { Icon(Icons.Filled.Delete, "Ukloni") }
                    }
                }
            }
        }
    }
}
