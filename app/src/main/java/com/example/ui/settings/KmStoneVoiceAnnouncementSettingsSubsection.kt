package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.KmAlertOutputMode
import com.example.model.KmStoneAlertSettings
import com.example.model.KmVoiceAnnouncementStyle
import com.example.model.KmVoiceSpeed
import com.example.navigation.KmStoneVoiceAnnouncementManager
import com.example.navigation.KmStoneVoiceScriptFormatter
import com.example.navigation.SmartTtsManager
import kotlin.math.roundToInt

/**
 * Dedicated expandable subsection inside:
 * Settings -> Navigation Alerts -> KM Stone Monitoring -> Voice Announcement
 *
 * Uses Android's native TextToSpeech engine with selectable styles (1-6), dynamic multi-interval
 * road-alignment thresholds, alert output coordination, language/voice/speed/volume controls,
 * and isolated sample previews that never alter live KM Stone monitoring state.
 */
@Composable
internal fun KmStoneVoiceAnnouncementSettingsSubsection(
    settings: KmStoneAlertSettings,
    expanded: Boolean,
    accentColor: Color,
    onToggleExpanded: () -> Unit,
    onUpdateSettings: ((KmStoneAlertSettings) -> KmStoneAlertSettings) -> Unit,
    onFeedbackMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ttsStatus by SmartTtsManager.status.collectAsStateWithLifecycle()
    val availableLanguages = ttsStatus.availableLanguages
    val availableVoices = ttsStatus.availableVoices

    var showStyleMenu by remember { mutableStateOf(false) }
    var showOutputModeMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }
    var customTemplateDraft by remember { mutableStateOf(settings.voiceCustomTemplate) }

    LaunchedEffect(expanded, settings.voiceAnnouncementEnabled, settings.voiceEnginePackage, settings.voiceLanguageTag) {
        if (expanded && settings.voiceAnnouncementEnabled) {
            SmartTtsManager.initializeIfNeeded(
                context = context,
                preferredEngine = settings.voiceEnginePackage,
                languageTag = settings.voiceLanguageTag,
                forceReinitialize = false
            )
        }
    }

    DisposableEffect(lifecycleOwner, expanded, settings.voiceAnnouncementEnabled, settings.voiceEnginePackage, settings.voiceLanguageTag) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && expanded && settings.voiceAnnouncementEnabled) {
                SmartTtsManager.revalidateOnResume(
                    context = context,
                    voiceOutputEnabled = true,
                    preferredEngine = settings.voiceEnginePackage,
                    languageTag = settings.voiceLanguageTag,
                    forceReinitialize = !SmartTtsManager.status.value.isReady
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(settings.voiceCustomTemplate) {
        if (customTemplateDraft != settings.voiceCustomTemplate) {
            customTemplateDraft = settings.voiceCustomTemplate
        }
    }

    val filteredVoices = remember(availableVoices, settings.voiceLanguageTag) {
        KmStoneVoiceAnnouncementManager.getVoicesForLanguage(settings.voiceLanguageTag)
    }

    val selectedLanguageLabel = remember(availableLanguages, settings.voiceLanguageTag) {
        if (settings.voiceLanguageTag.isBlank()) {
            "System Default"
        } else {
            availableLanguages.firstOrNull {
                it.languageTag.equals(settings.voiceLanguageTag, ignoreCase = true)
            }?.displayLabel ?: settings.voiceLanguageTag
        }
    }

    val selectedVoiceLabel = remember(availableVoices, settings.voiceName) {
        if (settings.voiceName.isBlank()) {
            "System Default Voice"
        } else {
            availableVoices.firstOrNull { it.name == settings.voiceName }?.displayLabel
                ?: settings.voiceName.substringAfterLast('#')
        }
    }

    val intervalLabels = remember(
        settings.voiceInterval1000m,
        settings.voiceInterval500m,
        settings.voiceInterval200m,
        settings.voiceInterval100m,
        settings.voiceInterval50m,
        settings.voiceAnnounceAtArrival
    ) {
        buildList {
            if (settings.voiceInterval1000m) add("1000m")
            if (settings.voiceInterval500m) add("500m")
            if (settings.voiceInterval200m) add("200m")
            if (settings.voiceInterval100m) add("100m")
            if (settings.voiceInterval50m) add("50m")
            if (settings.voiceAnnounceAtArrival) add("Arrival")
        }
    }

    val voiceSummary = if (!settings.voiceAnnouncementEnabled) {
        "OFF • Android TTS"
    } else {
        val intervalsPart = if (intervalLabels.isEmpty()) "No intervals" else intervalLabels.joinToString(", ")
        "ON • ${settings.voiceStyle.label} • $intervalsPart"
    }

    KmFlatExpandableHeader(
        title = "Voice Announcement",
        subtitle = voiceSummary,
        expanded = expanded,
        accentColor = accentColor,
        tag = "km_alert_sub_voice",
        onToggle = onToggleExpanded
    )

    AnimatedVisibility(visible = expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Master Voice Announcement ON/OFF
            KmFlatSwitchRow(
                title = "Voice Announcement",
                subtitle = "Speak upcoming KM Stones along calibrated road alignment using Android TTS",
                checked = settings.voiceAnnouncementEnabled,
                accentColor = accentColor,
                tag = "km_alert_switch_voice_enabled",
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        SmartTtsManager.stopSpeaking()
                    } else {
                        SmartTtsManager.initializeIfNeeded(
                            context = context,
                            preferredEngine = settings.voiceEnginePackage,
                            languageTag = settings.voiceLanguageTag,
                            forceReinitialize = true
                        )
                    }
                    onUpdateSettings { it.copy(voiceAnnouncementEnabled = enabled) }
                }
            )

            TtsEngineStatusAndSetupCard(
                voiceOutputEnabled = settings.voiceAnnouncementEnabled,
                ttsStatus = ttsStatus,
                accentColor = accentColor,
                contextSuffix = "Existing sound and vibration alerts remain fully active.",
                onInstallOrConfigure = { forMissingData ->
                    onFeedbackMessage(SmartTtsManager.launchSetupFlow(context, forMissingVoiceData = forMissingData))
                },
                onCheckAgain = {
                    SmartTtsManager.revalidateOnResume(
                        context = context,
                        voiceOutputEnabled = true,
                        preferredEngine = settings.voiceEnginePackage,
                        languageTag = settings.voiceLanguageTag,
                        forceReinitialize = true
                    )
                    onFeedbackMessage("Checking installed Android Text-to-Speech engines…")
                }
            )

            // Alert Output Coordination (Voice / Sound / Vibration)
            Box(modifier = Modifier.fillMaxWidth()) {
                KmFlatDropdownRow(
                    label = "Alert Output Coordination",
                    valueText = settings.alertOutputMode.label,
                    accentColor = accentColor,
                    tag = "km_alert_dropdown_output_mode",
                    onClick = { showOutputModeMenu = true }
                )
                DropdownMenu(
                    expanded = showOutputModeMenu,
                    onDismissRequest = { showOutputModeMenu = false }
                ) {
                    KmAlertOutputMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.label,
                                    fontWeight = if (settings.alertOutputMode == mode) FontWeight.Bold else FontWeight.Normal,
                                    color = if (settings.alertOutputMode == mode) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showOutputModeMenu = false
                                onUpdateSettings { it.copy(alertOutputMode = mode) }
                            },
                            modifier = Modifier.testTag("km_voice_output_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            // Dynamic Distance Intervals (multi-select)
            Text(
                text = "Dynamic Announcement Intervals (Road Alignment)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val intervalChoices = listOf(
                    Triple("1000 m", settings.voiceInterval1000m, "km_voice_interval_1000m") to
                        { cur: KmStoneAlertSettings -> cur.copy(voiceInterval1000m = !cur.voiceInterval1000m) },
                    Triple("500 m", settings.voiceInterval500m, "km_voice_interval_500m") to
                        { cur: KmStoneAlertSettings -> cur.copy(voiceInterval500m = !cur.voiceInterval500m) },
                    Triple("200 m", settings.voiceInterval200m, "km_voice_interval_200m") to
                        { cur: KmStoneAlertSettings -> cur.copy(voiceInterval200m = !cur.voiceInterval200m) },
                    Triple("100 m", settings.voiceInterval100m, "km_voice_interval_100m") to
                        { cur: KmStoneAlertSettings -> cur.copy(voiceInterval100m = !cur.voiceInterval100m) },
                    Triple("50 m", settings.voiceInterval50m, "km_voice_interval_50m") to
                        { cur: KmStoneAlertSettings -> cur.copy(voiceInterval50m = !cur.voiceInterval50m) },
                    Triple("At Arrival", settings.voiceAnnounceAtArrival, "km_voice_interval_arrival") to
                        { cur: KmStoneAlertSettings -> cur.copy(voiceAnnounceAtArrival = !cur.voiceAnnounceAtArrival) }
                )
                intervalChoices.forEach { (meta, updater) ->
                    val (label, selected, tag) = meta
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateSettings(updater) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.18f),
                            selectedLabelColor = accentColor
                        ),
                        modifier = Modifier.testTag(tag)
                    )
                }
            }

            // Selectable Announcement Style (Styles 1-6)
            Box(modifier = Modifier.fillMaxWidth()) {
                KmFlatDropdownRow(
                    label = "Announcement Style",
                    valueText = settings.voiceStyle.label,
                    accentColor = accentColor,
                    tag = "km_alert_dropdown_voice_style",
                    onClick = { showStyleMenu = true }
                )
                DropdownMenu(
                    expanded = showStyleMenu,
                    onDismissRequest = { showStyleMenu = false }
                ) {
                    KmVoiceAnnouncementStyle.entries.forEach { style ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = style.label,
                                        fontWeight = if (settings.voiceStyle == style) FontWeight.Bold else FontWeight.Normal,
                                        color = if (settings.voiceStyle == style) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = style.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            onClick = {
                                showStyleMenu = false
                                onUpdateSettings { it.copy(voiceStyle = style) }
                            },
                            modifier = Modifier.testTag("km_voice_style_${style.name.lowercase()}")
                        )
                    }
                }
            }

            // Style 6 – Custom Announcement Builder
            val previewSettingsForText = remember(settings, customTemplateDraft) {
                if (settings.voiceStyle == KmVoiceAnnouncementStyle.CUSTOM) {
                    settings.copy(voiceCustomTemplate = customTemplateDraft)
                } else {
                    settings
                }
            }

            if (settings.voiceStyle == KmVoiceAnnouncementStyle.CUSTOM) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                    border = BorderStroke(0.75.dp, accentColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("km_voice_custom_builder_panel")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Custom Announcement Fields",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val customFields = listOf(
                                Triple("Chainage", settings.voiceCustomIncludeChainage, "km_voice_custom_field_chainage") to
                                    { cur: KmStoneAlertSettings -> cur.copy(voiceCustomIncludeChainage = !cur.voiceCustomIncludeChainage) },
                                Triple("LHS / RHS", settings.voiceCustomIncludeDirectionSide, "km_voice_custom_field_direction") to
                                    { cur: KmStoneAlertSettings -> cur.copy(voiceCustomIncludeDirectionSide = !cur.voiceCustomIncludeDirectionSide) },
                                Triple("Distance", settings.voiceCustomIncludeDistance, "km_voice_custom_field_distance") to
                                    { cur: KmStoneAlertSettings -> cur.copy(voiceCustomIncludeDistance = !cur.voiceCustomIncludeDistance) },
                                Triple("Speed", settings.voiceCustomIncludeSpeed, "km_voice_custom_field_speed") to
                                    { cur: KmStoneAlertSettings -> cur.copy(voiceCustomIncludeSpeed = !cur.voiceCustomIncludeSpeed) },
                                Triple("Arrival Status", settings.voiceCustomIncludeArrivalStatus, "km_voice_custom_field_status") to
                                    { cur: KmStoneAlertSettings -> cur.copy(voiceCustomIncludeArrivalStatus = !cur.voiceCustomIncludeArrivalStatus) }
                            )
                            customFields.forEach { (meta, updater) ->
                                val (label, selected, tag) = meta
                                FilterChip(
                                    selected = selected,
                                    onClick = { onUpdateSettings(updater) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.20f),
                                        selectedLabelColor = accentColor
                                    ),
                                    modifier = Modifier.testTag(tag)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = customTemplateDraft,
                            onValueChange = { customTemplateDraft = it.take(180) },
                            label = { Text("Optional Custom Format Template") },
                            placeholder = { Text("e.g. {side}, {distance} to KM {stone}") },
                            supportingText = {
                                Text(
                                    text = "Tokens: {stone}, {chainage}, {side}, {distance}, {speed}, {status}. Leave blank to use selected chips.",
                                    fontSize = 10.sp
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("km_voice_custom_template_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val rec = KmStoneVoiceAnnouncementManager.previewCustomAnnouncement(
                                        context = context,
                                        settings = previewSettingsForText,
                                        isArrival = false
                                    )
                                    onFeedbackMessage("Preview: ${rec.spokenText}")
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("km_voice_preview_custom_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Preview Custom Announcement",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preview", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    onUpdateSettings { it.copy(voiceCustomTemplate = customTemplateDraft.trim()) }
                                    onFeedbackMessage("Custom announcement format saved")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("km_voice_custom_template_apply_btn")
                            ) {
                                Text("Save Format", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Live Spoken Preview Card (shows exact approaching + arrival wording)
            val sampleApproachText = remember(previewSettingsForText) {
                KmStoneVoiceScriptFormatter.buildAnnouncementText(
                    event = KmStoneVoiceScriptFormatter.buildSamplePreviewEvent(isArrival = false, intervalMetres = 500),
                    settings = previewSettingsForText
                )
            }
            val sampleArrivalText = remember(previewSettingsForText) {
                KmStoneVoiceScriptFormatter.buildAnnouncementText(
                    event = KmStoneVoiceScriptFormatter.buildSamplePreviewEvent(isArrival = true, intervalMetres = 0),
                    settings = previewSettingsForText
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("km_voice_live_preview_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Spoken Preview (Sample KM 198+000)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = "500 m: \"$sampleApproachText\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Arrival: \"$sampleArrivalText\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // TTS Language Selector
            Box(modifier = Modifier.fillMaxWidth()) {
                KmFlatDropdownRow(
                    label = "Language",
                    valueText = selectedLanguageLabel,
                    accentColor = accentColor,
                    tag = "km_alert_dropdown_voice_language",
                    onClick = { showLanguageMenu = true }
                )
                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = { showLanguageMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "System Default",
                                fontWeight = if (settings.voiceLanguageTag.isBlank()) FontWeight.Bold else FontWeight.Normal,
                                color = if (settings.voiceLanguageTag.isBlank()) accentColor else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showLanguageMenu = false
                            onUpdateSettings { it.copy(voiceLanguageTag = "", voiceName = "") }
                        },
                        modifier = Modifier.testTag("km_voice_lang_default")
                    )
                    availableLanguages.forEach { lang ->
                        val isSel = settings.voiceLanguageTag.equals(lang.languageTag, ignoreCase = true)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = lang.displayLabel,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showLanguageMenu = false
                                onUpdateSettings { it.copy(voiceLanguageTag = lang.languageTag, voiceName = "") }
                            }
                        )
                    }
                }
            }

            // TTS Voice Selector
            Box(modifier = Modifier.fillMaxWidth()) {
                KmFlatDropdownRow(
                    label = "Announcement Voice",
                    valueText = selectedVoiceLabel,
                    accentColor = accentColor,
                    tag = "km_alert_dropdown_voice_name",
                    onClick = { showVoiceMenu = true }
                )
                DropdownMenu(
                    expanded = showVoiceMenu,
                    onDismissRequest = { showVoiceMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "System Default Voice",
                                fontWeight = if (settings.voiceName.isBlank()) FontWeight.Bold else FontWeight.Normal,
                                color = if (settings.voiceName.isBlank()) accentColor else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showVoiceMenu = false
                            onUpdateSettings { it.copy(voiceName = "") }
                        },
                        modifier = Modifier.testTag("km_voice_name_default")
                    )
                    filteredVoices.take(25).forEach { voice ->
                        val isSel = settings.voiceName == voice.name
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = voice.displayLabel,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) accentColor else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                showVoiceMenu = false
                                onUpdateSettings { it.copy(voiceName = voice.name) }
                            }
                        )
                    }
                }
            }

            // Announcement Speed (Slow / Normal / Fast)
            Text(
                text = "Announcement Speed",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KmVoiceSpeed.entries.forEach { spd ->
                    val selected = settings.voiceSpeed == spd
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateSettings { it.copy(voiceSpeed = spd) } },
                        label = {
                            Text(
                                text = spd.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.18f),
                            selectedLabelColor = accentColor
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("km_voice_speed_${spd.name.lowercase()}")
                    )
                }
            }

            // Announcement Volume Slider
            Text(
                text = "Announcement Volume (${settings.voiceVolumePercent}%)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = settings.voiceVolumePercent.toFloat(),
                onValueChange = { vol ->
                    onUpdateSettings { it.copy(voiceVolumePercent = vol.roundToInt().coerceIn(0, 100)) }
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("km_voice_volume_slider")
            )

            // Repeat Announcement ON/OFF
            KmFlatSwitchRow(
                title = "Repeat Announcement",
                subtitle = "Repeat each spoken KM Stone announcement once after a short pause",
                checked = settings.voiceRepeatEnabled,
                accentColor = accentColor,
                tag = "km_alert_switch_voice_repeat",
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(voiceRepeatEnabled = enabled) }
                }
            )

            // Test & Preview Controls (strictly isolated sample values)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val rec = KmStoneVoiceAnnouncementManager.previewAnnouncementStyle(
                            context = context,
                            settings = previewSettingsForText,
                            isArrival = false,
                            sampleIntervalMetres = 500
                        )
                        onFeedbackMessage("Voice test: ${rec.spokenText}")
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("km_voice_test_style_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Test Announcement",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Test Announcement",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        val rec = KmStoneVoiceAnnouncementManager.testArrivalVoiceAnnouncement(
                            context = context,
                            settings = previewSettingsForText
                        )
                        onFeedbackMessage("Arrival test: ${rec.spokenText}")
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("km_voice_test_arrival_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Test Arrival Voice",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Test Arrival",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
