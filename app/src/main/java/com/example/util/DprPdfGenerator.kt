package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.HighwayProjectEntity
import com.example.data.SmartActivityEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class SubActivityPdfItem(
    val subName: String,
    val location: String = "",
    val fromCh: String,
    val toCh: String,
    val lengthM: Double,
    val widthM: Double,
    val depthM: Double,
    val comments: String
) {
    val quantityM3: Double
        get() = if (depthM > 0 && widthM > 0 && lengthM > 0) lengthM * widthM * (depthM / 1000.0) else 0.0
}

/**
 * Professional DPR PDF Generation Engine for CTECS Highway Construction.
 * Produces publication-grade Landscape A4 engineering reports with clean white background,
 * subtle table borders, restrained styling, horizontally centred daily headers, dynamic multi-line
 * wrapping, proportional logo and signature scaling, and robust pagination.
 */
object DprPdfGenerator {

    /**
     * Extracts sub-activities from a SmartActivityEntity.
     */
    fun extractSubActivities(act: SmartActivityEntity): List<SubActivityPdfItem> {
        val jsonStr = act.subComments
        if (jsonStr.startsWith("[")) {
            try {
                val array = org.json.JSONArray(jsonStr)
                val result = mutableListOf<SubActivityPdfItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val sName = obj.optString("name", "")
                    if (sName.isNotBlank() && !sName.equals("None", true)) {
                        val parsedDepth = if (obj.has("depthM")) {
                            obj.optDouble("depthM", 0.0)
                        } else if (obj.has("depth")) {
                            obj.optDouble("depth", 0.0)
                        } else if (obj.has("depthMm")) {
                            obj.optDouble("depthMm", 0.0)
                        } else {
                            act.subDepthM
                        }

                        result.add(
                            SubActivityPdfItem(
                                subName = sName,
                                location = obj.optString("location", ""),
                                fromCh = obj.optString("fromChainage", act.fromChainage),
                                toCh = obj.optString("toChainage", act.toChainage),
                                lengthM = obj.optDouble("lengthM", 0.0),
                                widthM = obj.optDouble("widthM", 0.0),
                                depthM = parsedDepth,
                                comments = obj.optString("comments", "")
                            )
                        )
                    }
                }
                if (result.isNotEmpty()) return result
            } catch (e: Exception) {
                // Fallback to legacy single sub-activity fields
            }
        }

        if (act.subActivityName.isNotBlank() && !act.subActivityName.equals("None", true)) {
            return listOf(
                SubActivityPdfItem(
                    subName = act.subActivityName,
                    location = "",
                    fromCh = act.subFromChainage.ifBlank { act.fromChainage },
                    toCh = act.subToChainage.ifBlank { act.toChainage },
                    lengthM = act.subLengthM,
                    widthM = act.subWidthM,
                    depthM = act.subDepthM,
                    comments = if (!act.subComments.startsWith("[")) act.subComments else ""
                )
            )
        }

