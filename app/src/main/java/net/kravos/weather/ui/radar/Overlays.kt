package net.kravos.weather.ui.radar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.kravos.weather.data.ForecastGrid
import net.kravos.weather.data.GridLayer
import net.kravos.weather.data.Http
import net.kravos.weather.data.RadarFrame
import net.kravos.weather.data.RainViewer
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

private fun lonToX(lon: Double, z: Int) = (lon + 180.0) / 360.0 * (1 shl z)
private fun latToY(lat: Double, z: Int): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * (1 shl z)
}
private fun xToLon(x: Double, z: Int) = x / (1 shl z) * 360.0 - 180.0
private fun yToLat(y: Double, z: Int): Double {
    val n = PI - 2.0 * PI * y / (1 shl z)
    return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
}

/**
 * Draws RainViewer radar tiles itself (instead of a tile source), so the max-zoom-7 data still shows,
 * stretched, when the base map is zoomed in further.
 */
class RadarOverlay(private val map: MapView) : Overlay() {
    var host: String = "https://tilecache.rainviewer.com"
    var frame: RadarFrame? = null
        set(v) { field = v; map.postInvalidate() }
    var opacity: Int = 190

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(4)
    private val pending = HashSet<String>()
    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect(0, 0, 256, 256)
    private val dst = RectF()
    private val p1 = Point(); private val p2 = Point()

    private fun zoomFor(mapZoom: Double) = floor(mapZoom).toInt().coerceIn(1, 7)

    private fun visibleTiles(z: Int): List<Triple<Int, Int, Int>> {
        val bb = map.boundingBox
        val n = 1 shl z
        val x0 = floor(lonToX(bb.lonWest, z)).toInt(); val x1 = floor(lonToX(bb.lonEast, z)).toInt()
        val y0 = floor(latToY(bb.latNorth, z)).toInt().coerceIn(0, n - 1); val y1 = floor(latToY(bb.latSouth, z)).toInt().coerceIn(0, n - 1)
        val out = ArrayList<Triple<Int, Int, Int>>()
        for (x in x0..x1) for (y in y0..y1) out += Triple(z, ((x % n) + n) % n, y)
        return out.take(48)
    }

    private fun key(f: RadarFrame, z: Int, x: Int, y: Int) = "${f.path}/$z/$x/$y"

    private fun request(f: RadarFrame, z: Int, x: Int, y: Int) {
        val k = key(f, z, x, y)
        synchronized(pending) { if (!pending.add(k)) return }
        scope.launch {
            gate.withPermit {
                val bytes = Http.bytes(RainViewer.tileUrl(host, f, z, x, y))
                val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bmp != null) cache.put(k, bmp)
                synchronized(pending) { pending.remove(k) }
                if (bmp != null && frame == f) map.postInvalidate()
            }
        }
    }

    /** Warm the cache for animation. */
    fun prefetch(frames: List<RadarFrame>) {
        val z = zoomFor(map.zoomLevelDouble)
        val tiles = visibleTiles(z)
        for (f in frames) for ((tz, x, y) in tiles) if (cache.get(key(f, tz, x, y)) == null) request(f, tz, x, y)
    }

    fun isReady(f: RadarFrame): Boolean {
        val z = zoomFor(map.zoomLevelDouble)
        return visibleTiles(z).all { (tz, x, y) -> cache.get(key(f, tz, x, y)) != null }
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val f = frame ?: return
        val z = zoomFor(osmv.zoomLevelDouble)
        val pj = osmv.projection
        paint.alpha = opacity
        for ((tz, x, y) in visibleTiles(z)) {
            val bmp = cache.get(key(f, tz, x, y))
            if (bmp == null) { request(f, tz, x, y); continue }
            pj.toPixels(GeoPoint(yToLat(y.toDouble(), tz), xToLon(x.toDouble(), tz)), p1)
            pj.toPixels(GeoPoint(yToLat(y + 1.0, tz), xToLon(x + 1.0, tz)), p2)
            dst.set(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat())
            src.set(0, 0, bmp.width, bmp.height)
            c.drawBitmap(bmp, src, dst, paint)
        }
    }

    override fun onDetach(mapView: MapView?) {
        cache.evictAll()
        super.onDetach(mapView)
    }
}

