package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/**
 * All 14 distinct highway construction activities evaluated by the IRC/MoRTH weather intelligence engine.
 */
enum class HighwayWorkActivity(
    val displayName: String,
    val shortName: String,
    val categoryLabel: String,
    val primaryReference: String,
    val isBituminousHotMix: Boolean,
    val defaultThicknessMm: Double,
    val productionUnitLabel: String
) {
    BC_LAYING(
        displayName = "BC Laying",
        shortName = "BC",
        categoryLabel = "Bituminous Wearing Course",
        primaryReference = "MoRTH 5th Rev Cl. 507.4.1 & 501.5.1 / IRC:105-2019 Cl. 3.3.4",
        isBituminousHotMix = true,
        defaultThicknessMm = 40.0,
        productionUnitLabel = "m²/hr"
    ),
    DBM_LAYING(
        displayName = "DBM Laying",
        shortName = "DBM",
        categoryLabel = "Bituminous Binder Course",
        primaryReference = "MoRTH 5th Rev Cl. 505 & 501.5.1 / IRC:105-2019 Cl. 3.3.4",
        isBituminousHotMix = true,
        defaultThicknessMm = 60.0,
        productionUnitLabel = "m²/hr"
    ),
    TACK_COAT_APPLICATION(
        displayName = "Tack Coat Application",
        shortName = "Tack Coat",
        categoryLabel = "Bituminous Surface Interface",
        primaryReference = "MoRTH 5th Rev Cl. 503.4 & 501.5.1",
        isBituminousHotMix = false,
        defaultThicknessMm = 0.0,
        productionUnitLabel = "m²/hr"
    ),
    PRIME_COAT_APPLICATION(
        displayName = "Prime Coat Application",
        shortName = "Prime Coat",
        categoryLabel = "Granular Base Priming",
        primaryReference = "MoRTH 5th Rev Cl. 502.4 & 501.5.1",
        isBituminousHotMix = false,
        defaultThicknessMm = 0.0,
        productionUnitLabel = "m²/hr"
    ),
    MILLING_OPERATIONS(
        displayName = "Milling Operations",
        shortName = "Milling",
        categoryLabel = "Pavement Scarifying & Profile Milling",
        primaryReference = "MoRTH 5th Rev Cl. 501.8.3 / IRC:SP:83 (Site Safety & Visibility)",
        isBituminousHotMix = false,
        defaultThicknessMm = 50.0,
        productionUnitLabel = "m²/hr"
    ),
    GLASS_GRID_APPLICATION(
        displayName = "Glass Grid Application",
        shortName = "Glass Grid",
        categoryLabel = "Geosynthetic Interlayer",
        primaryReference = "MoRTH Section 700 / Manufacturer & Contract Spec (Dry Clean Surface)",
        isBituminousHotMix = false,
        defaultThicknessMm = 0.0,
        productionUnitLabel = "m²/hr"
    ),
    CRACK_SEALING(
        displayName = "Crack Sealing",
        shortName = "Crack Sealing",
        categoryLabel = "Pavement Maintenance",
        primaryReference = "MoRTH Cl. 3004.3 / IRC:82 (Dry Clean Crack Walls)",
        isBituminousHotMix = false,
        defaultThicknessMm = 0.0,
        productionUnitLabel = "m/hr"
    ),
    SURFACE_PREPARATION_CLEANING(
        displayName = "Surface Preparation and Cleaning",
        shortName = "Surface Prep",
        categoryLabel = "Pre-Laying Preparation",
        primaryReference = "MoRTH 5th Rev Cl. 501.8 / Cl. 503.4.1 (Dry Dust-Free Surface)",
        isBituminousHotMix = false,
        defaultThicknessMm = 0.0,
        productionUnitLabel = "m²/hr"
    ),
    GSB_OPERATIONS(
        displayName = "GSB Operations",
        shortName = "GSB",
        categoryLabel = "Granular Sub-Base",
        primaryReference = "MoRTH 5th Rev Cl. 401.4 (OMC Moisture Control)",
        isBituminousHotMix = false,
        defaultThicknessMm = 200.0,
        productionUnitLabel = "m³/hr"
    ),
    WMM_OPERATIONS(
        displayName = "WMM Operations",
        shortName = "WMM",
        categoryLabel = "Wet Mix Macadam",
        primaryReference = "MoRTH 5th Rev Cl. 406.3 (OMC Moisture & Compaction Control)",
        isBituminousHotMix = false,
        defaultThicknessMm = 250.0,
        productionUnitLabel = "m³/hr"
    ),
    EARTHWORK(
        displayName = "Earthwork",
        shortName = "Earthwork",
        categoryLabel = "Embankment & Subgrade",
        primaryReference = "MoRTH 5th Rev Section 300 Cl. 305.3 (OMC / No Waterlogged Soil)",
        isBituminousHotMix = false,
        defaultThicknessMm = 200.0,
        productionUnitLabel = "m³/hr"
    ),
    ROAD_MARKING(
        displayName = "Road Marking",
        shortName = "Road Marking",
        categoryLabel = "Thermoplastic / Pavement Marking",
        primaryReference = "MoRTH 5th Rev Cl. 803.4 / IRC:35-2015 (Dry Surface, No Rain/Dew)",
        isBituminousHotMix = false,
        defaultThicknessMm = 2.5,
        productionUnitLabel = "m²/hr"
    ),
    CONCRETE_WORKS(
        displayName = "Concrete Works",
        shortName = "Concrete",
        categoryLabel = "PQC / Structural Concrete",
        primaryReference = "MoRTH 5th Rev Section 600 & 1700 / IS:456 / IRC:15",
        isBituminousHotMix = false,
        defaultThicknessMm = 250.0,
        productionUnitLabel = "m³/hr"
    ),
    GENERAL_HIGHWAY_MAINTENANCE(
        displayName = "General Highway Maintenance",
        shortName = "Maintenance",
        categoryLabel = "Routine Corridor Operations",
        primaryReference = "MoRTH Section 3000 / IRC:82 (Activity & Safety Dependent)",
        isBituminousHotMix = false,
        defaultThicknessMm = 0.0,
        productionUnitLabel = "km/hr"
    );

    companion object {
        fun fromName(raw: String?): HighwayWorkActivity =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BC_LAYING
    }
}

