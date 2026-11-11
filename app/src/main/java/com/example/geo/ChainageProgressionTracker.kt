package com.example.geo

import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Hybrid 1D Kalman Filter + Weighted Moving Average Smoother for Highway Chainage.
 *
 * Stabilizes flickering chainage values caused by GPS horizontal jitter (typically ±1m to ±6m
 * oscillation when stationary or moving slowly) while remaining immediately responsive to
 * genuine vehicle progression, calibration updates, and manual overrides.
 */
class ChainageKalmanFilter(
    private val windowSize: Int = 4
) {
    private var isInitialized: Boolean = false
    private var xHatMetres: Double = 0.0
    private var errorCovarianceP: Double = 4.0
    private var lastOutputMetres: Long = 0L
    private var lastProjectedPoint: GeoPoint? = null
    private var lastSource: ChainageSource? = null
    private val movingWindow = ArrayDeque<Double>(windowSize)

    companion object {
        // Threshold above which the filter snaps immediately (e.g., calibration change, fast jump, test step)
        private const val SNAP_THRESHOLD_METRES = 25.0

        // Stationary deadband: prevents ±1m–2m flickering when GPS wanders within accuracy circle
        private const val STATIONARY_DISPLACEMENT_METRES = 2.2
        private const val STATIONARY_CHAINAGE_DEADBAND_METRES = 2L
    }

    @Synchronized
    fun reset() {
        isInitialized = false
        xHatMetres = 0.0
        errorCovarianceP = 4.0
        lastOutputMetres = 0L
        lastProjectedPoint = null
        lastSource = null
        movingWindow.clear()
    }

    @Synchronized
    fun filter(
        rawChainageMetres: Long,
        gpsAccuracy: Float,
        projectedPoint: GeoPoint? = null,
        source: ChainageSource = ChainageSource.KMZ
    ): Long {
        val rawDouble = rawChainageMetres.toDouble()

        // 1. Immediate snap on initialization, source change (e.g. calibration toggled), or manual mode
        if (!isInitialized || source == ChainageSource.MANUAL || (lastSource != null && lastSource != source)) {
            snapTo(rawDouble, rawChainageMetres, projectedPoint, source)
            return rawChainageMetres
        }

        val deltaChainage = abs(rawDouble - xHatMetres)

        // 2. Immediate snap if chainage step exceeds snap threshold (> 25m)
        // Ensures zero lag on fast highway jumps, route switches, or discrete test steps
        if (deltaChainage > SNAP_THRESHOLD_METRES) {
            snapTo(rawDouble, rawChainageMetres, projectedPoint, source)
            return rawChainageMetres
        }

        // 3. Measure physical displacement along the alignment if projectedPoint is available
        val spatialStepM = if (projectedPoint != null && lastProjectedPoint != null) {
            GeoUtils.distanceMetres(lastProjectedPoint!!, projectedPoint)
        } else {
            deltaChainage
        }

        // 4. Stationary Deadband Suppression:
        // If GPS accuracy is >= 2.5m and physical movement is within stationary jitter radius (< 2.2m),
        // suppress ±1m to ±2m flicker around the current locked integer chainage.
        if (gpsAccuracy >= 2.5f &&
            spatialStepM <= STATIONARY_DISPLACEMENT_METRES &&
            abs(rawChainageMetres - lastOutputMetres) <= STATIONARY_CHAINAGE_DEADBAND_METRES
        ) {
            // Gently nudge internal state toward measurement with high damping, but keep output locked
            xHatMetres = 0.90 * xHatMetres + 0.10 * rawDouble
            return lastOutputMetres
        }

        // 5. 1D Kalman Filter Predict & Update Step
        // Process noise Q scales with spatial displacement squared (higher when moving, tiny when stationary)
        val qProcessNoise = max(0.20, (spatialStepM * spatialStepM) * 0.45)

        // Measurement noise R scales with GPS accuracy variance
        val clampedAcc = gpsAccuracy.coerceIn(1.5f, 30.0f).toDouble()
        val rMeasurementNoise = max(1.0, (clampedAcc * 0.55) * (clampedAcc * 0.55))

        // Prior error covariance prediction
        val pPred = errorCovarianceP + qProcessNoise

        // Optimal Kalman Gain K in [0, 1]
        val kalmanGain = (pPred / (pPred + rMeasurementNoise)).coerceIn(0.12, 0.92)

        // State update
        xHatMetres += kalmanGain * (rawDouble - xHatMetres)

        // Posterior error covariance update
        errorCovarianceP = (1.0 - kalmanGain) * pPred

        // 6. Weighted Moving Average over recent Kalman states for extra smoothness
        if (movingWindow.size >= windowSize) {
            movingWindow.removeFirst()
        }
        movingWindow.addLast(xHatMetres)

        var weightedSum = 0.0
        var weightTotal = 0.0
        var idx = 1
        for (sample in movingWindow) {
            val weight = idx.toDouble() // Linearly higher weight for newer estimates
            weightedSum += sample * weight
            weightTotal += weight
            idx++
        }
        val smoothedDouble = if (weightTotal > 0.0) weightedSum / weightTotal else xHatMetres
        val candidateMetres = smoothedDouble.roundToLong()

        // 7. Hysteresis of 0.65m to prevent boundary toggling (e.g., 174.49 <-> 174.51)
        val finalMetres = if (abs(candidateMetres - lastOutputMetres) == 1L &&
            abs(smoothedDouble - lastOutputMetres.toDouble()) < 0.65
        ) {
            lastOutputMetres
        } else {
            candidateMetres
        }

        lastOutputMetres = finalMetres
        if (projectedPoint != null) {
            lastProjectedPoint = projectedPoint
        }
        lastSource = source
        return finalMetres
    }

    private fun snapTo(
        rawDouble: Double,
        rawMetres: Long,
        projectedPoint: GeoPoint?,
        source: ChainageSource
    ) {
        isInitialized = true
        xHatMetres = rawDouble
        errorCovarianceP = 2.0
        lastOutputMetres = rawMetres
        lastProjectedPoint = projectedPoint
        lastSource = source
        movingWindow.clear()
        movingWindow.addLast(rawDouble)
    }
}

