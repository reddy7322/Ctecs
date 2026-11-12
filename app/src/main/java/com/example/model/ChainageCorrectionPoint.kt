package com.example.model

import org.json.JSONObject

/**
 * A custom chainage correction point defined along a specific KMZ/KML route.
 *
 * @param id Unique identifier
 * @param routeId ID of the KMZ route this correction point belongs to
 * @param latitude GPS latitude of the correction location
 * @param longitude GPS longitude of the correction location
 * @param originalKmzMetres The original uncorrected chainage distance in metres along the KMZ alignment
 * @param correctedMetres The true/calibrated chainage distance in metres entered by the user
 * @param routeDistanceMetres Cumulative distance along route alignment from start (if available)
 * @param description Optional user label or landmark description (e.g. "Culvert 198+500", "Toll Plaza")
 * @param isEnabled Whether this correction point is active for interpolation
 * @param createdAt Epoch timestamp of creation
 */
data class ChainageCorrectionPoint(
    val id: String = java.util.UUID.randomUUID().toString(),
    val routeId: String,
    val latitude: Double,
    val longitude: Double,
    val originalKmzMetres: Long,
    val correctedMetres: Long,
    val routeDistanceMetres: Double = 0.0,
    val description: String = "",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val point: GeoPoint get() = GeoPoint(latitude, longitude)
    val originalKmzChainage: Chainage get() = Chainage(originalKmzMetres)
    val correctedChainage: Chainage get() = Chainage(correctedMetres)
    val offsetMetres: Long get() = correctedMetres - originalKmzMetres

    val displayOffset: String
        get() = if (offsetMetres >= 0) "+${offsetMetres}m" else "${offsetMetres}m"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("routeId", routeId)
        put("lat", latitude)
        put("lng", longitude)
        put("originalKmzMetres", originalKmzMetres)
        put("correctedMetres", correctedMetres)
        put("routeDistanceMetres", routeDistanceMetres)
        put("description", description)
        put("isEnabled", isEnabled)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): ChainageCorrectionPoint {
            return ChainageCorrectionPoint(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                routeId = obj.optString("routeId", ""),
                latitude = obj.optDouble("lat", 0.0),
                longitude = obj.optDouble("lng", 0.0),
                originalKmzMetres = obj.optLong("originalKmzMetres", 0L),
                correctedMetres = obj.optLong("correctedMetres", 0L),
                routeDistanceMetres = obj.optDouble("routeDistanceMetres", 0.0),
                description = obj.optString("description", ""),
                isEnabled = obj.optBoolean("isEnabled", true),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
