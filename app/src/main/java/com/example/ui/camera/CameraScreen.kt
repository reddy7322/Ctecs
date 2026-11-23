package com.example.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.content.res.Configuration
import androidx.camera.view.LifecycleCameraController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import com.example.ui.ToastType
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import com.example.ui.theme.ctecsBorderlessInputColors
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.camera.CameraBrightnessEngine
import com.example.location.LocationStatus
import com.example.model.CameraGridMode
import com.example.model.CameraMode
import com.example.model.CaptureMode
import com.example.model.CaptureTimer
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.LogoPosition
import com.example.model.MatchConfidence
import com.example.model.MiniMapPosition
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

/**
 * Direct-tap Camera Screen side options in strict cycling order:
 * LHS -> RHS -> BHS -> NONE -> LHS
 */
internal enum class CameraSideOption(val label: String) {
    LHS("LHS"),
    RHS("RHS"),
    BHS("BHS"),
    NONE("NONE");

    fun next(): CameraSideOption = when (this) {
        LHS -> RHS
        RHS -> BHS
        BHS -> NONE
        NONE -> LHS
    }
}

/**
 * Resolved Camera Screen side display state.
 * - Never displays "AUTO" or "MANUAL".
 * - In underlying Automatic Detection mode, displays ONLY detected "LHS" or "RHS"
 *   (or neutral "---" if internally uncertain, without guessing).
 * - In Manual mode, displays ONLY "LHS", "RHS", "BHS", or "NONE".
 */
internal data class CameraSideDisplayState(
    val activeOption: CameraSideOption?,
    val displayLabel: String
) {
    fun nextOptionOnTap(): CameraSideOption = activeOption?.next() ?: CameraSideOption.LHS
}

internal fun resolveCameraSideDisplayState(
    smartDirectionState: com.example.model.SmartDirectionState,
    projectProfile: ProjectProfile
): CameraSideDisplayState {
    if (!smartDirectionState.isManualOverride) {
        return when (smartDirectionState.confirmedBinarySide) {
            "LHS" -> CameraSideDisplayState(CameraSideOption.LHS, CameraSideOption.LHS.label)
            "RHS" -> CameraSideDisplayState(CameraSideOption.RHS, CameraSideOption.RHS.label)
            else -> CameraSideDisplayState(null, "---")
        }
    }
    val option = when (smartDirectionState.confirmedSide?.trim()?.uppercase(Locale.ROOT)) {
        "LHS" -> CameraSideOption.LHS
        "RHS" -> CameraSideOption.RHS
        "BHS", "BOTH" -> CameraSideOption.BHS
        "NONE" -> CameraSideOption.NONE
        else -> when (projectProfile.roadSide) {
            RoadSide.LHS -> CameraSideOption.LHS
            RoadSide.RHS -> CameraSideOption.RHS
            RoadSide.BOTH -> CameraSideOption.BHS
            RoadSide.NONE -> CameraSideOption.NONE
            RoadSide.MEDIAN -> CameraSideOption.BHS
        }
    }
    return CameraSideDisplayState(option, option.label)
}

private fun cameraSideOptionTint(option: CameraSideOption?): Color = when (option) {
    CameraSideOption.LHS -> Color(0xFF34D399)
    CameraSideOption.RHS -> Color(0xFF38BDF8)
    CameraSideOption.BHS -> HighlightYellow
    CameraSideOption.NONE -> Color.White.copy(alpha = 0.78f)
    null -> Color.White.copy(alpha = 0.82f)
}

/**
 * Minimal, subtle animated glowing/pulsing red indicator bulb rendered beside the active
 * Camera direction (`LHS`, `RHS`, `BHS`, `NONE`) ONLY when manual direction override is active.
 * Immediately disappears when Auto direction resumes. Never shows text like "MANUAL" or "AUTO".
 */
