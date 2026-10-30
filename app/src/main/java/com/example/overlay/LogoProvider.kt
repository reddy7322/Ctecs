package com.example.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import com.example.model.LogoPosition
import com.example.model.LogoShape
import com.example.model.OverlayConfig
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object LogoProvider {

    /**
     * Resolves and returns the styled logo bitmap for drawing onto photos or canvas.
     */
    fun getProcessedLogo(
        context: Context,
        config: OverlayConfig,
        targetWidthPx: Int
    ): Bitmap? {
        val rawBitmap = loadRawLogoBitmap(context, config, targetWidthPx) ?: return null
        return processLogoBitmap(rawBitmap, config, targetWidthPx)
    }

    /**
     * Loads raw logo bitmap either from custom user URI or generates preset.
     */
    fun loadRawLogoBitmap(
        context: Context,
        config: OverlayConfig,
        targetWidthPx: Int
    ): Bitmap? {
        if (config.logoPreset == "custom" && !config.logoUri.isNullOrBlank()) {
            val customBitmap = loadCustomImage(context, config.logoUri)
            if (customBitmap != null) {
                return customBitmap
            }
        }

        // Return or generate preset bitmap
        return when (config.logoPreset.lowercase()) {
            "nhai" -> generateNhaiPreset(targetWidthPx)
            "survey_seal" -> generateSurveySealPreset(targetWidthPx)
            else -> generateCtecsPreset(targetWidthPx) // Default "ctecs"
        }
    }

    private fun loadCustomImage(context: Context, uriString: String): Bitmap? {
        return try {
            val file = File(uriString)
            if (file.exists() && file.isFile && file.length() > 0) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) return bitmap
            }
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val f = File(uri.path ?: uriString)
                if (f.exists()) {
                    BitmapFactory.decodeFile(f.absolutePath)
                } else null
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            null
        }
    }


    /**
     * Shapes and borders the bitmap according to user config.
     */
    fun processLogoBitmap(
        source: Bitmap,
        config: OverlayConfig,
        targetWidthPx: Int
    ): Bitmap {
        val aspect = source.height.toFloat() / source.width.toFloat()
        val targetHeightPx = (targetWidthPx * aspect).toInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(source, targetWidthPx, targetHeightPx, true)

        val output = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, targetWidthPx.toFloat(), targetHeightPx.toFloat())

        when (config.logoShape) {
            LogoShape.CIRCLE -> {
                val radius = min(targetWidthPx, targetHeightPx) / 2f
                val cx = targetWidthPx / 2f
                val cy = targetHeightPx / 2f
                canvas.drawCircle(cx, cy, radius, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(scaled, 0f, 0f, paint)
                paint.xfermode = null

                // Draw border if requested
                if (config.logoBorderWidthDp > 0) {
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = (config.logoBorderWidthDp * (targetWidthPx / 100f)).coerceAtLeast(2f)
                        color = try { Color.parseColor(config.logoBorderColorHex) } catch (e: Exception) { Color.WHITE }
                    }
                    canvas.drawCircle(cx, cy, radius - borderPaint.strokeWidth / 2f, borderPaint)
                }
            }
            LogoShape.ROUNDED_CORNER -> {
                val cornerRadius = (config.logoCornerRadiusDp * (targetWidthPx / 80f)).coerceAtLeast(4f)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(scaled, 0f, 0f, paint)
                paint.xfermode = null

                // Draw border if requested
                if (config.logoBorderWidthDp > 0) {
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = (config.logoBorderWidthDp * (targetWidthPx / 100f)).coerceAtLeast(2f)
                        color = try { Color.parseColor(config.logoBorderColorHex) } catch (e: Exception) { Color.WHITE }
                    }
                    val insetRect = RectF(rect).apply {
                        inset(borderPaint.strokeWidth / 2f, borderPaint.strokeWidth / 2f)
                    }
                    canvas.drawRoundRect(insetRect, cornerRadius, cornerRadius, borderPaint)
                }
            }
            LogoShape.ORIGINAL -> {
                canvas.drawBitmap(scaled, 0f, 0f, paint)
                if (config.logoBorderWidthDp > 0) {
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = (config.logoBorderWidthDp * (targetWidthPx / 100f)).coerceAtLeast(2f)
                        color = try { Color.parseColor(config.logoBorderColorHex) } catch (e: Exception) { Color.WHITE }
                    }
                    val insetRect = RectF(rect).apply {
                        inset(borderPaint.strokeWidth / 2f, borderPaint.strokeWidth / 2f)
                    }
                    canvas.drawRect(insetRect, borderPaint)
                }
            }
        }

        return output
    }

    /**
     * Copies picked custom image to internal storage and returns file URI.
     */
    fun saveCustomLogoToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val destinationFile = File(context.filesDir, "custom_logo_stamped.png")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destinationFile).toString()
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================================
    // BUILT-IN VECTOR/CANVAS LOGO PRESETS
    // =========================================================================



    /**
     * Generates CTECS PRO logo:
     * Highway divider shield + "CTECS PRO" + "Highways & Survey Documentation"
     */
    fun generateCtecsPreset(width: Int): Bitmap {
        val height = (width * 0.40f).toInt().coerceAtLeast(60)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = width / 360f

        val iconSize = height * 0.78f
        val iconX = height * 0.12f
        val iconY = (height - iconSize) / 2f

        // Blue / Cyan Shield
        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            style = Paint.Style.FILL
        }
        val shieldPath = Path().apply {
            moveTo(iconX + iconSize / 2f, iconY)
            lineTo(iconX + iconSize, iconY + iconSize * 0.25f)
            lineTo(iconX + iconSize * 0.85f, iconY + iconSize * 0.85f)
            lineTo(iconX + iconSize / 2f, iconY + iconSize)
            lineTo(iconX + iconSize * 0.15f, iconY + iconSize * 0.85f)
            lineTo(iconX, iconY + iconSize * 0.25f)
            close()
        }
        canvas.drawPath(shieldPath, shieldPaint)

        // Road stripes inside shield
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3f * scale
            style = Paint.Style.STROKE
        }
        canvas.drawLine(iconX + iconSize / 2f, iconY + iconSize * 0.2f, iconX + iconSize / 2f, iconY + iconSize * 0.8f, stripePaint)

        val textStartX = iconX + iconSize + 12f * scale
        val titleY = iconY + iconSize * 0.55f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 0, 0, 0)
            textSize = 28f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
        }
        canvas.drawText("CTECS PRO", textStartX, titleY, shadowPaint)
        canvas.drawText("CTECS PRO", textStartX, titleY, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 12f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Highway & Chainage Camera", textStartX, titleY + 16f * scale, subPaint)

        return bitmap
    }

    /**
     * Generates NHAI (National Highways Authority) road authority emblem.
     */
    fun generateNhaiPreset(width: Int): Bitmap {
        val height = (width * 0.40f).toInt().coerceAtLeast(60)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = width / 360f

        val iconSize = height * 0.78f
        val iconX = height * 0.12f
        val iconY = (height - iconSize) / 2f
        val radius = iconSize / 2f

        // Orange / Saffron circle
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EA580C")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(iconX + radius, iconY + radius, radius, bgPaint)

        // Road wedge
        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val roadPath = Path().apply {
            moveTo(iconX + radius, iconY + radius * 0.3f)
            lineTo(iconX + radius + radius * 0.6f, iconY + radius * 1.7f)
            lineTo(iconX + radius - radius * 0.6f, iconY + radius * 1.7f)
            close()
        }
        canvas.drawPath(roadPath, roadPaint)

        val textStartX = iconX + iconSize + 12f * scale
        val titleY = iconY + iconSize * 0.55f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 0, 0, 0)
            textSize = 28f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
        }
        canvas.drawText("NHAI", textStartX, titleY, shadowPaint)
        canvas.drawText("NHAI", textStartX, titleY, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDBA74")
            textSize = 12f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText("National Highways Authority", textStartX, titleY + 16f * scale, subPaint)

        return bitmap
    }

    /**
     * Generates Survey & QC Approved circular badge.
     */
    fun generateSurveySealPreset(width: Int): Bitmap {
        val size = width.coerceAtLeast(100)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = size / 200f
        val center = size / 2f
        val radius = center - 6f * scale

        // Seal Outer Ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#059669") // Emerald
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, ringPaint)

        // Inner Circle
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#065F46")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius * 0.85f, innerPaint)

        // Gold Ring border
        val goldBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FBBF24")
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
        }
        canvas.drawCircle(center, center, radius * 0.85f, goldBorder)

        // Center Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("QUALITY", center, center - 6f * scale, textPaint)
        canvas.drawText("APPROVED", center, center + 14f * scale, textPaint)

        val sealTag = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDE68A")
            textSize = 9f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("★ HIGHWAY QC ★", center, center + 32f * scale, sealTag)

        return bitmap
    }
}
