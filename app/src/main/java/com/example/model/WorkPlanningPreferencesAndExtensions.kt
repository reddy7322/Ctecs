package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

enum class WorkShiftType(val label: String) {
    DAY_SHIFT("Day Shift"),
    NIGHT_SHIFT("Night Shift"),
    CUSTOM_SHIFT("Custom Shift");

    companion object {
        fun fromName(raw: String?): WorkShiftType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DAY_SHIFT
    }
}

enum class RollingCamberMode(val label: String) {
    CROWN_CENTER("Crowned (Edge → Centre)"),
    SUPERELEVATION_LEFT("Superelevation (Left Lower → Right Upper)"),
    SUPERELEVATION_RIGHT("Superelevation (Right Lower → Left Upper)"),
    FLAT("Flat / Crossfall Verification");

    companion object {
        fun fromName(raw: String?): RollingCamberMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CROWN_CENTER
    }
}

enum class WorkPlanSaveStatus(val label: String) {
    IDLE(""),
    SAVING("Saving…"),
    SAVED("Saved"),
    OFFLINE_SAVED_LOCALLY("Offline – Saved Locally"),
    SAVE_FAILED("Save Failed – Retry")
}

enum class BackupFrequency(val label: String) {
    MANUAL_ONLY("Manual Only"),
    DAILY("Daily"),
    WEEKLY("Weekly");

    companion object {
        fun fromName(raw: String?): BackupFrequency =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MANUAL_ONLY
    }
}

enum class BackupDuplicateResolution(val label: String) {
    SKIP("Skip"),
    REPLACE("Replace"),
    IMPORT_AS_NEW("Import as New")
}

enum class PdfExportScope(val label: String) {
    COMPLETE_WORK_PLAN("Complete Work Plan"),
    SELECTED_ACTIVITY("Selected Activity"),
    MACHINERY_MANPOWER("Machinery & Manpower"),
    MATERIAL_COMPACTION("Material & Compaction"),
    WEATHER_RECOMMENDATIONS("Weather Recommendations"),
    FULL_COMBINED_REPORT("Full Combined Report");

    companion object {
        fun fromName(raw: String?): PdfExportScope =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: COMPLETE_WORK_PLAN
    }
}

enum class MachineryFilterPreference(val label: String) {
    ALL("All Models"),
    LATEST_CURRENT("Latest / Current"),
    LEGACY("Legacy Only");

    companion object {
        fun fromName(raw: String?): MachineryFilterPreference =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ALL
    }
}

/**
 * A single time-to-time working period (supports same-day and overnight midnight-crossing dates).
 */
data class WorkingTimePeriod(
    val id: String = UUID.randomUUID().toString(),
    val startDateIso: String = "",
    val startHour24: Int = 8,
    val startMinute: Int = 0,
    val endDateIso: String = "",
    val endHour24: Int = 12,
    val endMinute: Int = 0,
    val label: String = ""
) {
    fun durationMinutes(): Int {
        val startTotal = startHour24.coerceIn(0, 23) * 60 + startMinute.coerceIn(0, 59)
        val endTotal = endHour24.coerceIn(0, 23) * 60 + endMinute.coerceIn(0, 59)
        val dayDelta = computeDateDiffDays(startDateIso, endDateIso)
        return when {
            dayDelta != null && dayDelta > 0 -> (dayDelta * 1440) + (endTotal - startTotal)
            dayDelta != null && dayDelta == 0 && endTotal >= startTotal -> endTotal - startTotal
            endTotal > startTotal -> endTotal - startTotal
            endTotal < startTotal -> (1440 - startTotal) + endTotal // Overnight crossing midnight
            else -> 0
        }.coerceAtLeast(0)
    }

    fun formattedRange(): String {
        val s = String.format(Locale.US, "%02d:%02d", startHour24.coerceIn(0, 23), startMinute.coerceIn(0, 59))
        val e = String.format(Locale.US, "%02d:%02d", endHour24.coerceIn(0, 23), endMinute.coerceIn(0, 59))
        return if (startDateIso.isNotBlank() && endDateIso.isNotBlank() && startDateIso != endDateIso) {
            "$startDateIso $s – $endDateIso $e"
        } else {
            "$s–$e"
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("startDateIso", startDateIso)
        put("startHour24", startHour24)
        put("startMinute", startMinute)
        put("endDateIso", endDateIso)
        put("endHour24", endHour24)
        put("endMinute", endMinute)
        put("label", label)
    }

    companion object {
        fun computeDateDiffDays(startIso: String, endIso: String): Int? {
            if (startIso.isBlank() || endIso.isBlank()) return null
            return runCatching {
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                val s = fmt.parse(startIso.trim()) ?: return null
                val e = fmt.parse(endIso.trim()) ?: return null
                ((e.time - s.time) / 86_400_000L).toInt()
            }.getOrNull()
        }

        fun fromJson(obj: JSONObject): WorkingTimePeriod = WorkingTimePeriod(
            id = obj.optString("id", UUID.randomUUID().toString()),
            startDateIso = obj.optString("startDateIso", ""),
            startHour24 = obj.optInt("startHour24", 8).coerceIn(0, 23),
            startMinute = obj.optInt("startMinute", 0).coerceIn(0, 59),
            endDateIso = obj.optString("endDateIso", ""),
            endHour24 = obj.optInt("endHour24", 12).coerceIn(0, 23),
            endMinute = obj.optInt("endMinute", 0).coerceIn(0, 59),
            label = obj.optString("label", "")
        )
    }
}

/**
 * Individual stretch or lane width entry for multi-stretch area/volume calculation.
 */
data class RoadStretchWidthItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "Stretch 1",
    val lengthMeters: Double = 0.0,
    val widthMeters: Double = 0.0
) {
    val areaSqm: Double get() = (lengthMeters.coerceAtLeast(0.0) * widthMeters.coerceAtLeast(0.0))

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("lengthMeters", lengthMeters)
        put("widthMeters", widthMeters)
    }

    companion object {
        fun fromJson(obj: JSONObject): RoadStretchWidthItem = RoadStretchWidthItem(
            id = obj.optString("id", UUID.randomUUID().toString()),
            label = obj.optString("label", "Stretch"),
            lengthMeters = obj.optDouble("lengthMeters", 0.0).takeIf { it.isFinite() } ?: 0.0,
            widthMeters = obj.optDouble("widthMeters", 0.0).takeIf { it.isFinite() } ?: 0.0
        )
    }
}

