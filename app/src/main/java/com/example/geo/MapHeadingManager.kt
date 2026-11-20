package com.example.geo

import com.example.model.GeoPoint
import com.example.model.RouteData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Distinct operational states for the centralized Map Heading engine.
 * Kept strictly separate from the authoritative LHS/RHS road-direction engine.
 */
enum class MapHeadingStatus {
    RELIABLE,
    UNCERTAIN,
    STATIONARY,
    WEAK_GPS,
    RECOVERING,
    UNAVAILABLE
}

/**
 * Source that produced the current active map heading.
 */
enum class MapHeadingSource {
    GPS_MOVEMENT,
    GPS_DISPLACEMENT,
    ALIGNMENT_TANGENT,
    RETAINED_LAST_RELIABLE,
    NONE
}

/**
 * Immutable snapshot exposed to every Mini Map across CTECS (Home, Screensaver, Camera, Survey, Routes).
 */
data class MapHeadingState(
    val headingDegrees: Float? = null,
    val lastReliableHeadingDegrees: Float? = null,
    val status: MapHeadingStatus = MapHeadingStatus.UNAVAILABLE,
    val source: MapHeadingSource = MapHeadingSource.NONE,
    val confidence: Float = 0f,
    val timestampMs: Long = 0L
) {
    /**
     * True only when no reliable heading has been established and no valid fallback exists.
     */
    val isDirectionUnavailable: Boolean
        get() = status == MapHeadingStatus.UNAVAILABLE || headingDegrees == null

    /**
     * Compact non-intrusive status label shown only when heading is unavailable or weak.
     */
    val statusBadgeLabel: String?
        get() = when (status) {
            MapHeadingStatus.UNAVAILABLE -> "Heading unavailable"
            MapHeadingStatus.WEAK_GPS -> if (headingDegrees == null) "Heading unavailable" else null
            else -> null
        }
}

/**
 * Single authoritative centralized Map Heading Manager shared across the entire CTECS app.
 *
 * Responsibilities:
 * - Determines smooth, reliable Direction-Up map orientation from GPS movement bearing and
 *   consecutive GPS displacement vectors.
 * - Filters noisy bearings, rejects stationary GPS drift, and ignores magnetic compass
 *   interference inside vehicles while moving or stationary.
 * - Retains the last reliable heading whenever stationary, uncertain, or during weak GPS
 *   so maps NEVER snap back or auto-switch to North-Up.
 * - Smoothly recovers via [MapHeadingStatus.RECOVERING] -> [MapHeadingStatus.RELIABLE] when
 *   consistent movement resumes.
 * - Uses road alignment tangent as a valid initial fallback when positioned on an active KMZ/KML
 *   alignment prior to first movement, or reports [MapHeadingStatus.UNAVAILABLE] instead of
 *   inventing an artificial heading.
 * - Strictly separate from LHS/RHS road-travel direction logic.
 */
class MapHeadingManager {

    private val lock = Any()

    private val _headingState = MutableStateFlow(MapHeadingState())
    val headingState: StateFlow<MapHeadingState> = _headingState.asStateFlow()

    private var anchorPoint: GeoPoint? = null
    private var lastSampleTimestampMs: Long = 0L
    private var smoothedHeadingDeg: Float? = null
    private var lastReliableHeadingDeg: Float? = null
    private var lastPendingCandidateDeg: Float? = null
    private var consistentRecoveryCount: Int = 0

