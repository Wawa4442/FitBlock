package com.fitblock.app

import android.content.Context
import android.graphics.*
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.*

/** Native OSM raster map; only visible tiles are fetched, never whole areas. */
class RouteMapView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tiles = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pending = mutableSetOf<String>()
    private val failures = mutableMapOf<String, Long>()
    private var route = emptyList<RoutePoint>()
    private var centerX = .5
    private var centerY = .5
    private var zoom = 16
    private var following = true
    private var touchX = 0f
    private var touchY = 0f
    private var moved = false
    private val tileSize get() = context.dp(256).toDouble()
    private val cacheDir = File(context.cacheDir, "map-tiles").apply { mkdirs() }

    init {
        contentDescription = "Mapa de la ruta. Arrastra para explorar; usa los botones para acercar o encuadrar."
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun update(points: List<RoutePoint>) {
        if (points == route) return
        route = points
        if (following) fit()
        invalidate()
    }

    fun fit() {
        following = true
        if (route.isEmpty() || width == 0) return
        val anchor = x(route.first().longitude)
        val xs = route.map { anchor + wrap(x(it.longitude) - anchor) }
        val ys = route.map { y(it.latitude) }
        centerX = (xs.min() + xs.max()) / 2
        centerY = (ys.min() + ys.max()) / 2
        val usableWidth = (width - context.dp(64)).coerceAtLeast(1)
        val usableHeight = (height - context.dp(64)).coerceAtLeast(1)
        zoom = (18 downTo 3).firstOrNull {
            val world = tileSize * (1 shl it)
            (xs.max() - xs.min()) * world < usableWidth && (ys.max() - ys.min()) * world < usableHeight
        } ?: 3
        if (route.size == 1) zoom = 16
        invalidate()
    }

    fun zoomBy(delta: Int) { following = false; zoom = (zoom + delta).coerceIn(3, 18); invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { if (following) fit() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (route.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x; touchY = event.y; moved = false
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val world = tileSize * (1 shl zoom)
                centerX -= (event.x - touchX) / world
                centerY = (centerY - (event.y - touchY) / world).coerceIn(0.0, 1.0)
                touchX = event.x; touchY = event.y; following = false; moved = true; invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                if (!moved) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(C.card)
        if (route.isEmpty()) {
            paint.color = C.muted; paint.textSize = context.dp(14).toFloat(); paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Tu ruta aparecerá al recibir señal GPS", width / 2f, height / 2f, paint)
            return
        }
        val n = 1 shl zoom
        val world = tileSize * n
        val left = centerX * world - width / 2
        val top = centerY * world - height / 2
        var visibleTiles = 0
        for (tx in floor(left / tileSize).toInt()..floor((left + width) / tileSize).toInt()) {
            for (ty in floor(top / tileSize).toInt()..floor((top + height) / tileSize).toInt()) {
                if (ty !in 0 until n) continue
                val key = "$zoom-${Math.floorMod(tx, n)}-$ty"
                val box = RectF((tx * tileSize - left).toFloat(), (ty * tileSize - top).toFloat(),
                    ((tx + 1) * tileSize - left).toFloat(), ((ty + 1) * tileSize - top).toFloat())
                val bitmap = tiles.get(key)
                if (bitmap != null) { canvas.drawBitmap(bitmap, null, box, null); visibleTiles++ }
                else {
                    paint.color = C.line; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
                    canvas.drawRect(box, paint); paint.style = Paint.Style.FILL
                    requestTile(key, zoom, Math.floorMod(tx, n), ty)
                }
            }
        }
        fun px(p: RoutePoint) = (width / 2 + wrap(x(p.longitude) - centerX) * world).toFloat()
        fun py(p: RoutePoint) = (height / 2 + (y(p.latitude) - centerY) * world).toFloat()
        val path = Path()
        route.forEachIndexed { i, p ->
            if (i == 0 || p.segment != route[i - 1].segment) path.moveTo(px(p), py(p))
            else path.lineTo(px(p), py(p))
        }
        paint.style = Paint.Style.STROKE; paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
        paint.color = C.bg; paint.strokeWidth = context.dp(7).toFloat(); canvas.drawPath(path, paint)
        paint.color = C.green; paint.strokeWidth = context.dp(4).toFloat(); canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        for ((p, color) in listOf(route.first() to C.green, route.last() to C.cyan)) {
            paint.color = C.white; canvas.drawCircle(px(p), py(p), context.dp(8).toFloat(), paint)
            paint.color = color; canvas.drawCircle(px(p), py(p), context.dp(5).toFloat(), paint)
        }
        if (visibleTiles == 0) {
            paint.color = C.bg; canvas.drawRect(0f, 0f, width.toFloat(), context.dp(30).toFloat(), paint)
            paint.color = C.white; paint.textSize = context.dp(11).toFloat(); paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Mapa sin cargar · la ruta sigue disponible", width / 2f, context.dp(20).toFloat(), paint)
        }
        paint.color = C.bg
        canvas.drawRect(0f, height - context.dp(24).toFloat(), width.toFloat(), height.toFloat(), paint)
        paint.color = C.white; paint.textSize = context.dp(11).toFloat(); paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("© OpenStreetMap contributors", width - context.dp(8).toFloat(), height - context.dp(7).toFloat(), paint)
    }

    private fun requestTile(key: String, z: Int, x: Int, y: Int) {
        if (key in pending || System.currentTimeMillis() - (failures[key] ?: 0) < 30_000 || !isAttachedToWindow) return
        pending.add(key)
        worker.execute {
            val file = File(cacheDir, "$key.png")
            val bitmap = runCatching {
                // Honor at least seven days of local caching. No prefetch or offline download.
                if (!file.exists() || System.currentTimeMillis() - file.lastModified() > 7 * 86_400_000L) {
                    val connection = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
                    try {
                        connection.setRequestProperty("User-Agent", "FitBlock/1.0 (Android; com.fitblock.app; CommercialDemo)")
                        connection.connectTimeout = 6000; connection.readTimeout = 6000
                        check(connection.responseCode == 200)
                        val data = connection.inputStream.use { it.readBytes() }
                        check(data.size < 1_000_000)
                        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Invalid tile")
                        file.writeBytes(data)
                        val cached = cacheDir.listFiles().orEmpty()
                        if (cached.sumOf { it.length() } > 48L * 1024 * 1024) {
                            cached.filter { System.currentTimeMillis() - it.lastModified() > 7 * 86_400_000L }
                                .forEach { it.delete() }
                        }
                        decoded
                    } finally { connection.disconnect() }
                } else BitmapFactory.decodeFile(file.absolutePath)
            }.getOrNull()
            post {
                pending.remove(key)
                if (bitmap != null) tiles.put(key, bitmap) else failures[key] = System.currentTimeMillis()
                invalidate()
            }
        }
    }

    companion object {
        private val worker = Executors.newFixedThreadPool(2)
        private fun x(longitude: Double) = (longitude + 180) / 360
        private fun y(latitude: Double): Double {
            val rad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
            return (1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2
        }
        private fun wrap(delta: Double) = delta - floor(delta + .5)
    }
}
