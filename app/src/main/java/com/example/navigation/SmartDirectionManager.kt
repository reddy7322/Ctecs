package com.example.navigation

import com.example.geo.ChainageMatcher
import com.example.geo.GeoUtils
import com.example.geo.SegmentProjection
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.AlignmentDirectionConvention
import com.example.model.ChainageMatchResult
import com.example.model.ChainageProgressionTrend
import com.example.model.DirectionAutoReactivationReason
import com.example.model.DirectionControlMode
import com.example.model.DirectionDetectionState
import com.example.model.GeoPoint
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.SmartDirectionPreferences
import com.example.model.SmartDirectionState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Single Centralized Global LHS/RHS Direction Control Engine for CTECS.
 *
 * Strict priority order:
 * 1. Valid manual selection (LHS, RHS, BOTH/BHS, MEDIAN, NONE) remains active until either
 *    10 continuous minutes pass without a photograph capture (paused while recording video)
 *    or reliable vehicle speed reaches >= 5 km/h.
 * 2. Reliable consecutive GPS speed >= 5 km/h immediately activates AUTO mode, removing any
 *    active manual override, and stays in AUTO even when speed later drops below 5 km/h.
 * 3. AUTO mode determines LHS or RHS exclusively from the signed lateral offset between the
 *    current GPS position and the actual loaded KMZ/KML road alignment geometry (including
 *    curves, bends, irregular routes, and dual-carriageway LHS/RHS alignments).
 * 4. If valid KMZ/KML alignment or reliable GPS is unavailable, retains the last reliable
 *    direction on the active route or reports an unavailable state without inventing LHS/RHS.
 */
class SmartDirectionManager {

    private data class KmzOffsetSample(
        val side: String?, // "LHS", "RHS", or null when within centerline deadband / off-corridor
        val signedOffsetMetres: Double,
        val distanceToAlignmentMetres: Double,
        val alignmentId: String?,
        val isOffCorridor: Boolean = false
    )

    // Active route & alignment context
    private var activeRouteId: String? = null
    private var activeAlignmentId: String? = null
    private var activeConvention: AlignmentDirectionConvention = AlignmentDirectionConvention.INCREASING_IS_LHS

    // AUTO KMZ/KML offset confirmation state (single centralized state, zero legacy progression cues)
    private var confirmedAutoSide: String? = null
    private var lastConfirmedTimestampMs: Long = 0L
    private var lastDirectionChangeTimestampMs: Long = 0L
    private var pendingOppositeSide: String? = null
    private var pendingOppositeCount: Int = 0
    private var lastOffsetEvaluatedFixTimeMs: Long = Long.MIN_VALUE

    // Manual override & 10-minute photo-inactivity tracking
    private var activeManualKey: String? = null
    private var manualActivityAnchorMs: Long = Long.MIN_VALUE
    private var manualExpiredToAuto: Boolean = false
    private var isVideoRecordingActive: Boolean = false

    // Reliable consecutive >= 5 km/h speed trigger tracking
    private var consecutiveHighSpeedSamples: Int = 0
    private var lastHighSpeedFixTimeMs: Long = Long.MIN_VALUE
    private var lastHighSpeedEvalMs: Long = 0L

    @Volatile
    private var latestState: SmartDirectionState = SmartDirectionState.alignmentUnavailable()

    fun currentState(): SmartDirectionState = latestState

    /**
     * Resets all automatic offset detection state (e.g. route unload or explicit reset).
     */
    @Synchronized
    fun reset() {
        confirmedAutoSide = null
        lastConfirmedTimestampMs = 0L
        lastDirectionChangeTimestampMs = 0L
        pendingOppositeSide = null
        pendingOppositeCount = 0
        lastOffsetEvaluatedFixTimeMs = Long.MIN_VALUE
        consecutiveHighSpeedSamples = 0
        lastHighSpeedFixTimeMs = Long.MIN_VALUE
        lastHighSpeedEvalMs = 0L
        latestState = SmartDirectionState.alignmentUnavailable(activeRouteId, activeAlignmentId)
    }

    /**
     * Clears stale route-bound AUTO direction immediately whenever route or alignment changes.
     * Does NOT erase an active valid manual selection.
     */
    @Synchronized
    fun onAlignmentChanged(routeId: String?, alignmentId: String?) {
        val routeChanged = routeId != activeRouteId
        val alignChanged = alignmentId != activeAlignmentId
        activeRouteId = routeId
        activeAlignmentId = alignmentId
        if (routeChanged || alignChanged) {
            confirmedAutoSide = null
            lastConfirmedTimestampMs = 0L
            pendingOppositeSide = null
            pendingOppositeCount = 0
            lastOffsetEvaluatedFixTimeMs = Long.MIN_VALUE
            if (!latestState.isManualOverride) {
                latestState = SmartDirectionState.alignmentUnavailable(routeId, alignmentId)
            }
        }
    }

    /**
     * Records a new user manual direction selection (LHS, RHS, BOTH/BHS, MEDIAN, NONE),
     * starting a fresh 10-minute photo-inactivity timer and clearing stale speed triggers.
     */
    @Synchronized
    fun onManualDirectionSelected(side: String, nowMs: Long = System.currentTimeMillis()) {
        val normalized = normalizeManualSide(side) ?: return
        manualActivityAnchorMs = nowMs
        manualExpiredToAuto = false
        activeManualKey = "$normalized@$nowMs"
        consecutiveHighSpeedSamples = 0
        lastHighSpeedFixTimeMs = Long.MIN_VALUE
        lastHighSpeedEvalMs = 0L
    }

