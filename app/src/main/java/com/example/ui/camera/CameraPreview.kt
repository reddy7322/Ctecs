package com.example.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.CamcorderProfile
import android.os.Environment
import android.util.Log
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.camera.CameraBrightnessEngine
import com.example.camera.CaptureExposureController
import com.example.model.CaptureSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

import com.example.model.OverlayTemplate
import com.example.overlay.OverlayRenderer
import com.example.ui.theme.HighlightYellow
import com.example.ui.theme.PrimaryBackground
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * State representing visual autofocus status.
 */
enum class CameraFocusStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED
}

@Composable
fun CameraPreview(
    isFrontCamera: Boolean,
    isFlashOn: Boolean,
    zoomRatio: Float,
    exposureIndex: Int,
    isVideoMode: Boolean,
    isVideoRecording: Boolean = false,
    rotationDegrees: Int = 0,
    isLandscape: Boolean = false,
    photoResolution: String = "1920 x 1080",
    videoResolution: String = "1920 x 1080",
    photoAspectRatio: String = "4:3",
    videoAspectRatio: String = "16:9",
    autoBrightnessEnabled: Boolean = true,
    manualPreviewBrightness: Float = CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL,
    targetVideoFps: Int = 30,
    controller: LifecycleCameraController,
    currentSnapshotProvider: () -> CaptureSnapshot? = { null },
    overlayManager: com.example.overlay.RealtimeVideoOverlayManager? = null,
    videoManager: VideoRecordingManager? = null,
    onExposureChanged: ((Int) -> Unit)? = null,
    onZoomChanged: ((Float) -> Unit)? = null,
    onAutoBrightnessChanged: ((Boolean) -> Unit)? = null,
    onManualBrightnessChanged: ((Float) -> Unit)? = null,
    onCaptureExposureTelemetry: ((Float, Float, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestSnapshotProvider by rememberUpdatedState(currentSnapshotProvider)
    val latestOnAutoBrightnessChanged by rememberUpdatedState(onAutoBrightnessChanged)
    val latestOnManualBrightnessChanged by rememberUpdatedState(onManualBrightnessChanged)
    val latestOnCaptureExposureTelemetry by rememberUpdatedState(onCaptureExposureTelemetry)
    val latestZoomRatio by rememberUpdatedState(zoomRatio)

    // Camera Hardware Zoom Range State
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(10.0f) }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { state ->
            if (state != null) {
                minZoomRatio = state.minZoomRatio
                maxZoomRatio = state.maxZoomRatio
            }
        }
        controller.zoomState.observe(lifecycleOwner, observer)
        onDispose {
            controller.zoomState.removeObserver(observer)
        }
    }

    // Touch-to-Focus State
    var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }
    var focusStatus by remember { mutableStateOf(CameraFocusStatus.IDLE) }
    var focusPosition by remember { mutableStateOf(Offset.Zero) }
    var focusTriggerCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusTriggerCount, focusStatus) {
        if (focusStatus != CameraFocusStatus.IDLE) {
            delay(2500L)
            focusStatus = CameraFocusStatus.IDLE
        }
    }

    // Smooth Continuous Photo & Video Capture Exposure Controller (actual camera sensor exposure — NOT screen brightness)
    val exposureController = remember {
        CaptureExposureController(
            initialAutoExposure = autoBrightnessEnabled,
            initialManualLevel = manualPreviewBrightness
        )
    }
    var captureExposureState by remember { mutableStateOf(exposureController.getState()) }
    var lastAppliedHardwareEv by remember { mutableIntStateOf(0) }
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessDismissKey by remember { mutableIntStateOf(0) }
    var lastSyncedSliderEv by remember { mutableIntStateOf(exposureIndex) }

    // Publish live capture exposure telemetry silently so photo capture & UI readout stay synchronized
    LaunchedEffect(
        captureExposureState.effectiveNormalizedLevel,
        captureExposureState.targetEvStops,
        captureExposureState.capabilities.hasUsableEvRange
    ) {
        latestOnCaptureExposureTelemetry?.invoke(
            captureExposureState.effectiveNormalizedLevel,
            captureExposureState.targetEvStops,
            captureExposureState.capabilities.hasUsableEvRange
        )
    }

    // Synchronize persisted Auto Exposure / Manual Capture Exposure preferences without interrupting active drag
    LaunchedEffect(autoBrightnessEnabled) {
        if (!isDraggingBrightness) {
            captureExposureState = exposureController.setAutoExposure(autoBrightnessEnabled)
        }
    }

    LaunchedEffect(manualPreviewBrightness) {
        if (!isDraggingBrightness) {
            captureExposureState = exposureController.setManualNormalizedLevel(manualPreviewBrightness)
        }
    }

    // Cooperate cleanly with explicit EV slider changes without overriding right-side manual drags
    LaunchedEffect(exposureIndex) {
        if (exposureIndex != lastSyncedSliderEv && !isDraggingBrightness) {
            lastSyncedSliderEv = exposureIndex
            val caps = captureExposureState.capabilities
            if (caps.hasUsableEvRange) {
                val scaledTarget = ((exposureIndex.toFloat() / 4f) * maxOf(kotlin.math.abs(caps.minEvIndex), caps.maxEvIndex))
                    .toInt()
                    .coerceIn(caps.minEvIndex, caps.maxEvIndex)
                captureExposureState = exposureController.setFromHardwareEvIndex(scaledTarget)
            }
        }
    }

    LaunchedEffect(brightnessDismissKey, isDraggingBrightness) {
        if (!isDraggingBrightness && showBrightnessIndicator) {
            delay(1600L)
            showBrightnessIndicator = false
        }
    }

    fun triggerTouchToFocus(tapOffset: Offset, viewWidth: Int, viewHeight: Int) {
        focusPosition = tapOffset
        focusStatus = CameraFocusStatus.RUNNING
        focusTriggerCount++

        try {
            val factory = previewViewInstance?.meteringPointFactory
                ?: SurfaceOrientedMeteringPointFactory(
                    viewWidth.toFloat().coerceAtLeast(1f),
                    viewHeight.toFloat().coerceAtLeast(1f)
                )
            val meteringPoint = factory.createPoint(tapOffset.x, tapOffset.y)

            // When user manually adjusted capture exposure via gesture, lock AF without letting AE
            // cancel or reset the user's chosen capture brightness!
            val meteringFlags = if (captureExposureState.isAutoExposure) {
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
            } else {
                FocusMeteringAction.FLAG_AF
            }
            val focusAction = FocusMeteringAction.Builder(
                meteringPoint,
                meteringFlags
            ).setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()

            val future = controller.cameraControl?.startFocusAndMetering(focusAction)
            future?.addListener({
                try {
                    val result = future.get()
                    focusStatus = if (result.isFocusSuccessful) {
                        try {
                            // Haptic vibration feedback on successful autofocus lock
                            val vibrator = context.getSystemService(android.os.Vibrator::class.java)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(35)
                            }
                        } catch (ignored: Exception) {}
                        CameraFocusStatus.SUCCESS
                    } else {
                        CameraFocusStatus.FAILED
                    }
                } catch (e: Exception) {
                    focusStatus = CameraFocusStatus.FAILED
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.w("CameraPreview", "Failed to start focus and metering: ${e.message}", e)
            focusStatus = CameraFocusStatus.FAILED
        }
    }

    fun updateContinuousCaptureExposure(deltaDragPx: Float, dragSpanPx: Float) {
        val wasAuto = captureExposureState.isAutoExposure
        // Map continuous vertical right-side drag proportionally across full capture exposure range [0.0f..1.0f]
        // Dragging UP (negative deltaY) increases capture exposure; dragging DOWN decreases exposure.
        val deltaNormalized = -deltaDragPx / dragSpanPx.coerceAtLeast(180f)
        val updated = exposureController.applyContinuousDragDelta(deltaNormalized)
        captureExposureState = updated
        if (wasAuto && !updated.isAutoExposure) {
            latestOnAutoBrightnessChanged?.invoke(false)
        }
    }

    fun persistCurrentCaptureExposure() {
        val current = exposureController.getState()
        if (!current.isAutoExposure) {
            latestOnManualBrightnessChanged?.invoke(current.manualNormalizedLevel)
        }
    }

    fun onBrightnessIndicatorTapped() {
        val nextAuto = !captureExposureState.isAutoExposure
        captureExposureState = exposureController.setAutoExposure(nextAuto)
        latestOnAutoBrightnessChanged?.invoke(nextAuto)
        brightnessDismissKey++
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var lifecycleResumeVersion by remember { mutableIntStateOf(0) }

    // Refresh permission status and invalidate stale bindings on stop/resume
    DisposableEffect(lifecycleOwner, videoManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasCameraPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    lifecycleResumeVersion++
                }
                Lifecycle.Event.ON_STOP -> {
                    videoManager?.invalidatePipelineConfig()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Bind to Lifecycle ONLY when camera permission is granted
    DisposableEffect(lifecycleOwner, hasCameraPermission, videoManager) {
        if (hasCameraPermission) {
            try {
                controller.bindToLifecycle(lifecycleOwner)
                lifecycleResumeVersion++
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to bind camera controller to lifecycle", e)
            }
        }
        onDispose {
            videoManager?.invalidatePipelineConfig()
            try {
                controller.unbind()
            } catch (ignored: Exception) {}
        }
    }

    // Unified CameraX Pipeline Configuration (Camera Selector + QualitySelector + OverlayEffect + UseCases).
    // Running sequentially in a single LaunchedEffect eliminates mid-start rebinds between separate effects.
    // Never mutates CameraX use cases/effects while a recording session is preparing or active.
    LaunchedEffect(
        hasCameraPermission,
        isVideoMode,
        isLandscape,
        isFrontCamera,
        videoResolution,
        overlayManager,
        videoManager,
        lifecycleResumeVersion
    ) {
        if (!hasCameraPermission) return@LaunchedEffect
        if (videoManager?.isPreparingOrRecording() == true) return@LaunchedEffect

        try {
            controller.isPinchToZoomEnabled = false
            controller.isTapToFocusEnabled = false

            val adapter = LifecycleCameraControllerVideoAdapter(
                controller = controller,
                previewViewProvider = { previewViewInstance },
                context = context
            )

            // Wait for initial CameraX lifecycle bind to settle before querying cameraInfo / qualities
            adapter.awaitCameraInitialization(4000L)
            if (videoManager?.isPreparingOrRecording() == true) return@LaunchedEffect

            // Configure Camera Selector safely if facing changed
            val targetSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val desiredFront = if (controller.hasCamera(targetSelector)) {
                isFrontCamera
            } else if (controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                false
            } else if (controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                true
            } else {
                isFrontCamera
            }
            val effectiveSelector = if (desiredFront) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            if (videoManager == null || videoManager.appliedPipelineKey?.isFrontCamera != desiredFront) {
                try {
                    controller.cameraSelector = effectiveSelector
                    videoManager?.invalidatePipelineConfig()
                    adapter.awaitCameraInitialization(3000L)
                } catch (e: Exception) {
                    Log.w("CameraPreview", "Camera selector error", e)
                }
            }

            if (videoManager?.isPreparingOrRecording() == true) return@LaunchedEffect

            // In Landscape mode both Photo and Video triggers are immediately accessible without a mode toggle,
            // so pre-warm VIDEO_CAPTURE + OverlayEffect + QualitySelector right away.
            // In Portrait mode keep strict Photo/Video mode separation (enable video pipeline only when isVideoMode).
            val shouldEnableVideoPipeline = isVideoMode || isLandscape
            if (videoManager != null) {
                videoManager.ensureVideoPipelineConfigured(
                    adapter = adapter,
                    videoResolution = videoResolution,
                    isFrontCamera = desiredFront,
                    enableVideo = shouldEnableVideoPipeline,
                    strictVideoMode = isVideoMode,
                    overlayManager = overlayManager
                )
            } else if (shouldEnableVideoPipeline) {
                val safeSelector = buildSafeQualitySelector(videoResolution, controller.cameraInfo)
                if (safeSelector != null) {
                    controller.videoCaptureQualitySelector = safeSelector
                }
                val effect = overlayManager?.getOrCreateEffect()
                if (effect != null) {
                    try {
                        controller.setEffects(setOf(effect))
                    } catch (e: Exception) {
                        Log.w("CameraPreview", "Failed to attach real-time video overlay effect: ${e.message}", e)
                    }
                }
                try {
                    controller.setEnabledUseCases(
                        LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
                    )
                } catch (e: Throwable) {
                    if (isVideoMode) {
                        try {
                            controller.setEnabledUseCases(LifecycleCameraController.VIDEO_CAPTURE)
                        } catch (ignored: Exception) {}
                    }
                }
            } else {
                try { controller.clearEffects() } catch (ignored: Exception) {}
                try { controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE) } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            Log.w("CameraPreview", "Unified camera pipeline configuration notice: ${e.message}", e)
        }
    }

    // Query real hardware exposure limits (EV range, step, ISO, shutter) whenever lens/lifecycle initializes
    LaunchedEffect(hasCameraPermission, isFrontCamera, lifecycleResumeVersion) {
        if (!hasCameraPermission) return@LaunchedEffect
        val caps = CameraBrightnessEngine.queryCaptureExposureCapabilities(
            context = context,
            controller = controller,
            isFrontCamera = isFrontCamera
        )
        captureExposureState = exposureController.updateCapabilities(caps)
        // Re-query shortly after CameraX finishes async lens bind so live ExposureState is picked up
        delay(450L)
        val boundCaps = CameraBrightnessEngine.queryCaptureExposureCapabilities(
            context = context,
            controller = controller,
            isFrontCamera = isFrontCamera
        )
        captureExposureState = exposureController.updateCapabilities(boundCaps)
    }

    LaunchedEffect(isFlashOn) {
        try {
            controller.enableTorch(isFlashOn)
        } catch (ignored: Exception) {}
    }

    LaunchedEffect(zoomRatio, minZoomRatio, maxZoomRatio) {
        try {
            controller.setZoomRatio(zoomRatio.coerceIn(minZoomRatio, maxZoomRatio))
        } catch (ignored: Exception) {}
    }

    // Real-time Smooth Continuous Hardware Capture Exposure Application (preview + photo + active video recording).
    // Applies immediately during drag with frame-paced smoothing on fast jumps to eliminate flicker/hunting,
    // without touching Android screen brightness or interrupting active video recording.
    LaunchedEffect(
        hasCameraPermission,
        captureExposureState.targetEvIndex,
        captureExposureState.isAutoExposure,
        captureExposureState.effectiveNormalizedLevel,
        captureExposureState.capabilities,
        isVideoMode,
        isVideoRecording,
        targetVideoFps,
        isFrontCamera,
        lifecycleResumeVersion
    ) {
        if (!hasCameraPermission) return@LaunchedEffect
        val caps = captureExposureState.capabilities
        val targetEv = captureExposureState.targetEvIndex
        val totalSpan = (caps.maxEvIndex - caps.minEvIndex).coerceAtLeast(1)

        if (!caps.hasUsableEvRange) {
            // Hardware EV unavailable -> apply safe MANUAL_SENSOR fallback if supported
            CameraBrightnessEngine.applyHardwareCaptureExposure(
                controller = controller,
                cameraControl = controller.cameraControl,
                state = captureExposureState,
                appliedEvIndex = 0,
                isVideoMode = isVideoMode,
                isVideoRecording = isVideoRecording,
                targetVideoFps = targetVideoFps
            )
            return@LaunchedEffect
        }

        // First step applies immediately without waiting for gesture end
        var currentApplied = lastAppliedHardwareEv.coerceIn(caps.minEvIndex, caps.maxEvIndex)
        if (currentApplied == targetEv) {
            CameraBrightnessEngine.applyHardwareCaptureExposure(
                controller = controller,
                cameraControl = controller.cameraControl,
                state = captureExposureState,
                appliedEvIndex = targetEv,
                isVideoMode = isVideoMode,
                isVideoRecording = isVideoRecording,
                targetVideoFps = targetVideoFps
            )
        } else {
            while (currentApplied != targetEv) {
                currentApplied = CameraBrightnessEngine.computeNextSmoothEvIndex(
                    currentAppliedEv = currentApplied,
                    targetEvIndex = targetEv,
                    isVideoRecording = isVideoRecording,
                    totalEvSpan = totalSpan
                )
                lastAppliedHardwareEv = currentApplied
                CameraBrightnessEngine.applyHardwareCaptureExposure(
                    controller = controller,
                    cameraControl = controller.cameraControl,
                    state = captureExposureState,
                    appliedEvIndex = currentApplied,
                    isVideoMode = isVideoMode,
                    isVideoRecording = isVideoRecording,
                    targetVideoFps = targetVideoFps
                )
                if (currentApplied != targetEv) {
                    delay(16L) // ~60Hz smooth anti-jump ramp during fast swipes & video recording
                }
            }
        }
    }

    LaunchedEffect(photoResolution, isLandscape, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val photoParts = photoResolution.split("x", "×").mapNotNull { it.trim().toIntOrNull() }
            if (photoParts.size == 2) {
                val maxDim = maxOf(photoParts[0], photoParts[1])
                val minDim = minOf(photoParts[0], photoParts[1])
                // In landscape mode: width > height (e.g. 1920x1080)
                // In portrait mode: height > width (e.g. 1080x1920)
                val targetSize = if (isLandscape) {
                    android.util.Size(maxDim, minDim)
                } else {
                    android.util.Size(minDim, maxDim)
                }
                controller.imageCaptureTargetSize = androidx.camera.view.CameraController.OutputSize(targetSize)
            }
        } catch (e: Exception) {
            Log.w("CameraPreview", "Setting photo resolution error", e)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWidth = constraints.maxWidth.toFloat()
                val totalHeight = constraints.maxHeight.toFloat()
                val density = LocalDensity.current
                val rightZoneWidth = maxOf(with(density) { 92.dp.toPx() }, totalWidth * 0.28f)
                val rightZoneMargin = if (isLandscape) with(density) { 96.dp.toPx() } else 0f
                val rightZoneStart = (totalWidth - rightZoneMargin - rightZoneWidth).coerceAtLeast(0f)
                val rightZoneEnd = totalWidth - rightZoneMargin

                // 1. CameraX PreviewView Rendering
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            this.controller = controller
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            // COMPATIBLE uses TextureView which eliminates SurfaceView EGL_BAD_ATTRIBUTE emulation errors
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }.also {
                            previewViewInstance = it
                            videoManager?.attachPreviewView(it)
                        }
                    },
                    update = { pv ->
                        pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewViewInstance = pv
                        videoManager?.attachPreviewView(pv)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 2. Gesture Detector Layer for Pinch-to-Zoom, Touch-to-Focus & Existing Right-Side Brightness Drag
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isLandscape, minZoomRatio, maxZoomRatio, totalWidth, totalHeight) {
                            val touchSlop = viewConfiguration.touchSlop * 0.65f
                            val dragSpanPx = (totalHeight * 0.65f).coerceAtLeast(180f)

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startPos = down.position
                                val inRightZone = startPos.x in rightZoneStart..rightZoneEnd

                                var isDraggingDisplayBrightness = false
                                var isPinchActive = false
                                var hasMovedPastSlop = false
                                var lastY = startPos.y
                                var previousSpan = -1f
                                var activeZoom = latestZoomRatio

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedPointers = event.changes.filter { it.pressed }

                                    if (pressedPointers.size >= 2) {
                                        // Two-finger pinch to zoom!
                                        isPinchActive = true
                                        hasMovedPastSlop = true

                                        if (isDraggingDisplayBrightness) {
                                            isDraggingDisplayBrightness = false
                                            isDraggingBrightness = false
                                            persistCurrentCaptureExposure()
                                            brightnessDismissKey++
                                        }

                                        val p0 = pressedPointers[0].position
                                        val p1 = pressedPointers[1].position
                                        val currentSpan = (p0 - p1).getDistance()

                                        if (previousSpan > 0f && currentSpan > 0f) {
                                            val scaleFactor = currentSpan / previousSpan
                                            val targetZoom = (activeZoom * scaleFactor).coerceIn(minZoomRatio, maxZoomRatio)
                                            if (kotlin.math.abs(targetZoom - activeZoom) > 0.005f) {
                                                activeZoom = targetZoom
                                                onZoomChanged?.invoke(targetZoom)
                                            }
                                        }
                                        previousSpan = currentSpan
                                        event.changes.forEach { it.consume() }
                                    } else if (pressedPointers.size == 1 && !isPinchActive) {
                                        val change = pressedPointers[0]
                                        val currentPos = change.position
                                        val deltaY = currentPos.y - lastY
                                        val totalDistY = currentPos.y - startPos.y
                                        val totalDistX = currentPos.x - startPos.x

                                        if (kotlin.math.abs(totalDistY) > touchSlop || kotlin.math.abs(totalDistX) > touchSlop) {
                                            hasMovedPastSlop = true
                                        }

                                        if (!isDraggingDisplayBrightness) {
                                            if (inRightZone && kotlin.math.abs(totalDistY) > touchSlop && kotlin.math.abs(totalDistY) > kotlin.math.abs(totalDistX)) {
                                                isDraggingDisplayBrightness = true
                                                isDraggingBrightness = true
                                                showBrightnessIndicator = true
                                                change.consume()
                                                updateContinuousCaptureExposure(totalDistY, dragSpanPx)
                                                lastY = currentPos.y
                                            }
                                        } else {
                                            change.consume()
                                            updateContinuousCaptureExposure(deltaY, dragSpanPx)
                                            lastY = currentPos.y
                                        }
                                    }

                                    if (pressedPointers.isEmpty()) {
                                        // All fingers lifted
                                        if (!isDraggingDisplayBrightness && !hasMovedPastSlop && !isPinchActive) {
                                            // Tap detected! Focus at exact touched position
                                            triggerTouchToFocus(startPos, constraints.maxWidth, constraints.maxHeight)
                                        } else if (isDraggingDisplayBrightness) {
                                            isDraggingBrightness = false
                                            persistCurrentCaptureExposure()
                                            brightnessDismissKey++
                                        }
                                        break
                                    }
                                }
                            }
                        }
                )

                // 3. Animated Touch-to-Focus Indicator
                CameraFocusIndicator(
                    status = focusStatus,
                    position = focusPosition
                )

                // 4. Existing Right-Side Capture Exposure Indicator (Full Hardware EV Range + Auto Status)
                CameraBrightnessIndicator(
                    visible = showBrightnessIndicator,
                    exposureNormalized = captureExposureState.effectiveNormalizedLevel,
                    evStops = captureExposureState.targetEvStops,
                    isAutoExposure = captureExposureState.isAutoExposure,
                    onToggleAutoOrReset = { onBrightnessIndicatorTapped() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = if (isLandscape) 104.dp else 16.dp)
                )

                // 5. Active Aspect Ratio Viewfinder Framing Overlay
                val activeRatioStr = if (isVideoMode) videoAspectRatio else photoAspectRatio
                if (!activeRatioStr.equals("Full", ignoreCase = true) && constraints.maxWidth > 0 && constraints.maxHeight > 0) {
                    CameraFramingGuideOverlay(
                        aspectRatioStr = activeRatioStr,
                        isLandscape = isLandscape,
                        containerWidth = constraints.maxWidth.toFloat(),
                        containerHeight = constraints.maxHeight.toFloat()
                    )
                }
            }
        } else {
            // Elegant fallback UI when camera permission is requested or pending
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimaryBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = HighlightYellow,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        color = PrimaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Highway Timestamp Camera Pro requires camera access to capture survey photos with real-time chainage and GPS stamps.",
                        color = SecondaryText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HighlightYellow,
                            contentColor = PrimaryBackground
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant Camera Permission", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Animated Touch-to-Focus Indicator showing real-time CameraX autofocus reticle.
 * Displays viewfinder corner brackets with pulsing animation during search,
 * snaps to locked golden yellow on success, turns red on failure, and fades away.
 */
@Composable
fun CameraFocusIndicator(
    status: CameraFocusStatus,
    position: Offset,
    modifier: Modifier = Modifier
) {
    if (status == CameraFocusStatus.IDLE) return

    val transition = rememberInfiniteTransition(label = "focus_pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val entryScale by animateFloatAsState(
        targetValue = if (status == CameraFocusStatus.RUNNING) 1.0f else 1.05f,
        animationSpec = tween(200),
        label = "entry_scale"
    )

    val indicatorColor by animateColorAsState(
        targetValue = when (status) {
            CameraFocusStatus.RUNNING -> Color.White.copy(alpha = 0.92f)
            CameraFocusStatus.SUCCESS -> HighlightYellow
            CameraFocusStatus.FAILED -> Color(0xFFEF4444)
            CameraFocusStatus.IDLE -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "indicator_color"
    )

    val ringSizeDp = 64.dp
    val density = LocalDensity.current
    val ringSizePx = with(density) { ringSizeDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("camera_focus_indicator")
    ) {
        ComposeCanvas(
            modifier = Modifier
                .size(ringSizeDp)
                .graphicsLayer {
                    translationX = position.x - (ringSizePx / 2f)
                    translationY = position.y - (ringSizePx / 2f)
                    scaleX = entryScale * (if (status == CameraFocusStatus.RUNNING) pulseScale else 1.0f)
                    scaleY = entryScale * (if (status == CameraFocusStatus.RUNNING) pulseScale else 1.0f)
                }
        ) {
            val w = size.width
            val h = size.height
            val strokeWidth = 2.dp.toPx()
            val bracketLen = w * 0.28f

            // 1. Draw 4 Viewfinder Corner Brackets
            val path = Path().apply {
                // Top-Left Bracket
                moveTo(0f, bracketLen)
                lineTo(0f, 0f)
                lineTo(bracketLen, 0f)

                // Top-Right Bracket
                moveTo(w - bracketLen, 0f)
                lineTo(w, 0f)
                lineTo(w, bracketLen)

                // Bottom-Right Bracket
                moveTo(w, h - bracketLen)
                lineTo(w, h)
                lineTo(w - bracketLen, h)

                // Bottom-Left Bracket
                moveTo(bracketLen, h)
                lineTo(0f, h)
                lineTo(0f, h - bracketLen)
            }

            drawPath(
                path = path,
                color = indicatorColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 2. Inner subtle circular reticle
            drawCircle(
                color = indicatorColor.copy(alpha = if (status == CameraFocusStatus.SUCCESS) 0.85f else 0.45f),
                radius = w * 0.32f,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 3. Center Target Dot
            if (status == CameraFocusStatus.SUCCESS) {
                drawCircle(
                    color = indicatorColor,
                    radius = 3.5.dp.toPx()
                )
            } else if (status == CameraFocusStatus.RUNNING) {
                drawCircle(
                    color = indicatorColor.copy(alpha = 0.6f),
                    radius = 2.dp.toPx()
                )
            }
        }
    }
}

/**
 * Minimalist Glass Capture Exposure Indicator shown on the right side of the preview when the user
 * drags vertically in the existing right-side brightness control zone.
 * Displays continuous camera capture exposure (`min dim` to `max bright`), EV stops, and Auto status.
 */
@Composable
fun CameraBrightnessIndicator(
    visible: Boolean,
    exposureNormalized: Float,
    evStops: Float = 0f,
    isAutoExposure: Boolean = true,
    onToggleAutoOrReset: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)) + scaleIn(tween(140)),
        exit = fadeOut(tween(240)),
        modifier = modifier
    ) {
        val clamped = CameraBrightnessEngine.clampNormalizedExposure(exposureNormalized)
        val progress = clamped.coerceIn(0f, 1f)
        val evText = CameraBrightnessEngine.formatEvStops(evStops)
        val badgeText = when {
            isAutoExposure -> "AUTO"
            progress <= 0.04f -> "MIN"
            progress >= 0.96f -> "MAX"
            else -> "EV"
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xD80D111A),
            modifier = Modifier
                .width(54.dp)
                .height(176.dp)
                .then(
                    if (onToggleAutoOrReset != null) {
                        Modifier.clickable { onToggleAutoOrReset() }
                    } else Modifier
                )
                .testTag("camera_brightness_indicator")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            ) {
                // Top: Exposure / Auto Icon
                val sunTint = when {
                    isAutoExposure -> Color(0xFF38BDF8)
                    progress >= 0.65f -> HighlightYellow
                    progress <= 0.35f -> Color(0xFF94A3B8)
                    else -> Color.White
                }
                Icon(
                    imageVector = if (isAutoExposure) Icons.Default.BrightnessAuto else Icons.Default.LightMode,
                    contentDescription = if (isAutoExposure) "Auto Capture Exposure" else "Capture Exposure $evText EV",
                    tint = sunTint,
                    modifier = Modifier.size(20.dp)
                )

                // Middle: Smooth Continuous Vertical Exposure Bar (min dim -> balanced -> max bright)
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(progress.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isAutoExposure) Color(0xFF38BDF8) else HighlightYellow)
                    )
                }

                // Bottom: Continuous EV Stops & Mode Readout
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = evText,
                        color = if (isAutoExposure) Color(0xFF38BDF8) else if (kotlin.math.abs(evStops) >= 0.05f) HighlightYellow else Color.White,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = badgeText,
                        color = if (isAutoExposure) Color(0xFF38BDF8) else SecondaryText,
                        fontSize = 7.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Normalizes raw captured bitmap orientation based on CameraX image info rotation
 * and physical device sensor orientation, ensuring true landscape or portrait output.
 */
