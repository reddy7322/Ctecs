package com.example.geo

import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * High-Precision Single KMZ Alignment Chainage Matcher.
 *
 * Implements:
 * - Nearest-segment geodesic projection (instead of nearest-vertex).
 * - Polyline cumulative distance interpolation along curves, bends, and transitions.
 * - Spatial indexing and segment caching for high performance on large alignments.
 * - Bracketing reference marker interpolation with support for increasing & decreasing chainage.
 * - Segment continuity checks & parallel carriageway (LHS/RHS/Service) hysteresis.
 * - Sudden chainage jump detection with graceful fallback to last reliable reading.
 * - Four-tier quality classification (High, Medium, Low, No Match).
 */
object ChainageMatcher {

    const val DEFAULT_HIGH_CONFIDENCE_OFFSET_METRES = 15.0
    const val DEFAULT_MAX_MATCH_OFFSET_METRES = 35.0
    const val DEFAULT_MAX_GPS_ACCURACY_METRES = 12.0
    const val MAXIMUM_OFF_ROUTE_DISTANCE_METRES = 100.0
    const val SUDDEN_JUMP_THRESHOLD_METRES = 250.0

    /**
     * Precomputed spatial index and geometry cache for a single alignment polyline.
     */
    data class CachedAlignmentGeometry(
        val alignmentId: String,
        val alignmentName: String,
        val direction: String,
        val carriagewayType: String,
        val points: List<GeoPoint>,
        val segmentCount: Int,
        val segmentLengths: DoubleArray,
        val cumulativeDistances: DoubleArray,
        val totalLengthM: Double,
        // Cached marker projections along this alignment
        val mappedMarkers: List<MappedMarkerRef>
    )

    data class MappedMarkerRef(
        val marker: RouteMarker,
        val distanceAlongAlignmentM: Double,
        val chainageMetres: Long
    )

    // Thread-safe spatial geometry cache keyed by "routeId:alignmentId:markerCount"
    private val geometryCache = ConcurrentHashMap<String, CachedAlignmentGeometry>()

