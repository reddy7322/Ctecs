package com.example.data

import android.content.Context
import android.net.Uri
import com.example.model.RouteData
import com.example.parser.KmzParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class RouteRepository(
    private val context: Context,
    private val routeDao: RouteDao,
    private val preferencesManager: PreferencesManager,
    private val scope: CoroutineScope
) {
    private val _activeRouteData = MutableStateFlow<RouteData?>(null)
    val activeRouteData: StateFlow<RouteData?> = _activeRouteData.asStateFlow()

    val allRoutes = routeDao.getAllRoutes()

    init {
        // Load initial active route
        scope.launch {
            preferencesManager.activeRouteIdFlow.collect { activeId ->
                if (activeId != null) {
                    loadRouteById(activeId)
                } else {
                    // Check if there are any routes in db
                    val entity = withContext(Dispatchers.IO) {
                        // Check if bundled sample can be loaded initially if DB is empty
                        null
                    }
                }
            }
        }
    }

    suspend fun loadRouteById(routeId: String): RouteData? = withContext(Dispatchers.IO) {
        val entity = routeDao.getRouteById(routeId) ?: return@withContext null
        val filePath = entity.localFilePath
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                try {
                    file.inputStream().use { input ->
                        val data = KmzParser.parse(input, entity.fileName).copy(id = entity.id, name = entity.name)
                        _activeRouteData.value = data
                        return@withContext data
                    }
                } catch (ignored: Exception) {}
            }
        }
        null
    }

    suspend fun importRouteFromUri(uri: Uri, displayName: String): Result<RouteData> = withContext(Dispatchers.IO) {
        try {
            // Copy stream to app internal storage
            val routesDir = File(context.filesDir, "imported_routes").apply { mkdirs() }
            val sanitizedName = displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFile = File(routesDir, "${System.currentTimeMillis()}_$sanitizedName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open file stream"))

            // Parse file
            val parsedRoute = targetFile.inputStream().use { input ->
                KmzParser.parse(input, sanitizedName)
            }

            // Save in DB
            val entity = RouteEntity(
                id = parsedRoute.id,
                name = parsedRoute.name,
                fileName = displayName,
                markerCount = parsedRoute.markers.size,
                startChainageMetres = parsedRoute.startChainage?.metres,
                endChainageMetres = parsedRoute.endChainage?.metres,
                minLat = parsedRoute.bounds.minLat,
                maxLat = parsedRoute.bounds.maxLat,
                minLng = parsedRoute.bounds.minLng,
                maxLng = parsedRoute.bounds.maxLng,
                importedAt = parsedRoute.importedAt,
                isActive = true,
                localFilePath = targetFile.absolutePath
            )

            routeDao.deactivateAllRoutes()
            routeDao.insertRoute(entity)
            preferencesManager.setActiveRouteId(parsedRoute.id)
            _activeRouteData.value = parsedRoute

            Result.success(parsedRoute)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadBundledSampleRoute(): Result<RouteData> = withContext(Dispatchers.IO) {
        try {
            val assetName = "sample_nh58.kmz"
            val routesDir = File(context.filesDir, "imported_routes").apply { mkdirs() }
            val targetFile = File(routesDir, "NH-58_Muzzafarnagar_Haridwar.kmz")

            context.assets.open(assetName).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val parsedRoute = targetFile.inputStream().use { input ->
                KmzParser.parse(input, "NH-58-Muzzafarnagar-Haridwar.kmz")
            }

            val entity = RouteEntity(
                id = parsedRoute.id,
                name = "NH-58 Muzaffarnagar-Haridwar",
                fileName = "NH-58-Muzzafarnagar-Haridwar.kmz",
                markerCount = parsedRoute.markers.size,
                startChainageMetres = parsedRoute.startChainage?.metres,
                endChainageMetres = parsedRoute.endChainage?.metres,
                minLat = parsedRoute.bounds.minLat,
                maxLat = parsedRoute.bounds.maxLat,
                minLng = parsedRoute.bounds.minLng,
                maxLng = parsedRoute.bounds.maxLng,
                importedAt = parsedRoute.importedAt,
                isActive = true,
                localFilePath = targetFile.absolutePath
            )

            routeDao.deactivateAllRoutes()
            routeDao.insertRoute(entity)
            preferencesManager.setActiveRouteId(parsedRoute.id)
            _activeRouteData.value = parsedRoute

            Result.success(parsedRoute)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setActiveRoute(routeId: String) = withContext(Dispatchers.IO) {
        routeDao.deactivateAllRoutes()
        routeDao.activateRoute(routeId)
        preferencesManager.setActiveRouteId(routeId)
        loadRouteById(routeId)
    }

    suspend fun deleteRoute(routeId: String) = withContext(Dispatchers.IO) {
        val entity = routeDao.getRouteById(routeId)
        if (entity?.localFilePath != null) {
            File(entity.localFilePath).delete()
        }
        routeDao.deleteRouteById(routeId)
        if (_activeRouteData.value?.id == routeId) {
            _activeRouteData.value = null
            preferencesManager.setActiveRouteId(null)
        }
    }
}