fun normalizeBitmapOrientation(
    rawBitmap: Bitmap,
    cameraXRotationDegrees: Int,
    isLandscapeMode: Boolean = false,
    deviceRotationDegrees: Int = 0
): Bitmap {
    // 1. First apply CameraX reported rotation to bring image upright relative to target rotation
    val upright = if (cameraXRotationDegrees != 0) {
        try {
            val matrix = Matrix().apply { postRotate(cameraXRotationDegrees.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } catch (e: Exception) {
            rawBitmap
        }
    } else {
        rawBitmap
    }

    // 2. Validate against desired mode (Landscape vs Portrait)
    // If device is in landscape mode (or device sensor reports 90/270 degrees),
    // output MUST be landscape (width >= height).
    val shouldBeLandscape = isLandscapeMode || deviceRotationDegrees == 90 || deviceRotationDegrees == 270

    if (shouldBeLandscape) {
        if (upright.width < upright.height) {
            // Bitmap is portrait, must be rotated to landscape (width >= height)
            val rotateBy = if (deviceRotationDegrees == 270) 270f else 90f
            return try {
                val matrix = Matrix().apply { postRotate(rotateBy) }
                Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, matrix, true)
            } catch (e: Exception) {
                upright
            }
        } else if (deviceRotationDegrees == 270 && cameraXRotationDegrees == 0) {
            // Reverse landscape 180° adjustment if sensor did not account for it
            return try {
                val matrix = Matrix().apply { postRotate(180f) }
                Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, matrix, true)
            } catch (e: Exception) {
                upright
            }
        }
    } else {
        // Portrait mode: output MUST be portrait (height >= width)
        if (upright.width > upright.height) {
            // Bitmap is landscape, must be rotated to portrait (height >= width)
            val rotateBy = if (deviceRotationDegrees == 270) 270f else 90f
            return try {
                val matrix = Matrix().apply { postRotate(rotateBy) }
                Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, matrix, true)
            } catch (e: Exception) {
                upright
            }
        } else if (deviceRotationDegrees == 180 && cameraXRotationDegrees == 0) {
            // Reverse portrait 180° adjustment if sensor did not account for it
            return try {
                val matrix = Matrix().apply { postRotate(180f) }
                Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, matrix, true)
            } catch (e: Exception) {
                upright
            }
        }
    }

    return upright
}

