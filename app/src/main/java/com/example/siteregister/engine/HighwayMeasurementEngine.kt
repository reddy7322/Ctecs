package com.example.siteregister.engine

import com.example.siteregister.model.BcLayingEntry
import com.example.siteregister.model.GlassGridEntry
import com.example.siteregister.model.MeasurementMethod
import com.example.siteregister.model.MeasurementUnit
import com.example.siteregister.model.QuantityClassification
import com.example.siteregister.model.SiteSegmentRecord
import com.example.siteregister.model.TackCoatTrayRow
import com.example.siteregister.model.TestResultStatus
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Universal, deterministic Highway Measurement & Quantity Calculation Engine.
 *
 * Strictly enforces:
 * - Explicit unit handling (never silently mixes mm/cm/m/km).
 * - Separation of Design, Actual, and Verified measurements (never assumes loose == compacted).
 * - Never invents universal densities or universal specification limits.
 * - Never calculates volume where not technically applicable.
 * - Detects & prevents double-counting across overlapping chainage segments / vehicle entries.
 * - Always exposes formula, inputs, result, unit, and classification (Estimated / Measured / Verified).
 */
object HighwayMeasurementEngine {

    // =========================================================================
    // 1. CHAINAGE & UNIT NORMALIZATION
    // =========================================================================

    /**
     * Parses highway chainage strings such as "201+000", "180+351.5", "CH 12+450", or "201000"
     * into exact meters. Returns null if unparseable or negative.
     */
    fun parseChainageMeters(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replace("CH", "", ignoreCase = true)
            .replace("KM", "", ignoreCase = true)
            .replace(" ", "")
        if (cleaned.contains("+")) {
            val parts = cleaned.split("+")
            if (parts.size != 2) return null
            val km = parts[0].toDoubleOrNull() ?: return null
            val m = parts[1].toDoubleOrNull() ?: return null
            if (km < 0.0 || m < 0.0) return null
            return km * 1000.0 + m
        }
        val direct = cleaned.toDoubleOrNull() ?: return null
        return if (direct >= 0.0) direct else null
    }

    fun formatChainage(meters: Double): String {
        val safe = max(0.0, meters)
        val km = (safe / 1000.0).toInt()
        val rem = safe - (km * 1000.0)
        return if (abs(rem - rem.toInt()) < 0.001) {
            String.format(Locale.US, "%d+%03d", km, rem.toInt())
        } else {
            String.format(Locale.US, "%d+%06.2f", km, rem)
        }
    }

    /**
     * Converts an explicit length/depth measurement in [unit] strictly into meters.
     * Throws [IllegalArgumentException] if a non-length unit is supplied (never mixes dimensions).
     */
    fun toMeters(value: Double, unit: MeasurementUnit): Double = when (unit) {
        MeasurementUnit.MM -> value / 1000.0
        MeasurementUnit.CM -> value / 100.0
        MeasurementUnit.M -> value
        MeasurementUnit.KM -> value * 1000.0
        else -> throw IllegalArgumentException("Unit ${unit.symbol} is not a linear length unit.")
    }

    fun chainageDifferenceMeters(fromChainage: String?, toChainage: String?): Double? {
        val fromM = parseChainageMeters(fromChainage) ?: return null
        val toM = parseChainageMeters(toChainage) ?: return null
        return abs(toM - fromM)
    }

    // =========================================================================
    // 2. AVERAGING & UNIFORM / CROSS-SECTION CALCULATIONS
    // =========================================================================

    fun computeAverage(vararg values: Double?): Double? {
        val valid = values.filterNotNull().filter { it.isFinite() && it >= 0.0 }
        if (valid.isEmpty()) return null
        return valid.sum() / valid.size.toDouble()
    }

    data class FormulaBreakdown(
        val formula: String,
        val inputsSummary: String,
        val result: Double?,
        val unit: MeasurementUnit,
        val classification: QuantityClassification
    )

    data class VarianceResult(
        val designValue: Double,
        val actualValue: Double,
        val variance: Double,
        val variancePercent: Double?,
        val complianceStatus: TestResultStatus? // Null unless verified project tolerance supplied
    )

