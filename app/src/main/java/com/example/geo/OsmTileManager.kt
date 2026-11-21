package com.example.geo

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
 * - Shared persistent KMZ/KML offline tile store (`filesDir/offline_map_tiles`) + bounded volatile cache
 * - Parent-tile upscaling fallback (`z-1..z-4`) to prevent checkerboard/blank strips when zooming or offline
 * - Stitched bitmap LRU cache for zero frame-allocation flicker during UI animations
 * - Concurrency-capped asynchronous fetching with exponential offline/failure backoff & image validation
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
    const val MIN_VIEW_RANGE_METRES = 5.0
    const val MAX_VIEW_RANGE_METRES = 32_000_000.0

    private const val MAX_DISK_CACHE_BYTES = 50L * 1024L * 1024L // 50 MB bounded volatile disk cache
    private const val MAX_TILE_AGE_MS = 14L * 24L * 60L * 60L * 1000L // 14 days
    private const val MAX_CONCURRENT_DOWNLOADS = 4
    private const val BASE_FAILURE_BACKOFF_MS = 30_000L
    private const val MAX_FAILURE_BACKOFF_MS = 300_000L
    private const val OFFLINE_COOLDOWN_MS = 15_000L
    private const val TILE_UPDATE_COALESCE_MS = 75L

    data class RenderedMapResult(
        val bitmap: Bitmap?,
        val exactTilesFound: Int,
        val fallbackTilesUsed: Int,
        val missingTiles: Int,
        val totalTiles: Int
    ) {
        val hasAnyCoverage: Boolean get() = exactTilesFound > 0 || fallbackTilesUsed > 0
        val hasMissingTiles: Boolean get() = missingTiles > 0
        val isCompleteCoverage: Boolean get() = totalTiles > 0 && missingTiles == 0
    }

    enum class TileDownloadOutcome {
        ALREADY_CACHED,
        DOWNLOADED,
        TRANSIENT_FAILURE,
        RATE_LIMITED,
        PERMANENT_FAILURE
    }

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

    // Small stitched-frame cache prevents allocating bitmaps every frame during 60fps Canvas animations
    private val stitchedCache = object : LruCache<String, RenderedMapResult>(6) {}

    private data class TileFailureRecord(
        val failureCount: Int,
        val retryAfterMs: Long
    )

    private val tileFailureBackoff = ConcurrentHashMap<String, TileFailureRecord>()
    private val globalOfflineUntilMs = AtomicLong(0L)
    private val diskPruneScheduled = AtomicBoolean(false)
    private val tileEventCoalesceScheduled = AtomicBoolean(false)

    @Volatile
    var protectedTileKeysProvider: (() -> Set<String>)? = null

    @Volatile
    internal var isNetworkConnectedProvider: (Context) -> Boolean = { ctx ->
        OfflineMapSyncManager.checkDeviceNetwork(ctx).isConnected
    }

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
                stitchedCache.evictAll()
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                memoryCache.trimToSize(memoryCache.maxSize() / 2)
                stitchedCache.evictAll()
            }
        }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
        stitchedCache.evictAll()
    }

    fun invalidateStitchedCache() {
        stitchedCache.evictAll()
    }

    fun memoryCacheSizeBytes(): Int = memoryCache.size()

    internal fun putTileInMemoryCacheForTest(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
        stitchedCache.evictAll()
    }

    internal fun clearFailureBackoff() {
        tileFailureBackoff.clear()
        globalOfflineUntilMs.set(0L)
    }

    internal fun recordTileFailure(
        key: String,
        nowMs: Long = System.currentTimeMillis(),
        isOfflineError: Boolean = false,
        explicitCooldownMs: Long? = null
    ) {
        val prev = tileFailureBackoff[key]
        val nextCount = ((prev?.failureCount ?: 0) + 1).coerceAtMost(5)
        val backoffMs = explicitCooldownMs
            ?: (BASE_FAILURE_BACKOFF_MS * (1L shl (nextCount - 1))).coerceAtMost(MAX_FAILURE_BACKOFF_MS)
        tileFailureBackoff[key] = TileFailureRecord(nextCount, nowMs + backoffMs)
        if (tileFailureBackoff.size > 512) {
            tileFailureBackoff.entries.removeIf { it.value.retryAfterMs <= nowMs }
        }
        if (isOfflineError) {
            globalOfflineUntilMs.set(nowMs + (explicitCooldownMs ?: OFFLINE_COOLDOWN_MS))
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

    fun tileKey(zoom: Int, x: Int, y: Int, isSatellite: Boolean): String {
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val wrappedX = wrapTileX(x, safeZoom)
        val typeFolder = if (isSatellite) "satellite" else "standard"
        return "$typeFolder/$safeZoom/$wrappedX/$y"
    }

    fun tileFileFor(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean): File {
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val wrappedX = wrapTileX(x, safeZoom)
        val typeFolder = if (isSatellite) "satellite" else "standard"
        val ext = if (isSatellite) "jpg" else "png"
        return File(context.cacheDir, "osm_tiles/$typeFolder/$safeZoom/$wrappedX/$y.$ext")
    }

    fun offlineRootDir(context: Context): File = File(context.filesDir, "offline_map_tiles")

    fun offlineTileFileFor(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean): File {
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val wrappedX = wrapTileX(x, safeZoom)
        val typeFolder = if (isSatellite) "satellite" else "standard"
        val ext = if (isSatellite) "jpg" else "png"
        return File(offlineRootDir(context), "$typeFolder/$safeZoom/$wrappedX/$y.$ext")
    }

    fun findLocalTileFile(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean): File? {
        if (!isValidTileY(y, zoom)) return null
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val wrappedX = wrapTileX(x, safeZoom)
        val persistent = offlineTileFileFor(context, safeZoom, wrappedX, y, isSatellite)
        if (persistent.exists() && persistent.length() > 0L) return persistent
        val cached = tileFileFor(context, safeZoom, wrappedX, y, isSatellite)
        if (cached.exists() && cached.length() > 0L) return cached
        if (!isSatellite) {
            val legacy = File(context.cacheDir, "osm_tiles/$safeZoom/$wrappedX/$y.png")
            if (legacy.exists() && legacy.length() > 0L) return legacy
        }
        return null
    }

    fun hasCachedTile(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean): Boolean {
        if (!isValidTileY(y, zoom)) return false
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val key = tileKey(safeZoom, x, y, isSatellite)
        memoryCache.get(key)?.let { if (!it.isRecycled) return true }
        return findLocalTileFile(context, safeZoom, x, y, isSatellite) != null
    }

    fun tileUrlFor(zoom: Int, x: Int, y: Int, isSatellite: Boolean): String {
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val wrappedX = wrapTileX(x, safeZoom)
        return if (isSatellite) {
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$safeZoom/$y/$wrappedX"
        } else {
            "https://tile.openstreetmap.org/$safeZoom/$wrappedX/$y.png"
        }
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
        val clampedZoom = zoomLevel.coerceIn(MIN_TILE_ZOOM.toFloat(), 23.0f)
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
     * Uses parent-tile upscaling fallback (`z-1..z-4`) to prevent checkerboard gaps or partial blank areas.
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
        return renderMapBitmapWithCoverage(
            context = context,
            center = center,
            widthPx = widthPx,
            heightPx = heightPx,
            zoom = zoom,
            panOffsetX = panOffsetX,
            panOffsetY = panOffsetY,
            darkTheme = darkTheme,
            mapType = mapType
        ).bitmap
    }

    fun renderMapBitmapWithCoverage(
        context: Context,
        center: GeoPoint,
        widthPx: Int,
        heightPx: Int,
        zoom: Int = 15,
        panOffsetX: Float = 0f,
        panOffsetY: Float = 0f,
        darkTheme: Boolean = true,
        mapType: String = "standard"
    ): RenderedMapResult {
        if (widthPx <= 0 || heightPx <= 0) {
            return RenderedMapResult(null, 0, 0, 0, 0)
        }

        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val numTiles = 1 shl safeZoom
        val isSatellite = mapType.equals("satellite", ignoreCase = true)
        val epoch = _tileUpdateEvent.value
        val latQ = (center.latitude * 100000.0).roundToInt()
        val lonQ = (center.longitude * 100000.0).roundToInt()
        val cacheKey = "$latQ:$lonQ:$safeZoom:${widthPx}x$heightPx:${panOffsetX.toInt()},${panOffsetY.toInt()}:$darkTheme:$isSatellite:$epoch"

        stitchedCache.get(cacheKey)?.let { cached ->
            val bmp = cached.bitmap
            if (bmp == null || !bmp.isRecycled) {
                return cached
            }
            stitchedCache.remove(cacheKey)
        }

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
        if (minTileY > maxTileY) {
            return RenderedMapResult(null, 0, 0, 0, 0)
        }

        var destBitmap: Bitmap? = null
        var canvas: Canvas? = null
        var paint: Paint? = null
        var exactCount = 0
        var fallbackCount = 0
        var missingCount = 0
        var totalCount = 0

        for (tx in minTileX..maxTileX) {
            val wrappedTx = ((tx % numTiles) + numTiles) % numTiles
            for (ty in minTileY..maxTileY) {
                totalCount++
                val tileWorldLeft = tx * TILE_SIZE.toDouble()
                val tileWorldTop = ty * TILE_SIZE.toDouble()
                val screenLeft = (tileWorldLeft - minPxX).toFloat()
                val screenTop = (tileWorldTop - minPxY).toFloat()

                val exactTile = getTile(context, safeZoom, wrappedTx, ty, isSatellite)
                if (exactTile != null && !exactTile.isRecycled) {
                    if (destBitmap == null) {
                        destBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        canvas = Canvas(destBitmap)
                        paint = createTilePaint(isSatellite = isSatellite, darkTheme = darkTheme)
                    }
                    canvas?.drawBitmap(exactTile, screenLeft, screenTop, paint)
                    exactCount++
                } else {
                    val ancestorDrawn = drawAncestorFallbackTile(
                        context = context,
                        targetZoom = safeZoom,
                        wrappedTx = wrappedTx,
                        ty = ty,
                        isSatellite = isSatellite,
                        screenLeft = screenLeft,
                        screenTop = screenTop,
                        ensureCanvas = {
                            if (destBitmap == null) {
                                destBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                                canvas = Canvas(destBitmap)
                                paint = createTilePaint(isSatellite = isSatellite, darkTheme = darkTheme)
                            }
                            canvas!! to paint!!
                        }
                    )
                    if (ancestorDrawn) {
                        fallbackCount++
                    } else {
                        missingCount++
                    }
                }
            }
        }

        val result = RenderedMapResult(
            bitmap = destBitmap,
            exactTilesFound = exactCount,
            fallbackTilesUsed = fallbackCount,
            missingTiles = missingCount,
            totalTiles = totalCount
        )
        // Cache only if nothing was missing OR we're currently backed off/offline, so freshly arriving tiles aren't suppressed
        if (missingCount == 0 || globalOfflineUntilMs.get() > System.currentTimeMillis()) {
            stitchedCache.put(cacheKey, result)
        }
        return result
    }

    private inline fun drawAncestorFallbackTile(
        context: Context,
        targetZoom: Int,
        wrappedTx: Int,
        ty: Int,
        isSatellite: Boolean,
        screenLeft: Float,
        screenTop: Float,
        ensureCanvas: () -> Pair<Canvas, Paint>
    ): Boolean {
        val minParentZoom = (targetZoom - 4).coerceAtLeast(MIN_TILE_ZOOM)
        for (pz in (targetZoom - 1) downTo minParentZoom) {
            val dz = targetZoom - pz
            val div = 1 shl dz
            val pTx = wrappedTx ushr dz
            val pTy = ty ushr dz
            val ancestor = getCachedTileOnly(context, pz, pTx, pTy, isSatellite) ?: continue
            if (ancestor.isRecycled || ancestor.width < div || ancestor.height < div) continue

            val subX = wrappedTx and (div - 1)
            val subY = ty and (div - 1)
            val srcW = ancestor.width / div.toFloat()
            val srcH = ancestor.height / div.toFloat()
            val srcRect = Rect(
                floor(subX * srcW).toInt(),
                floor(subY * srcH).toInt(),
                floor((subX + 1) * srcW).toInt().coerceAtLeast(floor(subX * srcW).toInt() + 1),
                floor((subY + 1) * srcH).toInt().coerceAtLeast(floor(subY * srcH).toInt() + 1)
            )
            val dstRect = RectF(
                screenLeft,
                screenTop,
                screenLeft + TILE_SIZE,
                screenTop + TILE_SIZE
            )
            val (c, p) = ensureCanvas()
            c.drawBitmap(ancestor, srcRect, dstRect, p)
            return true
        }
        return false
    }

    private fun createTilePaint(isSatellite: Boolean, darkTheme: Boolean): Paint {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
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

    fun getCachedTileOnly(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean): Bitmap? {
        if (!isValidTileY(y, zoom)) return null
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val wrappedX = wrapTileX(x, safeZoom)
        val key = tileKey(safeZoom, wrappedX, y, isSatellite)

        memoryCache.get(key)?.let { cached ->
            if (!cached.isRecycled) return cached
            memoryCache.remove(key)
        }

        val candidates = listOfNotNull(
            offlineTileFileFor(context, safeZoom, wrappedX, y, isSatellite),
            tileFileFor(context, safeZoom, wrappedX, y, isSatellite),
            if (!isSatellite) File(context.cacheDir, "osm_tiles/$safeZoom/$wrappedX/$y.png") else null
        )

        for (file in candidates) {
            if (file.exists() && file.length() > 0L) {
                if (!hasValidImageHeader(file)) {
                    try { file.delete() } catch (_: Exception) {}
                    continue
                }
                try {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                        memoryCache.put(key, bmp)
                        return bmp
                    } else {
                        // Purge corrupt zero/truncated file so sync or live fetch can recover it cleanly
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode cached tile $key at ${file.name}", e)
                    try { file.delete() } catch (_: Exception) {}
                }
            }
        }
        return null
    }

    private fun hasValidImageHeader(file: File): Boolean {
        return try {
            val header = ByteArray(12)
            val read = file.inputStream().use { it.read(header) }
            hasValidImageHeaderBytes(header, read)
        } catch (_: Exception) {
            false
        }
    }

    private fun hasValidImageHeaderBytes(bytes: ByteArray, length: Int = bytes.size): Boolean {
        if (length < 12) return false
        val isPng = bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        val isJpeg = bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        val isWebp = bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        return isPng || isJpeg || isWebp
    }

    private fun getTile(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean): Bitmap? {
        val local = getCachedTileOnly(context, zoom, x, y, isSatellite)
        if (local != null) return local

        fetchTileAsync(context.applicationContext, zoom, wrapTileX(x, zoom), y, isSatellite)
        return null
    }

    private val pendingFetches = mutableSetOf<String>()

    fun notifyOfflineTilesAvailable() {
        stitchedCache.evictAll()
        notifyTileUpdatedCoalesced()
    }

    private fun notifyTileUpdatedCoalesced() {
        if (tileEventCoalesceScheduled.compareAndSet(false, true)) {
            scope.launch {
                try {
                    delay(TILE_UPDATE_COALESCE_MS)
                    stitchedCache.evictAll()
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
                pruneDiskCache(
                    rootDir = File(appContext.cacheDir, "osm_tiles"),
                    protectedKeys = protectedTileKeysProvider?.invoke().orEmpty()
                )
            } catch (_: Exception) {}
        }
    }

    internal fun pruneDiskCache(
        rootDir: File,
        maxBytes: Long = MAX_DISK_CACHE_BYTES,
        maxAgeMs: Long = MAX_TILE_AGE_MS,
        nowMs: Long = System.currentTimeMillis(),
        protectedKeys: Set<String> = emptySet()
    ) {
        if (!rootDir.exists() || !rootDir.isDirectory) return
        val files = rootDir.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return

        fun extractKey(file: File): String? {
            val rel = try {
                file.relativeTo(rootDir).invariantSeparatorsPath
            } catch (_: Exception) {
                return null
            }
            val dotIdx = rel.lastIndexOf('.')
            return if (dotIdx > 0) rel.substring(0, dotIdx) else rel
        }

        var totalBytes = 0L
        val survivingEvictable = mutableListOf<File>()
        for (f in files) {
            val key = extractKey(f)
            val isProtected = key != null && protectedKeys.contains(key)
            val age = nowMs - f.lastModified()
            if (!isProtected && age > maxAgeMs) {
                f.delete()
            } else {
                totalBytes += f.length()
                if (!isProtected) {
                    survivingEvictable.add(f)
                }
            }
        }

        if (totalBytes > maxBytes && survivingEvictable.isNotEmpty()) {
            val targetBytes = (maxBytes * 8L) / 10L
            val oldestFirst = survivingEvictable.sortedBy { it.lastModified() }
            for (f in oldestFirst) {
                if (totalBytes <= targetBytes) break
                val len = f.length()
                if (f.delete()) {
                    totalBytes -= len
                }
            }
        }
    }

    /**
     * Synchronously downloads, decodes-validates, and atomically writes a tile to either the
     * persistent shared offline store (`filesDir/offline_map_tiles`) or the volatile cache.
     */
    suspend fun downloadAndSaveTileSync(
        context: Context,
        zoom: Int,
        x: Int,
        y: Int,
        isSatellite: Boolean,
        persistent: Boolean = true,
        bypassBackoff: Boolean = false
    ): TileDownloadOutcome {
        if (!isValidTileY(y, zoom)) return TileDownloadOutcome.PERMANENT_FAILURE
        val safeZoom = zoom.coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
        val wrappedX = wrapTileX(x, safeZoom)
        val key = tileKey(safeZoom, wrappedX, y, isSatellite)

        if (persistent) {
            val offlineFile = offlineTileFileFor(context, safeZoom, wrappedX, y, isSatellite)
            if (offlineFile.exists() && offlineFile.length() > 0L) {
                return TileDownloadOutcome.ALREADY_CACHED
            }
            // Promote any valid cached tile directly into persistent store without re-downloading!
            val existingVolatile = findLocalTileFile(context, safeZoom, wrappedX, y, isSatellite)
            if (existingVolatile != null && existingVolatile.absolutePath != offlineFile.absolutePath) {
                try {
                    val bmp = BitmapFactory.decodeFile(existingVolatile.absolutePath)
                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                        offlineFile.parentFile?.mkdirs()
                        val tmp = File(offlineFile.parentFile, "${offlineFile.name}.tmp")
                        existingVolatile.copyTo(tmp, overwrite = true)
                        if (!tmp.renameTo(offlineFile)) {
                            tmp.copyTo(offlineFile, overwrite = true)
                            tmp.delete()
                        }
                        memoryCache.put(key, bmp)
                        return TileDownloadOutcome.ALREADY_CACHED
                    } else {
                        existingVolatile.delete()
                    }
                } catch (_: Exception) {}
            }
        } else if (hasCachedTile(context, safeZoom, wrappedX, y, isSatellite)) {
            return TileDownloadOutcome.ALREADY_CACHED
        }

        if (!bypassBackoff && isTileInBackoff(key)) {
            return TileDownloadOutcome.TRANSIENT_FAILURE
        }

        return fetchSemaphore.withPermit {
            try {
                val request = Request.Builder()
                    .url(tileUrlFor(safeZoom, wrappedX, y, isSatellite))
                    .header("User-Agent", USER_AGENT)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    if (code == 429 || code == 503) {
                        val retrySec = response.header("Retry-After")?.toLongOrNull()?.coerceIn(5L, 120L) ?: 20L
                        recordTileFailure(
                            key = key,
                            isOfflineError = true,
                            explicitCooldownMs = retrySec * 1000L
                        )
                        return@withPermit TileDownloadOutcome.RATE_LIMITED
                    }
                    if (code == 400 || code == 404) {
                        recordTileFailure(key)
                        return@withPermit TileDownloadOutcome.PERMANENT_FAILURE
                    }
                    if (!response.isSuccessful) {
                        recordTileFailure(key)
                        return@withPermit TileDownloadOutcome.TRANSIENT_FAILURE
                    }
                    val bytes = response.body?.bytes()
                    if (bytes == null || !hasValidImageHeaderBytes(bytes)) {
                        recordTileFailure(key)
                        return@withPermit TileDownloadOutcome.TRANSIENT_FAILURE
                    }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp == null || bmp.width <= 0 || bmp.height <= 0) {
                        recordTileFailure(key)
                        return@withPermit TileDownloadOutcome.TRANSIENT_FAILURE
                    }
                    memoryCache.put(key, bmp)
                    tileFailureBackoff.remove(key)

                    val targetFile = if (persistent) {
                        offlineTileFileFor(context, safeZoom, wrappedX, y, isSatellite)
                    } else {
                        tileFileFor(context, safeZoom, wrappedX, y, isSatellite)
                    }
                    writeTileBytesAtomically(targetFile, bytes)
                    notifyTileUpdatedCoalesced()
                    TileDownloadOutcome.DOWNLOADED
                }
            } catch (e: Exception) {
                val isOffline = e is UnknownHostException ||
                    e is ConnectException ||
                    e is SocketTimeoutException
                recordTileFailure(key, isOfflineError = isOffline)
                TileDownloadOutcome.TRANSIENT_FAILURE
            }
        }
    }

    private fun writeTileBytesAtomically(targetFile: File, bytes: ByteArray) {
        targetFile.parentFile?.mkdirs()
        val tmpFile = File(targetFile.parentFile, "${targetFile.name}.${Thread.currentThread().id}.tmp")
        FileOutputStream(tmpFile).use { it.write(bytes) }
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
    }

    private fun fetchTileAsync(context: Context, zoom: Int, x: Int, y: Int, isSatellite: Boolean) {
        if (!isValidTileY(y, zoom)) return
        if (!isNetworkConnectedProvider(context)) return
        val wrappedX = wrapTileX(x, zoom)
        val key = tileKey(zoom, wrappedX, y, isSatellite)

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
                downloadAndSaveTileSync(
                    context = context,
                    zoom = zoom,
                    x = wrappedX,
                    y = y,
                    isSatellite = isSatellite,
                    persistent = false,
                    bypassBackoff = false
                )
            } finally {
                synchronized(pendingFetches) {
                    pendingFetches.remove(key)
                    _isLoading.value = pendingFetches.isNotEmpty()
                }
            }
        }
    }
}
