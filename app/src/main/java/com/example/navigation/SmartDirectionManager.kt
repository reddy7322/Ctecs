package com.example.navigation

import com.example.geo.ChainageMatcher
import com.example.geo.GeoUtils
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.AlignmentDirectionConvention
import com.example.model.ChainageMatchResult
import com.example.model.ChainageProgressionTrend
import com.example.model.DirectionControlMode
import com.example.model.DirectionDetectionState
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.SmartDirectionPreferences
import com.example.model.SmartDirectionState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

/**
 * Single authoritative application-wide LHS/RHS Smart Direction Detection Engine.
 *
 * Core engineering guarantees:
 * 1. Reuses CTECS's existing GPS fixes ([LocationState]) and map-matched KMZ/KML alignment
 *    geometry ([ChainageMatchResult] + [ChainageMatcher.getOrCreateCachedGeometry]) — zero duplicate
 *    GPS listeners or competing alignment calculations.
 * 2. Never assumes increasing chainage always equals LHS: resolves the exact relationship from
 *    imported alignment metadata (`carriagewayType`, `direction`, alignment name) and user convention.
 * 3. Uses consecutive GPS positions along the alignment polyline + calibrated chainage progression +
 *    alignment tangent geometry (and speed-gated GPS course bearing as a secondary corroborator).
 *    Never relies exclusively on phone compass or magnetic heading.
 * 4. Ignores unreliable heading when stationary or moving slowly, filters GPS drift and temporary
 *    reverse movement, and requires multi-observation confirmation before flipping LHS <-> RHS.
 * 5. Never publishes a guessed direction when GPS is weak, alignment is unavailable, or movement
 *    evidence is insufficient.
 * 6. Resets stale route direction state immediately when switching active KMZ/KML alignments.
 */
class SmartDirectionManager {

    private data class FilteredSample(
        val point: GeoPoint,
        val projectedPoint: GeoPoint?,
        val distanceAlongM: Double,
        val chainageM: Long,
        val timestampMs: Long,
        val accuracyM: Float,
        val speedMps: Float?,
        val gpsBearingDeg: Float?,
        val segmentIndex: Int
    )

    private var activeRouteId: String? = null
    private var activeAlignmentId: String? = null
    private var activeConvention: AlignmentDirectionConvention = AlignmentDirectionConvention.INCREASING_IS_LHS

    private val recentSamples = ArrayDeque<FilteredSample>()
    private var confirmedSide: String? = null
    private var confirmedIncreasing: Boolean? = null
    private var lastConfirmedTimestampMs: Long = 0L
    private var lastDirectionChangeTimestampMs: Long = 0L

    // Confirmation hysteresis buffers for initial lock and genuine direction reversals
    private var pendingSide: String? = null
    private var pendingConsecutiveCount: Int = 0
    private var pendingCumulativeDisplacementM: Double = 0.0
    private var pendingFirstTimestampMs: Long = 0L

    private var lastPublishedState: SmartDirectionState = SmartDirectionState.alignmentUnavailable()

    @Synchronized
    fun reset() {
        activeRouteId = null
        activeAlignmentId = null
        recentSamples.clear()
        confirmedSide = null
        confirmedIncreasing = null
        lastConfirmedTimestampMs = 0L
        lastDirectionChangeTimestampMs = 0L
        clearPendingCandidate()
        lastPublishedState = SmartDirectionState.alignmentUnavailable()
    }

    @Synchronized
    fun onAlignmentChanged(routeId: String?, alignmentId: String?) {
        if (routeId != activeRouteId || alignmentId != activeAlignmentId) {
            activeRouteId = routeId
            activeAlignmentId = alignmentId
            recentSamples.clear()
            confirmedSide = null
            confirmedIncreasing = null
            lastConfirmedTimestampMs = 0L
            clearPendingCandidate()
            lastPublishedState = SmartDirectionState.alignmentUnavailable(routeId, alignmentId)
        }
    }

    @Synchronized
    fun currentState(): SmartDirectionState = lastPublishedState

