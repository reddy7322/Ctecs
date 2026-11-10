package com.example.survey.data

import com.example.survey.model.SurveyCalibrationPoint
import com.example.survey.model.SurveyMarker
import com.example.survey.model.SurveyPoint
import com.example.survey.model.SurveySession
import com.example.survey.model.SurveyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SaveStatus {
    object Idle : SaveStatus()
    object Saving : SaveStatus()
    data class Saved(val timestamp: Long) : SaveStatus()
    data class Failed(val error: String) : SaveStatus()
}

class SurveyRepository(
    private val surveyDao: SurveyDao,
    private val scope: CoroutineScope
) {
    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private val _activeSession = MutableStateFlow<SurveySession?>(null)
    val activeSession: StateFlow<SurveySession?> = _activeSession.asStateFlow()

    val allSessions: Flow<List<SurveySession>> = surveyDao.getAllSessions().map { list ->
        list.map { it.toDomain() }
    }

    init {
        // Attempt restoring any unfinished/interrupted session on startup
        scope.launch(Dispatchers.IO) {
            val unfinished = surveyDao.getActiveOrUnfinishedSession()
            if (unfinished != null) {
                val restored = unfinished.toDomain().let {
                    if (it.status == SurveyStatus.RECORDING) {
                        it.copy(status = SurveyStatus.READY_TO_RESUME)
                    } else it
                }
                _activeSession.value = restored
                surveyDao.updateSession(SurveySessionEntity.fromDomain(restored))
            }
        }
    }

    suspend fun createNewSession(session: SurveySession): SurveySession = withContext(Dispatchers.IO) {
        _saveStatus.value = SaveStatus.Saving
        try {
            surveyDao.insertSession(SurveySessionEntity.fromDomain(session))
            _activeSession.value = session
            _saveStatus.value = SaveStatus.Saved(System.currentTimeMillis())
            session
        } catch (e: Exception) {
            _saveStatus.value = SaveStatus.Failed(e.message ?: "Failed to create session")
            throw e
        }
    }

    suspend fun updateSession(session: SurveySession) = withContext(Dispatchers.IO) {
        _saveStatus.value = SaveStatus.Saving
        try {
            val updated = session.copy(updatedAt = System.currentTimeMillis(), lastSavedAt = System.currentTimeMillis())
            surveyDao.updateSession(SurveySessionEntity.fromDomain(updated))
            if (_activeSession.value?.id == session.id) {
                _activeSession.value = updated
            }
            _saveStatus.value = SaveStatus.Saved(System.currentTimeMillis())
        } catch (e: Exception) {
            _saveStatus.value = SaveStatus.Failed(e.message ?: "Failed to update session")
        }
    }

    fun setActiveSession(session: SurveySession?) {
        _activeSession.value = session
    }

    suspend fun getInterruptedSessionOnStartup(): SurveySession? = withContext(Dispatchers.IO) {
        val unfinished = surveyDao.getActiveOrUnfinishedSession() ?: return@withContext null
        val domain = unfinished.toDomain()
        if (domain.status == SurveyStatus.RECORDING ||
            domain.status == SurveyStatus.PAUSED ||
            domain.status == SurveyStatus.INTERRUPTED ||
            domain.status == SurveyStatus.READY_TO_RESUME
        ) {
            val ready = if (domain.status == SurveyStatus.RECORDING) {
                domain.copy(status = SurveyStatus.READY_TO_RESUME)
            } else domain
            surveyDao.updateSession(SurveySessionEntity.fromDomain(ready))
            _activeSession.value = ready
            ready
        } else {
            null
        }
    }

    suspend fun getSessionById(id: String): SurveySession? = withContext(Dispatchers.IO) {
        surveyDao.getSessionById(id)?.toDomain()
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        surveyDao.deletePointsForSession(id)
        surveyDao.deleteMarkersForSession(id)
        surveyDao.deleteCalibrationsForSession(id)
        surveyDao.deleteSession(id)
        if (_activeSession.value?.id == id) {
            _activeSession.value = null
        }
    }

    suspend fun savePoint(point: SurveyPoint) = withContext(Dispatchers.IO) {
        _saveStatus.value = SaveStatus.Saving
        try {
            surveyDao.insertPoint(SurveyPointEntity.fromDomain(point))
            _saveStatus.value = SaveStatus.Saved(System.currentTimeMillis())
        } catch (e: Exception) {
            _saveStatus.value = SaveStatus.Failed(e.message ?: "Point save failed")
        }
    }

    fun getPointsFlow(sessionId: String): Flow<List<SurveyPoint>> {
        return surveyDao.getPointsForSession(sessionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getPointsSync(sessionId: String): List<SurveyPoint> = withContext(Dispatchers.IO) {
        surveyDao.getPointsForSessionSync(sessionId).map { it.toDomain() }
    }

    suspend fun saveMarker(marker: SurveyMarker) = withContext(Dispatchers.IO) {
        _saveStatus.value = SaveStatus.Saving
        try {
            surveyDao.insertMarker(SurveyMarkerEntity.fromDomain(marker))
            _saveStatus.value = SaveStatus.Saved(System.currentTimeMillis())
        } catch (e: Exception) {
            _saveStatus.value = SaveStatus.Failed(e.message ?: "Marker save failed")
        }
    }

    suspend fun updateMarker(marker: SurveyMarker) = withContext(Dispatchers.IO) {
        _saveStatus.value = SaveStatus.Saving
        try {
            surveyDao.updateMarker(SurveyMarkerEntity.fromDomain(marker))
            _saveStatus.value = SaveStatus.Saved(System.currentTimeMillis())
        } catch (e: Exception) {
            _saveStatus.value = SaveStatus.Failed(e.message ?: "Marker update failed")
        }
    }

    suspend fun deleteMarker(markerId: String) = withContext(Dispatchers.IO) {
        surveyDao.deleteMarker(markerId)
    }

    fun getMarkersFlow(sessionId: String): Flow<List<SurveyMarker>> {
        return surveyDao.getMarkersForSession(sessionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getMarkersSync(sessionId: String): List<SurveyMarker> = withContext(Dispatchers.IO) {
        surveyDao.getMarkersForSessionSync(sessionId).map { it.toDomain() }
    }

    suspend fun saveCalibration(cal: SurveyCalibrationPoint) = withContext(Dispatchers.IO) {
        try {
            surveyDao.insertCalibration(SurveyCalibrationEntity.fromDomain(cal))
        } catch (ignored: Exception) {}
    }

    suspend fun getCalibrationsSync(sessionId: String): List<SurveyCalibrationPoint> = withContext(Dispatchers.IO) {
        surveyDao.getCalibrationsForSession(sessionId).map { it.toDomain() }
    }
}
