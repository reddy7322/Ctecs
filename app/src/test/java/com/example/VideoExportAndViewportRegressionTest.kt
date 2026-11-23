package com.example

import com.example.camera.CameraOutputFormatEngine
import com.example.model.CameraCropMode
import com.example.video.HardwareVideoTranscoder
import com.example.video.VideoExportMethod
import com.example.video.VideoExportState
import com.example.video.VideoExportStatus
import com.example.video.VideoExportStrategySelector
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VideoExportAndViewportRegressionTest {

    @Test
    fun strategySelector_picksDirectStreamCopy_forUnmodifiedFullModeMp4WithLiveOverlay() {
        val plan = VideoExportStrategySelector.selectPlan(
            displayWidth = 1920,
            displayHeight = 1080,
            sourceRotationDegrees = 0,
            cropMode = CameraCropMode.FULL,
            hasRealtimeOverlay = true,
            requireOverlayBurnIn = false,
            rotationOverrideDegrees = null,
            isContainerMp4 = true,
            preserveAudio = true,
            sourceHasAudio = true
        )

        assertEquals(VideoExportMethod.DIRECT_STREAM_COPY, plan.method)
        assertFalse(plan.needsPixelCrop)
        assertFalse(plan.needsOverlayBurnIn)
        assertEquals(1920, plan.outputDisplayWidth)
        assertEquals(1080, plan.outputDisplayHeight)
    }

    @Test
    fun strategySelector_picksFastRemux_whenOnlyOrientationOrContainerOrAudioStripChanges() {
        val rotPlan = VideoExportStrategySelector.selectPlan(
            displayWidth = 1080,
            displayHeight = 1920,
            sourceRotationDegrees = 90,
            cropMode = CameraCropMode.FULL,
            hasRealtimeOverlay = true,
            requireOverlayBurnIn = false,
            rotationOverrideDegrees = 270,
            isContainerMp4 = true,
            preserveAudio = true,
            sourceHasAudio = true
        )
        assertEquals(VideoExportMethod.FAST_REMUX, rotPlan.method)
        assertEquals(270, rotPlan.targetRotationMetadataDegrees)

        val stripAudioPlan = VideoExportStrategySelector.selectPlan(
            displayWidth = 1920,
            displayHeight = 1080,
            sourceRotationDegrees = 0,
            cropMode = CameraCropMode.FULL,
            hasRealtimeOverlay = true,
            requireOverlayBurnIn = false,
            rotationOverrideDegrees = null,
            isContainerMp4 = true,
            preserveAudio = false,
            sourceHasAudio = true
        )
        assertEquals(VideoExportMethod.FAST_REMUX, stripAudioPlan.method)

        val containerPlan = VideoExportStrategySelector.selectPlan(
            displayWidth = 1920,
            displayHeight = 1080,
            sourceRotationDegrees = 0,
            cropMode = CameraCropMode.FULL,
            hasRealtimeOverlay = true,
            requireOverlayBurnIn = false,
            rotationOverrideDegrees = null,
            isContainerMp4 = false,
            preserveAudio = true,
            sourceHasAudio = true
        )
        assertEquals(VideoExportMethod.FAST_REMUX, containerPlan.method)
    }

    @Test
    fun strategySelector_picksHardwareGpuTranscode_whenCropOrOverlayBurnInRequired() {
        val overlayPlan = VideoExportStrategySelector.selectPlan(
            displayWidth = 1920,
            displayHeight = 1080,
            sourceRotationDegrees = 0,
            cropMode = CameraCropMode.FULL,
            hasRealtimeOverlay = false,
            requireOverlayBurnIn = true,
            rotationOverrideDegrees = null,
            isContainerMp4 = true,
            preserveAudio = true,
            sourceHasAudio = true
        )
        assertEquals(VideoExportMethod.HARDWARE_GPU_TRANSCODE, overlayPlan.method)
        assertTrue(overlayPlan.needsOverlayBurnIn)
        assertFalse(overlayPlan.needsPixelCrop)

        for (crop in listOf(CameraCropMode.SEMI_CROP, CameraCropMode.RECTANGULAR_CROP, CameraCropMode.SQUARE_CROP)) {
            val cropPlan = VideoExportStrategySelector.selectPlan(
                displayWidth = 1920,
                displayHeight = 1080,
                sourceRotationDegrees = 0,
                cropMode = crop,
                hasRealtimeOverlay = true,
                requireOverlayBurnIn = false,
                rotationOverrideDegrees = null,
                isContainerMp4 = true,
                preserveAudio = true,
                sourceHasAudio = true
            )
            assertEquals("Crop $crop must use hardware GPU transcode", VideoExportMethod.HARDWARE_GPU_TRANSCODE, cropPlan.method)
            assertTrue("Crop $crop must set needsPixelCrop=true", cropPlan.needsPixelCrop)
            assertFalse("Crop $crop with live overlay must not duplicate overlay burn-in", cropPlan.needsOverlayBurnIn)
            assertEquals("Output width must be even for AVC", 0, cropPlan.outputDisplayWidth % 2)
            assertEquals("Output height must be even for AVC", 0, cropPlan.outputDisplayHeight % 2)
        }
    }

    @Test
    fun strategySelector_etaIsNullEarlyAndRealisticOnceMeasured() {
        // Must never fake ETA before real measurable progress (<4% or <600ms)
        assertNull(VideoExportStrategySelector.computeRealisticRemainingMs(0.01f, 5_000L))
        assertNull(VideoExportStrategySelector.computeRealisticRemainingMs(0.20f, 300L))
        assertEquals(0L, VideoExportStrategySelector.computeRealisticRemainingMs(1.0f, 4_000L))

        val etaAtHalf = VideoExportStrategySelector.computeRealisticRemainingMs(0.50f, 4_000L)
        assertNotNull(etaAtHalf)
        assertEquals(4_000L, etaAtHalf)

        val state = VideoExportState(
            status = VideoExportStatus.EXPORTING,
            progress = 0.50f,
            elapsedMs = 4_000L,
            estimatedRemainingMs = etaAtHalf
        )
        assertEquals(50, state.progressPercent)
        assertTrue(state.formatElapsedAndEta().contains("0:04"))
    }

    @Test
    fun strategySelector_storageEstimateAccountsForMediaStorePublish() {
        val tenMb = 10L * 1024L * 1024L
        val directNoPublish = VideoExportStrategySelector.estimateRequiredSpaceBytes(
            sourceFileSizeBytes = tenMb,
            method = VideoExportMethod.DIRECT_STREAM_COPY,
            publishToMediaStore = false
        )
        val directWithPublish = VideoExportStrategySelector.estimateRequiredSpaceBytes(
            sourceFileSizeBytes = tenMb,
            method = VideoExportMethod.DIRECT_STREAM_COPY,
            publishToMediaStore = true
        )
        assertTrue(directWithPublish > directNoPublish)
        assertTrue(directWithPublish > tenMb * 2)
    }

    @Test
    fun directStreamCopy_preservesOriginalBytesAndHonorsCancellationWithoutModifyingSource() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "ctecs_export_test_${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(tempDir, "original_gallery.mp4")
            val payload = ByteArray(2 * 1024 * 1024 + 137) { idx -> ((idx * 31) and 0xFF).toByte() }
            source.writeBytes(payload)

            // 1. Complete non-destructive copy (gallery export)
            val dest = File(tempDir, "exported_copy.mp4")
            val progressValues = mutableListOf<Float>()
            val ok = HardwareVideoTranscoder.directStreamCopy(
                inputFile = source,
                outputFile = dest,
                allowAtomicMove = false,
                isCancelled = { false },
                onProgress = { progressValues.add(it) }
            )
            assertTrue(ok)
            assertTrue("Original gallery source must NEVER be deleted", source.exists())
            assertArrayEquals("Exported file must match source byte-for-byte", payload, dest.readBytes())
            assertTrue(progressValues.isNotEmpty())
            assertEquals(1f, progressValues.last(), 0.001f)
            // Monotonic progress
            for (i in 1 until progressValues.size) {
                assertTrue(progressValues[i] >= progressValues[i - 1])
            }

            // 2. Cancelled copy cleans up partial temp output and preserves original
            val cancelDest = File(tempDir, "cancelled_copy.mp4")
            val cancelFlag = AtomicBoolean(false)
            val cancelledOk = HardwareVideoTranscoder.directStreamCopy(
                inputFile = source,
                outputFile = cancelDest,
                allowAtomicMove = false,
                isCancelled = { cancelFlag.get() },
                onProgress = { cancelFlag.set(true) }
            )
            assertFalse(cancelledOk)
            assertTrue("Original source must remain untouched after cancel", source.exists())
            assertArrayEquals(payload, source.readBytes())
            assertFalse("Cancelled destination must not be left behind", cancelDest.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun landscapeViewportAndCropGuides_stayStrictlyInsideFitCenterFrameWithoutDistortion() {
        // Real Samsung Galaxy S21 Ultra Landscape screen dimensions from reference screenshots
        val screenW = 2400f
        val screenH = 970f

        // 1. Still Photo 4:3 Landscape FIT_CENTER viewport
        val photoVp = CameraOutputFormatEngine.computePreviewViewportRect(
            containerWidth = screenW,
            containerHeight = screenH,
            sensorLongShortRatio = 4f / 3f,
            isLandscape = true
        )
        assertEquals(screenH, photoVp.heightPx, 0.5f)
        assertEquals(screenH * (4f / 3f), photoVp.widthPx, 1f)
        assertTrue("Landscape 4:3 must be centered with equal pillarboxes", abs(photoVp.leftPx - (screenW - photoVp.widthPx) / 2f) < 1f)
        assertFalse(photoVp.isCropped)

        // 2. Video 16:9 Landscape FIT_CENTER viewport (matches VideoCapture 1:1)
        val videoVp = CameraOutputFormatEngine.computePreviewViewportRect(
            containerWidth = screenW,
            containerHeight = screenH,
            sensorLongShortRatio = 16f / 9f,
            isLandscape = true
        )
        assertEquals(screenH, videoVp.heightPx, 0.5f)
        assertEquals(screenH * (16f / 9f), videoVp.widthPx, 1f)
        assertTrue(videoVp.widthPx > photoVp.widthPx)

        // 3. All 3 non-FULL crop guides must stay strictly within the 4:3 photo viewport
        for (mode in listOf(CameraCropMode.SEMI_CROP, CameraCropMode.RECTANGULAR_CROP, CameraCropMode.SQUARE_CROP)) {
            val guide = CameraOutputFormatEngine.computeCropGuideOverlayRect(
                containerWidth = screenW,
                containerHeight = screenH,
                sensorLongShortRatio = 4f / 3f,
                cropMode = mode,
                isLandscape = true
            )
            assertTrue("Guide for $mode must be cropped", guide.isCropped)
            assertTrue(guide.leftPx >= photoVp.leftPx - 0.5f)
            assertTrue(guide.topPx >= photoVp.topPx - 0.5f)
            assertTrue(guide.leftPx + guide.widthPx <= photoVp.leftPx + photoVp.widthPx + 0.5f)
            assertTrue(guide.topPx + guide.heightPx <= photoVp.topPx + photoVp.heightPx + 0.5f)
            if (mode == CameraCropMode.SQUARE_CROP) {
                assertEquals(1.0f, guide.widthPx / guide.heightPx, 0.01f)
            }
        }
    }

    @Test
    fun videoEncoderProfileGuard_handlesNullOrUnsupportedCameraInfoSilently() {
        assertTrue(com.example.ui.camera.getValidatedHardwareVideoQualities(null).isEmpty())
        assertFalse(com.example.ui.camera.hasSupportedHardwareVideoProfiles(null))
        assertNull(com.example.ui.camera.buildSafeQualitySelector("1920 x 1080", null))
        assertFalse(com.example.ui.camera.isVideoEncoderSupportedForProfile("", 1920, 1080))
        assertFalse(com.example.ui.camera.isVideoEncoderSupportedForProfile("video/avc", 0, 1080))
    }
}
