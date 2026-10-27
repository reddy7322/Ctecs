package com.example.model

import java.util.Locale
import kotlin.math.abs

/**
 * WGS84 Geographic Coordinate with optional altitude.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0
) {
    /**
     * Formats GPS coordinates matching the reference image: "29.9183N 78.1064E"
     * Supports configurable decimal precision (4 to 7 places).
     */
    fun formatReference(precision: Int = 4): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"
        val formatStr = "%.${precision}f%s %.${precision}f%s"
        return String.format(Locale.US, formatStr, abs(latitude), latDir, abs(longitude), lonDir)
    }

    /**
     * Standard ISO / Degree format: "29.9183° N, 78.1064° E"
     */
    fun formatStandard(precision: Int = 4): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"
        val formatStr = "%.${precision}f° %s, %.${precision}f° %s"
        return String.format(Locale.US, formatStr, abs(latitude), latDir, abs(longitude), lonDir)
    }
}

data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
) {
    val center: GeoPoint
        get() = GeoPoint((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)

    fun contains(point: GeoPoint): Boolean {
        return point.latitude in minLat..maxLat && point.longitude in minLng..maxLng
    }

    companion object {
        fun fromPoints(points: Collection<GeoPoint>): GeoBounds {
            if (points.isEmpty()) return GeoBounds(0.0, 0.0, 0.0, 0.0)
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLng = Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE
            for (p in points) {
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLng) minLng = p.longitude
                if (p.longitude > maxLng) maxLng = p.longitude
            }
            return GeoBounds(minLat, maxLat, minLng, maxLng)
        }
    }
}