    /**
     * Computes Design vs Actual variance without auto-declaring Pass/Fail unless
     * explicit verified project tolerance bounds are provided.
     */
    fun computeVariance(
        design: Double,
        actual: Double,
        toleranceMinus: Double? = null,
        tolerancePlus: Double? = null
    ): VarianceResult {
        val diff = actual - design
        val pct = if (abs(design) > 1e-9) (diff / design) * 100.0 else null
        val status = if (toleranceMinus != null && tolerancePlus != null) {
            val minAllowed = design - abs(toleranceMinus)
            val maxAllowed = design + abs(tolerancePlus)
            if (actual in minAllowed..maxAllowed) TestResultStatus.PASS else TestResultStatus.FAIL
        } else {
            null
        }
        return VarianceResult(
            designValue = design,
            actualValue = actual,
            variance = diff,
            variancePercent = pct,
            complianceStatus = status
        )
    }

    data class SegmentCalculationResult(
        val segmentId: String,
        val fromChainageM: Double?,
        val toChainageM: Double?,
        val lengthM: Double,
        val averageWidthM: Double?,
        val effectiveDepthM: Double?,
        val areaSqm: Double,
        val volumeCum: Double?,
        val materialQuantity: Double?,
        val materialUnit: MeasurementUnit?,
        val classification: QuantityClassification,
        val varianceThickness: VarianceResult?,
        val formulaBreakdowns: List<FormulaBreakdown>,
        val hasOverlapWarning: Boolean = false,
        val overlapMessage: String? = null
    )

    data class MultiSegmentSummary(
        val segmentResults: List<SegmentCalculationResult>,
        val rawTotalLengthM: Double,
        val deduplicatedLengthM: Double,
        val hasOverlaps: Boolean,
        val overlapWarnings: List<String>,
        val totalAreaSqm: Double,
        val totalVolumeCum: Double,
        val totalMaterialQty: Double?
    )