    /**
     * Explicitly returns control to AUTO mode and clears manual override tracking.
     */
    @Synchronized
    fun onAutoModeSelected() {
        activeManualKey = null
        manualActivityAnchorMs = Long.MIN_VALUE
        manualExpiredToAuto = false
        consecutiveHighSpeedSamples = 0
        lastHighSpeedFixTimeMs = Long.MIN_VALUE
        lastHighSpeedEvalMs = 0L
    }

    /**
     * Resets the 10-minute manual-override inactivity timer ONLY when a photograph is
     * successfully captured. Preview activity, phone movement, or GPS updates never call this.
     */
    @Synchronized
    fun onPhotoCaptured(nowMs: Long = System.currentTimeMillis()) {
        if (!manualExpiredToAuto && (activeManualKey != null || latestState.isManualOverride)) {
            manualActivityAnchorMs = if (manualActivityAnchorMs == Long.MIN_VALUE) {
                nowMs
            } else {
                max(manualActivityAnchorMs, nowMs)
            }
        }
    }

    /**
     * Updates active video recording state. While actively recording video, the 10-minute
     * photo-inactivity timeout MUST NOT fire; stopping active recording resets the inactivity
     * anchor because video recording counts as an active capture operation.
     */
    @Synchronized
    fun onVideoRecordingStateChanged(isRecording: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val wasRecording = isVideoRecordingActive
        isVideoRecordingActive = isRecording
        if (wasRecording && !isRecording && !manualExpiredToAuto && (activeManualKey != null || latestState.isManualOverride)) {
            manualActivityAnchorMs = if (manualActivityAnchorMs == Long.MIN_VALUE) {
                nowMs
            } else {
                max(manualActivityAnchorMs, nowMs)
            }
        }
    }

    /**
     * Returns remaining milliseconds before an active manual override expires due to photo inactivity,
     * or null when not in manual mode or while video recording is actively suspending expiry.
     */
    @Synchronized
    fun remainingManualInactivityMs(
        preferences: SmartDirectionPreferences,
        nowMs: Long = System.currentTimeMillis()
    ): Long? {
        if (preferences.controlMode != DirectionControlMode.MANUAL_OVERRIDE || manualExpiredToAuto) {
            return null
        }
        if (isVideoRecordingActive) {
            return MANUAL_INACTIVITY_TIMEOUT_MS
        }
        val anchor = resolveManualAnchorMs(preferences, nowMs)
        val elapsed = (nowMs - anchor).coerceAtLeast(0L)
        return (MANUAL_INACTIVITY_TIMEOUT_MS - elapsed).coerceAtLeast(0L)
    }

    /**
     * Centralized evaluation entry point shared across Camera, Home, Survey, Routes,
     * Overlays, Foreground Services, and Widgets.
     */
    @Synchronized
    fun evaluate(
        locationState: LocationState?,
        matchResult: ChainageMatchResult?,
        routeData: RouteData?,
        preferences: SmartDirectionPreferences = SmartDirectionPreferences(),
        nowMs: Long = System.currentTimeMillis()
    ): SmartDirectionState {
        val activeAlignment = resolveMatchedOrActiveAlignment(routeData, matchResult)
        val routeId = routeData?.id
        val alignmentId = activeAlignment?.id
        val effectiveConvention = resolveEffectiveConvention(
            routeData = routeData,
            activeAlignment = activeAlignment,
            requested = preferences.alignmentConvention
        )
        activeConvention = effectiveConvention

        // Clear stale route-bound AUTO direction immediately when switching or unloading routes
        if (routeId != activeRouteId || alignmentId != activeAlignmentId) {
            onAlignmentChanged(routeId, alignmentId)
        }

        // Track manual preference transitions cleanly (supports both ViewModel callbacks and direct pref injection)
        val requestedManualSide = if (preferences.controlMode == DirectionControlMode.MANUAL_OVERRIDE) {
            preferences.normalizedManualSide
        } else {
            null
        }

        if (requestedManualSide == null) {
            activeManualKey = null
            manualActivityAnchorMs = Long.MIN_VALUE
            manualExpiredToAuto = false
        } else {
            val prefKey = buildManualPreferenceKey(requestedManualSide, preferences.manualSelectedAtMs)
            if (activeManualKey == null) {
                activeManualKey = prefKey
                manualActivityAnchorMs = if (preferences.manualSelectedAtMs > 0L) preferences.manualSelectedAtMs else nowMs
                manualExpiredToAuto = false
                consecutiveHighSpeedSamples = 0
                lastHighSpeedFixTimeMs = Long.MIN_VALUE
                lastHighSpeedEvalMs = 0L
            } else if (!activeManualKey!!.startsWith("$requestedManualSide@") ||
                (preferences.manualSelectedAtMs > 0L && activeManualKey != prefKey)
            ) {
                // User changed manual side or re-selected manual side with a newer timestamp
                activeManualKey = prefKey
                manualActivityAnchorMs = if (preferences.manualSelectedAtMs > 0L) preferences.manualSelectedAtMs else nowMs
                manualExpiredToAuto = false
                consecutiveHighSpeedSamples = 0
                lastHighSpeedFixTimeMs = Long.MIN_VALUE
                lastHighSpeedEvalMs = 0L
            }
        }

        val isManualCurrentlyActive = requestedManualSide != null && !manualExpiredToAuto
        var autoReactivatedReason: DirectionAutoReactivationReason? = null

        // Priority 2 check (overrides Manual immediately): Reliable consecutive GPS speed >= 5 km/h
        val speedTriggerConfirmed = updateAndCheckSpeedTrigger(locationState, nowMs)
        if (isManualCurrentlyActive && speedTriggerConfirmed) {
            manualExpiredToAuto = true
            autoReactivatedReason = DirectionAutoReactivationReason.VEHICLE_SPEED_TRIGGER
        }

        // Priority 1 timeout check: 10 continuous minutes without photo capture (suspended during active video recording)
        if (requestedManualSide != null && !manualExpiredToAuto && !isVideoRecordingActive) {
            val anchor = resolveManualAnchorMs(preferences, nowMs)
            if (nowMs - anchor >= MANUAL_INACTIVITY_TIMEOUT_MS) {
                manualExpiredToAuto = true
                autoReactivatedReason = DirectionAutoReactivationReason.PHOTO_INACTIVITY_TIMEOUT
            }
        }

        // Priority 1: Valid manual override remains active and is NEVER overwritten by GPS updates
        if (requestedManualSide != null && !manualExpiredToAuto) {
            val manualState = buildManualState(
                manualSide = requestedManualSide,
                routeId = routeId,
                alignmentId = alignmentId,
                convention = effectiveConvention,
                nowMs = nowMs
            )
            latestState = manualState
            return manualState
        }

        // Priority 3 & 4: AUTO mode — KMZ/KML offset-based LHS/RHS detection ONLY
        val autoState = evaluateKmzOffsetAutoDirection(
            locationState = locationState,
            matchResult = matchResult,
            routeData = routeData,
            activeAlignment = activeAlignment,
            convention = effectiveConvention,
            nowMs = nowMs,
            autoReactivatedReason = autoReactivatedReason
        )
        latestState = autoState
        return autoState
    }

