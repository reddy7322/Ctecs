package com.example.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.OsmTileManager
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.HudMapMarkerStyle
import com.example.model.MiniMapStyle
import com.example.model.RouteData
import com.example.overlay.MiniMapRenderer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow

val LockNavMapRotationKey = SemanticsPropertyKey<Float>("LockNavMapRotation")
var SemanticsPropertyReceiver.lockNavMapRotation by LockNavMapRotationKey

private val DarkMapSaturationMatrix = ColorMatrix().apply {
    setToSaturation(0.18f)
}

/**
 * Google Maps dark navigation-inspired Lock-Screen Navigation display featuring:
 * A. Full-bleed dark minimalist map with bright white highlighted KMZ/KML route & bottom-centered GPS chevron
 * B. Rounded dark top direction/marker card (verified direction + distance + alignment/stretch name)
 * C. Large central live calibrated chainage & LHS/RHS badge
 * D. Compact dark bottom bar showing verified distance, ETA (when moving), speed, and GPS timestamp status
 */
@Composable
fun LockScreenNavigationContent(
    navState: LockScreenNavUiState,
    location: GeoPoint?,
    bearing: Float?,
    compassHeading: Float,
    routeData: RouteData?,
    screensaverSettings: HomeScreensaverSettings,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val mapBackground = Color(0xFF05070A)
    val cardSurface = Color(0xFF181A1F).copy(alpha = 0.96f)
    val cardBorder = Color(0xFF2B313D)
    val bottomBarBg = Color(0xFF0B0E14).copy(alpha = 0.94f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(mapBackground)
            .testTag("lock_screen_nav_root")
    ) {
        val isCompactH = maxHeight < 620.dp

        // A. Full-bleed Dark Navigation Map (matching Google Maps minimalist dark canvas)
        if (screensaverSettings.miniMapVisible) {
            LockScreenVectorNavigationMap(
                location = location,
                bearing = bearing,
                compassHeading = compassHeading,
                routeData = routeData,
                screensaverSettings = screensaverSettings,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("lock_screen_nav_map")
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF060911), Color(0xFF020408))
                        )
                    )
            )
        }

        // Subtle top & bottom gradient scrims for crisp legibility over map tiles
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF05070A).copy(alpha = 0.85f),
                            Color(0xFF05070A).copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0xFF05070A).copy(alpha = 0.78f),
                            Color(0xFF05070A).copy(alpha = 0.96f)
                        )
                    )
                )
        )

        // B & C. Top Instruction Card + Prominent Calibrated Live Chainage Hero
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Optional Sample badge when live GPS/route is absent in preview
            if (navState.isSamplePreview) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f)),
                    modifier = Modifier.testTag("lock_screen_sample_badge")
                ) {
                    Text(
                        text = "SAMPLE DATA • NO LIVE GPS",
                        color = Color(0xFFFCD34D),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.9.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            // Google Maps-style Dark Rounded Top Instruction Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardSurface,
                border = BorderStroke(1.dp, cardBorder),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lock_screen_top_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = if (isCompactH) 10.dp else 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left directional arrow indicator (based strictly on verified side/alignment)
                    val arrowIcon = when (navState.reliableSide) {
                        "RHS" -> Icons.Default.ArrowDownward
                        "LHS" -> Icons.Default.ArrowUpward
                        else -> Icons.Default.Navigation
                    }
                    val arrowTint = when (navState.reliableSide) {
                        "LHS" -> Color(0xFF34D399)
                        "RHS" -> Color(0xFF38BDF8)
                        else -> Color.White
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF232730),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = arrowIcon,
                                contentDescription = navState.directionInstruction,
                                tint = arrowTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Distance + Verified Road / Alignment Stretch + Next KM Marker
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val headlinePrimary = when {
                            screensaverSettings.lockScreenShowNextMarker && !navState.distanceToNextText.isNullOrBlank() ->
                                navState.distanceToNextText
                            navState.reliableSide != null ->
                                navState.directionInstruction
                            else ->
                                navState.directionInstruction
                        }
                        Text(
                            text = headlinePrimary,
                            color = Color.White,
                            fontSize = if (isCompactH) 17.sp else 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("lock_screen_top_distance")
                        )

                        val secondaryTitle = navState.roadOrStretchName
                            ?.takeIf { it.isNotBlank() }
                            ?: navState.directionInstruction
                        Text(
                            text = secondaryTitle,
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("lock_screen_top_road")
                        )

                        if (screensaverSettings.lockScreenShowNextMarker && !navState.nextMarkerLabel.isNullOrBlank()) {
                            Text(
                                text = navState.nextMarkerLabel,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("lock_screen_next_marker")
                            )
                        }
                    }

                    // Compact optional close button ONLY when callback provided (Activity/Preview)
                    if (onClose != null) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("lock_screen_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close navigation display",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Prominent Live Calibrated Chainage Strip right below the instruction card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0B0E14).copy(alpha = 0.88f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.34f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lock_screen_chainage_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (isCompactH) 8.dp else 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LockScreenChainageOdometerText(
                                chainageText = navState.chainageText,
                                isIncreasing = navState.isIncreasingTrend,
                                fontSizeSp = if (isCompactH) 28f else 34f,
                                modifier = Modifier
                                    .testTag("lock_screen_chainage_text")
                                    .clearAndSetSemantics {
                                        text = AnnotatedString(navState.chainageText)
                                        contentDescription = navState.chainageText
                                    }
                            )

                            AnimatedVisibility(
                                visible = navState.reliableSide != null,
                                enter = fadeIn(tween(180)),
                                exit = fadeOut(tween(150))
                            ) {
                                val side = navState.reliableSide
                                if (side != null) {
                                    val sideColor = if (side == "LHS") Color(0xFF34D399) else Color(0xFF38BDF8)
                                    Surface(
                                        shape = CircleShape,
                                        color = sideColor.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, sideColor.copy(alpha = 0.65f)),
                                        modifier = Modifier.testTag("lock_screen_side_badge")
                                    ) {
                                        Text(
                                            text = "$side ${navState.directionArrow}",
                                            color = sideColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.8.sp,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (navState.locationName.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = navState.locationName,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("lock_screen_location_name")
                                )
                            }
                        }
                    }

                    // Compact Speed badge on right when enabled
                    if (screensaverSettings.lockScreenShowSpeed) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF141923),
                            border = BorderStroke(1.dp, Color(0xFF283142)),
                            modifier = Modifier.testTag("lock_screen_speed_badge")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = navState.speedKmh?.toString() ?: "--",
                                    color = if (navState.speedKmh != null) Color.White else Color(0xFF64748B),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    style = TextStyle(fontFeatureSettings = "tnum")
                                )
                                Text(
                                    text = "km/h",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // D. Bottom Navigation Info Strip (Google Maps-style compact bottom readout)
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = bottomBarBg,
            border = BorderStroke(1.dp, cardBorder.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .testTag("lock_screen_bottom_bar")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val primaryMetric = when {
                    screensaverSettings.lockScreenShowEta && !navState.etaDurationText.isNullOrBlank() ->
                        navState.etaDurationText
                    screensaverSettings.lockScreenShowSpeed ->
                        navState.speedText
                    else ->
                        navState.chainageText
                }

                Text(
                    text = primaryMetric,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    modifier = Modifier.testTag("lock_screen_bottom_primary")
                )

                val detailParts = buildList {
                    if (screensaverSettings.lockScreenShowNextMarker && !navState.distanceToNextText.isNullOrBlank()) {
                        add(navState.distanceToNextText)
                    }
                    if (screensaverSettings.lockScreenShowEta && !navState.etaArrivalTimeText.isNullOrBlank()) {
                        add(navState.etaArrivalTimeText)
                    }
                    if (!navState.lastUpdateStatusText.isNullOrBlank()) {
                        add(navState.lastUpdateStatusText)
                    } else if (isEmpty()) {
                        add(navState.chainageText)
                        if (navState.reliableSide != null) add(navState.reliableSide)
                    }
                }

                Text(
                    text = detailParts.joinToString(" • "),
                    color = if (navState.isLiveGpsValid) Color(0xFF94A3B8) else Color(0xFFFBBF24),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("lock_screen_bottom_secondary")
                )
            }
        }
    }
}