/**
 * Accurately center-crops bitmap to match target aspect ratio (1:1, 4:3, 16:9, Full)
 * without stretching, distortion, or quality degradation.
 */
fun applyPhotoAspectRatio(
    bitmap: Bitmap,
    aspectRatioStr: String,
    isLandscape: Boolean = false,
    screenAspectRatio: Float = 0f
): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return bitmap

    val targetRatio = when (aspectRatioStr.lowercase(java.util.Locale.US)) {
        "1:1", "square", "square 1:1", "1:1 square" -> 1.0f
        "4:3", "standard", "standard 4:3", "4:3 standard" -> if (isLandscape) 4f / 3f else 3f / 4f
        "16:9", "widescreen", "widescreen 16:9", "16:9 widescreen" -> if (isLandscape) 16f / 9f else 9f / 16f
        "full", "full screen", "device" -> {
            if (screenAspectRatio > 0f) {
                if (isLandscape) Math.max(screenAspectRatio, 1f / screenAspectRatio)
                else Math.min(screenAspectRatio, 1f / screenAspectRatio)
            } else {
                return bitmap
            }
        }
        else -> return bitmap
    }

    val currentRatio = w.toFloat() / h.toFloat()
    if (Math.abs(currentRatio - targetRatio) < 0.015f) {
        return bitmap
    }

    val targetW: Int
    val targetH: Int
    if (currentRatio > targetRatio) {
        targetH = h
        targetW = (h * targetRatio).toInt().coerceIn(1, w)
    } else {
        targetW = w
        targetH = (w / targetRatio).toInt().coerceIn(1, h)
    }

    val startX = ((w - targetW) / 2).coerceAtLeast(0)
    val startY = ((h - targetH) / 2).coerceAtLeast(0)

    return try {
        Bitmap.createBitmap(bitmap, startX, startY, targetW, targetH)
    } catch (e: Exception) {
        Log.e("CameraPreview", "Failed to crop bitmap to aspect ratio: $aspectRatioStr", e)
        bitmap
    }
}

