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
import java.util.concurrent.atomic.AtomicLong
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
 * - Single-spike jump filtering with automatic next-fix recovery (never locks or freezes chainage).
 * - Baseline uncalibrated jump comparison so large calibration offsets (> 250m) never trigger false jump locks.
 * - Four-tier quality classification (High, Medium, Low, No Match).
 */
object ChainageMatcher {

    const val DEFAULT_HIGH_CONFIDENCE_OFFSET_METRES = 15.0
    const val DEFAULT_MAX_MATCH_OFFSET_METRES = 35.0
    const val DEFAULT_MAX_GPS_ACCURACY_METRES = 12.0
    const val MAXIMUM_OFF_ROUTE_DISTANCE_METRES = 100.0
    const val SUDDEN_JUMP_THRESHOLD_METRES = 250.0
    private const val MAX_SPIKE_HOLD_WINDOW_MS = 8_000L

    // Lightweight internal performance telemetry
    private val cacheHits = AtomicLong(0L)
    private val cacheMisses = AtomicLong(0L)
    private val lastMatchLatencyMicros = AtomicLong(0L)
    private val automaticJumpRecoveries = AtomicLong(0L)

    fun getCacheHits(): Long = cacheHits.get()
    fun getCacheMisses(): Long = cacheMisses.get()
    fun getLastMatchLatencyMicros(): Long = lastMatchLatencyMicros.get()
    fun getAutomaticJumpRecoveries(): Long = automaticJumpRecoveries.get()

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

    // Thread-safe spatial geometry cache keyed by alignment geometry signature
    private val geometryCache = ConcurrentHashMap<String, CachedAlignmentGeometry>()

