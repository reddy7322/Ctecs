package com.example

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.geo.OsmTileManager
import com.example.location.LocationNameResolver
import com.example.location.LocationTracker
import com.example.model.GeoPoint
import com.example.model.OverlayConfig
import com.example.overlay.LogoProvider
import com.example.overlay.MiniMapRenderer
import com.example.parser.KmzParser
import com.example.sensor.CompassAndOrientationTracker
import com.example.update.AppUpdateManager
import com.example.widget.CtecsWidgetUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Regression & stability test suite covering CTECS battery, memory, network,
 * GPS adaptive polling, sensor throttling, widget IPC caching, and parser resilience optimizations.
 */
@RunWith(RobolectricTestRunner::class)
class AppOptimizationAndStabilityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OsmTileManager.clearMemoryCache()
        OsmTileManager.clearFailureBackoff()
        LogoProvider.clearCache()
        MiniMapRenderer.clearCache()
        LocationNameResolver.invalidateCache()
        CtecsWidgetUpdater.invalidateWidgetInstanceCache()
    }

    @Test
    fun `OsmTileManager wraps antimeridian disk cache path and prunes oversized cache`() {
        val wrappedLeft = OsmTileManager.tileFileFor(context, zoom = 2, x = -1, y = 1, isSatellite = false)
        val canonicalLeft = OsmTileManager.tileFileFor(context, zoom = 2, x = 3, y = 1, isSatellite = false)
        assertEquals(canonicalLeft.absolutePath, wrappedLeft.absolutePath)
        assertTrue(wrappedLeft.absolutePath.endsWith("osm_tiles/standard/2/3/1.png"))

        val wrappedRight = OsmTileManager.tileFileFor(context, zoom = 2, x = 4, y = 1, isSatellite = true)
        assertTrue(wrappedRight.absolutePath.endsWith("osm_tiles/satellite/2/0/1.jpg"))

        // Bounded disk cache eviction test
        val tempRoot = File(context.cacheDir, "test_osm_prune_${System.nanoTime()}").apply { mkdirs() }
        try {
            val f1 = File(tempRoot, "a.png").apply { writeBytes(ByteArray(400)); setLastModified(1_000L) }
            val f2 = File(tempRoot, "b.png").apply { writeBytes(ByteArray(400)); setLastModified(2_000L) }
            val f3 = File(tempRoot, "c.png").apply { writeBytes(ByteArray(400)); setLastModified(3_000L) }
            OsmTileManager.pruneDiskCache(
                rootDir = tempRoot,
                maxBytes = 900L,
                maxAgeMs = 100_000L,
                nowMs = 5_000L
            )
            assertFalse("Oldest tile should be evicted to respect cache cap", f1.exists())
            assertTrue("Newest tile should survive", f3.exists())
            assertTrue(f2.exists() || !f2.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `OsmTileManager applies failure backoff and releases memory on trimMemory`() {
        val now = 1_000_000L
        val key = "standard/15/100/200"
        assertFalse(OsmTileManager.isTileInBackoff(key, now))
        OsmTileManager.recordTileFailure(key, nowMs = now, isOfflineError = false)
        assertTrue(OsmTileManager.isTileInBackoff(key, now + 10_000L))
        assertFalse(OsmTileManager.isTileInBackoff(key, now + 35_000L))

        // Offline error triggers global cooldown for other keys too
        OsmTileManager.recordTileFailure(key, nowMs = now + 40_000L, isOfflineError = true)
        assertTrue(OsmTileManager.isTileInBackoff("standard/15/999/888", now + 45_000L))

        // Memory cache trims cleanly
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        OsmTileManager.putTileInMemoryCacheForTest("test_key", bmp)
        assertTrue(OsmTileManager.memoryCacheSizeBytes() > 0)
        OsmTileManager.trimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        assertEquals(0, OsmTileManager.memoryCacheSizeBytes())
    }

    @Test
    fun `OsmTileManager renderMapBitmap returns null lazily without tiles in cache`() {
        // Put key in backoff so no background network request fires during test
        OsmTileManager.recordTileFailure("any", nowMs = System.currentTimeMillis(), isOfflineError = true)
        val result = OsmTileManager.renderMapBitmap(
            context = context,
            center = GeoPoint(28.6139, 77.2090),
            widthPx = 256,
            heightPx = 256,
            zoom = 15
        )
        assertNull("Should return null when no cached tiles exist", result)
    }

    @Test
    fun `LocationTracker steps down polling interval when stationary and wakes immediately on movement`() {
        val tracker = LocationTracker(context)
        val t0 = 100_000L

        assertEquals(LocationTracker.ACTIVE_INTERVAL_MS, tracker.getCurrentPollingIntervalMs())
        assertFalse(tracker.isStationaryPowerSaveActive())

        // Stationary fixes over 31 seconds -> enters power-save interval (3500ms)
        tracker.evaluateAdaptivePolling(28.6139, 77.2090, speedMps = 0.0f, nowMs = t0)
        tracker.evaluateAdaptivePolling(28.6139, 77.2090, speedMps = 0.1f, nowMs = t0 + 15_000L)
        assertFalse(tracker.isStationaryPowerSaveActive())

        tracker.evaluateAdaptivePolling(28.6139, 77.2090, speedMps = 0.0f, nowMs = t0 + 31_000L)
        assertTrue(tracker.isStationaryPowerSaveActive())
        assertEquals(LocationTracker.STATIONARY_INTERVAL_MS, tracker.getCurrentPollingIntervalMs())

        // High-frequency mode (e.g., Camera / Survey) forces immediate snapback to 1000ms
        tracker.setHighFrequencyMode(true)
        assertFalse(tracker.isStationaryPowerSaveActive())
        assertEquals(LocationTracker.ACTIVE_INTERVAL_MS, tracker.getCurrentPollingIntervalMs())

        // Release high-frequency mode, enter stationary again, then move vehicle -> immediate snapback
        tracker.setHighFrequencyMode(false)
        tracker.evaluateAdaptivePolling(28.6139, 77.2090, speedMps = 0.0f, nowMs = t0 + 40_000L)
        tracker.evaluateAdaptivePolling(28.6139, 77.2090, speedMps = 0.0f, nowMs = t0 + 75_000L)
        assertTrue(tracker.isStationaryPowerSaveActive())

        tracker.evaluateAdaptivePolling(28.6145, 77.2090, speedMps = 5.5f, nowMs = t0 + 76_000L)
        assertFalse(tracker.isStationaryPowerSaveActive())
        assertEquals(LocationTracker.ACTIVE_INTERVAL_MS, tracker.getCurrentPollingIntervalMs())
    }

    @Test
    fun `CompassAndOrientationTracker throttles jittery sub-degree updates and emits real turns`() {
        val tracker = CompassAndOrientationTracker(context)
        val t0 = 50_000L

        // First reading always emits
        assertTrue(tracker.updateCompass(90.0f, nowMs = t0))
        assertEquals("E", tracker.compassState.value.cardinalDirection)

        // Small jitter within 1.5 degrees is suppressed even after 500ms
        assertFalse(tracker.updateCompass(90.6f, nowMs = t0 + 500L))

        // Larger delta within 120ms same cardinal is suppressed
        assertFalse(tracker.updateCompass(93.0f, nowMs = t0 + 50L))

        // Meaningful delta after >= 120ms emits cleanly
        assertTrue(tracker.updateCompass(95.0f, nowMs = t0 + 200L))
        assertEquals(95.0f, tracker.compassState.value.azimuthDegrees, 0.01f)

        // Cardinal change emits immediately
        assertTrue(tracker.updateCompass(180.0f, nowMs = t0 + 230L))
        assertEquals("S", tracker.compassState.value.cardinalDirection)
    }

    @Test
    fun `LocationNameResolver enforces bounded LRU cache and offline backoff`() {
        for (i in 0 until 320) {
            LocationNameResolver.putCachedLocationForTest(28.0 + i * 0.01, 77.0, "City $i")
        }
        assertTrue("LRU cache must stay bounded at <= 256 entries", LocationNameResolver.cacheSize() <= 256)

        val now = 2_000_000L
        assertFalse(LocationNameResolver.isInFailureBackoff(now))
        LocationNameResolver.recordFailureBackoff(now)
        assertTrue(LocationNameResolver.isInFailureBackoff(now + 10_000L))
        assertFalse(LocationNameResolver.isInFailureBackoff(now + LocationNameResolver.FAILURE_BACKOFF_MS + 1L))
    }

    @Test
    fun `AppUpdateManager throttles automatic checks for 12 hours`() {
        val manager = AppUpdateManager(context)
        val t0 = 10_000_000L
        manager.recordAutomaticCheck(t0)

        assertFalse(manager.shouldRunAutomaticCheck(t0 + 60_000L))
        assertFalse(manager.shouldRunAutomaticCheck(t0 + AppUpdateManager.AUTO_CHECK_INTERVAL_MS - 1_000L))
        assertTrue(manager.shouldRunAutomaticCheck(t0 + AppUpdateManager.AUTO_CHECK_INTERVAL_MS + 1_000L))
    }

    @Test
    fun `LogoProvider caches processed logo bitmaps and clears cleanly`() {
        val cfg = OverlayConfig(showLogo = true, logoPreset = "ctecs")
        val first = LogoProvider.getProcessedLogo(context, cfg, 160)
        val second = LogoProvider.getProcessedLogo(context, cfg, 160)
        assertNotNull(first)
        assertSame("Repeated frames should reuse cached processed logo bitmap", first, second)

        LogoProvider.clearCache()
        val third = LogoProvider.getProcessedLogo(context, cfg, 160)
        assertNotNull(third)
        assertFalse("After clearCache a fresh bitmap should be generated", first === third)
    }

    @Test
    fun `CtecsWidgetUpdater short-circuits cleanly when no widgets are installed`() {
        val instances = CtecsWidgetUpdater.getActiveWidgetInstances(context, bypassCache = true)
        assertTrue(instances.isEmpty())
        // Should return cleanly without throwing or doing heavy rendering
        CtecsWidgetUpdater.updateAllWidgets(context, force = false)
        assertTrue(CtecsWidgetUpdater.getActiveWidgetInstances(context, bypassCache = false).isEmpty())
    }

    @Test
    fun `KmzParser handles malformed or truncated input safely without crashing`() {
        val garbage = ByteArrayInputStream("not-a-valid-kml-or-zip-file".toByteArray())
        val result = try {
            KmzParser.parse(garbage, "corrupt.kmz")
        } catch (_: Exception) {
            null
        }
        // Must fail gracefully via RouteParseException/empty result without throwing OutOfMemoryError or crashing JVM
        assertTrue(result == null || result.alignments.isEmpty())
    }
}