    companion object {
        const val WEAK_GPS_ACCURACY_M = 35f
        const val STATIONARY_SPEED_MPS = 0.85f
        const val MIN_RELIABLE_SPEED_MPS = 1.2f
        const val MIN_DISPLACEMENT_M = 4.5
        const val DRIFT_REJECT_M = 5.0
        const val ALIGNMENT_FALLBACK_MAX_OFFSET_M = 80.0
        const val CONFLICT_THRESHOLD_DEG = 65f
        const val SUDDEN_SPIKE_DEG = 95f
        const val MICRO_JITTER_DEADBAND_DEG = 2.0f
        const val MAX_ANGULAR_STEP_DEG = 28f
        const val MAX_SPIKE_WINDOW_MS = 1_800L

        /** Normalizes any angle in degrees into `[0f, 360f)`. */
        fun normalizeAngle(deg: Float): Float {
            if (!deg.isFinite()) return 0f
            val m = deg % 360f
            return if (m < 0f) m + 360f else m
        }

        /** Computes signed shortest angular difference `toDeg - fromDeg` in `(-180f, 180f]`. */
        fun shortestAngleDelta(fromDeg: Float, toDeg: Float): Float {
            var d = (normalizeAngle(toDeg) - normalizeAngle(fromDeg)) % 360f
            if (d > 180f) d -= 360f
            if (d <= -180f) d += 360f
            return d
        }

        /** Circular weighted mean of two angles in degrees. */
        fun circularWeightedMean(angleA: Float, weightA: Float, angleB: Float, weightB: Float): Float {
            val radA = Math.toRadians(normalizeAngle(angleA).toDouble())
            val radB = Math.toRadians(normalizeAngle(angleB).toDouble())
            val x = cos(radA) * weightA + cos(radB) * weightB
            val y = sin(radA) * weightA + sin(radB) * weightB
            if (abs(x) < 1e-6 && abs(y) < 1e-6) return normalizeAngle(angleA)
            val deg = Math.toDegrees(atan2(y, x)).toFloat()
            return normalizeAngle(deg)
        }

        /**
         * Smooth circular low-pass filter with deadband and per-update angular rate clamping.
         */
        fun blendAngles(
            currentDeg: Float,
            targetDeg: Float,
            alpha: Float,
            maxStepDeg: Float = MAX_ANGULAR_STEP_DEG
        ): Float {
            val delta = shortestAngleDelta(currentDeg, targetDeg)
            if (abs(delta) < MICRO_JITTER_DEADBAND_DEG) {
                return normalizeAngle(currentDeg)
            }
            val rawStep = delta * alpha.coerceIn(0.05f, 1.0f)
            val clampedStep = rawStep.coerceIn(-maxStepDeg, maxStepDeg)
            return normalizeAngle(currentDeg + clampedStep)
        }

        /**
         * Computes nearest KMZ/KML alignment tangent bearing (0..360°) when within [maxOffsetM].
         */
        fun computeAlignmentTangentBearing(
            location: GeoPoint,
            routeData: RouteData?,
            maxOffsetM: Double = ALIGNMENT_FALLBACK_MAX_OFFSET_M
        ): Float? {
            if (routeData == null || routeData.alignments.isEmpty()) return null
            var bestDist = Double.MAX_VALUE
            var bestBearing: Float? = null

            for (alignment in routeData.alignments) {
                val pts = alignment.points
                if (pts.size < 2) continue
                for (i in 0 until pts.size - 1) {
                    val a = pts[i]
                    val b = pts[i + 1]
                    val mid = GeoPoint((a.latitude + b.latitude) * 0.5, (a.longitude + b.longitude) * 0.5)
                    val d = GeoUtils.distanceMetres(location, mid)
                    if (d < bestDist) {
                        bestDist = d
                        bestBearing = normalizeAngle(GeoUtils.bearingDegrees(a, b).toFloat())
                    }
                }
            }
            return if (bestDist <= maxOffsetM) bestBearing else null
        }
    }