/**
 * Captures image from LifecycleCameraController and returns a correctly rotated Bitmap
 * that strictly adheres to the physical device orientation and selected aspect ratio.
 */
fun capturePhoto(
    controller: LifecycleCameraController,
    context: Context,
    deviceRotationDegrees: Int = 0,
    isLandscape: Boolean = false,
    photoAspectRatio: String = "4:3",
    onSuccess: (Bitmap) -> Unit,
    onError: (Throwable) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(context)
    controller.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val rawBitmap = try {
                        image.toBitmap()
                    } catch (e: Exception) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }

                    if (rawBitmap == null) {
                        image.close()
                        onError(Exception("Decoded bitmap is null"))
                        return
                    }

                    val normalizedBitmap = normalizeBitmapOrientation(
                        rawBitmap = rawBitmap,
                        cameraXRotationDegrees = rotationDegrees,
                        isLandscapeMode = isLandscape,
                        deviceRotationDegrees = deviceRotationDegrees
                    )

                    val finalBitmap = applyPhotoAspectRatio(
                        bitmap = normalizedBitmap,
                        aspectRatioStr = photoAspectRatio,
                        isLandscape = isLandscape
                    )

                    image.close()
                    onSuccess(finalBitmap)
                } catch (e: Exception) {
                    image.close()
                    onError(e)
                }
            }

            override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                onError(exception)
            }
        }
    )
}