    /**
     * Calculates a single segment independently using explicit units and method.
     * Uses ONLY compacted/actual depth for volume (never substitutes loose thickness for compacted).
     * Never calculates volume when [volumeApplicable] is false.
     * Never calculates density-based material quantity unless a verified density is explicitly given.
     */
    fun calculateSegment(
        segment: SiteSegmentRecord,
        volumeApplicable: Boolean = true
    ): SegmentCalculationResult {
        val fromM = parseChainageMeters(segment.fromChainage)
        val toM = parseChainageMeters(segment.toChainage)
        val lengthM = if (fromM != null && toM != null) abs(toM - fromM) else 0.0
        val units = max(1, segment.unitCount)

        val avgWidth = segment.actualWidthM
            ?: computeAverage(segment.startWidthM, segment.middleWidthM, segment.endWidthM)

        val effectiveDepthRaw = segment.verifiedThickness ?: segment.actualCompactedThickness
        val effectiveDepthM = effectiveDepthRaw?.let { toMeters(it, segment.thicknessUnit) }
        val classification = when {
            segment.verifiedThickness != null -> QuantityClassification.VERIFIED
            effectiveDepthRaw != null || segment.surveyedAreaSqm != null || avgWidth != null -> QuantityClassification.MEASURED
            else -> QuantityClassification.ESTIMATED
        }

        val breakdowns = mutableListOf<FormulaBreakdown>()
        breakdowns += FormulaBreakdown(
            formula = "L = |CH_to - CH_from|",
            inputsSummary = "${segment.fromChainage} → ${segment.toChainage}",
            result = lengthM,
            unit = MeasurementUnit.M,
            classification = classification
        )

        var areaSqm = 0.0
        var volumeCum: Double? = null

        when (segment.measurementMethod) {
            MeasurementMethod.UNIFORM_SECTION, MeasurementMethod.STRUCTURAL_UNITS -> {
                if (avgWidth != null && lengthM > 0.0) {
                    areaSqm = lengthM * avgWidth * units
                    breakdowns += FormulaBreakdown(
                        formula = if (units > 1) "Area = L × W_avg × Units" else "Area = L × W_avg",
                        inputsSummary = "${fmt(lengthM)} m × ${fmt(avgWidth)} m" + if (units > 1) " × $units" else "",
                        result = areaSqm,
                        unit = MeasurementUnit.SQM,
                        classification = classification
                    )
                }
                if (volumeApplicable && avgWidth != null && effectiveDepthM != null && lengthM > 0.0) {
                    volumeCum = lengthM * avgWidth * effectiveDepthM * units
                    breakdowns += FormulaBreakdown(
                        formula = if (units > 1) "Volume = L × W_avg × D_compacted × Units" else "Volume = L × W_avg × D_compacted",
                        inputsSummary = "${fmt(lengthM)} m × ${fmt(avgWidth)} m × ${fmt(effectiveDepthRaw!!)} ${segment.thicknessUnit.symbol} (${fmt(effectiveDepthM)} m)",
                        result = volumeCum,
                        unit = MeasurementUnit.CUM,
                        classification = classification
                    )
                }
            }
            MeasurementMethod.AVERAGE_END_AREA -> {
                val a1 = segment.crossSectionArea1Sqm
                val a2 = segment.crossSectionArea2Sqm
                if (avgWidth != null && lengthM > 0.0) {
                    areaSqm = lengthM * avgWidth
                }
                if (volumeApplicable && a1 != null && a2 != null && lengthM > 0.0) {
                    volumeCum = ((a1 + a2) / 2.0) * lengthM
                    breakdowns += FormulaBreakdown(
                        formula = "Volume = ((A₁ + A₂) / 2) × L",
                        inputsSummary = "((${fmt(a1)} + ${fmt(a2)}) / 2) m² × ${fmt(lengthM)} m",
                        result = volumeCum,
                        unit = MeasurementUnit.CUM,
                        classification = classification
                    )
                }
            }
            MeasurementMethod.IRREGULAR_SURVEYED -> {
                val surveyed = segment.surveyedAreaSqm ?: 0.0
                areaSqm = max(0.0, surveyed) * units
                breakdowns += FormulaBreakdown(
                    formula = "Area = Surveyed Area",
                    inputsSummary = "${fmt(surveyed)} m²" + if (units > 1) " × $units" else "",
                    result = areaSqm,
                    unit = MeasurementUnit.SQM,
                    classification = classification
                )
                if (volumeApplicable && areaSqm > 0.0 && effectiveDepthM != null) {
                    volumeCum = areaSqm * effectiveDepthM
                    breakdowns += FormulaBreakdown(
                        formula = "Volume = Surveyed Area × D_avg",
                        inputsSummary = "${fmt(areaSqm)} m² × ${fmt(effectiveDepthM)} m",
                        result = volumeCum,
                        unit = MeasurementUnit.CUM,
                        classification = classification
                    )
                }
            }
        }

        val matQty = if (volumeCum != null && segment.verifiedDensity != null && segment.verifiedDensity > 0.0) {
            val q = volumeCum * segment.verifiedDensity
            breakdowns += FormulaBreakdown(
                formula = "Quantity = Volume × Verified Density",
                inputsSummary = "${fmt(volumeCum)} m³ × ${fmt(segment.verifiedDensity)} ${segment.densityUnit ?: "MT/m³"}",
                result = q,
                unit = MeasurementUnit.MT,
                classification = QuantityClassification.ESTIMATED
            )
            q
        } else {
            null
        }

        val thicknessVariance = if (segment.designThickness != null && segment.actualCompactedThickness != null) {
            computeVariance(
                design = segment.designThickness,
                actual = segment.actualCompactedThickness
            )
        } else {
            null
        }

        return SegmentCalculationResult(
            segmentId = segment.id,
            fromChainageM = fromM,
            toChainageM = toM,
            lengthM = lengthM,
            averageWidthM = avgWidth,
            effectiveDepthM = effectiveDepthM,
            areaSqm = areaSqm,
            volumeCum = volumeCum,
            materialQuantity = matQty,
            materialUnit = if (matQty != null) MeasurementUnit.MT else null,
            classification = classification,
            varianceThickness = thicknessVariance,
            formulaBreakdowns = breakdowns
        )
    }

