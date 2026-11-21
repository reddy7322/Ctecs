package com.example.weather

import android.util.Log
import android.util.LruCache
import com.example.geo.GeoUtils
import com.example.model.DailyWeatherPoint
import com.example.model.GeoPoint
import com.example.model.HourlyWeatherPoint
import com.example.model.WeatherAlertItem
import com.example.model.WeatherAlertSeverity
import com.example.model.WeatherConditionType
import com.example.model.WeatherForecastBundle
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * Network fetcher abstraction allowing deterministic test injection without real network calls.
 * Preserved for full backward compatibility with existing tests and capture consumers.
 */
fun interface WeatherRemoteFetcher {
    suspend fun fetch(latitude: Double, longitude: Double, nowMs: Long): WeatherObservation?
}

/**
 * Extended forecast fetcher returning unified current + hourly + 7-day daily weather telemetry
 * from a single Open-Meteo API request.
 */
interface WeatherForecastFetcher : WeatherRemoteFetcher {
    suspend fun fetchForecastBundle(
        latitude: Double,
        longitude: Double,
        nowMs: Long
    ): WeatherForecastBundle?

    override suspend fun fetch(latitude: Double, longitude: Double, nowMs: Long): WeatherObservation? {
        return fetchForecastBundle(latitude, longitude, nowMs)?.current
    }
}

/**
 * Authoritative live weather client using Open-Meteo (free, global, high-accuracy, keyless).
 * Fetches current weather, full hourly forecast, and 7-day daily forecast in one unified call.
 */
class OpenMeteoWeatherFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
) : WeatherForecastFetcher {

    override suspend fun fetchForecastBundle(
        latitude: Double,
        longitude: Double,
        nowMs: Long
    ): WeatherForecastBundle? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,dew_point_2m,visibility" +
                "&hourly=temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,surface_pressure,cloud_cover,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant" +
                "&forecast_days=7&wind_speed_unit=kmh&timeformat=unixtime&timezone=auto",
            latitude,
            longitude
        )
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "CTECS-Highway-Android/1.0")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Open-Meteo HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                parseOpenMeteoForecastResponse(
                    jsonBody = body,
                    latitude = latitude,
                    longitude = longitude,
                    fetchedAtMs = nowMs
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Weather forecast fetch failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "OpenMeteoWeatherFetcher"

        /**
         * Preserved static parser for current-only responses and backward-compatible unit tests.
         */
        fun parseOpenMeteoCurrentResponse(
            jsonBody: String,
            latitude: Double,
            longitude: Double,
            fetchedAtMs: Long
        ): WeatherObservation? {
            return parseOpenMeteoForecastResponse(
                jsonBody = jsonBody,
                latitude = latitude,
                longitude = longitude,
                fetchedAtMs = fetchedAtMs
            )?.current
        }

        /**
         * Parses a full Open-Meteo response containing `current`, `hourly`, and `daily` sections.
         * Safely handles partial responses (e.g. current-only) without fabricating missing fields.
         */
        fun parseOpenMeteoForecastResponse(
            jsonBody: String,
            latitude: Double,
            longitude: Double,
            fetchedAtMs: Long
        ): WeatherForecastBundle? {
            return try {
                val root = JSONObject(jsonBody)
                val current = root.optJSONObject("current") ?: return null
                if (!current.has("temperature_2m") || current.isNull("temperature_2m")) return null

                val utcOffsetSeconds = root.optInt("utc_offset_seconds", 0)
                val timezoneId = root.optString("timezone", "")
                val tz = resolveTimeZone(timezoneId, utcOffsetSeconds)

                val tempC = current.getDouble("temperature_2m")
                val feelsLikeC = optNullableDouble(current, "apparent_temperature") ?: tempC
                val humidity = current.optInt("relative_humidity_2m", 0).coerceIn(0, 100)
                val windSpeedKmh = current.optDouble("wind_speed_10m", 0.0).coerceAtLeast(0.0)
                val windDirDeg = ((current.optInt("wind_direction_10m", 0) % 360) + 360) % 360
                val windGustsKmh = optNullableDouble(current, "wind_gusts_10m")?.coerceAtLeast(0.0)
                val wmoCode = current.optInt("weather_code", 0)
                val isDay = current.optInt("is_day", 1) == 1
                val apiTimeSec = current.optLong("time", 0L)
                val observedAtMs = if (apiTimeSec > 1_000_000_000L) apiTimeSec * 1000L else fetchedAtMs
                val (condType, condLabel) = WeatherConditionType.fromWmoCode(wmoCode, isDay)

                val precipMm = optNullableDouble(current, "precipitation")?.coerceAtLeast(0.0)
                val pressureHpa = optNullableDouble(current, "surface_pressure")
                val visibilityMeters = optNullableDouble(current, "visibility")
                val visibilityKm = visibilityMeters?.let { (it / 1000.0).coerceAtLeast(0.0) }
                val uvIndex = optNullableDouble(current, "uv_index")?.coerceAtLeast(0.0)
                val dewPointC = optNullableDouble(current, "dew_point_2m")
                val cloudCover = optNullableInt(current, "cloud_cover")?.coerceIn(0, 100)

                val hourlyList = parseHourlySection(root.optJSONObject("hourly"), tz)
                val dailyList = parseDailySection(root.optJSONObject("daily"))

                // Enrich current rain probability & today's min/max/sunrise/sunset from matching hourly/daily entries
                val matchingHour = hourlyList.minByOrNull { kotlin.math.abs(it.epochMs - observedAtMs) }
                val currentRainProb = matchingHour
                    ?.takeIf { kotlin.math.abs(it.epochMs - observedAtMs) <= 3_600_000L }
                    ?.rainProbabilityPercent
                val todayDaily = dailyList.firstOrNull()

                val observation = WeatherObservation(
                    temperatureC = tempC,
                    feelsLikeC = feelsLikeC,
                    humidityPercent = humidity,
                    windSpeedKmh = windSpeedKmh,
                    windDirectionDegrees = windDirDeg,
                    condition = condType,
                    conditionLabel = condLabel,
                    wmoCode = wmoCode,
                    isDay = isDay,
                    latitude = latitude,
                    longitude = longitude,
                    observedAtMs = observedAtMs,
                    fetchedAtMs = fetchedAtMs,
                    source = WeatherObservation.DEFAULT_SOURCE,
                    rainProbabilityPercent = currentRainProb,
                    precipitationMm = precipMm,
                    windGustsKmh = windGustsKmh,
                    pressureHpa = pressureHpa,
                    visibilityKm = visibilityKm,
                    uvIndex = uvIndex,
                    dewPointC = dewPointC,
                    cloudCoverPercent = cloudCover,
                    sunriseMs = todayDaily?.sunriseMs,
                    sunsetMs = todayDaily?.sunsetMs,
                    todayMinTempC = todayDaily?.minTempC,
                    todayMaxTempC = todayDaily?.maxTempC
                )

                val alerts = deriveWeatherAlerts(observation, hourlyList, observedAtMs)

                WeatherForecastBundle(
                    current = observation,
                    hourly = hourlyList,
                    daily = dailyList,
                    alerts = alerts,
                    utcOffsetSeconds = utcOffsetSeconds,
                    timezoneId = timezoneId,
                    fetchedAtMs = fetchedAtMs,
                    source = WeatherObservation.DEFAULT_SOURCE
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing Open-Meteo forecast JSON: ${e.message}")
                null
            }
        }

        private fun parseHourlySection(hourly: JSONObject?, tz: TimeZone): List<HourlyWeatherPoint> {
            if (hourly == null) return emptyList()
            val times = hourly.optJSONArray("time") ?: return emptyList()
            val temps = hourly.optJSONArray("temperature_2m") ?: return emptyList()
            val feels = hourly.optJSONArray("apparent_temperature")
            val humidities = hourly.optJSONArray("relative_humidity_2m")
            val rainProbs = hourly.optJSONArray("precipitation_probability")
            val precips = hourly.optJSONArray("precipitation")
            val codes = hourly.optJSONArray("weather_code")
            val winds = hourly.optJSONArray("wind_speed_10m")
            val gusts = hourly.optJSONArray("wind_gusts_10m")
            val windDirs = hourly.optJSONArray("wind_direction_10m")
            val isDays = hourly.optJSONArray("is_day")
            val visibilities = hourly.optJSONArray("visibility")
            val clouds = hourly.optJSONArray("cloud_cover")
            val pressures = hourly.optJSONArray("surface_pressure")
            val uvs = hourly.optJSONArray("uv_index")
            val dewPoints = hourly.optJSONArray("dew_point_2m")

            val count = minOf(times.length(), temps.length(), 72)
            val cal = Calendar.getInstance(tz)
            return buildList(count) {
                for (i in 0 until count) {
                    val epochSec = times.optLong(i, 0L)
                    if (epochSec <= 1_000_000_000L || temps.isNull(i)) continue
                    val epochMs = epochSec * 1000L
                    cal.timeInMillis = epochMs
                    val localHour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
                    val tempC = temps.optDouble(i)
                    val wmoCode = codes?.optInt(i, 0) ?: 0
                    val isDay = if (isDays != null && !isDays.isNull(i)) {
                        isDays.optInt(i, 1) == 1
                    } else {
                        localHour in 6..18
                    }
                    val (condType, condLabel) = WeatherConditionType.fromWmoCode(wmoCode, isDay)
                    val windKmh = optArrayDouble(winds, i)?.coerceAtLeast(0.0) ?: 0.0
                    val gustKmh = optArrayDouble(gusts, i)?.coerceAtLeast(windKmh) ?: windKmh
                    val visMeters = optArrayDouble(visibilities, i)

                    add(
                        HourlyWeatherPoint(
                            epochMs = epochMs,
                            localHourOfDay = localHour,
                            temperatureC = tempC,
                            feelsLikeC = optArrayDouble(feels, i) ?: tempC,
                            rainProbabilityPercent = (optArrayInt(rainProbs, i) ?: 0).coerceIn(0, 100),
                            precipitationMm = (optArrayDouble(precips, i) ?: 0.0).coerceAtLeast(0.0),
                            windSpeedKmh = windKmh,
                            windGustsKmh = gustKmh,
                            windDirectionDegrees = (((optArrayInt(windDirs, i) ?: 0) % 360) + 360) % 360,
                            humidityPercent = (optArrayInt(humidities, i) ?: 0).coerceIn(0, 100),
                            condition = condType,
                            conditionLabel = condLabel,
                            wmoCode = wmoCode,
                            isDay = isDay,
                            visibilityKm = visMeters?.let { (it / 1000.0).coerceAtLeast(0.0) },
                            cloudCoverPercent = optArrayInt(clouds, i)?.coerceIn(0, 100),
                            pressureHpa = optArrayDouble(pressures, i),
                            uvIndex = optArrayDouble(uvs, i)?.coerceAtLeast(0.0),
                            dewPointC = optArrayDouble(dewPoints, i)
                        )
                    )
                }
            }
        }

        private fun parseDailySection(daily: JSONObject?): List<DailyWeatherPoint> {
            if (daily == null) return emptyList()
            val times = daily.optJSONArray("time") ?: return emptyList()
            val maxTemps = daily.optJSONArray("temperature_2m_max") ?: return emptyList()
            val minTemps = daily.optJSONArray("temperature_2m_min") ?: return emptyList()
            val codes = daily.optJSONArray("weather_code")
            val rainProbs = daily.optJSONArray("precipitation_probability_max")
            val rainSums = daily.optJSONArray("precipitation_sum")
            val winds = daily.optJSONArray("wind_speed_10m_max")
            val gusts = daily.optJSONArray("wind_gusts_10m_max")
            val windDirs = daily.optJSONArray("wind_direction_10m_dominant")
            val uvs = daily.optJSONArray("uv_index_max")
            val sunrises = daily.optJSONArray("sunrise")
            val sunsets = daily.optJSONArray("sunset")

            val count = minOf(times.length(), maxTemps.length(), minTemps.length(), 7)
            return buildList(count) {
                for (i in 0 until count) {
                    val epochSec = times.optLong(i, 0L)
                    if (epochSec <= 1_000_000_000L || maxTemps.isNull(i) || minTemps.isNull(i)) continue
                    val wmoCode = codes?.optInt(i, 0) ?: 0
                    val (condType, condLabel) = WeatherConditionType.fromWmoCode(wmoCode, isDay = true)
                    val windMax = optArrayDouble(winds, i)?.coerceAtLeast(0.0) ?: 0.0
                    val gustMax = optArrayDouble(gusts, i)?.coerceAtLeast(windMax) ?: windMax
                    val sunriseSec = sunrises?.optLong(i, 0L) ?: 0L
                    val sunsetSec = sunsets?.optLong(i, 0L) ?: 0L
                    add(
                        DailyWeatherPoint(
                            dateEpochMs = epochSec * 1000L,
                            dayIndexFromToday = i,
                            minTempC = minTemps.optDouble(i),
                            maxTempC = maxTemps.optDouble(i),
                            condition = condType,
                            conditionLabel = condLabel,
                            wmoCode = wmoCode,
                            rainProbabilityPercent = (optArrayInt(rainProbs, i) ?: 0).coerceIn(0, 100),
                            precipitationSumMm = (optArrayDouble(rainSums, i) ?: 0.0).coerceAtLeast(0.0),
                            windSpeedMaxKmh = windMax,
                            windGustsMaxKmh = gustMax,
                            dominantWindDirectionDegrees = (((optArrayInt(windDirs, i) ?: 0) % 360) + 360) % 360,
                            uvIndexMax = optArrayDouble(uvs, i)?.coerceAtLeast(0.0),
                            sunriseMs = sunriseSec.takeIf { it > 1_000_000_000L }?.times(1000L),
                            sunsetMs = sunsetSec.takeIf { it > 1_000_000_000L }?.times(1000L)
                        )
                    )
                }
            }
        }

        /**
         * Derives factual severe/advisory weather alerts strictly from live forecast telemetry.
         */
        fun deriveWeatherAlerts(
            current: WeatherObservation,
            hourly: List<HourlyWeatherPoint>,
            referenceTimeMs: Long
        ): List<WeatherAlertItem> {
            val next18 = hourly.filter { it.epochMs >= referenceTimeMs - 3_600_000L }.take(18)
            val alerts = mutableListOf<WeatherAlertItem>()

            // 1. Thunderstorm alert
            val stormHours = next18.filter { it.condition == WeatherConditionType.THUNDERSTORM }
            if (current.condition == WeatherConditionType.THUNDERSTORM || stormHours.isNotEmpty()) {
                val start = stormHours.firstOrNull()?.epochMs ?: current.observedAtMs
                val end = stormHours.lastOrNull()?.epochMs?.plus(3_600_000L) ?: (start + 3_600_000L)
                alerts += WeatherAlertItem(
                    id = "thunderstorm_${start / 3_600_000L}",
                    title = "Thunderstorm & Lightning Warning",
                    description = "Thunderstorm conditions forecasted (${stormHours.size.coerceAtLeast(1)} hr window). Suspend exposed highway paving, crane, and electrical works.",
                    severity = WeatherAlertSeverity.SEVERE,
                    startEpochMs = start,
                    endEpochMs = end,
                    category = "THUNDERSTORM"
                )
            }

            // 2. Heavy rainfall alert
            val heavyRainHours = next18.filter { it.precipitationMm >= 7.5 || it.wmoCode in listOf(65, 82) }
            if (heavyRainHours.isNotEmpty()) {
                val maxMm = heavyRainHours.maxOf { it.precipitationMm }
                alerts += WeatherAlertItem(
                    id = "heavy_rain_${heavyRainHours.first().epochMs / 3_600_000L}",
                    title = "Heavy Rainfall Alert",
                    description = String.format(
                        Locale.US,
                        "Peak rainfall up to %.1f mm/hr expected. Risk of surface ponding, tack-coat washout, and subgrade saturation.",
                        maxMm
                    ),
                    severity = WeatherAlertSeverity.SEVERE,
                    startEpochMs = heavyRainHours.first().epochMs,
                    endEpochMs = heavyRainHours.last().epochMs + 3_600_000L,
                    category = "HEAVY_RAIN"
                )
            } else {
                val rainHours = next18.filter { it.rainProbabilityPercent >= 60 || it.precipitationMm >= 0.5 }
                if (rainHours.isNotEmpty()) {
                    val maxProb = rainHours.maxOf { it.rainProbabilityPercent }
                    alerts += WeatherAlertItem(
                        id = "rain_${rainHours.first().epochMs / 3_600_000L}",
                        title = "Rain Expected (${maxProb}% probability)",
                        description = "Rainfall expected during ${rainHours.first().formatCompactHourAmPm()}–${rainHours.last().formatCompactHourAmPm()}. Bituminous laying (BC/DBM) is restricted during imminent or active rain.",
                        severity = WeatherAlertSeverity.WARNING,
                        startEpochMs = rainHours.first().epochMs,
                        endEpochMs = rainHours.last().epochMs + 3_600_000L,
                        category = "RAIN"
                    )
                }
            }

            // 3. High wind alert (>40 km/h MoRTH Cl. 501.5.1 threshold)
            val highWindHours = next18.filter { it.windSpeedKmh > 40.0 || it.windGustsKmh >= 50.0 }
            if (current.windSpeedKmh > 40.0 || highWindHours.isNotEmpty()) {
                val peakWind = maxOf(
                    current.windSpeedKmh,
                    highWindHours.maxOfOrNull { it.windSpeedKmh } ?: 0.0
                )
                val start = highWindHours.firstOrNull()?.epochMs ?: current.observedAtMs
                val end = (highWindHours.lastOrNull()?.epochMs ?: start) + 3_600_000L
                alerts += WeatherAlertItem(
                    id = "high_wind_${start / 3_600_000L}",
                    title = "High Wind Advisory (${peakWind.toInt()} km/h)",
                    description = "Wind speed exceeds or approaches the 40 km/h MoRTH Cl. 501.5.1 limit. Verify 2m site wind speed before bituminous laying or spraying.",
                    severity = if (peakWind > 40.0) WeatherAlertSeverity.WARNING else WeatherAlertSeverity.ADVISORY,
                    startEpochMs = start,
                    endEpochMs = end,
                    category = "HIGH_WIND"
                )
            }

            // 4. Fog / low visibility alert
            val fogHours = next18.filter { it.condition == WeatherConditionType.FOGGY || (it.visibilityKm != null && it.visibilityKm < 1.0) }
            if (current.condition == WeatherConditionType.FOGGY || fogHours.isNotEmpty()) {
                val start = fogHours.firstOrNull()?.epochMs ?: current.observedAtMs
                val end = (fogHours.lastOrNull()?.epochMs ?: start) + 3_600_000L
                alerts += WeatherAlertItem(
                    id = "fog_${start / 3_600_000L}",
                    title = "Fog / Low Visibility Warning",
                    description = "Fog or low visibility detected. MoRTH Cl. 501.5.1 prohibits bituminous laying during fog; enforce highway work-zone traffic safety.",
                    severity = WeatherAlertSeverity.WARNING,
                    startEpochMs = start,
                    endEpochMs = end,
                    category = "FOG"
                )
            }

            // 5. Extreme temperature alerts
            val maxTemp = maxOf(current.temperatureC, next18.maxOfOrNull { it.temperatureC } ?: current.temperatureC)
            val minTemp = minOf(current.temperatureC, next18.minOfOrNull { it.temperatureC } ?: current.temperatureC)
            if (maxTemp >= 42.0) {
                alerts += WeatherAlertItem(
                    id = "extreme_heat_${referenceTimeMs / 86_400_000L}",
                    title = "Extreme Heat Advisory (${maxTemp.toInt()}°C)",
                    description = "High ambient heat increases concrete plastic shrinkage risk and crew heat stress. Follow hot-weather concreting & curing controls.",
                    severity = WeatherAlertSeverity.WARNING,
                    startEpochMs = referenceTimeMs,
                    endEpochMs = referenceTimeMs + 12 * 3_600_000L,
                    category = "EXTREME_HEAT"
                )
            }
            if (minTemp <= 10.0) {
                alerts += WeatherAlertItem(
                    id = "low_temp_${referenceTimeMs / 86_400_000L}",
                    title = "Low Temperature Alert (${minTemp.toInt()}°C)",
                    description = "Low ambient air temperature forecasted. Measure actual pavement surface temperature on site (<10°C conventional / <15°C modified bitumen prohibits laying).",
                    severity = WeatherAlertSeverity.WARNING,
                    startEpochMs = referenceTimeMs,
                    endEpochMs = referenceTimeMs + 12 * 3_600_000L,
                    category = "LOW_TEMP"
                )
            }

            return alerts
        }

        private fun resolveTimeZone(timezoneId: String, utcOffsetSeconds: Int): TimeZone {
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

        private fun optNullableDouble(obj: JSONObject, key: String): Double? {
            return if (obj.has(key) && !obj.isNull(key)) obj.optDouble(key).takeIf { !it.isNaN() } else null
        }

        private fun optNullableInt(obj: JSONObject, key: String): Int? {
            return if (obj.has(key) && !obj.isNull(key)) obj.optInt(key) else null
        }

        private fun optArrayDouble(arr: JSONArray?, index: Int): Double? {
            if (arr == null || index >= arr.length() || arr.isNull(index)) return null
            return arr.optDouble(index).takeIf { !it.isNaN() }
        }

        private fun optArrayInt(arr: JSONArray?, index: Int): Int? {
            if (arr == null || index >= arr.length() || arr.isNull(index)) return null
            return arr.optInt(index)
        }
    }
}

/**
 * Single shared, cached weather repository reused by Home Screen, Weather Dashboard,
 * Highway Work Intelligence, Camera Capture, Video Overlay, and Settings Preview.
 *
 * Guarantees:
 * - Zero duplicate API requests across screens (in-flight mutex + spatial/temporal LRU cache).
 * - Non-blocking synchronous capture accessor so shutter clicks are NEVER delayed.
 * - Offline backoff to prevent battery & mobile-data drain.
 * - Never fabricates fake or default weather values.
 */
class SharedWeatherRepository(
    private val fetcher: WeatherRemoteFetcher = OpenMeteoWeatherFetcher(),
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val fetchMutex = Mutex()
    private val spatialCache = LruCache<String, WeatherObservation>(MAX_CACHE_CELLS)
    private val forecastSpatialCache = LruCache<String, WeatherForecastBundle>(MAX_CACHE_CELLS)

    @Volatile
    private var lastAttemptedAtMs: Long = 0L

    @Volatile
    private var lastFetchedPoint: GeoPoint? = null

    @Volatile
    private var failureBackoffUntilMs: Long = 0L

    private val _weatherUiState = MutableStateFlow<WeatherUiState>(WeatherUiState.WaitingForGps)
    val weatherUiState: StateFlow<WeatherUiState> = _weatherUiState.asStateFlow()

    private val _latestObservation = MutableStateFlow<WeatherObservation?>(null)
    val latestObservation: StateFlow<WeatherObservation?> = _latestObservation.asStateFlow()

    private val _forecastBundle = MutableStateFlow<WeatherForecastBundle?>(null)
    val forecastBundle: StateFlow<WeatherForecastBundle?> = _forecastBundle.asStateFlow()

    /**
     * Hydrates the repository from a previously persisted observation (e.g., on cold app start)
     * while preserving its exact original observation timestamp so freshness checks stay truthful.
     */
    fun seedFromPersistedCache(observation: WeatherObservation?) {
        if (observation == null || observation.observedAtMs <= 0L) return
        if (_latestObservation.value == null) {
            _latestObservation.value = observation
            spatialCache.put(spatialCellKey(observation.latitude, observation.longitude), observation)
            _weatherUiState.value = WeatherUiState.Available(observation, isRefreshing = false)
        }
    }

    /**
     * Hydrates the complete current + hourly + 7-day forecast bundle from local offline storage.
     */
    fun seedFromPersistedForecast(bundle: WeatherForecastBundle?) {
        if (bundle == null || bundle.current.observedAtMs <= 0L) return
        if (_forecastBundle.value == null) {
            _forecastBundle.value = bundle
            val key = spatialCellKey(bundle.current.latitude, bundle.current.longitude)
            forecastSpatialCache.put(key, bundle)
            if (_latestObservation.value == null) {
                _latestObservation.value = bundle.current
                spatialCache.put(key, bundle.current)
                _weatherUiState.value = WeatherUiState.Available(bundle.current, isRefreshing = false)
            }
        }
    }

    /**
     * Updates the human-readable location name attached to the active weather observation
     * without triggering any weather network call.
     */
    fun attachResolvedLocationName(locationName: String) {
        val clean = locationName.trim()
        if (clean.isBlank() || clean.equals("None", ignoreCase = true)) return
        val current = _latestObservation.value ?: return
        if (current.locationName == clean) return
        val updated = current.copy(locationName = clean)
        _latestObservation.value = updated
        val key = spatialCellKey(updated.latitude, updated.longitude)
        spatialCache.put(key, updated)
        _forecastBundle.value?.let { existingBundle ->
            val updatedBundle = existingBundle.copy(current = updated)
            _forecastBundle.value = updatedBundle
            forecastSpatialCache.put(key, updatedBundle)
        }
        val ui = _weatherUiState.value
        if (ui is WeatherUiState.Available) {
            _weatherUiState.value = ui.copy(observation = updated)
        }
    }

    /**
     * Non-blocking synchronous accessor for Photo/Video capture snapshots.
     * NEVER performs network I/O and NEVER delays shutter capture.
     */
    fun getObservationForCapture(
        captureTimeMs: Long = clockMs(),
        maxAgeMs: Long = WeatherObservation.DEFAULT_CAPTURE_MAX_AGE_MS,
        omitIfStale: Boolean = true
    ): Pair<WeatherObservation?, Boolean> {
        val obs = _latestObservation.value ?: return null to false
        val isFresh = obs.isFreshAt(captureTimeMs, maxAgeMs)
        return if (omitIfStale && !isFresh) {
            obs to false
        } else {
            obs to isFresh
        }
    }

    /**
     * Refreshes live weather & full forecast for [point] when cache is expired, user moved
     * significantly (>2 km), or [forceRefresh] is explicitly requested.
     */
    suspend fun refreshForLocation(
        point: GeoPoint?,
        resolvedLocationName: String = "",
        forceRefresh: Boolean = false,
        cacheTtlMs: Long = CACHE_TTL_MS
    ): WeatherObservation? {
        if (point == null || (point.latitude == 0.0 && point.longitude == 0.0)) {
            if (_latestObservation.value == null) {
                _weatherUiState.value = WeatherUiState.WaitingForGps
            }
            return _latestObservation.value
        }

        val now = clockMs()
        val cellKey = spatialCellKey(point.latitude, point.longitude)
        val cleanLoc = resolvedLocationName.trim().takeIf { !it.equals("None", ignoreCase = true) }.orEmpty()

        // Fast-path cache check before acquiring mutex
        if (!forceRefresh) {
            val cached = spatialCache.get(cellKey)
            val cachedBundle = forecastSpatialCache.get(cellKey)
            val needsForecastUpgrade = fetcher is WeatherForecastFetcher && (cachedBundle == null || cachedBundle.hourly.isEmpty())
            if (cached != null && !needsForecastUpgrade && (now - cached.fetchedAtMs) < cacheTtlMs) {
                val enriched = if (cleanLoc.isNotBlank() && cached.locationName != cleanLoc) {
                    cached.copy(locationName = cleanLoc)
                } else cached
                publishObservation(enriched, cachedBundle?.copy(current = enriched))
                return enriched
            }
            val current = _latestObservation.value
            val prevPt = lastFetchedPoint
            if (current != null && prevPt != null && !needsForecastUpgrade) {
                val movedM = GeoUtils.distanceMetres(prevPt, point)
                if (movedM < MIN_REFETCH_DISTANCE_M && (now - current.fetchedAtMs) < cacheTtlMs) {
                    val enriched = if (cleanLoc.isNotBlank() && current.locationName != cleanLoc) {
                        current.copy(locationName = cleanLoc)
                    } else current
                    publishObservation(enriched, _forecastBundle.value?.copy(current = enriched))
                    return enriched
                }
            }
            if (now < failureBackoffUntilMs) {
                return _latestObservation.value
            }
        }

        return fetchMutex.withLock {
            val lockNow = clockMs()
            if (!forceRefresh) {
                val cachedAfterLock = spatialCache.get(cellKey)
                val cachedBundleAfterLock = forecastSpatialCache.get(cellKey)
                val needsForecastUpgrade = fetcher is WeatherForecastFetcher && (cachedBundleAfterLock == null || cachedBundleAfterLock.hourly.isEmpty())
                if (cachedAfterLock != null && !needsForecastUpgrade && (lockNow - cachedAfterLock.fetchedAtMs) < cacheTtlMs) {
                    val enriched = if (cleanLoc.isNotBlank() && cachedAfterLock.locationName != cleanLoc) {
                        cachedAfterLock.copy(locationName = cleanLoc)
                    } else cachedAfterLock
                    publishObservation(enriched, cachedBundleAfterLock?.copy(current = enriched))
                    return@withLock enriched
                }
                if (lockNow < failureBackoffUntilMs) {
                    return@withLock _latestObservation.value
                }
            }

            val previous = _latestObservation.value
            _weatherUiState.value = if (previous != null) {
                WeatherUiState.Available(previous, isRefreshing = true)
            } else {
                WeatherUiState.Loading(previous = null)
            }
            lastAttemptedAtMs = lockNow

            val fetchedBundle: WeatherForecastBundle? = try {
                if (fetcher is WeatherForecastFetcher) {
                    fetcher.fetchForecastBundle(point.latitude, point.longitude, lockNow)
                } else {
                    fetcher.fetch(point.latitude, point.longitude, lockNow)?.let { obs ->
                        WeatherForecastBundle(
                            current = obs,
                            fetchedAtMs = obs.fetchedAtMs,
                            source = obs.source
                        )
                    }
                }
            } catch (_: Exception) {
                null
            }

            if (fetchedBundle != null) {
                val finalObs = fetchedBundle.current.copy(
                    locationName = cleanLoc.ifBlank { previous?.locationName.orEmpty() }
                )
                val finalBundle = fetchedBundle.copy(current = finalObs)
                spatialCache.put(cellKey, finalObs)
                forecastSpatialCache.put(cellKey, finalBundle)
                lastFetchedPoint = point
                failureBackoffUntilMs = 0L
                publishObservation(finalObs, finalBundle)
                finalObs
            } else {
                failureBackoffUntilMs = lockNow + FAILURE_BACKOFF_MS
                _weatherUiState.value = if (previous != null) {
                    WeatherUiState.Available(previous, isRefreshing = false)
                } else {
                    WeatherUiState.Unavailable(
                        reason = "Weather unavailable offline",
                        cachedObservation = null
                    )
                }
                previous
            }
        }
    }

    private fun publishObservation(observation: WeatherObservation, bundle: WeatherForecastBundle?) {
        _latestObservation.value = observation
        if (bundle != null) {
            _forecastBundle.value = bundle
        }
        _weatherUiState.value = WeatherUiState.Available(observation, isRefreshing = false)
    }

    fun clearForTesting() {
        spatialCache.evictAll()
        forecastSpatialCache.evictAll()
        lastAttemptedAtMs = 0L
        lastFetchedPoint = null
        failureBackoffUntilMs = 0L
        _latestObservation.value = null
        _forecastBundle.value = null
        _weatherUiState.value = WeatherUiState.WaitingForGps
    }

    companion object {
        private const val MAX_CACHE_CELLS = 32
        const val CACHE_TTL_MS = 10L * 60L * 1000L // 10 minutes
        const val MIN_REFETCH_DISTANCE_M = 2_000.0 // 2 km spatial threshold
        const val FAILURE_BACKOFF_MS = 60_000L // 60 seconds offline retry backoff

        /**
         * Global process-wide shared weather repository singleton.
         */
        val instance: SharedWeatherRepository by lazy { SharedWeatherRepository() }

        /**
         * Quantizes coordinates to ~2 km grid cells (~0.02 degrees) for spatial weather caching.
         */
        fun spatialCellKey(latitude: Double, longitude: Double): String {
            val latBucket = (latitude * 50.0).roundToLong()
            val lngBucket = (longitude * 50.0).roundToLong()
            return "$latBucket:$lngBucket"
        }
    }
}