/**
 * Specification-driven work suitability status — never an arbitrary numerical weather score.
 */
enum class WorkSuitabilityStatus(val label: String) {
    SUITABLE("Suitable"),
    SUITABLE_WITH_CAUTION("Suitable with Caution"),
    NOT_RECOMMENDED("Not Recommended"),
    INSUFFICIENT_DATA("Insufficient Data");

    companion object {
        fun fromName(raw: String?): WorkSuitabilityStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: INSUFFICIENT_DATA
    }
}

/**
 * Applicable MoRTH Specifications for Road and Bridge Works revision.
 */
enum class MoRTHRevision(val label: String, val shortLabel: String) {
    FIFTH_REVISION_2013(
        label = "MoRTH Fifth Revision (2013)",
        shortLabel = "MoRTH 5th Rev (2013)"
    ),
    SIXTH_REVISION_2026(
        label = "MoRTH Sixth Revision (2026) — NH Effective 1 Jan 2027",
        shortLabel = "MoRTH 6th Rev (2026)"
    );

    companion object {
        /**
         * Stated effective implementation date for National Highway works under MoRTH 6th Revision:
         * 1 January 2027 00:00:00 IST (UTC+05:30).
         */
        val SIXTH_REVISION_NH_EFFECTIVE_MS: Long = Calendar.getInstance(TimeZone.getTimeZone("GMT+05:30")).apply {
            set(2027, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun fromName(raw: String?): MoRTHRevision =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FIFTH_REVISION_2013
    }
}

/**
 * Bitumen binder classification controlling mandatory pavement surface temperature thresholds
 * under MoRTH 5th Rev Cl. 501.5.1 and IRC:105-2019 Cl. 3.3.4.
 */
enum class BitumenBinderType(
    val displayName: String,
    val isModified: Boolean,
    val minPavementSurfaceTempC: Double,
    val defaultLayingTempMinC: Double,
    val defaultRollingTempMinC: Double
) {
    CONVENTIONAL_VG30("VG-30 (Conventional)", false, 10.0, 140.0, 90.0),
    CONVENTIONAL_VG40("VG-40 (Conventional)", false, 10.0, 145.0, 95.0),
    MODIFIED_PMB("PMB (Polymer Modified)", true, 15.0, 155.0, 105.0),
    MODIFIED_CRMB("CRMB (Crumb Rubber Modified)", true, 15.0, 155.0, 105.0),
    EMULSION_RS1("Bitumen Emulsion RS-1", false, 10.0, 20.0, 20.0),
    EMULSION_SS1("Bitumen Emulsion SS-1", false, 10.0, 20.0, 20.0);

    companion object {
        fun fromName(raw: String?): BitumenBinderType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CONVENTIONAL_VG30
    }
}

enum class SurfaceMoistureCondition(val label: String) {
    UNKNOWN("Not Verified on Site"),
    DRY("Dry & Clean"),
    DAMP("Damp Base / Binder"),
    WET_NO_STANDING_WATER("Wet Surface (No Ponding)"),
    STANDING_WATER("Standing Water on Surface");

    companion object {
        fun fromName(raw: String?): SurfaceMoistureCondition =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class TackCoatCondition(val label: String) {
    UNKNOWN("Not Verified"),
    CURED_AND_INTACT("Broken / Cured & Intact"),
    FRESH_NOT_CURED("Fresh (Not Yet Broken)"),
    CONTAMINATED_OR_WASHED("Contaminated / Rain-Washed"),
    NOT_APPLIED("Not Yet Applied");

    companion object {
        fun fromName(raw: String?): TackCoatCondition =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ValueSourceTag(val label: String) {
    USER_ENTERED("User-Entered"),
    HISTORICAL("Historical"),
    ESTIMATED("Estimated");

    companion object {
        fun fromName(raw: String?): ValueSourceTag =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: USER_ENTERED
    }
}

/**
 * Contract-specific specification configuration controlling how MoRTH and IRC rules are applied.
 * MoRTH 6th Revision never auto-replaces MoRTH 5th Revision without explicit contract selection.
 */
data class ContractSpecificationConfig(
    val projectName: String = "",
    val morthRevision: MoRTHRevision = MoRTHRevision.FIFTH_REVISION_2013,
    val sixthRevisionContractVerified: Boolean = false,
    val ircReferences: String = "IRC:105-2019 (Cl. 3.3.4), IRC:111-2009, IRC:35-2015",
    val contractAmendments: String = "",
    val approvedMethodology: String = "",
    val bcMixDesignation: String = "BC Grading-2 (VG-30 / Contract Approved)",
    val dbmMixDesignation: String = "DBM Grading-2 (VG-30 / Contract Approved)",
    val bcBinderType: BitumenBinderType = BitumenBinderType.CONVENTIONAL_VG30,
    val dbmBinderType: BitumenBinderType = BitumenBinderType.CONVENTIONAL_VG30,
    val bcLayingTempMinC: Double? = null,
    val bcRollingTempMinC: Double? = null,
    val dbmLayingTempMinC: Double? = null,
    val dbmRollingTempMinC: Double? = null,
    val engineerApprovedWeatherNotes: String = ""
) {
    fun binderFor(activity: HighwayWorkActivity): BitumenBinderType = when (activity) {
        HighwayWorkActivity.BC_LAYING -> bcBinderType
        HighwayWorkActivity.DBM_LAYING -> dbmBinderType
        HighwayWorkActivity.TACK_COAT_APPLICATION -> BitumenBinderType.EMULSION_RS1
        HighwayWorkActivity.PRIME_COAT_APPLICATION -> BitumenBinderType.EMULSION_SS1
        else -> bcBinderType
    }

    fun isSixthRevisionEffective(referenceTimeMs: Long): Boolean {
        return referenceTimeMs >= MoRTHRevision.SIXTH_REVISION_NH_EFFECTIVE_MS
    }

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("projectName", projectName)
        put("morthRevision", morthRevision.name)
        put("sixthRevisionContractVerified", sixthRevisionContractVerified)
        put("ircReferences", ircReferences)
        put("contractAmendments", contractAmendments)
        put("approvedMethodology", approvedMethodology)
        put("bcMixDesignation", bcMixDesignation)
        put("dbmMixDesignation", dbmMixDesignation)
        put("bcBinderType", bcBinderType.name)
        put("dbmBinderType", dbmBinderType.name)
        bcLayingTempMinC?.let { put("bcLayingTempMinC", it) }
        bcRollingTempMinC?.let { put("bcRollingTempMinC", it) }
        dbmLayingTempMinC?.let { put("dbmLayingTempMinC", it) }
        dbmRollingTempMinC?.let { put("dbmRollingTempMinC", it) }
        put("engineerApprovedWeatherNotes", engineerApprovedWeatherNotes)
    }

    companion object {
        fun fromJsonObject(obj: JSONObject?): ContractSpecificationConfig {
            if (obj == null) return ContractSpecificationConfig()
            return ContractSpecificationConfig(
                projectName = obj.optString("projectName", ""),
                morthRevision = MoRTHRevision.fromName(obj.optString("morthRevision", "")),
                sixthRevisionContractVerified = obj.optBoolean("sixthRevisionContractVerified", false),
                ircReferences = obj.optString("ircReferences", "IRC:105-2019 (Cl. 3.3.4), IRC:111-2009, IRC:35-2015"),
                contractAmendments = obj.optString("contractAmendments", ""),
                approvedMethodology = obj.optString("approvedMethodology", ""),
                bcMixDesignation = obj.optString("bcMixDesignation", "BC Grading-2 (VG-30 / Contract Approved)"),
                dbmMixDesignation = obj.optString("dbmMixDesignation", "DBM Grading-2 (VG-30 / Contract Approved)"),
                bcBinderType = BitumenBinderType.fromName(obj.optString("bcBinderType", "")),
                dbmBinderType = BitumenBinderType.fromName(obj.optString("dbmBinderType", "")),
                bcLayingTempMinC = if (obj.has("bcLayingTempMinC") && !obj.isNull("bcLayingTempMinC")) obj.optDouble("bcLayingTempMinC") else null,
                bcRollingTempMinC = if (obj.has("bcRollingTempMinC") && !obj.isNull("bcRollingTempMinC")) obj.optDouble("bcRollingTempMinC") else null,
                dbmLayingTempMinC = if (obj.has("dbmLayingTempMinC") && !obj.isNull("dbmLayingTempMinC")) obj.optDouble("dbmLayingTempMinC") else null,
                dbmRollingTempMinC = if (obj.has("dbmRollingTempMinC") && !obj.isNull("dbmRollingTempMinC")) obj.optDouble("dbmRollingTempMinC") else null,
                engineerApprovedWeatherNotes = obj.optString("engineerApprovedWeatherNotes", "")
            )
        }
    }
}

/**
 * Actual on-site physical measurements — kept strictly separate from Forecast Information.
 */
data class ActualSiteMeasurements(
    val pavementSurfaceTempC: Double? = null,
    val baseMoistureCondition: SurfaceMoistureCondition = SurfaceMoistureCondition.UNKNOWN,
    val hasStandingWater: Boolean = false,
    val bituminousMixTempC: Double? = null,
    val layingTempC: Double? = null,
    val rollingTempC: Double? = null,
    val surfaceConditionNote: String = "",
    val tackCoatCondition: TackCoatCondition = TackCoatCondition.UNKNOWN,
    val measuredAtMs: Long = 0L,
    val verifiedByEngineer: Boolean = false
) {
    val hasAnyMeasurement: Boolean
        get() = pavementSurfaceTempC != null ||
            baseMoistureCondition != SurfaceMoistureCondition.UNKNOWN ||
            hasStandingWater ||
            bituminousMixTempC != null ||
            layingTempC != null ||
            rollingTempC != null ||
            tackCoatCondition != TackCoatCondition.UNKNOWN

    fun toJsonString(): String = JSONObject().apply {
        pavementSurfaceTempC?.let { put("pavementSurfaceTempC", it) }
        put("baseMoistureCondition", baseMoistureCondition.name)
        put("hasStandingWater", hasStandingWater)
        bituminousMixTempC?.let { put("bituminousMixTempC", it) }
        layingTempC?.let { put("layingTempC", it) }
        rollingTempC?.let { put("rollingTempC", it) }
        put("surfaceConditionNote", surfaceConditionNote)
        put("tackCoatCondition", tackCoatCondition.name)
        put("measuredAtMs", measuredAtMs)
        put("verifiedByEngineer", verifiedByEngineer)
    }.toString()

    companion object {
        fun fromJsonString(raw: String?): ActualSiteMeasurements {
            if (raw.isNullOrBlank()) return ActualSiteMeasurements()
            return try {
                val obj = JSONObject(raw)
                ActualSiteMeasurements(
                    pavementSurfaceTempC = if (obj.has("pavementSurfaceTempC") && !obj.isNull("pavementSurfaceTempC")) obj.optDouble("pavementSurfaceTempC") else null,
                    baseMoistureCondition = SurfaceMoistureCondition.fromName(obj.optString("baseMoistureCondition", "")),
                    hasStandingWater = obj.optBoolean("hasStandingWater", false),
                    bituminousMixTempC = if (obj.has("bituminousMixTempC") && !obj.isNull("bituminousMixTempC")) obj.optDouble("bituminousMixTempC") else null,
                    layingTempC = if (obj.has("layingTempC") && !obj.isNull("layingTempC")) obj.optDouble("layingTempC") else null,
                    rollingTempC = if (obj.has("rollingTempC") && !obj.isNull("rollingTempC")) obj.optDouble("rollingTempC") else null,
                    surfaceConditionNote = obj.optString("surfaceConditionNote", ""),
                    tackCoatCondition = TackCoatCondition.fromName(obj.optString("tackCoatCondition", "")),
                    measuredAtMs = obj.optLong("measuredAtMs", 0L),
                    verifiedByEngineer = obj.optBoolean("verifiedByEngineer", false)
                )
            } catch (_: Exception) {
                ActualSiteMeasurements()
            }
        }
    }
}

/**
 * Individual specification rule evaluation check with explicit clause citation.
 */
data class SpecificationRuleCheck(
    val ruleName: String,
    val clauseReference: String,
    val isMandatoryRestriction: Boolean,
    val status: WorkSuitabilityStatus,
    val observedValue: String,
    val detail: String,
    val requiresSiteVerification: Boolean = false
)

/**
 * Complete rule-based weather suitability result for one highway construction activity.
 */
data class ActivitySuitabilityAssessment(
    val activity: HighwayWorkActivity,
    val status: WorkSuitabilityStatus,
    val summaryReason: String,
    val applicableReference: String,
    val checks: List<SpecificationRuleCheck>,
    val requiresSiteSurfaceTempVerification: Boolean,
    val requiresEngineerApprovalNote: String = ENGINEER_APPROVAL_DISCLAIMER,
    val dataTimestampMs: Long,
    val isForecastStale: Boolean = false
) {
    val violatedMandatoryChecks: List<SpecificationRuleCheck>
        get() = checks.filter { it.isMandatoryRestriction && it.status == WorkSuitabilityStatus.NOT_RECOMMENDED }

    companion object {
        const val ENGINEER_APPROVAL_DISCLAIMER =
            "Weather suitability is an engineering decision-support assessment only. It does NOT automatically approve laying, material acceptance, or quality compliance; final execution remains subject to contract specifications, approved methodology, site measurements, and Engineer approval."
    }
}

/**
 * Single hour in the Smart Hourly Work Suitability Timeline.
 */
data class HourlyWorkWindow(
    val hourPoint: HourlyWeatherPoint,
    val activity: HighwayWorkActivity,
    val status: WorkSuitabilityStatus,
    val confidence: ForecastConfidence,
    val primaryReason: String,
    val restrictions: List<String>,
    val isMandatoryViolation: Boolean
)

/**
 * User-configured or project-linked inputs for Highway Work Target & Completion Prediction.
 * Missing production rates are NEVER invented — null indicates user input is required.
 */
data class WorkTargetInput(
    val activity: HighwayWorkActivity = HighwayWorkActivity.BC_LAYING,
    val startChainageKm: Double? = null,
    val endChainageKm: Double? = null,
    val workingWidthMeters: Double = 7.0,
    val layerThicknessMm: Double = 40.0,
    val plannedStartHour: Int = 7,
    val availableWorkingHours: Double = 10.0,
    val expectedProductionRatePerHour: Double? = null, // Area m²/hr, Volume m³/hr, m/hr, or km/hr depending on activity
    val productionRateSource: ValueSourceTag = ValueSourceTag.USER_ENTERED,
    val paverCapacitySqmPerHour: Double? = null,
    val rollerCountAvailable: Int? = null,
    val hmpCapacityTph: Double? = null,
    val compactBulkDensityTPerM3: Double = 2.38, // Standard compacted BC/DBM density for HMP tonnage-to-area check
    val dumpersCount: Int? = null,
    val dumperPayloadTonnes: Double = 18.0,
    val dumperCycleMinutes: Double = 45.0,
    val manpowerAvailabilityPercent: Int = 100,
    val prepAndInspectionHours: Double = 0.75,
    val wetSurfaceRecoveryHoursPerRainEvent: Double = 1.5,
    val nightWorkAllowed: Boolean = false,
    val workingRestrictionNotes: String = ""
) {
    val totalLengthMeters: Double
        get() {
            val s = startChainageKm ?: return 0.0
            val e = endChainageKm ?: return 0.0
            return kotlin.math.abs(e - s) * 1000.0
        }

    fun toJsonString(): String = JSONObject().apply {
        put("activity", activity.name)
        startChainageKm?.let { put("startChainageKm", it) }
        endChainageKm?.let { put("endChainageKm", it) }
        put("workingWidthMeters", workingWidthMeters)
        put("layerThicknessMm", layerThicknessMm)
        put("plannedStartHour", plannedStartHour)
        put("availableWorkingHours", availableWorkingHours)
        expectedProductionRatePerHour?.let { put("expectedProductionRatePerHour", it) }
        put("productionRateSource", productionRateSource.name)
        paverCapacitySqmPerHour?.let { put("paverCapacitySqmPerHour", it) }
        rollerCountAvailable?.let { put("rollerCountAvailable", it) }
        hmpCapacityTph?.let { put("hmpCapacityTph", it) }
        put("compactBulkDensityTPerM3", compactBulkDensityTPerM3)
        dumpersCount?.let { put("dumpersCount", it) }
        put("dumperPayloadTonnes", dumperPayloadTonnes)
        put("dumperCycleMinutes", dumperCycleMinutes)
        put("manpowerAvailabilityPercent", manpowerAvailabilityPercent)
        put("prepAndInspectionHours", prepAndInspectionHours)
        put("wetSurfaceRecoveryHoursPerRainEvent", wetSurfaceRecoveryHoursPerRainEvent)
        put("nightWorkAllowed", nightWorkAllowed)
        put("workingRestrictionNotes", workingRestrictionNotes)
    }.toString()

    companion object {
        fun fromJsonString(raw: String?): WorkTargetInput {
            if (raw.isNullOrBlank()) return WorkTargetInput()
            return try {
                val obj = JSONObject(raw)
                WorkTargetInput(
                    activity = HighwayWorkActivity.fromName(obj.optString("activity", "")),
                    startChainageKm = if (obj.has("startChainageKm") && !obj.isNull("startChainageKm")) obj.optDouble("startChainageKm") else null,
                    endChainageKm = if (obj.has("endChainageKm") && !obj.isNull("endChainageKm")) obj.optDouble("endChainageKm") else null,
                    workingWidthMeters = obj.optDouble("workingWidthMeters", 7.0).coerceAtLeast(0.5),
                    layerThicknessMm = obj.optDouble("layerThicknessMm", 40.0).coerceAtLeast(0.0),
                    plannedStartHour = obj.optInt("plannedStartHour", 7).coerceIn(0, 23),
                    availableWorkingHours = obj.optDouble("availableWorkingHours", 10.0).coerceIn(0.5, 24.0),
                    expectedProductionRatePerHour = if (obj.has("expectedProductionRatePerHour") && !obj.isNull("expectedProductionRatePerHour")) obj.optDouble("expectedProductionRatePerHour") else null,
                    productionRateSource = ValueSourceTag.fromName(obj.optString("productionRateSource", "")),
                    paverCapacitySqmPerHour = if (obj.has("paverCapacitySqmPerHour") && !obj.isNull("paverCapacitySqmPerHour")) obj.optDouble("paverCapacitySqmPerHour") else null,
                    rollerCountAvailable = if (obj.has("rollerCountAvailable") && !obj.isNull("rollerCountAvailable")) obj.optInt("rollerCountAvailable") else null,
                    hmpCapacityTph = if (obj.has("hmpCapacityTph") && !obj.isNull("hmpCapacityTph")) obj.optDouble("hmpCapacityTph") else null,
                    compactBulkDensityTPerM3 = obj.optDouble("compactBulkDensityTPerM3", 2.38).coerceIn(1.2, 3.0),
                    dumpersCount = if (obj.has("dumpersCount") && !obj.isNull("dumpersCount")) obj.optInt("dumpersCount") else null,
                    dumperPayloadTonnes = obj.optDouble("dumperPayloadTonnes", 18.0).coerceAtLeast(1.0),
                    dumperCycleMinutes = obj.optDouble("dumperCycleMinutes", 45.0).coerceAtLeast(5.0),
                    manpowerAvailabilityPercent = obj.optInt("manpowerAvailabilityPercent", 100).coerceIn(10, 100),
                    prepAndInspectionHours = obj.optDouble("prepAndInspectionHours", 0.75).coerceIn(0.0, 6.0),
                    wetSurfaceRecoveryHoursPerRainEvent = obj.optDouble("wetSurfaceRecoveryHoursPerRainEvent", 1.5).coerceIn(0.0, 8.0),
                    nightWorkAllowed = obj.optBoolean("nightWorkAllowed", false),
                    workingRestrictionNotes = obj.optString("workingRestrictionNotes", "")
                )
            } catch (_: Exception) {
                WorkTargetInput()
            }
        }
    }
}

/**
 * Work Target & Completion Prediction output — explicitly marked as an engineering estimate.
 */
data class WorkTargetPrediction(
    val activity: HighwayWorkActivity,
    val isCalculationComplete: Boolean,
    val missingInputWarnings: List<String>,
    val totalLengthMeters: Double,
    val totalAreaSqm: Double,
    val compactedVolumeM3: Double,
    val quantityBasisNote: String,
    val plannedTargetQuantity: Double,
    val weatherAdjustedTargetQuantity: Double,
    val quantityUnit: String,
    val effectiveHourlyRate: Double,
    val rateSourceTag: ValueSourceTag,
    val limitingResourceNote: String,
    val plannedWorkingHours: Double,
    val suitableWeatherHours: Double,
    val weatherInterruptionHours: Double,
    val wetSurfaceRecoveryHours: Double,
    val prepAndInspectionHours: Double,
    val netEffectiveWorkingHours: Double,
    val expectedCompletionPercent: Double,
    val estimatedCompletionHours: Double?,
    val estimatedDelayHours: Double,
    val remainingBalanceQuantity: Double,
    val remainingHoursNeeded: Double?,
    val expectedCompletionDaysEstimate: Double?,
    val alternativeWorkWindowLabel: String?,
    val targetReductionReasons: List<String>,
    val confidence: ForecastConfidence,
    val estimateDisclaimer: String = ESTIMATE_DISCLAIMER
) {
    companion object {
        const val ESTIMATE_DISCLAIMER =
            "ESTIMATE ONLY — Not a guaranteed production or completion commitment. Actual output depends on verified site conditions, plant supply, haulage, equipment readiness, and Engineer approval."
    }
}

data class DelayRiskWarning(
    val title: String,
    val severity: WeatherAlertSeverity,
    val probabilityRangeLabel: String,
    val confidence: ForecastConfidence,
    val impactedWindowLabel: String,
    val impactSummary: String
)

data class DelayRiskSummary(
    val warnings: List<DelayRiskWarning>,
    val estimatedLostHoursMin: Double,
    val estimatedLostHoursMax: Double,
    val expectedDelayRangeLabel: String,
    val nextSuitableWindowLabel: String?,
    val dailyTargetImpactSummary: String,
    val overallConfidence: ForecastConfidence
)

enum class WindSpeedUnit(val symbol: String, val label: String) {
    KMH("km/h", "km/h"),
    MS("m/s", "m/s"),
    MPH("mph", "mph");

    fun format(kmh: Double): String {
        val converted = when (this) {
            KMH -> kmh
            MS -> kmh / 3.6
            MPH -> kmh * 0.621371
        }
        return if (this == MS) String.format(java.util.Locale.US, "%.1f %s", converted, symbol)
        else "${Math.round(converted)} $symbol"
    }

    companion object {
        fun fromName(raw: String?): WindSpeedUnit =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: KMH
    }
}

/**
 * Persisted settings for the Weather Page contextual Settings popup.
 * Preserves all existing JSON keys for seamless migration and zero data loss.
 */
data class WeatherIntelligenceSettings(
    // 1. Weather Display
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeedUnit: WindSpeedUnit = WindSpeedUnit.KMH,
    val compactForecastLayout: Boolean = true,
    val animationsEnabled: Boolean = true,
    val showHomeHourlyStrip: Boolean = true,
    val showRainProbabilityOnHomeCard: Boolean = true,
    // 2. Hourly and Daily Forecast
    val showHourlyForecastSection: Boolean = true,
    val showDailyForecastSection: Boolean = true,
    val forecastDaysCount: Int = 7,
    val showHourlyTrendChart: Boolean = true,
    val showSevenDayUncertaintyIndicators: Boolean = true,
    // 3. Activity Prediction
    val activityPredictionEnabled: Boolean = true,
    val predictionIntervalHours: Int = 1,
    val showSuitabilityIndicators: Boolean = true,
    val showBestWorkingWindow: Boolean = true,
    // 4. Weather Alerts
    val weatherAlertsEnabled: Boolean = true,
    val alertExpectedRain: Boolean = true,
    val alertHeavyRain: Boolean = true,
    val alertThunderstorms: Boolean = true,
    val alertStrongWind: Boolean = true,
    val alertExtremeTemps: Boolean = true,
    val alertFogVisibility: Boolean = true,
    val alertUnsuitableBituminousConditions: Boolean = true,
    val alertWorkTargetChanges: Boolean = true,
    val alertRainProbabilityThresholdPercent: Int = 55,
    val alertStrongWindThresholdKmh: Int = 40,
    val alertHighTempThresholdC: Int = 42,
    val alertLowTempThresholdC: Int = 10,
    // 5. Highway Work Activities
    val primaryActivity: HighwayWorkActivity = HighwayWorkActivity.BC_LAYING,
    val enabledAlertActivities: Set<HighwayWorkActivity> = setOf(
        HighwayWorkActivity.BC_LAYING,
        HighwayWorkActivity.DBM_LAYING,
        HighwayWorkActivity.TACK_COAT_APPLICATION,
        HighwayWorkActivity.PRIME_COAT_APPLICATION,
        HighwayWorkActivity.ROAD_MARKING,
        HighwayWorkActivity.CONCRETE_WORKS
    ),
    // 6. Contract Specification & IRC/MoRTH References
    val contractSpec: ContractSpecificationConfig = ContractSpecificationConfig(),
    // 7. Project Work Targets & Production Rates
    val savedProductionRatesByActivity: Map<HighwayWorkActivity, Double> = emptyMap(),
    val defaultProductionRateSource: ValueSourceTag = ValueSourceTag.USER_ENTERED,
    // 8. Weather Data Preferences
    val autoRefreshEnabled: Boolean = true,
    val autoRefreshIntervalMinutes: Int = 15,
    val staleDataThresholdMinutes: Int = 60,
    val preferCachedForecastOffline: Boolean = true
) {
    fun toJsonString(): String = JSONObject().apply {
        put("temperatureUnit", temperatureUnit.name)
        put("windSpeedUnit", windSpeedUnit.name)
        put("compactForecastLayout", compactForecastLayout)
        put("animationsEnabled", animationsEnabled)
        put("showHomeHourlyStrip", showHomeHourlyStrip)
        put("showRainProbabilityOnHomeCard", showRainProbabilityOnHomeCard)
        put("showHourlyForecastSection", showHourlyForecastSection)
        put("showDailyForecastSection", showDailyForecastSection)
        put("forecastDaysCount", forecastDaysCount)
        put("showHourlyTrendChart", showHourlyTrendChart)
        put("showSevenDayUncertaintyIndicators", showSevenDayUncertaintyIndicators)
        put("activityPredictionEnabled", activityPredictionEnabled)
        put("predictionIntervalHours", predictionIntervalHours)
        put("showSuitabilityIndicators", showSuitabilityIndicators)
        put("showBestWorkingWindow", showBestWorkingWindow)
        put("weatherAlertsEnabled", weatherAlertsEnabled)
        put("alertExpectedRain", alertExpectedRain)
        put("alertHeavyRain", alertHeavyRain)
        put("alertThunderstorms", alertThunderstorms)
        put("alertStrongWind", alertStrongWind)
        put("alertExtremeTemps", alertExtremeTemps)
        put("alertFogVisibility", alertFogVisibility)
        put("alertUnsuitableBituminousConditions", alertUnsuitableBituminousConditions)
        put("alertWorkTargetChanges", alertWorkTargetChanges)
        put("alertRainProbabilityThresholdPercent", alertRainProbabilityThresholdPercent)
        put("alertStrongWindThresholdKmh", alertStrongWindThresholdKmh)
        put("alertHighTempThresholdC", alertHighTempThresholdC)
        put("alertLowTempThresholdC", alertLowTempThresholdC)
        put("primaryActivity", primaryActivity.name)
        put("enabledAlertActivities", JSONArray().apply { enabledAlertActivities.forEach { put(it.name) } })
        put("contractSpec", contractSpec.toJsonObject())
        put("savedProductionRatesByActivity", JSONObject().apply {
            savedProductionRatesByActivity.forEach { (act, rate) -> put(act.name, rate) }
        })
        put("defaultProductionRateSource", defaultProductionRateSource.name)
        put("autoRefreshEnabled", autoRefreshEnabled)
        put("autoRefreshIntervalMinutes", autoRefreshIntervalMinutes)
        put("staleDataThresholdMinutes", staleDataThresholdMinutes)
        put("preferCachedForecastOffline", preferCachedForecastOffline)
    }.toString()

    companion object {
        fun fromJsonString(raw: String?): WeatherIntelligenceSettings {
            if (raw.isNullOrBlank()) return WeatherIntelligenceSettings()
            return try {
                val obj = JSONObject(raw)
                val actSet = buildSet {
                    val arr = obj.optJSONArray("enabledAlertActivities")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val name = arr.optString(i, "")
                            HighwayWorkActivity.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let(::add)
                        }
                    }
                }.ifEmpty { WeatherIntelligenceSettings().enabledAlertActivities }

                val rateMap = buildMap<HighwayWorkActivity, Double> {
                    val ratesObj = obj.optJSONObject("savedProductionRatesByActivity")
                    if (ratesObj != null) {
                        val keys = ratesObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val act = HighwayWorkActivity.entries.firstOrNull { it.name.equals(k, ignoreCase = true) }
                            val v = ratesObj.optDouble(k, -1.0)
                            if (act != null && v > 0.0) put(act, v)
                        }
                    }
                }

                WeatherIntelligenceSettings(
                    temperatureUnit = TemperatureUnit.fromName(obj.optString("temperatureUnit", "")),
                    windSpeedUnit = WindSpeedUnit.fromName(obj.optString("windSpeedUnit", "")),
                    compactForecastLayout = obj.optBoolean("compactForecastLayout", true),
                    animationsEnabled = obj.optBoolean("animationsEnabled", true),
                    showHomeHourlyStrip = obj.optBoolean("showHomeHourlyStrip", true),
                    showRainProbabilityOnHomeCard = obj.optBoolean("showRainProbabilityOnHomeCard", true),
                    showHourlyForecastSection = obj.optBoolean("showHourlyForecastSection", true),
                    showDailyForecastSection = obj.optBoolean("showDailyForecastSection", true),
                    forecastDaysCount = obj.optInt("forecastDaysCount", 7).coerceIn(3, 7),
                    showHourlyTrendChart = obj.optBoolean("showHourlyTrendChart", true),
                    showSevenDayUncertaintyIndicators = obj.optBoolean("showSevenDayUncertaintyIndicators", true),
                    activityPredictionEnabled = obj.optBoolean("activityPredictionEnabled", true),
                    predictionIntervalHours = obj.optInt("predictionIntervalHours", 1).coerceIn(1, 3),
                    showSuitabilityIndicators = obj.optBoolean("showSuitabilityIndicators", true),
                    showBestWorkingWindow = obj.optBoolean("showBestWorkingWindow", true),
                    weatherAlertsEnabled = obj.optBoolean("weatherAlertsEnabled", true),
                    alertExpectedRain = obj.optBoolean("alertExpectedRain", true),
                    alertHeavyRain = obj.optBoolean("alertHeavyRain", true),
                    alertThunderstorms = obj.optBoolean("alertThunderstorms", true),
                    alertStrongWind = obj.optBoolean("alertStrongWind", true),
                    alertExtremeTemps = obj.optBoolean("alertExtremeTemps", true),
                    alertFogVisibility = obj.optBoolean("alertFogVisibility", true),
                    alertUnsuitableBituminousConditions = obj.optBoolean("alertUnsuitableBituminousConditions", true),
                    alertWorkTargetChanges = obj.optBoolean("alertWorkTargetChanges", true),
                    alertRainProbabilityThresholdPercent = obj.optInt("alertRainProbabilityThresholdPercent", 55).coerceIn(10, 100),
                    alertStrongWindThresholdKmh = obj.optInt("alertStrongWindThresholdKmh", 40).coerceIn(15, 100),
                    alertHighTempThresholdC = obj.optInt("alertHighTempThresholdC", 42).coerceIn(30, 55),
                    alertLowTempThresholdC = obj.optInt("alertLowTempThresholdC", 10).coerceIn(-10, 25),
                    primaryActivity = HighwayWorkActivity.fromName(obj.optString("primaryActivity", "")),
                    enabledAlertActivities = actSet,
                    contractSpec = ContractSpecificationConfig.fromJsonObject(obj.optJSONObject("contractSpec")),
                    savedProductionRatesByActivity = rateMap,
                    defaultProductionRateSource = ValueSourceTag.fromName(obj.optString("defaultProductionRateSource", "")),
                    autoRefreshEnabled = obj.optBoolean("autoRefreshEnabled", true),
                    autoRefreshIntervalMinutes = obj.optInt("autoRefreshIntervalMinutes", 15).coerceIn(5, 120),
                    staleDataThresholdMinutes = obj.optInt("staleDataThresholdMinutes", 60).coerceIn(15, 360),
                    preferCachedForecastOffline = obj.optBoolean("preferCachedForecastOffline", true)
                )
            } catch (_: Exception) {
                WeatherIntelligenceSettings()
            }
        }
    }
}
