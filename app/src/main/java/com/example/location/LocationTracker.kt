package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.example.model.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

data class GpsValidationResult(
    val isValid: Boolean,
    val isFresh: Boolean,
    val fixAgeMs: Long,
    val reason: String
)

enum class LocationStatus {
    IDLE,
    ACQUIRING,
    READY,
    POOR_ACCURACY,
    PERMISSION_DENIED,
    DISABLED
}

data class LocationState(
    val point: GeoPoint? = null,
    val accuracy: Float = 0f,
    val bearing: Float? = null,
    val speed: Float? = null,
    val altitude: Double = 0.0,
    val status: LocationStatus = LocationStatus.IDLE,
    val lastFixTime: Long = 0L
) {
    val isUsable: Boolean
        get() = point != null && (status == LocationStatus.READY || status == LocationStatus.POOR_ACCURACY)
}

class LocationTracker(private val context: Context) {

    private val _locationState = MutableStateFlow(LocationState())
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @Volatile
    private var isTracking = false

    @Volatile
    private var highFrequencyMode = false

    @Volatile
    private var isStationaryMode = false

    @Volatile
    private var currentIntervalMs = ACTIVE_INTERVAL_MS

    private var stationaryAnchorLat: Double? = null
    private var stationaryAnchorLng: Double? = null
    private var stationarySinceMs: Long = 0L

    private val lastCallbackMillis = AtomicLong(0L)
    private val stallRecoveries = AtomicLong(0L)

    companion object {
        const val DEFAULT_GPS_FRESH_AGE_MS = 20_000L
        const val ACTIVE_INTERVAL_MS = 1_000L
        const val ACTIVE_MIN_INTERVAL_MS = 500L
        const val STATIONARY_INTERVAL_MS = 3_500L
        const val STATIONARY_MIN_INTERVAL_MS = 1_500L
        const val STATIONARY_DWELL_MS = 30_000L
        const val STATIONARY_SPEED_THRESHOLD_MPS = 0.5f
        const val MOVEMENT_WAKE_SPEED_MPS = 0.8f
        const val STATIONARY_DISPLACEMENT_METRES = 4.0

        fun isValidCoordinate(lat: Double, lng: Double): Boolean {
            if (lat.isNaN() || lng.isNaN() || lat.isInfinite() || lng.isInfinite()) return false
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return false
            if (lat == 0.0 && lng == 0.0) return false
            return true
        }
    }

    fun getStallRecoveriesCount(): Long = stallRecoveries.get()

    fun getCurrentPollingIntervalMs(): Long = currentIntervalMs

    fun isStationaryPowerSaveActive(): Boolean = isStationaryMode

    fun isTrackingActive(): Boolean = isTracking

    /**
     * Forces active 1Hz polling during precision capture/survey screens.
     * When disabled, allows automatic stationary step-down to save battery while vehicle is parked.
     */
    fun setHighFrequencyMode(enabled: Boolean) {
        if (highFrequencyMode == enabled) return
        highFrequencyMode = enabled
        if (enabled && isStationaryMode) {
            switchPollingInterval(stationary = false)
        }
    }

