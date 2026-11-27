package com.example.model

import org.json.JSONObject

enum class ChainageCardRefreshState {
    IDLE,
    REFRESHING,
    SUCCESS,
    WARNING
}

/**
 * Lightweight persisted snapshot of the last validated reliable chainage for cold-start
 * and background/foreground restoration. Intentionally omits raw GPS coordinates.
 */
data class LastReliableChainageSnapshot(
    val routeId: String,
    val chainageMetres: Long,
    val rawChainageMetres: Long? = null,
    val alignmentName: String = "",
    val distanceAlongMetres: Double = 0.0,
    val detectedSide: String = "RHS",
    val sourceName: String = ChainageSource.KMZ.name,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("routeId", routeId)
            put("chainageMetres", chainageMetres)
            if (rawChainageMetres != null) put("rawChainageMetres", rawChainageMetres)
            put("alignmentName", alignmentName)
            put("distanceAlongMetres", distanceAlongMetres)
            put("detectedSide", detectedSide)
            put("sourceName", sourceName)
            put("timestampMs", timestampMs)
        }.toString()
    }

    companion object {
        fun fromJsonString(json: String?): LastReliableChainageSnapshot? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                val routeId = obj.optString("routeId", "").trim()
                if (routeId.isEmpty() || !obj.has("chainageMetres")) return null
                LastReliableChainageSnapshot(
                    routeId = routeId,
                    chainageMetres = obj.getLong("chainageMetres").coerceAtLeast(0L),
                    rawChainageMetres = if (obj.has("rawChainageMetres") && !obj.isNull("rawChainageMetres")) {
                        obj.getLong("rawChainageMetres").coerceAtLeast(0L)
                    } else null,
                    alignmentName = obj.optString("alignmentName", ""),
                    distanceAlongMetres = obj.optDouble("distanceAlongMetres", 0.0).takeIf { it.isFinite() } ?: 0.0,
                    detectedSide = obj.optString("detectedSide", "RHS").ifBlank { "RHS" },
                    sourceName = obj.optString("sourceName", ChainageSource.KMZ.name),
                    timestampMs = obj.optLong("timestampMs", 0L)
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

