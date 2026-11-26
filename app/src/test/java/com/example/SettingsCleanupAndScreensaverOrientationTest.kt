package com.example

import android.app.Application
import android.os.Looper
import android.view.OrientationEventListener
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.ScreensaverDisplayMode
import com.example.model.resolveEffectiveScreensaverPresentation
import com.example.ui.MainViewModel
import com.example.ui.home.ChainageScreensaverOverlay
import com.example.ui.home.DashboardLandscapeRotationKey
import com.example.ui.home.DashboardMirrorScaleXKey
import com.example.ui.home.ScreensaverDisplayModeKey
import com.example.ui.home.ScreensaverDisplayState
import com.example.ui.home.ScreensaverOrientationClassifier
import com.example.ui.home.ScreensaverSensorPosture
import com.example.ui.settings.ScreensaverSettingsSectionContent
import com.example.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsCleanupAndScreensaverOrientationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sampleRoute(): RouteData = RouteData(
        id = "hud-orient-route",
        name = "NH-58 Test Corridor",
        fileName = "nh58.kmz",
        markers = emptyList(),
        alignments = listOf(
            RouteAlignment(
                id = "main",
                name = "Mainline",
                points = listOf(GeoPoint(29.500, 77.700), GeoPoint(29.510, 77.710)),
                direction = "INCREASING"
            )
        ),
        bounds = GeoBounds(29.495, 29.515, 77.695, 77.715),
        startChainage = com.example.model.Chainage(200_000L),
        endChainage = com.example.model.Chainage(210_000L)
    )

    private fun sampleDisplayState(): ScreensaverDisplayState = ScreensaverDisplayState(
        chainageText = "205+120",
        reliableSide = "RHS",
        directionArrow = "↓",
        isLiveGpsValid = true,
        isIncreasingTrend = false,
        locationName = "Haridwar Bypass",
        speedText = "54 km/h",
        speedKmh = 54
    )

    // =========================================================================
    // TASK A: Settings UI Cleanup & App Info Package Removal
    // =========================================================================

    @Test
    fun `settings app info omits package row completely while keeping version channel and credit`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        idleMain()

        composeTestRule.setContent {
            SettingsScreen(viewModel = vm, onNavigateBack = {})
        }
        composeTestRule.waitForIdle()

        // App Information is ALWAYS visible directly on SettingsScreen (no dropdown click required!)
        composeTestRule.onNodeWithTag("settings_app_info_section").performScrollTo().assertIsDisplayed()

        // Package string and app package identifier must NOT be displayed anywhere
        composeTestRule.onAllNodesWithText("Package", substring = true, ignoreCase = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(app.packageName, substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("9 Categories", substring = true).assertCountEquals(0)

        // Preserved App Info details: version, release channel, developer credit, and Buy Me a Coffee button
        composeTestRule.onNodeWithTag("app_info_version").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Update Channel:", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Made with ❤️ madhureddyainala").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("buy_me_a_coffee_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_done_button").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("Preferences saved automatically", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    // =========================================================================
    // TASK B: Auto-Orientation Detection, Deadzones, Mirror Rule & Quick Toggle
    // =========================================================================

    @Test
    fun `orientation classifier respects hysteresis deadzones and ORIENTATION_UNKNOWN`() {
        // Upright portrait (0 / 355) & upside-down portrait (180) -> PORTRAIT
        assertEquals(ScreensaverSensorPosture.PORTRAIT, ScreensaverOrientationClassifier.classifyAngle(0, null))
        assertEquals(ScreensaverSensorPosture.PORTRAIT, ScreensaverOrientationClassifier.classifyAngle(355, null))
        assertEquals(ScreensaverSensorPosture.PORTRAIT, ScreensaverOrientationClassifier.classifyAngle(180, null))

        // 90 deg -> LANDSCAPE_RIGHT (+90f rotation), 270 deg -> LANDSCAPE_LEFT (-90f rotation)
        assertEquals(ScreensaverSensorPosture.LANDSCAPE_RIGHT, ScreensaverOrientationClassifier.classifyAngle(90, ScreensaverSensorPosture.PORTRAIT))
        assertEquals(ScreensaverSensorPosture.LANDSCAPE_LEFT, ScreensaverOrientationClassifier.classifyAngle(270, ScreensaverSensorPosture.PORTRAIT))

        // Diagonal deadzones (45, 135, 225, 315) must hold current posture without flickering
        assertEquals(ScreensaverSensorPosture.PORTRAIT, ScreensaverOrientationClassifier.classifyAngle(45, ScreensaverSensorPosture.PORTRAIT))
        assertEquals(ScreensaverSensorPosture.LANDSCAPE_RIGHT, ScreensaverOrientationClassifier.classifyAngle(45, ScreensaverSensorPosture.LANDSCAPE_RIGHT))
        assertEquals(ScreensaverSensorPosture.LANDSCAPE_LEFT, ScreensaverOrientationClassifier.classifyAngle(225, ScreensaverSensorPosture.LANDSCAPE_LEFT))

        // Flat / ORIENTATION_UNKNOWN (-1) preserves current stable posture
        assertEquals(
            ScreensaverSensorPosture.LANDSCAPE_RIGHT,
            ScreensaverOrientationClassifier.classifyAngle(OrientationEventListener.ORIENTATION_UNKNOWN, ScreensaverSensorPosture.LANDSCAPE_RIGHT)
        )
    }

    @Test
    fun `mirror mode couples strictly with landscape while auto orientation preserves mirror intent across portrait tilts`() {
        // User enables Mirror Mode with Auto Orientation ON
        val mirrorSettings = HomeScreensaverSettings(
            displayMode = ScreensaverDisplayMode.MIRROR,
            autoOrientationEnabled = true,
            mirrorModePreferred = true
        )

        // 1. In physical Landscape -> active display is Landscape MIRROR
        val inLandscape = resolveEffectiveScreensaverPresentation(
            settings = mirrorSettings,
            sensorIsLandscape = true,
            windowIsLandscape = false
        )
        assertEquals(ScreensaverDisplayMode.MIRROR, inLandscape.activeMode)
        assertTrue(inLandscape.isLandscape)
        assertTrue(inLandscape.isMirror)
        assertTrue(inLandscape.mirrorPreferenceEnabled)

        // 2. Rotating physically to Portrait -> upright PORTRAIT Normal (never upside-down / never Portrait mirror), preserves mirrorPreferenceEnabled = true
        val rotatedToPortrait = resolveEffectiveScreensaverPresentation(
            settings = mirrorSettings,
            sensorIsLandscape = false,
            windowIsLandscape = false
        )
        assertEquals(ScreensaverDisplayMode.PORTRAIT, rotatedToPortrait.activeMode)
        assertFalse(rotatedToPortrait.isLandscape)
        assertFalse(rotatedToPortrait.isMirror)
        assertTrue(rotatedToPortrait.mirrorPreferenceEnabled)

        // 3. Rotating back to physical Landscape -> automatically restores Landscape MIRROR HUD
        val backToLandscape = resolveEffectiveScreensaverPresentation(
            settings = mirrorSettings,
            sensorIsLandscape = true,
            windowIsLandscape = false
        )
        assertEquals(ScreensaverDisplayMode.MIRROR, backToLandscape.activeMode)
        assertTrue(backToLandscape.isMirror)

        // 4. Portrait Normal -> user taps Quick Toggle Mirror -> temporaryLandscapeOverride shows Landscape MIRROR
        val portraitQuickMirror = resolveEffectiveScreensaverPresentation(
            settings = HomeScreensaverSettings(
                displayMode = ScreensaverDisplayMode.MIRROR,
                autoOrientationEnabled = true,
                mirrorModePreferred = true
            ),
            sensorIsLandscape = false,
            windowIsLandscape = false,
            temporaryLandscapeOverride = true
        )
        assertEquals(ScreensaverDisplayMode.MIRROR, portraitQuickMirror.activeMode)
        assertTrue(portraitQuickMirror.isLandscape)
        assertTrue(portraitQuickMirror.isMirror)

        // 5. Auto Orientation OFF -> ignores physical sensor posture and obeys saved displayMode strictly
        val manualDisabledAuto = mirrorSettings.withAutoOrientation(false)
        val forcedMirrorEvenPortraitSensor = resolveEffectiveScreensaverPresentation(
            settings = manualDisabledAuto,
            sensorIsLandscape = false,
            windowIsLandscape = false
        )
        assertEquals(ScreensaverDisplayMode.MIRROR, forcedMirrorEvenPortraitSensor.activeMode)
        assertTrue(forcedMirrorEvenPortraitSensor.isMirror)
    }

    @Test
    fun `overlay quick toggle switches portrait to landscape mirror without dismissing and responds to sensor posture`() {
        var settings by mutableStateOf(
            HomeScreensaverSettings(
                displayMode = ScreensaverDisplayMode.PORTRAIT,
                autoOrientationEnabled = true,
                touchToDismiss = true
            )
        )
        var sensorPosture by mutableStateOf<ScreensaverSensorPosture?>(ScreensaverSensorPosture.PORTRAIT)
        var dismissCount = 0

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 380.dp, height = 760.dp)) {
                ChainageScreensaverOverlay(
                    displayState = sampleDisplayState(),
                    location = GeoPoint(29.505, 77.705),
                    bearing = 90f,
                    compassHeading = 90f,
                    routeData = sampleRoute(),
                    screensaverSettings = settings,
                    sensorPostureOverride = sensorPosture,
                    onToggleMirrorQuickMode = { enableMirror, currentlyLandscape ->
                        settings = settings.withMirrorQuickToggle(enableMirror, currentlyLandscape)
                    },
                    onDismiss = { dismissCount += 1 }
                )
            }
        }

        // Starts in Portrait Normal
        val initialNode = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertEquals("PORTRAIT", initialNode.config[ScreensaverDisplayModeKey])
        assertEquals(1f, initialNode.config[DashboardMirrorScaleXKey], 0.001f)
        val toggleNode0 = composeTestRule.onNodeWithTag("screensaver_mirror_quick_toggle").fetchSemanticsNode()
        assertTrue(toggleNode0.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains("Normal") } == true)

        // Tap Quick Toggle -> transitions to Landscape Mirror WITHOUT dismissing (even though touchToDismiss = true)
        composeTestRule.onNodeWithTag("screensaver_mirror_quick_toggle").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, dismissCount)
        assertTrue(settings.mirrorModePreferred)

        val mirrorAfterTap = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertEquals("MIRROR", mirrorAfterTap.config[ScreensaverDisplayModeKey])
        assertEquals(-1f, mirrorAfterTap.config[DashboardMirrorScaleXKey], 0.001f)
        assertEquals(90f, mirrorAfterTap.config[DashboardLandscapeRotationKey], 0.001f)
        val toggleNodeMirror = composeTestRule.onNodeWithTag("screensaver_mirror_quick_toggle").fetchSemanticsNode()
        assertTrue(toggleNodeMirror.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains("Mirror") } == true)

        // Tap outside the toggle -> MUST trigger Touch-to-Dismiss
        composeTestRule.onNodeWithTag("home_chainage_screensaver").performTouchInput { click() }
        composeTestRule.waitForIdle()
        assertEquals(1, dismissCount)

        // Rotate physically to LANDSCAPE_LEFT (-90 deg) -> Mirror renders upright for left-hand landscape (-90f)
        composeTestRule.runOnIdle {
            sensorPosture = ScreensaverSensorPosture.LANDSCAPE_LEFT
        }
        composeTestRule.waitForIdle()
        val landscapeLeftMirror = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertEquals("MIRROR", landscapeLeftMirror.config[ScreensaverDisplayModeKey])
        assertEquals(-90f, landscapeLeftMirror.config[DashboardLandscapeRotationKey], 0.001f)

        // Rotate physically back to Portrait -> automatically exits Mirror display to upright Portrait Normal
        composeTestRule.runOnIdle {
            sensorPosture = ScreensaverSensorPosture.PORTRAIT
        }
        composeTestRule.waitForIdle()
        val uprightPortrait = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertEquals("PORTRAIT", uprightPortrait.config[ScreensaverDisplayModeKey])
        assertEquals(1f, uprightPortrait.config[DashboardMirrorScaleXKey], 0.001f)
        assertEquals(0f, uprightPortrait.config[DashboardLandscapeRotationKey], 0.001f)

        // Rotate back to Landscape -> Mirror HUD automatically resumes because mirrorModePreferred remained true
        composeTestRule.runOnIdle {
            sensorPosture = ScreensaverSensorPosture.LANDSCAPE_RIGHT
        }
        composeTestRule.waitForIdle()
        val restoredMirror = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertEquals("MIRROR", restoredMirror.config[ScreensaverDisplayModeKey])
        assertEquals(-1f, restoredMirror.config[DashboardMirrorScaleXKey], 0.001f)
    }

    @Test
    fun `auto orientation switch in Settings syncs with MainViewModel and JSON persistence`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        idleMain()

        composeTestRule.setContent {
            val state by vm.homeScreensaverSettings.collectAsState()
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ScreensaverSettingsSectionContent(
                    settings = state,
                    onUpdateSettings = { transform -> vm.updateScreensaverSettings(transform) },
                    onSelectDisplayMode = { mode -> vm.setScreensaverDisplayMode(mode) },
                    onToggleMirror = { en -> vm.setScreensaverMirrorEnabled(en) },
                    onHudBrightnessChange = { b -> vm.setScreensaverHudBrightness(b) },
                    onMiniMapOrientationChange = { o -> vm.setHomeMiniMapOrientation(o) },
                    onMiniMapZoomIn = { vm.zoomInHomeMiniMap() },
                    onMiniMapZoomOut = { vm.zoomOutHomeMiniMap() },
                    onMiniMapRecenter = { vm.recenterHomeMiniMap() },
                    onMiniMapStyleChange = { s -> vm.setHomeMiniMapStyle(s) },
                    onResetDefaults = { vm.resetScreensaverSettings() }
                )
            }
        }

        // Expand Display Mode -> verify Auto Orientation switch defaults ON and toggles persistently
        composeTestRule.onNodeWithTag("screensaver_section_display_mode").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_auto_orientation_switch").performScrollTo().assertIsDisplayed()
        assertTrue(vm.homeScreensaverSettings.value.autoOrientationEnabled)

        composeTestRule.onNodeWithTag("screensaver_auto_orientation_switch").performClick()
        repeat(4) {
            composeTestRule.waitForIdle()
            idleMain()
            delay(50L)
        }
        assertFalse(vm.homeScreensaverSettings.value.autoOrientationEnabled)

        // JSON round-trip preserves autoOrientationEnabled = false and mirrorModePreferred
        val customJson = HomeScreensaverSettings(
            displayMode = ScreensaverDisplayMode.MIRROR,
            autoOrientationEnabled = false,
            mirrorModePreferred = true
        ).toJsonString()
        val parsed = HomeScreensaverSettings.fromJsonString(customJson)
        assertFalse(parsed.autoOrientationEnabled)
        assertTrue(parsed.mirrorModePreferred)

        // Clean up back to defaults
        vm.resetScreensaverSettings()
        repeat(4) {
            idleMain()
            delay(50L)
        }
    }
}
