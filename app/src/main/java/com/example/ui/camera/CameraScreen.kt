package com.example.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.content.res.Configuration
import androidx.camera.view.LifecycleCameraController
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val controller = remember {
        LifecycleCameraController(context).apply {
            try {
                setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            } catch (ignored: Exception) {}
        }
    }
    val videoManager = remember { VideoRecordingManager(context) }

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

    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val matchResult by viewModel.liveMatchResult.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()
    val overlayConfig by viewModel.overlayConfig.collectAsStateWithLifecycle()
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isTopToolbarExpanded by remember { mutableStateOf(true) }
    var showLaneMenu by remember { mutableStateOf(false) }
    var isHdrEnabled by remember { mutableStateOf(false) }

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
        videoManager.startRecording(
            controller = controller,
            recordAudio = audioEnabled,
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
                        val finalRecFile = recFile ?: videoManager.currentRecordingFile ?: File(context.cacheDir, "temp_video/raw_video.mp4")
                        viewModel.processRecordedVideo(context, finalRecFile)
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
                    onSuccess = { bitmap -> viewModel.processCapture(bitmap) },
                    onError = {
                        val fallback = createSimulatedCameraFrame(
                            context = context,
                            rotationDegrees = rotationDegrees.toInt(),
                            isLandscapeMode = isLandscape,
                            resolutionStr = cameraSettings.photoResolution
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
                onSuccess = { bitmap -> viewModel.processCapture(bitmap) },
                onError = {
                    val fallback = createSimulatedCameraFrame(
                        context = context,
                        rotationDegrees = rotationDegrees.toInt(),
                        isLandscapeMode = isLandscape,
                        resolutionStr = cameraSettings.photoResolution
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
            controller = controller,
            currentSnapshotProvider = { viewModel.createCurrentSnapshot() },
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
            // LANDSCAPE CAMERA CONTROLS (MATCHING REFERENCE SCREENSHOT)
            // ==========================================================

            // 1. TOP BAR: Collapsible Smart Toolbar (Left) & Amber Status Checkmark (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Chevron Toggle + Expandable Toolbar Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Smart Toolbar Show/Hide Toggle Button
                    IconButton(
                        onClick = { isTopToolbarExpanded = !isTopToolbarExpanded },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x751A1A1A))
                            .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .testTag("landscape_toolbar_toggle")
                    ) {
                        Icon(
                            imageVector = if (isTopToolbarExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isTopToolbarExpanded) "Hide Camera Controls" else "Show Camera Controls",
                            tint = PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Expandable Smart Camera Secondary Controls
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
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x751A1A1A))
                                    .border(0.8.dp, if (isFlashOn) HighlightYellow else Color.White.copy(alpha = 0.25f), CircleShape)
                                    .testTag("landscape_flash_button")
                            ) {
                                Icon(
                                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Toggle Flash",
                                    tint = if (isFlashOn) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // 2. HDR Toggle Button
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x751A1A1A))
                                    .border(0.8.dp, if (isHdrEnabled) HighlightYellow else Color.White.copy(alpha = 0.25f), CircleShape)
                                    .clickable {
                                        isHdrEnabled = !isHdrEnabled
                                        viewModel.showToast(if (isHdrEnabled) "HDR Mode Active" else "HDR Mode Disabled", ToastType.SETTINGS_SAVED)
                                    }
                                    .testTag("landscape_hdr_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isHdrEnabled) HighlightYellow else Color.Transparent,
                                    border = if (isHdrEnabled) null else androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.8f))
                                ) {
                                    Text(
                                        text = "HDR",
                                        color = if (isHdrEnabled) Color.Black else PrimaryText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // 3. Grid Toggle Button
                            IconButton(
                                onClick = { viewModel.cycleGrid() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x751A1A1A))
                                    .border(0.8.dp, if (cameraSettings.gridMode != CameraGridMode.OFF) HighlightYellow else Color.White.copy(alpha = 0.25f), CircleShape)
                                    .testTag("landscape_grid_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = "Grid: ${cameraSettings.gridMode.label}",
                                    tint = if (cameraSettings.gridMode != CameraGridMode.OFF) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // 4. Aspect Ratio Button (16:9 badge)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x751A1A1A))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                    .clickable {
                                        viewModel.showInfo("Aspect ratio: 16:9 Full Screen Viewfinder")
                                    }
                                    .testTag("landscape_aspect_ratio_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.8f))
                                ) {
                                    Text(
                                        text = "16:9",
                                        color = PrimaryText,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // 5. Timer Cycle Button
                            IconButton(
                                onClick = { viewModel.cycleTimer() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x751A1A1A))
                                    .border(0.8.dp, if (cameraSettings.captureTimer != CaptureTimer.OFF) HighlightYellow else Color.White.copy(alpha = 0.25f), CircleShape)
                                    .testTag("landscape_timer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Timer: ${cameraSettings.captureTimer.label}",
                                    tint = if (cameraSettings.captureTimer != CaptureTimer.OFF) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // 6. Focus Frame / Horizon Leveler Toggle Button
                            IconButton(
                                onClick = {
                                    val nextLevel = !cameraSettings.showHorizonLevel
                                    viewModel.saveCameraSettingsSilent(cameraSettings.copy(showHorizonLevel = nextLevel))
                                    viewModel.showToast(if (nextLevel) "Horizon level on" else "Horizon level off", ToastType.SETTINGS_SAVED)
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x751A1A1A))
                                    .border(0.8.dp, if (cameraSettings.showHorizonLevel) HighlightYellow else Color.White.copy(alpha = 0.25f), CircleShape)
                                    .testTag("landscape_level_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterCenterFocus,
                                    contentDescription = "Horizon Leveler",
                                    tint = if (cameraSettings.showHorizonLevel) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // 7. Settings Gear Button
                            IconButton(
                                onClick = { showQuickSettingsPopup = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x751A1A1A))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                    .testTag("landscape_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Camera Settings",
                                    tint = PrimaryText,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                // Center if recording: Recording pill
                if (isRecording) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rec")
                    val recAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(tween(500), repeatMode = RepeatMode.Reverse),
                        label = "recording_pulse"
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xD90F172A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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

                // Right: Amber Status / Preset Checkmark Button (matching screenshot)
                Box(
                    modifier = Modifier
                        .padding(end = 92.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x40FBBF24))
                        .border(0.8.dp, Color(0xFFFBBF24).copy(alpha = 0.7f), CircleShape)
                        .clickable {
                            viewModel.showToast("Road Alignment: ${activeRoute?.name ?: "Active"}", ToastType.INFO)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Status OK",
                        tint = HighlightYellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. LEFT CORNER: HIGHWAY-RELATED CONTROLS (Vertical Column)
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 56.dp, start = 14.dp, bottom = 12.dp)
                    .width(152.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Control 1: MiniMap Preview Card
                MiniMapPreview(
                    location = locationState.point,
                    bearing = locationState.bearing,
                    routeData = activeRoute,
                    highwayTag = projectProfile.highwayId.ifBlank { "MH" },
                    gpsAccuracy = locationState.accuracy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(106.dp)
                        .clickable { showSimDialog = true }
                        .testTag("landscape_minimap_preview")
                )

                // Control 2: LHS / RHS Segmented Pill
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0x751A1A1A),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
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
                                    fontSize = 12.sp,
                                    fontWeight = if (isLhs) FontWeight.Black else FontWeight.Medium
                                )
                            }
                        }

                        // Divider line
                        Box(
                            modifier = Modifier
                                .width(0.8.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.15f))
                        )

                        // RHS Segment
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
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
                                    fontSize = 12.sp,
                                    fontWeight = if (isRhs) FontWeight.Black else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Control 3: Lane Selector Dropdown Pill
                Box {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0x751A1A1A),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Lane",
                                tint = SecondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLaneMenu,
                        onDismissRequest = { showLaneMenu = false },
                        modifier = Modifier.background(SurfaceDark)
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

                // Control 4: Activity Selector Dropdown Pill
                Box {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0x751A1A1A),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
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
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = projectProfile.activity.ifBlank { "BC" },
                                    color = PrimaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select Activity",
                                tint = SecondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showActivityMenu,
                        onDismissRequest = { showActivityMenu = false },
                        modifier = Modifier.background(SurfaceDark)
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

                // Control 5: Chainage Adjustment Pill
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0x751A1A1A),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, if (matchResult.isManual) AccentRed else Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
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
                            RoadMarkingIcon(tint = if (matchResult.isManual) AccentRed else PrimaryText)
                            Text(
                                text = "CH: ${matchResult.displayChainage}",
                                color = PrimaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Chainage",
                            tint = SecondaryText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Control 6: Comment Pill
                val hasComment = projectProfile.customDescription.isNotBlank() && !projectProfile.customDescription.equals("None", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (hasComment) Color(0x990284C7) else Color(0x751A1A1A),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, if (hasComment) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
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
                            color = PrimaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 3. EXPOSURE COMPENSATION SLIDER (When Toggled)
            if (showExposureSlider) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xD90F172A),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, HighlightYellow),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 58.dp)
                        .width(280.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                                inactiveTrackColor = SecondaryText.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. BOTTOM-RIGHT TIMESTAMP & WATERMARK OVERLAY
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 96.dp, bottom = 12.dp)
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

                liveLines.forEachIndexed { index, line ->
                    val isLast = index == liveLines.lastIndex
                    Text(
                        text = line,
                        color = PrimaryText,
                        fontSize = if (isLast) 15.sp else 13.sp,
                        fontWeight = if (isLast) FontWeight.Black else FontWeight.Bold,
                        textAlign = TextAlign.End,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                offset = Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        )
                    )
                }
            }

            // 5. RIGHT CORNER: CAPTURE & RECORDING CONTROLS (Authoritative Vertical Control Bar)
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                color = Color(0xD9090D16),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(82.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. Gallery Recent Photo Shortcut (Top)
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .background(Color(0x501A1A1A))
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
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // 2. Camera Flip Button
                        IconButton(
                            onClick = { viewModel.toggleCameraFacing() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x751A1A1A))
                                .border(0.8.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .testTag("landscape_camera_flip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Flip Camera",
                                tint = PrimaryText,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 3. Main Shutter Button (Prominent circular glass button with aperture iris icon)
                        if (isRecording) {
                            // Quick Photo Capture while Video Recording
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x751A1A1A))
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
                                            onSuccess = { bitmap ->
                                                viewModel.processCapture(bitmap)
                                                viewModel.showSuccess("Quick photo saved to Gallery!")
                                            },
                                            onError = {
                                                val fallback = createSimulatedCameraFrame(
                                                    context = context,
                                                    rotationDegrees = rotationDegrees.toInt(),
                                                    isLandscapeMode = isLandscape,
                                                    resolutionStr = cameraSettings.photoResolution
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
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .border(3.5.dp, Color.White.copy(alpha = 0.95f), CircleShape)
                                    .background(Color.White)
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
                                        color = Color.Black,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    CameraApertureIcon(tint = Color.Black)
                                }
                            }
                        }

                        // 4. Video Recording Button (Red Circle Dot / Stop Square) or Pause/Resume
                        if (isRecording) {
                            // Fixed Stop Video Recording Button
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(AccentRed)
                                    .border(2.dp, Color.White, CircleShape)
                                    .clickable { toggleVideoRecording() }
                                    .testTag("landscape_stop_video_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PrimaryText)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x751A1A1A))
                                    .border(1.dp, if (captureMode == CaptureMode.VIDEO) AccentRed else Color.White.copy(alpha = 0.3f), CircleShape)
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
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(AccentRed)
                                )
                            }
                        }

                        // 5. Bottom Action: Pause/Resume if recording, else Sliders / Quick settings button
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
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x751A1A1A))
                                    .border(0.8.dp, if (isRecordingPaused) HighlightYellow else Color.White.copy(alpha = 0.25f), CircleShape)
                                    .testTag("landscape_pause_resume_button")
                            ) {
                                Icon(
                                    imageVector = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isRecordingPaused) "Resume Recording" else "Pause Recording",
                                    tint = if (isRecordingPaused) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { showExposureSlider = !showExposureSlider },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (showExposureSlider) DarkYellowSurface else Color(0x751A1A1A))
                                    .border(0.8.dp, if (showExposureSlider) HighlightYellow else Color.White.copy(alpha = 0.2f), CircleShape)
                                    .testTag("landscape_tune_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Exposure / Settings",
                                    tint = if (showExposureSlider) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
        } else {
            // 6. Top Bar: Fully Transparent Single-Icon Controls & Status (PORTRAIT)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp, bottom = 6.dp, start = 8.dp, end = 8.dp)
            ) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    // Flash cycle button
                    IconButton(
                        onClick = { viewModel.toggleFlash() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ElevatedSurface)
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .testTag("flash_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (isFlashOn) HighlightYellow else PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                item {
                    // Video Audio Mic On/Off Toggle Button
                    IconButton(
                        onClick = { viewModel.toggleRecordAudio() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (cameraSettings.recordAudio) DarkYellowSurface else ElevatedSurface)
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .testTag("audio_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (cameraSettings.recordAudio) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (cameraSettings.recordAudio) "Audio Enabled" else "Audio Disabled",
                            tint = if (cameraSettings.recordAudio) HighlightYellow else SecondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                item {
                    // Timer cycle button (single compact icon)

                    IconButton(
                        onClick = { viewModel.cycleTimer() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (cameraSettings.captureTimer != CaptureTimer.OFF) DarkYellowSurface else ElevatedSurface)
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .testTag("timer_cycle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Timer",
                            tint = if (cameraSettings.captureTimer != CaptureTimer.OFF) HighlightYellow else PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                item {
                    // Grid cycle button
                    IconButton(
                        onClick = { viewModel.cycleGrid() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (cameraSettings.gridMode != CameraGridMode.OFF) DarkYellowSurface else ElevatedSurface)
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .testTag("grid_cycle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Grid",
                            tint = if (cameraSettings.gridMode != CameraGridMode.OFF) HighlightYellow else SecondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                item {
                    // Exposure Compensation Button
                    IconButton(
                        onClick = { showExposureSlider = !showExposureSlider },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (showExposureSlider) DarkYellowSurface else ElevatedSurface)
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .testTag("exposure_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Exposure,
                            contentDescription = "Exposure",
                            tint = if (exposureCompensation != 0) HighlightYellow else PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                item {
                    // Flip Camera
                    IconButton(
                        onClick = { viewModel.toggleCameraFacing() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ElevatedSurface)
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .testTag("camera_flip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera",
                            tint = PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                item {
                    // Tiny single-icon Route button
                    IconButton(
                        onClick = { onNavigateToRoutes() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (activeRoute != null) DarkYellowSurface else ElevatedSurface)
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .testTag("routes_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AltRoute,
                            contentDescription = "Routes",
                            tint = if (activeRoute != null) HighlightYellow else PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                item {
                    // Tiny single-icon Settings gear button
                    IconButton(
                        onClick = { showQuickSettingsPopup = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (showQuickSettingsPopup) DarkYellowSurface else ElevatedSurface)
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Camera Settings",
                            tint = if (showQuickSettingsPopup) HighlightYellow else PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Compact Status Row: GPS Status & Live Compass Heading
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAcquiring = locationState.status == LocationStatus.ACQUIRING || locationState.point == null
                val accuracy = if (locationState.point != null) locationState.accuracy else 999f

                val gpsColor = when {
                    isAcquiring -> Color(0xFF38BDF8) // Acquiring blue/cyan
                    accuracy <= 5f -> Color(0xFF22C55E) // Green: Excellent (<= 5 m)
                    accuracy <= 15f -> HighlightYellow // Yellow: Moderate (> 5 m to 15 m)
                    accuracy <= 30f -> Color(0xFFF97316) // Orange: Low (> 15 m to 30 m)
                    else -> AccentRed // Red: Poor (> 30 m) or unavailable
                }

                val infiniteTransition = rememberInfiniteTransition(label = "gps_pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
                    label = "gps_pulse"
                )
                val indicatorAlpha = if (isAcquiring) pulseAlpha else 1.0f

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceDark.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, gpsColor.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable { showSimDialog = true }
                        .testTag("gps_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(gpsColor)
                                .alpha(indicatorAlpha)
                        )
                        Text(
                            text = if (locationState.point != null) {
                                String.format(Locale.US, "GPS ±%.0fm", locationState.accuracy)
                            } else {
                                "GPS Acquiring..."
                            },
                            color = PrimaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (overlayConfig.showCompass) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceDark.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🧭 ${compassState.displayHeading}",
                                color = HighlightYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Expandable Exposure EV Slider
            if (showExposureSlider) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "EV: ${if (exposureCompensation > 0) "+$exposureCompensation" else "$exposureCompensation"}",
                        color = HighlightYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(54.dp)
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
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Reset", color = PrimaryText, fontSize = 11.sp)
                    }
                }
            }

            // Video Recording Banner with Pulsing Red Dot and Time Counter
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
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceDark.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(AccentRed.copy(alpha = alpha))
                            )
                            val mins = recordingDurationSec / 60
                            val secs = recordingDurationSec % 60
                            Text(
                                text = String.format("REC %02d:%02d", mins, secs),
                                color = PrimaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isRecordingPaused) {
                                Text(
                                    text = "[PAUSED]",
                                    color = HighlightYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. Unified Bottom Controls & Overlay HUD Container (Guarantees zero overlapping between HUD, field chips, mode selector, and shutter bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 10.dp)
        ) {
            // Live Preview Overlay (Mini-Map on lower-left, Metadata Text on lower-right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Lower-Left: Live Mini Map
                if (overlayConfig.showMapInset && overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
                    MiniMapPreview(
                        location = locationState.point,
                        bearing = locationState.bearing,
                        routeData = activeRoute,
                        highwayTag = projectProfile.highwayId.ifBlank { "MH" },
                        gpsAccuracy = locationState.accuracy,
                        modifier = Modifier
                            .size(width = 110.dp, height = 80.dp)
                            .clickable { showSimDialog = true }
                            .testTag("portrait_minimap_preview")
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Lower-Right: Information Block (Right-aligned text)
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                ) {
                    // Logo when ABOVE_TEXT position is selected
                    if (overlayConfig.showLogo && overlayConfig.logoPosition == LogoPosition.ABOVE_TEXT) {
                        LiveLogoOverlay(
                            config = overlayConfig,
                            modifier = Modifier.padding(bottom = 6.dp)
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

                    liveLines.forEachIndexed { index, line ->
                        val isLast = index == liveLines.lastIndex
                        Text(
                            text = line,
                            color = PrimaryText,
                            fontSize = if (isLast) 18.sp else 16.sp,
                            fontWeight = if (isLast) FontWeight.Black else FontWeight.Bold,
                            textAlign = TextAlign.End,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Minimalist Glass Field Selectors (Side, Lane, Activity, Chainage)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Side chip (RHS / LHS / BOTH / MEDIAN)
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x701A1A1A), // Translucent dark glass
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, BorderColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable {
                                val nextSide = when (projectProfile.roadSide) {
                                    RoadSide.RHS -> RoadSide.LHS
                                    RoadSide.LHS -> RoadSide.MEDIAN
                                    RoadSide.MEDIAN -> RoadSide.BOTH
                                    RoadSide.BOTH -> RoadSide.RHS
                                }
                                viewModel.updateSide(nextSide)
                            }
                            .testTag("side_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(HighlightYellow)
                            )
                            Text(
                                text = projectProfile.roadSide.name,
                                color = HighlightYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Lane chip
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x701A1A1A),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, BorderColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable {
                                val lanes = ProjectProfile.LANE_PRESETS
                                val nextIndex = (lanes.indexOf(projectProfile.lane) + 1) % lanes.size
                                viewModel.updateLane(lanes[nextIndex])
                            }
                            .testTag("lane_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = PrimaryText,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = projectProfile.lane,
                                color = PrimaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Activity chip
                item {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x701A1A1A),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, BorderColor.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(36.dp)
                                .clickable { showActivityMenu = true }
                                .testTag("activity_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                                    color = PrimaryText,
                                    fontSize = 11.sp,
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (matchResult.isManual) Color(0x99DC2626) else Color(0x701A1A1A),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, if (matchResult.isManual) AccentRed else BorderColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable { viewModel.openManualChainageDialog() }
                            .testTag("chainage_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Chainage",
                                tint = PrimaryText,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (matchResult.isManual) "Manual: ${matchResult.displayChainage}" else "CH: ${matchResult.displayChainage}",
                                color = PrimaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Custom Comment chip
                item {
                    val hasComment = projectProfile.customDescription.isNotBlank() && !projectProfile.customDescription.equals("None", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasComment) Color(0x990284C7) else Color(0x701A1A1A),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, if (hasComment) Color(0xFF38BDF8) else BorderColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable {
                                commentInput = projectProfile.customDescription
                                showCustomCommentDialog = true
                            }
                            .testTag("custom_comment_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Custom Comment",
                                tint = if (hasComment) Color(0xFF38BDF8) else PrimaryText,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (hasComment) "Comment: ${projectProfile.customDescription}" else "+ Comment",
                                color = PrimaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Mode Selector Pill: PHOTO | VIDEO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x701A1A1A),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (captureMode == CaptureMode.PHOTO) HighlightYellow else Color.Transparent)
                                .clickable { viewModel.setCaptureMode(CaptureMode.PHOTO) }
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "PHOTO",
                                color = if (captureMode == CaptureMode.PHOTO) PrimaryBackground else SecondaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (captureMode == CaptureMode.VIDEO) AccentRed else Color.Transparent)
                                .clickable { viewModel.setCaptureMode(CaptureMode.VIDEO) }
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "VIDEO",
                                color = if (captureMode == CaptureMode.VIDEO) PrimaryText else SecondaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom Shutter Bar (Gallery Thumbnail, Fixed Shutter, Quick Location Simulator)
            // Bottom Shutter Bar (Normal Mode vs Active Video Recording Mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    // 1. Quick Photo Capture Button during video recording
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x701A1A1A))
                            .border(1.dp, HighlightYellow, CircleShape)
                            .graphicsLayer { rotationZ = -animatedRotation }
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
                                    onSuccess = { bitmap ->
                                        viewModel.processCapture(bitmap)
                                        viewModel.showSuccess("Quick photo captured & saved to Gallery!")
                                    },
                                    onError = {
                                        val fallback = createSimulatedCameraFrame(
                                            context = context,
                                            rotationDegrees = rotationDegrees.toInt(),
                                            isLandscapeMode = isLandscape,
                                            resolutionStr = cameraSettings.photoResolution
                                        )
                                        viewModel.processCapture(fallback)
                                        viewModel.showSuccess("Quick photo captured & saved to Gallery!")
                                    }
                                )
                            }
                            .testTag("quick_photo_capture_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = "Quick Photo Capture",
                            tint = HighlightYellow,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 2. Fixed Stop Video Recording Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(AccentRed)
                            .clickable { toggleVideoRecording() }
                            .testTag("stop_video_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryText)
                        )
                    }

                    // 3. Pause / Resume Video Recording Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x701A1A1A))
                            .border(1.dp, if (isRecordingPaused) HighlightYellow else Color.White.copy(alpha = 0.5f), CircleShape)
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .clickable {
                                if (isRecordingPaused) {
                                    videoManager.resume()
                                    viewModel.onVideoRecordingPaused(false)
                                    viewModel.showInfo("Video recording resumed")
                                } else {
                                    videoManager.pause()
                                    viewModel.onVideoRecordingPaused(true)
                                    viewModel.showInfo("Video recording paused")
                                }
                            }
                            .testTag("pause_resume_video_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isRecordingPaused) "Resume Recording" else "Pause Recording",
                            tint = if (isRecordingPaused) HighlightYellow else PrimaryText,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    // Normal Shutter Bar (When NOT recording video)
                    // Recent Thumbnail / Gallery Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .background(Color(0x501A1A1A))
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .clickable { onNavigateToPhotos() }
                            .testTag("recent_thumbnail_button"),
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Fixed Capture Shutter Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (captureMode == CaptureMode.PHOTO) {
                                    if (isCapturing) AccentRed else PrimaryText
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
                                    color = PrimaryText,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = "Take Photo",
                                    tint = PrimaryBackground,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .graphicsLayer { rotationZ = -animatedRotation }
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(DeepRed)
                            )
                        }
                    }

                    // GPS Simulator / Preset Button
                    IconButton(
                        onClick = { showSimDialog = true },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x501A1A1A))
                            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .graphicsLayer { rotationZ = -animatedRotation }
                            .testTag("gps_sim_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Simulate Location",
                            tint = HighlightYellow,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

        // Manual Chainage Dialog
        if (showManualDialog) {
            var inputVal by remember { mutableStateOf(matchResult.chainage?.format() ?: "196+040") }
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            AlertDialog(
                onDismissRequest = { viewModel.closeManualChainageDialog() },
                containerColor = SurfaceDark,
                title = { Text("Highway Chainage", color = PrimaryText, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Enter chainage for this location (e.g. 196+040). Auto GPS will be overridden until cleared.",
                            fontSize = 13.sp,
                            color = SecondaryText
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = inputVal,
                            onValueChange = { inputVal = it },
                            label = { Text("Chainage (km+m)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    viewModel.setManualChainage(inputVal)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("manual_chainage_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.setManualChainage(inputVal) },
                        colors = ButtonDefaults.buttonColors(containerColor = HighlightYellow, contentColor = PrimaryBackground),
                        modifier = Modifier.testTag("save_manual_chainage_button")
                    ) {
                        Text("Set Chainage", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    if (matchResult.isManual) {
                        TextButton(onClick = { viewModel.clearManualChainage() }) {
                            Text("Reset to Auto GPS", color = AccentRed)
                        }
                    } else {
                        TextButton(onClick = { viewModel.closeManualChainageDialog() }) {
                            Text("Cancel", color = SecondaryText)
                        }
                    }
                }
            )
        }

        // Manual Activity / Layer Dialog
        if (showManualActivityDialog) {
            var activityInput by remember { mutableStateOf(projectProfile.activity) }
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            AlertDialog(
                onDismissRequest = { showManualActivityDialog = false },
                containerColor = SurfaceDark,
                title = { Text("Custom Activity / Pavement Layer", color = PrimaryText, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Type custom activity or pavement layer (e.g. Main CW to MCW):",
                            fontSize = 13.sp,
                            color = SecondaryText
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
                                focusedBorderColor = HighlightYellow,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = PrimaryText,
                                unfocusedTextColor = PrimaryText
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
                        colors = ButtonDefaults.buttonColors(containerColor = HighlightYellow, contentColor = PrimaryBackground),
                        modifier = Modifier.testTag("save_manual_activity_button")
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualActivityDialog = false }) {
                        Text("Cancel", color = PrimaryText)
                    }
                }
            )
        }

        // Custom Comment Dialog
        if (showCustomCommentDialog) {
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            AlertDialog(
                onDismissRequest = { showCustomCommentDialog = false },
                containerColor = SurfaceDark,
                title = { Text("Custom Comment Watermark", color = PrimaryText, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Add a custom comment line. When set, this will be displayed dynamically on the next line under the highway timestamp watermark.",
                            fontSize = 13.sp,
                            color = SecondaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            label = { Text("Custom Comment") },
                            placeholder = { Text("e.g. Culvert Inspection / Patch Work", color = SecondaryText.copy(alpha = 0.6f)) },
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
                                focusedBorderColor = HighlightYellow,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = PrimaryText,
                                unfocusedTextColor = PrimaryText
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
                        colors = ButtonDefaults.buttonColors(containerColor = HighlightYellow, contentColor = PrimaryBackground),
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
                            Text("Cancel", color = PrimaryText)
                        }
                    }
                }
            )
        }

        // GPS Simulation Dialog
        if (showSimDialog) {
            AlertDialog(
                onDismissRequest = { showSimDialog = false },
                containerColor = SurfaceDark,
                title = { Text("GPS Location Test Presets", color = PrimaryText, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Select a GPS position along NH-58 to test chainage matching and permanent overlays immediately:",
                            fontSize = 13.sp,
                            color = SecondaryText
                        )

                        Button(
                            onClick = {
                                viewModel.simulateGps(29.9183, 78.1064, 2.5f)
                                showSimDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface, contentColor = PrimaryText),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reference Photo (196+040, 29.9183N 78.1064E)")
                        }

                        Button(
                            onClick = {
                                viewModel.simulateGps(29.8680, 77.9250, 3.0f)
                                showSimDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface, contentColor = PrimaryText),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Roorkee Section (185+000, 29.8680N 77.9250E)")
                        }

                        Button(
                            onClick = {
                                viewModel.simulateGps(29.4750, 77.7120, 3.5f)
                                showSimDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface, contentColor = PrimaryText),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Muzaffarnagar Start (130+000, 29.4750N 77.7120E)")
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.startLocation()
                                showSimDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use Real Device Hardware GPS", color = HighlightYellow)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSimDialog = false }) {
                        Text("Close", color = PrimaryText)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
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
                        tint = HighlightYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Camera Settings", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = SecondaryText
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
                Text("HARDWARE & CAPTURE", color = HighlightYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                // Flash Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggleFlash() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Camera Flash", color = PrimaryText, fontSize = 13.sp)
                    Switch(
                        checked = isFlashOn,
                        onCheckedChange = { onToggleFlash() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = HighlightYellow)
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
                    Text("Shutter Sound", color = PrimaryText, fontSize = 13.sp)
                    Switch(
                        checked = cameraSettings.captureSound,
                        onCheckedChange = { onUpdateCameraSettings(cameraSettings.copy(captureSound = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = HighlightYellow)
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
                    Text("Touch to Take Photo", color = PrimaryText, fontSize = 13.sp)
                    Switch(
                        checked = cameraSettings.touchToCapture,
                        onCheckedChange = { onUpdateCameraSettings(cameraSettings.copy(touchToCapture = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = HighlightYellow)
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
                    Text("Electronic Level Indicator", color = PrimaryText, fontSize = 13.sp)
                    Switch(
                        checked = cameraSettings.showHorizonLevel,
                        onCheckedChange = { onUpdateCameraSettings(cameraSettings.copy(showHorizonLevel = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = HighlightYellow)
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
                    Text("Save Clean Original Photo", color = PrimaryText, fontSize = 13.sp)
                    Switch(
                        checked = cameraSettings.saveOriginalAndStamped,
                        onCheckedChange = { onUpdateCameraSettings(cameraSettings.copy(saveOriginalAndStamped = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = HighlightYellow)
                    )
                }

                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

                // Section 2: Grid & Timer
                Text("GRID & COUNTDOWN TIMER", color = HighlightYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                // Grid Mode Selector
                Column {
                    Text("Viewfinder Grid", color = SecondaryText, fontSize = 12.sp)
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
                                    selectedContainerColor = HighlightYellow,
                                    selectedLabelColor = Color.Black,
                                    containerColor = ElevatedSurface,
                                    labelColor = PrimaryText
                                )
                            )
                        }
                    }
                }

                // Timer Selector
                Column {
                    Text("Capture Timer", color = SecondaryText, fontSize = 12.sp)
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
                                    selectedContainerColor = HighlightYellow,
                                    selectedLabelColor = Color.Black,
                                    containerColor = ElevatedSurface,
                                    labelColor = PrimaryText
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))

                // Section 3: Watermark Overlay Toggles
                Text("CAMERA WATERMARK OVERLAYS", color = HighlightYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateOverlayConfig(overlayConfig.copy(showTimestamp = !overlayConfig.showTimestamp))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Timestamp Stamp", color = PrimaryText, fontSize = 13.sp)
                    Switch(
                        checked = overlayConfig.showTimestamp,
                        onCheckedChange = { onUpdateOverlayConfig(overlayConfig.copy(showTimestamp = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = HighlightYellow)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateOverlayConfig(overlayConfig.copy(showCoordinates = !overlayConfig.showCoordinates))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GPS Coordinates Stamp", color = PrimaryText, fontSize = 13.sp)
                    Switch(
                        checked = overlayConfig.showCoordinates,
                        onCheckedChange = { onUpdateOverlayConfig(overlayConfig.copy(showCoordinates = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = HighlightYellow)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateOverlayConfig(overlayConfig.copy(showCompass = !overlayConfig.showCompass))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Compass Heading Stamp", color = PrimaryText, fontSize = 13.sp)
                    Switch(
                        checked = overlayConfig.showCompass,
                        onCheckedChange = { onUpdateOverlayConfig(overlayConfig.copy(showCompass = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = HighlightYellow)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onUpdateOverlayConfig(overlayConfig.copy(showMapInset = !overlayConfig.showMapInset))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live MiniMap Stamp", color = PrimaryText, fontSize = 13.sp)
                    Switch(
                        checked = overlayConfig.showMapInset,
                        onCheckedChange = { onUpdateOverlayConfig(overlayConfig.copy(showMapInset = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = HighlightYellow)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = HighlightYellow, contentColor = Color.Black)
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
                Text("All App Settings...", color = PrimaryText)
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
    resolutionStr: String = "1920 x 1080"
): Bitmap {
    val shouldBeLandscape = isLandscapeMode || rotationDegrees == 90 || rotationDegrees == 270
    val photoParts = resolutionStr.split("x", "×").mapNotNull { it.trim().toIntOrNull() }
    val maxDim = if (photoParts.size == 2) maxOf(photoParts[0], photoParts[1]) else 1920
    val minDim = if (photoParts.size == 2) minOf(photoParts[0], photoParts[1]) else 1080

    val w = if (shouldBeLandscape) maxDim else minDim
    val h = if (shouldBeLandscape) minDim else maxDim
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Dark asphalt road color
    val roadPaint = AndroidPaint().apply { color = AndroidColor.rgb(20, 20, 20) }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), roadPaint)

    // Sky / Horizon at top
    val skyPaint = AndroidPaint().apply { color = AndroidColor.rgb(40, 40, 40) }
    canvas.drawRect(0f, 0f, w.toFloat(), h * 0.35f, skyPaint)

    // Centerline markings (Yellow)
    val linePaint = AndroidPaint().apply {
        color = AndroidColor.rgb(255, 214, 0)
        strokeWidth = 14f
    }
    val startY = (h * 0.4f).toInt()
    for (y in startY until h step 140) {
        canvas.drawLine(w * 0.5f, y.toFloat(), w * 0.5f, (y + 80).toFloat(), linePaint)
    }

    return bmp
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