@Composable
private fun LockScreenChainageOdometerText(
    chainageText: String,
    isIncreasing: Boolean,
    fontSizeSp: Float,
    modifier: Modifier = Modifier
) {
    val style = TextStyle(
        color = Color.White,
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = "tnum",
        shadow = Shadow(
            color = Color(0xFF38BDF8).copy(alpha = 0.28f),
            offset = Offset.Zero,
            blurRadius = 14f
        )
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        val len = chainageText.length
        for (i in 0 until len) {
            val ch = chainageText[i]
            val slotKey = len - i
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val enterOff = { h: Int -> if (isIncreasing) h / 3 else -h / 3 }
                    val exitOff = { h: Int -> if (isIncreasing) -h / 3 else h / 3 }
                    (slideInVertically(tween(180, easing = FastOutSlowInEasing), enterOff) + fadeIn(tween(160))) togetherWith
                        (slideOutVertically(tween(150), exitOff) + fadeOut(tween(130)))
                },
                label = "lock_chainage_char_$slotKey"
            ) { targetChar ->
                Text(
                    text = targetChar.toString(),
                    style = style,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Full-bleed Google Maps dark navigation canvas for the Lock-Screen Navigation experience.
 * Places the active GPS chevron in the lower third (`70%` down) with a subtle translucent
 * halo, bold bright-white glowing active route polyline, and shared orientation/zoom settings.
 */
@Composable
private fun LockScreenVectorNavigationMap(
    location: GeoPoint?,
    bearing: Float?,
    compassHeading: Float,
    routeData: RouteData?,
    screensaverSettings: HomeScreensaverSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileUpdate by OsmTileManager.tileUpdateEvent.collectAsStateWithLifecycle()
    val reusablePath = remember { Path() }

    val continuousZoom = screensaverSettings.miniMapZoomLevel.coerceIn(
        HomeScreensaverSettings.MIN_ZOOM_LEVEL,
        HomeScreensaverSettings.MAX_ZOOM_LEVEL
    )
    val baseZoom = floor(continuousZoom).toInt().coerceIn(12, 18)
    val fractionalScale = 2.0.pow((continuousZoom - baseZoom).toDouble()).toFloat().coerceIn(0.6f, 2.2f)

    var lastValidLocation by remember { mutableStateOf(location) }
    LaunchedEffect(location) {
        if (location != null) {
            lastValidLocation = location
        }
    }

    val anchorGeo = location ?: lastValidLocation ?: routeData?.bounds?.center ?: GeoPoint(28.6139, 77.2090)

    val effectiveBearing = remember(anchorGeo, bearing, routeData?.id) {
        MiniMapRenderer.resolveEffectiveBearing(anchorGeo, bearing, routeData)
    }
    val rawTargetRotationDeg = remember(screensaverSettings.miniMapOrientation, effectiveBearing) {
        MiniMapRenderer.resolveMapRotationDegrees(
            mode = screensaverSettings.miniMapOrientation,
            effectiveBearing = effectiveBearing
        )
    }

    var unwrappedRotation by remember { mutableFloatStateOf(rawTargetRotationDeg) }
    LaunchedEffect(rawTargetRotationDeg) {
        var delta = (rawTargetRotationDeg - unwrappedRotation) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        unwrappedRotation += delta
    }
    val animatedRotation by animateFloatAsState(
        targetValue = unwrappedRotation,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "lock_nav_map_rot"
    )

    val pulseAnim = rememberInfiniteTransition(label = "lock_nav_pulse")
    val haloAlpha by pulseAnim.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "lock_nav_halo_alpha"
    )

    val darkColorFilter = remember { ColorFilter.colorMatrix(DarkMapSaturationMatrix) }
    val osmMapType = if (screensaverSettings.miniMapStyle == MiniMapStyle.SATELLITE) "satellite" else "standard"
    val mapAlpha = screensaverSettings.miniMapOpacity.coerceIn(0.45f, 1.0f)

    Box(
        modifier = modifier
            .clipToBounds()
            .semantics { lockNavMapRotation = rawTargetRotationDeg }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val _tick = tileUpdate
            val w = size.width
            val h = size.height
            val cx = w / 2f
            // Position GPS vehicle pivot lower on screen (70% down) for forward road lookahead like Google Maps
            val pivotY = h * 0.70f
            val pivot = Offset(cx, pivotY)

            val diagPx = ceil(hypot(w.toDouble(), h.toDouble()) / fractionalScale)
                .toInt()
                .coerceIn(128, 960)

            val osmBmp = OsmTileManager.renderMapBitmap(
                context = context,
                center = anchorGeo,
                widthPx = diagPx,
                heightPx = diagPx,
                zoom = baseZoom,
                panOffsetX = 0f,
                panOffsetY = 0f,
                darkTheme = true,
                mapType = osmMapType
            )

            drawRect(color = Color(0xFF05070A))

            rotate(degrees = animatedRotation, pivot = pivot) {
                if (osmBmp != null) {
                    scale(scale = fractionalScale, pivot = pivot) {
                        drawImage(
                            image = osmBmp.asImageBitmap(),
                            dstOffset = IntOffset(
                                x = (pivot.x - diagPx / 2f).toInt(),
                                y = (pivot.y - diagPx / 2f).toInt()
                            ),
                            dstSize = IntSize(diagPx, diagPx),
                            alpha = (0.72f * mapAlpha).coerceIn(0.25f, 0.92f),
                            colorFilter = if (osmMapType == "standard") darkColorFilter else null
                        )
                    }
                } else {
                    // Ultra-minimalist dark geometric road grid fallback matching screenshot aesthetic
                    val step = 54.dp.toPx()
                    val span = maxOf(w, h) * 1.4f
                    var g = -span
                    while (g <= span) {
                        drawLine(
                            color = Color(0xFF1A202C).copy(alpha = 0.55f),
                            start = Offset(pivot.x + g, pivot.y - span),
                            end = Offset(pivot.x + g, pivot.y + span),
                            strokeWidth = 1.4.dp.toPx()
                        )
                        drawLine(
                            color = Color(0xFF1A202C).copy(alpha = 0.55f),
                            start = Offset(pivot.x - span, pivot.y + g),
                            end = Offset(pivot.x + span, pivot.y + g),
                            strokeWidth = 1.4.dp.toPx()
                        )
                        g += step
                    }
                }

                fun toScreen(pt: GeoPoint): Offset {
                    val (sx, sy) = OsmTileManager.geoToScreen(
                        pt = pt,
                        center = anchorGeo,
                        widthPx = w,
                        heightPx = h,
                        zoom = baseZoom,
                        panOffsetX = 0f,
                        panOffsetY = 0f
                    )
                    // Shift center from h/2 to pivotY so vehicle sits at 70% down looking ahead
                    val shiftedY = sy + (pivotY - h / 2f)
                    return Offset(
                        x = pivot.x + (sx - pivot.x) * fractionalScale,
                        y = pivot.y + (shiftedY - pivot.y) * fractionalScale
                    )
                }

                // Bright high-contrast white active KMZ/KML route polyline matching reference image
                if (screensaverSettings.miniMapShowRouteAlignment && routeData != null && routeData.alignments.isNotEmpty()) {
                    for (align in routeData.alignments) {
                        if (align.points.size < 2) continue
                        reusablePath.reset()
                        var started = false
                        for (pt in align.points) {
                            val off = toScreen(pt)
                            if (!started) {
                                reusablePath.moveTo(off.x, off.y)
                                started = true
                            } else {
                                reusablePath.lineTo(off.x, off.y)
                            }
                        }
                        // Dark highway road casing
                        drawPath(
                            path = reusablePath,
                            color = Color(0xFF1E242E),
                            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // Subtle white luminous outer edge
                        drawPath(
                            path = reusablePath,
                            color = Color.White.copy(alpha = 0.25f),
                            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // Crisp high-contrast bright white center route
                        drawPath(
                            path = reusablePath,
                            color = Color(0xFFF8FAFC),
                            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                // Subtle KM stone marker dots along route
                if (screensaverSettings.miniMapShowRouteAlignment && routeData != null) {
                    for (marker in routeData.markers) {
                        val mOff = toScreen(marker.point)
                        if (mOff.x in -60f..(w + 60f) && mOff.y in -60f..(h + 60f)) {
                            drawCircle(
                                color = Color(0xFF0F172A),
                                radius = 4.dp.toPx(),
                                center = mOff
                            )
                            drawCircle(
                                color = Color(0xFF94A3B8),
                                radius = 2.2.dp.toPx(),
                                center = mOff
                            )
                        }
                    }
                }

                // Current GPS Marker with translucent oval halo & navigation chevron
                val markerGeo = location ?: lastValidLocation ?: anchorGeo
                val userOff = toScreen(markerGeo)
                val markerColor = if (location != null) Color.White else Color(0xFF94A3B8)

                // Soft dark translucent oval navigation halo matching reference screenshot
                drawOval(
                    color = Color(0xFF475569).copy(alpha = haloAlpha),
                    topLeft = Offset(userOff.x - 32.dp.toPx(), userOff.y - 20.dp.toPx()),
                    size = Size(64.dp.toPx(), 40.dp.toPx())
                )

                val activeHeading = effectiveBearing ?: compassHeading
                when (screensaverSettings.miniMapMarkerStyle) {
                    HudMapMarkerStyle.NAVIGATION_ARROW -> {
                        rotate(degrees = activeHeading, pivot = userOff) {
                            reusablePath.reset()
                            reusablePath.moveTo(userOff.x, userOff.y - 13.dp.toPx())
                            reusablePath.lineTo(userOff.x - 10.dp.toPx(), userOff.y + 8.dp.toPx())
                            reusablePath.lineTo(userOff.x, userOff.y + 3.dp.toPx())
                            reusablePath.lineTo(userOff.x + 10.dp.toPx(), userOff.y + 8.dp.toPx())
                            reusablePath.close()
                            drawPath(path = reusablePath, color = markerColor)
                        }
                    }
                    HudMapMarkerStyle.PULSE_DOT -> {
                        drawCircle(color = Color(0xFF38BDF8).copy(alpha = 0.35f), radius = 14.dp.toPx(), center = userOff)
                        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = userOff)
                        drawCircle(color = Color(0xFF38BDF8), radius = 5.dp.toPx(), center = userOff)
                    }
                    HudMapMarkerStyle.CLASSIC_PIN -> {
                        drawCircle(color = Color.White, radius = 9.dp.toPx(), center = userOff)
                        drawCircle(color = Color(0xFF34D399), radius = 6.dp.toPx(), center = userOff)
                    }
                }
            }
        }
    }
}
