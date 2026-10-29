package com.example.overlay

import com.example.model.CameraMode
import com.example.model.CoordinateFormat
import com.example.model.OverlayConfig
import com.example.model.OverlayTemplate
import com.example.model.ProjectProfile
import com.example.model.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object OverlayTextGenerator {

    fun generateLines(
        config: OverlayConfig,
        project: ProjectProfile,
        location: GeoPoint?,
        accuracy: Float,
        compassHeading: String,
        speedMps: Float?,
        bearing: Float?,
        chainageStr: String,
        cameraMode: CameraMode,
        coordinateFormat: CoordinateFormat,
        is24HourFormat: Boolean,
        timestamp: Long = System.currentTimeMillis(),
        isForCapture: Boolean = false
    ): List<String> {
        val lines = mutableListOf<String>()

        when (config.template) {
            OverlayTemplate.GPS_COORDINATES_ONLY -> {
                if (config.showTimestamp) {
                    val pattern = if (is24HourFormat) "dd MMMM yyyy HH:mm" else config.timestampFormat
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    lines.add(sdf.format(Date(timestamp)))
                }
                if (config.showCoordinates && location != null) {
                    lines.add(location.formatReference(config.coordinatePrecision))
                }
            }
            OverlayTemplate.MINIMAL_TIMESTAMP -> {
                if (config.showTimestamp) {
                    val pattern = if (is24HourFormat) "dd MMMM yyyy HH:mm" else config.timestampFormat
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    lines.add(sdf.format(Date(timestamp)))
                }
            }
            else -> {
                val timestampStr = if (config.showTimestamp) {
                    val pattern = if (is24HourFormat) "dd MMMM yyyy HH:mm" else config.timestampFormat
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    sdf.format(Date(timestamp))
                } else null

                val isTimeAbove = config.timePosition.equals("Time in above", ignoreCase = true)

                // 1. Timestamp (if above)
                if (isTimeAbove && timestampStr != null) {
                    lines.add(timestampStr)
                }

                // 2. Coordinates, Altitude, Accuracy
                if (config.showCoordinates && location != null) {
                    val coordText = when (coordinateFormat) {
                        CoordinateFormat.DMS -> {
                            val latDms = toDmsStatic(location.latitude, true)
                            val lngDms = toDmsStatic(location.longitude, false)
                            "$latDms $lngDms"
                        }
                        else -> {
                            String.format(Locale.US, "%.4f°N %.4f°E", location.latitude, location.longitude)
                        }
                    }
                    var coordLine = coordText
                    if (config.showAltitude && location.altitude != 0.0) {
                        coordLine += " Alt: ${location.altitude.toInt()}m"
                    }
                    if (config.showAccuracy && accuracy > 0f) {
                        val accMargin = if (config.gpsAccuracyMarginPreset != "Off") " (${config.gpsAccuracyMarginPreset})" else " (±${accuracy.toInt()}m)"
                        coordLine += accMargin
                    }
                    lines.add(coordLine)
                }

                // 3. Location / Address breakdown
                val displayAddr = if (config.customAddressText.isNotBlank()) {
                    config.customAddressText
                } else {
                    config.locationAddressOption
                }
                if (displayAddr.isNotBlank() && !displayAddr.equals("None", ignoreCase = true) && !displayAddr.equals("Null", ignoreCase = true)) {
                    lines.add(displayAddr.trim())
                }

                // 4. Compass
                if (config.showCompass && compassHeading.isNotBlank() && compassHeading != "---") {
                    lines.add("Compass: $compassHeading")
                }

                // 5. Speed & Direction (STRICTLY governed by user settings; OFF by default for preview & capture)
                val shouldShowSpeed = if (isForCapture) config.includeSpeedInCapture else config.showSpeedOnPreview
                val shouldShowDirection = if (isForCapture) config.includeDirectionInCapture else config.showDirectionOnPreview

                val speedValid = speedMps != null && speedMps >= 0f
                val bearingValid = bearing != null

                val speedStr = if (shouldShowSpeed && speedValid) {
                    val speedKmh = speedMps!! * 3.6f
                    String.format(Locale.US, "Speed: %.1f km/h", speedKmh)
                } else null

                val directionStr = if (shouldShowDirection && bearingValid) {
                    val b = bearing!!
                    val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
                    val index = (((b + 11.25) % 360) / 22.5).toInt()
                    String.format(Locale.US, "Dir: %.0f° %s", b, directions[index % 16])
                } else null

                val telemetryParts = listOfNotNull(speedStr, directionStr)
                if (telemetryParts.isNotEmpty()) {
                    lines.add(telemetryParts.joinToString("  |  "))
                }

                // 6. Project Info / Chainage / Activity / Lane / Side
                if ((config.showProjectInfo || config.showChainage) && config.showIndexNumber) {
                    val highwayLine = project.buildHighwayText(if (config.showChainage) chainageStr else "")
                    if (highwayLine.isNotBlank()) {
                        lines.add(highwayLine)
                    }
                }

                // 7. Timestamp (if below)
                if (!isTimeAbove && timestampStr != null) {
                    lines.add(timestampStr)
                }
            }
        }

        return lines.filter { it.isNotBlank() }
    }

    private fun toDmsStatic(coord: Double, isLat: Boolean): String {
        val abs = abs(coord)
        val degrees = abs.toInt()
        val minutes = ((abs - degrees) * 60.0).toInt()
        val seconds = (((abs - degrees) * 60.0 - minutes) * 60.0).toInt()
        val ref = if (isLat) {
            if (coord >= 0) "N" else "S"
        } else {
            if (coord >= 0) "E" else "W"
        }
        return String.format(Locale.US, "%d°%d'%d\"%s", degrees, minutes, seconds, ref)
    }
}
