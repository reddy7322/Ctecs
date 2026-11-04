package com.example.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.isAppDarkTheme
import java.util.Locale
import kotlin.math.*

// ============================================================================
// 1. CHAINAGE CALCULATOR SCREEN
// ============================================================================
@Composable
fun ChainageCalculatorScreen(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: From / To Chainage Distance Calculator
        var startCh by remember { mutableStateOf("177+000") }
        var endCh by remember { mutableStateOf("180+450") }
        val startM = parseChainageToMeters(startCh)
        val endM = parseChainageToMeters(endCh)
        val diffM = abs(endM - startM)
        val midM = (startM + endM) / 2.0

        ToolSectionCard(
            title = "Chainage Distance Calculator",
            subtitle = "Calculate exact stretch length & midpoint between two chainages",
            icon = Icons.Default.Straighten
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = startCh,
                    onValueChange = { startCh = it },
                    label = "From Chainage",
                    modifier = Modifier.weight(1f),
                    isChainage = true
                )
                ToolTextField(
                    value = endCh,
                    onValueChange = { endCh = it },
                    label = "To Chainage",
                    modifier = Modifier.weight(1f),
                    isChainage = true
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        "Chainage Distance: ${String.format(Locale.US, "%.1f m (%.3f km)", diffM, diffM / 1000.0)} | Midpoint: ${formatMetersToChainage(midM)}"
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Stretch Length", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.1f m", diffM),
                            color = ToolColors.accentBlue,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("In Kilometers", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.3f km", diffM / 1000.0),
                            color = ToolColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Midpoint CH", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            formatMetersToChainage(midM),
                            color = ToolColors.accentGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Card 2: Chainage Offset Calculator (+ Add / - Subtract)
        var baseCh by remember { mutableStateOf("177+250") }
        var offsetMeters by remember { mutableStateOf("350") }
        var isAddition by remember { mutableStateOf(true) }
        val baseM = parseChainageToMeters(baseCh)
        val offsetM = offsetMeters.toDoubleOrNull() ?: 0.0
        val resultM = if (isAddition) baseM + offsetM else (baseM - offsetM).coerceAtLeast(0.0)
        val resultChFormatted = formatMetersToChainage(resultM)

        ToolSectionCard(
            title = "Chainage Offset Calculator",
            subtitle = "Add or subtract linear distance from a reference station",
            icon = Icons.Default.AddRoad
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = baseCh,
                    onValueChange = { baseCh = it },
                    label = "Reference CH",
                    modifier = Modifier.weight(1f),
                    isChainage = true
                )
                ToolTextField(
                    value = offsetMeters,
                    onValueChange = { offsetMeters = it },
                    label = "Offset Distance (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolChoiceChip(
                    selected = isAddition,
                    label = "+ Add Forward",
                    onClick = { isAddition = true },
                    modifier = Modifier.weight(1f)
                )
                ToolChoiceChip(
                    selected = !isAddition,
                    label = "- Subtract Backward",
                    onClick = { isAddition = false },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = { copyToolResult(context, "Target Chainage: $resultChFormatted") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resulting Chainage", color = ToolColors.textSecondary, fontSize = 12.sp)
                    Text(
                        resultChFormatted,
                        color = ToolColors.accentGreen,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Card 3: Work Progress & Target Tracker
        var totalScopeKm by remember { mutableStateOf("15.5") }
        var completedKm by remember { mutableStateOf("9.8") }
        val scope = totalScopeKm.toDoubleOrNull() ?: 1.0
        val done = completedKm.toDoubleOrNull() ?: 0.0
        val pct = if (scope > 0) ((done / scope) * 100.0).coerceIn(0.0, 100.0) else 0.0
        val balanceKm = (scope - done).coerceAtLeast(0.0)

        ToolSectionCard(
            title = "Work Progress & Target Tracker",
            subtitle = "Track completed stretch vs balance highway scope",
            icon = Icons.Default.TrendingUp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = totalScopeKm,
                    onValueChange = { totalScopeKm = it },
                    label = "Total Scope (km)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = completedKm,
                    onValueChange = { completedKm = it },
                    label = "Completed (km)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            String.format(Locale.US, "Completion: %.1f%%", pct),
                            color = ToolColors.accentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            String.format(Locale.US, "Remaining: %.2f km", balanceKm),
                            color = ToolColors.accentOrange,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (pct / 100.0).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ToolColors.accentBlue,
                        trackColor = ToolColors.border
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// 2. DISTANCE & GRADIENT CALCULATOR SCREEN
// ============================================================================
@Composable
fun DistanceGradientCalculatorScreen(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Longitudinal Gradient & Slope Calculator
        var startRl by remember { mutableStateOf("245.500") }
        var endRl by remember { mutableStateOf("248.250") }
        var horizDist by remember { mutableStateOf("110") }

        val rl1 = startRl.toDoubleOrNull() ?: 0.0
        val rl2 = endRl.toDoubleOrNull() ?: 0.0
        val runM = horizDist.toDoubleOrNull() ?: 1.0
        val riseM = rl2 - rl1
        val absRise = abs(riseM)
        val gradientPct = if (runM > 0) (riseM / runM) * 100.0 else 0.0
        val slopeRatio = if (absRise > 0.0001) runM / absRise else 0.0
        val slopeDeg = if (runM > 0) Math.toDegrees(atan(absRise / runM)) else 0.0
        val inclinedLength = sqrt(runM * runM + riseM * riseM)

        ToolSectionCard(
            title = "Longitudinal Gradient & Slope Calculator",
            subtitle = "Compute longitudinal slope %, 1:N ratio, angle & inclined length",
            icon = Icons.Default.Terrain
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = startRl,
                    onValueChange = { startRl = it },
                    label = "Start RL (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = endRl,
                    onValueChange = { endRl = it },
                    label = "End RL (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(
                value = horizDist,
                onValueChange = { horizDist = it },
                label = "Horizontal Distance / Run (m)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(
                            Locale.US,
                            "Gradient: %+.2f%% (1 in %.1f) | Elev Diff: %+.3f m | Inclined Length: %.2f m",
                            gradientPct,
                            slopeRatio,
                            riseM,
                            inclinedLength
                        )
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gradient (%)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(
                                    Locale.US,
                                    "%+.2f%% (%s)",
                                    gradientPct,
                                    if (riseM >= 0) "Rise" else "Fall"
                                ),
                                color = if (abs(gradientPct) <= 5.0) ToolColors.accentGreen else ToolColors.accentOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Slope Ratio (1 : N)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                if (slopeRatio > 0) String.format(Locale.US, "1 in %.1f", slopeRatio) else "Flat",
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            String.format(Locale.US, "Vertical ΔH: %+.3f m", riseM),
                            color = ToolColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            String.format(Locale.US, "Angle: %.2f°", slopeDeg),
                            color = ToolColors.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            String.format(Locale.US, "Slope Len: %.2f m", inclinedLength),
                            color = ToolColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Card 2: Interpolated Level at Intermediate Distance
        var baseElevation by remember { mutableStateOf("245.500") }
        var gradePercent by remember { mutableStateOf("2.5") }
        var targetDist by remember { mutableStateOf("60") }

        val bEl = baseElevation.toDoubleOrNull() ?: 0.0
        val gPct = gradePercent.toDoubleOrNull() ?: 0.0
        val tDist = targetDist.toDoubleOrNull() ?: 0.0
        val deltaZ = (gPct / 100.0) * tDist
        val targetRl = bEl + deltaZ

        ToolSectionCard(
            title = "Grade Level Interpolation Calculator",
            subtitle = "Find design Reduced Level (RL) at any distance along a uniform gradient",
            icon = Icons.Default.Timeline
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = baseElevation,
                    onValueChange = { baseElevation = it },
                    label = "Start RL (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = gradePercent,
                    onValueChange = { gradePercent = it },
                    label = "Gradient (+/- %)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(
                value = targetDist,
                onValueChange = { targetDist = it },
                label = "Distance from Start (m)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(Locale.US, "Design RL at %.1fm: %.3f m (ΔH: %+.3f m)", tDist, targetRl, deltaZ)
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Elevation Change (ΔH)", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%+.3f m", deltaZ),
                            color = ToolColors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Interpolated Design RL", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.3f m", targetRl),
                            color = ToolColors.accentGreen,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// 3. AREA CALCULATOR SCREEN
// ============================================================================
@Composable
fun AreaCalculatorScreen(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Carriageway / Pavement Area Calculator
        var areaLen by remember { mutableStateOf("250") }
        var areaWid by remember { mutableStateOf("10.5") }
        val l = areaLen.toDoubleOrNull() ?: 0.0
        val w = areaWid.toDoubleOrNull() ?: 0.0
        val sqm = l * w
        val sqFt = sqm * 10.7639
        val sqYd = sqm * 1.19599
        val hectares = sqm / 10000.0

        ToolSectionCard(
            title = "Carriageway & Paving Area Calculator",
            subtitle = "Compute surface area in m², sq.yd, sq.ft & hectares",
            icon = Icons.Default.SquareFoot
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = areaLen,
                    onValueChange = { areaLen = it },
                    label = "Stretch Length (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = areaWid,
                    onValueChange = { areaWid = it },
                    label = "Paving Width (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(Locale.US, "Area: %.2f m² | %.2f sq.yd | %.4f ha", sqm, sqYd, hectares)
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Area (m²)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.2f m²", sqm),
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Hectares (ha)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.4f ha", hectares),
                                color = ToolColors.accentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            String.format(Locale.US, "Sq. Yards: %,.2f sq.yd", sqYd),
                            color = ToolColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            String.format(Locale.US, "Sq. Feet: %,.1f sq.ft", sqFt),
                            color = ToolColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Card 2: Trapezoidal / Variable Width Area Calculator
        var widthStart by remember { mutableStateOf("7.0") }
        var widthEnd by remember { mutableStateOf("10.5") }
        var trapLength by remember { mutableStateOf("120") }
        val w1 = widthStart.toDoubleOrNull() ?: 0.0
        val w2 = widthEnd.toDoubleOrNull() ?: 0.0
        val tl = trapLength.toDoubleOrNull() ?: 0.0
        val meanWidth = (w1 + w2) / 2.0
        val trapArea = meanWidth * tl

        ToolSectionCard(
            title = "Transition / Tapered Width Area Calculator",
            subtitle = "Calculate area for flaring, toll plaza tapers & extra widening",
            icon = Icons.Default.CropFree
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = widthStart,
                    onValueChange = { widthStart = it },
                    label = "Start Width W1 (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = widthEnd,
                    onValueChange = { widthEnd = it },
                    label = "End Width W2 (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(
                value = trapLength,
                onValueChange = { trapLength = it },
                label = "Transition Length (m)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(Locale.US, "Taper Area: %.2f m² (Mean Width: %.2f m)", trapArea, meanWidth)
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mean Width", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.2f m", meanWidth),
                            color = ToolColors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Total Surface Area", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%,.2f m²", trapArea),
                            color = ToolColors.accentGreen,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// 4. VOLUME CALCULATOR SCREEN
// ============================================================================
@Composable
fun VolumeCalculatorScreen(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Rectangular Layer Volume Calculator
        var volLen by remember { mutableStateOf("200") }
        var volWid by remember { mutableStateOf("10.5") }
        var volThickMm by remember { mutableStateOf("200") }

        val vl = volLen.toDoubleOrNull() ?: 0.0
        val vw = volWid.toDoubleOrNull() ?: 0.0
        val vt = (volThickMm.toDoubleOrNull() ?: 0.0) / 1000.0
        val cum = vl * vw * vt
        val looseCum = cum * 1.25
        val cft = cum * 35.3147

        ToolSectionCard(
            title = "Pavement Layer Volume Calculator",
            subtitle = "Compacted & loose volume for GSB, WMM, DLC, PQC & structural layers",
            icon = Icons.Default.ViewInAr
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = volLen,
                    onValueChange = { volLen = it },
                    label = "Length (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = volWid,
                    onValueChange = { volWid = it },
                    label = "Width (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(
                value = volThickMm,
                onValueChange = { volThickMm = it },
                label = "Compacted Thickness (mm)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(Locale.US, "Compacted Vol: %.2f m³ | Loose Vol: %.2f m³ | %.1f cft", cum, looseCum, cft)
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Compacted Volume", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.2f m³", cum),
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Loose Volume (+25%)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.2f m³", looseCum),
                                color = ToolColors.accentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Text(
                        String.format(Locale.US, "Cubic Feet: %,.1f cft", cft),
                        color = ToolColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Card 2: Embankment / Trapezoidal Earthwork Volume Calculator
        var topWidth by remember { mutableStateOf("12.0") }
        var avgHeight by remember { mutableStateOf("2.5") }
        var sideSlope by remember { mutableStateOf("2.0") }
        var embLength by remember { mutableStateOf("100") }

        val b = topWidth.toDoubleOrNull() ?: 0.0
        val h = avgHeight.toDoubleOrNull() ?: 0.0
        val s = sideSlope.toDoubleOrNull() ?: 0.0
        val el = embLength.toDoubleOrNull() ?: 0.0
        val baseWidth = b + 2.0 * s * h
        val crossSectionArea = (b + s * h) * h
        val earthworkVol = crossSectionArea * el

        ToolSectionCard(
            title = "Embankment & Trench Trapezoidal Volume",
            subtitle = "Compute cut/fill earthwork volume with side slope (H:V = S:1)",
            icon = Icons.Default.Landscape
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = topWidth,
                    onValueChange = { topWidth = it },
                    label = "Formation Width B (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = avgHeight,
                    onValueChange = { avgHeight = it },
                    label = "Avg Height / Depth H (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = sideSlope,
                    onValueChange = { sideSlope = it },
                    label = "Side Slope S (S:1)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = embLength,
                    onValueChange = { embLength = it },
                    label = "Stretch Length L (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(
                            Locale.US,
                            "Earthwork Volume: %.2f m³ | Section Area: %.2f m² | Base Width: %.2f m",
                            earthworkVol,
                            crossSectionArea,
                            baseWidth
                        )
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cross-Section Area", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.2f m²", crossSectionArea),
                                color = ToolColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Total Earthwork Volume", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.2f m³", earthworkVol),
                                color = ToolColors.accentGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Text(
                        String.format(Locale.US, "Toe-to-Toe Base Width: %.2f m", baseWidth),
                        color = ToolColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// 5. LEVEL CALCULATOR SCREEN (AUTO LEVEL HI / RL / DIP CHECK)
// ============================================================================
@Composable
fun LevelCalculatorScreen(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Auto-Level Height of Instrument (HI) & Reduced Level (RL)
        var bmRl by remember { mutableStateOf("100.000") }
        var backsight by remember { mutableStateOf("1.455") }
        var foresight by remember { mutableStateOf("1.120") }
        var designRl by remember { mutableStateOf("100.350") }

        val bm = bmRl.toDoubleOrNull() ?: 0.0
        val bs = backsight.toDoubleOrNull() ?: 0.0
        val fs = foresight.toDoubleOrNull() ?: 0.0
        val desRl = designRl.toDoubleOrNull() ?: 0.0

        val hi = bm + bs
        val actualRl = hi - fs
        val riseOrFall = bs - fs
        val cutOrFill = actualRl - desRl // +ve means high (Cut), -ve means low (Fill)
        val requiredStaffReading = hi - desRl

        ToolSectionCard(
            title = "Auto-Level HI & Reduced Level (RL) Calculator",
            subtitle = "Compute Height of Instrument, Actual RL, Cut/Fill & Target Staff Reading",
            icon = Icons.Default.Speed
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = bmRl,
                    onValueChange = { bmRl = it },
                    label = "Benchmark TBM RL (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = backsight,
                    onValueChange = { backsight = it },
                    label = "Backsight BS (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = foresight,
                    onValueChange = { foresight = it },
                    label = "IS / Foresight FS (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = designRl,
                    onValueChange = { designRl = it },
                    label = "Design FRL / Top RL (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(
                            Locale.US,
                            "HI: %.3f m | Actual RL: %.3f m | Required Staff for Design RL: %.3f m | Diff: %+.3f m",
                            hi,
                            actualRl,
                            requiredStaffReading,
                            cutOrFill
                        )
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Height of Instrument (HI)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.3f m", hi),
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Observed Actual RL", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.3f m", actualRl),
                                color = ToolColors.accentGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Target Staff for Design RL", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.3f m", requiredStaffReading),
                                color = ToolColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Level Check (Actual - Design)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            val statusLabel = when {
                                abs(cutOrFill) < 0.005 -> "EXACT LEVEL (0 mm)"
                                cutOrFill > 0 -> String.format(Locale.US, "HIGH / CUT %+.0f mm", cutOrFill * 1000)
                                else -> String.format(Locale.US, "LOW / FILL %.0f mm", cutOrFill * 1000)
                            }
                            Text(
                                statusLabel,
                                color = if (abs(cutOrFill) <= 0.010) ToolColors.accentGreen else ToolColors.accentOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Text(
                        String.format(
                            Locale.US,
                            "Rise / Fall from TBM: %+.3f m (%s)",
                            riseOrFall,
                            if (riseOrFall >= 0) "Rise" else "Fall"
                        ),
                        color = ToolColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// 6. CURVE CALCULATOR SCREEN (HORIZONTAL CURVE & SUPERELEVATION)
// ============================================================================
@Composable
fun CurveCalculatorScreen(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var radiusR by remember { mutableStateOf("360") }
        var deflectionDeg by remember { mutableStateOf("42.5") }
        var designSpeedKmph by remember { mutableStateOf("80") }
        var carriagewayWidth by remember { mutableStateOf("7.0") }

        val r = radiusR.toDoubleOrNull() ?: 1.0
        val deltaDeg = deflectionDeg.toDoubleOrNull() ?: 0.0
        val deltaRad = Math.toRadians(deltaDeg)
        val v = designSpeedKmph.toDoubleOrNull() ?: 0.0
        val b = carriagewayWidth.toDoubleOrNull() ?: 7.0

        val tangentLength = if (r > 0) r * tan(deltaRad / 2.0) else 0.0
        val curveLength = if (r > 0) Math.PI * r * deltaDeg / 180.0 else 0.0
        val longChord = if (r > 0) 2.0 * r * sin(deltaRad / 2.0) else 0.0
        val externalDist = if (r > 0) r * (1.0 / cos(deltaRad / 2.0) - 1.0) else 0.0
        val midOrdinate = if (r > 0) r * (1.0 - cos(deltaRad / 2.0)) else 0.0

        // IRC Superelevation e = V^2 / (225 * R), capped at 7% (0.07) for plain/rolling terrain
        val calcE = if (r > 0) (v * v) / (225.0 * r) else 0.0
        val designE = calcE.coerceAtMost(0.07)
        val outerEdgeLiftM = designE * b

        ToolSectionCard(
            title = "Horizontal Circular Curve & Superelevation",
            subtitle = "Compute curve elements (T, L, C, E, M) & IRC superelevation",
            icon = Icons.Default.TurnRight
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = radiusR,
                    onValueChange = { radiusR = it },
                    label = "Curve Radius R (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = deflectionDeg,
                    onValueChange = { deflectionDeg = it },
                    label = "Deflection Angle Δ (°)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = designSpeedKmph,
                    onValueChange = { designSpeedKmph = it },
                    label = "Design Speed V (km/h)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = carriagewayWidth,
                    onValueChange = { carriagewayWidth = it },
                    label = "Pavement Width B (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(
                            Locale.US,
                            "Curve R=%.1fm, Δ=%.2f° -> Tangent T=%.2fm, Length L=%.2fm, Chord C=%.2fm, Apex E=%.2fm, Mid-Ord M=%.2fm, Superelevation e=%.2f%%",
                            r,
                            deltaDeg,
                            tangentLength,
                            curveLength,
                            longChord,
                            externalDist,
                            midOrdinate,
                            designE * 100.0
                        )
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Curve Length (L)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.2f m", curveLength),
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Tangent Length (T)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.2f m", tangentLength),
                                color = ToolColors.accentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            String.format(Locale.US, "Long Chord: %.2f m", longChord),
                            color = ToolColors.textPrimary,
                            fontSize = 12.sp
                        )
                        Text(
                            String.format(Locale.US, "Apex (E): %.2f m", externalDist),
                            color = ToolColors.textPrimary,
                            fontSize = 12.sp
                        )
                        Text(
                            String.format(Locale.US, "Mid-Ord (M): %.2f m", midOrdinate),
                            color = ToolColors.textPrimary,
                            fontSize = 12.sp
                        )
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("IRC Superelevation (e)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.2f%% (Max 7%%)", designE * 100.0),
                                color = ToolColors.accentOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Outer Edge Raise", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "+%.3f m (%.0f mm)", outerEdgeLiftM, outerEdgeLiftM * 1000.0),
                                color = ToolColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// 7. STEEL CALCULATOR SCREEN (REBAR WEIGHT & BBS)
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SteelCalculatorScreen(context: Context) {
    val standardDiameters = listOf(8, 10, 12, 16, 20, 25, 32)
    var selectedDia by remember { mutableStateOf(16) }
    var barLengthM by remember { mutableStateOf("12.0") }
    var numBars by remember { mutableStateOf("45") }

    val d = selectedDia.toDouble()
    val unitWeightKgM = (d * d) / 162.2
    val len = barLengthM.toDoubleOrNull() ?: 0.0
    val count = numBars.toDoubleOrNull() ?: 0.0
    val singleBarWeight = unitWeightKgM * len
    val totalWeightKg = singleBarWeight * count
    val totalWeightMt = totalWeightKg / 1000.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Reinforcement Steel Weight Calculator (BBS)",
            subtitle = "IS 1786 Rebar Weight using D² / 162.2 kg/m formula",
            icon = Icons.Default.Architecture
        ) {
            Text(
                "Select Bar Diameter (mm)",
                color = ToolColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                standardDiameters.forEach { dia ->
                    ToolChoiceChip(
                        selected = selectedDia == dia,
                        label = "${dia} mm",
                        onClick = { selectedDia = dia }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = barLengthM,
                    onValueChange = { barLengthM = it },
                    label = "Cut Length per Bar (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = numBars,
                    onValueChange = { numBars = it },
                    label = "Total No. of Bars",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(
                            Locale.US,
                            "Rebar %dmm: %.0f Nos × %.2fm = %.2f kg (%.3f MT) [Unit wt: %.3f kg/m]",
                            selectedDia,
                            count,
                            len,
                            totalWeightKg,
                            totalWeightMt,
                            unitWeightKgM
                        )
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Steel Weight (kg)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.2f kg", totalWeightKg),
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Weight in Metric Tonnes", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.3f MT", totalWeightMt),
                                color = ToolColors.accentGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            String.format(Locale.US, "Unit Wt: %.3f kg/m", unitWeightKgM),
                            color = ToolColors.textPrimary,
                            fontSize = 12.sp
                        )
                        Text(
                            String.format(Locale.US, "Wt / Bar: %.2f kg", singleBarWeight),
                            color = ToolColors.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            String.format(Locale.US, "Total Len: %.1f m", len * count),
                            color = ToolColors.textPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// 8. CONCRETE CALCULATOR SCREEN (PQC / STRUCTURAL CONCRETE)
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConcreteCalculatorScreen(context: Context) {
    val grades = listOf(
        "M15 (1:2:4)" to Triple(1.0, 2.0, 4.0),
        "M20 (1:1.5:3)" to Triple(1.0, 1.5, 3.0),
        "M25 (1:1:2)" to Triple(1.0, 1.0, 2.0),
        "M30 Design" to Triple(1.0, 1.2, 2.4),
        "M40 PQC" to Triple(1.0, 1.1, 2.2)
    )
    var selectedGradeIdx by remember { mutableStateOf(4) } // Default M40 PQC
    var concLen by remember { mutableStateOf("100") }
    var concWid by remember { mutableStateOf("9.0") }
    var concThickM by remember { mutableStateOf("0.30") }

    val l = concLen.toDoubleOrNull() ?: 0.0
    val w = concWid.toDoubleOrNull() ?: 0.0
    val t = concThickM.toDoubleOrNull() ?: 0.0
    val wetVol = l * w * t
    val dryVol = wetVol * 1.54

    val ratio = grades[selectedGradeIdx].second
    val sumRatio = ratio.first + ratio.second + ratio.third
    val cementVol = (ratio.first / sumRatio) * dryVol
    val cementBags = cementVol / 0.0347 // 1 bag (50kg) = 0.0347 m³
    val sandCum = (ratio.second / sumRatio) * dryVol
    val aggregateCum = (ratio.third / sumRatio) * dryVol
    val waterLiters = cementBags * 50.0 * 0.42 // W/C ratio ~0.42

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Concrete & PQC Mix Material Calculator",
            subtitle = "Compute Cement Bags, Fine Sand, Coarse Aggregate & Water",
            icon = Icons.Default.Foundation
        ) {
            Text(
                "Concrete Grade",
                color = ToolColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grades.forEachIndexed { idx, pair ->
                    ToolChoiceChip(
                        selected = selectedGradeIdx == idx,
                        label = pair.first,
                        onClick = { selectedGradeIdx = idx }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = concLen,
                    onValueChange = { concLen = it },
                    label = "Length (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = concWid,
                    onValueChange = { concWid = it },
                    label = "Width (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(
                value = concThickM,
                onValueChange = { concThickM = it },
                label = "Thickness / Depth (m)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(
                            Locale.US,
                            "Concrete (%s) Wet: %.2f m³ (Dry: %.2f m³) -> Cement: %.0f Bags (%.1f MT), Sand: %.2f m³, Aggregate: %.2f m³, Water: %.0f L",
                            grades[selectedGradeIdx].first,
                            wetVol,
                            dryVol,
                            cementBags,
                            (cementBags * 50.0) / 1000.0,
                            sandCum,
                            aggregateCum,
                            waterLiters
                        )
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wet Concrete Volume", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.2f m³", wetVol),
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Cement Required (50kg)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.0f Bags (%.1f MT)", cementBags, (cementBags * 50.0) / 1000.0),
                                color = ToolColors.accentGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fine Sand", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.2f m³", sandCum),
                                color = ToolColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Coarse Aggregate", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.2f m³", aggregateCum),
                                color = ToolColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Water (W/C 0.42)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.0f L", waterLiters),
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// 9. JOINT CALCULATOR SCREEN (PQC JOINTS, DOWEL BARS & TIE BARS)
// ============================================================================
@Composable
fun JointCalculatorScreen(context: Context) {
    var stretchLengthM by remember { mutableStateOf("500") }
    var panelSpacingM by remember { mutableStateOf("4.5") }
    var pqcWidthM by remember { mutableStateOf("9.0") }
    var dowelSpacingMm by remember { mutableStateOf("300") }

    val stretch = stretchLengthM.toDoubleOrNull() ?: 0.0
    val spacing = (panelSpacingM.toDoubleOrNull() ?: 4.5).coerceAtLeast(0.5)
    val width = pqcWidthM.toDoubleOrNull() ?: 0.0
    val dowelSpM = ((dowelSpacingMm.toDoubleOrNull() ?: 300.0) / 1000.0).coerceAtLeast(0.1)

    val numTransverseJoints = floor(stretch / spacing).toInt()
    val dowelsPerJoint = floor(width / dowelSpM).toInt() + 1
    val totalDowelBars = numTransverseJoints * dowelsPerJoint
    // Typical IRC 58 PQC Dowel: 32mm dia, 500mm length (unit wt = 6.313 kg/m -> 3.156 kg/bar)
    val totalDowelWeightKg = totalDowelBars * 0.5 * (32.0 * 32.0 / 162.2)

    // Longitudinal joint tie bars: typically 12mm dia, 640mm length @ 600mm c/c
    val totalTieBars = floor(stretch / 0.60).toInt()
    val totalTieWeightKg = totalTieBars * 0.64 * (12.0 * 12.0 / 162.2)

    val totalJointRunningM = (numTransverseJoints * width) + stretch

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "PQC Pavement Joint, Dowel & Tie Bar Calculator",
            subtitle = "IRC:58 Contraction Joints, Dowel Bars (32mm) & Longitudinal Tie Bars (12mm)",
            icon = Icons.Default.GridOn
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = stretchLengthM,
                    onValueChange = { stretchLengthM = it },
                    label = "PQC Stretch Length (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = panelSpacingM,
                    onValueChange = { panelSpacingM = it },
                    label = "Joint Spacing (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = pqcWidthM,
                    onValueChange = { pqcWidthM = it },
                    label = "Carriageway Width (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = dowelSpacingMm,
                    onValueChange = { dowelSpacingMm = it },
                    label = "Dowel Spacing (mm)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(
                            Locale.US,
                            "PQC Joints: %d Nos | Dowel Bars: %d Nos (%.1f kg) | Tie Bars: %d Nos (%.1f kg) | Joint Groove Length: %.1f RM",
                            numTransverseJoints,
                            totalDowelBars,
                            totalDowelWeightKg,
                            totalTieBars,
                            totalTieWeightKg,
                            totalJointRunningM
                        )
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Transverse Joints", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                "$numTransverseJoints Panels",
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Total Dowel Bars (32mm)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,d Nos (%.1f kg)", totalDowelBars, totalDowelWeightKg),
                                color = ToolColors.accentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tie Bars (12mm @ 600c/c)", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,d Nos (%.1f kg)", totalTieBars, totalTieWeightKg),
                                color = ToolColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Total Joint Cut Length", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.1f RM", totalJointRunningM),
                                color = ToolColors.accentOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// 10. UNIT CONVERTER SCREEN (DEDICATED & FIXED FLOWROW CHIPS)
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitConverterScreen(context: Context) {
    val categories = listOf("Length", "Area", "Volume", "Mass")
    var selectedCategory by remember { mutableStateOf("Length") }
    var inputVal by remember { mutableStateOf("100") }

    val unitsByCategory = mapOf(
        "Length" to listOf("Meters", "Kilometers", "Feet", "Inches", "Millimeters"),
        "Area" to listOf("Sq. Meters", "Hectares", "Sq. Yards", "Sq. Feet", "Acres"),
        "Volume" to listOf("Cubic Meters", "Cubic Feet", "Liters", "Brass"),
        "Mass" to listOf("Metric Tonnes", "Kilograms", "Quintals", "Pounds")
    )

    val currentUnits = unitsByCategory[selectedCategory] ?: emptyList()
    var selectedUnit by remember(selectedCategory) { mutableStateOf(currentUnits.first()) }
    val numVal = inputVal.toDoubleOrNull() ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolSectionCard(
            title = "Civil & Highway Unit Converter",
            subtitle = "Convert length, area, volume and mass instantly",
            icon = Icons.Default.Calculate
        ) {
            // Category Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    ToolChoiceChip(
                        selected = selectedCategory == cat,
                        label = cat,
                        onClick = { selectedCategory = cat },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ToolTextField(
                value = inputVal,
                onValueChange = { inputVal = it },
                label = "Input Value ($selectedUnit)",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Source Unit",
                color = ToolColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Wrapping FlowRow prevents any chip from squeezing vertically on narrow screens!
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                currentUnits.forEach { unit ->
                    ToolChoiceChip(
                        selected = selectedUnit == unit,
                        label = unit,
                        onClick = { selectedUnit = unit }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val results: List<Pair<String, String>> = when (selectedCategory) {
                "Length" -> {
                    val meters = when (selectedUnit) {
                        "Meters" -> numVal
                        "Kilometers" -> numVal * 1000.0
                        "Feet" -> numVal * 0.3048
                        "Inches" -> numVal * 0.0254
                        "Millimeters" -> numVal / 1000.0
                        else -> numVal
                    }
                    listOf(
                        "Meters (m)" to String.format(Locale.US, "%,.3f m", meters),
                        "Kilometers (km)" to String.format(Locale.US, "%,.4f km", meters / 1000.0),
                        "Feet (ft)" to String.format(Locale.US, "%,.2f ft", meters * 3.28084),
                        "Inches (in)" to String.format(Locale.US, "%,.1f in", meters * 39.3701),
                        "Millimeters (mm)" to String.format(Locale.US, "%,.0f mm", meters * 1000.0)
                    )
                }
                "Area" -> {
                    val sqm = when (selectedUnit) {
                        "Sq. Meters" -> numVal
                        "Hectares" -> numVal * 10000.0
                        "Sq. Yards" -> numVal / 1.19599
                        "Sq. Feet" -> numVal / 10.7639
                        "Acres" -> numVal * 4046.86
                        else -> numVal
                    }
                    listOf(
                        "Sq. Meters (m²)" to String.format(Locale.US, "%,.2f m²", sqm),
                        "Hectares (ha)" to String.format(Locale.US, "%,.4f ha", sqm / 10000.0),
                        "Sq. Yards (sq.yd)" to String.format(Locale.US, "%,.2f sq.yd", sqm * 1.19599),
                        "Sq. Feet (sq.ft)" to String.format(Locale.US, "%,.2f sq.ft", sqm * 10.7639),
                        "Acres" to String.format(Locale.US, "%,.4f ac", sqm / 4046.86)
                    )
                }
                "Volume" -> {
                    val cum = when (selectedUnit) {
                        "Cubic Meters" -> numVal
                        "Cubic Feet" -> numVal / 35.3147
                        "Liters" -> numVal / 1000.0
                        "Brass" -> numVal * 2.83168
                        else -> numVal
                    }
                    listOf(
                        "Cubic Meters (m³)" to String.format(Locale.US, "%,.3f m³", cum),
                        "Cubic Feet (cft)" to String.format(Locale.US, "%,.2f cft", cum * 35.3147),
                        "Liters (L)" to String.format(Locale.US, "%,.1f L", cum * 1000.0),
                        "Brass (100 cft)" to String.format(Locale.US, "%,.3f Brass", cum / 2.83168)
                    )
                }
                else -> {
                    val kg = when (selectedUnit) {
                        "Metric Tonnes" -> numVal * 1000.0
                        "Kilograms" -> numVal
                        "Quintals" -> numVal * 100.0
                        "Pounds" -> numVal * 0.453592
                        else -> numVal
                    }
                    listOf(
                        "Metric Tonnes (MT)" to String.format(Locale.US, "%,.3f MT", kg / 1000.0),
                        "Kilograms (kg)" to String.format(Locale.US, "%,.2f kg", kg),
                        "Quintals (q)" to String.format(Locale.US, "%,.2f q", kg / 100.0),
                        "Pounds (lb)" to String.format(Locale.US, "%,.1f lb", kg * 2.20462)
                    )
                }
            }

            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        results.joinToString(" | ") { "${it.first}: ${it.second}" }
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.forEachIndexed { index, (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                color = ToolColors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "= $value",
                                color = ToolColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (index < results.lastIndex) {
                            HorizontalDivider(color = ToolColors.border.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// SHARED UI CHIP & CLIPBOARD HELPER FOR TOOLS
// ============================================================================
@Composable
fun ToolChoiceChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()
    val containerColor = when {
        selected && isDark -> Color(0xFF1E3A8A).copy(alpha = 0.55f)
        selected && !isDark -> Color(0xFFEFF6FF)
        isDark -> Color(0xFF1E293B)
        else -> Color(0xFFFFFFFF)
    }
    val borderColor = when {
        selected -> ToolColors.accentBlue
        else -> ToolColors.border
    }
    val textColor = when {
        selected && isDark -> Color(0xFF93C5FD)
        selected && !isDark -> Color(0xFF1D4ED8)
        else -> ToolColors.textPrimary
    }

    Surface(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun copyToolResult(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Highway Tool Result", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
