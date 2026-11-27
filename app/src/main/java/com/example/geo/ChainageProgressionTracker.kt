package com.example.geo

import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.ManualChainageCalibrationState
import com.example.model.ManualChainageMode
import com.example.model.MatchConfidence
import com.example.model.RouteData
import com.example.model.TravelChainageDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Resolution output from the centralized direction-aware manual chainage engine.
 */
data class ManualCalibrationResolution(
    val matchResult: ChainageMatchResult,
    val updatedCalibration: ManualChainageCalibrationState,
    val calibrationChangedSignificantly: Boolean
)

/**
 * Intelligent Chainage Progression, Direction Detection & Manual Calibration Engine.
 *
 * Single centralized source of truth for:
 * - Automatic KMZ / Custom Correction chainage smoothing with GPS quality control.
 * - Multi-sample forward/reverse travel direction detection (strictly separate from LHS/RHS).
 * - Mode 1 ([ManualChainageMode.CONTINUE_STABLE]): direction-aware continuous progression from
 *   manual reference without automatic GPS-to-KMZ recalibration or jump resets.
 * - Mode 2 ([ManualChainageMode.DYNAMIC_CORRECTION]): immutable GPS-anchored calibration with
 *   exact non-cumulative signed offset along road geometry & carriageways.
 * - Seamless mode switching preserving current displayed chainage with zero jumps.
 */
class ChainageProgressionTracker {

    private var currentRouteId: String? = null
    private var lastValidChainage: Chainage? = null
    private var lastValidRawChainage: Chainage? = null
    private var lastCalibrationOffset: Long? = null
    private var lastChainageSource: ChainageSource? = null
    private var lastProjectedPoint: GeoPoint? = null
    private var smoothedMetres: Double? = null

    // Side stabilization state (LHS / RHS / BHS — completely separate from forward/reverse travel)
    private var currentDetectedSide: String = "RHS"
    private var hasInitialSide: Boolean = false
    private var candidateSide: String? = null
    private var candidateSideCount: Int = 0
    private var manualSideOverride: String? = null

    // Multi-sample travel direction state (Forward / Reverse along road alignment)
    private var lastChainageDelta: Long = 0L
    private var confirmedTravelDirection: TravelChainageDirection = TravelChainageDirection.UNKNOWN
    private var lastMovingDirectionSign: Int = 1
    private var pendingDirectionSign: Int = 0
    private var pendingDirectionSamples: Int = 0
    private var pendingDirectionAccumMetres: Double = 0.0

    // Manual Stable & Dynamic progression state
    private var lastManualSessionKey: String? = null
    private var stableContinuousMetres: Double? = null
    private var lastReliableManualMetres: Long? = null
    private var lastAcceptedGpsPoint: GeoPoint? = null
    private var lastAcceptedGpsTimeMs: Long = 0L
    private var lastAcceptedAlignedRawMetres: Long? = null
    private var lastAcceptedDistAlongMetres: Double? = null
    private var lastAcceptedAlignmentName: String? = null
    private var offRouteRefUnitEast: Double? = null
    private var offRouteRefUnitNorth: Double? = null
    private var consecutiveSpikeCount: Int = 0
    private var lastSpikeCandidatePoint: GeoPoint? = null

