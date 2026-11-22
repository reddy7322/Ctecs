package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.CameraEffect
import androidx.camera.core.ImageCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.util.Consumer
import androidx.test.core.app.ApplicationProvider
import com.example.model.CaptureSnapshot
import com.example.model.ChainageMatchResult
import com.example.model.MatchConfidence
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.overlay.RealtimeVideoOverlayManager
import com.example.ui.MainViewModel
import com.example.ui.camera.ActiveRecordingHandle
import com.example.ui.camera.RecordingSessionState
import com.example.ui.camera.VideoCameraAdapter
import com.example.ui.camera.VideoRecordingManager
import com.example.ui.camera.normalizeBitmapOrientation
import com.example.ui.camera.resolveDesiredImageCaptureMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CameraFailureFixRegressionTest {

    private lateinit var application: Application
    private lateinit var context: Context

    private class FakeHandle : ActiveRecordingHandle {
        var paused = false
        var stopped = false
        override fun pause() { paused = true }
        override fun resume() { paused = false }
        override fun stop() { stopped = true }
    }

    private class OverlayConflictVideoAdapter : VideoCameraAdapter {
        var effectAttached = false
        var videoBound = false
        var rejectEnableWhenEffectAttached = false
        var streamingGate: CompletableDeferred<Boolean>? = null
        val events = mutableListOf<String>()
        var startRecordingCount = 0

        override val isVideoCaptureEnabled: Boolean
            get() = videoBound
        override val hasCameraInfo: Boolean
            get() = true
        override val hasSupportedVideoEncoderProfiles: Boolean
            get() = true

        override fun applyQualitySelector(videoResolution: String) {
            events += "applyQualitySelector($videoResolution)"
        }

        override fun applyOverlayEffect(effect: CameraEffect?): Boolean {
            effectAttached = effect != null
            events += "applyOverlayEffect($effectAttached)"
            return effectAttached
        }

        override fun clearEffects() {
            effectAttached = false
            events += "clearEffects"
        }

        override fun enableVideoUseCases(includeImageCapture: Boolean): Boolean {
            val ok = !(rejectEnableWhenEffectAttached && effectAttached)
            if (ok) {
                videoBound = true
            }
            events += "enableVideoUseCases($includeImageCapture)->$ok"
            return ok
        }

        override fun enablePhotoOnlyUseCase() {
            videoBound = false
            events += "enablePhotoOnlyUseCase"
        }

        override suspend fun awaitCameraInitialization(timeoutMs: Long): Boolean = true

        override suspend fun awaitPreviewStreaming(timeoutMs: Long): Boolean {
            val gate = streamingGate
            return if (gate != null) gate.await() else true
        }

        override fun startRecording(
            outputFile: File,
            audioConfig: AudioConfig,
            executor: Executor,
            listener: Consumer<VideoRecordEvent>
        ): ActiveRecordingHandle {
            startRecordingCount++
            events += "startRecording"
            return FakeHandle()
        }
    }

    private fun testSnapshot(rotationDegrees: Int = 0): CaptureSnapshot = CaptureSnapshot(
        timestamp = 1763300000000L,
        location = null,
        locationName = "NH-58",
        gpsAccuracy = 4.0f,
        bearing = 0f,
        matchResult = ChainageMatchResult(
            chainage = com.example.model.Chainage(196450L),
            offsetMetres = 1.5,
            confidence = MatchConfidence.HIGH,
            detectedSide = "LHS",
            isManual = false
        ),
        projectProfile = ProjectProfile(highwayId = "NH-58"),
        overlayConfig = OverlayConfig(),
        deviceRotationDegrees = rotationDegrees,
        isVideoCapture = true
    )

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        context = application
    }

    @Test
    fun `resolveDesiredImageCaptureMode disables ZSL when video pipeline or flash is active`() {
        assertEquals(
            ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG,
            resolveDesiredImageCaptureMode(shouldEnableVideoPipeline = false, isFlashOn = false, zslSupported = true)
        )
        assertEquals(
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
            resolveDesiredImageCaptureMode(shouldEnableVideoPipeline = true, isFlashOn = false, zslSupported = true)
        )
        assertEquals(
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
            resolveDesiredImageCaptureMode(shouldEnableVideoPipeline = false, isFlashOn = true, zslSupported = true)
        )
        assertEquals(
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY,
            resolveDesiredImageCaptureMode(shouldEnableVideoPipeline = false, isFlashOn = false, zslSupported = false)
        )
    }

    @Test
    fun `normalizeBitmapOrientation produces upright dimensions without bogus 180 flips`() {
        // 1. Sensor landscape buffer (400x300) captured in Portrait with CameraX rotation 90 -> upright 300x400
        val sensorLandscape = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.RED)
        }
        val portraitFrom90 = normalizeBitmapOrientation(
            rawBitmap = sensorLandscape,
            cameraXRotationDegrees = 90,
            isLandscapeMode = false,
            deviceRotationDegrees = 0
        )
        assertTrue(portraitFrom90.height > portraitFrom90.width)

        // 2. Reverse-landscape (deviceRotationDegrees = 270) where buffer is ALREADY landscape upright (cameraXRotation = 0)
        // Must preserve pixel (0,0) top-left rather than flipping upside-down by 180 degrees.
        val uprightLandscape = normalizeBitmapOrientation(
            rawBitmap = sensorLandscape,
            cameraXRotationDegrees = 0,
            isLandscapeMode = true,
            deviceRotationDegrees = 270
        )
        assertEquals(400, uprightLandscape.width)
        assertEquals(300, uprightLandscape.height)
        assertEquals(Color.RED, uprightLandscape.getPixel(0, 0))

        // 3. Portrait buffer already upright (300x400, cameraXRotation = 0) with sensor tilt 180 or 270 -> stays Portrait upright
        val portraitAlreadyUpright = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.BLUE)
        }
        val keptPortrait = normalizeBitmapOrientation(
            rawBitmap = portraitAlreadyUpright,
            cameraXRotationDegrees = 0,
            isLandscapeMode = false,
            deviceRotationDegrees = 270
        )
        assertEquals(300, keptPortrait.width)
        assertEquals(400, keptPortrait.height)
        assertEquals(Color.BLUE, keptPortrait.getPixel(0, 0))

        // 4. Portrait UI when OEM HAL omitted rotationDegrees (400x300, cameraXRotation = 0) -> auto-rotates to 300x400
        val correctedPortrait = normalizeBitmapOrientation(
            rawBitmap = sensorLandscape,
            cameraXRotationDegrees = 0,
            isLandscapeMode = false,
            deviceRotationDegrees = 0
        )
        assertEquals(300, correctedPortrait.width)
        assertEquals(400, correctedPortrait.height)
    }

    @Test
    fun `VideoRecordingManager falls back cleanly without OverlayEffect when HAL rejects overlay stream`() = runBlocking {
        val manager = VideoRecordingManager(context, settleDelayFn = {})
        val overlayManager = RealtimeVideoOverlayManager(
            context = context,
            snapshotProvider = { testSnapshot(90) },
            rotationProvider = { 90 }
        )
        manager.overlayManager = overlayManager
        val controller = LifecycleCameraController(context)
        val adapter = OverlayConflictVideoAdapter().apply {
            rejectEnableWhenEffectAttached = true
        }

        var error: Throwable? = null
        manager.prepareAndStartRecording(
            controller = controller,
            videoResolution = "1920 x 1080",
            isFrontCamera = false,
            recordAudio = false,
            initialRotation = 90,
            onEvent = { _, _, _ -> },
            onError = { error = it },
            adapterOverride = adapter
        )

        assertNull("Recording should recover without OverlayEffect instead of failing", error)
        assertEquals(1, adapter.startRecordingCount)
        assertFalse(manager.lastOverlayEffectAttached)
        assertTrue(adapter.events.contains("clearEffects"))
        assertTrue(adapter.videoBound)
        assertEquals(RecordingSessionState.RECORDING, manager.sessionState.value)

        // Switching back to photo-only clears effects & resets state
        manager.stop()
        // Wait or simulate idle before photo switch
        manager.release()
        manager.ensureVideoPipelineConfigured(
            adapter = adapter,
            videoResolution = "",
            isFrontCamera = false,
            enableVideo = false,
            strictVideoMode = false,
            overlayManager = overlayManager
        )
        assertFalse(adapter.videoBound)

        overlayManager.release()
    }

    @Test
    fun `stopping VideoRecordingManager during PREPARING aborts startRecording cleanly`() = runBlocking {
        val manager = VideoRecordingManager(context, settleDelayFn = {})
        val controller = LifecycleCameraController(context)
        val gate = CompletableDeferred<Boolean>()
        val adapter = OverlayConflictVideoAdapter().apply {
            streamingGate = gate
        }

        val job = async {
            manager.prepareAndStartRecording(
                controller = controller,
                videoResolution = "1920 x 1080",
                isFrontCamera = false,
                recordAudio = false,
                initialRotation = 0,
                onEvent = { _, _, _ -> },
                onError = {},
                adapterOverride = adapter
            )
        }

        var spins = 0
        while (manager.sessionState.value != RecordingSessionState.PREPARING && spins < 50) {
            delay(10)
            spins++
        }
        assertEquals(RecordingSessionState.PREPARING, manager.sessionState.value)

        manager.stop()
        gate.complete(true)
        job.await()

        assertEquals(0, adapter.startRecordingCount)
        assertEquals(RecordingSessionState.IDLE, manager.sessionState.value)
        assertFalse(manager.isPreparingOrRecording())
    }

    @Test
    fun `RealtimeVideoOverlayManager recreates effect cleanly after release and MainViewModel setCapturing toggles`() {
        val overlayManager = RealtimeVideoOverlayManager(
            context = context,
            snapshotProvider = { testSnapshot(0) },
            rotationProvider = { 0 }
        )

        val firstEffect = overlayManager.getOrCreateEffect()
        assertNotNull(firstEffect)

        overlayManager.release()
        val recreatedEffect = overlayManager.getOrCreateEffect()
        assertNotNull(recreatedEffect)
        assertNotSame(firstEffect, recreatedEffect)
        overlayManager.release()

        val vm = MainViewModel(application)
        assertFalse(vm.isCapturing.value)
        vm.setCapturing(true)
        assertTrue(vm.isCapturing.value)
        vm.setCapturing(false)
        assertFalse(vm.isCapturing.value)
    }
}