    private fun buildManualPreferenceKey(normalizedSide: String, selectedAtMs: Long): String {
        return "$normalizedSide@$selectedAtMs"
    }

    private fun resolveManualAnchorMs(preferences: SmartDirectionPreferences, nowMs: Long): Long {
        if (manualActivityAnchorMs != Long.MIN_VALUE) return manualActivityAnchorMs
        if (preferences.manualSelectedAtMs > 0L) {
            manualActivityAnchorMs = preferences.manualSelectedAtMs
            return manualActivityAnchorMs
        }
        manualActivityAnchorMs = nowMs
        return manualActivityAnchorMs
    }

    /**
     * Tracks reliable consecutive GPS speed readings >= 5 km/h (1.3889 m/s).
     * - Rejects inaccurate/unusable/stale fixes and impossible speed spikes.
     * - Rejects duplicate evaluations of the exact same GPS fix timestamp.
     * - Requires [SPEED_CONFIRM_CONSECUTIVE_SAMPLES] consecutive reliable readings >= 5 km/h
     *   within [MAX_SPEED_SAMPLE_GAP_MS] so a single noisy GPS reading never triggers switching.
     */
    private fun updateAndCheckSpeedTrigger(locationState: LocationState?, nowMs: Long): Boolean {
        if (locationState == null || !isReliableGpsForSpeed(locationState, nowMs)) {
            return false
        }
        val rawSpeedMps = locationState.speed ?: return false
        if (rawSpeedMps.isNaN() || rawSpeedMps.isInfinite() || rawSpeedMps < 0f || rawSpeedMps > MAX_SANE_SPEED_MPS) {
            return false
        }

        val fixTime = locationState.lastFixTime
        // Do not double-count repeated evaluations of the same GPS fix
        if (fixTime > 0L && fixTime == lastHighSpeedFixTimeMs) {
            return consecutiveHighSpeedSamples >= SPEED_CONFIRM_CONSECUTIVE_SAMPLES
        }

        val speedKmh = rawSpeedMps.toDouble() * 3.6
        if (speedKmh >= SPEED_AUTO_TRIGGER_KMH - SPEED_EPSILON_KMH) {
            val gapMs = if (lastHighSpeedEvalMs > 0L) abs(nowMs - lastHighSpeedEvalMs) else 0L
            if (consecutiveHighSpeedSamples == 0 || gapMs <= MAX_SPEED_SAMPLE_GAP_MS) {
                consecutiveHighSpeedSamples += 1
            } else {
                consecutiveHighSpeedSamples = 1
            }
            lastHighSpeedFixTimeMs = fixTime
            lastHighSpeedEvalMs = nowMs
        } else {
            // Speed dropped below 5 km/h: reset pending consecutive trigger counter without ever switching
            consecutiveHighSpeedSamples = 0
            lastHighSpeedFixTimeMs = fixTime
            lastHighSpeedEvalMs = nowMs
        }

        return consecutiveHighSpeedSamples >= SPEED_CONFIRM_CONSECUTIVE_SAMPLES
    }

