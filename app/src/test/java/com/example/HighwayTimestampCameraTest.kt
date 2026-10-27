package com.example

import com.example.geo.ChainageMatcher
import com.example.geo.GeoUtils
import com.example.model.Chainage
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.parser.KmzParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileInputStream

class HighwayTimestampCameraTest {

    // 1. Parsing "196+040" into 196040 metres
    @Test
    fun testParseChainageStandard() {
        val ch = Chainage.parse("196+040")
        assertNotNull(ch)
        assertEquals(196040L, ch!!.metres)
        assertEquals("196+040", ch.format())
    }

    // 2. Parsing "130+000", "130+050" and "209+883"
    @Test
    fun testParseChainageRange() {
        val ch1 = Chainage.parse("130+000")
        assertNotNull(ch1)
        assertEquals(130000L, ch1!!.metres)
        assertEquals("130+000", ch1.format())

        val ch2 = Chainage.parse("130+050")
        assertNotNull(ch2)
        assertEquals(130050L, ch2!!.metres)
        assertEquals("130+050", ch2.format())

        val ch3 = Chainage.parse("209+883")
        assertNotNull(ch3)
        assertEquals(209883L, ch3!!.metres)
        assertEquals("209+883", ch3.format())

        // Test prefix variants: "CH: 196+040", "KM 196+040"
        val chPrefixed = Chainage.parse("CH: 196+040")
        assertNotNull(chPrefixed)
        assertEquals(196040L, chPrefixed!!.metres)
    }

    // 3. Rejecting invalid chainage strings
    @Test
    fun testRejectInvalidChainage() {
        assertNull(Chainage.parse(null))
        assertNull(Chainage.parse(""))
        assertNull(Chainage.parse("invalid"))
        assertNull(Chainage.parse("+"))
        assertNull(Chainage.parse("100+"))
        assertNull(Chainage.parse("abc+def"))
    }