/**
 * Compacted & Loose Thickness specification.
 * Strictly separates target compacted thickness from loose thickness — NEVER applies a universal multiplier.
 */
data class ThicknessCompactionSpec(
    val approvedLooseToCompactedFactor: Double? = null,
    val looseFactorSource: String = "",
    val actualLooseThicknessMm: Double? = null,
    val isManualLooseThickness: Boolean = false,
    val achievedCompactedThicknessMm: Double? = null,
    val verifiedToleranceMm: Double? = null
) {
    fun recommendedLooseThicknessMm(targetCompactedMm: Double?): Double? {
        val target = targetCompactedMm?.takeIf { it > 0.0 } ?: return null
        val factor = approvedLooseToCompactedFactor?.takeIf { it > 1.0 && it <= 2.5 } ?: return null
        return target * factor
    }

    fun thicknessVariationMm(targetCompactedMm: Double?): Double? {
        val target = targetCompactedMm?.takeIf { it > 0.0 } ?: return null
        val achieved = achievedCompactedThicknessMm?.takeIf { it > 0.0 } ?: return null
        return achieved - target
    }

    fun isWithinVerifiedTolerance(targetCompactedMm: Double?): Boolean? {
        val variation = thicknessVariationMm(targetCompactedMm) ?: return null
        val tol = verifiedToleranceMm?.takeIf { it > 0.0 } ?: return null
        return abs(variation) <= tol
    }

    fun toJson(): JSONObject = JSONObject().apply {
        approvedLooseToCompactedFactor?.let { put("approvedLooseToCompactedFactor", it) }
        put("looseFactorSource", looseFactorSource)
        actualLooseThicknessMm?.let { put("actualLooseThicknessMm", it) }
        put("isManualLooseThickness", isManualLooseThickness)
        achievedCompactedThicknessMm?.let { put("achievedCompactedThicknessMm", it) }
        verifiedToleranceMm?.let { put("verifiedToleranceMm", it) }
    }

    companion object {
        const val VERIFICATION_REQUIRED_LABEL = "Trial Section / Engineer Verification Required"

        fun fromJson(obj: JSONObject?): ThicknessCompactionSpec {
            if (obj == null) return ThicknessCompactionSpec()
            fun optD(k: String): Double? =
                if (obj.has(k) && !obj.isNull(k)) obj.optDouble(k).takeIf { it.isFinite() } else null
            return ThicknessCompactionSpec(
                approvedLooseToCompactedFactor = optD("approvedLooseToCompactedFactor"),
                looseFactorSource = obj.optString("looseFactorSource", ""),
                actualLooseThicknessMm = optD("actualLooseThicknessMm"),
                isManualLooseThickness = obj.optBoolean("isManualLooseThickness", false),
                achievedCompactedThicknessMm = optD("achievedCompactedThicknessMm"),
                verifiedToleranceMm = optD("verifiedToleranceMm")
            )
        }
    }
}

/**
 * Material-specific temperature tracking. Bituminous limits are NEVER applied to unrelated materials.
 */
data class MaterialTemperatureSpec(
    val materialTypeLabel: String = "",
    val mixOrBinderGrade: String = "VG-30",
    val binderTempC: Double? = null,
    val aggregateTempC: Double? = null,
    val plantProductionTempC: Double? = null,
    val dispatchTempC: Double? = null,
    val arrivalTempC: Double? = null,
    val layingTempC: Double? = null,
    val minRollingTempC: Double? = null,
    val actualRollingTempC: Double? = null,
    val measurementTimeLabel: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("materialTypeLabel", materialTypeLabel)
        put("mixOrBinderGrade", mixOrBinderGrade)
        binderTempC?.let { put("binderTempC", it) }
        aggregateTempC?.let { put("aggregateTempC", it) }
        plantProductionTempC?.let { put("plantProductionTempC", it) }
        dispatchTempC?.let { put("dispatchTempC", it) }
        arrivalTempC?.let { put("arrivalTempC", it) }
        layingTempC?.let { put("layingTempC", it) }
        minRollingTempC?.let { put("minRollingTempC", it) }
        actualRollingTempC?.let { put("actualRollingTempC", it) }
        put("measurementTimeLabel", measurementTimeLabel)
    }

    companion object {
        fun fromJson(obj: JSONObject?): MaterialTemperatureSpec {
            if (obj == null) return MaterialTemperatureSpec()
            fun optD(k: String): Double? =
                if (obj.has(k) && !obj.isNull(k)) obj.optDouble(k).takeIf { it.isFinite() } else null
            return MaterialTemperatureSpec(
                materialTypeLabel = obj.optString("materialTypeLabel", ""),
                mixOrBinderGrade = obj.optString("mixOrBinderGrade", "VG-30"),
                binderTempC = optD("binderTempC"),
                aggregateTempC = optD("aggregateTempC"),
                plantProductionTempC = optD("plantProductionTempC"),
                dispatchTempC = optD("dispatchTempC"),
                arrivalTempC = optD("arrivalTempC"),
                layingTempC = optD("layingTempC"),
                minRollingTempC = optD("minRollingTempC"),
                actualRollingTempC = optD("actualRollingTempC"),
                measurementTimeLabel = obj.optString("measurementTimeLabel", "")
            )
        }
    }
}

