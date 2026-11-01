package com.example.geo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.util.LruCache
import com.example.model.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * OpenStreetMap Tile Manager and Web Mercator Projection Engine.
 * Complies strictly with the OpenStreetMap Tile Usage Policy:
 * - Identifiable User-Agent
 * - Local disk and in-memory LRU caching to prevent redundant requests
 * - Asynchronous background fetching that never blocks camera or UI
 * - Graceful offline fallback
 * - Mandatory OpenStreetMap attribution
 */
object OsmTileManager {
    private const val TAG = "OsmTileManager"
    const val ATTRIBUTION = "© OpenStreetMap contributors"
    private const val USER_AGENT = "HighwayTimestampCameraPro/1.0 (CTECS Highway Survey Surveying App; Android)"
    private const val TILE_SIZE = 256

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // 16 MB in-memory cache for tile bitmaps
    private val memoryCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    // State flow notifying UI whenever a tile has been downloaded so map can re-render
    private val _tileUpdateEvent = MutableStateFlow(0L)
    val tileUpdateEvent: StateFlow<Long> = _tileUpdateEvent.asStateFlow()

    fun lonToTileX(lon: Double, zoom: Int): Double {
        return (lon + 180.0) / 360.0 * (1 shl zoom)
    }

    fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)
    }

    fun geoToScreen(
        pt: GeoPoint,
        center: GeoPoint,
        widthPx: Float,
        heightPx: Float,
        zoom: Int = 15,
        panOffsetX: Float = 0f,
        panOffsetY: Float = 0f
    ): Pair<Float, Float> {
        val centerPxX = lonToTileX(center.longitude, zoom) * TILE_SIZE
        val centerPxY = latToTileY(center.latitude, zoom) * TILE_SIZE
        val minPxX = centerPxX - (widthPx / 2f) - panOffsetX
        val minPxY = centerPxY - (heightPx / 2f) - panOffsetY

        val ptPxX = lonToTileX(pt.longitude, zoom) * TILE_SIZE
        val ptPxY = latToTileY(pt.latitude, zoom) * TILE_SIZE

        val sx = (ptPxX - minPxX).toFloat()
        val sy = (ptPxY - minPxY).toFloat()
        return Pair(sx, sy)
    }

    /**
     * Stitches real OpenStreetMap tiles centered around [center] into a target [Bitmap]
     * of dimensions [widthPx] x [heightPx], accounting for interactive pan offsets.
     * Returns null if no cached tiles are ready. Enqueues missing tiles for background download.
     */
    fun renderMapBitmap(
        context: Context,
        center: GeoPoint,
        widthPx: Int,
        heightPx: Int,
        zoom: Int = 15,
        panOffsetX: Float = 0f,
        panOffsetY: Float = 0f
    ): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null

        val centerPxX = lonToTileX(center.longitude, zoom) * TILE_SIZE
        val centerPxY = latToTileY(center.latitude, zoom) * TILE_SIZE

        val minPxX = centerPxX - (widthPx / 2.0) - panOffsetX
        val maxPxX = centerPxX + (widthPx / 2.0) - panOffsetX
        val minPxY = centerPxY - (heightPx / 2.0) - panOffsetY
        val maxPxY = centerPxY + (heightPx / 2.0) - panOffsetY

        val minTileX = floor(minPxX / TILE_SIZE).toInt()
        val maxTileX = floor(maxPxX / TILE_SIZE).toInt()
        val minTileY = floor(minPxY / TILE_SIZE).toInt()
        val maxTileY = floor(maxPxY / TILE_SIZE).toInt()

        var hasAnyTile = false
        val destBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(destBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        for (tx in minTileX..maxTileX) {
            for (ty in minTileY..maxTileY) {
                val tile = getTile(context, zoom, tx, ty)
                if (tile != null) {
                    hasAnyTile = true
                    val tileWorldLeft = tx * TILE_SIZE.toDouble()
                    val tileWorldTop = ty * TILE_SIZE.toDouble()

                    val screenLeft = (tileWorldLeft - minPxX).toFloat()
                    val screenTop = (tileWorldTop - minPxY).toFloat()

                    canvas.drawBitmap(tile, screenLeft, screenTop, paint)
                }
            }
        }

        return if (hasAnyTile) destBitmap else null
    }

    private fun getTile(context: Context, zoom: Int, x: Int, y: Int): Bitmap? {
        val key = "$zoom/$x/$y"
        // 1. In-memory LRU cache
        memoryCache.get(key)?.let { return it }

        // 2. Disk cache
        val cacheDir = File(context.cacheDir, "osm_tiles")
        val tileFile = File(cacheDir, "$zoom/$x/$y.png")
        if (tileFile.exists() && tileFile.length() > 0) {
            try {
                val bmp = BitmapFactory.decodeFile(tileFile.absolutePath)
                if (bmp != null) {
                    memoryCache.put(key, bmp)
                    return bmp
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode cached tile $key", e)
            }
        }

        // 3. Fetch asynchronously
        fetchTileAsync(context, zoom, x, y)
        return null
    }

    private val pendingFetches = mutableSetOf<String>()

    private fun fetchTileAsync(context: Context, zoom: Int, x: Int, y: Int) {
        val key = "$zoom/$x/$y"
        synchronized(pendingFetches) {
            if (pendingFetches.contains(key)) return
            pendingFetches.add(key)
        }

        scope.launch {
            try {
                val url = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            memoryCache.put(key, bmp)

                            // Save to disk cache
                            val cacheDir = File(context.cacheDir, "osm_tiles/$zoom/$x")
                            cacheDir.mkdirs()
                            val tileFile = File(cacheDir, "$y.png")
                            FileOutputStream(tileFile).use { it.write(bytes) }

                            // Trigger UI update
                            _tileUpdateEvent.value = System.currentTimeMillis()
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors (graceful offline behavior)
            } finally {
                synchronized(pendingFetches) {
                    pendingFetches.remove(key)
                }
            }
        }
    }
}