    /**
     * Pure KMZ/KML offset-based AUTO direction detection.
     * Uses ONLY the loaded KMZ/KML route alignment geometry and current GPS position.
     * Never uses old travel progression, fixed compass bearing, or hardcoded offsets.
     */
    private fun evaluateKmzOffsetAutoDirection(
        locationState: LocationState?,
        matchResult: ChainageMatchResult?,
        routeData: RouteData?,
        activeAlignment: RouteAlignment?,
        convention: AlignmentDirectionConvention,
        nowMs: Long,
        autoReactivatedReason: DirectionAutoReactivationReason?
    ): SmartDirectionState {
        val routeId = routeData?.id
        val alignmentId = activeAlignment?.id

        // 1. Require loaded KMZ/KML alignment geometry with at least 2 points
        val validAlignments = routeData?.alignments?.filter { it.points.size >= 2 }.orEmpty()
        if (routeData == null || validAlignments.isEmpty()) {
            confirmedAutoSide = null
            pendingOppositeSide = null
            pendingOppositeCount = 0
            return SmartDirectionState.alignmentUnavailable(routeId, alignmentId).copy(
                effectiveConvention = convention,
                autoReactivatedReason = autoReactivatedReason
            )
        }

        // 2. Validate GPS availability & accuracy
        val gpsPoint = locationState?.point
        val gpsReliable = locationState != null && gpsPoint != null && isReliableGpsForOffset(locationState, nowMs)
        if (!gpsReliable || gpsPoint == null) {
            pendingOppositeSide = null
            pendingOppositeCount = 0
            val retained = confirmedAutoSide
            if (retained != null) {
                return buildConfirmedAutoState(
                    side = retained,
                    routeId = routeId,
                    alignmentId = alignmentId,
                    convention = convention,
                    nowMs = nowMs,
                    signedOffsetMetres = latestState.signedLateralOffsetMetres,
                    confidence = 0.55f,
                    detailReason = formatAutoDetailReason(
                        base = "Retaining $retained (GPS accuracy weak)",
                        reactivation = autoReactivatedReason
                    ),
                    changedFrom = null,
                    autoReactivatedReason = autoReactivatedReason
                )
            }
            return SmartDirectionState(
                detectionState = DirectionDetectionState.GPS_SIGNAL_WEAK,
                confirmedSide = null,
                chainageTrend = ChainageProgressionTrend.UNKNOWN,
                isIncreasingChainage = null,
                directionArrow = "",
                isManualOverride = false,
                confidenceScore = 0f,
                routeId = routeId,
                alignmentId = alignmentId,
                effectiveConvention = convention,
                statusLabel = DirectionDetectionState.GPS_SIGNAL_WEAK.displayLabel,
                detailReason = formatAutoDetailReason(
                    base = "Waiting for reliable GPS fix for KMZ/KML offset",
                    reactivation = autoReactivatedReason
                ),
                autoReactivatedReason = autoReactivatedReason
            )
        }

        // 3. Compute exact signed lateral offset from the actual KMZ/KML alignment geometry
        val offsetSample = computeKmzOffsetSample(
            gpsPoint = gpsPoint,
            routeData = routeData,
            activeAlignment = activeAlignment,
            matchResult = matchResult
        )

        if (offsetSample == null || offsetSample.isOffCorridor) {
            pendingOppositeSide = null
            pendingOppositeCount = 0
            val retained = confirmedAutoSide
            if (retained != null) {
                return buildConfirmedAutoState(
                    side = retained,
                    routeId = routeId,
                    alignmentId = alignmentId,
                    convention = convention,
                    nowMs = nowMs,
                    signedOffsetMetres = offsetSample?.signedOffsetMetres,
                    confidence = 0.50f,
                    detailReason = formatAutoDetailReason(
                        base = "Retaining $retained (outside route corridor)",
                        reactivation = autoReactivatedReason
                    ),
                    changedFrom = null,
                    autoReactivatedReason = autoReactivatedReason
                )
            }
            return SmartDirectionState(
                detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
                confirmedSide = null,
                chainageTrend = ChainageProgressionTrend.UNKNOWN,
                isIncreasingChainage = null,
                directionArrow = "",
                isManualOverride = false,
                confidenceScore = 0f,
                routeId = routeId,
                alignmentId = alignmentId,
                effectiveConvention = convention,
                statusLabel = DirectionDetectionState.DIRECTION_UNCERTAIN.displayLabel,
                detailReason = formatAutoDetailReason(
                    base = "Outside KMZ/KML route corridor — direction unavailable",
                    reactivation = autoReactivatedReason
                ),
                autoReactivatedReason = autoReactivatedReason,
                signedLateralOffsetMetres = offsetSample?.signedOffsetMetres
            )
        }

        val gpsAccuracy = locationState.accuracy.coerceAtLeast(1f)
        val absOffset = abs(offsetSample.signedOffsetMetres)
        val initialDeadband = CENTERLINE_DEADBAND_M
        val switchHysteresis = max(SWITCH_HYSTERESIS_OFFSET_M, gpsAccuracy * ACCURACY_HYSTERESIS_FACTOR)

        // 4. Handle centerline deadband smoothly without guessing or noisy oscillation
        val detectedSide = offsetSample.side
        if (detectedSide == null || absOffset < initialDeadband) {
            pendingOppositeSide = null
            pendingOppositeCount = 0
            val retained = confirmedAutoSide
            if (retained != null) {
                return buildConfirmedAutoState(
                    side = retained,
                    routeId = routeId,
                    alignmentId = offsetSample.alignmentId ?: alignmentId,
                    convention = convention,
                    nowMs = nowMs,
                    signedOffsetMetres = offsetSample.signedOffsetMetres,
                    confidence = 0.72f,
                    detailReason = formatAutoDetailReason(
                        base = String.format(
                            Locale.US,
                            "AUTO %s (near centerline %.1fm)",
                            retained,
                            offsetSample.signedOffsetMetres
                        ),
                        reactivation = autoReactivatedReason
                    ),
                    changedFrom = null,
                    autoReactivatedReason = autoReactivatedReason
                )
            }
            return SmartDirectionState(
                detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
                confirmedSide = null,
                chainageTrend = ChainageProgressionTrend.UNKNOWN,
                isIncreasingChainage = null,
                directionArrow = "",
                isManualOverride = false,
                confidenceScore = 0.2f,
                routeId = routeId,
                alignmentId = offsetSample.alignmentId ?: alignmentId,
                effectiveConvention = convention,
                statusLabel = DirectionDetectionState.DIRECTION_UNCERTAIN.displayLabel,
                detailReason = formatAutoDetailReason(
                    base = String.format(
                        Locale.US,
                        "On KMZ/KML centerline (%.1fm) — waiting for clear lateral offset",
                        offsetSample.signedOffsetMetres
                    ),
                    reactivation = autoReactivatedReason
                ),
                autoReactivatedReason = autoReactivatedReason,
                signedLateralOffsetMetres = offsetSample.signedOffsetMetres
            )
        }

        // 5. Initial confirmation vs hysteresis-filtered opposite-side transition
        val currentConfirmed = confirmedAutoSide
        val fixTime = locationState.lastFixTime
        val isNewFix = fixTime <= 0L || fixTime != lastOffsetEvaluatedFixTimeMs
        lastOffsetEvaluatedFixTimeMs = fixTime

        var changedFrom: String? = null

        if (currentConfirmed == null) {
            // Cold start or fresh route: confirm immediately from clear reliable KMZ/KML lateral offset
            confirmedAutoSide = detectedSide
            lastConfirmedTimestampMs = nowMs
            pendingOppositeSide = null
            pendingOppositeCount = 0
        } else if (detectedSide == currentConfirmed) {
            // Consistent with current confirmed side
            lastConfirmedTimestampMs = nowMs
            pendingOppositeSide = null
            pendingOppositeCount = 0
        } else {
            // Opposite side detected: require clear lateral hysteresis + consecutive reliable fixes
            // so a single noisy GPS reading NEVER triggers a sudden direction flip.
            if (absOffset >= switchHysteresis) {
                if (pendingOppositeSide == detectedSide) {
                    if (isNewFix) {
                        pendingOppositeCount += 1
                    }
                } else {
                    pendingOppositeSide = detectedSide
                    pendingOppositeCount = 1
                }

                if (pendingOppositeCount >= SWITCH_CONFIRM_SAMPLES) {
                    changedFrom = currentConfirmed
                    confirmedAutoSide = detectedSide
                    lastConfirmedTimestampMs = nowMs
                    lastDirectionChangeTimestampMs = nowMs
                    pendingOppositeSide = null
                    pendingOppositeCount = 0
                }
            } else {
                // Opposite offset still within hysteresis margin — hold current confirmed side steadily
                pendingOppositeSide = null
                pendingOppositeCount = 0
            }
        }

        val finalSide = confirmedAutoSide ?: detectedSide
        val confidence = ((absOffset / (absOffset + gpsAccuracy * 0.5)).toFloat()).coerceIn(0.60f, 0.99f)
        val detailBase = if (pendingOppositeSide != null && pendingOppositeSide != finalSide) {
            String.format(
                Locale.US,
                "AUTO %s (KMZ offset %+.1fm, verifying %s)",
                finalSide,
                offsetSample.signedOffsetMetres,
                pendingOppositeSide
            )
        } else {
            String.format(
                Locale.US,
                "AUTO %s from KMZ/KML offset (%+.1fm)",
                finalSide,
                offsetSample.signedOffsetMetres
            )
        }

        return buildConfirmedAutoState(
            side = finalSide,
            routeId = routeId,
            alignmentId = offsetSample.alignmentId ?: alignmentId,
            convention = convention,
            nowMs = nowMs,
            signedOffsetMetres = offsetSample.signedOffsetMetres,
            confidence = confidence,
            detailReason = formatAutoDetailReason(detailBase, autoReactivatedReason),
            changedFrom = changedFrom,
            autoReactivatedReason = autoReactivatedReason,
            pendingSide = pendingOppositeSide
        )
    }