/**
 * Chainage parsing & kilometre-transition length calculation utility.
 * Supports formats like 188+840, 189+050, 202+560, 0+000, Ch 12+350, or decimal km 188.840.
 */
data class ChainageLengthCalculation(
    val startRaw: String,
    val endRaw: String,
    val startMeters: Double?,
    val endMeters: Double?,
    val calculatedLengthMeters: Double?,
    val isReversed: Boolean,
    val warningMessage: String?
)

object ChainageMath {
    private val PLUS_REGEX = Regex("""^\s*(?:ch\.?\s*|km\.?\s*)?(\d+)\s*\+\s*(\d+(?:\.\d+)?)\s*$""", RegexOption.IGNORE_CASE)
    private val NUMBER_REGEX = Regex("""^\s*(?:ch\.?\s*|km\.?\s*)?(\d+(?:\.\d+)?)\s*(m|km)?\s*$""", RegexOption.IGNORE_CASE)

    fun parseToMeters(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val plusMatch = PLUS_REGEX.matchEntire(trimmed)
        if (plusMatch != null) {
            val km = plusMatch.groupValues[1].toDoubleOrNull() ?: return null
            val m = plusMatch.groupValues[2].toDoubleOrNull() ?: return null
            if (m < 0.0 || m >= 1000.0) return null
            return km * 1000.0 + m
        }
        val numMatch = NUMBER_REGEX.matchEntire(trimmed)
        if (numMatch != null) {
            val value = numMatch.groupValues[1].toDoubleOrNull() ?: return null
            val unit = numMatch.groupValues[2].lowercase(Locale.US)
            return when (unit) {
                "km" -> value * 1000.0
                "m" -> value
                else -> if (trimmed.contains('.')) value * 1000.0 else value
            }
        }
        return null
    }

    fun calculateLength(startRaw: String, endRaw: String): ChainageLengthCalculation {
        val sTrim = startRaw.trim()
        val eTrim = endRaw.trim()
        if (sTrim.isEmpty() && eTrim.isEmpty()) {
            return ChainageLengthCalculation(startRaw, endRaw, null, null, null, false, null)
        }
        val startM = parseToMeters(sTrim)
        val endM = parseToMeters(eTrim)
        if (sTrim.isNotEmpty() && startM == null) {
            return ChainageLengthCalculation(
                startRaw, endRaw, null, endM, null, false,
                "Invalid Start Chainage format (use e.g. 188+840)."
            )
        }
        if (eTrim.isNotEmpty() && endM == null) {
            return ChainageLengthCalculation(
                startRaw, endRaw, startM, null, null, false,
                "Invalid End Chainage format (use e.g. 189+240)."
            )
        }
        if (startM == null || endM == null) {
            return ChainageLengthCalculation(startRaw, endRaw, startM, endM, null, false, null)
        }
        val reversed = endM < startM
        val length = abs(endM - startM)
        val warn = when {
            length == 0.0 -> "Start and End chainages are identical (0 m)."
            reversed -> "End chainage is lower than Start chainage; using absolute distance (${length.toInt()} m)."
            else -> null
        }
        return ChainageLengthCalculation(
            startRaw = startRaw,
            endRaw = endRaw,
            startMeters = startM,
            endMeters = endM,
            calculatedLengthMeters = length,
            isReversed = reversed,
            warningMessage = warn
        )
    }
}

/**
 * Complete shift & time-to-time calculation result supporting Day, Night (midnight crossing), and Custom shifts.
 */
data class ShiftTimingResult(
    val totalShiftMinutes: Int,
    val breakMinutes: Int,
    val effectiveWorkingMinutes: Int,
    val mobilisationMinutes: Int,
    val loadingWaitingMinutes: Int,
    val actualNetWorkingMinutes: Int,
    val crossesMidnight: Boolean,
    val activeHours24List: List<Int>,
    val estimatedCompletionTimeLabel: String
) {
    val totalShiftHours: Double get() = totalShiftMinutes / 60.0
    val effectiveWorkingHours: Double get() = effectiveWorkingMinutes / 60.0
    val actualNetWorkingHours: Double get() = actualNetWorkingMinutes / 60.0
}

