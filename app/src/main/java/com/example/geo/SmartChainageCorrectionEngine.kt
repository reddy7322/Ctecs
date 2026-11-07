package com.example.geo

import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteData
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Result returned by [SmartChainageCorrectionEngine.applyCorrection].
 */
data class SmartCorrectionResult(
    val correctedChainage: Chainage,
    val originalKmzChainage: Chainage,
    val appliedOffsetMetres: Long,
    val boundingPointA: ChainageCorrectionPoint?,
    val boundingPointB: ChainageCorrectionPoint?,
    val interpolationFraction: Double,
    val infoMessage: String
)

/**
 * Validates new or edited correction points against existing points and route boundaries.
 */
data class CorrectionValidationResult(
    val isValid: Boolean,
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Smart engine for custom KMZ chainage calibration and interpolation along highway alignments.
 */
object SmartChainageCorrectionEngine {

    // Maximum distance from a single correction point where fixed shift applies
    const val SINGLE_POINT_INFLUENCE_METRES = 2000L

    /**
     * Applies custom chainage correction to [rawChainage] if it falls within valid coverage
     * of enabled correction points on [routeData].
     *
     * Returns [SmartCorrectionResult] if a custom correction applies, or `null` if the
     * point is outside correction coverage or GPS accuracy is too low.
     */
    fun applyCorrection(
        rawChainage: Chainage,
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>,
        gpsPoint: GeoPoint? = null,
        gpsAccuracy: Float = 0f,
        confidence: MatchConfidence = MatchConfidence.HIGH
    ): SmartCorrectionResult? {
        // Safety: Do not apply custom correction if GPS fix is untrusted or off-route
        if (confidence == MatchConfidence.NO_MATCH || confidence == MatchConfidence.LOW) {
            return null
        }

        // Filter enabled correction points belonging strictly to this route
        val activePoints = correctionPoints
            .filter { it.routeId == routeData.id && it.isEnabled }
            .sortedBy { it.originalKmzMetres }

        if (activePoints.isEmpty()) {
            return null
        }

        val rawMetres = rawChainage.metres

        // Case 1: Single correction point calibration
        if (activePoints.size == 1) {
            val single = activePoints[0]
            val distFromPoint = abs(rawMetres - single.originalKmzMetres)
            if (distFromPoint <= SINGLE_POINT_INFLUENCE_METRES) {
                val corrected = rawMetres + single.offsetMetres
                return SmartCorrectionResult(
                    correctedChainage = Chainage(corrected),
                    originalKmzChainage = rawChainage,
                    appliedOffsetMetres = single.offsetMetres,
                    boundingPointA = single,
                    boundingPointB = null,
                    interpolationFraction = 1.0,
                    infoMessage = "Calibrated at ${single.originalKmzChainage.format()} (${single.displayOffset})"
                )
            } else {
                // Outside single point influence zone
                return null
            }
        }

        // Case 2: Multiple correction points - Route interpolation
        val minKmz = activePoints.first().originalKmzMetres
        val maxKmz = activePoints.last().originalKmzMetres

        // Check if rawChainage falls strictly within valid coverage
        if (rawMetres < minKmz || rawMetres > maxKmz) {
            // Outside coverage: Do not extrapolate custom corrections indefinitely
            return null
        }

        // Find the bounding segment [pA, pB]
        var pA: ChainageCorrectionPoint? = null
        var pB: ChainageCorrectionPoint? = null

        for (i in 0 until activePoints.size - 1) {
            val curr = activePoints[i]
            val next = activePoints[i + 1]
            if (rawMetres in curr.originalKmzMetres..next.originalKmzMetres) {
                pA = curr
                pB = next
                break
            }
        }

        if (pA == null || pB == null) {
            // Edge case: exactly at the last point
            if (rawMetres == maxKmz) {
                val last = activePoints.last()
                return SmartCorrectionResult(
                    correctedChainage = last.correctedChainage,
                    originalKmzChainage = rawChainage,
                    appliedOffsetMetres = last.offsetMetres,
                    boundingPointA = last,
                    boundingPointB = null,
                    interpolationFraction = 1.0,
                    infoMessage = "Calibrated at ${last.originalKmzChainage.format()}"
                )
            }
            return null
        }

        // Linear interpolation along the alignment
        val spanKmz = pB.originalKmzMetres - pA.originalKmzMetres
        val fraction = if (spanKmz != 0L) {
            (rawMetres - pA.originalKmzMetres).toDouble() / spanKmz.toDouble()
        } else {
            0.0
        }

        val correctedMetres = (pA.correctedMetres + fraction * (pB.correctedMetres - pA.correctedMetres)).roundToLong()
        val appliedOffset = correctedMetres - rawMetres

        val offsetStr = if (appliedOffset >= 0) "+${appliedOffset}m" else "${appliedOffset}m"
        val infoMsg = "${pA.correctedChainage.format()} ↔ ${pB.correctedChainage.format()} ($offsetStr)"

        return SmartCorrectionResult(
            correctedChainage = Chainage(correctedMetres),
            originalKmzChainage = rawChainage,
            appliedOffsetMetres = appliedOffset,
            boundingPointA = pA,
            boundingPointB = pB,
            interpolationFraction = fraction,
            infoMessage = infoMsg
        )
    }

    /**
     * Validates a correction point before adding or editing.
     */
    fun validatePoint(
        point: ChainageCorrectionPoint,
        existingPoints: List<ChainageCorrectionPoint>,
        routeData: RouteData?
    ): CorrectionValidationResult {
        val warnings = mutableListOf<String>()

        // 1. Basic validation
        if (point.routeId.isBlank()) {
            return CorrectionValidationResult(isValid = false, errorMessage = "No route selected for correction point.")
        }

        // 2. Duplicate check (within 10 metres on the same route)
        val otherPoints = existingPoints.filter { it.routeId == point.routeId && it.id != point.id }
        val duplicate = otherPoints.find { abs(it.originalKmzMetres - point.originalKmzMetres) < 10 }
        if (duplicate != null) {
            warnings.add("A correction point already exists at this location (${duplicate.originalKmzChainage.format()}).")
        }

        // 3. Route boundary check
        if (routeData != null && routeData.startChainage != null && routeData.endChainage != null) {
            val minRoute = minOf(routeData.startChainage.metres, routeData.endChainage.metres)
            val maxRoute = maxOf(routeData.startChainage.metres, routeData.endChainage.metres)
            if (point.originalKmzMetres < minRoute || point.originalKmzMetres > maxRoute) {
                warnings.add("Correction position (${point.originalKmzChainage.format()}) is outside imported KMZ range (${routeData.startChainage.format()} - ${routeData.endChainage.format()}).")
            }
        }

        // 4. Unexpected jump check (> 10 km shift)
        val offset = abs(point.offsetMetres)
        if (offset > 10_000) {
            warnings.add("Large correction shift detected: ${point.displayOffset}. Ensure correct chainage was entered.")
        }

        // 5. Direction progression conflict check
        val sortedOthers = otherPoints.sortedBy { it.originalKmzMetres }
        val prev = sortedOthers.lastOrNull { it.originalKmzMetres < point.originalKmzMetres }
        val next = sortedOthers.firstOrNull { it.originalKmzMetres > point.originalKmzMetres }

        if (prev != null && point.correctedMetres < prev.correctedMetres) {
            warnings.add("Corrected chainage (${point.correctedChainage.format()}) is less than previous point (${prev.correctedChainage.format()}), creating an inversion.")
        }
        if (next != null && point.correctedMetres > next.correctedMetres) {
            warnings.add("Corrected chainage (${point.correctedChainage.format()}) is greater than next point (${next.correctedChainage.format()}), creating an inversion.")
        }

        return CorrectionValidationResult(isValid = true, warnings = warnings)
    }
}
