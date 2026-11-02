package com.example.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import com.example.geo.GeoUtils
import com.example.model.CaptureSnapshot
import com.example.model.GeoPoint
import com.example.model.LogoPosition
import com.example.model.CoordinateFormat
import com.example.model.OverlayConfig
import com.example.model.OverlayTemplate
import com.example.model.RouteData
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

object OverlayRenderer {

    /**
     * Renders permanent overlay (text block, mini-map, and optional logo) directly onto the bitmap.
     * Returns a new bitmap with the overlay drawn at native resolution.
     */
    fun renderOverlay(
        context: Context,
        sourceBitmap: Bitmap,
        snapshot: CaptureSnapshot
    ): Bitmap {
        val resultBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        drawOverlayOnCanvas(context, canvas, sourceBitmap.width, sourceBitmap.height, snapshot)
        return resultBitmap
    }

    /**
     * Draws permanent overlay directly onto the provided Canvas without allocating a new Bitmap copy.
     * Highly optimized for video frame transcoding and live rendering pipelines.
     */
    fun drawOverlayOnCanvas(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        snapshot: CaptureSnapshot
    ) {
        val config = snapshot.overlayConfig
        val project = snapshot.projectProfile
        val location = snapshot.location

        // Scale factors relative to 1080p height
        val scale = max(width, height) / 1920f
        val marginX = (width * 0.042f).coerceIn(24f * scale, 64f * scale)
        val marginY = (height * 0.048f).coerceIn(28f * scale, 68f * scale)

        // 1. Calculate and Render Lower-Left Mini-Map Inset (if enabled)
        val showMiniMap = config.showMapInset && config.template != OverlayTemplate.MINIMAL_TIMESTAMP
        val mapSize = if (showMiniMap) {
            val mapRatio = if (width > height) 0.25f else 0.21f
            (min(width, height) * mapRatio).coerceIn(150f * scale, 340f * scale)
        } else 0f

        val mapRect: RectF? = if (showMiniMap) {
            RectF(
                marginX,
                height - marginY - mapSize,
                marginX + mapSize,
                height - marginY
            )
        } else null

        if (mapRect != null) {
            renderMiniMap(
                context = context,
                canvas = canvas,
                rect = mapRect,
                scale = scale,
                location = location,
                bearing = snapshot.bearing,
                routeData = snapshot.activeRoute,
                roadName = project.highwayId.ifBlank { "NH-58" }
            )
        }

        // 2. Render Information Block (passing mapRect so it never overlaps the map and wraps cleanly)
        val infoBlockTopY = renderInformationBlock(
            canvas = canvas,
            width = width,
            height = height,
            scale = scale,
            marginX = marginX,
            marginY = marginY,
            snapshot = snapshot,
            mapInsetRect = mapRect
        )

        // 3. Render Custom Logo / Image Overlay with full manual settings
        if (config.showLogo) {
            renderLogo(
                context = context,
                canvas = canvas,
                width = width,
                height = height,
                scale = scale,
                config = config,
                infoBlockTopY = infoBlockTopY,
                mapInsetRect = mapRect
            )
        }
    }

    private fun resolveTypeface(fontFamily: String, fontStyle: String): Typeface {
        val style = when (fontStyle.lowercase()) {
            "bold" -> Typeface.BOLD
            "italic" -> Typeface.ITALIC
            "medium" -> Typeface.BOLD
            else -> Typeface.NORMAL
        }
        val family = when (fontFamily.lowercase()) {
            "monospace" -> Typeface.MONOSPACE
            "serif" -> Typeface.SERIF
            "noto sans", "source sans pro", "sans-serif" -> Typeface.SANS_SERIF
            else -> Typeface.DEFAULT
        }
        return Typeface.create(family, style)
    }

