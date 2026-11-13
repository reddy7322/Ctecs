package com.example.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Authoritative source of a permanent chainage reference point.
 */
object ReferencePointSource {
    const val MANUAL = "MANUAL"
    const val ALIGNMENT_PICK = "ALIGNMENT_PICK"
    const val KMZ_IMPORT = "KMZ_IMPORT"
    const val GPS_VERIFIED = "GPS_VERIFIED"
}

/**
 * Validation status for a reference point or calibration profile.
 */
object CalibrationValidationStatus {
    const val VERIFIED = "VERIFIED"
    const val NEEDS_REVIEW = "NEEDS_REVIEW"
    const val LOW_CONFIDENCE = "LOW_CONFIDENCE"
    const val INVALID = "INVALID"

    fun displayLabel(status: String): String = when (status) {
        VERIFIED -> "Verified"
        NEEDS_REVIEW -> "Needs Review"
        LOW_CONFIDENCE -> "Low Confidence"
        INVALID -> "Invalid / Conflict"
        else -> status
    }
}

/**
 * A permanent verified chainage reference/control point defined along a specific KMZ/KML route.
 *
 * Combines:
 * - Permanent GPS + Chainage Reference Points
 * - KMZ/KML Alignment-Based Calibration
 * - Multiple Fixed Control Points with Piecewise Alignment Interpolation
 * - Permanent Calibration Profile linkage
 */
data class ChainageCorrectionPoint(
    val id: String = java.util.UUID.randomUUID().toString(),
    val routeId: String,
    val projectId: String = "",
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val originalKmzMetres: Long,
    val correctedMetres: Long,
    val routeDistanceMetres: Double = 0.0,
    val description: String = "",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val referenceSource: String = ReferencePointSource.MANUAL,
    val gpsAccuracyAtCapture: Float? = null,
    val surveyTimestamp: Long = createdAt,
    val verificationStatus: String = CalibrationValidationStatus.VERIFIED,
    val calibrationProfileId: String = ""
) {
    val point: GeoPoint get() = GeoPoint(latitude, longitude, altitude ?: 0.0)
    val originalKmzChainage: Chainage get() = Chainage(originalKmzMetres)
    val correctedChainage: Chainage get() = Chainage(correctedMetres)
    val offsetMetres: Long get() = correctedMetres - originalKmzMetres

    val isVerified: Boolean
        get() = verificationStatus == CalibrationValidationStatus.VERIFIED

    val displayOffset: String
        get() = if (offsetMetres >= 0) "+${offsetMetres}m" else "${offsetMetres}m"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("routeId", routeId)
        put("projectId", projectId)
        put("lat", latitude)
        put("lng", longitude)
        if (altitude != null) put("alt", altitude)
        put("originalKmzMetres", originalKmzMetres)
        put("correctedMetres", correctedMetres)
        put("routeDistanceMetres", routeDistanceMetres)
        put("description", description)
        put("isEnabled", isEnabled)
        put("createdAt", createdAt)
        put("referenceSource", referenceSource)
        if (gpsAccuracyAtCapture != null) put("gpsAccuracyAtCapture", gpsAccuracyAtCapture.toDouble())
        put("surveyTimestamp", surveyTimestamp)
        put("verificationStatus", verificationStatus)
        put("calibrationProfileId", calibrationProfileId)
    }

    companion object {
        fun fromJson(obj: JSONObject): ChainageCorrectionPoint {
            val created = obj.optLong("createdAt", System.currentTimeMillis())
            val alt = if (obj.has("alt") && !obj.isNull("alt")) obj.optDouble("alt") else null
            val gpsAcc = if (obj.has("gpsAccuracyAtCapture") && !obj.isNull("gpsAccuracyAtCapture")) {
                obj.optDouble("gpsAccuracyAtCapture").toFloat()
            } else null
            return ChainageCorrectionPoint(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                routeId = obj.optString("routeId", ""),
                projectId = obj.optString("projectId", ""),
                latitude = obj.optDouble("lat", 0.0),
                longitude = obj.optDouble("lng", 0.0),
                altitude = alt,
                originalKmzMetres = obj.optLong("originalKmzMetres", 0L),
                correctedMetres = obj.optLong("correctedMetres", 0L),
                routeDistanceMetres = obj.optDouble("routeDistanceMetres", 0.0),
                description = obj.optString("description", ""),
                isEnabled = obj.optBoolean("isEnabled", true),
                createdAt = created,
                referenceSource = obj.optString("referenceSource", ReferencePointSource.MANUAL),
                gpsAccuracyAtCapture = gpsAcc,
                surveyTimestamp = obj.optLong("surveyTimestamp", created),
                verificationStatus = obj.optString("verificationStatus", CalibrationValidationStatus.VERIFIED),
                calibrationProfileId = obj.optString("calibrationProfileId", "")
            )
        }
    }
}

/**
 * Automatically generated 2 km (or custom interval) calibrated chainage marker along the KMZ alignment.
 * Explicitly distinguished from permanent verified control points.
 */
