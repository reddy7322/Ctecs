package com.example.survey.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.survey.model.KmMarkerType
import com.example.survey.model.OffsetSide
import com.example.survey.model.RoadSide
import com.example.survey.model.SurveyCalibrationPoint
import com.example.survey.model.SurveyDirection
import com.example.survey.model.SurveyLane
import com.example.survey.model.SurveyMarker
import com.example.survey.model.SurveyPoint
import com.example.survey.model.SurveySession
import com.example.survey.model.SurveyStatus

@Entity(tableName = "survey_sessions")
data class SurveySessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val projectName: String,
    val highwayName: String,
    val roadNumber: String,
    val stretchName: String,
    val surveyDate: String,
    val startChainageMetres: Long,
    val plannedEndChainageMetres: Long? = null,
    val currentChainageMetres: Long,
    val direction: String,
    val roadSide: String = "RHS",
    val lane: String,
    val customLaneName: String,
    val centrelineOffsetMetres: Double,
    val offsetSide: String,
    val isOffsetAuto: Boolean,
    val confirmationIntervalMetres: Long? = 2000L,
    val lastConfirmedDistanceM: Double = 0.0,
    val status: String,
    val totalDistanceTravelledMetres: Double,
    val currentSegmentIndex: Int,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val lastAltitude: Double?,
    val lastSpeedMps: Float?,
    val lastBearing: Float?,
    val lastAccuracy: Float?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSavedAt: Long
) {
    fun toDomain(): SurveySession {
        return SurveySession(
            id = id,
            name = name,
            projectName = projectName,
            highwayName = highwayName,
            roadNumber = roadNumber,
            stretchName = stretchName,
            surveyDate = surveyDate,
            startChainageMetres = startChainageMetres,
            plannedEndChainageMetres = plannedEndChainageMetres,
            currentChainageMetres = currentChainageMetres,
            direction = try { SurveyDirection.valueOf(direction) } catch (e: Exception) { SurveyDirection.INCREASING },
            roadSide = try { RoadSide.valueOf(roadSide) } catch (e: Exception) { RoadSide.RHS },
            lane = try { SurveyLane.valueOf(lane) } catch (e: Exception) { SurveyLane.FAST },
            customLaneName = customLaneName,
            centrelineOffsetMetres = centrelineOffsetMetres,
            offsetSide = try { OffsetSide.valueOf(offsetSide) } catch (e: Exception) { OffsetSide.RIGHT },
            isOffsetAuto = isOffsetAuto,
            confirmationIntervalMetres = confirmationIntervalMetres,
            lastConfirmedDistanceM = lastConfirmedDistanceM,
            status = try { SurveyStatus.valueOf(status) } catch (e: Exception) { SurveyStatus.NOT_STARTED },
            totalDistanceTravelledMetres = totalDistanceTravelledMetres,
            currentSegmentIndex = currentSegmentIndex,
            lastLatitude = lastLatitude,
            lastLongitude = lastLongitude,
            lastAltitude = lastAltitude,
            lastSpeedMps = lastSpeedMps,
            lastBearing = lastBearing,
            lastAccuracy = lastAccuracy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastSavedAt = lastSavedAt
        )
    }

    companion object {
        fun fromDomain(s: SurveySession): SurveySessionEntity {
            return SurveySessionEntity(
                id = s.id,
                name = s.name,
                projectName = s.projectName,
                highwayName = s.highwayName,
                roadNumber = s.roadNumber,
                stretchName = s.stretchName,
                surveyDate = s.surveyDate,
                startChainageMetres = s.startChainageMetres,
                plannedEndChainageMetres = s.plannedEndChainageMetres,
                currentChainageMetres = s.currentChainageMetres,
                direction = s.direction.name,
                roadSide = s.roadSide.name,
                lane = s.lane.name,
                customLaneName = s.customLaneName,
                centrelineOffsetMetres = s.centrelineOffsetMetres,
                offsetSide = s.offsetSide.name,
                isOffsetAuto = s.isOffsetAuto,
                confirmationIntervalMetres = s.confirmationIntervalMetres,
                lastConfirmedDistanceM = s.lastConfirmedDistanceM,
                status = s.status.name,
                totalDistanceTravelledMetres = s.totalDistanceTravelledMetres,
                currentSegmentIndex = s.currentSegmentIndex,
                lastLatitude = s.lastLatitude,
                lastLongitude = s.lastLongitude,
                lastAltitude = s.lastAltitude,
                lastSpeedMps = s.lastSpeedMps,
                lastBearing = s.lastBearing,
                lastAccuracy = s.lastAccuracy,
                createdAt = s.createdAt,
                updatedAt = s.updatedAt,
                lastSavedAt = s.lastSavedAt
            )
        }
    }
}

