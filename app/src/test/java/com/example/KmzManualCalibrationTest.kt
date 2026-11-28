package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PhotoRecordEntity
import com.example.data.PreferencesManager
import com.example.data.RouteEntity
import com.example.geo.ChainageMatcher
import com.example.geo.ChainageProgressionTracker
import com.example.geo.KmzCalibrationEngine
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.location.LocationTracker
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.DirectionControlMode
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.KmzCalibrationCoverageStatus
import com.example.model.KmzManualCalibration
import com.example.model.MatchConfidence
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.navigation.CtecsNavigationTelemetryHub
import com.example.overlay.OverlayRenderer
import com.example.ui.MainViewModel
import com.example.ui.dialogs.KmzCalibrationDialogContent
import com.example.ui.theme.MyApplicationTheme
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
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KmzManualCalibrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager

    private val degPerMetre = 1.0 / 111_132.0
    private val baseLat = 29.500000
    private val baseLng = 77.700000

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        context = application
        preferencesManager = PreferencesManager(context)
        ChainageMatcher.clearCache()
        runBlocking {
            preferencesManager.resetAllSettings()
            delay(40)
        }
        PreferencesManager.clearMemoryCacheForProcessRestart()
        CtecsNavigationTelemetryHub.ensureInitialized(context, forceReload = true)
    }

    @After
    fun tearDown() {
        ChainageMatcher.clearCache()
        runBlocking {
            preferencesManager.resetAllSettings()
            delay(40)
        }
        PreferencesManager.clearMemoryCacheForProcessRestart()
    }

    private fun createRoute(
        id: String = "route-nh58",
        fileName: String = "NH58_Alignment.kmz",
        startMetres: Long = 188_000L,
        endMetres: Long = 198_000L,
        decreasing: Boolean = false,
        parallelSides: Boolean = false
    ): RouteData {
        val spanKm = (abs(endMetres - startMetres) / 1000L).toInt()
        val points = (0..spanKm).map { km ->
            GeoPoint(baseLat + (km * 1000.0) * degPerMetre, baseLng, 250.0)
        }
        val markers = (0..spanKm).map { km ->
            val ch = if (decreasing) endMetres - km * 1000L else startMetres + km * 1000L
            RouteMarker("m-$km", Chainage(ch).format(), Chainage(ch), points[km])
        }
        val mainAlign = RouteAlignment(
            id = if (parallelSides) "align-lhs" else "align-main",
            name = if (parallelSides) "LHS Mainline" else "NH-58 Main Carriageway",
            points = points,
            direction = if (decreasing) "DECREASING" else "INCREASING",
            carriagewayType = if (parallelSides) "LHS" else "MAIN"
        )
        val alignments = mutableListOf(mainAlign)
        if (parallelSides) {
            val rhsPts = points.map { GeoPoint(it.latitude, it.longitude + 0.00025, 250.0) }
            alignments += RouteAlignment(
                id = "align-rhs",
                name = "RHS Mainline",
                points = rhsPts,
                direction = "INCREASING",
                carriagewayType = "RHS"
            )
        }
        return RouteData(
            id = id,
            name = "NH-58 Test Route",
            fileName = fileName,
            markers = markers,
            alignments = alignments,
            bounds = GeoBounds.fromPoints(points),
            startChainage = Chainage(startMetres),
            endChainage = Chainage(endMetres),
            selectedAlignmentId = mainAlign.id
        )
    }

    private fun pointAlongIncreasingRoute(startMetres: Long, chainageMetres: Long, lngOffset: Double = 0.0): GeoPoint {
        val distM = (chainageMetres - startMetres).toDouble()
        return GeoPoint(baseLat + distM * degPerMetre, baseLng + lngOffset, 250.0)
    }

    private fun writeSampleKmlFile(
        fileName: String = "nh58_188_198.kml",
        startKm: Int = 188,
        endKm: Int = 198
    ): File {
        val file = File(context.filesDir, fileName)
        val coords = (startKm..endKm).joinToString(" ") { km ->
            val lat = baseLat + ((km - startKm) * 1000.0) * degPerMetre
            "$baseLng,$lat,250"
        }
        val placemarks = (startKm..endKm).joinToString("\n") { km ->
            val lat = baseLat + ((km - startKm) * 1000.0) * degPerMetre
            "<Placemark><name>$km+000</name><Point><coordinates>$baseLng,$lat,250</coordinates></Point></Placemark>"
        }
        val kml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("  <Document>")
            appendLine("    <name>NH-58 Corridor</name>")
            appendLine("    <Placemark>")
            appendLine("      <name>Mainline Alignment</name>")
            appendLine("      <LineString><coordinates>$coords</coordinates></LineString>")
            appendLine("    </Placemark>")
            appendLine("    $placemarks")
            appendLine("  </Document>")
            appendLine("</kml>")
        }
        file.writeText(kml)
        return file
    }

    private suspend fun seedActiveKmlRouteInDbAndPrefs(routeId: String = "route-kml-1"): Pair<File, RouteData> {
        val kmlFile = writeSampleKmlFile()
        val parsed = com.example.parser.KmzParser.parse(kmlFile, kmlFile.name).copy(id = routeId)
        val db = AppDatabase.getDatabase(context)
        db.routeDao().insertRoute(
            RouteEntity(
                id = routeId,
                name = parsed.name,
                fileName = parsed.fileName,
                markerCount = parsed.markers.size,
                startChainageMetres = parsed.startChainage?.metres ?: 0L,
                endChainageMetres = parsed.endChainage?.metres ?: 0L,
                minLat = parsed.bounds.minLat,
                maxLat = parsed.bounds.maxLat,
                minLng = parsed.bounds.minLng,
                maxLng = parsed.bounds.maxLng,
                localFilePath = kmlFile.absolutePath
            )
        )
        preferencesManager.setActiveRouteId(routeId)
        PreferencesManager.awaitPendingWrites()
        return kmlFile to parsed
    }

    private fun pushFreshGpsFix(
        tracker: LocationTracker,
        lat: Double,
        lng: Double,
        fixTimeMs: Long,
        accuracy: Float = 3.5f
    ) {
        val loc = Location("fused").apply {
            latitude = lat
            longitude = lng
            this.accuracy = accuracy
            speed = 8.0f
            bearing = 0f
            time = fixTimeMs
        }
        assertTrue(tracker.handleNewLocation(loc, nowMs = fixTimeMs))
    }

    // 1. Positive offset (+30 m: 192+200 -> 192+230)
    @Test
    fun `scenario 1 positive offset computes +30m and outputs 192+230 at reference`() {
        val route = createRoute()
        val cal = KmzCalibrationEngine.createCalibration(
            routeData = route,
            alignment = route.activeAlignment,
            originalKmzMetres = 192_200L,
            manualReferenceMetres = 192_230L
        )
        assertEquals(30L, cal.offsetMetres)
        assertEquals("+30 m", cal.formattedOffset)

        val key = KmzCalibrationEngine.storageKey(cal.routeId, cal.alignmentId)
        val eval = KmzCalibrationEngine.evaluateAndApply(
            rawChainage = Chainage(192_200L),
            distanceAlongAlignmentMetres = cal.anchorDistanceAlongMetres,
            routeData = route,
            calibrations = mapOf(key to cal),
            matchedAlignmentId = cal.alignmentId
        )
        assertEquals(KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE, eval.status)
        assertEquals(192_230L, eval.outputChainage.metres)
        assertEquals(192_200L, eval.rawKmzChainage.metres)
        assertEquals(30L, eval.appliedOffsetMetres)
    }

    // 2. Negative offset (-30 m: 192+200 -> 192+170)
    @Test
    fun `scenario 2 negative offset computes -30m and outputs 192+170 at reference`() {
        val route = createRoute()
        val cal = KmzCalibrationEngine.createCalibration(
            routeData = route,
            alignment = route.activeAlignment,
            originalKmzMetres = 192_200L,
            manualReferenceMetres = 192_170L
        )
        assertEquals(-30L, cal.offsetMetres)
        assertEquals("-30 m", cal.formattedOffset)

        val key = KmzCalibrationEngine.storageKey(cal.routeId, cal.alignmentId)
        val eval = KmzCalibrationEngine.evaluateAndApply(
            rawChainage = Chainage(192_200L),
            distanceAlongAlignmentMetres = cal.anchorDistanceAlongMetres,
            routeData = route,
            calibrations = mapOf(key to cal),
            matchedAlignmentId = cal.alignmentId
        )
        assertEquals(KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE, eval.status)
        assertEquals(192_170L, eval.outputChainage.metres)
    }

    // 3 & 4. Forward and Reverse movement within ±2 km (increasing & decreasing alignments)
    @Test
    fun `scenarios 3 and 4 forward and reverse movement within 2km apply single offset on increasing and decreasing alignments`() {
        val incRoute = createRoute(decreasing = false)
        val incCal = KmzCalibrationEngine.createCalibration(
            routeData = incRoute,
            alignment = incRoute.activeAlignment,
            originalKmzMetres = 192_200L,
            manualReferenceMetres = 192_230L
        )
        val incMap = mapOf(KmzCalibrationEngine.storageKey(incCal.routeId, incCal.alignmentId) to incCal)

        // Forward +500m & +1500m
        val incFwd500 = KmzCalibrationEngine.evaluateAndApply(
            Chainage(192_700L), incCal.anchorDistanceAlongMetres + 500.0, incRoute, incMap, incCal.alignmentId
        )
        assertEquals(KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE, incFwd500.status)
        assertEquals(192_730L, incFwd500.outputChainage.metres)

        val incFwd1500 = KmzCalibrationEngine.evaluateAndApply(
            Chainage(193_700L), incCal.anchorDistanceAlongMetres + 1500.0, incRoute, incMap, incCal.alignmentId
        )
        assertEquals(193_730L, incFwd1500.outputChainage.metres)

        // Reverse -500m & -1500m
        val incRev500 = KmzCalibrationEngine.evaluateAndApply(
            Chainage(191_700L), incCal.anchorDistanceAlongMetres - 500.0, incRoute, incMap, incCal.alignmentId
        )
        assertEquals(KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE, incRev500.status)
        assertEquals(191_730L, incRev500.outputChainage.metres)

        val incRev1500 = KmzCalibrationEngine.evaluateAndApply(
            Chainage(190_700L), incCal.anchorDistanceAlongMetres - 1500.0, incRoute, incMap, incCal.alignmentId
        )
        assertEquals(190_730L, incRev1500.outputChainage.metres)

        // Decreasing alignment (chainage decreases as distanceAlong increases)
        val decRoute = createRoute(id = "route-dec", decreasing = true)
        val decCal = KmzCalibrationEngine.createCalibration(
            routeData = decRoute,
            alignment = decRoute.activeAlignment,
            originalKmzMetres = 193_000L,
            manualReferenceMetres = 192_970L // -30m offset
        )
        val decMap = mapOf(KmzCalibrationEngine.storageKey(decCal.routeId, decCal.alignmentId) to decCal)
        val decAhead = KmzCalibrationEngine.evaluateAndApply(
            Chainage(191_500L), decCal.anchorDistanceAlongMetres + 1500.0, decRoute, decMap, decCal.alignmentId
        )
        assertEquals(KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE, decAhead.status)
        assertEquals(191_470L, decAhead.outputChainage.metres)
    }

    // 5 & 6. Exact ±2,000 m boundary and Outside ±2,000 m reversion
    @Test
    fun `scenarios 5 and 6 exact 2000m boundary stays active and outside 2km reverts cleanly to original KMZ`() {
        val route = createRoute()
        val cal = KmzCalibrationEngine.createCalibration(
            routeData = route,
            alignment = route.activeAlignment,
            originalKmzMetres = 192_200L,
            manualReferenceMetres = 192_230L
        )
        val map = mapOf(KmzCalibrationEngine.storageKey(cal.routeId, cal.alignmentId) to cal)

        // Exact +2000 m & -2000 m
        val exactPlus = KmzCalibrationEngine.evaluateAndApply(
            Chainage(194_200L), cal.anchorDistanceAlongMetres + 2000.0, route, map, cal.alignmentId
        )
        assertEquals(KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE, exactPlus.status)
        assertEquals(194_230L, exactPlus.outputChainage.metres)

        val exactMinus = KmzCalibrationEngine.evaluateAndApply(
            Chainage(190_200L), cal.anchorDistanceAlongMetres - 2000.0, route, map, cal.alignmentId
        )
        assertEquals(KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE, exactMinus.status)
        assertEquals(190_230L, exactMinus.outputChainage.metres)

        // Outside +2001 m, +2500 m, -2500 m, +4000 m -> reverts to original KMZ untouched
        for ((origM, distDelta) in listOf(194_201L to 2001.0, 194_700L to 2500.0, 189_700L to -2500.0, 196_200L to 4000.0)) {
            val outEval = KmzCalibrationEngine.evaluateAndApply(
                Chainage(origM), cal.anchorDistanceAlongMetres + distDelta, route, map, cal.alignmentId
            )
            assertEquals(KmzCalibrationCoverageStatus.OUTSIDE_2KM_RANGE, outEval.status)
            assertEquals(origM, outEval.outputChainage.metres)
            assertEquals(0L, outEval.appliedOffsetMetres)
        }
    }

    // 7. GPS drift, temporary GPS loss, off-route & jump spike never fabricate calibrated chainage
    @Test
    fun `scenario 7 GPS loss, off-route, and uncertain spike retain safe state without fabricating calibration`() {
        val route = createRoute()
        val tracker = ChainageProgressionTracker()
        val cal = KmzCalibrationEngine.createCalibration(
            routeData = route,
            alignment = route.activeAlignment,
            originalKmzMetres = 192_200L,
            manualReferenceMetres = 192_230L
        )
        val calMap = mapOf(KmzCalibrationEngine.storageKey(cal.routeId, cal.alignmentId) to cal)
        val t0 = 1_700_100_000_000L

        val kmzAt192200 = ChainageMatcher.match(
            pointAlongIncreasingRoute(188_000L, 192_200L),
            3.5f,
            routeData = route,
            fixTimeMs = t0
        )
        val evalAt192200 = KmzCalibrationEngine.evaluateAndApply(
            kmzAt192200.chainage!!, kmzAt192200.distanceAlongAlignmentMetres, route, calMap, kmzAt192200.matchedAlignmentId
        )
        val trackedValid = tracker.processResult(
            kmzAt192200.copy(
                chainage = evalAt192200.outputChainage,
                rawUncorrectedChainage = evalAt192200.rawKmzChainage,
                source = ChainageSource.CUSTOM_CORRECTION
            ),
            route.id
        )
        assertEquals(192_230L, trackedValid.chainage?.metres)

        // Sudden 3km implausible spike in 1s -> rejected & marked uncertain without changing offset
        val spikeKmz = ChainageMatcher.match(
            pointAlongIncreasingRoute(188_000L, 195_200L),
            12.0f,
            routeData = route,
            previousResult = trackedValid,
            fixTimeMs = t0 + 1000L
        )
        val afterSpike = tracker.processResult(spikeKmz, route.id)
        assertTrue(afterSpike.isUncertain)
        assertTrue(abs((afterSpike.chainage?.metres ?: 0L) - 192_230L) <= 5L)

        // Far off-route (>600m lateral) -> NO_MATCH, never fabricates a chainage
        val offRoute = ChainageMatcher.match(
            pointAlongIncreasingRoute(188_000L, 192_200L, lngOffset = 0.008),
            4.0f,
            routeData = route,
            previousResult = trackedValid,
            fixTimeMs = t0 + 5000L
        )
        assertEquals(MatchConfidence.NO_MATCH, offRoute.confidence)
    }

    // 8 & 9. Switching KMZ files / geometry fingerprint change & Parallel LHS/RHS alignment isolation
    @Test
    fun `scenarios 8 and 9 alignment switch, fingerprint mismatch, and parallel LHS RHS isolation prevent offset leakage`() {
        val routeA = createRoute(id = "route-a", fileName = "ProjectA.kmz", parallelSides = true)
        val routeB = createRoute(id = "route-b", fileName = "ProjectB.kmz", startMetres = 50_000L, endMetres = 60_000L)

        val lhsAlign = routeA.alignments.first { it.id == "align-lhs" }
        val rhsAlign = routeA.alignments.first { it.id == "align-rhs" }
        val lhsCal = KmzCalibrationEngine.createCalibration(routeA, lhsAlign, 192_000L, 192_040L)
        val calMap = mapOf(KmzCalibrationEngine.storageKey(lhsCal.routeId, lhsCal.alignmentId) to lhsCal)

        // Parallel RHS alignment must NOT inherit LHS +40m offset
        val rhsEval = KmzCalibrationEngine.evaluateAndApply(
            Chainage(192_000L), lhsCal.anchorDistanceAlongMetres, routeA, calMap, matchedAlignmentId = rhsAlign.id, matchedAlignmentName = rhsAlign.name
        )
        assertEquals(KmzCalibrationCoverageStatus.NOT_CALIBRATED, rhsEval.status)
        assertEquals(192_000L, rhsEval.outputChainage.metres)

        // Different KMZ route B must NOT inherit Route A offset
        val routeBEval = KmzCalibrationEngine.evaluateAndApply(
            Chainage(52_000L), 2000.0, routeB, calMap, matchedAlignmentId = "align-main"
        )
        assertEquals(KmzCalibrationCoverageStatus.NOT_CALIBRATED, routeBEval.status)
        assertEquals(52_000L, routeBEval.outputChainage.metres)

        // Modified KMZ file geometry with same routeId & alignmentId triggers ALIGNMENT_MISMATCH
        val modifiedRouteA = routeA.copy(fileName = "ProjectA_v2_reindexed.kmz")
        val modifiedEval = KmzCalibrationEngine.evaluateAndApply(
            Chainage(192_000L), lhsCal.anchorDistanceAlongMetres, modifiedRouteA, calMap, matchedAlignmentId = lhsAlign.id
        )
        assertEquals(KmzCalibrationCoverageStatus.ALIGNMENT_MISMATCH, modifiedEval.status)
        assertEquals(192_000L, modifiedEval.outputChainage.metres)
        assertEquals(0L, modifiedEval.appliedOffsetMetres)
    }

    // 10, 12, 13, 14 (Part A). MainViewModel end-to-end: manual LHS/RHS lock preservation,
    // video continuous timeline across ±2 km, cold-start persistence, non-cumulative replace & reset
    @Test
    fun `scenarios 10 12 13 14 ViewModel calibration preserves manual direction lock, updates video timeline across 2km, replaces without accumulation, resets, and persists across restart`() = runBlocking {
        val (kmlFile, _) = seedActiveKmlRouteInDbAndPrefs("route-e2e")
        val vm1 = MainViewModel(application)
        vm1.routeRepository.loadRouteById("route-e2e")
        delay(80)
        assertNotNull("Active KML route must load cleanly", vm1.activeRouteData.value)

        // Lock manual LHS direction BEFORE calibration
        vm1.updateSide(RoadSide.LHS)
        assertEquals(DirectionControlMode.MANUAL_OVERRIDE, vm1.smartDirectionPreferences.value.controlMode)
        assertEquals("LHS", vm1.smartDirectionPreferences.value.manualOverrideSide)

        // Push initial GPS at 192+200
        val t0 = 1_700_200_000_000L
        pushFreshGpsFix(
            vm1.locationTracker,
            lat = pointAlongIncreasingRoute(188_000L, 192_200L).latitude,
            lng = baseLng,
            fixTimeMs = t0
        )
        delay(80)

        // Apply +30m calibration (192+200 -> 192+230)
        var appliedSuccess = false
        vm1.applyManualKmzCalibration(
            manualReferenceInput = "192+230",
            originalKmzMetresOverride = 192_200L,
            gpsPointOverride = pointAlongIncreasingRoute(188_000L, 192_200L)
        ) { ok, _ -> appliedSuccess = ok }
        delay(60)
        assertTrue(appliedSuccess)
        assertEquals(30L, vm1.activeKmzCalibration.value?.offsetMetres)
        assertEquals(192_230L, vm1.liveMatchResult.value.chainage?.metres)
        assertEquals(ChainageSource.CUSTOM_CORRECTION, vm1.liveMatchResult.value.source)
        // Manual LHS lock must stay intact!
        assertEquals(DirectionControlMode.MANUAL_OVERRIDE, vm1.smartDirectionPreferences.value.controlMode)
        assertEquals("LHS", vm1.smartDirectionPreferences.value.manualOverrideSide)

        // Scenario 12: Start video recording and drive across 2km boundary (192+200 -> 194+500)
        vm1.onVideoRecordingStarted(nowMs = t0 + 1000L)
        delay(40)
        pushFreshGpsFix(
            vm1.locationTracker,
            lat = pointAlongIncreasingRoute(188_000L, 194_500L).latitude,
            lng = baseLng,
            fixTimeMs = t0 + 30_000L
        )
        delay(100)
        vm1.onVideoRecordingTick(5)
        vm1.onVideoRecordingStopped(nowMs = t0 + 31_000L)

        val videoSamples = vm1.getVideoChainageHistorySnapshot()
        assertTrue("Video timeline should contain samples", videoSamples.size >= 2)
        assertEquals(192_230L, videoSamples.first().chainageMetres)
        assertEquals(ChainageSource.CUSTOM_CORRECTION, videoSamples.first().source)
        // Outside 2km boundary (+2,300 m > 2,000 m), timeline sample falls back to original KMZ (194+500)
        val lastSample = videoSamples.last()
        assertEquals(ChainageSource.KMZ, lastSample.source)
        assertTrue(abs((lastSample.chainageMetres ?: 0L) - 194_500L) <= 10L)

        // Scenario 14: Replace calibration with 192+245 -> offset must be +45m, NEVER +75m!
        vm1.applyManualKmzCalibration(
            manualReferenceInput = "192+245",
            originalKmzMetresOverride = 192_200L,
            gpsPointOverride = pointAlongIncreasingRoute(188_000L, 192_200L)
        )
        delay(60)
        assertEquals(45L, vm1.activeKmzCalibration.value?.offsetMetres)
        PreferencesManager.awaitPendingWrites()

        // Scenario 13: Cold restart restores calibration & manual LHS lock cleanly
        PreferencesManager.clearMemoryCacheForProcessRestart()
        CtecsNavigationTelemetryHub.ensureInitialized(context, forceReload = true)
        val vm2 = MainViewModel(application)
        vm2.routeRepository.loadRouteById("route-e2e")
        delay(80)
        assertEquals(45L, vm2.activeKmzCalibration.value?.offsetMetres)
        assertEquals(192_245L, vm2.activeKmzCalibration.value?.manualReferenceMetres)
        assertEquals(DirectionControlMode.MANUAL_OVERRIDE, vm2.smartDirectionPreferences.value.controlMode)
        assertEquals("LHS", vm2.smartDirectionPreferences.value.manualOverrideSide)

        // Scenario 14: Reset restores original KMZ without deleting KMZ/KML file!
        vm2.resetManualKmzCalibration()
        delay(60)
        PreferencesManager.awaitPendingWrites()
        assertNull(vm2.activeKmzCalibration.value)
        assertTrue("Reset must NEVER delete KMZ/KML file", kmlFile.exists())
        pushFreshGpsFix(
            vm2.locationTracker,
            lat = pointAlongIncreasingRoute(188_000L, 192_200L).latitude,
            lng = baseLng,
            fixTimeMs = t0 + 40_000L
        )
        delay(80)
        assertEquals(ChainageSource.KMZ, vm2.liveMatchResult.value.source)
        assertTrue(abs((vm2.liveMatchResult.value.chainage?.metres ?: 0L) - 192_200L) <= 5L)
    }

    // 11. Photo capture metadata & EXIF preservation
    @Test
    fun `scenario 11 photo EXIF and PhotoRecordEntity store calibrated output while preserving original KMZ`() {
        val match = ChainageMatchResult(
            chainage = Chainage(192_230L),
            rawUncorrectedChainage = Chainage(192_200L),
            projectedPoint = GeoPoint(baseLat, baseLng),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 3.2f,
            detectedSide = "LHS",
            source = ChainageSource.CUSTOM_CORRECTION,
            manualCalibrationAnchorMetres = 192_230L,
            manualCalibrationOffsetMetres = 30L,
            calibrationValidationStatus = KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE.name
        )
        val snapshot = CaptureSnapshot(
            timestamp = 1_700_250_000_000L,
            location = GeoPoint(baseLat, baseLng),
            gpsAccuracy = 3.2f,
            matchResult = match,
            projectProfile = ProjectProfile(highwayId = "NH-58", roadSide = RoadSide.LHS)
        )
        val outPhoto = File(context.cacheDir, "calibrated_exif_test.jpg")
        outPhoto.delete()
        val bmp = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888)
        try {
            val saved = OverlayRenderer.saveBitmapWithExif(
                bitmap = bmp,
                destinationFile = outPhoto,
                snapshot = snapshot,
                preserveExif = true
            )
            assertTrue(saved)
            val exif = ExifInterface(outPhoto.absolutePath)
            val desc = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION).orEmpty()
            val comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT).orEmpty()
            assertTrue("Expected calibrated chainage in EXIF description: $desc", desc.contains("192+230"))
            assertTrue("Expected KMZ Calibrated summary in EXIF description: $desc", desc.contains("KMZ Calibrated"))
            assertTrue("Expected Ref & Offset & Orig in EXIF description: $desc",
                desc.contains("Ref: 192+230") && desc.contains("Offset: +30m") && desc.contains("Orig: 192+200"))
            assertTrue("Expected calibration info in UserComment: $comment", comment.contains("KMZ Calibrated"))
        } finally {
            bmp.recycle()
            outPhoto.delete()
        }

        val entity = PhotoRecordEntity(
            filePath = outPhoto.absolutePath,
            fileName = outPhoto.name,
            timestamp = 1_700_250_000_000L,
            latitude = baseLat,
            longitude = baseLng,
            accuracy = 3.2f,
            chainageMetres = match.chainage!!.metres,
            highwayId = "NH-58",
            packageSection = "Pkg-1",
            roadSide = "LHS",
            lane = "2L",
            activity = "BC"
        )
        assertEquals(192_230L, entity.chainageMetres)
    }

    // 14 (Part B). Compact Dialog UI Tests (Light & Dark themes, invalid input, Apply, Reset, GPS Capture, Outside 2km)
    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp-port")
    fun `ui dialog handles Light Dark themes, validation, Apply, Reset, GPS refresh, and Outside 2km status`() {
        val route = createRoute(parallelSides = true)
        var isDark by mutableStateOf(false)
        var activeCal by mutableStateOf<KmzManualCalibration?>(null)
        var currentDistAlong by mutableStateOf(4_200.0)
        var currentRawMetres by mutableStateOf(192_200L)
        var capturedGpsCount = 0
        var resetCount = 0

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = if (isDark) "dark" else "light") {
                val match = ChainageMatchResult(
                    chainage = Chainage(currentRawMetres),
                    rawUncorrectedChainage = Chainage(currentRawMetres),
                    projectedPoint = GeoPoint(baseLat, baseLng),
                    confidence = MatchConfidence.HIGH,
                    gpsAccuracy = 4.0f,
                    detectedSide = "LHS",
                    source = ChainageSource.KMZ,
                    matchedAlignmentId = "align-lhs",
                    matchedAlignmentName = "LHS Mainline",
                    distanceAlongAlignmentMetres = currentDistAlong
                )
                KmzCalibrationDialogContent(
                    matchResult = match,
                    activeRoute = route,
                    currentGpsPoint = GeoPoint(baseLat, baseLng),
                    currentGpsAccuracy = 4.0f,
                    activeCalibration = activeCal,
                    onCaptureGpsClick = { capturedGpsCount++ },
                    onApplyCalibration = { refInput, origM, _, alignId ->
                        val parsed = KmzCalibrationEngine.parseManualReferenceChainage(refInput).first!!
                        activeCal = KmzCalibrationEngine.createCalibration(
                            routeData = route,
                            alignment = route.alignments.firstOrNull { it.id == alignId },
                            originalKmzMetres = origM ?: currentRawMetres,
                            manualReferenceMetres = parsed.metres,
                            anchorDistanceAlongMetres = currentDistAlong
                        )
                    },
                    onResetCalibration = {
                        resetCount++
                        activeCal = null
                    },
                    onDismiss = {}
                )
            }
        }

        // Initial Light theme state: no calibration, Apply disabled on empty/invalid input
        composeTestRule.onNodeWithTag("calibration_alignment_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calibration_original_chainage_text").assertTextContains("192+200")
        composeTestRule.onNodeWithTag("calibration_coverage_status_text").assertTextContains("No calibration")
        composeTestRule.onNodeWithTag("calibration_apply_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("calibration_reset_button").assertIsNotEnabled()

        // Invalid negative input rejected cleanly
        composeTestRule.onNodeWithTag("calibration_manual_reference_input").performTextReplacement("-10+000")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calibration_apply_button").assertIsNotEnabled()

        // GPS Capture button works
        composeTestRule.onNodeWithTag("calibration_gps_capture_button").performClick()
        assertEquals(1, capturedGpsCount)

        // Enter valid 192+230 -> offset +30 m -> Apply Calibration
        composeTestRule.onNodeWithTag("calibration_manual_reference_input").performTextReplacement("192+230")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calibration_apply_button").assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("calibration_coverage_status_text")
            .assertTextContains("Active • within 2 km coverage")
        composeTestRule.onNodeWithTag("calibration_offset_text").assertTextContains("+30 m", substring = true)
        composeTestRule.onNodeWithTag("calibration_reset_button").assertIsEnabled()

        // Switch to Dark theme & move beyond ±2 km -> Outside 2 km range warning
        composeTestRule.runOnIdle {
            isDark = true
            currentDistAlong = 6_500.0
            currentRawMetres = 194_500L
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calibration_coverage_status_text").assertTextContains("Outside 2 km range")
        composeTestRule.onNodeWithTag("calibration_alignment_name").assertTextContains("LHS Mainline", substring = true)

        // Reset restores cleanly
        composeTestRule.onNodeWithTag("calibration_reset_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, resetCount)
        composeTestRule.onNodeWithTag("calibration_coverage_status_text").assertTextContains("No calibration")
    }

    // 15. Left / Right Parallel Centerline Offset Detection (straight, curved road, parallel divided carriageways)
    @Test
    fun `scenario 15 parallel centerline offset detects LEFT RIGHT ON_LINE OUTSIDE along straight and curved alignments`() {
        val route = createRoute(parallelSides = true)
        val align = route.alignments.first { it.id == "align-lhs" }

        // Alignment runs South -> North (bearing ~0°).
        // West of centerline (negative longitude delta) is LEFT (+signedLateralOffsetMetres);
        // East of centerline (positive longitude delta) is RIGHT (-signedLateralOffsetMetres).
        val centerPt = pointAlongIncreasingRoute(188_000L, 192_000L)
        val cosLat = kotlin.math.cos(Math.toRadians(baseLat))
        val degLngPerMetre = 1.0 / (111_320.0 * cosLat)

        // 1) Near centerline (0.3 m East -> ON_LINE)
        val onLinePt = GeoPoint(centerPt.latitude, centerPt.longitude + 0.3 * degLngPerMetre)
        val onLineRes = ChainageMatcher.computeParallelCenterlineOffset(onLinePt, align, route.markers)
        assertEquals(com.example.model.CenterlineOffsetZone.ON_LINE, onLineRes.zone)
        assertEquals("ON LINE", onLineRes.formattedBadge)

        // 2) Left of centerline (3.0 m, 10.0 m, 20.0 m West -> LEFT)
        for (latDistM in listOf(3.0, 10.0, 20.0)) {
            val leftPt = GeoPoint(centerPt.latitude, centerPt.longitude - latDistM * degLngPerMetre)
            val leftRes = ChainageMatcher.computeParallelCenterlineOffset(leftPt, align, route.markers)
            assertEquals(com.example.model.CenterlineOffsetZone.LEFT, leftRes.zone)
            assertTrue("Signed lateral offset should be positive for LEFT: ${leftRes.signedLateralOffsetMetres}",
                leftRes.signedLateralOffsetMetres > 0.0)
            assertEquals(latDistM, leftRes.perpendicularDistanceMetres, 0.35)
            assertTrue(leftRes.formattedBadge.startsWith("L "))
        }

        // 3) Right of centerline (3.0 m, 10.0 m, 20.0 m East -> RIGHT)
        for (latDistM in listOf(3.0, 10.0, 20.0)) {
            val rightPt = GeoPoint(centerPt.latitude, centerPt.longitude + latDistM * degLngPerMetre)
            val rightRes = ChainageMatcher.computeParallelCenterlineOffset(rightPt, align, route.markers)
            assertEquals(com.example.model.CenterlineOffsetZone.RIGHT, rightRes.zone)
            assertTrue("Signed lateral offset should be negative for RIGHT: ${rightRes.signedLateralOffsetMetres}",
                rightRes.signedLateralOffsetMetres < 0.0)
            assertEquals(latDistM, rightRes.perpendicularDistanceMetres, 0.35)
            assertTrue(rightRes.formattedBadge.startsWith("R "))
        }

        // 4) Outside reliable alignment matching range (500 m East -> OUTSIDE)
        val outsidePt = GeoPoint(centerPt.latitude, centerPt.longitude + 500.0 * degLngPerMetre)
        val outsideRes = ChainageMatcher.computeParallelCenterlineOffset(outsidePt, align, route.markers)
        assertEquals(com.example.model.CenterlineOffsetZone.OUTSIDE, outsideRes.zone)
        assertEquals("OUTSIDE", outsideRes.formattedBadge)

        // 5) Curved alignment (90-degree turn from Northbound to Eastbound)
        val curvePoints = listOf(
            GeoPoint(baseLat, baseLng),
            GeoPoint(baseLat + 500.0 * degPerMetre, baseLng),
            GeoPoint(baseLat + 500.0 * degPerMetre, baseLng + 500.0 * degLngPerMetre)
        )
        val curvedAlign = RouteAlignment(
            id = "align-curve",
            name = "Curved Highway",
            points = curvePoints,
            direction = "INCREASING"
        )
        // On the Eastbound leg (after the turn), North (+latitude) is LEFT and South (-latitude) is RIGHT!
        val eastboundMid = GeoPoint(baseLat + 500.0 * degPerMetre, baseLng + 250.0 * degLngPerMetre)
        val northOfEastbound = GeoPoint(eastboundMid.latitude + 6.0 * degPerMetre, eastboundMid.longitude)
        val southOfEastbound = GeoPoint(eastboundMid.latitude - 6.5 * degPerMetre, eastboundMid.longitude)

        val curveLeft = ChainageMatcher.computeParallelCenterlineOffset(northOfEastbound, curvedAlign)
        val curveRight = ChainageMatcher.computeParallelCenterlineOffset(southOfEastbound, curvedAlign)
        assertEquals(com.example.model.CenterlineOffsetZone.LEFT, curveLeft.zone)
        assertEquals(6.0, curveLeft.perpendicularDistanceMetres, 0.4)
        assertEquals(com.example.model.CenterlineOffsetZone.RIGHT, curveRight.zone)
        assertEquals(6.5, curveRight.perpendicularDistanceMetres, 0.4)
    }

    // 16. Smart Calibration Suggestion Engine (GPS ±3m, ±5m, ±8m, ±12m & lateral offsets 3m, 10m, 20m, >20m)
    @Test
    fun `scenario 16 smart suggestion engine enforces 5m GPS accuracy, 20m lateral offset, stability, and never alters chainage by lateral offset`() {
        val route = createRoute(parallelSides = true).copy(selectedAlignmentId = "align-lhs")
        val cosLat = kotlin.math.cos(Math.toRadians(baseLat))
        val degLngPerMetre = 1.0 / (111_320.0 * cosLat)
        val basePt = pointAlongIncreasingRoute(188_000L, 192_200L)

        fun buildMatch(lateralOffsetM: Double, gpsAcc: Float): Pair<GeoPoint, ChainageMatchResult> {
            val pt = GeoPoint(basePt.latitude, basePt.longitude + lateralOffsetM * degLngPerMetre)
            val match = ChainageMatcher.match(
                gpsPoint = pt,
                gpsAccuracy = gpsAcc,
                routeData = route,
                fixTimeMs = 1_700_300_000_000L
            )
            return pt to match
        }

        // Eligible: GPS ±3m and ±5m with lateral offsets 3m, 10m, 20m (both Left and Right)
        for (gpsAcc in listOf(3.0f, 5.0f)) {
            for (latM in listOf(-3.0, 3.0, -10.0, 10.0, -19.8, 19.8)) {
                val (pt, match) = buildMatch(latM, gpsAcc)
                // Lateral offset must NEVER be added to or subtracted from chainage
                assertEquals(192_200L, match.chainage?.metres)
                val suggestion = KmzCalibrationEngine.evaluateCalibrationSuggestion(
                    routeData = route,
                    matchResult = match,
                    gpsPoint = pt,
                    gpsAccuracyMetres = gpsAcc,
                    calibrations = emptyMap(),
                    stableSampleCount = 2,
                    requiredStableSamples = 2
                )
                assertTrue("Expected eligible suggestion for gps=$gpsAcc, lat=$latM but got ${suggestion.status}: ${suggestion.guidanceMessage}",
                    suggestion.isEligible)
                assertEquals(com.example.model.KmzCalibrationSuggestionStatus.ELIGIBLE_SUGGEST_CALIBRATION, suggestion.status)
                assertEquals("Suggest calibration", suggestion.status.badgeLabel)
            }
        }

        // Poor GPS: ±8m and ±12m -> WAIT_POOR_GPS_ACCURACY
        for (gpsAcc in listOf(8.0f, 12.0f)) {
            val (pt, match) = buildMatch(-6.0, gpsAcc)
            val suggestion = KmzCalibrationEngine.evaluateCalibrationSuggestion(
                routeData = route,
                matchResult = match,
                gpsPoint = pt,
                gpsAccuracyMetres = gpsAcc,
                calibrations = emptyMap(),
                stableSampleCount = 3,
                requiredStableSamples = 2
            )
            assertFalse(suggestion.isEligible)
            assertEquals(com.example.model.KmzCalibrationSuggestionStatus.WAIT_POOR_GPS_ACCURACY, suggestion.status)
            assertEquals("Wait", suggestion.status.badgeLabel)
        }

        // Excessive offset: >20m (e.g. 24m Right) with good GPS ±4m -> RECHECK_EXCESSIVE_LATERAL_OFFSET
        val (excPt, excessiveMatch) = buildMatch(24.0, 4.0f)
        val excessiveSuggestion = KmzCalibrationEngine.evaluateCalibrationSuggestion(
            routeData = route,
            matchResult = excessiveMatch,
            gpsPoint = excPt,
            gpsAccuracyMetres = 4.0f,
            calibrations = emptyMap(),
            stableSampleCount = 3,
            requiredStableSamples = 2
        )
        assertFalse(excessiveSuggestion.isEligible)
        assertEquals(com.example.model.KmzCalibrationSuggestionStatus.RECHECK_EXCESSIVE_LATERAL_OFFSET, excessiveSuggestion.status)
        assertEquals("Recheck alignment", excessiveSuggestion.status.badgeLabel)
    }

    // 17. Smart Calibration Chainage List: Add, Edit (with history preservation), Duplicate prevention, and Delete
    @Test
    fun `scenario 17 smart calibration list supports multiple references, duplicate prevention, edit with history preservation, and delete without touching KMZ`() = runBlocking {
        val (kmlFile, _) = seedActiveKmlRouteInDbAndPrefs("route-list-test")
        val vm = MainViewModel(application)
        vm.routeRepository.loadRouteById("route-list-test")
        delay(80)

        // 1. Add first reference at 190+000 -> 190+025 (+25 m)
        val pt190 = pointAlongIncreasingRoute(188_000L, 190_000L)
        var firstOk = false
        vm.applyManualKmzCalibration(
            manualReferenceInput = "190+025",
            originalKmzMetresOverride = 190_000L,
            gpsPointOverride = pt190
        ) { ok, _ -> firstOk = ok }
        delay(60)
        assertTrue(firstOk)
        assertEquals(1, vm.routeKmzCalibrationsList.value.size)

        // 2. Duplicate reference prevention: identical manual & original chainage is rejected
        var dupOk = true
        var dupMsg = ""
        vm.applyManualKmzCalibration(
            manualReferenceInput = "190+025",
            originalKmzMetresOverride = 190_000L,
            gpsPointOverride = pt190
        ) { ok, msg ->
            dupOk = ok
            dupMsg = msg
        }
        delay(40)
        assertFalse("Duplicate calibration reference should be prevented", dupOk)
        assertTrue(dupMsg.contains("Duplicate", ignoreCase = true))
        assertEquals(1, vm.routeKmzCalibrationsList.value.size)

        // 3. Add a second reference further along alignment at 195+000 -> 195+040 (+40 m)
        val pt195 = pointAlongIncreasingRoute(188_000L, 195_000L)
        var secondOk = false
        vm.applyManualKmzCalibration(
            manualReferenceInput = "195+040",
            originalKmzMetresOverride = 195_000L,
            gpsPointOverride = pt195
        ) { ok, _ -> secondOk = ok }
        delay(60)
        assertTrue(secondOk)
        assertEquals(2, vm.routeKmzCalibrationsList.value.size)

        // 4. Edit first reference from 190+025 to 190+035 (+35 m) -> preserves history & recalculates offset
        val firstRecord = vm.routeKmzCalibrationsList.value.first { it.originalKmzMetres == 190_000L }
        var editOk = false
        vm.editManualKmzCalibration(
            calibrationId = firstRecord.id,
            newManualReferenceInput = "190+035",
            newOriginalKmzInput = "190+000",
            newGpsPointOverride = pt190
        ) { ok, _ -> editOk = ok }
        delay(60)
        assertTrue(editOk)
        val editedRecord = vm.routeKmzCalibrationsList.value.first { it.id == firstRecord.id }
        assertEquals(190_035L, editedRecord.manualReferenceMetres)
        assertEquals(35L, editedRecord.offsetMetres)
        assertEquals(1, editedRecord.editHistory.size)
        assertEquals(190_025L, editedRecord.editHistory.first().previousManualReferenceMetres)
        assertEquals(25L, editedRecord.editHistory.first().previousOffsetMetres)

        // 5. Delete first reference -> deletes ONLY the selected record, keeps second reference & KMZ file intact
        var deleteOk = false
        vm.deleteManualKmzCalibration(firstRecord.id) { ok, _ -> deleteOk = ok }
        delay(60)
        assertTrue(deleteOk)
        assertEquals(1, vm.routeKmzCalibrationsList.value.size)
        assertEquals(195_000L, vm.routeKmzCalibrationsList.value.single().originalKmzMetres)
        assertTrue("Deleting calibration record must never delete KMZ/KML file", kmlFile.exists())
    }

    // 18. Fixed-point integer millimetre accuracy & exclusive Home Page Chainage Card tiny "mm" indicator
    @Test
    fun `scenario 18 millimetre fixed-point engine has zero drift and mm indicator appears ONLY on Home Page Chainage Card`() {
        // 1. Verify fixed-point millimetre arithmetic
        assertEquals(192_230_000L, Chainage.metresToMillimetres(192_230.0))
        assertEquals(192_230L, Chainage.fromMillimetres(192_230_400L).metres)
        assertEquals(192_231L, Chainage.fromMillimetres(192_230_600L).metres)

        // 2. Verify Chainage.format() and ChainageMatchResult.displayChainage NEVER append "mm"
        val ch = Chainage(196_040L)
        assertEquals("196+040", ch.format())
        assertFalse(ch.format().contains("mm", ignoreCase = true))

        val match = ChainageMatchResult(
            chainage = ch,
            projectedPoint = GeoPoint(baseLat, baseLng),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 3.0f,
            detectedSide = "LHS",
            source = ChainageSource.KMZ
        )
        assertEquals("196+040", match.displayChainage)
        assertFalse(match.displayChainage.contains("mm", ignoreCase = true))

        // 3. Render CentralChainageDisplay (Home Page Chainage Card) and verify tiny "mm" indicator exists inside it
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                val c = com.example.ui.theme.CtecsTheme.colors
                com.example.ui.home.CentralChainageDisplay(
                    chainageText = "196+040",
                    detectedSide = "LHS",
                    confidence = MatchConfidence.HIGH,
                    offsetMetres = 2.4,
                    hasActiveRoute = true,
                    source = ChainageSource.KMZ,
                    cardBgColor = c.secondaryBackground,
                    cardBorderColor = c.subtleSeparator,
                    mainTextColor = c.textPrimary,
                    subTextColor = c.textSecondary,
                    isDark = true,
                    onClick = {},
                    onToggleSide = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("central_chainage_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_chainage_card_mm_indicator", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("mm")
    }
}

