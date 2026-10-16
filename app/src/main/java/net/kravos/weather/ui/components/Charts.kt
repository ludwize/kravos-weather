package net.kravos.weather.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.weather.data.Hour
import net.kravos.weather.data.Minutely
import net.kravos.weather.util.Fmt
import net.kravos.weather.util.Units
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val RainBlue = Color(0xFF5AB0FF)
val WarmOrange = Color(0xFFFFB86B)
val CoolBlue = Color(0xFF7FB8FF)

private fun DrawScope.smoothPath(pts: List<Offset>): Path {
    val p = Path()
    if (pts.isEmpty()) return p
    p.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]; val b = pts[i]
        val mx = (a.x + b.x) / 2
        p.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
    }
    return p
}

private fun linePath(pts: List<Offset>): Path = Path().apply {
    pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
}

private fun DrawScope.label(text: String, x: Float, y: Float, color: Color, sizeSp: Float = 12f, bold: Boolean = false) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb(); textSize = sizeSp * density * fontScale; textAlign = Paint.Align.CENTER
        isFakeBoldText = bold
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

/** Scrollable hourly strip: time, icon, temperature curve, precipitation chance, wind. */
@Composable
fun HourlyStrip(hours: List<Hour>, u: Units, cell: Dp = 58.dp, showWind: Boolean = true) {
    if (hours.isEmpty()) return
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            Row {
                hours.forEachIndexed { i, h ->
                    Column(Modifier.width(cell), horizontalAlignment = Alignment.CenterHorizontally) {
                        val label = if (i == 0) "Sada" else if (h.time.hour == 0) Fmt.dayShort(h.time.dayOfWeek) else Fmt.hour(h.time)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (h.time.hour == 0 && i != 0) line else muted,
                            fontWeight = if (h.time.hour == 0) FontWeight.Bold else FontWeight.Normal)
                        Spacer(Modifier.height(6.dp))
                        WxIcon(h.code, h.isDay, 30.dp)
                    }
                }
            }
            val temps = hours.map { it.temp }
            val tMin = temps.min(); val tMax = temps.max()
            Canvas(Modifier.width(cell * hours.size).height(70.dp)) {
                val cw = size.width / hours.size
                val top = 22.dp.toPx(); val bottom = size.height - 6.dp.toPx()
                val range = max(tMax - tMin, 3.0)
                val pts = temps.mapIndexed { i, t ->
                    Offset(cw * i + cw / 2, (bottom - (t - tMin) / range * (bottom - top)).toFloat())
                }
                val path = smoothPath(pts)
                val fill = Path().apply { addPath(path); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), Color.Transparent)))
                drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                pts.forEachIndexed { i, p ->
                    drawCircle(line, 3.dp.toPx(), p)
                    label(Fmt.temp(temps[i], u), p.x, p.y - 8.dp.toPx(), onSurface, 13f, bold = true)
                }
            }
            Row {
                hours.forEach { h ->
                    Column(Modifier.width(cell), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (h.precipProb >= 5) "${h.precipProb}%" else " ",
                            style = MaterialTheme.typography.labelSmall, color = RainBlue,
                        )
                        if (h.precip >= 0.1) Text(Fmt.mm(h.precip).removeSuffix(" mm"), style = MaterialTheme.typography.labelSmall, color = RainBlue.copy(alpha = 0.8f))
                        else Text(" ", style = MaterialTheme.typography.labelSmall)
                        if (showWind) {
                            Spacer(Modifier.height(4.dp))
                            WindArrow(h.windDir, 14.dp)
                            Text(Fmt.windValue(h.windSpeed, u), style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    }
                }
            }
        }
    }
}

