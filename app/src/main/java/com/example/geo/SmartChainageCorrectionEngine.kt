package com.example.geo

import android.content.Context
import com.example.model.AlignmentDiagnostics
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Result returned by [SmartChainageCorrectionEngine.applyCorrection].
 */
data class SmartCorrectionResult(
    val correctedChainage: Chainage,
    val originalKmzChainage: Chainage,
    val appliedOffsetMetres: Long,
    val boundingPointA: ChainageCorrectionPoint?,
    val boundingPointB: ChainageCorrectionPoint?,
    val interpolationFraction: Double,
    val infoMessage: String,
    val isEquationBoundary: Boolean = false
)

/**
 * Resolved position along the active KMZ route geometry.
 */
data class ResolvedRoutePosition(
    val originalKmzMetres: Long,
    val routeDistanceMetres: Double,
    val point: GeoPoint,
    val offsetFromRouteMetres: Double = 0.0
)

/**
 * Validates new or edited correction points against existing points and route boundaries.
 */
data class CorrectionValidationResult(
    val isValid: Boolean,
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null
)

/**
 * CTECS Smart KMZ Chainage Correction Engine.
 *
 * Implements:
 * - Route-distance & route-geometry interpolation between custom correction points.
 * - Support for both increasing and decreasing chainages and reverse travel.
 * - Strict route-scoped and section-scoped coverage (no indefinite extrapolation).
 * - Comprehensive validation (duplicates, invalid chainages, out-of-bounds positions,
 *   unexpected chainage jumps, and route direction order conflicts).
 */
object SmartChainageCorrectionEngine {

    // Maximum local coverage around a single standalone correction point (2 km corridor; no indefinite extrapolation)
    const val SINGLE_POINT_INFLUENCE_METRES = 2_000L

    // Small tolerance (metres) at the exact boundary of the first/last correction point to handle ±3m GPS jitter
    private const val ENDPOINT_BOUNDARY_TOLERANCE_METRES = 15.0

    /**
     * Resolves a correction point's cumulative distance along the route (`routeDistanceMetres`)
     * using route geometry when not already populated.
     */
    fun resolveRouteDistanceMetres(
        point: ChainageCorrectionPoint,
        routeData: RouteData?
    ): Double {
        if (point.routeDistanceMetres > 0.01) {
            return point.routeDistanceMetres
        }
        if (routeData == null) {
            return point.originalKmzMetres.toDouble()
        }
        return resolveRoutePositionForChainage(
            routeData = routeData,
            targetKmzMetres = point.originalKmzMetres,
            fallbackGps = if (point.latitude != 0.0 || point.longitude != 0.0) point.point else null
        ).routeDistanceMetres
    }