    /**
     * Retrieves or calculates cached geometry for the alignment.
     */
    fun getOrCreateCachedGeometry(alignment: RouteAlignment, allMarkers: List<RouteMarker>): CachedAlignmentGeometry {
        val firstPt = alignment.points.firstOrNull()
        val lastPt = alignment.points.lastOrNull()
        val cacheKey = "${alignment.id}:${alignment.points.size}:${allMarkers.size}:${firstPt?.latitude},${firstPt?.longitude}:${lastPt?.latitude},${lastPt?.longitude}"
        val existing = geometryCache[cacheKey]
        if (existing != null) return existing

        val pts = alignment.points
        val n = pts.size
        val segLengths = DoubleArray(maxOf(0, n - 1))
        val cumDist = DoubleArray(n)
        var totalDist = 0.0

        for (i in 0 until n - 1) {
            val len = GeoUtils.distanceMetres(pts[i], pts[i + 1])
            segLengths[i] = len
            totalDist += len
            cumDist[i + 1] = totalDist
        }

        // Map reference markers to their cumulative distance along this alignment polyline
        val mapped = mutableListOf<MappedMarkerRef>()
        for (m in allMarkers) {
            // Find nearest segment for this marker along the alignment
            var bestDist = Double.MAX_VALUE
            var bestAlong = 0.0
            for (i in 0 until n - 1) {
                val proj = GeoUtils.projectPointToSegment(m.point, pts[i], pts[i + 1])
                if (proj.perpendicularDistance < bestDist) {
                    bestDist = proj.perpendicularDistance
                    bestAlong = cumDist[i] + proj.t * segLengths[i]
                }
            }
            if (bestDist <= 150.0) { // Marker belongs to this alignment
                mapped.add(MappedMarkerRef(m, bestAlong, m.chainage.metres))
            }
        }
        mapped.sortBy { it.distanceAlongAlignmentM }

        // If no explicit markers mapped to this alignment path, synthesize baseline chainage
        // directly from the path coordinates so every point along the path has an accurate baseline chainage
        if (mapped.isEmpty() && n >= 2 && totalDist > 0.0) {
            val inferredStartMetres = allMarkers.firstOrNull()?.chainage?.metres
                ?: Chainage.parse(alignment.name)?.metres
                ?: 0L
            val sign = if (alignment.direction == "DECREASING") -1 else 1
            val endMetres = (inferredStartMetres + sign * totalDist).roundToLong().coerceAtLeast(0L)
            val startCh = Chainage(inferredStartMetres)
            val endCh = Chainage(endMetres)
            mapped.add(
                MappedMarkerRef(
                    marker = RouteMarker("path-start-${alignment.id}", startCh.format(), startCh, pts.first()),
                    distanceAlongAlignmentM = 0.0,
                    chainageMetres = inferredStartMetres
                )
            )
            mapped.add(
                MappedMarkerRef(
                    marker = RouteMarker("path-end-${alignment.id}", endCh.format(), endCh, pts.last()),
                    distanceAlongAlignmentM = totalDist,
                    chainageMetres = endMetres
                )
            )
        }

        // Determine direction if not specified
        val dir = if (mapped.size >= 2) {
            if (mapped.last().chainageMetres >= mapped.first().chainageMetres) "INCREASING" else "DECREASING"
        } else {
            if (alignment.direction == "DECREASING") "DECREASING" else "INCREASING"
        }

        val cached = CachedAlignmentGeometry(
            alignmentId = alignment.id,
            alignmentName = alignment.name,
            direction = dir,
            carriagewayType = alignment.carriagewayType,
            points = pts,
            segmentCount = maxOf(0, n - 1),
            segmentLengths = segLengths,
            cumulativeDistances = cumDist,
            totalLengthM = totalDist,
            mappedMarkers = mapped
        )

        geometryCache[cacheKey] = cached
        return cached
    }