    fun validateLocationState(
        state: LocationState = _locationState.value,
        maxFreshAgeMs: Long = DEFAULT_GPS_FRESH_AGE_MS,
        nowMs: Long = System.currentTimeMillis()
    ): GpsValidationResult {
        val pt = state.point
        if (pt == null) {
            return GpsValidationResult(
                isValid = false,
                isFresh = false,
                fixAgeMs = -1L,
                reason = "No GPS fix available"
            )
        }
        if (!isValidCoordinate(pt.latitude, pt.longitude)) {
            return GpsValidationResult(
                isValid = false,
                isFresh = false,
                fixAgeMs = -1L,
                reason = "Invalid GPS coordinates"
            )
        }
        if (state.accuracy.isNaN() || state.accuracy < 0f) {
            return GpsValidationResult(
                isValid = false,
                isFresh = false,
                fixAgeMs = -1L,
                reason = "Invalid GPS accuracy"
            )
        }
        val fixAgeMs = if (state.lastFixTime > 0L) (nowMs - state.lastFixTime).coerceAtLeast(0L) else Long.MAX_VALUE
        val isFresh = state.lastFixTime > 0L && fixAgeMs <= maxFreshAgeMs
        val statusReason = when {
            state.status == LocationStatus.PERMISSION_DENIED -> "Location permission denied"
            state.status == LocationStatus.DISABLED -> "GPS provider disabled"
            !isFresh -> "Stale GPS fix (${fixAgeMs / 1000L}s old)"
            state.accuracy > 25f -> "Low GPS accuracy (±${state.accuracy.toInt()}m)"
            else -> "GPS fix valid (±${state.accuracy.toInt()}m)"
        }
        return GpsValidationResult(
            isValid = true,
            isFresh = isFresh && state.status != LocationStatus.PERMISSION_DENIED && state.status != LocationStatus.DISABLED,
            fixAgeMs = fixAgeMs,
            reason = statusReason
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: result.locations.lastOrNull()
            if (loc != null) {
                handleNewLocation(loc)
            }
        }
    }

