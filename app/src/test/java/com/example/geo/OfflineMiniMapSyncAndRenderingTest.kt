package com.example.geo

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.MiniMapOrientationMode
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.ui.camera.MiniMapPreview
import com.example.ui.home.InteractiveSmartMap
import com.example.ui.map.MiniMapViewportMath
import com.example.ui.map.OfflineMapSyncStatusStrip
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineMiniMapSyncAndRenderingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        OsmTileManager.clearMemoryCache()
        OsmTileManager.invalidateStitchedCache()
        OsmTileManager.clearFailureBackoff()
        OsmTileManager.offlineRootDir(app).deleteRecursively()
        File(app.cacheDir, "osm_tiles").deleteRecursively()
        File(app.filesDir, "offline_map_projects").deleteRecursively()
    }

    @After
    fun tearDown() {
        OsmTileManager.clearMemoryCache()
        OsmTileManager.invalidateStitchedCache()
        OsmTileManager.protectedTileKeysProvider = null
        OsmTileManager.isNetworkConnectedProvider = { ctx ->
            OfflineMapSyncManager.checkDeviceNetwork(ctx).isConnected
        }
        OsmTileManager.offlineRootDir(app).deleteRecursively()
        File(app.filesDir, "offline_map_projects").deleteRecursively()
    }

    private fun validPngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.LTGRAY)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun routeAlpha(): RouteData {
        // Highway segment A (diagonal multi-segment + marker + bounds)
        val p1 = GeoPoint(28.6100, 77.2000)
        val p2 = GeoPoint(28.6200, 77.2120)
        val p3 = GeoPoint(28.6300, 77.2240)
        return RouteData(
            id = "route-alpha-nh1",
            name = "NH-1 Corridor Alpha",
            fileName = "nh1_alpha.kmz",
            markers = listOf(
                RouteMarker(
                    id = "m-1",
                    label = "1+000",
                    chainage = com.example.model.Chainage(1000L),
                    point = p1
                ),
                RouteMarker(
                    id = "m-2",
                    label = "2+500",
                    chainage = com.example.model.Chainage(2500L),
                    point = p3
                )
            ),
            alignments = listOf(
                RouteAlignment(
                    id = "main",
                    name = "Main Carriageway",
                    points = listOf(p1, p2, p3),
                    direction = "INCREASING"
                )
            ),
            bounds = GeoBounds(28.609, 28.631, 77.199, 77.225),
            startChainage = com.example.model.Chainage(1000L),
            endChainage = com.example.model.Chainage(2500L)
        )
    }

    private fun routeBetaOverlapping(): RouteData {
        // Shares junction around p3 with Route Alpha, extends east to distinct unrelated tiles
        val shared = GeoPoint(28.6300, 77.2240)
        val east = GeoPoint(28.6340, 77.2550)
        return RouteData(
            id = "route-beta-nh2",
            name = "NH-2 Interchange Beta",
            fileName = "nh2_beta.kmz",
            markers = listOf(
                RouteMarker(
                    id = "m-3",
                    label = "5+000",
                    chainage = com.example.model.Chainage(5000L),
                    point = east
                )
            ),
            alignments = listOf(
                RouteAlignment(
                    id = "spur",
                    name = "Spur Link",
                    points = listOf(shared, east),
                    direction = "INCREASING"
                )
            ),
            bounds = GeoBounds(28.629, 28.635, 77.223, 77.256),
            startChainage = com.example.model.Chainage(3000L),
            endChainage = com.example.model.Chainage(5000L)
        )
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            delay(25L)
        }
    }

    // =========================================================================
    // 1. KMZ/KML CORRIDOR PLANNER TESTS
    // =========================================================================

    @Test
    fun corridorPlanner_coversDiagonalSegmentsAndNestedMarkers_excludesUnrelatedAreas_andCapsBudget() {
        val route = routeAlpha()
        val tiles = CorridorTilePlanner.computeCorridorTiles(route)
        assertTrue("Expected corridor tiles across zoom levels", tiles.isNotEmpty())

        val zoomsPresent = tiles.map { it.zoom }.toSet()
        assertTrue("Expected z15 corridor tiles", zoomsPresent.contains(15))
        assertTrue("Expected z16 navigation tiles", zoomsPresent.contains(16))
        assertTrue("Expected z17 close-approach tiles", zoomsPresent.contains(17))
        assertTrue("Expected z18 25m viewing zoom tiles", zoomsPresent.contains(18))

        // Midpoint along diagonal segment must be covered (no skipped gaps)
        val midTx16 = kotlin.math.floor(OsmTileManager.lonToTileX(77.2120, 16)).toInt()
        val midTy16 = kotlin.math.floor(OsmTileManager.latToTileY(28.6200, 16)).toInt()
        assertTrue(
            "Midpoint z16 tile ($midTx16, $midTy16) must be included in corridor",
            tiles.any { it.zoom == 16 && it.x == midTx16 && it.y == midTy16 }
        )

        // Unrelated city (Mumbai ~ 19.07, 72.87) must NEVER be included
        val unrelatedTx = kotlin.math.floor(OsmTileManager.lonToTileX(72.87, 16)).toInt()
        val unrelatedTy = kotlin.math.floor(OsmTileManager.latToTileY(19.07, 16)).toInt()
        assertFalse(
            "Unrelated city tiles must not be included",
            tiles.any { it.zoom == 16 && it.x == unrelatedTx && it.y == unrelatedTy }
        )

        // Long highway budget safety cap must be respected
        val longRoute = RouteData(
            id = "long-highway",
            name = "Cross-Country Expressway",
            fileName = "long.kmz",
            markers = emptyList(),
            alignments = listOf(
                RouteAlignment(
                    id = "main",
                    name = "Mainline",
                    points = listOf(GeoPoint(28.0, 77.0), GeoPoint(19.0, 72.8)),
                    direction = "INCREASING"
                )
            ),
            bounds = GeoBounds(19.0, 28.0, 72.8, 77.0),
            startChainage = null,
            endChainage = null
        )
        val capped = CorridorTilePlanner.computeCorridorTiles(longRoute, maxTilesBudget = 120)
        assertTrue("Budget cap must prevent runaway tile downloads", capped.size <= 120)
    }

    // =========================================================================
    // 2. SHARED OFFLINE CACHE REUSE, ISOLATION, PAUSE/RESUME, WIFI GATING
    // =========================================================================

    @Test
    fun offlineSyncManager_reusesCachedTilesAcrossRouteSwitches_isolatesManifests_andDeletesOnlyUnreferencedTiles() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val downloadedKeys = Collections.synchronizedList(mutableListOf<String>())
        val png = validPngBytes()

        val manager = OfflineMapSyncManager(
            scope = scope,
            ioDispatcher = Dispatchers.IO,
            tileDownloader = { ctx, z, x, y, sat ->
                downloadedKeys.add("$z/$x/$y")
                val target = OsmTileManager.offlineTileFileFor(ctx, z, x, y, sat)
                target.parentFile?.mkdirs()
                target.writeBytes(png)
                OsmTileManager.TileDownloadOutcome.DOWNLOADED
            },
            networkChecker = { NetworkAvailability(isConnected = true, isWifiOrUnmetered = true) },
            throttleDelayMs = 0L
        )

        val alpha = routeAlpha()
        val beta = routeBetaOverlapping()

        // 1. Sync Route Alpha completely
        manager.syncRoute(app, alpha, mapType = "standard", forceStart = true)
        waitUntil { manager.syncState.value.status == OfflineSyncStatus.COMPLETE }
        val alphaState = manager.syncState.value
        assertEquals(alpha.id, alphaState.activeRouteId)
        assertEquals(alphaState.totalTiles, alphaState.cachedTiles)
        val alphaDownloadCount = downloadedKeys.size
        assertTrue(alphaDownloadCount > 0)

        // 2. Switch to overlapping Route Beta -> shared tiles reused, only missing downloaded
        downloadedKeys.clear()
        manager.syncRoute(app, beta, mapType = "standard", forceStart = true)
        waitUntil { manager.syncState.value.status == OfflineSyncStatus.COMPLETE }
        val betaState = manager.syncState.value
        assertEquals(beta.id, betaState.activeRouteId)
        assertTrue("Shared junction tiles should be reused from Alpha", betaState.reusedTiles > 0)
        assertEquals(betaState.totalTiles - betaState.reusedTiles, downloadedKeys.size)

        // 3. Verify strict manifest isolation between Alpha & Beta
        val alphaManifestKeys = manager.getProjectManifestTileKeys(app, alpha.id)
        val betaManifestKeys = manager.getProjectManifestTileKeys(app, beta.id)
        val sharedOverlap = alphaManifestKeys.intersect(betaManifestKeys)
        assertTrue("Expected overlap at shared junction", sharedOverlap.isNotEmpty())
        val alphaOnlyKeys = alphaManifestKeys - betaManifestKeys
        assertTrue("Expected distinct Alpha corridor tiles", alphaOnlyKeys.isNotEmpty())
        assertTrue(
            "Beta manifest must not contain Alpha-only tiles",
            betaManifestKeys.none { it in alphaOnlyKeys }
        )

        // 4. Delete Alpha -> Alpha-only tiles cleaned up, Beta & shared overlap preserved!
        manager.deleteProjectOfflineData(app, alpha.id)
        val offlineRoot = OsmTileManager.offlineRootDir(app)
        for (sharedKey in sharedOverlap.take(5)) {
            val f = File(offlineRoot, "$sharedKey.png")
            assertTrue("Shared overlap tile $sharedKey must be preserved for Beta", f.exists())
        }
        for (alphaOnly in alphaOnlyKeys.take(5)) {
            val f = File(offlineRoot, "$alphaOnly.png")
            assertFalse("Unreferenced Alpha-only tile $alphaOnly should be deleted", f.exists())
        }

        scope.cancel()
    }

    @Test
    fun offlineSyncManager_respectsWifiOnly_networkLoss_andPauseResumeAcrossRestarts() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var connected = true
        var wifi = false
        val png = validPngBytes()

        val manager1 = OfflineMapSyncManager(
            scope = scope,
            ioDispatcher = Dispatchers.IO,
            tileDownloader = { ctx, z, x, y, sat ->
                val target = OsmTileManager.offlineTileFileFor(ctx, z, x, y, sat)
                target.parentFile?.mkdirs()
                target.writeBytes(png)
                OsmTileManager.TileDownloadOutcome.DOWNLOADED
            },
            networkChecker = { NetworkAvailability(isConnected = connected, isWifiOrUnmetered = wifi) },
            throttleDelayMs = 0L
        )

        val alpha = routeAlpha()

        // Wi-Fi only enabled on cellular -> WAITING_FOR_WIFI
        manager1.updatePreferences(app, autoPreload = true, wifiOnly = true)
        manager1.syncRoute(app, alpha, mapType = "standard", forceStart = false)
        waitUntil { manager1.syncState.value.status == OfflineSyncStatus.WAITING_FOR_WIFI }
        assertEquals(OfflineSyncStatus.WAITING_FOR_WIFI, manager1.syncState.value.status)

        // Network offline -> WAITING_FOR_NETWORK
        connected = false
        manager1.updatePreferences(app, autoPreload = true, wifiOnly = false)
        waitUntil { manager1.syncState.value.status == OfflineSyncStatus.WAITING_FOR_NETWORK }
        assertEquals(OfflineSyncStatus.WAITING_FOR_NETWORK, manager1.syncState.value.status)

        // Pause -> stays PAUSED even when recreating manager (restart persistence!)
        connected = true
        wifi = true
        manager1.pauseSync(app)
        assertEquals(OfflineSyncStatus.PAUSED, manager1.syncState.value.status)

        val manager2 = OfflineMapSyncManager(
            scope = scope,
            ioDispatcher = Dispatchers.IO,
            tileDownloader = { ctx, z, x, y, sat ->
                val target = OsmTileManager.offlineTileFileFor(ctx, z, x, y, sat)
                target.parentFile?.mkdirs()
                target.writeBytes(png)
                OsmTileManager.TileDownloadOutcome.DOWNLOADED
            },
            networkChecker = { NetworkAvailability(isConnected = true, isWifiOrUnmetered = true) },
            throttleDelayMs = 0L
        )
        manager2.syncRoute(app, alpha, mapType = "standard", forceStart = false)
        waitUntil { manager2.syncState.value.status == OfflineSyncStatus.PAUSED }
        assertEquals(OfflineSyncStatus.PAUSED, manager2.syncState.value.status)

        // Resume -> completes full sync
        manager2.resumeSync(app)
        waitUntil { manager2.syncState.value.status == OfflineSyncStatus.COMPLETE }
        assertEquals(OfflineSyncStatus.COMPLETE, manager2.syncState.value.status)

        scope.cancel()
    }

    // =========================================================================
    // 3. CORRUPT TILE RECOVERY, PROTECTED PRUNING & PARENT FALLBACK
    // =========================================================================

    @Test
    fun osmTileManager_rejectsCorruptTiles_preservesProtectedTiles_andFallsBackToParentZoom() {
        val corrupt = OsmTileManager.offlineTileFileFor(app, 16, 100, 100, isSatellite = false)
        corrupt.parentFile?.mkdirs()
        corrupt.writeText("NOT_A_VALID_PNG_FILE")

        val decoded = OsmTileManager.getCachedTileOnly(app, 16, 100, 100, isSatellite = false)
        assertNull("Corrupt tile bytes must return null", decoded)
        assertFalse("Corrupt tile file must be auto-deleted", corrupt.exists())

        // Protected key preservation during disk pruning
        val cacheRoot = File(app.cacheDir, "osm_tiles")
        val protectedFile = File(cacheRoot, "standard/15/42/99.png")
        protectedFile.parentFile?.mkdirs()
        protectedFile.writeBytes(validPngBytes())
        OsmTileManager.pruneDiskCache(
            rootDir = cacheRoot,
            maxBytes = 10L,
            protectedKeys = setOf("standard/15/42/99")
        )
        assertTrue("Protected tile must survive cache pruning", protectedFile.exists())

        // Parent tile fallback at z18 (25m scale) when only z16 parent tile exists
        val center = GeoPoint(28.6150, 77.2050)
        val z16x = kotlin.math.floor(OsmTileManager.lonToTileX(center.longitude, 16)).toInt()
        val z16y = kotlin.math.floor(OsmTileManager.latToTileY(center.latitude, 16)).toInt()
        val z16File = OsmTileManager.offlineTileFileFor(app, 16, z16x, z16y, isSatellite = false)
        z16File.parentFile?.mkdirs()
        z16File.writeBytes(validPngBytes())

        OsmTileManager.clearMemoryCache()
        OsmTileManager.invalidateStitchedCache()
        val coverage = OsmTileManager.renderMapBitmapWithCoverage(
            context = app,
            center = center,
            widthPx = 300,
            heightPx = 300,
            zoom = 18,
            panOffsetX = 0f,
            panOffsetY = 0f,
            darkTheme = false,
            mapType = "standard"
        )
        assertNotNull("Parent fallback must produce non-null bitmap", coverage.bitmap)
        assertTrue("Fallback tile count must be > 0", coverage.fallbackTilesUsed > 0)
        assertEquals("Parent fallback should cover missing z18 viewport", 0, coverage.missingTiles)
        assertTrue(coverage.isCompleteCoverage)
    }

    // =========================================================================
    // 4. COMPLETE MAP RENDERING VIEWPORT COVERAGE (NO CROPPING ON LARGE/ROTATED SCREENS)
    // =========================================================================

    @Test
    fun resolveTileRenderPlan_coversFullRotatedDiagonalWithoutCroppingAcrossAspectRatios() {
        val viewports = listOf(
            360f to 240f,
            1440f to 3120f, // Tall high-DPI portrait phone
            2400f to 1080f, // Wide landscape tablet
            180f to 180f,   // Camera inset square
            420f to 160f    // Panoramic strip
        )
        val zooms = listOf(10.0f, 13.5f, 15.2f, 17.8f, 18.6f, 20.5f)

        for ((w, h) in viewports) {
            val diag = hypot(w, h)
            for (z in zooms) {
                val plan = MiniMapViewportMath.resolveTileRenderPlan(w, h, z)
                val renderedCoveragePx = plan.renderSizePx * plan.renderScale
                assertTrue(
                    "Rendered coverage ($renderedCoveragePx) cropped diagonal ($diag) for ${w}x$h @ z=$z",
                    renderedCoveragePx >= diag
                )
                assertTrue("Bitmap size must stay safe against OOM", plan.renderSizePx <= 1800)
                assertTrue("Render scale must be positive", plan.renderScale > 0f)
            }
        }
    }

    // =========================================================================
    // 5. COMPOSE UI INTEGRATION: LIGHT/DARK THEMES, OFFLINE BADGE, SYNC STRIP
    // =========================================================================

    @Test
    fun miniMapPreview_lightTheme_showsOfflineBadgeWhenUncached_andSyncStripPauses() {
        OsmTileManager.isNetworkConnectedProvider = { false }
        var pauseClicks = 0

        val syncingState = OfflineMapSyncState(
            activeRouteId = "route-1",
            activeRouteName = "NH-1 Corridor",
            status = OfflineSyncStatus.SYNCING,
            totalTiles = 100,
            cachedTiles = 45,
            reusedTiles = 15,
            bytesOnDisk = 256_000L
        )

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "light") {
                Box(modifier = Modifier.size(360.dp, 340.dp)) {
                    MiniMapPreview(
                        location = GeoPoint(19.0760, 72.8777),
                        bearing = 45f,
                        routeData = null,
                        highwayTag = "NH-48",
                        orientationMode = MiniMapOrientationMode.DIRECTION_UP,
                        interactive = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp)
                    )
                    OfflineMapSyncStatusStrip(
                        syncState = syncingState,
                        onPauseSync = { pauseClicks++ },
                        onResumeSync = {},
                        onRetrySync = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("minimap_offline_coverage_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_sync_status_strip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_sync_action_button").performClick()
        assertEquals(1, pauseClicks)
    }

    @Test
    fun interactiveSmartMap_darkTheme_showsOfflineBadgeWhenUncached_andSyncStripResumesAndRetries() {
        OsmTileManager.isNetworkConnectedProvider = { false }
        var resumeClicks = 0
        var retryClicks = 0

        val pausedState = OfflineMapSyncState(
            activeRouteId = "route-1",
            activeRouteName = "NH-1 Corridor",
            status = OfflineSyncStatus.PAUSED,
            totalTiles = 100,
            cachedTiles = 45,
            reusedTiles = 15,
            bytesOnDisk = 256_000L
        )
        val errorState = pausedState.copy(status = OfflineSyncStatus.PARTIAL_ERROR)
        var currentStripState by mutableStateOf(pausedState)

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                Box(modifier = Modifier.size(360.dp, 340.dp)) {
                    InteractiveSmartMap(
                        location = GeoPoint(19.0760, 72.8777),
                        gpsAccuracy = 6f,
                        bearing = 90f,
                        compassHeading = 90f,
                        routeData = routeAlpha(),
                        matchedProjectedPoint = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp)
                    )
                    OfflineMapSyncStatusStrip(
                        syncState = currentStripState,
                        onPauseSync = {},
                        onResumeSync = { resumeClicks++ },
                        onRetrySync = { retryClicks++ }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("minimap_offline_coverage_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_sync_action_button").performClick()
        assertEquals(1, resumeClicks)

        composeTestRule.runOnIdle {
            currentStripState = errorState
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Partial Offline Cache (45/100)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_sync_action_button").performClick()
        assertEquals(1, retryClicks)
    }
}