    /**
     * Calculates all segments independently, detects chainage overlaps on the same (side, lane),
     * and prevents double counting of overlapping chainage lengths.
     */
    fun calculateSegmentsWithOverlapGuard(
        segments: List<SiteSegmentRecord>,
        volumeApplicable: Boolean = true
    ): MultiSegmentSummary {
        if (segments.isEmpty()) {
            return MultiSegmentSummary(emptyList(), 0.0, 0.0, false, emptyList(), 0.0, 0.0, null)
        }

        val rawResults = segments.map { calculateSegment(it, volumeApplicable) }
        val overlapWarnings = mutableListOf<String>()
        val overlapMap = mutableMapOf<String, String>()

        // Group intervals by (side, lane) to detect overlapping sections
        val grouped = segments.zip(rawResults).groupBy { (seg, _) ->
            "${seg.side.uppercase(Locale.US)}|${seg.lane.uppercase(Locale.US)}"
        }

        var deduplicatedLengthTotal = 0.0
        var deduplicatedAreaTotal = 0.0
        var deduplicatedVolumeTotal = 0.0
        var deduplicatedMaterialTotal = 0.0
        var hasMaterial = false

        grouped.forEach { (key, pairs) ->
            val sorted = pairs.sortedBy { (_, r) ->
                if (r.fromChainageM != null && r.toChainageM != null) min(r.fromChainageM, r.toChainageM) else Double.MAX_VALUE
            }
            val mergedIntervals = mutableListOf<Pair<Double, Double>>()
            var prevEnd = -1.0
            var prevLabel = ""

            for ((seg, res) in sorted) {
                val a = res.fromChainageM
                val b = res.toChainageM
                if (a != null && b != null && abs(b - a) > 1e-6) {
                    val start = min(a, b)
                    val end = max(a, b)
                    if (prevEnd > start + 1e-6) {
                        val overlapM = min(prevEnd, end) - start
                        val warn = "Overlap of ${fmt(overlapM)} m detected on $key between $prevLabel and ${seg.fromChainage}–${seg.toChainage}"
                        overlapWarnings += warn
                        overlapMap[seg.id] = warn

                        // Deduct overlapping portion from cumulative totals so we never double-count
                        val nonOverlapFraction = ((end - start - overlapM).coerceAtLeast(0.0)) / (end - start)
                        deduplicatedAreaTotal += res.areaSqm * nonOverlapFraction
                        deduplicatedVolumeTotal += (res.volumeCum ?: 0.0) * nonOverlapFraction
                        if (res.materialQuantity != null) {
                            hasMaterial = true
                            deduplicatedMaterialTotal += res.materialQuantity * nonOverlapFraction
                        }
                    } else {
                        deduplicatedAreaTotal += res.areaSqm
                        deduplicatedVolumeTotal += (res.volumeCum ?: 0.0)
                        if (res.materialQuantity != null) {
                            hasMaterial = true
                            deduplicatedMaterialTotal += res.materialQuantity
                        }
                    }

                    if (mergedIntervals.isEmpty() || start > mergedIntervals.last().second) {
                        mergedIntervals.add(start to end)
                    } else {
                        val last = mergedIntervals.removeAt(mergedIntervals.lastIndex)
                        mergedIntervals.add(last.first to max(last.second, end))
                    }
                    if (end > prevEnd) {
                        prevEnd = end
                        prevLabel = "${seg.fromChainage}–${seg.toChainage}"
                    }
                } else {
                    // Non-chainage / surveyed segments sum directly
                    deduplicatedLengthTotal += res.lengthM
                    deduplicatedAreaTotal += res.areaSqm
                    deduplicatedVolumeTotal += (res.volumeCum ?: 0.0)
                    if (res.materialQuantity != null) {
                        hasMaterial = true
                        deduplicatedMaterialTotal += res.materialQuantity
                    }
                }
            }
            deduplicatedLengthTotal += mergedIntervals.sumOf { (s, e) -> e - s }
        }

        val annotatedResults = rawResults.map { res ->
            val warn = overlapMap[res.segmentId]
            if (warn != null) res.copy(hasOverlapWarning = true, overlapMessage = warn) else res
        }

        return MultiSegmentSummary(
            segmentResults = annotatedResults,
            rawTotalLengthM = rawResults.sumOf { it.lengthM },
            deduplicatedLengthM = deduplicatedLengthTotal,
            hasOverlaps = overlapWarnings.isNotEmpty(),
            overlapWarnings = overlapWarnings,
            totalAreaSqm = deduplicatedAreaTotal,
            totalVolumeCum = deduplicatedVolumeTotal,
            totalMaterialQty = if (hasMaterial) deduplicatedMaterialTotal else null
        )
    }

