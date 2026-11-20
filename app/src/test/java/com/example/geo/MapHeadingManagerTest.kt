package com.example.geo

import com.example.model.Chainage
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.RouteAlignment
import com.example.model.RouteData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Comprehensive JVM tests for the centralized [MapHeadingManager].
 * Verifies:
 * - Always Direction-Up heading calculation from GPS movement + displacement vectors
 * - Zero magnetic compass interference / zero automatic reset to North-Up
 * - Smooth circular wraparound (355° -> 5°)
 * - Stationary drift rejection & last reliable heading retention
 * - Weak GPS retention & smooth RECOVERING -> RELIABLE recovery
 * - Conflicting bearing / multipath spike rejection (UNCERTAIN)
 * - Authentic U-turn acceptance on consecutive consistent samples
 * - Road alignment tangent initial fallback & honest UNAVAILABLE reporting
 */
class MapHeadingManagerTest {

    private fun sampleRouteNorthEast(): RouteData {
        return RouteData(
            id = "route-ne",
            name = "NH-44 Test",
            fileName = "nh44.kmz",
            markers = emptyList(),
            alignments = listOf(
                RouteAlignment(
                    id = "align-1",
                    name = "Mainline",
                    points = listOf(
                        GeoPoint(28.6000, 77.2000),
                        GeoPoint(28.6100, 77.2100) // ~41° NE tangent
                    ),
                    direction = "INCREASING"
                )
            ),
            bounds = GeoBounds(28.59, 28.62, 77.19, 77.22),
            startChainage = Chainage(100_000L),
            endChainage = Chainage(105_000L)
        )
    }

    @Test
    fun initialState_isUnavailable_andNeverInventsNorthUpHeading() {
        val manager = MapHeadingManager()
        val initial = manager.headingState.value
        assertEquals(MapHeadingStatus.UNAVAILABLE, initial.status)
        assertEquals(MapHeadingSource.NONE, initial.source)
        assertNull(initial.headingDegrees)
        assertNull(initial.lastReliableHeadingDegrees)
        assertTrue(initial.isDirectionUnavailable)
        assertEquals("Heading unavailable", initial.statusBadgeLabel)
    }

    @Test
    fun activeVehicleMovement_producesReliableDirectionUpHeading_andWrapsSmoothlyAcrossNorth() {
        val manager = MapHeadingManager()
        var ts = 10_000L

        // First movement sample heading ~355° (just west of North)
        val s1 = manager.processGpsSample(
            point = GeoPoint(28.6000, 77.2000),
            speedMps = 12f,
            gpsBearingDeg = 355f,
            accuracyMetres = 5f,
            timestampMs = ts
        )
        // First transition out of UNAVAILABLE goes through RECOVERING
        assertEquals(MapHeadingStatus.RECOVERING, s1.status)
        assertNotNull(s1.headingDegrees)
        assertFalse(s1.isDirectionUnavailable)

        // Second consistent movement sample heading ~5° (just east of North)
        ts += 1_000L
        val s2 = manager.processGpsSample(
            point = GeoPoint(28.60015, 77.20001),
            speedMps = 12f,
            gpsBearingDeg = 5f,
            accuracyMetres = 5f,
            timestampMs = ts
        )
        assertEquals(MapHeadingStatus.RELIABLE, s2.status)
        assertNotNull(s2.headingDegrees)
        // Shortest path from 355° toward 5° passes through 0° (within 12° of 0°, never 180° South!)
        val deltaFromNorth = abs(MapHeadingManager.shortestAngleDelta(0f, s2.headingDegrees!!))
        assertTrue("Expected smooth wraparound near 0°, got ${s2.headingDegrees}", deltaFromNorth <= 15f)
    }

