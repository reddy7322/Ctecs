package com.example.ui.home

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.GeoUtils
import com.example.geo.MapHeadingState
import com.example.geo.OsmTileManager
import com.example.location.LocationNameResolver
import com.example.location.LocationState
import com.example.location.LocationTracker
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.DirectionDetectionState
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.HudMapMarkerStyle
import com.example.model.MatchConfidence
import com.example.model.MiniMapOrientationMode
import com.example.model.MiniMapStyle
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.model.SmartDirectionState
import com.example.navigation.ActiveChainageNavigationTarget
import com.example.overlay.MiniMapRenderer
import com.example.ui.MainViewModel
import com.example.ui.components.SmartDirectionIndicatorBadge
import com.example.ui.components.SmartDirectionModePickerMenu
import com.example.ui.dialogs.ChainageManagementDialog
import com.example.ui.dialogs.ChainageSourceBadge
import com.example.ui.dialogs.NavigateToChainageDialog
import com.example.ui.dialogs.NavigateToChainageQuickButton
import com.example.ui.map.MiniMapHeadingUnavailableBadge
import com.example.ui.map.MiniMapViewportMath
import com.example.ui.map.miniMapNavigationGestures
import com.example.ui.theme.AccentRed
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.ctecsBorderlessInputColors
import com.example.ui.theme.smartFadeSurface
import com.example.ui.theme.smartMapEdgeFade
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToCamera: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTools: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToSurvey: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Normal Home Page obeys the device's system display sleep timeout;
    // FLAG_KEEP_SCREEN_ON is acquired only while the Screensaver overlay is active.
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val matchResult by viewModel.liveMatchResult.collectAsStateWithLifecycle()
    val smartDirectionState by viewModel.smartDirectionState.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()
    val compassState by viewModel.compassState.collectAsStateWithLifecycle()
    val mapHeadingState by viewModel.mapHeadingState.collectAsStateWithLifecycle()

    val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val mapThemePreference by viewModel.mapThemePreference.collectAsStateWithLifecycle()
    val mapTypePreference by viewModel.mapTypePreference.collectAsStateWithLifecycle()
    val screensaverSettings by viewModel.homeScreensaverSettings.collectAsStateWithLifecycle()

    // App-level & Home-level blocking modal states for safe screensaver gating
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val showWhatsNewDialog by viewModel.showWhatsNewDialog.collectAsStateWithLifecycle()
    val interruptedSession by viewModel.interruptedSurveySession.collectAsStateWithLifecycle()
    val isPageLoading by viewModel.isPageLoading.collectAsStateWithLifecycle()

    var showChainageManagementDialog by remember { mutableStateOf(false) }
    var showEditProjectStretchDialog by remember { mutableStateOf(false) }
    var showNavigateToChainageDialog by remember { mutableStateOf(false) }
    var activeChainageNavTarget by remember { mutableStateOf<ActiveChainageNavigationTarget?>(null) }
    val preferredExternalNavAppId by viewModel.preferredExternalNavAppId.collectAsStateWithLifecycle()
    var isScreensaverVisible by remember { mutableStateOf(false) }
    val screensaverStateHolder = remember { ScreensaverChainageStateHolder() }

    val activeScreensaverSensorPosture = rememberScreensaverSensorPosture(
        enabled = isScreensaverVisible &&
            screensaverSettings.autoOrientationEnabled &&
            !screensaverSettings.orientationLocked
    )
    val isScreensaverScreenOff by viewModel.isScreensaverScreenOff.collectAsStateWithLifecycle()
    val isScreensaverAodActive by viewModel.isScreensaverAodActive.collectAsStateWithLifecycle()
    val isKmStoneServiceRunning by viewModel.isKmStoneServiceRunning.collectAsStateWithLifecycle()
    val lastKmStoneHubEvent by viewModel.lastKmStoneHubEvent.collectAsStateWithLifecycle()
    val cameraSettings by viewModel.cameraSettings.collectAsStateWithLifecycle()
    val kmStoneAlertSettings by viewModel.kmStoneAlertSettings.collectAsStateWithLifecycle()
    val weatherUiState by viewModel.weatherUiState.collectAsStateWithLifecycle()
    LaunchedEffect(isScreensaverVisible) {
        viewModel.setScreensaverActive(isScreensaverVisible)
    }
    LaunchedEffect(isScreensaverVisible, activeScreensaverSensorPosture) {
        viewModel.updateScreensaverSensorPosture(
            if (isScreensaverVisible) activeScreensaverSensorPosture else null
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.setScreensaverActive(false)
        }
    }

    val isBlockingScreensaverModal = showChainageManagementDialog ||
        showEditProjectStretchDialog ||
        showNavigateToChainageDialog ||
        availableUpdate != null ||
        showWhatsNewDialog ||
        interruptedSession != null ||
        isPageLoading

    val isDark = com.example.ui.theme.isAppDarkTheme()
    val c = CtecsTheme.colors

    // Smooth Animated AMOLED Deep Black Dark Mode vs Professional Light Mode Palette
    val pageBgColor by animateColorAsState(
        targetValue = c.background,
        animationSpec = tween(220),
        label = "home_page_bg"
    )
    val cardBgColor by animateColorAsState(
        targetValue = c.surface,
        animationSpec = tween(220),
        label = "home_card_bg"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = c.subtleSeparator,
        animationSpec = tween(220),
        label = "home_card_border"
    )
    val mainTextColor by animateColorAsState(
        targetValue = c.textPrimary,
        animationSpec = tween(220),
        label = "home_main_text"
    )
    val subTextColor by animateColorAsState(
        targetValue = c.textSecondary,
        animationSpec = tween(220),
        label = "home_sub_text"
    )

    // Pulse transition for live GPS status dot (paused while full-screen Screensaver / Screen OFF / AOD is active)
    val gpsPulseAlpha: Float = if (!isScreensaverVisible && !isScreensaverScreenOff && !isScreensaverAodActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "gps_dot")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(750), repeatMode = RepeatMode.Reverse),
            label = "gps_dot_pulse"
        )
        animated
    } else {
        0.7f
    }

    val vmLocationName by viewModel.currentLocationName.collectAsStateWithLifecycle()
    val screensaverContext = LocalContext.current
    var screensaverResolvedLocationName by remember { mutableStateOf("") }
    var lastScreensaverGeocodePoint by remember { mutableStateOf<GeoPoint?>(null) }
    var lastScreensaverGeocodeAttemptMs by remember { mutableLongStateOf(0L) }
    var screensaverClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Throttled cached reverse-geocoding for active screensaver only (paused in Screen OFF and AMOLED AOD)
    LaunchedEffect(
        isScreensaverVisible,
        isScreensaverScreenOff,
        isScreensaverAodActive,
        locationState.point,
        vmLocationName,
        screensaverSettings.locationDynamicUpdates
    ) {
        if (vmLocationName.isNotBlank()) {
            screensaverResolvedLocationName = vmLocationName
        }
        val pt = locationState.point
        if (isScreensaverVisible &&
            !isScreensaverScreenOff &&
            !isScreensaverAodActive &&
            screensaverSettings.locationDynamicUpdates &&
            pt != null &&
            LocationTracker.isValidCoordinate(pt.latitude, pt.longitude)
        ) {
            val nowMs = System.currentTimeMillis()
            val movedMetres = lastScreensaverGeocodePoint?.let { GeoUtils.distanceMetres(it, pt) } ?: Double.MAX_VALUE
            val currentName = screensaverResolvedLocationName.ifBlank { vmLocationName }
            val shouldResolve = (currentName.isBlank() || movedMetres >= 120.0) &&
                (nowMs - lastScreensaverGeocodeAttemptMs >= 15_000L)
            if (shouldResolve) {
                lastScreensaverGeocodePoint = pt
                lastScreensaverGeocodeAttemptMs = nowMs
                val resolved = LocationNameResolver.resolveLocationName(
                    context = screensaverContext,
                    latitude = pt.latitude,
                    longitude = pt.longitude
                )
                if (resolved.isNotBlank()) {
                    screensaverResolvedLocationName = resolved
                }
            }
        }
    }

    // Staleness expiry check while screensaver is open so stale GPS speed reverts to "-- km/h" cleanly
    LaunchedEffect(isScreensaverVisible, locationState.lastFixTime, locationState.speed) {
        screensaverClockMs = System.currentTimeMillis()
        if (isScreensaverVisible && locationState.speed != null && locationState.lastFixTime > 0L) {
            val ageMs = (System.currentTimeMillis() - locationState.lastFixTime).coerceAtLeast(0L)
            val remainingMs = (ScreensaverChainageStateHolder.SCREENSAVER_SPEED_STALE_MS - ageMs).coerceAtLeast(0L)
            delay(remainingMs + 50L)
            screensaverClockMs = System.currentTimeMillis()
        }
    }

    val effectiveScreensaverLocationName = screensaverResolvedLocationName.ifBlank { vmLocationName }

    val screensaverDisplayState = remember(
        matchResult,
        activeRoute?.id,
        locationState,
        projectProfile.roadSide,
        effectiveScreensaverLocationName,
        screensaverClockMs
    ) {
        screensaverStateHolder.resolve(
            matchResult = matchResult,
            route = activeRoute,
            hasGpsFix = locationState.point != null,
            projectRoadSide = projectProfile.roadSide,
            locationState = locationState,
            locationName = effectiveScreensaverLocationName,
            nowMs = screensaverClockMs
        )
    }

    val aodNextKmTracker = remember { NextKmStoneTracker() }
    val aodNextKmState = remember(
        isScreensaverAodActive,
        matchResult,
        smartDirectionState,
        activeRoute?.id,
        screensaverDisplayState.reliableSide
    ) {
        if (!isScreensaverAodActive || activeRoute == null || screensaverDisplayState.reliableSide == null) {
            null
        } else {
            aodNextKmTracker.evaluate(
                matchResult = matchResult,
                route = activeRoute,
                detectedSide = screensaverDisplayState.reliableSide,
                isAutoDetectedSide = !smartDirectionState.isManualOverride,
                smartDirectionState = smartDirectionState
            )
        }
    }

    HomeScreensaverCoordinator(
        isBlockingUiOpen = isBlockingScreensaverModal,
        inactivityTimeoutMs = screensaverSettings.effectiveTimeoutMs(),
        isScreensaverVisible = isScreensaverVisible,
        onShowScreensaver = { isScreensaverVisible = true },
        onDismissScreensaver = { isScreensaverVisible = false },
        masterEnabled = screensaverSettings.masterEnabled,
        autoActivateEnabled = screensaverSettings.autoActivateEnabled,
        touchToDismiss = screensaverSettings.touchToDismiss,
        autoRestartTimer = screensaverSettings.autoRestartTimer,
        keepScreenAwake = screensaverSettings.keepScreenAwake,
        isScreenOff = isScreensaverScreenOff,
        isAodActive = isScreensaverAodActive,
        aodDimLevel = screensaverSettings.aodDimLevel,
        onWakeFromScreenOff = { viewModel.setScreensaverScreenOff(false) },
        onExitAod = { viewModel.setScreensaverAodActive(false) },
        screensaverContent = { dismissScreensaver ->
            ChainageScreensaverOverlay(
                displayState = screensaverDisplayState,
                location = locationState.point,
                bearing = locationState.bearing,
                compassHeading = compassState.azimuthDegrees,
                routeData = activeRoute,
                mapThemePreference = mapThemePreference,
                mapTypePreference = mapTypePreference,
                isDarkAppTheme = isDark,
                screensaverSettings = screensaverSettings,
                sensorPostureOverride = activeScreensaverSensorPosture,
                isScreenOff = isScreensaverScreenOff,
                isAodActive = isScreensaverAodActive,
                is24HourFormat = cameraSettings.is24HourFormat,
                nextKmLabel = aodNextKmState?.takeIf { it.isReliable }?.let { "Next KM ${it.nextChainage}" },
                distanceToNextMetres = aodNextKmState?.takeIf { it.isReliable }?.distanceMetres,
                lastKmStoneEvent = lastKmStoneHubEvent,
                isKmStoneMonitoringActive = isKmStoneServiceRunning || kmStoneAlertSettings.enabled,
                mapHeadingState = mapHeadingState,
                onToggleMirrorQuickMode = { enableMirror, currentlyLandscape ->
                    viewModel.toggleScreensaverMirrorQuickMode(
                        enableMirror = enableMirror,
                        isCurrentlyLandscape = currentlyLandscape
                    )
                },
                onToggleOrientationLock = { currentlyLandscape ->
                    viewModel.setScreensaverOrientationLocked(
                        locked = !screensaverSettings.orientationLocked,
                        currentIsLandscape = currentlyLandscape
                    )
                },
                onToggleScreenOff = { screenOff ->
                    viewModel.setScreensaverScreenOff(screenOff)
                },
                onToggleAod = { active ->
                    viewModel.setScreensaverAodActive(active)
                },
                onForceBlackScreenOff = {
                    viewModel.setScreensaverScreenOff(screenOff = true, forcePureBlack = true)
                },
                onDismiss = dismissScreensaver
            )
        }
    ) { interactionModifier ->
        BoxWithConstraints(
            modifier = modifier
                .then(interactionModifier)
                .fillMaxSize()
                .background(pageBgColor)
        ) {
            val maxH = maxHeight
            val isUltraCompact = maxH < 570.dp
            val isCompactHeight = maxH < 670.dp
            val verticalGap = when {
                isUltraCompact -> 4.dp
                isCompactHeight -> 5.5.dp
                else -> 7.dp
            }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = if (isUltraCompact) 4.dp else if (isCompactHeight) 6.dp else 8.dp,
                    bottom = if (isUltraCompact) 2.dp else 4.dp
                ),
            verticalArrangement = Arrangement.spacedBy(verticalGap)
        ) {
            // 1. Top Telemetry Header Row (4 Theme-Matched Cards)
            val speedKmh = if (locationState.speed != null && locationState.speed!! >= 0f) {
                locationState.speed!! * 3.6f
            } else {
                0f
            }
            val satCount = if (locationState.point != null) {
                if (locationState.accuracy <= 5f) 18 else if (locationState.accuracy <= 15f) 14 else 9
            } else {
                0
            }

            TopTelemetryHeaderRow(
                accuracy = locationState.accuracy,
                hasFix = locationState.point != null,
                gpsPulseAlpha = gpsPulseAlpha,
                satellitesCount = satCount,
                speedKmh = speedKmh,
                projectName = projectProfile.highwayId,
                stretchName = projectProfile.packageSection,
                onEditProjectStretch = { showEditProjectStretchDialog = true },
                cardBgColor = cardBgColor,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor,
                isDark = isDark,
                isCompact = isCompactHeight
            )

            // 2. Dominant Central Chainage Display (Borderless Hero Focus)
            val displayChainageText = when {
                matchResult.source == ChainageSource.MANUAL || matchResult.isManual -> matchResult.displayChainage
                activeRoute == null -> "NO KMZ"
                locationState.point == null -> "--+---"
                matchResult.chainage != null -> matchResult.displayChainage
                else -> "--+---"
            }

            val detectedSide = smartDirectionState.confirmedSide
                ?: smartDirectionState.detectionState.shortLabel

            CentralChainageDisplay(
                chainageText = displayChainageText,
                detectedSide = detectedSide,
                confidence = matchResult.confidence,
                offsetMetres = matchResult.offsetMetres,
                hasActiveRoute = activeRoute != null,
                source = matchResult.source,
                rawChainage = matchResult.rawUncorrectedChainage?.format(),
                smartDirectionState = smartDirectionState,
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor,
                isDark = isDark,
                isCompact = isCompactHeight,
                onClick = { showChainageManagementDialog = true },
                onSelectManualSide = { chosenSide ->
                    viewModel.updateSide(chosenSide)
                    viewModel.showToast("Manual direction override: ${chosenSide.label}", com.example.ui.ToastType.INFO)
                },
                onResetSideToAuto = {
                    viewModel.resetSideToAuto()
                    viewModel.showToast("Smart Auto LHS/RHS detection resumed", com.example.ui.ToastType.INFO)
                },
                onToggleSide = {
                    val nextSide = if (detectedSide == "LHS") com.example.model.RoadSide.RHS else com.example.model.RoadSide.LHS
                    viewModel.updateSide(nextSide)
                    viewModel.showToast("Road Side switched to ${nextSide.name}", com.example.ui.ToastType.INFO)
                }
            )

            if (showChainageManagementDialog) {
                val correctionsForRoute by viewModel.activeRouteCorrections.collectAsStateWithLifecycle()
                val isCorrectionsEnabled by viewModel.isRouteCorrectionsEnabled.collectAsStateWithLifecycle()
                ChainageManagementDialog(
                    viewModel = viewModel,
                    matchResult = matchResult,
                    activeRoute = activeRoute,
                    currentGpsPoint = locationState.point,
                    currentGpsAccuracy = locationState.accuracy,
                    correctionPoints = correctionsForRoute,
                    isCorrectionsEnabled = isCorrectionsEnabled,
                    initialTab = 0,
                    onDismiss = { showChainageManagementDialog = false }
                )
            }

            if (showEditProjectStretchDialog) {
                EditProjectStretchDialog(
                    initialProject = projectProfile.highwayId,
                    initialStretch = projectProfile.packageSection,
                    onDismiss = { showEditProjectStretchDialog = false },
                    onSave = { newProj, newStretch ->
                        viewModel.saveProjectProfile(
                            projectProfile.copy(
                                highwayId = newProj,
                                packageSection = newStretch
                            )
                        )
                        viewModel.showToast("Project & Stretch updated", com.example.ui.ToastType.SUCCESS)
                    }
                )
            }

            // 3. Premium Minimalist Borderless Live Weather Card (pauses animations when hidden/screensaver active)
            HomeLiveWeatherCard(
                weatherUiState = weatherUiState,
                resolvedLocationName = vmLocationName,
                is24HourFormat = cameraSettings.is24HourFormat,
                isDark = isDark,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor,
                isCompact = isCompactHeight || isUltraCompact,
                isVisible = !isScreensaverVisible && !isScreensaverScreenOff && !isScreensaverAodActive,
                onRefresh = {
                    viewModel.refreshWeather(force = true)
                }
            )

            // 4. Interactive Smart KMZ Map with Smart Edge Fades (Borderless)
            if (screensaverSettings.miniMapVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .alpha(
                            screensaverSettings.miniMapOpacity.coerceIn(
                                HomeScreensaverSettings.MIN_MINI_MAP_OPACITY,
                                1.0f
                            )
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .smartMapEdgeFade(fadeToColor = pageBgColor, edgeSize = 14.dp)
                        .testTag("home_interactive_map_container")
                ) {
                    InteractiveSmartMap(
                        location = locationState.point,
                        gpsAccuracy = locationState.accuracy,
                        bearing = locationState.bearing,
                        compassHeading = compassState.azimuthDegrees,
                        compassHeadingStr = compassState.displayHeading,
                        routeData = activeRoute,
                        matchedProjectedPoint = matchResult.projectedPoint,
                        onRefreshLocation = {
                            viewModel.refreshLocation()
                            viewModel.showToast("Location refreshed")
                        },
                        mapThemePreference = when (screensaverSettings.miniMapStyle) {
                            MiniMapStyle.DARK -> "dark"
                            MiniMapStyle.LIGHT -> "light"
                            else -> mapThemePreference
                        },
                        mapTypePreference = if (screensaverSettings.miniMapStyle == MiniMapStyle.SATELLITE) {
                            "satellite"
                        } else {
                            mapTypePreference
                        },
                        onToggleMapTheme = {
                            val nextStyle = when (screensaverSettings.miniMapStyle) {
                                MiniMapStyle.DARK -> MiniMapStyle.LIGHT
                                MiniMapStyle.LIGHT -> MiniMapStyle.STANDARD
                                else -> MiniMapStyle.DARK
                            }
                            viewModel.setHomeMiniMapStyle(nextStyle)
                            viewModel.showToast("Map Style: ${nextStyle.label}")
                        },
                        onToggleMapType = {
                            val nextStyle = if (screensaverSettings.miniMapStyle == MiniMapStyle.SATELLITE ||
                                mapTypePreference == "satellite"
                            ) {
                                MiniMapStyle.STANDARD
                            } else {
                                MiniMapStyle.SATELLITE
                            }
                            viewModel.setHomeMiniMapStyle(nextStyle)
                            viewModel.showToast(if (nextStyle == MiniMapStyle.SATELLITE) "Satellite Imagery" else "Standard Map")
                        },
                        sharedZoomLevel = screensaverSettings.miniMapZoomLevel,
                        sharedRecenterEpoch = screensaverSettings.recenterEpoch,
                        onSharedZoomIn = { viewModel.zoomInHomeMiniMap() },
                        onSharedZoomOut = { viewModel.zoomOutHomeMiniMap() },
                        onSharedRecenter = { viewModel.recenterHomeMiniMap() },
                        screensaverSettings = screensaverSettings,
                        mapHeadingState = mapHeadingState,
                        isVisible = !isScreensaverVisible && !isScreensaverScreenOff && !isScreensaverAodActive,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // 5. Prominent Next KM Stone Card
            val nextKmTracker = remember { NextKmStoneTracker() }
            val nextKmState = remember(
                matchResult,
                smartDirectionState,
                activeRoute?.id,
                activeRoute?.markers?.size,
                detectedSide
            ) {
                nextKmTracker.evaluate(
                    matchResult = matchResult,
                    route = activeRoute,
                    detectedSide = detectedSide,
                    isAutoDetectedSide = !smartDirectionState.isManualOverride,
                    smartDirectionState = smartDirectionState
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NextChainageCard(
                    nextChainage = nextKmState.nextChainage,
                    distanceText = nextKmState.distanceText,
                    detectedSide = nextKmState.directionSide,
                    directionArrow = nextKmState.directionArrow,
                    distanceMetres = nextKmState.distanceMetres,
                    intervalMetres = nextKmState.intervalMetres,
                    isReliable = nextKmState.isReliable,
                    cardBgColor = cardBgColor,
                    cardBorderColor = cardBorderColor,
                    mainTextColor = mainTextColor,
                    subTextColor = subTextColor,
                    isDark = isDark,
                    isCompact = isCompactHeight,
                    modifier = Modifier.weight(1f)
                )

                NavigateToChainageQuickButton(
                    onClick = { showNavigateToChainageDialog = true },
                    hasActiveTarget = activeChainageNavTarget != null &&
                        activeChainageNavTarget?.routeId == activeRoute?.id,
                    activeTargetChainageText = activeChainageNavTarget?.destinationChainage?.format(),
                    cardBgColor = cardBgColor,
                    cardBorderColor = cardBorderColor,
                    mainTextColor = mainTextColor,
                    isDark = isDark,
                    isCompact = isCompactHeight
                )
            }

            if (showNavigateToChainageDialog) {
                val correctionsForRoute by viewModel.activeRouteCorrections.collectAsStateWithLifecycle()
                val isCorrectionsEnabled by viewModel.isRouteCorrectionsEnabled.collectAsStateWithLifecycle()
                val voiceAssistantSettings by viewModel.chainageVoiceAssistantSettings.collectAsStateWithLifecycle()
                NavigateToChainageDialog(
                    matchResult = matchResult,
                    activeRoute = activeRoute,
                    currentGpsPoint = locationState.point,
                    correctionPoints = correctionsForRoute,
                    isCorrectionsEnabled = isCorrectionsEnabled,
                    projectRoadSide = projectProfile.roadSide,
                    detectedSideLabel = smartDirectionState.confirmedSide.orEmpty(),
                    nextKmStoneSuggestion = nextKmState.nextChainage.takeIf { nextKmState.isReliable },
                    activeTarget = activeChainageNavTarget?.takeIf { it.routeId == activeRoute?.id },
                    preferredExternalAppId = preferredExternalNavAppId,
                    voiceAssistantSettings = voiceAssistantSettings,
                    onSavePreferredExternalAppId = { viewModel.setPreferredExternalNavAppId(it) },
                    onStartInAppTarget = { target ->
                        activeChainageNavTarget = target
                    },
                    onClearActiveTarget = {
                        activeChainageNavTarget = null
                    },
                    onFeedbackMessage = { msg ->
                        viewModel.showToast(msg, com.example.ui.ToastType.INFO)
                    },
                    isDark = isDark,
                    onDismiss = { showNavigateToChainageDialog = false }
                )
            }

            // 6. Tools & DPR Notes Quick Action Buttons (Flush above bottom bar with zero gap)
            HomeQuickActionButtons(
                onNavigateToTools = onNavigateToTools,
                onNavigateToNotes = onNavigateToNotes,
                onNavigateToSurvey = onNavigateToSurvey,
                onNavigateToCamera = onNavigateToCamera,
                onNavigateToPhotos = onNavigateToPhotos,
                onNavigateToRoutes = onNavigateToRoutes,
                onNavigateToSettings = onNavigateToSettings,
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                isDark = isDark,
                isCompact = isCompactHeight
            )
            }
        }
    }
}

// -------------------------------------------------------------------
// COMPONENT 1: TOP STATUS STRIP (Borderless GPS, Sats, Speed + Compact Project | Stretch)
// -------------------------------------------------------------------
@Composable
fun TopTelemetryHeaderRow(
    accuracy: Float,
    hasFix: Boolean,
    gpsPulseAlpha: Float,
    satellitesCount: Int,
    speedKmh: Float,
    projectName: String,
    stretchName: String,
    onEditProjectStretch: () -> Unit,
    cardBgColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false
) {
    val c = CtecsTheme.colors
    val stripHeight = if (isCompact) 38.dp else 42.dp

    val (targetGpsColor, gpsValueText, gpsStatusSub) = when {
        !hasFix -> Triple(c.accentSecondary, "--", "GPS")
        accuracy <= 5f -> Triple(c.success, String.format(Locale.US, "±%.0fm", accuracy), "Good")
        accuracy <= 15f -> Triple(c.accent, String.format(Locale.US, "±%.0fm", accuracy), "Fair")
        else -> Triple(c.error, String.format(Locale.US, "±%.0fm", accuracy), "Weak")
    }

    val gpsAccentColor by animateColorAsState(
        targetValue = targetGpsColor,
        animationSpec = tween(220),
        label = "gps_accent_color"
    )

    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmh.coerceAtLeast(0f),
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "speed_anim"
    )

    // Compact Project & Stretch string: "MH | Stretch Name", or just "MH" when stretch is empty (no labels, no edit icon)
    val cleanProject = projectName.trim()
    val cleanStretch = stretchName.trim()
    val projectStretchDisplay = remember(cleanProject, cleanStretch) {
        when {
            cleanProject.isNotEmpty() && cleanStretch.isNotEmpty() -> "$cleanProject | $cleanStretch"
            cleanProject.isNotEmpty() -> cleanProject
            cleanStretch.isNotEmpty() -> cleanStretch
            else -> "Project"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Unified Flat Borderless Telemetry Pill: GPS + Sats + Speed
        Row(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight()
                .smartFadeSurface(
                    surfaceColor = cardBgColor,
                    cornerRadius = 12.dp,
                    edgeAlpha = 0.72f,
                    centerAlpha = 1f
                )
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // GPS Accuracy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(gpsAccentColor)
                        .alpha(if (hasFix) gpsPulseAlpha else 0.45f)
                )
                Text(
                    text = gpsValueText,
                    color = mainTextColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = gpsStatusSub,
                    color = gpsAccentColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Satellites
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SatelliteAlt,
                    contentDescription = "Satellites",
                    tint = c.accentSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = if (hasFix) "$satellitesCount" else "--",
                    color = mainTextColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Speed
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Speed",
                    tint = c.success,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = String.format(Locale.US, "%.0f", animatedSpeed),
                    color = mainTextColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "km/h",
                    color = subTextColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Compact Borderless Project & Stretch Pill (No "PROJECT"/"STRETCH" labels, no pencil icon, tap to edit)
        Box(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight()
                .smartFadeSurface(
                    surfaceColor = cardBgColor,
                    cornerRadius = 12.dp,
                    edgeAlpha = 0.72f,
                    centerAlpha = 1f
                )
                .clickable(onClick = onEditProjectStretch)
                .padding(horizontal = 10.dp)
                .testTag("home_card_project"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = projectStretchDisplay,
                color = if (cleanProject.isNotEmpty() || cleanStretch.isNotEmpty()) mainTextColor else subTextColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// -------------------------------------------------------------------
// COMPONENT 2: HERO CHAINAGE DISPLAY (BORDERLESS FOCAL ELEMENT)
// -------------------------------------------------------------------
@Composable
fun CentralChainageDisplay(
    chainageText: String,
    detectedSide: String,
    confidence: MatchConfidence,
    offsetMetres: Double,
    hasActiveRoute: Boolean,
    source: ChainageSource = ChainageSource.GPS,
    rawChainage: String? = null,
    smartDirectionState: SmartDirectionState? = null,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false,
    onClick: () -> Unit = {},
    onToggleSide: (() -> Unit)? = null,
    onSelectManualSide: ((RoadSide) -> Unit)? = null,
    onResetSideToAuto: (() -> Unit)? = null
) {
    val c = CtecsTheme.colors
    var showDirectionMenu by remember { mutableStateOf(false) }
    val kmBadgeBg = c.accent.copy(alpha = if (isDark) 0.16f else 0.12f)
    val kmBadgeText = c.accent

    val isLhs = detectedSide.equals("LHS", ignoreCase = true)
    val isRhs = detectedSide.equals("RHS", ignoreCase = true)

    val sideBadgeBg by animateColorAsState(
        targetValue = when {
            isLhs -> c.success.copy(alpha = if (isDark) 0.16f else 0.12f)
            isRhs -> c.accent.copy(alpha = if (isDark) 0.16f else 0.12f)
            else -> c.secondarySurface
        },
        animationSpec = tween(220),
        label = "side_badge_bg"
    )
    val sideBadgeText by animateColorAsState(
        targetValue = when {
            isLhs -> c.success
            isRhs -> c.accent
            else -> c.textSecondary
        },
        animationSpec = tween(220),
        label = "side_badge_text"
    )

    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)

    val subtleGlowAccent = when (source) {
        ChainageSource.MANUAL -> c.error
        ChainageSource.CUSTOM_CORRECTION -> c.accentSecondary
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .smartFadeSurface(
                surfaceColor = c.secondaryBackground,
                accentGlowColor = subtleGlowAccent,
                cornerRadius = 18.dp,
                edgeAlpha = 0.0f,
                centerAlpha = if (isDark) 0.72f else 0.85f
            )
            .clickable { onClick() }
            .testTag("central_chainage_card")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isCompact) 8.dp else 12.dp,
                    vertical = if (isCompact) 5.dp else 8.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = maxWidth
            val sideLen = detectedSide.length
            val isLongSideText = sideLen > 6 // "SEARCHING", "LOAD ROUTE", etc.
            
            // Dynamic check: on compact widths (< 350dp), high font scale (> 1.25f), or when side badge text is long
            // we place badges in a top micro-header strip so the huge chainage text has 100% full width and never truncates!
            val useStackedBadges = containerWidth < 350.dp || fontScale > 1.25f || (isLongSideText && containerWidth < 390.dp)

            // Dynamic font sizing calculation
            val charCount = chainageText.length.coerceAtLeast(6)
            val digitFontSize: TextUnit = if (useStackedBadges) {
                val availForDigits = (containerWidth - 20.dp).coerceAtLeast(140.dp)
                val calculatedSp = (availForDigits.value / (charCount * 0.58f * fontScale))
                calculatedSp.coerceIn(28f, if (containerWidth < 330.dp) 44f else 54f).sp
            } else {
                val kmBadgeW = if (containerWidth < 360.dp) 38.dp else 44.dp
                val sideBadgeW = if (isLongSideText) 95.dp else if (containerWidth < 360.dp) 68.dp else 80.dp
                val availForDigits = (containerWidth - kmBadgeW - sideBadgeW - 24.dp).coerceAtLeast(110.dp)
                val calculatedSp = (availForDigits.value / (charCount * 0.58f * fontScale))
                val maxCap = when {
                    containerWidth < 360.dp -> 44f
                    containerWidth < 400.dp -> 54f
                    containerWidth < 460.dp -> 64f
                    else -> 72f
                }
                calculatedSp.coerceIn(24f, maxCap).sp
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (useStackedBadges) {
                    // Micro-Header Row with KM Badge on Left and Side Badge on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Borderless KM Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(kmBadgeBg)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "KM",
                                color = kmBadgeText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Right: Smart LHS/RHS Direction Badge (with Auto/Manual selector)
                        Box {
                            if (smartDirectionState != null) {
                                SmartDirectionIndicatorBadge(
                                    directionState = smartDirectionState,
                                    isDark = isDark,
                                    isCompact = true,
                                    onClick = if (onSelectManualSide != null && onResetSideToAuto != null) {
                                        { showDirectionMenu = true }
                                    } else {
                                        onToggleSide
                                    },
                                    testTagName = "home_smart_direction_badge"
                                )
                                if (onSelectManualSide != null && onResetSideToAuto != null) {
                                    SmartDirectionModePickerMenu(
                                        expanded = showDirectionMenu,
                                        directionState = smartDirectionState,
                                        onDismiss = { showDirectionMenu = false },
                                        onSelectAuto = onResetSideToAuto,
                                        onSelectManualSide = onSelectManualSide,
                                        includeExtendedSides = true
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(sideBadgeBg)
                                        .then(if (onToggleSide != null) Modifier.clickable { onToggleSide() } else Modifier)
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(sideBadgeText)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = detectedSide,
                                        color = sideBadgeText,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Dominant Full-Width Live Chainage Digits
                    OdometerChainageRow(
                        chainageText = chainageText,
                        mainTextColor = mainTextColor,
                        digitFontSize = digitFontSize,
                        isDark = isDark
                    )
                } else {
                    // Inline Hero Row: [KM]  173+720  [RHS]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = if (containerWidth < 360.dp) 5.dp else 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(kmBadgeBg)
                                .padding(
                                    horizontal = if (containerWidth < 360.dp) 6.dp else 8.dp,
                                    vertical = if (containerWidth < 360.dp) 3.dp else 4.dp
                                )
                        ) {
                            Text(
                                text = "KM",
                                color = kmBadgeText,
                                fontSize = if (containerWidth < 360.dp) 11.sp else 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        OdometerChainageRow(
                            chainageText = chainageText,
                            mainTextColor = mainTextColor,
                            digitFontSize = digitFontSize,
                            isDark = isDark
                        )

                        Box(modifier = Modifier.padding(start = if (containerWidth < 360.dp) 5.dp else 8.dp)) {
                            if (smartDirectionState != null) {
                                SmartDirectionIndicatorBadge(
                                    directionState = smartDirectionState,
                                    isDark = isDark,
                                    isCompact = containerWidth < 360.dp,
                                    onClick = if (onSelectManualSide != null && onResetSideToAuto != null) {
                                        { showDirectionMenu = true }
                                    } else {
                                        onToggleSide
                                    },
                                    testTagName = "home_smart_direction_badge"
                                )
                                if (onSelectManualSide != null && onResetSideToAuto != null) {
                                    SmartDirectionModePickerMenu(
                                        expanded = showDirectionMenu,
                                        directionState = smartDirectionState,
                                        onDismiss = { showDirectionMenu = false },
                                        onSelectAuto = onResetSideToAuto,
                                        onSelectManualSide = onSelectManualSide,
                                        includeExtendedSides = true
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(sideBadgeBg)
                                        .then(if (onToggleSide != null) Modifier.clickable { onToggleSide() } else Modifier)
                                        .padding(
                                            horizontal = if (containerWidth < 360.dp) 6.dp else 8.dp,
                                            vertical = if (containerWidth < 360.dp) 3.dp else 4.dp
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (containerWidth < 360.dp) 5.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(sideBadgeText)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = detectedSide,
                                        color = sideBadgeText,
                                        fontSize = if (containerWidth < 360.dp) 11.sp else 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 3.dp else 5.dp))

                // Responsive Telemetry Metadata Strip
                val sourceLabel = when (source) {
                    ChainageSource.MANUAL -> "MANUAL"
                    ChainageSource.CUSTOM_CORRECTION -> if (rawChainage != null) "CUSTOM CORRECTION ($rawChainage)" else "CUSTOM CORRECTION"
                    ChainageSource.KMZ -> "KMZ"
                    ChainageSource.GPS -> "GPS"
                }

                val (confText, confCol) = when (confidence) {
                    MatchConfidence.HIGH -> "Good" to c.success
                    MatchConfidence.MEDIUM -> "Moderate" to c.accent
                    MatchConfidence.LOW -> "Poor" to c.error
                    MatchConfidence.NO_MATCH -> "No Fix" to subTextColor
                }

                if (containerWidth < 350.dp || fontScale > 1.25f) {
                    // 2-line compact metadata strip
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ChainageSourceBadge(source = source, isDark = isDark)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sourceLabel,
                            color = subTextColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = String.format(Locale.US, "Offset: %.1fm", offsetMetres),
                            color = mainTextColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = "  •  ",
                            color = subTextColor.copy(alpha = 0.6f),
                            fontSize = 10.5.sp
                        )
                        Text(
                            text = confText,
                            color = confCol,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                } else {
                    // 1-line standard metadata strip
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ChainageSourceBadge(source = source, isDark = isDark)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sourceLabel,
                            color = subTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "  •  ",
                            color = subTextColor.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = String.format(Locale.US, "Offset: %.1fm", offsetMetres),
                            color = mainTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = "  •  ",
                            color = subTextColor.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = confText,
                            color = confCol,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OdometerChainageRow(
    chainageText: String,
    mainTextColor: Color,
    digitFontSize: TextUnit = 58.sp,
    isDark: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val chars = chainageText.toCharArray()
        chars.forEach { ch ->
            OdometerSingleDigit(
                char = ch,
                mainTextColor = mainTextColor,
                digitFontSize = digitFontSize,
                isDark = isDark
            )
        }
    }
}

@Composable
fun OdometerSingleDigit(
    char: Char,
    mainTextColor: Color,
    digitFontSize: TextUnit = 58.sp,
    isDark: Boolean = false
) {
    var prevChar by remember { mutableStateOf(char) }
    var isChanging by remember { mutableStateOf(false) }

    LaunchedEffect(char) {
        if (char != prevChar) {
            isChanging = true
            prevChar = char
            delay(650L)
            isChanging = false
        }
    }

    val activeAccent = CtecsTheme.colors.accent

    val digitColor by animateColorAsState(
        targetValue = if (isChanging) activeAccent else mainTextColor,
        animationSpec = tween(220),
        label = "digit_accent_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val effectiveSpacing = if (digitFontSize < 34.sp) 0.sp else (-0.5).sp

        if (!char.isDigit()) {
            Text(
                text = char.toString(),
                color = if (char == '+') activeAccent else mainTextColor,
                fontSize = digitFontSize,
                lineHeight = digitFontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = effectiveSpacing,
                softWrap = false,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        } else {
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) { h -> h / 2 } + fadeIn(tween(150)))
                        .togetherWith(slideOutVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) { h -> -h / 2 } + fadeOut(tween(130)))
                },
                label = "digit_slide"
            ) { targetChar ->
                Text(
                    text = targetChar.toString(),
                    color = digitColor,
                    fontSize = digitFontSize,
                    lineHeight = digitFontSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = effectiveSpacing,
                    softWrap = false,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }

        AnimatedVisibility(
            visible = isChanging,
            enter = fadeIn(tween(150)) + expandHorizontally(),
            exit = fadeOut(tween(200)) + shrinkHorizontally()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .width(14.dp)
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(activeAccent)
            )
        }
    }
}

// -------------------------------------------------------------------
// EDIT PROJECT & STRETCH MODAL DIALOG (BORDERLESS FLAT)
// -------------------------------------------------------------------
@Composable
fun EditProjectStretchDialog(
    initialProject: String,
    initialStretch: String,
    onDismiss: () -> Unit,
    onSave: (project: String, stretch: String) -> Unit
) {
    var project by remember { mutableStateOf(initialProject) }
    var stretch by remember { mutableStateOf(initialStretch) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val c = CtecsTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = if (c.isDark) c.surface else c.elevatedSurface,
        tonalElevation = 0.dp,
        modifier = Modifier.imePadding(),
        title = {
            Text(
                text = "Project & Stretch",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = c.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Update project and stretch for dashboard header and camera watermark.",
                    fontSize = 12.5.sp,
                    color = c.textSecondary
                )

                OutlinedTextField(
                    value = project,
                    onValueChange = { project = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g. MH, NH-44") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_project_input"),
                    colors = ctecsBorderlessInputColors(
                        containerColor = if (c.isDark) c.secondarySurface else c.surface
                    )
                )

                OutlinedTextField(
                    value = stretch,
                    onValueChange = { stretch = it },
                    label = { Text("Stretch (Optional)") },
                    placeholder = { Text("e.g. Pkg-1, Section-A") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onSave(project.trim(), stretch.trim())
                            onDismiss()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_stretch_input"),
                    colors = ctecsBorderlessInputColors(
                        containerColor = if (c.isDark) c.secondarySurface else c.surface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(project.trim(), stretch.trim())
                    onDismiss()
                },
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.accent,
                    contentColor = c.onAccent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_project_stretch_button")
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = c.textSecondary)
            }
        }
    )
}

// -------------------------------------------------------------------
// COMPONENT 4: PROMINENT FULL-WIDTH NEXT KM STONE CARD
// -------------------------------------------------------------------
@Composable
fun KmStoneIcon(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 36.dp,
    isDark: Boolean = false
) {
    val domeColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB)

    Canvas(
        modifier = modifier
            .size(sizeDp)
            .testTag("km_stone_icon")
    ) {
        val w = size.width
        val h = size.height

        val stoneW = w * 0.70f
        val stoneH = h * 0.90f
        val left = (w - stoneW) / 2f
        val top = (h - stoneH) / 2f
        val right = left + stoneW
        val bottom = top + stoneH
        val archH = stoneW * 0.50f

        val stonePath = Path().apply {
            moveTo(left, bottom)
            lineTo(left, top + archH)
            arcTo(
                rect = Rect(left, top, right, top + archH * 2f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(right, bottom)
            close()
        }

        drawLine(
            color = Color.Black.copy(alpha = 0.20f),
            start = Offset(left - 3.dp.toPx(), bottom),
            end = Offset(right + 3.dp.toPx(), bottom),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawPath(
            path = stonePath,
            color = Color.White
        )

        val archPath = Path().apply {
            moveTo(left, top + archH)
            arcTo(
                rect = Rect(left, top, right, top + archH * 2f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(right, top + archH)
            close()
        }
        drawPath(
            path = archPath,
            color = domeColor
        )

        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(left, top + archH),
            end = Offset(right, top + archH),
            strokeWidth = 1.5.dp.toPx()
        )

        drawPath(
            path = stonePath,
            color = Color(0xFF1E293B),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val line1Y = top + archH + (bottom - (top + archH)) * 0.35f
        val line2Y = top + archH + (bottom - (top + archH)) * 0.68f
        drawLine(
            color = Color(0xFF475569),
            start = Offset(left + stoneW * 0.20f, line1Y),
            end = Offset(right - stoneW * 0.20f, line1Y),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(left + stoneW * 0.30f, line2Y),
            end = Offset(right - stoneW * 0.30f, line2Y),
            strokeWidth = 1.3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Validated KM Stone reference along the active alignment.
 */
data class KmStoneReference(
    val chainageMetres: Long,
    val formattedChainage: String
)

/**
 * Immutable UI state for the Home Page Smart Dynamic Next KM Stone Card.
 */
data class NextKmStoneState(
    val nextChainage: String,
    val nextChainageMetres: Long?,
    val distanceMetres: Long?,
    val distanceText: String,
    val directionSide: String,
    val directionArrow: String,
    val intervalMetres: Long = 1000L,
    val isReliable: Boolean = true
)

/**
 * Stateful, allocation-light tracker for the Home Page Next KM Stone Card.
 *
 * Features:
 * 1. LHS -> Increasing Chainage (next higher KM Stone `> currentChainage`).
 * 2. RHS -> Decreasing Chainage (next lower KM Stone `< currentChainage`).
 * 3. Uses actual KM Stone markers from imported KMZ/KML (`RouteData.markers`) wherever available,
 *    supporting irregular KM Stone intervals (e.g., 850 m or 1,250 m) and chainage equations while
 *    filtering out dense sub-km intermediate ticks (50 m / 100 m / 200 m).
 * 4. Falls back to the next valid 1 km calibrated boundary when KM Stone markers are unavailable.
 * 5. Applies boundary crossing hysteresis (3 m deadband) and auto-side hysteresis to prevent
 *    GPS jitter flicker while immediately responding to genuine reverse travel or user side changes.
 */
class NextKmStoneTracker(
    private val boundaryHysteresisMetres: Long = 3L
) {
    private var cachedRouteId: String? = null
    private var cachedMarkerCount: Int = -1
    private var cachedKmStones: List<KmStoneReference> = emptyList()

    private var confirmedSide: String? = null
    private var pendingSide: String? = null
    private var pendingSideCount: Int = 0

    private var lastChainageMetres: Long? = null
    private var lastTargetStoneMetres: Long? = null
    private var lastPassedStoneMetres: Long? = null
    private var lastIntervalMetres: Long = 1000L

    fun reset() {
        confirmedSide = null
        pendingSide = null
        pendingSideCount = 0
        lastChainageMetres = null
        lastTargetStoneMetres = null
        lastPassedStoneMetres = null
        lastIntervalMetres = 1000L
    }

    fun evaluate(
        matchResult: com.example.model.ChainageMatchResult,
        route: com.example.model.RouteData?,
        detectedSide: String,
        isAutoDetectedSide: Boolean = false,
        smartDirectionState: SmartDirectionState? = matchResult.smartDirectionState
    ): NextKmStoneState {
        val rawSide = smartDirectionState?.confirmedBinarySide
            ?: detectedSide.trim().uppercase(Locale.ROOT)
        val validInputSide = if (rawSide == "LHS" || rawSide == "RHS") rawSide else null
        val fallbackSide = validInputSide
            ?: smartDirectionState?.detectionState?.shortLabel
            ?: confirmedSide
            ?: "---"
        val fallbackArrow = smartDirectionState?.directionArrow?.ifBlank { "•" }
            ?: when (fallbackSide) {
                "LHS" -> "↑"
                "RHS" -> "↓"
                else -> "•"
            }

        if (route == null) {
            return NextKmStoneState(
                nextChainage = "--+---",
                nextChainageMetres = null,
                distanceMetres = null,
                distanceText = "No KMZ",
                directionSide = fallbackSide,
                directionArrow = fallbackArrow,
                isReliable = false
            )
        }

        val currentChainage = matchResult.chainage
        if (currentChainage == null || matchResult.confidence == com.example.model.MatchConfidence.NO_MATCH) {
            return NextKmStoneState(
                nextChainage = "--+---",
                nextChainageMetres = null,
                distanceMetres = null,
                distanceText = "Waiting GPS",
                directionSide = fallbackSide,
                directionArrow = fallbackArrow,
                isReliable = false
            )
        }

        // Guard against unreliable or severely off-corridor matches
        val isUnreliableMatch = matchResult.isUncertain ||
            (matchResult.confidence == com.example.model.MatchConfidence.LOW && matchResult.offsetMetres > 120.0)
        if (isUnreliableMatch) {
            return NextKmStoneState(
                nextChainage = "--+---",
                nextChainageMetres = null,
                distanceMetres = null,
                distanceText = "Low GPS Accuracy",
                directionSide = fallbackSide,
                directionArrow = fallbackArrow,
                isReliable = false
            )
        }

        // When authoritative SmartDirectionState is present and direction is not confirmed,
        // never guess LHS/RHS or display a marker behind the vehicle.
        if (smartDirectionState != null && !smartDirectionState.hasConfirmedDirection) {
            val statusLabel = when (smartDirectionState.detectionState) {
                DirectionDetectionState.GPS_SIGNAL_WEAK -> "Weak GPS"
                DirectionDetectionState.DIRECTION_CHANGING -> "Direction Changing"
                DirectionDetectionState.STATIONARY -> "Stationary"
                DirectionDetectionState.ALIGNMENT_UNAVAILABLE -> "No Alignment"
                else -> "Direction Uncertain"
            }
            return NextKmStoneState(
                nextChainage = "--+---",
                nextChainageMetres = null,
                distanceMetres = null,
                distanceText = statusLabel,
                directionSide = smartDirectionState.detectionState.shortLabel,
                directionArrow = "•",
                isReliable = false
            )
        }

        if (validInputSide == null) {
            return NextKmStoneState(
                nextChainage = "--+---",
                nextChainageMetres = null,
                distanceMetres = null,
                distanceText = "Side Unavailable",
                directionSide = "---",
                directionArrow = "•",
                isReliable = false
            )
        }

        val curM = currentChainage.metres.coerceAtLeast(0L)
        val prevM = lastChainageMetres

        // Resolve effective side: when SmartDirectionState is authoritative, trust its confirmed
        // direction directly (already multi-sample hysteresis filtered); otherwise keep legacy hysteresis.
        val prevConfirmedSide = confirmedSide
        val effectiveSide = if (smartDirectionState != null) {
            confirmedSide = validInputSide
            pendingSide = null
            pendingSideCount = 0
            validInputSide
        } else if (!isAutoDetectedSide || prevConfirmedSide == null) {
            confirmedSide = validInputSide
            pendingSide = null
            pendingSideCount = 0
            validInputSide
        } else if (validInputSide == prevConfirmedSide) {
            pendingSide = null
            pendingSideCount = 0
            prevConfirmedSide
        } else {
            val movedDistance = if (prevM != null) kotlin.math.abs(curM - prevM) else Long.MAX_VALUE
            if (movedDistance > boundaryHysteresisMetres) {
                confirmedSide = validInputSide
                pendingSide = null
                pendingSideCount = 0
                validInputSide
            } else {
                if (pendingSide == validInputSide) {
                    pendingSideCount += 1
                } else {
                    pendingSide = validInputSide
                    pendingSideCount = 1
                }
                if (pendingSideCount >= 2) {
                    confirmedSide = validInputSide
                    pendingSide = null
                    pendingSideCount = 0
                    validInputSide
                } else {
                    prevConfirmedSide
                }
            }
        }

        // Decouple chainage progression (increasing vs decreasing) from hardcoded LHS/RHS assumptions
        val isIncreasing = smartDirectionState?.isIncreasingChainage ?: (effectiveSide == "LHS")

        val sideChanged = prevConfirmedSide != null && prevConfirmedSide != effectiveSide
        if (sideChanged) {
            lastTargetStoneMetres = null
            lastPassedStoneMetres = null
        }

        val kmStones = getOrExtractKmStones(route)
        val candidate = resolveCandidateStone(curM, isIncreasing, kmStones)

        var targetStoneM = candidate.targetMetres
        var passedStoneM = candidate.passedMetres
        var intervalM = candidate.intervalMetres

        // Boundary hysteresis: when the user has just crossed a KM Stone in the travel direction,
        // prevent 1-3 m backwards GPS jitter from flickering back to the just-passed stone.
        val prevTargetM = lastTargetStoneMetres
        val prevPassedM = lastPassedStoneMetres
        if (!sideChanged && prevTargetM != null && prevPassedM != null && targetStoneM != prevTargetM) {
            if (isIncreasing) {
                if (targetStoneM == prevPassedM && curM < prevPassedM && (prevPassedM - curM) <= boundaryHysteresisMetres) {
                    targetStoneM = prevTargetM
                    passedStoneM = prevPassedM
                    intervalM = lastIntervalMetres
                }
            } else {
                if (targetStoneM == prevPassedM && curM > prevPassedM && (curM - prevPassedM) <= boundaryHysteresisMetres) {
                    targetStoneM = prevTargetM
                    passedStoneM = prevPassedM
                    intervalM = lastIntervalMetres
                }
            }
        }

        lastChainageMetres = curM
        lastTargetStoneMetres = targetStoneM
        lastPassedStoneMetres = passedStoneM
        lastIntervalMetres = intervalM

        val rawDistanceMetres = if (isIncreasing) {
            (targetStoneM - curM).coerceAtLeast(0L)
        } else {
            (curM - targetStoneM).coerceAtLeast(0L)
        }

        // If holding across a tiny 1-3 m boundary hysteresis jitter, clamp displayed distance to the interval
        val effectiveDistanceMetres = if (targetStoneM == prevTargetM && prevPassedM != null &&
            ((isIncreasing && curM < prevPassedM) || (!isIncreasing && curM > prevPassedM))
        ) {
            rawDistanceMetres.coerceAtMost(intervalM)
        } else {
            rawDistanceMetres
        }

        val nextChainageFormatted = com.example.model.Chainage.fromMetres(targetStoneM).format()
        val distanceText = formatDistanceAway(effectiveDistanceMetres)
        val directionArrow = if (isIncreasing) "↑" else "↓"

        return NextKmStoneState(
            nextChainage = nextChainageFormatted,
            nextChainageMetres = targetStoneM,
            distanceMetres = effectiveDistanceMetres,
            distanceText = distanceText,
            directionSide = effectiveSide,
            directionArrow = directionArrow,
            intervalMetres = intervalM.coerceAtLeast(100L),
            isReliable = true
        )
    }

    private data class CandidateResolution(
        val targetMetres: Long,
        val passedMetres: Long,
        val intervalMetres: Long
    )

    private fun resolveCandidateStone(
        curM: Long,
        isIncreasing: Boolean,
        kmStones: List<KmStoneReference>
    ): CandidateResolution {
        if (isIncreasing) {
            // Increasing Chainage: find next higher KM stone (> curM)
            val nextFromMarkers = kmStones.firstOrNull { it.chainageMetres > curM }
            val prevFromMarkers = kmStones.lastOrNull { it.chainageMetres <= curM }
            val fallbackNext = ((curM / 1000L) + 1L) * 1000L
            val fallbackPrev = (curM / 1000L) * 1000L

            // Use KMZ marker stone if available and within a valid single-stone span (<= 2500 m)
            if (nextFromMarkers != null && (nextFromMarkers.chainageMetres - curM) <= 2500L) {
                val target = nextFromMarkers.chainageMetres
                val passed = prevFromMarkers?.chainageMetres ?: fallbackPrev
                val span = (target - passed).takeIf { it in 200L..3000L } ?: 1000L
                return CandidateResolution(target, passed, span)
            }
            return CandidateResolution(fallbackNext, fallbackPrev, 1000L)
        } else {
            // Decreasing Chainage: find next lower KM stone (< curM)
            val nextFromMarkers = kmStones.lastOrNull { it.chainageMetres < curM }
            val prevFromMarkers = kmStones.firstOrNull { it.chainageMetres >= curM }
            val fallbackNext = if (curM == 0L) {
                0L
            } else if (curM % 1000L == 0L) {
                (curM - 1000L).coerceAtLeast(0L)
            } else {
                (curM / 1000L) * 1000L
            }
            val fallbackPrev = if (curM % 1000L == 0L) curM else ((curM / 1000L) + 1L) * 1000L

            if (nextFromMarkers != null && (curM - nextFromMarkers.chainageMetres) <= 2500L) {
                val target = nextFromMarkers.chainageMetres
                val passed = prevFromMarkers?.chainageMetres ?: fallbackPrev
                val span = (passed - target).takeIf { it in 200L..3000L } ?: 1000L
                return CandidateResolution(target, passed, span)
            }
            return CandidateResolution(fallbackNext, fallbackPrev, 1000L)
        }
    }

    private fun getOrExtractKmStones(route: com.example.model.RouteData): List<KmStoneReference> {
        if (cachedRouteId == route.id && cachedMarkerCount == route.markers.size) {
            return cachedKmStones
        }
        val extracted = extractKmStoneReferences(route.markers)
        cachedRouteId = route.id
        cachedMarkerCount = route.markers.size
        cachedKmStones = extracted
        return extracted
    }

    companion object {
        /**
         * Formats remaining distance in metres or kilometres according to CTECS specification:
         * - `<= 1000 m`: `"950 m Away"`, `"50 m Away"`
         * - `> 1000 m`: `"1.25 km Away"`
         */
        fun formatDistanceAway(distanceMetres: Long): String {
            val clamped = distanceMetres.coerceAtLeast(0L)
            return if (clamped <= 1000L) {
                "$clamped m Away"
            } else {
                String.format(Locale.US, "%.2f km Away", clamped / 1000.0)
            }
        }

        /**
         * Extracts verified KM Stone references from route markers while filtering out dense
         * intermediate sub-kilometre ticks (e.g. 50 m / 100 m / 200 m).
         * Preserves irregular KM Stone intervals (e.g. 200+850, 202+100) and chainage equations.
         */
        fun extractKmStoneReferences(markers: List<com.example.model.RouteMarker>): List<KmStoneReference> {
            if (markers.isEmpty()) return emptyList()
            val sortedUnique = markers
                .filter { it.chainage.metres >= 0L }
                .sortedBy { it.chainage.metres }
                .distinctBy { it.chainage.metres }

            if (sortedUnique.isEmpty()) return emptyList()

            return sortedUnique.mapIndexedNotNull { index, marker ->
                val metres = marker.chainage.metres
                val isExactKmBoundary = metres % 1000L == 0L

                val folderLower = marker.folderName.lowercase(Locale.ROOT)
                val labelLower = marker.label.trim().lowercase(Locale.ROOT)
                val descLower = marker.description.trim().lowercase(Locale.ROOT)
                val isExplicitKmStone = folderLower.contains("km stone") ||
                    folderLower.contains("km_stone") ||
                    folderLower.contains("milestone") ||
                    folderLower.contains("kilometre") ||
                    folderLower.contains("kilometer") ||
                    folderLower.contains("equation") ||
                    labelLower.contains("km stone") ||
                    labelLower.contains("milestone") ||
                    labelLower.contains("eq") ||
                    descLower.contains("km stone") ||
                    descLower.contains("milestone")

                val prevGap = if (index > 0) metres - sortedUnique[index - 1].chainage.metres else Long.MAX_VALUE
                val nextGap = if (index < sortedUnique.lastIndex) sortedUnique[index + 1].chainage.metres - metres else Long.MAX_VALUE
                // If adjacent markers are spaced >= 300 m apart, the KMZ markers represent KM stones
                // (including irregular KM stone intervals like 850 m or 1,250 m) rather than 50 m/100 m ticks.
                val isIrregularKmStoneSpacing = prevGap >= 300L && nextGap >= 300L

                if (isExactKmBoundary || isExplicitKmStone || isIrregularKmStoneSpacing) {
                    KmStoneReference(
                        chainageMetres = metres,
                        formattedChainage = marker.chainage.format()
                    )
                } else {
                    null
                }
            }
        }
    }
}

@Composable
fun NextChainageCard(
    nextChainage: String,
    distanceText: String,
    detectedSide: String = "RHS",
    directionArrow: String = if (detectedSide.equals("LHS", ignoreCase = true)) "↑" else "↓",
    distanceMetres: Long? = null,
    intervalMetres: Long = 1000L,
    isReliable: Boolean = !nextChainage.contains("-"),
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val cleanDistance = distanceText.replace("Exact ", "", ignoreCase = true).trim()
    val resolvedMetres = remember(distanceMetres, cleanDistance) {
        distanceMetres?.toInt()
            ?: cleanDistance.substringBefore(" ").filter { it.isDigit() }.toIntOrNull()
            ?: 500
    }
    val animatedMetres by animateIntAsState(
        targetValue = resolvedMetres.coerceAtLeast(0),
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "km_stone_distance_metres"
    )
    val animatedDistanceLabel = remember(isReliable, distanceMetres, animatedMetres, cleanDistance) {
        if (!isReliable || distanceMetres == null) {
            cleanDistance
        } else if (distanceMetres <= 1000L) {
            "$animatedMetres m Away"
        } else {
            NextKmStoneTracker.formatDistanceAway(distanceMetres)
        }
    }

    val safeSpan = intervalMetres.coerceAtLeast(100L).toFloat()
    val targetProgress = remember(isReliable, resolvedMetres, safeSpan) {
        if (!isReliable) {
            0.0f
        } else {
            ((safeSpan - resolvedMetres.toFloat()) / safeSpan).coerceIn(0.05f, 1.0f)
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "km_stone_progress"
    )

    val c = CtecsTheme.colors
    val isLhs = detectedSide.equals("LHS", ignoreCase = true)
    val labelColor = c.accent
    val iconBoxBg = c.accent.copy(alpha = if (isDark) 0.16f else 0.12f)
    val sideBadgeBg = when {
        !isReliable -> c.secondarySurface
        isLhs -> c.success.copy(alpha = if (isDark) 0.16f else 0.12f)
        else -> c.accent.copy(alpha = if (isDark) 0.16f else 0.12f)
    }
    val sideBadgeText = when {
        !isReliable -> subTextColor
        isLhs -> c.success
        else -> c.accent
    }
    val distancePillBg = if (isReliable) c.accent else c.secondarySurface
    val distancePillText = if (isReliable) c.onAccent else c.textSecondary

    val infiniteTransition = rememberInfiniteTransition(label = "km_badge_pulse")
    val badgePulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isReliable) 0.90f else 0.96f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgePulse"
    )

    Box(
        modifier = modifier
            .height(if (isCompact) 50.dp else 54.dp)
            .smartFadeSurface(
                surfaceColor = cardBgColor,
                cornerRadius = 14.dp,
                edgeAlpha = 0.78f,
                centerAlpha = 1f
            )
            .testTag("next_chainage_card")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTightRow = maxWidth < 305.dp
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isTightRow) 9.dp else 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompact || isTightRow) 32.dp else 36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconBoxBg),
                        contentAlignment = Alignment.Center
                    ) {
                        KmStoneIcon(sizeDp = if (isCompact || isTightRow) 22.dp else 26.dp, isDark = isDark)
                    }

                    Spacer(modifier = Modifier.width(if (isTightRow) 7.dp else 10.dp))

                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = "NEXT KM STONE",
                            color = labelColor,
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedContent(
                                targetState = nextChainage,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(220)) togetherWith
                                        fadeOut(animationSpec = tween(160))
                                },
                                label = "next_km_chainage_anim"
                            ) { targetChainage ->
                                Text(
                                    text = targetChainage,
                                    color = mainTextColor,
                                    fontSize = if (isCompact) 15.5.sp else 17.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.2.sp,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("next_km_stone_value")
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(sideBadgeBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("next_km_stone_side")
                            ) {
                                Text(
                                    text = "$directionArrow ${detectedSide.uppercase(Locale.ROOT)}".trim(),
                                    color = sideBadgeText,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Distance Away Flat Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(distancePillBg.copy(alpha = badgePulseAlpha))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("next_km_stone_distance")
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(distancePillText)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = animatedDistanceLabel,
                        color = distancePillText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Sleek Bottom Progress Bar toward Next KM Stone
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(c.secondarySurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(2.dp)
                        .background(c.accent)
                )
            }
        }
    }
}

// -------------------------------------------------------------------
// COMPONENT 5: TOOLS, SURVEY & DPR NOTES QUICK ACTION BUTTONS (FLAT BORDERLESS)
// -------------------------------------------------------------------
@Composable
fun HomeQuickActionButtons(
    onNavigateToTools: () -> Unit,
    onNavigateToNotes: () -> Unit,
    onNavigateToSurvey: () -> Unit = {},
    onNavigateToCamera: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToSettings: () -> Unit,
    cardBgColor: Color,
    cardBorderColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false
) {
    val c = CtecsTheme.colors
    val btnHeight = if (isCompact) 42.dp else 46.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeShortcutPill(
            label = "Survey",
            icon = Icons.Default.Engineering,
            accent = c.warning,
            surfaceColor = cardBgColor,
            titleColor = c.textPrimary,
            height = btnHeight,
            tag = "home_btn_survey",
            onClick = onNavigateToSurvey,
            modifier = Modifier.weight(1f)
        )
        HomeShortcutPill(
            label = "Tools",
            icon = Icons.Default.Build,
            accent = c.accent,
            surfaceColor = cardBgColor,
            titleColor = c.textPrimary,
            height = btnHeight,
            tag = "home_btn_tools",
            onClick = onNavigateToTools,
            modifier = Modifier.weight(1f)
        )
        HomeShortcutPill(
            label = "Notes",
            icon = Icons.Default.Description,
            accent = c.accentSecondary,
            surfaceColor = cardBgColor,
            titleColor = c.textPrimary,
            height = btnHeight,
            tag = "home_btn_notes",
            onClick = onNavigateToNotes,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeShortcutPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    surfaceColor: Color,
    titleColor: Color,
    height: Dp,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(height)
            .smartFadeSurface(
                surfaceColor = surfaceColor,
                cornerRadius = 14.dp,
                edgeAlpha = 0.78f,
                centerAlpha = 1f
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------------
// COMPONENT 6: INTERACTIVE SMART KMZ MAP (OPEN STREET MAP INTEGRATED)
// -------------------------------------------------------------------
@Composable
fun InteractiveSmartMap(
    location: GeoPoint?,
    gpsAccuracy: Float,
    bearing: Float?,
    compassHeading: Float,
    compassHeadingStr: String = "359° N",
    routeData: RouteData?,
    matchedProjectedPoint: GeoPoint?,
    onRefreshLocation: (() -> Unit)? = null,
    mapThemePreference: String = "system",
    mapTypePreference: String = "standard",
    onToggleMapTheme: (() -> Unit)? = null,
    onToggleMapType: (() -> Unit)? = null,
    sharedZoomLevel: Float? = null,
    sharedRecenterEpoch: Long = 0L,
    onSharedZoomIn: (() -> Unit)? = null,
    onSharedZoomOut: (() -> Unit)? = null,
    onSharedRecenter: (() -> Unit)? = null,
    screensaverSettings: HomeScreensaverSettings = HomeScreensaverSettings(),
    mapHeadingState: MapHeadingState? = null,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileUpdate by OsmTileManager.tileUpdateEvent.collectAsStateWithLifecycle()
    val isTileLoading by OsmTileManager.isLoading.collectAsStateWithLifecycle()
    val isAppDark = com.example.ui.theme.isAppDarkTheme()
    val isDark = com.example.ui.map.MapThemeConfig.resolveIsDark(mapThemePreference, isAppDark)
    val mapTheme = com.example.ui.map.rememberMapThemeColors(isDark)

    var exploredCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var continuousZoom by remember {
        mutableFloatStateOf(
            (sharedZoomLevel ?: screensaverSettings.miniMapZoomLevel).coerceIn(
                MiniMapViewportMath.MIN_ZOOM,
                MiniMapViewportMath.MAX_ZOOM
            )
        )
    }
    var isFollowingUser by remember { mutableStateOf(true) }

    LaunchedEffect(sharedZoomLevel) {
        if (sharedZoomLevel != null) {
            continuousZoom = sharedZoomLevel.coerceIn(
                MiniMapViewportMath.MIN_ZOOM,
                MiniMapViewportMath.MAX_ZOOM
            )
        }
    }

    LaunchedEffect(sharedRecenterEpoch) {
        if (sharedRecenterEpoch > 0L) {
            isFollowingUser = true
            exploredCenter = null
        }
    }

    LaunchedEffect(location, isFollowingUser) {
        if (isFollowingUser) {
            exploredCenter = null
        }
    }

    // Pause pulse animation when Home map is covered (e.g. screensaver / AOD) to save battery
    val pulseAlpha = if (isVisible) {
        val infiniteTransition = rememberInfiniteTransition(label = "map_pulse")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0.22f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(1350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        anim
    } else {
        0.35f
    }

    val (baseZoom, fractionalScale) = MiniMapViewportMath.splitContinuousZoom(continuousZoom)
    val viewRangeMetres = remember(continuousZoom) {
        OsmTileManager.viewRangeMetresForZoom(continuousZoom)
    }

    val controlSurfaceColor = if (mapTheme.isDark) Color(0xEB0D111A) else Color(0xF5FFFFFF)
    val controlBorderColor = if (mapTheme.isDark) Color(0xFF171E2B) else Color(0xFFEAF0F7)
    val controlIconColor = if (mapTheme.isDark) Color(0xFFFFFFFF) else Color(0xFF101828)
    val activeControlBg = if (mapTheme.isDark) Color(0xFF1685FF) else Color(0xFF1677F2)

    // Smooth shortest-path angular unwrap for compass instrument bezel
    var unwrappedHeading by remember { mutableFloatStateOf(compassHeading) }
    LaunchedEffect(compassHeading) {
        unwrappedHeading = MiniMapViewportMath.unwrapShortestAngle(unwrappedHeading, compassHeading)
    }
    val animatedCompassHeading by animateFloatAsState(
        targetValue = unwrappedHeading,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "map_compass_rotation"
    )

    // Centralized Direction-Up map orientation (retains last reliable heading when stationary; never resets to North-Up)
    val anchorForOrientation = location ?: routeData?.bounds?.center ?: GeoPoint(28.6139, 77.2090)
    val effectiveMapBearing = remember(anchorForOrientation, mapHeadingState?.headingDegrees, bearing, routeData?.id) {
        mapHeadingState?.headingDegrees
            ?: MiniMapRenderer.resolveEffectiveBearing(anchorForOrientation, bearing, routeData)
    }
    val rawTargetMapRotation = remember(screensaverSettings.miniMapOrientation, effectiveMapBearing) {
        MiniMapViewportMath.resolveDirectionUpRotation(
            mode = screensaverSettings.miniMapOrientation,
            headingDegrees = effectiveMapBearing
        )
    }
    var unwrappedMapRotation by remember { mutableFloatStateOf(rawTargetMapRotation) }
    LaunchedEffect(rawTargetMapRotation) {
        unwrappedMapRotation = MiniMapViewportMath.unwrapShortestAngle(unwrappedMapRotation, rawTargetMapRotation)
    }
    val animatedMapRotation by animateFloatAsState(
        targetValue = unwrappedMapRotation,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "home_shared_map_rotation"
    )
    val isDirectionUpActive = screensaverSettings.miniMapOrientation != MiniMapOrientationMode.NORTH_UP &&
        effectiveMapBearing != null

    BoxWithConstraints(modifier = modifier) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val gpsOrRouteCenter = location ?: routeData?.bounds?.center ?: GeoPoint(28.6139, 77.2090)
        val cameraCenter = if (isFollowingUser) {
            MiniMapViewportMath.computeFollowCameraCenter(
                vehicleGeo = gpsOrRouteCenter,
                continuousZoom = continuousZoom,
                viewportHeightPx = viewportHeightPx,
                mapRotationDeg = animatedMapRotation,
                isDirectionUpActive = isDirectionUpActive
            )
        } else {
            exploredCenter ?: gpsOrRouteCenter
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .miniMapNavigationGestures(enabled = true) { centroid, pan, zoom ->
                    val startCenter = if (isFollowingUser) cameraCenter else (exploredCenter ?: cameraCenter)
                    isFollowingUser = false
                    val (nextCenter, nextZoom) = MiniMapViewportMath.applyFocalPinchAndPan(
                        currentCenter = startCenter,
                        currentZoom = continuousZoom,
                        focalX = centroid.x,
                        focalY = centroid.y,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx,
                        panX = pan.x,
                        panY = pan.y,
                        zoomChange = zoom,
                        mapRotationDeg = animatedMapRotation
                    )
                    exploredCenter = nextCenter
                    continuousZoom = nextZoom
                }
        ) {
            val _tick = tileUpdate
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            val diagPx = MiniMapViewportMath.computeDiagonalRenderSizePx(
                widthPx = w,
                heightPx = h,
                fractionalScale = fractionalScale,
                maxPx = 1024
            )

            var hasOsmTiles = false
            val osmBmp = if (isVisible) {
                OsmTileManager.renderMapBitmap(
                    context = context,
                    center = cameraCenter,
                    widthPx = diagPx,
                    heightPx = diagPx,
                    zoom = baseZoom,
                    panOffsetX = 0f,
                    panOffsetY = 0f,
                    darkTheme = mapTheme.isDark,
                    mapType = mapTypePreference
                )
            } else {
                null
            }

            drawRect(color = mapTheme.mapBackground)

            rotate(degrees = animatedMapRotation, pivot = Offset(cx, cy)) {
                if (osmBmp != null) {
                    scale(scale = fractionalScale, pivot = Offset(cx, cy)) {
                        drawImage(
                            image = osmBmp.asImageBitmap(),
                            dstOffset = IntOffset(
                                x = (cx - diagPx / 2f).toInt(),
                                y = (cy - diagPx / 2f).toInt()
                            ),
                            dstSize = IntSize(diagPx, diagPx)
                        )
                    }
                    hasOsmTiles = true
                }

                if (!hasOsmTiles) {
                    val gridSpacing = 72.dp.toPx()
                    val extent = maxOf(w, h)
                    var g = -extent
                    while (g <= extent * 1.5f) {
                        drawLine(
                            color = mapTheme.gridLineColor,
                            start = Offset(cx + g, cy - extent),
                            end = Offset(cx + g, cy + extent),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = mapTheme.gridLineColor,
                            start = Offset(cx - extent, cy + g),
                            end = Offset(cx + extent, cy + g),
                            strokeWidth = 1f
                        )
                        g += gridSpacing
                    }
                }

                fun toScreen(pt: GeoPoint): Offset {
                    return MiniMapViewportMath.geoToUnrotatedScreen(
                        pt = pt,
                        center = cameraCenter,
                        viewportWidthPx = w,
                        viewportHeightPx = h,
                        continuousZoom = continuousZoom
                    )
                }

                // High-Contrast Highway Route Polylines
                if (screensaverSettings.miniMapShowRouteAlignment &&
                    routeData != null &&
                    routeData.alignments.isNotEmpty()
                ) {
                    for (align in routeData.alignments) {
                        if (align.points.size < 2) continue
                        val path = Path()
                        var started = false
                        for (pt in align.points) {
                            val off = toScreen(pt)
                            if (!started) {
                                path.moveTo(off.x, off.y)
                                started = true
                            } else {
                                path.lineTo(off.x, off.y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = Color(0xFF2563EB).copy(alpha = 0.22f),
                            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = path,
                            color = if (mapTheme.isDark) Color(0xFF0F172A) else Color(0xFF1E3A8A),
                            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = path,
                            color = if (mapTheme.isDark) Color(0xFF38BDF8) else Color(0xFF3B82F6),
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                // Route Chainage Markers
                if (screensaverSettings.miniMapShowRouteAlignment && routeData != null) {
                    val boundPad = maxOf(w, h) * 0.45f
                    for (marker in routeData.markers) {
                        val mOff = toScreen(marker.point)
                        if (mOff.x in -boundPad..(w + boundPad) && mOff.y in -boundPad..(h + boundPad)) {
                            drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = mOff)
                            drawCircle(
                                color = Color(0xFF2563EB),
                                radius = 4.dp.toPx(),
                                center = mOff
                            )
                        }
                    }
                }

                // Live GPS Location Marker synchronized with shared marker style & filtered heading
                if (location != null) {
                    val userOff = toScreen(location)

                    // GPS Accuracy circle + pulsing halo
                    val accHaloPx = if (gpsAccuracy > 0f) {
                        (gpsAccuracy / (viewRangeMetres / (w / 2f)).coerceAtLeast(0.5)).toFloat()
                            .coerceIn(18.dp.toPx(), 64.dp.toPx())
                    } else {
                        24.dp.toPx()
                    }
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.16f),
                        radius = accHaloPx,
                        center = userOff
                    )
                    drawCircle(
                        color = Color(0xFFEF4444).copy(alpha = pulseAlpha * 0.35f),
                        radius = 22.dp.toPx(),
                        center = userOff
                    )

                    val activeHeading = effectiveMapBearing
                    when (screensaverSettings.miniMapMarkerStyle) {
                        HudMapMarkerStyle.NAVIGATION_ARROW -> {
                            if (activeHeading != null) {
                                rotate(degrees = activeHeading, pivot = userOff) {
                                    val conePath = Path().apply {
                                        moveTo(userOff.x, userOff.y - 19.dp.toPx())
                                        lineTo(userOff.x - 8.5.dp.toPx(), userOff.y + 7.dp.toPx())
                                        lineTo(userOff.x, userOff.y + 2.dp.toPx())
                                        lineTo(userOff.x + 8.5.dp.toPx(), userOff.y + 7.dp.toPx())
                                        close()
                                    }
                                    drawPath(path = conePath, color = Color(0xFFEF4444))
                                }
                            }
                            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = userOff)
                            drawCircle(color = Color(0xFFEF4444), radius = 4.8.dp.toPx(), center = userOff)
                        }
                        HudMapMarkerStyle.PULSE_DOT -> {
                            drawCircle(color = Color.White, radius = 8.dp.toPx(), center = userOff)
                            drawCircle(color = Color(0xFF38BDF8), radius = 5.5.dp.toPx(), center = userOff)
                        }
                        HudMapMarkerStyle.CLASSIC_PIN -> {
                            drawCircle(color = Color.White, radius = 8.5.dp.toPx(), center = userOff)
                            drawCircle(color = Color(0xFFEF4444), radius = 5.8.dp.toPx(), center = userOff)
                        }
                    }
                }
            }
        }

        // Heading unavailable indicator (shown ONLY when no reliable heading exists yet)
        if (mapHeadingState?.isDirectionUnavailable == true &&
            effectiveMapBearing == null &&
            location != null &&
            screensaverSettings.miniMapOrientation != MiniMapOrientationMode.NORTH_UP
        ) {
            MiniMapHeadingUnavailableBadge(
                isDark = mapTheme.isDark,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp, start = 10.dp)
            )
        }

        // Tile / Satellite Loading Banner
        AnimatedVisibility(
            visible = isTileLoading,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = controlSurfaceColor,
                shadowElevation = 0.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.6.dp,
                        color = activeControlBg
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (mapTypePreference.equals("satellite", ignoreCase = true)) "Loading Satellite..." else "Loading Map...",
                        color = controlIconColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Map Attribution Badge (Bottom-Left, Borderless)
        Surface(
            shape = RoundedCornerShape(topEnd = 10.dp),
            color = controlSurfaceColor.copy(alpha = 0.86f),
            shadowElevation = 0.dp,
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = if (mapTypePreference.equals("satellite", ignoreCase = true)) OsmTileManager.ATTRIBUTION_SATELLITE else OsmTileManager.ATTRIBUTION_STANDARD,
                color = if (mapTheme.isDark) Color(0xFFA1AAB9) else Color(0xFF526174),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        // Left Map Action Controls (Flat Borderless Circles)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Map Type Switcher (Standard <-> Satellite)
            val isSatellite = mapTypePreference.equals("satellite", ignoreCase = true)
            Surface(
                shape = CircleShape,
                color = if (isSatellite) activeControlBg else controlSurfaceColor,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onToggleMapType?.invoke() }
                    .testTag("map_type_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SatelliteAlt,
                        contentDescription = "Map Type",
                        tint = if (isSatellite) Color.White else controlIconColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 2. Map Theme Switcher
            val themeIcon = when (mapThemePreference.lowercase(Locale.ROOT)) {
                "light" -> Icons.Default.LightMode
                "dark" -> Icons.Default.DarkMode
                else -> Icons.Default.Palette
            }
            Surface(
                shape = CircleShape,
                color = controlSurfaceColor,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onToggleMapTheme?.invoke() }
                    .testTag("map_theme_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = themeIcon,
                        contentDescription = "Map Theme",
                        tint = controlIconColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 3. Refresh GPS Location
            Surface(
                shape = CircleShape,
                color = controlSurfaceColor,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onRefreshLocation?.invoke() }
                    .testTag("map_refresh_location_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Location",
                        tint = activeControlBg,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 4. Re-center / Follow User Location
            Surface(
                shape = CircleShape,
                color = if (isFollowingUser) activeControlBg else controlSurfaceColor,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        isFollowingUser = true
                        exploredCenter = null
                        onSharedRecenter?.invoke()
                    }
                    .testTag("map_follow_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Center",
                        tint = if (isFollowingUser) Color.White else controlIconColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Top-Right Modern Dual-Ring Precision Compass Instrument Over the Map (Preserved inside Map!)
        val headingParts = remember(compassHeadingStr) {
            val tokens = compassHeadingStr.trim().split("\\s+".toRegex())
            val deg = tokens.firstOrNull().orEmpty().ifBlank { "0°" }
            val dir = tokens.getOrNull(1).orEmpty().ifBlank { "N" }
            deg to dir
        }
        val innerHubBg = if (mapTheme.isDark) Color(0xFF111827) else Color(0xFFF7F9FC)
        val cardinalColor = if (headingParts.second.startsWith("N", ignoreCase = true)) {
            Color(0xFFFF5263)
        } else if (mapTheme.isDark) {
            Color(0xFF20B8F0)
        } else {
            Color(0xFF1677F2)
        }

        Surface(
            shape = CircleShape,
            color = controlSurfaceColor,
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(54.dp)
                .clip(CircleShape)
                .clickable {
                    isFollowingUser = true
                    exploredCenter = null
                }
                .testTag("map_compass_widget")
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Outer Rotating Compass Bezel & Perimeter North/South Pointers
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outerRadius = size.minDimension / 2f - 3.dp.toPx()
                    val innerShieldRadius = 15.dp.toPx()

                    // Subtle outer compass track circle
                    drawCircle(
                        color = controlBorderColor.copy(alpha = 0.55f),
                        radius = outerRadius - 2.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Rotate bezel & perimeter needles smoothly
                    rotate(degrees = -animatedCompassHeading, pivot = Offset(cx, cy)) {
                        // 12 precision degree ticks every 30°
                        for (i in 0 until 12) {
                            val angleRad = Math.toRadians((i * 30).toDouble())
                            val isCardinal = i % 3 == 0
                            val tickInner = if (isCardinal) innerShieldRadius + 1.5.dp.toPx() else innerShieldRadius + 3.dp.toPx()
                            val tickOuter = outerRadius - 2.2.dp.toPx()
                            val sinA = kotlin.math.sin(angleRad).toFloat()
                            val cosA = kotlin.math.cos(angleRad).toFloat()

                            // Skip 0° (North) and 180° (South) ticks because triangles sit there
                            if (i != 0 && i != 6) {
                                drawLine(
                                    color = if (isCardinal) controlIconColor.copy(alpha = 0.65f) else controlBorderColor.copy(alpha = 0.75f),
                                    start = Offset(cx + sinA * tickInner, cy - cosA * tickInner),
                                    end = Offset(cx + sinA * tickOuter, cy - cosA * tickOuter),
                                    strokeWidth = if (isCardinal) 1.5.dp.toPx() else 1.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        // Vibrant Red North Perimeter Pointer (strictly outside inner shield)
                        val northTipY = cy - outerRadius + 0.5.dp.toPx()
                        val northBaseY = cy - innerShieldRadius - 0.8.dp.toPx()
                        val halfBaseW = 4.2.dp.toPx()
                        val northPointer = Path().apply {
                            moveTo(cx, northTipY)
                            lineTo(cx - halfBaseW, northBaseY)
                            lineTo(cx + halfBaseW, northBaseY)
                            close()
                        }
                        drawPath(northPointer, color = Color(0xFFEF4444))

                        // Subtle Slate South Counter-Pointer (strictly outside inner shield)
                        val southTipY = cy + outerRadius - 1.dp.toPx()
                        val southBaseY = cy + innerShieldRadius + 0.8.dp.toPx()
                        val southPointer = Path().apply {
                            moveTo(cx, southTipY)
                            lineTo(cx - 3.4.dp.toPx(), southBaseY)
                            lineTo(cx + 3.4.dp.toPx(), southBaseY)
                            close()
                        }
                        drawPath(
                            southPointer,
                            color = if (mapTheme.isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                        )
                    }

                    // Inner Non-Rotating Digital Hub Shield (keeps text 100% separated from needle!)
                    drawCircle(
                        color = innerHubBg,
                        radius = innerShieldRadius,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = controlBorderColor.copy(alpha = 0.85f),
                        radius = innerShieldRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Crisp Non-Overlapping Center Digital Heading Readout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = headingParts.first,
                        color = controlIconColor,
                        fontSize = 9.sp,
                        lineHeight = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = headingParts.second,
                        color = cardinalColor,
                        fontSize = 8.sp,
                        lineHeight = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Right-Side Zoom Controls (+ / -, Flat Borderless)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = controlSurfaceColor,
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        continuousZoom = (continuousZoom + screensaverSettings.miniMapZoomStep.coerceIn(0.25f, 1.5f))
                            .coerceAtMost(MiniMapViewportMath.MAX_ZOOM)
                        onSharedZoomIn?.invoke()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("map_zoom_in")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = controlIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(1.dp)
                        .background(controlBorderColor)
                )
                IconButton(
                    onClick = {
                        continuousZoom = (continuousZoom - screensaverSettings.miniMapZoomStep.coerceIn(0.25f, 1.5f))
                            .coerceAtLeast(MiniMapViewportMath.MIN_ZOOM)
                        onSharedZoomOut?.invoke()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("map_zoom_out")
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = controlIconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom-Right Dynamic Scale Badge (Flat Borderless)
        val scaleText = remember(viewRangeMetres) {
            val tenthRange = (viewRangeMetres / 10.0).coerceAtLeast(10.0)
            val roundedMetres = when {
                tenthRange <= 25.0 -> 25.0
                tenthRange <= 50.0 -> 50.0
                tenthRange <= 100.0 -> 100.0
                tenthRange <= 250.0 -> 250.0
                tenthRange <= 500.0 -> 500.0
                tenthRange <= 1_000.0 -> 1_000.0
                tenthRange <= 5_000.0 -> 5_000.0
                tenthRange <= 25_000.0 -> 25_000.0
                tenthRange <= 100_000.0 -> 100_000.0
                tenthRange <= 500_000.0 -> 500_000.0
                else -> 2_000_000.0
            }
            OsmTileManager.formatScaleLabel(roundedMetres)
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = controlSurfaceColor,
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp, end = 10.dp)
        ) {
            Text(
                text = scaleText,
                color = controlIconColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

// -------------------------------------------------------------------
// COMPONENT 7: HOME PAGE FULL-SCREEN DYNAMIC CHAINAGE SCREENSAVER
// -------------------------------------------------------------------

const val HOME_SCREENSAVER_INACTIVITY_TIMEOUT_MS = 20_000L

data class ScreensaverDisplayState(
    val chainageText: String,
    val reliableSide: String?, // Strictly "LHS", "RHS", or null when unreliable or GPS is lost
    val directionArrow: String, // "↑" for increasing, "↓" for decreasing, "" when null
    val isLiveGpsValid: Boolean,
    val isIncreasingTrend: Boolean = true,
    val locationName: String = "",
    val speedText: String = "-- km/h",
    val speedKmh: Int? = null,
    val directionState: DirectionDetectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
    val isDirectionManual: Boolean = false
)

/**
 * Pure gating predicate deciding when the inactivity timer may count down and present
 * the full-screen Chainage Screensaver.
 */
fun shouldAllowScreensaver(
    isLifecycleResumed: Boolean,
    isDialogOpen: Boolean,
    isKeyboardOpen: Boolean,
    isInputFocused: Boolean = false,
    isMasterEnabled: Boolean = true,
    isAutoActivateEnabled: Boolean = true
): Boolean {
    return isMasterEnabled &&
        isAutoActivateEnabled &&
        isLifecycleResumed &&
        !isDialogOpen &&
        !isKeyboardOpen &&
        !isInputFocused
}

/**
 * Stateful holder for Screensaver display data that:
 * - Retains the last valid chainage across temporary GPS loss without inventing values.
 * - Strictly suppresses LHS/RHS when GPS is unavailable or side detection is unreliable.
 * - Filters and smooths live GPS speed in km/h, displaying "0 km/h" only on valid stationary fixes
 *   and "-- km/h" whenever speed is unavailable or stale.
 * - Retains last valid resolved location name smoothly without flickering or fabricating names.
 * - Clears retained route chainage when unloading/switching routes.
 */
class ScreensaverChainageStateHolder {
    private var lastValidChainageText: String? = null
    private var lastValidChainageMetres: Long? = null
    private var lastRouteId: String? = null
    private var smoothedSpeedKmh: Double? = null
    private var displayedSpeedKmh: Int? = null
    private var lastValidLocationName: String = ""

    fun reset() {
        lastValidChainageText = null
        lastValidChainageMetres = null
        lastRouteId = null
        smoothedSpeedKmh = null
        displayedSpeedKmh = null
        lastValidLocationName = ""
    }

    fun resolveSpeedKmh(
        locationState: LocationState?,
        nowMs: Long = System.currentTimeMillis()
    ): Int? {
        if (locationState == null || locationState.point == null || !locationState.isUsable) {
            smoothedSpeedKmh = null
            displayedSpeedKmh = null
            return null
        }
        val rawMps = locationState.speed
        if (rawMps == null || rawMps.isNaN() || rawMps < 0f) {
            smoothedSpeedKmh = null
            displayedSpeedKmh = null
            return null
        }
        val fixTime = locationState.lastFixTime
        if (fixTime <= 0L || (nowMs - fixTime) > SCREENSAVER_SPEED_STALE_MS) {
            smoothedSpeedKmh = null
            displayedSpeedKmh = null
            return null
        }

        val rawKmh = (rawMps.toDouble() * 3.6).coerceIn(0.0, 260.0)
        // Valid stationary GPS speed reading (< 0.5 m/s) converges cleanly to 0 km/h
        if (rawKmh < 1.8) {
            val prev = smoothedSpeedKmh
            if (prev == null || prev < 3.5) {
                smoothedSpeedKmh = 0.0
                displayedSpeedKmh = 0
                return 0
            }
        }

        val prev = smoothedSpeedKmh
        val smoothed = if (prev == null) {
            rawKmh
        } else {
            val alpha = if (kotlin.math.abs(rawKmh - prev) >= 12.0) 0.48 else 0.32
            prev + alpha * (rawKmh - prev)
        }
        smoothedSpeedKmh = smoothed

        val candidateRounded = kotlin.math.round(smoothed).toInt().coerceIn(0, 260)
        val currentShown = displayedSpeedKmh
        if (currentShown == null || kotlin.math.abs(smoothed - currentShown.toDouble()) >= 0.65) {
            displayedSpeedKmh = candidateRounded
        }
        return displayedSpeedKmh
    }

    fun resolveLocationName(rawLocationName: String?): String {
        val cleaned = rawLocationName?.trim()?.takeIf { candidate ->
            if (candidate.isBlank()) return@takeIf false
            val lower = candidate.lowercase(Locale.ROOT)
            lower != "null" &&
                lower != "none" &&
                lower != "unknown" &&
                lower != "n/a" &&
                lower != "unnamed road" &&
                candidate.any { it.isLetter() }
        }
        if (cleaned != null) {
            lastValidLocationName = cleaned
        }
        return lastValidLocationName
    }

    fun resolve(
        matchResult: ChainageMatchResult,
        route: RouteData?,
        hasGpsFix: Boolean,
        projectRoadSide: RoadSide,
        locationState: LocationState? = null,
        locationName: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): ScreensaverDisplayState {
        if (route?.id != lastRouteId) {
            lastValidChainageText = null
            lastValidChainageMetres = null
            lastRouteId = route?.id
        }

        val resolvedSpeed = resolveSpeedKmh(locationState, nowMs)
        val formattedSpeed = resolvedSpeed?.let { "$it km/h" } ?: "-- km/h"
        val resolvedLocationName = resolveLocationName(locationName)

        val isManualActive = matchResult.source == ChainageSource.MANUAL || matchResult.isManual
        if (isManualActive) {
            val manualText = matchResult.displayChainage
                .takeIf { it.isNotBlank() && it != "---" && it != "Manual" }
            val metres = matchResult.chainage?.metres
            val increasing = if (metres != null && lastValidChainageMetres != null) {
                metres >= lastValidChainageMetres!!
            } else {
                true
            }
            if (manualText != null) {
                lastValidChainageText = manualText
            }
            if (metres != null) {
                lastValidChainageMetres = metres
            }
            val smartDir = matchResult.smartDirectionState
            val sideIfReliable = if (smartDir != null) {
                if (smartDir.isManualOverride || (hasGpsFix && route != null && !matchResult.isUncertain)) {
                    smartDir.confirmedBinarySide
                } else null
            } else if (
                hasGpsFix &&
                route != null &&
                matchResult.confidence != MatchConfidence.NO_MATCH &&
                !matchResult.isUncertain
            ) {
                normalizeBinarySide(matchResult.detectedSide)
                    ?: normalizeBinarySide(projectRoadSide.name)
            } else {
                null
            }
            val resolvedArrow = if (smartDir != null && sideIfReliable != null) {
                smartDir.directionArrow.ifBlank { sideArrowFor(sideIfReliable) }
            } else {
                sideArrowFor(sideIfReliable)
            }
            return ScreensaverDisplayState(
                chainageText = manualText ?: lastValidChainageText ?: "--+---",
                reliableSide = sideIfReliable,
                directionArrow = resolvedArrow,
                isLiveGpsValid = hasGpsFix && route != null,
                isIncreasingTrend = smartDir?.isIncreasingChainage ?: increasing,
                locationName = resolvedLocationName,
                speedText = formattedSpeed,
                speedKmh = resolvedSpeed,
                directionState = smartDir?.detectionState ?: if (sideIfReliable == "LHS") DirectionDetectionState.LHS_CONFIRMED else if (sideIfReliable == "RHS") DirectionDetectionState.RHS_CONFIRMED else DirectionDetectionState.DIRECTION_UNCERTAIN,
                isDirectionManual = smartDir?.isManualOverride == true
            )
        }

        val isReliableLiveMatch = route != null &&
            hasGpsFix &&
            matchResult.chainage != null &&
            matchResult.confidence != MatchConfidence.NO_MATCH &&
            !matchResult.isUncertain &&
            !(matchResult.confidence == MatchConfidence.LOW && matchResult.offsetMetres > 80.0)

        val smartDir = matchResult.smartDirectionState

        if (isReliableLiveMatch) {
            val metres = matchResult.chainage!!.metres
            val text = matchResult.displayChainage
            val increasing = smartDir?.isIncreasingChainage
                ?: lastValidChainageMetres?.let { metres >= it }
                ?: true
            lastValidChainageText = text
            lastValidChainageMetres = metres

            val reliableSide = if (smartDir != null) {
                smartDir.confirmedBinarySide
            } else {
                normalizeBinarySide(matchResult.detectedSide)
                    ?: normalizeBinarySide(projectRoadSide.name)
            }
            val resolvedArrow = if (smartDir != null && reliableSide != null) {
                smartDir.directionArrow.ifBlank { sideArrowFor(reliableSide) }
            } else {
                sideArrowFor(reliableSide)
            }

            return ScreensaverDisplayState(
                chainageText = text,
                reliableSide = reliableSide,
                directionArrow = resolvedArrow,
                isLiveGpsValid = true,
                isIncreasingTrend = increasing,
                locationName = resolvedLocationName,
                speedText = formattedSpeed,
                speedKmh = resolvedSpeed,
                directionState = smartDir?.detectionState ?: when (reliableSide) {
                    "LHS" -> DirectionDetectionState.LHS_CONFIRMED
                    "RHS" -> DirectionDetectionState.RHS_CONFIRMED
                    else -> DirectionDetectionState.DIRECTION_UNCERTAIN
                },
                isDirectionManual = smartDir?.isManualOverride == true
            )
        }

        // Temporary GPS loss, off-corridor uncertainty, or no route loaded:
        // Retain last valid chainage on the active route (never invent movement),
        // and omit LHS/RHS so no false direction is ever displayed.
        return ScreensaverDisplayState(
            chainageText = if (route != null) (lastValidChainageText ?: "--+---") else "--+---",
            reliableSide = null,
            directionArrow = "",
            isLiveGpsValid = false,
            isIncreasingTrend = true,
            locationName = resolvedLocationName,
            speedText = formattedSpeed,
            speedKmh = resolvedSpeed,
            directionState = smartDir?.detectionState ?: if (route == null) {
                DirectionDetectionState.ALIGNMENT_UNAVAILABLE
            } else {
                DirectionDetectionState.GPS_SIGNAL_WEAK
            },
            isDirectionManual = smartDir?.isManualOverride == true
        )
    }

    companion object {
        const val SCREENSAVER_SPEED_STALE_MS = 15_000L

        internal fun normalizeBinarySide(raw: String?): String? {
            return when (raw?.trim()?.uppercase(Locale.ROOT)) {
                "LHS" -> "LHS"
                "RHS" -> "RHS"
                else -> null
            }
        }

        internal fun sideArrowFor(side: String?): String {
            return when (side) {
                "LHS" -> "↑"
                "RHS" -> "↓"
                else -> ""
            }
        }
    }
}

private tailrec fun Context.findHostActivityWindow(): Window? {
    return when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.findHostActivityWindow()
        else -> null
    }
}

/**
 * Enforces true edge-to-edge immersive mode on both the Screensaver Dialog Window
 * and the underlying host Activity Window so clock, battery, status icons, and navigation bar
 * remain completely hidden even across Portrait / Landscape / Mirror focus transitions, and
 * cleanly restores system bars immediately upon dismissal.
 */
internal fun applyScreensaverImmersiveMode(
    dialogWindow: Window?,
    dialogView: View?,
    hostActivityWindow: Window?,
    enable: Boolean
) {
    if (dialogWindow != null) {
        WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
        dialogWindow.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        @Suppress("DEPRECATION")
        dialogWindow.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        dialogWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = dialogWindow.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            dialogWindow.attributes = attrs
        }
        val targetView = dialogView ?: dialogWindow.decorView
        val controller = WindowCompat.getInsetsController(dialogWindow, targetView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enable) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (hostActivityWindow != null) {
        val hostController = WindowCompat.getInsetsController(
            hostActivityWindow,
            hostActivityWindow.decorView
        )
        hostController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enable) {
            hostController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            hostController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

internal fun isDeviceScreenOffOrLocked(context: Context): Boolean {
    return try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val screenNotInteractive = powerManager != null && !powerManager.isInteractive
        val keyguardLocked = keyguardManager?.isKeyguardLocked == true
        screenNotInteractive || keyguardLocked
    } catch (_: Exception) {
        false
    }
}

/**
 * Coordinates the configurable inactivity countdown for the Home Page, tracks foreground
 * lifecycle, detects pointer/key activity cleanly without consuming child gestures, blocks while
 * dialogs/keyboard are active, respects Master/Auto-Activate/Touch-to-Dismiss/Auto-Restart toggles,
 * supports Screen OFF/ON power-saving mode, cleanly resumes the Screensaver after locking/unlocking
 * the phone, and hosts the true immersive edge-to-edge Dialog without disturbing Home Page scroll state.
 */
@Composable
fun HomeScreensaverCoordinator(
    isBlockingUiOpen: Boolean,
    inactivityTimeoutMs: Long = HOME_SCREENSAVER_INACTIVITY_TIMEOUT_MS,
    isScreensaverVisible: Boolean,
    onShowScreensaver: () -> Unit,
    onDismissScreensaver: () -> Unit,
    screensaverContent: @Composable (onDismiss: () -> Unit) -> Unit,
    masterEnabled: Boolean = true,
    autoActivateEnabled: Boolean = true,
    touchToDismiss: Boolean = true,
    autoRestartTimer: Boolean = true,
    keepScreenAwake: Boolean = true,
    isScreenOff: Boolean = false,
    isAodActive: Boolean = false,
    aodDimLevel: Float = com.example.model.HomeScreensaverSettings.DEFAULT_AOD_DIM_LEVEL,
    onWakeFromScreenOff: (() -> Unit)? = null,
    onExitAod: (() -> Unit)? = null,
    content: @Composable (interactionModifier: Modifier) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    var interactionEpoch by remember { mutableLongStateOf(0L) }
    var lastMoveBumpUptimeMs by remember { mutableLongStateOf(0L) }
    var hasDismissedInSession by remember { mutableStateOf(false) }
    var resumeScreensaverOnUnlock by remember { mutableStateOf(false) }

    val latestIsScreensaverVisible by rememberUpdatedState(isScreensaverVisible)
    val latestMasterEnabled by rememberUpdatedState(masterEnabled)
    val latestIsBlockingUiOpen by rememberUpdatedState(isBlockingUiOpen)
    val latestOnShowScreensaver by rememberUpdatedState(onShowScreensaver)
    val latestTouchToDismiss by rememberUpdatedState(touchToDismiss)
    val latestOnDismissScreensaver by rememberUpdatedState(onDismissScreensaver)
    val latestIsScreenOff by rememberUpdatedState(isScreenOff)
    val latestIsAodActive by rememberUpdatedState(isAodActive)
    val latestOnWakeFromScreenOff by rememberUpdatedState(onWakeFromScreenOff)
    val latestOnExitAod by rememberUpdatedState(onExitAod)

    fun notifyUserInteraction(force: Boolean) {
        val nowMs = SystemClock.uptimeMillis()
        if (force || (nowMs - lastMoveBumpUptimeMs) >= 400L) {
            lastMoveBumpUptimeMs = nowMs
            interactionEpoch += 1L
        }
    }

    // Reset single-cycle dismissal flag whenever auto-restart or master toggles change
    LaunchedEffect(autoRestartTimer, masterEnabled, autoActivateEnabled) {
        if (autoRestartTimer || !masterEnabled || !autoActivateEnabled) {
            hasDismissedInSession = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isLifecycleResumed = true
                    if (resumeScreensaverOnUnlock) {
                        resumeScreensaverOnUnlock = false
                        if (latestMasterEnabled && !latestIsBlockingUiOpen && !latestIsScreensaverVisible) {
                            latestOnShowScreensaver()
                        }
                    } else {
                        hasDismissedInSession = false
                        interactionEpoch += 1L
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isLifecycleResumed = false
                    if (latestIsScreensaverVisible && isDeviceScreenOffOrLocked(context)) {
                        // Phone locked normally while Screensaver was active -> preserve session to resume on unlock
                        resumeScreensaverOnUnlock = true
                    } else if (!resumeScreensaverOnUnlock) {
                        latestOnDismissScreensaver()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val density = LocalDensity.current
    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0
    val canAllowWhenVisible = shouldAllowScreensaver(
        isLifecycleResumed = isLifecycleResumed,
        isDialogOpen = isBlockingUiOpen,
        isKeyboardOpen = isKeyboardOpen,
        isMasterEnabled = masterEnabled,
        isAutoActivateEnabled = true // If already visible manually or via timer, only master/blocking/background closes it
    )
    val canRunAutoTimer = shouldAllowScreensaver(
        isLifecycleResumed = isLifecycleResumed,
        isDialogOpen = isBlockingUiOpen,
        isKeyboardOpen = isKeyboardOpen,
        isMasterEnabled = masterEnabled,
        isAutoActivateEnabled = autoActivateEnabled
    )

    // Dismiss immediately if disabled, dialog opens, keyboard opens, or normal background transition occurs
    LaunchedEffect(canAllowWhenVisible, isScreensaverVisible, resumeScreensaverOnUnlock) {
        if (!canAllowWhenVisible && isScreensaverVisible && !resumeScreensaverOnUnlock) {
            onDismissScreensaver()
        }
    }

    // Single inactivity timer coroutine — automatically cancelled & restarted on any interaction
    LaunchedEffect(
        isScreensaverVisible,
        canRunAutoTimer,
        interactionEpoch,
        inactivityTimeoutMs,
        hasDismissedInSession,
        autoRestartTimer
    ) {
        val shouldCount = !isScreensaverVisible &&
            canRunAutoTimer &&
            (autoRestartTimer || !hasDismissedInSession)
        if (shouldCount) {
            delay(inactivityTimeoutMs.coerceAtLeast(1_000L))
            onShowScreensaver()
        }
    }

    val interactionModifier = Modifier
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val hasDownOrUp = event.changes.any {
                        it.changedToDownIgnoreConsumed() || it.changedToUpIgnoreConsumed()
                    }
                    if (hasDownOrUp) {
                        notifyUserInteraction(force = true)
                    } else if (event.changes.any { it.pressed }) {
                        notifyUserInteraction(force = false)
                    }
                }
            }
        }
        .onPreviewKeyEvent {
            notifyUserInteraction(force = true)
            false
        }

    content(interactionModifier)

    if (isScreensaverVisible && canAllowWhenVisible) {
        val hostActivityWindow = remember(context) { context.findHostActivityWindow() }
        Dialog(
            onDismissRequest = {
                when {
                    latestIsScreenOff -> latestOnWakeFromScreenOff?.invoke()
                    latestIsAodActive -> latestOnExitAod?.invoke()
                    else -> {
                        notifyUserInteraction(force = true)
                        hasDismissedInSession = true
                        latestOnDismissScreensaver()
                    }
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = !isScreenOff && !isAodActive,
                dismissOnClickOutside = latestTouchToDismiss && !isScreenOff && !isAodActive
            )
        ) {
            BackHandler(enabled = isScreenOff || isAodActive) {
                if (latestIsScreenOff) {
                    latestOnWakeFromScreenOff?.invoke()
                } else if (latestIsAodActive) {
                    latestOnExitAod?.invoke()
                }
            }
            com.example.util.KeepScreenOn(
                enabled = keepScreenAwake,
                requesterId = "HomeScreensaver"
            )
            val localView = LocalView.current
            val dialogWindow = (localView.parent as? DialogWindowProvider)?.window
            val powerThermalSnapshot = remember(context, isAodActive) {
                com.example.navigation.DevicePowerThermalMonitor.snapshot(context)
            }
            DisposableEffect(dialogWindow, isScreenOff, isAodActive, aodDimLevel, powerThermalSnapshot.shouldConserveUiResources) {
                if (dialogWindow != null) {
                    val attrs = dialogWindow.attributes
                    attrs.screenBrightness = when {
                        isScreenOff -> 0.01f
                        isAodActive -> AmoledBurnInProtection.resolveWindowBrightness(
                            dimLevel = aodDimLevel,
                            conservePower = powerThermalSnapshot.shouldConserveUiResources
                        )
                        else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                    dialogWindow.attributes = attrs
                }
                onDispose {
                    if (dialogWindow != null) {
                        val attrs = dialogWindow.attributes
                        attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        dialogWindow.attributes = attrs
                    }
                }
            }
            DisposableEffect(dialogWindow, localView, hostActivityWindow, keepScreenAwake) {
                if (dialogWindow != null) {
                    dialogWindow.setDimAmount(0f)
                    dialogWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    if (keepScreenAwake) {
                        dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                applyScreensaverImmersiveMode(
                    dialogWindow = dialogWindow,
                    dialogView = localView,
                    hostActivityWindow = hostActivityWindow,
                    enable = true
                )
                val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                    if (hasFocus) {
                        if (keepScreenAwake) {
                            dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        applyScreensaverImmersiveMode(
                            dialogWindow = dialogWindow,
                            dialogView = localView,
                            hostActivityWindow = hostActivityWindow,
                            enable = true
                        )
                    }
                }
                localView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
                onDispose {
                    dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (localView.viewTreeObserver.isAlive) {
                        localView.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
                    }
                    applyScreensaverImmersiveMode(
                        dialogWindow = dialogWindow,
                        dialogView = localView,
                        hostActivityWindow = hostActivityWindow,
                        enable = false
                    )
                }
            }

            screensaverContent {
                when {
                    latestIsScreenOff -> latestOnWakeFromScreenOff?.invoke()
                    latestIsAodActive -> latestOnExitAod?.invoke()
                    latestTouchToDismiss -> {
                        notifyUserInteraction(force = true)
                        hasDismissedInSession = true
                        latestOnDismissScreensaver()
                    }
                }
            }
        }
    }
}

/**
 * Full-Screen Automotive HUD & Chainage Screensaver Overlay.
 * Minimal HUD layout featuring compact Mirror/Normal, Orientation Lock, and Screen OFF/ON
 * controls in safe corners. Tapping anywhere outside quick controls dismisses immediately
 * when Touch-to-Dismiss is active (or wakes smoothly from Screen OFF blackout mode).
 */
@Composable
fun ChainageScreensaverOverlay(
    displayState: ScreensaverDisplayState,
    location: GeoPoint?,
    bearing: Float?,
    compassHeading: Float,
    routeData: RouteData?,
    mapThemePreference: String = "system",
    mapTypePreference: String = "standard",
    isDarkAppTheme: Boolean = com.example.ui.theme.isAppDarkTheme(),
    screensaverSettings: HomeScreensaverSettings = HomeScreensaverSettings(),
    isSamplePreview: Boolean = false,
    sensorPostureOverride: ScreensaverSensorPosture? = null,
    isScreenOff: Boolean = false,
    isAodActive: Boolean = false,
    is24HourFormat: Boolean = true,
    nextKmLabel: String? = null,
    distanceToNextMetres: Long? = null,
    lastKmStoneEvent: com.example.navigation.KmStoneAlertEvent? = null,
    isKmStoneMonitoringActive: Boolean = false,
    mapHeadingState: MapHeadingState? = null,
    onToggleMirrorQuickMode: ((enableMirror: Boolean, isCurrentlyLandscape: Boolean) -> Unit)? = null,
    onToggleOrientationLock: ((isCurrentlyLandscape: Boolean) -> Unit)? = null,
    onToggleScreenOff: ((Boolean) -> Unit)? = null,
    onToggleAod: ((Boolean) -> Unit)? = null,
    onForceBlackScreenOff: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val resolvedSettings = remember(screensaverSettings, mapTypePreference, mapThemePreference) {
        if (screensaverSettings == HomeScreensaverSettings() &&
            mapTypePreference.equals("satellite", ignoreCase = true)
        ) {
            screensaverSettings.copy(miniMapStyle = MiniMapStyle.SATELLITE)
        } else {
            screensaverSettings
        }
    }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnToggleScreenOff by rememberUpdatedState(onToggleScreenOff)
    val currentOnToggleAod by rememberUpdatedState(onToggleAod)
    val isLowPowerDisplayMode = isScreenOff || isAodActive
    val hardwareSensorPosture = rememberScreensaverSensorPosture(
        enabled = resolvedSettings.autoOrientationEnabled && sensorPostureOverride == null && !isLowPowerDisplayMode
    )
    val activeSensorPosture = sensorPostureOverride ?: hardwareSensorPosture
    val blackoutFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isLowPowerDisplayMode) {
        if (isLowPowerDisplayMode) {
            try {
                blackoutFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    var temporaryLandscapeOverride by remember { mutableStateOf(false) }
    LaunchedEffect(activeSensorPosture) {
        if (activeSensorPosture?.isLandscape == true) {
            temporaryLandscapeOverride = false
        }
    }
    LaunchedEffect(resolvedSettings.mirrorModePreferred, resolvedSettings.displayMode) {
        if (!resolvedSettings.mirrorModePreferred &&
            resolvedSettings.displayMode != com.example.model.ScreensaverDisplayMode.MIRROR
        ) {
            temporaryLandscapeOverride = false
        }
    }

    val handleQuickToggle: ((Boolean, Boolean) -> Unit)? = if (onToggleMirrorQuickMode != null) {
        { enableMirror, currentlyLandscape ->
            if (enableMirror) {
                if (resolvedSettings.autoOrientationEnabled && activeSensorPosture?.isLandscape == false) {
                    temporaryLandscapeOverride = true
                }
            } else {
                temporaryLandscapeOverride = false
            }
            onToggleMirrorQuickMode(enableMirror, currentlyLandscape)
        }
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_chainage_screensaver")
            .focusRequester(blackoutFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (isLowPowerDisplayMode &&
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN
                ) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_VOLUME_UP,
                        AndroidKeyEvent.KEYCODE_VOLUME_DOWN,
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_SPACE,
                        AndroidKeyEvent.KEYCODE_BACK -> {
                            if (isScreenOff) {
                                currentOnToggleScreenOff?.invoke(false)
                            } else if (isAodActive) {
                                currentOnToggleAod?.invoke(false)
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(resolvedSettings.touchToDismiss, isLowPowerDisplayMode) {
                if (isLowPowerDisplayMode || !resolvedSettings.touchToDismiss) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val touchSlop = viewConfiguration.touchSlop
                    var movedTooFar = false
                    var consumedByChild = down.isConsumed
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            consumedByChild = true
                        }
                        if ((change.position - downPos).getDistance() > touchSlop) {
                            movedTooFar = true
                        }
                        if (!change.pressed) {
                            if (change.changedToUpIgnoreConsumed() && !consumedByChild && !movedTooFar) {
                                currentOnDismiss()
                            }
                            break
                        }
                    }
                }
            }
            .then(
                if (!isLowPowerDisplayMode && resolvedSettings.touchToDismiss) {
                    Modifier.semantics {
                        onClick(label = "Dismiss screensaver") {
                            currentOnDismiss()
                            true
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        AutomotiveChainageScreensaverContent(
            displayState = displayState,
            location = location,
            bearing = bearing,
            compassHeading = compassHeading,
            routeData = routeData,
            mapThemePreference = mapThemePreference,
            mapTypePreference = mapTypePreference,
            isDarkAppTheme = isDarkAppTheme,
            screensaverSettings = resolvedSettings,
            isSamplePreview = isSamplePreview,
            sensorPosture = activeSensorPosture,
            temporaryLandscapeOverride = temporaryLandscapeOverride,
            isScreenOff = isScreenOff,
            isAodActive = isAodActive,
            is24HourFormat = is24HourFormat,
            nextKmLabel = nextKmLabel,
            distanceToNextMetres = distanceToNextMetres,
            lastKmStoneEvent = lastKmStoneEvent,
            isKmStoneMonitoringActive = isKmStoneMonitoringActive,
            mapHeadingState = mapHeadingState,
            onToggleMirrorQuickMode = handleQuickToggle,
            onToggleOrientationLock = onToggleOrientationLock,
            onToggleScreenOff = onToggleScreenOff,
            onToggleAod = onToggleAod,
            onForceBlackScreenOff = onForceBlackScreenOff
        )
    }
}
