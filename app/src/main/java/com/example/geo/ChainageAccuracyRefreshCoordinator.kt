package com.example.geo

import com.example.location.GpsValidationResult
import com.example.location.LocationState
import com.example.location.LocationTracker
import com.example.model.CalibrationValidationStatus
import com.example.model.Chainage
import com.example.model.ChainageAccuracyReport
import com.example.model.ChainageAccuracyStatus
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.PermanentCalibrationProfile
import com.example.model.RefreshMode
import com.example.model.RouteData
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

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

        // Ignore duplicate callbacks carrying the same fix timestamp
        if (fixTimeMs > 0L && fixTimeMs == lastObservedFixTimeMs) {
            return StuckDetectionResult(false)
        }
        lastObservedFixTimeMs = fixTimeMs

        val displacement = GeoUtils.distanceMetres(prevAnchor, gpsPoint)

        // Legitimately stationary or negligible GPS jitter: reset counter & never force refresh
        if (displacement < stationaryBandMetres) {
            displacedSameChainageCount = 0
            return StuckDetectionResult(false)
        }

        // Chainage progressed normally: advance anchor cleanly
        if (chainageMetres != prevMetres) {
            anchorGpsPoint = gpsPoint
            anchorChainageMetres = chainageMetres
            displacedSameChainageCount = 0
            return StuckDetectionResult(false)
        }

        // Valid GPS displaced significantly (>= 12m), yet computed chainage did not change
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

/**
 * Race-condition-free orchestrator for Smart Refresh & Force Refresh All – Chainage Accuracy.
 * Ensures older async calculations cannot overwrite newer GPS fixes or newer refresh cycles,
 * preserves all permanent control points / profiles / manual values, and retains last valid
 * chainage with clear stale marking if any step fails.
 */
class ChainageAccuracyRefreshCoordinator {

    private val generationCounter = AtomicLong(0L)
    private val refreshInProgress = AtomicBoolean(false)

    data class CalibrationSyncOutcome(
        val syncedPoints: List<ChainageCorrectionPoint>,
        val compatibility: SmartChainageCorrectionEngine.ProfileCompatibilityResult?,
        val conflictReport: SmartChainageCorrectionEngine.ReferenceConflictReport,
        val hasDuplicates: Boolean,
        val duplicateDetails: List<String>
    )

    data class RefreshExecutionOutput(
        val generation: Long,
        val matchResult: ChainageMatchResult,
        val report: ChainageAccuracyReport,
        val syncedPoints: List<ChainageCorrectionPoint>?,
        val profileCompatibility: SmartChainageCorrectionEngine.ProfileCompatibilityResult?,
        val shouldPersistPoints: Boolean
    )

    fun currentGeneration(): Long = generationCounter.get()

    fun bumpGeneration(): Long = generationCounter.incrementAndGet()

    fun isBusy(): Boolean = refreshInProgress.get()

    fun tryBeginRefresh(): Long? {
        if (!refreshInProgress.compareAndSet(false, true)) {
            return null
        }
        return generationCounter.incrementAndGet()
    }

    fun endRefresh() {
        refreshInProgress.set(false)
    }