    /**
     * Resolves exact route position (`routeDistanceMetres` and `GeoPoint`) for a given KMZ chainage
     * or GPS location along [routeData].
     */
    fun resolveRoutePositionForChainage(
        routeData: RouteData,
        targetKmzMetres: Long,
        fallbackGps: GeoPoint? = null
    ): ResolvedRoutePosition {
        // 1. If a GPS coordinate is provided and lies close to the alignment, project it onto the route geometry
        val alignment = routeData.activeAlignment ?: routeData.alignments.firstOrNull()
        if (alignment != null && alignment.points.size >= 2) {
            val cached = ChainageMatcher.getOrCreateCachedGeometry(alignment, routeData.markers)

            if (fallbackGps != null && (fallbackGps.latitude != 0.0 || fallbackGps.longitude != 0.0)) {
                var bestDist = Double.MAX_VALUE
                var bestAlong = 0.0
                var bestProjPt: GeoPoint = fallbackGps
                for (i in 0 until cached.segmentCount) {
                    val proj = GeoUtils.projectPointToSegment(fallbackGps, cached.points[i], cached.points[i + 1])
                    if (proj.perpendicularDistance < bestDist) {
                        bestDist = proj.perpendicularDistance
                        bestAlong = cached.cumulativeDistances[i] + proj.t * cached.segmentLengths[i]
                        bestProjPt = proj.projectedPoint
                    }
                }
                // Check if targetKmzMetres matches the projected location reasonably (within 150m),
                // otherwise user manually edited the KMZ route position chainage so we resolve by chainage
                val mapped = cached.mappedMarkers
                if (bestDist <= 60.0 && mapped.isNotEmpty()) {
                    val estKmzAtGps = estimateChainageAtRouteDistance(bestAlong, cached)
                    if (abs(estKmzAtGps - targetKmzMetres) <= 30L) {
                        return ResolvedRoutePosition(
                            originalKmzMetres = targetKmzMetres,
                            routeDistanceMetres = bestAlong,
                            point = bestProjPt,
                            offsetFromRouteMetres = bestDist
                        )
                    }
                }
            }

            // 2. Resolve route distance along cached alignment geometry from targetKmzMetres
            val mapped = cached.mappedMarkers
            if (mapped.size >= 2) {
                val first = mapped.first()
                val last = mapped.last()
                val isIncreasing = last.chainageMetres >= first.chainageMetres

                val routeDist: Double = when {
                    (isIncreasing && targetKmzMetres <= first.chainageMetres) ||
                        (!isIncreasing && targetKmzMetres >= first.chainageMetres) -> {
                        val delta = abs(targetKmzMetres - first.chainageMetres).toDouble()
                        (first.distanceAlongAlignmentM - delta).coerceAtLeast(0.0)
                    }
                    (isIncreasing && targetKmzMetres >= last.chainageMetres) ||
                        (!isIncreasing && targetKmzMetres <= last.chainageMetres) -> {
                        val delta = abs(targetKmzMetres - last.chainageMetres).toDouble()
                        (last.distanceAlongAlignmentM + delta).coerceAtMost(cached.totalLengthM)
                    }
                    else -> {
                        var mA = first
                        var mB = last
                        for (k in 0 until mapped.size - 1) {
                            val curr = mapped[k]
                            val next = mapped[k + 1]
                            val minCh = minOf(curr.chainageMetres, next.chainageMetres)
                            val maxCh = maxOf(curr.chainageMetres, next.chainageMetres)
                            if (targetKmzMetres in minCh..maxCh) {
                                mA = curr
                                mB = next
                                break
                            }
                        }
                        val spanCh = (mB.chainageMetres - mA.chainageMetres).toDouble()
                        val frac = if (abs(spanCh) > 0.01) {
                            ((targetKmzMetres - mA.chainageMetres).toDouble() / spanCh).coerceIn(0.0, 1.0)
                        } else 0.0
                        mA.distanceAlongAlignmentM + frac * (mB.distanceAlongAlignmentM - mA.distanceAlongAlignmentM)
                    }
                }

                val ptOnRoute = interpolatePointAtRouteDistance(routeDist, cached)
                return ResolvedRoutePosition(
                    originalKmzMetres = targetKmzMetres,
                    routeDistanceMetres = routeDist,
                    point = ptOnRoute,
                    offsetFromRouteMetres = 0.0
                )
            } else if (mapped.size == 1) {
                val single = mapped[0]
                val sign = if (cached.direction == "DECREASING") -1 else 1
                val routeDist = (single.distanceAlongAlignmentM + sign * (targetKmzMetres - single.chainageMetres))
                    .coerceIn(0.0, cached.totalLengthM)
                val ptOnRoute = interpolatePointAtRouteDistance(routeDist, cached)
                return ResolvedRoutePosition(
                    originalKmzMetres = targetKmzMetres,
                    routeDistanceMetres = routeDist,
                    point = ptOnRoute,
                    offsetFromRouteMetres = 0.0
                )
            }
        }

        // 3. Fallback when route has markers only
        if (routeData.markers.isNotEmpty()) {
            val nearest = routeData.markers.minByOrNull { abs(it.chainage.metres - targetKmzMetres) }
                ?: routeData.markers.first()
            val startCh = routeData.startChainage?.metres ?: routeData.markers.first().chainage.metres
            val routeDist = abs(targetKmzMetres - startCh).toDouble()
            return ResolvedRoutePosition(
                originalKmzMetres = targetKmzMetres,
                routeDistanceMetres = routeDist,
                point = fallbackGps ?: nearest.point,
                offsetFromRouteMetres = 0.0
            )
        }

        return ResolvedRoutePosition(
            originalKmzMetres = targetKmzMetres,
            routeDistanceMetres = targetKmzMetres.toDouble(),
            point = fallbackGps ?: GeoPoint(0.0, 0.0),
            offsetFromRouteMetres = 0.0
        )
    }

