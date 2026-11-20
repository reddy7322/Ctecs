package com.example.weather

import android.util.Log
import android.util.LruCache
import com.example.geo.GeoUtils
import com.example.model.GeoPoint
import com.example.model.WeatherConditionType
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
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * Network fetcher abstraction allowing deterministic test injection without real network calls.
 */
fun interface WeatherRemoteFetcher {
    suspend fun fetch(latitude: Double, longitude: Double, nowMs: Long): WeatherObservation?
}

/**
 * Authoritative live weather client using Open-Meteo (free, global, high-accuracy, keyless).
 */
class OpenMeteoWeatherFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
) : WeatherRemoteFetcher {

    override suspend fun fetch(latitude: Double, longitude: Double, nowMs: Long): WeatherObservation? =
        withContext(Dispatchers.IO) {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,wind_direction_10m" +
                    "&wind_speed_unit=kmh&timeformat=unixtime&timezone=auto",
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
                    parseOpenMeteoCurrentResponse(
                        jsonBody = body,
                        latitude = latitude,
                        longitude = longitude,
                        fetchedAtMs = nowMs
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Weather fetch failed: ${e.message}")
                null
            }
        }

    companion object {
        private const val TAG = "OpenMeteoWeatherFetcher"

        fun parseOpenMeteoCurrentResponse(
            jsonBody: String,
            latitude: Double,
            longitude: Double,
            fetchedAtMs: Long
        ): WeatherObservation? {
            return try {
                val root = JSONObject(jsonBody)
                val current = root.optJSONObject("current") ?: return null
                if (!current.has("temperature_2m") || current.isNull("temperature_2m")) return null

                val tempC = current.getDouble("temperature_2m")
                val feelsLikeC = if (current.has("apparent_temperature") && !current.isNull("apparent_temperature")) {
                    current.getDouble("apparent_temperature")
                } else {
                    tempC
                }
                val humidity = current.optInt("relative_humidity_2m", 0).coerceIn(0, 100)
                val windSpeedKmh = current.optDouble("wind_speed_10m", 0.0).coerceAtLeast(0.0)
                val windDirDeg = current.optInt("wind_direction_10m", 0)
                val wmoCode = current.optInt("weather_code", 0)
                val isDay = current.optInt("is_day", 1) == 1
                val apiTimeSec = current.optLong("time", 0L)
                val observedAtMs = if (apiTimeSec > 1_000_000_000L) apiTimeSec * 1000L else fetchedAtMs
                val (condType, condLabel) = WeatherConditionType.fromWmoCode(wmoCode, isDay)

                WeatherObservation(
                    temperatureC = tempC,
                    feelsLikeC = feelsLikeC,
                    humidityPercent = humidity,
                    windSpeedKmh = windSpeedKmh,
                    windDirectionDegrees = ((windDirDeg % 360) + 360) % 360,
                    condition = condType,
                    conditionLabel = condLabel,
                    wmoCode = wmoCode,
                    isDay = isDay,
                    latitude = latitude,
                    longitude = longitude,
                    observedAtMs = observedAtMs,
                    fetchedAtMs = fetchedAtMs,
                    source = WeatherObservation.DEFAULT_SOURCE
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing Open-Meteo JSON: ${e.message}")
                null
            }
        }
    }
}

/**
 * Single shared, cached weather repository reused by Home Screen, Camera Capture,
 * Video Overlay, and Settings Preview.
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
        spatialCache.put(spatialCellKey(updated.latitude, updated.longitude), updated)
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
     * Refreshes live weather for [point] when cache is expired, user moved significantly (>2 km),
     * or [forceRefresh] is explicitly requested by user tap.
     */
    suspend fun refreshForLocation(
        point: GeoPoint?,
        resolvedLocationName: String = "",
        forceRefresh: Boolean = false
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
            if (cached != null && (now - cached.fetchedAtMs) < CACHE_TTL_MS) {
                val enriched = if (cleanLoc.isNotBlank() && cached.locationName != cleanLoc) {
                    cached.copy(locationName = cleanLoc)
                } else cached
                publishObservation(enriched)
                return enriched
            }
            val current = _latestObservation.value
            val prevPt = lastFetchedPoint
            if (current != null && prevPt != null) {
                val movedM = GeoUtils.distanceMetres(prevPt, point)
                if (movedM < MIN_REFETCH_DISTANCE_M && (now - current.fetchedAtMs) < CACHE_TTL_MS) {
                    val enriched = if (cleanLoc.isNotBlank() && current.locationName != cleanLoc) {
                        current.copy(locationName = cleanLoc)
                    } else current
                    publishObservation(enriched)
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
                if (cachedAfterLock != null && (lockNow - cachedAfterLock.fetchedAtMs) < CACHE_TTL_MS) {
                    val enriched = if (cleanLoc.isNotBlank() && cachedAfterLock.locationName != cleanLoc) {
                        cachedAfterLock.copy(locationName = cleanLoc)
                    } else cachedAfterLock
                    publishObservation(enriched)
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

            val fetched = try {
                fetcher.fetch(point.latitude, point.longitude, lockNow)
            } catch (_: Exception) {
                null
            }

            if (fetched != null) {
                val finalObs = fetched.copy(
                    locationName = cleanLoc.ifBlank { previous?.locationName.orEmpty() }
                )
                spatialCache.put(cellKey, finalObs)
                lastFetchedPoint = point
                failureBackoffUntilMs = 0L
                publishObservation(finalObs)
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

    private fun publishObservation(observation: WeatherObservation) {
        _latestObservation.value = observation
        _weatherUiState.value = WeatherUiState.Available(observation, isRefreshing = false)
    }

    fun clearForTesting() {
        spatialCache.evictAll()
        lastAttemptedAtMs = 0L
        lastFetchedPoint = null
        failureBackoffUntilMs = 0L
        _latestObservation.value = null
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