    /**
     * Computes signed lateral offset of [gpsPoint] relative to the actual loaded KMZ/KML route geometry.
     *
     * Supports:
     * - Dual-carriageway KMZ/KML routes with explicit separate LHS and RHS alignment polylines.
     * - Single-centerline or multi-alignment KMZ/KML routes with straight segments, sweeping curves,
     *   sharp 90°/180° bends (vertex bisector tangent), and irregular polyline geometry.
     */
    private fun computeKmzOffsetSample(
        gpsPoint: GeoPoint,
        routeData: RouteData,
        activeAlignment: RouteAlignment?,
        matchResult: ChainageMatchResult?
    ): KmzOffsetSample? {
        val validAlignments = routeData.alignments.filter { it.points.size >= 2 }
        if (validAlignments.isEmpty()) return null

        // Case A: Explicit Dual-Carriageway KMZ/KML (both LHS and RHS polylines present & no single alignment forced)
        if (routeData.selectedAlignmentId == null) {
            val lhsAlignments = validAlignments.filter { isExplicitCarriageway(it, "LHS") }
            val rhsAlignments = validAlignments.filter { isExplicitCarriageway(it, "RHS") }
            if (lhsAlignments.isNotEmpty() && rhsAlignments.isNotEmpty()) {
                val nearestLhs = findNearestAlignmentProjection(gpsPoint, lhsAlignments, routeData)
                val nearestRhs = findNearestAlignmentProjection(gpsPoint, rhsAlignments, routeData)
                if (nearestLhs != null && nearestRhs != null) {
                    val distLhs = nearestLhs.second.perpendicularDistance
                    val distRhs = nearestRhs.second.perpendicularDistance
                    val minDist = min(distLhs, distRhs)
                    val margin = distRhs - distLhs // > 0 => closer to LHS, < 0 => closer to RHS
                    if (minDist > MAX_ALIGNMENT_CORRIDOR_OFFSET_M) {
                        return KmzOffsetSample(
                            side = null,
                            signedOffsetMetres = margin,
                            distanceToAlignmentMetres = minDist,
                            alignmentId = if (distLhs <= distRhs) nearestLhs.first.alignmentId else nearestRhs.first.alignmentId,
                            isOffCorridor = true
                        )
                    }
                    val side = when {
                        abs(margin) < CENTERLINE_DEADBAND_M -> null
                        margin > 0.0 -> "LHS"
                        else -> "RHS"
                    }
                    return KmzOffsetSample(
                        side = side,
                        signedOffsetMetres = margin,
                        distanceToAlignmentMetres = minDist,
                        alignmentId = if (distLhs <= distRhs) nearestLhs.first.alignmentId else nearestRhs.first.alignmentId,
                        isOffCorridor = false
                    )
                }
            }
        }

        // Case B: Single-centerline or matched alignment polyline — exact signed perpendicular lateral offset
        val candidateAlignments = when {
            routeData.selectedAlignmentId != null -> {
                val selected = validAlignments.filter { it.id == routeData.selectedAlignmentId }
                if (selected.isNotEmpty()) selected else validAlignments
            }
            activeAlignment != null && activeAlignment.points.size >= 2 && validAlignments.size == 1 -> {
                listOf(activeAlignment)
            }
            else -> validAlignments
        }

        val nearest = findNearestAlignmentProjection(gpsPoint, candidateAlignments, routeData) ?: return null
        val (cached, proj, segIdx) = nearest

        if (proj.perpendicularDistance > MAX_ALIGNMENT_CORRIDOR_OFFSET_M) {
            return KmzOffsetSample(
                side = null,
                signedOffsetMetres = 0.0,
                distanceToAlignmentMetres = proj.perpendicularDistance,
                alignmentId = cached.alignmentId,
                isOffCorridor = true
            )
        }

        val rawSignedOffset = computeCurveAwareSignedLateralOffset(
            gpsPoint = gpsPoint,
            cached = cached,
            segIdx = segIdx,
            proj = proj
        )

        val isDecreasingGeometry = cached.direction.equals("DECREASING", ignoreCase = true)
        val orientedOffset = if (isDecreasingGeometry) -rawSignedOffset else rawSignedOffset
        val side = when {
            abs(orientedOffset) < CENTERLINE_DEADBAND_M -> null
            orientedOffset > 0.0 -> "LHS"
            else -> "RHS"
        }

        return KmzOffsetSample(
            side = side,
            signedOffsetMetres = orientedOffset,
            distanceToAlignmentMetres = proj.perpendicularDistance,
            alignmentId = cached.alignmentId,
            isOffCorridor = false
        )
    }

