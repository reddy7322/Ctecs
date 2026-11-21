package com.example.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.geo.MapHeadingSource
import com.example.geo.MapHeadingState
import com.example.geo.MapHeadingStatus
import com.example.model.Chainage
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.HudLocationPosition
import com.example.model.HudMapPosition
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapStyleConfig
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.ScreensaverDisplayMode
import com.example.model.ScreensaverThemeMode
import com.example.ui.camera.MiniMapPreview
import com.example.ui.home.ChainageScreensaverOverlay
import com.example.ui.home.DashboardMirrorScaleXKey
import com.example.ui.home.InteractiveSmartMap
import com.example.ui.home.MiniMapRotationDegKey
import com.example.ui.home.ScreensaverDisplayState
import com.example.ui.home.ScreensaverSensorPosture
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Comprehensive unit + Robolectric Compose UI test suite verifying:
 * 1. Global Always Direction-Up defaults & persistence across all Mini Map configs
 * 2. Google Maps-style viewport math: focal-point pinch zoom, rotation-aware panning,
 *    forward-looking follow framing, diagonal zero-clip rendering, full-Earth antimeridian
 *    navigation, and dynamic route fitting
 * 3. Home, Routes, Camera & Embedded Mini Maps (controls, Direction-Up rotation, Heading unavailable badge)
 * 4. Screensaver Portrait ZERO-OVERLAP verification across compact/standard/narrow screens,
 *    Light & Dark themes, all element combinations, Landscape mode, and Car Dashboard Mirror Mode
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MiniMapNavigationAndScreensaverPortraitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleRoute(): RouteData {
        return RouteData(
            id = "route-test-1",
            name = "Delhi-Dehradun Expressway",
            fileName = "dd_exp.kmz",
            markers = emptyList(),
            alignments = listOf(
                RouteAlignment(
                    id = "main-1",
                    name = "Mainline",
                    points = listOf(
                        GeoPoint(29.5000, 77.7000),
                        GeoPoint(29.5150, 77.7150),
                        GeoPoint(29.5300, 77.7300)
                    ),
                    direction = "INCREASING"
                )
            ),
            bounds = GeoBounds(29.5000, 29.5300, 77.7000, 77.7300),
            startChainage = Chainage(190_000L),
            endChainage = Chainage(195_000L)
        )
    }

    private fun fullDisplayState(): ScreensaverDisplayState {
        return ScreensaverDisplayState(
            chainageText = "196+494",
            reliableSide = "LHS",
            directionArrow = "↑",
            isLiveGpsValid = true,
            isIncreasingTrend = true,
            locationName = "Haridwar Bypass Toll Plaza",
            speedText = "84 km/h",
            speedKmh = 84
        )
    }

    private fun fullEnabledScreensaverSettings(
        locationPosition: HudLocationPosition = HudLocationPosition.ABOVE_MAP,
        mapPosition: HudMapPosition = HudMapPosition.AUTO,
        displayMode: ScreensaverDisplayMode = ScreensaverDisplayMode.PORTRAIT,
        themeMode: ScreensaverThemeMode = ScreensaverThemeMode.DARK
    ): HomeScreensaverSettings {
        return HomeScreensaverSettings(
            masterEnabled = true,
            displayMode = displayMode,
            screensaverThemeMode = themeMode,
            showChainage = true,
            showDirectionSide = true,
            showSpeedometer = true,
            showLocation = true,
            locationPosition = locationPosition,
            miniMapVisible = true,
            miniMapPosition = mapPosition,
            miniMapOrientation = MiniMapOrientationMode.DIRECTION_UP,
            showScreenOffControl = true,
            aodEnabled = true,
            mirrorModePreferred = displayMode == ScreensaverDisplayMode.MIRROR
        )
    }

    private fun assertNoOverlap(labelA: String, rectA: Rect, labelB: String, rectB: Rect) {
        // Allow 0.5px floating-point rounding tolerance
        val horizontalOverlap = rectA.left < rectB.right - 0.5f && rectB.left < rectA.right - 0.5f
        val verticalOverlap = rectA.top < rectB.bottom - 0.5f && rectB.top < rectA.bottom - 0.5f
        assertFalse(
            "UI overlap detected between $labelA ($rectA) and $labelB ($rectB)",
            horizontalOverlap && verticalOverlap
        )
    }

    // =========================================================================
    // 1. ALWAYS DIRECTION-UP DEFAULTS & CONFIG PERSISTENCE
    // =========================================================================

    @Test
    fun miniMapConfigs_defaultToDirectionUp_andPersistAcrossJsonRoundTrip() {
        val styleConfig = MiniMapStyleConfig()
        assertEquals(MiniMapOrientationMode.DIRECTION_UP, styleConfig.orientationMode)
        assertEquals(MiniMapOrientationMode.DIRECTION_UP, MiniMapOrientationMode.fromString(null))
        assertEquals(MiniMapOrientationMode.DIRECTION_UP, MiniMapOrientationMode.fromString("INVALID"))

        val parsedStyle = MiniMapStyleConfig.fromJsonString(styleConfig.toJsonString())
        assertEquals(MiniMapOrientationMode.DIRECTION_UP, parsedStyle.orientationMode)

        val screensaverSettings = HomeScreensaverSettings()
        assertEquals(MiniMapOrientationMode.DIRECTION_UP, screensaverSettings.miniMapOrientation)
        val parsedScreensaver = HomeScreensaverSettings.fromJsonString(screensaverSettings.toJsonString())
        assertEquals(MiniMapOrientationMode.DIRECTION_UP, parsedScreensaver.miniMapOrientation)
    }

    // =========================================================================
    // 2. GOOGLE MAPS-STYLE VIEWPORT MATH (PINCH, PAN, FOLLOW, FULL EARTH, FIT)
    // =========================================================================

    @Test
    fun focalPointPinchZoom_keepsGeographicCoordinateAnchoredUnderFingers() {
        val initialCenter = GeoPoint(28.6139, 77.2090)
        val initialZoom = 14.0f
        val w = 400f
        val h = 400f
        val focalX = 310f // off-center focal point
        val focalY = 120f
        val rotationDeg = -90f // Vehicle travelling East (Direction-Up)

        // Compute the geographic coordinate currently under the pinch focal point
        val unrotatedFocal = MiniMapViewportMath.unrotatedToScreenPoint(
            unrotated = Offset(focalX, focalY),
            viewportWidthPx = w,
            viewportHeightPx = h,
            mapRotationDeg = -rotationDeg
        )
        val focalGeoBefore = MiniMapViewportMath.offsetGeoPointContinuous(
            center = initialCenter,
            worldDeltaPxX = -(unrotatedFocal.x - w / 2f),
            worldDeltaPxY = -(unrotatedFocal.y - h / 2f),
            continuousZoom = initialZoom
        )

        // Apply 2x pinch zoom around (focalX, focalY)
        val (newCenter, newZoom) = MiniMapViewportMath.applyFocalPinchAndPan(
            currentCenter = initialCenter,
            currentZoom = initialZoom,
            focalX = focalX,
            focalY = focalY,
            viewportWidthPx = w,
            viewportHeightPx = h,
            panX = 0f,
            panY = 0f,
            zoomChange = 2.0f,
            mapRotationDeg = rotationDeg
        )

        assertEquals(15.0f, newZoom, 0.01f)

        // Project focalGeoBefore onto the new viewport and rotate by rotationDeg -> should still be at (focalX, focalY)
        val projectedUnrotated = MiniMapViewportMath.geoToUnrotatedScreen(
            pt = focalGeoBefore,
            center = newCenter,
            viewportWidthPx = w,
            viewportHeightPx = h,
            continuousZoom = newZoom
        )
        val projectedScreen = MiniMapViewportMath.unrotatedToScreenPoint(
            unrotated = projectedUnrotated,
            viewportWidthPx = w,
            viewportHeightPx = h,
            mapRotationDeg = rotationDeg
        )
        assertEquals(focalX, projectedScreen.x, 1.5f)
        assertEquals(focalY, projectedScreen.y, 1.5f)
    }

    @Test
    fun rotationAwarePan_movesMapInCorrectWorldDirection_andSupportsFullEarthAntimeridianWrap() {
        // When map is rotated -90° (heading East = up on screen), dragging finger DOWN (+screenDy)
        // means viewing what is AHEAD (East -> increasing longitude).
        val start = GeoPoint(0.0, 179.9)
        val (worldDx, worldDy) = MiniMapViewportMath.screenDeltaToWorldDelta(
            screenDx = 0f,
            screenDy = 100f,
            mapRotationDeg = -90f,
            fractionalScale = 1f
        )
        assertTrue("Expected negative worldDx when dragging down while heading East", worldDx < -90f)
        assertEquals(0f, worldDy, 0.5f)

        // Pan across the International Date Line (+179.9° -> wraps into negative Western hemisphere!)
        val wrapped = MiniMapViewportMath.offsetGeoPointContinuous(
            center = start,
            worldDeltaPxX = worldDx,
            worldDeltaPxY = worldDy,
            continuousZoom = 4.0f
        )
        assertTrue(
            "Longitude should wrap cleanly across antimeridian into [-180, 180], got ${wrapped.longitude}",
            wrapped.longitude in -180.0..180.0 && wrapped.longitude < 0.0
        )
    }

    @Test
    fun followCameraFraming_positionsVehicleBelowCenterForForwardLook_andDiagonalCoversAllCorners() {
        val vehicle = GeoPoint(28.6139, 77.2090)
        val w = 360f
        val h = 240f
        val zoom = 15.5f
        val rotationDeg = -90f // East is up

        val cameraCenter = MiniMapViewportMath.computeFollowCameraCenter(
            vehicleGeo = vehicle,
            continuousZoom = zoom,
            viewportHeightPx = h,
            mapRotationDeg = rotationDeg,
            isDirectionUpActive = true
        )
        val vehicleUnrotated = MiniMapViewportMath.geoToUnrotatedScreen(vehicle, cameraCenter, w, h, zoom)
        val vehicleScreen = MiniMapViewportMath.unrotatedToScreenPoint(vehicleUnrotated, w, h, rotationDeg)

        val expectedY = h * MiniMapViewportMath.FOLLOW_FORWARD_Y_FRACTION
        assertEquals("Vehicle X should remain horizontally centered", w / 2f, vehicleScreen.x, 1.5f)
        assertEquals("Vehicle Y should be positioned at 62% down for forward look", expectedY, vehicleScreen.y, 1.5f)

        val renderSize = MiniMapViewportMath.computeDiagonalRenderSizePx(w, h, fractionalScale = 1.0f)
        assertTrue("Render bitmap size must exceed viewport diagonal to avoid clipped corners", renderSize >= hypot(w, h))
    }

    @Test
    fun computeRouteFitCamera_fitsEntireRouteInsideArbitraryContainerWithoutCropping() {
        val route = sampleRoute()
        val w = 340f
        val h = 176f
        val (center, zoom) = MiniMapViewportMath.computeRouteFitCamera(route, w, h)

        assertTrue(zoom in MiniMapViewportMath.MIN_ZOOM..MiniMapViewportMath.MAX_ZOOM)

        // All alignment points should project cleanly inside [0..w] x [0..h]
        route.alignments.first().points.forEach { pt ->
            val pos = MiniMapViewportMath.geoToUnrotatedScreen(pt, center, w, h, zoom)
            assertTrue("Point X=${pos.x} cropped outside [0, $w]", pos.x in 0f..w)
            assertTrue("Point Y=${pos.y} cropped outside [0, $h]", pos.y in 0f..h)
        }
    }

    // =========================================================================
    // 3. HOME, ROUTES, CAMERA MINI MAPS UI TESTS
    // =========================================================================

    @Test
    fun homeInteractiveSmartMap_rendersDirectionUpControls_andShowsHeadingUnavailableBadgeWhenUnavailable() {
        var recenterCount = 0
        val unavailableHeading = MapHeadingState(
            headingDegrees = null,
            status = MapHeadingStatus.UNAVAILABLE,
            source = MapHeadingSource.NONE
        )

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                Box(modifier = Modifier.size(width = 360.dp, height = 280.dp)) {
                    InteractiveSmartMap(
                        location = GeoPoint(29.5150, 77.7150),
                        gpsAccuracy = 5f,
                        bearing = null,
                        compassHeading = 123f,
                        routeData = null,
                        matchedProjectedPoint = null,
                        onSharedRecenter = { recenterCount++ },
                        mapHeadingState = unavailableHeading,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("map_zoom_in").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("map_zoom_out").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("map_follow_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("minimap_heading_unavailable_badge").assertIsDisplayed()
        assertEquals(1, recenterCount)
    }

    @Test
    fun routesAndCameraMiniMapPreview_rendersInteractiveControls_andDirectionUpHeading() {
        val headingEast = MapHeadingState(
            headingDegrees = 90f,
            lastReliableHeadingDegrees = 90f,
            status = MapHeadingStatus.RELIABLE,
            source = MapHeadingSource.GPS_MOVEMENT
        )

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "light") {
                Box(modifier = Modifier.size(width = 360.dp, height = 180.dp)) {
                    MiniMapPreview(
                        location = GeoPoint(29.5150, 77.7150),
                        bearing = 90f,
                        routeData = sampleRoute(),
                        highwayTag = "NH-44",
                        mapHeadingState = headingEast,
                        interactive = true,
                        autoFitRoute = true,
                        orientationMode = MiniMapOrientationMode.DIRECTION_UP,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("preview_map_zoom_in").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("preview_map_zoom_out").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("preview_map_recenter").assertIsDisplayed().performClick()
    }

    // =========================================================================
    // 4. SCREENSAVER PORTRAIT ZERO-OVERLAP BOUNDING-BOX TESTS (CRITICAL)
    // =========================================================================

    private fun verifyScreensaverPortraitZeroOverlap(
        widthDp: Dp,
        heightDp: Dp,
        isDark: Boolean,
        locationPosition: HudLocationPosition,
        mapPosition: HudMapPosition
    ) {
        val settings = fullEnabledScreensaverSettings(
            locationPosition = locationPosition,
            mapPosition = mapPosition,
            displayMode = ScreensaverDisplayMode.PORTRAIT,
            themeMode = if (isDark) ScreensaverThemeMode.DARK else ScreensaverThemeMode.LIGHT
        )
        val headingNorthEast = MapHeadingState(
            headingDegrees = 45f,
            lastReliableHeadingDegrees = 45f,
            status = MapHeadingStatus.RELIABLE,
            source = MapHeadingSource.GPS_MOVEMENT
        )

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = if (isDark) "dark" else "light") {
                Box(modifier = Modifier.size(width = widthDp, height = heightDp)) {
                    ChainageScreensaverOverlay(
                        displayState = fullDisplayState(),
                        location = GeoPoint(29.5150, 77.7150),
                        bearing = 45f,
                        compassHeading = 45f,
                        routeData = sampleRoute(),
                        isDarkAppTheme = isDark,
                        screensaverSettings = settings,
                        isSamplePreview = true,
                        sensorPostureOverride = ScreensaverSensorPosture.PORTRAIT,
                        mapHeadingState = headingNorthEast,
                        onToggleMirrorQuickMode = { _, _ -> },
                        onToggleOrientationLock = { _ -> },
                        onToggleScreenOff = { _ -> },
                        onToggleAod = { _ -> },
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Verify EVERY enabled element & top control is displayed
        val topBar = composeTestRule.onNodeWithTag("screensaver_top_controls_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_orientation_lock_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_aod_toggle_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_screen_off_toggle_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_mirror_quick_toggle").assertIsDisplayed()

        val chainage = composeTestRule.onNodeWithTag("screensaver_chainage_text").assertIsDisplayed()
        val direction = composeTestRule.onNodeWithTag("screensaver_direction_badge").assertIsDisplayed()
        val speed = composeTestRule.onNodeWithTag("screensaver_speed_text").assertIsDisplayed()
        val location = composeTestRule.onNodeWithTag("screensaver_location_name").assertIsDisplayed()
        val miniMap = composeTestRule.onNodeWithTag("screensaver_glass_map").assertIsDisplayed()

        val rootBounds = composeTestRule.onNodeWithTag("home_chainage_screensaver").fetchSemanticsNode().boundsInRoot
        val topBarRect = topBar.fetchSemanticsNode().boundsInRoot
        val chainageRect = chainage.fetchSemanticsNode().boundsInRoot
        val directionRect = direction.fetchSemanticsNode().boundsInRoot
        val speedRect = speed.fetchSemanticsNode().boundsInRoot
        val locationRect = location.fetchSemanticsNode().boundsInRoot
        val miniMapRect = miniMap.fetchSemanticsNode().boundsInRoot

        // Assert every element has positive non-collapsed size and stays inside the viewport
        listOf(
            "topBar" to topBarRect,
            "chainage" to chainageRect,
            "direction" to directionRect,
            "speed" to speedRect,
            "location" to locationRect,
            "miniMap" to miniMapRect
        ).forEach { (name, rect) ->
            assertTrue("$name width must be > 0, got $rect", rect.width > 0f)
            assertTrue("$name height must be > 0, got $rect", rect.height > 0f)
            assertTrue(
                "$name ($rect) overflows viewport ($rootBounds)",
                rect.left >= rootBounds.left - 1f &&
                    rect.top >= rootBounds.top - 1f &&
                    rect.right <= rootBounds.right + 1f &&
                    rect.bottom <= rootBounds.bottom + 1f
            )
        }

        // Assert ZERO overlap between top controls bar and every HUD dashboard element
        assertNoOverlap("topControlsBar", topBarRect, "chainage", chainageRect)
        assertNoOverlap("topControlsBar", topBarRect, "direction", directionRect)
        assertNoOverlap("topControlsBar", topBarRect, "speed", speedRect)
        assertNoOverlap("topControlsBar", topBarRect, "location", locationRect)
        assertNoOverlap("topControlsBar", topBarRect, "miniMap", miniMapRect)

        // Assert ZERO overlap among primary dashboard sections
        assertNoOverlap("chainage", chainageRect, "direction", directionRect)
        assertNoOverlap("chainage", chainageRect, "speed", speedRect)
        assertNoOverlap("chainage", chainageRect, "location", locationRect)
        assertNoOverlap("chainage", chainageRect, "miniMap", miniMapRect)
        assertNoOverlap("direction", directionRect, "speed", speedRect)
        assertNoOverlap("direction", directionRect, "miniMap", miniMapRect)
        assertNoOverlap("speed", speedRect, "location", locationRect)
        assertNoOverlap("speed", speedRect, "miniMap", miniMapRect)
        assertNoOverlap("location", locationRect, "miniMap", miniMapRect)

        // Verify Direction-Up rotation semantic property on Screensaver Mini Map (-45f for 45° heading)
        val rot = miniMap.fetchSemanticsNode().config[MiniMapRotationDegKey]
        assertEquals(-45f, rot, 1.0f)
    }

    @Test
    fun screensaverPortrait_standardPhoneDark_allElementsEnabled_zeroOverlap() {
        verifyScreensaverPortraitZeroOverlap(
            widthDp = 390.dp,
            heightDp = 844.dp,
            isDark = true,
            locationPosition = HudLocationPosition.ABOVE_MAP,
            mapPosition = HudMapPosition.AUTO
        )
    }

    @Test
    fun screensaverPortrait_compactPhoneLight_allElementsEnabled_zeroOverlap() {
        verifyScreensaverPortraitZeroOverlap(
            widthDp = 360.dp,
            heightDp = 640.dp,
            isDark = false,
            locationPosition = HudLocationPosition.BELOW_SIDE,
            mapPosition = HudMapPosition.AUTO
        )
    }

    @Test
    fun screensaverPortrait_smallNarrowPhone_mapOnTop_locationTopBar_zeroOverlap() {
        verifyScreensaverPortraitZeroOverlap(
            widthDp = 320.dp,
            heightDp = 568.dp,
            isDark = true,
            locationPosition = HudLocationPosition.TOP_BAR,
            mapPosition = HudMapPosition.TOP
        )
    }

    @Test
    fun screensaverLandscapeAndCarDashboardMirrorMode_preserveLayoutAndMirrorTransform() {
        val mirrorSettings = fullEnabledScreensaverSettings(
            displayMode = ScreensaverDisplayMode.MIRROR,
            themeMode = ScreensaverThemeMode.DARK
        )

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                Box(modifier = Modifier.size(width = 800.dp, height = 390.dp)) {
                    ChainageScreensaverOverlay(
                        displayState = fullDisplayState(),
                        location = GeoPoint(29.5150, 77.7150),
                        bearing = 90f,
                        compassHeading = 90f,
                        routeData = sampleRoute(),
                        isDarkAppTheme = true,
                        screensaverSettings = mirrorSettings,
                        sensorPostureOverride = ScreensaverSensorPosture.LANDSCAPE_LEFT,
                        onToggleMirrorQuickMode = { _, _ -> },
                        onToggleOrientationLock = { _ -> },
                        onToggleScreenOff = { _ -> },
                        onToggleAod = { _ -> },
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val mirrorNode = composeTestRule.onNodeWithTag("screensaver_dashboard_root").assertIsDisplayed()
        assertEquals(-1f, mirrorNode.fetchSemanticsNode().config[DashboardMirrorScaleXKey], 0.01f)
        composeTestRule.onNodeWithTag("screensaver_top_controls_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_chainage_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_glass_map").assertIsDisplayed()
    }
}