    /**
     * Evaluates the latest GPS fix and map-matched alignment result to produce the single
     * authoritative [SmartDirectionState] synchronized across the entire CTECS application.
     */
    @Synchronized
    fun evaluate(
        locationState: LocationState?,
        matchResult: ChainageMatchResult,
        routeData: RouteData?,
        preferences: SmartDirectionPreferences = SmartDirectionPreferences(),
        nowMs: Long = System.currentTimeMillis()
    ): SmartDirectionState {
        val routeId = routeData?.id
        val activeAlign = resolveMatchedOrActiveAlignment(routeData, matchResult)
        val alignmentId = activeAlign?.id

        // 1. Reset stale automatic direction whenever route or selected alignment switches
        val alignmentSwitched = (routeId != activeRouteId) || (alignmentId != activeAlignmentId)
        if (alignmentSwitched) {
            activeRouteId = routeId
            activeAlignmentId = alignmentId
            recentSamples.clear()
            confirmedSide = null
            confirmedIncreasing = null
            lastConfirmedTimestampMs = 0L
            clearPendingCandidate()
        }

        // Resolve the effective chainage-to-side convention for the active alignment
        val resolvedConvention = resolveEffectiveConvention(
            routeData = routeData,
            activeAlignment = activeAlign,
            requested = preferences.alignmentConvention
        )
        if (resolvedConvention != activeConvention) {
            activeConvention = resolvedConvention
            // Re-map confirmed increasing/decreasing relationship if convention changed
            confirmedSide?.let { side ->
                confirmedIncreasing = isIncreasingForSide(side, resolvedConvention)
            }
        }

        // 2. Explicit Manual Override (clearly distinguished; never corrupts alignment calibration)
        if (preferences.controlMode == DirectionControlMode.MANUAL_OVERRIDE) {
            val manualSide = preferences.normalizedManualSide
            if (manualSide != null) {
                val inc = when (manualSide) {
                    "LHS", "RHS" -> isIncreasingForSide(manualSide, resolvedConvention)
                    else -> null
                }
                val detState = when (manualSide) {
                    "LHS" -> DirectionDetectionState.LHS_CONFIRMED
                    "RHS" -> DirectionDetectionState.RHS_CONFIRMED
                    else -> DirectionDetectionState.DIRECTION_UNCERTAIN
                }
                val trend = when (inc) {
                    true -> ChainageProgressionTrend.INCREASING
                    false -> ChainageProgressionTrend.DECREASING
                    null -> ChainageProgressionTrend.UNKNOWN
                }
                val manualState = SmartDirectionState(
                    detectionState = detState,
                    confirmedSide = manualSide,
                    pendingCandidateSide = null,
                    chainageTrend = trend,
                    isIncreasingChainage = inc,
                    directionArrow = trend.arrow,
                    isManualOverride = true,
                    confidenceScore = 1f,
                    routeId = routeId,
                    alignmentId = alignmentId,
                    effectiveConvention = resolvedConvention,
                    statusLabel = "$manualSide (Manual)",
                    detailReason = "Manual direction override active",
                    lastConfirmedTimestampMs = nowMs,
                    lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
                    directionChangedFrom = null
                )
                lastPublishedState = manualState
                return manualState
            }
        }

        // 3. Check Alignment Availability
        if (routeData == null || activeAlign == null || activeAlign.points.size < 2) {
            confirmedSide = null
            confirmedIncreasing = null
            clearPendingCandidate()
            val state = SmartDirectionState.alignmentUnavailable(routeId, alignmentId).copy(
                effectiveConvention = resolvedConvention
            )
            lastPublishedState = state
            return state
        }

        // 4. Check GPS Signal Quality (never publish guessed direction when GPS is weak or lost)
        val fixAgeMs = if (locationState != null && locationState.lastFixTime > 0L) {
            (nowMs - locationState.lastFixTime).coerceAtLeast(0L)
        } else {
            0L
        }
        val isGpsWeak = locationState == null ||
            locationState.point == null ||
            !locationState.isUsable ||
            locationState.status == LocationStatus.PERMISSION_DENIED ||
            locationState.status == LocationStatus.DISABLED ||
            locationState.status == LocationStatus.POOR_ACCURACY ||
            locationState.accuracy > MAX_RELIABLE_GPS_ACCURACY_M ||
            fixAgeMs > MAX_GPS_FIX_AGE_MS

        if (isGpsWeak) {
            clearPendingCandidate()
            val reason = when {
                locationState == null || locationState.point == null -> "Waiting for GPS signal"
                locationState.status == LocationStatus.DISABLED -> "GPS is turned off"
                locationState.status == LocationStatus.PERMISSION_DENIED -> "Location permission required"
                fixAgeMs > MAX_GPS_FIX_AGE_MS -> "GPS fix is stale"
                else -> String.format(Locale.US, "GPS accuracy weak (±%.0fm)", locationState.accuracy)
            }
            val state = SmartDirectionState(
                detectionState = DirectionDetectionState.GPS_SIGNAL_WEAK,
                confirmedSide = null,
                pendingCandidateSide = null,
                chainageTrend = ChainageProgressionTrend.UNKNOWN,
                isIncreasingChainage = null,
                directionArrow = "",
                isManualOverride = false,
                confidenceScore = 0f,
                routeId = routeId,
                alignmentId = alignmentId,
                effectiveConvention = resolvedConvention,
                statusLabel = DirectionDetectionState.GPS_SIGNAL_WEAK.shortLabel,
                detailReason = reason,
                lastConfirmedTimestampMs = lastConfirmedTimestampMs,
                lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
                directionChangedFrom = null
            )
            lastPublishedState = state
            return state
        }

        // 5. Check Map-Matched Alignment Validity
        val currentChainage = matchResult.chainage
        if (currentChainage == null ||
            matchResult.confidence == MatchConfidence.NO_MATCH ||
            matchResult.offsetMetres > MAX_ALIGNMENT_CORRIDOR_OFFSET_M
        ) {
            clearPendingCandidate()
            val state = SmartDirectionState(
                detectionState = DirectionDetectionState.ALIGNMENT_UNAVAILABLE,
                confirmedSide = null,
                pendingCandidateSide = null,
                chainageTrend = ChainageProgressionTrend.UNKNOWN,
                isIncreasingChainage = null,
                directionArrow = "",
                isManualOverride = false,
                confidenceScore = 0f,
                routeId = routeId,
                alignmentId = alignmentId,
                effectiveConvention = resolvedConvention,
                statusLabel = DirectionDetectionState.ALIGNMENT_UNAVAILABLE.shortLabel,
                detailReason = "Vehicle is off the active road alignment",
                lastConfirmedTimestampMs = lastConfirmedTimestampMs,
                lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
                directionChangedFrom = null
            )
            lastPublishedState = state
            return state
        }

        // Ignore transient GPS spike / uncertain match without flipping confirmed direction
        if (matchResult.isUncertain ||
            (matchResult.confidence == MatchConfidence.LOW && matchResult.offsetMetres > 45.0)
        ) {
            clearPendingCandidate()
            val heldSide = confirmedSide
            val heldInc = confirmedIncreasing
            val state = if (heldSide != null) {
                buildConfirmedOrHeldState(
                    detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
                    side = heldSide,
                    isIncreasing = heldInc,
                    confidence = 0.35f,
                    routeId = routeId,
                    alignmentId = alignmentId,
                    convention = resolvedConvention,
                    detailReason = "Transient alignment uncertainty — holding previous direction",
                    nowMs = nowMs,
                    changedFrom = null
                )
            } else {
                SmartDirectionState(
                    detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
                    confirmedSide = null,
                    pendingCandidateSide = null,
                    chainageTrend = ChainageProgressionTrend.UNKNOWN,
                    isIncreasingChainage = null,
                    directionArrow = "",
                    isManualOverride = false,
                    confidenceScore = 0.2f,
                    routeId = routeId,
                    alignmentId = alignmentId,
                    effectiveConvention = resolvedConvention,
                    statusLabel = DirectionDetectionState.DIRECTION_UNCERTAIN.shortLabel,
                    detailReason = "Alignment confidence low — verifying direction",
                    lastConfirmedTimestampMs = lastConfirmedTimestampMs,
                    lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
                    directionChangedFrom = null
                )
            }
            lastPublishedState = state
            return state
        }

        // 6. Record filtered sample into short trajectory window
        val gpsPoint = locationState!!.point!!
        val speedMps = locationState.speed?.takeIf { !it.isNaN() && it >= 0f }
        val sample = FilteredSample(
            point = gpsPoint,
            projectedPoint = matchResult.projectedPoint,
            distanceAlongM = matchResult.distanceAlongAlignmentMetres,
            chainageM = currentChainage.metres,
            timestampMs = nowMs,
            accuracyM = locationState.accuracy,
            speedMps = speedMps,
            gpsBearingDeg = locationState.bearing?.takeIf { !it.isNaN() },
            segmentIndex = matchResult.matchedSegmentIndex
        )
        val prevSample = recentSamples.lastOrNull()

        // Guard against extreme impossible teleport (> 400m in < 2s) which indicates GPS jump
        if (prevSample != null) {
            val dtSec = ((nowMs - prevSample.timestampMs).coerceAtLeast(1L)) / 1000.0
            val stepM = abs(sample.distanceAlongM - prevSample.distanceAlongM)
            if (dtSec < 2.0 && stepM > 350.0) {
                recentSamples.clear()
            }
        }
        recentSamples.addLast(sample)
        while (recentSamples.size > MAX_SAMPLE_WINDOW) {
            recentSamples.removeFirst()
        }

        // 7. Compute alignment-relative movement and stationary / slow-speed classification
        val baselineSample = selectMovementBaselineSample(sample)
        val stepChainageDeltaM = if (prevSample != null) (sample.chainageM - prevSample.chainageM).toDouble() else 0.0
        val stepAlignDeltaM = if (prevSample != null) (sample.distanceAlongM - prevSample.distanceAlongM) else 0.0
        val netChainageDeltaM = if (baselineSample != null) (sample.chainageM - baselineSample.chainageM).toDouble() else 0.0
        val netAlignDeltaM = if (baselineSample != null) (sample.distanceAlongM - baselineSample.distanceAlongM) else 0.0
        val rawGpsMoveM = if (baselineSample != null) GeoUtils.distanceMetres(baselineSample.point, sample.point) else 0.0
        val stepGpsMoveM = if (prevSample != null) GeoUtils.distanceMetres(prevSample.point, sample.point) else 0.0

        val isStationaryBySpeed = speedMps != null && speedMps < STATIONARY_SPEED_MPS
        val isStationaryByDisplacement = abs(netAlignDeltaM) < MIN_DISPLACEMENT_FOR_MOVEMENT_M &&
            abs(netChainageDeltaM) < MIN_DISPLACEMENT_FOR_MOVEMENT_M &&
            rawGpsMoveM < MIN_GPS_DISPLACEMENT_M
        val isStepStationary = prevSample != null &&
            abs(stepAlignDeltaM) < STEP_JITTER_DEADBAND_M &&
            abs(stepChainageDeltaM) < STEP_JITTER_DEADBAND_M &&
            stepGpsMoveM < STEP_JITTER_DEADBAND_M

        if (isStationaryBySpeed && isStationaryByDisplacement) {
            clearPendingCandidate()
            val heldSide = confirmedSide
            val heldInc = confirmedIncreasing
            val state = SmartDirectionState(
                detectionState = DirectionDetectionState.STATIONARY,
                confirmedSide = heldSide,
                pendingCandidateSide = null,
                chainageTrend = if (heldInc == true) {
                    ChainageProgressionTrend.INCREASING
                } else if (heldInc == false) {
                    ChainageProgressionTrend.DECREASING
                } else {
                    ChainageProgressionTrend.STATIONARY
                },
                isIncreasingChainage = heldInc,
                directionArrow = when (heldInc) {
                    true -> "↑"
                    false -> "↓"
                    null -> "•"
                },
                isManualOverride = false,
                confidenceScore = if (heldSide != null) 0.78f else 0f,
                routeId = routeId,
                alignmentId = alignmentId,
                effectiveConvention = resolvedConvention,
                statusLabel = heldSide ?: DirectionDetectionState.STATIONARY.shortLabel,
                detailReason = if (heldSide != null) {
                    "Vehicle stationary — holding confirmed $heldSide"
                } else {
                    "Vehicle stationary — move along highway to detect direction"
                },
                lastConfirmedTimestampMs = lastConfirmedTimestampMs,
                lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
                directionChangedFrom = null
            )
            lastPublishedState = state
            return state
        }

        // 8. Determine Alignment Tangent Bearing at matched segment & check movement vector agreement
        val cachedGeometry = ChainageMatcher.getOrCreateCachedGeometry(activeAlign, routeData.markers)
        val alignmentTangentDeg = computeAlignmentTangentBearing(
            cached = cachedGeometry,
            segmentIndex = matchResult.matchedSegmentIndex
        )

        // Determine if chainage increases along polyline direction (p1 -> p2)
        val alignmentChainageIncreasesForward = isAlignmentChainageIncreasingForward(cachedGeometry, activeAlign)

        // Primary Cue: Calibrated chainage & alignment distance progression from consecutive positions
        val effectiveDeltaM = if (abs(netChainageDeltaM) >= 2.0) netChainageDeltaM else {
            if (alignmentChainageIncreasesForward) netAlignDeltaM else -netAlignDeltaM
        }
        val effectiveStepDeltaM = if (abs(stepChainageDeltaM) >= 1.5) stepChainageDeltaM else {
            if (alignmentChainageIncreasesForward) stepAlignDeltaM else -stepAlignDeltaM
        }

        // Check geometric vector alignment (prevProjected -> curProjected vs alignment tangent)
        var vectorAlignmentCos = 0.0
        if (alignmentTangentDeg != null && baselineSample != null && rawGpsMoveM >= 3.5) {
            val moveBearing = GeoUtils.bearingDegrees(
                baselineSample.projectedPoint ?: baselineSample.point,
                sample.projectedPoint ?: sample.point
            )
            vectorAlignmentCos = cos(Math.toRadians(angleDifferenceDegrees(moveBearing, alignmentTangentDeg)))
        }

        // Secondary Cue (ONLY when moving >= 2.5 m/s with good GPS accuracy <= 18m): GPS course bearing
        var gpsBearingCos: Double? = null
        if (alignmentTangentDeg != null &&
            sample.gpsBearingDeg != null &&
            (speedMps == null || speedMps >= MIN_SPEED_FOR_GPS_BEARING_MPS) &&
            sample.accuracyM <= 18f
        ) {
            gpsBearingCos = cos(Math.toRadians(angleDifferenceDegrees(sample.gpsBearingDeg.toDouble(), alignmentTangentDeg)))
        }

        // Check if movement is strictly lateral/noisy jitter rather than longitudinal travel along road
        val isSlowSpeed = speedMps != null && speedMps < SLOW_SPEED_MPS
        val minStepForObservation = if (isSlowSpeed) SLOW_SPEED_STEP_M else NORMAL_STEP_M

        val observedIncreasing: Boolean? = when {
            abs(effectiveStepDeltaM) >= minStepForObservation && abs(effectiveDeltaM) >= minStepForObservation -> {
                // Step and window net movement agree
                if ((effectiveStepDeltaM > 0.0) == (effectiveDeltaM > 0.0)) {
                    effectiveDeltaM > 0.0
                } else {
                    // Instantaneous step opposes window (e.g. just started U-turn or reversing)
                    if (abs(effectiveStepDeltaM) >= 4.0) effectiveStepDeltaM > 0.0 else null
                }
            }
            abs(effectiveDeltaM) >= (if (isSlowSpeed) 8.0 else 5.5) -> {
                effectiveDeltaM > 0.0
            }
            // Dedicated dual-carriageway alignment (explicit LHS or RHS polyline) with strong match
            (activeAlign.carriagewayType.equals("LHS", ignoreCase = true) ||
                activeAlign.carriagewayType.equals("RHS", ignoreCase = true)) &&
                matchResult.confidence == MatchConfidence.HIGH &&
                matchResult.offsetMetres <= 18.0 &&
                abs(effectiveDeltaM) >= 3.0 -> {
                isIncreasingForSide(activeAlign.carriagewayType.uppercase(Locale.ROOT), resolvedConvention)
            }
            else -> null
        }

        // Validate observation against alignment geometry & filter GPS drift
        val stepDisplacementAbs = abs(effectiveStepDeltaM).coerceAtLeast(abs(stepAlignDeltaM))
        val observedSide: String? = observedIncreasing?.let { inc ->
            sideForProgression(isIncreasing = inc, convention = resolvedConvention)
        }

        // Confidence scoring combining GPS accuracy, map-match offset, vector agreement, and bearing corroboration
        val observationConfidence = computeObservationConfidence(
            accuracyM = sample.accuracyM,
            offsetM = matchResult.offsetMetres,
            vectorCos = vectorAlignmentCos,
            gpsBearingCos = gpsBearingCos,
            alignmentChainageIncreasesForward = alignmentChainageIncreasesForward,
            observedIncreasing = observedIncreasing,
            dedicatedCarriageway = activeAlign.carriagewayType.uppercase(Locale.ROOT),
            observedSide = observedSide,
            isSlowSpeed = isSlowSpeed
        )

        // If movement is too small or confidence too weak, hold existing confirmed state or report UNCERTAIN
        if (observedSide == null || observedIncreasing == null || isStepStationary || observationConfidence < MIN_OBSERVATION_CONFIDENCE) {
            val heldSide = confirmedSide
            val heldInc = confirmedIncreasing
            val state = if (heldSide != null) {
                val detState = if (isStepStationary && (speedMps == null || speedMps < STATIONARY_SPEED_MPS)) {
                    DirectionDetectionState.STATIONARY
                } else {
                    if (heldSide == "LHS") DirectionDetectionState.LHS_CONFIRMED else DirectionDetectionState.RHS_CONFIRMED
                }
                buildConfirmedOrHeldState(
                    detectionState = detState,
                    side = heldSide,
                    isIncreasing = heldInc,
                    confidence = 0.80f,
                    routeId = routeId,
                    alignmentId = alignmentId,
                    convention = resolvedConvention,
                    detailReason = if (detState == DirectionDetectionState.STATIONARY) {
                        "Vehicle stationary — holding confirmed $heldSide"
                    } else {
                        "Confirmed $heldSide along ${activeAlign.name}"
                    },
                    nowMs = nowMs,
                    changedFrom = null
                )
            } else {
                SmartDirectionState(
                    detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
                    confirmedSide = null,
                    pendingCandidateSide = pendingSide,
                    chainageTrend = ChainageProgressionTrend.UNKNOWN,
                    isIncreasingChainage = null,
                    directionArrow = "",
                    isManualOverride = false,
                    confidenceScore = observationConfidence,
                    routeId = routeId,
                    alignmentId = alignmentId,
                    effectiveConvention = resolvedConvention,
                    statusLabel = DirectionDetectionState.DIRECTION_UNCERTAIN.shortLabel,
                    detailReason = "Checking road alignment and travel movement",
                    lastConfirmedTimestampMs = lastConfirmedTimestampMs,
                    lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
                    directionChangedFrom = null
                )
            }
            lastPublishedState = state
            return state
        }

        // 9. State Machine: Initial Confirmation vs Steady Travel vs Direction Change / U-Turn Detection
        val currentConfirmed = confirmedSide
        if (currentConfirmed == null) {
            // Accumulate reliable evidence before initial confirmation (never guess on 1 noisy fix!)
            accumulatePending(observedSide, stepDisplacementAbs.coerceAtLeast(abs(netAlignDeltaM) / 2.0), nowMs)
            val reqCount = if (isSlowSpeed) INITIAL_CONFIRM_COUNT_SLOW else INITIAL_CONFIRM_COUNT_NORMAL
            val reqDistM = if (isSlowSpeed) INITIAL_CONFIRM_DIST_SLOW_M else INITIAL_CONFIRM_DIST_NORMAL_M
            val effectiveDistM = maxOf(pendingCumulativeDisplacementM, abs(netAlignDeltaM), abs(netChainageDeltaM))

            if (pendingConsecutiveCount >= reqCount && effectiveDistM >= reqDistM) {
                confirmedSide = observedSide
                confirmedIncreasing = observedIncreasing
                lastConfirmedTimestampMs = nowMs
                clearPendingCandidate()
                val state = buildConfirmedOrHeldState(
                    detectionState = if (observedSide == "LHS") {
                        DirectionDetectionState.LHS_CONFIRMED
                    } else {
                        DirectionDetectionState.RHS_CONFIRMED
                    },
                    side = observedSide,
                    isIncreasing = observedIncreasing,
                    confidence = observationConfidence,
                    routeId = routeId,
                    alignmentId = alignmentId,
                    convention = resolvedConvention,
                    detailReason = "Confirmed $observedSide (${if (observedIncreasing) "Increasing" else "Decreasing"} chainage)",
                    nowMs = nowMs,
                    changedFrom = null
                )
                lastPublishedState = state
                return state
            } else {
                val state = SmartDirectionState(
                    detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
                    confirmedSide = null,
                    pendingCandidateSide = pendingSide,
                    chainageTrend = if (observedIncreasing) {
                        ChainageProgressionTrend.INCREASING
                    } else {
                        ChainageProgressionTrend.DECREASING
                    },
                    isIncreasingChainage = null,
                    directionArrow = "",
                    isManualOverride = false,
                    confidenceScore = observationConfidence * 0.6f,
                    routeId = routeId,
                    alignmentId = alignmentId,
                    effectiveConvention = resolvedConvention,
                    statusLabel = DirectionDetectionState.DIRECTION_UNCERTAIN.shortLabel,
                    detailReason = "Verifying travel direction along alignment",
                    lastConfirmedTimestampMs = lastConfirmedTimestampMs,
                    lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
                    directionChangedFrom = null
                )
                lastPublishedState = state
                return state
            }
        }

        // Case B: Observation agrees with current confirmed direction -> reinforce & clear any transient reversal
        if (observedSide == currentConfirmed) {
            clearPendingCandidate()
            confirmedIncreasing = observedIncreasing
            lastConfirmedTimestampMs = nowMs
            val state = buildConfirmedOrHeldState(
                detectionState = if (currentConfirmed == "LHS") {
                    DirectionDetectionState.LHS_CONFIRMED
                } else {
                    DirectionDetectionState.RHS_CONFIRMED
                },
                side = currentConfirmed,
                isIncreasing = observedIncreasing,
                confidence = observationConfidence,
                routeId = routeId,
                alignmentId = alignmentId,
                convention = resolvedConvention,
                detailReason = "Confirmed $currentConfirmed (${if (observedIncreasing) "Increasing" else "Decreasing"} chainage)",
                nowMs = nowMs,
                changedFrom = null
            )
            lastPublishedState = state
            return state
        }

        // Case C: Observation opposes current confirmed direction -> distinguish temporary reverse/drift vs genuine U-turn / reversal
        accumulatePending(observedSide, stepDisplacementAbs, nowMs)
        val changeReqCount = if (isSlowSpeed) CHANGE_CONFIRM_COUNT_SLOW else CHANGE_CONFIRM_COUNT_NORMAL
        val changeReqDistM = if (isSlowSpeed) CHANGE_CONFIRM_DIST_SLOW_M else CHANGE_CONFIRM_DIST_NORMAL_M

        val isGenuineDirectionChange =
            (pendingConsecutiveCount >= changeReqCount && pendingCumulativeDisplacementM >= changeReqDistM) ||
                (pendingConsecutiveCount >= 2 && pendingCumulativeDisplacementM >= FAST_UTURN_CONFIRM_DIST_M && observationConfidence >= 0.72f)

        if (isGenuineDirectionChange) {
            val previousSide = currentConfirmed
            confirmedSide = observedSide
            confirmedIncreasing = observedIncreasing
            lastConfirmedTimestampMs = nowMs
            lastDirectionChangeTimestampMs = nowMs
            clearPendingCandidate()

            val state = buildConfirmedOrHeldState(
                detectionState = if (observedSide == "LHS") {
                    DirectionDetectionState.LHS_CONFIRMED
                } else {
                    DirectionDetectionState.RHS_CONFIRMED
                },
                side = observedSide,
                isIncreasing = observedIncreasing,
                confidence = observationConfidence,
                routeId = routeId,
                alignmentId = alignmentId,
                convention = resolvedConvention,
                detailReason = "Direction changed from $previousSide to $observedSide",
                nowMs = nowMs,
                changedFrom = previousSide
            )
            lastPublishedState = state
            return state
        }

        // Not enough confirmation yet (e.g., temporary reverse movement or start of U-turn):
        // Publish DIRECTION_CHANGING while preserving the previously confirmed direction until proven.
        val changingState = SmartDirectionState(
            detectionState = DirectionDetectionState.DIRECTION_CHANGING,
            confirmedSide = currentConfirmed,
            pendingCandidateSide = observedSide,
            chainageTrend = if (confirmedIncreasing == true) {
                ChainageProgressionTrend.INCREASING
            } else {
                ChainageProgressionTrend.DECREASING
            },
            isIncreasingChainage = confirmedIncreasing,
            directionArrow = "⇄",
            isManualOverride = false,
            confidenceScore = 0.55f,
            routeId = routeId,
            alignmentId = alignmentId,
            effectiveConvention = resolvedConvention,
            statusLabel = DirectionDetectionState.DIRECTION_CHANGING.shortLabel,
            detailReason = "Detecting possible direction change to $observedSide",
            lastConfirmedTimestampMs = lastConfirmedTimestampMs,
            lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
            directionChangedFrom = null
        )
        lastPublishedState = changingState
        return changingState
    }

