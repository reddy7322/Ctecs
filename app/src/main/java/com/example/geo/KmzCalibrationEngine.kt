package com.example.geo

import com.example.model.CenterlineOffsetZone
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.GeoPoint
import com.example.model.KmzCalibrationApplicationResult
import com.example.model.KmzCalibrationCoverageStatus
import com.example.model.KmzCalibrationEditHistoryEntry
import com.example.model.KmzCalibrationRecordStatus
import com.example.model.KmzCalibrationSuggestion
import com.example.model.KmzCalibrationSuggestionStatus
import com.example.model.KmzManualCalibration
import com.example.model.MatchConfidence
import com.example.model.ParallelCenterlineOffset
import com.example.model.RouteAlignment
import com.example.model.RouteData
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Centralized Manual Reference Calibration Engine for KMZ/KML alignments.
 *
 * Core Rules:
 * 1. Offset = Manual Reference Chainage - Original KMZ/KML Chainage at the reference position.
 * 2. Corrected Chainage = Original KMZ/KML Chainage + Offset (applied once in fixed-point integer millimetres, never cumulatively).
 * 3. Offset applies ONLY within ±2,000 m along the selected road alignment from the reference anchor.
 * 4. Outside ±2,000 m, original KMZ/KML chainage is returned untouched with clear coverage status.
 * 5. Supports Smart Calibration Chainage List (add, edit with preserved history, delete individual record, duplicate prevention).
 * 6. Evaluates Smart Calibration Suggestions using GPS accuracy <= ±5 m and lateral centerline offset <= 20 m.
 */
object KmzCalibrationEngine {

    const val COVERAGE_RADIUS_METRES = KmzManualCalibration.DEFAULT_COVERAGE_RADIUS_METRES
    const val COVERAGE_BOUNDARY_EPSILON_METRES = KmzManualCalibration.COVERAGE_BOUNDARY_EPSILON_METRES
    const val MAX_SUGGESTION_GPS_ACCURACY_METRES = 5.0f
    const val MAX_SUGGESTION_LATERAL_OFFSET_METRES = 20.0
    const val DEFAULT_REQUIRED_STABLE_SAMPLES = 2
    const val SAME_ANCHOR_MERGE_PROXIMITY_METRES = 25L
    const val CONFLICTING_REFERENCE_PROXIMITY_METRES = 250.0

