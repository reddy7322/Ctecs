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
     * Formats raw YYYY-MM-DD date into readable format e.g. "05 November 2026".
     */
    private fun formatDisplayDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.US)
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
            reportTitle = "DAILY SITE ACTIVITY REPORT",
            periodSubTitle = "Date: $displayDate",
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
        reportTitle: String = "WEEKLY HIGHWAY SITE ACTIVITY REPORT",
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
        val d1 = formatDisplayDate(startDateString)
        val d2 = formatDisplayDate(endDateString)

        return generateReportPdfInternal(
            context = context,
            project = project,
            isRangeReport = true,
            reportTitle = reportTitle,
            periodSubTitle = "Period: $d1 to $d2",
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
        val maxPageY = 530f
        val footerLineY = 552f

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        var y = 22f

        // Table column widths definition
        // Table A: MAIN ACTIVITIES (Total = 786)
        // 0: S.No (32), 1: From CH (64), 2: To CH (64), 3: Activity (56), 4: Len (54),
        // 5: Wid (50), 6: Depth (54), 7: Qty (66), 8: Side (40), 9: Location (52), 10: Comments (254)
        val mainColWidths = floatArrayOf(32f, 64f, 64f, 56f, 54f, 50f, 54f, 66f, 40f, 52f, 254f)
        val mainColHeaders = arrayOf(
            "S.No", "From CH", "To CH", "Activity", "Length (m)", "Width (m)",
            "Depth (mm)", "Quantity (m³)", "Side", "Location", "Main Comments"
        )

        // Table B: SUB ACTIVITIES (Total = 786)
        // 0: S.No (32), 1: Sub Activity (110), 2: From CH (64), 3: To CH (64), 4: Len (54),
        // 5: Wid (50), 6: Depth (54), 7: Qty (66), 8: Location (54), 9: Sub Comments (238)
        val subColWidths = floatArrayOf(32f, 110f, 64f, 64f, 54f, 50f, 54f, 66f, 54f, 238f)
        val subColHeaders = arrayOf(
            "S.No", "Sub Activity", "From CH", "To CH", "Length (m)", "Width (m)",
            "Depth (mm)", "Quantity (m³)", "Location", "Sub Comments"
        )

        fun drawFooter(curPageNumber: Int) {
            paint.color = tableBorderColor
            paint.strokeWidth = 0.8f
            canvas.drawLine(leftMargin, footerLineY, rightMargin, footerLineY, paint)

            paint.color = subTextColor
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())
            canvas.drawText("CTECS Highway Engineering DPR • Generated on $dateStr", leftMargin + 4f, footerLineY + 12f, paint)

            val pageStr = "Page $curPageNumber"
            val pageW = paint.measureText(pageStr)
            canvas.drawText(pageStr, rightMargin - pageW - 4f, footerLineY + 12f, paint)
        }

        fun drawTopBanner(isFirstPage: Boolean, activeDateLabel: String = "") {
            y = 20f

            // Top decorative bar
            paint.color = primaryColor
            canvas.drawRect(leftMargin, y, rightMargin, y + 3.5f, paint)
            y += 12f

            // Custom Logo at Top Right
            if (customLogoBitmap != null) {
                try {
                    val maxW = 80f
                    val maxH = 28f
                    val aspect = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
                    val targetW = if (aspect >= (maxW / maxH)) maxW else maxH * aspect
                    val targetH = if (aspect >= (maxW / maxH)) maxW / aspect else maxH
                    val destRect = RectF(rightMargin - targetW, y - 2f, rightMargin, y - 2f + targetH)
                    canvas.drawBitmap(customLogoBitmap, null, destRect, paint)
                } catch (e: Exception) {
                    // Ignore logo drawing error
                }
            }

            // Report Title
            paint.color = primaryColor
            paint.textSize = 13.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("HIGHWAY SITE ACTIVITY REPORT", leftMargin, y + 2f, paint)
            y += 13f

            paint.color = subTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val subText = if (activeDateLabel.isNotBlank()) {
                "$reportTitle • $periodSubTitle • Active Date: $activeDateLabel • Page $pageNumber"
            } else {
                "$reportTitle • $periodSubTitle • Page $pageNumber"
            }
            canvas.drawText(subText, leftMargin, y + 1f, paint)
            y += 14f
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
                canvas.drawText(mainColHeaders[i], curX + 4f, y + 12f, paint)
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
                canvas.drawText(subColHeaders[i], curX + 4f, y + 12f, paint)
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

                // Page break check before starting a new daily section
                if (dayIndex > 1 && y + 160f > maxPageY) {
                    drawFooter(pageNumber)
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawTopBanner(isFirstPage = false, activeDateLabel = displayDayDate)
                }

                // -------------------------------------------------------------
                // DAILY SECTION HEADER & PROJECT INFO
                // -------------------------------------------------------------
                val infoBoxH = 34f
                paint.color = Color.rgb(241, 245, 249)
                canvas.drawRoundRect(leftMargin, y, rightMargin, y + infoBoxH, 4f, 4f, paint)
                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(leftMargin, y, rightMargin, y + infoBoxH, 4f, 4f, paint)
                paint.style = Paint.Style.FILL

                // Day Title & Info line
                paint.color = primaryColor
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val dayTitle = "DAILY SITE ACTIVITY REPORT – ${displayDayDate.uppercase(Locale.US)}"
                canvas.drawText(dayTitle, leftMargin + 8f, y + 13f, paint)

                paint.color = textColor
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val prjName = if (project.name.isNotBlank()) project.name else project.id
                val stretch = if (project.startChainage.isNotBlank() && project.endChainage.isNotBlank()) {
                    "${project.startChainage} to ${project.endChainage}"
                } else "Full Project Section"
                val infoText = "Project: $prjName   |   Stretch: $stretch   |   Total Recorded Main Activities: ${dayActivities.size}"
                canvas.drawText(infoText, leftMargin + 8f, y + 26f, paint)

                y += infoBoxH + 10f

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
                    paint.textSize = 9.5f
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

                        // Text wrapping for main comments
                        val rawComments = act.siteObservations.ifBlank { act.remarks }.trim()
                        val commentLines = wrapText(rawComments, paint, mainColWidths[10] - 10f)
                        val rowH = max(18f, (commentLines.size * 9.5f) + 7f)

                        // Check page boundary
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

                        val textY = y + 12f
                        var drawX = leftMargin

                        // 0. S.No
                        canvas.drawText("${aIdx + 1}", drawX + 4f, textY, paint)
                        drawX += mainColWidths[0]

                        // 1. From CH
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        paint.color = chainageBlue
                        canvas.drawText(act.fromChainage.ifBlank { "-" }, drawX + 4f, textY, paint)
                        drawX += mainColWidths[1]

                        // 2. To CH
                        canvas.drawText(act.toChainage.ifBlank { "-" }, drawX + 4f, textY, paint)
                        drawX += mainColWidths[2]

                        // 3. Activity
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        paint.color = textColor
                        val actName = act.activityLayer.ifBlank { "BC" }
                        canvas.drawText(actName.take(14), drawX + 4f, textY, paint)
                        drawX += mainColWidths[3]

                        // 4. Length (m)
                        val lenStr = if (len > 0) "${len.toInt()}" else "-"
                        canvas.drawText(lenStr, drawX + 4f, textY, paint)
                        drawX += mainColWidths[4]

                        // 5. Width (m)
                        val widStr = if (wid > 0) "%.1f".format(Locale.US, wid) else "-"
                        canvas.drawText(widStr, drawX + 4f, textY, paint)
                        drawX += mainColWidths[5]

                        // 6. Depth (mm)
                        val depthStr = if (depthMm > 0) {
                            if (depthMm % 1.0 == 0.0) "${depthMm.toInt()}" else "${depthMm}"
                        } else "-"
                        canvas.drawText(depthStr, drawX + 4f, textY, paint)
                        drawX += mainColWidths[6]

                        // 7. Quantity (m³)
                        val qtyStr = if (volM3 > 0) "%.2f".format(Locale.US, volM3) else "-"
                        canvas.drawText(qtyStr, drawX + 4f, textY, paint)
                        drawX += mainColWidths[7]

                        // 8. Side
                        val sideStr = act.side.ifBlank { "LHS" }
                        canvas.drawText(sideStr, drawX + 4f, textY, paint)
                        drawX += mainColWidths[8]

                        // 9. Location
                        val locStr = act.roadSection.ifBlank { "I/L" }
                        canvas.drawText(locStr.take(12), drawX + 4f, textY, paint)
                        drawX += mainColWidths[9]

                        // 10. Main Comments (wrapped across lines, never clipped)
                        var cY = y + 10.5f
                        commentLines.forEach { line ->
                            canvas.drawText(line, drawX + 4f, cY, paint)
                            cY += 9.5f
                        }

                        y += rowH
                    }

                    // Main Activities Total Row (Clean, separate columns, zero overlap)
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

                    // Spanning label
                    canvas.drawText("Main Activities Daily Total:", leftMargin + 6f, y + 12f, paint)

                    // Length Cell (X: leftMargin + mainColWidths[0]+[1]+[2]+[3])
                    val lenColX = leftMargin + mainColWidths[0] + mainColWidths[1] + mainColWidths[2] + mainColWidths[3]
                    canvas.drawText("${dailyMainLen.toInt()} m", lenColX + 4f, y + 12f, paint)

                    // Quantity Cell (X: lenColX + mainColWidths[4]+[5]+[6])
                    val qtyColX = lenColX + mainColWidths[4] + mainColWidths[5] + mainColWidths[6]
                    val totVolStr = if (dailyMainVol > 0) "%.2f m³".format(Locale.US, dailyMainVol) else "-"
                    canvas.drawText(totVolStr, qtyColX + 4f, y + 12f, paint)

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
                        paint.textSize = 9.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText("B. SUB ACTIVITIES", leftMargin, y + 2f, paint)
                        y += 8f

                        drawSubTableHeader()

                        daySubs.forEachIndexed { sIdx, sub ->
                            dailySubLen += sub.lengthM
                            dailySubVol += sub.quantityM3

                            val subCommentLines = wrapText(sub.comments, paint, subColWidths[9] - 10f)
                            val rowH = max(18f, (subCommentLines.size * 9.5f) + 7f)

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

                            val textY = y + 12f
                            var drawX = leftMargin

                            // 0. S.No
                            canvas.drawText("${sIdx + 1}", drawX + 4f, textY, paint)
                            drawX += subColWidths[0]

                            // 1. Sub Activity
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            canvas.drawText(sub.subName.take(24), drawX + 4f, textY, paint)
                            drawX += subColWidths[1]

                            // 2. From CH
                            paint.color = chainageBlue
                            canvas.drawText(sub.fromCh.ifBlank { "-" }, drawX + 4f, textY, paint)
                            drawX += subColWidths[2]

                            // 3. To CH
                            canvas.drawText(sub.toCh.ifBlank { "-" }, drawX + 4f, textY, paint)
                            drawX += subColWidths[3]

                            // 4. Length (m)
                            paint.color = textColor
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            val sLenStr = if (sub.lengthM > 0) "${sub.lengthM.toInt()}" else "-"
                            canvas.drawText(sLenStr, drawX + 4f, textY, paint)
                            drawX += subColWidths[4]

                            // 5. Width (m)
                            val sWidStr = if (sub.widthM > 0) "%.1f".format(Locale.US, sub.widthM) else "-"
                            canvas.drawText(sWidStr, drawX + 4f, textY, paint)
                            drawX += subColWidths[5]

                            // 6. Depth (mm)
                            val sDepthStr = if (sub.depthM > 0) {
                                if (sub.depthM % 1.0 == 0.0) "${sub.depthM.toInt()}" else "${sub.depthM}"
                            } else "-"
                            canvas.drawText(sDepthStr, drawX + 4f, textY, paint)
                            drawX += subColWidths[6]

                            // 7. Quantity (m³)
                            val sQtyStr = if (sub.quantityM3 > 0) "%.2f".format(Locale.US, sub.quantityM3) else "-"
                            canvas.drawText(sQtyStr, drawX + 4f, textY, paint)
                            drawX += subColWidths[7]

                            // 8. Location
                            val sLoc = sub.location.takeIf { it.isNotBlank() && !it.equals("None", true) } ?: "-"
                            canvas.drawText(sLoc.take(12), drawX + 4f, textY, paint)
                            drawX += subColWidths[8]

                            // 9. Sub Comments
                            var scY = y + 10.5f
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
                        canvas.drawText("${dailySubLen.toInt()} m", subLenColX + 4f, y + 12f, paint)

                        val subQtyColX = subLenColX + subColWidths[4] + subColWidths[5] + subColWidths[6]
                        val subTotVolStr = if (dailySubVol > 0) "%.2f m³".format(Locale.US, dailySubVol) else "-"
                        canvas.drawText(subTotVolStr, subQtyColX + 4f, y + 12f, paint)

                        y += totH + 10f
                    }
                }

                // -------------------------------------------------------------
                // C. DAILY SUMMARY
                // -------------------------------------------------------------
                val dailyObsList = dayActivities.mapNotNull {
                    it.siteObservations.ifBlank { it.remarks }.trim().takeIf { s -> s.isNotBlank() && !s.equals("None", true) }
                }.distinct()

                val hasObs = dailyObsList.isNotEmpty() || (remarks.isNotBlank() && !isRangeReport)
                val summaryBoxH = if (hasObs) 48f else 32f

                if (y + summaryBoxH > maxPageY) {
                    drawFooter(pageNumber)
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawTopBanner(isFirstPage = false, activeDateLabel = "$displayDayDate (Cont.)")
                }

                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRoundRect(leftMargin, y, rightMargin, y + summaryBoxH, 4f, 4f, paint)
                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRoundRect(leftMargin, y, rightMargin, y + summaryBoxH, 4f, 4f, paint)
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

                if (hasObs) {
                    val obsText = if (remarks.isNotBlank() && !isRangeReport) remarks else dailyObsList.first()
                    val obsLine = "• Daily Remarks: " + obsText.take(130)
                    canvas.drawText(obsLine, leftMargin + 8f, y + 43f, paint)
                }

                y += summaryBoxH + 14f

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
        val sigRequiredH = 50f
        if (y + sigRequiredH > maxPageY) {
            drawFooter(pageNumber)
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            drawTopBanner(isFirstPage = false)
        }

        val signLineY = y + 26f

        if (signatureBitmap != null) {
            try {
                val targetSigW = 100f
                val targetSigH = 26f
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
        } else {
            paint.color = tableBorderColor
            paint.strokeWidth = 0.8f
            canvas.drawLine(leftMargin + 10f, signLineY, leftMargin + 160f, signLineY, paint)
        }

        val signer = if (preparedBy.isNotBlank()) preparedBy else "Site Engineer"
        paint.color = textColor
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Prepared By: $signer", leftMargin + 10f, signLineY + 12f, paint)

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
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
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
