package com.example

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.camera.core.CameraEffect
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.util.Consumer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PhotoRecordDao
import com.example.model.CaptureSnapshot
import com.example.model.ChainageMatchResult
import com.example.model.MatchConfidence
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.overlay.RealtimeVideoOverlayManager
import com.example.overlay.VideoOverlayProcessor
import com.example.overlay.VideoValidationResult
import com.example.ui.MainViewModel
import com.example.ui.ToastType
import com.example.ui.camera.ActiveRecordingHandle
import com.example.ui.camera.RecordingSessionState
import com.example.ui.camera.VideoCameraAdapter
import com.example.ui.camera.VideoRecordingManager
import com.example.ui.camera.isFinalizedRecordingValid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
@Config(sdk = [36])
class LandscapeVideoRecordingFixTest {

    private lateinit var application: Application
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: PhotoRecordDao

    private class FakeRecordingHandle : ActiveRecordingHandle {
        var pauseCount = 0
        var resumeCount = 0
        var stopCount = 0
        override fun pause() { pauseCount++ }
        override fun resume() { resumeCount++ }
        override fun stop() { stopCount++ }
    }

    private class FakeVideoCameraAdapter(
        initiallyVideoEnabled: Boolean = false,
        initiallyHasCameraInfo: Boolean = true,
        var streamingReady: Boolean = true,
        var overlayAttachSucceeds: Boolean = true
    ) : VideoCameraAdapter {
        var videoEnabled: Boolean = initiallyVideoEnabled
        var cameraInfoAvailable: Boolean = initiallyHasCameraInfo

        val events = mutableListOf<String>()
        var applyQualityCount = 0
        var applyEffectCount = 0
        var enableVideoCount = 0
        var startRecordingCount = 0
        var lastStartedFile: File? = null
        var streamingGate: CompletableDeferred<Boolean>? = null
        val createdHandles = mutableListOf<FakeRecordingHandle>()

        override val isVideoCaptureEnabled: Boolean
            get() = videoEnabled

        override val hasCameraInfo: Boolean
            get() = cameraInfoAvailable

        override fun applyQualitySelector(videoResolution: String) {
            applyQualityCount++
            events += "applyQualitySelector($videoResolution)"
        }

        override fun applyOverlayEffect(effect: CameraEffect?): Boolean {
            applyEffectCount++
            events += "applyOverlayEffect(${effect != null})"
            return effect != null && overlayAttachSucceeds
        }

        override fun clearEffects() {
            events += "clearEffects"
        }

        override fun enableVideoUseCases(includeImageCapture: Boolean): Boolean {
            enableVideoCount++
            videoEnabled = true
            events += "enableVideoUseCases($includeImageCapture)"
            return true
        }

        override fun enablePhotoOnlyUseCase() {
            videoEnabled = false
            events += "enablePhotoOnlyUseCase"
        }

        override suspend fun awaitCameraInitialization(timeoutMs: Long): Boolean {
            events += "awaitCameraInitialization"
            return cameraInfoAvailable
        }

        override suspend fun awaitPreviewStreaming(timeoutMs: Long): Boolean {
            events += "awaitPreviewStreaming"
            val gate = streamingGate
            return if (gate != null) gate.await() else streamingReady
        }

        override fun startRecording(
            outputFile: File,
            audioConfig: AudioConfig,
            executor: Executor,
            listener: Consumer<VideoRecordEvent>
        ): ActiveRecordingHandle {
            startRecordingCount++
            lastStartedFile = outputFile
            events += "startRecording(${outputFile.name})"
            return FakeRecordingHandle().also { createdHandles += it }
        }
    }

