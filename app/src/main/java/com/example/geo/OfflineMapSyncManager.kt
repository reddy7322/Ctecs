package com.example.geo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.RouteData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class OfflineSyncStatus {
    IDLE,
    PREPARING,
    SYNCING,
    PAUSED,
    WAITING_FOR_WIFI,
    WAITING_FOR_NETWORK,
    COMPLETE,
    PARTIAL_ERROR
}

data class TileCoord(
    val zoom: Int,
    val x: Int,
    val y: Int
) {
    fun tilePathKey(): String = "$zoom/$x/$y"
    fun storeKey(isSatellite: Boolean): String {
        val typeFolder = if (isSatellite) "satellite" else "standard"
        return "$typeFolder/$zoom/$x/$y"
    }
}

data class NetworkAvailability(
    val isConnected: Boolean,
    val isWifiOrUnmetered: Boolean
)

data class OfflineMapSyncState(
    val activeRouteId: String? = null,
    val activeRouteName: String = "",
    val mapType: String = "standard",
    val status: OfflineSyncStatus = OfflineSyncStatus.IDLE,
    val totalTiles: Int = 0,
    val cachedTiles: Int = 0,
    val reusedTiles: Int = 0,
    val downloadedThisSession: Int = 0,
    val failedTiles: Int = 0,
    val bytesOnDisk: Long = 0L,
    val coverageBounds: GeoBounds? = null,
    val lastError: String? = null,
    val autoPreloadEnabled: Boolean = true,
    val wifiOnlyEnabled: Boolean = false
) {
    val missingTiles: Int get() = (totalTiles - cachedTiles).coerceAtLeast(0)
    val progressFraction: Float
        get() = if (totalTiles <= 0) 0f else (cachedTiles.toFloat() / totalTiles.toFloat()).coerceIn(0f, 1f)
    val isSyncing: Boolean get() = status == OfflineSyncStatus.PREPARING || status == OfflineSyncStatus.SYNCING
    val formattedBytes: String
        get() = when {
            bytesOnDisk >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytesOnDisk / (1024.0 * 1024.0))
            bytesOnDisk >= 1024L -> String.format(Locale.US, "%.0f KB", bytesOnDisk / 1024.0)
            else -> "$bytesOnDisk B"
        }
}

/**
 * Pure JVM-testable KMZ/KML corridor tile planner.
 * Computes the exact Web Mercator tiles covering all highway alignments, segments, nested markers,
 * and a clean surrounding buffer — without downloading unrelated areas or excessive axis-aligned grids.
 */
object CorridorTilePlanner {
    private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

    // Navigation & 25m close-range viewing zoom targets (ordered by strategic importance)
    data class ZoomBufferSpec(val zoom: Int, val bufferMetres: Double)

    val DEFAULT_ZOOM_SPECS = listOf(
        ZoomBufferSpec(zoom = 15, bufferMetres = 420.0), // Corridor overview
        ZoomBufferSpec(zoom = 16, bufferMetres = 320.0), // Navigation zoom
        ZoomBufferSpec(zoom = 17, bufferMetres = 220.0), // Turn/junction approach
        ZoomBufferSpec(zoom = 13, bufferMetres = 550.0), // Broad route overview
        ZoomBufferSpec(zoom = 18, bufferMetres = 140.0)  // 25m initial close-range viewing base
    )

    const val DEFAULT_MAX_TILES_PER_PROJECT = 5500

