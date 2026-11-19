package com.example

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.AlignmentDirectionConvention
import com.example.model.CameraMode
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.CoordinateFormat
import com.example.model.DirectionControlMode
import com.example.model.DirectionDetectionState
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.KmStoneAlertSettings
import com.example.model.MatchConfidence
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.SmartDirectionPreferences
import com.example.model.SmartDirectionState
import com.example.navigation.ChainageNavigationResolver
import com.example.navigation.KmStoneArrivalDetector
import com.example.navigation.KmStoneMonitoringNotificationFormatter
import com.example.navigation.KmStoneVoiceAnnouncementManager
import com.example.navigation.KmStoneVoiceScriptFormatter
import com.example.navigation.LockScreenNavStateResolver
import com.example.navigation.SmartDirectionManager
import com.example.overlay.OverlayRenderer
import com.example.overlay.OverlayTextGenerator
import com.example.ui.components.SmartDirectionIndicatorBadge
import com.example.ui.home.AmoledAlwaysOnDisplay
import com.example.ui.home.NextKmStoneTracker
import com.example.ui.home.ScreensaverChainageStateHolder
import com.example.model.CtecsWidgetInstanceConfig
import com.example.navigation.LockScreenNavUiState
import com.example.widget.CtecsWidgetRenderer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import org.robolectric.annotation.Config
import java.io.File

