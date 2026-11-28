package com.example.geo

import com.example.model.CenterlineOffsetZone
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.ParallelCenterlineOffset
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

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

    // Multi-fix spatial jump confirmation state: prevents stale/glitched GPS fixes
    // (e.g. 171+000 -> 130+000) or duplicate-timestamp callbacks from replacing a valid chainage,
    // while scaling candidate step tolerance with velocity so real recovery never locks.
    private data class PendingJumpCandidate(
        val routeKey: String,
        val gpsPoint: GeoPoint,
        val estimatedMetres: Long,
        val alignmentName: String,
        val fixTimeMs: Long,
        val confirmCount: Int = 1
    )

    private val jumpValidationLock = Any()
    private var pendingJumpCandidate: PendingJumpCandidate? = null

    fun resetJumpValidationState() {
        synchronized(jumpValidationLock) {
            pendingJumpCandidate = null
        }
    }

    fun getCacheHits(): Long = cacheHits.get()
    fun getCacheMisses(): Long = cacheMisses.get()
    fun getLastMatchLatencyMicros(): Long = lastMatchLatencyMicros.get()
    fun getAutomaticJumpRecoveries(): Long = automaticJumpRecoveries.get()

    private const val SPATIAL_GRID_CELL_DEG = 0.002 // ~220m spatial grid cells for O(1) lookup
    private const val MAX_CELLS_PER_SEGMENT = 64

    private fun packGridKey(cellLat: Int, cellLng: Int): Long {
        return (cellLat.toLong() shl 32) or (cellLng.toLong() and 0xFFFF_FFFFL)
    }

    private fun latToCell(lat: Double): Int = floor(lat / SPATIAL_GRID_CELL_DEG).toInt()
    private fun lngToCell(lng: Double): Int = floor(lng / SPATIAL_GRID_CELL_DEG).toInt()

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
        val segmentBearingsDeg: DoubleArray,
        val cumulativeDistances: DoubleArray,
        val totalLengthM: Double,
        // 2D spatial hash grid mapping packed (cellLat, cellLng) -> segment indices intersecting cell
        val spatialGrid: Map<Long, IntArray>,
        // Segments spanning more than MAX_CELLS_PER_SEGMENT cells checked directly on query
        val longSegmentIndices: IntArray,
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
        val segCount = maxOf(0, n - 1)
        val segLengths = DoubleArray(segCount)
        val segBearings = DoubleArray(segCount)
        val cumDist = DoubleArray(n)
        var totalDist = 0.0

        val gridBuilder = HashMap<Long, MutableList<Int>>(segCount.coerceAtLeast(16))
        val longSegments = ArrayList<Int>()

        for (i in 0 until segCount) {
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val len = GeoUtils.distanceMetres(p1, p2)
            segLengths[i] = len
            segBearings[i] = GeoUtils.bearingDegrees(p1, p2)
            totalDist += len
            cumDist[i + 1] = totalDist

            val minCellLat = latToCell(minOf(p1.latitude, p2.latitude))
            val maxCellLat = latToCell(maxOf(p1.latitude, p2.latitude))
            val minCellLng = lngToCell(minOf(p1.longitude, p2.longitude))
            val maxCellLng = lngToCell(maxOf(p1.longitude, p2.longitude))
            val spanCells = (maxCellLat - minCellLat + 1L) * (maxCellLng - minCellLng + 1L)
            if (spanCells > MAX_CELLS_PER_SEGMENT) {
                longSegments.add(i)
            } else {
                for (cLat in minCellLat..maxCellLat) {
                    for (cLng in minCellLng..maxCellLng) {
                        val key = packGridKey(cLat, cLng)
                        var bucket = gridBuilder[key]
                        if (bucket == null) {
                            bucket = ArrayList(4)
                            gridBuilder[key] = bucket
                        }
                        bucket.add(i)
                    }
                }
            }
        }

        val spatialGrid = HashMap<Long, IntArray>(gridBuilder.size)
        for ((k, list) in gridBuilder) {
            spatialGrid[k] = list.toIntArray()
        }
        val longSegmentIndices = longSegments.toIntArray()

        // Map reference markers to their cumulative distance along this alignment polyline via O(1) spatial grid
        data class CandidateMarkerProj(
            val ref: MappedMarkerRef,
            val perpDist: Double
        )
        val rawMapped = ArrayList<CandidateMarkerProj>(allMarkers.size)
        val wideMapped = ArrayList<CandidateMarkerProj>(allMarkers.size)
        val seenMarkerSegments = HashSet<Int>(32)
        for (m in allMarkers) {
            val mLat = m.point.latitude
            val mLng = m.point.longitude
            val cLat = latToCell(mLat)
            val cLng = lngToCell(mLng)
            seenMarkerSegments.clear()
            var bestDist = Double.MAX_VALUE
            var bestAlong = 0.0

            for (dLat in -1..1) {
                for (dLng in -1..1) {
                    val segs = spatialGrid[packGridKey(cLat + dLat, cLng + dLng)] ?: continue
                    for (i in segs) {
                        if (!seenMarkerSegments.add(i)) continue
                        val proj = GeoUtils.projectPointToSegment(m.point, pts[i], pts[i + 1])
                        if (proj.perpendicularDistance < bestDist) {
                            bestDist = proj.perpendicularDistance
                            bestAlong = cumDist[i] + proj.t * segLengths[i]
                        }
                    }
                }
            }
            for (i in longSegmentIndices) {
                if (!seenMarkerSegments.add(i)) continue
                val proj = GeoUtils.projectPointToSegment(m.point, pts[i], pts[i + 1])
                if (proj.perpendicularDistance < bestDist) {
                    bestDist = proj.perpendicularDistance
                    bestAlong = cumDist[i] + proj.t * segLengths[i]
                }
            }
            if (bestDist <= 150.0) { // Marker belongs directly to this alignment
                rawMapped.add(CandidateMarkerProj(MappedMarkerRef(m, bestAlong, m.chainage.metres), bestDist))
            } else if (bestDist <= 500.0) {
                wideMapped.add(CandidateMarkerProj(MappedMarkerRef(m, bestAlong, m.chainage.metres), bestDist))
            }
        }
        if (rawMapped.isEmpty() && wideMapped.isNotEmpty()) {
            rawMapped.addAll(wideMapped)
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
        // directly from the nearest marker to path start (never blind first marker 130+000)
        if (mapped.isEmpty() && n >= 2 && totalDist > 0.0) {
            val nearestStartMarker = allMarkers.minByOrNull { GeoUtils.distanceMetres(pts.first(), it.point) }
            val inferredStartMetres = Chainage.parse(alignment.name)?.metres
                ?: nearestStartMarker?.chainage?.metres
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
            segmentCount = segCount,
            segmentLengths = segLengths,
            segmentBearingsDeg = segBearings,
            cumulativeDistances = cumDist,
            totalLengthM = totalDist,
            spatialGrid = spatialGrid,
            longSegmentIndices = longSegmentIndices,
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
        highConfidenceOffsetMetres: Double = DEFAULT_HIGH_CONFIDENCE_OFFSET_METRES,
        fixTimeMs: Long = System.currentTimeMillis()
    ): ChainageMatchResult {
        val startNs = System.nanoTime()
        val effectiveFixTimeMs = if (fixTimeMs > 0L) fixTimeMs else System.currentTimeMillis()
        try {
            if (gpsPoint.latitude.isNaN() || gpsPoint.longitude.isNaN() ||
                gpsPoint.latitude.isInfinite() || gpsPoint.longitude.isInfinite() ||
                (gpsPoint.latitude == 0.0 && gpsPoint.longitude == 0.0)
            ) {
                return ChainageMatchResult.noRoute(gpsAccuracy).copy(
                    statusMessage = "Invalid GPS coordinates",
                    timestamp = effectiveFixTimeMs
                )
            }
            if (routeData == null || (routeData.markers.isEmpty() && routeData.alignments.isEmpty())) {
                return ChainageMatchResult.noRoute(gpsAccuracy).copy(timestamp = effectiveFixTimeMs)
            }

            val prevAlignmentName = previousResult?.matchedAlignmentName.orEmpty()
            val prevDistAlongM = previousResult?.distanceAlongAlignmentMetres
                ?.takeIf { previousResult.matchedSegmentIndex >= 0 && it.isFinite() }

            // 1. Determine candidate alignments to match against
            val alignments = if (routeData.selectedAlignmentId != null) {
                val selected = routeData.alignments.filter { it.id == routeData.selectedAlignmentId }
                if (selected.isNotEmpty()) selected else routeData.alignments
            } else {
                routeData.alignments
            }

            if (alignments.isEmpty()) {
                return matchToMarkersDirectly(gpsPoint, gpsAccuracy, routeData.markers)
                    .copy(timestamp = effectiveFixTimeMs)
            }

            // 2. Perform O(1) spatial-grid nearest-segment projection across alignments
            var bestProj: SegmentProjection? = null
            var bestAlignment: CachedAlignmentGeometry? = null
            var bestSegIdx = -1
            var bestDistanceAlongM = 0.0
            var minScore = Double.MAX_VALUE

            val queryCellLat = latToCell(gpsPoint.latitude)
            val queryCellLng = lngToCell(gpsPoint.longitude)
            val hasMultiAlignments = alignments.size > 1
            val validBearing = bearing?.takeIf { !it.isNaN() && it.isFinite() }?.toDouble()
            val seenSegments = HashSet<Int>(32)

            fun evaluateSegment(
                cached: CachedAlignmentGeometry,
                alignName: String,
                segIdx: Int
            ) {
                val p1 = cached.points[segIdx]
                val p2 = cached.points[segIdx + 1]
                val proj = GeoUtils.projectPointToSegment(gpsPoint, p1, p2)
                val distAlong = cached.cumulativeDistances[segIdx] + proj.t * cached.segmentLengths[segIdx]

                // Parallel carriageway continuity hysteresis (5.0m normal / 7.5m under weaker GPS)
                val isPrevAlignment = prevAlignmentName.isNotBlank() && alignName == prevAlignmentName
                val alignmentContinuityBonus = if (isPrevAlignment) {
                    if (gpsAccuracy > 15.0f) 7.5 else 5.0
                } else 0.0

                // Longitudinal segment continuity tie-breaker: prevents snapping onto an unrelated
                // branch at tight curves, loops, cloverleafs, or crossing intersections when perpendicular
                // distances are similar, while still allowing forward OR reverse motion smoothly.
                var longitudinalBonus = 0.0
                if (isPrevAlignment && prevDistAlongM != null) {
                    val deltaAlong = abs(distAlong - prevDistAlongM)
                    longitudinalBonus = when {
                        deltaAlong <= 80.0 -> 4.0 * (1.0 - deltaAlong / 80.0)
                        deltaAlong > 300.0 && proj.perpendicularDistance <= 35.0 -> -3.5
                        else -> 0.0
                    }
                }

                // Parallel carriageway & intersection travel-bearing tie-breaker when multiple
                // alignments compete (never penalizes reverse driving on a single alignment).
                var bearingBonus = 0.0
                val isExplicitDirectionalCarriageway = cached.carriagewayType.equals("LHS", ignoreCase = true) ||
                    cached.carriagewayType.equals("RHS", ignoreCase = true)
                if (hasMultiAlignments && validBearing != null &&
                    segIdx in cached.segmentBearingsDeg.indices
                ) {
                    val segBearing = cached.segmentBearingsDeg[segIdx]
                    val diff = abs(((validBearing - segBearing + 540.0) % 360.0) - 180.0)
                    val cosAlign = cos(Math.toRadians(diff))
                    if (isExplicitDirectionalCarriageway) {
                        if (cosAlign >= 0.5) {
                            bearingBonus = 3.5
                        } else if (cosAlign <= -0.5) {
                            bearingBonus = -3.5
                        }
                    } else {
                        // Disambiguate intersecting/crossing alignments at junctions while allowing forward or reverse
                        val absCos = abs(cosAlign)
                        if (absCos >= 0.75) {
                            bearingBonus = 2.0
                        } else if (absCos <= 0.30) {
                            bearingBonus = -2.0
                        }
                    }
                }

                val score = proj.perpendicularDistance - alignmentContinuityBonus - longitudinalBonus - bearingBonus
                if (score < minScore) {
                    minScore = score
                    bestProj = proj
                    bestAlignment = cached
                    bestSegIdx = segIdx
                    bestDistanceAlongM = distAlong
                }
            }

            for (align in alignments) {
                if (align.points.size < 2) continue
                val cached = getOrCreateCachedGeometry(align, routeData.markers)
                seenSegments.clear()
                var checkedAnyGridSegment = false

                // Fast O(1) 3x3 neighborhood lookup (~±220m..440m around current GPS fix)
                for (dLat in -1..1) {
                    for (dLng in -1..1) {
                        val bucket = cached.spatialGrid[packGridKey(queryCellLat + dLat, queryCellLng + dLng)] ?: continue
                        for (segIdx in bucket) {
                            if (seenSegments.add(segIdx)) {
                                checkedAnyGridSegment = true
                                evaluateSegment(cached, align.name, segIdx)
                            }
                        }
                    }
                }
                for (segIdx in cached.longSegmentIndices) {
                    if (seenSegments.add(segIdx)) {
                        checkedAnyGridSegment = true
                        evaluateSegment(cached, align.name, segIdx)
                    }
                }

                // Fallback bounded scan ONLY if vehicle is farther away (>~220m) and grid had no close hit
                if (!checkedAnyGridSegment || (bestProj != null && bestProj!!.perpendicularDistance > MAXIMUM_OFF_ROUTE_DISTANCE_METRES)) {
                    val pts = cached.points
                    for (i in 0 until cached.segmentCount) {
                        if (seenSegments.contains(i)) continue
                        val p1 = pts[i]
                        val p2 = pts[i + 1]
                        val minLat = minOf(p1.latitude, p2.latitude) - 0.005
                        val maxLat = maxOf(p1.latitude, p2.latitude) + 0.005
                        val minLng = minOf(p1.longitude, p2.longitude) - 0.005
                        val maxLng = maxOf(p1.longitude, p2.longitude) + 0.005
                        if (gpsPoint.latitude !in minLat..maxLat || gpsPoint.longitude !in minLng..maxLng) {
                            continue
                        }
                        evaluateSegment(cached, align.name, i)
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
            val mappedMarkers = bestAlignment.mappedMarkers
            if (mappedMarkers.isEmpty()) {
                return matchToMarkersDirectly(gpsPoint, gpsAccuracy, routeData.markers)
                    .copy(timestamp = effectiveFixTimeMs)
            }

            var estimatedChainageMetres: Long
            var estimatedChainageMillimetres: Long
            var bracketingInfo: String? = null
            var nearestMarkerLabel: String? = null
            var referenceStatus = "Valid"

            if (mappedMarkers.isEmpty()) {
                estimatedChainageMetres = 0L
                estimatedChainageMillimetres = 0L
                referenceStatus = "No references found"
            } else if (mappedMarkers.size == 1) {
                val single = mappedMarkers[0]
                val deltaDist = bestDistanceAlongM - single.distanceAlongAlignmentM
                val sign = if (bestAlignment.direction == "DECREASING") -1 else 1
                val exactMetres = single.chainageMetres + sign * deltaDist
                estimatedChainageMillimetres = Chainage.metresToMillimetres(exactMetres)
                estimatedChainageMetres = Chainage.millimetresToRoundedMetres(estimatedChainageMillimetres)
                nearestMarkerLabel = single.marker.label
                bracketingInfo = "Single Ref: ${single.marker.label}"
            } else {
                val first = mappedMarkers.first()
                val last = mappedMarkers.last()

                if (bestDistanceAlongM <= first.distanceAlongAlignmentM) {
                    val distFromFirst = bestDistanceAlongM - first.distanceAlongAlignmentM
                    val sign = if (first.chainageMetres <= last.chainageMetres) 1 else -1
                    val exactMetres = first.chainageMetres + sign * distFromFirst
                    estimatedChainageMillimetres = Chainage.metresToMillimetres(exactMetres)
                    estimatedChainageMetres = Chainage.millimetresToRoundedMetres(estimatedChainageMillimetres)
                    nearestMarkerLabel = first.marker.label
                    bracketingInfo = "Pre-start: Extrapolated from ${first.marker.label}"
                    referenceStatus = "Extrapolated (Pre-Start)"
                } else if (bestDistanceAlongM >= last.distanceAlongAlignmentM) {
                    val distFromLast = bestDistanceAlongM - last.distanceAlongAlignmentM
                    val sign = if (first.chainageMetres <= last.chainageMetres) 1 else -1
                    val exactMetres = last.chainageMetres + sign * distFromLast
                    estimatedChainageMillimetres = Chainage.metresToMillimetres(exactMetres)
                    estimatedChainageMetres = Chainage.millimetresToRoundedMetres(estimatedChainageMillimetres)
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
                    val exactMetres = mA.chainageMetres + fraction * deltaChainage
                    estimatedChainageMillimetres = Chainage.metresToMillimetres(exactMetres)
                    estimatedChainageMetres = Chainage.millimetresToRoundedMetres(estimatedChainageMillimetres)
                    nearestMarkerLabel = if (fraction < 0.5) mA.marker.label else mB.marker.label
                    bracketingInfo = "${mA.marker.label} ↔ ${mB.marker.label}"
                }
            }

            // 4. Continuity & plausibility validation:
            // - Rejects out-of-order stale fix timestamps before they can regress chainage.
            // - Rejects poor-accuracy noise spikes cleanly.
            // - Validates large drops/jumps (e.g. 171+000 -> 130+000) against physical travel velocity
            //   (supporting BOTH forward and reverse movement equally) and requires 2 consecutive
            //   spatially-consistent, accurate, on-alignment fixes before snapping across an implausible jump.
            // - Always compares against the UNCALIBRATED baseline chainage of previousResult so that
            //   a calibration shift > 250m never triggers a false jump lock.
            var isUncertain = false
            var uncertainReason: String? = null
            val prevBaselineChainage = previousResult?.rawUncorrectedChainage ?: previousResult?.chainage
            var lastReliableChainage: Chainage? = prevBaselineChainage

            if (previousResult != null && prevBaselineChainage != null && previousResult.confidence != MatchConfidence.NO_MATCH) {
                val isOlderFixTimestamp = previousResult.timestamp > 0L && effectiveFixTimeMs + 500L < previousResult.timestamp
                val chainageJump = abs(estimatedChainageMetres - prevBaselineChainage.metres)
                val elapsedMs = (effectiveFixTimeMs - previousResult.timestamp).coerceAtLeast(0L)
                val dtSec = elapsedMs / 1000.0
                val isPoorAccuracySpike = gpsAccuracy > 35.0f && chainageJump > 25L
                // Upper physical travel bound across gaps; still catches multi-km teleports (e.g. 171+000 -> 130+000) cleanly
                val maxPlausibleDistM = maxOf(SUDDEN_JUMP_THRESHOLD_METRES, dtSec * 85.0 + 150.0)
                val isImplausibleSpeed = dtSec in 0.1..6.0 && chainageJump > maxOf(65.0, dtSec * 55.0 + 35.0)
                val isPhysicallyImpossibleJump = chainageJump > maxPlausibleDistM
                val isLargeJump = chainageJump > SUDDEN_JUMP_THRESHOLD_METRES

                when {
                    isOlderFixTimestamp -> {
                        isUncertain = true
                        uncertainReason = "STALE_GPS_FIX"
                        lastReliableChainage = prevBaselineChainage
                    }
                    isPoorAccuracySpike -> {
                        synchronized(jumpValidationLock) { pendingJumpCandidate = null }
                        isUncertain = true
                        uncertainReason = "POOR_GPS_ACCURACY"
                        lastReliableChainage = prevBaselineChainage
                    }
                    isLargeJump || isImplausibleSpeed -> {
                        val isHighQualityOnAlignment = gpsAccuracy <= 25.0f && effectiveOffset <= maxMatchOffsetMetres
                        val isRecoverableOnAlignment = gpsAccuracy <= 35.0f && effectiveOffset <= maxMatchOffsetMetres
                        val isPlausibleLongGapTravel = !isPhysicallyImpossibleJump &&
                            elapsedMs > MAX_SPIKE_HOLD_WINDOW_MS &&
                            isHighQualityOnAlignment

                        if (isPlausibleLongGapTravel) {
                            // Genuine travel (forward OR reverse) across a >8s GPS gap within physical velocity bounds
                            synchronized(jumpValidationLock) { pendingJumpCandidate = null }
                            isUncertain = false
                            lastReliableChainage = Chainage(estimatedChainageMetres)
                            automaticJumpRecoveries.incrementAndGet()
                        } else {
                            // Suspicious or physically impossible drop/jump (e.g. 171+000 -> 130+000):
                            // Require distinct, time-separated, spatially-consistent fixes (3 for impossible jumps from a
                            // reliable state, 2 otherwise), never confirming duplicate-timestamp callbacks and never
                            // locking highway-speed vehicles via a tiny fixed 60m step cap.
                            val routeKey = routeData.id
                            val isFromReliableState = !previousResult.isUncertain && previousResult.confidence != MatchConfidence.LOW
                            val requiredConfirmations = if (isPhysicallyImpossibleJump && isFromReliableState) 3 else 2
                            val canUseFixAsCandidate = if (isFromReliableState) isHighQualityOnAlignment else isRecoverableOnAlignment

                            val confirmedByMultiFix = synchronized(jumpValidationLock) {
                                val prevCand = pendingJumpCandidate
                                if (!canUseFixAsCandidate) {
                                    pendingJumpCandidate = null
                                    false
                                } else if (prevCand != null &&
                                    prevCand.routeKey == routeKey &&
                                    effectiveFixTimeMs == prevCand.fixTimeMs
                                ) {
                                    // Ignore duplicate-timestamp callback without resetting or advancing confirmation count
                                    false
                                } else {
                                    val candDeltaMs = if (prevCand != null) effectiveFixTimeMs - prevCand.fixTimeMs else -1L
                                    val candDtSec = candDeltaMs.coerceAtLeast(0L) / 1000.0
                                    val maxCandStepM = maxOf(80.0, candDtSec * 55.0 + 45.0)
                                    val matchesCandidate = prevCand != null &&
                                        prevCand.routeKey == routeKey &&
                                        candDeltaMs in 400L..15_000L &&
                                        GeoUtils.distanceMetres(prevCand.gpsPoint, gpsPoint) <= maxCandStepM &&
                                        abs(prevCand.estimatedMetres - estimatedChainageMetres) <= maxCandStepM.roundToLong() + 20L
                                    if (matchesCandidate) {
                                        val nextCount = prevCand!!.confirmCount + 1
                                        if (nextCount >= requiredConfirmations) {
                                            pendingJumpCandidate = null
                                            true
                                        } else {
                                            pendingJumpCandidate = PendingJumpCandidate(
                                                routeKey = routeKey,
                                                gpsPoint = gpsPoint,
                                                estimatedMetres = estimatedChainageMetres,
                                                alignmentName = bestAlignment.alignmentName,
                                                fixTimeMs = effectiveFixTimeMs,
                                                confirmCount = nextCount
                                            )
                                            false
                                        }
                                    } else {
                                        pendingJumpCandidate = PendingJumpCandidate(
                                            routeKey = routeKey,
                                            gpsPoint = gpsPoint,
                                            estimatedMetres = estimatedChainageMetres,
                                            alignmentName = bestAlignment.alignmentName,
                                            fixTimeMs = effectiveFixTimeMs,
                                            confirmCount = 1
                                        )
                                        false
                                    }
                                }
                            }

                            if (confirmedByMultiFix) {
                                isUncertain = false
                                lastReliableChainage = Chainage(estimatedChainageMetres)
                                automaticJumpRecoveries.incrementAndGet()
                            } else {
                                isUncertain = true
                                uncertainReason = if (isPhysicallyImpossibleJump) "IMPLAUSIBLE_DROP_JUMP" else "UNCONFIRMED_JUMP_SPIKE"
                                lastReliableChainage = prevBaselineChainage
                            }
                        }
                    }
                    else -> {
                        synchronized(jumpValidationLock) { pendingJumpCandidate = null }
                    }
                }
            } else {
                synchronized(jumpValidationLock) { pendingJumpCandidate = null }
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

            // 6. Parallel Centerline Offset Detection & Carriageway Side (LHS / RHS) Detection
            val parallelOffset = computeParallelCenterlineOffset(
                gpsPoint = gpsPoint,
                projectedPoint = finalProjectionPoint,
                perpendicularDistanceMetres = effectiveOffset,
                cachedAlignment = bestAlignment,
                segmentIndex = bestSegIdx,
                segmentProjectionT = bestProj.t
            )

            var detectedSide = bestAlignment.carriagewayType
            if (detectedSide == "MAIN" || detectedSide == "UNKNOWN") {
                detectedSide = when {
                    abs(parallelOffset.signedLateralOffsetMetres) < 1.0 -> previousResult?.detectedSide ?: "RHS"
                    parallelOffset.signedLateralOffsetMetres > 0.0 -> "LHS"
                    else -> "RHS"
                }
            }

            val finalChainage = if (isUncertain && lastReliableChainage != null) {
                lastReliableChainage
            } else {
                Chainage(estimatedChainageMetres)
            }
            val finalPreciseMm = if (isUncertain && lastReliableChainage != null) {
                lastReliableChainage.totalMillimetres
            } else {
                estimatedChainageMillimetres
            }
            val finalDistanceAlongM = if (isUncertain && lastReliableChainage != null &&
                previousResult != null && previousResult.matchedSegmentIndex >= 0
            ) {
                previousResult.distanceAlongAlignmentMetres
            } else {
                bestDistanceAlongM
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
                matchedAlignmentId = bestAlignment.alignmentId,
                matchedAlignmentName = bestAlignment.alignmentName,
                detectedSide = detectedSide,
                timestamp = effectiveFixTimeMs,
                matchedSegmentIndex = bestSegIdx,
                matchedSegmentLengthM = segLength,
                segmentProjectionT = bestProj.t,
                alignmentDirection = bestAlignment.direction,
                isUncertain = isUncertain,
                lastReliableChainage = lastReliableChainage,
                distanceAlongAlignmentMetres = finalDistanceAlongM,
                nearestReferenceMarkerLabel = nearestMarkerLabel,
                bracketingMarkersInfo = bracketingInfo,
                sourceKmzReferenceStatus = referenceStatus,
                signedLateralOffsetMetres = parallelOffset.signedLateralOffsetMetres,
                centerlineOffsetZone = parallelOffset.zone,
                localTangentBearingDegrees = parallelOffset.localTangentBearingDegrees,
                parallelCenterlineOffset = parallelOffset,
                preciseChainageMillimetres = finalPreciseMm
            )
        } finally {
            lastMatchLatencyMicros.set((System.nanoTime() - startNs) / 1_000L)
        }
    }

    /**
     * Convenience overload that projects [gpsPoint] onto [alignment] and returns the perpendicular
     * [ParallelCenterlineOffset] relative to the alignment's local forward direction.
     */
    fun computeParallelCenterlineOffset(
        gpsPoint: GeoPoint,
        alignment: RouteAlignment,
        markers: List<RouteMarker> = emptyList()
    ): ParallelCenterlineOffset {
        if (alignment.points.size < 2) {
            return ParallelCenterlineOffset.outside(gpsPoint)
        }
        val cached = getOrCreateCachedGeometry(alignment, markers)
        var bestSegIdx = 0
        var bestProj = GeoUtils.projectPointToSegment(gpsPoint, cached.points[0], cached.points[1])
        for (i in 1 until cached.segmentCount) {
            val proj = GeoUtils.projectPointToSegment(gpsPoint, cached.points[i], cached.points[i + 1])
            if (proj.perpendicularDistance < bestProj.perpendicularDistance) {
                bestProj = proj
                bestSegIdx = i
            }
        }
        return computeParallelCenterlineOffset(
            gpsPoint = gpsPoint,
            projectedPoint = bestProj.projectedPoint,
            perpendicularDistanceMetres = bestProj.perpendicularDistance,
            cachedAlignment = cached,
            segmentIndex = bestSegIdx,
            segmentProjectionT = bestProj.t
        )
    }

    /**
     * Computes perpendicular GPS offset relative to the selected KMZ/KML road centerline:
     * 1. Projects GPS onto the selected alignment at ([segmentIndex], [segmentProjectionT]).
     * 2. Determines smooth local tangent direction at the projected position (blending adjacent
     *    segment unit vectors near vertices so curves never flip or jump discontinuously).
     * 3. Orients the local tangent along the alignment's local forward direction.
     * 4. Computes signed perpendicular distance via 2D cross product between local tangent and
     *    the projected-to-GPS vector (never using raw lat/lng differences).
     * 5. Classifies LEFT (> +0.5 m), RIGHT (< -0.5 m), ON_LINE (<= 0.5 m), or OUTSIDE (> 100 m).
     */
    fun computeParallelCenterlineOffset(
        gpsPoint: GeoPoint,
        projectedPoint: GeoPoint,
        perpendicularDistanceMetres: Double,
        cachedAlignment: CachedAlignmentGeometry,
        segmentIndex: Int,
        segmentProjectionT: Double
    ): ParallelCenterlineOffset {
        val pts = cachedAlignment.points
        if (pts.size < 2 || segmentIndex !in 0 until cachedAlignment.segmentCount) {
            return ParallelCenterlineOffset.outside(gpsPoint, perpendicularDistanceMetres)
        }

        fun segmentUnitVector(idx: Int): Pair<Double, Double> {
            val a = pts[idx]
            val b = pts[idx + 1]
            val midLatRad = Math.toRadians((a.latitude + b.latitude) * 0.5)
            val dx = Math.toRadians(b.longitude - a.longitude) * GeoUtils.EARTH_RADIUS_METRES * cos(midLatRad)
            val dy = Math.toRadians(b.latitude - a.latitude) * GeoUtils.EARTH_RADIUS_METRES
            val len = sqrt(dx * dx + dy * dy)
            return if (len > 1e-5) (dx / len) to (dy / len) else 0.0 to 1.0
        }

        val (currUx, currUy) = segmentUnitVector(segmentIndex)
        var blendUx = currUx
        var blendUy = currUy

        // Smooth angle-bisector tangent blending near curve vertices (t in [0, 0.20] or [0.80, 1.0])
        val t = segmentProjectionT.coerceIn(0.0, 1.0)
        if (t < 0.20 && segmentIndex > 0) {
            val (prevUx, prevUy) = segmentUnitVector(segmentIndex - 1)
            val wPrev = ((0.20 - t) / 0.40).coerceIn(0.0, 0.5)
            val wCurr = 1.0 - wPrev
            blendUx = wCurr * currUx + wPrev * prevUx
            blendUy = wCurr * currUy + wPrev * prevUy
        } else if (t > 0.80 && segmentIndex + 1 < cachedAlignment.segmentCount) {
            val (nextUx, nextUy) = segmentUnitVector(segmentIndex + 1)
            val wNext = ((t - 0.80) / 0.40).coerceIn(0.0, 0.5)
            val wCurr = 1.0 - wNext
            blendUx = wCurr * currUx + wNext * nextUx
            blendUy = wCurr * currUy + wNext * nextUy
        }

        val norm = sqrt(blendUx * blendUx + blendUy * blendUy)
        var tangentEast = if (norm > 1e-5) blendUx / norm else currUx
        var tangentNorth = if (norm > 1e-5) blendUy / norm else currUy

        // Orient tangent in the alignment's local forward chainage direction
        if (cachedAlignment.direction == "DECREASING") {
            tangentEast = -tangentEast
            tangentNorth = -tangentNorth
        }

        val localTangentBearingDeg = ((Math.toDegrees(atan2(tangentEast, tangentNorth)) % 360.0) + 360.0) % 360.0

        // Vector from projected centerline point to actual GPS coordinate in local East/North metres
        val midLatRad = Math.toRadians((projectedPoint.latitude + gpsPoint.latitude) * 0.5)
        val deltaEast = Math.toRadians(gpsPoint.longitude - projectedPoint.longitude) *
            GeoUtils.EARTH_RADIUS_METRES * cos(midLatRad)
        val deltaNorth = Math.toRadians(gpsPoint.latitude - projectedPoint.latitude) *
            GeoUtils.EARTH_RADIUS_METRES

        // 2D cross product: tangent x delta -> > 0 is LEFT of local tangent, < 0 is RIGHT of local tangent
        val cross = tangentEast * deltaNorth - tangentNorth * deltaEast
        val cleanPerpDist = if (perpendicularDistanceMetres.isFinite() && perpendicularDistanceMetres >= 0.0) {
            perpendicularDistanceMetres
        } else {
            sqrt(deltaEast * deltaEast + deltaNorth * deltaNorth)
        }

        if (cleanPerpDist > MAXIMUM_OFF_ROUTE_DISTANCE_METRES) {
            return ParallelCenterlineOffset(
                zone = CenterlineOffsetZone.OUTSIDE,
                perpendicularDistanceMetres = cleanPerpDist,
                signedLateralOffsetMetres = if (cross >= 0.0) cleanPerpDist else -cleanPerpDist,
                localTangentBearingDegrees = localTangentBearingDeg,
                projectedPoint = projectedPoint,
                gpsPoint = gpsPoint,
                matchedAlignmentId = cachedAlignment.alignmentId,
                matchedAlignmentName = cachedAlignment.alignmentName
            )
        }

        if (cleanPerpDist <= ParallelCenterlineOffset.ON_CENTERLINE_THRESHOLD_METRES) {
            return ParallelCenterlineOffset(
                zone = CenterlineOffsetZone.ON_LINE,
                perpendicularDistanceMetres = cleanPerpDist,
                signedLateralOffsetMetres = if (abs(cross) < 1e-4) 0.0 else if (cross > 0.0) cleanPerpDist else -cleanPerpDist,
                localTangentBearingDegrees = localTangentBearingDeg,
                projectedPoint = projectedPoint,
                gpsPoint = gpsPoint,
                matchedAlignmentId = cachedAlignment.alignmentId,
                matchedAlignmentName = cachedAlignment.alignmentName
            )
        }

        val isLeft = if (abs(cross) > 1e-5) {
            cross > 0.0
        } else {
            val p1 = pts[segmentIndex]
            val p2 = pts[segmentIndex + 1]
            val segSign = calculateSignedLateralOffsetMetres(gpsPoint, p1, p2)
            if (cachedAlignment.direction == "DECREASING") segSign < 0.0 else segSign >= 0.0
        }

        val zone = if (isLeft) CenterlineOffsetZone.LEFT else CenterlineOffsetZone.RIGHT
        val signedOffset = if (isLeft) cleanPerpDist else -cleanPerpDist

        return ParallelCenterlineOffset(
            zone = zone,
            perpendicularDistanceMetres = cleanPerpDist,
            signedLateralOffsetMetres = signedOffset,
            localTangentBearingDegrees = localTangentBearingDeg,
            projectedPoint = projectedPoint,
            gpsPoint = gpsPoint,
            matchedAlignmentId = cachedAlignment.alignmentId,
            matchedAlignmentName = cachedAlignment.alignmentName
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
        resetJumpValidationState()
    }
}
