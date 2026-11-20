package com.example.model

import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Canonical weather condition categories rendered with dedicated smooth animated illustrations
 * and stored in photo/video timestamp metadata.
 */
enum class WeatherConditionType(val defaultLabel: String) {
    SUNNY("Sunny"),
    PARTLY_CLOUDY("Partly Cloudy"),
    CLOUDY("Cloudy"),
    RAINY("Rainy"),
    THUNDERSTORM("Thunderstorm"),
    FOGGY("Foggy"),
    NIGHT("Clear Night"),
    SNOWY("Snowy");

    companion object {
        fun fromName(name: String?): WeatherConditionType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PARTLY_CLOUDY
        }

        /**
         * Maps WMO weather interpretation codes (Open-Meteo standard) + day/night flag into
         * a canonical [WeatherConditionType] and accurate human-readable description.
         */
        fun fromWmoCode(wmoCode: Int, isDay: Boolean): Pair<WeatherConditionType, String> {
            return when (wmoCode) {
                0 -> if (isDay) SUNNY to "Sunny" else NIGHT to "Clear Night"
                1 -> if (isDay) SUNNY to "Mostly Sunny" else NIGHT to "Mostly Clear"
                2 -> PARTLY_CLOUDY to "Partly Cloudy"
                3 -> CLOUDY to "Cloudy"
                45, 48 -> FOGGY to "Foggy"
                51, 53, 55 -> RAINY to "Drizzle"
                56, 57 -> RAINY to "Freezing Drizzle"
                61 -> RAINY to "Light Rain"
                63 -> RAINY to "Rainy"
                65 -> RAINY to "Heavy Rain"
                66, 67 -> RAINY to "Freezing Rain"
                71 -> SNOWY to "Light Snow"
                73 -> SNOWY to "Snowy"
                75 -> SNOWY to "Heavy Snow"
                77 -> SNOWY to "Snow Grains"
                80, 81, 82 -> RAINY to "Rain Showers"
                85, 86 -> SNOWY to "Snow Showers"
                95 -> THUNDERSTORM to "Thunderstorm"
                96, 99 -> THUNDERSTORM to "Thunderstorm"
                else -> if (isDay) PARTLY_CLOUDY to "Partly Cloudy" else NIGHT to "Clear Night"
            }
        }
    }
}

/**
 * Display density option for Weather Information in Camera Timestamp & Watermark settings.
 */
enum class WeatherDisplayFormat(val displayName: String) {
    COMPACT("Compact"),
    DETAILED("Detailed");

    companion object {
        fun fromString(raw: String?): WeatherDisplayFormat {
            return entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.displayName.equals(raw, ignoreCase = true)
            } ?: COMPACT
        }
    }
}

/**
 * Immutable weather observation tied to a specific geographic coordinate and observation time.
 * Every field originates from actual live weather telemetry — never fabricated defaults.
 */
