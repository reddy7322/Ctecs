package com.example.geo

import com.example.model.ChainageMatchResult
import com.example.model.MatchConfidence

/**
 * Tracks consecutive valid KMZ/GPS-matched chainage readings to dynamically determine
 * road side progression (LHS vs RHS).
 *
 * Rules:
 * - When chainage increases -> display LHS
 * - When chainage decreases -> display RHS
 * - Ignores invalid readings, temporary GPS signal loss, or zero movement
 * - Prevents flickering from small GPS fluctuations
 */
class ChainageProgressionTracker {
    private var lastValidChainageMetres: Long? = null
    private var currentSide: String = "RHS" // Initial default
    private var pendingSide: String? = null
    private var consecutiveCount: Int = 0

    @Synchronized
    fun processResult(result: ChainageMatchResult): ChainageMatchResult {
        val chainage = result.chainage
        // Only evaluate valid KMZ/GPS-matched readings
        if (chainage == null || result.confidence == MatchConfidence.NO_MATCH) {
            // Signal loss or invalid reading: maintain current side
            return result.copy(detectedSide = currentSide)
        }

        val last = lastValidChainageMetres
        if (last == null) {
            lastValidChainageMetres = chainage.metres
            return result.copy(detectedSide = currentSide)
        }

        val delta = chainage.metres - last

        // Ignore repeated readings or tiny jitter (< 2 metres) to prevent noise
        if (kotlin.math.abs(delta) >= 2) {
            lastValidChainageMetres = chainage.metres

            // Chainage increases -> LHS, Chainage decreases -> RHS
            val newDirectionSide = if (delta > 0) "LHS" else "RHS"

            if (newDirectionSide != currentSide) {
                // Update immediately on significant step (>= 4m) or 2 consecutive readings in same new direction
                if (kotlin.math.abs(delta) >= 4 || (pendingSide == newDirectionSide && consecutiveCount >= 1)) {
                    currentSide = newDirectionSide
                    pendingSide = null
                    consecutiveCount = 0
                } else {
                    pendingSide = newDirectionSide
                    consecutiveCount++
                }
            } else {
                pendingSide = null
                consecutiveCount = 0
            }
        }

        return result.copy(detectedSide = currentSide)
    }

    @Synchronized
    fun reset() {
        lastValidChainageMetres = null
        pendingSide = null
        consecutiveCount = 0
    }
}