    fun computeCorridorTiles(
        routeData: RouteData,
        zoomSpecs: List<ZoomBufferSpec> = DEFAULT_ZOOM_SPECS,
        maxTilesBudget: Int = DEFAULT_MAX_TILES_PER_PROJECT
    ): List<TileCoord> {
        val result = LinkedHashSet<TileCoord>(256)
        val hasAlignments = routeData.alignments.any { it.points.isNotEmpty() }
        val hasMarkers = routeData.markers.isNotEmpty()

        for (spec in zoomSpecs) {
            if (result.size >= maxTilesBudget) break
            val z = spec.zoom.coerceIn(OsmTileManager.MIN_TILE_ZOOM, OsmTileManager.MAX_TILE_ZOOM)
            val numTiles = 1 shl z
            val refLat = routeData.bounds.center.latitude.coerceIn(-85.0, 85.0)
            val metersPerTile = ((EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(refLat)).coerceAtLeast(0.1)) / numTiles)
                .coerceAtLeast(1.0)
            val bufferTiles = ceil(spec.bufferMetres / metersPerTile).toInt().coerceIn(0, 3)

            fun addTileWithBuffer(rawTx: Int, rawTy: Int) {
                for (dx in -bufferTiles..bufferTiles) {
                    val wx = OsmTileManager.wrapTileX(rawTx + dx, z)
                    for (dy in -bufferTiles..bufferTiles) {
                        val ty = rawTy + dy
                        if (OsmTileManager.isValidTileY(ty, z)) {
                            if (result.size >= maxTilesBudget) return
                            result.add(TileCoord(zoom = z, x = wx, y = ty))
                        }
                    }
                }
            }

            // 1. Walk EVERY alignment polyline (MAIN, LHS, RHS, SERVICE, nested folders)
            for (align in routeData.alignments) {
                val pts = align.points
                if (pts.isEmpty()) continue
                if (pts.size == 1) {
                    val tx = floor(OsmTileManager.lonToTileX(pts[0].longitude, z)).toInt()
                    val ty = floor(OsmTileManager.latToTileY(pts[0].latitude, z)).toInt()
                    addTileWithBuffer(tx, ty)
                    continue
                }
                for (i in 0 until pts.size - 1) {
                    if (result.size >= maxTilesBudget) break
                    val p0 = pts[i]
                    val p1 = pts[i + 1]
                    var lon0 = p0.longitude
                    var lon1 = p1.longitude
                    val dLon = lon1 - lon0
                    if (abs(dLon) > 180.0) {
                        if (dLon > 0) lon0 += 360.0 else lon1 += 360.0
                    }

                    val x0 = OsmTileManager.lonToTileX(lon0, z)
                    val y0 = OsmTileManager.latToTileY(p0.latitude, z)
                    val x1 = OsmTileManager.lonToTileX(lon1, z)
                    val y1 = OsmTileManager.latToTileY(p1.latitude, z)
                    val tileDist = max(abs(x1 - x0), abs(y1 - y0))
                    val steps = ceil(tileDist * 2.5).toInt().coerceIn(1, 2048)

                    for (s in 0..steps) {
                        if (result.size >= maxTilesBudget) break
                        val t = s.toDouble() / steps.toDouble()
                        val sampleLon = OsmTileManager.wrapLongitude(lon0 + (lon1 - lon0) * t)
                        val sampleLat = p0.latitude + (p1.latitude - p0.latitude) * t
                        val tx = floor(OsmTileManager.lonToTileX(sampleLon, z)).toInt()
                        val ty = floor(OsmTileManager.latToTileY(sampleLat, z)).toInt()
                        addTileWithBuffer(tx, ty)
                    }
                }
            }

            // 2. Include all KMZ/KML markers (ensures marker-only or off-corridor placemark folders are covered)
            if (hasMarkers) {
                val stride = if (hasAlignments && routeData.markers.size > 400) {
                    (routeData.markers.size / 400).coerceAtLeast(1)
                } else {
                    1
                }
                var idx = 0
                while (idx < routeData.markers.size && result.size < maxTilesBudget) {
                    val m = routeData.markers[idx].point
                    val tx = floor(OsmTileManager.lonToTileX(m.longitude, z)).toInt()
                    val ty = floor(OsmTileManager.latToTileY(m.latitude, z)).toInt()
                    addTileWithBuffer(tx, ty)
                    idx += stride
                }
            }

            // 3. Bounds fallback only if a route had neither alignments nor markers
            if (!hasAlignments && !hasMarkers) {
                val b = routeData.bounds
                val minTx = floor(OsmTileManager.lonToTileX(b.minLng, z)).toInt()
                val maxTx = floor(OsmTileManager.lonToTileX(b.maxLng, z)).toInt()
                val minTy = floor(OsmTileManager.latToTileY(b.maxLat, z)).toInt().coerceAtLeast(0)
                val maxTy = floor(OsmTileManager.latToTileY(b.minLat, z)).toInt().coerceAtMost(numTiles - 1)
                for (tx in minTx..maxTx) {
                    for (ty in minTy..maxTy) {
                        if (result.size >= maxTilesBudget) break
                        addTileWithBuffer(tx, ty)
                    }
                }
            }
        }
        return result.toList()
    }
}

