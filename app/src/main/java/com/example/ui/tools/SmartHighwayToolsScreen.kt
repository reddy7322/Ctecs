package com.example.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.QcRecordEntity
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ToolsCategory(val label: String, val icon: ImageVector) {
    CHAINAGE("Chainage", Icons.Default.LinearScale),
    ROAD_CONST("Road Works", Icons.Default.Engineering),
    QUALITY_QC("QC & Inspection", Icons.Default.CheckCircle),
    UTILITIES("Utilities", Icons.Default.Handyman)
}

data class ToolCardItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val category: ToolsCategory
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHighwayToolsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeToolId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSavedQcDialog by remember { mutableStateOf(false) }

    val qcRecords by viewModel.allQcRecords.collectAsStateWithLifecycle()

    val toolItems = remember {
        listOf(
            ToolCardItem("chainage_calc", "Chainage\nCalculator", Icons.Default.Calculate, Color(0xFF6366F1), ToolsCategory.CHAINAGE),
            ToolCardItem("distance_calc", "Distance\nCalculator", Icons.Default.AltRoute, Color(0xFF16A34A), ToolsCategory.CHAINAGE),
            ToolCardItem("gradient_calc", "Gradient\nCalculator", Icons.Default.LinearScale, Color(0xFFF97316), ToolsCategory.CHAINAGE),
            ToolCardItem("area_calc", "Area\nCalculator", Icons.Default.Layers, Color(0xFF0284C7), ToolsCategory.CHAINAGE),
            ToolCardItem("volume_calc", "Volume\nCalculator", Icons.Default.Build, Color(0xFFEC4899), ToolsCategory.CHAINAGE),
            ToolCardItem("level_calc", "Level\nCalculator", Icons.Default.Engineering, Color(0xFF0D9488), ToolsCategory.CHAINAGE),
            ToolCardItem("curve_calc", "Curve\nCalculator", Icons.Default.AltRoute, Color(0xFFF59E0B), ToolsCategory.CHAINAGE),
            ToolCardItem("steel_calc", "Steel\nCalculator", Icons.Default.Build, Color(0xFF2563EB), ToolsCategory.ROAD_CONST),
            ToolCardItem("concrete_calc", "Concrete\nCalculator", Icons.Default.Engineering, Color(0xFF8B5CF6), ToolsCategory.ROAD_CONST),
            ToolCardItem("joint_calc", "Joint\nCalculator", Icons.Default.LinearScale, Color(0xFFEF4444), ToolsCategory.ROAD_CONST),
            ToolCardItem("pavement_layers", "Pavement\nLayers", Icons.Default.Layers, Color(0xFF06B6D4), ToolsCategory.ROAD_CONST),
            ToolCardItem("unit_converter", "Unit\nConverter", Icons.Default.Handyman, Color(0xFF64748B), ToolsCategory.CHAINAGE),
            ToolCardItem("qc_register", "QC & Inspection\nRegister", Icons.Default.CheckCircle, Color(0xFF10B981), ToolsCategory.QUALITY_QC),
            ToolCardItem("site_utilities", "Site Utilities\nCalculator", Icons.Default.Assignment, Color(0xFF4F46E5), ToolsCategory.UTILITIES)
        )
    }

    val filteredTools = remember(searchQuery, toolItems) {
        if (searchQuery.isBlank()) toolItems else {
            toolItems.filter { it.title.replace("\n", " ").contains(searchQuery, ignoreCase = true) }
        }
    }

    BackHandler {
        if (activeToolId != null) {
            activeToolId = null
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeToolId == null) "Tools" else toolItems.find { it.id == activeToolId }?.title?.replace("\n", " ") ?: "Tool",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeToolId != null) {
                                activeToolId = null
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (activeToolId == null) {
                        IconButton(onClick = { showSavedQcDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Saved QC Records"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (activeToolId == null) {
                // Main Tools Grid (Image 1 Style)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val rows = filteredTools.chunked(3)
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { item ->
                                ToolsGlassGridCard(
                                    item = item,
                                    onClick = { activeToolId = item.id },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size < 3) {
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                // Active Tool Detail View
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (activeToolId) {
                        "qc_register" -> QualityControlRegistersView(viewModel, context)
                        "site_utilities" -> SiteUtilitiesView(viewModel, context)
                        "steel_calc", "concrete_calc", "joint_calc", "pavement_layers" -> RoadConstructionCalculatorsView(context)
                        else -> ChainageMeasurementToolsView(context)
                    }
                }
            }
        }
    }

    // Saved QC Records History Modal
    if (showSavedQcDialog) {
        SavedQcRecordsDialog(
            records = qcRecords,
            onDismiss = { showSavedQcDialog = false },
            onDelete = { record ->
                scope.launch {
                    viewModel.deleteQcRecord(record)
                    Toast.makeText(context, "Deleted QC Record", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun ToolsGlassGridCard(
    item: ToolCardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        shadowElevation = 1.dp,
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("tool_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Colorful Squircle Icon Container
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = item.color,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ==========================================
// A. CHAINAGE & MEASUREMENT TOOLS
// ==========================================
@Composable
fun ChainageMeasurementToolsView(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Chainage Distance & From/To Calculator
        ToolCard(
            title = "From / To Chainage Distance Calculator",
            subtitle = "Calculates exact linear distance between two highway stations",
            icon = Icons.Default.LinearScale
        ) {
            var fromCh by remember { mutableStateOf("177+200") }
            var toCh by remember { mutableStateOf("178+650") }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fromCh,
                    onValueChange = { fromCh = it },
                    label = { Text("From Chainage") },
                    placeholder = { Text("177+200") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = toCh,
                    onValueChange = { toCh = it },
                    label = { Text("To Chainage") },
                    placeholder = { Text("178+650") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            val fromMeters = parseChainageToMeters(fromCh)
            val toMeters = parseChainageToMeters(toCh)
            val distanceM = abs(toMeters - fromMeters)
            val distanceKm = distanceM / 1000.0

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Net Distance (Meters)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.1f".format(Locale.US, distanceM)} m", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Distance (Kilometers)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.3f".format(Locale.US, distanceKm)} km", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0284C7))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Formula: |To Chainage (m) - From Chainage (m)| = |$toMeters - $fromMeters|", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 2. Chainage Offset / Addition Calculator
        ToolCard(
            title = "Chainage Offset Calculator",
            subtitle = "Add or subtract distance offsets from a known station",
            icon = Icons.Default.Calculate
        ) {
            var baseCh by remember { mutableStateOf("177+365") }
            var offsetM by remember { mutableStateOf("450") }
            var isAdd by remember { mutableStateOf(true) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = baseCh,
                    onValueChange = { baseCh = it },
                    label = { Text("Base Station (Km+m)") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = offsetM,
                    onValueChange = { offsetM = it },
                    label = { Text("Offset (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }

            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isAdd,
                    onClick = { isAdd = true },
                    label = { Text("+ Add Offset (Forward)") }
                )
                FilterChip(
                    selected = !isAdd,
                    onClick = { isAdd = false },
                    label = { Text("- Subtract Offset (Back)") }
                )
            }

            val baseMeters = parseChainageToMeters(baseCh)
            val delta = offsetM.toDoubleOrNull() ?: 0.0
            val targetMeters = if (isAdd) baseMeters + delta else (baseMeters - delta).coerceAtLeast(0.0)
            val resultFormatted = formatMetersToChainage(targetMeters)

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Text("Target Chainage Station", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(resultFormatted, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                Text("Total Station Meterage: ${"%,.1f".format(Locale.US, targetMeters)} m", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 3. Length, Width & Area Calculator
        ToolCard(
            title = "Highway Area & Paving Footprint",
            subtitle = "Computes road surface area in m², Hectares, Sq. Yards, Acres",
            icon = Icons.Default.AltRoute
        ) {
            var lengthM by remember { mutableStateOf("1250") }
            var widthM by remember { mutableStateOf("7.0") }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = lengthM,
                    onValueChange = { lengthM = it },
                    label = { Text("Length (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = widthM,
                    onValueChange = { widthM = it },
                    label = { Text("Paved Width (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            val l = lengthM.toDoubleOrNull() ?: 0.0
            val w = widthM.toDoubleOrNull() ?: 0.0
            val areaSqm = l * w
            val areaHa = areaSqm / 10000.0
            val areaSqYd = areaSqm * 1.19599
            val areaAcre = areaSqm / 4046.86

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Area (Square Meters)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.1f".format(Locale.US, areaSqm)} m²", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Area (Hectares)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.4f".format(Locale.US, areaHa)} ha", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF059669))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sq. Yards: ${"%,.1f".format(Locale.US, areaSqYd)} sq.yd", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Acres: ${"%.3f".format(Locale.US, areaAcre)} acres", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 4. Volume Calculator
        ToolCard(
            title = "Layer Volume Calculator",
            subtitle = "Formula: Length (m) × Width (m) × Thickness (mm/m)",
            icon = Icons.Default.Calculate
        ) {
            var lenM by remember { mutableStateOf("1000") }
            var widM by remember { mutableStateOf("7.0") }
            var thickMm by remember { mutableStateOf("40") } // e.g. 40mm BC

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lenM,
                    onValueChange = { lenM = it },
                    label = { Text("Length (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = widM,
                    onValueChange = { widM = it },
                    label = { Text("Width (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = thickMm,
                    onValueChange = { thickMm = it },
                    label = { Text("Thick (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            val l = lenM.toDoubleOrNull() ?: 0.0
            val w = widM.toDoubleOrNull() ?: 0.0
            val tM = (thickMm.toDoubleOrNull() ?: 0.0) / 1000.0
            val volCum = l * w * tM
            val volCuFt = volCum * 35.3147

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Compacted Volume (Cum / m³)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.2f".format(Locale.US, volCum)} m³", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Volume (Cubic Feet)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.1f".format(Locale.US, volCuFt)} cft", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFD97706))
                    }
                }
            }
        }

        // 5. Work Progress & % Completion Calculator
        ToolCard(
            title = "Work Progress & Target Tracker",
            subtitle = "Calculates physical completion % and remaining highway length/quantity",
            icon = Icons.Default.CheckCircle
        ) {
            var targetQty by remember { mutableStateOf("15.5") }
            var completedQty by remember { mutableStateOf("9.8") }
            var unit by remember { mutableStateOf("km") }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = targetQty,
                    onValueChange = { targetQty = it },
                    label = { Text("Total Target ($unit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = completedQty,
                    onValueChange = { completedQty = it },
                    label = { Text("Completed ($unit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            val target = targetQty.toDoubleOrNull() ?: 0.0
            val done = completedQty.toDoubleOrNull() ?: 0.0
            val pct = if (target > 0) ((done / target) * 100.0).coerceIn(0.0, 100.0) else 0.0
            val remaining = (target - done).coerceAtLeast(0.0)

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Completion: ${"%.1f".format(Locale.US, pct)}%", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Remaining: ${"%.2f".format(Locale.US, remaining)} $unit", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFFEF4444))
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (pct / 100f).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // 6. Unit Converter
        ToolCard(
            title = "Civil & Highway Unit Converter",
            subtitle = "Convert length, area, volume and mass instantly",
            icon = Icons.Default.Calculate
        ) {
            var inputValue by remember { mutableStateOf("100") }
            var selectedUnit by remember { mutableStateOf("Meters") }

            val units = listOf("Meters", "Kilometers", "Feet", "Inches", "Millimeters")

            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                label = { Text("Input Value") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                units.forEach { u ->
                    FilterChip(
                        selected = selectedUnit == u,
                        onClick = { selectedUnit = u },
                        label = { Text(u, fontSize = 11.sp) }
                    )
                }
            }

            val input = inputValue.toDoubleOrNull() ?: 0.0
            val inMeters = when (selectedUnit) {
                "Kilometers" -> input * 1000.0
                "Feet" -> input * 0.3048
                "Inches" -> input * 0.0254
                "Millimeters" -> input / 1000.0
                else -> input
            }

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("= ${"%,.3f".format(Locale.US, inMeters)} Meters", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("= ${"%.4f".format(Locale.US, inMeters / 1000.0)} Kilometers", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text("= ${"%,.2f".format(Locale.US, inMeters / 0.3048)} Feet", fontSize = 13.sp)
                    Text("= ${"%,.1f".format(Locale.US, inMeters / 0.0254)} Inches", fontSize = 13.sp)
                    Text("= ${"%,.0f".format(Locale.US, inMeters * 1000.0)} Millimeters", fontSize = 13.sp)
                }
            }
        }
    }
}

// ==========================================
// B. ROAD CONSTRUCTION CALCULATORS
// ==========================================
@Composable
fun RoadConstructionCalculatorsView(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Bituminous Concrete (BC) Quantity Calculator
        ToolCard(
            title = "BC (Bituminous Concrete) Quantity Calculator",
            subtitle = "Standard MORTH Clause 507 • Compacted Density: ~2.45 t/m³",
            icon = Icons.Default.Engineering
        ) {
            var lengthM by remember { mutableStateOf("500") }
            var widthM by remember { mutableStateOf("7.0") }
            var thickMm by remember { mutableStateOf("40") } // 40mm BC
            var density by remember { mutableStateOf("2.45") } // 2.45 t/m3
            var tipperCapacity by remember { mutableStateOf("25") } // 25 MT tipper

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lengthM,
                    onValueChange = { lengthM = it },
                    label = { Text("Length (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = widthM,
                    onValueChange = { widthM = it },
                    label = { Text("Width (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = thickMm,
                    onValueChange = { thickMm = it },
                    label = { Text("Thick (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = density,
                    onValueChange = { density = it },
                    label = { Text("Density (t/m³)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = tipperCapacity,
                    onValueChange = { tipperCapacity = it },
                    label = { Text("Truck (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            val l = lengthM.toDoubleOrNull() ?: 0.0
            val w = widthM.toDoubleOrNull() ?: 0.0
            val tM = (thickMm.toDoubleOrNull() ?: 0.0) / 1000.0
            val dens = density.toDoubleOrNull() ?: 2.45
            val truckCap = tipperCapacity.toDoubleOrNull() ?: 25.0

            val volCum = l * w * tM
            val totalTonnage = volCum * dens
            val numTrucks = if (truckCap > 0) totalTonnage / truckCap else 0.0

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("BC Total Weight Required", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.1f".format(Locale.US, totalTonnage)} MT", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Tipper Trips (${truckCap.toInt()} MT)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.1f".format(Locale.US, numTrucks)} Trips (~${numTrucks.roundToInt()})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFD97706))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Compacted Volume: ${"%,.2f".format(Locale.US, volCum)} m³ • Formula: $l × $w × ${"%.3f".format(Locale.US, tM)} × $dens", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 2. DBM (Dense Bituminous Macadam) Quantity Calculator
        ToolCard(
            title = "DBM Quantity Calculator",
            subtitle = "Standard MORTH Clause 505 • Compacted Density: ~2.40 t/m³",
            icon = Icons.Default.Engineering
        ) {
            var lengthM by remember { mutableStateOf("500") }
            var widthM by remember { mutableStateOf("7.0") }
            var thickMm by remember { mutableStateOf("60") } // 60mm DBM
            var density by remember { mutableStateOf("2.40") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lengthM,
                    onValueChange = { lengthM = it },
                    label = { Text("Length (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = widthM,
                    onValueChange = { widthM = it },
                    label = { Text("Width (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = thickMm,
                    onValueChange = { thickMm = it },
                    label = { Text("Thick (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            val l = lengthM.toDoubleOrNull() ?: 0.0
            val w = widthM.toDoubleOrNull() ?: 0.0
            val tM = (thickMm.toDoubleOrNull() ?: 0.0) / 1000.0
            val dens = density.toDoubleOrNull() ?: 2.40

            val volCum = l * w * tM
            val totalTonnage = volCum * dens

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("DBM Total Quantity", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.1f".format(Locale.US, totalTonnage)} MT", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Volume", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.2f".format(Locale.US, volCum)} m³", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0284C7))
                    }
                }
            }
        }

        // 3. WMM & GSB Quantity Calculator
        ToolCard(
            title = "WMM & GSB Base Layer Calculator",
            subtitle = "WMM: ~2.25 t/m³ • GSB: ~2.15 t/m³ with Water Requirement",
            icon = Icons.Default.Build
        ) {
            var selectedLayer by remember { mutableStateOf("WMM") }
            var lengthM by remember { mutableStateOf("500") }
            var widthM by remember { mutableStateOf("7.5") }
            var thickMm by remember { mutableStateOf("150") } // 150mm WMM or 200mm GSB

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedLayer == "WMM",
                    onClick = { selectedLayer = "WMM"; thickMm = "150" },
                    label = { Text("WMM (2.25 t/m³)") }
                )
                FilterChip(
                    selected = selectedLayer == "GSB",
                    onClick = { selectedLayer = "GSB"; thickMm = "200" },
                    label = { Text("GSB (2.15 t/m³)") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lengthM,
                    onValueChange = { lengthM = it },
                    label = { Text("Length (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = widthM,
                    onValueChange = { widthM = it },
                    label = { Text("Width (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = thickMm,
                    onValueChange = { thickMm = it },
                    label = { Text("Thick (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            val l = lengthM.toDoubleOrNull() ?: 0.0
            val w = widthM.toDoubleOrNull() ?: 0.0
            val tM = (thickMm.toDoubleOrNull() ?: 0.0) / 1000.0
            val dens = if (selectedLayer == "WMM") 2.25 else 2.15

            val volCum = l * w * tM
            val totalTonnage = volCum * dens
            val estWaterLitres = totalTonnage * 1000.0 * 0.06 // 6% OMC

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("$selectedLayer Total Weight", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.1f".format(Locale.US, totalTonnage)} MT", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Est. Water (6% OMC)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.0f".format(Locale.US, estWaterLitres)} Litres", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0284C7))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Compacted Volume: ${"%,.1f".format(Locale.US, volCum)} Cum (m³)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 4. Tack Coat & Prime Coat Calculator
        ToolCard(
            title = "Tack Coat & Prime Coat Emulsion Calculator",
            subtitle = "MORTH Table 500-3 & 500-5 Specification Rates",
            icon = Icons.Default.Opacity
        ) {
            var coatType by remember { mutableStateOf("Tack Coat") }
            var lengthM by remember { mutableStateOf("1000") }
            var widthM by remember { mutableStateOf("7.0") }
            var sprayRate by remember { mutableStateOf("0.25") } // 0.25 kg/m2 for Bituminous surface Tack

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = coatType == "Tack Coat",
                    onClick = { coatType = "Tack Coat"; sprayRate = "0.25" },
                    label = { Text("Tack Coat (0.25 kg/m²)") }
                )
                FilterChip(
                    selected = coatType == "Prime Coat",
                    onClick = { coatType = "Prime Coat"; sprayRate = "0.75" },
                    label = { Text("Prime Coat (0.75 kg/m²)") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lengthM,
                    onValueChange = { lengthM = it },
                    label = { Text("Length (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = widthM,
                    onValueChange = { widthM = it },
                    label = { Text("Width (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = sprayRate,
                    onValueChange = { sprayRate = it },
                    label = { Text("Rate (kg/m²)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.2f)
                )
            }

            val l = lengthM.toDoubleOrNull() ?: 0.0
            val w = widthM.toDoubleOrNull() ?: 0.0
            val rate = sprayRate.toDoubleOrNull() ?: 0.25

            val areaSqm = l * w
            val totalKg = areaSqm * rate
            val drums200kg = totalKg / 200.0

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Emulsion Required", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.1f".format(Locale.US, totalKg)} kg", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Standard Drums (200 kg)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.1f".format(Locale.US, drums200kg)} Drums", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFD97706))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Spray Surface Area: ${"%,.0f".format(Locale.US, areaSqm)} m² (~${"%,.0f".format(Locale.US, totalKg)} Litres)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 5. Bitumen Consumption Calculator
        ToolCard(
            title = "Bitumen Binder Consumption Calculator",
            subtitle = "Computes pure bitumen vs aggregate by binder content percentage",
            icon = Icons.Default.Opacity
        ) {
            var mixTonnage by remember { mutableStateOf("500") }
            var binderPct by remember { mutableStateOf("5.0") } // 5.0% for BC, 4.5% for DBM

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = mixTonnage,
                    onValueChange = { mixTonnage = it },
                    label = { Text("Total Mix Tonnage (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = binderPct,
                    onValueChange = { binderPct = it },
                    label = { Text("Bitumen % (Weight)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            val mix = mixTonnage.toDoubleOrNull() ?: 0.0
            val pct = binderPct.toDoubleOrNull() ?: 5.0
            val pureBitumenMT = mix * (pct / 100.0)
            val aggregateMT = mix - pureBitumenMT

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Bitumen VG-30/40 Binder", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.2f".format(Locale.US, pureBitumenMT)} MT (${(pureBitumenMT * 1000).toInt()} kg)", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Mineral Aggregates", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.2f".format(Locale.US, aggregateMT)} MT", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF475569))
                    }
                }
            }
        }
    }
}

// ==========================================
// C. QUALITY CONTROL & SITE INSPECTION REGISTERS
// ==========================================
@Composable
fun QualityControlRegistersView(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var selectedRegisterType by remember { mutableStateOf("BC_TEMP") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Register Type Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val registers = listOf(
                "BC Temp" to "BC_TEMP",
                "Glass Grid" to "GLASS_GRID",
                "Tack Coat" to "TACK_COAT",
                "Pre-Survey" to "PRE_SURVEY",
                "Site Obs" to "SITE_OBS",
                "NCR" to "NCR"
            )
            registers.take(3).forEach { (lbl, type) ->
                FilterChip(
                    selected = selectedRegisterType == type,
                    onClick = { selectedRegisterType = type },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val registers2 = listOf(
                "Pre-Survey" to "PRE_SURVEY",
                "Site Obs" to "SITE_OBS",
                "NCR Tracker" to "NCR"
            )
            registers2.forEach { (lbl, type) ->
                FilterChip(
                    selected = selectedRegisterType == type,
                    onClick = { selectedRegisterType = type },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
        }

        when (selectedRegisterType) {
            "BC_TEMP" -> BcTemperatureRegisterForm(viewModel, context)
            "GLASS_GRID" -> GlassGridRegisterForm(viewModel, context)
            "TACK_COAT" -> TackCoatChecklistForm(viewModel, context)
            "PRE_SURVEY" -> PreSurveyChecklistForm(viewModel, context)
            "SITE_OBS" -> SiteObservationForm(viewModel, context)
            "NCR" -> NcrTrackerForm(viewModel, context)
        }
    }
}

@Composable
fun BcTemperatureRegisterForm(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var chainage by remember { mutableStateOf("177+500") }
    var atmosphericTemp by remember { mutableStateOf("28") }
    var plantTemp by remember { mutableStateOf("162") }
    var siteArrivalTemp by remember { mutableStateOf("155") }
    var laydownTemp by remember { mutableStateOf("148") } // min 140
    var breakdownRollTemp by remember { mutableStateOf("135") } // 130-140
    var finishRollTemp by remember { mutableStateOf("90") } // min 85
    var inspectorName by remember { mutableStateOf("Quality Inspector") }

    val laydownOk = (laydownTemp.toDoubleOrNull() ?: 0.0) >= 140.0
    val finishOk = (finishRollTemp.toDoubleOrNull() ?: 0.0) >= 80.0
    val isCompliant = laydownOk && finishOk

    ToolCard(
        title = "BC Laying & Rolling Temperature Register",
        subtitle = "MORTH Clause 505 / 507 Permissible Temperature Check",
        icon = Icons.Default.Thermostat
    ) {
        OutlinedTextField(
            value = chainage,
            onValueChange = { chainage = it },
            label = { Text("Location Chainage") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = atmosphericTemp,
                onValueChange = { atmosphericTemp = it },
                label = { Text("Ambient (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = plantTemp,
                onValueChange = { plantTemp = it },
                label = { Text("Plant Mix (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = siteArrivalTemp,
                onValueChange = { siteArrivalTemp = it },
                label = { Text("Site Arrival (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = laydownTemp,
                onValueChange = { laydownTemp = it },
                label = { Text("Laydown (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = breakdownRollTemp,
                onValueChange = { breakdownRollTemp = it },
                label = { Text("Breakdown (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = finishRollTemp,
                onValueChange = { finishRollTemp = it },
                label = { Text("Finish (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = inspectorName,
            onValueChange = { inspectorName = it },
            label = { Text("Quality Inspector Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))
        ResultBox {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MORTH 507 Compliance Check", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (isCompliant) "✓ PASS (Temp in Permissible Range)" else "⚠ WARNING (Low Laydown/Finish Temp)",
                        color = if (isCompliant) Color(0xFF16A34A) else Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Button(
                    onClick = {
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val record = QcRecordEntity(
                            type = "BC_TEMP",
                            title = "BC Temp Register at $chainage",
                            projectId = "NH-48",
                            dateString = today,
                            chainage = chainage,
                            layer = "BC",
                            detailsJson = "Laydown: ${laydownTemp}°C, Breakdown: ${breakdownRollTemp}°C, Finish: ${finishRollTemp}°C, Ambient: ${atmosphericTemp}°C",
                            status = if (isCompliant) "Pass" else "Warning",
                            inspectorName = inspectorName,
                            remarks = if (isCompliant) "MORTH compliant" else "Rolling temperature low, monitored closely"
                        )
                        scope.launch {
                            viewModel.saveQcRecord(record)
                            Toast.makeText(context, "Saved to QC Inspection Register", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Record")
                }
            }
        }
    }
}

@Composable
fun GlassGridRegisterForm(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var chainageRange by remember { mutableStateOf("177+000 to 177+450") }
    var laneSide by remember { mutableStateOf("LHS Lane 1") }
    var overlapMm by remember { mutableStateOf("160") } // min 150mm
    var tackCoatRate by remember { mutableStateOf("0.30") }
    var isTensionOk by remember { mutableStateOf(true) }

    val overlapOk = (overlapMm.toIntOrNull() ?: 0) >= 150

    ToolCard(
        title = "Glass Grid (Geogrid) Application Register",
        subtitle = "Checks overlap (min 150mm), tension, tack rate and anchorage",
        icon = Icons.Default.CheckCircle
    ) {
        OutlinedTextField(
            value = chainageRange,
            onValueChange = { chainageRange = it },
            label = { Text("Chainage Span") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = laneSide,
                onValueChange = { laneSide = it },
                label = { Text("Lane / Side") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = overlapMm,
                onValueChange = { overlapMm = it },
                label = { Text("Overlap (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = tackCoatRate,
                onValueChange = { tackCoatRate = it },
                label = { Text("Tack Rate (kg/m²)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = isTensionOk,
                onClick = { isTensionOk = !isTensionOk },
                label = { Text(if (isTensionOk) "✓ Wrinkle-free" else "⚠ Wrinkled") },
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        ResultBox {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Overlap Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (overlapOk) "✓ Pass (≥ 150mm)" else "✕ Fail (< 150mm)", fontWeight = FontWeight.Bold, color = if (overlapOk) Color(0xFF16A34A) else Color(0xFFDC2626))
                }
                Button(
                    onClick = {
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val record = QcRecordEntity(
                            type = "GLASS_GRID",
                            title = "Glass Grid at $chainageRange",
                            projectId = "NH-48",
                            dateString = today,
                            chainage = chainageRange,
                            side = laneSide,
                            layer = "Glass Grid",
                            detailsJson = "Overlap: ${overlapMm}mm, Tack: ${tackCoatRate}kg/m², Tension: ${if (isTensionOk) "Good" else "Wrinkled"}",
                            status = if (overlapOk && isTensionOk) "Pass" else "Pending Action"
                        )
                        scope.launch {
                            viewModel.saveQcRecord(record)
                            Toast.makeText(context, "Saved Glass Grid Entry", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save Entry")
                }
            }
        }
    }
}

@Composable
fun TackCoatChecklistForm(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var chainage by remember { mutableStateOf("177+600 to 178+100") }
    var surfaceCleaned by remember { mutableStateOf(true) }
    var nozzlesChecked by remember { mutableStateOf(true) }
    var sprayRateSufficient by remember { mutableStateOf(true) }
    var curedToBlack by remember { mutableStateOf(true) }

    ToolCard(
        title = "Tack Coat Inspection Checklist",
        subtitle = "Verify cleaning, nozzle spray uniform distribution and break time",
        icon = Icons.Default.Opacity
    ) {
        OutlinedTextField(
            value = chainage,
            onValueChange = { chainage = it },
            label = { Text("Chainage Span") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChecklistRow("1. Existing surface thoroughly air-cleaned & dust free", surfaceCleaned) { surfaceCleaned = it }
        ChecklistRow("2. Distributor spray bar height & nozzles clear & uniform", nozzlesChecked) { nozzlesChecked = it }
        ChecklistRow("3. Rate of application verified by tray test (0.25-0.30 kg/m²)", sprayRateSufficient) { sprayRateSufficient = it }
        ChecklistRow("4. Emulsion broken & cured (turned from brown to black)", curedToBlack) { curedToBlack = it }

        val allOk = surfaceCleaned && nozzlesChecked && sprayRateSufficient && curedToBlack
        Spacer(modifier = Modifier.height(10.dp))
        ResultBox {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (allOk) "✓ Surface Approved for Paving" else "⚠ Rectification Required before Paver",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (allOk) Color(0xFF16A34A) else Color(0xFFDC2626)
                )
                Button(
                    onClick = {
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val record = QcRecordEntity(
                            type = "TACK_COAT",
                            title = "Tack Coat Checklist $chainage",
                            projectId = "NH-48",
                            dateString = today,
                            chainage = chainage,
                            layer = "Tack Coat",
                            detailsJson = "Cleaned: $surfaceCleaned, Nozzles: $nozzlesChecked, Rate: $sprayRateSufficient, Cured: $curedToBlack",
                            status = if (allOk) "Pass" else "Pending"
                        )
                        scope.launch {
                            viewModel.saveQcRecord(record)
                            Toast.makeText(context, "Saved Tack Coat Checklist", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save Checklist")
                }
            }
        }
    }
}

@Composable
fun PreSurveyChecklistForm(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var chainage by remember { mutableStateOf("177+000") }
    var calibrated by remember { mutableStateOf(true) }
    var benchmarkOk by remember { mutableStateOf(true) }
    var safetyConesPlaced by remember { mutableStateOf(true) }

    ToolCard(
        title = "Pre-Survey & Site Safety Checklist",
        subtitle = "Instrument calibration, Benchmark verification and traffic safety",
        icon = Icons.Default.Assignment
    ) {
        OutlinedTextField(
            value = chainage,
            onValueChange = { chainage = it },
            label = { Text("Base Benchmark / Station") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        ChecklistRow("1. Total Station / GPS Rover calibration within valid date", calibrated) { calibrated = it }
        ChecklistRow("2. TBM Reduced Level (RL) cross-verified with master BM", benchmarkOk) { benchmarkOk = it }
        ChecklistRow("3. Traffic safety cones, warning signage & flagmen positioned", safetyConesPlaced) { safetyConesPlaced = it }

        val ready = calibrated && benchmarkOk && safetyConesPlaced
        Spacer(modifier = Modifier.height(10.dp))
        ResultBox {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (ready) "✓ Survey Cleared" else "⚠ Missing Safety / Calibration",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (ready) Color(0xFF16A34A) else Color(0xFFDC2626)
                )
                Button(
                    onClick = {
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val record = QcRecordEntity(
                            type = "PRE_SURVEY",
                            title = "Pre-Survey Clearance at $chainage",
                            projectId = "NH-48",
                            dateString = today,
                            chainage = chainage,
                            detailsJson = "Calibrated: $calibrated, TBM Checked: $benchmarkOk, Safety Signage: $safetyConesPlaced",
                            status = if (ready) "Pass" else "Pending"
                        )
                        scope.launch {
                            viewModel.saveQcRecord(record)
                            Toast.makeText(context, "Saved Survey Clearance Record", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save Record")
                }
            }
        }
    }
}

@Composable
fun SiteObservationForm(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var chainage by remember { mutableStateOf("177+420") }
    var side by remember { mutableStateOf("LHS") }
    var severity by remember { mutableStateOf("Medium") }
    var description by remember { mutableStateOf("Bleeding spot noticed near outer wheel path, check bitumen content.") }
    var actionRequired by remember { mutableStateOf("Sand blotting and roller pass required before traffic opening.") }

    ToolCard(
        title = "Site Observation Register",
        subtitle = "Record defects, surface observations and corrective actions",
        icon = Icons.Default.ReportProblem
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = chainage,
                onValueChange = { chainage = it },
                label = { Text("Chainage") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = side,
                onValueChange = { side = it },
                label = { Text("Side") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Low", "Medium", "Critical").forEach { s ->
                FilterChip(
                    selected = severity == s,
                    onClick = { severity = s },
                    label = { Text(s) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Observation Description") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = actionRequired,
            onValueChange = { actionRequired = it },
            label = { Text("Corrective Action Required") },
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val record = QcRecordEntity(
                    type = "SITE_OBS",
                    title = "Observation at $chainage $side",
                    projectId = "NH-48",
                    dateString = today,
                    chainage = chainage,
                    side = side,
                    detailsJson = "Severity: $severity, Obs: $description, Action: $actionRequired",
                    status = "Open",
                    remarks = actionRequired
                )
                scope.launch {
                    viewModel.saveQcRecord(record)
                    Toast.makeText(context, "Observation Saved to Register", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Log Site Observation")
        }
    }
}

@Composable
fun NcrTrackerForm(viewModel: MainViewModel, context: Context) {
    val scope = rememberCoroutineScope()
    var ncrNo by remember { mutableStateOf("NCR-2026-014") }
    var clauseRef by remember { mutableStateOf("MORTH Clause 507.4.2 (Surface Evenness)") }
    var issueText by remember { mutableStateOf("Surface irregularity exceeds 6mm under 3m straight edge at CH 177+800.") }
    var correctiveAction by remember { mutableStateOf("Milling of high spot (3mm depth) and re-laying BC top lift.") }

    ToolCard(
        title = "Non-Compliance Report (NCR) & Rectification Tracker",
        subtitle = "Official quality deviation and corrective verification record",
        icon = Icons.Default.ReportProblem
    ) {
        OutlinedTextField(
            value = ncrNo,
            onValueChange = { ncrNo = it },
            label = { Text("NCR Reference Number") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = clauseRef,
            onValueChange = { clauseRef = it },
            label = { Text("Specification Clause Ref") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = issueText,
            onValueChange = { issueText = it },
            label = { Text("Non-Compliance Description") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = correctiveAction,
            onValueChange = { correctiveAction = it },
            label = { Text("Approved Rectification Action") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val record = QcRecordEntity(
                    type = "NCR",
                    title = "$ncrNo - $clauseRef",
                    projectId = "NH-48",
                    dateString = today,
                    detailsJson = "NCR: $ncrNo, Clause: $clauseRef, Issue: $issueText, Action: $correctiveAction",
                    status = "Open / Pending Rectification",
                    remarks = correctiveAction
                )
                scope.launch {
                    viewModel.saveQcRecord(record)
                    Toast.makeText(context, "NCR Logged Successfully", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Issue Non-Compliance Report (NCR)")
        }
    }
}

// ==========================================
// D. SITE UTILITIES & GPS
// ==========================================
@Composable
fun SiteUtilitiesView(viewModel: MainViewModel, context: Context) {
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Live GPS Coordinate Viewer
        ToolCard(
            title = "Live GPS Coordinates & Geodetic Converter",
            subtitle = "Decimal Degrees (DD), DMS and UTM Zone Estimator",
            icon = Icons.Default.GpsFixed
        ) {
            val lat = locationState.point?.latitude ?: 28.6139
            val lng = locationState.point?.longitude ?: 77.2090
            val alt = locationState.altitude
            val acc = locationState.accuracy

            val latDms = convertToDms(lat, isLat = true)
            val lngDms = convertToDms(lng, isLat = false)
            val utmZone = calculateUtmZone(lng)

            ResultBox {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Decimal Degrees:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.6f, %.6f".format(Locale.US, lat, lng), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("DMS Coordinates:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$latDms, $lngDms", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Altitude & Accuracy:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${alt.toInt()} m MSL (±${"%.1f".format(Locale.US, acc)}m)", fontSize = 12.sp, color = Color(0xFF0284C7))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estimated UTM Zone:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Zone $utmZone North (WGS 84)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val text = "%.6f, %.6f ($latDms, $lngDms) Alt: ${alt.toInt()}m".format(Locale.US, lat, lng)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("GPS", text))
                        Toast.makeText(context, "Copied coordinates to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy GPS Coordinates")
                }
            }
        }

        // 2. Road Camber & Slope Calculator
        ToolCard(
            title = "Camber & Cross-Slope Calculator",
            subtitle = "IRC standard 2.5% bituminous camber / crown height check",
            icon = Icons.Default.Engineering
        ) {
            var halfWidthM by remember { mutableStateOf("3.5") }
            var camberPct by remember { mutableStateOf("2.5") } // 2.5% for bituminous

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = halfWidthM,
                    onValueChange = { halfWidthM = it },
                    label = { Text("Carriageway Half-Width (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = camberPct,
                    onValueChange = { camberPct = it },
                    label = { Text("Camber % (Slope)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            val hw = halfWidthM.toDoubleOrNull() ?: 3.5
            val c = camberPct.toDoubleOrNull() ?: 2.5
            val crownHeightMm = hw * (c / 100.0) * 1000.0
            val slopeRatio = if (c > 0) 100.0 / c else 0.0

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Center Crown Height Rise", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%.1f".format(Locale.US, crownHeightMm)} mm", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Gradient Slope 1 in N", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("1 in ${slopeRatio.roundToInt()}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFD97706))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("IRC Specification: 2.5% for Bituminous Surface, 2.0% for Concrete Pavement", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 3. Working Days Calculator
        ToolCard(
            title = "Highway Working-Day & Deadline Calculator",
            subtitle = "Computes net productive days excluding Sundays & weather rain days",
            icon = Icons.Default.Calculate
        ) {
            var totalCalendarDays by remember { mutableStateOf("60") }
            var rainyDaysExpected by remember { mutableStateOf("8") }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = totalCalendarDays,
                    onValueChange = { totalCalendarDays = it },
                    label = { Text("Total Duration (Days)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = rainyDaysExpected,
                    onValueChange = { rainyDaysExpected = it },
                    label = { Text("Rain / Holiday Loss") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            val totalD = totalCalendarDays.toIntOrNull() ?: 60
            val rainD = rainyDaysExpected.toIntOrNull() ?: 8
            val sundays = (totalD / 7)
            val netWorkingDays = (totalD - sundays - rainD).coerceAtLeast(0)

            Spacer(modifier = Modifier.height(10.dp))
            ResultBox {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Net Working Days", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$netWorkingDays Productive Days", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Downtime (Sundays + Rain)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${sundays + rainD} Non-Working Days", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFDC2626))
                    }
                }
            }
        }
    }
}

// ==========================================
// COMMON UI HELPERS & CARDS
// ==========================================
@Composable
fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 10.dp)
            )
            content()
        }
    }
}

@Composable
fun ResultBox(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            content()
        }
    }
}

@Composable
fun ChecklistRow(text: String, isChecked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!isChecked) }
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (isChecked) Color(0xFF16A34A) else Color(0xFF94A3B8),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SavedQcRecordsDialog(
    records: List<QcRecordEntity>,
    onDismiss: () -> Unit,
    onDelete: (QcRecordEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quality Control Inspection Records (${records.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            if (records.isEmpty()) {
                Text("No QC inspection records saved yet. Fill out any QC checklist or temperature register and tap 'Save Record'.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(records, key = { it.id }) { rec ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rec.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Date: ${rec.dateString} • Status: ${rec.status}", fontSize = 11.sp, color = if (rec.status.contains("Pass", ignoreCase = true)) Color(0xFF16A34A) else Color(0xFFDC2626))
                                    Text(rec.detailsJson, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { onDelete(rec) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// Math & String Utilities
fun parseChainageToMeters(str: String): Double {
    val clean = str.replace("CH:", "", ignoreCase = true).replace("km", "", ignoreCase = true).trim()
    return if (clean.contains("+")) {
        val parts = clean.split("+")
        val km = parts[0].trim().toDoubleOrNull() ?: 0.0
        val m = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0
        (km * 1000.0) + m
    } else {
        val d = clean.toDoubleOrNull() ?: 0.0
        if (d < 1000.0 && clean.contains(".")) d * 1000.0 else d
    }
}

fun formatMetersToChainage(meters: Double): String {
    val totalM = meters.toLong()
    val km = totalM / 1000
    val m = totalM % 1000
    return "%d+%03d".format(Locale.US, km, m)
}

fun convertToDms(decDegrees: Double, isLat: Boolean): String {
    val dir = if (isLat) {
        if (decDegrees >= 0) "N" else "S"
    } else {
        if (decDegrees >= 0) "E" else "W"
    }
    val absVal = abs(decDegrees)
    val degrees = absVal.toInt()
    val minutesDouble = (absVal - degrees) * 60.0
    val minutes = minutesDouble.toInt()
    val seconds = (minutesDouble - minutes) * 60.0
    return "%d°%02d'%04.1f\"%s".format(Locale.US, degrees, minutes, seconds, dir)
}

fun calculateUtmZone(longitude: Double): Int {
    return ((longitude + 180.0) / 6.0).toInt() + 1
}
