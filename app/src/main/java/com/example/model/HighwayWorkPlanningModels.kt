package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Expanded highway construction activity catalog for optional Highway Work Planning.
 * Maps cleanly to [HighwayWorkActivity] for live MoRTH / IRC weather suitability evaluation.
 */
enum class PlanningWorkActivity(
    val displayName: String,
    val linkedWeatherActivity: HighwayWorkActivity,
    val defaultThicknessMm: Double,
    val defaultDensityTPerM3: Double?,
    val productionUnit: String
) {
    BC_LAYING("BC Laying", HighwayWorkActivity.BC_LAYING, 40.0, null, "tonnes/hour"),
    DBM_LAYING("DBM Laying", HighwayWorkActivity.DBM_LAYING, 60.0, null, "tonnes/hour"),
    BM_LAYING("BM – Bituminous Macadam", HighwayWorkActivity.DBM_LAYING, 50.0, null, "tonnes/hour"),
    SDBC_LAYING("SDBC – Semi-Dense Bituminous Concrete", HighwayWorkActivity.BC_LAYING, 25.0, null, "tonnes/hour"),
    SMA_LAYING("SMA – Stone Matrix Asphalt", HighwayWorkActivity.BC_LAYING, 40.0, null, "tonnes/hour"),
    TACK_COAT("Tack Coat", HighwayWorkActivity.TACK_COAT_APPLICATION, 0.0, null, "m²/hour"),
    PRIME_COAT("Prime Coat", HighwayWorkActivity.PRIME_COAT_APPLICATION, 0.0, null, "m²/hour"),
    MILLING("Milling", HighwayWorkActivity.MILLING_OPERATIONS, 50.0, null, "m³/hour"),
    GLASS_GRID("Glass Grid Application", HighwayWorkActivity.GLASS_GRID_APPLICATION, 0.0, null, "m²/hour"),
    GSB("GSB", HighwayWorkActivity.GSB_OPERATIONS, 200.0, null, "m³/hour"),
    WMM("WMM", HighwayWorkActivity.WMM_OPERATIONS, 250.0, null, "m³/hour"),
    EARTHWORK("Earthwork", HighwayWorkActivity.EARTHWORK, 200.0, null, "m³/hour"),
    SUBGRADE("Subgrade", HighwayWorkActivity.EARTHWORK, 500.0, null, "m³/hour"),
    SUBGRADE_PREP("Subgrade Preparation", HighwayWorkActivity.EARTHWORK, 500.0, null, "m³/hour"),
    DLC("DLC – Dry Lean Concrete", HighwayWorkActivity.CONCRETE_WORKS, 150.0, null, "m³/hour"),
    PQC("PQC – Pavement Quality Concrete", HighwayWorkActivity.CONCRETE_WORKS, 300.0, null, "m³/hour"),
    PQC_DLC("PQC / DLC", HighwayWorkActivity.CONCRETE_WORKS, 250.0, null, "m³/hour"),
    CONCRETE_WORKS("Concrete Works", HighwayWorkActivity.CONCRETE_WORKS, 200.0, null, "m³/hour"),
    ROAD_MARKING("Road Marking", HighwayWorkActivity.ROAD_MARKING, 2.5, null, "m²/hour"),
    ROAD_STUDS("Road Studs", HighwayWorkActivity.ROAD_MARKING, 0.0, null, "nos/hour"),
    ROAD_STUD("Road Stud Installation", HighwayWorkActivity.ROAD_MARKING, 0.0, null, "nos/hour"),
    BROOMING_CLEANING("Brooming / Surface Cleaning", HighwayWorkActivity.SURFACE_PREPARATION_CLEANING, 0.0, null, "m²/hour"),
    CRACK_SEALING("Crack Sealing", HighwayWorkActivity.CRACK_SEALING, 0.0, null, "m/hour"),
    SURFACE_DRESSING("Surface Dressing", HighwayWorkActivity.TACK_COAT_APPLICATION, 15.0, null, "m²/hour"),
    DRAINAGE_CULVERT("Drainage & Culvert Works", HighwayWorkActivity.CONCRETE_WORKS, 200.0, null, "m³/hour"),
    CRASH_BARRIER("Crash Barriers", HighwayWorkActivity.GENERAL_HIGHWAY_MAINTENANCE, 0.0, null, "m/hour"),
    OTHER_HIGHWAY_WORKS("Other Highway Works", HighwayWorkActivity.GENERAL_HIGHWAY_MAINTENANCE, 0.0, null, "units/hour");

    val isBituminousProduction: Boolean
        get() = this == BC_LAYING || this == DBM_LAYING || this == BM_LAYING || this == SDBC_LAYING || this == SMA_LAYING

    companion object {
        fun fromName(raw: String?): PlanningWorkActivity =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BC_LAYING
    }
}

/**
 * Complete 12-category highway construction machinery taxonomy (extensible via customCategoryLabel).
 */
enum class MachineryCategory(val displayName: String, val shortLabel: String) {
    ASPHALT_PAVER("Asphalt Paver Machines", "Paver"),
    HMP_PLANT("Hot Mix Plants (HMP) & Production Plants", "HMP / Plant"),
    HYVA_DUMPER("HYVA / Dumper / Tipper Vehicles", "HYVA / Dumper"),
    TANDEM_ROLLER("Tandem / Vibratory / Static Rollers", "Roller"),
    PTR_ROLLER("PTR – Pneumatic Tyred Rollers", "PTR"),
    ROAD_MARKING("Road Marking Machinery", "Road Marking"),
    BROOMER_SWEEPER("Broomers & Road Cleaning Machinery", "Broomer"),
    MILLING_RECYCLING("Milling & Pavement Recycling Machinery", "Milling"),
    EARTHWORK_SUBGRADE("Earthwork & Subgrade Machinery", "Earthwork"),
    CONCRETE_STRUCTURES("Concrete & Structure Machinery", "Concrete"),
    BITUMEN_SURFACE("Bitumen & Surface Treatment Equipment", "Bitumen / Sprayer"),
    OTHER_EQUIPMENT("Other Highway Construction Equipment", "Other / Support");

    companion object {
        fun fromName(raw: String?): MachineryCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OTHER_EQUIPMENT
    }
}

enum class ModelAvailabilityStatus(val label: String) {
    CURRENT("Current"),
    LEGACY("Legacy"),
    DISCONTINUED("Discontinued"),
    UNVERIFIED("Unverified");