object ShiftTimingCalculator {
    fun calculate(
        shiftType: WorkShiftType,
        startDateIso: String,
        startHour24: Int,
        startMinute: Int,
        endDateIso: String,
        endHour24: Int,
        endMinute: Int,
        breakDurationMinutes: Int,
        mobilisationMinutes: Int,
        loadingWaitingMinutes: Int,
        workingPeriods: List<WorkingTimePeriod>
    ): ShiftTimingResult {
        val periods = if (workingPeriods.isNotEmpty()) {
            workingPeriods
        } else {
            listOf(
                WorkingTimePeriod(
                    startDateIso = startDateIso,
                    startHour24 = startHour24,
                    startMinute = startMinute,
                    endDateIso = endDateIso,
                    endHour24 = endHour24,
                    endMinute = endMinute
                )
            )
        }

        val totalMins = periods.sumOf { it.durationMinutes() }
        val safeBreak = breakDurationMinutes.coerceIn(0, totalMins)
        val effectiveMins = (totalMins - safeBreak).coerceAtLeast(0)
        val safeMob = mobilisationMinutes.coerceAtLeast(0)
        val safeWait = loadingWaitingMinutes.coerceAtLeast(0)
        val netWorkMins = (effectiveMins - safeMob - safeWait).coerceAtLeast(0)

        val crossesMidnight = shiftType == WorkShiftType.NIGHT_SHIFT || periods.any { p ->
            val s = p.startHour24 * 60 + p.startMinute
            val e = p.endHour24 * 60 + p.endMinute
            val dayDelta = WorkingTimePeriod.computeDateDiffDays(p.startDateIso, p.endDateIso)
            (dayDelta != null && dayDelta > 0) || e < s
        }

        val hourSet = linkedSetOf<Int>()
        periods.forEach { p ->
            val durH = ((p.durationMinutes() + 59) / 60).coerceIn(1, 24)
            for (offset in 0 until durH) {
                hourSet.add((p.startHour24 + offset) % 24)
            }
        }

        val lastPeriod = periods.last()
        val endTimeLabel = buildString {
            if (lastPeriod.endDateIso.isNotBlank() && lastPeriod.endDateIso != periods.first().startDateIso) {
                append(lastPeriod.endDateIso).append(" ")
            }
            append(String.format(Locale.US, "%02d:%02d", lastPeriod.endHour24.coerceIn(0, 23), lastPeriod.endMinute.coerceIn(0, 59)))
        }

        return ShiftTimingResult(
            totalShiftMinutes = totalMins,
            breakMinutes = safeBreak,
            effectiveWorkingMinutes = effectiveMins,
            mobilisationMinutes = safeMob,
            loadingWaitingMinutes = safeWait,
            actualNetWorkingMinutes = netWorkMins,
            crossesMidnight = crossesMidnight,
            activeHours24List = hourSet.toList(),
            estimatedCompletionTimeLabel = endTimeLabel
        )
    }

    fun nextIsoDate(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = fmt.parse(isoDate) ?: return ""
            val cal = Calendar.getInstance().apply {
                time = date
                add(Calendar.DAY_OF_YEAR, 1)
            }
            fmt.format(cal.time)
        }.getOrDefault("")
    }
}

/**
 * Traceable MoRTH / IRC / IS engineering reference record.
 * Never invents clauses; marks unknown entries as Specification Verification Required.
 */
data class MoRTHReferenceRecord(
    val standardName: String,
    val editionOrRevision: String,
    val clauseOrTableReference: String,
    val activityLabel: String,
    val materialLabel: String,
    val requirementSummary: String,
    val sourceHierarchyRank: Int,
    val verificationStatus: String
)

data class MaterialTemperatureGuidance(
    val categoryTitle: String,
    val isBituminous: Boolean,
    val guidanceLines: List<String>,
    val referenceNote: String
)

data class RollingStagePlan(
    val stageTitle: String,
    val rollerType: String,
    val operatingMode: String,
    val rollingSequence: String,
    val recommendedPassesText: String,
    val isProvisional: Boolean
)

data class ActivityCompactionGuidance(
    val isCompactionApplicable: Boolean,
    val isPtrRecommended: Boolean,
    val ptrSuitabilityNote: String,
    val stages: List<RollingStagePlan>,
    val densityVerificationReminder: String
)

object WorkPlanningStandardEngine {
    const val SPEC_VERIFICATION_REQUIRED = "Specification Verification Required"

