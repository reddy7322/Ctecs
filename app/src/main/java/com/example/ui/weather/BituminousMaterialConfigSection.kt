package com.example.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BituminousBinderGrade
import com.example.model.BituminousMaterialConfig
import com.example.model.BituminousWorkCategory
import com.example.model.OtherBituminousWorkType
import com.example.model.WeatherIntelligenceSettings
import com.example.ui.theme.CtecsTheme
import java.util.Locale

/**
 * Applies a [BituminousMaterialConfig] update to [WeatherIntelligenceSettings], keeping
 * primary activity and legacy binder references synchronized immediately.
 */
internal fun applyBituminousConfigUpdate(
    current: WeatherIntelligenceSettings,
    updatedConfig: BituminousMaterialConfig
): WeatherIntelligenceSettings {
    return current.copy(
        primaryActivity = updatedConfig.mappedHighwayActivity(),
        bituminousConfig = updatedConfig,
        contractSpec = current.contractSpec.copy(
            bcBinderType = updatedConfig.bcBinderGrade.toLegacyBinderType(),
            dbmBinderType = updatedConfig.dbmBinderGrade.toLegacyBinderType()
        )
    )
}

/**
 * Compact, borderless animated Bituminous Material & Binder Configuration section
 * hosted exclusively inside the Weather Page Settings popup.
 */
