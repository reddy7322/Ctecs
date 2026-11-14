package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.RouteMarker
import com.example.model.MatchConfidence
import com.example.model.MiniMapCornerShape
import com.example.model.MiniMapDirectionStyle
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapPosition
import com.example.model.MiniMapSizePreset
import com.example.model.MiniMapStyle
import com.example.model.MiniMapStyleConfig
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.overlay.MiniMapRenderer
import com.example.overlay.OverlayRenderer
import com.example.ui.settings.MiniMapSettingsSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MiniMapOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleKmzRoute(): RouteData {
        val pts = listOf(
            GeoPoint(29.4720, 77.7050),
            GeoPoint(29.4765, 77.7080), // ~North-North-East (~30 deg bearing)
            GeoPoint(29.4810, 77.7110)
        )
        val markers = listOf(
            RouteMarker("m0", "KM 200+000", Chainage(200_000L), pts[0]),
            RouteMarker("m1", "KM 200+500", Chainage(200_500L), pts[1]),
            RouteMarker("m2", "KM 201+000", Chainage(201_000L), pts[2])
        )
        return RouteData(
            id = "nh58-route",
            name = "Muzzafarnagar-Haridwar-NH-58-50m.kmz",
            fileName = "Muzzafarnagar-Haridwar-NH-58-50m.kmz",
            markers = markers,
            alignments = listOf(
                RouteAlignment(
                    id = "align-nh58",
                    name = "NH-58 Mainline",
                    points = pts,
                    carriagewayType = "MAIN"
                )
            ),
            bounds = GeoBounds(
                minLat = 29.4720,
                maxLat = 29.4810,
                minLng = 77.7050,
                maxLng = 77.7110
            ),
            startChainage = Chainage(200_000L),
            endChainage = Chainage(201_000L)
        )
    }

    @Test
    fun testMiniMapConfigJsonRoundTripAndIndependentPhotoVideoModes() {
        val photoCfg = MiniMapStyleConfig(
            enabled = true,
            sizePreset = MiniMapSizePreset.CUSTOM,
            customWidthDp = 128,
            customHeightDp = 96,
            opacity = 0.77f,
            position = MiniMapPosition.CUSTOM,
            customPosXFraction = 0.15f,
            customPosYFraction = 0.25f,
            cornerShape = MiniMapCornerShape.SQUARE,
            cornerRadiusDp = 4,
            borderEnabled = false,
            borderColorHex = "#38BDF8",
            borderThicknessDp = 2.5f,
            backgroundAlpha = 0.55f,
            brightness = 1.25f,
            contrast = 1.30f,
            mapStyle = MiniMapStyle.SATELLITE,
            routeColorHex = "#22C55E",
            gpsMarkerColorHex = "#F97316",
            directionMarkerStyle = MiniMapDirectionStyle.CONE,
            shadowEnabled = false,
            autoZoom = false,
            zoomLevel = 17.2f,
            orientationMode = MiniMapOrientationMode.DIRECTION_UP,
            showCurrentPositionMarker = true,
            showRoadAlignment = true,
            showCurrentChainageLabel = false,
            showSideIndicator = true,
            showDirectionArrow = true,
            showNearbyChainageMarkers = false
        )

        val decoded = MiniMapStyleConfig.fromJsonString(photoCfg.toJsonString())
        assertEquals(photoCfg, decoded)
        assertEquals(128, decoded.resolveWidthDp())
        assertEquals(96, decoded.resolveHeightDp())
        assertEquals(0, decoded.resolveCornerRadiusDp()) // Square -> 0

        val videoCfg = MiniMapStyleConfig(
            enabled = false,
            sizePreset = MiniMapSizePreset.LARGE,
            position = MiniMapPosition.TOP_RIGHT,
            mapStyle = MiniMapStyle.DARK,
            orientationMode = MiniMapOrientationMode.AUTO
        )

        val overlay = OverlayConfig(
            showMapInset = true,
            photoMiniMap = photoCfg,
            videoMiniMap = videoCfg
        )

        assertTrue(overlay.isMiniMapActive(isVideo = false))
        assertFalse(overlay.isMiniMapActive(isVideo = true))
        assertEquals(photoCfg, overlay.getEffectiveMiniMapConfig(isVideo = false))
        assertEquals(videoCfg, overlay.getEffectiveMiniMapConfig(isVideo = true))

        // Master OFF disables both Photo and Video regardless of individual toggles
        val masterOff = overlay.copy(showMapInset = false, videoMiniMap = videoCfg.copy(enabled = true))
        assertFalse(masterOff.isMiniMapActive(isVideo = false))
        assertFalse(masterOff.isMiniMapActive(isVideo = true))
    }

    @Test
    fun testPreferencesManagerPersistsMiniMapSettingsAcrossReReads() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)

        val customPhoto = MiniMapStyleConfig(
            enabled = true,
            sizePreset = MiniMapSizePreset.MEDIUM,
            opacity = 0.82f,
            position = MiniMapPosition.TOP_RIGHT,
            mapStyle = MiniMapStyle.DARK,
            orientationMode = MiniMapOrientationMode.DIRECTION_UP,
            zoomLevel = 16.5f,
            autoZoom = false
        )
        val customVideo = MiniMapStyleConfig(
            enabled = true,
            sizePreset = MiniMapSizePreset.LARGE,
            opacity = 0.90f,
            position = MiniMapPosition.BOTTOM_RIGHT,
            mapStyle = MiniMapStyle.SATELLITE,
            directionMarkerStyle = MiniMapDirectionStyle.CHEVRON
        )
        val saved = OverlayConfig(
            showMapInset = true,
            photoMiniMap = customPhoto,
            videoMiniMap = customVideo
        )

        prefs.updateOverlayConfig(saved)
        val reloaded = prefs.overlayConfigFlow.first()

        assertTrue(reloaded.showMapInset)
        assertEquals(customPhoto, reloaded.photoMiniMap)
        assertEquals(customVideo, reloaded.videoMiniMap)

        // Clean up back to defaults
        prefs.updateOverlayConfig(OverlayConfig())
    }

    @Test
    fun testMiniMapRendererAutoZoomAndBearingOrientationResolution() {
        val route = sampleKmzRoute()

        // Close to alignment -> tight zoom
        val onRoad = GeoPoint(29.4765, 77.7080)
        val closeZoom = MiniMapRenderer.computeAutoZoom(onRoad, route)
        assertTrue("Expected high zoom near alignment, got $closeZoom", closeZoom >= 15.5f)

        // ~800m away -> zooms out so alignment remains visible
        val midOffset = GeoPoint(29.4765, 77.7160)
        val midZoom = MiniMapRenderer.computeAutoZoom(midOffset, route)
        assertTrue("Expected medium zoom away from alignment ($midZoom < $closeZoom)", midZoom < closeZoom)

        // Bearing prefers GPS bearing when provided
        assertEquals(123f, MiniMapRenderer.resolveEffectiveBearing(onRoad, 123f, route)!!, 0.01f)

        // Falls back to KMZ alignment tangent bearing (~30 degrees) when GPS bearing is null
        val fallbackBearing = MiniMapRenderer.resolveEffectiveBearing(onRoad, null, route)
        assertNotNull(fallbackBearing)
        assertTrue("Expected NNE tangent bearing 15..45 deg, got $fallbackBearing", fallbackBearing!! in 15f..45f)

        // Map rotation for orientation modes
        assertEquals(0f, MiniMapRenderer.resolveMapRotationDegrees(MiniMapOrientationMode.NORTH_UP, 45f), 0.01f)
        assertEquals(-45f, MiniMapRenderer.resolveMapRotationDegrees(MiniMapOrientationMode.DIRECTION_UP, 45f), 0.01f)
        assertEquals(-45f, MiniMapRenderer.resolveMapRotationDegrees(MiniMapOrientationMode.AUTO, 45f), 0.01f)
        assertEquals(0f, MiniMapRenderer.resolveMapRotationDegrees(MiniMapOrientationMode.AUTO, null), 0.01f)
    }

    @Test
    fun testOverlayRendererDrawsPhotoAndVideoAndAvoidsInfoOverlap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val route = sampleKmzRoute()
        val gpsFix = GeoPoint(29.4765, 77.7080)
        val matchResult = ChainageMatchResult(
            chainage = Chainage(200_500L),
            projectedPoint = gpsFix,
            offsetMetres = 2.0,
            gpsAccuracy = 4.5f,
            confidence = MatchConfidence.HIGH,
            detectedSide = "LHS"
        )

        val photoBitmap = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
        val photoConfig = OverlayConfig(
            showMapInset = true,
            watermarkPosition = "Bottom Right",
            photoMiniMap = MiniMapStyleConfig(
                enabled = true,
                sizePreset = MiniMapSizePreset.MEDIUM,
                position = MiniMapPosition.TOP_LEFT,
                mapStyle = MiniMapStyle.DARK,
                directionMarkerStyle = MiniMapDirectionStyle.CONE
            ),
            videoMiniMap = MiniMapStyleConfig(
                enabled = true,
                sizePreset = MiniMapSizePreset.LARGE,
                position = MiniMapPosition.BOTTOM_LEFT,
                mapStyle = MiniMapStyle.SATELLITE,
                orientationMode = MiniMapOrientationMode.DIRECTION_UP
            )
        )

        val photoSnapshot = CaptureSnapshot(
            timestamp = 1700000000000L,
            location = gpsFix,
            bearing = 32f,
            gpsAccuracy = 4.5f,
            projectProfile = ProjectProfile(highwayId = "NH-58", roadSide = RoadSide.LHS),
            matchResult = matchResult,
            overlayConfig = photoConfig,
            activeRoute = route,
            isVideoCapture = false
        )
        OverlayRenderer.renderOverlay(context, photoBitmap, photoSnapshot, isVideoFrame = false)

        // Video frame (16:9 landscape) with retained GPS after signal loss
        val videoBitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val videoRetainedSnapshot = CaptureSnapshot(
            timestamp = 1700000005000L,
            location = null,
            lastValidLocation = gpsFix,
            lastValidBearing = 32f,
            isGpsRetained = true,
            bearing = null,
            gpsAccuracy = 0f,
            projectProfile = ProjectProfile(highwayId = "NH-58", roadSide = RoadSide.LHS),
            matchResult = matchResult,
            overlayConfig = photoConfig,
            activeRoute = route,
            isVideoCapture = true
        )
        assertEquals(gpsFix, videoRetainedSnapshot.effectiveMiniMapLocation)
        assertEquals(32f, videoRetainedSnapshot.effectiveMiniMapBearing!!, 0.01f)
        OverlayRenderer.renderOverlay(context, videoBitmap, videoRetainedSnapshot, isVideoFrame = true)

        // Missing GPS and missing route must never crash or invent geometry
        val noDataBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val emptySnapshot = CaptureSnapshot(
            timestamp = 1700000010000L,
            location = null,
            lastValidLocation = null,
            overlayConfig = photoConfig,
            activeRoute = null,
            isVideoCapture = false
        )
        assertNull(emptySnapshot.effectiveMiniMapLocation)
        OverlayRenderer.renderOverlay(context, noDataBitmap, emptySnapshot, isVideoFrame = false)

        // Overlap avoidance: Mini Map Bottom Left vs Timestamp info block colliding in Bottom Left
        val mapRect = OverlayRenderer.computeMiniMapRect(
            width = 1080,
            height = 1440,
            padLeftPx = 24f,
            padRightPx = 24f,
            padTopPx = 24f,
            padBottomPx = 24f,
            miniMapConfig = MiniMapStyleConfig(position = MiniMapPosition.BOTTOM_LEFT)
        )
        val infoBounds = RectF(24f, 1200f, 600f, 1416f)
        val adjusted = OverlayRenderer.adjustMiniMapRectForInfoBlock(
            mapRect = mapRect,
            infoBounds = infoBounds,
            miniMapConfig = MiniMapStyleConfig(position = MiniMapPosition.BOTTOM_LEFT),
            height = 1440,
            padTopPx = 24f,
            padBottomPx = 24f,
            gapPx = 16f
        )
        assertFalse(
            "Adjusted Mini Map rect should not intersect timestamp info bounds",
            RectF.intersects(adjusted, infoBounds)
        )
        assertTrue("Adjusted Bottom Left mini map should shift above info block", adjusted.bottom <= infoBounds.top)

        // Direct Canvas draw verification with Master OFF ensures nothing fails
        val canvas = Canvas(noDataBitmap)
        MiniMapRenderer.drawMiniMap(
            context = context,
            canvas = canvas,
            bounds = RectF(20f, 20f, 200f, 200f),
            config = photoConfig.photoMiniMap,
            location = null,
            isGpsRetained = false,
            bearing = null,
            routeData = null,
            chainageLabel = null,
            sideLabel = null,
            dpScale = 2f
        )
    }

    @Test
    fun testMiniMapSettingsSectionUiInteractiveFlow() {
        var latestSavedConfig: OverlayConfig? = null
        var resetCalled = false

        composeTestRule.setContent {
            MaterialTheme {
                var savedOverlay by remember { mutableStateOf(OverlayConfig()) }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MiniMapSettingsSection(
                        savedOverlay = savedOverlay,
                        liveLocation = GeoPoint(29.4765, 77.7080),
                        liveBearing = 35f,
                        activeRoute = sampleKmzRoute(),
                        currentChainage = "200+500",
                        currentSide = "LHS",
                        accentColor = Color(0xFF4F46E5),
                        accentContentColor = Color.White,
                        isExpanded = true,
                        onToggleExpand = {},
                        onSaveConfig = { updated ->
                            savedOverlay = updated
                            latestSavedConfig = updated
                        },
                        onResetMiniMapDefaults = {
                            resetCalled = true
                            savedOverlay = savedOverlay.copy(
                                showMapInset = true,
                                photoMiniMap = MiniMapStyleConfig(),
                                videoMiniMap = MiniMapStyleConfig()
                            )
                        },
                        onShowMessage = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mini_map_settings_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("minimap_settings_live_preview").performScrollTo().assertIsDisplayed()

        // Switch to Video Settings and pick Large + Top Right independently
        composeTestRule.onNodeWithTag("minimap_mode_video").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Large").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Top Right").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Cancel Changes must revert unsaved edits back to saved Small / Bottom Left
        composeTestRule.onNodeWithTag("minimap_cancel_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Edit Video mode again, Copy Photo -> Video, then save
        composeTestRule.onNodeWithText("Large").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Top Right").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("minimap_save_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertNotNull(latestSavedConfig)
        assertEquals(MiniMapSizePreset.LARGE, latestSavedConfig!!.videoMiniMap.sizePreset)
        assertEquals(MiniMapPosition.TOP_RIGHT, latestSavedConfig!!.videoMiniMap.position)
        // Photo config remains independent
        assertEquals(MiniMapSizePreset.SMALL, latestSavedConfig!!.photoMiniMap.sizePreset)
        assertEquals(MiniMapPosition.BOTTOM_LEFT, latestSavedConfig!!.photoMiniMap.position)

        // Toggle Master OFF and save
        composeTestRule.onNodeWithTag("switch_master_minimap").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("minimap_save_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(latestSavedConfig!!.showMapInset)

        // Reset to Default
        composeTestRule.onNodeWithTag("minimap_reset_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(resetCalled)
    }
}