    companion object {
        fun fromName(raw: String?): ModelAvailabilityStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNVERIFIED
    }
}

/**
 * Verified manufacturer catalog record for a highway construction machine.
 * Unknown or unpublished fields MUST remain null (never invented) and render as
 * "Not Available / Verify Manually".
 */
data class MachineryCatalogEntry(
    val id: String,
    val category: MachineryCategory,
    val customCategoryLabel: String = "",
    val equipmentSubType: String,
    val manufacturer: String,
    val modelName: String,
    val variant: String = "Standard",
    val modelYearOrGeneration: String? = null,
    val availabilityStatus: ModelAvailabilityStatus = ModelAvailabilityStatus.CURRENT,
    val maxRatedCapacityValue: Double? = null,
    val maxRatedCapacityUnit: String = "",
    val publishedOperatingCapacity: String? = null,
    val enginePowerKw: Double? = null,
    val workingWidthM: Double? = null,
    val hopperTankDrumCapacity: String? = null,
    val operatingWeightKg: Double? = null,
    // Dumper / Tipper / HYVA strictly separated fields (NEVER derive legal payload from HYVA cylinder rating)
    val dumperBodyVolumeM3: Double? = null,
    val manufacturerRatedPayloadTonnes: Double? = null,
    val gvwTonnes: Double? = null,
    val hyvaCylinderModel: String? = null,
    val hyvaTippingCapacityTonnes: Double? = null,
    val wheelConfiguration: String? = null,
    // Roller / PTR fields
    val drumConfiguration: String? = null,
    val vibrationFrequencyHz: String? = null,
    val amplitudeMm: String? = null,
    val centrifugalForceKn: String? = null,
    val ballastWeightRangeKg: String? = null,
    val tyreConfiguration: String? = null,
    val tyrePressureRangeBar: String? = null,
    // Additional technical specifications (batch size, milling depth, heating capacity, sweeping speed, etc.)
    val additionalSpecs: Map<String, String> = emptyMap(),
    val dataSourceName: String = "Manufacturer Technical Datasheet",
    val sourceUrl: String = "",
    val lastVerifiedDate: String = "2026-10-15",
    val marketRegion: String = "India / Global"
) {
    val categoryLabel: String
        get() = customCategoryLabel.ifBlank { category.displayName }

    val fullDisplayTitle: String
        get() = if (variant.isNotBlank() && !variant.equals("Standard", ignoreCase = true)) {
            "$manufacturer $modelName ($variant)"
        } else {
            "$manufacturer $modelName"
        }

    val formattedMaxRatedCapacity: String
        get() = if (maxRatedCapacityValue != null && maxRatedCapacityUnit.isNotBlank()) {
            val formatted = if (maxRatedCapacityValue % 1.0 == 0.0) {
                maxRatedCapacityValue.toInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", maxRatedCapacityValue)
            }
            "$formatted $maxRatedCapacityUnit"
        } else {
            NOT_AVAILABLE_LABEL
        }

    fun matchesQuery(query: String): Boolean {
        val q = normalizeSearchText(query)
        if (q.isEmpty()) return true
        return normalizeSearchText(manufacturer).contains(q) ||
            normalizeSearchText(modelName).contains(q) ||
            normalizeSearchText(variant).contains(q) ||
            normalizeSearchText(equipmentSubType).contains(q) ||
            normalizeSearchText(categoryLabel).contains(q) ||
            normalizeSearchText(availabilityStatus.label).contains(q) ||
            (hyvaCylinderModel?.let { normalizeSearchText(it).contains(q) } == true)
    }

    private fun normalizeSearchText(raw: String): String {
        val trimmed = raw.trim().lowercase(java.util.Locale.US)
        if (trimmed.isEmpty()) return ""
        val decomposed = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(decomposed, "")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("category", category.name)
        put("customCategoryLabel", customCategoryLabel)
        put("equipmentSubType", equipmentSubType)
        put("manufacturer", manufacturer)
        put("modelName", modelName)
        put("variant", variant)
        modelYearOrGeneration?.let { put("modelYearOrGeneration", it) }
        put("availabilityStatus", availabilityStatus.name)
        maxRatedCapacityValue?.let { put("maxRatedCapacityValue", it) }
        put("maxRatedCapacityUnit", maxRatedCapacityUnit)
        publishedOperatingCapacity?.let { put("publishedOperatingCapacity", it) }
        enginePowerKw?.let { put("enginePowerKw", it) }
        workingWidthM?.let { put("workingWidthM", it) }
        hopperTankDrumCapacity?.let { put("hopperTankDrumCapacity", it) }
        operatingWeightKg?.let { put("operatingWeightKg", it) }
        dumperBodyVolumeM3?.let { put("dumperBodyVolumeM3", it) }
        manufacturerRatedPayloadTonnes?.let { put("manufacturerRatedPayloadTonnes", it) }
        gvwTonnes?.let { put("gvwTonnes", it) }
        hyvaCylinderModel?.let { put("hyvaCylinderModel", it) }
        hyvaTippingCapacityTonnes?.let { put("hyvaTippingCapacityTonnes", it) }
        wheelConfiguration?.let { put("wheelConfiguration", it) }
        drumConfiguration?.let { put("drumConfiguration", it) }
        vibrationFrequencyHz?.let { put("vibrationFrequencyHz", it) }
        amplitudeMm?.let { put("amplitudeMm", it) }
        centrifugalForceKn?.let { put("centrifugalForceKn", it) }
        ballastWeightRangeKg?.let { put("ballastWeightRangeKg", it) }
        tyreConfiguration?.let { put("tyreConfiguration", it) }
        tyrePressureRangeBar?.let { put("tyrePressureRangeBar", it) }
        put("additionalSpecs", JSONObject().apply {
            additionalSpecs.forEach { (k, v) -> put(k, v) }
        })
        put("dataSourceName", dataSourceName)
        put("sourceUrl", sourceUrl)
        put("lastVerifiedDate", lastVerifiedDate)
        put("marketRegion", marketRegion)
    }

    companion object {
        const val NOT_AVAILABLE_LABEL = "Not Available / Verify Manually"
        private val DIACRITICS_REGEX = "\\p{Mn}+".toRegex()

        fun fromJson(obj: JSONObject): MachineryCatalogEntry {
            val specsMap = linkedMapOf<String, String>()
            obj.optJSONObject("additionalSpecs")?.let { specObj ->
                val keys = specObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    specsMap[k] = specObj.optString(k, "")
                }
            }
            fun optDoubleOrNull(key: String): Double? =
                if (obj.has(key) && !obj.isNull(key)) obj.optDouble(key).takeIf { it.isFinite() } else null
            fun optStringOrNull(key: String): String? =
                if (obj.has(key) && !obj.isNull(key)) obj.optString(key).takeIf { it.isNotBlank() } else null

            return MachineryCatalogEntry(
                id = obj.optString("id", UUID.randomUUID().toString()),
                category = MachineryCategory.fromName(obj.optString("category")),
                customCategoryLabel = obj.optString("customCategoryLabel", ""),
                equipmentSubType = obj.optString("equipmentSubType", ""),
                manufacturer = obj.optString("manufacturer", ""),
                modelName = obj.optString("modelName", ""),
                variant = obj.optString("variant", "Standard"),
                modelYearOrGeneration = optStringOrNull("modelYearOrGeneration"),
                availabilityStatus = ModelAvailabilityStatus.fromName(obj.optString("availabilityStatus")),
                maxRatedCapacityValue = optDoubleOrNull("maxRatedCapacityValue"),
                maxRatedCapacityUnit = obj.optString("maxRatedCapacityUnit", ""),
                publishedOperatingCapacity = optStringOrNull("publishedOperatingCapacity"),
                enginePowerKw = optDoubleOrNull("enginePowerKw"),
                workingWidthM = optDoubleOrNull("workingWidthM"),
                hopperTankDrumCapacity = optStringOrNull("hopperTankDrumCapacity"),
                operatingWeightKg = optDoubleOrNull("operatingWeightKg"),
                dumperBodyVolumeM3 = optDoubleOrNull("dumperBodyVolumeM3"),
                manufacturerRatedPayloadTonnes = optDoubleOrNull("manufacturerRatedPayloadTonnes"),
                gvwTonnes = optDoubleOrNull("gvwTonnes"),
                hyvaCylinderModel = optStringOrNull("hyvaCylinderModel"),
                hyvaTippingCapacityTonnes = optDoubleOrNull("hyvaTippingCapacityTonnes"),
                wheelConfiguration = optStringOrNull("wheelConfiguration"),
                drumConfiguration = optStringOrNull("drumConfiguration"),
                vibrationFrequencyHz = optStringOrNull("vibrationFrequencyHz"),
                amplitudeMm = optStringOrNull("amplitudeMm"),
                centrifugalForceKn = optStringOrNull("centrifugalForceKn"),
                ballastWeightRangeKg = optStringOrNull("ballastWeightRangeKg"),
                tyreConfiguration = optStringOrNull("tyreConfiguration"),
                tyrePressureRangeBar = optStringOrNull("tyrePressureRangeBar"),
                additionalSpecs = specsMap,
                dataSourceName = obj.optString("dataSourceName", "Manufacturer Technical Datasheet"),
                sourceUrl = obj.optString("sourceUrl", ""),
                lastVerifiedDate = obj.optString("lastVerifiedDate", "2026-10-15"),
                marketRegion = obj.optString("marketRegion", "India / Global")
            )
        }
    }
}