data class GeneratedChainageMarker(
    val calibratedChainage: Chainage,
    val originalKmzChainage: Chainage,
    val point: GeoPoint,
    val distanceAlongAlignmentMetres: Double,
    val alignmentName: String = "",
    val intervalMetres: Long = 2000L,
    val isGenerated2KmMarker: Boolean = true,
    val interpolationMode: String = "CALIBRATED_ALIGNMENT"
) {
    val label: String get() = calibratedChainage.format()
    val offsetMetres: Long get() = calibratedChainage.metres - originalKmzChainage.metres
}

/**
 * Permanent reusable calibration profile for a specific project and KMZ/KML alignment.
 * Supports automatic geometry fingerprint matching and modified-alignment detection.
 */
data class PermanentCalibrationProfile(
    val profileId: String = java.util.UUID.randomUUID().toString(),
    val profileName: String,
    val projectId: String,
    val routeId: String,
    val originalFileName: String,
    val alignmentFingerprint: String,
    val controlPoints: List<ChainageCorrectionPoint>,
    val chainageDirection: String = "INCREASING",
    val chainageEquations: List<String> = emptyList(),
    val markerIntervalMetres: Long = 2000L,
    val calibrationOffsetSummary: String = "",
    val lastValidatedAt: Long = System.currentTimeMillis(),
    val validationStatus: String = CalibrationValidationStatus.VERIFIED,
    val statusDetailMessage: String = "Validated Permanent Calibration Profile",
    val version: Int = 1
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("profileId", profileId)
        put("profileName", profileName)
        put("projectId", projectId)
        put("routeId", routeId)
        put("originalFileName", originalFileName)
        put("alignmentFingerprint", alignmentFingerprint)
        val ptsArr = JSONArray()
        controlPoints.forEach { ptsArr.put(it.toJson()) }
        put("controlPoints", ptsArr)
        put("chainageDirection", chainageDirection)
        val eqArr = JSONArray()
        chainageEquations.forEach { eqArr.put(it) }
        put("chainageEquations", eqArr)
        put("markerIntervalMetres", markerIntervalMetres)
        put("calibrationOffsetSummary", calibrationOffsetSummary)
        put("lastValidatedAt", lastValidatedAt)
        put("validationStatus", validationStatus)
        put("statusDetailMessage", statusDetailMessage)
        put("version", version)
    }

    companion object {
        fun fromJson(obj: JSONObject): PermanentCalibrationProfile {
            val pts = mutableListOf<ChainageCorrectionPoint>()
            val ptsArr = obj.optJSONArray("controlPoints")
            if (ptsArr != null) {
                for (i in 0 until ptsArr.length()) {
                    val pObj = ptsArr.optJSONObject(i) ?: continue
                    pts.add(ChainageCorrectionPoint.fromJson(pObj))
                }
            }
            val eqs = mutableListOf<String>()
            val eqArr = obj.optJSONArray("chainageEquations")
            if (eqArr != null) {
                for (i in 0 until eqArr.length()) {
                    val s = eqArr.optString(i)
                    if (s.isNotBlank()) eqs.add(s)
                }
            }
            return PermanentCalibrationProfile(
                profileId = obj.optString("profileId", java.util.UUID.randomUUID().toString()),
                profileName = obj.optString("profileName", "Calibration Profile"),
                projectId = obj.optString("projectId", ""),
                routeId = obj.optString("routeId", ""),
                originalFileName = obj.optString("originalFileName", ""),
                alignmentFingerprint = obj.optString("alignmentFingerprint", ""),
                controlPoints = pts,
                chainageDirection = obj.optString("chainageDirection", "INCREASING"),
                chainageEquations = eqs,
                markerIntervalMetres = obj.optLong("markerIntervalMetres", 2000L),
                calibrationOffsetSummary = obj.optString("calibrationOffsetSummary", ""),
                lastValidatedAt = obj.optLong("lastValidatedAt", System.currentTimeMillis()),
                validationStatus = obj.optString("validationStatus", CalibrationValidationStatus.VERIFIED),
                statusDetailMessage = obj.optString("statusDetailMessage", "Validated Permanent Calibration Profile"),
                version = obj.optInt("version", 1)
            )
        }
    }
}

/**
 * Preview and audit report of a minimal-change KMZ/KML calibration replacement operation.
 */
data class KmzReplacementPreview(
    val rangeStart: Chainage,
    val rangeEnd: Chainage,
    val intervalMetres: Long = 2000L,
    val removedOldMarkersCount: Int,
    val removedSampleLabels: List<String>,
    val inserted2KmMarkers: List<GeneratedChainageMarker>,
    val preservedChainageMarkersOutsideRangeCount: Int,
    val preservedNonChainagePlacemarksCount: Int,
    val preservedLineStringsCount: Int,
    val preservedArchiveEntriesCount: Int,
    val suggestedOutputFileName: String,
    val validationStatus: String = CalibrationValidationStatus.VERIFIED,
    val summaryMessage: String
)