    private val legacyListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isTracking) {
            checkAndRecoverIfStale()
            return
        }
        resetStationaryState()
        if (_locationState.value.point == null) {
            _locationState.value = _locationState.value.copy(status = LocationStatus.ACQUIRING)
        }

        registerLocationCallbacks()
    }

    @SuppressLint("MissingPermission")
    private fun removeAllLocationListeners() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        try {
            locationManager?.removeUpdates(legacyListener)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun registerLocationCallbacks() {
        val intervalMs = if (isStationaryMode && !highFrequencyMode) STATIONARY_INTERVAL_MS else ACTIVE_INTERVAL_MS
        val minIntervalMs = if (isStationaryMode && !highFrequencyMode) STATIONARY_MIN_INTERVAL_MS else ACTIVE_MIN_INTERVAL_MS
        currentIntervalMs = intervalMs
        try {
            // Setup continuous high-accuracy fused location request with 0m min distance
            // so OEM stationary sleep filters never freeze the location stream.
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(minIntervalMs)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()

            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnFailureListener {
                    startLegacyFallback(intervalMs)
                }

            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && _locationState.value.point == null) {
                    handleNewLocation(loc)
                }
            }

            isTracking = true
            if (lastCallbackMillis.get() == 0L) {
                lastCallbackMillis.set(System.currentTimeMillis())
            }
        } catch (e: SecurityException) {
            _locationState.value = _locationState.value.copy(status = LocationStatus.PERMISSION_DENIED)
        } catch (e: Exception) {
            startLegacyFallback(intervalMs)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLegacyFallback(intervalMs: Long = ACTIVE_INTERVAL_MS) {
        try {
            val provider = if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }
            locationManager?.requestLocationUpdates(
                provider,
                intervalMs,
                0f,
                legacyListener,
                Looper.getMainLooper()
            )
            isTracking = true
            if (lastCallbackMillis.get() == 0L) {
                lastCallbackMillis.set(System.currentTimeMillis())
            }
        } catch (e: Exception) {
            _locationState.value = _locationState.value.copy(status = LocationStatus.DISABLED)
        }
    }

    private fun resetStationaryState() {
        isStationaryMode = false
        currentIntervalMs = ACTIVE_INTERVAL_MS
        stationaryAnchorLat = null
        stationaryAnchorLng = null
        stationarySinceMs = 0L
    }

    private fun switchPollingInterval(stationary: Boolean) {
        val targetStationary = stationary && !highFrequencyMode
        val targetInterval = if (targetStationary) STATIONARY_INTERVAL_MS else ACTIVE_INTERVAL_MS
        if (isStationaryMode == targetStationary && currentIntervalMs == targetInterval) return
        isStationaryMode = targetStationary
        currentIntervalMs = targetInterval
        if (isTracking) {
            removeAllLocationListeners()
            registerLocationCallbacks()
        }
    }

    internal fun evaluateAdaptivePolling(lat: Double, lng: Double, speedMps: Float?, nowMs: Long) {
        if (highFrequencyMode) {
            if (isStationaryMode) switchPollingInterval(stationary = false)
            stationaryAnchorLat = lat
            stationaryAnchorLng = lng
            stationarySinceMs = nowMs
            return
        }

        val anchorLat = stationaryAnchorLat
        val anchorLng = stationaryAnchorLng
        val movedMetres = if (anchorLat != null && anchorLng != null) {
            com.example.geo.GeoUtils.distanceMetres(GeoPoint(anchorLat, anchorLng), GeoPoint(lat, lng))
        } else {
            Double.MAX_VALUE
        }

        val isMovingFast = (speedMps != null && speedMps >= MOVEMENT_WAKE_SPEED_MPS) ||
            (anchorLat != null && movedMetres >= STATIONARY_DISPLACEMENT_METRES)

        if (anchorLat == null || isMovingFast) {
            stationaryAnchorLat = lat
            stationaryAnchorLng = lng
            stationarySinceMs = nowMs
            if (isStationaryMode) {
                switchPollingInterval(stationary = false)
            }
            return
        }

        val isSlow = speedMps == null || speedMps < STATIONARY_SPEED_THRESHOLD_MPS
        if (isSlow && movedMetres < STATIONARY_DISPLACEMENT_METRES) {
            if (stationarySinceMs <= 0L) {
                stationarySinceMs = nowMs
            } else if (!isStationaryMode && (nowMs - stationarySinceMs) >= STATIONARY_DWELL_MS) {
                switchPollingInterval(stationary = true)
            }
        } else {
            stationaryAnchorLat = lat
            stationaryAnchorLng = lng
            stationarySinceMs = nowMs
        }
    }

    /**
     * Automatic watchdog check: if tracking is active and no location callback has arrived
     * within the stall threshold (e.g. after provider transition or temporary GPS drop),
     * automatically re-requests an immediate fix and re-binds the listener without app restart.
     */
    @SuppressLint("MissingPermission")
    fun checkAndRecoverIfStale(stallThresholdMs: Long = 8_000L): Boolean {
        if (!isTracking) return false
        val effectiveThreshold = maxOf(stallThresholdMs, currentIntervalMs * 2L + 4_000L)
        val lastMs = lastCallbackMillis.get()
        val now = System.currentTimeMillis()
        if (lastMs > 0L && (now - lastMs) > effectiveThreshold) {
            stallRecoveries.incrementAndGet()
            lastCallbackMillis.set(now)
            resetStationaryState()
            removeAllLocationListeners()
            registerLocationCallbacks()
            forceRefreshLocation()
            return true
        }
        return false
    }

    fun stopTracking() {
        if (!isTracking) return
        removeAllLocationListeners()
        isTracking = false
        resetStationaryState()
        _locationState.value = _locationState.value.copy(status = LocationStatus.IDLE)
    }

    private fun handleNewLocation(loc: Location) {
        if (!isValidCoordinate(loc.latitude, loc.longitude)) return
        val now = System.currentTimeMillis()
        lastCallbackMillis.set(now)
        val accuracy = if (loc.accuracy.isNaN() || loc.accuracy < 0f) 0f else loc.accuracy
        val status = if (accuracy <= 15.0f) LocationStatus.READY else LocationStatus.POOR_ACCURACY
        val point = GeoPoint(loc.latitude, loc.longitude, loc.altitude)
        val validBearing = if (loc.hasBearing() && !loc.bearing.isNaN()) loc.bearing else null
        val validSpeed = if (loc.hasSpeed() && !loc.speed.isNaN() && loc.speed >= 0f) loc.speed else null

        evaluateAdaptivePolling(loc.latitude, loc.longitude, validSpeed, now)

        _locationState.value = LocationState(
            point = point,
            accuracy = accuracy,
            bearing = validBearing,
            speed = validSpeed,
            altitude = loc.altitude,
            status = status,
            lastFixTime = now
        )
    }

    /**
     * Forces an immediate high-accuracy GPS fix update.
     */
    @SuppressLint("MissingPermission")
    fun forceRefreshLocation(onComplete: ((Boolean) -> Unit)? = null) {
        if (isStationaryMode) {
            switchPollingInterval(stationary = false)
        }
        try {
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null && isValidCoordinate(loc.latitude, loc.longitude)) {
                        handleNewLocation(loc)
                        onComplete?.invoke(true)
                    } else {
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null && isValidCoordinate(lastLoc.latitude, lastLoc.longitude)) {
                                handleNewLocation(lastLoc)
                                onComplete?.invoke(true)
                            } else {
                                onComplete?.invoke(false)
                            }
                        }.addOnFailureListener {
                            onComplete?.invoke(false)
                        }
                    }
                }
                .addOnFailureListener {
                    onComplete?.invoke(false)
                }
        } catch (e: Exception) {
            onComplete?.invoke(false)
        }
    }

    /**
     * Coroutine-friendly GPS refresh with bounded timeout and stale-listener recovery.
     * Returns true if a valid fresh fix was obtained or confirmed, false otherwise.
     * Never fabricates GPS coordinates on failure or timeout.
     */
    @SuppressLint("MissingPermission")
    suspend fun refreshGpsAsync(
        forceNewFix: Boolean = true,
        timeoutMs: Long = 3_500L,
        maxFreshAgeMs: Long = DEFAULT_GPS_FRESH_AGE_MS
    ): Boolean {
        val currentValidation = validateLocationState(maxFreshAgeMs = maxFreshAgeMs)
        if (!forceNewFix && currentValidation.isValid && currentValidation.isFresh) {
            return true
        }

        if (isStationaryMode) {
            switchPollingInterval(stationary = false)
        }

        // If tracking is active and listener looks stale, rebind cleanly without creating duplicate listeners
        if (isTracking) {
            val lastCb = lastCallbackMillis.get()
            if (lastCb > 0L && (System.currentTimeMillis() - lastCb) > maxFreshAgeMs) {
                removeAllLocationListeners()
                registerLocationCallbacks()
            }
        }

        val freshFixObtained = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont: CancellableContinuation<Boolean> ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation {
                    try { cts.cancel() } catch (_: Exception) {}
                }
                try {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc ->
                            if (loc != null && isValidCoordinate(loc.latitude, loc.longitude)) {
                                handleNewLocation(loc)
                                if (cont.isActive) cont.resume(true)
                            } else {
                                fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                                    if (lastLoc != null && isValidCoordinate(lastLoc.latitude, lastLoc.longitude)) {
                                        handleNewLocation(lastLoc)
                                        if (cont.isActive) cont.resume(true)
                                    } else {
                                        if (cont.isActive) cont.resume(false)
                                    }
                                }.addOnFailureListener {
                                    if (cont.isActive) cont.resume(false)
                                }
                            }
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(false)
                        }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(false)
                }
            }
        }

        if (freshFixObtained == true) return true

        // If getCurrentLocation timed out (e.g. unit tests or indoor sensor), accept existing valid fresh fix if available
        val postValidation = validateLocationState(maxFreshAgeMs = maxFreshAgeMs)
        return postValidation.isValid && postValidation.isFresh
    }

    /**
     * Injects synthetic location (used for testing or emulator field simulation).
     */
    fun injectLocation(lat: Double, lng: Double, accuracy: Float = 2.5f, altitude: Double = 270.0) {
        val now = System.currentTimeMillis()
        lastCallbackMillis.set(now)
        val point = GeoPoint(lat, lng, altitude)
        _locationState.value = LocationState(
            point = point,
            accuracy = accuracy,
            altitude = altitude,
            status = LocationStatus.READY,
            lastFixTime = now
        )
    }
}