    // =========================================================================
    // 3. BC LAYING VEHICLE-WISE CALCULATOR (WITH OVERLAP DEDUPLICATION)
    // =========================================================================

    data class BcEntryCalculation(
        val entryId: String,
        val lengthM: Double,
        val averageWidthM: Double?,
        val areaSqm: Double,
        val compactedVolumeCum: Double?,
        val materialMt: Double?,
        val materialClassification: QuantityClassification,
        val hasChainageOverlap: Boolean = false
    )

    data class BcDailySummary(
        val entries: List<BcEntryCalculation>,
        val vehicleCount: Int,
        val deduplicatedLaidLengthM: Double,
        val deduplicatedLaidAreaSqm: Double,
        val deduplicatedCompactedVolumeCum: Double,
        val totalMeasuredMaterialMt: Double,
        val overlapWarnings: List<String>
    )

    fun calculateBcLayingSummary(entries: List<BcLayingEntry>): BcDailySummary {
        if (entries.isEmpty()) {
            return BcDailySummary(emptyList(), 0, 0.0, 0.0, 0.0, 0.0, emptyList())
        }
        val segmentProxies = entries.mapIndexed { idx, e ->
            SiteSegmentRecord(
                id = e.id,
                registerId = e.registerId,
                revisionNumber = e.revisionNumber,
                segmentIndex = idx + 1,
                fromChainage = e.fromChainage,
                toChainage = e.toChainage,
                side = e.side,
                lane = e.lane,
                startWidthM = e.startWidthM,
                middleWidthM = e.middleWidthM,
                endWidthM = e.endWidthM,
                designThickness = e.designThicknessMm,
                looseThickness = e.looseThicknessMm,
                actualCompactedThickness = e.compactedThicknessMm,
                thicknessUnit = MeasurementUnit.MM,
                verifiedDensity = e.verifiedDensityMtPerCum
            )
        }
        val multi = calculateSegmentsWithOverlapGuard(segmentProxies, volumeApplicable = true)
        val byId = multi.segmentResults.associateBy { it.segmentId }

        val calcs = entries.map { e ->
            val seg = byId[e.id]
            val length = e.actualLaidLengthM ?: seg?.lengthM ?: 0.0
            val avgW = seg?.averageWidthM
            val area = if (avgW != null) length * avgW else 0.0
            val vol = if (avgW != null && e.compactedThicknessMm != null) {
                length * avgW * (e.compactedThicknessMm / 1000.0)
            } else {
                null
            }
            val (mat, cls) = when {
                e.measuredMaterialMt != null -> e.measuredMaterialMt to QuantityClassification.MEASURED
                vol != null && e.verifiedDensityMtPerCum != null && e.verifiedDensityMtPerCum > 0.0 ->
                    (vol * e.verifiedDensityMtPerCum) to QuantityClassification.ESTIMATED
                else -> null to QuantityClassification.MEASURED
            }
            BcEntryCalculation(
                entryId = e.id,
                lengthM = length,
                averageWidthM = avgW,
                areaSqm = area,
                compactedVolumeCum = vol,
                materialMt = mat,
                materialClassification = cls,
                hasChainageOverlap = seg?.hasOverlapWarning == true
            )
        }

        // Vehicle weighbridge/measured tonnes are additive per vehicle even if two tippers serve an overlapping paving strip,
        // while laid length/area/volume are strictly deduplicated to prevent double counting!
        val totalMatMt = calcs.sumOf { it.materialMt ?: 0.0 }

        return BcDailySummary(
            entries = calcs,
            vehicleCount = entries.size,
            deduplicatedLaidLengthM = multi.deduplicatedLengthM,
            deduplicatedLaidAreaSqm = multi.totalAreaSqm,
            deduplicatedCompactedVolumeCum = multi.totalVolumeCum,
            totalMeasuredMaterialMt = totalMatMt,
            overlapWarnings = multi.overlapWarnings
        )
    }

