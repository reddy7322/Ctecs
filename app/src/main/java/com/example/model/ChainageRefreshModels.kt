package com.example.model

import org.json.JSONObject

enum class RefreshMode(val label: String) {
    SMART("Smart Refresh"),
    FORCE_ALL("Force Refresh All")
}

enum class ChainageCardRefreshState {
    IDLE,
    REFRESHING,
    SUCCESS,
    WARNING
}

enum class ChainageAccuracyStatus(val label: String) {
    VERIFIED("Accurate / Verified"),
    UPDATED("Updated"),
    NEEDS_GPS("Needs GPS"),
    ALIGNMENT_MISMATCH("Alignment Mismatch"),
    CALIBRATION_NEEDS_REVIEW("Calibration Needs Review")
}

/**
 * Privacy-safe diagnostic entry recorded whenever a suspicious, stale, inaccurate,
 * or mismatched chainage calculation is rejected. Intentionally omits raw GPS latitude/longitude.
 */
data class RejectedChainageDiagnostic(
    val timestampMs: Long = System.currentTimeMillis(),
    val gpsAccuracyMetres: Float = 0f,
    val alignmentName: String = "",
    val previousChainageMetres: Long? = null,
    val rejectedChainageMetres: Long? = null,
    val direction: String = "",
    val reason: String = ""
)

/**
 * Lightweight persisted snapshot of the last validated reliable chainage for cold-start
 * and background/foreground restoration. Intentionally omits raw GPS coordinates.
 */
data class LastReliableChainageSnapshot(
    val routeId: String,
    val chainageMetres: Long,
    val rawChainageMetres: Long? = null,
    val alignmentName: String = "",
    val distanceAlongMetres: Double = 0.0,
    val detectedSide: String = "RHS",
    val sourceName: String = ChainageSource.KMZ.name,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("routeId", routeId)
            put("chainageMetres", chainageMetres)
            if (rawChainageMetres != null) put("rawChainageMetres", rawChainageMetres)
            put("alignmentName", alignmentName)
            put("distanceAlongMetres", distanceAlongMetres)
            put("detectedSide", detectedSide)
            put("sourceName", sourceName)
            put("timestampMs", timestampMs)
        }.toString()
    }

    companion object {
        fun fromJsonString(json: String?): LastReliableChainageSnapshot? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                val routeId = obj.optString("routeId", "").trim()
                if (routeId.isEmpty() || !obj.has("chainageMetres")) return null
                LastReliableChainageSnapshot(
                    routeId = routeId,
                    chainageMetres = obj.getLong("chainageMetres").coerceAtLeast(0L),
                    rawChainageMetres = if (obj.has("rawChainageMetres") && !obj.isNull("rawChainageMetres")) {
                        obj.getLong("rawChainageMetres").coerceAtLeast(0L)
                    } else null,
                    alignmentName = obj.optString("alignmentName", ""),
                    distanceAlongMetres = obj.optDouble("distanceAlongMetres", 0.0).takeIf { it.isFinite() } ?: 0.0,
                    detectedSide = obj.optString("detectedSide", "RHS").ifBlank { "RHS" },
                    sourceName = obj.optString("sourceName", ChainageSource.KMZ.name),
                    timestampMs = obj.optLong("timestampMs", 0L)
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class ChainageAccuracyReport(
    val status: ChainageAccuracyStatus = ChainageAccuracyStatus.NEEDS_GPS,
    val mode: RefreshMode = RefreshMode.SMART,
    val isRefreshing: Boolean = false,
    val isStaleFallback: Boolean = false,
    val summaryMessage: String = "",
    val lastRefreshedEpochMs: Long = 0L,
    val isGpsValid: Boolean = false,
    val isGpsFresh: Boolean = false,
    val gpsFixAgeMs: Long = -1L,
    val matchConfidence: MatchConfidence = MatchConfidence.NO_MATCH,
    val offsetMetres: Double = 0.0,
    val isCalibrationProfileValid: Boolean = true,
    val isControlPointConsistent: Boolean = true,
    val isChainageProgressionValid: Boolean = false,
    val isSideConsistent: Boolean = false,
    val isCustomCorrectionValid: Boolean = true,
    val isManualOverrideActive: Boolean = false,
    val stepNotes: List<String> = emptyList()
)

