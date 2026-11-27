package com.example.geo

import com.example.model.Chainage
import com.example.model.GeoPoint
import com.example.model.KmzCalibrationApplicationResult
import com.example.model.KmzCalibrationCoverageStatus
import com.example.model.KmzManualCalibration
import com.example.model.RouteAlignment
import com.example.model.RouteData
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Simple, deterministic Manual Reference Calibration Engine for KMZ/KML alignments.
 *
 * Rules:
 * 1. One manual reference point per (routeId, alignmentId) replaces any previous calibration.
 * 2. Offset = Manual Reference Chainage - Original KMZ/KML Chainage at the reference position.
 * 3. Corrected Chainage = Original KMZ/KML Chainage + Offset (applied once, never cumulatively).
 * 4. Offset applies ONLY within ±2,000 m along the selected road alignment from the reference anchor.
 * 5. Outside ±2,000 m, original KMZ/KML chainage is returned untouched with clear coverage status.
 */
object KmzCalibrationEngine {

    const val COVERAGE_RADIUS_METRES = KmzManualCalibration.DEFAULT_COVERAGE_RADIUS_METRES
    const val COVERAGE_BOUNDARY_EPSILON_METRES = KmzManualCalibration.COVERAGE_BOUNDARY_EPSILON_METRES

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
        val alignmentName: String
    )

    fun storageKey(routeId: String, alignmentId: String?): String {
        val cleanRoute = routeId.trim()
        val cleanAlign = alignmentId?.trim().takeIf { !it.isNullOrEmpty() } ?: cleanRoute
        return "$cleanRoute::$cleanAlign"
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
     * Resolves an original KMZ/KML chainage and alignment distance from a GPS coordinate on [alignment].
     */
    fun resolveAlignmentPositionFromGps(
        routeData: RouteData,
        alignment: RouteAlignment?,
        gpsPoint: GeoPoint
    ): ResolvedAlignmentPosition? {
        if (alignment == null || alignment.points.size < 2) return null
        val singleRoute = routeData.copy(
            alignments = listOf(alignment),
            selectedAlignmentId = alignment.id
        )
        val match = ChainageMatcher.match(
            gpsPoint = gpsPoint,
            gpsAccuracy = 5.0f,
            routeData = singleRoute,
            previousResult = null
        )
        val ch = match.rawUncorrectedChainage ?: match.chainage ?: return null
        return ResolvedAlignmentPosition(
            originalKmzMetres = ch.metres,
            distanceAlongAlignmentMetres = match.distanceAlongAlignmentMetres,
            point = match.projectedPoint ?: gpsPoint,
            alignmentId = alignment.id,
            alignmentName = alignment.name
        )
    }

    /**
     * Creates a single manual reference calibration replacing any previous calibration for this alignment.
     */
    fun createCalibration(
        routeData: RouteData,
        alignment: RouteAlignment?,
        originalKmzMetres: Long,
        manualReferenceMetres: Long,
        anchorDistanceAlongMetres: Double? = null,
        anchorPoint: GeoPoint? = null,
        nowMs: Long = System.currentTimeMillis()
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

        return KmzManualCalibration(
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
            updatedAtMs = nowMs
        )
    }

    /**
     * Finds the calibration matching the active route & matched alignment, validating alignment identity
     * and geometry fingerprint so switching KMZ/KML files or alignments never leaks an unrelated offset.
     */
    fun findCalibrationForMatch(
        calibrations: Map<String, KmzManualCalibration>,
        routeData: RouteData?,
        matchedAlignmentId: String = "",
        matchedAlignmentName: String = ""
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

        val exactKey = storageKey(routeData.id, effectiveAlignId)
        val candidate = calibrations[exactKey]
            ?: calibrations.values.firstOrNull { cal ->
                cal.routeId == routeData.id && (
                    cal.alignmentId == effectiveAlignId ||
                        (matchedAlignmentName.isNotBlank() && cal.alignmentName == matchedAlignmentName) ||
                        (routeData.alignments.size <= 1 && cal.alignmentId == routeData.id)
                    )
            }
            ?: return null to null

        val currentFingerprint = computeAlignmentFingerprint(routeData, targetAlign)
        if (candidate.alignmentFingerprint.isNotBlank() &&
            candidate.alignmentFingerprint != currentFingerprint
        ) {
            return candidate to KmzCalibrationCoverageStatus.ALIGNMENT_MISMATCH
        }
        return candidate to null
    }

    /**
     * Evaluates and applies [calibration] to [rawChainage] at [distanceAlongAlignmentMetres].
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
        matchedAlignmentName: String = ""
    ): KmzCalibrationApplicationResult {
        val (calibration, mismatchStatus) = findCalibrationForMatch(
            calibrations = calibrations,
            routeData = routeData,
            matchedAlignmentId = matchedAlignmentId,
            matchedAlignmentName = matchedAlignmentName
        )

        if (calibration == null) {
            return KmzCalibrationApplicationResult(
                status = KmzCalibrationCoverageStatus.NOT_CALIBRATED,
                outputChainage = rawChainage,
                rawKmzChainage = rawChainage,
                appliedOffsetMetres = 0L,
                distanceFromAnchorMetres = null,
                calibration = null,
                statusMessage = KmzCalibrationCoverageStatus.NOT_CALIBRATED.detailLabel
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
                statusMessage = KmzCalibrationCoverageStatus.ALIGNMENT_MISMATCH.detailLabel
            )
        }

        val distFromAnchor = abs(distanceAlongAlignmentMetres - calibration.anchorDistanceAlongMetres)
        val withinRange = calibration.isWithinCoverage(distanceAlongAlignmentMetres)

        return if (withinRange) {
            val correctedMetres = rawChainage.metres + calibration.offsetMetres
            val corrected = Chainage(correctedMetres)
            KmzCalibrationApplicationResult(
                status = KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE,
                outputChainage = corrected,
                rawKmzChainage = rawChainage,
                appliedOffsetMetres = calibration.offsetMetres,
                distanceFromAnchorMetres = distFromAnchor,
                calibration = calibration,
                statusMessage = "Calibrated ${calibration.formattedOffset} (${distFromAnchor.roundToLong()} m / 2,000 m)"
            )
        } else {
            KmzCalibrationApplicationResult(
                status = KmzCalibrationCoverageStatus.OUTSIDE_2KM_RANGE,
                outputChainage = rawChainage,
                rawKmzChainage = rawChainage,
                appliedOffsetMetres = 0L,
                distanceFromAnchorMetres = distFromAnchor,
                calibration = calibration,
                statusMessage = "Outside 2 km calibration range (${distFromAnchor.roundToLong()} m) — using original KMZ"
            )
        }
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

    private fun interpolateOriginalMetresAtDistance(
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