    // =========================================================================
    // 4. TACK COAT APPLICATION & TACK COAT SPRAY TEST CALCULATOR
    // =========================================================================

    data class TackCoatApplicationResult(
        val areaSqm: Double,
        val estimatedRequirementKg: Double?,
        val actualConsumptionKg: Double?,
        val varianceKg: Double?,
        val formulaSummary: String
    )

    fun calculateTackCoatApplication(
        lengthM: Double,
        widthM: Double,
        sprayRateKgPerSqm: Double?,
        actualConsumptionKg: Double?
    ): TackCoatApplicationResult {
        val safeL = max(0.0, lengthM)
        val safeW = max(0.0, widthM)
        val area = safeL * safeW
        val estimated = if (sprayRateKgPerSqm != null && sprayRateKgPerSqm > 0.0) {
            area * sprayRateKgPerSqm
        } else null
        val variance = if (estimated != null && actualConsumptionKg != null) {
            actualConsumptionKg - estimated
        } else null
        return TackCoatApplicationResult(
            areaSqm = area,
            estimatedRequirementKg = estimated,
            actualConsumptionKg = actualConsumptionKg,
            varianceKg = variance,
            formulaSummary = "Area = ${fmt(safeL)} × ${fmt(safeW)} = ${fmt(area)} m²" +
                (estimated?.let { " | Est. Req = ${fmt(area)} × ${fmt(sprayRateKgPerSqm!!)} = ${fmt(it)} kg" } ?: "")
        )
    }

    data class TackCoatTrayResult(
        val trayNumber: Int,
        val isValid: Boolean,
        val validationError: String? = null,
        val bitumenWeightKg: Double? = null,
        val sprayRateKgPerSqm: Double? = null,
        val status: TestResultStatus = TestResultStatus.NEEDS_REVIEW
    )

    data class TackCoatSprayTestSummary(
        val trayResults: List<TackCoatTrayResult>,
        val validTrayCount: Int,
        val averageSprayRateKgPerSqm: Double?,
        val overallStatus: TestResultStatus,
        val formulaSummary: String
    )

    /**
     * Exact Tack Coat Spray Test tray calculator per Specification Part 9:
     * - BitumenWeight = AfterSpray - BeforeSpray
     * - SprayRate = BitumenWeight / TrayArea
     * - AverageSprayRate = sum(valid individual rates) / validTrayCount
     * - Validates zero/negative tray area, negative weights, after < before.
     * - Returns Pass/Fail ONLY when valid project-approved spec limits [specMinKgSqm, specMaxKgSqm] are provided.
     */
    fun calculateTackCoatSprayTest(
        trays: List<TackCoatTrayRow>,
        specMinKgSqm: Double? = null,
        specMaxKgSqm: Double? = null
    ): TackCoatSprayTestSummary {
        val hasValidCriteria = specMinKgSqm != null && specMaxKgSqm != null &&
            specMinKgSqm > 0.0 && specMaxKgSqm >= specMinKgSqm

        val results = trays.map { row ->
            when {
                row.trayAreaSqm <= 0.0 -> TackCoatTrayResult(
                    trayNumber = row.trayNumber,
                    isValid = false,
                    validationError = "Tray area must be > 0 m²",
                    status = TestResultStatus.NEEDS_REVIEW
                )
                row.weightBeforeKg < 0.0 || row.weightAfterKg < 0.0 -> TackCoatTrayResult(
                    trayNumber = row.trayNumber,
                    isValid = false,
                    validationError = "Tray weights cannot be negative",
                    status = TestResultStatus.NEEDS_REVIEW
                )
                row.weightAfterKg < row.weightBeforeKg -> TackCoatTrayResult(
                    trayNumber = row.trayNumber,
                    isValid = false,
                    validationError = "Weight after spray cannot be less than weight before spray",
                    status = TestResultStatus.NEEDS_REVIEW
                )
                else -> {
                    val bitumenKg = row.weightAfterKg - row.weightBeforeKg
                    val rate = bitumenKg / row.trayAreaSqm
                    val trayStatus = if (hasValidCriteria) {
                        if (rate in specMinKgSqm!!..specMaxKgSqm!!) TestResultStatus.PASS else TestResultStatus.FAIL
                    } else {
                        TestResultStatus.NEEDS_REVIEW
                    }
                    TackCoatTrayResult(
                        trayNumber = row.trayNumber,
                        isValid = true,
                        bitumenWeightKg = bitumenKg,
                        sprayRateKgPerSqm = rate,
                        status = trayStatus
                    )
                }
            }
        }

        val validRates = results.mapNotNull { if (it.isValid) it.sprayRateKgPerSqm else null }
        val avg = if (validRates.isNotEmpty()) validRates.sum() / validRates.size else null
        val overall = when {
            avg == null || !hasValidCriteria -> TestResultStatus.NEEDS_REVIEW
            results.any { !it.isValid } -> TestResultStatus.NEEDS_REVIEW
            avg in specMinKgSqm!!..specMaxKgSqm!! && results.all { it.status == TestResultStatus.PASS } -> TestResultStatus.PASS
            else -> TestResultStatus.FAIL
        }

        val summary = if (avg != null) {
            "Avg Spray Rate = Σ(${validRates.size} valid trays) / ${validRates.size} = ${String.format(Locale.US, "%.4f", avg)} kg/m²"
        } else {
            "No valid tray measurements"
        }

        return TackCoatSprayTestSummary(
            trayResults = results,
            validTrayCount = validRates.size,
            averageSprayRateKgPerSqm = avg,
            overallStatus = overall,
            formulaSummary = summary
        )
    }