    /**
     * Projects [gpsPoint] onto the closest segment across [alignments] using cached alignment geometry.
     * Guarantees exact nearest-segment selection even on tight curves, hairpins, and irregular routes.
     */
    private fun findNearestAlignmentProjection(
        gpsPoint: GeoPoint,
        alignments: List<RouteAlignment>,
        routeData: RouteData
    ): Triple<ChainageMatcher.CachedAlignmentGeometry, SegmentProjection, Int>? {
        var bestCached: ChainageMatcher.CachedAlignmentGeometry? = null
        var bestProj: SegmentProjection? = null
        var bestSegIdx = -1
        var minDist = Double.MAX_VALUE

        for (align in alignments) {
            if (align.points.size < 2) continue
            val cached = ChainageMatcher.getOrCreateCachedGeometry(align, routeData.markers)
            val pts = cached.points
            val useFastCull = cached.segmentCount > 500
            for (i in 0 until cached.segmentCount) {
                val p1 = pts[i]
                val p2 = pts[i + 1]
                if (useFastCull) {
                    val minLat = min(p1.latitude, p2.latitude) - 0.004
                    val maxLat = max(p1.latitude, p2.latitude) + 0.004
                    val minLng = min(p1.longitude, p2.longitude) - 0.004
                    val maxLng = max(p1.longitude, p2.longitude) + 0.004
                    if (gpsPoint.latitude !in minLat..maxLat || gpsPoint.longitude !in minLng..maxLng) {
                        continue
                    }
                }
                val proj = GeoUtils.projectPointToSegment(gpsPoint, p1, p2)
                if (proj.perpendicularDistance < minDist) {
                    minDist = proj.perpendicularDistance
                    bestCached = cached
                    bestProj = proj
                    bestSegIdx = i
                }
            }
        }

        val chosenCached = bestCached ?: return null
        val chosenProj = bestProj ?: return null
        return Triple(chosenCached, chosenProj, bestSegIdx)
    }

