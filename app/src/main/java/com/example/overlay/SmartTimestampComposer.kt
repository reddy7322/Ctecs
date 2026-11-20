package com.example.overlay

import com.example.model.CoordinateFormat
import com.example.model.GeoPoint
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.WeatherDisplayFormat
import com.example.model.WeatherObservation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Centralized Smart Dynamic Timestamp Composer for CTECS.
 *
 * When Weather Information is enabled (`config.showWeatherInfo == true`), composes the clean,
 * structured 4-line highway inspection timestamp from actual capture-time telemetry:
 *
 * Line 1 – Capture Date and Time:       20 November 2026 3:51 AM
 * Line 2 – GPS Coordinates:             29.9166°N 78.0523°E
 * Line 3 – Location and Weather:        Bahadarabad, Uttarakhand | 29°C
 * Line 4 – Highway Project Information: MH | MM | 190+675 MCW BC
 *
 * Strict guarantees:
 * - Dynamically populates every value from capture-time GPS, weather observation, location name,
 *   calibrated chainage, authoritative LHS/RHS direction, and project profile.
 * - Never fabricates fake or default weather/GPS values when data is missing or stale.
 * - Prevents duplicate GPS, weather, location, project, or chainage lines.
 * - Independently honors every Weather Information toggle and Compact vs Detailed display format.
 */
object SmartTimestampComposer {

    fun formatDateTimeLine(
        timestampMs: Long,
        timestampFormat: String,
        is24HourFormat: Boolean,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val pattern = if (is24HourFormat) {
            to24HourPattern(timestampFormat)
        } else {
            timestampFormat.ifBlank { "dd MMMM yyyy h:mm a" }
        }
        return try {
            SimpleDateFormat(pattern, Locale.US).apply {
                this.timeZone = timeZone
            }.format(Date(timestampMs))
        } catch (_: Exception) {
            SimpleDateFormat(
                if (is24HourFormat) "dd MMMM yyyy HH:mm" else "dd MMMM yyyy h:mm a",
                Locale.US
            ).apply {
                this.timeZone = timeZone
            }.format(Date(timestampMs))
        }
    }

