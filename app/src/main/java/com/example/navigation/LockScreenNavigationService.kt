package com.example.navigation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PreferencesManager
import com.example.geo.ChainageMatcher
import com.example.model.KmStoneAlertSettings
import com.example.model.NavigationNotificationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class LockScreenNotificationSummary(
    val title: String,
    val contentText: String,
    val expandedText: String? = null,
    val shortCriticalText: String? = null
)

private data class NotificationRenderSignature(
    val summary: LockScreenNotificationSummary,
    val showOnLockScreen: Boolean,
    val liveNotificationEnabled: Boolean,
    val isMonitoringOnlyMode: Boolean = false
)

object LockScreenNotificationFormatter {

    fun format(
        navState: LockScreenNavUiState,
        settings: NavigationNotificationSettings = NavigationNotificationSettings.defaultSettings()
    ): LockScreenNotificationSummary {
        val validChainage = navState.chainageText.isNotBlank() && navState.chainageText != "--+---"
        val chainagePart = if (settings.showChainage && validChainage) {
            "CH ${navState.chainageText}"
        } else {
            null
        }
        val sidePart = if (settings.showRoadSide && !navState.reliableSide.isNullOrBlank()) {
            "${navState.reliableSide} ${navState.directionArrow}".trim()
        } else {
            null
        }
        val titleParts = listOfNotNull(chainagePart, sidePart)
        val title = if (titleParts.isNotEmpty()) {
            titleParts.joinToString(" | ")
        } else {
            "CTECS Navigation Active"
        }

        val collapsedDetails = buildList {
            if (settings.showNextKmMarker && !navState.nextMarkerLabel.isNullOrBlank()) {
                add(navState.nextMarkerLabel)
            }
            if (settings.showDistanceRemaining && navState.distanceToNextMetres != null) {
                add("${navState.distanceToNextMetres} m")
            }
            if (settings.showSpeed) {
                add(navState.speedText)
            }
            if (isEmpty()) {
                add(navState.lastUpdateStatusText ?: navState.directionInstruction)
            }
        }
        val contentText = collapsedDetails.joinToString(" | ")

        val expandedText = if (settings.expandedInfoEnabled) {
            buildList {
                if (contentText.isNotBlank()) add(contentText)
                if (settings.showLocation && navState.locationName.isNotBlank()) {
                    add("Location: ${navState.locationName}")
                }
                if (!navState.roadOrStretchName.isNullOrBlank() &&
                    !navState.roadOrStretchName.equals(navState.locationName, ignoreCase = true)
                ) {
                    add("Stretch: ${navState.roadOrStretchName}")
                }
                val timeSuffix = navState.currentTimeText.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
                if (navState.isLiveGpsValid) {
                    val etaPart = navState.etaDurationText?.let { " | ETA $it" }.orEmpty()
                    add("GPS: Live tracking$etaPart$timeSuffix")
                } else {
                    add("${navState.lastUpdateStatusText ?: "GPS signal unavailable"}$timeSuffix")
                }
            }.joinToString("\n")
        } else {
            null
        }

        val shortCriticalText = if (settings.liveNotificationEnabled) {
            when {
                settings.showChainage && validChainage && navState.chainageText.length <= 7 ->
                    navState.chainageText
                settings.showDistanceRemaining && navState.distanceToNextMetres != null ->
                    "${navState.distanceToNextMetres}m".takeIf { it.length <= 7 }
                settings.showSpeed && navState.speedKmh != null ->
                    "${navState.speedKmh}kmh".takeIf { it.length <= 7 }
                else -> null
            }
        } else {
            null
        }

        return LockScreenNotificationSummary(
            title = title,
            contentText = contentText,
            expandedText = expandedText,
            shortCriticalText = shortCriticalText
        )
    }
}

/**
 * Battery-conscious Foreground Location Service keeping live chainage navigation synced for
 * Home-Screen Widgets, KM Stone arrival alerts, and the ongoing Status-Bar / Android Live Update /
 * Samsung Now Bar Navigation Notification.
 */
class LockScreenNavigationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val navResolver = LockScreenNavStateResolver()
    private var collectorJob: Job? = null
    private var lastPostedSignature: NotificationRenderSignature? = null
    private var lastMinorNotificationPostMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CtecsNavigationTelemetryHub.ensureInitialized(applicationContext)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CtecsNavigationTelemetryHub.ensureInitialized(applicationContext)
        when (intent?.action) {
            ACTION_STOP_NAVIGATION -> {
                serviceScope.launch {
                    val pm = PreferencesManager(applicationContext)
                    val currentNotif = CtecsNavigationTelemetryHub.navigationNotificationSettings.value
                    val currentAlerts = CtecsNavigationTelemetryHub.kmStoneAlertSettings.value
                    if (currentNotif.enabled) {
                        val updatedNotif = currentNotif.copy(enabled = false)
                        CtecsNavigationTelemetryHub.updateTelemetry(
                            navigationNotificationSettings = updatedNotif
                        )
                        pm.updateNavigationNotificationSettings(updatedNotif)
                    }
                    if (currentAlerts.lockScreenNotificationsEnabled) {
                        val updatedAlerts = currentAlerts.copy(lockScreenNotificationsEnabled = false)
                        CtecsNavigationTelemetryHub.updateTelemetry(
                            kmStoneAlertSettings = updatedAlerts
                        )
                        pm.updateKmStoneAlertSettings(updatedAlerts)
                    }
                }
                stopNavigationService()
                return START_NOT_STICKY
            }
        }

        if (!hasLocationPermission(this)) {
            CtecsNavigationTelemetryHub.setServiceRunning(
                running = false,
                note = "Location permission required for live navigation updates"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val notifSettings = CtecsNavigationTelemetryHub.navigationNotificationSettings.value
        val alertSettings = CtecsNavigationTelemetryHub.kmStoneAlertSettings.value
        val hasRoute = CtecsNavigationTelemetryHub.activeRoute.value != null
        if (!shouldNotificationServiceStayActive(notifSettings, alertSettings, hasRoute)) {
            stopNavigationService()
            return START_NOT_STICKY
        }

        val initialState = currentSnapshotState()
        val isMonitoringOnly = !notifSettings.enabled
        val initialSummary = formatServiceNotificationSummary(initialState, notifSettings)
        val initialSignature = NotificationRenderSignature(
            summary = initialSummary,
            showOnLockScreen = notifSettings.showOnLockScreen,
            liveNotificationEnabled = notifSettings.liveNotificationEnabled,
            isMonitoringOnlyMode = isMonitoringOnly
        )
        val initialNotification = buildNotification(initialState, notifSettings, initialSummary)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
            lastPostedSignature = initialSignature
            lastMinorNotificationPostMs = System.currentTimeMillis()
        } catch (se: SecurityException) {
            CtecsNavigationTelemetryHub.setServiceRunning(
                running = false,
                note = "Unable to start location foreground service: ${se.message ?: "permission denied"}"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        // Reuse the single shared LocationTracker instance (never registers a second GPS listener)
        CtecsNavigationTelemetryHub.getOrCreateLocationTracker(applicationContext).startTracking()
        CtecsNavigationTelemetryHub.setServiceRunning(running = true, note = null)
        startTelemetryObserver()

        return START_STICKY
    }

    private fun startTelemetryObserver() {
        if (collectorJob?.isActive == true) return
        val tracker = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(applicationContext)
        collectorJob = serviceScope.launch {
            val baseTelemetryFlow = combine(
                tracker.locationState,
                CtecsNavigationTelemetryHub.matchResult,
                CtecsNavigationTelemetryHub.activeRoute,
                CtecsNavigationTelemetryHub.projectProfile,
                CtecsNavigationTelemetryHub.locationName
            ) { loc, match, route, profile, locName ->
                // Fallback geometric matching only if hub has no match yet (e.g., cold service restart)
                val effectiveMatch = if (match.chainage == null && route != null && loc.point != null && loc.isUsable) {
                    val rawRecovered = ChainageMatcher.match(
                        gpsPoint = loc.point,
                        gpsAccuracy = loc.accuracy ?: 15f,
                        bearing = loc.bearing,
                        routeData = route,
                        previousResult = match
                    )
                    val dirPrefs = CtecsNavigationTelemetryHub.directionPreferences.value
                    val recoveredDir = CtecsNavigationTelemetryHub.sharedDirectionManager.evaluate(
                        locationState = loc,
                        matchResult = rawRecovered,
                        routeData = route,
                        preferences = dirPrefs
                    )
                    if (recoveredDir.autoReactivatedReason != null ||
                        (!recoveredDir.isManualOverride && dirPrefs.controlMode == com.example.model.DirectionControlMode.MANUAL_OVERRIDE)
                    ) {
                        val autoPrefs = dirPrefs.copy(
                            controlMode = com.example.model.DirectionControlMode.AUTO,
                            manualOverrideSide = null,
                            manualSelectedAtMs = 0L
                        )
                        CtecsNavigationTelemetryHub.updateTelemetry(
                            directionState = recoveredDir,
                            directionPreferences = autoPrefs
                        )
                    } else {
                        CtecsNavigationTelemetryHub.updateTelemetry(directionState = recoveredDir)
                    }
                    rawRecovered.copy(
                        detectedSide = recoveredDir.confirmedSide ?: "Unknown",
                        smartDirectionState = recoveredDir
                    )
                } else {
                    match
                }
                // Evaluate KM Stone arrival / advance warning alerts ONLY if dedicated KM Stone
                // foreground service is not already running (prevents duplicate detector passes).
                if (!CtecsNavigationTelemetryHub.isKmStoneServiceRunning.value) {
                    val alertCfg = CtecsNavigationTelemetryHub.kmStoneAlertSettings.value
                    val events = CtecsNavigationTelemetryHub.sharedKmStoneDetector.evaluate(
                        locationState = loc,
                        matchResult = effectiveMatch,
                        routeData = route,
                        projectRoadSide = profile.roadSide,
                        settings = alertCfg,
                        smartDirectionState = effectiveMatch.smartDirectionState
                    )
                    if (events.isNotEmpty()) {
                        val isRec = CtecsNavigationTelemetryHub.isCameraOrSurveyRecording.value
                        events.forEach { evt ->
                            KmStoneAlertFeedbackManager.dispatchAlert(
                                context = applicationContext,
                                event = evt,
                                settings = alertCfg,
                                isRecordingActive = isRec
                            )
                        }
                    }
                }
                val resolvedUi = navResolver.resolve(
                    locationState = loc,
                    matchResult = effectiveMatch,
                    routeData = route,
                    projectProfile = profile,
                    locationName = locName,
                    is24HourFormat = CtecsNavigationTelemetryHub.is24HourFormat.value
                )
                resolvedUi to (route != null)
            }

            combine(
                baseTelemetryFlow,
                CtecsNavigationTelemetryHub.navigationNotificationSettings,
                CtecsNavigationTelemetryHub.kmStoneAlertSettings
            ) { (uiState, hasRoute), notifSettings, alertSettings ->
                Triple(uiState, notifSettings, shouldNotificationServiceStayActive(notifSettings, alertSettings, hasRoute))
            }.collect { (uiState, notifSettings, stayActive) ->
                // Keep home-screen widgets synced while foreground service is active
                com.example.widget.CtecsWidgetUpdater.updateAllWidgets(applicationContext)

                if (!stayActive) {
                    stopNavigationService()
                    return@collect
                }
                val isMonitoringOnly = !notifSettings.enabled
                val summary = formatServiceNotificationSummary(uiState, notifSettings)
                val newSignature = NotificationRenderSignature(
                    summary = summary,
                    showOnLockScreen = notifSettings.showOnLockScreen,
                    liveNotificationEnabled = notifSettings.liveNotificationEnabled,
                    isMonitoringOnlyMode = isMonitoringOnly
                )
                val previous = lastPostedSignature
                if (newSignature == previous) {
                    return@collect
                }
                val now = System.currentTimeMillis()
                val coreTitleChanged = previous == null || previous.summary.title != summary.title
                val settingsChanged = previous == null ||
                    previous.showOnLockScreen != newSignature.showOnLockScreen ||
                    previous.liveNotificationEnabled != newSignature.liveNotificationEnabled ||
                    previous.isMonitoringOnlyMode != newSignature.isMonitoringOnlyMode
                if (coreTitleChanged || settingsChanged || (now - lastMinorNotificationPostMs) >= MINOR_UPDATE_THROTTLE_MS) {
                    lastPostedSignature = newSignature
                    lastMinorNotificationPostMs = now
                    val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    mgr?.notify(NOTIFICATION_ID, buildNotification(uiState, notifSettings, summary))
                }
            }
        }
    }

    private fun formatServiceNotificationSummary(
        navState: LockScreenNavUiState,
        settings: NavigationNotificationSettings
    ): LockScreenNotificationSummary {
        if (settings.enabled) {
            return LockScreenNotificationFormatter.format(navState, settings)
        }
        val validChainage = navState.chainageText.isNotBlank() && navState.chainageText != "--+---"
        val chPart = if (validChainage) "CH ${navState.chainageText}" else "Waiting for GPS alignment"
        val sidePart = navState.reliableSide?.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
        return LockScreenNotificationSummary(
            title = "CTECS KM Stone Monitoring",
            contentText = "$chPart$sidePart • Active for arrival alerts",
            expandedText = "$chPart$sidePart\nMonitoring verified KM Stones in background & while locked.",
            shortCriticalText = null
        )
    }

    private fun currentSnapshotState(): LockScreenNavUiState {
        val tracker = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(applicationContext)
        return navResolver.resolve(
            locationState = tracker.locationState.value,
            matchResult = CtecsNavigationTelemetryHub.matchResult.value,
            routeData = CtecsNavigationTelemetryHub.activeRoute.value,
            projectProfile = CtecsNavigationTelemetryHub.projectProfile.value,
            locationName = CtecsNavigationTelemetryHub.locationName.value,
            is24HourFormat = CtecsNavigationTelemetryHub.is24HourFormat.value
        )
    }

    internal fun buildNotification(
        navState: LockScreenNavUiState,
        settings: NavigationNotificationSettings = CtecsNavigationTelemetryHub.navigationNotificationSettings.value,
        precomputedSummary: LockScreenNotificationSummary? = null
    ): Notification {
        val summary = precomputedSummary ?: LockScreenNotificationFormatter.format(navState, settings)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPending = PendingIntent.getActivity(
            this,
            1001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LockScreenNavigationService::class.java).apply {
            action = ACTION_STOP_NAVIGATION
        }
        val stopPending = PendingIntent.getService(
            this,
            1002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val visibility = if (settings.enabled && settings.showOnLockScreen) {
            NotificationCompat.VISIBILITY_PUBLIC
        } else {
            NotificationCompat.VISIBILITY_PRIVATE
        }

        val timestampMs = navState.lastReliableTimestampMs.takeIf { it > 0L } ?: System.currentTimeMillis()

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ctecs_nav)
            .setColor(0xFF0EA5E9.toInt())
            .setContentTitle(summary.title)
            .setContentText(summary.contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(timestampMs)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(visibility)
            .setPriority(
                if (settings.enabled && settings.liveNotificationEnabled) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_LOW
            )
            .setContentIntent(openPending)
            .addAction(0, "Stop", stopPending)

        if (!summary.expandedText.isNullOrBlank()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(summary.title)
                    .bigText(summary.expandedText)
            )
        }

        if (settings.enabled && settings.liveNotificationEnabled) {
            builder.setRequestPromotedOngoing(true)
            if (!summary.shortCriticalText.isNullOrBlank()) {
                builder.setShortCriticalText(summary.shortCriticalText)
            }
        }

        return builder.build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            // Remove older low-importance channel if lingering so Live Update / Now Bar importance applies
            if (mgr.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
                try {
                    mgr.deleteNotificationChannel(LEGACY_CHANNEL_ID)
                } catch (_: Exception) {}
            }
            if (mgr.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Live Chainage Navigation",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Displays live calibrated highway chainage, side, speed, and next KM marker updates."
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun stopNavigationService() {
        collectorJob?.cancel()
        collectorJob = null
        lastPostedSignature = null
        CtecsNavigationTelemetryHub.setServiceRunning(running = false, note = null)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        try {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            mgr?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        collectorJob?.cancel()
        lastPostedSignature = null
        CtecsNavigationTelemetryHub.setServiceRunning(running = false, note = null)
        try {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            mgr?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val LEGACY_CHANNEL_ID = "ctecs_lock_nav_channel"
        const val NOTIFICATION_CHANNEL_ID = "ctecs_lock_nav_live_channel"
        const val NOTIFICATION_ID = 4108
        const val ACTION_START_NAVIGATION = "com.example.action.START_LOCK_NAV"
        const val ACTION_STOP_NAVIGATION = "com.example.action.STOP_LOCK_NAV"
        private const val MINOR_UPDATE_THROTTLE_MS = 500L

        fun shouldNotificationServiceStayActive(
            notifSettings: NavigationNotificationSettings,
            kmStoneAlertSettings: KmStoneAlertSettings? = null,
            hasActiveRoute: Boolean = false
        ): Boolean {
            if (notifSettings.enabled) return true
            return kmStoneAlertSettings != null &&
                kmStoneAlertSettings.enabled &&
                kmStoneAlertSettings.lockScreenNotificationsEnabled &&
                hasActiveRoute
        }

        fun hasLocationPermission(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }

        fun hasNotificationPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun syncServiceState(context: Context, enabled: Boolean) {
            val appCtx = context.applicationContext
            if (!enabled) {
                try {
                    appCtx.stopService(Intent(appCtx, LockScreenNavigationService::class.java))
                } catch (_: Exception) {}
                try {
                    val mgr = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    mgr?.cancel(NOTIFICATION_ID)
                } catch (_: Exception) {}
                CtecsNavigationTelemetryHub.setServiceRunning(running = false, note = null)
                return
            }
            if (!hasLocationPermission(appCtx)) {
                CtecsNavigationTelemetryHub.setServiceRunning(
                    running = false,
                    note = "Grant location permission to start live navigation updates."
                )
                return
            }
            val startIntent = Intent(appCtx, LockScreenNavigationService::class.java).apply {
                action = ACTION_START_NAVIGATION
            }
            try {
                ContextCompat.startForegroundService(appCtx, startIntent)
            } catch (e: Exception) {
                CtecsNavigationTelemetryHub.setServiceRunning(
                    running = false,
                    note = e.message ?: "Foreground service unavailable on this device state."
                )
            }
        }
    }
}