        return emptyList()
    }

    /**
     * Formats raw YYYY-MM-DD date into readable format e.g. "06 November 2026".
     */
    fun formatDisplayDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.US)
            formatter.format(parser.parse(dateStr) ?: Date())
        } catch (e: Exception) {
            dateStr
        }
    }

    /**
     * Formats raw YYYY-MM-DD date into short format e.g. "06 Nov 2026".
     */
    private fun formatShortDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.US)
            formatter.format(parser.parse(dateStr) ?: Date())
        } catch (e: Exception) {
            dateStr
        }
    }

    /**
     * Generates a Daily Project Report (DPR) with clean engineering tables.
     */
    fun generateDprPdf(
        context: Context,
        project: HighwayProjectEntity,
        dateString: String,
        activities: List<SmartActivityEntity>,
        summary: String = "",
        remarks: String = "",
        preparedBy: String = "",
        customLogoBitmap: Bitmap? = null,
        signatureBitmap: Bitmap? = null,
        includeLogo: Boolean = true,
        includeSignature: Boolean = true,
        exportScope: String = "MAIN_WITH_SUB"
    ): File {
        val displayDate = formatDisplayDate(dateString)

        return generateReportPdfInternal(
            context = context,
            project = project,
            isRangeReport = false,
            reportTitle = "SITE ACTIVITY – DPR",
            periodSubTitle = displayDate,
            activities = activities,
            summary = summary,
            remarks = remarks,
            preparedBy = preparedBy,
            customLogoBitmap = if (includeLogo) customLogoBitmap else null,
            signatureBitmap = if (includeSignature) signatureBitmap else null,
            fileNamePrefix = "DPR_${project.id.replace(Regex("[^a-zA-Z0-9]"), "")}_${dateString.replace("-", "")}",
            exportScope = exportScope
        )
    }

    /**
     * Generates a Weekly / Custom Period Site Activity Report PDF with distinct daily sections.
     */
    fun generateRangeDprPdf(
        context: Context,
        project: HighwayProjectEntity,
        startDateString: String,
        endDateString: String,
        reportTitle: String = "WEEKLY SITE ACTIVITY – DPR",
        activities: List<SmartActivityEntity>,
        summary: String = "",
        remarks: String = "",
        preparedBy: String = "",
        customLogoBitmap: Bitmap? = null,
        signatureBitmap: Bitmap? = null,
        includeLogo: Boolean = true,
        includeSignature: Boolean = true,
        exportScope: String = "MAIN_WITH_SUB"
    ): File {
        val d1 = formatShortDate(startDateString)
        val d2 = formatShortDate(endDateString)
        val periodText = "$d1 – $d2"

        return generateReportPdfInternal(
            context = context,
            project = project,
            isRangeReport = true,
            reportTitle = reportTitle,
            periodSubTitle = periodText,
            activities = activities,
            summary = summary,
            remarks = remarks,
            preparedBy = preparedBy,
            customLogoBitmap = if (includeLogo) customLogoBitmap else null,
            signatureBitmap = if (includeSignature) signatureBitmap else null,
            fileNamePrefix = "REPORT_${project.id.replace(Regex("[^a-zA-Z0-9]"), "")}_RANGE",
            exportScope = exportScope
        )
    }

    /**
     * Master rendering engine implementing Landscape A4 engineering tables,
     * chronological date grouping, dynamic line wrapping, independent calculations,
     * and zero text overlap.
     */
    private fun generateReportPdfInternal(
        context: Context,
        project: HighwayProjectEntity,
        isRangeReport: Boolean,
        reportTitle: String,
        periodSubTitle: String,
        activities: List<SmartActivityEntity>,
        summary: String,
        remarks: String,
        preparedBy: String,
        customLogoBitmap: Bitmap?,
        signatureBitmap: Bitmap?,
        fileNamePrefix: String,
        exportScope: String = "MAIN_WITH_SUB"
    ): File {
        val pdfDocument = PdfDocument()

        // Landscape A4 Page dimensions (Standard for wide highway engineering DPRs)
        val pageWidth = 842
        val pageHeight = 595

        val paint = Paint().apply { isAntiAlias = true }

        val primaryColor = Color.rgb(20, 83, 45)       // Deep Forest Green
        val tableHeaderBg = Color.rgb(22, 101, 52)     // Table Header Green
        val altRowBg = Color.rgb(248, 250, 252)        // Soft Slate Gray Row
        val tableBorderColor = Color.rgb(203, 213, 225)// Slate 300
        val textColor = Color.rgb(15, 23, 42)          // Dark Charcoal Text
        val subTextColor = Color.rgb(100, 116, 139)    // Muted Slate Text
        val chainageBlue = Color.rgb(30, 58, 138)      // Engineering Blue

        val leftMargin = 28f
        val rightMargin = 814f
        val tableWidth = rightMargin - leftMargin      // 786 points
        val maxPageY = 535f
        val footerLineY = 556f

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        var y = 20f

        // Table column widths definition
        // Table A: MAIN ACTIVITIES (Total = 786)
        // 0: S.No (30), 1: From CH (64), 2: To CH (64), 3: Activity (65), 4: Len (50),
        // 5: Wid (46), 6: Depth (48), 7: Qty (60), 8: Side (38), 9: Location (55), 10: Comments (266)
        val mainColWidths = floatArrayOf(30f, 64f, 64f, 65f, 50f, 46f, 48f, 60f, 38f, 55f, 266f)
        val mainColHeaders = arrayOf(
            "S.No", "From CH", "To CH", "Activity", "Length (m)", "Width (m)",
            "Depth (mm)", "Quantity (m³)", "Side", "Location", "Comments / Remarks"
        )

        // Table B: SUB ACTIVITIES (Total = 786)
        // 0: S.No (30), 1: Sub Activity (110), 2: From CH (64), 3: To CH (64), 4: Len (50),
        // 5: Wid (46), 6: Depth (48), 7: Qty (60), 8: Location (58), 9: Sub Comments (256)
        val subColWidths = floatArrayOf(30f, 110f, 64f, 64f, 50f, 46f, 48f, 60f, 58f, 256f)
        val subColHeaders = arrayOf(
            "S.No", "Sub Activity", "From CH", "To CH", "Length (m)", "Width (m)",
            "Depth (mm)", "Quantity (m³)", "Location", "Sub Comments / Remarks"
        )

        fun drawFooter(curPageNumber: Int) {
            paint.color = tableBorderColor
            paint.strokeWidth = 0.8f
            canvas.drawLine(leftMargin, footerLineY, rightMargin, footerLineY, paint)

            paint.color = subTextColor
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())
            canvas.drawText("CTECS Highway Engineering DPR • Generated on $dateStr", leftMargin + 4f, footerLineY + 13f, paint)

            val pageStr = "Page $curPageNumber"
            val pageW = paint.measureText(pageStr)
            canvas.drawText(pageStr, rightMargin - pageW - 4f, footerLineY + 13f, paint)
        }

        val prjName = if (project.name.isNotBlank()) project.name else project.id
        val stretch = if (project.startChainage.isNotBlank() && project.endChainage.isNotBlank()) {
            "Ch. ${project.startChainage} to ${project.endChainage}"
        } else "Full Alignment Section"
        val scopeText = when (exportScope) {
            "MAIN_ONLY" -> "Main Activities Only"
            "SUB_ONLY" -> "Sub Activities Only"
            else -> "Main & Sub Activities"
        }

        fun drawTopBanner(isFirstPage: Boolean, activeDateLabel: String = "") {
            if (isFirstPage) {
                y = 16f

                // Top decorative accent bar
                paint.color = primaryColor
                canvas.drawRect(leftMargin, y, rightMargin, y + 3.5f, paint)
                y += 10f

                // Custom Logo at Top Right (Proportional aspect ratio, zero distortion, zero clipping)
                var logoDrawnW = 0f
                if (customLogoBitmap != null) {
                    try {
                        val maxW = 85f
                        val maxH = 30f
                        val aspect = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
                        val targetW = if (aspect >= (maxW / maxH)) maxW else maxH * aspect
                        val targetH = if (aspect >= (maxW / maxH)) maxW / aspect else maxH
                        val destRect = RectF(rightMargin - targetW, y, rightMargin, y + targetH)
                        canvas.drawBitmap(customLogoBitmap, null, destRect, paint)
                        logoDrawnW = targetW + 12f
                    } catch (e: Exception) {
                        // Ignore logo drawing error
                    }
                }

                // Main Report Title (Compact, authoritative header)
                paint.color = primaryColor
                paint.textSize = 13.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(reportTitle, leftMargin, y + 12f, paint)

                // Subtitle / Period line
                paint.color = subTextColor
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val periodLine = if (isRangeReport) "Reporting Period: $periodSubTitle" else "Report Date: $periodSubTitle"
                canvas.drawText(periodLine, leftMargin, y + 24f, paint)

                y += 32f

                // Minimal Executive Metadata Strip (Page 1 Only - Zero Redundancy, Compact)
                val cardH = 22f
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRoundRect(leftMargin, y, rightMargin, y + cardH, 4f, 4f, paint)
                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRoundRect(leftMargin, y, rightMargin, y + cardH, 4f, 4f, paint)
                paint.style = Paint.Style.FILL

                // Single balanced line: Project, Stretch, Scope, Contractor / Client
                paint.color = textColor
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val metaItems = mutableListOf<String>()
                metaItems.add("Project: $prjName")
                metaItems.add("Section: $stretch")
                metaItems.add("Scope: $scopeText")
                if (project.contractorName.isNotBlank()) metaItems.add("Contractor: ${project.contractorName}")
                if (project.clientName.isNotBlank()) metaItems.add("Client: ${project.clientName}")
                if (preparedBy.isNotBlank()) metaItems.add("Prepared By: $preparedBy")

                val metaText = metaItems.joinToString("   |   ")
                canvas.drawText(metaText, leftMargin + 8f, y + 14f, paint)

                y += cardH + 12f
            } else {
                // Subsequent pages (Page 2+) - Minimal, professional running header
                y = 16f
                paint.color = subTextColor
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                val runningTitle = if (activeDateLabel.isNotBlank()) {
                    "$reportTitle • $prjName • $activeDateLabel"
                } else {
                    "$reportTitle • $prjName • $periodSubTitle"
                }
                canvas.drawText(runningTitle, leftMargin, y + 8f, paint)

                val pageStr = "Page $pageNumber"
                val pageW = paint.measureText(pageStr)
                canvas.drawText(pageStr, rightMargin - pageW, y + 8f, paint)

                // Hairline rule below running header
                paint.color = tableBorderColor
                paint.strokeWidth = 0.5f
                canvas.drawLine(leftMargin, y + 13f, rightMargin, y + 13f, paint)

                y += 20f
            }
        }

        fun drawMainTableHeader() {
            val hHeight = 18f
            paint.color = tableHeaderBg
            canvas.drawRect(leftMargin, y, rightMargin, y + hHeight, paint)

            paint.color = Color.WHITE
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            var curX = leftMargin
            for (i in mainColHeaders.indices) {
                val colW = mainColWidths[i]
                val headerText = mainColHeaders[i]
                // Center S.No and Side headers, right align numerical headers, left align others
                when (i) {
                    0, 8 -> {
                        val tw = paint.measureText(headerText)
                        canvas.drawText(headerText, curX + (colW - tw) / 2f, y + 12f, paint)
                    }
                    4, 5, 6, 7 -> {
                        val tw = paint.measureText(headerText)
                        canvas.drawText(headerText, curX + colW - tw - 4f, y + 12f, paint)
                    }
                    else -> {
                        canvas.drawText(headerText, curX + 4f, y + 12f, paint)
                    }
                }
                curX += colW
            }

            paint.color = tableBorderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            canvas.drawRect(leftMargin, y, rightMargin, y + hHeight, paint)
            paint.style = Paint.Style.FILL

            y += hHeight
        }

        fun drawSubTableHeader() {
            val hHeight = 18f
            paint.color = tableHeaderBg
            canvas.drawRect(leftMargin, y, rightMargin, y + hHeight, paint)

            paint.color = Color.WHITE
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            var curX = leftMargin
            for (i in subColHeaders.indices) {
                val colW = subColWidths[i]
                val headerText = subColHeaders[i]
                when (i) {
                    0 -> {
                        val tw = paint.measureText(headerText)
                        canvas.drawText(headerText, curX + (colW - tw) / 2f, y + 12f, paint)
                    }
                    4, 5, 6, 7 -> {
                        val tw = paint.measureText(headerText)
                        canvas.drawText(headerText, curX + colW - tw - 4f, y + 12f, paint)
                    }
                    else -> {
                        canvas.drawText(headerText, curX + 4f, y + 12f, paint)
                    }
                }
                curX += colW
            }

            paint.color = tableBorderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            canvas.drawRect(leftMargin, y, rightMargin, y + hHeight, paint)
            paint.style = Paint.Style.FILL

            y += hHeight
        }

        // Initialize First Page
        drawTopBanner(isFirstPage = true)

        // Group records chronologically by date
        val activitiesByDate = if (activities.isEmpty()) {
            emptyMap()
        } else if (isRangeReport) {
            activities.groupBy { it.dateString.ifBlank { "Unspecified" } }.toSortedMap()
        } else {
            val singleKey = activities.firstOrNull()?.dateString?.ifBlank { "Today" } ?: "Today"
            mapOf(singleKey to activities)
        }

        // Overall Period Accumulators
        var overallPeriodMainLen = 0.0
        var overallPeriodMainVol = 0.0
        var overallPeriodSubLen = 0.0
        var overallPeriodSubVol = 0.0

        if (activitiesByDate.isEmpty()) {
            val emptyBoxH = 40f
            paint.color = Color.rgb(248, 250, 252)
            canvas.drawRoundRect(leftMargin, y, rightMargin, y + emptyBoxH, 4f, 4f, paint)
            paint.color = subTextColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("No site activity records logged for this selected period.", leftMargin + 16f, y + 24f, paint)
            y += emptyBoxH + 20f
        } else {
            var dayIndex = 0
            for ((dateKey, dayActivities) in activitiesByDate) {
                dayIndex++
                val displayDayDate = formatDisplayDate(dateKey)
                val centeredDateUpper = displayDayDate.uppercase(Locale.US)

                // Distinct Horizontally Centred Heading for every day (Daily, Weekly, and Range exports)
                val dateBannerH = 22f
                if (y + dateBannerH + 50f > maxPageY) {
                    drawFooter(pageNumber)
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawTopBanner(isFirstPage = false, activeDateLabel = displayDayDate)
                }

                // Clean light pill container
                paint.color = Color.rgb(241, 245, 249)
                canvas.drawRoundRect(leftMargin, y, rightMargin, y + dateBannerH, 4f, 4f, paint)
                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRoundRect(leftMargin, y, rightMargin, y + dateBannerH, 4f, 4f, paint)
                paint.style = Paint.Style.FILL

                // Centred Title Calculation
                paint.color = primaryColor
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                val bannerText = if (isRangeReport) {
                    "DAILY SITE ACTIVITIES – $centeredDateUpper"
                } else {
                    "SITE ACTIVITY – DPR  •  $centeredDateUpper"
                }
                val bannerTextW = paint.measureText(bannerText)
                val centerStartX = leftMargin + (tableWidth - bannerTextW) / 2f
                canvas.drawText(bannerText, centerStartX, y + 14.5f, paint)

                // Right-aligned activities count badge
                paint.color = subTextColor
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val cntText = "${dayActivities.size} Activities"
                val cntW = paint.measureText(cntText)
                canvas.drawText(cntText, rightMargin - cntW - 8f, y + 14.5f, paint)

                y += dateBannerH + 10f

                // Accumulators for this specific day
                var dailyMainLen = 0.0
                var dailyMainVol = 0.0
                var dailySubLen = 0.0
                var dailySubVol = 0.0

                // -------------------------------------------------------------
                // A. MAIN SITE ACTIVITIES TABLE
                // -------------------------------------------------------------
                if (exportScope != "SUB_ONLY") {
                    paint.color = primaryColor
                    paint.textSize = 9f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("A. MAIN SITE ACTIVITIES", leftMargin, y + 2f, paint)
                    y += 8f

                    drawMainTableHeader()

                    dayActivities.forEachIndexed { aIdx, act ->
                        val len = act.completedLengthM.takeIf { it > 0 } ?: act.quantity
                        val wid = act.widthM
                        val depthMm = if (act.depthM > 0) act.depthM else if (act.thicknessMm > 0) act.thicknessMm else 0.0
                        val volM3 = if (depthMm > 0 && wid > 0 && len > 0) len * wid * (depthMm / 1000.0) else 0.0

                        dailyMainLen += len
                        dailyMainVol += volM3

                        // Text wrapping for multi-line fields to prevent ANY truncation or clipping
                        paint.textSize = 7.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                        val actName = act.activityLayer.ifBlank { "BC" }
                        val actLines = wrapText(actName, paint, mainColWidths[3] - 8f)

                        val locStr = act.roadSection.ifBlank { "I/L" }
                        val locLines = wrapText(locStr, paint, mainColWidths[9] - 8f)

                        val rawComments = act.siteObservations.ifBlank { act.remarks }.trim()
                        val commentLines = wrapText(rawComments, paint, mainColWidths[10] - 8f)

                        val maxLines = maxOf(actLines.size, locLines.size, commentLines.size, 1)
                        val rowH = max(18f, (maxLines * 9.5f) + 7f)

                        // Check page boundary and repeat column headers on continuation page
                        if (y + rowH > maxPageY) {
                            drawFooter(pageNumber)
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            drawTopBanner(isFirstPage = false, activeDateLabel = "$displayDayDate (Cont.)")
                            drawMainTableHeader()
                        }

                        // Row background
                        paint.color = if (aIdx % 2 == 0) Color.WHITE else altRowBg
                        canvas.drawRect(leftMargin, y, rightMargin, y + rowH, paint)

                        // Row cell borders & grid lines
                        paint.color = tableBorderColor
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 0.5f
                        canvas.drawRect(leftMargin, y, rightMargin, y + rowH, paint)

                        var cellX = leftMargin
                        for (w in mainColWidths) {
                            cellX += w
                            canvas.drawLine(cellX, y, cellX, y + rowH, paint)
                        }
                        paint.style = Paint.Style.FILL

                        // Draw Cell Contents
                        paint.color = textColor
                        paint.textSize = 7.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                        val textY = y + 11.5f
                        var drawX = leftMargin

                        // 0. S.No (Centered)
                        val sNoStr = "${aIdx + 1}"
                        val sNoW = paint.measureText(sNoStr)
                        canvas.drawText(sNoStr, drawX + (mainColWidths[0] - sNoW) / 2f, textY, paint)
                        drawX += mainColWidths[0]

                        // 1. From CH (Bold Blue)
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        paint.color = chainageBlue
                        canvas.drawText(act.fromChainage.ifBlank { "-" }, drawX + 4f, textY, paint)
                        drawX += mainColWidths[1]

                        // 2. To CH
                        canvas.drawText(act.toChainage.ifBlank { "-" }, drawX + 4f, textY, paint)
                        drawX += mainColWidths[2]

                        // 3. Activity (Multi-line wrapped, zero truncation)
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        paint.color = textColor
                        var aY = textY
                        actLines.forEach { line ->
                            canvas.drawText(line, drawX + 4f, aY, paint)
                            aY += 9.5f
                        }
                        drawX += mainColWidths[3]

                        // 4. Length (m) (Right aligned)
                        val lenStr = if (len > 0) "${len.toInt()}" else "-"
                        val lenW = paint.measureText(lenStr)
                        canvas.drawText(lenStr, drawX + mainColWidths[4] - lenW - 4f, textY, paint)
                        drawX += mainColWidths[4]

                        // 5. Width (m) (Right aligned)
                        val widStr = if (wid > 0) "%.1f".format(Locale.US, wid) else "-"
                        val widW = paint.measureText(widStr)
                        canvas.drawText(widStr, drawX + mainColWidths[5] - widW - 4f, textY, paint)
                        drawX += mainColWidths[5]

                        // 6. Depth (mm) (Right aligned)
                        val depthStr = if (depthMm > 0) {
                            if (depthMm % 1.0 == 0.0) "${depthMm.toInt()}" else "$depthMm"
                        } else "-"
                        val depthW = paint.measureText(depthStr)
                        canvas.drawText(depthStr, drawX + mainColWidths[6] - depthW - 4f, textY, paint)
                        drawX += mainColWidths[6]

                        // 7. Quantity (m³) (Right aligned)
                        val qtyStr = if (volM3 > 0) "%.2f".format(Locale.US, volM3) else "-"
                        val qtyW = paint.measureText(qtyStr)
                        canvas.drawText(qtyStr, drawX + mainColWidths[7] - qtyW - 4f, textY, paint)
                        drawX += mainColWidths[7]

                        // 8. Side (Centered)
                        val sideStr = act.side.ifBlank { "LHS" }
                        val sideW = paint.measureText(sideStr)
                        canvas.drawText(sideStr, drawX + (mainColWidths[8] - sideW) / 2f, textY, paint)
                        drawX += mainColWidths[8]

                        // 9. Location (Multi-line wrapped, zero truncation)
                        var lY = textY
                        locLines.forEach { line ->
                            canvas.drawText(line, drawX + 4f, lY, paint)
                            lY += 9.5f
                        }
                        drawX += mainColWidths[9]

                        // 10. Comments / Remarks (Multi-line wrapped, never clipped)
                        var cY = textY
                        commentLines.forEach { line ->
                            canvas.drawText(line, drawX + 4f, cY, paint)
                            cY += 9.5f
                        }

                        y += rowH
                    }

                    // Main Activities Total Row (Clean, separate columns, aligned totals)
                    val totH = 18f
                    if (y + totH > maxPageY) {
                        drawFooter(pageNumber)
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        drawTopBanner(isFirstPage = false, activeDateLabel = "$displayDayDate (Cont.)")
                    }

                    paint.color = Color.rgb(241, 245, 249)
                    canvas.drawRect(leftMargin, y, rightMargin, y + totH, paint)
                    paint.color = tableBorderColor
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.8f
                    canvas.drawRect(leftMargin, y, rightMargin, y + totH, paint)
                    paint.style = Paint.Style.FILL

                    paint.color = primaryColor
                    paint.textSize = 7.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                    // Spanning label across S.No to Activity
                    canvas.drawText("Main Activities Daily Total:", leftMargin + 6f, y + 12f, paint)

                    // Length Cell (Right aligned)
                    val lenColX = leftMargin + mainColWidths[0] + mainColWidths[1] + mainColWidths[2] + mainColWidths[3]
                    val totLenStr = "${dailyMainLen.toInt()} m"
                    val totLenW = paint.measureText(totLenStr)
                    canvas.drawText(totLenStr, lenColX + mainColWidths[4] - totLenW - 4f, y + 12f, paint)

                    // Quantity Cell (Right aligned)
                    val qtyColX = lenColX + mainColWidths[4] + mainColWidths[5] + mainColWidths[6]
                    val totVolStr = if (dailyMainVol > 0) "%.2f m³".format(Locale.US, dailyMainVol) else "-"
                    val totVolW = paint.measureText(totVolStr)
                    canvas.drawText(totVolStr, qtyColX + mainColWidths[7] - totVolW - 4f, y + 12f, paint)

                    y += totH + 10f
                }

                // -------------------------------------------------------------
                // B. SUB ACTIVITIES TABLE
                // -------------------------------------------------------------
                if (exportScope != "MAIN_ONLY") {
                    val daySubs = dayActivities.flatMap { extractSubActivities(it) }
                    if (daySubs.isNotEmpty()) {
                        if (y + 45f > maxPageY) {
                            drawFooter(pageNumber)
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            drawTopBanner(isFirstPage = false, activeDateLabel = "$displayDayDate (Cont.)")
                        }

                        paint.color = primaryColor
                        paint.textSize = 9f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText("B. SUB ACTIVITIES", leftMargin, y + 2f, paint)
                        y += 8f

                        drawSubTableHeader()

                        daySubs.forEachIndexed { sIdx, sub ->
                            dailySubLen += sub.lengthM
                            dailySubVol += sub.quantityM3

                            paint.textSize = 7.5f
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                            val subNameLines = wrapText(sub.subName, paint, subColWidths[1] - 8f)
                            val sLoc = sub.location.takeIf { it.isNotBlank() && !it.equals("None", true) } ?: "-"
                            val subLocLines = wrapText(sLoc, paint, subColWidths[8] - 8f)
                            val subCommentLines = wrapText(sub.comments, paint, subColWidths[9] - 8f)

                            val maxLines = maxOf(subNameLines.size, subLocLines.size, subCommentLines.size, 1)
                            val rowH = max(18f, (maxLines * 9.5f) + 7f)

                            if (y + rowH > maxPageY) {
                                drawFooter(pageNumber)
                                pdfDocument.finishPage(page)
                                pageNumber++
                                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                                page = pdfDocument.startPage(pageInfo)
                                canvas = page.canvas
                                drawTopBanner(isFirstPage = false, activeDateLabel = "$displayDayDate (Cont.)")
                                drawSubTableHeader()
                            }

                            paint.color = if (sIdx % 2 == 0) Color.WHITE else altRowBg
                            canvas.drawRect(leftMargin, y, rightMargin, y + rowH, paint)

                            paint.color = tableBorderColor
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 0.5f
                            canvas.drawRect(leftMargin, y, rightMargin, y + rowH, paint)

                            var cellX = leftMargin
                            for (w in subColWidths) {
                                cellX += w
                                canvas.drawLine(cellX, y, cellX, y + rowH, paint)
                            }
                            paint.style = Paint.Style.FILL

                            // Draw Sub Activity Cell Contents
                            paint.color = textColor
                            paint.textSize = 7.5f
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                            val textY = y + 11.5f
                            var drawX = leftMargin

                            // 0. S.No (Centered)
                            val sNoStr = "${sIdx + 1}"
                            val sNoW = paint.measureText(sNoStr)
                            canvas.drawText(sNoStr, drawX + (subColWidths[0] - sNoW) / 2f, textY, paint)
                            drawX += subColWidths[0]

                            // 1. Sub Activity (Wrapped, bold)
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            var sNameY = textY
                            subNameLines.forEach { line ->
                                canvas.drawText(line, drawX + 4f, sNameY, paint)
                                sNameY += 9.5f
                            }
                            drawX += subColWidths[1]

                            // 2. From CH (Bold Blue)
                            paint.color = chainageBlue
                            canvas.drawText(sub.fromCh.ifBlank { "-" }, drawX + 4f, textY, paint)
                            drawX += subColWidths[2]

                            // 3. To CH
                            canvas.drawText(sub.toCh.ifBlank { "-" }, drawX + 4f, textY, paint)
                            drawX += subColWidths[3]

                            // 4. Length (m) (Right aligned)
                            paint.color = textColor
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            val sLenStr = if (sub.lengthM > 0) "${sub.lengthM.toInt()}" else "-"
                            val sLenW = paint.measureText(sLenStr)
                            canvas.drawText(sLenStr, drawX + subColWidths[4] - sLenW - 4f, textY, paint)
                            drawX += subColWidths[4]

                            // 5. Width (m) (Right aligned)
                            val sWidStr = if (sub.widthM > 0) "%.1f".format(Locale.US, sub.widthM) else "-"
                            val sWidW = paint.measureText(sWidStr)
                            canvas.drawText(sWidStr, drawX + subColWidths[5] - sWidW - 4f, textY, paint)
                            drawX += subColWidths[5]

                            // 6. Depth (mm) (Right aligned)
                            val sDepthStr = if (sub.depthM > 0) {
                                if (sub.depthM % 1.0 == 0.0) "${sub.depthM.toInt()}" else "${sub.depthM}"
                            } else "-"
                            val sDepthW = paint.measureText(sDepthStr)
                            canvas.drawText(sDepthStr, drawX + subColWidths[6] - sDepthW - 4f, textY, paint)
                            drawX += subColWidths[6]

                            // 7. Quantity (m³) (Right aligned)
                            val sQtyStr = if (sub.quantityM3 > 0) "%.2f".format(Locale.US, sub.quantityM3) else "-"
                            val sQtyW = paint.measureText(sQtyStr)
                            canvas.drawText(sQtyStr, drawX + subColWidths[7] - sQtyW - 4f, textY, paint)
                            drawX += subColWidths[7]

                            // 8. Location (Multi-line wrapped)
                            var sLocY = textY
                            subLocLines.forEach { line ->
                                canvas.drawText(line, drawX + 4f, sLocY, paint)
                                sLocY += 9.5f
                            }
                            drawX += subColWidths[8]

                            // 9. Sub Comments (Multi-line wrapped)
                            var scY = textY
                            subCommentLines.forEach { line ->
                                canvas.drawText(line, drawX + 4f, scY, paint)
                                scY += 9.5f
                            }

                            y += rowH
                        }

                        // Sub Activities Total Row
                        val totH = 18f
                        if (y + totH > maxPageY) {
                            drawFooter(pageNumber)
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            drawTopBanner(isFirstPage = false, activeDateLabel = "$displayDayDate (Cont.)")
                        }

                        paint.color = Color.rgb(241, 245, 249)
                        canvas.drawRect(leftMargin, y, rightMargin, y + totH, paint)
                        paint.color = tableBorderColor
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 0.8f
                        canvas.drawRect(leftMargin, y, rightMargin, y + totH, paint)
                        paint.style = Paint.Style.FILL

                        paint.color = primaryColor
                        paint.textSize = 7.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                        canvas.drawText("Sub Activities Daily Total:", leftMargin + 6f, y + 12f, paint)

                        val subLenColX = leftMargin + subColWidths[0] + subColWidths[1] + subColWidths[2] + subColWidths[3]
                        val subTotLenStr = "${dailySubLen.toInt()} m"
                        val subTotLenW = paint.measureText(subTotLenStr)
                        canvas.drawText(subTotLenStr, subLenColX + subColWidths[4] - subTotLenW - 4f, y + 12f, paint)

                        val subQtyColX = subLenColX + subColWidths[4] + subColWidths[5] + subColWidths[6]
                        val subTotVolStr = if (dailySubVol > 0) "%.2f m³".format(Locale.US, dailySubVol) else "-"
                        val subTotVolW = paint.measureText(subTotVolStr)
                        canvas.drawText(subTotVolStr, subQtyColX + subColWidths[7] - subTotVolW - 4f, y + 12f, paint)

                        y += totH + 10f
                    }
                }

                // -------------------------------------------------------------
                // C. DAILY SUMMARY
                // -------------------------------------------------------------
                val dailyObsList = dayActivities.mapNotNull {
                    it.siteObservations.ifBlank { it.remarks }.trim().takeIf { s -> s.isNotBlank() && !s.equals("None", true) }
                }.distinct()

                val allDailyRemarks = mutableListOf<String>()
                if (remarks.isNotBlank() && !isRangeReport) {
                    allDailyRemarks.add(remarks.trim())
                }
                dailyObsList.forEach { obs ->
                    if (!allDailyRemarks.contains(obs)) {
                        allDailyRemarks.add(obs)
                    }
                }

                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val remarkWrappedLines = if (allDailyRemarks.isNotEmpty()) {
                    val combined = allDailyRemarks.joinToString(" • ")
                    wrapText("• Daily Remarks: $combined", paint, tableWidth - 18f)
                } else emptyList()

                val summaryContentH = 36f + (if (remarkWrappedLines.isNotEmpty()) (remarkWrappedLines.size * 9.5f) + 6f else 0f)

                if (y + summaryContentH > maxPageY) {
                    drawFooter(pageNumber)
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawTopBanner(isFirstPage = false, activeDateLabel = "$displayDayDate (Cont.)")
                }

                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRoundRect(leftMargin, y, rightMargin, y + summaryContentH, 4f, 4f, paint)
                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRoundRect(leftMargin, y, rightMargin, y + summaryContentH, 4f, 4f, paint)
                paint.style = Paint.Style.FILL

                paint.color = primaryColor
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("C. DAILY SUMMARY – $displayDayDate", leftMargin + 8f, y + 12f, paint)

                paint.color = textColor
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                val mainTotKm = dailyMainLen / 1000.0
                val mainVolDesc = if (dailyMainVol > 0) "${"%.2f".format(Locale.US, dailyMainVol)} m³" else "N/A (Linear/Surface)"
                val line1 = "• Total Main Activity Length: ${dailyMainLen.toInt()} m (${"%.2f".format(Locale.US, mainTotKm)} km)   |   Total Main Activity Quantity: $mainVolDesc"
                canvas.drawText(line1, leftMargin + 8f, y + 23f, paint)

                val subTotKm = dailySubLen / 1000.0
                val subVolDesc = if (dailySubVol > 0) "${"%.2f".format(Locale.US, dailySubVol)} m³" else "N/A"
                val line2 = "• Total Sub Activity Length: ${dailySubLen.toInt()} m (${"%.2f".format(Locale.US, subTotKm)} km)   |   Total Sub Activity Quantity: $subVolDesc"
                canvas.drawText(line2, leftMargin + 8f, y + 33f, paint)

                if (remarkWrappedLines.isNotEmpty()) {
                    var rY = y + 43f
                    remarkWrappedLines.forEach { rLine ->
                        canvas.drawText(rLine, leftMargin + 8f, rY, paint)
                        rY += 9.5f
                    }
                }

                y += summaryContentH + 12f

                // Accumulate overall period totals
                overallPeriodMainLen += dailyMainLen
                overallPeriodMainVol += dailyMainVol
                overallPeriodSubLen += dailySubLen
                overallPeriodSubVol += dailySubVol
            }
        }

        // -------------------------------------------------------------
        // D. OVERALL PERIOD SUMMARY (FOR MULTI-DAY REPORTS)
        // -------------------------------------------------------------
        if (isRangeReport && activitiesByDate.size > 1) {
            val periodBoxH = 46f
            if (y + periodBoxH > maxPageY) {
                drawFooter(pageNumber)
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                drawTopBanner(isFirstPage = false, activeDateLabel = "Period Summary")
            }

            paint.color = Color.rgb(238, 242, 255)
            canvas.drawRoundRect(leftMargin, y, rightMargin, y + periodBoxH, 4f, 4f, paint)
            paint.color = Color.rgb(199, 210, 254)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(leftMargin, y, rightMargin, y + periodBoxH, 4f, 4f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(49, 46, 129)
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("D. OVERALL PERIOD SUMMARY ($periodSubTitle)", leftMargin + 8f, y + 13f, paint)

            paint.color = textColor
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            val pMainKm = overallPeriodMainLen / 1000.0
            val pMainVolStr = if (overallPeriodMainVol > 0) "${"%.2f".format(Locale.US, overallPeriodMainVol)} m³" else "N/A"
            val pLine1 = "• Total Main Activities Length: ${overallPeriodMainLen.toInt()} m (${"%.2f".format(Locale.US, pMainKm)} km)   |   Total Volume: $pMainVolStr"
            canvas.drawText(pLine1, leftMargin + 8f, y + 26f, paint)

            val pSubKm = overallPeriodSubLen / 1000.0
            val pSubVolStr = if (overallPeriodSubVol > 0) "${"%.2f".format(Locale.US, overallPeriodSubVol)} m³" else "N/A"
            val pLine2 = "• Total Sub Activities Length: ${overallPeriodSubLen.toInt()} m (${"%.2f".format(Locale.US, pSubKm)} km)   |   Total Volume: $pSubVolStr   |   Active Days: ${activitiesByDate.size}"
            canvas.drawText(pLine2, leftMargin + 8f, y + 38f, paint)

            y += periodBoxH + 16f
        }

        // -------------------------------------------------------------
        // PREPARED BY & SIGNATURE SECTION
        // -------------------------------------------------------------
        val sigRequiredH = 52f
        if (y + sigRequiredH > maxPageY) {
            drawFooter(pageNumber)
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            drawTopBanner(isFirstPage = false)
        }

        val signLineY = y + 28f
        val signer = if (preparedBy.isNotBlank()) preparedBy else "Site Engineer"

        if (signatureBitmap != null) {
            try {
                val targetSigW = 110f
                val targetSigH = 28f
                val sigAspect = signatureBitmap.width.toFloat() / signatureBitmap.height.toFloat()
                val actualW = if (sigAspect >= (targetSigW / targetSigH)) targetSigW else targetSigH * sigAspect
                val actualH = if (sigAspect >= (targetSigW / targetSigH)) targetSigW / sigAspect else targetSigH
                val sigDest = RectF(leftMargin + 10f, signLineY - actualH - 2f, leftMargin + 10f + actualW, signLineY - 2f)
                canvas.drawBitmap(signatureBitmap, null, sigDest, paint)

                paint.color = tableBorderColor
                paint.strokeWidth = 0.8f
                canvas.drawLine(leftMargin + 10f, signLineY, leftMargin + 160f, signLineY, paint)
            } catch (e: Exception) {
                // Ignore signature drawing error
            }

            paint.color = textColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Prepared By: $signer", leftMargin + 10f, signLineY + 13f, paint)
        } else {
            // Clean display without fake lines or blank boxes when no signature is provided
            paint.color = primaryColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Prepared By: $signer", leftMargin + 10f, y + 16f, paint)

            paint.color = subTextColor
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Highway Site Engineer / Authorised Signatory", leftMargin + 10f, y + 28f, paint)
        }

        // Draw final page footer
        drawFooter(pageNumber)
        pdfDocument.finishPage(page)

        // Write output to Cache file
        val pdfFile = File(context.cacheDir, "${fileNamePrefix}_${System.currentTimeMillis() % 100000}.pdf")
        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return pdfFile
    }

    /**
     * Share DPR PDF via Android Share sheet.
     */
    fun shareDprPdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Highway Site Activity Report - ${pdfFile.nameWithoutExtension}")
            putExtra(Intent.EXTRA_TEXT, "Attached is the official Highway Site Activity Report (PDF): ${pdfFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Site Activity Report (PDF)"))
    }

    /**
     * Helper to load bitmap from a file URI or path safely.
     */
    fun loadBitmapFromUri(context: Context, uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content" || uri.scheme == "file") {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else {
                BitmapFactory.decodeFile(uriString)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Helper to wrap text into multiple lines based on maximum column width.
     */
    fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("-")
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        for (paragraph in paragraphs) {
            val trimmedPara = paragraph.trim()
            if (trimmedPara.isEmpty()) continue
            val words = trimmedPara.split(Regex("\\s+"))
            var currentLine = ""
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) <= maxWidth) {
                    currentLine = testLine
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                        currentLine = word
                    } else {
                        lines.add(word)
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
        }
        return if (lines.isEmpty()) listOf("-") else lines
    }
}
