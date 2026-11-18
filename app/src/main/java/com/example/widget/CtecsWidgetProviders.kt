package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.model.CtecsWidgetInstanceConfig
import com.example.model.CtecsWidgetSettings
import com.example.model.CtecsWidgetType
import com.example.model.GeoPoint
import com.example.model.RouteData
import com.example.navigation.CtecsNavigationTelemetryHub
import com.example.navigation.LockScreenNavStateResolver
import com.example.navigation.LockScreenNavUiState
import org.json.JSONObject

/**
 * Central coordinator that keeps all 4 CTECS Home-Screen AppWidget types synchronized with
 * live GPS, calibrated chainage, KMZ/KML alignment, and widget settings.
 *
 * Also persists a lightweight fast-boot snapshot in SharedPreferences so widgets preserve
 * their state and custom per-instance settings across app restarts and device reboots
 * without spawning duplicate GPS listeners.
 */
object CtecsWidgetUpdater {

    const val ACTION_WIDGET_MANUAL_REFRESH = "com.example.widget.ACTION_WIDGET_MANUAL_REFRESH"
    const val ACTION_WIDGET_SYNC_ALL = "com.example.widget.ACTION_WIDGET_SYNC_ALL"

    const val EXTRA_WIDGET_OPEN_TARGET = "com.example.widget.EXTRA_OPEN_TARGET"
    const val TARGET_DASHBOARD = "dashboard"
    const val TARGET_CHAINAGE = "chainage"
    const val TARGET_MINI_MAP = "mini_map"
    const val TARGET_CONFIRM_TRACKING_TOGGLE = "confirm_tracking_toggle"

    private const val PREFS_NAME = "ctecs_widget_fast_cache"
    private const val KEY_CACHED_WIDGET_SETTINGS_JSON = "cached_widget_settings_json"
    private const val KEY_CACHED_NAV_STATE_JSON = "cached_nav_state_json"
    private const val KEY_CACHED_LAT = "cached_lat"
    private const val KEY_CACHED_LNG = "cached_lng"
    private const val KEY_CACHED_HAS_LOC = "cached_has_loc"

    private val resolver = LockScreenNavStateResolver()
    private var lastUpdateSignature: String? = null
    private var lastUpdateEpochMs: Long = 0L
    private const val MIN_AUTOMATIC_PUSH_INTERVAL_MS = 450L

