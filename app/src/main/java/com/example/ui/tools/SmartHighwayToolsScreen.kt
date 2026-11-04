package com.example.ui.tools

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.QcRecordEntity
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.theme.isAppDarkTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

internal object ToolColors {
    val bgDark: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFF0A0E17) else Color(0xFFF8FAFC)
    val cardBg: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFF131B2E) else Color(0xFFFFFFFF)
    val cardBgElevated: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFF1C2742) else Color(0xFFF1F5F9)
    val border: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFF263554) else Color(0xFFE2E8F0)
    // Never use bright yellow in Light Theme; use refined blue/slate accents
    val accentYellow: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFF38BDF8) else Color(0xFF2563EB)
    val accentOrange: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFFFF6D00) else Color(0xFFD97706)
    val accentGreen: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFF00E676) else Color(0xFF059669)
    val accentRed: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFFFF5252) else Color(0xFFDC2626)
    val accentBlue: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFF40C4FF) else Color(0xFF2563EB)
    val textPrimary: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary: Color
        @Composable get() = if (isAppDarkTheme()) Color(0xFF94A3B8) else Color(0xFF64748B)
}

private data class ToolGridItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badgeBg: Color
)

@Composable
fun SmartHighwayToolsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {}
) {
    var activeToolId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDark = isAppDarkTheme()

    BackHandler(enabled = activeToolId != null || isSearchExpanded) {
        if (activeToolId != null) {
            activeToolId = null
        } else if (isSearchExpanded) {
            isSearchExpanded = false
            searchQuery = ""
        }
    }

    // 13 unique, canonical highway engineering tools — zero duplicates
    val gridTools = remember {
        listOf(
            ToolGridItem(
                id = "chainage_calc",
                title = "Chainage Calculator",
                subtitle = "Distance, offset & progress",
                icon = Icons.Default.Straighten,
                badgeBg = Color(0xFF4F46E5)
            ),
            ToolGridItem(
                id = "distance_gradient_calc",
                title = "Distance & Gradient",
                subtitle = "Slope %, 1:N ratio & RL",
                icon = Icons.Default.Terrain,
                badgeBg = Color(0xFFEA580C)
            ),
            ToolGridItem(
                id = "area_calc",
                title = "Area Calculator",
                subtitle = "m², ha, sq.yd & taper area",
                icon = Icons.Default.Layers,
                badgeBg = Color(0xFF2563EB)
            ),
            ToolGridItem(
                id = "volume_calc",
                title = "Volume Calculator",
                subtitle = "Layer & earthwork m³",
                icon = Icons.Default.ViewInAr,
                badgeBg = Color(0xFFDB2777)
            ),
            ToolGridItem(
                id = "level_calc",
                title = "Level Calculator",
                subtitle = "Auto-level HI, RL & dip",
                icon = Icons.Default.Speed,
                badgeBg = Color(0xFF0D9488)
            ),
            ToolGridItem(
                id = "curve_calc",
                title = "Curve Calculator",
                subtitle = "Horizontal curve & e%",
                icon = Icons.Default.TurnRight,
                badgeBg = Color(0xFF7C3AED)
            ),
            ToolGridItem(
                id = "steel_calc",
                title = "Steel Calculator",
                subtitle = "Rebar weight & BBS",
                icon = Icons.Default.Architecture,
                badgeBg = Color(0xFF0284C7)
            ),
            ToolGridItem(
                id = "concrete_calc",
                title = "Concrete Calculator",
                subtitle = "PQC & grade M15–M40",
                icon = Icons.Default.Foundation,
                badgeBg = Color(0xFF4338CA)
            ),
            ToolGridItem(
                id = "joint_calc",
                title = "Joint Calculator",
                subtitle = "PQC dowel & tie bars",
                icon = Icons.Default.GridOn,
                badgeBg = Color(0xFFDC2626)
            ),
            ToolGridItem(
                id = "pavement_layers",
                title = "Pavement Layers",
                subtitle = "BC, DBM, WMM & Tack",
                icon = Icons.Default.AddRoad,
                badgeBg = Color(0xFF0891B2)
            ),
            ToolGridItem(
                id = "unit_converter",
                title = "Unit Converter",
                subtitle = "Length, area, vol & mass",
                icon = Icons.Default.Calculate,
                badgeBg = Color(0xFF475569)
            ),
            ToolGridItem(
                id = "qc_register",
                title = "QC & Inspection Register",
                subtitle = "MORTH logs & NCR tracker",
                icon = Icons.Default.FactCheck,
                badgeBg = Color(0xFF059669)
            ),
            ToolGridItem(
                id = "site_utilities",
                title = "Site Utilities Calculator",
                subtitle = "GPS, camber & workdays",
                icon = Icons.Default.Explore,
                badgeBg = Color(0xFF1D4ED8)
            )
        )
    }

    val filteredTools = remember(searchQuery, gridTools) {
        if (searchQuery.isBlank()) gridTools
        else gridTools.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }

    val pageBackground = if (isDark) Color(0xFF0A0E17) else Color(0xFFF8FAFC)
    val headerTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .statusBarsPadding()
    ) {
        // Minimalist Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = {
                        if (activeToolId != null) {
                            activeToolId = null
                        } else {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("tools_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = headerTextColor
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                val currentTitle = if (activeToolId == null) {
                    "Tools"
                } else {
                    gridTools.find { it.id == activeToolId }?.title ?: "Tools"
                }
                Text(
                    text = currentTitle,
                    color = headerTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (activeToolId == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) searchQuery = ""
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("tools_search_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search Tools",
                            tint = headerTextColor
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setTab(AppTab.SMART_NOTES) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("tools_notes_shortcut_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Site Activity DPR",
                            tint = headerTextColor
                        )
                    }
                }
            }
        }

        if (activeToolId == null && isSearchExpanded) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search calculators & registers...", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = subTextColor)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = subTextColor)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ToolColors.cardBg,
                    unfocusedContainerColor = ToolColors.cardBg,
                    focusedBorderColor = ToolColors.accentBlue,
                    unfocusedBorderColor = ToolColors.border
                )
            )
        }

        if (activeToolId == null) {
            // Responsive Tools Grid using BoxWithConstraints
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                val columns = when {
                    maxWidth < 420.dp -> 2
                    maxWidth < 660.dp -> 3
                    else -> 4
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val rows = filteredTools.chunked(columns)
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { tool ->
                                ToolsResponsiveCard(
                                    item = tool,
                                    isDark = isDark,
                                    onClick = { activeToolId = tool.id },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag("tool_card_${tool.id}")
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    // Bottom padding so floating bottom navigation bar never overlaps cards
                    Spacer(modifier = Modifier.height(92.dp))
                }
            }
        } else {
            // Strict 1-to-1 Tool-to-Screen Routing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (activeToolId) {
                    "chainage_calc" -> ChainageCalculatorScreen(context)
                    "distance_gradient_calc" -> DistanceGradientCalculatorScreen(context)
                    "area_calc" -> AreaCalculatorScreen(context)
                    "volume_calc" -> VolumeCalculatorScreen(context)
                    "level_calc" -> LevelCalculatorScreen(context)
                    "curve_calc" -> CurveCalculatorScreen(context)
                    "steel_calc" -> SteelCalculatorScreen(context)
                    "concrete_calc" -> ConcreteCalculatorScreen(context)
                    "joint_calc" -> JointCalculatorScreen(context)
                    "pavement_layers" -> PavementLayersCalculatorScreen(context)
                    "unit_converter" -> UnitConverterScreen(context)
                    "qc_register" -> QualityControlRegistersView(viewModel, context)
                    "site_utilities" -> SiteUtilitiesView(viewModel, context)
                    else -> ChainageCalculatorScreen(context)
                }
            }
        }
    }
}

