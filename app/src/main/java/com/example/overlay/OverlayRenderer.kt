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
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val resultBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val config = snapshot.overlayConfig
        val project = snapshot.projectProfile
        val location = snapshot.location
        val match = snapshot.matchResult

        // Scale factors relative to 1080p height
        val scale = max(width, height) / 1920f
        val marginX = (width * 0.04f).coerceAtLeast(32f * scale)
        val marginY = (height * 0.05f).coerceAtLeast(36f * scale)

        // 1. Render Lower-Right Information Block (returns top Y coordinate of block)
        val infoBlockTopY = renderInformationBlock(
            canvas = canvas,
            width = width,
            height = height,
            scale = scale,
            marginX = marginX,
            marginY = marginY,
            snapshot = snapshot
        )

        // 2. Render Lower-Left Mini-Map Inset
        var mapRect: RectF? = null
        if (config.showMapInset && config.template != OverlayTemplate.MINIMAL_TIMESTAMP) {
            val mapSize = (min(width, height) * 0.22f).coerceIn(160f * scale, 340f * scale)
            val rect = RectF(
                marginX,
                height - marginY - mapSize,
                marginX + mapSize,
                height - marginY
            )
            mapRect = rect
            renderMiniMap(
                canvas = canvas,
                rect = rect,
                scale = scale,
                location = location,
                bearing = snapshot.bearing,
                routeData = snapshot.activeRoute,
                roadName = project.highwayId.ifBlank { "NH-58" }
            )
        }

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

        return resultBitmap
    }

    private fun renderInformationBlock(
        canvas: Canvas,
        width: Int,
        height: Int,
        scale: Float,
        marginX: Float,
        marginY: Float,
        snapshot: CaptureSnapshot
    ): Float {
        val config = snapshot.overlayConfig
        val project = snapshot.projectProfile
        val location = snapshot.location
        val match = snapshot.matchResult

        val isLandscape = width > height
        val baseFontSize = if (isLandscape) {
            (config.fontSizeSp * 1.55f * scale).coerceIn(20f, 62f)
        } else {
            (config.fontSizeSp * 1.8f * scale).coerceIn(24f, 84f)
        }
        val lineSpacing = baseFontSize * 0.35f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = try { Color.parseColor(config.textColorHex) } catch (e: Exception) { Color.WHITE }
            textSize = baseFontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 0, 0, 0)
            textSize = baseFontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            style = Paint.Style.STROKE
            strokeWidth = (baseFontSize * 0.12f).coerceAtLeast(3f)
            strokeJoin = Paint.Join.ROUND
        }

        val lines = OverlayTextGenerator.generateLines(
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

        val rightX = width - marginX
        var currentY = height - marginY

        // Draw lines from bottom to top
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            if (config.hasTextShadow) {
                canvas.drawText(line, rightX, currentY, strokePaint)
            }
            canvas.drawText(line, rightX, currentY, textPaint)
            currentY -= (baseFontSize + lineSpacing)
        }

        return currentY
    }

    private fun renderMiniMap(
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

        // Subtle grid lines
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

        // Draw road alignment if available
        val centerPoint = location ?: routeData?.bounds?.center
        if (centerPoint != null) {
            val viewRangeMetres = 1200.0 // 1.2km view span
            val midLat = Math.toRadians(centerPoint.latitude)
            val cosMidLat = cos(midLat)
            val metresPerPixel = viewRangeMetres / rect.width()

            fun geoToScreen(pt: GeoPoint): Pair<Float, Float> {
                val dxMetres = Math.toRadians(pt.longitude - centerPoint.longitude) * GeoUtils.EARTH_RADIUS_METRES * cosMidLat
                val dyMetres = Math.toRadians(pt.latitude - centerPoint.latitude) * GeoUtils.EARTH_RADIUS_METRES
                val sx = (rect.centerX() + (dxMetres / metresPerPixel)).toFloat()
                val sy = (rect.centerY() - (dyMetres / metresPerPixel)).toFloat()
                return Pair(sx, sy)
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

            if (routeData != null) {
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
                    val dist = GeoUtils.distanceMetres(m.point, centerPoint)
                    if (dist < viewRangeMetres) {
                        val (sx, sy) = geoToScreen(m.point)
                        canvas.drawCircle(sx, sy, 3f * scale, markerDotPaint)
                    }
                }
            } else {
                // Draw a stylized curved highway representation when no route is loaded
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
                    color = Color.parseColor("#EF4444")
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

        // Road / Map tag at bottom of inset
        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 15, 23, 42)
            style = Paint.Style.FILL
        }
        val tagRect = RectF(rect.left, rect.bottom - 22f * scale, rect.right, rect.bottom)
        canvas.drawRect(tagRect, tagBgPaint)

        val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
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