@Composable
internal fun CameraManualDirectionRedBulb(
    modifier: Modifier = Modifier,
    diameterDp: Dp = 7.dp
) {
    val transition = rememberInfiniteTransition(label = "camera_manual_direction_bulb")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "camera_manual_direction_bulb_alpha"
    )
    val haloScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "camera_manual_direction_bulb_halo"
    )

    ComposeCanvas(
        modifier = modifier
            .size(diameterDp + 4.dp)
            .semantics { contentDescription = "Manual direction active" }
            .testTag("camera_manual_direction_red_bulb")
    ) {
        val coreRadius = (diameterDp.toPx() / 2f).coerceAtLeast(2f)
        // Outer soft red glow halo
        drawCircle(
            color = Color(0xFFEF4444).copy(alpha = 0.34f * pulseAlpha),
            radius = (coreRadius * haloScale).coerceAtMost(size.minDimension / 2f),
            center = center
        )
        // Crisp glowing red bulb core
        drawCircle(
            color = Color(0xFFFF3B30).copy(alpha = pulseAlpha),
            radius = coreRadius,
            center = center
        )
        // Tiny warm specular highlight for realistic LED-bulb depth
        drawCircle(
            color = Color(0xFFFFD6D4).copy(alpha = 0.72f * pulseAlpha),
            radius = coreRadius * 0.34f,
            center = Offset(center.x - coreRadius * 0.24f, center.y - coreRadius * 0.24f)
        )
    }
}

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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
                imageCaptureMode = androidx.camera.core.ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                imageCaptureResolutionSelector =
                    com.example.camera.CameraResolutionHelper.buildNativePhotoResolutionSelector(4f / 3f)
                previewResolutionSelector =
                    com.example.camera.CameraResolutionHelper.buildNativePreviewResolutionSelector(
                        sensorLongShortRatio = 4f / 3f,
                        isVideoMode = false
                    )
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

    DisposableEffect(overlayManager, videoManager) {
        videoManager.overlayManager = overlayManager
        onDispose {
            videoManager.release()
            overlayManager.release()
        }
    }

    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val isCapturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    val captureMode by viewModel.captureMode.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isRecordingPaused by viewModel.isRecordingPaused.collectAsStateWithLifecycle()
    val captureExposureEvStops by viewModel.captureExposureEvStops.collectAsStateWithLifecycle()
    val recordingSessionState by videoManager.sessionState.collectAsStateWithLifecycle()
    val isVideoPreparing = recordingSessionState == RecordingSessionState.PREPARING ||
        recordingSessionState == RecordingSessionState.FINALIZING
    val recordingDurationSec by viewModel.recordingDurationSeconds.collectAsStateWithLifecycle()
    val currentZoomRatio by viewModel.currentZoomRatio.collectAsStateWithLifecycle()
    val exposureCompensation by viewModel.exposureCompensation.collectAsStateWithLifecycle()
    val cameraSettings by viewModel.cameraSettings.collectAsStateWithLifecycle()
    val smartDirectionState by viewModel.smartDirectionState.collectAsStateWithLifecycle()
    val latestWeatherObservation by viewModel.latestWeatherObservation.collectAsStateWithLifecycle()
    val miniMapViewDistance by viewModel.miniMapViewDistancePreference.collectAsStateWithLifecycle()
    val videoExportState by viewModel.videoExportState.collectAsStateWithLifecycle()
    var previewViewportRect by remember { mutableStateOf<com.example.camera.FramingPreviewRect?>(null) }
    var rootContainerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val screenDensity = androidx.compose.ui.platform.LocalDensity.current

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var zoomCapabilities by remember {
        mutableStateOf(
            com.example.camera.CameraUltraWideZoomManager.detectCapabilities(
                context = context,
                cameraInfo = null,
                isFrontCamera = isFrontCamera
            )
        )
    }
    var deviceCameraProfile by remember {
        mutableStateOf(
            com.example.camera.CameraDeviceCapabilityManager.inspectActiveCamera(
                context = context,
                cameraInfo = controller.cameraInfo,
                isFrontCamera = isFrontCamera,
                zoomCapabilities = zoomCapabilities
            )
        )
    }
    var minZoomRatio by remember { mutableFloatStateOf(zoomCapabilities.minZoomRatio) }
    var maxZoomRatio by remember { mutableFloatStateOf(zoomCapabilities.maxZoomRatio) }
    val rawUseStandaloneUltraWide = !isFrontCamera &&
        zoomCapabilities.usesStandalonePhysicalUltraWide &&
        zoomCapabilities.widePresetRatio != null &&
        currentZoomRatio < 0.99f
    val isRecordingLensLocked = isRecording || isVideoPreparing
    var lockedStandaloneUltraWideForRecording by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isRecordingLensLocked) {
        lockedStandaloneUltraWideForRecording = if (isRecordingLensLocked) {
            rawUseStandaloneUltraWide
        } else {
            null
        }
    }
    val useStandaloneUltraWide = if (isRecordingLensLocked) {
        lockedStandaloneUltraWideForRecording ?: rawUseStandaloneUltraWide
    } else {
        rawUseStandaloneUltraWide
    }
    val latestUseStandaloneUltraWide by rememberUpdatedState(useStandaloneUltraWide)

    LaunchedEffect(zoomCapabilities) {
        if (!latestUseStandaloneUltraWide) {
            minZoomRatio = zoomCapabilities.minZoomRatio
            maxZoomRatio = zoomCapabilities.maxZoomRatio
        }
        val effectiveMin = zoomCapabilities.effectiveMinZoomRatio.coerceAtMost(1.0f)
        val effectiveMax = zoomCapabilities.maxZoomRatio.coerceAtLeast(1.0f)
        val clamped = currentZoomRatio.coerceIn(effectiveMin, effectiveMax)
        if (kotlin.math.abs(clamped - currentZoomRatio) > 0.01f) {
            viewModel.setZoomRatio(clamped)
        }
    }

    LaunchedEffect(isFrontCamera) {
        // When flipping between rear & front cameras, clamp zoom cleanly to the active camera's supported range
        if (currentZoomRatio < 0.99f) {
            viewModel.setZoomRatio(1.0f)
        } else if (currentZoomRatio > zoomCapabilities.maxZoomRatio) {
            viewModel.setZoomRatio(zoomCapabilities.maxZoomRatio.coerceAtLeast(1.0f))
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { state ->
            if (state != null && !latestUseStandaloneUltraWide) {
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
    val lastValidLocation by viewModel.lastValidLocation.collectAsStateWithLifecycle()
    val mapHeadingState by viewModel.mapHeadingState.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val matchResult by viewModel.cameraMatchResult.collectAsStateWithLifecycle()
    val manualChainageText by viewModel.manualChainageText.collectAsStateWithLifecycle()
    val savedCustomChainages by viewModel.savedCustomChainages.collectAsStateWithLifecycle()
    val isRouteCorrectionsEnabled by viewModel.isRouteCorrectionsEnabled.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()
    val overlayConfig by viewModel.overlayConfig.collectAsStateWithLifecycle()
    val currentLocationName by viewModel.currentLocationName.collectAsStateWithLifecycle()
    val lastPhoto by viewModel.lastCapturedPhoto.collectAsStateWithLifecycle()
    val showManualDialog by viewModel.showManualChainageDialog.collectAsStateWithLifecycle()

    val isVideoCaptureMode = (captureMode == CaptureMode.VIDEO || isRecording)
    val activeMiniMapConfig = overlayConfig.getEffectiveMiniMapConfig(isVideo = isVideoCaptureMode)
    val showMiniMapOverlay = overlayConfig.isMiniMapActive(isVideo = isVideoCaptureMode)
    val effectiveMiniMapLocation = locationState.point ?: lastValidLocation
    val isMiniMapGpsRetained = (locationState.point == null && lastValidLocation != null)
    val reliableMiniMapChainage = remember(matchResult) {
        if (!matchResult.isUncertain &&
            (matchResult.isManual ||
                matchResult.confidence == com.example.model.MatchConfidence.HIGH ||
                matchResult.confidence == com.example.model.MatchConfidence.MEDIUM) &&
            matchResult.chainage != null
        ) {
            matchResult.displayChainage
        } else {
            ""
        }
    }
    val authoritativePreviewSide: String? = remember(smartDirectionState, projectProfile) {
        com.example.overlay.OverlayTextGenerator.resolveAuthoritativeDisplaySide(smartDirectionState, projectProfile)
    }
    val reliableMiniMapSide = remember(authoritativePreviewSide) {
        authoritativePreviewSide.orEmpty()
    }

    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val isCameraScreenVisible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val hasActiveRoute = remember(activeRoute) {
        activeRoute != null && (activeRoute!!.alignments.isNotEmpty() || activeRoute!!.markers.isNotEmpty())
    }
    val chainageIndicators = remember(matchResult, manualChainageText, hasActiveRoute, isRouteCorrectionsEnabled) {
        resolveChainageIndicatorsState(
            matchResult = matchResult,
            manualChainageText = manualChainageText,
            hasActiveRoute = hasActiveRoute,
            isCorrectionsEnabled = isRouteCorrectionsEnabled
        )
    }

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

    val currentSideDisplay = resolveCameraSideDisplayState(smartDirectionState, projectProfile)
    val animatedSideTint by animateColorAsState(
        targetValue = cameraSideOptionTint(currentSideDisplay.activeOption),
        animationSpec = tween(160),
        label = "camera_side_option_tint"
    )
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun cycleNextCameraSide() {
        hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        when (currentSideDisplay.nextOptionOnTap()) {
            CameraSideOption.LHS -> viewModel.updateSide(RoadSide.LHS)
            CameraSideOption.RHS -> viewModel.updateSide(RoadSide.RHS)
            CameraSideOption.BHS -> viewModel.updateSide(RoadSide.BOTH)
            CameraSideOption.NONE -> viewModel.updateSide(RoadSide.NONE)
        }
    }
    var showExposureSlider by remember { mutableStateOf(false) }
    var showZoomSlider by remember { mutableStateOf(false) }
    var showQuickSettingsPopup by remember { mutableStateOf(false) }
    var showCustomCommentDialog by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    var isTopToolbarExpanded by remember { mutableStateOf(true) }
    var showLaneMenu by remember { mutableStateOf(false) }
    var isHdrEnabled by remember { mutableStateOf(true) }

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
    var lastShutterTapMs by remember { mutableStateOf(0L) }

    LaunchedEffect(showQuickCaptureFlash) {
        if (showQuickCaptureFlash) {
            delay(95L)
            showQuickCaptureFlash = false
        }
    }

    fun startVideoRecording(audioEnabled: Boolean) {
        if (videoManager.isPreparingOrRecording()) return
        val effectiveRotation = if (isLandscape && (rotationDegrees == 0f || rotationDegrees == 180f)) 90 else rotationDegrees.toInt()
        val autoVideoRes = com.example.camera.CameraResolutionHelper.resolveBestAutomaticVideoResolution(
            context = context,
            isFrontCamera = isFrontCamera
        )
        coroutineScope.launch {
            videoManager.prepareAndStartRecording(
                controller = controller,
                videoResolution = autoVideoRes,
                isFrontCamera = isFrontCamera,
                recordAudio = audioEnabled,
                initialRotation = effectiveRotation,
                onRecordingStarted = { viewModel.onVideoRecordingStarted() },
                onEvent = { event, recFile, hasOverlay ->
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
                            if (isLandscape && captureMode != CaptureMode.PHOTO) {
                                viewModel.setCaptureMode(CaptureMode.PHOTO)
                            }
                            if (!isFinalizedRecordingValid(event.hasError(), event.error, recFile)) {
                                android.util.Log.e(
                                    "CameraScreen",
                                    "Video recording finalized without valid camera output: error=${event.error}, fileSize=${recFile?.length() ?: 0L}",
                                    event.cause
                                )
                                try { recFile?.delete() } catch (ignored: Exception) {}
                                viewModel.showError("Video recording failed: camera could not capture video frames. Please try again.")
                            } else {
                                viewModel.processRecordedVideo(
                                    context = context,
                                    rawVideoFile = recFile!!,
                                    forcedRotation = effectiveRotation,
                                    hasRealtimeOverlay = hasOverlay
                                )
                            }
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
    }

    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        startVideoRecording(isGranted && cameraSettings.recordAudio)
    }

    // Execute immediate ultra-fast shutter capture using an immutable snapshot captured at press time
    fun executeImmediatePhotoCapture() {
        val shutterSnapshot = viewModel.createCurrentSnapshot()
        viewModel.setCapturing(true)
        showQuickCaptureFlash = true
        capturePhoto(
            controller = controller,
            context = context,
            deviceRotationDegrees = rotationDegrees.toInt(),
            isLandscape = isLandscape,
            photoAspectRatio = "Full",
            isFrontCamera = isFrontCamera,
            frontCameraMirror = cameraSettings.frontCameraMirror,
            playShutterSound = cameraSettings.captureSound,
            onSuccessWithExif = { bitmap, cameraExif ->
                viewModel.processCapture(bitmap, shutterSnapshot.copy(cameraExifMetadata = cameraExif))
            },
            onError = { error ->
                viewModel.setCapturing(false)
                viewModel.showError(
                    error.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Photo capture failed. Please try again."
                )
            }
        )
    }

    // Trigger Photo Capture (with safe rapid-tap debounce and optional countdown)
    fun triggerPhotoCapture() {
        if (isCapturing || isCountingDown) return
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastShutterTapMs < 180L) return
        lastShutterTapMs = nowMs

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
                executeImmediatePhotoCapture()
            }
        } else {
            executeImmediatePhotoCapture()
        }
    }

    // Trigger Video Recording Toggle
    fun toggleVideoRecording() {
        if (!isRecording) {
            if (videoManager.isPreparingOrRecording()) return
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryBackground)
            .onSizeChanged { rootContainerSize = it }
    ) {
        // 1. CameraX Preview
        CameraPreview(
            isFrontCamera = isFrontCamera,
            isFlashOn = isFlashOn,
            zoomRatio = currentZoomRatio,
            exposureIndex = exposureCompensation,
            isVideoMode = (captureMode == CaptureMode.VIDEO),
            isVideoRecording = isRecording,
            rotationDegrees = rotationDegrees.toInt(),
            isLandscape = isLandscape,
            photoResolution = cameraSettings.photoResolution,
            videoResolution = cameraSettings.videoResolution,
            photoAspectRatio = cameraSettings.photoAspectRatio,
            videoAspectRatio = cameraSettings.videoAspectRatio,
            cropMode = cameraSettings.cropMode,
            autoBrightnessEnabled = cameraSettings.autoBrightnessEnabled,
            manualPreviewBrightness = cameraSettings.manualPreviewBrightness,
            targetVideoFps = cameraSettings.videoFps,
            photoQualityPreset = cameraSettings.photoQualityPreset,
            videoStabilizationMode = cameraSettings.videoStabilizationMode,
            autofocusMode = cameraSettings.autofocusMode,
            photoEnhancementEnabled = cameraSettings.photoEnhancementEnabled,
            videoNoiseReductionEnabled = cameraSettings.videoNoiseReductionEnabled,
            isHdrEnabled = isHdrEnabled,
            touchToCapture = cameraSettings.touchToCapture,
            easyToggleCameras = cameraSettings.easyToggleCameras,
            useStandaloneUltraWide = useStandaloneUltraWide,
            standaloneUltraWideCameraId = zoomCapabilities.standaloneUltraWideCameraId,
            logicalWideCameraId = zoomCapabilities.logicalWideCameraId,
            controller = controller,
            currentSnapshotProvider = { viewModel.createCurrentSnapshot() },
            overlayManager = overlayManager,
            videoManager = videoManager,
            onExposureChanged = { newEv -> viewModel.setExposureCompensation(newEv) },
            onZoomChanged = { newZoom -> viewModel.setZoomRatio(newZoom) },
            onAutoBrightnessChanged = { enabled -> viewModel.setCameraAutoBrightness(enabled) },
            onManualBrightnessChanged = { level -> viewModel.setCameraManualBrightness(level) },
            onCaptureExposureTelemetry = { level, evStops, hwSupported ->
                viewModel.updateCaptureExposureTelemetry(level, evStops, hwSupported)
            },
            onTouchToCapture = { triggerPhotoCapture() },
            onDoubleTapSwitchCamera = {
                if (!isRecording && !isVideoPreparing) {
                    viewModel.toggleCameraFacing()
                }
            },
            onZoomCapabilitiesChanged = { caps -> zoomCapabilities = caps },
            onPreviewViewportChanged = { rect -> previewViewportRect = rect },
            onDeviceCapabilitiesChanged = { profile -> deviceCameraProfile = profile },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle power-saving viewfinder dimming scrim when Battery Save Mode is enabled (never affects capture output)
        if (cameraSettings.batterySaveMode) {
            val dimAlpha = ((1f - cameraSettings.batterySaveBrightness.coerceIn(0.25f, 0.85f)) * 0.42f).coerceIn(0.08f, 0.35f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
            )
        }

        // Visual flash feedback on capture (photo capture & quick photo during video)
        if (showQuickCaptureFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }

        // 2. Composition Grid Lines (Off, 3x3, 4x4) — bounded strictly inside the live FIT_CENTER camera frame
        if (cameraSettings.gridMode != CameraGridMode.OFF) {
            val divisions = if (cameraSettings.gridMode == CameraGridMode.GRID_3X3) 3 else 4
            val activeVp = previewViewportRect
            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 1.dp.toPx()
                val gridColor = Color.White.copy(alpha = 0.35f)
                val left = activeVp?.leftPx ?: 0f
                val top = activeVp?.topPx ?: 0f
                val vpW = (activeVp?.widthPx ?: size.width).coerceAtLeast(1f)
                val vpH = (activeVp?.heightPx ?: size.height).coerceAtLeast(1f)
                val stepX = vpW / divisions
                val stepY = vpH / divisions

                for (i in 1 until divisions) {
                    drawLine(
                        color = gridColor,
                        start = Offset(left + stepX * i, top),
                        end = Offset(left + stepX * i, top + vpH),
                        strokeWidth = strokeW
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(left, top + stepY * i),
                        end = Offset(left + vpW, top + stepY * i),
                        strokeWidth = strokeW
                    )
                }
            }
        }

        // 3. Sensor-Driven Native Electronic Horizon Level Reticle (matches native camera center crosshair + tilt lock)
        if (cameraSettings.showHorizonLevel) {
            NativeHorizonLevelIndicator(
                isLandscape = isLandscape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("camera_horizon_level_indicator")
            )
        }

        // 4. Live Custom Logo Overlay on Viewfinder (Portrait standalone positions only;
        //    Landscape renders a single clean logo anchored inside the camera frame above its timestamp)
        if (!isLandscape && overlayConfig.showLogo && overlayConfig.logoPosition != LogoPosition.ABOVE_TEXT) {
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

        // Compute exact FIT_CENTER viewport insets so Landscape overlays stay 100% inside the live camera frame
        val vpStartDp = with(screenDensity) { (previewViewportRect?.leftPx ?: 0f).toDp() }
        val vpEndDp = with(screenDensity) {
            val rect = previewViewportRect
            if (rect != null && rootContainerSize.width > 0) {
                (rootContainerSize.width - (rect.leftPx + rect.widthPx)).coerceAtLeast(0f).toDp()
            } else {
                0.dp
            }
        }
        val vpTopDp = with(screenDensity) { (previewViewportRect?.topPx ?: 0f).toDp() }
        val vpBottomDp = with(screenDensity) {
            val rect = previewViewportRect
            if (rect != null && rootContainerSize.height > 0) {
                (rootContainerSize.height - (rect.topPx + rect.heightPx)).coerceAtLeast(0f).toDp()
            } else {
                0.dp
            }
        }

        // 4b. Configurable Dynamic Mini Map Overlay Preview (Photo & Video independent settings)
        if (showMiniMapOverlay) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        start = if (isLandscape) maxOf(166.dp, vpStartDp + 8.dp) else 10.dp,
                        end = if (isLandscape) maxOf(94.dp, vpEndDp + 8.dp) else 10.dp,
                        top = if (isLandscape) maxOf(56.dp, vpTopDp + 8.dp) else 104.dp,
                        bottom = if (isLandscape) maxOf(12.dp, vpBottomDp + 10.dp) else 232.dp
                    )
            ) {
                val safeW = maxWidth
                val safeH = maxHeight
                val mapW = activeMiniMapConfig.resolveWidthDp().dp
                    .coerceIn(40.dp, (safeW * 0.48f).coerceAtLeast(40.dp))
                val mapH = activeMiniMapConfig.resolveHeightDp().dp
                    .coerceIn(40.dp, (safeH * 0.48f).coerceAtLeast(40.dp))

                val maxX = (safeW - mapW).coerceAtLeast(0.dp)
                val maxY = (safeH - mapH).coerceAtLeast(0.dp)

                val (offsetX, offsetY) = when (activeMiniMapConfig.position) {
                    MiniMapPosition.TOP_LEFT -> Pair(0.dp, 0.dp)
                    MiniMapPosition.TOP_RIGHT -> {
                        // Keep clear of top-right Portrait timestamp card when anchored Top Right
                        val topClearance = if (!isLandscape) 106.dp.coerceAtMost(maxY) else 0.dp
                        Pair(maxX, topClearance)
                    }
                    MiniMapPosition.BOTTOM_LEFT -> Pair(0.dp, maxY)
                    MiniMapPosition.BOTTOM_RIGHT -> Pair(maxX, maxY)
                    MiniMapPosition.CUSTOM -> Pair(
                        maxX * activeMiniMapConfig.customPosXFraction.coerceIn(0f, 1f),
                        maxY * activeMiniMapConfig.customPosYFraction.coerceIn(0f, 1f)
                    )
                }

                Box(
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .size(width = mapW, height = mapH)
                        .clickable { showSimDialog = true }
                        .testTag(if (isLandscape) "landscape_minimap_preview" else "portrait_minimap_preview")
                ) {
                    MiniMapPreview(
                        location = effectiveMiniMapLocation,
                        bearing = mapHeadingState.headingDegrees ?: locationState.bearing,
                        routeData = activeRoute,
                        highwayTag = projectProfile.highwayId,
                        miniMapConfig = activeMiniMapConfig,
                        chainageLabel = reliableMiniMapChainage,
                        sideLabel = reliableMiniMapSide,
                        isGpsRetained = isMiniMapGpsRetained,
                        mapHeadingState = mapHeadingState,
                        viewDistancePreference = miniMapViewDistance,
                        speedMps = locationState.speed,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
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
                        .background(Color(0xCC0D111A)),
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
            // LANDSCAPE CAMERA CONTROLS: FLAT BORDERLESS AMOLED HUD
            // ==========================================================
            val hudPillBg = Color(0x99080B12)
            val hudPillActiveBg = Color(0xCC171E2B)

            // 1. TOP BAR: Collapsible Smart Minimalist Glass Toolbar (Left) & Status Checkmark (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.displayCutout)
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
                            .background(hudPillBg)
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
                            // 1. Flash Toggle Button (adapts automatically to hardware flash availability)
                            val flashSupported = deviceCameraProfile.hasFlashUnit
                            IconButton(
                                onClick = {
                                    if (flashSupported) {
                                        viewModel.toggleFlash()
                                    } else {
                                        viewModel.showToast("Flash not available on this lens", ToastType.INFO)
                                    }
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isFlashOn && flashSupported) hudPillActiveBg else hudPillBg)
                                    .testTag("landscape_flash_button")
                            ) {
                                Icon(
                                    imageVector = if (isFlashOn && flashSupported) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Toggle Flash",
                                    tint = when {
                                        !flashSupported -> PrimaryText.copy(alpha = 0.35f)
                                        isFlashOn -> HighlightYellow
                                        else -> PrimaryText
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 2. HDR Toggle Button
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isHdrEnabled) hudPillActiveBg else hudPillBg)
                                    .clickable {
                                        isHdrEnabled = !isHdrEnabled
                                        viewModel.showToast(if (isHdrEnabled) "HDR Mode Active" else "HDR Mode Disabled", ToastType.SETTINGS_SAVED)
                                    }
                                    .testTag("landscape_hdr_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isHdrEnabled) HighlightYellow.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f)
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
                                    .background(if (cameraSettings.gridMode != CameraGridMode.OFF) hudPillActiveBg else hudPillBg)
                                    .testTag("landscape_grid_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = "Grid: ${cameraSettings.gridMode.label}",
                                    tint = if (cameraSettings.gridMode != CameraGridMode.OFF) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 4. Timer Cycle Button
                            IconButton(
                                onClick = { viewModel.cycleTimer() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (cameraSettings.captureTimer != CaptureTimer.OFF) hudPillActiveBg else hudPillBg)
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
                                    .background(if (cameraSettings.showHorizonLevel) hudPillActiveBg else hudPillBg)
                                    .testTag("landscape_level_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterCenterFocus,
                                    contentDescription = "Horizon Leveler",
                                    tint = if (cameraSettings.showHorizonLevel) HighlightYellow else PrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 8. Tap-to-Switch Crop Mode Control (Full -> Semi -> Rect -> 1:1)
                            CameraCropModeSwitchPill(
                                cropMode = cameraSettings.cropMode,
                                isLandscape = true,
                                onTapCycle = {
                                    try {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    } catch (ignored: Exception) {}
                                    viewModel.cycleCameraCropMode()
                                },
                                onLongPressResetFull = {
                                    if (cameraSettings.cropMode != com.example.model.CameraCropMode.FULL) {
                                        try {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        } catch (ignored: Exception) {}
                                        viewModel.setCameraCropMode(com.example.model.CameraCropMode.FULL)
                                    }
                                },
                                modifier = Modifier.testTag("camera_crop_mode_button")
                            )

                            // 9. Settings Gear Button
                            IconButton(
                                onClick = { showQuickSettingsPopup = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(hudPillBg)
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

                // Center if recording: Flat Borderless Recording Pill
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
                        color = AccentRed.copy(alpha = 0.25f)
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

                // Right: Alignment Status Checkmark Button
                Box(
                    modifier = Modifier
                        .padding(end = 92.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(hudPillBg)
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

            // 2. LEFT CORNER: HIGHWAY FUNCTIONS — ALWAYS VISIBLE & RESPONSIVE IN LANDSCAPE
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(top = 52.dp, start = 14.dp, bottom = 12.dp)
            ) {
                val isUltraShortHeight = maxHeight < 180.dp
                val isCompactHeight = maxHeight < 380.dp
                val pillHeight = when {
                    isUltraShortHeight -> 28.dp
                    isCompactHeight -> 30.dp
                    else -> 33.dp
                }
                val itemSpacing = when {
                    isUltraShortHeight -> 4.dp
                    isCompactHeight -> 5.dp
                    else -> 6.dp
                }
                val pillWidth = if (isUltraShortHeight) 144.dp else 150.dp

                val sidePillContent: @Composable () -> Unit = {
                    // Control 2: Direct-Tap Side Selection Pill (LHS -> RHS -> BHS -> NONE -> LHS);
                    // Long-press resumes Auto direction. Shows tiny animated red bulb ONLY in Manual mode.
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = hudPillBg,
                        modifier = Modifier
                            .width(pillWidth)
                            .height(pillHeight)
                            .clip(RoundedCornerShape(18.dp))
                            .combinedClickable(
                                onClick = { cycleNextCameraSide() },
                                onLongClick = {
                                    if (smartDirectionState.isManualOverride) {
                                        viewModel.resetSideToAuto()
                                        viewModel.showToast("Auto direction resumed", ToastType.INFO)
                                    }
                                }
                            )
                            .testTag("landscape_side_pill")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (smartDirectionState.isManualOverride) {
                                CameraManualDirectionRedBulb(diameterDp = 7.dp)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(animatedSideTint)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentSideDisplay.displayLabel,
                                color = animatedSideTint,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                    }
                }

                val lanePillContent: @Composable () -> Unit = {
                    // Control 3: Lane Selector Dropdown Pill (Full Transparent Glass)
                    Box(modifier = Modifier.width(pillWidth)) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = hudPillBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pillHeight)
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
                                        text = projectProfile.lane.ifBlank { "None" },
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
                            onDismissRequest = { showLaneMenu = false }
                        ) {
                            ProjectProfile.LANE_PRESETS.forEach { l ->
                                val isSel = projectProfile.lane.equals(l, ignoreCase = true)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = l,
                                            color = if (isSel) HighlightYellow else Color.Unspecified,
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
                }

                val activityPillContent: @Composable () -> Unit = {
                    // Control 4: Activity & Sub-Activity Selector Dropdown Pill (Full Transparent Glass)
                    Box(modifier = Modifier.width(pillWidth)) {
                        val actLabel = projectProfile.activity.ifBlank { "None" }
                        val subSuffix = if (projectProfile.hasActiveSubActivity()) " (${projectProfile.subActivity})" else ""
                        val displayActivityPill = if (actLabel.equals("None", ignoreCase = true)) "None" else "$actLabel$subSuffix"

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = hudPillBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pillHeight)
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
                                        text = displayActivityPill,
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
                            onDismissRequest = { showActivityMenu = false }
                        ) {
                            ProjectProfile.ACTIVITY_PRESETS.forEach { act ->
                                val isSel = projectProfile.activity.equals(act, ignoreCase = true) ||
                                    (act == "None" && projectProfile.activity.isBlank())
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = act,
                                            color = if (isSel) HighlightYellow else Color.Unspecified,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.updateActivity(act)
                                        showActivityMenu = false
                                    }
                                )
                            }
                            SmartFadeDivider()
                            DropdownMenuItem(
                                text = { Text("Custom Activity / Sub-Activity...", color = HighlightYellow, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showActivityMenu = false
                                    showManualActivityDialog = true
                                }
                            )
                        }
                    }
                }

                val chainagePillContent: @Composable () -> Unit = {
                    // Control 5: Always-visible Chainage Adjustment Pill (Value + Animated Status Bulbs Only)
                    val isManualSource = chainageIndicators.manualStatus == ChainageBubbleStatus.ACTIVE
                    val isCustomCorrectionSource = chainageIndicators.customStatus == ChainageBubbleStatus.ACTIVE
                    val sourceBgColor = when {
                        isManualSource -> Color(0x40FFB52E)
                        isCustomCorrectionSource -> Color(0x40A855F7)
                        chainageIndicators.kmzStatus == ChainageBubbleStatus.WARNING -> Color(0x40F59E0B)
                        else -> hudPillBg
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = sourceBgColor,
                        modifier = Modifier
                            .width(pillWidth)
                            .height(pillHeight)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { viewModel.openManualChainageDialog() }
                            .testTag("landscape_chainage_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = chainageIndicators.displayChainage,
                                color = PrimaryText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .testTag("hud_landscape_chainage_value")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            ChainageStatusBubblesRow(
                                state = chainageIndicators,
                                isDarkTheme = true,
                                isScreenVisible = isCameraScreenVisible,
                                showInactiveBubbles = false,
                                showLabels = false,
                                tagPrefix = "hud_landscape_"
                            )
                        }
                    }
                }

                val commentPillContent: @Composable () -> Unit = {
                    // Control 6: Comment Pill
                    val hasComment = projectProfile.customDescription.isNotBlank() && !projectProfile.customDescription.equals("None", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (hasComment) Color(0x401685FF) else hudPillBg,
                        modifier = Modifier
                            .width(pillWidth)
                            .height(pillHeight)
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

                if (isUltraShortHeight) {
                    FlowRow(
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        verticalArrangement = Arrangement.spacedBy(itemSpacing),
                        modifier = Modifier
                            .widthIn(max = 304.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("landscape_highway_functions_column")
                    ) {
                        sidePillContent()
                        lanePillContent()
                        activityPillContent()
                        chainagePillContent()
                        commentPillContent()
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(itemSpacing),
                        modifier = Modifier
                            .width(pillWidth)
                            .verticalScroll(rememberScrollState())
                            .testTag("landscape_highway_functions_column")
                    ) {
                        sidePillContent()
                        lanePillContent()
                        activityPillContent()
                        chainagePillContent()
                        commentPillContent()
                    }
                }
            }

            // 3. EXPOSURE COMPENSATION SLIDER (Minimalist Full Transparent Glass)
            if (showExposureSlider) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = hudPillBg,
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

            // 3b. ON-SCREEN ZOOM CONTROLS (LANDSCAPE — positioned cleanly above center, clear of bottom timestamp & minimap)
            CameraZoomControlBar(
                currentZoomRatio = currentZoomRatio,
                minZoomRatio = minZoomRatio,
                maxZoomRatio = maxZoomRatio,
                onZoomChanged = { newZoom -> viewModel.setZoomRatio(newZoom) },
                onResetZoom = { viewModel.setZoomRatio(1.0f) },
                isLandscape = true,
                zoomCapabilities = zoomCapabilities,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = if (showExposureSlider) 104.dp else 56.dp)
            )

            // 4. BOTTOM-RIGHT TIMESTAMP & WATERMARK OVERLAY (anchored inside live FIT_CENTER camera frame)
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        end = maxOf(94.dp, vpEndDp + 10.dp),
                        bottom = maxOf(12.dp, vpBottomDp + 10.dp)
                    )
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
                    timestamp = currentTimeMillis,
                    locationName = currentLocationName.ifBlank { overlayConfig.lastDetectedLocationName },
                    authoritativeSideOverride = authoritativePreviewSide,
                    useAuthoritativeSide = true,
                    weatherObservation = latestWeatherObservation
                )

                LiveViewfinderWatermarkBlock(
                    liveLines = liveLines,
                    overlayConfig = overlayConfig,
                    isLandscape = true
                )
            }

            // 5. RIGHT CORNER: FLAT BORDERLESS CAPTURE & RECORDING DOCK
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                color = Color(0x88000000),
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
                    // 1. Gallery Recent Photo Shortcut
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(hudPillBg)
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

                    // 2. Camera Flip Button
                    IconButton(
                        onClick = {
                            if (!isRecording && !isVideoPreparing) {
                                viewModel.toggleCameraFacing()
                            }
                        },
                        enabled = !isRecording && !isVideoPreparing,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(hudPillBg)
                            .testTag("landscape_camera_flip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera",
                            tint = if (isRecording || isVideoPreparing) PrimaryText.copy(alpha = 0.4f) else PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. Main Shutter Button
                    if (isRecording) {
                        // Quick Photo Capture while Video Recording
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(hudPillActiveBg)
                                .clickable(enabled = !isCapturing) {
                                    try {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    } catch (ignored: Exception) {}
                                    viewModel.setCapturing(true)
                                    showQuickCaptureFlash = true
                                    val quickSnapshot = viewModel.createCurrentSnapshot()
                                    capturePhoto(
                                        controller = controller,
                                        context = context,
                                        deviceRotationDegrees = rotationDegrees.toInt(),
                                        isLandscape = isLandscape,
                                        photoAspectRatio = "Full",
                                        isFrontCamera = isFrontCamera,
                                        frontCameraMirror = cameraSettings.frontCameraMirror,
                                        playShutterSound = cameraSettings.captureSound,
                                        onSuccessWithExif = { bitmap, cameraExif ->
                                            viewModel.processCapture(
                                                bitmap,
                                                quickSnapshot.copy(cameraExifMetadata = cameraExif)
                                            )
                                        },
                                        onError = { error ->
                                            viewModel.setCapturing(false)
                                            viewModel.showError(
                                                error.localizedMessage?.takeIf { it.isNotBlank() }
                                                    ?: "Quick photo capture failed. Please try again."
                                            )
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
                                .background(if (captureMode == CaptureMode.VIDEO) AccentRed.copy(alpha = 0.25f) else hudPillBg)
                                .clickable(enabled = !isVideoPreparing) {
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

                    // 5. Bottom Action: Pause/Resume if recording, else Exposure Tune Button
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
                                .background(if (isRecordingPaused) hudPillActiveBg else hudPillBg)
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
                                .background(if (showExposureSlider) hudPillActiveBg else hudPillBg)
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
                            // Flash cycle button (adapts automatically to detected hardware flash unit)
                            val portraitFlashSupported = deviceCameraProfile.hasFlashUnit
                            IconButton(
                                onClick = {
                                    if (portraitFlashSupported) {
                                        viewModel.toggleFlash()
                                    } else {
                                        viewModel.showToast("Flash not available on this lens", ToastType.INFO)
                                    }
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("flash_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isFlashOn && portraitFlashSupported) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Flash",
                                    tint = when {
                                        !portraitFlashSupported -> Color.White.copy(alpha = 0.35f)
                                        isFlashOn -> HighlightYellow
                                        else -> Color.White
                                    },
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
                                onClick = {
                                    if (!isRecording && !isVideoPreparing) {
                                        viewModel.toggleCameraFacing()
                                    }
                                },
                                enabled = !isRecording && !isVideoPreparing,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("camera_flip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlipCameraAndroid,
                                    contentDescription = "Flip Camera",
                                    tint = if (isRecording || isVideoPreparing) Color.White.copy(alpha = 0.4f) else Color.White,
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

                // Status Row: Minimal Tiny GPS Status Capsule (Left) & Tap-to-Switch Crop Mode Pill (Right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
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

                    // Right cluster: Detected Native Sensor Capability Badge (e.g. 12M / 4K) + Dynamic Crop Mode Switch Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val activeCapBadge = deviceCameraProfile.activeModeBadge(isVideoMode = isVideoCaptureMode)
                        if (activeCapBadge.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x40101726),
                                border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = -animatedRotation }
                                    .testTag("camera_capability_badge")
                            ) {
                                Text(
                                    text = activeCapBadge,
                                    color = HighlightYellow,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.4.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Tap-to-Switch Crop Mode Pill (Full -> Semi Crop -> Rectangular Crop -> Square Crop)
                        CameraCropModeSwitchPill(
                            cropMode = cameraSettings.cropMode,
                            isLandscape = false,
                            animatedRotation = animatedRotation,
                            onTapCycle = {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                } catch (ignored: Exception) {}
                                viewModel.cycleCameraCropMode()
                            },
                            onLongPressResetFull = {
                                if (cameraSettings.cropMode != com.example.model.CameraCropMode.FULL) {
                                    try {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    } catch (ignored: Exception) {}
                                    viewModel.setCameraCropMode(com.example.model.CameraCropMode.FULL)
                                }
                            },
                            modifier = Modifier.testTag("camera_crop_mode_button")
                        )
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
                            color = AccentRed.copy(alpha = 0.25f)
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

                // Top Viewfinder HUD: Minimalist Timestamp Card (Top-Right; Mini Map sits in configurable safe layer)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.Top
                ) {

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
                            SmartFadeDivider(
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .fillMaxWidth(0.9f),
                                color = Color(0x40FFFFFF)
                            )
                            // GPS Coordinates (with accurate N/S & E/W notation)
                            if (locationState.point != null && overlayConfig.showCoordinates &&
                                (!overlayConfig.showWeatherInfo || overlayConfig.weatherShowCoordinates) &&
                                overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP
                            ) {
                                val coordFormatted = com.example.overlay.SmartTimestampComposer.formatCoordinatesLine(
                                    location = locationState.point!!,
                                    precision = overlayConfig.coordinatePrecision,
                                    coordinateFormat = cameraSettings.coordinateFormat
                                )
                                Text(
                                    text = coordFormatted,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            // Dynamic Location Name & Optional Weather Information
                            val displayLoc = currentLocationName.ifBlank { overlayConfig.lastDetectedLocationName }.trim()
                            if (overlayConfig.showWeatherInfo && overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
                                val weatherLines = com.example.overlay.SmartTimestampComposer.formatLocationAndWeatherLines(
                                    config = overlayConfig,
                                    locationName = displayLoc,
                                    weatherObservation = latestWeatherObservation,
                                    captureTimestampMs = currentTimeMillis
                                )
                                weatherLines.forEach { wLine ->
                                    Text(
                                        text = wLine,
                                        color = Color.White.copy(alpha = 0.92f),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else if (overlayConfig.showLocationName && displayLoc.isNotBlank() && !displayLoc.equals("None", ignoreCase = true) && overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
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
                            // Highlight Line: Authoritative Side + Lane + Activity + Sub-Activity in Yellow
                            val highlightTokens = mutableListOf<String>()
                            if (!authoritativePreviewSide.isNullOrBlank()) {
                                highlightTokens.add(authoritativePreviewSide)
                            }
                            if (projectProfile.hasActiveLane()) {
                                highlightTokens.add(projectProfile.lane)
                            }
                            if (projectProfile.hasActiveActivity()) {
                                highlightTokens.add(projectProfile.activity)
                            }
                            if (projectProfile.hasActiveSubActivity()) {
                                highlightTokens.add("(${projectProfile.subActivity})")
                            }
                            if (highlightTokens.isNotEmpty() && overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
                                Text(
                                    text = highlightTokens.joinToString(" "),
                                    color = HighlightYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (projectProfile.customDescription.isNotBlank() &&
                                !projectProfile.customDescription.equals("None", ignoreCase = true) &&
                                overlayConfig.template != OverlayTemplate.MINIMAL_TIMESTAMP
                            ) {
                                Text(
                                    text = projectProfile.customDescription,
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium
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
                // Always-visible Responsive Highway Functions Bar (Side, Lane, Activity, Chainage, Custom Comments)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    val isNarrowPortrait = maxWidth < 360.dp
                    val hGap = if (isNarrowPortrait) 5.dp else 6.dp
                    val vGap = if (isNarrowPortrait) 4.dp else 5.dp

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("portrait_highway_functions_bar"),
                        horizontalArrangement = Arrangement.spacedBy(hGap, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(vGap)
                    ) {
                        // Direct-tap Side chip (LHS -> RHS -> BHS -> NONE -> LHS);
                        // Long-press resumes Auto direction. Tiny animated red bulb ONLY when Manual override is active.
                        FloatingGlassCard(
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = Color(0x1A101726),
                            borderColor = Color(0x35FFFFFF),
                            onClick = { cycleNextCameraSide() },
                            onLongClick = {
                                if (smartDirectionState.isManualOverride) {
                                    viewModel.resetSideToAuto()
                                    viewModel.showToast("Auto direction resumed", ToastType.INFO)
                                }
                            },
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .testTag("side_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (smartDirectionState.isManualOverride) {
                                    CameraManualDirectionRedBulb(diameterDp = 6.5.dp)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(animatedSideTint)
                                    )
                                }
                                Text(
                                    text = currentSideDisplay.displayLabel,
                                    color = animatedSideTint,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Lane / Type chip
                        FloatingGlassCard(
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = Color(0x1A101726),
                            borderColor = Color(0x35FFFFFF),
                            onClick = {
                                val lanes = ProjectProfile.LANE_PRESETS
                                val currentIdx = lanes.indexOfFirst { it.equals(projectProfile.lane, ignoreCase = true) }
                                val nextIndex = if (currentIdx >= 0) (currentIdx + 1) % lanes.size else 0
                                viewModel.updateLane(lanes[nextIndex])
                            },
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight()
                                .testTag("lane_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
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
                                    text = projectProfile.lane.ifBlank { "None" },
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Activity chip
                        Box {
                            val actText = projectProfile.activity.ifBlank { "None" }
                            val subText = if (projectProfile.hasActiveSubActivity()) " (${projectProfile.subActivity})" else ""
                            val chipDisplay = if (actText.equals("None", ignoreCase = true)) "None" else "$actText$subText"

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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
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
                                        text = chipDisplay,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 140.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showActivityMenu,
                                onDismissRequest = { showActivityMenu = false }
                            ) {
                                ProjectProfile.ACTIVITY_PRESETS.forEach { act ->
                                    val isSel = projectProfile.activity.equals(act, ignoreCase = true) ||
                                        (act == "None" && projectProfile.activity.isBlank())
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = act,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) HighlightYellow else Color.Unspecified
                                            )
                                        },
                                        onClick = {
                                            viewModel.updateActivity(act)
                                            showActivityMenu = false
                                        }
                                    )
                                }
                                SmartFadeDivider()
                                DropdownMenuItem(
                                    text = { Text("Custom Activity & Sub-Activity...", color = HighlightYellow, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showActivityMenu = false
                                        showManualActivityDialog = true
                                    }
                                )
                            }
                        }

                        // Always-visible Chainage quick edit button (Value + Animated Status Bulbs Only)
                        val isManualActive = chainageIndicators.manualStatus == ChainageBubbleStatus.ACTIVE
                        val isCustomActive = chainageIndicators.customStatus == ChainageBubbleStatus.ACTIVE
                        val chipBg = when {
                            isManualActive -> Color(0x25FACC15)
                            isCustomActive -> Color(0x259333EA)
                            chainageIndicators.kmzStatus == ChainageBubbleStatus.WARNING -> Color(0x25F59E0B)
                            else -> Color(0x1A101726)
                        }
                        val chipBorder = when {
                            isManualActive -> HighlightYellow
                            isCustomActive -> Color(0xFFA855F7)
                            chainageIndicators.kmzStatus == ChainageBubbleStatus.WARNING -> Color(0xFFF59E0B)
                            else -> Color(0x35FFFFFF)
                        }
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = chainageIndicators.displayChainage,
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.testTag("hud_chainage_value")
                                )
                                ChainageStatusBubblesRow(
                                    state = chainageIndicators,
                                    isDarkTheme = true,
                                    isScreenVisible = isCameraScreenVisible,
                                    showInactiveBubbles = false,
                                    showLabels = false,
                                    tagPrefix = "hud_portrait_"
                                )
                            }
                        }

                        // Custom Comments chip
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
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
                    zoomCapabilities = zoomCapabilities,
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
                                .background(if (captureMode == CaptureMode.PHOTO) Color(0x38FFB52E) else Color.Transparent)
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
                                .background(if (captureMode == CaptureMode.VIDEO) Color(0x38FF5263) else Color.Transparent)
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
                                if (!isCapturing) {
                                    try {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    } catch (ignored: Exception) {}
                                    viewModel.setCapturing(true)
                                    showQuickCaptureFlash = true
                                    val quickSnapshot = viewModel.createCurrentSnapshot()
                                    capturePhoto(
                                        controller = controller,
                                        context = context,
                                        deviceRotationDegrees = rotationDegrees.toInt(),
                                        isLandscape = isLandscape,
                                        photoAspectRatio = "Full",
                                        isFrontCamera = isFrontCamera,
                                        frontCameraMirror = cameraSettings.frontCameraMirror,
                                        playShutterSound = cameraSettings.captureSound,
                                        onSuccessWithExif = { bitmap, cameraExif ->
                                            viewModel.processCapture(
                                                bitmap,
                                                quickSnapshot.copy(cameraExifMetadata = cameraExif)
                                            )
                                        },
                                        onError = { error ->
                                            viewModel.setCapturing(false)
                                            viewModel.showError(
                                                error.localizedMessage?.takeIf { it.isNotBlank() }
                                                    ?: "Quick photo capture failed. Please try again."
                                            )
                                        }
                                    )
                                }
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
            }
        }

        // Non-intrusive Video Export Progress Pill (persists across navigation & rotation)
        if (videoExportState.isActive) {
            com.example.ui.video.VideoExportProgressPill(
                state = videoExportState,
                onCancel = { viewModel.cancelVideoExport(context) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = if (isLandscape) 98.dp else 86.dp)
            )
        }

        // Minimal Smart Chainage Edit Dialog
        if (showManualDialog) {
            val dialogColors = com.example.ui.theme.rememberDialogColors()
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            val primaryAccent = CtecsTheme.colors.accent
            val onAccent = CtecsTheme.colors.onAccent

            // Lock initial input on dialog open so background KMZ updates never overwrite user input
            var chainageInput by remember(showManualDialog) {
                val initVal = when {
                    !manualChainageText.isNullOrBlank() -> manualChainageText!!
                    matchResult.displayChainage != "---" && matchResult.displayChainage != "Manual" -> matchResult.displayChainage
                    else -> ""
                }
                mutableStateOf(initVal)
            }
            var editingItemOriginal by remember(showManualDialog) { mutableStateOf<String?>(null) }
            var validationError by remember(showManualDialog) { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = { viewModel.closeManualChainageDialog() },
                shape = dialogColors.shape,
                containerColor = dialogColors.containerColor,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .widthIn(min = 270.dp, max = if (isLandscape) 370.dp else 330.dp)
                    .testTag("manual_chainage_dialog"),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Main visual focus: Active Chainage Value
                        Text(
                            text = chainageIndicators.displayChainage,
                            color = dialogColors.titleColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("chainage_edit_primary_value")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Secondary compact rounded status bubbles in edit dialog
                        ChainageStatusBubblesRow(
                            state = chainageIndicators,
                            isDarkTheme = dialogColors.isDark,
                            isScreenVisible = isCameraScreenVisible,
                            showInactiveBubbles = true,
                            showLabels = true,
                            tagPrefix = ""
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = chainageInput,
                                onValueChange = {
                                    chainageInput = it
                                    validationError = null
                                },
                                placeholder = {
                                    Text(
                                        "0+000",
                                        color = dialogColors.textSecondaryColor.copy(alpha = 0.55f),
                                        fontSize = 13.sp
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
                                                contentDescription = "Clear",
                                                tint = dialogColors.textSecondaryColor,
                                                modifier = Modifier.size(16.dp)
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
                                            validationError = "Invalid format (e.g. 198+880)"
                                        }
                                    }
                                ),
                                colors = ctecsBorderlessInputColors(
                                    containerColor = dialogColors.inputBackground,
                                    accentColor = primaryAccent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manual_chainage_input")
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = primaryAccent.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .size(48.dp)
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
                                            validationError = "Invalid format (e.g. 198+880)"
                                        }
                                    }
                                    .testTag("add_custom_chainage_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (editingItemOriginal != null) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = if (editingItemOriginal != null) "Update" else "Add",
                                        tint = primaryAccent,
                                        modifier = Modifier.size(18.dp)
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
                        }

                        // Compact Step Nudge Pills (-50m, -10m, +10m, +50m)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            listOf(-50L to "-50m", -10L to "-10m", 10L to "+10m", 50L to "+50m").forEach { (delta, label) ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = dialogColors.surfaceColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(50))
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
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = dialogColors.textColor
                                        )
                                    }
                                }
                            }
                        }

                        if (savedCustomChainages.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = if (isLandscape) 105.dp else 135.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                savedCustomChainages.forEach { savedItem ->
                                    val isSelected = manualChainageText.equals(savedItem, ignoreCase = true)
                                    val isBeingEdited = editingItemOriginal.equals(savedItem, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = when {
                                            isBeingEdited -> primaryAccent.copy(alpha = 0.16f)
                                            isSelected -> AccentRed.copy(alpha = 0.14f)
                                            else -> dialogColors.surfaceColor
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(50))
                                            .clickable {
                                                chainageInput = savedItem
                                                viewModel.setManualChainage(savedItem)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                isSelected -> AccentRed
                                                                isBeingEdited -> primaryAccent
                                                                else -> dialogColors.textSecondaryColor.copy(alpha = 0.45f)
                                                            }
                                                        )
                                                )
                                                Text(
                                                    text = savedItem,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = dialogColors.textColor
                                                )
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
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit $savedItem",
                                                        tint = primaryAccent,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (editingItemOriginal.equals(savedItem, ignoreCase = true)) {
                                                            editingItemOriginal = null
                                                        }
                                                        viewModel.deleteCustomChainagePreset(savedItem)
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete $savedItem",
                                                        tint = AccentRed,
                                                        modifier = Modifier.size(13.dp)
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
                                validationError = "Invalid format (e.g. 198+880)"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = onAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_manual_chainage_button")
                    ) {
                        Text(
                            text = "Apply",
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

        // Manual Activity & Sub-Activity Dialog
        if (showManualActivityDialog) {
            val dialogColors = com.example.ui.theme.rememberDialogColors()
            var activityInput by remember(projectProfile.activity, showManualActivityDialog) {
                mutableStateOf(if (projectProfile.activity.equals("None", ignoreCase = true)) "" else projectProfile.activity)
            }
            var subActivityInput by remember(projectProfile.subActivity, showManualActivityDialog) {
                mutableStateOf(if (projectProfile.subActivity.equals("None", ignoreCase = true)) "" else projectProfile.subActivity)
            }
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            val primaryAccent = CtecsTheme.colors.accent
            val onAccent = CtecsTheme.colors.onAccent

            AlertDialog(
                onDismissRequest = { showManualActivityDialog = false },
                shape = dialogColors.shape,
                containerColor = dialogColors.containerColor,
                tonalElevation = 0.dp,
                title = { Text("Activity & Sub-Activity", color = dialogColors.titleColor, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Select a preset or type a custom activity and optional sub-activity (or choose 'None' to leave blank):",
                            fontSize = 12.sp,
                            color = dialogColors.textSecondaryColor
                        )
                        OutlinedTextField(
                            value = activityInput,
                            onValueChange = { activityInput = it },
                            label = { Text("Activity / Layer Name (Blank = None)") },
                            placeholder = { Text("e.g. DBM, BC, WMM, GSB, Tack Coat") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            colors = ctecsBorderlessInputColors(
                                containerColor = dialogColors.inputBackground,
                                accentColor = primaryAccent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("manual_activity_input")
                        )

                        OutlinedTextField(
                            value = subActivityInput,
                            onValueChange = { subActivityInput = it },
                            label = { Text("Sub-Activity / Stage (Optional)") },
                            placeholder = { Text("e.g. Layer 1, Final Layer, Core Cutting") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    val finalAct = activityInput.trim().ifBlank { "None" }
                                    val finalSub = subActivityInput.trim()
                                    viewModel.updateActivity(finalAct)
                                    viewModel.updateSubActivity(finalSub)
                                    viewModel.showToast(
                                        if (finalAct == "None") "Activity set to None" else "Activity updated: $finalAct",
                                        ToastType.SUCCESS
                                    )
                                    showManualActivityDialog = false
                                }
                            ),
                            colors = ctecsBorderlessInputColors(
                                containerColor = dialogColors.inputBackground,
                                accentColor = primaryAccent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("manual_sub_activity_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalAct = activityInput.trim().ifBlank { "None" }
                            val finalSub = subActivityInput.trim()
                            viewModel.updateActivity(finalAct)
                            viewModel.updateSubActivity(finalSub)
                            viewModel.showToast(
                                if (finalAct == "None") "Activity set to None" else "Activity updated: $finalAct",
                                ToastType.SUCCESS
                            )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                viewModel.updateActivity("None")
                                viewModel.updateSubActivity("")
                                viewModel.showToast("Activity cleared (None)", ToastType.INFO)
                                showManualActivityDialog = false
                            }
                        ) {
                            Text("Set None", color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { showManualActivityDialog = false }) {
                            Text("Cancel", color = dialogColors.textSecondaryColor)
                        }
                    }
                }
            )
        }

        // Custom Comment Dialog
        if (showCustomCommentDialog) {
            val dialogColors = com.example.ui.theme.rememberDialogColors()
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            val primaryAccent = CtecsTheme.colors.accent
            val onAccent = CtecsTheme.colors.onAccent

            AlertDialog(
                onDismissRequest = { showCustomCommentDialog = false },
                shape = dialogColors.shape,
                containerColor = dialogColors.containerColor,
                tonalElevation = 0.dp,
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
                            colors = ctecsBorderlessInputColors(
                                containerColor = dialogColors.inputBackground,
                                accentColor = primaryAccent
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
                tonalElevation = 0.dp,
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
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
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
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
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
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Muzaffarnagar Start (130+000, 29.4750N 77.7120E)")
                        }

                        Button(
                            onClick = {
                                viewModel.startLocation()
                                showSimDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CtecsTheme.colors.accent.copy(alpha = 0.16f),
                                contentColor = CtecsTheme.colors.accent
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use Real Device Hardware GPS", fontWeight = FontWeight.Bold)
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
                captureExposureEvStops = captureExposureEvStops,
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
 * Compact, borderless tap-to-switch Camera Crop Mode pill displayed directly on the Camera Screen.
 *
 * - Single tap cycles: `Full` -> `Semi Crop` -> `Rectangular Crop` -> `Square Crop` -> `Full`.
 * - Long press resets directly to `Full` (zero crop).
 * - Never opens a dropdown or popup; updates preview framing immediately with subtle icon/label animation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraCropModeSwitchPill(
    cropMode: com.example.model.CameraCropMode,
    isLandscape: Boolean,
    animatedRotation: Float = 0f,
    onTapCycle: () -> Unit,
    onLongPressResetFull: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isCropped = cropMode != com.example.model.CameraCropMode.FULL
    val containerColor by animateColorAsState(
        targetValue = if (isCropped) Color(0xCC1E2636) else Color(0x99080B12),
        animationSpec = tween(160),
        label = "crop_pill_bg"
    )
    val contentTint by animateColorAsState(
        targetValue = if (isCropped) HighlightYellow else Color.White,
        animationSpec = tween(160),
        label = "crop_pill_tint"
    )
    val modeIcon = when (cropMode) {
        com.example.model.CameraCropMode.FULL -> Icons.Default.CropFree
        com.example.model.CameraCropMode.SEMI_CROP -> Icons.Default.Crop
        com.example.model.CameraCropMode.RECTANGULAR_CROP -> if (isLandscape) Icons.Default.CropLandscape else Icons.Default.CropPortrait
        com.example.model.CameraCropMode.SQUARE_CROP -> Icons.Default.CropSquare
    }

    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .combinedClickable(
                onClick = onTapCycle,
                onLongClick = onLongPressResetFull,
                onClickLabel = "Switch camera crop mode: ${cropMode.label}"
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = cropMode,
            transitionSpec = {
                fadeIn(tween(130)) togetherWith fadeOut(tween(110))
            },
            label = "crop_mode_pill_content"
        ) { activeMode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.graphicsLayer {
                    if (!isLandscape) rotationZ = -animatedRotation
                }
            ) {
                Icon(
                    imageVector = modeIcon,
                    contentDescription = "Crop mode ${activeMode.label}",
                    tint = contentTint,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = activeMode.shortLabel,
                    color = contentTint,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
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
    captureExposureEvStops: Float = 0f,
    onToggleFlash: () -> Unit,
    onUpdateCameraSettings: (com.example.model.CameraSettings) -> Unit,
    onUpdateOverlayConfig: (OverlayConfig) -> Unit,
    onNavigateToFullSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogColors = com.example.ui.theme.rememberDialogColors()
    val accentColor = CtecsTheme.colors.accent
    val onAccentColor = CtecsTheme.colors.onAccent

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        tonalElevation = 0.dp,
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
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
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

                // Auto Exposure / Capture Brightness (returns smoothly to automatic camera exposure when ON)
                val evDisplay = CameraBrightnessEngine.formatEvStops(captureExposureEvStops)
                val autoBrightnessSubtitle = if (cameraSettings.autoBrightnessEnabled) {
                    "Automatic camera exposure active (0.0 EV)"
                } else {
                    "Right-side capture exposure ($evDisplay EV) • Tap ON for Auto"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUpdateCameraSettings(
                                cameraSettings.copy(autoBrightnessEnabled = !cameraSettings.autoBrightnessEnabled)
                            )
                        }
                        .testTag("camera_auto_brightness_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Auto Brightness", color = dialogColors.textColor, fontSize = 13.sp)
                        Text(
                            text = autoBrightnessSubtitle,
                            color = dialogColors.textSecondaryColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = cameraSettings.autoBrightnessEnabled,
                        onCheckedChange = {
                            onUpdateCameraSettings(cameraSettings.copy(autoBrightnessEnabled = it))
                        },
                        modifier = Modifier.testTag("camera_auto_brightness_switch"),
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

                // Keep Original Photo (synced between CameraSettings.saveOriginalAndStamped and OverlayConfig.saveOriginalImage)
                val saveOriginalActive = overlayConfig.saveOriginalImage || cameraSettings.saveOriginalAndStamped
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val next = !saveOriginalActive
                        onUpdateCameraSettings(cameraSettings.copy(saveOriginalAndStamped = next))
                        onUpdateOverlayConfig(overlayConfig.copy(saveOriginalImage = next))
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Save Clean Original Photo", color = dialogColors.textColor, fontSize = 13.sp)
                    Switch(
                        checked = saveOriginalActive,
                        onCheckedChange = { next ->
                            onUpdateCameraSettings(cameraSettings.copy(saveOriginalAndStamped = next))
                            onUpdateOverlayConfig(overlayConfig.copy(saveOriginalImage = next))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor, checkedTrackColor = accentColor)
                    )
                }

                SmartFadeDivider()

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
                                border = null
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
                                border = null
                            )
                        }
                    }
                }

                SmartFadeDivider()

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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingGlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    backgroundColor: Color = Color(0x99080B12),
    borderColor: Color = Color.Transparent,
    borderWidth: androidx.compose.ui.unit.Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val flatFill = if (backgroundColor == Color(0x1A101726)) Color(0x99080B12) else backgroundColor
    val interactiveModifier = when {
        onClick != null && onLongClick != null -> modifier.clip(shape).combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
        onClick != null -> modifier.clip(shape).clickable(onClick = onClick)
        else -> modifier.clip(shape)
    }
    Surface(
        shape = shape,
        color = flatFill,
        modifier = interactiveModifier
    ) {
        content()
    }
}

enum class ChainageBubbleStatus {
    ACTIVE,
    WARNING,
    INACTIVE
}

data class ChainageIndicatorsState(
    val displayChainage: String,
    val kmzStatus: ChainageBubbleStatus,
    val customStatus: ChainageBubbleStatus,
    val manualStatus: ChainageBubbleStatus
)

/**
 * Resolves the three minimal smart chainage status indicators (KMZ, CUSTOM, MANUAL)
 * with strict source priority:
 * - MANUAL mode active -> MANUAL is ACTIVE (static bulb), KMZ & CUSTOM are INACTIVE,
 *   and manual chainage is locked against automatic KMZ overwriting.
 * - CUSTOM correction applied to KMZ alignment -> both KMZ (ACTIVE or WARNING) and
 *   CUSTOM (ACTIVE) are shown without implying two independent engines.
 * - KMZ alignment calculation -> KMZ is ACTIVE when reliable, WARNING when alignment
 *   matching is unreliable or out-of-corridor for a loaded route, and INACTIVE when no route is available.
 */
internal fun resolveChainageIndicatorsState(
    matchResult: ChainageMatchResult,
    manualChainageText: String?,
    hasActiveRoute: Boolean,
    isCorrectionsEnabled: Boolean
): ChainageIndicatorsState {
    val cleanManual = manualChainageText?.trim()?.takeIf { it.isNotEmpty() }
    val isManualActive = cleanManual != null ||
        matchResult.isManual ||
        matchResult.source == ChainageSource.MANUAL

    if (isManualActive) {
        val manualDisplay = cleanManual
            ?: matchResult.customChainageText?.takeIf { it.isNotBlank() }
            ?: matchResult.chainage?.format()
            ?: matchResult.displayChainage
        return ChainageIndicatorsState(
            displayChainage = manualDisplay,
            kmzStatus = ChainageBubbleStatus.INACTIVE,
            customStatus = ChainageBubbleStatus.INACTIVE,
            manualStatus = ChainageBubbleStatus.ACTIVE
        )
    }

    val isKmzDerived = (matchResult.source == ChainageSource.KMZ ||
        matchResult.source == ChainageSource.CUSTOM_CORRECTION) &&
        matchResult.chainage != null &&
        matchResult.confidence != MatchConfidence.NO_MATCH

    val isUnreliable = matchResult.isUncertain ||
        matchResult.confidence == MatchConfidence.LOW ||
        !matchResult.sourceKmzReferenceStatus.equals("Valid", ignoreCase = true)

    val kmzStatus = when {
        !hasActiveRoute && !isKmzDerived -> ChainageBubbleStatus.INACTIVE
        !isKmzDerived -> ChainageBubbleStatus.WARNING
        isUnreliable -> ChainageBubbleStatus.WARNING
        else -> ChainageBubbleStatus.ACTIVE
    }

    val isCustomApplied = isCorrectionsEnabled &&
        matchResult.source == ChainageSource.CUSTOM_CORRECTION &&
        matchResult.chainage != null &&
        matchResult.confidence != MatchConfidence.NO_MATCH

    val customStatus = if (isCustomApplied) {
        ChainageBubbleStatus.ACTIVE
    } else {
        ChainageBubbleStatus.INACTIVE
    }

    return ChainageIndicatorsState(
        displayChainage = matchResult.displayChainage,
        kmzStatus = kmzStatus,
        customStatus = customStatus,
        manualStatus = ChainageBubbleStatus.INACTIVE
    )
}

@Composable
internal fun ChainageStatusBubblesRow(
    state: ChainageIndicatorsState,
    isDarkTheme: Boolean,
    isScreenVisible: Boolean,
    showInactiveBubbles: Boolean = true,
    showLabels: Boolean = false,
    tagPrefix: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (showLabels) 4.dp else 5.dp)
    ) {
        if (showInactiveBubbles) {
            ChainageStatusBubble(
                label = "KMZ",
                status = state.kmzStatus,
                isDynamic = true,
                showLabel = showLabels,
                isDarkTheme = isDarkTheme,
                isScreenVisible = isScreenVisible,
                bubbleTag = "${tagPrefix}chainage_status_bubble_kmz",
                bulbTag = "${tagPrefix}chainage_bulb_kmz"
            )
            ChainageStatusBubble(
                label = "CUSTOM",
                status = state.customStatus,
                isDynamic = true,
                showLabel = showLabels,
                isDarkTheme = isDarkTheme,
                isScreenVisible = isScreenVisible,
                bubbleTag = "${tagPrefix}chainage_status_bubble_custom",
                bulbTag = "${tagPrefix}chainage_bulb_custom"
            )
            ChainageStatusBubble(
                label = "MANUAL",
                status = state.manualStatus,
                isDynamic = true,
                showLabel = showLabels,
                isDarkTheme = isDarkTheme,
                isScreenVisible = isScreenVisible,
                bubbleTag = "${tagPrefix}chainage_status_bubble_manual",
                bulbTag = "${tagPrefix}chainage_bulb_manual"
            )
        } else {
            if (state.manualStatus == ChainageBubbleStatus.ACTIVE) {
                ChainageStatusBubble(
                    label = "MANUAL",
                    status = ChainageBubbleStatus.ACTIVE,
                    isDynamic = true,
                    showLabel = showLabels,
                    isDarkTheme = isDarkTheme,
                    isScreenVisible = isScreenVisible,
                    bubbleTag = "${tagPrefix}chainage_status_bubble_manual",
                    bulbTag = "${tagPrefix}chainage_bulb_manual"
                )
            } else {
                if (state.kmzStatus != ChainageBubbleStatus.INACTIVE) {
                    ChainageStatusBubble(
                        label = "KMZ",
                        status = state.kmzStatus,
                        isDynamic = true,
                        showLabel = showLabels,
                        isDarkTheme = isDarkTheme,
                        isScreenVisible = isScreenVisible,
                        bubbleTag = "${tagPrefix}chainage_status_bubble_kmz",
                        bulbTag = "${tagPrefix}chainage_bulb_kmz"
                    )
                }
                if (state.customStatus == ChainageBubbleStatus.ACTIVE) {
                    ChainageStatusBubble(
                        label = "CUSTOM",
                        status = ChainageBubbleStatus.ACTIVE,
                        isDynamic = true,
                        showLabel = showLabels,
                        isDarkTheme = isDarkTheme,
                        isScreenVisible = isScreenVisible,
                        bubbleTag = "${tagPrefix}chainage_status_bubble_custom",
                        bulbTag = "${tagPrefix}chainage_bulb_custom"
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChainageStatusBubble(
    label: String,
    status: ChainageBubbleStatus,
    isDynamic: Boolean,
    showLabel: Boolean = false,
    isDarkTheme: Boolean,
    isScreenVisible: Boolean,
    bubbleTag: String,
    bulbTag: String,
    modifier: Modifier = Modifier
) {
    val shouldPulse = isDynamic && status == ChainageBubbleStatus.ACTIVE && isScreenVisible
    val pulseProgress = if (shouldPulse) {
        val transition = rememberInfiniteTransition(label = "bulb_pulse_$label")
        val animated by transition.animateFloat(
            initialValue = 0.0f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bulb_pulse_progress_$label"
        )
        animated
    } else {
        1.0f
    }

    val bulbColor = when (status) {
        ChainageBubbleStatus.ACTIVE -> when (label) {
            "KMZ" -> if (isDarkTheme) Color(0xFF22C55E) else Color(0xFF16A34A)
            "CUSTOM" -> if (isDarkTheme) Color(0xFFA855F7) else Color(0xFF7C3AED)
            else -> if (isDarkTheme) HighlightYellow else Color(0xFFEAB308)
        }
        ChainageBubbleStatus.WARNING -> if (isDarkTheme) Color(0xFFF59E0B) else Color(0xFFD97706)
        ChainageBubbleStatus.INACTIVE -> if (isDarkTheme) Color(0xFF64748B).copy(alpha = 0.45f) else Color(0xFF94A3B8).copy(alpha = 0.55f)
    }

    val accessibleDescription = when (status) {
        ChainageBubbleStatus.ACTIVE -> when (label) {
            "KMZ" -> "KMZ chainage active"
            "CUSTOM" -> "Custom correction chainage active"
            else -> "Manual chainage active"
        }
        ChainageBubbleStatus.WARNING -> when (label) {
            "KMZ" -> "KMZ chainage signal weak"
            "CUSTOM" -> "Custom correction warning"
            else -> "Manual chainage warning"
        }
        ChainageBubbleStatus.INACTIVE -> "$label chainage inactive"
    }

    if (!showLabel) {
        val haloAlpha = if (shouldPulse) 0.16f + 0.34f * pulseProgress else 0f
        val coreAlpha = if (shouldPulse) 0.72f + 0.28f * pulseProgress else 0.92f
        val coreScale = if (shouldPulse) 0.90f + 0.18f * pulseProgress else 1.0f

        Box(
            modifier = modifier
                .size(12.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibleDescription
                    stateDescription = if (shouldPulse) "${label}_ANIMATED_ACTIVE" else "${label}_${status.name}"
                }
                .testTag(bubbleTag),
            contentAlignment = Alignment.Center
        ) {
            if (shouldPulse) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer {
                            val haloScale = 0.85f + 0.25f * pulseProgress
                            scaleX = haloScale
                            scaleY = haloScale
                        }
                        .clip(CircleShape)
                        .background(bulbColor.copy(alpha = haloAlpha))
                )
            }
            Box(
                modifier = Modifier
                    .size(6.5.dp)
                    .graphicsLayer {
                        alpha = coreAlpha
                        scaleX = coreScale
                        scaleY = coreScale
                    }
                    .clip(CircleShape)
                    .background(bulbColor)
                    .semantics {
                        contentDescription = accessibleDescription
                        stateDescription = if (shouldPulse) "ANIMATED_ACTIVE" else status.name
                    }
                    .testTag(bulbTag)
            )
        }
        return
    }

    val textColor = when (status) {
        ChainageBubbleStatus.ACTIVE -> when (label) {
            "KMZ" -> if (isDarkTheme) Color(0xFF4ADE80) else Color(0xFF15803D)
            "CUSTOM" -> if (isDarkTheme) Color(0xFFC084FC) else Color(0xFF6D28D9)
            else -> if (isDarkTheme) HighlightYellow else Color(0xFFA16207)
        }
        ChainageBubbleStatus.WARNING -> if (isDarkTheme) Color(0xFFFBBF24) else Color(0xFFB45309)
        ChainageBubbleStatus.INACTIVE -> if (isDarkTheme) Color(0xFF94A3B8).copy(alpha = 0.55f) else Color(0xFF64748B).copy(alpha = 0.65f)
    }

    val bgColor = when (status) {
        ChainageBubbleStatus.ACTIVE -> bulbColor.copy(alpha = if (isDarkTheme) 0.16f else 0.12f)
        ChainageBubbleStatus.WARNING -> bulbColor.copy(alpha = if (isDarkTheme) 0.16f else 0.12f)
        ChainageBubbleStatus.INACTIVE -> CtecsTheme.colors.surface
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        modifier = modifier
            .semantics {
                contentDescription = accessibleDescription
                stateDescription = "${label}_${status.name}"
            }
            .testTag(bubbleTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.5.dp)
        ) {
            val dotAlpha = if (shouldPulse) 0.6f + 0.4f * pulseProgress else 1.0f
            val dotScale = if (shouldPulse) 0.88f + 0.14f * pulseProgress else 1.0f
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .graphicsLayer {
                        alpha = dotAlpha
                        scaleX = dotScale
                        scaleY = dotScale
                    }
                    .clip(CircleShape)
                    .background(bulbColor)
                    .semantics {
                        stateDescription = if (shouldPulse) "ANIMATED_ACTIVE" else status.name
                    }
                    .testTag(bulbTag)
            )
            Text(
                text = label,
                color = textColor,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Live sensor-driven Native Electronic Horizon Level Reticle matching modern OEM camera viewfinders:
 * - Outer translucent circular guide + center crosshair
 * - Dynamic horizontal level bar that tilts smoothly with gravity roll angle
 * - Snaps golden yellow when level within ±1.5°
 * - Zero battery drain when hidden; safe fallback to 0° on devices without gravity/accelerometer sensors.
 */
@Composable
fun NativeHorizonLevelIndicator(
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var rawRollDegrees by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context, isLandscape) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)

        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                val values = event?.values ?: return
                if (values.size < 2) return
                val ax = values[0].toDouble()
                val ay = values[1].toDouble()
                val mag = kotlin.math.hypot(ax, ay)
                if (mag < 1.5) return // Phone lying flat -> keep level steady
                val angleRad = kotlin.math.atan2(ax, ay)
                var deg = Math.toDegrees(angleRad).toFloat()
                if (isLandscape) {
                    deg = if (deg >= 0f) deg - 90f else deg + 90f
                }
                // Wrap to [-45, +45] relative to nearest horizontal axis
                while (deg > 90f) deg -= 180f
                while (deg < -90f) deg += 180f
                rawRollDegrees = deg.coerceIn(-45f, 45f)
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }

        if (sensorManager != null && sensor != null) {
            try {
                sensorManager.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
            } catch (_: Throwable) {}
        }

        onDispose {
            try {
                sensorManager?.unregisterListener(listener)
            } catch (_: Throwable) {}
        }
    }

    val smoothRoll by animateFloatAsState(
        targetValue = rawRollDegrees,
        animationSpec = tween(90, easing = FastOutSlowInEasing),
        label = "horizon_roll"
    )
    val isLevel = kotlin.math.abs(smoothRoll) <= 1.5f
    val activeColor by animateColorAsState(
        targetValue = if (isLevel) HighlightYellow else Color.White.copy(alpha = 0.85f),
        animationSpec = tween(140),
        label = "horizon_level_color"
    )

    ComposeCanvas(
        modifier = modifier.size(112.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val circleRadius = 28.dp.toPx()
        val strokeThin = 1.3.dp.toPx()
        val strokeBar = if (isLevel) 2.2.dp.toPx() else 1.7.dp.toPx()

        // Outer reference circle
        drawCircle(
            color = Color.White.copy(alpha = 0.50f),
            radius = circleRadius,
            center = Offset(cx, cy),
            style = Stroke(width = strokeThin)
        )

        // Fixed horizontal reference wing ticks outside circle
        val outerTickStart = circleRadius + 4.dp.toPx()
        val outerTickEnd = circleRadius + 18.dp.toPx()
        drawLine(
            color = activeColor,
            start = Offset(cx - outerTickEnd, cy),
            end = Offset(cx - outerTickStart, cy),
            strokeWidth = strokeBar,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = Offset(cx + outerTickStart, cy),
            end = Offset(cx + outerTickEnd, cy),
            strokeWidth = strokeBar,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Dynamic rotating center crosshair + inner horizon line
        rotate(degrees = -smoothRoll, pivot = Offset(cx, cy)) {
            val innerHalf = circleRadius - 4.dp.toPx()
            drawLine(
                color = activeColor,
                start = Offset(cx - innerHalf, cy),
                end = Offset(cx + innerHalf, cy),
                strokeWidth = strokeBar,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            val crossVert = 7.dp.toPx()
            drawLine(
                color = activeColor,
                start = Offset(cx, cy - crossVert),
                end = Offset(cx, cy + crossVert),
                strokeWidth = strokeThin,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}


