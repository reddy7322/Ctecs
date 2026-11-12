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

    @Test
    fun testKalmanFilterStabilizesGpsChainageJitter() {
        val tracker = com.example.geo.ChainageProgressionTracker()
        val basePoint = GeoPoint(28.5000, 77.1000)

        // Initial reading at 205+174 with 7m GPS accuracy
        val r0 = ChainageMatchResult(
            chainage = Chainage(205174L),
            projectedPoint = basePoint,
            gpsAccuracy = 7.0f,
            confidence = MatchConfidence.MEDIUM,
            detectedSide = "LHS",
            source = ChainageSource.CUSTOM_CORRECTION,
            rawUncorrectedChainage = Chainage(205217L)
        )
        val out0 = tracker.processResult(r0, "route-1")
        assertEquals(205174L, out0.chainage?.metres)
        assertEquals(205217L, out0.rawUncorrectedChainage?.metres)

        // Simulate stationary GPS jitter (±1m to ±2m oscillation around basePoint)
        val jitterSamples = listOf(205175L, 205173L, 205176L, 205174L, 205173L, 205175L)
        for (noisyMetres in jitterSamples) {
            val noisyResult = r0.copy(
                chainage = Chainage(noisyMetres),
                rawUncorrectedChainage = Chainage(noisyMetres + 43L)
            )
            val stabilized = tracker.processResult(noisyResult, "route-1")
            // Stationary deadband + Kalman filter keeps value rock-solid at 205+174
            assertEquals(205174L, stabilized.chainage?.metres)
            assertEquals("205+174", stabilized.displayChainage)
        }

        // Now simulate a genuine calibration or jump (+100m to 205+274): must snap immediately without lag
        val jumpedResult = r0.copy(
            chainage = Chainage(205274L),
            rawUncorrectedChainage = Chainage(205317L)
        )
        val jumpedOut = tracker.processResult(jumpedResult, "route-1")
        assertEquals(205274L, jumpedOut.chainage?.metres)
    }

    @Test
    fun testKmzParserExtractsPathCoordinatesForBaselineChainage() {
        // KML containing ONLY a LineString path (no Point markers) starting at 150+000
        val pathOnlyKml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>NH-44 Highway Path 150+000</name>
                <Placemark>
                  <name>Main Alignment 150+000</name>
                  <LineString>
                    <coordinates>
                      77.0000,28.0000,200.0
                      77.0000,28.0090,200.0
                      77.0000,28.0180,200.0
                    </coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val route = KmzParser.parseKmlStream(ByteArrayInputStream(pathOnlyKml.toByteArray()), "path_only.kml")

        // Verify path coordinates were extracted and baseline markers were generated along the ~2000m path
        val extractedCoords = KmzParser.extractPathCoordinates(route)
        assertEquals(3, extractedCoords.size)
        assertTrue("Baseline markers should be synthesized along the path", route.markers.size >= 15)
        assertEquals(150000L, route.startChainage?.metres)
        assertTrue("End chainage should be ~152+000", (route.endChainage?.metres ?: 0L) in 151950L..152050L)

        // Match a GPS point midway along the first segment (latitude 28.0045 -> ~500m from start)
        val midGps = GeoPoint(28.0045, 77.00005, 200.0)
        val match = ChainageMatcher.match(gpsPoint = midGps, gpsAccuracy = 4.0f, routeData = route)
        assertEquals(MatchConfidence.HIGH, match.confidence)
        val matchedMetres = match.chainage?.metres ?: 0L
        assertTrue("Midpoint chainage on path-only KMZ should be ~150+500, got $matchedMetres", matchedMetres in 150475L..150525L)

        // Verify calibration system works seamlessly on top of the extracted path baseline chainage
        val cp = ChainageCorrectionPoint(
            routeId = route.id,
            latitude = midGps.latitude,
            longitude = midGps.longitude,
            originalKmzMetres = matchedMetres,
            correctedMetres = 150600L,
            isEnabled = true
        )
        val calibrated = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = match.chainage!!,
            routeData = route,
            correctionPoints = listOf(cp),
            confidence = match.confidence
        )
        assertNotNull(calibrated)
        assertEquals(150600L, calibrated?.correctedChainage?.metres)
    }

    @Test
    fun testIndianHighwayChainageFormats() {
        assertEquals(198000L, Chainage.parse("198/0")?.metres)
        assertEquals(198200L, Chainage.parse("198/200")?.metres)
        assertEquals(198200L, Chainage.parse("198/2")?.metres)
        assertEquals(198400L, Chainage.parse("198/4")?.metres)
        assertEquals(198600L, Chainage.parse("KM 198/6")?.metres)
        assertEquals(198400L, Chainage.parse("KM 198/400")?.metres)
        assertEquals(198880L, Chainage.parse("CH: 198+880.40")?.metres)
        assertEquals(198880L, Chainage.parse("198.88 km")?.metres)
        assertEquals(198880L, Chainage.parse("198880 m")?.metres)
        assertEquals(198000L, Chainage.parse("STA 198+00")?.metres)
        assertEquals(198500L, Chainage.parse("<td><b>Chainage:</b> 198+500</td>")?.metres)
    }

    @Test
    fun testKmlWithNamespaces() {
        val namespacedKml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml:kml xmlns:kml="http://www.opengis.net/kml/2.2">
              <kml:Document>
                <kml:name>Namespaced Highway</kml:name>
                <kml:Placemark>
                  <kml:name>CH 10+000</kml:name>
                  <kml:Point>
                    <kml:coordinates>77.1000,28.5000,200.0</kml:coordinates>
                  </kml:Point>
                </kml:Placemark>
                <kml:Placemark>
                  <kml:name>Alignment Centerline</kml:name>
                  <kml:LineString>
                    <kml:coordinates>
                      77.1000,28.5000,200.0
                      77.1000,28.5100,200.0
                    </kml:coordinates>
                  </kml:LineString>
                </kml:Placemark>
              </kml:Document>
            </kml:kml>
        """.trimIndent()

        val route = KmzParser.parseKmlStream(ByteArrayInputStream(namespacedKml.toByteArray()), "namespaced.kml")
        assertEquals("Namespaced Highway", route.name)
        assertTrue(route.markers.isNotEmpty())
        assertEquals(10000L, route.markers[0].chainage.metres)
        assertEquals(1, route.alignments.size)
        assertEquals(2, route.alignments[0].points.size)
    }

    @Test
    fun testKmlWithSpaceDelimitedCoordinates() {
        val spaceKml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>Space Delimited Alignment</name>
                <Placemark>
                  <name>Alignment 0+000</name>
                  <LineString>
                    <coordinates>
                      77.1000 28.5000 200.0
                      77.1000 28.5100 200.0
                    </coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val route = KmzParser.parseKmlStream(ByteArrayInputStream(spaceKml.toByteArray()), "space.kml")
        assertEquals(1, route.alignments.size)
        assertEquals(2, route.alignments[0].points.size)
    }

    @Test
    fun testKmlWithContinuousCommaDelimitedCoordinates() {
        val continuousKml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>Continuous Comma Alignment</name>
                <Placemark>
                  <name>Continuous Alignment</name>
                  <LineString>
                    <coordinates>77.1,28.5,200.0,77.1,28.51,200.0,77.1,28.52,200.0</coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val route = KmzParser.parseKmlStream(ByteArrayInputStream(continuousKml.toByteArray()), "continuous.kml")
        assertEquals(1, route.alignments.size)
        assertEquals(3, route.alignments[0].points.size)
    }

    @Test
    fun testKmlWithUnclassifiedPointsFallback() {
        val unclassifiedKml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>Waypoint Survey</name>
                <Placemark>
                  <name>Survey Station Alpha</name>
                  <Point>
                    <coordinates>77.1000,28.5000,0</coordinates>
                  </Point>
                </Placemark>
                <Placemark>
                  <name>Survey Station Beta</name>
                  <Point>
                    <coordinates>77.1000,28.5100,0</coordinates>
                  </Point>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val route = KmzParser.parseKmlStream(ByteArrayInputStream(unclassifiedKml.toByteArray()), "waypoints.kml")
        assertEquals("Waypoint Survey", route.name)
        assertEquals(2, route.markers.size)
        assertEquals(1, route.alignments.size)
    }

    @Test
    fun testKmzZipArchiveParsingViaFile() {
        val kmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>Zipped KMZ Route</name>
                <Placemark>
                  <name>0+000 Start</name>
                  <Point>
                    <coordinates>77.1000,28.5000,100.0</coordinates>
                  </Point>
                </Placemark>
                <Placemark>
                  <name>1+000 Milestone</name>
                  <Point>
                    <coordinates>77.1000,28.5090,100.0</coordinates>
                  </Point>
                </Placemark>
                <Placemark>
                  <name>Main Highway Polyline</name>
                  <LineString>
                    <coordinates>
                      77.1000,28.5000,100.0
                      77.1000,28.5090,100.0
                    </coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val tempKmz = java.io.File.createTempFile("test_route_", ".kmz")
        try {
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(tempKmz)).use { zos ->
                val entry = java.util.zip.ZipEntry("doc.kml")
                zos.putNextEntry(entry)
                zos.write(kmlContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // Test parsing via File
            val routeFromFile = KmzParser.parse(tempKmz)
            assertEquals("Zipped KMZ Route", routeFromFile.name)
            assertEquals(2, routeFromFile.markers.size)
            assertEquals(0L, routeFromFile.markers[0].chainage.metres)
            assertEquals(1000L, routeFromFile.markers[1].chainage.metres)
            assertEquals(1, routeFromFile.alignments.size)

            // Test parsing via InputStream
            val routeFromStream = tempKmz.inputStream().use { input ->
                KmzParser.parse(input, "test_route.kmz")
            }
            assertEquals("Zipped KMZ Route", routeFromStream.name)
            assertEquals(2, routeFromStream.markers.size)
        } finally {
            tempKmz.delete()
        }
    }

    @Test
    fun testReferenceMuzzafarnagarHaridwarNh58KmzStructureCompatibility() {
        val sb = StringBuilder(512 * 1024)
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2">""").append('\n')
        sb.append("""  <Folder>""").append('\n')
        sb.append("""    <name>Model</name>""").append('\n')

        // Nested Folder 1: Chainage Point Placemarks (1,599 markers: 130+000 to 209+850 step 50m, then 209+883)
        sb.append("""    <Folder>""").append('\n')
        sb.append("""      <name>Chainage_Markers</name>""").append('\n')

        val baseLat = 29.470000
        val baseLng = 77.700000
        val degPerMetreLat = 1.0 / 111132.954

        val chainageValues = ArrayList<Long>(1599)
        var chM = 130000L
        while (chM <= 209850L) {
            chainageValues.add(chM)
            chM += 50L
        }
        chainageValues.add(209883L) // Irregular 33 m interval at the end
        assertEquals(1599, chainageValues.size)

        for (metres in chainageValues) {
            val offsetFromStartM = (metres - 130000L).toDouble()
            val lat = baseLat + offsetFromStartM * degPerMetreLat
            val label = Chainage(metres).format()
            sb.append("""      <Placemark><name>$label</name><Point><coordinates>$baseLng,$lat,245.0</coordinates></Point></Placemark>""").append('\n')
        }
        sb.append("""    </Folder>""").append('\n')

        // Nested Folder 2: Alignment LineStrings & MultiGeometry features totaling 3,774 LineStrings
        sb.append("""    <Folder>""").append('\n')
        sb.append("""      <name>Alignment_Geometry</name>""").append('\n')

        var lineStringsEmitted = 0
        for (i in 0 until chainageValues.size - 1) {
            val m1 = chainageValues[i]
            val m2 = chainageValues[i + 1]
            val lat1 = baseLat + (m1 - 130000L) * degPerMetreLat
            val lat2 = baseLat + (m2 - 130000L) * degPerMetreLat
            if (i % 10 == 0 && i + 1 < chainageValues.size - 1) {
                val m3 = chainageValues[i + 2]
                val lat3 = baseLat + (m3 - 130000L) * degPerMetreLat
                sb.append("""      <Placemark><name>Align_$i</name><MultiGeometry>""")
                sb.append("""<LineString><coordinates>$baseLng,$lat1,245.0 $baseLng,$lat2,245.0</coordinates></LineString>""")
                sb.append("""<LineString><coordinates>$baseLng,$lat2,245.0 $baseLng,$lat3,245.0</coordinates></LineString>""")
                sb.append("""</MultiGeometry></Placemark>""").append('\n')
                lineStringsEmitted += 2
            } else {
                sb.append("""      <Placemark><name>Seg_$i</name><LineString><coordinates>$baseLng,$lat1,245.0 $baseLng,$lat2,245.0</coordinates></LineString></Placemark>""").append('\n')
                lineStringsEmitted += 1
            }
        }

        val remaining = 3774 - lineStringsEmitted
        for (k in 0 until remaining) {
            val stationM = 130000L + (k % 1598) * 50L
            val lat = baseLat + (stationM - 130000L) * degPerMetreLat
            val lngLeft = baseLng - 0.00004
            val lngRight = baseLng + 0.00004
            sb.append("""      <Placemark><LineString><coordinates>$lngLeft,$lat,245.0 $lngRight,$lat,245.0</coordinates></LineString></Placemark>""").append('\n')
            lineStringsEmitted++
        }
        assertEquals(3774, lineStringsEmitted)

        sb.append("""    </Folder>""").append('\n')
        sb.append("""  </Folder>""").append('\n')
        sb.append("""</kml>""").append('\n')

        val tempKmz = java.io.File.createTempFile("Muzzafarnagar-Haridwar-NH-58-50m", ".kmz")
        try {
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(tempKmz)).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("doc.kml"))
                zos.write(sb.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            ChainageMatcher.clearCache()
            val route = KmzParser.parse(tempKmz, "Muzzafarnagar-Haridwar-NH-58-50m.kmz")

            assertEquals("Muzzafarnagar-Haridwar-NH-58-50m.kmz", route.fileName)
            assertEquals("Muzzafarnagar-Haridwar-NH-58-50m", route.name)

            assertEquals(1599, route.markers.size)
            assertEquals(130000L, route.startChainage?.metres)
            assertEquals("130+000", route.startChainage?.format())
            assertEquals(209883L, route.endChainage?.metres)
            assertEquals("209+883", route.endChainage?.format())

            assertEquals(50L, route.markers[1].chainage.metres - route.markers[0].chainage.metres)
            assertEquals(209850L, route.markers[1597].chainage.metres)
            assertEquals(209883L, route.markers[1598].chainage.metres)
            assertEquals(33L, route.markers[1598].chainage.metres - route.markers[1597].chainage.metres)

            assertNotNull(route.diagnostics)
            assertEquals(1599, route.diagnostics?.rawPointCount)
            assertEquals(3774, route.diagnostics?.rawLineStringCount)
            assertTrue((route.diagnostics?.multiGeometryCount ?: 0) > 0)

            val targetMetres = 196040L
            val gpsAt196040 = GeoPoint(
                latitude = baseLat + (targetMetres - 130000L) * degPerMetreLat,
                longitude = baseLng + 0.00002,
                altitude = 245.0
            )
            val match196040 = ChainageMatcher.match(
                gpsPoint = gpsAt196040,
                gpsAccuracy = 3.5f,
                routeData = route
            )
            assertEquals(MatchConfidence.HIGH, match196040.confidence)
            val matchedM = match196040.chainage?.metres ?: 0L
            assertTrue("Expected ~196040m, got $matchedM", matchedM in 196038L..196042L)

            val gpsAtEnd = GeoPoint(
                latitude = baseLat + (209883L - 130000L) * degPerMetreLat,
                longitude = baseLng,
                altitude = 245.0
            )
            val matchEnd = ChainageMatcher.match(
                gpsPoint = gpsAtEnd,
                gpsAccuracy = 3.0f,
                routeData = route
            )
            assertEquals(209883L, matchEnd.chainage?.metres)
            assertEquals("209+883", matchEnd.chainage?.format())

            val calPoint1 = ChainageCorrectionPoint(
                id = "cal-1",
                routeId = route.id,
                latitude = gpsAt196040.latitude,
                longitude = gpsAt196040.longitude,
                originalKmzMetres = 196040L,
                correctedMetres = 196100L,
                isEnabled = true
            )
            val calResult1 = SmartChainageCorrectionEngine.applyCorrection(
                rawChainage = match196040.chainage!!,
                routeData = route,
                correctionPoints = listOf(calPoint1),
                confidence = match196040.confidence,
                distanceAlongAlignmentMetres = match196040.distanceAlongAlignmentMetres
            )
            assertNotNull(calResult1)
            assertEquals(60L, calResult1?.appliedOffsetMetres)
            assertTrue((calResult1?.correctedChainage?.metres ?: 0L) in 196098L..196102L)

            val calPointRecalibrated = calPoint1.copy(correctedMetres = 196020L)
            val calResult2 = SmartChainageCorrectionEngine.applyCorrection(
                rawChainage = match196040.chainage!!,
                routeData = route,
                correctionPoints = listOf(calPointRecalibrated),
                confidence = match196040.confidence,
                distanceAlongAlignmentMetres = match196040.distanceAlongAlignmentMetres
            )
            assertNotNull(calResult2)
            assertEquals(-20L, calResult2?.appliedOffsetMetres)
            assertTrue((calResult2?.correctedChainage?.metres ?: 0L) in 196018L..196022L)
        } finally {
            tempKmz.delete()
        }
    }

    @Test
    fun testKmzCaseInsensitiveSubfolderAndSecurityEdgeCases() {
        val validKml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Folder>
                <Placemark><name>130+000</name><Point><coordinates>77.7000,29.4700,0</coordinates></Point></Placemark>
                <Placemark><name>130+050</name><Point><coordinates>77.7000,29.47045,0</coordinates></Point></Placemark>
              </Folder>
            </kml>
        """.trimIndent()

        val subfolderKmz = java.io.File.createTempFile("subfolder_doc_", ".kmz")
        try {
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(subfolderKmz)).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("files/SubFolder/DOC.KML"))
                zos.write(validKml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            val parsed = KmzParser.parse(subfolderKmz, "subfolder.kmz")
            assertEquals(2, parsed.markers.size)
            assertEquals("files/SubFolder/DOC.KML", parsed.diagnostics?.selectedKmlEntryName)
        } finally {
            subfolderKmz.delete()
        }

        assertFalse(KmzParser.isSafeZipEntryName("../evil.kml"))
        assertFalse(KmzParser.isSafeZipEntryName("folder/../../evil.kml"))
        assertFalse(KmzParser.isSafeZipEntryName("/etc/passwd"))
        assertTrue(KmzParser.isSafeZipEntryName("doc.kml"))
        assertTrue(KmzParser.isSafeZipEntryName("subfolder/alignment.kml"))

        val noKmlZip = java.io.File.createTempFile("no_kml_", ".kmz")
        try {
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(noKmlZip)).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
                zos.write("not a kml".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            try {
                KmzParser.parse(noKmlZip, "no_kml.kmz")
                fail("Expected RouteParseException for KMZ without .kml entry")
            } catch (e: com.example.parser.RouteParseException) {
                assertTrue(e.message.orEmpty().contains("No valid KML", ignoreCase = true))
            }
        } finally {
            noKmlZip.delete()
        }

        val emptyGeometryKml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document><name>Empty Route</name></Document>
            </kml>
        """.trimIndent()
        try {
            KmzParser.parseKmlBytes(emptyGeometryKml.toByteArray(Charsets.UTF_8), "empty_geom.kml")
            fail("Expected RouteParseException for valid KML with no geometry")
        } catch (e: com.example.parser.RouteParseException) {
            assertTrue(e.message.orEmpty().contains("no supported route geometry", ignoreCase = true))
        }
    }
}


