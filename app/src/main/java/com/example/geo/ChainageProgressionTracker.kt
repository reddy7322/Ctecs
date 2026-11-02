package com.example.geo

import com.example.model.ChainageMatchResult
import com.example.model.MatchConfidence

/**
 * Ensures high stability of KML/KMZ-derived LHS/RHS classification.
 *
 * Requirements:
 * - KML/KMZ route direction is the first and authoritative reference.
 * - Position to the left of the ordered route direction = LHS.
 * - Position to the right of the ordered route direction = RHS.
 * - Chainage increasing or decreasing has ZERO influence on LHS/RHS classification.
 * - Reduces incorrect switching caused by GPS fluctuations or low accuracy.
 * - Applies centerline deadband (within 1.8m of centerline, retains previous side).
 * - Applies hysteresis (requires consecutive reliable samples or large offset with good GPS to switch sides).
 * - Avoids rapid switching when stationary.
 */
class ChainageProgressionTracker {
    private var currentSide: String = "RHS" // Initial default
    private var pendingSide: String? = null
    private var pendingSideCount: Int = 0
    private var lastActiveRouteId: String? = null

    // Deadband near centerline where vehicle could be straddling lanes
    private val CENTERLINE_DEADBAND_METRES = 1.8

    // Number of consecutive consistent samples required to switch sides across centerline
    private val REQUIRED_CONFIRMATIONS = 2

    // Maximum GPS accuracy error allowed to switch sides
    private val MAX_ACCURACY_FOR_SWITCH = 15.0f

    @Synchronized
    fun processResult(result: ChainageMatchResult, routeId: String? = null): ChainageMatchResult {
        // 1. Reset state if route has changed
        if (routeId != null && routeId != lastActiveRouteId) {
            lastActiveRouteId = routeId
            pendingSide = null
            pendingSideCount = 0
            if (result.detectedSide == "LHS" || result.detectedSide == "RHS") {
                currentSide = result.detectedSide
            }
            return result.copy(detectedSide = currentSide)
        }

        // 2. Ignore invalid readings, signal loss, or NO_MATCH
        if (result.confidence == MatchConfidence.NO_MATCH || result.chainage == null) {
            return result.copy(detectedSide = currentSide)
        }

        val rawSide = result.detectedSide
        val accuracy = result.gpsAccuracy
        val offset = result.offsetMetres

        // If raw side is Unknown or unclassified, retain current side
        if (rawSide != "LHS" && rawSide != "RHS") {
            return result.copy(detectedSide = currentSide)
        }

        // 3. Centerline deadband: if offset is within deadband of centerline, retain current side
        if (offset < CENTERLINE_DEADBAND_METRES) {
            pendingSide = null
            pendingSideCount = 0
            return result.copy(detectedSide = currentSide)
        }

        // 4. Low GPS accuracy check: if accuracy uncertainty circle spans across centerline, retain current side
        if (accuracy > MAX_ACCURACY_FOR_SWITCH || accuracy > (offset * 1.8f)) {
            pendingSide = null
            pendingSideCount = 0
            return result.copy(detectedSide = currentSide)
        }

        // 5. If raw side matches current side, reset pending switch
        if (rawSide == currentSide) {
            pendingSide = null
            pendingSideCount = 0
            return result
        }

        // 6. rawSide != currentSide (attempting side switch across centerline)
        // Immediate switch permitted ONLY if offset is large (>= 4.0m) AND GPS accuracy is sharp (<= 8.0m)
        if (offset >= 4.0 && accuracy <= 8.0f) {
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

        return result.copy(detectedSide = currentSide)
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
    }

    @Synchronized
    fun setManualSide(side: String) {
        if (side == "LHS" || side == "RHS") {
            currentSide = side
            pendingSide = null
            pendingSideCount = 0
        }
    }

    @Synchronized
    fun getCurrentSide(): String = currentSide
}
