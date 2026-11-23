package com.example.siteregister.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.siteregister.engine.HighwayMeasurementEngine
import com.example.siteregister.model.BcLayingEntry
import com.example.siteregister.model.GlassGridEntry
import com.example.siteregister.model.HighwayActivityCategory
import com.example.siteregister.model.ProjectInfo
import com.example.siteregister.model.QualityTestReportRecord
import com.example.siteregister.model.SiteAuditEvent
import com.example.siteregister.model.SiteObservationRecord
import com.example.siteregister.model.SiteRegisterRecord
import com.example.siteregister.model.SiteRegisterRevision
import com.example.siteregister.model.SiteSegmentRecord
import com.example.siteregister.model.SiteSignatureRecord
import com.example.siteregister.model.TackCoatTestRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Production Daily Excel (.xlsx) and Printable PDF Export Engine for CTECS Site Register.
 *
 * - Generates genuine standards-compliant OOXML `.xlsx` workbooks containing all 14 required
 *   worksheets with styled headers, frozen header rows, auto-filters, and readable column widths.
 * - Generates printable PDFs using Landscape pages (842×595 pt) for wide registers and Portrait
 *   pages (595×842 pt) for individual test reports, including page numbers and generation timestamps.
 * - Never silently overwrites existing exported reports (always creates unique timestamped filenames).
 * - Explicitly flags if pending offline records have not yet synchronized and never labels
 *   estimated values as verified quantities.
 */
object SiteRegisterExportEngine {

    data class ExportBundleInput(
        val project: ProjectInfo,
        val reportDate: String,
        val activityFilter: String = "ALL",
        val registers: List<SiteRegisterRecord>,
        val revisions: List<SiteRegisterRevision> = emptyList(),
        val segmentsByRegister: Map<String, List<SiteSegmentRecord>> = emptyMap(),
        val bcEntriesByRegister: Map<String, List<BcLayingEntry>> = emptyMap(),
        val glassGridByRegister: Map<String, List<GlassGridEntry>> = emptyMap(),
        val tackCoatTests: List<TackCoatTestRecord> = emptyList(),
        val qualityTests: List<QualityTestReportRecord> = emptyList(),
        val observations: List<SiteObservationRecord> = emptyList(),
        val signaturesByRegister: Map<String, List<SiteSignatureRecord>> = emptyMap(),
        val auditLogs: List<SiteAuditEvent> = emptyList(),
        val pendingOfflineSyncCount: Int = 0
    )

    data class ExportResult(
        val file: File,
        val fileName: String,
        val mimeType: String,
        val sheetOrPageCount: Int,
        val pendingSyncWarning: String?
    )

    private val all14SheetNames = listOf(
        "Daily Summary",
        "BC Laying Register",
        "DBM BM Register",
        "Glass Grid Register",
        "Tack Coat Spray Test",
        "Other Quality Tests",
        "Measurements Quantities",
        "Earthwork",
        "Concrete Structures",
        "Drainage",
        "Site Observations",
        "Rectification",
        "Signatures Approvals",
        "Revision Audit Summary"
    )

    // =========================================================================
    // 1. REAL 14-SHEET OOXML .XLSX GENERATOR (ZERO THIRD-PARTY BLOAT)
    // =========================================================================

    fun exportDailyExcelWorkbook(
        context: Context,
        input: ExportBundleInput
    ): ExportResult {
        val outFile = createUniqueExportFile(
            context = context,
            prefix = "CTECS_SiteRegister_${sanitizeFilePart(input.project.projectName)}_${input.reportDate}",
            extension = "xlsx"
        )
        val filteredRegisters = if (input.activityFilter.equals("ALL", ignoreCase = true)) {
            input.registers
        } else {
            input.registers.filter { it.activityCode.equals(input.activityFilter, ignoreCase = true) }
        }

        val sheetsData: List<Pair<String, List<List<String>>>> = buildAll14Sheets(input, filteredRegisters)

        ZipOutputStream(FileOutputStream(outFile).buffered()).use { zos ->
            writeZip(zos, "[Content_Types].xml", buildContentTypesXml(sheetsData.size))
            writeZip(zos, "_rels/.rels", ROOT_RELS_XML)
            writeZip(zos, "xl/workbook.xml", buildWorkbookXml(sheetsData.map { it.first }))
            writeZip(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheetsData.size))
            writeZip(zos, "xl/styles.xml", STYLES_XML)

            sheetsData.forEachIndexed { idx, (_, rows) ->
                writeZip(zos, "xl/worksheets/sheet${idx + 1}.xml", buildWorksheetXml(rows))
            }
        }

