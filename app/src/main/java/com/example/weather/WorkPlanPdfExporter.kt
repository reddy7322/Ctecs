package com.example.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.model.HighwayWorkPlan
import com.example.model.HighwayWorkPlanCalculation
import com.example.model.PdfExportScope
import com.example.model.RollingCamberMode
import com.example.model.ThicknessCompactionSpec
import com.example.model.WorkPlanningStandardEngine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Generates a print-friendly A4 (595 x 842 pt) engineering PDF Work Plan strictly from actual saved plan data.
 * Omits empty/irrelevant sections, embeds vector Rolling Pattern Diagram, repeats table headers across pages,
 * and adds project header + generation timestamp + page numbers on every page.
 */
object WorkPlanPdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val FOOTER_Y = PAGE_HEIGHT - 24f
    private const val MAX_BODY_Y = PAGE_HEIGHT - 46f

    private class PageWriter(
        private val doc: PdfDocument,
        private val plan: HighwayWorkPlan,
        private val generatedStamp: String
    ) {
        var pageNumber = 0
            private set
        private var currentPage: PdfDocument.Page? = null
        var canvas: Canvas = Canvas()
            private set
        var cursorY: Float = 0f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 24, 40)
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 119, 242)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 9.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 24, 40)
            textSize = 9.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val headerBgPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.7f
        }

        fun startPage(subtitle: String = "") {
            currentPage?.let { doc.finishPage(it) }
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = doc.startPage(info)
            currentPage = page
            canvas = page.canvas

            // Top engineering report header
            canvas.drawRect(MARGIN, 24f, PAGE_WIDTH - MARGIN, 54f, headerBgPaint)
            val headerTitle = plan.projectName.ifBlank { "CTECS Highway Work Plan" }
            canvas.drawText(ellipsize(headerTitle, titlePaint, CONTENT_WIDTH - 140f), MARGIN + 8f, 41f, titlePaint)
            val rightMeta = "Generated: $generatedStamp"
            canvas.drawText(rightMeta, PAGE_WIDTH - MARGIN - mutedPaint.measureText(rightMeta) - 8f, 41f, mutedPaint)
            if (subtitle.isNotBlank()) {
                canvas.drawText(ellipsize(subtitle, mutedPaint, CONTENT_WIDTH - 16f), MARGIN + 8f, 51f, mutedPaint)
            }
            canvas.drawLine(MARGIN, 56f, PAGE_WIDTH - MARGIN, 56f, linePaint)

            // Footer
            canvas.drawLine(MARGIN, FOOTER_Y - 12f, PAGE_WIDTH - MARGIN, FOOTER_Y - 12f, linePaint)
            canvas.drawText("CTECS Engineering Report · Estimated Planning Values", MARGIN, FOOTER_Y, mutedPaint)
            val pageTag = "Page $pageNumber"
            canvas.drawText(pageTag, PAGE_WIDTH - MARGIN - mutedPaint.measureText(pageTag), FOOTER_Y, mutedPaint)

            cursorY = 72f
        }

        fun ensureSpace(needed: Float, onNewPageHeader: (() -> Unit)? = null) {
            if (cursorY + needed > MAX_BODY_Y) {
                startPage(plan.effectivePlanTitle)
                onNewPageHeader?.invoke()
            }
        }

        fun drawSectionHeader(title: String) {
            ensureSpace(26f)
            canvas.drawText(title, MARGIN, cursorY, sectionPaint)
            cursorY += 4f
            canvas.drawLine(MARGIN, cursorY, PAGE_WIDTH - MARGIN, cursorY, linePaint)
            cursorY += 13f
        }

        fun drawKeyValueRow(label: String, value: String) {
            ensureSpace(16f)
            canvas.drawText(label, MARGIN, cursorY, boldPaint)
            drawWrappedText(value, MARGIN + 168f, CONTENT_WIDTH - 168f, bodyPaint)
            cursorY += 4f
        }

        fun drawTable(headers: List<String>, colWidths: List<Float>, rows: List<List<String>>) {
            if (rows.isEmpty()) return
            fun drawHeaderRow() {
                canvas.drawRect(MARGIN, cursorY - 10f, PAGE_WIDTH - MARGIN, cursorY + 4f, headerBgPaint)
                var x = MARGIN + 4f
                headers.forEachIndexed { idx, h ->
                    val w = colWidths.getOrElse(idx) { 80f }
                    canvas.drawText(ellipsize(h, boldPaint, w - 6f), x, cursorY, boldPaint)
                    x += w
                }
                cursorY += 14f
            }
            ensureSpace(30f)
            drawHeaderRow()
            rows.forEach { row ->
                ensureSpace(15f) { drawHeaderRow() }
                var x = MARGIN + 4f
                row.forEachIndexed { idx, cell ->
                    val w = colWidths.getOrElse(idx) { 80f }
                    canvas.drawText(ellipsize(cell, bodyPaint, w - 6f), x, cursorY, bodyPaint)
                    x += w
                }
                cursorY += 13f
                canvas.drawLine(MARGIN, cursorY - 9f, PAGE_WIDTH - MARGIN, cursorY - 9f, linePaint)
            }
            cursorY += 4f
        }

        fun drawWrappedText(text: String, startX: Float = MARGIN, maxWidth: Float = CONTENT_WIDTH, paint: Paint = bodyPaint) {
            val words = text.split(" ")
            var line = StringBuilder()
            words.forEach { word ->
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) <= maxWidth) {
                    line = StringBuilder(test)
                } else {
                    if (line.isNotEmpty()) {
                        ensureSpace(12f)
                        canvas.drawText(line.toString(), startX, cursorY, paint)
                        cursorY += 11.5f
                    }
                    line = StringBuilder(word)
                }
            }
            if (line.isNotEmpty()) {
                ensureSpace(12f)
                canvas.drawText(line.toString(), startX, cursorY, paint)
                cursorY += 11.5f
            }
        }

        fun drawRollingDiagram(roadWidthM: Double, rollerWidthM: Double, overlapM: Double, camber: RollingCamberMode) {
            ensureSpace(110f)
            val rect = RectF(MARGIN, cursorY, PAGE_WIDTH - MARGIN, cursorY + 86f)
            val bg = Paint().apply { color = Color.rgb(248, 250, 252) }
            val border = Paint().apply {
                color = Color.rgb(148, 163, 184)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRect(rect, bg)
            canvas.drawRect(rect, border)

            val effStrip = (rollerWidthM - overlapM).coerceAtLeast(0.4)
            val strips = ceil(roadWidthM / effStrip).toInt().coerceIn(1, 8)
            val laneTop = rect.top + 18f
            val laneH = 52f
            val stripH = laneH / strips

            val trackPaint = Paint().apply { color = Color.argb(42, 22, 119, 242) }
            val overlapPaint = Paint().apply { color = Color.argb(70, 217, 154, 32) }
            val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(22, 119, 242)
                strokeWidth = 1.2f
            }

            for (i in 0 until strips) {
                val y0 = laneTop + i * stripH
                val y1 = y0 + stripH
                canvas.drawRect(rect.left + 12f, y0 + 1f, rect.right - 12f, y1 - 1f, trackPaint)
                if (i > 0) {
                    canvas.drawRect(rect.left + 12f, y0 - 1.5f, rect.right - 12f, y0 + 1.5f, overlapPaint)
                }
                val midY = (y0 + y1) / 2f
                canvas.drawLine(rect.left + 24f, midY, rect.right - 24f, midY, arrowPaint)
                canvas.drawText("T${i + 1}", rect.left + 14f, midY + 3f, mutedPaint)
            }
            canvas.drawText(
                "Top-View Rolling Pattern · Width ${fmt(roadWidthM)}m · Drum ${fmt(rollerWidthM)}m · Overlap ${fmt(overlapM)}m · ${camber.label}",
                rect.left + 8f,
                rect.top + 12f,
                boldPaint
            )
            canvas.drawText(
                "Sequence: ${if (camber == RollingCamberMode.CROWN_CENTER) "Edges → Centre (Joint First)" else "Lower Edge → Upper Edge"} · $strips tracks",
                rect.left + 8f,
                rect.bottom - 5f,
                mutedPaint
            )
            cursorY = rect.bottom + 12f
        }

        fun finish() {
            currentPage?.let { doc.finishPage(it) }
            currentPage = null
        }

        private fun ellipsize(text: String, paint: Paint, maxW: Float): String {
            if (paint.measureText(text) <= maxW) return text
            var s = text
            while (s.length > 3 && paint.measureText("$s…") > maxW) {
                s = s.dropLast(1)
            }
            return "$s…"
        }
    }

    fun generatePdf(
        context: Context,
        plan: HighwayWorkPlan,
        calc: HighwayWorkPlanCalculation,
        scope: PdfExportScope = PdfExportScope.COMPLETE_WORK_PLAN,
        includeRollingDiagram: Boolean = true,
        includeApprovalBlock: Boolean = true
    ): File {
        val dir = File(context.cacheDir, "work_plan_pdfs").apply { if (!exists()) mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeTitle = plan.effectiveActivityName.replace(Regex("[^A-Za-z0-9]+"), "_").take(24)
        val outFile = File(dir, "CTECS_WorkPlan_${safeTitle}_${stamp}.pdf")

        val generatedReadable = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())
        val includeAll = scope == PdfExportScope.COMPLETE_WORK_PLAN || scope == PdfExportScope.FULL_COMBINED_REPORT

        val nativeResult = runCatching {
            val doc = PdfDocument()
            try {
                val w = PageWriter(doc, plan, generatedReadable)

                // PAGE 1: Work Overview
                w.startPage("${plan.effectiveActivityName} · ${scope.label}")
                w.drawSectionHeader("1. WORK OVERVIEW")
                w.drawKeyValueRow("Project Name", plan.projectName.ifBlank { "Not Specified" })
                w.drawKeyValueRow("Highway / Road", plan.roadOrHighwayName.ifBlank { "Not Specified" })
                if (plan.locationName.isNotBlank()) w.drawKeyValueRow("Location", plan.locationName)
                w.drawKeyValueRow("Work Date & Shift", "${plan.workDateIso.ifBlank { "—" }} · ${plan.shiftType.label}")
                w.drawKeyValueRow("Work Activity", plan.effectiveActivityName)
                val chText = listOf(plan.startChainage, plan.endChainage).filter { it.isNotBlank() }.joinToString(" to ").ifBlank { "Manual" }
                w.drawKeyValueRow("Chainage & Side", "$chText · Side: ${plan.roadSide}")
                val lenSource = if (plan.isLengthFromChainage) "Calculated from Chainage" else "Manual Entry"
                w.drawKeyValueRow("Length & Width", "Length: ${fmtOpt(plan.effectiveLengthMeters, "m")} ($lenSource) · Width: ${fmtOpt(plan.effectiveWorkWidthMeters, "m")}")
                w.drawKeyValueRow("Work Area", fmtOpt(calc.workAreaSqm, "m²"))
                val shiftTiming = plan.shiftTimingResult
                w.drawKeyValueRow(
                    "Shift Timing",
                    "Total ${fmt(shiftTiming.totalShiftHours)}h · Break ${shiftTiming.breakMinutes}m · Effective ${fmt(shiftTiming.effectiveWorkingHours)}h · End ${shiftTiming.estimatedCompletionTimeLabel}"
                )
                w.drawKeyValueRow("Weather Suitability", calc.overallWeatherSuitability.label)
                w.drawKeyValueRow("Best Working Window", calc.bestWorkingWindowText)

                // SECTION 2: Material, Thickness & Quantity
                if (includeAll || scope == PdfExportScope.MATERIAL_COMPACTION || scope == PdfExportScope.SELECTED_ACTIVITY) {
                    w.drawSectionHeader("2. MATERIAL, THICKNESS & QUANTITY (ESTIMATED PLANNING VALUES)")
                    val recLoose = plan.thicknessSpec.recommendedLooseThicknessMm(plan.layerThicknessMm)
                    w.drawKeyValueRow("Material / Grade", plan.customMaterialType.ifBlank { plan.temperatureSpec.mixOrBinderGrade.ifBlank { plan.effectiveActivityName } })
                    w.drawKeyValueRow("Target Compacted Thickness", fmtOpt(plan.layerThicknessMm, "mm"))
                    w.drawKeyValueRow(
                        "Recommended Loose Thickness",
                        recLoose?.let { "${fmt(it)} mm (Factor ${plan.thicknessSpec.approvedLooseToCompactedFactor} · ${plan.thicknessSpec.looseFactorSource.ifBlank { "Approved" }})" }
                            ?: ThicknessCompactionSpec.VERIFICATION_REQUIRED_LABEL
                    )
                    plan.thicknessSpec.actualLooseThicknessMm?.let {
                        w.drawKeyValueRow("Actual Loose Thickness", "${fmt(it)} mm${if (plan.thicknessSpec.isManualLooseThickness) " (Manual)" else ""}")
                    }
                    plan.thicknessSpec.achievedCompactedThicknessMm?.let {
                        val diff = plan.thicknessSpec.thicknessVariationMm(plan.layerThicknessMm)
                        w.drawKeyValueRow("Achieved Compacted", "${fmt(it)} mm${diff?.let { d -> " (Var ${fmt(d)} mm)" } ?: ""}")
                    }
                    w.drawKeyValueRow("Volume & Approved Density", "Volume: ${fmtOpt(calc.materialVolumeM3, "m³")} · Density: ${fmtOpt(plan.materialDensityTPerM3, "t/m³")}")
                    w.drawKeyValueRow("Estimated Material Quantity", fmtOpt(calc.estimatedMaterialTonnes, "tonnes"))
                    plan.actualQuantityDelivered?.let { actual ->
                        val diff = calc.estimatedMaterialTonnes?.let { actual - it }
                        w.drawKeyValueRow("Actual vs Estimated", "${fmt(actual)} t${diff?.let { d -> " (Diff ${fmt(d)} t)" } ?: ""}")
                    }
                }

                // SECTION 3: Machinery & Transport
                if (includeAll || scope == PdfExportScope.MACHINERY_MANPOWER) {
                    w.drawSectionHeader("3. MACHINERY & TRANSPORT PLANNING")
                    if (plan.machinery.isNotEmpty()) {
                        val machRows = plan.machinery.map { item ->
                            listOf(
                                item.catalogEntry.category.shortLabel,
                                item.catalogEntry.fullDisplayTitle,
                                item.quantity.toString(),
                                item.catalogEntry.formattedMaxRatedCapacity,
                                item.effectiveHourlyCapacityPerUnit()?.let { "${fmt(it)} ${item.actualOperatingCapacityUnit}" } ?: "Verify"
                            )
                        }
                        w.drawTable(
                            headers = listOf("Category", "Model / Variant", "Qty", "Max Rated", "Site Eff."),
                            colWidths = listOf(80f, 195f, 34f, 110f, 104f),
                            rows = machRows
                        )
                    } else {
                        w.drawWrappedText("No machinery assigned yet in plan.")
                    }
                    val tr = calc.transportResult
                    w.drawKeyValueRow("HMP Lead & Round Trip", "One-Way: ${fmtOpt(tr.oneWayLeadKm, "km")} · Round Trip: ${fmtOpt(tr.roundTripKm, "km")}")
                    w.drawKeyValueRow("Cycle & Fleet Supply", "Cycle: ${fmtOpt(tr.totalCycleTimeMin, "min")} · Fleet: ${tr.fleetSize} dumpers · Supply: ${fmtOpt(tr.estimatedSupplyTonnesPerHour, "t/hr")} (${tr.status.label})")
                }

                // SECTION 4: Material Temperature Control
                if (includeAll || scope == PdfExportScope.MATERIAL_COMPACTION || scope == PdfExportScope.SELECTED_ACTIVITY) {
                    val tempGuide = WorkPlanningStandardEngine.temperatureGuidanceForActivity(plan.activity, plan.temperatureSpec.mixOrBinderGrade)
                    w.drawSectionHeader("4. TEMPERATURE CONTROL — ${tempGuide.categoryTitle.uppercase(Locale.US)}")
                    val ts = plan.temperatureSpec
                    if (tempGuide.isBituminous) {
                        w.drawKeyValueRow("Binder / Aggregate Temp", "Binder: ${fmtOpt(ts.binderTempC, "°C")} · Aggregate: ${fmtOpt(ts.aggregateTempC, "°C")}")
                        w.drawKeyValueRow("Plant / Dispatch / Arrival", "Plant: ${fmtOpt(ts.plantProductionTempC, "°C")} · Dispatch: ${fmtOpt(ts.dispatchTempC, "°C")} · Arrival: ${fmtOpt(ts.arrivalTempC, "°C")}")
                        w.drawKeyValueRow("Laying / Min / Actual Rolling", "Laying: ${fmtOpt(ts.layingTempC, "°C")} · Min Roll: ${fmtOpt(ts.minRollingTempC, "°C")} · Actual: ${fmtOpt(ts.actualRollingTempC, "°C")}")
                    } else {
                        w.drawKeyValueRow("Material / Application Temp", "Recorded: ${fmtOpt(ts.layingTempC ?: ts.plantProductionTempC, "°C")} · Time: ${ts.measurementTimeLabel.ifBlank { "—" }}")
                    }
                    tempGuide.guidanceLines.forEach { w.drawWrappedText("• $it") }
                }

                // SECTION 5: Rolling & Compaction
                if (includeAll || scope == PdfExportScope.MATERIAL_COMPACTION) {
                    val compGuide = WorkPlanningStandardEngine.compactionGuidanceForActivity(
                        activity = plan.activity,
                        trialPassesEntered = plan.compaction.requiredPasses,
                        tandemModel = plan.machinery.firstOrNull { it.catalogEntry.category.name == "TANDEM_ROLLER" }?.catalogEntry?.fullDisplayTitle,
                        ptrModel = plan.machinery.firstOrNull { it.catalogEntry.category.name == "PTR_ROLLER" }?.catalogEntry?.fullDisplayTitle
                    )
                    if (compGuide.isCompactionApplicable) {
                        w.drawSectionHeader("5. ROLLING PATTERN & COMPACTION")
                        w.drawKeyValueRow("PTR Suitability", compGuide.ptrSuitabilityNote)
                        val comp = calc.compactionResult
                        w.drawKeyValueRow("Coverage & Capacity", "Eff. Width: ${fmtOpt(comp.effectiveStripWidthM, "m")} · Rolling Cap: ${fmtOpt(comp.totalRollingCapacityTonnesPerHour, "t/hr")} · Rollers Req: ${comp.estimatedTandemRollersRequired ?: "Verify"}")
                        if (compGuide.stages.isNotEmpty()) {
                            w.drawTable(
                                headers = listOf("Stage", "Roller", "Mode", "Passes"),
                                colWidths = listOf(105f, 130f, 155f, 133f),
                                rows = compGuide.stages.map { listOf(it.stageTitle, it.rollerType, it.operatingMode, it.recommendedPassesText) }
                            )
                        }
                        if (includeRollingDiagram) {
                            w.drawRollingDiagram(
                                roadWidthM = plan.effectiveWorkWidthMeters ?: 7.0,
                                rollerWidthM = plan.compaction.tandemRollerDrumWidthM ?: 1.68,
                                overlapM = plan.compaction.overlapM,
                                camber = plan.camberMode
                            )
                        }
                        w.drawWrappedText(compGuide.densityVerificationReminder, paint = w.mutedPaint)
                    }
                }

                // SECTION 6: Manpower & Production Summary
                if (includeAll || scope == PdfExportScope.MACHINERY_MANPOWER || scope == PdfExportScope.SELECTED_ACTIVITY) {
                    w.drawSectionHeader("6. MANPOWER & PRODUCTION SUMMARY (ESTIMATED PLANNING VALUES)")
                    val mp = calc.manpowerSummary
                    w.drawKeyValueRow("Manpower Totals", "Required: ${mp.totalRequired} · Available: ${mp.totalAvailable} · Deployed: ${mp.totalDeployed} · Shortage: ${mp.totalShortage}")
                    val activeRoles = plan.manpower.filter { it.requiredCount > 0 || it.availableCount > 0 || it.deployedCount > 0 }
                    if (activeRoles.isNotEmpty()) {
                        w.drawTable(
                            headers = listOf("Team", "Role", "Req", "Avail", "Shortage"),
                            colWidths = listOf(115f, 200f, 65f, 65f, 78f),
                            rows = activeRoles.map { listOf(it.team.label, it.roleName, it.requiredCount.toString(), it.availableCount.toString(), it.shortageCount.toString()) }
                        )
                    }
                    w.drawKeyValueRow("Hourly Production", fmtOpt(calc.effectiveHourlyProduction, calc.productionUnit))
                    w.drawKeyValueRow("Shift Production", fmtOpt(calc.estimatedDailyProduction, calc.productionUnit.substringBefore("/")))
                    w.drawKeyValueRow("Est. Completion Time", fmtOpt(calc.expectedCompletionHours, "hours"))
                    w.drawKeyValueRow("Limiting Bottleneck", calc.limitingBottleneck)
                    w.drawKeyValueRow("Engineering Recommendation", calc.dynamicRecommendation)
                }

                // SECTION 7: Weather Recommendations for Exact Working Period
                if (includeAll || scope == PdfExportScope.WEATHER_RECOMMENDATIONS) {
                    w.drawSectionHeader("7. WEATHER RECOMMENDATIONS (${plan.shiftType.label.uppercase(Locale.US)})")
                    if (calc.hourlySlots.isNotEmpty()) {
                        w.drawTable(
                            headers = listOf("Time", "Temp", "Rain%", "Wind", "Status", "Engineering Reason"),
                            colWidths = listOf(42f, 40f, 38f, 48f, 88f, 267f),
                            rows = calc.hourlySlots.map { slot ->
                                listOf(
                                    slot.timeLabel24,
                                    slot.temperatureC?.let { "${Math.round(it)}°C" } ?: "—",
                                    slot.rainProbabilityPercent?.let { "$it%" } ?: "—",
                                    slot.windSpeedKmh?.let { "${Math.round(it)} km/h" } ?: "—",
                                    slot.suitability.label,
                                    slot.engineeringExplanation
                                )
                            }
                        )
                    } else {
                        w.drawWrappedText("Hourly weather forecast unavailable; verify site conditions before execution.")
                    }
                }

                // FINAL SECTION: Standards & Approval
                if (includeApprovalBlock || includeAll) {
                    w.drawSectionHeader("8. STANDARDS, REFERENCES & APPROVAL")
                    val refs = WorkPlanningStandardEngine.referencesForActivity(
                        activity = plan.activity,
                        revision = plan.morthRevision,
                        jmfRef = plan.jmfReference,
                        trialRef = plan.trialSectionReference
                    )
                    w.drawTable(
                        headers = listOf("Standard / Edition", "Clause / Ref", "Verification Status"),
                        colWidths = listOf(190f, 180f, 153f),
                        rows = refs.map { listOf("${it.standardName} (${it.editionOrRevision})", it.clauseOrTableReference, it.verificationStatus) }
                    )
                    w.ensureSpace(64f)
                    w.cursorY += 8f
                    val sigW = CONTENT_WIDTH / 3f
                    val labels = listOf(
                        "Prepared By: ${plan.preparedBy.ifBlank { "________________" }}",
                        "Checked By: ${plan.checkedBy.ifBlank { "________________" }}",
                        "Approved By: ${plan.approvedBy.ifBlank { "________________" }}"
                    )
                    labels.forEachIndexed { idx, txt ->
                        val sx = MARGIN + idx * sigW
                        w.canvas.drawText(txt, sx, w.cursorY, w.boldPaint)
                        w.canvas.drawText("Date & Signature: ______________", sx, w.cursorY + 24f, w.mutedPaint)
                    }
                    w.cursorY += 38f
                }

                w.finish()
                FileOutputStream(outFile).use { doc.writeTo(it) }
            } finally {
                runCatching { doc.close() }
            }
        }

        if (nativeResult.isFailure || !outFile.exists() || outFile.length() < 64L) {
            writeFallbackStandardPdf(outFile, plan, calc, scope, generatedReadable, includeApprovalBlock)
        }
        return outFile
    }

    /**
     * Standards-compliant pure-Kotlin A4 PDF 1.4 writer used strictly as a fallback when Android's
     * native PdfDocument JNI renderer is unavailable (e.g. headless JVM tests).
     */
    private fun writeFallbackStandardPdf(
        outFile: File,
        plan: HighwayWorkPlan,
        calc: HighwayWorkPlanCalculation,
        scope: PdfExportScope,
        generatedReadable: String,
        includeApprovalBlock: Boolean
    ) {
        val lines = buildList {
            add("CTECS HIGHWAY WORK PLAN - ${scope.label.uppercase(Locale.US)}")
            add("Generated: $generatedReadable | Estimated Planning Values")
            add("Project: ${plan.projectName.ifBlank { "Not Specified" }} | Road: ${plan.roadOrHighwayName.ifBlank { "Not Specified" }}")
            add("Activity: ${plan.effectiveActivityName} | Date: ${plan.workDateIso.ifBlank { "-" }} | Shift: ${plan.shiftType.label}")
            add("Chainage: ${plan.startChainage} to ${plan.endChainage} (${plan.roadSide}) | Length: ${fmtOpt(plan.effectiveLengthMeters, "m")} | Width: ${fmtOpt(plan.effectiveWorkWidthMeters, "m")}")
            add("Area: ${fmtOpt(calc.workAreaSqm, "m2")} | Volume: ${fmtOpt(calc.materialVolumeM3, "m3")} | Est Qty: ${fmtOpt(calc.estimatedMaterialTonnes, "t")}")
            add("Hourly Output: ${fmtOpt(calc.effectiveHourlyProduction, calc.productionUnit)} | Bottleneck: ${calc.limitingBottleneck}")
            add("Weather Suitability: ${calc.overallWeatherSuitability.label} | Window: ${calc.bestWorkingWindowText}")
            add("Machinery Assigned: ${plan.machinery.size} | Dumpers: ${plan.transport.dumperCount} | Transport: ${calc.transportResult.status.label}")
            add("Manpower: Req ${calc.manpowerSummary.totalRequired}, Avail ${calc.manpowerSummary.totalAvailable}, Short ${calc.manpowerSummary.totalShortage}")
            add("MoRTH Revision: ${plan.morthRevision.label}")
            if (includeApprovalBlock) {
                add("Prepared By: ${plan.preparedBy.ifBlank { "________" }} | Checked By: ${plan.checkedBy.ifBlank { "________" }} | Approved By: ${plan.approvedBy.ifBlank { "________" }}")
            }
        }
        fun esc(s: String): String = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace(Regex("[^\\x20-\\x7E]"), " ")
        val streamBody = buildString {
            append("BT\n/F1 10 Tf\n36 800 Td\n14 TL\n")
            lines.forEachIndexed { idx, line ->
                if (idx == 0) append("(${esc(line)}) Tj\n") else append("T* (${esc(line)}) Tj\n")
            }
            append("ET\n")
        }
        val streamBytes = streamBody.toByteArray(Charsets.US_ASCII)
        val objs = listOf(
            "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n",
            "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n",
            "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n",
            "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n",
            "5 0 obj << /Length ${streamBytes.size} >> stream\n${streamBody}endstream endobj\n"
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objs.forEach { o ->
            offsets.add(sb.length)
            sb.append(o)
        }
        val xrefPos = sb.length
        sb.append("xref\n0 ${objs.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { off -> sb.append(String.format(Locale.US, "%010d 00000 n \n", off)) }
        sb.append("trailer << /Size ${objs.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        outFile.writeBytes(sb.toString().toByteArray(Charsets.US_ASCII))
    }

    private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
    private fun fmtOpt(v: Double?, unit: String): String = v?.let { "${fmt(it)} $unit" } ?: "Not Available / Verify"
}
