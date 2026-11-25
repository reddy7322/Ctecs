package com.example.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.MachineryCatalogEntry
import com.example.model.MachineryCategory
import com.example.model.ModelAvailabilityStatus
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import java.util.Locale
import java.util.UUID

/**
 * Searchable cascading machinery selector dialog:
 * Category -> Manufacturer -> Model / Variant -> Verified Specifications.
 * Also supports adding custom equipment / new categories dynamically.
 */
@Composable
fun MachineryPickerDialog(
    catalog: List<MachineryCatalogEntry>,
    suggestedCategories: List<MachineryCategory>,
    onSelectMachine: (MachineryCatalogEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val c = CtecsTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<MachineryCategory?>(null) }
    var selectedManufacturer by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<ModelAvailabilityStatus?>(null) }
    var showCustomForm by remember { mutableStateOf(false) }

    // Custom machine form state
    var customCategoryName by remember { mutableStateOf("") }
    var customManufacturer by remember { mutableStateOf("") }
    var customModelName by remember { mutableStateOf("") }
    var customVariant by remember { mutableStateOf("") }
    var customSubType by remember { mutableStateOf("") }
    var customMaxCapacity by remember { mutableStateOf("") }
    var customMaxCapacityUnit by remember { mutableStateOf("t/h") }
    var customWidthM by remember { mutableStateOf("") }

    val orderedCategories = remember(suggestedCategories) {
        val suggestedSet = suggestedCategories.toSet()
        suggestedCategories + MachineryCategory.entries.filter { it !in suggestedSet }
    }

    val manufacturersInScope = remember(catalog, selectedCategory) {
        catalog
            .filter { selectedCategory == null || it.category == selectedCategory }
            .map { it.manufacturer }
            .distinct()
            .sorted()
    }

    val filteredModels = remember(catalog, searchQuery, selectedCategory, selectedManufacturer, statusFilter) {
        catalog.filter { entry ->
            (selectedCategory == null || entry.category == selectedCategory) &&
                (selectedManufacturer == null || entry.manufacturer.equals(selectedManufacturer, ignoreCase = true)) &&
                (statusFilter == null || entry.availabilityStatus == statusFilter) &&
                entry.matchesQuery(searchQuery)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("machinery_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (showCustomForm) "Add Custom Equipment" else "Select Machinery",
                            color = c.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (showCustomForm) "Dynamically add new model or category"
                            else "${filteredModels.size} verified models · Current & Legacy",
                            color = c.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    TextButton(
                        onClick = { showCustomForm = !showCustomForm },
                        modifier = Modifier.testTag("machinery_picker_toggle_custom")
                    ) {
                        Text(
                            text = if (showCustomForm) "Browse DB" else "+ Custom",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("machinery_picker_close")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close picker", tint = c.textPrimary)
                    }
                }

                if (showCustomForm) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = customCategoryName,
                                onValueChange = { customCategoryName = it },
                                label = { Text("Category (optional custom category)") },
                                placeholder = { Text("e.g. Guardrail / Asphalt Paver / Custom") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = customManufacturer,
                                onValueChange = { customManufacturer = it },
                                label = { Text("Manufacturer *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = customModelName,
                                onValueChange = { customModelName = it },
                                label = { Text("Model Name *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customVariant,
                                    onValueChange = { customVariant = it },
                                    label = { Text("Variant") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = customSubType,
                                    onValueChange = { customSubType = it },
                                    label = { Text("Equipment Type") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customMaxCapacity,
                                    onValueChange = { customMaxCapacity = it },
                                    label = { Text("Max Rated Capacity") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = customMaxCapacityUnit,
                                    onValueChange = { customMaxCapacityUnit = it },
                                    label = { Text("Unit") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.7f)
                                )
                                OutlinedTextField(
                                    value = customWidthM,
                                    onValueChange = { customWidthM = it },
                                    label = { Text("Width (m)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(0.8f)
                                )
                            }
                        }
                        item {
                            Button(
                                onClick = {
                                    if (customManufacturer.isNotBlank() && customModelName.isNotBlank()) {
                                        val entry = MachineryCatalogEntry(
                                            id = "custom_${UUID.randomUUID()}",
                                            category = selectedCategory ?: MachineryCategory.OTHER_EQUIPMENT,
                                            customCategoryLabel = customCategoryName.trim(),
                                            equipmentSubType = customSubType.trim().ifBlank { "Custom Site Equipment" },
                                            manufacturer = customManufacturer.trim(),
                                            modelName = customModelName.trim(),
                                            variant = customVariant.trim().ifBlank { "Site Fleet" },
                                            availabilityStatus = ModelAvailabilityStatus.UNVERIFIED,
                                            maxRatedCapacityValue = customMaxCapacity.toDoubleOrNull(),
                                            maxRatedCapacityUnit = customMaxCapacityUnit.trim(),
                                            workingWidthM = customWidthM.toDoubleOrNull(),
                                            dataSourceName = "User Entered Site Specification",
                                            lastVerifiedDate = "Manual Entry"
                                        )
                                        onSelectMachine(entry)
                                    }
                                },
                                enabled = customManufacturer.isNotBlank() && customModelName.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Add Equipment to Plan")
                            }
                        }
                    }
                } else {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search category, manufacturer, model, HYVA…", fontSize = 12.5.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("machinery_picker_search")
                    )

                    // Cascading Step 1: Category selector
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            PickerFilterChip(
                                label = "All Categories",
                                selected = selectedCategory == null,
                                onClick = {
                                    selectedCategory = null
                                    selectedManufacturer = null
                                }
                            )
                        }
                        items(orderedCategories, key = { it.name }) { cat ->
                            PickerFilterChip(
                                label = cat.shortLabel,
                                selected = selectedCategory == cat,
                                onClick = {
                                    selectedCategory = if (selectedCategory == cat) null else cat
                                    selectedManufacturer = null
                                },
                                modifier = Modifier.testTag("machinery_category_${cat.name.lowercase()}")
                            )
                        }
                    }

                    // Cascading Step 2: Manufacturer + Current/Legacy Status filter
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            PickerFilterChip(
                                label = "All Makes",
                                selected = selectedManufacturer == null,
                                onClick = { selectedManufacturer = null }
                            )
                        }
                        items(manufacturersInScope, key = { it }) { make ->
                            PickerFilterChip(
                                label = make,
                                selected = selectedManufacturer == make,
                                onClick = {
                                    selectedManufacturer = if (selectedManufacturer == make) null else make
                                }
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PickerFilterChip(
                            label = "All Status",
                            selected = statusFilter == null,
                            onClick = { statusFilter = null }
                        )
                        PickerFilterChip(
                            label = "Current",
                            selected = statusFilter == ModelAvailabilityStatus.CURRENT,
                            onClick = { statusFilter = ModelAvailabilityStatus.CURRENT }
                        )
                        PickerFilterChip(
                            label = "Legacy",
                            selected = statusFilter == ModelAvailabilityStatus.LEGACY,
                            onClick = { statusFilter = ModelAvailabilityStatus.LEGACY }
                        )
                    }

                    // Cascading Step 3: Models & Variants list with expandable verified specs
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredModels, key = { it.id }) { entry ->
                            MachineryCatalogModelCard(
                                entry = entry,
                                onSelect = { onSelectMachine(entry) }
                            )
                        }
                        if (filteredModels.isEmpty()) {
                            item {
                                Text(
                                    text = "No matching catalog models. Tap '+ Custom' above to add your equipment.",
                                    color = c.textMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(16.dp)
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
private fun PickerFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) c.accent.copy(alpha = if (c.isDark) 0.22f else 0.14f)
                else c.secondarySurface
            )
            .border(
                1.dp,
                if (selected) c.accent.copy(alpha = 0.65f) else c.subtleSeparator,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) c.accent else c.textPrimary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun MachineryCatalogModelCard(
    entry: MachineryCatalogEntry,
    onSelect: () -> Unit
) {
    val c = CtecsTheme.colors
    var showSpecs by remember { mutableStateOf(false) }
    val statusColor = when (entry.availabilityStatus) {
        ModelAvailabilityStatus.CURRENT -> c.success
        ModelAvailabilityStatus.LEGACY -> c.warning
        ModelAvailabilityStatus.DISCONTINUED -> c.error
        ModelAvailabilityStatus.UNVERIFIED -> c.textMuted
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = c.secondarySurface,
        border = BorderStroke(1.dp, c.subtleSeparator),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("machinery_catalog_item_${entry.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${entry.manufacturer} ${entry.modelName}",
                            color = c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = entry.availabilityStatus.label,
                            color = statusColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(statusColor.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = "${entry.variant} · ${entry.equipmentSubType}",
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.accent)
                        .clickable(onClick = onSelect)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("machinery_select_${entry.id}")
                ) {
                    Text(
                        text = "Select",
                        color = c.background,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Key verified specs preview
            val quickLine = buildList {
                if (entry.maxRatedCapacityValue != null) add("Max Rated: ${entry.formattedMaxRatedCapacity}")
                entry.workingWidthM?.let { add("Width: ${String.format(Locale.US, "%.2f", it)} m") }
                entry.manufacturerRatedPayloadTonnes?.let { add("Rated Payload: ${String.format(Locale.US, "%.1f", it)} t") }
                entry.dumperBodyVolumeM3?.let { add("Body: ${String.format(Locale.US, "%.1f", it)} m³") }
                entry.operatingWeightKg?.let { add("Wt: ${String.format(Locale.US, "%.1f", it / 1000.0)} t") }
            }.joinToString(" · ")
            if (quickLine.isNotBlank()) {
                Text(
                    text = quickLine,
                    color = c.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSpecs = !showSpecs },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (showSpecs) "Hide verified specs" else "View verified specs & source",
                    color = c.accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
            }

            AnimatedVisibility(visible = showSpecs) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    SmartFadeDivider(color = c.subtleSeparator)
                    SpecLine("Category", entry.categoryLabel)
                    SpecLine("Max Rated Capacity", entry.formattedMaxRatedCapacity)
                    SpecLine("Operating Capacity", entry.publishedOperatingCapacity ?: MachineryCatalogEntry.NOT_AVAILABLE_LABEL)
                    SpecLine("Engine Power", entry.enginePowerKw?.let { "${String.format(Locale.US, "%.1f", it)} kW" } ?: MachineryCatalogEntry.NOT_AVAILABLE_LABEL)
                    SpecLine("Working Width", entry.workingWidthM?.let { "${String.format(Locale.US, "%.2f", it)} m" } ?: MachineryCatalogEntry.NOT_AVAILABLE_LABEL)
                    entry.hopperTankDrumCapacity?.let { SpecLine("Hopper / Tank / Drum", it) }
                    entry.operatingWeightKg?.let { SpecLine("Operating Weight", "${String.format(Locale.US, "%.0f", it)} kg") }
                    entry.gvwTonnes?.let { SpecLine("GVW", "${String.format(Locale.US, "%.1f", it)} t") }
                    entry.hyvaCylinderModel?.let { SpecLine("HYVA Cylinder", it) }
                    entry.hyvaTippingCapacityTonnes?.let { SpecLine("HYVA Cylinder Lift (Not Legal Payload)", "${String.format(Locale.US, "%.1f", it)} t") }
                    entry.drumConfiguration?.let { SpecLine("Drum Config", it) }
                    entry.vibrationFrequencyHz?.let { SpecLine("Vibration Frequency", it) }
                    entry.ballastWeightRangeKg?.let { SpecLine("Ballast Range", it) }
                    entry.tyreConfiguration?.let { SpecLine("Tyre Config", it) }
                    entry.tyrePressureRangeBar?.let { SpecLine("Tyre Pressure Range", it) }
                    entry.additionalSpecs.forEach { (k, v) -> SpecLine(k, v) }
                    SpecLine("Data Source", entry.dataSourceName)
                    SpecLine("Last Verified", entry.lastVerifiedDate)
                }
            }
        }
    }
}

@Composable
private fun SpecLine(label: String, value: String) {
    val c = CtecsTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = c.textMuted, fontSize = 10.sp, modifier = Modifier.weight(0.45f))
        Text(value, color = c.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f))
    }
}