object Scales {
    private fun lerp(a: Int, b: Int, t: Double): Int {
        val tt = t.coerceIn(0.0, 1.0)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * tt).roundToInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).roundToInt(),
        )
    }

    private fun ramp(v: Double, stops: List<Pair<Double, Int>>): Int {
        if (v.isNaN()) return Color.TRANSPARENT
        if (v <= stops.first().first) return stops.first().second
        for (i in 1 until stops.size) {
            val (b, cb) = stops[i]
            val (a, ca) = stops[i - 1]
            if (v <= b) return lerp(ca, cb, (v - a) / (b - a))
        }
        return stops.last().second
    }

    val PRECIP = listOf(
        0.0 to 0x00000000, 0.05 to 0x0000A0FF, 0.2 to 0x9940A0FF.toInt(), 1.0 to 0xB32F6FE0.toInt(),
        2.5 to 0xC041C060.toInt(), 5.0 to 0xD0F2D33C.toInt(), 10.0 to 0xE0F2994A.toInt(), 20.0 to 0xE0E53935.toInt(),
        40.0 to 0xE0C2185B.toInt(),
    )
    val TEMP = listOf(
        -20.0 to 0xB06A1B9A.toInt(), -10.0 to 0xB03949AB.toInt(), 0.0 to 0xB029B6F6.toInt(), 8.0 to 0xB026A69A.toInt(),
        15.0 to 0xB09CCC65.toInt(), 22.0 to 0xB0FFEE58.toInt(), 28.0 to 0xB0FFA726.toInt(), 35.0 to 0xB0E53935.toInt(),
        42.0 to 0xB0880E4F.toInt(),
    )
    val WIND = listOf(
        0.0 to 0x2042A5F5, 10.0 to 0x7042A5F5, 20.0 to 0xA066BB6A.toInt(), 35.0 to 0xB0FFEE58.toInt(),
        50.0 to 0xC0FFA726.toInt(), 70.0 to 0xD0E53935.toInt(), 100.0 to 0xE08E24AA.toInt(),
    )
    val CLOUDS = listOf(0.0 to 0x00FFFFFF, 30.0 to 0x30FFFFFF, 70.0 to 0x90E0E0E0.toInt(), 100.0 to 0xC0D0D0D0.toInt())

    fun color(layer: GridLayer, v: Double): Int = when (layer) {
        GridLayer.PRECIP -> ramp(v, PRECIP)
        GridLayer.TEMP -> ramp(v, TEMP)
        GridLayer.WIND -> ramp(v, WIND)
        GridLayer.CLOUDS -> ramp(v, CLOUDS)
    }

    fun stops(layer: GridLayer) = when (layer) {
        GridLayer.PRECIP -> PRECIP; GridLayer.TEMP -> TEMP; GridLayer.WIND -> WIND; GridLayer.CLOUDS -> CLOUDS
    }
}

/** Colour field + labels/arrows for a [ForecastGrid] at a chosen hour. */
class GridOverlay : Overlay() {
    var grid: ForecastGrid? = null
        set(v) { field = v; bitmapKey = null }
    var layer: GridLayer = GridLayer.PRECIP
        set(v) { field = v; bitmapKey = null }
    var hour: Int = 0
        set(v) { field = v; bitmapKey = null }
    var tempLabel: (Double) -> String = { "${it.roundToInt()}°" }