    private fun accumulatePending(candidateSide: String, stepDistM: Double, nowMs: Long) {
        if (pendingSide == candidateSide) {
            pendingConsecutiveCount += 1
            pendingCumulativeDisplacementM += stepDistM.coerceAtLeast(0.0)
        } else {
            pendingSide = candidateSide
            pendingConsecutiveCount = 1
            pendingCumulativeDisplacementM = stepDistM.coerceAtLeast(0.0)
            pendingFirstTimestampMs = nowMs
        }
    }

    private fun clearPendingCandidate() {
        pendingSide = null
        pendingConsecutiveCount = 0
        pendingCumulativeDisplacementM = 0.0
        pendingFirstTimestampMs = 0L
    }

    private fun selectMovementBaselineSample(latest: FilteredSample): FilteredSample? {
        if (recentSamples.size < 2) return null
        // Pick the earliest sample in the short window that is at least 1.5s old or >= 8m away for noise rejection
        var best: FilteredSample? = null
        for (i in 0 until recentSamples.size - 1) {
            val s = recentSamples[i]
            val ageMs = latest.timestampMs - s.timestampMs
            if (ageMs <= MAX_WINDOW_AGE_MS) {
                best = s
                break
            }
        }
        return best ?: recentSamples[recentSamples.size - 2]
    }

