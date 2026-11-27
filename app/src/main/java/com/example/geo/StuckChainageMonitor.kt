package com.example.geo

import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.GeoPoint
import com.example.model.MatchConfidence

/**
 * Monitors live GPS movement vs. calculated chainage progression to detect unexpected stalls
 * without triggering false recoveries when the device is legitimately stationary.
 */
class StuckChainageMonitor(
    private val movementThresholdMetres: Double = 12.0,
    private val stationaryBandMetres: Double = 4.5,
    private val requiredStalledFixes: Int = 3,
    private val recoveryCooldownMs: Long = 5_000L
) {
    data class StuckDetectionResult(
        val isStuck: Boolean,
        val affectedComponent: String = "",
        val reason: String = ""
    )

    private var anchorGpsPoint: GeoPoint? = null
    private var anchorChainageMetres: Long? = null
    private var lastObservedFixTimeMs: Long = 0L
    private var displacedSameChainageCount: Int = 0
    private var lastRecoveryMs: Long = 0L
    private var currentRouteId: String? = null

    @Synchronized
    fun reset() {
        anchorGpsPoint = null
        anchorChainageMetres = null
        lastObservedFixTimeMs = 0L
        displacedSameChainageCount = 0
    }

    @Synchronized
    fun evaluate(
        gpsPoint: GeoPoint?,
        fixTimeMs: Long,
        matchResult: ChainageMatchResult,
        routeId: String?,
        isManualOverride: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): StuckDetectionResult {
        if (routeId != currentRouteId) {
            currentRouteId = routeId
            reset()
        }

        if (isManualOverride || gpsPoint == null || routeId == null) {
            displacedSameChainageCount = 0
            return StuckDetectionResult(false)
        }

        val chainageMetres = matchResult.chainage?.metres
        if (chainageMetres == null || matchResult.confidence == MatchConfidence.NO_MATCH) {
            displacedSameChainageCount = 0
            return StuckDetectionResult(false)
        }

        val prevAnchor = anchorGpsPoint
        val prevMetres = anchorChainageMetres
        if (prevAnchor == null || prevMetres == null) {
            anchorGpsPoint = gpsPoint
            anchorChainageMetres = chainageMetres
            lastObservedFixTimeMs = fixTimeMs
            displacedSameChainageCount = 0
            return StuckDetectionResult(false)
        }

        if (fixTimeMs > 0L && fixTimeMs == lastObservedFixTimeMs) {
            return StuckDetectionResult(false)
        }
        lastObservedFixTimeMs = fixTimeMs

        val displacement = GeoUtils.distanceMetres(prevAnchor, gpsPoint)

        if (displacement < stationaryBandMetres) {
            displacedSameChainageCount = 0
            return StuckDetectionResult(false)
        }

        if (chainageMetres != prevMetres) {
            anchorGpsPoint = gpsPoint
            anchorChainageMetres = chainageMetres
            displacedSameChainageCount = 0
            return StuckDetectionResult(false)
        }

        if (displacement >= movementThresholdMetres) {
            displacedSameChainageCount++
            val cooldownElapsed = (nowMs - lastRecoveryMs) >= recoveryCooldownMs
            if (displacedSameChainageCount >= requiredStalledFixes && cooldownElapsed) {
                lastRecoveryMs = nowMs
                val component = if (matchResult.isUncertain) {
                    "Spike Hold & Progression Filter"
                } else {
                    "GPS-to-Alignment Matching Engine"
                }
                val reason = "GPS moved ${displacement.toInt()}m across $displacedSameChainageCount fixes while chainage held at ${Chainage(chainageMetres).format()}"
                reset()
                return StuckDetectionResult(
                    isStuck = true,
                    affectedComponent = component,
                    reason = reason
                )
            }
        }

        return StuckDetectionResult(false)
    }
}