/**
 * Ensures high stability of KML/KMZ-derived chainage values (via 1D Kalman + Moving Average filter)
 * and LHS/RHS carriageway side classification.
 *
 * Requirements:
 * - Stabilizes flickering chainage readings caused by GPS jitter using [ChainageKalmanFilter].
 * - KML/KMZ route direction is the first and authoritative reference.
 * - Perpendicular offset to the left/right of ordered route direction determines LHS/RHS.
 * - Reduces incorrect switching caused by GPS fluctuations or low accuracy.
 * - Applies centerline deadband (within 1.8m of centerline, retains previous side).
 * - Applies hysteresis (requires consecutive reliable samples or large offset with good GPS to switch sides).
 * - Avoids rapid switching when stationary or during signal loss.
 * - Manual LHS/RHS override takes precedence until cleared or set back to Auto.
 */
class ChainageProgressionTracker {
    private var currentSide: String = "RHS" // Initial default
    private var pendingSide: String? = null
    private var pendingSideCount: Int = 0
    private var lastActiveRouteId: String? = null
    private var isManualSideOverride: Boolean = false

    private val chainageKalmanFilter = ChainageKalmanFilter()

    // Deadband near centerline where vehicle could be straddling lanes
    private val CENTERLINE_DEADBAND_METRES = 1.8

    // Number of consecutive consistent samples required to switch sides across centerline
    private val REQUIRED_CONFIRMATIONS = 2

    // Maximum GPS accuracy error allowed to switch sides
    private val MAX_ACCURACY_FOR_SWITCH = 15.0f

