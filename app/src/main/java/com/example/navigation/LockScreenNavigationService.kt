package com.example.navigation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class LockScreenNotificationSummary(
    val title: String,
    val contentText: String
)

object LockScreenNotificationFormatter {
    fun format(navState: LockScreenNavUiState): LockScreenNotificationSummary {
        val sideSuffix = navState.reliableSide?.let { side ->
            " • $side ${navState.directionArrow}".trimEnd()
        }.orEmpty()
        val title = if (navState.chainageText.isNotBlank() && navState.chainageText != "--+---") {
            "CH ${navState.chainageText}$sideSuffix"
        } else {
            "CTECS Navigation Active"
        }

        val details = buildList {
            if (!navState.distanceToNextText.isNullOrBlank()) {
                val markerPart = navState.nextMarkerLabel?.let { "$it (${navState.distanceToNextText})" }
                    ?: navState.distanceToNextText
                add(markerPart)
            }
            if (navState.speedKmh != null) {
                add(navState.speedText)
            }
            if (!navState.etaDurationText.isNullOrBlank()) {
                add("ETA ${navState.etaDurationText}")
            }
            if (isEmpty()) {
                add(navState.lastUpdateStatusText ?: navState.directionInstruction)
            }
        }
        return LockScreenNotificationSummary(
            title = title,
            contentText = details.joinToString(" • ")
        )
    }
}

/**
 * Battery-conscious Foreground Location Service keeping live chainage navigation synced while
 * locked, exposing an ongoing VISIBILITY_PUBLIC lock-screen notification and optionally launching
 * [LockScreenNavigationActivity] when the device locks (never bypassing keyguard security).
 */
class LockScreenNavigationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val navResolver = LockScreenNavStateResolver()
    private var collectorJob: Job? = null
    private var lastNotificationPostMs = 0L
    private var lastPostedChainage = ""
    private var lastPostedSide: String? = null
    private var screenReceiverRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val cfg = CtecsNavigationTelemetryHub.screensaverSettings.value
                if (cfg.lockScreenNavEnabled && cfg.lockScreenOpenActivityOnLock) {
                    launchLockScreenActivitySafely(this@LockScreenNavigationService)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_NAVIGATION -> {
                serviceScope.launch {
                    val pm = PreferencesManager(applicationContext)
                    val current = CtecsNavigationTelemetryHub.screensaverSettings.value
                    val updated = current.copy(lockScreenNavEnabled = false)
                    CtecsNavigationTelemetryHub.updateTelemetry(screensaverSettings = updated)
                    pm.updateHomeScreensaverSettings(updated)
                }
                stopNavigationService()
                return START_NOT_STICKY
            }
        }

        if (!hasLocationPermission(this)) {
            CtecsNavigationTelemetryHub.setServiceRunning(
                running = false,
                note = "Location permission required for lock-screen navigation"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val initialState = currentSnapshotState()
        val initialNotification = buildNotification(initialState)
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
        registerScreenReceiver()
        startTelemetryObserver()

        return START_STICKY
    }

    private fun startTelemetryObserver() {
        if (collectorJob?.isActive == true) return
        val tracker = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(applicationContext)
        collectorJob = serviceScope.launch {
            combine(
                tracker.locationState,
                CtecsNavigationTelemetryHub.matchResult,
                CtecsNavigationTelemetryHub.activeRoute,
                CtecsNavigationTelemetryHub.projectProfile,
                CtecsNavigationTelemetryHub.locationName
            ) { loc, match, route, profile, locName ->
                navResolver.resolve(
                    locationState = loc,
                    matchResult = match,
                    routeData = route,
                    projectProfile = profile,
                    locationName = locName,
                    is24HourFormat = CtecsNavigationTelemetryHub.is24HourFormat.value
                )
            }.collect { uiState ->
                val now = System.currentTimeMillis()
                val majorChange = uiState.chainageText != lastPostedChainage ||
                    uiState.reliableSide != lastPostedSide
                if (majorChange || (now - lastNotificationPostMs) >= NOTIFICATION_THROTTLE_MS) {
                    lastNotificationPostMs = now
                    lastPostedChainage = uiState.chainageText
                    lastPostedSide = uiState.reliableSide
                    val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    mgr?.notify(NOTIFICATION_ID, buildNotification(uiState))
                }
            }
        }
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

    private fun buildNotification(navState: LockScreenNavUiState): Notification {
        val summary = LockScreenNotificationFormatter.format(navState)

        val openIntent = Intent(this, LockScreenNavigationActivity::class.java).apply {
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

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(summary.title)
            .setContentText(summary.contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.contentText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = mgr.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Live Chainage Lock-Screen Navigation",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Displays live calibrated highway chainage and alignment updates on the lock screen."
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenOffReceiver, filter)
            }
            screenReceiverRegistered = true
        } catch (_: Exception) {}
    }

    private fun stopNavigationService() {
        collectorJob?.cancel()
        collectorJob = null
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (_: Exception) {}
            screenReceiverRegistered = false
        }
        CtecsNavigationTelemetryHub.setServiceRunning(running = false, note = null)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        collectorJob?.cancel()
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (_: Exception) {}
            screenReceiverRegistered = false
        }
        CtecsNavigationTelemetryHub.setServiceRunning(running = false, note = null)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ctecs_lock_nav_channel"
        const val NOTIFICATION_ID = 4108
        const val ACTION_START_NAVIGATION = "com.example.action.START_LOCK_NAV"
        const val ACTION_STOP_NAVIGATION = "com.example.action.STOP_LOCK_NAV"
        private const val NOTIFICATION_THROTTLE_MS = 1500L

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
                CtecsNavigationTelemetryHub.setServiceRunning(running = false, note = null)
                return
            }
            if (!hasLocationPermission(appCtx)) {
                CtecsNavigationTelemetryHub.setServiceRunning(
                    running = false,
                    note = "Grant location permission to start live lock-screen navigation."
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

        fun launchLockScreenActivitySafely(context: Context) {
            try {
                val intent = Intent(context, LockScreenNavigationActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                // Restricted on certain OEM builds when launched from background; notification remains active.
            }
        }
    }
}
