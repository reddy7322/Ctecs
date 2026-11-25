package com.example.weather

import com.example.model.ActualSiteMeasurements
import com.example.model.CompactionPlanningInput
import com.example.model.CompactionPlanningResult
import com.example.model.ContractSpecificationConfig
import com.example.model.HighwayWorkPlan
import com.example.model.HighwayWorkPlanCalculation
import com.example.model.HourlyWeatherPoint
import com.example.model.HourlyWorkPlanSlot
import com.example.model.MachineryCategory
import com.example.model.ManpowerPlanningSummary
import com.example.model.ManpowerRoleAllocation
import com.example.model.PlannedMachineryItem
import com.example.model.TransportPlanningInput
import com.example.model.TransportPlanningResult
import com.example.model.TransportStatus
import com.example.model.WeatherConditionType
import com.example.model.WeatherForecastBundle
import com.example.model.WorkSuitabilityStatus
import java.util.Locale
import kotlin.math.ceil

/**
 * Deterministic, engineering-safeguarded calculation engine for optional CTECS Highway Work Planning.
 *
 * Strictly maintains separation between:
 * 1. Manufacturer Maximum Rated Capacity
 * 2. Actual Site Operating Capacity
 * 3. Estimated Planning Capacity (lowest bottleneck in production chain)
 * 4. Actual Site Production
 *
 * Never invents missing specs, never treats HYVA cylinder lift as truck legal payload,
 * never confuses body m³ with tonnes without verified density, and marks weather as
 * Insufficient Data whenever the forecast is missing or stale.
 */
object HighwayWorkPlanningEngine {