    private fun buildConfirmedOrHeldState(
        detectionState: DirectionDetectionState,
        side: String,
        isIncreasing: Boolean?,
        confidence: Float,
        routeId: String?,
        alignmentId: String?,
        convention: AlignmentDirectionConvention,
        detailReason: String,
        nowMs: Long,
        changedFrom: String?
    ): SmartDirectionState {
        val trend = when (isIncreasing) {
            true -> ChainageProgressionTrend.INCREASING
            false -> ChainageProgressionTrend.DECREASING
            null -> ChainageProgressionTrend.UNKNOWN
        }
        return SmartDirectionState(
            detectionState = detectionState,
            confirmedSide = side,
            pendingCandidateSide = null,
            chainageTrend = trend,
            isIncreasingChainage = isIncreasing,
            directionArrow = trend.arrow,
            isManualOverride = false,
            confidenceScore = confidence.coerceIn(0f, 1f),
            routeId = routeId,
            alignmentId = alignmentId,
            effectiveConvention = convention,
            statusLabel = if (detectionState.isConfirmed) side else detectionState.shortLabel,
            detailReason = detailReason,
            lastConfirmedTimestampMs = lastConfirmedTimestampMs.coerceAtLeast(nowMs),
            lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
            directionChangedFrom = changedFrom
        )
    }

