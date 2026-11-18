package com.example.navigation

import android.content.Context
import com.example.location.LocationState
import com.example.location.LocationTracker
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.MatchConfidence
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.sensor.CompassState
import com.example.ui.home.NextKmStoneTracker
import com.example.ui.home.ScreensaverChainageStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Process-wide singleton telemetry hub so MainViewModel, LockScreenNavigationService,
 * and LockScreenNavigationActivity share the EXACT same single [LocationTracker] instance
 * without creating duplicate GPS listeners or conflicting location callbacks.
 */
object CtecsNavigationTelemetryHub {
    @Volatile
    private var sharedTracker: LocationTracker? = null

    fun getOrCreateLocationTracker(context: Context): LocationTracker {
        return sharedTracker ?: synchronized(this) {
            sharedTracker ?: LocationTracker(context.applicationContext).also {
                sharedTracker = it
            }
        }
    }

    private val _matchResult = MutableStateFlow(ChainageMatchResult())
    val matchResult: StateFlow<ChainageMatchResult> = _matchResult.asStateFlow()

    private val _activeRoute = MutableStateFlow<RouteData?>(null)
    val activeRoute: StateFlow<RouteData?> = _activeRoute.asStateFlow()

    private val _projectProfile = MutableStateFlow(ProjectProfile())
    val projectProfile: StateFlow<ProjectProfile> = _projectProfile.asStateFlow()

    private val _locationName = MutableStateFlow("")
    val locationName: StateFlow<String> = _locationName.asStateFlow()

    private val _compassState = MutableStateFlow(CompassState())
    val compassState: StateFlow<CompassState> = _compassState.asStateFlow()

    private val _screensaverSettings = MutableStateFlow(HomeScreensaverSettings())
    val screensaverSettings: StateFlow<HomeScreensaverSettings> = _screensaverSettings.asStateFlow()

    private val _is24HourFormat = MutableStateFlow(true)
    val is24HourFormat: StateFlow<Boolean> = _is24HourFormat.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _serviceStatusNote = MutableStateFlow<String?>(null)
    val serviceStatusNote: StateFlow<String?> = _serviceStatusNote.asStateFlow()

    fun updateTelemetry(
        matchResult: ChainageMatchResult? = null,
        routeData: RouteData? = _activeRoute.value,
        projectProfile: ProjectProfile? = null,
        locationName: String? = null,
        compassState: CompassState? = null,
        screensaverSettings: HomeScreensaverSettings? = null,
        is24HourFormat: Boolean? = null
    ) {
        if (matchResult != null) _matchResult.value = matchResult
        if (routeData !== _activeRoute.value) _activeRoute.value = routeData
        if (projectProfile != null) _projectProfile.value = projectProfile
        if (locationName != null) _locationName.value = locationName
        if (compassState != null) _compassState.value = compassState
        if (screensaverSettings != null) _screensaverSettings.value = screensaverSettings
        if (is24HourFormat != null) _is24HourFormat.value = is24HourFormat
    }

    fun setServiceRunning(running: Boolean, note: String? = null) {
        _isServiceRunning.value = running
        _serviceStatusNote.value = note
    }
}

/**
 * Immutable UI state for Google Maps-style Lock-Screen Live Chainage Navigation.
 * Every field originates from actual GPS, KMZ/KML alignment, and calibration data,
 * or explicitly flags [isSamplePreview] when shown as a settings sample.
 */
data class LockScreenNavUiState(
    val chainageText: String = "--+---",
    val reliableSide: String? = null,
    val directionArrow: String = "",
    val isIncreasingTrend: Boolean = true,
    val directionInstruction: String = "Waiting for GPS alignment",
    val roadOrStretchName: String? = null,
    val nextMarkerLabel: String? = null,
    val distanceToNextText: String? = null,
    val distanceToNextMetres: Long? = null,
    val speedKmh: Int? = null,
    val speedText: String = "-- km/h",
    val etaDurationText: String? = null,
    val etaArrivalTimeText: String? = null,
    val locationName: String = "",
    val isLiveGpsValid: Boolean = false,
    val hasRouteData: Boolean = false,
    val lastReliableTimestampMs: Long = 0L,
    val lastUpdateStatusText: String? = null,
    val isSamplePreview: Boolean = false
)

