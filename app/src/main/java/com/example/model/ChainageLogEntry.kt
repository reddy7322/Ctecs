package com.example.model

import org.json.JSONObject

/**
 * Historical log entry recording user-initiated manual chainage overrides and KMZ calibrations.
 */
data class ChainageLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val routeId: String = "",
    val routeName: String = "",
    val actionType: String, // "MANUAL_OVERRIDE", "MANUAL_RESET", "POINT_ADDED", "POINT_EDITED", "POINT_DELETED", "ALL_RESET", "TOGGLED"
    val summary: String,
    val details: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("routeId", routeId)
        put("routeName", routeName)
        put("actionType", actionType)
        put("summary", summary)
        put("details", details)
    }

    companion object {
        fun fromJson(obj: JSONObject): ChainageLogEntry = ChainageLogEntry(
            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            routeId = obj.optString("routeId", ""),
            routeName = obj.optString("routeName", ""),
            actionType = obj.optString("actionType", "ADJUSTMENT"),
            summary = obj.optString("summary", ""),
            details = obj.optString("details", "")
        )
    }
}
