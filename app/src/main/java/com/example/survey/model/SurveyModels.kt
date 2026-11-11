package com.example.survey.model

import com.example.model.Chainage
import com.example.model.GeoPoint
import java.util.UUID

enum class SurveyStatus(val label: String) {
    NOT_STARTED("Not Started"),
    RECORDING("Recording"),
    PAUSED("Paused"),
    INTERRUPTED("Interrupted"),
    READY_TO_RESUME("Ready to Resume"),
    COMPLETED("Completed")
}

enum class RoadSide(val label: String, val shortCode: String) {
    AUTO("Auto", "AUTO"),
    LHS("LHS", "LHS"),
    RHS("RHS", "RHS"),
    BHS("BHS", "BHS");

    fun toOffsetSide(): OffsetSide = when (this) {
        LHS -> OffsetSide.LEFT
        RHS -> OffsetSide.RIGHT
        BHS -> OffsetSide.CENTRE
        AUTO -> OffsetSide.RIGHT
    }
}

enum class SurveyLane(val label: String, val defaultOffsetMetres: Double) {
    AUTO("Auto", 7.0),
    INNER("Inner Lane", 3.5),
    OUTER("Outer Lane", 5.25),
    FAST("Fast Lane", 7.0),
    SLOW("Slow Lane", 3.5),
    SERVICE_ROAD("Service Road", 14.0),
    CUSTOM("Other / Custom", 0.0),

    // Backward compatibility aliases
    MCW_FAST("Fast Lane", 7.0),
    MCW_SLOW("Slow Lane", 3.5),
    MCW_INNER("Inner Lane", 3.5),
    MCW_OUTER("Outer Lane", 5.25),
    SLIP_ROAD("Slip Road", 10.5)
}

enum class SurveyDirection(val label: String) {
    INCREASING("Increasing Chainage"),
    DECREASING("Decreasing Chainage"),
    CUSTOM("Custom Direction")
}

enum class OffsetSide(val label: String) {
    CENTRE("Centreline (0m)"),
    LEFT("LHS (Left of CL)"),
    RIGHT("RHS (Right of CL)")
}

enum class ConfirmationIntervalOption(val label: String, val intervalMetres: Long?) {
    EVERY_1_KM("Every 1 KM", 1000L),
    EVERY_2_KM("Every 2 KM (Default)", 2000L),
    EVERY_3_KM("Every 3 KM", 3000L),
    EVERY_5_KM("Every 5 KM", 5000L),
    CUSTOM("Custom Interval", null),
    OFF("Off", null);

    companion object {
        fun fromMetres(m: Long?): ConfirmationIntervalOption {
            return when (m) {
                null -> OFF
                1000L -> EVERY_1_KM
                2000L -> EVERY_2_KM
                3000L -> EVERY_3_KM
                5000L -> EVERY_5_KM
                else -> CUSTOM
            }
        }
    }
}

enum class KmMarkerType(val label: String, val shortBadge: String) {
    FULL_KM("Full KM Stone", "1 KM"),
    HALF_KM("Half-KM Stone (500m)", "0.5 KM"),
    QUARTER_KM("Quarter-KM Marker (250m)", "250m"),
    CUSTOM_CHAINAGE("Custom Chainage Marker", "CH"),
    SURVEY_POINT("Survey Reference Point", "REF")
}

data class SurveySession(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Highway Survey",
    val projectName: String = "",
    val highwayName: String = "",
    val roadNumber: String = "",
    val stretchName: String = "",
    val surveyDate: String = "",
    val startChainageMetres: Long = 0L,
    val plannedEndChainageMetres: Long? = null,
    val currentChainageMetres: Long = 0L,
    val direction: SurveyDirection = SurveyDirection.INCREASING,
    val roadSide: RoadSide = RoadSide.RHS,
    val lane: SurveyLane = SurveyLane.FAST,
    val customLaneName: String = "",
    val centrelineOffsetMetres: Double = 7.0,
    val offsetSide: OffsetSide = OffsetSide.RIGHT,
    val isOffsetAuto: Boolean = false,
    val confirmationIntervalMetres: Long? = 2000L,
    val lastConfirmedDistanceM: Double = 0.0,
    val status: SurveyStatus = SurveyStatus.NOT_STARTED,
    val totalDistanceTravelledMetres: Double = 0.0,
    val currentSegmentIndex: Int = 0,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastAltitude: Double? = null,
    val lastSpeedMps: Float? = null,
    val lastBearing: Float? = null,
    val lastAccuracy: Float? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSavedAt: Long = System.currentTimeMillis()
) {
    val displayLaneName: String
        get() = if (lane == SurveyLane.CUSTOM && customLaneName.isNotBlank()) customLaneName else lane.label

    val displayRoadSide: String
        get() = roadSide.label

    val displayStartChainage: String
        get() = Chainage(startChainageMetres).format()

    val displayCurrentChainage: String
        get() = Chainage(currentChainageMetres).format()

    val displayPlannedEndChainage: String
        get() = plannedEndChainageMetres?.let { Chainage(it).format() } ?: "None"

    val hasPlannedEndChainage: Boolean
        get() = plannedEndChainageMetres != null

    val plannedSurveyLengthMetres: Long?
        get() = plannedEndChainageMetres?.let { kotlin.math.abs(it - startChainageMetres) }

    val remainingDistanceMetres: Long?
        get() {
            val end = plannedEndChainageMetres ?: return null
            return when (direction) {
                SurveyDirection.INCREASING -> (end - currentChainageMetres).coerceAtLeast(0L)
                SurveyDirection.DECREASING -> (currentChainageMetres - end).coerceAtLeast(0L)
                SurveyDirection.CUSTOM -> kotlin.math.abs(end - currentChainageMetres)
            }
        }
}

data class SurveyPoint(
    val id: Long = 0,
    val sessionId: String,
    val segmentIndex: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speedMps: Float = 0f,
    val bearing: Float = 0f,
    val calculatedChainageMetres: Long = 0L,
    val offsetMetres: Double = 0.0,
    val offsetSide: String = "RIGHT",
    val roadSide: String = "RHS",
    val lane: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRelocationBoundary: Boolean = false
) {
    val geoPoint: GeoPoint get() = GeoPoint(latitude, longitude, altitude)
    val displayChainage: String get() = Chainage(calculatedChainageMetres).format()
}

data class SurveyMarker(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val chainageMetres: Long,
    val displayChainage: String = Chainage(chainageMetres).format(),
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val markerType: KmMarkerType = KmMarkerType.FULL_KM,
    val roadSide: String = "RHS",
    val lane: String = "",
    val direction: String = "",
    val offsetMetres: Double = 0.0,
    val offsetSide: String = "RIGHT",
    val isManuallyCalibrated: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
) {
    val geoPoint: GeoPoint get() = GeoPoint(latitude, longitude, altitude)
}

data class SurveyCalibrationPoint(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val markerId: String = "",
    val checkpointName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val measuredDistanceMetres: Double,
    val originalChainageMetres: Long = 0L,
    val knownChainageMetres: Long, // Corrected Chainage
    val driftMetres: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class CentrelineChainageLabel(
    val chainageMetres: Long,
    val displayChainage: String,
    val position: GeoPoint,
    val bearingDeg: Double
)

data class CheckpointPrompt(
    val checkpointIndex: Int,
    val targetDistanceM: Double,
    val calculatedChainageMetres: Long,
    val checkpointChainageMetres: Long,
    val totalSurveyedDistanceM: Double,
    val roadSide: String,
    val lane: String,
    val currentGps: GeoPoint?
)