    fun calculatePlan(
        plan: HighwayWorkPlan,
        forecastBundle: WeatherForecastBundle?,
        contractSpec: ContractSpecificationConfig = ContractSpecificationConfig(),
        siteMeasurements: ActualSiteMeasurements = ActualSiteMeasurements(),
        nowMs: Long = System.currentTimeMillis(),
        staleThresholdMs: Long = 60L * 60_000L
    ): HighwayWorkPlanCalculation {
        // 1. Work geometry & estimated material demand (never invents material density)
        val lengthM = plan.effectiveLengthMeters
        val widthM = plan.effectiveWorkWidthMeters
        val thicknessMm = plan.layerThicknessMm?.takeIf { it > 0.0 }
        val density = plan.materialDensityTPerM3?.takeIf { it > 0.0 }

        val stretchesTotalArea = plan.stretches.sumOf { it.areaSqm }.takeIf { it > 0.0 }
        val primaryAreaSqm = if (lengthM != null && widthM != null) lengthM * widthM else null
        val workAreaSqm = when {
            stretchesTotalArea != null && primaryAreaSqm != null -> primaryAreaSqm + stretchesTotalArea
            primaryAreaSqm != null -> primaryAreaSqm
            else -> stretchesTotalArea
        }
        val materialVolumeM3 = if (workAreaSqm != null && thicknessMm != null) {
            workAreaSqm * (thicknessMm / 1000.0)
        } else {
            null
        }
        val estimatedMaterialTonnes = if (materialVolumeM3 != null && density != null) {
            materialVolumeM3 * density
        } else if (plan.targetQuantity != null && plan.targetQuantity > 0.0 && plan.activity.isBituminousProduction) {
            plan.targetQuantity
        } else {
            null
        }

        // 2. Plant & Paver effective operating capacity (strictly editable site operating capacity or efficiency-adjusted)
        val hmpItems = plan.machinery.filter { it.catalogEntry.category == MachineryCategory.HMP_PLANT }
        val paverItems = plan.machinery.filter { it.catalogEntry.category == MachineryCategory.ASPHALT_PAVER }
        val dumperItems = plan.machinery.filter { it.catalogEntry.category == MachineryCategory.HYVA_DUMPER }
        val tandemItems = plan.machinery.filter { it.catalogEntry.category == MachineryCategory.TANDEM_ROLLER }
        val ptrItems = plan.machinery.filter { it.catalogEntry.category == MachineryCategory.PTR_ROLLER }

        val hmpCapacityTph = sumEffectiveCapacity(hmpItems)
        val paverCapacityTph = sumEffectiveCapacity(paverItems)

        // 3. Transport Planning (uses real road-route lead distance & actual vehicle payload — NEVER HYVA cylinder rating)
        val effectiveTransportInput = enrichTransportFromMachinery(plan.transport, dumperItems)
        val demandRateForTransport = listOfNotNull(hmpCapacityTph, paverCapacityTph).minOrNull()
        val effectiveWorkingHours = plan.effectiveWorkingHoursValue
        val transportResult = calculateTransport(
            input = effectiveTransportInput,
            plannedWorkingHours = effectiveWorkingHours,
            targetHourlyDemandTonnes = demandRateForTransport
        )

        // 4. Compaction / Roller & PTR Planning (uses selected roller specs + user-editable JMF passes)
        val effectiveCompactionInput = enrichCompactionFromMachinery(
            input = plan.compaction,
            workWidthM = widthM,
            paverItems = paverItems,
            tandemItems = tandemItems,
            ptrItems = ptrItems
        )
        val compactionResult = calculateCompaction(
            input = effectiveCompactionInput,
            workAreaSqm = workAreaSqm,
            thicknessMm = thicknessMm,
            densityTPerM3 = density,
            targetHourlyTonnes = listOfNotNull(hmpCapacityTph, paverCapacityTph, transportResult.estimatedSupplyTonnesPerHour).minOrNull()
        )

        // 5. Manpower Shortage Analysis
        val manpowerSummary = calculateManpowerSummary(plan.manpower)

        // 6. Bottleneck-Driven Effective Hourly Production (lowest link in chain)
        val productionUnit = plan.activity.productionUnit
        val chainLinks = linkedMapOf<String, Double>()
        if (plan.actualProductionRatePerHour != null && plan.actualProductionRatePerHour > 0.0) {
            chainLinks["Actual Site Rate"] = plan.actualProductionRatePerHour
        }
        if (plan.activity.isBituminousProduction) {
            hmpCapacityTph?.let { chainLinks["HMP Production"] = it }
            paverCapacityTph?.let { chainLinks["Paver Capacity"] = it }
            transportResult.estimatedSupplyTonnesPerHour?.let { chainLinks["Dumper Transport Supply"] = it }
            compactionResult.totalRollingCapacityTonnesPerHour?.let { chainLinks["Roller Compaction"] = it }
        } else {
            // Non-bituminous primary equipment capacity
            val primaryMachines = plan.machinery.filter {
                it.catalogEntry.category != MachineryCategory.HYVA_DUMPER &&
                    it.catalogEntry.category != MachineryCategory.OTHER_EQUIPMENT
            }
            sumEffectiveCapacity(primaryMachines)?.let { chainLinks["Primary Machinery"] = it }
            if (transportResult.estimatedSupplyTonnesPerHour != null && density != null && density > 0.0 && productionUnit.startsWith("m³")) {
                chainLinks["Dumper Transport Supply"] = transportResult.estimatedSupplyTonnesPerHour / density
            }
        }

        val bottleneckEntry = chainLinks.minByOrNull { it.value }
        val baseHourlyProduction = bottleneckEntry?.value
        val bottleneckName = when {
            bottleneckEntry != null -> bottleneckEntry.key
            manpowerSummary.totalShortage > 0 -> "Manpower Shortage"
            else -> "Insufficient Machinery / Rate Data"
        }

        // 7. Weather Evaluation across planned shift hours
        val isWeatherMissingOrStale = forecastBundle == null ||
            forecastBundle.hourly.isEmpty() ||
            forecastBundle.isStaleAt(nowMs, staleThresholdMs)

        val machineryShortSummary = buildMachineryShortSummary(plan.machinery)
        val shiftTiming = plan.shiftTimingResult
        val plannedHours = if (shiftTiming.activeHours24List.isNotEmpty()) {
            shiftTiming.activeHours24List
        } else {
            val shiftHoursCount = plan.plannedWorkingHours.toInt().coerceIn(1, 24)
            (0 until shiftHoursCount).map { offset -> (plan.plannedStartHour24 + offset) % 24 }
        }

        val hourlyByLocalHour = forecastBundle?.hourly?.associateBy { it.localHourOfDay }.orEmpty()
        val matchedPoints: List<HourlyWeatherPoint?> = plannedHours.map { h -> hourlyByLocalHour[h] }

        val hourlySlots = plannedHours.mapIndexed { idx, hour24 ->
            val timeLabel24 = String.format(Locale.US, "%02d:00", hour24)
            val pt = matchedPoints[idx]
            if (isWeatherMissingOrStale || pt == null) {
                HourlyWorkPlanSlot(
                    hour24 = hour24,
                    timeLabel24 = timeLabel24,
                    conditionLabel = if (pt != null) "${pt.conditionLabel} (Stale)" else "Unavailable",
                    visualCondition = pt?.visualCondition ?: WeatherConditionType.CLOUDY,
                    temperatureC = pt?.temperatureC?.takeIf { it.isFinite() },
                    rainProbabilityPercent = pt?.rainProbabilityPercent?.takeIf { it in 0..100 },
                    windSpeedKmh = pt?.windSpeedKmh?.takeIf { it.isFinite() },
                    activityName = plan.effectiveActivityName,
                    machinerySummary = machineryShortSummary,
                    expectedProductionValue = null,
                    productionUnit = productionUnit,
                    suitability = WorkSuitabilityStatus.INSUFFICIENT_DATA,
                    engineeringExplanation = if (forecastBundle != null && forecastBundle.isStaleAt(nowMs, staleThresholdMs)) {
                        "Cached weather forecast is stale; refresh forecast & verify site conditions."
                    } else {
                        "Weather forecast unavailable for $timeLabel24; verify site conditions manually."
                    }
                )
            } else {
                val window = IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
                    activity = plan.activity.linkedWeatherActivity,
                    hourlyPoints = listOf(pt),
                    contractSpec = contractSpec,
                    siteMeasurements = if (idx <= 1) siteMeasurements else ActualSiteMeasurements()
                ).first()

                val expectedProd = when (window.status) {
                    WorkSuitabilityStatus.SUITABLE -> baseHourlyProduction
                    WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> baseHourlyProduction?.times(0.80)
                    WorkSuitabilityStatus.NOT_RECOMMENDED -> 0.0
                    WorkSuitabilityStatus.INSUFFICIENT_DATA -> null
                }

                HourlyWorkPlanSlot(
                    hour24 = hour24,
                    timeLabel24 = timeLabel24,
                    conditionLabel = pt.conditionLabel,
                    visualCondition = pt.visualCondition,
                    temperatureC = pt.temperatureC.takeIf { it.isFinite() },
                    rainProbabilityPercent = pt.rainProbabilityPercent.takeIf { it in 0..100 },
                    windSpeedKmh = pt.windSpeedKmh.takeIf { it.isFinite() },
                    activityName = plan.effectiveActivityName,
                    machinerySummary = machineryShortSummary,
                    expectedProductionValue = expectedProd,
                    productionUnit = productionUnit,
                    suitability = window.status,
                    engineeringExplanation = window.primaryReason
                )
            }
        }