    /**
     * Computes signed lateral offset in metres relative to the local polyline tangent.
     * - Positive => Left of segment direction (`p1 -> p2`)
     * - Negative => Right of segment direction (`p1 -> p2`)
     * At shared bend vertices (`t == 0` or `t == 1`), uses the unit bisector tangent of the
     * incoming and outgoing segments so sharp 90°/S-bend outer & inner corners never invert sign.
     */
    private fun computeCurveAwareSignedLateralOffset(
        gpsPoint: GeoPoint,
        cached: ChainageMatcher.CachedAlignmentGeometry,
        segIdx: Int,
        proj: SegmentProjection
    ): Double {
        val pts = cached.points
        if (segIdx !in 0 until cached.segmentCount) return 0.0
        val p1 = pts[segIdx]
        val p2 = pts[segIdx + 1]

        // Interior of segment: direct exact perpendicular signed offset
        if (proj.t > VERTEX_EPSILON && proj.t < 1.0 - VERTEX_EPSILON) {
            return ChainageMatcher.calculateSignedLateralOffsetMetres(gpsPoint, p1, p2)
        }

        // At a shared vertex between two adjacent segments: use local bisector tangent
        val vertexIdx = if (proj.t <= VERTEX_EPSILON) segIdx else segIdx + 1
        if (vertexIdx in 1 until pts.lastIndex) {
            val prev = pts[vertexIdx - 1]
            val vertex = pts[vertexIdx]
            val next = pts[vertexIdx + 1]

            val midLatRad = Math.toRadians((prev.latitude + vertex.latitude + next.latitude + gpsPoint.latitude) / 4.0)
            val cosLat = cos(midLatRad)

            val ax = Math.toRadians(vertex.longitude - prev.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosLat
            val ay = Math.toRadians(vertex.latitude - prev.latitude) * GeoUtils.EARTH_RADIUS_METRES
            val aLen = sqrt(ax * ax + ay * ay)

            val bx = Math.toRadians(next.longitude - vertex.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosLat
            val by = Math.toRadians(next.latitude - vertex.latitude) * GeoUtils.EARTH_RADIUS_METRES
            val bLen = sqrt(bx * bx + by * by)

            if (aLen > 1e-4 && bLen > 1e-4) {
                val tx = (ax / aLen) + (bx / bLen)
                val ty = (ay / aLen) + (by / bLen)
                val tLen = sqrt(tx * tx + ty * ty)
                if (tLen > 1e-3) {
                    val px = Math.toRadians(gpsPoint.longitude - vertex.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosLat
                    val py = Math.toRadians(gpsPoint.latitude - vertex.latitude) * GeoUtils.EARTH_RADIUS_METRES
                    val cross = (tx / tLen) * py - (ty / tLen) * px
                    val sign = if (cross >= 0.0) 1.0 else -1.0
                    return sign * proj.perpendicularDistance
                }
            }
        }

        return ChainageMatcher.calculateSignedLateralOffsetMetres(gpsPoint, p1, p2)
    }

    private fun isExplicitCarriageway(alignment: RouteAlignment, side: String): Boolean {
        val cw = alignment.carriagewayType.trim().uppercase(Locale.ROOT)
        if (cw == side) return true
        val lower = alignment.name.lowercase(Locale.ROOT)
        return when (side) {
            "LHS" -> lower.contains("lhs") || lower.contains("left carriageway") || lower.contains("left side")
            "RHS" -> lower.contains("rhs") || lower.contains("right carriageway") || lower.contains("right side")
            else -> false
        }
    }

    private fun isReliableGpsForOffset(locationState: LocationState, nowMs: Long): Boolean {
        if (!locationState.isUsable || locationState.point == null) return false
        if (locationState.status == LocationStatus.POOR_ACCURACY ||
            locationState.status == LocationStatus.DISABLED ||
            locationState.status == LocationStatus.PERMISSION_DENIED
        ) {
            return false
        }
        val acc = locationState.accuracy
        if (acc.isNaN() || acc <= 0f || acc > MAX_RELIABLE_GPS_ACCURACY_M) return false
        if (locationState.lastFixTime > 0L && nowMs > 0L) {
            val age = nowMs - locationState.lastFixTime
            if (age > MAX_GPS_FIX_AGE_MS) return false
        }
        return true
    }

    private fun isReliableGpsForSpeed(locationState: LocationState, nowMs: Long): Boolean {
        return isReliableGpsForOffset(locationState, nowMs) && locationState.speed != null
    }

    private fun buildManualState(
        manualSide: String,
        routeId: String?,
        alignmentId: String?,
        convention: AlignmentDirectionConvention,
        nowMs: Long
    ): SmartDirectionState {
        val isBinary = manualSide == "LHS" || manualSide == "RHS"
        val isInc = if (isBinary) isIncreasingForSide(manualSide, convention) else null
        val detState = when (manualSide) {
            "LHS" -> DirectionDetectionState.LHS_CONFIRMED
            "RHS" -> DirectionDetectionState.RHS_CONFIRMED
            else -> DirectionDetectionState.DIRECTION_UNCERTAIN
        }
        val trend = when (isInc) {
            true -> ChainageProgressionTrend.INCREASING
            false -> ChainageProgressionTrend.DECREASING
            null -> ChainageProgressionTrend.UNKNOWN
        }
        val arrow = when (isInc) {
            true -> "↑"
            false -> "↓"
            null -> ""
        }
        val detail = if (isVideoRecordingActive) {
            "Manual override: $manualSide (timeout paused during recording)"
        } else {
            "Manual override: $manualSide (auto resumes after 10 min inactivity or >= 5 km/h)"
        }
        return SmartDirectionState(
            detectionState = detState,
            confirmedSide = manualSide,
            pendingCandidateSide = null,
            chainageTrend = trend,
            isIncreasingChainage = isInc,
            directionArrow = arrow,
            isManualOverride = true,
            confidenceScore = 1f,
            routeId = routeId,
            alignmentId = alignmentId,
            effectiveConvention = convention,
            statusLabel = "Manual $manualSide",
            detailReason = detail,
            lastConfirmedTimestampMs = nowMs,
            lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
            directionChangedFrom = null,
            autoReactivatedReason = null
        )
    }

    private fun buildConfirmedAutoState(
        side: String,
        routeId: String?,
        alignmentId: String?,
        convention: AlignmentDirectionConvention,
        nowMs: Long,
        signedOffsetMetres: Double?,
        confidence: Float,
        detailReason: String,
        changedFrom: String?,
        autoReactivatedReason: DirectionAutoReactivationReason?,
        pendingSide: String? = null
    ): SmartDirectionState {
        val isInc = isIncreasingForSide(side, convention)
        val detState = if (side == "LHS") {
            DirectionDetectionState.LHS_CONFIRMED
        } else {
            DirectionDetectionState.RHS_CONFIRMED
        }
        val trend = if (isInc) {
            ChainageProgressionTrend.INCREASING
        } else {
            ChainageProgressionTrend.DECREASING
        }
        val arrow = if (isInc) "↑" else "↓"
        return SmartDirectionState(
            detectionState = detState,
            confirmedSide = side,
            pendingCandidateSide = pendingSide,
            chainageTrend = trend,
            isIncreasingChainage = isInc,
            directionArrow = arrow,
            isManualOverride = false,
            confidenceScore = confidence,
            routeId = routeId,
            alignmentId = alignmentId,
            effectiveConvention = convention,
            statusLabel = detState.displayLabel,
            detailReason = detailReason,
            lastConfirmedTimestampMs = lastConfirmedTimestampMs.takeIf { it > 0L } ?: nowMs,
            lastDirectionChangeTimestampMs = lastDirectionChangeTimestampMs,
            directionChangedFrom = changedFrom,
            autoReactivatedReason = autoReactivatedReason,
            signedLateralOffsetMetres = signedOffsetMetres
        )
    }

    private fun formatAutoDetailReason(
        base: String,
        reactivation: DirectionAutoReactivationReason?
    ): String {
        return if (reactivation != null) {
            "$base (${reactivation.label})"
        } else {
            base
        }
    }

    companion object {
        // 10 continuous minutes of photo inactivity before manual override expires to AUTO
        const val MANUAL_INACTIVITY_TIMEOUT_MS = 10L * 60L * 1000L

        // Vehicle speed threshold: >= 5 km/h confirmed across consecutive reliable GPS readings
        const val SPEED_AUTO_TRIGGER_KMH = 5.0
        const val SPEED_AUTO_TRIGGER_MPS = 5.0f / 3.6f
        const val SPEED_CONFIRM_CONSECUTIVE_SAMPLES = 2
        private const val SPEED_EPSILON_KMH = 0.001
        private const val MAX_SANE_SPEED_MPS = 70.0f // ~252 km/h sanity upper bound
        private const val MAX_SPEED_SAMPLE_GAP_MS = 10_000L

        // GPS accuracy, freshness & KMZ/KML offset filtering
        const val MAX_RELIABLE_GPS_ACCURACY_M = 25.0f
        const val MAX_GPS_FIX_AGE_MS = 20_000L
        const val MAX_ALIGNMENT_CORRIDOR_OFFSET_M = 120.0
        const val CENTERLINE_DEADBAND_M = 1.5
        const val SWITCH_HYSTERESIS_OFFSET_M = 2.0
        const val SWITCH_CONFIRM_SAMPLES = 2
        private const val ACCURACY_HYSTERESIS_FACTOR = 0.25
        private const val VERTEX_EPSILON = 1e-4

        fun normalizeManualSide(raw: String?): String? {
            return when (raw?.trim()?.uppercase(Locale.ROOT)) {
                "LHS" -> "LHS"
                "RHS" -> "RHS"
                "BHS", "BOTH" -> "BHS"
                "MEDIAN" -> "MEDIAN"
                "NONE" -> "NONE"
                else -> null
            }
        }

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
    }
}
