package com.example

import android.content.Context
import android.net.Uri
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
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.RouteRepository
import com.example.geo.ChainageAccuracyRefreshCoordinator
import com.example.geo.ChainageMatcher
import com.example.geo.ChainageProgressionTracker
import com.example.geo.SmartChainageCorrectionEngine
import com.example.geo.StuckChainageMonitor
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.location.LocationTracker
import com.example.model.Chainage
import com.example.model.ChainageAccuracyReport
import com.example.model.ChainageAccuracyStatus
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.PermanentCalibrationProfile
import com.example.model.ReferencePointSource
import com.example.model.RefreshMode
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.ui.dialogs.LiveCalibrationStatusSection
import com.example.ui.home.NextKmStoneTracker
import com.example.ui.theme.DialogThemeTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChainageForceRefreshTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val createdScopes = mutableListOf<CoroutineScope>()
    private val createdDbs = mutableListOf<AppDatabase>()

    @After
    fun tearDown() {
        ChainageMatcher.clearCache()
        createdScopes.forEach { it.cancel() }
        createdDbs.forEach { it.close() }
    }

    private fun createCorridorRoute(
        id: String = "nh58-corridor",
        name: String = "Muzzafarnagar-Haridwar-NH-58-50m.kmz",
        startMetres: Long = 200_000L,
        endMetres: Long = 202_000L
    ): RouteData {
        val p1 = GeoPoint(29.5000, 77.7000, 240.0)
        val p2 = GeoPoint(29.5090, 77.7000, 240.0)
        val p3 = GeoPoint(29.5180, 77.7000, 240.0)

        val midMetres = (startMetres + endMetres) / 2L
        val m1 = RouteMarker("m-start", Chainage(startMetres).format(), Chainage(startMetres), p1)
        val m2 = RouteMarker("m-mid", Chainage(midMetres).format(), Chainage(midMetres), p2)
        val m3 = RouteMarker("m-end", Chainage(endMetres).format(), Chainage(endMetres), p3)

        val align = RouteAlignment(
            id = "align-main",
            name = "NH-58 Main Carriageway",
            points = listOf(p1, p2, p3),
            direction = "INCREASING",
            carriagewayType = "MAIN"
        )

        return RouteData(
            id = id,
            name = name,
            fileName = name,
            markers = listOf(m1, m2, m3),
            alignments = listOf(align),
            bounds = GeoBounds.fromPoints(listOf(p1, p2, p3)),
            startChainage = Chainage(startMetres),
            endChainage = Chainage(endMetres),
            selectedAlignmentId = align.id
        )
    }

    private fun sampleKmlString(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <name>NH-58 Test Alignment</name>
            <Placemark>
              <name>Main Line</name>
              <LineString>
                <coordinates>
                  77.7000,29.5000,240
                  77.7000,29.5090,240
                  77.7000,29.5180,240
                </coordinates>
              </LineString>
            </Placemark>
            <Placemark><name>200+000</name><Point><coordinates>77.7000,29.5000,240</coordinates></Point></Placemark>
            <Placemark><name>201+000</name><Point><coordinates>77.7000,29.5090,240</coordinates></Point></Placemark>
            <Placemark><name>202+000</name><Point><coordinates>77.7000,29.5180,240</coordinates></Point></Placemark>
          </Document>
        </kml>
    """.trimIndent()

    // -------------------------------------------------------------------------
    // TEST 1: Stuck chainage detection & recovery vs. stationary protection
    // -------------------------------------------------------------------------
    @Test
    fun `stuck chainage triggers recovery only on real GPS displacement and ignores stationary jitter`() {
        val monitor = StuckChainageMonitor(
            movementThresholdMetres = 12.0,
            stationaryBandMetres = 4.5,
            requiredStalledFixes = 3,
            recoveryCooldownMs = 1_000L
        )
        val stuckMatch = ChainageMatchResult(
            chainage = Chainage(200_500L),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 3.5f,
            detectedSide = "RHS",
            source = ChainageSource.KMZ
        )

        // Anchor at 29.5045, 77.7000
        assertFalse(
            monitor.evaluate(GeoPoint(29.5045, 77.7000), 1000L, stuckMatch, "r1", false, nowMs = 1000L).isStuck
        )

        // Stationary jitter < 4.5m across 5 fixes must NEVER trigger recovery
        for (i in 1..5) {
            val jitter = GeoPoint(29.5045 + i * 0.000004, 77.7000) // ~0.4m - 2.2m
            val res = monitor.evaluate(jitter, 1000L + i * 1000L, stuckMatch, "r1", false, nowMs = 1000L + i * 1000L)
            assertFalse("Stationary device must not trigger false stuck recovery", res.isStuck)
        }

        // Now GPS displaces >= 15m across 3 consecutive fixes while chainage stays frozen at 200+500
        val movedPt1 = GeoPoint(29.5047, 77.7000) // ~22m north
        val movedPt2 = GeoPoint(29.5049, 77.7000) // ~44m north
        val movedPt3 = GeoPoint(29.5051, 77.7000) // ~66m north

        assertFalse(monitor.evaluate(movedPt1, 7000L, stuckMatch, "r1", false, nowMs = 7000L).isStuck)
        assertFalse(monitor.evaluate(movedPt2, 8000L, stuckMatch, "r1", false, nowMs = 8000L).isStuck)
        val triggered = monitor.evaluate(movedPt3, 9000L, stuckMatch, "r1", false, nowMs = 9000L)
        assertTrue("3+ displaced fixes with stuck chainage should trigger recovery", triggered.isStuck)
        assertTrue(triggered.affectedComponent.isNotBlank())

        SmartChainageCorrectionEngine.recordStuckChainageRecovery(triggered.affectedComponent, triggered.reason)
        assertTrue(SmartChainageCorrectionEngine.diagnosticsFlow.value.stuckRecoveryCount >= 1L)
    }

    // -------------------------------------------------------------------------
    // TEST 2: Force Refresh All resets progression hold & recalculates immediately
    // -------------------------------------------------------------------------
    @Test
    fun `force refresh resets stale smoothing lock and snaps calibrated chainage immediately`() {
        val route = createCorridorRoute()
        val tracker = ChainageProgressionTracker()
        val coordinator = ChainageAccuracyRefreshCoordinator()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locTracker = LocationTracker(context)

        // Seed stale progression lock at 200+100
        val staleResult = ChainageMatchResult(
            chainage = Chainage(200_100L),
            projectedPoint = GeoPoint(29.5009, 77.7000),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 3.0f,
            detectedSide = "RHS",
            source = ChainageSource.KMZ
        )
        tracker.processResult(staleResult, route.id)

        // GPS is actually at midpoint ~201+000 (29.5090)
        val nowMs = System.currentTimeMillis()
        val locState = LocationState(
            point = GeoPoint(29.5090, 77.7000, 240.0),
            accuracy = 3.2f,
            status = LocationStatus.READY,
            lastFixTime = nowMs
        )
        val gpsVal = locTracker.validateLocationState(locState, nowMs = nowMs)
        val sync = coordinator.synchronizeCalibrationData(route, emptyList(), emptyList(), forceRevalidate = true)

        val output = coordinator.recalculateAndValidate(
            generation = coordinator.bumpGeneration(),
            mode = RefreshMode.FORCE_ALL,
            locationState = locState,
            gpsValidation = gpsVal,
            route = route,
            routeValidGeometry = true,
            calibrationSync = sync,
            isCorrectionsEnabled = true,
            previousResult = staleResult,
            progressionTracker = tracker,
            manualChainage = null,
            manualChainageText = null,
            gpsRefreshSucceeded = true,
            nowMs = nowMs
        )

        val snapped = output.matchResult.chainage?.metres ?: 0L
        assertTrue("Expected snapped chainage near 201+000, got $snapped", snapped in 200_960L..201_040L)
        assertEquals(ChainageAccuracyStatus.UPDATED, output.report.status)
    }

    // -------------------------------------------------------------------------
    // TEST 3: GPS location validation & stale fix detection (zero fabrication)
    // -------------------------------------------------------------------------
    @Test
    fun `gps validation rejects invalid zero or NaN coords and flags stale fixes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locTracker = LocationTracker(context)
        val nowMs = 1_000_000L

        assertFalse(LocationTracker.isValidCoordinate(0.0, 0.0))
        assertFalse(LocationTracker.isValidCoordinate(Double.NaN, 77.7))
        assertFalse(LocationTracker.isValidCoordinate(91.0, 77.7))
        assertTrue(LocationTracker.isValidCoordinate(29.5045, 77.7000))

        val missing = locTracker.validateLocationState(LocationState(point = null), nowMs = nowMs)
        assertFalse(missing.isValid)
        assertFalse(missing.isFresh)

        val stale = locTracker.validateLocationState(
            LocationState(
                point = GeoPoint(29.5045, 77.7000),
                accuracy = 4.0f,
                status = LocationStatus.READY,
                lastFixTime = nowMs - 45_000L
            ),
            nowMs = nowMs
        )
        assertTrue(stale.isValid)
        assertFalse(stale.isFresh)
        assertTrue(stale.reason.contains("Stale", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // TEST 4: KMZ/KML spatial cache reuse vs forced rebuild & file preservation
    // -------------------------------------------------------------------------
    @Test
    fun `route validation avoids unnecessary reparse and preserves KMZ file while rebuilding cache cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        createdDbs.add(db)
        val prefs = PreferencesManager(context)
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        createdScopes.add(repoScope)
        val repo = RouteRepository(context, db.routeDao(), prefs, repoScope)

        val kmlFile = File(context.cacheDir, "test_refresh_nh58_${System.currentTimeMillis()}.kml").apply {
            writeText(sampleKmlString())
        }
        try {
            val importRes = repo.importRouteFromUri(Uri.fromFile(kmlFile), "NH58-Test.kml")
            assertTrue("KML import should succeed", importRes.isSuccess)
            val route = importRes.getOrThrow()

            // Wait briefly or run Smart Refresh: unchanged file must NOT trigger XML reparse
            val smartOutcome = repo.validateAndRefreshActiveRoute(forceRebuildCache = false)
            assertTrue(smartOutcome.isValidGeometry)
            assertFalse("Unchanged KML should not re-parse needlessly", smartOutcome.reparsedFromFile)
            assertNotNull(smartOutcome.route)

            // Force Refresh All: spatial index cache must rebuild while preserving stored KML & alignment ID
            val forceOutcome = repo.validateAndRefreshActiveRoute(forceRebuildCache = true)
            assertTrue(forceOutcome.isValidGeometry)
            assertTrue("FORCE_ALL should rebuild spatial index", forceOutcome.rebuiltSpatialCache)
            assertEquals(route.id, forceOutcome.route?.id)
            assertTrue("Imported route file must remain preserved", kmlFile.exists())
        } finally {
            kmlFile.delete()
        }
    }

    // -------------------------------------------------------------------------
    // TEST 5: Permanent calibration refs preserved & duplicate/conflicts caught
    // -------------------------------------------------------------------------
    @Test
    fun `permanent calibration points and profiles are preserved and conflicts flagged as Needs Review`() {
        val route = createCorridorRoute()
        val coordinator = ChainageAccuracyRefreshCoordinator()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locTracker = LocationTracker(context)

        // Two duplicate/conflicting reference points at practically the same position (200+500)
        val pConflict1 = ChainageCorrectionPoint(
            id = "cp-1",
            routeId = route.id,
            latitude = 29.5045,
            longitude = 77.7000,
            originalKmzMetres = 200_500L,
            correctedMetres = 200_650L,
            routeDistanceMetres = 500.0,
            referenceSource = ReferencePointSource.MANUAL
        )
        val pConflict2 = ChainageCorrectionPoint(
            id = "cp-2",
            routeId = route.id,
            latitude = 29.5045,
            longitude = 77.7000,
            originalKmzMetres = 200_500L,
            correctedMetres = 200_800L,
            routeDistanceMetres = 500.0,
            referenceSource = ReferencePointSource.GPS_VERIFIED
        )

        val profile = PermanentCalibrationProfile(
            profileId = "prof-1",
            profileName = "NH-58 Verified Profile",
            projectId = "proj-1",
            routeId = route.id,
            originalFileName = route.fileName,
            alignmentFingerprint = SmartChainageCorrectionEngine.computeAlignmentFingerprint(route),
            controlPoints = listOf(pConflict1, pConflict2)
        )

        val sync = coordinator.synchronizeCalibrationData(
            route = route,
            rawPoints = listOf(pConflict1, pConflict2),
            profiles = listOf(profile),
            forceRevalidate = true
        )

        // Both control points MUST remain preserved (never deleted silently)
        assertEquals(2, sync.syncedPoints.size)
        assertTrue("Duplicate references should be detected", sync.hasDuplicates)

        val nowMs = System.currentTimeMillis()
        val locState = LocationState(
            point = GeoPoint(29.5045, 77.7000, 240.0),
            accuracy = 3.0f,
            status = LocationStatus.READY,
            lastFixTime = nowMs
        )
        val out = coordinator.recalculateAndValidate(
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

        assertFalse(out.report.isControlPointConsistent)
        assertEquals(ChainageAccuracyStatus.CALIBRATION_NEEDS_REVIEW, out.report.status)
    }

    // -------------------------------------------------------------------------
    // TEST 6: LHS / RHS detection refreshed cleanly with hysteresis stability
    // -------------------------------------------------------------------------
    @Test
    fun `lhs rhs carriageway side updates accurately after refresh with hysteresis rules`() {
        val route = createCorridorRoute()
        val tracker = ChainageProgressionTracker()
        val coordinator = ChainageAccuracyRefreshCoordinator()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locTracker = LocationTracker(context)
        val sync = coordinator.synchronizeCalibrationData(route, emptyList(), emptyList(), forceRevalidate = false)

        // Northbound alignment at lng=77.7000: east (77.70015) is RHS, west (77.69985) is LHS
        val nowMs = System.currentTimeMillis()
        val rhsLoc = LocationState(
            point = GeoPoint(29.5045, 77.70015, 240.0),
            accuracy = 3.0f,
            status = LocationStatus.READY,
            lastFixTime = nowMs
        )
        val outRhs = coordinator.recalculateAndValidate(
            generation = coordinator.bumpGeneration(),
            mode = RefreshMode.FORCE_ALL,
            locationState = rhsLoc,
            gpsValidation = locTracker.validateLocationState(rhsLoc, nowMs = nowMs),
            route = route,
            routeValidGeometry = true,
            calibrationSync = sync,
            isCorrectionsEnabled = false,
            previousResult = null,
            progressionTracker = tracker,
            manualChainage = null,
            manualChainageText = null,
            gpsRefreshSucceeded = true,
            nowMs = nowMs
        )
        assertEquals("RHS", outRhs.matchResult.detectedSide)
        assertTrue(outRhs.report.isSideConsistent)

        // Single noisy LHS reading is dampened by 2-reading hysteresis
        val lhsLoc = LocationState(
            point = GeoPoint(29.5045, 77.69985, 240.0),
            accuracy = 3.0f,
            status = LocationStatus.READY,
            lastFixTime = nowMs + 1000L
        )
        val outFlip1 = coordinator.recalculateAndValidate(
            generation = coordinator.bumpGeneration(),
            mode = RefreshMode.SMART,
            locationState = lhsLoc,
            gpsValidation = locTracker.validateLocationState(lhsLoc, nowMs = nowMs + 1000L),
            route = route,
            routeValidGeometry = true,
            calibrationSync = sync,
            isCorrectionsEnabled = false,
            previousResult = outRhs.matchResult,
            progressionTracker = tracker,
            manualChainage = null,
            manualChainageText = null,
            gpsRefreshSucceeded = true,
            nowMs = nowMs + 1000L
        )
        assertEquals("Single noisy flip must hold RHS via hysteresis", "RHS", outFlip1.matchResult.detectedSide)

        // Second confirmed LHS reading switches cleanly to LHS
        val outFlip2 = coordinator.recalculateAndValidate(
            generation = coordinator.bumpGeneration(),
            mode = RefreshMode.FORCE_ALL,
            locationState = lhsLoc.copy(lastFixTime = nowMs + 2000L),
            gpsValidation = locTracker.validateLocationState(lhsLoc.copy(lastFixTime = nowMs + 2000L), nowMs = nowMs + 2000L),
            route = route,
            routeValidGeometry = true,
            calibrationSync = sync,
            isCorrectionsEnabled = false,
            previousResult = outFlip1.matchResult,
            progressionTracker = tracker,
            manualChainage = null,
            manualChainageText = null,
            gpsRefreshSucceeded = true,
            nowMs = nowMs + 2000L
        )
        assertEquals("LHS", outFlip2.matchResult.detectedSide)
    }

    // -------------------------------------------------------------------------
    // TEST 7: Manual chainage mode preserved & never overwritten by refresh
    // -------------------------------------------------------------------------
    @Test
    fun `manual chainage override is synchronized and never overwritten during Force Refresh All`() {
        val route = createCorridorRoute()
        val coordinator = ChainageAccuracyRefreshCoordinator()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locTracker = LocationTracker(context)
        val nowMs = System.currentTimeMillis()
        val locState = LocationState(
            point = GeoPoint(29.5045, 77.7000, 240.0),
            accuracy = 3.0f,
            status = LocationStatus.READY,
            lastFixTime = nowMs
        )
        val sync = coordinator.synchronizeCalibrationData(route, emptyList(), emptyList(), forceRevalidate = true)

        val manual = Chainage(125_400L)
        val out = coordinator.recalculateAndValidate(
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
            manualChainage = manual,
            manualChainageText = "125+400",
            gpsRefreshSucceeded = true,
            nowMs = nowMs
        )

        assertTrue(out.report.isManualOverrideActive)
        assertEquals(ChainageAccuracyStatus.UPDATED, out.report.status)
        assertTrue(out.report.summaryMessage.contains("Manual override preserved"))
        // Background KMZ reference updated cleanly without altering manual value
        assertNotNull(out.matchResult.chainage)
    }

    // -------------------------------------------------------------------------
    // TEST 8: Custom correction remains functional & updates calibrated output
    // -------------------------------------------------------------------------
    @Test
    fun `custom calibration control points interpolate calibrated chainage cleanly after refresh`() {
        val route = createCorridorRoute()
        val coordinator = ChainageAccuracyRefreshCoordinator()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locTracker = LocationTracker(context)

        val cpStart = ChainageCorrectionPoint(
            id = "cp-start",
            routeId = route.id,
            latitude = 29.5000,
            longitude = 77.7000,
            originalKmzMetres = 200_000L,
            correctedMetres = 200_150L,
            routeDistanceMetres = 0.0
        )
        val cpEnd = ChainageCorrectionPoint(
            id = "cp-end",
            routeId = route.id,
            latitude = 29.5180,
            longitude = 77.7000,
            originalKmzMetres = 202_000L,
            correctedMetres = 202_250L,
            routeDistanceMetres = 2000.0
        )

        val sync = coordinator.synchronizeCalibrationData(
            route = route,
            rawPoints = listOf(cpStart, cpEnd),
            profiles = emptyList(),
            forceRevalidate = true
        )
        assertFalse(sync.hasDuplicates)
        assertFalse(sync.conflictReport.hasConflict)

        val nowMs = System.currentTimeMillis()
        val locState = LocationState(
            point = GeoPoint(29.5090, 77.7000, 240.0), // exact midpoint (~201+000 raw)
            accuracy = 2.5f,
            status = LocationStatus.READY,
            lastFixTime = nowMs
        )
        val out = coordinator.recalculateAndValidate(
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

        assertEquals(ChainageSource.CUSTOM_CORRECTION, out.matchResult.source)
        val correctedMetres = out.matchResult.chainage?.metres ?: 0L
        // Midpoint raw is ~201+000, interpolated offset is ~+200m => ~201+200
        assertTrue("Expected interpolated calibrated chainage ~201+200, got $correctedMetres", correctedMetres in 201_170L..201_230L)
        assertTrue(out.report.isCustomCorrectionValid)
    }

    // -------------------------------------------------------------------------
    // TEST 9: Next KM Stone Card updates immediately from refreshed result
    // -------------------------------------------------------------------------
    @Test
    fun `next km stone card recalculates immediately when chainage or side refreshes`() {
        val route = createCorridorRoute(startMetres = 200_000L, endMetres = 202_000L)
        val stoneTracker = NextKmStoneTracker()

        val beforeMatch = ChainageMatchResult(
            chainage = Chainage(200_050L),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 3.0f,
            offsetMetres = 2.0,
            detectedSide = "LHS"
        )
        val beforeStone = stoneTracker.evaluate(beforeMatch, route, "LHS")
        assertEquals(201_000L, beforeStone.nextChainageMetres)
        assertEquals(950L, beforeStone.distanceMetres)

        // Simulate refreshed match result moving forward to 200+450
        val afterMatch = beforeMatch.copy(chainage = Chainage(200_450L))
        val afterStone = stoneTracker.evaluate(afterMatch, route, "LHS")
        assertEquals(201_000L, afterStone.nextChainageMetres)
        assertEquals(550L, afterStone.distanceMetres)
    }

    // -------------------------------------------------------------------------
    // TEST 10: Repeated rapid refresh prevention & stale async generation guard
    // -------------------------------------------------------------------------
    @Test
    fun `concurrent refresh guard prevents duplicate runs and detects stale generation`() {
        val coordinator = ChainageAccuracyRefreshCoordinator()
        val firstGen = coordinator.tryBeginRefresh()
        assertNotNull("First refresh acquires lock", firstGen)
        assertTrue(coordinator.isBusy())

        val secondGen = coordinator.tryBeginRefresh()
        assertNull("Repeated simultaneous click while busy must be blocked", secondGen)

        // Even if another newer cycle bumps generation, old generation is detectable as stale
        val newerGen = coordinator.bumpGeneration()
        assertTrue(newerGen > (firstGen ?: 0L))

        coordinator.endRefresh()
        assertFalse(coordinator.isBusy())
        val thirdGen = coordinator.tryBeginRefresh()
        assertNotNull("After completion, new refresh should succeed cleanly", thirdGen)
        coordinator.endRefresh()
    }

    // -------------------------------------------------------------------------
    // TEST 11: Temporary GPS loss retains last valid chainage safely (no fake GPS)
    // -------------------------------------------------------------------------
    @Test
    fun `temporary gps loss retains last valid chainage marked stale without fabricating coordinates`() {
        val route = createCorridorRoute()
        val coordinator = ChainageAccuracyRefreshCoordinator()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locTracker = LocationTracker(context)
        val sync = coordinator.synchronizeCalibrationData(route, emptyList(), emptyList(), forceRevalidate = false)

        val lastValid = ChainageMatchResult(
            chainage = Chainage(200_750L),
            projectedPoint = GeoPoint(29.5067, 77.7000),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 3.0f,
            detectedSide = "RHS",
            source = ChainageSource.KMZ
        )

        val lostGps = LocationState(point = null, status = LocationStatus.ACQUIRING)
        val gpsVal = locTracker.validateLocationState(lostGps)

        val out = coordinator.recalculateAndValidate(
            generation = coordinator.bumpGeneration(),
            mode = RefreshMode.FORCE_ALL,
            locationState = lostGps,
            gpsValidation = gpsVal,
            route = route,
            routeValidGeometry = true,
            calibrationSync = sync,
            isCorrectionsEnabled = true,
            previousResult = lastValid,
            progressionTracker = ChainageProgressionTracker(),
            manualChainage = null,
            manualChainageText = null,
            gpsRefreshSucceeded = false
        )

        assertEquals(ChainageAccuracyStatus.NEEDS_GPS, out.report.status)
        assertTrue("Previous valid chainage must be retained and marked stale", out.report.isStaleFallback)
        assertEquals(200_750L, out.matchResult.chainage?.metres)
        assertFalse(out.report.isGpsValid)
    }

    // -------------------------------------------------------------------------
    // TEST 12: Failed alignment/off-route stale fallback + Force Refresh All UI
    // -------------------------------------------------------------------------
    @Test
    fun `failed alignment refresh preserves stale fallback and Force Refresh All UI enforces lock and themes`() {
        val route = createCorridorRoute()
        val coordinator = ChainageAccuracyRefreshCoordinator()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locTracker = LocationTracker(context)
        val nowMs = System.currentTimeMillis()

        val lastValid = ChainageMatchResult(
            chainage = Chainage(200_500L),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 3.0f,
            detectedSide = "RHS",
            source = ChainageSource.KMZ
        )
        val validGps = LocationState(
            point = GeoPoint(29.5045, 77.7000),
            accuracy = 3.0f,
            status = LocationStatus.READY,
            lastFixTime = nowMs
        )
        val outInvalidRoute = coordinator.recalculateAndValidate(
            generation = coordinator.bumpGeneration(),
            mode = RefreshMode.FORCE_ALL,
            locationState = validGps,
            gpsValidation = locTracker.validateLocationState(validGps, nowMs = nowMs),
            route = route,
            routeValidGeometry = false,
            calibrationSync = coordinator.synchronizeCalibrationData(null, emptyList(), emptyList(), false),
            isCorrectionsEnabled = true,
            previousResult = lastValid,
            progressionTracker = ChainageProgressionTracker(),
            manualChainage = null,
            manualChainageText = null,
            gpsRefreshSucceeded = true,
            nowMs = nowMs
        )
        assertEquals(ChainageAccuracyStatus.ALIGNMENT_MISMATCH, outInvalidRoute.report.status)
        assertTrue(outInvalidRoute.report.isStaleFallback)
        assertEquals(200_500L, outInvalidRoute.matchResult.chainage?.metres)

        // Robolectric Compose UI test: Light & Dark themes, click handling, disabled progress state, status badge
        var clickCount by mutableIntStateOf(0)
        var isRefreshing by mutableStateOf(false)
        var report by mutableStateOf<ChainageAccuracyReport?>(null)
        var useDarkTheme by mutableStateOf(false)

        composeTestRule.setContent {
            val colors = if (useDarkTheme) {
                DialogThemeTokens(
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
            } else {
                DialogThemeTokens(
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
            }
            MaterialTheme(colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()) {
                LiveCalibrationStatusSection(
                    activeRoute = route,
                    matchResult = lastValid,
                    correctionPoints = emptyList(),
                    isCorrectionsEnabled = true,
                    isRefreshingAccuracy = isRefreshing,
                    accuracyReport = report,
                    primaryAccent = Color(0xFF0284C7),
                    greenAccent = Color(0xFF10B981),
                    amberAccent = Color(0xFFF59E0B),
                    redAccent = Color(0xFFEF4444),
                    dialogColors = colors,
                    onToggleEnabled = {},
                    onForceRefreshAll = {
                        clickCount++
                        isRefreshing = true
                    }
                )
            }
        }

        // Light theme idle state
        composeTestRule.onNodeWithTag("force_refresh_all_chainage_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        assertEquals(1, clickCount)
        composeTestRule.waitForIdle()

        // Active refreshing state: button disabled + progress indicator shown -> repeated tap ignored
        composeTestRule.onNodeWithTag("force_refresh_all_chainage_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("force_refresh_all_progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("force_refresh_all_chainage_button").performClick()
        assertEquals(1, clickCount)

        // Complete refresh in Dark theme with Verified status report
        composeTestRule.runOnIdle {
            useDarkTheme = true
            isRefreshing = false
            report = ChainageAccuracyReport(
                status = ChainageAccuracyStatus.VERIFIED,
                mode = RefreshMode.FORCE_ALL,
                isRefreshing = false,
                isStaleFallback = false,
                summaryMessage = "Accurate / Verified • 200+500 (RHS)"
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("force_refresh_all_chainage_button").assertIsEnabled()
        composeTestRule.onNodeWithTag("chainage_accuracy_status_badge")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Accurate / Verified • 200+500 (RHS)").assertIsDisplayed()
    }
}
