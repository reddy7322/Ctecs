package com.example.ui.tools

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.QcRecordEntity
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

// ============================================================================
// REUSABLE STANDARD COMPLIANCE BANNER
// ============================================================================
@Composable
fun StandardComplianceBanner(assessment: ComplianceAssessment) {
    val (bgColor, borderColor, iconColor, statusLabel) = when (assessment.status) {
        ComplianceStatus.PASS -> Quad(Color(0xFF16A34A).copy(alpha = 0.12f), Color(0xFF16A34A).copy(alpha = 0.35f), Color(0xFF16A34A), "PASS")
        ComplianceStatus.FAIL -> Quad(Color(0xFFDC2626).copy(alpha = 0.12f), Color(0xFFDC2626).copy(alpha = 0.35f), Color(0xFFDC2626), "FAIL")
        ComplianceStatus.CONDITIONAL -> Quad(Color(0xFFD97706).copy(alpha = 0.12f), Color(0xFFD97706).copy(alpha = 0.35f), Color(0xFFD97706), "CONDITIONAL")
        ComplianceStatus.NOT_ASSESSED -> Quad(Color(0xFF64748B).copy(alpha = 0.12f), Color(0xFF64748B).copy(alpha = 0.35f), Color(0xFF64748B), "NOT ASSESSED")
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (assessment.status == ComplianceStatus.PASS) Icons.Default.CheckCircle
                        else if (assessment.status == ComplianceStatus.FAIL) Icons.Default.Cancel
                        else Icons.Default.Info,
                        contentDescription = statusLabel,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = statusLabel,
                        color = iconColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = assessment.referenceText,
                    color = ToolColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Acceptance Criteria: ${assessment.formattedCriteria}",
                color = ToolColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (assessment.deviationMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = assessment.deviationMessage,
                    color = ToolColors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ============================================================================
// 12. EARTHWORK & EMBANKMENT ENGINEERING TOOL
// ============================================================================
@Composable
fun EarthworkEmbankmentToolScreen(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var startCh by remember { mutableStateOf("120+000") }
    var endCh by remember { mutableStateOf("120+500") }
    var layerType by remember { mutableStateOf("Subgrade Top 500mm") } // or Embankment
    var topWidth by remember { mutableStateOf("12.0") }
    var bottomWidth by remember { mutableStateOf("14.0") }
    var liftThicknessMm by remember { mutableStateOf("250") } // uncompacted
    var labMdd by remember { mutableStateOf("2.05") } // g/cc
    var labOmc by remember { mutableStateOf("11.5") } // %
    var fieldWetDensity by remember { mutableStateOf("2.24") } // g/cc
    var fieldMoisture by remember { mutableStateOf("11.8") } // %
    var leadKm by remember { mutableStateOf("4.5") }

    val startM = parseChainageToMeters(startCh)
    val endM = parseChainageToMeters(endCh)
    val lengthM = abs(endM - startM)
    val topW = topWidth.toDoubleOrNull() ?: 0.0
    val botW = bottomWidth.toDoubleOrNull() ?: 0.0
    val avgW = (topW + botW) / 2.0
    val thickM = (liftThicknessMm.toDoubleOrNull() ?: 0.0) / 1000.0
    val volumeCum = lengthM * avgW * thickM

    val mdd = labMdd.toDoubleOrNull() ?: 0.0
    val wetDens = fieldWetDensity.toDoubleOrNull() ?: 0.0
    val moist = fieldMoisture.toDoubleOrNull() ?: 0.0
    val fieldDryDensity = if (moist > -100 && wetDens > 0) wetDens / (1.0 + (moist / 100.0)) else 0.0
    val compactionPct = if (mdd > 0 && fieldDryDensity > 0) (fieldDryDensity / mdd) * 100.0 else 0.0

    val clauseId = if (layerType.contains("Subgrade")) "morth_305_subgrade_compaction" else "morth_305_embankment_compaction"
    val compliance = MorthIrcStandardsRepository.assessCompaction(clauseId, if (compactionPct > 0) compactionPct else null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Earthwork & Embankment Engineering",
            subtitle = "MoRTH Section 300 lift thickness, volume & field dry density compaction",
            icon = Icons.Default.Landscape
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Subgrade Top 500mm", "Embankment Layer").forEach { type ->
                    ToolChoiceChip(
                        selected = layerType == type,
                        onClick = { layerType = type },
                        text = type,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = startCh, onValueChange = { startCh = it }, label = "From Chainage", modifier = Modifier.weight(1f), isChainage = true)
                ToolTextField(value = endCh, onValueChange = { endCh = it }, label = "To Chainage", modifier = Modifier.weight(1f), isChainage = true)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = topWidth, onValueChange = { topWidth = it }, label = "Top Width (m)", modifier = Modifier.weight(1f))
                ToolTextField(value = bottomWidth, onValueChange = { bottomWidth = it }, label = "Bottom Width (m)", modifier = Modifier.weight(1f))
                ToolTextField(value = liftThicknessMm, onValueChange = { liftThicknessMm = it }, label = "Lift (mm)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("FIELD DENSITY TEST (FDT) & COMPACTION CHECK", color = ToolColors.accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = labMdd, onValueChange = { labMdd = it }, label = "Lab MDD (g/cc)", modifier = Modifier.weight(1f))
                ToolTextField(value = labOmc, onValueChange = { labOmc = it }, label = "Lab OMC (%)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = fieldWetDensity, onValueChange = { fieldWetDensity = it }, label = "Field Wet Dens (g/cc)", modifier = Modifier.weight(1f))
                ToolTextField(value = fieldMoisture, onValueChange = { fieldMoisture = it }, label = "Field Moisture (%)", modifier = Modifier.weight(1f))
                ToolTextField(value = leadKm, onValueChange = { leadKm = it }, label = "Borrow Lead (km)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))

            StandardComplianceBanner(assessment = compliance)

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        "Earthwork: $layerType | Ch $startCh to $endCh (${lengthM}m) | Vol: ${String.format(Locale.US, "%.1f", volumeCum)} m³ | Field Dry Density: ${String.format(Locale.US, "%.3f", fieldDryDensity)} g/cc | Compaction: ${String.format(Locale.US, "%.1f%%", compactionPct)} (${compliance.status})"
                    )
                }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Compacted Volume", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(String.format(Locale.US, "%.1f m³", volumeCum), color = ToolColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Field Dry Density", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(String.format(Locale.US, "%.3f g/cc", fieldDryDensity), color = ToolColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Compaction %", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.1f%%", compactionPct),
                            color = if (compliance.status == ComplianceStatus.PASS) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val record = QcRecordEntity(
                            type = "EARTHWORK_QC",
                            title = "Earthwork $layerType Compaction",
                            projectId = "DEFAULT_PRJ",
                            dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            chainage = "$startCh - $endCh",
                            side = "Both",
                            layer = layerType,
                            detailsJson = """{"lengthM":$lengthM,"volumeCum":$volumeCum,"fieldDryDensity":$fieldDryDensity,"mdd":$mdd,"compactionPct":$compactionPct,"status":"${compliance.status}","standard":"${compliance.referenceText}"}""",
                            status = if (compliance.status == ComplianceStatus.PASS) "Pass" else "Fail",
                            remarks = "Lead $leadKm km. Moisture $fieldMoisture% against OMC $labOmc%."
                        )
                        viewModel.saveQcRecord(record)
                        Toast.makeText(context, "Earthwork QC Record Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Earthwork QC Record", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 13. SOIL & SUBGRADE LABORATORY TESTS
// ============================================================================
@Composable
fun SoilSubgradeLabToolScreen(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var liquidLimit by remember { mutableStateOf("32.5") }
    var plasticLimit by remember { mutableStateOf("18.0") }
    var soakedCbr by remember { mutableStateOf("8.5") }
    var soilSampleId by remember { mutableStateOf("SUB-CH-122+500") }

    val ll = liquidLimit.toDoubleOrNull() ?: 0.0
    val pl = plasticLimit.toDoubleOrNull() ?: 0.0
    val pi = if (ll > 0 && pl > 0) max(0.0, ll - pl) else 0.0
    val cbr = soakedCbr.toDoubleOrNull() ?: 0.0

    // A-line equation for plasticity chart: Ip = 0.73 * (LL - 20)
    val aLinePi = 0.73 * (ll - 20.0)
    val soilClass = if (ll == 0.0) "Not Entered"
    else if (ll < 35.0) {
        if (pi > aLinePi && pi > 7.0) "CL (Clay of Low Plasticity)"
        else if (pi < aLinePi || pi < 4.0) "ML (Silt of Low Plasticity)"
        else "CL-ML (Silty Clay)"
    } else if (ll <= 50.0) {
        if (pi > aLinePi) "CI (Clay of Medium Plasticity)" else "MI (Silt of Medium Plasticity)"
    } else {
        if (pi > aLinePi) "CH (Clay of High Plasticity)" else "MH (Silt of High Plasticity)"
    }

    val clause = MorthIrcStandardsRepository.getClauseById("morth_305_subgrade_cbr") ?: MorthIrcStandardsRepository.allClauses.first()
    val cbrAssessment = ComplianceAssessment(
        status = if (cbr >= 8.0) ComplianceStatus.PASS else if (cbr > 0.0) ComplianceStatus.FAIL else ComplianceStatus.NOT_ASSESSED,
        measuredValue = if (cbr > 0) cbr else null,
        standardClause = clause,
        formattedCriteria = "≥ 8.0% soaked CBR (IRC:37-2018)",
        deviationMessage = if (cbr >= 8.0) "Complies with design requirement" else if (cbr > 0) "Subgrade CBR below 8.0% design requirement" else "Enter CBR"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Soil & Subgrade Laboratory Tests",
            subtitle = "IS:2720 Atterberg Limits (LL, PL, PI), Soil Classification & Soaked CBR",
            icon = Icons.Default.Science
        ) {
            ToolTextField(value = soilSampleId, onValueChange = { soilSampleId = it }, label = "Sample ID / Chainage")
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = liquidLimit, onValueChange = { liquidLimit = it }, label = "Liquid Limit LL (%)", modifier = Modifier.weight(1f))
                ToolTextField(value = plasticLimit, onValueChange = { plasticLimit = it }, label = "Plastic Limit PL (%)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(value = soakedCbr, onValueChange = { soakedCbr = it }, label = "4-Day Soaked CBR (%)")
            Spacer(modifier = Modifier.height(10.dp))

            StandardComplianceBanner(assessment = cbrAssessment)

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        "Soil Test ($soilSampleId): LL=$ll%, PL=$pl%, PI=${String.format(Locale.US, "%.1f%%", pi)} | Class: $soilClass | Soaked CBR: $cbr% (${cbrAssessment.status})"
                    )
                }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Plasticity Index (PI)", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(String.format(Locale.US, "%.1f%%", pi), color = ToolColors.accentBlue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("IS Soil Class", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(soilClass, color = ToolColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text("Soaked CBR", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.1f%%", cbr),
                            color = if (cbrAssessment.status == ComplianceStatus.PASS) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val record = QcRecordEntity(
                            type = "SOIL_LAB_TEST",
                            title = "Soil Atterberg & CBR Test ($soilSampleId)",
                            projectId = "DEFAULT_PRJ",
                            dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            chainage = soilSampleId,
                            side = "Subgrade",
                            layer = "Subgrade",
                            detailsJson = """{"sampleId":"$soilSampleId","LL":$ll,"PL":$pl,"PI":$pi,"classification":"$soilClass","soakedCBR":$cbr,"status":"${cbrAssessment.status}"}""",
                            status = if (cbrAssessment.status == ComplianceStatus.PASS) "Pass" else "Fail",
                            remarks = "LL=$ll, PL=$pl, PI=$pi. Class: $soilClass. Soaked CBR: $cbr%."
                        )
                        viewModel.saveQcRecord(record)
                        Toast.makeText(context, "Soil Lab Test Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Soil Test Record", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 14. AGGREGATE & QUARRY QUALITY TESTS
// ============================================================================
@Composable
fun AggregateQuarryToolScreen(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var aggregateLayer by remember { mutableStateOf("DBM / BC Wearing Course") } // or WMM
    var totalMassG by remember { mutableStateOf("2500") }
    var flakyMassG by remember { mutableStateOf("320") }
    var elongatedMassG by remember { mutableStateOf("310") }
    var aivSampleM1 by remember { mutableStateOf("400") }
    var aivPassingM2 by remember { mutableStateOf("88") }
    var waterAbsorptionPct by remember { mutableStateOf("1.2") }
    var strippingRetainedPct by remember { mutableStateOf("96") }

    val totM = totalMassG.toDoubleOrNull() ?: 0.0
    val flakM = flakyMassG.toDoubleOrNull() ?: 0.0
    val elonM = elongatedMassG.toDoubleOrNull() ?: 0.0
    val combinedFlakElonPct = if (totM > 0) ((flakM + elonM) / totM) * 100.0 else 0.0

    val m1 = aivSampleM1.toDoubleOrNull() ?: 0.0
    val m2 = aivPassingM2.toDoubleOrNull() ?: 0.0
    val aivPct = if (m1 > 0) (m2 / m1) * 100.0 else 0.0
    val wa = waterAbsorptionPct.toDoubleOrNull() ?: 0.0
    val strip = strippingRetainedPct.toDoubleOrNull() ?: 0.0

    val maxFlakAllowed = 35.0 // MoRTH Table 500-8 & 400-12
    val maxAivAllowed = if (aggregateLayer.contains("BC")) 24.0 else if (aggregateLayer.contains("DBM")) 27.0 else 30.0
    val isFlakPass = combinedFlakElonPct <= maxFlakAllowed && combinedFlakElonPct > 0
    val isAivPass = aivPct <= maxAivAllowed && aivPct > 0
    val isWaPass = wa <= 2.0 && wa > 0
    val isStripPass = strip >= 95.0

    val overallPass = isFlakPass && isAivPass && isWaPass && isStripPass

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Aggregate & Quarry Quality Tests",
            subtitle = "IS:2386 Flakiness, Elongation, Aggregate Impact Value (AIV), Water Absorption & Stripping",
            icon = Icons.Default.Category
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DBM / BC Wearing Course", "WMM Base Course").forEach { lyr ->
                    ToolChoiceChip(
                        selected = aggregateLayer == lyr,
                        onClick = { aggregateLayer = lyr },
                        text = lyr,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("FLAKINESS & ELONGATION (IS:2386 Part 1)", color = ToolColors.accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = totalMassG, onValueChange = { totalMassG = it }, label = "Total Mass (g)", modifier = Modifier.weight(1f))
                ToolTextField(value = flakyMassG, onValueChange = { flakyMassG = it }, label = "Flaky Mass (g)", modifier = Modifier.weight(1f))
                ToolTextField(value = elongatedMassG, onValueChange = { elongatedMassG = it }, label = "Elongated (g)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("AGGREGATE IMPACT VALUE & STRIPPING (IS:2386 Part 4 / IS:6241)", color = ToolColors.accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = aivSampleM1, onValueChange = { aivSampleM1 = it }, label = "AIV Mass M1 (g)", modifier = Modifier.weight(1f))
                ToolTextField(value = aivPassingM2, onValueChange = { aivPassingM2 = it }, label = "Passing 2.36mm M2", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = waterAbsorptionPct, onValueChange = { waterAbsorptionPct = it }, label = "Water Absorp (%)", modifier = Modifier.weight(1f))
                ToolTextField(value = strippingRetainedPct, onValueChange = { strippingRetainedPct = it }, label = "Coating Retained (%)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (overallPass) Color(0xFF16A34A).copy(alpha = 0.12f) else Color(0xFFDC2626).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (overallPass) Color(0xFF16A34A).copy(alpha = 0.35f) else Color(0xFFDC2626).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (overallPass) "ALL AGGREGATE TESTS PASS SPECIFICATION" else "ONE OR MORE TESTS FAIL SPECIFICATION",
                        color = if (overallPass) Color(0xFF16A34A) else Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "MoRTH Table 500-8: Combined Flakiness+Elongation ≤ $maxFlakAllowed% | AIV ≤ $maxAivAllowed% | Water Absorp ≤ 2.0% | Retained Coating ≥ 95%",
                        color = ToolColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        "Aggregate Test ($aggregateLayer): Combined Flak+Elon: ${String.format(Locale.US, "%.1f%%", combinedFlakElonPct)} | AIV: ${String.format(Locale.US, "%.1f%%", aivPct)} | WA: $wa% | Stripping: $strip% | Status: ${if (overallPass) "PASS" else "FAIL"}"
                    )
                }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Flakiness+Elon", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.1f%%", combinedFlakElonPct),
                            color = if (isFlakPass) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("AIV %", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.1f%%", aivPct),
                            color = if (isAivPass) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Water Absorp", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            "$wa%",
                            color = if (isWaPass) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val record = QcRecordEntity(
                            type = "AGGREGATE_QC",
                            title = "Aggregate Quality Test ($aggregateLayer)",
                            projectId = "DEFAULT_PRJ",
                            dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            chainage = "Quarry Stockpile",
                            side = "Stockpile",
                            layer = aggregateLayer,
                            detailsJson = """{"layer":"$aggregateLayer","combinedFlakinessPct":$combinedFlakElonPct,"aivPct":$aivPct,"waterAbsorptionPct":$wa,"retainedCoatingPct":$strip,"overallPass":$overallPass}""",
                            status = if (overallPass) "Pass" else "Fail",
                            remarks = "Combined Flakiness: ${String.format(Locale.US, "%.1f%%", combinedFlakElonPct)}, AIV: ${String.format(Locale.US, "%.1f%%", aivPct)}, WA: $wa%."
                        )
                        viewModel.saveQcRecord(record)
                        Toast.makeText(context, "Aggregate Test Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Aggregate Test Record", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 15. BITUMEN & BINDER TESTING TOOL
// ============================================================================
@Composable
fun BitumenBinderTestingToolScreen(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var binderGrade by remember { mutableStateOf("VG-30") } // VG-30, VG-40, CRMB-60, PMB-40
    var penetrationVal by remember { mutableStateOf("55") } // 1/10 mm
    var softeningPointC by remember { mutableStateOf("49.5") } // °C
    var ductilityCm by remember { mutableStateOf("75") } // cm
    var viscosityPoises by remember { mutableStateOf("2600") } // at 60°C
    var flashPointC by remember { mutableStateOf("245") } // °C
    var elasticRecoveryPct by remember { mutableStateOf("62") } // % for modified

    val pen = penetrationVal.toDoubleOrNull() ?: 0.0
    val sp = softeningPointC.toDoubleOrNull() ?: 0.0
    val duct = ductilityCm.toDoubleOrNull() ?: 0.0
    val visc = viscosityPoises.toDoubleOrNull() ?: 0.0
    val flash = flashPointC.toDoubleOrNull() ?: 0.0

    // IS:73-2013 Table 1 criteria:
    // VG-30: Pen min 45 (typically 50-70), Softening Point min 47°C, Viscosity at 60°C: 2400-3600 Poises, Ductility min 40 cm, Flash min 220°C
    // VG-40: Pen min 35 (typically 40-60), Softening Point min 50°C, Viscosity at 60°C: 3200-4800 Poises, Ductility min 25 cm, Flash min 220°C
    val isPenPass = if (binderGrade == "VG-30") pen in 45.0..70.0 else pen in 35.0..60.0
    val isSpPass = if (binderGrade == "VG-30") sp >= 47.0 else sp >= 50.0
    val isDuctPass = if (binderGrade == "VG-30") duct >= 40.0 else duct >= 25.0
    val isFlashPass = flash >= 220.0
    val overallPass = isPenPass && isSpPass && isDuctPass && isFlashPass

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Bitumen & Binder Testing (IS:73)",
            subtitle = "Penetration, Softening Point, Ductility, Viscosity & Flash Point",
            icon = Icons.Default.Opacity
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("VG-30", "VG-40", "CRMB-60", "PMB-40").forEach { grade ->
                    ToolChoiceChip(
                        selected = binderGrade == grade,
                        onClick = { binderGrade = grade },
                        text = grade,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = penetrationVal, onValueChange = { penetrationVal = it }, label = "Penetration (1/10 mm)", modifier = Modifier.weight(1f))
                ToolTextField(value = softeningPointC, onValueChange = { softeningPointC = it }, label = "Softening Pt (°C)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = ductilityCm, onValueChange = { ductilityCm = it }, label = "Ductility at 25°C (cm)", modifier = Modifier.weight(1f))
                ToolTextField(value = viscosityPoises, onValueChange = { viscosityPoises = it }, label = "Viscosity at 60°C (P)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = flashPointC, onValueChange = { flashPointC = it }, label = "Flash Point (°C)", modifier = Modifier.weight(1f))
                ToolTextField(value = elasticRecoveryPct, onValueChange = { elasticRecoveryPct = it }, label = "Elastic Recovery (%)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (overallPass) Color(0xFF16A34A).copy(alpha = 0.12f) else Color(0xFFDC2626).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (overallPass) Color(0xFF16A34A).copy(alpha = 0.35f) else Color(0xFFDC2626).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (overallPass) "PASSES IS:73 TABLE 1 SPECIFICATIONS" else "DOES NOT COMPLY WITH IS:73",
                        color = if (overallPass) Color(0xFF16A34A) else Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (binderGrade == "VG-30") "VG-30 Criteria: Pen 45-70 | Softening Pt ≥ 47°C | Ductility ≥ 40 cm | Flash Pt ≥ 220°C"
                        else "VG-40 Criteria: Pen 35-60 | Softening Pt ≥ 50°C | Ductility ≥ 25 cm | Flash Pt ≥ 220°C",
                        color = ToolColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        "Bitumen Binder ($binderGrade): Pen: $pen (1/10mm) | Softening Point: $sp°C | Ductility: $duct cm | Viscosity: $visc P | Flash: $flash°C | Status: ${if (overallPass) "PASS" else "FAIL"}"
                    )
                }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Penetration", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            "$pen",
                            color = if (isPenPass) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Softening Pt", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            "$sp°C",
                            color = if (isSpPass) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Ductility", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            "$duct cm",
                            color = if (isDuctPass) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val record = QcRecordEntity(
                            type = "BITUMEN_QC",
                            title = "Bitumen Binder Quality ($binderGrade)",
                            projectId = "DEFAULT_PRJ",
                            dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            chainage = "Refinery / Tank No.",
                            side = "Tanker",
                            layer = binderGrade,
                            detailsJson = """{"grade":"$binderGrade","penetration":$pen,"softeningPoint":$sp,"ductility":$duct,"viscosity":$visc,"flashPoint":$flash,"status":"${if (overallPass) "Pass" else "Fail"}"}""",
                            status = if (overallPass) "Pass" else "Fail",
                            remarks = "Binder grade: $binderGrade. Pen: $pen, Softening Pt: $sp°C, Ductility: $duct cm."
                        )
                        viewModel.saveQcRecord(record)
                        Toast.makeText(context, "Bitumen Test Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Bitumen Test Record", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 16. ASPHALT MIX DESIGN & PLANT QUALITY CONTROL
// ============================================================================
@Composable
fun AsphaltPlantMixToolScreen(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var mixType by remember { mutableStateOf("Bituminous Concrete (BC)") } // or DBM
    var bulkSpecificGravityGmb by remember { mutableStateOf("2.395") } // Gmb
    var maxTheoreticalGmm by remember { mutableStateOf("2.495") } // Gmm
    var binderContentPct by remember { mutableStateOf("5.4") } // Pb %
    var aggregateGsb by remember { mutableStateOf("2.68") } // Gsb
    var stabilityKn by remember { mutableStateOf("13.2") } // kN
    var flowMm by remember { mutableStateOf("3.1") } // mm

    val gmb = bulkSpecificGravityGmb.toDoubleOrNull() ?: 0.0
    val gmm = maxTheoreticalGmm.toDoubleOrNull() ?: 0.0
    val pb = binderContentPct.toDoubleOrNull() ?: 0.0
    val gsb = aggregateGsb.toDoubleOrNull() ?: 0.0
    val stab = stabilityKn.toDoubleOrNull() ?: 0.0
    val flow = flowMm.toDoubleOrNull() ?: 0.0

    // Air voids: Va = (1 - Gmb / Gmm) * 100
    val airVoidsPct = if (gmm > 0 && gmb > 0) (1.0 - (gmb / gmm)) * 100.0 else 0.0
    // VMA = 100 - (Gmb * (100 - Pb) / Gsb)
    val ps = 100.0 - pb
    val vmaPct = if (gsb > 0 && gmb > 0) 100.0 - ((gmb * ps) / gsb) else 0.0
    // VFB = 100 * (VMA - Va) / VMA
    val vfbPct = if (vmaPct > 0) 100.0 * ((vmaPct - airVoidsPct) / vmaPct) else 0.0

    val isVaPass = airVoidsPct in 3.0..5.0 // MoRTH Table 500-11 & 500-18
    val minVma = if (mixType.contains("BC")) 14.0 else 13.0
    val isVmaPass = vmaPct >= minVma
    val isVfbPass = vfbPct in 65.0..75.0
    val isStabPass = stab >= 9.0 // min 9 kN
    val isFlowPass = flow in 2.0..4.0
    val overallPass = isVaPass && isVmaPass && isVfbPass && isStabPass && isFlowPass

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Asphalt Marshall Mix Design & Plant QC",
            subtitle = "MoRTH Table 500-11/18 Volumetrics: Air Voids (Va), VMA, VFB, Stability & Flow",
            icon = Icons.Default.Factory
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Bituminous Concrete (BC)", "Dense Bituminous Macadam (DBM)").forEach { type ->
                    ToolChoiceChip(
                        selected = mixType == type,
                        onClick = { mixType = type },
                        text = if (type.contains("BC")) "BC Wearing" else "DBM Binder",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = bulkSpecificGravityGmb, onValueChange = { bulkSpecificGravityGmb = it }, label = "Bulk Sp Gr (Gmb)", modifier = Modifier.weight(1f))
                ToolTextField(value = maxTheoreticalGmm, onValueChange = { maxTheoreticalGmm = it }, label = "Max Sp Gr (Gmm)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = binderContentPct, onValueChange = { binderContentPct = it }, label = "Binder Pb (%)", modifier = Modifier.weight(1f))
                ToolTextField(value = aggregateGsb, onValueChange = { aggregateGsb = it }, label = "Aggregate Gsb", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = stabilityKn, onValueChange = { stabilityKn = it }, label = "Stability (kN)", modifier = Modifier.weight(1f))
                ToolTextField(value = flowMm, onValueChange = { flowMm = it }, label = "Flow (mm)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (overallPass) Color(0xFF16A34A).copy(alpha = 0.12f) else Color(0xFFDC2626).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (overallPass) Color(0xFF16A34A).copy(alpha = 0.35f) else Color(0xFFDC2626).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (overallPass) "ALL MARSHALL VOLUMETRICS COMPLY WITH MORTH" else "MARSHALL PROPERTIES OUT OF SPECIFICATION",
                        color = if (overallPass) Color(0xFF16A34A) else Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "MoRTH Requirement: Air Voids 3.0-5.0% | VMA ≥ $minVma% | VFB 65-75% | Stability ≥ 9.0 kN | Flow 2-4 mm",
                        color = ToolColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        "Marshall Mix ($mixType): Air Voids: ${String.format(Locale.US, "%.2f%%", airVoidsPct)} | VMA: ${String.format(Locale.US, "%.1f%%", vmaPct)} | VFB: ${String.format(Locale.US, "%.1f%%", vfbPct)} | Stability: ${stab}kN | Flow: ${flow}mm | Status: ${if (overallPass) "PASS" else "FAIL"}"
                    )
                }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Air Voids (Va)", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.2f%%", airVoidsPct),
                            color = if (isVaPass) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("VMA %", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.1f%%", vmaPct),
                            color = if (isVmaPass) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("VFB %", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.1f%%", vfbPct),
                            color = if (isVfbPass) ToolColors.accentGreen else ToolColors.accentRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val record = QcRecordEntity(
                            type = "ASPHALT_JMF_QC",
                            title = "Marshall Plant Mix QC ($mixType)",
                            projectId = "DEFAULT_PRJ",
                            dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            chainage = "HMP Plant Batch",
                            side = "Plant",
                            layer = mixType,
                            detailsJson = """{"mixType":"$mixType","Gmb":$gmb,"Gmm":$gmm,"airVoids":$airVoidsPct,"VMA":$vmaPct,"VFB":$vfbPct,"stability":$stab,"flow":$flow,"status":"${if (overallPass) "Pass" else "Fail"}"}""",
                            status = if (overallPass) "Pass" else "Fail",
                            remarks = "Va=${String.format(Locale.US, "%.2f", airVoidsPct)}%, VMA=${String.format(Locale.US, "%.1f", vmaPct)}%, VFB=${String.format(Locale.US, "%.1f", vfbPct)}%."
                        )
                        viewModel.saveQcRecord(record)
                        Toast.makeText(context, "Asphalt Mix QC Record Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Asphalt QC Record", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 19. TACK COAT & PRIME COAT CALCULATOR AND REGISTER
// ============================================================================
@Composable
fun TackPrimeCoatToolScreen(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var coatType by remember { mutableStateOf("Tack Coat (RS-1)") } // or Prime Coat (SS-1)
    var surfaceType by remember { mutableStateOf("Bituminous Surface") } // Granular Base (WMM), Concrete
    var startCh by remember { mutableStateOf("150+000") }
    var endCh by remember { mutableStateOf("150+800") }
    var applicationWidthM by remember { mutableStateOf("7.5") }
    var approvedRateKgM2 by remember { mutableStateOf("0.25") } // MoRTH Table 500-4

    val startM = parseChainageToMeters(startCh)
    val endM = parseChainageToMeters(endCh)
    val lengthM = abs(endM - startM)
    val widthM = applicationWidthM.toDoubleOrNull() ?: 0.0
    val areaM2 = lengthM * widthM
    val rate = approvedRateKgM2.toDoubleOrNull() ?: 0.0
    val totalRequiredKg = areaM2 * rate
    val totalRequiredLitres = totalRequiredKg / 1.01 // Emulsion density approx 1.01 kg/L
    val totalDrums200kg = kotlin.math.ceil(totalRequiredKg / 200.0).toInt()

    val clauseId = if (coatType.contains("Prime")) "morth_502_prime_coat" else "morth_503_tack_coat"
    val standard = MorthIrcStandardsRepository.getClauseById(clauseId) ?: MorthIrcStandardsRepository.allClauses.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Tack Coat & Prime Coat Calculator",
            subtitle = "MoRTH Clauses 502 & 503 application rate, area & required emulsion quantity",
            icon = Icons.Default.FormatPaint
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Tack Coat (RS-1)", "Prime Coat (SS-1)").forEach { type ->
                    ToolChoiceChip(
                        selected = coatType == type,
                        onClick = {
                            coatType = type
                            approvedRateKgM2 = if (type.contains("Prime")) "0.80" else "0.25"
                        },
                        text = type,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = startCh, onValueChange = { startCh = it }, label = "From Chainage", modifier = Modifier.weight(1f), isChainage = true)
                ToolTextField(value = endCh, onValueChange = { endCh = it }, label = "To Chainage", modifier = Modifier.weight(1f), isChainage = true)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = applicationWidthM, onValueChange = { applicationWidthM = it }, label = "Width (m)", modifier = Modifier.weight(1f))
                ToolTextField(value = approvedRateKgM2, onValueChange = { approvedRateKgM2 = it }, label = "Rate (kg/m²)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF2563EB).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "STANDARD REFERENCE: ${standard.codeNumber} Cl. ${standard.clauseNumber}",
                        color = ToolColors.accentBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = standard.acceptanceCriteria,
                        color = ToolColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        "$coatType: Ch $startCh to $endCh (${lengthM}m × ${widthM}m = ${String.format(Locale.US, "%.1f", areaM2)} m²) | Rate: $rate kg/m² | Total: ${String.format(Locale.US, "%.1f", totalRequiredKg)} kg (${String.format(Locale.US, "%.1f", totalRequiredLitres)} Litres) | $totalDrums200kg Drums"
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Sprayed Area", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(String.format(Locale.US, "%.1f m²", areaM2), color = ToolColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Required Emulsion", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(String.format(Locale.US, "%.1f kg", totalRequiredKg), color = ToolColors.accentBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Volume (L)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(String.format(Locale.US, "%.1f L", totalRequiredLitres), color = ToolColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("200 kg Drum Requirement:", color = ToolColors.textSecondary, fontSize = 12.sp)
                        Text("$totalDrums200kg Drums (200kg)", color = ToolColors.accentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val record = QcRecordEntity(
                            type = "TACK_PRIME_QC",
                            title = "$coatType Verification",
                            projectId = "DEFAULT_PRJ",
                            dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            chainage = "$startCh - $endCh",
                            side = "Both",
                            layer = coatType,
                            detailsJson = """{"coatType":"$coatType","areaM2":$areaM2,"rateKgM2":$rate,"totalKg":$totalRequiredKg,"totalL":$totalRequiredLitres,"status":"Pass"}""",
                            status = "Pass",
                            remarks = "Rate $rate kg/m² applied uniformly. Area: ${String.format(Locale.US, "%.1f", areaM2)} m²."
                        )
                        viewModel.saveQcRecord(record)
                        Toast.makeText(context, "$coatType Record Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Spray Record", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 21. DRAINAGE, CULVERT & MINOR STRUCTURES
// ============================================================================
@Composable
fun DrainageCulvertToolScreen(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var structureType by remember { mutableStateOf("Trapezoidal Side Drain") } // Box Culvert, Pipe Culvert
    var lengthM by remember { mutableStateOf("250.0") }
    var bottomWidthM by remember { mutableStateOf("0.60") }
    var topWidthM by remember { mutableStateOf("1.40") }
    var depthM by remember { mutableStateOf("0.80") }
    var liningThicknessM by remember { mutableStateOf("0.10") } // 100mm lining

    val len = lengthM.toDoubleOrNull() ?: 0.0
    val bW = bottomWidthM.toDoubleOrNull() ?: 0.0
    val tW = topWidthM.toDoubleOrNull() ?: 0.0
    val d = depthM.toDoubleOrNull() ?: 0.0
    val t = liningThicknessM.toDoubleOrNull() ?: 0.0

    // Excavation cross-sectional area = (TopW + BotW) / 2 * Depth
    val excavationArea = ((tW + bW) / 2.0) * d
    val excavationVolume = excavationArea * len

    // Perimeter of lining = BotW + 2 * sqrt(((TopW - BotW)/2)^2 + Depth^2)
    val sideSlant = sqrt(((tW - bW) / 2.0).pow(2.0) + d.pow(2.0))
    val wettedPerimeter = bW + (2.0 * sideSlant)
    val concreteLiningVolume = wettedPerimeter * t * len

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Drainage, Culvert & Minor Structures",
            subtitle = "Excavation, Bed PCC & Concrete lining quantities for Highway Drains & Culverts",
            icon = Icons.Default.WaterDrop
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Trapezoidal Side Drain", "Box Culvert", "Pipe Culvert").forEach { type ->
                    ToolChoiceChip(
                        selected = structureType == type,
                        onClick = { structureType = type },
                        text = type.replace("Trapezoidal ", ""),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = lengthM, onValueChange = { lengthM = it }, label = "Total Length (m)", modifier = Modifier.weight(1f))
                ToolTextField(value = depthM, onValueChange = { depthM = it }, label = "Drain Depth (m)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = topWidthM, onValueChange = { topWidthM = it }, label = "Top Width (m)", modifier = Modifier.weight(1f))
                ToolTextField(value = bottomWidthM, onValueChange = { bottomWidthM = it }, label = "Bottom Width (m)", modifier = Modifier.weight(1f))
                ToolTextField(value = liningThicknessM, onValueChange = { liningThicknessM = it }, label = "Lining (m)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))

            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        "Drainage ($structureType): Length: ${len}m | Excavation Vol: ${String.format(Locale.US, "%.1f", excavationVolume)} m³ | Concrete Lining (M15): ${String.format(Locale.US, "%.1f", concreteLiningVolume)} m³"
                    )
                }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Excavation Volume", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(String.format(Locale.US, "%.1f m³", excavationVolume), color = ToolColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Lining Concrete", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(String.format(Locale.US, "%.1f m³", concreteLiningVolume), color = ToolColors.accentBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val record = QcRecordEntity(
                            type = "DRAINAGE_RECORD",
                            title = "$structureType Quantity Calculation",
                            projectId = "DEFAULT_PRJ",
                            dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            chainage = "Highway Drain",
                            side = "LHS/RHS",
                            layer = "Drainage",
                            detailsJson = """{"structure":"$structureType","lengthM":$len,"excavationM3":$excavationVolume,"concreteM3":$concreteLiningVolume}""",
                            status = "Pass",
                            remarks = "Length: ${len}m. Excavation: ${String.format(Locale.US, "%.1f", excavationVolume)} m³. Lining: ${String.format(Locale.US, "%.1f", concreteLiningVolume)} m³."
                        )
                        viewModel.saveQcRecord(record)
                        Toast.makeText(context, "Drainage Record Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Drainage Calculation", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 22. ROAD MARKING, SIGNAGE & SAFETY FURNITURE
// ============================================================================
@Composable
fun RoadMarkingSignageToolScreen(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var itemCategory by remember { mutableStateOf("Thermoplastic Line Marking") } // Metal Beam Crash Barrier, Road Studs
    var lineLengthM by remember { mutableStateOf("5000") } // meters
    var lineWidthM by remember { mutableStateOf("0.15") } // 150mm edge line
    var thicknessMm by remember { mutableStateOf("2.5") } // MoRTH Cl. 803

    val len = lineLengthM.toDoubleOrNull() ?: 0.0
    val wid = lineWidthM.toDoubleOrNull() ?: 0.0
    val thick = thicknessMm.toDoubleOrNull() ?: 0.0
    val markingAreaM2 = len * wid
    // Density of thermoplastic ~2.15 kg/m² per mm thickness -> for 2.5mm = ~5.375 kg/m²
    val thermoPerM2 = thick * 2.15
    val totalThermoplasticKg = markingAreaM2 * thermoPerM2
    // Drop-on glass beads min 250 g/m² = 0.25 kg/m² per MoRTH Cl. 803
    val totalGlassBeadsKg = markingAreaM2 * 0.25

    val standard = MorthIrcStandardsRepository.getClauseById("morth_803_thermoplastic_marking") ?: MorthIrcStandardsRepository.allClauses.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Road Marking, Signage & Safety Furniture",
            subtitle = "MoRTH Section 800 & IRC:35 Thermoplastic, Crash Barrier & Road Stud Estimator",
            icon = Icons.Default.Traffic
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Thermoplastic Line Marking", "W-Beam Crash Barrier", "Road Studs (Cat's Eyes)").forEach { cat ->
                    ToolChoiceChip(
                        selected = itemCategory == cat,
                        onClick = { itemCategory = cat },
                        text = if (cat.contains("Thermoplastic")) "Road Marking" else if (cat.contains("Barrier")) "Crash Barrier" else "Road Studs",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = lineLengthM, onValueChange = { lineLengthM = it }, label = "Total Length (m)", modifier = Modifier.weight(1f))
                ToolTextField(value = lineWidthM, onValueChange = { lineWidthM = it }, label = "Line Width (m)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(value = thicknessMm, onValueChange = { thicknessMm = it }, label = "Thickness (mm) - MoRTH min 2.5mm")
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF2563EB).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "STANDARD: ${standard.codeNumber} Cl. ${standard.clauseNumber}",
                        color = ToolColors.accentBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${standard.acceptanceCriteria} | Drop-on Glass Beads min 250 g/m²",
                        color = ToolColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        "Road Marking: ${len}m × ${wid}m = ${String.format(Locale.US, "%.1f", markingAreaM2)} m² | Thermoplastic Compound: ${String.format(Locale.US, "%.1f", totalThermoplasticKg)} kg | Drop-on Glass Beads: ${String.format(Locale.US, "%.1f", totalGlassBeadsKg)} kg"
                    )
                }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Marking Area", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(String.format(Locale.US, "%.1f m²", markingAreaM2), color = ToolColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Thermoplastic", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(String.format(Locale.US, "%.1f kg", totalThermoplasticKg), color = ToolColors.accentBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Glass Beads", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(String.format(Locale.US, "%.1f kg", totalGlassBeadsKg), color = ToolColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val record = QcRecordEntity(
                            type = "ROAD_FURNITURE_RECORD",
                            title = "Thermoplastic Road Marking Estimate",
                            projectId = "DEFAULT_PRJ",
                            dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            chainage = "Highway Stretch",
                            side = "Both",
                            layer = "Road Furniture",
                            detailsJson = """{"lengthM":$len,"widthM":$wid,"thicknessMm":$thick,"areaM2":$markingAreaM2,"thermoKg":$totalThermoplasticKg,"glassBeadsKg":$totalGlassBeadsKg}""",
                            status = "Pass",
                            remarks = "Area: ${String.format(Locale.US, "%.1f", markingAreaM2)} m². Thermoplastic: ${String.format(Locale.US, "%.1f", totalThermoplasticKg)} kg."
                        )
                        viewModel.saveQcRecord(record)
                        Toast.makeText(context, "Marking Estimate Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Furniture Estimate", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 26. EQUIPMENT & PRODUCTIVITY TOOLS
// ============================================================================
@Composable
fun EquipmentProductivityToolScreen(viewModel: MainViewModel, context: Context) {
    var paverSpeedMMin by remember { mutableStateOf("3.0") } // m/min
    var screedWidthM by remember { mutableStateOf("7.5") }
    var compactedThicknessM by remember { mutableStateOf("0.05") } // 50mm BC
    var mixDensityMTM3 by remember { mutableStateOf("2.42") } // MT/m³
    var workingHours by remember { mutableStateOf("8.0") }
    var tipperCapacityMT by remember { mutableStateOf("25.0") }

    val speed = paverSpeedMMin.toDoubleOrNull() ?: 0.0
    val wid = screedWidthM.toDoubleOrNull() ?: 0.0
    val thick = compactedThicknessM.toDoubleOrNull() ?: 0.0
    val dens = mixDensityMTM3.toDoubleOrNull() ?: 0.0
    val hrs = workingHours.toDoubleOrNull() ?: 0.0
    val tipperCap = tipperCapacityMT.toDoubleOrNull() ?: 0.0

    // Hourly paver laying capacity (MT/hr) = Speed (m/min) * 60 * Width * Thickness * Density
    val hourlyOutputMT = speed * 60.0 * wid * thick * dens
    val dailyOutputMT = hourlyOutputMT * hrs
    val tippersRequired = if (tipperCap > 0) ceil(dailyOutputMT / tipperCap).toInt() else 0
    val linearMetersPerDay = speed * 60.0 * hrs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Equipment & Productivity Tools",
            subtitle = "Sensor Paver, Roller output (MT/hr), Tipper trips & daily laying capacity",
            icon = Icons.Default.PrecisionManufacturing
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = paverSpeedMMin, onValueChange = { paverSpeedMMin = it }, label = "Paver Speed (m/min)", modifier = Modifier.weight(1f))
                ToolTextField(value = screedWidthM, onValueChange = { screedWidthM = it }, label = "Paving Width (m)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = compactedThicknessM, onValueChange = { compactedThicknessM = it }, label = "Thickness (m)", modifier = Modifier.weight(1f))
                ToolTextField(value = mixDensityMTM3, onValueChange = { mixDensityMTM3 = it }, label = "Density (MT/m³)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolTextField(value = workingHours, onValueChange = { workingHours = it }, label = "Active Shift (Hours)", modifier = Modifier.weight(1f))
                ToolTextField(value = tipperCapacityMT, onValueChange = { tipperCapacityMT = it }, label = "Tipper Payload (MT)", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))

            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        "Paver Productivity: Speed: ${speed}m/min | Width: ${wid}m | Hourly Output: ${String.format(Locale.US, "%.1f", hourlyOutputMT)} MT/hr | Daily (${hrs}h): ${String.format(Locale.US, "%.1f", dailyOutputMT)} MT (${String.format(Locale.US, "%.0f", linearMetersPerDay)} Rm) | Tipper Trips: $tippersRequired trips"
                    )
                }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Hourly Output", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(String.format(Locale.US, "%.1f MT/hr", hourlyOutputMT), color = ToolColors.accentBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Daily Output", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(String.format(Locale.US, "%.1f MT", dailyOutputMT), color = ToolColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Tipper Trips", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text("$tippersRequired trips", color = ToolColors.accentGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 27. ROAD SAFETY & WORK-ZONE SITE INSPECTION
// ============================================================================
@Composable
fun RoadSafetyWorkZoneToolScreen(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var projectStretch by remember { mutableStateOf("Ch 140+000 to 145+000") }
    var advanceWarningDone by remember { mutableStateOf(true) }
    var retroreflectiveCones by remember { mutableStateOf(true) }
    var nightFlashingLights by remember { mutableStateOf(true) }
    var flagmenPresent by remember { mutableStateOf(true) }
    var ppeCompliant by remember { mutableStateOf(true) }
    var hazardNotes by remember { mutableStateOf("Deep trench excavation barricaded with retroreflective sheet.") }

    val allPass = advanceWarningDone && retroreflectiveCones && nightFlashingLights && flagmenPresent && ppeCompliant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Work-Zone Safety & Inspection (IRC:SP:55)",
            subtitle = "Traffic diversion, advance warning signs, retroreflective delineators & PPE compliance",
            icon = Icons.Default.HealthAndSafety
        ) {
            ToolTextField(value = projectStretch, onValueChange = { projectStretch = it }, label = "Work Zone Stretch")
            Spacer(modifier = Modifier.height(10.dp))

            listOf(
                Pair("Advance Warning Signs at 1000m, 500m & 200m", advanceWarningDone) to { advanceWarningDone = !advanceWarningDone },
                Pair("Retroreflective Traffic Cones & Barricades", retroreflectiveCones) to { retroreflectiveCones = !retroreflectiveCones },
                Pair("Night Flashing Lights / Solar Blinkers Active", nightFlashingLights) to { nightFlashingLights = !nightFlashingLights },
                Pair("Trained Flagmen with Red/Green Flags & Whistles", flagmenPresent) to { flagmenPresent = !flagmenPresent },
                Pair("All Site Personnel Wearing High-Vis Class 3 PPE", ppeCompliant) to { ppeCompliant = !ppeCompliant }
            ).forEach { (item, toggle) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = toggle)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.first, color = ToolColors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = item.second,
                        onCheckedChange = { toggle() },
                        colors = SwitchDefaults.colors(checkedThumbColor = ToolColors.accentGreen)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(value = hazardNotes, onValueChange = { hazardNotes = it }, label = "Work Zone Observations / Hazard Register")
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (allPass) Color(0xFF16A34A).copy(alpha = 0.12f) else Color(0xFFDC2626).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (allPass) Color(0xFF16A34A).copy(alpha = 0.35f) else Color(0xFFDC2626).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (allPass) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (allPass) Color(0xFF16A34A) else Color(0xFFDC2626),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (allPass) "WORK ZONE SAFETY COMPLIANT (IRC:SP:55)" else "SAFETY DEFICIENCIES IDENTIFIED",
                            color = if (allPass) Color(0xFF16A34A) else Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (allPass) "All required warning, barricading and PPE checks verified on site" else "Rectify missing safety measures immediately prior to continuing work",
                            color = ToolColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch {
                        val record = QcRecordEntity(
                            type = "SAFETY_INSPECTION",
                            title = "Work-Zone Safety Inspection ($projectStretch)",
                            projectId = "DEFAULT_PRJ",
                            dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            chainage = projectStretch,
                            side = "Both",
                            layer = "Safety",
                            detailsJson = """{"stretch":"$projectStretch","allPass":$allPass,"notes":"$hazardNotes"}""",
                            status = if (allPass) "Pass" else "Fail",
                            remarks = hazardNotes
                        )
                        viewModel.saveQcRecord(record)
                        Toast.makeText(context, "Safety Inspection Logged!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Log Safety Audit Record", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