@Composable
private fun ToolsResponsiveCard(
    item: ToolGridItem,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardSurface = if (isDark) Color(0xFF151E32) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF263554) else Color(0xFFE2E8F0)
    val titleColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val chevronColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)

    Surface(
        modifier = modifier
            .heightIn(min = 78.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = cardSurface,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = if (isDark) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 11.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(item.badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    color = subtitleColor,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = chevronColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ============================================================================
// 11. PAVEMENT LAYERS CALCULATOR SCREEN (BC, DBM, WMM/GSB, TACK COAT, BITUMEN)
// ============================================================================
@Composable
fun PavementLayersCalculatorScreen(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. BC (Bituminous Concrete) Quantity & Truck Trips Calculator
        var bcLen by remember { mutableStateOf("500") }
        var bcWid by remember { mutableStateOf("7.0") }
        var bcThickMm by remember { mutableStateOf("40") }
        var bcDensity by remember { mutableStateOf("2.36") }
        var tipperCap by remember { mutableStateOf("25") }

        val bcl = bcLen.toDoubleOrNull() ?: 0.0
        val bcw = bcWid.toDoubleOrNull() ?: 0.0
        val bct = (bcThickMm.toDoubleOrNull() ?: 0.0) / 1000.0
        val bcd = bcDensity.toDoubleOrNull() ?: 2.36
        val cap = (tipperCap.toDoubleOrNull() ?: 25.0).coerceAtLeast(1.0)
        val bcArea = bcl * bcw
        val bcVol = bcArea * bct
        val bcTons = bcVol * bcd
        val bcTrips = ceil(bcTons / cap).toInt()

        ToolSectionCard(
            title = "BC (Bituminous Concrete) Quantity Calculator",
            subtitle = "MORTH Clause 507 • Area, Compacted Volume, Tonnage & Tipper Trips",
            icon = Icons.Default.LocalShipping
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = bcLen,
                    onValueChange = { bcLen = it },
                    label = "Stretch Length (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = bcWid,
                    onValueChange = { bcWid = it },
                    label = "Paving Width (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = bcThickMm,
                    onValueChange = { bcThickMm = it },
                    label = "Thickness (mm)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = bcDensity,
                    onValueChange = { bcDensity = it },
                    label = "Density (t/m³)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(
                value = tipperCap,
                onValueChange = { tipperCap = it },
                label = "Tipper / Dumper Capacity (MT)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(
                            Locale.US,
                            "BC Mix: %.2f MT | Vol: %.2f m³ | Area: %.1f m² | Tipper Trips: %d",
                            bcTons,
                            bcVol,
                            bcArea,
                            bcTrips
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
                            Text("Total BC Mix Required", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.2f MT", bcTons),
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Estimated Tipper Trips", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                "$bcTrips Trips",
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
                            String.format(Locale.US, "Paving Area: %,.1f m²", bcArea),
                            color = ToolColors.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            String.format(Locale.US, "Compacted Vol: %,.2f m³", bcVol),
                            color = ToolColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 2. DBM (Dense Bituminous Macadam) Quantity Calculator
        var dbmLen by remember { mutableStateOf("500") }
        var dbmWid by remember { mutableStateOf("7.0") }
        var dbmThickMm by remember { mutableStateOf("75") }
        var dbmDensity by remember { mutableStateOf("2.32") }

        val dVol = (dbmLen.toDoubleOrNull() ?: 0.0) *
            (dbmWid.toDoubleOrNull() ?: 0.0) *
            ((dbmThickMm.toDoubleOrNull() ?: 0.0) / 1000.0)
        val dTons = dVol * (dbmDensity.toDoubleOrNull() ?: 2.32)

        ToolSectionCard(
            title = "DBM (Dense Bituminous Macadam) Calculator",
            subtitle = "MORTH Clause 505 • Binder Course Mix Quantity",
            icon = Icons.Default.Layers
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = dbmLen,
                    onValueChange = { dbmLen = it },
                    label = "Length (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = dbmWid,
                    onValueChange = { dbmWid = it },
                    label = "Width (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = dbmThickMm,
                    onValueChange = { dbmThickMm = it },
                    label = "Thickness (mm)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = dbmDensity,
                    onValueChange = { dbmDensity = it },
                    label = "Density (t/m³)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(context, String.format(Locale.US, "DBM Tonnage: %.2f MT (%.2f m³)", dTons, dVol))
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Compacted Volume", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%,.2f m³", dVol),
                            color = ToolColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("DBM Mix Required", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%,.2f MT", dTons),
                            color = ToolColors.accentOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        // 3. WMM / GSB Base Layer Calculator
        var wmmLen by remember { mutableStateOf("500") }
        var wmmWid by remember { mutableStateOf("9.0") }
        var wmmThickMm by remember { mutableStateOf("250") }
        var wmmMdd by remember { mutableStateOf("2.24") }
        var wmmOmcPct by remember { mutableStateOf("6.0") }

        val wVol = (wmmLen.toDoubleOrNull() ?: 0.0) *
            (wmmWid.toDoubleOrNull() ?: 0.0) *
            ((wmmThickMm.toDoubleOrNull() ?: 0.0) / 1000.0)
        val wDryTons = wVol * (wmmMdd.toDoubleOrNull() ?: 2.24)
        val wWaterKL = wDryTons * ((wmmOmcPct.toDoubleOrNull() ?: 6.0) / 100.0)
        val wWetTons = wDryTons + wWaterKL

        ToolSectionCard(
            title = "WMM / GSB Base Layer & OMC Water Calculator",
            subtitle = "MORTH Clause 401 / 406 • Dry Aggregate, OMC Water & Wet Mix",
            icon = Icons.Default.Terrain
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = wmmLen,
                    onValueChange = { wmmLen = it },
                    label = "Length (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = wmmWid,
                    onValueChange = { wmmWid = it },
                    label = "Width (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = wmmThickMm,
                    onValueChange = { wmmThickMm = it },
                    label = "Thickness (mm)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = wmmMdd,
                    onValueChange = { wmmMdd = it },
                    label = "Lab MDD (g/cc)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(
                value = wmmOmcPct,
                onValueChange = { wmmOmcPct = it },
                label = "Optimum Moisture Content OMC (%)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(Locale.US, "WMM Wet Mix: %.2f MT | Dry: %.2f MT | Water: %.1f KL", wWetTons, wDryTons, wWaterKL)
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Wet Mix", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.2f MT", wWetTons),
                                color = ToolColors.accentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Dry Aggregate", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.2f MT", wDryTons),
                                color = ToolColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Water Required", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%,.1f KL", wWaterKL),
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. Tack Coat & Prime Coat Emulsion Calculator
        var tackLen by remember { mutableStateOf("500") }
        var tackWid by remember { mutableStateOf("7.0") }
        var tackRate by remember { mutableStateOf("0.25") } // kg/sqm

        val tArea = (tackLen.toDoubleOrNull() ?: 0.0) * (tackWid.toDoubleOrNull() ?: 0.0)
        val tRate = tackRate.toDoubleOrNull() ?: 0.25
        val tKg = tArea * tRate
        val tTons = tKg / 1000.0
        val tDrums = ceil(tKg / 200.0).toInt()

        ToolSectionCard(
            title = "Tack Coat / Prime Coat Emulsion Calculator",
            subtitle = "MORTH Clause 502 / 503 • Spray Rate (kg/m²) & Drum Count",
            icon = Icons.Default.WaterDrop
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = tackLen,
                    onValueChange = { tackLen = it },
                    label = "Length (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = tackWid,
                    onValueChange = { tackWid = it },
                    label = "Width (m)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToolTextField(
                value = tackRate,
                onValueChange = { tackRate = it },
                label = "Application Spray Rate (kg/m²)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "0.20 (Bituminous)" to "0.20",
                    "0.25 (Granular)" to "0.25",
                    "0.60 (Prime)" to "0.60"
                ).forEach { (label, rateVal) ->
                    ToolChoiceChip(
                        selected = tackRate == rateVal,
                        label = label,
                        onClick = { tackRate = rateVal },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(Locale.US, "Emulsion: %.1f kg (%.3f MT) | Area: %.1f m² | Drums: %d", tKg, tTons, tArea, tDrums)
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spray Area", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%,.1f m²", tArea),
                            color = ToolColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Emulsion Needed", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%,.1f kg", tKg),
                            color = ToolColors.accentBlue,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("200kg Drums", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            "$tDrums Drums",
                            color = ToolColors.accentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // 5. Bitumen Binder Content Calculator
        var totalMixTons by remember { mutableStateOf("330") }
        var binderPct by remember { mutableStateOf("5.4") }
        val mixT = totalMixTons.toDoubleOrNull() ?: 0.0
        val bPct = binderPct.toDoubleOrNull() ?: 5.4
        val pureBitumenTons = mixT * (bPct / 100.0)
        val aggregateTons = (mixT - pureBitumenTons).coerceAtLeast(0.0)

        ToolSectionCard(
            title = "Bitumen Binder Consumption Calculator",
            subtitle = "VG-30 / VG-40 / PMB-40 Binder vs Aggregate Breakdown by JMF %",
            icon = Icons.Default.Science
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = totalMixTons,
                    onValueChange = { totalMixTons = it },
                    label = "Total HMP Mix (MT)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = binderPct,
                    onValueChange = { binderPct = it },
                    label = "JMF Bitumen Content (%)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(Locale.US, "Bitumen Binder: %.2f MT | Aggregate: %.2f MT", pureBitumenTons, aggregateTons)
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pure Bitumen Binder", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.2f MT (%,.0f kg)", pureBitumenTons, pureBitumenTons * 1000),
                            color = ToolColors.accentBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Mineral Aggregate", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.2f MT", aggregateTons),
                            color = ToolColors.accentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

// ============================================================================
// 12. QUALITY CONTROL & INSPECTION REGISTERS VIEW (FIXED LAYOUT & NO OVERLAP)
// ============================================================================
@Composable
fun QualityControlRegistersView(viewModel: MainViewModel, context: Context) {
    val allQcRecords by viewModel.allQcRecords.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val qcTabs = listOf(
        "BC Temp" to "BC_TEMP",
        "Glass Grid" to "GLASS_GRID",
        "Tack Coat" to "TACK_COAT",
        "Pre-Survey" to "PRE_SURVEY",
        "Site Obs" to "SITE_OBS",
        "NCR Tracker" to "NCR"
    )
    var selectedQcTab by remember { mutableStateOf("BC_TEMP") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Clean 3x2 grid of QC Register Tabs
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            qcTabs.chunked(3).forEach { rowTabs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTabs.forEach { (label, code) ->
                        ToolChoiceChip(
                            selected = selectedQcTab == code,
                            label = label,
                            onClick = { selectedQcTab = code },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        when (selectedQcTab) {
            "BC_TEMP" -> BcTemperatureRegisterForm(viewModel, context)
            "GLASS_GRID" -> GlassGridInspectionForm(viewModel, context)
            "TACK_COAT" -> TackCoatVerificationForm(viewModel, context)
            "PRE_SURVEY" -> PreConstructionSurveyForm(viewModel, context)
            "SITE_OBS" -> SiteObservationLogForm(viewModel, context)
            "NCR" -> NcrQualityTrackerForm(viewModel, context)
        }

        // Saved Records History for active tab
        val filteredRecords = remember(allQcRecords, selectedQcTab) {
            allQcRecords.filter { it.type == selectedQcTab }
        }

        ToolSectionCard(
            title = "Saved Register Logs (${filteredRecords.size})",
            subtitle = "Persisted on-device for site audit & inspection review",
            icon = Icons.Default.History
        ) {
            if (filteredRecords.isEmpty()) {
                Text(
                    "No inspection records logged for this register yet.",
                    color = ToolColors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredRecords.forEach { record ->
                        SavedQcRecordItem(
                            record = record,
                            onCopy = {
                                copyToolResult(
                                    context,
                                    "[${record.type}] CH: ${record.chainage} (${record.side})\n${record.title}\n${record.detailsJson}\nStatus: ${record.status} | By: ${record.inspectorName}"
                                )
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    viewModel.deleteQcRecord(record)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

@Composable
private fun BcTemperatureRegisterForm(viewModel: MainViewModel, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var chainage by remember { mutableStateOf("177+500") }
    var side by remember { mutableStateOf("LHS") }
    var ambientTemp by remember { mutableStateOf("28") }
    var plantTemp by remember { mutableStateOf("162") }
    var arrivalTemp by remember { mutableStateOf("155") }
    var laydownTemp by remember { mutableStateOf("148") }
    var rollingTemp by remember { mutableStateOf("135") }
    var finishTemp by remember { mutableStateOf("90") }
    var inspector by remember { mutableStateOf("Quality Inspector") }

    val layT = laydownTemp.toDoubleOrNull() ?: 0.0
    val rollT = rollingTemp.toDoubleOrNull() ?: 0.0
    val arrT = arrivalTemp.toDoubleOrNull() ?: 0.0
    // MORTH Table 500-16 compliance: Laydown >= 130°C, Rolling >= 100°C, Arrival <= 175°C
    val isPass = layT in 130.0..170.0 && rollT >= 100.0 && arrT <= 175.0
    val statusText = if (isPass) "PASS" else "FAIL - TEMP OUT OF SPEC"

    ToolSectionCard(
        title = "BC Laying & Rolling Temperature Register",
        subtitle = "MORTH Clause 505 / 507 Permissible Temperature Check",
        icon = Icons.Default.Thermostat
    ) {
        ToolTextField(
            value = chainage,
            onValueChange = { chainage = it },
            label = "Location Chainage",
            modifier = Modifier.fillMaxWidth(),
            isChainage = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("LHS", "RHS", "BHS").forEach { s ->
                ToolChoiceChip(
                    selected = side == s,
                    label = s,
                    onClick = { side = s },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 2-column rows so labels never wrap or clip!
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTextField(
                value = ambientTemp,
                onValueChange = { ambientTemp = it },
                label = "Ambient Temp (°C)",
                modifier = Modifier.weight(1f)
            )
            ToolTextField(
                value = plantTemp,
                onValueChange = { plantTemp = it },
                label = "Plant Mix (°C)",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTextField(
                value = arrivalTemp,
                onValueChange = { arrivalTemp = it },
                label = "Site Arrival (°C)",
                modifier = Modifier.weight(1f)
            )
            ToolTextField(
                value = laydownTemp,
                onValueChange = { laydownTemp = it },
                label = "Laydown (°C)",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTextField(
                value = rollingTemp,
                onValueChange = { rollingTemp = it },
                label = "Breakdown Roll (°C)",
                modifier = Modifier.weight(1f)
            )
            ToolTextField(
                value = finishTemp,
                onValueChange = { finishTemp = it },
                label = "Finish Roll (°C)",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ToolTextField(
            value = inspector,
            onValueChange = { inspector = it },
            label = "Quality Inspector Name",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text
        )

        Spacer(modifier = Modifier.height(12.dp))
        ResultBox {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("MORTH Compliance Check", color = ToolColors.textSecondary, fontSize = 11.sp)
                    Text(
                        if (isPass) "✓ PASS (Temp in Permissible Range)" else "⚠ FAIL (Temp Out of Range)",
                        color = if (isPass) ToolColors.accentGreen else ToolColors.accentRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.saveQcRecord(
                                QcRecordEntity(
                                    type = "BC_TEMP",
                                    projectId = "PRJ-01",
                                    dateString = todayDate,
                                    chainage = chainage,
                                    side = side,
                                    layer = "BC",
                                    title = "BC Temp Check • Laydown ${laydownTemp}°C | Rolling ${rollingTemp}°C",
                                    detailsJson = "Ambient: ${ambientTemp}°C | Plant: ${plantTemp}°C | Arrival: ${arrivalTemp}°C | Laydown: ${laydownTemp}°C | Breakdown: ${rollingTemp}°C | Finish: ${finishTemp}°C",
                                    status = statusText,
                                    inspectorName = inspector
                                )
                            )
                            Toast.makeText(context, "BC Temperature Record Saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ToolColors.accentBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Temperature Record", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GlassGridInspectionForm(viewModel: MainViewModel, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var fromCh by remember { mutableStateOf("177+200") }
    var toCh by remember { mutableStateOf("177+350") }
    var side by remember { mutableStateOf("LHS") }
    var rollWidth by remember { mutableStateOf("1.5") }
    var longOverlapMm by remember { mutableStateOf("100") }
    var transOverlapMm by remember { mutableStateOf("150") }
    var surfaceClean by remember { mutableStateOf(true) }
    var tensionOk by remember { mutableStateOf(true) }
    var tackCured by remember { mutableStateOf(true) }

    val len = abs(parseChainageToMeters(toCh) - parseChainageToMeters(fromCh))
    val wid = rollWidth.toDoubleOrNull() ?: 1.5
    val netArea = len * wid
    val grossAreaWithOverlap = netArea * 1.08
    val allPassed = surfaceClean && tensionOk && tackCured

    ToolSectionCard(
        title = "Glass Grid Laying Inspection Checklist",
        subtitle = "Fiberglass Geogrid Interlayer Area, Overlap & Tension Verification",
        icon = Icons.Default.GridOn
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTextField(
                value = fromCh,
                onValueChange = { fromCh = it },
                label = "From CH",
                modifier = Modifier.weight(1f),
                isChainage = true
            )
            ToolTextField(
                value = toCh,
                onValueChange = { toCh = it },
                label = "To CH",
                modifier = Modifier.weight(1f),
                isChainage = true
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTextField(
                value = rollWidth,
                onValueChange = { rollWidth = it },
                label = "Width (m)",
                modifier = Modifier.weight(1f)
            )
            ToolTextField(
                value = longOverlapMm,
                onValueChange = { longOverlapMm = it },
                label = "Long. Overlap (mm)",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ToolTextField(
            value = transOverlapMm,
            onValueChange = { transOverlapMm = it },
            label = "Transverse Overlap (mm)",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolChoiceChip(
                selected = surfaceClean,
                label = "✓ Surface Clean",
                onClick = { surfaceClean = !surfaceClean },
                modifier = Modifier.weight(1f)
            )
            ToolChoiceChip(
                selected = tensionOk,
                label = "✓ No Wrinkles",
                onClick = { tensionOk = !tensionOk },
                modifier = Modifier.weight(1f)
            )
            ToolChoiceChip(
                selected = tackCured,
                label = "✓ Tack Cured",
                onClick = { tackCured = !tackCured },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        ResultBox {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            String.format(Locale.US, "Net Area: %.1f m²", netArea),
                            color = ToolColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            String.format(Locale.US, "Gross (+8%% Overlap): %.1f m²", grossAreaWithOverlap),
                            color = ToolColors.accentBlue,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        if (allPassed) "APPROVED" else "HOLD",
                        color = if (allPassed) ToolColors.accentGreen else ToolColors.accentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.saveQcRecord(
                                QcRecordEntity(
                                    type = "GLASS_GRID",
                                    projectId = "PRJ-01",
                                    dateString = todayDate,
                                    chainage = "$fromCh to $toCh",
                                    side = side,
                                    layer = "Glass Grid",
                                    title = String.format(Locale.US, "Glass Grid %.1f m² (Gross %.1f m²)", netArea, grossAreaWithOverlap),
                                    detailsJson = "Overlap L:${longOverlapMm}mm T:${transOverlapMm}mm | Clean:$surfaceClean | Tension:$tensionOk | TackCured:$tackCured",
                                    status = if (allPassed) "APPROVED" else "HOLD",
                                    inspectorName = "Site QC Engineer"
                                )
                            )
                            Toast.makeText(context, "Glass Grid Inspection Saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ToolColors.accentBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log Glass Grid Inspection", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TackCoatVerificationForm(viewModel: MainViewModel, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var chainage by remember { mutableStateOf("178+000") }
    var targetRate by remember { mutableStateOf("0.25") }
    var trayWeightBeforeG by remember { mutableStateOf("450") }
    var trayWeightAfterG by remember { mutableStateOf("514") }
    var trayAreaSqm by remember { mutableStateOf("0.25") } // 50cm x 50cm pad

    val w1 = trayWeightBeforeG.toDoubleOrNull() ?: 0.0
    val w2 = trayWeightAfterG.toDoubleOrNull() ?: 0.0
    val area = (trayAreaSqm.toDoubleOrNull() ?: 0.25).coerceAtLeast(0.01)
    val actualRateKgSqm = ((w2 - w1) / 1000.0) / area
    val target = targetRate.toDoubleOrNull() ?: 0.25
    val tolerancePass = abs(actualRateKgSqm - target) <= 0.03

    ToolSectionCard(
        title = "Tack Coat Rate of Spread Field Test",
        subtitle = "Pad / Tray Test Verification (g → kg/m²)",
        icon = Icons.Default.WaterDrop
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTextField(
                value = chainage,
                onValueChange = { chainage = it },
                label = "Test Chainage",
                modifier = Modifier.weight(1f),
                isChainage = true
            )
            ToolTextField(
                value = targetRate,
                onValueChange = { targetRate = it },
                label = "Target Rate (kg/m²)",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTextField(
                value = trayWeightBeforeG,
                onValueChange = { trayWeightBeforeG = it },
                label = "Empty Tray Wt (g)",
                modifier = Modifier.weight(1f)
            )
            ToolTextField(
                value = trayWeightAfterG,
                onValueChange = { trayWeightAfterG = it },
                label = "Sprayed Tray Wt (g)",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ToolTextField(
            value = trayAreaSqm,
            onValueChange = { trayAreaSqm = it },
            label = "Test Tray Area (m²)",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        ResultBox {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        String.format(Locale.US, "Actual Rate: %.3f kg/m²", actualRateKgSqm),
                        color = ToolColors.accentBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        if (tolerancePass) "✓ Within ±0.03 kg/m² Tolerance" else "⚠ Adjust Sprayer Pressure/Speed",
                        color = if (tolerancePass) ToolColors.accentGreen else ToolColors.accentOrange,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.saveQcRecord(
                                QcRecordEntity(
                                    type = "TACK_COAT",
                                    projectId = "PRJ-01",
                                    dateString = todayDate,
                                    chainage = chainage,
                                    side = "Full Width",
                                    layer = "Tack Coat",
                                    title = String.format(Locale.US, "Tack Coat Spread: %.3f kg/m² (Target %.2f)", actualRateKgSqm, target),
                                    detailsJson = "Tray Wt Before: ${trayWeightBeforeG}g | After: ${trayWeightAfterG}g | Tray Area: ${trayAreaSqm}m²",
                                    status = if (tolerancePass) "PASS" else "RE-ADJUST",
                                    inspectorName = "Field Lab Tech"
                                )
                            )
                            Toast.makeText(context, "Tack Coat Test Saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ToolColors.accentBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Field Test", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PreConstructionSurveyForm(viewModel: MainViewModel, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var chainage by remember { mutableStateOf("179+000") }
    var benchmarkId by remember { mutableStateOf("TBM-18") }
    var designRl by remember { mutableStateOf("242.550") }
    var actualRl by remember { mutableStateOf("242.555") }
    var remarks by remember { mutableStateOf("ROW cleared, toe line marked both sides") }

    val dRl = designRl.toDoubleOrNull() ?: 0.0
    val aRl = actualRl.toDoubleOrNull() ?: 0.0
    val diffMm = (aRl - dRl) * 1000.0

    ToolSectionCard(
        title = "Pre-Construction Joint Survey Checklist",
        subtitle = "TBM Level Verification, OGL Check & ROW Clearance Log",
        icon = Icons.Default.CompassCalibration
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTextField(
                value = chainage,
                onValueChange = { chainage = it },
                label = "Chainage",
                modifier = Modifier.weight(1f),
                isChainage = true
            )
            ToolTextField(
                value = benchmarkId,
                onValueChange = { benchmarkId = it },
                label = "TBM / Pillar ID",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Text
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTextField(
                value = designRl,
                onValueChange = { designRl = it },
                label = "Design RL (m)",
                modifier = Modifier.weight(1f)
            )
            ToolTextField(
                value = actualRl,
                onValueChange = { actualRl = it },
                label = "Observed RL (m)",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ToolTextField(
            value = remarks,
            onValueChange = { remarks = it },
            label = "Survey & ROW Clearance Notes",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text
        )
        Spacer(modifier = Modifier.height(10.dp))
        ResultBox {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    Text(
                        String.format(Locale.US, "Level Difference: %+.1f mm", diffMm),
                        color = if (abs(diffMm) <= 10.0) ToolColors.accentGreen else ToolColors.accentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        if (abs(diffMm) <= 10.0) "✓ Within ±10mm Permissible Tolerance" else "⚠ Exceeds ±10mm Level Tolerance",
                        color = ToolColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.saveQcRecord(
                                QcRecordEntity(
                                    type = "PRE_SURVEY",
                                    projectId = "PRJ-01",
                                    dateString = todayDate,
                                    chainage = chainage,
                                    side = benchmarkId,
                                    layer = "Survey",
                                    title = String.format(Locale.US, "Joint Survey @ %s (ΔRL: %+.1f mm)", benchmarkId, diffMm),
                                    detailsJson = "Design RL: ${designRl}m | Observed RL: ${actualRl}m | Notes: $remarks",
                                    status = if (abs(diffMm) <= 10.0) "VERIFIED" else "CHECK LEVEL",
                                    inspectorName = "Chief Surveyor"
                                )
                            )
                            Toast.makeText(context, "Survey Record Saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ToolColors.accentBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Survey Log", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SiteObservationLogForm(viewModel: MainViewModel, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var chainage by remember { mutableStateOf("180+100") }
    var category by remember { mutableStateOf("Safety / Traffic") }
    var observation by remember { mutableStateOf("Provide reflective cones and flagman at diversion zone") }
    var priority by remember { mutableStateOf("HIGH") }

    ToolSectionCard(
        title = "Site Observation & Walkthrough Register",
        subtitle = "Record field observations, safety directives & drainage checks",
        icon = Icons.Default.Visibility
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolTextField(
                value = chainage,
                onValueChange = { chainage = it },
                label = "Chainage",
                modifier = Modifier.weight(1f),
                isChainage = true
            )
            ToolTextField(
                value = category,
                onValueChange = { category = it },
                label = "Category",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Text
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ToolTextField(
            value = observation,
            onValueChange = { observation = it },
            label = "Observation & Corrective Instruction",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { p ->
                ToolChoiceChip(
                    selected = priority == p,
                    label = p,
                    onClick = { priority = p },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                coroutineScope.launch {
                    viewModel.saveQcRecord(
                        QcRecordEntity(
                            type = "SITE_OBS",
                            projectId = "PRJ-01",
                            dateString = todayDate,
                            chainage = chainage,
                            side = category,
                            layer = category,
                            title = "[$priority] $category Observation",
                            detailsJson = observation,
                            status = priority,
                            inspectorName = "Resident Engineer"
                        )
                    )
                    Toast.makeText(context, "Site Observation Logged", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ToolColors.accentBlue,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Log Site Observation", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NcrQualityTrackerForm(viewModel: MainViewModel, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var chainage by remember { mutableStateOf("176+850") }
    var defectTitle by remember { mutableStateOf("Localised segregation & low rolling temp on RHS lane") }
    var rootCause by remember { mutableStateOf("Paver stoppage due to delayed tipper arrival") }
    var correctiveAction by remember { mutableStateOf("Mill out defective 15m patch and relay fresh BC") }
    var status by remember { mutableStateOf("OPEN") }

    ToolSectionCard(
        title = "Non-Conformance Report (NCR) & Quality Issue Tracker",
        subtitle = "Track defective work, root cause & rectification status",
        icon = Icons.Default.ReportProblem
    ) {
        ToolTextField(
            value = chainage,
            onValueChange = { chainage = it },
            label = "Affected Chainage",
            modifier = Modifier.fillMaxWidth(),
            isChainage = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        ToolTextField(
            value = defectTitle,
            onValueChange = { defectTitle = it },
            label = "Non-Conformance Description",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text
        )
        Spacer(modifier = Modifier.height(8.dp))
        ToolTextField(
            value = rootCause,
            onValueChange = { rootCause = it },
            label = "Root Cause",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text
        )
        Spacer(modifier = Modifier.height(8.dp))
        ToolTextField(
            value = correctiveAction,
            onValueChange = { correctiveAction = it },
            label = "Corrective / Preventive Action",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("OPEN", "RECTIFYING", "CLOSED").forEach { st ->
                ToolChoiceChip(
                    selected = status == st,
                    label = st,
                    onClick = { status = st },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                coroutineScope.launch {
                    viewModel.saveQcRecord(
                        QcRecordEntity(
                            type = "NCR",
                            projectId = "PRJ-01",
                            dateString = todayDate,
                            chainage = chainage,
                            side = "QA/QC",
                            layer = "NCR",
                            title = "NCR: $defectTitle",
                            detailsJson = "Root Cause: $rootCause | Action: $correctiveAction",
                            status = status,
                            inspectorName = "QA/QC Manager"
                        )
                    )
                    Toast.makeText(context, "NCR Record Saved", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ToolColors.accentRed,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Register NCR Entry", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SavedQcRecordItem(
    record: QcRecordEntity,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val statusColor = when (record.status) {
        "PASS", "APPROVED", "VERIFIED", "CLOSED" -> ToolColors.accentGreen
        "OPEN", "FAIL - TEMP OUT OF SPEC", "CRITICAL" -> ToolColors.accentRed
        else -> ToolColors.accentOrange
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ToolColors.cardBgElevated,
        border = BorderStroke(1.dp, ToolColors.border)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ToolColors.accentBlue.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "CH ${record.chainage}",
                            color = ToolColors.accentBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = record.status,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Row {
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = ToolColors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = ToolColors.accentRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                record.title,
                color = ToolColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                record.detailsJson,
                color = ToolColors.textSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${dateFormat.format(Date(record.timestamp))} • By ${record.inspectorName}",
                color = ToolColors.textSecondary.copy(alpha = 0.75f),
                fontSize = 10.sp
            )
        }
    }
}

// ============================================================================
// 13. SITE UTILITIES VIEW (GPS CONVERTER, CAMBER, WORKING DAYS)
// ============================================================================
@Composable
fun SiteUtilitiesView(viewModel: MainViewModel, context: Context) {
    val locState by viewModel.locationState.collectAsStateWithLifecycle()
    val liveLat = locState.point?.latitude ?: 0.0
    val liveLon = locState.point?.longitude ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Live GPS Coordinate & UTM Zone Converter
        var latInput by remember(liveLat) {
            mutableStateOf(
                if (liveLat != 0.0) String.format(Locale.US, "%.6f", liveLat)
                else "17.385044"
            )
        }
        var lonInput by remember(liveLon) {
            mutableStateOf(
                if (liveLon != 0.0) String.format(Locale.US, "%.6f", liveLon)
                else "78.486671"
            )
        }

        val lat = latInput.toDoubleOrNull() ?: 0.0
        val lon = lonInput.toDoubleOrNull() ?: 0.0
        val dmsString = formatCoordinatesDMS(lat, lon)
        val utmZone = floor((lon + 180.0) / 6.0).toInt() + 1
        val hemisphere = if (lat >= 0) "N" else "S"

        ToolSectionCard(
            title = "GPS Coordinates & Geodetic Converter",
            subtitle = "Convert Decimal Degrees (DD) to DMS & UTM Zone",
            icon = Icons.Default.GpsFixed
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = latInput,
                    onValueChange = { latInput = it },
                    label = "Latitude (DD)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = lonInput,
                    onValueChange = { lonInput = it },
                    label = "Longitude (DD)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(context, "DD: $lat, $lon | DMS: $dmsString | UTM Zone: $utmZone$hemisphere")
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "DMS: $dmsString",
                        color = ToolColors.accentBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "UTM Zone: $utmZone$hemisphere • Live Accuracy: ±${locState.accuracy.toInt()}m",
                        color = ToolColors.accentGreen,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. Camber / Cross-Fall & Superelevation Calculator
        var carriagewayWidth by remember { mutableStateOf("7.0") }
        var camberPercent by remember { mutableStateOf("2.5") }
        var isTwoWayCrown by remember { mutableStateOf(true) }

        val cw = carriagewayWidth.toDoubleOrNull() ?: 7.0
        val cPct = camberPercent.toDoubleOrNull() ?: 2.5
        val effectiveWidth = if (isTwoWayCrown) cw / 2.0 else cw
        val crownDropMeters = effectiveWidth * (cPct / 100.0)
        val crownDropMm = crownDropMeters * 1000.0

        ToolSectionCard(
            title = "Camber & Cross-Slope Calculator",
            subtitle = "Calculate crown height / edge drop for BC, DBM, WMM & earthen shoulder",
            icon = Icons.Default.Architecture
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = carriagewayWidth,
                    onValueChange = { carriagewayWidth = it },
                    label = "Carriageway Width (m)",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = camberPercent,
                    onValueChange = { camberPercent = it },
                    label = "Camber / Cross-Fall (%)",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolChoiceChip(
                    selected = isTwoWayCrown,
                    label = "Center Crown (B/2)",
                    onClick = { isTwoWayCrown = true },
                    modifier = Modifier.weight(1f)
                )
                ToolChoiceChip(
                    selected = !isTwoWayCrown,
                    label = "Unidirectional (Full B)",
                    onClick = { isTwoWayCrown = false },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("2.0% (PQC)" to "2.0", "2.5% (BC/DBM)" to "2.5", "3.0% (Shoulder)" to "3.0").forEach { (lbl, v) ->
                    ToolChoiceChip(
                        selected = camberPercent == v,
                        label = lbl,
                        onClick = { camberPercent = v },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            ResultBox(
                onCopy = {
                    copyToolResult(
                        context,
                        String.format(Locale.US, "Camber Drop: %.1f mm (%.3f m) over %.2f m", crownDropMm, crownDropMeters, effectiveWidth)
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Effective Slope Width", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.2f m", effectiveWidth),
                            color = ToolColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Vertical Drop / Raise", color = ToolColors.textSecondary, fontSize = 11.sp)
                        Text(
                            String.format(Locale.US, "%.0f mm (%.3f m)", crownDropMm, crownDropMeters),
                            color = ToolColors.accentGreen,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // 3. Working Days & Milestone Estimator (Fixed spacing between columns)
        var calendarDays by remember { mutableStateOf("60") }
        var rainStoppageDays by remember { mutableStateOf("8") }
        var maintenanceDays by remember { mutableStateOf("4") }
        var dailyTargetM by remember { mutableStateOf("250") }

        val cal = calendarDays.toIntOrNull() ?: 0
        val rain = rainStoppageDays.toIntOrNull() ?: 0
        val maint = maintenanceDays.toIntOrNull() ?: 0
        val netWorkingDays = (cal - rain - maint).coerceAtLeast(0)
        val dailyM = dailyTargetM.toDoubleOrNull() ?: 0.0
        val achievableKm = (netWorkingDays * dailyM) / 1000.0

        ToolSectionCard(
            title = "Highway Working-Day & Deadline Calculator",
            subtitle = "Estimate effective working days & achievable paving output",
            icon = Icons.Default.EventAvailable
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = calendarDays,
                    onValueChange = { calendarDays = it },
                    label = "Calendar Days",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = rainStoppageDays,
                    onValueChange = { rainStoppageDays = it },
                    label = "Rain / Stoppage Days",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTextField(
                    value = maintenanceDays,
                    onValueChange = { maintenanceDays = it },
                    label = "Plant Maint. Days",
                    modifier = Modifier.weight(1f)
                )
                ToolTextField(
                    value = dailyTargetM,
                    onValueChange = { dailyTargetM = it },
                    label = "Daily Progress (m/day)",
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Net Working Days", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                "$netWorkingDays Productive Days",
                                color = ToolColors.accentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Achievable Stretch", color = ToolColors.textSecondary, fontSize = 11.sp)
                            Text(
                                String.format(Locale.US, "%.2f km", achievableKm),
                                color = ToolColors.accentBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ToolColors.border)
                    Text(
                        "Non-Working / Stoppage Days: ${rain + maint} Days",
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
// REUSABLE UI BUILDING BLOCKS & HELPERS
// ============================================================================
@Composable
internal fun ToolSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ToolColors.cardBg,
        border = BorderStroke(1.dp, ToolColors.border),
        shadowElevation = if (isAppDarkTheme()) 0.dp else 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ToolColors.accentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = ToolColors.accentBlue,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = ToolColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        subtitle,
                        color = ToolColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = ToolColors.border)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun ToolTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    isChainage: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }

    val completeChainageFormatting = {
        if (isChainage && value.isNotBlank()) {
            val formatted = formatChainageInputOnComplete(value)
            if (formatted != value) {
                onValueChange(formatted)
            }
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { newVal ->
            if (isChainage) {
                onValueChange(sanitizeRawChainageTyping(newVal))
            } else {
                onValueChange(newVal)
            }
        },
        label = {
            Text(
                label,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isChainage) KeyboardType.Phone else keyboardType,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                completeChainageFormatting()
                focusManager.clearFocus()
            }
        ),
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                hadFocus = true
            } else if (hadFocus) {
                hadFocus = false
                completeChainageFormatting()
            }
        },
        shape = RoundedCornerShape(10.dp),
        textStyle = LocalTextStyle.current.copy(
            color = ToolColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ToolColors.accentBlue,
            unfocusedBorderColor = ToolColors.border,
            focusedLabelColor = ToolColors.accentBlue,
            unfocusedLabelColor = ToolColors.textSecondary,
            cursorColor = ToolColors.accentBlue,
            focusedContainerColor = ToolColors.cardBgElevated,
            unfocusedContainerColor = ToolColors.cardBgElevated
        )
    )
}

@Composable
internal fun ResultBox(
    onCopy: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ToolColors.cardBgElevated,
        border = BorderStroke(1.dp, ToolColors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                content = content
            )
            if (onCopy != null) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Result",
                        tint = ToolColors.accentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

internal fun sanitizeRawChainageTyping(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '+' || it == '.' }
    val firstPlus = filtered.indexOf('+')
    return if (firstPlus != -1) {
        val before = filtered.substring(0, firstPlus + 1)
        val after = filtered.substring(firstPlus + 1).replace("+", "")
        before + after
    } else {
        filtered
    }
}

internal fun formatChainageInputOnComplete(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.contains("+")) {
        val parts = trimmed.split("+")
        val km = parts.getOrNull(0)?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
        val mStr = parts.getOrNull(1)?.filter { it.isDigit() } ?: "0"
        val m = mStr.toLongOrNull() ?: 0L
        val totalMetres = km * 1000L + m
        return formatMetersToChainage(totalMetres.toDouble())
    }
    val digitsOnly = trimmed.filter { it.isDigit() }
    val totalM = digitsOnly.toLongOrNull() ?: return trimmed
    return formatMetersToChainage(totalM.toDouble())
}

internal fun parseChainageToMeters(chStr: String): Double {
    val trimmed = chStr.trim()
    if (trimmed.isEmpty()) return 0.0
    if (trimmed.contains("+")) {
        val parts = trimmed.split("+")
        val km = parts.getOrNull(0)?.filter { it.isDigit() }?.toDoubleOrNull() ?: 0.0
        val m = parts.getOrNull(1)?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull() ?: 0.0
        return km * 1000.0 + m
    }
    return trimmed.toDoubleOrNull() ?: 0.0
}

internal fun formatMetersToChainage(meters: Double): String {
    val totalM = meters.roundToInt().coerceAtLeast(0)
    val km = totalM / 1000
    val remM = totalM % 1000
    return String.format(Locale.US, "%d+%03d", km, remM)
}

private fun formatCoordinatesDMS(lat: Double, lon: Double): String {
    fun toDms(value: Double, posHem: Char, negHem: Char): String {
        val hem = if (value >= 0) posHem else negHem
        val absVal = abs(value)
        val deg = floor(absVal).toInt()
        val minFull = (absVal - deg) * 60.0
        val min = floor(minFull).toInt()
        val sec = (minFull - min) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\"%c", deg, min, sec, hem)
    }
    return "${toDms(lat, 'N', 'S')}  ${toDms(lon, 'E', 'W')}"
}
