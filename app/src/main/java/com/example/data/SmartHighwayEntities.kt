package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "highway_projects")
data class HighwayProjectEntity(
    @PrimaryKey val id: String, // e.g. "PRJ-01"
    val name: String,
    val clientName: String = "National Highways Authority",
    val contractorName: String = "Highway Infra Ltd.",
    val startChainage: String = "0+000",
    val endChainage: String = "50+000",
    val totalLengthKm: Double = 50.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "smart_activities")
data class SmartActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val projectName: String,
    val dateString: String, // "YYYY-MM-DD"
    val timestamp: Long = System.currentTimeMillis(),
    val workDescription: String = "",
    val fromChainage: String = "",
    val toChainage: String = "",
    val side: String = "LHS", // LHS, RHS, Both, Median
    val roadSection: String = "Main Carriageway", // Main Carriageway, Service Road, Ramp, Other
    val activityLayer: String = "BC", // BC, DBM, WMM, GSB, Tack Coat, Prime Coat, Glass Grid, Subgrade, PQC, Custom
    val completedLengthM: Double = 0.0,
    val widthM: Double = 0.0,
    val depthM: Double = 0.0,
    val thicknessMm: Double = 0.0,
    val quantity: Double = 0.0,
    val unit: String = "MT", // MT, Sqm, Cum, Rm, Litres
    val contractor: String = "",
    val machineryDetails: String = "",
    val manpowerDetails: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val siteObservations: String = "",
    val qualityIssues: String = "",
    val rectificationInstructions: String = "",
    val pendingWorks: String = "",
    val remarks: String = "",
    val photoUris: String = "", // Comma-separated paths
    val hasUnresolvedIssues: Boolean = false,
    val subActivityName: String = "",
    val subFromChainage: String = "",
    val subToChainage: String = "",
    val subLengthM: Double = 0.0,
    val subWidthM: Double = 0.0,
    val subDepthM: Double = 0.0,
    val subComments: String = "",
    val sideMode: String = "Auto",
    val isSideAutoDetected: Boolean = false
)

@Entity(tableName = "qc_inspection_records")
data class QcRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "BC_TEMP", "GLASS_GRID", "TACK_COAT", "PRE_SURVEY", "SITE_OBS", "NCR", "PROGRESS"
    val title: String,
    val projectId: String,
    val dateString: String,
    val timestamp: Long = System.currentTimeMillis(),
    val chainage: String = "",
    val side: String = "LHS",
    val layer: String = "",
    val detailsJson: String = "",
    val status: String = "Pass", // "Pass", "Fail", "Pending", "Open", "Closed"
    val photoUri: String = "",
    val inspectorName: String = "",
    val remarks: String = ""
)

@Entity(tableName = "daily_project_reports")
data class DailyProjectReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val projectName: String,
    val reportDate: String, // "YYYY-MM-DD"
    val generatedTimestamp: Long = System.currentTimeMillis(),
    val dailySummary: String = "",
    val overallRemarks: String = "",
    val preparedBy: String = "",
    val verifiedBy: String = "",
    val pdfFilePath: String = "",
    val activityCount: Int = 0
)