/**
 * Editable machine assignment within a Highway Work Plan.
 * Strictly separates Manufacturer Max Rated Capacity from user-editable Actual Site Operating Capacity.
 * Refreshing the catalog NEVER overwrites user-entered actual site values.
 */
data class PlannedMachineryItem(
    val instanceId: String = UUID.randomUUID().toString(),
    val catalogEntry: MachineryCatalogEntry,
    val quantity: Int = 1,
    val actualOperatingCapacity: Double? = null,
    val actualOperatingCapacityUnit: String = catalogEntry.maxRatedCapacityUnit,
    val actualWorkingWidthM: Double? = catalogEntry.workingWidthM,
    val actualRegisteredPayloadTonnes: Double? = catalogEntry.manufacturerRatedPayloadTonnes,
    val actualSiteLoadTonnes: Double? = catalogEntry.manufacturerRatedPayloadTonnes,
    val actualBallastWeightKg: Double? = catalogEntry.operatingWeightKg,
    val actualTyrePressureBar: String = "",
    val operatingEfficiencyPercent: Double = 80.0,
    val siteNotes: String = ""
) {
    /**
     * Effective operating capacity per unit. Never silently assumes 100% of maximum rated capacity:
     * if actual site capacity is explicitly entered, uses it directly; otherwise applies site efficiency %
     * to published max rated capacity (and returns null when neither is known).
     */
    fun effectiveHourlyCapacityPerUnit(): Double? {
        if (actualOperatingCapacity != null && actualOperatingCapacity > 0.0) {
            return actualOperatingCapacity
        }
        val rated = catalogEntry.maxRatedCapacityValue ?: return null
        if (rated <= 0.0) return null
        val eff = (operatingEfficiencyPercent / 100.0).coerceIn(0.2, 1.0)
        return rated * eff
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("instanceId", instanceId)
        put("catalogEntry", catalogEntry.toJson())
        put("quantity", quantity)
        actualOperatingCapacity?.let { put("actualOperatingCapacity", it) }
        put("actualOperatingCapacityUnit", actualOperatingCapacityUnit)
        actualWorkingWidthM?.let { put("actualWorkingWidthM", it) }
        actualRegisteredPayloadTonnes?.let { put("actualRegisteredPayloadTonnes", it) }
        actualSiteLoadTonnes?.let { put("actualSiteLoadTonnes", it) }
        actualBallastWeightKg?.let { put("actualBallastWeightKg", it) }
        put("actualTyrePressureBar", actualTyrePressureBar)
        put("operatingEfficiencyPercent", operatingEfficiencyPercent)
        put("siteNotes", siteNotes)
    }

    companion object {
        fun fromJson(obj: JSONObject): PlannedMachineryItem {
            val entry = MachineryCatalogEntry.fromJson(obj.getJSONObject("catalogEntry"))
            fun optD(k: String): Double? =
                if (obj.has(k) && !obj.isNull(k)) obj.optDouble(k).takeIf { it.isFinite() } else null
            return PlannedMachineryItem(
                instanceId = obj.optString("instanceId", UUID.randomUUID().toString()),
                catalogEntry = entry,
                quantity = obj.optInt("quantity", 1).coerceAtLeast(1),
                actualOperatingCapacity = optD("actualOperatingCapacity"),
                actualOperatingCapacityUnit = obj.optString("actualOperatingCapacityUnit", entry.maxRatedCapacityUnit),
                actualWorkingWidthM = optD("actualWorkingWidthM") ?: entry.workingWidthM,
                actualRegisteredPayloadTonnes = optD("actualRegisteredPayloadTonnes") ?: entry.manufacturerRatedPayloadTonnes,
                actualSiteLoadTonnes = optD("actualSiteLoadTonnes") ?: entry.manufacturerRatedPayloadTonnes,
                actualBallastWeightKg = optD("actualBallastWeightKg") ?: entry.operatingWeightKg,
                actualTyrePressureBar = obj.optString("actualTyrePressureBar", ""),
                operatingEfficiencyPercent = obj.optDouble("operatingEfficiencyPercent", 80.0).coerceIn(20.0, 100.0),
                siteNotes = obj.optString("siteNotes", "")
            )
        }
    }
}