@Entity(
    tableName = "survey_points",
    indices = [Index(value = ["sessionId", "timestamp"])]
)
data class SurveyPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val segmentIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speedMps: Float,
    val bearing: Float,
    val calculatedChainageMetres: Long,
    val offsetMetres: Double,
    val offsetSide: String,
    val roadSide: String = "RHS",
    val lane: String,
    val timestamp: Long,
    val isRelocationBoundary: Boolean
) {
    fun toDomain(): SurveyPoint = SurveyPoint(
        id = id,
        sessionId = sessionId,
        segmentIndex = segmentIndex,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        accuracy = accuracy,
        speedMps = speedMps,
        bearing = bearing,
        calculatedChainageMetres = calculatedChainageMetres,
        offsetMetres = offsetMetres,
        offsetSide = offsetSide,
        roadSide = roadSide,
        lane = lane,
        timestamp = timestamp,
        isRelocationBoundary = isRelocationBoundary
    )

    companion object {
        fun fromDomain(p: SurveyPoint): SurveyPointEntity = SurveyPointEntity(
            id = p.id,
            sessionId = p.sessionId,
            segmentIndex = p.segmentIndex,
            latitude = p.latitude,
            longitude = p.longitude,
            altitude = p.altitude,
            accuracy = p.accuracy,
            speedMps = p.speedMps,
            bearing = p.bearing,
            calculatedChainageMetres = p.calculatedChainageMetres,
            offsetMetres = p.offsetMetres,
            offsetSide = p.offsetSide,
            roadSide = p.roadSide,
            lane = p.lane,
            timestamp = p.timestamp,
            isRelocationBoundary = p.isRelocationBoundary
        )
    }
}

@Entity(
    tableName = "survey_markers",
    indices = [Index(value = ["sessionId", "chainageMetres"])]
)
data class SurveyMarkerEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val chainageMetres: Long,
    val displayChainage: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val markerType: String,
    val roadSide: String = "RHS",
    val lane: String,
    val direction: String,
    val offsetMetres: Double,
    val offsetSide: String,
    val isManuallyCalibrated: Boolean,
    val timestamp: Long,
    val notes: String
) {
    fun toDomain(): SurveyMarker = SurveyMarker(
        id = id,
        sessionId = sessionId,
        chainageMetres = chainageMetres,
        displayChainage = displayChainage,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        accuracy = accuracy,
        markerType = try { KmMarkerType.valueOf(markerType) } catch (e: Exception) { KmMarkerType.FULL_KM },
        roadSide = roadSide,
        lane = lane,
        direction = direction,
        offsetMetres = offsetMetres,
        offsetSide = offsetSide,
        isManuallyCalibrated = isManuallyCalibrated,
        timestamp = timestamp,
        notes = notes
    )

    companion object {
        fun fromDomain(m: SurveyMarker): SurveyMarkerEntity = SurveyMarkerEntity(
            id = m.id,
            sessionId = m.sessionId,
            chainageMetres = m.chainageMetres,
            displayChainage = m.displayChainage,
            latitude = m.latitude,
            longitude = m.longitude,
            altitude = m.altitude,
            accuracy = m.accuracy,
            markerType = m.markerType.name,
            roadSide = m.roadSide,
            lane = m.lane,
            direction = m.direction,
            offsetMetres = m.offsetMetres,
            offsetSide = m.offsetSide,
            isManuallyCalibrated = m.isManuallyCalibrated,
            timestamp = m.timestamp,
            notes = m.notes
        )
    }
}

@Entity(
    tableName = "survey_calibrations",
    indices = [Index(value = ["sessionId"])]
)
data class SurveyCalibrationEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val markerId: String = "",
    val checkpointName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val measuredDistanceMetres: Double,
    val originalChainageMetres: Long = 0L,
    val knownChainageMetres: Long,
    val driftMetres: Double,
    val timestamp: Long
) {
    fun toDomain(): SurveyCalibrationPoint = SurveyCalibrationPoint(
        id = id,
        sessionId = sessionId,
        markerId = markerId,
        checkpointName = checkpointName,
        latitude = latitude,
        longitude = longitude,
        measuredDistanceMetres = measuredDistanceMetres,
        originalChainageMetres = originalChainageMetres,
        knownChainageMetres = knownChainageMetres,
        driftMetres = driftMetres,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(c: SurveyCalibrationPoint): SurveyCalibrationEntity = SurveyCalibrationEntity(
            id = c.id,
            sessionId = c.sessionId,
            markerId = c.markerId,
            checkpointName = c.checkpointName,
            latitude = c.latitude,
            longitude = c.longitude,
            measuredDistanceMetres = c.measuredDistanceMetres,
            originalChainageMetres = c.originalChainageMetres,
            knownChainageMetres = c.knownChainageMetres,
            driftMetres = c.driftMetres,
            timestamp = c.timestamp
        )
    }
}