    fun referencesForActivity(
        activity: PlanningWorkActivity,
        revision: MoRTHRevision,
        jmfRef: String = "",
        trialRef: String = ""
    ): List<MoRTHReferenceRecord> {
        val revLabel = revision.label
        val list = mutableListOf<MoRTHReferenceRecord>()
        when (activity) {
            PlanningWorkActivity.BC_LAYING,
            PlanningWorkActivity.DBM_LAYING,
            PlanningWorkActivity.BM_LAYING,
            PlanningWorkActivity.SDBC_LAYING,
            PlanningWorkActivity.SMA_LAYING -> {
                val clause = when (activity) {
                    PlanningWorkActivity.BC_LAYING -> if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Section 507 & Cl. 501.5.1" else "Contract Applicable Section 500 (Verify 6th Rev Table)"
                    PlanningWorkActivity.DBM_LAYING -> if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Section 505 & Cl. 501.5.1" else "Contract Applicable Section 500 (Verify 6th Rev Table)"
                    PlanningWorkActivity.SMA_LAYING -> if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Section 515 & IRC:SP:79" else "Contract Applicable SMA Clause (Verify 6th Rev)"
                    else -> if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Section 500 / Cl. 501" else "Contract Applicable Bituminous Section (Verify 6th Rev)"
                }
                list += MoRTHReferenceRecord(
                    standardName = "MoRTH Specifications for Road and Bridge Works",
                    editionOrRevision = revLabel,
                    clauseOrTableReference = clause,
                    activityLabel = activity.displayName,
                    materialLabel = "Hot-Mix Bituminous Layer",
                    requirementSummary = "Laying prohibited during rain, standing water, dust storms, fog, or wind >40 km/h; verify pavement surface temp & JMF temperatures.",
                    sourceHierarchyRank = 1,
                    verificationStatus = if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Verified (5th Rev)" else "Contract Circular & Effective Date Verification Required"
                )
                list += MoRTHReferenceRecord(
                    standardName = "IRC:105 / IRC:111 / IRC:SP:53",
                    editionOrRevision = "Applicable Current IRC Edition",
                    clauseOrTableReference = "IRC:111 (Dense Graded) / IRC:105 Cl. 3.3.4",
                    activityLabel = activity.displayName,
                    materialLabel = "Bituminous Mix & Modified Binder",
                    requirementSummary = "Compaction rolling must finish before mat cools below approved JMF minimum rolling temperature; field density cores govern acceptance.",
                    sourceHierarchyRank = 2,
                    verificationStatus = "Verified Reference"
                )
            }
            PlanningWorkActivity.TACK_COAT, PlanningWorkActivity.PRIME_COAT, PlanningWorkActivity.SURFACE_DRESSING -> {
                list += MoRTHReferenceRecord(
                    standardName = "MoRTH Specifications for Road and Bridge Works",
                    editionOrRevision = revLabel,
                    clauseOrTableReference = if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Sections 502 / 503 / 510" else "Contract Applicable Surface Treatment Section",
                    activityLabel = activity.displayName,
                    materialLabel = "Bitumen Emulsion / Cutback / Chips",
                    requirementSummary = "Apply on dry, clean broomed surface; verify emulsion breaking before overlay or traffic; avoid rain and high winds.",
                    sourceHierarchyRank = 1,
                    verificationStatus = if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Verified (5th Rev)" else SPEC_VERIFICATION_REQUIRED
                )
            }
            PlanningWorkActivity.GSB, PlanningWorkActivity.WMM -> {
                list += MoRTHReferenceRecord(
                    standardName = "MoRTH Specifications for Road and Bridge Works",
                    editionOrRevision = revLabel,
                    clauseOrTableReference = if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Sections 401 (GSB) / 406 (WMM)" else "Contract Applicable Section 400",
                    activityLabel = activity.displayName,
                    materialLabel = "Granular Sub-Base / Wet Mix Macadam",
                    requirementSummary = "Compact at OMC to approved MDD; verify loose-to-compacted thickness from trial stretch and field density test.",
                    sourceHierarchyRank = 1,
                    verificationStatus = if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Verified (5th Rev)" else SPEC_VERIFICATION_REQUIRED
                )
            }
            PlanningWorkActivity.EARTHWORK, PlanningWorkActivity.SUBGRADE -> {
                list += MoRTHReferenceRecord(
                    standardName = "MoRTH Specifications for Road and Bridge Works",
                    editionOrRevision = revLabel,
                    clauseOrTableReference = if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Section 305 (Embankment & Subgrade)" else "Contract Applicable Section 300",
                    activityLabel = activity.displayName,
                    materialLabel = "Soil / Embankment / Subgrade",
                    requirementSummary = "Verify soil moisture content vs OMC before rolling; suspend compaction on saturated or waterlogged layers.",
                    sourceHierarchyRank = 1,
                    verificationStatus = if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Verified (5th Rev)" else SPEC_VERIFICATION_REQUIRED
                )
            }
            PlanningWorkActivity.PQC_DLC, PlanningWorkActivity.DLC, PlanningWorkActivity.PQC, PlanningWorkActivity.CONCRETE_WORKS -> {
                list += MoRTHReferenceRecord(
                    standardName = "MoRTH & IRC:15 / IRC:SP:49 / IS:456",
                    editionOrRevision = revLabel,
                    clauseOrTableReference = if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Sections 601 (DLC) / 602 (PQC)" else "Contract Applicable Section 600",
                    activityLabel = activity.displayName,
                    materialLabel = "Cement Concrete (DLC / PQC / Structures)",
                    requirementSummary = "Control concrete placement temperature, evaporation risk, rain protection, and immediate curing membrane/wet burlap.",
                    sourceHierarchyRank = 1,
                    verificationStatus = if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Verified (5th Rev)" else SPEC_VERIFICATION_REQUIRED
                )
            }
            PlanningWorkActivity.ROAD_MARKING, PlanningWorkActivity.ROAD_STUDS -> {
                list += MoRTHReferenceRecord(
                    standardName = "MoRTH & IRC:35",
                    editionOrRevision = revLabel,
                    clauseOrTableReference = if (revision == MoRTHRevision.FIFTH_REVISION_2013) "Section 803 (Road Markings) / IRC:35" else "Contract Applicable Section 800 / IRC:35",
                    activityLabel = activity.displayName,
                    materialLabel = "Thermoplastic / Cold Paint / Road Studs",
                    requirementSummary = "Surface must be completely dry and dust-free; follow approved thermoplastic heating temperature and retro-reflectivity checks.",
                    sourceHierarchyRank = 1,
                    verificationStatus = "Verified Reference"
                )
            }
            PlanningWorkActivity.GLASS_GRID -> {
                list += MoRTHReferenceRecord(
                    standardName = "MoRTH Section 700 / Manufacturer Method Statement",
                    editionOrRevision = revLabel,
                    clauseOrTableReference = "Section 700 (Geosynthetics) & Approved Method Statement",
                    activityLabel = activity.displayName,
                    materialLabel = "Fibreglass Geogrid Reinforcement",
                    requirementSummary = "Dry, clean levelled substrate; tack coat curing and wind-lift control per approved method statement.",
                    sourceHierarchyRank = 1,
                    verificationStatus = "Method Statement Verification Required"
                )
            }
            else -> {
                list += MoRTHReferenceRecord(
                    standardName = "Contract Specifications / Applicable MoRTH & IRC",
                    editionOrRevision = revLabel,
                    clauseOrTableReference = SPEC_VERIFICATION_REQUIRED,
                    activityLabel = activity.displayName,
                    materialLabel = activity.displayName,
                    requirementSummary = "Verify applicable contract clause, tolerances, and method statement with the Engineer prior to execution.",
                    sourceHierarchyRank = 4,
                    verificationStatus = SPEC_VERIFICATION_REQUIRED
                )
            }
        }
        if (jmfRef.isNotBlank()) {
            list += MoRTHReferenceRecord(
                standardName = "Approved Job Mix Formula (JMF)",
                editionOrRevision = "Project Approved",
                clauseOrTableReference = jmfRef.trim(),
                activityLabel = activity.displayName,
                materialLabel = "Project JMF",
                requirementSummary = "Project JMF governs exact target density, binder content, and mix/rolling temperatures.",
                sourceHierarchyRank = 5,
                verificationStatus = "Project Reference Linked"
            )
        }
        if (trialRef.isNotBlank()) {
            list += MoRTHReferenceRecord(
                standardName = "Approved Trial Section Results",
                editionOrRevision = "Project Approved",
                clauseOrTableReference = trialRef.trim(),
                activityLabel = activity.displayName,
                materialLabel = "Field Compaction Trial",
                requirementSummary = "Trial stretch establishes loose-to-compacted factor and rolling pass pattern.",
                sourceHierarchyRank = 6,
                verificationStatus = "Trial Data Linked"
            )
        }
        return list
    }