    private fun renderInformationBlock(
        canvas: Canvas,
        width: Int,
        height: Int,
        scale: Float,
        marginX: Float,
        marginY: Float,
        snapshot: CaptureSnapshot,
        mapInsetRect: RectF? = null
    ): Float {
        val config = snapshot.overlayConfig
        val project = snapshot.projectProfile
        val location = snapshot.location
        val match = snapshot.matchResult

        val isLandscape = width > height
        val baseFontSize = if (isLandscape) {
            (config.fontSizeSp * 1.55f * scale).coerceIn(18f, 68f)
        } else {
            (config.fontSizeSp * 1.8f * scale).coerceIn(20f, 90f)
        }

        val customTypeface = resolveTypeface(config.fontFamily, config.fontStyle)

        val posLower = config.watermarkPosition.lowercase()
        val isLeft = posLower.contains("left")
        val isCenter = posLower.contains("center") && !posLower.contains("right") && !posLower.contains("left")
        val isTop = posLower.contains("top")

        val align = when {
            isLeft -> Paint.Align.LEFT
            isCenter -> Paint.Align.CENTER
            else -> Paint.Align.RIGHT
        }

        val baseColor = try { Color.parseColor(config.textColorHex) } catch (e: Exception) { Color.WHITE }
        val alphaVal = (config.textOpacity.coerceIn(0.1f, 1f) * 255).toInt()
        val textColorWithAlpha = Color.argb(alphaVal, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

        var currentFontSize = baseFontSize
        val lineSpacing = currentFontSize * 0.35f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorWithAlpha
            textSize = currentFontSize
            typeface = customTypeface
            textAlign = align
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((alphaVal * 0.85f).toInt(), 0, 0, 0)
            textSize = currentFontSize
            typeface = customTypeface
            textAlign = align
            style = Paint.Style.STROKE
            strokeWidth = (currentFontSize * (config.textStrokeWidthDp / 20f)).coerceAtLeast(2f * scale)
            strokeJoin = Paint.Join.ROUND
        }

        // Calculate available width for text to guarantee no clipping and no map overlap
        val minLeftBound = if (mapInsetRect != null && !isTop && !isLeft) {
            mapInsetRect.right + 20f * scale
        } else {
            marginX
        }
        val maxAvailableWidth = (width - marginX - minLeftBound).coerceAtLeast(width * 0.38f)

        val rawLines = OverlayTextGenerator.generateLines(
            config = config,
            project = project,
            location = location,
            accuracy = snapshot.gpsAccuracy,
            compassHeading = snapshot.compassHeading,
            speedMps = snapshot.speedMps,
            bearing = snapshot.bearing,
            chainageStr = match.displayChainage,
            cameraMode = com.example.model.CameraMode.HIGHWAY,
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestamp = snapshot.timestamp,
            isForCapture = true
        )

        if (rawLines.isEmpty()) return height - marginY

        // Auto-scale font size if lines are overly wide
        var maxMeasuredWidth = 0f
        for (line in rawLines) {
            val w = textPaint.measureText(line)
            if (w > maxMeasuredWidth) maxMeasuredWidth = w
        }

        if (maxMeasuredWidth > maxAvailableWidth) {
            val shrinkRatio = (maxAvailableWidth / maxMeasuredWidth).coerceIn(0.72f, 1.0f)
            currentFontSize = (baseFontSize * shrinkRatio).coerceAtLeast(14f * scale)
            textPaint.textSize = currentFontSize
            strokePaint.textSize = currentFontSize
            strokePaint.strokeWidth = (currentFontSize * (config.textStrokeWidthDp / 20f)).coerceAtLeast(2f * scale)
        }

        // Dynamically wrap any line that still exceeds available width
        val lines = mutableListOf<String>()
        for (line in rawLines) {
            if (textPaint.measureText(line) <= maxAvailableWidth) {
                lines.add(line)
            } else {
                lines.addAll(wrapText(line, textPaint, maxAvailableWidth))
            }
        }

        val targetX = when {
            isLeft -> marginX
            isCenter -> (minLeftBound + (width - marginX)) / 2f
            else -> width - marginX
        }

        val totalBlockHeight = lines.size * currentFontSize + (lines.size - 1) * lineSpacing

        // Background card panel if configured
        val showBgPanel = config.backgroundStyle != "Transparent" || config.backgroundOpacity > 0f
        if (showBgPanel) {
            val bgAlpha = if (config.backgroundStyle == "Solid") 220 else (config.backgroundOpacity.coerceIn(0.1f, 1f) * 255).toInt()
            val bgColor = try { Color.parseColor(config.backgroundColorHex) } catch (e: Exception) { Color.BLACK }
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(bgAlpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
                style = Paint.Style.FILL
            }

            var actualMaxLineWidth = 0f
            for (line in lines) {
                val w = textPaint.measureText(line)
                if (w > actualMaxLineWidth) actualMaxLineWidth = w
            }

            val padX = 14f * scale
            val padY = 10f * scale

            val bgRect = when {
                isLeft -> RectF(marginX - padX, if (isTop) marginY - padY else height - marginY - totalBlockHeight - padY, marginX + actualMaxLineWidth + padX, if (isTop) marginY + totalBlockHeight + padY else height - marginY + padY)
                isCenter -> RectF(targetX - actualMaxLineWidth / 2f - padX, if (isTop) marginY - padY else height - marginY - totalBlockHeight - padY, targetX + actualMaxLineWidth / 2f + padX, if (isTop) marginY + totalBlockHeight + padY else height - marginY + padY)
                else -> RectF(width - marginX - actualMaxLineWidth - padX, if (isTop) marginY - padY else height - marginY - totalBlockHeight - padY, width - marginX + padX, if (isTop) marginY + totalBlockHeight + padY else height - marginY + padY)
            }

            canvas.drawRoundRect(bgRect, 10f * scale, 10f * scale, bgPaint)
        }

        var currentY = if (isTop) marginY + currentFontSize else height - marginY

        if (isTop) {
            // Draw top to bottom
            for (line in lines) {
                if (config.hasTextShadow) {
                    canvas.drawText(line, targetX, currentY, strokePaint)
                }
                canvas.drawText(line, targetX, currentY, textPaint)
                currentY += (currentFontSize + lineSpacing)
            }
            return currentY
        } else {
            // Draw bottom to top
            for (i in lines.indices.reversed()) {
                val line = lines[i]
                if (config.hasTextShadow) {
                    canvas.drawText(line, targetX, currentY, strokePaint)
                }
                canvas.drawText(line, targetX, currentY, textPaint)
                currentY -= (currentFontSize + lineSpacing)
            }
            return currentY
        }
    }