enum class TransportStatus(val label: String) {
    SUFFICIENT("Sufficient"),
    SHORTAGE("Shortage"),
    INSUFFICIENT_DATA("Insufficient Data")
}

data class TransportPlanningInput(
    val hmpPlantLocation: String = "",
    val workZoneLocation: String = "",
    val roadLeadDistanceKm: Double? = null,
    val isManualDistanceOverride: Boolean = true,
    val dumperCount: Int = 0,
    val selectedDumperModelLabel: String = "",
    val actualDumperPayloadTonnes: Double? = null,
    val dumperBodyVolumeM3: Double? = null,
    val materialDensityTPerM3: Double? = null,
    val loadingTimeMin: Double = 8.0,
    val loadedTravelTimeMin: Double? = null,
    val unloadingTimeMin: Double = 7.0,
    val emptyReturnTimeMin: Double? = null,
    val waitingQueueTimeMin: Double = 10.0,
    val manualCycleTimeOverrideMin: Double? = null,
    val loadedAvgSpeedKmh: Double = 30.0,
    val emptyAvgSpeedKmh: Double = 40.0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("hmpPlantLocation", hmpPlantLocation)
        put("workZoneLocation", workZoneLocation)
        roadLeadDistanceKm?.let { put("roadLeadDistanceKm", it) }
        put("isManualDistanceOverride", isManualDistanceOverride)
        put("dumperCount", dumperCount)
        put("selectedDumperModelLabel", selectedDumperModelLabel)
        actualDumperPayloadTonnes?.let { put("actualDumperPayloadTonnes", it) }
        dumperBodyVolumeM3?.let { put("dumperBodyVolumeM3", it) }
        materialDensityTPerM3?.let { put("materialDensityTPerM3", it) }
        put("loadingTimeMin", loadingTimeMin)
        loadedTravelTimeMin?.let { put("loadedTravelTimeMin", it) }
        put("unloadingTimeMin", unloadingTimeMin)
        emptyReturnTimeMin?.let { put("emptyReturnTimeMin", it) }
        put("waitingQueueTimeMin", waitingQueueTimeMin)
        manualCycleTimeOverrideMin?.let { put("manualCycleTimeOverrideMin", it) }
        put("loadedAvgSpeedKmh", loadedAvgSpeedKmh)
        put("emptyAvgSpeedKmh", emptyAvgSpeedKmh)
    }

    companion object {
        fun fromJson(obj: JSONObject?): TransportPlanningInput {
            if (obj == null) return TransportPlanningInput()
            fun optD(k: String): Double? =
                if (obj.has(k) && !obj.isNull(k)) obj.optDouble(k).takeIf { it.isFinite() } else null
            return TransportPlanningInput(
                hmpPlantLocation = obj.optString("hmpPlantLocation", ""),
                workZoneLocation = obj.optString("workZoneLocation", ""),
                roadLeadDistanceKm = optD("roadLeadDistanceKm"),
                isManualDistanceOverride = obj.optBoolean("isManualDistanceOverride", true),
                dumperCount = obj.optInt("dumperCount", 0).coerceAtLeast(0),
                selectedDumperModelLabel = obj.optString("selectedDumperModelLabel", ""),
                actualDumperPayloadTonnes = optD("actualDumperPayloadTonnes"),
                dumperBodyVolumeM3 = optD("dumperBodyVolumeM3"),
                materialDensityTPerM3 = optD("materialDensityTPerM3"),
                loadingTimeMin = obj.optDouble("loadingTimeMin", 8.0),
                loadedTravelTimeMin = optD("loadedTravelTimeMin"),
                unloadingTimeMin = obj.optDouble("unloadingTimeMin", 7.0),
                emptyReturnTimeMin = optD("emptyReturnTimeMin"),
                waitingQueueTimeMin = obj.optDouble("waitingQueueTimeMin", 10.0),
                manualCycleTimeOverrideMin = optD("manualCycleTimeOverrideMin"),
                loadedAvgSpeedKmh = obj.optDouble("loadedAvgSpeedKmh", 30.0),
                emptyAvgSpeedKmh = obj.optDouble("emptyAvgSpeedKmh", 40.0)
            )
        }
    }
}

data class TransportPlanningResult(
    val oneWayLeadKm: Double?,
    val roundTripKm: Double?,
    val loadedTravelMin: Double?,
    val emptyReturnMin: Double?,
    val totalCycleTimeMin: Double?,
    val tripsPerDumperPerShift: Double?,
    val totalTripsPerShift: Double?,
    val dumperPayloadTonnes: Double?,
    val fleetSize: Int,
    val estimatedSupplyTonnesPerHour: Double?,
    val totalShiftTransportCapacityTonnes: Double?,
    val requiredDumpersForTarget: Int?,
    val status: TransportStatus,
    val statusDetail: String
)