    fun temperatureGuidanceForActivity(activity: PlanningWorkActivity, binderGrade: String): MaterialTemperatureGuidance {
        return when {
            activity.isBituminousProduction -> {
                val isModified = binderGrade.contains("PMB", ignoreCase = true) || binderGrade.contains("CRMB", ignoreCase = true)
                MaterialTemperatureGuidance(
                    categoryTitle = "Bituminous Hot-Mix Temperature Control ($binderGrade)",
                    isBituminous = true,
                    guidanceLines = listOf(
                        "Track separately: Binder, Aggregate, Plant Mix, Dispatch, Site Arrival, Laying, and Rolling temperatures.",
                        if (isModified) {
                            "Modified binder (PMB/CRMB): Verify elevated mixing, laying, and minimum rolling temperatures strictly per approved JMF & IRC:SP:53."
                        } else {
                            "Conventional binder ($binderGrade): Verify plant mixing, laying, and minimum finish-rolling temperatures strictly per approved JMF & MoRTH Table 500."
                        },
                        "Never extrapolate mat temperature from air temperature; record calibrated IR / probe readings on site."
                    ),
                    referenceNote = "MoRTH Section 500 & Approved JMF"
                )
            }
            activity == PlanningWorkActivity.PQC_DLC || activity == PlanningWorkActivity.DLC ||
                activity == PlanningWorkActivity.PQC || activity == PlanningWorkActivity.CONCRETE_WORKS -> MaterialTemperatureGuidance(
                categoryTitle = "Concrete Placement & Curing Control",
                isBituminous = false,
                guidanceLines = listOf(
                    "Monitor concrete mix placement temperature, ambient air temperature, and wind-driven plastic shrinkage risk.",
                    "Apply curing compound / wet hessian immediately after texturing; protect fresh concrete from rainfall.",
                    "Bituminous laying/rolling limits do not apply to cement concrete."
                ),
                referenceNote = "MoRTH Section 600 / IRC:15 / IS:456"
            )
            activity == PlanningWorkActivity.ROAD_MARKING -> MaterialTemperatureGuidance(
                categoryTitle = "Thermoplastic / Paint Heating & Application",
                isBituminous = false,
                guidanceLines = listOf(
                    "Verify thermoplastic pre-heater and application temperature per manufacturer datasheet & MoRTH Cl. 803.4.",
                    "Check pavement surface dryness and dew point; avoid overheating thermoplastic binder."
                ),
                referenceNote = "MoRTH Cl. 803.4 / IRC:35"
            )
            activity == PlanningWorkActivity.TACK_COAT || activity == PlanningWorkActivity.PRIME_COAT ||
                activity == PlanningWorkActivity.SURFACE_DRESSING -> MaterialTemperatureGuidance(
                categoryTitle = "Emulsion / Binder Application & Breaking",
                isBituminous = false,
                guidanceLines = listOf(
                    "Check emulsion spray temperature, ambient air/surface temperature, and complete emulsion breaking before next layer.",
                    "Do not spray over wet/ponded surfaces or when rain is imminent."
                ),
                referenceNote = "MoRTH Sections 502 / 503 / 510"
            )
            else -> MaterialTemperatureGuidance(
                categoryTitle = "Material & Compaction Timing Control",
                isBituminous = false,
                guidanceLines = listOf(
                    "Verify moisture content (OMC), curing/setting time, and substrate readiness per approved method statement.",
                    "Bituminous hot-mix temperature limits do not apply to ${activity.displayName}."
                ),
                referenceNote = "Project Specification / Approved Method Statement"
            )
        }
    }

