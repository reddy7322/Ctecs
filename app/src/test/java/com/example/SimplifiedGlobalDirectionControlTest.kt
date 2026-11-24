package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.DirectionAutoReactivationReason
import com.example.model.DirectionControlMode
import com.example.model.DirectionDetectionState
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.SmartDirectionPreferences
import com.example.navigation.CtecsNavigationTelemetryHub
import com.example.navigation.LockScreenNavStateResolver
import com.example.navigation.SmartDirectionManager
import com.example.overlay.OverlayTextGenerator
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraSideOption
import com.example.ui.camera.resolveCameraSideDisplayState
import com.example.ui.home.NextKmStoneTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos

/**
 * Comprehensive 12-scenario test suite verifying CTECS Simplified Global LHS/RHS Direction Control:
 * - Pure KMZ/KML signed lateral offset detection (straight, curved, 90° bend, S-bend, dual-carriageway)
 * - Manual direction override (LHS, RHS, BOTH/BHS, MEDIAN, NONE) immune to GPS updates
 * - 10-minute photo inactivity timeout (reset ONLY by photo capture, paused during video recording)
 * - Reliable consecutive >= 5 km/h vehicle speed trigger activating AUTO without oscillation
 * - App-wide synchronization across overlays, Camera, Home, Navigation, and ViewModel
 * - Safe unavailable / retained behavior on missing KMZ/KML or weak GPS (never invents LHS/RHS)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SimplifiedGlobalDirectionControlTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        CtecsNavigationTelemetryHub.sharedDirectionManager.reset()
        CtecsNavigationTelemetryHub.sharedDirectionManager.onAutoModeSelected()
    }

    /**
     * Straight north-going KMZ alignment along lng = 72.8700 (lat 19.0000 -> 19.0200, ~2.22 km).
     * Increasing chainage goes North:
     * - West of centerline (lng < 72.8700) => LHS (+lateral offset)
     * - East of centerline (lng > 72.8700) => RHS (-lateral offset)
     */
    private fun buildNorthSouthRoute(
        routeId: String = "route_ns",
        directionMeta: String = "INCREASING"
    ): RouteData {
        val p0 = GeoPoint(19.000, 72.870)
        val p1 = GeoPoint(19.010, 72.870)
        val p2 = GeoPoint(19.020, 72.870)
        val markers = if (directionMeta == "DECREASING") {
            listOf(
                RouteMarker("m1", "KM 202+000", Chainage.fromMetres(202_000L), p0),
                RouteMarker("m2", "KM 201+000", Chainage.fromMetres(201_000L), p1),
                RouteMarker("m3", "KM 200+000", Chainage.fromMetres(200_000L), p2)
            )
        } else {
            listOf(
                RouteMarker("m1", "KM 200+000", Chainage.fromMetres(200_000L), p0),
                RouteMarker("m2", "KM 201+000", Chainage.fromMetres(201_000L), p1),
                RouteMarker("m3", "KM 202+000", Chainage.fromMetres(202_000L), p2)
            )
        }
        return RouteData(
            id = routeId,
            name = "NH-48 Corridor",
            fileName = "$routeId.kmz",
            markers = markers,
            alignments = listOf(
                RouteAlignment(
                    id = "${routeId}_main",
                    name = "NH-48 Centerline",
                    points = listOf(p0, p1, p2),
                    carriagewayType = "MAIN",
                    direction = directionMeta
                )
            ),
            bounds = GeoBounds(19.000, 19.020, 72.869, 72.871),
            startChainage = Chainage.fromMetres(200_000L),
            endChainage = Chainage.fromMetres(202_000L)
        )
    }

    /**
     * L-shaped + S-bend route:
     * Leg 1: North (19.000, 72.870) -> (19.010, 72.870)
     * Leg 2: East  (19.010, 72.870) -> (19.010, 72.880)  (90° right turn)
     * Leg 3: South (19.010, 72.880) -> (19.000, 72.880)  (90° right turn, now heading South!)
     */
    private fun buildCurvedBendingRoute(): RouteData {
        val a = GeoPoint(19.000, 72.870)
        val b = GeoPoint(19.010, 72.870)
        val c = GeoPoint(19.010, 72.880)
        val d = GeoPoint(19.000, 72.880)
        return RouteData(
            id = "route_curved",
            name = "Curved Bypass",
            fileName = "curved.kmz",
            markers = listOf(
                RouteMarker("m0", "KM 0+000", Chainage.fromMetres(0L), a),
                RouteMarker("m1", "KM 1+110", Chainage.fromMetres(1_110L), b),
                RouteMarker("m2", "KM 2+160", Chainage.fromMetres(2_160L), c),
                RouteMarker("m3", "KM 3+270", Chainage.fromMetres(3_270L), d)
            ),
            alignments = listOf(
                RouteAlignment(
                    id = "align_curved",
                    name = "Curved Bypass CL",
                    points = listOf(a, b, c, d),
                    carriagewayType = "MAIN",
                    direction = "INCREASING"
                )
            ),
            bounds = GeoBounds(19.000, 19.010, 72.870, 72.880),
            startChainage = Chainage.fromMetres(0L),
            endChainage = Chainage.fromMetres(3_270L)
        )
    }

    /**
     * Dual-carriageway KMZ with separate explicit LHS (west, -12m) and RHS (east, +12m) polylines.
     */
    private fun buildDualCarriagewayRoute(): RouteData {
        val dLng12m = metresToLngDeg(12.0, 19.005)
        val lhsPts = listOf(GeoPoint(19.000, 72.870 - dLng12m), GeoPoint(19.020, 72.870 - dLng12m))
        val rhsPts = listOf(GeoPoint(19.000, 72.870 + dLng12m), GeoPoint(19.020, 72.870 + dLng12m))
        return RouteData(
            id = "route_dual",
            name = "Expressway Dual",
            fileName = "dual.kmz",
            markers = listOf(
                RouteMarker("m0", "KM 10+000", Chainage.fromMetres(10_000L), GeoPoint(19.000, 72.870)),
                RouteMarker("m1", "KM 12+000", Chainage.fromMetres(12_000L), GeoPoint(19.020, 72.870))
            ),
            alignments = listOf(
                RouteAlignment("align_lhs", "Expressway LHS", lhsPts, carriagewayType = "LHS", direction = "INCREASING"),
                RouteAlignment("align_rhs", "Expressway RHS", rhsPts, carriagewayType = "RHS", direction = "INCREASING")
            ),
            bounds = GeoBounds(19.000, 19.020, 72.869, 72.871),
            startChainage = Chainage.fromMetres(10_000L),
            endChainage = Chainage.fromMetres(12_000L)
        )
    }

    private fun metresToLatDeg(metres: Double): Double = metres / 111_111.0
    private fun metresToLngDeg(metres: Double, latDeg: Double): Double =
        metres / (111_111.0 * cos(Math.toRadians(latDeg)))

    /**
     * Builds a GPS point at [lat] with signed east offset [eastOffsetMetres] relative to 72.870.
     * Negative eastOffsetMetres => West (LHS on north-going route); positive => East (RHS).
     */
    private fun nsLocation(
        lat: Double = 19.005,
        eastOffsetMetres: Double,
        nowMs: Long,
        speedKmh: Double = 0.0,
        accuracyM: Float = 5.0f,
        status: LocationStatus = LocationStatus.READY
    ): LocationState {
        val pt = GeoPoint(lat, 72.870 + metresToLngDeg(eastOffsetMetres, lat))
        return LocationState(
            status = status,
            point = pt,
            accuracy = accuracyM,
            speed = (speedKmh / 3.6).toFloat(),
            bearing = 0f,
            lastFixTime = nowMs
        )
    }

    private fun matchStub(chainageM: Long = 200_550L): ChainageMatchResult = ChainageMatchResult(
        chainage = Chainage.fromMetres(chainageM),
        confidence = MatchConfidence.HIGH,
        gpsAccuracy = 5f,
        source = ChainageSource.KMZ,
        matchedAlignmentName = "NH-48 Centerline"
    )

    // ------------------------------------------------------------------
    // Scenario 1: Manual LHS remains active despite GPS updates on RHS
    // ------------------------------------------------------------------
    @Test
    fun scenario01_manualLhsRemainsActiveDespiteGpsUpdates() {
        val route = buildNorthSouthRoute()
        val manager = SmartDirectionManager()
        val prefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "LHS",
            manualSelectedAtMs = 1_000_000L
        )
        manager.onManualDirectionSelected("LHS", 1_000_000L)

        // Multiple reliable GPS updates clearly on RHS side (+10m east) at walking speed (< 5 km/h)
        for (i in 1..6) {
            val t = 1_000_000L + i * 30_000L // up to 3 minutes elapsed (< 10 min)
            val state = manager.evaluate(
                locationState = nsLocation(eastOffsetMetres = +10.0, nowMs = t, speedKmh = 2.0),
                matchResult = matchStub(),
                routeData = route,
                preferences = prefs,
                nowMs = t
            )
            assertTrue(state.isManualOverride)
            assertEquals("LHS", state.confirmedSide)
            assertEquals(DirectionDetectionState.LHS_CONFIRMED, state.detectionState)
            assertNull(state.autoReactivatedReason)
        }
    }

    // ------------------------------------------------------------------
    // Scenario 2: Manual RHS remains active despite GPS updates on LHS
    // ------------------------------------------------------------------
    @Test
    fun scenario02_manualRhsRemainsActiveDespiteGpsUpdates() {
        val route = buildNorthSouthRoute()
        val manager = SmartDirectionManager()
        val prefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "RHS",
            manualSelectedAtMs = 1_000_000L
        )
        manager.onManualDirectionSelected("RHS", 1_000_000L)

        // Multiple reliable GPS updates clearly on LHS side (-10m west) at < 5 km/h
        for (i in 1..6) {
            val t = 1_000_000L + i * 45_000L // up to 4.5 min (< 10 min)
            val state = manager.evaluate(
                locationState = nsLocation(eastOffsetMetres = -10.0, nowMs = t, speedKmh = 3.5),
                matchResult = matchStub(),
                routeData = route,
                preferences = prefs,
                nowMs = t
            )
            assertTrue(state.isManualOverride)
            assertEquals("RHS", state.confirmedSide)
            assertEquals(DirectionDetectionState.RHS_CONFIRMED, state.detectionState)
        }
    }

    // ------------------------------------------------------------------
    // Scenario 3: BOTH, MEDIAN, and NONE remain unchanged while manual override is valid
    // ------------------------------------------------------------------
    @Test
    fun scenario03_bothMedianAndNoneRemainUnchangedWhileManualValid() {
        val route = buildNorthSouthRoute()
        val cases = listOf(
            Triple(RoadSide.BOTH, "BHS", "BHS"),
            Triple(RoadSide.MEDIAN, "MEDIAN", "MEDIAN"),
            Triple(RoadSide.NONE, "NONE", null)
        )

        for ((roadSide, expectedConfirmed, expectedOverlayLabel) in cases) {
            val manager = SmartDirectionManager()
            val prefs = SmartDirectionPreferences(
                controlMode = DirectionControlMode.MANUAL_OVERRIDE,
                manualOverrideSide = roadSide.label,
                manualSelectedAtMs = 2_000_000L
            )
            manager.onManualDirectionSelected(roadSide.label, 2_000_000L)

            val state = manager.evaluate(
                locationState = nsLocation(eastOffsetMetres = -12.0, nowMs = 2_120_000L, speedKmh = 1.0),
                matchResult = matchStub(),
                routeData = route,
                preferences = prefs,
                nowMs = 2_120_000L
            )
            assertTrue("Expected manual override for $roadSide", state.isManualOverride)
            assertEquals(expectedConfirmed, state.confirmedSide)
            assertEquals(
                expectedOverlayLabel,
                OverlayTextGenerator.resolveAuthoritativeDisplaySide(state, ProjectProfile(roadSide = roadSide))
            )
        }
    }

    // ------------------------------------------------------------------
    // Scenario 4: Taking a photograph resets the 10-minute inactivity timer
    // ------------------------------------------------------------------
    @Test
    fun scenario04_photoCaptureResetsTenMinuteInactivityTimer() {
        val route = buildNorthSouthRoute()
        val manager = SmartDirectionManager()
        val t0 = 10_000_000L
        val prefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "RHS",
            manualSelectedAtMs = t0
        )
        manager.onManualDirectionSelected("RHS", t0)

        // At t = 8 min: still manual RHS
        val t8m = t0 + 8L * 60_000L
        val s8m = manager.evaluate(nsLocation(eastOffsetMetres = -8.0, nowMs = t8m), matchStub(), route, prefs, nowMs = t8m)
        assertTrue(s8m.isManualOverride)
        assertEquals("RHS", s8m.confirmedSide)

        // User captures a photograph at t = 8 min -> resets 10-min timer!
        manager.onPhotoCaptured(t8m)

        // At t = 15 min (7 min after photo, 15 min after initial selection): MUST STILL BE MANUAL RHS!
        val t15m = t0 + 15L * 60_000L
        val s15m = manager.evaluate(nsLocation(eastOffsetMetres = -8.0, nowMs = t15m), matchStub(), route, prefs, nowMs = t15m)
        assertTrue("Photo at 8m must extend manual override past 15m", s15m.isManualOverride)
        assertEquals("RHS", s15m.confirmedSide)
        assertNull(s15m.autoReactivatedReason)

        // At t = 18 min + 1 sec (10m 1s after last photo): expires to AUTO and detects LHS from KMZ offset (-8m west)!
        val t18m = t8m + SmartDirectionManager.MANUAL_INACTIVITY_TIMEOUT_MS + 1_000L
        val s18m = manager.evaluate(nsLocation(eastOffsetMetres = -8.0, nowMs = t18m), matchStub(), route, prefs, nowMs = t18m)
        assertFalse("Manual override must expire 10m after last photo", s18m.isManualOverride)
        assertEquals(DirectionAutoReactivationReason.PHOTO_INACTIVITY_TIMEOUT, s18m.autoReactivatedReason)
        assertEquals("LHS", s18m.confirmedSide)
    }

    // ------------------------------------------------------------------
    // Scenario 5: No photo for 10 continuous minutes expires manual override (GPS updates do NOT reset timer!)
    // ------------------------------------------------------------------
    @Test
    fun scenario05_tenMinutesWithoutPhotoExpiresManualEvenWithContinuousGpsUpdates() {
        val route = buildNorthSouthRoute()
        val manager = SmartDirectionManager()
        val t0 = 20_000_000L
        val prefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "MEDIAN",
            manualSelectedAtMs = t0
        )
        manager.onManualDirectionSelected("MEDIAN", t0)

        // Feed GPS updates every minute for 9 minutes — must NOT reset inactivity timer!
        for (min in 1..9) {
            val t = t0 + min * 60_000L
            val state = manager.evaluate(
                locationState = nsLocation(eastOffsetMetres = +9.0, nowMs = t, speedKmh = 1.5),
                matchResult = matchStub(),
                routeData = route,
                preferences = prefs,
                nowMs = t
            )
            assertTrue("Still manual at ${min}m", state.isManualOverride)
            assertEquals("MEDIAN", state.confirmedSide)
        }

        // Exactly at 10 minutes with no photo capture -> expires to AUTO -> KMZ offset (+9m east) => RHS!
        val t10m = t0 + SmartDirectionManager.MANUAL_INACTIVITY_TIMEOUT_MS
        val expired = manager.evaluate(
            locationState = nsLocation(eastOffsetMetres = +9.0, nowMs = t10m, speedKmh = 1.5),
            matchResult = matchStub(),
            routeData = route,
            preferences = prefs,
            nowMs = t10m
        )
        assertFalse(expired.isManualOverride)
        assertEquals(DirectionAutoReactivationReason.PHOTO_INACTIVITY_TIMEOUT, expired.autoReactivatedReason)
        assertEquals("RHS", expired.confirmedSide)
        assertEquals(DirectionDetectionState.RHS_CONFIRMED, expired.detectionState)
    }

    // ------------------------------------------------------------------
    // Scenario 6: Active video recording prevents inactivity expiry
    // ------------------------------------------------------------------
    @Test
    fun scenario06_activeVideoRecordingPreventsTenMinuteTimeout() {
        val route = buildNorthSouthRoute()
        val manager = SmartDirectionManager()
        val t0 = 30_000_000L
        val prefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "BHS",
            manualSelectedAtMs = t0
        )
        manager.onManualDirectionSelected("BHS", t0)

        // Start video recording at t = 2m
        manager.onVideoRecordingStateChanged(true, t0 + 2L * 60_000L)

        // At t = 14m (well past 10m!), video is STILL recording -> MUST NOT EXPIRE!
        val t14m = t0 + 14L * 60_000L
        val duringRec = manager.evaluate(
            locationState = nsLocation(eastOffsetMetres = -9.0, nowMs = t14m, speedKmh = 0.0),
            matchResult = matchStub(),
            routeData = route,
            preferences = prefs,
            nowMs = t14m
        )
        assertTrue("Active video recording must prevent 10-min inactivity expiry", duringRec.isManualOverride)
        assertEquals("BHS", duringRec.confirmedSide)
        assertNull(duringRec.autoReactivatedReason)

        // Stop video recording at t = 14m -> resets inactivity anchor from end of active capture
        manager.onVideoRecordingStateChanged(false, t14m)

        // At t = 23m (9m after recording ended): still manual
        val t23m = t14m + 9L * 60_000L
        val afterStop9m = manager.evaluate(
            locationState = nsLocation(eastOffsetMetres = -9.0, nowMs = t23m, speedKmh = 0.0),
            matchResult = matchStub(),
            routeData = route,
            preferences = prefs,
            nowMs = t23m
        )
        assertTrue(afterStop9m.isManualOverride)
        assertEquals("BHS", afterStop9m.confirmedSide)

        // At t = 24m (10m after recording ended): expires cleanly to AUTO -> LHS (-9m west)
        val t24m = t14m + SmartDirectionManager.MANUAL_INACTIVITY_TIMEOUT_MS
        val afterStop10m = manager.evaluate(
            locationState = nsLocation(eastOffsetMetres = -9.0, nowMs = t24m, speedKmh = 0.0),
            matchResult = matchStub(),
            routeData = route,
            preferences = prefs,
            nowMs = t24m
        )
        assertFalse(afterStop10m.isManualOverride)
        assertEquals(DirectionAutoReactivationReason.PHOTO_INACTIVITY_TIMEOUT, afterStop10m.autoReactivatedReason)
        assertEquals("LHS", afterStop10m.confirmedSide)
    }

    // ------------------------------------------------------------------
    // Scenario 7: Reliable >= 5 km/h triggers AUTO & overrides manual; single noisy reading does NOT
    // ------------------------------------------------------------------
    @Test
    fun scenario07_reliableFiveKmhTriggersAutoAndSingleNoisyReadingIsIgnored() {
        val route = buildNorthSouthRoute()
        val manager = SmartDirectionManager()
        val t0 = 40_000_000L
        val prefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "RHS",
            manualSelectedAtMs = t0
        )
        manager.onManualDirectionSelected("RHS", t0)

        // 7a. Inaccurate GPS spike (accuracy = 45m, speed = 22 km/h) -> MUST NOT trigger!
        val noisyBadAcc = manager.evaluate(
            locationState = nsLocation(eastOffsetMetres = -10.0, nowMs = t0 + 2_000L, speedKmh = 22.0, accuracyM = 45f),
            matchResult = matchStub(),
            routeData = route,
            preferences = prefs,
            nowMs = t0 + 2_000L
        )
        assertTrue("Inaccurate GPS speed spike must not cancel manual override", noisyBadAcc.isManualOverride)
        assertEquals("RHS", noisyBadAcc.confirmedSide)

        // 7b. Single good-accuracy glitch (8 km/h) followed by stationary (0.5 km/h) -> MUST NOT trigger!
        val singleSpike = manager.evaluate(
            locationState = nsLocation(eastOffsetMetres = -10.0, nowMs = t0 + 4_000L, speedKmh = 8.0, accuracyM = 6f),
            matchResult = matchStub(),
            routeData = route,
            preferences = prefs,
            nowMs = t0 + 4_000L
        )
        assertTrue("Single reading >= 5 km/h must wait for consecutive confirmation", singleSpike.isManualOverride)
        assertEquals("RHS", singleSpike.confirmedSide)

        val backToStopped = manager.evaluate(
            locationState = nsLocation(eastOffsetMetres = -10.0, nowMs = t0 + 5_000L, speedKmh = 0.5, accuracyM = 6f),
            matchResult = matchStub(),
            routeData = route,
            preferences = prefs,
            nowMs = t0 + 5_000L
        )
        assertTrue(backToStopped.isManualOverride)
        assertEquals("RHS", backToStopped.confirmedSide)

        // 7c. Two consecutive reliable readings >= 5.0 km/h -> IMMEDIATELY activates AUTO & detects LHS (-10m west)!
        val step1 = manager.evaluate(
            locationState = nsLocation(eastOffsetMetres = -10.0, nowMs = t0 + 10_000L, speedKmh = 5.0, accuracyM = 5f),
            matchResult = matchStub(),
            routeData = route,
            preferences = prefs,
            nowMs = t0 + 10_000L
        )
        assertTrue("First consecutive reading still waits for confirmation", step1.isManualOverride)

        val step2 = manager.evaluate(
            locationState = nsLocation(eastOffsetMetres = -10.0, nowMs = t0 + 11_000L, speedKmh = 5.4, accuracyM = 5f),
            matchResult = matchStub(),
            routeData = route,
            preferences = prefs,
            nowMs = t0 + 11_000L
        )
        assertFalse("Consecutive reliable >= 5 km/h must override manual and activate AUTO", step2.isManualOverride)
        assertEquals(DirectionAutoReactivationReason.VEHICLE_SPEED_TRIGGER, step2.autoReactivatedReason)
        assertEquals("LHS", step2.confirmedSide)
        assertEquals(DirectionDetectionState.LHS_CONFIRMED, step2.detectionState)
    }

    // ------------------------------------------------------------------
    // Scenario 8: Speed fluctuations around 5 km/h never oscillate or restore expired manual
    // ------------------------------------------------------------------
    @Test
    fun scenario08_speedFluctuationsAroundFiveKmhNeverOscillateOrRestoreExpiredManual() {
        val route = buildNorthSouthRoute()
        val manager = SmartDirectionManager()
        val t0 = 50_000_000L
        val staleManualPrefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "RHS",
            manualSelectedAtMs = t0
        )
        manager.onManualDirectionSelected("RHS", t0)

        // Trigger AUTO via >= 5 km/h while on LHS (-8m west)
        manager.evaluate(nsLocation(eastOffsetMetres = -8.0, nowMs = t0 + 1_000L, speedKmh = 5.5), matchStub(), route, staleManualPrefs, nowMs = t0 + 1_000L)
        val triggered = manager.evaluate(nsLocation(eastOffsetMetres = -8.0, nowMs = t0 + 2_000L, speedKmh = 5.8), matchStub(), route, staleManualPrefs, nowMs = t0 + 2_000L)
        assertFalse(triggered.isManualOverride)
        assertEquals("LHS", triggered.confirmedSide)

        // Speed fluctuates across 5 km/h boundary (4.2 -> 5.1 -> 3.8 -> 5.4 -> 0.0 km/h)
        val speeds = listOf(4.2, 5.1, 3.8, 5.4, 2.0, 0.0)
        var t = t0 + 3_000L
        for (spd in speeds) {
            // Even if preferences weren't cleared yet, expired manual MUST NEVER be restored!
            val state = manager.evaluate(
                locationState = nsLocation(eastOffsetMetres = -8.0, nowMs = t, speedKmh = spd),
                matchResult = matchStub(),
                routeData = route,
                preferences = staleManualPrefs,
                nowMs = t
            )
            assertFalse("Must stay in AUTO when speed drops to $spd km/h", state.isManualOverride)
            assertEquals("Direction must remain steadily LHS without oscillating", "LHS", state.confirmedSide)
            assertNull("No spurious direction change event", state.directionChangedFrom)
            t += 1_500L
        }
    }

    // ------------------------------------------------------------------
    // Scenario 9: Automatic LHS/RHS follows KMZ/KML offset dynamically & filters single GPS spike
    // ------------------------------------------------------------------
    @Test
    fun scenario09_autoDirectionFollowsKmzOffsetDynamicallyAndFiltersSingleNoiseSpike() {
        val route = buildNorthSouthRoute()
        val manager = SmartDirectionManager()
        val autoPrefs = SmartDirectionPreferences(controlMode = DirectionControlMode.AUTO)

        // Initial position on LHS (-7m west of north-going centerline) -> LHS confirmed immediately
        val s1 = manager.evaluate(nsLocation(eastOffsetMetres = -7.0, nowMs = 1_000L), matchStub(), route, autoPrefs, nowMs = 1_000L)
        assertEquals("LHS", s1.confirmedSide)
        assertEquals(DirectionDetectionState.LHS_CONFIRMED, s1.detectionState)

        // Single noisy GPS jump to RHS (+6m east) -> MUST NOT flip immediately!
        val sNoise = manager.evaluate(nsLocation(eastOffsetMetres = +6.0, nowMs = 2_000L), matchStub(), route, autoPrefs, nowMs = 2_000L)
        assertEquals("Single noisy GPS spike must not flip LHS to RHS", "LHS", sNoise.confirmedSide)
        assertNull(sNoise.directionChangedFrom)

        // Next reading returns to LHS (-6.5m west) -> pending cleared, no flip occurred
        val sBack = manager.evaluate(nsLocation(eastOffsetMetres = -6.5, nowMs = 3_000L), matchStub(), route, autoPrefs, nowMs = 3_000L)
        assertEquals("LHS", sBack.confirmedSide)
        assertNull(sBack.pendingCandidateSide)

        // Genuine crossing to RHS (+7m east) across 2 consecutive reliable readings -> cleanly switches to RHS!
        manager.evaluate(nsLocation(eastOffsetMetres = +7.0, nowMs = 4_000L), matchStub(), route, autoPrefs, nowMs = 4_000L)
        val sRhs = manager.evaluate(nsLocation(eastOffsetMetres = +7.5, nowMs = 5_000L), matchStub(), route, autoPrefs, nowMs = 5_000L)
        assertEquals("RHS", sRhs.confirmedSide)
        assertEquals(DirectionDetectionState.RHS_CONFIRMED, sRhs.detectionState)
        assertEquals("LHS", sRhs.directionChangedFrom)

        // Dual-carriageway KMZ route with explicit LHS & RHS polylines
        val dualRoute = buildDualCarriagewayRoute()
        val dualManager = SmartDirectionManager()
        val onLhsCarriageway = dualManager.evaluate(
            locationState = nsLocation(eastOffsetMetres = -11.5, nowMs = 10_000L),
            matchResult = matchStub(),
            routeData = dualRoute,
            preferences = autoPrefs,
            nowMs = 10_000L
        )
        assertEquals("LHS", onLhsCarriageway.confirmedSide)

        dualManager.evaluate(nsLocation(eastOffsetMetres = +11.5, nowMs = 11_000L), matchStub(), dualRoute, autoPrefs, nowMs = 11_000L)
        val onRhsCarriageway = dualManager.evaluate(
            locationState = nsLocation(eastOffsetMetres = +12.0, nowMs = 12_000L),
            matchResult = matchStub(),
            routeData = dualRoute,
            preferences = autoPrefs,
            nowMs = 12_000L
        )
        assertEquals("RHS", onRhsCarriageway.confirmedSide)
        assertEquals("LHS", onRhsCarriageway.directionChangedFrom)

        // Decreasing-chainage KMZ alignment flips geometric left/right relative to increasing chainage
        val decRoute = buildNorthSouthRoute(routeId = "route_dec", directionMeta = "DECREASING")
        val decManager = SmartDirectionManager()
        // Facing increasing chainage (South on a decreasing-north polyline): East (+8m) is LEFT = LHS!
        val decLhs = decManager.evaluate(
            locationState = nsLocation(eastOffsetMetres = +8.0, nowMs = 20_000L),
            matchResult = matchStub(),
            routeData = decRoute,
            preferences = autoPrefs,
            nowMs = 20_000L
        )
        assertEquals("LHS", decLhs.confirmedSide)
    }

    // ------------------------------------------------------------------
    // Scenario 10: Curved, 90° bending, and irregular KMZ/KML routes
    // ------------------------------------------------------------------
    @Test
    fun scenario10_curvedAndBendingKmzRoutesDetermineLhsRhsAccurately() {
        val curvedRoute = buildCurvedBendingRoute()
        val autoPrefs = SmartDirectionPreferences(controlMode = DirectionControlMode.AUTO)

        // Leg 1 (heading North along lng 72.870): West = LHS, East = RHS
        val m1 = SmartDirectionManager()
        val leg1Lhs = m1.evaluate(
            locationState = LocationState(
                status = LocationStatus.READY,
                point = GeoPoint(19.005, 72.870 - metresToLngDeg(8.0, 19.005)),
                accuracy = 4f,
                lastFixTime = 1_000L
            ),
            matchResult = matchStub(),
            routeData = curvedRoute,
            preferences = autoPrefs,
            nowMs = 1_000L
        )
        assertEquals("LHS", leg1Lhs.confirmedSide)

        // Corner Vertex B (19.010, 72.870) turning 90° from North -> East:
        // Outer corner NW (-8m W, +8m N) is LEFT (LHS); Inner corner SE (+8m E, -8m S) is RIGHT (RHS)
        val mCornerOut = SmartDirectionManager()
        val cornerOuter = mCornerOut.evaluate(
            locationState = LocationState(
                status = LocationStatus.READY,
                point = GeoPoint(19.010 + metresToLatDeg(8.0), 72.870 - metresToLngDeg(8.0, 19.010)),
                accuracy = 4f,
                lastFixTime = 2_000L
            ),
            matchResult = matchStub(),
            routeData = curvedRoute,
            preferences = autoPrefs,
            nowMs = 2_000L
        )
        assertEquals("Outer NW corner of North->East 90° bend must be LHS", "LHS", cornerOuter.confirmedSide)

        val mCornerIn = SmartDirectionManager()
        val cornerInner = mCornerIn.evaluate(
            locationState = LocationState(
                status = LocationStatus.READY,
                point = GeoPoint(19.010 - metresToLatDeg(8.0), 72.870 + metresToLngDeg(8.0, 19.010)),
                accuracy = 4f,
                lastFixTime = 2_000L
            ),
            matchResult = matchStub(),
            routeData = curvedRoute,
            preferences = autoPrefs,
            nowMs = 2_000L
        )
        assertEquals("Inner SE corner of North->East 90° bend must be RHS", "RHS", cornerInner.confirmedSide)

        // Leg 2 (heading East along lat 19.010): North (+8m lat) = LHS, South (-8m lat) = RHS
        val m2 = SmartDirectionManager()
        val leg2Rhs = m2.evaluate(
            locationState = LocationState(
                status = LocationStatus.READY,
                point = GeoPoint(19.010 - metresToLatDeg(8.0), 72.875),
                accuracy = 4f,
                lastFixTime = 3_000L
            ),
            matchResult = matchStub(),
            routeData = curvedRoute,
            preferences = autoPrefs,
            nowMs = 3_000L
        )
        assertEquals("South of East-going leg must be RHS", "RHS", leg2Rhs.confirmedSide)

        // Leg 3 (heading South along lng 72.880): East (+8m lng) = LHS, West (-8m lng) = RHS!
        val m3 = SmartDirectionManager()
        val leg3Lhs = m3.evaluate(
            locationState = LocationState(
                status = LocationStatus.READY,
                point = GeoPoint(19.005, 72.880 + metresToLngDeg(8.0, 19.005)),
                accuracy = 4f,
                lastFixTime = 4_000L
            ),
            matchResult = matchStub(),
            routeData = curvedRoute,
            preferences = autoPrefs,
            nowMs = 4_000L
        )
        assertEquals("East of South-going leg must be LHS", "LHS", leg3Lhs.confirmedSide)
    }

    // ------------------------------------------------------------------
    // Scenario 11: Global app-wide synchronization across screens, overlays, ViewModel & metadata
    // ------------------------------------------------------------------
    @Test
    fun scenario11_directionSynchronizesConsistentlyAcrossModulesAndViewModel() {
        val route = buildNorthSouthRoute()
        val viewModel = MainViewModel(app)

        // Manual MEDIAN selected -> consistent across ViewModel, Hub, Camera display, Overlay, and Preferences!
        viewModel.updateSide(RoadSide.MEDIAN)
        val medianState = viewModel.smartDirectionState.value
        assertTrue(medianState.isManualOverride)
        assertEquals("MEDIAN", medianState.confirmedSide)
        assertEquals("MEDIAN", CtecsNavigationTelemetryHub.directionState.value.confirmedSide)
        assertEquals(
            CameraSideOption.MEDIAN,
            resolveCameraSideDisplayState(medianState, viewModel.projectProfile.value).activeOption
        )
        assertEquals(
            "MEDIAN",
            OverlayTextGenerator.resolveAuthoritativeCaptureSide(viewModel.createCurrentSnapshot()).first
        )

        // Manual BOTH -> consistent across Camera (BHS), Overlay (BHS), ViewModel, and Hub!
        viewModel.updateSide(RoadSide.BOTH)
        val bothState = viewModel.smartDirectionState.value
        assertTrue(bothState.isManualOverride)
        assertEquals("BHS", bothState.confirmedSide)
        assertEquals("BHS", OverlayTextGenerator.resolveAuthoritativeCaptureSide(viewModel.createCurrentSnapshot()).first)
        assertEquals(CameraSideOption.BHS, resolveCameraSideDisplayState(bothState, viewModel.projectProfile.value).activeOption)

        // Manual NONE -> omits side cleanly from capture overlay/metadata while showing NONE on Camera pill
        viewModel.updateSide(RoadSide.NONE)
        val noneState = viewModel.smartDirectionState.value
        assertTrue(noneState.isManualOverride)
        assertEquals("NONE", noneState.confirmedSide)
        assertNull(OverlayTextGenerator.resolveAuthoritativeCaptureSide(viewModel.createCurrentSnapshot()).first)
        assertEquals(CameraSideOption.NONE, resolveCameraSideDisplayState(noneState, viewModel.projectProfile.value).activeOption)

        // Centralized AUTO KMZ offset result synchronizes across Overlay, LockScreenNavResolver & NextKmStoneTracker
        val manager = SmartDirectionManager()
        val autoRhs = manager.evaluate(
            locationState = nsLocation(lat = 19.012, eastOffsetMetres = +9.0, nowMs = 60_000L, speedKmh = 40.0),
            matchResult = matchStub(201_330L),
            routeData = route,
            preferences = SmartDirectionPreferences(controlMode = DirectionControlMode.AUTO),
            nowMs = 60_000L
        )
        assertEquals("RHS", autoRhs.confirmedSide)
        val syncedMatch = matchStub(201_330L).copy(detectedSide = "RHS", smartDirectionState = autoRhs)
        val oppositeProfile = ProjectProfile(highwayId = "NH-48", roadSide = RoadSide.LHS)

        val snap = CaptureSnapshot(
            timestamp = 60_000L,
            location = nsLocation(lat = 19.012, eastOffsetMetres = +9.0, nowMs = 60_000L).point,
            matchResult = syncedMatch,
            projectProfile = oppositeProfile
        )
        assertEquals("RHS", OverlayTextGenerator.resolveAuthoritativeCaptureSide(snap).first)

        val navUi = LockScreenNavStateResolver().resolve(
            locationState = nsLocation(lat = 19.012, eastOffsetMetres = +9.0, nowMs = 60_000L, speedKmh = 40.0),
            matchResult = syncedMatch,
            routeData = route,
            projectProfile = oppositeProfile,
            locationName = "NH-48",
            nowMs = 60_000L
        )
        assertEquals("RHS", navUi.reliableSide)

        val nextKm = NextKmStoneTracker().evaluate(syncedMatch, route, "RHS", true, autoRhs)
        assertEquals("RHS", nextKm.directionSide)
        assertEquals("201+000", nextKm.nextChainage)

        // Preferences JSON serialization round-trip preserves manualSelectedAtMs
        val prefsRoundTrip = SmartDirectionPreferences.fromJsonString(
            SmartDirectionPreferences(
                controlMode = DirectionControlMode.MANUAL_OVERRIDE,
                manualOverrideSide = "MEDIAN",
                manualSelectedAtMs = 123_456_789L
            ).toJsonString()
        )
        assertEquals(DirectionControlMode.MANUAL_OVERRIDE, prefsRoundTrip.controlMode)
        assertEquals("MEDIAN", prefsRoundTrip.normalizedManualSide)
        assertEquals(123_456_789L, prefsRoundTrip.manualSelectedAtMs)
    }

    // ------------------------------------------------------------------
    // Scenario 12: Missing KMZ/KML or weak/unreliable GPS never invents LHS or RHS
    // ------------------------------------------------------------------
    @Test
    fun scenario12_missingKmzOrWeakGpsNeverInventsFalseDirection() {
        val route = buildNorthSouthRoute()
        val autoPrefs = SmartDirectionPreferences(controlMode = DirectionControlMode.AUTO)

        // 12a. Missing KMZ/KML route -> ALIGNMENT_UNAVAILABLE, confirmedSide == null
        val mNoRoute = SmartDirectionManager()
        val noRouteState = mNoRoute.evaluate(
            locationState = nsLocation(eastOffsetMetres = -8.0, nowMs = 1_000L),
            matchResult = ChainageMatchResult.noRoute(),
            routeData = null,
            preferences = autoPrefs,
            nowMs = 1_000L
        )
        assertEquals(DirectionDetectionState.ALIGNMENT_UNAVAILABLE, noRouteState.detectionState)
        assertNull("Missing route must never invent LHS/RHS", noRouteState.confirmedSide)

        // 12b. Cold start with weak GPS (accuracy 55m) -> GPS_SIGNAL_WEAK, confirmedSide == null
        val mWeak = SmartDirectionManager()
        val weakState = mWeak.evaluate(
            locationState = nsLocation(eastOffsetMetres = -8.0, nowMs = 2_000L, accuracyM = 55f),
            matchResult = matchStub(),
            routeData = route,
            preferences = autoPrefs,
            nowMs = 2_000L
        )
        assertEquals(DirectionDetectionState.GPS_SIGNAL_WEAK, weakState.detectionState)
        assertNull("Weak GPS on cold start must never invent LHS/RHS", weakState.confirmedSide)

        // 12c. Cold start directly on centerline (0.2m within deadband) -> DIRECTION_UNCERTAIN, confirmedSide == null
        val mCenter = SmartDirectionManager()
        val centerState = mCenter.evaluate(
            locationState = nsLocation(eastOffsetMetres = 0.2, nowMs = 3_000L, accuracyM = 5f),
            matchResult = matchStub(),
            routeData = route,
            preferences = autoPrefs,
            nowMs = 3_000L
        )
        assertEquals(DirectionDetectionState.DIRECTION_UNCERTAIN, centerState.detectionState)
        assertNull("Centerline cold start must never invent LHS/RHS", centerState.confirmedSide)

        // 12d. Cold start far off-corridor (+350m east) -> DIRECTION_UNCERTAIN, confirmedSide == null
        val mOff = SmartDirectionManager()
        val offState = mOff.evaluate(
            locationState = nsLocation(eastOffsetMetres = +350.0, nowMs = 4_000L, accuracyM = 5f),
            matchResult = matchStub(),
            routeData = route,
            preferences = autoPrefs,
            nowMs = 4_000L
        )
        assertEquals(DirectionDetectionState.DIRECTION_UNCERTAIN, offState.detectionState)
        assertNull("Off-corridor GPS must never invent LHS/RHS", offState.confirmedSide)

        // 12e. After reliable LHS confirmation, temporary weak GPS retains last reliable LHS without flipping!
        val mRetain = SmartDirectionManager()
        val confirmed = mRetain.evaluate(nsLocation(eastOffsetMetres = -8.0, nowMs = 5_000L, accuracyM = 5f), matchStub(), route, autoPrefs, nowMs = 5_000L)
        assertEquals("LHS", confirmed.confirmedSide)

        val tempWeak = mRetain.evaluate(nsLocation(eastOffsetMetres = +12.0, nowMs = 6_000L, accuracyM = 60f), matchStub(), route, autoPrefs, nowMs = 6_000L)
        assertEquals("Temporary weak GPS must retain last reliable LHS", "LHS", tempWeak.confirmedSide)

        // Unloading route clears retained direction immediately -> ALIGNMENT_UNAVAILABLE (null)
        val unloaded = mRetain.evaluate(nsLocation(eastOffsetMetres = -8.0, nowMs = 7_000L, accuracyM = 5f), ChainageMatchResult.noRoute(), null, autoPrefs, nowMs = 7_000L)
        assertEquals(DirectionDetectionState.ALIGNMENT_UNAVAILABLE, unloaded.detectionState)
        assertNull(unloaded.confirmedSide)
    }
}
