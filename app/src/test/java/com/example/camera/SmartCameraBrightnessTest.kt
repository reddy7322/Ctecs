package com.example.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.io.File
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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
class SmartCameraBrightnessTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun advanceAndIdle(ms: Long = 250L) {
        composeTestRule.mainClock.advanceTimeBy(ms)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `manual right-side drag covers full dim to max brightness with fine-grained precision and zero EV drift`() {
        val controller = AmbientBrightnessController(
            initialAutoEnabled = false,
            initialManualBrightness = 0.50f
        )

        // Fine-grained upward drag to maximum supported brightness (1.0f)
        repeat(60) {
            controller.applyManualDragDelta(+0.015f)
        }
        val maxState = controller.getState()
        assertEquals(CameraBrightnessEngine.MAX_SUPPORTED_BRIGHTNESS, maxState.effectiveDisplayBrightness, 0.0001f)
        assertEquals(100, maxState.displayPercent)
        assertEquals("Manual screen brightness must NEVER alter camera sensor EV", 0, maxState.recommendedAutoExposureOffset)

        // Fine-grained downward drag to lowest practical dim brightness (0.02f)
        repeat(100) {
            controller.applyManualDragDelta(-0.015f)
        }
        val minState = controller.getState()
        assertEquals(CameraBrightnessEngine.MIN_PRACTICAL_BRIGHTNESS, minState.effectiveDisplayBrightness, 0.0001f)
        assertEquals(0, minState.displayPercent)
        assertEquals("Manual dim brightness must NEVER underexpose camera sensor EV", 0, minState.recommendedAutoExposureOffset)
    }

    @Test
    fun `auto brightness adapts smoothly across low normal high and direct sunlight with hysteresis stability`() {
        val controller = AmbientBrightnessController(
            initialAutoEnabled = true,
            initialManualBrightness = 0.65f,
            initialHasLightSensor = true
        )

        // 1. Low light (8 lux) -> dim comfortable preview, LOW_LIGHT zone, neutral EV
        val low = controller.onAmbientLuxSample(8f)
        assertEquals(AmbientLightZone.LOW_LIGHT, low.lightZone)
        assertTrue("Low light display should dim comfortably (<0.30f), was ${low.effectiveDisplayBrightness}", low.effectiveDisplayBrightness < 0.30f)
        assertEquals(0, low.recommendedAutoExposureOffset)

        // 2. Oscillate around low-light exit boundary (42..55 lux) -> hysteresis prevents rapid zone flipping!
        val osc1 = controller.onAmbientLuxSample(45f)
        val osc2 = controller.onAmbientLuxSample(52f)
        assertEquals("Hysteresis must hold LOW_LIGHT below 60 lux exit threshold", AmbientLightZone.LOW_LIGHT, osc1.lightZone)
        assertEquals("Hysteresis must hold LOW_LIGHT below 60 lux exit threshold", AmbientLightZone.LOW_LIGHT, osc2.lightZone)

        // 3. Bright daylight -> settles into HIGH_LIGHT
        var bright = osc2
        repeat(12) { bright = controller.onAmbientLuxSample(2_500f) }
        assertEquals(AmbientLightZone.HIGH_LIGHT, bright.lightZone)
        assertTrue("High light display should be bright (>0.70f), was ${bright.effectiveDisplayBrightness}", bright.effectiveDisplayBrightness > 0.70f)

        // 4. Direct sunlight (>= 10,000 lux) -> maximum hardware brightness (1.0f) + anti-overexposure guard (-1 EV)
        var sun = bright
        repeat(20) { sun = controller.onAmbientLuxSample(25_000f) }
        assertEquals(AmbientLightZone.HIGH_LIGHT, sun.lightZone)
        assertEquals(CameraBrightnessEngine.MAX_SUPPORTED_BRIGHTNESS, sun.effectiveDisplayBrightness, 0.01f)
        assertEquals("Extreme sunlight in Auto mode applies subtle highlight protection EV guard", -1, sun.recommendedAutoExposureOffset)

        // 5. Small lux jitter in sunlight stays completely rock-steady (deadband suppression)
        val jitter = controller.onAmbientLuxSample(24_200f)
        assertEquals(sun.effectiveDisplayBrightness, jitter.effectiveDisplayBrightness, 0.0001f)
    }