    /**
     * Retrieves or calculates cached geometry for the alignment.
     */
    fun getOrCreateCachedGeometry(alignment: RouteAlignment, allMarkers: List<RouteMarker>): CachedAlignmentGeometry {
        val firstPt = alignment.points.firstOrNull()
        val lastPt = alignment.points.lastOrNull()
        val firstMCh = allMarkers.firstOrNull()?.chainage?.metres ?: -1L
        val lastMCh = allMarkers.lastOrNull()?.chainage?.metres ?: -1L
        val cacheKey = "${alignment.id}:${alignment.direction}:${alignment.points.size}:${allMarkers.size}:$firstMCh:$lastMCh:${firstPt?.latitude},${firstPt?.longitude}:${lastPt?.latitude},${lastPt?.longitude}"
        val existing = geometryCache[cacheKey]
        if (existing != null) {
            cacheHits.incrementAndGet()
            return existing
        }
        cacheMisses.incrementAndGet()

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
        data class CandidateMarkerProj(
            val ref: MappedMarkerRef,
            val perpDist: Double
        )
        val rawMapped = ArrayList<CandidateMarkerProj>(allMarkers.size)
        for (m in allMarkers) {
            val mLat = m.point.latitude
            val mLng = m.point.longitude
            var bestDist = Double.MAX_VALUE
            var bestAlong = 0.0
            for (i in 0 until n - 1) {
                val p1 = pts[i]
                val p2 = pts[i + 1]
                // Fast bounding-box pre-filter (~220m margin around segment)
                val minLat = if (p1.latitude < p2.latitude) p1.latitude else p2.latitude
                val maxLat = if (p1.latitude > p2.latitude) p1.latitude else p2.latitude
                if (mLat < minLat - 0.002 || mLat > maxLat + 0.002) continue

                val minLng = if (p1.longitude < p2.longitude) p1.longitude else p2.longitude
                val maxLng = if (p1.longitude > p2.longitude) p1.longitude else p2.longitude
                if (mLng < minLng - 0.002 || mLng > maxLng + 0.002) continue

                val proj = GeoUtils.projectPointToSegment(m.point, p1, p2)
                if (proj.perpendicularDistance < bestDist) {
                    bestDist = proj.perpendicularDistance
                    bestAlong = cumDist[i] + proj.t * segLengths[i]
                }
            }
            if (bestDist <= 150.0) { // Marker belongs to this alignment
                rawMapped.add(CandidateMarkerProj(MappedMarkerRef(m, bestAlong, m.chainage.metres), bestDist))
            }
        }
        rawMapped.sortBy { it.ref.distanceAlongAlignmentM }

        // Deduplicate markers that clamp to the exact same alignment distance (e.g. off-end markers at 0.0 or totalDist)
        val mapped = ArrayList<MappedMarkerRef>(rawMapped.size)
        for (cand in rawMapped) {
            if (mapped.isEmpty()) {
                mapped.add(cand.ref)
            } else {
                val prev = mapped.last()
                if (abs(cand.ref.distanceAlongAlignmentM - prev.distanceAlongAlignmentM) < 0.5) {
                    // Keep the marker whose actual coordinate is closer to that point on the alignment
                    val prevDist = GeoUtils.distanceMetres(
                        prev.marker.point,
                        if (prev.distanceAlongAlignmentM <= 0.5) pts.first() else pts.last()
                    )
                    if (cand.perpDist < prevDist) {
                        mapped[mapped.lastIndex] = cand.ref
                    }
                } else {
                    mapped.add(cand.ref)
                }
            }
        }

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
     * Interpolates exact geographical coordinate along the cached alignment at a target alignment distance.
     */
    fun interpolatePointAtDistanceAlongAlignment(
        cached: CachedAlignmentGeometry,
        distanceAlongMetres: Double
    ): GeoPoint {
        val pts = cached.points
        if (pts.isEmpty()) return GeoPoint(0.0, 0.0)
        if (pts.size == 1 || cached.totalLengthM <= 0.0) return pts.first()

        val clampedDist = distanceAlongMetres.coerceIn(0.0, cached.totalLengthM)
        if (clampedDist <= 0.0) return pts.first()
        if (clampedDist >= cached.totalLengthM) return pts.last()

        // Binary search or linear scan over cumulativeDistances
        var segIdx = 0
        for (i in 0 until cached.segmentCount) {
            val segStart = cached.cumulativeDistances[i]
            val segEnd = cached.cumulativeDistances[i + 1]
            if (clampedDist in segStart..segEnd) {
                segIdx = i
                break
            }
        }
        val segLen = cached.segmentLengths[segIdx]
        if (segLen <= 1e-6) return pts[segIdx]

        val fraction = ((clampedDist - cached.cumulativeDistances[segIdx]) / segLen).coerceIn(0.0, 1.0)
        val p1 = pts[segIdx]
        val p2 = pts[segIdx + 1]
        val lat = p1.latitude + fraction * (p2.latitude - p1.latitude)
        val lng = p1.longitude + fraction * (p2.longitude - p1.longitude)
        val alt = if (p1.altitude != null && p2.altitude != null) {
            p1.altitude + fraction * (p2.altitude - p1.altitude)
        } else {
            p1.altitude ?: p2.altitude
        }
        return GeoPoint(lat, lng, alt)
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
        val startNs = System.nanoTime()
        try {
            if (routeData == null || (routeData.markers.isEmpty() && routeData.alignments.isEmpty())) {
                return ChainageMatchResult.noRoute(gpsAccuracy)
            }

            val prevAlignmentName = previousResult?.matchedAlignmentName.orEmpty()

            // 1. Determine candidate alignments to match against
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
                val first = mappedMarkers.first()
                val last = mappedMarkers.last()

                if (bestDistanceAlongM <= first.distanceAlongAlignmentM) {
                    val distFromFirst = bestDistanceAlongM - first.distanceAlongAlignmentM
                    val sign = if (first.chainageMetres <= last.chainageMetres) 1 else -1
                    estimatedChainageMetres = (first.chainageMetres + sign * distFromFirst).roundToLong()
                    nearestMarkerLabel = first.marker.label
                    bracketingInfo = "Pre-start: Extrapolated from ${first.marker.label}"
                    referenceStatus = "Extrapolated (Pre-Start)"
                } else if (bestDistanceAlongM >= last.distanceAlongAlignmentM) {
                    val distFromLast = bestDistanceAlongM - last.distanceAlongAlignmentM
                    val sign = if (first.chainageMetres <= last.chainageMetres) 1 else -1
                    estimatedChainageMetres = (last.chainageMetres + sign * distFromLast).roundToLong()
                    nearestMarkerLabel = last.marker.label
                    bracketingInfo = "Post-end: Extrapolated from ${last.marker.label}"
                    referenceStatus = "Extrapolated (Post-End)"
                } else {
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

            // 4. Continuity check: Detect single-spike chainage jumps without ever locking permanently.
            // CRITICAL: Always compare against the UNCALIBRATED baseline chainage of previousResult
            // so that a calibration shift > 250m never triggers a false jump lock!
            var isUncertain = false
            val prevBaselineChainage = previousResult?.rawUncorrectedChainage ?: previousResult?.chainage
            var lastReliableChainage: Chainage? = prevBaselineChainage

            if (previousResult != null && prevBaselineChainage != null && previousResult.confidence != MatchConfidence.NO_MATCH) {
                val chainageJump = abs(estimatedChainageMetres - prevBaselineChainage.metres)
                if (chainageJump > SUDDEN_JUMP_THRESHOLD_METRES) {
                    val elapsedMs = abs(System.currentTimeMillis() - previousResult.timestamp)
                    if (previousResult.isUncertain || elapsedMs > MAX_SPIKE_HOLD_WINDOW_MS) {
                        // Previous reading already held a spike once, or > 8s elapsed:
                        // Automatically recover and snap to the new position so chainage never freezes!
                        isUncertain = false
                        lastReliableChainage = Chainage(estimatedChainageMetres)
                        automaticJumpRecoveries.incrementAndGet()
                    } else {
                        // Single transient GPS spike: hold previous baseline chainage for this 1 reading
                        isUncertain = true
                        lastReliableChainage = prevBaselineChainage
                    }
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
                rawUncorrectedChainage = finalChainage,
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
        } finally {
            lastMatchLatencyMicros.set((System.nanoTime() - startNs) / 1_000L)
        }
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
            rawUncorrectedChainage = nearest.chainage,
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
     * Validates whether a non-stale spatial cache entry already exists for [alignment] & [allMarkers].
     */
    fun isCacheValidFor(alignment: RouteAlignment, allMarkers: List<RouteMarker>): Boolean {
        if (alignment.points.size < 2) return false
        val firstPt = alignment.points.firstOrNull()
        val lastPt = alignment.points.lastOrNull()
        val firstMCh = allMarkers.firstOrNull()?.chainage?.metres ?: -1L
        val lastMCh = allMarkers.lastOrNull()?.chainage?.metres ?: -1L
        val cacheKey = "${alignment.id}:${alignment.direction}:${alignment.points.size}:${allMarkers.size}:$firstMCh:$lastMCh:${firstPt?.latitude},${firstPt?.longitude}:${lastPt?.latitude},${lastPt?.longitude}"
        val cached = geometryCache[cacheKey] ?: return false
        return cached.segmentCount == alignment.points.size - 1 && cached.totalLengthM > 0.0
    }

    /**
     * Clears spatial geometry caches when route data is updated or unmounted.
     */
    fun clearCache() {
        geometryCache.clear()
    }
}
