package com.example.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Rational
import com.example.model.CameraOutputFormat
import com.example.model.OutputQualityPreset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure normalized framing rectangle (0f..1f in container space) for viewfinder preview accuracy.
 */
data class FramingPreviewRect(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val isCropped: Boolean,
    val targetAspectRatio: Float
)

/**
 * Human-readable output resolution and estimated file size summary shown in Camera Settings.
 */
data class OutputEstimateSummary(
    val widthPx: Int,
    val heightPx: Int,
    val resolutionLabel: String,
    val detailLabel: String,
    val estimatedFileSize: String
)

/**
 * Authoritative Output Format Engine for CTECS Camera Photo & Video modes.
 *
 * Implements the 4 simple output formats without stretching, distortion, or overlay clipping:
 * 1. [CameraOutputFormat.LANDSCAPE_FULL] – Wide horizontal output preserving full natural aspect ratio.
 * 2. [CameraOutputFormat.PORTRAIT_FULL] – Full-height vertical output with maximum vertical framing.
 * 3. [CameraOutputFormat.SQUARE] – Smart 1:1 center-cropped square framing.
 * 4. [CameraOutputFormat.RECTANGULAR_CROP] – Smart 4:3 (landscape) or 3:4 (portrait) rectangular crop.
 */
object CameraOutputFormatEngine {

    private const val TAG = "CameraOutputFormat"

    /**
     * Resolves the desired target width/height aspect ratio for viewfinder preview and cropping.
     *
     * @param format Active photo or video output format.
     * @param isDeviceLandscape Current device orientation (true when held horizontally).
     * @param naturalSensorRatio Native sensor long/short ratio (typically 4/3 ≈ 1.333 or 16/9 ≈ 1.778).
     */
    fun resolveTargetAspectRatio(
        format: CameraOutputFormat,
        isDeviceLandscape: Boolean,
        naturalSensorRatio: Float = 4f / 3f
    ): Float {
        val safeNatural = naturalSensorRatio.coerceIn(1.2f, 2.2f)
        return when (format) {
            CameraOutputFormat.LANDSCAPE_FULL -> safeNatural.coerceAtLeast(16f / 9f.coerceAtMost(safeNatural))
            CameraOutputFormat.PORTRAIT_FULL -> 1f / safeNatural
            CameraOutputFormat.SQUARE -> 1.0f
            CameraOutputFormat.RECTANGULAR_CROP -> if (isDeviceLandscape) 4f / 3f else 3f / 4f
        }
    }

    /**
     * Resolves CameraX [Rational] aspect ratio for ViewPort / VideoCapture alignment.
     */
    fun resolveCameraXViewPortRational(
        format: CameraOutputFormat,
        isDeviceLandscape: Boolean
    ): Rational {
        return when (format) {
            CameraOutputFormat.LANDSCAPE_FULL -> Rational(16, 9)
            CameraOutputFormat.PORTRAIT_FULL -> Rational(9, 16)
            CameraOutputFormat.SQUARE -> Rational(1, 1)
            CameraOutputFormat.RECTANGULAR_CROP -> if (isDeviceLandscape) Rational(4, 3) else Rational(3, 4)
        }
    }