    private val STRICT_PLUS_REGEX = Regex(
        """^\s*(?:CH|CHAINAGE|KM|STA|STATION)?[:\s.\-]*(\d{1,5})\s*\+\s*(\d{1,3}(?:\.\d{1,2})?)\s*(?:m)?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val STRICT_SLASH_REGEX = Regex(
        """^\s*(?:CH|CHAINAGE|KM)?[:\s.\-]*(\d{1,5})\s*/\s*(\d{1,3})\s*(?:m)?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val STRICT_DECIMAL_KM_REGEX = Regex(
        """^\s*(\d{1,5})\.(\d{1,3})\s*(?:km)?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val STRICT_DIGITS_REGEX = Regex("""^\s*(\d{1,8})\s*$""")

    data class ResolvedAlignmentPosition(
        val originalKmzMetres: Long,
        val distanceAlongAlignmentMetres: Double,
        val point: GeoPoint,
        val alignmentId: String,
        val alignmentName: String,
        val parallelOffset: ParallelCenterlineOffset? = null
    )

    fun storageKey(routeId: String, alignmentId: String?): String {
        val cleanRoute = routeId.trim()
        val cleanAlign = alignmentId?.trim().takeIf { !it.isNullOrEmpty() } ?: cleanRoute
        return "$cleanRoute::$cleanAlign"
    }

    fun recordStorageKey(calibration: KmzManualCalibration): String {
        val base = storageKey(calibration.routeId, calibration.alignmentId)
        val cleanId = calibration.id.trim()
        return if (cleanId.isNotEmpty() && cleanId.startsWith("$base::")) {
            cleanId
        } else if (cleanId.isNotEmpty()) {
            "$base::$cleanId"
        } else {
            "$base::${calibration.originalKmzMetres}"
        }
    }

    fun resolveEffectiveAlignment(
        routeData: RouteData,
        alignmentId: String? = null,
        alignmentName: String? = null
    ): RouteAlignment? {
        if (routeData.alignments.isEmpty()) return null
        if (!alignmentId.isNullOrBlank()) {
            routeData.alignments.firstOrNull { it.id == alignmentId }?.let { return it }
        }
        if (!alignmentName.isNullOrBlank()) {
            routeData.alignments.firstOrNull { it.name == alignmentName }?.let { return it }
        }
        if (!routeData.selectedAlignmentId.isNullOrBlank()) {
            routeData.alignments.firstOrNull { it.id == routeData.selectedAlignmentId }?.let { return it }
        }
        return routeData.activeAlignment ?: routeData.alignments.firstOrNull()
    }

    /**
     * Computes a stable SHA-256 fingerprint for an alignment's geometry and markers so
     * switching or modifying KMZ/KML files never silently applies an incompatible offset.
     */
    fun computeAlignmentFingerprint(
        routeData: RouteData,
        alignment: RouteAlignment? = resolveEffectiveAlignment(routeData)
    ): String {
        val sb = StringBuilder()
        sb.append(routeData.fileName.trim()).append('|')
        if (alignment != null) {
            sb.append(alignment.id).append('|')
            sb.append(alignment.name).append('|')
            sb.append(alignment.direction).append('|')
            sb.append(alignment.points.size).append('|')
            alignment.points.firstOrNull()?.let {
                sb.append(String.format(Locale.US, "%.5f,%.5f", it.latitude, it.longitude)).append('|')
            }
            alignment.points.lastOrNull()?.let {
                sb.append(String.format(Locale.US, "%.5f,%.5f", it.latitude, it.longitude)).append('|')
            }
        }
        val markers = if (alignment != null && alignment.points.size >= 2) {
            ChainageMatcher.getOrCreateCachedGeometry(alignment, routeData.markers).mappedMarkers
                .map { it.marker }
        } else {
            routeData.markers
        }
        sb.append(markers.size).append('|')
        markers.firstOrNull()?.let {
            sb.append(it.chainage.metres).append('@')
                .append(String.format(Locale.US, "%.5f,%.5f", it.point.latitude, it.point.longitude))
                .append('|')
        }
        markers.lastOrNull()?.let {
            sb.append(it.chainage.metres).append('@')
                .append(String.format(Locale.US, "%.5f,%.5f", it.point.latitude, it.point.longitude))
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun parseManualReferenceChainage(rawInput: String?): Pair<Chainage?, String?> {
        val trimmed = rawInput?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null to "Enter a manual reference chainage (for example: 192+230)."
        }
        if (trimmed.startsWith("-")) {
            return null to "Negative chainage is not valid."
        }
        if (trimmed.contains("+")) {
            val m = STRICT_PLUS_REGEX.matchEntire(trimmed)
                ?: return null to "Invalid format. Use KM+MMM (for example: 192+230)."
            val km = m.groupValues[1].toLongOrNull()
                ?: return null to "Invalid kilometre value."
            val metresVal = m.groupValues[2].toDoubleOrNull()
                ?: return null to "Invalid metre value."
            if (metresVal < 0.0 || metresVal >= 1000.0) {
                return null to "Metres after '+' must be between 000 and 999."
            }
            return Chainage(km * 1000L + Math.round(metresVal)) to null
        }
        if (trimmed.contains("/")) {
            val m = STRICT_SLASH_REGEX.matchEntire(trimmed)
                ?: return null to "Invalid format. Use KM+MMM (for example: 192+230)."
            val parsed = Chainage.parse(trimmed)
            return if (parsed != null && parsed.metres >= 0L) {
                parsed to null
            } else {
                null to "Invalid chainage value."
            }
        }
        val dec = STRICT_DECIMAL_KM_REGEX.matchEntire(trimmed)
        if (dec != null) {
            val km = dec.groupValues[1].toLongOrNull() ?: return null to "Invalid chainage value."
            val m = dec.groupValues[2].padEnd(3, '0').take(3).toLongOrNull()
                ?: return null to "Invalid chainage value."
            return Chainage(km * 1000L + m) to null
        }
        val digits = STRICT_DIGITS_REGEX.matchEntire(trimmed)
        if (digits != null) {
            val raw = digits.groupValues[1]
            val value = raw.toLongOrNull() ?: return null to "Invalid chainage number."
            val metres = if (raw.length <= 3 && !raw.startsWith("0")) value * 1000L else value
            return Chainage(metres) to null
        }
        return null to "Invalid chainage format. Use KM+MMM (for example: 192+230)."
    }

    /**
     * Resolves exact cumulative distance along [targetAlignment] and interpolated coordinate for an
     * original KMZ/KML chainage value, supporting increasing, decreasing, and non-uniform markers.
     */
    fun resolveAlignmentPositionForOriginalChainage(
        routeData: RouteData,
        originalKmzMetres: Long,
        targetAlignment: RouteAlignment? = resolveEffectiveAlignment(routeData)
    ): ResolvedAlignmentPosition {
        if (targetAlignment == null || targetAlignment.points.size < 2) {
            val nearest = routeData.markers.minByOrNull { abs(it.chainage.metres - originalKmzMetres) }
            return ResolvedAlignmentPosition(
                originalKmzMetres = originalKmzMetres,
                distanceAlongAlignmentMetres = 0.0,
                point = nearest?.point ?: routeData.bounds.center,
                alignmentId = targetAlignment?.id ?: routeData.id,
                alignmentName = targetAlignment?.name ?: routeData.name
            )
        }

        val cached = ChainageMatcher.getOrCreateCachedGeometry(targetAlignment, routeData.markers)
        val mapped = cached.mappedMarkers
        val distAlongM: Double = when {
            mapped.isEmpty() -> 0.0
            mapped.size == 1 -> {
                val single = mapped[0]
                val sign = if (cached.direction == "DECREASING") -1.0 else 1.0
                (single.distanceAlongAlignmentM + sign * (originalKmzMetres - single.chainageMetres))
                    .coerceIn(0.0, cached.totalLengthM)
            }
            else -> {
                val first = mapped.first()
                val last = mapped.last()
                val isDecreasing = first.chainageMetres > last.chainageMetres
                var resolvedDist: Double? = null

                for (i in 0 until mapped.size - 1) {
                    val mA = mapped[i]
                    val mB = mapped[i + 1]
                    val minCh = minOf(mA.chainageMetres, mB.chainageMetres)
                    val maxCh = maxOf(mA.chainageMetres, mB.chainageMetres)
                    if (originalKmzMetres in minCh..maxCh) {
                        val chSpan = (mB.chainageMetres - mA.chainageMetres).toDouble()
                        val frac = if (abs(chSpan) > 0.01) {
                            ((originalKmzMetres - mA.chainageMetres).toDouble() / chSpan).coerceIn(0.0, 1.0)
                        } else 0.0
                        resolvedDist = mA.distanceAlongAlignmentM +
                            frac * (mB.distanceAlongAlignmentM - mA.distanceAlongAlignmentM)
                        break
                    }
                }

                if (resolvedDist != null) {
                    resolvedDist.coerceIn(0.0, cached.totalLengthM)
                } else {
                    val sign = if (isDecreasing) -1.0 else 1.0
                    val beforeStart = if (isDecreasing) {
                        originalKmzMetres > first.chainageMetres
                    } else {
                        originalKmzMetres < first.chainageMetres
                    }
                    if (beforeStart) {
                        (first.distanceAlongAlignmentM + sign * (originalKmzMetres - first.chainageMetres))
                            .coerceIn(0.0, cached.totalLengthM)
                    } else {
                        (last.distanceAlongAlignmentM + sign * (originalKmzMetres - last.chainageMetres))
                            .coerceIn(0.0, cached.totalLengthM)
                    }
                }
            }
        }

        val pt = ChainageMatcher.interpolatePointAtDistanceAlongAlignment(cached, distAlongM)
        return ResolvedAlignmentPosition(
            originalKmzMetres = originalKmzMetres,
            distanceAlongAlignmentMetres = distAlongM,
            point = pt,
            alignmentId = cached.alignmentId,
            alignmentName = cached.alignmentName
        )
    }

    /**
     * Resolves an original KMZ/KML chainage, alignment distance, and parallel centerline offset
     * from a GPS coordinate on [alignment].
     */
    fun resolveAlignmentPositionFromGps(
        routeData: RouteData,
        alignment: RouteAlignment?,
        gpsPoint: GeoPoint,
        gpsAccuracy: Float = 5.0f
    ): ResolvedAlignmentPosition? {
        if (alignment == null || alignment.points.size < 2) return null
        val singleRoute = routeData.copy(
            alignments = listOf(alignment),
            selectedAlignmentId = alignment.id
        )
        val match = ChainageMatcher.match(
            gpsPoint = gpsPoint,
            gpsAccuracy = gpsAccuracy,
            routeData = singleRoute,
            previousResult = null
        )
        val ch = match.rawUncorrectedChainage ?: match.chainage ?: return null
        return ResolvedAlignmentPosition(
            originalKmzMetres = ch.metres,
            distanceAlongAlignmentMetres = match.distanceAlongAlignmentMetres,
            point = match.projectedPoint ?: gpsPoint,
            alignmentId = alignment.id,
            alignmentName = alignment.name,
            parallelOffset = match.parallelCenterlineOffset
        )
    }

    /**
     * Creates a manual reference calibration record bound to [routeData] and [alignment].
     */
    fun createCalibration(
        routeData: RouteData,
        alignment: RouteAlignment?,
        originalKmzMetres: Long,
        manualReferenceMetres: Long,
        anchorDistanceAlongMetres: Double? = null,
        anchorPoint: GeoPoint? = null,
        nowMs: Long = System.currentTimeMillis(),
        gpsAccuracyMetres: Float = 3.5f,
        signedLateralOffsetMetres: Double = 0.0,
        centerlineOffsetZone: CenterlineOffsetZone? = null,
        idOverride: String? = null
    ): KmzManualCalibration {
        val targetAlign = alignment ?: resolveEffectiveAlignment(routeData)
        val resolvedPos = if (anchorDistanceAlongMetres == null || anchorPoint == null) {
            resolveAlignmentPositionForOriginalChainage(routeData, originalKmzMetres, targetAlign)
        } else null
        val effectiveDist = anchorDistanceAlongMetres ?: resolvedPos?.distanceAlongAlignmentMetres ?: 0.0
        val effectivePt = anchorPoint ?: resolvedPos?.point
        val effectiveAlignId = targetAlign?.id?.takeIf { it.isNotBlank() } ?: routeData.id
        val effectiveAlignName = targetAlign?.name?.takeIf { it.isNotBlank() } ?: routeData.name
        val fingerprint = computeAlignmentFingerprint(routeData, targetAlign)
        val resolvedZone = centerlineOffsetZone ?: KmzManualCalibration.resolveZoneFromSignedOffset(signedLateralOffsetMetres)
        val recordId = idOverride?.takeIf { it.isNotBlank() }
            ?: KmzManualCalibration.buildCalibrationId(routeData.id, effectiveAlignId, originalKmzMetres)

        return KmzManualCalibration(
            id = recordId,
            routeId = routeData.id,
            routeFileName = routeData.fileName,
            alignmentId = effectiveAlignId,
            alignmentName = effectiveAlignName,
            alignmentFingerprint = fingerprint,
            originalKmzMetres = originalKmzMetres,
            manualReferenceMetres = manualReferenceMetres,
            anchorDistanceAlongMetres = effectiveDist,
            anchorLatitude = effectivePt?.latitude,
            anchorLongitude = effectivePt?.longitude,
            coverageRadiusMetres = COVERAGE_RADIUS_METRES,
            updatedAtMs = nowMs,
            createdAtMs = nowMs,
            gpsAccuracyAtCalibrationMetres = gpsAccuracyMetres.coerceAtLeast(0.1f),
            signedLateralOffsetMetres = signedLateralOffsetMetres,
            centerlineOffsetZone = resolvedZone
        )
    }

    /**
     * Edits an existing calibration reference:
     * - Updates manual reference chainage and/or GPS reference position (or original KMZ chainage).
     * - Recalculates correction offset automatically.
     * - Revalidates alignment matching, lateral offset, and GPS accuracy.
     * - Preserves previous reference details in [KmzManualCalibration.editHistory].
     */
    fun editCalibration(
        existing: KmzManualCalibration,
        routeData: RouteData,
        alignment: RouteAlignment? = resolveEffectiveAlignment(routeData, existing.alignmentId, existing.alignmentName),
        newManualReferenceMetres: Long,
        newOriginalKmzMetres: Long? = null,
        newGpsPoint: GeoPoint? = null,
        newGpsAccuracyMetres: Float? = null,
        newSignedLateralOffsetMetres: Double? = null,
        nowMs: Long = System.currentTimeMillis()
    ): KmzManualCalibration {
        val targetAlign = alignment ?: resolveEffectiveAlignment(routeData, existing.alignmentId, existing.alignmentName)
        val resolvedFromGps = if (newGpsPoint != null && targetAlign != null) {
            resolveAlignmentPositionFromGps(
                routeData = routeData,
                alignment = targetAlign,
                gpsPoint = newGpsPoint,
                gpsAccuracy = newGpsAccuracyMetres ?: existing.gpsAccuracyAtCalibrationMetres
            )
        } else null

        val effectiveOriginalMetres = newOriginalKmzMetres
            ?: resolvedFromGps?.originalKmzMetres
            ?: existing.originalKmzMetres

        val resolvedPos = resolveAlignmentPositionForOriginalChainage(
            routeData = routeData,
            originalKmzMetres = effectiveOriginalMetres,
            targetAlignment = targetAlign
        )

        val effectiveDistAlong = resolvedFromGps?.distanceAlongAlignmentMetres
            ?.takeIf { newOriginalKmzMetres == null || abs(resolvedFromGps.originalKmzMetres - newOriginalKmzMetres) <= 5L }
            ?: resolvedPos.distanceAlongAlignmentMetres

        val effectiveAnchorLat = newGpsPoint?.latitude ?: existing.anchorLatitude ?: resolvedPos.point.latitude
        val effectiveAnchorLng = newGpsPoint?.longitude ?: existing.anchorLongitude ?: resolvedPos.point.longitude
        val effectiveGpsAcc = (newGpsAccuracyMetres ?: existing.gpsAccuracyAtCalibrationMetres).coerceAtLeast(0.1f)
        val effectiveSignedLateral = newSignedLateralOffsetMetres
            ?: resolvedFromGps?.parallelOffset?.signedLateralOffsetMetres
            ?: existing.signedLateralOffsetMetres
        val effectiveZone = resolvedFromGps?.parallelOffset?.zone
            ?: KmzManualCalibration.resolveZoneFromSignedOffset(effectiveSignedLateral)
        val fingerprint = computeAlignmentFingerprint(routeData, targetAlign)

        val historySnapshot = KmzCalibrationEditHistoryEntry(
            previousManualReferenceMetres = existing.manualReferenceMetres,
            previousOriginalKmzMetres = existing.originalKmzMetres,
            previousOffsetMetres = existing.offsetMetres,
            previousAnchorDistanceAlongMetres = existing.anchorDistanceAlongMetres,
            previousAnchorLatitude = existing.anchorLatitude,
            previousAnchorLongitude = existing.anchorLongitude,
            previousGpsAccuracyMetres = existing.gpsAccuracyAtCalibrationMetres,
            previousSignedLateralOffsetMetres = existing.signedLateralOffsetMetres,
            editedAtMs = nowMs
        )

        return existing.copy(
            routeFileName = routeData.fileName,
            alignmentId = targetAlign?.id?.takeIf { it.isNotBlank() } ?: existing.alignmentId,
            alignmentName = targetAlign?.name?.takeIf { it.isNotBlank() } ?: existing.alignmentName,
            alignmentFingerprint = fingerprint,
            originalKmzMetres = effectiveOriginalMetres,
            manualReferenceMetres = newManualReferenceMetres,
            anchorDistanceAlongMetres = effectiveDistAlong,
            anchorLatitude = effectiveAnchorLat,
            anchorLongitude = effectiveAnchorLng,
            gpsAccuracyAtCalibrationMetres = effectiveGpsAcc,
            signedLateralOffsetMetres = effectiveSignedLateral,
            centerlineOffsetZone = effectiveZone,
            updatedAtMs = nowMs,
            editHistory = existing.editHistory + historySnapshot
        )
    }

    /**
     * Checks if an identical calibration reference already exists for the same route, alignment,
     * original KMZ chainage (within 2 m), and manual reference chainage.
     */
    fun isDuplicateCalibration(
        calibrations: Map<String, KmzManualCalibration>,
        routeId: String,
        alignmentId: String,
        originalKmzMetres: Long,
        manualReferenceMetres: Long,
        excludeCalibrationId: String? = null
    ): Boolean {
        val cleanRoute = routeId.trim()
        val cleanAlign = alignmentId.trim().ifEmpty { cleanRoute }
        return calibrations.values.any { cal ->
            val calAlign = cal.alignmentId.trim().ifEmpty { cal.routeId.trim() }
            cal.id != excludeCalibrationId &&
                cal.routeId.trim() == cleanRoute &&
                calAlign == cleanAlign &&
                abs(cal.originalKmzMetres - originalKmzMetres) <= 2L &&
                cal.manualReferenceMetres == manualReferenceMetres
        }
    }

    /**
     * Lists all calibration references for [routeId] (optionally filtered to [alignmentId]),
     * sorted by most recently updated or by chainage order.
     */
    fun listCalibrationsForRoute(
        calibrations: Map<String, KmzManualCalibration>,
        routeId: String? = null,
        alignmentId: String? = null
    ): List<KmzManualCalibration> {
        return calibrations.values
            .filter { cal ->
                val routeMatches = routeId.isNullOrBlank() || cal.routeId == routeId
                val alignMatches = alignmentId.isNullOrBlank() ||
                    cal.alignmentId == alignmentId ||
                    cal.alignmentId == cal.routeId
                routeMatches && alignMatches
            }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<KmzManualCalibration> { it.updatedAtMs }.thenBy { it.originalKmzMetres })
    }

    /**
     * Evaluates the display status (`Active`, `Inactive`, `Outside Coverage`, or `Invalid`)
     * for a record in the Smart Calibration Chainage List.
     */
    fun evaluateRecordStatus(
        calibration: KmzManualCalibration,
        routeData: RouteData?,
        activeAlignmentId: String? = null,
        currentDistanceAlongMetres: Double? = null,
        activeCalibrationId: String? = null
    ): KmzCalibrationRecordStatus {
        if (!calibration.isEnabled) return KmzCalibrationRecordStatus.INACTIVE
        if (routeData == null || calibration.routeId != routeData.id) {
            return KmzCalibrationRecordStatus.INACTIVE
        }

        val targetAlign = resolveEffectiveAlignment(
            routeData = routeData,
            alignmentId = calibration.alignmentId.takeIf { it.isNotBlank() },
            alignmentName = calibration.alignmentName.takeIf { it.isNotBlank() }
        ) ?: return KmzCalibrationRecordStatus.INVALID

        val currentFingerprint = computeAlignmentFingerprint(routeData, targetAlign)
        if (calibration.alignmentFingerprint.isNotBlank() &&
            calibration.alignmentFingerprint != currentFingerprint
        ) {
            return KmzCalibrationRecordStatus.INVALID
        }

        // Check if original KMZ chainage is grossly outside alignment valid range (> 5km off endpoints)
        val rawMin = routeData.startChainage?.metres ?: routeData.markers.minOfOrNull { it.chainage.metres }
        val rawMax = routeData.endChainage?.metres ?: routeData.markers.maxOfOrNull { it.chainage.metres }
        if (rawMin != null && rawMax != null) {
            val lo = minOf(rawMin, rawMax) - 5_000L
            val hi = maxOf(rawMin, rawMax) + 5_000L
            if (calibration.originalKmzMetres !in lo..hi) {
                return KmzCalibrationRecordStatus.INVALID
            }
        }

        val effectiveActiveAlignId = activeAlignmentId?.takeIf { it.isNotBlank() }
            ?: routeData.selectedAlignmentId?.takeIf { it.isNotBlank() }
            ?: routeData.activeAlignment?.id?.takeIf { it.isNotBlank() }
            ?: routeData.id

        val calAlignId = calibration.alignmentId.ifBlank { calibration.routeId }
        val isOnSelectedAlignment = calAlignId == effectiveActiveAlignId ||
            (routeData.alignments.size <= 1 && calAlignId == routeData.id)
        if (!isOnSelectedAlignment) {
            return KmzCalibrationRecordStatus.INACTIVE
        }

        if (currentDistanceAlongMetres != null && currentDistanceAlongMetres.isFinite()) {
            if (!calibration.isWithinCoverage(currentDistanceAlongMetres)) {
                return KmzCalibrationRecordStatus.OUTSIDE_COVERAGE
            }
            if (activeCalibrationId != null && activeCalibrationId != calibration.id) {
                return KmzCalibrationRecordStatus.INACTIVE
            }
            return KmzCalibrationRecordStatus.ACTIVE
        }

        return if (activeCalibrationId == null || activeCalibrationId == calibration.id) {
            KmzCalibrationRecordStatus.ACTIVE
        } else {
            KmzCalibrationRecordStatus.INACTIVE
        }
    }

    /**
     * Finds the best calibration matching the active route & matched alignment, validating alignment
     * identity and geometry fingerprint so switching KMZ/KML files or alignments never leaks an unrelated offset.
     * When multiple calibration references exist on the same alignment, selects the enabled reference
     * within ±2,000 m coverage closest to [currentDistanceAlongMetres] (or the nearest reference if outside 2 km).
     */
    fun findCalibrationForMatch(
        calibrations: Map<String, KmzManualCalibration>,
        routeData: RouteData?,
        matchedAlignmentId: String = "",
        matchedAlignmentName: String = "",
        currentDistanceAlongMetres: Double? = null
    ): Pair<KmzManualCalibration?, KmzCalibrationCoverageStatus?> {
        if (routeData == null || calibrations.isEmpty()) return null to null

        val targetAlign = resolveEffectiveAlignment(
            routeData = routeData,
            alignmentId = matchedAlignmentId.takeIf { it.isNotBlank() },
            alignmentName = matchedAlignmentName.takeIf { it.isNotBlank() }
        )
        val effectiveAlignId = targetAlign?.id?.takeIf { it.isNotBlank() }
            ?: matchedAlignmentId.takeIf { it.isNotBlank() }
            ?: routeData.id

        val candidates = calibrations.values.filter { cal ->
            cal.isEnabled && cal.routeId == routeData.id && (
                cal.alignmentId == effectiveAlignId ||
                    (matchedAlignmentName.isNotBlank() && cal.alignmentName == matchedAlignmentName) ||
                    (routeData.alignments.size <= 1 && (cal.alignmentId == routeData.id || cal.alignmentId.isBlank()))
                )
        }.distinctBy { it.id }

        val candidate = when {
            candidates.isEmpty() -> {
                val exactKey = storageKey(routeData.id, effectiveAlignId)
                calibrations[exactKey]?.takeIf { it.isEnabled }
            }
            candidates.size == 1 -> candidates.first()
            currentDistanceAlongMetres != null && currentDistanceAlongMetres.isFinite() -> {
                val withinCoverage = candidates.filter { it.isWithinCoverage(currentDistanceAlongMetres) }
                if (withinCoverage.isNotEmpty()) {
                    withinCoverage.minWithOrNull(
                        compareBy<KmzManualCalibration> { abs(currentDistanceAlongMetres - it.anchorDistanceAlongMetres) }
                            .thenByDescending { it.updatedAtMs }
                    )
                } else {
                    candidates.minWithOrNull(
                        compareBy<KmzManualCalibration> { abs(currentDistanceAlongMetres - it.anchorDistanceAlongMetres) }
                            .thenByDescending { it.updatedAtMs }
                    )
                }
            }
            else -> candidates.maxByOrNull { it.updatedAtMs }
        } ?: return null to null

        val currentFingerprint = computeAlignmentFingerprint(routeData, targetAlign)
        if (candidate.alignmentFingerprint.isNotBlank() &&
            candidate.alignmentFingerprint != currentFingerprint
        ) {
            return candidate to KmzCalibrationCoverageStatus.ALIGNMENT_MISMATCH
        }
        return candidate to null
    }

    /**
     * Evaluates and applies [calibration] to [rawChainage] at [distanceAlongAlignmentMetres]
     * using integer millimetre fixed-point representation.
     * Guarantees:
     * - Offset is applied ONCE: Corrected = Original + Offset.
     * - Offset applies ONLY within ±2,000 m along the selected road alignment.
     * - Outside ±2,000 m, returns [rawChainage] untouched with OUTSIDE_2KM_RANGE status.
     */
    fun evaluateAndApply(
        rawChainage: Chainage,
        distanceAlongAlignmentMetres: Double,
        routeData: RouteData,
        calibrations: Map<String, KmzManualCalibration>,
        matchedAlignmentId: String = "",
        matchedAlignmentName: String = "",
        rawChainageMillimetres: Long = rawChainage.totalMillimetres
    ): KmzCalibrationApplicationResult {
        val (calibration, mismatchStatus) = findCalibrationForMatch(
            calibrations = calibrations,
            routeData = routeData,
            matchedAlignmentId = matchedAlignmentId,
            matchedAlignmentName = matchedAlignmentName,
            currentDistanceAlongMetres = distanceAlongAlignmentMetres
        )

        if (calibration == null) {
            return KmzCalibrationApplicationResult(
                status = KmzCalibrationCoverageStatus.NOT_CALIBRATED,
                outputChainage = rawChainage,
                rawKmzChainage = rawChainage,
                appliedOffsetMetres = 0L,
                distanceFromAnchorMetres = null,
                calibration = null,
                statusMessage = KmzCalibrationCoverageStatus.NOT_CALIBRATED.detailLabel,
                outputChainageMillimetres = rawChainageMillimetres,
                rawKmzChainageMillimetres = rawChainageMillimetres
            )
        }

        if (mismatchStatus == KmzCalibrationCoverageStatus.ALIGNMENT_MISMATCH) {
            return KmzCalibrationApplicationResult(
                status = KmzCalibrationCoverageStatus.ALIGNMENT_MISMATCH,
                outputChainage = rawChainage,
                rawKmzChainage = rawChainage,
                appliedOffsetMetres = 0L,
                distanceFromAnchorMetres = null,
                calibration = calibration,
                statusMessage = KmzCalibrationCoverageStatus.ALIGNMENT_MISMATCH.detailLabel,
                outputChainageMillimetres = rawChainageMillimetres,
                rawKmzChainageMillimetres = rawChainageMillimetres
            )
        }

        val distFromAnchor = abs(distanceAlongAlignmentMetres - calibration.anchorDistanceAlongMetres)
        val withinRange = calibration.isWithinCoverage(distanceAlongAlignmentMetres)

        return if (withinRange) {
            val correctedMillimetres = rawChainageMillimetres + calibration.offsetMillimetres
            val correctedMetres = rawChainage.metres + calibration.offsetMetres
            val corrected = Chainage(correctedMetres)
            KmzCalibrationApplicationResult(
                status = KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE,
                outputChainage = corrected,
                rawKmzChainage = rawChainage,
                appliedOffsetMetres = calibration.offsetMetres,
                distanceFromAnchorMetres = distFromAnchor,
                calibration = calibration,
                statusMessage = "Calibrated ${calibration.formattedOffset} (${distFromAnchor.roundToLong()} m / 2,000 m)",
                outputChainageMillimetres = correctedMillimetres,
                rawKmzChainageMillimetres = rawChainageMillimetres
            )
        } else {
            KmzCalibrationApplicationResult(
                status = KmzCalibrationCoverageStatus.OUTSIDE_2KM_RANGE,
                outputChainage = rawChainage,
                rawKmzChainage = rawChainage,
                appliedOffsetMetres = 0L,
                distanceFromAnchorMetres = distFromAnchor,
                calibration = calibration,
                statusMessage = "Outside 2 km calibration range (${distFromAnchor.roundToLong()} m) — using original KMZ",
                outputChainageMillimetres = rawChainageMillimetres,
                rawKmzChainageMillimetres = rawChainageMillimetres
            )
        }
    }

    /**
     * Evaluates the Smart Calibration Suggestion Engine for the current GPS fix and alignment match.
     *
     * Suggestion conditions (ALL 7 must hold):
     * 1. GPS horizontal accuracy is ±5 m or better (<= 5.0 m).
     * 2. GPS position matches the correct KMZ/KML alignment.
     * 3. Absolute perpendicular centerline offset is no greater than 20 m (<= 20.0 m).
     * 4. Multiple reliable GPS samples confirm a stable position ([stableSampleCount] >= [requiredStableSamples]).
     * 5. The correct carriageway and alignment are identified.
     * 6. No conflicting reference exists nearby.
     * 7. The proposed reference position is within the alignment's valid chainage range.
     *
     * Note: The 20 m threshold is strictly a suggestion eligibility limit. Lateral offset is NEVER
     * added to or subtracted from chainage, and suggestions are NEVER auto-applied.
     */
    fun evaluateCalibrationSuggestion(
        routeData: RouteData?,
        matchResult: ChainageMatchResult,
        gpsPoint: GeoPoint?,
        gpsAccuracyMetres: Float,
        calibrations: Map<String, KmzManualCalibration> = emptyMap(),
        stableSampleCount: Int = DEFAULT_REQUIRED_STABLE_SAMPLES,
        requiredStableSamples: Int = DEFAULT_REQUIRED_STABLE_SAMPLES
    ): KmzCalibrationSuggestion {
        val rawChainage = matchResult.rawUncorrectedChainage ?: matchResult.chainage
        val parallelOffset = matchResult.parallelCenterlineOffset ?: run {
            val signed = matchResult.signedLateralOffsetMetres
            val perp = matchResult.offsetMetres
            val zone = if (matchResult.confidence == MatchConfidence.NO_MATCH || gpsPoint == null) {
                CenterlineOffsetZone.OUTSIDE
            } else if (perp <= ParallelCenterlineOffset.ON_CENTERLINE_THRESHOLD_METRES) {
                CenterlineOffsetZone.ON_LINE
            } else if (signed >= 0.0) {
                CenterlineOffsetZone.LEFT
            } else {
                CenterlineOffsetZone.RIGHT
            }
            ParallelCenterlineOffset(
                zone = zone,
                perpendicularDistanceMetres = perp,
                signedLateralOffsetMetres = signed,
                localTangentBearingDegrees = matchResult.localTangentBearingDegrees,
                projectedPoint = matchResult.projectedPoint,
                gpsPoint = gpsPoint,
                matchedAlignmentId = matchResult.matchedAlignmentId,
                matchedAlignmentName = matchResult.matchedAlignmentName
            )
        }

        val targetAlign = routeData?.let {
            resolveEffectiveAlignment(
                routeData = it,
                alignmentId = matchResult.matchedAlignmentId.takeIf { id -> id.isNotBlank() },
                alignmentName = matchResult.matchedAlignmentName.takeIf { name -> name.isNotBlank() }
            )
        }
        val alignId = targetAlign?.id ?: matchResult.matchedAlignmentId
        val alignName = targetAlign?.name?.takeIf { it.isNotBlank() }
            ?: matchResult.matchedAlignmentName.ifBlank { routeData?.name.orEmpty() }

        val accText = if (gpsAccuracyMetres > 0f && gpsAccuracyMetres.isFinite()) {
            String.format(Locale.US, "GPS ±%.0f m", gpsAccuracyMetres)
        } else {
            "GPS --"
        }
        val offsetTelemetry = when (parallelOffset.zone) {
            CenterlineOffsetZone.LEFT -> String.format(Locale.US, "Left offset %.1f m (%s)", parallelOffset.perpendicularDistanceMetres, parallelOffset.formattedBadge)
            CenterlineOffsetZone.RIGHT -> String.format(Locale.US, "Right centerline offset %.1f m (%s)", parallelOffset.perpendicularDistanceMetres, parallelOffset.formattedBadge)
            CenterlineOffsetZone.ON_LINE -> "Near centerline (ON LINE)"
            CenterlineOffsetZone.OUTSIDE -> "Outside matching range (OUTSIDE)"
        }
        val telemetryLine = "$accText · $offsetTelemetry"

        // Condition 2 & 5: Valid route, GPS fix, and alignment match
        if (routeData == null || gpsPoint == null || rawChainage == null ||
            matchResult.confidence == MatchConfidence.NO_MATCH ||
            parallelOffset.zone == CenterlineOffsetZone.OUTSIDE ||
            alignId.isBlank()
        ) {
            return KmzCalibrationSuggestion(
                status = KmzCalibrationSuggestionStatus.ALIGNMENT_OR_GPS_UNAVAILABLE,
                originalKmzChainage = null,
                gpsAccuracyMetres = gpsAccuracyMetres,
                parallelOffset = parallelOffset,
                matchedAlignmentId = alignId,
                matchedAlignmentName = alignName,
                stableSampleCount = stableSampleCount,
                requiredStableSamples = requiredStableSamples,
                telemetryLine = telemetryLine,
                guidanceMessage = "Waiting for valid GPS match on the selected KMZ/KML alignment."
            )
        }

        // Condition 1: GPS horizontal accuracy must be ±5 m or better
        if (gpsAccuracyMetres <= 0f || !gpsAccuracyMetres.isFinite() ||
            gpsAccuracyMetres > MAX_SUGGESTION_GPS_ACCURACY_METRES
        ) {
            return KmzCalibrationSuggestion(
                status = KmzCalibrationSuggestionStatus.WAIT_POOR_GPS_ACCURACY,
                originalKmzChainage = rawChainage,
                gpsAccuracyMetres = gpsAccuracyMetres,
                parallelOffset = parallelOffset,
                matchedAlignmentId = alignId,
                matchedAlignmentName = alignName,
                stableSampleCount = stableSampleCount,
                requiredStableSamples = requiredStableSamples,
                telemetryLine = telemetryLine,
                guidanceMessage = "Do not suggest calibration until GPS accuracy improves (±5 m or better required)."
            )
        }

        // Condition 3: Absolute perpendicular centerline offset must be <= 20 m
        val absLateralOffset = abs(parallelOffset.perpendicularDistanceMetres)
        if (absLateralOffset > MAX_SUGGESTION_LATERAL_OFFSET_METRES) {
            return KmzCalibrationSuggestion(
                status = KmzCalibrationSuggestionStatus.RECHECK_EXCESSIVE_LATERAL_OFFSET,
                originalKmzChainage = rawChainage,
                gpsAccuracyMetres = gpsAccuracyMetres,
                parallelOffset = parallelOffset,
                matchedAlignmentId = alignId,
                matchedAlignmentName = alignName,
                stableSampleCount = stableSampleCount,
                requiredStableSamples = requiredStableSamples,
                telemetryLine = telemetryLine,
                guidanceMessage = "Do not suggest calibration. Recheck centerline and carriageway matching (max 20 m lateral offset)."
            )
        }

        // Condition 7: Proposed reference position must be within the alignment's valid chainage range
        val startCh = routeData.startChainage?.metres ?: routeData.markers.minOfOrNull { it.chainage.metres }
        val endCh = routeData.endChainage?.metres ?: routeData.markers.maxOfOrNull { it.chainage.metres }
        val isExtrapolatedOut = matchResult.sourceKmzReferenceStatus.startsWith("Extrapolated", ignoreCase = true)
        val isOutOfChainageBounds = if (startCh != null && endCh != null) {
            val minCh = minOf(startCh, endCh)
            val maxCh = maxOf(startCh, endCh)
            rawChainage.metres !in minCh..maxCh
        } else false

        if (isExtrapolatedOut || isOutOfChainageBounds) {
            return KmzCalibrationSuggestion(
                status = KmzCalibrationSuggestionStatus.OUT_OF_ALIGNMENT_RANGE,
                originalKmzChainage = rawChainage,
                gpsAccuracyMetres = gpsAccuracyMetres,
                parallelOffset = parallelOffset,
                matchedAlignmentId = alignId,
                matchedAlignmentName = alignName,
                stableSampleCount = stableSampleCount,
                requiredStableSamples = requiredStableSamples,
                telemetryLine = telemetryLine,
                guidanceMessage = "Position is outside the alignment's valid chainage range."
            )
        }

        // Condition 6: Check if a conflicting reference already exists nearby on the same alignment
        val existingOnAlign = listCalibrationsForRoute(calibrations, routeData.id, alignId)
            .filter { it.isEnabled }
        val currentFingerprint = computeAlignmentFingerprint(routeData, targetAlign)
        val hasNearbyConflict = existingOnAlign.any { cal ->
            val fingerprintValid = cal.alignmentFingerprint.isBlank() || cal.alignmentFingerprint == currentFingerprint
            fingerprintValid && (
                abs(matchResult.distanceAlongAlignmentMetres - cal.anchorDistanceAlongMetres) <= CONFLICTING_REFERENCE_PROXIMITY_METRES ||
                    abs(rawChainage.metres - cal.originalKmzMetres) <= CONFLICTING_REFERENCE_PROXIMITY_METRES.roundToLong()
                )
        }
        if (hasNearbyConflict) {
            return KmzCalibrationSuggestion(
                status = KmzCalibrationSuggestionStatus.CONFLICTING_REFERENCE_NEARBY,
                originalKmzChainage = rawChainage,
                gpsAccuracyMetres = gpsAccuracyMetres,
                parallelOffset = parallelOffset,
                matchedAlignmentId = alignId,
                matchedAlignmentName = alignName,
                stableSampleCount = stableSampleCount,
                requiredStableSamples = requiredStableSamples,
                telemetryLine = telemetryLine,
                guidanceMessage = "A verified calibration reference already exists nearby on this alignment."
            )
        }

        // Condition 4: Multiple reliable GPS samples confirm a stable position (no jump uncertainty)
        if (matchResult.isUncertain || stableSampleCount < requiredStableSamples) {
            return KmzCalibrationSuggestion(
                status = KmzCalibrationSuggestionStatus.WAIT_STABILIZING_SAMPLES,
                originalKmzChainage = rawChainage,
                gpsAccuracyMetres = gpsAccuracyMetres,
                parallelOffset = parallelOffset,
                matchedAlignmentId = alignId,
                matchedAlignmentName = alignName,
                stableSampleCount = stableSampleCount,
                requiredStableSamples = requiredStableSamples,
                telemetryLine = telemetryLine,
                guidanceMessage = "Confirming stable GPS position ($stableSampleCount/$requiredStableSamples reliable samples)."
            )
        }

        return KmzCalibrationSuggestion(
            status = KmzCalibrationSuggestionStatus.ELIGIBLE_SUGGEST_CALIBRATION,
            originalKmzChainage = rawChainage,
            gpsAccuracyMetres = gpsAccuracyMetres,
            parallelOffset = parallelOffset,
            matchedAlignmentId = alignId,
            matchedAlignmentName = alignName,
            stableSampleCount = stableSampleCount,
            requiredStableSamples = requiredStableSamples,
            telemetryLine = telemetryLine,
            guidanceMessage = "Suggest entering a verified KM stone or known site chainage."
        )
    }

    /**
     * Resolves the alignment chainage min/max bounds taking into account that [calibration]
     * only shifts chainages within ±2,000 m of its anchor along the alignment.
     */
    fun computeCalibratedRouteRange(
        routeData: RouteData,
        calibration: KmzManualCalibration?,
        targetAlignment: RouteAlignment? = resolveEffectiveAlignment(routeData)
    ): Pair<Long, Long> {
        val rawMin = routeData.startChainage?.metres
            ?: routeData.markers.minOfOrNull { it.chainage.metres }
            ?: 0L
        val rawMax = routeData.endChainage?.metres
            ?: routeData.markers.maxOfOrNull { it.chainage.metres }
            ?: rawMin
        val baseMin = minOf(rawMin, rawMax)
        val baseMax = maxOf(rawMin, rawMax)

        if (calibration == null || calibration.routeId != routeData.id) {
            return baseMin to baseMax
        }
        if (targetAlignment != null &&
            calibration.alignmentId.isNotBlank() &&
            calibration.alignmentId != targetAlignIdOrRoute(targetAlignment, routeData)
        ) {
            return baseMin to baseMax
        }

        val cached = if (targetAlignment != null && targetAlignment.points.size >= 2) {
            ChainageMatcher.getOrCreateCachedGeometry(targetAlignment, routeData.markers)
        } else null

        val calMinCandidate = calibration.manualReferenceMetres - calibration.coverageRadiusMetres.roundToLong()
        val calMaxCandidate = calibration.manualReferenceMetres + calibration.coverageRadiusMetres.roundToLong()
        if (cached == null || cached.mappedMarkers.isEmpty()) {
            return minOf(baseMin, calMinCandidate) to maxOf(baseMax, calMaxCandidate)
        }

        val anchorDist = calibration.anchorDistanceAlongMetres
        val startCovDist = (anchorDist - calibration.coverageRadiusMetres).coerceIn(0.0, cached.totalLengthM)
        val endCovDist = (anchorDist + calibration.coverageRadiusMetres).coerceIn(0.0, cached.totalLengthM)
        val startRaw = interpolateOriginalMetresAtDistance(cached, startCovDist)
        val endRaw = interpolateOriginalMetresAtDistance(cached, endCovDist)
        val calMin = minOf(startRaw, endRaw) + calibration.offsetMetres
        val calMax = maxOf(startRaw, endRaw) + calibration.offsetMetres

        return minOf(baseMin, calMin) to maxOf(baseMax, calMax)
    }

    /**
     * Inverse lookup for navigation: given a target calibrated chainage, returns
     * `(distanceAlongAlignmentMetres, originalKmzMetresAtThatPosition)`.
     */
    fun resolveAlignmentDistanceForCalibratedChainage(
        targetCalibratedMetres: Long,
        routeData: RouteData,
        calibration: KmzManualCalibration?,
        targetAlignment: RouteAlignment? = resolveEffectiveAlignment(routeData)
    ): Pair<Double, Long> {
        if (calibration != null && calibration.routeId == routeData.id &&
            (targetAlignment == null || calibration.alignmentId.isBlank() ||
                calibration.alignmentId == targetAlignIdOrRoute(targetAlignment, routeData))
        ) {
            val candidateRaw = targetCalibratedMetres - calibration.offsetMetres
            val candidatePos = resolveAlignmentPositionForOriginalChainage(
                routeData = routeData,
                originalKmzMetres = candidateRaw,
                targetAlignment = targetAlignment
            )
            if (calibration.isWithinCoverage(candidatePos.distanceAlongAlignmentMetres)) {
                return candidatePos.distanceAlongAlignmentMetres to candidateRaw
            }
        }

        val rawPos = resolveAlignmentPositionForOriginalChainage(
            routeData = routeData,
            originalKmzMetres = targetCalibratedMetres,
            targetAlignment = targetAlignment
        )
        return rawPos.distanceAlongAlignmentMetres to targetCalibratedMetres
    }

    private fun targetAlignIdOrRoute(alignment: RouteAlignment, routeData: RouteData): String =
        alignment.id.takeIf { it.isNotBlank() } ?: routeData.id

    fun interpolateOriginalMetresAtDistance(
        cached: ChainageMatcher.CachedAlignmentGeometry,
        distAlongM: Double
    ): Long {
        val mapped = cached.mappedMarkers
        if (mapped.isEmpty()) return 0L
        if (mapped.size == 1) {
            val single = mapped[0]
            val sign = if (cached.direction == "DECREASING") -1 else 1
            return (single.chainageMetres + sign * (distAlongM - single.distanceAlongAlignmentM)).roundToLong()
        }
        val first = mapped.first()
        val last = mapped.last()
        val sign = if (first.chainageMetres <= last.chainageMetres) 1 else -1
        if (distAlongM <= first.distanceAlongAlignmentM) {
            return (first.chainageMetres + sign * (distAlongM - first.distanceAlongAlignmentM)).roundToLong()
        }
        if (distAlongM >= last.distanceAlongAlignmentM) {
            return (last.chainageMetres + sign * (distAlongM - last.distanceAlongAlignmentM)).roundToLong()
        }
        for (i in 0 until mapped.size - 1) {
            val mA = mapped[i]
            val mB = mapped[i + 1]
            if (distAlongM in mA.distanceAlongAlignmentM..mB.distanceAlongAlignmentM) {
                val span = mB.distanceAlongAlignmentM - mA.distanceAlongAlignmentM
                val frac = if (span > 0.01) ((distAlongM - mA.distanceAlongAlignmentM) / span).coerceIn(0.0, 1.0) else 0.0
                return (mA.chainageMetres + frac * (mB.chainageMetres - mA.chainageMetres)).roundToLong()
            }
        }
        return last.chainageMetres
    }
}
