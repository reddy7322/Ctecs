package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.geo.ChainageMatcher
import com.example.geo.ChainageProgressionTracker
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.Chainage
import com.example.model.DirectionControlMode
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.ManualChainageMode
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.SmartDirectionPreferences
import com.example.model.TravelChainageDirection
import com.example.navigation.SmartDirectionManager
import com.example.overlay.OverlayTextGenerator
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.cos

/**
 * Comprehensive verification suite for CTECS Camera Manual LHS/RHS Direction Lock.
 * Verifies all mandatory requirements:
 * 1. Manual LHS remains locked despite opposing GPS updates & high speed.
 * 2. Manual RHS remains locked despite opposing GPS updates & high speed.
 * 3. Extended inactivity (10+ min / hours) NEVER auto-reverts manual selection to AUTO.
 * 4. Temporary GPS loss & recovery NEVER resets manual selection.
 * 5. Photo capture and video recording preserve the locked manual direction.
 * 6. Switching Continue Stable / Dynamic Chainage modes preserves manual LHS/RHS while
 *    forward/reverse chainage progression stays strictly independent.
 * 7. Explicitly selecting AUTO hands control back to KMZ/GPS alignment detection.
 * 8. Reopening Camera / recreating ViewModel restores previously selected mode ("MANUAL_LHS",
 *    "MANUAL_RHS", or "AUTO").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CameraManualDirectionLockTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    companion object {
        private const val BASE_LAT = 17.400000
        private const val BASE_LNG = 78.400000
        private const val METRES_PER_DEG_LAT = 111_132.0
    }

    @Before
    fun setUp() {
        ChainageMatcher.clearCache()
    }

    private fun pointNorthEast(northMetres: Double, eastMetres: Double = 0.0): GeoPoint {
        val lat = BASE_LAT + northMetres / METRES_PER_DEG_LAT
        val lng = BASE_LNG + eastMetres / (METRES_PER_DEG_LAT * cos(Math.toRadians(BASE_LAT)))
        return GeoPoint(lat, lng)
    }

    private fun buildHighwayRoute(): RouteData {
        val points = (0..1000 step 50).map { pointNorthEast(it.toDouble(), 0.0) }
        val markers = (0..1000 step 50).map { d ->
            val ch = Chainage(174_000L + d)
            RouteMarker("m_$d", ch.format(), ch, pointNorthEast(d.toDouble(), 0.0))
        }
        val alignment = RouteAlignment(
            id = "nh_align_1",
            name = "Main Alignment",
            points = points,
            direction = "INCREASING",
            carriagewayType = "MAIN"
        )
        return RouteData(
            id = "route_lock_test",
            name = "NH-Lock-Test",
            fileName = "lock_test.kmz",
            markers = markers,
            alignments = listOf(alignment),
            bounds = GeoBounds(
                minLat = points.minOf { it.latitude },
                maxLat = points.maxOf { it.latitude },
                minLng = points.minOf { it.longitude },
                maxLng = points.maxOf { it.longitude }
            ),
            startChainage = Chainage(174_000L),
            endChainage = Chainage(175_000L)
        )
    }

    private fun locAt(northM: Double, eastM: Double, speedMps: Float, timeMs: Long, acc: Float = 4.0f): LocationState {
        return LocationState(
            point = pointNorthEast(northM, eastM),
            accuracy = acc,
            speed = speedMps,
            bearing = 0f,
            status = LocationStatus.READY,
            lastFixTime = timeMs
        )
    }

    @Test
    fun `1 and 2 - manual LHS and RHS remain strictly locked despite opposing GPS offsets and high vehicle speed`() {
        val route = buildHighwayRoute()
        val manager = SmartDirectionManager()
        val t0 = 1_700_100_000_000L

        // --- Test 1: Manual LHS locked while driving on RHS side (east = +12m) at 60 km/h (16.7 m/s) ---
        val lhsPrefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "LHS",
            manualSelectedAtMs = t0
        )
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, lhsPrefs.internalDirectionMode)
        manager.onManualDirectionSelected("LHS", t0)

        for (i in 1..10) {
            val ts = t0 + i * 2_000L
            val loc = locAt(northM = 100.0 + i * 30.0, eastM = +12.0, speedMps = 16.7f, timeMs = ts)
            val match = ChainageMatcher.match(loc.point!!, loc.accuracy, loc.bearing, route, fixTimeMs = ts)
            val state = manager.evaluate(loc, match, route, lhsPrefs, nowMs = ts)
            assertTrue("Manual LHS must remain locked at step $i", state.isManualOverride)
            assertEquals("LHS", state.confirmedSide)
            assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, state.internalDirectionMode)
            assertNull("Manual lock must never auto-reactivate AUTO", state.autoReactivatedReason)
        }

        // --- Test 2: Manual RHS locked while driving on LHS side (east = -12m) at 72 km/h (20.0 m/s) ---
        val rhsPrefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "RHS",
            manualSelectedAtMs = t0 + 30_000L
        )
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_RHS, rhsPrefs.internalDirectionMode)
        manager.onManualDirectionSelected("RHS", t0 + 30_000L)

        for (i in 1..10) {
            val ts = t0 + 30_000L + i * 2_000L
            val loc = locAt(northM = 200.0 + i * 30.0, eastM = -12.0, speedMps = 20.0f, timeMs = ts)
            val match = ChainageMatcher.match(loc.point!!, loc.accuracy, loc.bearing, route, fixTimeMs = ts)
            val state = manager.evaluate(loc, match, route, rhsPrefs, nowMs = ts)
            assertTrue("Manual RHS must remain locked at step $i", state.isManualOverride)
            assertEquals("RHS", state.confirmedSide)
            assertEquals(SmartDirectionPreferences.MODE_MANUAL_RHS, state.internalDirectionMode)
            assertNull("Manual lock must never auto-reactivate AUTO", state.autoReactivatedReason)
        }
    }

    @Test
    fun `3 and 4 - extended inactivity and temporary GPS loss never reset manual LHS or RHS to AUTO`() {
        val route = buildHighwayRoute()
        val manager = SmartDirectionManager()
        val t0 = 1_700_200_000_000L
        val lhsPrefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "LHS",
            manualSelectedAtMs = t0
        )
        manager.onManualDirectionSelected("LHS", t0)

        // Inactivity check: 15 minutes, 2 hours, and 24 hours with zero photo captures
        assertNull("remainingManualInactivityMs must always be null (no timeout)", manager.remainingManualInactivityMs(lhsPrefs, t0))
        for (elapsedMs in listOf(15L * 60_000L, 2L * 3_600_000L, 24L * 3_600_000L)) {
            val ts = t0 + elapsedMs
            val loc = locAt(northM = 200.0, eastM = +10.0, speedMps = 0f, timeMs = ts)
            val state = manager.evaluate(loc, null, route, lhsPrefs, nowMs = ts)
            assertTrue("Manual LHS must survive ${elapsedMs / 60_000L}m inactivity", state.isManualOverride)
            assertEquals("LHS", state.confirmedSide)
            assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, state.internalDirectionMode)
            assertNull(state.autoReactivatedReason)
        }

        // Temporary GPS loss (null point + weak 85m accuracy) and recovery on opposite side
        val tLost = t0 + 25L * 3_600_000L
        val lostLoc = LocationState(point = null, status = LocationStatus.ACQUIRING, lastFixTime = tLost)
        val lostState = manager.evaluate(lostLoc, null, route, lhsPrefs, nowMs = tLost)
        assertTrue(lostState.isManualOverride)
        assertEquals("LHS", lostState.confirmedSide)

        val weakLoc = locAt(northM = 250.0, eastM = +15.0, speedMps = 12f, timeMs = tLost + 5_000L, acc = 85f)
        val weakState = manager.evaluate(weakLoc, null, route, lhsPrefs, nowMs = tLost + 5_000L)
        assertTrue(weakState.isManualOverride)
        assertEquals("LHS", weakState.confirmedSide)

        val recoveredLoc = locAt(northM = 300.0, eastM = +15.0, speedMps = 15f, timeMs = tLost + 15_000L, acc = 4f)
        val recoveredState = manager.evaluate(recoveredLoc, null, route, lhsPrefs, nowMs = tLost + 15_000L)
        assertTrue("Manual LHS must stay locked after GPS recovery", recoveredState.isManualOverride)
        assertEquals("LHS", recoveredState.confirmedSide)
    }

    @Test
    fun `5 and 6 - photo capture, video recording, and Stable-Dynamic chainage switching keep manual side locked and independent of travel direction`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        val route = buildHighwayRoute()
        val tracker = ChainageProgressionTracker()
        val baseTime = 1_700_300_000_000L

        // Lock manual LHS on Camera
        viewModel.updateSide(RoadSide.LHS)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, viewModel.directionLockMode.value)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertEquals("LHS", viewModel.smartDirectionState.value.confirmedSide)

        // Photo snapshot immediately captures locked LHS
        val photoSnap = viewModel.createCurrentSnapshot()
        assertEquals("LHS", OverlayTextGenerator.resolveAuthoritativeCaptureSide(photoSnap).first)
        assertTrue(photoSnap.matchResult.smartDirectionState?.isManualOverride == true)

        // Video recording start + stop preserves locked LHS
        viewModel.onVideoRecordingStarted()
        val duringVideoSnap = viewModel.createCurrentSnapshot()
        assertEquals("LHS", OverlayTextGenerator.resolveAuthoritativeCaptureSide(duringVideoSnap).first)
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, viewModel.directionLockMode.value)
        viewModel.onVideoRecordingStopped()
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, viewModel.directionLockMode.value)

        // Switching Continue Stable <-> Dynamic Chainage Correction preserves manual LHS lock
        viewModel.setManualChainage("174+250", ManualChainageMode.CONTINUE_STABLE)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, viewModel.directionLockMode.value)
        assertEquals("LHS", viewModel.smartDirectionState.value.confirmedSide)

        viewModel.setManualChainageMode(ManualChainageMode.DYNAMIC_CORRECTION)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, viewModel.directionLockMode.value)
        assertEquals("LHS", viewModel.smartDirectionState.value.confirmedSide)

        viewModel.clearManualChainage()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, viewModel.directionLockMode.value)

        // Dynamic Chainage Correction respects locked manual side ("LHS") while calculating reverse travel independently!
        tracker.setManualSide("LHS")
        val p0 = pointNorthEast(400.0, eastMetres = +10.0)
        val l0 = locAt(400.0, 10.0, 10f, baseTime)
        val a0 = ChainageMatcher.match(p0, 4.0f, 0f, route, fixTimeMs = baseTime)
        var cal = tracker.establishManualCalibration(
            enteredChainage = Chainage(174_420L),
            mode = ManualChainageMode.DYNAMIC_CORRECTION,
            currentAutoResult = a0,
            locationState = l0,
            routeId = route.id,
            nowMs = baseTime
        )
        var prevAuto = a0
        var lastRes = a0
        listOf(380.0, 360.0, 340.0).forEachIndexed { idx, north ->
            val ts = baseTime + (idx + 1) * 2_000L
            val loc = locAt(north, 10.0, 10f, ts).copy(bearing = 180f)
            val auto = ChainageMatcher.match(loc.point!!, 4.0f, 180f, route, prevAuto, fixTimeMs = ts)
            prevAuto = auto
            val out = tracker.processWithManualCalibration(auto, loc, route, cal, ts)
            cal = out.updatedCalibration
            lastRes = out.matchResult
        }
        assertEquals("Locked road side must remain LHS", "LHS", lastRes.detectedSide)
        assertEquals("Travel direction must independently detect REVERSE", TravelChainageDirection.REVERSE, lastRes.travelDirection)
        assertEquals("Calibrated reverse chainage must decrease to 174+360", 174_360.0, lastRes.chainage!!.metres.toDouble(), 2.0)

        viewModel.resetSideToAuto()
    }

    @Test
    fun `7 - explicitly selecting AUTO hands control back to KMZ alignment offset detection`() {
        val route = buildHighwayRoute()
        val manager = SmartDirectionManager()
        val t0 = 1_700_400_000_000L

        // Start locked to MANUAL_LHS
        val lhsPrefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "LHS",
            manualSelectedAtMs = t0
        )
        manager.onManualDirectionSelected("LHS", t0)
        val locked = manager.evaluate(locAt(200.0, +10.0, 12f, t0), null, route, lhsPrefs, t0)
        assertTrue(locked.isManualOverride)
        assertEquals("LHS", locked.confirmedSide)

        // User explicitly selects AUTO
        manager.onAutoModeSelected()
        val autoPrefs = SmartDirectionPreferences(controlMode = DirectionControlMode.AUTO)
        assertEquals(SmartDirectionPreferences.MODE_AUTO, autoPrefs.internalDirectionMode)

        // Feed reliable GPS fixes on the RHS (east = +10m) -> AUTO detects RHS cleanly
        var autoState = locked
        for (i in 1..4) {
            val ts = t0 + i * 2_000L
            val loc = locAt(200.0 + i * 20.0, +10.0, 12f, ts)
            val match = ChainageMatcher.match(loc.point!!, loc.accuracy, loc.bearing, route, fixTimeMs = ts)
            autoState = manager.evaluate(loc, match, route, autoPrefs, nowMs = ts)
        }
        assertFalse("Explicit AUTO must clear manual override", autoState.isManualOverride)
        assertEquals(SmartDirectionPreferences.MODE_AUTO, autoState.internalDirectionMode)
        assertEquals("AUTO KMZ offset detection must confirm RHS on east=+10m side", "RHS", autoState.confirmedSide)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun `8 - reopening Camera and recreating ViewModel restores persisted MANUAL_LHS, MANUAL_RHS, and AUTO`() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()

        // 1. Verify SmartDirectionPreferences JSON serialization round-trip for all modes
        val lhsJson = SmartDirectionPreferences(DirectionControlMode.MANUAL_OVERRIDE, "LHS", manualSelectedAtMs = 1234L).toJsonString()
        assertTrue(lhsJson.contains("\"internalDirectionMode\":\"MANUAL_LHS\""))
        val restoredLhsPrefs = SmartDirectionPreferences.fromJsonString(lhsJson)
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, restoredLhsPrefs.internalDirectionMode)
        assertEquals("LHS", restoredLhsPrefs.normalizedManualSide)

        val rhsJson = SmartDirectionPreferences(DirectionControlMode.MANUAL_OVERRIDE, "RHS", manualSelectedAtMs = 5678L).toJsonString()
        assertTrue(rhsJson.contains("\"internalDirectionMode\":\"MANUAL_RHS\""))
        val restoredRhsPrefs = SmartDirectionPreferences.fromJsonString(rhsJson)
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_RHS, restoredRhsPrefs.internalDirectionMode)
        assertEquals("RHS", restoredRhsPrefs.normalizedManualSide)

        val autoJson = SmartDirectionPreferences(DirectionControlMode.AUTO).toJsonString()
        assertTrue(autoJson.contains("\"internalDirectionMode\":\"AUTO\""))
        assertEquals(SmartDirectionPreferences.MODE_AUTO, SmartDirectionPreferences.fromJsonString(autoJson).internalDirectionMode)

        // 2. Select RHS manually in ViewModel #1, flush, simulate process restart -> ViewModel #2 restores MANUAL_RHS immediately
        val vm1 = MainViewModel(app)
        vm1.updateSide(RoadSide.RHS)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(120)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_RHS, vm1.directionLockMode.value)

        PreferencesManager.clearMemoryCacheForProcessRestart()
        val vm2 = MainViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(SmartDirectionPreferences.MODE_MANUAL_RHS, vm2.directionLockMode.value)
        assertTrue("Restored ViewModel must remain in manual override", vm2.smartDirectionState.value.isManualOverride)
        assertEquals("RHS", vm2.smartDirectionState.value.confirmedSide)

        // 3. Open CameraScreen, force full recomposition (simulating Camera reopen) -> stays RHS locked
        var sessionKey by mutableIntStateOf(0)
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                Box(modifier = Modifier.fillMaxSize()) {
                    key(sessionKey) {
                        CameraScreen(
                            viewModel = vm2,
                            onNavigateToPhotos = {}
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(250L)
        shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.onNodeWithTag("side_chip").assertIsDisplayed().assertTextEquals("RHS")

        sessionKey++
        composeTestRule.mainClock.advanceTimeBy(250L)
        shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.onNodeWithTag("side_chip").assertIsDisplayed().assertTextEquals("RHS")
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_RHS, vm2.directionLockMode.value)

        // 4. Explicitly select AUTO -> mode becomes AUTO and persists across restart
        vm2.resetSideToAuto()
        composeTestRule.mainClock.advanceTimeBy(250L)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(120)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SmartDirectionPreferences.MODE_AUTO, vm2.directionLockMode.value)
        assertFalse(vm2.smartDirectionState.value.isManualOverride)

        PreferencesManager.clearMemoryCacheForProcessRestart()
        val vm3 = MainViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SmartDirectionPreferences.MODE_AUTO, vm3.directionLockMode.value)
        assertFalse(vm3.smartDirectionState.value.isManualOverride)
    }
}
