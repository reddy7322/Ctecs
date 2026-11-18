package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageMatchResult
import com.example.model.GeoPoint
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.navigation.ActiveChainageNavigationTarget
import com.example.navigation.ChainageDestinationCalculation
import com.example.navigation.ChainageNavigationResolver
import com.example.navigation.ChainageTravelDirection
import com.example.navigation.ExternalNavApp
import com.example.navigation.ExternalNavigationLauncher
import java.util.Locale

/**
 * Compact, minimalist Home Page shortcut button placed immediately to the right of the Next KM Stone card.
 */
@Composable
fun NavigateToChainageQuickButton(
    onClick: () -> Unit,
    hasActiveTarget: Boolean = false,
    activeTargetChainageText: String? = null,
    cardBgColor: Color,
    cardBorderColor: Color,
    mainTextColor: Color,
    isDark: Boolean = false,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "nav_quick_btn_scale"
    )

    val accentColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB)
    val activeAccent = if (isDark) Color(0xFF10B981) else Color(0xFF059669)
    val effectiveTint = if (hasActiveTarget) activeAccent else accentColor
    val buttonSize = if (isCompact) 50.dp else 54.dp
    val surfaceBg = if (isDark) cardBgColor.copy(alpha = 0.90f) else cardBgColor.copy(alpha = 0.97f)
    val borderColor = if (hasActiveTarget) {
        effectiveTint.copy(alpha = 0.65f)
    } else {
        cardBorderColor
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = surfaceBg,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .semantics {
                contentDescription = "Navigate to Chainage"
            }
            .testTag("home_navigate_to_chainage_button")
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 24.dp else 26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(effectiveTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = null,
                        tint = effectiveTint,
                        modifier = Modifier.size(if (isCompact) 15.dp else 16.5.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (hasActiveTarget && !activeTargetChainageText.isNullOrBlank()) {
                        activeTargetChainageText
                    } else {
                        "GO KM"
                    },
                    color = if (hasActiveTarget) effectiveTint else mainTextColor.copy(alpha = 0.88f),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (hasActiveTarget) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(activeAccent)
                )
            }
        }
    }
}

