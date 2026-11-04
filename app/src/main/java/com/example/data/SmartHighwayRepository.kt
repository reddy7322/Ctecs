package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmartHighwayRepository(
    private val context: Context,
    private val projectDao: HighwayProjectDao,
    private val activityDao: SmartActivityDao,
    private val qcRecordDao: QcRecordDao,
    private val reportDao: DailyReportRecordDao,
    private val scope: CoroutineScope
) {
    val allProjects: Flow<List<HighwayProjectEntity>> = projectDao.getAllProjects()
    val allQcRecords: Flow<List<QcRecordEntity>> = qcRecordDao.getAllQcRecords()

    init {
        // Seed default initial project if empty
        scope.launch(Dispatchers.IO) {
            val existing = projectDao.getProjectById("NH-48")
            if (existing == null) {
                projectDao.insertProject(
                    HighwayProjectEntity(
                        id = "NH-48",
                        name = "NH-48 Delhi-Mumbai Expressway",
                        clientName = "National Highways Authority of India",
                        contractorName = "Highway Infra Consortium",
                        startChainage = "120+000",
                        endChainage = "195+500",
                        totalLengthKm = 75.5
                    )
                )
            }
        }
    }

    fun getActivitiesForProject(projectId: String): Flow<List<SmartActivityEntity>> {
        return activityDao.getActivitiesByProject(projectId)
    }

    fun getActivitiesForProjectAndDate(projectId: String, dateString: String): Flow<List<SmartActivityEntity>> {
        return activityDao.getActivitiesByProjectAndDate(projectId, dateString)
    }

    suspend fun getActivitiesForProjectAndDateList(projectId: String, dateString: String): List<SmartActivityEntity> {
        return withContext(Dispatchers.IO) {
            activityDao.getActivitiesByProjectAndDateList(projectId, dateString)
        }
    }

    suspend fun getActivitiesForProjectAndDateRangeList(projectId: String, startDate: String, endDate: String): List<SmartActivityEntity> {
        return withContext(Dispatchers.IO) {
            activityDao.getActivitiesByProjectAndDateRangeList(projectId, startDate, endDate)
        }
    }

    fun getRecordedDatesForProject(projectId: String): Flow<List<String>> {
        return activityDao.getRecordedDatesForProject(projectId)
    }

    suspend fun saveActivity(activity: SmartActivityEntity): Long {
        return withContext(Dispatchers.IO) {
            if (activity.id == 0L) {
                activityDao.insertActivity(activity)
            } else {
                activityDao.updateActivity(activity)
                activity.id
            }
        }
    }

    suspend fun deleteActivity(activity: SmartActivityEntity) {
        withContext(Dispatchers.IO) {
            activityDao.deleteActivity(activity)
        }
    }

    suspend fun saveProject(project: HighwayProjectEntity) {
        withContext(Dispatchers.IO) {
            projectDao.insertProject(project)
        }
    }

    suspend fun saveQcRecord(record: QcRecordEntity): Long {
        return withContext(Dispatchers.IO) {
            qcRecordDao.insertQcRecord(record)
        }
    }

    suspend fun deleteQcRecord(record: QcRecordEntity) {
        withContext(Dispatchers.IO) {
            qcRecordDao.deleteQcRecord(record)
        }
    }

    fun getReportsForProject(projectId: String): Flow<List<DailyProjectReportEntity>> {
        return reportDao.getReportsByProject(projectId)
    }

    suspend fun saveReport(report: DailyProjectReportEntity): Long {
        return withContext(Dispatchers.IO) {
            reportDao.insertReport(report)
        }
    }

    suspend fun deleteReport(report: DailyProjectReportEntity) {
        withContext(Dispatchers.IO) {
            reportDao.deleteReport(report)
        }
    }
}
