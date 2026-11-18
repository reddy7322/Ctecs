package com.example.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
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
import com.example.geo.OsmTileManager
import com.example.location.LocationNameResolver
import com.example.location.LocationState
import com.example.location.LocationTracker
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.HudMapMarkerStyle
import com.example.model.MatchConfidence
import com.example.model.MiniMapStyle
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.navigation.ActiveChainageNavigationTarget
import com.example.overlay.MiniMapRenderer
import com.example.ui.MainViewModel
import com.example.ui.dialogs.ChainageManagementDialog
import com.example.ui.dialogs.ChainageSourceBadge
import com.example.ui.dialogs.NavigateToChainageDialog
import com.example.ui.dialogs.NavigateToChainageQuickButton
import com.example.ui.theme.AccentRed
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
    com.example.util.KeepScreenOn(enabled = true, requesterId = "HomeScreen")

    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val matchResult by viewModel.liveMatchResult.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()
    val compassState by viewModel.compassState.collectAsStateWithLifecycle()

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
    var preferredExternalNavAppId by remember { mutableStateOf<String?>(null) }
    var isScreensaverVisible by remember { mutableStateOf(false) }
    val screensaverStateHolder = remember { ScreensaverChainageStateHolder() }

    val isBlockingScreensaverModal = showChainageManagementDialog ||
        showEditProjectStretchDialog ||
        showNavigateToChainageDialog ||
        availableUpdate != null ||
        showWhatsNewDialog ||
        interruptedSession != null ||
        isPageLoading

    val isDark = com.example.ui.theme.isAppDarkTheme()

    // Smooth Animated Light Theme vs Dark Theme Palette
    val pageBgColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF090D16) else Color(0xFFF1F5F9),
        animationSpec = tween(260),
        label = "home_page_bg"
    )
    val cardBgColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF111827) else Color(0xFFFFFFFF),
        animationSpec = tween(260),
        label = "home_card_bg"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
        animationSpec = tween(260),
        label = "home_card_border"
    )
    val mainTextColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
        animationSpec = tween(260),
        label = "home_main_text"
    )
    val subTextColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
        animationSpec = tween(260),
        label = "home_sub_text"
    )

    // Pulse transition for live GPS status dot
    val infiniteTransition = rememberInfiniteTransition(label = "gps_dot")
    val gpsPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(750), repeatMode = RepeatMode.Reverse),
        label = "gps_dot_pulse"
    )

    val vmLocationName by viewModel.currentLocationName.collectAsStateWithLifecycle()
    val screensaverContext = LocalContext.current
    var screensaverResolvedLocationName by remember { mutableStateOf("") }
    var lastScreensaverGeocodePoint by remember { mutableStateOf<GeoPoint?>(null) }
    var lastScreensaverGeocodeAttemptMs by remember { mutableLongStateOf(0L) }
    var screensaverClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Throttled cached reverse-geocoding for active screensaver only (never blocks UI or calls Geocoder on every GPS tick)
    LaunchedEffect(
        isScreensaverVisible,
        locationState.point,
        vmLocationName,
        screensaverSettings.locationDynamicUpdates
    ) {
        if (vmLocationName.isNotBlank()) {
            screensaverResolvedLocationName = vmLocationName
        }
        val pt = locationState.point
        if (isScreensaverVisible &&
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
                onToggleMirrorQuickMode = { enableMirror, currentlyLandscape ->
                    viewModel.toggleScreensaverMirrorQuickMode(
                        enableMirror = enableMirror,
                        isCurrentlyLandscape = currentlyLandscape
                    )
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
                compassHeadingStr = compassState.displayHeading,
                currentThemeMode = currentThemeMode,
                onToggleTheme = {
                    val nextMode = when (currentThemeMode) {
                        "dark" -> "light"
                        "light" -> "system"
                        else -> "dark"
                    }
                    viewModel.setThemeMode(nextMode)
                    viewModel.showToast("Theme: ${nextMode.uppercase()}")
                },
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor,
                isDark = isDark,
                isCompact = isCompactHeight
            )

            // 2. Dominant Central Chainage Display (Highest Priority)
            val displayChainageText = when {
                matchResult.source == ChainageSource.MANUAL || matchResult.isManual -> matchResult.displayChainage
                activeRoute == null -> "NO KMZ"
                locationState.point == null -> "--+---"
                matchResult.chainage != null -> matchResult.displayChainage
                else -> "--+---"
            }

            val detectedSide = when {
                matchResult.detectedSide.equals("LHS", ignoreCase = true) -> "LHS"
                matchResult.detectedSide.equals("RHS", ignoreCase = true) -> "RHS"
                matchResult.detectedSide.equals("MEDIAN", ignoreCase = true) -> "MEDIAN"
                matchResult.detectedSide.equals("BOTH", ignoreCase = true) -> "BHS"
                projectProfile.roadSide == com.example.model.RoadSide.LHS -> "LHS"
                projectProfile.roadSide == com.example.model.RoadSide.RHS -> "RHS"
                projectProfile.roadSide == com.example.model.RoadSide.MEDIAN -> "MEDIAN"
                projectProfile.roadSide == com.example.model.RoadSide.BOTH -> "BHS"
                else -> "RHS"
            }

            CentralChainageDisplay(
                chainageText = displayChainageText,
                detectedSide = detectedSide,
                confidence = matchResult.confidence,
                offsetMetres = matchResult.offsetMetres,
                hasActiveRoute = activeRoute != null,
                source = matchResult.source,
                rawChainage = matchResult.rawUncorrectedChainage?.format(),
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor,
                isDark = isDark,
                isCompact = isCompactHeight,
                onClick = { showChainageManagementDialog = true },
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

            // 3. Project & Stretch Information Cards

            ProjectStretchCardsRow(
                projectName = projectProfile.highwayId,
                stretchName = projectProfile.packageSection,
                onEdit = { showEditProjectStretchDialog = true },
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor,
                isDark = isDark,
                isCompact = isCompactHeight
            )

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

            // 4. Interactive Smart KMZ Map - Synchronized with Shared Mini Map Settings (all Screensaver config lives in Settings -> Screensaver)
            if (screensaverSettings.miniMapVisible) {
                val mapBorderModifier = if (screensaverSettings.miniMapShowBorder) {
                    Modifier.border(
                        width = 1.2.dp,
                        color = Color(screensaverSettings.miniMapBorderColorPreset.colorLong).copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier
                }
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
                        .clip(RoundedCornerShape(14.dp))
                        .then(mapBorderModifier)
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // 5. Prominent Next KM Stone Card
            val nextKmTracker = remember { NextKmStoneTracker() }
            val isExplicitRoadSide = projectProfile.roadSide == com.example.model.RoadSide.LHS ||
                projectProfile.roadSide == com.example.model.RoadSide.RHS
            val nextKmState = remember(
                matchResult,
                activeRoute?.id,
                activeRoute?.markers?.size,
                detectedSide,
                isExplicitRoadSide
            ) {
                nextKmTracker.evaluate(
                    matchResult = matchResult,
                    route = activeRoute,
                    detectedSide = detectedSide,
                    isAutoDetectedSide = !isExplicitRoadSide
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
                NavigateToChainageDialog(
                    matchResult = matchResult,
                    activeRoute = activeRoute,
                    currentGpsPoint = locationState.point,
                    correctionPoints = correctionsForRoute,
                    isCorrectionsEnabled = isCorrectionsEnabled,
                    projectRoadSide = projectProfile.roadSide,
                    detectedSideLabel = detectedSide,
                    nextKmStoneSuggestion = nextKmState.nextChainage.takeIf { nextKmState.isReliable },
                    activeTarget = activeChainageNavTarget?.takeIf { it.routeId == activeRoute?.id },
                    preferredExternalAppId = preferredExternalNavAppId,
                    onSavePreferredExternalAppId = { preferredExternalNavAppId = it },
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
// COMPONENT 1: TOP TELEMETRY HEADER ROW (4 Theme-Matched Cards)
// -------------------------------------------------------------------
@Composable
fun TopTelemetryHeaderRow(
    accuracy: Float,
    hasFix: Boolean,
    gpsPulseAlpha: Float,
    satellitesCount: Int,
    speedKmh: Float,
    compassHeadingStr: String,
    currentThemeMode: String,
    onToggleTheme: () -> Unit,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false
) {
    val cardHeight = if (isCompact) 42.dp else 46.dp

    val (targetGpsColor, gpsValueText, gpsStatusSub) = when {
        !hasFix -> Triple(
            if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            "--",
            "Searching"
        )
        accuracy <= 5f -> Triple(
            if (isDark) Color(0xFF10B981) else Color(0xFF059669),
            String.format(Locale.US, "±%.0fm", accuracy),
            "GPS Good"
        )
        accuracy <= 15f -> Triple(
            if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
            String.format(Locale.US, "±%.0fm", accuracy),
            "GPS Fair"
        )
        else -> Triple(
            if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
            String.format(Locale.US, "±%.0fm", accuracy),
            "GPS Weak"
        )
    }

    val gpsAccentColor by animateColorAsState(
        targetValue = targetGpsColor,
        animationSpec = tween(250),
        label = "gps_accent_color"
    )

    val satAccent = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val speedAccent = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
    val compassAccent = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isVeryNarrow = maxWidth < 310.dp

        if (isVeryNarrow) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    GpsAccuracyTelemetryCard(
                        gpsAccentColor = gpsAccentColor,
                        gpsValueText = gpsValueText,
                        gpsStatusSub = gpsStatusSub,
                        hasFix = hasFix,
                        gpsPulseAlpha = gpsPulseAlpha,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor,
                        mainTextColor = mainTextColor,
                        subTextColor = subTextColor,
                        isDark = isDark,
                        modifier = Modifier.weight(1f).height(cardHeight)
                    )
                    SatelliteTelemetryCard(
                        satellitesCount = satellitesCount,
                        hasFix = hasFix,
                        satAccent = satAccent,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor,
                        mainTextColor = mainTextColor,
                        subTextColor = subTextColor,
                        isDark = isDark,
                        modifier = Modifier.weight(1f).height(cardHeight)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SpeedTelemetryCard(
                        speedKmh = speedKmh,
                        speedAccent = speedAccent,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor,
                        mainTextColor = mainTextColor,
                        subTextColor = subTextColor,
                        isDark = isDark,
                        modifier = Modifier.weight(1f).height(cardHeight)
                    )
                    CompassTelemetryCard(
                        compassHeadingStr = compassHeadingStr,
                        currentThemeMode = currentThemeMode,
                        compassAccent = compassAccent,
                        onToggleTheme = onToggleTheme,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor,
                        mainTextColor = mainTextColor,
                        subTextColor = subTextColor,
                        isDark = isDark,
                        modifier = Modifier.weight(1.1f).height(cardHeight)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GpsAccuracyTelemetryCard(
                    gpsAccentColor = gpsAccentColor,
                    gpsValueText = gpsValueText,
                    gpsStatusSub = gpsStatusSub,
                    hasFix = hasFix,
                    gpsPulseAlpha = gpsPulseAlpha,
                    cardBgColor = cardBgColor,
                    cardBorderColor = cardBorderColor,
                    mainTextColor = mainTextColor,
                    subTextColor = subTextColor,
                    isDark = isDark,
                    modifier = Modifier.weight(1.06f).height(cardHeight)
                )
                SatelliteTelemetryCard(
                    satellitesCount = satellitesCount,
                    hasFix = hasFix,
                    satAccent = satAccent,
                    cardBgColor = cardBgColor,
                    cardBorderColor = cardBorderColor,
                    mainTextColor = mainTextColor,
                    subTextColor = subTextColor,
                    isDark = isDark,
                    modifier = Modifier.weight(1.02f).height(cardHeight)
                )
                SpeedTelemetryCard(
                    speedKmh = speedKmh,
                    speedAccent = speedAccent,
                    cardBgColor = cardBgColor,
                    cardBorderColor = cardBorderColor,
                    mainTextColor = mainTextColor,
                    subTextColor = subTextColor,
                    isDark = isDark,
                    modifier = Modifier.weight(0.84f).height(cardHeight)
                )
                CompassTelemetryCard(
                    compassHeadingStr = compassHeadingStr,
                    currentThemeMode = currentThemeMode,
                    compassAccent = compassAccent,
                    onToggleTheme = onToggleTheme,
                    cardBgColor = cardBgColor,
                    cardBorderColor = cardBorderColor,
                    mainTextColor = mainTextColor,
                    subTextColor = subTextColor,
                    isDark = isDark,
                    modifier = Modifier.weight(1.18f).height(cardHeight)
                )
            }
        }
    }
}

@Composable
private fun GpsAccuracyTelemetryCard(
    gpsAccentColor: Color,
    gpsValueText: String,
    gpsStatusSub: String,
    hasFix: Boolean,
    gpsPulseAlpha: Float,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(gpsAccentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "GPS Status",
                    tint = gpsAccentColor,
                    modifier = Modifier
                        .size(13.dp)
                        .alpha(if (!hasFix) gpsPulseAlpha else 1f)
                )
            }
            Spacer(modifier = Modifier.width(4.5.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(gpsAccentColor)
                            .alpha(if (hasFix) gpsPulseAlpha else 0.5f)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = gpsValueText,
                        color = mainTextColor,
                        fontSize = 11.5.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = gpsStatusSub,
                    color = gpsAccentColor,
                    fontSize = 8.8.sp,
                    lineHeight = 10.sp,
                    letterSpacing = (-0.15).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun SatelliteTelemetryCard(
    satellitesCount: Int,
    hasFix: Boolean,
    satAccent: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(satAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SatelliteAlt,
                    contentDescription = "Satellites",
                    tint = satAccent,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.5.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (hasFix) "$satellitesCount" else "--",
                    color = mainTextColor,
                    fontSize = 11.5.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "Satellites",
                    color = subTextColor,
                    fontSize = 8.8.sp,
                    lineHeight = 10.sp,
                    letterSpacing = (-0.15).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun SpeedTelemetryCard(
    speedKmh: Float,
    speedAccent: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmh.coerceAtLeast(0f),
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "speed_anim"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(speedAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Speed",
                    tint = speedAccent,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.5.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format(Locale.US, "%.0f", animatedSpeed),
                    color = mainTextColor,
                    fontSize = 11.5.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "km/h",
                    color = subTextColor,
                    fontSize = 8.8.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun CompassTelemetryCard(
    compassHeadingStr: String,
    currentThemeMode: String,
    compassAccent: Color,
    onToggleTheme: () -> Unit,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleTheme() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(23.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(compassAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CompassCalibration,
                        contentDescription = "Compass",
                        tint = compassAccent,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.5.dp))
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = compassHeadingStr,
                        color = mainTextColor,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Compass",
                        color = subTextColor,
                        fontSize = 8.8.sp,
                        lineHeight = 10.sp,
                        letterSpacing = (-0.15).sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Icon(
                imageVector = when (currentThemeMode) {
                    "light" -> Icons.Default.LightMode
                    "dark" -> Icons.Default.DarkMode
                    else -> Icons.Default.Palette
                },
                contentDescription = "Toggle Theme",
                tint = subTextColor,
                modifier = Modifier.size(13.5.dp)
            )
        }
    }
}

// -------------------------------------------------------------------
// COMPONENT 2: HERO CHAINAGE DISPLAY (LARGEST & MOST PROMINENT ELEMENT)
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
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false,
    onClick: () -> Unit = {},
    onToggleSide: (() -> Unit)? = null
) {
    val kmBadgeBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.45f) else Color(0xFFEFF6FF)
    val kmBadgeBorder = if (isDark) Color(0xFF3B82F6).copy(alpha = 0.4f) else Color(0xFFBFDBFE)
    val kmBadgeText = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)

    val isLhs = detectedSide.equals("LHS", ignoreCase = true)
    val isRhs = detectedSide.equals("RHS", ignoreCase = true)

    val sideBadgeBg by animateColorAsState(
        targetValue = when {
            isLhs -> if (isDark) Color(0xFF064E3B).copy(alpha = 0.55f) else Color(0xFFECFDF5)
            isRhs -> if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.55f) else Color(0xFFEFF6FF)
            else -> if (isDark) Color(0xFF4C1D95).copy(alpha = 0.55f) else Color(0xFFF3E8FF)
        },
        animationSpec = tween(220),
        label = "side_badge_bg"
    )
    val sideBadgeBorder by animateColorAsState(
        targetValue = when {
            isLhs -> if (isDark) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFFA7F3D0)
            isRhs -> if (isDark) Color(0xFF3B82F6).copy(alpha = 0.45f) else Color(0xFFBFDBFE)
            else -> if (isDark) Color(0xFF8B5CF6).copy(alpha = 0.45f) else Color(0xFFDDD6FE)
        },
        animationSpec = tween(220),
        label = "side_badge_border"
    )
    val sideBadgeText by animateColorAsState(
        targetValue = when {
            isLhs -> if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
            isRhs -> if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
            else -> if (isDark) Color(0xFFC084FC) else Color(0xFF7E22CE)
        },
        animationSpec = tween(220),
        label = "side_badge_text"
    )

    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)

    val borderStroke = when (source) {
        ChainageSource.MANUAL -> BorderStroke(1.5.dp, AccentRed)
        ChainageSource.CUSTOM_CORRECTION -> BorderStroke(1.5.dp, if (isDark) Color(0xFFA855F7) else Color(0xFF7C3AED))
        else -> BorderStroke(1.dp, cardBorderColor)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBgColor,
        border = borderStroke,
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
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
                        // Left: KM Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = kmBadgeBg,
                            border = BorderStroke(0.8.dp, kmBadgeBorder)
                        ) {
                            Text(
                                text = "KM",
                                color = kmBadgeText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                            )
                        }

                        // Right: LHS / RHS / BHS Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = sideBadgeBg,
                            border = BorderStroke(0.8.dp, sideBadgeBorder),
                            modifier = Modifier.then(
                                if (onToggleSide != null) Modifier.clickable { onToggleSide() } else Modifier
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
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
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = kmBadgeBg,
                            border = BorderStroke(0.9.dp, kmBadgeBorder),
                            modifier = Modifier.padding(end = if (containerWidth < 360.dp) 4.dp else 8.dp)
                        ) {
                            Text(
                                text = "KM",
                                color = kmBadgeText,
                                fontSize = if (containerWidth < 360.dp) 11.sp else 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(
                                    horizontal = if (containerWidth < 360.dp) 6.dp else 8.dp,
                                    vertical = if (containerWidth < 360.dp) 3.dp else 4.dp
                                )
                            )
                        }

                        OdometerChainageRow(
                            chainageText = chainageText,
                            mainTextColor = mainTextColor,
                            digitFontSize = digitFontSize,
                            isDark = isDark
                        )

                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = sideBadgeBg,
                            border = BorderStroke(0.9.dp, sideBadgeBorder),
                            modifier = Modifier
                                .padding(start = if (containerWidth < 360.dp) 4.dp else 8.dp)
                                .then(if (onToggleSide != null) Modifier.clickable { onToggleSide() } else Modifier)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(
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

                Spacer(modifier = Modifier.height(if (isCompact) 3.dp else 5.dp))

                // Responsive Telemetry Metadata Strip
                val sourceLabel = when (source) {
                    ChainageSource.MANUAL -> "MANUAL"
                    ChainageSource.CUSTOM_CORRECTION -> if (rawChainage != null) "CUSTOM CORRECTION ($rawChainage)" else "CUSTOM CORRECTION"
                    ChainageSource.KMZ -> "KMZ"
                    ChainageSource.GPS -> "GPS"
                }

                val (confText, confCol) = when (confidence) {
                    MatchConfidence.HIGH -> "Good" to (if (isDark) Color(0xFF34D399) else Color(0xFF059669))
                    MatchConfidence.MEDIUM -> "Moderate" to (if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB))
                    MatchConfidence.LOW -> "Poor" to AccentRed
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

    val activeAccent = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB)

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
// COMPONENT 3: PROJECT & STRETCH CARDS (ROW OF 2 CARDS WITH EDIT INDICATOR)
// -------------------------------------------------------------------
@Composable
fun ProjectStretchCardsRow(
    projectName: String,
    stretchName: String,
    onEdit: () -> Unit,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false
) {
    val projectAccent = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val stretchAccent = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
    val cardHeight = if (isCompact) 52.dp else 56.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Project Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cardBgColor,
            border = BorderStroke(1.dp, cardBorderColor),
            shadowElevation = if (isDark) 0.dp else 1.dp,
            modifier = Modifier
                .weight(1f)
                .height(cardHeight)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onEdit() },
                        onLongPress = { onEdit() }
                    )
                }
                .testTag("home_card_project")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Left Icon Badge
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(projectAccent.copy(alpha = 0.12f))
                        .align(Alignment.CenterStart),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Engineering,
                        contentDescription = null,
                        tint = projectAccent,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Centered Content Column (Smart Dynamic Font Sizing & Center Alignment)
                val pText = projectName.ifBlank { "Set Project" }
                val pFontSize = when {
                    pText.length > 20 -> 10.5.sp
                    pText.length > 14 -> 11.5.sp
                    else -> 12.5.sp
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 22.dp)
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PROJECT",
                        color = subTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = pText,
                        color = if (projectName.isNotBlank()) mainTextColor else subTextColor,
                        fontSize = pFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = (pFontSize.value + 2).sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right Edit Pencil Badge
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(subTextColor.copy(alpha = 0.08f))
                        .align(Alignment.CenterEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Project",
                        tint = subTextColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        // Stretch Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cardBgColor,
            border = BorderStroke(1.dp, cardBorderColor),
            shadowElevation = if (isDark) 0.dp else 1.dp,
            modifier = Modifier
                .weight(1f)
                .height(cardHeight)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onEdit() },
                        onLongPress = { onEdit() }
                    )
                }
                .testTag("home_card_stretch")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Left Icon Badge
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(stretchAccent.copy(alpha = 0.12f))
                        .align(Alignment.CenterStart),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = null,
                        tint = stretchAccent,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Centered Content Column (Smart Dynamic Font Sizing & Center Alignment)
                val sText = stretchName.ifBlank { "Set Stretch" }
                val sFontSize = when {
                    sText.length > 20 -> 10.5.sp
                    sText.length > 14 -> 11.5.sp
                    else -> 12.5.sp
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 22.dp)
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "STRETCH",
                        color = subTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = sText,
                        color = if (stretchName.isNotBlank()) mainTextColor else subTextColor,
                        fontSize = sFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = (sFontSize.value + 2).sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right Edit Pencil Badge
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(subTextColor.copy(alpha = 0.08f))
                        .align(Alignment.CenterEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Stretch",
                        tint = subTextColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------
// EDIT PROJECT & STRETCH MODAL DIALOG
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
    val dialogColors = com.example.ui.theme.rememberDialogColors()
    val dialogAccent = if (dialogColors.isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogColors.shape,
        containerColor = dialogColors.containerColor,
        modifier = Modifier.imePadding(),
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(dialogAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = dialogAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        title = {
            Text(
                text = "Edit Project & Stretch",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = dialogColors.titleColor
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Update project information for camera watermark and documentation:",
                    fontSize = 12.sp,
                    color = dialogColors.textSecondaryColor
                )

                OutlinedTextField(
                    value = project,
                    onValueChange = { project = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g. NH-44, Express Corridor") },
                    singleLine = true,
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = dialogColors.textColor,
                        unfocusedTextColor = dialogColors.textColor,
                        focusedBorderColor = dialogAccent,
                        unfocusedBorderColor = dialogColors.inputBorder,
                        focusedLabelColor = dialogAccent,
                        unfocusedLabelColor = dialogColors.textSecondaryColor,
                        cursorColor = dialogAccent
                    )
                )

                OutlinedTextField(
                    value = stretch,
                    onValueChange = { stretch = it },
                    label = { Text("Stretch") },
                    placeholder = { Text("e.g. MM, Pkg-1, Section-A") },
                    singleLine = true,
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = dialogColors.textColor,
                        unfocusedTextColor = dialogColors.textColor,
                        focusedBorderColor = dialogAccent,
                        unfocusedBorderColor = dialogColors.inputBorder,
                        focusedLabelColor = dialogAccent,
                        unfocusedLabelColor = dialogColors.textSecondaryColor,
                        cursorColor = dialogAccent
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = dialogAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_project_stretch_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = dialogColors.textSecondaryColor)
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
        isAutoDetectedSide: Boolean = false
    ): NextKmStoneState {
        val rawSide = detectedSide.trim().uppercase(Locale.ROOT)
        val validInputSide = if (rawSide == "LHS" || rawSide == "RHS") rawSide else null
        val fallbackSide = validInputSide ?: confirmedSide ?: "RHS"
        val fallbackArrow = if (fallbackSide == "LHS") "↑" else "↓"

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

        // Resolve effective side with hysteresis against minor GPS jitter when auto-detected
        val prevConfirmedSide = confirmedSide
        val effectiveSide = if (!isAutoDetectedSide || prevConfirmedSide == null) {
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

        val sideChanged = prevConfirmedSide != null && prevConfirmedSide != effectiveSide
        if (sideChanged) {
            lastTargetStoneMetres = null
            lastPassedStoneMetres = null
        }

        val kmStones = getOrExtractKmStones(route)
        val candidate = resolveCandidateStone(curM, effectiveSide, kmStones)

        var targetStoneM = candidate.targetMetres
        var passedStoneM = candidate.passedMetres
        var intervalM = candidate.intervalMetres

        // Boundary hysteresis: when the user has just crossed a KM Stone in the travel direction,
        // prevent 1-3 m backwards GPS jitter from flickering back to the just-passed stone.
        val prevTargetM = lastTargetStoneMetres
        val prevPassedM = lastPassedStoneMetres
        if (!sideChanged && prevTargetM != null && prevPassedM != null && targetStoneM != prevTargetM) {
            if (effectiveSide == "LHS") {
                // On LHS (increasing), if we already advanced past prevPassedM to prevTargetM
                // and curM jittered slightly below prevPassedM within boundaryHysteresisMetres:
                if (targetStoneM == prevPassedM && curM < prevPassedM && (prevPassedM - curM) <= boundaryHysteresisMetres) {
                    targetStoneM = prevTargetM
                    passedStoneM = prevPassedM
                    intervalM = lastIntervalMetres
                }
            } else {
                // On RHS (decreasing), if we already advanced below prevPassedM to prevTargetM
                // and curM jittered slightly above prevPassedM within boundaryHysteresisMetres:
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

        val rawDistanceMetres = if (effectiveSide == "LHS") {
            (targetStoneM - curM).coerceAtLeast(0L)
        } else {
            (curM - targetStoneM).coerceAtLeast(0L)
        }

        // If holding across a tiny 1-3 m boundary hysteresis jitter, clamp displayed distance to the interval
        val effectiveDistanceMetres = if (targetStoneM == prevTargetM && prevPassedM != null &&
            ((effectiveSide == "LHS" && curM < prevPassedM) || (effectiveSide == "RHS" && curM > prevPassedM))
        ) {
            rawDistanceMetres.coerceAtMost(intervalM)
        } else {
            rawDistanceMetres
        }

        val nextChainageFormatted = com.example.model.Chainage.fromMetres(targetStoneM).format()
        val distanceText = formatDistanceAway(effectiveDistanceMetres)
        val directionArrow = if (effectiveSide == "LHS") "↑" else "↓"

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
        side: String,
        kmStones: List<KmStoneReference>
    ): CandidateResolution {
        if (side == "LHS") {
            // LHS - Increasing Chainage: find next higher KM stone (> curM)
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
            // RHS - Decreasing Chainage: find next lower KM stone (< curM)
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

    val isLhs = detectedSide.equals("LHS", ignoreCase = true)
    val labelColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val glassSurfaceColor = if (isDark) {
        cardBgColor.copy(alpha = 0.88f)
    } else {
        cardBgColor.copy(alpha = 0.96f)
    }
    val iconBoxBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.35f) else Color(0xFFEFF6FF)
    val sideBadgeBg = when {
        !isReliable -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        isLhs -> if (isDark) Color(0xFF064E3B).copy(alpha = 0.55f) else Color(0xFFECFDF5)
        else -> if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.55f) else Color(0xFFEFF6FF)
    }
    val sideBadgeBorder = when {
        !isReliable -> cardBorderColor
        isLhs -> if (isDark) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFF6EE7B7)
        else -> if (isDark) Color(0xFF60A5FA).copy(alpha = 0.45f) else Color(0xFF93C5FD)
    }
    val sideBadgeText = when {
        !isReliable -> subTextColor
        isLhs -> if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
        else -> if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
    }
    val distancePillBg = if (isReliable) {
        if (isDark) Color(0xFF2563EB) else Color(0xFF2563EB)
    } else {
        if (isDark) Color(0xFF334155) else Color(0xFF64748B)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "km_badge_pulse")
    val badgePulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isReliable) 0.88f else 0.95f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgePulse"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = glassSurfaceColor,
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier
            .height(if (isCompact) 50.dp else 54.dp)
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
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = iconBoxBg,
                        modifier = Modifier.size(if (isCompact || isTightRow) 32.dp else 38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            KmStoneIcon(sizeDp = if (isCompact || isTightRow) 24.dp else 30.dp, isDark = isDark)
                        }
                    }

                    Spacer(modifier = Modifier.width(if (isTightRow) 7.dp else 10.dp))

                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = "NEXT KM STONE",
                            color = labelColor,
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
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
                                    letterSpacing = 0.3.sp,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("next_km_stone_value")
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = sideBadgeBg,
                                border = BorderStroke(0.8.dp, sideBadgeBorder),
                                modifier = Modifier.testTag("next_km_stone_side")
                            ) {
                                Text(
                                    text = "$directionArrow ${detectedSide.uppercase(Locale.ROOT)}".trim(),
                                    color = sideBadgeText,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                }

                // Subtle separator bar (`|`) + Distance Away pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(if (isCompact) 22.dp else 26.dp)
                            .background(cardBorderColor.copy(alpha = 0.65f))
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = distancePillBg.copy(alpha = badgePulseAlpha),
                        shadowElevation = 1.dp,
                        modifier = Modifier.testTag("next_km_stone_distance")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = animatedDistanceLabel,
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Sleek Bottom Progress Bar toward Next KM Stone
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.2.dp)
                    .background(cardBorderColor.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(2.2.dp)
                        .background(labelColor.copy(alpha = 0.85f))
                )
            }
        }
    }
}