data class CompactionPlanningInput(
    val paverWorkingWidthM: Double? = null,
    val tandemRollerDrumWidthM: Double? = null,
    val tandemRollerCount: Int = 0,
    val ptrWorkingWidthM: Double? = null,
    val ptrCount: Int = 0,
    val overlapM: Double = 0.20,
    val rollerSpeedKmh: Double = 3.5,
    val requiredPasses: Int? = null, // Editable per approved JMF / field trial (never hardcoded)
    val rollingPatternNote: String = "Per approved JMF & field trial",
    val siteEfficiencyPercent: Double = 75.0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        paverWorkingWidthM?.let { put("paverWorkingWidthM", it) }
        tandemRollerDrumWidthM?.let { put("tandemRollerDrumWidthM", it) }
        put("tandemRollerCount", tandemRollerCount)
        ptrWorkingWidthM?.let { put("ptrWorkingWidthM", it) }
        put("ptrCount", ptrCount)
        put("overlapM", overlapM)
        put("rollerSpeedKmh", rollerSpeedKmh)
        requiredPasses?.let { put("requiredPasses", it) }
        put("rollingPatternNote", rollingPatternNote)
        put("siteEfficiencyPercent", siteEfficiencyPercent)
    }

    companion object {
        fun fromJson(obj: JSONObject?): CompactionPlanningInput {
            if (obj == null) return CompactionPlanningInput()
            fun optD(k: String): Double? =
                if (obj.has(k) && !obj.isNull(k)) obj.optDouble(k).takeIf { it.isFinite() } else null
            fun optI(k: String): Int? =
                if (obj.has(k) && !obj.isNull(k)) obj.optInt(k).takeIf { it > 0 } else null
            return CompactionPlanningInput(
                paverWorkingWidthM = optD("paverWorkingWidthM"),
                tandemRollerDrumWidthM = optD("tandemRollerDrumWidthM"),
                tandemRollerCount = obj.optInt("tandemRollerCount", 0).coerceAtLeast(0),
                ptrWorkingWidthM = optD("ptrWorkingWidthM"),
                ptrCount = obj.optInt("ptrCount", 0).coerceAtLeast(0),
                overlapM = obj.optDouble("overlapM", 0.20).coerceIn(0.0, 1.0),
                rollerSpeedKmh = obj.optDouble("rollerSpeedKmh", 3.5).coerceIn(0.5, 12.0),
                requiredPasses = optI("requiredPasses"),
                rollingPatternNote = obj.optString("rollingPatternNote", "Per approved JMF & field trial"),
                siteEfficiencyPercent = obj.optDouble("siteEfficiencyPercent", 75.0).coerceIn(30.0, 100.0)
            )
        }
    }
}

data class CompactionPlanningResult(
    val effectiveStripWidthM: Double?,
    val stripsAcrossPaverWidth: Int?,
    val requiredPassesUsed: Int?,
    val estimatedCoverageSqmPerHourPerRoller: Double?,
    val totalRollingCapacitySqmPerHour: Double?,
    val totalRollingCapacityTonnesPerHour: Double?,
    val estimatedRollingTimeHours: Double?,
    val estimatedTandemRollersRequired: Int?,
    val estimatedPtrRequired: Int?,
    val hasCompactionBottleneck: Boolean,
    val advisoryNote: String
)

enum class ManpowerTeamGroup(val label: String) {
    PAVING_TEAM("Paving Team"),
    ROLLING_TEAM("Rolling Team"),
    PLANT_TRANSPORT("Plant & Transport"),
    QUALITY_SITE("Quality & Site Team");

    companion object {
        fun fromName(raw: String?): ManpowerTeamGroup =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: QUALITY_SITE
    }
}

data class ManpowerRoleAllocation(
    val id: String = UUID.randomUUID().toString(),
    val team: ManpowerTeamGroup,
    val roleName: String,
    val requiredCount: Int = 0,
    val availableCount: Int = 0,
    val deployedCount: Int = 0,
    val shiftTiming: String = "08:00–18:00",
    val affectedActivityHint: String = ""
) {
    val shortageCount: Int
        get() = (requiredCount - availableCount).coerceAtLeast(0)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("team", team.name)
        put("roleName", roleName)
        put("requiredCount", requiredCount)
        put("availableCount", availableCount)
        put("deployedCount", deployedCount)
        put("shiftTiming", shiftTiming)
        put("affectedActivityHint", affectedActivityHint)
    }

    companion object {
        fun fromJson(obj: JSONObject): ManpowerRoleAllocation = ManpowerRoleAllocation(
            id = obj.optString("id", UUID.randomUUID().toString()),
            team = ManpowerTeamGroup.fromName(obj.optString("team")),
            roleName = obj.optString("roleName", ""),
            requiredCount = obj.optInt("requiredCount", 0).coerceAtLeast(0),
            availableCount = obj.optInt("availableCount", 0).coerceAtLeast(0),
            deployedCount = obj.optInt("deployedCount", 0).coerceAtLeast(0),
            shiftTiming = obj.optString("shiftTiming", "08:00–18:00"),
            affectedActivityHint = obj.optString("affectedActivityHint", "")
        )
    }
}

data class ManpowerPlanningSummary(
    val totalRequired: Int,
    val totalAvailable: Int,
    val totalDeployed: Int,
    val totalShortage: Int,
    val shortageRoles: List<ManpowerRoleAllocation>,
    val affectedActivities: List<String>
)

/**
 * Full editable Highway Work Plan that can be saved, reopened, edited, or cleared independently.
 */