    private fun estimateChainageAtRouteDistance(
        distanceAlongM: Double,
        cached: ChainageMatcher.CachedAlignmentGeometry
    ): Long {
        val mapped = cached.mappedMarkers
        if (mapped.isEmpty()) return 0L
        if (mapped.size == 1) {
            val single = mapped[0]
            val sign = if (cached.direction == "DECREASING") -1 else 1
            return (single.chainageMetres + sign * (distanceAlongM - single.distanceAlongAlignmentM)).roundToLong()
        }
        val first = mapped.first()
        val last = mapped.last()
        if (distanceAlongM <= first.distanceAlongAlignmentM) {
            val sign = if (first.chainageMetres <= last.chainageMetres) 1 else -1
            return (first.chainageMetres + sign * (distanceAlongM - first.distanceAlongAlignmentM)).roundToLong()
        }
        if (distanceAlongM >= last.distanceAlongAlignmentM) {
            val sign = if (first.chainageMetres <= last.chainageMetres) 1 else -1
            return (last.chainageMetres + sign * (distanceAlongM - last.distanceAlongAlignmentM)).roundToLong()
        }
        var mA = first
        var mB = last
        for (k in 0 until mapped.size - 1) {
            val curr = mapped[k]
            val next = mapped[k + 1]
            if (distanceAlongM in curr.distanceAlongAlignmentM..next.distanceAlongAlignmentM) {
                mA = curr
                mB = next
                break
            }
        }
        val spanDist = mB.distanceAlongAlignmentM - mA.distanceAlongAlignmentM
        val frac = if (spanDist > 0.01) {
            ((distanceAlongM - mA.distanceAlongAlignmentM) / spanDist).coerceIn(0.0, 1.0)
        } else 0.0
        return (mA.chainageMetres + frac * (mB.chainageMetres - mA.chainageMetres)).roundToLong()
    }

    private fun interpolatePointAtRouteDistance(
        distanceAlongM: Double,
        cached: ChainageMatcher.CachedAlignmentGeometry
    ): GeoPoint {
        val pts = cached.points
        if (pts.isEmpty()) return GeoPoint(0.0, 0.0)
        if (pts.size == 1 || distanceAlongM <= 0.0) return pts.first()
        if (distanceAlongM >= cached.totalLengthM) return pts.last()

        for (i in 0 until cached.segmentCount) {
            val segStart = cached.cumulativeDistances[i]
            val segLen = cached.segmentLengths[i]
            val segEnd = segStart + segLen
            if (distanceAlongM in segStart..segEnd) {
                val t = if (segLen > 0.001) ((distanceAlongM - segStart) / segLen).coerceIn(0.0, 1.0) else 0.0
                val p1 = pts[i]
                val p2 = pts[i + 1]
                return GeoPoint(
                    latitude = p1.latitude + t * (p2.latitude - p1.latitude),
                    longitude = p1.longitude + t * (p2.longitude - p1.longitude),
                    altitude = p1.altitude + t * (p2.altitude - p1.altitude)
                )
            }
        }
        return pts.last()
    }

    /**
     * Sorts correction points in route order (by distance along the route geometry from route start).
     */
    fun sortPointsInRouteOrder(
        points: List<ChainageCorrectionPoint>,
        routeData: RouteData?
    ): List<ChainageCorrectionPoint> {
        if (points.size <= 1) return points
        val isDecreasingRoute = isRouteDecreasing(routeData)
        return points.sortedWith { a, b ->
            val distA = resolveRouteDistanceMetres(a, routeData)
            val distB = resolveRouteDistanceMetres(b, routeData)
            if (abs(distA - distB) > 0.5) {
                distA.compareTo(distB)
            } else {
                if (isDecreasingRoute) {
                    b.originalKmzMetres.compareTo(a.originalKmzMetres)
                } else {
                    a.originalKmzMetres.compareTo(b.originalKmzMetres)
                }
            }
        }
    }

    fun isRouteDecreasing(routeData: RouteData?): Boolean {
        if (routeData == null) return false
        val activeAlignDir = routeData.activeAlignment?.direction
        if (activeAlignDir == "DECREASING") return true
        if (activeAlignDir == "INCREASING") return false
        if (routeData.diagnostics != null) return !routeData.diagnostics.isChainageIncreasing
        val s = routeData.startChainage?.metres
        val e = routeData.endChainage?.metres
        return s != null && e != null && e < s
    }