/**
 * Safely constructs a QualitySelector with ordered fallback to prevent
 * "No supported EncoderProfiles" errors on emulators and unsupported devices.
 */
fun buildSafeQualitySelector(
    videoResolution: String = "1920 x 1080",
    cameraInfo: androidx.camera.core.CameraInfo? = null
): androidx.camera.video.QualitySelector? {
    val supported = if (cameraInfo != null) {
        try {
            androidx.camera.video.QualitySelector.getSupportedQualities(cameraInfo)
        } catch (ignored: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }

    // If device/emulator camera HAL does not report any EncoderProfiles,
    // return null so CameraX keeps its internal default without forced re-evaluation
    if (supported.isEmpty()) {
        return null
    }

    val videoParts = videoResolution.split("x", "×").mapNotNull { it.trim().toIntOrNull() }
    val w = if (videoParts.size == 2) Math.max(videoParts[0], videoParts[1]) else 1920
    val preferredQuality = when {
        w >= 3840 -> androidx.camera.video.Quality.UHD
        w >= 1920 -> androidx.camera.video.Quality.FHD
        w >= 1280 -> androidx.camera.video.Quality.HD
        else -> androidx.camera.video.Quality.SD
    }

    val preferredList = when (preferredQuality) {
        androidx.camera.video.Quality.UHD -> listOf(
            androidx.camera.video.Quality.UHD,
            androidx.camera.video.Quality.FHD,
            androidx.camera.video.Quality.HD,
            androidx.camera.video.Quality.SD,
            androidx.camera.video.Quality.LOWEST
        )
        androidx.camera.video.Quality.FHD -> listOf(
            androidx.camera.video.Quality.FHD,
            androidx.camera.video.Quality.HD,
            androidx.camera.video.Quality.SD,
            androidx.camera.video.Quality.LOWEST
        )
        androidx.camera.video.Quality.HD -> listOf(
            androidx.camera.video.Quality.HD,
            androidx.camera.video.Quality.SD,
            androidx.camera.video.Quality.LOWEST
        )
        else -> listOf(
            androidx.camera.video.Quality.SD,
            androidx.camera.video.Quality.LOWEST
        )
    }

    val matching = preferredList.filter { supported.contains(it) }
    val finalQualityList = if (matching.isNotEmpty()) matching else supported

    return androidx.camera.video.QualitySelector.fromOrderedList(
        finalQualityList,
        androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(
            finalQualityList.last()
        )
    )
}

/**
 * Lifecycle state for an active video recording pipeline session.
 */
enum class RecordingSessionState {
    IDLE,
    PREPARING,
    RECORDING,
    PAUSED,
    FINALIZING
}

/**
 * Snapshot key representing the last CameraX video configuration applied to the controller,
 * preventing duplicate use-case/effect rebinds that terminate a recording mid-start.
 */
data class VideoPipelineConfigKey(
    val videoEnabled: Boolean,
    val videoResolution: String,
    val isFrontCamera: Boolean,
    val overlayEffectIdentity: Int
)

/**
 * Handle to an active recording session for pause/resume/stop lifecycle control.
 */
interface ActiveRecordingHandle {
    fun pause()
    fun resume()
    fun stop()
}

private class CameraXActiveRecordingHandle(
    private val recording: Recording
) : ActiveRecordingHandle {
    override fun pause() = recording.pause()
    override fun resume() = recording.resume()
    override fun stop() = recording.stop()
}

/**
 * Determines whether a finalized CameraX recording has usable camera output.
 * Fatal CameraX errors or empty/missing files must never trigger demo fallback footage.
 */
fun isFinalizedRecordingValid(
    hasError: Boolean,
    errorCode: Int,
    outputFile: File?
): Boolean {
    if (outputFile == null || !outputFile.exists() || outputFile.length() <= 0L) {
        return false
    }
    if (!hasError) return true
    return errorCode == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED ||
        errorCode == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
}

/**
 * Abstraction over CameraX [LifecycleCameraController] + [PreviewView] readiness and recording,
 * allowing deterministic unit testing without live camera hardware.
 */
interface VideoCameraAdapter {
    val isVideoCaptureEnabled: Boolean
    val hasCameraInfo: Boolean
    fun applyQualitySelector(videoResolution: String)
    fun applyOverlayEffect(effect: androidx.camera.core.CameraEffect?): Boolean
    fun clearEffects()
    fun enableVideoUseCases(includeImageCapture: Boolean): Boolean
    fun enablePhotoOnlyUseCase()
    suspend fun awaitCameraInitialization(timeoutMs: Long = 4000L): Boolean
    suspend fun awaitPreviewStreaming(timeoutMs: Long = 2500L): Boolean
    fun startRecording(
        outputFile: File,
        audioConfig: AudioConfig,
        executor: Executor,
        listener: androidx.core.util.Consumer<VideoRecordEvent>
    ): ActiveRecordingHandle
}

class LifecycleCameraControllerVideoAdapter(
    private val controller: LifecycleCameraController,
    private val previewViewProvider: () -> PreviewView?,
    private val context: Context
) : VideoCameraAdapter {
    override val isVideoCaptureEnabled: Boolean
        get() = try { controller.isVideoCaptureEnabled } catch (ignored: Exception) { false }

    override val hasCameraInfo: Boolean
        get() = try { controller.cameraInfo != null } catch (ignored: Exception) { false }

    override fun applyQualitySelector(videoResolution: String) {
        try {
            val safeSelector = buildSafeQualitySelector(videoResolution, controller.cameraInfo)
            if (safeSelector != null) {
                controller.videoCaptureQualitySelector = safeSelector
            }
        } catch (e: Exception) {
            Log.w("CameraPreview", "Setting video quality selector notice: ${e.message}", e)
        }
    }

    override fun applyOverlayEffect(effect: androidx.camera.core.CameraEffect?): Boolean {
        if (effect == null) return false
        return try {
            controller.setEffects(setOf(effect))
            true
        } catch (e: Exception) {
            Log.w("CameraPreview", "Failed to attach real-time video overlay effect: ${e.message}", e)
            false
        }
    }

    override fun clearEffects() {
        try {
            controller.clearEffects()
        } catch (ignored: Exception) {}
    }

    override fun enableVideoUseCases(includeImageCapture: Boolean): Boolean {
        val targetMask = if (includeImageCapture) {
            LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
        } else {
            LifecycleCameraController.VIDEO_CAPTURE
        }
        return try {
            controller.setEnabledUseCases(targetMask)
            true
        } catch (e: Throwable) {
            Log.w("CameraPreview", "Failed to enable use-case mask $targetMask: ${e.message}")
            false
        }
    }

    override fun enablePhotoOnlyUseCase() {
        try {
            controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        } catch (ignored: Exception) {}
    }

    override suspend fun awaitCameraInitialization(timeoutMs: Long): Boolean {
        val future = try {
            controller.initializationFuture
        } catch (e: Exception) {
            return hasCameraInfo
        }
        if (future.isDone) {
            return try {
                future.get()
                true
            } catch (e: Exception) {
                false
            }
        }
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                future.addListener({
                    if (!cont.isCompleted) {
                        val ok = try {
                            future.get()
                            true
                        } catch (e: Exception) {
                            false
                        }
                        cont.resume(ok)
                    }
                }, mainExecutor)
            }
        }
        return result == true
    }

    override suspend fun awaitPreviewStreaming(timeoutMs: Long): Boolean {
        val pv = previewViewProvider() ?: return hasCameraInfo
        val streamLiveData = pv.previewStreamState
        if (streamLiveData.value == PreviewView.StreamState.STREAMING) {
            return true
        }
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                lateinit var observer: androidx.lifecycle.Observer<PreviewView.StreamState>
                observer = androidx.lifecycle.Observer { state ->
                    if (state == PreviewView.StreamState.STREAMING && !cont.isCompleted) {
                        mainHandler.post {
                            try { streamLiveData.removeObserver(observer) } catch (ignored: Exception) {}
                        }
                        cont.resume(true)
                    }
                }
                mainHandler.post {
                    if (streamLiveData.value == PreviewView.StreamState.STREAMING) {
                        if (!cont.isCompleted) cont.resume(true)
                    } else {
                        streamLiveData.observeForever(observer)
                    }
                }
                cont.invokeOnCancellation {
                    mainHandler.post {
                        try { streamLiveData.removeObserver(observer) } catch (ignored: Exception) {}
                    }
                }
            }
        }
        return result == true
    }

    override fun startRecording(
        outputFile: File,
        audioConfig: AudioConfig,
        executor: Executor,
        listener: androidx.core.util.Consumer<VideoRecordEvent>
    ): ActiveRecordingHandle {
        val fileOptions = FileOutputOptions.Builder(outputFile).build()
        return CameraXActiveRecordingHandle(
            controller.startRecording(fileOptions, audioConfig, executor, listener)
        )
    }
}

