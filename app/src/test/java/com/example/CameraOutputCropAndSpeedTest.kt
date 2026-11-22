package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.camera.CameraOutputFormatEngine
import com.example.camera.CameraResolutionHelper
import com.example.data.PreferencesManager
import com.example.model.CameraCropMode
import com.example.model.CameraSettings
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.GeoPoint
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.overlay.OverlayRenderer
import com.example.overlay.VideoOverlayProcessor
import com.example.overlay.VideoValidationResult
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraCropModeSwitchPill
import com.example.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CameraOutputCropAndSpeedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -------------------------------------------------------------------------
    // 1. FULL Mode Zero-Crop Guarantee (Landscape & Portrait)
    // -------------------------------------------------------------------------

    @Test
    fun fullMode_appliesAbsolutelyZeroCrop_andPreservesExactCornerPixels() {
        val landscapeSrc = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.DKGRAY)
            setPixel(0, 0, Color.RED)
            setPixel(399, 0, Color.GREEN)
            setPixel(0, 299, Color.BLUE)
            setPixel(399, 299, Color.YELLOW)
        }
        val landscapeOut = CameraOutputFormatEngine.applyCropModeToBitmap(
            source = landscapeSrc,
            cropMode = CameraCropMode.FULL,
            isLandscape = true
        )
        assertSame("FULL mode must return exact source bitmap with zero copy/crop", landscapeSrc, landscapeOut)
        assertEquals(400, landscapeOut.width)
        assertEquals(300, landscapeOut.height)
        assertEquals(Color.RED, landscapeOut.getPixel(0, 0))
        assertEquals(Color.GREEN, landscapeOut.getPixel(399, 0))
        assertEquals(Color.BLUE, landscapeOut.getPixel(0, 299))
        assertEquals(Color.YELLOW, landscapeOut.getPixel(399, 299))

        val portraitSrc = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GRAY)
            setPixel(0, 0, Color.CYAN)
            setPixel(299, 399, Color.MAGENTA)
        }
        val portraitOut = CameraOutputFormatEngine.applyCropModeToBitmap(
            source = portraitSrc,
            cropMode = CameraCropMode.FULL,
            isLandscape = false
        )
        assertSame(portraitSrc, portraitOut)
        assertEquals(300, portraitOut.width)
        assertEquals(400, portraitOut.height)
        assertEquals(Color.CYAN, portraitOut.getPixel(0, 0))
        assertEquals(Color.MAGENTA, portraitOut.getPixel(299, 399))

        landscapeSrc.recycle()
        portraitSrc.recycle()
    }

    // -------------------------------------------------------------------------
    // 2. All 4 Crop Modes: Orientation Adaptation, Even Alignment & Zero Distortion
    // -------------------------------------------------------------------------

    @Test
    fun allFourCropModes_adaptCleanlyBetweenLandscapeAndPortrait_forPhotosAndVideos() {
        // 4:3 Photo sensor (Landscape 4000x3000 & Portrait 3000x4000)
        val fullLand = CameraOutputFormatEngine.computeCropRectForMode(4000, 3000, CameraCropMode.FULL, true)
        assertFalse(fullLand.isCropped)
        assertEquals(4000, fullLand.width)
        assertEquals(3000, fullLand.height)

        val semiLand = CameraOutputFormatEngine.computeCropRectForMode(4000, 3000, CameraCropMode.SEMI_CROP, true)
        assertTrue(semiLand.isCropped)
        assertEquals(0, semiLand.width % 2)
        assertEquals(0, semiLand.height % 2)
        assertEquals(4f / 3f, semiLand.aspectRatio, 0.01f)
        assertEquals(3440, semiLand.width)
        assertEquals(2580, semiLand.height)

        val rectLand = CameraOutputFormatEngine.computeCropRectForMode(4000, 3000, CameraCropMode.RECTANGULAR_CROP, true)
        assertTrue(rectLand.isCropped)
        assertEquals(16f / 9f, rectLand.aspectRatio, 0.01f)
        assertEquals(4000, rectLand.width)
        assertEquals(2250, rectLand.height)

        val squareLand = CameraOutputFormatEngine.computeCropRectForMode(4000, 3000, CameraCropMode.SQUARE_CROP, true)
        assertTrue(squareLand.isCropped)
        assertEquals(3000, squareLand.width)
        assertEquals(3000, squareLand.height)
        assertEquals(1.0f, squareLand.aspectRatio, 0.001f)

        // Portrait 3000x4000
        val rectPort = CameraOutputFormatEngine.computeCropRectForMode(3000, 4000, CameraCropMode.RECTANGULAR_CROP, false)
        assertTrue(rectPort.isCropped)
        assertEquals(9f / 16f, rectPort.aspectRatio, 0.01f)
        assertEquals(2250, rectPort.width)
        assertEquals(4000, rectPort.height)

        val squarePort = CameraOutputFormatEngine.computeCropRectForMode(3000, 4000, CameraCropMode.SQUARE_CROP, false)
        assertTrue(squarePort.isCropped)
        assertEquals(3000, squarePort.width)
        assertEquals(3000, squarePort.height)

        // 16:9 Video stream (Landscape 1920x1080 & Portrait 1080x1920)
        val vidFull = CameraOutputFormatEngine.computeCropRectForMode(1920, 1080, CameraCropMode.FULL, true)
        assertFalse(vidFull.isCropped)
        assertEquals(1920, vidFull.width)
        assertEquals(1080, vidFull.height)

        val vidRectLand = CameraOutputFormatEngine.computeCropRectForMode(1920, 1080, CameraCropMode.RECTANGULAR_CROP, true)
        assertTrue(vidRectLand.isCropped)
        assertEquals(4f / 3f, vidRectLand.aspectRatio, 0.01f)
        assertEquals(1440, vidRectLand.width)
        assertEquals(1080, vidRectLand.height)

        val vidSquarePort = CameraOutputFormatEngine.computeCropRectForMode(1080, 1920, CameraCropMode.SQUARE_CROP, false)
        assertTrue(vidSquarePort.isCropped)
        assertEquals(1080, vidSquarePort.width)
        assertEquals(1080, vidSquarePort.height)
    }

    // -------------------------------------------------------------------------
    // 3. Live Preview FIT_CENTER Viewport Matches Saved Output 1:1
    // -------------------------------------------------------------------------

    @Test
    fun previewCropGuideRect_matchesSavedOutputCropFractionIdentically_acrossOrientationsAndSensors() {
        val scenarios = listOf(
            // (screenW, screenH, sensorRatio, isLandscape, sampleW, sampleH)
            arrayOf(1080f, 2400f, 4f / 3f, false, 3000, 4000),
            arrayOf(2400f, 1080f, 4f / 3f, true, 4000, 3000),
            arrayOf(1080f, 2400f, 16f / 9f, false, 1080, 1920),
            arrayOf(2400f, 1080f, 16f / 9f, true, 1920, 1080)
        )

        for (sc in scenarios) {
            val screenW = sc[0] as Float
            val screenH = sc[1] as Float
            val sensorRatio = sc[2] as Float
            val isLandscape = sc[3] as Boolean
            val srcW = sc[4] as Int
            val srcH = sc[5] as Int

            val sensorViewport = CameraOutputFormatEngine.computePreviewViewportRect(
                containerWidth = screenW,
                containerHeight = screenH,
                sensorLongShortRatio = sensorRatio,
                isLandscape = isLandscape
            )

            for (mode in CameraCropMode.entries) {
                val guide = CameraOutputFormatEngine.computeCropGuideOverlayRect(
                    containerWidth = screenW,
                    containerHeight = screenH,
                    sensorLongShortRatio = sensorRatio,
                    cropMode = mode,
                    isLandscape = isLandscape
                )
                val outputCrop = CameraOutputFormatEngine.computeCropRectForMode(
                    sourceWidth = srcW,
                    sourceHeight = srcH,
                    cropMode = mode,
                    isLandscape = isLandscape
                )

                assertEquals("isCropped mismatch for $mode (land=$isLandscape)", outputCrop.isCropped, guide.isCropped)

                // Normalized crop fractions relative to displayed sensor viewport MUST match saved output fractions!
                val previewNormLeft = (guide.leftPx - sensorViewport.leftPx) / sensorViewport.widthPx
                val previewNormTop = (guide.topPx - sensorViewport.topPx) / sensorViewport.heightPx
                val previewNormW = guide.widthPx / sensorViewport.widthPx
                val previewNormH = guide.heightPx / sensorViewport.heightPx

                val outNormLeft = outputCrop.left.toFloat() / srcW.toFloat()
                val outNormTop = outputCrop.top.toFloat() / srcH.toFloat()
                val outNormW = outputCrop.width.toFloat() / srcW.toFloat()
                val outNormH = outputCrop.height.toFloat() / srcH.toFloat()

                assertTrue("Left mismatch for $mode", abs(previewNormLeft - outNormLeft) < 0.005f)
                assertTrue("Top mismatch for $mode", abs(previewNormTop - outNormTop) < 0.005f)
                assertTrue("Width mismatch for $mode", abs(previewNormW - outNormW) < 0.005f)
                assertTrue("Height mismatch for $mode", abs(previewNormH - outNormH) < 0.005f)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 4. 100% Timestamp, Metadata & EXIF Preservation Across All 4 Crop Modes
    // -------------------------------------------------------------------------

    @Test
    fun allFourCropModes_preserveTimestampsOverlaysAndExifMetadata_inSavedJpegFiles() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        for (isLandscape in listOf(true, false)) {
            val srcW = if (isLandscape) 800 else 600
            val srcH = if (isLandscape) 600 else 800

            for (mode in CameraCropMode.entries) {
                val raw = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.rgb(30, 50, 70))
                }
                val cropped = CameraOutputFormatEngine.applyCropModeToBitmap(raw, mode, isLandscape)

                val snapshot = CaptureSnapshot(
                    timestamp = 1735689600000L,
                    location = GeoPoint(28.6139, 77.2090, 215.0),
                    locationName = "NH-48 Gurgaon",
                    gpsAccuracy = 4.2f,
                    matchResult = ChainageMatchResult(
                        chainage = Chainage(196_450L),
                        detectedSide = "LHS"
                    ),
                    projectProfile = ProjectProfile(
                        highwayId = "NH-48",
                        packageSection = "Pkg-2",
                        roadSide = RoadSide.LHS,
                        activity = "BC"
                    ),
                    overlayConfig = OverlayConfig(
                        showTimestamp = true,
                        showCoordinates = true,
                        showChainage = true,
                        showProjectInfo = true,
                        showMapInset = false
                    ),
                    deviceRotationDegrees = if (isLandscape) 90 else 0,
                    cropMode = mode,
                    photoJpegQuality = 95,
                    preserveExif = true
                )

                val stamped = OverlayRenderer.renderOverlay(app, cropped, snapshot)
                assertEquals(cropped.width, stamped.width)
                assertEquals(cropped.height, stamped.height)

                val outFile = File(app.cacheDir, "crop_verify_${mode.name}_${isLandscape}.jpg").apply { delete() }
                val saved = OverlayRenderer.saveBitmapWithExif(
                    bitmap = stamped,
                    destinationFile = outFile,
                    snapshot = snapshot,
                    jpegQuality = snapshot.photoJpegQuality,
                    context = app,
                    preserveExif = snapshot.preserveExif
                )
                assertTrue("JPEG save must succeed for $mode (land=$isLandscape)", saved)
                assertTrue(outFile.exists() && outFile.length() > 1000L)

                val decoded = BitmapFactory.decodeFile(outFile.absolutePath)
                assertNotNull(decoded)
                assertEquals(cropped.width, decoded.width)
                assertEquals(cropped.height, decoded.height)

                val exif = ExifInterface(outFile.absolutePath)
                assertEquals(ExifInterface.ORIENTATION_NORMAL.toString(), exif.getAttribute(ExifInterface.TAG_ORIENTATION))
                assertNotNull("EXIF DateTime must be preserved in $mode", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                assertNotNull("EXIF GPS Latitude must be preserved in $mode", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
                assertNotNull("EXIF GPS Longitude must be preserved in $mode", exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
                val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT).orEmpty()
                assertTrue("EXIF UserComment must retain highway/chainage metadata in $mode", userComment.contains("NH-48"))

                decoded.recycle()
                if (stamped !== cropped && !stamped.isRecycled) stamped.recycle()
                if (cropped !== raw && !cropped.isRecycled) cropped.recycle()
                if (!raw.isRecycled) raw.recycle()
                outFile.delete()
            }
        }
    }

    // -------------------------------------------------------------------------
    // 5. Persistence & Automatic Resolution Defaults
    // -------------------------------------------------------------------------

    @Test
    fun cropMode_persistsCleanlyInPreferencesManager_andDefaultsToFull() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = PreferencesManager(app)
        prefs.resetAllSettings()

        assertEquals(CameraCropMode.FULL, prefs.cameraSettingsFlow.first().cropMode)

        for (mode in CameraCropMode.entries) {
            prefs.editCameraSettings { it.copy(cropMode = mode) }
            val reloaded = PreferencesManager(app).cameraSettingsFlow.first()
            assertEquals(mode, reloaded.cropMode)
        }

        // Verify automatic photo size adapts naturally to Portrait & Landscape
        val landPhoto = CameraResolutionHelper.resolveBestAutomaticPhotoSize(app, isFrontCamera = false, isLandscape = true)
        val portPhoto = CameraResolutionHelper.resolveBestAutomaticPhotoSize(app, isFrontCamera = false, isLandscape = false)
        assertTrue(landPhoto.width > landPhoto.height)
        assertTrue(portPhoto.height > portPhoto.width)
        assertEquals(landPhoto.width, portPhoto.height)
        assertEquals(landPhoto.height, portPhoto.width)
    }

    // -------------------------------------------------------------------------
    // 6. Tap-to-Switch Crop Mode Pill UI & Simplified Borderless Settings UI
    // -------------------------------------------------------------------------

    @Test
    fun cameraCropModeSwitchPill_cyclesAllFourModesOnTap_andResetsToFullOnLongPress() {
        composeTestRule.setContent {
            var mode by remember { mutableStateOf(CameraCropMode.FULL) }
            MaterialTheme {
                CameraCropModeSwitchPill(
                    cropMode = mode,
                    isLandscape = false,
                    onTapCycle = { mode = mode.next() },
                    onLongPressResetFull = { mode = CameraCropMode.FULL },
                    modifier = Modifier.testTag("camera_crop_mode_button")
                )
            }
        }

        composeTestRule.onNodeWithText("Full").assertIsDisplayed()

        // Tap 1: Full -> Semi
        composeTestRule.onNodeWithTag("camera_crop_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Semi").assertIsDisplayed()

        // Tap 2: Semi -> Rect
        composeTestRule.onNodeWithTag("camera_crop_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Rect").assertIsDisplayed()

        // Tap 3: Rect -> 1:1
        composeTestRule.onNodeWithTag("camera_crop_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1:1").assertIsDisplayed()

        // Tap 4: 1:1 -> Full
        composeTestRule.onNodeWithTag("camera_crop_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Full").assertIsDisplayed()

        // Tap twice -> Rect, then long press -> jumps immediately back to Full
        composeTestRule.onNodeWithTag("camera_crop_mode_button").performClick()
        composeTestRule.onNodeWithTag("camera_crop_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Rect").assertIsDisplayed()

        composeTestRule.onNodeWithTag("camera_crop_mode_button").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Full").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_hidesManualOutputSelectors_andShowsBorderlessAutoQualityNotice() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)

        composeTestRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Camera, Photo & Video Settings").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_auto_output_status").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("preview_ratio_ratio_4_3").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("photo_output_format_square").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("choose_photo_resolution_button").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("photo_quality_preset_high").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("switch_photo_size_optimization").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("video_output_format_landscape_full").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("choose_video_resolution_button").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("video_quality_preset_high").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("video_fps_30").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("switch_video_size_optimization").assertCountEquals(0)
    }

    // -------------------------------------------------------------------------
    // 7. Video Processor FULL Fast Path (Instant Zero-Reencode Finalization)
    // -------------------------------------------------------------------------

    @Test
    fun videoOverlayProcessor_fullModeWithRealtimeOverlay_finalizesDirectlyWithoutReencoding(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val inputFile = File(app.cacheDir, "raw_full_realtime.mp4").apply {
            writeBytes(ByteArray(2048) { (it % 251).toByte() })
        }
        val outputFile = File(app.cacheDir, "final_full_realtime.mp4").apply { delete() }

        val snapshot = CaptureSnapshot(
            timestamp = System.currentTimeMillis(),
            cropMode = CameraCropMode.FULL,
            isVideoCapture = true
        )

        val ok = VideoOverlayProcessor.applyOverlayToVideo(
            context = app,
            inputFile = inputFile,
            outputFile = outputFile,
            snapshotsTimeline = listOf(snapshot),
            defaultSnapshot = snapshot,
            hasRealtimeOverlay = true,
            validator = {
                VideoValidationResult(
                    isValid = true,
                    hasVideoTrack = true,
                    durationMs = 3000L,
                    rawWidth = 1920,
                    rawHeight = 1080,
                    rotationDegrees = 0,
                    frameRate = 30f
                )
            }
        )

        assertTrue("FULL mode with realtime overlay must finalize directly", ok)
        assertTrue(outputFile.exists() && outputFile.length() == 2048L)
        outputFile.delete()
        inputFile.delete()
        Unit
    }
}
