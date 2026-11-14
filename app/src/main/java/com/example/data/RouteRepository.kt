package com.example.data

import android.content.Context
import android.net.Uri
import com.example.geo.ChainageMatcher
import com.example.model.RouteData
import com.example.parser.KmzParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class RouteRepository(
    private val context: Context,
    private val routeDao: RouteDao,
    private val preferencesManager: PreferencesManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val LOG_TAG = "CTECS_KMZ_REPO"
    }

    private val _activeRouteData = MutableStateFlow<RouteData?>(null)
    val activeRouteData: StateFlow<RouteData?> = _activeRouteData.asStateFlow()

    val allRoutes = routeDao.getAllRoutes()

    // Mutex to prevent duplicate concurrent import jobs when user taps import rapidly
    private val importMutex = Mutex()

    @Volatile
    private var loadedSourcePath: String? = null
    @Volatile
    private var loadedSourceLength: Long = -1L
    @Volatile
    private var loadedSourceLastModified: Long = -1L

    data class RouteRefreshOutcome(
        val route: RouteData?,
        val reparsedFromFile: Boolean,
        val rebuiltSpatialCache: Boolean,
        val isValidGeometry: Boolean,
        val message: String
    )

    init {
        scope.launch {
            preferencesManager.activeRouteIdFlow.collect { activeId ->
                if (activeId != null) {
                    val current = _activeRouteData.value
                    if (current == null || current.id != activeId) {
                        val loaded = loadRouteById(activeId)
                        if (loaded == null) {
                            _activeRouteData.value = null
                        }
                    }
                } else {
                    _activeRouteData.value = null
                }
            }
        }
    }

    private fun logInfo(msg: String) {
        try {
            android.util.Log.i(LOG_TAG, msg)
        } catch (_: Throwable) {}
    }

    private fun logError(msg: String, err: Throwable? = null) {
        try {
            if (err != null) {
                android.util.Log.e(LOG_TAG, msg, err)
            } else {
                android.util.Log.e(LOG_TAG, msg)
            }
        } catch (_: Throwable) {}
    }

    suspend fun loadRouteById(routeId: String): RouteData? = withContext(Dispatchers.IO) {
        val entity = routeDao.getRouteById(routeId) ?: return@withContext null
        val filePath = entity.localFilePath
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists() && file.length() > 0L) {
                try {
                    val parsed = KmzParser.parse(file, entity.fileName)
                    val data = parsed.copy(
                        id = entity.id,
                        name = entity.name.ifBlank { parsed.name },
                        fileName = entity.fileName.ifBlank { parsed.fileName }
                    )
                    ChainageMatcher.clearCache()
                    loadedSourcePath = file.absolutePath
                    loadedSourceLength = file.length()
                    loadedSourceLastModified = file.lastModified()
                    _activeRouteData.value = data
                    logInfo("Loaded active route '${data.name}' (${data.fileName}) with ${data.markers.size} markers")
                    return@withContext data
                } catch (e: Exception) {
                    logError("Failed to load persisted route '${entity.fileName}': ${e.message}", e)
                }
            }
        }
        null
    }

    /**
     * Validates active KMZ/KML alignment without deleting files or needlessly re-parsing unchanged XML.
     * Re-parses ONLY if route is missing, geometry is invalid, or the local source file changed on disk.
     * Rebuilds ChainageMatcher spatial index when [forceRebuildCache] is true or cache is missing/stale.
     */
    suspend fun validateAndRefreshActiveRoute(
        forceRebuildCache: Boolean = false
    ): RouteRefreshOutcome = withContext(Dispatchers.IO) {
        importMutex.withLock {
            val activeId = _activeRouteData.value?.id ?: preferencesManager.activeRouteIdFlow.firstOrNull()
            val currentRoute = _activeRouteData.value
            val entity = if (activeId != null) routeDao.getRouteById(activeId) else null
            val sourceFile = entity?.localFilePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }

            val fileUnchanged = sourceFile != null &&
                loadedSourcePath == sourceFile.absolutePath &&
                loadedSourceLength == sourceFile.length() &&
                loadedSourceLastModified == sourceFile.lastModified()

            val currentGeometryValid = currentRoute != null && (
                currentRoute.alignments.any { align ->
                    align.points.size >= 2 && align.points.all {
                        !it.latitude.isNaN() && !it.longitude.isNaN()
                    }
                } || currentRoute.markers.isNotEmpty()
            )

            var reparsed = false
            var workingRoute = currentRoute

            if ((workingRoute == null || !currentGeometryValid || (sourceFile != null && !fileUnchanged)) && activeId != null && sourceFile != null) {
                try {
                    val parsed = KmzParser.parse(sourceFile, entity.fileName)
                    val preservedAlignId = workingRoute?.selectedAlignmentId
                    workingRoute = parsed.copy(
                        id = entity.id,
                        name = entity.name.ifBlank { parsed.name },
                        fileName = entity.fileName.ifBlank { parsed.fileName },
                        selectedAlignmentId = preservedAlignId?.takeIf { sel ->
                            parsed.alignments.any { it.id == sel }
                        }
                    )
                    loadedSourcePath = sourceFile.absolutePath
                    loadedSourceLength = sourceFile.length()
                    loadedSourceLastModified = sourceFile.lastModified()
                    _activeRouteData.value = workingRoute
                    reparsed = true
                } catch (e: Exception) {
                    logError("KMZ/KML validation re-parse failed (retaining last valid route): ${e.message}", e)
                }
            }

            if (workingRoute == null) {
                return@withContext RouteRefreshOutcome(
                    route = null,
                    reparsedFromFile = false,
                    rebuiltSpatialCache = false,
                    isValidGeometry = false,
                    message = "No active KMZ/KML alignment"
                )
            }

            val validAfterCheck = workingRoute.alignments.any { it.points.size >= 2 } || workingRoute.markers.isNotEmpty()
            val activeAlign = workingRoute.activeAlignment ?: workingRoute.alignments.firstOrNull()
            var rebuiltCache = false
            if (validAfterCheck && activeAlign != null && activeAlign.points.size >= 2) {
                val cacheValid = ChainageMatcher.isCacheValidFor(activeAlign, workingRoute.markers)
                if (forceRebuildCache || reparsed || !cacheValid) {
                    if (forceRebuildCache || reparsed) {
                        ChainageMatcher.clearCache()
                    }
                    ChainageMatcher.getOrCreateCachedGeometry(activeAlign, workingRoute.markers)
                    rebuiltCache = true
                }
            }

            RouteRefreshOutcome(
                route = workingRoute,
                reparsedFromFile = reparsed,
                rebuiltSpatialCache = rebuiltCache,
                isValidGeometry = validAfterCheck,
                message = when {
                    !validAfterCheck -> "Alignment geometry empty or invalid"
                    reparsed -> "Alignment reloaded & spatial cache rebuilt"
                    rebuiltCache -> "Alignment verified & spatial index refreshed"
                    else -> "Cached alignment & spatial index valid"
                }
            )
        }
    }

    suspend fun importRouteFromUri(uri: Uri, displayName: String): Result<RouteData> = withContext(Dispatchers.IO) {
        importMutex.withLock {
            var tempFile: File? = null
            try {
                logInfo("Starting KMZ/KML import from URI='$uri', displayName='$displayName'")

                // Take persistable read permission if supported by document provider
                if (uri.scheme == "content") {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                        // Not all SAF providers support persistable permissions; transient grant is still valid
                    }
                }

                // Resolve actual display name from ContentResolver
                val resolvedName = resolveFileNameFromUri(context, uri, displayName)
                val lowerExt = resolvedName.substringAfterLast('.', "").lowercase()
                if (lowerExt.isNotEmpty() && lowerExt != "kmz" && lowerExt != "kml" && lowerExt != "xml" && lowerExt != "zip") {
                    return@withLock Result.failure(
                        Exception("Unsupported file format '.$lowerExt'. Please select a valid .kmz or .kml file.")
                    )
                }

                val routesDir = File(context.filesDir, "imported_routes").apply { mkdirs() }
                val sanitizedName = resolvedName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                tempFile = File(routesDir, "staging_${System.currentTimeMillis()}_$sanitizedName")

                var copied = false
                if (uri.scheme == "file") {
                    val srcFile = uri.path?.let { File(it) }
                    if (srcFile != null && srcFile.exists() && srcFile.isFile) {
                        srcFile.copyTo(tempFile, overwrite = true)
                        copied = true
                    }
                }
                if (!copied) {
                    val inputStream = try {
                        context.contentResolver.openInputStream(uri)
                    } catch (secEx: SecurityException) {
                        tempFile.delete()
                        logError("Permission denied opening URI: $uri", secEx)
                        return@withLock Result.failure(
                            Exception("Storage permission denied for '$resolvedName'. Please re-select the file.")
                        )
                    } catch (e: Exception) {
                        tempFile.delete()
                        logError("Document provider failed to open URI: $uri", e)
                        return@withLock Result.failure(
                            Exception("Cannot open '$resolvedName' from storage provider: ${e.message}")
                        )
                    }

                    if (inputStream == null) {
                        tempFile.delete()
                        return@withLock Result.failure(
                            Exception("Document provider returned an empty stream for '$resolvedName'.")
                        )
                    }

                    inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                        copied = true
                    }
                }

                if (!copied || !tempFile.exists() || tempFile.length() == 0L) {
                    tempFile.delete()
                    return@withLock Result.failure(
                        Exception("Selected file '$resolvedName' is empty (0 bytes) or could not be read.")
                    )
                }

                logInfo("Stream copied to staging file (${tempFile.length()} bytes). Validating & parsing '$resolvedName'...")

                // Parse and validate BEFORE modifying any existing active alignment or database state
                val parsedRoute = KmzParser.parse(tempFile, resolvedName)

                val cleanResolvedBase = resolvedName.substringBeforeLast(".")
                val finalDisplayName = if (parsedRoute.name.isNotBlank() &&
                    !parsedRoute.name.equals("Model", ignoreCase = true) &&
                    !parsedRoute.name.equals("doc", ignoreCase = true) &&
                    !parsedRoute.name.equals("doc.kml", ignoreCase = true) &&
                    parsedRoute.name != sanitizedName
                ) {
                    parsedRoute.name
                } else {
                    cleanResolvedBase.ifBlank { parsedRoute.name.ifBlank { "Imported Alignment" } }
                }

                // Check if the same file was already imported previously to avoid duplicate database records
                val existingRoutes = routeDao.getAllRoutes().firstOrNull().orEmpty()
                val existingSameFile = existingRoutes.firstOrNull {
                    it.fileName.equals(resolvedName, ignoreCase = true)
                }

                val effectiveRouteId = existingSameFile?.id ?: parsedRoute.id
                val targetFile = File(routesDir, "${System.currentTimeMillis()}_$sanitizedName")
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                // Remove old file on disk if re-importing the same file
                if (existingSameFile?.localFilePath != null && existingSameFile.localFilePath != targetFile.absolutePath) {
                    try {
                        File(existingSameFile.localFilePath).delete()
                    } catch (_: Exception) {}
                }

                val finalRoute = parsedRoute.copy(
                    id = effectiveRouteId,
                    name = finalDisplayName,
                    fileName = resolvedName
                )

                val entity = RouteEntity(
                    id = finalRoute.id,
                    name = finalDisplayName,
                    fileName = resolvedName,
                    markerCount = finalRoute.markers.size,
                    startChainageMetres = finalRoute.startChainage?.metres,
                    endChainageMetres = finalRoute.endChainage?.metres,
                    minLat = finalRoute.bounds.minLat,
                    maxLat = finalRoute.bounds.maxLat,
                    minLng = finalRoute.bounds.minLng,
                    maxLng = finalRoute.bounds.maxLng,
                    importedAt = finalRoute.importedAt,
                    isActive = true,
                    localFilePath = targetFile.absolutePath
                )

                // Clear cached geometry from previous route before activating new route
                ChainageMatcher.clearCache()
                routeDao.deactivateAllRoutes()
                routeDao.insertRoute(entity)
                loadedSourcePath = targetFile.absolutePath
                loadedSourceLength = targetFile.length()
                loadedSourceLastModified = targetFile.lastModified()
                _activeRouteData.value = finalRoute
                preferencesManager.setActiveRouteId(finalRoute.id)

                logInfo(
                    "Successfully imported & activated '${finalRoute.fileName}' " +
                        "(id=${finalRoute.id}, markers=${finalRoute.markers.size}, " +
                        "range=${finalRoute.startChainage?.format()}..${finalRoute.endChainage?.format()})"
                )

                Result.success(finalRoute)
            } catch (e: Exception) {
                try {
                    tempFile?.delete()
                } catch (_: Exception) {}
                logError("KMZ/KML import failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private fun resolveFileNameFromUri(context: Context, uri: Uri, fallback: String): String {
        var resultName: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) resultName = name
                        }
                    }
                }
            } catch (_: Exception) {}
        } else if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) }
            if (file != null && file.name.isNotBlank()) {
                resultName = file.name
            }
        }

        val decodedFallback = try { Uri.decode(fallback) } catch (e: Exception) { fallback }
        val candidate = resultName ?: decodedFallback.ifBlank {
            uri.lastPathSegment?.let { try { Uri.decode(it) } catch (e: Exception) { it } }?.substringAfterLast('/') ?: "imported_route.kmz"
        }
        val cleanCandidate = candidate.substringAfterLast('/').substringAfterLast(':')
        return if (cleanCandidate.isNotBlank()) cleanCandidate else "imported_route.kmz"
    }

    suspend fun setActiveRoute(routeId: String) = withContext(Dispatchers.IO) {
        ChainageMatcher.clearCache()
        routeDao.deactivateAllRoutes()
        routeDao.activateRoute(routeId)
        preferencesManager.setActiveRouteId(routeId)
        loadRouteById(routeId)
    }

    suspend fun deleteRoute(routeId: String) = withContext(Dispatchers.IO) {
        val entity = routeDao.getRouteById(routeId)
        if (entity?.localFilePath != null) {
            try {
                File(entity.localFilePath).delete()
            } catch (_: Exception) {}
        }
        routeDao.deleteRouteById(routeId)
        ChainageMatcher.clearCache()
        if (_activeRouteData.value?.id == routeId) {
            loadedSourcePath = null
            loadedSourceLength = -1L
            loadedSourceLastModified = -1L
            _activeRouteData.value = null
            preferencesManager.setActiveRouteId(null)
        }
    }

    suspend fun getActiveRouteBytes(): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        val activeId = _activeRouteData.value?.id ?: preferencesManager.activeRouteIdFlow.firstOrNull()
        if (activeId != null) {
            val entity = routeDao.getRouteById(activeId)
            if (entity?.localFilePath != null) {
                val file = File(entity.localFilePath)
                if (file.exists() && file.isFile) {
                    val fileName = entity.fileName.ifBlank { file.name }
                    return@withContext Pair(fileName, file.readBytes())
                }
            }
        }
        null
    }

    suspend fun importRouteFromBytes(bytes: ByteArray, fileName: String, displayName: String): Result<RouteData> = withContext(Dispatchers.IO) {
        importMutex.withLock {
            var tempFile: File? = null
            try {
                if (bytes.isEmpty()) {
                    return@withLock Result.failure(Exception("Cannot import empty route file"))
                }
                val routesDir = File(context.filesDir, "imported_routes").apply { mkdirs() }
                val sanitizedName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                tempFile = File(routesDir, "staging_${System.currentTimeMillis()}_$sanitizedName")
                tempFile.writeBytes(bytes)

                val parsedRoute = KmzParser.parse(tempFile, fileName)

                val finalDisplayName = if (displayName.isNotBlank() && displayName != sanitizedName) {
                    displayName
                } else if (parsedRoute.name.isNotBlank() && parsedRoute.name != sanitizedName) {
                    parsedRoute.name
                } else {
                    fileName.substringBeforeLast(".")
                }

                val existingRoutes = routeDao.getAllRoutes().firstOrNull().orEmpty()
                val existingSameFile = existingRoutes.firstOrNull {
                    it.fileName.equals(fileName, ignoreCase = true)
                }
                val effectiveRouteId = existingSameFile?.id ?: parsedRoute.id

                val targetFile = File(routesDir, "${System.currentTimeMillis()}_$sanitizedName")
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                if (existingSameFile?.localFilePath != null && existingSameFile.localFilePath != targetFile.absolutePath) {
                    try {
                        File(existingSameFile.localFilePath).delete()
                    } catch (_: Exception) {}
                }

                val finalRoute = parsedRoute.copy(
                    id = effectiveRouteId,
                    name = finalDisplayName,
                    fileName = fileName
                )

                val entity = RouteEntity(
                    id = finalRoute.id,
                    name = finalDisplayName,
                    fileName = fileName,
                    markerCount = finalRoute.markers.size,
                    startChainageMetres = finalRoute.startChainage?.metres,
                    endChainageMetres = finalRoute.endChainage?.metres,
                    minLat = finalRoute.bounds.minLat,
                    maxLat = finalRoute.bounds.maxLat,
                    minLng = finalRoute.bounds.minLng,
                    maxLng = finalRoute.bounds.maxLng,
                    importedAt = finalRoute.importedAt,
                    isActive = true,
                    localFilePath = targetFile.absolutePath
                )

                ChainageMatcher.clearCache()
                routeDao.deactivateAllRoutes()
                routeDao.insertRoute(entity)
                loadedSourcePath = targetFile.absolutePath
                loadedSourceLength = targetFile.length()
                loadedSourceLastModified = targetFile.lastModified()
                _activeRouteData.value = finalRoute
                preferencesManager.setActiveRouteId(finalRoute.id)

                Result.success(finalRoute)
            } catch (e: Exception) {
                try {
                    tempFile?.delete()
                } catch (_: Exception) {}
                Result.failure(e)
            }
        }
    }
}
