package com.example.geo

import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Intelligent Chainage Progression & Carriageway Side Stabilization Engine.
 *
 * Ensures:
 * - Smooth, continuous chainage progression during highway driving (even across curves or noisy GPS).
 * - Immediate propagation when calibration points/profiles or chainage sources change (even when stationary).
 * - Automatic recovery from temporary GPS drops or large position transitions without freezing.
 * - Stationary jitter deadband (prevents chainage from bouncing +/- 1..2m while vehicle is parked).
 * - Stable LHS / RHS / Service carriageway side detection with median hysteresis.
 */
class ChainageProgressionTracker {

    private var currentRouteId: String? = null
    private var lastValidChainage: Chainage? = null
    private var lastValidRawChainage: Chainage? = null
    private var lastCalibrationOffset: Long? = null
    private var lastChainageSource: ChainageSource? = null
    private var lastProjectedPoint: GeoPoint? = null
    private var smoothedMetres: Double? = null

    // Side stabilization state
    private var currentDetectedSide: String = "RHS"
    private var hasInitialSide: Boolean = false
    private var candidateSide: String? = null
    private var candidateSideCount: Int = 0
    private var manualSideOverride: String? = null

    // Progression direction tracking
    private var lastChainageDelta: Long = 0L

    companion object {
        private const val SIDE_CONFIRMATION_COUNT = 2
        private const val STATIONARY_JITTER_METRES = 1.5
        private const val EMA_ALPHA = 0.72
        private const val SNAP_THRESHOLD_METRES = 25L
    }

    /**
     * Resets internal state when a new route is loaded or calibration changes.
     */
    @Synchronized
    fun reset() {
        lastValidChainage = null
        lastValidRawChainage = null
        lastCalibrationOffset = null
        lastChainageSource = null
        lastProjectedPoint = null
        smoothedMetres = null
        hasInitialSide = false
        candidateSide = null
        candidateSideCount = 0
        lastChainageDelta = 0L
    }

    /**
     * Resets only the chainage smoothing filter while preserving carriageway side state.
     * Used when calibration control points or profiles are added, edited, toggled, or deleted.
     */
    @Synchronized
    fun resetChainageFilter() {
        lastValidChainage = null
        lastValidRawChainage = null
        lastCalibrationOffset = null
        lastChainageSource = null
        lastProjectedPoint = null
        smoothedMetres = null
        lastChainageDelta = 0L
    }

    /**
     * Sets an explicit manual side override ("LHS", "RHS", "BHS").
     */
    @Synchronized
    fun setManualSide(side: String) {
        manualSideOverride = side
        currentDetectedSide = side
    }

    /**
     * Clears manual side override and returns to automatic carriageway detection.
     */
    @Synchronized
    fun clearManualSide() {
        manualSideOverride = null
    }

    @Synchronized
    fun isManualOverride(): Boolean = manualSideOverride != null

    @Synchronized
    fun getCurrentSide(): String = manualSideOverride ?: currentDetectedSide

