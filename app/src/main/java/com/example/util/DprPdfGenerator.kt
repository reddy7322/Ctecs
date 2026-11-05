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

data class SubActivityPdfItem(
    val subName: String,
    val location: String = "",
    val fromCh: String,
    val toCh: String,
    val lengthM: Double,
    val widthM: Double,
    val depthM: Double,
    val comments: String
)

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
                        result.add(
                            SubActivityPdfItem(
                                subName = sName,
                                location = obj.optString("location", ""),
                                fromCh = obj.optString("fromChainage", act.fromChainage),
                                toCh = obj.optString("toChainage", act.toChainage),
                                lengthM = obj.optDouble("lengthM", 0.0),
                                widthM = obj.optDouble("widthM", 0.0),
                                depthM = obj.optDouble("depthM", act.subDepthM),
                                comments = obj.optString("comments", "")
                            )
                        )
                    }
                }
                if (result.isNotEmpty()) return result
            } catch (e: Exception) {
                // Fallback
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
        val displayDate = try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.US)
            formatter.format(parser.parse(dateString) ?: Date())
        } catch (e: Exception) {
            dateString
        }

        return generateReportPdfInternal(
            context = context,
            project = project,
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
     * Generates a Weekly / Custom Period Site Activity Report PDF.
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
        val displayPeriod = try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.US)
            val d1 = formatter.format(parser.parse(startDateString) ?: Date())
            val d2 = formatter.format(parser.parse(endDateString) ?: Date())
            "Period: $d1 to $d2"
        } catch (e: Exception) {
            "Period: $startDateString to $endDateString"
        }

        return generateReportPdfInternal(
            context = context,
            project = project,
            reportTitle = reportTitle,
            periodSubTitle = displayPeriod,
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

    private fun generateReportPdfInternal(
        context: Context,
        project: HighwayProjectEntity,
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
        val pageWidth = 595
        val pageHeight = 842

        val paint = Paint().apply { isAntiAlias = true }

        val primaryColor = Color.rgb(20, 83, 45)      // Forest Green Header
        val tableHeaderBg = Color.rgb(22, 101, 52)
        val altRowBg = Color.rgb(245, 247, 250)
        val tableBorderColor = Color.rgb(203, 213, 225)
        val textColor = Color.rgb(15, 23, 42)
        val subTextColor = Color.rgb(100, 116, 139)

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        var y = 28f

        fun drawPageHeader(isFirstPage: Boolean) {
            y = 28f

            // Top decorative bar
            paint.color = primaryColor
            canvas.drawRect(30f, y, 565f, y + 4f, paint)
            y += 14f

            // Custom Logo (if enabled and present)
            if (customLogoBitmap != null) {
                try {
                    val maxW = 80f
                    val maxH = 35f
                    val aspect = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
                    val targetW = if (aspect >= 1f) maxW else maxH * aspect
                    val targetH = if (aspect >= 1f) maxW / aspect else maxH
                    val destRect = RectF(565f - targetW, y - 2f, 565f, y - 2f + targetH)
                    canvas.drawBitmap(customLogoBitmap, null, destRect, paint)
                } catch (e: Exception) {
                    // Ignore logo drawing failure
                }
            }

            paint.color = primaryColor
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("HIGHWAY SITE ACTIVITY REPORT", 30f, y, paint)
            y += 13f

            paint.color = subTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("$reportTitle • $periodSubTitle • Page $pageNumber", 30f, y, paint)
            y += 14f

            if (isFirstPage) {
                // Simple Project Header Box
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRoundRect(30f, y, 565f, y + 36f, 4f, 4f, paint)
                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(30f, y, 565f, y + 36f, 4f, 4f, paint)
                paint.style = Paint.Style.FILL

                paint.color = textColor
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val prjName = if (project.name.isNotBlank()) project.name else project.id
                canvas.drawText("Project: $prjName", 38f, y + 14f, paint)

                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = subTextColor
                canvas.drawText("Total Recorded Activities: ${activities.size}", 38f, y + 27f, paint)

                if (project.startChainage.isNotBlank() && project.endChainage.isNotBlank()) {
                    canvas.drawText("Stretch: ${project.startChainage} to ${project.endChainage}", 350f, y + 14f, paint)
                }

                y += 44f
            }
        }

        drawPageHeader(isFirstPage = true)

        // -------------------------------------------------------------
        // MAIN ACTIVITY TABLE
        // -------------------------------------------------------------
        if (exportScope != "SUB_ONLY") {
            paint.color = primaryColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("MAIN SITE ACTIVITIES", 30f, y, paint)
            y += 10f

            fun drawMainActivityTableHeader() {
                val hHeight = 18f
                paint.color = tableHeaderBg
                canvas.drawRect(30f, y, 565f, y + hHeight, paint)

                paint.color = Color.WHITE
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                val textY = y + 12f
                canvas.drawText("S.No", 33f, textY, paint)
                canvas.drawText("From CH", 53f, textY, paint)
                canvas.drawText("To CH", 98f, textY, paint)
                canvas.drawText("Activity", 143f, textY, paint)
                canvas.drawText("Len (m)", 193f, textY, paint)
                canvas.drawText("Wid (m)", 235f, textY, paint)
                canvas.drawText("Depth", 275f, textY, paint)
                canvas.drawText("Side", 315f, textY, paint)
                canvas.drawText("Location", 345f, textY, paint)
                canvas.drawText("Main Comments", 395f, textY, paint)

                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRect(30f, y, 565f, y + hHeight, paint)
                paint.style = Paint.Style.FILL

                y += hHeight
            }

            drawMainActivityTableHeader()

            var totalLengthM = 0.0

            if (activities.isEmpty()) {
                val rHeight = 18f
                paint.color = Color.WHITE
                canvas.drawRect(30f, y, 565f, y + rHeight, paint)
                paint.color = subTextColor
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No site activity records available for this selection.", 40f, y + 12f, paint)
                y += rHeight
            } else {
                activities.forEachIndexed { idx, act ->
                    totalLengthM += act.completedLengthM.takeIf { it > 0 } ?: act.quantity

                    val rowHeight = 18f
                    if (y + rowHeight > 740f) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        drawPageHeader(isFirstPage = false)
                        drawMainActivityTableHeader()
                    }

                    // Row Background
                    paint.color = if (idx % 2 == 0) Color.WHITE else altRowBg
                    canvas.drawRect(30f, y, 565f, y + rowHeight, paint)

                    // Row Border
                    paint.color = tableBorderColor
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.5f
                    canvas.drawRect(30f, y, 565f, y + rowHeight, paint)
                    paint.style = Paint.Style.FILL

                    paint.color = textColor
                    paint.textSize = 7.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                    val rowY = y + 12f
                    canvas.drawText("${idx + 1}", 33f, rowY, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.color = Color.rgb(30, 58, 138)
                    canvas.drawText(act.fromChainage.ifBlank { "-" }, 53f, rowY, paint)
                    canvas.drawText(act.toChainage.ifBlank { "-" }, 98f, rowY, paint)

                    paint.color = textColor
                    val actLabel = act.activityLayer.ifBlank { "BC" }
                    canvas.drawText(actLabel.take(12), 143f, rowY, paint)

                    val lenVal = if (act.completedLengthM > 0) "${act.completedLengthM.toInt()}" else if (act.quantity > 0) "${act.quantity.toInt()}" else "-"
                    canvas.drawText(lenVal, 193f, rowY, paint)

                    val widVal = if (act.widthM > 0) "%.1f".format(Locale.US, act.widthM) else "-"
                    canvas.drawText(widVal, 235f, rowY, paint)

                    val depthVal = if (act.depthM > 0) "%.2f".format(Locale.US, act.depthM) else if (act.thicknessMm > 0) "%.0f".format(Locale.US, act.thicknessMm) else "-"
                    canvas.drawText(depthVal, 275f, rowY, paint)

                    val sideVal = act.side.ifBlank { "LHS" }
                    canvas.drawText(sideVal, 315f, rowY, paint)

                    val locVal = act.roadSection.ifBlank { "I/L" }
                    canvas.drawText(locVal.take(10), 345f, rowY, paint)

                    val mainComm = act.siteObservations.ifBlank { act.remarks }.takeIf { it.isNotBlank() && !it.equals("None", true) } ?: "-"
                    canvas.drawText(mainComm.take(32), 395f, rowY, paint)

                    y += rowHeight
                }

                // Total Completed Length Summary Row
                val totalRowH = 18f
                paint.color = Color.rgb(241, 245, 249)
                canvas.drawRect(30f, y, 565f, y + totalRowH, paint)
                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRect(30f, y, 565f, y + totalRowH, paint)
                paint.style = Paint.Style.FILL

                paint.color = primaryColor
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Total Completed Length:", 35f, y + 12f, paint)

                val totalKm = totalLengthM / 1000.0
                val totalText = "${totalLengthM.toInt()} m (${"%.2f".format(Locale.US, totalKm)} km)"
                canvas.drawText(totalText, 193f, y + 12f, paint)

                y += totalRowH + 12f
            }
        }

        // -------------------------------------------------------------
        // SUB ACTIVITY TABLE
        // -------------------------------------------------------------
        if (exportScope != "MAIN_ONLY") {
        val allSubActivities = mutableListOf<SubActivityPdfItem>()
        activities.forEach { act ->
            allSubActivities.addAll(extractSubActivities(act))
        }

        if (allSubActivities.isNotEmpty()) {
            if (y + 40f > 740f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                drawPageHeader(isFirstPage = false)
            }

            paint.color = primaryColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("SUB ACTIVITIES", 30f, y, paint)
            y += 10f

            fun drawSubActivityTableHeader() {
                val hHeight = 18f
                paint.color = tableHeaderBg
                canvas.drawRect(30f, y, 565f, y + hHeight, paint)

                paint.color = Color.WHITE
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                val textY = y + 12f
                canvas.drawText("S.No", 33f, textY, paint)
                canvas.drawText("Sub Activity", 55f, textY, paint)
                canvas.drawText("Location", 135f, textY, paint)
                canvas.drawText("From CH", 180f, textY, paint)
                canvas.drawText("To CH", 225f, textY, paint)
                canvas.drawText("Len (m)", 270f, textY, paint)
                canvas.drawText("Wid (m)", 310f, textY, paint)
                canvas.drawText("Depth", 350f, textY, paint)
                canvas.drawText("Sub Comments", 395f, textY, paint)

                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRect(30f, y, 565f, y + hHeight, paint)
                paint.style = Paint.Style.FILL

                y += hHeight
            }

            drawSubActivityTableHeader()

            allSubActivities.forEachIndexed { sIdx, sub ->
                val rowHeight = 18f
                if (y + rowHeight > 740f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawPageHeader(isFirstPage = false)
                    drawSubActivityTableHeader()
                }

                paint.color = if (sIdx % 2 == 0) Color.WHITE else altRowBg
                canvas.drawRect(30f, y, 565f, y + rowHeight, paint)

                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                canvas.drawRect(30f, y, 565f, y + rowHeight, paint)
                paint.style = Paint.Style.FILL

                paint.color = textColor
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                val rowY = y + 12f
                canvas.drawText("${sIdx + 1}", 33f, rowY, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(sub.subName.take(16), 55f, rowY, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val subLoc = sub.location.takeIf { it.isNotBlank() && !it.equals("None", true) } ?: "-"
                canvas.drawText(subLoc.take(10), 135f, rowY, paint)

                paint.color = Color.rgb(30, 58, 138)
                canvas.drawText(sub.fromCh.ifBlank { "-" }, 180f, rowY, paint)
                canvas.drawText(sub.toCh.ifBlank { "-" }, 225f, rowY, paint)

                paint.color = textColor
                val subLenStr = if (sub.lengthM > 0) "${sub.lengthM.toInt()}" else "-"
                canvas.drawText(subLenStr, 270f, rowY, paint)

                val subWidStr = if (sub.widthM > 0) "%.1f".format(Locale.US, sub.widthM) else "-"
                canvas.drawText(subWidStr, 310f, rowY, paint)

                val subDepthStr = if (sub.depthM > 0) "%.2f".format(Locale.US, sub.depthM) else "-"
                canvas.drawText(subDepthStr, 350f, rowY, paint)

                val commStr = if (sub.comments.isNotBlank() && !sub.comments.equals("None", true)) sub.comments.take(30) else "-"
                canvas.drawText(commStr, 395f, rowY, paint)

                y += rowHeight
            }
            y += 12f
        }
        }

        // Main Comments / Observations Section
        val combinedComments = activities.mapNotNull { it.siteObservations.ifBlank { it.remarks }.takeIf { s -> s.isNotBlank() && !s.equals("None", true) } }
        if (combinedComments.isNotEmpty() || remarks.isNotBlank()) {
            if (y + 40f > 740f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                drawPageHeader(isFirstPage = false)
            }

            paint.color = primaryColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("SITE OBSERVATIONS & REMARKS", 30f, y, paint)
            y += 12f

            paint.color = textColor
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            val textList = if (remarks.isNotBlank()) listOf(remarks) else combinedComments.take(3)
            textList.forEach { comm ->
                wrapText(comm, paint, 530f).take(2).forEach { l ->
                    canvas.drawText("• $l", 32f, y, paint)
                    y += 10f
                }
            }
            y += 8f
        }

        // -------------------------------------------------------------
        // CLEAN FOOTER & PREPARED BY SIGNATURE SECTION
        // (NO RESIDENT ENGINEER, PMC, IE, OR CONTRACTOR PLACEHOLDERS)
        // -------------------------------------------------------------
        val sigAreaY = 750f
        if (y > sigAreaY - 20f) {
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            drawPageHeader(isFirstPage = false)
        }

        val lineY = sigAreaY + 36f

        // Only draw signature image & line if signature is provided
        if (signatureBitmap != null) {
            try {
                val targetSigW = 120f
                val targetSigH = 34f
                val sigAspect = signatureBitmap.width.toFloat() / signatureBitmap.height.toFloat()
                val actualW = if (sigAspect >= (targetSigW / targetSigH)) targetSigW else targetSigH * sigAspect
                val actualH = if (sigAspect >= (targetSigW / targetSigH)) targetSigW / sigAspect else targetSigH
                val sigDest = RectF(40f, lineY - actualH - 2f, 40f + actualW, lineY - 2f)
                canvas.drawBitmap(signatureBitmap, null, sigDest, paint)

                paint.color = tableBorderColor
                paint.strokeWidth = 1f
                canvas.drawLine(40f, lineY, 200f, lineY, paint)
            } catch (e: Exception) {
                // Ignore signature drawing error
            }
        }

        if (preparedBy.isNotBlank()) {
            paint.color = textColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Prepared By: $preparedBy", 40f, lineY + 13f, paint)
        }

        pdfDocument.finishPage(page)

        // Write to Cache directory
        val pdfFile = File(context.cacheDir, "${fileNamePrefix}_${System.currentTimeMillis() % 10000}.pdf")
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

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }
}
