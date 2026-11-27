package com.example.model

import org.json.JSONObject
import java.util.Locale

/**
 * Operating mode for Camera Manual Chainage.
 *
 * - [CONTINUE_STABLE]: Starts from the manually entered reference chainage and progresses
 *   continuously using filtered direction-aware movement along alignment/travel without
 *   automatic GPS-to-KMZ recalibration or GPS jump resets.
 * - [DYNAMIC_CORRECTION]: Anchors the manually entered chainage to a reliable GPS alignment
 *   match (storing an immutable calibration anchor and single signed offset) and updates
 *   calibrated chainage direction-aware along road geometry without cumulative offset errors.
 */
enum class ManualChainageMode(
    val storageKey: String,
    val shortLabel: String,
    val displayLabel: String
) {
    CONTINUE_STABLE(
        storageKey = "CONTINUE_STABLE",
        shortLabel = "Stable",
        displayLabel = "Continue Stable Chainage"
    ),
    DYNAMIC_CORRECTION(
        storageKey = "DYNAMIC_CORRECTION",
        shortLabel = "Dynamic",
        displayLabel = "Dynamic Chainage Correction"
    );

    companion object {
        fun fromStorageKey(raw: String?): ManualChainageMode {
            if (raw.isNullOrBlank()) return CONTINUE_STABLE
            return entries.firstOrNull {
                it.storageKey.equals(raw.trim(), ignoreCase = true) ||
                    it.name.equals(raw.trim(), ignoreCase = true)
            } ?: CONTINUE_STABLE
        }
    }
}

/**
 * Actual movement direction relative to chainage progression.
 * Strictly separate from carriageway side (LHS / RHS / BHS).
 */