    /**
     * Step C: Validates permanent control points, synchronizes active calibration profile,
     * detects duplicate/conflicting references, and recalculates alignment-distance projections
     * WITHOUT deleting any control points or resetting profiles.
     */
    fun synchronizeCalibrationData(
        route: RouteData?,
        rawPoints: List<ChainageCorrectionPoint>,
        profiles: List<PermanentCalibrationProfile>,
        forceRevalidate: Boolean
    ): CalibrationSyncOutcome {
        if (route == null) {
            return CalibrationSyncOutcome(
                syncedPoints = rawPoints,
                compatibility = null,
                conflictReport = SmartChainageCorrectionEngine.ReferenceConflictReport(
                    hasConflict = false,
                    validationStatus = CalibrationValidationStatus.VERIFIED,
                    conflicts = emptyList(),
                    warnings = emptyList()
                ),
                hasDuplicates = false,
                duplicateDetails = emptyList()
            )
        }

        val fingerprint = SmartChainageCorrectionEngine.computeAlignmentFingerprint(route)
        val exactProfile = profiles.firstOrNull { it.alignmentFingerprint == fingerprint }
        val sameRouteProfile = profiles.firstOrNull {
            it.originalFileName.equals(route.fileName, ignoreCase = true) || it.routeId == route.id
        }
        val candidateProfile = exactProfile ?: sameRouteProfile
        val compat = candidateProfile?.let {
            SmartChainageCorrectionEngine.evaluateProfileCompatibility(it, route)
        }

        // Preserve existing permanent points; only hydrate from verified profile if route has none yet
        val basePoints = if (rawPoints.isNotEmpty()) {
            rawPoints
        } else if (compat != null && compat.isExactGeometryMatch && compat.revalidatedPoints.isNotEmpty()) {
            compat.revalidatedPoints
        } else {
            emptyList()
        }

        val refreshedPoints = if (forceRevalidate && basePoints.isNotEmpty()) {
            val reprojected = basePoints.map { pt ->
                val resolved = SmartChainageCorrectionEngine.resolveRoutePositionForChainage(
                    routeData = route,
                    targetKmzMetres = pt.originalKmzMetres,
                    fallbackGps = pt.point
                )
                pt.copy(
                    routeDistanceMetres = resolved.routeDistanceMetres
                )
            }
            SmartChainageCorrectionEngine.sortPointsInRouteOrder(reprojected, route)
        } else {
            SmartChainageCorrectionEngine.sortPointsInRouteOrder(basePoints, route)
        }

        val enabledPoints = refreshedPoints.filter { it.isEnabled }
        val conflictReport = if (enabledPoints.isNotEmpty()) {
            SmartChainageCorrectionEngine.detectReferenceConflicts(enabledPoints, route)
        } else {
            SmartChainageCorrectionEngine.ReferenceConflictReport(
                hasConflict = false,
                validationStatus = CalibrationValidationStatus.VERIFIED,
                conflicts = emptyList(),
                warnings = emptyList()
            )
        }

        // Detect duplicate control points placed on nearly identical positions/chainages
        val dupDetails = mutableListOf<String>()
        for (i in 0 until enabledPoints.size - 1) {
            val a = enabledPoints[i]
            val b = enabledPoints[i + 1]
            val sameOrig = abs(a.originalKmzMetres - b.originalKmzMetres) <= 1L
            val sameDist = a.routeDistanceMetres > 0.01 && b.routeDistanceMetres > 0.01 &&
                abs(a.routeDistanceMetres - b.routeDistanceMetres) <= 1.5
            if (sameOrig || sameDist) {
                dupDetails.add("Duplicate ref near ${a.originalKmzChainage.format()}")
            }
        }

        return CalibrationSyncOutcome(
            syncedPoints = refreshedPoints,
            compatibility = compat,
            conflictReport = conflictReport,
            hasDuplicates = dupDetails.isNotEmpty(),
            duplicateDetails = dupDetails
        )
    }