    /**
     * Intelligently wraps text into multiple lines so that no line exceeds [maxWidth].
     * Prefers splitting on " | " separator first, then by spaces.
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val segments = if (text.contains(" | ")) {
            text.split(" | ")
        } else {
            listOf(text)
        }

        var currentLine = ""
        for (segment in segments) {
            val candidate = if (currentLine.isEmpty()) segment else "$currentLine | $segment"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine = candidate
            } else {
                if (currentLine.isNotEmpty()) {
                    result.add(currentLine)
                    currentLine = ""
                }
                if (paint.measureText(segment) <= maxWidth) {
                    currentLine = segment
                } else {
                    // Split segment by spaces
                    val words = segment.split(" ")
                    var subLine = ""
                    for (word in words) {
                        val wordCand = if (subLine.isEmpty()) word else "$subLine $word"
                        if (paint.measureText(wordCand) <= maxWidth) {
                            subLine = wordCand
                        } else {
                            if (subLine.isNotEmpty()) result.add(subLine)
                            subLine = word
                        }
                    }
                    if (subLine.isNotEmpty()) currentLine = subLine
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            result.add(currentLine)
        }
        return if (result.isEmpty()) listOf(text) else result
    }


    private fun renderMiniMap(
        context: Context,
        canvas: Canvas,
        rect: RectF,
        scale: Float,
        location: GeoPoint?,
        bearing: Float?,
        routeData: RouteData?,
        roadName: String
    ) {
        val cornerRadius = 16f * scale

        // Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6111827") // Dark slate with alpha
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // Clip to rounded rect for inner map contents
        canvas.save()
        val clipPath = Path()
        clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        val centerPoint = location ?: routeData?.bounds?.center
        var hasOsmTiles = false

        if (centerPoint != null) {
            val osmBmp = com.example.geo.OsmTileManager.renderMapBitmap(
                context = context,
                center = centerPoint,
                widthPx = rect.width().toInt().coerceAtLeast(1),
                heightPx = rect.height().toInt().coerceAtLeast(1),
                zoom = 15
            )
            if (osmBmp != null) {
                canvas.drawBitmap(osmBmp, rect.left, rect.top, Paint(Paint.FILTER_BITMAP_FLAG))
                val dimPaint = Paint().apply { color = Color.argb(45, 0, 0, 0) }
                canvas.drawRect(rect, dimPaint)
                hasOsmTiles = true
            }
        }

        // Subtle grid lines if OSM tiles not available
        if (!hasOsmTiles) {
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(40, 255, 255, 255)
                strokeWidth = 1f * scale
            }
            val step = rect.width() / 4f
            for (i in 1..3) {
                val gx = rect.left + i * step
                canvas.drawLine(gx, rect.top, gx, rect.bottom, gridPaint)
                val gy = rect.top + i * step
                canvas.drawLine(rect.left, gy, rect.right, gy, gridPaint)
            }
        }

        // Draw road alignment if available
        if (centerPoint != null) {
            fun geoToScreen(pt: GeoPoint): Pair<Float, Float> {
                val (sx, sy) = com.example.geo.OsmTileManager.geoToScreen(
                    pt = pt,
                    center = centerPoint,
                    widthPx = rect.width(),
                    heightPx = rect.height(),
                    zoom = 15
                )
                return Pair(rect.left + sx, rect.top + sy)
            }

            // Route alignments
            val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8") // Sky blue highway centerline
                strokeWidth = 5f * scale
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val roadOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0284C7")
                strokeWidth = 9f * scale
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            if (routeData != null && routeData.alignments.isNotEmpty()) {
                for (align in routeData.alignments) {
                    if (align.points.size < 2) continue
                    val path = Path()
                    var started = false
                    for (pt in align.points) {
                        val (sx, sy) = geoToScreen(pt)
                        if (!started) {
                            path.moveTo(sx, sy)
                            started = true
                        } else {
                            path.lineTo(sx, sy)
                        }
                    }
                    canvas.drawPath(path, roadOuterPaint)
                    canvas.drawPath(path, roadPaint)
                }

                // Nearby chainage marker dots
                val markerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FBBF24") // Amber dots
                    style = Paint.Style.FILL
                }
                for (m in routeData.markers) {
                    val (sx, sy) = geoToScreen(m.point)
                    if (sx in rect.left..rect.right && sy in rect.top..rect.bottom) {
                        canvas.drawCircle(sx, sy, 3f * scale, markerDotPaint)
                    }
                }
            } else if (!hasOsmTiles) {
                // Draw a stylized curved highway representation when no route and no tile is loaded
                val genericPath = Path()
                genericPath.moveTo(rect.left, rect.bottom - 20f * scale)
                genericPath.cubicTo(
                    rect.left + rect.width() * 0.4f, rect.bottom - 40f * scale,
                    rect.left + rect.width() * 0.6f, rect.top + 50f * scale,
                    rect.right, rect.top + 30f * scale
                )
                canvas.drawPath(genericPath, roadOuterPaint)
                canvas.drawPath(genericPath, roadPaint)
            }

            // Draw GPS pin at center
            val pinRadius = 6f * scale
            val pulseRadius = 14f * scale

            val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 244, 63, 94) // Red glow
                style = Paint.Style.FILL
            }
            canvas.drawCircle(rect.centerX(), rect.centerY(), pulseRadius, pulsePaint)

            val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#EF4444")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(rect.centerX(), rect.centerY(), pinRadius, pinPaint)

            val innerPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(rect.centerX(), rect.centerY(), pinRadius * 0.4f, innerPinPaint)

            // Bearing arrow
            if (bearing != null) {
                canvas.save()
                canvas.rotate(bearing, rect.centerX(), rect.centerY())
                val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#38BDF8")
                    style = Paint.Style.FILL
                }
                val arrowPath = Path().apply {
                    moveTo(rect.centerX(), rect.centerY() - pulseRadius - 4f * scale)
                    lineTo(rect.centerX() - 5f * scale, rect.centerY() - pulseRadius + 6f * scale)
                    lineTo(rect.centerX() + 5f * scale, rect.centerY() - pulseRadius + 6f * scale)
                    close()
                }
                canvas.drawPath(arrowPath, arrowPaint)
                canvas.restore()
            }
        }

        // OpenStreetMap Attribution (Top-Right)
        val osmAttrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 255, 255)
            textSize = 8.5f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("© OSM", rect.right - 8f * scale, rect.top + 13f * scale, osmAttrPaint)

        // Road / Map tag at bottom of inset
        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 15, 23, 42)
            style = Paint.Style.FILL
        }
        val tagRect = RectF(rect.left, rect.bottom - 22f * scale, rect.right, rect.bottom)
        canvas.drawRect(tagRect, tagBgPaint)

        val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(roadName, rect.centerX(), rect.bottom - 6f * scale, tagTextPaint)

        canvas.restore()
    }

    private fun renderLogo(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        scale: Float,
        config: OverlayConfig,
        infoBlockTopY: Float,
        mapInsetRect: RectF?
    ) {
        try {
            val targetWidthPx = (config.logoSizeDp * 3.2f * scale).toInt().coerceIn(60, (width * 0.45f).toInt())
            val processedLogo = LogoProvider.getProcessedLogo(context, config, targetWidthPx) ?: return

            val logoWidth = processedLogo.width.toFloat()
            val logoHeight = processedLogo.height.toFloat()

            val customMarginX = (config.logoMarginXDp * scale).coerceAtLeast(8f * scale)
            val customMarginY = (config.logoMarginYDp * scale).coerceAtLeast(8f * scale)

            val (posX, posY) = when (config.logoPosition) {
                LogoPosition.TOP_RIGHT -> {
                    Pair(width - customMarginX - logoWidth, customMarginY)
                }
                LogoPosition.TOP_LEFT -> {
                    Pair(customMarginX, customMarginY)
                }
                LogoPosition.BOTTOM_LEFT -> {
                    val bottomY = if (mapInsetRect != null) {
                        mapInsetRect.top - customMarginY - logoHeight
                    } else {
                        height - customMarginY - logoHeight
                    }
                    Pair(customMarginX, bottomY.coerceAtLeast(customMarginY))
                }
                LogoPosition.BOTTOM_RIGHT -> {
                    Pair(width - customMarginX - logoWidth, height - customMarginY - logoHeight)
                }
                LogoPosition.ABOVE_TEXT -> {
                    Pair(width - customMarginX - logoWidth, (infoBlockTopY - customMarginY - logoHeight).coerceAtLeast(customMarginY))
                }
                LogoPosition.TOP_CENTER -> {
                    Pair((width - logoWidth) / 2f, customMarginY)
                }
                LogoPosition.BOTTOM_CENTER -> {
                    Pair((width - logoWidth) / 2f, height - customMarginY - logoHeight)
                }
                LogoPosition.CENTER -> {
                    Pair((width - logoWidth) / 2f, (height - logoHeight) / 2f)
                }
            }

            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (config.logoOpacity.coerceIn(0.1f, 1f) * 255).toInt()
            }

            canvas.drawBitmap(processedLogo, posX, posY, logoPaint)
        } catch (ignored: Exception) {}
    }

    /**
     * Saves bitmap atomically to destination file and writes standard EXIF metadata.
     */
    fun saveBitmapWithExif(
        bitmap: Bitmap,
        destinationFile: File,
        snapshot: CaptureSnapshot,
        jpegQuality: Int = 95,
        context: Context? = null
    ): Boolean {
        destinationFile.parentFile?.mkdirs()
        val tempFile = try {
            if (destinationFile.parentFile?.canWrite() == true) {
                File(destinationFile.parentFile, "${destinationFile.name}.tmp")
            } else {
                File.createTempFile("stamp_", ".tmp", context?.cacheDir)
            }
        } catch (e: Exception) {
            try {
                File.createTempFile("stamp_", ".tmp", context?.cacheDir)
            } catch (e2: Exception) {
                File(destinationFile.absolutePath + ".tmp")
            }
        }

        try {
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                out.flush()
            }

            // Write EXIF tags
            try {
                val exif = ExifInterface(tempFile.absolutePath)
                val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                val dateStr = sdf.format(Date(snapshot.timestamp))
                exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)

                val loc = snapshot.location
                if (loc != null) {
                    setGpsExif(exif, loc)
                }

                // Orientation metadata: since the bitmap itself is already upright, tag as NORMAL
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.setAttribute(ExifInterface.TAG_MAKE, android.os.Build.MANUFACTURER)
                exif.setAttribute(ExifInterface.TAG_MODEL, android.os.Build.MODEL)
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Highway Timestamp Camera Pro")

                if (snapshot.compassAzimuth > 0f) {
                    exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, "${snapshot.compassAzimuth.toInt()}/1")
                    exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M")
                }

