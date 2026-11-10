package com.example.ui.survey

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.Chainage
import com.example.model.GeoPoint
import com.example.survey.engine.RelocationState
import com.example.survey.model.KmMarkerType
import com.example.survey.model.OffsetSide
import com.example.survey.model.SurveyDirection
import com.example.survey.model.SurveyLane
import com.example.survey.model.SurveyMarker
import com.example.survey.model.SurveySession
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
    var projectName by remember { mutableStateOf(initialProjectName) }
    var highwayName by remember { mutableStateOf(initialHighwayName.ifBlank { "NH-44" }) }
    var roadNumber by remember { mutableStateOf("") }
    var stretchName by remember { mutableStateOf("") }
    var surveyName by remember { mutableStateOf("Survey ${SimpleDateFormat("dd-MMM HH:mm", Locale.US).format(Date())}") }
    var startChainageInput by remember { mutableStateOf("0+000") }
    var selectedLane by remember { mutableStateOf(SurveyLane.MCW_FAST) }
    var customLaneName by remember { mutableStateOf("") }
    var selectedDirection by remember { mutableStateOf(SurveyDirection.INCREASING) }
    var offsetM by remember { mutableDoubleStateOf(selectedLane.defaultOffsetMetres) }
    var offsetInputText by remember { mutableStateOf(String.format(Locale.US, "%.2f", selectedLane.defaultOffsetMetres)) }
    var offsetSide by remember { mutableStateOf(OffsetSide.RIGHT) }
    var isOffsetAuto by remember { mutableStateOf(false) }
    var isLaneDropdownOpen by remember { mutableStateOf(false) }

    val parsedChainage = Chainage.parse(startChainageInput)
    val isChainageValid = parsedChainage != null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
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
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NEW HIGHWAY SURVEY",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        // Highway / Project details
                        Text("1. ROAD & PROJECT INFORMATION", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = highwayName,
                                onValueChange = { highwayName = it },
                                label = { Text("Highway / Road Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFFD700),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                                )
                            )
                            OutlinedTextField(
                                value = roadNumber,
                                onValueChange = { roadNumber = it },
                                label = { Text("Road No (opt)") },
                                singleLine = true,
                                modifier = Modifier.width(105.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFFD700),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                                )
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = stretchName,
                            onValueChange = { stretchName = it },
                            label = { Text("Package / Stretch Section (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFD700),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                    }

                    item {
                        // Starting Chainage
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("2. STARTING CHAINAGE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = startChainageInput,
                            onValueChange = { startChainageInput = it },
                            label = { Text("Starting Chainage (e.g. 198+000 or 12450)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isChainageValid) Color(0xFFFFD700) else Color(0xFFEF4444), fontWeight = FontWeight.Bold),
                            supportingText = {
                                Text(
                                    text = if (isChainageValid) "Formatted: ${parsedChainage?.format()} (${parsedChainage?.metres}m)" else "Invalid format! Use km+m (e.g. 12+500)",
                                    color = if (isChainageValid) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    fontSize = 10.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFD700),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                    }

                    item {
                        // Lane Selection
                        Text("3. LANE & CARRIAGEWAY", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
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
                                        Text(selectedLane.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Standard CL offset: ${selectedLane.defaultOffsetMetres}m", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFFFFD700))
                                }
                            }
                            DropdownMenu(
                                expanded = isLaneDropdownOpen,
                                onDismissRequest = { isLaneDropdownOpen = false },
                                modifier = Modifier.background(Color(0xFF1E293B))
                            ) {
                                SurveyLane.entries.forEach { lane ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(lane.label, color = Color.White, fontSize = 12.sp)
                                                if (lane == selectedLane) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedLane = lane
                                            offsetM = lane.defaultOffsetMetres
                                            offsetInputText = String.format(Locale.US, "%.2f", lane.defaultOffsetMetres)
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
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFFD700),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                                )
                            )
                        }
                    }

                    item {
                        // Direction Selection
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("4. DIRECTION OF TRAVEL", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
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

                    item {
                        // 5. Unified Centreline Offset Control (Auto vs Manual)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("5. CENTRELINE OFFSET & SIDE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)

                        // Mode toggle: Auto (from Lane) vs Manual
                        SurveyGlassSegmentedToggle(
                            options = listOf(
                                true to "Auto (${selectedLane.defaultOffsetMetres}m from Lane)",
                                false to "Manual Custom"
                            ),
                            selected = isOffsetAuto,
                            onSelect = { auto ->
                                isOffsetAuto = auto
                                if (auto) {
                                    offsetM = selectedLane.defaultOffsetMetres
                                    offsetInputText = String.format(Locale.US, "%.2f", selectedLane.defaultOffsetMetres)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (isOffsetAuto) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x330F172A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x40FFD700)),
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
                                        text = "Derived Offset: ${selectedLane.defaultOffsetMetres}m",
                                        color = Color(0xFFFFD700),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = selectedLane.label,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = offsetInputText,
                                onValueChange = { input ->
                                    offsetInputText = input
                                    val parsed = input.toDoubleOrNull()
                                    if (parsed != null && parsed >= 0.0) {
                                        offsetM = parsed
                                    }
                                },
                                label = { Text("Custom Centreline Offset (metres)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFFFD700), fontWeight = FontWeight.Bold),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFFD700),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Offset Side Selector
                        SurveyGlassSegmentedToggle(
                            options = listOf(
                                OffsetSide.LEFT to "LHS (Left of CL)",
                                OffsetSide.RIGHT to "RHS (Right of CL)",
                                OffsetSide.CENTRE to "Centreline (0m)"
                            ),
                            selected = offsetSide,
                            onSelect = { side ->
                                offsetSide = side
                                if (side == OffsetSide.CENTRE) {
                                    offsetM = 0.0
                                    offsetInputText = "0.00"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Minimalist Glass Action Buttons
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
                            if (isChainageValid) {
                                val finalOffset = if (isOffsetAuto) selectedLane.defaultOffsetMetres else offsetM
                                val session = SurveySession(
                                    name = surveyName,
                                    projectName = projectName.trim(),
                                    highwayName = highwayName.trim(),
                                    roadNumber = roadNumber.trim(),
                                    stretchName = stretchName.trim(),
                                    surveyDate = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date()),
                                    startChainageMetres = parsedChainage!!.metres,
                                    currentChainageMetres = parsedChainage.metres,
                                    direction = selectedDirection,
                                    lane = selectedLane,
                                    customLaneName = customLaneName.trim(),
                                    centrelineOffsetMetres = finalOffset,
                                    offsetSide = offsetSide,
                                    isOffsetAuto = isOffsetAuto
                                )
                                onStartSurvey(session)
                            }
                        },
                        enabled = isChainageValid && highwayName.isNotBlank(),
                        variant = GlassButtonVariant.GOLD,
                        modifier = Modifier.weight(1.5f)
                    )
                }
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
    val currentFormatted = Chainage(currentCalculatedChainageMetres).format()
    var inputChainageText by remember { mutableStateOf(currentFormatted) }
    var selectedMarkerType by remember { mutableStateOf(KmMarkerType.FULL_KM) }
    var notesText by remember { mutableStateOf("") }

    val parsed = Chainage.parse(inputChainageText)
    val isChainageValid = parsed != null

    // Nearest standard round KM suggestions
    val km = currentCalculatedChainageMetres / 1000
    val nearest1Km = (km * 1000)
    val nearestNext1Km = ((km + 1) * 1000)
    val nearest500m = (currentCalculatedChainageMetres / 500) * 500

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
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
                        Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MARK KM STONE", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFFFD700))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 10.dp))

                Text("GPS Position: ${String.format(Locale.US, "%.5f, %.5f", currentLocation?.latitude ?: 0.0, currentLocation?.longitude ?: 0.0)} • ±${currentAccuracy.toInt()}m", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                Text("Calculated Chainage: $currentFormatted", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)

                Spacer(modifier = Modifier.height(10.dp))
                Text("CONFIRM / CORRECT CHAINAGE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = inputChainageText,
                    onValueChange = { inputChainageText = it },
                    label = { Text("KM Stone Value") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD700),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    )
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
                                selectedContainerColor = Color(0xFFFFD700),
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("MARKER TYPE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
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
                                selectedContainerColor = Color(0xFFFFD700),
                                selectedLabelColor = Color.Black
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
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD700),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
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
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
            modifier = Modifier.padding(12.dp)
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
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                    Text("Movement While Paused", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Text(
                    text = "The vehicle has moved ${String.format(Locale.US, "%.0f m", relocation.distanceM)} away from the last recorded point.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = "To maintain route integrity and avoid creating false straight connections across junctions or parallel roads, select how to proceed:",
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.65f),
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
    var isAuto by remember { mutableStateOf(isAutoMode) }
    var offsetInput by remember { mutableStateOf(String.format(Locale.US, "%.2f", currentOffsetM)) }
    var selectedSide by remember { mutableStateOf(currentSide) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f)),
            modifier = Modifier.padding(12.dp)
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
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CENTRELINE OFFSET", fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), fontSize = 14.sp)
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

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                // Unified Mode Toggle: Auto (Lane Default) vs Manual Custom
                Text("MODE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
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
                        color = Color(0x330F172A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x40FFD700)),
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
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Automatic",
                                color = Color(0xFF22C55E),
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
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFFFD700), fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }

                Text("OFFSET SIDE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
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
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SAVED HIGHWAY SURVEYS", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 14.sp)
                    SurveyGlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.NEUTRAL,
                        size = 30.dp,
                        iconSize = 16.dp
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))

                if (sessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("No saved surveys recorded yet.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sessions) { s ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
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
                                        Text(s.highwayName.ifBlank { s.name }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${s.displayStartChainage} → ${s.displayCurrentChainage} • ${String.format(Locale.US, "%.1f km", s.totalDistanceTravelledMetres / 1000.0)}", color = Color(0xFFFFD700), fontSize = 11.sp)
                                        Text("${s.displayLaneName} • ${s.status.label} • ${s.surveyDate}", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
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
    session: com.example.survey.model.SurveySession,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalDistKm = session.totalDistanceTravelledMetres / 1000.0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
            modifier = Modifier.padding(12.dp)
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
                            .background(Color(0xFFFFD700).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Interrupted Survey",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Resume Survey Session?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Interrupted survey detected from previous run",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                // Survey Details Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.35f)),
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
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFD700).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFFD700).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = session.status.label,
                                    color = Color(0xFFFFD700),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (session.highwayName.isNotBlank() || session.stretchName.isNotBlank()) {
                            Text(
                                text = "${session.highwayName} ${if (session.stretchName.isNotBlank()) "• " + session.stretchName else ""}",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                        // Chainage & Distance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Chainage Span", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                                Text(
                                    text = "${session.displayStartChainage} ➔ ${session.displayCurrentChainage}",
                                    color = Color(0xFFFFD700),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Distance", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                                Text(
                                    text = String.format(java.util.Locale.US, "%.2f km", totalDistKm),
                                    color = Color(0xFF22C55E),
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
                                text = "Lane: ${session.displayLaneName}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Offset: ${session.centrelineOffsetMetres}m ${session.offsetSide.name}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Text(
                    text = "Would you like to resume live GPS tracking and chainage calculation from the last recorded point?",
                    color = Color.White.copy(alpha = 0.85f),
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

