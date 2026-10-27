package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val fileName: String,
    val markerCount: Int,
    val startChainageMetres: Long? = null,
    val endChainageMetres: Long? = null,
    val minLat: Double = 0.0,
    val maxLat: Double = 0.0,
    val minLng: Double = 0.0,
    val maxLng: Double = 0.0,
    val importedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false,
    val localFilePath: String? = null
)
