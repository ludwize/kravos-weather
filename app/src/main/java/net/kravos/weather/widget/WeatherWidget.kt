package net.kravos.weather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kravos.weather.App
import net.kravos.weather.R
import net.kravos.weather.data.Bundle
import net.kravos.weather.ui.MainActivity
import net.kravos.weather.util.Fmt
import net.kravos.weather.util.Insights
import net.kravos.weather.util.Units
import net.kravos.weather.util.Wx

class WeatherWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(120.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
        private val BIG = DpSize(250.dp, 220.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, BIG))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as App
        val prefs = app.prefs
        val all = listOfNotNull(prefs.gpsPlace?.takeIf { prefs.useGps }) + prefs.savedPlaces
        val place = all.firstOrNull { it.id == prefs.selectedId } ?: all.firstOrNull()
        val bundle = place?.let { app.repo.cached(it) }
        val units = Units(prefs.tempUnit, prefs.windUnit)
        provideContent { Content(bundle, units) }
    }

    @Composable
    private fun Content(b: Bundle?, u: Units) {
        val white = ColorProvider(Color.White)
        val dim = ColorProvider(Color(0xCCFFFFFF))
        val f = b?.forecast
        Column(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (f == null) {
                Text("Kravos Vreme", style = TextStyle(color = white, fontWeight = FontWeight.Bold))
                Text("Otvori aplikaciju da učita prognozu", style = TextStyle(color = dim, fontSize = 12.sp))
                return@Column
            }
            val c = f.current
            val size = LocalSize.current
            val now = Insights.localNow(f)
            val today = f.days.firstOrNull { it.date == now.toLocalDate() }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(b!!.place.name, style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Text(Fmt.temp(c.temp, u), style = TextStyle(color = white, fontSize = 40.sp))
                }
                Image(ImageProvider(Wx.icon(c.code, c.isDay)), Wx.describe(c.code), GlanceModifier.size(if (size.width >= WIDE.width) 56.dp else 44.dp))
            }
            Text(
                Wx.describe(c.code) + (today?.let { "  ↑${Fmt.temp(it.tMax, u)} ↓${Fmt.temp(it.tMin, u)}" } ?: ""),
                style = TextStyle(color = dim, fontSize = 12.sp), maxLines = 1,
            )
            if (size.width >= WIDE.width) {
                Insights.nowcast(f)?.takeIf { !it.startsWith("Bez") }?.let {
                    Text(it, style = TextStyle(color = white, fontSize = 11.sp), maxLines = 1)
                }
            }
            if (size.height >= BIG.height) {
                Spacer(GlanceModifier.height(10.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Insights.upcomingHours(f, 12).filterIndexed { i, _ -> i % 2 == 0 }.take(5).forEach { h ->
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fmt.hour(h.time), style = TextStyle(color = dim, fontSize = 11.sp))
                            Image(ImageProvider(Wx.icon(h.code, h.isDay)), null, GlanceModifier.size(26.dp))
                            Text(Fmt.temp(h.temp, u), style = TextStyle(color = white, fontSize = 12.sp))
                        }
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                f.days.filter { !it.date.isBefore(now.toLocalDate()) }.drop(1).take(3).forEach { d ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.dayShort(d.date.dayOfWeek), GlanceModifier.width(40.dp), style = TextStyle(color = white, fontSize = 12.sp))
                        Image(ImageProvider(Wx.icon(d.code, true)), null, GlanceModifier.size(20.dp))
                        Spacer(GlanceModifier.width(6.dp))
                        Text(if (d.precipProb >= 20) "${d.precipProb}%" else "", GlanceModifier.defaultWeight(), style = TextStyle(color = ColorProvider(Color(0xFF9FD0FF)), fontSize = 11.sp))
                        Text("${Fmt.temp(d.tMin, u)} / ${Fmt.temp(d.tMax, u)}", style = TextStyle(color = white, fontSize = 12.sp))
                    }
                }
            }
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun update(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { WeatherWidget().updateAll(app) } }
    }
}
