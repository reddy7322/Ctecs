package com.example

import com.example.data.PhotoRecordEntity
import com.example.data.PhotoRepository
import com.example.geo.SmartChainageCorrectionEngine
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
