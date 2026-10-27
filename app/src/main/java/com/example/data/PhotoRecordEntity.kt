package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_records")
data class PhotoRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val thumbnailPath: String? = null,
    val fileName: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double = 0.0,
    val chainageMetres: Long? = null,
    val chainageLabel: String? = null,
    val chainageConfidence: String = "NO_MATCH",
    val isManualChainage: Boolean = false,
    val offsetMetres: Float = 0f,
    val highwayId: String = "NH-58",
    val packageSection: String = "MM",
    val roadSide: String = "RHS",
    val lane: String = "I/L",
    val activity: String = "DBM",
    val customDescription: String = "",
    val inspectorName: String = "",
    val formattedOverlayText: String = ""
)
