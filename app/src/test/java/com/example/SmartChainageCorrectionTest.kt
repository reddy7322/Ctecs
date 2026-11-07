package com.example

import com.example.geo.SmartChainageCorrectionEngine
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartChainageCorrectionTest {

    private fun createSampleRoute(
        id: String = "route-nh48",
        startMetres: Long = 190000L,
        endMetres: Long = 210000L
    ): RouteData {
        return RouteData(
            id = id,
            name = "NH-48 Test Highway",
            fileName = "nh48.kmz",
            markers = emptyList(),
            alignments = emptyList(),
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
            description = "Calibration Point 1",
            isEnabled = true
        )

        val rawChainage = Chainage(198750L) // 250m away from 198+500 (within 2000m influence)
        val result = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = rawChainage,
            routeData = route,
            correctionPoints = listOf(p1),
            confidence = MatchConfidence.HIGH
        )

        assertNotNull(result)
        assertEquals(198850L, result?.correctedChainage?.metres) // 198750 + 100m
        assertEquals("198+850", result?.correctedChainage?.format())
        assertEquals(100L, result?.appliedOffsetMetres)
        assertEquals(198750L, result?.originalKmzChainage?.metres)
    }

    @Test
    fun testSmartCorrection_multiPointInterpolation() {
        val route = createSampleRoute()
        // Multi-point linear interpolation
        // Point 1: raw 198+000 -> corrected 198+000 (0m shift)
        // Point 2: raw 200+000 -> corrected 200+400 (+400m shift)
        val p1 = ChainageCorrectionPoint(
            id = "p1",
            routeId = route.id,
            latitude = 12.4,
            longitude = 77.4,
            originalKmzMetres = 198000L,
            correctedMetres = 198000L,
            isEnabled = true
        )
        val p2 = ChainageCorrectionPoint(
            id = "p2",
            routeId = route.id,
            latitude = 12.6,
            longitude = 77.6,
            originalKmzMetres = 200000L,
            correctedMetres = 200400L,
            isEnabled = true
        )

        // Midway at raw 199+000 (50% interpolation => shift should be +200m => 199+200)
        val rawChainage = Chainage(199000L)
        val result = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = rawChainage,
            routeData = route,
            correctionPoints = listOf(p1, p2),
            confidence = MatchConfidence.HIGH
        )

        assertNotNull(result)
        assertEquals(199200L, result?.correctedChainage?.metres)
        assertEquals("199+200", result?.correctedChainage?.format())
        assertEquals(200L, result?.appliedOffsetMetres)
        assertEquals(0.5, result?.interpolationFraction ?: 0.0, 0.001)
    }

    @Test
    fun testSmartCorrection_boundaryNoExtrapolation() {
        val route = createSampleRoute()
        val p1 = ChainageCorrectionPoint(
            id = "p1",
            routeId = route.id,
            latitude = 12.4,
            longitude = 77.4,
            originalKmzMetres = 198000L,
            correctedMetres = 198200L,
            isEnabled = true
        )
        val p2 = ChainageCorrectionPoint(
            id = "p2",
            routeId = route.id,
            latitude = 12.6,
            longitude = 77.6,
            originalKmzMetres = 200000L,
            correctedMetres = 200200L,
            isEnabled = true
        )

        // Match outside coverage beyond max bounds (e.g. at raw 202+000)
        val rawChainage = Chainage(202000L)
        val result = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = rawChainage,
            routeData = route,
            correctionPoints = listOf(p1, p2),
            confidence = MatchConfidence.HIGH
        )

        // Outside coverage: returns null so the original KMZ chainage is preserved
        assertNull(result)
    }

    @Test
    fun testSmartCorrection_lowGpsConfidenceGating() {
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
        // LOW confidence (degraded GPS fix or off-alignment)
        val result = SmartChainageCorrectionEngine.applyCorrection(
            rawChainage = rawChainage,
            routeData = route,
            correctionPoints = listOf(p1),
            confidence = MatchConfidence.LOW
        )

        // Should NOT apply custom correction when GPS fix is unreliable
        assertNull(result)
    }

    @Test
    fun testValidationWarnings_duplicateAndJumps() {
        val route = createSampleRoute()
        val p1 = ChainageCorrectionPoint(
            id = "p1",
            routeId = route.id,
            latitude = 12.5,
            longitude = 77.5,
            originalKmzMetres = 198000L,
            correctedMetres = 198000L
        )
        // Duplicate point within 5 metres
        val duplicate = ChainageCorrectionPoint(
            id = "p2",
            routeId = route.id,
            latitude = 12.5001,
            longitude = 77.5001,
            originalKmzMetres = 198004L,
            correctedMetres = 198005L
        )
        // Point with extreme jump > 10km offset
        val extremeJump = ChainageCorrectionPoint(
            id = "p3",
            routeId = route.id,
            latitude = 12.51,
            longitude = 77.51,
            originalKmzMetres = 199000L,
            correctedMetres = 220000L // 21km shift
        )

        val valResultDup = SmartChainageCorrectionEngine.validatePoint(
            point = duplicate,
            existingPoints = listOf(p1),
            routeData = route
        )
        assertTrue(valResultDup.warnings.any { it.contains("already exists", ignoreCase = true) })

        val valResultJump = SmartChainageCorrectionEngine.validatePoint(
            point = extremeJump,
            existingPoints = listOf(p1),
            routeData = route
        )
        assertTrue(valResultJump.warnings.any { it.contains("Large correction shift", ignoreCase = true) })
    }
}