    companion object {
        private const val SIDE_CONFIRMATION_COUNT = 2
        private const val STATIONARY_JITTER_METRES = 1.5
        private const val MANUAL_STATIONARY_DEADBAND_METRES = 1.8
        private const val EMA_ALPHA_MODERATE = 0.72
        private const val EMA_ALPHA_HIGH_ACCURACY = 0.85
        private const val SNAP_THRESHOLD_METRES = 25L

        // GPS quality & spike filtering thresholds
        const val MAX_TRACKING_GPS_ACCURACY_M = 35.0f
        const val MAX_ANCHOR_GPS_ACCURACY_M = 25.0f
        const val MAX_GPS_FIX_AGE_MS = 20_000L
        const val MAX_PLAUSIBLE_SPEED_MPS = 55.0 // ~198 km/h upper highway sanity bound
        const val MAX_SHORT_DROPOUT_BRIDGE_MS = 15_000L
        private const val DIRECTION_FLIP_MIN_SAMPLES = 2
        private const val DIRECTION_FLIP_MIN_ACCUM_M = 3.5
        private const val DIRECTION_FLIP_INSTANT_M = 8.0
    }

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
        resetDirectionAndManualTracking()
    }

    @Synchronized
    fun resetChainageFilter() {
        lastValidChainage = null
        lastValidRawChainage = null
        lastCalibrationOffset = null
        lastChainageSource = null
        lastProjectedPoint = null
        smoothedMetres = null
        lastChainageDelta = 0L
        resetDirectionAndManualTracking()
    }

    @Synchronized
    fun resetManualProgressionSession() {
        lastManualSessionKey = null
        stableContinuousMetres = null
        lastReliableManualMetres = null
        lastAcceptedGpsPoint = null
        lastAcceptedGpsTimeMs = 0L
        lastAcceptedAlignedRawMetres = null
        lastAcceptedDistAlongMetres = null
        lastAcceptedAlignmentName = null
        offRouteRefUnitEast = null
        offRouteRefUnitNorth = null
        consecutiveSpikeCount = 0
        lastSpikeCandidatePoint = null
    }

    private fun resetDirectionAndManualTracking() {
        confirmedTravelDirection = TravelChainageDirection.UNKNOWN
        lastMovingDirectionSign = 1
        pendingDirectionSign = 0
        pendingDirectionSamples = 0
        pendingDirectionAccumMetres = 0.0
        resetManualProgressionSession()
    }

    @Synchronized
    fun setManualSide(side: String) {
        manualSideOverride = side
        currentDetectedSide = side
    }

    @Synchronized
    fun clearManualSide() {
        manualSideOverride = null
    }

    @Synchronized
    fun isManualOverride(): Boolean = manualSideOverride != null

    @Synchronized
    fun getCurrentSide(): String = manualSideOverride ?: currentDetectedSide

    @Synchronized
    fun getCurrentTravelDirection(): TravelChainageDirection = confirmedTravelDirection

    /**
     * Establishes a fresh manual calibration state from an explicit user chainage entry
     * (Apply / Calibrate / Recalibrate).
     */
    @Synchronized
    fun establishManualCalibration(
        enteredChainage: Chainage,
        mode: ManualChainageMode,
        currentAutoResult: ChainageMatchResult?,
        locationState: LocationState?,
        routeId: String?,
        nowMs: Long = System.currentTimeMillis()
    ): ManualChainageCalibrationState {
        val formatted = enteredChainage.format()
        val effectiveSide = resolveSide(currentAutoResult)
        val hasReliableAnchorGps = mode == ManualChainageMode.DYNAMIC_CORRECTION &&
            isReliableAnchorFix(locationState, currentAutoResult, nowMs)

        val rawAlignedM = if (hasReliableAnchorGps) {
            (currentAutoResult?.rawUncorrectedChainage ?: currentAutoResult?.chainage)?.metres
        } else null
        val computedOffset = if (rawAlignedM != null) enteredChainage.metres - rawAlignedM else null

        resetManualProgressionSession()
        stableContinuousMetres = enteredChainage.metres.toDouble()
        lastReliableManualMetres = enteredChainage.metres
        if (locationState?.point != null && isValidPoint(locationState.point) &&
            locationState.accuracy <= MAX_TRACKING_GPS_ACCURACY_M
        ) {
            lastAcceptedGpsPoint = locationState.point
            lastAcceptedGpsTimeMs = if (locationState.lastFixTime > 0L) locationState.lastFixTime else nowMs
        }
        if (currentAutoResult != null && isReliableAlignmentMatch(currentAutoResult)) {
            lastAcceptedAlignedRawMetres = (currentAutoResult.rawUncorrectedChainage ?: currentAutoResult.chainage)?.metres
            lastAcceptedDistAlongMetres = currentAutoResult.distanceAlongAlignmentMetres
            lastAcceptedAlignmentName = currentAutoResult.matchedAlignmentName
        }

        val state = ManualChainageCalibrationState(
            isActive = true,
            mode = mode,
            routeId = routeId,
            anchorChainageMetres = enteredChainage.metres,
            anchorFormattedText = formatted,
            currentChainageMetres = enteredChainage.metres,
            anchorRawAlignmentChainageMetres = rawAlignedM,
            anchorDistanceAlongMetres = if (hasReliableAnchorGps) currentAutoResult?.distanceAlongAlignmentMetres else null,
            anchorAlignmentName = if (hasReliableAnchorGps) currentAutoResult?.matchedAlignmentName else null,
            anchorCarriagewaySide = effectiveSide,
            anchorLatitude = locationState?.point?.latitude,
            anchorLongitude = locationState?.point?.longitude,
            anchorTimestampMs = nowMs,
            isDynamicAnchorCalibrated = hasReliableAnchorGps,
            travelDirection = confirmedTravelDirection,
            computedOffsetMetres = computedOffset,
            lastUpdatedTimestampMs = nowMs
        )
        lastManualSessionKey = buildSessionKey(state)
        return state
    }

    /**
     * Switches cleanly between [ManualChainageMode.CONTINUE_STABLE] and
     * [ManualChainageMode.DYNAMIC_CORRECTION] while strictly preserving the current
     * displayed chainage without unexpected jumps.
     */
    @Synchronized
    fun switchManualMode(
        currentCalibration: ManualChainageCalibrationState,
        targetMode: ManualChainageMode,
        currentAutoResult: ChainageMatchResult?,
        locationState: LocationState?,
        routeId: String?,
        nowMs: Long = System.currentTimeMillis()
    ): ManualChainageCalibrationState {
        if (!currentCalibration.isActive) {
            return currentCalibration.copy(mode = targetMode, lastUpdatedTimestampMs = nowMs)
        }
        if (currentCalibration.mode == targetMode) {
            return currentCalibration
        }

        val preservedMetres = currentCalibration.effectiveCurrentMetres
            ?: lastReliableManualMetres
            ?: currentAutoResult?.chainage?.metres
            ?: 0L
        val preservedChainage = Chainage(preservedMetres)

        return if (targetMode == ManualChainageMode.DYNAMIC_CORRECTION) {
            // Entering Dynamic mode: anchor at current displayed chainage + current reliable GPS
            establishManualCalibration(
                enteredChainage = preservedChainage,
                mode = ManualChainageMode.DYNAMIC_CORRECTION,
                currentAutoResult = currentAutoResult,
                locationState = locationState,
                routeId = routeId ?: currentCalibration.routeId,
                nowMs = nowMs
            )
        } else {
            // Returning to Stable mode: continue seamlessly from current displayed chainage
            stableContinuousMetres = preservedMetres.toDouble()
            lastReliableManualMetres = preservedMetres
            if (locationState?.point != null && isValidPoint(locationState.point) &&
                locationState.accuracy <= MAX_TRACKING_GPS_ACCURACY_M
            ) {
                lastAcceptedGpsPoint = locationState.point
                lastAcceptedGpsTimeMs = if (locationState.lastFixTime > 0L) locationState.lastFixTime else nowMs
            }
            if (currentAutoResult != null && isReliableAlignmentMatch(currentAutoResult)) {
                lastAcceptedAlignedRawMetres = (currentAutoResult.rawUncorrectedChainage ?: currentAutoResult.chainage)?.metres
                lastAcceptedDistAlongMetres = currentAutoResult.distanceAlongAlignmentMetres
                lastAcceptedAlignmentName = currentAutoResult.matchedAlignmentName
            }
            val switched = currentCalibration.copy(
                mode = ManualChainageMode.CONTINUE_STABLE,
                anchorChainageMetres = preservedMetres,
                anchorFormattedText = preservedChainage.format(),
                currentChainageMetres = preservedMetres,
                isDynamicAnchorCalibrated = false,
                computedOffsetMetres = null,
                anchorTimestampMs = nowMs,
                lastUpdatedTimestampMs = nowMs
            )
            lastManualSessionKey = buildSessionKey(switched)
            switched
        }
    }

    /**
     * Processes automatic (KMZ / Custom Correction) match results with GPS accuracy protection,
     * adaptive smoothing, stationary jitter filtering, travel direction tracking, and side hysteresis.
     */
    @Synchronized
    fun processResult(rawResult: ChainageMatchResult, routeId: String?): ChainageMatchResult {
        if (routeId != currentRouteId) {
            currentRouteId = routeId
            reset()
        }

        // Preserve backwards compatibility if caller passes an explicit manual result directly
        if (rawResult.isManual) {
            val side = manualSideOverride ?: rawResult.detectedSide.ifBlank { currentDetectedSide }
            lastChainageSource = rawResult.source
            return rawResult.copy(
                detectedSide = side,
                travelDirection = if (rawResult.travelDirection != TravelChainageDirection.UNKNOWN) {
                    rawResult.travelDirection
                } else {
                    confirmedTravelDirection
                }
            )
        }

        val effectiveSide = if (manualSideOverride != null) {
            manualSideOverride!!
        } else {
            stabilizeSide(rawResult.detectedSide, rawResult.confidence)
        }

        if (rawResult.confidence == MatchConfidence.NO_MATCH || rawResult.chainage == null) {
            return rawResult.copy(
                detectedSide = effectiveSide,
                lastReliableChainage = lastValidChainage,
                travelDirection = confirmedTravelDirection
            )
        }

        val rawChainage = rawResult.chainage
        val rawUncorrected = rawResult.rawUncorrectedChainage ?: rawChainage
        val projPt = rawResult.projectedPoint

        // Poor GPS accuracy gate: retain last reliable chainage cleanly instead of blending noisy data
        if (rawResult.gpsAccuracy > MAX_TRACKING_GPS_ACCURACY_M && lastValidChainage != null) {
            return rawResult.copy(
                chainage = lastValidChainage,
                confidence = MatchConfidence.LOW,
                isUncertain = true,
                detectedSide = effectiveSide,
                lastReliableChainage = lastValidChainage,
                travelDirection = confirmedTravelDirection
            )
        }

        val prevOffset = lastCalibrationOffset
        val currOffset = rawChainage.metres - rawUncorrected.metres
        val calibrationChanged = (lastChainageSource != null && lastChainageSource != rawResult.source) ||
            (prevOffset != null && abs(currOffset - prevOffset) >= 2L)

        if (calibrationChanged) {
            lastValidChainage = rawChainage
            lastValidRawChainage = rawUncorrected
            lastCalibrationOffset = currOffset
            lastChainageSource = rawResult.source
            lastProjectedPoint = projPt
            smoothedMetres = rawChainage.metres.toDouble()
            return rawResult.copy(
                chainage = rawChainage,
                detectedSide = effectiveSide,
                lastReliableChainage = rawChainage,
                travelDirection = confirmedTravelDirection
            )
        }

        val smoothedChainage: Chainage
        val lastCh = lastValidChainage
        val lastProj = lastProjectedPoint

        if (rawResult.isUncertain && lastCh != null) {
            smoothedChainage = lastCh
        } else if (lastProj != null && lastCh != null && projPt != null) {
            val movedDist = GeoUtils.distanceMetres(projPt, lastProj)
            val deltaMetres = rawChainage.metres - lastCh.metres

            if (movedDist < STATIONARY_JITTER_METRES && abs(deltaMetres) <= 2L) {
                smoothedChainage = lastCh
                if (confirmedTravelDirection != TravelChainageDirection.UNKNOWN) {
                    confirmedTravelDirection = TravelChainageDirection.STATIONARY
                }
            } else if (abs(deltaMetres) >= SNAP_THRESHOLD_METRES) {
                smoothedMetres = rawChainage.metres.toDouble()
                smoothedChainage = rawChainage
                lastChainageDelta = deltaMetres
                lastProjectedPoint = projPt
                updateTravelDirection(deltaMetres.toDouble(), rawResult.gpsAccuracy)
            } else {
                val alpha = if (rawResult.gpsAccuracy in 0.1f..12.0f) EMA_ALPHA_HIGH_ACCURACY else EMA_ALPHA_MODERATE
                val prevSmooth = smoothedMetres ?: lastCh.metres.toDouble()
                val nextSmooth = alpha * rawChainage.metres.toDouble() + (1.0 - alpha) * prevSmooth
                smoothedMetres = nextSmooth
                smoothedChainage = Chainage(nextSmooth.roundToLong().coerceAtLeast(0L))
                val appliedDelta = smoothedChainage.metres - lastCh.metres
                lastChainageDelta = appliedDelta
                lastProjectedPoint = projPt
                if (abs(deltaMetres) >= 2L) {
                    updateTravelDirection(deltaMetres.toDouble(), rawResult.gpsAccuracy)
                }
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
            lastReliableChainage = lastValidChainage,
            travelDirection = confirmedTravelDirection
        )
    }

    /**
     * Centralized direction-aware resolution for active Manual Chainage
     * ([ManualChainageMode.CONTINUE_STABLE] or [ManualChainageMode.DYNAMIC_CORRECTION]).
     *
     * Reuses the already-computed [autoResult] from [ChainageMatcher] + [SmartDirectionManager]
     * without duplicating matching logic.
     */
    @Synchronized
    fun processWithManualCalibration(
        autoResult: ChainageMatchResult,
        locationState: LocationState?,
        routeData: RouteData?,
        calibration: ManualChainageCalibrationState,
        nowMs: Long = System.currentTimeMillis()
    ): ManualCalibrationResolution {
        if (!calibration.isActive || calibration.effectiveCurrentMetres == null) {
            return ManualCalibrationResolution(
                matchResult = autoResult,
                updatedCalibration = calibration,
                calibrationChangedSignificantly = false
            )
        }

        // Sync session baseline if restored from persistence or re-anchored externally
        val sessionKey = buildSessionKey(calibration)
        if (lastManualSessionKey != sessionKey) {
            lastManualSessionKey = sessionKey
            val startMetres = calibration.effectiveCurrentMetres!!
            stableContinuousMetres = startMetres.toDouble()
            lastReliableManualMetres = startMetres
            confirmedTravelDirection = calibration.travelDirection
            if (calibration.travelDirection == TravelChainageDirection.REVERSE) {
                lastMovingDirectionSign = -1
            } else if (calibration.travelDirection == TravelChainageDirection.FORWARD) {
                lastMovingDirectionSign = 1
            }
            lastAcceptedGpsPoint = if (calibration.anchorLatitude != null && calibration.anchorLongitude != null) {
                GeoPoint(calibration.anchorLatitude, calibration.anchorLongitude)
            } else {
                locationState?.point?.takeIf { isValidPoint(it) }
            }
            lastAcceptedGpsTimeMs = calibration.lastUpdatedTimestampMs.takeIf { it > 0L }
                ?: locationState?.lastFixTime?.takeIf { it > 0L }
                ?: nowMs
            lastAcceptedAlignedRawMetres = calibration.anchorRawAlignmentChainageMetres
                ?: (autoResult.rawUncorrectedChainage ?: autoResult.chainage)?.takeIf { isReliableAlignmentMatch(autoResult) }?.metres
            lastAcceptedDistAlongMetres = calibration.anchorDistanceAlongMetres
                ?: autoResult.distanceAlongAlignmentMetres.takeIf { isReliableAlignmentMatch(autoResult) }
            lastAcceptedAlignmentName = calibration.anchorAlignmentName
                ?: autoResult.matchedAlignmentName.takeIf { it.isNotBlank() }
            consecutiveSpikeCount = 0
            lastSpikeCandidatePoint = null
        }

        val effectiveSide = resolveSide(autoResult)
        val anchorMetres = calibration.anchorChainageMetres ?: calibration.effectiveCurrentMetres!!
        val previousMetres = lastReliableManualMetres ?: calibration.effectiveCurrentMetres!!
        val gpsPoint = locationState?.point
        val fixTimeMs = locationState?.lastFixTime?.takeIf { it > 0L } ?: nowMs
        val gpsReliableForTracking = isReliableTrackingFix(locationState, nowMs)

        // 1. Reject missing / invalid / stale / severely poor-accuracy GPS cleanly: hold last reliable chainage!
        if (!gpsReliableForTracking || gpsPoint == null) {
            val heldChainage = Chainage(previousMetres)
            val isPendingDynamic = calibration.mode == ManualChainageMode.DYNAMIC_CORRECTION &&
                !calibration.isDynamicAnchorCalibrated
            val heldStatus = when {
                isPendingDynamic -> "Dynamic Calibration: Waiting for reliable GPS"
                calibration.mode == ManualChainageMode.CONTINUE_STABLE ->
                    "Stable Chainage Retained (${heldChainage.format()}, GPS weak)"
                else -> "Dynamic Chainage Retained (${heldChainage.format()}, GPS weak)"
            }
            val result = autoResult.copy(
                chainage = heldChainage,
                customChainageText = heldChainage.format(),
                confidence = if (isPendingDynamic) MatchConfidence.MEDIUM else MatchConfidence.HIGH,
                isManual = true,
                isUncertain = true,
                detectedSide = effectiveSide,
                source = ChainageSource.MANUAL,
                lastReliableChainage = heldChainage,
                rawUncorrectedChainage = autoResult.rawUncorrectedChainage ?: autoResult.chainage,
                statusMessage = heldStatus,
                manualChainageMode = calibration.mode,
                travelDirection = confirmedTravelDirection,
                manualCalibrationAnchorMetres = anchorMetres,
                manualCalibrationOffsetMetres = calibration.computedOffsetMetres,
                isManualCalibrationPendingGps = isPendingDynamic
            )
            return ManualCalibrationResolution(result, calibration, false)
        }

        // 2. Implausible GPS jump spike rejection (without ever freezing permanently)
        val prevPt = lastAcceptedGpsPoint
        val elapsedMs = if (lastAcceptedGpsTimeMs > 0L) abs(fixTimeMs - lastAcceptedGpsTimeMs) else 0L
        val dtSec = elapsedMs / 1000.0
        val physicalMovedM = if (prevPt != null) GeoUtils.distanceMetres(prevPt, gpsPoint) else 0.0
        val isImplausibleSpike = prevPt != null && dtSec in 0.1..10.0 &&
            physicalMovedM > max(55.0, dtSec * MAX_PLAUSIBLE_SPEED_MPS + 30.0)

        if (isImplausibleSpike) {
            val consistentWithLastSpike = lastSpikeCandidatePoint?.let {
                GeoUtils.distanceMetres(it, gpsPoint) <= 35.0
            } ?: false
            if (consistentWithLastSpike) {
                consecutiveSpikeCount++
            } else {
                consecutiveSpikeCount = 1
                lastSpikeCandidatePoint = gpsPoint
            }
            if (consecutiveSpikeCount < 2) {
                // Reject single GPS jump spike cleanly without corrupting chainage or anchor
                val heldChainage = Chainage(previousMetres)
                val result = autoResult.copy(
                    chainage = heldChainage,
                    customChainageText = heldChainage.format(),
                    confidence = MatchConfidence.LOW,
                    isManual = true,
                    isUncertain = true,
                    detectedSide = effectiveSide,
                    source = ChainageSource.MANUAL,
                    lastReliableChainage = heldChainage,
                    rawUncorrectedChainage = autoResult.rawUncorrectedChainage ?: autoResult.chainage,
                    statusMessage = "GPS Jump Filtered (Holding ${heldChainage.format()})",
                    manualChainageMode = calibration.mode,
                    travelDirection = confirmedTravelDirection,
                    manualCalibrationAnchorMetres = anchorMetres,
                    manualCalibrationOffsetMetres = calibration.computedOffsetMetres,
                    isManualCalibrationPendingGps = calibration.mode == ManualChainageMode.DYNAMIC_CORRECTION &&
                        !calibration.isDynamicAnchorCalibrated
                )
                return ManualCalibrationResolution(result, calibration, false)
            }
            // 2nd consistent fix confirmed real displacement: reset spike state and proceed
            consecutiveSpikeCount = 0
            lastSpikeCandidatePoint = null
        } else {
            consecutiveSpikeCount = 0
            lastSpikeCandidatePoint = null
        }

        // 3. Branch cleanly by active mode
        return when (calibration.mode) {
            ManualChainageMode.CONTINUE_STABLE -> processContinueStableMode(
                autoResult = autoResult,
                locationState = locationState,
                gpsPoint = gpsPoint,
                fixTimeMs = fixTimeMs,
                elapsedMs = elapsedMs,
                physicalMovedM = physicalMovedM,
                effectiveSide = effectiveSide,
                calibration = calibration,
                nowMs = nowMs
            )
            ManualChainageMode.DYNAMIC_CORRECTION -> processDynamicCorrectionMode(
                autoResult = autoResult,
                locationState = locationState,
                gpsPoint = gpsPoint,
                fixTimeMs = fixTimeMs,
                physicalMovedM = physicalMovedM,
                effectiveSide = effectiveSide,
                calibration = calibration,
                nowMs = nowMs
            )
        }
    }

    /**
     * MODE 1: Continue Stable Chainage.
     * - Starts from manually entered reference and progresses continuously via filtered signed delta.
     * - NEVER snaps to raw KMZ chainage or auto-recalibrates from GPS jumps.
     * - Forward movement increases chainage; reverse movement decreases chainage.
     */
    private fun processContinueStableMode(
        autoResult: ChainageMatchResult,
        locationState: LocationState,
        gpsPoint: GeoPoint,
        fixTimeMs: Long,
        elapsedMs: Long,
        physicalMovedM: Double,
        effectiveSide: String,
        calibration: ManualChainageCalibrationState,
        nowMs: Long
    ): ManualCalibrationResolution {
        val currentBase = stableContinuousMetres
            ?: (calibration.effectiveCurrentMetres ?: 0L).toDouble()
        val alignedOk = isReliableAlignmentMatch(autoResult)
        val rawAlignedM = if (alignedOk) (autoResult.rawUncorrectedChainage ?: autoResult.chainage)?.metres else null
        val distAlongM = if (alignedOk) autoResult.distanceAlongAlignmentMetres else null
        val alignName = if (alignedOk) autoResult.matchedAlignmentName else null

        val speedMps = locationState.speed
        val isStationaryFix = physicalMovedM < MANUAL_STATIONARY_DEADBAND_METRES &&
            (speedMps == null || speedMps < 0.5f)

        var signedDelta = 0.0
        var acceptNewBaseline = true

        if (lastAcceptedGpsPoint == null) {
            // Initial GPS lock after activating Stable mode: lock baseline with 0 jump
            signedDelta = 0.0
        } else if (elapsedMs > MAX_SHORT_DROPOUT_BRIDGE_MS && physicalMovedM > 80.0) {
            // Returning after a long GPS dropout: preserve retained chainage cleanly without jumping!
            signedDelta = 0.0
        } else if (!isStationaryFix) {
            val prevAlignedRaw = lastAcceptedAlignedRawMetres
            val prevDistAlong = lastAcceptedDistAlongMetres
            val sameAlignment = alignName != null && lastAcceptedAlignmentName != null &&
                alignName == lastAcceptedAlignmentName

            signedDelta = when {
                // Best case: both previous and current fixes are reliably matched on the alignment
                // Uses actual alignment-interpolated chainage delta (respects curves, non-uniform
                // KMZ intervals, and decreasing sections) — NEVER snaps to raw KMZ!
                alignedOk && rawAlignedM != null && prevAlignedRaw != null &&
                    (sameAlignment || abs(rawAlignedM - prevAlignedRaw) <= 120L) -> {
                    val deltaM = (rawAlignedM - prevAlignedRaw).toDouble()
                    if (abs(deltaM) < MANUAL_STATIONARY_DEADBAND_METRES) 0.0 else deltaM
                }
                // Secondary: alignment polyline distance delta oriented by alignment direction
                alignedOk && distAlongM != null && prevDistAlong != null && sameAlignment -> {
                    val alignSign = if (autoResult.alignmentDirection.equals("DECREASING", ignoreCase = true)) -1.0 else 1.0
                    val deltaM = alignSign * (distAlongM - prevDistAlong)
                    if (abs(deltaM) < MANUAL_STATIONARY_DEADBAND_METRES) 0.0 else deltaM
                }
                // Off-route / no alignment fallback: heading-projected GPS displacement
                else -> computeOffRouteSignedDelta(lastAcceptedGpsPoint!!, gpsPoint, physicalMovedM)
            }
        } else {
            // Stationary: hold steady and avoid drifting baseline on micro-jitter
            acceptNewBaseline = false
            if (confirmedTravelDirection != TravelChainageDirection.UNKNOWN) {
                confirmedTravelDirection = TravelChainageDirection.STATIONARY
            }
        }

        if (abs(signedDelta) >= MANUAL_STATIONARY_DEADBAND_METRES) {
            updateTravelDirection(signedDelta, locationState.accuracy)
        }

        val updatedContinuous = (currentBase + signedDelta).coerceAtLeast(0.0)
        stableContinuousMetres = updatedContinuous
        val roundedMetres = updatedContinuous.roundToLong().coerceAtLeast(0L)
        val prevDisplayed = lastReliableManualMetres ?: calibration.effectiveCurrentMetres ?: roundedMetres
        lastReliableManualMetres = roundedMetres

        if (acceptNewBaseline || lastAcceptedGpsPoint == null) {
            lastAcceptedGpsPoint = gpsPoint
            lastAcceptedGpsTimeMs = fixTimeMs
            if (alignedOk) {
                lastAcceptedAlignedRawMetres = rawAlignedM
                lastAcceptedDistAlongMetres = distAlongM
                lastAcceptedAlignmentName = alignName
            }
        }

        val finalChainage = Chainage(roundedMetres)
        val updatedCalibration = calibration.copy(
            currentChainageMetres = roundedMetres,
            travelDirection = confirmedTravelDirection,
            lastUpdatedTimestampMs = nowMs
        )
        val changedSignificantly = abs(roundedMetres - (calibration.currentChainageMetres ?: roundedMetres)) >= 5L ||
            calibration.travelDirection != confirmedTravelDirection

        val dirTag = if (confirmedTravelDirection.arrow == "↑" || confirmedTravelDirection.arrow == "↓") {
            " ${confirmedTravelDirection.arrow}"
        } else ""
        val matchResult = autoResult.copy(
            chainage = finalChainage,
            customChainageText = finalChainage.format(),
            confidence = MatchConfidence.HIGH,
            isManual = true,
            isUncertain = false,
            detectedSide = effectiveSide,
            source = ChainageSource.MANUAL,
            lastReliableChainage = finalChainage,
            rawUncorrectedChainage = autoResult.rawUncorrectedChainage ?: autoResult.chainage,
            statusMessage = "Stable Chainage (${finalChainage.format()}$dirTag)",
            manualChainageMode = ManualChainageMode.CONTINUE_STABLE,
            travelDirection = confirmedTravelDirection,
            manualCalibrationAnchorMetres = calibration.anchorChainageMetres ?: prevDisplayed,
            manualCalibrationOffsetMetres = null,
            isManualCalibrationPendingGps = false
        )

        return ManualCalibrationResolution(
            matchResult = matchResult,
            updatedCalibration = updatedCalibration,
            calibrationChangedSignificantly = changedSignificantly
        )
    }

    /**
     * MODE 2: Dynamic Chainage Correction.
     * - Anchors manually entered chainage ONCE to a reliable GPS alignment match.
     * - Computes calibrated chainage directly from the immutable anchor:
     *     calibrated = anchorManualMetres + (currentAlignedMetres - anchorAlignedMetres)
     * - NEVER adds the offset cumulatively across repeated GPS updates.
     * - Direction-aware along alignment geometry (handles curves, reverse driving, U-turns,
     *   divided/parallel carriageways, non-uniform KMZ intervals, and decreasing sections).
     */
    private fun processDynamicCorrectionMode(
        autoResult: ChainageMatchResult,
        locationState: LocationState,
        gpsPoint: GeoPoint,
        fixTimeMs: Long,
        physicalMovedM: Double,
        effectiveSide: String,
        calibration: ManualChainageCalibrationState,
        nowMs: Long
    ): ManualCalibrationResolution {
        val anchorMetres = calibration.anchorChainageMetres ?: calibration.effectiveCurrentMetres ?: 0L
        val previousMetres = lastReliableManualMetres ?: calibration.effectiveCurrentMetres ?: anchorMetres
        val alignedOk = isReliableAlignmentMatch(autoResult)
        val rawAlignedM = if (alignedOk) (autoResult.rawUncorrectedChainage ?: autoResult.chainage)?.metres else null

        var workingCal = calibration
        var anchorJustLocked = false

        // Step A: If Dynamic anchor is not yet calibrated, lock it ONCE on the first reliable aligned GPS fix
        if (!workingCal.isDynamicAnchorCalibrated) {
            if (isReliableAnchorFix(locationState, autoResult, nowMs) && rawAlignedM != null) {
                val computedOffset = anchorMetres - rawAlignedM
                workingCal = workingCal.copy(
                    anchorRawAlignmentChainageMetres = rawAlignedM,
                    anchorDistanceAlongMetres = autoResult.distanceAlongAlignmentMetres,
                    anchorAlignmentName = autoResult.matchedAlignmentName,
                    anchorCarriagewaySide = effectiveSide,
                    anchorLatitude = gpsPoint.latitude,
                    anchorLongitude = gpsPoint.longitude,
                    anchorTimestampMs = fixTimeMs,
                    isDynamicAnchorCalibrated = true,
                    computedOffsetMetres = computedOffset,
                    currentChainageMetres = anchorMetres,
                    lastUpdatedTimestampMs = nowMs
                )
                lastManualSessionKey = buildSessionKey(workingCal)
                lastAcceptedGpsPoint = gpsPoint
                lastAcceptedGpsTimeMs = fixTimeMs
                lastAcceptedAlignedRawMetres = rawAlignedM
                lastAcceptedDistAlongMetres = autoResult.distanceAlongAlignmentMetres
                lastAcceptedAlignmentName = autoResult.matchedAlignmentName
                lastReliableManualMetres = anchorMetres
                anchorJustLocked = true
            } else {
                // Reliable aligned GPS not yet available — hold manual reference safely without jumping
                val held = Chainage(previousMetres)
                val result = autoResult.copy(
                    chainage = held,
                    customChainageText = held.format(),
                    confidence = MatchConfidence.MEDIUM,
                    isManual = true,
                    isUncertain = true,
                    detectedSide = effectiveSide,
                    source = ChainageSource.MANUAL,
                    lastReliableChainage = held,
                    rawUncorrectedChainage = autoResult.rawUncorrectedChainage ?: autoResult.chainage,
                    statusMessage = "Dynamic Calibration: Waiting for aligned GPS",
                    manualChainageMode = ManualChainageMode.DYNAMIC_CORRECTION,
                    travelDirection = confirmedTravelDirection,
                    manualCalibrationAnchorMetres = anchorMetres,
                    manualCalibrationOffsetMetres = null,
                    isManualCalibrationPendingGps = true
                )
                return ManualCalibrationResolution(result, workingCal, false)
            }
        }

        // Step B: If currently off-route or uncertain match, retain last reliable calibrated chainage cleanly
        if (!alignedOk || rawAlignedM == null) {
            val held = Chainage(previousMetres)
            val result = autoResult.copy(
                chainage = held,
                customChainageText = held.format(),
                confidence = MatchConfidence.LOW,
                isManual = true,
                isUncertain = true,
                detectedSide = effectiveSide,
                source = ChainageSource.MANUAL,
                lastReliableChainage = held,
                rawUncorrectedChainage = autoResult.rawUncorrectedChainage ?: autoResult.chainage,
                statusMessage = "Dynamic Chainage Retained (${held.format()}, off-alignment)",
                manualChainageMode = ManualChainageMode.DYNAMIC_CORRECTION,
                travelDirection = confirmedTravelDirection,
                manualCalibrationAnchorMetres = anchorMetres,
                manualCalibrationOffsetMetres = workingCal.computedOffsetMetres,
                isManualCalibrationPendingGps = false
            )
            return ManualCalibrationResolution(result, workingCal, anchorJustLocked)
        }

        // Step C: Direct NON-CUMULATIVE dynamic calculation from the immutable anchor!
        val anchorRawM = workingCal.anchorRawAlignmentChainageMetres
        val anchorDistAlong = workingCal.anchorDistanceAlongMetres
        val rawCalibratedMetres: Long = when {
            anchorRawM != null -> {
                // Exact non-cumulative formula: anchorManual + (currentAligned - anchorAligned)
                // Naturally preserves non-uniform KMZ intervals, curves, decreasing sections,
                // parallel carriageways, and forward/reverse motion!
                (anchorMetres + (rawAlignedM - anchorRawM)).coerceAtLeast(0L)
            }
            anchorDistAlong != null -> {
                val alignSign = if (autoResult.alignmentDirection.equals("DECREASING", ignoreCase = true)) -1.0 else 1.0
                (anchorMetres + (alignSign * (autoResult.distanceAlongAlignmentMetres - anchorDistAlong)).roundToLong())
                    .coerceAtLeast(0L)
            }
            else -> anchorMetres
        }

        // Step D: Stationary anti-jitter deadband & multi-sample direction detection
        val speedMps = locationState.speed
        val isStationaryJitter = !anchorJustLocked &&
            physicalMovedM < MANUAL_STATIONARY_DEADBAND_METRES &&
            abs(rawCalibratedMetres - previousMetres) <= 2L &&
            (speedMps == null || speedMps < 0.5f)

        val finalMetres: Long
        if (isStationaryJitter) {
            finalMetres = previousMetres
            if (confirmedTravelDirection != TravelChainageDirection.UNKNOWN) {
                confirmedTravelDirection = TravelChainageDirection.STATIONARY
            }
        } else {
            val prevAlignedForDir = lastAcceptedAlignedRawMetres
            if (!anchorJustLocked && prevAlignedForDir != null) {
                val stepDelta = (rawAlignedM - prevAlignedForDir).toDouble()
                if (abs(stepDelta) >= MANUAL_STATIONARY_DEADBAND_METRES) {
                    updateTravelDirection(stepDelta, locationState.accuracy)
                }
            }
            finalMetres = rawCalibratedMetres
            lastAcceptedGpsPoint = gpsPoint
            lastAcceptedGpsTimeMs = fixTimeMs
            lastAcceptedAlignedRawMetres = rawAlignedM
            lastAcceptedDistAlongMetres = autoResult.distanceAlongAlignmentMetres
            lastAcceptedAlignmentName = autoResult.matchedAlignmentName
        }

        lastReliableManualMetres = finalMetres
        val finalChainage = Chainage(finalMetres)
        val updatedCalibration = workingCal.copy(
            currentChainageMetres = finalMetres,
            travelDirection = confirmedTravelDirection,
            lastUpdatedTimestampMs = nowMs
        )
        val changedSignificantly = anchorJustLocked ||
            abs(finalMetres - (calibration.currentChainageMetres ?: finalMetres)) >= 5L ||
            calibration.travelDirection != confirmedTravelDirection

        val offsetBadge = updatedCalibration.formattedOffsetBadge?.let { " $it" }.orEmpty()
        val dirArrow = if (confirmedTravelDirection.arrow == "↑" || confirmedTravelDirection.arrow == "↓") {
            " ${confirmedTravelDirection.arrow}"
        } else ""
        val matchResult = autoResult.copy(
            chainage = finalChainage,
            customChainageText = finalChainage.format(),
            confidence = autoResult.confidence.coerceAtLeast(MatchConfidence.MEDIUM),
            isManual = true,
            isUncertain = false,
            detectedSide = effectiveSide,
            source = ChainageSource.MANUAL,
            lastReliableChainage = finalChainage,
            rawUncorrectedChainage = Chainage(rawAlignedM),
            correctionPointInfo = "Dynamic Cal Ref ${Chainage(anchorMetres).format()}$offsetBadge",
            statusMessage = "Dynamic Calibrated (${finalChainage.format()}$offsetBadge$dirArrow)",
            manualChainageMode = ManualChainageMode.DYNAMIC_CORRECTION,
            travelDirection = confirmedTravelDirection,
            manualCalibrationAnchorMetres = anchorMetres,
            manualCalibrationOffsetMetres = updatedCalibration.computedOffsetMetres,
            isManualCalibrationPendingGps = false
        )

        return ManualCalibrationResolution(
            matchResult = matchResult,
            updatedCalibration = updatedCalibration,
            calibrationChangedSignificantly = changedSignificantly
        )
    }

    private fun updateTravelDirection(signedDeltaMetres: Double, gpsAccuracy: Float) {
        if (abs(signedDeltaMetres) < MANUAL_STATIONARY_DEADBAND_METRES) return
        val stepSign = if (signedDeltaMetres > 0.0) 1 else -1

        if (confirmedTravelDirection == TravelChainageDirection.UNKNOWN) {
            confirmedTravelDirection = if (stepSign > 0) TravelChainageDirection.FORWARD else TravelChainageDirection.REVERSE
            lastMovingDirectionSign = stepSign
            pendingDirectionSign = 0
            pendingDirectionSamples = 0
            pendingDirectionAccumMetres = 0.0
            return
        }

        if (stepSign == lastMovingDirectionSign) {
            confirmedTravelDirection = if (stepSign > 0) TravelChainageDirection.FORWARD else TravelChainageDirection.REVERSE
            pendingDirectionSign = 0
            pendingDirectionSamples = 0
            pendingDirectionAccumMetres = 0.0
            return
        }

        // Opposite sign detected: apply multi-sample hysteresis so GPS noise never flips direction,
        // while real U-turns / reverse driving respond cleanly within 1-2 fixes.
        if (pendingDirectionSign == stepSign) {
            pendingDirectionSamples += 1
            pendingDirectionAccumMetres += abs(signedDeltaMetres)
        } else {
            pendingDirectionSign = stepSign
            pendingDirectionSamples = 1
            pendingDirectionAccumMetres = abs(signedDeltaMetres)
        }

        val instantCleanFlip = abs(signedDeltaMetres) >= DIRECTION_FLIP_INSTANT_M && gpsAccuracy <= 15.0f
        if (instantCleanFlip ||
            (pendingDirectionSamples >= DIRECTION_FLIP_MIN_SAMPLES &&
                pendingDirectionAccumMetres >= DIRECTION_FLIP_MIN_ACCUM_M)
        ) {
            lastMovingDirectionSign = stepSign
            confirmedTravelDirection = if (stepSign > 0) TravelChainageDirection.FORWARD else TravelChainageDirection.REVERSE
            pendingDirectionSign = 0
            pendingDirectionSamples = 0
            pendingDirectionAccumMetres = 0.0
        }
    }

    private fun computeOffRouteSignedDelta(prev: GeoPoint, curr: GeoPoint, movedDist: Double): Double {
        if (movedDist < MANUAL_STATIONARY_DEADBAND_METRES) return 0.0
        val midLatRad = Math.toRadians((prev.latitude + curr.latitude) / 2.0)
        val dNorth = Math.toRadians(curr.latitude - prev.latitude) * GeoUtils.EARTH_RADIUS_METRES
        val dEast = Math.toRadians(curr.longitude - prev.longitude) * GeoUtils.EARTH_RADIUS_METRES * cos(midLatRad)
        val len = sqrt(dEast * dEast + dNorth * dNorth)
        if (len < MANUAL_STATIONARY_DEADBAND_METRES) return 0.0
        val uE = dEast / len
        val uN = dNorth / len

        val refE = offRouteRefUnitEast
        val refN = offRouteRefUnitNorth
        if (refE == null || refN == null) {
            offRouteRefUnitEast = uE
            offRouteRefUnitNorth = uN
            return len
        }

        val dot = uE * refE + uN * refN
        return if (dot >= -0.35) {
            // Continuing forward (or rounding a curve): smoothly track road tangent
            if (dot >= 0.25) {
                val blendE = 0.7 * refE + 0.3 * uE
                val blendN = 0.7 * refN + 0.3 * uN
                val bLen = sqrt(blendE * blendE + blendN * blendN)
                if (bLen > 1e-4) {
                    offRouteRefUnitEast = blendE / bLen
                    offRouteRefUnitNorth = blendN / bLen
                }
            }
            len
        } else {
            // Reversing against established forward tangent
            -len
        }
    }

    private fun isValidPoint(pt: GeoPoint): Boolean {
        if (pt.latitude.isNaN() || pt.longitude.isNaN() || pt.latitude.isInfinite() || pt.longitude.isInfinite()) return false
        if (pt.latitude !in -90.0..90.0 || pt.longitude !in -180.0..180.0) return false
        if (pt.latitude == 0.0 && pt.longitude == 0.0) return false
        return true
    }

    private fun isReliableTrackingFix(loc: LocationState?, nowMs: Long): Boolean {
        if (loc == null || loc.point == null || !isValidPoint(loc.point)) return false
        if (loc.status == LocationStatus.DISABLED || loc.status == LocationStatus.PERMISSION_DENIED) return false
        if (loc.accuracy.isNaN() || loc.accuracy < 0f || loc.accuracy > MAX_TRACKING_GPS_ACCURACY_M) return false
        if (loc.lastFixTime > 0L && nowMs > 0L && (nowMs - loc.lastFixTime) > MAX_GPS_FIX_AGE_MS) return false
        return true
    }

    private fun isReliableAnchorFix(
        loc: LocationState?,
        matchResult: ChainageMatchResult?,
        nowMs: Long
    ): Boolean {
        if (loc == null || loc.point == null || !isValidPoint(loc.point)) return false
        if (loc.status == LocationStatus.DISABLED || loc.status == LocationStatus.PERMISSION_DENIED) return false
        if (loc.accuracy.isNaN() || loc.accuracy < 0f || loc.accuracy > MAX_ANCHOR_GPS_ACCURACY_M) return false
        if (loc.lastFixTime > 0L && nowMs > 0L && (nowMs - loc.lastFixTime) > MAX_GPS_FIX_AGE_MS) return false
        return matchResult != null && isReliableAlignmentMatch(matchResult)
    }

    private fun isReliableAlignmentMatch(matchResult: ChainageMatchResult): Boolean {
        val rawCh = matchResult.rawUncorrectedChainage ?: matchResult.chainage
        return rawCh != null &&
            matchResult.confidence != MatchConfidence.NO_MATCH &&
            !matchResult.isUncertain
    }

    private fun resolveSide(matchResult: ChainageMatchResult?): String {
        return manualSideOverride
            ?: matchResult?.smartDirectionState?.confirmedSide?.takeIf { it.isNotBlank() }
            ?: matchResult?.detectedSide?.takeIf { it.isNotBlank() }
            ?: currentDetectedSide
    }

    private fun buildSessionKey(cal: ManualChainageCalibrationState): String {
        return "${cal.mode.storageKey}|${cal.anchorChainageMetres}|${cal.anchorTimestampMs}|${cal.isDynamicAnchorCalibrated}|${cal.anchorRawAlignmentChainageMetres}"
    }

    private fun MatchConfidence.coerceAtLeast(minConfidence: MatchConfidence): MatchConfidence {
        // MatchConfidence order: HIGH(0), MEDIUM(1), LOW(2), NO_MATCH(3)
        return if (this.ordinal <= minConfidence.ordinal) this else minConfidence
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
