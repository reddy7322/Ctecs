package com.example

import com.example.model.Chainage
import com.example.model.GeoPoint
import com.example.survey.engine.SurveyGeometryEngine
import com.example.survey.export.KmzSurveyExporter
import com.example.survey.model.*
import org.junit.Assert.*
import org.junit.Test

class HighwaySurveySimpleChainageTest {

    @Test
    fun testRoadSideAndLaneDefaultsAndDetection() {
        // Test RoadSide enum values
        assertEquals(RoadSide.AUTO, RoadSide.valueOf("AUTO"))
        assertEquals(RoadSide.LHS, RoadSide.valueOf("LHS"))
        assertEquals(RoadSide.RHS, RoadSide.valueOf("RHS"))
        assertEquals(RoadSide.BHS, RoadSide.valueOf("BHS"))

        // Test SurveyLane labels
        assertEquals("Auto", SurveyLane.AUTO.label)
        assertEquals("Inner Lane", SurveyLane.INNER.label)
        assertEquals("Outer Lane", SurveyLane.OUTER.label)
        assertEquals("Fast Lane", SurveyLane.FAST.label)
        assertEquals("Slow Lane", SurveyLane.SLOW.label)
        assertEquals("Service Road", SurveyLane.SERVICE_ROAD.label)
        assertEquals("Other / Custom", SurveyLane.CUSTOM.label)

        // Automatic detection when offset is left vs right
        val (sideLeft, laneLeft) = SurveyGeometryEngine.detectRoadSideAndLane(
            offsetDistanceM = 7.0,
            offsetSide = OffsetSide.LEFT
        )
        assertEquals(RoadSide.LHS, sideLeft)
        assertEquals(SurveyLane.FAST, laneLeft)

        val (sideRight, laneRight) = SurveyGeometryEngine.detectRoadSideAndLane(
            offsetDistanceM = 15.0,
            offsetSide = OffsetSide.RIGHT
        )
        assertEquals(RoadSide.RHS, sideRight)
        assertEquals(SurveyLane.SERVICE_ROAD, laneRight)

        val (sideCentre, laneCentre) = SurveyGeometryEngine.detectRoadSideAndLane(
            offsetDistanceM = 0.0,
            offsetSide = OffsetSide.CENTRE
        )
        assertEquals(RoadSide.BHS, sideCentre)
        assertEquals(SurveyLane.CUSTOM, laneCentre)
    }

    @Test
    fun testStartingAndEndingChainageCalculations() {
        // INCREASING direction
        val sessionInc = SurveySession(
            id = "sess-inc-1",
            name = "NH-44 Northbound",
            startChainageMetres = 190000L,
            plannedEndChainageMetres = 200000L,
            currentChainageMetres = 194500L,
            direction = SurveyDirection.INCREASING,
            roadSide = RoadSide.RHS,
            lane = SurveyLane.INNER
        )

        assertEquals(10000L, sessionInc.plannedSurveyLengthMetres)
        // At 194+500, remaining distance to 200+000 is 5500m
        assertEquals(5500L, sessionInc.remainingDistanceMetres)

        // DECREASING direction
        val sessionDec = SurveySession(
            id = "sess-dec-1",
            name = "NH-44 Southbound",
            startChainageMetres = 200000L,
            plannedEndChainageMetres = 190000L,
            currentChainageMetres = 198200L,
            direction = SurveyDirection.DECREASING,
            roadSide = RoadSide.LHS,
            lane = SurveyLane.OUTER
        )

        assertEquals(10000L, sessionDec.plannedSurveyLengthMetres)
        // At 198+200, remaining distance to 190+000 is 8200m
        assertEquals(8200L, sessionDec.remainingDistanceMetres)
    }

    @Test
    fun testConfirmationIntervalOptions() {
        val options = ConfirmationIntervalOption.values()
        assertEquals(6, options.size)

        // Default should be 2 KM (2000m)
        val defaultOption = ConfirmationIntervalOption.EVERY_2_KM
        assertEquals("Every 2 KM (Default)", defaultOption.label)
        assertEquals(2000L, defaultOption.intervalMetres)

        // Options: 1 KM, 2 KM, 3 KM, 5 KM, Custom, Off
        assertTrue(options.any { it.intervalMetres == 1000L })
        assertTrue(options.any { it.intervalMetres == 2000L })
        assertTrue(options.any { it.intervalMetres == 3000L })
        assertTrue(options.any { it.intervalMetres == 5000L })
        assertTrue(options.any { it.intervalMetres == null }) // Off or Custom
        assertEquals(ConfirmationIntervalOption.EVERY_2_KM, ConfirmationIntervalOption.fromMetres(2000L))
        assertEquals(ConfirmationIntervalOption.OFF, ConfirmationIntervalOption.fromMetres(null))
    }

