package com.example.navigation

import android.content.Context
import com.example.location.LocationState
import com.example.location.LocationTracker
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.CtecsWidgetSettings
import com.example.model.DirectionDetectionState
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.KmStoneAlertSettings
import com.example.model.MatchConfidence
import com.example.model.NavigationNotificationSettings
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.model.SmartDirectionPreferences
import com.example.model.SmartDirectionState
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
 * and Home-Screen Widgets share the EXACT same single [LocationTracker]
 * instance without creating duplicate GPS listeners or conflicting location callbacks.
 */
object CtecsNavigationTelemetryHub {
    @Volatile
    private var sharedTracker: LocationTracker? = null

    @Volatile
    private var initializedFromPreferences: Boolean = false

    fun getOrCreateLocationTracker(context: Context): LocationTracker {
        return sharedTracker ?: synchronized(this) {
            sharedTracker ?: LocationTracker(context.applicationContext).also {
                sharedTracker = it
            }
        }
    }

    /**
     * Hydrates the singleton telemetry hub directly from persisted DataStore preferences
     * when background services or widgets start cold after process death or device reboot.
     */
    fun ensureInitialized(context: Context, forceReload: Boolean = false) {
        if (initializedFromPreferences && !forceReload) return
        synchronized(this) {
            if (initializedFromPreferences && !forceReload) return
            try {
                val prefsManager = com.example.data.PreferencesManager(context.applicationContext)
                val profile = prefsManager.getInitialProjectProfile()
                val screensaver = prefsManager.getInitialHomeScreensaverSettings()
                val notif = prefsManager.getInitialNavigationNotificationSettings()
                val widgets = prefsManager.getInitialWidgetSettings()
                val alerts = prefsManager.getInitialKmStoneAlertSettings()
                val directionPrefs = prefsManager.getInitialSmartDirectionPreferences()
                val cam = prefsManager.getInitialCameraSettings()
                val overlay = prefsManager.getInitialOverlayConfig()

                _projectProfile.value = profile
                _screensaverSettings.value = screensaver
                _navigationNotificationSettings.value = notif
                _widgetSettings.value = widgets
                _kmStoneAlertSettings.value = alerts
                _directionPreferences.value = directionPrefs
                _is24HourFormat.value = cam.is24HourFormat
                if (_locationName.value.isBlank() && overlay.lastDetectedLocationName.isNotBlank()) {
                    _locationName.value = overlay.lastDetectedLocationName
                }
                initializedFromPreferences = true
            } catch (_: Exception) {}
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

    private val _navigationNotificationSettings = MutableStateFlow(NavigationNotificationSettings())
    val navigationNotificationSettings: StateFlow<NavigationNotificationSettings> =
        _navigationNotificationSettings.asStateFlow()

    private val _widgetSettings = MutableStateFlow(CtecsWidgetSettings())
    val widgetSettings: StateFlow<CtecsWidgetSettings> = _widgetSettings.asStateFlow()

    private val _kmStoneAlertSettings = MutableStateFlow(KmStoneAlertSettings())
    val kmStoneAlertSettings: StateFlow<KmStoneAlertSettings> = _kmStoneAlertSettings.asStateFlow()

    private val _isCameraOrSurveyRecording = MutableStateFlow(false)
    val isCameraOrSurveyRecording: StateFlow<Boolean> = _isCameraOrSurveyRecording.asStateFlow()

    private val _is24HourFormat = MutableStateFlow(true)
    val is24HourFormat: StateFlow<Boolean> = _is24HourFormat.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _serviceStatusNote = MutableStateFlow<String?>(null)
    val serviceStatusNote: StateFlow<String?> = _serviceStatusNote.asStateFlow()

    private val _isKmStoneServiceRunning = MutableStateFlow(false)
    val isKmStoneServiceRunning: StateFlow<Boolean> = _isKmStoneServiceRunning.asStateFlow()

    private val _kmStoneServiceStatusNote = MutableStateFlow<String?>(null)
    val kmStoneServiceStatusNote: StateFlow<String?> = _kmStoneServiceStatusNote.asStateFlow()

    private val _lastKmStoneEvent = MutableStateFlow<KmStoneAlertEvent?>(null)
    val lastKmStoneEvent: StateFlow<KmStoneAlertEvent?> = _lastKmStoneEvent.asStateFlow()

    // Shared single KM Stone Arrival Detector & Smart LHS/RHS Direction Manager across Services & ViewModel
    val sharedKmStoneDetector = KmStoneArrivalDetector()
    val sharedDirectionManager = SmartDirectionManager()

    private val _directionState = MutableStateFlow(SmartDirectionState.alignmentUnavailable())
    val directionState: StateFlow<SmartDirectionState> = _directionState.asStateFlow()

    private val _directionPreferences = MutableStateFlow(SmartDirectionPreferences())
    val directionPreferences: StateFlow<SmartDirectionPreferences> = _directionPreferences.asStateFlow()

    fun updateTelemetry(
        matchResult: ChainageMatchResult? = null,
        routeData: RouteData? = _activeRoute.value,
        projectProfile: ProjectProfile? = null,
        locationName: String? = null,
        compassState: CompassState? = null,
        screensaverSettings: HomeScreensaverSettings? = null,
        navigationNotificationSettings: NavigationNotificationSettings? = null,
        widgetSettings: CtecsWidgetSettings? = null,
        kmStoneAlertSettings: KmStoneAlertSettings? = null,
        isCameraOrSurveyRecording: Boolean? = null,
        is24HourFormat: Boolean? = null,
        directionState: SmartDirectionState? = null,
        directionPreferences: SmartDirectionPreferences? = null
    ) {
        if (matchResult != null) {
            _matchResult.value = matchResult
            matchResult.smartDirectionState?.let { _directionState.value = it }
        }
        if (routeData !== _activeRoute.value) _activeRoute.value = routeData
        if (projectProfile != null) _projectProfile.value = projectProfile
        if (locationName != null) _locationName.value = locationName
        if (compassState != null) _compassState.value = compassState
        if (screensaverSettings != null) _screensaverSettings.value = screensaverSettings
        if (navigationNotificationSettings != null) _navigationNotificationSettings.value = navigationNotificationSettings
        if (widgetSettings != null) _widgetSettings.value = widgetSettings
        if (kmStoneAlertSettings != null) _kmStoneAlertSettings.value = kmStoneAlertSettings
        if (isCameraOrSurveyRecording != null) _isCameraOrSurveyRecording.value = isCameraOrSurveyRecording
        if (is24HourFormat != null) _is24HourFormat.value = is24HourFormat
        if (directionState != null) _directionState.value = directionState
        if (directionPreferences != null) _directionPreferences.value = directionPreferences
    }

    fun setServiceRunning(running: Boolean, note: String? = null) {
        _isServiceRunning.value = running
        _serviceStatusNote.value = note
    }

    fun setKmStoneServiceRunning(running: Boolean, note: String? = null) {
        _isKmStoneServiceRunning.value = running
        _kmStoneServiceStatusNote.value = note
    }

    fun recordKmStoneAlertEvent(event: KmStoneAlertEvent?) {
        _lastKmStoneEvent.value = event
    }
}

/**
 * Snapshot of current device battery-saver & thermal pressure used to gracefully trim non-essential
 * UI/notification redraws without ever disabling essential KM Stone monitoring.
 */
data class DevicePowerThermalState(
    val isPowerSaveMode: Boolean = false,
    val batteryPercent: Int = 100,
    val isLowBattery: Boolean = false,
    val isThermalThrottled: Boolean = false
) {
    val shouldConserveUiResources: Boolean
        get() = isPowerSaveMode || isLowBattery || isThermalThrottled
}

object DevicePowerThermalMonitor {
    const val LOW_BATTERY_THRESHOLD_PERCENT = 20

    fun snapshot(context: Context): DevicePowerThermalState {
        return try {
            val powerMgr = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val batteryMgr = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val powerSave = powerMgr?.isPowerSaveMode == true
            val rawPct = batteryMgr?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 1..100 }
                ?: 100
            val thermalHot = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                (powerMgr?.currentThermalStatus ?: android.os.PowerManager.THERMAL_STATUS_NONE) >=
                    android.os.PowerManager.THERMAL_STATUS_MODERATE
            } else {
                false
            }
            DevicePowerThermalState(
                isPowerSaveMode = powerSave,
                batteryPercent = rawPct,
                isLowBattery = rawPct <= LOW_BATTERY_THRESHOLD_PERCENT,
                isThermalThrottled = thermalHot
            )
        } catch (_: Exception) {
            DevicePowerThermalState()
        }
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
    val currentTimeText: String = "",
    val locationName: String = "",
    val isLiveGpsValid: Boolean = false,
    val hasRouteData: Boolean = false,
    val lastReliableTimestampMs: Long = 0L,
    val lastUpdateStatusText: String? = null,
    val isSamplePreview: Boolean = false,
    val directionState: DirectionDetectionState = DirectionDetectionState.ALIGNMENT_UNAVAILABLE,
    val isDirectionManual: Boolean = false
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
        val smartDir = matchResult.smartDirectionState
        val effectiveSide = if (smartDir != null) {
            smartDir.confirmedBinarySide
        } else {
            screensaverResolved.reliableSide
        }
        val effectiveDirectionState = smartDir?.detectionState ?: when {
            routeData == null -> DirectionDetectionState.ALIGNMENT_UNAVAILABLE
            !hasGpsFix -> DirectionDetectionState.GPS_SIGNAL_WEAK
            effectiveSide == "LHS" -> DirectionDetectionState.LHS_CONFIRMED
            effectiveSide == "RHS" -> DirectionDetectionState.RHS_CONFIRMED
            else -> DirectionDetectionState.DIRECTION_UNCERTAIN
        }
        val effectiveIsIncreasing = smartDir?.isIncreasingChainage ?: screensaverResolved.isIncreasingTrend
        val effectiveArrow = when {
            smartDir != null -> smartDir.directionArrow
            else -> screensaverResolved.directionArrow
        }

        // Determine verified Next KM Stone only when route, match, and direction are valid
        val isExplicitRoadSide = smartDir?.isManualOverride ?: (
            projectProfile.roadSide == RoadSide.LHS || projectProfile.roadSide == RoadSide.RHS
        )
        val nextKmState = if (effectiveSide != null && routeData != null) {
            nextKmTracker.evaluate(
                matchResult = matchResult,
                route = routeData,
                detectedSide = effectiveSide,
                isAutoDetectedSide = !isExplicitRoadSide,
                smartDirectionState = smartDir
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
        val timePattern = if (is24HourFormat) "HH:mm" else "h:mm a"
        val currentTimeFormatted = SimpleDateFormat(timePattern, Locale.getDefault())
            .format(Date(nowMs))

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
            val arrivalStr = SimpleDateFormat(timePattern, Locale.getDefault())
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
            effectiveDirectionState == DirectionDetectionState.GPS_SIGNAL_WEAK ->
                "GPS signal weak — checking direction"
            effectiveDirectionState == DirectionDetectionState.DIRECTION_CHANGING ->
                "Direction changing…"
            effectiveDirectionState == DirectionDetectionState.DIRECTION_UNCERTAIN ->
                "Travel direction uncertain"
            effectiveDirectionState == DirectionDetectionState.STATIONARY && effectiveSide == null ->
                "Vehicle stationary"
            effectiveSide != null -> {
                val trendLabel = if (effectiveIsIncreasing) "Increasing Chainage" else "Decreasing Chainage"
                "$trendLabel ($effectiveSide)"
            }
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
            directionArrow = effectiveArrow,
            isIncreasingTrend = effectiveIsIncreasing,
            directionInstruction = directionInstruction,
            roadOrStretchName = roadName,
            nextMarkerLabel = nextMarkerLabel,
            distanceToNextText = distanceText,
            distanceToNextMetres = verifiedDistanceM,
            speedKmh = screensaverResolved.speedKmh,
            speedText = screensaverResolved.speedText,
            etaDurationText = etaPair.first,
            etaArrivalTimeText = etaPair.second,
            currentTimeText = currentTimeFormatted,
            locationName = screensaverResolved.locationName,
            isLiveGpsValid = screensaverResolved.isLiveGpsValid,
            hasRouteData = routeData != null,
            lastReliableTimestampMs = lastReliableTimestampMs,
            lastUpdateStatusText = staleStatus,
            isSamplePreview = false,
            directionState = effectiveDirectionState,
            isDirectionManual = smartDir?.isManualOverride == true
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
                isSamplePreview = true,
                directionState = DirectionDetectionState.LHS_CONFIRMED,
                isDirectionManual = false
            )
        }
    }
}