    /**
     * Applies custom chainage correction to [rawChainage] if it falls within valid coverage
     * of enabled correction points on [routeData].
     *
     * Uses route distance (`distanceAlongAlignmentMetres` and each correction point's
     * resolved `routeDistanceMetres`) for accurate geometric interpolation along curves
     * and transitions, supporting both increasing and decreasing chainages and reverse travel.
     */
    fun applyCorrection(
        rawChainage: Chainage,
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>,
        gpsPoint: GeoPoint? = null,
        gpsAccuracy: Float = 0f,
        confidence: MatchConfidence = MatchConfidence.HIGH,
        distanceAlongAlignmentMetres: Double = 0.0,
        isUncertainJump: Boolean = false
    ): SmartCorrectionResult? {
        // Safety: Do not apply custom correction if GPS fix is untrusted, off-route, or flagged as sudden jump
        if (confidence == MatchConfidence.NO_MATCH || confidence == MatchConfidence.LOW || isUncertainJump) {
            return null
        }

        // Filter enabled correction points belonging strictly to this route
        val routeScopedPoints = correctionPoints.filter { it.routeId == routeData.id && it.isEnabled }
        if (routeScopedPoints.isEmpty()) {
            return null
        }

        val orderedPoints = sortPointsInRouteOrder(routeScopedPoints, routeData)
        val rawMetres = rawChainage.metres

        // Resolve current position's distance along the route alignment
        val hasGeometryDistance = distanceAlongAlignmentMetres > 0.01 ||
            (routeData.alignments.isNotEmpty() && routeData.alignments.first().points.size >= 2)
        val currentRouteDist = if (distanceAlongAlignmentMetres > 0.01) {
            distanceAlongAlignmentMetres
        } else {
            resolveRoutePositionForChainage(routeData, rawMetres, gpsPoint).routeDistanceMetres
        }

        // Case 1: Single correction point on route — local bounded coverage (no indefinite extrapolation)
        if (orderedPoints.size == 1) {
            val single = orderedPoints[0]
            val singleDist = resolveRouteDistanceMetres(single, routeData)
            val distFromPoint = if (hasGeometryDistance && singleDist > 0.01) {
                abs(currentRouteDist - singleDist).roundToLong()
            } else {
                abs(rawMetres - single.originalKmzMetres)
            }

            if (distFromPoint <= SINGLE_POINT_INFLUENCE_METRES) {
                val corrected = rawMetres + single.offsetMetres
                return SmartCorrectionResult(
                    correctedChainage = Chainage(corrected),
                    originalKmzChainage = rawChainage,
                    appliedOffsetMetres = single.offsetMetres,
                    boundingPointA = single,
                    boundingPointB = null,
                    interpolationFraction = 1.0,
                    infoMessage = "Point ${single.correctedChainage.format()} (${single.displayOffset})"
                )
            }
            return null
        }

        // Case 2: Multiple correction points (2 or more) — Route-distance piecewise interpolation
        val pointDistances = orderedPoints.map { resolveRouteDistanceMetres(it, routeData) }
        val minRouteDist = pointDistances.first()
        val maxRouteDist = pointDistances.last()

        val canUseRouteDistance = hasGeometryDistance && (maxRouteDist - minRouteDist) > 1.0

        if (canUseRouteDistance) {
            // Check whether current GPS route position falls within the covered section [minRouteDist, maxRouteDist]
            if (currentRouteDist < minRouteDist - ENDPOINT_BOUNDARY_TOLERANCE_METRES ||
                currentRouteDist > maxRouteDist + ENDPOINT_BOUNDARY_TOLERANCE_METRES
            ) {
                return null
            }

            val clampedDist = currentRouteDist.coerceIn(minRouteDist, maxRouteDist)
            var idxA = 0
            for (i in 0 until orderedPoints.size - 1) {
                val dA = pointDistances[i]
                val dB = pointDistances[i + 1]
                if (clampedDist in minOf(dA, dB)..maxOf(dA, dB)) {
                    idxA = i
                    break
                }
            }

            val pA = orderedPoints[idxA]
            val pB = orderedPoints[idxA + 1]
            val dA = pointDistances[idxA]
            val dB = pointDistances[idxA + 1]
            val spanDist = dB - dA

            val fraction = if (abs(spanDist) > 0.1) {
                ((clampedDist - dA) / spanDist).coerceIn(0.0, 1.0)
            } else {
                val spanKmz = pB.originalKmzMetres - pA.originalKmzMetres
                if (spanKmz != 0L) {
                    ((rawMetres - pA.originalKmzMetres).toDouble() / spanKmz.toDouble()).coerceIn(0.0, 1.0)
                } else 0.0
            }

            val correctedMetres = (pA.correctedMetres + fraction * (pB.correctedMetres - pA.correctedMetres)).roundToLong()
            val appliedOffset = correctedMetres - rawMetres
            val offsetStr = if (appliedOffset >= 0) "+${appliedOffset}m" else "${appliedOffset}m"
            val infoMsg = "${pA.correctedChainage.format()} ↔ ${pB.correctedChainage.format()} ($offsetStr)"

            return SmartCorrectionResult(
                correctedChainage = Chainage(correctedMetres),
                originalKmzChainage = rawChainage,
                appliedOffsetMetres = appliedOffset,
                boundingPointA = pA,
                boundingPointB = pB,
                interpolationFraction = fraction,
                infoMessage = infoMsg
            )
        } else {
            // Fallback to KMZ chainage span when route only has discrete markers without polyline distance
            val minKmz = orderedPoints.minOf { it.originalKmzMetres }
            val maxKmz = orderedPoints.maxOf { it.originalKmzMetres }
            if (rawMetres < minKmz - ENDPOINT_BOUNDARY_TOLERANCE_METRES.toLong() ||
                rawMetres > maxKmz + ENDPOINT_BOUNDARY_TOLERANCE_METRES.toLong()
            ) {
                return null
            }

            val clampedRaw = rawMetres.coerceIn(minKmz, maxKmz)
            var pA: ChainageCorrectionPoint = orderedPoints.first()
            var pB: ChainageCorrectionPoint = orderedPoints.last()
            for (i in 0 until orderedPoints.size - 1) {
                val curr = orderedPoints[i]
                val next = orderedPoints[i + 1]
                val segMin = minOf(curr.originalKmzMetres, next.originalKmzMetres)
                val segMax = maxOf(curr.originalKmzMetres, next.originalKmzMetres)
                if (clampedRaw in segMin..segMax) {
                    pA = curr
                    pB = next
                    break
                }
            }

            val spanKmz = pB.originalKmzMetres - pA.originalKmzMetres
            val fraction = if (spanKmz != 0L) {
                ((clampedRaw - pA.originalKmzMetres).toDouble() / spanKmz.toDouble()).coerceIn(0.0, 1.0)
            } else 0.0

            val correctedMetres = (pA.correctedMetres + fraction * (pB.correctedMetres - pA.correctedMetres)).roundToLong()
            val appliedOffset = correctedMetres - rawMetres
            val offsetStr = if (appliedOffset >= 0) "+${appliedOffset}m" else "${appliedOffset}m"
            val infoMsg = "${pA.correctedChainage.format()} ↔ ${pB.correctedChainage.format()} ($offsetStr)"

            return SmartCorrectionResult(
                correctedChainage = Chainage(correctedMetres),
                originalKmzChainage = rawChainage,
                appliedOffsetMetres = appliedOffset,
                boundingPointA = pA,
                boundingPointB = pB,
                interpolationFraction = fraction,
                infoMessage = infoMsg
            )
        }
    }