        val warn = if (input.pendingOfflineSyncCount > 0) {
            "Warning: ${input.pendingOfflineSyncCount} offline record(s) have not yet synchronized to Supabase."
        } else null

        return ExportResult(
            file = outFile,
            fileName = outFile.name,
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            sheetOrPageCount = sheetsData.size,
            pendingSyncWarning = warn
        )
    }

    private fun buildAll14Sheets(
        input: ExportBundleInput,
        registers: List<SiteRegisterRecord>
    ): List<Pair<String, List<List<String>>>> {
        val tsNow = formatTimestamp(System.currentTimeMillis())
        val syncNote = if (input.pendingOfflineSyncCount > 0) {
            "PENDING OFFLINE SYNC (${input.pendingOfflineSyncCount} items)"
        } else "ALL SYNCHRONIZED"

        // Sheet 1: Daily Summary
        val s1 = mutableListOf(
            listOf("CTECS DIGITAL HIGHWAY SITE REGISTER – DAILY SUMMARY", "Project: ${input.project.projectName}", "Highway: ${input.project.highwayName}", "Date: ${input.reportDate}", "Sync: $syncNote", "Generated: $tsNow"),
            listOf("Reg No.", "Date", "Shift", "Activity", "Category", "Chainage From", "Chainage To", "Side", "Lane", "Length (m)", "Area (m²)", "Volume (m³)", "Material Qty", "Unit", "Classification", "Status", "Rev", "Remarks")
        )
        registers.forEach { r ->
            s1 += listOf(
                r.registerNumber, r.workDate, r.shift, r.activityName, r.activityCategory.displayLabel,
                r.chainageFrom, r.chainageTo, r.side, r.lane, fmt(r.totalLengthM), fmt(r.totalAreaSqm),
                fmt(r.totalVolumeCum), r.totalMaterialQty?.let { fmt(it) } ?: "—", r.totalMaterialUnit.symbol,
                r.quantityClassification.badgeLabel, r.status.displayLabel, "R${r.currentRevision}", r.remarks
            )
        }

        // Sheet 2: BC Laying Register
        val s2 = mutableListOf(
            listOf("Sl.No", "Register", "Vehicle No.", "BC Type", "Layer", "From CH", "To CH", "Side", "Lane", "Start", "End", "Laying °C", "Rolling °C", "W_start (m)", "W_mid (m)", "W_end (m)", "W_avg (m)", "Length (m)", "Design (mm)", "Loose (mm)", "Compacted (mm)", "Area (m²)", "Volume (m³)", "Material (MT)", "Class", "Remarks")
        )
        registers.filter { it.activityCode == "BC_LAYING" }.forEach { reg ->
            val entries = input.bcEntriesByRegister[reg.id].orEmpty()
            val summary = HighwayMeasurementEngine.calculateBcLayingSummary(entries)
            val calcById = summary.entries.associateBy { it.entryId }
            entries.forEach { e ->
                val c = calcById[e.id]
                s2 += listOf(
                    e.serialNumber.toString(), reg.registerNumber, e.vehicleNumber, e.bcType, e.layer,
                    e.fromChainage, e.toChainage, e.side, e.lane, e.startTime, e.endTime,
                    e.layingTempC?.let { fmt(it) } ?: "—", e.rollingTempC?.let { fmt(it) } ?: "—",
                    e.startWidthM?.let { fmt(it) } ?: "—", e.middleWidthM?.let { fmt(it) } ?: "—", e.endWidthM?.let { fmt(it) } ?: "—",
                    c?.averageWidthM?.let { fmt(it) } ?: "—", fmt(c?.lengthM ?: 0.0),
                    e.designThicknessMm?.let { fmt(it) } ?: "—", e.looseThicknessMm?.let { fmt(it) } ?: "—", e.compactedThicknessMm?.let { fmt(it) } ?: "—",
                    fmt(c?.areaSqm ?: 0.0), c?.compactedVolumeCum?.let { fmt(it) } ?: "—",
                    c?.materialMt?.let { fmt(it) } ?: "—", c?.materialClassification?.badgeLabel ?: "Measured", e.remarks
                )
            }
        }

        // Sheet 3: DBM / BM Register
        val s3 = mutableListOf(
            listOf("Register", "Activity", "Date", "Vehicle No.", "From CH", "To CH", "Side", "Lane", "Laying °C", "Rolling °C", "Length (m)", "Avg Width (m)", "Compacted (mm)", "Area (m²)", "Volume (m³)", "Material (MT)", "Status")
        )
        registers.filter { it.activityCode in setOf("DBM_LAYING", "BM_LAYING", "DENSE_GRADED_BITUMINOUS_MACADAM") }.forEach { r ->
            val entries = input.bcEntriesByRegister[r.id].orEmpty()
            if (entries.isEmpty()) {
                s3 += listOf(r.registerNumber, r.activityName, r.workDate, "—", r.chainageFrom, r.chainageTo, r.side, r.lane, "—", "—", fmt(r.totalLengthM), "—", "—", fmt(r.totalAreaSqm), fmt(r.totalVolumeCum), r.totalMaterialQty?.let { fmt(it) } ?: "—", r.status.displayLabel)
            } else {
                entries.forEach { e ->
                    val len = HighwayMeasurementEngine.chainageDifferenceMeters(e.fromChainage, e.toChainage) ?: 0.0
                    val avgW = HighwayMeasurementEngine.computeAverage(e.startWidthM, e.middleWidthM, e.endWidthM)
                    val area = if (avgW != null) len * avgW else 0.0
                    val vol = if (avgW != null && e.compactedThicknessMm != null) area * (e.compactedThicknessMm / 1000.0) else null
                    s3 += listOf(r.registerNumber, r.activityName, r.workDate, e.vehicleNumber, e.fromChainage, e.toChainage, e.side, e.lane, e.layingTempC?.let { fmt(it) } ?: "—", e.rollingTempC?.let { fmt(it) } ?: "—", fmt(len), avgW?.let { fmt(it) } ?: "—", e.compactedThicknessMm?.let { fmt(it) } ?: "—", fmt(area), vol?.let { fmt(it) } ?: "—", e.measuredMaterialMt?.let { fmt(it) } ?: "—", r.status.displayLabel)
                }
            }
        }

        // Sheet 4: Glass Grid Register
        val s4 = mutableListOf(
            listOf("Register", "From CH", "To CH", "Side", "Length (m)", "App Width (m)", "Roll Width (m)", "Rolls", "Overlap (m)", "Effective Width (m)", "Net Area (m²)", "Start", "End", "Tack Coat", "PTR Passes", "Alignment/Wrinkles", "Remarks")
        )
        registers.forEach { r ->
            input.glassGridByRegister[r.id].orEmpty().forEach { g ->
                val calc = HighwayMeasurementEngine.calculateGlassGridCoverage(g)
                s4 += listOf(
                    r.registerNumber, g.fromChainage, g.toChainage, g.applicationSide,
                    fmt(g.applicationLengthM), fmt(g.applicationWidthM), fmt(g.rollWidthM),
                    g.numberOfRolls.toString(), fmt(g.overlapWidthM), fmt(calc.effectiveCoveredWidthM),
                    fmt(calc.netApplicationAreaSqm), g.startTime, g.endTime, g.tackCoatStatus,
                    g.ptrPasses?.toString() ?: "—", g.wrinklesAlignment, g.remarks
                )
            }
        }

        // Sheet 5: Tack Coat Spray Test
        val s5 = mutableListOf(
            listOf("Test Date", "Chainage", "Side", "Material", "Temp (°C)", "Tray No.", "Tray Area (m²)", "Before (kg)", "After (kg)", "Bitumen (kg)", "Spray Rate (kg/m²)", "Spec Limit (kg/m²)", "Tray Result", "Avg Rate (kg/m²)", "Overall Status", "Tested By")
        )
        input.tackCoatTests.forEach { t ->
            val summary = HighwayMeasurementEngine.calculateTackCoatSprayTest(t.trays, t.specMinRateKgSqm, t.specMaxRateKgSqm)
            val specLabel = if (t.specMinRateKgSqm != null && t.specMaxRateKgSqm != null) "${fmt(t.specMinRateKgSqm)}–${fmt(t.specMaxRateKgSqm)}" else "Not Set"
            t.trays.zip(summary.trayResults).forEach { (row, res) ->
                s5 += listOf(
                    t.testDate, t.chainage, t.side, t.materialType, t.temperatureC?.let { fmt(it) } ?: "—",
                    row.trayNumber.toString(), fmt(row.trayAreaSqm), fmt(row.weightBeforeKg), fmt(row.weightAfterKg),
                    res.bitumenWeightKg?.let { fmt(it) } ?: "Invalid", res.sprayRateKgPerSqm?.let { String.format(Locale.US, "%.4f", it) } ?: "Invalid",
                    specLabel, res.status.label, summary.averageSprayRateKgPerSqm?.let { String.format(Locale.US, "%.4f", it) } ?: "—",
                    summary.overallStatus.label, t.testedBy
                )
            }
        }

        // Sheet 6: Other Quality Tests
        val s6 = mutableListOf(
            listOf("Date", "Category", "Test Title", "Chainage", "Side", "Location", "Tested By", "Measured Values", "Spec Criteria", "Result", "OCR Verified", "Photos", "Remarks")
        )
        input.qualityTests.forEach { qt ->
            s6 += listOf(
                qt.testDate, qt.category.label, qt.testTitle, qt.chainage, qt.side, qt.sampleLocation,
                qt.testedBy, qt.measuredValues.entries.joinToString("; ") { "${it.key}=${it.value}" },
                qt.specCriteria, qt.resultStatus.label, if (qt.ocrVerifiedManually) "Yes" else "No",
                qt.photoUris.size.toString(), qt.remarks
            )
        }

        // Sheet 7: Measurements & Quantities (All segments, Design vs Actual vs Verified + Formulas)
        val s7 = mutableListOf(
            listOf("Register", "Activity", "Seg#", "From CH", "To CH", "Side", "Lane", "Method", "Length (m)", "Avg Width (m)", "Design Depth", "Actual Depth", "Verified Depth", "Depth Unit", "Variance", "Variance %", "Area (m²)", "Volume (m³)", "Material Qty", "Classification", "Overlap Warn", "Formula")
        )
        registers.forEach { r ->
            val segs = input.segmentsByRegister[r.id].orEmpty()
            val multi = HighwayMeasurementEngine.calculateSegmentsWithOverlapGuard(segs)
            segs.zip(multi.segmentResults).forEach { (seg, res) ->
                s7 += listOf(
                    r.registerNumber, r.activityName, seg.segmentIndex.toString(), seg.fromChainage, seg.toChainage,
                    seg.side, seg.lane, seg.measurementMethod.displayLabel, fmt(res.lengthM),
                    res.averageWidthM?.let { fmt(it) } ?: "—", seg.designThickness?.let { fmt(it) } ?: "—",
                    seg.actualCompactedThickness?.let { fmt(it) } ?: "—", seg.verifiedThickness?.let { fmt(it) } ?: "—",
                    seg.thicknessUnit.symbol, res.varianceThickness?.let { fmt(it.variance) } ?: "—",
                    res.varianceThickness?.variancePercent?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—",
                    fmt(res.areaSqm), res.volumeCum?.let { fmt(it) } ?: "—",
                    res.materialQuantity?.let { fmt(it) } ?: "—", res.classification.badgeLabel,
                    if (res.hasOverlapWarning) "YES" else "No",
                    res.formulaBreakdowns.joinToString(" | ") { "${it.formula} (${it.inputsSummary})" }
                )
            }
        }

        // Sheet 8: Earthwork
        val s8 = buildCategoryRegisterSheet(registers.filter {
            it.activityCategory == HighwayActivityCategory.EARTHWORK_GRANULAR && it.activityCode != "DRAINAGE_WORK"
        })

        // Sheet 9: Concrete and Structures
        val s9 = buildCategoryRegisterSheet(registers.filter {
            it.activityCategory == HighwayActivityCategory.CONCRETE_STRUCTURES
        })

        // Sheet 10: Drainage
        val s10 = buildCategoryRegisterSheet(registers.filter { it.activityCode == "DRAINAGE_WORK" })

        // Sheet 11: Site Observations
        val s11 = mutableListOf(
            listOf("Sl.No", "Date", "Activity", "From CH", "To CH", "Side", "Lane", "Category", "Severity", "Affected L×W×D", "Area (m²)", "Volume (m³)", "Description", "Responsible Contractor", "Target Date", "Status")
        )
        input.observations.forEach { obs ->
            s11 += listOf(
                obs.serialNumber.toString(), formatTimestamp(obs.observedAtMs), obs.activityName,
                obs.chainageFrom, obs.chainageTo, obs.side, obs.lane, obs.category.label, obs.severity.label,
                "${obs.affectedLengthM ?: "—"}×${obs.affectedWidthM ?: "—"}×${obs.affectedDepthM ?: "—"}",
                obs.affectedAreaSqm?.let { fmt(it) } ?: "—", obs.affectedVolumeCum?.let { fmt(it) } ?: "—",
                obs.description, obs.responsibleContractor, obs.targetDate, obs.status.label
            )
        }

        // Sheet 12: Rectification
        val s12 = mutableListOf(
            listOf("Obs Sl.No", "Chainage", "Category", "Corrective Action", "Status", "Before Photos", "After Photos", "Verified By", "Verification Date", "Final Remarks")
        )
        input.observations.forEach { obs ->
            s12 += listOf(
                obs.serialNumber.toString(), "${obs.chainageFrom}–${obs.chainageTo}", obs.category.label,
                obs.correctiveAction, obs.status.label, obs.beforePhotoUris.size.toString(), obs.afterPhotoUris.size.toString(),
                obs.verifiedByName, obs.verificationDateMs?.let { formatTimestamp(it) } ?: "—", obs.finalRemarks
            )
        }

        // Sheet 13: Signatures and Approvals
        val s13 = mutableListOf(
            listOf("Register", "Rev", "Approval Status", "Approved By PMC", "Approved At", "Signer Name", "Designation", "Organization", "Role", "Signature Status", "Signed At")
        )
        registers.forEach { r ->
            val sigs = input.signaturesByRegister[r.id].orEmpty()
            if (sigs.isEmpty()) {
                s13 += listOf(r.registerNumber, "R${r.currentRevision}", r.status.displayLabel, r.approvedByName ?: "—", r.approvedAtMs?.let { formatTimestamp(it) } ?: "—", "—", "—", "—", "—", "Pending", "—")
            } else {
                sigs.forEach { sig ->
                    s13 += listOf(r.registerNumber, "R${sig.revisionNumber}", r.status.displayLabel, r.approvedByName ?: "—", r.approvedAtMs?.let { formatTimestamp(it) } ?: "—", sig.signerName, sig.signerDesignation, sig.signerOrganization, sig.signerRole.displayLabel, sig.status.label, formatTimestamp(sig.signedAtMs))
                }
            }
        }

        // Sheet 14: Revision and Audit Summary
        val s14 = mutableListOf(
            listOf("Timestamp", "Register ID", "Rev", "Action", "User", "Org Type", "Assigned Role", "Before", "After")
        )
        input.auditLogs.forEach { a ->
            s14 += listOf(
                formatTimestamp(a.timestampMs), a.registerId?.take(8) ?: "Project",
                a.revisionNumber?.let { "R$it" } ?: "—", a.action, a.userName.ifBlank { a.userId.take(8) },
                a.organizationType, a.assignedRole, a.beforeSummary, a.afterSummary
            )
        }

        val allTables = listOf(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14)
        return all14SheetNames.zip(allTables)
    }

    private fun buildCategoryRegisterSheet(regs: List<SiteRegisterRecord>): List<List<String>> {
        val rows = mutableListOf(
            listOf("Register", "Activity", "Date", "Shift", "From CH", "To CH", "Side", "Lane", "Length (m)", "Area (m²)", "Volume (m³)", "Material Qty", "Unit", "Classification", "Status", "Remarks")
        )
        regs.forEach { r ->
            rows += listOf(
                r.registerNumber, r.activityName, r.workDate, r.shift, r.chainageFrom, r.chainageTo,
                r.side, r.lane, fmt(r.totalLengthM), fmt(r.totalAreaSqm), fmt(r.totalVolumeCum),
                r.totalMaterialQty?.let { fmt(it) } ?: "—", r.totalMaterialUnit.symbol,
                r.quantityClassification.badgeLabel, r.status.displayLabel, r.remarks
            )
        }
        return rows
    }

    // =========================================================================
    // 2. PRINTABLE PDF GENERATOR (LANDSCAPE WIDE REGISTERS + PORTRAIT TESTS)
    // =========================================================================

    fun exportDailyPdfReport(
        context: Context,
        input: ExportBundleInput
    ): ExportResult {
        val outFile = createUniqueExportFile(
            context = context,
            prefix = "CTECS_DailyReport_${sanitizeFilePart(input.project.projectName)}_${input.reportDate}",
            extension = "pdf"
        )

        val generatedAt = formatTimestamp(System.currentTimeMillis())
        var pageNumber = 0

        val nativeSucceeded = try {
            val pdf = PdfDocument()

            val titlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val subtitlePaint = Paint().apply {
                color = Color.rgb(71, 85, 105)
                textSize = 9.5f
                isAntiAlias = true
            }
            val headerBgPaint = Paint().apply { color = Color.rgb(30, 58, 138) }
            val headerTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val cellPaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 8.2f
                isAntiAlias = true
            }
            val linePaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 0.7f
            }
            val warnPaint = Paint().apply {
                color = Color.rgb(180, 83, 9)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }

            fun drawFooter(canvas: android.graphics.Canvas, width: Int, height: Int, pageNo: Int) {
                canvas.drawLine(32f, height - 34f, width - 32f, height - 34f, linePaint)
                canvas.drawText("CTECS Digital Highway Site Register • Generated: $generatedAt", 32f, height - 20f, subtitlePaint)
                canvas.drawText("Page $pageNo", width - 72f, height - 20f, subtitlePaint)
            }

            // Page 1+: Landscape Wide Site Register Summary (842 × 595 pt)
            run {
                val w = 842
                val h = 595
                pageNumber++
                var page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, pageNumber).create())
                var canvas = page.canvas
                var y = 36f

                canvas.drawText("CTECS DIGITAL SITE REGISTER – DAILY PROGRESS & MEASUREMENT REPORT", 32f, y, titlePaint)
                y += 16f
                canvas.drawText(
                    "Project: ${input.project.projectName} | Highway: ${input.project.highwayName} (${input.project.nhShNumber}) | Package: ${input.project.packageNumber} | Date: ${input.reportDate}",
                    32f, y, subtitlePaint
                )
                y += 13f
                canvas.drawText(
                    "Contractor: ${input.project.contractorName} | PMC: ${input.project.pmcOrganization} | IE: ${input.project.ieOrganization}",
                    32f, y, subtitlePaint
                )
                y += 14f
                if (input.pendingOfflineSyncCount > 0) {
                    canvas.drawText(
                        "NOTICE: ${input.pendingOfflineSyncCount} offline record(s) have not yet synchronized to Supabase.",
                        32f, y, warnPaint
                    )
                    y += 14f
                }

                // Table header
                val cols = listOf("Reg No.", "Activity", "Chainage", "Side/Lane", "Length(m)", "Area(m²)", "Vol(m³)", "Mat.Qty", "Class", "Status/Rev")
                val colX = listOf(32f, 108f, 250f, 345f, 412f, 476f, 540f, 602f, 666f, 728f)
                canvas.drawRect(32f, y, w - 32f, y + 18f, headerBgPaint)
                cols.forEachIndexed { i, title -> canvas.drawText(title, colX[i] + 3f, y + 12f, headerTextPaint) }
                y += 22f

                val filteredRegs = if (input.activityFilter.equals("ALL", true)) input.registers
                else input.registers.filter { it.activityCode.equals(input.activityFilter, true) }

                for (r in filteredRegs) {
                    if (y > h - 60f) {
                        drawFooter(canvas, w, h, pageNumber)
                        pdf.finishPage(page)
                        pageNumber++
                        page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, pageNumber).create())
                        canvas = page.canvas
                        y = 42f
                    }
                    val rowVals = listOf(
                        r.registerNumber.take(12),
                        r.activityName.take(24),
                        "${r.chainageFrom}-${r.chainageTo}".take(16),
                        "${r.side}/${r.lane}".take(10),
                        fmt(r.totalLengthM),
                        fmt(r.totalAreaSqm),
                        fmt(r.totalVolumeCum),
                        r.totalMaterialQty?.let { "${fmt(it)} ${r.totalMaterialUnit.symbol}" } ?: "—",
                        r.quantityClassification.badgeLabel,
                        "${r.status.displayLabel} (R${r.currentRevision})".take(18)
                    )
                    rowVals.forEachIndexed { i, v -> canvas.drawText(v, colX[i] + 3f, y + 10f, cellPaint) }
                    canvas.drawLine(32f, y + 15f, w - 32f, y + 15f, linePaint)
                    y += 18f
                }

                y += 10f
                if (y < h - 80f) {
                    canvas.drawText(
                        "Open Observations: ${input.observations.count { it.status.dbValue != "CLOSED" }} | Tack Coat Tests: ${input.tackCoatTests.size} | Quality Reports: ${input.qualityTests.size} | Audit Events: ${input.auditLogs.size}",
                        32f, y, subtitlePaint
                    )
                }
                drawFooter(canvas, w, h, pageNumber)
                pdf.finishPage(page)
            }

            // Portrait pages (595 × 842 pt) for Individual Tack Coat Spray Test Reports
            input.tackCoatTests.forEach { test ->
                val w = 595
                val h = 842
                pageNumber++
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, pageNumber).create())
                val canvas = page.canvas
                var y = 40f
                val summary = HighwayMeasurementEngine.calculateTackCoatSprayTest(test.trays, test.specMinRateKgSqm, test.specMaxRateKgSqm)

                canvas.drawText("TACK COAT SPRAY RATE TEST REPORT", 32f, y, titlePaint)
                y += 18f
                canvas.drawText("Project: ${test.projectName.ifBlank { input.project.projectName }}", 32f, y, subtitlePaint); y += 14f
                canvas.drawText("Contractor: ${test.contractorName.ifBlank { input.project.contractorName }} | PMC: ${test.pmcConsultant.ifBlank { input.project.pmcOrganization }}", 32f, y, subtitlePaint); y += 14f
                canvas.drawText("Date: ${test.testDate} | Chainage: ${test.chainage} (${test.side}) | Material: ${test.materialType} | Temp: ${test.temperatureC ?: "—"} °C", 32f, y, subtitlePaint); y += 20f

                val headers = listOf("Tray#", "Area(m²)", "Before(kg)", "After(kg)", "Bitumen(kg)", "Rate(kg/m²)", "Status")
                val xs = listOf(32f, 82f, 155f, 232f, 308f, 392f, 482f)
                canvas.drawRect(32f, y, w - 32f, y + 18f, headerBgPaint)
                headers.forEachIndexed { i, title -> canvas.drawText(title, xs[i] + 3f, y + 12f, headerTextPaint) }
                y += 22f

                test.trays.zip(summary.trayResults).forEach { (row, res) ->
                    val vals = listOf(
                        row.trayNumber.toString(),
                        fmt(row.trayAreaSqm),
                        fmt(row.weightBeforeKg),
                        fmt(row.weightAfterKg),
                        res.bitumenWeightKg?.let { fmt(it) } ?: "ERR",
                        res.sprayRateKgPerSqm?.let { String.format(Locale.US, "%.4f", it) } ?: "ERR",
                        res.status.label
                    )
                    vals.forEachIndexed { i, v -> canvas.drawText(v, xs[i] + 3f, y + 10f, cellPaint) }
                    canvas.drawLine(32f, y + 15f, w - 32f, y + 15f, linePaint)
                    y += 18f
                }

                y += 14f
                canvas.drawText("Summary: ${summary.formulaSummary}", 32f, y, titlePaint.apply { textSize = 10.5f }); y += 16f
                canvas.drawText("Overall Evaluation: ${summary.overallStatus.label} (Tested By: ${test.testedBy})", 32f, y, subtitlePaint)
                titlePaint.textSize = 14f

                drawFooter(canvas, w, h, pageNumber)
                pdf.finishPage(page)
            }

            FileOutputStream(outFile).use { fos -> pdf.writeTo(fos) }
            pdf.close()
            true
        } catch (_: Throwable) {
            false
        }

        if (!nativeSucceeded) {
            pageNumber = writeFallbackStandardPdf(outFile, input, generatedAt)
        }

        val warn = if (input.pendingOfflineSyncCount > 0) {
            "Warning: ${input.pendingOfflineSyncCount} offline record(s) have not yet synchronized to Supabase."
        } else null

        return ExportResult(
            file = outFile,
            fileName = outFile.name,
            mimeType = "application/pdf",
            sheetOrPageCount = pageNumber,
            pendingSyncWarning = warn
        )
    }

    /**
     * Standards-compliant pure-Kotlin PDF-1.4 fallback generator (Landscape 842x595 register pages
     * + Portrait 595x842 test report pages) used whenever native Android PdfDocument is unavailable.
     */
    private fun writeFallbackStandardPdf(
        outFile: File,
        input: ExportBundleInput,
        generatedAt: String
    ): Int {
        val pages = mutableListOf<Triple<Int, Int, List<String>>>()
        val regLines = mutableListOf(
            "CTECS DIGITAL SITE REGISTER - DAILY PROGRESS & MEASUREMENT REPORT",
            "Project: ${input.project.projectName} | Highway: ${input.project.highwayName} | Date: ${input.reportDate}",
            "Contractor: ${input.project.contractorName} | PMC: ${input.project.pmcOrganization} | IE: ${input.project.ieOrganization}"
        )
        if (input.pendingOfflineSyncCount > 0) {
            regLines += "WARNING: ${input.pendingOfflineSyncCount} offline record(s) have not yet synchronized."
        }
        input.registers.forEach { r ->
            regLines += "${r.registerNumber} | ${r.activityName} | ${r.chainageFrom}-${r.chainageTo} | L=${fmt(r.totalLengthM)}m A=${fmt(r.totalAreaSqm)}m2 V=${fmt(r.totalVolumeCum)}m3 | ${r.status.displayLabel} R${r.currentRevision}"
        }
        regLines += "Generated: $generatedAt | Page 1"
        pages += Triple(842, 595, regLines)

        input.tackCoatTests.forEachIndexed { idx, t ->
            val sum = HighwayMeasurementEngine.calculateTackCoatSprayTest(t.trays, t.specMinRateKgSqm, t.specMaxRateKgSqm)
            val lines = mutableListOf(
                "TACK COAT SPRAY RATE TEST REPORT",
                "Project: ${t.projectName.ifBlank { input.project.projectName }} | Date: ${t.testDate} | CH: ${t.chainage}",
                "Summary: ${sum.formulaSummary} | Overall: ${sum.overallStatus.label}",
                "Generated: $generatedAt | Page ${idx + 2}"
            )
            pages += Triple(595, 842, lines)
        }

        val objects = mutableListOf<String>()
        objects += "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
        val pageObjIds = pages.indices.map { 4 + it * 2 }
        objects += "2 0 obj << /Type /Pages /Kids [${pageObjIds.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >> endobj\n"
        objects += "3 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"

        pages.forEachIndexed { idx, (w, h, lines) ->
            val pageId = 4 + idx * 2
            val contentId = pageId + 1
            val stream = buildString {
                append("BT /F1 10 Tf 36 ${h - 40} Td 14 TL ")
                lines.forEach { line ->
                    val safe = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                    append("($safe) Tj T* ")
                }
                append("ET")
            }
            objects += "$pageId 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] /Resources << /Font << /F1 3 0 R >> >> /Contents $contentId 0 R >> endobj\n"
            objects += "$contentId 0 obj << /Length ${stream.toByteArray(Charsets.US_ASCII).size} >> stream\n$stream\nendstream endobj\n"
        }

        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEach { obj ->
            offsets += sb.length
            sb.append(obj)
        }
        val xrefOffset = sb.length
        sb.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { off ->
            sb.append(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        sb.append("trailer << /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        outFile.writeBytes(sb.toString().toByteArray(Charsets.US_ASCII))
        return pages.size
    }

    fun getShareableUri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Never silently overwrites an existing exported report — appends timestamp and sequence suffix.
     */
    fun createUniqueExportFile(context: Context, prefix: String, extension: String): File {
        val dir = File(context.cacheDir, "site_register_exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var candidate = File(dir, "${prefix}_$stamp.$extension")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${prefix}_${stamp}_$counter.$extension")
            counter++
        }
        return candidate
    }

    private fun sanitizeFilePart(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9_-]"), "_").take(24).ifBlank { "Project" }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.3f", v)

    private fun formatTimestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

    // =========================================================================
    // OOXML PACKAGING HELPERS
    // =========================================================================

    private fun writeZip(zos: ZipOutputStream, entryName: String, content: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeXml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun colLetters(colIndexZeroBased: Int): String {
        var n = colIndexZeroBased + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun buildWorksheetXml(rows: List<List<String>>): String {
        val maxCols = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val lastCol = colLetters(maxCols - 1)
        val lastRow = rows.size.coerceAtLeast(1)
        val headerRowIdx = if (rows.size >= 2 && rows.first().size < rows[1].size) 2 else 1

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        // Freeze header row for smooth field scrolling in Excel
        sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="$headerRowIdx" topLeftCell="A${headerRowIdx + 1}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        // Readable auto-sized column widths
        sb.append("<cols>")
        for (c in 0 until maxCols) {
            val maxLen = rows.maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 12
            val width = (maxLen + 4).coerceIn(12, 38)
            sb.append("""<col min="${c + 1}" max="${c + 1}" width="$width" customWidth="1"/>""")
        }
        sb.append("</cols><sheetData>")
        rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            val isHeader = rowNum <= headerRowIdx
            val styleAttr = if (isHeader) """ s="1"""" else ""
            sb.append("""<row r="$rowNum">""")
            row.forEachIndexed { cIdx, value ->
                val cellRef = "${colLetters(cIdx)}$rowNum"
                val numVal = if (!isHeader) value.toDoubleOrNull() else null
                if (numVal != null && numVal.isFinite()) {
                    sb.append("""<c r="$cellRef"$styleAttr><v>$numVal</v></c>""")
                } else {
                    sb.append("""<c r="$cellRef" t="inlineStr"$styleAttr><is><t>${escapeXml(value)}</t></is></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        sb.append("""<autoFilter ref="A$headerRowIdx:$lastCol$lastRow"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun buildContentTypesXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun buildWorkbookXml(sheetNames: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheetNames.forEachIndexed { idx, name ->
            val safe = escapeXml(name.take(31))
            append("""<sheet name="$safe" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val ROOT_RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val STYLES_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="10"/><name val="Calibri"/></font><font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF1E3A8A"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs></styleSheet>"""
}
