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
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.KmStoneAlertSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Formatted notification payload for the dedicated KM Stone Monitoring Foreground Service.
 */
data class KmStoneMonitoringNotificationContent(
    val title: String,
    val contentText: String,
    val expandedText: String,
    val subText: String
)

private data class KmStoneMonitoringRenderSignature(
    val content: KmStoneMonitoringNotificationContent,
    val showOnLockScreen: Boolean
)

/**
 * Pure, testable formatter for the persistent KM Stone Monitoring notification.
 * Includes:
 * - KM Stone Monitoring active status
 * - Current chainage
 * - LHS / RHS direction
 * - Next KM Stone chainage & distance remaining
 * - GPS tracking status
 * - Current speed when valid
 */
object KmStoneMonitoringNotificationFormatter {

    fun resolveGpsStatusLabel(locationState: LocationState?, isLiveValid: Boolean): String {
        return when {
            locationState == null -> "GPS Acquiring"
            locationState.status == LocationStatus.PERMISSION_DENIED -> "GPS Permission Needed"
            locationState.status == LocationStatus.DISABLED -> "GPS Off"
            locationState.status == LocationStatus.POOR_ACCURACY ->
                "GPS Weak (±${locationState.accuracy.toInt()}m)"
            locationState.point == null -> "GPS Acquiring"
            !locationState.isUsable || locationState.accuracy > 30f ->
                "GPS Weak (±${locationState.accuracy.toInt()}m)"
            isLiveValid -> "GPS Live (±${locationState.accuracy.toInt()}m)"
            else -> "GPS Active"
        }
    }

    fun format(
        navState: LockScreenNavUiState,
        locationState: LocationState?,
        lastEvent: KmStoneAlertEvent? = null,
        conservingPower: Boolean = false
    ): KmStoneMonitoringNotificationContent {
        val validChainage = navState.chainageText.isNotBlank() && navState.chainageText != "--+---"
        val gpsLabel = resolveGpsStatusLabel(locationState, navState.isLiveGpsValid)

        val title = buildString {
            append("KM Stone Monitoring")
            if (validChainage) {
                append(" • CH ${navState.chainageText}")
                if (!navState.reliableSide.isNullOrBlank()) {
                    append(" ${navState.reliableSide}")
                } else if (navState.directionState == com.example.model.DirectionDetectionState.DIRECTION_CHANGING) {
                    append(" Changing")
                }
            } else if (!navState.hasRouteData) {
                append(" • Waiting for Route")
            } else {
                append(" • $gpsLabel")
            }
        }

        val compactParts = mutableListOf<String>()
        if (!navState.nextMarkerLabel.isNullOrBlank()) {
            val distSuffix = navState.distanceToNextText?.let { " in $it" }.orEmpty()
            compactParts.add("${navState.nextMarkerLabel}$distSuffix")
        } else if (!validChainage) {
            compactParts.add(navState.directionInstruction)
        }
        if (navState.speedKmh != null && navState.isLiveGpsValid) {
            compactParts.add("${navState.speedKmh} km/h")
        }
        compactParts.add(gpsLabel)
        val contentText = compactParts.joinToString(" • ")

        val expandedLines = mutableListOf<String>()
        expandedLines.add(
            buildString {
                append("Status: Active")
                if (conservingPower) append(" (Battery Saver)")
                append(" • ")
                append(gpsLabel)
            }
        )
        expandedLines.add(
            buildString {
                append("Current Chainage: ")
                append(if (validChainage) navState.chainageText else "Acquiring…")
                if (!navState.reliableSide.isNullOrBlank()) {
                    append(" (${navState.reliableSide}")
                    if (navState.directionArrow.isNotBlank()) append(" ${navState.directionArrow}")
                    if (navState.isDirectionManual) append(" • Manual")
                    append(")")
                } else if (validChainage) {
                    append(" (${navState.directionState.shortLabel})")
                }
            }
        )
        if (!navState.nextMarkerLabel.isNullOrBlank()) {
            val distText = navState.distanceToNextText ?: "--"
            expandedLines.add("${navState.nextMarkerLabel} • Remaining: $distText")
        } else if (!navState.hasRouteData) {
            expandedLines.add("Route: Load a KMZ/KML alignment to detect KM Stones")
        }
        if (navState.speedKmh != null && navState.isLiveGpsValid) {
            expandedLines.add("Speed: ${navState.speedKmh} km/h")
        }
        if (lastEvent != null) {
            val prefix = if (lastEvent.kind == KmStoneAlertKind.ARRIVAL) "Last Arrived" else "Approaching"
            val sidePart = lastEvent.directionSide.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            expandedLines.add("$prefix: KM ${lastEvent.stoneFormattedChainage}$sidePart")
        }
        if (!navState.roadOrStretchName.isNullOrBlank()) {
            expandedLines.add("Alignment: ${navState.roadOrStretchName}")
        }

        val subText = if (navState.isLiveGpsValid) "Monitoring Active" else gpsLabel

        return KmStoneMonitoringNotificationContent(
            title = title,
            contentText = contentText,
            expandedText = expandedLines.joinToString("\n"),
            subText = subText
        )
    }
}

