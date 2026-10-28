package com.example.geo

import com.example.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    const val EARTH_RADIUS_METRES = 6371000.0

    /**
     * Calculates great-circle distance between two points in metres using the Haversine formula.
     */
    fun distanceMetres(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLng = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLng / 2.0) * sin(dLng / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METRES * c
    }

    /**
     * Projects point P onto line segment AB in local metric Euclidean space.
     * Returns:
     * - projectedPoint: the closest point on segment AB
     * - t: projection parameter clamped to [0.0, 1.0]
     * - perpendicularDistance: distance from P to the segment in metres
     */
    fun projectPointToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
        val midLat = Math.toRadians((a.latitude + b.latitude + p.latitude) / 3.0)
        val cosMidLat = cos(midLat)

        // Convert to local metric offsets (metres) relative to point A
        val ax = 0.0
        val ay = 0.0

        val bx = Math.toRadians(b.longitude - a.longitude) * EARTH_RADIUS_METRES * cosMidLat
        val by = Math.toRadians(b.latitude - a.latitude) * EARTH_RADIUS_METRES

        val px = Math.toRadians(p.longitude - a.longitude) * EARTH_RADIUS_METRES * cosMidLat
        val py = Math.toRadians(p.latitude - a.latitude) * EARTH_RADIUS_METRES

        val segDx = bx - ax
        val segDy = by - ay
        val segLenSq = segDx * segDx + segDy * segDy

        val t: Double
        val projX: Double
        val projY: Double

        if (segLenSq < 1e-6) {
            t = 0.0
            projX = 0.0
            projY = 0.0
        } else {
            val dot = (px - ax) * segDx + (py - ay) * segDy
            val unconstrainedT = dot / segLenSq
            t = max(0.0, min(1.0, unconstrainedT))
            projX = ax + t * segDx
            projY = ay + t * segDy
        }

        // Distance from P to projection
        val dx = px - projX
        val dy = py - projY
        val perpDist = sqrt(dx * dx + dy * dy)

        // Convert projected point back to lat/lng
        val projLng = a.longitude + Math.toDegrees(projX / (EARTH_RADIUS_METRES * cosMidLat))
        val projLat = a.latitude + Math.toDegrees(projY / EARTH_RADIUS_METRES)
        val projPoint = GeoPoint(projLat, projLng, a.altitude + t * (b.altitude - a.altitude))

        return SegmentProjection(
            projectedPoint = projPoint,
            t = t,
            perpendicularDistance = perpDist,
            p1 = a,
            p2 = b
        )
    }

    /**
     * Calculates initial bearing from p1 to p2 in degrees [0, 360).
     */
    fun bearingDegrees(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLng = Math.toRadians(p2.longitude - p1.longitude)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }
}

data class SegmentProjection(
    val projectedPoint: GeoPoint,
    val t: Double,
    val perpendicularDistance: Double,
    val p1: GeoPoint? = null,
    val p2: GeoPoint? = null
)
