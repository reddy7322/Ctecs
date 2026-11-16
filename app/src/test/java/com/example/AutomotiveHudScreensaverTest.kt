package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapStyle
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.ScreensaverDisplayMode
import com.example.ui.MainViewModel
import com.example.ui.home.ChainageScreensaverOverlay
import com.example.ui.home.DashboardHudBrightnessKey
import com.example.ui.home.DashboardLandscapeRotationKey
import com.example.ui.home.DashboardMirrorScaleXKey
import com.example.ui.home.HomeScreensaverAndMapControls
import com.example.ui.home.MiniMapRotationDegKey
import com.example.ui.home.ScreensaverDisplayModeKey
import com.example.ui.home.ScreensaverDisplayState
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
@Config(sdk = [36])
class AutomotiveHudScreensaverTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleRoute(): RouteData {
        return RouteData(
            id = "hud-route-1",
            name = "Sample Alignment",
            fileName = "sample.kmz",
            markers = emptyList(),
            alignments = listOf(
                RouteAlignment(
                    id = "main-1",
                    name = "Mainline",
                    points = listOf(
                        GeoPoint(29.5000, 77.7000),
                        GeoPoint(29.5050, 77.7050),
                        GeoPoint(29.5100, 77.7100)
                    ),
                    direction = "INCREASING"
                )
            ),
            bounds = GeoBounds(29.49, 29.52, 77.69, 77.72),
            startChainage = com.example.model.Chainage(200_000L),
            endChainage = com.example.model.Chainage(210_000L)
        )
    }

    private fun sampleDisplayState(
        chainage: String = "204+320",
        side: String? = "LHS",
        speedKmh: Int? = 68,
        speedText: String = "68 km/h",
        locationName: String = "Roorkee Bypass"
    ): ScreensaverDisplayState {
        return ScreensaverDisplayState(
            chainageText = chainage,
            reliableSide = side,
            directionArrow = when (side) {
                "LHS" -> "↑"
                "RHS" -> "↓"
                else -> ""
            },
            isLiveGpsValid = true,
            isIncreasingTrend = true,
            locationName = locationName,
            speedText = speedText,
            speedKmh = speedKmh
        )
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun collectDescendantRolesAndClickables(node: SemanticsNode): List<SemanticsNode> {
        val result = mutableListOf<SemanticsNode>()
        fun walk(current: SemanticsNode, isRoot: Boolean) {
            if (!isRoot) {
                val role = current.config.getOrNull(SemanticsProperties.Role)
                val hasClick = current.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
                if (role == Role.Button || role == Role.Switch || role == Role.RadioButton || hasClick) {
                    result.add(current)
                }
            }
            for (child in current.children) {
                walk(child, isRoot = false)
            }
        }
        walk(node, isRoot = true)
        return result
    }

    @Test
    fun `mirror mode couples strictly with landscape and exits cleanly when portrait selected`() {
        val initial = HomeScreensaverSettings()
        assertEquals(ScreensaverDisplayMode.PORTRAIT, initial.displayMode)
        assertFalse(initial.isLandscapeLayout)
        assertFalse(initial.isMirrorMode)

        // Enabling Mirror ON from Portrait automatically switches to Landscape HUD Mirror
        val mirrored = initial.setMirrorEnabled(true)
        assertEquals(ScreensaverDisplayMode.MIRROR, mirrored.displayMode)
        assertTrue(mirrored.isLandscapeLayout)
        assertTrue(mirrored.isMirrorMode)

        // Toggling Mirror OFF keeps Landscape Dashboard active
        val landscapeOnly = mirrored.setMirrorEnabled(false)
        assertEquals(ScreensaverDisplayMode.LANDSCAPE, landscapeOnly.displayMode)
        assertTrue(landscapeOnly.isLandscapeLayout)
        assertFalse(landscapeOnly.isMirrorMode)

        // Selecting Portrait while Mirror is ON automatically disables Mirror
        val backToPortrait = mirrored.selectPortrait()
        assertEquals(ScreensaverDisplayMode.PORTRAIT, backToPortrait.displayMode)
        assertFalse(backToPortrait.isLandscapeLayout)
        assertFalse(backToPortrait.isMirrorMode)

        // JSON persistence round-trip preserves all fields
        val customized = mirrored
            .withHudBrightness(0.55f)
            .withOrientation(MiniMapOrientationMode.DIRECTION_UP)
            .withZoomIn()
            .withMiniMapVisible(false)
            .withMiniMapStyle(MiniMapStyle.DARK)
        val restored = HomeScreensaverSettings.fromJsonString(customized.toJsonString())
        assertEquals(ScreensaverDisplayMode.MIRROR, restored.displayMode)
        assertEquals(0.55f, restored.hudBrightness, 0.001f)
        assertEquals(MiniMapOrientationMode.DIRECTION_UP, restored.miniMapOrientation)
        assertEquals(customized.miniMapZoomLevel, restored.miniMapZoomLevel, 0.001f)
        assertFalse(restored.miniMapVisible)
        assertEquals(MiniMapStyle.DARK, restored.miniMapStyle)
    }

    @Test
    fun `viewModel persists screensaver mode mirror coupling and shared minimap settings across restarts`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm1 = MainViewModel(app)
        idleMain()

        // Turn Mirror ON -> auto selects Landscape + Mirror
        vm1.setScreensaverMirrorEnabled(true)
        vm1.setScreensaverHudBrightness(0.45f)
        vm1.setHomeMiniMapOrientation(MiniMapOrientationMode.AUTO)
        vm1.zoomInHomeMiniMap()
        vm1.setHomeMiniMapStyle(MiniMapStyle.SATELLITE)
        vm1.setHomeMiniMapVisible(false)
        repeat(6) {
            idleMain()
            delay(80L)
        }

        val state1 = vm1.homeScreensaverSettings.value
        assertEquals(ScreensaverDisplayMode.MIRROR, state1.displayMode)
        assertTrue(state1.isLandscapeLayout)
        assertTrue(state1.isMirrorMode)
        assertEquals(0.45f, state1.hudBrightness, 0.01f)
        assertEquals(MiniMapOrientationMode.AUTO, state1.miniMapOrientation)
        assertEquals(MiniMapStyle.SATELLITE, state1.miniMapStyle)
        assertFalse(state1.miniMapVisible)

        // Recreate MainViewModel and verify DataStore reload restores exact state
        val vm2 = MainViewModel(app)
        var attempts = 0
        while (vm2.homeScreensaverSettings.value.displayMode != ScreensaverDisplayMode.MIRROR && attempts < 20) {
            delay(80L)
            idleMain()
            attempts++
        }
        val state2 = vm2.homeScreensaverSettings.value
        assertEquals(ScreensaverDisplayMode.MIRROR, state2.displayMode)
        assertEquals(0.45f, state2.hudBrightness, 0.01f)
        assertEquals(MiniMapOrientationMode.AUTO, state2.miniMapOrientation)
        assertEquals(state1.miniMapZoomLevel, state2.miniMapZoomLevel, 0.01f)
        assertEquals(MiniMapStyle.SATELLITE, state2.miniMapStyle)
        assertFalse(state2.miniMapVisible)

        // Reset clean
        vm2.setScreensaverDisplayMode(ScreensaverDisplayMode.PORTRAIT)
        vm2.setHomeMiniMapVisible(true)
        repeat(4) {
            idleMain()
            delay(50L)
        }
    }

    @Test
    fun `mirror mode applies horizontal flip at complete dashboard root with brightness and button-free screensaver`() {
        var settingsState by mutableStateOf(
            HomeScreensaverSettings(
                displayMode = ScreensaverDisplayMode.PORTRAIT,
                hudBrightness = 0.62f,
                miniMapVisible = true,
                miniMapOrientation = MiniMapOrientationMode.DIRECTION_UP
            )
        )
        var dismissCount = 0

        composeTestRule.setContent {
            // Portrait phone aspect viewport (380x760) to also test portrait-to-landscape HUD rotation
            Box(modifier = Modifier.size(width = 380.dp, height = 760.dp)) {
                ChainageScreensaverOverlay(
                    displayState = sampleDisplayState(),
                    location = GeoPoint(29.504, 77.704),
                    bearing = 90f,
                    compassHeading = 90f,
                    routeData = sampleRoute(),
                    screensaverSettings = settingsState,
                    onDismiss = { dismissCount += 1 }
                )
            }
        }

        // 1. Portrait Mode checks
        composeTestRule.onNodeWithTag("screensaver_portrait_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_chainage_text").assertTextEquals("204+320")
        composeTestRule.onNodeWithTag("screensaver_speed_text").assertTextEquals("68 km/h")
        composeTestRule.onNodeWithTag("screensaver_glass_map").assertIsDisplayed()

        val portraitRootNode = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertEquals(1f, portraitRootNode.config[DashboardMirrorScaleXKey], 0.001f)
        assertEquals(0f, portraitRootNode.config[DashboardLandscapeRotationKey], 0.001f)
        assertEquals("PORTRAIT", portraitRootNode.config[ScreensaverDisplayModeKey])
        assertTrue(
            "Screensaver must contain zero buttons or interactive child controls",
            collectDescendantRolesAndClickables(
                composeTestRule.onNodeWithTag("home_chainage_screensaver").fetchSemanticsNode()
            ).isEmpty()
        )

        val mapNode = composeTestRule.onNodeWithTag("screensaver_glass_map").fetchSemanticsNode()
        assertEquals(-90f, mapNode.config[MiniMapRotationDegKey], 0.001f)

        // 2. Landscape Mode checks (rotates dashboard +90 deg inside portrait viewport cleanly)
        composeTestRule.runOnIdle {
            settingsState = settingsState.selectLandscape()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_landscape_layout").assertIsDisplayed()
        val landscapeRootNode = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertEquals(1f, landscapeRootNode.config[DashboardMirrorScaleXKey], 0.001f)
        assertEquals(90f, landscapeRootNode.config[DashboardLandscapeRotationKey], 0.001f)
        assertEquals("LANDSCAPE", landscapeRootNode.config[ScreensaverDisplayModeKey])

        // 3. Mirror HUD Mode checks (complete dashboard root flips scaleX = -1f & obeys hudBrightness)
        composeTestRule.runOnIdle {
            settingsState = settingsState.setMirrorEnabled(true)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_landscape_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_chainage_text").assertTextEquals("204+320")
        composeTestRule.onNodeWithTag("screensaver_speed_text").assertTextEquals("68 km/h")
        val mirrorRootNode = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertEquals(-1f, mirrorRootNode.config[DashboardMirrorScaleXKey], 0.001f)
        assertEquals(0.62f, mirrorRootNode.config[DashboardHudBrightnessKey], 0.001f)
        assertEquals(90f, mirrorRootNode.config[DashboardLandscapeRotationKey], 0.001f)
        assertEquals("MIRROR", mirrorRootNode.config[ScreensaverDisplayModeKey])
        assertTrue(
            "Mirror HUD screensaver must remain strictly button-free",
            collectDescendantRolesAndClickables(
                composeTestRule.onNodeWithTag("home_chainage_screensaver").fetchSemanticsNode()
            ).isEmpty()
        )

        // 4. Shared Mini Map Hide hides map across screensaver modes
        composeTestRule.runOnIdle {
            settingsState = settingsState.withMiniMapVisible(false)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_glass_map").assertDoesNotExist()
    }

    @Test
    fun `home dashboard controls update modes mirror coupling and shared minimap state`() {
        var settingsState by mutableStateOf(HomeScreensaverSettings())

        composeTestRule.setContent {
            HomeScreensaverAndMapControls(
                settings = settingsState,
                onSelectMode = { mode ->
                    settingsState = when (mode) {
                        ScreensaverDisplayMode.PORTRAIT -> settingsState.selectPortrait()
                        ScreensaverDisplayMode.LANDSCAPE -> settingsState.selectLandscape()
                        ScreensaverDisplayMode.MIRROR -> settingsState.setMirrorEnabled(true)
                    }
                },
                onToggleMirror = { enabled -> settingsState = settingsState.setMirrorEnabled(enabled) },
                onHudBrightnessChange = { b -> settingsState = settingsState.withHudBrightness(b) },
                onOrientationChange = { o -> settingsState = settingsState.withOrientation(o) },
                onZoomIn = { settingsState = settingsState.withZoomIn() },
                onZoomOut = { settingsState = settingsState.withZoomOut() },
                onRecenter = { settingsState = settingsState.withRecenter() },
                onToggleMapVisible = { settingsState = settingsState.withMiniMapVisible(!settingsState.miniMapVisible) },
                onStyleChange = { s -> settingsState = settingsState.withMiniMapStyle(s) },
                cardBgColor = androidx.compose.ui.graphics.Color.White,
                cardBorderColor = androidx.compose.ui.graphics.Color.LightGray,
                mainTextColor = androidx.compose.ui.graphics.Color.Black,
                subTextColor = androidx.compose.ui.graphics.Color.Gray,
                isDark = false,
                isCompact = false
            )
        }

        // Turning Mirror ON auto-selects Landscape & reveals HUD brightness slider
        composeTestRule.onNodeWithTag("home_mode_mirror_switch").performClick()
        composeTestRule.waitForIdle()
        assertTrue(settingsState.isMirrorMode)
        assertTrue(settingsState.isLandscapeLayout)
        composeTestRule.onNodeWithTag("home_hud_brightness_slider").assertIsDisplayed()

        // Tapping Portrait exits Mirror mode cleanly
        composeTestRule.onNodeWithTag("home_mode_portrait_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ScreensaverDisplayMode.PORTRAIT, settingsState.displayMode)
        assertFalse(settingsState.isMirrorMode)

        // Shared Mini Map controls update orientation, zoom, recenter, visibility, and style
        val initialZoom = settingsState.miniMapZoomLevel
        composeTestRule.onNodeWithTag("home_map_orient_direction_up").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("home_map_zoom_in_btn").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("home_map_recenter_btn").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("home_map_style_dark").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("home_map_visibility_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(MiniMapOrientationMode.DIRECTION_UP, settingsState.miniMapOrientation)
        assertTrue(settingsState.miniMapZoomLevel > initialZoom)
        assertEquals(1L, settingsState.recenterEpoch)
        assertEquals(MiniMapStyle.DARK, settingsState.miniMapStyle)
        assertFalse(settingsState.miniMapVisible)
    }
}
