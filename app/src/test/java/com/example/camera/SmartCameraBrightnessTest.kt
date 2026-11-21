package com.example.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.model.CameraSettings
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.overlay.OverlayRenderer
import com.example.ui.camera.CameraBrightnessIndicator
import com.example.ui.camera.CameraQuickSettingsDialog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartCameraBrightnessTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun waitForComposeIdle() {
        shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `proportional hardware EV mapping covers full min to max across symmetric and asymmetric ranges`() {
        // Symmetric standard lens (-12..+12)
        assertEquals(-12, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(0.0f, -12, 12))
        assertEquals(-6, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(0.25f, -12, 12))
        assertEquals(0, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(0.5f, -12, 12))
        assertEquals(6, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(0.75f, -12, 12))
        assertEquals(12, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(1.0f, -12, 12))

        // High-precision lens (-24..+24)
        assertEquals(-24, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(0.0f, -24, 24))
        assertEquals(0, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(0.5f, -24, 24))
        assertEquals(24, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(1.0f, -24, 24))

        // Asymmetric lens (-12..+8)
        assertEquals(-12, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(0.0f, -12, 8))
        assertEquals(0, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(0.5f, -12, 8))
        assertEquals(4, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(0.75f, -12, 8))
        assertEquals(8, CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(1.0f, -12, 8))

        // Inverse mapping round-trip
        assertEquals(0.0f, CameraBrightnessEngine.mapHardwareEvIndexToNormalized(-12, -12, 8), 0.001f)
        assertEquals(0.5f, CameraBrightnessEngine.mapHardwareEvIndexToNormalized(0, -12, 8), 0.001f)
        assertEquals(1.0f, CameraBrightnessEngine.mapHardwareEvIndexToNormalized(8, -12, 8), 0.001f)
    }

    @Test
    fun `continuous finger drag accumulates slow micro-movements across full min-to-max range and retains value`() {
        val caps = CaptureExposureCapabilities(
            isExposureCompensationSupported = true,
            minEvIndex = -12,
            maxEvIndex = 12,
            evStep = 1f / 3f
        )
        val controller = CaptureExposureController(
            initialAutoExposure = true,
            initialManualLevel = CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL,
            initialCapabilities = caps
        )

        // Initial state: Auto Exposure (0.0 EV)
        val initial = controller.getState()
        assertTrue(initial.isAutoExposure)
        assertEquals(0, initial.targetEvIndex)
        assertEquals(0f, initial.targetEvStops, 0.001f)

        // 1. Slow micro-drag UP from Auto -> smooth zero-jump handoff from 0.5f neutral
        val firstMicro = controller.applyContinuousDragDelta(+0.005f)
        assertFalse("Dragging must immediately override Auto Exposure", firstMicro.isAutoExposure)
        assertEquals(0.505f, firstMicro.effectiveNormalizedLevel, 0.0001f)

        // Continue 100 tiny upward micro-movements -> smoothly reaches max bright (+12 EV / +4.0 stops)
        var prevNormalized = firstMicro.effectiveNormalizedLevel
        var prevEv = firstMicro.targetEvIndex
        repeat(100) {
            val step = controller.applyContinuousDragDelta(+0.005f)
            assertTrue("Normalized exposure must increase monotonically", step.effectiveNormalizedLevel >= prevNormalized)
            assertTrue("Hardware EV index must increase monotonically without jitter", step.targetEvIndex >= prevEv)
            prevNormalized = step.effectiveNormalizedLevel
            prevEv = step.targetEvIndex
        }
        val atMax = controller.getState()
        assertEquals(1.0f, atMax.effectiveNormalizedLevel, 0.0001f)
        assertEquals(12, atMax.targetEvIndex)
        assertEquals(4.0f, atMax.targetEvStops, 0.01f)
        assertEquals("+4.0", atMax.evFormatted)

        // 2. Smoothly drag all the way DOWN to minimum dim (-12 EV / -4.0 stops)
        repeat(200) {
            controller.applyContinuousDragDelta(-0.005f)
        }
        val atMin = controller.getState()
        assertEquals(0.0f, atMin.effectiveNormalizedLevel, 0.0001f)
        assertEquals(-12, atMin.targetEvIndex)
        assertEquals(-4.0f, atMin.targetEvStops, 0.01f)
        assertEquals("-4.0", atMin.evFormatted)

        // 3. Repeated fine adjustments and retention after stopping finger movement
        val fineUp = controller.applyContinuousDragDelta(+0.375f) // 0.375f -> -3 EV (-1.0 stops)
        assertEquals(-3, fineUp.targetEvIndex)
        assertEquals(-1.0f, fineUp.targetEvStops, 0.01f)
        val retained = controller.getState()
        assertEquals(fineUp, retained)

        // 4. Re-enabling Auto Exposure cleanly restores 0.0 EV without losing saved manual level
        val backToAuto = controller.setAutoExposure(true)
        assertTrue(backToAuto.isAutoExposure)
        assertEquals(0, backToAuto.targetEvIndex)
        assertEquals(0f, backToAuto.targetEvStops, 0.001f)
        assertEquals(0.375f, backToAuto.manualNormalizedLevel, 0.001f)
    }

    @Test
    fun `smooth anti-jump EV ramp prevents harsh flashes during fast swipes and active video recording`() {
        // 1. Active video recording: rapid swipe from -12 to +12 ramps smoothly by <=2 steps per frame
        var currentVideoEv = -12
        var videoTicks = 0
        while (currentVideoEv != 12 && videoTicks < 50) {
            val next = CameraBrightnessEngine.computeNextSmoothEvIndex(
                currentAppliedEv = currentVideoEv,
                targetEvIndex = 12,
                isVideoRecording = true,
                totalEvSpan = 24
            )
            val step = abs(next - currentVideoEv)
            assertTrue("Video exposure step must stay smooth (<=2), was $step", step in 1..2)
            currentVideoEv = next
            videoTicks++
        }
        assertEquals("Video exposure must converge cleanly to exact target EV", 12, currentVideoEv)
        assertTrue("Video ramp must span multiple frames to avoid harsh jump", videoTicks >= 12)

        // 2. Slow 1-index drag in both photo & video tracks 1:1 immediately with zero lag
        assertEquals(
            4,
            CameraBrightnessEngine.computeNextSmoothEvIndex(
                currentAppliedEv = 3,
                targetEvIndex = 4,
                isVideoRecording = true,
                totalEvSpan = 24
            )
        )
        assertEquals(
            -5,
            CameraBrightnessEngine.computeNextSmoothEvIndex(
                currentAppliedEv = -4,
                targetEvIndex = -5,
                isVideoRecording = false,
                totalEvSpan = 24
            )
        )
    }

    @Test
    fun `manual sensor ISO and shutter fallback strictly respects FPS and hardware bounds`() {
        val manualCaps = CaptureExposureCapabilities(
            isExposureCompensationSupported = false,
            minEvIndex = 0,
            maxEvIndex = 0,
            evStep = 0f,
            supportsManualSensor = true,
            isoRange = 100..6400,
            exposureTimeRangeNs = 100_000L..250_000_000L // 1/10000s to 1/4s
        )

        val dim30 = requireNotNull(CameraBrightnessEngine.computeSafeManualSensorExposure(0.0f, manualCaps, targetFps = 30))
        val mid30 = requireNotNull(CameraBrightnessEngine.computeSafeManualSensorExposure(0.5f, manualCaps, targetFps = 30))
        val max30 = requireNotNull(CameraBrightnessEngine.computeSafeManualSensorExposure(1.0f, manualCaps, targetFps = 30))
        val max60 = requireNotNull(CameraBrightnessEngine.computeSafeManualSensorExposure(1.0f, manualCaps, targetFps = 60))

        // Monotonic progression from dim -> mid -> bright
        assertTrue(dim30.iso <= mid30.iso && mid30.iso <= max30.iso)
        assertTrue(dim30.exposureTimeNs < mid30.exposureTimeNs && mid30.exposureTimeNs < max30.exposureTimeNs)

        // FPS ceiling strictly respected so video frame rate never drops
        assertTrue("30fps max shutter must not exceed 33.33ms", max30.exposureTimeNs <= 1_000_000_000L / 30L)
        assertTrue("60fps max shutter must not exceed 16.66ms", max60.exposureTimeNs <= 1_000_000_000L / 60L)
        assertTrue("ISO stays within conservative noise cap <=3200", max30.iso <= 3200)
    }

    @Test
    fun `photo capture exposure preserves manual hardware exposure, denoises boosted low-light, and applies photometric fallback`() {
        // 1. Hardware EV applied + intentional darker manual exposure (-2.5 EV) -> NEVER lifted or undone!
        val intentionalDark = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        intentionalDark.eraseColor(Color.rgb(36, 38, 42))
        val darkResult = CameraBrightnessEngine.processCapturedPhotoForCaptureExposure(
            source = intentionalDark,
            normalizedLevel = 0.15f,
            evStops = -2.5f,
            isAutoExposure = false,
            hardwareEvApplied = true
        )
        assertSame(
            "Manual darker hardware-captured photo must be preserved exactly without auto-lifting shadows",
            intentionalDark,
            darkResult
        )

        // 2. Hardware EV applied + user boosted brightness in noisy low-light -> reduces noise while keeping sharp edges
        val noisyBoosted = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        var seed = 4242
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                seed = (seed * 1103515245 + 12345) and 0x7fffffff
                val lumaNoise = (seed % 21) - 10
                val chromaNoise = ((seed ushr 8) % 13) - 6
                val base = if (x < 32) 48 else 124
                val r = (base + lumaNoise + chromaNoise).coerceIn(0, 255)
                val g = (base + lumaNoise).coerceIn(0, 255)
                val b = (base + lumaNoise - chromaNoise).coerceIn(0, 255)
                noisyBoosted.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        val beforeMetrics = CameraQualityEngine.analyzeScene(noisyBoosted)
        val denoisedBoosted = CameraBrightnessEngine.processCapturedPhotoForCaptureExposure(
            source = noisyBoosted,
            normalizedLevel = 0.88f,
            evStops = +2.8f,
            isAutoExposure = false,
            hardwareEvApplied = true
        )
        assertNotSame(noisyBoosted, denoisedBoosted)
        val afterMetrics = CameraQualityEngine.analyzeScene(denoisedBoosted)
        assertTrue(
            "Denoise must lower noise in boosted low-light (${beforeMetrics.estimatedNoiseLevel} -> ${afterMetrics.estimatedNoiseLevel})",
            afterMetrics.estimatedNoiseLevel < beforeMetrics.estimatedNoiseLevel
        )
        // Edge contrast preserved
        val edgeDiff = abs(
            Color.red(denoisedBoosted.getPixel(36, 32)) - Color.red(denoisedBoosted.getPixel(28, 32))
        )
        assertTrue("Edge contrast must remain sharp (>50), was $edgeDiff", edgeDiff > 50)

        // 3. Hardware EV NOT available (fallback) -> photometric curve darkens for -EV and brightens for +EV
        val midGrey = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        midGrey.eraseColor(Color.rgb(120, 120, 120))
        val fallbackDim = CameraBrightnessEngine.processCapturedPhotoForCaptureExposure(
            source = midGrey,
            normalizedLevel = 0.15f,
            evStops = -2.1f,
            isAutoExposure = false,
            hardwareEvApplied = false
        )
        val fallbackBright = CameraBrightnessEngine.processCapturedPhotoForCaptureExposure(
            source = midGrey,
            normalizedLevel = 0.85f,
            evStops = +2.1f,
            isAutoExposure = false,
            hardwareEvApplied = false
        )
        val fallbackAuto = CameraBrightnessEngine.processCapturedPhotoForCaptureExposure(
            source = midGrey,
            normalizedLevel = 0.5f,
            evStops = 0f,
            isAutoExposure = true,
            hardwareEvApplied = false
        )
        val dimLuma = CameraQualityEngine.analyzeScene(fallbackDim).meanLuminance
        val origLuma = CameraQualityEngine.analyzeScene(midGrey).meanLuminance
        val brightLuma = CameraQualityEngine.analyzeScene(fallbackBright).meanLuminance
        assertTrue("Fallback -EV must darken saved photo ($dimLuma < $origLuma)", dimLuma < origLuma - 20f)
        assertTrue("Fallback +EV must brighten saved photo ($brightLuma > $origLuma)", brightLuma > origLuma + 20f)
        assertSame("Auto exposure fallback must leave photo untouched", midGrey, fallbackAuto)

        intentionalDark.recycle()
        noisyBoosted.recycle()
        denoisedBoosted.recycle()
        midGrey.recycle()
        fallbackDim.recycle()
        fallbackBright.recycle()
    }

    @Test
    fun `PreferencesManager persists Auto Exposure and full manual capture exposure range reliably`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = PreferencesManager(app)

        prefs.editCameraSettings { current ->
            current.copy(
                autoBrightnessEnabled = false,
                manualPreviewBrightness = 0.0f // Minimum dim hardware capture exposure
            )
        }
        val storedMin = prefs.cameraSettingsFlow.first()
        assertFalse(storedMin.autoBrightnessEnabled)
        assertEquals(0.0f, storedMin.manualPreviewBrightness, 0.001f)

        prefs.editCameraSettings { current ->
            current.copy(
                autoBrightnessEnabled = false,
                manualPreviewBrightness = 1.0f // Maximum bright hardware capture exposure
            )
        }
        val storedMax = prefs.cameraSettingsFlow.first()
        assertFalse(storedMax.autoBrightnessEnabled)
        assertEquals(1.0f, storedMax.manualPreviewBrightness, 0.001f)

        prefs.editCameraSettings { current ->
            current.copy(autoBrightnessEnabled = true)
        }
        val storedAuto = prefs.cameraSettingsFlow.first()
        assertTrue(storedAuto.autoBrightnessEnabled)
    }

    @Test
    fun `existing timestamp GPS chainage and EXIF metadata remain 100 percent intact after capture exposure pass`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val rawPhoto = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        rawPhoto.eraseColor(Color.rgb(90, 96, 102))

        val exposureProcessed = CameraBrightnessEngine.processCapturedPhotoForCaptureExposure(
            source = rawPhoto,
            normalizedLevel = 0.78f,
            evStops = +1.7f,
            isAutoExposure = false,
            hardwareEvApplied = false
        )

        val snapshot = CaptureSnapshot(
            timestamp = 1_735_000_000_000L,
            location = GeoPoint(28.6139, 77.2090, 216.0),
            gpsAccuracy = 3.5f,
            speedMps = 12.0f,
            bearing = 90f,
            matchResult = ChainageMatchResult(
                chainage = Chainage(125_400L),
                detectedSide = "LHS",
                offsetMetres = 2.1,
                confidence = MatchConfidence.HIGH,
                source = ChainageSource.GPS
            ),
            projectProfile = ProjectProfile(
                highwayId = "NH-44 Highway Audit",
                roadSide = RoadSide.LHS,
                lane = "I/L"
            ),
            overlayConfig = OverlayConfig(
                showTimestamp = true,
                showCoordinates = true,
                showChainage = true,
                showProjectInfo = true,
                showMapInset = false
            )
        )

        val stamped = OverlayRenderer.renderOverlay(app, exposureProcessed, snapshot, isVideoFrame = false)
        assertEquals(rawPhoto.width, stamped.width)
        assertEquals(rawPhoto.height, stamped.height)

        val outFile = File(app.cacheDir, "ctecs_capture_exposure_overlay_preserve_test.jpg")
        if (outFile.exists()) outFile.delete()
        val saved = OverlayRenderer.saveBitmapWithExif(
            bitmap = stamped,
            destinationFile = outFile,
            snapshot = snapshot,
            jpegQuality = 92,
            context = app
        )
        assertTrue("Stamped photo must save cleanly", saved)
        assertTrue(outFile.exists())

        val exif = ExifInterface(outFile.absolutePath)
        assertTrue("EXIF timestamp must remain intact", !exif.getAttribute(ExifInterface.TAG_DATETIME).isNullOrBlank())
        assertEquals("N", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF))
        assertEquals("E", exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF))
        val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT).orEmpty()
        assertTrue("EXIF chainage & project comment must remain intact: $userComment", userComment.contains("125+400"))
        assertTrue("EXIF project highway ID must remain intact: $userComment", userComment.contains("NH-44 Highway Audit"))

        outFile.delete()
        rawPhoto.recycle()
        if (exposureProcessed !== rawPhoto) exposureProcessed.recycle()
        stamped.recycle()
    }

    @Test
    fun `CameraQuickSettingsDialog shows capture exposure EV and toggles cleanly back to Auto Exposure`() {
        var settings by mutableStateOf(
            CameraSettings(
                autoBrightnessEnabled = false,
                manualPreviewBrightness = 0.72f
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                CameraQuickSettingsDialog(
                    cameraSettings = settings,
                    overlayConfig = OverlayConfig(),
                    isFlashOn = false,
                    captureExposureEvStops = 1.7f,
                    onToggleFlash = {},
                    onUpdateCameraSettings = { settings = it },
                    onUpdateOverlayConfig = {},
                    onNavigateToFullSettings = {},
                    onDismiss = {}
                )
            }
        }
        waitForComposeIdle()

        composeTestRule.onNodeWithText("Auto Brightness", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Right-side capture exposure (+1.7 EV) • Tap ON for Auto",
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("camera_auto_brightness_switch").assertIsDisplayed().assertIsOff()

        // Toggle Auto Brightness ON -> returns to automatic camera exposure (0.0 EV)
        composeTestRule.onNodeWithTag("camera_auto_brightness_switch").performClick()
        waitForComposeIdle()
        assertTrue("Auto Brightness switch must update CameraSettings", settings.autoBrightnessEnabled)
        composeTestRule.onNodeWithTag("camera_auto_brightness_switch").assertIsOn()
        composeTestRule.onNodeWithText(
            "Automatic camera exposure active (0.0 EV)",
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun `CameraBrightnessIndicator shows continuous MIN EV MAX exposure levels and resets to AUTO on tap`() {
        var exposureNorm by mutableStateOf(CameraBrightnessEngine.MIN_EXPOSURE_LEVEL)
        var evStops by mutableStateOf(-4.0f)
        var isAuto by mutableStateOf(false)

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraBrightnessIndicator(
                        visible = true,
                        exposureNormalized = exposureNorm,
                        evStops = evStops,
                        isAutoExposure = isAuto,
                        onToggleAutoOrReset = {
                            isAuto = true
                            exposureNorm = CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL
                            evStops = 0f
                        }
                    )
                }
            }
        }
        waitForComposeIdle()

        // 1. Minimum Dim Hardware Exposure (-4.0 MIN)
        composeTestRule.onNodeWithTag("camera_brightness_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("-4.0", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("MIN", useUnmergedTree = true).assertIsDisplayed()

        // 2. Intermediate Continuous Manual Exposure (+1.7 EV)
        composeTestRule.runOnIdle {
            exposureNorm = 0.72f
            evStops = 1.7f
        }
        waitForComposeIdle()
        composeTestRule.onNodeWithText("+1.7", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("EV", useUnmergedTree = true).assertIsDisplayed()

        // 3. Maximum Bright Hardware Exposure (+4.0 MAX)
        composeTestRule.runOnIdle {
            exposureNorm = CameraBrightnessEngine.MAX_EXPOSURE_LEVEL
            evStops = 4.0f
        }
        waitForComposeIdle()
        composeTestRule.onNodeWithText("+4.0", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("MAX", useUnmergedTree = true).assertIsDisplayed()

        // 4. Tap indicator -> cleanly returns to Auto Exposure (0.0 AUTO)
        composeTestRule.onNodeWithTag("camera_brightness_indicator").performClick()
        waitForComposeIdle()
        assertTrue(isAuto)
        composeTestRule.onNodeWithText("0.0", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("AUTO", useUnmergedTree = true).assertIsDisplayed()
    }
}
