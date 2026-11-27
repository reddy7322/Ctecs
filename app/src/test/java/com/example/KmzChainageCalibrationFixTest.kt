package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.geo.ChainageAccuracyRefreshCoordinator
import com.example.geo.ChainageMatcher
import com.example.geo.ChainageProgressionTracker
import com.example.geo.SmartChainageCorrectionEngine
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.location.LocationTracker
import com.example.model.CalibrationValidationStatus
import com.example.model.Chainage
import com.example.model.ChainageAccuracyReport
import com.example.model.ChainageAccuracyStatus
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.DirectionControlMode
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.PermanentCalibrationProfile
import com.example.model.ReferencePointSource
import com.example.model.RefreshMode
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.SmartDirectionPreferences
import com.example.navigation.SmartDirectionManager
import com.example.ui.camera.ChainageBubbleStatus
import com.example.ui.camera.resolveChainageIndicatorsState
import com.example.ui.dialogs.ChainageSourceBadge
import com.example.ui.dialogs.LiveCalibrationStatusSection
import com.example.ui.home.CentralChainageDisplay
import com.example.ui.theme.DialogThemeTokens
import com.example.ui.theme.MyApplicationTheme
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
class KmzChainageCalibrationFixTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val degPerMetre = 1.0 / 111_132.0
    private val baseLat = 29.470000
    private val baseLng = 77.700000

    @After
    fun tearDown() {
        ChainageMatcher.clearCache()
    }

    private fun createCorridor130To180(
        id: String = "nh58-long",
        direction: String = "INCREASING"
    ): RouteData {
        val startKm = 130_000L
        val endKm = 180_000L
        val pts = (0..50).map { km ->
            GeoPoint(baseLat + (km * 1000.0) * degPerMetre, baseLng, 240.0)
        }
        val markers = (0..50).map { km ->
            val ch = if (direction == "DECREASING") endKm - km * 1000L else startKm + km * 1000L
            RouteMarker("m-$km", Chainage(ch).format(), Chainage(ch), pts[km])
        }
        val align = RouteAlignment(
            id = "align-main",
            name = "NH-58 Main",
            points = pts,
            direction = direction,
            carriagewayType = "MAIN"
        )
        return RouteData(
            id = id,
            name = "NH-58 Long Corridor",
            fileName = "NH58.kmz",
            markers = markers,
            alignments = listOf(align),
            bounds = GeoBounds.fromPoints(pts),
            startChainage = Chainage(if (direction == "DECREASING") endKm else startKm),
            endChainage = Chainage(if (direction == "DECREASING") startKm else endKm),
            selectedAlignmentId = align.id
        )
    }

    private fun lightTokens() = DialogThemeTokens(
        containerColor = Color.White,
        surfaceColor = Color(0xFFF8FAFC),
        surfaceVariantColor = Color(0xFFF1F5F9),
        titleColor = Color(0xFF0F172A),
        textColor = Color(0xFF1E293B),
        textSecondaryColor = Color(0xFF64748B),
        borderColor = Color(0xFFCBD5E1),
        inputBackground = Color.White,
        inputBorder = Color(0xFF94A3B8),
        isDark = false
    )

    private fun darkTokens() = DialogThemeTokens(
        containerColor = Color(0xFF0F172A),
        surfaceColor = Color(0xFF1E293B),
        surfaceVariantColor = Color(0xFF334155),
        titleColor = Color.White,
        textColor = Color(0xFFE2E8F0),
        textSecondaryColor = Color(0xFF94A3B8),
        borderColor = Color(0xFF334155),
        inputBackground = Color(0xFF1E293B),
        inputBorder = Color(0xFF475569),
        isDark = true
    )

    // -------------------------------------------------------------------------
    // CASE 1: Bug reproduction — GPS at 170+696 vs anchor 135+529 (-35167m)
    // -------------------------------------------------------------------------
    @Test
    fun `gps at 170+696 does not corrupt anchor 135+529 position and -35167m offset is rejected as INVALID`() {
        val route = createCorridor130To180()
        val gpsAt170696 = GeoPoint(baseLat + 40_696.0 * degPerMetre, baseLng, 240.0)

        // Anchor position resolution for target 135+529 MUST resolve near 5529m along the corridor,
        // NOT overwrite with user's current GPS at 40696m!
        val resolvedAnchor = SmartChainageCorrectionEngine.resolveRoutePositionForChainage(
            routeData = route,
            targetKmzMetres = 135_529L,
            fallbackGps = gpsAt170696
        )
        assertTrue(
            "Anchor 135+529 must resolve near 5529m along route, got ${resolvedAnchor.routeDistanceMetres}",
            abs(resolvedAnchor.routeDistanceMetres - 5529.0) <= 25.0
        )

        // Single extreme offset point reproducing the bug: 170+696 -> 135+529 (-35,167m)
        val bogusPoint = ChainageCorrectionPoint(
            id = "bogus-35167",
            routeId = route.id,
            latitude = gpsAt170696.latitude,
            longitude = gpsAt170696.longitude,
            originalKmzMetres = 170_696L,
            correctedMetres = 135_529L,
            routeDistanceMetres = 40_696.0,
            referenceSource = ReferencePointSource.MANUAL,
            isEnabled = true
        )

        val validation = SmartChainageCorrectionEngine.validatePoint(bogusPoint, emptyList(), route)
        assertFalse("Extreme -35167m calibration must fail validatePoint", validation.isValid)

        val conflicts = SmartChainageCorrectionEngine.detectReferenceConflicts(listOf(bogusPoint), route)
        assertTrue("Single-point extreme offset must trigger conflict", conflicts.hasConflict)
        assertEquals(CalibrationValidationStatus.INVALID, conflicts.validationStatus)

        val correctionResult = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(170_696L),
            routeData = route,
            correctionPoints = listOf(bogusPoint),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 40_696.0
        )
        assertNotNull(correctionResult)
        assertEquals(CalibrationValidationStatus.INVALID, correctionResult!!.validationStatus)
        assertTrue(correctionResult.hasConflict)
        assertEquals(SmartChainageCorrectionEngine.CorrectionMode.CONFLICT_DETECTED, correctionResult.mode)
        assertEquals(170_696L, correctionResult.correctedChainage.metres) // Original KMZ preserved, never corrupted
        assertEquals(0L, correctionResult.appliedOffsetMetres)
        assertEquals(-35_167L, correctionResult.attemptedOffsetMetres)

        // Verify coordinator also surfaces CALIBRATION_NEEDS_REVIEW & LOW confidence
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locTracker = LocationTracker(context)
        val coordinator = ChainageAccuracyRefreshCoordinator()
        val nowMs = System.currentTimeMillis()
        val locState = LocationState(
            point = gpsAt170696,
            accuracy = 4.0f,
            status = LocationStatus.READY,
            lastFixTime = nowMs
        )
        val sync = coordinator.synchronizeCalibrationData(route, listOf(bogusPoint), emptyList(), forceRevalidate = true)
        val output = coordinator.recalculateAndValidate(
            generation = coordinator.bumpGeneration(),
            mode = RefreshMode.FORCE_ALL,
            locationState = locState,
            gpsValidation = locTracker.validateLocationState(locState, nowMs = nowMs),
            route = route,
            routeValidGeometry = true,
            calibrationSync = sync,
            isCorrectionsEnabled = true,
            previousResult = null,
            progressionTracker = ChainageProgressionTracker(),
            manualChainage = null,
            manualChainageText = null,
            gpsRefreshSucceeded = true,
            nowMs = nowMs
        )
        assertEquals(ChainageAccuracyStatus.CALIBRATION_NEEDS_REVIEW, output.report.status)
        assertFalse(output.report.isControlPointConsistent)
        assertFalse(output.report.isCustomCorrectionValid)
        assertEquals(CalibrationValidationStatus.INVALID, output.matchResult.calibrationValidationStatus)
        assertEquals(170_696L, output.matchResult.chainage?.metres)
        assertTrue(output.matchResult.isUncertain)

        // Compose UI check: never show "High Confidence", "Verified", or green "Calibration ON"
        composeTestRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LiveCalibrationStatusSection(
                    activeRoute = route,
                    matchResult = output.matchResult,
                    correctionPoints = listOf(bogusPoint),
                    isCorrectionsEnabled = true,
                    isRefreshingAccuracy = false,
                    accuracyReport = output.report,
                    primaryAccent = Color(0xFF0284C7),
                    greenAccent = Color(0xFF10B981),
                    amberAccent = Color(0xFFF59E0B),
                    redAccent = Color(0xFFEF4444),
                    dialogColors = darkTokens(),
                    onToggleEnabled = {},
                    onForceRefreshAll = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calibration_validation_status_pill").assertIsDisplayed()
        composeTestRule.onNodeWithText("Invalid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Calibration Invalid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Output (Rejected)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Blocked (-35167m)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calibration_validation_reason_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("High Confidence").assertDoesNotExist()
        composeTestRule.onNodeWithText("Verified").assertDoesNotExist()
        composeTestRule.onNodeWithText("Calibration ON").assertDoesNotExist()
    }

    // -------------------------------------------------------------------------
    // CASE 2: Valid +/- offsets, forward/reverse travel, increasing/decreasing
    // -------------------------------------------------------------------------
    @Test
    fun `valid positive and negative offsets apply once across increasing and decreasing routes without negative clamping`() {
        val incRoute = createCorridor130To180(id = "r-inc", direction = "INCREASING")
        val pPlus = ChainageCorrectionPoint(
            id = "p-plus",
            routeId = incRoute.id,
            latitude = baseLat + 10_000.0 * degPerMetre,
            longitude = baseLng,
            originalKmzMetres = 140_000L,
            correctedMetres = 140_120L,
            routeDistanceMetres = 10_000.0,
            referenceSource = ReferencePointSource.GPS_VERIFIED
        )

        val fwd = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(141_500L),
            routeData = incRoute,
            correctionPoints = listOf(pPlus),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 11_500.0
        )!!
        assertEquals(141_620L, fwd.correctedChainage.metres)
        assertEquals(120L, fwd.appliedOffsetMetres)
        assertEquals(CalibrationValidationStatus.VERIFIED, fwd.validationStatus)

        val rev = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(138_200L),
            routeData = incRoute,
            correctionPoints = listOf(pPlus),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 8_200.0
        )!!
        assertEquals(138_320L, rev.correctedChainage.metres)
        assertEquals(120L, rev.appliedOffsetMetres)

        // Decreasing corridor with negative offset (-85m)
        val decRoute = createCorridor130To180(id = "r-dec", direction = "DECREASING")
        val pMinus = ChainageCorrectionPoint(
            id = "p-minus",
            routeId = decRoute.id,
            latitude = baseLat + 10_000.0 * degPerMetre,
            longitude = baseLng,
            originalKmzMetres = 170_000L,
            correctedMetres = 169_915L,
            routeDistanceMetres = 10_000.0,
            referenceSource = ReferencePointSource.MANUAL
        )
        val decRes = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(168_500L),
            routeData = decRoute,
            correctionPoints = listOf(pMinus),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 11_500.0
        )!!
        assertEquals(168_415L, decRes.correctedChainage.metres)
        assertEquals(-85L, decRes.appliedOffsetMetres)

        // Negative result must fail validation instead of silently clamping to 0+000
        val nearZeroRoute = createCorridor130To180(id = "r-zero", direction = "INCREASING")
        val pNeg = ChainageCorrectionPoint(
            id = "p-neg-clamp",
            routeId = nearZeroRoute.id,
            latitude = baseLat,
            longitude = baseLng,
            originalKmzMetres = 130_000L,
            correctedMetres = 129_800L,
            routeDistanceMetres = 0.0
        )
        val negOutputRes = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(100L),
            routeData = nearZeroRoute,
            correctionPoints = listOf(pNeg),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 0.0
        )!!
        assertEquals(CalibrationValidationStatus.INVALID, negOutputRes.validationStatus)
        assertTrue(negOutputRes.hasConflict)
        assertEquals(100L, negOutputRes.correctedChainage.metres)
    }

    // -------------------------------------------------------------------------
    // CASE 3: Multi-marker piecewise interpolation + 0.0m anchor + 2km markers
    // -------------------------------------------------------------------------
    @Test
    fun `zero-distance start anchor and multi-point interpolation with 2km markers work accurately`() {
        val route = createCorridor130To180(id = "r-multi")
        val p0 = ChainageCorrectionPoint(
            id = "p0",
            routeId = route.id,
            latitude = baseLat,
            longitude = baseLng,
            originalKmzMetres = 130_000L,
            correctedMetres = 130_100L,
            routeDistanceMetres = 0.0,
            referenceSource = ReferencePointSource.GPS_VERIFIED
        )
        val p1 = ChainageCorrectionPoint(
            id = "p1",
            routeId = route.id,
            latitude = baseLat + 4_000.0 * degPerMetre,
            longitude = baseLng,
            originalKmzMetres = 134_000L,
            correctedMetres = 134_300L,
            routeDistanceMetres = 4_000.0,
            referenceSource = ReferencePointSource.GPS_VERIFIED
        )
        val p2 = ChainageCorrectionPoint(
            id = "p2",
            routeId = route.id,
            latitude = baseLat + 8_000.0 * degPerMetre,
            longitude = baseLng,
            originalKmzMetres = 138_000L,
            correctedMetres = 138_300L,
            routeDistanceMetres = 8_000.0,
            referenceSource = ReferencePointSource.GPS_VERIFIED
        )

        // Exact 0.0m start anchor still calibrates properly
        val singleAtZero = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(130_500L),
            routeData = route,
            correctionPoints = listOf(p0),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 500.0
        )!!
        assertEquals(130_600L, singleAtZero.correctedChainage.metres)

        // Midpoint between p0 (0m -> 130+100) and p1 (4000m -> 134+300) at 2000m => 132+200
        val midRes = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(132_000L),
            routeData = route,
            correctionPoints = listOf(p0, p1, p2),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 2_000.0
        )!!
        assertEquals(132_200L, midRes.correctedChainage.metres)
        assertEquals(200L, midRes.appliedOffsetMetres)

        val generated = SmartChainageCorrectionEngine.generate2KmCalibrationMarkers(
            routeData = route,
            correctionPoints = listOf(p0, p1, p2),
            rangeStartMetres = 132_000L,
            rangeEndMetres = 136_000L,
            intervalMetres = 2_000L
        )
        assertEquals(listOf("132+000", "134+000", "136+000"), generated.map { it.label })
    }

    // -------------------------------------------------------------------------
    // CASE 4: Multi-polyline / parallel carriageway selection propagation
    // -------------------------------------------------------------------------
    @Test
    fun `selectedAlignmentId steers position resolution and correction to selected carriageway`() {
        val ptsLhs = (0..10).map { km -> GeoPoint(baseLat + (km * 1000.0) * degPerMetre, baseLng, 240.0) }
        val ptsRhs = (0..10).map { km -> GeoPoint(baseLat + (km * 1000.0) * degPerMetre, baseLng + 0.001, 240.0) }
        val markers = (0..10).map { km ->
            RouteMarker("m-$km", Chainage(130_000L + km * 1000L).format(), Chainage(130_000L + km * 1000L), ptsLhs[km])
        }
        val alignLhs = RouteAlignment("align-lhs", "NH-58 LHS", ptsLhs, "INCREASING", "LHS")
        val alignRhs = RouteAlignment("align-rhs", "NH-58 RHS", ptsRhs, "INCREASING", "RHS")

        val routeRhsSelected = RouteData(
            id = "dual-cw",
            name = "Dual Carriageway",
            fileName = "dual.kmz",
            markers = markers,
            alignments = listOf(alignLhs, alignRhs),
            bounds = GeoBounds.fromPoints(ptsLhs + ptsRhs),
            startChainage = Chainage(130_000L),
            endChainage = Chainage(140_000L),
            selectedAlignmentId = "align-rhs"
        )

        val pos = SmartChainageCorrectionEngine.resolveRoutePositionForChainage(
            routeData = routeRhsSelected,
            targetKmzMetres = 135_000L
        )
        assertTrue("Selected RHS longitude should be ~77.701, got ${pos.point.longitude}", pos.point.longitude > 77.7005)
    }

    // -------------------------------------------------------------------------
    // CASE 5 & CASE 7 & CASE 8: 6 validation states + reset + persistence
    // -------------------------------------------------------------------------
    @Test
    fun `all six validation states reset restoration and json persistence behave accurately`() {
        val route = createCorridor130To180(id = "r-states")

        val manualPt = ChainageCorrectionPoint(
            id = "pt-man",
            routeId = route.id,
            latitude = baseLat + 5_000.0 * degPerMetre,
            longitude = baseLng,
            originalKmzMetres = 135_000L,
            correctedMetres = 135_080L,
            routeDistanceMetres = 5_000.0,
            referenceSource = ReferencePointSource.MANUAL
        )
        val gpsVerifiedPt = manualPt.copy(
            id = "pt-gps",
            referenceSource = ReferencePointSource.GPS_VERIFIED
        )

        // 1. GPS_UNAVAILABLE
        val gpsUnavail = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(135_500L),
            routeData = route,
            correctionPoints = listOf(manualPt),
            confidence = MatchConfidence.MEDIUM,
            distanceAlongAlignmentMetres = 5_500.0,
            isGpsAvailable = false
        )!!
        assertEquals(CalibrationValidationStatus.GPS_UNAVAILABLE, gpsUnavail.validationStatus)

        // 2. LOW_CONFIDENCE
        val lowConf = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(135_500L),
            routeData = route,
            correctionPoints = listOf(manualPt),
            confidence = MatchConfidence.LOW,
            distanceAlongAlignmentMetres = 5_500.0,
            isGpsAvailable = true
        )!!
        assertEquals(CalibrationValidationStatus.LOW_CONFIDENCE, lowConf.validationStatus)

        // 3. CALIBRATED (single manual point, medium GPS)
        val calibrated = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(135_500L),
            routeData = route,
            correctionPoints = listOf(manualPt),
            confidence = MatchConfidence.MEDIUM,
            distanceAlongAlignmentMetres = 5_500.0,
            isGpsAvailable = true
        )!!
        assertEquals(CalibrationValidationStatus.CALIBRATED, calibrated.validationStatus)

        // 4. VERIFIED (GPS verified anchor + HIGH confidence)
        val verified = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(135_500L),
            routeData = route,
            correctionPoints = listOf(gpsVerifiedPt),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 5_500.0,
            isGpsAvailable = true
        )!!
        assertEquals(CalibrationValidationStatus.VERIFIED, verified.validationStatus)

        // 5. PENDING (large 3.5 km shift needing review)
        val pendingPt = manualPt.copy(correctedMetres = 138_500L)
        val pending = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(135_500L),
            routeData = route,
            correctionPoints = listOf(pendingPt),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 5_500.0,
            isGpsAvailable = true
        )!!
        assertEquals(CalibrationValidationStatus.PENDING, pending.validationStatus)

        // 6. INVALID (anchor mismatch > 500m from KMZ marker)
        val mismatchPt = manualPt.copy(routeDistanceMetres = 20_000.0)
        val invalidMismatch = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(135_500L),
            routeData = route,
            correctionPoints = listOf(mismatchPt),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 5_500.0,
            isGpsAvailable = true
        )!!
        assertEquals(CalibrationValidationStatus.INVALID, invalidMismatch.validationStatus)

        // Reset/disable restores raw KMZ chainage cleanly without altering route
        val disabled = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(135_500L),
            routeData = route,
            correctionPoints = listOf(gpsVerifiedPt.copy(isEnabled = false)),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 5_500.0
        )
        assertEquals(null, disabled)
        assertEquals(51, route.markers.size)

        // JSON persistence roundtrip & profile compatibility
        val profile = PermanentCalibrationProfile(
            profileId = "prof-save",
            profileName = "NH-58 Profile",
            projectId = "proj-1",
            routeId = route.id,
            originalFileName = route.fileName,
            alignmentFingerprint = SmartChainageCorrectionEngine.computeAlignmentFingerprint(route),
            controlPoints = listOf(gpsVerifiedPt),
            validationStatus = CalibrationValidationStatus.VERIFIED
        )
        val restored = PermanentCalibrationProfile.fromJson(profile.toJson())
        assertEquals(CalibrationValidationStatus.VERIFIED, restored.validationStatus)
        assertEquals(gpsVerifiedPt.routeDistanceMetres, restored.controlPoints.first().routeDistanceMetres, 0.01)

        val compatSame = SmartChainageCorrectionEngine.evaluateProfileCompatibility(restored, route)
        assertTrue(compatSame.isCompatible)
        assertTrue(compatSame.isExactGeometryMatch)
        assertEquals(CalibrationValidationStatus.VERIFIED, compatSame.validationStatus)
    }

    // -------------------------------------------------------------------------
    // CASE 6 & CASE 9: Force Refresh preserves manual LHS/RHS & Home/Camera sync
    // -------------------------------------------------------------------------
    @Test
    fun `force refresh preserves manual LHS lock and Home plus Camera sync invalid calibration warnings`() {
        val route = createCorridor130To180(id = "r-sync")
        val nowMs = System.currentTimeMillis()
        val dirManager = SmartDirectionManager()
        dirManager.onManualDirectionSelected("LHS", nowMs)
        val lhsPrefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.MANUAL_OVERRIDE,
            manualOverrideSide = "LHS",
            manualSelectedAtMs = nowMs
        )
        val gpsEastPt = GeoPoint(baseLat + 5_100.0 * degPerMetre, baseLng + 0.0002)
        val rawEastMatch = ChainageMatcher.match(gpsEastPt, 3.0f, 0f, route, fixTimeMs = nowMs)
        val lockedState = dirManager.evaluate(
            LocationState(
                point = gpsEastPt,
                accuracy = 3.0f,
                speed = 11.0f,
                bearing = 0f,
                status = LocationStatus.READY,
                lastFixTime = nowMs
            ),
            rawEastMatch,
            route,
            lhsPrefs,
            nowMs = nowMs
        )
        assertEquals("LHS", lockedState.confirmedSide)
        assertTrue(lockedState.isManualOverride)
        assertEquals(SmartDirectionPreferences.MODE_MANUAL_LHS, lockedState.internalDirectionMode)

        // Camera indicators never show green ACTIVE on INVALID or PENDING calibrations
        val invalidMatch = ChainageMatchResult(
            chainage = Chainage(170_696L),
            rawUncorrectedChainage = Chainage(170_696L),
            confidence = MatchConfidence.LOW,
            gpsAccuracy = 4.0f,
            detectedSide = "RHS",
            source = ChainageSource.CUSTOM_CORRECTION,
            isUncertain = true,
            calibrationValidationStatus = CalibrationValidationStatus.INVALID,
            calibrationValidationReason = "Extreme -35167m offset rejected"
        )
        val camInvalid = resolveChainageIndicatorsState(
            matchResult = invalidMatch,
            manualChainageText = null,
            hasActiveRoute = true,
            isCorrectionsEnabled = true
        )
        assertEquals(ChainageBubbleStatus.WARNING, camInvalid.kmzStatus)
        assertEquals(ChainageBubbleStatus.WARNING, camInvalid.customStatus)

        val validMatch = ChainageMatchResult(
            chainage = Chainage(170_746L),
            rawUncorrectedChainage = Chainage(170_696L),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 3.2f,
            detectedSide = "RHS",
            source = ChainageSource.CUSTOM_CORRECTION,
            calibrationValidationStatus = CalibrationValidationStatus.VERIFIED
        )
        val camValid = resolveChainageIndicatorsState(
            matchResult = validMatch,
            manualChainageText = null,
            hasActiveRoute = true,
            isCorrectionsEnabled = true
        )
        assertEquals(ChainageBubbleStatus.ACTIVE, camValid.kmzStatus)
        assertEquals(ChainageBubbleStatus.ACTIVE, camValid.customStatus)

        // Home CentralChainageDisplay shows CALIBRATION INVALID + Invalid Cal instead of Good
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                CentralChainageDisplay(
                    chainageText = "170+696",
                    detectedSide = "RHS",
                    confidence = MatchConfidence.HIGH,
                    offsetMetres = 2.0,
                    hasActiveRoute = true,
                    source = ChainageSource.CUSTOM_CORRECTION,
                    rawChainage = "170+696",
                    calibrationValidationStatus = CalibrationValidationStatus.INVALID,
                    cardBgColor = Color(0xFF1E293B),
                    cardBorderColor = Color(0xFF334155),
                    mainTextColor = Color.White,
                    subTextColor = Color(0xFF94A3B8),
                    isDark = true
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Invalid Cal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Good").assertDoesNotExist()
    }

    // -------------------------------------------------------------------------
    // CASE 10: Compact 360dp Light/Dark UI tests + all Force Refresh tags
    // -------------------------------------------------------------------------
    @Test
    @Config(qualifiers = "w360dp-h640dp-port")
    fun `compact 360dp dialog status section renders 6 states in light and dark themes with working refresh controls`() {
        val route = createCorridor130To180(id = "r-ui")
        var clickCount by mutableIntStateOf(0)
        var isRefreshing by mutableStateOf(false)
        var useDark by mutableStateOf(false)
        var validationStatus by mutableStateOf<String?>(CalibrationValidationStatus.CALIBRATED)
        var report by mutableStateOf<ChainageAccuracyReport?>(null)

        composeTestRule.setContent {
            MaterialTheme(colorScheme = if (useDark) darkColorScheme() else lightColorScheme()) {
                Column {
                    ChainageSourceBadge(
                        source = ChainageSource.CUSTOM_CORRECTION,
                        isDark = useDark,
                        validationStatus = validationStatus
                    )
                    LiveCalibrationStatusSection(
                        activeRoute = route,
                        matchResult = ChainageMatchResult(
                            chainage = Chainage(135_600L),
                            rawUncorrectedChainage = Chainage(135_500L),
                            confidence = MatchConfidence.HIGH,
                            gpsAccuracy = 3.5f,
                            detectedSide = "RHS",
                            source = ChainageSource.CUSTOM_CORRECTION,
                            calibrationValidationStatus = validationStatus
                        ),
                        correctionPoints = listOf(
                            ChainageCorrectionPoint(
                                id = "cp-ui",
                                routeId = route.id,
                                latitude = baseLat + 5_500.0 * degPerMetre,
                                longitude = baseLng,
                                originalKmzMetres = 135_500L,
                                correctedMetres = 135_600L,
                                routeDistanceMetres = 5_500.0
                            )
                        ),
                        isCorrectionsEnabled = true,
                        isRefreshingAccuracy = isRefreshing,
                        accuracyReport = report,
                        primaryAccent = Color(0xFF0284C7),
                        greenAccent = Color(0xFF10B981),
                        amberAccent = Color(0xFFF59E0B),
                        redAccent = Color(0xFFEF4444),
                        dialogColors = if (useDark) darkTokens() else lightTokens(),
                        onToggleEnabled = {},
                        onForceRefreshAll = {
                            clickCount++
                            isRefreshing = true
                        }
                    )
                }
            }
        }

        // Light theme CALIBRATED state + refresh click
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Calibrated").assertIsDisplayed()
        composeTestRule.onNodeWithText("+100m").assertIsDisplayed()
        composeTestRule.onNodeWithTag("force_refresh_all_chainage_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, clickCount)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("force_refresh_all_chainage_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("force_refresh_all_progress").assertIsDisplayed()

        // Dark theme PENDING state + accuracy badge
        composeTestRule.runOnIdle {
            useDark = true
            isRefreshing = false
            validationStatus = CalibrationValidationStatus.PENDING
            report = ChainageAccuracyReport(
                status = ChainageAccuracyStatus.CALIBRATION_NEEDS_REVIEW,
                mode = RefreshMode.FORCE_ALL,
                isRefreshing = false,
                isStaleFallback = false,
                summaryMessage = "Needs Review • Offset >2 km"
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Pending Review").assertIsDisplayed()
        composeTestRule.onNodeWithText("Calibration Pending").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chainage_accuracy_status_badge").assertIsDisplayed()
    }
}
