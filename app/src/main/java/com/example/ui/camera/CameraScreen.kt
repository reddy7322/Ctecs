package com.example.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.camera.view.LifecycleCameraController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun CameraScreen(
    viewModel: MainViewModel,
    onNavigateToPhotos: () -> Unit,
    onNavigateToRoutes: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val controller = remember { LifecycleCameraController(context) }
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
    var showExposureSlider by remember { mutableStateOf(false) }
    var showZoomSlider by remember { mutableStateOf(false) }

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

    // Video Recording Timer ticker
    LaunchedEffect(isRecording, isRecordingPaused) {
        if (isRecording && !isRecordingPaused) {
            var elapsed = recordingDurationSec
            while (isRecording && !isRecordingPaused) {
                delay(1000L)
                elapsed++
                viewModel.onVideoRecordingTick(elapsed)
            }
        }
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
                    onSuccess = { bitmap -> viewModel.processCapture(bitmap) },
                    onError = {
                        val fallback = createSimulatedCameraFrame(context)
                        viewModel.processCapture(fallback)
                    }
                )
            }
        } else {
            capturePhoto(
                controller = controller,
                context = context,
                onSuccess = { bitmap -> viewModel.processCapture(bitmap) },
                onError = {
                    val fallback = createSimulatedCameraFrame(context)
                    viewModel.processCapture(fallback)
                }
            )
        }
    }

    // Trigger Video Recording Toggle
    fun toggleVideoRecording() {
        if (!isRecording) {
            videoManager.startRecording(
                controller = controller,
                recordAudio = cameraSettings.recordAudio,
                onEvent = { event ->
                    when (event) {
                        is androidx.camera.video.VideoRecordEvent.Start -> {
                            viewModel.onVideoRecordingStarted()
                        }
                        is androidx.camera.video.VideoRecordEvent.Finalize -> {
                            viewModel.onVideoRecordingStopped()
                            if (event.hasError()) {
                                viewModel.showError("Recording issue: ${event.cause?.message ?: "Code ${event.error}"}")
                            } else {
                                viewModel.showInfo("Video recording saved")
                            }
                        }
                    }
                },
                onError = { throwable ->
                    viewModel.onVideoRecordingStopped()
                    viewModel.showError(throwable.message ?: "Video recording not supported on this device profile")
                }
            )
        } else {
            videoManager.stop()
            viewModel.onVideoRecordingStopped()
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
            controller = controller,
            modifier = Modifier.fillMaxSize()
        )

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

        // 6. Top Bar: Minimal Single-Icon Controls & Status
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color(0x80000000))
                .padding(top = 24.dp, bottom = 6.dp, start = 8.dp, end = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                // Tiny single-icon Settings gear button
                IconButton(
                    onClick = { onNavigateToSettings() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElevatedSurface)
                        .graphicsLayer { rotationZ = -animatedRotation }
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = PrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
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

        // 7. Live Overlay HUD (Bottom Section above Shutter)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 150.dp)
        ) {
            // Live Preview Overlay mirroring reference road photo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Lower-Left: Live Mini Map
                if (overlayConfig.showMapInset && overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
                    MiniMapPreview(
                        location = locationState.point,
                        bearing = locationState.bearing,
                        routeData = activeRoute,
                        highwayTag = projectProfile.highwayId
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Lower-Right: Information Block (Right-aligned bold white text with black shadow)
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                ) {
                    // Logo when ABOVE_TEXT position is selected
                    if (overlayConfig.showLogo && overlayConfig.logoPosition == LogoPosition.ABOVE_TEXT) {
                        LiveLogoOverlay(
                            config = overlayConfig,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                        )
                    }

                    // Date & Time
                    if (overlayConfig.showTimestamp) {
                        val pattern = if (cameraSettings.is24HourFormat) "dd MMMM yyyy HH:mm" else overlayConfig.timestampFormat
                        val dateText = try {
                            SimpleDateFormat(pattern, Locale.US).format(Date(currentTimeMillis))
                        } catch (e: Exception) {
                            SimpleDateFormat("dd MMMM yyyy h:mm a", Locale.US).format(Date(currentTimeMillis))
                        }
                        Text(
                            text = dateText,
                            color = PrimaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
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

                    // GPS Coordinates
                    if (overlayConfig.showCoordinates && locationState.point != null) {
                        val p = locationState.point!!
                        val coordText = when (cameraSettings.coordinateFormat) {
                            com.example.model.CoordinateFormat.DMS -> {
                                val latDms = toDms(p.latitude, true)
                                val lngDms = toDms(p.longitude, false)
                                "$latDms $lngDms"
                            }
                            else -> {
                                String.format(Locale.US, "%.4f°N %.4f°E", p.latitude, p.longitude)
                            }
                        }
                        Text(
                            text = coordText,
                            color = PrimaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
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

                    // Telemetry: Altitude, Accuracy & Compass Heading
                    val telemetryItems = mutableListOf<String>()
                    if (overlayConfig.showCompass) {
                        telemetryItems.add(compassState.displayHeading)
                    }
                    if (overlayConfig.showAltitude && locationState.point != null && locationState.altitude != 0.0) {
                        telemetryItems.add(String.format(Locale.US, "%.0f m", locationState.altitude))
                    }
                    if (overlayConfig.showAccuracy && locationState.point != null && locationState.accuracy > 0f) {
                        telemetryItems.add(String.format(Locale.US, "±%.0fm", locationState.accuracy))
                    }
                    if (telemetryItems.isNotEmpty()) {
                        Text(
                            text = telemetryItems.joinToString("  |  "),
                            color = HighlightYellow,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
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

                    // Speed & Direction Telemetry for Highway / Location mode
                    if (cameraSettings.cameraMode == CameraMode.HIGHWAY || cameraSettings.cameraMode == CameraMode.LOCATION) {
                        val speedKmh = if (locationState.speed != null && locationState.speed!! >= 0f) locationState.speed!! * 3.6f else null
                        val speedMph = if (locationState.speed != null && locationState.speed!! >= 0f) locationState.speed!! * 2.23694f else null
                        val speedStr = when {
                            speedKmh == null -> "Speed: Unavailable"
                            cameraSettings.speedUnit == SpeedUnit.KMH -> String.format(Locale.US, "Speed: %.1f km/h", speedKmh)
                            else -> String.format(Locale.US, "Speed: %.1f mph", speedMph)
                        }

                        val directionStr = if (locationState.bearing != null) {
                            val b = locationState.bearing!!
                            val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
                            val index = (((b + 11.25) % 360) / 22.5).toInt()
                            String.format(Locale.US, "Dir: %.0f° %s", b, directions[index % 16])
                        } else {
                            "Dir: Unavailable"
                        }

                        Text(
                            text = "$speedStr  |  $directionStr",
                            color = PrimaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
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

                    // Highway || Package || Chainage || Road Side || Lane || Activity
                    if (overlayConfig.showProjectInfo || overlayConfig.showChainage) {
                        val sep = projectProfile.separator
                        val lineBuilder = StringBuilder()

                        if (projectProfile.highwayId.isNotBlank()) lineBuilder.append("${projectProfile.highwayId} $sep ")
                        if (projectProfile.packageSection.isNotBlank()) lineBuilder.append("${projectProfile.packageSection} $sep ")

                        if (overlayConfig.showChainage) {
                            lineBuilder.append("${matchResult.displayChainage} ")
                        }

                        if (projectProfile.roadSide != RoadSide.BOTH) {
                            lineBuilder.append("${projectProfile.roadSide.name} ")
                        }

                        if (projectProfile.lane.isNotBlank()) {
                            lineBuilder.append("${projectProfile.lane} ")
                        }

                        if (projectProfile.activity.isNotBlank()) {
                            lineBuilder.append(projectProfile.activity)
                        }

                        Text(
                            text = lineBuilder.toString(),
                            color = PrimaryText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.End,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    offset = Offset(3f, 3f),
                                    blurRadius = 5f
                                )
                            )
                        )
                    }
                }
            }

            // Minimalist Glass Field Selectors (Side, Lane, Activity, Chainage)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
                            .height(38.dp)
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
                            .height(38.dp)
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
                                .height(38.dp)
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
                            .height(38.dp)
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
            }
        }

        // 8. Bottom Controls: Mode Selector + Zoom Selector Bar + Shutter + Mode Switch
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {




            // Mode Selector Pill: PHOTO | VIDEO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x501A1A1A),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (captureMode == CaptureMode.PHOTO) HighlightYellow else Color.Transparent)
                                .clickable { viewModel.setCaptureMode(CaptureMode.PHOTO) }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
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
                                .padding(horizontal = 16.dp, vertical = 6.dp)
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

            // Bottom Shutter Bar (Gallery Thumbnail, Prominent Shutter, Quick Simulator)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                // Shutter / Record Button
                if (captureMode == CaptureMode.PHOTO) {
                    // White Shutter Button with Camera Icon
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(if (isCapturing) AccentRed else PrimaryText)
                            .clickable(enabled = !isCapturing && !isCountingDown) {
                                triggerPhotoCapture()
                            }
                            .testTag("shutter_button"),
                        contentAlignment = Alignment.Center
                    ) {
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
                    }
                } else {
                    // Video Record Button: Red Outer Ring with Red circle or square stop button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(AccentRed)
                            .clickable { toggleVideoRecording() }
                            .testTag("shutter_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PrimaryText)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(DeepRed)
                            )
                        }
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

        // Manual Chainage Dialog
        if (showManualDialog) {
            var inputVal by remember { mutableStateOf(matchResult.chainage?.format() ?: "196+040") }
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
    }
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
 * Creates a high resolution simulated camera frame with road texture if device camera hardware is unavailable.
 */
private fun createSimulatedCameraFrame(context: Context): Bitmap {
    val w = 1920
    val h = 1080
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
    for (y in 400 until h step 140) {
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
