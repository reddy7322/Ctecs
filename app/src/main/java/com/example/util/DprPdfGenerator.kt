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
    val mainActivity: String,
    val subName: String,
    val fromCh: String,
    val toCh: String,
    val lengthM: Double,
    val widthM: Double,
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
                                mainActivity = act.activityLayer,
                                subName = sName,
                                fromCh = obj.optString("fromChainage", act.fromChainage),
                                toCh = obj.optString("toChainage", act.toChainage),
                                lengthM = obj.optDouble("lengthM", 0.0),
                                widthM = obj.optDouble("widthM", 0.0),
                                comments = obj.optString("comments", "")
                            )
                        )
                    }
                }
                if (result.isNotEmpty()) return result
            } catch (e: Exception) {
                // Ignore JSON parse error and fallback
            }
        }

        if (act.subActivityName.isNotBlank() && !act.subActivityName.equals("None", true)) {
            return listOf(
                SubActivityPdfItem(
                    mainActivity = act.activityLayer,
                    subName = act.subActivityName,
                    fromCh = act.subFromChainage.ifBlank { act.fromChainage },
                    toCh = act.subToChainage.ifBlank { act.toChainage },
                    lengthM = act.subLengthM,
                    widthM = act.subWidthM,
                    comments = if (!act.subComments.startsWith("[")) act.subComments else ""
                )
            )
        }

        return emptyList()
    }

    /**
     * Generates a Daily Project Report (DPR) with engineering smart tables.
     */
    fun generateDprPdf(
        context: Context,
        project: HighwayProjectEntity,
        dateString: String,
        activities: List<SmartActivityEntity>,
        summary: String = "",
        remarks: String = "",
        preparedBy: String = "Site Engineer",
        verifiedBy: String = "Resident Engineer",
        customLogoBitmap: Bitmap? = null,
        signatureBitmap: Bitmap? = null
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
            verifiedBy = verifiedBy,
            customLogoBitmap = customLogoBitmap,
            signatureBitmap = signatureBitmap,
            fileNamePrefix = "DPR_${project.id.replace(Regex("[^a-zA-Z0-9]"), "")}_${dateString.replace("-", "")}"
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
        preparedBy: String = "Site Engineer",
        verifiedBy: String = "Resident Engineer",
        customLogoBitmap: Bitmap? = null,
        signatureBitmap: Bitmap? = null
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
            verifiedBy = verifiedBy,
            customLogoBitmap = customLogoBitmap,
            signatureBitmap = signatureBitmap,
            fileNamePrefix = "REPORT_${project.id.replace(Regex("[^a-zA-Z0-9]"), "")}_RANGE"
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
        verifiedBy: String,
        customLogoBitmap: Bitmap?,
        signatureBitmap: Bitmap?,
        fileNamePrefix: String
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val paint = Paint().apply { isAntiAlias = true }

        val primaryColor = Color.rgb(20, 83, 45)      // Forest Green Header
        val headerBgColor = Color.rgb(240, 245, 244)
        val tableHeaderBg = Color.rgb(22, 101, 52)
        val altRowBg = Color.rgb(245, 247, 250)
        val tableBorderColor = Color.rgb(203, 213, 225)
        val textColor = Color.rgb(15, 23, 42)
        val subTextColor = Color.rgb(100, 116, 139)

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        var y = 30f

        fun drawPageHeader(isFirstPage: Boolean) {
            y = 28f

            // Top decorative bar
            paint.color = primaryColor
            canvas.drawRect(30f, y, 565f, y + 4f, paint)
            y += 14f

            // Custom Logo (if available)
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
            canvas.drawText("CTECS HIGHWAY CONSTRUCTION RECORD", 30f, y, paint)
            y += 13f

            paint.color = subTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("$reportTitle • $periodSubTitle • Page $pageNumber", 30f, y, paint)
            y += 14f

            if (isFirstPage) {
                // Project Summary Header Box
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRoundRect(30f, y, 565f, y + 48f, 4f, 4f, paint)
                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(30f, y, 565f, y + 48f, 4f, 4f, paint)
                paint.style = Paint.Style.FILL

                paint.color = textColor
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val prjName = if (project.name.isNotBlank()) project.name else "Highway Construction Project"
                canvas.drawText("Project: $prjName", 38f, y + 15f, paint)

                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = subTextColor
                if (project.clientName.isNotBlank() && !project.clientName.equals("None", true)) {
                    canvas.drawText("Client: ${project.clientName}", 38f, y + 28f, paint)
                }
                if (project.contractorName.isNotBlank() && !project.contractorName.equals("None", true)) {
                    canvas.drawText("Agency / Contractor: ${project.contractorName}", 38f, y + 40f, paint)
                }

                if (project.startChainage.isNotBlank() && project.endChainage.isNotBlank()) {
                    canvas.drawText("Stretch: ${project.startChainage} to ${project.endChainage}", 350f, y + 15f, paint)
                }
                canvas.drawText("Total Recorded Activities: ${activities.size}", 350f, y + 28f, paint)

                y += 58f
            }
        }

        drawPageHeader(isFirstPage = true)

        // -------------------------------------------------------------
        // MAIN ACTIVITY ENGINEERING TABLE
        // -------------------------------------------------------------
        paint.color = primaryColor
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("1. MAIN SITE ACTIVITIES TABLE", 30f, y, paint)
        y += 10f

        fun drawMainActivityTableHeader() {
            val hHeight = 18f
            paint.color = tableHeaderBg
            canvas.drawRect(30f, y, 565f, y + hHeight, paint)

            paint.color = Color.WHITE
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            val textY = y + 12f
            canvas.drawText("S.No", 33f, textY, paint)
            canvas.drawText("From CH", 67f, textY, paint)
            canvas.drawText("To CH", 132f, textY, paint)
            canvas.drawText("Activity", 197f, textY, paint)
            canvas.drawText("Length (m)", 302f, textY, paint)
            canvas.drawText("Width (m)", 367f, textY, paint)
            canvas.drawText("Side", 427f, textY, paint)
            canvas.drawText("Location", 487f, textY, paint)

            // Header Border
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
            canvas.drawText("No site activity records available for this period.", 40f, y + 12f, paint)
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

                // Cell Content
                paint.color = textColor
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                val rowY = y + 12f
                canvas.drawText("${idx + 1}", 34f, rowY, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.rgb(30, 58, 138)
                canvas.drawText(act.fromChainage.ifBlank { "-" }, 67f, rowY, paint)
                canvas.drawText(act.toChainage.ifBlank { "-" }, 132f, rowY, paint)

                paint.color = textColor
                val actLabel = act.activityLayer.ifBlank { "BC" }
                canvas.drawText(actLabel.take(18), 197f, rowY, paint)

                val lenVal = if (act.completedLengthM > 0) "${act.completedLengthM.toInt()}" else if (act.quantity > 0) "${act.quantity.toInt()}" else "-"
                canvas.drawText(lenVal, 302f, rowY, paint)

                val widVal = if (act.widthM > 0) "%.1f".format(Locale.US, act.widthM) else "-"
                canvas.drawText(widVal, 367f, rowY, paint)

                val sideVal = act.side.ifBlank { "LHS" }
                canvas.drawText(sideVal, 427f, rowY, paint)

                val locVal = act.roadSection.ifBlank { "I/L" }
                canvas.drawText(locVal.take(15), 487f, rowY, paint)

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
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Total Completed Length:", 197f, y + 12f, paint)

            val totalKm = totalLengthM / 1000.0
            val totalText = "${totalLengthM.toInt()} m (${"%.2f".format(Locale.US, totalKm)} km)"
            canvas.drawText(totalText, 302f, y + 12f, paint)

            y += totalRowH + 12f
        }

        // -------------------------------------------------------------
        // SUB ACTIVITIES ENGINEERING TABLE (IF ANY EXIST)
        // -------------------------------------------------------------
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
            canvas.drawText("2. SUB ACTIVITIES TABLE", 30f, y, paint)
            y += 10f

            fun drawSubActivityTableHeader() {
                val hHeight = 18f
                paint.color = tableHeaderBg
                canvas.drawRect(30f, y, 565f, y + hHeight, paint)

                paint.color = Color.WHITE
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                val textY = y + 12f
                canvas.drawText("S.No", 33f, textY, paint)
                canvas.drawText("Main Activity", 67f, textY, paint)
                canvas.drawText("Sub Activity", 157f, textY, paint)
                canvas.drawText("From CH", 252f, textY, paint)
                canvas.drawText("To CH", 317f, textY, paint)
                canvas.drawText("Length", 382f, textY, paint)
                canvas.drawText("Width", 437f, textY, paint)
                canvas.drawText("Comments", 487f, textY, paint)

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
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                val rowY = y + 12f
                canvas.drawText("${sIdx + 1}", 34f, rowY, paint)
                canvas.drawText(sub.mainActivity.take(14), 67f, rowY, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(sub.subName.take(15), 157f, rowY, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = Color.rgb(30, 58, 138)
                canvas.drawText(sub.fromCh.ifBlank { "-" }, 252f, rowY, paint)
                canvas.drawText(sub.toCh.ifBlank { "-" }, 317f, rowY, paint)

                paint.color = textColor
                val subLenStr = if (sub.lengthM > 0) "${sub.lengthM.toInt()}m" else "-"
                canvas.drawText(subLenStr, 382f, rowY, paint)

                val subWidStr = if (sub.widthM > 0) "%.1f".format(Locale.US, sub.widthM) else "-"
                canvas.drawText(subWidStr, 437f, rowY, paint)

                val commStr = if (sub.comments.isNotBlank() && !sub.comments.equals("None", true)) sub.comments.take(16) else "-"
                canvas.drawText(commStr, 487f, rowY, paint)

                y += rowHeight
            }
            y += 12f
        }

        // Main Comments / Remarks Section
        val combinedComments = activities.mapNotNull { it.siteObservations.ifBlank { it.remarks }.takeIf { s -> s.isNotBlank() && !s.equals("None", true) } }
        if (combinedComments.isNotEmpty() || remarks.isNotBlank()) {
            if (y + 50f > 740f) {
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
        // FOOTER & SIGNATURE SECTION
        // -------------------------------------------------------------
        val sigAreaY = 745f
        if (y > sigAreaY - 20f) {
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            drawPageHeader(isFirstPage = false)
        }

        val lineY = sigAreaY + 36f
        paint.color = tableBorderColor
        paint.strokeWidth = 1f
        canvas.drawLine(40f, lineY, 220f, lineY, paint)
        canvas.drawLine(360f, lineY, 540f, lineY, paint)

        if (signatureBitmap != null) {
            try {
                val targetSigW = 120f
                val targetSigH = 34f
                val sigAspect = signatureBitmap.width.toFloat() / signatureBitmap.height.toFloat()
                val actualW = if (sigAspect >= (targetSigW / targetSigH)) targetSigW else targetSigH * sigAspect
                val actualH = if (sigAspect >= (targetSigW / targetSigH)) targetSigW / sigAspect else targetSigH
                val sigDest = RectF(50f, lineY - actualH - 2f, 50f + actualW, lineY - 2f)
                canvas.drawBitmap(signatureBitmap, null, sigDest, paint)
            } catch (e: Exception) {
                // Ignore signature render error
            }
        }

        paint.color = textColor
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val prepText = if (preparedBy.isNotBlank()) preparedBy else "Site Engineer"
        canvas.drawText(prepText, 45f, lineY + 13f, paint)

        val verText = if (verifiedBy.isNotBlank()) verifiedBy else "Resident Engineer"
        canvas.drawText(verText, 365f, lineY + 13f, paint)

        paint.color = subTextColor
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Prepared & Inspected By (Site Incharge)", 45f, lineY + 24f, paint)
        canvas.drawText("Verified & Approved By (Quality Authority)", 365f, lineY + 24f, paint)

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