    /**
     * Steps D, E, F: Recalculates KMZ match, calibration, LHS/RHS side, and evaluates all
     * 9 accuracy checks into one strict status classification.
     */
    fun recalculateAndValidate(
        generation: Long,
        mode: RefreshMode,
        locationState: LocationState,
        gpsValidation: GpsValidationResult,
        route: RouteData?,
        routeValidGeometry: Boolean,
        calibrationSync: CalibrationSyncOutcome,
        isCorrectionsEnabled: Boolean,
        previousResult: ChainageMatchResult?,
        progressionTracker: ChainageProgressionTracker,
        manualChainage: Chainage?,
        manualChainageText: String?,
        gpsRefreshSucceeded: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): RefreshExecutionOutput {
        val notes = mutableListOf<String>()
        val isManualActive = manualChainage != null || !manualChainageText.isNullOrBlank()

        notes += if (gpsValidation.isValid && gpsValidation.isFresh) {
            "GPS: ${gpsValidation.reason}"
        } else {
            "GPS: ${gpsValidation.reason}"
        }

        if (route == null || !routeValidGeometry) {
            val retained = previousResult?.copy(
                gpsAccuracy = if (gpsValidation.isValid) locationState.accuracy else previousResult.gpsAccuracy,
                statusMessage = "Alignment unavailable — retained last state"
            ) ?: ChainageMatchResult.noRoute(locationState.accuracy)

            val reportStatus = if (!gpsValidation.isValid) {
                ChainageAccuracyStatus.NEEDS_GPS
            } else {
                ChainageAccuracyStatus.ALIGNMENT_MISMATCH
            }

            notes += "Alignment: ${if (route == null) "No KMZ/KML loaded" else "Invalid alignment geometry"}"

            return RefreshExecutionOutput(
                generation = generation,
                matchResult = retained,
                report = ChainageAccuracyReport(
                    status = reportStatus,
                    mode = mode,
                    isRefreshing = false,
                    isStaleFallback = previousResult?.chainage != null,
                    summaryMessage = if (previousResult?.chainage != null) {
                        "${reportStatus.label} • Retained last chainage ${previousResult.displayChainage} (Stale)"
                    } else {
                        "${reportStatus.label} • Load a valid KMZ/KML alignment"
                    },
                    lastRefreshedEpochMs = nowMs,
                    isGpsValid = gpsValidation.isValid,
                    isGpsFresh = gpsValidation.isFresh,
                    gpsFixAgeMs = gpsValidation.fixAgeMs,
                    matchConfidence = MatchConfidence.NO_MATCH,
                    isCalibrationProfileValid = calibrationSync.compatibility?.isCompatible ?: true,
                    isControlPointConsistent = !calibrationSync.conflictReport.hasConflict && !calibrationSync.hasDuplicates,
                    isChainageProgressionValid = false,
                    isSideConsistent = false,
                    isCustomCorrectionValid = true,
                    isManualOverrideActive = isManualActive,
                    stepNotes = notes
                ),
                syncedPoints = calibrationSync.syncedPoints,
                profileCompatibility = calibrationSync.compatibility,
                shouldPersistPoints = calibrationSync.syncedPoints.isNotEmpty()
            )
        }

        // Retain last valid chainage safely if GPS coordinates are completely invalid/missing
        if (!gpsValidation.isValid || locationState.point == null) {
            val fallback = (previousResult ?: ChainageMatchResult.noRoute(locationState.accuracy)).copy(
                statusMessage = if (previousResult?.chainage != null) {
                    "GPS unavailable — retained ${previousResult.displayChainage} (Stale)"
                } else {
                    "Waiting for valid GPS location"
                }
            )
            notes += "Chainage: Preserved last valid state (${fallback.displayChainage})"
            return RefreshExecutionOutput(
                generation = generation,
                matchResult = fallback,
                report = ChainageAccuracyReport(
                    status = ChainageAccuracyStatus.NEEDS_GPS,
                    mode = mode,
                    isRefreshing = false,
                    isStaleFallback = previousResult?.chainage != null,
                    summaryMessage = if (previousResult?.chainage != null) {
                        "Needs GPS • Retained last valid chainage ${previousResult.displayChainage} (Stale)"
                    } else {
                        "Needs GPS • Valid location fix required"
                    },
                    lastRefreshedEpochMs = nowMs,
                    isGpsValid = false,
                    isGpsFresh = false,
                    gpsFixAgeMs = gpsValidation.fixAgeMs,
                    matchConfidence = previousResult?.confidence ?: MatchConfidence.NO_MATCH,
                    offsetMetres = previousResult?.offsetMetres ?: 0.0,
                    isCalibrationProfileValid = calibrationSync.compatibility?.isCompatible ?: true,
                    isControlPointConsistent = !calibrationSync.conflictReport.hasConflict && !calibrationSync.hasDuplicates,
                    isChainageProgressionValid = false,
                    isSideConsistent = false,
                    isCustomCorrectionValid = !calibrationSync.conflictReport.hasConflict,
                    isManualOverrideActive = isManualActive,
                    stepNotes = notes
                ),
                syncedPoints = calibrationSync.syncedPoints,
                profileCompatibility = calibrationSync.compatibility,
                shouldPersistPoints = false
            )
        }

        // Force Refresh All resets transient progression filter & previous lock so stuck chainage recalculates cleanly
        if (mode == RefreshMode.FORCE_ALL) {
            progressionTracker.resetChainageFilter()
        }

        val kmzMatch = ChainageMatcher.match(
            gpsPoint = locationState.point,
            gpsAccuracy = locationState.accuracy,
            bearing = locationState.bearing,
            routeData = route,
            previousResult = if (mode == RefreshMode.FORCE_ALL) null else previousResult
        )

        val rawKmz = kmzMatch.chainage
        if (kmzMatch.confidence == MatchConfidence.NO_MATCH || rawKmz == null) {
            val stabilizedNoMatch = progressionTracker.processResult(kmzMatch, route.id).copy(
                source = ChainageSource.GPS,
                rawUncorrectedChainage = null
            )
            notes += "Alignment: GPS is off-route (${kmzMatch.offsetMetres.toInt()}m from corridor)"
            return RefreshExecutionOutput(
                generation = generation,
                matchResult = stabilizedNoMatch,
                report = ChainageAccuracyReport(
                    status = ChainageAccuracyStatus.ALIGNMENT_MISMATCH,
                    mode = mode,
                    isRefreshing = false,
                    isStaleFallback = false,
                    summaryMessage = "Alignment Mismatch • ${kmzMatch.offsetMetres.toInt()}m from ${route.name}",
                    lastRefreshedEpochMs = nowMs,
                    isGpsValid = true,
                    isGpsFresh = gpsValidation.isFresh,
                    gpsFixAgeMs = gpsValidation.fixAgeMs,
                    matchConfidence = MatchConfidence.NO_MATCH,
                    offsetMetres = kmzMatch.offsetMetres,
                    isCalibrationProfileValid = calibrationSync.compatibility?.isCompatible ?: true,
                    isControlPointConsistent = !calibrationSync.conflictReport.hasConflict && !calibrationSync.hasDuplicates,
                    isChainageProgressionValid = false,
                    isSideConsistent = false,
                    isCustomCorrectionValid = true,
                    isManualOverrideActive = isManualActive,
                    stepNotes = notes
                ),
                syncedPoints = calibrationSync.syncedPoints,
                profileCompatibility = calibrationSync.compatibility,
                shouldPersistPoints = false
            )
        }

        // Apply calibrated correction if enabled
        val enabledPoints = calibrationSync.syncedPoints.filter { it.isEnabled }
        val smartCorrected = if (isCorrectionsEnabled && enabledPoints.isNotEmpty()) {
            SmartChainageCorrectionEngine.applyCorrection(
                rawChainage = rawKmz,
                routeData = route,
                correctionPoints = enabledPoints,
                gpsPoint = locationState.point,
                gpsAccuracy = locationState.accuracy,
                confidence = kmzMatch.confidence,
                distanceAlongAlignmentMetres = kmzMatch.distanceAlongAlignmentMetres,
                isUncertainJump = kmzMatch.isUncertain
            )
        } else null

        val preStabilized = if (smartCorrected != null) {
            kmzMatch.copy(
                chainage = smartCorrected.correctedChainage,
                source = ChainageSource.CUSTOM_CORRECTION,
                rawUncorrectedChainage = rawKmz,
                correctionPointInfo = smartCorrected.infoMessage,
                statusMessage = "Custom Correction (${smartCorrected.infoMessage})"
            )
        } else {
            kmzMatch.copy(
                source = ChainageSource.KMZ,
                rawUncorrectedChainage = rawKmz
            )
        }

        val stabilized = progressionTracker.processResult(preStabilized, route.id)

        // Evaluate all 9 validation rules strictly
        val hasControlPointConflict = calibrationSync.conflictReport.hasConflict ||
            calibrationSync.hasDuplicates ||
            (smartCorrected?.hasConflict == true) ||
            calibrationSync.conflictReport.validationStatus == CalibrationValidationStatus.NEEDS_REVIEW
        val isProfileValid = calibrationSync.compatibility?.let {
            it.isCompatible && (it.isExactGeometryMatch || enabledPoints.isEmpty())
        } ?: true
        val isCustomValid = !hasControlPointConflict
        val isSideValid = stabilized.detectedSide.equals("LHS", true) ||
            stabilized.detectedSide.equals("RHS", true) ||
            stabilized.detectedSide.equals("BHS", true)
        val isProgressionValid = stabilized.chainage != null &&
            !stabilized.isUncertain &&
            stabilized.confidence != MatchConfidence.NO_MATCH
        val isReliableMatch = (stabilized.confidence == MatchConfidence.HIGH || stabilized.confidence == MatchConfidence.MEDIUM) &&
            !stabilized.isUncertain &&
            gpsValidation.isValid &&
            gpsValidation.isFresh

        val changedFromPrevious = previousResult == null ||
            previousResult.chainage?.metres != stabilized.chainage?.metres ||
            previousResult.detectedSide != stabilized.detectedSide ||
            previousResult.source != stabilized.source ||
            previousResult.confidence != stabilized.confidence

        val finalStatus = when {
            !gpsValidation.isValid || (!gpsRefreshSucceeded && !gpsValidation.isFresh) -> ChainageAccuracyStatus.NEEDS_GPS
            !isProfileValid -> ChainageAccuracyStatus.CALIBRATION_NEEDS_REVIEW
            hasControlPointConflict -> ChainageAccuracyStatus.CALIBRATION_NEEDS_REVIEW
            stabilized.confidence == MatchConfidence.LOW || stabilized.isUncertain -> ChainageAccuracyStatus.NEEDS_GPS
            !isReliableMatch -> ChainageAccuracyStatus.NEEDS_GPS
            isManualActive -> ChainageAccuracyStatus.UPDATED
            changedFromPrevious -> ChainageAccuracyStatus.UPDATED
            else -> ChainageAccuracyStatus.VERIFIED
        }

        notes += "Alignment: Matched ${stabilized.matchedAlignmentName.ifBlank { route.name }} (±${stabilized.offsetMetres.toInt()}m)"
        notes += "Calibration: ${if (smartCorrected != null) smartCorrected.infoMessage else "Original KMZ Baseline"}"
        notes += "Side: ${stabilized.detectedSide} • Manual: ${if (isManualActive) "Active (${manualChainage?.format() ?: manualChainageText})" else "Off"}"

        val summary = when (finalStatus) {
            ChainageAccuracyStatus.VERIFIED ->
                "Accurate / Verified • ${stabilized.displayChainage} (${stabilized.detectedSide})"
            ChainageAccuracyStatus.UPDATED -> if (isManualActive) {
                "Updated • Background KMZ refreshed (${stabilized.displayChainage}); Manual override preserved"
            } else {
                "Updated • ${stabilized.displayChainage} (${stabilized.detectedSide})"
            }
            ChainageAccuracyStatus.NEEDS_GPS ->
                "Needs GPS • ${gpsValidation.reason}"
            ChainageAccuracyStatus.ALIGNMENT_MISMATCH ->
                "Alignment Mismatch • Check active KMZ/KML route"
            ChainageAccuracyStatus.CALIBRATION_NEEDS_REVIEW -> {
                val conflictMsg = calibrationSync.conflictReport.conflicts.firstOrNull()
                    ?: calibrationSync.duplicateDetails.firstOrNull()
                    ?: calibrationSync.compatibility?.statusMessage
                    ?: "Review control points"
                "Calibration Needs Review • $conflictMsg"
            }
        }

        return RefreshExecutionOutput(
            generation = generation,
            matchResult = stabilized,
            report = ChainageAccuracyReport(
                status = finalStatus,
                mode = mode,
                isRefreshing = false,
                isStaleFallback = false,
                summaryMessage = summary,
                lastRefreshedEpochMs = nowMs,
                isGpsValid = gpsValidation.isValid,
                isGpsFresh = gpsValidation.isFresh,
                gpsFixAgeMs = gpsValidation.fixAgeMs,
                matchConfidence = stabilized.confidence,
                offsetMetres = stabilized.offsetMetres,
                isCalibrationProfileValid = isProfileValid,
                isControlPointConsistent = !hasControlPointConflict,
                isChainageProgressionValid = isProgressionValid,
                isSideConsistent = isSideValid,
                isCustomCorrectionValid = isCustomValid,
                isManualOverrideActive = isManualActive,
                stepNotes = notes
            ),
            syncedPoints = calibrationSync.syncedPoints,
            profileCompatibility = calibrationSync.compatibility,
            shouldPersistPoints = calibrationSync.syncedPoints.isNotEmpty()
        )
    }
}
