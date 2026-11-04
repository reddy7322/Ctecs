package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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

object DprPdfGenerator {

    /**
     * Generates a professional highway Daily Project Report (DPR) PDF.
     * Returns the created PDF file.
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
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val paint = Paint().apply { isAntiAlias = true }

        val primaryColor = Color.rgb(20, 83, 45)      // Deep Forest Green
        val headerBgColor = Color.rgb(240, 245, 244)
        val tableBorderColor = Color.rgb(203, 213, 225)
        val textColor = Color.rgb(15, 23, 42)
        val subTextColor = Color.rgb(100, 116, 139)
        val accentYellow = Color.rgb(217, 119, 6)

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        var y = 36f

        // Helper to draw standard page header
        fun drawPageHeader(isFirstPage: Boolean) {
            y = 30f

            // Top decorative bar
            paint.color = primaryColor
            canvas.drawRect(30f, y, 565f, y + 5f, paint)
            y += 16f

            // Logo drawing if provided
            var titleStartX = 30f
            if (customLogoBitmap != null) {
                try {
                    val maxLogoW = 90f
                    val maxLogoH = 40f
                    val aspect = customLogoBitmap.width.toFloat() / customLogoBitmap.height.toFloat()
                    val targetW: Float
                    val targetH: Float
                    if (aspect >= 1f) {
                        targetW = maxLogoW
                        targetH = (maxLogoW / aspect).coerceAtMost(maxLogoH)
                    } else {
                        targetH = maxLogoH
                        targetW = (maxLogoH * aspect).coerceAtMost(maxLogoW)
                    }
                    val destRect = RectF(565f - targetW, y - 4f, 565f, y - 4f + targetH)
                    canvas.drawBitmap(customLogoBitmap, null, destRect, paint)
                } catch (e: Exception) {
                    // Ignore drawing error
                }
            }

            paint.color = primaryColor
            paint.textSize = 15f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("CTECS HIGHWAY DAILY PROJECT REPORT (DPR)", titleStartX, y, paint)
            y += 14f

            paint.color = subTextColor
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val displayDate = try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val formatter = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.US)
                formatter.format(parser.parse(dateString) ?: Date())
            } catch (e: Exception) {
                dateString
            }
            canvas.drawText("Site Activity & Quality Progress Record • Date: $displayDate • Page $pageNumber", titleStartX, y, paint)
            y += 16f

            if (isFirstPage) {
                // Project Header Summary Box
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRoundRect(30f, y, 565f, y + 52f, 6f, 6f, paint)
                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(30f, y, 565f, y + 52f, 6f, 6f, paint)
                paint.style = Paint.Style.FILL

                paint.color = textColor
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val prjNameText = if (project.name.isNotBlank()) project.name else "Highway Construction Project"
                canvas.drawText("Project: $prjNameText", 40f, y + 16f, paint)

                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = subTextColor
                if (project.clientName.isNotBlank() && !project.clientName.equals("None", true)) {
                    canvas.drawText("Client: ${project.clientName}", 40f, y + 30f, paint)
                }
                if (project.contractorName.isNotBlank() && !project.contractorName.equals("None", true)) {
                    canvas.drawText("Agency / Contractor: ${project.contractorName}", 40f, y + 43f, paint)
                }

                if (project.startChainage.isNotBlank() && project.endChainage.isNotBlank()) {
                    canvas.drawText("Chainage Stretch: ${project.startChainage} to ${project.endChainage}", 340f, y + 16f, paint)
                }
                if (project.totalLengthKm > 0) {
                    canvas.drawText("Total Project Length: ${project.totalLengthKm} km", 340f, y + 30f, paint)
                }

                y += 62f
            }
        }

        // Draw initial page header
        drawPageHeader(isFirstPage = true)

        // Summary if provided
        if (summary.isNotBlank()) {
            paint.color = primaryColor
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("DAILY WORK SUMMARY", 30f, y, paint)
            y += 12f

            paint.color = textColor
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val summaryLines = wrapText(summary, paint, 530f)
            summaryLines.forEach { line ->
                canvas.drawText(line, 30f, y, paint)
                y += 10f
            }
            y += 6f
        }

        // Section Title: Activities
        paint.color = primaryColor
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val exportTitle = if (activities.size == 1) "SITE ACTIVITY DETAIL (Single Activity Export)" else "SITE ACTIVITIES RECORD (${activities.size} Entries)"
        canvas.drawText(exportTitle, 30f, y, paint)
        y += 10f

        if (activities.isEmpty()) {
            paint.color = subTextColor
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("No site activity recorded for this selection.", 40f, y + 14f, paint)
            y += 30f
        } else {
            activities.forEachIndexed { index, act ->
                // Check if card fits on current page (card height ~75f to 95f)
                val estimatedCardH = if (act.subActivityName.isNotBlank() || act.siteObservations.isNotBlank()) 92f else 72f
                if (y + estimatedCardH > 720f) {
                    // Finish current page and start a new one
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawPageHeader(isFirstPage = false)
                }

                val cardTop = y
                val cardBottom = cardTop + estimatedCardH

                // Activity Card Background
                paint.color = if (index % 2 == 0) Color.rgb(255, 255, 255) else Color.rgb(248, 250, 252)
                canvas.drawRoundRect(30f, cardTop, 565f, cardBottom, 5f, 5f, paint)

                paint.color = tableBorderColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                canvas.drawRoundRect(30f, cardTop, 565f, cardBottom, 5f, 5f, paint)
                paint.style = Paint.Style.FILL

                // Left Accent Stripe
                paint.color = primaryColor
                canvas.drawRoundRect(30f, cardTop, 35f, cardBottom, 2f, 2f, paint)

                var cardY = cardTop + 14f

                // Line 1: Activity Name, Main Chainage, Length, Width
                paint.color = textColor
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val actLabel = if (act.activityLayer.isNotBlank() && !act.activityLayer.equals("None", true)) act.activityLayer else "Activity #${index + 1}"
                canvas.drawText("${index + 1}. $actLabel", 42f, cardY, paint)

                val chText = if (act.fromChainage.isNotBlank() && act.toChainage.isNotBlank()) {
                    "Ch. ${act.fromChainage} → ${act.toChainage}"
                } else if (act.fromChainage.isNotBlank()) {
                    "Ch. ${act.fromChainage}"
                } else ""
                if (chText.isNotBlank()) {
                    paint.color = Color.rgb(30, 58, 138)
                    paint.textSize = 9f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(chText, 170f, cardY, paint)
                }

                // Length & Width
                val lenStr = if (act.completedLengthM > 0) "${act.completedLengthM.toInt()}m" else if (act.quantity > 0) "${act.quantity.toInt()}m" else ""
                val widStr = if (act.widthM > 0) "${"%.1f".format(Locale.US, act.widthM)}m" else ""
                val dimStr = listOf(lenStr.takeIf { it.isNotBlank() }?.let { "L: $it" }, widStr.takeIf { it.isNotBlank() }?.let { "W: $it" })
                    .filterNotNull()
                    .joinToString(" • ")

                if (dimStr.isNotBlank()) {
                    paint.color = textColor
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText(dimStr, 340f, cardY, paint)
                }

                // Side & Road Location
                val sideTag = when {
                    act.isSideAutoDetected -> "${act.side} (Auto)"
                    act.side.isNotBlank() && !act.side.equals("None", true) -> "${act.side} (Manual)"
                    else -> ""
                }
                val locTag = if (act.roadSection.isNotBlank() && !act.roadSection.equals("None", true)) act.roadSection else ""
                val sideLocText = listOf(sideTag.takeIf { it.isNotBlank() }, locTag.takeIf { it.isNotBlank() })
                    .filterNotNull()
                    .joinToString(" • ")

                if (sideLocText.isNotBlank()) {
                    paint.color = Color.rgb(180, 83, 9)
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(sideLocText, 440f, cardY, paint)
                }

                cardY += 15f

                // Line 2: Sub-Activity (if present)
                val hasSub = act.subActivityName.isNotBlank() && !act.subActivityName.equals("None", true)
                if (hasSub) {
                    paint.color = primaryColor
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("Sub Activity: ${act.subActivityName}", 42f, cardY, paint)

                    val subCh = if (act.subFromChainage.isNotBlank() && act.subToChainage.isNotBlank()) {
                        "Ch. ${act.subFromChainage} → ${act.subToChainage}"
                    } else ""
                    if (subCh.isNotBlank()) {
                        paint.color = subTextColor
                        paint.textSize = 8f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        canvas.drawText(subCh, 180f, cardY, paint)
                    }

                    val subLen = if (act.subLengthM > 0) "L: ${act.subLengthM.toInt()}m" else ""
                    val subWid = if (act.subWidthM > 0) "W: ${"%.1f".format(Locale.US, act.subWidthM)}m" else ""
                    val subDims = listOf(subLen.takeIf { it.isNotBlank() }, subWid.takeIf { it.isNotBlank() })
                        .filterNotNull()
                        .joinToString(" • ")
                    if (subDims.isNotBlank()) {
                        paint.color = subTextColor
                        paint.textSize = 8f
                        canvas.drawText(subDims, 340f, cardY, paint)
                    }

                    if (act.subComments.isNotBlank() && !act.subComments.equals("None", true)) {
                        paint.color = Color.rgb(71, 85, 105)
                        paint.textSize = 7.5f
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                        val subCommWrapped = wrapText("Note: ${act.subComments}", paint, 180f).firstOrNull() ?: ""
                        canvas.drawText(subCommWrapped, 430f, cardY, paint)
                    }

                    cardY += 14f
                }

                // Line 3: Main Site Observations & Comments
                val obs = act.siteObservations.ifBlank { act.remarks }
                if (obs.isNotBlank() && !obs.equals("None", true)) {
                    paint.color = subTextColor
                    paint.textSize = 8f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("Observations: ", 42f, cardY, paint)

                    paint.color = textColor
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val obsLines = wrapText(obs, paint, 430f).take(2)
                    obsLines.forEachIndexed { i, l ->
                        canvas.drawText(l, if (i == 0) 108f else 42f, cardY, paint)
                        if (i < obsLines.size - 1) cardY += 10f
                    }
                }

                y = cardBottom + 6f
            }
        }

        // Remarks Section (if provided)
        if (remarks.isNotBlank()) {
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
            wrapText(remarks, paint, 530f).forEach { l ->
                canvas.drawText(l, 30f, y, paint)
                y += 10f
            }
            y += 8f
        }

        // Prepared By & Sign-Off Section (always at bottom of last page)
        val sigAreaY = 745f
        if (y > sigAreaY - 20f) {
            // Need an additional page for signatures if content reaches bottom
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            drawPageHeader(isFirstPage = false)
        }

        // Signature Lines
        val lineY = sigAreaY + 36f
        paint.color = tableBorderColor
        paint.strokeWidth = 1f
        canvas.drawLine(40f, lineY, 220f, lineY, paint)
        canvas.drawLine(360f, lineY, 540f, lineY, paint)

        // Draw Signature Bitmap if available
        if (signatureBitmap != null) {
            try {
                val targetSigW = 120f
                val targetSigH = 34f
                val sigAspect = signatureBitmap.width.toFloat() / signatureBitmap.height.toFloat()
                val actualW: Float
                val actualH: Float
                if (sigAspect >= (targetSigW / targetSigH)) {
                    actualW = targetSigW
                    actualH = (targetSigW / sigAspect).coerceAtMost(targetSigH)
                } else {
                    actualH = targetSigH
                    actualW = (targetSigH * sigAspect).coerceAtMost(targetSigW)
                }
                val sigDest = RectF(50f, lineY - actualH - 2f, 50f + actualW, lineY - 2f)
                canvas.drawBitmap(signatureBitmap, null, sigDest, paint)
            } catch (e: Exception) {
                // Ignore signature render failure
            }
        }

        // Signatory Names
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
        val cleanDate = dateString.replace("-", "")
        val cleanProject = project.id.replace(Regex("[^a-zA-Z0-9]"), "")
        val pdfFile = File(context.cacheDir, "DPR_${cleanProject}_${cleanDate}_${System.currentTimeMillis() % 10000}.pdf")
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
            putExtra(Intent.EXTRA_SUBJECT, "Highway Daily Project Report - ${pdfFile.nameWithoutExtension}")
            putExtra(Intent.EXTRA_TEXT, "Attached is the official Highway Daily Project Report (DPR): ${pdfFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Daily Project Report (PDF)"))
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