    /**
     * Matches a GPS coordinate to the active route with high geometric precision.
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
        if (routeData == null || (routeData.markers.isEmpty() && routeData.alignments.isEmpty())) {
            return ChainageMatchResult.noRoute(gpsAccuracy)
        }

        val prevAlignmentName = previousResult?.matchedAlignmentName.orEmpty()

        // 1. Determine candidate alignments to match against
        // If route has user-selected alignment, prioritize it
        val alignments = if (routeData.selectedAlignmentId != null) {
            val selected = routeData.alignments.filter { it.id == routeData.selectedAlignmentId }
            if (selected.isNotEmpty()) selected else routeData.alignments
        } else {
            routeData.alignments
        }

        if (alignments.isEmpty()) {
            return matchToMarkersDirectly(gpsPoint, gpsAccuracy, routeData.markers)
        }

        // 2. Perform nearest-segment projection across alignments with spatial bounding filter
        var bestProj: SegmentProjection? = null
        var bestAlignment: CachedAlignmentGeometry? = null
        var bestSegIdx = -1
        var bestDistanceAlongM = 0.0
        var minScore = Double.MAX_VALUE

        for (align in alignments) {
            if (align.points.size < 2) continue
            val cached = getOrCreateCachedGeometry(align, routeData.markers)
            val pts = cached.points

            for (i in 0 until cached.segmentCount) {
                val p1 = pts[i]
                val p2 = pts[i + 1]

                // Fast spatial bounding box rejection (with ~500m / 0.005° margin)
                val minLat = minOf(p1.latitude, p2.latitude) - 0.005
                val maxLat = maxOf(p1.latitude, p2.latitude) + 0.005
                val minLng = minOf(p1.longitude, p2.longitude) - 0.005
                val maxLng = maxOf(p1.longitude, p2.longitude) + 0.005

                if (gpsPoint.latitude !in minLat..maxLat || gpsPoint.longitude !in minLng..maxLng) {
                    continue
                }

                val proj = GeoUtils.projectPointToSegment(gpsPoint, p1, p2)

                // Parallel carriageway & continuity bonus:
                // Give previously matched alignment a 5.0m hysteresis advantage
                // to prevent rapid fluttering between LHS/RHS or Service road across median
                val isPrevAlignment = prevAlignmentName.isNotBlank() && align.name == prevAlignmentName
                val continuityBonus = if (isPrevAlignment) 5.0 else 0.0
                val score = proj.perpendicularDistance - continuityBonus

                if (score < minScore) {
                    minScore = score
                    bestProj = proj
                    bestAlignment = cached
                    bestSegIdx = i
                    bestDistanceAlongM = cached.cumulativeDistances[i] + proj.t * cached.segmentLengths[i]
                }
            }
        }

        // Fallback to direct marker matching if no alignment polyline matched closely
        if (bestProj == null || bestAlignment == null) {
            return matchToMarkersDirectly(gpsPoint, gpsAccuracy, routeData.markers)
        }

        val effectiveOffset = bestProj.perpendicularDistance
        val finalProjectionPoint = bestProj.projectedPoint
        val segLength = if (bestSegIdx in bestAlignment.segmentLengths.indices) bestAlignment.segmentLengths[bestSegIdx] else 0.0

        // 3. Interpolate Chainage along polyline using mapped reference markers
        val mappedMarkers = bestAlignment.mappedMarkers.ifEmpty {
            // If alignment had no mapped markers, use global markers
            routeData.markers.map { MappedMarkerRef(it, 0.0, it.chainage.metres) }
        }

        var estimatedChainageMetres: Long
        var bracketingInfo: String? = null
        var nearestMarkerLabel: String? = null
        var referenceStatus = "Valid"

        if (mappedMarkers.isEmpty()) {
            estimatedChainageMetres = 0L
            referenceStatus = "No references found"
        } else if (mappedMarkers.size == 1) {
            val single = mappedMarkers[0]
            val deltaDist = bestDistanceAlongM - single.distanceAlongAlignmentM
            val sign = if (bestAlignment.direction == "DECREASING") -1 else 1
            estimatedChainageMetres = (single.chainageMetres + sign * deltaDist).roundToLong()
            nearestMarkerLabel = single.marker.label
            bracketingInfo = "Single Ref: ${single.marker.label}"
        } else {
            // Find bracketing reference markers: mA <= bestDistanceAlongM <= mB
            val first = mappedMarkers.first()
            val last = mappedMarkers.last()

            if (bestDistanceAlongM <= first.distanceAlongAlignmentM) {
                // Before first marker: extrapolate along polyline
                val distFromFirst = bestDistanceAlongM - first.distanceAlongAlignmentM
                val sign = if (first.chainageMetres <= last.chainageMetres) 1 else -1
                estimatedChainageMetres = (first.chainageMetres + sign * distFromFirst).roundToLong()
                nearestMarkerLabel = first.marker.label
                bracketingInfo = "Pre-start: Extrapolated from ${first.marker.label}"
                referenceStatus = "Extrapolated (Pre-Start)"
            } else if (bestDistanceAlongM >= last.distanceAlongAlignmentM) {
                // Past last marker: extrapolate along polyline
                val distFromLast = bestDistanceAlongM - last.distanceAlongAlignmentM
                val sign = if (first.chainageMetres <= last.chainageMetres) 1 else -1
                estimatedChainageMetres = (last.chainageMetres + sign * distFromLast).roundToLong()
                nearestMarkerLabel = last.marker.label
                bracketingInfo = "Post-end: Extrapolated from ${last.marker.label}"
                referenceStatus = "Extrapolated (Post-End)"
            } else {
                // Interpolate between bracketing markers along the polyline
                var mA = first
                var mB = last
                for (k in 0 until mappedMarkers.size - 1) {
                    val curr = mappedMarkers[k]
                    val next = mappedMarkers[k + 1]
                    if (bestDistanceAlongM in curr.distanceAlongAlignmentM..next.distanceAlongAlignmentM) {
                        mA = curr
                        mB = next
                        break
                    }
                }

                val spanDistanceM = mB.distanceAlongAlignmentM - mA.distanceAlongAlignmentM
                val fraction = if (spanDistanceM > 0.01) {
                    ((bestDistanceAlongM - mA.distanceAlongAlignmentM) / spanDistanceM).coerceIn(0.0, 1.0)
                } else 0.0

                val deltaChainage = mB.chainageMetres - mA.chainageMetres
                estimatedChainageMetres = (mA.chainageMetres + fraction * deltaChainage).roundToLong()
                nearestMarkerLabel = if (fraction < 0.5) mA.marker.label else mB.marker.label
                bracketingInfo = "${mA.marker.label} ↔ ${mB.marker.label}"
            }
        }

        // 4. Continuity check: Detect sudden chainage jumps
        var isUncertain = false
        var lastReliableChainage: Chainage? = previousResult?.chainage

        if (previousResult != null && previousResult.chainage != null && previousResult.confidence != MatchConfidence.NO_MATCH) {
            val chainageJump = abs(estimatedChainageMetres - previousResult.chainage.metres)
            // If jump exceeds threshold (> 250m) in a short interval, flag as uncertain
            if (chainageJump > SUDDEN_JUMP_THRESHOLD_METRES) {
                isUncertain = true
                // Retain last reliable chainage when uncertain jump occurs
                lastReliableChainage = previousResult.chainage
            }
        }

        // 5. Four-Tier Confidence Scoring
        val isAccuracyAcceptable = gpsAccuracy <= DEFAULT_MAX_GPS_ACCURACY_METRES
        val confidence: MatchConfidence
        val statusMessage: String

        when {
            effectiveOffset > MAXIMUM_OFF_ROUTE_DISTANCE_METRES -> {
                confidence = MatchConfidence.NO_MATCH
                statusMessage = String.format(Locale.US, "Off Route (%.0fm from %s)", effectiveOffset, bestAlignment.alignmentName)
            }
            isUncertain -> {
                confidence = MatchConfidence.LOW
                statusMessage = String.format(Locale.US, "Sudden Jump (Offset: %.1fm, GPS: ±%.1fm)", effectiveOffset, gpsAccuracy)
            }
            effectiveOffset <= highConfidenceOffsetMetres && isAccuracyAcceptable -> {
                confidence = MatchConfidence.HIGH
                statusMessage = String.format(Locale.US, "High Precision Match (Offset: %.1fm, GPS: ±%.1fm)", effectiveOffset, gpsAccuracy)
            }
            effectiveOffset <= maxMatchOffsetMetres && gpsAccuracy <= 25.0f -> {
                confidence = MatchConfidence.MEDIUM
                statusMessage = String.format(Locale.US, "Medium Confidence (Offset: %.1fm, GPS: ±%.1fm)", effectiveOffset, gpsAccuracy)
            }
            else -> {
                confidence = MatchConfidence.LOW
                statusMessage = String.format(Locale.US, "Low Confidence (Offset: %.1fm > 35m, GPS: ±%.1fm)", effectiveOffset, gpsAccuracy)
            }
        }

        // 6. Direction & Carriageway Side (LHS / RHS) Detection
        var detectedSide = bestAlignment.carriagewayType
        if (detectedSide == "MAIN" || detectedSide == "UNKNOWN") {
            // Determine side relative to nearest segment direction
            if (bestProj.p1 != null && bestProj.p2 != null) {
                val lateral = calculateSignedLateralOffsetMetres(gpsPoint, bestProj.p1!!, bestProj.p2!!)
                detectedSide = when {
                    abs(lateral) < 1.0 -> previousResult?.detectedSide ?: "RHS"
                    lateral > 0.0 -> if (bestAlignment.direction == "DECREASING") "RHS" else "LHS"
                    else -> if (bestAlignment.direction == "DECREASING") "LHS" else "RHS"
                }
            } else {
                detectedSide = previousResult?.detectedSide ?: "RHS"
            }
        }

        val finalChainage = if (isUncertain && lastReliableChainage != null) {
            lastReliableChainage
        } else {
            Chainage(estimatedChainageMetres)
        }

        return ChainageMatchResult(
            chainage = finalChainage,
            projectedPoint = finalProjectionPoint,
            offsetMetres = effectiveOffset,
            gpsAccuracy = gpsAccuracy,
            confidence = confidence,
            statusMessage = statusMessage,
            isManual = false,
            matchedAlignmentName = bestAlignment.alignmentName,
            detectedSide = detectedSide,
            matchedSegmentIndex = bestSegIdx,
            matchedSegmentLengthM = segLength,
            segmentProjectionT = bestProj.t,
            alignmentDirection = bestAlignment.direction,
            isUncertain = isUncertain,
            lastReliableChainage = lastReliableChainage,
            distanceAlongAlignmentMetres = bestDistanceAlongM,
            nearestReferenceMarkerLabel = nearestMarkerLabel,
            bracketingMarkersInfo = bracketingInfo,
            sourceKmzReferenceStatus = referenceStatus
        )
    }

    private fun matchToMarkersDirectly(
        gpsPoint: GeoPoint,
        gpsAccuracy: Float,
        markers: List<RouteMarker>
    ): ChainageMatchResult {
        if (markers.isEmpty()) {
            return ChainageMatchResult.noRoute(gpsAccuracy)
        }

        val nearest = markers.minByOrNull { GeoUtils.distanceMetres(gpsPoint, it.point) } ?: markers[0]
        val dist = GeoUtils.distanceMetres(gpsPoint, nearest.point)

        val confidence = when {
            dist <= DEFAULT_HIGH_CONFIDENCE_OFFSET_METRES && gpsAccuracy <= DEFAULT_MAX_GPS_ACCURACY_METRES -> MatchConfidence.HIGH
            dist <= DEFAULT_MAX_MATCH_OFFSET_METRES -> MatchConfidence.MEDIUM
            dist <= MAXIMUM_OFF_ROUTE_DISTANCE_METRES -> MatchConfidence.LOW
            else -> MatchConfidence.NO_MATCH
        }

        return ChainageMatchResult(
            chainage = nearest.chainage,
            projectedPoint = nearest.point,
            offsetMetres = dist,
            gpsAccuracy = gpsAccuracy,
            confidence = confidence,
            statusMessage = String.format(Locale.US, "Direct Marker Match (±%.1fm)", dist),
            isManual = false,
            nearestReferenceMarkerLabel = nearest.label,
            sourceKmzReferenceStatus = "Direct Marker Match"
        )
    }

    /**
     * Calculates signed lateral offset in metres:
     * > 0: Left of segment vector (p1 -> p2)
     * < 0: Right of segment vector
     */
    fun calculateSignedLateralOffsetMetres(point: GeoPoint, p1: GeoPoint, p2: GeoPoint): Double {
        val midLat = Math.toRadians((p1.latitude + p2.latitude + point.latitude) / 3.0)
        val cosMidLat = kotlin.math.cos(midLat)

        val segDx = Math.toRadians(p2.longitude - p1.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosMidLat
        val segDy = Math.toRadians(p2.latitude - p1.latitude) * GeoUtils.EARTH_RADIUS_METRES
        val segLen = kotlin.math.sqrt(segDx * segDx + segDy * segDy)
        if (segLen < 1e-4) return 0.0

        val ptDx = Math.toRadians(point.longitude - p1.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosMidLat
        val ptDy = Math.toRadians(point.latitude - p1.latitude) * GeoUtils.EARTH_RADIUS_METRES

        val cross = segDx * ptDy - segDy * ptDx
        return cross / segLen
    }

    /**
     * Clears spatial geometry caches when route data is updated or unmounted.
     */
    fun clearCache() {
        geometryCache.clear()
    }
}