    private fun computeObservationConfidence(
        accuracyM: Float,
        offsetM: Double,
        vectorCos: Double,
        gpsBearingCos: Double?,
        alignmentChainageIncreasesForward: Boolean,
        observedIncreasing: Boolean?,
        dedicatedCarriageway: String,
        observedSide: String?,
        isSlowSpeed: Boolean
    ): Float {
        if (observedIncreasing == null || observedSide == null) return 0f
        var score = 0.72f

        // Reward good GPS accuracy and close alignment snap
        if (accuracyM <= 10f) score += 0.12f
        else if (accuracyM > 20f) score -= 0.14f

        if (offsetM <= 15.0) score += 0.08f
        else if (offsetM > 35.0) score -= 0.12f

        // Check geometric vector alignment with the polyline tangent
        val expectedPolylineSign = if (observedIncreasing == alignmentChainageIncreasesForward) 1.0 else -1.0
        if (abs(vectorCos) > 0.01) {
            val signedAgreement = vectorCos * expectedPolylineSign
            if (signedAgreement >= 0.45) score += 0.08f
            else if (signedAgreement < -0.25) score -= 0.28f
        }

        // Secondary corroboration from GPS bearing (only when speed >= 2.5 m/s)
        // Note: inaccurate or drifting bearing penalizes mildly, never overrides solid alignment trajectory
        if (gpsBearingCos != null) {
            val signedBearingAgreement = gpsBearingCos * expectedPolylineSign
            if (signedBearingAgreement >= 0.50) score += 0.06f
            else if (signedBearingAgreement < -0.50) score -= 0.10f
        }

        // Dedicated carriageway bonus (when KMZ separates LHS and RHS polylines)
        if ((dedicatedCarriageway == "LHS" || dedicatedCarriageway == "RHS") && dedicatedCarriageway == observedSide) {
            score += 0.08f
        }

        if (isSlowSpeed) {
            score -= 0.06f
        }

        return score.coerceIn(0f, 1f)
    }

