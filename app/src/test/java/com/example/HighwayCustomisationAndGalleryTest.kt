package com.example

import com.example.data.PhotoRecordEntity
import com.example.data.PhotoRepository
import com.example.geo.SmartChainageCorrectionEngine
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.ui.camera.ChainageBubbleStatus
import com.example.ui.camera.resolveChainageIndicatorsState
import com.example.ui.home.NextKmStoneTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HighwayCustomisationAndGalleryTest {

    @Before
    fun setUp() {
        SmartChainageCorrectionEngine.resetDiagnostics(nowMs = 1_000_000L)
    }

    @Test
    fun `heartbeat monitor detects stalled chainage calculation and records sub-task restart`() {
        SmartChainageCorrectionEngine.notifyTaskStarted(nowMs = 1_000_000L)
        assertTrue(SmartChainageCorrectionEngine.diagnosticsFlow.value.isCalculationInProgress)

        // Before stall timeout (1.5s elapsed < 2.5s threshold)
        val stalledEarly = SmartChainageCorrectionEngine.checkHeartbeatAndDetectStall(
            nowMs = 1_001_500L,
            stallThresholdMs = 2_500L
        )
        assertFalse(stalledEarly)
        assertEquals(0L, SmartChainageCorrectionEngine.diagnosticsFlow.value.stallDetectedCount)

        // Exceeds stall timeout (3.0s elapsed >= 2.5s threshold)
        val stalled = SmartChainageCorrectionEngine.checkHeartbeatAndDetectStall(
            nowMs = 1_003_000L,
            stallThresholdMs = 2_500L
        )
        assertTrue(stalled)
        assertEquals(1L, SmartChainageCorrectionEngine.diagnosticsFlow.value.stallDetectedCount)
        assertEquals(
            SmartChainageCorrectionEngine.EngineHealthStatus.STALL_DETECTED,
            SmartChainageCorrectionEngine.diagnosticsFlow.value.engineStatus
        )

        // Auto-restart affected sub-task
        SmartChainageCorrectionEngine.recordSubTaskRestarted(
            reason = "Heartbeat watchdog auto-restarted stalled calculation task",
            nowMs = 1_003_050L
        )
        val diagAfterRestart = SmartChainageCorrectionEngine.diagnosticsFlow.value
        assertEquals(1L, diagAfterRestart.autoRestartCount)
        assertEquals(SmartChainageCorrectionEngine.EngineHealthStatus.RECOVERED, diagAfterRestart.engineStatus)
        assertTrue(diagAfterRestart.lastRecoveryReason.contains("Heartbeat watchdog"))
    }

    @Test
    fun `conflated GPS updates track coalesced updates and matching throughput`() {
        // First GPS fix starts processing
        SmartChainageCorrectionEngine.recordGpsUpdateReceived(nowMs = 2_000_000L)
        SmartChainageCorrectionEngine.notifyTaskStarted(nowMs = 2_000_010L)

        // Two rapid GPS fixes arrive while matching task is running -> conflated
        SmartChainageCorrectionEngine.recordGpsUpdateReceived(nowMs = 2_000_050L)
        SmartChainageCorrectionEngine.recordGpsUpdateReceived(nowMs = 2_000_090L)

        SmartChainageCorrectionEngine.notifyTaskCompleted(
            latencyMicros = 420L,
            alignmentName = "NH-58 Mainline",
            segmentsCount = 1280,
            gridCellsCount = 340,
            matchedChainage = "200+050",
            offsetMetres = 2.4,
            confidence = "HIGH",
            hierarchyTier = SmartChainageCorrectionEngine.CalibrationHierarchyTier.TIER_1_VERIFIED_CONTROL_POINT.label,
            nowMs = 2_000_500L
        )

        val diag = SmartChainageCorrectionEngine.diagnosticsFlow.value
        assertEquals(3L, diag.totalGpsUpdatesReceived)
        assertEquals(2L, diag.conflatedSkippedUpdates)
        assertEquals(1L, diag.totalMatchesCompleted)
        assertEquals(420L, diag.lastMatchLatencyMicros)
        assertEquals("NH-58 Mainline", diag.activeAlignmentName)
        assertEquals(1280, diag.activeAlignmentSegmentsCount)
        assertEquals("200+050", diag.activeMatchedChainageText)
    }

    @Test
    fun `ProjectProfile buildHighwayText handles None, custom activity, subActivity and BHS`() {
        val noneProfile = ProjectProfile(
            highwayId = "NH-58",
            packageSection = "None",
            workType = "None",
            roadSide = RoadSide.NONE,
            lane = "None",
            activity = "None",
            subActivity = "None",
            customDescription = ""
        )
        val noneText = noneProfile.buildHighwayText("198+880")
        assertEquals("NH-58 | 198+880", noneText)
        assertFalse(noneText.contains("DBM"))
        assertFalse(noneText.contains("None"))

        val customProfile = ProjectProfile(
            highwayId = "NH-58",
            packageSection = "Muzzafarnagar-Haridwar",
            workType = "Six Laning",
            roadSide = RoadSide.fromString("BHS"),
            lane = "M/L",
            activity = "BC",
            subActivity = "Layer 2",
            customDescription = "Trial Stretch"
        )
        assertEquals(RoadSide.BOTH, customProfile.roadSide)
        assertEquals("BOTH", customProfile.roadSide.label)
        val customText = customProfile.buildHighwayText("200+050")
        assertEquals(
            "NH-58 | Muzzafarnagar-Haridwar | Six Laning | 200+050 BOTH M/L BC (Layer 2)",
            customText
        )
        assertEquals("Trial Stretch", customProfile.getCustomCommentLine())
    }

    @Test
    fun `PhotoRepository recovers true capture-time activity from legacy DBM records`() {
        // 1. Legacy record where user selected "BC" + "Layer 1", but DB defaulted activity to "DBM"
        val bcRecord = PhotoRecordEntity(
            id = 1L,
            filePath = "/storage/emulated/0/Pictures/HighwayCamera/NH58--Pkg1--200+050-RHS-ML-BC-Layer1_20260328_100000.jpg",
            fileName = "NH58--Pkg1--200+050-RHS-ML-BC-Layer1_20260328_100000.jpg",
            timestamp = 1700000000000L,
            latitude = 29.5,
            longitude = 77.7,
            accuracy = 3.0f,
            chainageMetres = 200050L,
            chainageLabel = "200+050",
            chainageConfidence = "HIGH",
            isManualChainage = false,
            offsetMetres = 1.2f,
            highwayId = "NH-58",
            packageSection = "Pkg-1",
            roadSide = "RHS",
            lane = "M/L",
            activity = "DBM", // Wrong legacy default
            formattedOverlayText = "NH-58 | Pkg-1 | 200+050 RHS M/L BC (Layer 1)"
        )
        val repairedBc = PhotoRepository.applyRecoveredMetadata(bcRecord)
        assertEquals("BC", repairedBc.activity)
        assertEquals("Layer 1", repairedBc.subActivity)
        assertEquals("NH-58 • RHS • M/L • BC (Layer 1)", repairedBc.displayHighwaySummary())

        // 2. Legacy record where user selected "None" for activity, but DB defaulted activity to "DBM"
        val noneRecord = bcRecord.copy(
            id = 2L,
            fileName = "NH58--Pkg1--200+050-LHS-IL_20260328_100500.jpg",
            roadSide = "LHS",
            lane = "I/L",
            activity = "DBM",
            formattedOverlayText = "NH-58 | Pkg-1 | 200+050 LHS I/L"
        )
        val repairedNone = PhotoRepository.applyRecoveredMetadata(noneRecord)
        assertEquals("None", repairedNone.activity)
        assertEquals("", repairedNone.cleanActivity())
        assertEquals("NH-58 • LHS • I/L", repairedNone.displayHighwaySummary())
        assertFalse(repairedNone.displayHighwaySummary().contains("DBM"))
        assertFalse(repairedNone.displayHighwaySummary().contains("None"))

        // 3. Record where user genuinely selected "DBM"
        val genuineDbmRecord = bcRecord.copy(
            id = 3L,
            activity = "DBM",
            formattedOverlayText = "NH-58 | Pkg-1 | 200+050 RHS M/L DBM"
        )
        assertNull(PhotoRepository.recoverTrueActivityForRecord(genuineDbmRecord))
        assertEquals("DBM", genuineDbmRecord.cleanActivity())

        // 4. New record captured with "WMM" is never modified by recovery
        val wmmRecord = bcRecord.copy(
            id = 4L,
            activity = "WMM",
            subActivity = "Final Layer",
            formattedOverlayText = "NH-58 | Pkg-1 | 200+050 RHS M/L WMM (Final Layer)"
        )
        assertNull(PhotoRepository.recoverTrueActivityForRecord(wmmRecord))
        assertEquals("WMM (Final Layer)", wmmRecord.displayActivityWithSub())
    }

    @Test
    fun `Minimal Smart Chainage Status Indicators accurately reflect KMZ, CUSTOM and MANUAL priority`() {
        // 1. No route loaded -> all three indicators INACTIVE
        val noRouteState = resolveChainageIndicatorsState(
            matchResult = ChainageMatchResult.noRoute(),
            manualChainageText = null,
            hasActiveRoute = false,
            isCorrectionsEnabled = true
        )
        assertEquals("---", noRouteState.displayChainage)
        assertEquals(ChainageBubbleStatus.INACTIVE, noRouteState.kmzStatus)
        assertEquals(ChainageBubbleStatus.INACTIVE, noRouteState.customStatus)
        assertEquals(ChainageBubbleStatus.INACTIVE, noRouteState.manualStatus)

        // 2. Route loaded, active reliable KMZ match -> KMZ is ACTIVE, CUSTOM & MANUAL are INACTIVE
        val kmzMatch = ChainageMatchResult(
            chainage = Chainage(198_880L),
            confidence = MatchConfidence.HIGH,
            source = ChainageSource.KMZ
        )
        val kmzActiveState = resolveChainageIndicatorsState(
            matchResult = kmzMatch,
            manualChainageText = null,
            hasActiveRoute = true,
            isCorrectionsEnabled = true
        )
        assertEquals("198+880", kmzActiveState.displayChainage)
        assertEquals(ChainageBubbleStatus.ACTIVE, kmzActiveState.kmzStatus)
        assertEquals(ChainageBubbleStatus.INACTIVE, kmzActiveState.customStatus)
        assertEquals(ChainageBubbleStatus.INACTIVE, kmzActiveState.manualStatus)

        // 3. Unreliable KMZ match (LOW confidence or uncertain) -> KMZ shows WARNING
        val kmzLowConfMatch = kmzMatch.copy(confidence = MatchConfidence.LOW, isUncertain = true)
        val kmzWarningState = resolveChainageIndicatorsState(
            matchResult = kmzLowConfMatch,
            manualChainageText = null,
            hasActiveRoute = true,
            isCorrectionsEnabled = true
        )
        assertEquals(ChainageBubbleStatus.WARNING, kmzWarningState.kmzStatus)
        assertEquals(ChainageBubbleStatus.INACTIVE, kmzWarningState.customStatus)
        assertEquals(ChainageBubbleStatus.INACTIVE, kmzWarningState.manualStatus)

        // 4. Custom correction genuinely applied to KMZ-derived chainage -> BOTH KMZ and CUSTOM are ACTIVE
        val customCorrectedMatch = ChainageMatchResult(
            chainage = Chainage(198_910L),
            rawUncorrectedChainage = Chainage(198_880L),
            confidence = MatchConfidence.HIGH,
            source = ChainageSource.CUSTOM_CORRECTION,
            correctionPointInfo = "CP-1 (+30m)"
        )
        val customActiveState = resolveChainageIndicatorsState(
            matchResult = customCorrectedMatch,
            manualChainageText = null,
            hasActiveRoute = true,
            isCorrectionsEnabled = true
        )
        assertEquals("198+910", customActiveState.displayChainage)
        assertEquals(ChainageBubbleStatus.ACTIVE, customActiveState.kmzStatus)
        assertEquals(ChainageBubbleStatus.ACTIVE, customActiveState.customStatus)
        assertEquals(ChainageBubbleStatus.INACTIVE, customActiveState.manualStatus)

        // 5. Custom correction disabled -> CUSTOM turns off immediately
        val customDisabledState = resolveChainageIndicatorsState(
            matchResult = customCorrectedMatch,
            manualChainageText = null,
            hasActiveRoute = true,
            isCorrectionsEnabled = false
        )
        assertEquals(ChainageBubbleStatus.ACTIVE, customDisabledState.kmzStatus)
        assertEquals(ChainageBubbleStatus.INACTIVE, customDisabledState.customStatus)
        assertEquals(ChainageBubbleStatus.INACTIVE, customDisabledState.manualStatus)

        // 6. Manual mode active -> MANUAL is ACTIVE, KMZ & CUSTOM are INACTIVE, and manual value is never overwritten by KMZ
        val manualState = resolveChainageIndicatorsState(
            matchResult = customCorrectedMatch, // Even if background KMZ/custom updates arrive
            manualChainageText = "205+500",
            hasActiveRoute = true,
            isCorrectionsEnabled = true
        )
        assertEquals("205+500", manualState.displayChainage)
        assertEquals(ChainageBubbleStatus.INACTIVE, manualState.kmzStatus)
        assertEquals(ChainageBubbleStatus.INACTIVE, manualState.customStatus)
        assertEquals(ChainageBubbleStatus.ACTIVE, manualState.manualStatus)
    }

    @Test
    fun `NextKmStoneTracker satisfies LHS increasing and RHS decreasing specification scenarios`() {
        // Dense 50m KMZ route from 199+000 to 203+000 (simulating Muzzafarnagar-Haridwar-NH-58-50m.kmz)
        val dense50mMarkers = (199_000L..203_000L step 50L).map { m ->
            RouteMarker(
                id = "m-$m",
                label = Chainage.fromMetres(m).format(),
                chainage = Chainage.fromMetres(m),
                point = GeoPoint(29.5 + (m - 199_000L) * 0.00001, 77.7)
            )
        }
        val route = RouteData(
            id = "nh58-50m",
            name = "Muzzafarnagar-Haridwar-NH-58-50m.kmz",
            fileName = "Muzzafarnagar-Haridwar-NH-58-50m.kmz",
            markers = dense50mMarkers,
            alignments = emptyList(),
            bounds = GeoBounds(29.4, 29.6, 77.6, 77.8),
            startChainage = Chainage(199_000L),
            endChainage = Chainage(203_000L)
        )

        val tracker = NextKmStoneTracker()
        fun matchAt(metres: Long) = ChainageMatchResult(
            chainage = Chainage(metres),
            confidence = MatchConfidence.HIGH,
            source = ChainageSource.KMZ
        )

        // LHS - Increasing Chainage scenarios
        val lhs200_050 = tracker.evaluate(matchAt(200_050L), route, "LHS")
        assertEquals("201+000", lhs200_050.nextChainage)
        assertEquals("950 m Away", lhs200_050.distanceText)
        assertEquals("LHS", lhs200_050.directionSide)
        assertEquals("↑", lhs200_050.directionArrow)

        val lhs200_500 = tracker.evaluate(matchAt(200_500L), route, "LHS")
        assertEquals("201+000", lhs200_500.nextChainage)
        assertEquals("500 m Away", lhs200_500.distanceText)

        val lhs200_950 = tracker.evaluate(matchAt(200_950L), route, "LHS")
        assertEquals("201+000", lhs200_950.nextChainage)
        assertEquals("50 m Away", lhs200_950.distanceText)

        // RHS - Decreasing Chainage scenarios
        val rhs200_050 = tracker.evaluate(matchAt(200_050L), route, "RHS")
        assertEquals("200+000", rhs200_050.nextChainage)
        assertEquals("50 m Away", rhs200_050.distanceText)
        assertEquals("RHS", rhs200_050.directionSide)
        assertEquals("↓", rhs200_050.directionArrow)

        val rhs200_500 = tracker.evaluate(matchAt(200_500L), route, "RHS")
        assertEquals("200+000", rhs200_500.nextChainage)
        assertEquals("500 m Away", rhs200_500.distanceText)

        val rhs201_050 = tracker.evaluate(matchAt(201_050L), route, "RHS")
        assertEquals("201+000", rhs201_050.nextChainage)
        assertEquals("50 m Away", rhs201_050.distanceText)
    }

    @Test
    fun `NextKmStoneTracker handles passing KM stone, boundary jitter hysteresis, reverse travel, irregular intervals and GPS loss`() {
        val route = RouteData(
            id = "nh58-std",
            name = "NH-58",
            fileName = "NH-58.kmz",
            markers = emptyList(), // Also verifies calibrated 1 km boundary fallback
            alignments = emptyList(),
            bounds = GeoBounds(29.4, 29.6, 77.6, 77.8),
            startChainage = Chainage(199_000L),
            endChainage = Chainage(203_000L)
        )
        val tracker = NextKmStoneTracker(boundaryHysteresisMetres = 3L)
        fun matchAt(metres: Long) = ChainageMatchResult(
            chainage = Chainage(metres),
            confidence = MatchConfidence.HIGH,
            source = ChainageSource.KMZ
        )

        // 1. Passing a KM Stone on LHS (200+990 -> 201+001 switches to 202+000)
        val beforeBoundary = tracker.evaluate(matchAt(200_990L), route, "LHS")
        assertEquals("201+000", beforeBoundary.nextChainage)
        assertEquals("10 m Away", beforeBoundary.distanceText)

        val crossedBoundary = tracker.evaluate(matchAt(201_001L), route, "LHS")
        assertEquals("202+000", crossedBoundary.nextChainage)
        assertEquals("999 m Away", crossedBoundary.distanceText)

        // 2. Boundary jitter (1-2m GPS fluctuation back to 200+999) does NOT flicker back to 201+000
        val jitterBack = tracker.evaluate(matchAt(200_999L), route, "LHS")
        assertEquals("202+000", jitterBack.nextChainage)
        assertEquals("1000 m Away", jitterBack.distanceText)

        // 3. Genuine reverse travel beyond 3m hysteresis band (back to 200+950) switches back to 201+000
        val reverseTravel = tracker.evaluate(matchAt(200_950L), route, "LHS")
        assertEquals("201+000", reverseTravel.nextChainage)
        assertEquals("50 m Away", reverseTravel.distanceText)

        // 4. Irregular KM Stone intervals (e.g. KM stones at 200+000, 200+850, 202+100)
        val irregularRoute = RouteData(
            id = "nh58-irregular",
            name = "NH-58 Equation Stretch",
            fileName = "NH-58-Eq.kmz",
            markers = listOf(
                RouteMarker("km-1", "KM 200+000", Chainage(200_000L), GeoPoint(29.50, 77.70), folderName = "KM Stones"),
                RouteMarker("km-2", "KM 200+850", Chainage(200_850L), GeoPoint(29.51, 77.70), folderName = "KM Stones"),
                RouteMarker("km-3", "KM 202+100", Chainage(202_100L), GeoPoint(29.52, 77.70), folderName = "KM Stones")
            ),
            alignments = emptyList(),
            bounds = GeoBounds(29.4, 29.6, 77.6, 77.8),
            startChainage = Chainage(200_000L),
            endChainage = Chainage(202_100L)
        )
        val irregularLhs = tracker.evaluate(matchAt(200_050L), irregularRoute, "LHS")
        assertEquals("200+850", irregularLhs.nextChainage)
        assertEquals("800 m Away", irregularLhs.distanceText)

        // Long irregular span > 1000m formats cleanly in kilometres (200+850 -> 202+100 = 1250m -> 1.25 km Away)
        val irregularLongSpan = tracker.evaluate(matchAt(200_850L), irregularRoute, "LHS")
        assertEquals("202+100", irregularLongSpan.nextChainage)
        assertEquals("1.25 km Away", irregularLongSpan.distanceText)

        // 5. Temporary GPS loss & unavailable states never produce negative or fabricated distances
        val gpsLossState = tracker.evaluate(
            ChainageMatchResult(chainage = null, confidence = MatchConfidence.NO_MATCH),
            irregularRoute,
            "LHS"
        )
        assertFalse(gpsLossState.isReliable)
        assertEquals("--+---", gpsLossState.nextChainage)
        assertEquals("Waiting GPS", gpsLossState.distanceText)
        assertNull(gpsLossState.distanceMetres)

        val noRouteState = tracker.evaluate(matchAt(200_500L), null, "LHS")
        assertFalse(noRouteState.isReliable)
        assertEquals("--+---", noRouteState.nextChainage)
        assertEquals("No KMZ", noRouteState.distanceText)
    }
}