/**
 * Pure, testable resolver deriving [LockScreenNavUiState] without fabricating road names,
 * turns, chainages, or ETAs.
 */
class LockScreenNavStateResolver {
    private val chainageStateHolder = ScreensaverChainageStateHolder()
    private val nextKmTracker = NextKmStoneTracker()
    private var lastReliableTimestampMs: Long = 0L

    fun reset() {
        chainageStateHolder.reset()
        nextKmTracker.reset()
        lastReliableTimestampMs = 0L
    }

    fun resolve(
        locationState: LocationState?,
        matchResult: ChainageMatchResult,
        routeData: RouteData?,
        projectProfile: ProjectProfile,
        locationName: String?,
        is24HourFormat: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): LockScreenNavUiState {
        val hasGpsFix = locationState?.point != null && locationState.isUsable
        val screensaverResolved = chainageStateHolder.resolve(
            matchResult = matchResult,
            route = routeData,
            hasGpsFix = hasGpsFix,
            projectRoadSide = projectProfile.roadSide,
            locationState = locationState,
            locationName = locationName,
            nowMs = nowMs
        )

        if (screensaverResolved.isLiveGpsValid && (locationState?.lastFixTime ?: 0L) > 0L) {
            lastReliableTimestampMs = locationState!!.lastFixTime
        } else if (hasGpsFix && (locationState?.lastFixTime ?: 0L) > 0L) {
            lastReliableTimestampMs = locationState!!.lastFixTime
        }

        val roadName = resolveVerifiedRoadOrStretch(routeData, projectProfile)
        val effectiveSide = screensaverResolved.reliableSide

        // Determine verified Next KM Stone only when route & match are valid
        val isExplicitRoadSide = projectProfile.roadSide == RoadSide.LHS ||
            projectProfile.roadSide == RoadSide.RHS
        val nextKmState = if (effectiveSide != null && routeData != null) {
            nextKmTracker.evaluate(
                matchResult = matchResult,
                route = routeData,
                detectedSide = effectiveSide,
                isAutoDetectedSide = !isExplicitRoadSide
            )
        } else {
            null
        }

        val verifiedDistanceM = nextKmState?.takeIf { it.isReliable }?.distanceMetres
        val distanceText = verifiedDistanceM?.let { formatNavigationDistance(it) }
        val nextMarkerLabel = nextKmState?.takeIf { it.isReliable }?.let { "Next KM ${it.nextChainage}" }

        // ETA strictly computed ONLY when distance > 0 and verified moving speed >= 0.56 m/s (2 km/h)
        val rawSpeedMps = locationState?.speed?.takeIf {
            hasGpsFix &&
                !it.isNaN() &&
                locationState.lastFixTime > 0L &&
                (nowMs - locationState.lastFixTime) <= ScreensaverChainageStateHolder.SCREENSAVER_SPEED_STALE_MS
        }
        val etaPair = if (
            verifiedDistanceM != null &&
            verifiedDistanceM > 0L &&
            rawSpeedMps != null &&
            rawSpeedMps >= MIN_ETA_SPEED_MPS
        ) {
            val secondsToNext = (verifiedDistanceM.toDouble() / rawSpeedMps.toDouble()).roundToLong()
            val etaMinutes = ((secondsToNext + 30L) / 60L).coerceAtLeast(1L)
            val durationStr = when {
                secondsToNext < 45L -> "< 1 min"
                etaMinutes < 60L -> "$etaMinutes min"
                else -> "${etaMinutes / 60L}h ${etaMinutes % 60L}m"
            }
            val pattern = if (is24HourFormat) "HH:mm" else "h:mm a"
            val arrivalStr = SimpleDateFormat(pattern, Locale.getDefault())
                .format(Date(nowMs + secondsToNext * 1000L))
            durationStr to arrivalStr
        } else {
            null to null
        }

        val directionInstruction = when {
            routeData == null -> "Load KMZ/KML alignment"
            !hasGpsFix -> "Searching for GPS signal"
            matchResult.source == ChainageSource.MANUAL || matchResult.isManual ->
                "Manual Chainage Active"
            matchResult.confidence == MatchConfidence.NO_MATCH || matchResult.isUncertain ->
                "Approaching route corridor"
            effectiveSide == "LHS" -> "Increasing Chainage (LHS)"
            effectiveSide == "RHS" -> "Decreasing Chainage (RHS)"
            else -> "Following active alignment"
        }

        val staleStatus = if (!hasGpsFix || !screensaverResolved.isLiveGpsValid) {
            if (lastReliableTimestampMs > 0L) {
                val ageSec = ((nowMs - lastReliableTimestampMs).coerceAtLeast(0L)) / 1000L
                when {
                    ageSec < 60L -> "Last GPS update ${ageSec}s ago"
                    ageSec < 3600L -> "Last GPS update ${ageSec / 60L}m ago"
                    else -> "GPS signal unavailable"
                }
            } else {
                "Waiting for live GPS"
            }
        } else {
            null
        }

        return LockScreenNavUiState(
            chainageText = screensaverResolved.chainageText,
            reliableSide = effectiveSide,
            directionArrow = screensaverResolved.directionArrow,
            isIncreasingTrend = screensaverResolved.isIncreasingTrend,
            directionInstruction = directionInstruction,
            roadOrStretchName = roadName,
            nextMarkerLabel = nextMarkerLabel,
            distanceToNextText = distanceText,
            distanceToNextMetres = verifiedDistanceM,
            speedKmh = screensaverResolved.speedKmh,
            speedText = screensaverResolved.speedText,
            etaDurationText = etaPair.first,
            etaArrivalTimeText = etaPair.second,
            locationName = screensaverResolved.locationName,
            isLiveGpsValid = screensaverResolved.isLiveGpsValid,
            hasRouteData = routeData != null,
            lastReliableTimestampMs = lastReliableTimestampMs,
            lastUpdateStatusText = staleStatus,
            isSamplePreview = false
        )
    }

