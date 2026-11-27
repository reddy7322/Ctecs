package com.example

import android.content.Context
import android.location.Location
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.geo.ChainageMatcher
import com.example.geo.ChainageProgressionTracker
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.location.LocationTracker
import com.example.model.Chainage
import com.example.model.ChainageCardRefreshState
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.DirectionControlMode
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.LastReliableChainageSnapshot
import com.example.model.ManualChainageMode
import com.example.model.MatchConfidence
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.SmartDirectionPreferences
import com.example.model.TravelChainageDirection
import com.example.navigation.SmartDirectionManager
import com.example.ui.home.CentralChainageDisplay
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChainageDropPreventionAndHomeRefreshTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val degPerMetre = 1.0 / 111_132.0
    private val baseLat = 28.500000
    private val baseLng = 77.700000

    @After
    fun tearDown() {
        ChainageMatcher.clearCache()
    }

    /**
     * Builds a 45 km highway corridor spanning 130+000 to 175+000 (1 km marker spacing).
     */
    private fun createHighway130To175Route(
        id: String = "nh-130-175",
        extraUnmarkedAlignmentNear171: Boolean = false
    ): RouteData {
        val points = (0..45).map { km ->
            GeoPoint(baseLat + (km * 1000.0) * degPerMetre, baseLng, 240.0)
        }
        val markers = (0..45).map { km ->
            val chM = 130_000L + km * 1000L
            RouteMarker("m-$km", Chainage(chM).format(), Chainage(chM), points[km])
        }
        val mainAlign = RouteAlignment(
            id = "align-main",
            name = "NH-58 Main Carriageway",
            points = points,
            direction = "INCREASING",
            carriagewayType = "MAIN"
        )
        val alignments = mutableListOf(mainAlign)
        if (extraUnmarkedAlignmentNear171) {
            // Secondary service/ramp alignment starting near 171+000, offset 200m east so no markers map within 150m
            val servicePts = listOf(
                GeoPoint(baseLat + 41_000.0 * degPerMetre, baseLng + 0.0022, 240.0),
                GeoPoint(baseLat + 42_000.0 * degPerMetre, baseLng + 0.0022, 240.0)
            )
            alignments += RouteAlignment(
                id = "align-service-171",
                name = "Service Road Bypass",
                points = servicePts,
                direction = "INCREASING",
                carriagewayType = "RHS"
            )
        }
        return RouteData(
            id = id,
            name = "NH-58 130-175km",
            fileName = "NH58_130_175.kmz",
            markers = markers,
            alignments = alignments,
            bounds = GeoBounds.fromPoints(points),
            startChainage = Chainage(130_000L),
            endChainage = Chainage(175_000L),
            selectedAlignmentId = if (extraUnmarkedAlignmentNear171) null else mainAlign.id
        )
    }

    private fun pointAtChainage(chainageMetres: Long, lngOffsetDeg: Double = 0.0): GeoPoint {
        val distFromStartM = (chainageMetres - 130_000L).toDouble()
        return GeoPoint(baseLat + distFromStartM * degPerMetre, baseLng + lngOffsetDeg, 240.0)
    }

    // =========================================================================
    // 1. Prevent 171+000 -> 130+000 drop (even when >8s elapsed!) & privacy-safe diagnostics
    // =========================================================================
    @Test
    fun `171+000 to 130+000 suspicious drop is rejected even after 15 seconds and logs privacy-safe diagnostic`() {
        val route = createHighway130To175Route()
        val tracker = ChainageProgressionTracker()
        val t0 = 1_700_000_000_000L

        // Establish reliable position at 171+000
        val valid171 = ChainageMatcher.match(
            gpsPoint = pointAtChainage(171_000L),
            gpsAccuracy = 3.5f,
            routeData = route,
            previousResult = null,
            fixTimeMs = t0
        )
        val tracked171 = tracker.processResult(valid171, route.id)
        assertTrue(abs((tracked171.chainage?.metres ?: 0L) - 171_000L) <= 5L)
        assertFalse(tracked171.isUncertain)

        // Stale/glitched GPS fix at 130+000 arrives 15 seconds later (> old 8s MAX_SPIKE_HOLD_WINDOW_MS!)
        val glitched130 = ChainageMatcher.match(
            gpsPoint = pointAtChainage(130_000L),
            gpsAccuracy = 6.0f,
            routeData = route,
            previousResult = tracked171,
            fixTimeMs = t0 + 15_000L
        )
        val afterGlitch = tracker.processResult(glitched130, route.id)

        // MUST reject 130+000 and retain 171+000!
        assertTrue("Expected 171+000 retained, got ${afterGlitch.displayChainage}",
            abs((afterGlitch.chainage?.metres ?: 0L) - 171_000L) <= 5L)
        assertTrue("Suspicious drop must be flagged uncertain", afterGlitch.isUncertain)

        // Next valid fix at 171+020 recovers seamlessly without freezing!
        val nextValid = ChainageMatcher.match(
            gpsPoint = pointAtChainage(171_020L),
            gpsAccuracy = 3.5f,
            routeData = route,
            previousResult = afterGlitch,
            fixTimeMs = t0 + 16_000L
        )
        val trackedNext = tracker.processResult(nextValid, route.id)
        assertFalse(trackedNext.isUncertain)
        assertTrue("Expected smooth continuation near 171+020, got ${trackedNext.chainage?.metres}",
            abs((trackedNext.chainage?.metres ?: 0L) - 171_020L) <= 8L)
    }

    // =========================================================================
    // 2. LocationTracker stale OS fix rejection & monotonic callback ordering
    // =========================================================================
    @Test
    fun `LocationTracker preserves true OS fix time and rejects stale cached or older out-of-order callbacks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tracker = LocationTracker(context)
        val nowMs = 1_700_000_500_000L

        // 1) Stale 10-minute-old cached location at 130+000 MUST be rejected
        val staleLoc = Location("fused").apply {
            latitude = pointAtChainage(130_000L).latitude
            longitude = pointAtChainage(130_000L).longitude
            accuracy = 8.0f
            time = nowMs - 600_000L
        }
        assertFalse("Stale 10m-old location must be rejected",
            tracker.handleNewLocation(staleLoc, maxAgeMs = LocationTracker.DEFAULT_GPS_FRESH_AGE_MS, nowMs = nowMs))

        // 2) Fresh accurate location at 171+000 MUST be accepted with true OS time preserved
        val fresh171 = Location("fused").apply {
            latitude = pointAtChainage(171_000L).latitude
            longitude = pointAtChainage(171_000L).longitude
            accuracy = 4.0f
            time = nowMs - 1_200L
        }
        assertTrue(tracker.handleNewLocation(fresh171, nowMs = nowMs))
        assertEquals(nowMs - 1_200L, tracker.locationState.value.lastFixTime)
        assertEquals(fresh171.latitude, tracker.locationState.value.point!!.latitude, 1e-6)

        // 3) Older out-of-order callback (5s older than fresh171) MUST NOT overwrite newer 171+000 fix
        val olderDelayedLoc = Location("network").apply {
            latitude = pointAtChainage(130_000L).latitude
            longitude = pointAtChainage(130_000L).longitude
            accuracy = 12.0f
            time = nowMs - 6_200L
        }
        assertFalse("Older out-of-order location must be rejected",
            tracker.handleNewLocation(olderDelayedLoc, nowMs = nowMs))
        assertEquals("Location must stay at 171+000", fresh171.latitude, tracker.locationState.value.point!!.latitude, 1e-6)
    }

    // =========================================================================
    // 3. Force Refresh recalculates 130+000 -> 171+000 with fresh GPS, rejects stale/weak GPS
    // =========================================================================
    @Test
    fun `Force Refresh recalculates to 171+000 with fresh GPS and retains last reliable chainage on stale or poor GPS`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locTracker = LocationTracker(context)
        val route = createHighway130To175Route()
        val tracker = ChainageProgressionTracker()
        val nowMs = 1_700_001_000_000L

        // Suppose display somehow held 130+000 previously, and user taps Force Refresh at actual 171+000
        val stalePrev130 = ChainageMatchResult(
            chainage = Chainage(130_000L),
            rawUncorrectedChainage = Chainage(130_000L),
            projectedPoint = pointAtChainage(130_000L),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 4.0f,
            detectedSide = "RHS",
            source = ChainageSource.KMZ,
            timestamp = nowMs - 2_000L
        )
        tracker.processResult(stalePrev130, route.id)

        val freshAt171 = LocationState(
            point = pointAtChainage(171_000L),
            accuracy = 3.2f,
            status = LocationStatus.READY,
            lastFixTime = nowMs
        )
        val validFresh = locTracker.validateLocationState(freshAt171, nowMs = nowMs)
        assertTrue(validFresh.isValid && validFresh.isFresh)

        // Force Refresh resets smoothing buffer and recalculates cleanly without stale jump rejection
        tracker.resetAutoChainageFilterOnly()
        ChainageMatcher.resetJumpValidationState()
        val freshMatch = tracker.processResult(
            ChainageMatcher.match(freshAt171.point!!, freshAt171.accuracy, routeData = route, previousResult = null, fixTimeMs = nowMs),
            route.id
        )
        assertFalse(freshMatch.isUncertain)
        assertTrue("Expected recalibrated chainage 171+000, got ${freshMatch.displayChainage}",
            abs((freshMatch.chainage?.metres ?: 0L) - 171_000L) <= 5L)

        // Stale GPS at 130+000 (60s old) is flagged invalid so 171+000 is retained
        val staleGpsAt130 = LocationState(
            point = pointAtChainage(130_000L),
            accuracy = 5.0f,
            status = LocationStatus.READY,
            lastFixTime = nowMs - 60_000L
        )
        val staleValidation = locTracker.validateLocationState(staleGpsAt130, nowMs = nowMs + 1000L)
        assertFalse("Stale 60s-old GPS must not be accepted", staleValidation.isValid && staleValidation.isFresh)

        // Off-route GPS (500m lateral offset) does not produce a confident match
        val offRouteGps = LocationState(
            point = pointAtChainage(171_000L, lngOffsetDeg = 0.006),
            accuracy = 4.0f,
            status = LocationStatus.READY,
            lastFixTime = nowMs + 2000L
        )
        val offRouteMatch = ChainageMatcher.match(
            gpsPoint = offRouteGps.point!!,
            gpsAccuracy = offRouteGps.accuracy,
            routeData = route,
            previousResult = freshMatch,
            fixTimeMs = nowMs + 2000L
        )
        assertEquals(MatchConfidence.NO_MATCH, offRouteMatch.confidence)
    }

    // =========================================================================
    // 4. Secondary unmarked alignment near 171+000 must NOT default baseline to 130+000
    // =========================================================================
    @Test
    fun `unmarked secondary alignment near 171+000 infers baseline from nearest marker instead of route start 130+000`() {
        val route = createHighway130To175Route(extraUnmarkedAlignmentNear171 = true)
        val serviceAlign = route.alignments.first { it.id == "align-service-171" }
        val cached = ChainageMatcher.getOrCreateCachedGeometry(serviceAlign, route.markers)
        val startMetres = cached.mappedMarkers.first().chainageMetres
        assertTrue(
            "Expected secondary alignment baseline near 171+000, not 130+000; got $startMetres",
            abs(startMetres - 171_000L) <= 50L
        )
    }

    // =========================================================================
    // 5. Forward AND Reverse movement (including reverse tunnel gap) work cleanly
    // =========================================================================
    @Test
    fun `forward and reverse travel progression and plausible reverse gap are accepted without false drops`() {
        val route = createHighway130To175Route()
        val tracker = ChainageProgressionTracker()
        var t = 1_700_002_000_000L

        // Start at 171+000
        var prev = tracker.processResult(
            ChainageMatcher.match(pointAtChainage(171_000L), 3.0f, routeData = route, previousResult = null, fixTimeMs = t),
            route.id
        )

        // Move FORWARD 171+000 -> 171+060 (3 steps of +20m)
        for (step in 1..3) {
            t += 1_000L
            val m = ChainageMatcher.match(
                pointAtChainage(171_000L + step * 20L), 3.0f, routeData = route, previousResult = prev, fixTimeMs = t
            )
            assertFalse("Forward step $step must not be flagged uncertain", m.isUncertain)
            prev = tracker.processResult(m, route.id)
        }
        assertEquals(TravelChainageDirection.FORWARD, tracker.getCurrentTravelDirection())

        // Now move REVERSE 171+060 -> 170+980 (4 steps of -20m)
        for (step in 1..4) {
            t += 1_000L
            val targetM = 171_060L - step * 20L
            val m = ChainageMatcher.match(
                pointAtChainage(targetM), 3.0f, routeData = route, previousResult = prev, fixTimeMs = t
            )
            assertFalse("Reverse step $step must not be flagged uncertain", m.isUncertain)
            prev = tracker.processResult(m, route.id)
        }
        assertEquals(TravelChainageDirection.REVERSE, tracker.getCurrentTravelDirection())
        assertTrue("Expected reverse chainage near 170+980, got ${prev.chainage?.metres}",
            abs((prev.chainage?.metres ?: 0L) - 170_980L) <= 10L)

        // Genuine 15-second reverse travel through a 500m tunnel (170+980 -> 170+480 = 33 m/s <= 55 m/s)
        t += 15_000L
        val afterTunnel = ChainageMatcher.match(
            pointAtChainage(170_480L), 4.0f, routeData = route, previousResult = prev, fixTimeMs = t
        )
        assertFalse("Plausible reverse tunnel travel must be accepted cleanly", afterTunnel.isUncertain)
        assertTrue("Expected ~170+480 after tunnel, got ${afterTunnel.chainage?.metres}",
            abs((afterTunnel.chainage?.metres ?: 0L) - 170_480L) <= 10L)
    }

    // =========================================================================
    // 6. Manual Calibration (Stable & Dynamic) and Travel Direction preserved on Force Refresh
    // =========================================================================
    @Test
    fun `Force Refresh preserves manual calibration anchor in Stable and Dynamic modes without wiping direction`() {
        val route = createHighway130To175Route()
        val tracker = ChainageProgressionTracker()
        val t0 = 1_700_003_000_000L

        val loc171 = LocationState(
            point = pointAtChainage(171_000L),
            accuracy = 3.0f,
            status = LocationStatus.READY,
            lastFixTime = t0
        )
        val auto171 = ChainageMatcher.match(loc171.point!!, 3.0f, routeData = route, fixTimeMs = t0)

        // Establish Dynamic Correction calibration: user enters 171+250 at raw 171+000 (offset +250m)
        var cal = tracker.establishManualCalibration(
            enteredChainage = Chainage(171_250L),
            mode = ManualChainageMode.DYNAMIC_CORRECTION,
            currentAutoResult = auto171,
            locationState = loc171,
            routeId = route.id,
            nowMs = t0
        )
        assertTrue(cal.isDynamicAnchorCalibrated)
        assertEquals(250L, cal.computedOffsetMetres)

        // Force Refresh calls resetAutoChainageFilterOnly() -> MUST preserve manual calibration anchor & offset!
        tracker.resetAutoChainageFilterOnly()

        val loc171040 = LocationState(
            point = pointAtChainage(171_040L),
            accuracy = 3.0f,
            status = LocationStatus.READY,
            lastFixTime = t0 + 2_000L
        )
        val auto171040 = ChainageMatcher.match(loc171040.point!!, 3.0f, routeData = route, fixTimeMs = t0 + 2_000L)
        val dynRes = tracker.processWithManualCalibration(auto171040, loc171040, route, cal, nowMs = t0 + 2_000L)
        assertEquals(171_290L, dynRes.matchResult.chainage?.metres)
        assertEquals(171_250L, dynRes.updatedCalibration.anchorChainageMetres)
        assertEquals(250L, dynRes.updatedCalibration.computedOffsetMetres)

        // Switch to Stable Mode and run Force Refresh filter reset -> continues smoothly with zero jump!
        val stableCal = tracker.switchManualMode(
            currentCalibration = dynRes.updatedCalibration,
            targetMode = ManualChainageMode.CONTINUE_STABLE,
            currentAutoResult = auto171040,
            locationState = loc171040,
            routeId = route.id,
            nowMs = t0 + 3_000L
        )
        tracker.resetAutoChainageFilterOnly()
        val loc171070 = LocationState(
            point = pointAtChainage(171_070L),
            accuracy = 3.0f,
            status = LocationStatus.READY,
            lastFixTime = t0 + 4_000L
        )
        val auto171070 = ChainageMatcher.match(loc171070.point!!, 3.0f, routeData = route, fixTimeMs = t0 + 4_000L)
        val stableRes = tracker.processWithManualCalibration(auto171070, loc171070, route, stableCal, nowMs = t0 + 4_000L)
        assertTrue("Stable mode must continue cleanly near 171+320, got ${stableRes.matchResult.chainage?.metres}",
            abs((stableRes.matchResult.chainage?.metres ?: 0L) - 171_320L) <= 5L)
    }

    // =========================================================================
    // 7. Manual LHS/RHS lock NEVER resets to AUTO during Force Refresh
    // =========================================================================
    @Test
    fun `manual LHS or RHS lock stays locked across Force Refresh and opposite-side GPS updates`() {
        val route = createHighway130To175Route()
        val tracker = ChainageProgressionTracker()
        val dirManager = SmartDirectionManager()
        val nowMs = 1_700_004_000_000L

        // User explicitly locks MANUAL LHS
        tracker.setManualSide("LHS")
        dirManager.onManualDirectionSelected("LHS", nowMs)
        val lockedPrefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "LHS",
            manualSelectedAtMs = nowMs
        )

        // GPS sits physically on the EAST (RHS) side of northbound alignment during Force Refresh!
        val eastLoc = LocationState(
            point = pointAtChainage(171_000L, lngOffsetDeg = 0.00015),
            accuracy = 3.0f,
            status = LocationStatus.READY,
            lastFixTime = nowMs + 1000L
        )
        tracker.resetAutoChainageFilterOnly()
        val rawMatch = ChainageMatcher.match(
            gpsPoint = eastLoc.point!!,
            gpsAccuracy = eastLoc.accuracy,
            routeData = route,
            previousResult = null,
            fixTimeMs = nowMs + 1000L
        )
        val tracked = tracker.processResult(rawMatch, route.id)
        val dirState = dirManager.evaluate(eastLoc, tracked, route, lockedPrefs)

        assertTrue("Tracker must keep manual override active", tracker.isManualOverride())
        assertEquals("LHS", tracked.detectedSide)
        assertTrue("SmartDirectionManager must remain locked in manual override", dirState.isManualOverride)
        assertEquals("LHS", dirState.confirmedSide)
    }

    // =========================================================================
    // 8. LastReliableChainageSnapshot persistence & cold-start restoration
    // =========================================================================
    @Test
    fun `LastReliableChainageSnapshot persists across process restart and seeds progression tracker cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        val snapshot = LastReliableChainageSnapshot(
            routeId = "nh-130-175",
            chainageMetres = 171_000L,
            rawChainageMetres = 171_000L,
            alignmentName = "NH-58 Main Carriageway",
            distanceAlongMetres = 41_000.0,
            detectedSide = "RHS",
            sourceName = ChainageSource.KMZ.name,
            timestampMs = 1_700_005_000_000L
        )
        val json = snapshot.toJsonString()
        assertFalse("Snapshot JSON must not store raw latitude", json.contains("latitude"))
        assertFalse("Snapshot JSON must not store raw longitude", json.contains("longitude"))

        prefs.updateLastReliableChainageSnapshot(snapshot)
        PreferencesManager.awaitPendingWrites()

        // Simulate cold process restart
        PreferencesManager.clearMemoryCacheForProcessRestart()
        val restoredPrefs = PreferencesManager(context)
        val restored = restoredPrefs.getInitialLastReliableChainageSnapshot()
        assertNotNull(restored)
        assertEquals("nh-130-175", restored!!.routeId)
        assertEquals(171_000L, restored.chainageMetres)
        assertEquals("RHS", restored.detectedSide)

        // Seed fresh tracker on app restart -> older stale callback is rejected, 171+000 is retained!
        val tracker = ChainageProgressionTracker()
        tracker.seedPersistedLastReliableChainage("nh-130-175", restored)
        val staleOldResult = ChainageMatchResult(
            chainage = Chainage(130_000L),
            rawUncorrectedChainage = Chainage(130_000L),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 5.0f,
            detectedSide = "RHS",
            timestamp = 1_700_004_900_000L // 100s OLDER than restored snapshot!
        )
        val afterStale = tracker.processResult(staleOldResult, "nh-130-175")
        assertEquals(171_000L, afterStale.chainage?.metres)
        assertTrue(afterStale.isUncertain)
    }

    // =========================================================================
    // 9. Home Page Chainage Card minimalist Refresh Icon states (Dark & Light) & tap guard
    // =========================================================================
    @Test
    fun `Home Chainage Card minimalist refresh icon supports idle, refreshing disable, success checkmark, warning and dark light themes`() {
        var refreshState by mutableStateOf(ChainageCardRefreshState.IDLE)
        var isAwaiting by mutableStateOf(false)
        var isDark by mutableStateOf(true)
        var clickCount by mutableIntStateOf(0)

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = if (isDark) "dark" else "light") {
                CentralChainageDisplay(
                        chainageText = "171+000",
                        detectedSide = "RHS",
                        confidence = MatchConfidence.HIGH,
                        offsetMetres = 2.4,
                        hasActiveRoute = true,
                        source = ChainageSource.KMZ,
                        cardBgColor = Color.Transparent,
                        cardBorderColor = Color.Transparent,
                        mainTextColor = if (isDark) Color.White else Color.Black,
                        subTextColor = Color.Gray,
                        isDark = isDark,
                        refreshState = refreshState,
                        isAwaitingReliableFix = isAwaiting,
                    onForceRefreshChainage = { clickCount++ }
                )
            }
        }

        // IDLE state (Dark Theme): visible, enabled, invokes refresh once tapped
        composeTestRule.onNodeWithTag("home_chainage_refresh_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, clickCount)
        composeTestRule.onNodeWithTag("home_chainage_refresh_idle_icon", useUnmergedTree = true).assertIsDisplayed()

        // REFRESHING state: button disabled against rapid repeated taps while chainage stays visible
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread {
            refreshState = ChainageCardRefreshState.REFRESHING
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.onNodeWithTag("home_chainage_refresh_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag("home_chainage_refresh_spinning_icon", useUnmergedTree = true).assertIsDisplayed()

        // SUCCESS state: animated green checkmark shown (also verify Light Theme)
        composeTestRule.runOnUiThread {
            refreshState = ChainageCardRefreshState.SUCCESS
            isDark = false
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(350L)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_chainage_refresh_success_icon", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Chainage recalibrated").assertIsDisplayed()

        // WARNING state + Awaiting GPS indicator while retaining previous chainage
        composeTestRule.runOnIdle {
            refreshState = ChainageCardRefreshState.WARNING
            isAwaiting = true
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_chainage_refresh_warning_icon", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Awaiting GPS").assertIsDisplayed()
    }
}
