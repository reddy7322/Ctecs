package com.example.geo

import com.example.model.AlignmentDiagnostics
import com.example.model.CalibrationValidationStatus
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.GeneratedChainageMarker
import com.example.model.GeoPoint
import com.example.model.KmzReplacementPreview
import com.example.model.MatchConfidence
import com.example.model.PermanentCalibrationProfile
import com.example.model.ReferencePointSource
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.parser.KmzParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * CTECS Unified Permanent Chainage Calibration Engine.
 *
 * Combines Four Calibration Methods into One Intelligent Engine:
 * 1. Permanent GPS + Chainage Reference Points
 * 2. KMZ/KML Alignment-Based Calibration
 * 3. Multiple Fixed Control Points with Piecewise Alignment Interpolation & Conflict Detection
 * 4. Permanent Calibration Profiles with Automatic Geometry Fingerprint Reuse
 *
 * Enforces the 5-Tier Calibration Priority Hierarchy:
 * 1. Verified permanent control points (VERIFIED)
 * 2. Validated KMZ/KML alignment geometry
 * 3. Piecewise alignment-distance interpolation between verified control points
 * 4. Automatically calculated 2 km calibration markers
 * 5. GPS-derived position subject to accuracy checks
 */
object SmartChainageCorrectionEngine {

    private const val MAX_INTERPOLATION_SCALE_DEVIATION = 0.35 // 35% tolerance before flagging chainage equation/warning
    private const val CONFLICT_SCALE_THRESHOLD = 0.65 // >65% discrepancy without chainage equation flags conflict
    const val DEFAULT_CALIBRATION_MARKER_INTERVAL_METRES = 2000L // 2 km (2,000 m)

    // Lightweight internal performance telemetry
    private val lastCalibrationLatencyMicros = AtomicLong(0L)
    private val totalCalibrationsComputed = AtomicLong(0L)

    fun getLastCalibrationLatencyMicros(): Long = lastCalibrationLatencyMicros.get()
    fun getTotalCalibrationsComputed(): Long = totalCalibrationsComputed.get()

    enum class CalibrationHierarchyTier(val rank: Int, val label: String) {
        TIER_1_VERIFIED_CONTROL_POINT(1, "Verified Permanent Control Point"),
        TIER_2_VALIDATED_ALIGNMENT(2, "Validated KMZ/KML Alignment Geometry"),
        TIER_3_PIECEWISE_INTERPOLATION(3, "Piecewise Control-Point Interpolation"),
        TIER_4_GENERATED_2KM_MARKER(4, "2 km Calibrated Alignment Marker"),
        TIER_5_GPS_FALLBACK(5, "GPS Position (Accuracy Checked)")
    }

    data class SmartCorrectionResult(
        val correctedChainage: Chainage,
        val rawKmzChainage: Chainage,
        val appliedOffsetMetres: Long,
        val mode: CorrectionMode,
        val infoMessage: String,
        val validationStatus: String = CalibrationValidationStatus.VERIFIED,
        val hierarchyTier: CalibrationHierarchyTier = CalibrationHierarchyTier.TIER_3_PIECEWISE_INTERPOLATION,
        val hasConflict: Boolean = false
    ) {
        val originalKmzChainage: Chainage get() = rawKmzChainage
    }

    enum class CorrectionMode {
        SINGLE_POINT_PROPAGATION,
        MULTI_POINT_INTERPOLATION,
        EXTRAPOLATED_PRE_START,
        EXTRAPOLATED_POST_END,
        CONFLICT_DETECTED
    }

    data class ResolvedPointPosition(
        val point: GeoPoint,
        val routeDistanceMetres: Double,
        val offsetFromAlignmentMetres: Double = 0.0
    )

    data class ReferenceConflictReport(
        val hasConflict: Boolean,
        val validationStatus: String,
        val conflicts: List<String>,
        val warnings: List<String>
    )

    data class ProfileCompatibilityResult(
        val isCompatible: Boolean,
        val isExactGeometryMatch: Boolean,
        val validationStatus: String,
        val statusMessage: String,
        val revalidatedPoints: List<ChainageCorrectionPoint>
    )

    data class ChainagePerformanceDiagnostics(
        val lastMatchLatencyMicros: Long,
        val lastCalibrationLatencyMicros: Long,
        val cacheHits: Long,
        val cacheMisses: Long,
        val automaticJumpRecoveries: Long,
        val totalCalibrationsComputed: Long
    )

    fun getPerformanceDiagnostics(): ChainagePerformanceDiagnostics {
        return ChainagePerformanceDiagnostics(
            lastMatchLatencyMicros = ChainageMatcher.getLastMatchLatencyMicros(),
            lastCalibrationLatencyMicros = lastCalibrationLatencyMicros.get(),
            cacheHits = ChainageMatcher.getCacheHits(),
            cacheMisses = ChainageMatcher.getCacheMisses(),
            automaticJumpRecoveries = ChainageMatcher.getAutomaticJumpRecoveries(),
            totalCalibrationsComputed = totalCalibrationsComputed.get()
        )
    }

