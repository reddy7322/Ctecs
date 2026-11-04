package com.example.ui.home

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.OsmTileManager
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteData
import com.example.ui.MainViewModel
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val matchResult by viewModel.liveMatchResult.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()
    val compassState by viewModel.compassState.collectAsStateWithLifecycle()

    val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val mapThemePreference by viewModel.mapThemePreference.collectAsStateWithLifecycle()
    val mapTypePreference by viewModel.mapTypePreference.collectAsStateWithLifecycle()

    val isDark = com.example.ui.theme.isAppDarkTheme()

    // Premium Minimalist Light Theme vs Dark Theme Palette
    val pageBgColor = if (isDark) Color(0xFF0A0E17) else Color(0xFFF3F6FA)
    val cardBgColor = if (isDark) Color(0xFF131B2E) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDark) Color(0xFF263554) else Color(0xFFE2E8F0)
    val mainTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Pulse transition for live GPS status dot
    val infiniteTransition = rememberInfiniteTransition(label = "gps_dot")
    val gpsPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(750), repeatMode = RepeatMode.Reverse),
        label = "gps_dot_pulse"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(pageBgColor)
    ) {
        val maxH = maxHeight
        val isCompactHeight = maxH < 690.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (isCompactHeight) 3.dp else 6.dp,
                    bottom = if (isCompactHeight) 2.dp else 4.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 5.dp else 7.dp)
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
                activeRoute == null -> "NO KMZ"
                locationState.point == null -> "--+---"
                matchResult.chainage != null -> matchResult.displayChainage
                else -> "--+---"
            }

            val detectedSide = when {
                activeRoute == null -> "Load Route"
                locationState.point == null -> "Searching"
                matchResult.detectedSide.isNotBlank() -> matchResult.detectedSide.uppercase(Locale.ROOT)
                else -> "RHS"
            }

            CentralChainageDisplay(
                chainageText = displayChainageText,
                detectedSide = detectedSide,
                confidence = matchResult.confidence,
                offsetMetres = matchResult.offsetMetres,
                hasActiveRoute = activeRoute != null,
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor,
                isDark = isDark,
                isCompact = isCompactHeight
            )

            // 3. Project & Stretch Information Cards
            var showEditProjectStretchDialog by remember { mutableStateOf(false) }

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

            // 4. Interactive Smart KMZ Map - Dynamic Height Priority (weight 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, cardBorderColor, RoundedCornerShape(14.dp))
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
                    mapThemePreference = mapThemePreference,
                    mapTypePreference = mapTypePreference,
                    onToggleMapTheme = {
                        val next = when (mapThemePreference) {
                            "system" -> "light"
                            "light" -> "dark"
                            else -> "system"
                        }
                        viewModel.setMapTheme(next)
                        viewModel.showToast("Map Theme: ${next.uppercase(Locale.ROOT)}")
                    },
                    onToggleMapType = {
                        val next = if (mapTypePreference == "satellite") "standard" else "satellite"
                        viewModel.setMapType(next)
                        viewModel.showToast(if (next == "satellite") "Satellite Imagery" else "Standard Map")
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 5. Prominent Next KM Stone Card
            val curChainage = matchResult.chainage
            val (nextKmStone, nextKmDistance) = remember(curChainage) {
                if (curChainage != null) {
                    val curM = curChainage.metres
                    val nextKm = ((curM / 1000) + 1) * 1000
                    val dist = (nextKm - curM).coerceAtLeast(1)
                    val kmLabel = com.example.model.Chainage.fromMetres(nextKm).format()
                    val distLabel = "${dist}m away"
                    kmLabel to distLabel
                } else {
                    "181+000" to "12m away"
                }
            }

            NextChainageCard(
                nextChainage = nextKmStone,
                distanceText = nextKmDistance,
                detectedSide = detectedSide,
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor,
                isDark = isDark,
                isCompact = isCompactHeight
            )

            // 6. Tools & DPR Notes Quick Action Buttons
            HomeQuickActionButtons(
                onNavigateToTools = onNavigateToTools,
                onNavigateToNotes = onNavigateToNotes,
                onNavigateToCamera = onNavigateToCamera,
                onNavigateToPhotos = onNavigateToPhotos,
                onNavigateToRoutes = onNavigateToRoutes,
                onNavigateToSettings = onNavigateToSettings,
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                isDark = isDark,
                isCompact = isCompactHeight
            )

            // Bottom Spacing so nothing is hidden behind the Floating Bottom Navigation Bar
            Spacer(modifier = Modifier.height(if (isCompactHeight) 52.dp else 58.dp))
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

    val (gpsAccentColor, gpsValueText, gpsStatusSub) = when {
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

    val satAccent = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val speedAccent = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
    val compassAccent = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isVeryNarrow = maxWidth < 330.dp

        if (isVeryNarrow) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    modifier = Modifier.weight(1.05f).height(cardHeight)
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
                    modifier = Modifier.weight(0.95f).height(cardHeight)
                )
                SpeedTelemetryCard(
                    speedKmh = speedKmh,
                    speedAccent = speedAccent,
                    cardBgColor = cardBgColor,
                    cardBorderColor = cardBorderColor,
                    mainTextColor = mainTextColor,
                    subTextColor = subTextColor,
                    isDark = isDark,
                    modifier = Modifier.weight(0.90f).height(cardHeight)
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
                    modifier = Modifier.weight(1.22f).height(cardHeight)
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
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(gpsAccentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "GPS Status",
                    tint = gpsAccentColor,
                    modifier = Modifier
                        .size(14.dp)
                        .alpha(if (!hasFix) gpsPulseAlpha else 1f)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
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
                            .alpha(if (!hasFix) gpsPulseAlpha else 1f)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = gpsValueText,
                        color = mainTextColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = gpsStatusSub,
                    color = gpsAccentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(satAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SatelliteAlt,
                    contentDescription = "Satellites",
                    tint = satAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (hasFix) "$satellitesCount" else "--",
                    color = mainTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    text = "Satellites",
                    color = subTextColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(speedAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Speed",
                    tint = speedAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format(Locale.US, "%.0f", speedKmh),
                    color = mainTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    text = "km/h",
                    color = subTextColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
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
                .padding(horizontal = 7.dp),
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
                        .background(compassAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CompassCalibration,
                        contentDescription = "Compass",
                        tint = compassAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = compassHeadingStr,
                        color = mainTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Compass",
                        color = subTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                modifier = Modifier.size(14.dp)
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
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false
) {
    val kmBadgeBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.45f) else Color(0xFFEFF6FF)
    val kmBadgeBorder = if (isDark) Color(0xFF3B82F6).copy(alpha = 0.4f) else Color(0xFFBFDBFE)
    val kmBadgeText = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)

    val sideBadgeBg = if (isDark) Color(0xFF064E3B).copy(alpha = 0.45f) else Color(0xFFECFDF5)
    val sideBadgeBorder = if (isDark) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFA7F3D0)
    val sideBadgeText = if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("central_chainage_card")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 10.dp,
                    vertical = if (isCompact) 6.dp else 8.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = maxWidth
            val charCount = chainageText.length.coerceAtLeast(6)
            // Significantly increased chainage font size while preventing clipping on any phone width
            val digitFontSize = when {
                charCount > 8 -> when {
                    containerWidth < 340.dp -> 36.sp
                    containerWidth < 390.dp -> 42.sp
                    else -> 48.sp
                }
                containerWidth < 340.dp -> 44.sp
                containerWidth < 380.dp -> 52.sp
                containerWidth < 430.dp -> 58.sp
                else -> 64.sp
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Compact Left Label: KM
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = kmBadgeBg,
                        border = BorderStroke(0.8.dp, kmBadgeBorder),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "KM",
                            color = kmBadgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    // Dominant Live Chainage Digits
                    OdometerChainageRow(
                        chainageText = chainageText,
                        mainTextColor = mainTextColor,
                        digitFontSize = digitFontSize,
                        isDark = isDark
                    )

                    // Compact Right Badge: LHS / RHS / BHS
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sideBadgeBg,
                        border = BorderStroke(0.8.dp, sideBadgeBorder),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(sideBadgeText)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = detectedSide,
                                color = sideBadgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))

                // Subtle Telemetry Metadata Strip Beneath Dominant Chainage
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (hasActiveRoute) "Project Alignment" else "GPS-Derived",
                        color = subTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
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
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "  •  ",
                        color = subTextColor.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )

                    val (confText, confCol) = when (confidence) {
                        MatchConfidence.HIGH -> "Good" to (if (isDark) Color(0xFF34D399) else Color(0xFF059669))
                        MatchConfidence.MEDIUM -> "Moderate" to (if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB))
                        MatchConfidence.LOW -> "Poor" to AccentRed
                        MatchConfidence.NO_MATCH -> "No Fix" to subTextColor
                    }
                    Text(
                        text = confText,
                        color = confCol,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
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
        if (!char.isDigit()) {
            Text(
                text = char.toString(),
                color = if (char == '+') activeAccent else mainTextColor,
                fontSize = digitFontSize,
                lineHeight = digitFontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.0).sp,
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
                    letterSpacing = (-1.0).sp,
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
    val cardHeight = if (isCompact) 48.dp else 52.dp

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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(projectAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Engineering,
                        contentDescription = null,
                        tint = projectAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PROJECT",
                        color = subTextColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = projectName.ifBlank { "Set Project" },
                        color = if (projectName.isNotBlank()) mainTextColor else subTextColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(subTextColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Project",
                        tint = subTextColor,
                        modifier = Modifier.size(11.dp)
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(stretchAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = null,
                        tint = stretchAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "STRETCH",
                        color = subTextColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = stretchName.ifBlank { "Set Stretch" },
                        color = if (stretchName.isNotBlank()) mainTextColor else subTextColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(subTextColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Stretch",
                        tint = subTextColor,
                        modifier = Modifier.size(11.dp)
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
    val isDark = com.example.ui.theme.isAppDarkTheme()
    val dialogAccent = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = if (isDark) Color(0xFF131B2E) else Color.White,
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
                color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
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
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
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
                        focusedBorderColor = dialogAccent,
                        unfocusedBorderColor = if (isDark) Color(0xFF263554) else Color(0xFFCBD5E1),
                        focusedLabelColor = dialogAccent,
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
                        focusedBorderColor = dialogAccent,
                        unfocusedBorderColor = if (isDark) Color(0xFF263554) else Color(0xFFCBD5E1),
                        focusedLabelColor = dialogAccent,
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
                Text("Cancel", color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
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

@Composable
fun NextChainageCard(
    nextChainage: String,
    distanceText: String,
    detectedSide: String = "RHS",
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false
) {
    val cleanDistance = distanceText.replace("Exact ", "", ignoreCase = true).trim()

    val labelColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val badgeBgColor = if (isDark) Color(0xFF2563EB) else Color(0xFF2563EB)
    val iconBoxBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.35f) else Color(0xFFEFF6FF)
    val sideBadgeBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val sideBadgeText = if (isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF)

    val infiniteTransition = rememberInfiniteTransition(label = "km_badge_pulse")
    val badgePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgePulse"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompact) 52.dp else 56.dp)
            .testTag("next_chainage_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        KmStoneIcon(sizeDp = 30.dp, isDark = isDark)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "NEXT KM STONE",
                        color = labelColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (!nextChainage.startsWith("KM") && !nextChainage.contains("-")) "KM $nextChainage" else nextChainage,
                            color = mainTextColor,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.3.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = sideBadgeBg,
                            border = BorderStroke(0.8.dp, cardBorderColor)
                        ) {
                            Text(
                                text = detectedSide.uppercase(Locale.ROOT),
                                color = sideBadgeText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }
            }

            // Prominent Distance Remaining Pill
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = badgeBgColor.copy(alpha = badgePulseAlpha),
                shadowElevation = 1.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.5.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = cleanDistance,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
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
    onNavigateToCamera: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToSettings: () -> Unit,
    cardBgColor: Color,
    cardBorderColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false
) {
    val toolsAccent = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val notesAccent = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
    val titleColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val chevronColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
    val btnHeight = if (isCompact) 42.dp else 46.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(toolsAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Tools",
                            tint = toolsAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tools",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = chevronColor,
                    modifier = Modifier.size(16.dp)
                )
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
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(notesAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "DPR Notes",
                            tint = notesAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DPR Notes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = chevronColor,
                    modifier = Modifier.size(16.dp)
                )
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

            if (osmBmp != null) {
                drawImage(
                    image = osmBmp.asImageBitmap(),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(w.toInt(), h.toInt())
                )
                hasOsmTiles = true
            }

            if (!hasOsmTiles) {
                drawRect(color = mapTheme.mapBackground)
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

            // High-Contrast Highway Route Polylines
            if (routeData != null && routeData.alignments.isNotEmpty()) {
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
            if (routeData != null) {
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

            // Live GPS Location Marker with Heading Cone & Accuracy Aura
            if (location != null) {
                val userOff = toScreen(location)

                // Pulsing GPS Accuracy Halo
                drawCircle(
                    color = Color(0xFFEF4444).copy(alpha = pulseAlpha * 0.35f),
                    radius = 26.dp.toPx(),
                    center = userOff
                )

                // Directional Heading Pointer Cone
                val activeHeading = bearing ?: compassHeading
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

                // Crisp White Outer Ring + Red Core Beacon
                drawCircle(color = Color.White, radius = 7.dp.toPx(), center = userOff)
                drawCircle(color = Color(0xFFEF4444), radius = 4.8.dp.toPx(), center = userOff)
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

        // Top-Right Refined Compass Widget
        Surface(
            shape = CircleShape,
            color = controlSurfaceColor,
            border = BorderStroke(1.dp, controlBorderColor),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(50.dp)
                .testTag("map_compass_widget")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(42.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    // Subtle compass dial ring
                    drawCircle(
                        color = controlBorderColor.copy(alpha = 0.6f),
                        radius = size.minDimension / 2.2f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    rotate(degrees = -compassHeading, pivot = Offset(cx, cy)) {
                        val northNeedle = Path().apply {
                            moveTo(cx, cy - 14.dp.toPx())
                            lineTo(cx - 4.5.dp.toPx(), cy)
                            lineTo(cx + 4.5.dp.toPx(), cy)
                            close()
                        }
                        drawPath(northNeedle, color = Color(0xFFEF4444))

                        val southNeedle = Path().apply {
                            moveTo(cx, cy + 14.dp.toPx())
                            lineTo(cx - 4.5.dp.toPx(), cy)
                            lineTo(cx + 4.5.dp.toPx(), cy)
                            close()
                        }
                        drawPath(southNeedle, color = Color(0xFF94A3B8).copy(alpha = 0.45f))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "N",
                        color = Color(0xFFEF4444),
                        fontSize = 7.5.sp,
                        lineHeight = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = compassHeadingStr,
                        color = controlIconColor,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Black
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
                    onClick = { viewRangeMetres = (viewRangeMetres * 0.7).coerceAtLeast(100.0) },
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
                    onClick = { viewRangeMetres = (viewRangeMetres * 1.4).coerceAtMost(15000.0) },
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
