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
    cropMode: com.example.model.CameraCropMode = com.example.model.CameraCropMode.FULL,
    autoBrightnessEnabled: Boolean = true,
    manualPreviewBrightness: Float = CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL,
    targetVideoFps: Int = 30,
    photoQualityPreset: com.example.model.OutputQualityPreset = com.example.model.OutputQualityPreset.MAXIMUM,
    videoStabilizationMode: com.example.model.VideoStabilizationMode = com.example.model.VideoStabilizationMode.AUTO,
    autofocusMode: com.example.model.CameraAutofocusMode = com.example.model.CameraAutofocusMode.CONTINUOUS,
    photoEnhancementEnabled: Boolean = true,
    videoNoiseReductionEnabled: Boolean = true,
    isHdrEnabled: Boolean = true,
    touchToCapture: Boolean = false,
    easyToggleCameras: Boolean = true,
    useStandaloneUltraWide: Boolean = false,
    standaloneUltraWideCameraId: String? = null,
    logicalWideCameraId: String? = null,
    controller: LifecycleCameraController,
    currentSnapshotProvider: () -> CaptureSnapshot? = { null },
    overlayManager: com.example.overlay.RealtimeVideoOverlayManager? = null,
    videoManager: VideoRecordingManager? = null,
    onExposureChanged: ((Int) -> Unit)? = null,
    onZoomChanged: ((Float) -> Unit)? = null,
    onAutoBrightnessChanged: ((Boolean) -> Unit)? = null,
    onManualBrightnessChanged: ((Float) -> Unit)? = null,
    onCaptureExposureTelemetry: ((Float, Float, Boolean) -> Unit)? = null,
    onTouchToCapture: (() -> Unit)? = null,
    onDoubleTapSwitchCamera: (() -> Unit)? = null,
    onZoomCapabilitiesChanged: ((com.example.camera.CameraLensZoomCapabilities) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestSnapshotProvider by rememberUpdatedState(currentSnapshotProvider)
    val latestOnAutoBrightnessChanged by rememberUpdatedState(onAutoBrightnessChanged)
    val latestOnManualBrightnessChanged by rememberUpdatedState(onManualBrightnessChanged)
    val latestOnCaptureExposureTelemetry by rememberUpdatedState(onCaptureExposureTelemetry)
    val latestOnTouchToCapture by rememberUpdatedState(onTouchToCapture)
    val latestOnDoubleTapSwitchCamera by rememberUpdatedState(onDoubleTapSwitchCamera)
    val latestOnZoomCapabilitiesChanged by rememberUpdatedState(onZoomCapabilitiesChanged)
    val latestZoomRatio by rememberUpdatedState(zoomRatio)
    val latestUseStandaloneUltraWide by rememberUpdatedState(useStandaloneUltraWide)
    val latestTouchToCapture by rememberUpdatedState(touchToCapture)
    val latestEasyToggleCameras by rememberUpdatedState(easyToggleCameras)
    val latestIsVideoMode by rememberUpdatedState(isVideoMode)
    val latestIsVideoRecording by rememberUpdatedState(isVideoRecording)

    // Camera Hardware Zoom Range State
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(10.0f) }
    var liveHardwareMinZoom by remember { mutableFloatStateOf(1.0f) }
    var liveHardwareMaxZoom by remember { mutableFloatStateOf(10.0f) }
    var zoomCapabilities by remember { mutableStateOf(com.example.camera.CameraLensZoomCapabilities()) }
    var cameraBindEpoch by remember { mutableIntStateOf(0) }
    var lastTapTimeMs by remember { mutableStateOf(0L) }
    val latestZoomCapabilities by rememberUpdatedState(zoomCapabilities)
    val latestMaxZoomRatio by rememberUpdatedState(maxZoomRatio)

    DisposableEffect(controller, lifecycleOwner) {
        val observer = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { state ->
            if (state != null) {
                liveHardwareMinZoom = state.minZoomRatio
                liveHardwareMaxZoom = state.maxZoomRatio
                // Never let a standalone physical ultra-wide sensor's smaller maxZoomRatio (e.g. 4x)
                // overwrite the rear camera system's full zoom range while previewing at < 1.0x.
                if (!latestUseStandaloneUltraWide) {
                    minZoomRatio = state.minZoomRatio
                    maxZoomRatio = state.maxZoomRatio
                }
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
            ).setAutoCancelDuration(6, TimeUnit.SECONDS)
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
    var retryBindTrigger by remember { mutableIntStateOf(0) }
    var cameraInitError by remember { mutableStateOf<String?>(null) }

    // Refresh permission status, safely finalize active recording on ON_STOP before CameraX unbinds, and auto-recover on ON_RESUME
    DisposableEffect(lifecycleOwner, videoManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasCameraPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    lifecycleResumeVersion++
                    if (cameraInitError != null) {
                        retryBindTrigger++
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (videoManager?.isPreparingOrRecording() == true) {
                        videoManager.stop()
                    }
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
    DisposableEffect(lifecycleOwner, hasCameraPermission, videoManager, retryBindTrigger) {
        if (hasCameraPermission) {
            try {
                controller.bindToLifecycle(lifecycleOwner)
                cameraInitError = null
                lifecycleResumeVersion++
                cameraBindEpoch++
            } catch (e: Throwable) {
                Log.e("CameraPreview", "Failed to bind camera controller to lifecycle", e)
                cameraInitError = "Unable to start camera (${e.localizedMessage ?: "hardware busy"})."
            }
        }
        onDispose {
            if (videoManager?.isPreparingOrRecording() == true) {
                videoManager.stop()
            }
            videoManager?.invalidatePipelineConfig()
            try {
                controller.unbind()
            } catch (ignored: Exception) {}
        }
    }

    // Unified CameraX Pipeline Configuration (Camera Selector + QualitySelector + OverlayEffect + UseCases).
    // Running sequentially in a single LaunchedEffect eliminates mid-start rebinds between separate effects.
    // Never mutates CameraX use cases/effects while a recording session is preparing or active.
    var lastAppliedFrontCamera by remember { mutableStateOf<Boolean?>(null) }
    var lastAppliedStandaloneUltraWide by remember { mutableStateOf(false) }
    var lastAppliedLogicalWideCameraId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(
        hasCameraPermission,
        isVideoMode,
        isVideoRecording,
        isLandscape,
        isFlashOn,
        isFrontCamera,
        useStandaloneUltraWide,
        standaloneUltraWideCameraId,
        logicalWideCameraId,
        videoResolution,
        photoQualityPreset,
        overlayManager,
        videoManager,
        lifecycleResumeVersion,
        retryBindTrigger
    ) {
        if (!hasCameraPermission) return@LaunchedEffect
        if (videoManager?.isPreparingOrRecording() == true) return@LaunchedEffect

        try {
            controller.isPinchToZoomEnabled = false
            controller.isTapToFocusEnabled = false

            // Bind VIDEO_CAPTURE + OverlayEffect ONLY during Video mode or active recording.
            // Keeping Landscape still photography on a pure Preview + ImageCapture session preserves
            // full native sensor resolution (e.g. 12MP 4000x3000) and OEM multi-frame HDR/MFNR quality
            // instead of downgrading ImageCapture to video-bound 1920x1440 / MINIMIZE_LATENCY.
            val shouldEnableVideoPipeline = isVideoMode || isVideoRecording
            try {
                val zslSupported = !shouldEnableVideoPipeline &&
                    !isFlashOn &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    try { controller.cameraInfo?.isZslSupported == true } catch (_: Throwable) { false }
                val desiredCaptureMode = resolveDesiredImageCaptureMode(
                    shouldEnableVideoPipeline = shouldEnableVideoPipeline,
                    isFlashOn = isFlashOn,
                    zslSupported = zslSupported,
                    preferMaxQuality = true
                )
                if (controller.imageCaptureMode != desiredCaptureMode) {
                    controller.imageCaptureMode = desiredCaptureMode
                }
            } catch (ignored: Throwable) {}

            val autoVideoResolution = com.example.camera.CameraResolutionHelper.resolveBestAutomaticVideoResolution(
                context = context,
                isFrontCamera = isFrontCamera,
                cameraInfo = controller.cameraInfo
            )

            val adapter = LifecycleCameraControllerVideoAdapter(
                controller = controller,
                previewViewProvider = { previewViewInstance },
                context = context
            )

            // Wait for initial CameraX lifecycle bind to settle before querying cameraInfo / qualities
            adapter.awaitCameraInitialization(4000L)
            if (videoManager?.isPreparingOrRecording() == true) return@LaunchedEffect

            // Configure Camera Selector safely using strict Camera2 ID filtering so CameraX
            // genuinely switches to the standalone physical ultra-wide lens or logical multi-camera
            // instead of silently falling back to the 1.0x camera.
            val requestedStandalone = !isFrontCamera && useStandaloneUltraWide && !standaloneUltraWideCameraId.isNullOrBlank()
            val standaloneSelector = if (requestedStandalone) {
                com.example.camera.CameraUltraWideZoomManager.buildStrictCameraIdSelector(
                    lensFacing = CameraSelector.LENS_FACING_BACK,
                    cameraId = standaloneUltraWideCameraId!!
                )
            } else {
                null
            }
            val logicalWideSelector = if (!isFrontCamera && !requestedStandalone && !logicalWideCameraId.isNullOrBlank()) {
                com.example.camera.CameraUltraWideZoomManager.buildStrictCameraIdSelector(
                    lensFacing = CameraSelector.LENS_FACING_BACK,
                    cameraId = logicalWideCameraId
                )
            } else {
                null
            }

            val canUseStandalone = standaloneSelector != null && try {
                controller.hasCamera(standaloneSelector)
            } catch (_: Throwable) {
                false
            }
            val canUseLogicalWide = logicalWideSelector != null && try {
                controller.hasCamera(logicalWideSelector)
            } catch (_: Throwable) {
                false
            }

            val hasFrontHw = try { controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) } catch (_: Throwable) { false }
            val hasBackHw = try { controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) } catch (_: Throwable) { false }

            val desiredFront = if (isFrontCamera) {
                if (hasFrontHw) true else !hasBackHw
            } else {
                if (canUseStandalone || canUseLogicalWide || hasBackHw) false else hasFrontHw
            }

            val effectiveStandalone = !desiredFront && canUseStandalone
            val effectiveLogicalWideId = if (!desiredFront && !effectiveStandalone && canUseLogicalWide) {
                logicalWideCameraId
            } else {
                null
            }
            val effectiveSelector = when {
                desiredFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                effectiveStandalone && standaloneSelector != null -> standaloneSelector
                effectiveLogicalWideId != null && logicalWideSelector != null -> logicalWideSelector
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }

            val needsLensRebind = lastAppliedFrontCamera == null ||
                lastAppliedFrontCamera != desiredFront ||
                lastAppliedStandaloneUltraWide != effectiveStandalone ||
                lastAppliedLogicalWideCameraId != effectiveLogicalWideId ||
                (videoManager != null && videoManager.appliedPipelineKey?.isFrontCamera != desiredFront)

            if (needsLensRebind) {
                try {
                    controller.cameraSelector = effectiveSelector
                    lastAppliedFrontCamera = desiredFront
                    lastAppliedStandaloneUltraWide = effectiveStandalone
                    lastAppliedLogicalWideCameraId = effectiveLogicalWideId
                    videoManager?.invalidatePipelineConfig()
                    adapter.awaitCameraInitialization(3000L)
                    cameraInitError = null
                    cameraBindEpoch++
                } catch (e: Throwable) {
                    Log.w("CameraPreview", "Camera selector error on $effectiveSelector, falling back to default lens", e)
                    val fallbackSelector = if (desiredFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        controller.cameraSelector = fallbackSelector
                        lastAppliedFrontCamera = desiredFront
                        lastAppliedStandaloneUltraWide = false
                        lastAppliedLogicalWideCameraId = null
                        videoManager?.invalidatePipelineConfig()
                        adapter.awaitCameraInitialization(3000L)
                        cameraInitError = null
                        if (effectiveStandalone && zoomRatio < 0.99f) {
                            onZoomChanged?.invoke(1.0f)
                        }
                        cameraBindEpoch++
                    } catch (fallbackErr: Throwable) {
                        Log.e("CameraPreview", "Fallback camera selector failed", fallbackErr)
                        cameraInitError = "Camera lens unavailable (${fallbackErr.localizedMessage ?: "error"})."
                    }
                }
            }

            if (videoManager?.isPreparingOrRecording() == true) return@LaunchedEffect

            // In Landscape mode both Photo and Video triggers are immediately accessible without a mode toggle,
            // so pre-warm VIDEO_CAPTURE + OverlayEffect + QualitySelector right away when hardware supports video.
            // In Portrait mode keep strict Photo/Video mode separation (enable video pipeline only when isVideoMode).
            if (videoManager != null) {
                videoManager.ensureVideoPipelineConfigured(
                    adapter = adapter,
                    videoResolution = autoVideoResolution,
                    isFrontCamera = desiredFront,
                    enableVideo = shouldEnableVideoPipeline,
                    strictVideoMode = isVideoMode,
                    overlayManager = overlayManager
                )
            } else if (shouldEnableVideoPipeline && hasSupportedHardwareVideoProfiles(controller.cameraInfo)) {
                val safeSelector = buildSafeQualitySelector(autoVideoResolution, controller.cameraInfo)
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
                    try {
                        controller.clearEffects()
                        controller.setEnabledUseCases(
                            if (isVideoMode) LifecycleCameraController.VIDEO_CAPTURE
                            else LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
                        )
                    } catch (ignored: Throwable) {}
                }
            } else {
                if (controller.isVideoCaptureEnabled || !controller.isImageCaptureEnabled) {
                    try { controller.clearEffects() } catch (ignored: Exception) {}
                    try { controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE) } catch (ignored: Exception) {}
                }
            }
            // Re-apply active zoom ratio after use-case / video pipeline configuration completes
            cameraBindEpoch++
        } catch (e: Throwable) {
            Log.w("CameraPreview", "Unified camera pipeline configuration notice: ${e.message}", e)
        }
    }

    // Query real hardware exposure limits (EV range, step, ISO, shutter) & Ultra-Wide Zoom capabilities
    LaunchedEffect(hasCameraPermission, isFrontCamera, useStandaloneUltraWide, lifecycleResumeVersion) {
        if (!hasCameraPermission) return@LaunchedEffect
        val caps = CameraBrightnessEngine.queryCaptureExposureCapabilities(
            context = context,
            controller = controller,
            isFrontCamera = isFrontCamera
        )
        captureExposureState = exposureController.updateCapabilities(caps)

        val bindableIds = queryBindableCameraXIds(context)
        val initialZoomCaps = com.example.camera.CameraUltraWideZoomManager.detectCapabilities(
            context = context,
            cameraInfo = controller.cameraInfo,
            isFrontCamera = isFrontCamera,
            fallbackMinZoom = minZoomRatio,
            fallbackMaxZoom = maxZoomRatio,
            isCurrentlyBoundToStandaloneUltraWide = useStandaloneUltraWide,
            bindableCameraIds = bindableIds
        )
        zoomCapabilities = initialZoomCaps
        latestOnZoomCapabilitiesChanged?.invoke(initialZoomCaps)
        cameraBindEpoch++

        // Re-query shortly after CameraX finishes async lens bind so live ZoomState & ExposureState are picked up
        delay(450L)
        val boundCaps = CameraBrightnessEngine.queryCaptureExposureCapabilities(
            context = context,
            controller = controller,
            isFrontCamera = isFrontCamera
        )
        captureExposureState = exposureController.updateCapabilities(boundCaps)

        val refreshedBindableIds = queryBindableCameraXIds(context) ?: bindableIds
        val boundZoomCaps = com.example.camera.CameraUltraWideZoomManager.detectCapabilities(
            context = context,
            cameraInfo = controller.cameraInfo,
            isFrontCamera = isFrontCamera,
            fallbackMinZoom = minZoomRatio,
            fallbackMaxZoom = maxZoomRatio,
            isCurrentlyBoundToStandaloneUltraWide = useStandaloneUltraWide,
            bindableCameraIds = refreshedBindableIds
        )
        zoomCapabilities = boundZoomCaps
        latestOnZoomCapabilitiesChanged?.invoke(boundZoomCaps)
        cameraBindEpoch++
    }

    // Apply Camera2 hardware quality tuning (Hardware HDR, Auto Antibanding, Continuous AF, Hardware NR/Edge,
    // OIS/EIS Stabilization, Video Target FPS) non-destructively without restarting the camera session.
    LaunchedEffect(
        hasCameraPermission,
        isFrontCamera,
        useStandaloneUltraWide,
        isVideoMode,
        isVideoRecording,
        isHdrEnabled,
        isFlashOn,
        targetVideoFps,
        videoStabilizationMode,
        autofocusMode,
        photoEnhancementEnabled,
        videoNoiseReductionEnabled,
        lifecycleResumeVersion,
        cameraBindEpoch
    ) {
        if (!hasCameraPermission) return@LaunchedEffect
        delay(200L)
        try {
            val hwCaps = com.example.camera.CameraQualityEngine.queryHardwareCapabilities(
                context = context,
                isFrontCamera = isFrontCamera,
                cameraInfo = controller.cameraInfo
            )
            com.example.camera.CameraQualityEngine.applyCamera2Tuning(
                cameraControl = controller.cameraControl,
                capabilities = hwCaps,
                isVideoMode = isVideoMode || isVideoRecording,
                targetVideoFps = targetVideoFps,
                stabilizationMode = videoStabilizationMode,
                autofocusMode = autofocusMode,
                photoEnhancementEnabled = photoEnhancementEnabled,
                videoNoiseReductionEnabled = videoNoiseReductionEnabled,
                isHdrEnabled = isHdrEnabled,
                isFlashOn = isFlashOn
            )
        } catch (ignored: Throwable) {}
    }

    LaunchedEffect(hasCameraPermission, isFlashOn, isFrontCamera, cameraBindEpoch) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val hasFlash = try { controller.cameraInfo?.hasFlashUnit() == true } catch (_: Throwable) { false }
            controller.imageCaptureFlashMode = if (isFlashOn && hasFlash) {
                ImageCapture.FLASH_MODE_ON
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
            controller.enableTorch(isFlashOn && hasFlash)
        } catch (ignored: Exception) {}
    }

    // Rebind-safe hardware zoom application: waits for active CameraControl, reads live sensor ZoomState
    // (never clamping < 1.0x against stale defaults), applies setZoomRatio to Preview + ImageCapture +
    // VideoCapture simultaneously, and retries once if interrupted by a concurrent lens/use-case rebind.
    LaunchedEffect(
        hasCameraPermission,
        zoomRatio,
        liveHardwareMinZoom,
        liveHardwareMaxZoom,
        useStandaloneUltraWide,
        zoomCapabilities.minZoomRatio,
        zoomCapabilities.maxZoomRatio,
        zoomCapabilities.widePresetRatio,
        cameraBindEpoch
    ) {
        if (!hasCameraPermission) return@LaunchedEffect
        var readyAttempts = 0
        while ((controller.cameraControl == null || controller.cameraInfo == null) && readyAttempts < 20) {
            delay(60L)
            readyAttempts++
        }
        val liveZoomState = controller.cameraInfo?.zoomState?.value
        val effectiveHwMin = liveZoomState?.minZoomRatio ?: if (useStandaloneUltraWide) {
            liveHardwareMinZoom.coerceAtLeast(1.0f)
        } else {
            minOf(liveHardwareMinZoom, zoomCapabilities.minZoomRatio)
        }
        val effectiveHwMax = liveZoomState?.maxZoomRatio ?: maxOf(liveHardwareMaxZoom, zoomCapabilities.maxZoomRatio)

        val targetHardwareZoom = com.example.camera.CameraUltraWideZoomManager.computeTargetHardwareZoomRatio(
            requestedZoom = zoomRatio,
            hardwareMinZoom = effectiveHwMin,
            hardwareMaxZoom = effectiveHwMax,
            useStandaloneUltraWide = useStandaloneUltraWide,
            widePresetRatio = zoomCapabilities.widePresetRatio
        )

        val applied = applyZoomRatioWithVerification(controller, context, targetHardwareZoom)
        if (!applied) {
            delay(180L)
            val retryState = controller.cameraInfo?.zoomState?.value
            val retryMin = retryState?.minZoomRatio ?: effectiveHwMin
            val retryMax = retryState?.maxZoomRatio ?: effectiveHwMax
            val retryTarget = com.example.camera.CameraUltraWideZoomManager.computeTargetHardwareZoomRatio(
                requestedZoom = zoomRatio,
                hardwareMinZoom = retryMin,
                hardwareMaxZoom = retryMax,
                useStandaloneUltraWide = useStandaloneUltraWide,
                widePresetRatio = zoomCapabilities.widePresetRatio
            )
            applyZoomRatioWithVerification(controller, context, retryTarget)
        }
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

    // Automatically configure the highest suitable full-native-sensor photo & preview resolution selectors.
    // Using orientation-agnostic CameraX ResolutionSelector (instead of deprecated OutputSize(Size)) guarantees
    // Portrait and Landscape both capture full native sensor framing (e.g. 4000x3000 12MP) and NEVER fall back
    // to a rotated 1:1 square size (like 1088x1088) on OEM HALs.
    val nativeSensorAspectRatio = remember(isFrontCamera, isVideoMode, useStandaloneUltraWide, cameraBindEpoch) {
        com.example.camera.CameraResolutionHelper.resolveNativeSensorAspectRatio(
            context = context,
            isFrontCamera = isFrontCamera,
            isVideoMode = isVideoMode,
            cameraInfo = controller.cameraInfo
        )
    }

    LaunchedEffect(
        isFrontCamera,
        useStandaloneUltraWide,
        standaloneUltraWideCameraId,
        logicalWideCameraId,
        isVideoMode,
        hasCameraPermission
    ) {
        if (!hasCameraPermission) return@LaunchedEffect
        if (videoManager?.isPreparingOrRecording() == true) return@LaunchedEffect
        try {
            val photoSensorRatio = com.example.camera.CameraResolutionHelper.resolveNativeSensorAspectRatio(
                context = context,
                isFrontCamera = isFrontCamera,
                isVideoMode = false,
                cameraInfo = controller.cameraInfo
            )
            val previewSensorRatio = com.example.camera.CameraResolutionHelper.resolveNativeSensorAspectRatio(
                context = context,
                isFrontCamera = isFrontCamera,
                isVideoMode = isVideoMode,
                cameraInfo = controller.cameraInfo
            )
            controller.imageCaptureResolutionSelector =
                com.example.camera.CameraResolutionHelper.buildNativePhotoResolutionSelector(
                    sensorLongShortRatio = photoSensorRatio
                )
            controller.previewResolutionSelector =
                com.example.camera.CameraResolutionHelper.buildNativePreviewResolutionSelector(
                    sensorLongShortRatio = previewSensorRatio,
                    isVideoMode = isVideoMode
                )
        } catch (e: Exception) {
            Log.w("CameraPreview", "Setting automatic native photo/preview resolution selector notice: ${e.message}", e)
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

                // 1. CameraX PreviewView Rendering — FIT_CENTER guarantees 100% corner-to-corner native sensor FOV
                // with ZERO hidden edge cropping so live preview matches saved photos and videos identically.
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            this.controller = controller
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                            // COMPATIBLE uses TextureView which eliminates SurfaceView EGL_BAD_ATTRIBUTE emulation errors
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }.also {
                            previewViewInstance = it
                            videoManager?.attachPreviewView(it)
                        }
                    },
                    update = { pv ->
                        if (pv.scaleType != PreviewView.ScaleType.FIT_CENTER) {
                            pv.scaleType = PreviewView.ScaleType.FIT_CENTER
                        }
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
                                            val pinchMinZoom = latestZoomCapabilities.effectiveMinZoomRatio
                                            val pinchMaxZoom = maxOf(latestMaxZoomRatio, latestZoomCapabilities.maxZoomRatio)
                                            val targetZoom = (activeZoom * scaleFactor).coerceIn(pinchMinZoom, pinchMaxZoom)
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
                                            val nowTap = android.os.SystemClock.elapsedRealtime()
                                            val isDoubleTap = (nowTap - lastTapTimeMs) in 50L..340L
                                            lastTapTimeMs = nowTap

                                            if (!latestTouchToCapture &&
                                                latestEasyToggleCameras &&
                                                !latestIsVideoRecording &&
                                                isDoubleTap &&
                                                latestOnDoubleTapSwitchCamera != null
                                            ) {
                                                lastTapTimeMs = 0L
                                                latestOnDoubleTapSwitchCamera?.invoke()
                                            } else {
                                                // Tap detected! Focus at exact touched position
                                                triggerTouchToFocus(startPos, constraints.maxWidth, constraints.maxHeight)
                                                if (!latestIsVideoMode && latestTouchToCapture) {
                                                    latestOnTouchToCapture?.invoke()
                                                }
                                            }
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

                // 5. Dynamic Crop Mode Framing Overlay — synchronized 1:1 with the FIT_CENTER sensor viewport
                //    In FULL mode: absolutely ZERO crop mask (complete corner-to-corner sensor frame visible).
                if (cropMode != com.example.model.CameraCropMode.FULL && constraints.maxWidth > 0 && constraints.maxHeight > 0) {
                    CameraCropModeGuideOverlay(
                        cropMode = cropMode,
                        sensorLongShortRatio = nativeSensorAspectRatio,
                        isLandscape = isLandscape,
                        containerWidth = constraints.maxWidth.toFloat(),
                        containerHeight = constraints.maxHeight.toFloat()
                    )
                }

                // 6. Camera Hardware / Session Recovery Banner (only visible when initialization fails)
                if (cameraInitError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .testTag("camera_init_error_overlay"),
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
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = cameraInitError ?: "Camera unavailable",
                                color = PrimaryText,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    cameraInitError = null
                                    retryBindTrigger++
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HighlightYellow,
                                    contentColor = PrimaryBackground
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Retry Camera", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
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
 * Resolves the safe, highest-quality [ImageCapture] capture mode:
 * - Whenever [shouldEnableVideoPipeline] is true, CameraX requires [ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY]
 *   so quick snapshots during video recording never stall or conflict with VideoCapture/OverlayEffect.
 * - For pure still photography with [preferMaxQuality] enabled (default), uses
 *   [ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY] so OEM HALs engage full-resolution multi-frame
 *   HDR/MFNR highlight recovery and natural noise reduction.
 * - When [preferMaxQuality] is false, falls back to ZSL (if supported & flash off) or MINIMIZE_LATENCY.
 */
fun resolveDesiredImageCaptureMode(
    shouldEnableVideoPipeline: Boolean,
    isFlashOn: Boolean,
    zslSupported: Boolean,
    preferMaxQuality: Boolean = true
): Int {
    return when {
        shouldEnableVideoPipeline -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        preferMaxQuality -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        !isFlashOn && zslSupported -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
        else -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    }
}

/**
 * Normalizes raw captured bitmap orientation based on CameraX image info rotation
 * and active UI mode (Landscape vs Portrait), ensuring true upright output without bogus 180° flips.
 */
fun normalizeBitmapOrientation(
    rawBitmap: Bitmap,
    cameraXRotationDegrees: Int,
    isLandscapeMode: Boolean = false,
    deviceRotationDegrees: Int = 0
): Bitmap {
    // 1. First apply CameraX reported rotation to bring image upright relative to target rotation
    val normalizedRot = ((cameraXRotationDegrees % 360) + 360) % 360
    val upright = if (normalizedRot != 0) {
        try {
            val matrix = Matrix().apply { postRotate(normalizedRot.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } catch (e: Exception) {
            rawBitmap
        }
    } else {
        rawBitmap
    }

    // 2. Guard only when buffer dimensions still disagree with the active UI orientation (some OEM HALs
    // omit imageInfo.rotationDegrees or deliver sensor-landscape frames). Never flip an already-upright image 180°.
    if (isLandscapeMode) {
        if (upright.width < upright.height) {
            val rotateBy = if (deviceRotationDegrees == 270) 270f else 90f
            return try {
                val matrix = Matrix().apply { postRotate(rotateBy) }
                Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, matrix, true)
            } catch (e: Exception) {
                upright
            }
        }
    } else {
        if (upright.width > upright.height) {
            val rotateBy = if (deviceRotationDegrees == 270) 270f else 90f
            return try {
                val matrix = Matrix().apply { postRotate(rotateBy) }
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

private val captureDecodeExecutor: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CtecsFastPhotoDecode").apply { priority = Thread.MAX_PRIORITY - 1 }
    }

/**
 * Extracts optical and exposure camera EXIF tags from the raw hardware JPEG buffer before bitmap decode
 * so the final saved CTECS photograph retains authentic sensor metadata (aperture, shutter speed, ISO,
 * focal length, white balance, metering mode) alongside GPS, chainage, and project metadata.
 */
internal fun extractCameraExifMetadata(jpegBytes: ByteArray): Map<String, String> {
    if (jpegBytes.isEmpty()) return emptyMap()
    val tags = listOf(
        android.media.ExifInterface.TAG_EXPOSURE_TIME,
        android.media.ExifInterface.TAG_F_NUMBER,
        android.media.ExifInterface.TAG_ISO_SPEED_RATINGS,
        android.media.ExifInterface.TAG_FOCAL_LENGTH,
        android.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        android.media.ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        android.media.ExifInterface.TAG_WHITE_BALANCE,
        android.media.ExifInterface.TAG_FLASH,
        android.media.ExifInterface.TAG_METERING_MODE,
        android.media.ExifInterface.TAG_EXPOSURE_PROGRAM,
        android.media.ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        android.media.ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        android.media.ExifInterface.TAG_LIGHT_SOURCE
    )
    return try {
        val exif = android.media.ExifInterface(java.io.ByteArrayInputStream(jpegBytes))
        buildMap {
            for (tag in tags) {
                val value = exif.getAttribute(tag)
                if (!value.isNullOrBlank()) {
                    put(tag, value)
                }
            }
        }
    } catch (_: Throwable) {
        emptyMap()
    }
}

/**
 * Captures an image from [LifecycleCameraController] at native full sensor resolution while preserving
 * original hardware JPEG detail and optical/exposure EXIF metadata.
 *
 * - Runs JPEG/ImageProxy decoding, EXIF extraction, orientation normalization, and optional front-camera
 *   mirroring strictly off the Main Thread via [captureDecodeExecutor] so the viewfinder never stalls.
 * - Preserves 100% of the complete native sensor frame (ZERO hidden aspect-ratio crop here); any selected
 *   [com.example.model.CameraCropMode] is applied once in the unified capture pipeline before overlays.
 */
fun capturePhoto(
    controller: LifecycleCameraController,
    context: Context,
    deviceRotationDegrees: Int = 0,
    isLandscape: Boolean = false,
    photoAspectRatio: String = "Full",
    isFrontCamera: Boolean = false,
    frontCameraMirror: Boolean = false,
    playShutterSound: Boolean = false,
    onSuccess: (Bitmap) -> Unit = {},
    onSuccessWithExif: ((Bitmap, Map<String, String>) -> Unit)? = null,
    onError: (Throwable) -> Unit
) {
    val mainExecutor = ContextCompat.getMainExecutor(context)

    if (controller.cameraInfo == null) {
        mainExecutor.execute { onError(IllegalStateException("Camera is still initializing. Please try again.")) }
        return
    }

    if (!controller.isImageCaptureEnabled) {
        try {
            val mask = if (controller.isVideoCaptureEnabled) {
                LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
            } else {
                LifecycleCameraController.IMAGE_CAPTURE
            }
            controller.setEnabledUseCases(mask)
        } catch (e: Throwable) {
            mainExecutor.execute { onError(IllegalStateException("Photo capture use case is unavailable: ${e.message}", e)) }
            return
        }
    }

    if (playShutterSound) {
        try {
            android.media.MediaActionSound().apply {
                play(android.media.MediaActionSound.SHUTTER_CLICK)
            }
        } catch (ignored: Throwable) {}
    }

    fun executeTakePicture(allowFallbackRetry: Boolean) {
        try {
            controller.takePicture(
                captureDecodeExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        var rawBitmap: Bitmap? = null
                        var rotationDegrees = 0
                        var extractedExif: Map<String, String> = emptyMap()
                        try {
                            rotationDegrees = image.imageInfo.rotationDegrees
                            if (image.format == android.graphics.ImageFormat.JPEG && image.planes.isNotEmpty()) {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                extractedExif = extractCameraExifMetadata(bytes)
                                rawBitmap = try {
                                    val fullOpts = BitmapFactory.Options().apply {
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                        inScaled = false
                                    }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, fullOpts)
                                } catch (_: OutOfMemoryError) {
                                    try {
                                        val safeOpts = BitmapFactory.Options().apply {
                                            inPreferredConfig = Bitmap.Config.ARGB_8888
                                            inSampleSize = 2
                                        }
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, safeOpts)
                                    } catch (_: Throwable) {
                                        null
                                    }
                                }
                            }
                            if (rawBitmap == null) {
                                rawBitmap = try {
                                    image.toBitmap()
                                } catch (_: Throwable) {
                                    null
                                }
                            }
                        } catch (t: Throwable) {
                            mainExecutor.execute { onError(t) }
                            return
                        } finally {
                            // Always release ImageProxy immediately so camera HAL buffer queue never stalls
                            try { image.close() } catch (ignored: Throwable) {}
                        }

                        if (rawBitmap == null) {
                            mainExecutor.execute { onError(IllegalStateException("Failed to decode captured camera frame")) }
                            return
                        }

                        try {
                            val normalizedBitmap = normalizeBitmapOrientation(
                                rawBitmap = rawBitmap,
                                cameraXRotationDegrees = rotationDegrees,
                                isLandscapeMode = isLandscape,
                                deviceRotationDegrees = deviceRotationDegrees
                            )
                            if (normalizedBitmap !== rawBitmap && !rawBitmap.isRecycled) {
                                try { rawBitmap.recycle() } catch (ignored: Throwable) {}
                            }

                            val finalBitmap = if (isFrontCamera && frontCameraMirror) {
                                val mirrorMatrix = Matrix().apply {
                                    postScale(-1f, 1f, normalizedBitmap.width / 2f, normalizedBitmap.height / 2f)
                                }
                                val mirrored = Bitmap.createBitmap(
                                    normalizedBitmap,
                                    0,
                                    0,
                                    normalizedBitmap.width,
                                    normalizedBitmap.height,
                                    mirrorMatrix,
                                    true
                                )
                                if (mirrored !== normalizedBitmap && !normalizedBitmap.isRecycled) {
                                    try { normalizedBitmap.recycle() } catch (ignored: Throwable) {}
                                }
                                mirrored
                            } else {
                                normalizedBitmap
                            }

                            if (onSuccessWithExif != null) {
                                onSuccessWithExif(finalBitmap, extractedExif)
                            } else {
                                onSuccess(finalBitmap)
                            }
                        } catch (t: Throwable) {
                            if (!rawBitmap.isRecycled) {
                                try { rawBitmap.recycle() } catch (ignored: Throwable) {}
                            }
                            mainExecutor.execute { onError(t) }
                        }
                    }

                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                        if (allowFallbackRetry &&
                            controller.imageCaptureMode != ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        ) {
                            Log.w(
                                "CameraPreview",
                                "takePicture failed in mode=${controller.imageCaptureMode} (${exception.imageCaptureError}), retrying with MINIMIZE_LATENCY"
                            )
                            mainExecutor.execute {
                                try {
                                    controller.imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                                    executeTakePicture(allowFallbackRetry = false)
                                } catch (retryErr: Throwable) {
                                    onError(retryErr)
                                }
                            }
                        } else {
                            mainExecutor.execute { onError(exception) }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            mainExecutor.execute { onError(t) }
        }
    }

    executeTakePicture(allowFallbackRetry = true)
}

/**
 * Silently checks whether the bound camera sensor exposes at least one hardware
 * CamcorderProfile / EncoderProfiles entry before querying CameraX [QualitySelector]
 * or binding [LifecycleCameraController.VIDEO_CAPTURE].
 *
 * Prevents CameraX's internal `CapabilitiesByQuality` from logging repeated
 * `No supported EncoderProfiles` errors on emulators or virtual camera HALs.
 */
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
fun hasSupportedHardwareVideoProfiles(cameraInfo: androidx.camera.core.CameraInfo?): Boolean {
    if (cameraInfo == null) return false
    val cameraId = try {
        androidx.camera.camera2.interop.Camera2CameraInfo.from(cameraInfo).cameraId
    } catch (_: Throwable) {
        return true
    }

    val qualities = intArrayOf(
        CamcorderProfile.QUALITY_HIGH,
        CamcorderProfile.QUALITY_1080P,
        CamcorderProfile.QUALITY_720P,
        CamcorderProfile.QUALITY_480P,
        CamcorderProfile.QUALITY_2160P,
        CamcorderProfile.QUALITY_LOW
    )

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        for (q in qualities) {
            try {
                val profiles = CamcorderProfile.getAll(cameraId, q)
                if (profiles != null && profiles.videoProfiles.isNotEmpty()) {
                    return true
                }
            } catch (_: Throwable) {
                // Probe remaining profiles silently
            }
        }
    }

    val numericId = cameraId.toIntOrNull()
    if (numericId != null) {
        for (q in qualities) {
            try {
                if (CamcorderProfile.hasProfile(numericId, q)) {
                    return true
                }
            } catch (_: Throwable) {
                // Probe remaining profiles silently
            }
        }
    }

    return false
}

/**
 * Safely constructs a QualitySelector with ordered fallback to prevent
 * "No supported EncoderProfiles" errors on emulators and unsupported devices.
 */
fun buildSafeQualitySelector(
    videoResolution: String = "1920 x 1080",
    cameraInfo: androidx.camera.core.CameraInfo? = null
): androidx.camera.video.QualitySelector? {
    if (!hasSupportedHardwareVideoProfiles(cameraInfo)) {
        return null
    }

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
    val hasSupportedVideoEncoderProfiles: Boolean
        get() = true
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

    override val hasSupportedVideoEncoderProfiles: Boolean
        get() = hasSupportedHardwareVideoProfiles(
            try { controller.cameraInfo } catch (ignored: Exception) { null }
        )

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
            if (controller.isVideoCaptureEnabled || !controller.isImageCaptureEnabled) {
                controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            }
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
        if (isRecordingActive()) {
            return false
        }

        adapter.awaitCameraInitialization(4000L)

        // Do not bind VIDEO_CAPTURE or create an OpenGL OverlayEffect when the active camera HAL
        // exposes no hardware EncoderProfiles (avoids CapabilitiesByQuality & EGL_emulation errors).
        if (enableVideo && !adapter.hasSupportedVideoEncoderProfiles) {
            if (adapter.isVideoCaptureEnabled) {
                adapter.clearEffects()
                adapter.enablePhotoOnlyUseCase()
            }
            lastOverlayEffectAttached = false
            appliedPipelineKey = VideoPipelineConfigKey(
                videoEnabled = false,
                videoResolution = "",
                isFrontCamera = isFrontCamera,
                overlayEffectIdentity = 0
            )
            return false
        }

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

            // 3. Enable dual IMAGE_CAPTURE | VIDEO_CAPTURE, with VIDEO_CAPTURE-only fallback in strict video mode.
            //    If the hardware HAL rejects OverlayEffect + VideoCapture stream combinations, retry cleanly
            //    without OverlayEffect so recording proceeds (VideoOverlayProcessor burns overlays in post-processing).
            var videoBound = adapter.enableVideoUseCases(includeImageCapture = true)
            if (!videoBound && strictVideoMode) {
                videoBound = adapter.enableVideoUseCases(includeImageCapture = false)
            }
            var finalEffectIdentity = if (overlayAttached) targetKey.overlayEffectIdentity else 0
            if (!videoBound && overlayAttached) {
                Log.w("VideoRecordingManager", "Video bind failed with OverlayEffect attached; retrying without OverlayEffect")
                adapter.clearEffects()
                lastOverlayEffectAttached = false
                finalEffectIdentity = 0
                videoBound = adapter.enableVideoUseCases(includeImageCapture = true)
                if (!videoBound && strictVideoMode) {
                    videoBound = adapter.enableVideoUseCases(includeImageCapture = false)
                }
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
                appliedPipelineKey = targetKey.copy(overlayEffectIdentity = finalEffectIdentity)
            }
            return true
        } else {
            adapter.clearEffects()
            overlayManager?.release()
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
            // Switch ImageCapture to MINIMIZE_LATENCY before binding VideoCapture so quick snapshots
            // during Landscape/Portrait video recording never stall or conflict with the video encoder.
            try {
                if (controller.imageCaptureMode != ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY) {
                    controller.imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                }
            } catch (_: Throwable) {}

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

            if (!adapter.hasSupportedVideoEncoderProfiles) {
                Log.w("VideoRecordingManager", "Camera reports no supported video EncoderProfiles")
                _sessionState.value = RecordingSessionState.IDLE
                onError(IllegalStateException("Video recording is not supported on this camera (no supported encoder profiles)."))
                return
            }

            if (rebound) {
                // Allow CameraX async unbind/rebind on main looper to transition previewStreamState
                // before inspecting awaitPreviewStreaming, preventing ERROR_SOURCE_INACTIVE on cold start.
                settleDelayFn(150L)
                if (cancelPrepareRequested) {
                    _sessionState.value = RecordingSessionState.IDLE
                    return
                }
            }

            val videoReady = adapter.isVideoCaptureEnabled && adapter.hasCameraInfo
            val streaming = if (videoReady) adapter.awaitPreviewStreaming(3000L) else false

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

            var finalizedDuringStart = false
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
                        finalizedDuringStart = true
                        _sessionState.value = RecordingSessionState.FINALIZING
                        activeRecording = null
                        overlayManager?.notifyRecordingStopped()
                        if (event.hasError()) {
                            Log.e(
                                "VideoRecordingManager",
                                "Video recording finalized with error: ${event.error}, cause: ${event.cause}"
                            )
                        }
                        _sessionState.value = RecordingSessionState.IDLE
                        onEvent(event, recFile, sessionHasOverlay)
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
            if (!finalizedDuringStart) {
                activeRecording = pendingRecording
                if (_sessionState.value == RecordingSessionState.PREPARING) {
                    _sessionState.value = RecordingSessionState.RECORDING
                }
            } else {
                activeRecording = null
                _sessionState.value = RecordingSessionState.IDLE
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
 * Dynamic viewfinder crop guide overlay mapped 1:1 to the `FIT_CENTER` live sensor viewport via
 * [com.example.camera.CameraOutputFormatEngine.computeCropGuideOverlayRect].
 *
 * Guarantees that the preview region inside the brackets matches the final cropped photo/video
 * identically across Portrait and Landscape orientations. In [com.example.model.CameraCropMode.FULL],
 * nothing is drawn so the full sensor frame remains 100% unobstructed corner to corner.
 */
@Composable
fun CameraCropModeGuideOverlay(
    cropMode: com.example.model.CameraCropMode,
    sensorLongShortRatio: Float,
    isLandscape: Boolean,
    containerWidth: Float,
    containerHeight: Float,
    modifier: Modifier = Modifier
) {
    if (cropMode == com.example.model.CameraCropMode.FULL) return
    val guide = com.example.camera.CameraOutputFormatEngine.computeCropGuideOverlayRect(
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        sensorLongShortRatio = sensorLongShortRatio,
        cropMode = cropMode,
        isLandscape = isLandscape
    )
    if (!guide.isCropped) return

    val animLeft by animateFloatAsState(
        targetValue = guide.leftPx,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "crop_left"
    )
    val animTop by animateFloatAsState(
        targetValue = guide.topPx,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "crop_top"
    )
    val animRight by animateFloatAsState(
        targetValue = guide.leftPx + guide.widthPx,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "crop_right"
    )
    val animBottom by animateFloatAsState(
        targetValue = guide.topPx + guide.heightPx,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "crop_bottom"
    )

    ComposeCanvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("camera_crop_guide_overlay")
    ) {
        val left = animLeft.coerceIn(0f, size.width)
        val top = animTop.coerceIn(0f, size.height)
        val right = animRight.coerceIn(left, size.width)
        val bottom = animBottom.coerceIn(top, size.height)
        val maskColor = Color.Black.copy(alpha = 0.48f)

        // Top mask
        if (top > 0.5f) {
            drawRect(
                color = maskColor,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, top)
            )
        }
        // Bottom mask
        if (size.height - bottom > 0.5f) {
            drawRect(
                color = maskColor,
                topLeft = Offset(0f, bottom),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - bottom)
            )
        }
        // Left mask (between top and bottom to prevent corner double-alpha)
        if (left > 0.5f) {
            drawRect(
                color = maskColor,
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(left, (bottom - top).coerceAtLeast(0f))
            )
        }
        // Right mask (between top and bottom to prevent corner double-alpha)
        if (size.width - right > 0.5f) {
            drawRect(
                color = maskColor,
                topLeft = Offset(right, top),
                size = androidx.compose.ui.geometry.Size(size.width - right, (bottom - top).coerceAtLeast(0f))
            )
        }

        // Subtle thin frame outline + crisp corner brackets matching exact saved boundaries
        val outlineStroke = 0.8.dp.toPx()
        drawRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
            style = Stroke(width = outlineStroke)
        )

        val bracketLen = 20.dp.toPx()
        val bracketStroke = 2.dp.toPx()
        val bracketColor = HighlightYellow.copy(alpha = 0.90f)

        // Top-left
        drawLine(bracketColor, Offset(left, top), Offset((left + bracketLen).coerceAtMost(right), top), bracketStroke)
        drawLine(bracketColor, Offset(left, top), Offset(left, (top + bracketLen).coerceAtMost(bottom)), bracketStroke)
        // Top-right
        drawLine(bracketColor, Offset(right, top), Offset((right - bracketLen).coerceAtLeast(left), top), bracketStroke)
        drawLine(bracketColor, Offset(right, top), Offset(right, (top + bracketLen).coerceAtMost(bottom)), bracketStroke)
        // Bottom-left
        drawLine(bracketColor, Offset(left, bottom), Offset((left + bracketLen).coerceAtMost(right), bottom), bracketStroke)
        drawLine(bracketColor, Offset(left, bottom), Offset(left, (bottom - bracketLen).coerceAtLeast(top)), bracketStroke)
        // Bottom-right
        drawLine(bracketColor, Offset(right, bottom), Offset((right - bracketLen).coerceAtLeast(left), bottom), bracketStroke)
        drawLine(bracketColor, Offset(right, bottom), Offset(right, (bottom - bracketLen).coerceAtLeast(top)), bracketStroke)
    }
}

/**
 * Legacy helper retained for backward compatibility with any callers referencing aspect-ratio strings.
 */
@Composable
fun CameraFramingGuideOverlay(
    aspectRatioStr: String,
    isLandscape: Boolean,
    containerWidth: Float,
    containerHeight: Float,
    modifier: Modifier = Modifier
) {
    val mappedMode = when (aspectRatioStr.lowercase(java.util.Locale.US)) {
        "1:1", "square", "square 1:1", "1:1 square" -> com.example.model.CameraCropMode.SQUARE_CROP
        "4:3", "standard", "16:9", "widescreen" -> com.example.model.CameraCropMode.RECTANGULAR_CROP
        else -> com.example.model.CameraCropMode.FULL
    }
    CameraCropModeGuideOverlay(
        cropMode = mappedMode,
        sensorLongShortRatio = 4f / 3f,
        isLandscape = isLandscape,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        modifier = modifier
    )
}

/**
 * Safely retrieves the exact Camera2 IDs that CameraX's ProcessCameraProvider can bind on this device.
 * Returns null if CameraX provider is not ready within the short timeout, allowing graceful fallback.
 */
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
private suspend fun queryBindableCameraXIds(context: Context): Set<String>? {
    return try {
        withTimeoutOrNull(800L) {
            suspendCancellableCoroutine { cont ->
                try {
                    val future = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        try {
                            val provider = future.get()
                            val ids = provider.availableCameraInfos.mapNotNull { info ->
                                try {
                                    androidx.camera.camera2.interop.Camera2CameraInfo.from(info).cameraId
                                } catch (_: Throwable) {
                                    null
                                }
                            }.toSet()
                            if (cont.isActive) cont.resume(ids.takeIf { it.isNotEmpty() })
                        } catch (_: Throwable) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }, ContextCompat.getMainExecutor(context))
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    } catch (_: Throwable) {
        null
    }
}

/**
 * Applies hardware zoom ratio to CameraX LifecycleCameraController and awaits completion of the
 * returned ListenableFuture so dropped/canceled calls during a lens or use-case rebind are detected
 * and retried cleanly.
 */
private suspend fun applyZoomRatioWithVerification(
    controller: LifecycleCameraController,
    context: Context,
    targetHardwareZoom: Float
): Boolean {
    return try {
        val future = controller.setZoomRatio(targetHardwareZoom)
        withTimeoutOrNull(750L) {
            suspendCancellableCoroutine<Boolean> { cont ->
                future.addListener({
                    try {
                        future.get()
                        if (cont.isActive) cont.resume(true)
                    } catch (e: Throwable) {
                        Log.w("CameraPreview", "setZoomRatio($targetHardwareZoom) interrupted: ${e.message}")
                        if (cont.isActive) cont.resume(false)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        } ?: true
    } catch (e: Throwable) {
        Log.w("CameraPreview", "setZoomRatio($targetHardwareZoom) exception: ${e.message}")
        false
    }
}