/**
 * Manages video recording lifecycle, CameraX pipeline readiness synchronization,
 * duplicate-start prevention, and clean session teardown.
 */
class VideoRecordingManager(
    private val context: Context,
    private val settleDelayFn: suspend (Long) -> Unit = { ms -> delay(ms) }
) {
    private var activeRecording: ActiveRecordingHandle? = null
    private var previewViewRef: PreviewView? = null
    @Volatile
    private var cancelPrepareRequested: Boolean = false

    var currentRecordingFile: File? = null
        private set
    var overlayManager: com.example.overlay.RealtimeVideoOverlayManager? = null

    private val _sessionState = MutableStateFlow(RecordingSessionState.IDLE)
    val sessionState: StateFlow<RecordingSessionState> = _sessionState.asStateFlow()

    @Volatile
    var appliedPipelineKey: VideoPipelineConfigKey? = null
        private set

    @Volatile
    var lastOverlayEffectAttached: Boolean = false
        private set

    fun attachPreviewView(previewView: PreviewView) {
        previewViewRef = previewView
    }

    fun invalidatePipelineConfig() {
        appliedPipelineKey = null
        lastOverlayEffectAttached = false
    }

    fun isRecordingActive(): Boolean = activeRecording != null ||
        _sessionState.value == RecordingSessionState.RECORDING ||
        _sessionState.value == RecordingSessionState.PAUSED

    fun isPreparingOrRecording(): Boolean = activeRecording != null ||
        _sessionState.value != RecordingSessionState.IDLE

    /**
     * Configures CameraX quality selector, [OverlayEffect], and enabled use cases in a single
     * idempotent sequence so no conflicting LaunchedEffect rebinds can abort a recording start.
     *
     * @return true if a CameraX pipeline change/rebind was performed, false if already configured.
     */
    suspend fun ensureVideoPipelineConfigured(
        adapter: VideoCameraAdapter,
        videoResolution: String,
        isFrontCamera: Boolean,
        enableVideo: Boolean,
        strictVideoMode: Boolean,
        overlayManager: com.example.overlay.RealtimeVideoOverlayManager?
    ): Boolean {
        adapter.awaitCameraInitialization(4000L)

        val effect = if (enableVideo) overlayManager?.getOrCreateEffect() else null
        val targetKey = VideoPipelineConfigKey(
            videoEnabled = enableVideo,
            videoResolution = if (enableVideo) videoResolution else "",
            isFrontCamera = isFrontCamera,
            overlayEffectIdentity = if (effect != null) System.identityHashCode(effect) else 0
        )

        if (appliedPipelineKey == targetKey && (!enableVideo || adapter.isVideoCaptureEnabled)) {
            return false
        }

        if (enableVideo) {
            // 1. Set quality selector BEFORE binding VideoCapture + OverlayEffect
            adapter.applyQualitySelector(videoResolution)

            // 2. Attach real-time OverlayEffect cleanly
            val overlayAttached = adapter.applyOverlayEffect(effect)
            lastOverlayEffectAttached = overlayAttached

            // 3. Enable dual IMAGE_CAPTURE | VIDEO_CAPTURE, with VIDEO_CAPTURE-only fallback in strict video mode
            var videoBound = adapter.enableVideoUseCases(includeImageCapture = true)
            if (!videoBound && strictVideoMode) {
                videoBound = adapter.enableVideoUseCases(includeImageCapture = false)
            }
            if (!videoBound && !strictVideoMode) {
                // Restricted hardware couldn't combine Photo + Video idle pre-warm; revert cleanly to Photo until Record click
                adapter.clearEffects()
                adapter.enablePhotoOnlyUseCase()
                lastOverlayEffectAttached = false
                appliedPipelineKey = null
                return false
            }

            adapter.awaitCameraInitialization(4000L)
            if (adapter.isVideoCaptureEnabled) {
                appliedPipelineKey = targetKey
            }
            return true
        } else {
            adapter.clearEffects()
            adapter.enablePhotoOnlyUseCase()
            lastOverlayEffectAttached = false
            appliedPipelineKey = targetKey
            return true
        }
    }

    /**
     * Synchronizes CameraX readiness (initializationFuture + VideoCapture + OverlayEffect + live stream)
     * before invoking [VideoCameraAdapter.startRecording], preventing first-use landscape fallback failures.
     */
    suspend fun prepareAndStartRecording(
        controller: LifecycleCameraController,
        videoResolution: String,
        isFrontCamera: Boolean,
        recordAudio: Boolean,
        initialRotation: Int = 0,
        onRecordingStarted: () -> Unit = {},
        onEvent: (VideoRecordEvent, File?, Boolean) -> Unit,
        onError: (Throwable) -> Unit,
        adapterOverride: VideoCameraAdapter? = null
    ) {
        if (isPreparingOrRecording()) {
            Log.d("VideoRecordingManager", "Ignoring duplicate recording start while state=${_sessionState.value}")
            return
        }

        _sessionState.value = RecordingSessionState.PREPARING
        cancelPrepareRequested = false

        val adapter = adapterOverride ?: LifecycleCameraControllerVideoAdapter(
            controller = controller,
            previewViewProvider = { previewViewRef },
            context = context
        )

        try {
            val rebound = ensureVideoPipelineConfigured(
                adapter = adapter,
                videoResolution = videoResolution,
                isFrontCamera = isFrontCamera,
                enableVideo = true,
                strictVideoMode = true,
                overlayManager = overlayManager
            )

            if (cancelPrepareRequested) {
                _sessionState.value = RecordingSessionState.IDLE
                return
            }

            val videoReady = adapter.isVideoCaptureEnabled && adapter.hasCameraInfo
            val streaming = if (videoReady) adapter.awaitPreviewStreaming(2500L) else false

            if (cancelPrepareRequested) {
                _sessionState.value = RecordingSessionState.IDLE
                return
            }

            if (!videoReady || !streaming) {
                Log.e(
                    "VideoRecordingManager",
                    "Camera video stream not ready (videoEnabled=${adapter.isVideoCaptureEnabled}, hasCameraInfo=${adapter.hasCameraInfo}, streaming=$streaming)"
                )
                _sessionState.value = RecordingSessionState.IDLE
                onError(IllegalStateException("Camera is still initializing. Please try again."))
                return
            }

            if (rebound) {
                // Allow SurfaceProcessor + VideoCapture surface 150ms to receive live frames before MediaCodec starts
                settleDelayFn(150L)
                if (cancelPrepareRequested) {
                    _sessionState.value = RecordingSessionState.IDLE
                    return
                }
            }

            val tempDir = File(context.cacheDir, "temp_video").apply { mkdirs() }
            // Clean up any stale 0-byte temp files from prior aborted attempts
            try {
                tempDir.listFiles()?.forEach { f ->
                    if (f.isFile && f.length() == 0L && f.name.startsWith("RAW_")) {
                        f.delete()
                    }
                }
            } catch (ignored: Exception) {}

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val uniqueSuffix = UUID.randomUUID().toString().replace("-", "").take(6)
            val tempFile = File(tempDir, "RAW_${timeStamp}_${uniqueSuffix}.mp4")
            currentRecordingFile = tempFile

            val executor = ContextCompat.getMainExecutor(context)
            val hasAudioPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val audioConfig = if (recordAudio && hasAudioPerm) {
                try {
                    AudioConfig.create(true)
                } catch (e: Exception) {
                    AudioConfig.AUDIO_DISABLED
                }
            } else {
                AudioConfig.AUDIO_DISABLED
            }

            val sessionHasOverlay = lastOverlayEffectAttached
            overlayManager?.notifyRecordingStarted(initialRotation)
            onRecordingStarted()

            val recordListener = androidx.core.util.Consumer<VideoRecordEvent> { event ->
                val recFile = currentRecordingFile
                when (event) {
                    is VideoRecordEvent.Pause -> {
                        if (_sessionState.value == RecordingSessionState.RECORDING) {
                            _sessionState.value = RecordingSessionState.PAUSED
                        }
                    }
                    is VideoRecordEvent.Resume -> {
                        if (_sessionState.value == RecordingSessionState.PAUSED) {
                            _sessionState.value = RecordingSessionState.RECORDING
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        _sessionState.value = RecordingSessionState.FINALIZING
                        activeRecording = null
                        overlayManager?.notifyRecordingStopped()
                        if (event.hasError()) {
                            Log.e(
                                "VideoRecordingManager",
                                "Video recording finalized with error: ${event.error}, cause: ${event.cause}"
                            )
                        }
                        onEvent(event, recFile, sessionHasOverlay)
                        _sessionState.value = RecordingSessionState.IDLE
                    }
                    else -> Unit
                }
            }

            val pendingRecording = try {
                adapter.startRecording(tempFile, audioConfig, executor, recordListener)
            } catch (e: SecurityException) {
                Log.w("VideoRecordingManager", "Audio recording permission error, retrying without audio", e)
                adapter.startRecording(tempFile, AudioConfig.AUDIO_DISABLED, executor, recordListener)
            }
            activeRecording = pendingRecording
            if (_sessionState.value == RecordingSessionState.PREPARING) {
                _sessionState.value = RecordingSessionState.RECORDING
            }
        } catch (e: Throwable) {
            activeRecording = null
            overlayManager?.notifyRecordingStopped()
            _sessionState.value = RecordingSessionState.IDLE
            try { currentRecordingFile?.takeIf { it.exists() && it.length() == 0L }?.delete() } catch (ignored: Exception) {}
            onError(e)
        }
    }

    fun pause() {
        try {
            activeRecording?.pause()
        } catch (ignored: Exception) {}
    }

    fun resume() {
        try {
            activeRecording?.resume()
        } catch (ignored: Exception) {}
    }

    fun stop() {
        if (_sessionState.value == RecordingSessionState.PREPARING) {
            cancelPrepareRequested = true
        }
        try {
            activeRecording?.stop()
        } catch (ignored: Exception) {}
        if (activeRecording == null && _sessionState.value == RecordingSessionState.PREPARING) {
            _sessionState.value = RecordingSessionState.IDLE
            overlayManager?.notifyRecordingStopped()
        }
    }

    fun release() {
        cancelPrepareRequested = true
        try {
            activeRecording?.stop()
        } catch (ignored: Exception) {}
        activeRecording = null
        previewViewRef = null
        invalidatePipelineConfig()
        _sessionState.value = RecordingSessionState.IDLE
        overlayManager?.notifyRecordingStopped()
    }
}

/**
 * Subtle framing guide overlay displaying corner brackets and gentle cropping mask
 * matching the selected aspect ratio (1:1, 4:3, 16:9).
 */
@Composable
fun CameraFramingGuideOverlay(
    aspectRatioStr: String,
    isLandscape: Boolean,
    containerWidth: Float,
    containerHeight: Float,
    modifier: Modifier = Modifier
) {
    val targetRatio = when (aspectRatioStr.lowercase(java.util.Locale.US)) {
        "1:1", "square", "square 1:1", "1:1 square" -> 1.0f
        "4:3", "standard", "standard 4:3", "4:3 standard" -> if (isLandscape) 4f / 3f else 3f / 4f
        "16:9", "widescreen", "widescreen 16:9", "16:9 widescreen" -> if (isLandscape) 16f / 9f else 9f / 16f
        else -> return
    }

    val currentRatio = containerWidth / containerHeight
    val frameW: Float
    val frameH: Float
    if (currentRatio > targetRatio) {
        frameH = containerHeight
        frameW = (containerHeight * targetRatio).coerceAtMost(containerWidth)
    } else {
        frameW = containerWidth
        frameH = (containerWidth / targetRatio).coerceAtMost(containerHeight)
    }

    val left = (containerWidth - frameW) / 2f
    val top = (containerHeight - frameH) / 2f
    val right = left + frameW
    val bottom = top + frameH

    ComposeCanvas(modifier = modifier.fillMaxSize()) {
        val maskColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.28f)
        if (left > 0.5f) {
            drawRect(
                color = maskColor,
                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(left, containerHeight)
            )
            drawRect(
                color = maskColor,
                topLeft = androidx.compose.ui.geometry.Offset(right, 0f),
                size = androidx.compose.ui.geometry.Size(containerWidth - right, containerHeight)
            )
        }
        if (top > 0.5f) {
            drawRect(
                color = maskColor,
                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(containerWidth, top)
            )
            drawRect(
                color = maskColor,
                topLeft = androidx.compose.ui.geometry.Offset(0f, bottom),
                size = androidx.compose.ui.geometry.Size(containerWidth, containerHeight - bottom)
            )
        }

        // Crisp corner brackets around the framed active region
        val bracketLen = 22.dp.toPx()
        val bracketStroke = 1.8.dp.toPx()
        val bracketColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.70f)

        // Top-left
        drawLine(bracketColor, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left + bracketLen, top), bracketStroke)
        drawLine(bracketColor, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, top + bracketLen), bracketStroke)
        // Top-right
        drawLine(bracketColor, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right - bracketLen, top), bracketStroke)
        drawLine(bracketColor, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right, top + bracketLen), bracketStroke)
        // Bottom-left
        drawLine(bracketColor, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left + bracketLen, bottom), bracketStroke)
        drawLine(bracketColor, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left, bottom + bracketLen), bracketStroke)
        // Bottom-right
        drawLine(bracketColor, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right - bracketLen, bottom), bracketStroke)
        drawLine(bracketColor, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right, bottom - bracketLen), bracketStroke)
    }
}