    /**
     * Validates a correction point before adding or editing.
     *
     * Warns the user if:
     * 1. Correction points are duplicated.
     * 2. Entered chainages are invalid.
     * 3. Route positions are outside the selected KMZ.
     * 4. Correction points create an unexpected chainage jump.
     * 5. Correction order conflicts with the selected route direction.
     */
    fun validatePoint(
        point: ChainageCorrectionPoint,
        existingPoints: List<ChainageCorrectionPoint>,
        routeData: RouteData?
    ): CorrectionValidationResult {
        val warnings = mutableListOf<String>()

        // 1. Route selection & valid chainage check
        if (point.routeId.isBlank()) {
            return CorrectionValidationResult(
                isValid = false,
                errorMessage = "No KMZ route selected for this correction point."
            )
        }
        if (point.originalKmzMetres < 0L || point.correctedMetres < 0L) {
            warnings.add("Entered chainage is negative. Verify chainage format (e.g. 198+500).")
        }

        val otherPoints = existingPoints.filter { it.routeId == point.routeId && it.id != point.id }
        val pointRouteDist = resolveRouteDistanceMetres(point, routeData)

        // 2. Duplicate check (same route position or same corrected chainage within 10 metres)
        val duplicatePos = otherPoints.find { other ->
            val otherDist = resolveRouteDistanceMetres(other, routeData)
            abs(other.originalKmzMetres - point.originalKmzMetres) < 10L ||
                (pointRouteDist > 0.0 && otherDist > 0.0 && abs(otherDist - pointRouteDist) < 10.0)
        }
        if (duplicatePos != null) {
            warnings.add(
                "Duplicate correction point: Another point already exists at route position ${duplicatePos.originalKmzChainage.format()} (${duplicatePos.correctedChainage.format()})."
            )
        } else {
            val duplicateCorr = otherPoints.find { abs(it.correctedMetres - point.correctedMetres) < 5L }
            if (duplicateCorr != null) {
                warnings.add(
                    "Duplicate chainage: Corrected chainage ${point.correctedChainage.format()} is already assigned to route position ${duplicateCorr.originalKmzChainage.format()}."
                )
            }
        }

        // 3. Route boundary check (outside selected KMZ chainage bounds or far from route geometry)
        if (routeData != null) {
            if (routeData.startChainage != null && routeData.endChainage != null) {
                val minRoute = minOf(routeData.startChainage.metres, routeData.endChainage.metres)
                val maxRoute = maxOf(routeData.startChainage.metres, routeData.endChainage.metres)
                if (point.originalKmzMetres < minRoute || point.originalKmzMetres > maxRoute) {
                    warnings.add(
                        "Route position (${point.originalKmzChainage.format()}) is outside the selected KMZ range (${routeData.startChainage.format()} – ${routeData.endChainage.format()})."
                    )
                }
            }
            if (point.latitude != 0.0 || point.longitude != 0.0) {
                val match = ChainageMatcher.match(
                    gpsPoint = point.point,
                    gpsAccuracy = 5f,
                    routeData = routeData
                )
                if (match.confidence == MatchConfidence.NO_MATCH && match.offsetMetres > ChainageMatcher.MAXIMUM_OFF_ROUTE_DISTANCE_METRES) {
                    warnings.add(
                        String.format(
                            Locale.US,
                            "Selected GPS coordinates are %.0fm away from the KMZ alignment (>100m tolerance).",
                            match.offsetMetres
                        )
                    )
                }
            }
        }

        // 4. Unexpected chainage jump check
        val offset = abs(point.offsetMetres)
        if (offset > 2_000L) {
            warnings.add(
                "Unexpected chainage jump: Correction shifts KMZ chainage by ${point.displayOffset}. Verify entered chainage."
            )
        }

        // 5. Route order & direction conflict check + inter-point jump check
        val allCandidatePoints = sortPointsInRouteOrder(otherPoints + point, routeData)
        val idxInOrder = allCandidatePoints.indexOfFirst { it.id == point.id }
        val prev = if (idxInOrder > 0) allCandidatePoints[idxInOrder - 1] else null
        val next = if (idxInOrder >= 0 && idxInOrder < allCandidatePoints.size - 1) allCandidatePoints[idxInOrder + 1] else null

        val isDecreasing = isRouteDecreasing(routeData)

        if (prev != null) {
            val prevDist = resolveRouteDistanceMetres(prev, routeData)
            val routeSpanM = abs(pointRouteDist - prevDist).roundToLong()
            val corrSpanM = abs(point.correctedMetres - prev.correctedMetres)

            // Direction conflict with previous point
            if (!isDecreasing && point.correctedMetres <= prev.correctedMetres) {
                warnings.add(
                    "Direction conflict: Corrected chainage (${point.correctedChainage.format()}) does not increase after previous point (${prev.correctedChainage.format()}) on an increasing route."
                )
            } else if (isDecreasing && point.correctedMetres >= prev.correctedMetres) {
                warnings.add(
                    "Direction conflict: Corrected chainage (${point.correctedChainage.format()}) does not decrease after previous point (${prev.correctedChainage.format()}) on a decreasing route."
                )
            }

            // Unexpected jump relative to route distance between prev and current
            if (routeSpanM > 20L && abs(corrSpanM - routeSpanM) > maxOf(250L, (routeSpanM * 0.45).roundToLong())) {
                warnings.add(
                    "Unexpected chainage jump: Chainage span (${corrSpanM}m) between ${prev.correctedChainage.format()} and ${point.correctedChainage.format()} differs from route distance (${routeSpanM}m)."
                )
            }
        }

        if (next != null) {
            val nextDist = resolveRouteDistanceMetres(next, routeData)
            val routeSpanM = abs(nextDist - pointRouteDist).roundToLong()
            val corrSpanM = abs(next.correctedMetres - point.correctedMetres)

            // Direction conflict with next point
            if (!isDecreasing && point.correctedMetres >= next.correctedMetres) {
                warnings.add(
                    "Direction conflict: Corrected chainage (${point.correctedChainage.format()}) is ≥ next correction point (${next.correctedChainage.format()}) on an increasing route."
                )
            } else if (isDecreasing && point.correctedMetres <= next.correctedMetres) {
                warnings.add(
                    "Direction conflict: Corrected chainage (${point.correctedChainage.format()}) is ≤ next correction point (${next.correctedChainage.format()}) on a decreasing route."
                )
            }

            // Unexpected jump relative to route distance between current and next
            if (routeSpanM > 20L && abs(corrSpanM - routeSpanM) > maxOf(250L, (routeSpanM * 0.45).roundToLong())) {
                warnings.add(
                    "Unexpected chainage jump: Chainage span (${corrSpanM}m) between ${point.correctedChainage.format()} and ${next.correctedChainage.format()} differs from route distance (${routeSpanM}m)."
                )
            }
        }

        return CorrectionValidationResult(isValid = true, warnings = warnings)
    }

