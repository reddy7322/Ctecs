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
    val packageSection: String = "",
    val roadSide: String = "RHS",
    val lane: String = "I/L",
    val activity: String = "",
    val subActivity: String = "",
    val customDescription: String = "",
    val inspectorName: String = "",
    val formattedOverlayText: String = ""
) {
    private fun isValidValue(s: String?): Boolean {
        if (s == null) return false
        val t = s.trim()
        return t.isNotEmpty() && !t.equals("None", ignoreCase = true) && !t.equals("null", ignoreCase = true)
    }

    fun cleanActivity(): String = if (isValidValue(activity)) activity.trim() else ""

    fun cleanSubActivity(): String = if (isValidValue(subActivity)) subActivity.trim() else ""

    fun cleanRoadSide(): String = if (isValidValue(roadSide)) roadSide.trim() else ""

    fun cleanLane(): String = if (isValidValue(lane)) lane.trim() else ""

    fun cleanHighwayId(): String = if (isValidValue(highwayId)) highwayId.trim() else ""

    fun cleanPackageSection(): String = if (isValidValue(packageSection)) packageSection.trim() else ""

    fun displayActivityWithSub(): String {
        val act = cleanActivity()
        val sub = cleanSubActivity()
        return when {
            act.isNotEmpty() && sub.isNotEmpty() -> "$act ($sub)"
            act.isNotEmpty() -> act
            sub.isNotEmpty() -> sub
            else -> ""
        }
    }

    fun displayHighwaySummary(): String {
        val tokens = listOf(
            cleanHighwayId(),
            cleanRoadSide(),
            cleanLane(),
            displayActivityWithSub()
        ).filter { it.isNotEmpty() }
        return tokens.joinToString(" • ")
    }
}

