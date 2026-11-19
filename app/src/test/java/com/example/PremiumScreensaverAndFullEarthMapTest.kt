package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.geo.ChainageMatcher
import com.example.geo.OsmTileManager
import com.example.location.LocationState
import com.example.model.Chainage
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.MatchConfidence
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.ScreensaverDisplayMode
import com.example.model.ScreensaverThemeMode
import com.example.model.resolveEffectiveScreensaverPresentation
import com.example.ui.MainViewModel
import com.example.ui.home.AutomotiveChainageScreensaverContent
import com.example.ui.home.ChainageScreensaverOverlay
import com.example.ui.home.DashboardMirrorScaleXKey
import com.example.ui.home.HomeScreensaverCoordinator
import com.example.ui.home.ScreensaverDisplayModeKey
import com.example.ui.home.ScreensaverDisplayState
import com.example.ui.home.ScreensaverSensorPosture
import com.example.ui.home.ScreensaverThemeDarkKey
import com.example.ui.settings.ScreensaverSettingsSectionContent
import com.example.util.ScreenWakeLockManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PremiumScreensaverAndFullEarthMapTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sampleRoute(): RouteData = RouteData(
        id = "nh58-corridor",
        name = "NH-58 Roorkee Bypass",
        fileName = "nh58.kmz",
        markers = emptyList(),
        alignments = listOf(
            RouteAlignment(
                id = "main",
                name = "Mainline",
                points = listOf(
                    GeoPoint(29.850, 77.880),
                    GeoPoint(29.860, 77.890)
                ),
                direction = "INCREASING"
            )
        ),
        bounds = GeoBounds(29.845, 29.865, 77.875, 77.895),
        startChainage = Chainage(196_000L),
        endChainage = Chainage(205_000L)
    )

    private fun sampleDisplayState(chainage: String = "196+494"): ScreensaverDisplayState =
        ScreensaverDisplayState(
            chainageText = chainage,
            reliableSide = "RHS",
            directionArrow = "↓",
            isLiveGpsValid = true,
            isIncreasingTrend = false,
            locationName = "Badrabad-Rajputna Road",
            speedText = "68 km/h",
            speedKmh = 68
        )

    @Test
    fun `full Earth OsmTileManager wraps antimeridian handles Z1 to Z19 and never extends route chainage`() {
        // Antimeridian longitude wrapping
        assertEquals(-165.0, OsmTileManager.wrapLongitude(195.0), 0.001)
        assertEquals(165.0, OsmTileManager.wrapLongitude(-195.0), 0.001)

        // Tile X wrapping & Tile Y clamping at zoom 2 (4x4 grid)
        assertEquals(3, OsmTileManager.wrapTileX(-1, 2))
        assertEquals(0, OsmTileManager.wrapTileX(4, 2))
        assertTrue(OsmTileManager.isValidTileY(0, 2))
        assertTrue(OsmTileManager.isValidTileY(3, 2))
        assertFalse(OsmTileManager.isValidTileY(-1, 2))
        assertFalse(OsmTileManager.isValidTileY(4, 2))

        // Full Earth view range -> zoom mapping spans Z2..Z19 without artificial lock
        assertTrue(OsmTileManager.zoomFromViewRangeMetres(OsmTileManager.MAX_VIEW_RANGE_METRES) <= 3)
        assertEquals(19, OsmTileManager.zoomFromViewRangeMetres(OsmTileManager.MIN_VIEW_RANGE_METRES))
        assertEquals("500 m", OsmTileManager.formatScaleLabel(500.0))
        assertEquals("2500 km", OsmTileManager.formatScaleLabel(2_500_000.0))

        // Panning across Pacific dateline wraps cleanly without clamping to a local corridor
        val fiji = GeoPoint(-17.8, 179.5)
        val pannedAcrossDateline = OsmTileManager.offsetGeoPointByPixels(
            center = fiji,
            deltaPxX = -1500f,
            deltaPxY = 0f,
            zoom = 4
        )
        assertTrue(pannedAcrossDateline.longitude < 0.0 && pannedAcrossDateline.longitude > -180.0)

        // Shortest-path screen projection across antimeridian stays close rather than jumping world width
        val (sx, _) = OsmTileManager.geoToScreen(
            pt = GeoPoint(0.0, -179.0),
            center = GeoPoint(0.0, 179.0),
            widthPx = 800f,
            heightPx = 600f,
            zoom = 3
        )
        assertTrue("Dateline projection jumped unexpectedly: sx=$sx", abs(sx - 400f) < 100f)

        // Chainage MUST NOT extend outside the imported NH-58 corridor even when map views London or New York
        val route = sampleRoute()
        val outsideMatch = ChainageMatcher.match(
            gpsPoint = GeoPoint(51.5074, -0.1278),
            gpsAccuracy = 5f,
            routeData = route
        )
        assertNull(outsideMatch.chainage)
        assertEquals(MatchConfidence.NO_MATCH, outsideMatch.confidence)
    }

    @Test
    fun `screensaver coordinator acquires KeepScreenOn when active and releases on dismiss or when disabled`() {
        var keepAwake by mutableStateOf(true)
        var isVisible by mutableStateOf(false)

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            HomeScreensaverCoordinator(
                isBlockingUiOpen = false,
                inactivityTimeoutMs = 5_000L,
                isScreensaverVisible = isVisible,
                onShowScreensaver = { isVisible = true },
                onDismissScreensaver = { isVisible = false },
                masterEnabled = true,
                autoActivateEnabled = true,
                touchToDismiss = true,
                autoRestartTimer = true,
                keepScreenAwake = keepAwake,
                screensaverContent = { dismiss ->
                    ChainageScreensaverOverlay(
                        displayState = sampleDisplayState(),
                        location = GeoPoint(29.855, 77.885),
                        bearing = 90f,
                        compassHeading = 90f,
                        routeData = sampleRoute(),
                        screensaverSettings = HomeScreensaverSettings(keepScreenAwake = keepAwake),
                        onDismiss = dismiss
                    )
                }
            ) { interactionModifier ->
                Box(modifier = interactionModifier.fillMaxSize().testTag("home_idle_box"))
            }
        }

        // Normal Home Page -> wake lock must NOT be held by Screensaver
        assertFalse(ScreenWakeLockManager.getActiveRequesters().contains("HomeScreensaver"))

        // Advance past 5s timeout -> Screensaver opens and acquires HomeScreensaver wake lock
        composeTestRule.mainClock.advanceTimeBy(5_200L)
        composeTestRule.waitForIdle()
        assertTrue(isVisible)
        assertTrue(ScreenWakeLockManager.getActiveRequesters().contains("HomeScreensaver"))

        // Toggle keepScreenAwake OFF while active -> immediately releases wake lock
        composeTestRule.runOnIdle { keepAwake = false }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        assertFalse(ScreenWakeLockManager.getActiveRequesters().contains("HomeScreensaver"))

        // Toggle back ON -> re-acquires, then dismissing releases immediately
        composeTestRule.runOnIdle { keepAwake = true }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        assertTrue(ScreenWakeLockManager.getActiveRequesters().contains("HomeScreensaver"))

        composeTestRule.runOnIdle { isVisible = false }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        assertFalse(ScreenWakeLockManager.getActiveRequesters().contains("HomeScreensaver"))
    }

    @Test
    fun `orientation lock freezes posture across sensor changes without dismissing screensaver`() {
        // 1. Model presentation resolution under orientation lock
        val lockedPortrait = HomeScreensaverSettings(
            displayMode = ScreensaverDisplayMode.MIRROR,
            mirrorModePreferred = true,
            autoOrientationEnabled = true
        ).withOrientationLock(locked = true, currentIsLandscape = false)

        val resolvedWhileTiltedLandscape = resolveEffectiveScreensaverPresentation(
            settings = lockedPortrait,
            sensorIsLandscape = true,
            windowIsLandscape = true
        )
        assertEquals(ScreensaverDisplayMode.PORTRAIT, resolvedWhileTiltedLandscape.activeMode)
        assertFalse(resolvedWhileTiltedLandscape.isLandscape)
        assertTrue(resolvedWhileTiltedLandscape.isOrientationLocked)

        // 2. UI Orientation Lock button toggles lock without dismissing and ignores subsequent sensor tilt
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
            Box(modifier = Modifier.size(width = 380.dp, height = 740.dp)) {
                ChainageScreensaverOverlay(
                    displayState = sampleDisplayState(),
                    location = GeoPoint(29.855, 77.885),
                    bearing = 90f,
                    compassHeading = 90f,
                    routeData = sampleRoute(),
                    screensaverSettings = settings,
                    sensorPostureOverride = sensorPosture,
                    onToggleMirrorQuickMode = { en, isLand ->
                        settings = settings.withMirrorQuickToggle(en, isLand)
                    },
                    onToggleOrientationLock = { isLand ->
                        settings = settings.withOrientationLock(!settings.orientationLocked, isLand)
                    },
                    onDismiss = { dismissCount += 1 }
                )
            }
        }

        // Tap Orientation Lock button -> locks Portrait without dismissing
        composeTestRule.onNodeWithTag("screensaver_orientation_lock_btn").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, dismissCount)
        assertTrue(settings.orientationLocked)

        // Tilt sensor to LANDSCAPE -> stays locked in PORTRAIT
        composeTestRule.runOnIdle { sensorPosture = ScreensaverSensorPosture.LANDSCAPE_RIGHT }
        composeTestRule.waitForIdle()
        val lockedNode = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertEquals("PORTRAIT", lockedNode.config[ScreensaverDisplayModeKey])

        // Unlock -> immediately rotates to LANDSCAPE
        composeTestRule.onNodeWithTag("screensaver_orientation_lock_btn").performClick()
        composeTestRule.waitForIdle()
        assertFalse(settings.orientationLocked)
        val unlockedNode = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertEquals("LANDSCAPE", unlockedNode.config[ScreensaverDisplayModeKey])
    }

    @Test
    fun `screensaver honors light and dark themes cleanly`() {
        var themeMode by mutableStateOf(ScreensaverThemeMode.LIGHT)

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 680.dp, height = 360.dp)) {
                AutomotiveChainageScreensaverContent(
                    displayState = sampleDisplayState("201+860"),
                    location = GeoPoint(29.855, 77.885),
                    bearing = 90f,
                    compassHeading = 90f,
                    routeData = sampleRoute(),
                    mapThemePreference = "light",
                    mapTypePreference = "standard",
                    isDarkAppTheme = true,
                    screensaverSettings = HomeScreensaverSettings(
                        displayMode = ScreensaverDisplayMode.LANDSCAPE,
                        autoOrientationEnabled = false,
                        screensaverThemeMode = themeMode
                    )
                )
            }
        }

        // Light Theme verification on Screensaver
        val hudLight = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertFalse(hudLight.config[ScreensaverThemeDarkKey])

        // Dark Theme verification on Screensaver
        composeTestRule.runOnIdle { themeMode = ScreensaverThemeMode.DARK }
        composeTestRule.waitForIdle()
        val hudDark = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
        assertTrue(hudDark.config[ScreensaverThemeDarkKey])
    }

    @Test
    fun `new settings controls and ViewModel JSON persistence work cleanly`() = runBlocking {
        val serialized = HomeScreensaverSettings(
            keepScreenAwake = false,
            orientationLocked = true,
            lockedOrientationIsLandscape = false,
            screensaverThemeMode = ScreensaverThemeMode.LIGHT
        ).toJsonString()
        val parsed = HomeScreensaverSettings.fromJsonString(serialized)
        assertFalse(parsed.keepScreenAwake)
        assertTrue(parsed.orientationLocked)
        assertEquals(false, parsed.lockedOrientationIsLandscape)
        assertEquals(ScreensaverThemeMode.LIGHT, parsed.screensaverThemeMode)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        idleMain()

        composeTestRule.setContent {
            val state by vm.homeScreensaverSettings.collectAsState()
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ScreensaverSettingsSectionContent(
                    settings = state,
                    onUpdateSettings = { t -> vm.updateScreensaverSettings(t) },
                    onSelectDisplayMode = { m -> vm.setScreensaverDisplayMode(m) },
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

        // Expand Display Mode -> toggle Keep Screen Awake, Orientation Lock, and Light Theme
        composeTestRule.onNodeWithTag("screensaver_section_display_mode").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_keep_awake_switch").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("screensaver_orientation_lock_switch").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("screensaver_theme_light").performScrollTo().performClick()
        repeat(4) {
            composeTestRule.waitForIdle()
            idleMain()
            delay(40L)
        }
        assertFalse(vm.homeScreensaverSettings.value.keepScreenAwake)
        assertTrue(vm.homeScreensaverSettings.value.orientationLocked)
        assertEquals(ScreensaverThemeMode.LIGHT, vm.homeScreensaverSettings.value.screensaverThemeMode)

        // Confirm Lock-Screen Navigation subsection is completely removed
        composeTestRule.onNodeWithTag("screensaver_section_lockscreen").assertDoesNotExist()

        vm.resetScreensaverSettings()
        repeat(4) {
            idleMain()
            delay(40L)
        }
    }
}
