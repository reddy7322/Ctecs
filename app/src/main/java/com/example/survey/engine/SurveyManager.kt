package com.example.survey.engine

import android.content.Context
import com.example.geo.GeoUtils
import com.example.model.Chainage
import com.example.model.GeoPoint
import com.example.survey.data.SaveStatus
import com.example.survey.data.SurveyRepository
import com.example.survey.model.CheckpointPrompt
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

data class RelocationState(
    val lastPoint: GeoPoint,
    val currentPoint: GeoPoint,
    val distanceM: Double,
    val bearingDeg: Double
)

class SurveyManager(
    private val context: Context,
    val repository: SurveyRepository,
    private val scope: CoroutineScope
) {
    val activeSession: StateFlow<SurveySession?> = repository.activeSession
    val saveStatus: StateFlow<SaveStatus> = repository.saveStatus

    private val _relocationPrompt = MutableStateFlow<RelocationState?>(null)
    val relocationPrompt: StateFlow<RelocationState?> = _relocationPrompt.asStateFlow()

    private val _checkpointPrompt = MutableStateFlow<CheckpointPrompt?>(null)
    val checkpointPrompt: StateFlow<CheckpointPrompt?> = _checkpointPrompt.asStateFlow()

    private val _endingChainageReachedPrompt = MutableStateFlow<Boolean>(false)
    val endingChainageReachedPrompt: StateFlow<Boolean> = _endingChainageReachedPrompt.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activePoints: StateFlow<List<SurveyPoint>> = repository.activeSession.flatMapLatest { session ->
        if (session != null) repository.getPointsFlow(session.id) else flowOf(emptyList())
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMarkers: StateFlow<List<SurveyMarker>> = repository.activeSession.flatMapLatest { session ->
        if (session != null) repository.getMarkersFlow(session.id) else flowOf(emptyList())
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun startNewSurvey(session: SurveySession): SurveySession = withContext(Dispatchers.IO) {
        val created = repository.createNewSession(session.copy(status = SurveyStatus.RECORDING))
        _relocationPrompt.value = null
        _checkpointPrompt.value = null
        _endingChainageReachedPrompt.value = false
        created
    }

    suspend fun pauseSurvey() = withContext(Dispatchers.IO) {
        val current = activeSession.value ?: return@withContext
        val updated = current.copy(status = SurveyStatus.PAUSED)
        repository.updateSession(updated)
    }

    fun requestResumeSurvey(currentGps: GeoPoint?) {
        val current = activeSession.value ?: return
        val lastLat = current.lastLatitude
        val lastLng = current.lastLongitude

        if (currentGps != null && lastLat != null && lastLng != null) {
            val lastPoint = GeoPoint(lastLat, lastLng, current.lastAltitude ?: 0.0)
            val dist = GeoUtils.distanceMetres(lastPoint, currentGps)
            if (dist > 40.0) {
                val bearing = GeoUtils.bearingDegrees(lastPoint, currentGps)
                _relocationPrompt.value = RelocationState(
                    lastPoint = lastPoint,
                    currentPoint = currentGps,
                    distanceM = dist,
                    bearingDeg = bearing
                )
                return
            }
        }

        // Distance <= 40m or no previous point, resume directly
        scope.launch(Dispatchers.IO) {
            repository.updateSession(current.copy(status = SurveyStatus.RECORDING))
        }
    }

    fun resolveRelocation(startNewSegment: Boolean) {
        val current = activeSession.value ?: return
        val prompt = _relocationPrompt.value
        _relocationPrompt.value = null

        scope.launch(Dispatchers.IO) {
            val updated = if (startNewSegment) {
                current.copy(
                    status = SurveyStatus.RECORDING,
                    currentSegmentIndex = current.currentSegmentIndex + 1,
                    lastLatitude = prompt?.currentPoint?.latitude ?: current.lastLatitude,
                    lastLongitude = prompt?.currentPoint?.longitude ?: current.lastLongitude
                )
            } else {
                current.copy(status = SurveyStatus.RECORDING)
            }
            repository.updateSession(updated)
        }
    }

    fun dismissRelocation() {
        _relocationPrompt.value = null
    }

    suspend fun stopSurvey(): SurveySession? = withContext(Dispatchers.IO) {
        val current = activeSession.value ?: return@withContext null
        _checkpointPrompt.value = null
        _endingChainageReachedPrompt.value = false
        val finalized = current.copy(
            status = SurveyStatus.COMPLETED,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateSession(finalized)
        finalized
    }

    fun processLocationUpdate(point: GeoPoint, accuracy: Float, speedMps: Float, bearing: Float) {
        val session = activeSession.value ?: return
        if (session.status != SurveyStatus.RECORDING) return

        val lastPt = if (session.lastLatitude != null && session.lastLongitude != null) {
            GeoPoint(session.lastLatitude, session.lastLongitude, session.lastAltitude ?: 0.0)
        } else null

        // Accuracy & jitter filter
        if (!SurveyGeometryEngine.isMovementReliable(lastPt, point, accuracy, speedMps)) {
            return
        }

        val stepDistance = if (lastPt != null) GeoUtils.distanceMetres(lastPt, point) else 0.0
        val newTotalDist = session.totalDistanceTravelledMetres + stepDistance

        scope.launch(Dispatchers.IO) {
            val calibrations = repository.getCalibrationsSync(session.id)
            val calculatedChainage = SurveyGeometryEngine.calculateChainageMetres(
                startChainageMetres = session.startChainageMetres,
                distanceTravelledM = newTotalDist,
                direction = session.direction,
                calibrations = calibrations
            )

            val surveyPoint = SurveyPoint(
                sessionId = session.id,
                segmentIndex = session.currentSegmentIndex,
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude,
                accuracy = accuracy,
                speedMps = speedMps,
                bearing = bearing,
                calculatedChainageMetres = calculatedChainage,
                offsetMetres = session.centrelineOffsetMetres,
                offsetSide = session.offsetSide.name,
                roadSide = session.roadSide.name,
                lane = session.displayLaneName,
                timestamp = System.currentTimeMillis(),
                isRelocationBoundary = (session.currentSegmentIndex > 0 && lastPt == null)
            )

            repository.savePoint(surveyPoint)

            val updatedSession = session.copy(
                currentChainageMetres = calculatedChainage,
                totalDistanceTravelledMetres = newTotalDist,
                lastLatitude = point.latitude,
                lastLongitude = point.longitude,
                lastAltitude = point.altitude,
                lastSpeedMps = speedMps,
                lastBearing = bearing,
                lastAccuracy = accuracy,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateSession(updatedSession)

            // ------------------------------------------------------------------
            // 1. Configurable Chainage Confirmation Checkpoint Trigger
            // Triggered based strictly on actual accumulated surveyed distance
            // ------------------------------------------------------------------
            val interval = session.confirmationIntervalMetres
            if (interval != null && interval > 0 && _checkpointPrompt.value == null) {
                val nextCheckpointDist = (((session.lastConfirmedDistanceM / interval).toLong() + 1) * interval).toDouble()
                if (newTotalDist >= nextCheckpointDist) {
                    val checkpointCh = when (session.direction) {
                        SurveyDirection.INCREASING -> session.startChainageMetres + nextCheckpointDist.toLong()
                        SurveyDirection.DECREASING -> session.startChainageMetres - nextCheckpointDist.toLong()
                        SurveyDirection.CUSTOM -> session.startChainageMetres + nextCheckpointDist.toLong()
                    }
                    val prompt = CheckpointPrompt(
                        checkpointIndex = (nextCheckpointDist / interval).toInt(),
                        targetDistanceM = nextCheckpointDist,
                        calculatedChainageMetres = calculatedChainage,
                        checkpointChainageMetres = checkpointCh,
                        totalSurveyedDistanceM = newTotalDist,
                        roadSide = session.roadSide.label,
                        lane = session.displayLaneName,
                        currentGps = point
                    )
                    _checkpointPrompt.value = prompt
                }
            }

            // ------------------------------------------------------------------
            // 2. Planned Ending Chainage Reached Check
            // Shows non-blocking alert with Finish / Continue. NEVER stops automatically!
            // ------------------------------------------------------------------
            val plannedEnd = session.plannedEndChainageMetres
            if (plannedEnd != null && !_endingChainageReachedPrompt.value) {
                val reached = when (session.direction) {
                    SurveyDirection.INCREASING -> calculatedChainage >= plannedEnd
                    SurveyDirection.DECREASING -> calculatedChainage <= plannedEnd
                    SurveyDirection.CUSTOM -> abs(calculatedChainage - session.startChainageMetres) >= abs(plannedEnd - session.startChainageMetres)
                }
                if (reached) {
                    _endingChainageReachedPrompt.value = true
                }
            }
        }
    }

    /**
     * Confirms or edits chainage at a distance checkpoint.
     * STRICT DATA INTEGRITY:
     * - Coordinates (lat, lon, altitude) are NEVER changed.
     * - The actual recorded GPS points and centreline remain 100% untouched.
     * - Modifies only the calibration / chainage reference value.
     * - Stores Original Chainage and Corrected Chainage separately in audit log.
     */
    suspend fun confirmCheckpoint(
        prompt: CheckpointPrompt,
        correctedChainageMetres: Long? = null
    ) = withContext(Dispatchers.IO) {
        val session = activeSession.value ?: return@withContext
        _checkpointPrompt.value = null

        val effectiveTargetDist = prompt.targetDistanceM
        val effectiveCh = correctedChainageMetres ?: prompt.calculatedChainageMetres
        val isCorrected = correctedChainageMetres != null && correctedChainageMetres != prompt.calculatedChainageMetres

        if (isCorrected) {
            val drift = (correctedChainageMetres!! - prompt.calculatedChainageMetres).toDouble()
            val cal = SurveyCalibrationPoint(
                sessionId = session.id,
                markerId = "CP_${prompt.checkpointIndex}",
                checkpointName = "Checkpoint #${prompt.checkpointIndex} (${String.format(Locale.US, "%.0f km", prompt.targetDistanceM / 1000.0)})",
                latitude = prompt.currentGps?.latitude ?: session.lastLatitude ?: 0.0,
                longitude = prompt.currentGps?.longitude ?: session.lastLongitude ?: 0.0,
                measuredDistanceMetres = session.totalDistanceTravelledMetres,
                originalChainageMetres = prompt.calculatedChainageMetres,
                knownChainageMetres = correctedChainageMetres,
                driftMetres = drift,
                timestamp = System.currentTimeMillis()
            )
            repository.saveCalibration(cal)
        }

        val updated = session.copy(
            currentChainageMetres = effectiveCh,
            lastConfirmedDistanceM = effectiveTargetDist,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateSession(updated)
    }

    fun dismissEndingChainagePrompt() {
        _endingChainageReachedPrompt.value = false
    }

    suspend fun updateRoadSide(newSide: RoadSide) = withContext(Dispatchers.IO) {
        val session = activeSession.value ?: return@withContext
        val newOffsetSide = newSide.toOffsetSide()
        val updated = session.copy(
            roadSide = newSide,
            offsetSide = newOffsetSide,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateSession(updated)
    }

    suspend fun updateLane(newLane: SurveyLane, customLaneName: String = "") = withContext(Dispatchers.IO) {
        val session = activeSession.value ?: return@withContext
        val newOffsetM = if (newLane != SurveyLane.CUSTOM && newLane != SurveyLane.AUTO) newLane.defaultOffsetMetres else session.centrelineOffsetMetres
        val updated = session.copy(
            lane = newLane,
            customLaneName = customLaneName,
            centrelineOffsetMetres = newOffsetM,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateSession(updated)
    }

    suspend fun updateConfirmationInterval(newIntervalM: Long?) = withContext(Dispatchers.IO) {
        val session = activeSession.value ?: return@withContext
        val updated = session.copy(
            confirmationIntervalMetres = newIntervalM,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateSession(updated)
    }

    suspend fun updatePlannedEndChainage(plannedEndM: Long?) = withContext(Dispatchers.IO) {
        val session = activeSession.value ?: return@withContext
        val updated = session.copy(
            plannedEndChainageMetres = plannedEndM,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateSession(updated)
    }

    suspend fun addKmStoneMarker(
        point: GeoPoint,
        accuracy: Float,
        markerType: KmMarkerType,
        confirmedChainageMetres: Long? = null,
        notes: String = ""
    ): SurveyMarker = withContext(Dispatchers.IO) {
        val session = activeSession.value ?: throw IllegalStateException("No active survey session")
        val effectiveChainage = confirmedChainageMetres ?: session.currentChainageMetres
        val isCalibrated = confirmedChainageMetres != null && confirmedChainageMetres != session.currentChainageMetres

        val marker = SurveyMarker(
            sessionId = session.id,
            chainageMetres = effectiveChainage,
            displayChainage = Chainage(effectiveChainage).format(),
            latitude = point.latitude,
            longitude = point.longitude,
            altitude = point.altitude,
            accuracy = accuracy,
            markerType = markerType,
            roadSide = session.roadSide.name,
            lane = session.displayLaneName,
            direction = session.direction.label,
            offsetMetres = session.centrelineOffsetMetres,
            offsetSide = session.offsetSide.name,
            isManuallyCalibrated = isCalibrated,
            timestamp = System.currentTimeMillis(),
            notes = notes
        )

        repository.saveMarker(marker)

        if (isCalibrated) {
            val drift = (confirmedChainageMetres!! - session.currentChainageMetres).toDouble()
            val cal = SurveyCalibrationPoint(
                sessionId = session.id,
                markerId = marker.id,
                checkpointName = "KM Stone Marker (${Chainage(confirmedChainageMetres).format()})",
                latitude = point.latitude,
                longitude = point.longitude,
                measuredDistanceMetres = session.totalDistanceTravelledMetres,
                originalChainageMetres = session.currentChainageMetres,
                knownChainageMetres = confirmedChainageMetres,
                driftMetres = drift,
                timestamp = System.currentTimeMillis()
            )
            repository.saveCalibration(cal)

            val updated = session.copy(currentChainageMetres = confirmedChainageMetres)
            repository.updateSession(updated)
        }

        marker
    }

    suspend fun updateCentrelineOffset(newOffsetM: Double, newSide: OffsetSide, isAuto: Boolean = false) = withContext(Dispatchers.IO) {
        val session = activeSession.value ?: return@withContext
        val updated = session.copy(
            centrelineOffsetMetres = newOffsetM,
            offsetSide = newSide,
            isOffsetAuto = isAuto,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateSession(updated)
    }

    suspend fun updateSessionMetadata(
        name: String,
        highwayName: String,
        stretchName: String,
        startChainageMetres: Long,
        plannedEndChainageMetres: Long? = activeSession.value?.plannedEndChainageMetres,
        direction: SurveyDirection = activeSession.value?.direction ?: SurveyDirection.INCREASING,
        roadSide: RoadSide = activeSession.value?.roadSide ?: RoadSide.RHS,
        lane: SurveyLane = activeSession.value?.lane ?: SurveyLane.FAST,
        customLaneName: String = activeSession.value?.customLaneName ?: "",
        confirmationIntervalMetres: Long? = activeSession.value?.confirmationIntervalMetres
    ) = withContext(Dispatchers.IO) {
        val session = activeSession.value ?: return@withContext
        val updated = session.copy(
            name = name,
            highwayName = highwayName,
            stretchName = stretchName,
            startChainageMetres = startChainageMetres,
            plannedEndChainageMetres = plannedEndChainageMetres,
            direction = direction,
            roadSide = roadSide,
            offsetSide = roadSide.toOffsetSide(),
            lane = lane,
            customLaneName = customLaneName,
            confirmationIntervalMetres = confirmationIntervalMetres,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateSession(updated)
    }

    suspend fun deleteMarker(markerId: String) = withContext(Dispatchers.IO) {
        repository.deleteMarker(markerId)
    }

    suspend fun updateMarker(marker: SurveyMarker) = withContext(Dispatchers.IO) {
        repository.updateMarker(marker)
    }

    fun resumeExistingSession(session: SurveySession) {
        scope.launch(Dispatchers.IO) {
            val toResume = session.copy(
                status = SurveyStatus.READY_TO_RESUME,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateSession(toResume)
            repository.setActiveSession(toResume)
        }
    }
}
