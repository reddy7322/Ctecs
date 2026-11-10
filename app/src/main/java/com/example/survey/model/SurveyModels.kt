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

enum class SurveyLane(val label: String, val defaultOffsetMetres: Double) {
    MCW_FAST("MCW Fast Lane", 7.0),
    MCW_SLOW("MCW Slow Lane", 3.5),
    MCW_INNER("MCW Inner Lane", 3.5),
    MCW_OUTER("MCW Outer Lane", 5.25),
    SERVICE_ROAD("Service Road", 14.0),
    SLIP_ROAD("Slip Road", 10.5),
    CUSTOM("Other / Custom Lane", 0.0)
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
    val currentChainageMetres: Long = 0L,
    val direction: SurveyDirection = SurveyDirection.INCREASING,
    val lane: SurveyLane = SurveyLane.MCW_FAST,
    val customLaneName: String = "",
    val centrelineOffsetMetres: Double = 7.0,
    val offsetSide: OffsetSide = OffsetSide.RIGHT,
    val isOffsetAuto: Boolean = false,
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

    val displayStartChainage: String
        get() = Chainage(startChainageMetres).format()

    val displayCurrentChainage: String
        get() = Chainage(currentChainageMetres).format()
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
    val markerId: String,
    val measuredDistanceMetres: Double,
    val knownChainageMetres: Long,
    val driftMetres: Double,
    val timestamp: Long = System.currentTimeMillis()
)