        // 8. Daily estimated production & expected completion time
        val dailyProduction = if (baseHourlyProduction != null) {
            val workableSum = hourlySlots.mapNotNull { it.expectedProductionValue }.sum()
            if (hourlySlots.any { it.expectedProductionValue != null }) workableSum else null
        } else {
            null
        }

        val targetForCompletion = when {
            plan.targetQuantity != null && plan.targetQuantity > 0.0 -> plan.targetQuantity
            plan.activity.isBituminousProduction -> estimatedMaterialTonnes
            productionUnit.startsWith("m³") -> materialVolumeM3
            productionUnit.startsWith("m²") -> workAreaSqm
            else -> null
        }
        val expectedCompletionHours = if (targetForCompletion != null && baseHourlyProduction != null && baseHourlyProduction > 0.0) {
            targetForCompletion / baseHourlyProduction
        } else {
            null
        }

        // 9. Overall Weather Suitability & Best Shift Window
        val overallWeatherStatus = when {
            isWeatherMissingOrStale || hourlySlots.all { it.suitability == WorkSuitabilityStatus.INSUFFICIENT_DATA } ->
                WorkSuitabilityStatus.INSUFFICIENT_DATA
            hourlySlots.all { it.suitability == WorkSuitabilityStatus.NOT_RECOMMENDED } ->
                WorkSuitabilityStatus.NOT_RECOMMENDED
            hourlySlots.any { it.suitability == WorkSuitabilityStatus.NOT_RECOMMENDED || it.suitability == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION } ->
                WorkSuitabilityStatus.SUITABLE_WITH_CAUTION
            else -> WorkSuitabilityStatus.SUITABLE
        }

        val (bestWindowText, bestWindowReason) = computeShiftBestWindow(hourlySlots, plan.effectiveActivityName)