@Composable
internal fun BituminousMaterialConfigSection(
    config: BituminousMaterialConfig,
    animationsEnabled: Boolean,
    onConfigChange: (BituminousMaterialConfig) -> Unit,
    onResetDefault: () -> Unit
) {
    val c = CtecsTheme.colors
    val animMs = if (animationsEnabled) 180 else 0

    var categoryExpanded by remember { mutableStateOf(false) }
    var otherWorkExpanded by remember { mutableStateOf(false) }
    var binderExpanded by remember { mutableStateOf(false) }

    val applicableBinders = remember(config.workCategory, config.otherWorkType) {
        BituminousMaterialConfig.applicableBindersFor(config.workCategory, config.otherWorkType)
    }

    var customTempDraft by remember(config.customMinSurfaceTempC) {
        mutableStateOf(
            config.customMinSurfaceTempC?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.1f", it)
            }.orEmpty()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ws_bituminous_material_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header + Reset to Default action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PopupSectionHeader("BITUMINOUS MATERIAL CONFIGURATION")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.secondarySurface)
                    .clickable {
                        categoryExpanded = false
                        otherWorkExpanded = false
                        binderExpanded = false
                        onResetDefault()
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("ws_bituminous_reset_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = "Reset bituminous configuration to default",
                    tint = c.accent,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Reset to Default",
                    color = c.accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        // A. Work Category Selection Dropdown
        CompactAnimatedDropdown(
            label = "Work Category",
            selectedDisplay = config.workCategory.displayName,
            expanded = categoryExpanded,
            animMs = animMs,
            dropdownTag = "ws_bituminous_category_dropdown",
            onToggle = {
                categoryExpanded = !categoryExpanded
                if (categoryExpanded) {
                    otherWorkExpanded = false
                    binderExpanded = false
                }
            },
            items = BituminousWorkCategory.entries,
            selectedItem = config.workCategory,
            itemTitle = { it.displayName },
            itemSubtitle = { cat ->
                when (cat) {
                    BituminousWorkCategory.BC -> "Wearing course · MoRTH Cl. 507"
                    BituminousWorkCategory.DBM -> "Binder course · MoRTH Cl. 505"
                    BituminousWorkCategory.OTHER_BITUMINOUS -> "BM, SDBC, SMA, Tack, Prime, Surfacing"
                    BituminousWorkCategory.CUSTOM -> "Enter project-specific bituminous work"
                }
            },
            itemTag = { "ws_bituminous_cat_${it.name.lowercase(Locale.US)}" },
            onSelect = { selectedCat ->
                categoryExpanded = false
                onConfigChange(config.withCategoryChanged(selectedCat))
            }
        )

        // Conditional A2: Specific Activity when "Other Bituminous Works" is chosen
        AnimatedVisibility(
            visible = config.workCategory == BituminousWorkCategory.OTHER_BITUMINOUS,
            enter = expandVertically(tween(animMs)) + fadeIn(tween(animMs)),
            exit = shrinkVertically(tween(animMs)) + fadeOut(tween(animMs))
        ) {
            CompactAnimatedDropdown(
                label = "Bituminous Work Type",
                selectedDisplay = config.otherWorkType.displayName,
                expanded = otherWorkExpanded,
                animMs = animMs,
                dropdownTag = "ws_bituminous_other_dropdown",
                onToggle = {
                    otherWorkExpanded = !otherWorkExpanded
                    if (otherWorkExpanded) {
                        categoryExpanded = false
                        binderExpanded = false
                    }
                },
                items = OtherBituminousWorkType.entries,
                selectedItem = config.otherWorkType,
                itemTitle = { it.displayName },
                itemSubtitle = { it.referenceLabel },
                itemTag = { "ws_bituminous_other_${it.name.lowercase(Locale.US)}" },
                onSelect = { selectedOther ->
                    otherWorkExpanded = false
                    onConfigChange(config.withOtherWorkTypeChanged(selectedOther))
                }
            )
        }

        // Conditional A3: Custom Work Category Name
        AnimatedVisibility(
            visible = config.workCategory == BituminousWorkCategory.CUSTOM,
            enter = expandVertically(tween(animMs)) + fadeIn(tween(animMs)),
            exit = shrinkVertically(tween(animMs)) + fadeOut(tween(animMs))
        ) {
            CompactBorderlessInput(
                label = "Custom Work Category",
                value = config.customWorkCategoryName,
                placeholder = "e.g. Warm Mix BC, Mastic Asphalt",
                testTag = "ws_bituminous_custom_category_input",
                onValueChange = { text ->
                    onConfigChange(
                        config.copy(
                            customWorkCategoryName = text.take(48),
                            isCustomCategoryActive = true
                        )
                    )
                }
            )
        }

        // B. Binder / Bitumen Grade Selection Dropdown (Filtered by Category)
        CompactAnimatedDropdown(
            label = "Binder / Bitumen Grade",
            selectedDisplay = if (config.binderGrade == BituminousBinderGrade.OTHER_CUSTOM &&
                config.customBinderSpecification.isNotBlank()
            ) {
                "Custom: ${config.customBinderSpecification.trim()}"
            } else {
                config.binderGrade.displayName
            },
            expanded = binderExpanded,
            animMs = animMs,
            dropdownTag = "ws_bituminous_binder_dropdown",
            onToggle = {
                binderExpanded = !binderExpanded
                if (binderExpanded) {
                    categoryExpanded = false
                    otherWorkExpanded = false
                }
            },
            items = applicableBinders,
            selectedItem = config.binderGrade,
            itemTitle = { it.displayName },
            itemSubtitle = { grade ->
                when (grade) {
                    BituminousBinderGrade.PMB_VG_40 -> "Modified polymer binder · ≥15°C surface min (distinct from VG-40)"
                    BituminousBinderGrade.OTHER_CUSTOM -> "Enter custom JMF / project binder spec"
                    else -> grade.standardMinSurfaceTempC?.let { minT ->
                        val kind = when {
                            grade.isEmulsion -> "Emulsion"
                            grade.isModified -> "Modified"
                            else -> "Paving Grade"
                        }
                        "$kind · ≥${minT.toInt()}°C surface min"
                    } ?: "Project / JMF specification"
                }
            },
            itemTag = { "ws_bituminous_binder_${it.name.lowercase(Locale.US)}" },
            onSelect = { selectedBinder ->
                binderExpanded = false
                onConfigChange(config.withBinderChanged(selectedBinder))
            }
        )

        // Conditional B2: Custom Binder Specification & Optional Approved JMF Min Surface Temp
        val showCustomSpecInputs = config.binderGrade == BituminousBinderGrade.OTHER_CUSTOM ||
            config.workCategory == BituminousWorkCategory.CUSTOM
        AnimatedVisibility(
            visible = showCustomSpecInputs,
            enter = expandVertically(tween(animMs)) + fadeIn(tween(animMs)),
            exit = shrinkVertically(tween(animMs)) + fadeOut(tween(animMs))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (config.binderGrade == BituminousBinderGrade.OTHER_CUSTOM) {
                    CompactBorderlessInput(
                        label = "Custom Binder Specification",
                        value = config.customBinderSpecification,
                        placeholder = "e.g. PMB 76-10E, PG 70-10, CRMB-60",
                        testTag = "ws_bituminous_custom_binder_input",
                        onValueChange = { text ->
                            onConfigChange(config.copy(customBinderSpecification = text.take(40)))
                        }
                    )
                }
                CompactBorderlessInput(
                    label = "Approved JMF Min Surface Temp (°C, optional)",
                    value = customTempDraft,
                    placeholder = "Leave blank if unavailable",
                    keyboardType = KeyboardType.Decimal,
                    testTag = "ws_bituminous_custom_temp_input",
                    onValueChange = { raw ->
                        val sanitized = raw.filter { it.isDigit() || it == '.' }.take(5)
                        customTempDraft = sanitized
                        val parsed = sanitized.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..50.0 }
                        onConfigChange(config.copy(customMinSurfaceTempC = parsed))
                    }
                )
            }
        }

        // Inline Technical Note or Insufficient Specification Data indicator
        val isInsufficient = config.hasInsufficientSpecificationData
        val noteBg = if (isInsufficient) {
            c.warning.copy(alpha = if (c.isDark) 0.14f else 0.10f)
        } else {
            c.secondarySurface
        }
        val noteAccent = if (isInsufficient) c.warning else c.accent
        val noteText = if (isInsufficient) {
            "Insufficient Specification Data — enter approved JMF minimum surface temperature or follow Engineer-in-Charge instructions."
        } else {
            val minTempStr = config.effectiveMinSurfaceTempC?.let { "Min surface ≥${it.toInt()}°C. " }.orEmpty()
            "$minTempStr${config.binderGrade.viscosityNote}"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(noteBg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("ws_bituminous_spec_note"),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = if (isInsufficient) Icons.Rounded.WarningAmber else Icons.Rounded.Info,
                contentDescription = null,
                tint = noteAccent,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(14.dp)
            )
            Text(
                text = noteText,
                color = if (isInsufficient) c.warning else c.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.5.sp
            )
        }
    }
}

