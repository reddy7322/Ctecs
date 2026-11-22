package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import android.media.ExifInterface
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupCategory
import com.example.backup.RestoreMode
import com.example.backup.SettingsBackupManager
import com.example.camera.CameraHardwareLensEntry
import com.example.camera.CameraLensZoomCapabilities
import com.example.camera.CameraQualityEngine
import com.example.camera.CameraUltraWideZoomManager
import com.example.data.PreferencesManager
import com.example.model.CameraAutofocusMode
import com.example.model.CameraOutputFormat
import com.example.model.CameraSettings
import com.example.model.CaptureSnapshot
import com.example.model.GeoPoint
import com.example.model.OutputQualityPreset
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.VideoStabilizationMode
import com.example.overlay.OverlayRenderer
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraZoomControlBar
import com.example.ui.settings.SettingsScreen
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraUltraWideAndSettingsAuditTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    // -------------------------------------------------------------------------
    // 1. CameraUltraWideZoomManager Hardware Detection Tests
    // -------------------------------------------------------------------------

    @Test
    fun ultraWideDetection_logicalMultiCameraSupporting06x_usesSmoothLogicalZoom() {
        val entries = listOf(
            CameraHardwareLensEntry(
                cameraId = "0",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 0.5f,
                maxZoomRatio = 10.0f,
                minFocalLengthMm = 4.2f,
                isLogicalMultiCamera = true,
                physicalCameraIds = setOf("2", "3")
            )
        )

        val caps = CameraUltraWideZoomManager.resolveCapabilities(
            entries = entries,
            targetFacing = CameraCharacteristics.LENS_FACING_BACK,
            activeCameraId = "0",
            activeCameraMinZoom = 0.5f,
            activeCameraMaxZoom = 10.0f
        )

        assertTrue(caps.supportsUltraWide)
        assertEquals(0.5f, caps.widePresetRatio ?: -1f, 0.001f)
        assertFalse(caps.usesStandalonePhysicalUltraWide)
        assertNull(caps.standaloneUltraWideCameraId)

        val presets = CameraUltraWideZoomManager.resolveZoomPresets(
            minZoomRatio = caps.minZoomRatio,
            maxZoomRatio = caps.maxZoomRatio,
            capabilities = caps
        )
        assertEquals(listOf(0.5f, 1.0f, 2.0f, 5.0f, 10.0f), presets)
    }

    @Test
    fun ultraWideDetection_narrowerLogicalWideFov_usesNearestGenuineSupportedWideRatio() {
        val entries = listOf(
            CameraHardwareLensEntry(
                cameraId = "0",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 0.72f,
                maxZoomRatio = 5.0f,
                minFocalLengthMm = 4.2f,
                isLogicalMultiCamera = true
            )
        )

        val caps = CameraUltraWideZoomManager.resolveCapabilities(
            entries = entries,
            targetFacing = CameraCharacteristics.LENS_FACING_BACK,
            activeCameraId = "0",
            activeCameraMinZoom = 0.72f,
            activeCameraMaxZoom = 5.0f
        )

        assertTrue(caps.supportsUltraWide)
        // Must NOT fake 0.6x when lens only reaches 0.72x; rounds up to reachable 0.8x
        assertEquals(0.8f, caps.widePresetRatio ?: -1f, 0.001f)
        val presets = CameraUltraWideZoomManager.resolveZoomPresets(caps.minZoomRatio, caps.maxZoomRatio, caps)
        assertEquals(listOf(0.8f, 1.0f, 2.0f, 5.0f), presets)
    }

    @Test
    fun ultraWideDetection_standalonePhysicalUltraWideRearCamera_detectedCleanly() {
        val entries = listOf(
            CameraHardwareLensEntry(
                cameraId = "0",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 1.0f,
                maxZoomRatio = 8.0f,
                minFocalLengthMm = 4.5f,
                isLogicalMultiCamera = false
            ),
            CameraHardwareLensEntry(
                cameraId = "2",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 1.0f,
                maxZoomRatio = 4.0f,
                minFocalLengthMm = 2.65f, // 2.65 / 4.5 = 0.589 -> 0.6x equivalent ultra-wide
                isLogicalMultiCamera = false
            )
        )

        val caps = CameraUltraWideZoomManager.resolveCapabilities(
            entries = entries,
            targetFacing = CameraCharacteristics.LENS_FACING_BACK,
            activeCameraId = "0",
            activeCameraMinZoom = 1.0f,
            activeCameraMaxZoom = 8.0f
        )

        assertTrue(caps.supportsUltraWide)
        assertTrue(caps.usesStandalonePhysicalUltraWide)
        assertEquals("2", caps.standaloneUltraWideCameraId)
        assertEquals(0.6f, caps.widePresetRatio ?: -1f, 0.001f)
        assertEquals(0.6f, caps.effectiveMinZoomRatio, 0.001f)
    }

    @Test
    fun ultraWideDetection_singleLensDevice_neverDisplaysUnsupported06x() {
        val entries = listOf(
            CameraHardwareLensEntry(
                cameraId = "0",
                lensFacing = CameraCharacteristics.LENS_FACING_BACK,
                minZoomRatio = 1.0f,
                maxZoomRatio = 6.0f,
                minFocalLengthMm = 4.3f,
                isLogicalMultiCamera = false
            )
        )

        val caps = CameraUltraWideZoomManager.resolveCapabilities(
            entries = entries,
            targetFacing = CameraCharacteristics.LENS_FACING_BACK,
            activeCameraId = "0",
            activeCameraMinZoom = 1.0f,
            activeCameraMaxZoom = 6.0f
        )

        assertFalse(caps.supportsUltraWide)
        assertNull(caps.widePresetRatio)
        val presets = CameraUltraWideZoomManager.resolveZoomPresets(caps.minZoomRatio, caps.maxZoomRatio, caps)
        assertEquals(listOf(1.0f, 2.0f, 5.0f), presets)
    }

    // -------------------------------------------------------------------------
    // 2. CameraZoomControlBar Compose UI Tests
    // -------------------------------------------------------------------------

    @Test
    fun cameraZoomControlBar_shows06xPresetWhenSupported_andInvokesCallback() {
        var selectedZoom = 1.0f
        val caps = CameraLensZoomCapabilities(
            minZoomRatio = 0.5f,
            maxZoomRatio = 8.0f,
            widePresetRatio = 0.6f,
            isLogicalMultiCamera = true
        )

        composeTestRule.setContent {
            MaterialTheme {
                CameraZoomControlBar(
                    currentZoomRatio = selectedZoom,
                    minZoomRatio = 0.5f,
                    maxZoomRatio = 8.0f,
                    zoomCapabilities = caps,
                    onZoomChanged = { selectedZoom = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("zoom_preset_0.6").assertIsDisplayed().performClick()
        assertEquals(0.6f, selectedZoom, 0.001f)
        composeTestRule.onNodeWithTag("zoom_preset_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zoom_preset_2").assertIsDisplayed()
    }

    @Test
    fun cameraZoomControlBar_hidesUltraWidePresetWhenHardwareUnsupported() {
        val caps = CameraLensZoomCapabilities(
            minZoomRatio = 1.0f,
            maxZoomRatio = 5.0f,
            widePresetRatio = null
        )

        composeTestRule.setContent {
            MaterialTheme {
                CameraZoomControlBar(
                    currentZoomRatio = 1.0f,
                    minZoomRatio = 1.0f,
                    maxZoomRatio = 5.0f,
                    zoomCapabilities = caps,
                    onZoomChanged = {}
                )
            }
        }

        composeTestRule.onAllNodesWithTag("zoom_preset_0.6").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("zoom_preset_0.5").assertCountEquals(0)
        composeTestRule.onNodeWithTag("zoom_preset_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zoom_preset_2").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // 3. Quality Engine, Size Optimization & EXIF Toggle Runtime Verification
    // -------------------------------------------------------------------------

    @Test
    fun qualityEngine_andExifToggle_behaveAccordingToUserSettings() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        // Effective JPEG quality responds to photoQualityPreset & photoOutputSizeOptimization
        val optimizedHigh = CameraQualityEngine.resolveEffectivePhotoJpegQuality(
            CameraSettings(photoQualityPreset = OutputQualityPreset.HIGH, photoOutputSizeOptimization = true)
        )
        val unoptimizedHigh = CameraQualityEngine.resolveEffectivePhotoJpegQuality(
            CameraSettings(photoQualityPreset = OutputQualityPreset.HIGH, photoOutputSizeOptimization = false)
        )
        assertTrue(unoptimizedHigh > optimizedHigh)

        // Effective video bitrate responds to qualityPreset, FPS & videoOutputSizeOptimization
        val bitrateOpt = CameraQualityEngine.resolveEffectiveVideoBitrate(1920, 1080, 30, OutputQualityPreset.HIGH, true)
        val bitrateUnopt = CameraQualityEngine.resolveEffectiveVideoBitrate(1920, 1080, 30, OutputQualityPreset.HIGH, false)
        val bitrate60 = CameraQualityEngine.resolveEffectiveVideoBitrate(1920, 1080, 60, OutputQualityPreset.HIGH, false)
        assertTrue(bitrateUnopt > bitrateOpt)
        assertTrue(bitrate60 > bitrateUnopt)

        // Enhancement toggle disabled + size optimization disabled returns source bitmap untouched
        val src = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        val untouched = CameraQualityEngine.enhanceCapturedPhoto(
            source = src,
            qualityPreset = OutputQualityPreset.SMALLER_SIZE,
            enhancementEnabled = false,
            sizeOptimizationEnabled = false
        )
        assertTrue(untouched === src)

        // EXIF toggle: preserveExif = true writes GPS/DateTime, preserveExif = false omits them
        val snapshot = CaptureSnapshot(
            timestamp = 1735689600000L,
            location = GeoPoint(28.6139, 77.2090, 215.0),
            locationName = "New Delhi",
            projectProfile = ProjectProfile(highwayId = "NH-48")
        )
        val withExifFile = File(app.cacheDir, "test_with_exif.jpg").apply { delete() }
        val noExifFile = File(app.cacheDir, "test_no_exif.jpg").apply { delete() }

        assertTrue(OverlayRenderer.saveBitmapWithExif(src, withExifFile, snapshot, jpegQuality = 90, context = app, preserveExif = true))
        assertTrue(OverlayRenderer.saveBitmapWithExif(src, noExifFile, snapshot, jpegQuality = 90, context = app, preserveExif = false))

        val exifWith = ExifInterface(withExifFile.absolutePath)
        val exifWithout = ExifInterface(noExifFile.absolutePath)

        assertNotNull(exifWith.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertNotNull(exifWith.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exifWithout.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertNull(exifWithout.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(ExifInterface.ORIENTATION_NORMAL.toString(), exifWithout.getAttribute(ExifInterface.TAG_ORIENTATION))

        withExifFile.delete()
        noExifFile.delete()
        src.recycle()
    }

    // -------------------------------------------------------------------------
    // 4. Persistence, Sync & Backup Round-Trip Verification
    // -------------------------------------------------------------------------

    @Test
    fun allNewCameraSettings_persistSyncAndBackupRoundTripCleanly() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = PreferencesManager(app)
        prefs.resetAllSettings()

        val custom = CameraSettings(
            photoOutputFormat = CameraOutputFormat.SQUARE,
            videoOutputFormat = CameraOutputFormat.PORTRAIT_FULL,
            photoQualityPreset = OutputQualityPreset.MAXIMUM,
            videoQualityPreset = OutputQualityPreset.BALANCED,
            videoStabilizationMode = VideoStabilizationMode.ON,
            autofocusMode = CameraAutofocusMode.TAP_LOCK,
            photoOutputSizeOptimization = false,
            photoEnhancementEnabled = false,
            videoOutputSizeOptimization = true,
            videoNoiseReductionEnabled = false,
            saveOriginalAndStamped = true
        )
        prefs.updateCameraSettings(custom)

        // Fresh PreferencesManager cold-restart verification + saveOriginalImage cross-sync verification
        val reloadedPrefs = PreferencesManager(app)
        val loadedCam = reloadedPrefs.cameraSettingsFlow.first()
        val loadedOverlay = reloadedPrefs.overlayConfigFlow.first()

        assertEquals(CameraOutputFormat.SQUARE, loadedCam.photoOutputFormat)
        assertEquals(CameraOutputFormat.PORTRAIT_FULL, loadedCam.videoOutputFormat)
        assertEquals(OutputQualityPreset.MAXIMUM, loadedCam.photoQualityPreset)
        assertEquals(OutputQualityPreset.BALANCED, loadedCam.videoQualityPreset)
        assertEquals(VideoStabilizationMode.ON, loadedCam.videoStabilizationMode)
        assertEquals(CameraAutofocusMode.TAP_LOCK, loadedCam.autofocusMode)
        assertFalse(loadedCam.photoOutputSizeOptimization)
        assertFalse(loadedCam.photoEnhancementEnabled)
        assertTrue(loadedCam.videoOutputSizeOptimization)
        assertFalse(loadedCam.videoNoiseReductionEnabled)
        assertTrue(loadedCam.saveOriginalAndStamped)
        assertTrue("OverlayConfig.saveOriginalImage must sync with CameraSettings.saveOriginalAndStamped", loadedOverlay.saveOriginalImage)

        // Backup & restore round-trip
        val backupMgr = SettingsBackupManager(app, reloadedPrefs)
        val exportResult = backupMgr.exportBackup()
        assertTrue(exportResult.success)
        val backupFile = File(exportResult.localBackupFilePath!!)
        assertTrue(backupFile.exists())

        // Reset settings to defaults, then restore from backup
        reloadedPrefs.resetAllSettings()
        assertEquals(CameraOutputFormat.PORTRAIT_FULL, reloadedPrefs.cameraSettingsFlow.first().photoOutputFormat)

        val preview = backupMgr.inspectBackupFile(backupFile)
        val restoreResult = backupMgr.restoreFromPreview(
            preview = preview,
            mode = RestoreMode.COMPLETE,
            selectedCategories = BackupCategory.entries.toSet()
        )
        assertTrue(restoreResult.success)

        val restoredCam = reloadedPrefs.cameraSettingsFlow.first()
        assertEquals(CameraOutputFormat.SQUARE, restoredCam.photoOutputFormat)
        assertEquals(CameraOutputFormat.PORTRAIT_FULL, restoredCam.videoOutputFormat)
        assertEquals(OutputQualityPreset.MAXIMUM, restoredCam.photoQualityPreset)
        assertEquals(OutputQualityPreset.BALANCED, restoredCam.videoQualityPreset)
        assertEquals(VideoStabilizationMode.ON, restoredCam.videoStabilizationMode)
        assertEquals(CameraAutofocusMode.TAP_LOCK, restoredCam.autofocusMode)
        assertFalse(restoredCam.photoOutputSizeOptimization)
        assertFalse(restoredCam.photoEnhancementEnabled)
        assertTrue(restoredCam.videoOutputSizeOptimization)
        assertFalse(restoredCam.videoNoiseReductionEnabled)
        assertTrue(restoredCam.saveOriginalAndStamped)
    }

    // -------------------------------------------------------------------------
    // 5. SettingsScreen UI Audit: Simplified Photo/Video, Horizon Placement & Expand All
    // -------------------------------------------------------------------------

    @Test
    fun settingsScreen_photoVideoSeparated_horizonMovedToCamera_expandAllWorks() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        idleMain()

        composeTestRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Expand Camera, Photo & Video section
        composeTestRule.onNodeWithText("Camera, Photo & Video Settings").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify simplified automatic borderless camera settings exist and manual output/resolution/quality controls are removed
        composeTestRule.onNodeWithTag("settings_auto_output_status").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_horizon_level").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_keep_original").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_preserve_exif").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("video_stabilization_auto").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_record_audio").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("photo_output_format_square").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("photo_quality_preset_maximum").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("choose_photo_resolution_button").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("choose_video_resolution_button").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("video_output_format_landscape_full").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("video_quality_preset_balanced").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("JPEG Compression Quality").assertCountEquals(0)

        // Collapse Camera section and expand Display and Appearance — Horizon level indicator must NOT be duplicated there
        composeTestRule.onNodeWithText("Camera, Photo & Video Settings").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Display and Appearance").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("switch_horizon_level").assertCountEquals(0)
        composeTestRule.onNodeWithText("App Theme Mode").performScrollTo().assertIsDisplayed()

        // Verify Expand All expands previously missed sections (e.g. Navigation Notifications & Widgets)
        composeTestRule.onNodeWithTag("settings_expand_all_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_horizon_level").performScrollTo().assertIsDisplayed()

        // Collapse All collapses sections cleanly
        composeTestRule.onNodeWithTag("settings_collapse_all_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("switch_horizon_level").assertCountEquals(0)
    }
}