    @Test
    fun `manual right-side drag during Auto Brightness smoothly hands off without jump`() {
        val controller = AmbientBrightnessController(
            initialAutoEnabled = true,
            initialManualBrightness = 0.85f,
            initialHasLightSensor = true
        )
        val lowAuto = controller.onAmbientLuxSample(6f)
        assertTrue(lowAuto.isAutoEnabled)
        assertTrue(lowAuto.effectiveDisplayBrightness < 0.25f)

        // User starts dragging slightly up -> hands off from current Auto display brightness, NOT old 0.85f!
        val handedOff = controller.applyManualDragDelta(+0.05f)
        assertFalse("Manual drag must transition cleanly out of Auto mode", handedOff.isAutoEnabled)
        assertEquals(
            "Zero jump from Auto display brightness",
            lowAuto.effectiveDisplayBrightness + 0.05f,
            handedOff.effectiveDisplayBrightness,
            0.001f
        )
        assertEquals(0, handedOff.recommendedAutoExposureOffset)
    }

    @Test
    fun `manual drag before first lux sample hands off smoothly from manual level with zero jump`() {
        val controller = AmbientBrightnessController(
            initialAutoEnabled = true,
            initialManualBrightness = 0.22f,
            initialHasLightSensor = true
        )
        // No lux sample yet -> effective is 0.22f
        val before = controller.getState()
        assertEquals(0.22f, before.effectiveDisplayBrightness, 0.001f)

        val after = controller.applyManualDragDelta(+0.03f)
        assertFalse(after.isAutoEnabled)
        assertEquals(0.25f, after.effectiveDisplayBrightness, 0.001f)
    }

    @Test
    fun `PreferencesManager persists Auto Brightness and last manual brightness level reliably`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = PreferencesManager(app)

        val updated = prefs.editCameraSettings { current ->
            current.copy(
                autoBrightnessEnabled = true,
                manualPreviewBrightness = 0.14f
            )
        }
        assertTrue(updated.autoBrightnessEnabled)
        assertEquals(0.14f, updated.manualPreviewBrightness, 0.001f)

        val stored = prefs.cameraSettingsFlow.first()
        assertTrue(stored.autoBrightnessEnabled)
        assertEquals(0.14f, stored.manualPreviewBrightness, 0.001f)