    fun cacheWidgetSettings(context: Context, settings: CtecsWidgetSettings) {
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_WIDGET_SETTINGS_JSON, settings.toJsonString())
                .apply()
        } catch (_: Exception) {}
    }

    fun loadCachedWidgetSettings(context: Context): CtecsWidgetSettings {
        val hubSettings = CtecsNavigationTelemetryHub.widgetSettings.value
        if (hubSettings != CtecsWidgetSettings.defaultSettings()) {
            return hubSettings
        }
        return try {
            val raw = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CACHED_WIDGET_SETTINGS_JSON, null)
            CtecsWidgetSettings.fromJsonString(raw)
        } catch (_: Exception) {
            hubSettings
        }
    }

    fun cacheTelemetryState(context: Context, navState: LockScreenNavUiState, point: GeoPoint?) {
        try {
            val obj = JSONObject().apply {
                put("chainageText", navState.chainageText)
                put("reliableSide", navState.reliableSide ?: "")
                put("directionArrow", navState.directionArrow)
                put("directionInstruction", navState.directionInstruction)
                put("roadOrStretchName", navState.roadOrStretchName ?: "")
                put("nextMarkerLabel", navState.nextMarkerLabel ?: "")
                put("distanceToNextText", navState.distanceToNextText ?: "")
                if (navState.distanceToNextMetres != null) {
                    put("distanceToNextMetres", navState.distanceToNextMetres)
                }
                if (navState.speedKmh != null) {
                    put("speedKmh", navState.speedKmh)
                }
                put("speedText", navState.speedText)
                put("currentTimeText", navState.currentTimeText)
                put("locationName", navState.locationName)
                put("isLiveGpsValid", navState.isLiveGpsValid)
                put("hasRouteData", navState.hasRouteData)
                put("lastUpdateStatusText", navState.lastUpdateStatusText ?: "")
            }
            val edit = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_NAV_STATE_JSON, obj.toString())
            if (point != null) {
                edit.putBoolean(KEY_CACHED_HAS_LOC, true)
                edit.putString(KEY_CACHED_LAT, point.latitude.toString())
                edit.putString(KEY_CACHED_LNG, point.longitude.toString())
            }
            edit.apply()
        } catch (_: Exception) {}
    }

    fun resolveCurrentNavState(context: Context): Pair<LockScreenNavUiState, GeoPoint?> {
        val appCtx = context.applicationContext
        val tracker = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(appCtx)
        val locState = tracker.locationState.value
        val match = CtecsNavigationTelemetryHub.matchResult.value
        val route = CtecsNavigationTelemetryHub.activeRoute.value
        val profile = CtecsNavigationTelemetryHub.projectProfile.value
        val locName = CtecsNavigationTelemetryHub.locationName.value
        val is24h = CtecsNavigationTelemetryHub.is24HourFormat.value

        val liveResolved = resolver.resolve(
            locationState = locState,
            matchResult = match,
            routeData = route,
            projectProfile = profile,
            locationName = locName,
            is24HourFormat = is24h
        )

        if (liveResolved.chainageText != "--+---" || locState.point != null || route != null) {
            cacheTelemetryState(appCtx, liveResolved, locState.point)
            return liveResolved to locState.point
        }

        // Fallback to cached snapshot across reboot / process start so last valid chainage is retained
        return try {
            val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_CACHED_NAV_STATE_JSON, null)
            val cachedPt = if (prefs.getBoolean(KEY_CACHED_HAS_LOC, false)) {
                val lat = prefs.getString(KEY_CACHED_LAT, null)?.toDoubleOrNull()
                val lng = prefs.getString(KEY_CACHED_LNG, null)?.toDoubleOrNull()
                if (lat != null && lng != null) GeoPoint(lat, lng) else null
            } else null
            if (!raw.isNullOrBlank()) {
                val obj = JSONObject(raw)
                val cachedState = LockScreenNavUiState(
                    chainageText = obj.optString("chainageText", "--+---"),
                    reliableSide = obj.optString("reliableSide", "").takeIf { it.isNotBlank() },
                    directionArrow = obj.optString("directionArrow", ""),
                    directionInstruction = obj.optString("directionInstruction", "Waiting for GPS alignment"),
                    roadOrStretchName = obj.optString("roadOrStretchName", "").takeIf { it.isNotBlank() },
                    nextMarkerLabel = obj.optString("nextMarkerLabel", "").takeIf { it.isNotBlank() },
                    distanceToNextText = obj.optString("distanceToNextText", "").takeIf { it.isNotBlank() },
                    distanceToNextMetres = if (obj.has("distanceToNextMetres")) obj.optLong("distanceToNextMetres") else null,
                    speedKmh = null,
                    speedText = "-- km/h",
                    currentTimeText = liveResolved.currentTimeText,
                    locationName = obj.optString("locationName", ""),
                    isLiveGpsValid = false,
                    hasRouteData = obj.optBoolean("hasRouteData", false),
                    lastUpdateStatusText = "GPS unavailable"
                )
                cachedState to cachedPt
            } else {
                liveResolved to null
            }
        } catch (_: Exception) {
            liveResolved to null
        }
    }

    fun getActiveWidgetInstances(context: Context): List<Pair<Int, CtecsWidgetType>> {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx) ?: return emptyList()
        val result = mutableListOf<Pair<Int, CtecsWidgetType>>()
        val providers = listOf(
            CtecsCompactWidgetProvider::class.java to CtecsWidgetType.COMPACT,
            CtecsNavigationWidgetProvider::class.java to CtecsWidgetType.NAVIGATION,
            CtecsMiniMapWidgetProvider::class.java to CtecsWidgetType.MINI_MAP,
            CtecsDashboardWidgetProvider::class.java to CtecsWidgetType.DASHBOARD
        )
        for ((cls, type) in providers) {
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(appCtx, cls))
            } catch (_: Exception) {
                IntArray(0)
            }
            for (id in ids) {
                result.add(id to type)
            }
        }
        return result
    }

    fun updateAllWidgets(context: Context, force: Boolean = false) {
        val appCtx = context.applicationContext
        val instances = getActiveWidgetInstances(appCtx)
        if (instances.isEmpty()) return

        val (navState, point) = resolveCurrentNavState(appCtx)
        val settings = loadCachedWidgetSettings(appCtx)
        val route = CtecsNavigationTelemetryHub.activeRoute.value
        val isServiceRunning = CtecsNavigationTelemetryHub.isServiceRunning.value

        val sig = buildString {
            append(navState.chainageText).append('|')
            append(navState.reliableSide).append('|')
            append(navState.speedText).append('|')
            append(navState.nextMarkerLabel).append('|')
            append(navState.distanceToNextMetres).append('|')
            append(navState.isLiveGpsValid).append('|')
            append(navState.locationName).append('|')
            append(route?.id).append('|')
            append(isServiceRunning).append('|')
            append(settings.toJsonString())
        }
        val now = System.currentTimeMillis()
        if (!force && sig == lastUpdateSignature && (now - lastUpdateEpochMs) < 15_000L) {
            return
        }
        if (!force && (now - lastUpdateEpochMs) < MIN_AUTOMATIC_PUSH_INTERVAL_MS) {
            return
        }
        lastUpdateSignature = sig
        lastUpdateEpochMs = now

        val mgr = AppWidgetManager.getInstance(appCtx) ?: return
        for ((widgetId, type) in instances) {
            updateSingleWidget(
                context = appCtx,
                appWidgetManager = mgr,
                appWidgetId = widgetId,
                widgetType = type,
                navState = navState,
                point = point,
                settings = settings
            )
        }
    }

    fun updateSingleWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        widgetType: CtecsWidgetType,
        navState: LockScreenNavUiState? = null,
        point: GeoPoint? = null,
        settings: CtecsWidgetSettings? = null,
        optionsOverride: Bundle? = null
    ) {
        val appCtx = context.applicationContext
        val (resolvedNav, resolvedPt) = if (navState != null) {
            navState to point
        } else {
            resolveCurrentNavState(appCtx)
        }
        val resolvedSettings = settings ?: loadCachedWidgetSettings(appCtx)
        val instanceCfg: CtecsWidgetInstanceConfig = resolvedSettings.resolveForInstance(appWidgetId, widgetType)

        val options = optionsOverride ?: try {
            appWidgetManager.getAppWidgetOptions(appWidgetId)
        } catch (_: Exception) {
            null
        }
        val defaultW = when (widgetType) {
            CtecsWidgetType.COMPACT -> 200
            CtecsWidgetType.NAVIGATION -> 280
            CtecsWidgetType.MINI_MAP -> 240
            CtecsWidgetType.DASHBOARD -> 300
        }
        val defaultH = when (widgetType) {
            CtecsWidgetType.COMPACT -> 68
            CtecsWidgetType.NAVIGATION -> 125
            CtecsWidgetType.MINI_MAP -> 160
            CtecsWidgetType.DASHBOARD -> 165
        }
        val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaultW)
            ?.takeIf { it > 0 } ?: defaultW
        val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defaultH)
            ?.takeIf { it > 0 } ?: defaultH

        val tracker = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(appCtx)
        val bearing = tracker.locationState.value.bearing
        val route = CtecsNavigationTelemetryHub.activeRoute.value
        val isTrackingActive = CtecsNavigationTelemetryHub.isServiceRunning.value

        val views = buildRemoteViewsForInstance(
            context = appCtx,
            appWidgetId = appWidgetId,
            instanceConfig = instanceCfg,
            navState = resolvedNav,
            widthDp = widthDp,
            heightDp = heightDp,
            location = resolvedPt,
            bearing = bearing,
            routeData = route,
            isTrackingActive = isTrackingActive
        )

        try {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (_: Exception) {}
    }

    fun buildRemoteViewsForInstance(
        context: Context,
        appWidgetId: Int,
        instanceConfig: CtecsWidgetInstanceConfig,
        navState: LockScreenNavUiState,
        widthDp: Int,
        heightDp: Int,
        location: GeoPoint? = null,
        bearing: Float? = null,
        routeData: RouteData? = null,
        isTrackingActive: Boolean = false
    ): RemoteViews {
        val rendered = CtecsWidgetRenderer.renderWidgetBitmap(
            context = context,
            navState = navState,
            instanceConfig = instanceConfig,
            widthDp = widthDp,
            heightDp = heightDp,
            location = location,
            bearing = bearing,
            routeData = routeData,
            isTrackingActive = isTrackingActive
        )

        val views = RemoteViews(context.packageName, R.layout.widget_ctecs_host)
        views.setImageViewBitmap(R.id.widget_canvas_image, rendered.bitmap)

        // Populate accessible text mirrors
        views.setTextViewText(R.id.widget_text_chainage, rendered.displayedChainage)
        views.setTextViewText(R.id.widget_text_direction, rendered.displayedDirection)
        views.setTextViewText(R.id.widget_text_speed, rendered.displayedSpeed)
        views.setTextViewText(R.id.widget_text_next_km, rendered.displayedNextKm)
        views.setTextViewText(R.id.widget_text_status, rendered.displayedStatus)

        // 1. Tap Widget Header -> Open Main Navigation Dashboard
        views.setOnClickPendingIntent(
            R.id.widget_zone_main,
            buildMainActivityPendingIntent(context, appWidgetId * 10 + 1, TARGET_DASHBOARD)
        )
        // 2. Tap Chainage -> Open CTECS Chainage Screen
        views.setOnClickPendingIntent(
            R.id.widget_zone_chainage,
            buildMainActivityPendingIntent(context, appWidgetId * 10 + 2, TARGET_CHAINAGE)
        )
        // 3. Tap Mini Map / Right Zone -> Open Navigation Map Screen
        views.setOnClickPendingIntent(
            R.id.widget_zone_minimap,
            buildMainActivityPendingIntent(context, appWidgetId * 10 + 3, TARGET_MINI_MAP)
        )
        // 4. Tap Tracking Start/Stop -> Open Confirmation Dialog in CTECS
        views.setOnClickPendingIntent(
            R.id.widget_btn_tracking,
            buildMainActivityPendingIntent(context, appWidgetId * 10 + 4, TARGET_CONFIRM_TRACKING_TOGGLE)
        )
        // 5. Tap Manual Refresh -> Broadcast immediate widget & GPS refresh
        val refreshIntent = Intent(context, CtecsCompactWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_MANUAL_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPending = PendingIntent.getBroadcast(
            context,
            appWidgetId * 10 + 5,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPending)

        return views
    }

    private fun buildMainActivityPendingIntent(
        context: Context,
        requestCode: Int,
        target: String
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_WIDGET_OPEN_TARGET, target)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

abstract class BaseCtecsWidgetProvider(
    private val widgetType: CtecsWidgetType
) : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            CtecsWidgetUpdater.ACTION_WIDGET_MANUAL_REFRESH -> {
                // Trigger immediate GPS check on existing single LocationTracker & refresh all widgets
                try {
                    val tracker = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(context.applicationContext)
                    tracker.checkAndRecoverIfStale(3_000L)
                } catch (_: Exception) {}
                CtecsWidgetUpdater.updateAllWidgets(context, force = true)
            }
            CtecsWidgetUpdater.ACTION_WIDGET_SYNC_ALL,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                CtecsWidgetUpdater.updateAllWidgets(context, force = true)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            CtecsWidgetUpdater.updateSingleWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = widgetId,
                widgetType = widgetType
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        CtecsWidgetUpdater.updateSingleWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = appWidgetId,
            widgetType = widgetType,
            optionsOverride = newOptions
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        var current = CtecsWidgetUpdater.loadCachedWidgetSettings(context)
        for (id in appWidgetIds) {
            current = current.withoutInstanceConfig(id)
        }
        CtecsNavigationTelemetryHub.updateTelemetry(widgetSettings = current)
        CtecsWidgetUpdater.cacheWidgetSettings(context, current)
    }
}

/** A. Compact Widget */
class CtecsCompactWidgetProvider : BaseCtecsWidgetProvider(CtecsWidgetType.COMPACT)

/** B. Navigation Widget */
class CtecsNavigationWidgetProvider : BaseCtecsWidgetProvider(CtecsWidgetType.NAVIGATION)

/** C. Mini Map Widget */
class CtecsMiniMapWidgetProvider : BaseCtecsWidgetProvider(CtecsWidgetType.MINI_MAP)

/** D. Dashboard Widget */
class CtecsDashboardWidgetProvider : BaseCtecsWidgetProvider(CtecsWidgetType.DASHBOARD)
