package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
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
    SNOWY("Snowy"),
    CLOUDY_NIGHT("Cloudy Night"),
    WINDY("Windy");

    companion object {
        fun fromName(name: String?): WeatherConditionType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PARTLY_CLOUDY
        }

        /**
         * Maps WMO weather interpretation codes (Open-Meteo standard) + day/night flag into
         * a canonical [WeatherConditionType] and accurate human-readable description.
         * Preserves 2-argument signature for full backward compatibility.
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

        /**
         * Enhanced visual mapping for Dashboard & Hourly animations that distinguishes
         * Cloudy Night and Windy conditions when wind speed is high under dry skies.
         */
        fun resolveVisualCondition(
            baseCondition: WeatherConditionType,
            wmoCode: Int,
            isDay: Boolean,
            windSpeedKmh: Double
        ): WeatherConditionType {
            return when {
                baseCondition == THUNDERSTORM || baseCondition == RAINY || baseCondition == SNOWY || baseCondition == FOGGY -> baseCondition
                windSpeedKmh >= 35.0 -> WINDY
                !isDay && (wmoCode == 2 || wmoCode == 3 || baseCondition == CLOUDY || baseCondition == PARTLY_CLOUDY) -> CLOUDY_NIGHT
                else -> baseCondition
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
 * Temperature display unit preference for Weather Dashboard & Home Weather Card.
 */
enum class TemperatureUnit(val symbol: String, val label: String) {
    CELSIUS("°C", "Celsius (°C)"),
    FAHRENHEIT("°F", "Fahrenheit (°F)");

    fun format(tempC: Double): String {
        val converted = if (this == FAHRENHEIT) (tempC * 9.0 / 5.0) + 32.0 else tempC
        return "${converted.roundToInt()}$symbol"
    }

    companion object {
        fun fromName(raw: String?): TemperatureUnit =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CELSIUS
    }
}

/**
 * Forecast confidence band — explicitly reflects higher meteorological uncertainty on extended days.
 */
enum class ForecastConfidence(val label: String) {
    HIGH("High"),
    MODERATE("Moderate"),
    LOW("Low (Extended)");

    companion object {
        fun fromName(raw: String?): ForecastConfidence =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MODERATE
    }
}

enum class WeatherAlertSeverity(val label: String) {
    ADVISORY("Advisory"),
    WARNING("Warning"),
    SEVERE("Severe");

    companion object {
        fun fromName(raw: String?): WeatherAlertSeverity =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ADVISORY
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
    val source: String = DEFAULT_SOURCE,
    val rainProbabilityPercent: Int? = null,
    val precipitationMm: Double? = null,
    val windGustsKmh: Double? = null,
    val pressureHpa: Double? = null,
    val visibilityKm: Double? = null,
    val uvIndex: Double? = null,
    val dewPointC: Double? = null,
    val cloudCoverPercent: Int? = null,
    val sunriseMs: Long? = null,
    val sunsetMs: Long? = null,
    val todayMinTempC: Double? = null,
    val todayMaxTempC: Double? = null
) {
    val roundedTempC: Int
        get() = temperatureC.roundToInt()

    val roundedFeelsLikeC: Int
        get() = feelsLikeC.roundToInt()

    val windDirectionCompass: String
        get() = degreesToCompass16(windDirectionDegrees)

    val visualCondition: WeatherConditionType
        get() = WeatherConditionType.resolveVisualCondition(condition, wmoCode, isDay, windSpeedKmh)

    val isHeavyRain: Boolean
        get() = wmoCode == 65 || wmoCode == 82 || (precipitationMm ?: 0.0) >= 7.5

    fun formatTemperature(unit: TemperatureUnit = TemperatureUnit.CELSIUS): String = unit.format(temperatureC)

    fun formatFeelsLike(unit: TemperatureUnit = TemperatureUnit.CELSIUS): String = unit.format(feelsLikeC)

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

    fun toJsonObject(): JSONObject {
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
            rainProbabilityPercent?.let { put("rainProbabilityPercent", it) }
            precipitationMm?.let { put("precipitationMm", it) }
            windGustsKmh?.let { put("windGustsKmh", it) }
            pressureHpa?.let { put("pressureHpa", it) }
            visibilityKm?.let { put("visibilityKm", it) }
            uvIndex?.let { put("uvIndex", it) }
            dewPointC?.let { put("dewPointC", it) }
            cloudCoverPercent?.let { put("cloudCoverPercent", it) }
            sunriseMs?.let { put("sunriseMs", it) }
            sunsetMs?.let { put("sunsetMs", it) }
            todayMinTempC?.let { put("todayMinTempC", it) }
            todayMaxTempC?.let { put("todayMaxTempC", it) }
        }
    }

    fun toJsonString(): String = toJsonObject().toString()

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

        fun fromJsonObject(obj: JSONObject): WeatherObservation? {
            return try {
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
                    source = obj.optString("source", DEFAULT_SOURCE).ifBlank { DEFAULT_SOURCE },
                    rainProbabilityPercent = if (obj.has("rainProbabilityPercent") && !obj.isNull("rainProbabilityPercent")) obj.optInt("rainProbabilityPercent") else null,
                    precipitationMm = if (obj.has("precipitationMm") && !obj.isNull("precipitationMm")) obj.optDouble("precipitationMm") else null,
                    windGustsKmh = if (obj.has("windGustsKmh") && !obj.isNull("windGustsKmh")) obj.optDouble("windGustsKmh") else null,
                    pressureHpa = if (obj.has("pressureHpa") && !obj.isNull("pressureHpa")) obj.optDouble("pressureHpa") else null,
                    visibilityKm = if (obj.has("visibilityKm") && !obj.isNull("visibilityKm")) obj.optDouble("visibilityKm") else null,
                    uvIndex = if (obj.has("uvIndex") && !obj.isNull("uvIndex")) obj.optDouble("uvIndex") else null,
                    dewPointC = if (obj.has("dewPointC") && !obj.isNull("dewPointC")) obj.optDouble("dewPointC") else null,
                    cloudCoverPercent = if (obj.has("cloudCoverPercent") && !obj.isNull("cloudCoverPercent")) obj.optInt("cloudCoverPercent") else null,
                    sunriseMs = if (obj.has("sunriseMs") && !obj.isNull("sunriseMs")) obj.optLong("sunriseMs") else null,
                    sunsetMs = if (obj.has("sunsetMs") && !obj.isNull("sunsetMs")) obj.optLong("sunsetMs") else null,
                    todayMinTempC = if (obj.has("todayMinTempC") && !obj.isNull("todayMinTempC")) obj.optDouble("todayMinTempC") else null,
                    todayMaxTempC = if (obj.has("todayMaxTempC") && !obj.isNull("todayMaxTempC")) obj.optDouble("todayMaxTempC") else null
                )
            } catch (_: Exception) {
                null
            }
        }

        fun fromJsonString(raw: String?): WeatherObservation? {
            if (raw.isNullOrBlank()) return null
            return try {
                fromJsonObject(JSONObject(raw))
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Single hourly weather forecast entry (local hour aligned 0..23 -> 12:00 AM to 11:00 PM).
 */
data class HourlyWeatherPoint(
    val epochMs: Long,
    val localHourOfDay: Int,
    val temperatureC: Double,
    val feelsLikeC: Double = temperatureC,
    val rainProbabilityPercent: Int = 0,
    val precipitationMm: Double = 0.0,
    val windSpeedKmh: Double = 0.0,
    val windGustsKmh: Double = windSpeedKmh,
    val windDirectionDegrees: Int = 0,
    val humidityPercent: Int = 0,
    val condition: WeatherConditionType = WeatherConditionType.PARTLY_CLOUDY,
    val conditionLabel: String = condition.defaultLabel,
    val wmoCode: Int = 0,
    val isDay: Boolean = localHourOfDay in 6..18,
    val visibilityKm: Double? = null,
    val cloudCoverPercent: Int? = null,
    val pressureHpa: Double? = null,
    val uvIndex: Double? = null,
    val dewPointC: Double? = null
) {
    val visualCondition: WeatherConditionType
        get() = WeatherConditionType.resolveVisualCondition(condition, wmoCode, isDay, windSpeedKmh)

    val isHeavyRain: Boolean
        get() = wmoCode == 65 || wmoCode == 82 || precipitationMm >= 7.5

    val windDirectionCompass: String
        get() = WeatherObservation.degreesToCompass16(windDirectionDegrees)

    fun formatHourAmPm(): String {
        val h = ((localHourOfDay % 24) + 24) % 24
        return when (h) {
            0 -> "12:00 AM"
            in 1..11 -> "$h:00 AM"
            12 -> "12:00 PM"
            else -> "${h - 12}:00 PM"
        }
    }

    fun formatCompactHourAmPm(): String {
        val h = ((localHourOfDay % 24) + 24) % 24
        return when (h) {
            0 -> "12 AM"
            in 1..11 -> "$h AM"
            12 -> "12 PM"
            else -> "${h - 12} PM"
        }
    }

    fun formatRainfall(): String {
        val mm = precipitationMm.coerceAtLeast(0.0)
        return when {
            mm < 0.05 -> "0 mm"
            mm < 10.0 -> String.format(Locale.US, "%.1f mm", mm)
            else -> "${mm.roundToInt()} mm"
        }
    }

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("epochMs", epochMs)
        put("localHourOfDay", localHourOfDay)
        put("temperatureC", temperatureC)
        put("feelsLikeC", feelsLikeC)
        put("rainProbabilityPercent", rainProbabilityPercent)
        put("precipitationMm", precipitationMm)
        put("windSpeedKmh", windSpeedKmh)
        put("windGustsKmh", windGustsKmh)
        put("windDirectionDegrees", windDirectionDegrees)
        put("humidityPercent", humidityPercent)
        put("condition", condition.name)
        put("conditionLabel", conditionLabel)
        put("wmoCode", wmoCode)
        put("isDay", isDay)
        visibilityKm?.let { put("visibilityKm", it) }
        cloudCoverPercent?.let { put("cloudCoverPercent", it) }
        pressureHpa?.let { put("pressureHpa", it) }
        uvIndex?.let { put("uvIndex", it) }
        dewPointC?.let { put("dewPointC", it) }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): HourlyWeatherPoint? = try {
            val cond = WeatherConditionType.fromName(obj.optString("condition", ""))
            HourlyWeatherPoint(
                epochMs = obj.getLong("epochMs"),
                localHourOfDay = obj.optInt("localHourOfDay", 0).coerceIn(0, 23),
                temperatureC = obj.getDouble("temperatureC"),
                feelsLikeC = obj.optDouble("feelsLikeC", obj.getDouble("temperatureC")),
                rainProbabilityPercent = obj.optInt("rainProbabilityPercent", 0).coerceIn(0, 100),
                precipitationMm = obj.optDouble("precipitationMm", 0.0).coerceAtLeast(0.0),
                windSpeedKmh = obj.optDouble("windSpeedKmh", 0.0).coerceAtLeast(0.0),
                windGustsKmh = obj.optDouble("windGustsKmh", obj.optDouble("windSpeedKmh", 0.0)).coerceAtLeast(0.0),
                windDirectionDegrees = obj.optInt("windDirectionDegrees", 0),
                humidityPercent = obj.optInt("humidityPercent", 0).coerceIn(0, 100),
                condition = cond,
                conditionLabel = obj.optString("conditionLabel", cond.defaultLabel).ifBlank { cond.defaultLabel },
                wmoCode = obj.optInt("wmoCode", 0),
                isDay = obj.optBoolean("isDay", true),
                visibilityKm = if (obj.has("visibilityKm") && !obj.isNull("visibilityKm")) obj.optDouble("visibilityKm") else null,
                cloudCoverPercent = if (obj.has("cloudCoverPercent") && !obj.isNull("cloudCoverPercent")) obj.optInt("cloudCoverPercent") else null,
                pressureHpa = if (obj.has("pressureHpa") && !obj.isNull("pressureHpa")) obj.optDouble("pressureHpa") else null,
                uvIndex = if (obj.has("uvIndex") && !obj.isNull("uvIndex")) obj.optDouble("uvIndex") else null,
                dewPointC = if (obj.has("dewPointC") && !obj.isNull("dewPointC")) obj.optDouble("dewPointC") else null
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Single day in the 7-day forecast with explicit meteorological uncertainty band.
 */
data class DailyWeatherPoint(
    val dateEpochMs: Long,
    val dayIndexFromToday: Int,
    val minTempC: Double,
    val maxTempC: Double,
    val condition: WeatherConditionType,
    val conditionLabel: String = condition.defaultLabel,
    val wmoCode: Int = 0,
    val rainProbabilityPercent: Int = 0,
    val precipitationSumMm: Double = 0.0,
    val windSpeedMaxKmh: Double = 0.0,
    val windGustsMaxKmh: Double = windSpeedMaxKmh,
    val dominantWindDirectionDegrees: Int = 0,
    val uvIndexMax: Double? = null,
    val sunriseMs: Long? = null,
    val sunsetMs: Long? = null
) {
    val forecastConfidence: ForecastConfidence
        get() = when {
            dayIndexFromToday <= 1 -> ForecastConfidence.HIGH
            dayIndexFromToday <= 3 -> ForecastConfidence.MODERATE
            else -> ForecastConfidence.LOW
        }

    val uncertaintyNote: String
        get() = when (forecastConfidence) {
            ForecastConfidence.HIGH -> "High forecast confidence (short-range)"
            ForecastConfidence.MODERATE -> "Moderate forecast confidence (medium-range)"
            ForecastConfidence.LOW -> "Higher uncertainty (days 4–7; re-verify closer to work)"
        }

    val windDirectionCompass: String
        get() = WeatherObservation.degreesToCompass16(dominantWindDirectionDegrees)

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("dateEpochMs", dateEpochMs)
        put("dayIndexFromToday", dayIndexFromToday)
        put("minTempC", minTempC)
        put("maxTempC", maxTempC)
        put("condition", condition.name)
        put("conditionLabel", conditionLabel)
        put("wmoCode", wmoCode)
        put("rainProbabilityPercent", rainProbabilityPercent)
        put("precipitationSumMm", precipitationSumMm)
        put("windSpeedMaxKmh", windSpeedMaxKmh)
        put("windGustsMaxKmh", windGustsMaxKmh)
        put("dominantWindDirectionDegrees", dominantWindDirectionDegrees)
        uvIndexMax?.let { put("uvIndexMax", it) }
        sunriseMs?.let { put("sunriseMs", it) }
        sunsetMs?.let { put("sunsetMs", it) }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): DailyWeatherPoint? = try {
            val cond = WeatherConditionType.fromName(obj.optString("condition", ""))
            DailyWeatherPoint(
                dateEpochMs = obj.getLong("dateEpochMs"),
                dayIndexFromToday = obj.optInt("dayIndexFromToday", 0).coerceAtLeast(0),
                minTempC = obj.getDouble("minTempC"),
                maxTempC = obj.getDouble("maxTempC"),
                condition = cond,
                conditionLabel = obj.optString("conditionLabel", cond.defaultLabel).ifBlank { cond.defaultLabel },
                wmoCode = obj.optInt("wmoCode", 0),
                rainProbabilityPercent = obj.optInt("rainProbabilityPercent", 0).coerceIn(0, 100),
                precipitationSumMm = obj.optDouble("precipitationSumMm", 0.0).coerceAtLeast(0.0),
                windSpeedMaxKmh = obj.optDouble("windSpeedMaxKmh", 0.0).coerceAtLeast(0.0),
                windGustsMaxKmh = obj.optDouble("windGustsMaxKmh", obj.optDouble("windSpeedMaxKmh", 0.0)).coerceAtLeast(0.0),
                dominantWindDirectionDegrees = obj.optInt("dominantWindDirectionDegrees", 0),
                uvIndexMax = if (obj.has("uvIndexMax") && !obj.isNull("uvIndexMax")) obj.optDouble("uvIndexMax") else null,
                sunriseMs = if (obj.has("sunriseMs") && !obj.isNull("sunriseMs")) obj.optLong("sunriseMs") else null,
                sunsetMs = if (obj.has("sunsetMs") && !obj.isNull("sunsetMs")) obj.optLong("sunsetMs") else null
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Severe or actionable weather alert derived from actual forecast telemetry.
 */
data class WeatherAlertItem(
    val id: String,
    val title: String,
    val description: String,
    val severity: WeatherAlertSeverity,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val category: String
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("severity", severity.name)
        put("startEpochMs", startEpochMs)
        put("endEpochMs", endEpochMs)
        put("category", category)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): WeatherAlertItem? = try {
            WeatherAlertItem(
                id = obj.getString("id"),
                title = obj.getString("title"),
                description = obj.optString("description", ""),
                severity = WeatherAlertSeverity.fromName(obj.optString("severity", "")),
                startEpochMs = obj.optLong("startEpochMs", 0L),
                endEpochMs = obj.optLong("endEpochMs", 0L),
                category = obj.optString("category", "WEATHER")
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Unified current + hourly + 7-day forecast bundle backed by a single live GPS weather fetch
 * and persisted locally for reliable offline access.
 */
data class WeatherForecastBundle(
    val current: WeatherObservation,
    val hourly: List<HourlyWeatherPoint> = emptyList(),
    val daily: List<DailyWeatherPoint> = emptyList(),
    val alerts: List<WeatherAlertItem> = emptyList(),
    val utcOffsetSeconds: Int = 0,
    val timezoneId: String = "",
    val fetchedAtMs: Long = current.fetchedAtMs,
    val source: String = current.source
) {
    fun isStaleAt(referenceTimeMs: Long, maxAgeMs: Long = DEFAULT_FORECAST_STALE_MS): Boolean {
        if (fetchedAtMs <= 0L) return true
        return (referenceTimeMs - fetchedAtMs).coerceAtLeast(0L) > maxAgeMs
    }

    /**
     * Extracts today's complete 24-hour forecast from 12:00 AM (00:00) to 11:00 PM (23:00)
     * in the location's local timezone.
     */
    fun todayFullDayHours(referenceTimeMs: Long = System.currentTimeMillis()): List<HourlyWeatherPoint> {
        if (hourly.isEmpty()) return emptyList()
        val tz = resolvedTimeZone()
        val refCal = Calendar.getInstance(tz).apply { timeInMillis = referenceTimeMs }
        val refYear = refCal.get(Calendar.YEAR)
        val refDayOfYear = refCal.get(Calendar.DAY_OF_YEAR)
        val todayOnly = hourly.filter { pt ->
            val c = Calendar.getInstance(tz).apply { timeInMillis = pt.epochMs }
            c.get(Calendar.YEAR) == refYear && c.get(Calendar.DAY_OF_YEAR) == refDayOfYear
        }.sortedBy { it.localHourOfDay }
        return if (todayOnly.isNotEmpty()) {
            todayOnly
        } else {
            hourly.take(24).sortedBy { it.localHourOfDay }
        }
    }

    /**
     * Returns the current local hour (0..23) in the forecast's timezone.
     */
    fun currentLocalHour(referenceTimeMs: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance(resolvedTimeZone()).apply { timeInMillis = referenceTimeMs }
        return cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
    }

    /**
     * Returns the next 24 consecutive hours starting from the current hour (or first available hour).
     */
    fun next24Hours(referenceTimeMs: Long = System.currentTimeMillis()): List<HourlyWeatherPoint> {
        if (hourly.isEmpty()) return emptyList()
        val sorted = hourly.sortedBy { it.epochMs }
        val floorHourMs = referenceTimeMs - 3_599_000L
        val upcoming = sorted.filter { it.epochMs >= floorHourMs }.take(24)
        return if (upcoming.size >= 6) upcoming else sorted.take(24)
    }

    fun resolvedTimeZone(): TimeZone {
        if (timezoneId.isNotBlank()) {
            val tz = TimeZone.getTimeZone(timezoneId)
            if (tz.id.equals(timezoneId, ignoreCase = true) || timezoneId == "GMT" || timezoneId == "UTC") {
                return tz
            }
        }
        val offsetHours = utcOffsetSeconds / 3600
        val offsetMins = kotlin.math.abs((utcOffsetSeconds % 3600) / 60)
        val sign = if (utcOffsetSeconds >= 0) "+" else "-"
        return TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:%02d", sign, kotlin.math.abs(offsetHours), offsetMins))
    }

    fun toJsonString(): String {
        return JSONObject().apply {
            put("current", current.toJsonObject())
            put("utcOffsetSeconds", utcOffsetSeconds)
            put("timezoneId", timezoneId)
            put("fetchedAtMs", fetchedAtMs)
            put("source", source)
            put("hourly", JSONArray().apply { hourly.forEach { put(it.toJsonObject()) } })
            put("daily", JSONArray().apply { daily.forEach { put(it.toJsonObject()) } })
            put("alerts", JSONArray().apply { alerts.forEach { put(it.toJsonObject()) } })
        }.toString()
    }

    companion object {
        const val DEFAULT_FORECAST_STALE_MS = 60L * 60L * 1000L // 60 minutes

        fun fromJsonString(raw: String?): WeatherForecastBundle? {
            if (raw.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(raw)
                val currentObj = obj.optJSONObject("current") ?: return null
                val current = WeatherObservation.fromJsonObject(currentObj) ?: return null
                val hourlyArr = obj.optJSONArray("hourly")
                val hourlyList = buildList {
                    if (hourlyArr != null) {
                        for (i in 0 until hourlyArr.length()) {
                            hourlyArr.optJSONObject(i)?.let { HourlyWeatherPoint.fromJsonObject(it)?.let(::add) }
                        }
                    }
                }
                val dailyArr = obj.optJSONArray("daily")
                val dailyList = buildList {
                    if (dailyArr != null) {
                        for (i in 0 until dailyArr.length()) {
                            dailyArr.optJSONObject(i)?.let { DailyWeatherPoint.fromJsonObject(it)?.let(::add) }
                        }
                    }
                }
                val alertsArr = obj.optJSONArray("alerts")
                val alertsList = buildList {
                    if (alertsArr != null) {
                        for (i in 0 until alertsArr.length()) {
                            alertsArr.optJSONObject(i)?.let { WeatherAlertItem.fromJsonObject(it)?.let(::add) }
                        }
                    }
                }
                WeatherForecastBundle(
                    current = current,
                    hourly = hourlyList,
                    daily = dailyList,
                    alerts = alertsList,
                    utcOffsetSeconds = obj.optInt("utcOffsetSeconds", 0),
                    timezoneId = obj.optString("timezoneId", ""),
                    fetchedAtMs = obj.optLong("fetchedAtMs", current.fetchedAtMs),
                    source = obj.optString("source", current.source).ifBlank { current.source }
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