                val userComment = "${snapshot.projectProfile.highwayId} ${snapshot.projectProfile.packageSection} ${snapshot.displayChainage} ${snapshot.projectProfile.roadSide.name} ${snapshot.projectProfile.lane} ${snapshot.projectProfile.activity}"
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, userComment)

                exif.saveAttributes()
            } catch (ignored: Exception) {}

            // Atomic rename or copy
            if (tempFile.renameTo(destinationFile)) {
                return true
            } else {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
                return true
            }
        } catch (e: Exception) {
            try { tempFile.delete() } catch (ignored: Exception) {}
            return false
        }
    }

    private fun setGpsExif(exif: ExifInterface, loc: GeoPoint) {
        val lat = loc.latitude
        val lng = loc.longitude

        val latRef = if (lat >= 0) "N" else "S"
        val lngRef = if (lng >= 0) "E" else "W"

        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lngRef)
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, decimalToDms(abs(lat)))
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, decimalToDms(abs(lng)))

        if (loc.altitude != 0.0) {
            val altRef = if (loc.altitude >= 0) "0" else "1"
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, altRef)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${abs(loc.altitude.toInt())}/1")
        }
    }

    private fun decimalToDms(coord: Double): String {
        val degrees = coord.toInt()
        val minutesDouble = (coord - degrees) * 60.0
        val minutes = minutesDouble.toInt()
        val seconds = (minutesDouble - minutes) * 60.0 * 1000.0
        return "$degrees/1,$minutes/1,${seconds.toInt()}/1000"
    }
}
