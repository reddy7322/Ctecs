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
        var bestProjection: SegmentProjection? = null
        var bestSegmentMarkerBefore: RouteMarker? = null
        var bestSegmentMarkerAfter: RouteMarker? = null
        var minPerpDistance = Double.MAX_VALUE
        var matchedAlignmentName = ""

        // Check each alignment polyline
        for (alignment in routeData.alignments) {
            val pts = alignment.points
            if (pts.size < 2) continue

            for (i in 0 until pts.size - 1) {
                val p1 = pts[i]
                val p2 = pts[i + 1]

                // Quick bounding box check with 100m padding (~0.001 deg)
                val minLat = minOf(p1.latitude, p2.latitude) - 0.001
                val maxLat = maxOf(p1.latitude, p2.latitude) + 0.001
                val minLng = minOf(p1.longitude, p2.longitude) - 0.001
                val maxLng = maxOf(p1.longitude, p2.longitude) + 0.001

                if (gpsPoint.latitude !in minLat..maxLat || gpsPoint.longitude !in minLng..maxLng) {
                    continue
                }

                val proj = GeoUtils.projectPointToSegment(gpsPoint, p1, p2)
                if (proj.perpendicularDistance < minPerpDistance) {
                    minPerpDistance = proj.perpendicularDistance
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
        val finalProjectionPoint = if (bestProjection != null && bestProjection.perpendicularDistance <= minPerpDistance) {
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

        // 4. Carriageway LHS / RHS detection based on road alignment geometry & travel direction
        var detectedSide = "Unknown"
        if (confidence == MatchConfidence.HIGH || confidence == MatchConfidence.MEDIUM) {
            val lowerAlign = matchedAlignmentName.lowercase(Locale.ROOT)
            val lowerRouteName = routeData.name.lowercase(Locale.ROOT)

            if (lowerAlign.contains("lhs") || lowerAlign.contains("left") || lowerRouteName.contains("lhs")) {
                detectedSide = "LHS"
            } else if (lowerAlign.contains("rhs") || lowerAlign.contains("right") || lowerRouteName.contains("rhs")) {
                detectedSide = "RHS"
            } else if (bestProjection != null && bestProjection.p1 != null && bestProjection.p2 != null && effectiveOffset >= 1.5) {
                val p1 = bestProjection.p1!!
                val p2 = bestProjection.p2!!
                val midLat = Math.toRadians((p1.latitude + p2.latitude + gpsPoint.latitude) / 3.0)
                val cosMidLat = kotlin.math.cos(midLat)

                val segDx = Math.toRadians(p2.longitude - p1.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosMidLat
                val segDy = Math.toRadians(p2.latitude - p1.latitude) * GeoUtils.EARTH_RADIUS_METRES

                val px = Math.toRadians(gpsPoint.longitude - bestProjection.projectedPoint.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosMidLat
                val py = Math.toRadians(gpsPoint.latitude - bestProjection.projectedPoint.latitude) * GeoUtils.EARTH_RADIUS_METRES

                // 2D cross product: segDx * py - segDy * px
                // In East-North coordinates, looking along segment (p1 -> p2),
                // cross < 0 corresponds to being on the right side of the centerline.
                val cross = segDx * py - segDy * px
                val isRightOfAlignment = cross < 0.0

                var isReverseTravel = false
                if (bearing != null && bearing >= 0f) {
                    val segBearing = GeoUtils.bearingDegrees(p1, p2)
                    val diff = (abs(bearing - segBearing) % 360)
                    val acuteDiff = if (diff > 180) 360 - diff else diff
                    if (acuteDiff > 90) {
                        isReverseTravel = true
                    }
                }

                detectedSide = when {
                    isReverseTravel -> if (isRightOfAlignment) "LHS" else "RHS"
                    else -> if (isRightOfAlignment) "RHS" else "LHS"
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
}
