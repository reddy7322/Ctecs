package com.example.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DelayRiskSummary
import com.example.model.HighwayWorkActivity
import com.example.model.ValueSourceTag
import com.example.model.WeatherAlertSeverity
import com.example.model.WorkTargetInput
import com.example.model.WorkTargetPrediction
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import java.util.Locale

/**
 * Compact Smart-Dropdown WORK TARGET PREDICTION & WEATHER DELAY ESTIMATION section.
 *
 * Displays essential calculated figures up front with explicit ESTIMATE labeling, keeps
 * important delay warnings visible even when collapsed, and expands to full calculation
 * breakdown and editable project/resource inputs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkTargetAndRiskSection(
    workTargetInput: WorkTargetInput,
    workTargetPrediction: WorkTargetPrediction,
    delayRiskSummary: DelayRiskSummary,
    onUpdateWorkTargetInput: (WorkTargetInput) -> Unit,
    onSelectActivity: (HighwayWorkActivity) -> Unit,
    expandedSections: Set<String> = emptySet(),
    onToggleSection: (String) -> Unit = {}
) {
    val c = CtecsTheme.colors

    var startChText by remember(workTargetInput.startChainageKm) {
        mutableStateOf(workTargetInput.startChainageKm?.let { String.format(Locale.US, "%.3f", it) } ?: "")
    }
    var endChText by remember(workTargetInput.endChainageKm) {
        mutableStateOf(workTargetInput.endChainageKm?.let { String.format(Locale.US, "%.3f", it) } ?: "")
    }
    var widthText by remember(workTargetInput.workingWidthMeters) {
        mutableStateOf(String.format(Locale.US, "%.2f", workTargetInput.workingWidthMeters))
    }
    var thicknessText by remember(workTargetInput.layerThicknessMm) {
        mutableStateOf(String.format(Locale.US, "%.0f", workTargetInput.layerThicknessMm))
    }
    var rateText by remember(workTargetInput.expectedProductionRatePerHour) {
        mutableStateOf(workTargetInput.expectedProductionRatePerHour?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var hoursText by remember(workTargetInput.availableWorkingHours) {
        mutableStateOf(String.format(Locale.US, "%.1f", workTargetInput.availableWorkingHours))
    }
    var hmpText by remember(workTargetInput.hmpCapacityTph) {
        mutableStateOf(workTargetInput.hmpCapacityTph?.let { String.format(Locale.US, "%.0f", it) } ?: "")
    }
    var dumpersText by remember(workTargetInput.dumpersCount) {
        mutableStateOf(workTargetInput.dumpersCount?.toString() ?: "")
    }
    var paverText by remember(workTargetInput.paverCapacitySqmPerHour) {
        mutableStateOf(workTargetInput.paverCapacitySqmPerHour?.let { String.format(Locale.US, "%.0f", it) } ?: "")
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("work_target_and_risk_section")
    ) {
        // 1. Target Prediction (Smart Dropdown)
        item(key = "section_target_prediction") {
            val hasInputs = workTargetPrediction.missingInputWarnings.isEmpty()
            val pct = workTargetPrediction.expectedCompletionPercent.coerceIn(0.0, 100.0)
            val summaryText = if (hasInputs) {
                "${workTargetInput.activity.shortName}: ${String.format(Locale.US, "%.0f", workTargetPrediction.weatherAdjustedTargetQuantity)} / ${String.format(Locale.US, "%.0f", workTargetPrediction.plannedTargetQuantity)} ${workTargetPrediction.quantityUnit} • ${pct.toInt()}%"
            } else {
                "${workTargetInput.activity.shortName}: Inputs required — tap to enter"
            }

            WeatherSmartSection(
                sectionKey = WeatherSectionKeys.TARGET_PREDICTION,
                title = "Target Prediction",
                summaryText = summaryText,
                badgeText = "ESTIMATE",
                badgeColor = c.warning,
                icon = Icons.Rounded.Calculate,
                iconTint = c.accent,
                isExpanded = WeatherSectionKeys.TARGET_PREDICTION in expandedSections,
                onToggle = { onToggleSection(WeatherSectionKeys.TARGET_PREDICTION) },
                modifier = Modifier.testTag("work_target_prediction_card"),
                alwaysVisibleContent = if (hasInputs) {
                    {
                        LinearProgressIndicator(
                            progress = { (pct / 100.0).toFloat() },
                            color = if (pct >= 80.0) c.success else if (pct >= 45.0) c.warning else c.error,
                            trackColor = c.secondarySurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Workable: ${String.format(Locale.US, "%.1f", workTargetPrediction.netEffectiveWorkingHours)}/${String.format(Locale.US, "%.1f", workTargetPrediction.plannedWorkingHours)} hr",
                                color = c.textSecondary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Delay: ${String.format(Locale.US, "%.1f", workTargetPrediction.estimatedDelayHours)} hr",
                                color = if (workTargetPrediction.estimatedDelayHours > 0.0) c.warning else c.success,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Window: ${workTargetPrediction.alternativeWorkWindowLabel ?: "Forecast-dependent"}",
                                color = c.textMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else null
            ) {
                if (!hasInputs) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(c.warning.copy(alpha = 0.12f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Required Inputs", color = c.warning, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        workTargetPrediction.missingInputWarnings.forEach { warn ->
                            Text("• $warn", color = c.textPrimary, fontSize = 10.sp)
                        }
                    }
                } else {
                    CompactMetricRow(
                        "Planned / Weather-Adjusted",
                        "${String.format(Locale.US, "%.1f", workTargetPrediction.plannedTargetQuantity)} / ${String.format(Locale.US, "%.1f", workTargetPrediction.weatherAdjustedTargetQuantity)} ${workTargetPrediction.quantityUnit}"
                    )
                    CompactMetricRow(
                        "Length / Area",
                        "${String.format(Locale.US, "%.0f", workTargetPrediction.totalLengthMeters)} m / ${String.format(Locale.US, "%.0f", workTargetPrediction.totalAreaSqm)} m²"
                    )
                    CompactMetricRow(
                        "Compacted Volume",
                        "${String.format(Locale.US, "%.1f", workTargetPrediction.compactedVolumeM3)} m³"
                    )
                    CompactMetricRow(
                        "Remaining Balance",
                        "${String.format(Locale.US, "%.1f", workTargetPrediction.remainingBalanceQuantity)} ${workTargetPrediction.quantityUnit}" +
                            (workTargetPrediction.remainingHoursNeeded?.let { " (${String.format(Locale.US, "%.1f", it)} hr)" } ?: "")
                    )
                    CompactMetricRow(
                        "Rate Source",
                        workTargetPrediction.rateSourceTag.label,
                        valueColor = c.accent
                    )
                    CompactMetricRow("Limiting Resource", workTargetPrediction.limitingResourceNote)
                    Text(workTargetPrediction.quantityBasisNote, color = c.textMuted, fontSize = 9.5.sp)

                    if (workTargetPrediction.targetReductionReasons.isNotEmpty()) {
                        workTargetPrediction.targetReductionReasons.forEach { reason ->
                            Text("• $reason", color = c.textSecondary, fontSize = 10.sp)
                        }
                    }
                }

                SmartFadeDivider()

                // Editable Calculation Inputs inside expanded Target Prediction
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("work_target_inputs_card"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Calculation Inputs",
                        color = c.textMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HighwayWorkActivity.entries.forEach { act ->
                            FilterChip(
                                selected = workTargetInput.activity == act,
                                onClick = {
                                    onSelectActivity(act)
                                    onUpdateWorkTargetInput(
                                        workTargetInput.copy(
                                            activity = act,
                                            layerThicknessMm = if (act.defaultThicknessMm > 0.0) act.defaultThicknessMm else workTargetInput.layerThicknessMm
                                        )
                                    )
                                },
                                label = { Text(act.shortName, fontSize = 9.5.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = startChText,
                            onValueChange = { txt ->
                                startChText = txt
                                onUpdateWorkTargetInput(workTargetInput.copy(startChainageKm = txt.toDoubleOrNull()))
                            },
                            label = { Text("Start Ch (km)", fontSize = 9.5.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_start_chainage_km")
                        )
                        OutlinedTextField(
                            value = endChText,
                            onValueChange = { txt ->
                                endChText = txt
                                onUpdateWorkTargetInput(workTargetInput.copy(endChainageKm = txt.toDoubleOrNull()))
                            },
                            label = { Text("End Ch (km)", fontSize = 9.5.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_end_chainage_km")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { txt ->
                                widthText = txt
                                txt.toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                                    onUpdateWorkTargetInput(workTargetInput.copy(workingWidthMeters = it))
                                }
                            },
                            label = { Text("Width (m)", fontSize = 9.5.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = thicknessText,
                            onValueChange = { txt ->
                                thicknessText = txt
                                txt.toDoubleOrNull()?.takeIf { it >= 0.0 }?.let {
                                    onUpdateWorkTargetInput(workTargetInput.copy(layerThicknessMm = it))
                                }
                            },
                            label = { Text("Thick (mm)", fontSize = 9.5.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = hoursText,
                            onValueChange = { txt ->
                                hoursText = txt
                                txt.toDoubleOrNull()?.takeIf { it in 0.5..24.0 }?.let {
                                    onUpdateWorkTargetInput(workTargetInput.copy(availableWorkingHours = it))
                                }
                            },
                            label = { Text("Shift (h)", fontSize = 9.5.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.85f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = rateText,
                            onValueChange = { txt ->
                                rateText = txt
                                onUpdateWorkTargetInput(workTargetInput.copy(expectedProductionRatePerHour = txt.toDoubleOrNull()))
                            },
                            label = { Text("Rate (${workTargetInput.activity.productionUnitLabel})", fontSize = 9.5.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("input_production_rate")
                        )
                        OutlinedTextField(
                            value = hmpText,
                            onValueChange = { txt ->
                                hmpText = txt
                                onUpdateWorkTargetInput(workTargetInput.copy(hmpCapacityTph = txt.toDoubleOrNull()))
                            },
                            label = { Text("HMP (TPH)", fontSize = 9.5.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = paverText,
                            onValueChange = { txt ->
                                paverText = txt
                                onUpdateWorkTargetInput(workTargetInput.copy(paverCapacitySqmPerHour = txt.toDoubleOrNull()))
                            },
                            label = { Text("Paver (m²/h)", fontSize = 9.5.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dumpersText,
                            onValueChange = { txt ->
                                dumpersText = txt
                                onUpdateWorkTargetInput(workTargetInput.copy(dumpersCount = txt.toIntOrNull()))
                            },
                            label = { Text("Dumpers", fontSize = 9.5.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ValueSourceTag.entries.forEach { tag ->
                            FilterChip(
                                selected = workTargetInput.productionRateSource == tag,
                                onClick = { onUpdateWorkTargetInput(workTargetInput.copy(productionRateSource = tag)) },
                                label = { Text(tag.label, fontSize = 9.5.sp) }
                            )
                        }
                    }

                    Text(
                        text = workTargetPrediction.estimateDisclaimer,
                        color = c.textMuted,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // 2. Weather Delay Estimation (Smart Dropdown; important warnings remain visible)
        item(key = "section_weather_delay_estimation") {
            val nextWin = delayRiskSummary.nextSuitableWindowLabel ?: "Forecast-dependent"
            val severeRisks = delayRiskSummary.warnings.filter {
                it.severity == WeatherAlertSeverity.SEVERE || it.severity == WeatherAlertSeverity.WARNING
            }
            val topRiskColor = when (delayRiskSummary.warnings.firstOrNull()?.severity) {
                WeatherAlertSeverity.SEVERE -> c.error
                WeatherAlertSeverity.WARNING -> c.warning
                WeatherAlertSeverity.ADVISORY -> c.accent
                null -> c.success
            }

            WeatherSmartSection(
                sectionKey = WeatherSectionKeys.WEATHER_DELAY_ESTIMATION,
                title = "Weather Delay Estimation",
                summaryText = "Delay: ${delayRiskSummary.expectedDelayRangeLabel} • Next Window: $nextWin",
                badgeText = delayRiskSummary.overallConfidence.label,
                badgeColor = topRiskColor,
                icon = Icons.Rounded.Schedule,
                iconTint = topRiskColor,
                isExpanded = WeatherSectionKeys.WEATHER_DELAY_ESTIMATION in expandedSections,
                onToggle = { onToggleSection(WeatherSectionKeys.WEATHER_DELAY_ESTIMATION) },
                modifier = Modifier.testTag("weather_delay_risk_card"),
                alwaysVisibleContent = if (severeRisks.isNotEmpty()) {
                    {
                        severeRisks.take(2).forEach { risk ->
                            val rColor = if (risk.severity == WeatherAlertSeverity.SEVERE) c.error else c.warning
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(rColor.copy(alpha = if (c.isDark) 0.16f else 0.10f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = rColor, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "${risk.title} (${risk.probabilityRangeLabel}) • ${risk.impactedWindowLabel}",
                                    color = c.textPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else null
            ) {
                CompactMetricRow("Expected Delay", delayRiskSummary.expectedDelayRangeLabel)
                CompactMetricRow("Confidence", delayRiskSummary.overallConfidence.label)
                CompactMetricRow("Next Suitable Window", nextWin, valueColor = c.success)
                Text(
                    text = delayRiskSummary.dailyTargetImpactSummary,
                    color = c.textSecondary,
                    fontSize = 10.5.sp
                )

                if (delayRiskSummary.warnings.isEmpty()) {
                    Text(
                        text = "No significant weather delay risks in next 24h.",
                        color = c.success,
                        fontSize = 10.5.sp
                    )
                } else {
                    SmartFadeDivider()
                    delayRiskSummary.warnings.forEach { risk ->
                        val riskColor = when (risk.severity) {
                            WeatherAlertSeverity.SEVERE -> c.error
                            WeatherAlertSeverity.WARNING -> c.warning
                            WeatherAlertSeverity.ADVISORY -> c.accent
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(riskColor.copy(alpha = if (c.isDark) 0.14f else 0.09f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = riskColor, modifier = Modifier.size(14.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${risk.title} • ${risk.probabilityRangeLabel}",
                                    color = c.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${risk.impactedWindowLabel} • Conf: ${risk.confidence.label}",
                                    color = riskColor,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = risk.impactSummary,
                                    color = c.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "targets_bottom_pad") { Spacer(Modifier.height(14.dp)) }
    }
}