    /**
     * Computes exact viewfinder preview guide bounds inside `[containerWidth x containerHeight]`
     * so the live camera viewfinder accurately previews the final captured framing.
     */
    fun computeFramingPreviewRect(
        format: CameraOutputFormat,
        isDeviceLandscape: Boolean,
        containerWidth: Float,
        containerHeight: Float
    ): FramingPreviewRect {
        if (containerWidth <= 1f || containerHeight <= 1f) {
            return FramingPreviewRect(0f, 0f, containerWidth, containerHeight, false, 1f)
        }

        val currentRatio = containerWidth / containerHeight
        val targetRatio = when (format) {
            CameraOutputFormat.LANDSCAPE_FULL -> if (isDeviceLandscape) currentRatio else 16f / 9f
            CameraOutputFormat.PORTRAIT_FULL -> if (!isDeviceLandscape) currentRatio else 9f / 16f
            CameraOutputFormat.SQUARE -> 1.0f
            CameraOutputFormat.RECTANGULAR_CROP -> if (isDeviceLandscape) 4f / 3f else 3f / 4f
        }

        if (abs(currentRatio - targetRatio) < 0.025f) {
            return FramingPreviewRect(
                leftPx = 0f,
                topPx = 0f,
                widthPx = containerWidth,
                heightPx = containerHeight,
                isCropped = false,
                targetAspectRatio = targetRatio
            )
        }

        val frameW: Float
        val frameH: Float
        if (currentRatio > targetRatio) {
            frameH = containerHeight
            frameW = (containerHeight * targetRatio).coerceAtMost(containerWidth)
        } else {
            frameW = containerWidth
            frameH = (containerWidth / targetRatio).coerceAtMost(containerHeight)
        }

        val left = ((containerWidth - frameW) / 2f).coerceAtLeast(0f)
        val top = ((containerHeight - frameH) / 2f).coerceAtLeast(0f)
        return FramingPreviewRect(
            leftPx = left,
            topPx = top,
            widthPx = frameW,
            heightPx = frameH,
            isCropped = true,
            targetAspectRatio = targetRatio
        )
    }

    /**
     * Applies the selected [CameraOutputFormat] to a captured photo [Bitmap]:
     * - Preserves full native sensor resolution wherever possible.
     * - Uses intelligent center cropping without stretching or distorting subjects.
     * - Runs strictly BEFORE overlay burn-in so all timestamp/GPS/chainage/weather/logo/minimap
     *   overlays are rendered cleanly inside the final output dimensions without ever cropping or shifting.
     */
    fun applyOutputFormatToBitmap(
        source: Bitmap,
        format: CameraOutputFormat,
        isDeviceLandscape: Boolean
    ): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source

