package com.example.ui.dialogs

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageVoiceAssistantSettings
import com.example.model.GeoPoint
import com.example.model.KmzManualCalibration
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.navigation.ActiveChainageNavigationTarget
import com.example.navigation.ChainageDestinationCalculation
import com.example.navigation.ChainageNavigationResolver
import com.example.navigation.ChainageVoiceAssistantCallbacks
import com.example.navigation.ChainageVoiceAssistantManager
import com.example.navigation.ChainageVoiceAssistantStage
import com.example.navigation.ChainageVoiceCommandParser
import com.example.navigation.ExternalNavApp
import com.example.navigation.ExternalNavigationLauncher
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider

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

    val c = CtecsTheme.colors
    val effectiveAccent = if (hasActiveTarget) c.success else c.accent
    val buttonSize = if (isCompact) 50.dp else 54.dp

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(effectiveAccent)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .semantics {
                contentDescription = "Navigate to Chainage"
            }
            .testTag("home_navigate_to_chainage_button"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AltRoute,
                contentDescription = null,
                tint = c.onAccent,
                modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (hasActiveTarget && !activeTargetChainageText.isNullOrBlank()) {
                    activeTargetChainageText
                } else {
                    "GO KM"
                },
                color = c.onAccent,
                fontSize = 8.5.sp,
                lineHeight = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (hasActiveTarget) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * Premium minimalist, borderless Material 3 popup for CTECS Navigate to Chainage with
 * intelligent Chainage Voice Search and Smart Voice Assistant confirmation workflow.
 *
 * Design & Functional Guarantees:
 * - Borderless translucent surface with soft fading margins (no bulky cards or heavy shadows).
 * - Responsive Portrait (vertical) & Landscape (two-column) layouts with IME keyboard protection.
 * - Large, live-sanitized chainage input (`196+494`, `198+000`, `201+500`) + animated Mic button.
 * - Reuses existing [ChainageNavigationResolver] and [ExternalNavigationLauncher] without duplicating
 *   GPS, chainage, or alignment engines.
 * - Never starts navigation from ambiguous or unconfirmed voice recognition.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigateToChainageDialog(
    matchResult: ChainageMatchResult,
    activeRoute: RouteData?,
    currentGpsPoint: GeoPoint?,
    activeCalibration: KmzManualCalibration?,
    projectRoadSide: RoadSide,
    detectedSideLabel: String,
    nextKmStoneSuggestion: String?,
    activeTarget: ActiveChainageNavigationTarget?,
    preferredExternalAppId: String?,
    voiceAssistantSettings: ChainageVoiceAssistantSettings = ChainageVoiceAssistantSettings(),
    onSavePreferredExternalAppId: (String?) -> Unit,
    onStartInAppTarget: (ActiveChainageNavigationTarget) -> Unit,
    onClearActiveTarget: () -> Unit,
    onFeedbackMessage: (String) -> Unit,
    isDark: Boolean,
    onDismiss: () -> Unit,
    externalVoiceManagerForTest: ChainageVoiceAssistantManager? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var popupVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { popupVisible = true }

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
    var hasCalculated by remember { mutableStateOf(submittedQuery.isNotBlank()) }
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
    var pendingChooserActionIsStart by remember { mutableStateOf<Boolean?>(null) }

    // Live road-alignment destination calculation (strictly reuses existing ChainageNavigationResolver)
    val calculation: ChainageDestinationCalculation? = remember(
        hasCalculated,
        submittedQuery,
        matchResult,
        activeRoute?.id,
        activeRoute?.selectedAlignmentId,
        activeCalibration,
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
                activeCalibration = activeCalibration,
                projectRoadSide = projectRoadSide,
                detectedSideOverride = detectedSideLabel
            )
        }
    }

    val latestMatch by rememberUpdatedState(matchResult)
    val latestRoute by rememberUpdatedState(activeRoute)
    val latestCalibration by rememberUpdatedState(activeCalibration)
    val latestRoadSide by rememberUpdatedState(projectRoadSide)
    val latestDetectedSide by rememberUpdatedState(detectedSideLabel)
    val latestCalc by rememberUpdatedState(calculation)
    val latestVoiceSettings by rememberUpdatedState(voiceAssistantSettings)
    val latestSelectedApp by rememberUpdatedState(selectedAppOption)

    fun evaluateForInput(raw: String): ChainageDestinationCalculation {
        return ChainageNavigationResolver.calculateAndResolveDestination(
            rawDestinationInput = raw,
            matchResult = latestMatch,
            activeRoute = latestRoute,
            activeCalibration = latestCalibration,
            projectRoadSide = latestRoadSide,
            detectedSideOverride = latestDetectedSide
        )
    }

    fun triggerCalculate(rawToCalculate: String = chainageInput): ChainageDestinationCalculation {
        val formatted = ChainageNavigationResolver.autoFormatChainageInput(rawToCalculate)
        chainageInput = formatted
        submittedQuery = formatted
        hasCalculated = true
        statusBannerMessage = null
        isStatusError = false
        pendingChooserActionIsStart = null
        focusManager.clearFocus()
        keyboardController?.hide()
        return evaluateForInput(formatted)
    }

    var voiceManagerRef: ChainageVoiceAssistantManager? = null

    fun executeNavigationWithApp(app: ExternalNavApp, isStartMode: Boolean) {
        val calc = latestCalc ?: return
        val destPoint = calc.destinationPoint ?: return
        val destCh = calc.destinationChainage ?: return

        if (isStartMode && latestRoute != null && calc.travelDirection != null) {
            onStartInAppTarget(
                ActiveChainageNavigationTarget(
                    destinationChainage = destCh,
                    destinationPoint = destPoint,
                    alignmentName = calc.alignmentName.ifBlank { latestRoute!!.name },
                    routeId = latestRoute!!.id,
                    travelDirection = calc.travelDirection,
                    initialDistanceMetres = calc.chainageDifferenceMetres
                )
            )
            voiceManagerRef?.markNavigationStarted(destCh.format())
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

    fun invokePrimaryNavAction(isStartMode: Boolean) {
        if (bothAppsInstalled && latestSelectedApp == "ask") {
            pendingChooserActionIsStart = isStartMode
        } else {
            val targetApp = ExternalNavApp.fromId(latestSelectedApp)
                ?: if (isMapplsInstalled && !isGoogleMapsInstalled) {
                    ExternalNavApp.MAPPLS
                } else {
                    ExternalNavApp.GOOGLE_MAPS
                }
            executeNavigationWithApp(targetApp, isStartMode = isStartMode)
        }
    }

    // Dedicated single-instance Voice Assistant Manager scoped strictly to this popup's lifecycle
    val voiceAssistantManager = remember(context, externalVoiceManagerForTest) {
        externalVoiceManagerForTest ?: ChainageVoiceAssistantManager(
            context = context,
            callbacks = object : ChainageVoiceAssistantCallbacks {
                override fun onDestinationRecognized(
                    chainage: Chainage,
                    rawTranscript: String
                ): ChainageDestinationCalculation {
                    return triggerCalculate(chainage.format())
                }

                override fun onRequestRepeatDestination(): ChainageDestinationCalculation? {
                    return latestCalc
                }

                override fun onRequestClearDestination() {
                    chainageInput = ""
                    submittedQuery = ""
                    hasCalculated = false
                    statusBannerMessage = null
                    pendingChooserActionIsStart = null
                }

                override fun onRequestStartNavigation() {
                    invokePrimaryNavAction(isStartMode = true)
                }

                override fun onRequestCancelPopup() {
                    onDismiss()
                }
            }
        )
    }
    voiceManagerRef = voiceAssistantManager

    LaunchedEffect(voiceAssistantSettings) {
        voiceAssistantManager.updateSettings(voiceAssistantSettings)
    }

    val assistantState by voiceAssistantManager.state.collectAsStateWithLifecycle()

    // Track whether user tapped Mic for initial search vs. confirmation reply
    var pendingPermissionForConfirmation by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceAssistantManager.startListening(
                settings = latestVoiceSettings,
                forConfirmation = pendingPermissionForConfirmation
            )
        } else {
            voiceAssistantManager.onPermissionDenied()
        }
    }

    fun requestOrStartVoiceListening(forConfirmation: Boolean) {
        if (!latestVoiceSettings.effectiveVoiceSearchActive()) {
            onFeedbackMessage("Voice Search is disabled in Settings → Navigation → Chainage Voice Assistant")
            return
        }
        if (assistantState.stage == ChainageVoiceAssistantStage.LISTENING) {
            voiceAssistantManager.stopListening()
            return
        }
        focusManager.clearFocus()
        keyboardController?.hide()
        if (voiceAssistantManager.hasRecordAudioPermission()) {
            voiceAssistantManager.startListening(
                settings = latestVoiceSettings,
                forConfirmation = forConfirmation
            )
        } else {
            pendingPermissionForConfirmation = forConfirmation
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Optional automatic listening on popup open (OFF by default per Settings)
    LaunchedEffect(Unit) {
        if (voiceAssistantSettings.autoListenOnPopupOpen &&
            voiceAssistantSettings.effectiveVoiceSearchActive() &&
            voiceAssistantManager.hasRecordAudioPermission() &&
            voiceAssistantManager.isSpeechServiceAvailable()
        ) {
            voiceAssistantManager.startListening(voiceAssistantSettings, forConfirmation = false)
        }
    }

    // Guarantee microphone & TTS resources stop cleanly whenever popup closes
    DisposableEffect(voiceAssistantManager) {
        onDispose {
            if (externalVoiceManagerForTest == null) {
                voiceAssistantManager.release()
            } else {
                voiceAssistantManager.stopListening()
            }
        }
    }

    // AMOLED Deep Black Dark Mode & Professional Light Mode borderless palette
    val c = CtecsTheme.colors
    val baseGlass = if (isDark) c.surface else c.elevatedSurface
    val edgeGlass = if (isDark) c.secondaryBackground else c.secondaryBackground
    val subtleSurface = if (isDark) c.secondarySurface else c.surface
    val hairlineColor = c.subtleSeparator
    val primaryText = c.textPrimary
    val secondaryText = c.textSecondary
    val accentBlue = c.accent
    val accentEmerald = c.success
    val errorColor = c.error
    val warningColor = c.warning

    val currentLiveFormatted = remember(matchResult) {
        if (matchResult.chainage != null) matchResult.displayChainage else "--+---"
    }

    val quickPresets = remember(matchResult.chainage, nextKmStoneSuggestion, activeRoute?.id) {
        buildList {
            if (!nextKmStoneSuggestion.isNullOrBlank() && !nextKmStoneSuggestion.contains("-")) {
                add("Next KM $nextKmStoneSuggestion" to nextKmStoneSuggestion)
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

    val buttonsEnabled = calculation?.canStartNavigation == true

    // Reusable Left Pane (Destination Input + Voice Search + Quick Presets + Voice Status/Confirmation)
    val destinationInputAndVoiceContent: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Active target subtle pill
            if (activeTarget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentEmerald.copy(alpha = 0.11f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
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
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Active: KM ${activeTarget.destinationChainage.format()}",
                            color = primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
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
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("navigate_chainage_reset_target_btn")
                    ) {
                        Text("Reset", color = accentEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Large, borderless Destination Chainage Input + Animated Voice Microphone Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(subtleSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "DESTINATION CHAINAGE",
                        color = secondaryText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (chainageInput.isEmpty()) {
                                Text(
                                    text = "196+494",
                                    color = secondaryText.copy(alpha = 0.45f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            BasicTextField(
                                value = chainageInput,
                                onValueChange = { next ->
                                    val sanitized = ChainageVoiceCommandParser.sanitizeTypingInput(next)
                                    chainageInput = sanitized
                                    statusBannerMessage = null
                                    if (sanitized.isEmpty()) {
                                        submittedQuery = ""
                                        hasCalculated = false
                                    } else if (ChainageNavigationResolver.parseAndValidateDestinationChainage(sanitized).first != null) {
                                        submittedQuery = sanitized
                                        hasCalculated = true
                                    }
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = primaryText
                                ),
                                cursorBrush = SolidColor(accentBlue),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = { triggerCalculate() },
                                    onDone = { triggerCalculate() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("navigate_chainage_input")
                            )
                        }

                        // Quick '+' insertion helper while typing km digits
                        if (!chainageInput.contains("+") && chainageInput.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accentBlue.copy(alpha = 0.16f))
                                    .clickable { chainageInput = "$chainageInput+" }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                    .testTag("navigate_chainage_plus_btn")
                            ) {
                                Text(
                                    text = "+",
                                    color = accentBlue,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        if (chainageInput.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    chainageInput = ""
                                    submittedQuery = ""
                                    hasCalculated = false
                                    statusBannerMessage = null
                                    pendingChooserActionIsStart = null
                                    voiceAssistantManager.resetToIdle()
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("navigate_chainage_clear_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear chainage input",
                                    tint = secondaryText,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                // Prominent Animated Voice Search Microphone Button
                VoiceSearchMicButton(
                    assistantState = assistantState,
                    voiceSearchEnabled = voiceAssistantSettings.effectiveVoiceSearchActive(),
                    accentBlue = accentBlue,
                    accentEmerald = accentEmerald,
                    errorColor = errorColor,
                    warningColor = warningColor,
                    onClick = { requestOrStartVoiceListening(forConfirmation = false) }
                )
            }

            // Voice Assistant Status, Transcript, Ambiguity & Confirmation Strip
            VoiceAssistantStatusPanel(
                assistantState = assistantState,
                primaryText = primaryText,
                secondaryText = secondaryText,
                accentBlue = accentBlue,
                accentEmerald = accentEmerald,
                errorColor = errorColor,
                warningColor = warningColor,
                canStartNavigation = buttonsEnabled,
                onSelectAmbiguousCandidate = { chosen ->
                    voiceAssistantManager.selectDisambiguatedCandidate(chosen, voiceAssistantSettings)
                },
                onConfirmAndStart = {
                    invokePrimaryNavAction(isStartMode = true)
                },
                onSpeakConfirmationReply = {
                    requestOrStartVoiceListening(forConfirmation = true)
                },
                onRepeatDestination = {
                    voiceAssistantManager.handleRecognitionHypotheses(
                        hypotheses = listOf("repeat destination"),
                        forConfirmation = true
                    )
                },
                onChangeDestination = {
                    voiceAssistantManager.handleRecognitionHypotheses(
                        hypotheses = listOf("change destination"),
                        forConfirmation = true
                    )
                    requestOrStartVoiceListening(forConfirmation = false)
                },
                onRequestMicPermissionAgain = {
                    pendingPermissionForConfirmation = false
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )

            // Compact Search / Validate + Quick Chainage Suggestions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentBlue.copy(alpha = 0.14f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { triggerCalculate() }
                        .testTag("navigate_chainage_calculate_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = accentBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Validate",
                            color = accentBlue,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (quickPresets.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        quickPresets.forEach { (label, value) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(subtleSurface)
                                    .clickable {
                                        chainageInput = value
                                        triggerCalculate(value)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = secondaryText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Reusable Right / Bottom Telemetry & App Selection Pane
    val navigationDetailsContent: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (calculation != null) {
                if (!calculation.isValidChainageInput) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(errorColor.copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("navigate_chainage_error_banner")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = errorColor,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = calculation.inputValidationError ?: "Invalid chainage value.",
                            color = errorColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Clean Borderless Telemetry Layout
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(subtleSurface.copy(alpha = if (isDark) 0.36f else 0.58f))
                            .padding(12.dp)
                            .testTag("navigate_chainage_result_card")
                    ) {
                        // Current vs Destination Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CURRENT",
                                    color = secondaryText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = calculation.formattedCurrentChainage.ifBlank { currentLiveFormatted },
                                        color = primaryText,
                                        fontSize = 15.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.testTag("navigate_chainage_current_value")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val smartDir = matchResult.smartDirectionState
                                    if (smartDir != null) {
                                        com.example.ui.components.SmartDirectionIndicatorBadge(
                                            directionState = smartDir,
                                            isDark = isDark,
                                            isCompact = true,
                                            testTagName = "navigate_chainage_side_badge"
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(accentBlue.copy(alpha = 0.14f))
                                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                .testTag("navigate_chainage_side_badge")
                                        ) {
                                            Text(
                                                text = calculation.currentSideLabel,
                                                color = accentBlue,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "DESTINATION",
                                    color = secondaryText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = calculation.formattedDestinationChainage,
                                    color = accentEmerald,
                                    fontSize = 15.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.testTag("navigate_chainage_destination_value")
                                )
                            }
                        }

                        SmartFadeDivider(color = hairlineColor)

                        // Remaining Road-Alignment Distance & Travel Direction Row
                        val displayDistanceText = remember(calculation) {
                            if (calculation.hasChainageEquationOnSpan && calculation.alignmentTravelDistanceMetres != null) {
                                ChainageNavigationResolver.formatChainageDifference(calculation.alignmentTravelDistanceMetres)
                            } else {
                                calculation.formattedChainageDifference
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "REMAINING DISTANCE",
                                    color = secondaryText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = displayDistanceText,
                                    color = accentBlue,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.testTag("navigate_chainage_distance_value")
                                )
                                if (calculation.formattedDifferenceMetresDetail.isNotBlank()) {
                                    Text(
                                        text = "Alignment Δ: ${calculation.formattedDifferenceMetresDetail}",
                                        color = secondaryText,
                                        fontSize = 9.5.sp
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Text(
                                    text = "TRAVEL DIRECTION",
                                    color = secondaryText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                                val dirText = calculation.travelDirection?.let {
                                    "${it.shortArrow} ${it.label}"
                                } ?: "Awaiting GPS Chainage"
                                Text(
                                    text = dirText,
                                    color = primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.testTag("navigate_chainage_direction_value")
                                )
                                if (!calculation.carriagewayCompatibilityNote.isNullOrBlank()) {
                                    Text(
                                        text = calculation.carriagewayCompatibilityNote,
                                        color = secondaryText,
                                        fontSize = 9.5.sp,
                                        textAlign = TextAlign.End,
                                        maxLines = 2
                                    )
                                }
                            }
                        }

                        if (!calculation.chainageEquationNote.isNullOrBlank()) {
                            Text(
                                text = calculation.chainageEquationNote,
                                color = warningColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(warningColor.copy(alpha = 0.10f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        SmartFadeDivider(color = hairlineColor)

                        // Alignment Availability + Mini Route Preview
                        if (calculation.isLocationResolved && calculation.destinationPoint != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = accentEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = calculation.locationDetailsText,
                                        color = primaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("navigate_chainage_location_value")
                                    )
                                    if (calculation.nearestMarkerInfo.isNotBlank()) {
                                        Text(
                                            text = calculation.nearestMarkerInfo,
                                            color = secondaryText,
                                            fontSize = 9.8.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (activeRoute != null) {
                                BorderlessChainageRouteMiniPreview(
                                    routeData = activeRoute,
                                    currentPoint = matchResult.projectedPoint ?: currentGpsPoint,
                                    destinationPoint = calculation.destinationPoint,
                                    currentLabel = calculation.formattedCurrentChainage,
                                    destinationLabel = calculation.formattedDestinationChainage,
                                    isDark = isDark,
                                    accentBlue = accentBlue,
                                    accentEmerald = accentEmerald
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(errorColor.copy(alpha = 0.10f))
                                    .padding(9.dp)
                                    .testTag("navigate_chainage_unavailable_banner")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = errorColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = calculation.unavailableReason
                                        ?: "Destination location cannot be resolved on the active alignment.",
                                    color = errorColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Minimalist Borderless App Selector Row (Google Maps / Mappls)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val appChips = buildList {
                    add(Triple(ExternalNavApp.GOOGLE_MAPS.id, if (isGoogleMapsInstalled) "Google Maps ✓" else "Google Maps", "navigate_app_google_maps"))
                    add(Triple(ExternalNavApp.MAPPLS.id, if (isMapplsInstalled) "Mappls ✓" else "Mappls", "navigate_app_mappls"))
                    if (bothAppsInstalled) {
                        add(Triple("ask", "Ask", "navigate_app_ask"))
                    }
                }
                appChips.forEach { (id, label, tag) ->
                    val selected = selectedAppOption == id
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(if (id == "ask") 0.55f else 1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) accentBlue.copy(alpha = 0.16f) else subtleSurface)
                            .clickable {
                                selectedAppOption = id
                                onSavePreferredExternalAppId(id)
                                if (id != "ask") pendingChooserActionIsStart = null
                            }
                            .padding(vertical = 7.dp, horizontal = 8.dp)
                            .testTag(tag)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) accentBlue else secondaryText,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Inline Smart App Chooser when both apps installed and "Ask" is selected
            AnimatedVisibility(
                visible = pendingChooserActionIsStart != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val isStart = pendingChooserActionIsStart == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentBlue.copy(alpha = 0.10f))
                        .padding(8.dp)
                        .testTag("navigate_app_chooser_prompt"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            pendingChooserActionIsStart = null
                            executeNavigationWithApp(ExternalNavApp.GOOGLE_MAPS, isStart)
                        },
                        shape = RoundedCornerShape(9.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("Google Maps", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            pendingChooserActionIsStart = null
                            executeNavigationWithApp(ExternalNavApp.MAPPLS, isStart)
                        },
                        shape = RoundedCornerShape(9.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentEmerald),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("Mappls", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Status Feedback Pill
            if (!statusBannerMessage.isNullOrBlank()) {
                val bannerCol = if (isStatusError) errorColor else accentEmerald
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(bannerCol.copy(alpha = 0.11f))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("navigate_chainage_status_banner")
                ) {
                    Icon(
                        imageVector = if (isStatusError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = bannerCol,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusBannerMessage!!,
                        color = bannerCol,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // Sticky Bottom Action Row: Direction, Start Navigation, Cancel
    val actionsRowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    voiceAssistantManager.stopListening()
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("navigate_chainage_cancel_btn")
            ) {
                Text(
                    text = "Cancel",
                    color = secondaryText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (buttonsEnabled) accentBlue.copy(alpha = 0.15f) else subtleSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = buttonsEnabled) { invokePrimaryNavAction(isStartMode = false) }
                    .testTag("navigate_chainage_direction_btn")
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        tint = if (buttonsEnabled) accentBlue else secondaryText.copy(alpha = 0.45f),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Direction",
                        color = if (buttonsEnabled) accentBlue else secondaryText.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = { invokePrimaryNavAction(isStartMode = true) },
                enabled = buttonsEnabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentEmerald,
                    contentColor = Color.White,
                    disabledContainerColor = subtleSurface.copy(alpha = 0.6f),
                    disabledContentColor = secondaryText.copy(alpha = 0.45f)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .weight(1.15f)
                    .height(44.dp)
                    .testTag("navigate_chainage_start_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Start Navigation",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    Dialog(
        onDismissRequest = {
            voiceAssistantManager.stopListening()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            val isLandscapeSplit = maxWidth >= 560.dp && maxWidth > maxHeight

            AnimatedVisibility(
                visible = popupVisible,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.95f, animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(140))
            ) {
                // Soft fading borderless outer glass surface (zero heavy outlines or bulky shadows)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isLandscapeSplit) 0.92f else 0.96f)
                        .widthIn(max = if (isLandscapeSplit) 760.dp else 460.dp)
                        .heightIn(max = (this@BoxWithConstraints.maxHeight * 0.92f).coerceAtLeast(320.dp))
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    edgeGlass,
                                    baseGlass.copy(alpha = 0.96f),
                                    baseGlass.copy(alpha = 0.96f),
                                    edgeGlass
                                )
                            )
                        )
                        .testTag("navigate_to_chainage_dialog")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 15.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Minimalist Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(accentBlue.copy(alpha = 0.13f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AltRoute,
                                        contentDescription = null,
                                        tint = accentBlue,
                                        modifier = Modifier.size(18.dp)
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
                                            activeRoute.name
                                        } else {
                                            "Type or speak destination (KM+MMM)"
                                        },
                                        color = secondaryText,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    voiceAssistantManager.stopListening()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("navigate_chainage_close_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Navigate to Chainage",
                                    tint = secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Scrollable responsive body (Portrait stack vs. Landscape 2-column split)
                        if (isLandscapeSplit) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .testTag("navigate_chainage_landscape_layout"),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    destinationInputAndVoiceContent()
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1.05f)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    navigationDetailsContent()
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .testTag("navigate_chainage_portrait_layout"),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                destinationInputAndVoiceContent()
                                navigationDetailsContent()
                            }
                        }

                        // Sticky Bottom Action Bar (never hidden by keyboard)
                        actionsRowContent()
                    }
                }
            }
        }
    }
}