/**
 * Clean, minimalist Material-style popup for CTECS Navigate to Chainage.
 *
 * Features:
 * - Numeric/chainage input field with automatic formatting, clear button, and prominent Search/Calculate button.
 * - Live calculation of Current Chainage, Destination Chainage, Current LHS/RHS, Chainage Difference,
 *   Travel Direction (Increasing/Decreasing), and Destination Location on the active KMZ/KML alignment.
 * - Compact route preview canvas showing current position and destination on the active alignment.
 * - Smart Google Maps and Mappls integration with preferred app selection and installed status detection.
 * - Prominent Direction and Start buttons enabled only after successful destination validation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigateToChainageDialog(
    matchResult: ChainageMatchResult,
    activeRoute: RouteData?,
    currentGpsPoint: GeoPoint?,
    correctionPoints: List<ChainageCorrectionPoint>,
    isCorrectionsEnabled: Boolean,
    projectRoadSide: RoadSide,
    detectedSideLabel: String,
    nextKmStoneSuggestion: String?,
    activeTarget: ActiveChainageNavigationTarget?,
    preferredExternalAppId: String?,
    onSavePreferredExternalAppId: (String?) -> Unit,
    onStartInAppTarget: (ActiveChainageNavigationTarget) -> Unit,
    onClearActiveTarget: () -> Unit,
    onFeedbackMessage: (String) -> Unit,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var chainageInput by remember {
        mutableStateOf(
            activeTarget?.destinationChainage?.format()
                ?: nextKmStoneSuggestion?.takeIf { !it.contains("-") }
                ?: ""
        )
    }
    var submittedQuery by remember {
        mutableStateOf(
            activeTarget?.destinationChainage?.format()
                ?: nextKmStoneSuggestion?.takeIf { !it.contains("-") }
                ?: ""
        )
    }
    var hasCalculated by remember {
        mutableStateOf(submittedQuery.isNotBlank())
    }
    var statusBannerMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }

    // Detect installed supported navigation applications (Google Maps & Mappls)
    val installedApps = remember(context) {
        ExternalNavigationLauncher.getInstalledSupportedApps(context)
    }
    val isGoogleMapsInstalled = remember(installedApps) {
        installedApps.contains(ExternalNavApp.GOOGLE_MAPS)
    }
    val isMapplsInstalled = remember(installedApps) {
        installedApps.contains(ExternalNavApp.MAPPLS)
    }
    val bothAppsInstalled = isGoogleMapsInstalled && isMapplsInstalled

    // Selected navigation app ("google_maps", "mappls", or "ask" when both are installed)
    var selectedAppOption by remember(preferredExternalAppId, isGoogleMapsInstalled, isMapplsInstalled) {
        val saved = ExternalNavApp.fromId(preferredExternalAppId)
        val initial = when {
            saved != null -> saved.id
            bothAppsInstalled -> "ask"
            isGoogleMapsInstalled -> ExternalNavApp.GOOGLE_MAPS.id
            isMapplsInstalled -> ExternalNavApp.MAPPLS.id
            else -> ExternalNavApp.GOOGLE_MAPS.id
        }
        mutableStateOf(initial)
    }

    // Pending app chooser prompt when both apps are installed and user selected "Ask Every Time"
    var pendingChooserActionIsStart by remember { mutableStateOf<Boolean?>(null) }

    // Recalculate automatically whenever submittedQuery, live matchResult, activeRoute, or side changes
    val calculation: ChainageDestinationCalculation? = remember(
        hasCalculated,
        submittedQuery,
        matchResult,
        activeRoute?.id,
        activeRoute?.selectedAlignmentId,
        correctionPoints,
        isCorrectionsEnabled,
        projectRoadSide,
        detectedSideLabel
    ) {
        if (!hasCalculated) {
            null
        } else {
            ChainageNavigationResolver.calculateAndResolveDestination(
                rawDestinationInput = submittedQuery,
                matchResult = matchResult,
                activeRoute = activeRoute,
                correctionPoints = correctionPoints,
                isCorrectionsEnabled = isCorrectionsEnabled,
                projectRoadSide = projectRoadSide,
                detectedSideOverride = detectedSideLabel
            )
        }
    }

    fun triggerCalculate(rawToCalculate: String = chainageInput) {
        val formatted = ChainageNavigationResolver.autoFormatChainageInput(rawToCalculate)
        chainageInput = formatted
        submittedQuery = formatted
        hasCalculated = true
        statusBannerMessage = null
        isStatusError = false
        pendingChooserActionIsStart = null
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun executeNavigationWithApp(app: ExternalNavApp, isStartMode: Boolean) {
        val calc = calculation ?: return
        val destPoint = calc.destinationPoint ?: return
        val destCh = calc.destinationChainage ?: return

        if (isStartMode && activeRoute != null && calc.travelDirection != null) {
            onStartInAppTarget(
                ActiveChainageNavigationTarget(
                    destinationChainage = destCh,
                    destinationPoint = destPoint,
                    alignmentName = calc.alignmentName.ifBlank { activeRoute.name },
                    routeId = activeRoute.id,
                    travelDirection = calc.travelDirection,
                    initialDistanceMetres = calc.chainageDifferenceMetres
                )
            )
        }

        val launchResult = ExternalNavigationLauncher.launchExternalNavigation(
            context = context,
            preferredApp = app,
            isStartMode = isStartMode,
            originPoint = currentGpsPoint,
            destinationPoint = destPoint,
            destinationChainageLabel = destCh.format()
        )

        if (launchResult.launchedExternalApp) {
            statusBannerMessage = launchResult.message
            isStatusError = false
            onFeedbackMessage(launchResult.message)
        } else {
            statusBannerMessage = if (isStartMode) {
                "Destination KM ${destCh.format()} saved in CTECS. ${launchResult.message}"
            } else {
                launchResult.message
            }
            isStatusError = !isStartMode
            onFeedbackMessage(statusBannerMessage!!)
        }
    }

    val dialogBg = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF)
    val cardBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.65f) else Color(0xFFF8FAFC)
    val borderColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accentBlue = if (isDark) Color(0xFF38BDF8) else Color(0xFF2563EB)
    val accentEmerald = if (isDark) Color(0xFF10B981) else Color(0xFF059669)
    val errorColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)

    val currentLiveFormatted = remember(matchResult) {
        if (matchResult.chainage != null) matchResult.displayChainage else "--+---"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = dialogBg,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 460.dp)
                .testTag("navigate_to_chainage_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Minimalist Header Row
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
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(accentBlue.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AltRoute,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Navigate to Chainage",
                                color = primaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (activeRoute != null) {
                                    "Active Alignment: ${activeRoute.name}"
                                } else {
                                    "Enter target chainage (KM+MMM)"
                                },
                                color = secondaryText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("navigate_chainage_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Active In-App Target Banner (with Clear/Reset option)
                if (activeTarget != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentEmerald.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accentEmerald.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = accentEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Active Target: KM ${activeTarget.destinationChainage.format()}",
                                    color = primaryText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = {
                                    onClearActiveTarget()
                                    statusBannerMessage = "Cleared active destination target."
                                    isStatusError = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("navigate_chainage_reset_target_btn")
                            ) {
                                Text(
                                    text = "Reset",
                                    color = accentEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 2. Destination Chainage Input + Prominent Calculate Button
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = chainageInput,
                        onValueChange = { next ->
                            chainageInput = next
                            statusBannerMessage = null
                        },
                        label = { Text("Destination Chainage (e.g. 196+000, 201+500)") },
                        placeholder = { Text("196+494") },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { triggerCalculate() },
                            onDone = { triggerCalculate() }
                        ),
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (!chainageInput.contains("+") && chainageInput.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = accentBlue.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .clickable {
                                                chainageInput = "$chainageInput+"
                                            }
                                            .padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "+",
                                            color = accentBlue,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (chainageInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            chainageInput = ""
                                            submittedQuery = ""
                                            hasCalculated = false
                                            statusBannerMessage = null
                                            pendingChooserActionIsStart = null
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("navigate_chainage_clear_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear destination chainage",
                                            tint = secondaryText,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentBlue,
                            unfocusedBorderColor = borderColor,
                            focusedLabelColor = accentBlue,
                            unfocusedLabelColor = secondaryText
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("navigate_chainage_input")
                    )

                    // Quick Chainage Chips + Prominent Search/Calculate Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { triggerCalculate() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("navigate_chainage_calculate_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Search / Calculate",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (hasCalculated || chainageInput.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    chainageInput = ""
                                    submittedQuery = ""
                                    hasCalculated = false
                                    statusBannerMessage = null
                                    pendingChooserActionIsStart = null
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, borderColor),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("navigate_chainage_reset_btn")
                            ) {
                                Text(
                                    text = "Clear",
                                    color = secondaryText,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Quick presets from current chainage / route bounds if available
                    val quickPresets = remember(matchResult.chainage, nextKmStoneSuggestion, activeRoute?.id) {
                        buildList {
                            if (!nextKmStoneSuggestion.isNullOrBlank() && !nextKmStoneSuggestion.contains("-")) {
                                add("Next KM ($nextKmStoneSuggestion)" to nextKmStoneSuggestion)
                            }
                            val curM = matchResult.chainage?.metres
                            if (curM != null) {
                                val plus1Km = Chainage(curM + 1000L).format()
                                add("+1 km ($plus1Km)" to plus1Km)
                                if (curM >= 1000L) {
                                    val minus1Km = Chainage(curM - 1000L).format()
                                    add("-1 km ($minus1Km)" to minus1Km)
                                }
                            }
                        }.distinctBy { it.second }.take(3)
                    }

                    if (quickPresets.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            quickPresets.forEach { (label, value) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = cardBg,
                                    border = BorderStroke(0.8.dp, borderColor),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            chainageInput = value
                                            triggerCalculate(value)
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        color = secondaryText,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Validation Error or Calculation Results
                if (calculation != null) {
                    if (!calculation.isValidChainageInput) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = errorColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, errorColor.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("navigate_chainage_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = errorColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = calculation.inputValidationError ?: "Invalid chainage value.",
                                    color = errorColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        // Valid Destination Chainage -> Show Telemetry & Distance Calculation Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("navigate_chainage_result_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Current Chainage vs Destination Chainage Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "CURRENT CHAINAGE",
                                            color = secondaryText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = calculation.formattedCurrentChainage.ifBlank { currentLiveFormatted },
                                                color = primaryText,
                                                fontSize = 16.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.testTag("navigate_chainage_current_value")
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = accentBlue.copy(alpha = 0.14f),
                                                border = BorderStroke(0.8.dp, accentBlue.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = calculation.currentSideLabel,
                                                    color = accentBlue,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "DESTINATION CHAINAGE",
                                            color = secondaryText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = calculation.formattedDestinationChainage,
                                            color = accentEmerald,
                                            fontSize = 16.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.testTag("navigate_chainage_destination_value")
                                        )
                                    }
                                }

                                HorizontalDivider(color = borderColor.copy(alpha = 0.7f))

                                // Distance & Travel Direction Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "DISTANCE",
                                            color = secondaryText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = calculation.formattedChainageDifference,
                                            color = accentBlue,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.testTag("navigate_chainage_distance_value")
                                        )
                                        if (calculation.formattedDifferenceMetresDetail.isNotBlank()) {
                                            Text(
                                                text = "Chainage Δ: ${calculation.formattedDifferenceMetresDetail}",
                                                color = secondaryText,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.weight(1.1f)
                                    ) {
                                        Text(
                                            text = "DIRECTION",
                                            color = secondaryText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        val dirText = calculation.travelDirection?.let {
                                            "${it.shortArrow} ${it.label}"
                                        } ?: "Awaiting Live Chainage"
                                        Text(
                                            text = dirText,
                                            color = primaryText,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.testTag("navigate_chainage_direction_value")
                                        )
                                        if (!calculation.carriagewayCompatibilityNote.isNullOrBlank()) {
                                            Text(
                                                text = calculation.carriagewayCompatibilityNote,
                                                color = secondaryText,
                                                fontSize = 9.8.sp,
                                                textAlign = TextAlign.End,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }

                                // Chainage Equation Warning if applicable
                                if (!calculation.chainageEquationNote.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                        border = BorderStroke(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = calculation.chainageEquationNote,
                                            color = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = borderColor.copy(alpha = 0.7f))

                                // Destination Location Details or Unavailable Explanation
                                if (calculation.isLocationResolved && calculation.destinationPoint != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = null,
                                                tint = accentEmerald,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = calculation.locationDetailsText,
                                                color = primaryText,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.testTag("navigate_chainage_location_value")
                                            )
                                        }
                                        if (calculation.nearestMarkerInfo.isNotBlank()) {
                                            Text(
                                                text = calculation.nearestMarkerInfo,
                                                color = secondaryText,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }

                                    // Small Route Preview Canvas showing Active Alignment, Current Position & Destination
                                    if (activeRoute != null) {
                                        ChainageRouteMiniPreview(
                                            routeData = activeRoute,
                                            currentPoint = matchResult.projectedPoint ?: currentGpsPoint,
                                            destinationPoint = calculation.destinationPoint,
                                            currentLabel = calculation.formattedCurrentChainage,
                                            destinationLabel = calculation.formattedDestinationChainage,
                                            isDark = isDark,
                                            accentBlue = accentBlue,
                                            accentEmerald = accentEmerald,
                                            borderColor = borderColor
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = errorColor.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, errorColor.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("navigate_chainage_unavailable_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = errorColor,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = calculation.unavailableReason
                                                    ?: "Destination location cannot be reliably resolved on the active alignment.",
                                                color = errorColor,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Navigation App Selection (Google Maps / Mappls)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "NAVIGATION APP",
                        color = secondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val gmapsSelected = selectedAppOption == ExternalNavApp.GOOGLE_MAPS.id
                        FilterChip(
                            selected = gmapsSelected,
                            onClick = {
                                selectedAppOption = ExternalNavApp.GOOGLE_MAPS.id
                                onSavePreferredExternalAppId(ExternalNavApp.GOOGLE_MAPS.id)
                                pendingChooserActionIsStart = null
                            },
                            label = {
                                Text(
                                    text = if (isGoogleMapsInstalled) "Google Maps ✓" else "Google Maps",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentBlue.copy(alpha = 0.18f),
                                selectedLabelColor = accentBlue
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("navigate_app_google_maps")
                        )

                        val mapplsSelected = selectedAppOption == ExternalNavApp.MAPPLS.id
                        FilterChip(
                            selected = mapplsSelected,
                            onClick = {
                                selectedAppOption = ExternalNavApp.MAPPLS.id
                                onSavePreferredExternalAppId(ExternalNavApp.MAPPLS.id)
                                pendingChooserActionIsStart = null
                            },
                            label = {
                                Text(
                                    text = if (isMapplsInstalled) "Mappls ✓" else "Mappls",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentBlue.copy(alpha = 0.18f),
                                selectedLabelColor = accentBlue
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("navigate_app_mappls")
                        )

                        if (bothAppsInstalled) {
                            val askSelected = selectedAppOption == "ask"
                            FilterChip(
                                selected = askSelected,
                                onClick = {
                                    selectedAppOption = "ask"
                                    onSavePreferredExternalAppId("ask")
                                },
                                label = {
                                    Text(
                                        text = "Ask",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentBlue.copy(alpha = 0.18f),
                                    selectedLabelColor = accentBlue
                                ),
                                modifier = Modifier.testTag("navigate_app_ask")
                            )
                        }
                    }
                }

                // Inline Smart App Chooser when both Google Maps and Mappls are installed and "Ask" is active
                AnimatedVisibility(
                    visible = pendingChooserActionIsStart != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val isStart = pendingChooserActionIsStart == true
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentBlue.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, accentBlue.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("navigate_app_chooser_prompt")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isStart) "Start Navigation with:" else "Open Directions with:",
                                color = primaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        pendingChooserActionIsStart = null
                                        executeNavigationWithApp(ExternalNavApp.GOOGLE_MAPS, isStart)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Google Maps", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        pendingChooserActionIsStart = null
                                        executeNavigationWithApp(ExternalNavApp.MAPPLS, isStart)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentEmerald),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Mappls", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Status feedback banner
                if (!statusBannerMessage.isNullOrBlank()) {
                    val bannerCol = if (isStatusError) errorColor else accentEmerald
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bannerCol.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, bannerCol.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("navigate_chainage_status_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isStatusError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = bannerCol,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = statusBannerMessage!!,
                                color = bannerCol,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 5. Two Prominent Functional Buttons: Direction & Start
                val buttonsEnabled = calculation?.canStartNavigation == true

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A. Direction Button
                    OutlinedButton(
                        onClick = {
                            if (bothAppsInstalled && selectedAppOption == "ask") {
                                pendingChooserActionIsStart = false
                            } else {
                                val targetApp = ExternalNavApp.fromId(selectedAppOption)
                                    ?: if (isMapplsInstalled && !isGoogleMapsInstalled) {
                                        ExternalNavApp.MAPPLS
                                    } else {
                                        ExternalNavApp.GOOGLE_MAPS
                                    }
                                executeNavigationWithApp(targetApp, isStartMode = false)
                            }
                        },
                        enabled = buttonsEnabled,
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(
                            1.2.dp,
                            if (buttonsEnabled) accentBlue else borderColor
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("navigate_chainage_direction_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            tint = if (buttonsEnabled) accentBlue else secondaryText.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Direction",
                            color = if (buttonsEnabled) accentBlue else secondaryText.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // B. Start Button
                    Button(
                        onClick = {
                            if (bothAppsInstalled && selectedAppOption == "ask") {
                                pendingChooserActionIsStart = true
                            } else {
                                val targetApp = ExternalNavApp.fromId(selectedAppOption)
                                    ?: if (isMapplsInstalled && !isGoogleMapsInstalled) {
                                        ExternalNavApp.MAPPLS
                                    } else {
                                        ExternalNavApp.GOOGLE_MAPS
                                    }
                                executeNavigationWithApp(targetApp, isStartMode = true)
                            }
                        },
                        enabled = buttonsEnabled,
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentEmerald,
                            contentColor = Color.White,
                            disabledContainerColor = borderColor.copy(alpha = 0.5f),
                            disabledContentColor = secondaryText.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("navigate_chainage_start_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Start",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact route preview displaying the active KMZ/KML alignment polyline,
 * the current device/chainage position, and the resolved destination chainage coordinate.
 */