/** Precipitation for the next hours in 15-minute steps (the "Pro" minute-cast). */
@Composable
fun MinuteCastChart(data: List<Minutely>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val maxV = max(data.maxOf { it.precip }, 1.0)
            val bw = size.width / data.size
            for (i in 1..3) {
                val y = size.height * i / 4
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            }
            data.forEachIndexed { i, m ->
                val h = (m.precip / maxV * size.height).toFloat()
                if (m.precip > 0) drawRoundRect(
                    RainBlue.copy(alpha = 0.55f + 0.45f * min(1.0, m.precip / 2).toFloat()),
                    topLeft = Offset(i * bw + 2, size.height - max(h, 3f)),
                    size = Size(bw - 4, max(h, 3f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val step = max(1, data.size / 4)
            for (i in data.indices step step) {
                Text(Fmt.hhmm(data[i].time), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}

data class Series(val values: List<Double?>, val color: Color, val fill: Boolean = false, val dashed: Boolean = false)

/**
 * Generic line chart with optional band (low..high), labels along the X axis.
 * [fmt] formats Y values for the min/max legend.
 */
@Composable
fun LineChart(
    series: List<Series>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    band: Pair<List<Double?>, List<Double?>>? = null,
    bandColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    fmt: (Double) -> String = { it.roundToInt().toString() },
    height: Dp = 150.dp,
    zeroLine: Boolean = false,
    labelEvery: Int = 0,
    floorZero: Boolean = false,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val all = series.flatMap { it.values }.filterNotNull() + (band?.let { (it.first + it.second).filterNotNull() } ?: emptyList())
    if (all.isEmpty()) return
    var lo = all.min(); var hi = all.max()
    if (zeroLine) { lo = min(lo, 0.0); hi = max(hi, 0.0) }
    if (hi - lo < 1e-6) { hi += 1; lo -= 1 }
    val pad = (hi - lo) * 0.08
    lo -= pad; hi += pad
    if (floorZero) lo = maxOf(lo, 0.0)
    val n = series.maxOf { it.values.size }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val left = 34.dp.toPx(); val w = size.width - left; val h = size.height
            fun x(i: Int) = left + if (n <= 1) w / 2 else w * i / (n - 1)
            fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()
            for (k in 0..3) {
                val v = lo + (hi - lo) * k / 3
                drawLine(grid, Offset(left, y(v)), Offset(size.width, y(v)), 1f)
                label(fmt(v), left / 2, y(v) + 4.dp.toPx(), muted, 10f)
            }
            if (zeroLine && lo < 0 && hi > 0) drawLine(muted, Offset(left, y(0.0)), Offset(size.width, y(0.0)), 1.5f)
            band?.let { (low, high) ->
                val path = Path()
                var started = false
                for (i in high.indices) { val v = high[i] ?: continue; if (!started) { path.moveTo(x(i), y(v)); started = true } else path.lineTo(x(i), y(v)) }
                for (i in low.indices.reversed()) { val v = low[i] ?: continue; path.lineTo(x(i), y(v)) }
                path.close()
                drawPath(path, bandColor)
            }
            series.forEach { s ->
                val pts = s.values.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }
                if (pts.size < 2) return@forEach
                val path = if (floorZero) linePath(pts) else smoothPath(pts)
                if (s.fill) {
                    val f = Path().apply { addPath(path); lineTo(pts.last().x, h); lineTo(pts.first().x, h); close() }
                    drawPath(f, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.3f), Color.Transparent)))
                }
                drawPath(
                    path, s.color,
                    style = Stroke(
                        2.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = if (s.dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                    ),
                )
            }
        }
        if (xLabels.isNotEmpty()) {
            val every = if (labelEvery > 0) labelEvery else max(1, xLabels.size / 6)
            Row(Modifier.fillMaxWidth().padding(start = 34.dp)) {
                for (i in xLabels.indices step every) {
                    Text(xLabels[i], Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                }
            }
        }
    }
}

/** Bars centred on zero (anomalies): warm/wet colours above, cool/dry below. */
@Composable
fun AnomalyBars(
    values: List<Double?>,
    labels: List<String>,
    posColor: Color,
    negColor: Color,
    fmt: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val vs = values.filterNotNull()
    if (vs.isEmpty()) return
    val m = max(vs.maxOf { kotlin.math.abs(it) }, 0.5)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val bw = size.width / values.size
            val mid = size.height / 2
            drawLine(muted, Offset(0f, mid), Offset(size.width, mid), 1.5f)
            values.forEachIndexed { i, v ->
                v ?: return@forEachIndexed
                val h = (kotlin.math.abs(v) / m * (mid - 16.dp.toPx())).toFloat()
                val c = if (v >= 0) posColor else negColor
                val top = if (v >= 0) mid - h else mid
                drawRoundRect(c, Offset(i * bw + bw * 0.18f, top), Size(bw * 0.64f, max(h, 2f)), androidx.compose.ui.geometry.CornerRadius(6f))
                val ty = if (v >= 0) top - 4.dp.toPx() else top + h + 12.dp.toPx()
                label(fmt(v), i * bw + bw / 2, ty, onSurface, 11f, bold = true)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.Center, maxLines = 1, fontSize = 10.sp) }
        }
    }
}

/** Day row temperature range bar (min..max of the whole period). */
@Composable
fun RangeBar(lo: Double, hi: Double, minAll: Double, maxAll: Double, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.height(6.dp)) {
        val r = max(maxAll - minAll, 1.0)
        val a = ((lo - minAll) / r * size.width).toFloat()
        val b = ((hi - minAll) / r * size.width).toFloat()
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        drawRoundRect(
            Brush.horizontalGradient(listOf(CoolBlue, WarmOrange), startX = 0f, endX = size.width),
            topLeft = Offset(a, 0f), size = Size(max(b - a, size.height), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
        )
    }
}
