package com.example.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.content.res.Configuration
import androidx.camera.view.LifecycleCameraController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.ui.ToastType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import com.example.camera.CameraResolutionHelper
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause

import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.location.LocationStatus
import com.example.model.CameraGridMode
import com.example.model.CameraMode
import com.example.model.CaptureMode
import com.example.model.CaptureTimer
import com.example.model.Chainage
import com.example.model.ChainageSource
import com.example.model.LogoPosition
import com.example.model.OverlayConfig
import com.example.model.OverlayTemplate
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.SpeedUnit
import com.example.overlay.LogoProvider
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.theme.AccentRed
import com.example.ui.theme.BorderColor
import com.example.ui.theme.DarkYellowSurface
import com.example.ui.theme.DeepRed
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighlightYellow
import com.example.ui.theme.PrimaryBackground
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SurfaceDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun RoadMarkingIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    ComposeCanvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        // Left angled line \
        drawLine(
            color = tint,
            start = Offset(w * 0.28f, h * 0.15f),
            end = Offset(w * 0.10f, h * 0.85f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Center dashed line
        drawLine(
            color = tint.copy(alpha = 0.55f),
            start = Offset(w * 0.48f, h * 0.25f),
            end = Offset(w * 0.48f, h * 0.75f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Right angled line /
        drawLine(
            color = tint,
            start = Offset(w * 0.72f, h * 0.15f),
            end = Offset(w * 0.90f, h * 0.85f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CameraApertureIcon(modifier: Modifier = Modifier, tint: Color = Color.Black) {
    ComposeCanvas(modifier = modifier.size(28.dp)) {
        val r = size.minDimension / 2f
        val c = center
        val numBlades = 6
        for (i in 0 until numBlades) {
            val angle = i * (360f / numBlades)
            rotate(angle, pivot = c) {
                val bladePath = Path().apply {
                    moveTo(c.x + r * 0.22f, c.y - r * 0.75f)
                    lineTo(c.x + r * 0.82f, c.y - r * 0.42f)
                    lineTo(c.x + r * 0.35f, c.y + r * 0.18f)
                    close()
                }
                drawPath(bladePath, color = tint)
            }
        }
        drawCircle(
            color = tint,
            radius = r * 0.24f,
            center = c,
            style = Stroke(width = 1.8.dp.toPx())
        )
    }
}

@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    onNavigateToHome: () -> Unit = {},
    onNavigateToPhotos: () -> Unit,
    onNavigateToRoutes: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    com.example.util.KeepScreenOn(enabled = true, requesterId = "CameraScreen")

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()
    val controller = remember {
        LifecycleCameraController(context).apply {
            try {
                setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            } catch (ignored: Exception) {}
        }
    }
    val videoManager = remember { VideoRecordingManager(context) }
    val overlayManager = remember {
        com.example.overlay.RealtimeVideoOverlayManager(
            context = context.applicationContext,
            snapshotProvider = {
                val effRot = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && (viewModel.rotationDegrees.value == 0f || viewModel.rotationDegrees.value == 180f)) 90 else viewModel.rotationDegrees.value.toInt()
                viewModel.createCurrentSnapshot(effRot)
            },
            rotationProvider = {
                if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && (viewModel.rotationDegrees.value == 0f || viewModel.rotationDegrees.value == 180f)) 90 else viewModel.rotationDegrees.value.toInt()
            }
        )
    }

    DisposableEffect(overlayManager) {
        videoManager.overlayManager = overlayManager
        onDispose {
            overlayManager.release()
        }
    }

    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val isCapturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    val captureMode by viewModel.captureMode.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isRecordingPaused by viewModel.isRecordingPaused.collectAsStateWithLifecycle()
    val recordingDurationSec by viewModel.recordingDurationSeconds.collectAsStateWithLifecycle()
    val currentZoomRatio by viewModel.currentZoomRatio.collectAsStateWithLifecycle()
    val exposureCompensation by viewModel.exposureCompensation.collectAsStateWithLifecycle()
    val cameraSettings by viewModel.cameraSettings.collectAsStateWithLifecycle()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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

    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val matchResult by viewModel.cameraMatchResult.collectAsStateWithLifecycle()
    val manualChainageText by viewModel.manualChainageText.collectAsStateWithLifecycle()
    val savedCustomChainages by viewModel.savedCustomChainages.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()
    val overlayConfig by viewModel.overlayConfig.collectAsStateWithLifecycle()
    val currentLocationName by viewModel.currentLocationName.collectAsStateWithLifecycle()
    val lastPhoto by viewModel.lastCapturedPhoto.collectAsStateWithLifecycle()
    val showManualDialog by viewModel.showManualChainageDialog.collectAsStateWithLifecycle()

    // Real-time Compass & Device Orientation state
    val rotationDegrees by viewModel.rotationDegrees.collectAsStateWithLifecycle()
    val compassState by viewModel.compassState.collectAsStateWithLifecycle()
    val animatedRotation by animateFloatAsState(
        targetValue = rotationDegrees.toFloat(),
        animationSpec = tween(250),
        label = "icon_rotation"
    )

    var showSimDialog by remember { mutableStateOf(false) }
    var showActivityMenu by remember { mutableStateOf(false) }
    var showManualActivityDialog by remember { mutableStateOf(false) }
    var showExposureSlider by remember { mutableStateOf(false) }
    var showZoomSlider by remember { mutableStateOf(false) }
    var showQuickSettingsPopup by remember { mutableStateOf(false) }
    var showCustomCommentDialog by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    var isTopToolbarExpanded by remember { mutableStateOf(true) }
    var showLaneMenu by remember { mutableStateOf(false) }
    var isHdrEnabled by remember { mutableStateOf(false) }
    var activeOutputSettingsType by remember { mutableStateOf<String?>(null) }

    // Countdown Timer State
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var isCountingDown by remember { mutableStateOf(false) }

    // Live clock for HUD preview
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showQuickCaptureFlash by remember { mutableStateOf(false) }

    LaunchedEffect(showQuickCaptureFlash) {
        if (showQuickCaptureFlash) {
            delay(120L)
            showQuickCaptureFlash = false
        }
    }

    fun startVideoRecording(audioEnabled: Boolean) {
        viewModel.onVideoRecordingStarted()
        val effectiveRotation = if (isLandscape && (rotationDegrees == 0f || rotationDegrees == 180f)) 90 else rotationDegrees.toInt()
        videoManager.startRecording(
            controller = controller,
            recordAudio = audioEnabled,
            initialRotation = effectiveRotation,
            onEvent = { event, recFile ->
                when (event) {
                    is androidx.camera.video.VideoRecordEvent.Start -> {
                        viewModel.showInfo("Recording video...")
                    }
                    is androidx.camera.video.VideoRecordEvent.Pause -> {
                        viewModel.onVideoRecordingPaused(true)
                    }
                    is androidx.camera.video.VideoRecordEvent.Resume -> {
                        viewModel.onVideoRecordingPaused(false)
                    }
                    is androidx.camera.video.VideoRecordEvent.Finalize -> {
                        viewModel.onVideoRecordingStopped()
                        if (event.hasError()) {
                            android.util.Log.e("CameraScreen", "Video recording finalized with error: ${event.error}, cause: ${event.cause}")
                        }
                        val finalRecFile = recFile ?: videoManager.currentRecordingFile ?: File(context.cacheDir, "temp_video/raw_video.mp4")
                        viewModel.processRecordedVideo(context, finalRecFile, effectiveRotation)
                    }
                }
            },
            onError = { throwable ->
                android.util.Log.e("CameraScreen", "Video recording error: ${throwable.message}", throwable)
                viewModel.onVideoRecordingStopped()
                viewModel.showError(throwable.message ?: "Failed to start video recording")
            }
        )
    }

    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        startVideoRecording(isGranted && cameraSettings.recordAudio)
    }

    // Trigger Photo Capture (with or without countdown)
    fun triggerPhotoCapture() {
        if (isCapturing || isCountingDown) return
        val timerSec = cameraSettings.captureTimer.seconds
        if (timerSec > 0) {
            isCountingDown = true
            countdownRemaining = timerSec
            coroutineScope.launch {
                while (countdownRemaining > 0) {
                    delay(1000L)
                    countdownRemaining--
                }
                isCountingDown = false
                capturePhoto(
                    controller = controller,
                    context = context,
                    deviceRotationDegrees = rotationDegrees.toInt(),
                    isLandscape = isLandscape,
                    photoAspectRatio = cameraSettings.photoAspectRatio,
                    onSuccess = { bitmap -> viewModel.processCapture(bitmap) },
                    onError = {
                        val fallback = createSimulatedCameraFrame(
                            context = context,
                            rotationDegrees = rotationDegrees.toInt(),
                            isLandscapeMode = isLandscape,
                            resolutionStr = cameraSettings.photoResolution,
                            aspectRatioStr = cameraSettings.photoAspectRatio
                        )
                        viewModel.processCapture(fallback)
                    }
                )
            }
        } else {
            capturePhoto(
                controller = controller,
                context = context,
                deviceRotationDegrees = rotationDegrees.toInt(),
                isLandscape = isLandscape,
                photoAspectRatio = cameraSettings.photoAspectRatio,
                onSuccess = { bitmap -> viewModel.processCapture(bitmap) },
                onError = {
                    val fallback = createSimulatedCameraFrame(
                        context = context,
                        rotationDegrees = rotationDegrees.toInt(),
                        isLandscapeMode = isLandscape,
                        resolutionStr = cameraSettings.photoResolution,
                        aspectRatioStr = cameraSettings.photoAspectRatio
                    )
                    viewModel.processCapture(fallback)
                }
            )
        }
    }

    // Trigger Video Recording Toggle
    fun toggleVideoRecording() {
        if (!isRecording) {
            val hasAudioPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (cameraSettings.recordAudio && !hasAudioPerm) {
                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            } else {
                startVideoRecording(cameraSettings.recordAudio && hasAudioPerm)
            }
        } else {
            viewModel.showInfo("Saving video...")
            videoManager.stop()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(PrimaryBackground)) {
        // 1. CameraX Preview
        CameraPreview(
            isFrontCamera = isFrontCamera,
            isFlashOn = isFlashOn,
            zoomRatio = currentZoomRatio,
            exposureIndex = exposureCompensation,
            isVideoMode = (captureMode == CaptureMode.VIDEO),
            rotationDegrees = rotationDegrees.toInt(),
            isLandscape = isLandscape,
            photoResolution = cameraSettings.photoResolution,
            videoResolution = cameraSettings.videoResolution,
            photoAspectRatio = cameraSettings.photoAspectRatio,
            videoAspectRatio = cameraSettings.videoAspectRatio,
            controller = controller,
            currentSnapshotProvider = { viewModel.createCurrentSnapshot() },
            overlayManager = overlayManager,
            onExposureChanged = { newEv -> viewModel.setExposureCompensation(newEv) },
            onZoomChanged = { newZoom -> viewModel.setZoomRatio(newZoom) },
            modifier = Modifier.fillMaxSize()
        )

        // Visual flash feedback on capture (photo capture & quick photo during video)
        if (showQuickCaptureFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }

        // 2. Composition Grid Lines (Off, 3x3, 4x4)
        if (cameraSettings.gridMode != CameraGridMode.OFF) {
            val divisions = if (cameraSettings.gridMode == CameraGridMode.GRID_3X3) 3 else 4
            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 1.dp.toPx()
                val gridColor = Color.White.copy(alpha = 0.35f)
                val stepX = size.width / divisions
                val stepY = size.height / divisions

                for (i in 1 until divisions) {
                    drawLine(
                        color = gridColor,
                        start = Offset(stepX * i, 0f),
                        end = Offset(stepX * i, size.height),
                        strokeWidth = strokeW
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, stepY * i),
                        end = Offset(size.width, stepY * i),
                        strokeWidth = strokeW
                    )
                }
            }
        }

        // 3. Electronic Horizon Level Indicator
        if (cameraSettings.showHorizonLevel) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(160.dp)
                    .height(2.dp)
                    .background(HighlightYellow.copy(alpha = 0.85f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(HighlightYellow)
            )
        }

        // 4. Live Custom Logo Overlay on Viewfinder
        if (overlayConfig.showLogo && overlayConfig.logoPosition != LogoPosition.ABOVE_TEXT) {
            val alignment = when (overlayConfig.logoPosition) {
                LogoPosition.TOP_RIGHT -> Alignment.TopEnd
                LogoPosition.TOP_LEFT -> Alignment.TopStart
                LogoPosition.BOTTOM_LEFT -> Alignment.BottomStart
                LogoPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                else -> Alignment.TopEnd
            }
            val isTop = overlayConfig.logoPosition == LogoPosition.TOP_RIGHT || overlayConfig.logoPosition == LogoPosition.TOP_LEFT
            val padTop = if (isTop) (overlayConfig.logoMarginYDp + 70).dp else 0.dp
            val padBottom = if (!isTop) (overlayConfig.logoMarginYDp + 210).dp else 0.dp
            val padStart = if (overlayConfig.logoPosition == LogoPosition.TOP_LEFT || overlayConfig.logoPosition == LogoPosition.BOTTOM_LEFT) overlayConfig.logoMarginXDp.dp else 0.dp
            val padEnd = if (overlayConfig.logoPosition == LogoPosition.TOP_RIGHT || overlayConfig.logoPosition == LogoPosition.BOTTOM_RIGHT) overlayConfig.logoMarginXDp.dp else 0.dp

            LiveLogoOverlay(
                config = overlayConfig,
                modifier = Modifier
                    .align(alignment)
                    .padding(start = padStart, end = padEnd, top = padTop, bottom = padBottom)
            )
        }

        // 5. Center Countdown Timer Overlay
        if (isCountingDown && countdownRemaining > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x55000000)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark.copy(alpha = 0.90f))
                        .border(4.dp, HighlightYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$countdownRemaining",
                        color = HighlightYellow,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        if (isLandscape) {
            // ==========================================================
            // LANDSCAPE CAMERA CONTROLS: SMART MINIMALIST GLASS UI
            // Full transparent background (Color.Transparent) for all elements & buttons
            // ==========================================================
            val glassBorderIdle = Color.White.copy(alpha = 0.38f)

            // 1. TOP BAR: Collapsible Smart Minimalist Glass Toolbar (Left) & Status Checkmark (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Chevron Toggle + Expandable Minimalist Glass Toolbar Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Smart Toolbar Show/Hide Toggle Button (Full Transparent Glass)
                    IconButton(
                        onClick = { isTopToolbarExpanded = !isTopToolbarExpanded },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(0.8.dp, glassBorderIdle, CircleShape)
                            .testTag("landscape_toolbar_toggle")
                    ) {
                        Icon(
                            imageVector = if (isTopToolbarExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isTopToolbarExpanded) "Hide Camera Controls" else "Show Camera Controls",
                            tint = PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Expandable Smart Camera Secondary Controls (Full Transparent Glass)
                    AnimatedVisibility(
                        visible = isTopToolbarExpanded,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Flash Toggle Button
                            IconButton(
                                onClick = { viewModel.toggleFlash() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(
                                        0.8.dp,
                                        if (isFlashOn) HighlightYellow else glassBorderIdle,
                                        CircleShape
                                    )
                                    .testTag("landscape_flash_button")
                            ) {
                                Icon(
                                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Toggle Flash",
                                    tint = if (isFlashOn) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 2. HDR Toggle Button
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(
                                        0.8.dp,
                                        if (isHdrEnabled) HighlightYellow else glassBorderIdle,
                                        CircleShape
                                    )
                                    .clickable {
                                        isHdrEnabled = !isHdrEnabled
                                        viewModel.showToast(if (isHdrEnabled) "HDR Mode Active" else "HDR Mode Disabled", ToastType.SETTINGS_SAVED)
                                    }
                                    .testTag("landscape_hdr_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.8.dp,
                                        if (isHdrEnabled) HighlightYellow else Color.White.copy(alpha = 0.75f)
                                    )
                                ) {
                                    Text(
                                        text = "HDR",
                                        color = if (isHdrEnabled) HighlightYellow else PrimaryText,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                    )
                                }
                            }

                            // 3. Grid Toggle Button
                            IconButton(
                                onClick = { viewModel.cycleGrid() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(
                                        0.8.dp,
                                        if (cameraSettings.gridMode != CameraGridMode.OFF) HighlightYellow else glassBorderIdle,
                                        CircleShape
                                    )
                                    .testTag("landscape_grid_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = "Grid: ${cameraSettings.gridMode.label}",
                                    tint = if (cameraSettings.gridMode != CameraGridMode.OFF) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 4. Smart Photo Output Button (Full Transparent Glass Pill)
                            val photoBadgeLandscape = CameraResolutionHelper.getPhotoBadgeText(
                                cameraSettings.photoResolution,
                                cameraSettings.photoAspectRatio
                            )
                            Surface(
                                shape = RoundedCornerShape(19.dp),
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    0.8.dp,
                                    if (activeOutputSettingsType == "PHOTO") HighlightYellow else glassBorderIdle
                                ),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .clickable {
                                        activeOutputSettingsType = if (activeOutputSettingsType == "PHOTO") null else "PHOTO"
                                    }
                                    .testTag("landscape_photo_settings_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Photo Output",
                                        tint = if (activeOutputSettingsType == "PHOTO") HighlightYellow else PrimaryText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = photoBadgeLandscape,
                                        color = if (activeOutputSettingsType == "PHOTO") HighlightYellow else PrimaryText,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = if (activeOutputSettingsType == "PHOTO") HighlightYellow else SecondaryText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            // 5. Smart Video Output Button (Full Transparent Glass Pill)
                            val videoBadgeLandscape = CameraResolutionHelper.getVideoBadgeText(
                                cameraSettings.videoResolution,
                                cameraSettings.videoAspectRatio
                            )
                            Surface(
                                shape = RoundedCornerShape(19.dp),
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    0.8.dp,
                                    if (activeOutputSettingsType == "VIDEO") AccentRed else glassBorderIdle
                                ),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .clickable {
                                        activeOutputSettingsType = if (activeOutputSettingsType == "VIDEO") null else "VIDEO"
                                    }
                                    .testTag("landscape_video_settings_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Video Output",
                                        tint = if (activeOutputSettingsType == "VIDEO") AccentRed else PrimaryText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = videoBadgeLandscape,
                                        color = if (activeOutputSettingsType == "VIDEO") AccentRed else PrimaryText,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = if (activeOutputSettingsType == "VIDEO") AccentRed else SecondaryText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            // 6. Timer Cycle Button
                            IconButton(
                                onClick = { viewModel.cycleTimer() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(
                                        0.8.dp,
                                        if (cameraSettings.captureTimer != CaptureTimer.OFF) HighlightYellow else glassBorderIdle,
                                        CircleShape
                                    )
                                    .testTag("landscape_timer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Timer: ${cameraSettings.captureTimer.label}",
                                    tint = if (cameraSettings.captureTimer != CaptureTimer.OFF) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 7. Focus Frame / Horizon Leveler Toggle Button
                            IconButton(
                                onClick = {
                                    val nextLevel = !cameraSettings.showHorizonLevel
                                    viewModel.saveCameraSettingsSilent(cameraSettings.copy(showHorizonLevel = nextLevel))
                                    viewModel.showToast(if (nextLevel) "Horizon level on" else "Horizon level off", ToastType.SETTINGS_SAVED)
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(
                                        0.8.dp,
                                        if (cameraSettings.showHorizonLevel) HighlightYellow else glassBorderIdle,
                                        CircleShape
                                    )
                                    .testTag("landscape_level_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterCenterFocus,
                                    contentDescription = "Horizon Leveler",
                                    tint = if (cameraSettings.showHorizonLevel) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 8. Settings Gear Button
                            IconButton(
                                onClick = { showQuickSettingsPopup = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(0.8.dp, glassBorderIdle, CircleShape)
                                    .testTag("landscape_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Camera Settings",
                                    tint = PrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Center if recording: Minimalist Transparent Glass Recording Pill
                if (isRecording) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rec")
                    val recAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(tween(500), repeatMode = RepeatMode.Reverse),
                        label = "recording_pulse"
                    )
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(0.9.dp, AccentRed)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AccentRed)
                                    .alpha(recAlpha)
                            )
                            val mins = recordingDurationSec / 60
                            val secs = recordingDurationSec % 60
                            Text(
                                text = String.format(Locale.US, "REC %02d:%02d", mins, secs),
                                color = PrimaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Right: Minimalist Transparent Glass Alignment Status Checkmark Button
                Box(
                    modifier = Modifier
                        .padding(end = 92.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(0.8.dp, HighlightYellow.copy(alpha = 0.75f), CircleShape)
                        .clickable {
                            viewModel.showToast("Road Alignment: ${activeRoute?.name ?: "Active"}", ToastType.INFO)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Status OK",
                        tint = HighlightYellow,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // 2. LEFT CORNER: HIGHWAY-RELATED MINIMALIST GLASS CONTROLS (Full Transparent Background)
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 54.dp, start = 14.dp, bottom = 12.dp)
                    .width(150.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Control 1: Minimalist Transparent Glass MiniMap Preview Card
                MiniMapPreview(
                    location = locationState.point,
                    bearing = locationState.bearing,
                    routeData = activeRoute,
                    highwayTag = "",
                    gpsAccuracy = null,
                    showGpsBadge = false,
                    showProjectTag = false,
                    showAttribution = false,
                    transparentBackground = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .clickable { showSimDialog = true }
                        .testTag("landscape_minimap_preview")
                )

                // Control 2: LHS / RHS Segmented Pill (Full Transparent Glass)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, glassBorderIdle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(33.dp)
                        .testTag("landscape_side_pill")
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val isLhs = projectProfile.roadSide == RoadSide.LHS
                        val isRhs = projectProfile.roadSide == RoadSide.RHS

                        // LHS Segment
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                                .background(Color.Transparent)
                                .clickable { viewModel.updateSide(RoadSide.LHS) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isLhs) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(HighlightYellow)
                                    )
                                }
                                Text(
                                    text = "LHS",
                                    color = if (isLhs) HighlightYellow else PrimaryText,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isLhs) FontWeight.Black else FontWeight.Medium
                                )
                            }
                        }

                        // Subtle Glass Divider line
                        Box(
                            modifier = Modifier
                                .width(0.8.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.25f))
                        )

                        // RHS Segment
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                                .background(Color.Transparent)
                                .clickable { viewModel.updateSide(RoadSide.RHS) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isRhs) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(HighlightYellow)
                                    )
                                }
                                Text(
                                    text = "RHS",
                                    color = if (isRhs) HighlightYellow else PrimaryText,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isRhs) FontWeight.Black else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Control 3: Lane Selector Dropdown Pill (Full Transparent Glass)
                Box {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, glassBorderIdle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(33.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { showLaneMenu = true }
                            .testTag("landscape_lane_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                RoadMarkingIcon(tint = PrimaryText)
                                Text(
                                    text = projectProfile.lane.ifBlank { "Main CW" },
                                    color = PrimaryText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Lane",
                                tint = PrimaryText.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLaneMenu,
                        onDismissRequest = { showLaneMenu = false },
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, glassBorderIdle),
                        modifier = Modifier.background(Color.Transparent)
                    ) {
                        ProjectProfile.LANE_PRESETS.forEach { l ->
                            val isSel = projectProfile.lane.equals(l, ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = l,
                                        color = if (isSel) HighlightYellow else PrimaryText,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    viewModel.updateLane(l)
                                    showLaneMenu = false
                                }
                            )
                        }
                    }
                }

                // Control 4: Activity Selector Dropdown Pill (Full Transparent Glass)
                Box {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, glassBorderIdle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(33.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { showActivityMenu = true }
                            .testTag("landscape_activity_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = PrimaryText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = projectProfile.activity.ifBlank { "BC" },
                                    color = PrimaryText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Activity",
                                tint = PrimaryText.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showActivityMenu,
                        onDismissRequest = { showActivityMenu = false },
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, glassBorderIdle),
                        modifier = Modifier.background(Color.Transparent)
                    ) {
                        ProjectProfile.ACTIVITY_PRESETS.forEach { act ->
                            val isSel = projectProfile.activity.equals(act, ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = act,
                                        color = if (isSel) HighlightYellow else PrimaryText,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    viewModel.updateActivity(act)
                                    showActivityMenu = false
                                }
                            )
                        }
                        HorizontalDivider(color = glassBorderIdle)
                        DropdownMenuItem(
                            text = { Text("Custom / Manual...", color = HighlightYellow, fontWeight = FontWeight.Bold) },
                            onClick = {
                                showActivityMenu = false
                                showManualActivityDialog = true
                            }
                        )
                    }
                }

                // Control 5: Chainage Adjustment Pill (Full Transparent Glass)
                val isManualSource = matchResult.source == ChainageSource.MANUAL
                val isCustomCorrectionSource = matchResult.source == ChainageSource.CUSTOM_CORRECTION
                val sourceBorderColor = when (matchResult.source) {
                    ChainageSource.MANUAL -> AccentRed
                    ChainageSource.CUSTOM_CORRECTION -> Color(0xFFA855F7)
                    else -> glassBorderIdle
                }
                val sourcePrefix = when (matchResult.source) {
                    ChainageSource.MANUAL -> "MANUAL: "
                    ChainageSource.CUSTOM_CORRECTION -> "CUSTOM: "
                    ChainageSource.KMZ -> "KMZ: "
                    ChainageSource.GPS -> "GPS: "
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        sourceBorderColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(33.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { viewModel.openManualChainageDialog() }
                        .testTag("landscape_chainage_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            RoadMarkingIcon(
                                tint = when {
                                    isManualSource -> AccentRed
                                    isCustomCorrectionSource -> Color(0xFFC084FC)
                                    else -> PrimaryText
                                }
                            )
                            Text(
                                text = "$sourcePrefix${matchResult.displayChainage}",
                                color = PrimaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Chainage",
                            tint = PrimaryText.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Control 6: Comment Pill (Full Transparent Glass)
                val hasComment = projectProfile.customDescription.isNotBlank() && !projectProfile.customDescription.equals("None", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (hasComment) Color(0xFF38BDF8) else glassBorderIdle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(33.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            commentInput = projectProfile.customDescription
                            showCustomCommentDialog = true
                        }
                        .testTag("landscape_comment_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Custom Comment",
                            tint = if (hasComment) Color(0xFF38BDF8) else PrimaryText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (hasComment) projectProfile.customDescription else "+ Comment",
                            color = if (hasComment) Color(0xFF38BDF8) else PrimaryText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 3. EXPOSURE COMPENSATION SLIDER (Minimalist Full Transparent Glass)
            if (showExposureSlider) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, HighlightYellow.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 56.dp)
                        .width(320.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "EV: ${if (exposureCompensation > 0) "+$exposureCompensation" else "$exposureCompensation"}",
                            color = HighlightYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = exposureCompensation.toFloat(),
                            onValueChange = { viewModel.setExposureCompensation(it.toInt()) },
                            valueRange = -4f..4f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                thumbColor = HighlightYellow,
                                activeTrackColor = HighlightYellow,
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.setExposureCompensation(0) },
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Reset", color = HighlightYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3b. ON-SCREEN ZOOM CONTROLS (LANDSCAPE — Full Transparent Glass)
            CameraZoomControlBar(
                currentZoomRatio = currentZoomRatio,
                minZoomRatio = minZoomRatio,
                maxZoomRatio = maxZoomRatio,
                onZoomChanged = { newZoom -> viewModel.setZoomRatio(newZoom) },
                onResetZoom = { viewModel.setZoomRatio(1.0f) },
                isLandscape = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 12.dp)
            )

            // 4. BOTTOM-RIGHT TIMESTAMP & WATERMARK OVERLAY (Full Transparent Background)
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 94.dp, bottom = 12.dp)
            ) {
                // Custom Logo (if enabled)
                if (overlayConfig.showLogo) {
                    LiveLogoOverlay(
                        config = overlayConfig,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                val liveLines = com.example.overlay.OverlayTextGenerator.generateLines(
                    config = overlayConfig,
                    project = projectProfile,
                    location = locationState.point,
                    accuracy = locationState.accuracy,
                    compassHeading = compassState.displayHeading,
                    speedMps = locationState.speed,
                    bearing = locationState.bearing,
                    chainageStr = matchResult.displayChainage,
                    cameraMode = cameraSettings.cameraMode,
                    coordinateFormat = cameraSettings.coordinateFormat,
                    is24HourFormat = cameraSettings.is24HourFormat,
                    timestamp = currentTimeMillis
                )

                LiveViewfinderWatermarkBlock(
                    liveLines = liveLines,
                    overlayConfig = overlayConfig,
                    isLandscape = true
                )
            }

            // 5. RIGHT CORNER: MINIMALIST GLASS CAPTURE & RECORDING DOCK (Full Transparent Background)
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.28f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(80.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Gallery Recent Photo Shortcut (Top — Full Transparent Glass)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Transparent)
                            .border(0.8.dp, glassBorderIdle, RoundedCornerShape(12.dp))
                            .clickable { onNavigateToPhotos() }
                            .testTag("landscape_gallery_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (lastPhoto?.filePath != null && File(lastPhoto!!.filePath).exists()) {
                            AsyncImage(
                                model = File(lastPhoto!!.filePath),
                                contentDescription = "Recent Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = PrimaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 2. Camera Flip Button (Full Transparent Glass)
                    IconButton(
                        onClick = { viewModel.toggleCameraFacing() },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(0.8.dp, glassBorderIdle, CircleShape)
                            .testTag("landscape_camera_flip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera",
                            tint = PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. Main Shutter Button (Smart Minimalist Transparent Glass Ring + Aperture Icon)
                    if (isRecording) {
                        // Quick Photo Capture while Video Recording
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(1.dp, HighlightYellow, CircleShape)
                                .clickable {
                                    try {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    } catch (ignored: Exception) {}
                                    showQuickCaptureFlash = true
                                    capturePhoto(
                                        controller = controller,
                                        context = context,
                                        deviceRotationDegrees = rotationDegrees.toInt(),
                                        isLandscape = isLandscape,
                                        photoAspectRatio = cameraSettings.photoAspectRatio,
                                        onSuccess = { bitmap ->
                                            viewModel.processCapture(bitmap)
                                            viewModel.showSuccess("Quick photo saved to Gallery!")
                                        },
                                        onError = {
                                            val fallback = createSimulatedCameraFrame(
                                                context = context,
                                                rotationDegrees = rotationDegrees.toInt(),
                                                isLandscapeMode = isLandscape,
                                                resolutionStr = cameraSettings.photoResolution,
                                                aspectRatioStr = cameraSettings.photoAspectRatio
                                            )
                                            viewModel.processCapture(fallback)
                                            viewModel.showSuccess("Quick photo saved to Gallery!")
                                        }
                                    )
                                }
                                .testTag("landscape_quick_photo_capture"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "Quick Photo Capture",
                                tint = HighlightYellow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(0.9.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                                .clickable(enabled = !isCapturing && !isCountingDown) {
                                    if (captureMode != CaptureMode.PHOTO) {
                                        viewModel.setCaptureMode(CaptureMode.PHOTO)
                                    }
                                    triggerPhotoCapture()
                                }
                                .testTag("landscape_shutter_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    color = PrimaryText,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                CameraApertureIcon(tint = PrimaryText)
                            }
                        }
                    }

                    // 4. Video Recording Button (Minimalist Transparent Glass Circle)
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(1.5.dp, AccentRed, CircleShape)
                                .clickable { toggleVideoRecording() }
                                .testTag("landscape_stop_video_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentRed)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(
                                    0.9.dp,
                                    if (captureMode == CaptureMode.VIDEO) AccentRed else glassBorderIdle,
                                    CircleShape
                                )
                                .clickable {
                                    if (captureMode != CaptureMode.VIDEO) {
                                        viewModel.setCaptureMode(CaptureMode.VIDEO)
                                    }
                                    toggleVideoRecording()
                                }
                                .testTag("landscape_video_record_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(AccentRed)
                            )
                        }
                    }

                    // 5. Bottom Action: Pause/Resume if recording, else Exposure Tune Button (Full Transparent Glass)
                    if (isRecording) {
                        IconButton(
                            onClick = {
                                if (isRecordingPaused) {
                                    videoManager.resume()
                                    viewModel.onVideoRecordingPaused(false)
                                    viewModel.showInfo("Video recording resumed")
                                } else {
                                    videoManager.pause()
                                    viewModel.onVideoRecordingPaused(true)
                                    viewModel.showInfo("Video recording paused")
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(
                                    0.8.dp,
                                    if (isRecordingPaused) HighlightYellow else glassBorderIdle,
                                    CircleShape
                                )
                                .testTag("landscape_pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isRecordingPaused) "Resume Recording" else "Pause Recording",
                                tint = if (isRecordingPaused) HighlightYellow else PrimaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { showExposureSlider = !showExposureSlider },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(
                                    0.8.dp,
                                    if (showExposureSlider) HighlightYellow else glassBorderIdle,
                                    CircleShape
                                )
                                .testTag("landscape_tune_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Exposure / Settings",
                                tint = if (showExposureSlider) HighlightYellow else PrimaryText,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // 6. Modern Glassmorphism Top Bar & HUD (PORTRAIT)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                // Top Controls Toolbar: 3 Tiny Floating Glass Pills (8dp margin, 20dp icons, 4dp blur, 10% opacity)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pill 1: Flash, Audio Mic, Timer
                    FloatingGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0x1A101726),
                        borderColor = Color(0x35FFFFFF)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Flash cycle button
                            IconButton(
                                onClick = { viewModel.toggleFlash() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("flash_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Flash",
                                    tint = if (isFlashOn) HighlightYellow else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Audio mic toggle
                            IconButton(
                                onClick = { viewModel.toggleRecordAudio() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("audio_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (cameraSettings.recordAudio) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = if (cameraSettings.recordAudio) "Audio Enabled" else "Audio Disabled",
                                    tint = if (cameraSettings.recordAudio) HighlightYellow else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Timer cycle button
                            IconButton(
                                onClick = { viewModel.cycleTimer() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("timer_cycle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Timer",
                                    tint = if (cameraSettings.captureTimer != CaptureTimer.OFF) HighlightYellow else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Pill 2: Grid, Exposure, Flip Camera
                    FloatingGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0x1A101726),
                        borderColor = Color(0x35FFFFFF)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Grid cycle button
                            IconButton(
                                onClick = { viewModel.cycleGrid() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("grid_cycle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = "Grid",
                                    tint = if (cameraSettings.gridMode != CameraGridMode.OFF) HighlightYellow else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Exposure Compensation
                            IconButton(
                                onClick = { showExposureSlider = !showExposureSlider },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("exposure_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Exposure,
                                    contentDescription = "Exposure",
                                    tint = if (showExposureSlider || exposureCompensation != 0) HighlightYellow else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Flip Camera
                            IconButton(
                                onClick = { viewModel.toggleCameraFacing() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("camera_flip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlipCameraAndroid,
                                    contentDescription = "Flip Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Pill 3: Routes & Settings
                    FloatingGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0x1A101726),
                        borderColor = Color(0x35FFFFFF)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Route button with highlight
                            IconButton(
                                onClick = { onNavigateToRoutes() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (activeRoute != null) HighlightYellow.copy(alpha = 0.25f) else Color.Transparent)
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("routes_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AltRoute,
                                    contentDescription = "Routes",
                                    tint = HighlightYellow,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Settings gear button
                            IconButton(
                                onClick = { showQuickSettingsPopup = true },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Camera Settings",
                                    tint = if (showQuickSettingsPopup) HighlightYellow else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Status Row: Minimal Tiny GPS Status Capsule (Left) — "CUBET CH" colored text totally removed
                // Status Row: Minimal Tiny GPS Status Capsule (Left) — 8dp consistent margin, 4dp blur, 10% opacity
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isAcquiring = locationState.status == LocationStatus.ACQUIRING || locationState.point == null
                    val accuracy = if (locationState.point != null) locationState.accuracy else 999f

                    val infiniteTransition = rememberInfiniteTransition(label = "gps_pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
                        label = "gps_pulse"
                    )
                    val indicatorAlpha = if (isAcquiring) pulseAlpha else 1.0f

                    FloatingGlassCard(
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = Color(0x1A101726),
                        borderColor = Color(0x35FFFFFF),
                        onClick = { showSimDialog = true },
                        modifier = Modifier
                            .wrapContentWidth()
                            .wrapContentHeight()
                            .testTag("gps_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(HighlightYellow)
                                    .alpha(indicatorAlpha)
                            )
                            Text(
                                text = if (locationState.point != null) {
                                    String.format(Locale.US, "GPS ±%.0fm", locationState.accuracy)
                                } else {
                                    "GPS Acquiring..."
                                },
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Expandable Exposure EV Slider
                if (showExposureSlider) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 12.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "EV: ${if (exposureCompensation > 0) "+$exposureCompensation" else "$exposureCompensation"}",
                            color = HighlightYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(46.dp)
                        )
                        Slider(
                            value = exposureCompensation.toFloat(),
                            onValueChange = { viewModel.setExposureCompensation(it.toInt()) },
                            valueRange = -4f..4f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                thumbColor = HighlightYellow,
                                activeTrackColor = HighlightYellow,
                                inactiveTrackColor = SecondaryText.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.setExposureCompensation(0) },
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Reset", color = PrimaryText, fontSize = 10.sp)
                        }
                    }
                }

                // Video Recording Banner
                if (isRecording) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(tween(500), repeatMode = RepeatMode.Reverse),
                        label = "recording_pulse"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x95101726),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, AccentRed)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AccentRed.copy(alpha = alpha))
                                )
                                val mins = recordingDurationSec / 60
                                val secs = recordingDurationSec % 60
                                Text(
                                    text = String.format("REC %02d:%02d", mins, secs),
                                    color = PrimaryText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isRecordingPaused) {
                                    Text(
                                        text = "[PAUSED]",
                                        color = HighlightYellow,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Top Viewfinder HUD: Tiny Standard Photo Mini Map (Top-Left) & Minimalist Timestamp Card (Top-Right)
                // 8dp consistent margin from screen edges and each other, dynamic in height, strictly compact in width
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Top-Left: Tiny Standard Photo Mini Map Preview (8dp margin, 4dp blur & 10% opacity glass container)
                    if (overlayConfig.showMapInset && overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
                        FloatingGlassCard(
                            shape = RoundedCornerShape(10.dp),
                            backgroundColor = Color(0x1A101726),
                            borderColor = Color(0x38FFFFFF),
                            borderWidth = 0.6.dp,
                            onClick = { showSimDialog = true },
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("portrait_minimap_preview")
                        ) {
                            MiniMapPreview(
                                location = locationState.point,
                                bearing = locationState.bearing,
                                routeData = activeRoute,
                                highwayTag = "",
                                gpsAccuracy = null,
                                showGpsBadge = false,
                                showProjectTag = false,
                                showAttribution = false,
                                modifier = Modifier.fillMaxSize(),
                                mapType = "satellite"
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Top-Right: Minimal & Tiny Size Glass Timestamp & Project Info Card
                    // Dynamic in height, strictly compact in width, 4dp blur and 10% opacity
                    FloatingGlassCard(
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = Color(0x1A101726),
                        borderColor = Color(0x38FFFFFF),
                        borderWidth = 0.6.dp,
                        onClick = { viewModel.openManualChainageDialog() },
                        modifier = Modifier
                            .widthIn(min = 125.dp, max = 175.dp)
                            .wrapContentHeight()
                            .testTag("portrait_timestamp_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            // Date
                            val dateStr = SimpleDateFormat("dd MMMM yyyy", Locale.US).format(Date(currentTimeMillis))
                            Text(
                                text = dateStr,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            // Digital Time
                            val timeFormatPattern = if (cameraSettings.is24HourFormat) "HH:mm" else "hh:mm a"
                            val timeStr = SimpleDateFormat(timeFormatPattern, Locale.US).format(Date(currentTimeMillis))
                            Text(
                                text = timeStr,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .fillMaxWidth(0.9f),
                                thickness = 0.5.dp,
                                color = Color(0x30FFFFFF)
                            )
                            // GPS Coordinates
                            if (locationState.point != null && overlayConfig.showCoordinates && overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
                                val lat = String.format(Locale.US, "%.4f°N", kotlin.math.abs(locationState.point!!.latitude))
                                val lon = String.format(Locale.US, "%.4f°E", kotlin.math.abs(locationState.point!!.longitude))
                                Text(
                                    text = "$lat $lon",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            // Dynamic Location Name (Area / Town / City)
                            val displayLoc = currentLocationName.ifBlank { overlayConfig.lastDetectedLocationName }.trim()
                            if (overlayConfig.showLocationName && displayLoc.isNotBlank() && !displayLoc.equals("None", ignoreCase = true) && overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
                                Text(
                                    text = displayLoc,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            // Project / Stretch / Chainage info
                            val projTokens = mutableListOf<String>()
                            if (projectProfile.highwayId.isNotBlank() && !projectProfile.highwayId.equals("None", ignoreCase = true)) {
                                projTokens.add(projectProfile.highwayId)
                            }
                            if (projectProfile.packageSection.isNotBlank() && !projectProfile.packageSection.equals("None", ignoreCase = true)) {
                                projTokens.add(projectProfile.packageSection)
                            }
                            if (projectProfile.workType.isNotBlank() && !projectProfile.workType.equals("None", ignoreCase = true)) {
                                projTokens.add(projectProfile.workType)
                            }
                            val chDisplay = matchResult.displayChainage
                            if (chDisplay.isNotBlank() && !chDisplay.equals("None", ignoreCase = true)) {
                                projTokens.add(chDisplay)
                            }
                            if (projTokens.isNotEmpty() && overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
                                Text(
                                    text = projTokens.joinToString(" | "),
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            // Highlight Line: Side + Activity in Yellow
                            val highlightTokens = mutableListOf<String>()
                            highlightTokens.add(projectProfile.roadSide.name)
                            if (projectProfile.activity.isNotBlank() && !projectProfile.activity.equals("None", ignoreCase = true)) {
                                highlightTokens.add(projectProfile.activity)
                            }
                            if (highlightTokens.isNotEmpty() && overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
                                Text(
                                    text = highlightTokens.joinToString(" "),
                                    color = HighlightYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 7. Sleek Tiny-Size Bottom Half Controls Container (PORTRAIT)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Quick Controls Bar (8dp margin, dynamic height, compact width, 4dp blur, 10% opacity)
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Side chip (LHS / RHS / MEDIAN / BOTH)
                    item {
                        FloatingGlassCard(
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = Color(0x1A101726),
                            borderColor = Color(0x35FFFFFF),
                            onClick = {
                                val nextSide = when (projectProfile.roadSide) {
                                    RoadSide.RHS -> RoadSide.LHS
                                    RoadSide.LHS -> RoadSide.MEDIAN
                                    RoadSide.MEDIAN -> RoadSide.BOTH
                                    RoadSide.BOTH -> RoadSide.RHS
                                }
                                viewModel.updateSide(nextSide)
                            },
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .testTag("side_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(HighlightYellow)
                                )
                                Text(
                                    text = projectProfile.roadSide.name,
                                    color = HighlightYellow,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Lane / Type chip
                    item {
                        FloatingGlassCard(
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = Color(0x1A101726),
                            borderColor = Color(0x35FFFFFF),
                            onClick = {
                                val lanes = ProjectProfile.LANE_PRESETS
                                val nextIndex = (lanes.indexOf(projectProfile.lane) + 1) % lanes.size
                                viewModel.updateLane(lanes[nextIndex])
                            },
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .testTag("lane_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = projectProfile.lane,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Activity chip
                    item {
                        Box {
                            FloatingGlassCard(
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = Color(0x1A101726),
                                borderColor = Color(0x35FFFFFF),
                                onClick = { showActivityMenu = true },
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .wrapContentHeight()
                                    .testTag("activity_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = HighlightYellow,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = projectProfile.activity.ifBlank { "Activity" },
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showActivityMenu,
                                onDismissRequest = { showActivityMenu = false }
                            ) {
                                ProjectProfile.ACTIVITY_PRESETS.forEach { act ->
                                    DropdownMenuItem(
                                        text = { Text(act) },
                                        onClick = {
                                            viewModel.updateActivity(act)
                                            showActivityMenu = false
                                        }
                                    )
                                }
                                HorizontalDivider(color = BorderColor)
                                DropdownMenuItem(
                                    text = { Text("Custom / Manual...", color = HighlightYellow, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showActivityMenu = false
                                        showManualActivityDialog = true
                                    }
                                )
                            }
                        }
                    }

                    // Chainage quick edit button
                    item {
                        val isManualActive = matchResult.source == ChainageSource.MANUAL
                        val isCustomActive = matchResult.source == ChainageSource.CUSTOM_CORRECTION
                        val chipBg = when {
                            isManualActive -> Color(0x25DC2626)
                            isCustomActive -> Color(0x259333EA)
                            else -> Color(0x1A101726)
                        }
                        val chipBorder = when {
                            isManualActive -> AccentRed
                            isCustomActive -> Color(0xFFA855F7)
                            else -> Color(0x35FFFFFF)
                        }
                        val sourceTag = when (matchResult.source) {
                            ChainageSource.MANUAL -> "MANUAL"
                            ChainageSource.CUSTOM_CORRECTION -> "CUSTOM CORRECTION"
                            ChainageSource.KMZ -> "KMZ"
                            ChainageSource.GPS -> "GPS"
                        }
                        val chipText = "$sourceTag: ${matchResult.displayChainage}"
                        FloatingGlassCard(
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = chipBg,
                            borderColor = chipBorder,
                            onClick = { viewModel.openManualChainageDialog() },
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .testTag("chainage_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Chainage",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = chipText,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Custom Comments chip
                    item {
                        val hasComment = projectProfile.customDescription.isNotBlank() && !projectProfile.customDescription.equals("None", ignoreCase = true)
                        FloatingGlassCard(
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = if (hasComment) Color(0x250284C7) else Color(0x1A101726),
                            borderColor = if (hasComment) Color(0xFF38BDF8) else Color(0x35FFFFFF),
                            onClick = {
                                commentInput = projectProfile.customDescription
                                showCustomCommentDialog = true
                            },
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .testTag("custom_comment_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = "Custom Comment",
                                    tint = if (hasComment) Color(0xFF38BDF8) else Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (hasComment) projectProfile.customDescription else "+ Comments",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                            }
                        }
                    }
                }

                // Tiny Sleek Zoom Controller Bar
                CameraZoomControlBar(
                    currentZoomRatio = currentZoomRatio,
                    minZoomRatio = minZoomRatio,
                    maxZoomRatio = maxZoomRatio,
                    onZoomChanged = { newZoom -> viewModel.setZoomRatio(newZoom) },
                    onResetZoom = { viewModel.setZoomRatio(1.0f) },
                    isLandscape = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 1.dp)
                )

                // Tiny Sleek Segmented Mode Switcher Pill: PHOTO | VIDEO (4dp blur, 10% opacity)
                FloatingGlassCard(
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x1A101726),
                    borderColor = Color(0x35FFFFFF),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(13.dp))
                                .background(if (captureMode == CaptureMode.PHOTO) Color(0x35FACC15) else Color.Transparent)
                                .border(
                                    width = if (captureMode == CaptureMode.PHOTO) 0.8.dp else 0.dp,
                                    color = if (captureMode == CaptureMode.PHOTO) HighlightYellow.copy(alpha = 0.6f) else Color.Transparent,
                                    shape = RoundedCornerShape(13.dp)
                                )
                                .clickable { viewModel.setCaptureMode(CaptureMode.PHOTO) }
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = if (captureMode == CaptureMode.PHOTO) HighlightYellow else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "PHOTO",
                                    color = if (captureMode == CaptureMode.PHOTO) HighlightYellow else Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(13.dp))
                                .background(if (captureMode == CaptureMode.VIDEO) Color(0x35EF4444) else Color.Transparent)
                                .border(
                                    width = if (captureMode == CaptureMode.VIDEO) 0.8.dp else 0.dp,
                                    color = if (captureMode == CaptureMode.VIDEO) AccentRed.copy(alpha = 0.6f) else Color.Transparent,
                                    shape = RoundedCornerShape(13.dp)
                                )
                                .clickable { viewModel.setCaptureMode(CaptureMode.VIDEO) }
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = if (captureMode == CaptureMode.VIDEO) AccentRed else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "VIDEO",
                                    color = if (captureMode == CaptureMode.VIDEO) AccentRed else Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Tiny Sleek Shutter Bar (Gallery Thumbnail, Center Shutter, GPS Location Preset)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRecording) {
                        // Quick Photo Capture Button during video recording
                        FloatingGlassCard(
                            shape = CircleShape,
                            backgroundColor = Color(0x1A101726),
                            borderColor = HighlightYellow,
                            borderWidth = 0.8.dp,
                            onClick = {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                } catch (ignored: Exception) {}
                                showQuickCaptureFlash = true
                                capturePhoto(
                                    controller = controller,
                                    context = context,
                                    deviceRotationDegrees = rotationDegrees.toInt(),
                                    isLandscape = isLandscape,
                                    photoAspectRatio = cameraSettings.photoAspectRatio,
                                    onSuccess = { bitmap ->
                                        viewModel.processCapture(bitmap)
                                        viewModel.showSuccess("Quick photo captured & saved to Gallery!")
                                    },
                                    onError = {
                                        val fallback = createSimulatedCameraFrame(
                                            context = context,
                                            rotationDegrees = rotationDegrees.toInt(),
                                            isLandscapeMode = isLandscape,
                                            resolutionStr = cameraSettings.photoResolution,
                                            aspectRatioStr = cameraSettings.photoAspectRatio
                                        )
                                        viewModel.processCapture(fallback)
                                        viewModel.showSuccess("Quick photo captured & saved to Gallery!")
                                    }
                                )
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { rotationZ = -animatedRotation }
                                .testTag("quick_photo_capture_button")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = "Quick Photo Capture",
                                    tint = HighlightYellow,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Stop Video Recording Button
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(AccentRed)
                                .clickable { toggleVideoRecording() }
                                .testTag("stop_video_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color.White)
                            )
                        }

                        // Pause / Resume Video Recording Button
                        FloatingGlassCard(
                            shape = CircleShape,
                            backgroundColor = Color(0x1A101726),
                            borderColor = if (isRecordingPaused) HighlightYellow else Color(0x35FFFFFF),
                            borderWidth = 0.8.dp,
                            onClick = {
                                if (isRecordingPaused) {
                                    videoManager.resume()
                                    viewModel.onVideoRecordingPaused(false)
                                    viewModel.showInfo("Video recording resumed")
                                } else {
                                    videoManager.pause()
                                    viewModel.onVideoRecordingPaused(true)
                                    viewModel.showInfo("Video recording paused")
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { rotationZ = -animatedRotation }
                                .testTag("pause_resume_video_button")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isRecordingPaused) "Resume Recording" else "Pause Recording",
                                    tint = if (isRecordingPaused) HighlightYellow else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        // Normal Shutter Bar
                        // Recent Thumbnail / Gallery Button (20dp icon, 4dp blur, 10% opacity)
                        FloatingGlassCard(
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = Color(0x1A101726),
                            borderColor = Color(0x35FFFFFF),
                            borderWidth = 0.6.dp,
                            onClick = { onNavigateToPhotos() },
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { rotationZ = -animatedRotation }
                                .testTag("recent_thumbnail_button")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (lastPhoto?.filePath != null && File(lastPhoto!!.filePath).exists()) {
                                    AsyncImage(
                                        model = File(lastPhoto!!.filePath),
                                        contentDescription = "Recent Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = "Gallery",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Triple-Ring Aperture Shutter Button (20dp icon inside 64dp button)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (captureMode == CaptureMode.PHOTO) {
                                        if (isCapturing) AccentRed else Color.White
                                    } else {
                                        AccentRed
                                    }
                                )
                                .clickable(enabled = (!isCapturing && !isCountingDown) || captureMode == CaptureMode.VIDEO) {
                                    if (captureMode == CaptureMode.PHOTO) {
                                        triggerPhotoCapture()
                                    } else {
                                        toggleVideoRecording()
                                    }
                                }
                                .testTag("shutter_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (captureMode == CaptureMode.PHOTO) {
                                if (isCapturing) {
                                    CircularProgressIndicator(
                                        color = PrimaryBackground,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    CameraApertureIcon(
                                        tint = Color(0xFF101726),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer { rotationZ = -animatedRotation }
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(DeepRed)
                                )
                            }
                        }

                        // GPS Location Simulator / Target Preset Button (20dp icon, 4dp blur, 10% opacity)
                        FloatingGlassCard(
                            shape = CircleShape,
                            backgroundColor = Color(0x1A101726),
                            borderColor = Color(0x35FFFFFF),
                            borderWidth = 0.6.dp,
                            onClick = { showSimDialog = true },
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { rotationZ = -animatedRotation }
                                .testTag("gps_sim_button")
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = "Simulate Location",
                                    tint = HighlightYellow,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Tiny Sleek Bottom Output Selection Capsule: Photo & Video Output settings
                val photoBadgePortrait = CameraResolutionHelper.getPhotoBadgeText(
                    cameraSettings.photoResolution,
                    cameraSettings.photoAspectRatio
                )
                val videoBadgePortrait = CameraResolutionHelper.getVideoBadgeText(
                    cameraSettings.videoResolution,
                    cameraSettings.videoAspectRatio
                )

                // Tiny Sleek Bottom Output Selection Cards: Photo & Video (8dp margin, dynamic height, compact width, 4dp blur, 10% opacity)
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("portrait_bottom_output_bar"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Camera / Photo Output Card: Tiny width, Dynamic height, 20dp icons, High-contrast readability
                    FloatingGlassCard(
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = if (activeOutputSettingsType == "PHOTO") Color(0x25FACC15) else Color(0x1A101726),
                        borderColor = if (activeOutputSettingsType == "PHOTO") HighlightYellow else Color(0x35FFFFFF),
                        borderWidth = 0.5.dp,
                        onClick = {
                            activeOutputSettingsType = if (activeOutputSettingsType == "PHOTO") null else "PHOTO"
                        },
                        modifier = Modifier
                            .wrapContentWidth()
                            .wrapContentHeight()
                            .testTag("portrait_photo_settings_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Photo Output Settings",
                                tint = if (activeOutputSettingsType == "PHOTO") HighlightYellow else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(
                                modifier = Modifier.wrapContentSize(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Photo",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (activeOutputSettingsType == "PHOTO") HighlightYellow else Color.White.copy(alpha = 0.9f),
                                    lineHeight = 10.sp
                                )
                                Text(
                                    text = photoBadgePortrait,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    lineHeight = 12.sp,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = if (activeOutputSettingsType == "PHOTO") HighlightYellow else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Video Output Card: Tiny width, Dynamic height, 20dp icons, High-contrast readability
                    FloatingGlassCard(
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = if (activeOutputSettingsType == "VIDEO") Color(0x25EF4444) else Color(0x1A101726),
                        borderColor = if (activeOutputSettingsType == "VIDEO") AccentRed else Color(0x35FFFFFF),
                        borderWidth = 0.5.dp,
                        onClick = {
                            activeOutputSettingsType = if (activeOutputSettingsType == "VIDEO") null else "VIDEO"
                        },
                        modifier = Modifier
                            .wrapContentWidth()
                            .wrapContentHeight()
                            .testTag("portrait_video_settings_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video Output Settings",
                                tint = if (activeOutputSettingsType == "VIDEO") AccentRed else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(
                                modifier = Modifier.wrapContentSize(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Video",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (activeOutputSettingsType == "VIDEO") AccentRed else Color.White.copy(alpha = 0.9f),
                                    lineHeight = 10.sp
                                )
                                Text(
                                    text = videoBadgePortrait,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    lineHeight = 12.sp,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = if (activeOutputSettingsType == "VIDEO") AccentRed else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Manual Chainage Input Dialog
        if (showManualDialog) {
            val dialogColors = com.example.ui.theme.rememberDialogColors()
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            val primaryAccent = if (dialogColors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
            val onAccent = if (dialogColors.isDark) Color.Black else Color.White

            val initialChainageText = remember(manualChainageText, matchResult.displayChainage) {
                when {
                    !manualChainageText.isNullOrBlank() -> manualChainageText!!
                    matchResult.displayChainage != "---" && matchResult.displayChainage != "Manual" -> matchResult.displayChainage
                    else -> ""
                }
            }
            var chainageInput by remember(showManualDialog) { mutableStateOf(initialChainageText) }
            var editingItemOriginal by remember(showManualDialog) { mutableStateOf<String?>(null) }
            var validationError by remember(showManualDialog) { mutableStateOf<String?>(null) }
            val isManualActive = !manualChainageText.isNullOrBlank() || matchResult.source == ChainageSource.MANUAL
            val parsedPreview = remember(chainageInput) { Chainage.parse(chainageInput.trim()) }

            val activeSourceText = when (matchResult.source) {
                ChainageSource.MANUAL -> "MANUAL"
                ChainageSource.CUSTOM_CORRECTION -> "CUSTOM CORRECTION"
                ChainageSource.KMZ -> "KMZ"
                ChainageSource.GPS -> "GPS"
            }
            val activeSourceColor = when (matchResult.source) {
                ChainageSource.MANUAL -> AccentRed
                ChainageSource.CUSTOM_CORRECTION -> Color(0xFFA855F7)
                ChainageSource.KMZ -> Color(0xFF10B981)
                ChainageSource.GPS -> primaryAccent
            }

            AlertDialog(
                onDismissRequest = { viewModel.closeManualChainageDialog() },
                shape = dialogColors.shape,
                containerColor = dialogColors.containerColor,
                modifier = Modifier.testTag("manual_chainage_dialog"),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                editingItemOriginal != null -> "Edit Custom Chainage"
                                isManualActive -> "Manual Chainage Active"
                                else -> "Manual Chainage Input"
                            },
                            color = dialogColors.titleColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = activeSourceColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                activeSourceColor
                            )
                        ) {
                            Text(
                                text = activeSourceText,
                                color = activeSourceColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (editingItemOriginal != null) {
                                "Update custom chainage \"$editingItemOriginal\" below:"
                            } else {
                                "Enter chainage (supports \"198+880\", \"198880\", \"200+000\"):"
                            },
                            fontSize = 12.sp,
                            color = dialogColors.textSecondaryColor
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = chainageInput,
                                onValueChange = {
                                    chainageInput = it
                                    validationError = null
                                },
                                label = {
                                    Text(if (editingItemOriginal != null) "Edit Chainage" else "Manual Chainage")
                                },
                                placeholder = {
                                    Text(
                                        "e.g. 198+880 or 198880",
                                        color = dialogColors.textSecondaryColor.copy(alpha = 0.6f)
                                    )
                                },
                                isError = validationError != null,
                                singleLine = true,
                                trailingIcon = {
                                    if (chainageInput.isNotEmpty()) {
                                        IconButton(onClick = {
                                            chainageInput = ""
                                            validationError = null
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear input",
                                                tint = dialogColors.textSecondaryColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        val trimmed = chainageInput.trim()
                                        val parsed = Chainage.parse(trimmed)
                                        if (parsed != null) {
                                            val formatted = parsed.format()
                                            chainageInput = formatted
                                            if (editingItemOriginal != null) {
                                                viewModel.updateCustomChainagePreset(editingItemOriginal!!, formatted)
                                                editingItemOriginal = null
                                            }
                                            viewModel.setManualChainage(formatted)
                                        } else {
                                            validationError = "Invalid format. Use 198+880, 198880, or 200+000."
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = dialogColors.textColor,
                                    unfocusedTextColor = dialogColors.textColor,
                                    focusedBorderColor = primaryAccent,
                                    unfocusedBorderColor = dialogColors.inputBorder,
                                    focusedLabelColor = primaryAccent,
                                    unfocusedLabelColor = dialogColors.textSecondaryColor,
                                    cursorColor = primaryAccent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manual_chainage_input")
                            )

                            // Quick Add / Update button for the saved custom chainage list
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = primaryAccent.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, primaryAccent),
                                modifier = Modifier
                                    .height(52.dp)
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = chainageInput.isNotBlank()) {
                                        val trimmed = chainageInput.trim()
                                        val parsed = Chainage.parse(trimmed)
                                        if (parsed != null) {
                                            val formatted = parsed.format()
                                            chainageInput = formatted
                                            validationError = null
                                            if (editingItemOriginal != null) {
                                                viewModel.updateCustomChainagePreset(editingItemOriginal!!, formatted)
                                                editingItemOriginal = null
                                            } else {
                                                viewModel.addCustomChainagePreset(formatted)
                                            }
                                        } else {
                                            validationError = "Invalid format. Use 198+880, 198880, or 200+000."
                                        }
                                    }
                                    .testTag("add_custom_chainage_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (editingItemOriginal != null) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = if (editingItemOriginal != null) "Save Edited Chainage" else "Add Custom Chainage",
                                        tint = primaryAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (editingItemOriginal != null) "Update" else "Add",
                                        color = primaryAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (validationError != null) {
                            Text(
                                text = validationError!!,
                                color = AccentRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (chainageInput.isNotBlank()) {
                            Text(
                                text = if (parsedPreview != null) {
                                    "Auto-formatted: ${parsedPreview.format()} (${parsedPreview.metres} m)"
                                } else {
                                    "Enter valid chainage (e.g. 198+880, 198880, 200+000)"
                                },
                                color = if (parsedPreview != null) Color(0xFF10B981) else AccentRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Quick Step / Edit Nudge Buttons (-50m, -10m, +10m, +50m)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(-50L to "-50m", -10L to "-10m", 10L to "+10m", 50L to "+50m").forEach { (delta, label) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (dialogColors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, dialogColors.borderColor),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val baseMetres = Chainage.parse(chainageInput)?.metres
                                                ?: matchResult.chainage?.metres
                                                ?: 0L
                                            chainageInput = Chainage((baseMetres + delta).coerceAtLeast(0L)).format()
                                            validationError = null
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = dialogColors.textColor
                                        )
                                    }
                                }
                            }
                        }

                        // Saved Custom Chainages List (Tap to select, Edit icon to edit, Delete icon to remove)
                        if (savedCustomChainages.isNotEmpty()) {
                            HorizontalDivider(color = dialogColors.borderColor)
                            Text(
                                text = "Saved Custom Chainages (Tap to use or Edit):",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = dialogColors.textSecondaryColor
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                savedCustomChainages.forEach { savedItem ->
                                    val isSelected = manualChainageText.equals(savedItem, ignoreCase = true)
                                    val isBeingEdited = editingItemOriginal.equals(savedItem, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = when {
                                            isBeingEdited -> primaryAccent.copy(alpha = 0.18f)
                                            isSelected -> AccentRed.copy(alpha = 0.14f)
                                            dialogColors.isDark -> Color(0xFF1E293B)
                                            else -> Color(0xFFF8FAFC)
                                        },
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            when {
                                                isBeingEdited -> primaryAccent
                                                isSelected -> AccentRed
                                                else -> dialogColors.borderColor
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                chainageInput = savedItem
                                                viewModel.setManualChainage(savedItem)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = savedItem,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = dialogColors.textColor
                                                )
                                                if (isSelected) {
                                                    Text(
                                                        text = "(MANUAL Active)",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = AccentRed
                                                    )
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        editingItemOriginal = savedItem
                                                        chainageInput = savedItem
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit $savedItem",
                                                        tint = primaryAccent,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (editingItemOriginal.equals(savedItem, ignoreCase = true)) {
                                                            editingItemOriginal = null
                                                        }
                                                        viewModel.deleteCustomChainagePreset(savedItem)
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete $savedItem",
                                                        tint = AccentRed,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = chainageInput.trim()
                            val parsed = Chainage.parse(trimmed)
                            if (parsed != null) {
                                val formatted = parsed.format()
                                chainageInput = formatted
                                validationError = null
                                if (editingItemOriginal != null) {
                                    viewModel.updateCustomChainagePreset(editingItemOriginal!!, formatted)
                                    editingItemOriginal = null
                                }
                                viewModel.setManualChainage(formatted)
                            } else {
                                validationError = "Invalid chainage. Enter formats like 198+880, 198880, or 200+000."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = onAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_manual_chainage_button")
                    ) {
                        Text(
                            text = if (editingItemOriginal != null) "Save & Apply" else "Apply",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                chainageInput = ""
                                editingItemOriginal = null
                                validationError = null
                                viewModel.clearManualChainage()
                            },
                            modifier = Modifier.testTag("clear_manual_chainage_button")
                        ) {
                            Text("Reset", color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                editingItemOriginal = null
                                validationError = null
                                viewModel.closeManualChainageDialog()
                            }
                        ) {
                            Text("Cancel", color = dialogColors.textSecondaryColor)
                        }
                    }
                }
            )
        }

        // Manual Activity / Layer Dialog
        if (showManualActivityDialog) {
            val dialogColors = com.example.ui.theme.rememberDialogColors()
            var activityInput by remember { mutableStateOf(projectProfile.activity) }
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            val primaryAccent = if (dialogColors.isDark) HighlightYellow else Color(0xFF0284C7)
            val onAccent = if (dialogColors.isDark) Color.Black else Color.White

            AlertDialog(
                onDismissRequest = { showManualActivityDialog = false },
                shape = dialogColors.shape,
                containerColor = dialogColors.containerColor,
                title = { Text("Custom Activity / Pavement Layer", color = dialogColors.titleColor, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Type custom activity or pavement layer (e.g. Main CW to MCW):",
                            fontSize = 13.sp,
                            color = dialogColors.textSecondaryColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = activityInput,
                            onValueChange = { activityInput = it },
                            label = { Text("Activity / Layer Name") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    if (activityInput.isNotBlank()) {
                                        viewModel.updateActivity(activityInput.trim())
                                        viewModel.showToast("Activity updated: ${activityInput.trim()}", ToastType.SUCCESS)
                                    }
                                    showManualActivityDialog = false
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = dialogColors.textColor,
                                unfocusedTextColor = dialogColors.textColor,
                                focusedBorderColor = primaryAccent,
                                unfocusedBorderColor = dialogColors.inputBorder,
                                focusedLabelColor = primaryAccent,
                                unfocusedLabelColor = dialogColors.textSecondaryColor,
                                cursorColor = primaryAccent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("manual_activity_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (activityInput.isNotBlank()) {
                                viewModel.updateActivity(activityInput.trim())
                                viewModel.showToast("Activity updated: ${activityInput.trim()}", ToastType.SUCCESS)
                            }
                            showManualActivityDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = onAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_manual_activity_button")
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualActivityDialog = false }) {
                        Text("Cancel", color = dialogColors.textSecondaryColor)
                    }
                }
            )
        }

        // Custom Comment Dialog
        if (showCustomCommentDialog) {
            val dialogColors = com.example.ui.theme.rememberDialogColors()
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            val primaryAccent = if (dialogColors.isDark) HighlightYellow else Color(0xFF0284C7)
            val onAccent = if (dialogColors.isDark) Color.Black else Color.White

            AlertDialog(
                onDismissRequest = { showCustomCommentDialog = false },
                shape = dialogColors.shape,
                containerColor = dialogColors.containerColor,
                title = { Text("Custom Comment Watermark", color = dialogColors.titleColor, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Add a custom comment line. When set, this will be displayed dynamically on the next line under the highway timestamp watermark.",
                            fontSize = 13.sp,
                            color = dialogColors.textSecondaryColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            label = { Text("Custom Comment") },
                            placeholder = { Text("e.g. Culvert Inspection / Patch Work", color = dialogColors.textSecondaryColor.copy(alpha = 0.6f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    viewModel.updateCustomDescription(commentInput.trim())
                                    if (commentInput.trim().isNotBlank()) {
                                        viewModel.showToast("Comment set: ${commentInput.trim()}", ToastType.SUCCESS)
                                    }
                                    showCustomCommentDialog = false
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = dialogColors.textColor,
                                unfocusedTextColor = dialogColors.textColor,
                                focusedBorderColor = primaryAccent,
                                unfocusedBorderColor = dialogColors.inputBorder,
                                focusedLabelColor = primaryAccent,
                                unfocusedLabelColor = dialogColors.textSecondaryColor,
                                cursorColor = primaryAccent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("custom_comment_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateCustomDescription(commentInput.trim())
                            if (commentInput.trim().isNotBlank()) {
                                viewModel.showToast("Comment set: ${commentInput.trim()}", ToastType.SUCCESS)
                            }
                            showCustomCommentDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = onAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_custom_comment_button")
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        if (projectProfile.customDescription.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    commentInput = ""
                                    viewModel.updateCustomDescription("")
                                    viewModel.showToast("Comment cleared", ToastType.INFO)
                                    showCustomCommentDialog = false
                                }
                            ) {
                                Text("Clear", color = AccentRed)
                            }
                        }
                        TextButton(onClick = { showCustomCommentDialog = false }) {
                            Text("Cancel", color = dialogColors.textSecondaryColor)
                        }
                    }
                }
            )
        }

        // GPS Simulation Dialog
        if (showSimDialog) {
            val dialogColors = com.example.ui.theme.rememberDialogColors()
            AlertDialog(
                onDismissRequest = { showSimDialog = false },
                shape = dialogColors.shape,
                containerColor = dialogColors.containerColor,
                title = { Text("GPS Location Test Presets", color = dialogColors.titleColor, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Select a GPS position along NH-58 to test chainage matching and permanent overlays immediately:",
                            fontSize = 13.sp,
                            color = dialogColors.textSecondaryColor
                        )

                        Button(
                            onClick = {
                                viewModel.simulateGps(29.9183, 78.1064, 2.5f)
                                showSimDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = dialogColors.surfaceColor, contentColor = dialogColors.textColor),
                            border = BorderStroke(1.dp, dialogColors.borderColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reference Photo (196+040, 29.9183N 78.1064E)")
                        }

                        Button(
                            onClick = {
                                viewModel.simulateGps(29.8680, 77.9250, 3.0f)
                                showSimDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = dialogColors.surfaceColor, contentColor = dialogColors.textColor),
                            border = BorderStroke(1.dp, dialogColors.borderColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Roorkee Section (185+000, 29.8680N 77.9250E)")
                        }

                        Button(
                            onClick = {
                                viewModel.simulateGps(29.4750, 77.7120, 3.5f)
                                showSimDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = dialogColors.surfaceColor, contentColor = dialogColors.textColor),
                            border = BorderStroke(1.dp, dialogColors.borderColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Muzaffarnagar Start (130+000, 29.4750N 77.7120E)")
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.startLocation()
                                showSimDialog = false
                            },
                            border = BorderStroke(1.dp, if (dialogColors.isDark) HighlightYellow else Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use Real Device Hardware GPS", color = if (dialogColors.isDark) HighlightYellow else Color(0xFF0284C7))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSimDialog = false }) {
                        Text("Close", color = dialogColors.textSecondaryColor)
                    }
                }
            )
        }

        // Camera Quick Settings Popup Dialog
        if (showQuickSettingsPopup) {
            CameraQuickSettingsDialog(
                cameraSettings = cameraSettings,
                overlayConfig = overlayConfig,
                isFlashOn = isFlashOn,
                onToggleFlash = { viewModel.toggleFlash() },
                onUpdateCameraSettings = { updated -> viewModel.saveCameraSettingsSilent(updated) },
                onUpdateOverlayConfig = { updated -> viewModel.saveOverlayConfigSilent(updated) },
                onNavigateToFullSettings = { onNavigateToSettings() },
                onDismiss = { showQuickSettingsPopup = false }
            )
        }

        // Smart Photo / Video Output Settings Popup Dialog
        if (activeOutputSettingsType != null) {
            CameraOutputSettingsPopup(
                category = activeOutputSettingsType!!,
                cameraSettings = cameraSettings,
                isFrontCamera = isFrontCamera,
                isLandscape = isLandscape,
                onUpdateCameraSettings = { updated ->
                    viewModel.saveCameraSettingsSilent(updated)
                    if (activeOutputSettingsType == "PHOTO") {
                        viewModel.saveOverlayConfigSilent(overlayConfig.copy(resolutionOption = updated.photoResolution))
                    }
                },
                onShowToast = { msg -> viewModel.showToast(msg) },
                onDismiss = { activeOutputSettingsType = null }
            )
        }
    }
}

/**
 * Smart Photo & Video Output Settings Popup Dialog.
 * Compact Material 3 popup matching the existing Camera UI.
 * Organizes options cleanly into Aspect Ratio and Resolution sections with active selection highlights,
 * sensor vs cropped output indicators, and direct hardware support detection.
 */
@Composable
fun CameraOutputSettingsPopup(
    category: String, // "PHOTO" or "VIDEO"
    cameraSettings: com.example.model.CameraSettings,
    isFrontCamera: Boolean,
    isLandscape: Boolean,
    onUpdateCameraSettings: (com.example.model.CameraSettings) -> Unit,
    onShowToast: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPhoto = category.equals("PHOTO", ignoreCase = true)

    val supportedResolutions = remember(isPhoto, isFrontCamera) {
        if (isPhoto) {
            CameraResolutionHelper.getSupportedPhotoResolutions(context, isFrontCamera)
        } else {
            CameraResolutionHelper.getSupportedVideoResolutions(context, isFrontCamera)
        }
    }

    var selectedRatio by remember(isPhoto, cameraSettings.photoAspectRatio, cameraSettings.videoAspectRatio) {
        mutableStateOf(if (isPhoto) cameraSettings.photoAspectRatio else cameraSettings.videoAspectRatio)
    }

    var selectedResolution by remember(isPhoto, cameraSettings.photoResolution, cameraSettings.videoResolution) {
        mutableStateOf(if (isPhoto) cameraSettings.photoResolution else cameraSettings.videoResolution)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xF2121722),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            shadowElevation = 16.dp,
            modifier = Modifier
                .widthIn(min = 280.dp, max = 330.dp)
                .wrapContentHeight()
                .padding(vertical = 12.dp, horizontal = 12.dp)
                .testTag(if (isPhoto) "photo_output_settings_popup" else "video_output_settings_popup")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp)
            ) {
                // 1. Header with category icon, title, active lens badge, and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isPhoto) HighlightYellow.copy(alpha = 0.2f) else AccentRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPhoto) Icons.Default.CameraAlt else Icons.Default.Videocam,
                                contentDescription = null,
                                tint = if (isPhoto) HighlightYellow else AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isPhoto) "Photo Output Settings" else "Video Output Settings",
                                color = PrimaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isFrontCamera) "Front Camera Lens" else "Rear Main Camera Lens",
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .testTag("output_settings_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = PrimaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content area containing Aspect Ratio and Resolution sections
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // SECTION A: ASPECT RATIO
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = HighlightYellow,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "ASPECT RATIO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = HighlightYellow,
                            letterSpacing = 0.8.sp
                        )
                    }

                    // Aspect ratio chips / cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CameraResolutionHelper.AVAILABLE_ASPECT_RATIOS.forEach { opt ->
                            val isSelected = selectedRatio.equals(opt.id, ignoreCase = true) ||
                                (opt.id == "Full" && selectedRatio.equals("Full Screen", ignoreCase = true))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) HighlightYellow.copy(alpha = 0.22f) else Color(0x601A202C),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) HighlightYellow else Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedRatio = opt.id
                                        val updated = if (isPhoto) {
                                            cameraSettings.copy(photoAspectRatio = opt.id)
                                        } else {
                                            cameraSettings.copy(videoAspectRatio = opt.id)
                                        }
                                        onUpdateCameraSettings(updated)
                                        onShowToast("${if (isPhoto) "Photo" else "Video"} aspect ratio: ${opt.title}")
                                    }
                                    .testTag("aspect_ratio_${opt.id.lowercase(java.util.Locale.US)}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = opt.shortLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) HighlightYellow else PrimaryText
                                    )
                                    Text(
                                        text = when (opt.id) {
                                            "4:3" -> "Standard"
                                            "16:9" -> "Wide"
                                            "1:1" -> "Square"
                                            else -> "Full"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) HighlightYellow else SecondaryText
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // SECTION B: RESOLUTION & SENSOR OUTPUT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = HighlightYellow,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (isPhoto) "HARDWARE RESOLUTION (PHOTO)" else "VIDEO RECORDING RESOLUTION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = HighlightYellow,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Text(
                            text = "${supportedResolutions.size} detected",
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    supportedResolutions.forEach { res ->
                        val isSelected = selectedResolution.equals(res.label, ignoreCase = true) ||
                            selectedResolution.contains(res.key)

                        // Calculate aspect ratio crop details for still photos
                        val cropDetails = if (isPhoto) {
                            CameraResolutionHelper.calculateCropDetails(
                                rawWidth = res.width,
                                rawHeight = res.height,
                                aspectRatioId = selectedRatio,
                                isLandscape = isLandscape
                            )
                        } else null

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0x35EAB308) else Color(0x50181F2E),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) HighlightYellow else Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedResolution = res.label
                                    val updated = if (isPhoto) {
                                        cameraSettings.copy(
                                            photoResolution = res.label,
                                            photoAspectRatio = selectedRatio
                                        )
                                    } else {
                                        cameraSettings.copy(
                                            videoResolution = res.label,
                                            videoAspectRatio = selectedRatio
                                        )
                                    }
                                    onUpdateCameraSettings(updated)
                                    onShowToast("${if (isPhoto) "Photo" else "Video"} resolution: ${res.label}")
                                }
                                .testTag("resolution_option_${res.width}x${res.height}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = res.label,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                            color = if (isSelected) HighlightYellow else PrimaryText
                                        )
                                        if (res.megapixelsText.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSelected) HighlightYellow.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = res.megapixelsText,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) HighlightYellow else SecondaryText,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (res.isMax) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0x4010B981)
                                            ) {
                                                Text(
                                                    text = "MAX",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF34D399),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (cropDetails != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${cropDetails.cropDescription} • ${cropDetails.estimatedFileSize}",
                                            fontSize = 11.sp,
                                            color = if (isSelected) HighlightYellow.copy(alpha = 0.85f) else SecondaryText
                                        )
                                    } else if (res.tag.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = res.tag,
                                            fontSize = 11.sp,
                                            color = if (isSelected) HighlightYellow.copy(alpha = 0.85f) else SecondaryText
                                        )
                                    }
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedResolution = res.label
                                        val updated = if (isPhoto) {
                                            cameraSettings.copy(
                                                photoResolution = res.label,
                                                photoAspectRatio = selectedRatio
                                            )
                                        } else {
                                            cameraSettings.copy(
                                                videoResolution = res.label,
                                                videoAspectRatio = selectedRatio
                                            )
                                        }
                                        onUpdateCameraSettings(updated)
                                        onShowToast("${if (isPhoto) "Photo" else "Video"} resolution: ${res.label}")
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = HighlightYellow,
                                        unselectedColor = Color.White.copy(alpha = 0.4f)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Done Action Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HighlightYellow,
                        contentColor = PrimaryBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("output_settings_done_button")
                ) {
                    Text(
                        text = "Done",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Clean Popup Dialog showing only Camera-related Settings
 */
@Composable
fun CameraQuickSettingsDialog(
    cameraSettings: com.example.model.CameraSettings,
    overlayConfig: OverlayConfig,
    isFlashOn: Boolean,
    onToggleFlash: () -> Unit,
    onUpdateCameraSettings: (com.example.model.CameraSettings) -> Unit,
    onUpdateOverlayConfig: (OverlayConfig) -> Unit,
    onNavigateToFullSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogColors = com.example.ui.theme.rememberDialogColors()
    val accentColor = if (dialogColors.isDark) HighlightYellow else Color(0xFF0284C7)
    val onAccentColor = if (dialogColors.isDark) Color.Black else Color.White

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Camera Settings", color = dialogColors.titleColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = dialogColors.textSecondaryColor
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section 1: Capture Controls
                Text("HARDWARE & CAPTURE", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                // Flash Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggleFlash() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Camera Flash", color = dialogColors.textColor, fontSize = 13.sp)
                    Switch(
                        checked = isFlashOn,
                        onCheckedChange = { onToggleFlash() },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor, checkedTrackColor = accentColor)
                    )
                }

                // Shutter Sound
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateCameraSettings(cameraSettings.copy(captureSound = !cameraSettings.captureSound))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Shutter Sound", color = dialogColors.textColor, fontSize = 13.sp)
                    Switch(
                        checked = cameraSettings.captureSound,
                        onCheckedChange = { onUpdateCameraSettings(cameraSettings.copy(captureSound = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor, checkedTrackColor = accentColor)
                    )
                }

                // Touch to Capture
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateCameraSettings(cameraSettings.copy(touchToCapture = !cameraSettings.touchToCapture))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Touch to Take Photo", color = dialogColors.textColor, fontSize = 13.sp)
                    Switch(
                        checked = cameraSettings.touchToCapture,
                        onCheckedChange = { onUpdateCameraSettings(cameraSettings.copy(touchToCapture = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor, checkedTrackColor = accentColor)
                    )
                }

                // Horizon Leveler
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateCameraSettings(cameraSettings.copy(showHorizonLevel = !cameraSettings.showHorizonLevel))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Electronic Level Indicator", color = dialogColors.textColor, fontSize = 13.sp)
                    Switch(
                        checked = cameraSettings.showHorizonLevel,
                        onCheckedChange = { onUpdateCameraSettings(cameraSettings.copy(showHorizonLevel = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor, checkedTrackColor = accentColor)
                    )
                }

                // Keep Original Photo
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateCameraSettings(cameraSettings.copy(saveOriginalAndStamped = !cameraSettings.saveOriginalAndStamped))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Save Clean Original Photo", color = dialogColors.textColor, fontSize = 13.sp)
                    Switch(
                        checked = cameraSettings.saveOriginalAndStamped,
                        onCheckedChange = { onUpdateCameraSettings(cameraSettings.copy(saveOriginalAndStamped = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor, checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = dialogColors.borderColor)

                // Section 2: Grid & Timer
                Text("GRID & COUNTDOWN TIMER", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                // Grid Mode Selector
                Column {
                    Text("Viewfinder Grid", color = dialogColors.textSecondaryColor, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CameraGridMode.values().forEach { gMode ->
                            val isSel = cameraSettings.gridMode == gMode
                            FilterChip(
                                selected = isSel,
                                onClick = { onUpdateCameraSettings(cameraSettings.copy(gridMode = gMode)) },
                                label = { Text(gMode.name, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = onAccentColor,
                                    containerColor = dialogColors.surfaceColor,
                                    labelColor = dialogColors.textColor
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = dialogColors.borderColor,
                                    enabled = true,
                                    selected = isSel
                                )
                            )
                        }
                    }
                }

                // Timer Selector
                Column {
                    Text("Capture Timer", color = dialogColors.textSecondaryColor, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CaptureTimer.values().forEach { timerVal ->
                            val isSel = cameraSettings.captureTimer == timerVal
                            FilterChip(
                                selected = isSel,
                                onClick = { onUpdateCameraSettings(cameraSettings.copy(captureTimer = timerVal)) },
                                label = { Text(if (timerVal == CaptureTimer.OFF) "OFF" else "${timerVal.seconds}s", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = onAccentColor,
                                    containerColor = dialogColors.surfaceColor,
                                    labelColor = dialogColors.textColor
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = dialogColors.borderColor,
                                    enabled = true,
                                    selected = isSel
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = dialogColors.borderColor)

                // Section 3: Watermark Overlay Toggles
                Text("CAMERA WATERMARK OVERLAYS", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateOverlayConfig(overlayConfig.copy(showTimestamp = !overlayConfig.showTimestamp))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Timestamp Stamp", color = dialogColors.textColor, fontSize = 13.sp)
                    Switch(
                        checked = overlayConfig.showTimestamp,
                        onCheckedChange = { onUpdateOverlayConfig(overlayConfig.copy(showTimestamp = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor, checkedTrackColor = accentColor)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateOverlayConfig(overlayConfig.copy(showCoordinates = !overlayConfig.showCoordinates))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GPS Coordinates Stamp", color = dialogColors.textColor, fontSize = 13.sp)
                    Switch(
                        checked = overlayConfig.showCoordinates,
                        onCheckedChange = { onUpdateOverlayConfig(overlayConfig.copy(showCoordinates = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor, checkedTrackColor = accentColor)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateOverlayConfig(overlayConfig.copy(showCompass = !overlayConfig.showCompass))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Compass Heading Stamp", color = dialogColors.textColor, fontSize = 13.sp)
                    Switch(
                        checked = overlayConfig.showCompass,
                        onCheckedChange = { onUpdateOverlayConfig(overlayConfig.copy(showCompass = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor, checkedTrackColor = accentColor)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateOverlayConfig(overlayConfig.copy(showMapInset = !overlayConfig.showMapInset))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live MiniMap Stamp", color = dialogColors.textColor, fontSize = 13.sp)
                    Switch(
                        checked = overlayConfig.showMapInset,
                        onCheckedChange = { onUpdateOverlayConfig(overlayConfig.copy(showMapInset = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor, checkedTrackColor = accentColor)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = onAccentColor)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onNavigateToFullSettings()
                }
            ) {
                Text("All App Settings...", color = dialogColors.textSecondaryColor)
            }
        }
    )
}

/**
 * Converts decimal degrees to DMS string
 */
private fun toDms(dec: Double, isLat: Boolean): String {
    val absVal = Math.abs(dec)
    val deg = absVal.toInt()
    val minDec = (absVal - deg) * 60
    val min = minDec.toInt()
    val sec = (minDec - min) * 60
    val dir = if (isLat) {
        if (dec >= 0) "N" else "S"
    } else {
        if (dec >= 0) "E" else "W"
    }
    return String.format(Locale.US, "%d°%02d'%04.1f\"%s", deg, min, sec, dir)
}

/**
 * Creates a high resolution simulated camera frame with road texture if device camera hardware is unavailable,
 * correctly sized for landscape (1920x1080) or portrait (1080x1920).
 */
fun createSimulatedCameraFrame(
    context: Context,
    rotationDegrees: Int = 0,
    isLandscapeMode: Boolean = false,
    resolutionStr: String = "1920 x 1080",
    aspectRatioStr: String = "4:3"
): Bitmap {
    val shouldBeLandscape = isLandscapeMode || rotationDegrees == 90 || rotationDegrees == 270
    val photoParts = resolutionStr.split("x", "×").mapNotNull { it.trim().toIntOrNull() }
    val maxDim = if (photoParts.size == 2) maxOf(photoParts[0], photoParts[1]) else 1920
    val minDim = if (photoParts.size == 2) minOf(photoParts[0], photoParts[1]) else 1080

    val w = if (shouldBeLandscape) maxDim else minDim
    val h = if (shouldBeLandscape) minDim else maxDim
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val horizonY = h * 0.30f

    // 1. Natural daylight sky (soft atmospheric blue to horizon gradient)
    val skyShader = android.graphics.LinearGradient(
        0f, 0f, 0f, horizonY,
        AndroidColor.rgb(115, 168, 218),
        AndroidColor.rgb(205, 225, 240),
        android.graphics.Shader.TileMode.CLAMP
    )
    val skyPaint = AndroidPaint().apply {
        shader = skyShader
        isAntiAlias = true
    }
    canvas.drawRect(0f, 0f, w.toFloat(), horizonY, skyPaint)

    // 2. Distant hills / terrain skyline
    val hillPaint = AndroidPaint().apply {
        color = AndroidColor.rgb(130, 160, 150)
        isAntiAlias = true
    }
    val hillPath = android.graphics.Path().apply {
        moveTo(0f, horizonY)
        lineTo(w * 0.25f, horizonY - h * 0.04f)
        lineTo(w * 0.55f, horizonY - h * 0.02f)
        lineTo(w * 0.80f, horizonY - h * 0.05f)
        lineTo(w.toFloat(), horizonY)
        close()
    }
    canvas.drawPath(hillPath, hillPaint)

    // 3. Roadside terrain / embankment
    val terrainPaint = AndroidPaint().apply {
        color = AndroidColor.rgb(78, 102, 68)
        isAntiAlias = true
    }
    canvas.drawRect(0f, horizonY, w.toFloat(), h.toFloat(), terrainPaint)

    // 4. Perspective Asphalt Highway Road
    val roadTopWidth = w * 0.22f
    val roadBottomWidth = w * 1.18f
    val roadCenterX = w * 0.50f

    val roadPath = android.graphics.Path().apply {
        moveTo(roadCenterX - roadTopWidth / 2f, horizonY)
        lineTo(roadCenterX + roadTopWidth / 2f, horizonY)
        lineTo(roadCenterX + roadBottomWidth / 2f, h.toFloat())
        lineTo(roadCenterX - roadBottomWidth / 2f, h.toFloat())
        close()
    }
    val roadShader = android.graphics.LinearGradient(
        0f, horizonY, 0f, h.toFloat(),
        AndroidColor.rgb(68, 72, 76),
        AndroidColor.rgb(44, 46, 50),
        android.graphics.Shader.TileMode.CLAMP
    )
    val roadPaint = AndroidPaint().apply {
        shader = roadShader
        isAntiAlias = true
    }
    canvas.drawPath(roadPath, roadPaint)

    // 5. White Road Shoulder / Edge Lines
    val edgePaint = AndroidPaint().apply {
        color = AndroidColor.rgb(245, 245, 245)
        isAntiAlias = true
        strokeWidth = (w * 0.007f).coerceIn(4f, 14f)
        style = AndroidPaint.Style.STROKE
    }
    canvas.drawLine(roadCenterX - roadTopWidth / 2f, horizonY, roadCenterX - roadBottomWidth / 2f, h.toFloat(), edgePaint)
    canvas.drawLine(roadCenterX + roadTopWidth / 2f, horizonY, roadCenterX + roadBottomWidth / 2f, h.toFloat(), edgePaint)

    // 6. Yellow Centerline Dashes (Perspective-scaled)
    val centerLinePaint = AndroidPaint().apply {
        color = AndroidColor.rgb(255, 214, 0)
        isAntiAlias = true
        strokeWidth = (w * 0.009f).coerceIn(5f, 18f)
        strokeCap = AndroidPaint.Cap.ROUND
    }
    val steps = 12
    for (i in 0 until steps) {
        val t1 = Math.pow(i.toDouble() / steps, 1.8).toFloat()
        val t2 = Math.pow((i + 0.52).coerceAtMost(steps.toDouble()) / steps, 1.8).toFloat()
        val y1 = horizonY + (h - horizonY) * t1
        val y2 = horizonY + (h - horizonY) * t2
        canvas.drawLine(roadCenterX, y1, roadCenterX, y2, centerLinePaint)
    }

    val rawSimulated = bmp
    return applyPhotoAspectRatio(
        bitmap = rawSimulated,
        aspectRatioStr = aspectRatioStr,
        isLandscape = shouldBeLandscape
    )
}

@Composable
fun LiveLogoOverlay(
    config: OverlayConfig,
    modifier: Modifier = Modifier
) {
    if (!config.showLogo) return
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val targetWidthPx = with(density) { (config.logoSizeDp.dp).roundToPx() }

    val logoBitmap = remember(config, targetWidthPx) {
        LogoProvider.getProcessedLogo(context, config, targetWidthPx.coerceAtLeast(60))
    }

    if (logoBitmap != null) {
        Image(
            bitmap = logoBitmap.asImageBitmap(),
            contentDescription = "Custom Logo Stamp",
            modifier = modifier
                .alpha(config.logoOpacity.coerceIn(0.1f, 1f))
                .testTag("camera_live_logo_overlay")
        )
    }
}

/**
 * Live Viewfinder Watermark Block that applies user preferences
 * (text color, font size, custom font family, text shadow, and background box) exactly as configured.
 */
@Composable
fun LiveViewfinderWatermarkBlock(
    liveLines: List<String>,
    overlayConfig: OverlayConfig,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    val textColor = remember(overlayConfig.textColorHex) {
        try {
            Color(android.graphics.Color.parseColor(overlayConfig.textColorHex))
        } catch (e: Exception) {
            Color.White
        }
    }

    val overlayFontFamily = remember(overlayConfig.fontFamily) {
        when (overlayConfig.fontFamily.lowercase(Locale.US)) {
            "monospace", "inconsolata", "roboto mono" -> FontFamily.Monospace
            "serif", "playfair display", "merriweather" -> FontFamily.Serif
            "sans-serif", "sans-serif-condensed", "roboto", "open sans", "montserrat", "oswald", "lato" -> FontFamily.SansSerif
            "cursive" -> FontFamily.Cursive
            else -> FontFamily.Default
        }
    }

    val baseFontSizeSp = overlayConfig.fontSizeSp.coerceIn(10f, 32f)
    val scaleFactor = if (isLandscape) 0.82f else 1.0f
    val primaryFontSize = (baseFontSizeSp * scaleFactor).sp
    val secondaryFontSize = (baseFontSizeSp * 0.88f * scaleFactor).sp

    val textShadow = if (overlayConfig.hasTextShadow) {
        androidx.compose.ui.graphics.Shadow(
            color = Color.Black,
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    val bgModifier = if (!isLandscape && (overlayConfig.backgroundOpacity > 0.05f || overlayConfig.backgroundStyle != "Transparent")) {
        val bgBoxColor = remember(overlayConfig.backgroundColorHex, overlayConfig.backgroundOpacity) {
            try {
                val base = android.graphics.Color.parseColor(overlayConfig.backgroundColorHex)
                val alpha = (overlayConfig.backgroundOpacity.coerceIn(0.1f, 1f) * 255).toInt().coerceIn(0, 255)
                Color(android.graphics.Color.argb(alpha, android.graphics.Color.red(base), android.graphics.Color.green(base), android.graphics.Color.blue(base)))
            } catch (e: Exception) {
                Color(0x99000000)
            }
        }
        Modifier
            .background(bgBoxColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    } else {
        Modifier.background(Color.Transparent)
    }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier.then(bgModifier)
    ) {
        liveLines.forEachIndexed { index, line ->
            val isLast = index == liveLines.lastIndex
            Text(
                text = line,
                color = textColor,
                fontSize = if (isLast) primaryFontSize else secondaryFontSize,
                fontWeight = if (isLast) FontWeight.Black else FontWeight.Bold,
                fontFamily = overlayFontFamily,
                textAlign = TextAlign.End,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = textShadow
                )
            )
        }
    }
}

/**
 * Modern minimalist floating glass card with uniform 4dp blur and 10% opacity.
 * Provides high-contrast readability against translucent background while staying compact and dynamic in height.
 */
@Composable
fun FloatingGlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    backgroundColor: Color = Color(0x1A101726), // 10% opacity dark glass
    borderColor: Color = Color(0x35FFFFFF), // Translucent frosted border
    borderWidth: androidx.compose.ui.unit.Dp = 0.5.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = shape,
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(4.dp)
                    .background(backgroundColor)
            )
            content()
        }
    }
}