    fun compactionGuidanceForActivity(
        activity: PlanningWorkActivity,
        trialPassesEntered: Int?,
        tandemModel: String?,
        ptrModel: String?
    ): ActivityCompactionGuidance {
        val provisional = trialPassesEntered == null
        val tandemName = tandemModel?.takeIf { it.isNotBlank() } ?: "Tandem Vibratory Roller (Verify Model)"
        val ptrName = ptrModel?.takeIf { it.isNotBlank() } ?: "Pneumatic Tyred Roller (PTR)"

        return when {
            activity.isBituminousProduction -> {
                val ptrSuitable = activity != PlanningWorkActivity.SMA_LAYING
                val ptrNote = if (ptrSuitable) {
                    "PTR recommended for intermediate kneading compaction on ${activity.displayName}; verify ballast & tyre pressure per approved trial."
                } else {
                    "PTR is typically NOT recommended for SMA (stone-on-stone skeleton & binder flushing risk); verify contract specification."
                }
                val breakdownPasses = trialPassesEntered?.let { "${(it * 0.35).toInt().coerceAtLeast(2)} passes (from JMF total $it)" }
                    ?: "Provisional — Verify via Trial Section"
                val interPasses = trialPassesEntered?.let { "${(it * 0.45).toInt().coerceAtLeast(2)} passes (from JMF total $it)" }
                    ?: "Provisional — Verify via Trial Section"
                val finishPasses = trialPassesEntered?.let { "${(it * 0.20).toInt().coerceAtLeast(1)} static passes (from JMF total $it)" }
                    ?: "Provisional — Static passes to remove roller marks"

                ActivityCompactionGuidance(
                    isCompactionApplicable = true,
                    isPtrRecommended = ptrSuitable,
                    ptrSuitabilityNote = ptrNote,
                    stages = listOf(
                        RollingStagePlan(
                            stageTitle = "1. Breakdown Rolling",
                            rollerType = tandemName,
                            operatingMode = "Initial static seal pass + vibratory mode (per JMF)",
                            rollingSequence = "Longitudinal joints first, then lower edge → upper edge / crown",
                            recommendedPassesText = breakdownPasses,
                            isProvisional = provisional
                        ),
                        RollingStagePlan(
                            stageTitle = "2. Intermediate Rolling",
                            rollerType = if (ptrSuitable) "$ptrName / $tandemName" else tandemName,
                            operatingMode = if (ptrSuitable) "Kneading tyre action / vibratory density build-up" else "Static/low-amplitude vibratory per SMA JMF",
                            rollingSequence = "Continuous longitudinal tracks with 150–200 mm overlap",
                            recommendedPassesText = interPasses,
                            isProvisional = provisional
                        ),
                        RollingStagePlan(
                            stageTitle = "3. Finish Rolling",
                            rollerType = tandemName,
                            operatingMode = "Static mode ONLY (vibration OFF)",
                            rollingSequence = "Remove roller marks while mat remains above min finish-rolling temp",
                            recommendedPassesText = finishPasses,
                            isProvisional = provisional
                        )
                    ),
                    densityVerificationReminder = "Pass count alone is NEVER proof of compaction compliance. Core/nuclear field density tests vs approved JMF govern acceptance."
                )
            }
            activity == PlanningWorkActivity.GSB || activity == PlanningWorkActivity.WMM ||
                activity == PlanningWorkActivity.EARTHWORK || activity == PlanningWorkActivity.SUBGRADE -> {
                val passText = trialPassesEntered?.let { "$it passes (per approved trial section)" }
                    ?: "Provisional — Establish via Field Compaction Trial"
                ActivityCompactionGuidance(
                    isCompactionApplicable = true,
                    isPtrRecommended = false,
                    ptrSuitabilityNote = "PTR is not prescribed as primary roller for ${activity.displayName}; use single-drum vibratory soil compactor.",
                    stages = listOf(
                        RollingStagePlan(
                            stageTitle = "1. Initial Seating Pass",
                            rollerType = "Vibratory Soil Compactor (Single Drum)",
                            operatingMode = "Static seating pass at OMC",
                            rollingSequence = "Lower edge → upper edge / crown with ≥200 mm overlap",
                            recommendedPassesText = "1–2 static passes (Provisional)",
                            isProvisional = provisional
                        ),
                        RollingStagePlan(
                            stageTitle = "2. Main Vibratory Compaction",
                            rollerType = "Vibratory Soil Compactor (8–12t)",
                            operatingMode = "High-amplitude vibratory until MDD achieved",
                            rollingSequence = "Overlapping longitudinal passes across full width",
                            recommendedPassesText = passText,
                            isProvisional = provisional
                        ),
                        RollingStagePlan(
                            stageTitle = "3. Finish / Proof Rolling",
                            rollerType = "Single Drum Roller",
                            operatingMode = "Static finish pass",
                            rollingSequence = "Uniform surface finish; check camber & level",
                            recommendedPassesText = "1–2 static passes",
                            isProvisional = provisional
                        )
                    ),
                    densityVerificationReminder = "Verify actual moisture (OMC) and field dry density (FDD vs MDD) per MoRTH Section 300/400 — pass count alone does not confirm compliance."
                )
            }
            activity == PlanningWorkActivity.DLC -> ActivityCompactionGuidance(
                isCompactionApplicable = true,
                isPtrRecommended = false,
                ptrSuitabilityNote = "PTR is not prescribed for DLC; use smooth-wheeled dual-drum vibratory roller (8–10t) per MoRTH Cl. 601.",
                stages = listOf(
                    RollingStagePlan(
                        stageTitle = "DLC Vibratory Compaction",
                        rollerType = tandemName,
                        operatingMode = "Static seating + vibratory compaction + static finish within setting window",
                        rollingSequence = "Edge-to-centre / lower-to-upper before initial cement setting time",
                        recommendedPassesText = trialPassesEntered?.let { "$it passes (trial verified)" } ?: "Provisional — Verify via DLC Trial Section",
                        isProvisional = provisional
                    )
                ),
                densityVerificationReminder = "Verify DLC density (≥98% MDD) and cube strength per MoRTH Section 601 — pass count alone is insufficient."
            )
            else -> ActivityCompactionGuidance(
                isCompactionApplicable = false,
                isPtrRecommended = false,
                ptrSuitabilityNote = "Roller / PTR rolling is not prescribed for ${activity.displayName}.",
                stages = emptyList(),
                densityVerificationReminder = "Follow activity-specific QA/QC checks per approved contract specification."
            )
        }
    }
}

