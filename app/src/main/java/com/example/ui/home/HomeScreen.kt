package com.example.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.GeoUtils
import com.example.model.CoordinateFormat
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteData
import com.example.ui.MainViewModel
import com.example.ui.theme.AccentRed
import com.example.ui.theme.BorderColor
import com.example.ui.theme.DarkYellowSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighlightYellow
import com.example.ui.theme.PrimaryBackground
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SurfaceDark
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToCamera: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val matchResult by viewModel.liveMatchResult.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()
    val compassState by viewModel.compassState.collectAsStateWithLifecycle()
    val cameraSettings by viewModel.cameraSettings.collectAsStateWithLifecycle()

    // Last update elapsed ticker
    var lastUpdatedText by remember { mutableStateOf("Just now") }
    LaunchedEffect(locationState.lastFixTime) {
        val updateTime = locationState.lastFixTime
        if (updateTime == 0L) {
            lastUpdatedText = "Awaiting GPS Fix"
            return@LaunchedEffect
        }
        while (true) {
            val diffSec = ((System.currentTimeMillis() - updateTime) / 1000).coerceAtLeast(0)
            lastUpdatedText = when {
                diffSec < 2 -> "Just now"
                diffSec < 60 -> "${diffSec}s ago"
                else -> "${diffSec / 60}m ago"
            }
            delay(1000L)
        }
    }

    // GPS acquisition pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "gps_dot")
    val gpsPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
        label = "gps_dot_pulse"
    )

    // Highlight animation on chainage value update
    var isChainageHighlighted by remember { mutableStateOf(false) }
    LaunchedEffect(matchResult.displayChainage) {
        if (matchResult.displayChainage.isNotBlank()) {
            isChainageHighlighted = true
            delay(650L)
            isChainageHighlighted = false
        }
    }
    val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val mainTextColor = MaterialTheme.colorScheme.onBackground
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val animatedChainageColor by animateColorAsState(
        targetValue = if (isChainageHighlighted) HighlightYellow else mainTextColor,
        animationSpec = tween(400),
        label = "chainage_glow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp)
        ) {
            // Header Row with Quick Theme Toggle Shortcut
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (projectProfile.highwayId.isNotBlank() || projectProfile.packageSection.isNotBlank()) {
                    val projTag = listOf(projectProfile.highwayId, projectProfile.packageSection)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    Text(
                        text = projTag.uppercase(Locale.ROOT),
                        color = subTextColor.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                } else {
                    Text(
                        text = "GPS CHAINAGE SURVEY",
                        color = subTextColor.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                // Quick Theme Toggle Shortcut Button
                IconButton(
                    onClick = {
                        val nextMode = when (currentThemeMode) {
                            "dark" -> "light"
                            "light" -> "system"
                            else -> "dark"
                        }
                        viewModel.setThemeMode(nextMode)
                        viewModel.showToast("Theme: ${nextMode.uppercase()}")
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("home_theme_toggle_shortcut")
                ) {
                    Icon(
                        imageVector = when (currentThemeMode) {
                            "light" -> Icons.Default.LightMode
                            "dark" -> Icons.Default.DarkMode
                            else -> Icons.Default.Palette
                        },
                        contentDescription = "Toggle Theme",
                        tint = HighlightYellow,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 1. Extra-Large Central Chainage & Compact Telemetry (No Top Navbar)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val screenWidth = maxWidth
                val chainageFontSize = when {
                    screenWidth < 360.dp -> 46.sp
                    screenWidth < 420.dp -> 54.sp
                    screenWidth < 600.dp -> 64.sp
                    else -> 74.sp
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Main Focus: Extra-Large Centered Chainage with small KM prefix and tiny LHS/RHS
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val hasValidChainage = activeRoute != null && locationState.point != null && matchResult.chainage != null
                        val displayChainageText = when {
                            activeRoute == null -> "NO KMZ"
                            locationState.point == null -> "--+---"
                            hasValidChainage -> matchResult.displayChainage
                            else -> "--+---"
                        }

                        if (hasValidChainage) {
                            Text(
                                text = "KM",
                                color = HighlightYellow,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = (chainageFontSize.value * 0.16f).dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        AnimatedContent(
                            targetState = displayChainageText,
                            transitionSpec = {
                                (slideInVertically(animationSpec = tween(280)) { height -> height / 2 } + fadeIn(tween(200)))
                                    .togetherWith(slideOutVertically(animationSpec = tween(280)) { height -> -height / 2 } + fadeOut(tween(180)))
                            },
                            label = "extra_large_chainage_anim"
                        ) { targetChainage ->
                            Text(
                                text = targetChainage,
                                color = if (hasValidChainage) animatedChainageColor else subTextColor.copy(alpha = 0.5f),
                                fontSize = chainageFontSize,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val sideText = when {
                            activeRoute == null -> "Load Route"
                            locationState.point == null -> "Awaiting Fix"
                            matchResult.detectedSide.uppercase(Locale.ROOT) == "LHS" -> "LHS"
                            matchResult.detectedSide.uppercase(Locale.ROOT) == "RHS" -> "RHS"
                            else -> "Unknown"
                        }
                        val sideColor = when (sideText) {
                            "RHS" -> HighlightYellow.copy(alpha = 0.95f)
                            "LHS" -> Color(0xFF60A5FA)
                            "Load Route" -> HighlightYellow.copy(alpha = 0.8f)
                            else -> subTextColor
                        }
                        Text(
                            text = "· $sideText",
                            color = sideColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(bottom = (chainageFontSize.value * 0.16f).dp)
                                .testTag("carriageway_badge")
                        )
                    }

                    // Road Name, Offset, and Match Confidence
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        val routeDisplay = activeRoute?.name ?: "No KMZ Road Alignment Selected"
                        Text(
                            text = routeDisplay,
                            color = subTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (matchResult.offsetMetres > 0.0) {
                            Text(
                                text = String.format(Locale.US, "  ·  Offset: %.1fm", matchResult.offsetMetres),
                                color = subTextColor.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                        if (activeRoute != null && matchResult.confidence != MatchConfidence.NO_MATCH) {
                            val confLabel = when (matchResult.confidence) {
                                MatchConfidence.HIGH -> "High Match"
                                MatchConfidence.MEDIUM -> "Moderate"
                                MatchConfidence.LOW -> "Low Match"
                                else -> ""
                            }
                            if (confLabel.isNotBlank()) {
                                Text(
                                    text = "  ·  $confLabel",
                                    color = Color(0xFF22C55E).copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Compact Telemetry Status Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isAcquiring = locationState.point == null
                        val (gpsDotCol, gpsLabel) = when {
                            isAcquiring -> Color(0xFF38BDF8) to "Acquiring..."
                            locationState.accuracy <= 5f -> Color(0xFF22C55E) to String.format(Locale.US, "GPS ±%.0fm", locationState.accuracy)
                            locationState.accuracy <= 15f -> HighlightYellow to String.format(Locale.US, "GPS ±%.0fm", locationState.accuracy)
                            else -> AccentRed to String.format(Locale.US, "GPS ±%.0fm", locationState.accuracy)
                        }

                        // GPS Accuracy Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(gpsDotCol)
                                    .alpha(if (isAcquiring) gpsPulseAlpha else 1.0f)
                            )
                            Text(
                                text = gpsLabel,
                                color = mainTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Speed
                        val currentSpeed = locationState.speed
                        val speedKmh = if (currentSpeed != null && currentSpeed >= 0f) currentSpeed * 3.6f else 0.0f
                        Text(
                            text = String.format(Locale.US, "%.1f km/h", speedKmh),
                            color = mainTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Direction / Heading
                        val currentBearing = locationState.bearing
                        val dirText = if (currentBearing != null && currentBearing >= 0f) {
                            val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
                            val index = (((currentBearing + 11.25) % 360) / 22.5).toInt()
                            String.format(Locale.US, "%.0f° %s", currentBearing, directions[index % 16])
                        } else "Stationary"
                        Text(
                            text = dirText,
                            color = subTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Last Update
                        Text(
                            text = lastUpdatedText,
                            color = subTextColor,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // 2. Interactive Smart Map & Compass
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                InteractiveSmartMap(
                    location = locationState.point,
                    gpsAccuracy = locationState.accuracy,
                    bearing = locationState.bearing,
                    compassHeading = compassState.azimuthDegrees,
                    routeData = activeRoute,
                    matchedProjectedPoint = matchResult.projectedPoint,
                    modifier = Modifier.fillMaxSize()
                )

                // Animated Circular Compass Overlay (Top Right)
                AnimatedGlassCompass(
                    azimuthDegrees = compassState.azimuthDegrees,
                    cardinalDirection = compassState.cardinalDirection,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )

                // Map Legend / Route Status (Top Left)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (activeRoute != null) HighlightYellow else DisabledColor)
                        )
                        Text(
                            text = if (activeRoute != null) "${activeRoute?.alignments?.size ?: 0} Alignments Loaded" else "No KMZ Active",
                            color = mainTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private val DisabledColor = Color(0xFF6B7280)

@Composable
fun HomeTopBar(
    highwayId: String,
    packageSection: String,
    onNavigateToRoutes: () -> Unit,
    onNavigateToPhotos: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Surface(
        color = SecondaryBackground,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // App Branding & Highway Project Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(HighlightYellow, Color(0xFFE5A100))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Ctecs Camera",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CTECS",
                            color = PrimaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = " PRO",
                            color = HighlightYellow,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val projTag = if (highwayId.isNotBlank() && packageSection.isNotBlank()) {
                        "$highwayId || $packageSection"
                    } else "Highway Survey"
                    Text(
                        text = projTag,
                        color = SecondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Quick Navigation Shortcuts
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateToRoutes,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElevatedSurface)
                        .testTag("home_routes_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = "KMZ Routes",
                        tint = HighlightYellow,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onNavigateToPhotos,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElevatedSurface)
                        .testTag("home_photos_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Photos",
                        tint = PrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElevatedSurface)
                        .testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = PrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HighwayDashboardCard(
    routeName: String,
    highwayTag: String,
    packageTag: String,
    displayChainage: String,
    detectedSide: String,
    confidence: MatchConfidence,
    offsetMetres: Double,
    matchedAlignment: String,
    accuracy: Float,
    speedMps: Float?,
    bearing: Float?,
    point: GeoPoint?,
    coordFormat: CoordinateFormat,
    lastUpdatedText: String,
    hasActiveRoute: Boolean,
    onSelectRoute: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Road Name + Side Indicator Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routeName,
                        color = PrimaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (matchedAlignment.isNotBlank()) "Alignment: $matchedAlignment" else "Highway Alignment Tracking",
                        color = SecondaryText,
                        fontSize = 11.sp
                    )
                }

                // Carriageway Badge (LHS, RHS, or Unknown)
                CarriagewayBadge(detectedSide = detectedSide)
            }

            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), thickness = 0.8.dp)

            // Row 2: Prominent Chainage Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PRECISE KMZ CHAINAGE",
                        color = SecondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = displayChainage,
                        color = HighlightYellow,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                // Confidence & Offset Pill
                ConfidenceOffsetBadge(
                    confidence = confidence,
                    offsetMetres = offsetMetres,
                    hasActiveRoute = hasActiveRoute,
                    onSelectRoute = onSelectRoute
                )
            }

            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), thickness = 0.8.dp)

            // Row 3: Live GPS Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Accuracy Pill
                GpsStatusPill(accuracy = accuracy, lastUpdated = lastUpdatedText)

                // Speed
                val speedKmh = if (speedMps != null && speedMps >= 0f) speedMps * 3.6f else 0.0f
                TelemetryMetric(
                    label = "SPEED",
                    value = String.format(Locale.US, "%.1f km/h", speedKmh)
                )

                // Direction of Travel
                val dirText = if (bearing != null && bearing >= 0f) {
                    val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
                    val index = (((bearing + 11.25) % 360) / 22.5).toInt()
                    String.format(Locale.US, "%.0f° %s", bearing, directions[index % 16])
                } else "Stationary"
                TelemetryMetric(
                    label = "HEADING",
                    value = dirText
                )
            }

            // Row 4: Geographic Coordinates
            if (point != null) {
                val coordStr = when (coordFormat) {
                    CoordinateFormat.DMS -> {
                        val latDms = toDms(point.latitude, true)
                        val lngDms = toDms(point.longitude, false)
                        "$latDms  |  $lngDms"
                    }
                    else -> String.format(Locale.US, "%.5f°N, %.5f°E", point.latitude, point.longitude)
                }
                val altStr = if (point.altitude != 0.0) "  |  Alt: ${point.altitude.toInt()}m" else ""
                Text(
                    text = "$coordStr$altStr",
                    color = SecondaryText,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CarriagewayBadge(detectedSide: String) {
    val (bgColor, textColor, borderCol) = when (detectedSide.uppercase(Locale.ROOT)) {
        "LHS" -> Triple(Color(0xFF1E3A8A), Color(0xFF93C5FD), Color(0xFF3B82F6))
        "RHS" -> Triple(DarkYellowSurface, HighlightYellow, HighlightYellow.copy(alpha = 0.6f))
        else -> Triple(ElevatedSurface, SecondaryText, BorderColor)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
        modifier = Modifier.testTag("carriageway_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = "CARRIAGEWAY: $detectedSide",
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun ConfidenceOffsetBadge(
    confidence: MatchConfidence,
    offsetMetres: Double,
    hasActiveRoute: Boolean,
    onSelectRoute: () -> Unit
) {
    if (!hasActiveRoute) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ElevatedSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            modifier = Modifier.clickable { onSelectRoute() }
        ) {
            Text(
                text = "Load KMZ Route →",
                color = HighlightYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        return
    }

    val (label, col) = when (confidence) {
        MatchConfidence.HIGH -> "High Match" to Color(0xFF22C55E)
        MatchConfidence.MEDIUM -> "Caution" to HighlightYellow
        MatchConfidence.LOW -> "Low Match" to AccentRed
        MatchConfidence.NO_MATCH -> "Off Route" to DisabledColor
    }

    Column(horizontalAlignment = Alignment.End) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = col.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, col.copy(alpha = 0.5f))
        ) {
            Text(
                text = label,
                color = col,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (offsetMetres > 0.0) {
            Text(
                text = String.format(Locale.US, "Offset: %.1fm", offsetMetres),
                color = SecondaryText,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun GpsStatusPill(accuracy: Float, lastUpdated: String) {
    val (dotCol, statusLabel) = when {
        accuracy <= 0f -> Color.Gray to "No Fix"
        accuracy <= 4f -> Color(0xFF22C55E) to "GPS ±${accuracy.toInt()}m"
        accuracy <= 12f -> HighlightYellow to "GPS ±${accuracy.toInt()}m"
        else -> AccentRed to "GPS ±${accuracy.toInt()}m"
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotCol)
            )
            Text(
                text = statusLabel,
                color = PrimaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = lastUpdated,
            color = SecondaryText,
            fontSize = 10.sp
        )
    }
}

@Composable
fun TelemetryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label,
            color = SecondaryText,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            color = PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InteractiveSmartMap(
    location: GeoPoint?,
    gpsAccuracy: Float,
    bearing: Float?,
    compassHeading: Float,
    routeData: RouteData?,
    matchedProjectedPoint: GeoPoint?,
    modifier: Modifier = Modifier
) {
    // Map Viewport state: pan offset in metres and zoom scale (metres per window width)
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var viewRangeMetres by remember { mutableDoubleStateOf(1000.0) } // Default ~1km view range
    var isFollowingUser by remember { mutableStateOf(true) }

    // Re-center when following user and location updates
    LaunchedEffect(location, isFollowingUser) {
        if (isFollowingUser) {
            panOffsetX = 0f
            panOffsetY = 0f
        }
    }

    // Animation transition for pulsing GPS circle
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        isFollowingUser = false
                        // Adjust zoom clamped to 100m to 10km
                        viewRangeMetres = (viewRangeMetres / zoom).coerceIn(100.0, 15000.0)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f + panOffsetX, h / 2f + panOffsetY)

            // Dark map background
            drawRect(color = Color(0xFF0F172A))

            // Subtle coordinate grid
            val gridSpacing = 80.dp.toPx()
            var gx = 0f
            while (gx < w) {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 1f
                )
                gx += gridSpacing
            }
            var gy = 0f
            while (gy < h) {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 1f
                )
                gy += gridSpacing
            }

            // Geographic reference center
            val anchorGeo = location ?: routeData?.bounds?.center ?: GeoPoint(28.6139, 77.2090)
            val midLat = Math.toRadians(anchorGeo.latitude)
            val cosMidLat = cos(midLat)
            val metresPerPx = viewRangeMetres / w

            fun toScreen(pt: GeoPoint): Offset {
                val dx = Math.toRadians(pt.longitude - anchorGeo.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosMidLat
                val dy = Math.toRadians(pt.latitude - anchorGeo.latitude) * GeoUtils.EARTH_RADIUS_METRES
                val sx = (center.x + (dx / metresPerPx)).toFloat()
                val sy = (center.y - (dy / metresPerPx)).toFloat()
                return Offset(sx, sy)
            }

            // 1. Draw KMZ Road Alignment Polylines
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

                    // Outer dark road casing
                    drawPath(
                        path = path,
                        color = Color(0xFF1E293B),
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Inner glowing road centerline
                    drawPath(
                        path = path,
                        color = Color(0xFF38BDF8),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // 2. Draw Chainage Reference Markers
            if (routeData != null) {
                for (marker in routeData.markers) {
                    val mOff = toScreen(marker.point)
                    if (mOff.x in -100f..(w + 100f) && mOff.y in -100f..(h + 100f)) {
                        // Marker dot
                        drawCircle(
                            color = HighlightYellow,
                            radius = 4.dp.toPx(),
                            center = mOff
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 4.dp.toPx(),
                            center = mOff,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }

            // 3. Matched Segment projection line from user to road
            if (location != null && matchedProjectedPoint != null) {
                val userOff = toScreen(location)
                val projOff = toScreen(matchedProjectedPoint)

                drawLine(
                    color = HighlightYellow.copy(alpha = 0.7f),
                    start = userOff,
                    end = projOff,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = HighlightYellow,
                    radius = 3.dp.toPx(),
                    center = projOff
                )
            }

            // 4. Live GPS Location Pin & Heading Cone
            if (location != null) {
                val userOff = toScreen(location)

                // GPS Accuracy radius circle
                if (gpsAccuracy > 0f) {
                    val accuracyRadiusPx = (gpsAccuracy / metresPerPx).toFloat().coerceIn(8f, 150f)
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = pulseAlpha * 0.5f),
                        radius = accuracyRadiusPx,
                        center = userOff
                    )
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.6f),
                        radius = accuracyRadiusPx,
                        center = userOff,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Direction / Heading pointer cone
                val activeHeading = bearing ?: compassHeading
                rotate(degrees = activeHeading, pivot = userOff) {
                    val conePath = Path().apply {
                        moveTo(userOff.x, userOff.y - 18.dp.toPx())
                        lineTo(userOff.x - 7.dp.toPx(), userOff.y + 6.dp.toPx())
                        lineTo(userOff.x, userOff.y)
                        lineTo(userOff.x + 7.dp.toPx(), userOff.y + 6.dp.toPx())
                        close()
                    }
                    drawPath(
                        path = conePath,
                        color = HighlightYellow
                    )
                }

                // Inner core dot
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = userOff
                )
                drawCircle(
                    color = Color(0xFF0284C7),
                    radius = 4.dp.toPx(),
                    center = userOff
                )
            }
        }

        // Map Control Floating Buttons (Zoom +, Zoom -, Follow Me)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Follow location button
            IconButton(
                onClick = {
                    isFollowingUser = true
                    panOffsetX = 0f
                    panOffsetY = 0f
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isFollowingUser) HighlightYellow else SurfaceDark)
                    .border(1.dp, BorderColor, CircleShape)
                    .testTag("map_follow_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Center on GPS",
                    tint = if (isFollowingUser) Color.Black else PrimaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom In
            IconButton(
                onClick = { viewRangeMetres = (viewRangeMetres * 0.7).coerceAtLeast(100.0) },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, BorderColor, CircleShape)
                    .testTag("map_zoom_in")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = PrimaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom Out
            IconButton(
                onClick = { viewRangeMetres = (viewRangeMetres * 1.4).coerceAtMost(15000.0) },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, BorderColor, CircleShape)
                    .testTag("map_zoom_out")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = PrimaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AnimatedGlassCompass(
    azimuthDegrees: Float,
    cardinalDirection: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = Color(0xE01A1A1A),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, HighlightYellow.copy(alpha = 0.7f)),
        shadowElevation = 6.dp,
        modifier = modifier
            .size(76.dp)
            .testTag("animated_compass")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(68.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val center = Offset(cx, cy)
                val r = cx - 2.dp.toPx()

                // Dial tick marks
                rotate(degrees = -azimuthDegrees, pivot = center) {
                    for (deg in 0 until 360 step 30) {
                        val rad = Math.toRadians(deg.toDouble())
                        val isCardinal = deg % 90 == 0
                        val tickLen = if (isCardinal) 6.dp.toPx() else 3.dp.toPx()
                        val start = Offset(
                            (cx + (r - tickLen) * kotlin.math.sin(rad)).toFloat(),
                            (cy - (r - tickLen) * kotlin.math.cos(rad)).toFloat()
                        )
                        val end = Offset(
                            (cx + r * kotlin.math.sin(rad)).toFloat(),
                            (cy - r * kotlin.math.cos(rad)).toFloat()
                        )
                        drawLine(
                            color = if (deg == 0) AccentRed else Color.White.copy(alpha = 0.5f),
                            start = start,
                            end = end,
                            strokeWidth = if (isCardinal) 2f else 1f
                        )
                    }

                    // North Needle (Red)
                    val needlePathNorth = Path().apply {
                        moveTo(cx, cy - r + 8.dp.toPx())
                        lineTo(cx - 3.5.dp.toPx(), cy)
                        lineTo(cx + 3.5.dp.toPx(), cy)
                        close()
                    }
                    drawPath(path = needlePathNorth, color = AccentRed)

                    // South Needle (White)
                    val needlePathSouth = Path().apply {
                        moveTo(cx, cy + r - 8.dp.toPx())
                        lineTo(cx - 3.5.dp.toPx(), cy)
                        lineTo(cx + 3.5.dp.toPx(), cy)
                        close()
                    }
                    drawPath(path = needlePathSouth, color = Color.White.copy(alpha = 0.6f))
                }

                // Center pivot dot
                drawCircle(color = HighlightYellow, radius = 2.5.dp.toPx(), center = center)
            }

            // Azimuth and Cardinal label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${azimuthDegrees.toInt()}°",
                    color = PrimaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = cardinalDirection,
                    color = HighlightYellow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FloatingCameraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        shape = RoundedCornerShape(32.dp),
        modifier = modifier
            .padding(horizontal = 24.dp)
            .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = HighlightYellow, spotColor = HighlightYellow)
            .testTag("floating_camera_button")
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xF018181B),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, HighlightYellow),
            modifier = Modifier.wrapContentHeight()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(HighlightYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "OPEN HIGHWAY CAMERA",
                        color = PrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Instant Capture & Video with Chainage",
                        color = HighlightYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = HighlightYellow,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun FloatingGlassBottomNav(
    onNavigateToCamera: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color(0xEB131823),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 18.dp,
        modifier = modifier
            .padding(horizontal = 20.dp)
            .height(64.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Camera (Primary Action)
            NavGlassItem(
                icon = Icons.Default.CameraAlt,
                label = "Camera",
                isPrimary = true,
                testTag = "floating_camera_button",
                onClick = onNavigateToCamera
            )

            // 2. Gallery
            NavGlassItem(
                icon = Icons.Default.PhotoLibrary,
                label = "Gallery",
                isPrimary = false,
                testTag = "home_photos_button",
                onClick = onNavigateToGallery
            )

            // 3. KMZ Routes
            NavGlassItem(
                icon = Icons.Default.AltRoute,
                label = "Routes",
                isPrimary = false,
                testTag = "home_routes_button",
                onClick = onNavigateToRoutes
            )

            // 4. Settings
            NavGlassItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isPrimary = false,
                testTag = "home_settings_button",
                onClick = onNavigateToSettings
            )
        }
    }
}

@Composable
private fun NavGlassItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isPrimary: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "nav_item_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                isPressed = true
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag(testTag)
    ) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(150L)
                isPressed = false
            }
        }
        if (isPrimary) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(HighlightYellow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.Black,
                    modifier = Modifier.size(19.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PrimaryText.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isPrimary) HighlightYellow else SecondaryText,
            fontSize = 10.sp,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun toDms(coord: Double, isLat: Boolean): String {
    val abs = kotlin.math.abs(coord)
    val degrees = abs.toInt()
    val minutes = ((abs - degrees) * 60.0).toInt()
    val seconds = (((abs - degrees) * 60.0 - minutes) * 60.0).toInt()
    val ref = if (isLat) {
        if (coord >= 0) "N" else "S"
    } else {
        if (coord >= 0) "E" else "W"
    }
    return String.format(Locale.US, "%d°%d'%d\"%s", degrees, minutes, seconds, ref)
}
