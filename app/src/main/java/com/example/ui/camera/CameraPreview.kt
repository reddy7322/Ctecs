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
import com.example.model.CaptureSnapshot
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

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
    rotationDegrees: Int = 0,
    isLandscape: Boolean = false,
    photoResolution: String = "1920 x 1080",
    videoResolution: String = "1920 x 1080",
    controller: LifecycleCameraController,
    currentSnapshotProvider: () -> CaptureSnapshot? = { null },
    overlayManager: com.example.overlay.RealtimeVideoOverlayManager? = null,
    onExposureChanged: ((Int) -> Unit)? = null,
    onZoomChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestSnapshotProvider by rememberUpdatedState(currentSnapshotProvider)

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

    // Right-Side Brightness Control Gesture State
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessDismissKey by remember { mutableIntStateOf(0) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }
    var currentEvIndex by remember(exposureIndex) { mutableIntStateOf(exposureIndex) }

    val exposureState = controller.cameraInfo?.exposureState
    val isEvSupported = exposureState?.isExposureCompensationSupported ?: false
    val evRange = exposureState?.exposureCompensationRange ?: android.util.Range(-4, 4)
    // Ensure minEv and maxEv have a valid range even if hardware reports 0..0
    val minEv = if (isEvSupported && evRange.lower < evRange.upper) evRange.lower else -4
    val maxEv = if (isEvSupported && evRange.lower < evRange.upper) evRange.upper else 4
    val evStepRational = exposureState?.exposureCompensationStep ?: android.util.Rational(1, 1)
    val evStep = if (evStepRational.denominator != 0 && evStepRational.numerator != 0) evStepRational.toFloat() else 1f

    LaunchedEffect(brightnessDismissKey, isDraggingBrightness) {
        if (!isDraggingBrightness && showBrightnessIndicator) {
            delay(1500L)
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

            // Enhanced focus with AF (Autofocus), AE (Auto Exposure), and AWB (Auto White Balance)
            val focusAction = FocusMeteringAction.Builder(
                meteringPoint,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
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

    fun updateBrightness(deltaDrag: Float, dragStepPx: Float) {
        accumulatedDragY += -deltaDrag // Dragging UP (negative deltaDrag) increases brightness

        var changed = false
        var targetEv = currentEvIndex

        while (accumulatedDragY >= dragStepPx) {
            if (targetEv < maxEv) {
                targetEv++
                changed = true
            }
            accumulatedDragY -= dragStepPx
        }

        while (accumulatedDragY <= -dragStepPx) {
            if (targetEv > minEv) {
                targetEv--
                changed = true
            }
            accumulatedDragY += dragStepPx
        }

        if (changed) {
            currentEvIndex = targetEv
            try {
                if (isEvSupported && evRange.lower < evRange.upper) {
                    controller.cameraControl?.setExposureCompensationIndex(targetEv)
                }
            } catch (ignored: Exception) {}
            onExposureChanged?.invoke(targetEv)
        }
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

    // Refresh permission status when activity resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Bind to Lifecycle ONLY when camera permission is granted
    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        if (hasCameraPermission) {
            try {
                controller.bindToLifecycle(lifecycleOwner)
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to bind camera controller to lifecycle", e)
            }
        }
        onDispose {
            try {
                controller.unbind()
            } catch (ignored: Exception) {}
        }
    }

    // Configure Use Cases: In photo mode, only IMAGE_CAPTURE is active.
    // When isVideoMode is toggled to true, VIDEO_CAPTURE is enabled and
    // real-time video overlay effect is attached.
    LaunchedEffect(hasCameraPermission, isVideoMode, overlayManager) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            controller.isPinchToZoomEnabled = false
            controller.isTapToFocusEnabled = false

            if (isVideoMode) {
                try {
                    controller.setEnabledUseCases(
                        LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
                    )
                } catch (e: Throwable) {
                    try {
                        controller.setEnabledUseCases(LifecycleCameraController.VIDEO_CAPTURE)
                    } catch (ignored: Exception) {}
                }

                // Attach real-time video overlay effect directly to CameraX pipeline
                val effect = overlayManager?.getOrCreateEffect()
                if (effect != null) {
                    try {
                        controller.setEffects(setOf(effect))
                    } catch (e: Exception) {
                        Log.w("CameraPreview", "Failed to attach real-time video overlay effect: ${e.message}", e)
                    }
                }
            } else {
                try {
                    controller.clearEffects()
                } catch (ignored: Exception) {}
                try {
                    controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
                } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            Log.w("CameraPreview", "Setting use cases fallback", e)
        }
    }

    // Safe Camera Selector
    LaunchedEffect(isFrontCamera, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val targetSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            if (controller.hasCamera(targetSelector)) {
                controller.cameraSelector = targetSelector
            } else if (controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            } else if (controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            }
        } catch (e: Exception) {
            Log.w("CameraPreview", "Camera selector error", e)
        }
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

    LaunchedEffect(exposureIndex) {
        currentEvIndex = exposureIndex
        try {
            if (isEvSupported && evRange.lower < evRange.upper) {
                controller.cameraControl?.setExposureCompensationIndex(exposureIndex)
            }
        } catch (ignored: Exception) {}
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

    LaunchedEffect(videoResolution, isVideoMode, hasCameraPermission) {
        if (!hasCameraPermission || !isVideoMode) return@LaunchedEffect
        try {
            val safeSelector = buildSafeQualitySelector(videoResolution, controller.cameraInfo)
            if (safeSelector != null) {
                controller.videoCaptureQualitySelector = safeSelector
            }
        } catch (e: Exception) {
            Log.w("CameraPreview", "Setting video quality error", e)
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
                        }.also { previewViewInstance = it }
                    },
                    update = { pv ->
                        pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewViewInstance = pv
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 1b. Real-time Visual Brightness Feedback Overlay
                // Ensures immediate, smooth, and distinct visual brightness change on any device or emulator
                val visualEv = currentEvIndex.coerceIn(minEv, maxEv)
                if (visualEv > 0) {
                    val alpha = (visualEv.toFloat() / maxOf(1, maxEv).toFloat()) * 0.38f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = alpha))
                    )
                } else if (visualEv < 0) {
                    val alpha = (kotlin.math.abs(visualEv).toFloat() / maxOf(1, kotlin.math.abs(minEv)).toFloat()) * 0.45f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = alpha))
                    )
                }

                fun resetBrightness() {
                    currentEvIndex = 0
                    accumulatedDragY = 0f
                    try {
                        if (isEvSupported && evRange.lower < evRange.upper) {
                            controller.cameraControl?.setExposureCompensationIndex(0)
                        }
                    } catch (ignored: Exception) {}
                    onExposureChanged?.invoke(0)
                }

                // 2. Gesture Detector Layer for Pinch-to-Zoom, Touch-to-Focus & Right-Side Brightness Drag
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(minEv, maxEv, isLandscape, minZoomRatio, maxZoomRatio) {
                            val touchSlop = viewConfiguration.touchSlop * 0.65f
                            val dragStepPx = 18.dp.toPx()

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startPos = down.position
                                val inRightZone = startPos.x in rightZoneStart..rightZoneEnd

                                var isDraggingEv = false
                                var isPinchActive = false
                                var hasMovedPastSlop = false
                                var lastY = startPos.y
                                var previousSpan = -1f
                                var activeZoom = zoomRatio

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedPointers = event.changes.filter { it.pressed }

                                    if (pressedPointers.size >= 2) {
                                        // Two-finger pinch to zoom!
                                        isPinchActive = true
                                        hasMovedPastSlop = true

                                        if (isDraggingEv) {
                                            isDraggingEv = false
                                            isDraggingBrightness = false
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

                                        if (!isDraggingEv) {
                                            if (inRightZone && kotlin.math.abs(totalDistY) > touchSlop && kotlin.math.abs(totalDistY) > kotlin.math.abs(totalDistX)) {
                                                isDraggingEv = true
                                                isDraggingBrightness = true
                                                showBrightnessIndicator = true
                                                change.consume()
                                                updateBrightness(totalDistY, dragStepPx)
                                                lastY = currentPos.y
                                            }
                                        } else {
                                            change.consume()
                                            updateBrightness(deltaY, dragStepPx)
                                            lastY = currentPos.y
                                        }
                                    }

                                    if (pressedPointers.isEmpty()) {
                                        // All fingers lifted
                                        if (!isDraggingEv && !hasMovedPastSlop && !isPinchActive) {
                                            // Tap detected! Focus at exact touched position
                                            triggerTouchToFocus(startPos, constraints.maxWidth, constraints.maxHeight)
                                        } else if (isDraggingEv) {
                                            isDraggingBrightness = false
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

                // 4. Right-Side Brightness Control Indicator
                CameraBrightnessIndicator(
                    visible = showBrightnessIndicator,
                    currentEv = currentEvIndex,
                    minEv = minEv,
                    maxEv = maxEv,
                    step = evStep,
                    onReset = { resetBrightness() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = if (isLandscape) 104.dp else 16.dp)
                )
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
 * Minimalist Glass Brightness & Exposure indicator shown on the right side of preview
 * when user touches and drags vertically to adjust camera hardware exposure compensation.
 */
@Composable
fun CameraBrightnessIndicator(
    visible: Boolean,
    currentEv: Int,
    minEv: Int,
    maxEv: Int,
    step: Float,
    onReset: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(180)),
        exit = fadeOut(tween(280)),
        modifier = modifier
    ) {
        val totalRange = (maxEv - minEv).coerceAtLeast(1)
        val progress = ((currentEv - minEv).toFloat() / totalRange.toFloat()).coerceIn(0f, 1f)
        val calculatedEv = currentEv * step
        val evText = if (calculatedEv > 0f) {
            String.format(Locale.US, "+%.1f", calculatedEv)
        } else if (calculatedEv < 0f) {
            String.format(Locale.US, "%.1f", calculatedEv)
        } else {
            "0.0"
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xD0141414),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (currentEv != 0) HighlightYellow else Color.White.copy(alpha = 0.25f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .width(44.dp)
                .height(168.dp)
                .then(
                    if (currentEv != 0 && onReset != null) {
                        Modifier.clickable { onReset() }
                    } else Modifier
                )
                .testTag("camera_brightness_indicator")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp, horizontal = 6.dp)
            ) {
                // Top: Sun Icon
                val sunTint = when {
                    currentEv > 0 -> HighlightYellow
                    currentEv < 0 -> Color(0xFF94A3B8)
                    else -> Color.White
                }
                Icon(
                    imageVector = Icons.Default.LightMode,
                    contentDescription = "Brightness",
                    tint = sunTint,
                    modifier = Modifier.size(20.dp)
                )

                // Middle: Vertical Fill Slider Track
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(progress)
                            .clip(RoundedCornerShape(2.dp))
                            .background(HighlightYellow)
                    )
                }

                // Bottom: Numerical Readout
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = evText,
                        color = if (currentEv != 0) HighlightYellow else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (currentEv != 0) "RESET" else "EV",
                        color = if (currentEv != 0) HighlightYellow else SecondaryText,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
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
 * Captures image from LifecycleCameraController and returns a correctly rotated Bitmap
 * that strictly adheres to the physical device orientation and original aspect ratio.
 */
fun capturePhoto(
    controller: LifecycleCameraController,
    context: Context,
    deviceRotationDegrees: Int = 0,
    isLandscape: Boolean = false,
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

                    image.close()
                    onSuccess(normalizedBitmap)
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
 * Manages video recording with start/pause/resume/stop lifecycle
 */
class VideoRecordingManager(private val context: Context) {
    private var activeRecording: Recording? = null
    var currentRecordingFile: File? = null
        private set
    var overlayManager: com.example.overlay.RealtimeVideoOverlayManager? = null

    fun isRecordingActive(): Boolean = activeRecording != null

    fun startRecording(
        controller: LifecycleCameraController,
        recordAudio: Boolean,
        initialRotation: Int = 0,
        onEvent: (VideoRecordEvent, File?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            if (!controller.isVideoCaptureEnabled) {
                try {
                    controller.setEnabledUseCases(
                        LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
                    )
                } catch (e: Exception) {
                    try {
                        controller.setEnabledUseCases(LifecycleCameraController.VIDEO_CAPTURE)
                    } catch (e2: Exception) {
                        Log.w("VideoRecordingManager", "VIDEO_CAPTURE usecase enable failed: ${e2.message}")
                    }
                }
            }

            overlayManager?.notifyRecordingStarted(initialRotation)

            val tempDir = File(context.cacheDir, "temp_video").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val tempFile = File(tempDir, "RAW_${timeStamp}.mp4")
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

            val fileOptions = FileOutputOptions.Builder(tempFile).build()

            val recordListener = androidx.core.util.Consumer<VideoRecordEvent> { event ->
                val recFile = currentRecordingFile
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                    overlayManager?.notifyRecordingStopped()
                    if (event.hasError()) {
                        Log.e("VideoRecordingManager", "Video recording finalized with error: ${event.error}, cause: ${event.cause}")
                    }
                }
                onEvent(event, recFile)
            }

            val pendingRecording = try {
                controller.startRecording(
                    fileOptions,
                    audioConfig,
                    executor,
                    recordListener
                )
            } catch (e: SecurityException) {
                Log.w("VideoRecordingManager", "Audio recording permission error, retrying without audio", e)
                controller.startRecording(
                    fileOptions,
                    AudioConfig.AUDIO_DISABLED,
                    executor,
                    recordListener
                )
            }
            activeRecording = pendingRecording
        } catch (e: Throwable) {
            activeRecording = null
            overlayManager?.notifyRecordingStopped()
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
        try {
            activeRecording?.stop()
        } catch (ignored: Exception) {}
        activeRecording = null
        overlayManager?.notifyRecordingStopped()
    }
}