    @Synchronized
    fun processResult(result: ChainageMatchResult, routeId: String? = null): ChainageMatchResult {
        // 1. Reset filter & side state if route has changed
        if (routeId != null && routeId != lastActiveRouteId) {
            lastActiveRouteId = routeId
            pendingSide = null
            pendingSideCount = 0
            chainageKalmanFilter.reset()
            if (!isManualSideOverride && (result.detectedSide == "LHS" || result.detectedSide == "RHS" || result.detectedSide == "MEDIAN" || result.detectedSide == "BHS")) {
                currentSide = result.detectedSide
            }
        }

        // 2. Ignore invalid readings, signal loss, or NO_MATCH (retain last reliable side)
        if (result.confidence == MatchConfidence.NO_MATCH || result.chainage == null) {
            return result.copy(detectedSide = currentSide)
        }

        // 3. Stabilize chainage value using Kalman + Moving Average filter
        val stabilizedChainageMetres = chainageKalmanFilter.filter(
            rawChainageMetres = result.chainage.metres,
            gpsAccuracy = result.gpsAccuracy,
            projectedPoint = result.projectedPoint,
            source = result.source
        )
        val stabilizedChainage = Chainage(stabilizedChainageMetres)

        // Also stabilize rawUncorrectedChainage consistently if present
        val stabilizedRawChainage = if (result.rawUncorrectedChainage != null && result.chainage.metres != 0L) {
            val calibrationDelta = result.chainage.metres - result.rawUncorrectedChainage.metres
            Chainage(stabilizedChainageMetres - calibrationDelta)
        } else {
            result.rawUncorrectedChainage
        }

        // 4. If user explicitly set manual side override, respect it until cleared/reset
        if (isManualSideOverride) {
            return result.copy(
                chainage = stabilizedChainage,
                rawUncorrectedChainage = stabilizedRawChainage,
                detectedSide = currentSide
            )
        }

        val rawSide = result.detectedSide
        val accuracy = result.gpsAccuracy
        val offset = result.offsetMetres

        // If raw side is Unknown or unclassified, retain current side
        if (rawSide != "LHS" && rawSide != "RHS" && rawSide != "MEDIAN" && rawSide != "BHS") {
            return result.copy(
                chainage = stabilizedChainage,
                rawUncorrectedChainage = stabilizedRawChainage,
                detectedSide = currentSide
            )
        }

        // 5. Centerline deadband: if offset is within deadband of centerline, retain current side
        if (offset < CENTERLINE_DEADBAND_METRES) {
            pendingSide = null
            pendingSideCount = 0
            return result.copy(
                chainage = stabilizedChainage,
                rawUncorrectedChainage = stabilizedRawChainage,
                detectedSide = currentSide
            )
        }

        // 6. Low GPS accuracy check: if accuracy uncertainty circle spans across centerline, retain current side
        if (accuracy > MAX_ACCURACY_FOR_SWITCH || accuracy > (offset * 1.8f)) {
            pendingSide = null
            pendingSideCount = 0
            return result.copy(
                chainage = stabilizedChainage,
                rawUncorrectedChainage = stabilizedRawChainage,
                detectedSide = currentSide
            )
        }

        // 7. If raw side matches current side, reset pending switch
        if (rawSide == currentSide) {
            pendingSide = null
            pendingSideCount = 0
            return result.copy(
                chainage = stabilizedChainage,
                rawUncorrectedChainage = stabilizedRawChainage,
                detectedSide = currentSide
            )
        }

        // 8. rawSide != currentSide (attempting side switch across centerline)
        // Immediate switch permitted ONLY if offset is large (>= 3.5m) AND GPS accuracy is sharp (<= 8.0m)
        if (offset >= 3.5 && accuracy <= 8.0f) {
            currentSide = rawSide
            pendingSide = null
            pendingSideCount = 0
        } else {
            // Otherwise require consecutive confirmed samples to prevent single-sample GPS jitter
            if (pendingSide == rawSide) {
                pendingSideCount++
                if (pendingSideCount >= REQUIRED_CONFIRMATIONS) {
                    currentSide = rawSide
                    pendingSide = null
                    pendingSideCount = 0
                }
            } else {
                pendingSide = rawSide
                pendingSideCount = 1
            }
        }

        return result.copy(
            chainage = stabilizedChainage,
            rawUncorrectedChainage = stabilizedRawChainage,
            detectedSide = currentSide
        )
    }

    @Synchronized
    fun processResult(result: ChainageMatchResult): ChainageMatchResult {
        return processResult(result, null)
    }

    @Synchronized
    fun reset() {
        pendingSide = null
        pendingSideCount = 0
        lastActiveRouteId = null
        isManualSideOverride = false
        chainageKalmanFilter.reset()
    }

    @Synchronized
    fun resetChainageFilter() {
        chainageKalmanFilter.reset()
    }

    @Synchronized
    fun setManualSide(side: String) {
        if (side == "LHS" || side == "RHS" || side == "MEDIAN" || side == "BHS") {
            currentSide = side
            isManualSideOverride = true
            pendingSide = null
            pendingSideCount = 0
        } else if (side.equals("AUTO", ignoreCase = true)) {
            isManualSideOverride = false
            pendingSide = null
            pendingSideCount = 0
        }
    }

    @Synchronized
    fun clearManualSide() {
        isManualSideOverride = false
        pendingSide = null
        pendingSideCount = 0
    }

    @Synchronized
    fun isManualOverride(): Boolean = isManualSideOverride

    @Synchronized
    fun getCurrentSide(): String = currentSide
}