    /**
     * Converts a 12-hour format pattern cleanly to 24-hour if the user enabled 24-Hour Time Format,
     * while preserving custom date styles (such as "dd MMMM yyyy", "dd-MM-yyyy", "yyyy-MM-dd").
     */
    internal fun to24HourPattern(rawPattern: String): String {
        val trimmed = rawPattern.trim()
        if (trimmed.isEmpty()) return "dd MMMM yyyy HH:mm"
        if (trimmed.contains("HH")) return trimmed
        return trimmed
            .replace("hh", "HH")
            .replace("h", "HH")
            .replace(Regex("\\s*a\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Formats GPS coordinates with configurable decimal precision (4..7) and accurate N/S & E/W
     * directional notation (e.g., "29.9166°N 78.0523°E" or "33.8688°S 151.2093°E").
     */
    fun formatCoordinatesLine(
        location: GeoPoint,
        precision: Int = 4,
        coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL
    ): String {
        val safePrecision = precision.coerceIn(4, 7)
        return when (coordinateFormat) {
            CoordinateFormat.DMS -> {
                "${toDms(location.latitude, isLat = true)} ${toDms(location.longitude, isLat = false)}"
            }
            else -> {
                val latDir = if (location.latitude >= 0.0) "N" else "S"
                val lonDir = if (location.longitude >= 0.0) "E" else "W"
                val fmt = "%.${safePrecision}f°%s %.${safePrecision}f°%s"
                String.format(
                    Locale.US,
                    fmt,
                    abs(location.latitude),
                    latDir,
                    abs(location.longitude),
                    lonDir
                )
            }
        }
    }

    /**
     * Composes Line 3 (and optional Line 3b in Detailed mode when humidity/wind are enabled)
     * combining resolved location name and capture-time weather without inventing missing data.
     */
    fun formatLocationAndWeatherLines(
        config: OverlayConfig,
        locationName: String,
        weatherObservation: WeatherObservation?,
        captureTimestampMs: Long
    ): List<String> {
        val cleanLocation = if (config.weatherShowLocationName && config.showLocationName) {
            locationName.trim().takeIf {
                it.isNotBlank() &&
                    !it.equals("None", ignoreCase = true) &&
                    !it.equals("Null", ignoreCase = true)
            }
        } else {
            null
        }

        val validWeather = weatherObservation?.takeIf { obs ->
            if (config.weatherOmitIfStale) {
                obs.isFreshAt(captureTimestampMs, WeatherObservation.DEFAULT_CAPTURE_MAX_AGE_MS)
            } else {
                obs.observedAtMs > 0L
            }
        }

        val tempToken = if (config.weatherShowTemperature && validWeather != null) {
            validWeather.formatTemperature()
        } else null

        val condToken = if (config.weatherShowCondition && validWeather != null) {
            validWeather.conditionLabel.trim().takeIf { it.isNotBlank() }
        } else null

        val humidityToken = if (config.weatherShowHumidity && validWeather != null) {
            if (config.weatherDisplayFormat == WeatherDisplayFormat.DETAILED) {
                "Humidity ${validWeather.formatHumidity()}"
            } else {
                "RH ${validWeather.formatHumidity()}"
            }
        } else null

        val windToken = if (validWeather != null && (config.weatherShowWindSpeed || config.weatherShowWindDirection)) {
            val parts = buildList {
                if (config.weatherShowWindSpeed) add(validWeather.formatWindSpeed())
                if (config.weatherShowWindDirection) add(validWeather.windDirectionCompass)
            }
            if (parts.isNotEmpty()) {
                if (config.weatherDisplayFormat == WeatherDisplayFormat.DETAILED) {
                    "Wind ${parts.joinToString(" ")}"
                } else {
                    parts.joinToString(" ")
                }
            } else null
        } else null

        return when (config.weatherDisplayFormat) {
            WeatherDisplayFormat.COMPACT -> {
                val segments = mutableListOf<String>()
                if (cleanLocation != null) segments.add(cleanLocation)

                val primaryWeather = listOfNotNull(tempToken, condToken).joinToString(" ")
                if (primaryWeather.isNotBlank()) segments.add(primaryWeather)

                if (humidityToken != null) segments.add(humidityToken)
                if (windToken != null) segments.add(windToken)

                if (segments.isEmpty()) emptyList() else listOf(segments.joinToString(" | "))
            }

            WeatherDisplayFormat.DETAILED -> {
                val result = mutableListOf<String>()
                val linePrimary = mutableListOf<String>()
                if (cleanLocation != null) linePrimary.add(cleanLocation)
                if (tempToken != null) linePrimary.add(tempToken)
                if (condToken != null) linePrimary.add(condToken)
                if (linePrimary.isNotEmpty()) {
                    result.add(linePrimary.joinToString(" | "))
                }

                val detailMetrics = listOfNotNull(humidityToken, windToken)
                if (detailMetrics.isNotEmpty()) {
                    result.add(detailMetrics.joinToString(" | "))
                }
                result
            }
        }
    }

    /**
     * Composes the complete dynamic timestamp lines when Weather Information is enabled.
     * Respects `timePosition` ("Time in above" vs "Time in below") while preserving the exact
     * canonical 4-line order by default.
     */
    fun composeLines(
        config: OverlayConfig,
        project: ProjectProfile,
        location: GeoPoint?,
        chainageStr: String,
        coordinateFormat: CoordinateFormat,
        is24HourFormat: Boolean,
        timestampMs: Long,
        locationName: String,
        weatherObservation: WeatherObservation?,
        authoritativeSideOverride: String? = null,
        useAuthoritativeSide: Boolean = false,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<String> {
        val lines = mutableListOf<String>()
        val isTimeAbove = config.timePosition.equals("Time in above", ignoreCase = true)

        // Line 1 – Capture Date and Time
        val dateTimeLine = if (config.showTimestamp) {
            formatDateTimeLine(
                timestampMs = timestampMs,
                timestampFormat = config.timestampFormat,
                is24HourFormat = is24HourFormat,
                timeZone = timeZone
            )
        } else null

        if (isTimeAbove && !dateTimeLine.isNullOrBlank()) {
            lines.add(dateTimeLine)
        }

        // Line 2 – GPS Coordinates
        if (config.showCoordinates && config.weatherShowCoordinates && location != null) {
            lines.add(
                formatCoordinatesLine(
                    location = location,
                    precision = config.coordinatePrecision,
                    coordinateFormat = coordinateFormat
                )
            )
        }

        // Line 3 – Location and Weather
        val resolvedLoc = locationName.ifBlank {
            weatherObservation?.locationName.orEmpty().ifBlank { config.lastDetectedLocationName }
        }
        lines.addAll(
            formatLocationAndWeatherLines(
                config = config,
                locationName = resolvedLoc,
                weatherObservation = weatherObservation,
                captureTimestampMs = timestampMs
            )
        )

        // Line 4 – Highway Project Information (Project | Road/Work | Chainage [Side] [Lane] [Activity])
        if (config.showProjectInfo || config.showChainage) {
            val highwayLine = project.buildHighwayText(
                chainageFormatted = if (config.showChainage) chainageStr else "",
                includeProjectName = config.showProjectInfo,
                sideOverride = authoritativeSideOverride,
                useSideOverride = useAuthoritativeSide
            )
            if (highwayLine.isNotBlank()) {
                lines.add(highwayLine)
            }
            val customComment = project.getCustomCommentLine()
            if (!customComment.isNullOrBlank()) {
                lines.add(customComment)
            }
        }

        if (!isTimeAbove && !dateTimeLine.isNullOrBlank()) {
            lines.add(dateTimeLine)
        }

        // Deduplicate identical consecutive/exact lines cleanly while preserving order
        val deduped = LinkedHashSet<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                deduped.add(trimmed)
            }
        }
        return deduped.toList()
    }

    private fun toDms(coord: Double, isLat: Boolean): String {
        val absVal = abs(coord)
        val degrees = absVal.toInt()
        val minutes = ((absVal - degrees) * 60.0).toInt()
        val seconds = (((absVal - degrees) * 60.0 - minutes) * 60.0).toInt()
        val ref = if (isLat) {
            if (coord >= 0.0) "N" else "S"
        } else {
            if (coord >= 0.0) "E" else "W"
        }
        return String.format(Locale.US, "%d°%d'%d\"%s", degrees, minutes, seconds, ref)
    }
}
