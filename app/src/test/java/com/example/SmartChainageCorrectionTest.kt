package com.example

import com.example.geo.SmartChainageCorrectionEngine
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartChainageCorrectionTest {

    private fun createSampleRoute(
        id: String = "route-nh48",
        startMetres: Long = 190000L,
        endMetres: Long = 210000L,
        direction: String = "INCREASING"
    ): RouteData {
        val p1 = GeoPoint(12.0000, 77.0000)
        val p2 = GeoPoint(12.0900, 77.0000)
        val p3 = GeoPoint(12.1800, 77.0000)
        return RouteData(
            id = id,
            name = "NH-48 Test Highway",
            fileName = "nh48.kmz",
            markers = listOf(
                RouteMarker("m1", Chainage(startMetres).format(), Chainage(startMetres), p1),
                RouteMarker("m2", Chainage((startMetres + endMetres) / 2).format(), Chainage((startMetres + endMetres) / 2), p2),
                RouteMarker("m3", Chainage(endMetres).format(), Chainage(endMetres), p3)
            ),
            alignments = listOf(
                RouteAlignment("align-1", "NH-48 Main", listOf(p1, p2, p3), direction = direction)
            ),
            bounds = GeoBounds(12.0, 77.0, 13.0, 78.0),
            startChainage = Chainage(startMetres),
            endChainage = Chainage(endMetres)
        )
    }

    @Test
    fun testChainageParsing_formats() {
        // Standard highway format
        val c1 = Chainage.parse("198+880")
        assertNotNull(c1)
        assertEquals(198880L, c1?.metres)
        assertEquals("198+880", c1?.format())

        // Plain digits format
        val c2 = Chainage.parse("198880")
        assertNotNull(c2)
        assertEquals(198880L, c2?.metres)
        assertEquals("198+880", c2?.format())

        // Exact kilometer format
        val c3 = Chainage.parse("200+000")
        assertNotNull(c3)
        assertEquals(200000L, c3?.metres)
        assertEquals("200+000", c3?.format())

        val c4 = Chainage.parse("200000")
        assertNotNull(c4)
        assertEquals(200000L, c4?.metres)

        // Decimal representation
        val c5 = Chainage.parse("198.880")
        assertNotNull(c5)
        assertEquals(198880L, c5?.metres)

        // Prefixed strings
        val c6 = Chainage.parse("CH: 198+880")
        assertNotNull(c6)
        assertEquals(198880L, c6?.metres)

        val c7 = Chainage.parse("KM 200+050")
        assertNotNull(c7)
        assertEquals(200050L, c7?.metres)
    }

    @Test
    fun testSmartCorrection_singlePointShift() {
        val route = createSampleRoute()
        // Single point shift: Calibration Point at raw 198+500 set to 198+600 (+100m shift)
        val p1 = ChainageCorrectionPoint(
            id = "pt-1",
            routeId = route.id,
            latitude = 12.5,
            longitude = 77.5,
            originalKmzMetres = 198500L,
            correctedMetres = 198600L,
            routeDistanceMetres = 8500.0,
            description = "Calibration Point 1",
            isEnabled = true
        )

        val rawChainage = Chainage(198750L) // 250m away from 198+500 (within 2000m influence)
        val result = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = rawChainage,
            routeData = route,
            correctionPoints = listOf(p1),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 8750.0
        )

        assertNotNull(result)
        assertEquals(198850L, result?.correctedChainage?.metres) // 198750 + 100m
        assertEquals("198+850", result?.correctedChainage?.format())
        assertEquals(100L, result?.appliedOffsetMetres)
        assertEquals(198750L, result?.originalKmzChainage?.metres)
    }

    @Test
    fun testSmartCorrection_multiPointRouteDistanceInterpolation_andReverseTravel() {
        val route = createSampleRoute()
        // Three correction points along the route:
        // Point 1: routeDist 8500m (raw 198+500) -> corrected 198+500
        // Point 2: routeDist 9000m (raw 199+050) -> corrected 199+000
        // Point 3: routeDist 10000m (raw 200+100) -> corrected 200+000
        val p1 = ChainageCorrectionPoint(
            id = "p1",
            projectId = "proj-1",
            routeId = route.id,
            latitude = 12.0765,
            longitude = 77.0,
            originalKmzMetres = 198500L,
            correctedMetres = 198500L,
            routeDistanceMetres = 8500.0,
            isEnabled = true
        )
        val p2 = ChainageCorrectionPoint(
            id = "p2",
            projectId = "proj-1",
            routeId = route.id,
            latitude = 12.0810,
            longitude = 77.0,
            originalKmzMetres = 199050L,
            correctedMetres = 199000L,
            routeDistanceMetres = 9000.0,
            isEnabled = true
        )
        val p3 = ChainageCorrectionPoint(
            id = "p3",
            projectId = "proj-1",
            routeId = route.id,
            latitude = 12.0900,
            longitude = 77.0,
            originalKmzMetres = 200100L,
            correctedMetres = 200000L,
            routeDistanceMetres = 10000.0,
            isEnabled = true
        )

        // Forward travel: midway between Point 1 (8500m) and Point 2 (9000m) at routeDist 8750m -> 198+750
        val resForward = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(198775L),
            routeData = route,
            correctionPoints = listOf(p3, p1, p2), // unsorted input should be sorted in route order
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 8750.0
        )
        assertNotNull(resForward)
        assertEquals(198750L, resForward?.correctedChainage?.metres)
        assertEquals("198+750", resForward?.correctedChainage?.format())

        // Reverse travel: moving backward from Point 3 (10000m) to 9500m (midway between P3 and P2) -> 199+500
        val resReverse = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(199575L),
            routeData = route,
            correctionPoints = listOf(p1, p2, p3),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 9500.0
        )
        assertNotNull(resReverse)
        assertEquals(199500L, resReverse?.correctedChainage?.metres)
        assertEquals("199+500", resReverse?.correctedChainage?.format())
    }

    @Test
    fun testSmartCorrection_decreasingChainageRoute() {
        val routeDec = createSampleRoute(
            id = "route-dec",
            startMetres = 210000L,
            endMetres = 190000L,
            direction = "DECREASING"
        )
        // Decreasing route: at routeDist 1000m -> 209+000, at routeDist 2000m -> 208+000
        val p1 = ChainageCorrectionPoint(
            id = "p1",
            routeId = routeDec.id,
            latitude = 12.01,
            longitude = 77.0,
            originalKmzMetres = 209050L,
            correctedMetres = 209000L,
            routeDistanceMetres = 1000.0,
            isEnabled = true
        )
        val p2 = ChainageCorrectionPoint(
            id = "p2",
            routeId = routeDec.id,
            latitude = 12.02,
            longitude = 77.0,
            originalKmzMetres = 208050L,
            correctedMetres = 208000L,
            routeDistanceMetres = 2000.0,
            isEnabled = true
        )

        val result = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(208550L),
            routeData = routeDec,
            correctionPoints = listOf(p1, p2),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 1500.0
        )
        assertNotNull(result)
        assertEquals(208500L, result?.correctedChainage?.metres)
        assertEquals("208+500", result?.correctedChainage?.format())
    }

    @Test
    fun testSmartCorrection_boundaryNoExtrapolation_andRouteIsolation() {
        val route1 = createSampleRoute(id = "route-1")
        val route2 = createSampleRoute(id = "route-2")
        val p1 = ChainageCorrectionPoint(
            id = "p1",
            routeId = route1.id,
            latitude = 12.4,
            longitude = 77.4,
            originalKmzMetres = 198000L,
            correctedMetres = 198200L,
            routeDistanceMetres = 8000.0,
            isEnabled = true
        )
        val p2 = ChainageCorrectionPoint(
            id = "p2",
            routeId = route1.id,
            latitude = 12.6,
            longitude = 77.6,
            originalKmzMetres = 200000L,
            correctedMetres = 200200L,
            routeDistanceMetres = 10000.0,
            isEnabled = true
        )

        // Match beyond last control point (at raw 202+000 / routeDist 12000m) propagates along alignment
        val outsideResult = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(202000L),
            routeData = route1,
            correctionPoints = listOf(p1, p2),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 12000.0
        )
        assertNotNull("Should propagate calibration beyond last control point along alignment", outsideResult)
        assertEquals(202200L, outsideResult?.correctedChainage?.metres)
        assertEquals(SmartChainageCorrectionEngine.CorrectionMode.EXTRAPOLATED_POST_END, outsideResult?.mode)

        // Match on unrelated route-2 at same chainage 199+000
        val unrelatedRouteResult = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = Chainage(199000L),
            routeData = route2,
            correctionPoints = listOf(p1, p2),
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 9000.0
        )
        assertNull("Should not apply route-1 corrections to route-2", unrelatedRouteResult)
    }

    @Test
    fun testSmartCorrection_lowGpsConfidenceDoesNotFreezeAndNoMatchGating() {
        val route = createSampleRoute()
        val p1 = ChainageCorrectionPoint(
            id = "p1",
            routeId = route.id,
            latitude = 12.5,
            longitude = 77.5,
            originalKmzMetres = 198000L,
            correctedMetres = 198200L,
            isEnabled = true
        )

        val rawChainage = Chainage(198500L)
        // LOW confidence (degraded GPS fix) must NOT freeze or drop calibration when matched to alignment
        val lowConfResult = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = rawChainage,
            routeData = route,
            correctionPoints = listOf(p1),
            confidence = MatchConfidence.LOW
        )
        assertNotNull(lowConfResult)
        assertEquals(198700L, lowConfResult?.correctedChainage?.metres)
        assertEquals(com.example.model.CalibrationValidationStatus.LOW_CONFIDENCE, lowConfResult?.validationStatus)

        // NO_MATCH (off-route) should return null
        val noMatchResult = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = rawChainage,
            routeData = route,
            correctionPoints = listOf(p1),
            confidence = MatchConfidence.NO_MATCH
        )
        assertNull(noMatchResult)
    }

    @Test
    fun testValidationWarnings_duplicateOutsideJumpAndDirectionConflict() {
        val route = createSampleRoute(direction = "INCREASING")
        val p1 = ChainageCorrectionPoint(
            id = "p1",
            routeId = route.id,
            latitude = 12.04,
            longitude = 77.0,
            originalKmzMetres = 198000L,
            correctedMetres = 198000L,
            routeDistanceMetres = 8000.0
        )
        // 1. Duplicate point within 5 metres
        val duplicate = ChainageCorrectionPoint(
            id = "p2",
            routeId = route.id,
            latitude = 12.04,
            longitude = 77.0,
            originalKmzMetres = 198003L,
            correctedMetres = 198003L,
            routeDistanceMetres = 8003.0
        )
        // 2. Point with extreme jump > 10km offset
        val extremeJump = ChainageCorrectionPoint(
            id = "p3",
            routeId = route.id,
            latitude = 12.05,
            longitude = 77.0,
            originalKmzMetres = 199000L,
            correctedMetres = 220000L, // 21km shift
            routeDistanceMetres = 9000.0
        )
        // 3. Direction conflict: further along INCREASING route (9500m), but lower corrected chainage (197+000 < 198+000)
        val directionConflict = ChainageCorrectionPoint(
            id = "p4",
            routeId = route.id,
            latitude = 12.06,
            longitude = 77.0,
            originalKmzMetres = 199500L,
            correctedMetres = 197000L,
            routeDistanceMetres = 9500.0
        )

        val valResultDup = SmartChainageCorrectionEngine.validatePoint(
            point = duplicate,
            existingPoints = listOf(p1),
            routeData = route
        )
        assertTrue(valResultDup.warnings.any { it.contains("Duplicate", ignoreCase = true) || it.contains("already exists", ignoreCase = true) })

        val valResultJump = SmartChainageCorrectionEngine.validatePoint(
            point = extremeJump,
            existingPoints = listOf(p1),
            routeData = route
        )
        assertTrue(valResultJump.warnings.any { it.contains("Large correction shift", ignoreCase = true) || it.contains("jump", ignoreCase = true) })

        val valResultConflict = SmartChainageCorrectionEngine.validatePoint(
            point = directionConflict,
            existingPoints = listOf(p1),
            routeData = route
        )
        assertTrue(valResultConflict.warnings.any { it.contains("Direction conflict", ignoreCase = true) })
    }

    @Test
    fun testPersistenceAndPriorityDisplay() {
        val route = createSampleRoute(id = "route-nh48")
        val p1 = ChainageCorrectionPoint(
            id = "cp-persist-1",
            projectId = "proj-nh48",
            routeId = "route-nh48",
            latitude = 12.123456,
            longitude = 77.654321,
            originalKmzMetres = 199500L,
            correctedMetres = 199880L,
            routeDistanceMetres = 9500.5,
            description = "Toll Plaza Reference",
            isEnabled = true
        )
        val p2 = ChainageCorrectionPoint(
            id = "cp-persist-2",
            projectId = "proj-nh48",
            routeId = "route-nh48",
            latitude = 12.080000,
            longitude = 77.654321,
            originalKmzMetres = 198500L,
            correctedMetres = 198880L,
            routeDistanceMetres = 8500.5,
            description = "Culvert Reference",
            isEnabled = true
        )

        val sorted = SmartChainageCorrectionEngine.sortPointsInRouteOrder(listOf(p1, p2), route)
        assertEquals("cp-persist-2", sorted[0].id)
        assertEquals("cp-persist-1", sorted[1].id)
        assertEquals("proj-nh48", sorted[0].projectId)
        assertEquals("route-nh48", sorted[0].routeId)
        assertEquals("+380m", sorted[0].displayOffset)
        assertTrue(sorted[0].isEnabled)

        // Verify Manual MatchResult preserves formatted chainage and MANUAL source
        val manualRes = ChainageMatchResult.manual(
            chainage = Chainage(198880L),
            side = "LHS",
            customText = "198+880"
        )
        assertEquals(ChainageSource.MANUAL, manualRes.source)
        assertEquals("198+880", manualRes.displayChainage)
        assertEquals("KM 198+880", manualRes.displayKmChainage)
        assertTrue(manualRes.isManual)
    }
}