    companion object {
        const val MIN_ETA_SPEED_MPS = 0.56f // ~2.0 km/h minimum reliable travel threshold

        fun resolveVerifiedRoadOrStretch(
            routeData: RouteData?,
            projectProfile: ProjectProfile
        ): String? {
            val highway = projectProfile.highwayId.trim()
            val stretch = projectProfile.packageSection.trim()
            val combinedProject = listOf(highway, stretch).filter { it.isNotBlank() }.joinToString(" • ")
            if (combinedProject.isNotBlank()) return combinedProject
            return routeData?.name
                ?.substringBeforeLast('.')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

        fun formatNavigationDistance(metres: Long): String {
            return if (metres >= 1000L) {
                String.format(Locale.US, "%.1f km", metres / 1000.0)
            } else {
                "$metres m"
            }
        }

        /**
         * Explicit sample state ONLY for Settings preview when no live GPS or active KMZ route
         * is available. Always sets [LockScreenNavUiState.isSamplePreview] = true.
         */
        fun samplePreviewState(): LockScreenNavUiState {
            return LockScreenNavUiState(
                chainageText = "204+320",
                reliableSide = "LHS",
                directionArrow = "↑",
                isIncreasingTrend = true,
                directionInstruction = "Increasing Chainage (LHS)",
                roadOrStretchName = "Sample Alignment Preview",
                nextMarkerLabel = "Next KM 205+000",
                distanceToNextText = "680 m",
                distanceToNextMetres = 680L,
                speedKmh = 54,
                speedText = "54 km/h",
                etaDurationText = "1 min",
                etaArrivalTimeText = "Sample",
                locationName = "Sample Corridor",
                isLiveGpsValid = true,
                hasRouteData = true,
                lastReliableTimestampMs = System.currentTimeMillis(),
                lastUpdateStatusText = null,
                isSamplePreview = true
            )
        }
    }
}
