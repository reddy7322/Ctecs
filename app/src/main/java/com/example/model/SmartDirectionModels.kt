package com.example.model

import org.json.JSONObject
import java.util.Locale

/**
 * Authoritative internal detection states for CTECS Smart LHS/RHS Auto Direction Engine.
 */
enum class DirectionDetectionState(
    val displayLabel: String,
    val shortLabel: String,
    val isConfirmed: Boolean
) {
    LHS_CONFIRMED("LHS Confirmed", "LHS", true),
    RHS_CONFIRMED("RHS Confirmed", "RHS", true),
    DIRECTION_CHANGING("Direction Changing", "Changing…", false),
    DIRECTION_UNCERTAIN("Direction Uncertain", "Uncertain", false),
    GPS_SIGNAL_WEAK("GPS Signal Weak", "Weak GPS", false),
    STATIONARY("Stationary", "Stopped", false),
    ALIGNMENT_UNAVAILABLE("Alignment Unavailable", "No Route", false)
}

/**
 * Defines how chainage progression maps to LHS / RHS on the active highway alignment.
 * Never assumes increasing chainage always means LHS without checking alignment metadata or preference.
 */
enum class AlignmentDirectionConvention(
    val id: String,
    val label: String,
    val description: String
) {
    AUTO_FROM_ALIGNMENT(
        id = "auto_from_alignment",
        label = "Auto (KMZ)",
        description = "Derive LHS/RHS relationship automatically from active KMZ/KML alignment metadata"
    ),
    INCREASING_IS_LHS(
        id = "increasing_is_lhs",
        label = "Inc = LHS",
        description = "Increasing chainage is LHS; decreasing chainage is RHS"
    ),
    INCREASING_IS_RHS(
        id = "increasing_is_rhs",
        label = "Inc = RHS",
        description = "Increasing chainage is RHS; decreasing chainage is LHS"
    );

    companion object {
        fun fromId(raw: String?): AlignmentDirectionConvention {
            val cleaned = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull {
                it.id.equals(cleaned, ignoreCase = true) || it.name.equals(cleaned, ignoreCase = true)
            } ?: AUTO_FROM_ALIGNMENT
        }
    }
}

/**
 * Controls whether CTECS determines LHS/RHS automatically from GPS + road alignment
 * or uses an explicit temporary manual override.
 */
enum class DirectionControlMode(val id: String, val label: String) {
    AUTO("auto", "Auto"),
    MANUAL_OVERRIDE("manual_override", "Manual");

    companion object {
        fun fromId(raw: String?): DirectionControlMode {
            val cleaned = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull {
                it.id.equals(cleaned, ignoreCase = true) || it.name.equals(cleaned, ignoreCase = true)
            } ?: AUTO
        }
    }
}

/**
 * Actual calibrated chainage progression direction along the active alignment.
 */
enum class ChainageProgressionTrend(val label: String, val arrow: String) {
    INCREASING("Increasing", "↑"),
    DECREASING("Decreasing", "↓"),
    STATIONARY("Stationary", "•"),
    UNKNOWN("Unknown", "")
}

/**
 * User-configured preferences for Smart LHS/RHS Direction Detection.
 * Persists ONLY user preferences — never persists stale GPS direction state.
 */
data class SmartDirectionPreferences(
    val controlMode: DirectionControlMode = DirectionControlMode.AUTO,
    val manualOverrideSide: String? = null,
    val alignmentConvention: AlignmentDirectionConvention = AlignmentDirectionConvention.AUTO_FROM_ALIGNMENT,
    val voiceDirectionAnnouncementsEnabled: Boolean = true
) {
    val normalizedManualSide: String?
        get() = when (manualOverrideSide?.trim()?.uppercase(Locale.ROOT)) {
            "LHS" -> "LHS"
            "RHS" -> "RHS"
            "BHS", "BOTH" -> "BHS"
            "MEDIAN" -> "MEDIAN"
            "NONE" -> "NONE"
            else -> null
        }

    fun toJsonString(): String {
        return JSONObject().apply {
            put("controlMode", controlMode.id)
            put("manualOverrideSide", normalizedManualSide ?: "")
            put("alignmentConvention", alignmentConvention.id)
            put("voiceDirectionAnnouncementsEnabled", voiceDirectionAnnouncementsEnabled)
        }.toString()
    }

    companion object {
        fun fromJsonString(raw: String?): SmartDirectionPreferences {
            if (raw.isNullOrBlank()) return SmartDirectionPreferences()
            return try {
                val obj = JSONObject(raw)
                val mode = DirectionControlMode.fromId(obj.optString("controlMode", DirectionControlMode.AUTO.id))
                val rawManual = obj.optString("manualOverrideSide", "").trim().takeIf { it.isNotBlank() }
                val effectiveMode = if (mode == DirectionControlMode.MANUAL_OVERRIDE && rawManual != null) {
                    DirectionControlMode.MANUAL_OVERRIDE
                } else {
                    DirectionControlMode.AUTO
                }
                SmartDirectionPreferences(
                    controlMode = effectiveMode,
                    manualOverrideSide = if (effectiveMode == DirectionControlMode.MANUAL_OVERRIDE) rawManual else null,
                    alignmentConvention = AlignmentDirectionConvention.fromId(
                        obj.optString("alignmentConvention", AlignmentDirectionConvention.AUTO_FROM_ALIGNMENT.id)
                    ),
                    voiceDirectionAnnouncementsEnabled = obj.optBoolean("voiceDirectionAnnouncementsEnabled", true)
                )
            } catch (_: Exception) {
                SmartDirectionPreferences()
            }
        }
    }
}

