package com.example.geo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
 * OpenStreetMap & Satellite Tile Manager and Web Mercator Projection Engine.
 * Supports:
 * - Standard OpenStreetMap street-map tiles
 * - Genuine Satellite imagery tiles (Esri World Imagery ArcGIS MapServer)
 * - Identifiable User-Agent
 * - Local disk and in-memory LRU caching to prevent redundant requests
 * - Asynchronous background fetching with loading state indicator
 * - Graceful offline fallback with terrain grid
 * - Accurate attribution for both providers
 */
object OsmTileManager {
    private const val TAG = "OsmTileManager"
    const val ATTRIBUTION_STANDARD = "© OpenStreetMap contributors"
    const val ATTRIBUTION_SATELLITE = "© Esri, Maxar, Earthstar Geographics"
    const val ATTRIBUTION = ATTRIBUTION_STANDARD
    private const val USER_AGENT = "HighwayTimestampCameraPro/1.0 (CTECS Highway Survey Surveying App; Android)"
    private const val TILE_SIZE = 256

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // 24 MB in-memory cache for tile bitmaps (standard & satellite)
    private val memoryCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    // State flow notifying UI whenever a tile has been downloaded so map can re-render
    private val _tileUpdateEvent = MutableStateFlow(0L)
    val tileUpdateEvent: StateFlow<Long> = _tileUpdateEvent.asStateFlow()

    // State flow tracking whether background tile downloads are currently in flight
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
     * Stitches real map tiles (Standard street map or Satellite imagery) centered around [center]
     * into a target [Bitmap] of dimensions [widthPx] x [heightPx], accounting for interactive pan offsets.
     * Returns null if no cached tiles are ready. Enqueues missing tiles for background download.
     */
    fun renderMapBitmap(
        context: Context,
        center: GeoPoint,
        widthPx: Int,
        heightPx: Int,
        zoom: Int = 15,
        panOffsetX: Float = 0f,
        panOffsetY: Float = 0f,
        darkTheme: Boolean = true,
        mapType: String = "standard"
    ): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null

        val isSatellite = mapType.equals("satellite", ignoreCase = true)
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

        if (isSatellite) {
            // Satellite imagery: natural aerial photography without inverted colors
            val satMatrix = ColorMatrix().apply {
                setSaturation(1.05f)
            }
            paint.colorFilter = ColorMatrixColorFilter(satMatrix)
        } else if (darkTheme) {
            // Standard Street Map (Dark Mode): invert brightness into a deep slate palette while preserving road clarity
            val darkMatrix = ColorMatrix(floatArrayOf(
                -0.85f,  0.00f,  0.00f, 0.0f, 215f,
                 0.00f, -0.85f,  0.00f, 0.0f, 215f,
                 0.00f,  0.00f, -0.80f, 0.0f, 225f,
                 0.00f,  0.00f,  0.00f, 1.0f,   0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(darkMatrix)
        } else {
            // Standard Street Map (Light Mode): crisp, natural OpenStreetMap palette with vibrant roads and clear labels
            val lightMatrix = ColorMatrix().apply {
                setSaturation(1.08f)
            }
            paint.colorFilter = ColorMatrixColorFilter(lightMatrix)
        }

        for (tx in minTileX..maxTileX) {
            for (ty in minTileY..maxTileY) {
                val tile = getTile(context, zoom, tx, ty, isSatellite)
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

    private fun getTile(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean): Bitmap? {
        val typeKey = if (isSatellite) "satellite" else "standard"
        val key = "$typeKey/$zoom/$x/$y"

        // 1. In-memory LRU cache
        memoryCache.get(key)?.let { return it }

        // 2. Disk cache
        val ext = if (isSatellite) "jpg" else "png"
        val typeFolder = if (isSatellite) "satellite" else "standard"
        val cacheDir = File(context.cacheDir, "osm_tiles/$typeFolder")
        val tileFile = File(cacheDir, "$zoom/$x/$y.$ext")

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

        // Backward compatibility fallback for standard tiles cached previously under root "osm_tiles"
        if (!isSatellite) {
            val legacyFile = File(context.cacheDir, "osm_tiles/$zoom/$x/$y.png")
            if (legacyFile.exists() && legacyFile.length() > 0) {
                try {
                    val bmp = BitmapFactory.decodeFile(legacyFile.absolutePath)
                    if (bmp != null) {
                        memoryCache.put(key, bmp)
                        return bmp
                    }
                } catch (ignored: Exception) {}
            }
        }

        // 3. Fetch asynchronously
        fetchTileAsync(context, zoom, x, y, isSatellite)
        return null
    }

    private val pendingFetches = mutableSetOf<String>()

    private fun fetchTileAsync(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean) {
        val typeKey = if (isSatellite) "satellite" else "standard"
        val key = "$typeKey/$zoom/$x/$y"

        synchronized(pendingFetches) {
            if (pendingFetches.contains(key)) return
            pendingFetches.add(key)
            _isLoading.value = true
        }

        scope.launch {
            try {
                val url = if (isSatellite) {
                    // ArcGIS World Imagery tile URL format: {zoom}/{row_y}/{col_x}
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"
                } else {
                    // OpenStreetMap tile URL format: {zoom}/{col_x}/{row_y}.png
                    "https://tile.openstreetmap.org/$zoom/$x/$y.png"
                }

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
                            val typeFolder = if (isSatellite) "satellite" else "standard"
                            val ext = if (isSatellite) "jpg" else "png"
                            val cacheDir = File(context.cacheDir, "osm_tiles/$typeFolder/$zoom/$x")
                            cacheDir.mkdirs()
                            val tileFile = File(cacheDir, "$y.$ext")
                            FileOutputStream(tileFile).use { it.write(bytes) }

                            // Trigger UI update
                            _tileUpdateEvent.value = System.currentTimeMillis()
                        }
                    }
                }
            } catch (e: Exception) {
                // Graceful offline behavior: missing tiles do not crash the app
            } finally {
                synchronized(pendingFetches) {
                    pendingFetches.remove(key)
                    _isLoading.value = pendingFetches.isNotEmpty()
                }
            }
        }
    }
}
