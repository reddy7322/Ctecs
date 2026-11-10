package com.example.survey.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyDao {

    // --- Session Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SurveySessionEntity)

    @Update
    suspend fun updateSession(session: SurveySessionEntity)

    @Query("SELECT * FROM survey_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): SurveySessionEntity?

    @Query("SELECT * FROM survey_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<SurveySessionEntity>>

    @Query("SELECT * FROM survey_sessions WHERE status IN ('RECORDING', 'PAUSED', 'INTERRUPTED', 'READY_TO_RESUME') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActiveOrUnfinishedSession(): SurveySessionEntity?

    @Query("DELETE FROM survey_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM survey_points WHERE sessionId = :sessionId")
    suspend fun deletePointsForSession(sessionId: String)

    @Query("DELETE FROM survey_markers WHERE sessionId = :sessionId")
    suspend fun deleteMarkersForSession(sessionId: String)

    @Query("DELETE FROM survey_calibrations WHERE sessionId = :sessionId")
    suspend fun deleteCalibrationsForSession(sessionId: String)

    // --- Points Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: SurveyPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<SurveyPointEntity>)

    @Query("SELECT * FROM survey_points WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getPointsForSession(sessionId: String): Flow<List<SurveyPointEntity>>

    @Query("SELECT * FROM survey_points WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getPointsForSessionSync(sessionId: String): List<SurveyPointEntity>

    @Query("SELECT COUNT(*) FROM survey_points WHERE sessionId = :sessionId")
    suspend fun getPointCountForSession(sessionId: String): Int

    // --- Markers Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: SurveyMarkerEntity)

    @Update
    suspend fun updateMarker(marker: SurveyMarkerEntity)

    @Query("DELETE FROM survey_markers WHERE id = :markerId")
    suspend fun deleteMarker(markerId: String)

    @Query("SELECT * FROM survey_markers WHERE sessionId = :sessionId ORDER BY chainageMetres ASC")
    fun getMarkersForSession(sessionId: String): Flow<List<SurveyMarkerEntity>>

    @Query("SELECT * FROM survey_markers WHERE sessionId = :sessionId ORDER BY chainageMetres ASC")
    suspend fun getMarkersForSessionSync(sessionId: String): List<SurveyMarkerEntity>

    // --- Calibration Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibration(cal: SurveyCalibrationEntity)

    @Query("SELECT * FROM survey_calibrations WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getCalibrationsForSession(sessionId: String): List<SurveyCalibrationEntity>
}
