package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class KmzCalibrationCoverageStatus(val shortLabel: String, val detailLabel: String) {
    NOT_CALIBRATED("No calibration", "Using original KMZ/KML chainage"),
    ACTIVE_WITHIN_RANGE("Active • within 2 km coverage", "Calibration offset applied within ±2,000 m along alignment"),
    OUTSIDE_2KM_RANGE("Outside 2 km range", "Beyond ±2,000 m from reference — using original KMZ/KML chainage"),
    ALIGNMENT_MISMATCH("Alignment changed", "Active KMZ/KML alignment changed — recalibrate or reset"),
    GPS_UNAVAILABLE("GPS unavailable", "Waiting for valid GPS location on alignment")
}

/**
 * Classification of perpendicular GPS position relative to the selected KMZ/KML road centerline's
 * local alignment direction.
 */
enum class CenterlineOffsetZone(
    val positionLabel: String,
    val shortBadgeLabel: String
) {
    LEFT("Left of centerline", "LEFT"),
    RIGHT("Right of centerline", "RIGHT"),
    ON_LINE("Near centerline", "ON LINE"),
    OUTSIDE("Outside matching range", "OUTSIDE")
}

/**
 * Perpendicular GPS lateral offset relative to the selected KMZ/KML road centerline.
 *
 * Sign convention:
 * - [signedLateralOffsetMetres] > 0.0 -> LEFT of local alignment direction
 * - [signedLateralOffsetMetres] < 0.0 -> RIGHT of local alignment direction
 * - [signedLateralOffsetMetres] == 0.0 -> ON LINE or OUTSIDE
 */
data class ParallelCenterlineOffset(
    val zone: CenterlineOffsetZone,
    val perpendicularDistanceMetres: Double,
    val signedLateralOffsetMetres: Double,
    val localTangentBearingDegrees: Double = 0.0,
    val projectedPoint: GeoPoint? = null,
    val gpsPoint: GeoPoint? = null,
    val matchedAlignmentId: String = "",
    val matchedAlignmentName: String = ""
) {
    val isLeft: Boolean get() = zone == CenterlineOffsetZone.LEFT
    val isRight: Boolean get() = zone == CenterlineOffsetZone.RIGHT
    val isOnCenterline: Boolean get() = zone == CenterlineOffsetZone.ON_LINE
    val isOutsideMatchingRange: Boolean get() = zone == CenterlineOffsetZone.OUTSIDE

    /**
     * Formats lateral offset exactly as required:
     * - Left of centerline -> "L 4.2 m"
     * - Right of centerline -> "R 6.5 m"
     * - Near centerline -> "ON LINE"
     * - Outside matching range -> "OUTSIDE"
     */
    val formattedBadge: String
        get() = formatLateralOffsetBadge(zone, perpendicularDistanceMetres)

    val formattedPositionLabel: String
        get() = zone.positionLabel

    val formattedSummary: String
        get() = "${zone.positionLabel} • $formattedBadge"

    companion object {
        const val ON_CENTERLINE_THRESHOLD_METRES = 0.5
        const val MAX_RELIABLE_MATCH_RANGE_METRES = 100.0

        fun formatLateralOffsetBadge(
            zone: CenterlineOffsetZone,
            perpendicularDistanceMetres: Double
        ): String {
            val cleanDist = if (perpendicularDistanceMetres.isFinite() && perpendicularDistanceMetres >= 0.0) {
                perpendicularDistanceMetres
            } else 0.0
            return when (zone) {
                CenterlineOffsetZone.LEFT -> String.format(Locale.US, "L %.1f m", cleanDist)
                CenterlineOffsetZone.RIGHT -> String.format(Locale.US, "R %.1f m", cleanDist)
                CenterlineOffsetZone.ON_LINE -> "ON LINE"
                CenterlineOffsetZone.OUTSIDE -> "OUTSIDE"
            }
        }

        fun formatSignedLateralOffset(
            signedOffsetMetres: Double,
            zoneOverride: CenterlineOffsetZone? = null
        ): String {
            if (!signedOffsetMetres.isFinite()) return "OUTSIDE"
            val absDist = abs(signedOffsetMetres)
            val resolvedZone = zoneOverride ?: when {
                absDist > MAX_RELIABLE_MATCH_RANGE_METRES -> CenterlineOffsetZone.OUTSIDE
                absDist <= ON_CENTERLINE_THRESHOLD_METRES -> CenterlineOffsetZone.ON_LINE
                signedOffsetMetres > 0.0 -> CenterlineOffsetZone.LEFT
                else -> CenterlineOffsetZone.RIGHT
            }
            return formatLateralOffsetBadge(resolvedZone, absDist)
        }

        fun outside(
            gpsPoint: GeoPoint? = null,
            perpendicularDistanceMetres: Double = Double.POSITIVE_INFINITY
        ) = ParallelCenterlineOffset(
            zone = CenterlineOffsetZone.OUTSIDE,
            perpendicularDistanceMetres = if (perpendicularDistanceMetres.isFinite()) perpendicularDistanceMetres else 0.0,
            signedLateralOffsetMetres = 0.0,
            localTangentBearingDegrees = 0.0,
            projectedPoint = null,
            gpsPoint = gpsPoint
        )
    }
}