    // =========================================================================
    // 5. GLASS GRID / GEOGRID EFFECTIVE COVERAGE (ZERO OVERLAP DOUBLE COUNTING)
    // =========================================================================

    data class GlassGridCalculation(
        val grossRollWidthM: Double,
        val overlapDeductionWidthM: Double,
        val effectiveCoveredWidthM: Double,
        val netApplicationAreaSqm: Double,
        val formulaSummary: String
    )

    fun calculateGlassGridCoverage(entry: GlassGridEntry): GlassGridCalculation {
        val rolls = max(1, entry.numberOfRolls)
        val rollW = max(0.0, entry.rollWidthM)
        val overlap = max(0.0, entry.overlapWidthM).coerceAtMost(rollW)
        val grossWidth = rollW * rolls
        val overlapDeduction = overlap * (rolls - 1)
        val rawEffectiveWidth = (grossWidth - overlapDeduction).coerceAtLeast(0.0)
        // Never exceed actual road application width (prevents double counting overlap or overhang)
        val effectiveWidth = if (entry.applicationWidthM > 0.0) {
            min(rawEffectiveWidth, entry.applicationWidthM)
        } else {
            rawEffectiveWidth
        }
        val length = max(0.0, entry.applicationLengthM)
        val area = length * effectiveWidth
        return GlassGridCalculation(
            grossRollWidthM = grossWidth,
            overlapDeductionWidthM = overlapDeduction,
            effectiveCoveredWidthM = effectiveWidth,
            netApplicationAreaSqm = area,
            formulaSummary = "W_eff = min(${rolls}×${fmt(rollW)} - ${rolls - 1}×${fmt(overlap)}, ${fmt(entry.applicationWidthM)}) = ${fmt(effectiveWidth)} m | Area = ${fmt(length)} × ${fmt(effectiveWidth)} = ${fmt(area)} m²"
        )
    }

    // =========================================================================
    // 6. CROSS-SECTION TRAPEZOIDAL & AVERAGE END-AREA EARTHWORK CALCULATOR
    // =========================================================================

    fun calculateTrapezoidalCrossSectionArea(
        topWidthM: Double,
        bottomWidthM: Double,
        heightOrDepthM: Double
    ): Double {
        if (topWidthM < 0.0 || bottomWidthM < 0.0 || heightOrDepthM <= 0.0) return 0.0
        return ((topWidthM + bottomWidthM) / 2.0) * heightOrDepthM
    }

    fun calculateAverageEndAreaVolume(
        area1Sqm: Double,
        area2Sqm: Double,
        lengthM: Double
    ): Double {
        if (area1Sqm < 0.0 || area2Sqm < 0.0 || lengthM <= 0.0) return 0.0
        return ((area1Sqm + area2Sqm) / 2.0) * lengthM
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.3f", v)
}
