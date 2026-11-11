package com.example

import com.example.geo.ChainageMatcher
import com.example.geo.GeoUtils
import com.example.geo.SmartChainageCorrectionEngine
import com.example.model.*
import com.example.parser.KmzParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class PrecisionKmzChainageCorrectionTest {

    private fun createSampleAlignmentRoute(
        id: String = "route-precision-1",
        name: String = "NH-48 Precision Corridor",
        direction: String = "INCREASING",
        carriagewayType: String = "MAIN"
    ): RouteData {
        // Alignment of 3 points: (28.000, 77.000) -> (28.009, 77.000) -> (28.018, 77.000) (~2000m north)
        val p1 = GeoPoint(28.0000, 77.0000, 200.0)
        val p2 = GeoPoint(28.0090, 77.0000, 200.0)
        val p3 = GeoPoint(28.0180, 77.0000, 200.0)

        val alignment = RouteAlignment(
            id = "align-1",
            name = name,
            points = listOf(p1, p2, p3),
            direction = direction,
            carriagewayType = carriagewayType
        )

        // Markers at KM 100+000, 101+000, 102+000
        val m1 = RouteMarker(
            id = "m-100",
            label = "100+000",
            chainage = Chainage(100000L),
            point = p1
        )
        val m2 = RouteMarker(
            id = "m-101",
            label = "101+000",
            chainage = Chainage(101000L),
            point = p2
        )
        val m3 = RouteMarker(
            id = "m-102",
            label = "102+000",
            chainage = Chainage(102000L),
            point = p3
        )

        return RouteData(
            id = id,
            name = name,
            fileName = "nh48.kmz",
            markers = listOf(m1, m2, m3),
            alignments = listOf(alignment),
            bounds = GeoBounds.fromPoints(listOf(p1, p2, p3)),
            startChainage = Chainage(100000L),
            endChainage = Chainage(102000L)
        )
    }

    @Test
    fun testNearestSegmentProjectionVsNearestVertex() {
        val route = createSampleAlignmentRoute()
        // Query GPS point midway between p1 (28.0000) and p2 (28.0090) at latitude 28.0045, offset by 10m east
        // The midpoint is ~500m away from vertex p1 and ~500m away from vertex p2,
        // but perpendicular distance to segment [p1, p2] is only ~10m!
        val queryGps = GeoPoint(28.0045, 77.0001, 200.0) // ~10m east of centerline

        val result = ChainageMatcher.match(
            gpsPoint = queryGps,
            gpsAccuracy = 3.5f,
            routeData = route
        )

        assertEquals(MatchConfidence.HIGH, result.confidence)
        // Offset to segment should be ~9-11 metres (perpendicular projection onto segment, NOT 500m to vertex)
        assertTrue("Perpendicular offset should be close to 10m", result.offsetMetres < 15.0)
        assertEquals(0, result.matchedSegmentIndex)

        // Chainage should interpolate midway between 100+000 and 101+000 => ~100+500
        val ch = result.chainage?.metres ?: 0L
        assertTrue("Chainage should be ~100+500, got $ch", ch in 100480L..100520L)
    }

    @Test
    fun testIncreasingAndDecreasingChainageDirections() {
        // INCREASING
        val routeInc = createSampleAlignmentRoute(direction = "INCREASING")
        val gpsMid = GeoPoint(28.0045, 77.0000, 200.0)
        val resInc = ChainageMatcher.match(gpsPoint = gpsMid, gpsAccuracy = 4f, routeData = routeInc)
        assertEquals(100500L, resInc.chainage?.metres?.let { Math.round(it / 10.0) * 10 })

        // DECREASING direction: start at 102+000, end at 100+000
        val p1 = GeoPoint(28.0000, 77.0000, 200.0)
        val p2 = GeoPoint(28.0090, 77.0000, 200.0)
        val routeDec = RouteData(
            id = "route-dec",
            name = "Decreasing Alignment",
            fileName = "dec.kmz",
            markers = listOf(
                RouteMarker("m-1", "102+000", Chainage(102000L), p1),
                RouteMarker("m-2", "100+000", Chainage(100000L), p2)
            ),
            alignments = listOf(
                RouteAlignment("align-dec", "Decreasing Alignment", listOf(p1, p2), direction = "DECREASING")
            ),
            bounds = GeoBounds.fromPoints(listOf(p1, p2)),
            startChainage = Chainage(102000L),
            endChainage = Chainage(100000L)
        )

        val resDec = ChainageMatcher.match(gpsPoint = gpsMid, gpsAccuracy = 4f, routeData = routeDec)
        // Midway from 102+000 decreasing to 100+000 is 101+000
        val chDec = resDec.chainage?.metres ?: 0L
        assertTrue("Decreasing chainage at midpoint should be ~101+000, got $chDec", chDec in 100950L..101050L)
    }

    @Test
    fun testCurvesAndTransitionSectionsInterpolation() {
        // Alignment around a bend: p1 -> p2 (turn east) -> p3
        val p1 = GeoPoint(28.0000, 77.0000)
        val p2 = GeoPoint(28.0050, 77.0000)
        val p3 = GeoPoint(28.0050, 77.0050)

        val alignment = RouteAlignment("bend-align", "Bend Alignment", listOf(p1, p2, p3))
        val m1 = RouteMarker("m1", "50+000", Chainage(50000L), p1)
        val m2 = RouteMarker("m2", "51+000", Chainage(51000L), p3)

        val route = RouteData(
            id = "bend-route",
            name = "Bend Highway",
            fileName = "bend.kmz",
            markers = listOf(m1, m2),
            alignments = listOf(alignment),
            bounds = GeoBounds.fromPoints(listOf(p1, p2, p3)),
            startChainage = Chainage(50000L),
            endChainage = Chainage(51000L)
        )

        // Point on the second leg [p2, p3] halfway to p3
        val query = GeoPoint(28.0050, 77.0025)
        val match = ChainageMatcher.match(gpsPoint = query, gpsAccuracy = 5f, routeData = route)

        assertEquals(MatchConfidence.HIGH, match.confidence)
        assertEquals(1, match.matchedSegmentIndex) // Second segment
        // Distance along polyline is length(p1->p2) + 0.5 * length(p2->p3)
        assertTrue(match.chainage != null && match.chainage!!.metres > 50500L)
    }

    @Test
    fun testContinuityHysteresisPreventsParallelCarriagewayFlutter() {
        // Parallel carriageways: LHS and RHS 20 metres apart
        val lhsPts = listOf(GeoPoint(28.000, 77.000), GeoPoint(28.010, 77.000))
        val rhsPts = listOf(GeoPoint(28.000, 77.0002), GeoPoint(28.010, 77.0002)) // ~20m east

        val alignLhs = RouteAlignment("a-lhs", "NH-48 LHS Carriageway", lhsPts, carriagewayType = "LHS")
        val alignRhs = RouteAlignment("a-rhs", "NH-48 RHS Carriageway", rhsPts, carriagewayType = "RHS")

        val route = RouteData(
            id = "parallel-route",
            name = "Dual Carriageway",
            fileName = "dual.kmz",
            markers = listOf(RouteMarker("m1", "0+000", Chainage(0L), lhsPts[0])),
            alignments = listOf(alignLhs, alignRhs),
            bounds = GeoBounds.fromPoints(lhsPts + rhsPts),
            startChainage = Chainage(0L),
            endChainage = Chainage(1000L)
        )

        // Previous result was tracking LHS
        val prevResult = ChainageMatchResult(
            chainage = Chainage(500L),
            matchedAlignmentName = "NH-48 LHS Carriageway",
            confidence = MatchConfidence.HIGH
        )

        // Vehicle moves near the median, 9m from LHS and 11m from RHS
        // Without hysteresis, a noisy reading might jump. With 5m hysteresis bonus to LHS:
        // LHS effective score = 9m - 5m = 4m vs RHS score = 11m
        val medianGps = GeoPoint(28.005, 77.00009) // closer to LHS
        val match = ChainageMatcher.match(
            gpsPoint = medianGps,
            gpsAccuracy = 6f,
            routeData = route,
            previousResult = prevResult
        )

        assertEquals("NH-48 LHS Carriageway", match.matchedAlignmentName)
    }

    @Test
    fun testSuddenJumpDetectionAndGracefulFallback() {
        val route = createSampleAlignmentRoute()
        val prev = ChainageMatchResult(
            chainage = Chainage(100100L),
            matchedAlignmentName = "NH-48 Precision Corridor",
            confidence = MatchConfidence.HIGH
        )

        // GPS suddenly reports a jump 800m ahead to 100+900 in one step
        val jumpGps = GeoPoint(28.0080, 77.0000) // near 100+900

        val match = ChainageMatcher.match(
            gpsPoint = jumpGps,
            gpsAccuracy = 5f,
            routeData = route,
            previousResult = prev
        )

        // Sudden jump (> 250m) detected:
        assertTrue("isUncertain should be flagged", match.isUncertain)
        assertEquals(MatchConfidence.LOW, match.confidence)
        // Should retain last reliable chainage rather than jumping silently
        assertEquals(100100L, match.chainage?.metres)
    }

    @Test
    fun testStrictDataIntegrityCoordinatesNeverShiftOnCalibration() {
        val route = createSampleAlignmentRoute()
        val originalGps = GeoPoint(28.0050, 77.0000, 185.5)
        val origLat = originalGps.latitude
        val origLng = originalGps.longitude
        val origAlt = originalGps.altitude

        // Control point: at raw 100+500, calibrate to 100+580 (+80m shift)
        val controlPoint = ChainageCorrectionPoint(
            id = "cp-1",
            routeId = route.id,
            latitude = origLat,
            longitude = origLng,
            originalKmzMetres = 100500L,
            correctedMetres = 100580L,
            isEnabled = true
        )

        val result = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(100500L),
            routeData = route,
            correctionPoints = listOf(controlPoint),
            confidence = MatchConfidence.HIGH
        )

        assertNotNull(result)
        assertEquals(100580L, result?.correctedChainage?.metres)
        assertEquals(100500L, result?.originalKmzChainage?.metres)
        assertEquals(80L, result?.appliedOffsetMetres)

        // Strict Requirement: GPS and Alignment coordinates must be 100% unchanged
        assertEquals(origLat, originalGps.latitude, 0.0)
        assertEquals(origLng, originalGps.longitude, 0.0)
        assertEquals(origAlt, originalGps.altitude, 0.0)
        assertEquals(28.0000, route.alignments[0].points[0].latitude, 0.0)
    }

    @Test
    fun testAlignmentDiagnosticsDetectsDuplicatesAndInversions() {
        val p1 = GeoPoint(28.00, 77.00)
        val p2 = GeoPoint(28.01, 77.00)
        val p3 = GeoPoint(28.02, 77.00)

        // Marker 2 has duplicate chainage of Marker 1, and Marker 3 is inverted
        val m1 = RouteMarker("m1", "10+000", Chainage(10000L), p1)
        val m2 = RouteMarker("m2", "10+000", Chainage(10000L), GeoPoint(28.0001, 77.0001)) // duplicate
        val m3 = RouteMarker("m3", "09+500", Chainage(9500L), p3) // inversion

        val route = RouteData(
            id = "flawed-route",
            name = "Flawed Route",
            fileName = "flawed.kmz",
            markers = listOf(m1, m2, m3),
            alignments = listOf(RouteAlignment("a1", "A1", listOf(p1, p2, p3))),
            bounds = GeoBounds.fromPoints(listOf(p1, p2, p3)),
            startChainage = Chainage(10000L),
            endChainage = Chainage(9500L)
        )

        val diagnostics = SmartChainageCorrectionEngine.performAlignmentAudit(route)

        assertTrue(diagnostics.duplicateMarkersCount >= 1)
        assertTrue(diagnostics.inversionCount >= 1)
        assertTrue(diagnostics.qualityScorePercent < 90)
    }

    @Test
    fun testKmzParserExtractsExtendedDataAndMultiGeometry() {
        val sampleKml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>NH-48 ExtendedData Test</name>
                <Placemark>
                  <name>KM Milestone</name>
                  <ExtendedData>
                    <Data name="Chainage">
                      <value>198+500</value>
                    </Data>
                  </ExtendedData>
                  <MultiGeometry>
                    <Point>
                      <coordinates>77.1000,28.5000,210.0</coordinates>
                    </Point>
                  </MultiGeometry>
                </Placemark>
                <Placemark>
                  <name>NH-48 LHS Carriageway</name>
                  <MultiGeometry>
                    <LineString>
                      <coordinates>
                        77.1000,28.5000,210.0
                        77.1000,28.5100,210.0
                      </coordinates>
                    </LineString>
                  </MultiGeometry>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val route = KmzParser.parseKmlStream(ByteArrayInputStream(sampleKml.toByteArray()), "test_extended.kml")

        assertEquals("NH-48 ExtendedData Test", route.name)
        assertEquals(1, route.markers.size)
        assertEquals(198500L, route.markers[0].chainage.metres)
        assertEquals(1, route.alignments.size)
        assertEquals("LHS", route.alignments[0].carriagewayType)
    }
}