    // 4. KML XML Parsing: Point Placemarks, LineString and nested folders
    @Test
    fun testParseKmlPlacemarksAndAlignments() {
        val sampleKml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>Test Highway Section</name>
                <Folder>
                  <name>Centerline</name>
                  <Placemark>
                    <name>Main Alignment</name>
                    <LineString>
                      <coordinates>
                        78.1000,29.9100,0 78.1100,29.9200,0 78.1200,29.9300,0
                      </coordinates>
                    </LineString>
                  </Placemark>
                </Folder>
                <Folder>
                  <name>Markers</name>
                  <Placemark>
                    <name>196+000</name>
                    <Point>
                      <coordinates>78.1060,29.9180,0</coordinates>
                    </Point>
                  </Placemark>
                  <Placemark>
                    <name>196+040</name>
                    <Point>
                      <coordinates>78.1064,29.9183,0</coordinates>
                    </Point>
                  </Placemark>
                  <Placemark>
                    <name>196+100</name>
                    <Point>
                      <coordinates>78.1070,29.9188,0</coordinates>
                    </Point>
                  </Placemark>
                </Folder>
              </Document>
            </kml>
        """.trimIndent()

        val routeData = KmzParser.parseKmlStream(ByteArrayInputStream(sampleKml.toByteArray()), "test.kml")

        assertEquals("Test Highway Section", routeData.name)
        assertEquals(3, routeData.markers.size)
        assertEquals(1, routeData.alignments.size)
        assertEquals(3, routeData.alignments[0].points.size)
        assertEquals(196000L, routeData.startChainage?.metres)
        assertEquals(196100L, routeData.endChainage?.metres)
    }

    // 5. Chainage Matching: GPS position close to a known marker (high confidence)
    @Test
    fun testMatchCloseToMarker() {
        val marker1 = RouteMarker("1", "196+000", Chainage(196000), GeoPoint(29.9180, 78.1060))
        val marker2 = RouteMarker("2", "196+040", Chainage(196040), GeoPoint(29.9183, 78.1064))
        val marker3 = RouteMarker("3", "196+100", Chainage(196100), GeoPoint(29.9188, 78.1070))

        val alignment = RouteAlignment("a1", "Road", listOf(marker1.point, marker2.point, marker3.point))
        val routeData = RouteData(
            id = "test-route",
            name = "Test Route",
            fileName = "test.kml",
            markers = listOf(marker1, marker2, marker3),
            alignments = listOf(alignment),
            bounds = GeoBounds(29.9180, 29.9188, 78.1060, 78.1070),
            startChainage = marker1.chainage,
            endChainage = marker3.chainage
        )

        // GPS fix exactly at marker 2 (196+040) with 2m accuracy
        val gps = GeoPoint(29.9183, 78.1064)
        val result = ChainageMatcher.match(gps, 2.0f, null, routeData)

        assertEquals(MatchConfidence.HIGH, result.confidence)
        assertEquals(196040L, result.chainage?.metres)
        assertEquals("196+040", result.displayChainage)
        assertTrue("Offset should be under 5m", result.offsetMetres < 5.0)
    }

    // 6. Chainage Interpolation between two markers
    @Test
    fun testInterpolateChainageBetweenMarkers() {
        // Two markers 100m apart
        val m1 = RouteMarker("1", "100+000", Chainage(100000), GeoPoint(29.0000, 77.0000))
        val m2 = RouteMarker("2", "100+100", Chainage(100100), GeoPoint(29.0010, 77.0000))

        val alignment = RouteAlignment("a1", "Road", listOf(m1.point, m2.point))
        val routeData = RouteData(
            id = "test-route",
            name = "Test Route",
            fileName = "test.kml",
            markers = listOf(m1, m2),
            alignments = listOf(alignment),
            bounds = GeoBounds(29.0000, 29.0010, 77.0000, 77.0000),
            startChainage = m1.chainage,
            endChainage = m2.chainage
        )

        // Midpoint between m1 and m2
        val midGps = GeoPoint(29.0005, 77.0000)
        val result = ChainageMatcher.match(midGps, 2.0f, null, routeData)

        assertEquals(MatchConfidence.HIGH, result.confidence)
        // Midpoint should interpolate to ~100+050 (+/- 5m)
        val metres = result.chainage?.metres ?: 0L
        assertTrue("Interpolated chainage should be ~100050, got $metres", Math.abs(metres - 100050L) <= 5L)
    }

    // 7. Rejecting GPS positions far from the alignment
    @Test
    fun testRejectFarGpsPosition() {
        val m1 = RouteMarker("1", "196+000", Chainage(196000), GeoPoint(29.9180, 78.1060))
        val routeData = RouteData(
            id = "test-route",
            name = "Test Route",
            fileName = "test.kml",
            markers = listOf(m1),
            alignments = emptyList(),
            bounds = GeoBounds(29.9180, 29.9180, 78.1060, 78.1060),
            startChainage = m1.chainage,
            endChainage = m1.chainage
        )

        // Point 5 kilometres away
        val farGps = GeoPoint(29.9600, 78.1500)
        val result = ChainageMatcher.match(farGps, 3.0f, null, routeData)

        assertEquals(MatchConfidence.NO_MATCH, result.confidence)
        assertTrue(result.offsetMetres > 100.0)
    }

    // 8. GPS Coordinate & Timestamp Formatting
    @Test
    fun testCoordinateAndTimestampFormatting() {
        val point = GeoPoint(29.918345, 78.106421)
        val refFmt = point.formatReference(4)
        assertEquals("29.9183N 78.1064E", refFmt)

        val project = ProjectProfile(
            highwayId = "MH",
            packageSection = "MM",
            roadSide = RoadSide.RHS,
            lane = "I/L",
            activity = "DBM",
            separator = "||"
        )
        val line3 = project.buildHighwayText("196+040")
        assertEquals("MH || MM || 196+040 RHS I/L DBM", line3)
    }

    // 9. Validating the generated sample NH-58 KMZ and counting chainage markers
    @Test
    fun testBundledKmzFileParsing() {
        val kmzFile = listOf(
            java.io.File("src/main/assets/sample_nh58.kmz"),
            java.io.File("app/src/main/assets/sample_nh58.kmz")
        ).firstOrNull { it.exists() }
        assertNotNull("sample_nh58.kmz must exist in assets", kmzFile)

        val routeData = kmzFile!!.inputStream().use { input ->
            KmzParser.parse(input, "sample_nh58.kmz")
        }

        // Check markers count (1600 markers generated for 130+000 to 209+883 at 50m intervals)
        assertEquals(1600, routeData.markers.size)
        assertEquals(130000L, routeData.startChainage?.metres)
        assertEquals(209883L, routeData.endChainage?.metres)

        // Verify reference photo location (29.9183, 78.1064) matches 196+040
        val refGps = GeoPoint(29.9183, 78.1064)
        val matchResult = ChainageMatcher.match(refGps, 2.0f, null, routeData)

        assertEquals(MatchConfidence.HIGH, matchResult.confidence)
        val matchedMetres = matchResult.chainage?.metres ?: 0L
        assertTrue(
            "Matched chainage should be ~196040 (+/- 15m), got $matchedMetres",
            Math.abs(matchedMetres - 196040L) <= 15L
        )
    }

    // 9b. Validating KMZ parsing when filename lacks .kmz extension (e.g. Android SAF content URI)
    @Test
    fun testKmzParsingWithoutKmzExtension() {
        val kmzFile = listOf(
            java.io.File("src/main/assets/sample_nh58.kmz"),
            java.io.File("app/src/main/assets/sample_nh58.kmz")
        ).firstOrNull { it.exists() }
        assertNotNull("sample_nh58.kmz must exist in assets", kmzFile)

        // Pass a name without .kmz (simulating raw SAF ID: 1000868329)
        val routeData = kmzFile!!.inputStream().use { input ->
            KmzParser.parse(input, "1000868329")
        }

        assertEquals(1600, routeData.markers.size)
        assertEquals(130000L, routeData.startChainage?.metres)
        assertEquals(209883L, routeData.endChainage?.metres)
    }

    // 10. Testing Smart Inbuilt Update Semantic Versioning
    @Test
    fun testSemanticVersionComparison() {
        // Newer patch version
        assertTrue(com.example.update.AppUpdateManager.isNewerVersion("1.0.1", "1.0.0"))
        // Newer minor version
        assertTrue(com.example.update.AppUpdateManager.isNewerVersion("1.1.0", "1.0.9"))
        // Newer major version
        assertTrue(com.example.update.AppUpdateManager.isNewerVersion("2.0.0", "1.9.9"))
        // Multi-digit numbers
        assertTrue(com.example.update.AppUpdateManager.isNewerVersion("1.10.0", "1.9.0"))

        // Same version
        org.junit.Assert.assertFalse(com.example.update.AppUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        // Older remote version
        org.junit.Assert.assertFalse(com.example.update.AppUpdateManager.isNewerVersion("0.9.0", "1.0.0"))
        org.junit.Assert.assertFalse(com.example.update.AppUpdateManager.isNewerVersion("1.0.1", "1.0.2"))
    }

    // 11. Testing Dynamic What's New parsing from release changelog
    @Test
    fun testReleaseNotesParsing() {
        val changelog = """
            ## Ctecs v1.1.0 Release
            - **High Precision GPS**: Enhanced Kalman filter for 10Hz RTK GPS antennas.
            - **Export DXF**: Direct AutoCAD DXF alignment overlay export.
            - Bug fix: Resolved camera shutter vibration delay.
        """.trimIndent()

        val features = com.example.update.WhatsNewManager.parseReleaseNotesToFeatures(changelog)
        assertEquals(3, features.size)
        assertEquals("High Precision GPS", features[0].title)
        assertEquals("Enhanced Kalman filter for 10Hz RTK GPS antennas.", features[0].description)
        assertEquals("Export DXF", features[1].title)
        assertEquals("Direct AutoCAD DXF alignment overlay export.", features[1].description)
        assertEquals("Bug fix", features[2].title)

        // Empty notes fallback
        val emptyFeatures = com.example.update.WhatsNewManager.parseReleaseNotesToFeatures("")
        assertTrue(emptyFeatures.isNotEmpty())
        assertEquals("Automatic Highway Chainage", emptyFeatures[0].title)
    }

    // 12. Testing Logo configuration enums and fallbacks
    @Test
    fun testLogoConfigurationEnums() {
        assertEquals(com.example.model.LogoPosition.TOP_RIGHT, com.example.model.LogoPosition.fromString("TOP_RIGHT"))
        assertEquals(com.example.model.LogoPosition.TOP_LEFT, com.example.model.LogoPosition.fromString("top_left"))
        assertEquals(com.example.model.LogoPosition.ABOVE_TEXT, com.example.model.LogoPosition.fromString("ABOVE_TEXT"))
        assertEquals(com.example.model.LogoPosition.TOP_RIGHT, com.example.model.LogoPosition.fromString("unknown_position"))

        assertEquals(com.example.model.LogoShape.ROUNDED_CORNER, com.example.model.LogoShape.fromString("ROUNDED_CORNER"))
        assertEquals(com.example.model.LogoShape.CIRCLE, com.example.model.LogoShape.fromString("circle"))
        assertEquals(com.example.model.LogoShape.ORIGINAL, com.example.model.LogoShape.fromString("invalid"))

        val config = com.example.model.OverlayConfig()
        assertTrue(config.showLogo)
        assertEquals("cubetech", config.logoPreset)
        assertEquals(com.example.model.LogoPosition.TOP_RIGHT, config.logoPosition)
        assertEquals(58, config.logoSizeDp)
        assertEquals(0.90f, config.logoOpacity, 0.001f)
    }
}