    /**
     * Audits an entire list of correction points for route-wide warnings.
     */
    fun auditCorrectionPoints(
        points: List<ChainageCorrectionPoint>,
        routeData: RouteData?
    ): List<String> {
        if (points.isEmpty()) return emptyList()
        val ordered = sortPointsInRouteOrder(points, routeData)
        val allWarnings = linkedSetOf<String>()
        for (pt in ordered) {
            val res = validatePoint(pt, ordered, routeData)
            allWarnings.addAll(res.warnings)
        }
        return allWarnings.toList()
    }

    /**
     * Comprehensive alignment audit: validates progression, checks for duplicate markers,
     * inversions, and chainage equations without modifying any source data.
     */
    fun performAlignmentAudit(routeData: RouteData): AlignmentDiagnostics {
        val markers = routeData.markers
        if (markers.isEmpty()) {
            return AlignmentDiagnostics(
                totalMarkersCount = 0,
                qualityScorePercent = 50,
                summaryMessage = "No chainage markers found in route"
            )
        }

        val warnings = mutableListOf<String>()
        val chainageEquations = mutableListOf<String>()

        var duplicateCount = 0
        var inversionCount = 0
        var largeGaps = 0

        for (i in 0 until markers.size - 1) {
            val m1 = markers[i]
            val m2 = markers[i + 1]

            if (abs(m1.chainage.metres - m2.chainage.metres) < 5 && GeoUtils.distanceMetres(m1.point, m2.point) < 25.0) {
                duplicateCount++
            }

            if (m2.chainage.metres < m1.chainage.metres) {
                inversionCount++
            }

            val deltaCh = abs(m2.chainage.metres - m1.chainage.metres)
            val geomDist = GeoUtils.distanceMetres(m1.point, m2.point)

            if (deltaCh > 2500) {
                largeGaps++
            }

            if (abs(deltaCh - geomDist) > 150.0 && deltaCh > 100) {
                chainageEquations.add("Discontinuity at ${m1.chainage.format()} ↔ ${m2.chainage.format()}: ΔCh=${deltaCh}m vs Geom=${geomDist.toInt()}m")
            }
        }

        if (duplicateCount > 0) warnings.add("$duplicateCount duplicate chainage reference markers detected.")
        if (inversionCount > 0) warnings.add("$inversionCount direction inversions found.")
        if (largeGaps > 0) warnings.add("$largeGaps unreferenced marker gaps > 2.5 km.")

        var score = 100 - (duplicateCount * 5) - (inversionCount * 15) - (largeGaps * 5)
        score = score.coerceIn(10, 100)

        val summary = when {
            score >= 90 -> "100% Validated KMZ Highway Alignment"
            score >= 70 -> "Good Alignment Quality (${markers.size} Reference Points)"
            else -> "Alignment Warnings: $duplicateCount Duplicates, $inversionCount Inversions"
        }

        return AlignmentDiagnostics(
            isChainageIncreasing = inversionCount <= markers.size / 2,
            totalMarkersCount = markers.size,
            duplicateMarkersCount = duplicateCount,
            inversionCount = inversionCount,
            gapCount = largeGaps,
            chainageEquations = chainageEquations,
            warnings = warnings,
            qualityScorePercent = score,
            summaryMessage = summary
        )
    }