/**
 * Comprehensive 20-scenario verification suite for CTECS Application-Wide Smart LHS/RHS
 * Auto Direction Enhancement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmartDirectionEnhancementTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        KmStoneVoiceAnnouncementManager.resetTestState()
    }

    /**
     * Builds a straight north-south highway corridor (lat 19.000 -> 19.040, ~4.45 km) with
     * configurable KM stone markers and alignment metadata.
     */
    private fun buildHighwayRoute(
        routeId: String = "route_nh48",
        alignmentId: String = "align_main",
        alignmentName: String = "NH-48 Main",
        carriagewayType: String = "",
        directionMeta: String = "",
        irregularMarkers: Boolean = false
    ): RouteData {
        // 0.009 deg latitude ~ 1000 m northward
        val p0 = GeoPoint(19.000, 72.870)
        val p1 = GeoPoint(19.018, 72.870)
        val p2 = GeoPoint(19.036, 72.870)
        val markers = if (irregularMarkers) {
            listOf(
                RouteMarker("m1", "KM 197+000", Chainage.fromMetres(197_000L), GeoPoint(19.000, 72.870)),
                RouteMarker("m2", "KM 197+850", Chainage.fromMetres(197_850L), GeoPoint(19.00765, 72.870)),
                RouteMarker("m3", "KM 199+200", Chainage.fromMetres(199_200L), GeoPoint(19.0198, 72.870)),
                RouteMarker("m4", "KM 200+500", Chainage.fromMetres(200_500L), GeoPoint(19.0315, 72.870))
            )
        } else {
            listOf(
                RouteMarker("m197", "KM 197+000", Chainage.fromMetres(197_000L), GeoPoint(19.000, 72.870)),
                RouteMarker("m198", "KM 198+000", Chainage.fromMetres(198_000L), GeoPoint(19.009, 72.870)),
                RouteMarker("m199", "KM 199+000", Chainage.fromMetres(199_000L), GeoPoint(19.018, 72.870)),
                RouteMarker("m200", "KM 200+000", Chainage.fromMetres(200_000L), GeoPoint(19.027, 72.870))
            )
        }
        return RouteData(
            id = routeId,
            name = alignmentName,
            fileName = "$routeId.kmz",
            markers = markers,
            alignments = listOf(
                RouteAlignment(
                    id = alignmentId,
                    name = alignmentName,
                    points = listOf(p0, p1, p2),
                    carriagewayType = carriagewayType,
                    direction = directionMeta
                )
            ),
            bounds = GeoBounds(19.000, 19.036, 72.870, 72.870),
            startChainage = Chainage.fromMetres(197_000L),
            endChainage = Chainage.fromMetres(201_000L)
        )
    }

    private fun pointForChainage(chainageMetres: Long): GeoPoint {
        val distFromStart = (chainageMetres - 197_000L).toDouble()
        val lat = 19.000 + (distFromStart / 111_111.0)
        return GeoPoint(lat, 72.870)
    }

    private fun fixFor(
        chainageMetres: Long,
        nowMs: Long,
        speedMps: Float = 15f,
        bearingDeg: Float? = 0f,
        accuracyM: Float = 5f,
        status: LocationStatus = LocationStatus.READY
    ): Pair<LocationState, ChainageMatchResult> {
        val pt = pointForChainage(chainageMetres)
        val distAlong = (chainageMetres - 197_000L).toDouble().coerceAtLeast(0.0)
        val loc = LocationState(
            status = status,
            point = pt,
            accuracy = accuracyM,
            speed = speedMps,
            bearing = bearingDeg,
            lastFixTime = nowMs
        )
        val match = ChainageMatchResult(
            chainage = Chainage.fromMetres(chainageMetres),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = accuracyM,
            statusMessage = "Matched",
            source = ChainageSource.KMZ,
            detectedSide = "---",
            offsetMetres = 2.0,
            projectedPoint = pt,
            distanceAlongAlignmentMetres = distAlong,
            matchedSegmentIndex = 0,
            matchedAlignmentName = "NH-48 Main",
            isManual = false,
            isUncertain = false
        )
        return loc to match
    }

    private fun feedSequence(
        manager: SmartDirectionManager,
        route: RouteData,
        chainages: List<Long>,
        startTimeMs: Long = 100_000L,
        stepTimeMs: Long = 1_500L,
        speedMps: Float = 15f,
        bearingDeg: Float? = 0f,
        accuracyM: Float = 5f,
        prefs: SmartDirectionPreferences = SmartDirectionPreferences()
    ): SmartDirectionState {
        var t = startTimeMs
        var last = manager.currentState()
        for (ch in chainages) {
            val (loc, match) = fixFor(ch, t, speedMps = speedMps, bearingDeg = bearingDeg, accuracyM = accuracyM)
            last = manager.evaluate(loc, match, route, prefs, nowMs = t)
            t += stepTimeMs
        }
        return last
    }

    // ------------------------------------------------------------------
    // Scenarios 1, 2, 3, 4: Normal LHS/RHS & Increasing/Decreasing Chainage
    // (including reversed alignment convention Inc = RHS!)
    // ------------------------------------------------------------------
    @Test
    fun scenarios1To4_normalLhsRhsAndIncreasingDecreasingConventions() {
        val standardRoute = buildHighwayRoute()
        val manager = SmartDirectionManager()

        // 1 & 3. Normal increasing travel on standard alignment -> LHS_CONFIRMED (↑)
        val lhsState = feedSequence(
            manager = manager,
            route = standardRoute,
            chainages = listOf(197_100L, 197_115L, 197_130L),
            bearingDeg = 0f
        )
        assertEquals(DirectionDetectionState.LHS_CONFIRMED, lhsState.detectionState)
        assertEquals("LHS", lhsState.confirmedSide)
        assertEquals(true, lhsState.isIncreasingChainage)
        assertEquals("↑", lhsState.directionArrow)
        assertFalse(lhsState.isManualOverride)

        // 2 & 4. Normal decreasing travel on fresh manager -> RHS_CONFIRMED (↓)
        val rhsManager = SmartDirectionManager()
        val rhsState = feedSequence(
            manager = rhsManager,
            route = standardRoute,
            chainages = listOf(198_500L, 198_485L, 198_470L),
            bearingDeg = 180f
        )
        assertEquals(DirectionDetectionState.RHS_CONFIRMED, rhsState.detectionState)
        assertEquals("RHS", rhsState.confirmedSide)
        assertEquals(false, rhsState.isIncreasingChainage)
        assertEquals("↓", rhsState.directionArrow)

        // Never assume increasing == LHS: alignment with RHS = INCREASING metadata must resolve increasing -> RHS!
        val reversedRoute = buildHighwayRoute(
            routeId = "route_reversed",
            alignmentId = "align_rhs_inc",
            alignmentName = "Expressway RHS_INC",
            carriagewayType = "RHS",
            directionMeta = "INCREASING"
        )
        val revManager = SmartDirectionManager()
        val revState = feedSequence(
            manager = revManager,
            route = reversedRoute,
            chainages = listOf(197_200L, 197_215L, 197_230L),
            bearingDeg = 0f
        )
        assertEquals(AlignmentDirectionConvention.INCREASING_IS_RHS, revState.effectiveConvention)
        assertEquals(DirectionDetectionState.RHS_CONFIRMED, revState.detectionState)
        assertEquals("RHS", revState.confirmedSide)
        assertEquals(true, revState.isIncreasingChainage)
        assertEquals("↑", revState.directionArrow)
    }

    // ------------------------------------------------------------------
    // Scenarios 5 & 6: Stationary Vehicle & Slow-Moving Vehicle
    // ------------------------------------------------------------------
    @Test
    fun scenarios5And6_stationaryNeverGuessesAndSlowSpeedRequiresStricterConfirmation() {
        val route = buildHighwayRoute()
        val manager = SmartDirectionManager()

        // 5a. Stationary from cold start -> STATIONARY with null confirmedSide (never guesses!)
        val stoppedInitial = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_500L, 197_501L, 197_500L, 197_502L),
            speedMps = 0.1f,
            bearingDeg = 270f
        )
        assertEquals(DirectionDetectionState.STATIONARY, stoppedInitial.detectionState)
        assertNull("Stationary cold start must never guess LHS/RHS", stoppedInitial.confirmedSide)

        // 6. Slow-moving vehicle (1.4 m/s ~ 5 km/h): 2 short steps should NOT confirm yet;
        //    sustained slow movement (>= 3 steps & >= 16m) confirms cleanly without flicker.
        val slowEarly = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_506L, 197_511L),
            startTimeMs = 120_000L,
            speedMps = 1.4f,
            bearingDeg = 0f
        )
        assertFalse(
            "Slow-moving vehicle must wait for sufficient distance/samples before confirming",
            slowEarly.hasConfirmedDirection
        )

        val slowConfirmed = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_516L, 197_522L, 197_528L),
            startTimeMs = 126_000L,
            speedMps = 1.4f,
            bearingDeg = 0f
        )
        assertEquals(DirectionDetectionState.LHS_CONFIRMED, slowConfirmed.detectionState)
        assertEquals("LHS", slowConfirmed.confirmedSide)

        // 5b. Stopping after confirmed LHS holds steady without flipping to RHS
        val stoppedAfterConfirm = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_528L, 197_527L, 197_528L),
            startTimeMs = 135_000L,
            speedMps = 0.0f,
            bearingDeg = 180f // erratic heading while stopped must be ignored
        )
        assertEquals(DirectionDetectionState.STATIONARY, stoppedAfterConfirm.detectionState)
        assertEquals("LHS", stoppedAfterConfirm.confirmedSide)
    }

    // ------------------------------------------------------------------
    // Scenarios 7, 8, 9, 10: Temporary Reverse, Genuine U-Turn, LHS->RHS, RHS->LHS
    // ------------------------------------------------------------------
    @Test
    fun scenarios7To10_temporaryReverseRejectedAndGenuineUTurnLhsToRhsAndRhsToLhsConfirmed() {
        val route = buildHighwayRoute()
        val manager = SmartDirectionManager()

        // Confirm initial LHS travel
        val lhs = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_300L, 197_318L, 197_336L),
            startTimeMs = 200_000L,
            bearingDeg = 0f
        )
        assertEquals("LHS", lhs.confirmedSide)

        // 7. Temporary short reverse movement (~8m back) -> enters DIRECTION_CHANGING, does NOT flip!
        val tempRev = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_328L),
            startTimeMs = 206_000L,
            speedMps = 3.5f,
            bearingDeg = 180f
        )
        assertEquals(DirectionDetectionState.DIRECTION_CHANGING, tempRev.detectionState)
        assertEquals("LHS", tempRev.confirmedSide)
        assertNull(tempRev.directionChangedFrom)

        // Resume forward LHS -> recovers cleanly back to LHS_CONFIRMED
        val recovered = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_342L, 197_358L),
            startTimeMs = 208_000L,
            speedMps = 12f,
            bearingDeg = 0f
        )
        assertEquals(DirectionDetectionState.LHS_CONFIRMED, recovered.detectionState)
        assertEquals("LHS", recovered.confirmedSide)
        assertNull(recovered.directionChangedFrom)

        // 8 & 9. Genuine U-Turn (LHS -> RHS): sustained decreasing movement > 30m over 3 observations
        val rhsFlipped = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_344L, 197_328L, 197_310L),
            startTimeMs = 214_000L,
            speedMps = 12f,
            bearingDeg = 180f
        )
        assertEquals(DirectionDetectionState.RHS_CONFIRMED, rhsFlipped.detectionState)
        assertEquals("RHS", rhsFlipped.confirmedSide)
        assertEquals("LHS", rhsFlipped.directionChangedFrom)
        assertEquals(false, rhsFlipped.isIncreasingChainage)

        // 10. Genuine reverse back (RHS -> LHS): sustained increasing movement > 30m over 3 observations
        val lhsFlippedBack = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_325L, 197_342L, 197_360L),
            startTimeMs = 222_000L,
            speedMps = 12f,
            bearingDeg = 0f
        )
        assertEquals(DirectionDetectionState.LHS_CONFIRMED, lhsFlippedBack.detectionState)
        assertEquals("LHS", lhsFlippedBack.confirmedSide)
        assertEquals("RHS", lhsFlippedBack.directionChangedFrom)
        assertEquals(true, lhsFlippedBack.isIncreasingChainage)
    }

    // ------------------------------------------------------------------
    // Scenarios 11 & 12: Weak GPS + Recovery & GPS Drift / Inaccurate Heading
    // ------------------------------------------------------------------
    @Test
    fun scenarios11And12_weakGpsNeverGuessesAndInaccurateCompassDoesNotCorruptTrajectory() {
        val route = buildHighwayRoute()
        val manager = SmartDirectionManager()

        // 11a. Weak GPS (accuracy 65m) -> GPS_SIGNAL_WEAK with null confirmedSide
        val (weakLoc, weakMatch) = fixFor(197_400L, 300_000L, accuracyM = 65f)
        val weakState = manager.evaluate(weakLoc, weakMatch, route, nowMs = 300_000L)
        assertEquals(DirectionDetectionState.GPS_SIGNAL_WEAK, weakState.detectionState)
        assertNull("Weak GPS must never publish guessed direction", weakState.confirmedSide)

        // 11b. GPS recovers cleanly and confirms LHS
        val recovered = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_410L, 197_426L, 197_442L),
            startTimeMs = 304_000L,
            accuracyM = 5f,
            bearingDeg = 0f
        )
        assertEquals(DirectionDetectionState.LHS_CONFIRMED, recovered.detectionState)
        assertEquals("LHS", recovered.confirmedSide)

        // 12. Inaccurate phone compass / 180-deg wrong GPS bearing while actually progressing forward on LHS
        //     must NOT override alignment geometry + consecutive GPS trajectory!
        val badHeadingState = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(197_458L, 197_474L, 197_490L),
            startTimeMs = 310_000L,
            accuracyM = 6f,
            bearingDeg = 180f // opposite inaccurate heading
        )
        assertEquals(DirectionDetectionState.LHS_CONFIRMED, badHeadingState.detectionState)
        assertEquals("LHS", badHeadingState.confirmedSide)
    }

    // ------------------------------------------------------------------
    // Scenario 13: Irregular KM Stone Intervals (Standard & Reversed Convention)
    // ------------------------------------------------------------------
    @Test
    fun scenario13_irregularKmStoneIntervalsFollowConfirmedDirectionAndConvention() {
        val irregularRoute = buildHighwayRoute(irregularMarkers = true) // 197+000, 197+850, 199+200, 200+500
        val tracker = NextKmStoneTracker()

        // Increasing LHS at 197+500 -> next irregular stone is 197+850 (350m away)
        val smartLhsInc = SmartDirectionState(
            detectionState = DirectionDetectionState.LHS_CONFIRMED,
            confirmedSide = "LHS",
            isIncreasingChainage = true,
            directionArrow = "↑"
        )
        val (_, m1) = fixFor(197_500L, 400_000L)
        val s1 = tracker.evaluate(m1.copy(smartDirectionState = smartLhsInc), irregularRoute, "LHS", true, smartLhsInc)
        assertTrue(s1.isReliable)
        assertEquals("197+850", s1.nextChainage)
        assertEquals(350L, s1.distanceMetres)
        assertEquals("LHS", s1.directionSide)

        // Reversed convention: RHS = INCREASING at 198+000 -> next irregular stone MUST be 199+200 (1200m away), NOT 197+850!
        val smartRhsInc = SmartDirectionState(
            detectionState = DirectionDetectionState.RHS_CONFIRMED,
            confirmedSide = "RHS",
            isIncreasingChainage = true,
            directionArrow = "↑",
            effectiveConvention = AlignmentDirectionConvention.INCREASING_IS_RHS
        )
        val (_, m2) = fixFor(198_000L, 405_000L)
        val s2 = tracker.evaluate(m2.copy(smartDirectionState = smartRhsInc), irregularRoute, "RHS", true, smartRhsInc)
        assertTrue(s2.isReliable)
        assertEquals("199+200", s2.nextChainage)
        assertEquals(1200L, s2.distanceMetres)
        assertEquals("RHS", s2.directionSide)
        assertEquals("↑", s2.directionArrow)

        // Uncertain auto direction -> NextKmStoneTracker must NOT display a guessed stone
        val smartUncertain = SmartDirectionState(
            detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
            confirmedSide = null,
            isIncreasingChainage = null
        )
        val s3 = tracker.evaluate(m2.copy(smartDirectionState = smartUncertain), irregularRoute, "Uncertain", true, smartUncertain)
        assertFalse(s3.isReliable)
        assertEquals("--+---", s3.nextChainage)
        assertEquals("Direction Uncertain", s3.distanceText)
    }

    // ------------------------------------------------------------------
    // Scenario 14: Alignment Switching Clears Stale Direction Immediately
    // ------------------------------------------------------------------
    @Test
    fun scenario14_alignmentSwitchingResetsStaleRouteDirectionImmediately() {
        val routeA = buildHighwayRoute(routeId = "route_A", alignmentId = "align_A", alignmentName = "Highway A")
        val routeB = buildHighwayRoute(routeId = "route_B", alignmentId = "align_B", alignmentName = "Highway B")
        val manager = SmartDirectionManager()

        val confirmedA = feedSequence(
            manager = manager,
            route = routeA,
            chainages = listOf(197_100L, 197_115L, 197_130L),
            startTimeMs = 500_000L
        )
        assertEquals("LHS", confirmedA.confirmedSide)

        // Switch to Route B with single initial sample -> stale LHS from Route A must be cleared!
        val (locB, matchB) = fixFor(198_000L, 510_000L)
        val firstOnB = manager.evaluate(
            locB,
            matchB.copy(matchedAlignmentName = "Highway B"),
            routeB,
            nowMs = 510_000L
        )
        assertNull("Switching alignments must clear stale direction from previous route", firstOnB.confirmedSide)
        assertEquals(DirectionDetectionState.DIRECTION_UNCERTAIN, firstOnB.detectionState)
        assertEquals("route_B", firstOnB.routeId)
    }

    // ------------------------------------------------------------------
    // Scenario 15: Screensaver (Portrait/Landscape/Mirror) & AMOLED AOD
    // ------------------------------------------------------------------
    @Test
    fun scenario15_screensaverMirrorAndAmoledAodReflectConfirmedAndUncertainStates() {
        val route = buildHighwayRoute()
        val holder = ScreensaverChainageStateHolder()
        val (loc, baseMatch) = fixFor(198_240L, 600_000L, speedMps = 16.5f)

        val smartLhs = SmartDirectionState(
            detectionState = DirectionDetectionState.LHS_CONFIRMED,
            confirmedSide = "LHS",
            isIncreasingChainage = true,
            directionArrow = "↑"
        )
        val confirmedDisplay = holder.resolve(
            matchResult = baseMatch.copy(smartDirectionState = smartLhs),
            route = route,
            hasGpsFix = true,
            projectRoadSide = RoadSide.RHS, // intentionally opposite to prove SmartDirectionState wins!
            locationState = loc,
            nowMs = 600_000L
        )
        assertEquals("LHS", confirmedDisplay.reliableSide)
        assertEquals("↑", confirmedDisplay.directionArrow)
        assertEquals(DirectionDetectionState.LHS_CONFIRMED, confirmedDisplay.directionState)

        // Uncertain state suppresses side badge instead of falling back to projectRoadSide RHS
        val smartUncertain = SmartDirectionState(
            detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
            confirmedSide = null,
            isIncreasingChainage = null
        )
        val uncertainDisplay = holder.resolve(
            matchResult = baseMatch.copy(smartDirectionState = smartUncertain),
            route = route,
            hasGpsFix = true,
            projectRoadSide = RoadSide.RHS,
            locationState = loc,
            nowMs = 602_000L
        )
        assertNull(uncertainDisplay.reliableSide)
        assertEquals(DirectionDetectionState.DIRECTION_UNCERTAIN, uncertainDisplay.directionState)

        // Render AMOLED AOD in Mirror mode -> underlying direction text remains "LHS ↑" (never reversed to RHS!)
        composeRule.setContent {
            MaterialTheme {
                AmoledAlwaysOnDisplay(
                    displayState = confirmedDisplay,
                    settings = HomeScreensaverSettings(aodShowDirectionSide = true, aodShowChainage = true),
                    isMirrorMode = true,
                    onExitAod = {}
                )
            }
        }
        composeRule.onNodeWithTag("aod_direction_row").assertIsDisplayed()
        composeRule.onNodeWithText("LHS ↑").assertIsDisplayed()
    }

    // ------------------------------------------------------------------
    // Scenario 16: Camera Overlays, EXIF & Photo Metadata
    // ------------------------------------------------------------------
    @Test
    fun scenario16_cameraOverlayAndExifUseConfirmedDirectionAndOmitUncertainGuesses() {
        val project = ProjectProfile(
            highwayId = "NH-48",
            packageSection = "Pkg-01",
            roadSide = RoadSide.RHS, // opposite of confirmed LHS
            lane = "I/L",
            activity = "DBM"
        )
        val (loc, baseMatch) = fixFor(198_500L, 700_000L)
        val confirmedLhs = SmartDirectionState(
            detectionState = DirectionDetectionState.LHS_CONFIRMED,
            confirmedSide = "LHS",
            isIncreasingChainage = true,
            directionArrow = "↑"
        )
        val confirmedSnap = CaptureSnapshot(
            timestamp = 700_000L,
            location = loc.point,
            gpsAccuracy = 5f,
            matchResult = baseMatch.copy(smartDirectionState = confirmedLhs, detectedSide = "LHS"),
            projectProfile = project,
            overlayConfig = OverlayConfig(showChainage = true, showProjectInfo = true),
            locationName = "Surat Bypass"
        )

        val (side1, useOverride1) = OverlayTextGenerator.resolveAuthoritativeCaptureSide(confirmedSnap)
        assertTrue(useOverride1)
        assertEquals("LHS", side1)

        val linesConfirmed = OverlayTextGenerator.generateLines(
            config = confirmedSnap.overlayConfig,
            project = project,
            location = loc.point,
            accuracy = 5f,
            compassHeading = "N",
            speedMps = 15f,
            bearing = 0f,
            chainageStr = "198+500",
            cameraMode = CameraMode.HIGHWAY,
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = true,
            timestamp = 700_000L,
            isForCapture = true,
            authoritativeSideOverride = side1,
            useAuthoritativeSide = useOverride1
        )
        assertTrue(linesConfirmed.any { it.contains("198+500 LHS") })
        assertFalse(linesConfirmed.any { it.contains("198+500 RHS") })

        // Verify EXIF user comment writes confirmed LHS
        val tmpJpg = File(context.cacheDir, "smart_dir_exif_test.jpg")
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        assertTrue(OverlayRenderer.saveBitmapWithExif(bmp, tmpJpg, confirmedSnap, context = context))
        val exif = ExifInterface(tmpJpg.absolutePath)
        val comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT).orEmpty()
        assertTrue("EXIF UserComment must contain confirmed LHS: $comment", comment.contains("LHS"))
        assertFalse("EXIF UserComment must not stamp stale RHS: $comment", comment.contains("RHS"))
        bmp.recycle()
        tmpJpg.delete()

        // Uncertain Auto direction must omit side rather than stamping guessed RHS
        val uncertainSnap = confirmedSnap.copy(
            matchResult = baseMatch.copy(
                smartDirectionState = SmartDirectionState(
                    detectionState = DirectionDetectionState.GPS_SIGNAL_WEAK,
                    confirmedSide = null
                )
            )
        )
        val (side2, useOverride2) = OverlayTextGenerator.resolveAuthoritativeCaptureSide(uncertainSnap)
        assertTrue(useOverride2)
        assertNull(side2)
        val highwayTextUncertain = project.buildHighwayText("198+500", sideOverride = side2, useSideOverride = useOverride2)
        assertFalse("Uncertain capture must not stamp guessed RHS", highwayTextUncertain.contains("RHS"))
        assertFalse("Uncertain capture must not stamp guessed LHS", highwayTextUncertain.contains("LHS"))
    }

    // ------------------------------------------------------------------
    // Scenario 17: Background Foreground Service Notifications
    // ------------------------------------------------------------------
    @Test
    fun scenario17_foregroundServiceNotificationFormattingShowsConfirmedOrUncertainTruthfully() {
        val route = buildHighwayRoute()
        val (loc, baseMatch) = fixFor(198_420L, 800_000L)
        val smartRhs = SmartDirectionState(
            detectionState = DirectionDetectionState.RHS_CONFIRMED,
            confirmedSide = "RHS",
            isIncreasingChainage = false,
            directionArrow = "↓"
        )
        val navResolver = LockScreenNavStateResolver()
        val navState = navResolver.resolve(
            locationState = loc,
            matchResult = baseMatch.copy(smartDirectionState = smartRhs, detectedSide = "RHS"),
            routeData = route,
            projectProfile = ProjectProfile(highwayId = "NH-48", roadSide = RoadSide.LHS),
            locationName = "NH-48",
            nowMs = 800_000L
        )
        assertEquals("RHS", navState.reliableSide)
        assertTrue(navState.nextMarkerLabel.orEmpty().contains("198+000"))
        val notif = KmStoneMonitoringNotificationFormatter.format(
            navState = navState,
            locationState = loc,
            lastEvent = null
        )
        assertTrue(notif.title.contains("RHS"))
        assertTrue(notif.contentText.contains("198+000"))

        // Weak GPS -> notification never displays guessed side
        val weakLoc = loc.copy(accuracy = 70f, status = LocationStatus.POOR_ACCURACY)
        val weakNavState = LockScreenNavStateResolver().resolve(
            locationState = weakLoc,
            matchResult = baseMatch.copy(
                smartDirectionState = SmartDirectionState(
                    detectionState = DirectionDetectionState.GPS_SIGNAL_WEAK,
                    confirmedSide = null
                )
            ),
            routeData = route,
            projectProfile = ProjectProfile(highwayId = "NH-48", roadSide = RoadSide.LHS),
            locationName = "NH-48",
            nowMs = 802_000L
        )
        assertNull(weakNavState.reliableSide)
        val weakNotif = KmStoneMonitoringNotificationFormatter.format(
            navState = weakNavState,
            locationState = weakLoc,
            lastEvent = null
        )
        assertFalse(weakNotif.title.contains("LHS"))
        assertFalse(weakNotif.title.contains("RHS"))
    }

    // ------------------------------------------------------------------
    // Scenario 18: Voice Announcements for Direction Changes, Cooldown & Gating
    // ------------------------------------------------------------------
    @Test
    fun scenario18_voiceDirectionChangeAnnouncementsRespectSettingsCooldownAndStatusText() {
        assertEquals("Direction changed to RHS.", KmStoneVoiceScriptFormatter.buildDirectionChangeText("RHS"))
        assertEquals("Direction changed to LHS.", KmStoneVoiceScriptFormatter.buildDirectionChangeText("LHS"))
        assertEquals(
            "You are travelling on LHS.",
            KmStoneVoiceScriptFormatter.buildDirectionStatusText(
                SmartDirectionState(
                    detectionState = DirectionDetectionState.LHS_CONFIRMED,
                    confirmedSide = "LHS"
                )
            )
        )
        assertEquals(
            "Travel direction uncertain. Checking road alignment.",
            KmStoneVoiceScriptFormatter.buildDirectionStatusText(
                SmartDirectionState(
                    detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
                    confirmedSide = null
                )
            )
        )

        val enabledSettings = KmStoneAlertSettings(enabled = true, voiceAnnouncementEnabled = true)
        val changedToRhsState = SmartDirectionState(
            detectionState = DirectionDetectionState.RHS_CONFIRMED,
            confirmedSide = "RHS",
            directionChangedFrom = "LHS",
            isManualOverride = false
        )

        // First confirmed change announces cleanly
        val r1 = KmStoneVoiceAnnouncementManager.announceDirectionChange(
            context = context,
            directionState = changedToRhsState,
            settings = enabledSettings,
            voiceDirectionEnabled = true,
            nowMs = 900_000L
        )
        assertNotNull(r1)
        assertEquals("Direction changed to RHS.", r1!!.spokenText)

        // Rapid duplicate within 10s cooldown is suppressed
        val rDup = KmStoneVoiceAnnouncementManager.announceDirectionChange(
            context = context,
            directionState = changedToRhsState,
            settings = enabledSettings,
            voiceDirectionEnabled = true,
            nowMs = 903_000L
        )
        assertNull("Cooldown must prevent repetitive voice direction spam", rDup)

        // Disabled voice setting suppresses announcement
        val changedToLhsState = SmartDirectionState(
            detectionState = DirectionDetectionState.LHS_CONFIRMED,
            confirmedSide = "LHS",
            directionChangedFrom = "RHS",
            isManualOverride = false
        )
        val rDisabled = KmStoneVoiceAnnouncementManager.announceDirectionChange(
            context = context,
            directionState = changedToLhsState,
            settings = KmStoneAlertSettings(enabled = true, voiceAnnouncementEnabled = false),
            voiceDirectionEnabled = true,
            nowMs = 920_000L
        )
        assertNull("Voice OFF must suppress direction change speech", rDisabled)
    }

    // ------------------------------------------------------------------
    // Scenario 19: Android Widgets & Manual Override Preferences Persistence
    // ------------------------------------------------------------------
    @Test
    fun scenario19_widgetsRenderAuthoritativeDirectionAndPreferencesPersistWithoutStaleGps() = runBlocking {
        val widgetCfg = CtecsWidgetInstanceConfig(appWidgetId = 1, showRoadSide = true, showChainage = true)
        val confirmedNav = LockScreenNavUiState(
            chainageText = "198+420",
            reliableSide = "LHS",
            directionArrow = "↑",
            directionState = DirectionDetectionState.LHS_CONFIRMED,
            isDirectionManual = false,
            isLiveGpsValid = true,
            hasRouteData = true
        )
        val confirmedRender = CtecsWidgetRenderer.renderWidgetBitmap(
            context = context,
            navState = confirmedNav,
            instanceConfig = widgetCfg,
            widthDp = 280,
            heightDp = 120
        )
        assertTrue(confirmedRender.displayedDirection.contains("LHS"))

        val uncertainNav = LockScreenNavUiState(
            chainageText = "198+420",
            reliableSide = null,
            directionArrow = "",
            directionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
            isDirectionManual = false,
            isLiveGpsValid = true,
            hasRouteData = true
        )
        val uncertainRender = CtecsWidgetRenderer.renderWidgetBitmap(
            context = context,
            navState = uncertainNav,
            instanceConfig = widgetCfg,
            widthDp = 280,
            heightDp = 120
        )
        assertFalse(
            "Widget must not guess LHS/RHS when uncertain",
            uncertainRender.displayedDirection.contains("LHS") || uncertainRender.displayedDirection.contains("RHS")
        )

        // Preferences persistence round-trip (only user preferences, never stale GPS direction)
        val customPrefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "RHS",
            alignmentConvention = AlignmentDirectionConvention.INCREASING_IS_RHS,
            voiceDirectionAnnouncementsEnabled = false
        )
        val prefsMgr = PreferencesManager(context)
        prefsMgr.updateSmartDirectionPreferences(customPrefs)
        val restored = prefsMgr.smartDirectionPreferencesFlow.first()
        assertEquals(customPrefs, restored)

        // Reset back to AUTO clears manualOverrideSide so stale direction is never persisted
        val autoPrefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.AUTO,
            manualOverrideSide = null,
            alignmentConvention = AlignmentDirectionConvention.AUTO_FROM_ALIGNMENT,
            voiceDirectionAnnouncementsEnabled = true
        )
        prefsMgr.updateSmartDirectionPreferences(autoPrefs)
        val restoredAuto = prefsMgr.smartDirectionPreferencesFlow.first()
        assertEquals(DirectionControlMode.AUTO, restoredAuto.controlMode)
        assertNull(restoredAuto.manualOverrideSide)
    }

    // ------------------------------------------------------------------
    // Scenario 20: Global Application-Wide Synchronization Across All Modules
    // ------------------------------------------------------------------
    @Test
    fun scenario20_globalSynchronizationMatchesAcrossAllModulesAndUiBadge() {
        val route = buildHighwayRoute()
        val manager = SmartDirectionManager()

        // Drive 3 consecutive RHS (decreasing) fixes to confirm RHS authoritatively
        val authState = feedSequence(
            manager = manager,
            route = route,
            chainages = listOf(198_600L, 198_582L, 198_564L),
            startTimeMs = 1_000_000L,
            speedMps = 15f,
            bearingDeg = 180f
        )
        assertEquals(DirectionDetectionState.RHS_CONFIRMED, authState.detectionState)
        assertEquals("RHS", authState.confirmedSide)

        val (loc, baseMatch) = fixFor(198_564L, 1_003_000L, speedMps = 15f, bearingDeg = 180f)
        val syncedMatch = baseMatch.copy(
            detectedSide = authState.confirmedSide ?: "---",
            smartDirectionState = authState
        )
        val oppositeProfile = ProjectProfile(highwayId = "NH-48", roadSide = RoadSide.LHS)

        // 1. Next KM Stone Card tracker
        val nextKm = NextKmStoneTracker().evaluate(syncedMatch, route, "RHS", true, authState)
        assertEquals("RHS", nextKm.directionSide)
        assertEquals("198+000", nextKm.nextChainage)

        // 2. KM Stone Arrival Detector (move to 198+495 to trigger 500m RHS approach)
        val detector = KmStoneArrivalDetector()
        val alertSettings = KmStoneAlertSettings(enabled = true, voiceAnnouncementEnabled = true, voiceInterval500m = true)
        detector.evaluate(loc, syncedMatch, route, RoadSide.LHS, alertSettings, 1_003_000L, authState)
        val (loc2, m2) = fixFor(198_495L, 1_006_000L, speedMps = 15f, bearingDeg = 180f)
        val arrivalEvents = detector.evaluate(loc2, m2.copy(smartDirectionState = authState), route, RoadSide.LHS, alertSettings, 1_006_000L, authState)
        assertEquals(1, arrivalEvents.size)
        assertEquals("RHS", arrivalEvents.first().directionSide)
        assertEquals("198+000", arrivalEvents.first().stoneFormattedChainage)

        // 3. Screensaver / AMOLED state holder
        val screensaverState = ScreensaverChainageStateHolder().resolve(
            matchResult = syncedMatch,
            route = route,
            hasGpsFix = true,
            projectRoadSide = oppositeProfile.roadSide,
            locationState = loc,
            nowMs = 1_003_000L
        )
        assertEquals("RHS", screensaverState.reliableSide)

        // 4. Lock-Screen / Foreground Service state resolver
        val serviceState = LockScreenNavStateResolver().resolve(
            locationState = loc,
            matchResult = syncedMatch,
            routeData = route,
            projectProfile = oppositeProfile,
            locationName = "NH-48",
            nowMs = 1_003_000L
        )
        assertEquals("RHS", serviceState.reliableSide)

        // 5. Navigate to Chainage resolver
        val navCalc = ChainageNavigationResolver.calculateAndResolveDestination(
            rawDestinationInput = "198+000",
            matchResult = syncedMatch,
            activeRoute = route,
            projectRoadSide = oppositeProfile.roadSide
        )
        assertEquals("RHS", navCalc.currentSideLabel)
        assertTrue(navCalc.carriagewayCompatibilityNote.orEmpty().contains("Forward along RHS"))

        // 6. Camera / Photo / Gallery snapshot side
        val snap = CaptureSnapshot(
            timestamp = 1_003_000L,
            location = loc.point,
            matchResult = syncedMatch,
            projectProfile = oppositeProfile
        )
        assertEquals("RHS", OverlayTextGenerator.resolveAuthoritativeCaptureSide(snap).first)

        // 7. Shared SmartDirectionIndicatorBadge UI component
        composeRule.setContent {
            MaterialTheme {
                SmartDirectionIndicatorBadge(
                    directionState = authState,
                    isDark = true
                )
            }
        }
        composeRule.onNodeWithTag("smart_direction_badge").assertIsDisplayed()
        composeRule.onNodeWithText("RHS ↓").assertIsDisplayed()
    }
}