data class HighwayWorkPlan(
    val planId: String = UUID.randomUUID().toString(),
    val planTitle: String = "",
    val projectName: String = "",
    val roadOrHighwayName: String = "",
    val locationName: String = "",
    val workDateIso: String = "",
    val activity: PlanningWorkActivity = PlanningWorkActivity.BC_LAYING,
    val customActivityName: String = "",
    val customMaterialType: String = "",
    val startChainage: String = "",
    val endChainage: String = "",
    val roadSide: String = "LHS",
    val workLengthMeters: Double? = null,
    val useManualLengthOverride: Boolean = false,
    val totalRoadWidthMeters: Double? = null,
    val individualLaneWidthMeters: Double? = null,
    val numberOfLanes: Int? = null,
    val workWidthMeters: Double? = null,
    val stretches: List<RoadStretchWidthItem> = emptyList(),
    val layerThicknessMm: Double? = null,
    val materialDensityTPerM3: Double? = null,
    val actualQuantityDelivered: Double? = null,
    // Day / Night / Custom Shift & Time-to-Time Planning
    val shiftType: WorkShiftType = WorkShiftType.DAY_SHIFT,
    val shiftStartDateIso: String = "",
    val shiftEndDateIso: String = "",
    val plannedStartHour24: Int = 8,
    val plannedStartMinute: Int = 0,
    val plannedEndHour24: Int = 17,
    val plannedEndMinute: Int = 0,
    val breakDurationMinutes: Int = 60,
    val mobilisationMinutes: Int = 30,
    val loadingWaitingMinutes: Int = 0,
    val workingPeriods: List<WorkingTimePeriod> = emptyList(),
    val plannedWorkingHours: Double = 8.0,
    val targetQuantity: Double? = null,
    val actualProductionRatePerHour: Double? = null,
    // Thickness, Temperature, Compaction & Standards
    val thicknessSpec: ThicknessCompactionSpec = ThicknessCompactionSpec(),
    val temperatureSpec: MaterialTemperatureSpec = MaterialTemperatureSpec(),
    val camberMode: RollingCamberMode = RollingCamberMode.CROWN_CENTER,
    val morthRevision: MoRTHRevision = MoRTHRevision.FIFTH_REVISION_2013,
    val jmfReference: String = "",
    val trialSectionReference: String = "",
    // Machinery, Transport, Compaction & Manpower
    val machinery: List<PlannedMachineryItem> = emptyList(),
    val transport: TransportPlanningInput = TransportPlanningInput(),
    val compaction: CompactionPlanningInput = CompactionPlanningInput(),
    val manpower: List<ManpowerRoleAllocation> = defaultManpowerRoles(),
    // Approval & Traceability Metadata
    val preparedBy: String = "",
    val checkedBy: String = "",
    val approvedBy: String = "",
    val weatherForecastSnapshotNote: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val effectiveActivityName: String
        get() = if (activity == PlanningWorkActivity.OTHER_HIGHWAY_WORKS && customActivityName.isNotBlank()) {
            customActivityName.trim()
        } else {
            activity.displayName
        }

    val effectivePlanTitle: String
        get() = planTitle.ifBlank {
            listOf(effectiveActivityName, startChainage.ifBlank { roadOrHighwayName })
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "Highway Work Plan" }
        }

    val chainageLengthCalc: ChainageLengthCalculation
        get() = ChainageMath.calculateLength(startChainage, endChainage)

    /**
     * Effective length: uses manual override when explicitly toggled or when chainage is unavailable,
     * otherwise uses exact calculated chainage difference |End - Start|.
     */
    val effectiveLengthMeters: Double?
        get() = when {
            useManualLengthOverride -> workLengthMeters?.takeIf { it > 0.0 }
            chainageLengthCalc.calculatedLengthMeters != null && chainageLengthCalc.calculatedLengthMeters!! > 0.0 ->
                chainageLengthCalc.calculatedLengthMeters
            else -> workLengthMeters?.takeIf { it > 0.0 }
        }

    val isLengthFromChainage: Boolean
        get() = !useManualLengthOverride && (chainageLengthCalc.calculatedLengthMeters ?: 0.0) > 0.0

    /**
     * Effective work width: NEVER multiplies lane width × lane count when workWidthMeters or totalRoadWidthMeters
     * already represents the actual work width.
     */
    val effectiveWorkWidthMeters: Double?
        get() = when {
            workWidthMeters != null && workWidthMeters > 0.0 -> workWidthMeters
            totalRoadWidthMeters != null && totalRoadWidthMeters > 0.0 -> totalRoadWidthMeters
            individualLaneWidthMeters != null && individualLaneWidthMeters > 0.0 && numberOfLanes != null && numberOfLanes > 0 ->
                individualLaneWidthMeters * numberOfLanes
            else -> null
        }

    val shiftTimingResult: ShiftTimingResult
        get() = ShiftTimingCalculator.calculate(
            shiftType = shiftType,
            startDateIso = shiftStartDateIso.ifBlank { workDateIso },
            startHour24 = plannedStartHour24,
            startMinute = plannedStartMinute,
            endDateIso = shiftEndDateIso.ifBlank {
                if (shiftType == WorkShiftType.NIGHT_SHIFT) ShiftTimingCalculator.nextIsoDate(shiftStartDateIso.ifBlank { workDateIso })
                else shiftStartDateIso.ifBlank { workDateIso }
            },
            endHour24 = plannedEndHour24,
            endMinute = plannedEndMinute,
            breakDurationMinutes = breakDurationMinutes,
            mobilisationMinutes = mobilisationMinutes,
            loadingWaitingMinutes = loadingWaitingMinutes,
            workingPeriods = workingPeriods
        )

    val effectiveWorkingHoursValue: Double
        get() {
            val calcHours = shiftTimingResult.effectiveWorkingHours
            return if (calcHours > 0.0) calcHours else plannedWorkingHours
        }

    val formattedLocation: String
        get() = buildList {
            if (roadOrHighwayName.isNotBlank()) add(roadOrHighwayName.trim())
            if (locationName.isNotBlank()) add(locationName.trim())
            val ch = when {
                startChainage.isNotBlank() && endChainage.isNotBlank() -> "${startChainage.trim()} – ${endChainage.trim()}"
                startChainage.isNotBlank() -> startChainage.trim()
                endChainage.isNotBlank() -> endChainage.trim()
                else -> ""
            }
            if (ch.isNotBlank()) add(ch)
            if (roadSide.isNotBlank()) add(roadSide)
        }.joinToString(" | ").ifBlank { "Location not specified" }

    fun toJson(): JSONObject = JSONObject().apply {
        put("planId", planId)
        put("planTitle", planTitle)
        put("projectName", projectName)
        put("roadOrHighwayName", roadOrHighwayName)
        put("locationName", locationName)
        put("workDateIso", workDateIso)
        put("activity", activity.name)
        put("customActivityName", customActivityName)
        put("customMaterialType", customMaterialType)
        put("startChainage", startChainage)
        put("endChainage", endChainage)
        put("roadSide", roadSide)
        workLengthMeters?.let { put("workLengthMeters", it) }
        put("useManualLengthOverride", useManualLengthOverride)
        totalRoadWidthMeters?.let { put("totalRoadWidthMeters", it) }
        individualLaneWidthMeters?.let { put("individualLaneWidthMeters", it) }
        numberOfLanes?.let { put("numberOfLanes", it) }
        workWidthMeters?.let { put("workWidthMeters", it) }
        put("stretches", JSONArray().apply { stretches.forEach { put(it.toJson()) } })
        layerThicknessMm?.let { put("layerThicknessMm", it) }
        materialDensityTPerM3?.let { put("materialDensityTPerM3", it) }
        actualQuantityDelivered?.let { put("actualQuantityDelivered", it) }
        put("shiftType", shiftType.name)
        put("shiftStartDateIso", shiftStartDateIso)
        put("shiftEndDateIso", shiftEndDateIso)
        put("plannedStartHour24", plannedStartHour24)
        put("plannedStartMinute", plannedStartMinute)
        put("plannedEndHour24", plannedEndHour24)
        put("plannedEndMinute", plannedEndMinute)
        put("breakDurationMinutes", breakDurationMinutes)
        put("mobilisationMinutes", mobilisationMinutes)
        put("loadingWaitingMinutes", loadingWaitingMinutes)
        put("workingPeriods", JSONArray().apply { workingPeriods.forEach { put(it.toJson()) } })
        put("plannedWorkingHours", plannedWorkingHours)
        targetQuantity?.let { put("targetQuantity", it) }
        actualProductionRatePerHour?.let { put("actualProductionRatePerHour", it) }
        put("thicknessSpec", thicknessSpec.toJson())
        put("temperatureSpec", temperatureSpec.toJson())
        put("camberMode", camberMode.name)
        put("morthRevision", morthRevision.name)
        put("jmfReference", jmfReference)
        put("trialSectionReference", trialSectionReference)
        put("machinery", JSONArray().apply { machinery.forEach { put(it.toJson()) } })
        put("transport", transport.toJson())
        put("compaction", compaction.toJson())
        put("manpower", JSONArray().apply { manpower.forEach { put(it.toJson()) } })
        put("preparedBy", preparedBy)
        put("checkedBy", checkedBy)
        put("approvedBy", approvedBy)
        put("weatherForecastSnapshotNote", weatherForecastSnapshotNote)
        put("createdAtMs", createdAtMs)
        put("updatedAtMs", updatedAtMs)
    }

    fun toJsonString(): String = toJson().toString()

    companion object {
        fun defaultManpowerRoles(): List<ManpowerRoleAllocation> = listOf(
            // Paving Team
            ManpowerRoleAllocation("mp_paver_op", ManpowerTeamGroup.PAVING_TEAM, "Paver Operator", affectedActivityHint = "Paving"),
            ManpowerRoleAllocation("mp_paver_asst", ManpowerTeamGroup.PAVING_TEAM, "Paver Assistant", affectedActivityHint = "Paving"),
            ManpowerRoleAllocation("mp_screed_op", ManpowerTeamGroup.PAVING_TEAM, "Screed Operator", affectedActivityHint = "Paving"),
            ManpowerRoleAllocation("mp_rakers", ManpowerTeamGroup.PAVING_TEAM, "Asphalt Rakers", affectedActivityHint = "Paving"),
            ManpowerRoleAllocation("mp_asphalt_lab", ManpowerTeamGroup.PAVING_TEAM, "Asphalt Labourers", affectedActivityHint = "Paving"),
            ManpowerRoleAllocation("mp_mat_handle", ManpowerTeamGroup.PAVING_TEAM, "Material Handling Staff", affectedActivityHint = "Paving"),
            // Rolling Team
            ManpowerRoleAllocation("mp_tandem_op", ManpowerTeamGroup.ROLLING_TEAM, "Tandem Roller Operator", affectedActivityHint = "Compaction"),
            ManpowerRoleAllocation("mp_ptr_op", ManpowerTeamGroup.ROLLING_TEAM, "PTR Operator", affectedActivityHint = "Compaction"),
            ManpowerRoleAllocation("mp_roller_asst", ManpowerTeamGroup.ROLLING_TEAM, "Roller Assistants", affectedActivityHint = "Compaction"),
            // Plant and Transport
            ManpowerRoleAllocation("mp_hmp_op", ManpowerTeamGroup.PLANT_TRANSPORT, "HMP Operator", affectedActivityHint = "Plant Production"),
            ManpowerRoleAllocation("mp_plant_help", ManpowerTeamGroup.PLANT_TRANSPORT, "Plant Helpers", affectedActivityHint = "Plant Production"),
            ManpowerRoleAllocation("mp_dumper_drv", ManpowerTeamGroup.PLANT_TRANSPORT, "Dumper / HYVA Drivers", affectedActivityHint = "Material Transport"),
            ManpowerRoleAllocation("mp_loader_op", ManpowerTeamGroup.PLANT_TRANSPORT, "Loader Operator", affectedActivityHint = "Plant Feeding"),
            ManpowerRoleAllocation("mp_mechanic", ManpowerTeamGroup.PLANT_TRANSPORT, "Mechanic", affectedActivityHint = "Equipment Readiness"),
            ManpowerRoleAllocation("mp_electrician", ManpowerTeamGroup.PLANT_TRANSPORT, "Electrician", affectedActivityHint = "Plant Electrical"),
            // Quality and Site Team
            ManpowerRoleAllocation("mp_site_eng", ManpowerTeamGroup.QUALITY_SITE, "Site Engineer", affectedActivityHint = "Site Supervision"),
            ManpowerRoleAllocation("mp_qa_eng", ManpowerTeamGroup.QUALITY_SITE, "Quality Engineer", affectedActivityHint = "QA / QC"),
            ManpowerRoleAllocation("mp_surveyor", ManpowerTeamGroup.QUALITY_SITE, "Surveyor", affectedActivityHint = "Line & Level"),
            ManpowerRoleAllocation("mp_lab_tech", ManpowerTeamGroup.QUALITY_SITE, "Laboratory Technician", affectedActivityHint = "Mix & Compaction QC"),
            ManpowerRoleAllocation("mp_safety", ManpowerTeamGroup.QUALITY_SITE, "Safety Officer", affectedActivityHint = "Work-Zone Safety"),
            ManpowerRoleAllocation("mp_supervisor", ManpowerTeamGroup.QUALITY_SITE, "Supervisor", affectedActivityHint = "Crew Coordination"),
            ManpowerRoleAllocation("mp_marshal", ManpowerTeamGroup.QUALITY_SITE, "Traffic Marshal", affectedActivityHint = "Traffic Control"),
            ManpowerRoleAllocation("mp_flagman", ManpowerTeamGroup.QUALITY_SITE, "Flagman", affectedActivityHint = "Traffic Control")
        )

        fun fromJson(obj: JSONObject): HighwayWorkPlan {
            fun optD(k: String): Double? =
                if (obj.has(k) && !obj.isNull(k)) obj.optDouble(k).takeIf { it.isFinite() } else null
            fun optI(k: String): Int? =
                if (obj.has(k) && !obj.isNull(k)) obj.optInt(k).takeIf { it > 0 } else null
            val machList = mutableListOf<PlannedMachineryItem>()
            obj.optJSONArray("machinery")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { machList.add(PlannedMachineryItem.fromJson(it)) }
                }
            }
            val mpList = mutableListOf<ManpowerRoleAllocation>()
            obj.optJSONArray("manpower")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { mpList.add(ManpowerRoleAllocation.fromJson(it)) }
                }
            }
            val periodList = mutableListOf<WorkingTimePeriod>()
            obj.optJSONArray("workingPeriods")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { periodList.add(WorkingTimePeriod.fromJson(it)) }
                }
            }
            val stretchList = mutableListOf<RoadStretchWidthItem>()
            obj.optJSONArray("stretches")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { stretchList.add(RoadStretchWidthItem.fromJson(it)) }
                }
            }
            val now = System.currentTimeMillis()
            return HighwayWorkPlan(
                planId = obj.optString("planId", UUID.randomUUID().toString()),
                planTitle = obj.optString("planTitle", ""),
                projectName = obj.optString("projectName", ""),
                roadOrHighwayName = obj.optString("roadOrHighwayName", ""),
                locationName = obj.optString("locationName", ""),
                workDateIso = obj.optString("workDateIso", ""),
                activity = PlanningWorkActivity.fromName(obj.optString("activity")),
                customActivityName = obj.optString("customActivityName", ""),
                customMaterialType = obj.optString("customMaterialType", ""),
                startChainage = obj.optString("startChainage", ""),
                endChainage = obj.optString("endChainage", ""),
                roadSide = obj.optString("roadSide", "LHS"),
                workLengthMeters = optD("workLengthMeters"),
                useManualLengthOverride = obj.optBoolean("useManualLengthOverride", false),
                totalRoadWidthMeters = optD("totalRoadWidthMeters"),
                individualLaneWidthMeters = optD("individualLaneWidthMeters"),
                numberOfLanes = optI("numberOfLanes"),
                workWidthMeters = optD("workWidthMeters"),
                stretches = stretchList,
                layerThicknessMm = optD("layerThicknessMm"),
                materialDensityTPerM3 = optD("materialDensityTPerM3"),
                actualQuantityDelivered = optD("actualQuantityDelivered"),
                shiftType = WorkShiftType.fromName(obj.optString("shiftType")),
                shiftStartDateIso = obj.optString("shiftStartDateIso", ""),
                shiftEndDateIso = obj.optString("shiftEndDateIso", ""),
                plannedStartHour24 = obj.optInt("plannedStartHour24", 8).coerceIn(0, 23),
                plannedStartMinute = obj.optInt("plannedStartMinute", 0).coerceIn(0, 59),
                plannedEndHour24 = obj.optInt("plannedEndHour24", 17).coerceIn(0, 23),
                plannedEndMinute = obj.optInt("plannedEndMinute", 0).coerceIn(0, 59),
                breakDurationMinutes = obj.optInt("breakDurationMinutes", 60).coerceIn(0, 720),
                mobilisationMinutes = obj.optInt("mobilisationMinutes", 30).coerceIn(0, 360),
                loadingWaitingMinutes = obj.optInt("loadingWaitingMinutes", 0).coerceIn(0, 360),
                workingPeriods = periodList,
                plannedWorkingHours = obj.optDouble("plannedWorkingHours", 8.0).coerceIn(1.0, 24.0),
                targetQuantity = optD("targetQuantity"),
                actualProductionRatePerHour = optD("actualProductionRatePerHour"),
                thicknessSpec = ThicknessCompactionSpec.fromJson(obj.optJSONObject("thicknessSpec")),
                temperatureSpec = MaterialTemperatureSpec.fromJson(obj.optJSONObject("temperatureSpec")),
                camberMode = RollingCamberMode.fromName(obj.optString("camberMode")),
                morthRevision = MoRTHRevision.fromName(obj.optString("morthRevision")),
                jmfReference = obj.optString("jmfReference", ""),
                trialSectionReference = obj.optString("trialSectionReference", ""),
                machinery = machList,
                transport = TransportPlanningInput.fromJson(obj.optJSONObject("transport")),
                compaction = CompactionPlanningInput.fromJson(obj.optJSONObject("compaction")),
                manpower = if (mpList.isNotEmpty()) mpList else defaultManpowerRoles(),
                preparedBy = obj.optString("preparedBy", ""),
                checkedBy = obj.optString("checkedBy", ""),
                approvedBy = obj.optString("approvedBy", ""),
                weatherForecastSnapshotNote = obj.optString("weatherForecastSnapshotNote", ""),
                createdAtMs = obj.optLong("createdAtMs", now),
                updatedAtMs = obj.optLong("updatedAtMs", now)
            )
        }

        fun fromJsonString(raw: String?): HighwayWorkPlan? {
            if (raw.isNullOrBlank()) return null
            return runCatching { fromJson(JSONObject(raw)) }.getOrNull()
        }

        fun listToJsonString(plans: List<HighwayWorkPlan>): String {
            return JSONArray().apply { plans.forEach { put(it.toJson()) } }.toString()
        }

        fun listFromJsonString(raw: String?): List<HighwayWorkPlan> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { add(fromJson(it)) }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}