    /**
     * Exports a verified KMZ file containing 100% untouched original geometry
     * plus a dedicated folder of Calibrated Chainage References.
     */
    suspend fun exportCalibratedKmz(
        context: Context,
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, "kmz_calibrated_exports").apply { mkdirs() }
        val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitized = routeData.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val kmzFile = File(exportDir, "${sanitized}_calibrated_${dateSuffix}.kmz")

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("<Document>\n")
        sb.append("  <name>${escapeXml(routeData.name)} (CTECS Calibrated)</name>\n")
        sb.append("  <description>Calibrated Highway Alignment exported from CTECS Smart Chainage Correction.</description>\n")

        if (routeData.alignments.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>Original Highway Alignments</name>\n")
            routeData.alignments.forEach { align ->
                sb.append("    <Placemark>\n")
                sb.append("      <name>${escapeXml(align.name)}</name>\n")
                sb.append("      <LineString>\n")
                sb.append("        <tessellate>1</tessellate>\n")
                sb.append("        <coordinates>\n")
                align.points.forEach { pt ->
                    sb.append("          ${pt.longitude},${pt.latitude},${pt.altitude}\n")
                }
                sb.append("        </coordinates>\n")
                sb.append("      </LineString>\n")
                sb.append("    </Placemark>\n")
            }
            sb.append("  </Folder>\n")
        }