    @Test
    fun stationaryVehicle_retainsLastReliableHeading_andRejectsGpsDrift() {
        val manager = MapHeadingManager()
        var ts = 20_000L

        // Establish reliable East heading (~90°)
        manager.processGpsSample(
            point = GeoPoint(28.6000, 77.2000),
            speedMps = 10f,
            gpsBearingDeg = 90f,
            accuracyMetres = 4f,
            timestampMs = ts
        )
        ts += 1_000L
        val moving = manager.processGpsSample(
            point = GeoPoint(28.6000, 77.20015),
            speedMps = 10f,
            gpsBearingDeg = 90f,
            accuracyMetres = 4f,
            timestampMs = ts
        )
        assertEquals(MapHeadingStatus.RELIABLE, moving.status)
        val reliableHeading = moving.headingDegrees!!
        assertTrue("Expected heading near 90°, got $reliableHeading", abs(reliableHeading - 90f) < 5f)

        // Vehicle stops at traffic light with noisy stationary GPS bearing (270°) & 1.5m GPS jitter
        ts += 1_000L
        val stopped = manager.processGpsSample(
            point = GeoPoint(28.600008, 77.200155),
            speedMps = 0.2f,
            gpsBearingDeg = 270f,
            accuracyMetres = 6f,
            timestampMs = ts
        )
        assertEquals(MapHeadingStatus.STATIONARY, stopped.status)
        assertEquals(MapHeadingSource.RETAINED_LAST_RELIABLE, stopped.source)
        assertEquals(reliableHeading, stopped.headingDegrees!!, 0.01f)
        assertEquals(reliableHeading, stopped.lastReliableHeadingDegrees!!, 0.01f)
        assertNull("Should not show unavailable badge when heading is retained", stopped.statusBadgeLabel)
    }

    @Test
    fun weakGpsSignal_retainsLastReliableHeading_andRecoversSmoothly() {
        val manager = MapHeadingManager()
        var ts = 30_000L

        // Establish reliable South heading (~180°)
        manager.processGpsSample(GeoPoint(28.6100, 77.2000), 11f, 180f, 5f, timestampMs = ts)
        ts += 1_000L
        val reliable = manager.processGpsSample(GeoPoint(28.60985, 77.2000), 11f, 180f, 5f, timestampMs = ts)
        assertEquals(MapHeadingStatus.RELIABLE, reliable.status)
        val retainedDeg = reliable.headingDegrees!!

        // Tunnel / underpass: poor GPS accuracy (65m) and null point
        ts += 1_000L
        val weak = manager.processGpsSample(GeoPoint(28.6097, 77.2003), 8f, 30f, 65f, timestampMs = ts)
        assertEquals(MapHeadingStatus.WEAK_GPS, weak.status)
        assertEquals(MapHeadingSource.RETAINED_LAST_RELIABLE, weak.source)
        assertEquals(retainedDeg, weak.headingDegrees!!, 0.01f)

        ts += 1_000L
        val lostFix = manager.processGpsSample(null, null, null, null, timestampMs = ts)
        assertEquals(MapHeadingStatus.WEAK_GPS, lostFix.status)
        assertEquals(retainedDeg, lostFix.headingDegrees!!, 0.01f)

        // GPS recovers cleanly: RECOVERING -> RELIABLE
        ts += 1_000L
        val recovering = manager.processGpsSample(GeoPoint(28.6095, 77.2000), 10f, 185f, 6f, timestampMs = ts)
        assertEquals(MapHeadingStatus.RECOVERING, recovering.status)

        ts += 1_000L
        val recovered = manager.processGpsSample(GeoPoint(28.6093, 77.2000), 10f, 185f, 6f, timestampMs = ts)
        assertEquals(MapHeadingStatus.RELIABLE, recovered.status)
        assertTrue(abs(MapHeadingManager.shortestAngleDelta(185f, recovered.headingDegrees!!)) < 10f)
    }

    @Test
    fun suddenMultipathSpike_andBearingDisplacementConflict_markUncertainAndHoldHeading() {
        val manager = MapHeadingManager()
        var ts = 40_000L

        // Establish reliable 90° East travel
        manager.processGpsSample(GeoPoint(28.6000, 77.2000), 12f, 90f, 4f, timestampMs = ts)
        ts += 1_000L
        val reliable = manager.processGpsSample(GeoPoint(28.6000, 77.20015), 12f, 90f, 4f, timestampMs = ts)
        assertEquals(MapHeadingStatus.RELIABLE, reliable.status)
        val stableDeg = reliable.headingDegrees!!

        // 1) Bearing vs Displacement conflict: moving East geographically, but bogus GPS bearing says 260° West
        ts += 1_000L
        val conflict = manager.processGpsSample(
            point = GeoPoint(28.6000, 77.20030), // actual displacement is ~90° East
            speedMps = 12f,
            gpsBearingDeg = 260f,
            accuracyMetres = 5f,
            timestampMs = ts
        )
        assertEquals(MapHeadingStatus.UNCERTAIN, conflict.status)
        assertEquals(stableDeg, conflict.headingDegrees!!, 0.01f)

        // 2) Single-sample sudden >95° spike within 1.8s window
        ts += 500L
        val spike = manager.processGpsSample(
            point = GeoPoint(28.60001, 77.20031), // tiny displacement, no displacement bearing
            speedMps = 10f,
            gpsBearingDeg = 250f, // 160° jump in 500ms!
            accuracyMetres = 5f,
            timestampMs = ts
        )
        assertEquals(MapHeadingStatus.UNCERTAIN, spike.status)
        assertEquals(stableDeg, spike.headingDegrees!!, 0.01f)
    }