enum class TravelChainageDirection(
    val sign: Int,
    val label: String,
    val arrow: String
) {
    FORWARD(sign = 1, label = "Forward", arrow = "↑"),
    REVERSE(sign = -1, label = "Reverse", arrow = "↓"),
    STATIONARY(sign = 0, label = "Stationary", arrow = "•"),
    UNKNOWN(sign = 0, label = "Detecting", arrow = "·");

    companion object {
        fun fromStorageKey(raw: String?): TravelChainageDirection {
            if (raw.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: UNKNOWN
        }
    }
}

/**
 * Single centralized calibration state for Manual Chainage (Stable & Dynamic modes).
 * Persisted in [com.example.data.PreferencesManager] so calibration and active mode
 * survive app restarts cleanly.
 *
 * The user's entered [anchorChainageMetres] and dynamic reference [anchorRawAlignmentChainageMetres]
 * are stored separately from live progressing [currentChainageMetres] so repeated GPS updates
 * can NEVER cumulatively re-add the calibration offset.
 */
data class ManualChainageCalibrationState(
    val isActive: Boolean = false,
    val mode: ManualChainageMode = ManualChainageMode.CONTINUE_STABLE,
    val routeId: String? = null,
    val anchorChainageMetres: Long? = null,
    val anchorFormattedText: String? = null,
    val currentChainageMetres: Long? = null,
    val anchorRawAlignmentChainageMetres: Long? = null,
    val anchorDistanceAlongMetres: Double? = null,
    val anchorAlignmentName: String? = null,
    val anchorCarriagewaySide: String? = null,
    val anchorLatitude: Double? = null,
    val anchorLongitude: Double? = null,
    val anchorTimestampMs: Long = 0L,
    val isDynamicAnchorCalibrated: Boolean = false,
    val travelDirection: TravelChainageDirection = TravelChainageDirection.UNKNOWN,
    val computedOffsetMetres: Long? = null,
    val lastUpdatedTimestampMs: Long = 0L
) {
    val effectiveCurrentMetres: Long?
        get() = currentChainageMetres ?: anchorChainageMetres

    val effectiveDisplayChainage: String?
        get() = effectiveCurrentMetres?.let { Chainage(it).format() }
            ?: anchorFormattedText?.takeIf { it.isNotBlank() }

    val formattedOffsetBadge: String?
        get() {
            val offset = computedOffsetMetres ?: return null
            return if (offset >= 0L) "+${offset}m" else "${offset}m"
        }

    fun formatExifSummary(): String? {
        if (!isActive) return null
        val refText = anchorChainageMetres?.let { Chainage(it).format() } ?: anchorFormattedText
        val parts = mutableListOf<String>()
        parts.add("ChainageMode: ${mode.displayLabel}")
        if (!refText.isNullOrBlank()) {
            parts.add("CalRef: $refText")
        }
        if (mode == ManualChainageMode.DYNAMIC_CORRECTION && isDynamicAnchorCalibrated) {
            formattedOffsetBadge?.let { parts.add("CalOffset: $it") }
            anchorAlignmentName?.takeIf { it.isNotBlank() }?.let { parts.add("Align: $it") }
        }
        if (travelDirection == TravelChainageDirection.FORWARD || travelDirection == TravelChainageDirection.REVERSE) {
            parts.add("Travel: ${travelDirection.label}")
        }
        return parts.joinToString(", ")
    }

    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("isActive", isActive)
        obj.put("mode", mode.storageKey)
        if (!routeId.isNullOrBlank()) obj.put("routeId", routeId)
        if (anchorChainageMetres != null) obj.put("anchorChainageMetres", anchorChainageMetres)
        if (!anchorFormattedText.isNullOrBlank()) obj.put("anchorFormattedText", anchorFormattedText)
        if (currentChainageMetres != null) obj.put("currentChainageMetres", currentChainageMetres)
        if (anchorRawAlignmentChainageMetres != null) {
            obj.put("anchorRawAlignmentChainageMetres", anchorRawAlignmentChainageMetres)
        }
        if (anchorDistanceAlongMetres != null && anchorDistanceAlongMetres.isFinite()) {
            obj.put("anchorDistanceAlongMetres", anchorDistanceAlongMetres)
        }
        if (!anchorAlignmentName.isNullOrBlank()) obj.put("anchorAlignmentName", anchorAlignmentName)
        if (!anchorCarriagewaySide.isNullOrBlank()) obj.put("anchorCarriagewaySide", anchorCarriagewaySide)
        if (anchorLatitude != null && anchorLatitude.isFinite()) obj.put("anchorLatitude", anchorLatitude)
        if (anchorLongitude != null && anchorLongitude.isFinite()) obj.put("anchorLongitude", anchorLongitude)
        obj.put("anchorTimestampMs", anchorTimestampMs)
        obj.put("isDynamicAnchorCalibrated", isDynamicAnchorCalibrated)
        obj.put("travelDirection", travelDirection.name)
        if (computedOffsetMetres != null) obj.put("computedOffsetMetres", computedOffsetMetres)
        obj.put("lastUpdatedTimestampMs", lastUpdatedTimestampMs)
        return obj.toString()
    }

    companion object {
        fun inactive(defaultMode: ManualChainageMode = ManualChainageMode.CONTINUE_STABLE) =
            ManualChainageCalibrationState(
                isActive = false,
                mode = defaultMode
            )

        fun fromJsonString(rawJson: String?): ManualChainageCalibrationState {
            if (rawJson.isNullOrBlank()) return ManualChainageCalibrationState()
            return try {
                val obj = JSONObject(rawJson)
                ManualChainageCalibrationState(
                    isActive = obj.optBoolean("isActive", false),
                    mode = ManualChainageMode.fromStorageKey(obj.optString("mode", "")),
                    routeId = obj.optString("routeId", "").takeIf { it.isNotBlank() },
                    anchorChainageMetres = if (obj.has("anchorChainageMetres") && !obj.isNull("anchorChainageMetres")) {
                        obj.optLong("anchorChainageMetres")
                    } else null,
                    anchorFormattedText = obj.optString("anchorFormattedText", "").takeIf { it.isNotBlank() },
                    currentChainageMetres = if (obj.has("currentChainageMetres") && !obj.isNull("currentChainageMetres")) {
                        obj.optLong("currentChainageMetres")
                    } else null,
                    anchorRawAlignmentChainageMetres = if (
                        obj.has("anchorRawAlignmentChainageMetres") && !obj.isNull("anchorRawAlignmentChainageMetres")
                    ) {
                        obj.optLong("anchorRawAlignmentChainageMetres")
                    } else null,
                    anchorDistanceAlongMetres = if (
                        obj.has("anchorDistanceAlongMetres") && !obj.isNull("anchorDistanceAlongMetres")
                    ) {
                        obj.optDouble("anchorDistanceAlongMetres").takeIf { it.isFinite() }
                    } else null,
                    anchorAlignmentName = obj.optString("anchorAlignmentName", "").takeIf { it.isNotBlank() },
                    anchorCarriagewaySide = obj.optString("anchorCarriagewaySide", "").takeIf { it.isNotBlank() },
                    anchorLatitude = if (obj.has("anchorLatitude") && !obj.isNull("anchorLatitude")) {
                        obj.optDouble("anchorLatitude").takeIf { it.isFinite() }
                    } else null,
                    anchorLongitude = if (obj.has("anchorLongitude") && !obj.isNull("anchorLongitude")) {
                        obj.optDouble("anchorLongitude").takeIf { it.isFinite() }
                    } else null,
                    anchorTimestampMs = obj.optLong("anchorTimestampMs", 0L),
                    isDynamicAnchorCalibrated = obj.optBoolean("isDynamicAnchorCalibrated", false),
                    travelDirection = TravelChainageDirection.fromStorageKey(obj.optString("travelDirection", "")),
                    computedOffsetMetres = if (obj.has("computedOffsetMetres") && !obj.isNull("computedOffsetMetres")) {
                        obj.optLong("computedOffsetMetres")
                    } else null,
                    lastUpdatedTimestampMs = obj.optLong("lastUpdatedTimestampMs", 0L)
                )
            } catch (_: Exception) {
                ManualChainageCalibrationState()
            }
        }
    }
}

/**
 * Timestamped chainage history sample recorded continuously during video recording.
 */
data class VideoChainageHistorySample(
    val wallTimestampMs: Long,
    val recordingElapsedMs: Long,
    val chainageMetres: Long?,
    val chainageLabel: String,
    val source: ChainageSource,
    val manualMode: ManualChainageMode? = null,
    val travelDirection: TravelChainageDirection = TravelChainageDirection.UNKNOWN,
    val roadSide: String = "Unknown",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracy: Float = 0f,
    val isGpsRetained: Boolean = false
) {
    fun formatCompact(): String {
        val sec = recordingElapsedMs / 1000L
        val mins = sec / 60L
        val remSec = sec % 60L
        val timeStr = String.format(Locale.US, "%02d:%02d", mins, remSec)
        val dirArrow = if (travelDirection.arrow == "↑" || travelDirection.arrow == "↓") " ${travelDirection.arrow}" else ""
        return "$timeStr $chainageLabel$dirArrow"
    }
}
