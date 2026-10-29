package com.example.ui.home

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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.GeoUtils
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteData
import com.example.ui.MainViewModel
import com.example.ui.theme.AccentRed
import com.example.ui.theme.BorderColor
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HighlightYellow
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
    val context = LocalContext.current
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val matchResult by viewModel.liveMatchResult.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRouteData.collectAsStateWithLifecycle()
    val projectProfile by viewModel.projectProfile.collectAsStateWithLifecycle()
    val compassState by viewModel.compassState.collectAsStateWithLifecycle()

    val currentThemeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val mainTextColor = MaterialTheme.colorScheme.onBackground
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBgColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    // Pulse transition for GPS dot
    val infiniteTransition = rememberInfiniteTransition(label = "gps_dot")
    val gpsPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
        label = "gps_dot_pulse"
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Top Telemetry Header Row (4 Cards Across)
            val speedKmh = if (locationState.speed != null && locationState.speed!! >= 0f) locationState.speed!! * 3.6f else 0f
            val satCount = if (locationState.point != null) {
                if (locationState.accuracy <= 5f) 18 else if (locationState.accuracy <= 15f) 14 else 9
            } else 0

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
                onScreenOff = {
                    com.example.util.ScreenOffUtil.turnScreenOff(context) { msg ->
                        viewModel.showToast(msg)
                    }
                },
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor
            )

            // 2. Extra-Large Central Chainage Display with Individual Odometer Digit Animations
            val displayChainageText = when {
                activeRoute == null -> "NO KMZ"
                locationState.point == null -> "--+---"
                matchResult.chainage != null -> matchResult.displayChainage
                else -> "--+---"
            }

            val detectedSide = when {
                activeRoute == null -> "Load Route"
                locationState.point == null -> "Awaiting Fix"
                matchResult.detectedSide.isNotBlank() -> matchResult.detectedSide.uppercase(Locale.ROOT)
                else -> "RHS"
            }

            CentralChainageDisplay(
                chainageText = displayChainageText,
                detectedSide = detectedSide,
                confidence = matchResult.confidence,
                offsetMetres = matchResult.offsetMetres,
                hasActiveRoute = activeRoute != null,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor
            )

            // 3. Highway & Project Information Cards (Row of 2 Cards)
            val highwayTitle = if (projectProfile.highwayId.isNotBlank()) projectProfile.highwayId else (activeRoute?.name ?: "NH 44")
            val highwaySubtitle = activeRoute?.name ?: "Hyderabad - Nagpur"
            val packageTitle = if (projectProfile.packageSection.isNotBlank()) projectProfile.packageSection else "Package - 03"

            HighwayProjectCardsRow(
                highwayTitle = highwayTitle,
                highwaySubtitle = highwaySubtitle,
                packageTitle = packageTitle,
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor
            )

            // 4. Interactive Smart KMZ Map - Expanded 360.dp Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(0.8.dp, cardBorderColor, RoundedCornerShape(18.dp))
            ) {
                InteractiveSmartMap(
                    location = locationState.point,
                    gpsAccuracy = locationState.accuracy,
                    bearing = locationState.bearing,
                    compassHeading = compassState.azimuthDegrees,
                    compassHeadingStr = compassState.displayHeading,
                    routeData = activeRoute,
                    matchedProjectedPoint = matchResult.projectedPoint,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 5. Prominent Full-Width Next Chainage Card (Single element below Map)
            val curChainage = matchResult.chainage
            val nextChainageValue = if (curChainage != null) {
                com.example.model.Chainage.fromMetres(((curChainage.metres / 100) + 1) * 100).format()
            } else "198+500"

            NextChainageCard(
                nextChainage = nextChainageValue,
                distanceMetres = "50 m",
                cardBgColor = cardBgColor,
                cardBorderColor = cardBorderColor,
                mainTextColor = mainTextColor,
                subTextColor = subTextColor
            )

            // Bottom Spacing for Floating Glass Nav Bar
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

// -------------------------------------------------------------------
// COMPONENT 1: TOP TELEMETRY HEADER ROW (Adaptive 4 Cards / 2x2 Grid)
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
    onScreenOff: () -> Unit = {},
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 360.dp

        val (dotCol, gpsText) = when {
            !hasFix -> Color(0xFF38BDF8) to "GPS --"
            accuracy <= 5f -> Color(0xFF22C55E) to String.format(Locale.US, "GPS ±%.0fm", accuracy)
            accuracy <= 15f -> HighlightYellow to String.format(Locale.US, "GPS ±%.0fm", accuracy)
            else -> AccentRed to String.format(Locale.US, "GPS ±%.0fm", accuracy)
        }

        if (isNarrow) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 1: GPS Accuracy Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotCol)
                                    .alpha(if (!hasFix) gpsPulseAlpha else 1.0f)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = gpsText,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Card 2: Satellites
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBgColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SatelliteAlt,
                                contentDescription = null,
                                tint = mainTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = if (hasFix) "$satellitesCount" else "--",
                                    color = mainTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Satellites",
                                    color = subTextColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 3: Speed
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBgColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = mainTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = String.format(Locale.US, "%.0f", speedKmh),
                                    color = mainTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "km/h",
                                    color = subTextColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Card 4: Compass Pill + Quick Theme Shortcut
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(44.dp)
                            .clickable { onToggleTheme() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CompassCalibration,
                                    contentDescription = null,
                                    tint = HighlightYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = compassHeadingStr,
                                    color = HighlightYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Icon(
                                imageVector = when (currentThemeMode) {
                                    "light" -> Icons.Default.LightMode
                                    "dark" -> Icons.Default.DarkMode
                                    else -> Icons.Default.Palette
                                },
                                contentDescription = "Toggle Theme",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Card 5: Screen Off Button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F172A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { onScreenOff() }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Turn Screen Off",
                                tint = AccentRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Card 1: GPS Accuracy Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotCol)
                                .alpha(if (!hasFix) gpsPulseAlpha else 1.0f)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = gpsText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Card 2: Satellites
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBgColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SatelliteAlt,
                            contentDescription = null,
                            tint = mainTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = if (hasFix) "$satellitesCount" else "--",
                                color = mainTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Satellites",
                                color = subTextColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Card 3: Speed
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBgColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = mainTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = String.format(Locale.US, "%.0f", speedKmh),
                                color = mainTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "km/h",
                                color = subTextColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Card 4: Compass Pill + Quick Theme Shortcut
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(44.dp)
                        .clickable { onToggleTheme() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CompassCalibration,
                                contentDescription = null,
                                tint = HighlightYellow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = compassHeadingStr,
                                color = HighlightYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Icon(
                            imageVector = when (currentThemeMode) {
                                "light" -> Icons.Default.LightMode
                                "dark" -> Icons.Default.DarkMode
                                else -> Icons.Default.Palette
                            },
                            contentDescription = "Toggle Theme",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Card 5: Screen Off Button
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onScreenOff() }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Turn Screen Off",
                            tint = AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------
// COMPONENT 2: EXTRA-LARGE CHAINAGE DISPLAY WITH DIGIT ODOMETER ANIMATION
// -------------------------------------------------------------------
@Composable
fun CentralChainageDisplay(
    chainageText: String,
    detectedSide: String,
    confidence: MatchConfidence,
    offsetMetres: Double,
    hasActiveRoute: Boolean,
    mainTextColor: Color,
    subTextColor: Color
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val digitFontSize = when {
            containerWidth < 340.dp -> 42.sp
            containerWidth < 380.dp -> 50.sp
            containerWidth < 430.dp -> 58.sp
            else -> 66.sp
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
                // Left Yellow Pill: KM (Small & Compact)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = HighlightYellow,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "KM",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Central Extra-Large Odometer Chainage Digits
                OdometerChainageRow(
                    chainageText = chainageText,
                    mainTextColor = mainTextColor,
                    digitFontSize = digitFontSize
                )

                // Right Yellow Pill: RHS / LHS (Small & Compact)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = HighlightYellow,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = "• $detectedSide",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Quality & Offset Status Line Below Chainage
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (hasActiveRoute) "Model" else "GPS-Derived",
                    color = mainTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "  •  ",
                    color = subTextColor,
                    fontSize = 12.sp
                )
                Text(
                    text = String.format(Locale.US, "Offset: %.1fm", offsetMetres),
                    color = mainTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "  •  ",
                    color = subTextColor,
                    fontSize = 12.sp
                )

                val (confText, confCol) = when (confidence) {
                    MatchConfidence.HIGH -> "Good" to Color(0xFF22C55E)
                    MatchConfidence.MEDIUM -> "Moderate" to Color(0xFF22C55E)
                    MatchConfidence.LOW -> "Poor" to AccentRed
                    MatchConfidence.NO_MATCH -> "No Fix" to Color.Gray
                }
                Text(
                    text = confText,
                    color = confCol,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun OdometerChainageRow(
    chainageText: String,
    mainTextColor: Color,
    digitFontSize: TextUnit = 62.sp
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
                digitFontSize = digitFontSize
            )
        }
    }
}

@Composable
fun OdometerSingleDigit(
    char: Char,
    mainTextColor: Color,
    digitFontSize: TextUnit = 62.sp
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

    val digitColor by animateColorAsState(
        targetValue = if (isChanging) HighlightYellow else mainTextColor,
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
                color = mainTextColor,
                fontSize = digitFontSize,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        } else {
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { h -> h / 2 } + fadeIn(tween(160)))
                        .togetherWith(slideOutVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { h -> -h / 2 } + fadeOut(tween(140)))
                },
                label = "digit_slide"
            ) { targetChar ->
                Text(
                    text = targetChar.toString(),
                    color = digitColor,
                    fontSize = digitFontSize,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Minimalist active digit indicator line
        AnimatedVisibility(
            visible = isChanging,
            enter = fadeIn(tween(150)) + expandHorizontally(),
            exit = fadeOut(tween(200)) + shrinkHorizontally()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(14.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(HighlightYellow)
            )
        }
    }
}

// -------------------------------------------------------------------
// COMPONENT 3: HIGHWAY & PROJECT INFO CARDS (ROW OF 2 CARDS)
// -------------------------------------------------------------------
@Composable
fun HighwayProjectCardsRow(
    highwayTitle: String,
    highwaySubtitle: String,
    packageTitle: String,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Highway Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBgColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AltRoute,
                    contentDescription = null,
                    tint = mainTextColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = highwayTitle,
                        color = mainTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = highwaySubtitle,
                        color = subTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Section / Package Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBgColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = mainTextColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Section",
                        color = subTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = packageTitle,
                        color = mainTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------
// COMPONENT 4: PROMINENT FULL-WIDTH NEXT CHAINAGE CARD
// -------------------------------------------------------------------
@Composable
fun NextChainageCard(
    nextChainage: String,
    distanceMetres: String,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    subTextColor: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .testTag("next_chainage_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = HighlightYellow.copy(alpha = 0.2f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AltRoute,
                            contentDescription = "Next Chainage",
                            tint = HighlightYellow,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "NEXT CHAINAGE",
                        color = subTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (!nextChainage.startsWith("KM") && !nextChainage.contains("-")) "KM $nextChainage" else nextChainage,
                        color = mainTextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = HighlightYellow
            ) {
                Text(
                    text = "$distanceMetres away",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun MapToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDesc: String, tag: String) {
    IconButton(
        onClick = {},
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xD0121212))
            .border(0.8.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

// -------------------------------------------------------------------
// COMPONENT 5: INTERACTIVE SMART KMZ MAP
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
    modifier: Modifier = Modifier
) {
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
                        viewRangeMetres = (viewRangeMetres / zoom).coerceIn(100.0, 15000.0)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f + panOffsetX, h / 2f + panOffsetY)

            // Light / Dark Map background
            drawRect(color = Color(0xFFC7E1C5)) // Soft vector map terrain color

            // Grid
            val gridSpacing = 80.dp.toPx()
            var gx = 0f
            while (gx < w) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.04f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 1f
                )
                gx += gridSpacing
            }
            var gy = 0f
            while (gy < h) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.04f),
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = 1f
                )
                gy += gridSpacing
            }

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

            // Draw KMZ Polylines
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

                    // Outer casing
                    drawPath(
                        path = path,
                        color = Color(0xFF0284C7),
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Inner cyan path
                    drawPath(
                        path = path,
                        color = Color(0xFF38BDF8),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // Draw Markers
            if (routeData != null) {
                for (marker in routeData.markers) {
                    val mOff = toScreen(marker.point)
                    if (mOff.x in -100f..(w + 100f) && mOff.y in -100f..(h + 100f)) {
                        drawCircle(color = HighlightYellow, radius = 5.dp.toPx(), center = mOff)
                        drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = mOff, style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }

            // Live Location Pin with Red Aura
            if (location != null) {
                val userOff = toScreen(location)

                // Pulse Red Ring
                drawCircle(
                    color = Color(0xFFFF0000).copy(alpha = pulseAlpha * 0.5f),
                    radius = 28.dp.toPx(),
                    center = userOff
                )

                // Red Pointer Cone
                val activeHeading = bearing ?: compassHeading
                rotate(degrees = activeHeading, pivot = userOff) {
                    val conePath = Path().apply {
                        moveTo(userOff.x, userOff.y - 18.dp.toPx())
                        lineTo(userOff.x - 9.dp.toPx(), userOff.y + 8.dp.toPx())
                        lineTo(userOff.x, userOff.y + 2.dp.toPx())
                        lineTo(userOff.x + 9.dp.toPx(), userOff.y + 8.dp.toPx())
                        close()
                    }
                    drawPath(path = conePath, color = Color(0xFFFF0000))
                }

                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = userOff)
                drawCircle(color = Color(0xFFFF0000), radius = 4.dp.toPx(), center = userOff)
            }
        }

        // Left Map Tools (Layers, Re-center/Follow, Pin)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapToolButton(icon = Icons.Default.Layers, contentDesc = "Layers", tag = "map_layers_btn")
            IconButton(
                onClick = {
                    isFollowingUser = true
                    panOffsetX = 0f
                    panOffsetY = 0f
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isFollowingUser) HighlightYellow else Color(0xD0121212))
                    .border(0.8.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .testTag("map_follow_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Center",
                    tint = if (isFollowingUser) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            MapToolButton(icon = Icons.Default.Place, contentDesc = "Pin", tag = "map_pin_btn")
        }

        // Top Right Circular Compass Widget
        Surface(
            shape = CircleShape,
            color = Color(0xEE121212),
            border = androidx.compose.foundation.BorderStroke(1.dp, HighlightYellow.copy(alpha = 0.8f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(54.dp)
                .testTag("map_compass_widget")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(46.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    rotate(degrees = -compassHeading, pivot = Offset(cx, cy)) {
                        val p = Path().apply {
                            moveTo(cx, cy - 14.dp.toPx())
                            lineTo(cx - 5.dp.toPx(), cy + 6.dp.toPx())
                            lineTo(cx, cy + 2.dp.toPx())
                            lineTo(cx + 5.dp.toPx(), cy + 6.dp.toPx())
                            close()
                        }
                        drawPath(p, color = Color(0xFFEF4444))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "N",
                        color = Color(0xFFEF4444),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = compassHeadingStr,
                        color = HighlightYellow,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // Right Side Zoom Controls (+ / -)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White.copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.LightGray),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = { viewRangeMetres = (viewRangeMetres * 0.7).coerceAtLeast(100.0) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("map_zoom_in")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(0.8.dp)
                        .background(Color.LightGray)
                )
                IconButton(
                    onClick = { viewRangeMetres = (viewRangeMetres * 1.4).coerceAtMost(15000.0) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("map_zoom_out")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Bottom Right Scale Bar
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xD0000000),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 10.dp, end = 10.dp)
        ) {
            Text(
                text = "50 m",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