    /**
     * Computes a deterministic geometry fingerprint for a RouteData alignment.
     * Used to automatically recognize previously calibrated alignments and detect modified geometry.
     */
    fun computeAlignmentFingerprint(routeData: RouteData): String {
        val activeAlign = routeData.activeAlignment ?: routeData.alignments.firstOrNull()
        val pts = activeAlign?.points.orEmpty()
        val totalLenRounded = (activeAlign?.totalLengthMetres ?: 0.0).roundToLong()
        val firstPt = pts.firstOrNull()
        val lastPt = pts.lastOrNull()
        val midPt = if (pts.isNotEmpty()) pts[pts.size / 2] else null

        val rawSig = buildString {
            append("v1|")
            append("pts=").append(pts.size).append("|")
            append("len=").append(totalLenRounded).append("|")
            append("dir=").append(activeAlign?.direction ?: "INCREASING").append("|")
            if (firstPt != null) {
                append(String.format(Locale.US, "p0=%.5f,%.5f|", firstPt.latitude, firstPt.longitude))
            }
            if (midPt != null) {
                append(String.format(Locale.US, "pm=%.5f,%.5f|", midPt.latitude, midPt.longitude))
            }
            if (lastPt != null) {
                append(String.format(Locale.US, "pN=%.5f,%.5f|", lastPt.latitude, lastPt.longitude))
            }
            append(
                String.format(
                    Locale.US,
                    "b=%.4f,%.4f,%.4f,%.4f",
                    routeData.bounds.minLat,
                    routeData.bounds.maxLat,
                    routeData.bounds.minLng,
                    routeData.bounds.maxLng
                )
            )
        }

        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(rawSig.toByteArray(Charsets.UTF_8))
            digest.take(12).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            rawSig.hashCode().toUInt().toString(16)
        }
    }

    /**
     * Evaluates whether a saved PermanentCalibrationProfile matches the given RouteData.
     * - If alignmentFingerprint matches: returns VERIFIED and reuses the profile's verified control points.
     * - If originalFileName or routeId matches for the same project, but geometry changed:
     *   re-projects control points and marks status as NEEDS_REVIEW.
     * - Otherwise: marks as incompatible (different road).
     */
    fun evaluateProfileCompatibility(
        profile: PermanentCalibrationProfile,
        routeData: RouteData
    ): ProfileCompatibilityResult {
        val currentFingerprint = computeAlignmentFingerprint(routeData)
        if (profile.alignmentFingerprint.isNotBlank() && profile.alignmentFingerprint == currentFingerprint) {
            val remapped = profile.controlPoints.map { pt ->
                val resolved = resolveRoutePositionForChainage(routeData, pt.originalKmzMetres, pt.point)
                pt.copy(
                    routeId = routeData.id,
                    routeDistanceMetres = resolved.routeDistanceMetres,
                    verificationStatus = if (pt.verificationStatus == CalibrationValidationStatus.INVALID) {
                        CalibrationValidationStatus.NEEDS_REVIEW
                    } else {
                        CalibrationValidationStatus.VERIFIED
                    },
                    calibrationProfileId = profile.profileId
                )
            }
            return ProfileCompatibilityResult(
                isCompatible = true,
                isExactGeometryMatch = true,
                validationStatus = CalibrationValidationStatus.VERIFIED,
                statusMessage = "Verified Profile Matched (${profile.profileName})",
                revalidatedPoints = remapped
            )
        }

        val sameFileName = profile.originalFileName.isNotBlank() &&
            profile.originalFileName.equals(routeData.fileName, ignoreCase = true)
        val sameRouteId = profile.routeId.isNotBlank() && profile.routeId == routeData.id

        if (sameFileName || sameRouteId) {
            // Same alignment file/route, but geometry was modified -> revalidate each point
            val activeAlign = routeData.activeAlignment ?: routeData.alignments.firstOrNull()
            val cached = if (activeAlign != null && activeAlign.points.size >= 2) {
                ChainageMatcher.getOrCreateCachedGeometry(activeAlign, routeData.markers)
            } else null

            var anyShifted = false
            val reprojected = profile.controlPoints.map { pt ->
                val resolved = resolveRoutePositionForChainage(routeData, pt.originalKmzMetres, pt.point)
                var minPerpDist = 0.0
                if (cached != null && (pt.latitude != 0.0 || pt.longitude != 0.0)) {
                    var best = Double.MAX_VALUE
                    for (i in 0 until cached.segmentCount) {
                        val proj = GeoUtils.projectPointToSegment(pt.point, cached.points[i], cached.points[i + 1])
                        if (proj.perpendicularDistance < best) best = proj.perpendicularDistance
                    }
                    minPerpDist = if (best == Double.MAX_VALUE) 0.0 else best
                }
                val distShift = if (pt.routeDistanceMetres > 0.01) {
                    abs(resolved.routeDistanceMetres - pt.routeDistanceMetres)
                } else 0.0

                val pointNeedsReview = minPerpDist > 15.0 || distShift > 10.0
                if (pointNeedsReview) anyShifted = true

                pt.copy(
                    routeId = routeData.id,
                    routeDistanceMetres = resolved.routeDistanceMetres,
                    verificationStatus = if (pointNeedsReview) {
                        CalibrationValidationStatus.NEEDS_REVIEW
                    } else {
                        pt.verificationStatus
                    },
                    calibrationProfileId = profile.profileId
                )
            }

            return ProfileCompatibilityResult(
                isCompatible = true,
                isExactGeometryMatch = false,
                validationStatus = CalibrationValidationStatus.NEEDS_REVIEW,
                statusMessage = if (anyShifted) {
                    "Alignment geometry modified — control points marked Needs Review"
                } else {
                    "Alignment updated — please review & confirm calibration profile"
                },
                revalidatedPoints = reprojected
            )
        }

        return ProfileCompatibilityResult(
            isCompatible = false,
            isExactGeometryMatch = false,
            validationStatus = CalibrationValidationStatus.INVALID,
            statusMessage = "Profile belongs to a different road/alignment (${profile.originalFileName})",
            revalidatedPoints = emptyList()
        )
    }

    /**
     * Detects duplicate, reversed, or conflicting reference points along the active alignment.
     */
    fun detectReferenceConflicts(
        points: List<ChainageCorrectionPoint>,
        routeData: RouteData
    ): ReferenceConflictReport {
        val enabledPoints = points.filter { it.isEnabled }
        if (enabledPoints.isEmpty()) {
            return ReferenceConflictReport(
                hasConflict = false,
                validationStatus = CalibrationValidationStatus.VERIFIED,
                conflicts = emptyList(),
                warnings = emptyList()
            )
        }

        val sorted = sortPointsInRouteOrder(enabledPoints, routeData)
        val conflicts = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val activeAlign = routeData.activeAlignment ?: routeData.alignments.firstOrNull()
        val expectedIncreasing = if (activeAlign != null) {
            val cached = ChainageMatcher.getOrCreateCachedGeometry(activeAlign, routeData.markers)
            cached.direction != "DECREASING"
        } else {
            val firstM = routeData.markers.firstOrNull()?.chainage?.metres ?: 0L
            val lastM = routeData.markers.lastOrNull()?.chainage?.metres ?: 0L
            lastM >= firstM
        }

        for (i in 0 until sorted.size - 1) {
            val pA = sorted[i]
            val pB = sorted[i + 1]
            val distSpan = abs(pB.routeDistanceMetres - pA.routeDistanceMetres)
            val origDelta = pB.originalKmzMetres - pA.originalKmzMetres
            val corrDelta = pB.correctedMetres - pA.correctedMetres

            // 1. Check duplicate alignment position with different corrected chainages
            if (distSpan < 0.5 && abs( origDelta ) < 1L) {
                if (abs(corrDelta) > 1L) {
                    conflicts.add(
                        "Conflicting references at ${pA.originalKmzChainage.format()}: " +
                            "${pA.correctedChainage.format()} vs ${pB.correctedChainage.format()}"
                    )
                } else {
                    warnings.add("Duplicate control point at ${pA.correctedChainage.format()}")
                }
                continue
            }

            // 2. Check reversed corrected chainage direction relative to alignment progression
            val isSpanIncreasing = corrDelta > 0L
            if (corrDelta == 0L && distSpan > 5.0) {
                conflicts.add(
                    "Identical calibrated chainage ${pA.correctedChainage.format()} at two distinct positions (${distSpan.roundToInt()}m apart)"
                )
            } else if (expectedIncreasing && !isSpanIncreasing) {
                conflicts.add(
                    "Reversed chainage order between ${pA.correctedChainage.format()} and ${pB.correctedChainage.format()} on increasing alignment"
                )
            } else if (!expectedIncreasing && isSpanIncreasing) {
                conflicts.add(
                    "Reversed chainage order between ${pA.correctedChainage.format()} and ${pB.correctedChainage.format()} on decreasing alignment"
                )
            }

            // 3. Check extreme scale distortion vs alignment distance
            val effectiveGroundDist = if (distSpan > 1.0) distSpan else abs(origDelta).toDouble()
            if (effectiveGroundDist > 10.0) {
                val scaleDiffRatio = abs(abs(corrDelta).toDouble() - effectiveGroundDist) / effectiveGroundDist
                if (scaleDiffRatio > CONFLICT_SCALE_THRESHOLD && !pA.description.contains("eq", ignoreCase = true) && !pB.description.contains("eq", ignoreCase = true)) {
                    warnings.add(
                        "Chainage equation / irregular span (${(scaleDiffRatio * 100).roundToInt()}% delta) between " +
                            "${pA.correctedChainage.format()} and ${pB.correctedChainage.format()}"
                    )
                }
            }
        }

        val anyNeedsReview = sorted.any { it.verificationStatus == CalibrationValidationStatus.NEEDS_REVIEW }
        val status = when {
            conflicts.isNotEmpty() -> CalibrationValidationStatus.INVALID
            anyNeedsReview || warnings.isNotEmpty() -> CalibrationValidationStatus.NEEDS_REVIEW
            else -> CalibrationValidationStatus.VERIFIED
        }

        return ReferenceConflictReport(
            hasConflict = conflicts.isNotEmpty(),
            validationStatus = status,
            conflicts = conflicts,
            warnings = warnings
        )
    }

    /**
     * Inspects imported KMZ alignment markers & polylines to produce a complete
     * diagnostic report (direction, duplicates, gaps, chainage equations, quality score).
     */
    fun analyzeRouteDiagnostics(
        markers: List<RouteMarker>,
        alignments: List<RouteAlignment>,
        rawPlacemarkCount: Int = markers.size + alignments.size,
        rawPointCount: Int = markers.size,
        rawLineStringCount: Int = alignments.size,
        multiGeometryCount: Int = 0,
        selectedKmlEntryName: String = "doc.kml"
    ): AlignmentDiagnostics {
        if (markers.isEmpty() && alignments.isEmpty()) {
            return AlignmentDiagnostics(
                totalMarkersCount = 0,
                qualityScorePercent = 0,
                summaryMessage = "Empty Route (No markers or alignments)",
                rawPlacemarkCount = rawPlacemarkCount,
                rawPointCount = rawPointCount,
                rawLineStringCount = rawLineStringCount,
                multiGeometryCount = multiGeometryCount,
                selectedKmlEntryName = selectedKmlEntryName
            )
        }

        if (markers.size < 2) {
            return AlignmentDiagnostics(
                totalMarkersCount = markers.size,
                qualityScorePercent = if (alignments.isNotEmpty()) 90 else 75,
                summaryMessage = "Single Reference Marker (${alignments.size} alignment paths)",
                rawPlacemarkCount = rawPlacemarkCount,
                rawPointCount = rawPointCount,
                rawLineStringCount = rawLineStringCount,
                multiGeometryCount = multiGeometryCount,
                selectedKmlEntryName = selectedKmlEntryName
            )
        }

        val primaryAlignment = alignments.maxByOrNull { it.points.size }
        val orderedMarkers = if (primaryAlignment != null && primaryAlignment.points.size >= 2) {
            val cached = ChainageMatcher.getOrCreateCachedGeometry(primaryAlignment, markers)
            if (cached.mappedMarkers.size >= 2) {
                cached.mappedMarkers.map { it.marker }
            } else {
                markers
            }
        } else {
            markers
        }

        val firstCh = orderedMarkers.first().chainage.metres
        val lastCh = orderedMarkers.last().chainage.metres
        val isIncreasing = if (orderedMarkers.size <= 4 && primaryAlignment != null) {
            !primaryAlignment.direction.equals("DECREASING", ignoreCase = true)
        } else {
            lastCh >= firstCh
        }

        var duplicates = 0
        var inversions = 0
        var gaps = 0
        val equations = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val deltas = mutableListOf<Long>()
        for (i in 0 until orderedMarkers.size - 1) {
            val d = abs(orderedMarkers[i + 1].chainage.metres - orderedMarkers[i].chainage.metres)
            if (d > 0) deltas.add(d)
        }
        val sortedDeltas = deltas.sorted()
        val typicalIntervalMetres = if (sortedDeltas.isNotEmpty()) {
            sortedDeltas[sortedDeltas.size / 2]
        } else 50L

        for (i in 0 until orderedMarkers.size - 1) {
            val curr = orderedMarkers[i]
            val next = orderedMarkers[i + 1]
            val chDiff = next.chainage.metres - curr.chainage.metres
            val absChDiff = abs(chDiff)
            val geoDist = GeoUtils.distanceMetres(curr.point, next.point)

            if (absChDiff == 0L || geoDist < 0.3) {
                duplicates++
                continue
            }

            if ((isIncreasing && chDiff < 0) || (!isIncreasing && chDiff > 0)) {
                inversions++
                if (warnings.size < 5) {
                    warnings.add("Inversion at ${curr.label} -> ${next.label}")
                }
            }

            if (absChDiff > typicalIntervalMetres * 3 && absChDiff > 250L) {
                gaps++
                if (warnings.size < 5) {
                    warnings.add("Gap of ${absChDiff}m between ${curr.label} and ${next.label}")
                }
            }

            if (geoDist > 10.0 && absChDiff > 10L) {
                val discrepancy = abs(geoDist - absChDiff.toDouble())
                val ratio = discrepancy / geoDist
                if (ratio > 0.35 && discrepancy > 15.0) {
                    if (equations.size < 5) {
                        equations.add("${curr.label}..${next.label} (CH Δ=${absChDiff}m vs Geo=${geoDist.roundToInt()}m)")
                    }
                }
            }
        }

        val penalty = (duplicates * 2) + (inversions * 10) + (gaps * 3) + (equations.size * 2)
        val score = (100 - penalty).coerceIn(40, 100)

        val dirLabel = if (isIncreasing) "Increasing" else "Decreasing"
        val summary = buildString {
            append("$dirLabel (${orderedMarkers.first().label} → ${orderedMarkers.last().label})")
            if (typicalIntervalMetres > 0) append(" • ~${typicalIntervalMetres}m step")
            if (duplicates > 0) append(" • $duplicates dup")
            if (gaps > 0) append(" • $gaps gaps")
        }

        return AlignmentDiagnostics(
            isChainageIncreasing = isIncreasing,
            totalMarkersCount = markers.size,
            duplicateMarkersCount = duplicates,
            inversionCount = inversions,
            gapCount = gaps,
            chainageEquations = equations,
            warnings = warnings,
            qualityScorePercent = score,
            summaryMessage = summary,
            rawPlacemarkCount = rawPlacemarkCount,
            rawPointCount = rawPointCount,
            rawLineStringCount = rawLineStringCount,
            multiGeometryCount = multiGeometryCount,
            selectedKmlEntryName = selectedKmlEntryName
        )
    }

    /**
     * Audits a complete [RouteData] object for duplicate markers, chainage inversions, gaps, and equations.
     */
    fun performAlignmentAudit(routeData: RouteData): AlignmentDiagnostics {
        return analyzeRouteDiagnostics(
            markers = routeData.markers,
            alignments = routeData.alignments
        )
    }

    /**
     * Resolves a target KMZ chainage to its exact GeoPoint and cumulative distance along the route.
     */
    fun resolveRoutePositionForChainage(
        routeData: RouteData,
        targetKmzMetres: Long,
        fallbackGps: GeoPoint? = null
    ): ResolvedPointPosition {
        val activeAlign = routeData.activeAlignment ?: routeData.alignments.firstOrNull()

        if (activeAlign != null && activeAlign.points.size >= 2) {
            val cached = ChainageMatcher.getOrCreateCachedGeometry(activeAlign, routeData.markers)

            if (fallbackGps != null && (fallbackGps.latitude != 0.0 || fallbackGps.longitude != 0.0)) {
                var bestDist = Double.MAX_VALUE
                var bestAlong = 0.0
                var bestPt: GeoPoint = fallbackGps
                for (i in 0 until cached.segmentCount) {
                    val proj = GeoUtils.projectPointToSegment(fallbackGps, cached.points[i], cached.points[i + 1])
                    if (proj.perpendicularDistance < bestDist) {
                        bestDist = proj.perpendicularDistance
                        bestAlong = cached.cumulativeDistances[i] + proj.t * cached.segmentLengths[i]
                        bestPt = proj.projectedPoint
                    }
                }
                if (bestDist <= 60.0) {
                    return ResolvedPointPosition(bestPt, bestAlong, bestDist)
                }
            }

            val mapped = cached.mappedMarkers
            if (mapped.size >= 2) {
                val isIncreasing = mapped.last().chainageMetres >= mapped.first().chainageMetres
                val first = mapped.first()
                val last = mapped.last()

                val targetDistAlong: Double = when {
                    (isIncreasing && targetKmzMetres <= first.chainageMetres) ||
                        (!isIncreasing && targetKmzMetres >= first.chainageMetres) -> {
                        val sign = if (isIncreasing) 1.0 else -1.0
                        val deltaCh = (targetKmzMetres - first.chainageMetres) * sign
                        (first.distanceAlongAlignmentM + deltaCh).coerceIn(0.0, cached.totalLengthM)
                    }
                    (isIncreasing && targetKmzMetres >= last.chainageMetres) ||
                        (!isIncreasing && targetKmzMetres <= last.chainageMetres) -> {
                        val sign = if (isIncreasing) 1.0 else -1.0
                        val deltaCh = (targetKmzMetres - last.chainageMetres) * sign
                        (last.distanceAlongAlignmentM + deltaCh).coerceIn(0.0, cached.totalLengthM)
                    }
                    else -> {
                        var mA = first
                        var mB = last
                        for (k in 0 until mapped.size - 1) {
                            val cA = mapped[k]
                            val cB = mapped[k + 1]
                            val minCh = minOf(cA.chainageMetres, cB.chainageMetres)
                            val maxCh = maxOf(cA.chainageMetres, cB.chainageMetres)
                            if (targetKmzMetres in minCh..maxCh) {
                                mA = cA
                                mB = cB
                                break
                            }
                        }
                        val chSpan = (mB.chainageMetres - mA.chainageMetres).toDouble()
                        val frac = if (abs(chSpan) > 0.01) {
                            ((targetKmzMetres - mA.chainageMetres).toDouble() / chSpan).coerceIn(0.0, 1.0)
                        } else 0.0
                        mA.distanceAlongAlignmentM + frac * (mB.distanceAlongAlignmentM - mA.distanceAlongAlignmentM)
                    }
                }

                val pt = ChainageMatcher.interpolatePointAtDistanceAlongAlignment(cached, targetDistAlong)
                return ResolvedPointPosition(pt, targetDistAlong, 0.0)
            } else if (mapped.size == 1) {
                val single = mapped.first()
                val sign = if (cached.direction == "DECREASING") -1.0 else 1.0
                val deltaCh = (targetKmzMetres - single.chainageMetres) * sign
                val targetDistAlong = (single.distanceAlongAlignmentM + deltaCh).coerceIn(0.0, cached.totalLengthM)
                val pt = ChainageMatcher.interpolatePointAtDistanceAlongAlignment(cached, targetDistAlong)
                return ResolvedPointPosition(pt, targetDistAlong, 0.0)
            }
        }

        val markers = routeData.markers
        if (markers.isEmpty()) {
            return ResolvedPointPosition(fallbackGps ?: GeoPoint(0.0, 0.0), 0.0)
        }

        val exact = markers.find { it.chainage.metres == targetKmzMetres }
        if (exact != null) {
            val dist = calculateCumulativeRouteDistanceForMarker(markers, exact)
            return ResolvedPointPosition(exact.point, dist)
        }

        val sortedByCh = markers.sortedBy { it.chainage.metres }
        val before = sortedByCh.lastOrNull { it.chainage.metres <= targetKmzMetres }
        val after = sortedByCh.firstOrNull { it.chainage.metres >= targetKmzMetres }

        if (before != null && after != null && before != after) {
            val spanMetres = (after.chainage.metres - before.chainage.metres).toDouble()
            val fraction = ((targetKmzMetres - before.chainage.metres) / spanMetres).coerceIn(0.0, 1.0)
            val lat = before.point.latitude + fraction * (after.point.latitude - before.point.latitude)
            val lng = before.point.longitude + fraction * (after.point.longitude - before.point.longitude)
            val interpolatedPoint = GeoPoint(lat, lng)

            val beforeDist = calculateCumulativeRouteDistanceForMarker(markers, before)
            val segmentDist = GeoUtils.distanceMetres(before.point, after.point)
            return ResolvedPointPosition(interpolatedPoint, beforeDist + fraction * segmentDist)
        }

        val nearest = markers.minByOrNull { abs(it.chainage.metres - targetKmzMetres) } ?: markers.first()
        val nearestDist = calculateCumulativeRouteDistanceForMarker(markers, nearest)
        return ResolvedPointPosition(fallbackGps ?: nearest.point, nearestDist)
    }

    private fun calculateCumulativeRouteDistanceForMarker(markers: List<RouteMarker>, target: RouteMarker): Double {
        if (markers.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until markers.size - 1) {
            if (markers[i].id == target.id) return total
            total += GeoUtils.distanceMetres(markers[i].point, markers[i + 1].point)
        }
        return total
    }

    /**
     * Sorts correction points strictly according to their progression order along the KMZ alignment.
     */
    fun sortPointsInRouteOrder(
        points: List<ChainageCorrectionPoint>,
        routeData: RouteData
    ): List<ChainageCorrectionPoint> {
        if (points.size <= 1) return points
        val enriched = points.map { pt ->
            if (pt.routeDistanceMetres > 0.01) {
                pt
            } else {
                val resolved = resolveRoutePositionForChainage(routeData, pt.originalKmzMetres, pt.point)
                pt.copy(routeDistanceMetres = resolved.routeDistanceMetres)
            }
        }
        val hasDistinctRouteDistances = enriched.map { (it.routeDistanceMetres * 10).roundToLong() }.distinct().size > 1
        return if (hasDistinctRouteDistances) {
            enriched.sortedBy { it.routeDistanceMetres }
        } else {
            val firstMarker = routeData.markers.firstOrNull()?.chainage?.metres ?: 0L
            val lastMarker = routeData.markers.lastOrNull()?.chainage?.metres ?: 0L
            val isDecreasing = firstMarker > lastMarker
            if (isDecreasing) {
                enriched.sortedByDescending { it.originalKmzMetres }
            } else {
                enriched.sortedBy { it.originalKmzMetres }
            }
        }
    }

    /**
     * Extracts permanent verified reference points from existing KMZ/KML route markers
     * at a desired step interval (e.g., every 2,000 m or all key milestones).
     */
    fun extractReferencePointsFromRoute(
        routeData: RouteData,
        projectId: String,
        stepMetres: Long = 2000L,
        maxPoints: Int = 50
    ): List<ChainageCorrectionPoint> {
        if (routeData.markers.isEmpty()) return emptyList()
        val candidates = routeData.markers.filter { m ->
            stepMetres <= 0L || (m.chainage.metres % stepMetres == 0L)
        }.ifEmpty {
            listOfNotNull(routeData.markers.firstOrNull(), routeData.markers.lastOrNull()).distinctBy { it.id }
        }

        return candidates.take(maxPoints).map { m ->
            val resolved = resolveRoutePositionForChainage(routeData, m.chainage.metres, m.point)
            ChainageCorrectionPoint(
                routeId = routeData.id,
                projectId = projectId,
                latitude = m.point.latitude,
                longitude = m.point.longitude,
                altitude = m.point.altitude,
                originalKmzMetres = m.chainage.metres,
                correctedMetres = m.chainage.metres,
                routeDistanceMetres = resolved.routeDistanceMetres,
                description = m.label.ifBlank { "KMZ Ref ${m.chainage.format()}" },
                isEnabled = true,
                referenceSource = ReferencePointSource.KMZ_IMPORT,
                verificationStatus = CalibrationValidationStatus.VERIFIED
            )
        }
    }

    /**
     * Applies the Unified Permanent Chainage Calibration Engine to a live or queried KMZ match.
     *
     * Key guarantee:
     * - Never drops or freezes calibration when GPS confidence is MEDIUM or LOW (as long as matched to alignment).
     * - Prioritizes verified permanent control points -> alignment geometry -> piecewise interpolation.
     * - Detects conflicting verified control points and flags NEEDS_REVIEW / CONFLICT_DETECTED.
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
        val startNs = System.nanoTime()
        try {
            if (confidence == MatchConfidence.NO_MATCH) {
                return null
            }

            val enabledPoints = correctionPoints.filter {
                it.isEnabled &&
                    it.verificationStatus != CalibrationValidationStatus.INVALID &&
                    (it.routeId.isBlank() || routeData.id.isBlank() || it.routeId == routeData.id)
            }
            if (enabledPoints.isEmpty()) return null

            val sortedPoints = sortPointsInRouteOrder(enabledPoints, routeData)
            val conflictReport = detectReferenceConflicts(sortedPoints, routeData)
            val currentKmzMetres = rawChainage.metres

            val currentRouteDist = if (distanceAlongAlignmentMetres > 0.01) {
                distanceAlongAlignmentMetres
            } else {
                resolveRoutePositionForChainage(routeData, currentKmzMetres, gpsPoint).routeDistanceMetres
            }

            val firstM = routeData.markers.firstOrNull()?.chainage?.metres ?: 0L
            val lastM = routeData.markers.lastOrNull()?.chainage?.metres ?: 0L
            val isRouteDecreasing = firstM > lastM && routeData.markers.size >= 2
            val routeDirectionSign = if (isRouteDecreasing) -1L else 1L

            val baseValidationStatus = when {
                conflictReport.hasConflict -> CalibrationValidationStatus.INVALID
                confidence == MatchConfidence.LOW || isUncertainJump -> CalibrationValidationStatus.LOW_CONFIDENCE
                conflictReport.validationStatus == CalibrationValidationStatus.NEEDS_REVIEW -> CalibrationValidationStatus.NEEDS_REVIEW
                else -> CalibrationValidationStatus.VERIFIED
            }

            // Tier 1 check: Are we directly at a verified control point (within 1.5m along alignment)?
            val exactControlPt = sortedPoints.firstOrNull { pt ->
                (currentRouteDist > 0.01 && pt.routeDistanceMetres > 0.01 && abs(currentRouteDist - pt.routeDistanceMetres) <= 1.5) ||
                    abs(currentKmzMetres - pt.originalKmzMetres) <= 1L
            }
            if (exactControlPt != null && !conflictReport.hasConflict) {
                val corrected = Chainage(exactControlPt.correctedMetres.coerceAtLeast(0L))
                val appliedOffset = corrected.metres - currentKmzMetres
                val signStr = if (appliedOffset >= 0) "+${appliedOffset}m" else "${appliedOffset}m"
                totalCalibrationsComputed.incrementAndGet()
                return SmartCorrectionResult(
                    correctedChainage = corrected,
                    rawKmzChainage = rawChainage,
                    appliedOffsetMetres = appliedOffset,
                    mode = CorrectionMode.SINGLE_POINT_PROPAGATION,
                    infoMessage = "Verified Ref ${exactControlPt.correctedChainage.format()} ($signStr)",
                    validationStatus = baseValidationStatus,
                    hierarchyTier = CalibrationHierarchyTier.TIER_1_VERIFIED_CONTROL_POINT,
                    hasConflict = false
                )
            }

            // CASE 1: Single Fixed Control Point -> Alignment-Distance Propagation
            if (sortedPoints.size == 1) {
                val p1 = sortedPoints.first()
                val correctedMetres = if (currentRouteDist > 0.01 && p1.routeDistanceMetres > 0.01) {
                    val deltaDistAlong = currentRouteDist - p1.routeDistanceMetres
                    (p1.correctedMetres + (routeDirectionSign * deltaDistAlong).roundToLong()).coerceAtLeast(0L)
                } else {
                    (currentKmzMetres + p1.offsetMetres).coerceAtLeast(0L)
                }

                val appliedOffset = correctedMetres - currentKmzMetres
                val signStr = if (appliedOffset >= 0) "+${appliedOffset}m" else "${appliedOffset}m"
                val desc = p1.description.takeIf { it.isNotBlank() } ?: "Ref ${p1.correctedChainage.format()}"
                totalCalibrationsComputed.incrementAndGet()

                return SmartCorrectionResult(
                    correctedChainage = Chainage(correctedMetres),
                    rawKmzChainage = rawChainage,
                    appliedOffsetMetres = appliedOffset,
                    mode = CorrectionMode.SINGLE_POINT_PROPAGATION,
                    infoMessage = "$desc ($signStr)",
                    validationStatus = baseValidationStatus,
                    hierarchyTier = CalibrationHierarchyTier.TIER_2_VALIDATED_ALIGNMENT,
                    hasConflict = false
                )
            }

            // CASE 2: Multiple Fixed Control Points -> Piecewise Alignment Interpolation
            var beforeIdx = -1
            var afterIdx = -1

            val useRouteDist = currentRouteDist > 0.01 && sortedPoints.all { it.routeDistanceMetres > 0.01 }

            if (useRouteDist) {
                for (i in sortedPoints.indices) {
                    if (sortedPoints[i].routeDistanceMetres <= currentRouteDist) {
                        beforeIdx = i
                    }
                    if (sortedPoints[i].routeDistanceMetres >= currentRouteDist && afterIdx == -1) {
                        afterIdx = i
                    }
                }
            } else {
                for (i in sortedPoints.indices) {
                    val condBefore = if (isRouteDecreasing) {
                        sortedPoints[i].originalKmzMetres >= currentKmzMetres
                    } else {
                        sortedPoints[i].originalKmzMetres <= currentKmzMetres
                    }
                    if (condBefore) beforeIdx = i

                    val condAfter = if (isRouteDecreasing) {
                        sortedPoints[i].originalKmzMetres <= currentKmzMetres
                    } else {
                        sortedPoints[i].originalKmzMetres >= currentKmzMetres
                    }
                    if (condAfter && afterIdx == -1) afterIdx = i
                }
            }

            totalCalibrationsComputed.incrementAndGet()

            return when {
                // Before first control point: propagate from first verified reference along alignment
                beforeIdx == -1 && afterIdx != -1 -> {
                    val firstPt = sortedPoints.first()
                    val correctedMetres = if (useRouteDist) {
                        val deltaDist = currentRouteDist - firstPt.routeDistanceMetres // negative
                        (firstPt.correctedMetres + (routeDirectionSign * deltaDist).roundToLong()).coerceAtLeast(0L)
                    } else {
                        (currentKmzMetres + firstPt.offsetMetres).coerceAtLeast(0L)
                    }
                    val appliedOffset = correctedMetres - currentKmzMetres
                    val signStr = if (appliedOffset >= 0) "+${appliedOffset}m" else "${appliedOffset}m"
                    SmartCorrectionResult(
                        correctedChainage = Chainage(correctedMetres),
                        rawKmzChainage = rawChainage,
                        appliedOffsetMetres = appliedOffset,
                        mode = CorrectionMode.EXTRAPOLATED_PRE_START,
                        infoMessage = "Pre-Ref ${firstPt.correctedChainage.format()} ($signStr)",
                        validationStatus = if (baseValidationStatus == CalibrationValidationStatus.VERIFIED) {
                            CalibrationValidationStatus.LOW_CONFIDENCE
                        } else baseValidationStatus,
                        hierarchyTier = CalibrationHierarchyTier.TIER_2_VALIDATED_ALIGNMENT,
                        hasConflict = conflictReport.hasConflict
                    )
                }

                // After last control point: propagate from last verified reference along alignment
                beforeIdx != -1 && afterIdx == -1 -> {
                    val lastPt = sortedPoints.last()
                    val correctedMetres = if (useRouteDist) {
                        val deltaDist = currentRouteDist - lastPt.routeDistanceMetres // positive
                        (lastPt.correctedMetres + (routeDirectionSign * deltaDist).roundToLong()).coerceAtLeast(0L)
                    } else {
                        (currentKmzMetres + lastPt.offsetMetres).coerceAtLeast(0L)
                    }
                    val appliedOffset = correctedMetres - currentKmzMetres
                    val signStr = if (appliedOffset >= 0) "+${appliedOffset}m" else "${appliedOffset}m"
                    SmartCorrectionResult(
                        correctedChainage = Chainage(correctedMetres),
                        rawKmzChainage = rawChainage,
                        appliedOffsetMetres = appliedOffset,
                        mode = CorrectionMode.EXTRAPOLATED_POST_END,
                        infoMessage = "Post-Ref ${lastPt.correctedChainage.format()} ($signStr)",
                        validationStatus = if (baseValidationStatus == CalibrationValidationStatus.VERIFIED) {
                            CalibrationValidationStatus.LOW_CONFIDENCE
                        } else baseValidationStatus,
                        hierarchyTier = CalibrationHierarchyTier.TIER_2_VALIDATED_ALIGNMENT,
                        hasConflict = conflictReport.hasConflict
                    )
                }

                // Exact control point match
                beforeIdx != -1 && beforeIdx == afterIdx -> {
                    val exactPt = sortedPoints[beforeIdx]
                    val correctedMetres = exactPt.correctedMetres.coerceAtLeast(0L)
                    val appliedOffset = correctedMetres - currentKmzMetres
                    val signStr = if (appliedOffset >= 0) "+${appliedOffset}m" else "${appliedOffset}m"
                    SmartCorrectionResult(
                        correctedChainage = Chainage(correctedMetres),
                        rawKmzChainage = rawChainage,
                        appliedOffsetMetres = appliedOffset,
                        mode = CorrectionMode.MULTI_POINT_INTERPOLATION,
                        infoMessage = "At Ref ${exactPt.correctedChainage.format()} ($signStr)",
                        validationStatus = baseValidationStatus,
                        hierarchyTier = CalibrationHierarchyTier.TIER_1_VERIFIED_CONTROL_POINT,
                        hasConflict = conflictReport.hasConflict
                    )
                }

                // Between two bracketing verified control points pA and pB -> Piecewise Alignment Interpolation
                beforeIdx != -1 && afterIdx != -1 -> {
                    val pA = sortedPoints[beforeIdx]
                    val pB = sortedPoints[afterIdx]

                    val spanRouteDist = pB.routeDistanceMetres - pA.routeDistanceMetres
                    val spanOrigMetres = (pB.originalKmzMetres - pA.originalKmzMetres).toDouble()
                    val spanCorrMetres = (pB.correctedMetres - pA.correctedMetres).toDouble()

                    // Check if pA and pB have a direct directional conflict on this span
                    val spanReversedConflict = (!isRouteDecreasing && spanCorrMetres < 0.0) ||
                        (isRouteDecreasing && spanCorrMetres > 0.0)
                    if (spanReversedConflict) {
                        val safeFallback = (currentKmzMetres + pA.offsetMetres).coerceAtLeast(0L)
                        val appliedOffset = safeFallback - currentKmzMetres
                        return SmartCorrectionResult(
                            correctedChainage = Chainage(safeFallback),
                            rawKmzChainage = rawChainage,
                            appliedOffsetMetres = appliedOffset,
                            mode = CorrectionMode.CONFLICT_DETECTED,
                            infoMessage = "Conflict: ${pA.correctedChainage.format()} ↔ ${pB.correctedChainage.format()} (Review Required)",
                            validationStatus = CalibrationValidationStatus.INVALID,
                            hierarchyTier = CalibrationHierarchyTier.TIER_3_PIECEWISE_INTERPOLATION,
                            hasConflict = true
                        )
                    }

                    val t = if (useRouteDist && spanRouteDist > 0.5) {
                        ((currentRouteDist - pA.routeDistanceMetres) / spanRouteDist).coerceIn(0.0, 1.0)
                    } else if (abs(spanOrigMetres) > 0.5) {
                        ((currentKmzMetres - pA.originalKmzMetres).toDouble() / spanOrigMetres).coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }

                    val scaleFactor = if (abs(spanOrigMetres) > 1.0) {
                        abs(spanCorrMetres / spanOrigMetres)
                    } else 1.0

                    val interpolatedCorrMetres = if (abs(scaleFactor - 1.0) <= MAX_INTERPOLATION_SCALE_DEVIATION) {
                        (pA.correctedMetres + t * spanCorrMetres).roundToLong().coerceAtLeast(0L)
                    } else {
                        val blendedOffset = (pA.offsetMetres + t * (pB.offsetMetres - pA.offsetMetres)).roundToLong()
                        (currentKmzMetres + blendedOffset).coerceAtLeast(0L)
                    }

                    val appliedOffset = interpolatedCorrMetres - currentKmzMetres
                    val signStr = if (appliedOffset >= 0) "+${appliedOffset}m" else "${appliedOffset}m"
                    val warningSuffix = if (abs(scaleFactor - 1.0) > MAX_INTERPOLATION_SCALE_DEVIATION) " [Eq]" else ""

                    SmartCorrectionResult(
                        correctedChainage = Chainage(interpolatedCorrMetres),
                        rawKmzChainage = rawChainage,
                        appliedOffsetMetres = appliedOffset,
                        mode = CorrectionMode.MULTI_POINT_INTERPOLATION,
                        infoMessage = "${pA.correctedChainage.format()} ↔ ${pB.correctedChainage.format()} ($signStr)$warningSuffix",
                        validationStatus = baseValidationStatus,
                        hierarchyTier = CalibrationHierarchyTier.TIER_3_PIECEWISE_INTERPOLATION,
                        hasConflict = conflictReport.hasConflict
                    )
                }

                else -> null
            }
        } finally {
            lastCalibrationLatencyMicros.set((System.nanoTime() - startNs) / 1_000L)
        }
    }

    /**
     * Resolves the exact alignment distance (in metres from start of active alignment)
     * corresponding to a target CALIBRATED chainage value.
     */
    fun resolveAlignmentDistanceForCalibratedChainage(
        targetCalibratedMetres: Long,
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>
    ): Pair<Double, Long> {
        val activeAlign = routeData.activeAlignment ?: routeData.alignments.firstOrNull()
        val cached = if (activeAlign != null && activeAlign.points.size >= 2) {
            ChainageMatcher.getOrCreateCachedGeometry(activeAlign, routeData.markers)
        } else null

        val enabledPoints = sortPointsInRouteOrder(
            correctionPoints.filter { it.isEnabled && it.verificationStatus != CalibrationValidationStatus.INVALID },
            routeData
        )

        val isDecreasing = if (cached != null) {
            cached.direction == "DECREASING"
        } else {
            val f = routeData.markers.firstOrNull()?.chainage?.metres ?: 0L
            val l = routeData.markers.lastOrNull()?.chainage?.metres ?: 0L
            f > l && routeData.markers.size >= 2
        }
        val dirSign = if (isDecreasing) -1.0 else 1.0

        // Helper to compute original KMZ chainage at a given alignment distance
        fun originalChainageAtDistance(distM: Double): Long {
            if (cached == null || cached.mappedMarkers.isEmpty()) return targetCalibratedMetres
            val mapped = cached.mappedMarkers
            if (mapped.size == 1) {
                val s = mapped.first()
                return (s.chainageMetres + dirSign * (distM - s.distanceAlongAlignmentM)).roundToLong()
            }
            val first = mapped.first()
            val last = mapped.last()
            if (distM <= first.distanceAlongAlignmentM) {
                return (first.chainageMetres + dirSign * (distM - first.distanceAlongAlignmentM)).roundToLong()
            }
            if (distM >= last.distanceAlongAlignmentM) {
                return (last.chainageMetres + dirSign * (distM - last.distanceAlongAlignmentM)).roundToLong()
            }
            var mA = first
            var mB = last
            for (k in 0 until mapped.size - 1) {
                if (distM in mapped[k].distanceAlongAlignmentM..mapped[k + 1].distanceAlongAlignmentM) {
                    mA = mapped[k]
                    mB = mapped[k + 1]
                    break
                }
            }
            val span = mB.distanceAlongAlignmentM - mA.distanceAlongAlignmentM
            val frac = if (span > 0.01) ((distM - mA.distanceAlongAlignmentM) / span).coerceIn(0.0, 1.0) else 0.0
            return (mA.chainageMetres + frac * (mB.chainageMetres - mA.chainageMetres)).roundToLong()
        }

        if (enabledPoints.isEmpty()) {
            val pos = resolveRoutePositionForChainage(routeData, targetCalibratedMetres)
            return Pair(pos.routeDistanceMetres, targetCalibratedMetres)
        }

        if (enabledPoints.size == 1) {
            val p1 = enabledPoints.first()
            val origCh = (targetCalibratedMetres - p1.offsetMetres).coerceAtLeast(0L)
            if (p1.routeDistanceMetres > 0.01 && cached != null) {
                val deltaCh = (targetCalibratedMetres - p1.correctedMetres) * dirSign
                val targetDist = (p1.routeDistanceMetres + deltaCh).coerceIn(0.0, cached.totalLengthM)
                return Pair(targetDist, origCh)
            } else {
                val pos = resolveRoutePositionForChainage(routeData, origCh)
                return Pair(pos.routeDistanceMetres, origCh)
            }
        }

        // Multiple verified control points -> inverse piecewise interpolation along alignment distance
        val firstPt = enabledPoints.first()
        val lastPt = enabledPoints.last()
        val beforeFirst = (!isDecreasing && targetCalibratedMetres <= firstPt.correctedMetres) ||
            (isDecreasing && targetCalibratedMetres >= firstPt.correctedMetres)
        val afterLast = (!isDecreasing && targetCalibratedMetres >= lastPt.correctedMetres) ||
            (isDecreasing && targetCalibratedMetres <= lastPt.correctedMetres)

        val targetDist: Double = when {
            beforeFirst -> {
                val deltaCh = (targetCalibratedMetres - firstPt.correctedMetres) * dirSign
                val maxLen = cached?.totalLengthM ?: Double.MAX_VALUE
                (firstPt.routeDistanceMetres + deltaCh).coerceIn(0.0, maxLen)
            }
            afterLast -> {
                val deltaCh = (targetCalibratedMetres - lastPt.correctedMetres) * dirSign
                val maxLen = cached?.totalLengthM ?: Double.MAX_VALUE
                (lastPt.routeDistanceMetres + deltaCh).coerceIn(0.0, maxLen)
            }
            else -> {
                var pA = firstPt
                var pB = lastPt
                for (i in 0 until enabledPoints.size - 1) {
                    val cA = enabledPoints[i]
                    val cB = enabledPoints[i + 1]
                    val minC = minOf(cA.correctedMetres, cB.correctedMetres)
                    val maxC = maxOf(cA.correctedMetres, cB.correctedMetres)
                    if (targetCalibratedMetres in minC..maxC) {
                        pA = cA
                        pB = cB
                        break
                    }
                }
                val corrSpan = (pB.correctedMetres - pA.correctedMetres).toDouble()
                val frac = if (abs(corrSpan) > 0.01) {
                    ((targetCalibratedMetres - pA.correctedMetres).toDouble() / corrSpan).coerceIn(0.0, 1.0)
                } else 0.0
                pA.routeDistanceMetres + frac * (pB.routeDistanceMetres - pA.routeDistanceMetres)
            }
        }

        val origCh = originalChainageAtDistance(targetDist)
        return Pair(targetDist, origCh)
    }

    /**
     * Generates Automatic 2 km (2,000 m) Calibration Markers along the calibrated KMZ/KML alignment.
     *
     * Example:
     * - Verified reference point: "200+050"
     * - Selected range: "198+000" to "202+000"
     * - Generated markers: "198+000", "200+000", "202+000"
     *
     * Every generated marker has its exact geographical coordinate interpolated along the alignment
     * and is explicitly marked as `isGenerated2KmMarker = true` to distinguish it from permanent control points.
     */
    fun generate2KmCalibrationMarkers(
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>,
        rangeStartMetres: Long? = null,
        rangeEndMetres: Long? = null,
        intervalMetres: Long = DEFAULT_CALIBRATION_MARKER_INTERVAL_METRES
    ): List<GeneratedChainageMarker> {
        val safeInterval = if (intervalMetres <= 0L) DEFAULT_CALIBRATION_MARKER_INTERVAL_METRES else intervalMetres

        val defaultBounds = computeCalibratedRouteRange(routeData, correctionPoints)
        val rawStart = rangeStartMetres ?: defaultBounds.first
        val rawEnd = rangeEndMetres ?: defaultBounds.second

        val minCh = minOf(rawStart, rawEnd).coerceAtLeast(0L)
        val maxCh = maxOf(rawStart, rawEnd).coerceAtLeast(minCh)

        // Find first multiple of safeInterval >= minCh
        val firstMultiple = if (minCh % safeInterval == 0L) {
            minCh
        } else {
            ((minCh / safeInterval) + 1L) * safeInterval
        }

        if (firstMultiple > maxCh) return emptyList()

        val activeAlign = routeData.activeAlignment ?: routeData.alignments.firstOrNull()
        val cached = if (activeAlign != null && activeAlign.points.size >= 2) {
            ChainageMatcher.getOrCreateCachedGeometry(activeAlign, routeData.markers)
        } else null

        val isDecreasing = if (cached != null) {
            cached.direction == "DECREASING"
        } else {
            val f = routeData.markers.firstOrNull()?.chainage?.metres ?: 0L
            val l = routeData.markers.lastOrNull()?.chainage?.metres ?: 0L
            f > l && routeData.markers.size >= 2
        }

        val generated = mutableListOf<GeneratedChainageMarker>()
        var currMetres = firstMultiple
        while (currMetres <= maxCh) {
            val (distAlongM, origKmzMetres) = resolveAlignmentDistanceForCalibratedChainage(
                targetCalibratedMetres = currMetres,
                routeData = routeData,
                correctionPoints = correctionPoints
            )

            val geoPoint = if (cached != null) {
                ChainageMatcher.interpolatePointAtDistanceAlongAlignment(cached, distAlongM)
            } else {
                resolveRoutePositionForChainage(routeData, origKmzMetres).point
            }

            generated.add(
                GeneratedChainageMarker(
                    calibratedChainage = Chainage(currMetres),
                    originalKmzChainage = Chainage(origKmzMetres.coerceAtLeast(0L)),
                    point = geoPoint,
                    distanceAlongAlignmentMetres = distAlongM,
                    alignmentName = activeAlign?.name ?: routeData.name,
                    intervalMetres = safeInterval,
                    isGenerated2KmMarker = true,
                    interpolationMode = if (correctionPoints.size >= 2) "PIECEWISE_CALIBRATED" else "SINGLE_REF_CALIBRATED"
                )
            )
            currMetres += safeInterval
        }

        return if (isDecreasing) generated.reversed() else generated
    }

    /**
     * Computes the full calibrated chainage range (startMetres, endMetres) for the active route.
     */
    fun computeCalibratedRouteRange(
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>
    ): Pair<Long, Long> {
        val rawStart = routeData.startChainage ?: routeData.markers.firstOrNull()?.chainage ?: Chainage(0L)
        val rawEnd = routeData.endChainage ?: routeData.markers.lastOrNull()?.chainage ?: Chainage(0L)
        val activeAlign = routeData.activeAlignment ?: routeData.alignments.firstOrNull()
        val totalLen = activeAlign?.totalLengthMetres ?: 0.0

        val startCorr = applyCorrection(
            rawChainage = rawStart,
            routeData = routeData,
            correctionPoints = correctionPoints,
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = 0.0
        )?.correctedChainage?.metres ?: rawStart.metres

        val endCorr = applyCorrection(
            rawChainage = rawEnd,
            routeData = routeData,
            correctionPoints = correctionPoints,
            confidence = MatchConfidence.HIGH,
            distanceAlongAlignmentMetres = totalLen
        )?.correctedChainage?.metres ?: rawEnd.metres

        return Pair(startCorr, endCorr)
    }

    // ============================================================================================
    // Final Apply — Existing KMZ/KML Replacement with Minimal-Change XML Editing
    // ============================================================================================

    private val NON_CHAINAGE_FEATURE_KEYWORDS = listOf(
        "toll", "plaza", "bridge", "culvert", "rob", "rub", "vup", "pup", "lvup", "svup",
        "flyover", "interchange", "junction", "rest area", "truck lay", "bus bay", "office",
        "camp", "plant", "quarry", "hospital", "police", "school", "temple", "mosque", "church",
        "petrol", "fuel", "weighbridge", "tunnel", "gantry", "sos", "cctv", "centerline", "alignment"
    )

    private val PLACEMARK_REGEX = Regex("""<Placemark\b[^>]*>[\s\S]*?</Placemark>""", RegexOption.IGNORE_CASE)
    private val NAME_TAG_REGEX = Regex("""<name>([\s\S]*?)</name>""", RegexOption.IGNORE_CASE)
    private val DESC_TAG_REGEX = Regex("""<description>([\s\S]*?)</description>""", RegexOption.IGNORE_CASE)
    private val STYLE_URL_REGEX = Regex("""<styleUrl>([\s\S]*?)</styleUrl>""", RegexOption.IGNORE_CASE)
    private val POINT_COORDS_REGEX = Regex("""<Point\b[^>]*>[\s\S]*?<coordinates>([\s\S]*?)</coordinates>[\s\S]*?</Point>""", RegexOption.IGNORE_CASE)
    private val GEOMETRY_CONTAINER_REGEX = Regex("""<(LineString|Polygon|MultiGeometry|Model|GroundOverlay)\b""", RegexOption.IGNORE_CASE)

    /**
     * Determines whether a single `<Placemark>...</Placemark>` XML block is strictly a
     * chainage-only Point marker, and if so, returns its parsed Chainage and coordinates.
     *
     * Never matches LineString, MultiGeometry, Polygon, or non-chainage landmarks/structures.
     */
    private fun inspectChainageOnlyPlacemark(placemarkXml: String): Pair<Chainage, GeoPoint?>? {
        // Must NOT contain LineString, Polygon, MultiGeometry, Model, or GroundOverlay
        if (GEOMETRY_CONTAINER_REGEX.containsMatchIn(placemarkXml)) {
            return null
        }
        // Must contain a <Point><coordinates>...</coordinates></Point>
        val coordsMatch = POINT_COORDS_REGEX.find(placemarkXml) ?: return null
        val rawName = NAME_TAG_REGEX.find(placemarkXml)?.groupValues?.getOrNull(1)
            ?.replace("<![CDATA[", "")?.replace("]]>", "")?.trim().orEmpty()
        val rawDesc = DESC_TAG_REGEX.find(placemarkXml)?.groupValues?.getOrNull(1)
            ?.replace("<![CDATA[", "")?.replace("]]>", "")?.trim().orEmpty()

        val lowerName = rawName.lowercase(Locale.US)
        if (NON_CHAINAGE_FEATURE_KEYWORDS.any { lowerName.contains(it) }) {
            return null
        }

        val parsedChainage = Chainage.parse(rawName)
            ?: (if (rawName.isBlank()) Chainage.parseFromDescription(rawDesc) else null)
            ?: return null

        val coordTokens = coordsMatch.groupValues[1].trim().split(Regex("\\s+")).firstOrNull()?.split(",")
        val geoPt = if (coordTokens != null && coordTokens.size >= 2) {
            val lng = coordTokens[0].toDoubleOrNull()
            val lat = coordTokens[1].toDoubleOrNull()
            val alt = coordTokens.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            if (lat != null && lng != null) GeoPoint(lat, lng, alt) else null
        } else null

        return Pair(parsedChainage, geoPt)
    }

    /**
     * Generates a preview of the minimal-change KMZ/KML calibration replacement without modifying any file.
     */
    fun previewKmzReplacement(
        sourceBytes: ByteArray,
        sourceFileName: String,
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>,
        rangeStartMetres: Long? = null,
        rangeEndMetres: Long? = null,
        intervalMetres: Long = DEFAULT_CALIBRATION_MARKER_INTERVAL_METRES
    ): KmzReplacementPreview {
        val defaultRange = computeCalibratedRouteRange(routeData, correctionPoints)
        val minRange = minOf(rangeStartMetres ?: defaultRange.first, rangeEndMetres ?: defaultRange.second).coerceAtLeast(0L)
        val maxRange = maxOf(rangeStartMetres ?: defaultRange.first, rangeEndMetres ?: defaultRange.second).coerceAtLeast(minRange)

        val generatedMarkers = generate2KmCalibrationMarkers(
            routeData = routeData,
            correctionPoints = correctionPoints,
            rangeStartMetres = minRange,
            rangeEndMetres = maxRange,
            intervalMetres = intervalMetres
        )

        val extracted = extractKmlAndArchiveInfo(sourceBytes)
        val kmlText = extracted.kmlContent

        var removedCount = 0
        val removedSamples = mutableListOf<String>()
        var preservedChainageOutside = 0
        var preservedNonChainage = 0

        for (match in PLACEMARK_REGEX.findAll(kmlText)) {
            val block = match.value
            val inspected = inspectChainageOnlyPlacemark(block)
            if (inspected == null) {
                preservedNonChainage++
            } else {
                val (ch, pt) = inspected
                val calibratedAtPt = applyCorrection(
                    rawChainage = ch,
                    routeData = routeData,
                    correctionPoints = correctionPoints,
                    gpsPoint = pt,
                    confidence = MatchConfidence.HIGH
                )?.correctedChainage ?: ch

                val inRange = (ch.metres in minRange..maxRange) || (calibratedAtPt.metres in minRange..maxRange)
                if (inRange) {
                    removedCount++
                    if (removedSamples.size < 6) removedSamples.add(ch.format())
                } else {
                    preservedChainageOutside++
                }
            }
        }

        val baseName = sourceFileName.substringBeforeLast(".")
        val ext = if (extracted.isKmzArchive || sourceFileName.endsWith(".kmz", ignoreCase = true)) "kmz" else "kml"
        val suggestedName = "${baseName}_calibrated_2km.$ext"
        val conflictReport = detectReferenceConflicts(correctionPoints, routeData)

        return KmzReplacementPreview(
            rangeStart = Chainage(minRange),
            rangeEnd = Chainage(maxRange),
            intervalMetres = intervalMetres,
            removedOldMarkersCount = removedCount,
            removedSampleLabels = removedSamples,
            inserted2KmMarkers = generatedMarkers,
            preservedChainageMarkersOutsideRangeCount = preservedChainageOutside,
            preservedNonChainagePlacemarksCount = preservedNonChainage,
            preservedLineStringsCount = routeData.diagnostics?.rawLineStringCount ?: routeData.alignments.size,
            preservedArchiveEntriesCount = extracted.otherEntries.size,
            suggestedOutputFileName = suggestedName,
            validationStatus = conflictReport.validationStatus,
            summaryMessage = "Replace $removedCount old markers in ${Chainage(minRange).format()}..${Chainage(maxRange).format()} with ${generatedMarkers.size} calibrated 2 km markers"
        )
    }

    /**
     * Performs Minimal-Change XML Editing on an existing KMZ or KML byte array:
     * 1. Removes ONLY verified obsolete chainage-only `<Placemark>` markers inside `[rangeStartMetres, rangeEndMetres]`.
     * 2. Inserts newly calibrated 2 km `<Placemark>` markers at their exact geographical positions along the alignment.
     * 3. Preserves 100% of all unrelated `<LineString>`, `<MultiGeometry>`, folders, styles, icons, `<ExtendedData>`,
     *    metadata, and non-KML ZIP archive entries byte-for-byte.
     * 4. Validates the output KMZ/KML with `KmzParser.parseBytes` before returning.
     */
    fun applyCalibrationToExistingKmzOrKml(
        sourceBytes: ByteArray,
        sourceFileName: String,
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>,
        rangeStartMetres: Long? = null,
        rangeEndMetres: Long? = null,
        intervalMetres: Long = DEFAULT_CALIBRATION_MARKER_INTERVAL_METRES,
        profileName: String = "Permanent Calibration Profile"
    ): Pair<ByteArray, KmzReplacementPreview> {
        val conflictReport = detectReferenceConflicts(correctionPoints, routeData)
        if (conflictReport.hasConflict) {
            throw IllegalStateException(
                "Cannot apply calibration while conflicting reference points exist: ${conflictReport.conflicts.first()}"
            )
        }

        val preview = previewKmzReplacement(
            sourceBytes = sourceBytes,
            sourceFileName = sourceFileName,
            routeData = routeData,
            correctionPoints = correctionPoints,
            rangeStartMetres = rangeStartMetres,
            rangeEndMetres = rangeEndMetres,
            intervalMetres = intervalMetres
        )

        val minRange = preview.rangeStart.metres
        val maxRange = preview.rangeEnd.metres
        val extracted = extractKmlAndArchiveInfo(sourceBytes)
        val originalKml = extracted.kmlContent

        var detectedChainageStyleUrl: String? = null
        var firstRemovedOffset = -1

        // Build modified KML with surgical <Placemark> replacement
        val sb = StringBuilder(originalKml.length + preview.inserted2KmMarkers.size * 320)
        var lastIndex = 0

        for (match in PLACEMARK_REGEX.findAll(originalKml)) {
            val block = match.value
            val inspected = inspectChainageOnlyPlacemark(block)
            if (inspected != null) {
                if (detectedChainageStyleUrl == null) {
                    detectedChainageStyleUrl = STYLE_URL_REGEX.find(block)?.groupValues?.getOrNull(1)?.trim()
                }
                val (ch, pt) = inspected
                val calibratedAtPt = applyCorrection(
                    rawChainage = ch,
                    routeData = routeData,
                    correctionPoints = correctionPoints,
                    gpsPoint = pt,
                    confidence = MatchConfidence.HIGH
                )?.correctedChainage ?: ch

                val inRange = (ch.metres in minRange..maxRange) || (calibratedAtPt.metres in minRange..maxRange)
                if (inRange) {
                    // Append everything before this obsolete chainage placemark
                    sb.append(originalKml, lastIndex, match.range.first)
                    if (firstRemovedOffset == -1) {
                        firstRemovedOffset = sb.length
                    }
                    lastIndex = match.range.last + 1
                    continue
                }
            }
            sb.append(originalKml, lastIndex, match.range.last + 1)
            lastIndex = match.range.last + 1
        }
        sb.append(originalKml, lastIndex, originalKml.length)

        // Build XML for the newly calibrated 2 km markers (plus verified control points inside range)
        val newPlacemarksXml = buildCalibratedMarkersKmlFragment(
            generatedMarkers = preview.inserted2KmMarkers,
            verifiedPoints = correctionPoints.filter {
                it.isEnabled && it.correctedMetres in minRange..maxRange && (it.correctedMetres % intervalMetres != 0L)
            },
            styleUrl = detectedChainageStyleUrl,
            profileName = profileName
        )

        val updatedKml = if (firstRemovedOffset != -1 && firstRemovedOffset <= sb.length) {
            // Insert directly at the exact folder location where old chainage markers were removed
            sb.insert(firstRemovedOffset, "\n$newPlacemarksXml\n").toString()
        } else {
            // Insert before closing </Document>, </Folder>, or </kml>
            val currentXml = sb.toString()
            val insertPos = currentXml.lastIndexOf("</Document>")
                .takeIf { it != -1 }
                ?: currentXml.lastIndexOf("</Folder>").takeIf { it != -1 }
                ?: currentXml.lastIndexOf("</kml>").takeIf { it != -1 }
                ?: currentXml.length
            StringBuilder(currentXml).insert(insertPos, "\n$newPlacemarksXml\n").toString()
        }

        val updatedKmlBytes = updatedKml.toByteArray(Charsets.UTF_8)

        val finalOutputBytes = if (extracted.isKmzArchive || sourceFileName.endsWith(".kmz", ignoreCase = true)) {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                // Write the updated KML entry
                val kmlEntry = ZipEntry(extracted.kmlEntryName.ifBlank { "doc.kml" })
                zos.putNextEntry(kmlEntry)
                zos.write(updatedKmlBytes)
                zos.closeEntry()

                // Preserve 100% of all other entries in the original KMZ archive byte-for-byte
                for ((entryName, entryBytes) in extracted.otherEntries) {
                    val entry = ZipEntry(entryName)
                    zos.putNextEntry(entry)
                    zos.write(entryBytes)
                    zos.closeEntry()
                }
            }
            baos.toByteArray()
        } else {
            updatedKmlBytes
        }

        // Validate output KMZ/KML integrity using KmzParser before returning
        KmzParser.parseBytes(finalOutputBytes, preview.suggestedOutputFileName)

        return Pair(finalOutputBytes, preview)
    }

    private fun buildCalibratedMarkersKmlFragment(
        generatedMarkers: List<GeneratedChainageMarker>,
        verifiedPoints: List<ChainageCorrectionPoint>,
        styleUrl: String?,
        profileName: String
    ): String {
        val sb = StringBuilder()
        val cleanProfile = escapeXml(profileName)

        // 1. Generated 2 km calibrated chainage markers
        for (marker in generatedMarkers) {
            val label = escapeXml(marker.label)
            val altStr = marker.point.altitude?.let { String.format(Locale.US, ",%.2f", it) } ?: ",0"
            val coordsStr = String.format(
                Locale.US,
                "%.7f,%.7f%s",
                marker.point.longitude,
                marker.point.latitude,
                altStr
            )
            sb.append("    <Placemark>\n")
            sb.append("      <name>").append(label).append("</name>\n")
            sb.append("      <description>Calibrated 2km Marker | Chainage: ")
                .append(label)
                .append(" | Original KMZ: ")
                .append(escapeXml(marker.originalKmzChainage.format()))
                .append(" | Profile: ")
                .append(cleanProfile)
                .append("</description>\n")
            if (!styleUrl.isNullOrBlank()) {
                sb.append("      <styleUrl>").append(escapeXml(styleUrl)).append("</styleUrl>\n")
            }
            sb.append("      <ExtendedData>\n")
            sb.append("        <Data name=\"ctecs_marker_type\"><value>CALIBRATED_2KM_MARKER</value></Data>\n")
            sb.append("        <Data name=\"calibrated_chainage_m\"><value>").append(marker.calibratedChainage.metres).append("</value></Data>\n")
            sb.append("        <Data name=\"original_kmz_chainage_m\"><value>").append(marker.originalKmzChainage.metres).append("</value></Data>\n")
            sb.append("      </ExtendedData>\n")
            sb.append("      <Point>\n")
            sb.append("        <coordinates>").append(coordsStr).append("</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("    </Placemark>\n")
        }

        // 2. Include verified permanent control points (explicitly tagged as VERIFIED_CONTROL_POINT)
        for (pt in verifiedPoints) {
            val label = escapeXml(pt.correctedChainage.format())
            val altStr = pt.altitude?.let { String.format(Locale.US, ",%.2f", it) } ?: ",0"
            val coordsStr = String.format(
                Locale.US,
                "%.7f,%.7f%s",
                pt.longitude,
                pt.latitude,
                altStr
            )
            sb.append("    <Placemark>\n")
            sb.append("      <name>").append(label).append("</name>\n")
            sb.append("      <description>Verified Permanent Control Point | Chainage: ")
                .append(label)
                .append(" | Source: ")
                .append(escapeXml(pt.referenceSource))
                .append("</description>\n")
            if (!styleUrl.isNullOrBlank()) {
                sb.append("      <styleUrl>").append(escapeXml(styleUrl)).append("</styleUrl>\n")
            }
            sb.append("      <ExtendedData>\n")
            sb.append("        <Data name=\"ctecs_marker_type\"><value>VERIFIED_CONTROL_POINT</value></Data>\n")
            sb.append("        <Data name=\"reference_id\"><value>").append(escapeXml(pt.id)).append("</value></Data>\n")
            sb.append("        <Data name=\"calibrated_chainage_m\"><value>").append(pt.correctedMetres).append("</value></Data>\n")
            sb.append("      </ExtendedData>\n")
            sb.append("      <Point>\n")
            sb.append("        <coordinates>").append(coordsStr).append("</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("    </Placemark>\n")
        }

        return sb.toString().trimEnd()
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private data class ExtractedKmlArchive(
        val isKmzArchive: Boolean,
        val kmlEntryName: String,
        val kmlContent: String,
        val otherEntries: Map<String, ByteArray>
    )

    private fun extractKmlAndArchiveInfo(sourceBytes: ByteArray): ExtractedKmlArchive {
        val isZip = sourceBytes.size >= 4 &&
            sourceBytes[0] == 0x50.toByte() &&
            sourceBytes[1] == 0x4B.toByte()

        if (!isZip) {
            val content = decodeUtf8WithBomHandling(sourceBytes)
            return ExtractedKmlArchive(
                isKmzArchive = false,
                kmlEntryName = "doc.kml",
                kmlContent = content,
                otherEntries = emptyMap()
            )
        }

        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(sourceBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/')
                    if (!name.contains("../") && !name.startsWith("/")) {
                        entries[name] = zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val targetKmlName = KmzParser.findBestKmlEntryName(entries.keys.toList())
            ?: entries.keys.firstOrNull { it.endsWith(".kml", ignoreCase = true) || it.endsWith(".xml", ignoreCase = true) }
            ?: throw IllegalArgumentException("KMZ archive does not contain a valid KML entry")

        val kmlBytes = entries[targetKmlName]
            ?: throw IllegalArgumentException("Could not read KML entry '$targetKmlName' from KMZ archive")
        val other = entries.filterKeys { it != targetKmlName }

        return ExtractedKmlArchive(
            isKmzArchive = true,
            kmlEntryName = targetKmlName,
            kmlContent = decodeUtf8WithBomHandling(kmlBytes),
            otherEntries = other
        )
    }

    private fun decodeUtf8WithBomHandling(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Generates a complete KML document containing the route alignment polylines and calibrated markers.
     */
    fun generateCorrectedKmlContent(
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>${escapeXml(routeData.name)} (Calibrated)</name>\n")

        for (align in routeData.alignments) {
            if (align.points.size >= 2) {
                sb.append("    <Placemark>\n")
                sb.append("      <name>${escapeXml(align.name)}</name>\n")
                sb.append("      <LineString>\n")
                sb.append("        <tessellate>1</tessellate>\n")
                sb.append("        <coordinates>\n")
                sb.append("          ")
                align.points.forEachIndexed { idx, pt ->
                    if (idx > 0) sb.append(" ")
                    val alt = pt.altitude ?: 0.0
                    sb.append(String.format(Locale.US, "%.7f,%.7f,%.2f", pt.longitude, pt.latitude, alt))
                }
                sb.append("\n        </coordinates>\n")
                sb.append("      </LineString>\n")
                sb.append("    </Placemark>\n")
            }
        }

        val markers2Km = generate2KmCalibrationMarkers(routeData, correctionPoints)
        if (markers2Km.isNotEmpty()) {
            sb.append(
                buildCalibratedMarkersKmlFragment(
                    generatedMarkers = markers2Km,
                    verifiedPoints = correctionPoints.filter { it.isEnabled },
                    styleUrl = null,
                    profileName = routeData.name
                )
            ).append("\n")
        } else {
            for (marker in routeData.markers) {
                val smartResult = applyCorrection(
                    rawChainage = marker.chainage,
                    routeData = routeData,
                    correctionPoints = correctionPoints,
                    gpsPoint = marker.point,
                    confidence = MatchConfidence.HIGH
                )
                val finalChainage = smartResult?.correctedChainage ?: marker.chainage
                val label = escapeXml(finalChainage.format())
                val alt = marker.point.altitude ?: 0.0
                sb.append("    <Placemark>\n")
                sb.append("      <name>$label</name>\n")
                sb.append("      <Point>\n")
                sb.append(
                    String.format(
                        Locale.US,
                        "        <coordinates>%.7f,%.7f,%.2f</coordinates>\n",
                        marker.point.longitude,
                        marker.point.latitude,
                        alt
                    )
                )
                sb.append("      </Point>\n")
                sb.append("    </Placemark>\n")
            }
        }

        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    data class ValidationResult(
        val isValid: Boolean,
        val warnings: List<String> = emptyList()
    )

    /**
     * Validates a candidate reference point against existing control points and the active route.
     */
    fun validatePoint(
        point: ChainageCorrectionPoint,
        existingPoints: List<ChainageCorrectionPoint>,
        routeData: RouteData
    ): ValidationResult {
        val warnings = mutableListOf<String>()
        val otherPoints = existingPoints.filter { it.id != point.id && it.isEnabled }

        val minKmz = minOf(routeData.startChainage?.metres ?: 0L, routeData.endChainage?.metres ?: Long.MAX_VALUE)
        val maxKmz = maxOf(routeData.startChainage?.metres ?: 0L, routeData.endChainage?.metres ?: Long.MAX_VALUE)
        if (routeData.startChainage != null && routeData.endChainage != null) {
            if (point.originalKmzMetres < minKmz - 500 || point.originalKmzMetres > maxKmz + 500) {
                warnings.add("Original chainage (${point.originalKmzChainage.format()}) is outside route bounds (${Chainage(minKmz).format()}..${Chainage(maxKmz).format()}).")
            }
        }

        val dupOrig = otherPoints.find {
            abs(it.originalKmzMetres - point.originalKmzMetres) <= 5L ||
                (it.routeDistanceMetres > 0.01 && point.routeDistanceMetres > 0.01 && abs(it.routeDistanceMetres - point.routeDistanceMetres) <= 5.0)
        }
        if (dupOrig != null) {
            warnings.add("Duplicate reference point: another control point already exists near KMZ ${dupOrig.originalKmzChainage.format()}.")
        }

        if (abs(point.offsetMetres) > 10_000L) {
            warnings.add("Large correction shift (${point.displayOffset} jump) exceeds 10 km threshold.")
        }

        val combined = sortPointsInRouteOrder(otherPoints + point, routeData)
        val conflictReport = detectReferenceConflicts(combined, routeData)
        if (conflictReport.hasConflict) {
            warnings.add("Direction conflict: ${conflictReport.conflicts.first()}")
        }
        warnings.addAll(conflictReport.conflicts)
        warnings.addAll(conflictReport.warnings)

        return ValidationResult(
            isValid = !conflictReport.hasConflict,
            warnings = warnings.distinct()
        )
    }

    /**
     * Exports a calibrated KMZ archive to the app's cache directory for sharing.
     */
    suspend fun exportCalibratedKmz(
        context: android.content.Context,
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>
    ): java.io.File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val kmlContent = generateCorrectedKmlContent(routeData, correctionPoints)
        val safeName = routeData.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "calibrated_route" }
        val exportFile = java.io.File(context.cacheDir, "${safeName}_calibrated.kmz")
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(exportFile)).use { zos ->
            val entry = ZipEntry("doc.kml")
            zos.putNextEntry(entry)
            zos.write(kmlContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        exportFile
    }
}

