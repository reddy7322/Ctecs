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

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @Volatile
    private var isTracking = false

    private val lastCallbackMillis = AtomicLong(0L)
    private val stallRecoveries = AtomicLong(0L)

    companion object {
        const val DEFAULT_GPS_FRESH_AGE_MS = 20_000L

        fun isValidCoordinate(lat: Double, lng: Double): Boolean {
            if (lat.isNaN() || lng.isNaN() || lat.isInfinite() || lng.isInfinite()) return false
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return false
            if (lat == 0.0 && lng == 0.0) return false
            return true
        }
    }

    fun getStallRecoveriesCount(): Long = stallRecoveries.get()

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
        if (_locationState.value.point == null) {
            _locationState.value = _locationState.value.copy(status = LocationStatus.ACQUIRING)
        }

        registerLocationCallbacks()
    }

    @SuppressLint("MissingPermission")
    private fun registerLocationCallbacks() {
        try {
            // Setup continuous high-accuracy fused location request with 0m min distance
            // so OEM stationary sleep filters never freeze the location stream.
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()

            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnFailureListener {
                    startLegacyFallback()
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
            startLegacyFallback()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLegacyFallback() {
        try {
            val provider = if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }
            locationManager?.requestLocationUpdates(
                provider,
                1000L,
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

    /**
     * Automatic watchdog check: if tracking is active and no location callback has arrived
     * within the stall threshold (e.g. after provider transition or temporary GPS drop),
     * automatically re-requests an immediate fix and re-binds the listener without app restart.
     */
    @SuppressLint("MissingPermission")
    fun checkAndRecoverIfStale(stallThresholdMs: Long = 8_000L): Boolean {
        if (!isTracking) return false
        val lastMs = lastCallbackMillis.get()
        val now = System.currentTimeMillis()
        if (lastMs > 0L && (now - lastMs) > stallThresholdMs) {
            stallRecoveries.incrementAndGet()
            lastCallbackMillis.set(now)
            try {
                fusedClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) {}
            registerLocationCallbacks()
            forceRefreshLocation()
            return true
        }
        return false
    }

    fun stopTracking() {
        if (!isTracking) return
        try {
            fusedClient.removeLocationUpdates(locationCallback)
            locationManager?.removeUpdates(legacyListener)
        } catch (ignored: Exception) {}
        isTracking = false
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

        // If tracking is active and listener looks stale, rebind cleanly without creating duplicate listeners
        if (isTracking) {
            val lastCb = lastCallbackMillis.get()
            if (lastCb > 0L && (System.currentTimeMillis() - lastCb) > maxFreshAgeMs) {
                try {
                    fusedClient.removeLocationUpdates(locationCallback)
                } catch (_: Exception) {}
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
