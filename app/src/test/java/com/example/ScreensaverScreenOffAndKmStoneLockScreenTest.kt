package com.example

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.KmStoneAlertSettings
import com.example.model.MatchConfidence
import com.example.model.NavigationNotificationSettings
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.ScreensaverDisplayMode
import com.example.model.ScreensaverThemeMode
import com.example.navigation.KmStoneAlertEvent
import com.example.navigation.KmStoneAlertFeedbackManager
import com.example.navigation.KmStoneAlertKind
import com.example.navigation.KmStoneArrivalDetector
import com.example.navigation.LockScreenNavigationService
import com.example.ui.home.ChainageScreensaverOverlay
import com.example.ui.home.DashboardMirrorScaleXKey
import com.example.ui.home.HomeScreensaverCoordinator
import com.example.ui.home.ScreensaverDisplayModeKey
import com.example.ui.home.ScreensaverDisplayState
import com.example.ui.home.ScreensaverScreenOffKey
import com.example.ui.home.ScreensaverThemeDarkKey
import com.example.ui.settings.KmStoneAlertSettingsSectionContent
import com.example.ui.settings.ScreensaverSettingsSectionContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreensaverScreenOffAndKmStoneLockScreenTest {

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
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sampleRoute(): RouteData {
        val pts = listOf(
            GeoPoint(29.500, 77.700),
            GeoPoint(29.510, 77.710),
            GeoPoint(29.520, 77.720)
        )
        return RouteData(
            id = "route_nh58_test",
            name = "NH-58 Test Alignment",
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

    private fun sampleDisplayState(chainage: String = "200+850", speed: Int? = 62): ScreensaverDisplayState {
        return ScreensaverDisplayState(
            chainageText = chainage,
            reliableSide = "LHS",
            directionArrow = "↑",
            isLiveGpsValid = speed != null,
            isIncreasingTrend = true,
            locationName = "Roorkee Highway",
            speedText = if (speed != null) "$speed km/h" else "-- km/h",
            speedKmh = speed
        )
    }

    @Test
    fun screensaver_screenOffTogglesBlackScreenPausesRenderingAndRestoresModeAndTheme() {
        var isScreenOff by mutableStateOf(false)
        var dismissCount = 0
        var settings by mutableStateOf(
            HomeScreensaverSettings(
                displayMode = ScreensaverDisplayMode.MIRROR,
                autoOrientationEnabled = false,
                mirrorModePreferred = true,
                screensaverThemeMode = ScreensaverThemeMode.DARK,
                touchToDismiss = true,
                showScreenOffControl = true
            )
        )
        var liveChainage by mutableStateOf("200+850")

        composeRule.mainClock.autoAdvance = false
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
                onToggleMirrorQuickMode = { enableMirror, landscape ->
                    settings = settings.withMirrorQuickToggle(enableMirror, landscape)
                },
                onToggleOrientationLock = { landscape ->
                    settings = settings.withOrientationLock(!settings.orientationLocked, landscape)
                },
                onToggleScreenOff = { off -> isScreenOff = off },
                onDismiss = { dismissCount += 1 }
            )
        }

        // Initial Screen ON state in Mirror Mode
        composeRule.onNodeWithTag("screensaver_chainage_text").assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_glass_map").assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_screen_off_toggle_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_dashboard_root")
            .assert(SemanticsMatcher.expectValue(DashboardMirrorScaleXKey, -1f))
            .assert(SemanticsMatcher.expectValue(ScreensaverDisplayModeKey, ScreensaverDisplayMode.MIRROR.name))
            .assert(SemanticsMatcher.expectValue(ScreensaverScreenOffKey, false))

        // Tap Screen OFF -> smoothly fades to black and pauses heavy dashboard/map rendering
        composeRule.onNodeWithTag("screensaver_screen_off_toggle_btn").performClick()
        composeRule.mainClock.advanceTimeBy(350L)
        composeRule.waitForIdle()
        assertTrue(isScreenOff)
        assertEquals(0, dismissCount)
        composeRule.onNodeWithTag("screensaver_blackout_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_dashboard_root")
            .assert(SemanticsMatcher.expectValue(ScreensaverScreenOffKey, true))
        // Heavy dashboard & mini map rendering is paused while pure black is active
        composeRule.onNodeWithTag("screensaver_glass_map").assertDoesNotExist()
        composeRule.onNodeWithTag("screensaver_chainage_text").assertDoesNotExist()

        // While Screen OFF, live chainage continues updating underneath and tapping blackout wakes to Screen ON
        composeRule.runOnIdle { liveChainage = "201+005" }
        composeRule.onNodeWithTag("screensaver_blackout_overlay").performTouchInput { click() }
        composeRule.mainClock.advanceTimeBy(350L)
        composeRule.waitForIdle()

        // Screen ON restored immediately with updated live chainage, same Mirror mode, and zero dismissals
        assertFalse(isScreenOff)
        assertEquals(0, dismissCount)
        composeRule.onNodeWithTag("screensaver_blackout_overlay").assertDoesNotExist()
        composeRule.onNodeWithTag("screensaver_chainage_text").assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_glass_map").assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_dashboard_root")
            .assert(SemanticsMatcher.expectValue(DashboardMirrorScaleXKey, -1f))
            .assert(SemanticsMatcher.expectValue(ScreensaverDisplayModeKey, ScreensaverDisplayMode.MIRROR.name))
            .assert(SemanticsMatcher.expectValue(ScreensaverThemeDarkKey, true))
            .assert(SemanticsMatcher.expectValue(ScreensaverScreenOffKey, false))
    }

    @Test
    fun screensaver_screenOffControlWorksInPortraitAndLandscapeAndRespectsVisibilitySetting() {
        var settings by mutableStateOf(
            HomeScreensaverSettings(
                displayMode = ScreensaverDisplayMode.PORTRAIT,
                autoOrientationEnabled = false,
                showScreenOffControl = true
            )
        )
        var isScreenOff by mutableStateOf(false)

        composeRule.setContent {
            ChainageScreensaverOverlay(
                displayState = sampleDisplayState(),
                location = GeoPoint(29.505, 77.705),
                bearing = 30f,
                compassHeading = 30f,
                routeData = sampleRoute(),
                screensaverSettings = settings,
                isScreenOff = isScreenOff,
                onToggleScreenOff = { isScreenOff = it },
                onDismiss = {}
            )
        }

        // Portrait mode shows Screen OFF button
        composeRule.onNodeWithTag("screensaver_portrait_layout").assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_screen_off_toggle_btn").assertIsDisplayed()

        // Switch to Landscape mode -> Screen OFF button remains accessible
        composeRule.runOnIdle { settings = settings.selectLandscape() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screensaver_landscape_layout").assertIsDisplayed()
        composeRule.onNodeWithTag("screensaver_screen_off_toggle_btn").assertIsDisplayed()

        // Disable quick control via Settings -> hidden cleanly
        composeRule.runOnIdle { settings = settings.withShowScreenOffControl(false) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screensaver_screen_off_toggle_btn").assertDoesNotExist()
    }

    @Test
    fun kmStoneArrivalNotification_postsLockScreenCompatibleDetailsUpdatesInPlaceAndRespectsPrivacyAndPermission() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)
        nm.cancelAll()

        val enabledSettings = KmStoneAlertSettings(
            enabled = true,
            lockScreenNotificationsEnabled = true,
            vibrationEnabled = true,
            soundEnabled = false
        )
        val event1 = KmStoneAlertEvent(
            kind = KmStoneAlertKind.ARRIVAL,
            stoneChainageMetres = 201_000L,
            stoneFormattedChainage = "201+000",
            currentChainageMetres = 200_996L,
            currentFormattedChainage = "200+996",
            directionSide = "LHS",
            distanceMetres = 4L,
            routeId = "route_nh58_test",
            timestampMs = 50_000L
        )

        val record1 = KmStoneAlertFeedbackManager.dispatchAlert(
            context = context,
            event = event1,
            settings = enabledSettings,
            isRecordingActive = false
        )
        assertTrue(record1.notificationPosted)

        val posted1 = shadowNm.getNotification(KmStoneAlertFeedbackManager.ALERT_NOTIFICATION_ID)
        assertNotNull("KM Stone arrival notification must be posted", posted1)
        assertEquals(Notification.VISIBILITY_PUBLIC, posted1.visibility)
        assertEquals("Notification must remain in shade without auto-timeout", 0L, posted1.timeoutAfter)
        assertNotNull("Notification must include PendingIntent to open CTECS", posted1.contentIntent)

        val title1 = posted1.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text1 = posted1.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue("Title must include KM Stone chainage: $title1", title1.contains("201+000"))
        assertTrue("Body must include current chainage: $text1", text1.contains("200+996"))
        assertTrue("Body must include LHS/RHS side: $text1", text1.contains("LHS"))
        assertTrue("Body must include distance or arrival status: $text1", text1.contains("Arrived") && text1.contains("4 m"))

        // Second arrival updates the SAME notification ID in place (no duplicate notifications)
        val event2 = event1.copy(
            stoneChainageMetres = 202_000L,
            stoneFormattedChainage = "202+000",
            currentChainageMetres = 202_003L,
            currentFormattedChainage = "202+003",
            directionSide = "RHS",
            distanceMetres = 3L,
            timestampMs = 95_000L
        )
        KmStoneAlertFeedbackManager.dispatchAlert(
            context = context,
            event = event2,
            settings = enabledSettings.copy(lockScreenNotificationsEnabled = false),
            isRecordingActive = false
        )
        val activeNotifs = shadowNm.allNotifications
        assertEquals("Stable notification ID must update in place without duplicates", 1, activeNotifs.size)
        val posted2 = shadowNm.getNotification(KmStoneAlertFeedbackManager.ALERT_NOTIFICATION_ID)
        assertEquals(Notification.VISIBILITY_PRIVATE, posted2.visibility)
        assertTrue(posted2.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("202+000"))

        // Test Arrival Notification helper works cleanly
        val testRec = KmStoneAlertFeedbackManager.testArrivalNotification(context, enabledSettings)
        assertTrue(testRec.notificationPosted)

        // When POST_NOTIFICATIONS is denied, no crash and notificationPosted == false
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val deniedRec = KmStoneAlertFeedbackManager.dispatchAlert(
            context = context,
            event = event1,
            settings = enabledSettings,
            isRecordingActive = false
        )
        assertFalse(deniedRec.notificationPosted)
    }

    @Test
    fun backgroundServicePolicy_keepsSingleServiceActiveForKmStoneLockScreenAlertsWithRoute() {
        val navOff = NavigationNotificationSettings(enabled = false)
        val navOn = NavigationNotificationSettings(enabled = true)
        val alertsLockOn = KmStoneAlertSettings(enabled = true, lockScreenNotificationsEnabled = true)
        val alertsLockOff = KmStoneAlertSettings(enabled = true, lockScreenNotificationsEnabled = false)

        // 1-arg backward-compatible check
        assertFalse(LockScreenNavigationService.shouldNotificationServiceStayActive(navOff))
        assertTrue(LockScreenNavigationService.shouldNotificationServiceStayActive(navOn))

        // With active route & lock-screen KM Stone alerts ON -> service stays active even if Nav Notif OFF
        assertTrue(
            LockScreenNavigationService.shouldNotificationServiceStayActive(
                notifSettings = navOff,
                kmStoneAlertSettings = alertsLockOn,
                hasActiveRoute = true
            )
        )
        // Without route -> does NOT needlessly run service when Nav Notif is OFF
        assertFalse(
            LockScreenNavigationService.shouldNotificationServiceStayActive(
                notifSettings = navOff,
                kmStoneAlertSettings = alertsLockOn,
                hasActiveRoute = false
            )
        )
        // With lock-screen KM Stone alerts OFF -> stops when Nav Notif is OFF
        assertFalse(
            LockScreenNavigationService.shouldNotificationServiceStayActive(
                notifSettings = navOff,
                kmStoneAlertSettings = alertsLockOff,
                hasActiveRoute = true
            )
        )
    }

    @Test
    fun phoneLockAndUnlock_resumesActiveScreensaverWhileNormalBackgroundingDismisses() {
        val powerMgr = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardMgr = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val shadowPower = Shadows.shadowOf(powerMgr)
        val shadowKeyguard = Shadows.shadowOf(keyguardMgr)
        shadowPower.setIsInteractive(true)
        shadowKeyguard.setKeyguardLocked(false)

        val fakeOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        fakeOwner.registry.currentState = Lifecycle.State.RESUMED

        var isVisible by mutableStateOf(true)
        var isScreenOff by mutableStateOf(true)

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides fakeOwner) {
                HomeScreensaverCoordinator(
                    isBlockingUiOpen = false,
                    inactivityTimeoutMs = 20_000L,
                    isScreensaverVisible = isVisible,
                    onShowScreensaver = { isVisible = true },
                    onDismissScreensaver = { isVisible = false },
                    isScreenOff = isScreenOff,
                    onWakeFromScreenOff = { isScreenOff = false },
                    screensaverContent = { dismiss ->
                        ChainageScreensaverOverlay(
                            displayState = sampleDisplayState(),
                            location = GeoPoint(29.505, 77.705),
                            bearing = 45f,
                            compassHeading = 45f,
                            routeData = sampleRoute(),
                            isScreenOff = isScreenOff,
                            onToggleScreenOff = { isScreenOff = it },
                            onDismiss = dismiss
                        )
                    }
                ) { interactionMod ->
                    Box(modifier = interactionMod.fillMaxSize())
                }
            }
        }

        composeRule.onNodeWithTag("home_chainage_screensaver").assertIsDisplayed()

        // 1. Lock phone normally -> Dialog hides while locked, Screensaver session stays preserved
        composeRule.runOnIdle {
            shadowPower.setIsInteractive(false)
            shadowKeyguard.setKeyguardLocked(true)
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        composeRule.waitForIdle()
        idleMain()
        assertTrue("Screensaver state must stay preserved across phone lock", isVisible)
        composeRule.onNodeWithTag("home_chainage_screensaver").assertDoesNotExist()

        // 2. Unlock phone -> Screensaver resumes cleanly!
        composeRule.runOnIdle {
            shadowPower.setIsInteractive(true)
            shadowKeyguard.setKeyguardLocked(false)
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        composeRule.waitForIdle()
        idleMain()
        assertTrue(isVisible)
        composeRule.onNodeWithTag("home_chainage_screensaver").assertIsDisplayed()

        // 3. Normal backgrounding while phone is UNLOCKED still dismisses Screensaver
        composeRule.runOnIdle {
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            fakeOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        composeRule.waitForIdle()
        idleMain()
        assertFalse("Leaving app while unlocked must dismiss screensaver", isVisible)
    }

    @Test
    fun kmStoneDetector_handlesGpsLossAndRecoveryWithoutDuplicateAlertsOnUnlock() {
        val detector = KmStoneArrivalDetector()
        val route = sampleRoute()
        val settings = KmStoneAlertSettings(enabled = true, proximityThresholdMetres = 20, maxGpsAccuracyMetres = 25)

        val validLoc = LocationState(
            status = LocationStatus.READY,
            point = GeoPoint(29.510, 77.710),
            accuracy = 5f,
            speed = 14f,
            bearing = 45f,
            lastFixTime = 10_000L
        )
        val matchAt201 = ChainageMatchResult(
            chainage = Chainage.fromMetres(201_000L),
            confidence = MatchConfidence.HIGH,
            gpsAccuracy = 5f,
            detectedSide = "LHS",
            source = ChainageSource.KMZ
        )

        // Arrival fires once
        val ev1 = detector.evaluate(validLoc, matchAt201, route, RoadSide.LHS, settings, nowMs = 10_500L)
        assertEquals(1, ev1.size)

        // GPS signal lost -> no false alert
        val lostLoc = LocationState(status = LocationStatus.ACQUIRING, point = null)
        val evLost = detector.evaluate(lostLoc, ChainageMatchResult(), route, RoadSide.LHS, settings, nowMs = 12_000L)
        assertTrue(evLost.isEmpty())

        // GPS recovers right at the same KM stone (e.g. phone unlocked near stone) -> NO duplicate notification
        val recoveredLoc = validLoc.copy(lastFixTime = 14_000L)
        val evRecovered = detector.evaluate(recoveredLoc, matchAt201, route, RoadSide.LHS, settings, nowMs = 14_200L)
        assertTrue("Unlocking / GPS recovery at same stone must not duplicate alert", evRecovered.isEmpty())
    }

    @Test
    fun settingsUiAndGlobalPersistence_saveAndRestoreNewControlsAcrossRestart() = runBlocking {
        // 1. JSON & DataStore persistence round-trip
        val pm1 = PreferencesManager(context)
        val customScreensaver = HomeScreensaverSettings(showScreenOffControl = false, keepScreenAwake = true)
        val customAlerts = KmStoneAlertSettings(enabled = true, lockScreenNotificationsEnabled = false)
        pm1.updateHomeScreensaverSettings(customScreensaver)
        pm1.updateKmStoneAlertSettings(customAlerts)

        val pm2 = PreferencesManager(context)
        val loadedScreensaver = pm2.homeScreensaverSettingsFlow.first()
        val loadedAlerts = pm2.kmStoneAlertSettingsFlow.first()
        assertFalse(loadedScreensaver.showScreenOffControl)
        assertFalse(loadedAlerts.lockScreenNotificationsEnabled)

        // Restore defaults cleanly for subsequent tests
        pm2.updateHomeScreensaverSettings(HomeScreensaverSettings.defaultSettings())
        pm2.updateKmStoneAlertSettings(KmStoneAlertSettings.defaultSettings())

        // 2. Settings UI toggles & Test Notification button
        var screenCfg by mutableStateOf(HomeScreensaverSettings.defaultSettings())
        var alertCfg by mutableStateOf(KmStoneAlertSettings.defaultSettings())
        var testFeedback by mutableStateOf("")

        composeRule.setContent {
            Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
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
                    onUpdateSettings = { alertCfg = it(alertCfg) },
                    onResetDefaults = { alertCfg = KmStoneAlertSettings.defaultSettings() },
                    onFeedbackMessage = { testFeedback = it }
                )
            }
        }

        // Expand Display Mode section & toggle Screen OFF / ON Quick Control
        composeRule.onNodeWithTag("screensaver_section_display_mode").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screensaver_screen_off_control_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(screenCfg.showScreenOffControl)

        // Toggle Lock-Screen Arrival Notifications & tap Test Arrival Notification
        composeRule.onNodeWithTag("km_alert_switch_lock_screen_notif").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(alertCfg.lockScreenNotificationsEnabled)

        composeRule.onNodeWithTag("km_alert_test_notification_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue("Expected test notification feedback, got: $testFeedback", testFeedback.contains("notification", ignoreCase = true))
    }
}