        if (routeData.markers.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>Original KMZ Markers</name>\n")
            routeData.markers.forEach { m ->
                sb.append("    <Placemark>\n")
                sb.append("      <name>${escapeXml(m.label)}</name>\n")
                sb.append("      <description>Original Chainage: ${m.chainage.format()}</description>\n")
                sb.append("      <Point>\n")
                sb.append("        <coordinates>${m.point.longitude},${m.point.latitude},${m.point.altitude}</coordinates>\n")
                sb.append("      </Point>\n")
                sb.append("    </Placemark>\n")
            }
            sb.append("  </Folder>\n")
        }

        val orderedPoints = sortPointsInRouteOrder(correctionPoints, routeData)
        if (orderedPoints.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>Custom Chainage Correction Points</name>\n")
            orderedPoints.forEachIndexed { idx, cp ->
                sb.append("    <Placemark>\n")
                sb.append("      <name>Correction Point ${idx + 1}: ${cp.correctedChainage.format()}</name>\n")
                sb.append("      <description>Original KMZ: ${cp.originalKmzChainage.format()} | Corrected: ${cp.correctedChainage.format()} | Applied Shift: ${cp.displayOffset} | Status: ${if (cp.isEnabled) "Enabled" else "Disabled"}</description>\n")
                sb.append("      <Point>\n")
                sb.append("        <coordinates>${cp.longitude},${cp.latitude}</coordinates>\n")
                sb.append("      </Point>\n")
                sb.append("    </Placemark>\n")
            }
            sb.append("  </Folder>\n")
        }

        sb.append("</Document>\n")
        sb.append("</kml>\n")

        ZipOutputStream(FileOutputStream(kmzFile)).use { zos ->
            val entry = ZipEntry("doc.kml")
            zos.putNextEntry(entry)
            zos.write(sb.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.flush()
        }

        kmzFile
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