// -------------------------------------------------------------------
// COMPONENT 5: TOOLS & DPR NOTES QUICK ACTION BUTTONS
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
    val surveyAccent = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
    val toolsAccent = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val notesAccent = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
    val titleColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val btnHeight = if (isCompact) 40.dp else 44.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Survey Card Button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cardBgColor,
            border = BorderStroke(1.dp, cardBorderColor),
            shadowElevation = if (isDark) 0.dp else 1.dp,
            modifier = Modifier
                .weight(1f)
                .height(btnHeight)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onNavigateToSurvey() }
                .testTag("home_btn_survey")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(surveyAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Engineering,
                            contentDescription = "Survey",
                            tint = surveyAccent,
                            modifier = Modifier.size(14.5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Survey",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Tools Card Button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cardBgColor,
            border = BorderStroke(1.dp, cardBorderColor),
            shadowElevation = if (isDark) 0.dp else 1.dp,
            modifier = Modifier
                .weight(1f)
                .height(btnHeight)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onNavigateToTools() }
                .testTag("home_btn_tools")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(toolsAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Tools",
                            tint = toolsAccent,
                            modifier = Modifier.size(14.5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tools",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // DPR Notes Card Button
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cardBgColor,
            border = BorderStroke(1.dp, cardBorderColor),
            shadowElevation = if (isDark) 0.dp else 1.dp,
            modifier = Modifier
                .weight(1f)
                .height(btnHeight)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onNavigateToNotes() }
                .testTag("home_btn_notes")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(notesAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Notes",
                            tint = notesAccent,
                            modifier = Modifier.size(14.5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Notes",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileUpdate by OsmTileManager.tileUpdateEvent.collectAsStateWithLifecycle()
    val isTileLoading by OsmTileManager.isLoading.collectAsStateWithLifecycle()
    val isAppDark = com.example.ui.theme.isAppDarkTheme()
    val isDark = com.example.ui.map.MapThemeConfig.resolveIsDark(mapThemePreference, isAppDark)
    val mapTheme = com.example.ui.map.rememberMapThemeColors(isDark)

    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var viewRangeMetres by remember { mutableDoubleStateOf(1000.0) }
    var isFollowingUser by remember { mutableStateOf(true) }

    LaunchedEffect(sharedZoomLevel) {
        if (sharedZoomLevel != null) {
            viewRangeMetres = when {
                sharedZoomLevel >= 17.8f -> 250.0
                sharedZoomLevel >= 16.8f -> 550.0
                sharedZoomLevel >= 15.8f -> 1000.0
                sharedZoomLevel >= 14.8f -> 2200.0
                sharedZoomLevel >= 13.8f -> 4500.0
                sharedZoomLevel >= 12.8f -> 8500.0
                else -> 13000.0
            }
        }
    }

    LaunchedEffect(sharedRecenterEpoch) {
        if (sharedRecenterEpoch > 0L) {
            isFollowingUser = true
            panOffsetX = 0f
            panOffsetY = 0f
        }
    }

    LaunchedEffect(location, isFollowingUser) {
        if (isFollowingUser) {
            panOffsetX = 0f
            panOffsetY = 0f
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "map_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val currentZoom = remember(viewRangeMetres) {
        when {
            viewRangeMetres <= 300.0 -> 18
            viewRangeMetres <= 650.0 -> 17
            viewRangeMetres <= 1300.0 -> 16
            viewRangeMetres <= 2800.0 -> 15
            viewRangeMetres <= 5500.0 -> 14
            viewRangeMetres <= 10000.0 -> 13
            else -> 12
        }
    }

    val controlSurfaceColor = if (mapTheme.isDark) Color(0xE60F172A) else Color(0xF5FFFFFF)
    val controlBorderColor = if (mapTheme.isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    val controlIconColor = if (mapTheme.isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val activeControlBg = Color(0xFF2563EB)

    // Smooth shortest-path angular unwrap for compass rotation
    var unwrappedHeading by remember { mutableFloatStateOf(compassHeading) }
    LaunchedEffect(compassHeading) {
        var delta = (compassHeading - unwrappedHeading) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        unwrappedHeading += delta
    }
    val animatedCompassHeading by animateFloatAsState(
        targetValue = unwrappedHeading,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "map_compass_rotation"
    )

    // Shared map orientation mode rotation (North Up / Direction Up / Auto)
    val anchorForOrientation = location ?: routeData?.bounds?.center ?: GeoPoint(28.6139, 77.2090)
    val effectiveMapBearing = remember(anchorForOrientation, bearing, routeData?.id) {
        MiniMapRenderer.resolveEffectiveBearing(anchorForOrientation, bearing, routeData)
    }
    val rawTargetMapRotation = remember(screensaverSettings.miniMapOrientation, effectiveMapBearing) {
        MiniMapRenderer.resolveMapRotationDegrees(
            mode = screensaverSettings.miniMapOrientation,
            effectiveBearing = effectiveMapBearing
        )
    }
    var unwrappedMapRotation by remember { mutableFloatStateOf(rawTargetMapRotation) }
    LaunchedEffect(rawTargetMapRotation) {
        var delta = (rawTargetMapRotation - unwrappedMapRotation) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        unwrappedMapRotation += delta
    }
    val animatedMapRotation by animateFloatAsState(
        targetValue = unwrappedMapRotation,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "home_shared_map_rotation"
    )

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        isFollowingUser = false
                        viewRangeMetres = (viewRangeMetres / zoom).coerceIn(100.0, 15000.0)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
        ) {
            // Read tileUpdate so Canvas recomposes when tiles finish loading
            val _tick = tileUpdate
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            val anchorGeo = location ?: routeData?.bounds?.center ?: GeoPoint(28.6139, 77.2090)

            var hasOsmTiles = false
            val osmBmp = OsmTileManager.renderMapBitmap(
                context = context,
                center = anchorGeo,
                widthPx = w.toInt().coerceAtLeast(1),
                heightPx = h.toInt().coerceAtLeast(1),
                zoom = currentZoom,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                darkTheme = mapTheme.isDark,
                mapType = mapTypePreference
            )

            drawRect(color = mapTheme.mapBackground)

            rotate(degrees = animatedMapRotation, pivot = Offset(cx, cy)) {
                if (osmBmp != null) {
                    drawImage(
                        image = osmBmp.asImageBitmap(),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w.toInt(), h.toInt())
                    )
                    hasOsmTiles = true
                }

                if (!hasOsmTiles) {
                    val gridSpacing = 72.dp.toPx()
                    var gx = 0f
                    while (gx < w) {
                        drawLine(
                            color = mapTheme.gridLineColor,
                            start = Offset(gx, 0f),
                            end = Offset(gx, h),
                            strokeWidth = 1f
                        )
                        gx += gridSpacing
                    }
                    var gy = 0f
                    while (gy < h) {
                        drawLine(
                            color = mapTheme.gridLineColor,
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1f
                        )
                        gy += gridSpacing
                    }
                }

                fun toScreen(pt: GeoPoint): Offset {
                    val (sx, sy) = OsmTileManager.geoToScreen(
                        pt = pt,
                        center = anchorGeo,
                        widthPx = w,
                        heightPx = h,
                        zoom = currentZoom,
                        panOffsetX = panOffsetX,
                        panOffsetY = panOffsetY
                    )
                    return Offset(sx, sy)
                }

                // High-Contrast Highway Route Polylines (synchronized with shared Mini Map alignment visibility)
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

                        // Soft outer glow for clear distinction from surrounding roads
                        drawPath(
                            path = path,
                            color = Color(0xFF2563EB).copy(alpha = 0.22f),
                            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // Outer high-contrast casing
                        drawPath(
                            path = path,
                            color = if (mapTheme.isDark) Color(0xFF0F172A) else Color(0xFF1E3A8A),
                            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // Inner vibrant alignment line
                        drawPath(
                            path = path,
                            color = if (mapTheme.isDark) Color(0xFF38BDF8) else Color(0xFF3B82F6),
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                // Route Chainage Markers
                if (screensaverSettings.miniMapShowRouteAlignment && routeData != null) {
                    for (marker in routeData.markers) {
                        val mOff = toScreen(marker.point)
                        if (mOff.x in -100f..(w + 100f) && mOff.y in -100f..(h + 100f)) {
                            drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = mOff)
                            drawCircle(
                                color = Color(0xFF2563EB),
                                radius = 4.dp.toPx(),
                                center = mOff
                            )
                        }
                    }
                }

                // Live GPS Location Marker synchronized with shared marker style
                if (location != null) {
                    val userOff = toScreen(location)

                    // Pulsing GPS Accuracy Halo
                    drawCircle(
                        color = Color(0xFFEF4444).copy(alpha = pulseAlpha * 0.35f),
                        radius = 26.dp.toPx(),
                        center = userOff
                    )

                    val activeHeading = bearing ?: animatedCompassHeading
                    when (screensaverSettings.miniMapMarkerStyle) {
                        HudMapMarkerStyle.NAVIGATION_ARROW -> {
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
                border = BorderStroke(1.dp, controlBorderColor),
                shadowElevation = 3.dp
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

        // Map Attribution Badge (Bottom-Left)
        Surface(
            shape = RoundedCornerShape(topEnd = 8.dp),
            color = controlSurfaceColor.copy(alpha = 0.88f),
            border = BorderStroke(0.6.dp, controlBorderColor),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = if (mapTypePreference.equals("satellite", ignoreCase = true)) OsmTileManager.ATTRIBUTION_SATELLITE else OsmTileManager.ATTRIBUTION_STANDARD,
                color = if (mapTheme.isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
            )
        }

        // Left Map Action Controls (Aligned Vertically)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // 1. Map Type Switcher (Standard <-> Satellite)
            val isSatellite = mapTypePreference.equals("satellite", ignoreCase = true)
            Surface(
                shape = CircleShape,
                color = if (isSatellite) activeControlBg else controlSurfaceColor,
                border = BorderStroke(1.dp, if (isSatellite) activeControlBg else controlBorderColor),
                shadowElevation = 2.dp,
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
                border = BorderStroke(1.dp, controlBorderColor),
                shadowElevation = 2.dp,
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
                border = BorderStroke(1.dp, controlBorderColor),
                shadowElevation = 2.dp,
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
                border = BorderStroke(1.dp, if (isFollowingUser) activeControlBg else controlBorderColor),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable {
                        isFollowingUser = true
                        panOffsetX = 0f
                        panOffsetY = 0f
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

        // Top-Right Modern Dual-Ring Precision Compass Instrument Over the Map
        val headingParts = remember(compassHeadingStr) {
            val tokens = compassHeadingStr.trim().split("\\s+".toRegex())
            val deg = tokens.firstOrNull().orEmpty().ifBlank { "0°" }
            val dir = tokens.getOrNull(1).orEmpty().ifBlank { "N" }
            deg to dir
        }
        val innerHubBg = if (mapTheme.isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)
        val cardinalColor = if (headingParts.second.startsWith("N", ignoreCase = true)) {
            Color(0xFFEF4444)
        } else if (mapTheme.isDark) {
            Color(0xFF38BDF8)
        } else {
            Color(0xFF2563EB)
        }

        Surface(
            shape = CircleShape,
            color = controlSurfaceColor,
            border = BorderStroke(1.dp, controlBorderColor),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(54.dp)
                .clip(CircleShape)
                .clickable {
                    isFollowingUser = true
                    panOffsetX = 0f
                    panOffsetY = 0f
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

        // Right-Side Zoom Controls (+ / -)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = controlSurfaceColor,
            border = BorderStroke(1.dp, controlBorderColor),
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        viewRangeMetres = (viewRangeMetres * 0.7).coerceAtLeast(100.0)
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
                        .width(22.dp)
                        .height(1.dp)
                        .background(controlBorderColor)
                )
                IconButton(
                    onClick = {
                        viewRangeMetres = (viewRangeMetres * 1.4).coerceAtMost(15000.0)
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

        // Bottom-Right Dynamic Scale Badge
        val dynamicScaleMetres = remember(viewRangeMetres) {
            when {
                viewRangeMetres <= 300.0 -> 25
                viewRangeMetres <= 650.0 -> 50
                viewRangeMetres <= 1300.0 -> 100
                viewRangeMetres <= 2800.0 -> 200
                viewRangeMetres <= 5500.0 -> 500
                else -> 1000
            }
        }
        val scaleText = if (dynamicScaleMetres >= 1000) "${dynamicScaleMetres / 1000} km" else "$dynamicScaleMetres m"

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = controlSurfaceColor,
            border = BorderStroke(0.8.dp, controlBorderColor),
            shadowElevation = 1.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp, end = 10.dp)
        ) {
            Text(
                text = scaleText,
                color = controlIconColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
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
    val directionArrow: String, // "↑" for LHS, "↓" for RHS, "" when null
    val isLiveGpsValid: Boolean,
    val isIncreasingTrend: Boolean = true,
    val locationName: String = "",
    val speedText: String = "-- km/h",
    val speedKmh: Int? = null
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
            val sideIfReliable = if (
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
            return ScreensaverDisplayState(
                chainageText = manualText ?: lastValidChainageText ?: "--+---",
                reliableSide = sideIfReliable,
                directionArrow = sideArrowFor(sideIfReliable),
                isLiveGpsValid = hasGpsFix && route != null,
                isIncreasingTrend = increasing,
                locationName = resolvedLocationName,
                speedText = formattedSpeed,
                speedKmh = resolvedSpeed
            )
        }

        val isReliableLiveMatch = route != null &&
            hasGpsFix &&
            matchResult.chainage != null &&
            matchResult.confidence != MatchConfidence.NO_MATCH &&
            !matchResult.isUncertain &&
            !(matchResult.confidence == MatchConfidence.LOW && matchResult.offsetMetres > 80.0)

        if (isReliableLiveMatch) {
            val metres = matchResult.chainage!!.metres
            val text = matchResult.displayChainage
            val increasing = lastValidChainageMetres?.let { metres >= it } ?: true
            lastValidChainageText = text
            lastValidChainageMetres = metres

            val reliableSide = normalizeBinarySide(matchResult.detectedSide)
                ?: normalizeBinarySide(projectRoadSide.name)

            return ScreensaverDisplayState(
                chainageText = text,
                reliableSide = reliableSide,
                directionArrow = sideArrowFor(reliableSide),
                isLiveGpsValid = true,
                isIncreasingTrend = increasing,
                locationName = resolvedLocationName,
                speedText = formattedSpeed,
                speedKmh = resolvedSpeed
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
            speedKmh = resolvedSpeed
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

/**
 * Coordinates the configurable inactivity countdown for the Home Page, tracks foreground
 * lifecycle, detects pointer/key activity cleanly without consuming child gestures, blocks while
 * dialogs/keyboard are active, respects Master/Auto-Activate/Touch-to-Dismiss/Auto-Restart toggles,
 * and hosts the true immersive edge-to-edge Dialog without disturbing Home Page scroll position or state.
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
                    hasDismissedInSession = false
                    interactionEpoch += 1L
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isLifecycleResumed = false
                    onDismissScreensaver()
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

    // Dismiss immediately if disabled, dialog opens, keyboard opens, or background transition occurs
    LaunchedEffect(canAllowWhenVisible, isScreensaverVisible) {
        if (!canAllowWhenVisible && isScreensaverVisible) {
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

    val latestTouchToDismiss by rememberUpdatedState(touchToDismiss)
    val latestOnDismissScreensaver by rememberUpdatedState(onDismissScreensaver)

    if (isScreensaverVisible && canAllowWhenVisible) {
        val hostActivityWindow = remember(context) { context.findHostActivityWindow() }
        Dialog(
            onDismissRequest = {
                notifyUserInteraction(force = true)
                hasDismissedInSession = true
                latestOnDismissScreensaver()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = latestTouchToDismiss
            )
        ) {
            val localView = LocalView.current
            val dialogWindow = (localView.parent as? DialogWindowProvider)?.window
            DisposableEffect(dialogWindow, localView, hostActivityWindow) {
                if (dialogWindow != null) {
                    dialogWindow.setDimAmount(0f)
                    dialogWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                }
                applyScreensaverImmersiveMode(
                    dialogWindow = dialogWindow,
                    dialogView = localView,
                    hostActivityWindow = hostActivityWindow,
                    enable = true
                )
                val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                    if (hasFocus) {
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
                if (latestTouchToDismiss) {
                    notifyUserInteraction(force = true)
                    hasDismissedInSession = true
                    latestOnDismissScreensaver()
                }
            }
        }
    }
}

/**
 * Full-Screen Automotive HUD & Chainage Screensaver Overlay.
 * Minimal HUD layout featuring only a compact Mirror/Normal quick-switch toggle in a safe corner.
 * Tapping anywhere outside the quick toggle dismisses immediately when Touch-to-Dismiss is active.
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
    onToggleMirrorQuickMode: ((enableMirror: Boolean, isCurrentlyLandscape: Boolean) -> Unit)? = null,
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
    val hardwareSensorPosture = rememberScreensaverSensorPosture(
        enabled = resolvedSettings.autoOrientationEnabled && sensorPostureOverride == null
    )
    val activeSensorPosture = sensorPostureOverride ?: hardwareSensorPosture

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
            .pointerInput(resolvedSettings.touchToDismiss) {
                if (!resolvedSettings.touchToDismiss) return@pointerInput
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
                if (resolvedSettings.touchToDismiss) {
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
            onToggleMirrorQuickMode = handleQuickToggle
        )
    }
}