@Composable
private fun <T> CompactAnimatedDropdown(
    label: String,
    selectedDisplay: String,
    expanded: Boolean,
    animMs: Int,
    dropdownTag: String,
    onToggle: () -> Unit,
    items: List<T>,
    selectedItem: T,
    itemTitle: (T) -> String,
    itemSubtitle: (T) -> String,
    itemTag: (T) -> String,
    onSelect: (T) -> Unit
) {
    val c = CtecsTheme.colors
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(animMs),
        label = "${dropdownTag}_chevron"
    )

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            color = c.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.secondarySurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag(dropdownTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedDisplay,
                    color = c.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse $label" else "Expand $label",
                    tint = c.accent,
                    modifier = Modifier
                        .size(19.dp)
                        .rotate(chevronAngle)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(animMs)) + fadeIn(tween(animMs)),
                exit = shrinkVertically(tween(animMs)) + fadeOut(tween(animMs))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items.forEach { item ->
                        val isSelected = item == selectedItem
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 40.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (isSelected) c.accent.copy(alpha = if (c.isDark) 0.22f else 0.13f)
                                    else c.surface.copy(alpha = 0.72f)
                                )
                                .clickable { onSelect(item) }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                .testTag(itemTag(item)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = itemTitle(item),
                                    color = if (isSelected) c.accent else c.textPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = itemSubtitle(item),
                                    color = c.textMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = c.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactBorderlessInput(
    label: String,
    value: String,
    placeholder: String,
    testTag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    val c = CtecsTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = c.textSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.secondarySurface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = c.textMuted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(
                    color = c.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )
        }
    }
}
