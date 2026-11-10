package com.example.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Intelligent Reverse Geocoding and Location Name Engine for CTECS.
 * Automatically resolves GPS coordinates into concise, meaningful location names
 * (e.g. "Dehradun, Uttarakhand" or "Rehmadpur, Roorkee"), avoiding long postal addresses.
 * Features smart spatial caching to prevent unnecessary network and battery consumption.
 */
object LocationNameResolver {

    private const val TAG = "LocationNameResolver"

    // In-memory cache keyed by ~100m coordinate grid cells (lat*1000, lng*1000)
    private val locationCache = ConcurrentHashMap<Pair<Long, Long>, String>()

    @Volatile
    private var lastResolvedLat: Double? = null
    @Volatile
    private var lastResolvedLng: Double? = null
    @Volatile
    private var lastResolvedName: String = ""
    @Volatile
    private var lastResolveTimeMs: Long = 0L

    /**
     * Resolves the current location name from GPS coordinates.
     * Checks in-memory cache first, then performs intelligent reverse geocoding on IO dispatcher.
     */
    suspend fun resolveLocationName(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String = withContext(Dispatchers.IO) {
        if (latitude == 0.0 && longitude == 0.0) return@withContext ""

        // 1. Spatial cache check: if moved less than 100m from last resolution, reuse immediately
        val lastLat = lastResolvedLat
        val lastLng = lastResolvedLng
        if (lastLat != null && lastLng != null && lastResolvedName.isNotBlank()) {
            val dist = calculateDistanceMeters(latitude, longitude, lastLat, lastLng)
            if (dist < 100.0) {
                return@withContext lastResolvedName
            }
        }

        // 2. Grid cell cache check (~100m cell)
        val cellKey = Pair((latitude * 1000).toLong(), (longitude * 1000).toLong())
        val cached = locationCache[cellKey]
        if (!cached.isNullOrBlank()) {
            lastResolvedLat = latitude
            lastResolvedLng = longitude
            lastResolvedName = cached
            return@withContext cached
        }

        // 3. Geocode with Geocoder
        if (!Geocoder.isPresent()) {
            return@withContext lastResolvedName
        }

        val resolved = try {
            withTimeoutOrNull(4000L) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        // On Android 13+, synchronous getFromLocation is still valid on background thread
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)
                }

                val address = addresses?.firstOrNull()
                if (address != null) {
                    formatShortLocationName(address)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocode failed: ${e.message}")
            null
        }

        if (!resolved.isNullOrBlank()) {
            locationCache[cellKey] = resolved
            lastResolvedLat = latitude
            lastResolvedLng = longitude
            lastResolvedName = resolved
            lastResolveTimeMs = System.currentTimeMillis()
            return@withContext resolved
        }

        // Return last valid cached result or empty string (never return 'None')
        return@withContext lastResolvedName
    }

    /**
     * Extracts a clean, short, meaningful location name from Address.
     * Prefers Village/Locality + City/Town or City + State, omitting lengthy street/postal numbers.
     */
    fun formatShortLocationName(address: Address): String {
        val subLocality = address.subLocality?.trim()?.takeIf { isValidName(it) }
        val locality = address.locality?.trim()?.takeIf { isValidName(it) }
        val subAdmin = address.subAdminArea?.trim()?.takeIf { isValidName(it) }
        val admin = address.adminArea?.trim()?.takeIf { isValidName(it) }
        val country = address.countryName?.trim()?.takeIf { isValidName(it) }

        // Clean out words like "District" or "Division" to keep text compact
        val cleanSubAdmin = subAdmin?.replace(Regex("(?i)\\s*(District|Division|Tehsil)"), "")?.trim()
        val cleanAdmin = admin?.replace(Regex("(?i)\\s*(State|Province)"), "")?.trim()

        return when {
            // Case 1: SubLocality (Area/Village) + Locality (City/Town) e.g. "Rehmadpur, Roorkee"
            !subLocality.isNullOrBlank() && !locality.isNullOrBlank() && !subLocality.equals(locality, ignoreCase = true) -> {
                "$subLocality, $locality"
            }
            // Case 2: Locality (City) + State e.g. "Dehradun, Uttarakhand"
            !locality.isNullOrBlank() && !cleanAdmin.isNullOrBlank() && !locality.equals(cleanAdmin, ignoreCase = true) -> {
                "$locality, $cleanAdmin"
            }
            // Case 3: SubLocality + State e.g. "Doiwala, Uttarakhand"
            !subLocality.isNullOrBlank() && !cleanAdmin.isNullOrBlank() && !subLocality.equals(cleanAdmin, ignoreCase = true) -> {
                "$subLocality, $cleanAdmin"
            }
            // Case 4: Locality (City) + District
            !locality.isNullOrBlank() && !cleanSubAdmin.isNullOrBlank() && !locality.equals(cleanSubAdmin, ignoreCase = true) -> {
                "$locality, $cleanSubAdmin"
            }
            // Case 5: District + State
            !cleanSubAdmin.isNullOrBlank() && !cleanAdmin.isNullOrBlank() && !cleanSubAdmin.equals(cleanAdmin, ignoreCase = true) -> {
                "$cleanSubAdmin, $cleanAdmin"
            }
            // Case 6: Locality alone
            !locality.isNullOrBlank() -> locality
            // Case 7: SubLocality alone
            !subLocality.isNullOrBlank() -> subLocality
            // Case 8: District alone
            !cleanSubAdmin.isNullOrBlank() -> cleanSubAdmin
            // Case 9: State + Country
            !cleanAdmin.isNullOrBlank() && !country.isNullOrBlank() -> "$cleanAdmin, $country"
            !cleanAdmin.isNullOrBlank() -> cleanAdmin
            !country.isNullOrBlank() -> country
            else -> ""
        }
    }

    private fun isValidName(name: String): Boolean {
        if (name.isBlank()) return false
        val lower = name.lowercase(Locale.ROOT)
        return lower != "null" && lower != "none" && lower != "unnamed road" && !name.all { it.isDigit() || it == '-' || it == '+' }
    }

    fun invalidateCache() {
        locationCache.clear()
        lastResolvedLat = null
        lastResolvedLng = null
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
