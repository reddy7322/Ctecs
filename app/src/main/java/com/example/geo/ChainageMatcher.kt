package com.example.geo

import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteData
import com.example.model.RouteMarker
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object ChainageMatcher {

    const val DEFAULT_HIGH_CONFIDENCE_OFFSET_METRES = 15.0
    const val DEFAULT_MAX_MATCH_OFFSET_METRES = 35.0
    const val DEFAULT_MAX_GPS_ACCURACY_METRES = 15.0

    /**
     * Matches a GPS coordinate to the active route, returning estimated chainage,
     * perpendicular distance offset, and confidence level.
     */
    fun match(
        gpsPoint: GeoPoint,
        gpsAccuracy: Float,
        bearing: Float? = null,
        routeData: RouteData?,
        previousResult: ChainageMatchResult? = null,
        maxMatchOffsetMetres: Double = DEFAULT_MAX_MATCH_OFFSET_METRES,
        highConfidenceOffsetMetres: Double = DEFAULT_HIGH_CONFIDENCE_OFFSET_METRES
    ): ChainageMatchResult {
        if (routeData == null || routeData.markers.isEmpty()) {
            return ChainageMatchResult(
                confidence = MatchConfidence.NO_MATCH,
                gpsAccuracy = gpsAccuracy,
                statusMessage = "No active route loaded"
            )
        }

        // 1. Find nearest candidate segment across all alignments
        val prevAlignmentName = previousResult?.matchedAlignmentName.orEmpty()
        var bestProjection: SegmentProjection? = null
        var minScore = Double.MAX_VALUE
        var matchedAlignmentName = ""

        // Check each alignment polyline
        for (alignment in routeData.alignments) {
            val pts = alignment.points
            if (pts.size < 2) continue

            for (i in 0 until pts.size - 1) {
                val p1 = pts[i]
                val p2 = pts[i + 1]

                // Bounding box check with 500m padding (~0.005 deg)
                val minLat = minOf(p1.latitude, p2.latitude) - 0.005
                val maxLat = maxOf(p1.latitude, p2.latitude) + 0.005
                val minLng = minOf(p1.longitude, p2.longitude) - 0.005
                val maxLng = maxOf(p1.longitude, p2.longitude) + 0.005

                if (gpsPoint.latitude !in minLat..maxLat || gpsPoint.longitude !in minLng..maxLng) {
                    continue
                }

                val proj = GeoUtils.projectPointToSegment(gpsPoint, p1, p2)
                // Continuity bias: give previously matched alignment a 4.0m hysteresis advantage
                // to prevent rapid fluttering across median between parallel carriageways
                val continuityBonus = if (prevAlignmentName.isNotBlank() && alignment.name == prevAlignmentName) 4.0 else 0.0
                val score = proj.perpendicularDistance - continuityBonus

                if (score < minScore) {
                    minScore = score
                    bestProjection = proj
                    matchedAlignmentName = alignment.name
                }
            }
        }

        // Also check direct point-to-marker distance if no polyline matched closely
        val sortedMarkers = routeData.markers
        val nearestMarker = sortedMarkers.minByOrNull { GeoUtils.distanceMetres(gpsPoint, it.point) }
            ?: return ChainageMatchResult.noRoute(gpsAccuracy)

        val distToNearestMarker = GeoUtils.distanceMetres(gpsPoint, nearestMarker.point)

        // If polyline gave a projection within acceptable distance, use it
        val finalProjectionPoint = if (bestProjection != null && bestProjection.perpendicularDistance <= (distToNearestMarker + 20.0)) {
            bestProjection.projectedPoint
        } else {
            nearestMarker.point
        }

        val effectiveOffset = if (bestProjection != null && bestProjection.perpendicularDistance < distToNearestMarker) {
            bestProjection.perpendicularDistance
        } else {
            distToNearestMarker
        }

        // 2. Locate bracketing markers to interpolate chainage accurately
        // Find index of marker closest to projected point
        val nearestIndex = sortedMarkers.indexOfFirst { it.id == nearestMarker.id }
        var estimatedChainageMetres: Long

        if (sortedMarkers.size == 1) {
            estimatedChainageMetres = nearestMarker.chainage.metres
        } else {
            // Find predecessor and successor markers
            val prevMarker = if (nearestIndex > 0) sortedMarkers[nearestIndex - 1] else null
            val nextMarker = if (nearestIndex < sortedMarkers.size - 1) sortedMarkers[nearestIndex + 1] else null

            // Determine if projection lies between prev & nearest, or nearest & next
            val dPrev = prevMarker?.let { GeoUtils.distanceMetres(it.point, finalProjectionPoint) } ?: Double.MAX_VALUE
            val dNext = nextMarker?.let { GeoUtils.distanceMetres(it.point, finalProjectionPoint) } ?: Double.MAX_VALUE

            if (prevMarker != null && dPrev <= dNext && prevMarker.chainage.metres != nearestMarker.chainage.metres) {
                // Between prevMarker and nearestMarker
                val spanDist = GeoUtils.distanceMetres(prevMarker.point, nearestMarker.point)
                val fraction = if (spanDist > 0.1) (dPrev / spanDist).coerceIn(0.0, 1.0) else 0.0
                val deltaCh = nearestMarker.chainage.metres - prevMarker.chainage.metres
                estimatedChainageMetres = (prevMarker.chainage.metres + fraction * deltaCh).roundToLong()
            } else if (nextMarker != null && nextMarker.chainage.metres != nearestMarker.chainage.metres) {
                // Between nearestMarker and nextMarker
                val spanDist = GeoUtils.distanceMetres(nearestMarker.point, nextMarker.point)
                val dFromNearest = GeoUtils.distanceMetres(nearestMarker.point, finalProjectionPoint)
                val fraction = if (spanDist > 0.1) (dFromNearest / spanDist).coerceIn(0.0, 1.0) else 0.0
                val deltaCh = nextMarker.chainage.metres - nearestMarker.chainage.metres
                estimatedChainageMetres = (nearestMarker.chainage.metres + fraction * deltaCh).roundToLong()
            } else {
                estimatedChainageMetres = nearestMarker.chainage.metres
            }
        }

        // 3. Confidence assessment
        val isAccuracyAcceptable = gpsAccuracy <= DEFAULT_MAX_GPS_ACCURACY_METRES
        val confidence: MatchConfidence
        val statusMessage: String

        when {
            effectiveOffset <= highConfidenceOffsetMetres && isAccuracyAcceptable -> {
                confidence = MatchConfidence.HIGH
                statusMessage = String.format(
                    Locale.US,
                    "Matched (Offset: %.1fm, GPS: ±%.1fm)",
                    effectiveOffset,
                    gpsAccuracy
                )
            }
            effectiveOffset <= maxMatchOffsetMetres -> {
                confidence = MatchConfidence.MEDIUM
                statusMessage = String.format(
                    Locale.US,
                    "Caution: Offset %.1fm (GPS: ±%.1fm)",
                    effectiveOffset,
                    gpsAccuracy
                )
            }
            effectiveOffset <= 100.0 -> {
                confidence = MatchConfidence.LOW
                statusMessage = String.format(
                    Locale.US,
                    "Low Confidence: Offset %.1fm > 35m",
                    effectiveOffset
                )
            }
            else -> {
                confidence = MatchConfidence.NO_MATCH
                statusMessage = String.format(
                    Locale.US,
                    "Off Route (%.0fm away)",
                    effectiveOffset
                )
            }
        }

        // 4. Carriageway LHS / RHS detection based strictly on KML/KMZ route direction
        // Imported KML/KMZ route direction is the authoritative reference.
        // Chainage direction and vehicle travel direction NEVER reverse LHS/RHS!
        var detectedSide = "Unknown"
        if (confidence == MatchConfidence.HIGH || confidence == MatchConfidence.MEDIUM) {
            val lowerAlign = matchedAlignmentName.lowercase(Locale.ROOT)
            val lowerRouteName = routeData.name.lowercase(Locale.ROOT)

            if (lowerAlign.contains("lhs") || lowerAlign.contains("left") || lowerRouteName.contains("lhs")) {
                detectedSide = "LHS"
            } else if (lowerAlign.contains("rhs") || lowerAlign.contains("right") || lowerRouteName.contains("rhs")) {
                detectedSide = "RHS"
            } else {
                // Determine geometric side relative to nearest ordered route segment
                val segment = if (bestProjection != null && bestProjection.p1 != null && bestProjection.p2 != null) {
                    Pair(bestProjection.p1!!, bestProjection.p2!!)
                } else if (sortedMarkers.size >= 2) {
                    val p1 = if (nearestIndex < sortedMarkers.size - 1) sortedMarkers[nearestIndex].point else sortedMarkers[nearestIndex - 1].point
                    val p2 = if (nearestIndex < sortedMarkers.size - 1) sortedMarkers[nearestIndex + 1].point else sortedMarkers[nearestIndex].point
                    Pair(p1, p2)
                } else {
                    null
                }

                if (segment != null) {
                    val lateralOffset = calculateSignedLateralOffsetMetres(gpsPoint, segment.first, segment.second)
                    detectedSide = when {
                        lateralOffset > 0.0 -> "LHS"
                        lateralOffset < 0.0 -> "RHS"
                        else -> previousResult?.detectedSide ?: "RHS"
                    }
                }
            }
        }

        return ChainageMatchResult(
            chainage = Chainage(estimatedChainageMetres),
            projectedPoint = finalProjectionPoint,
            offsetMetres = effectiveOffset,
            gpsAccuracy = gpsAccuracy,
            confidence = confidence,
            statusMessage = statusMessage,
            isManual = false,
            matchedAlignmentName = matchedAlignmentName,
            detectedSide = detectedSide
        )
    }

    /**
     * Calculates the signed lateral distance in metres of [point] relative to the
     * ordered route segment from [p1] to [p2].
     *
     * Looking forward along the ordered direction from [p1] to [p2]:
     * - Positive (> 0): Point is strictly to the LEFT (LHS) of the route.
     * - Negative (< 0): Point is strictly to the RIGHT (RHS) of the route.
     * - Zero (= 0): Point is on the route.
     */
    fun calculateSignedLateralOffsetMetres(point: GeoPoint, p1: GeoPoint, p2: GeoPoint): Double {
        val midLat = Math.toRadians((p1.latitude + p2.latitude + point.latitude) / 3.0)
        val cosMidLat = kotlin.math.cos(midLat)

        // Vector along segment (p1 -> p2) in metres (East, North)
        val segDx = Math.toRadians(p2.longitude - p1.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosMidLat
        val segDy = Math.toRadians(p2.latitude - p1.latitude) * GeoUtils.EARTH_RADIUS_METRES
        val segLen = kotlin.math.sqrt(segDx * segDx + segDy * segDy)
        if (segLen < 1e-4) return 0.0

        // Vector from p1 to query point in metres (East, North)
        val ptDx = Math.toRadians(point.longitude - p1.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosMidLat
        val ptDy = Math.toRadians(point.latitude - p1.latitude) * GeoUtils.EARTH_RADIUS_METRES

        // 2D Cross Product: segDx * ptDy - segDy * ptDx
        // Positive = LHS, Negative = RHS
        val cross = segDx * ptDy - segDy * ptDx
        return cross / segLen
    }
}
