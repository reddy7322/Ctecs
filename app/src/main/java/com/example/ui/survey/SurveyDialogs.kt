package com.example.ui.survey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.Chainage
import com.example.model.GeoPoint
import com.example.survey.engine.RelocationState
import com.example.survey.model.CheckpointPrompt
import com.example.survey.model.ConfirmationIntervalOption
import com.example.survey.model.KmMarkerType
import com.example.survey.model.OffsetSide
import com.example.survey.model.RoadSide
import com.example.survey.model.SurveyDirection
import com.example.survey.model.SurveyLane
import com.example.survey.model.SurveyMarker
import com.example.survey.model.SurveySession
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NewSurveyConfigDialog(
    initialProjectName: String = "",
    initialHighwayName: String = "",
    currentGpsLocation: GeoPoint?,
    onDismiss: () -> Unit,
    onStartSurvey: (SurveySession) -> Unit
) {
    val colors = rememberSurveyThemeColors()

    var highwayName by remember { mutableStateOf(initialHighwayName.ifBlank { "NH-44" }) }
    var surveyName by remember { mutableStateOf("Survey ${SimpleDateFormat("dd-MMM HH:mm", Locale.US).format(Date())}") }
    var projectName by remember { mutableStateOf(initialProjectName) }
    var roadNumber by remember { mutableStateOf("") }
    var stretchName by remember { mutableStateOf("") }

    // Road Side & Lane
    var selectedRoadSide by remember { mutableStateOf(RoadSide.RHS) }
    var selectedLane by remember { mutableStateOf(SurveyLane.FAST) }
    var customLaneName by remember { mutableStateOf("") }
    var isLaneDropdownOpen by remember { mutableStateOf(false) }

    // Starting & Ending Chainage
    var startChainageInput by remember { mutableStateOf("190+000") }
    var endingChainageInput by remember { mutableStateOf("") }
    var selectedDirection by remember { mutableStateOf(SurveyDirection.INCREASING) }

    // Chainage Confirmation Interval (Default Every 2 KM)
    var selectedIntervalOption by remember { mutableStateOf(ConfirmationIntervalOption.EVERY_2_KM) }
    var customIntervalInput by remember { mutableStateOf("2000") }

    // Expandable Advanced Details
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var manualOffsetInput by remember { mutableStateOf(String.format(Locale.US, "%.2f", selectedLane.defaultOffsetMetres)) }

    val parsedStartChainage = Chainage.parse(startChainageInput)
    val isStartChainageValid = parsedStartChainage != null

    val parsedEndChainage = if (endingChainageInput.isNotBlank()) Chainage.parse(endingChainageInput) else null
    val isEndChainageValid = endingChainageInput.isBlank() || parsedEndChainage != null

    // Direction & Planned Distance Validation
    val isDirectionCompatible = when {
        parsedStartChainage == null || parsedEndChainage == null -> true
        selectedDirection == SurveyDirection.INCREASING -> parsedEndChainage.metres > parsedStartChainage.metres
        selectedDirection == SurveyDirection.DECREASING -> parsedEndChainage.metres < parsedStartChainage.metres
        else -> true
    }

    val plannedDistanceM = if (parsedStartChainage != null && parsedEndChainage != null) {
        kotlin.math.abs(parsedEndChainage.metres - parsedStartChainage.metres)
    } else null

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.inputFocusedBorder,
        unfocusedBorderColor = colors.inputBorder,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedLabelColor = colors.inputFocusedBorder,
        unfocusedLabelColor = colors.textSecondary,
        focusedContainerColor = colors.inputBackground,
        unfocusedContainerColor = colors.inputBackground
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.95f)
                .padding(vertical = 8.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AltRoute,
                            contentDescription = null,
                            tint = colors.gold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START HIGHWAY SURVEY",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.gold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Highway & Survey Name
                    item {
                        Text("1. HIGHWAY & SURVEY NAME", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = highwayName,
                                onValueChange = { highwayName = it },
                                label = { Text("Highway (e.g. NH-44)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                colors = textFieldColors
                            )
                            OutlinedTextField(
                                value = surveyName,
                                onValueChange = { surveyName = it },
                                label = { Text("Survey Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                colors = textFieldColors
                            )
                        }
                    }

                    // 2. Road Side & Lane Selection (Two independent simple controls)
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("2. ROAD SIDE & LANE", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)

                        // Road Side Selector: Auto, LHS, RHS, BHS
                        Text("Road Side", fontSize = 11.sp, color = colors.textSecondary)
                        SurveyGlassSegmentedToggle(
                            options = listOf(
                                RoadSide.AUTO to "Auto",
                                RoadSide.LHS to "LHS",
                                RoadSide.RHS to "RHS",
                                RoadSide.BHS to "BHS"
                            ),
                            selected = selectedRoadSide,
                            onSelect = { selectedRoadSide = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Lane Selector Dropdown: Auto, Inner, Outer, Fast, Slow, Service Road, Other / Custom
                        Text("Lane", fontSize = 11.sp, color = colors.textSecondary)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (colors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.inputBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isLaneDropdownOpen = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(selectedLane.label, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Offset: ${selectedLane.defaultOffsetMetres}m • Side: ${selectedRoadSide.label}", color = colors.textSecondary, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.gold)
                                }
                            }
                            DropdownMenu(
                                expanded = isLaneDropdownOpen,
                                onDismissRequest = { isLaneDropdownOpen = false },
                                modifier = Modifier.background(if (colors.isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF))
                            ) {
                                listOf(
                                    SurveyLane.AUTO,
                                    SurveyLane.INNER,
                                    SurveyLane.OUTER,
                                    SurveyLane.FAST,
                                    SurveyLane.SLOW,
                                    SurveyLane.SERVICE_ROAD,
                                    SurveyLane.CUSTOM
                                ).forEach { lane ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(lane.label, color = colors.textPrimary, fontSize = 12.sp)
                                                if (lane == selectedLane) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = colors.gold, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedLane = lane
                                            manualOffsetInput = String.format(Locale.US, "%.2f", lane.defaultOffsetMetres)
                                            isLaneDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedLane == SurveyLane.CUSTOM) {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = customLaneName,
                                onValueChange = { customLaneName = it },
                                label = { Text("Custom Lane Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                colors = textFieldColors
                            )
                        }
                    }

                    // 3. Starting Chainage & Ending Chainage
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("3. STARTING & ENDING CHAINAGE", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Starting Chainage (Mandatory)
                            OutlinedTextField(
                                value = startChainageInput,
                                onValueChange = { startChainageInput = it },
                                label = { Text("Start Chainage *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (isStartChainageValid) colors.gold else colors.red,
                                    fontWeight = FontWeight.Bold
                                ),
                                supportingText = {
                                    Text(
                                        text = if (isStartChainageValid) parsedStartChainage!!.format() else "Required (e.g. 190+000)",
                                        color = if (isStartChainageValid) colors.green else colors.red,
                                        fontSize = 10.sp
                                    )
                                },
                                colors = textFieldColors
                            )

                            // Ending Chainage (Optional)
                            OutlinedTextField(
                                value = endingChainageInput,
                                onValueChange = { endingChainageInput = it },
                                label = { Text("End Chainage (opt)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (isEndChainageValid) colors.gold else colors.red,
                                    fontWeight = FontWeight.Bold
                                ),
                                supportingText = {
                                    Text(
                                        text = if (parsedEndChainage != null) parsedEndChainage.format() else "Optional",
                                        color = if (isEndChainageValid) colors.textSecondary else colors.red,
                                        fontSize = 10.sp
                                    )
                                },
                                colors = textFieldColors
                            )
                        }

                        if (plannedDistanceM != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDirectionCompatible) colors.surfaceHighlight else colors.red.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isDirectionCompatible) colors.gold.copy(alpha = 0.4f) else colors.red),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isDirectionCompatible) {
                                            "Planned Length: ${String.format(Locale.US, "%.2f km", plannedDistanceM / 1000.0)} (${String.format(Locale.US, "%,d m", plannedDistanceM)})"
                                        } else {
                                            "Direction Conflict: End Chainage does not match direction!"
                                        },
                                        color = if (isDirectionCompatible) colors.gold else colors.red,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Travel Direction", fontSize = 11.sp, color = colors.textSecondary)
                        SurveyGlassSegmentedToggle(
                            options = listOf(
                                SurveyDirection.INCREASING to "Increasing (+)",
                                SurveyDirection.DECREASING to "Decreasing (-)"
                            ),
                            selected = selectedDirection,
                            onSelect = { selectedDirection = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 4. Configurable Chainage Confirmation Interval (Default Every 2 KM)
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("4. CONFIRMATION INTERVAL", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)
                            Text("Default: Every 2 KM", fontSize = 10.sp, color = colors.gold, fontWeight = FontWeight.Medium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                ConfirmationIntervalOption.EVERY_1_KM to "1 KM",
                                ConfirmationIntervalOption.EVERY_2_KM to "2 KM",
                                ConfirmationIntervalOption.EVERY_3_KM to "3 KM",
                                ConfirmationIntervalOption.EVERY_5_KM to "5 KM",
                                ConfirmationIntervalOption.OFF to "Off"
                            ).forEach { (opt, label) ->
                                FilterChip(
                                    selected = selectedIntervalOption == opt,
                                    onClick = { selectedIntervalOption = opt },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.gold,
                                        selectedLabelColor = if (colors.isDark) Color.Black else Color.White
                                    )
                                )
                            }
                        }

                        if (selectedIntervalOption == ConfirmationIntervalOption.CUSTOM) {
                            OutlinedTextField(
                                value = customIntervalInput,
                                onValueChange = { customIntervalInput = it },
                                label = { Text("Custom Interval in metres") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                colors = textFieldColors
                            )
                        }
                    }

                    // 5. Expandable Advanced Details (Road Number, Stretch, Manual Offset)
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("5. ADVANCED HIGHWAY DETAILS", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = colors.textSecondary
                            )
                        }

                        AnimatedVisibility(visible = isAdvancedExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                OutlinedTextField(
                                    value = projectName,
                                    onValueChange = { projectName = it },
                                    label = { Text("Project Name (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                    colors = textFieldColors
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = roadNumber,
                                        onValueChange = { roadNumber = it },
                                        label = { Text("Road No (opt)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                        colors = textFieldColors
                                    )
                                    OutlinedTextField(
                                        value = stretchName,
                                        onValueChange = { stretchName = it },
                                        label = { Text("Stretch / Package (opt)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.5f),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                        colors = textFieldColors
                                    )
                                }
                                OutlinedTextField(
                                    value = manualOffsetInput,
                                    onValueChange = { manualOffsetInput = it },
                                    label = { Text("Centreline Offset (m)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                                    colors = textFieldColors
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SurveyGlassButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.NEUTRAL,
                        modifier = Modifier.weight(1f)
                    )

                    SurveyGlassButton(
                        text = "Start Live Survey",
                        icon = Icons.Default.PlayArrow,
                        onClick = {
                            if (isStartChainageValid && isDirectionCompatible) {
                                val offsetParsed = manualOffsetInput.toDoubleOrNull() ?: selectedLane.defaultOffsetMetres
                                val intervalM = when (selectedIntervalOption) {
                                    ConfirmationIntervalOption.OFF -> null
                                    ConfirmationIntervalOption.CUSTOM -> customIntervalInput.toLongOrNull()?.coerceAtLeast(100L) ?: 2000L
                                    else -> selectedIntervalOption.intervalMetres
                                }

                                val session = SurveySession(
                                    name = surveyName.trim(),
                                    projectName = projectName.trim(),
                                    highwayName = highwayName.trim(),
                                    roadNumber = roadNumber.trim(),
                                    stretchName = stretchName.trim(),
                                    surveyDate = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date()),
                                    startChainageMetres = parsedStartChainage!!.metres,
                                    plannedEndChainageMetres = parsedEndChainage?.metres,
                                    currentChainageMetres = parsedStartChainage.metres,
                                    direction = selectedDirection,
                                    roadSide = selectedRoadSide,
                                    lane = selectedLane,
                                    customLaneName = customLaneName.trim(),
                                    centrelineOffsetMetres = offsetParsed,
                                    offsetSide = selectedRoadSide.toOffsetSide(),
                                    isOffsetAuto = selectedRoadSide == RoadSide.AUTO,
                                    confirmationIntervalMetres = intervalM
                                )
                                onStartSurvey(session)
                            }
                        },
                        enabled = isStartChainageValid && isDirectionCompatible && highwayName.isNotBlank(),
                        variant = GlassButtonVariant.GOLD,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }
    }
}

/**
 * Smart Chainage Confirmation Popup
 * Displays:
 * - Current calculated chainage
 * - Checkpoint chainage
 * - Total surveyed distance
 * - Current Road Side & Current Lane
 * - Visible 10-second countdown
 * ONLY TWO ACTIONS:
 * 1. Confirm
 * 2. Edit Chainage
 * If 10 seconds elapse: auto-confirms current calculated chainage.
 * Coordinates are NEVER modified!
 */
@Composable
fun SmartChainageConfirmationDialog(
    prompt: CheckpointPrompt,
    onConfirm: () -> Unit,
    onConfirmWithCorrection: (Long) -> Unit
) {
    val colors = rememberSurveyThemeColors()
    var isEditing by remember { mutableStateOf(false) }
    var editInputText by remember { mutableStateOf(Chainage(prompt.calculatedChainageMetres).format()) }
    var remainingSeconds by remember { mutableIntStateOf(10) }

    val parsedCorrection = Chainage.parse(editInputText)
    val isCorrectionValid = parsedCorrection != null

    // 10-Second Auto-confirm Countdown (paused if user is actively editing chainage)
    LaunchedEffect(isEditing) {
        if (!isEditing) {
            remainingSeconds = 10
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds -= 1
            }
            // Auto confirm when 10s expires
            onConfirm()
        }
    }

    Dialog(onDismissRequest = { /* Modal: must Confirm or Edit to avoid accidental outside tap */ }) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.gold),
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.94f)
                .padding(8.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header with Checkpoint Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.gold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = colors.gold, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                text = "CHAINAGE CHECKPOINT #${prompt.checkpointIndex}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.gold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Every ${String.format(Locale.US, "%.0f km", prompt.targetDistanceM / 1000.0)} Confirmation",
                                fontSize = 10.5.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    // 10-second countdown pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (remainingSeconds <= 3) colors.red.copy(alpha = 0.15f) else colors.gold.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (remainingSeconds <= 3) colors.red else colors.gold.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isEditing) "PAUSED" else "${remainingSeconds}s",
                            color = if (remainingSeconds <= 3) colors.red else colors.gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (!isEditing) {
                    LinearProgressIndicator(
                        progress = { (remainingSeconds / 10f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = if (remainingSeconds <= 3) colors.red else colors.gold,
                        trackColor = colors.cardBorder.copy(alpha = 0.3f),
                    )
                }

                HorizontalDivider(color = colors.divider)

                // Checkpoint Metrics Summary Card
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (colors.isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Current Calculated Chainage", fontSize = 10.sp, color = colors.textSecondary)
                                Text(
                                    text = Chainage(prompt.calculatedChainageMetres).format(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.gold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Expected Checkpoint", fontSize = 10.sp, color = colors.textSecondary)
                                Text(
                                    text = Chainage(prompt.checkpointChainageMetres).format(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                            }
                        }

                        HorizontalDivider(color = colors.divider)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Total Surveyed Distance", fontSize = 10.sp, color = colors.textSecondary)
                                Text(
                                    text = String.format(Locale.US, "%.2f km", prompt.totalSurveyedDistanceM / 1000.0),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.green
                                )
                            }
                            Column {
                                Text("Road Side", fontSize = 10.sp, color = colors.textSecondary)
                                Text(
                                    text = prompt.roadSide,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.sky
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Lane", fontSize = 10.sp, color = colors.textSecondary)
                                Text(
                                    text = prompt.lane,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // If user selected Edit Chainage
                if (isEditing) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CORRECT CHAINAGE VALUE", style = MaterialTheme.typography.labelSmall, color = colors.gold, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = editInputText,
                            onValueChange = { editInputText = it },
                            label = { Text("Correct Chainage (e.g. 192+025)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                color = colors.gold,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            supportingText = {
                                Text(
                                    text = if (isCorrectionValid) "Correction: ${parsedCorrection!!.format()} (GPS location untouched)" else "Invalid format! Use km+m (e.g. 192+025)",
                                    color = if (isCorrectionValid) colors.green else colors.red,
                                    fontSize = 10.5.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.inputFocusedBorder,
                                unfocusedBorderColor = colors.inputBorder,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedLabelColor = colors.inputFocusedBorder,
                                unfocusedLabelColor = colors.textSecondary,
                                focusedContainerColor = colors.inputBackground,
                                unfocusedContainerColor = colors.inputBackground
                            )
                        )
                    }
                }

                // Actions: strictly Confirm and Edit Chainage
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isEditing) {
                        SurveyGlassButton(
                            text = "Cancel Edit",
                            onClick = { isEditing = false },
                            variant = GlassButtonVariant.NEUTRAL,
                            modifier = Modifier.weight(1f)
                        )
                        SurveyGlassButton(
                            text = "Save & Confirm",
                            icon = Icons.Default.Check,
                            onClick = {
                                if (isCorrectionValid) {
                                    onConfirmWithCorrection(parsedCorrection!!.metres)
                                }
                            },
                            enabled = isCorrectionValid,
                            variant = GlassButtonVariant.GOLD,
                            modifier = Modifier.weight(1.3f)
                        )
                    } else {
                        SurveyGlassButton(
                            text = "Edit Chainage",
                            icon = Icons.Default.Edit,
                            onClick = { isEditing = true },
                            variant = GlassButtonVariant.SKY,
                            modifier = Modifier.weight(1f)
                        )

                        SurveyGlassButton(
                            text = "Confirm (${remainingSeconds}s)",
                            icon = Icons.Default.Check,
                            onClick = onConfirm,
                            variant = GlassButtonVariant.GOLD,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog shown when Planned Ending Chainage is reached.
 * Never stops automatically without user confirmation!
 */
@Composable
fun EndingChainageReachedDialog(
    session: SurveySession,
    onFinishSurvey: () -> Unit,
    onContinueSurvey: () -> Unit
) {
    val colors = rememberSurveyThemeColors()

    Dialog(onDismissRequest = onContinueSurvey) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.gold),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.gold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = colors.gold, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            text = "Planned Ending Chainage Reached",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Survey reached target ${session.displayPlannedEndChainage}",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = colors.divider)

                Text(
                    text = "Current Chainage: ${session.displayCurrentChainage}\nTotal Surveyed: ${String.format(Locale.US, "%.2f km", session.totalDistanceTravelledMetres / 1000.0)}\n\nWould you like to finish and finalize the survey, or continue surveying further along this highway?",
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                    lineHeight = 17.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SurveyGlassButton(
                        text = "Continue Survey",
                        onClick = onContinueSurvey,
                        variant = GlassButtonVariant.NEUTRAL,
                        modifier = Modifier.weight(1f)
                    )
                    SurveyGlassButton(
                        text = "Finish Survey",
                        icon = Icons.Default.Check,
                        onClick = onFinishSurvey,
                        variant = GlassButtonVariant.GOLD,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}

/**
 * Quick Survey Settings Dialog
 * Allows changing Confirmation Interval, Road Side, Lane, and Planned Ending Chainage on the fly
 * during live surveying without stopping or resetting distance!
 */
@Composable
fun SurveyQuickSettingsDialog(
    session: SurveySession,
    onUpdateRoadSide: (RoadSide) -> Unit,
    onUpdateLane: (SurveyLane, String) -> Unit,
    onUpdateInterval: (Long?) -> Unit,
    onUpdatePlannedEnd: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = rememberSurveyThemeColors()
    var selectedSide by remember(session.roadSide) { mutableStateOf(session.roadSide) }
    var selectedLane by remember(session.lane) { mutableStateOf(session.lane) }
    var customLaneText by remember(session.customLaneName) { mutableStateOf(session.customLaneName) }
    var selectedInterval by remember(session.confirmationIntervalMetres) {
        mutableStateOf(ConfirmationIntervalOption.fromMetres(session.confirmationIntervalMetres))
    }
    var endChainageText by remember(session.plannedEndChainageMetres) {
        mutableStateOf(session.plannedEndChainageMetres?.let { Chainage(it).format() } ?: "")
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.inputFocusedBorder,
        unfocusedBorderColor = colors.inputBorder,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedLabelColor = colors.inputFocusedBorder,
        unfocusedLabelColor = colors.textSecondary,
        focusedContainerColor = colors.inputBackground,
        unfocusedContainerColor = colors.inputBackground
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.94f)
                .padding(8.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = colors.gold, modifier = Modifier.size(20.dp))
                        Text("SURVEY SETTINGS", fontWeight = FontWeight.Bold, color = colors.gold, fontSize = 14.sp)
                    }
                    SurveyGlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.NEUTRAL,
                        size = 30.dp,
                        iconSize = 16.dp
                    )
                }

                HorizontalDivider(color = colors.divider)

                // Road Side
                Text("ROAD SIDE", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)
                SurveyGlassSegmentedToggle(
                    options = listOf(
                        RoadSide.AUTO to "Auto",
                        RoadSide.LHS to "LHS",
                        RoadSide.RHS to "RHS",
                        RoadSide.BHS to "BHS"
                    ),
                    selected = selectedSide,
                    onSelect = {
                        selectedSide = it
                        onUpdateRoadSide(it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Lane
                Text("LANE", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        SurveyLane.INNER to "Inner",
                        SurveyLane.FAST to "Fast",
                        SurveyLane.OUTER to "Outer",
                        SurveyLane.SLOW to "Slow",
                        SurveyLane.SERVICE_ROAD to "Service"
                    ).forEach { (lane, label) ->
                        FilterChip(
                            selected = selectedLane == lane,
                            onClick = {
                                selectedLane = lane
                                onUpdateLane(lane, customLaneText)
                            },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.gold,
                                selectedLabelColor = if (colors.isDark) Color.Black else Color.White
                            )
                        )
                    }
                }

                // Confirmation Interval
                Text("CONFIRMATION INTERVAL", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        ConfirmationIntervalOption.EVERY_1_KM to "1 KM",
                        ConfirmationIntervalOption.EVERY_2_KM to "2 KM",
                        ConfirmationIntervalOption.EVERY_3_KM to "3 KM",
                        ConfirmationIntervalOption.EVERY_5_KM to "5 KM",
                        ConfirmationIntervalOption.OFF to "Off"
                    ).forEach { (opt, label) ->
                        FilterChip(
                            selected = selectedInterval == opt,
                            onClick = {
                                selectedInterval = opt
                                onUpdateInterval(opt.intervalMetres)
                            },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.gold,
                                selectedLabelColor = if (colors.isDark) Color.Black else Color.White
                            )
                        )
                    }
                }

                // Planned Ending Chainage
                Text("PLANNED ENDING CHAINAGE", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = endChainageText,
                    onValueChange = {
                        endChainageText = it
                        val parsed = Chainage.parse(it)
                        onUpdatePlannedEnd(parsed?.metres)
                    },
                    label = { Text("Ending Chainage (e.g. 205+000)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                    colors = textFieldColors
                )

                SurveyGlassButton(
                    text = "Done",
                    onClick = onDismiss,
                    variant = GlassButtonVariant.GOLD,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun AddKmStoneDialog(
    currentCalculatedChainageMetres: Long,
    currentLocation: GeoPoint?,
    currentAccuracy: Float,
    selectedLaneName: String,
    onDismiss: () -> Unit,
    onConfirmMarker: (KmMarkerType, Long, String) -> Unit
) {
    val colors = rememberSurveyThemeColors()
    val currentFormatted = Chainage(currentCalculatedChainageMetres).format()
    var inputChainageText by remember { mutableStateOf(currentFormatted) }
    var selectedMarkerType by remember { mutableStateOf(KmMarkerType.FULL_KM) }
    var notesText by remember { mutableStateOf("") }

    val parsed = Chainage.parse(inputChainageText)
    val isChainageValid = parsed != null

    val km = currentCalculatedChainageMetres / 1000
    val nearest1Km = (km * 1000)
    val nearestNext1Km = ((km + 1) * 1000)
    val nearest500m = (currentCalculatedChainageMetres / 500) * 500

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.inputFocusedBorder,
        unfocusedBorderColor = colors.inputBorder,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedLabelColor = colors.inputFocusedBorder,
        unfocusedLabelColor = colors.textSecondary,
        focusedContainerColor = colors.inputBackground,
        unfocusedContainerColor = colors.inputBackground
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.gold),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.94f)
                .padding(8.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = colors.gold, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MARK KM STONE", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.gold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 10.dp))

                Text(
                    text = "GPS Position: ${String.format(Locale.US, "%.5f, %.5f", currentLocation?.latitude ?: 0.0, currentLocation?.longitude ?: 0.0)} • ±${currentAccuracy.toInt()}m",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = "Calculated Chainage: $currentFormatted",
                    fontSize = 12.sp,
                    color = colors.sky,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("CONFIRM / CORRECT CHAINAGE", style = MaterialTheme.typography.labelSmall, color = colors.gold, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = inputChainageText,
                    onValueChange = { inputChainageText = it },
                    label = { Text("KM Stone Value") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = colors.gold,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    colors = textFieldColors
                )

                // Quick round snap chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        nearest1Km to "${nearest1Km / 1000}+000",
                        nearest500m to Chainage(nearest500m).format(),
                        nearestNext1Km to "${nearestNext1Km / 1000}+000"
                    ).forEach { (m, label) ->
                        FilterChip(
                            selected = parsed?.metres == m,
                            onClick = { inputChainageText = label },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.gold,
                                selectedLabelColor = if (colors.isDark) Color.Black else Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("MARKER TYPE", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KmMarkerType.entries.take(3).forEach { type ->
                        FilterChip(
                            selected = selectedMarkerType == type,
                            onClick = { selectedMarkerType = type },
                            label = { Text(type.shortBadge, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.gold,
                                selectedLabelColor = if (colors.isDark) Color.Black else Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes / Physical Condition (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                    colors = textFieldColors
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SurveyGlassButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.NEUTRAL,
                        modifier = Modifier.weight(1f)
                    )

                    SurveyGlassButton(
                        text = "Save Marker",
                        icon = Icons.Default.Check,
                        onClick = {
                            if (isChainageValid) {
                                onConfirmMarker(selectedMarkerType, parsed!!.metres, notesText.trim())
                            }
                        },
                        enabled = isChainageValid,
                        variant = GlassButtonVariant.GOLD,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun RelocationDialog(
    relocation: RelocationState,
    onConfirmNewSegment: () -> Unit,
    onConfirmConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = rememberSurveyThemeColors()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.gold),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = colors.gold, modifier = Modifier.size(24.dp))
                    Text("Movement While Paused", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }

                Text(
                    text = "The vehicle has moved ${String.format(Locale.US, "%.0f m", relocation.distanceM)} away from the last recorded point.",
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = "To maintain route integrity and avoid creating false straight connections across junctions or parallel roads, select how to proceed:",
                    fontSize = 11.5.sp,
                    color = colors.textSecondary,
                    lineHeight = 16.sp
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SurveyGlassButton(
                        text = "Resume as New Segment (Recommended)",
                        icon = Icons.Default.PlayArrow,
                        onClick = onConfirmNewSegment,
                        variant = GlassButtonVariant.GOLD,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SurveyGlassButton(
                            text = "Connect Line",
                            icon = Icons.Default.AltRoute,
                            onClick = onConfirmConnect,
                            variant = GlassButtonVariant.SKY,
                            modifier = Modifier.weight(1f)
                        )
                        SurveyGlassButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            variant = GlassButtonVariant.NEUTRAL,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChangeOffsetDialog(
    currentOffsetM: Double,
    currentSide: OffsetSide,
    defaultLaneOffsetM: Double = 7.0,
    isAutoMode: Boolean = false,
    onDismiss: () -> Unit,
    onSaveOffset: (Double, OffsetSide, Boolean) -> Unit
) {
    val colors = rememberSurveyThemeColors()
    var isAuto by remember { mutableStateOf(isAutoMode) }
    var offsetInput by remember { mutableStateOf(String.format(Locale.US, "%.2f", currentOffsetM)) }
    var selectedSide by remember { mutableStateOf(currentSide) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.inputFocusedBorder,
        unfocusedBorderColor = colors.inputBorder,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedLabelColor = colors.inputFocusedBorder,
        unfocusedLabelColor = colors.textSecondary,
        focusedContainerColor = colors.inputBackground,
        unfocusedContainerColor = colors.inputBackground
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .padding(8.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = colors.gold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CENTRELINE OFFSET", fontWeight = FontWeight.Bold, color = colors.gold, fontSize = 14.sp)
                    }
                    SurveyGlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.NEUTRAL,
                        size = 30.dp,
                        iconSize = 16.dp
                    )
                }

                HorizontalDivider(color = colors.divider)

                Text("MODE", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)
                SurveyGlassSegmentedToggle(
                    options = listOf(
                        true to "Auto (${defaultLaneOffsetM}m)",
                        false to "Manual Custom"
                    ),
                    selected = isAuto,
                    onSelect = { auto ->
                        isAuto = auto
                        if (auto) {
                            offsetInput = String.format(Locale.US, "%.2f", defaultLaneOffsetM)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isAuto) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surfaceHighlight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.gold.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lane Default: ${defaultLaneOffsetM}m",
                                color = colors.gold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Automatic",
                                color = colors.green,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = offsetInput,
                        onValueChange = { offsetInput = it },
                        label = { Text("Offset Distance (metres)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.gold, fontWeight = FontWeight.Bold),
                        colors = textFieldColors
                    )
                }

                Text("OFFSET SIDE", style = MaterialTheme.typography.labelSmall, color = colors.sky, fontWeight = FontWeight.Bold)
                SurveyGlassSegmentedToggle(
                    options = listOf(
                        OffsetSide.LEFT to "LHS",
                        OffsetSide.RIGHT to "RHS",
                        OffsetSide.CENTRE to "Centre (0m)"
                    ),
                    selected = selectedSide,
                    onSelect = { side ->
                        selectedSide = side
                        if (side == OffsetSide.CENTRE) {
                            offsetInput = "0.00"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SurveyGlassButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.NEUTRAL,
                        modifier = Modifier.weight(1f)
                    )
                    SurveyGlassButton(
                        text = "Apply Offset",
                        icon = Icons.Default.Check,
                        onClick = {
                            val v = if (isAuto) defaultLaneOffsetM else (offsetInput.toDoubleOrNull() ?: currentOffsetM)
                            val finalSide = if (v == 0.0) OffsetSide.CENTRE else selectedSide
                            onSaveOffset(v.coerceAtLeast(0.0), finalSide, isAuto)
                        },
                        variant = GlassButtonVariant.GOLD,
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun SavedSurveysDialog(
    sessions: List<SurveySession>,
    onSelectSession: (SurveySession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = rememberSurveyThemeColors()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.95f)
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SAVED HIGHWAY SURVEYS", fontWeight = FontWeight.Bold, color = colors.sky, fontSize = 14.sp)
                    SurveyGlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.NEUTRAL,
                        size = 30.dp,
                        iconSize = 16.dp
                    )
                }

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

                if (sessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("No saved surveys recorded yet.", color = colors.textSecondary, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sessions) { s ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (colors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSession(s) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(s.highwayName.ifBlank { s.name }, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = "${s.displayStartChainage} → ${s.displayCurrentChainage} • ${String.format(Locale.US, "%.1f km", s.totalDistanceTravelledMetres / 1000.0)}",
                                            color = colors.gold,
                                            fontSize = 11.sp
                                        )
                                        Text("${s.displayRoadSide} • ${s.displayLaneName} • ${s.status.label} • ${s.surveyDate}", color = colors.textSecondary, fontSize = 10.sp)
                                    }
                                    SurveyGlassIconButton(
                                        icon = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        onClick = { onDeleteSession(s.id) },
                                        variant = GlassButtonVariant.DESTRUCTIVE,
                                        size = 32.dp,
                                        iconSize = 16.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Launch-time dialog that alerts the user when an interrupted or unfinished survey is detected on app startup,
 * displaying key session metrics and offering a clear 1-tap option to resume live tracking.
 */
@Composable
fun ResumeSessionDialog(
    session: SurveySession,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = rememberSurveyThemeColors()
    val totalDistKm = session.totalDistanceTravelledMetres / 1000.0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.gold),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.gold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Interrupted Survey",
                            tint = colors.gold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Resume Survey Session?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Interrupted survey detected from previous run",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = colors.divider)

                // Survey Details Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (colors.isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.gold.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.name.ifBlank { "Highway Survey" },
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.gold.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.gold.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = session.status.label,
                                    color = colors.gold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (session.highwayName.isNotBlank() || session.stretchName.isNotBlank()) {
                            Text(
                                text = "${session.highwayName} ${if (session.stretchName.isNotBlank()) "• " + session.stretchName else ""}",
                                color = colors.sky,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 4.dp))

                        // Chainage & Distance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Chainage Span", fontSize = 10.sp, color = colors.textSecondary)
                                Text(
                                    text = "${session.displayStartChainage} ➔ ${session.displayCurrentChainage}",
                                    color = colors.gold,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Distance", fontSize = 10.sp, color = colors.textSecondary)
                                Text(
                                    text = String.format(Locale.US, "%.2f km", totalDistKm),
                                    color = colors.green,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Lane & Offset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${session.displayRoadSide} • ${session.displayLaneName}",
                                color = colors.textPrimary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Offset: ${session.centrelineOffsetMetres}m",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Text(
                    text = "Would you like to resume live GPS tracking and chainage calculation from the last recorded point?",
                    color = colors.textPrimary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                SurveyGlassButton(
                    text = "Resume Session",
                    icon = Icons.Default.PlayArrow,
                    onClick = onResume,
                    variant = GlassButtonVariant.GOLD,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SurveyGlassButton(
                        text = "Discard",
                        icon = Icons.Default.Delete,
                        onClick = onDiscard,
                        variant = GlassButtonVariant.DESTRUCTIVE,
                        modifier = Modifier.weight(1f)
                    )
                    SurveyGlassButton(
                        text = "Later",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.NEUTRAL,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
