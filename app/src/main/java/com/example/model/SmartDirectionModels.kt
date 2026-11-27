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
 * Reason why manual direction override automatically transitioned back to AUTO mode.
 */
enum class DirectionAutoReactivationReason(val label: String) {
    PHOTO_INACTIVITY_TIMEOUT("10-min photo inactivity timeout"),
    VEHICLE_SPEED_TRIGGER("Vehicle speed >= 5 km/h")
}

/**
 * User-configured preferences for Centralized Global LHS/RHS Direction Control.
 * Persists user mode preference and manual override timestamp — never persists stale GPS direction.
 */
data class SmartDirectionPreferences(
    val controlMode: DirectionControlMode = DirectionControlMode.AUTO,
    val manualOverrideSide: String? = null,
    val alignmentConvention: AlignmentDirectionConvention = AlignmentDirectionConvention.AUTO_FROM_ALIGNMENT,
    val voiceDirectionAnnouncementsEnabled: Boolean = true,
    val manualSelectedAtMs: Long = 0L
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

    /**
     * Authoritative internal direction lock mode string:
     * - "MANUAL_LHS" when user manually locked LHS
     * - "MANUAL_RHS" when user manually locked RHS
     * - "MANUAL_BHS" / "MANUAL_MEDIAN" / "MANUAL_NONE" for other explicit manual selections
     * - "AUTO" ONLY when user explicitly selected AUTO mode
     */
    val internalDirectionMode: String
        get() {
            if (controlMode != DirectionControlMode.MANUAL_OVERRIDE) return MODE_AUTO
            return when (normalizedManualSide) {
                "LHS" -> MODE_MANUAL_LHS
                "RHS" -> MODE_MANUAL_RHS
                "BHS" -> MODE_MANUAL_BHS
                "MEDIAN" -> MODE_MANUAL_MEDIAN
                "NONE" -> MODE_MANUAL_NONE
                else -> MODE_AUTO
            }
        }

    fun toJsonString(): String {
        return JSONObject().apply {
            put("controlMode", controlMode.id)
            put("internalDirectionMode", internalDirectionMode)
            put("manualOverrideSide", normalizedManualSide ?: "")
            put("alignmentConvention", alignmentConvention.id)
            put("voiceDirectionAnnouncementsEnabled", voiceDirectionAnnouncementsEnabled)
            put("manualSelectedAtMs", if (controlMode == DirectionControlMode.MANUAL_OVERRIDE) manualSelectedAtMs else 0L)
        }.toString()
    }

    companion object {
        const val MODE_MANUAL_LHS = "MANUAL_LHS"
        const val MODE_MANUAL_RHS = "MANUAL_RHS"
        const val MODE_MANUAL_BHS = "MANUAL_BHS"
        const val MODE_MANUAL_MEDIAN = "MANUAL_MEDIAN"
        const val MODE_MANUAL_NONE = "MANUAL_NONE"
        const val MODE_AUTO = "AUTO"

        fun fromJsonString(raw: String?): SmartDirectionPreferences {
            if (raw.isNullOrBlank()) return SmartDirectionPreferences()
            return try {
                val obj = JSONObject(raw)
                val storedInternalMode = obj.optString("internalDirectionMode", "").trim().uppercase(Locale.ROOT)
                val (effectiveMode, effectiveManualSide) = when (storedInternalMode) {
                    MODE_MANUAL_LHS -> DirectionControlMode.MANUAL_OVERRIDE to "LHS"
                    MODE_MANUAL_RHS -> DirectionControlMode.MANUAL_OVERRIDE to "RHS"
                    MODE_MANUAL_BHS -> DirectionControlMode.MANUAL_OVERRIDE to "BHS"
                    MODE_MANUAL_MEDIAN -> DirectionControlMode.MANUAL_OVERRIDE to "MEDIAN"
                    MODE_MANUAL_NONE -> DirectionControlMode.MANUAL_OVERRIDE to "NONE"
                    MODE_AUTO -> DirectionControlMode.AUTO to null
                    else -> {
                        val mode = DirectionControlMode.fromId(obj.optString("controlMode", DirectionControlMode.AUTO.id))
                        val rawManual = obj.optString("manualOverrideSide", "").trim().takeIf { it.isNotBlank() }
                        if (mode == DirectionControlMode.MANUAL_OVERRIDE && rawManual != null) {
                            DirectionControlMode.MANUAL_OVERRIDE to rawManual
                        } else {
                            DirectionControlMode.AUTO to null
                        }
                    }
                }
                val selectedAt = obj.optLong("manualSelectedAtMs", 0L).coerceAtLeast(0L)
                SmartDirectionPreferences(
                    controlMode = effectiveMode,
                    manualOverrideSide = effectiveManualSide,
                    alignmentConvention = AlignmentDirectionConvention.fromId(
                        obj.optString("alignmentConvention", AlignmentDirectionConvention.AUTO_FROM_ALIGNMENT.id)
                    ),
                    voiceDirectionAnnouncementsEnabled = obj.optBoolean("voiceDirectionAnnouncementsEnabled", true),
                    manualSelectedAtMs = if (effectiveMode == DirectionControlMode.MANUAL_OVERRIDE) selectedAt else 0L
                )
            } catch (_: Exception) {
                SmartDirectionPreferences()
            }
        }
    }
}

/**
 * Immutable, authoritative application-wide LHS/RHS direction snapshot published by
 * the single centralized [com.example.navigation.SmartDirectionManager].
 *
 * Strictly guarantees:
 * - [confirmedSide] is non-null ONLY when valid KMZ/KML offset evidence exists (or active manual override).
 * - [isIncreasingChainage] is resolved from [confirmedSide] + [effectiveConvention].
 * - [directionChangedFrom] is populated ONLY on the exact update where a confirmed direction transition completes.
 * - [autoReactivatedReason] is populated on the update where manual override expires or is overridden by >= 5 km/h speed.
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
    val directionChangedFrom: String? = null,
    val autoReactivatedReason: DirectionAutoReactivationReason? = null,
    val signedLateralOffsetMetres: Double? = null
) {
    /**
     * Strictly returns "LHS" or "RHS" only when a binary highway direction is confirmed (or manually set).
     */
    val confirmedBinarySide: String?
        get() = confirmedSide?.trim()?.uppercase(Locale.ROOT)?.takeIf { it == "LHS" || it == "RHS" }

    /**
     * Authoritative internal direction lock mode string ("MANUAL_LHS", "MANUAL_RHS", "AUTO", etc.).
     */
    val internalDirectionMode: String
        get() {
            if (!isManualOverride) return SmartDirectionPreferences.MODE_AUTO
            return when (confirmedSide?.trim()?.uppercase(Locale.ROOT)) {
                "LHS" -> SmartDirectionPreferences.MODE_MANUAL_LHS
                "RHS" -> SmartDirectionPreferences.MODE_MANUAL_RHS
                "BHS", "BOTH" -> SmartDirectionPreferences.MODE_MANUAL_BHS
                "MEDIAN" -> SmartDirectionPreferences.MODE_MANUAL_MEDIAN
                "NONE" -> SmartDirectionPreferences.MODE_MANUAL_NONE
                else -> SmartDirectionPreferences.MODE_AUTO
            }
        }

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
            detailReason = "Load a KMZ/KML road alignment to detect LHS/RHS from route offset"
        )
    }
}
