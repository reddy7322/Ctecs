package com.example

import com.example.model.Chainage
import com.example.model.GeoPoint
import com.example.survey.engine.SurveyGeometryEngine
import com.example.survey.export.KmzSurveyExporter
import com.example.survey.model.*
import org.junit.Assert.*
import org.junit.Test

class HighwaySurveyEngineTest {

    @Test
    fun testParseAndFormatChainage() {
        assertEquals(0L, Chainage.parse("0+000")?.metres ?: 0L)
        assertEquals(12450L, Chainage.parse("12+450")?.metres ?: 0L)
        assertEquals(198750L, Chainage.parse("198+750")?.metres ?: 0L)
        assertEquals("12+450", Chainage(12450L).format())
        assertEquals("0+000", Chainage(0L).format())
        assertEquals("198+750", Chainage(198750L).format())
    }

    @Test
    fun testChainageProgressionIncreasingAndDecreasing() {
        // Increasing direction
        val chInc = SurveyGeometryEngine.calculateChainageMetres(
            startChainageMetres = 10000L,
            distanceTravelledM = 500.0,
            direction = SurveyDirection.INCREASING,
            calibrations = emptyList()
        )
        assertEquals(10500L, chInc)

        // Decreasing direction
        val chDec = SurveyGeometryEngine.calculateChainageMetres(
            startChainageMetres = 10000L,
            distanceTravelledM = 500.0,
            direction = SurveyDirection.DECREASING,
            calibrations = emptyList()
        )
        assertEquals(9500L, chDec)
    }

    @Test
    fun testChainageWithCalibration() {
        val calibration = SurveyCalibrationPoint(
            sessionId = "test-session",
            markerId = "m-1",
            measuredDistanceMetres = 1000.0,
            knownChainageMetres = 11050L,
            driftMetres = 50.0 // +50m drift
        )

        // After calibration point (at 1500m travelled)
        val chCalibrated = SurveyGeometryEngine.calculateChainageMetres(
            startChainageMetres = 10000L,
            distanceTravelledM = 1500.0,
            direction = SurveyDirection.INCREASING,
            calibrations = listOf(calibration)
        )
        // 10000 + 1500 + 50 = 11550
        assertEquals(11550L, chCalibrated)
    }

    @Test
    fun testMovementReliabilityFilter() {
        val p1 = GeoPoint(29.8337, 77.8893)
        val p2Close = GeoPoint(29.8337001, 77.8893001) // < 1m
        val p2Good = GeoPoint(29.8338, 77.8894) // ~15m
        val p2Teleport = GeoPoint(30.8337, 78.8893) // ~100+ km

        // Stationary drift (speed = 0 and distance < 1.5m)
        assertFalse(SurveyGeometryEngine.isMovementReliable(p1, p2Close, accuracy = 5f, speedMps = 0.1f))

        // Normal movement
        assertTrue(SurveyGeometryEngine.isMovementReliable(p1, p2Good, accuracy = 5f, speedMps = 15f))

        // Poor accuracy (> 25m default)
        assertFalse(SurveyGeometryEngine.isMovementReliable(p1, p2Good, accuracy = 45f, speedMps = 15f))

        // Teleportation jump (> 150m)
        assertFalse(SurveyGeometryEngine.isMovementReliable(p1, p2Teleport, accuracy = 5f, speedMps = 15f))
    }

    @Test
    fun testCentrelineGenerationWithOffset() {
        val p1 = SurveyPoint(
            sessionId = "s1", segmentIndex = 0,
            latitude = 29.8000, longitude = 77.8000,
            calculatedChainageMetres = 0L
        )
        val p2 = SurveyPoint(
            sessionId = "s1", segmentIndex = 0,
            latitude = 29.8010, longitude = 77.8000, // Heading due North (bearing 0)
            calculatedChainageMetres = 111L
        )

        // Heading is 0 deg (North). If surveyed lane was on LEFT (-7m), centreline is on the RIGHT (+7m, East)
        val centreline = SurveyGeometryEngine.generateCentrelineTrack(
            points = listOf(p1, p2),
            offsetDistanceM = 7.0,
            offsetSide = OffsetSide.LEFT
        )

        assertEquals(2, centreline.size)
        // With lane on LEFT, centreline should be to the RIGHT (higher longitude)
        assertTrue(centreline[0].longitude > p1.longitude)
    }

    @Test
    fun testKmlExportStructure() {
        val session = SurveySession(
            id = "test-session-123",
            name = "Northbound MCW Survey",
            projectName = "NH-44 Expansion",
            highwayName = "NH-44",
            roadNumber = "44",
            stretchName = "Delhi-Ambala",
            surveyDate = "2026-11-10",
            lane = SurveyLane.MCW_FAST,
            centrelineOffsetMetres = 7.0,
            offsetSide = OffsetSide.LEFT,
            startChainageMetres = 168000L,
            currentChainageMetres = 170000L,
            totalDistanceTravelledMetres = 2000.0
        )

        val points = listOf(
            SurveyPoint(sessionId = session.id, segmentIndex = 0, latitude = 29.8337, longitude = 77.8893, calculatedChainageMetres = 168000L),
            SurveyPoint(sessionId = session.id, segmentIndex = 0, latitude = 29.8400, longitude = 77.8920, calculatedChainageMetres = 170000L)
        )

        val markers = listOf(
            SurveyMarker(
                sessionId = session.id,
                chainageMetres = 169000L,
                displayChainage = "169+000",
                latitude = 29.8368,
                longitude = 77.8906,
                markerType = KmMarkerType.FULL_KM,
                notes = "KM 169"
            )
        )

        val kml = KmzSurveyExporter.buildKmlString(session, points, markers, emptyList())

        // Validate KML contains key Google Earth folders
        assertTrue(kml.contains("<kml xmlns=\"http://www.opengis.net/kml/2.2\">"))
        assertTrue(kml.contains("<name>Highway Centreline</name>"))
        assertTrue(kml.contains("<name>Lane Alignment</name>"))
        assertTrue(kml.contains("Fast Lane Alignment"))
        assertTrue(kml.contains("<name>Original GPS Track</name>"))
        assertTrue(kml.contains("<name>KM Stone Markers</name>"))
        assertTrue(kml.contains("NH-44 Expansion"))
        assertTrue(kml.contains("169+000"))
        assertTrue(kml.contains("</kml>"))
    }
}