/**
 * Status of an individual calibration reference record in the Smart Calibration Chainage List.
 */
enum class KmzCalibrationRecordStatus(val label: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    OUTSIDE_COVERAGE("Outside Coverage"),
    INVALID("Invalid")
}

/**
 * Preserved snapshot of a calibration reference before an Edit operation.
 */
data class KmzCalibrationEditHistoryEntry(
    val previousManualReferenceMetres: Long,
    val previousOriginalKmzMetres: Long,
    val previousOffsetMetres: Long,
    val previousAnchorDistanceAlongMetres: Double,
    val previousAnchorLatitude: Double? = null,
    val previousAnchorLongitude: Double? = null,
    val previousGpsAccuracyMetres: Float = 3.5f,
    val previousSignedLateralOffsetMetres: Double = 0.0,
    val editedAtMs: Long = System.currentTimeMillis()
) {
    val previousManualReferenceChainage: Chainage
        get() = Chainage(previousManualReferenceMetres)

    val previousOriginalKmzChainage: Chainage
        get() = Chainage(previousOriginalKmzMetres)

    val formattedPreviousOffset: String
        get() = if (previousOffsetMetres >= 0L) "+${previousOffsetMetres} m" else "${previousOffsetMetres} m"

    fun toJson(): JSONObject = JSONObject().apply {
        put("previousManualReferenceMetres", previousManualReferenceMetres)
        put("previousOriginalKmzMetres", previousOriginalKmzMetres)
        put("previousOffsetMetres", previousOffsetMetres)
        put("previousAnchorDistanceAlongMetres", previousAnchorDistanceAlongMetres)
        if (previousAnchorLatitude != null) put("previousAnchorLatitude", previousAnchorLatitude)
        if (previousAnchorLongitude != null) put("previousAnchorLongitude", previousAnchorLongitude)
        put("previousGpsAccuracyMetres", previousGpsAccuracyMetres.toDouble())
        put("previousSignedLateralOffsetMetres", previousSignedLateralOffsetMetres)
        put("editedAtMs", editedAtMs)
    }

    companion object {
        fun fromJson(obj: JSONObject): KmzCalibrationEditHistoryEntry? {
            if (!obj.has("previousManualReferenceMetres") || !obj.has("previousOriginalKmzMetres")) return null
            val lat = if (obj.has("previousAnchorLatitude") && !obj.isNull("previousAnchorLatitude")) {
                obj.optDouble("previousAnchorLatitude").takeIf { it.isFinite() }
            } else null
            val lng = if (obj.has("previousAnchorLongitude") && !obj.isNull("previousAnchorLongitude")) {
                obj.optDouble("previousAnchorLongitude").takeIf { it.isFinite() }
            } else null
            return KmzCalibrationEditHistoryEntry(
                previousManualReferenceMetres = obj.optLong("previousManualReferenceMetres", 0L),
                previousOriginalKmzMetres = obj.optLong("previousOriginalKmzMetres", 0L),
                previousOffsetMetres = obj.optLong("previousOffsetMetres", 0L),
                previousAnchorDistanceAlongMetres = obj.optDouble("previousAnchorDistanceAlongMetres", 0.0),
                previousAnchorLatitude = lat,
                previousAnchorLongitude = lng,
                previousGpsAccuracyMetres = obj.optDouble("previousGpsAccuracyMetres", 3.5).toFloat(),
                previousSignedLateralOffsetMetres = obj.optDouble("previousSignedLateralOffsetMetres", 0.0),
                editedAtMs = obj.optLong("editedAtMs", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Manual reference calibration bound strictly to one KMZ/KML route and alignment.
 *
 * Formulas:
 * - offsetMetres = manualReferenceMetres - originalKmzMetres
 * - correctedChainageMetres = originalKmzMetresAtCurrentPos + offsetMetres (only within ±2000m along alignment)
 */
data class KmzManualCalibration(
    val routeId: String,
    val routeFileName: String = "",
    val alignmentId: String = "",
    val alignmentName: String = "",
    val alignmentFingerprint: String = "",
    val originalKmzMetres: Long,
    val manualReferenceMetres: Long,
    val anchorDistanceAlongMetres: Double,
    val anchorLatitude: Double? = null,
    val anchorLongitude: Double? = null,
    val coverageRadiusMetres: Double = DEFAULT_COVERAGE_RADIUS_METRES,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val id: String = buildCalibrationId(routeId, alignmentId, originalKmzMetres),
    val gpsAccuracyAtCalibrationMetres: Float = 3.5f,
    val signedLateralOffsetMetres: Double = 0.0,
    val centerlineOffsetZone: CenterlineOffsetZone = resolveZoneFromSignedOffset(signedLateralOffsetMetres),
    val createdAtMs: Long = updatedAtMs,
    val isEnabled: Boolean = true,
    val editHistory: List<KmzCalibrationEditHistoryEntry> = emptyList()
) {
    val offsetMetres: Long
        get() = manualReferenceMetres - originalKmzMetres

    val offsetMillimetres: Long
        get() = offsetMetres * Chainage.MILLIMETRES_PER_METRE

    val formattedOffset: String
        get() = if (offsetMetres >= 0L) "+${offsetMetres} m" else "${offsetMetres} m"

    val originalKmzChainage: Chainage
        get() = Chainage(originalKmzMetres)

    val manualReferenceChainage: Chainage
        get() = Chainage(manualReferenceMetres)

    val formattedGpsAccuracy: String
        get() = String.format(Locale.US, "±%.1f m", gpsAccuracyAtCalibrationMetres.coerceAtLeast(0.1f))

    val formattedLateralOffset: String
        get() = ParallelCenterlineOffset.formatSignedLateralOffset(
            signedOffsetMetres = signedLateralOffsetMetres,
            zoneOverride = centerlineOffsetZone
        )

    val formattedCoverageRange: String
        get() {
            val radiusLong = coverageRadiusMetres.roundToLong()
            val startCh = Chainage((manualReferenceMetres - radiusLong).coerceAtLeast(0L)).format()
            val endCh = Chainage(manualReferenceMetres + radiusLong).format()
            val kmEachDir = coverageRadiusMetres / 1000.0
            return String.format(Locale.US, "±%.1f km (%s ↔ %s)", kmEachDir, startCh, endCh)
        }

    fun isWithinCoverage(currentDistanceAlongMetres: Double): Boolean {
        if (!currentDistanceAlongMetres.isFinite() || !anchorDistanceAlongMetres.isFinite()) return false
        return abs(currentDistanceAlongMetres - anchorDistanceAlongMetres) <=
            coverageRadiusMetres + COVERAGE_BOUNDARY_EPSILON_METRES
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("routeId", routeId)
        put("routeFileName", routeFileName)
        put("alignmentId", alignmentId)
        put("alignmentName", alignmentName)
        put("alignmentFingerprint", alignmentFingerprint)
        put("originalKmzMetres", originalKmzMetres)
        put("manualReferenceMetres", manualReferenceMetres)
        put("anchorDistanceAlongMetres", anchorDistanceAlongMetres)
        if (anchorLatitude != null) put("anchorLatitude", anchorLatitude)
        if (anchorLongitude != null) put("anchorLongitude", anchorLongitude)
        put("coverageRadiusMetres", coverageRadiusMetres)
        put("updatedAtMs", updatedAtMs)
        put("createdAtMs", createdAtMs)
        put("gpsAccuracyAtCalibrationMetres", gpsAccuracyAtCalibrationMetres.toDouble())
        put("signedLateralOffsetMetres", signedLateralOffsetMetres)
        put("centerlineOffsetZone", centerlineOffsetZone.name)
        put("isEnabled", isEnabled)
        if (editHistory.isNotEmpty()) {
            val arr = JSONArray()
            editHistory.forEach { arr.put(it.toJson()) }
            put("editHistory", arr)
        }
    }

    companion object {
        const val DEFAULT_COVERAGE_RADIUS_METRES = 2000.0
        const val COVERAGE_BOUNDARY_EPSILON_METRES = 0.05

        fun buildCalibrationId(
            routeId: String,
            alignmentId: String?,
            originalKmzMetres: Long
        ): String {
            val cleanRoute = routeId.trim()
            val cleanAlign = alignmentId?.trim().takeIf { !it.isNullOrEmpty() } ?: cleanRoute
            return "$cleanRoute::$cleanAlign::$originalKmzMetres"
        }

        fun resolveZoneFromSignedOffset(signedLateralOffsetMetres: Double): CenterlineOffsetZone {
            if (!signedLateralOffsetMetres.isFinite()) return CenterlineOffsetZone.OUTSIDE
            val absDist = abs(signedLateralOffsetMetres)
            return when {
                absDist > ParallelCenterlineOffset.MAX_RELIABLE_MATCH_RANGE_METRES -> CenterlineOffsetZone.OUTSIDE
                absDist <= ParallelCenterlineOffset.ON_CENTERLINE_THRESHOLD_METRES -> CenterlineOffsetZone.ON_LINE
                signedLateralOffsetMetres > 0.0 -> CenterlineOffsetZone.LEFT
                else -> CenterlineOffsetZone.RIGHT
            }
        }

        fun fromJson(obj: JSONObject): KmzManualCalibration? {
            val routeId = obj.optString("routeId", "").trim()
            if (routeId.isEmpty()) return null
            if (!obj.has("originalKmzMetres") || !obj.has("manualReferenceMetres")) return null
            val anchorLat = if (obj.has("anchorLatitude") && !obj.isNull("anchorLatitude")) {
                obj.optDouble("anchorLatitude").takeIf { it.isFinite() }
            } else null
            val anchorLng = if (obj.has("anchorLongitude") && !obj.isNull("anchorLongitude")) {
                obj.optDouble("anchorLongitude").takeIf { it.isFinite() }
            } else null
            val anchorDist = obj.optDouble("anchorDistanceAlongMetres", 0.0).let {
                if (it.isFinite() && it >= 0.0) it else 0.0
            }
            val radius = obj.optDouble("coverageRadiusMetres", DEFAULT_COVERAGE_RADIUS_METRES).let {
                if (it.isFinite() && it > 0.0) it else DEFAULT_COVERAGE_RADIUS_METRES
            }
            val origMetres = obj.optLong("originalKmzMetres", 0L)
            val alignId = obj.optString("alignmentId", "")
            val rawId = obj.optString("id", "").trim().ifEmpty {
                buildCalibrationId(routeId, alignId, origMetres)
            }
            val gpsAcc = obj.optDouble("gpsAccuracyAtCalibrationMetres", 3.5).let {
                if (it.isFinite() && it > 0.0) it.toFloat() else 3.5f
            }
            val signedLateral = obj.optDouble("signedLateralOffsetMetres", 0.0).let {
                if (it.isFinite()) it else 0.0
            }
            val zoneName = obj.optString("centerlineOffsetZone", "")
            val resolvedZone = try {
                if (zoneName.isNotBlank()) CenterlineOffsetZone.valueOf(zoneName)
                else resolveZoneFromSignedOffset(signedLateral)
            } catch (_: Exception) {
                resolveZoneFromSignedOffset(signedLateral)
            }
            val updatedAt = obj.optLong("updatedAtMs", System.currentTimeMillis())
            val createdAt = obj.optLong("createdAtMs", updatedAt)
            val isEnabled = obj.optBoolean("isEnabled", true)

            val historyList = mutableListOf<KmzCalibrationEditHistoryEntry>()
            val historyArr = obj.optJSONArray("editHistory")
            if (historyArr != null) {
                for (i in 0 until historyArr.length()) {
                    val hObj = historyArr.optJSONObject(i) ?: continue
                    KmzCalibrationEditHistoryEntry.fromJson(hObj)?.let { historyList.add(it) }
                }
            }

            return KmzManualCalibration(
                id = rawId,
                routeId = routeId,
                routeFileName = obj.optString("routeFileName", ""),
                alignmentId = alignId,
                alignmentName = obj.optString("alignmentName", ""),
                alignmentFingerprint = obj.optString("alignmentFingerprint", ""),
                originalKmzMetres = origMetres,
                manualReferenceMetres = obj.optLong("manualReferenceMetres", 0L),
                anchorDistanceAlongMetres = anchorDist,
                anchorLatitude = anchorLat,
                anchorLongitude = anchorLng,
                coverageRadiusMetres = radius,
                updatedAtMs = updatedAt,
                createdAtMs = createdAt,
                gpsAccuracyAtCalibrationMetres = gpsAcc,
                signedLateralOffsetMetres = signedLateral,
                centerlineOffsetZone = resolvedZone,
                isEnabled = isEnabled,
                editHistory = historyList
            )
        }
    }
}

data class KmzCalibrationApplicationResult(
    val status: KmzCalibrationCoverageStatus,
    val outputChainage: Chainage,
    val rawKmzChainage: Chainage,
    val appliedOffsetMetres: Long,
    val distanceFromAnchorMetres: Double?,
    val calibration: KmzManualCalibration?,
    val statusMessage: String,
    val outputChainageMillimetres: Long = outputChainage.totalMillimetres,
    val rawKmzChainageMillimetres: Long = rawKmzChainage.totalMillimetres
) {
    val isCalibratedOutputActive: Boolean
        get() = status == KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE
}

/**
 * Decision state of the Smart Calibration Suggestion Engine.
 */
enum class KmzCalibrationSuggestionStatus(
    val badgeLabel: String,
    val isEligible: Boolean
) {
    ELIGIBLE_SUGGEST_CALIBRATION("Suggest calibration", true),
    WAIT_POOR_GPS_ACCURACY("Wait", false),
    RECHECK_EXCESSIVE_LATERAL_OFFSET("Recheck alignment", false),
    WAIT_STABILIZING_SAMPLES("Stabilizing GPS", false),
    CONFLICTING_REFERENCE_NEARBY("Reference active", false),
    OUT_OF_ALIGNMENT_RANGE("Outside range", false),
    ALIGNMENT_OR_GPS_UNAVAILABLE("Waiting for GPS", false)
}

/**
 * Result of evaluating whether to suggest manual reference chainage calibration at the current GPS position.
 *
 * Eligibility rules:
 * 1. GPS horizontal accuracy <= ±5.0 m
 * 2. GPS position matches the selected KMZ/KML alignment
 * 3. Absolute perpendicular centerline offset <= 20.0 m
 * 4. Multiple reliable GPS samples confirm a stable position
 * 5. Correct carriageway and alignment identified
 * 6. No conflicting reference exists nearby
 * 7. Proposed reference position is within the alignment's valid chainage range
 */
data class KmzCalibrationSuggestion(
    val status: KmzCalibrationSuggestionStatus,
    val originalKmzChainage: Chainage?,
    val gpsAccuracyMetres: Float,
    val parallelOffset: ParallelCenterlineOffset,
    val matchedAlignmentId: String,
    val matchedAlignmentName: String,
    val stableSampleCount: Int,
    val requiredStableSamples: Int,
    val telemetryLine: String,
    val guidanceMessage: String,
    val coverageRadiusMetres: Double = KmzManualCalibration.DEFAULT_COVERAGE_RADIUS_METRES
) {
    val isEligible: Boolean
        get() = status.isEligible && originalKmzChainage != null
}
