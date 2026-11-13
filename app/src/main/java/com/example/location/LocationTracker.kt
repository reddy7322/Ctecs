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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

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

    fun getStallRecoveriesCount(): Long = stallRecoveries.get()

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
        val now = System.currentTimeMillis()
        lastCallbackMillis.set(now)
        val accuracy = loc.accuracy
        val status = if (accuracy <= 15.0f) LocationStatus.READY else LocationStatus.POOR_ACCURACY
        val point = GeoPoint(loc.latitude, loc.longitude, loc.altitude)

        _locationState.value = LocationState(
            point = point,
            accuracy = accuracy,
            bearing = if (loc.hasBearing()) loc.bearing else null,
            speed = if (loc.hasSpeed()) loc.speed else null,
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
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        handleNewLocation(loc)
                        onComplete?.invoke(true)
                    } else {
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
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
