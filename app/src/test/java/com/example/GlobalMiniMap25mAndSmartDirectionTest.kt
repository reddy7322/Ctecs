package com.example

import com.example.geo.ChainageMatcher
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.AlignmentDirectionConvention
import com.example.model.CameraMode
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.CoordinateFormat
import com.example.model.DirectionControlMode
import com.example.model.DirectionDetectionState
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.SmartDirectionPreferences
import com.example.model.SmartDirectionState
import com.example.navigation.SmartDirectionManager
import com.example.overlay.MiniMapRenderer
import com.example.overlay.OverlayTextGenerator
import com.example.ui.map.MiniMapViewportMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GlobalMiniMap25mAndSmartDirectionTest {

    @Before
    fun setUp() {
        ChainageMatcher.clearCache()
    }

    // =========================================================================
    // 1. GLOBAL MINI MAP — DYNAMIC 25-METRE GROUND VIEWING RANGE
    // =========================================================================

    @Test
    fun dynamic25mZoom_adaptsToLatitudeViewportSizeAndAspectRatio_andRoundTripsExact25m() {
        val testCases = listOf(
            Triple(0.0, 400f, 400f),      // Equator, square
            Triple(28.6139, 360f, 640f),  // Delhi, portrait phone
            Triple(28.6139, 800f, 360f),  // Delhi, landscape phone
            Triple(28.6139, 180f, 180f),  // Camera inset Mini Map
            Triple(59.9139, 500f, 320f)   // High latitude (Oslo)
        )

        for ((lat, w, h) in testCases) {
            val zoom25m = MiniMapViewportMath.computeZoomForGroundRangeMetres(
                latitude = lat,
                viewportWidthPx = w,
                viewportHeightPx = h,
                groundRangeMetres = MiniMapViewportMath.DEFAULT_GROUND_VIEW_RANGE_METRES
            )
            assertTrue("Zoom $zoom25m must be within bounds", zoom25m in MiniMapViewportMath.MIN_ZOOM..MiniMapViewportMath.MAX_ZOOM)

            val groundRange = MiniMapViewportMath.computeGroundRangeMetres(
                continuousZoom = zoom25m,
                latitude = lat,
                viewportWidthPx = w,
                viewportHeightPx = h
            )
            assertEquals("Ground range must equal 25.0m for lat=$lat, ${w}x${h}", 25.0, groundRange, 0.15)

            // splitContinuousZoom must keep baseZoom <= 19 and smooth overscale factor > 1 when zoom > 19
            val (baseZoom, fracScale) = MiniMapViewportMath.splitContinuousZoom(zoom25m)
            assertTrue("Base tile zoom must not exceed 19", baseZoom <= 19)
            assertTrue("Fractional scale must be finite and positive", fracScale in 0.5f..16.0f)
        }

        // Higher latitude has smaller cos(lat), so the same 25m ground span requires lower zoom than Equator
        val zEquator = MiniMapViewportMath.computeZoomForGroundRangeMetres(0.0, 400f, 400f, 25.0)
        val zOslo = MiniMapViewportMath.computeZoomForGroundRangeMetres(60.0, 400f, 400f, 25.0)
        assertTrue("High-latitude zoom ($zOslo) must be lower than Equator zoom ($zEquator) for same 25m range", zOslo < zEquator)

        // Larger viewport requires higher zoom to keep the same 25m ground range
        val zSmall = MiniMapViewportMath.computeZoomForGroundRangeMetres(28.6, 200f, 200f, 25.0)
        val zLarge = MiniMapViewportMath.computeZoomForGroundRangeMetres(28.6, 600f, 600f, 25.0)
        assertTrue("Larger viewport zoom ($zLarge) must exceed smaller viewport zoom ($zSmall)", zLarge > zSmall)

        // MiniMapRenderer.computeAutoZoom must match the global 25m viewport math
        val rendererZoom = MiniMapRenderer.computeAutoZoom(
            center = GeoPoint(28.6139, 77.2090),
            routeData = null,
            viewportWidthPx = 420f,
            viewportHeightPx = 300f
        )
        val expectedZoom = MiniMapViewportMath.computeZoomForGroundRangeMetres(28.6139, 420f, 300f, 25.0)
        assertEquals(expectedZoom, rendererZoom, 0.001f)
    }

    // =========================================================================
    // 2. FAST SPATIAL-INDEXED KMZ/KML ALIGNMENT & PARALLEL CARRIAGEWAY MATCHING
    // =========================================================================

    @Test
    fun spatialGridIndex_matchesLargeAlignmentAccuratelyAndUsesBearingTieBreakerOnParallelCarriageways() {
        // Build a large 2,500-segment highway alignment (~50 km at 20m spacing)
        val baseLat = 28.5000
        val baseLng = 77.2000
        val metersPerDegLat = 111_320.0
        val stepDegLat = 20.0 / metersPerDegLat
        val pts = (0..2500).map { i -> GeoPoint(baseLat + i * stepDegLat, baseLng) }
        val markers = listOf(
            RouteMarker("m0", "0+000", Chainage(0L), pts.first()),
            RouteMarker("m1", "50+000", Chainage(50_000L), pts.last())
        )
        val largeRoute = RouteData(
            id = "large-kmz",
            name = "Large Expressway",
            fileName = "large.kmz",
            markers = markers,
            alignments = listOf(RouteAlignment("main", "Mainline", pts, direction = "INCREASING")),
            bounds = GeoBounds(pts.first().latitude, pts.last().latitude, baseLng, baseLng),
            startChainage = Chainage(0L),
            endChainage = Chainage(50_000L)
        )

        // Query mid-corridor at segment 1250 (25+000)
        val queryPt = GeoPoint(baseLat + 1250 * stepDegLat, baseLng + 0.00004) // ~4m East
        val match = ChainageMatcher.match(
            gpsPoint = queryPt,
            gpsAccuracy = 4f,
            bearing = 0f,
            routeData = largeRoute
        )
        assertEquals(MatchConfidence.HIGH, match.confidence)
        assertEquals(25_000.0, (match.chainage?.metres ?: -1L).toDouble(), 10.0)
        val cached = ChainageMatcher.getOrCreateCachedGeometry(largeRoute.alignments.first(), markers)
        assertTrue("Spatial grid must be populated", cached.spatialGrid.isNotEmpty())
        assertEquals(2500, cached.segmentBearingsDeg.size)
        assertEquals(0.0, cached.segmentBearingsDeg[1250], 1.0)

        // Parallel LHS (Northbound, 0 deg) & RHS (Southbound, 180 deg) carriageways separated by 10m
        val lngOffset10m = 10.0 / (metersPerDegLat * cos(Math.toRadians(baseLat)))
        val lhsPts = (0..20).map { i -> GeoPoint(baseLat + i * stepDegLat, baseLng) }
        val rhsPts = (0..20).map { i -> GeoPoint(baseLat + (20 - i) * stepDegLat, baseLng + lngOffset10m) }
        val dualRoute = RouteData(
            id = "dual-kmz",
            name = "Dual Highway",
            fileName = "dual.kmz",
            markers = listOf(
                RouteMarker("d0", "10+000", Chainage(10_000L), lhsPts.first()),
                RouteMarker("d1", "10+400", Chainage(10_400L), lhsPts.last())
            ),
            alignments = listOf(
                RouteAlignment("lhs", "LHS", lhsPts, direction = "INCREASING", carriagewayType = "LHS"),
                RouteAlignment("rhs", "RHS", rhsPts, direction = "DECREASING", carriagewayType = "RHS")
            ),
            bounds = GeoBounds(lhsPts.first().latitude, lhsPts.last().latitude, baseLng, baseLng + lngOffset10m),
            startChainage = Chainage(10_000L),
            endChainage = Chainage(10_400L)
        )

        // Vehicle positioned midway between LHS & RHS (5m from each) travelling South (180°) -> bearing tie-breaker picks RHS!
        val midPt = GeoPoint(baseLat + 10 * stepDegLat, baseLng + lngOffset10m * 0.5)
        val southboundMatch = ChainageMatcher.match(midPt, gpsAccuracy = 5f, bearing = 180f, routeData = dualRoute)
        assertEquals("RHS", southboundMatch.matchedAlignmentName)

        // Same midpoint travelling North (0°) -> bearing tie-breaker picks LHS!
        val northboundMatch = ChainageMatcher.match(midPt, gpsAccuracy = 5f, bearing = 0f, routeData = dualRoute)
        assertEquals("LHS", northboundMatch.matchedAlignmentName)
    }

    // =========================================================================
    // 3. SMART LHS/RHS DETECTION ON CURVES, TRANSIENT WEAK GPS & ROUTE SWITCHES
    // =========================================================================

    @Test
    fun smartDirectionManager_handlesTightCurve_briefWeakGpsHoldAndExpiry_andInstantRouteSwitch() {
        // Build a 90-degree curved road alignment (North then curving smoothly East)
        val centerLat = 29.0000
        val centerLng = 77.5000
        val metersPerDegLat = 111_320.0
        val metersPerDegLng = metersPerDegLat * cos(Math.toRadians(centerLat))
        val radiusM = 120.0

        // Quarter circle from South-West (angle = 180°) to North (angle = 90°) -> heading turns from 0° (N) to 90° (E)
        val curvePts = (0..18).map { i ->
            val theta = Math.toRadians(180.0 - i * 5.0) // 180° -> 90°
            val dxM = radiusM * cos(theta)
            val dyM = radiusM * sin(theta)
            GeoPoint(centerLat + dyM / metersPerDegLat, centerLng + dxM / metersPerDegLng)
        }
        val curveRoute = RouteData(
            id = "curve-route",
            name = "Curved Interchange",
            fileName = "curve.kmz",
            markers = listOf(
                RouteMarker("c0", "5+000", Chainage(5_000L), curvePts.first()),
                RouteMarker("c1", "5+188", Chainage(5_188L), curvePts.last())
            ),
            alignments = listOf(RouteAlignment("curve-main", "Curve Main", curvePts, direction = "INCREASING")),
            bounds = GeoBounds(
                curvePts.minOf { it.latitude },
                curvePts.maxOf { it.latitude },
                curvePts.minOf { it.longitude },
                curvePts.maxOf { it.longitude }
            ),
            startChainage = Chainage(5_000L),
            endChainage = Chainage(5_188L)
        )

        val manager = SmartDirectionManager()
        val prefs = SmartDirectionPreferences(
            controlMode = DirectionControlMode.AUTO,
            alignmentConvention = AlignmentDirectionConvention.INCREASING_IS_LHS
        )

        var lastState = SmartDirectionState.alignmentUnavailable()
        var prevMatch: ChainageMatchResult? = null
        var now = 100_000L
        // Traverse around the curve (indices 0, 2, 4, 6, 8, 10)
        for (idx in listOf(0, 2, 4, 6, 8, 10)) {
            val pt = curvePts[idx]
            val nextPt = curvePts[(idx + 1).coerceAtMost(curvePts.lastIndex)]
            val heading = com.example.geo.GeoUtils.bearingDegrees(pt, nextPt).toFloat()
            val match = ChainageMatcher.match(pt, gpsAccuracy = 5f, bearing = heading, routeData = curveRoute, previousResult = prevMatch)
            prevMatch = match
            val loc = LocationState(
                point = pt,
                accuracy = 5f,
                speed = 10f,
                bearing = heading,
                status = LocationStatus.READY,
                lastFixTime = now
            )
            lastState = manager.evaluate(loc, match, curveRoute, prefs, nowMs = now)
            now += 2_000L
        }
        assertEquals(DirectionDetectionState.LHS_CONFIRMED, lastState.detectionState)
        assertEquals("LHS", lastState.confirmedSide)

        // Brief transient weak GPS drop (3s after last confirmed, accuracy 50m) -> briefly holds "LHS" while marking GPS_SIGNAL_WEAK
        val briefWeakLoc = LocationState(
            point = curvePts[10],
            accuracy = 50f,
            speed = 10f,
            bearing = 45f,
            status = LocationStatus.POOR_ACCURACY,
            lastFixTime = now + 1_000L
        )
        val briefWeakState = manager.evaluate(briefWeakLoc, prevMatch!!, curveRoute, prefs, nowMs = now + 1_000L)
        assertEquals(DirectionDetectionState.GPS_SIGNAL_WEAK, briefWeakState.detectionState)
        assertEquals("Brief weak GPS (< 6s) should hold last confirmed side", "LHS", briefWeakState.confirmedSide)

        // Prolonged weak GPS (> 6s after last confirmed) -> drops confirmedSide to null rather than guessing
        val expiredWeakTime = now + SmartDirectionManager.TRANSIENT_WEAK_GPS_HOLD_MS + 3_000L
        val prolongedWeakLoc = briefWeakLoc.copy(lastFixTime = expiredWeakTime)
        val expiredWeakState = manager.evaluate(prolongedWeakLoc, prevMatch, curveRoute, prefs, nowMs = expiredWeakTime)
        assertEquals(DirectionDetectionState.GPS_SIGNAL_WEAK, expiredWeakState.detectionState)
        assertNull("Expired weak GPS must clear confirmedSide instead of guessing", expiredWeakState.confirmedSide)

        // Switching to a new KMZ route immediately clears stale direction without app restart
        val secondRoute = curveRoute.copy(id = "second-kmz", name = "Second Highway")
        val afterSwitch = manager.evaluate(
            locationState = LocationState(
                point = curvePts[12],
                accuracy = 5f,
                speed = 10f,
                bearing = 60f,
                status = LocationStatus.READY,
                lastFixTime = expiredWeakTime + 2_000L
            ),
            matchResult = prevMatch,
            routeData = secondRoute,
            preferences = prefs,
            nowMs = expiredWeakTime + 2_000L
        )
        assertEquals("second-kmz", afterSwitch.routeId)
        assertNull("Route switch must immediately clear previous route's confirmed side", afterSwitch.confirmedSide)
    }

    // =========================================================================
    // 4. PREVIEW / PHOTO / VIDEO / EXIF SIDE CONSISTENCY (LHS / RHS / BHS / NONE / AUTO)
    // =========================================================================

    @Test
    fun overlayTextGenerator_sideOutputConsistentAcrossManualAndAutoModes() {
        val baseProfile = ProjectProfile(
            highwayId = "NH-44",
            packageSection = "Pkg-01",
            workType = "MM",
            roadSide = RoadSide.RHS,
            lane = "I/L",
            activity = "DBM"
        )
        val config = OverlayConfig(
            showTimestamp = false,
            showCoordinates = false,
            showLocationName = false,
            showProjectInfo = true,
            showChainage = true,
            showWeatherInfo = false
        )

        fun buildOverlayLine(dirState: SmartDirectionState, profile: ProjectProfile): String {
            val snap = CaptureSnapshot(
                timestamp = 1_700_000_000_000L,
                location = GeoPoint(28.6, 77.2),
                gpsAccuracy = 5f,
                matchResult = ChainageMatchResult(
                    chainage = Chainage(12_345L),
                    confidence = MatchConfidence.HIGH,
                    smartDirectionState = dirState
                ),
                projectProfile = profile,
                overlayConfig = config
            )
            val displaySide = OverlayTextGenerator.resolveAuthoritativeDisplaySide(dirState, profile)
            val (captureSide, useOverride) = OverlayTextGenerator.resolveAuthoritativeCaptureSide(snap)
            assertEquals("Camera preview side must strictly equal capture/EXIF side", displaySide, captureSide)
            assertTrue(useOverride)
            return OverlayTextGenerator.generateLines(
                config = config,
                project = profile,
                location = GeoPoint(28.6, 77.2),
                accuracy = 5f,
                compassHeading = "",
                speedMps = null,
                bearing = null,
                chainageStr = "12+345",
                cameraMode = CameraMode.NORMAL,
                coordinateFormat = CoordinateFormat.DECIMAL,
                is24HourFormat = false,
                authoritativeSideOverride = captureSide,
                useAuthoritativeSide = useOverride
            ).joinToString(" ")
        }

        // Manual LHS -> "LHS"
        val lhsLine = buildOverlayLine(
            SmartDirectionState(isManualOverride = true, confirmedSide = "LHS"),
            baseProfile.copy(roadSide = RoadSide.LHS)
        )
        assertTrue(lhsLine.contains("12+345 LHS I/L DBM"))

        // Manual RHS -> "RHS"
        val rhsLine = buildOverlayLine(
            SmartDirectionState(isManualOverride = true, confirmedSide = "RHS"),
            baseProfile.copy(roadSide = RoadSide.RHS)
        )
        assertTrue(rhsLine.contains("12+345 RHS I/L DBM"))

        // Manual BHS -> "BHS" (never "BOTH" or "AUTO")
        val bhsLine = buildOverlayLine(
            SmartDirectionState(isManualOverride = true, confirmedSide = "BHS"),
            baseProfile.copy(roadSide = RoadSide.BOTH)
        )
        assertTrue(bhsLine.contains("12+345 BHS I/L DBM"))
        assertFalse(bhsLine.contains("BOTH"))

        // Manual NONE -> side omitted cleanly
        val noneLine = buildOverlayLine(
            SmartDirectionState(isManualOverride = true, confirmedSide = "NONE"),
            baseProfile.copy(roadSide = RoadSide.NONE)
        )
        assertTrue(noneLine.contains("12+345 I/L DBM"))
        assertFalse(noneLine.contains("NONE"))
        assertFalse(noneLine.contains("LHS"))
        assertFalse(noneLine.contains("RHS"))
        assertFalse(noneLine.contains("BHS"))

        // Auto mode with confirmed LHS -> "LHS" only (never "AUTO", never "BHS")
        val autoLhsLine = buildOverlayLine(
            SmartDirectionState(detectionState = DirectionDetectionState.LHS_CONFIRMED, isManualOverride = false, confirmedSide = "LHS"),
            baseProfile.copy(roadSide = RoadSide.BOTH)
        )
        assertTrue(autoLhsLine.contains("12+345 LHS I/L DBM"))
        assertFalse(autoLhsLine.contains("AUTO"))
        assertFalse(autoLhsLine.contains("BHS"))

        // Auto mode uncertain -> side omitted cleanly (no guessed side)
        val autoUncertainLine = buildOverlayLine(
            SmartDirectionState(detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN, isManualOverride = false, confirmedSide = null),
            baseProfile.copy(roadSide = RoadSide.RHS)
        )
        assertTrue(autoUncertainLine.contains("12+345 I/L DBM"))
        assertFalse(autoUncertainLine.contains("RHS"))
        assertFalse(autoUncertainLine.contains("LHS"))
        assertFalse(autoUncertainLine.contains("AUTO"))
    }
}
