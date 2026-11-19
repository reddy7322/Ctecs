package com.example.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChainageVoiceAssistantSettings
import com.example.model.ChainageVoiceLanguage
import com.example.model.KmVoiceSpeed
import com.example.navigation.ChainageVoiceAssistantManager
import com.example.navigation.KmStoneVoiceAnnouncementManager
import kotlin.math.roundToInt

/**
 * Settings -> Navigation -> Chainage Voice Assistant
 *
 * Configures the Navigate to Chainage Smart Voice Assistant, speech recognition language,
 * Android TTS voice output, speech speed & volume, voice confirmation before navigation,
 * optional auto-listen on popup open (OFF by default), microphone/TTS tests, and permission transparency.
 */
@Composable
fun ChainageVoiceAssistantSettingsSectionContent(
    settings: ChainageVoiceAssistantSettings,
    accentColor: Color,
    onUpdateSettings: ((ChainageVoiceAssistantSettings) -> ChainageVoiceAssistantSettings) -> Unit,
    onResetDefaults: () -> Unit,
    onFeedbackMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val availableVoices by KmStoneVoiceAnnouncementManager.availableVoices.collectAsStateWithLifecycle()
    val ttsAvailable by KmStoneVoiceAnnouncementManager.ttsAvailable.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(ChainageVoiceAssistantManager.hasRecordAudioPermission(context))
    }
    val isSpeechServiceAvailable = remember(context) {
        ChainageVoiceAssistantManager.isSpeechServiceAvailable(context)
    }

    var showLanguageMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var micTestStatus by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            micTestStatus = if (isSpeechServiceAvailable) {
                "Microphone & Android Speech Recognition are ready."
            } else {
                "Microphone granted, but Android Speech Recognition service is unavailable on this device."
            }
            onFeedbackMessage(micTestStatus!!)
        } else {
            micTestStatus = "Microphone permission denied. Voice search requires microphone access only while listening."
            onFeedbackMessage(micTestStatus!!)
        }
    }

    LaunchedEffect(Unit) {
        KmStoneVoiceAnnouncementManager.ensureInitialized(context)
    }

    val filteredVoices = remember(availableVoices, settings.voiceLanguage) {
        KmStoneVoiceAnnouncementManager.getVoicesForLanguage(settings.voiceLanguage.languageTag)
    }

    val selectedVoiceLabel = remember(availableVoices, settings.ttsVoiceName) {
        if (settings.ttsVoiceName.isBlank()) {
            "System Default Voice"
        } else {
            availableVoices.firstOrNull { it.name == settings.ttsVoiceName }?.displayLabel
                ?: settings.ttsVoiceName.substringAfterLast('#')
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("chainage_voice_assistant_settings_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Master Voice Assistant ON/OFF
        KmFlatSwitchRow(
            title = "Voice Assistant",
            subtitle = "Enable smart voice search and spoken confirmation inside Navigate to Chainage",
            checked = settings.voiceAssistantEnabled,
            accentColor = accentColor,
            tag = "cva_switch_enabled",
            onCheckedChange = { enabled ->
                onUpdateSettings { it.copy(voiceAssistantEnabled = enabled) }
            }
        )

        // Voice Search ON/OFF
        KmFlatSwitchRow(
            title = "Voice Search",
            subtitle = "Speak destination chainages naturally (e.g. \"201 plus 500\")",
            checked = settings.voiceSearchEnabled,
            accentColor = accentColor,
            tag = "cva_switch_voice_search",
            onCheckedChange = { enabled ->
                onUpdateSettings { it.copy(voiceSearchEnabled = enabled) }
            }
        )

        // Voice Language Selector
        Box(modifier = Modifier.fillMaxWidth()) {
            KmFlatDropdownRow(
                label = "Voice Language",
                valueText = settings.voiceLanguage.label,
                accentColor = accentColor,
                tag = "cva_dropdown_language",
                onClick = { showLanguageMenu = true }
            )
            DropdownMenu(
                expanded = showLanguageMenu,
                onDismissRequest = { showLanguageMenu = false }
            ) {
                ChainageVoiceLanguage.entries.forEach { lang ->
                    val isSelected = settings.voiceLanguage == lang
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = lang.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = lang.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            showLanguageMenu = false
                            onUpdateSettings { it.copy(voiceLanguage = lang, ttsVoiceName = "") }
                        },
                        modifier = Modifier.testTag("cva_lang_${lang.name.lowercase()}")
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // Voice Output ON/OFF
        KmFlatSwitchRow(
            title = "Voice Output",
            subtitle = "Speak destination distance and confirmation prompts using Android TTS",
            checked = settings.voiceOutputEnabled,
            accentColor = accentColor,
            tag = "cva_switch_voice_output",
            onCheckedChange = { enabled ->
                onUpdateSettings { it.copy(voiceOutputEnabled = enabled) }
            }
        )

        if (!ttsAvailable) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Android Text-to-Speech is unavailable on this device. Visual destination confirmation remains active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // TTS Voice Selector
        Box(modifier = Modifier.fillMaxWidth()) {
            KmFlatDropdownRow(
                label = "Assistant TTS Voice",
                valueText = selectedVoiceLabel,
                accentColor = accentColor,
                tag = "cva_dropdown_tts_voice",
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
                            fontWeight = if (settings.ttsVoiceName.isBlank()) FontWeight.Bold else FontWeight.Normal,
                            color = if (settings.ttsVoiceName.isBlank()) accentColor else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        showVoiceMenu = false
                        onUpdateSettings { it.copy(ttsVoiceName = "") }
                    },
                    modifier = Modifier.testTag("cva_tts_voice_default")
                )
                filteredVoices.take(25).forEach { voice ->
                    val isSel = settings.ttsVoiceName == voice.name
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
                            onUpdateSettings { it.copy(ttsVoiceName = voice.name) }
                        }
                    )
                }
            }
        }

        // Speech Speed (Slow / Normal / Fast)
        Text(
            text = "Speech Speed",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KmVoiceSpeed.entries.forEach { spd ->
                val selected = settings.speechSpeed == spd
                FilterChip(
                    selected = selected,
                    onClick = { onUpdateSettings { it.copy(speechSpeed = spd) } },
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
                        .testTag("cva_speed_${spd.name.lowercase()}")
                )
            }
        }

        // Announcement Volume Slider
        Text(
            text = "Announcement Volume (${settings.volumePercent}%)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = settings.volumePercent.toFloat(),
            onValueChange = { v ->
                onUpdateSettings { it.copy(volumePercent = v.roundToInt().coerceIn(0, 100)) }
            },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cva_volume_slider")
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // Voice Confirmation Before Navigation ON/OFF
        KmFlatSwitchRow(
            title = "Voice Confirmation Before Navigation",
            subtitle = "Confirm recognized destination and road-alignment distance before starting navigation",
            checked = settings.voiceConfirmationBeforeNav,
            accentColor = accentColor,
            tag = "cva_switch_confirm_before_nav",
            onCheckedChange = { enabled ->
                onUpdateSettings { it.copy(voiceConfirmationBeforeNav = enabled) }
            }
        )

        // Automatic Listening After Popup Opens (OFF by default)
        KmFlatSwitchRow(
            title = "Automatic Listening on Popup Open",
            subtitle = "Start listening immediately when Navigate to Chainage opens (OFF by default)",
            checked = settings.autoListenOnPopupOpen,
            accentColor = accentColor,
            tag = "cva_switch_auto_listen",
            onCheckedChange = { enabled ->
                onUpdateSettings { it.copy(autoListenOnPopupOpen = enabled) }
            }
        )

        // Test Microphone & Test Voice Output
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val grantedNow = ChainageVoiceAssistantManager.hasRecordAudioPermission(context)
                    hasMicPermission = grantedNow
                    if (!grantedNow) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else if (!isSpeechServiceAvailable) {
                        micTestStatus = "Microphone granted, but Android Speech Recognition service is not available on this device."
                        onFeedbackMessage(micTestStatus!!)
                    } else {
                        micTestStatus = "Microphone permission granted & Android Speech Recognition ready (${settings.voiceLanguage.label})."
                        onFeedbackMessage(micTestStatus!!)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("cva_test_microphone_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Test Microphone",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Test Microphone",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    val rec = ChainageVoiceAssistantManager.testVoiceOutput(context, settings)
                    onFeedbackMessage("Voice preview: ${rec.spokenText}")
                },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("cva_test_voice_output_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Test Voice Output",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Test Voice Output",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!micTestStatus.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.10f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cva_mic_test_status")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = micTestStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Permission & Privacy Explanation Card
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cva_permission_explanation_card")
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Microphone & Speech Recognition Privacy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Microphone permission is requested only when you tap Voice Search inside Navigate to Chainage. CTECS uses Android's built-in Speech Recognition service, never listens in the background, and stops recording immediately when the popup closes.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val statusSummary = buildString {
                        append(if (hasMicPermission) "Microphone: Granted" else "Microphone: Not granted")
                        append(" • ")
                        append(if (isSpeechServiceAvailable) "Speech Service: Available" else "Speech Service: Unavailable")
                    }
                    Text(
                        text = statusSummary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
            }
        }

        // Reset Defaults
        OutlinedButton(
            onClick = { showResetDialog = true },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cva_reset_defaults_btn")
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reset Voice Assistant Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Chainage Voice Assistant?") },
            text = {
                Text("This will restore default language, TTS voice, speed, volume, confirmation, and auto-listen preferences.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetDefaults()
                    },
                    modifier = Modifier.testTag("cva_confirm_reset_btn")
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
