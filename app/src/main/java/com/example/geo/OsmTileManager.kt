package com.example.geo

import android.content.ComponentCallbacks2
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * OpenStreetMap & Satellite Tile Manager and Web Mercator Projection Engine.
 * Supports:
 * - Full-Earth pan and zoom (Zoom 1..19) with seamless antimeridian longitude wrapping
 * - Standard OpenStreetMap street-map tiles
 * - Genuine Satellite imagery tiles (Esri World Imagery ArcGIS MapServer)
 * - Identifiable User-Agent
 * - Dynamic memory-aware LRU cache + bounded disk cache with automatic pruning
 * - Concurrency-capped asynchronous fetching with exponential offline/failure backoff
 * - Coalesced tile redraw notifications and lazy bitmap allocation
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
    const val MIN_TILE_ZOOM = 1
    const val MAX_TILE_ZOOM = 19
    const val MAX_MERCATOR_LAT = 85.05112878
    const val MIN_VIEW_RANGE_METRES = 60.0
    const val MAX_VIEW_RANGE_METRES = 32_000_000.0

    private const val MAX_DISK_CACHE_BYTES = 50L * 1024L * 1024L // 50 MB bounded disk cache
    private const val MAX_TILE_AGE_MS = 14L * 24L * 60L * 60L * 1000L // 14 days
    private const val MAX_CONCURRENT_DOWNLOADS = 4
    private const val BASE_FAILURE_BACKOFF_MS = 30_000L
    private const val MAX_FAILURE_BACKOFF_MS = 300_000L
    private const val OFFLINE_COOLDOWN_MS = 15_000L
    private const val TILE_UPDATE_COALESCE_MS = 75L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fetchSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 12
                maxRequestsPerHost = MAX_CONCURRENT_DOWNLOADS
            }
        )
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    // Adaptive in-memory cache capped at 24 MB (or 1/12th of JVM max heap on constrained devices)
    private val maxMemoryCacheBytes: Int = run {
        val heapShare = (Runtime.getRuntime().maxMemory() / 12L).coerceIn(4L * 1024L * 1024L, 24L * 1024L * 1024L)
        heapShare.toInt()
    }

    private val memoryCache = object : LruCache<String, Bitmap>(maxMemoryCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount.coerceAtLeast(1)
        }
    }

    private data class TileFailureRecord(
        val failureCount: Int,
        val retryAfterMs: Long
    )

    private val tileFailureBackoff = ConcurrentHashMap<String, TileFailureRecord>()
    private val globalOfflineUntilMs = AtomicLong(0L)
    private val diskPruneScheduled = AtomicBoolean(false)
    private val tileEventCoalesceScheduled = AtomicBoolean(false)

    // State flow notifying UI whenever a batch of tiles has been downloaded so map can re-render
    private val _tileUpdateEvent = MutableStateFlow(0L)
    val tileUpdateEvent: StateFlow<Long> = _tileUpdateEvent.asStateFlow()

    // State flow tracking whether background tile downloads are currently in flight
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun trimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                memoryCache.evictAll()
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                memoryCache.trimToSize(memoryCache.maxSize() / 2)
            }
        }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    fun memoryCacheSizeBytes(): Int = memoryCache.size()

    internal fun putTileInMemoryCacheForTest(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }

    internal fun clearFailureBackoff() {
        tileFailureBackoff.clear()
        globalOfflineUntilMs.set(0L)
    }

    internal fun recordTileFailure(key: String, nowMs: Long = System.currentTimeMillis(), isOfflineError: Boolean = false) {
        val prev = tileFailureBackoff[key]
        val nextCount = ((prev?.failureCount ?: 0) + 1).coerceAtMost(5)
        val backoffMs = (BASE_FAILURE_BACKOFF_MS * (1L shl (nextCount - 1))).coerceAtMost(MAX_FAILURE_BACKOFF_MS)
        tileFailureBackoff[key] = TileFailureRecord(nextCount, nowMs + backoffMs)
        if (tileFailureBackoff.size > 512) {
            tileFailureBackoff.entries.removeIf { it.value.retryAfterMs <= nowMs }
        }
        if (isOfflineError) {
            globalOfflineUntilMs.set(nowMs + OFFLINE_COOLDOWN_MS)
        }
    }

    internal fun isTileInBackoff(key: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (nowMs < globalOfflineUntilMs.get()) return true
        val record = tileFailureBackoff[key] ?: return false
        if (nowMs >= record.retryAfterMs) {
            tileFailureBackoff.remove(key)
            return false
        }
        return true
    }

    fun tileFileFor(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean): File {
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val wrappedX = wrapTileX(x, safeZoom)
        val typeFolder = if (isSatellite) "satellite" else "standard"
        val ext = if (isSatellite) "jpg" else "png"
        return File(context.cacheDir, "osm_tiles/$typeFolder/$safeZoom/$wrappedX/$y.$ext")
    }

    fun wrapLongitude(lon: Double): Double {
        return ((((lon + 180.0) % 360.0) + 360.0) % 360.0) - 180.0
    }

    fun wrapTileX(tx: Int, zoom: Int): Int {
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val numTiles = 1 shl safeZoom
        return ((tx % numTiles) + numTiles) % numTiles
    }

    fun isValidTileY(ty: Int, zoom: Int): Boolean {
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val numTiles = 1 shl safeZoom
        return ty in 0 until numTiles
    }

    fun lonToTileX(lon: Double, zoom: Int): Double {
        return (wrapLongitude(lon) + 180.0) / 360.0 * (1 shl zoom.coerceIn(0, 22))
    }

    fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT))
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom.coerceIn(0, 22))
    }

    /**
     * Converts an interactive pixel pan delta into an updated geographic map center,
     * seamlessly wrapping across +/-180 deg longitude and clamping latitude to Web Mercator limits.
     */
    fun offsetGeoPointByPixels(
        center: GeoPoint,
        deltaPxX: Float,
        deltaPxY: Float,
        zoom: Int
    ): GeoPoint {
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val worldPx = (1 shl safeZoom).toDouble() * TILE_SIZE
        val newCenterPxX = lonToTileX(center.longitude, safeZoom) * TILE_SIZE - deltaPxX.toDouble()
        val newCenterPxY = (latToTileY(center.latitude, safeZoom) * TILE_SIZE - deltaPxY.toDouble())
            .coerceIn(0.0, worldPx)

        val lonDeg = wrapLongitude((newCenterPxX / worldPx) * 360.0 - 180.0)
        val n = Math.PI - 2.0 * Math.PI * (newCenterPxY / worldPx)
        val latDeg = Math.toDegrees(atan(sinh(n))).coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        return GeoPoint(latDeg, lonDeg)
    }

    fun zoomFromViewRangeMetres(viewRangeMetres: Double): Int {
        val clamped = viewRangeMetres.coerceIn(MIN_VIEW_RANGE_METRES, MAX_VIEW_RANGE_METRES)
        val ratio = (clamped / 125.0).coerceAtLeast(0.25)
        return (19.0 - ln(ratio) / ln(2.0)).roundToInt().coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
    }

    fun viewRangeMetresForZoom(zoomLevel: Float): Double {
        val clampedZoom = zoomLevel.coerceIn(MIN_TILE_ZOOM.toFloat(), MAX_TILE_ZOOM.toFloat())
        return (125.0 * 2.0.pow((19.0f - clampedZoom).toDouble()))
            .coerceIn(MIN_VIEW_RANGE_METRES, MAX_VIEW_RANGE_METRES)
    }

    fun formatScaleLabel(viewRangeMetres: Double): String {
        return when {
            viewRangeMetres >= 1000.0 -> "${(viewRangeMetres / 1000.0).roundToInt()} km"
            else -> "${viewRangeMetres.roundToInt()} m"
        }
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
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val worldPx = (1 shl safeZoom).toDouble() * TILE_SIZE
        val centerPxX = lonToTileX(center.longitude, safeZoom) * TILE_SIZE
        val centerPxY = latToTileY(center.latitude, safeZoom) * TILE_SIZE
        val ptPxX = lonToTileX(pt.longitude, safeZoom) * TILE_SIZE
        val ptPxY = latToTileY(pt.latitude, safeZoom) * TILE_SIZE

        var dxPx = ptPxX - centerPxX
        if (worldPx > 0.0) {
            if (dxPx > worldPx / 2.0) dxPx -= worldPx
            if (dxPx < -worldPx / 2.0) dxPx += worldPx
        }
        val sx = (widthPx / 2.0 + panOffsetX + dxPx).toFloat()
        val sy = (heightPx / 2.0 + panOffsetY + (ptPxY - centerPxY)).toFloat()
        return Pair(sx, sy)
    }

    /**
     * Stitches real map tiles (Standard street map or Satellite imagery) centered around [center]
     * into a target [Bitmap] of dimensions [widthPx] x [heightPx], accounting for interactive pan offsets.
     * Allocates the destination bitmap lazily only when at least one cached tile is available.
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

        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val numTiles = 1 shl safeZoom
        val isSatellite = mapType.equals("satellite", ignoreCase = true)
        val centerPxX = lonToTileX(center.longitude, safeZoom) * TILE_SIZE
        val centerPxY = latToTileY(center.latitude, safeZoom) * TILE_SIZE

        val minPxX = centerPxX - (widthPx / 2.0) - panOffsetX
        val maxPxX = centerPxX + (widthPx / 2.0) - panOffsetX
        val minPxY = centerPxY - (heightPx / 2.0) - panOffsetY
        val maxPxY = centerPxY + (heightPx / 2.0) - panOffsetY

        val minTileX = floor(minPxX / TILE_SIZE).toInt()
        val maxTileX = floor(maxPxX / TILE_SIZE).toInt().coerceAtMost(minTileX + 24)
        val minTileY = floor(minPxY / TILE_SIZE).toInt().coerceAtLeast(0)
        val maxTileY = floor(maxPxY / TILE_SIZE).toInt().coerceAtMost(numTiles - 1)
        if (minTileY > maxTileY) return null

        var destBitmap: Bitmap? = null
        var canvas: Canvas? = null
        var paint: Paint? = null

        for (tx in minTileX..maxTileX) {
            val wrappedTx = ((tx % numTiles) + numTiles) % numTiles
            for (ty in minTileY..maxTileY) {
                val tile = getTile(context, safeZoom, wrappedTx, ty, isSatellite)
                if (tile != null && !tile.isRecycled) {
                    if (destBitmap == null) {
                        destBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        canvas = Canvas(destBitmap)
                        paint = createTilePaint(isSatellite = isSatellite, darkTheme = darkTheme)
                    }
                    val tileWorldLeft = tx * TILE_SIZE.toDouble()
                    val tileWorldTop = ty * TILE_SIZE.toDouble()
                    val screenLeft = (tileWorldLeft - minPxX).toFloat()
                    val screenTop = (tileWorldTop - minPxY).toFloat()
                    canvas?.drawBitmap(tile, screenLeft, screenTop, paint)
                }
            }
        }

        return destBitmap
    }

    private fun createTilePaint(isSatellite: Boolean, darkTheme: Boolean): Paint {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (isSatellite) {
            val satMatrix = ColorMatrix().apply { setSaturation(1.05f) }
            paint.colorFilter = ColorMatrixColorFilter(satMatrix)
        } else if (darkTheme) {
            val darkMatrix = ColorMatrix(floatArrayOf(
                -0.85f,  0.00f,  0.00f, 0.0f, 215f,
                 0.00f, -0.85f,  0.00f, 0.0f, 215f,
                 0.00f,  0.00f, -0.80f, 0.0f, 225f,
                 0.00f,  0.00f,  0.00f, 1.0f,   0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(darkMatrix)
        } else {
            val lightMatrix = ColorMatrix().apply { setSaturation(1.08f) }
            paint.colorFilter = ColorMatrixColorFilter(lightMatrix)
        }
        return paint
    }

    private fun getTile(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean): Bitmap? {
        if (!isValidTileY(y, zoom)) return null
        val wrappedX = wrapTileX(x, zoom)
        val typeKey = if (isSatellite) "satellite" else "standard"
        val key = "$typeKey/$zoom/$wrappedX/$y"

        // 1. In-memory LRU cache
        memoryCache.get(key)?.let { cached ->
            if (!cached.isRecycled) return cached
            memoryCache.remove(key)
        }

        // 2. Disk cache (always keyed by wrappedX for antimeridian consistency)
        val tileFile = tileFileFor(context, zoom, wrappedX, y, isSatellite)
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
            val legacyFile = File(context.cacheDir, "osm_tiles/$zoom/$wrappedX/$y.png")
            if (legacyFile.exists() && legacyFile.length() > 0) {
                try {
                    val bmp = BitmapFactory.decodeFile(legacyFile.absolutePath)
                    if (bmp != null) {
                        memoryCache.put(key, bmp)
                        return bmp
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Fetch asynchronously with concurrency limit and failure backoff
        fetchTileAsync(context.applicationContext, zoom, wrappedX, y, isSatellite)
        return null
    }

    private val pendingFetches = mutableSetOf<String>()

    private fun notifyTileUpdatedCoalesced() {
        if (tileEventCoalesceScheduled.compareAndSet(false, true)) {
            scope.launch {
                try {
                    delay(TILE_UPDATE_COALESCE_MS)
                    _tileUpdateEvent.value = System.currentTimeMillis()
                } finally {
                    tileEventCoalesceScheduled.set(false)
                }
            }
        }
    }

    private fun scheduleDiskCachePruneIfNeeded(appContext: Context) {
        if (!diskPruneScheduled.compareAndSet(false, true)) return
        scope.launch {
            try {
                pruneDiskCache(File(appContext.cacheDir, "osm_tiles"))
            } catch (_: Exception) {}
        }
    }

    internal fun pruneDiskCache(
        rootDir: File,
        maxBytes: Long = MAX_DISK_CACHE_BYTES,
        maxAgeMs: Long = MAX_TILE_AGE_MS,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!rootDir.exists() || !rootDir.isDirectory) return
        val files = rootDir.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return

        var totalBytes = 0L
        val surviving = mutableListOf<File>()
        for (f in files) {
            val age = nowMs - f.lastModified()
            if (age > maxAgeMs) {
                f.delete()
            } else {
                totalBytes += f.length()
                surviving.add(f)
            }
        }

        if (totalBytes > maxBytes) {
            val targetBytes = (maxBytes * 8L) / 10L
            val oldestFirst = surviving.sortedBy { it.lastModified() }
            for (f in oldestFirst) {
                if (totalBytes <= targetBytes) break
                val len = f.length()
                if (f.delete()) {
                    totalBytes -= len
                }
            }
        }
    }

    private fun fetchTileAsync(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean) {
        if (!isValidTileY(y, zoom)) return
        val wrappedX = wrapTileX(x, zoom)
        val typeKey = if (isSatellite) "satellite" else "standard"
        val key = "$typeKey/$zoom/$wrappedX/$y"

        val nowMs = System.currentTimeMillis()
        if (isTileInBackoff(key, nowMs)) return

        synchronized(pendingFetches) {
            if (pendingFetches.contains(key)) return
            pendingFetches.add(key)
            _isLoading.value = true
        }

        scheduleDiskCachePruneIfNeeded(context)

        scope.launch {
            try {
                fetchSemaphore.withPermit {
                    if (isTileInBackoff(key)) return@withPermit

                    val url = if (isSatellite) {
                        // ArcGIS World Imagery tile URL format: {zoom}/{row_y}/{col_x}
                        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$wrappedX"
                    } else {
                        // OpenStreetMap tile URL format: {zoom}/{col_x}/{row_y}.png
                        "https://tile.openstreetmap.org/$zoom/$wrappedX/$y.png"
                    }

                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bmp != null) {
                                    memoryCache.put(key, bmp)
                                    tileFailureBackoff.remove(key)

                                    // Save to disk cache atomically using wrappedX
                                    val tileFile = tileFileFor(context, zoom, wrappedX, y, isSatellite)
                                    tileFile.parentFile?.mkdirs()
                                    val tmpFile = File(tileFile.parentFile, "${tileFile.name}.tmp")
                                    FileOutputStream(tmpFile).use { it.write(bytes) }
                                    if (!tmpFile.renameTo(tileFile)) {
                                        tmpFile.copyTo(tileFile, overwrite = true)
                                        tmpFile.delete()
                                    }

                                    notifyTileUpdatedCoalesced()
                                } else {
                                    recordTileFailure(key)
                                }
                            } else {
                                recordTileFailure(key)
                            }
                        } else {
                            recordTileFailure(key)
                        }
                    }
                }
            } catch (e: Exception) {
                val isOffline = e is UnknownHostException ||
                    e is ConnectException ||
                    e is SocketTimeoutException
                recordTileFailure(key, isOfflineError = isOffline)
            } finally {
                synchronized(pendingFetches) {
                    pendingFetches.remove(key)
                    _isLoading.value = pendingFetches.isNotEmpty()
                }
            }
        }
    }
}
