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

    private var isTracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleNewLocation(it) }
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
        if (isTracking) return
        _locationState.value = _locationState.value.copy(status = LocationStatus.ACQUIRING)

        try {
            // Setup high-accuracy fused location request
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0.5f)
                .build()

            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnFailureListener {
                    // Fallback to LocationManager GPS
                    startLegacyFallback()
                }

            // Also request last known location immediately
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && _locationState.value.point == null) {
                    handleNewLocation(loc)
                }
            }

            isTracking = true
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
                0.5f,
                legacyListener,
                Looper.getMainLooper()
            )
            isTracking = true
        } catch (e: Exception) {
            _locationState.value = _locationState.value.copy(status = LocationStatus.DISABLED)
        }
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
            lastFixTime = System.currentTimeMillis()
        )
    }

    /**
     * Injects synthetic location (used for testing or emulator field simulation).
     */
    fun injectLocation(lat: Double, lng: Double, accuracy: Float = 2.5f, altitude: Double = 270.0) {
        val point = GeoPoint(lat, lng, altitude)
        _locationState.value = LocationState(
            point = point,
            accuracy = accuracy,
            altitude = altitude,
            status = LocationStatus.READY,
            lastFixTime = System.currentTimeMillis()
        )
    }
}
