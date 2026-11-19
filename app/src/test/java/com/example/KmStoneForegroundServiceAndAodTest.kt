package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.Chainage
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.KmStoneAlertSettings
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.ScreensaverDisplayMode
import com.example.model.ScreensaverThemeMode
import com.example.navigation.CtecsNavigationTelemetryHub
import com.example.navigation.KmStoneAlertEvent
import com.example.navigation.KmStoneAlertFeedbackManager
import com.example.navigation.KmStoneAlertKind
import com.example.navigation.KmStoneMonitoringForegroundService
import com.example.navigation.KmStoneMonitoringNotificationFormatter
import com.example.navigation.LockScreenNavUiState
import com.example.ui.home.AmoledBurnInProtection
import com.example.ui.home.ChainageScreensaverOverlay
import com.example.ui.home.DashboardMirrorScaleXKey
import com.example.ui.home.ScreensaverAodActiveKey
import com.example.ui.home.ScreensaverDisplayModeKey
import com.example.ui.home.ScreensaverDisplayState
import com.example.ui.home.ScreensaverThemeDarkKey
import com.example.ui.home.resolveAodKmStoneStatusText
import com.example.ui.settings.KmStoneAlertSettingsSectionContent
import com.example.ui.settings.ScreensaverSettingsSectionContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KmStoneForegroundServiceAndAodTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var context: Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        CtecsNavigationTelemetryHub.setKmStoneServiceRunning(false, null)
        CtecsNavigationTelemetryHub.recordKmStoneAlertEvent(null)
    }

    private fun sampleRoute(): RouteData {
        val pts = listOf(
            GeoPoint(29.500, 77.700),
            GeoPoint(29.510, 77.710),
            GeoPoint(29.520, 77.720)
        )
        return RouteData(
            id = "route_aod_fgs_test",
            name = "NH-58 Alignment",
            fileName = "nh58.kmz",
            markers = listOf(
                RouteMarker("k1", "KM 200+000", Chainage.fromMetres(200_000L), GeoPoint(29.500, 77.700)),
                RouteMarker("k2", "KM 201+000", Chainage.fromMetres(201_000L), GeoPoint(29.510, 77.710)),
                RouteMarker("k3", "KM 202+000", Chainage.fromMetres(202_000L), GeoPoint(29.520, 77.720))
            ),
            alignments = listOf(RouteAlignment("a1", "Main", pts)),
            bounds = GeoBounds(29.500, 29.520, 77.700, 77.720),
            startChainage = Chainage.fromMetres(200_000L),
            endChainage = Chainage.fromMetres(202_000L)
        )
    }

    private fun sampleDisplayState(chainage: String = "200+850", speed: Int? = 58): ScreensaverDisplayState {
        return ScreensaverDisplayState(
            chainageText = chainage,
            reliableSide = "LHS",
            directionArrow = "↑",
            isLiveGpsValid = speed != null,
            isIncreasingTrend = true,
            locationName = "NH-58 Corridor",
            speedText = if (speed != null) "$speed km/h" else "-- km/h",
            speedKmh = speed
        )
    }

    @Test
    fun kmStoneForegroundService_policyStartStopAndSingleGpsReuse() {
        val fullOn = KmStoneAlertSettings(
            enabled = true,
            backgroundMonitoringEnabled = true,
            persistentNotificationEnabled = true
        )
        assertTrue(KmStoneMonitoringForegroundService.shouldServiceStayActive(fullOn, hasActiveRoute = true))
        assertFalse(KmStoneMonitoringForegroundService.shouldServiceStayActive(fullOn, hasActiveRoute = false))
        assertFalse(
            KmStoneMonitoringForegroundService.shouldServiceStayActive(
                fullOn.copy(backgroundMonitoringEnabled = false),
                hasActiveRoute = true
            )
        )
        assertFalse(
            KmStoneMonitoringForegroundService.shouldServiceStayActive(
                fullOn.copy(persistentNotificationEnabled = false),
                hasActiveRoute = true
            )
        )
        assertFalse(
            KmStoneMonitoringForegroundService.shouldServiceStayActive(
                fullOn.copy(enabled = false),
                hasActiveRoute = true
            )
        )

        // Reuse single LocationTracker instance across app and service
        val trackerBefore = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(context)
        CtecsNavigationTelemetryHub.updateTelemetry(
            routeData = sampleRoute(),
            kmStoneAlertSettings = fullOn
        )

        val controller = Robolectric.buildService(KmStoneMonitoringForegroundService::class.java).create()
        val service = controller.get()
        val startResult = service.onStartCommand(
            Intent(context, KmStoneMonitoringForegroundService::class.java).apply {
                action = KmStoneMonitoringForegroundService.ACTION_START_MONITORING
            },
            0,
            1
        )
        assertEquals(android.app.Service.START_STICKY, startResult)
        assertTrue("Service must mark hub as running", CtecsNavigationTelemetryHub.isKmStoneServiceRunning.value)
        assertSame(
            "Foreground service must reuse the single shared LocationTracker without duplicates",
            trackerBefore,
            CtecsNavigationTelemetryHub.getOrCreateLocationTracker(context)
        )

        // Stop action cleanly tears down service
        val stopResult = service.onStartCommand(
            Intent(context, KmStoneMonitoringForegroundService::class.java).apply {
                action = KmStoneMonitoringForegroundService.ACTION_STOP_MONITORING
            },
            0,
            2
        )
        assertEquals(android.app.Service.START_NOT_STICKY, stopResult)
        assertFalse("Service must mark hub stopped on explicit stop", CtecsNavigationTelemetryHub.isKmStoneServiceRunning.value)
        controller.destroy()
    }

    @Test
    fun kmStoneForegroundService_handlesMissingPermissionGracefullyWithoutCrashing() {
        Shadows.shadowOf(app).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        CtecsNavigationTelemetryHub.updateTelemetry(
            routeData = sampleRoute(),
            kmStoneAlertSettings = KmStoneAlertSettings(enabled = true, backgroundMonitoringEnabled = true)
        )

        val controller = Robolectric.buildService(KmStoneMonitoringForegroundService::class.java).create()
        val service = controller.get()
        val startResult = service.onStartCommand(
            Intent(context, KmStoneMonitoringForegroundService::class.java),
            0,
            1
        )
        assertEquals(android.app.Service.START_NOT_STICKY, startResult)
        assertFalse(CtecsNavigationTelemetryHub.isKmStoneServiceRunning.value)
        assertNotNull(CtecsNavigationTelemetryHub.kmStoneServiceStatusNote.value)
        controller.destroy()
    }

    @Test
    fun persistentMonitoringNotification_containsAllTelemetryFieldsSilentAndRespectsLockScreenVisibility() {
        val navState = LockScreenNavUiState(
            chainageText = "200+850",
            reliableSide = "LHS",
            directionArrow = "↑",
            isIncreasingTrend = true,
            directionInstruction = "Increasing Chainage (LHS)",
            roadOrStretchName = "NH-58 Alignment",
            nextMarkerLabel = "Next KM 201+000",
            distanceToNextText = "150 m",
            distanceToNextMetres = 150L,
            speedKmh = 60,
            speedText = "60 km/h",
            isLiveGpsValid = true,
            hasRouteData = true
        )
        val locState = LocationState(
            status = LocationStatus.READY,
            point = GeoPoint(29.508, 77.708),
            accuracy = 4.5f,
            speed = 16.7f,
            lastFixTime = System.currentTimeMillis()
        )

        val formatted = KmStoneMonitoringNotificationFormatter.format(
            navState = navState,
            locationState = locState,
            lastEvent = null,
            conservingPower = false
        )
        assertTrue(formatted.title.contains("KM Stone Monitoring"))
        assertTrue(formatted.title.contains("200+850"))
        assertTrue(formatted.title.contains("LHS"))
        assertTrue(formatted.contentText.contains("Next KM 201+000"))
        assertTrue(formatted.contentText.contains("150 m"))
        assertTrue(formatted.contentText.contains("60 km/h"))
        assertTrue(formatted.contentText.contains("GPS Live"))
        assertTrue(formatted.expandedText.contains("Current Chainage: 200+850 (LHS"))
        assertTrue(formatted.expandedText.contains("Remaining: 150 m"))

        val controller = Robolectric.buildService(KmStoneMonitoringForegroundService::class.java).create()
        val service = controller.get()

        val publicNotif = service.buildMonitoringNotification(
            formatted,
            KmStoneAlertSettings(lockScreenNotificationsEnabled = true)
        )
        assertEquals(Notification.VISIBILITY_PUBLIC, publicNotif.visibility)
        assertTrue((publicNotif.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertTrue((publicNotif.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
        assertNotNull("Tap action to open CTECS must exist", publicNotif.contentIntent)
        assertNotNull("Stop action must exist", publicNotif.actions?.firstOrNull { it.title == "Stop" })

        val privateNotif = service.buildMonitoringNotification(
            formatted,
            KmStoneAlertSettings(lockScreenNotificationsEnabled = false)
        )
        assertEquals(Notification.VISIBILITY_PRIVATE, privateNotif.visibility)
        controller.destroy()
    }

    @Test
    fun arrivalAlerts_respectArrivalAlertsToggleAndPublishToTelemetryHub() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()

        val evt = KmStoneAlertEvent(
            kind = KmStoneAlertKind.ARRIVAL,
            stoneChainageMetres = 201_000L,
            stoneFormattedChainage = "201+000",
            currentChainageMetres = 201_002L,
            currentFormattedChainage = "201+002",
            directionSide = "LHS",
            distanceMetres = 2L,
            routeId = "route_aod_fgs_test",
            timestampMs = 100_000L
        )

        // When arrivalAlertsEnabled == false, no notification/sound/vibration triggers
        val mutedRecord = KmStoneAlertFeedbackManager.dispatchAlert(
            context = context,
            event = evt,
            settings = KmStoneAlertSettings(enabled = true, arrivalAlertsEnabled = false, soundEnabled = false)
        )
        assertFalse(mutedRecord.notificationPosted)
        assertFalse(mutedRecord.vibrationTriggered)
        assertEquals(evt, CtecsNavigationTelemetryHub.lastKmStoneEvent.value)

        // When arrivalAlertsEnabled == true, notification dispatches immediately
        val activeRecord = KmStoneAlertFeedbackManager.dispatchAlert(
            context = context,
            event = evt,
            settings = KmStoneAlertSettings(enabled = true, arrivalAlertsEnabled = true, soundEnabled = false)
        )
        assertTrue(activeRecord.notificationPosted)
        val statusText = resolveAodKmStoneStatusText(evt, isMonitoringActive = true, nowMs = 105_000L)
        assertTrue(statusText.contains("Arrived KM 201+000"))
        assertTrue(statusText.contains("LHS"))
    }

    @Test
    fun amoledBurnInProtection_shiftsSafelyWithinBoundsAndDimsWindowBrightness() {
        val disabledOffset = AmoledBurnInProtection.computeShiftOffset(stepIndex = 5, enabled = false)
        assertEquals(0f, disabledOffset.xDp, 0.001f)
        assertEquals(0f, disabledOffset.yDp, 0.001f)

        val step0 = AmoledBurnInProtection.computeShiftOffset(stepIndex = 0, enabled = true, maxShiftDp = 14f)
        val step3 = AmoledBurnInProtection.computeShiftOffset(stepIndex = 3, enabled = true, maxShiftDp = 14f)
        assertTrue(abs(step0.xDp) <= 14.01f && abs(step0.yDp) <= 14.01f)
        assertTrue(abs(step3.xDp) <= 14.01f && abs(step3.yDp) <= 14.01f)
        assertNotEquals("Burn-in offset must shift over time", step0, step3)

        val normalBrightness = AmoledBurnInProtection.resolveWindowBrightness(0.38f, conservePower = false)
        val powerSaveBrightness = AmoledBurnInProtection.resolveWindowBrightness(0.38f, conservePower = true)
        assertTrue(normalBrightness in 0.03f..0.22f)
        assertTrue("Power-save brightness must be dimmer than normal AOD", powerSaveBrightness < normalBrightness)
    }

    @Test
    fun screensaver_amoledAodTogglesPausesHeavyUiRespectsSelectedFieldsAndRestoresMirrorMode() {
        var isAodActive by mutableStateOf(false)
        var isScreenOff by mutableStateOf(false)
        var dismissCount = 0
        var liveChainage by mutableStateOf("200+850")
        var settings by mutableStateOf(
            HomeScreensaverSettings(
                displayMode = ScreensaverDisplayMode.MIRROR,
                autoOrientationEnabled = false,
                mirrorModePreferred = true,
                screensaverThemeMode = ScreensaverThemeMode.DARK,
                aodEnabled = true,
                aodShowChainage = true,
                aodShowDirectionSide = true,
                aodShowTime = false,
                aodShowGpsStatus = true,
                aodShowKmStoneStatus = true,
                aodShowNextKmStone = true,
                aodBurnInProtectionEnabled = true
            )
        )

        composeRule.setContent {
            ChainageScreensaverOverlay(
                displayState = sampleDisplayState(chainage = liveChainage),
                location = GeoPoint(29.508, 77.708),
                bearing = 45f,
                compassHeading = 45f,
                routeData = sampleRoute(),
                isDarkAppTheme = true,
                screensaverSettings = settings,
                isScreenOff = isScreenOff,
                isAodActive = isAodActive,
                nextKmLabel = "Next KM 201+000",
                distanceToNextMetres = 150L,
                isKmStoneMonitoringActive = true,
                onToggleScreenOff = { off -> isScreenOff = off },
                onToggleAod = { active -> isAodActive = active },
                onDismiss = { dismissCount += 1 }
            )
        }

        // Normal Mirror Screensaver displays quick AOD button, chainage, and Mini Map
        composeRule.onNodeWithTag("screensaver_aod_toggle_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_glass_map").assertIsDisplayed()

        // Tap AOD button -> activates pure-black AMOLED AOD and unmounts heavy dashboard/map
        composeRule.onNodeWithTag("screensaver_aod_toggle_btn").performClick()
        composeRule.waitForIdle()
        assertTrue(isAodActive)
        assertEquals(0, dismissCount)
        composeRule.onNodeWithTag("screensaver_amoled_aod_root")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(ScreensaverAodActiveKey, true))
        composeRule.onNodeWithTag("screensaver_glass_map").assertDoesNotExist()
        composeRule.onNodeWithTag("aod_chainage_text").assertIsDisplayed()
        composeRule.onNodeWithTag("aod_direction_row").assertIsDisplayed()
        composeRule.onNodeWithTag("aod_next_km_text").assertIsDisplayed()
        composeRule.onNodeWithTag("aod_gps_status_row").assertIsDisplayed()
        composeRule.onNodeWithTag("aod_km_stone_status_row").assertIsDisplayed()
        // Time was disabled in settings -> must NOT appear
        composeRule.onNodeWithTag("aod_time_text").assertDoesNotExist()

        // Live chainage updates while AOD is active; tapping exit restores normal Mirror Screensaver cleanly
        composeRule.runOnIdle { liveChainage = "201+010" }
        composeRule.onNodeWithTag("aod_exit_to_screensaver_btn").performClick()
        composeRule.waitForIdle()

        assertFalse(isAodActive)
        assertEquals(0, dismissCount)
        composeRule.onNodeWithTag("screensaver_amoled_aod_root").assertDoesNotExist()
        composeRule.onNodeWithTag("screensaver_glass_map").assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_dashboard_root")
            .assert(SemanticsMatcher.expectValue(DashboardMirrorScaleXKey, -1f))
            .assert(SemanticsMatcher.expectValue(ScreensaverDisplayModeKey, ScreensaverDisplayMode.MIRROR.name))
            .assert(SemanticsMatcher.expectValue(ScreensaverThemeDarkKey, true))
    }

    @Test
    fun settingsPersistenceAndUi_roundTripNewMonitoringAndAodSettingsAcrossRestart() = runBlocking {
        val pm1 = PreferencesManager(context)
        val customAlerts = KmStoneAlertSettings(
            enabled = true,
            backgroundMonitoringEnabled = false,
            persistentNotificationEnabled = false,
            lockScreenNotificationsEnabled = false,
            arrivalAlertsEnabled = false
        )
        val customScreensaver = HomeScreensaverSettings(
            aodEnabled = true,
            aodAutoActivateOnScreenOff = true,
            aodShowChainage = true,
            aodShowDirectionSide = false,
            aodShowTime = false,
            aodShowGpsStatus = true,
            aodShowKmStoneStatus = false,
            aodShowNextKmStone = false,
            aodDimLevel = 0.28f,
            aodBurnInProtectionEnabled = false
        )
        pm1.updateKmStoneAlertSettings(customAlerts)
        pm1.updateHomeScreensaverSettings(customScreensaver)

        val pm2 = PreferencesManager(context)
        val restoredAlerts = pm2.kmStoneAlertSettingsFlow.first()
        val restoredScreensaver = pm2.homeScreensaverSettingsFlow.first()

        assertEquals(false, restoredAlerts.backgroundMonitoringEnabled)
        assertEquals(false, restoredAlerts.persistentNotificationEnabled)
        assertEquals(false, restoredAlerts.lockScreenNotificationsEnabled)
        assertEquals(false, restoredAlerts.arrivalAlertsEnabled)

        assertEquals(true, restoredScreensaver.aodEnabled)
        assertEquals(true, restoredScreensaver.aodAutoActivateOnScreenOff)
        assertEquals(false, restoredScreensaver.aodShowDirectionSide)
        assertEquals(false, restoredScreensaver.aodShowTime)
        assertEquals(false, restoredScreensaver.aodShowKmStoneStatus)
        assertEquals(false, restoredScreensaver.aodShowNextKmStone)
        assertEquals(0.28f, restoredScreensaver.aodDimLevel, 0.005f)
        assertEquals(false, restoredScreensaver.aodBurnInProtectionEnabled)

        // Restore clean defaults for subsequent tests
        pm2.updateKmStoneAlertSettings(KmStoneAlertSettings.defaultSettings())
        pm2.updateHomeScreensaverSettings(HomeScreensaverSettings.defaultSettings())

        // Verify new Settings UI controls wire up cleanly
        var screenCfg by mutableStateOf(HomeScreensaverSettings.defaultSettings())
        var alertCfg by mutableStateOf(KmStoneAlertSettings.defaultSettings())

        composeRule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ScreensaverSettingsSectionContent(
                    settings = screenCfg,
                    onUpdateSettings = { screenCfg = it(screenCfg) },
                    onSelectDisplayMode = { mode ->
                        screenCfg = when (mode) {
                            ScreensaverDisplayMode.PORTRAIT -> screenCfg.selectPortrait()
                            ScreensaverDisplayMode.LANDSCAPE -> screenCfg.selectLandscape()
                            ScreensaverDisplayMode.MIRROR -> screenCfg.setMirrorEnabled(true)
                        }
                    },
                    onToggleMirror = { screenCfg = screenCfg.setMirrorEnabled(it) },
                    onHudBrightnessChange = { screenCfg = screenCfg.withHudBrightness(it) },
                    onMiniMapOrientationChange = { screenCfg = screenCfg.withOrientation(it) },
                    onMiniMapZoomIn = { screenCfg = screenCfg.withZoomIn() },
                    onMiniMapZoomOut = { screenCfg = screenCfg.withZoomOut() },
                    onMiniMapRecenter = { screenCfg = screenCfg.withRecenter() },
                    onMiniMapStyleChange = { screenCfg = screenCfg.withMiniMapStyle(it) },
                    onResetDefaults = { screenCfg = HomeScreensaverSettings.defaultSettings() }
                )
                KmStoneAlertSettingsSectionContent(
                    settings = alertCfg,
                    accentColor = Color(0xFF38BDF8),
                    isMonitoringServiceRunning = true,
                    hasActiveRoute = true,
                    onUpdateSettings = { alertCfg = it(alertCfg) },
                    onResetDefaults = { alertCfg = KmStoneAlertSettings.defaultSettings() }
                )
            }
        }

        // Expand AMOLED AOD section and toggle controls
        composeRule.onNodeWithTag("screensaver_section_aod").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screensaver_aod_inline_preview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_aod_auto_activate_switch").performScrollTo().performClick()
        composeRule.onNodeWithTag("screensaver_aod_burn_in_switch").performScrollTo().performClick()
        composeRule.onNodeWithTag("screensaver_aod_show_time_chip").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(screenCfg.aodAutoActivateOnScreenOff)
        assertFalse(screenCfg.aodBurnInProtectionEnabled)
        assertFalse(screenCfg.aodShowTime)

        // Toggle new KM Stone Monitoring controls
        composeRule.onNodeWithTag("km_alert_service_status_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("km_alert_switch_background_monitoring").performScrollTo().performClick()
        composeRule.onNodeWithTag("km_alert_switch_persistent_notif").performScrollTo().performClick()
        composeRule.onNodeWithTag("km_alert_switch_arrival_alerts").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(alertCfg.backgroundMonitoringEnabled)
        assertFalse(alertCfg.persistentNotificationEnabled)
        assertFalse(alertCfg.arrivalAlertsEnabled)
    }
}