@Composable
private fun ChainageRouteMiniPreview(
    routeData: RouteData,
    currentPoint: GeoPoint?,
    destinationPoint: GeoPoint,
    currentLabel: String,
    destinationLabel: String,
    isDark: Boolean,
    accentBlue: Color,
    accentEmerald: Color,
    borderColor: Color
) {
    val activeAlign = routeData.activeAlignment ?: routeData.alignments.firstOrNull()
    val alignPoints = remember(activeAlign?.id, routeData.markers.size) {
        val pts = activeAlign?.points.orEmpty()
        if (pts.size >= 2) pts else routeData.markers.map { it.point }
    }

    if (alignPoints.isEmpty()) return

    val previewBg = if (isDark) Color(0xFF090D16) else Color(0xFFF1F5F9)

    Surface(
        shape = RoundedCornerShape(9.dp),
        color = previewBg,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .testTag("navigate_chainage_route_preview")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                val allPts = buildList {
                    addAll(alignPoints)
                    if (currentPoint != null) add(currentPoint)
                    add(destinationPoint)
                }
                val minLat = allPts.minOf { it.latitude }
                val maxLat = allPts.maxOf { it.latitude }
                val minLng = allPts.minOf { it.longitude }
                val maxLng = allPts.maxOf { it.longitude }

                val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
                val lngSpan = (maxLng - minLng).coerceAtLeast(0.0005)

                fun project(pt: GeoPoint): Offset {
                    val x = (((pt.longitude - minLng) / lngSpan).toFloat().coerceIn(0f, 1f)) * size.width
                    val y = (1f - ((pt.latitude - minLat) / latSpan).toFloat().coerceIn(0f, 1f)) * size.height
                    return Offset(x, y)
                }

                // Draw alignment polyline
                if (alignPoints.size >= 2) {
                    val path = Path()
                    val step = (alignPoints.size / 120).coerceAtLeast(1)
                    var started = false
                    var idx = 0
                    while (idx < alignPoints.size) {
                        val o = project(alignPoints[idx])
                        if (!started) {
                            path.moveTo(o.x, o.y)
                            started = true
                        } else {
                            path.lineTo(o.x, o.y)
                        }
                        idx += step
                    }
                    val lastO = project(alignPoints.last())
                    path.lineTo(lastO.x, lastO.y)

                    drawPath(
                        path = path,
                        color = accentBlue.copy(alpha = 0.45f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // Draw line connecting current position to destination
                val destOffset = project(destinationPoint)
                if (currentPoint != null) {
                    val curOffset = project(currentPoint)
                    drawLine(
                        color = accentEmerald.copy(alpha = 0.75f),
                        start = curOffset,
                        end = destOffset,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Current point halo & dot
                    drawCircle(
                        color = accentBlue.copy(alpha = 0.28f),
                        radius = 8.dp.toPx(),
                        center = curOffset
                    )
                    drawCircle(
                        color = accentBlue,
                        radius = 4.2.dp.toPx(),
                        center = curOffset
                    )
                }

                // Destination point halo & pin dot
                drawCircle(
                    color = accentEmerald.copy(alpha = 0.30f),
                    radius = 9.dp.toPx(),
                    center = destOffset
                )
                drawCircle(
                    color = accentEmerald,
                    radius = 4.8.dp.toPx(),
                    center = destOffset
                )
            }

            // Legend overlay
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentBlue)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Current: $currentLabel",
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentEmerald)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Dest: $destinationLabel",
                        color = accentEmerald,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