/**
 * Dedicated Android Location Foreground Service for continuous KM Stone arrival monitoring.
 *
 * Architecture guarantees:
 * - Reuses the single shared [CtecsNavigationTelemetryHub.getOrCreateLocationTracker] and
 *   [CtecsNavigationTelemetryHub.sharedKmStoneDetector] (zero duplicate GPS listeners or engines).
 * - Maintains an in-place, silent, expandable persistent monitoring notification (stable ID 4110).
 * - Immediately dispatches high-priority KM Stone Arrival / Advance Warning notifications via
 *   [KmStoneAlertFeedbackManager] without delay even when conserving UI/notification redraws.
 * - Handles process recreation (`START_STICKY` + `ensureInitialized`), GPS loss/recovery,
 *   permission changes, Android 12+ background FGS restrictions, and safe teardown when disabled.
 */
class KmStoneMonitoringForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val navResolver = LockScreenNavStateResolver()
    private var collectorJob: Job? = null
    private var lastSignature: KmStoneMonitoringRenderSignature? = null
    private var lastNotificationPostMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CtecsNavigationTelemetryHub.ensureInitialized(applicationContext)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CtecsNavigationTelemetryHub.ensureInitialized(applicationContext)

        if (intent?.action == ACTION_STOP_MONITORING) {
            serviceScope.launch {
                try {
                    val pm = PreferencesManager(applicationContext)
                    val current = CtecsNavigationTelemetryHub.kmStoneAlertSettings.value
                    if (current.backgroundMonitoringEnabled) {
                        val updated = current.copy(backgroundMonitoringEnabled = false)
                        CtecsNavigationTelemetryHub.updateTelemetry(kmStoneAlertSettings = updated)
                        pm.updateKmStoneAlertSettings(updated)
                    }
                } catch (_: Exception) {}
            }
            stopMonitoringService()
            return START_NOT_STICKY
        }

        if (!hasLocationPermission(this)) {
            CtecsNavigationTelemetryHub.setKmStoneServiceRunning(
                running = false,
                note = "Location permission required for KM Stone monitoring"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val settings = CtecsNavigationTelemetryHub.kmStoneAlertSettings.value
        val hasRoute = CtecsNavigationTelemetryHub.activeRoute.value != null
        val isRestartAfterProcessDeath = intent == null
        // When recreated by Android after process death (`intent == null`), keep running if user
        // still has monitoring + background monitoring enabled so GPS/route recovery can resume.
        val allowRun = if (isRestartAfterProcessDeath) {
            settings.enabled && settings.backgroundMonitoringEnabled && settings.persistentNotificationEnabled
        } else {
            shouldServiceStayActive(settings, hasRoute)
        }

        if (!allowRun) {
            stopMonitoringService()
            return START_NOT_STICKY
        }

        val tracker = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(applicationContext)
        val initialNavState = navResolver.resolve(
            locationState = tracker.locationState.value,
            matchResult = CtecsNavigationTelemetryHub.matchResult.value,
            routeData = CtecsNavigationTelemetryHub.activeRoute.value,
            projectProfile = CtecsNavigationTelemetryHub.projectProfile.value,
            locationName = CtecsNavigationTelemetryHub.locationName.value,
            is24HourFormat = CtecsNavigationTelemetryHub.is24HourFormat.value
        )
        val powerState = DevicePowerThermalMonitor.snapshot(applicationContext)
        val initialContent = KmStoneMonitoringNotificationFormatter.format(
            navState = initialNavState,
            locationState = tracker.locationState.value,
            lastEvent = CtecsNavigationTelemetryHub.lastKmStoneEvent.value,
            conservingPower = powerState.shouldConserveUiResources
        )
        val initialSig = KmStoneMonitoringRenderSignature(
            content = initialContent,
            showOnLockScreen = settings.lockScreenNotificationsEnabled
        )
        val notification = buildMonitoringNotification(initialContent, settings)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            lastSignature = initialSig
            lastNotificationPostMs = System.currentTimeMillis()
        } catch (se: SecurityException) {
            CtecsNavigationTelemetryHub.setKmStoneServiceRunning(
                running = false,
                note = "Location foreground service denied: ${se.message ?: "permission required"}"
            )
            stopSelf()
            return START_NOT_STICKY
        } catch (e: Exception) {
            CtecsNavigationTelemetryHub.setKmStoneServiceRunning(
                running = false,
                note = e.message ?: "Unable to start KM Stone foreground service"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        // Reuse the single shared LocationTracker instance (never registers a second GPS listener)
        tracker.startTracking()
        CtecsNavigationTelemetryHub.setKmStoneServiceRunning(running = true, note = null)
        startMonitoringLoop()

        return START_STICKY
    }

    private fun startMonitoringLoop() {
        if (collectorJob?.isActive == true) return
        val tracker = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(applicationContext)
        collectorJob = serviceScope.launch {
            val telemetryFlow = combine(
                tracker.locationState,
                CtecsNavigationTelemetryHub.matchResult,
                CtecsNavigationTelemetryHub.activeRoute,
                CtecsNavigationTelemetryHub.projectProfile,
                CtecsNavigationTelemetryHub.locationName
            ) { loc, match, route, profile, locName ->
                // Check runtime permission revocation gracefully
                if (!hasLocationPermission(applicationContext)) {
                    CtecsNavigationTelemetryHub.setKmStoneServiceRunning(
                        running = false,
                        note = "Location permission revoked"
                    )
                    stopMonitoringService()
                    return@combine null
                }

                // Fallback geometric matching only if hub has no live match yet (e.g. cold service recovery)
                val effectiveMatch = if (match.chainage == null && route != null && loc.point != null && loc.isUsable) {
                    val rawRecovered = ChainageMatcher.match(
                        gpsPoint = loc.point,
                        gpsAccuracy = loc.accuracy,
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
                    CtecsNavigationTelemetryHub.updateTelemetry(directionState = recoveredDir)
                    if (recoveredDir.directionChangedFrom != null && !recoveredDir.isManualOverride) {
                        KmStoneVoiceAnnouncementManager.announceDirectionChange(
                            context = applicationContext,
                            directionState = recoveredDir,
                            settings = CtecsNavigationTelemetryHub.kmStoneAlertSettings.value,
                            voiceDirectionEnabled = dirPrefs.voiceDirectionAnnouncementsEnabled,
                            isRecordingActive = CtecsNavigationTelemetryHub.isCameraOrSurveyRecording.value
                        )
                    }
                    rawRecovered.copy(
                        detectedSide = recoveredDir.confirmedSide ?: "Unknown",
                        smartDirectionState = recoveredDir
                    )
                } else {
                    match
                }

                // Evaluate KM Stone arrivals immediately (independent of UI rendering or power-save throttling)
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
                        if (evt.kind != KmStoneAlertKind.VOICE_DISTANCE) {
                            CtecsNavigationTelemetryHub.recordKmStoneAlertEvent(evt)
                        }
                        KmStoneAlertFeedbackManager.dispatchAlert(
                            context = applicationContext,
                            event = evt,
                            settings = alertCfg,
                            isRecordingActive = isRec
                        )
                    }
                }

                val uiState = navResolver.resolve(
                    locationState = loc,
                    matchResult = effectiveMatch,
                    routeData = route,
                    projectProfile = profile,
                    locationName = locName,
                    is24HourFormat = CtecsNavigationTelemetryHub.is24HourFormat.value
                )
                Triple(uiState, loc, route != null)
            }

            combine(
                telemetryFlow,
                CtecsNavigationTelemetryHub.kmStoneAlertSettings,
                CtecsNavigationTelemetryHub.lastKmStoneEvent
            ) { telemetry, alertCfg, lastEvent ->
                Triple(telemetry, alertCfg, lastEvent)
            }.collect { (telemetry, alertCfg, lastEvent) ->
                if (telemetry == null) return@collect
                val (uiState, locState, hasRoute) = telemetry

                if (!shouldServiceStayActive(alertCfg, hasRoute)) {
                    stopMonitoringService()
                    return@collect
                }

                // Keep home-screen widgets synced while monitoring runs
                com.example.widget.CtecsWidgetUpdater.updateAllWidgets(applicationContext)

                val powerState = DevicePowerThermalMonitor.snapshot(applicationContext)
                val content = KmStoneMonitoringNotificationFormatter.format(
                    navState = uiState,
                    locationState = locState,
                    lastEvent = lastEvent,
                    conservingPower = powerState.shouldConserveUiResources
                )
                val signature = KmStoneMonitoringRenderSignature(
                    content = content,
                    showOnLockScreen = alertCfg.lockScreenNotificationsEnabled
                )
                val previous = lastSignature
                if (signature == previous) return@collect

                val nowMs = System.currentTimeMillis()
                val minIntervalMs = if (powerState.shouldConserveUiResources) {
                    POWER_SAVE_UPDATE_THROTTLE_MS
                } else {
                    NORMAL_UPDATE_THROTTLE_MS
                }
                val isCriticalChange = previous == null ||
                    previous.content.title != signature.content.title ||
                    previous.showOnLockScreen != signature.showOnLockScreen
                if (!isCriticalChange && (nowMs - lastNotificationPostMs) < minIntervalMs) {
                    return@collect
                }

                lastSignature = signature
                lastNotificationPostMs = nowMs
                postNotificationUpdate(content, alertCfg)
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = mgr.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "KM Stone Monitoring Service",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Persistent live chainage & KM Stone monitoring status while CTECS runs in the background"
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    internal fun buildMonitoringNotification(
        content: KmStoneMonitoringNotificationContent,
        settings: KmStoneAlertSettings
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            5101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, KmStoneMonitoringForegroundService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            5102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val visibility = if (settings.lockScreenNotificationsEnabled) {
            NotificationCompat.VISIBILITY_PUBLIC
        } else {
            NotificationCompat.VISIBILITY_PRIVATE
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ctecs_nav)
            .setContentTitle(content.title)
            .setContentText(content.contentText)
            .setSubText(content.subText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(content.title)
                    .bigText(content.expandedText)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setSound(null)
            .setVibrate(null)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(visibility)
            .setContentIntent(openPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun postNotificationUpdate(
        content: KmStoneMonitoringNotificationContent,
        settings: KmStoneAlertSettings
    ) {
        if (!hasNotificationPermission(this)) return
        try {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            mgr.notify(NOTIFICATION_ID, buildMonitoringNotification(content, settings))
        } catch (_: Exception) {}
    }

    private fun stopMonitoringService() {
        collectorJob?.cancel()
        collectorJob = null
        lastSignature = null
        CtecsNavigationTelemetryHub.setKmStoneServiceRunning(running = false, note = null)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        try {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            mgr?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        collectorJob?.cancel()
        lastSignature = null
        CtecsNavigationTelemetryHub.setKmStoneServiceRunning(running = false, note = null)
        try {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            mgr?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ctecs_km_stone_monitoring_channel"
        const val NOTIFICATION_ID = 4110
        const val ACTION_START_MONITORING = "com.example.action.START_KM_STONE_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.action.STOP_KM_STONE_MONITORING"

        private const val NORMAL_UPDATE_THROTTLE_MS = 1_000L
        private const val POWER_SAVE_UPDATE_THROTTLE_MS = 3_000L

        fun shouldServiceStayActive(
            settings: KmStoneAlertSettings,
            hasActiveRoute: Boolean
        ): Boolean = settings.shouldRunBackgroundForegroundService(hasActiveRoute)

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

        fun hasBackgroundLocationPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocationPermission(context)
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
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
                    appCtx.stopService(Intent(appCtx, KmStoneMonitoringForegroundService::class.java))
                } catch (_: Exception) {}
                try {
                    val mgr = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    mgr?.cancel(NOTIFICATION_ID)
                } catch (_: Exception) {}
                CtecsNavigationTelemetryHub.setKmStoneServiceRunning(running = false, note = null)
                return
            }

            if (!hasLocationPermission(appCtx)) {
                CtecsNavigationTelemetryHub.setKmStoneServiceRunning(
                    running = false,
                    note = "Grant location permission to start KM Stone monitoring"
                )
                return
            }

            val startIntent = Intent(appCtx, KmStoneMonitoringForegroundService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            try {
                ContextCompat.startForegroundService(appCtx, startIntent)
            } catch (e: Exception) {
                // Handles Android 12+ ForegroundServiceStartNotAllowedException cleanly without crashing
                CtecsNavigationTelemetryHub.setKmStoneServiceRunning(
                    running = false,
                    note = e.message ?: "Foreground service unavailable while app is restricted"
                )
            }
        }
    }
}