/**
 * Coordinates KMZ/KML-driven background offline Mini Map synchronization across the entire app.
 * Uses a single shared persistent tile cache (`filesDir/offline_map_tiles`) while isolating each
 * project's coverage manifest (`filesDir/offline_map_projects`) so project switches reuse existing tiles
 * cleanly without leaking one project's coverage metrics into another.
 */
class OfflineMapSyncManager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val tileDownloader: suspend (Context, Int, Int, Int, Boolean) -> OsmTileManager.TileDownloadOutcome = { ctx, z, x, y, sat ->
        OsmTileManager.downloadAndSaveTileSync(
            context = ctx,
            zoom = z,
            x = x,
            y = y,
            isSatellite = sat,
            persistent = true,
            bypassBackoff = true
        )
    },
    private val networkChecker: (Context) -> NetworkAvailability = { ctx -> checkDeviceNetwork(ctx) },
    private val throttleDelayMs: Long = 55L
) {
    private val _syncState = MutableStateFlow(OfflineMapSyncState())
    val syncState: StateFlow<OfflineMapSyncState> = _syncState.asStateFlow()

    @Volatile
    private var currentRoute: RouteData? = null
    @Volatile
    private var currentMapType: String = "standard"
    @Volatile
    private var autoPreloadEnabled: Boolean = true
    @Volatile
    private var wifiOnlyEnabled: Boolean = false
    @Volatile
    private var activeJob: Job? = null
    @Volatile
    private var lastAppContext: Context? = null

    private val lock = Any()

    init {
        OsmTileManager.protectedTileKeysProvider = {
            lastAppContext?.let { getAllManifestProtectedKeys(it) }.orEmpty()
        }
    }

    fun updatePreferences(
        context: Context,
        autoPreload: Boolean,
        wifiOnly: Boolean
    ) {
        lastAppContext = context.applicationContext
        val changedWifi = wifiOnlyEnabled != wifiOnly
        val changedPreload = autoPreloadEnabled != autoPreload
        autoPreloadEnabled = autoPreload
        wifiOnlyEnabled = wifiOnly
        _syncState.value = _syncState.value.copy(
            autoPreloadEnabled = autoPreload,
            wifiOnlyEnabled = wifiOnly
        )
        val route = currentRoute ?: return
        if ((changedPreload && autoPreload) || (changedWifi && !wifiOnly)) {
            syncRoute(context, route, currentMapType, forceStart = false)
        } else if (changedPreload && !autoPreload && _syncState.value.isSyncing) {
            pauseSync(context)
        }
    }

    fun syncRoute(
        context: Context,
        routeData: RouteData?,
        mapType: String = currentMapType,
        forceStart: Boolean = false
    ) {
        val appCtx = context.applicationContext
        lastAppContext = appCtx
        val normalizedMapType = if (mapType.equals("satellite", ignoreCase = true)) "satellite" else "standard"

        synchronized(lock) {
            currentRoute = routeData
            currentMapType = normalizedMapType
            activeJob?.cancel()
            activeJob = null
        }

        if (routeData == null) {
            _syncState.value = OfflineMapSyncState(
                mapType = normalizedMapType,
                status = OfflineSyncStatus.IDLE,
                autoPreloadEnabled = autoPreloadEnabled,
                wifiOnlyEnabled = wifiOnlyEnabled
            )
            return
        }

        val job = scope.launch(ioDispatcher) {
            executeSyncForRoute(
                context = appCtx,
                routeData = routeData,
                mapType = normalizedMapType,
                forceStart = forceStart
            )
        }
        synchronized(lock) {
            activeJob = job
        }
    }

    fun pauseSync(context: Context) {
        val appCtx = context.applicationContext
        lastAppContext = appCtx
        val route = currentRoute ?: return
        setRoutePausedFlag(appCtx, route.id, paused = true)
        synchronized(lock) {
            activeJob?.cancel()
            activeJob = null
        }
        val prev = _syncState.value
        if (prev.activeRouteId == route.id) {
            _syncState.value = prev.copy(status = OfflineSyncStatus.PAUSED, lastError = null)
        }
    }

    fun resumeSync(context: Context) {
        val appCtx = context.applicationContext
        lastAppContext = appCtx
        val route = currentRoute ?: return
        setRoutePausedFlag(appCtx, route.id, paused = false)
        syncRoute(appCtx, route, currentMapType, forceStart = true)
    }

    fun retrySync(context: Context) {
        val appCtx = context.applicationContext
        lastAppContext = appCtx
        val route = currentRoute ?: return
        setRoutePausedFlag(appCtx, route.id, paused = false)
        OsmTileManager.clearFailureBackoff()
        syncRoute(appCtx, route, currentMapType, forceStart = true)
    }

    /**
     * Deletes the offline coverage manifest for [routeId] and cleans up ONLY tiles that are NOT
     * referenced by any other active/retained project manifest (safe shared cache reuse).
     */
    fun deleteProjectOfflineData(context: Context, routeId: String) {
        val appCtx = context.applicationContext
        lastAppContext = appCtx
        if (currentRoute?.id == routeId) {
            synchronized(lock) {
                activeJob?.cancel()
                activeJob = null
                currentRoute = null
            }
            _syncState.value = OfflineMapSyncState(
                mapType = currentMapType,
                status = OfflineSyncStatus.IDLE,
                autoPreloadEnabled = autoPreloadEnabled,
                wifiOnlyEnabled = wifiOnlyEnabled
            )
        }
        setRoutePausedFlag(appCtx, routeId, paused = false)
        manifestFileFor(appCtx, routeId).delete()
        pruneUnreferencedOfflineTiles(appCtx, keepRouteIds = null)
    }

    /**
     * Removes manifests and tiles from inactive projects while strictly preserving all tiles
     * belonging to [activeRouteId] (even overlapping tiles shared with other routes).
     */
    fun clearInactiveProjectCaches(context: Context, activeRouteId: String?) {
        val appCtx = context.applicationContext
        lastAppContext = appCtx
        val dir = manifestsDir(appCtx)
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                if (file.extension == "json") {
                    val storedId = try {
                        JSONObject(file.readText()).optString("routeId", file.nameWithoutExtension)
                    } catch (_: Exception) {
                        file.nameWithoutExtension
                    }
                    if (storedId != activeRouteId) {
                        file.delete()
                    }
                }
            }
        }
        val keepSet = if (activeRouteId != null) setOf(activeRouteId) else emptySet()
        pruneUnreferencedOfflineTiles(appCtx, keepRouteIds = keepSet)
        val route = currentRoute
        if (route != null && route.id == activeRouteId) {
            syncRoute(appCtx, route, currentMapType, forceStart = false)
        }
    }

    private suspend fun executeSyncForRoute(
        context: Context,
        routeData: RouteData,
        mapType: String,
        forceStart: Boolean
    ) {
        val isSatellite = mapType == "satellite"
        val isExplicitlyPaused = !forceStart && isRoutePausedFlag(context, routeData.id)

        _syncState.value = OfflineMapSyncState(
            activeRouteId = routeData.id,
            activeRouteName = routeData.name,
            mapType = mapType,
            status = OfflineSyncStatus.PREPARING,
            coverageBounds = routeData.bounds,
            autoPreloadEnabled = autoPreloadEnabled,
            wifiOnlyEnabled = wifiOnlyEnabled
        )

        val corridorTiles = CorridorTilePlanner.computeCorridorTiles(routeData)
        saveProjectManifest(context, routeData, mapType, corridorTiles)

        val missing = ArrayList<TileCoord>(corridorTiles.size)
        var reusedCount = 0
        var existingBytes = 0L

        for (tile in corridorTiles) {
            if (!kotlin.coroutines.coroutineContext.isActive) return
            val existingFile = OsmTileManager.findLocalTileFile(
                context = context,
                zoom = tile.zoom,
                x = tile.x,
                y = tile.y,
                isSatellite = isSatellite
            )
            if (existingFile != null && existingFile.length() > 0L) {
                val persistentFile = OsmTileManager.offlineTileFileFor(
                    context = context,
                    zoom = tile.zoom,
                    x = tile.x,
                    y = tile.y,
                    isSatellite = isSatellite
                )
                if (existingFile.absolutePath != persistentFile.absolutePath && !persistentFile.exists()) {
                    try {
                        persistentFile.parentFile?.mkdirs()
                        existingFile.copyTo(persistentFile, overwrite = true)
                    } catch (_: Exception) {}
                }
                reusedCount++
                existingBytes += persistentFile.takeIf { it.exists() }?.length() ?: existingFile.length()
            } else {
                missing.add(tile)
            }
        }

        val totalCount = corridorTiles.size
        if (missing.isEmpty()) {
            _syncState.value = OfflineMapSyncState(
                activeRouteId = routeData.id,
                activeRouteName = routeData.name,
                mapType = mapType,
                status = OfflineSyncStatus.COMPLETE,
                totalTiles = totalCount,
                cachedTiles = totalCount,
                reusedTiles = reusedCount,
                downloadedThisSession = 0,
                failedTiles = 0,
                bytesOnDisk = existingBytes,
                coverageBounds = routeData.bounds,
                lastError = null,
                autoPreloadEnabled = autoPreloadEnabled,
                wifiOnlyEnabled = wifiOnlyEnabled
            )
            OsmTileManager.notifyOfflineTilesAvailable()
            return
        }

        if (isExplicitlyPaused || (!autoPreloadEnabled && !forceStart)) {
            _syncState.value = OfflineMapSyncState(
                activeRouteId = routeData.id,
                activeRouteName = routeData.name,
                mapType = mapType,
                status = OfflineSyncStatus.PAUSED,
                totalTiles = totalCount,
                cachedTiles = reusedCount,
                reusedTiles = reusedCount,
                downloadedThisSession = 0,
                failedTiles = 0,
                bytesOnDisk = existingBytes,
                coverageBounds = routeData.bounds,
                lastError = null,
                autoPreloadEnabled = autoPreloadEnabled,
                wifiOnlyEnabled = wifiOnlyEnabled
            )
            return
        }

        val net = networkChecker(context)
        if (!net.isConnected) {
            _syncState.value = OfflineMapSyncState(
                activeRouteId = routeData.id,
                activeRouteName = routeData.name,
                mapType = mapType,
                status = OfflineSyncStatus.WAITING_FOR_NETWORK,
                totalTiles = totalCount,
                cachedTiles = reusedCount,
                reusedTiles = reusedCount,
                downloadedThisSession = 0,
                failedTiles = 0,
                bytesOnDisk = existingBytes,
                coverageBounds = routeData.bounds,
                lastError = "No internet connection — cached tiles active",
                autoPreloadEnabled = autoPreloadEnabled,
                wifiOnlyEnabled = wifiOnlyEnabled
            )
            return
        }
        if (wifiOnlyEnabled && !net.isWifiOrUnmetered) {
            _syncState.value = OfflineMapSyncState(
                activeRouteId = routeData.id,
                activeRouteName = routeData.name,
                mapType = mapType,
                status = OfflineSyncStatus.WAITING_FOR_WIFI,
                totalTiles = totalCount,
                cachedTiles = reusedCount,
                reusedTiles = reusedCount,
                downloadedThisSession = 0,
                failedTiles = 0,
                bytesOnDisk = existingBytes,
                coverageBounds = routeData.bounds,
                lastError = "Waiting for Wi-Fi (Wi-Fi only preload enabled)",
                autoPreloadEnabled = autoPreloadEnabled,
                wifiOnlyEnabled = wifiOnlyEnabled
            )
            return
        }

        var downloadedCount = 0
        var failedCount = 0
        var totalBytes = existingBytes
        var consecutiveTransientFailures = 0
        var lastErrMsg: String? = null

        _syncState.value = OfflineMapSyncState(
            activeRouteId = routeData.id,
            activeRouteName = routeData.name,
            mapType = mapType,
            status = OfflineSyncStatus.SYNCING,
            totalTiles = totalCount,
            cachedTiles = reusedCount,
            reusedTiles = reusedCount,
            downloadedThisSession = 0,
            failedTiles = 0,
            bytesOnDisk = totalBytes,
            coverageBounds = routeData.bounds,
            lastError = null,
            autoPreloadEnabled = autoPreloadEnabled,
            wifiOnlyEnabled = wifiOnlyEnabled
        )

        for ((index, tile) in missing.withIndex()) {
            if (!kotlin.coroutines.coroutineContext.isActive) return

            if (isRoutePausedFlag(context, routeData.id)) {
                _syncState.value = _syncState.value.copy(status = OfflineSyncStatus.PAUSED)
                return
            }

            if (index % 15 == 0 && index > 0) {
                val liveNet = networkChecker(context)
                if (!liveNet.isConnected) {
                    _syncState.value = _syncState.value.copy(
                        status = OfflineSyncStatus.WAITING_FOR_NETWORK,
                        lastError = "Connection lost during sync — progress preserved"
                    )
                    return
                }
                if (wifiOnlyEnabled && !liveNet.isWifiOrUnmetered) {
                    _syncState.value = _syncState.value.copy(
                        status = OfflineSyncStatus.WAITING_FOR_WIFI,
                        lastError = "Switched off Wi-Fi — sync paused automatically"
                    )
                    return
                }
            }

            val outcome = try {
                tileDownloader(context, tile.zoom, tile.x, tile.y, isSatellite)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                OsmTileManager.TileDownloadOutcome.TRANSIENT_FAILURE
            }

            when (outcome) {
                OsmTileManager.TileDownloadOutcome.ALREADY_CACHED -> {
                    reusedCount++
                    consecutiveTransientFailures = 0
                }
                OsmTileManager.TileDownloadOutcome.DOWNLOADED -> {
                    downloadedCount++
                    consecutiveTransientFailures = 0
                    val f = OsmTileManager.offlineTileFileFor(context, tile.zoom, tile.x, tile.y, isSatellite)
                    if (f.exists()) totalBytes += f.length()
                }
                OsmTileManager.TileDownloadOutcome.RATE_LIMITED -> {
                    failedCount++
                    consecutiveTransientFailures++
                    lastErrMsg = "Tile server rate-limited; backing off briefly"
                    if (consecutiveTransientFailures >= 3) {
                        _syncState.value = _syncState.value.copy(
                            status = OfflineSyncStatus.PARTIAL_ERROR,
                            cachedTiles = reusedCount + downloadedCount,
                            reusedTiles = reusedCount,
                            downloadedThisSession = downloadedCount,
                            failedTiles = failedCount + (missing.size - index - 1),
                            bytesOnDisk = totalBytes,
                            lastError = lastErrMsg
                        )
                        return
                    }
                    delay(800L)
                }
                OsmTileManager.TileDownloadOutcome.TRANSIENT_FAILURE,
                OsmTileManager.TileDownloadOutcome.PERMANENT_FAILURE -> {
                    failedCount++
                    consecutiveTransientFailures++
                    lastErrMsg = "Some corridor tiles could not be downloaded"
                    if (consecutiveTransientFailures >= 6) {
                        val remainingNet = networkChecker(context)
                        val nextStatus = if (!remainingNet.isConnected) {
                            OfflineSyncStatus.WAITING_FOR_NETWORK
                        } else {
                            OfflineSyncStatus.PARTIAL_ERROR
                        }
                        _syncState.value = _syncState.value.copy(
                            status = nextStatus,
                            cachedTiles = reusedCount + downloadedCount,
                            reusedTiles = reusedCount,
                            downloadedThisSession = downloadedCount,
                            failedTiles = failedCount + (missing.size - index - 1),
                            bytesOnDisk = totalBytes,
                            lastError = lastErrMsg
                        )
                        return
                    }
                }
            }

            val cachedSoFar = (reusedCount + downloadedCount).coerceAtMost(totalCount)
            if (index % 4 == 0 || index == missing.lastIndex) {
                _syncState.value = OfflineMapSyncState(
                    activeRouteId = routeData.id,
                    activeRouteName = routeData.name,
                    mapType = mapType,
                    status = OfflineSyncStatus.SYNCING,
                    totalTiles = totalCount,
                    cachedTiles = cachedSoFar,
                    reusedTiles = reusedCount,
                    downloadedThisSession = downloadedCount,
                    failedTiles = failedCount,
                    bytesOnDisk = totalBytes,
                    coverageBounds = routeData.bounds,
                    lastError = lastErrMsg,
                    autoPreloadEnabled = autoPreloadEnabled,
                    wifiOnlyEnabled = wifiOnlyEnabled
                )
            }

            if (throttleDelayMs > 0L && outcome == OsmTileManager.TileDownloadOutcome.DOWNLOADED) {
                delay(throttleDelayMs)
            }
        }

        val finalCached = (reusedCount + downloadedCount).coerceAtMost(totalCount)
        val finalStatus = if (failedCount == 0) {
            OfflineSyncStatus.COMPLETE
        } else {
            OfflineSyncStatus.PARTIAL_ERROR
        }
        _syncState.value = OfflineMapSyncState(
            activeRouteId = routeData.id,
            activeRouteName = routeData.name,
            mapType = mapType,
            status = finalStatus,
            totalTiles = totalCount,
            cachedTiles = finalCached,
            reusedTiles = reusedCount,
            downloadedThisSession = downloadedCount,
            failedTiles = failedCount,
            bytesOnDisk = totalBytes,
            coverageBounds = routeData.bounds,
            lastError = if (failedCount > 0) lastErrMsg else null,
            autoPreloadEnabled = autoPreloadEnabled,
            wifiOnlyEnabled = wifiOnlyEnabled
        )
        OsmTileManager.notifyOfflineTilesAvailable()
    }

    private fun manifestsDir(context: Context): File = File(context.filesDir, "offline_map_projects")

    private fun manifestFileFor(context: Context, routeId: String): File {
        val safeId = routeId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(manifestsDir(context), "$safeId.json")
    }

    private fun prefsFile(context: Context): File = File(manifestsDir(context), "sync_prefs.state")

    private fun isRoutePausedFlag(context: Context, routeId: String): Boolean {
        return try {
            val file = prefsFile(context)
            if (!file.exists()) return false
            val obj = JSONObject(file.readText())
            val pausedArr = obj.optJSONArray("pausedIds") ?: return false
            for (i in 0 until pausedArr.length()) {
                if (pausedArr.optString(i) == routeId) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun setRoutePausedFlag(context: Context, routeId: String, paused: Boolean) {
        try {
            val dir = manifestsDir(context).apply { mkdirs() }
            val file = prefsFile(context)
            val set = LinkedHashSet<String>()
            if (file.exists()) {
                val obj = JSONObject(file.readText())
                val arr = obj.optJSONArray("pausedIds")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val id = arr.optString(i)
                        if (id.isNotBlank()) set.add(id)
                    }
                }
            }
            if (paused) set.add(routeId) else set.remove(routeId)
            val out = JSONObject().apply {
                put("pausedIds", JSONArray(set.toList()))
            }
            val tmp = File(dir, "sync_prefs.state.tmp")
            tmp.writeText(out.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {}
    }

    private fun saveProjectManifest(
        context: Context,
        routeData: RouteData,
        mapType: String,
        tiles: List<TileCoord>
    ) {
        try {
            val dir = manifestsDir(context).apply { mkdirs() }
            val target = manifestFileFor(context, routeData.id)
            val json = JSONObject().apply {
                put("routeId", routeData.id)
                put("routeName", routeData.name)
                put("fileName", routeData.fileName)
                put("mapType", mapType)
                put("minLat", routeData.bounds.minLat)
                put("minLng", routeData.bounds.minLng)
                put("maxLat", routeData.bounds.maxLat)
                put("maxLng", routeData.bounds.maxLng)
                put("tileCount", tiles.size)
                put("tiles", JSONArray(tiles.map { it.tilePathKey() }))
            }
            val tmp = File(dir, "${target.name}.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {}
    }

    /**
     * Reads tile keys strictly belonging to [routeId]'s saved manifest (project-isolated inspection).
     */
    fun getProjectManifestTileKeys(context: Context, routeId: String): Set<String> {
        val f = manifestFileFor(context, routeId)
        if (!f.exists()) return emptySet()
        return try {
            val obj = JSONObject(f.readText())
            val mapType = obj.optString("mapType", "standard")
            val arr = obj.optJSONArray("tiles") ?: return emptySet()
            val out = HashSet<String>(arr.length())
            for (i in 0 until arr.length()) {
                val pathKey = arr.optString(i)
                if (pathKey.isNotBlank()) out.add("$mapType/$pathKey")
            }
            out
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun getAllManifestProtectedKeys(context: Context, onlyRouteIds: Set<String>? = null): Set<String> {
        val dir = manifestsDir(context)
        if (!dir.exists()) return emptySet()
        val out = HashSet<String>()
        dir.listFiles()?.forEach { file ->
            if (file.extension == "json") {
                try {
                    val obj = JSONObject(file.readText())
                    val routeId = obj.optString("routeId", file.nameWithoutExtension)
                    if (onlyRouteIds == null || onlyRouteIds.contains(routeId)) {
                        val mapType = obj.optString("mapType", "standard")
                        val arr = obj.optJSONArray("tiles")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val pathKey = arr.optString(i)
                                if (pathKey.isNotBlank()) {
                                    out.add("$mapType/$pathKey")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return out
    }

    private fun pruneUnreferencedOfflineTiles(context: Context, keepRouteIds: Set<String>?) {
        val protected = getAllManifestProtectedKeys(context, keepRouteIds)
        val offlineRoot = OsmTileManager.offlineRootDir(context)
        if (!offlineRoot.exists()) return
        offlineRoot.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = try {
                f.relativeTo(offlineRoot).invariantSeparatorsPath
            } catch (_: Exception) {
                return@forEach
            }
            val dotIdx = rel.lastIndexOf('.')
            val key = if (dotIdx > 0) rel.substring(0, dotIdx) else rel
            if (!protected.contains(key)) {
                f.delete()
            }
        }
    }

    companion object {
        val shared: OfflineMapSyncManager by lazy { OfflineMapSyncManager() }

        fun checkDeviceNetwork(context: Context): NetworkAvailability {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return NetworkAvailability(isConnected = true, isWifiOrUnmetered = false)
                val active = cm.activeNetwork ?: return NetworkAvailability(isConnected = false, isWifiOrUnmetered = false)
                val caps = cm.getNetworkCapabilities(active)
                    ?: return NetworkAvailability(isConnected = false, isWifiOrUnmetered = false)
                val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val wifiOrUnmetered = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                NetworkAvailability(isConnected = connected, isWifiOrUnmetered = wifiOrUnmetered)
            } catch (_: Exception) {
                NetworkAvailability(isConnected = true, isWifiOrUnmetered = false)
            }
        }
    }
}