/**
 * Single hour inside the Smart Hourly Work Plan combining forecast + machinery capacity + engineering explanation.
 */
data class HourlyWorkPlanSlot(
    val hour24: Int,
    val timeLabel24: String,
    val conditionLabel: String,
    val visualCondition: WeatherConditionType,
    val temperatureC: Double?,
    val rainProbabilityPercent: Int?,
    val windSpeedKmh: Double?,
    val activityName: String,
    val machinerySummary: String,
    val expectedProductionValue: Double?,
    val productionUnit: String,
    val suitability: WorkSuitabilityStatus,
    val engineeringExplanation: String
)

/**
 * Complete estimated planning calculation result (strictly labeled as Estimated Planning Values).
 */
data class HighwayWorkPlanCalculation(
    val workAreaSqm: Double?,
    val materialVolumeM3: Double?,
    val estimatedMaterialTonnes: Double?,
    val hmpCapacityTonnesPerHour: Double?,
    val paverCapacityTonnesPerHour: Double?,
    val transportResult: TransportPlanningResult,
    val compactionResult: CompactionPlanningResult,
    val manpowerSummary: ManpowerPlanningSummary,
    val effectiveHourlyProduction: Double?,
    val productionUnit: String,
    val estimatedDailyProduction: Double?,
    val expectedCompletionHours: Double?,
    val limitingBottleneck: String,
    val overallWeatherSuitability: WorkSuitabilityStatus,
    val bestWorkingWindowText: String,
    val bestWorkingWindowReason: String,
    val hourlySlots: List<HourlyWorkPlanSlot>,
    val dynamicRecommendation: String,
    val isWeatherStaleOrUnavailable: Boolean
)