    /**
     * Processes a raw match result to apply progression smoothing and side hysteresis.
     */
    @Synchronized
    fun processResult(rawResult: ChainageMatchResult, routeId: String?): ChainageMatchResult {
        if (routeId != currentRouteId) {
            currentRouteId = routeId
            reset()
        }

        // If manual chainage is active, return immediately with stabilized side
        if (rawResult.isManual) {
            val side = manualSideOverride ?: rawResult.detectedSide.ifBlank { currentDetectedSide }
            lastChainageSource = rawResult.source
            return rawResult.copy(detectedSide = side)
        }

        // 1. Stabilize Carriageway Side (LHS / RHS / BHS / Service)
        val effectiveSide = if (manualSideOverride != null) {
            manualSideOverride!!
        } else {
            stabilizeSide(rawResult.detectedSide, rawResult.confidence)
        }

        // 2. Handle Off-Route / No Match
        if (rawResult.confidence == MatchConfidence.NO_MATCH || rawResult.chainage == null) {
            return rawResult.copy(
                detectedSide = effectiveSide,
                lastReliableChainage = lastValidChainage
            )
        }

        val rawChainage = rawResult.chainage
        val rawUncorrected = rawResult.rawUncorrectedChainage ?: rawChainage
        val projPt = rawResult.projectedPoint

        // Check if calibration source or calibration offset changed
        val prevOffset = lastCalibrationOffset
        val currOffset = rawChainage.metres - rawUncorrected.metres
        val calibrationChanged = (lastChainageSource != null && lastChainageSource != rawResult.source) ||
            (prevOffset != null && abs(currOffset - prevOffset) >= 2L)

        if (calibrationChanged) {
            // Immediately snap to the newly calibrated/uncalibrated chainage without any stationary lock or EMA lag
            lastValidChainage = rawChainage
            lastValidRawChainage = rawUncorrected
            lastCalibrationOffset = currOffset
            lastChainageSource = rawResult.source
            lastProjectedPoint = projPt
            smoothedMetres = rawChainage.metres.toDouble()
            return rawResult.copy(
                chainage = rawChainage,
                detectedSide = effectiveSide,
                lastReliableChainage = rawChainage
            )
        }

        // 3. Stationary Jitter Filter & Smooth Progression
        val smoothedChainage: Chainage
        val lastCh = lastValidChainage
        val lastProj = lastProjectedPoint

        if (rawResult.isUncertain && lastCh != null) {
            // Retain last valid chainage on single transient uncertain jump
            smoothedChainage = lastCh
        } else if (lastProj != null && lastCh != null && projPt != null) {
            val movedDist = GeoUtils.distanceMetres(projPt, lastProj)
            val deltaMetres = rawChainage.metres - lastCh.metres

            if (movedDist < STATIONARY_JITTER_METRES && abs(deltaMetres) <= 2L) {
                // Vehicle is stationary and chainage only jittered by <= 2m: hold steady
                smoothedChainage = lastCh
            } else if (abs(deltaMetres) >= SNAP_THRESHOLD_METRES) {
                // Significant real movement or jump recovery (>= 25m): snap directly without EMA lag
                smoothedMetres = rawChainage.metres.toDouble()
                smoothedChainage = rawChainage
                lastChainageDelta = deltaMetres
                lastProjectedPoint = projPt
            } else {
                // Apply responsive exponential smoothing for sub-25m steps
                val prevSmooth = smoothedMetres ?: lastCh.metres.toDouble()
                val nextSmooth = EMA_ALPHA * rawChainage.metres.toDouble() + (1.0 - EMA_ALPHA) * prevSmooth
                smoothedMetres = nextSmooth
                smoothedChainage = Chainage(nextSmooth.roundToLong().coerceAtLeast(0L))
                lastChainageDelta = smoothedChainage.metres - lastCh.metres
                lastProjectedPoint = projPt
            }
        } else {
            smoothedChainage = rawChainage
            smoothedMetres = rawChainage.metres.toDouble()
            lastProjectedPoint = projPt
        }

        lastValidChainage = smoothedChainage
        lastValidRawChainage = rawUncorrected
        lastCalibrationOffset = currOffset
        lastChainageSource = rawResult.source

        return rawResult.copy(
            chainage = smoothedChainage,
            detectedSide = effectiveSide,
            lastReliableChainage = lastValidChainage
        )
    }

    private fun stabilizeSide(newSide: String, confidence: MatchConfidence): String {
        if (newSide.isBlank() || newSide == "Unknown" || newSide == "MAIN") {
            return currentDetectedSide
        }
        if (confidence == MatchConfidence.LOW || confidence == MatchConfidence.NO_MATCH) {
            return currentDetectedSide
        }
        if (!hasInitialSide) {
            currentDetectedSide = newSide
            hasInitialSide = true
            candidateSide = null
            candidateSideCount = 0
            return currentDetectedSide
        }
        if (newSide == currentDetectedSide) {
            candidateSide = null
            candidateSideCount = 0
            return currentDetectedSide
        }
        if (newSide == candidateSide) {
            candidateSideCount++
            if (candidateSideCount >= SIDE_CONFIRMATION_COUNT) {
                currentDetectedSide = newSide
                candidateSide = null
                candidateSideCount = 0
            }
        } else {
            candidateSide = newSide
            candidateSideCount = 1
        }
        return currentDetectedSide
    }
}