/**
 * Contextual Settings exclusively for the separate WORK PLANNING page popup.
 * Kept strictly independent from Weather Page settings and Main Settings Page.
 */
data class WorkPlanningSettings(
    // Planning Preferences
    val defaultActivity: PlanningWorkActivity = PlanningWorkActivity.BC_LAYING,
    val defaultShift: WorkShiftType = WorkShiftType.DAY_SHIFT,
    val useMetricUnits: Boolean = true,
    val chainagePlusFormat: Boolean = true,
    // Machinery Preferences
    val machineryFilter: MachineryFilterPreference = MachineryFilterPreference.ALL,
    val preferredManufacturerFilter: String = "",
    val showOnlyVerifiedSpecs: Boolean = false,
    val offlineCatalogPreferred: Boolean = true,
    // Engineering Preferences
    val morthRevision: MoRTHRevision = MoRTHRevision.FIFTH_REVISION_2013,
    val showCalculationDetails: Boolean = true,
    val showCompactionRecommendations: Boolean = true,
    val showRollingDiagram: Boolean = true,
    val showSourceReferences: Boolean = true,
    // Data & Export
    val autoSaveEnabled: Boolean = true,
    val backupFrequency: BackupFrequency = BackupFrequency.MANUAL_ONLY,
    val pdfDefaultScope: PdfExportScope = PdfExportScope.COMPLETE_WORK_PLAN,
    val pdfIncludeRollingDiagram: Boolean = true,
    val pdfIncludeApprovalBlock: Boolean = true
) {
    fun toJsonString(): String = JSONObject().apply {
        put("defaultActivity", defaultActivity.name)
        put("defaultShift", defaultShift.name)
        put("useMetricUnits", useMetricUnits)
        put("chainagePlusFormat", chainagePlusFormat)
        put("machineryFilter", machineryFilter.name)
        put("preferredManufacturerFilter", preferredManufacturerFilter)
        put("showOnlyVerifiedSpecs", showOnlyVerifiedSpecs)
        put("offlineCatalogPreferred", offlineCatalogPreferred)
        put("morthRevision", morthRevision.name)
        put("showCalculationDetails", showCalculationDetails)
        put("showCompactionRecommendations", showCompactionRecommendations)
        put("showRollingDiagram", showRollingDiagram)
        put("showSourceReferences", showSourceReferences)
        put("autoSaveEnabled", autoSaveEnabled)
        put("backupFrequency", backupFrequency.name)
        put("pdfDefaultScope", pdfDefaultScope.name)
        put("pdfIncludeRollingDiagram", pdfIncludeRollingDiagram)
        put("pdfIncludeApprovalBlock", pdfIncludeApprovalBlock)
    }.toString()

    companion object {
        fun fromJsonString(raw: String?): WorkPlanningSettings {
            if (raw.isNullOrBlank()) return WorkPlanningSettings()
            return runCatching {
                val obj = JSONObject(raw)
                WorkPlanningSettings(
                    defaultActivity = PlanningWorkActivity.fromName(obj.optString("defaultActivity")),
                    defaultShift = WorkShiftType.fromName(obj.optString("defaultShift")),
                    useMetricUnits = obj.optBoolean("useMetricUnits", true),
                    chainagePlusFormat = obj.optBoolean("chainagePlusFormat", true),
                    machineryFilter = MachineryFilterPreference.fromName(obj.optString("machineryFilter")),
                    preferredManufacturerFilter = obj.optString("preferredManufacturerFilter", ""),
                    showOnlyVerifiedSpecs = obj.optBoolean("showOnlyVerifiedSpecs", false),
                    offlineCatalogPreferred = obj.optBoolean("offlineCatalogPreferred", true),
                    morthRevision = MoRTHRevision.fromName(obj.optString("morthRevision")),
                    showCalculationDetails = obj.optBoolean("showCalculationDetails", true),
                    showCompactionRecommendations = obj.optBoolean("showCompactionRecommendations", true),
                    showRollingDiagram = obj.optBoolean("showRollingDiagram", true),
                    showSourceReferences = obj.optBoolean("showSourceReferences", true),
                    autoSaveEnabled = obj.optBoolean("autoSaveEnabled", true),
                    backupFrequency = BackupFrequency.fromName(obj.optString("backupFrequency")),
                    pdfDefaultScope = PdfExportScope.fromName(obj.optString("pdfDefaultScope")),
                    pdfIncludeRollingDiagram = obj.optBoolean("pdfIncludeRollingDiagram", true),
                    pdfIncludeApprovalBlock = obj.optBoolean("pdfIncludeApprovalBlock", true)
                )
            }.getOrDefault(WorkPlanningSettings())
        }
    }
}
