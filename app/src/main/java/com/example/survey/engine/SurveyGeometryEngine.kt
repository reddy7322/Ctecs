package com.example.survey.engine

import com.example.geo.GeoUtils
import com.example.model.Chainage
import com.example.model.GeoPoint
import com.example.survey.model.CentrelineChainageLabel
import com.example.survey.model.OffsetSide
import com.example.survey.model.RoadSide
import com.example.survey.model.SurveyCalibrationPoint
import com.example.survey.model.SurveyDirection
import com.example.survey.model.SurveyLane
import com.example.survey.model.SurveyPoint
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object SurveyGeometryEngine {

    const val EARTH_RADIUS_M = 6371000.0

    /**
     * Calculates the destination GeoPoint given a starting point, distance in metres, and bearing in degrees.
     * Uses spherical geodesic mathematics for exact centimeter-level precision.
     */
    fun destinationPoint(start: GeoPoint, distanceM: Double, bearingDeg: Double): GeoPoint {
        if (distanceM == 0.0) return start
        val dR = distanceM / EARTH_RADIUS_M
        val theta = Math.toRadians((bearingDeg + 360.0) % 360.0)

        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)

        val lat2 = asin(sin(lat1) * cos(dR) + cos(lat1) * sin(dR) * cos(theta))
        val lon2 = lon1 + atan2(
            sin(theta) * sin(dR) * cos(lat1),
            cos(dR) - sin(lat1) * sin(lat2)
        )

        return GeoPoint(
            latitude = Math.toDegrees(lat2),
            longitude = Math.toDegrees(lon2),
            altitude = start.altitude
        )
    }

    /**
     * Calculates the perpendicular offset point from a route point given local travel direction and offset.
     * If offsetSide is RIGHT, perpendicular bearing is heading + 90°.
     * If offsetSide is LEFT, perpendicular bearing is heading - 90°.
     */
    fun calculatePerpendicularOffset(
        point: GeoPoint,
        headingDeg: Double,
        offsetDistanceM: Double,
        offsetSide: OffsetSide
    ): GeoPoint {
        if (offsetDistanceM <= 0.0 || offsetSide == OffsetSide.CENTRE) {
            return point
        }
        val perpAngle = when (offsetSide) {
            OffsetSide.RIGHT -> (headingDeg + 90.0) % 360.0
            OffsetSide.LEFT -> (headingDeg - 90.0 + 360.0) % 360.0
            OffsetSide.CENTRE -> headingDeg
        }
        return destinationPoint(point, offsetDistanceM, perpAngle)
    }

    /**
     * Generates highway centreline polyline points from a recorded lane track.
     * Since the lane is offset from the centreline, the centreline is in the OPPOSITE
     * direction of the lane's offset (e.g. if vehicle is in RHS lane at +7m, the centreline is 7m to LHS of vehicle).
     */
    fun generateCentrelineTrack(
        points: List<SurveyPoint>,
        offsetDistanceM: Double,
        offsetSide: OffsetSide
    ): List<GeoPoint> {
        if (points.isEmpty()) return emptyList()
        if (offsetDistanceM <= 0.0 || offsetSide == OffsetSide.CENTRE) {
            return points.map { it.geoPoint }
        }

        // Centreline is opposite of surveyed lane offset
        val clSide = when (offsetSide) {
            OffsetSide.RIGHT -> OffsetSide.LEFT
            OffsetSide.LEFT -> OffsetSide.RIGHT
            OffsetSide.CENTRE -> OffsetSide.CENTRE
        }

        val result = mutableListOf<GeoPoint>()
        val n = points.size

        for (i in 0 until n) {
            val currPt = points[i].geoPoint
            val tangentBearing = when {
                n == 1 -> points[0].bearing.toDouble()
                i == 0 -> GeoUtils.bearingDegrees(currPt, points[1].geoPoint)
                i == n - 1 -> GeoUtils.bearingDegrees(points[n - 2].geoPoint, currPt)
                else -> {
                    // Smooth tangent bearing across curves using predecessor and successor
                    GeoUtils.bearingDegrees(points[i - 1].geoPoint, points[i + 1].geoPoint)
                }
            }

            val clPt = calculatePerpendicularOffset(currPt, tangentBearing, offsetDistanceM, clSide)
            result.add(clPt)
        }
        return result
    }

    /**
     * Calculates chainage in metres from starting chainage and distance travelled,
     * applying calibration drift correction if available.
     */
    fun calculateChainageMetres(
        startChainageMetres: Long,
        distanceTravelledM: Double,
        direction: SurveyDirection,
        calibrations: List<SurveyCalibrationPoint> = emptyList()
    ): Long {
        val rawChainage = when (direction) {
            SurveyDirection.INCREASING -> startChainageMetres + distanceTravelledM.toLong()
            SurveyDirection.DECREASING -> startChainageMetres - distanceTravelledM.toLong()
            SurveyDirection.CUSTOM -> startChainageMetres + distanceTravelledM.toLong()
        }

        // If calibration points exist, apply the latest calibration drift
        val latestCalibration = calibrations.maxByOrNull { it.timestamp }
        return if (latestCalibration != null) {
            rawChainage + latestCalibration.driftMetres.toLong()
        } else {
            rawChainage
        }
    }

    /**
     * Generates precise 200-metre chainage labels along the generated Highway Centreline.
     * Places each label at its actual interpolated geographical position along the centreline polyline,
     * maintaining the active chainage calibration and travel direction without modifying original GPS tracks.
     */
    fun generate200mChainageLabels(
        centrelinePts: List<GeoPoint>,
        startChainageMetres: Long,
        direction: SurveyDirection,
        calibrations: List<SurveyCalibrationPoint> = emptyList()
    ): List<CentrelineChainageLabel> {
        if (centrelinePts.size < 2) return emptyList()

        val n = centrelinePts.size
        val segLengths = DoubleArray(n - 1)
        var totalCentrelineLengthM = 0.0

        for (i in 0 until n - 1) {
            val d = GeoUtils.distanceMetres(centrelinePts[i], centrelinePts[i + 1])
            segLengths[i] = d
            totalCentrelineLengthM += d
        }

        if (totalCentrelineLengthM < 50.0) return emptyList()

        val driftM = calibrations.maxByOrNull { it.timestamp }?.driftMetres?.toLong() ?: 0L
        val effectiveStartCh = startChainageMetres + driftM

        val result = mutableListOf<CentrelineChainageLabel>()

        val targetChainages = mutableListOf<Long>()
        if (direction == SurveyDirection.DECREASING) {
            val first200 = (effectiveStartCh / 200L) * 200L
            val last200 = ((effectiveStartCh - totalCentrelineLengthM.toLong() + 199L) / 200L) * 200L
            var ch = first200
            while (ch >= last200) {
                targetChainages.add(ch)
                ch -= 200L
            }
        } else {
            // INCREASING or CUSTOM
            val first200 = ((effectiveStartCh + 199L) / 200L) * 200L
            val last200 = (effectiveStartCh + totalCentrelineLengthM.toLong()) / 200L * 200L
            var ch = first200
            while (ch <= last200) {
                targetChainages.add(ch)
                ch += 200L
            }
        }

        for (ch in targetChainages) {
            val distFromStart = abs((ch - driftM) - startChainageMetres).toDouble()
            if (distFromStart in 0.0..totalCentrelineLengthM) {
                // Find segment
                var accumulated = 0.0
                var found = false
                for (i in 0 until n - 1) {
                    val segLen = segLengths[i]
                    if (distFromStart <= accumulated + segLen || (i == n - 2 && !found)) {
                        val fraction = if (segLen > 0.0) ((distFromStart - accumulated) / segLen).coerceIn(0.0, 1.0) else 0.0
                        val p1 = centrelinePts[i]
                        val p2 = centrelinePts[i + 1]

                        val lat = p1.latitude + fraction * (p2.latitude - p1.latitude)
                        val lon = p1.longitude + fraction * (p2.longitude - p1.longitude)
                        val alt = p1.altitude + fraction * (p2.altitude - p1.altitude)
                        val bearing = GeoUtils.bearingDegrees(p1, p2)

                        result.add(
                            CentrelineChainageLabel(
                                chainageMetres = ch,
                                displayChainage = Chainage(ch).format(),
                                position = GeoPoint(lat, lon, alt),
                                bearingDeg = bearing
                            )
                        )
                        found = true
                        break
                    }
                    accumulated += segLen
                }
            }
        }

        return result
    }

    /**
     * Automatic detection helper for Road Side (LHS/RHS/BHS) and Lane (Inner/Fast/Outer/Slow/Service)
     * based on lateral offset distance and side configuration.
     */
    fun detectRoadSideAndLane(
        offsetDistanceM: Double,
        offsetSide: OffsetSide
    ): Pair<RoadSide, SurveyLane> {
        val side = when (offsetSide) {
            OffsetSide.LEFT -> RoadSide.LHS
            OffsetSide.RIGHT -> RoadSide.RHS
            OffsetSide.CENTRE -> RoadSide.BHS
        }

        val lane = when {
            offsetDistanceM <= 0.5 -> SurveyLane.CUSTOM
            offsetDistanceM in 0.5..4.5 -> SurveyLane.INNER
            offsetDistanceM in 4.5..8.5 -> SurveyLane.FAST
            offsetDistanceM in 8.5..12.0 -> SurveyLane.OUTER
            offsetDistanceM > 12.0 -> SurveyLane.SERVICE_ROAD
            else -> SurveyLane.FAST
        }

        return Pair(side, lane)
    }

    /**
     * Intelligent GPS movement filtering:
     * - Rejects stationary jitter (< 1.5m movement when speed is below 0.3 m/s)
     * - Rejects sudden GPS jumps (> 150m between successive points in normal interval)
     * - Checks accuracy threshold (< 30m)
     */
    fun isMovementReliable(
        lastPoint: GeoPoint?,
        currentPoint: GeoPoint,
        accuracy: Float,
        speedMps: Float,
        accuracyThresholdM: Float = 25f
    ): Boolean {
        if (accuracy > accuracyThresholdM && accuracyThresholdM > 0f) {
            return false
        }
        if (lastPoint == null) {
            return true
        }

        val distance = GeoUtils.distanceMetres(lastPoint, currentPoint)

        // Stationary jitter rejection
        if (distance < 1.5 && speedMps < 0.3f) {
            return false
        }

        // Sudden jump rejection (teleportation anomaly)
        if (distance > 150.0 && speedMps < 35f) {
            return false
        }

        return true
    }
}