data class WeatherObservation(
    val temperatureC: Double,
    val feelsLikeC: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Int,
    val condition: WeatherConditionType,
    val conditionLabel: String = condition.defaultLabel,
    val wmoCode: Int = 0,
    val isDay: Boolean = true,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationName: String = "",
    val observedAtMs: Long,
    val fetchedAtMs: Long = observedAtMs,
    val source: String = DEFAULT_SOURCE
) {
    val roundedTempC: Int
        get() = temperatureC.roundToInt()

    val roundedFeelsLikeC: Int
        get() = feelsLikeC.roundToInt()

    val windDirectionCompass: String
        get() = degreesToCompass16(windDirectionDegrees)

    fun formatTemperature(): String = "${roundedTempC}°C"

    fun formatFeelsLike(): String = "${roundedFeelsLikeC}°C"

    fun formatHumidity(): String = "${humidityPercent.coerceIn(0, 100)}%"

    fun formatWindSpeed(): String {
        val speed = windSpeedKmh.coerceAtLeast(0.0)
        return if (speed >= 10.0 || speed % 1.0 == 0.0) {
            "${speed.roundToInt()} km/h"
        } else {
            String.format(Locale.US, "%.1f km/h", speed)
        }
    }

    fun ageMs(referenceTimeMs: Long): Long = (referenceTimeMs - observedAtMs).coerceAtLeast(0L)

    fun isFreshAt(referenceTimeMs: Long, maxAgeMs: Long = DEFAULT_CAPTURE_MAX_AGE_MS): Boolean {
        if (observedAtMs <= 0L) return false
        return ageMs(referenceTimeMs) <= maxAgeMs
    }

    fun freshnessDescription(referenceTimeMs: Long): String {
        val ageMin = ageMs(referenceTimeMs) / 60_000L
        return when {
            ageMin < 1L -> "Just now"
            ageMin < 60L -> "${ageMin}m ago"
            else -> "${ageMin / 60L}h ${ageMin % 60L}m ago"
        }
    }

    fun toJsonString(): String {
        return JSONObject().apply {
            put("temperatureC", temperatureC)
            put("feelsLikeC", feelsLikeC)
            put("humidityPercent", humidityPercent)
            put("windSpeedKmh", windSpeedKmh)
            put("windDirectionDegrees", windDirectionDegrees)
            put("condition", condition.name)
            put("conditionLabel", conditionLabel)
            put("wmoCode", wmoCode)
            put("isDay", isDay)
            put("latitude", latitude)
            put("longitude", longitude)
            put("locationName", locationName)
            put("observedAtMs", observedAtMs)
            put("fetchedAtMs", fetchedAtMs)
            put("source", source)
        }.toString()
    }

    companion object {
        const val DEFAULT_SOURCE = "Open-Meteo"
        const val DEFAULT_CAPTURE_MAX_AGE_MS = 45L * 60L * 1000L // 45 minutes

        private val COMPASS_16 = arrayOf(
            "N", "NNE", "NE", "ENE",
            "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW",
            "W", "WNW", "NW", "NNW"
        )

        fun degreesToCompass16(degrees: Int): String {
            val normalized = ((degrees % 360) + 360) % 360
            val idx = (((normalized + 11.25) % 360.0) / 22.5).toInt()
            return COMPASS_16[idx % COMPASS_16.size]
        }

        fun fromJsonString(raw: String?): WeatherObservation? {
            if (raw.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(raw)
                val observedAt = obj.optLong("observedAtMs", 0L)
                if (observedAt <= 0L) return null
                val cond = WeatherConditionType.fromName(obj.optString("condition", ""))
                WeatherObservation(
                    temperatureC = obj.getDouble("temperatureC"),
                    feelsLikeC = obj.optDouble("feelsLikeC", obj.getDouble("temperatureC")),
                    humidityPercent = obj.optInt("humidityPercent", 0),
                    windSpeedKmh = obj.optDouble("windSpeedKmh", 0.0),
                    windDirectionDegrees = obj.optInt("windDirectionDegrees", 0),
                    condition = cond,
                    conditionLabel = obj.optString("conditionLabel", cond.defaultLabel).ifBlank { cond.defaultLabel },
                    wmoCode = obj.optInt("wmoCode", 0),
                    isDay = obj.optBoolean("isDay", true),
                    latitude = obj.optDouble("latitude", 0.0),
                    longitude = obj.optDouble("longitude", 0.0),
                    locationName = obj.optString("locationName", ""),
                    observedAtMs = observedAt,
                    fetchedAtMs = obj.optLong("fetchedAtMs", observedAt),
                    source = obj.optString("source", DEFAULT_SOURCE).ifBlank { DEFAULT_SOURCE }
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * UI state for the Home Screen Live Weather Card.
 */
sealed class WeatherUiState {
    data object WaitingForGps : WeatherUiState()
    data class Loading(val previous: WeatherObservation? = null) : WeatherUiState()
    data class Available(
        val observation: WeatherObservation,
        val isRefreshing: Boolean = false
    ) : WeatherUiState()

    data class Unavailable(
        val reason: String,
        val cachedObservation: WeatherObservation? = null
    ) : WeatherUiState()
}