        // 10. Dynamic Context-Aware Engineering Recommendation (NEVER a fixed static template)
        val dynamicRecommendation = buildDynamicRecommendation(
            plan = plan,
            overallWeather = overallWeatherStatus,
            isWeatherStaleOrUnavailable = isWeatherMissingOrStale,
            bottleneck = bottleneckName,
            hmpTph = hmpCapacityTph,
            paverTph = paverCapacityTph,
            transportResult = transportResult,
            compactionResult = compactionResult,
            manpowerSummary = manpowerSummary,
            hourlySlots = hourlySlots
        )

        return HighwayWorkPlanCalculation(
            workAreaSqm = workAreaSqm,
            materialVolumeM3 = materialVolumeM3,
            estimatedMaterialTonnes = estimatedMaterialTonnes,
            hmpCapacityTonnesPerHour = hmpCapacityTph,
            paverCapacityTonnesPerHour = paverCapacityTph,
            transportResult = transportResult,
            compactionResult = compactionResult,
            manpowerSummary = manpowerSummary,
            effectiveHourlyProduction = baseHourlyProduction,
            productionUnit = productionUnit,
            estimatedDailyProduction = dailyProduction,
            expectedCompletionHours = expectedCompletionHours,
            limitingBottleneck = bottleneckName,
            overallWeatherSuitability = overallWeatherStatus,
            bestWorkingWindowText = bestWindowText,
            bestWorkingWindowReason = bestWindowReason,
            hourlySlots = hourlySlots,
            dynamicRecommendation = dynamicRecommendation,
            isWeatherStaleOrUnavailable = isWeatherMissingOrStale
        )
    }

    fun calculateTransport(
        input: TransportPlanningInput,
        plannedWorkingHours: Double,
        targetHourlyDemandTonnes: Double?
    ): TransportPlanningResult {
        val leadKm = input.roadLeadDistanceKm?.takeIf { it > 0.0 }
        val roundTripKm = leadKm?.times(2.0)
        val payloadT = input.actualDumperPayloadTonnes?.takeIf { it > 0.0 }
        val fleet = input.dumperCount.coerceAtLeast(0)

        val loadedTravelMin = input.loadedTravelTimeMin?.takeIf { it > 0.0 }
            ?: leadKm?.let { (it / input.loadedAvgSpeedKmh.coerceAtLeast(5.0)) * 60.0 }
        val emptyReturnMin = input.emptyReturnTimeMin?.takeIf { it > 0.0 }
            ?: leadKm?.let { (it / input.emptyAvgSpeedKmh.coerceAtLeast(5.0)) * 60.0 }

        val totalCycleMin = input.manualCycleTimeOverrideMin?.takeIf { it > 0.0 }
            ?: if (loadedTravelMin != null && emptyReturnMin != null) {
                input.loadingTimeMin.coerceAtLeast(0.0) +
                    loadedTravelMin +
                    input.unloadingTimeMin.coerceAtLeast(0.0) +
                    emptyReturnMin +
                    input.waitingQueueTimeMin.coerceAtLeast(0.0)
            } else {
                null
            }

        if (payloadT == null || totalCycleMin == null || totalCycleMin <= 0.0 || fleet <= 0) {
            val missing = buildList {
                if (leadKm == null && input.manualCycleTimeOverrideMin == null) add("road lead distance")
                if (payloadT == null) add("actual vehicle payload (tonnes)")
                if (fleet <= 0) add("dumper fleet size")
            }.joinToString(", ")
            return TransportPlanningResult(
                oneWayLeadKm = leadKm,
                roundTripKm = roundTripKm,
                loadedTravelMin = loadedTravelMin,
                emptyReturnMin = emptyReturnMin,
                totalCycleTimeMin = totalCycleMin,
                tripsPerDumperPerShift = null,
                totalTripsPerShift = null,
                dumperPayloadTonnes = payloadT,
                fleetSize = fleet,
                estimatedSupplyTonnesPerHour = null,
                totalShiftTransportCapacityTonnes = null,
                requiredDumpersForTarget = null,
                status = TransportStatus.INSUFFICIENT_DATA,
                statusDetail = "Enter $missing to estimate material supply."
            )
        }

        val tripsPerDumperPerHour = 60.0 / totalCycleMin
        val tripsPerDumperPerShift = tripsPerDumperPerHour * plannedWorkingHours.coerceAtLeast(1.0)
        val totalTripsPerShift = tripsPerDumperPerShift * fleet
        val supplyTph = fleet * tripsPerDumperPerHour * payloadT
        val shiftCapacityT = totalTripsPerShift * payloadT

        val requiredDumpers = if (targetHourlyDemandTonnes != null && targetHourlyDemandTonnes > 0.0) {
            val singleDumperTph = tripsPerDumperPerHour * payloadT
            if (singleDumperTph > 0.0) ceil(targetHourlyDemandTonnes / singleDumperTph).toInt().coerceAtLeast(1) else null
        } else {
            null
        }

        val status = when {
            requiredDumpers == null -> TransportStatus.SUFFICIENT
            fleet >= requiredDumpers -> TransportStatus.SUFFICIENT
            else -> TransportStatus.SHORTAGE
        }
        val detail = when (status) {
            TransportStatus.SUFFICIENT ->
                if (requiredDumpers != null) {
                    "Fleet of $fleet dumpers covers estimated demand (min $requiredDumpers required)."
                } else {
                    "Estimated supply ${String.format(Locale.US, "%.1f", supplyTph)} t/hr based on actual payload."
                }
            TransportStatus.SHORTAGE ->
                "Shortage of ${(requiredDumpers ?: fleet) - fleet} dumper(s): supply ${String.format(Locale.US, "%.1f", supplyTph)} t/hr < demand ${String.format(Locale.US, "%.1f", targetHourlyDemandTonnes ?: 0.0)} t/hr."
            TransportStatus.INSUFFICIENT_DATA -> "Insufficient transport inputs."
        }

        return TransportPlanningResult(
            oneWayLeadKm = leadKm,
            roundTripKm = roundTripKm,
            loadedTravelMin = loadedTravelMin,
            emptyReturnMin = emptyReturnMin,
            totalCycleTimeMin = totalCycleMin,
            tripsPerDumperPerShift = tripsPerDumperPerShift,
            totalTripsPerShift = totalTripsPerShift,
            dumperPayloadTonnes = payloadT,
            fleetSize = fleet,
            estimatedSupplyTonnesPerHour = supplyTph,
            totalShiftTransportCapacityTonnes = shiftCapacityT,
            requiredDumpersForTarget = requiredDumpers,
            status = status,
            statusDetail = detail
        )
    }

    fun calculateCompaction(
        input: CompactionPlanningInput,
        workAreaSqm: Double?,
        thicknessMm: Double?,
        densityTPerM3: Double?,
        targetHourlyTonnes: Double?
    ): CompactionPlanningResult {
        val paverWidth = input.paverWorkingWidthM?.takeIf { it > 0.0 }
        val drumWidth = input.tandemRollerDrumWidthM?.takeIf { it > 0.0 }
        val passes = input.requiredPasses?.takeIf { it > 0 }
        val eff = (input.siteEfficiencyPercent / 100.0).coerceIn(0.3, 1.0)

        if (drumWidth == null || passes == null) {
            val missing = buildList {
                if (drumWidth == null) add("selected roller drum width")
                if (passes == null) add("field-trial / JMF passes")
            }.joinToString(" & ")
            return CompactionPlanningResult(
                effectiveStripWidthM = drumWidth?.let { (it - input.overlapM).coerceAtLeast(0.4) },
                stripsAcrossPaverWidth = null,
                requiredPassesUsed = passes,
                estimatedCoverageSqmPerHourPerRoller = null,
                totalRollingCapacitySqmPerHour = null,
                totalRollingCapacityTonnesPerHour = null,
                estimatedRollingTimeHours = null,
                estimatedTandemRollersRequired = null,
                estimatedPtrRequired = null,
                hasCompactionBottleneck = false,
                advisoryNote = "Provide $missing (per approved JMF & field trial) to estimate compaction capacity."
            )
        }

        val effectiveStrip = (drumWidth - input.overlapM).coerceAtLeast(0.4)
        val strips = paverWidth?.let { ceil(it / effectiveStrip).toInt().coerceAtLeast(1) }
        val speedMPerHr = input.rollerSpeedKmh * 1000.0
        val coveragePerRollerSqmHr = (effectiveStrip * speedMPerHr * eff) / passes.toDouble()

        val activeRollers = input.tandemRollerCount.coerceAtLeast(0)
        val totalSqmPerHr = if (activeRollers > 0) coveragePerRollerSqmHr * activeRollers else null

        val tonnesPerSqm = if (thicknessMm != null && thicknessMm > 0.0 && densityTPerM3 != null && densityTPerM3 > 0.0) {
            (thicknessMm / 1000.0) * densityTPerM3
        } else {
            null
        }
        val totalTonnesPerHr = if (totalSqmPerHr != null && tonnesPerSqm != null) {
            totalSqmPerHr * tonnesPerSqm
        } else {
            null
        }
        val rollingTimeHrs = if (workAreaSqm != null && totalSqmPerHr != null && totalSqmPerHr > 0.0) {
            workAreaSqm / totalSqmPerHr
        } else {
            null
        }

        val tandemRequired = if (targetHourlyTonnes != null && tonnesPerSqm != null && tonnesPerSqm > 0.0) {
            val targetSqmHr = targetHourlyTonnes / tonnesPerSqm
            ceil(targetSqmHr / coveragePerRollerSqmHr).toInt().coerceAtLeast(1)
        } else {
            null
        }

        val ptrRequired = if (input.ptrWorkingWidthM != null && input.ptrWorkingWidthM > 0.0 && targetHourlyTonnes != null && tonnesPerSqm != null) {
            val ptrStrip = (input.ptrWorkingWidthM - input.overlapM).coerceAtLeast(0.5)
            val ptrCoverageSqmHr = (ptrStrip * (input.rollerSpeedKmh * 1.15 * 1000.0) * eff) / passes.toDouble()
            val targetSqmHr = targetHourlyTonnes / tonnesPerSqm
            ceil(targetSqmHr / ptrCoverageSqmHr).toInt().coerceAtLeast(1)
        } else if (tandemRequired != null) {
            1
        } else {
            null
        }

        val bottleneck = (tandemRequired != null && activeRollers > 0 && activeRollers < tandemRequired) ||
            (ptrRequired != null && input.ptrCount > 0 && input.ptrCount < ptrRequired)

        val advisory = when {
            activeRollers == 0 ->
                "Add tandem roller count to evaluate compaction fleet sufficiency (passes must follow JMF field trial)."
            bottleneck ->
                "Potential compaction bottleneck: deployed rollers ($activeRollers tandem, ${input.ptrCount} PTR) below estimated need (${tandemRequired ?: activeRollers} tandem, ${ptrRequired ?: input.ptrCount} PTR)."
            else ->
                "Estimated rolling capacity ${String.format(Locale.US, "%.0f", totalSqmPerHr ?: 0.0)} m²/hr using $passes passes (verify density by core/NDG per JMF trial)."
        }

        return CompactionPlanningResult(
            effectiveStripWidthM = effectiveStrip,
            stripsAcrossPaverWidth = strips,
            requiredPassesUsed = passes,
            estimatedCoverageSqmPerHourPerRoller = coveragePerRollerSqmHr,
            totalRollingCapacitySqmPerHour = totalSqmPerHr,
            totalRollingCapacityTonnesPerHour = totalTonnesPerHr,
            estimatedRollingTimeHours = rollingTimeHrs,
            estimatedTandemRollersRequired = tandemRequired,
            estimatedPtrRequired = ptrRequired,
            hasCompactionBottleneck = bottleneck,
            advisoryNote = advisory
        )
    }

    fun calculateManpowerSummary(roles: List<ManpowerRoleAllocation>): ManpowerPlanningSummary {
        val totalReq = roles.sumOf { it.requiredCount }
        val totalAvail = roles.sumOf { it.availableCount }
        val totalDep = roles.sumOf { it.deployedCount }
        val shortages = roles.filter { it.shortageCount > 0 }
        val totalShortage = shortages.sumOf { it.shortageCount }
        val affected = shortages
            .map { it.affectedActivityHint.ifBlank { it.team.label } }
            .distinct()
        return ManpowerPlanningSummary(
            totalRequired = totalReq,
            totalAvailable = totalAvail,
            totalDeployed = totalDep,
            totalShortage = totalShortage,
            shortageRoles = shortages,
            affectedActivities = affected
        )
    }

    private fun sumEffectiveCapacity(items: List<PlannedMachineryItem>): Double? {
        val values = items.mapNotNull { item ->
            item.effectiveHourlyCapacityPerUnit()?.times(item.quantity.coerceAtLeast(1))
        }
        return if (values.isEmpty()) null else values.sum()
    }

    private fun enrichTransportFromMachinery(
        input: TransportPlanningInput,
        dumperItems: List<PlannedMachineryItem>
    ): TransportPlanningInput {
        val firstDumper = dumperItems.firstOrNull() ?: return input
        val totalFleet = if (input.dumperCount > 0) input.dumperCount else dumperItems.sumOf { it.quantity }
        val payload = input.actualDumperPayloadTonnes
            ?: firstDumper.actualSiteLoadTonnes
            ?: firstDumper.actualRegisteredPayloadTonnes
            ?: firstDumper.catalogEntry.manufacturerRatedPayloadTonnes
        val bodyVol = input.dumperBodyVolumeM3 ?: firstDumper.catalogEntry.dumperBodyVolumeM3
        val label = input.selectedDumperModelLabel.ifBlank { firstDumper.catalogEntry.fullDisplayTitle }
        return input.copy(
            dumperCount = totalFleet,
            actualDumperPayloadTonnes = payload,
            dumperBodyVolumeM3 = bodyVol,
            selectedDumperModelLabel = label
        )
    }

    private fun enrichCompactionFromMachinery(
        input: CompactionPlanningInput,
        workWidthM: Double?,
        paverItems: List<PlannedMachineryItem>,
        tandemItems: List<PlannedMachineryItem>,
        ptrItems: List<PlannedMachineryItem>
    ): CompactionPlanningInput {
        val paverW = input.paverWorkingWidthM
            ?: workWidthM
            ?: paverItems.firstOrNull()?.actualWorkingWidthM
            ?: paverItems.firstOrNull()?.catalogEntry?.workingWidthM
        val tandemW = input.tandemRollerDrumWidthM
            ?: tandemItems.firstOrNull()?.actualWorkingWidthM
            ?: tandemItems.firstOrNull()?.catalogEntry?.workingWidthM
        val tandemCount = if (input.tandemRollerCount > 0) input.tandemRollerCount else tandemItems.sumOf { it.quantity }
        val ptrW = input.ptrWorkingWidthM
            ?: ptrItems.firstOrNull()?.actualWorkingWidthM
            ?: ptrItems.firstOrNull()?.catalogEntry?.workingWidthM
        val ptrCount = if (input.ptrCount > 0) input.ptrCount else ptrItems.sumOf { it.quantity }
        return input.copy(
            paverWorkingWidthM = paverW,
            tandemRollerDrumWidthM = tandemW,
            tandemRollerCount = tandemCount,
            ptrWorkingWidthM = ptrW,
            ptrCount = ptrCount
        )
    }

    private fun buildMachineryShortSummary(items: List<PlannedMachineryItem>): String {
        if (items.isEmpty()) return "No machinery selected"
        return items.take(3).joinToString(", ") { item ->
            val qtyPrefix = if (item.quantity > 1) "${item.quantity}× " else ""
            "$qtyPrefix${item.catalogEntry.modelName}"
        } + if (items.size > 3) " +${items.size - 3} more" else ""
    }

    private fun computeShiftBestWindow(
        slots: List<HourlyWorkPlanSlot>,
        activityName: String
    ): Pair<String, String> {
        if (slots.isEmpty() || slots.all { it.suitability == WorkSuitabilityStatus.INSUFFICIENT_DATA }) {
            return "Working window cannot be reliably determined" to
                "Live weather forecast is missing or stale for the planned shift."
        }

        fun longestRun(target: WorkSuitabilityStatus): Pair<Int, Int>? {
            var bestStart = -1
            var bestLen = 0
            var curStart = -1
            var curLen = 0
            slots.forEachIndexed { i, slot ->
                if (slot.suitability == target) {
                    if (curStart == -1) curStart = i
                    curLen++
                    if (curLen > bestLen) {
                        bestLen = curLen
                        bestStart = curStart
                    }
                } else {
                    curStart = -1
                    curLen = 0
                }
            }
            return if (bestStart >= 0 && bestLen > 0) bestStart to bestLen else null
        }

        val suitable = longestRun(WorkSuitabilityStatus.SUITABLE)
        if (suitable != null) {
            val startH = slots[suitable.first].hour24
            val endH = (slots[suitable.first + suitable.second - 1].hour24 + 1) % 24
            val range = String.format(Locale.US, "%02d:00 – %02d:00", startH, endH)
            return "$range ($activityName)" to "Longest continuous forecast window without rainfall or high-wind restrictions."
        }

        val caution = longestRun(WorkSuitabilityStatus.SUITABLE_WITH_CAUTION)
        if (caution != null) {
            val startH = slots[caution.first].hour24
            val endH = (slots[caution.first + caution.second - 1].hour24 + 1) % 24
            val range = String.format(Locale.US, "%02d:00 – %02d:00 (Caution)", startH, endH)
            return "$range ($activityName)" to "No fully unrestricted hours; site temperature, moisture & wind checks required."
        }

        return "No suitable window in planned shift" to "Forecast indicates adverse weather restrictions across all planned hours."
    }

    private fun buildDynamicRecommendation(
        plan: HighwayWorkPlan,
        overallWeather: WorkSuitabilityStatus,
        isWeatherStaleOrUnavailable: Boolean,
        bottleneck: String,
        hmpTph: Double?,
        paverTph: Double?,
        transportResult: TransportPlanningResult,
        compactionResult: CompactionPlanningResult,
        manpowerSummary: ManpowerPlanningSummary,
        hourlySlots: List<HourlyWorkPlanSlot>
    ): String {
        val sentences = mutableListOf<String>()

        // 1. Weather assessment
        when {
            isWeatherStaleOrUnavailable || overallWeather == WorkSuitabilityStatus.INSUFFICIENT_DATA ->
                sentences += "Weather forecast is currently unavailable or stale for ${plan.effectiveActivityName}; refresh weather and verify field conditions before mobilizing."
            overallWeather == WorkSuitabilityStatus.NOT_RECOMMENDED ->
                sentences += "Forecast conditions are unfavourable across the planned shift for ${plan.effectiveActivityName}; defer execution or reschedule to a dry window."
            overallWeather == WorkSuitabilityStatus.SUITABLE_WITH_CAUTION -> {
                val blockedHours = hourlySlots.count { it.suitability == WorkSuitabilityStatus.NOT_RECOMMENDED }
                sentences += if (blockedHours > 0) {
                    "Weather is partially favourable with $blockedHours restricted hour(s) during the planned shift; plan transverse joints and rain protection accordingly."
                } else {
                    "Forecast is workable with caution for ${plan.effectiveActivityName}; verify actual site conditions before starting."
                }
            }
            else ->
                sentences += "Weather conditions are currently favourable for the planned ${plan.effectiveActivityName} window."
        }

        // 2. Bottleneck & transport / compaction insight
        if (transportResult.status == TransportStatus.SHORTAGE) {
            sentences += "However, dumper supply (${String.format(Locale.US, "%.1f", transportResult.estimatedSupplyTonnesPerHour ?: 0.0)} t/hr) may limit ${if (hmpTph != null) "HMP/paver" else "paver"} production; consider adding dumper(s) or optimizing cycle time."
        } else if (compactionResult.hasCompactionBottleneck) {
            sentences += "Compaction fleet appears to be a potential bottleneck; review tandem roller / PTR deployment against JMF passes."
        } else if (bottleneck != "Insufficient Machinery / Rate Data" && bottleneck != "Manpower Shortage") {
            sentences += "Estimated output is currently governed by $bottleneck."
        }

        // 3. Manpower shortage insight
        if (manpowerSummary.totalShortage > 0) {
            val impacted = manpowerSummary.affectedActivities.take(2).joinToString(" & ")
            sentences += "Manpower shortage of ${manpowerSummary.totalShortage} personnel detected (impacting $impacted)."
        }

        // 4. Activity-specific field verification reminder
        sentences += when {
            plan.activity.isBituminousProduction ->
                "Verify dry base, actual pavement surface temperature, and mix laying/rolling temperatures on site before paving."
            plan.activity == com.example.model.PlanningWorkActivity.GSB ||
                plan.activity == com.example.model.PlanningWorkActivity.WMM ||
                plan.activity == com.example.model.PlanningWorkActivity.EARTHWORK ||
                plan.activity == com.example.model.PlanningWorkActivity.SUBGRADE_PREP ->
                "Confirm actual material moisture (OMC) and field density testing prior to rolling."
            plan.activity == com.example.model.PlanningWorkActivity.PQC_DLC ||
                plan.activity == com.example.model.PlanningWorkActivity.CONCRETE_WORKS ->
                "Confirm mix temperature, slump, rain covers, and immediate curing arrangements on site."
            else ->
                "Verify actual site surface readiness and obtain Engineer approval before work commencement."
        }

        return sentences.joinToString(" ")
    }
}
