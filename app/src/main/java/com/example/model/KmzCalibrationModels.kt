package com.example.model

import org.json.JSONObject
import kotlin.math.abs

enum class KmzCalibrationCoverageStatus(val shortLabel: String, val detailLabel: String) {
    NOT_CALIBRATED("No calibration", "Using original KMZ/KML chainage"),
    ACTIVE_WITHIN_RANGE("Active • within 2 km coverage", "Calibration offset applied within ±2,000 m along alignment"),
    OUTSIDE_2KM_RANGE("Outside 2 km range", "Beyond ±2,000 m from reference — using original KMZ/KML chainage"),
    ALIGNMENT_MISMATCH("Alignment changed", "Active KMZ/KML alignment changed — recalibrate or reset"),
    GPS_UNAVAILABLE("GPS unavailable", "Waiting for valid GPS location on alignment")
}

/**
 * Single manual reference calibration bound strictly to one KMZ/KML route and alignment.
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
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val offsetMetres: Long
        get() = manualReferenceMetres - originalKmzMetres

    val formattedOffset: String
        get() = if (offsetMetres >= 0L) "+${offsetMetres} m" else "${offsetMetres} m"

    val originalKmzChainage: Chainage
        get() = Chainage(originalKmzMetres)

    val manualReferenceChainage: Chainage
        get() = Chainage(manualReferenceMetres)

    fun isWithinCoverage(currentDistanceAlongMetres: Double): Boolean {
        if (!currentDistanceAlongMetres.isFinite() || !anchorDistanceAlongMetres.isFinite()) return false
        return abs(currentDistanceAlongMetres - anchorDistanceAlongMetres) <=
            coverageRadiusMetres + COVERAGE_BOUNDARY_EPSILON_METRES
    }

    fun toJson(): JSONObject = JSONObject().apply {
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
    }

    companion object {
        const val DEFAULT_COVERAGE_RADIUS_METRES = 2000.0
        const val COVERAGE_BOUNDARY_EPSILON_METRES = 0.05

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
            return KmzManualCalibration(
                routeId = routeId,
                routeFileName = obj.optString("routeFileName", ""),
                alignmentId = obj.optString("alignmentId", ""),
                alignmentName = obj.optString("alignmentName", ""),
                alignmentFingerprint = obj.optString("alignmentFingerprint", ""),
                originalKmzMetres = obj.optLong("originalKmzMetres", 0L),
                manualReferenceMetres = obj.optLong("manualReferenceMetres", 0L),
                anchorDistanceAlongMetres = anchorDist,
                anchorLatitude = anchorLat,
                anchorLongitude = anchorLng,
                coverageRadiusMetres = radius,
                updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis())
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
    val statusMessage: String
) {
    val isCalibratedOutputActive: Boolean
        get() = status == KmzCalibrationCoverageStatus.ACTIVE_WITHIN_RANGE
}