    @Test
    fun genuineUTurn_acceptedSmoothlyAfterConsecutiveConsistentSamples() {
        val manager = MapHeadingManager()
        var ts = 50_000L

        // Driving North (0°)
        manager.processGpsSample(GeoPoint(28.6000, 77.2000), 10f, 0f, 4f, timestampMs = ts)
        ts += 1_000L
        val north = manager.processGpsSample(GeoPoint(28.60015, 77.2000), 10f, 0f, 4f, timestampMs = ts)
        assertEquals(MapHeadingStatus.RELIABLE, north.status)

        // First Southbound sample (180°) -> held as UNCERTAIN pending confirmation
        ts += 1_000L
        val uTurn1 = manager.processGpsSample(GeoPoint(28.60015, 77.2000), 8f, 180f, 5f, timestampMs = ts)
        assertEquals(MapHeadingStatus.UNCERTAIN, uTurn1.status)

        // Second consecutive Southbound sample -> confirmed genuine turn, begins smooth rotation
        ts += 1_000L
        val uTurn2 = manager.processGpsSample(GeoPoint(28.60000, 77.2000), 8f, 180f, 5f, timestampMs = ts)
        assertEquals(MapHeadingStatus.RECOVERING, uTurn2.status)
        assertTrue(
            "Heading should step smoothly away from 0° on confirmed U-turn",
            abs(MapHeadingManager.shortestAngleDelta(0f, uTurn2.headingDegrees!!)) >= 20f
        )
    }

    @Test
    fun stationaryOnActiveRouteAlignment_usesAlignmentTangentFallback_whileOffRouteReportsUnavailable() {
        val manager = MapHeadingManager()
        val route = sampleRouteNorthEast()

        // Stationary far from route -> UNAVAILABLE (never guesses 0° North)
        val offRoute = manager.processGpsSample(
            point = GeoPoint(29.9000, 78.9000),
            speedMps = 0f,
            gpsBearingDeg = null,
            accuracyMetres = 5f,
            routeData = route,
            timestampMs = 60_000L
        )
        assertEquals(MapHeadingStatus.UNAVAILABLE, offRoute.status)
        assertNull(offRoute.headingDegrees)
        assertEquals("Heading unavailable", offRoute.statusBadgeLabel)

        // Stationary directly on active route alignment -> uses alignment tangent (~41° NE)
        val onRoute = manager.processGpsSample(
            point = GeoPoint(28.6050, 77.2050),
            speedMps = 0f,
            gpsBearingDeg = null,
            accuracyMetres = 5f,
            routeData = route,
            timestampMs = 61_000L
        )
        assertEquals(MapHeadingStatus.STATIONARY, onRoute.status)
        assertEquals(MapHeadingSource.ALIGNMENT_TANGENT, onRoute.source)
        assertNotNull(onRoute.headingDegrees)
        assertTrue(
            "Expected NE alignment tangent (~41°), got ${onRoute.headingDegrees}",
            onRoute.headingDegrees!! in 30f..55f
        )
    }

    @Test
    fun displacementOnlyMovement_withoutGpsBearing_calculatesReliableDirectionUpHeading() {
        val manager = MapHeadingManager()
        var ts = 70_000L

        // Anchor point
        manager.processGpsSample(GeoPoint(28.6000, 77.2000), 0f, null, 4f, timestampMs = ts)
        // Move ~15m due East without hardware bearing reported
        ts += 2_000L
        val move1 = manager.processGpsSample(GeoPoint(28.6000, 77.20018), 5f, null, 4f, timestampMs = ts)
        assertEquals(MapHeadingSource.GPS_DISPLACEMENT, move1.source)
        assertNotNull(move1.headingDegrees)
        assertTrue("Expected ~90° East displacement heading, got ${move1.headingDegrees}", abs(move1.headingDegrees!! - 90f) < 5f)

        // Continue moving due East -> RELIABLE
        ts += 2_000L
        val move2 = manager.processGpsSample(GeoPoint(28.6000, 77.20036), 5f, null, 4f, timestampMs = ts)
        assertEquals(MapHeadingStatus.RELIABLE, move2.status)
        assertTrue(abs(move2.headingDegrees!! - 90f) < 5f)
    }
}
