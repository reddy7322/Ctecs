package com.example.model

enum class MatchConfidence {
    HIGH,     // Perpendicular offset <= 15m and GPS accuracy <= 10m
    MEDIUM,   // Offset 15m..30m or moderate GPS accuracy
    LOW,      // Offset > 30m or poor GPS fix
    NO_MATCH  // No route loaded or GPS too far
}

/**
 * Result of matching a live GPS coordinate against the active route alignment.
 */
data class ChainageMatchResult(
    val chainage: Chainage? = null,
    val projectedPoint: GeoPoint? = null,
    val offsetMetres: Double = 0.0,
    val gpsAccuracy: Float = 0.0f,
    val confidence: MatchConfidence = MatchConfidence.NO_MATCH,
    val statusMessage: String = "No route loaded",
    val isManual: Boolean = false,
    val matchedAlignmentName: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayChainage: String
        get() = chainage?.format() ?: if (isManual) "Manual" else "---"

    companion object {
        fun noRoute(accuracy: Float = 0f) = ChainageMatchResult(
            confidence = MatchConfidence.NO_MATCH,
            gpsAccuracy = accuracy,
            statusMessage = "No route loaded"
        )

        fun manual(chainage: Chainage, point: GeoPoint? = null) = ChainageMatchResult(
            chainage = chainage,
            projectedPoint = point,
            confidence = MatchConfidence.HIGH,
            isManual = true,
            statusMessage = "Manual Entry"
        )
    }
}