    companion object {
        const val MAX_RELIABLE_GPS_ACCURACY_M = 30f
        const val MAX_GPS_FIX_AGE_MS = 12_000L
        const val MAX_ALIGNMENT_CORRIDOR_OFFSET_M = 95.0

        const val STATIONARY_SPEED_MPS = 0.8f      // ~2.9 km/h
        const val SLOW_SPEED_MPS = 2.2f            // ~7.9 km/h
        const val MIN_SPEED_FOR_GPS_BEARING_MPS = 2.5f

        private const val MIN_DISPLACEMENT_FOR_MOVEMENT_M = 4.5
        private const val MIN_GPS_DISPLACEMENT_M = 4.5
        private const val STEP_JITTER_DEADBAND_M = 1.8
        private const val NORMAL_STEP_M = 2.5
        private const val SLOW_SPEED_STEP_M = 4.0

        private const val INITIAL_CONFIRM_COUNT_NORMAL = 2
        private const val INITIAL_CONFIRM_COUNT_SLOW = 3
        private const val INITIAL_CONFIRM_DIST_NORMAL_M = 10.0
        private const val INITIAL_CONFIRM_DIST_SLOW_M = 16.0

        private const val CHANGE_CONFIRM_COUNT_NORMAL = 3
        private const val CHANGE_CONFIRM_COUNT_SLOW = 4
        private const val CHANGE_CONFIRM_DIST_NORMAL_M = 25.0
        private const val CHANGE_CONFIRM_DIST_SLOW_M = 32.0
        private const val FAST_UTURN_CONFIRM_DIST_M = 36.0

        private const val MIN_OBSERVATION_CONFIDENCE = 0.45f
        private const val MAX_SAMPLE_WINDOW = 8
        private const val MAX_WINDOW_AGE_MS = 18_000L

        /**
         * Resolves the active [RouteAlignment] matching the current [ChainageMatchResult] or selected alignment.
         */
        fun resolveMatchedOrActiveAlignment(
            routeData: RouteData?,
            matchResult: ChainageMatchResult?
        ): RouteAlignment? {
            if (routeData == null || routeData.alignments.isEmpty()) return null
            if (routeData.selectedAlignmentId != null) {
                routeData.alignments.firstOrNull { it.id == routeData.selectedAlignmentId }?.let { return it }
            }
            val matchedName = matchResult?.matchedAlignmentName?.trim().orEmpty()
            if (matchedName.isNotEmpty()) {
                routeData.alignments.firstOrNull { it.name.equals(matchedName, ignoreCase = true) }?.let { return it }
            }
            return routeData.activeAlignment ?: routeData.alignments.firstOrNull()
        }

        /**
         * Resolves whether increasing chainage maps to LHS or RHS based on alignment metadata & user convention.
         * Never hardcodes increasing == LHS when the alignment explicitly configures RHS = INCREASING or LHS = DECREASING.
         */
        fun resolveEffectiveConvention(
            routeData: RouteData?,
            activeAlignment: RouteAlignment?,
            requested: AlignmentDirectionConvention
        ): AlignmentDirectionConvention {
            if (requested == AlignmentDirectionConvention.INCREASING_IS_LHS ||
                requested == AlignmentDirectionConvention.INCREASING_IS_RHS
            ) {
                return requested
            }

            // Inspect active alignment and sibling alignments in the route for explicit convention cues
            val candidates = buildList {
                if (activeAlignment != null) add(activeAlignment)
                routeData?.alignments?.forEach { if (it.id != activeAlignment?.id) add(it) }
            }

            for (align in candidates) {
                val cw = align.carriagewayType.trim().uppercase(Locale.ROOT)
                val dir = align.direction.trim().uppercase(Locale.ROOT)
                val lowerName = align.name.lowercase(Locale.ROOT)

                if ((cw == "RHS" && dir == "INCREASING") ||
                    (cw == "LHS" && dir == "DECREASING") ||
                    lowerName.contains("rhs_inc") ||
                    lowerName.contains("rhs inc") ||
                    lowerName.contains("lhs_dec") ||
                    lowerName.contains("lhs dec")
                ) {
                    return AlignmentDirectionConvention.INCREASING_IS_RHS
                }

                if ((cw == "LHS" && dir == "INCREASING") ||
                    (cw == "RHS" && dir == "DECREASING") ||
                    lowerName.contains("lhs_inc") ||
                    lowerName.contains("lhs inc") ||
                    lowerName.contains("rhs_dec") ||
                    lowerName.contains("rhs dec")
                ) {
                    return AlignmentDirectionConvention.INCREASING_IS_LHS
                }
            }

            // Highway standard default when alignment carries no inverted convention metadata
            return AlignmentDirectionConvention.INCREASING_IS_LHS
        }

        fun sideForProgression(
            isIncreasing: Boolean,
            convention: AlignmentDirectionConvention
        ): String {
            return when (convention) {
                AlignmentDirectionConvention.INCREASING_IS_RHS -> if (isIncreasing) "RHS" else "LHS"
                AlignmentDirectionConvention.INCREASING_IS_LHS,
                AlignmentDirectionConvention.AUTO_FROM_ALIGNMENT -> if (isIncreasing) "LHS" else "RHS"
            }
        }

        fun isIncreasingForSide(
            side: String,
            convention: AlignmentDirectionConvention
        ): Boolean {
            val upper = side.trim().uppercase(Locale.ROOT)
            return when (convention) {
                AlignmentDirectionConvention.INCREASING_IS_RHS -> upper == "RHS"
                AlignmentDirectionConvention.INCREASING_IS_LHS,
                AlignmentDirectionConvention.AUTO_FROM_ALIGNMENT -> upper == "LHS"
            }
        }

        private fun computeAlignmentTangentBearing(
            cached: ChainageMatcher.CachedAlignmentGeometry,
            segmentIndex: Int
        ): Double? {
            if (cached.points.size < 2) return null
            val idx = segmentIndex.coerceIn(0, cached.points.size - 2)
            return GeoUtils.bearingDegrees(cached.points[idx], cached.points[idx + 1])
        }

        private fun isAlignmentChainageIncreasingForward(
            cached: ChainageMatcher.CachedAlignmentGeometry,
            alignment: RouteAlignment
        ): Boolean {
            if (cached.mappedMarkers.size >= 2) {
                val first = cached.mappedMarkers.first()
                val last = cached.mappedMarkers.last()
                if (first.chainageMetres != last.chainageMetres) {
                    return last.chainageMetres > first.chainageMetres
                }
            }
            return !alignment.direction.equals("DECREASING", ignoreCase = true)
        }

        private fun angleDifferenceDegrees(a: Double, b: Double): Double {
            var diff = (a - b) % 360.0
            if (diff > 180.0) diff -= 360.0
            if (diff < -180.0) diff += 360.0
            return diff
        }
    }
}
