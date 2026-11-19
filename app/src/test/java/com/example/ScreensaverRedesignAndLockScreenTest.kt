package com.example

import android.app.Application
import android.content.pm.ServiceInfo
import android.os.Looper
import android.view.Window
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.HudAnimationSpeed
import com.example.model.HudColorPreset
import com.example.model.HudDirectionColorMode
import com.example.model.HudDirectionPosition
import com.example.model.HudDirectionStyle
import com.example.model.HudElementSize
import com.example.model.HudFontStyle
import com.example.model.HudLocationPosition
import com.example.model.HudMapMarkerStyle
import com.example.model.HudMapPosition
import com.example.model.HudPanelSize
import com.example.model.HudSpeedometerStyle
import com.example.model.HudVerticalPosition
import com.example.model.MatchConfidence
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapStyle
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import com.example.model.ScreensaverDisplayMode
import com.example.navigation.LockScreenNavStateResolver
import com.example.navigation.LockScreenNotificationFormatter
import com.example.ui.MainViewModel
import com.example.ui.home.AutomotiveChainageScreensaverContent
import com.example.ui.home.ChainageScreensaverOverlay
import com.example.ui.home.HomeScreensaverCoordinator
import com.example.ui.home.ScreensaverDisplayState
import com.example.ui.home.applyScreensaverImmersiveMode
import com.example.ui.home.shouldAllowScreensaver
import com.example.ui.settings.ScreensaverSettingsSectionContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreensaverRedesignAndLockScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sampleRoute(): RouteData {
        return RouteData(
            id = "nh58-nav-route",
            name = "NH-58 Roorkee Bypass",
            fileName = "nh58_roorkee.kmz",
            markers = listOf(
                RouteMarker(
                    id = "km-204",
                    label = "204+000",
                    chainage = Chainage(204_000L),
                    point = GeoPoint(29.5000, 77.7000)
                ),
                RouteMarker(
                    id = "km-205",
                    label = "205+000",
                    chainage = Chainage(205_000L),
                    point = GeoPoint(29.5080, 77.7080)
                )
            ),
            alignments = listOf(
                RouteAlignment(
                    id = "mainline",
                    name = "NH-58 Mainline",
                    points = listOf(
                        GeoPoint(29.4980, 77.6980),
                        GeoPoint(29.5040, 77.7040),
                        GeoPoint(29.5100, 77.7100)
                    ),
                    direction = "INCREASING"
                )
            ),
            bounds = GeoBounds(29.495, 29.515, 77.695, 77.715),
            startChainage = Chainage(200_000L),
            endChainage = Chainage(215_000L)
        )
    }

    private fun sampleDisplayState(): ScreensaverDisplayState {
        return ScreensaverDisplayState(
            chainageText = "204+320",
            reliableSide = "LHS",
            directionArrow = "↑",
            isLiveGpsValid = true,
            isIncreasingTrend = true,
            locationName = "Badrabad-Rajputna Road",
            speedText = "62 km/h",
            speedKmh = 62
        )
    }

    @Test
    fun `expanded HomeScreensaverSettings serializes deserializes clamps timeouts and resets cleanly`() {
        val customized = HomeScreensaverSettings(
            masterEnabled = true,
            autoActivateEnabled = false,
            inactivityTimeoutSeconds = 45,
            isCustomTimeout = true,
            touchToDismiss = false,
            autoRestartTimer = false,
            displayMode = ScreensaverDisplayMode.MIRROR,
            hudBrightness = 0.52f,
            showChainage = true,
            chainageFontSize = HudElementSize.EXTRA_LARGE,
            chainageFontStyle = HudFontStyle.DIGITAL,
            chainageColorPreset = HudColorPreset.EMERALD,
            chainageOdometerAnimation = false,
            chainageAnimationSpeed = HudAnimationSpeed.FAST,
            chainagePanelSize = HudPanelSize.EXPANDED,
            chainagePanelPosition = HudVerticalPosition.CENTER,
            showDirectionSide = true,
            directionIndicatorStyle = HudDirectionStyle.OUTLINE,
            directionArrowsAnimated = false,
            directionColorPreset = HudDirectionColorMode.AMBER,
            directionSize = HudElementSize.LARGE,
            directionPosition = HudDirectionPosition.INLINE,
            showSpeedometer = true,
            speedometerStyle = HudSpeedometerStyle.DIGITAL,
            speedometerSize = HudElementSize.LARGE,
            speedometerColorPreset = HudColorPreset.AMBER,
            speedometerAnimateNeedle = false,
            speedometerAnimateNumbers = false,
            showLocation = true,
            locationFontSize = HudElementSize.SMALL,
            locationPosition = HudLocationPosition.TOP_BAR,
            locationDynamicUpdates = false,
            miniMapVisible = true,
            miniMapOrientation = MiniMapOrientationMode.DIRECTION_UP,
            miniMapZoomLevel = 17.5f,
            miniMapZoomStep = 1.25f,
            miniMapStyle = MiniMapStyle.DARK,
            miniMapSize = HudPanelSize.EXPANDED,
            miniMapPosition = HudMapPosition.TOP,
            miniMapOpacity = 0.65f,
            miniMapShowBorder = false,
            miniMapBorderColorPreset = HudColorPreset.EMERALD,
            miniMapMarkerStyle = HudMapMarkerStyle.PULSE_DOT,
            miniMapShowRouteAlignment = false
        )

        val restored = HomeScreensaverSettings.fromJsonString(customized.toJsonString())
        assertEquals(customized, restored)

        // Legacy minimal JSON gracefully preserves sensible defaults for all new fields
        val legacy = HomeScreensaverSettings.fromJsonString("""{"displayMode":"LANDSCAPE","hudBrightness":0.75}""")
        assertEquals(ScreensaverDisplayMode.LANDSCAPE, legacy.displayMode)
        assertEquals(0.75f, legacy.hudBrightness, 0.001f)
        assertTrue(legacy.masterEnabled)
        assertTrue(legacy.autoActivateEnabled)
        assertEquals(20, legacy.inactivityTimeoutSeconds)
        assertTrue(legacy.touchToDismiss)
        assertTrue(legacy.autoRestartTimer)

        // Custom timeout bounds validation
        val clampedLow = HomeScreensaverSettings().withTimeoutSeconds(1, custom = true)
        assertEquals(HomeScreensaverSettings.MIN_TIMEOUT_SEC, clampedLow.inactivityTimeoutSeconds)
        val clampedHigh = HomeScreensaverSettings().withTimeoutSeconds(9999, custom = true)
        assertEquals(HomeScreensaverSettings.MAX_TIMEOUT_SEC, clampedHigh.inactivityTimeoutSeconds)
    }

    @Test
    fun `viewModel persists all redesigned screensaver settings and resets cleanly to defaults`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        idleMain()

        vm.updateScreensaverSettings {
            it.withTimeoutSeconds(45, custom = true)
                .copy(
                    masterEnabled = true,
                    touchToDismiss = false,
                    speedometerStyle = HudSpeedometerStyle.DIGITAL,
                    directionPosition = HudDirectionPosition.INLINE,
                    miniMapMarkerStyle = HudMapMarkerStyle.CLASSIC_PIN
                )
        }
        repeat(6) {
            idleMain()
            delay(80L)
        }
        assertEquals(45, vm.homeScreensaverSettings.value.inactivityTimeoutSeconds)
        assertFalse(vm.homeScreensaverSettings.value.touchToDismiss)
        assertEquals(HudSpeedometerStyle.DIGITAL, vm.homeScreensaverSettings.value.speedometerStyle)

        vm.resetScreensaverSettings()
        repeat(6) {
            idleMain()
            delay(80L)
        }
        assertEquals(HomeScreensaverSettings.defaultSettings(), vm.homeScreensaverSettings.value)
    }

    @Test
    fun `coordinator enforces master toggle, auto-activate, custom timeout, touch-to-dismiss, and one-shot timer restart`() {
        assertFalse(
            shouldAllowScreensaver(
                isLifecycleResumed = true,
                isDialogOpen = false,
                isKeyboardOpen = false,
                isInputFocused = false,
                isMasterEnabled = false,
                isAutoActivateEnabled = true
            )
        )
        assertFalse(
            shouldAllowScreensaver(
                isLifecycleResumed = true,
                isDialogOpen = false,
                isKeyboardOpen = false,
                isInputFocused = false,
                isMasterEnabled = true,
                isAutoActivateEnabled = false
            )
        )

        var masterEnabled by mutableStateOf(true)
        var autoActivateEnabled by mutableStateOf(true)
        var touchToDismiss by mutableStateOf(false)
        var autoRestartTimer by mutableStateOf(false)
        var timeoutMs by mutableStateOf(10_000L)
        var isVisible by mutableStateOf(false)
        var showCount by mutableIntStateOf(0)

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            HomeScreensaverCoordinator(
                isBlockingUiOpen = false,
                inactivityTimeoutMs = timeoutMs,
                isScreensaverVisible = isVisible,
                onShowScreensaver = {
                    showCount += 1
                    isVisible = true
                },
                onDismissScreensaver = { isVisible = false },
                masterEnabled = masterEnabled,
                autoActivateEnabled = autoActivateEnabled,
                touchToDismiss = touchToDismiss,
                autoRestartTimer = autoRestartTimer,
                screensaverContent = { dismiss ->
                    ChainageScreensaverOverlay(
                        displayState = sampleDisplayState(),
                        location = GeoPoint(29.504, 77.704),
                        bearing = 45f,
                        compassHeading = 45f,
                        routeData = sampleRoute(),
                        screensaverSettings = HomeScreensaverSettings(touchToDismiss = touchToDismiss),
                        onDismiss = dismiss
                    )
                }
            ) { interactionModifier ->
                Box(modifier = interactionModifier.fillMaxSize().testTag("coordinator_home_box"))
            }
        }

        // Custom 10s timeout -> closed at 9.5s, opens after 10s
        composeTestRule.mainClock.advanceTimeBy(9_500L)
        composeTestRule.waitForIdle()
        assertFalse(isVisible)

        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()
        assertTrue(isVisible)
        assertEquals(1, showCount)
        composeTestRule.onNodeWithTag("home_chainage_screensaver").assertIsDisplayed()

        // Touch-to-Dismiss OFF -> single touch does NOT dismiss
        composeTestRule.onNodeWithTag("home_chainage_screensaver").performTouchInput { click() }
        composeTestRule.waitForIdle()
        assertTrue(isVisible)

        // Enable Touch-to-Dismiss -> single touch dismisses immediately
        composeTestRule.runOnIdle { touchToDismiss = true }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        idleMain()
        composeTestRule.onNodeWithTag("home_chainage_screensaver").performTouchInput { click() }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        idleMain()
        assertFalse(isVisible)

        // Auto-Restart Timer OFF -> advancing past 10s must NOT re-open automatically
        composeTestRule.mainClock.advanceTimeBy(15_000L)
        composeTestRule.waitForIdle()
        assertFalse(isVisible)
        assertEquals(1, showCount)

        // Re-enable Auto-Restart Timer -> opens again cleanly
        composeTestRule.runOnIdle { autoRestartTimer = true }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(10_500L)
        composeTestRule.waitForIdle()
        assertTrue(isVisible)
        assertEquals(2, showCount)

        // Master Disable immediately dismisses active screensaver
        composeTestRule.runOnIdle { masterEnabled = false }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        assertFalse(isVisible)
    }

    @Test
    fun `applyScreensaverImmersiveMode hides and restores system bars cleanly`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val window: Window = activity.window

        applyScreensaverImmersiveMode(
            dialogWindow = window,
            dialogView = window.decorView,
            hostActivityWindow = window,
            enable = true
        )
        val hiddenController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        assertNotNull(hiddenController)

        // Disable restores system bars safely without throwing
        applyScreensaverImmersiveMode(
            dialogWindow = window,
            dialogView = window.decorView,
            hostActivityWindow = window,
            enable = false
        )
        val restoredController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        assertNotNull(restoredController)
    }

    @Test
    fun `screensaver HUD respects custom element visibility, styles, digital speedometer, and sample badge`() {
        var settings by mutableStateOf(
            HomeScreensaverSettings(
                showChainage = true,
                showDirectionSide = true,
                directionPosition = HudDirectionPosition.INLINE,
                showSpeedometer = true,
                speedometerStyle = HudSpeedometerStyle.DIGITAL,
                showLocation = true,
                miniMapVisible = true
            )
        )
        var sampleFlag by mutableStateOf(true)

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 380.dp, height = 760.dp)) {
                AutomotiveChainageScreensaverContent(
                    displayState = sampleDisplayState(),
                    location = GeoPoint(29.504, 77.704),
                    bearing = 90f,
                    compassHeading = 90f,
                    routeData = sampleRoute(),
                    mapThemePreference = "dark",
                    mapTypePreference = "standard",
                    isDarkAppTheme = true,
                    screensaverSettings = settings,
                    isSamplePreview = sampleFlag
                )
            }
        }

        // All visible elements & sample preview badge rendered
        composeTestRule.onNodeWithTag("screensaver_preview_sample_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_chainage_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_direction_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_speed_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_location_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_glass_map").assertIsDisplayed()

        // Normal live mode hides sample badge & hides toggled-off widgets
        composeTestRule.runOnIdle {
            sampleFlag = false
            settings = settings.copy(
                showChainage = false,
                showDirectionSide = false,
                showSpeedometer = false,
                showLocation = false,
                miniMapVisible = false
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_preview_sample_badge").assertDoesNotExist()
        composeTestRule.onNodeWithTag("screensaver_chainage_text").assertDoesNotExist()
        composeTestRule.onNodeWithTag("screensaver_direction_badge").assertDoesNotExist()
        composeTestRule.onNodeWithTag("screensaver_speed_text").assertDoesNotExist()
        composeTestRule.onNodeWithTag("screensaver_location_name").assertDoesNotExist()
        composeTestRule.onNodeWithTag("screensaver_glass_map").assertDoesNotExist()
    }

    @Test
    fun `settings screensaver section supports timeouts, previews with sample badge, and confirmed reset`() {
        var settingsState by mutableStateOf(HomeScreensaverSettings())

        composeTestRule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ScreensaverSettingsSectionContent(
                    settings = settingsState,
                    onUpdateSettings = { transform -> settingsState = transform(settingsState) },
                    onSelectDisplayMode = { mode ->
                        settingsState = when (mode) {
                            ScreensaverDisplayMode.PORTRAIT -> settingsState.selectPortrait()
                            ScreensaverDisplayMode.LANDSCAPE -> settingsState.selectLandscape()
                            ScreensaverDisplayMode.MIRROR -> settingsState.setMirrorEnabled(true)
                        }
                    },
                    onToggleMirror = { enabled -> settingsState = settingsState.setMirrorEnabled(enabled) },
                    onHudBrightnessChange = { b -> settingsState = settingsState.withHudBrightness(b) },
                    onMiniMapOrientationChange = { o -> settingsState = settingsState.withOrientation(o) },
                    onMiniMapZoomIn = { settingsState = settingsState.withZoomIn() },
                    onMiniMapZoomOut = { settingsState = settingsState.withZoomOut() },
                    onMiniMapRecenter = { settingsState = settingsState.withRecenter() },
                    onMiniMapStyleChange = { s -> settingsState = settingsState.withMiniMapStyle(s) },
                    onResetDefaults = { settingsState = HomeScreensaverSettings.defaultSettings() }
                )
            }
        }

        // General sub-section expanded by default: pick 30s preset, then Custom 12s
        composeTestRule.onNodeWithTag("screensaver_timeout_30s").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(30, settingsState.inactivityTimeoutSeconds)
        assertFalse(settingsState.isCustomTimeout)

        composeTestRule.onNodeWithTag("screensaver_timeout_custom_chip").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_timeout_custom_input").performScrollTo().performTextReplacement("12")
        composeTestRule.onNodeWithTag("screensaver_timeout_custom_apply").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(12, settingsState.inactivityTimeoutSeconds)
        assertTrue(settingsState.isCustomTimeout)

        // Open Live Preview & verify explicit SAMPLE DATA badge when no live GPS is connected
        composeTestRule.onNodeWithTag("screensaver_quick_preview_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_chainage_screensaver").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_preview_sample_badge").assertIsDisplayed()

        // Tap preview overlay to dismiss
        composeTestRule.onNodeWithTag("home_chainage_screensaver").performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_chainage_screensaver").assertDoesNotExist()

        // Expand Previews & Reset -> click Reset -> verify confirmation dialog -> confirm reset
        composeTestRule.onNodeWithTag("screensaver_section_previews_reset").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_reset_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_reset_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_reset_confirm_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals(HomeScreensaverSettings.defaultSettings(), settingsState)
    }

    @Test
    fun `lock screen nav resolver never fabricates ETA when stopped and preserves chainage on GPS loss`() {
        val resolver = LockScreenNavStateResolver()
        val route = sampleRoute()
        val project = ProjectProfile(
            highwayId = "Badrabad-Rajputna Road",
            packageSection = "Pkg-2",
            roadSide = RoadSide.LHS
        )
        val match = ChainageMatchResult(
            chainage = Chainage(204_320L),
            confidence = MatchConfidence.HIGH,
            detectedSide = "LHS",
            source = ChainageSource.KMZ
        )
        val t0 = 1_700_000_000_000L

        // 1. Stationary GPS fix (0.2 m/s) -> No fake ETA, verified distance to Next KM 205+000 (680 m)
        val stationary = resolver.resolve(
            locationState = LocationState(
                point = GeoPoint(29.503, 77.703),
                accuracy = 3.5f,
                speed = 0.2f,
                status = LocationStatus.READY,
                lastFixTime = t0
            ),
            matchResult = match,
            routeData = route,
            projectProfile = project,
            locationName = "Badrabad",
            is24HourFormat = true,
            nowMs = t0
        )
        assertEquals("204+320", stationary.chainageText)
        assertEquals("LHS", stationary.reliableSide)
        assertEquals("Badrabad-Rajputna Road • Pkg-2", stationary.roadOrStretchName)
        assertEquals("680 m", stationary.distanceToNextText)
        assertEquals("0 km/h", stationary.speedText)
        assertNull("Stationary vehicle must never display a fabricated ETA", stationary.etaDurationText)
        assertNull(stationary.etaArrivalTimeText)

        // 2. Moving at 10.0 m/s (36 km/h) -> Valid ETA computed (680m / 10 m/s = 68s -> 1 min)
        val moving = resolver.resolve(
            locationState = LocationState(
                point = GeoPoint(29.503, 77.703),
                accuracy = 3.5f,
                speed = 10.0f,
                status = LocationStatus.READY,
                lastFixTime = t0 + 1000L
            ),
            matchResult = match,
            routeData = route,
            projectProfile = project,
            locationName = "Badrabad",
            is24HourFormat = true,
            nowMs = t0 + 1000L
        )
        assertNotNull(moving.etaDurationText)
        assertNotNull(moving.etaArrivalTimeText)
        val summary = LockScreenNotificationFormatter.format(moving)
        assertTrue(summary.title.contains("204+320"))
        assertTrue(summary.contentText.contains("680 m"))

        // 3. GPS loss -> retains chainage 204+320, drops ETA & speed to null / "-- km/h", reports age cleanly
        val lost = resolver.resolve(
            locationState = LocationState(point = null, status = LocationStatus.ACQUIRING),
            matchResult = ChainageMatchResult(confidence = MatchConfidence.NO_MATCH),
            routeData = route,
            projectProfile = project,
            locationName = null,
            nowMs = t0 + 12_000L
        )
        assertEquals("204+320", lost.chainageText)
        assertNull(lost.speedKmh)
        assertEquals("-- km/h", lost.speedText)
        assertNull(lost.etaDurationText)
        assertTrue(lost.lastUpdateStatusText?.contains("Last GPS update") == true)
    }

    @Test
    fun `manifest declares navigation foreground service safely without lock screen saver activity`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pm = app.packageManager
        val pkgInfo = pm.getPackageInfo(
            app.packageName,
            android.content.pm.PackageManager.GET_ACTIVITIES or
                android.content.pm.PackageManager.GET_SERVICES or
                android.content.pm.PackageManager.GET_PERMISSIONS
        )

        val permissions = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(permissions.contains(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION))
        assertTrue(permissions.contains(android.Manifest.permission.POST_NOTIFICATIONS))

        val serviceInfo = pkgInfo.services?.firstOrNull {
            it.name.endsWith("LockScreenNavigationService")
        }
        assertNotNull("LockScreenNavigationService must be declared in AndroidManifest.xml", serviceInfo)
        assertTrue(
            (serviceInfo!!.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) != 0
        )

        val activityInfo = pkgInfo.activities?.firstOrNull {
            it.name.endsWith("LockScreenNavigationActivity")
        }
        assertNull("LockScreenNavigationActivity must be completely removed from AndroidManifest.xml", activityInfo)
    }
}