/**
 * Immutable, authoritative application-wide LHS/RHS travel direction snapshot published by
 * [com.example.navigation.SmartDirectionManager].
 *
 * Strictly guarantees:
 * - [confirmedSide] is non-null ONLY when sufficient reliable evidence exists (or explicit manual override).
 * - [isIncreasingChainage] is resolved from [confirmedSide] + [effectiveConvention] rather than
 *   hardcoding increasing == LHS.
 * - [directionChangedFrom] is populated ONLY on the exact update where a confirmed direction transition completes.
 */
data class SmartDirectionState(
    val detectionState: DirectionDetectionState = DirectionDetectionState.ALIGNMENT_UNAVAILABLE,
    val confirmedSide: String? = null,
    val pendingCandidateSide: String? = null,
    val chainageTrend: ChainageProgressionTrend = ChainageProgressionTrend.UNKNOWN,
    val isIncreasingChainage: Boolean? = null,
    val directionArrow: String = "",
    val isManualOverride: Boolean = false,
    val confidenceScore: Float = 0f,
    val routeId: String? = null,
    val alignmentId: String? = null,
    val effectiveConvention: AlignmentDirectionConvention = AlignmentDirectionConvention.INCREASING_IS_LHS,
    val statusLabel: String = "No Route",
    val detailReason: String = "Waiting for active road alignment",
    val lastConfirmedTimestampMs: Long = 0L,
    val lastDirectionChangeTimestampMs: Long = 0L,
    val directionChangedFrom: String? = null
) {
    /**
     * Strictly returns "LHS" or "RHS" only when a binary highway direction is confirmed (or manually set).
     */
    val confirmedBinarySide: String?
        get() = confirmedSide?.trim()?.uppercase(Locale.ROOT)?.takeIf { it == "LHS" || it == "RHS" }

    val hasConfirmedDirection: Boolean
        get() = confirmedBinarySide != null || (isManualOverride && !confirmedSide.isNullOrBlank())

    val isUncertainOrWeak: Boolean
        get() = detectionState == DirectionDetectionState.DIRECTION_UNCERTAIN ||
            detectionState == DirectionDetectionState.GPS_SIGNAL_WEAK ||
            detectionState == DirectionDetectionState.DIRECTION_CHANGING

    val compactDisplayText: String
        get() = when {
            confirmedBinarySide != null -> buildString {
                if (directionArrow.isNotBlank()) {
                    append(directionArrow)
                    append(' ')
                }
                append(confirmedBinarySide)
            }
            isManualOverride && !confirmedSide.isNullOrBlank() -> confirmedSide
            else -> detectionState.shortLabel
        }

    companion object {
        fun alignmentUnavailable(
            routeId: String? = null,
            alignmentId: String? = null
        ) = SmartDirectionState(
            detectionState = DirectionDetectionState.ALIGNMENT_UNAVAILABLE,
            confirmedSide = null,
            chainageTrend = ChainageProgressionTrend.UNKNOWN,
            isIncreasingChainage = null,
            directionArrow = "",
            isManualOverride = false,
            confidenceScore = 0f,
            routeId = routeId,
            alignmentId = alignmentId,
            statusLabel = "No Route",
            detailReason = "Load a KMZ/KML road alignment to detect travel direction"
        )
    }
}