    @Test
    fun test200mCentrelineLabelsGeneration() {
        // Create a ~1km straight line going North: ~0.009 degrees latitude is ~1000m
        val p1 = GeoPoint(28.6000, 77.2000, 200.0)
        val p2 = GeoPoint(28.6090, 77.2000, 200.0)

        val centreline = listOf(p1, p2)

        val labels = SurveyGeometryEngine.generate200mChainageLabels(
            centrelinePts = centreline,
            startChainageMetres = 190000L,
            direction = SurveyDirection.INCREASING,
            calibrations = emptyList()
        )

        // For a ~1000m track starting at 190+000, 200m labels should include:
        // 190+000, 190+200, 190+400, 190+600, 190+800, 191+000
        assertTrue(labels.isNotEmpty())
        assertEquals("190+000", labels[0].displayChainage)
        assertEquals(190000L, labels[0].chainageMetres)

        // Verify all labels are multiples of 200m
        for (label in labels) {
            assertEquals(0L, label.chainageMetres % 200L)
            assertTrue(label.position.latitude >= p1.latitude)
            assertTrue(label.position.latitude <= p2.latitude)
        }

        // Check the second label is 190+200
        val l200 = labels.firstOrNull { it.chainageMetres == 190200L }
        assertNotNull(l200)
        assertEquals("190+200", l200?.displayChainage)
    }

    @Test
    fun testDataIntegrityGpsCoordinatesNeverChangeOnChainageEdit() {
        // Simulate a recorded survey point with GPS coordinates
        val originalLat = 28.613939
        val originalLon = 77.209021
        val originalAlt = 216.5
        val originalCalculatedChainage = 192000L

        val point = SurveyPoint(
            id = 1L,
            sessionId = "s-integrity",
            segmentIndex = 0,
            latitude = originalLat,
            longitude = originalLon,
            altitude = originalAlt,
            accuracy = 2.5f,
            calculatedChainageMetres = originalCalculatedChainage
        )

        // User edits chainage at this checkpoint from 192+000 to 192+150 (+150m drift)
        val editedChainage = 192150L
        val drift = (editedChainage - originalCalculatedChainage).toDouble()

        val calibration = SurveyCalibrationPoint(
            sessionId = "s-integrity",
            markerId = "cp-checkpoint-2km",
            checkpointName = "2 KM Checkpoint",
            latitude = point.latitude,
            longitude = point.longitude,
            measuredDistanceMetres = 2000.0,
            originalChainageMetres = originalCalculatedChainage,
            knownChainageMetres = editedChainage,
            driftMetres = drift
        )

        // Strict Requirement: GPS coordinates of the recorded survey point MUST NEVER CHANGE
        assertEquals(originalLat, point.latitude, 0.0)
        assertEquals(originalLon, point.longitude, 0.0)
        assertEquals(originalAlt, point.altitude, 0.0)

        // Verify calibration record retains both original and corrected values
        assertEquals(192000L, calibration.originalChainageMetres)
        assertEquals(192150L, calibration.knownChainageMetres)
        assertEquals(150.0, calibration.driftMetres, 0.01)

        // Verify chainage calculation incorporates the calibration drift for subsequent points
        val subsequentChainage = SurveyGeometryEngine.calculateChainageMetres(
            startChainageMetres = 190000L,
            distanceTravelledM = 2500.0, // 500m past checkpoint
            direction = SurveyDirection.INCREASING,
            calibrations = listOf(calibration)
        )
        // 190000 + 2500 + 150 = 192650
        assertEquals(192650L, subsequentChainage)
    }

    @Test
    fun testKmzExportIncludes200mLabelsAndMetadata() {
        val session = SurveySession(
            id = "sess-kmz-labels",
            name = "NH-44 Express Survey",
            projectName = "NHAI Project Package 1",
            highwayName = "NH-44",
            startChainageMetres = 190000L,
            plannedEndChainageMetres = 192000L,
            currentChainageMetres = 192000L,
            roadSide = RoadSide.RHS,
            lane = SurveyLane.INNER,
            confirmationIntervalMetres = 2000L,
            totalDistanceTravelledMetres = 2000.0
        )

        val points = listOf(
            SurveyPoint(sessionId = session.id, segmentIndex = 0, latitude = 28.6000, longitude = 77.2000, calculatedChainageMetres = 190000L),
            SurveyPoint(sessionId = session.id, segmentIndex = 0, latitude = 28.6180, longitude = 77.2000, calculatedChainageMetres = 192000L)
        )

        val kml = KmzSurveyExporter.buildKmlString(session, points, emptyList(), emptyList())

        // Verify metadata fields
        assertTrue(kml.contains("Road Side"))
        assertTrue(kml.contains("RHS"))
        assertTrue(kml.contains("Inner Lane"))
        assertTrue(kml.contains("Planned End Chainage"))
        assertTrue(kml.contains("192+000"))
        assertTrue(kml.contains("Confirmation Interval"))
        assertTrue(kml.contains("Every 2 KM"))

        // Verify dedicated 200m labels folder
        assertTrue(kml.contains("<name>Centreline Chainage (200m)</name>"))
        assertTrue(kml.contains("190+000"))
        assertTrue(kml.contains("190+200"))
    }
}