    private var bitmap: Bitmap? = null
    private var bitmapKey: Triple<ForecastGrid, GridLayer, Int>? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 1f, Color.BLACK); isFakeBoldText = true
    }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 3.5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }
    private val p1 = Point(); private val p2 = Point()
    private val dst = RectF()

    private fun build(g: ForecastGrid): Bitmap {
        val scale = 24
        val w = (g.nx - 1) * scale + 1
        val h = (g.ny - 1) * scale + 1
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val gy = y.toDouble() / scale
            val iy = floor(gy).toInt().coerceAtMost(g.ny - 2); val ty = gy - iy
            for (x in 0 until w) {
                val gx = x.toDouble() / scale
                val ix = floor(gx).toInt().coerceAtMost(g.nx - 2); val tx = gx - ix
                val a = g.value(layer, hour, ix, iy); val b = g.value(layer, hour, ix + 1, iy)
                val c = g.value(layer, hour, ix, iy + 1); val d = g.value(layer, hour, ix + 1, iy + 1)
                val v = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
                px[y * w + x] = Scales.color(layer, v)
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val g = grid ?: return
        if (g.times.isEmpty()) return
        val k = Triple(g, layer, hour)
        if (bitmapKey != k) { bitmap = build(g); bitmapKey = k }
        val bmp = bitmap ?: return
        val pj = osmv.projection
        pj.toPixels(GeoPoint(g.north, g.west), p1)
        pj.toPixels(GeoPoint(g.south, g.east), p2)
        dst.set(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat())
        paint.alpha = 255
        c.drawBitmap(bmp, null, dst, paint)

        val cellW = (dst.width() / (g.nx - 1)).coerceAtLeast(1f)
        val step = if (cellW < 70) 2 else 1
        val pt = Point()
        for (iy in 0 until g.ny step step) for (ix in 0 until g.nx step step) {
            val lat = g.north - (g.north - g.south) * iy / (g.ny - 1)
            val lon = g.west + (g.east - g.west) * ix / (g.nx - 1)
            pj.toPixels(GeoPoint(lat, lon), pt)
            when (layer) {
                GridLayer.TEMP -> {
                    val v = g.value(GridLayer.TEMP, hour, ix, iy)
                    if (!v.isNaN()) c.drawText(tempLabel(v), pt.x.toFloat(), pt.y.toFloat() + 10f, text)
                }
                GridLayer.WIND -> {
                    val sp = g.value(GridLayer.WIND, hour, ix, iy)
                    val dir = g.windDir.getOrNull(hour)?.getOrNull(iy * g.nx + ix) ?: Double.NaN
                    if (!sp.isNaN() && !dir.isNaN()) drawArrow(c, pt.x.toFloat(), pt.y.toFloat(), dir, sp)
                }
                GridLayer.PRECIP -> {
                    val v = g.value(GridLayer.PRECIP, hour, ix, iy)
                    if (!v.isNaN() && v >= 0.5) c.drawText(String.format("%.0f", v), pt.x.toFloat(), pt.y.toFloat() + 10f, text)
                }
                GridLayer.CLOUDS -> Unit
            }
        }
    }

    private val path = Path()
    private fun drawArrow(c: Canvas, x: Float, y: Float, fromDeg: Double, speed: Double) {
        val len = (14 + speed * 0.9).coerceAtMost(60.0).toFloat()
        val toRad = Math.toRadians(fromDeg + 180) // direction the wind blows towards
        val dx = (sin(toRad) * len).toFloat(); val dy = (-cos(toRad) * len).toFloat()
        val sx = x - dx / 2; val sy = y - dy / 2; val ex = x + dx / 2; val ey = y + dy / 2
        c.drawLine(sx, sy, ex, ey, arrow)
        path.reset()
        val head = 9f
        val a1 = toRad + Math.toRadians(150.0); val a2 = toRad - Math.toRadians(150.0)
        path.moveTo(ex, ey); path.lineTo(ex + (sin(a1) * head).toFloat(), ey - (cos(a1) * head).toFloat())
        path.moveTo(ex, ey); path.lineTo(ex + (sin(a2) * head).toFloat(), ey - (cos(a2) * head).toFloat())
        c.drawPath(path, arrow)
    }
}