    private fun testSnapshot(rotationDegrees: Int = 90): CaptureSnapshot = CaptureSnapshot(
        timestamp = 1763300000000L,
        location = null,
        locationName = "NH-44",
        gpsAccuracy = 5.0f,
        bearing = 90f,
        matchResult = ChainageMatchResult(
            chainage = com.example.model.Chainage(168400L),
            offsetMetres = 2.0,
            confidence = MatchConfidence.HIGH,
            detectedSide = "RHS",
            isManual = false
        ),
        projectProfile = ProjectProfile(highwayId = "NH-44"),
        overlayConfig = OverlayConfig(),
        deviceRotationDegrees = rotationDegrees,
        isVideoCapture = true
    )

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        context = application
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.photoRecordDao()
        File(context.cacheDir, "temp_video").deleteRecursively()
        val appMoviesDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            "HighwayCamera"
        )
        appMoviesDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        db.close()
        File(context.cacheDir, "temp_video").deleteRecursively()
    }

    @Test
    fun `first landscape recording initializes pipeline before start and never rebinds mid-recording`() = runBlocking {
        val manager = VideoRecordingManager(context, settleDelayFn = {})
        val overlayManager = RealtimeVideoOverlayManager(
            context = context,
            snapshotProvider = { testSnapshot() },
            rotationProvider = { 90 }
        )
        manager.overlayManager = overlayManager
        val controller = LifecycleCameraController(context)
        val adapter = FakeVideoCameraAdapter(
            initiallyVideoEnabled = false,
            initiallyHasCameraInfo = true,
            streamingReady = true
        )

        var startedCallbackCount = 0
        var capturedError: Throwable? = null

        manager.prepareAndStartRecording(
            controller = controller,
            videoResolution = "1920 x 1080",
            isFrontCamera = false,
            recordAudio = false,
            initialRotation = 90,
            onRecordingStarted = { startedCallbackCount++ },
            onEvent = { _, _, _ -> },
            onError = { capturedError = it },
            adapterOverride = adapter
        )

        assertNull(capturedError)
        assertEquals(1, startedCallbackCount)
        assertEquals(1, adapter.startRecordingCount)
        assertEquals(RecordingSessionState.RECORDING, manager.sessionState.value)
        assertTrue(manager.isRecordingActive())
        assertTrue(adapter.isVideoCaptureEnabled)
        assertNotNull(adapter.lastStartedFile)
        assertTrue(adapter.lastStartedFile!!.name.startsWith("RAW_"))

        // Verify strict ordering: awaitInit -> applyQuality -> applyEffect -> enableVideo -> awaitInit -> awaitStream -> startRecording
        val qualityIdx = adapter.events.indexOf("applyQualitySelector(1920 x 1080)")
        val effectIdx = adapter.events.indexOfFirst { it.startsWith("applyOverlayEffect") }
        val enableIdx = adapter.events.indexOf("enableVideoUseCases(true)")
        val streamIdx = adapter.events.indexOf("awaitPreviewStreaming")
        val startIdx = adapter.events.indexOfFirst { it.startsWith("startRecording(") }
        assertTrue(qualityIdx in 0 until effectIdx)
        assertTrue(effectIdx in 0 until enableIdx)
        assertTrue(enableIdx in 0 until streamIdx)
        assertTrue(streamIdx in 0 until startIdx)

        // Simulate Compose LaunchedEffect running when captureMode toggles PHOTO -> VIDEO: must NOT rebind!
        val reboundAfterStart = manager.ensureVideoPipelineConfigured(
            adapter = adapter,
            videoResolution = "1920 x 1080",
            isFrontCamera = false,
            enableVideo = true,
            strictVideoMode = true,
            overlayManager = overlayManager
        )
        assertFalse(reboundAfterStart)
        assertEquals(1, adapter.applyQualityCount)
        assertEquals(1, adapter.enableVideoCount)
        assertEquals(1, adapter.applyEffectCount)

        manager.release()
        overlayManager.release()
    }

    @Test
    fun `portrait to landscape prewarm configures video once and starts immediately on tap`() = runBlocking {
        val manager = VideoRecordingManager(context, settleDelayFn = {})
        val overlayManager = RealtimeVideoOverlayManager(
            context = context,
            snapshotProvider = { testSnapshot() },
            rotationProvider = { 90 }
        )
        manager.overlayManager = overlayManager
        val controller = LifecycleCameraController(context)
        val adapter = FakeVideoCameraAdapter(
            initiallyVideoEnabled = false,
            initiallyHasCameraInfo = true,
            streamingReady = true
        )

        // Pre-warm on entering Landscape (even while captureMode is still PHOTO / strictVideoMode = false)
        val prewarmed = manager.ensureVideoPipelineConfigured(
            adapter = adapter,
            videoResolution = "1920 x 1080",
            isFrontCamera = false,
            enableVideo = true,
            strictVideoMode = false,
            overlayManager = overlayManager
        )
        assertTrue(prewarmed)
        assertTrue(adapter.isVideoCaptureEnabled)
        assertEquals(1, adapter.enableVideoCount)

        // User immediately taps landscape record button
        manager.prepareAndStartRecording(
            controller = controller,
            videoResolution = "1920 x 1080",
            isFrontCamera = false,
            recordAudio = false,
            initialRotation = 90,
            onEvent = { _, _, _ -> },
            onError = { throw AssertionError("Unexpected error: $it") },
            adapterOverride = adapter
        )

        assertEquals(RecordingSessionState.RECORDING, manager.sessionState.value)
        assertEquals(1, adapter.enableVideoCount) // Zero extra rebinds
        assertEquals(1, adapter.startRecordingCount)

        manager.release()
        overlayManager.release()
    }

    @Test
    fun `double tap while preparing is ignored and never starts duplicate recordings`() = runBlocking {
        val manager = VideoRecordingManager(context, settleDelayFn = {})
        val controller = LifecycleCameraController(context)
        val gate = CompletableDeferred<Boolean>()
        val adapter = FakeVideoCameraAdapter(
            initiallyVideoEnabled = false,
            initiallyHasCameraInfo = true
        ).apply { streamingGate = gate }

        val firstAttempt = async {
            manager.prepareAndStartRecording(
                controller = controller,
                videoResolution = "1920 x 1080",
                isFrontCamera = false,
                recordAudio = false,
                initialRotation = 90,
                onEvent = { _, _, _ -> },
                onError = {},
                adapterOverride = adapter
            )
        }

        // Wait briefly until firstAttempt is inside PREPARING waiting on streamingGate
        var spin = 0
        while (manager.sessionState.value != RecordingSessionState.PREPARING && spin < 50) {
            delay(10)
            spin++
        }
        assertEquals(RecordingSessionState.PREPARING, manager.sessionState.value)

        // Second tap while PREPARING
        manager.prepareAndStartRecording(
            controller = controller,
            videoResolution = "1920 x 1080",
            isFrontCamera = false,
            recordAudio = false,
            initialRotation = 90,
            onEvent = { _, _, _ -> },
            onError = {},
            adapterOverride = adapter
        )

        gate.complete(true)
        firstAttempt.await()

        assertEquals(1, adapter.startRecordingCount)
        assertEquals(RecordingSessionState.RECORDING, manager.sessionState.value)

        manager.release()
    }

    @Test
    fun `unready camera stream aborts startRecording and surfaces error cleanly`() = runBlocking {
        val manager = VideoRecordingManager(context, settleDelayFn = {})
        val controller = LifecycleCameraController(context)
        val adapter = FakeVideoCameraAdapter(
            initiallyVideoEnabled = false,
            initiallyHasCameraInfo = true,
            streamingReady = false
        )

        var startedCount = 0
        var capturedError: Throwable? = null

        manager.prepareAndStartRecording(
            controller = controller,
            videoResolution = "1920 x 1080",
            isFrontCamera = false,
            recordAudio = false,
            initialRotation = 90,
            onRecordingStarted = { startedCount++ },
            onEvent = { _, _, _ -> },
            onError = { capturedError = it },
            adapterOverride = adapter
        )

        assertNotNull(capturedError)
        assertEquals(0, startedCount)
        assertEquals(0, adapter.startRecordingCount)
        assertEquals(RecordingSessionState.IDLE, manager.sessionState.value)
        assertFalse(manager.isPreparingOrRecording())
    }

    @Test
    fun `navigating away and returning cleanly releases and reinitializes recording session`() = runBlocking {
        val manager = VideoRecordingManager(context, settleDelayFn = {})
        val overlayManager = RealtimeVideoOverlayManager(
            context = context,
            snapshotProvider = { testSnapshot() },
            rotationProvider = { 90 }
        )
        manager.overlayManager = overlayManager
        val controller = LifecycleCameraController(context)
        val adapter = FakeVideoCameraAdapter(
            initiallyVideoEnabled = false,
            initiallyHasCameraInfo = true,
            streamingReady = true
        )

        manager.prepareAndStartRecording(
            controller = controller,
            videoResolution = "1920 x 1080",
            isFrontCamera = false,
            recordAudio = false,
            initialRotation = 90,
            onEvent = { _, _, _ -> },
            onError = { throw AssertionError(it) },
            adapterOverride = adapter
        )
        assertEquals(1, adapter.startRecordingCount)

        // Navigate away from CameraScreen -> release()
        manager.release()
        assertEquals(1, adapter.createdHandles.first().stopCount)
        assertEquals(RecordingSessionState.IDLE, manager.sessionState.value)
        assertNull(manager.appliedPipelineKey)

        // Re-open CameraScreen -> clean second session
        val freshAdapter = FakeVideoCameraAdapter(
            initiallyVideoEnabled = false,
            initiallyHasCameraInfo = true,
            streamingReady = true
        )
        manager.prepareAndStartRecording(
            controller = controller,
            videoResolution = "1920 x 1080",
            isFrontCamera = false,
            recordAudio = false,
            initialRotation = 90,
            onEvent = { _, _, _ -> },
            onError = { throw AssertionError(it) },
            adapterOverride = freshAdapter
        )
        assertEquals(1, freshAdapter.startRecordingCount)
        assertEquals(RecordingSessionState.RECORDING, manager.sessionState.value)

        manager.release()
        overlayManager.release()
    }

    @Test
    fun `finalized recording validator rejects fatal errors and empty or missing files`() {
        val tempDir = File(context.cacheDir, "temp_video").apply { mkdirs() }
        val missingFile = File(tempDir, "missing.mp4")
        val emptyFile = File(tempDir, "empty.mp4").apply { createNewFile() }
        val nonEmptyFile = File(tempDir, "valid.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        assertFalse(isFinalizedRecordingValid(hasError = false, errorCode = VideoRecordEvent.Finalize.ERROR_NONE, outputFile = null))
        assertFalse(isFinalizedRecordingValid(hasError = false, errorCode = VideoRecordEvent.Finalize.ERROR_NONE, outputFile = missingFile))
        assertFalse(isFinalizedRecordingValid(hasError = false, errorCode = VideoRecordEvent.Finalize.ERROR_NONE, outputFile = emptyFile))
        assertFalse(isFinalizedRecordingValid(hasError = true, errorCode = VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE, outputFile = nonEmptyFile))
        assertFalse(isFinalizedRecordingValid(hasError = true, errorCode = VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA, outputFile = nonEmptyFile))
        assertTrue(isFinalizedRecordingValid(hasError = false, errorCode = VideoRecordEvent.Finalize.ERROR_NONE, outputFile = nonEmptyFile))
        assertTrue(isFinalizedRecordingValid(hasError = true, errorCode = VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED, outputFile = nonEmptyFile))
    }

    @Test
    fun `VideoOverlayProcessor refuses missing empty or invalid video without synthesizing demo road footage`() = runBlocking {
        val tempDir = File(context.cacheDir, "temp_video").apply { mkdirs() }
        val missingInput = File(tempDir, "missing.mp4")
        val emptyInput = File(tempDir, "empty.mp4").apply { createNewFile() }
        val corruptInput = File(tempDir, "corrupt.mp4").apply { writeBytes("not-an-mp4".toByteArray()) }
        val outputFile = File(tempDir, "output.mp4").apply { writeBytes(byteArrayOf(9, 9)) }

        val snap = testSnapshot(90)

        assertFalse(VideoOverlayProcessor.applyOverlayToVideo(context, missingInput, outputFile, listOf(snap), snap))
        assertFalse(outputFile.exists())

        assertFalse(VideoOverlayProcessor.applyOverlayToVideo(context, emptyInput, outputFile, listOf(snap), snap))
        assertFalse(outputFile.exists())

        assertFalse(VideoOverlayProcessor.applyOverlayToVideo(context, corruptInput, outputFile, listOf(snap), snap))
        assertFalse(outputFile.exists())
    }

    @Test
    fun `VideoOverlayProcessor preserves genuine realtime camera bytes and orientation dimensions`() = runBlocking {
        val tempDir = File(context.cacheDir, "temp_video").apply { mkdirs() }
        val realCameraInput = File(tempDir, "camera_real.mp4")
        val expectedBytes = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6D, 0x70, 0x34, 0x32)
        realCameraInput.writeBytes(expectedBytes)
        val outputFile = File(tempDir, "exported_real.mp4")
        val snap = testSnapshot(90)

        val validationRotated = VideoValidationResult(
            isValid = true,
            hasVideoTrack = true,
            durationMs = 4200L,
            rawWidth = 1080,
            rawHeight = 1920,
            rotationDegrees = 90,
            frameRate = 30f
        )
        assertEquals(1920, validationRotated.displayWidth)
        assertEquals(1080, validationRotated.displayHeight)

        val saved = VideoOverlayProcessor.applyOverlayToVideo(
            context = context,
            inputFile = realCameraInput,
            outputFile = outputFile,
            snapshotsTimeline = listOf(snap),
            defaultSnapshot = snap,
            hasRealtimeOverlay = true,
            validator = { validationRotated }
        )

        assertTrue(saved)
        assertTrue(outputFile.exists())
        assertArrayEquals(expectedBytes, outputFile.readBytes())
    }

    @Test
    fun `MainViewModel processRecordedVideo emits error instead of fake success or DB record when video missing`() = runBlocking {
        val viewModel = MainViewModel(application)
        val missingRaw = File(context.cacheDir, "temp_video/never_recorded.mp4")

        viewModel.clearToast()
        viewModel.processRecordedVideo(
            context = context,
            rawVideoFile = missingRaw,
            forcedRotation = 90,
            hasRealtimeOverlay = true
        )

        // Pump Robolectric main looper so withContext(Dispatchers.Main) inside processRecordedVideo executes
        var waited = 0
        while ((viewModel.isExportingVideo.value || viewModel.toastMessage.value == null) && waited < 120) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
            waited++
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val toast = viewModel.toastMessage.value
        assertNotNull("Expected error toast to be emitted", toast)
        assertEquals(ToastType.ERROR, toast!!.type)
        assertFalse(viewModel.isExportingVideo.value)

        val appMoviesDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            "HighwayCamera"
        )
        val savedVideos = appMoviesDir.listFiles()?.filter { it.extension.equals("mp4", ignoreCase = true) }.orEmpty()
        assertTrue("No fake/demo video should be written to Movies/HighwayCamera", savedVideos.isEmpty())
    }
}