        // Toggle back OFF and persist max bright (1.0f)
        prefs.editCameraSettings { current ->
            current.copy(
                autoBrightnessEnabled = false,
                manualPreviewBrightness = 1.0f
            )
        }
        val reloaded = prefs.cameraSettingsFlow.first()
        assertFalse(reloaded.autoBrightnessEnabled)
        assertEquals(1.0f, reloaded.manualPreviewBrightness, 0.001f)
    }

    @Test
    fun `optimizeCapturedPhotoForLighting improves noisy low-light, protects high-light, and keeps clean normal-light natural`() {
        // 1. Clean normal-light bitmap -> pass-through (zero artificial distortion)
        val cleanNormal = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val luma = 128 + ((x / 16) * 4)
                cleanNormal.setPixel(x, y, Color.rgb(luma, luma, luma))
            }
        }
        val normalResult = CameraBrightnessEngine.optimizeCapturedPhotoForLighting(
            source = cleanNormal,
            ambientLux = 400f,
            ambientZone = AmbientLightZone.NORMAL_LIGHT
        )
        assertSame("Clean normal-light photo must stay untouched for natural colors", cleanNormal, normalResult)

        // 2. Noisy low-light bitmap with sharp road-edge step -> reduces luma+chroma grain + lifts shadows while keeping edge crisp
        val noisyLowLight = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        var seed = 1337
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                seed = (seed * 1103515245 + 12345) and 0x7fffffff
                val lumaNoise = ((seed % 19) - 9)
                val chromaNoise = (((seed ushr 8) % 11) - 5)
                val base = if (x < 32) 34 else 98 // crisp vertical road-edge contrast
                val r = (base + lumaNoise + chromaNoise).coerceIn(0, 255)
                val g = (base + lumaNoise).coerceIn(0, 255)
                val b = (base + lumaNoise - chromaNoise).coerceIn(0, 255)
                noisyLowLight.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        val beforeLowMetrics = CameraQualityEngine.analyzeScene(noisyLowLight)
        val enhancedLow = CameraBrightnessEngine.optimizeCapturedPhotoForLighting(
            source = noisyLowLight,
            ambientLux = 10f,
            ambientZone = AmbientLightZone.LOW_LIGHT
        )
        assertNotSame(noisyLowLight, enhancedLow)
        val afterLowMetrics = CameraQualityEngine.analyzeScene(enhancedLow)
        assertTrue(
            "Low-light optimization must lift shadow visibility (${beforeLowMetrics.meanLuminance} -> ${afterLowMetrics.meanLuminance})",
            afterLowMetrics.meanLuminance > beforeLowMetrics.meanLuminance
        )
        assertTrue(
            "Low-light optimization must reduce grain/noise (${beforeLowMetrics.estimatedNoiseLevel} -> ${afterLowMetrics.estimatedNoiseLevel})",
            afterLowMetrics.estimatedNoiseLevel < beforeLowMetrics.estimatedNoiseLevel
        )

        // Verify sharp vertical edge is preserved (not smeared/plastic)
        val leftPixel = enhancedLow.getPixel(28, 32)
        val rightPixel = enhancedLow.getPixel(36, 32)
        val edgeContrast = abs(Color.red(rightPixel) - Color.red(leftPixel))
        assertTrue("Edge contrast must remain crisp (>40), was $edgeContrast", edgeContrast > 40)

        // 3. Harsh overexposed high-light bitmap -> controls highlight washout
        val harshSun = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val v = if (y < 40) 246 else 185
                harshSun.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        val beforeHighMetrics = CameraQualityEngine.analyzeScene(harshSun)
        val protectedHigh = CameraBrightnessEngine.optimizeCapturedPhotoForLighting(
            source = harshSun,
            ambientLux = 25_000f,
            ambientZone = AmbientLightZone.HIGH_LIGHT
        )
        assertNotSame(harshSun, protectedHigh)
        val afterHighMetrics = CameraQualityEngine.analyzeScene(protectedHigh)
        assertTrue(
            "High-light optimization must compress blown highlights (${beforeHighMetrics.meanLuminance} -> ${afterHighMetrics.meanLuminance})",
            afterHighMetrics.meanLuminance < beforeHighMetrics.meanLuminance
        )

        cleanNormal.recycle()
        noisyLowLight.recycle()
        enhancedLow.recycle()
        harshSun.recycle()
        protectedHigh.recycle()
    }

    @Test
    fun `existing timestamp GPS chainage and weather overlays render intact after lighting optimization`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val rawPhoto = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        rawPhoto.eraseColor(Color.rgb(28, 30, 34))

        val optimized = CameraBrightnessEngine.optimizeCapturedPhotoForLighting(
            source = rawPhoto,
            ambientLux = 12f,
            ambientZone = AmbientLightZone.LOW_LIGHT
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

        val stamped = OverlayRenderer.renderOverlay(app, optimized, snapshot, isVideoFrame = false)
        assertEquals(rawPhoto.width, stamped.width)
        assertEquals(rawPhoto.height, stamped.height)

        // Verify EXIF timestamp, GPS, and project/chainage metadata are preserved intact after lighting optimization
        val outFile = File(app.cacheDir, "ctecs_brightness_overlay_preserve_test.jpg")
        if (outFile.exists()) outFile.delete()
        val saved = OverlayRenderer.saveBitmapWithExif(
            bitmap = stamped,
            destinationFile = outFile,
            snapshot = snapshot,
            jpegQuality = 92,
            context = app
        )
        assertTrue("Stamped & lighting-optimized photo must save cleanly", saved)
        assertTrue(outFile.exists())

        val exif = ExifInterface(outFile.absolutePath)
        assertTrue("EXIF timestamp must remain intact", !exif.getAttribute(ExifInterface.TAG_DATETIME).isNullOrBlank())
        assertEquals("N", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF))
        assertEquals("E", exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF))
        val description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION).orEmpty()
        assertTrue("EXIF project/chainage description must remain intact: $description", description.contains("125+400"))

        outFile.delete()
        rawPhoto.recycle()
        if (optimized !== rawPhoto) optimized.recycle()
        stamped.recycle()
    }

    @Test
    fun `CameraQuickSettingsDialog exposes Auto Brightness toggle inside existing Camera Settings`() {
        composeTestRule.mainClock.autoAdvance = false

        var settings by mutableStateOf(
            CameraSettings(
                autoBrightnessEnabled = false,
                manualPreviewBrightness = 0.02f
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                CameraQuickSettingsDialog(
                    cameraSettings = settings,
                    overlayConfig = OverlayConfig(),
                    isFlashOn = false,
                    ambientLightZone = AmbientLightZone.HIGH_LIGHT,
                    ambientLux = 14_500f,
                    onToggleFlash = {},
                    onUpdateCameraSettings = { settings = it },
                    onUpdateOverlayConfig = {},
                    onNavigateToFullSettings = {},
                    onDismiss = {}
                )
            }
        }
        advanceAndIdle()

        composeTestRule.onNodeWithText("Auto Brightness").assertIsDisplayed()
        composeTestRule.onNodeWithTag("camera_auto_brightness_switch").assertIsDisplayed().assertIsOff()

        // Toggle Auto Brightness ON inside existing Camera Settings dialog
        composeTestRule.onNodeWithTag("camera_auto_brightness_switch").performClick()
        advanceAndIdle()
        assertTrue("Auto Brightness toggle must update CameraSettings", settings.autoBrightnessEnabled)
        composeTestRule.onNodeWithTag("camera_auto_brightness_switch").assertIsOn()
        composeTestRule.onNodeWithText("Adapts to light (High Light • 14500 lux)").assertIsDisplayed()
    }

    @Test
    fun `CameraBrightnessIndicator reflects full manual DIM to MAX range and AUTO mode cleanly`() {
        composeTestRule.mainClock.autoAdvance = false

        var indicatorBrightness by mutableStateOf(CameraBrightnessEngine.MIN_PRACTICAL_BRIGHTNESS)
        var indicatorAuto by mutableStateOf(false)

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                CameraBrightnessIndicator(
                    visible = true,
                    brightness = indicatorBrightness,
                    isAutoBrightness = indicatorAuto,
                    lightZone = AmbientLightZone.HIGH_LIGHT,
                    onToggleAutoOrReset = { indicatorAuto = !indicatorAuto }
                )
            }
        }
        advanceAndIdle()

        // Manual Maximum Dim (0% DIM)
        composeTestRule.onNodeWithTag("camera_brightness_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("0%").assertIsDisplayed()
        composeTestRule.onNodeWithText("DIM").assertIsDisplayed()

        // Manual Maximum Bright (100% MAX)
        indicatorBrightness = CameraBrightnessEngine.MAX_SUPPORTED_BRIGHTNESS
        advanceAndIdle()
        composeTestRule.onNodeWithText("100%").assertIsDisplayed()
        composeTestRule.onNodeWithText("MAX").assertIsDisplayed()

        // Tap indicator -> toggles into AUTO mode
        composeTestRule.onNodeWithTag("camera_brightness_indicator").performClick()
        advanceAndIdle()
        assertTrue(indicatorAuto)
        composeTestRule.onNodeWithText("AUTO").assertIsDisplayed()
    }
}
