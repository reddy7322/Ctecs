package com.example

import android.app.Application
import android.graphics.Bitmap
import android.media.ExifInterface
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.geo.ChainageMatcher
import com.example.geo.ChainageProgressionTracker
import com.example.geo.GeoUtils
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.CameraMode
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.CoordinateFormat
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.ManualChainageMode
import com.example.model.MatchConfidence
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.TravelChainageDirection
import com.example.overlay.OverlayRenderer
import com.example.overlay.OverlayTextGenerator
import com.example.overlay.VideoOverlayProcessor
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraScreen
import com.example.ui.camera.resolveChainageIndicatorsState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Comprehensive Quantitative Validation & 14-Scenario Regression Test Suite for:
 * - Camera Manual Chainage (Mode 1: Continue Stable Chainage & Mode 2: Dynamic Chainage Correction)
 * - Centralized ChainageMatcher + ChainageProgressionTracker accuracy enhancements
 * - Baseline vs Enhanced quantitative error comparison
 * - Photo EXIF/overlay integration, Continuous Video Chainage (incl. pause/resume & reverse),
 *   app restart restoration, and Camera Manual Chainage UI interactions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CameraManualChainageAndAccuracyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        ChainageMatcher.clearCache()
    }

    companion object {
        private const val BASE_LAT = 17.400000
        private const val BASE_LNG = 78.400000
        private const val METRES_PER_DEG_LAT = 111_132.0
    }

    private fun pointNorthEast(northMetres: Double, eastMetres: Double = 0.0): GeoPoint {
        val lat = BASE_LAT + northMetres / METRES_PER_DEG_LAT
        val lng = BASE_LNG + eastMetres / (METRES_PER_DEG_LAT * cos(Math.toRadians(BASE_LAT)))
        return GeoPoint(lat, lng)
    }

    private fun matchFix(
        point: GeoPoint,
        route: RouteData,
        accuracy: Float,
        bearing: Float?,
        previous: ChainageMatchResult? = null,
        fixTimeMs: Long
    ): ChainageMatchResult = ChainageMatcher.match(
        gpsPoint = point,
        gpsAccuracy = accuracy,
        bearing = bearing,
        routeData = route,
        previousResult = previous,
        fixTimeMs = fixTimeMs
    )

    private fun boundsOf(points: List<GeoPoint>): GeoBounds {
        return GeoBounds(
            minLat = points.minOf { it.latitude },
            maxLat = points.maxOf { it.latitude },
            minLng = points.minOf { it.longitude },
            maxLng = points.maxOf { it.longitude }
        )
    }

    private fun buildStraightRoute(
        id: String = "route_straight",
        startMetres: Long = 174_000L,
        lengthMetres: Int = 1_000,
        stepMetres: Int = 50,
        decreasing: Boolean = false
    ): RouteData {
        val points = mutableListOf<GeoPoint>()
        val markers = mutableListOf<RouteMarker>()
        var d = 0
        while (d <= lengthMetres) {
            val pt = pointNorthEast(northMetres = d.toDouble())
            points.add(pt)
            val ch = if (decreasing) startMetres - d.toLong() else startMetres + d.toLong()
            val label = Chainage(ch).format()
            markers.add(
                RouteMarker(
                    id = "${id}_m_$d",
                    label = label,
                    chainage = Chainage(ch),
                    point = pt
                )
            )
            d += stepMetres
        }
        val track = RouteAlignment(
            id = "${id}_align",
            name = "Main Highway Alignment",
            points = points,
            direction = if (decreasing) "DECREASING" else "INCREASING",
            carriagewayType = "MAIN"
        )
        val endCh = Chainage(if (decreasing) startMetres - lengthMetres else startMetres + lengthMetres)
        return RouteData(
            id = id,
            name = id,
            fileName = "$id.kmz",
            markers = markers,
            alignments = listOf(track),
            bounds = boundsOf(points),
            startChainage = Chainage(startMetres),
            endChainage = endCh
        )
    }

    private fun buildNonUniformRoute(): RouteData {
        val distances = listOf(0, 37, 215, 290, 500, 742, 1000)
        val points = distances.map { pointNorthEast(northMetres = it.toDouble()) }
        val markers = distances.map { d ->
            val ch = Chainage(100_000L + d.toLong())
            RouteMarker(
                id = "nu_$d",
                label = ch.format(),
                chainage = ch,
                point = pointNorthEast(northMetres = d.toDouble())
            )
        }
        val track = RouteAlignment(
            id = "nu_align",
            name = "Non-Uniform NH Corridor",
            points = points,
            direction = "INCREASING",
            carriagewayType = "MAIN"
        )
        return RouteData(
            id = "route_non_uniform",
            name = "Non-Uniform Route",
            fileName = "non_uniform.kmz",
            markers = markers,
            alignments = listOf(track),
            bounds = boundsOf(points),
            startChainage = Chainage(100_000L),
            endChainage = Chainage(101_000L)
        )
    }

    private fun buildHairpinCurveRoute(): RouteData {
        val pts = mutableListOf<GeoPoint>()
        val markers = mutableListOf<RouteMarker>()
        var cumDist = 0.0
        var prevPt: GeoPoint? = null
        var idx = 0

        fun addPt(pt: GeoPoint) {
            prevPt?.let { cumDist += GeoUtils.distanceMetres(it, pt) }
            pts.add(pt)
            val ch = Chainage(150_000L + cumDist.toLong())
            markers.add(RouteMarker(id = "hp_${idx++}", label = ch.format(), chainage = ch, point = pt))
            prevPt = pt
        }

        for (y in 0..200 step 20) addPt(pointNorthEast(y.toDouble(), 0.0))
        for (deg in 160 downTo 20 step 20) {
            val rad = Math.toRadians(deg.toDouble())
            val east = 14.0 + 14.0 * cos(rad)
            val north = 200.0 + 14.0 * sin(rad)
            addPt(pointNorthEast(north, east))
        }
        for (y in 200 downTo 0 step 20) addPt(pointNorthEast(y.toDouble(), 28.0))

        val track = RouteAlignment(
            id = "hp_align",
            name = "Hairpin Bend Alignment",
            points = pts,
            direction = "INCREASING",
            carriagewayType = "MAIN"
        )
        return RouteData(
            id = "route_hairpin",
            name = "Hairpin Bend",
            fileName = "hairpin.kmz",
            markers = markers,
            alignments = listOf(track),
            bounds = boundsOf(pts),
            startChainage = Chainage(150_000L),
            endChainage = Chainage(150_000L + cumDist.toLong())
        )
    }

    private fun buildDividedCarriagewayRoute(): RouteData {
        val lhsPts = (0..600 step 50).map { pointNorthEast(it.toDouble(), eastMetres = -8.0) }
        val rhsPts = (0..600 step 50).map { pointNorthEast(it.toDouble(), eastMetres = 8.0) }
        val lhsMarkers = (0..600 step 50).map { d ->
            val ch = Chainage(120_000L + d)
            RouteMarker(id = "lhs_$d", label = "LHS ${ch.format()}", chainage = ch, point = pointNorthEast(d.toDouble(), -8.0))
        }
        val rhsMarkers = (0..600 step 50).map { d ->
            val ch = Chainage(120_000L + d)
            RouteMarker(id = "rhs_$d", label = "RHS ${ch.format()}", chainage = ch, point = pointNorthEast(d.toDouble(), 8.0))
        }
        return RouteData(
            id = "route_divided",
            name = "Divided Carriageway",
            fileName = "divided.kmz",
            markers = lhsMarkers + rhsMarkers,
            alignments = listOf(
                RouteAlignment("lhs_align", "LHS Carriageway", lhsPts, direction = "INCREASING", carriagewayType = "LHS"),
                RouteAlignment("rhs_align", "RHS Carriageway", rhsPts, direction = "INCREASING", carriagewayType = "RHS")
            ),
            bounds = boundsOf(lhsPts + rhsPts),
            startChainage = Chainage(120_000L),
            endChainage = Chainage(120_600L)
        )
    }

    private fun makeLocationState(
        point: GeoPoint,
        accuracy: Float = 5.0f,
        speed: Float = 10.0f,
        bearing: Float = 0.0f,
        timeMs: Long
    ): LocationState {
        return LocationState(
            point = point,
            accuracy = accuracy,
            speed = speed,
            bearing = bearing,
            status = LocationStatus.READY,
            lastFixTime = timeMs
        )
    }

    private fun computeRmse(errors: List<Double>): Double {
        if (errors.isEmpty()) return 0.0
        val meanSq = errors.sumOf { it * it } / errors.size.toDouble()
        return sqrt(meanSq)
    }

    // -------------------------------------------------------------------------
    // QUANTITATIVE BASELINE vs ENHANCED COMPARISON
    // -------------------------------------------------------------------------
    @Test
    fun `quantitative benchmark proves enhanced engine outperforms baseline across offset, spikes, and reverse travel`() {
        val route = buildStraightRoute(startMetres = 174_000L, lengthMetres = 1_000)
        val tracker = ChainageProgressionTracker()

        val trueOffset = 35L
        val baseTime = 1_700_000_000_000L

        data class Step(val trueNorthM: Double, val gpsNorthM: Double, val acc: Float, val bearing: Float, val speed: Float)
        val drive = listOf(
            Step(200.0, 200.0, 4.5f, 0f, 10f),
            Step(240.0, 240.0, 4.5f, 0f, 10f),
            Step(280.0, 280.0, 5.0f, 0f, 10f),
            Step(320.0, 320.0, 4.8f, 0f, 10f),
            Step(360.0, 360.0, 5.2f, 0f, 10f),
            Step(400.0, 400.0, 4.5f, 0f, 10f),
            Step(420.0, 640.0, 9.0f, 0f, 10f),   // Single implausible +240m GPS jump spike in 1s!
            Step(440.0, 440.0, 4.5f, 0f, 10f),
            Step(480.0, 480.0, 4.5f, 0f, 10f),
            Step(500.0, 500.0, 4.5f, 0f, 10f),
            Step(500.0, 585.0, 58.0f, 0f, 0f),   // Poor GPS accuracy (>35m) drift
            Step(470.0, 470.0, 4.5f, 180f, 8f),  // Reverse travel
            Step(430.0, 430.0, 4.5f, 180f, 8f),
            Step(390.0, 390.0, 4.5f, 180f, 8f),
            Step(350.0, 350.0, 4.5f, 180f, 8f)
        )

        val firstPt = pointNorthEast(drive[0].gpsNorthM)
        val firstLoc = makeLocationState(firstPt, drive[0].acc, drive[0].speed, drive[0].bearing, baseTime)
        val firstAuto = matchFix(firstPt, route, drive[0].acc, drive[0].bearing, null, baseTime)
        var cal = tracker.establishManualCalibration(
            enteredChainage = Chainage(174_200L + trueOffset),
            mode = ManualChainageMode.DYNAMIC_CORRECTION,
            currentAutoResult = firstAuto,
            locationState = firstLoc,
            routeId = route.id,
            nowMs = baseTime
        )

        val baselineFrozenErrors = mutableListOf<Double>()
        val baselineRawKmzErrors = mutableListOf<Double>()
        val enhancedDynamicErrors = mutableListOf<Double>()

        var prevAuto: ChainageMatchResult? = firstAuto
        drive.forEachIndexed { idx, step ->
            val ts = baseTime + idx * 1_000L
            val pt = pointNorthEast(step.gpsNorthM)
            val loc = makeLocationState(pt, step.acc, step.speed, step.bearing, ts)
            val auto = matchFix(pt, route, step.acc, step.bearing, prevAuto, ts)
            if (!auto.isUncertain && step.acc <= 35f) prevAuto = auto
            val resolved = tracker.processWithManualCalibration(auto, loc, route, cal, nowMs = ts)
            cal = resolved.updatedCalibration

            val groundTruth = 174_000L + step.trueNorthM.toLong() + trueOffset
            val baselineFrozenPred = 174_200L + trueOffset
            val baselineRawKmzPred = (auto.rawUncorrectedChainage ?: auto.chainage)?.metres ?: groundTruth
            val enhancedPred = resolved.matchResult.chainage!!.metres

            baselineFrozenErrors.add(abs(baselineFrozenPred - groundTruth).toDouble())
            baselineRawKmzErrors.add(abs(baselineRawKmzPred - groundTruth).toDouble())
            enhancedDynamicErrors.add(abs(enhancedPred - groundTruth).toDouble())
        }

        val frozenRmse = computeRmse(baselineFrozenErrors)
        val rawKmzRmse = computeRmse(baselineRawKmzErrors)
        val enhancedRmse = computeRmse(enhancedDynamicErrors)
        val enhancedMaxError = enhancedDynamicErrors.maxOrNull() ?: 0.0

        assertTrue("Expected frozen baseline RMSE > 100m, got $frozenRmse", frozenRmse > 100.0)
        assertTrue("Expected raw KMZ RMSE > 30m, got $rawKmzRmse", rawKmzRmse > 30.0)
        assertTrue("Expected enhanced RMSE < 8m, got $enhancedRmse", enhancedRmse < 8.0)
        assertTrue("Expected enhanced max error <= 22m, got $enhancedMaxError", enhancedMaxError <= 22.0)
        assertEquals("Final reverse chainage must match ground truth within 2m", 174_385.0, cal.currentChainageMetres!!.toDouble(), 2.0)
    }

    // -------------------------------------------------------------------------
    // SCENARIOS 1, 2, 3: Forward, Reverse, Forward -> Stop -> Reverse -> Forward
    // -------------------------------------------------------------------------
    @Test
    fun `scenarios 1 to 3 - forward, reverse, and stop-reverse-forward transitions in Stable and Dynamic modes`() {
        val route = buildStraightRoute(startMetres = 174_000L, lengthMetres = 1_000)
        val baseTime = 1_700_001_000_000L

        for (mode in listOf(ManualChainageMode.CONTINUE_STABLE, ManualChainageMode.DYNAMIC_CORRECTION)) {
            ChainageMatcher.clearCache()
            val tracker = ChainageProgressionTracker()

            val startPt = pointNorthEast(200.0)
            val startLoc = makeLocationState(startPt, 4.0f, 8.0f, 0f, baseTime)
            val startAuto = matchFix(startPt, route, 4.0f, 0f, null, baseTime)

            var cal = tracker.establishManualCalibration(
                enteredChainage = Chainage(174_250L),
                mode = mode,
                currentAutoResult = startAuto,
                locationState = startLoc,
                routeId = route.id,
                nowMs = baseTime
            )

            var prevAuto = startAuto
            var lastRes = tracker.processWithManualCalibration(startAuto, startLoc, route, cal, baseTime).also {
                cal = it.updatedCalibration
            }.matchResult
            assertEquals(174_250L, lastRes.chainage?.metres)

            // Phase 1: Forward driving (200m -> 320m)
            var stepIdx = 1
            for (north in listOf(220.0, 240.0, 260.0, 280.0, 300.0, 320.0)) {
                val ts = baseTime + stepIdx++ * 2_000L
                val pt = pointNorthEast(north)
                val loc = makeLocationState(pt, 4.0f, 10.0f, 0f, ts)
                val auto = matchFix(pt, route, 4.0f, 0f, prevAuto, ts)
                prevAuto = auto
                val out = tracker.processWithManualCalibration(auto, loc, route, cal, ts)
                cal = out.updatedCalibration
                lastRes = out.matchResult
            }
            assertEquals("Mode $mode forward end chainage", 174_370.0, lastRes.chainage!!.metres.toDouble(), 2.0)
            assertEquals("Mode $mode forward direction", TravelChainageDirection.FORWARD, lastRes.travelDirection)

            // Phase 2: Stop (3 stationary fixes with tiny 0.5m GPS jitter)
            for (jitter in listOf(0.4, -0.5, 0.3)) {
                val ts = baseTime + stepIdx++ * 2_000L
                val pt = pointNorthEast(320.0 + jitter)
                val loc = makeLocationState(pt, 4.5f, 0.0f, 0f, ts)
                val auto = matchFix(pt, route, 4.5f, 0f, prevAuto, ts)
                prevAuto = auto
                val out = tracker.processWithManualCalibration(auto, loc, route, cal, ts)
                cal = out.updatedCalibration
                lastRes = out.matchResult
            }
            assertEquals("Mode $mode stationary hold", 174_370.0, lastRes.chainage!!.metres.toDouble(), 2.0)
            assertEquals("Mode $mode stationary direction", TravelChainageDirection.STATIONARY, lastRes.travelDirection)

            // Phase 3: Reverse driving (320m -> 240m)
            for (north in listOf(300.0, 280.0, 260.0, 240.0)) {
                val ts = baseTime + stepIdx++ * 2_000L
                val pt = pointNorthEast(north)
                val loc = makeLocationState(pt, 4.0f, 10.0f, 180f, ts)
                val auto = matchFix(pt, route, 4.0f, 180f, prevAuto, ts)
                prevAuto = auto
                val out = tracker.processWithManualCalibration(auto, loc, route, cal, ts)
                cal = out.updatedCalibration
                lastRes = out.matchResult
            }
            assertEquals("Mode $mode reverse end chainage", 174_290.0, lastRes.chainage!!.metres.toDouble(), 2.0)
            assertEquals("Mode $mode reverse direction", TravelChainageDirection.REVERSE, lastRes.travelDirection)

            // Phase 4: Forward again (240m -> 300m)
            for (north in listOf(260.0, 280.0, 300.0)) {
                val ts = baseTime + stepIdx++ * 2_000L
                val pt = pointNorthEast(north)
                val loc = makeLocationState(pt, 4.0f, 10.0f, 0f, ts)
                val auto = matchFix(pt, route, 4.0f, 0f, prevAuto, ts)
                prevAuto = auto
                val out = tracker.processWithManualCalibration(auto, loc, route, cal, ts)
                cal = out.updatedCalibration
                lastRes = out.matchResult
            }
            assertEquals("Mode $mode resumed forward chainage", 174_350.0, lastRes.chainage!!.metres.toDouble(), 2.0)
            assertEquals("Mode $mode resumed forward direction", TravelChainageDirection.FORWARD, lastRes.travelDirection)
        }
    }

    // -------------------------------------------------------------------------
    // SCENARIOS 4 & 5: GPS Drift, Jump Spikes, Poor Accuracy & Signal Loss
    // -------------------------------------------------------------------------
    @Test
    fun `scenarios 4 and 5 - GPS drift, jump spikes, poor accuracy, and temporary signal loss hold reliable chainage`() {
        val route = buildStraightRoute(startMetres = 174_000L, lengthMetres = 1_000)
        val tracker = ChainageProgressionTracker()
        val baseTime = 1_700_002_000_000L

        val p0 = pointNorthEast(100.0)
        val l0 = makeLocationState(p0, 4.0f, 10.0f, 0f, baseTime)
        val a0 = matchFix(p0, route, 4.0f, 0f, null, baseTime)
        var cal = tracker.establishManualCalibration(
            enteredChainage = Chainage(174_250L),
            mode = ManualChainageMode.CONTINUE_STABLE,
            currentAutoResult = a0,
            locationState = l0,
            routeId = route.id,
            nowMs = baseTime
        )

        // Move forward 50m -> 174+300
        val p1 = pointNorthEast(150.0)
        val l1 = makeLocationState(p1, 4.5f, 10.0f, 0f, baseTime + 5_000L)
        val a1 = matchFix(p1, route, 4.5f, 0f, a0, baseTime + 5_000L)
        val r1 = tracker.processWithManualCalibration(a1, l1, route, cal, baseTime + 5_000L)
        cal = r1.updatedCalibration
        assertEquals(174_300.0, r1.matchResult.chainage!!.metres.toDouble(), 2.0)

        // 1. Sudden severe GPS jump spike (+200m in 1s) -> MUST be rejected, holding 174+300
        val pSpike = pointNorthEast(350.0)
        val lSpike = makeLocationState(pSpike, 6.0f, 10.0f, 0f, baseTime + 6_000L)
        val aSpike = matchFix(pSpike, route, 6.0f, 0f, a1, baseTime + 6_000L)
        val rSpike = tracker.processWithManualCalibration(aSpike, lSpike, route, cal, baseTime + 6_000L)
        assertEquals(174_300.0, rSpike.matchResult.chainage!!.metres.toDouble(), 2.0)
        assertTrue(rSpike.matchResult.isUncertain)

        // 2. Poor GPS accuracy (55m > 35m limit) -> MUST retain 174+300 without jumping
        val pPoor = pointNorthEast(230.0)
        val lPoor = makeLocationState(pPoor, 55.0f, 5.0f, 0f, baseTime + 8_000L)
        val aPoor = matchFix(pPoor, route, 55.0f, 0f, a1, baseTime + 8_000L)
        val rPoor = tracker.processWithManualCalibration(aPoor, lPoor, route, cal, baseTime + 8_000L)
        assertEquals(174_300.0, rPoor.matchResult.chainage!!.metres.toDouble(), 2.0)

        // 3. Complete GPS signal loss (point = null) -> MUST retain 174+300
        val lLost = LocationState(point = null, status = LocationStatus.ACQUIRING, lastFixTime = baseTime + 10_000L)
        val rLost = tracker.processWithManualCalibration(ChainageMatchResult.noRoute(), lLost, route, cal, baseTime + 10_000L)
        assertEquals(174_300.0, rLost.matchResult.chainage!!.metres.toDouble(), 2.0)
        assertTrue(rLost.matchResult.isManual)

        // 4. Clean GPS recovery after long dropout (30s later, 120m further) -> Stable mode MUST NOT jump/snap to KMZ
        val pRecovered = pointNorthEast(270.0)
        val lRec = makeLocationState(pRecovered, 5.0f, 10.0f, 0f, baseTime + 40_000L)
        val aRec = matchFix(pRecovered, route, 5.0f, 0f, a1, baseTime + 40_000L)
        val rRec = tracker.processWithManualCalibration(aRec, lRec, route, cal, baseTime + 40_000L)
        cal = rRec.updatedCalibration
        assertEquals("Stable mode preserves chainage on long GPS recovery without jump", 174_300.0, rRec.matchResult.chainage!!.metres.toDouble(), 2.0)

        // 5. Subsequent normal +30m movement resumes smoothly from 174+300 -> 174+330
        val pAfter = pointNorthEast(300.0)
        val lAfter = makeLocationState(pAfter, 4.5f, 10.0f, 0f, baseTime + 43_000L)
        val aAfter = matchFix(pAfter, route, 4.5f, 0f, aRec, baseTime + 43_000L)
        val rAfter = tracker.processWithManualCalibration(aAfter, lAfter, route, cal, baseTime + 43_000L)
        assertEquals(174_330.0, rAfter.matchResult.chainage!!.metres.toDouble(), 2.0)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 6: Curves, Bends & Hairpin Continuity
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 6 - hairpin bend maintains longitudinal segment continuity without snapping across parallel leg`() {
        val route = buildHairpinCurveRoute()
        val baseTime = 1_700_003_000_000L

        val outboundSteps = listOf(
            pointNorthEast(100.0, 0.0),
            pointNorthEast(140.0, 0.0),
            pointNorthEast(170.0, 11.0)
        )
        var prev: ChainageMatchResult? = null
        var lastCh = 0L
        outboundSteps.forEachIndexed { i, pt ->
            val ts = baseTime + i * 3_000L
            val res = matchFix(pt, route, 6.0f, 0f, prev, ts)
            prev = res
            assertNotNull(res.chainage)
            assertTrue(
                "Chainage should stay on outbound leg (~150+100..150+185), got ${res.chainage!!.format()}",
                res.chainage!!.metres in 150_090L..150_190L
            )
            if (i > 0) {
                assertTrue("Chainage should advance monotonically on outbound leg", res.chainage!!.metres >= lastCh)
            }
            lastCh = res.chainage!!.metres
        }
    }

    // -------------------------------------------------------------------------
    // SCENARIO 7: Divided / Parallel Carriageways (LHS vs RHS vs Travel Direction)
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 7 - divided parallel carriageways keep LHS and RHS strictly separate from forward and reverse travel`() {
        val route = buildDividedCarriagewayRoute()
        val tracker = ChainageProgressionTracker()
        val baseTime = 1_700_004_000_000L

        val pStart = pointNorthEast(300.0, eastMetres = 8.0)
        val lStart = makeLocationState(pStart, 4.0f, 10.0f, 0f, baseTime)
        val aStart = matchFix(pStart, route, 4.0f, 0f, null, baseTime)
        var cal = tracker.establishManualCalibration(
            enteredChainage = Chainage(120_325L),
            mode = ManualChainageMode.DYNAMIC_CORRECTION,
            currentAutoResult = aStart,
            locationState = lStart,
            routeId = route.id,
            nowMs = baseTime
        )
        assertTrue(cal.isDynamicAnchorCalibrated)
        assertEquals(25L, cal.computedOffsetMetres)

        var prevAuto = aStart
        var lastRes = aStart
        listOf(280.0, 260.0, 240.0, 220.0).forEachIndexed { idx, north ->
            val ts = baseTime + (idx + 1) * 2_000L
            val pt = pointNorthEast(north, eastMetres = 8.0)
            val loc = makeLocationState(pt, 4.0f, 10.0f, 180f, ts)
            val auto = matchFix(pt, route, 4.0f, 180f, prevAuto, ts)
            prevAuto = auto
            val out = tracker.processWithManualCalibration(auto, loc, route, cal, ts)
            cal = out.updatedCalibration
            lastRes = out.matchResult
        }

        assertEquals(120_245.0, lastRes.chainage!!.metres.toDouble(), 2.0)
        assertEquals(TravelChainageDirection.REVERSE, lastRes.travelDirection)
        assertEquals("RHS", lastRes.detectedSide)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 8: Non-Uniform KMZ Intervals & Decreasing Chainage Section
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 8 - non-uniform KMZ intervals and decreasing chainage sections calibrate accurately`() {
        val baseTime = 1_700_005_000_000L

        // Part A: Non-uniform intervals (0, 37, 215, 290, 500, 742, 1000)
        val nonUniformRoute = buildNonUniformRoute()
        val trackerA = ChainageProgressionTracker()

        val ptA0 = pointNorthEast(37.0)
        val locA0 = makeLocationState(ptA0, 4.0f, 10.0f, 0f, baseTime)
        val autoA0 = matchFix(ptA0, nonUniformRoute, 4.0f, 0f, null, baseTime)
        var calA = trackerA.establishManualCalibration(
            enteredChainage = Chainage(100_057L),
            mode = ManualChainageMode.DYNAMIC_CORRECTION,
            currentAutoResult = autoA0,
            locationState = locA0,
            routeId = nonUniformRoute.id,
            nowMs = baseTime
        )
        var prevA = autoA0
        var resA = autoA0
        listOf(120.0, 215.0, 290.0, 395.0).forEachIndexed { i, north ->
            val ts = baseTime + (i + 1) * 4_000L
            val pt = pointNorthEast(north)
            val loc = makeLocationState(pt, 4.0f, 10.0f, 0f, ts)
            val auto = matchFix(pt, nonUniformRoute, 4.0f, 0f, prevA, ts)
            prevA = auto
            val out = trackerA.processWithManualCalibration(auto, loc, nonUniformRoute, calA, ts)
            calA = out.updatedCalibration
            resA = out.matchResult
        }
        assertEquals("Non-uniform dynamic chainage at 395m (+20m offset = 100+415)", 100_415.0, resA.chainage!!.metres.toDouble(), 3.0)

        // Part B: Decreasing chainage section (200+500 down to 200+000 as we travel north 0m -> 500m)
        ChainageMatcher.clearCache()
        val decreasingRoute = buildStraightRoute(
            id = "route_dec",
            startMetres = 200_500L,
            lengthMetres = 500,
            stepMetres = 50,
            decreasing = true
        )
        val trackerB = ChainageProgressionTracker()

        val ptB0 = pointNorthEast(100.0)
        val locB0 = makeLocationState(ptB0, 4.0f, 10.0f, 0f, baseTime)
        val autoB0 = matchFix(ptB0, decreasingRoute, 4.0f, 0f, null, baseTime)
        var calB = trackerB.establishManualCalibration(
            enteredChainage = Chainage(200_415L),
            mode = ManualChainageMode.DYNAMIC_CORRECTION,
            currentAutoResult = autoB0,
            locationState = locB0,
            routeId = decreasingRoute.id,
            nowMs = baseTime
        )

        var prevB = autoB0
        var resB = autoB0
        listOf(150.0, 200.0, 250.0).forEachIndexed { i, north ->
            val ts = baseTime + (i + 1) * 4_000L
            val pt = pointNorthEast(north)
            val loc = makeLocationState(pt, 4.0f, 10.0f, 0f, ts)
            val auto = matchFix(pt, decreasingRoute, 4.0f, 0f, prevB, ts)
            prevB = auto
            val out = trackerB.processWithManualCalibration(auto, loc, decreasingRoute, calB, ts)
            calB = out.updatedCalibration
            resB = out.matchResult
        }
        assertEquals("Decreasing alignment dynamic chainage", 200_265.0, resB.chainage!!.metres.toDouble(), 3.0)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 9: Stationary Condition with GPS Jitter
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 9 - stationary vehicle with GPS jitter shows zero chainage bounce`() {
        val route = buildStraightRoute(startMetres = 174_000L, lengthMetres = 500)
        val tracker = ChainageProgressionTracker()
        val baseTime = 1_700_006_000_000L

        val pt0 = pointNorthEast(250.0)
        val loc0 = makeLocationState(pt0, 5.0f, 0.0f, 0f, baseTime)
        val auto0 = matchFix(pt0, route, 5.0f, 0f, null, baseTime)
        var cal = tracker.establishManualCalibration(
            enteredChainage = Chainage(174_250L),
            mode = ManualChainageMode.CONTINUE_STABLE,
            currentAutoResult = auto0,
            locationState = loc0,
            routeId = route.id,
            nowMs = baseTime
        )

        var prevAuto = auto0
        val jitterOffsets = listOf(0.8, -0.9, 1.2, -1.1, 0.6, -0.7, 1.0, -0.8, 0.5, -1.2)
        jitterOffsets.forEachIndexed { i, dNorth ->
            val ts = baseTime + (i + 1) * 1_000L
            val pt = pointNorthEast(250.0 + dNorth, eastMetres = if (i % 2 == 0) 0.7 else -0.6)
            val loc = makeLocationState(pt, 5.5f, 0.1f, 0f, ts)
            val auto = matchFix(pt, route, 5.5f, 0f, prevAuto, ts)
            prevAuto = auto
            val out = tracker.processWithManualCalibration(auto, loc, route, cal, ts)
            cal = out.updatedCalibration
            assertEquals("Stationary jitter fix #$i must not bounce chainage", 174_250L, out.matchResult.chainage!!.metres)
        }
    }

    // -------------------------------------------------------------------------
    // SCENARIO 10: Zero Cumulative Offset Drift, Explicit Recalibration & Seamless Mode Switch
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 10 - 60 updates have zero cumulative offset drift, recalibration updates anchor, and mode switching preserves chainage`() {
        val route = buildStraightRoute(startMetres = 174_000L, lengthMetres = 1_000)
        val tracker = ChainageProgressionTracker()
        val baseTime = 1_700_007_000_000L

        val p0 = pointNorthEast(100.0)
        val l0 = makeLocationState(p0, 4.0f, 10.0f, 0f, baseTime)
        val a0 = matchFix(p0, route, 4.0f, 0f, null, baseTime)
        var cal = tracker.establishManualCalibration(
            enteredChainage = Chainage(174_142L),
            mode = ManualChainageMode.DYNAMIC_CORRECTION,
            currentAutoResult = a0,
            locationState = l0,
            routeId = route.id,
            nowMs = baseTime
        )
        assertEquals(42L, cal.computedOffsetMetres)

        var lastAuto = a0
        var lastLoc = l0
        var lastRes = a0
        for (i in 1..60) {
            val north = 100.0 + i * 10.0
            val ts = baseTime + i * 1_000L
            val pt = pointNorthEast(north)
            lastLoc = makeLocationState(pt, 4.0f, 10.0f, 0f, ts)
            lastAuto = matchFix(pt, route, 4.0f, 0f, lastAuto, ts)
            val out = tracker.processWithManualCalibration(lastAuto, lastLoc, route, cal, ts)
            cal = out.updatedCalibration
            lastRes = out.matchResult
        }
        assertEquals("Immutable offset must never accumulate", 42L, cal.computedOffsetMetres)
        assertEquals("After 600m travel with +42m offset -> 174+742", 174_742.0, lastRes.chainage!!.metres.toDouble(), 2.0)

        // Seamless switch: Dynamic -> Stable
        val beforeSwitch = lastRes.chainage!!.metres
        cal = tracker.switchManualMode(
            currentCalibration = cal,
            targetMode = ManualChainageMode.CONTINUE_STABLE,
            currentAutoResult = lastAuto,
            locationState = lastLoc,
            routeId = route.id,
            nowMs = baseTime + 61_000L
        )
        assertEquals(ManualChainageMode.CONTINUE_STABLE, cal.mode)
        assertEquals("Dynamic->Stable switch must preserve exact chainage", beforeSwitch, cal.currentChainageMetres)

        // Advance 30m in Stable mode -> 174+772
        val ptStable = pointNorthEast(730.0)
        val locStable = makeLocationState(ptStable, 4.0f, 10.0f, 0f, baseTime + 64_000L)
        val autoStable = matchFix(ptStable, route, 4.0f, 0f, lastAuto, baseTime + 64_000L)
        val outStable = tracker.processWithManualCalibration(autoStable, locStable, route, cal, baseTime + 64_000L)
        cal = outStable.updatedCalibration
        assertEquals(174_772.0, outStable.matchResult.chainage!!.metres.toDouble(), 2.0)

        // Seamless switch back: Stable -> Dynamic
        val beforeBackSwitch = outStable.matchResult.chainage!!.metres
        cal = tracker.switchManualMode(
            currentCalibration = cal,
            targetMode = ManualChainageMode.DYNAMIC_CORRECTION,
            currentAutoResult = autoStable,
            locationState = locStable,
            routeId = route.id,
            nowMs = baseTime + 65_000L
        )
        assertEquals(ManualChainageMode.DYNAMIC_CORRECTION, cal.mode)
        assertEquals("Stable->Dynamic switch must preserve exact chainage", beforeBackSwitch, cal.currentChainageMetres)
        assertTrue(cal.isDynamicAnchorCalibrated)

        // Explicit Recalibration at north=730m (KMZ 174+730) to 174+800 -> new offset = +70m
        cal = tracker.establishManualCalibration(
            enteredChainage = Chainage(174_800L),
            mode = ManualChainageMode.DYNAMIC_CORRECTION,
            currentAutoResult = autoStable,
            locationState = locStable,
            routeId = route.id,
            nowMs = baseTime + 66_000L
        )
        assertEquals(70L, cal.computedOffsetMetres)
        assertEquals(174_800L, cal.currentChainageMetres)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 11: Photo Capture Overlay & EXIF Integration
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 11 - photo overlay and EXIF metadata reflect exact active manual chainage, mode, offset, and travel direction`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val match = ChainageMatchResult(
            chainage = Chainage(174_315L),
            rawUncorrectedChainage = Chainage(174_280L),
            customChainageText = "174+315",
            confidence = MatchConfidence.HIGH,
            isManual = true,
            detectedSide = "RHS",
            source = ChainageSource.MANUAL,
            manualChainageMode = ManualChainageMode.DYNAMIC_CORRECTION,
            travelDirection = TravelChainageDirection.REVERSE,
            manualCalibrationAnchorMetres = 174_250L,
            manualCalibrationOffsetMetres = 35L
        )
        val project = ProjectProfile(highwayId = "NH-65 Survey")
        val overlayConfig = OverlayConfig()
        val snapshot = CaptureSnapshot(
            timestamp = 1_700_008_000_000L,
            location = pointNorthEast(280.0),
            gpsAccuracy = 4.2f,
            matchResult = match,
            projectProfile = project,
            overlayConfig = overlayConfig,
            locationName = ""
        )

        val lines = OverlayTextGenerator.generateLines(
            config = overlayConfig,
            project = project,
            location = snapshot.location,
            accuracy = snapshot.gpsAccuracy,
            compassHeading = "180° S",
            speedMps = 8.0f,
            bearing = 180f,
            chainageStr = snapshot.displayChainage,
            cameraMode = CameraMode.HIGHWAY,
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = true,
            timestamp = snapshot.timestamp
        )
        assertTrue(
            "Overlay lines must include live calibrated chainage 174+315, got: $lines",
            lines.any { it.contains("174+315") }
        )

        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val tempPhoto = File(context.cacheDir, "test_manual_chainage_photo.jpg")
        try {
            val saved = OverlayRenderer.saveBitmapWithExif(bitmap, tempPhoto, snapshot, jpegQuality = 90, context = context)
            assertTrue("Photo JPEG with EXIF must save cleanly", saved)

            val exif = ExifInterface(tempPhoto.absolutePath)
            val desc = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION).orEmpty()
            val comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT).orEmpty()

            assertTrue("EXIF description must include 174+315, got: $desc", desc.contains("174+315"))
            assertTrue("EXIF description must include Dynamic Chainage Correction, got: $desc", desc.contains("Dynamic Chainage Correction"))
            assertTrue("EXIF comment must include Ref 174+250, got: $comment", comment.contains("174+250"))
            assertTrue("EXIF comment must include Offset +35m, got: $comment", comment.contains("+35m"))
            assertTrue("EXIF comment must include Travel Reverse, got: $comment", comment.contains("Reverse"))
        } finally {
            bitmap.recycle()
            tempPhoto.delete()
        }
    }

    // -------------------------------------------------------------------------
    // SCENARIOS 12 & 13: Continuous Video Chainage (Forward, Reverse, GPS Loss & Pause/Resume)
    // -------------------------------------------------------------------------
    @Test
    fun `scenarios 12 and 13 - video timeline updates manual chainage continuously across forward, reverse, GPS loss, and pause-resume`() {
        val baseWall = 1_700_009_000_000L

        fun videoSnap(
            wallOffsetMs: Long,
            activeElapsedMs: Long,
            chainageM: Long,
            dir: TravelChainageDirection,
            mode: ManualChainageMode = ManualChainageMode.DYNAMIC_CORRECTION,
            speed: Float = 10f,
            gpsRetained: Boolean = false
        ): CaptureSnapshot {
            val ch = Chainage(chainageM)
            return CaptureSnapshot(
                timestamp = baseWall + wallOffsetMs,
                videoElapsedMs = activeElapsedMs,
                location = pointNorthEast(chainageM.toDouble() - 174_000.0),
                gpsAccuracy = if (gpsRetained) 60f else 4.5f,
                speedMps = speed,
                bearing = if (dir == TravelChainageDirection.REVERSE) 180f else 0f,
                isGpsRetained = gpsRetained,
                matchResult = ChainageMatchResult(
                    chainage = ch,
                    customChainageText = ch.format(),
                    confidence = MatchConfidence.HIGH,
                    isManual = true,
                    isUncertain = gpsRetained,
                    source = ChainageSource.MANUAL,
                    manualChainageMode = mode,
                    travelDirection = dir
                ),
                projectProfile = ProjectProfile(),
                overlayConfig = OverlayConfig()
            )
        }

        val timeline = listOf(
            videoSnap(0L, 0L, 174_250L, TravelChainageDirection.FORWARD),
            videoSnap(4_000L, 4_000L, 174_290L, TravelChainageDirection.FORWARD),
            videoSnap(34_000L, 4_001L, 174_290L, TravelChainageDirection.REVERSE),
            videoSnap(38_000L, 8_000L, 174_250L, TravelChainageDirection.REVERSE),
            videoSnap(40_000L, 10_000L, 174_250L, TravelChainageDirection.REVERSE, speed = 0f, gpsRetained = true)
        )

        val midFwd = VideoOverlayProcessor.getSnapshotAtTime(timeline, timeline.first(), 2_000L)
        assertEquals("174+270", midFwd.displayChainage)
        assertEquals("174+270", midFwd.matchResult.customChainageText)

        val midRevAfterPause = VideoOverlayProcessor.getSnapshotAtTime(timeline, timeline.first(), 6_000L)
        assertEquals(
            "Pause-safe video elapsed time must interpolate reverse segment cleanly",
            174_270.0,
            midRevAfterPause.matchResult.chainage!!.metres.toDouble(),
            2.0
        )
        assertEquals(midRevAfterPause.matchResult.chainage!!.format(), midRevAfterPause.matchResult.customChainageText)

        val endRev = VideoOverlayProcessor.getSnapshotAtTime(timeline, timeline.first(), 8_000L)
        assertEquals(174_250.0, endRev.matchResult.chainage!!.metres.toDouble(), 2.0)

        val tunnelHold = VideoOverlayProcessor.getSnapshotAtTime(timeline, timeline.first(), 12_000L)
        assertEquals("GPS-loss video segment must hold last reliable chainage", 174_250L, tunnelHold.matchResult.chainage!!.metres)

        val revOnly = listOf(videoSnap(0L, 0L, 174_300L, TravelChainageDirection.REVERSE, speed = 10f))
        val revExtrap = VideoOverlayProcessor.getSnapshotAtTime(revOnly, revOnly.first(), 3_000L)
        assertEquals("Reverse extrapolation must decrease chainage", 174_270L, revExtrap.matchResult.chainage!!.metres)
        assertEquals("174+270", revExtrap.matchResult.customChainageText)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 14: App Restart Persistence + Camera Screen Manual Chainage UI Flow
    // -------------------------------------------------------------------------
    @Test
    fun `scenario 14 - manual calibration persists across app restart and HUD resolves live progressing chainage`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm1 = MainViewModel(app)
        vm1.clearManualChainage()

        vm1.setManualChainage("174+250", ManualChainageMode.DYNAMIC_CORRECTION)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("174+250", vm1.manualChainageText.value)
        assertEquals(ManualChainageMode.DYNAMIC_CORRECTION, vm1.manualChainageCalibration.value.mode)
        assertTrue(vm1.manualChainageCalibration.value.isActive)

        PreferencesManager.clearMemoryCacheForProcessRestart()
        val vm2 = MainViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("Manual calibration must survive app restart", vm2.manualChainageCalibration.value.isActive)
        assertEquals(ManualChainageMode.DYNAMIC_CORRECTION, vm2.manualChainageCalibration.value.mode)
        assertEquals(174_250L, vm2.manualChainageCalibration.value.anchorChainageMetres)

        val progressedIndicators = resolveChainageIndicatorsState(
            matchResult = ChainageMatchResult(
                chainage = Chainage(174_310L),
                customChainageText = "174+310",
                confidence = MatchConfidence.HIGH,
                isManual = true,
                source = ChainageSource.MANUAL,
                manualChainageMode = ManualChainageMode.DYNAMIC_CORRECTION
            ),
            manualChainageText = "174+250",
            hasActiveRoute = true,
            isCorrectionsEnabled = true
        )
        assertEquals("HUD must show live progressed chainage 174+310", "174+310", progressedIndicators.displayChainage)

        vm2.clearManualChainage()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun `scenario 14b - CameraScreen compact Manual Chainage UI toggles modes, applies reference, recalibrates, and resets`() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.clearManualChainage()

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraScreen(
                        viewModel = viewModel,
                        onNavigateToPhotos = {}
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(250L)
        shadowOf(Looper.getMainLooper()).idle()

        // 1. Open compact Manual Chainage panel directly from Camera HUD chip
        composeTestRule.onNodeWithTag("chainage_chip").assertIsDisplayed().performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        shadowOf(Looper.getMainLooper()).idle()

        // 2. Verify two-option segmented toggle switch is displayed
        composeTestRule.onNodeWithTag("manual_chainage_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_mode_stable_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_mode_dynamic_button").assertIsDisplayed()

        // 3. Select Dynamic Chainage Correction, type "174+250", and Apply
        composeTestRule.onNodeWithTag("manual_mode_dynamic_button").performClick()
        composeTestRule.onNodeWithTag("manual_chainage_input").performTextClearance()
        composeTestRule.onNodeWithTag("manual_chainage_input").performTextInput("174+250")
        composeTestRule.onNodeWithTag("save_manual_chainage_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(300L)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(ManualChainageMode.DYNAMIC_CORRECTION, viewModel.manualChainageCalibration.value.mode)
        assertEquals(174_250L, viewModel.manualChainageCalibration.value.anchorChainageMetres)
        composeTestRule.onNodeWithTag("hud_chainage_value", useUnmergedTree = true).assertTextContains("174+250")

        // 4. Re-open panel, verify mode badge & Calibrate button, switch seamlessly to Continue Stable Chainage
        composeTestRule.onNodeWithTag("chainage_chip").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithTag("manual_chainage_mode_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recalibrate_manual_chainage_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_mode_stable_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(ManualChainageMode.CONTINUE_STABLE, viewModel.manualChainageCalibration.value.mode)
        assertEquals(174_250L, viewModel.manualChainageCalibration.value.currentChainageMetres)

        // 5. Reset returns cleanly to automatic KMZ/GPS mode
        composeTestRule.onNodeWithTag("clear_manual_chainage_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(viewModel.manualChainageCalibration.value.isActive)
    }
}