        // Step 1: Ensure natural orientation matches Landscape-Full vs Portrait-Full explicitly
        val oriented = when (format) {
            CameraOutputFormat.LANDSCAPE_FULL -> {
                // Full wide horizontal framing: if bitmap is portrait (h > w), center-crop to natural wide 16:9
                // without rotating upright subject sideways!
                if (w >= h) {
                    return source // Already full-width horizontal preserving natural sensor aspect ratio!
                } else {
                    cropCenterToAspectRatio(source, 16f / 9f)
                }
            }
            CameraOutputFormat.PORTRAIT_FULL -> {
                // Full-height vertical framing: if already portrait (h >= w), preserve full natural height & width!
                if (h >= w) {
                    return source
                } else {
                    cropCenterToAspectRatio(source, 9f / 16f)
                }
            }
            CameraOutputFormat.SQUARE -> {
                cropCenterToAspectRatio(source, 1.0f)
            }
            CameraOutputFormat.RECTANGULAR_CROP -> {
                val targetRatio = if (isDeviceLandscape) 4f / 3f else 3f / 4f
                cropCenterToAspectRatio(source, targetRatio)
            }
        }
        return oriented
    }

    /**
     * Pure center crop to [targetAspectRatio] (`width / height`) preserving maximum resolution
     * with zero stretching or distortion.
     */
    fun cropCenterToAspectRatio(source: Bitmap, targetAspectRatio: Float): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0 || !targetAspectRatio.isFinite() || targetAspectRatio <= 0f) {
            return source
        }
        val currentRatio = w.toFloat() / h.toFloat()
        if (abs(currentRatio - targetAspectRatio) < 0.015f) {
            return source
        }

        val targetW: Int
        val targetH: Int
        if (currentRatio > targetAspectRatio) {
            targetH = h
            targetW = (h * targetAspectRatio).roundToInt().coerceIn(2, w)
        } else {
            targetW = w
            targetH = (w / targetAspectRatio).roundToInt().coerceIn(2, h)
        }
        // Keep even dimensions for clean codec/JPEG MCU alignment
        val evenW = (targetW and 1.inv()).coerceAtLeast(2).coerceAtMost(w)
        val evenH = (targetH and 1.inv()).coerceAtLeast(2).coerceAtMost(h)
        val startX = ((w - evenW) / 2).coerceIn(0, w - evenW)
        val startY = ((h - evenH) / 2).coerceIn(0, h - evenH)

        return try {
            Bitmap.createBitmap(source, startX, startY, evenW, evenH)
        } catch (e: Throwable) {
            Log.e(TAG, "Center crop failed ($evenW x $evenH): ${e.message}", e)
            source
        }
    }

    /**
     * Parses raw dimensions from a resolution string such as `"4000 × 3000"` or `"1920 x 1080"`.
     */
    fun parseResolutionPair(resolutionStr: String, fallbackW: Int = 1920, fallbackH: Int = 1080): Pair<Int, Int> {
        val parts = resolutionStr.split("x", "×").mapNotNull { it.trim().toIntOrNull() }
        return if (parts.size == 2 && parts[0] > 0 && parts[1] > 0) {
            max(parts[0], parts[1]) to min(parts[0], parts[1])
        } else {
            fallbackW to fallbackH
        }
    }

    /**
     * Computes final photo output dimensions and estimated file size for the selected format + quality preset.
     */
    fun estimatePhotoOutputSummary(
        sensorResolutionStr: String,
        format: CameraOutputFormat,
        qualityPreset: OutputQualityPreset,
        isDeviceLandscape: Boolean
    ): OutputEstimateSummary {
        val (sensorLong, sensorShort) = parseResolutionPair(sensorResolutionStr, 4000, 3000)
        val baseW = if (isDeviceLandscape) sensorLong else sensorShort
        val baseH = if (isDeviceLandscape) sensorShort else sensorLong

        val (croppedW, croppedH) = computeDimensionsForFormat(baseW, baseH, format, isDeviceLandscape)

        // Apply quality preset long-edge cap cleanly (0 = full native sensor resolution)
        val maxEdge = qualityPreset.photoMaxLongEdgePx
        val currentLong = max(croppedW, croppedH)
        val (finalW, finalH) = if (maxEdge > 0 && currentLong > maxEdge) {
            val scale = maxEdge.toDouble() / currentLong.toDouble()
            val sw = ((croppedW * scale).roundToInt() and 1.inv()).coerceAtLeast(2)
            val sh = ((croppedH * scale).roundToInt() and 1.inv()).coerceAtLeast(2)
            sw to sh
        } else {
            croppedW to croppedH
        }

        val pixels = finalW.toLong() * finalH.toLong()
        val mp = pixels / 1_000_000.0
        val mpLabel = if (mp >= 1.0) String.format(Locale.US, "%.1f MP", mp) else String.format(Locale.US, "%.2f MP", mp)
        val estMb = (pixels * qualityPreset.photoBytesPerPixel) / (1024.0 * 1024.0)
        val sizeText = if (estMb < 1.0) {
            String.format(Locale.US, "~%d KB", (estMb * 1024.0).roundToInt().coerceAtLeast(120))
        } else {
            String.format(Locale.US, "~%.1f MB", estMb)
        }

        return OutputEstimateSummary(
            widthPx = finalW,
            heightPx = finalH,
            resolutionLabel = "$finalW × $finalH",
            detailLabel = "$mpLabel • ${qualityPreset.shortLabel}",
            estimatedFileSize = sizeText
        )
    }

    /**
     * Computes final video output resolution, target bitrate, and estimated file size per minute.
     */
    fun estimateVideoOutputSummary(
        videoResolutionStr: String,
        videoFps: Int,
        format: CameraOutputFormat,
        qualityPreset: OutputQualityPreset,
        isDeviceLandscape: Boolean
    ): OutputEstimateSummary {
        val (resLong, resShort) = parseResolutionPair(videoResolutionStr, 1920, 1080)
        val baseW = when (format) {
            CameraOutputFormat.LANDSCAPE_FULL -> resLong
            CameraOutputFormat.PORTRAIT_FULL -> resShort
            CameraOutputFormat.SQUARE -> resShort
            CameraOutputFormat.RECTANGULAR_CROP -> if (isDeviceLandscape) resLong else resShort
        }
        val baseH = when (format) {
            CameraOutputFormat.LANDSCAPE_FULL -> resShort
            CameraOutputFormat.PORTRAIT_FULL -> resLong
            CameraOutputFormat.SQUARE -> resShort
            CameraOutputFormat.RECTANGULAR_CROP -> if (isDeviceLandscape) (resLong * 3 / 4) else (resShort * 4 / 3).coerceAtMost(resLong)
        }
        val evenW = (baseW and 1.inv()).coerceAtLeast(320)
        val evenH = (baseH and 1.inv()).coerceAtLeast(240)
        val fps = videoFps.coerceIn(24, 60)

        val baseMbps = when {
            resLong >= 3840 -> 42.0
            resLong >= 2560 -> 24.0
            resLong >= 1920 -> 14.0
            resLong >= 1280 -> 7.5
            else -> 4.0
        }
        val fpsScale = if (fps >= 55) 1.42 else 1.0
        val effectiveMbps = (baseMbps * fpsScale * qualityPreset.videoBitrateMultiplier).coerceAtLeast(2.0)
        // MB per minute = (Mbps / 8) * 60
        val mbPerMin = (effectiveMbps / 8.0) * 60.0
        val tierTag = when {
            resLong >= 3840 -> "4K"
            resLong >= 2560 -> "2K"
            resLong >= 1920 -> "1080p"
            resLong >= 1280 -> "720p"
            else -> "SD"
        }

        return OutputEstimateSummary(
            widthPx = evenW,
            heightPx = evenH,
            resolutionLabel = "$evenW × $evenH",
            detailLabel = "$tierTag • ${fps}fps • ${qualityPreset.shortLabel}",
            estimatedFileSize = String.format(Locale.US, "~%.0f MB/min", mbPerMin)
        )
    }

    /**
     * Pure dimension calculation helper (JVM-testable without Android Bitmap dependency).
     */
    fun computeDimensionsForFormat(
        baseW: Int,
        baseH: Int,
        format: CameraOutputFormat,
        isDeviceLandscape: Boolean
    ): Pair<Int, Int> {
        if (baseW <= 0 || baseH <= 0) return baseW to baseH
        val targetRatio = when (format) {
            CameraOutputFormat.LANDSCAPE_FULL -> if (baseW >= baseH) baseW.toFloat() / baseH.toFloat() else 16f / 9f
            CameraOutputFormat.PORTRAIT_FULL -> if (baseH >= baseW) baseW.toFloat() / baseH.toFloat() else 9f / 16f
            CameraOutputFormat.SQUARE -> 1.0f
            CameraOutputFormat.RECTANGULAR_CROP -> if (isDeviceLandscape) 4f / 3f else 3f / 4f
        }
        val currentRatio = baseW.toFloat() / baseH.toFloat()
        if (abs(currentRatio - targetRatio) < 0.015f) {
            return (baseW and 1.inv()).coerceAtLeast(2) to (baseH and 1.inv()).coerceAtLeast(2)
        }
        val outW: Int
        val outH: Int
        if (currentRatio > targetRatio) {
            outH = baseH
            outW = (baseH * targetRatio).roundToInt().coerceIn(2, baseW)
        } else {
            outW = baseW
            outH = (baseW / targetRatio).roundToInt().coerceIn(2, baseH)
        }
        return (outW and 1.inv()).coerceAtLeast(2) to (outH and 1.inv()).coerceAtLeast(2)
    }

    /**
     * Compact HUD pill label for Photo output (e.g., `"Port • High"` or `"1:1 • Max"`).
     */
    fun getCompactPhotoHudBadge(
        format: CameraOutputFormat,
        qualityPreset: OutputQualityPreset
    ): String = "${format.shortLabel} • ${qualityPreset.shortLabel}"

    /**
     * Compact HUD pill label for Video output (e.g., `"Land • 1080p 30"` or `"1:1 • 4K"`).
     */
    fun getCompactVideoHudBadge(
        videoResolutionStr: String,
        videoFps: Int,
        format: CameraOutputFormat
    ): String {
        val (longDim, _) = parseResolutionPair(videoResolutionStr, 1920, 1080)
        val tier = when {
            longDim >= 3840 -> "4K"
            longDim >= 2560 -> "2K"
            longDim >= 1920 -> "1080p"
            longDim >= 1280 -> "720p"
            else -> "SD"
        }
        return "${format.shortLabel} • $tier ${videoFps}"
    }
}
