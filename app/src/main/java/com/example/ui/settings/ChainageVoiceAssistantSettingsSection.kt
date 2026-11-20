package com.example.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.model.ChainageVoiceAssistantSettings
import com.example.model.ChainageVoiceLanguage
import com.example.model.KmVoiceSpeed
import com.example.navigation.ChainageVoiceAssistantManager
import com.example.navigation.SmartTtsManager
import com.example.navigation.TtsAvailabilityStatus
import com.example.navigation.TtsEngineReadiness
import com.example.navigation.TtsLanguageAvailability
import com.example.navigation.TtsStatusSnapshot
import com.example.navigation.TtsTestResult
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Settings -> Navigation -> Chainage Voice Assistant
 *
 * Configures Navigate to Chainage Smart Voice Assistant, speech recognition language,
 * automatic Android TTS engine detection & manual engine selection, dynamic language & voice
 * availability with voice preview, speech speed, pitch, volume, voice confirmation before
 * navigation, single compact setup/fallback guidance with non-truncating buttons, separated
 * Microphone / Speech Recognition / TTS Engine / Language statuses, and real async Test Voice Output.
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val ttsStatus by SmartTtsManager.status.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(ChainageVoiceAssistantManager.hasRecordAudioPermission(context))
    }
    val isSpeechServiceAvailable = remember(context) {
        ChainageVoiceAssistantManager.isSpeechServiceAvailable(context)
    }

    var showEngineMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var micTestStatus by remember { mutableStateOf<String?>(null) }
    var voiceTestResult by remember { mutableStateOf<TtsTestResult?>(null) }
    var isTestingVoice by remember { mutableStateOf(false) }
    var isPreviewingVoice by remember { mutableStateOf(false) }
    var inlineSetupNote by remember { mutableStateOf<String?>(null) }
    var hadPausedOrStopped by remember { mutableStateOf(false) }

    val effectiveLangTag = settings.effectiveLanguageTag()

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

    // Smart Voice Output state management:
    // - ON: verify & initialize working TTS engine automatically (without cancelling in-flight binds).
    // - OFF: stop active speech and do NOT unnecessarily initialize TTS.
    LaunchedEffect(settings.voiceOutputEnabled, settings.ttsEnginePackage, effectiveLangTag) {
        if (settings.voiceOutputEnabled) {
            SmartTtsManager.initializeIfNeeded(
                context = context,
                preferredEngine = settings.ttsEnginePackage,
                languageTag = effectiveLangTag,
                forceReinitialize = false
            )
        } else {
            SmartTtsManager.stopSpeaking()
        }
    }

    // Clear stale test failure or manual setup notes immediately once TTS recovers to READY.
    LaunchedEffect(ttsStatus.status, ttsStatus.activeEnginePackage, ttsStatus.languageCheck?.availability) {
        if (ttsStatus.isReady) {
            inlineSetupNote = null
            if (voiceTestResult?.success == false) {
                voiceTestResult = null
            }
        }
    }

    // Automatically re-detect TTS engine & installed voice data ONLY after actually returning
    // from background / Android TTS Settings (ON_PAUSE/ON_STOP -> ON_RESUME).
    // Never fire on initial observer attach so an in-flight Samsung SMT bind is NEVER cancelled!
    DisposableEffect(lifecycleOwner, settings.voiceOutputEnabled, settings.ttsEnginePackage, effectiveLangTag) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    hadPausedOrStopped = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (hadPausedOrStopped) {
                        hadPausedOrStopped = false
                        hasMicPermission = ChainageVoiceAssistantManager.hasRecordAudioPermission(context)
                        if (settings.voiceOutputEnabled) {
                            SmartTtsManager.revalidateOnResume(
                                context = context,
                                voiceOutputEnabled = true,
                                preferredEngine = settings.ttsEnginePackage,
                                languageTag = effectiveLangTag,
                                forceReinitialize = !SmartTtsManager.status.value.isReady
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredVoices = remember(ttsStatus.availableVoices, effectiveLangTag) {
        SmartTtsManager.getVoicesForLanguage(effectiveLangTag)
    }

    val selectedEngineLabel = remember(
        ttsStatus.installedEngines,
        ttsStatus.disabledEngines,
        ttsStatus.activeEngineLabel,
        ttsStatus.isFallbackActive,
        settings.ttsEnginePackage
    ) {
        if (settings.ttsEnginePackage.isBlank()) {
            if (ttsStatus.activeEngineLabel.isNotBlank()) {
                "Auto (${ttsStatus.activeEngineLabel})"
            } else {
                "Auto (System Default)"
            }
        } else {
            val matched = ttsStatus.installedEngines.firstOrNull { it.packageName == settings.ttsEnginePackage }
                ?: ttsStatus.disabledEngines.firstOrNull { it.packageName == settings.ttsEnginePackage }
            val baseLabel = matched?.label ?: settings.ttsEnginePackage.substringAfterLast('.')
            if (ttsStatus.isFallbackActive && ttsStatus.activeEngineLabel.isNotBlank()) {
                "$baseLabel (using ${ttsStatus.activeEngineLabel})"
            } else {
                baseLabel
            }
        }
    }

    val selectedLanguageLabel = remember(settings.voiceLanguage, settings.customLanguageTag, ttsStatus.availableLanguages) {
        if (settings.customLanguageTag.isNotBlank()) {
            ttsStatus.availableLanguages.firstOrNull {
                it.languageTag.equals(settings.customLanguageTag, ignoreCase = true)
            }?.displayLabel ?: Locale.forLanguageTag(settings.customLanguageTag).getDisplayName(Locale.getDefault())
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                .ifBlank { settings.customLanguageTag }
        } else {
            settings.voiceLanguage.label
        }
    }

    val extraInstalledLanguages = remember(ttsStatus.availableLanguages) {
        val presetTags = ChainageVoiceLanguage.entries
            .map { it.languageTag.lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()
        ttsStatus.availableLanguages.filterNot {
            it.languageTag.lowercase(Locale.ROOT) in presetTags
        }
    }

    val selectedVoiceLabel = remember(ttsStatus.availableVoices, settings.ttsVoiceName) {
        if (settings.ttsVoiceName.isBlank()) {
            "System Default Voice"
        } else {
            ttsStatus.availableVoices.firstOrNull { it.name == settings.ttsVoiceName }?.displayLabel
                ?: settings.ttsVoiceName.substringAfterLast('#')
        }
    }

    val voiceOutputSubtitle = remember(settings.voiceOutputEnabled, ttsStatus) {
        when {
            !settings.voiceOutputEnabled ->
                "Voice Output is OFF • Visual destination confirmation remains active"
            ttsStatus.isReady && ttsStatus.isFallbackActive ->
                "Active via fallback (${ttsStatus.activeEngineLabel}) • Preference preserved"
            ttsStatus.isReady ->
                "Ready • ${ttsStatus.activeEngineLabel.ifBlank { "Android TTS" }}"
            ttsStatus.status == TtsAvailabilityStatus.INITIALIZING ||
                ttsStatus.status == TtsAvailabilityStatus.NOT_INITIALIZED ->
                "Initializing Android Text-to-Speech engine…"
            else ->
                "Engine setup required • Visual destination confirmation remains active"
        }
    }

    val selectedLangAvailability = ttsStatus.languageCheck?.availability
    val isSelectedLangMissingOrUnsupported = settings.voiceOutputEnabled &&
        (selectedLangAvailability == TtsLanguageAvailability.MISSING_DATA ||
            selectedLangAvailability == TtsLanguageAvailability.NOT_SUPPORTED)

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
                if (!enabled) {
                    SmartTtsManager.stopSpeaking()
                }
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

        // Voice Language Selector (Presets + all dynamically installed TTS engine languages)
        Box(modifier = Modifier.fillMaxWidth()) {
            KmFlatDropdownRow(
                label = "Voice Language",
                valueText = selectedLanguageLabel,
                accentColor = accentColor,
                tag = "cva_dropdown_language",
                onClick = { showLanguageMenu = true }
            )
            DropdownMenu(
                expanded = showLanguageMenu,
                onDismissRequest = { showLanguageMenu = false }
            ) {
                ChainageVoiceLanguage.entries.forEach { lang ->
                    val isSelected = settings.customLanguageTag.isBlank() && settings.voiceLanguage == lang
                    val installedMatch = lang.languageTag.isBlank() ||
                        ttsStatus.availableLanguages.any {
                            it.languageTag.equals(lang.languageTag, ignoreCase = true) ||
                                it.languageTag.substringBefore('-').equals(
                                    lang.languageTag.substringBefore('-'),
                                    ignoreCase = true
                                )
                        }
                    val subtitleText = if (ttsStatus.isReady && lang.languageTag.isNotBlank()) {
                        if (installedMatch) "${lang.subtitle} • Installed" else "${lang.subtitle} • Voice data required"
                    } else {
                        lang.subtitle
                    }
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = lang.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            showLanguageMenu = false
                            voiceTestResult = null
                            inlineSetupNote = null
                            onUpdateSettings {
                                it.copy(
                                    voiceLanguage = lang,
                                    customLanguageTag = "",
                                    ttsVoiceName = ""
                                )
                            }
                        },
                        modifier = Modifier.testTag("cva_lang_${lang.name.lowercase()}")
                    )
                }

                if (extraInstalledLanguages.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    extraInstalledLanguages.take(25).forEach { dynLang ->
                        val isSel = settings.customLanguageTag.equals(dynLang.languageTag, ignoreCase = true)
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = dynLang.displayLabel,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${dynLang.languageTag} • Installed on engine",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                showLanguageMenu = false
                                voiceTestResult = null
                                inlineSetupNote = null
                                onUpdateSettings {
                                    it.copy(
                                        voiceLanguage = ChainageVoiceLanguage.SYSTEM,
                                        customLanguageTag = dynLang.languageTag,
                                        ttsVoiceName = ""
                                    )
                                }
                            },
                            modifier = Modifier.testTag("cva_lang_dyn_${dynLang.languageTag.lowercase()}")
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

        // Voice Output ON/OFF with truthful readiness status in subtitle
        KmFlatSwitchRow(
            title = "Voice Output",
            subtitle = voiceOutputSubtitle,
            checked = settings.voiceOutputEnabled,
            accentColor = accentColor,
            tag = "cva_switch_voice_output",
            onCheckedChange = { enabled ->
                voiceTestResult = null
                inlineSetupNote = null
                if (!enabled) {
                    SmartTtsManager.stopSpeaking()
                } else {
                    SmartTtsManager.initializeIfNeeded(
                        context = context,
                        preferredEngine = settings.ttsEnginePackage,
                        languageTag = effectiveLangTag,
                        forceReinitialize = true
                    )
                }
                onUpdateSettings { it.copy(voiceOutputEnabled = enabled) }
            }
        )

        // STRICTLY ONE compact TTS status / fallback / setup panel
        TtsEngineStatusAndSetupCard(
            voiceOutputEnabled = settings.voiceOutputEnabled,
            ttsStatus = ttsStatus,
            accentColor = accentColor,
            contextSuffix = "Visual destination confirmation remains active.",
            inlineSetupNote = inlineSetupNote,
            onInstallOrConfigure = { forMissingData ->
                val msg = SmartTtsManager.launchSetupFlow(context, forMissingVoiceData = forMissingData)
                inlineSetupNote = msg
                onFeedbackMessage(msg)
            },
            onCheckAgain = {
                inlineSetupNote = null
                voiceTestResult = null
                SmartTtsManager.revalidateOnResume(
                    context = context,
                    voiceOutputEnabled = true,
                    preferredEngine = settings.ttsEnginePackage,
                    languageTag = effectiveLangTag,
                    forceReinitialize = true
                )
                onFeedbackMessage("Checking installed Android Text-to-Speech engines…")
            },
            onKeepFallbackEngine = if (ttsStatus.isFallbackActive && ttsStatus.activeEnginePackage.isNotBlank()) {
                {
                    inlineSetupNote = null
                    voiceTestResult = null
                    val fallbackPkg = ttsStatus.activeEnginePackage
                    onUpdateSettings { it.copy(ttsEnginePackage = fallbackPkg, ttsVoiceName = "") }
                    SmartTtsManager.selectEngine(context, fallbackPkg, effectiveLangTag)
                    onFeedbackMessage("Saved ${ttsStatus.activeEngineLabel} as your preferred voice engine.")
                }
            } else {
                null
            }
        )

        // TTS Engine Selector (Auto + detected installed engines with truthful readiness badges)
        Box(modifier = Modifier.fillMaxWidth()) {
            KmFlatDropdownRow(
                label = "Text-to-Speech Engine",
                valueText = selectedEngineLabel,
                accentColor = accentColor,
                tag = "cva_dropdown_tts_engine",
                onClick = { showEngineMenu = true }
            )
            DropdownMenu(
                expanded = showEngineMenu,
                onDismissRequest = { showEngineMenu = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "Auto (System Default)",
                                    fontWeight = if (settings.ttsEnginePackage.isBlank()) FontWeight.Bold else FontWeight.Normal,
                                    color = if (settings.ttsEnginePackage.isBlank()) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Automatically picks default or working installed engine",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (settings.ttsEnginePackage.isBlank()) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        showEngineMenu = false
                        voiceTestResult = null
                        inlineSetupNote = null
                        onUpdateSettings { it.copy(ttsEnginePackage = "", ttsVoiceName = "") }
                        if (settings.voiceOutputEnabled) {
                            SmartTtsManager.selectEngine(context, "", effectiveLangTag)
                        }
                    },
                    modifier = Modifier.testTag("cva_tts_engine_auto")
                )

                ttsStatus.installedEngines.forEach { engine ->
                    val isSel = settings.ttsEnginePackage == engine.packageName
                    val readinessColor = when (engine.readiness) {
                        TtsEngineReadiness.READY -> accentColor
                        TtsEngineReadiness.FAILED, TtsEngineReadiness.DISABLED -> MaterialTheme.colorScheme.error
                        TtsEngineReadiness.INITIALIZING, TtsEngineReadiness.UNTESTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = engine.label + if (engine.isSystemDefault) " (Default)" else "",
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${engine.readiness.badgeLabel} • ${engine.packageName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = readinessColor
                                    )
                                }
                                if (isSel) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            showEngineMenu = false
                            voiceTestResult = null
                            inlineSetupNote = null
                            onUpdateSettings { it.copy(ttsEnginePackage = engine.packageName, ttsVoiceName = "") }
                            if (settings.voiceOutputEnabled) {
                                SmartTtsManager.selectEngine(context, engine.packageName, effectiveLangTag)
                            }
                        },
                        modifier = Modifier.testTag("cva_tts_engine_${engine.packageName}")
                    )
                }

                if (ttsStatus.disabledEngines.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ttsStatus.disabledEngines.forEach { disabled ->
                        DropdownMenuItem(
                            enabled = false,
                            text = {
                                Column {
                                    Text(
                                        text = disabled.label,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                    Text(
                                        text = "Disabled in Android Settings • ${disabled.packageName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            },
                            onClick = {},
                            modifier = Modifier.testTag("cva_tts_engine_disabled_${disabled.packageName}")
                        )
                    }
                }
            }
        }

        // TTS Voice Selector + Voice Preview Action
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
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
                        if (filteredVoices.isEmpty() && isSelectedLangMissingOrUnsupported) {
                            DropdownMenuItem(
                                enabled = false,
                                text = {
                                    Text(
                                        text = "No installed voices for $selectedLanguageLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {}
                            )
                        } else {
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
                }

                OutlinedButton(
                    onClick = {
                        if (!settings.voiceOutputEnabled) {
                            onFeedbackMessage("Turn ON Voice Output to preview voices.")
                        } else if (!isPreviewingVoice) {
                            isPreviewingVoice = true
                            coroutineScope.launch {
                                try {
                                    val res = ChainageVoiceAssistantManager.previewVoiceAsync(context, settings)
                                    if (res.success) {
                                        onFeedbackMessage("Voice preview: ${res.spokenText}")
                                    } else {
                                        onFeedbackMessage("${res.headline}: ${res.message}")
                                    }
                                } finally {
                                    isPreviewingVoice = false
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cva_preview_voice_btn")
                ) {
                    if (isPreviewingVoice) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = accentColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Preview Voice",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Preview",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (isSelectedLangMissingOrUnsupported) {
                Text(
                    text = if (selectedLangAvailability == TtsLanguageAvailability.MISSING_DATA) {
                        "Voice data is missing for $selectedLanguageLabel. Install voice data in TTS settings to see installed voices."
                    } else {
                        "$selectedLanguageLabel is not supported by the active TTS engine."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .testTag("cva_voice_missing_data_hint")
                )
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
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.18f),
                        selectedLabelColor = accentColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("cva_speed_${spd.name.lowercase()}")
                )
            }
        }

        // Speech Pitch Slider (0.6x to 1.5x)
        val formattedPitch = String.format(Locale.US, "%.2fx", settings.effectiveSpeechPitch())
        Text(
            text = "Speech Pitch ($formattedPitch)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = settings.effectiveSpeechPitch(),
            onValueChange = { p ->
                val snapped = ((p * 20f).roundToInt() / 20f).coerceIn(0.6f, 1.5f)
                onUpdateSettings { it.copy(speechPitch = snapped) }
            },
            valueRange = 0.6f..1.5f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cva_pitch_slider")
        )

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

        // Test Microphone & Test Voice Output (responsive non-truncating buttons)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackTestButtons = maxWidth < 310.dp
            val testMicButton: @Composable (Modifier) -> Unit = { btnMod ->
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
                            micTestStatus = "Microphone permission granted & Android Speech Recognition ready ($selectedLanguageLabel)."
                            onFeedbackMessage(micTestStatus!!)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = btnMod
                        .heightIn(min = 48.dp)
                        .testTag("cva_test_microphone_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Test Microphone",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Test Microphone",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            val testVoiceButton: @Composable (Modifier) -> Unit = { btnMod ->
                OutlinedButton(
                    onClick = {
                        if (!settings.voiceOutputEnabled) {
                            val disabledMsg = "Voice Output is currently OFF. Turn ON Voice Output to test speech."
                            voiceTestResult = TtsTestResult(
                                success = false,
                                spokenText = SmartTtsManager.TEST_VOICE_SAMPLE_TEXT,
                                headline = "Voice Output is OFF",
                                message = disabledMsg,
                                status = TtsAvailabilityStatus.NOT_INITIALIZED
                            )
                            onFeedbackMessage("$disabledMsg (${SmartTtsManager.TEST_VOICE_SAMPLE_TEXT})")
                        } else if (!isTestingVoice) {
                            isTestingVoice = true
                            coroutineScope.launch {
                                try {
                                    val res = ChainageVoiceAssistantManager.testVoiceOutputAsync(context, settings)
                                    voiceTestResult = res
                                    if (res.success) {
                                        onFeedbackMessage("${res.headline}: ${res.spokenText}")
                                    } else {
                                        onFeedbackMessage("${res.headline} (${res.spokenText}): ${res.message}")
                                    }
                                } finally {
                                    isTestingVoice = false
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = btnMod
                        .heightIn(min = 48.dp)
                        .testTag("cva_test_voice_output_btn")
                ) {
                    if (isTestingVoice) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = accentColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Test Voice Output",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isTestingVoice) "Testing…" else "Test Voice Output",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (stackTestButtons) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    testMicButton(Modifier.fillMaxWidth())
                    testVoiceButton(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    testMicButton(Modifier.weight(1f))
                    testVoiceButton(Modifier.weight(1f))
                }
            }
        }

        // Inline Test Voice Output result — strictly suppresses duplicate engine/language error cards
        // when the single primary tts_status_card_setup warning panel is already displayed above.
        val showVoiceTestCard = voiceTestResult?.let { res ->
            res.success ||
                !settings.voiceOutputEnabled ||
                res.status == TtsAvailabilityStatus.AUDIO_OUTPUT_UNAVAILABLE ||
                !ttsStatus.status.needsSetupAction
        } == true

        if (showVoiceTestCard) {
            voiceTestResult?.let { testRes ->
                val containerColor = if (testRes.success) {
                    accentColor.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                }
                val iconTint = if (testRes.success) accentColor else MaterialTheme.colorScheme.tertiary
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = containerColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cva_voice_test_status")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (testRes.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = if (testRes.success) "Voice test successful" else testRes.headline,
                            tint = iconTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = testRes.headline,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = testRes.message,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (testRes.success) {
                                Text(
                                    text = "\"${testRes.spokenText}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }
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

        // Separated Independent Status & Privacy Card:
        // Clearly separates Microphone Permission, Speech Recognition, TTS Engine, and Selected TTS Language.
        val ttsEngineStatusText = when {
            !settings.voiceOutputEnabled -> "Off"
            ttsStatus.isReady && ttsStatus.isFallbackActive ->
                "Ready (Fallback: ${ttsStatus.activeEngineLabel})"
            ttsStatus.isReady ->
                "Ready (${ttsStatus.activeEngineLabel.ifBlank { "System Default" }})"
            ttsStatus.status == TtsAvailabilityStatus.INITIALIZING ||
                ttsStatus.status == TtsAvailabilityStatus.NOT_INITIALIZED -> "Initializing…"
            else -> "Unavailable"
        }
        val ttsEngineOk = !settings.voiceOutputEnabled || ttsStatus.isReady

        val ttsLangStatusText = when {
            !settings.voiceOutputEnabled -> "Inactive (Voice Output Off)"
            ttsStatus.status == TtsAvailabilityStatus.INITIALIZING ||
                ttsStatus.status == TtsAvailabilityStatus.NOT_INITIALIZED -> "Checking…"
            !ttsStatus.isReady && ttsStatus.status != TtsAvailabilityStatus.LANGUAGE_MISSING_DATA &&
                ttsStatus.status != TtsAvailabilityStatus.LANGUAGE_NOT_SUPPORTED -> "Unavailable (Engine not ready)"
            selectedLangAvailability == TtsLanguageAvailability.AVAILABLE -> "Available ($selectedLanguageLabel)"
            selectedLangAvailability == TtsLanguageAvailability.MISSING_DATA -> "Missing Voice Data ($selectedLanguageLabel)"
            selectedLangAvailability == TtsLanguageAvailability.NOT_SUPPORTED -> "Not Supported ($selectedLanguageLabel)"
            else -> "Pending"
        }
        val ttsLangOk = !settings.voiceOutputEnabled || selectedLangAvailability == TtsLanguageAvailability.AVAILABLE

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cva_permission_explanation_card")
        ) {
            Column(
                modifier = Modifier.padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Voice system status",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Voice System Status & Privacy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                VoiceSystemStatusRow(
                    label = "Microphone Permission",
                    value = if (hasMicPermission) "Granted" else "Denied",
                    positive = hasMicPermission,
                    accentColor = accentColor,
                    tag = "cva_status_mic"
                )
                VoiceSystemStatusRow(
                    label = "Speech Recognition",
                    value = if (isSpeechServiceAvailable) "Available" else "Unavailable",
                    positive = isSpeechServiceAvailable,
                    accentColor = accentColor,
                    tag = "cva_status_speech_rec"
                )
                VoiceSystemStatusRow(
                    label = "Text-to-Speech Engine",
                    value = ttsEngineStatusText,
                    positive = ttsEngineOk,
                    accentColor = accentColor,
                    tag = "cva_status_tts_engine"
                )
                VoiceSystemStatusRow(
                    label = "Selected TTS Language",
                    value = ttsLangStatusText,
                    positive = ttsLangOk,
                    accentColor = accentColor,
                    tag = "cva_status_tts_language"
                )

                Text(
                    text = "Microphone access is used only while Voice Search is active inside Navigate to Chainage. CTECS never listens in the background and releases speech resources when closed.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                .heightIn(min = 48.dp)
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
                Text("This will restore default TTS engine, language, voice, speed, pitch, volume, confirmation, and auto-listen preferences.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        voiceTestResult = null
                        inlineSetupNote = null
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

@Composable
private fun VoiceSystemStatusRow(
    label: String,
    value: String,
    positive: Boolean,
    accentColor: Color,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (positive) accentColor else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Single compact Android TTS status, fallback, and setup card.
 *
 * Guarantees:
 * - Strictly ONE configuration warning panel when Voice Output needs attention.
 * - Responsive button layout (`Configure Engine` / `Install Voice Data` + `Check Again`) so
 *   labels NEVER truncate with ellipsis on compact portrait or landscape screens.
 * - Explicit fallback controls when a fallback engine is active, never silently overwriting
 *   the user's saved engine preference.
 */
@Composable
internal fun TtsEngineStatusAndSetupCard(
    voiceOutputEnabled: Boolean,
    ttsStatus: TtsStatusSnapshot,
    accentColor: Color,
    contextSuffix: String,
    inlineSetupNote: String? = null,
    onInstallOrConfigure: (forMissingVoiceData: Boolean) -> Unit,
    onCheckAgain: () -> Unit,
    onKeepFallbackEngine: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!voiceOutputEnabled) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = modifier
                .fillMaxWidth()
                .testTag("tts_status_card_off")
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Voice output disabled",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Voice Output is OFF. $contextSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    when (ttsStatus.status) {
        TtsAvailabilityStatus.READY -> {
            if (ttsStatus.isFallbackActive) {
                // Transparent fallback notice: user's saved preference is untouched; user can keep fallback or retry/configure
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.50f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.40f)),
                    modifier = modifier
                        .fillMaxWidth()
                        .testTag("tts_status_card_fallback")
                ) {
                    Column(
                        modifier = Modifier.padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Fallback TTS engine active",
                                tint = accentColor,
                                modifier = Modifier.size(17.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = ttsStatus.statusHeadline,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = ttsStatus.statusDetail,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                                )
                            }
                        }

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val stack = maxWidth < 310.dp
                            val keepBtn: @Composable (Modifier) -> Unit = { mod ->
                                if (onKeepFallbackEngine != null) {
                                    Button(
                                        onClick = onKeepFallbackEngine,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = mod
                                            .heightIn(min = 48.dp)
                                            .testTag("cva_keep_fallback_engine_btn")
                                    ) {
                                        Text(
                                            text = "Keep Fallback",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                            val retryBtn: @Composable (Modifier) -> Unit = { mod ->
                                OutlinedButton(
                                    onClick = onCheckAgain,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = mod
                                        .heightIn(min = 48.dp)
                                        .testTag("cva_check_tts_again_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Check Again",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Check Again",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            if (stack) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    keepBtn(Modifier.fillMaxWidth())
                                    retryBtn(Modifier.fillMaxWidth())
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (onKeepFallbackEngine != null) {
                                        keepBtn(Modifier.weight(1f))
                                    }
                                    retryBtn(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.11f),
                    modifier = modifier
                        .fillMaxWidth()
                        .testTag("tts_status_card_ready")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "TTS engine ready",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ttsStatus.statusHeadline,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = ttsStatus.statusDetail,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        TtsAvailabilityStatus.INITIALIZING,
        TtsAvailabilityStatus.NOT_INITIALIZED -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("tts_status_card_initializing")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                    Text(
                        text = "Detecting and initializing Android Text-to-Speech engine…",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        else -> {
            val isMissingLanguageData = ttsStatus.status == TtsAvailabilityStatus.LANGUAGE_MISSING_DATA ||
                ttsStatus.status == TtsAvailabilityStatus.LANGUAGE_NOT_SUPPORTED
            val primaryActionLabel = if (isMissingLanguageData) {
                "Install Voice Data"
            } else {
                "Configure Engine"
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("tts_status_card_setup")
            ) {
                Column(
                    modifier = Modifier.padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Voice engine setup required",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = ttsStatus.statusHeadline,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = ttsStatus.statusDetail.ifBlank { contextSuffix },
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                            )
                            if (!inlineSetupNote.isNullOrBlank()) {
                                Text(
                                    text = inlineSetupNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // Responsive non-truncating action layout for Portrait & Landscape
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val stackVertically = maxWidth < 310.dp

                        val primaryButton: @Composable (Modifier) -> Unit = { btnMod ->
                            Button(
                                onClick = { onInstallOrConfigure(isMissingLanguageData) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = btnMod
                                    .heightIn(min = 48.dp)
                                    .testTag(
                                        if (isMissingLanguageData) "cva_install_voice_data_btn"
                                        else "cva_install_configure_tts_btn"
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isMissingLanguageData) Icons.Default.Download else Icons.Default.Settings,
                                    contentDescription = primaryActionLabel,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = primaryActionLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        val checkAgainButton: @Composable (Modifier) -> Unit = { btnMod ->
                            OutlinedButton(
                                onClick = onCheckAgain,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = btnMod
                                    .heightIn(min = 48.dp)
                                    .testTag("cva_check_tts_again_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Check Again",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Check Again",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (stackVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                primaryButton(Modifier.fillMaxWidth())
                                checkAgainButton(Modifier.fillMaxWidth())
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                primaryButton(Modifier.weight(1.1f))
                                checkAgainButton(Modifier.weight(0.9f))
                            }
                        }
                    }
                }
            }
        }
    }
}
