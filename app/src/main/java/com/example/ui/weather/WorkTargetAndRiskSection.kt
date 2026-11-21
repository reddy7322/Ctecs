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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DelayRiskSummary
import com.example.model.HighwayWorkActivity
import com.example.model.ValueSourceTag
import com.example.model.WeatherAlertSeverity
import com.example.model.WorkTargetInput
import com.example.model.WorkTargetPrediction
import com.example.ui.theme.CtecsTheme
import java.util.Locale

/**
 * WORK TARGET & COMPLETION PREDICTION + WEATHER DELAY & RISK ANALYSIS section.
 *
 * Clearly separates user-entered / historical / estimated inputs and explicitly labels
 * all calculated outputs as engineering estimates (never guaranteed production).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkTargetAndRiskSection(
    workTargetInput: WorkTargetInput,
    workTargetPrediction: WorkTargetPrediction,
    delayRiskSummary: DelayRiskSummary,
    onUpdateWorkTargetInput: (WorkTargetInput) -> Unit,
    onSelectActivity: (HighwayWorkActivity) -> Unit
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
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("work_target_and_risk_section")
    ) {
        // 1. Work Target & Completion Prediction Output Summary
        item(key = "prediction_summary_card") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("work_target_prediction_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Calculate, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                            Text(
                                text = "WORK TARGET & COMPLETION PREDICTION",
                                color = c.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = c.warning.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "ESTIMATE",
                                color = c.warning,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (workTargetPrediction.missingInputWarnings.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = c.warning.copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Inputs Needed to Compute Prediction:",
                                    color = c.warning,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                workTargetPrediction.missingInputWarnings.forEach { warn ->
                                    Text("• $warn", color = c.textPrimary, fontSize = 10.5.sp)
                                }
                            }
                        }
                    } else {
                        val pct = workTargetPrediction.expectedCompletionPercent.coerceIn(0.0, 100.0)
                        LinearProgressIndicator(
                            progress = { (pct / 100.0).toFloat() },
                            color = if (pct >= 80.0) c.success else if (pct >= 45.0) c.warning else c.error,
                            trackColor = c.secondarySurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PredictionStat(
                                label = "Planned Target",
                                value = "${String.format(Locale.US, "%.1f", workTargetPrediction.plannedTargetQuantity)} ${workTargetPrediction.quantityUnit}"
                            )
                            PredictionStat(
                                label = "Weather-Adjusted",
                                value = "${String.format(Locale.US, "%.1f", workTargetPrediction.weatherAdjustedTargetQuantity)} ${workTargetPrediction.quantityUnit}"
                            )
                            PredictionStat(
                                label = "Est. Completion",
                                value = "${pct.toInt()}%"
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PredictionStat(
                                label = "Length / Area",
                                value = "${String.format(Locale.US, "%.0f", workTargetPrediction.totalLengthMeters)} m / ${String.format(Locale.US, "%.0f", workTargetPrediction.totalAreaSqm)} m²"
                            )
                            PredictionStat(
                                label = "Compacted Vol.",
                                value = "${String.format(Locale.US, "%.1f", workTargetPrediction.compactedVolumeM3)} m³"
                            )
                            PredictionStat(
                                label = "Est. Delay",
                                value = "${String.format(Locale.US, "%.1f", workTargetPrediction.estimatedDelayHours)} hr"
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PredictionStat(
                                label = "Net Workable",
                                value = "${String.format(Locale.US, "%.1f", workTargetPrediction.netEffectiveWorkingHours)} / ${String.format(Locale.US, "%.1f", workTargetPrediction.plannedWorkingHours)} hr"
                            )
                            PredictionStat(
                                label = "Remaining Bal.",
                                value = "${String.format(Locale.US, "%.1f", workTargetPrediction.remainingBalanceQuantity)} ${workTargetPrediction.quantityUnit}"
                            )
                            PredictionStat(
                                label = "Rem. Hours",
                                value = workTargetPrediction.remainingHoursNeeded?.let { "${String.format(Locale.US, "%.1f", it)} hr" } ?: "—"
                            )
                        }

                        Text(
                            text = "Rate Source: ${workTargetPrediction.rateSourceTag.label} • ${workTargetPrediction.limitingResourceNote}",
                            color = c.accent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = workTargetPrediction.quantityBasisNote,
                            color = c.textMuted,
                            fontSize = 10.sp
                        )

                        workTargetPrediction.alternativeWorkWindowLabel?.let { altWin ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.success.copy(alpha = 0.13f))
                                    .padding(8.dp)
                            ) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = c.success, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "Best Available Window: $altWin",
                                    color = c.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (workTargetPrediction.targetReductionReasons.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Target Reduction Factors:",
                                    color = c.textSecondary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                workTargetPrediction.targetReductionReasons.forEach { reason ->
                                    Text("• $reason", color = c.textMuted, fontSize = 10.5.sp)
                                }
                            }
                        }
                    }

                    Text(
                        text = workTargetPrediction.estimateDisclaimer,
                        color = c.textMuted,
                        fontSize = 9.5.sp
                    )
                }
            }
        }

        // 2. Editable Project & Resource Target Inputs
        item(key = "target_inputs_card") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("work_target_inputs_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PROJECT & PRODUCTION INPUTS",
                        color = c.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                label = { Text(act.shortName, fontSize = 10.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = startChText,
                            onValueChange = { txt ->
                                startChText = txt
                                onUpdateWorkTargetInput(workTargetInput.copy(startChainageKm = txt.toDoubleOrNull()))
                            },
                            label = { Text("Start Ch (km)", fontSize = 10.sp) },
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
                            label = { Text("End Ch (km)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_end_chainage_km")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { txt ->
                                widthText = txt
                                txt.toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                                    onUpdateWorkTargetInput(workTargetInput.copy(workingWidthMeters = it))
                                }
                            },
                            label = { Text("Width (m)", fontSize = 10.sp) },
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
                            label = { Text("Thickness (mm)", fontSize = 10.sp) },
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
                            label = { Text("Shift (hrs)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = rateText,
                            onValueChange = { txt ->
                                rateText = txt
                                onUpdateWorkTargetInput(
                                    workTargetInput.copy(expectedProductionRatePerHour = txt.toDoubleOrNull())
                                )
                            },
                            label = { Text("Prod. Rate (${workTargetInput.activity.productionUnitLabel})", fontSize = 10.sp) },
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
                            label = { Text("HMP (TPH)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = paverText,
                            onValueChange = { txt ->
                                paverText = txt
                                onUpdateWorkTargetInput(workTargetInput.copy(paverCapacitySqmPerHour = txt.toDoubleOrNull()))
                            },
                            label = { Text("Paver Cap. (m²/hr)", fontSize = 10.sp) },
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
                            label = { Text("Dumpers Count", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    Text("Production Rate Source Tag:", color = c.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ValueSourceTag.entries.forEach { tag ->
                            FilterChip(
                                selected = workTargetInput.productionRateSource == tag,
                                onClick = { onUpdateWorkTargetInput(workTargetInput.copy(productionRateSource = tag)) },
                                label = { Text(tag.label, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Weather-Based Delay & Risk Prediction Card
        item(key = "delay_risk_card") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weather_delay_risk_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "WEATHER-BASED DELAY & RISK PREDICTION",
                        color = c.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        PredictionStat(
                            label = "Expected Delay",
                            value = delayRiskSummary.expectedDelayRangeLabel
                        )
                        PredictionStat(
                            label = "Confidence",
                            value = delayRiskSummary.overallConfidence.label
                        )
                    }

                    Text(
                        text = delayRiskSummary.dailyTargetImpactSummary,
                        color = c.textSecondary,
                        fontSize = 11.5.sp
                    )

                    delayRiskSummary.nextSuitableWindowLabel?.let { win ->
                        Text(
                            text = "Next Suitable Window: $win",
                            color = c.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (delayRiskSummary.warnings.isEmpty()) {
                        Text(
                            text = "No significant weather delay risks detected in the upcoming 24-hour forecast.",
                            color = c.success,
                            fontSize = 11.sp
                        )
                    } else {
                        delayRiskSummary.warnings.forEach { risk ->
                            val riskColor = when (risk.severity) {
                                WeatherAlertSeverity.SEVERE -> c.error
                                WeatherAlertSeverity.WARNING -> c.warning
                                WeatherAlertSeverity.ADVISORY -> c.accent
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(riskColor.copy(alpha = 0.13f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = riskColor, modifier = Modifier.size(16.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${risk.title} • Prob ${risk.probabilityRangeLabel}",
                                        color = c.textPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${risk.impactedWindowLabel} • Conf: ${risk.confidence.label}",
                                        color = riskColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = risk.impactSummary,
                                        color = c.textSecondary,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "targets_bottom_pad") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PredictionStat(label: String, value: String) {
    val c = CtecsTheme.colors
    Column {
        Text(label, color = c.textMuted, fontSize = 10.sp)
        Text(value, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
