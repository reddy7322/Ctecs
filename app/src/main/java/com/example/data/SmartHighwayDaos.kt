package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HighwayProjectDao {
    @Query("SELECT * FROM highway_projects ORDER BY createdAt ASC")
    fun getAllProjects(): Flow<List<HighwayProjectEntity>>

    @Query("SELECT * FROM highway_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): HighwayProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: HighwayProjectEntity)

    @Update
    suspend fun updateProject(project: HighwayProjectEntity)

    @Delete
    suspend fun deleteProject(project: HighwayProjectEntity)
}

@Dao
interface SmartActivityDao {
    @Query("SELECT * FROM smart_activities ORDER BY timestamp DESC")
    fun getAllActivities(): Flow<List<SmartActivityEntity>>

    @Query("SELECT * FROM smart_activities WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getActivitiesByProject(projectId: String): Flow<List<SmartActivityEntity>>

    @Query("SELECT * FROM smart_activities WHERE projectId = :projectId AND dateString = :dateString ORDER BY timestamp ASC")
    fun getActivitiesByProjectAndDate(projectId: String, dateString: String): Flow<List<SmartActivityEntity>>

    @Query("SELECT * FROM smart_activities WHERE projectId = :projectId AND dateString = :dateString ORDER BY timestamp ASC")
    suspend fun getActivitiesByProjectAndDateList(projectId: String, dateString: String): List<SmartActivityEntity>

    @Query("SELECT DISTINCT dateString FROM smart_activities WHERE projectId = :projectId")
    fun getRecordedDatesForProject(projectId: String): Flow<List<String>>

    @Query("SELECT * FROM smart_activities WHERE id = :id LIMIT 1")
    suspend fun getActivityById(id: Long): SmartActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: SmartActivityEntity): Long

    @Update
    suspend fun updateActivity(activity: SmartActivityEntity)

    @Delete
    suspend fun deleteActivity(activity: SmartActivityEntity)

    @Query("DELETE FROM smart_activities WHERE id = :id")
    suspend fun deleteActivityById(id: Long)
}

@Dao
interface QcRecordDao {
    @Query("SELECT * FROM qc_inspection_records ORDER BY timestamp DESC")
    fun getAllQcRecords(): Flow<List<QcRecordEntity>>

    @Query("SELECT * FROM qc_inspection_records WHERE type = :type ORDER BY timestamp DESC")
    fun getQcRecordsByType(type: String): Flow<List<QcRecordEntity>>

    @Query("SELECT * FROM qc_inspection_records WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getQcRecordsByProject(projectId: String): Flow<List<QcRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQcRecord(record: QcRecordEntity): Long

    @Delete
    suspend fun deleteQcRecord(record: QcRecordEntity)
}

@Dao
interface DailyReportRecordDao {
    @Query("SELECT * FROM daily_project_reports ORDER BY generatedTimestamp DESC")
    fun getAllReports(): Flow<List<DailyProjectReportEntity>>

    @Query("SELECT * FROM daily_project_reports WHERE projectId = :projectId ORDER BY generatedTimestamp DESC")
    fun getReportsByProject(projectId: String): Flow<List<DailyProjectReportEntity>>

    @Query("SELECT * FROM daily_project_reports WHERE projectId = :projectId AND reportDate = :dateString LIMIT 1")
    suspend fun getReportForDate(projectId: String, dateString: String): DailyProjectReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: DailyProjectReportEntity): Long

    @Delete
    suspend fun deleteReport(report: DailyProjectReportEntity)
}
