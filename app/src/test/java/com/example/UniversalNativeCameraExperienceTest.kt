package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.os.Looper
import android.util.Size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.camera.CameraDeviceCapabilityManager
import com.example.camera.CameraHardwareLensEntry
import com.example.camera.CameraHardwareLevel
import com.example.camera.CameraOutputFormatEngine
import com.example.camera.CameraUltraWideZoomManager
import com.example.model.CameraCropMode
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UniversalNativeCameraExperienceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun advanceAndIdle(ms: Long = 200L) {
        composeTestRule.mainClock.advanceTimeBy(ms)
        shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
    }

    @Test
    fun multiDeviceCapabilityDetection_adaptsAcrossFlagshipMidRangeAndBudgetHardware() {
        // 1. Flagship multi-lens device (e.g. Samsung Galaxy S-Ultra: 0.6x UW + 1x + 3x + 10x optical tele, 12MP, 4K60)
        val flagshipEntries = listOf(
            CameraHardwareLensEntry(
                cameraId = "0",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 0.6f,
                maxZoomRatio = 100f,
                minFocalLengthMm = 6.3f,
                isLogicalMultiCamera = true,
                physicalCameraIds = setOf("2", "3", "4", "5")
            ),
            CameraHardwareLensEntry(
                cameraId = "2",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 1.0f,
                maxZoomRatio = 1.0f,
                minFocalLengthMm = 2.2f,
                isLogicalMultiCamera = false
            ),
            CameraHardwareLensEntry(
                cameraId = "3",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 1.0f,
                maxZoomRatio = 1.0f,
                minFocalLengthMm = 6.3f,
                isLogicalMultiCamera = false
            ),
            CameraHardwareLensEntry(
                cameraId = "4",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 1.0f,
                maxZoomRatio = 1.0f,
                minFocalLengthMm = 18.9f, // 3x optical telephoto
                isLogicalMultiCamera = false
            ),
            CameraHardwareLensEntry(
                cameraId = "5",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 1.0f,
                maxZoomRatio = 1.0f,
                minFocalLengthMm = 63.0f, // 10x optical periscope telephoto
                isLogicalMultiCamera = false
            )
        )
        val flagshipZoomCaps = CameraUltraWideZoomManager.resolveCapabilities(
            entries = flagshipEntries,
            targetFacing = CameraCharacteristics.LENS_FACING_BACK,
            activeCameraId = "0",
            activeCameraMinZoom = 0.6f,
            activeCameraMaxZoom = 100f
        )
        val flagshipProfile = CameraDeviceCapabilityManager.buildProfileFromSpecs(
            cameraId = "0",
            isFrontCamera = false,
            hardwareLevelInt = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3,
            sensorAspectRatio = 4f / 3f,
            optimalPhotoSize = Size(4000, 3000),
            bestVideoResolution = "3840 x 2160",
            hasFlashUnit = true,
            supportsOis = true,
            supportsEis = true,
            supportsContinuousAf = true,
            supportsZsl = true,
            supportedFocalLengthsMm = listOf(2.2f, 6.3f, 18.9f, 63.0f),
            physicalLensCount = 4,
            supportedFpsOptions = listOf(30, 60),
            zoomCapabilities = flagshipZoomCaps
        )
        assertEquals(CameraHardwareLevel.LEVEL_3, flagshipProfile.hardwareLevel)
        assertEquals(12, flagshipProfile.effectiveMegapixels)
        assertEquals("12M", flagshipProfile.activeModeBadge(isVideoMode = false))
        assertEquals("4K", flagshipProfile.activeModeBadge(isVideoMode = true))
        assertTrue(flagshipProfile.hasFlashUnit)
        val flagshipPresets = CameraUltraWideZoomManager.resolveZoomPresets(
            minZoomRatio = flagshipZoomCaps.minZoomRatio,
            maxZoomRatio = flagshipZoomCaps.maxZoomRatio,
            capabilities = flagshipZoomCaps
        )
        assertEquals(listOf(0.6f, 1.0f, 2.0f, 3.0f, 10.0f, 30.0f, 100.0f), flagshipPresets)

        // 2. Pixel-class multi-lens device (0.5x UW + 1x + 5x optical tele up to 30x)
        val pixelEntries = listOf(
            CameraHardwareLensEntry(
                cameraId = "0",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 0.5f,
                maxZoomRatio = 30f,
                minFocalLengthMm = 6.9f,
                isLogicalMultiCamera = true,
                physicalCameraIds = setOf("2", "3", "4")
            ),
            CameraHardwareLensEntry(
                cameraId = "2",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 1.0f,
                maxZoomRatio = 1.0f,
                minFocalLengthMm = 1.95f,
                isLogicalMultiCamera = false
            ),
            CameraHardwareLensEntry(
                cameraId = "3",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 1.0f,
                maxZoomRatio = 1.0f,
                minFocalLengthMm = 6.9f,
                isLogicalMultiCamera = false
            ),
            CameraHardwareLensEntry(
                cameraId = "4",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 1.0f,
                maxZoomRatio = 1.0f,
                minFocalLengthMm = 34.5f, // 5x optical telephoto
                isLogicalMultiCamera = false
            )
        )
        val pixelZoomCaps = CameraUltraWideZoomManager.resolveCapabilities(
            entries = pixelEntries,
            targetFacing = CameraCharacteristics.LENS_FACING_BACK,
            activeCameraId = "0",
            activeCameraMinZoom = 0.5f,
            activeCameraMaxZoom = 30f
        )
        val pixelPresets = CameraUltraWideZoomManager.resolveZoomPresets(
            minZoomRatio = pixelZoomCaps.minZoomRatio,
            maxZoomRatio = pixelZoomCaps.maxZoomRatio,
            capabilities = pixelZoomCaps
        )
        assertEquals(listOf(0.5f, 1.0f, 2.0f, 5.0f, 30.0f), pixelPresets)

        // 3. Budget / front single-lens device (no ultra-wide, no flash, 8MP, 1080p30)
        val budgetEntries = listOf(
            CameraHardwareLensEntry(
                cameraId = "1",
                lensFacing = CameraCharacteristics.LENS_FACING_FRONT,
                minZoomRatio = 1.0f,
                maxZoomRatio = 4.0f,
                minFocalLengthMm = 3.5f,
                isLogicalMultiCamera = false
            )
        )
        val budgetZoomCaps = CameraUltraWideZoomManager.resolveCapabilities(
            entries = budgetEntries,
            targetFacing = CameraCharacteristics.LENS_FACING_FRONT,
            activeCameraId = "1",
            activeCameraMinZoom = 1.0f,
            activeCameraMaxZoom = 4.0f
        )
        val budgetProfile = CameraDeviceCapabilityManager.buildProfileFromSpecs(
            cameraId = "1",
            isFrontCamera = true,
            hardwareLevelInt = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            sensorAspectRatio = 4f / 3f,
            optimalPhotoSize = Size(3264, 2448),
            bestVideoResolution = "1920 x 1080",
            hasFlashUnit = false,
            supportsOis = false,
            supportsEis = false,
            supportsContinuousAf = true,
            supportsZsl = false,
            supportedFocalLengthsMm = listOf(3.5f),
            physicalLensCount = 1,
            supportedFpsOptions = listOf(30),
            zoomCapabilities = budgetZoomCaps
        )
        assertEquals(CameraHardwareLevel.LIMITED, budgetProfile.hardwareLevel)
        assertEquals(8, budgetProfile.effectiveMegapixels)
        assertEquals("8M", budgetProfile.activeModeBadge(isVideoMode = false))
        assertEquals("FHD", budgetProfile.activeModeBadge(isVideoMode = true))
        assertFalse(budgetProfile.hasFlashUnit)
        assertFalse(budgetZoomCaps.supportsUltraWide)
        assertEquals(
            listOf(1.0f, 2.0f, 4.0f),
            CameraUltraWideZoomManager.resolveZoomPresets(
                minZoomRatio = budgetZoomCaps.minZoomRatio,
                maxZoomRatio = budgetZoomCaps.maxZoomRatio,
                capabilities = budgetZoomCaps
            )
        )
    }

    @Test
    fun edgeToEdgePreviewTransform_fillsFullDisplayWithoutCroppingSavedFullSensorPhoto() {
        // Modern 20:9 Portrait screen displaying native 4:3 sensor
        val portraitFull = CameraOutputFormatEngine.computeEdgeToEdgePreviewTransform(
            containerWidth = 1080f,
            containerHeight = 2400f,
            sensorLongShortRatio = 4f / 3f,
            cropMode = CameraCropMode.FULL,
            isLandscape = false
        )
        assertTrue("FULL portrait preview must fill edge-to-edge with zero black bars", portraitFull.isEdgeToEdgeFilled)
        assertEquals(0f, portraitFull.visibleViewportRect.leftPx, 1f)
        assertEquals(0f, portraitFull.visibleViewportRect.topPx, 1f)
        assertEquals(1080f, portraitFull.visibleViewportRect.widthPx, 1.5f)
        assertEquals(2400f, portraitFull.visibleViewportRect.heightPx, 1.5f)
        assertTrue("Edge-to-edge scale must expand 4:3 sensor to cover 20:9 height", portraitFull.previewScale > 1.6f)

        // Modern 20:9 Landscape screen displaying native 4:3 sensor
        val landscapeFull = CameraOutputFormatEngine.computeEdgeToEdgePreviewTransform(
            containerWidth = 2400f,
            containerHeight = 1080f,
            sensorLongShortRatio = 4f / 3f,
            cropMode = CameraCropMode.FULL,
            isLandscape = true
        )
        assertTrue("FULL landscape preview must fill edge-to-edge with zero pillarbox bars", landscapeFull.isEdgeToEdgeFilled)
        assertEquals(2400f, landscapeFull.visibleViewportRect.widthPx, 1.5f)
        assertEquals(1080f, landscapeFull.visibleViewportRect.heightPx, 1.5f)

        // Strict Output Invariant: Saved FULL photo MUST keep 100% of uncropped native 4:3 sensor pixels!
        val fullSensorPortrait = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val fullSensorLandscape = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val savedPortrait = CameraOutputFormatEngine.applyCropModeToBitmap(fullSensorPortrait, CameraCropMode.FULL, isLandscape = false)
        val savedLandscape = CameraOutputFormatEngine.applyCropModeToBitmap(fullSensorLandscape, CameraCropMode.FULL, isLandscape = true)
        assertSame("FULL portrait save must NOT crop native sensor bitmap", fullSensorPortrait, savedPortrait)
        assertSame("FULL landscape save must NOT crop native sensor bitmap", fullSensorLandscape, savedLandscape)
        assertEquals(300, savedPortrait.width)
        assertEquals(400, savedPortrait.height)
        assertEquals(400, savedLandscape.width)
        assertEquals(300, savedLandscape.height)

        // Dynamic crop modes must keep their entire crop guide 100% inside the visible screen with exact ratios
        for (mode in listOf(CameraCropMode.SEMI_CROP, CameraCropMode.RECTANGULAR_CROP, CameraCropMode.SQUARE_CROP)) {
            val pt = CameraOutputFormatEngine.computeEdgeToEdgePreviewTransform(
                containerWidth = 1080f,
                containerHeight = 2400f,
                sensorLongShortRatio = 4f / 3f,
                cropMode = mode,
                isLandscape = false
            )
            assertTrue("$mode guide must be marked cropped", pt.cropGuideRect.isCropped)
            assertTrue("$mode guide left >= 0", pt.cropGuideRect.leftPx >= -0.5f)
            assertTrue("$mode guide top >= 0", pt.cropGuideRect.topPx >= -0.5f)
            assertTrue("$mode guide right <= containerW", pt.cropGuideRect.leftPx + pt.cropGuideRect.widthPx <= 1080.5f)
            assertTrue("$mode guide bottom <= containerH", pt.cropGuideRect.topPx + pt.cropGuideRect.heightPx <= 2400.5f)

            val savedCropped = CameraOutputFormatEngine.applyCropModeToBitmap(fullSensorPortrait, mode, isLandscape = false)
            val savedRatio = savedCropped.width.toFloat() / savedCropped.height.toFloat()
            val guideRatio = pt.cropGuideRect.widthPx / pt.cropGuideRect.heightPx
            assertEquals("$mode guide ratio must match saved photo aspect ratio 1:1", savedRatio, guideRatio, 0.03f)
            if (savedCropped !== fullSensorPortrait) savedCropped.recycle()
        }

        fullSensorPortrait.recycle()
        fullSensorLandscape.recycle()
    }

    @Test
    fun mapScreenTapToUnscaledPreview_invertsEdgeToEdgeScaleAccurately() {
        val w = 1080f
        val h = 2400f
        val scale = 2.0f

        // Center tap always maps to exact center
        val (cx, cy) = CameraOutputFormatEngine.mapScreenTapToUnscaledPreview(540f, 1200f, w, h, scale)
        assertEquals(540f, cx, 0.01f)
        assertEquals(1200f, cy, 0.01f)

        // Tap 200px right and 400px down from center under 2x scale maps to +100px, +200px in unscaled PreviewView
        val (ux, uy) = CameraOutputFormatEngine.mapScreenTapToUnscaledPreview(740f, 1600f, w, h, scale)
        assertEquals(640f, ux, 0.01f)
        assertEquals(1400f, uy, 0.01f)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun portraitCameraScreen_rendersNativeHorizonLevelAndPreservesAllCtecsOverlays() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.setManualChainage("145+500")
        viewModel.saveCameraSettingsSilent(viewModel.cameraSettings.value.copy(showHorizonLevel = true))

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraScreen(
                        viewModel = viewModel,
                        onNavigateToPhotos = {}
                    )
                }
            }
        }
        advanceAndIdle(200L)

        // Live native horizon reticle is displayed when enabled
        composeTestRule.onNodeWithTag("camera_horizon_level_indicator").assertIsDisplayed()

        // Dynamic crop mode button cycles FULL -> SEMI_CROP cleanly while preserving all CTECS controls
        assertEquals(CameraCropMode.FULL, viewModel.cameraSettings.value.cropMode)
        composeTestRule.onNodeWithTag("camera_crop_mode_button").assertIsDisplayed().performClick()
        advanceAndIdle(150L)
        assertEquals(CameraCropMode.SEMI_CROP, viewModel.cameraSettings.value.cropMode)

        composeTestRule.onNodeWithTag("portrait_highway_functions_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("portrait_timestamp_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shutter_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zoom_preset_1").assertIsDisplayed()
    }
}