    /**
     * Feeds a new GPS location update into the centralized map heading engine.
     * Magnetic compass readings are intentionally NOT accepted here so cabin magnetic interference
     * can never corrupt moving or stationary Direction-Up map orientation.
     */
    fun processGpsSample(
        point: GeoPoint?,
        speedMps: Float?,
        gpsBearingDeg: Float?,
        accuracyMetres: Float?,
        routeData: RouteData? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): MapHeadingState = synchronized(lock) {
        val currentState = _headingState.value
        val dtMs = if (lastSampleTimestampMs > 0L) (timestampMs - lastSampleTimestampMs).coerceAtLeast(0L) else 0L

        // 1. Weak or missing GPS signal -> retain last reliable heading, NEVER reset to North-Up
        val isWeakGps = point == null ||
            accuracyMetres == null ||
            !accuracyMetres.isFinite() ||
            accuracyMetres <= 0f ||
            accuracyMetres > WEAK_GPS_ACCURACY_M

        if (isWeakGps) {
            consistentRecoveryCount = 0
            lastPendingCandidateDeg = null
            lastSampleTimestampMs = timestampMs
            val retained = lastReliableHeadingDeg
            val alignmentFallback = if (retained == null && point != null) {
                computeAlignmentTangentBearing(point, routeData)
            } else {
                null
            }
            val next = when {
                retained != null -> MapHeadingState(
                    headingDegrees = retained,
                    lastReliableHeadingDegrees = retained,
                    status = MapHeadingStatus.WEAK_GPS,
                    source = MapHeadingSource.RETAINED_LAST_RELIABLE,
                    confidence = 0.25f,
                    timestampMs = timestampMs
                )
                alignmentFallback != null -> MapHeadingState(
                    headingDegrees = alignmentFallback,
                    lastReliableHeadingDegrees = null,
                    status = MapHeadingStatus.WEAK_GPS,
                    source = MapHeadingSource.ALIGNMENT_TANGENT,
                    confidence = 0.30f,
                    timestampMs = timestampMs
                )
                else -> MapHeadingState(
                    headingDegrees = null,
                    lastReliableHeadingDegrees = null,
                    status = MapHeadingStatus.UNAVAILABLE,
                    source = MapHeadingSource.NONE,
                    confidence = 0f,
                    timestampMs = timestampMs
                )
            }
            _headingState.value = next
            return next
        }

        val validPoint = point!!
        val validAcc = accuracyMetres!!
        val validSpeed = speedMps?.takeIf { it.isFinite() && it >= 0f }
        val validGpsBearing = gpsBearingDeg?.takeIf { it.isFinite() }?.let { normalizeAngle(it) }

        // 2. Compute displacement vector relative to anchor point (filtered against GPS drift)
        val prevAnchor = anchorPoint
        var displacementMetres = 0.0
        var displacementBearing: Float? = null
        if (prevAnchor == null) {
            anchorPoint = validPoint
        } else {
            displacementMetres = GeoUtils.distanceMetres(prevAnchor, validPoint)
            val driftGate = maxOf(MIN_DISPLACEMENT_M, (validAcc * 0.45).coerceAtMost(14.0))
            if (displacementMetres >= driftGate) {
                displacementBearing = normalizeAngle(GeoUtils.bearingDegrees(prevAnchor, validPoint).toFloat())
                anchorPoint = validPoint
            }
        }

        // 3. Stationary detection (low speed + negligible displacement) -> retain last reliable heading
        val speedIndicatesStationary = validSpeed != null && validSpeed < STATIONARY_SPEED_MPS
        val displacementIndicatesStationary = displacementBearing == null && displacementMetres < DRIFT_REJECT_M
        val noMotionEvidence = (speedIndicatesStationary && displacementIndicatesStationary) ||
            (validSpeed == null && displacementBearing == null && validGpsBearing == null)

        if (noMotionEvidence) {
            consistentRecoveryCount = 0
            lastPendingCandidateDeg = null
            lastSampleTimestampMs = timestampMs
            val retained = lastReliableHeadingDeg
            val next = if (retained != null) {
                MapHeadingState(
                    headingDegrees = retained,
                    lastReliableHeadingDegrees = retained,
                    status = MapHeadingStatus.STATIONARY,
                    source = MapHeadingSource.RETAINED_LAST_RELIABLE,
                    confidence = 0.75f,
                    timestampMs = timestampMs
                )
            } else {
                val alignmentFallback = computeAlignmentTangentBearing(validPoint, routeData)
                if (alignmentFallback != null) {
                    smoothedHeadingDeg = alignmentFallback
                    MapHeadingState(
                        headingDegrees = alignmentFallback,
                        lastReliableHeadingDegrees = null,
                        status = MapHeadingStatus.STATIONARY,
                        source = MapHeadingSource.ALIGNMENT_TANGENT,
                        confidence = 0.50f,
                        timestampMs = timestampMs
                    )
                } else {
                    MapHeadingState(
                        headingDegrees = null,
                        lastReliableHeadingDegrees = null,
                        status = MapHeadingStatus.UNAVAILABLE,
                        source = MapHeadingSource.NONE,
                        confidence = 0f,
                        timestampMs = timestampMs
                    )
                }
            }
            _headingState.value = next
            return next
        }

        // 4. Determine candidate travel heading from GPS movement & displacement
        val hasStrongSpeed = validSpeed != null && validSpeed >= MIN_RELIABLE_SPEED_MPS
        val usableGpsBearing = if (validGpsBearing != null && (hasStrongSpeed || validSpeed == null)) {
            validGpsBearing
        } else {
            null
        }

        // Check conflict between raw GPS bearing and actual geographic displacement vector
        if (usableGpsBearing != null && displacementBearing != null) {
            val conflictDeg = abs(shortestAngleDelta(usableGpsBearing, displacementBearing))
            if (conflictDeg > CONFLICT_THRESHOLD_DEG) {
                // Conflicting measurements -> do not jerk map; hold last reliable heading and mark UNCERTAIN
                lastSampleTimestampMs = timestampMs
                consistentRecoveryCount = 0
                lastPendingCandidateDeg = displacementBearing
                val held = lastReliableHeadingDeg ?: smoothedHeadingDeg
                val next = if (held != null) {
                    MapHeadingState(
                        headingDegrees = held,
                        lastReliableHeadingDegrees = lastReliableHeadingDeg,
                        status = MapHeadingStatus.UNCERTAIN,
                        source = MapHeadingSource.RETAINED_LAST_RELIABLE,
                        confidence = 0.30f,
                        timestampMs = timestampMs
                    )
                } else {
                    MapHeadingState(
                        headingDegrees = null,
                        lastReliableHeadingDegrees = null,
                        status = MapHeadingStatus.UNAVAILABLE,
                        source = MapHeadingSource.NONE,
                        confidence = 0f,
                        timestampMs = timestampMs
                    )
                }
                _headingState.value = next
                return next
            }
        }

        val candidateAndSource: Pair<Float, MapHeadingSource>? = when {
            usableGpsBearing != null && displacementBearing != null -> {
                val gpsWeight = if ((validSpeed ?: 0f) >= 2.5f) 0.72f else 0.55f
                val fused = circularWeightedMean(usableGpsBearing, gpsWeight, displacementBearing, 1f - gpsWeight)
                fused to MapHeadingSource.GPS_MOVEMENT
            }
            usableGpsBearing != null -> usableGpsBearing to MapHeadingSource.GPS_MOVEMENT
            displacementBearing != null -> displacementBearing to MapHeadingSource.GPS_DISPLACEMENT
            else -> null
        }

        if (candidateAndSource == null) {
            lastSampleTimestampMs = timestampMs
            val held = lastReliableHeadingDeg ?: smoothedHeadingDeg
            val next = if (held != null) {
                MapHeadingState(
                    headingDegrees = held,
                    lastReliableHeadingDegrees = lastReliableHeadingDeg,
                    status = MapHeadingStatus.STATIONARY,
                    source = MapHeadingSource.RETAINED_LAST_RELIABLE,
                    confidence = 0.65f,
                    timestampMs = timestampMs
                )
            } else {
                val alignmentFallback = computeAlignmentTangentBearing(validPoint, routeData)
                if (alignmentFallback != null) {
                    MapHeadingState(
                        headingDegrees = alignmentFallback,
                        lastReliableHeadingDegrees = null,
                        status = MapHeadingStatus.STATIONARY,
                        source = MapHeadingSource.ALIGNMENT_TANGENT,
                        confidence = 0.45f,
                        timestampMs = timestampMs
                    )
                } else {
                    MapHeadingState(
                        headingDegrees = null,
                        lastReliableHeadingDegrees = null,
                        status = MapHeadingStatus.UNAVAILABLE,
                        source = MapHeadingSource.NONE,
                        confidence = 0f,
                        timestampMs = timestampMs
                    )
                }
            }
            _headingState.value = next
            return next
        }

        val (candidateDeg, candidateSource) = candidateAndSource

        // 5. Reject single-sample sudden angular spikes (multipath / urban canyon jump)
        val existingReliable = lastReliableHeadingDeg
        if (existingReliable != null && dtMs in 1L..MAX_SPIKE_WINDOW_MS) {
            val jumpDeg = abs(shortestAngleDelta(existingReliable, candidateDeg))
            if (jumpDeg > SUDDEN_SPIKE_DEG) {
                val pending = lastPendingCandidateDeg
                val agreesWithPending = pending != null && abs(shortestAngleDelta(pending, candidateDeg)) <= 35f
                if (!agreesWithPending) {
                    // First spike sample -> mark UNCERTAIN and retain last reliable heading
                    lastPendingCandidateDeg = candidateDeg
                    consistentRecoveryCount = 0
                    lastSampleTimestampMs = timestampMs
                    val next = MapHeadingState(
                        headingDegrees = existingReliable,
                        lastReliableHeadingDegrees = existingReliable,
                        status = MapHeadingStatus.UNCERTAIN,
                        source = MapHeadingSource.RETAINED_LAST_RELIABLE,
                        confidence = 0.35f,
                        timestampMs = timestampMs
                    )
                    _headingState.value = next
                    return next
                }
            }
        }

        lastPendingCandidateDeg = null
        lastSampleTimestampMs = timestampMs

        // 6. Smooth circular filtering & recovery state progression
        val wasDegraded = currentState.status == MapHeadingStatus.WEAK_GPS ||
            currentState.status == MapHeadingStatus.UNCERTAIN ||
            currentState.status == MapHeadingStatus.UNAVAILABLE

        val speedFactor = ((validSpeed ?: 3f) / 10f).coerceIn(0f, 1f)
        val alpha = 0.30f + speedFactor * 0.22f // 0.30 at slow speed, up to 0.52 at highway speed

        val previousHeading = smoothedHeadingDeg
        val newSmoothed = if (previousHeading == null) {
            candidateDeg
        } else {
            val maxStep = if (wasDegraded) 45f else MAX_ANGULAR_STEP_DEG
            blendAngles(previousHeading, candidateDeg, alpha = alpha, maxStepDeg = maxStep)
        }

        smoothedHeadingDeg = newSmoothed
        lastReliableHeadingDeg = newSmoothed

        val nextStatus = when {
            wasDegraded -> {
                consistentRecoveryCount = 1
                MapHeadingStatus.RECOVERING
            }
            currentState.status == MapHeadingStatus.RECOVERING -> {
                consistentRecoveryCount += 1
                if (consistentRecoveryCount >= 2) MapHeadingStatus.RELIABLE else MapHeadingStatus.RECOVERING
            }
            else -> {
                consistentRecoveryCount = 2
                MapHeadingStatus.RELIABLE
            }
        }

        val accuracyConfidence = (1f - (validAcc / WEAK_GPS_ACCURACY_M)).coerceIn(0.35f, 1.0f)
        val finalConfidence = if (nextStatus == MapHeadingStatus.RECOVERING) {
            (accuracyConfidence * 0.7f).coerceIn(0.4f, 0.8f)
        } else {
            accuracyConfidence
        }

        val next = MapHeadingState(
            headingDegrees = newSmoothed,
            lastReliableHeadingDegrees = newSmoothed,
            status = nextStatus,
            source = candidateSource,
            confidence = finalConfidence,
            timestampMs = timestampMs
        )
        _headingState.value = next
        return next
    }

    /**
     * Resets internal state (primarily for tests or explicit session reset).
     */
    fun reset() = synchronized(lock) {
        anchorPoint = null
        lastSampleTimestampMs = 0L
        smoothedHeadingDeg = null
        lastReliableHeadingDeg = null
        lastPendingCandidateDeg = null
        consistentRecoveryCount = 0
        _headingState.value = MapHeadingState()
    }
}
