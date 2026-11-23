package com.example.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Rational
import com.example.model.CameraCropMode
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
 * Pixel-exact crop region inside a source frame `(sourceWidth x sourceHeight)`.
 * Shared identically by Live Preview, Photo Capture, Video Overlay, and Video Export so
 * the preview frame matches the saved output with zero hidden cropping.
 */
data class CropFrameRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val isCropped: Boolean
) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height.toFloat() else 1f
}

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
     * Linear retention factor for [CameraCropMode.SEMI_CROP] (moderate 14% gentle framing crop).
     */
    const val SEMI_CROP_RETENTION_FACTOR: Float = 0.86f

    /**
     * Authoritative unified crop rectangle calculation for [CameraCropMode].
     *
     * Used identically by Live Preview guide rendering, Photo capture cropping, Video real-time overlay
     * safe-area calculation, and Video post-crop export:
     * - [CameraCropMode.FULL]: **ZERO crop** — preserves 100% of `(sourceWidth x sourceHeight)` corner to corner.
     * - [CameraCropMode.SEMI_CROP]: Moderate intentional centered crop (~86% width & height), preserving natural
     *   orientation proportions with zero stretching or distortion.
     * - [CameraCropMode.RECTANGULAR_CROP]: Orientation-adaptive crisp rectangular framing (`16:9` landscape /
     *   `9:16` portrait for standard 4:3 sensors; `4:3` landscape / `3:4` portrait when source is already 16:9).
     * - [CameraCropMode.SQUARE_CROP]: Exact centered `1:1` square crop.
     */
    fun computeCropRectForMode(
        sourceWidth: Int,
        sourceHeight: Int,
        cropMode: CameraCropMode,
        isLandscape: Boolean
    ): CropFrameRect {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return CropFrameRect(0, 0, sourceWidth.coerceAtLeast(0), sourceHeight.coerceAtLeast(0), false)
        }

        if (cropMode == CameraCropMode.FULL) {
            return CropFrameRect(
                left = 0,
                top = 0,
                width = sourceWidth,
                height = sourceHeight,
                isCropped = false
            )
        }

        val currentRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
        val effectiveLandscape = if (sourceWidth != sourceHeight) sourceWidth > sourceHeight else isLandscape

        when (cropMode) {
            CameraCropMode.SEMI_CROP -> {
                val rawW = (sourceWidth * SEMI_CROP_RETENTION_FACTOR).roundToInt().coerceIn(2, sourceWidth)
                val rawH = (sourceHeight * SEMI_CROP_RETENTION_FACTOR).roundToInt().coerceIn(2, sourceHeight)
                val evenW = (rawW and 1.inv()).coerceAtLeast(2).coerceAtMost(sourceWidth)
                val evenH = (rawH and 1.inv()).coerceAtLeast(2).coerceAtMost(sourceHeight)
                val left = ((sourceWidth - evenW) / 2).coerceIn(0, sourceWidth - evenW)
                val top = ((sourceHeight - evenH) / 2).coerceIn(0, sourceHeight - evenH)
                return CropFrameRect(
                    left = left,
                    top = top,
                    width = evenW,
                    height = evenH,
                    isCropped = evenW < sourceWidth || evenH < sourceHeight
                )
            }

            CameraCropMode.RECTANGULAR_CROP -> {
                val longShortRatio = max(sourceWidth, sourceHeight).toFloat() / min(sourceWidth, sourceHeight).toFloat()
                // On 4:3 native sensors (~1.33), Rectangular Crop delivers crisp 16:9 / 9:16 framing.
                // On 16:9 native streams (~1.78), Rectangular Crop delivers crisp 4:3 / 3:4 framing so
                // switching modes is always distinct, centered, and orientation-adaptive without distortion.
                val targetRatio = if (longShortRatio < 1.55f) {
                    if (effectiveLandscape) 16f / 9f else 9f / 16f
                } else {
                    if (effectiveLandscape) 4f / 3f else 3f / 4f
                }
                return computeCenteredRatioCropRect(sourceWidth, sourceHeight, currentRatio, targetRatio)
            }

            CameraCropMode.SQUARE_CROP -> {
                val side = (min(sourceWidth, sourceHeight) and 1.inv()).coerceAtLeast(2).coerceAtMost(min(sourceWidth, sourceHeight))
                val left = ((sourceWidth - side) / 2).coerceIn(0, sourceWidth - side)
                val top = ((sourceHeight - side) / 2).coerceIn(0, sourceHeight - side)
                return CropFrameRect(
                    left = left,
                    top = top,
                    width = side,
                    height = side,
                    isCropped = side < sourceWidth || side < sourceHeight
                )
            }

            CameraCropMode.FULL -> {
                return CropFrameRect(0, 0, sourceWidth, sourceHeight, false)
            }
        }
    }

    private fun computeCenteredRatioCropRect(
        sourceWidth: Int,
        sourceHeight: Int,
        currentRatio: Float,
        targetRatio: Float
    ): CropFrameRect {
        if (abs(currentRatio - targetRatio) < 0.01f) {
            return CropFrameRect(0, 0, sourceWidth, sourceHeight, false)
        }
        val targetW: Int
        val targetH: Int
        if (currentRatio > targetRatio) {
            targetH = sourceHeight
            targetW = (sourceHeight * targetRatio).roundToInt().coerceIn(2, sourceWidth)
        } else {
            targetW = sourceWidth
            targetH = (sourceWidth / targetRatio).roundToInt().coerceIn(2, sourceHeight)
        }
        val evenW = (targetW and 1.inv()).coerceAtLeast(2).coerceAtMost(sourceWidth)
        val evenH = (targetH and 1.inv()).coerceAtLeast(2).coerceAtMost(sourceHeight)
        val left = ((sourceWidth - evenW) / 2).coerceIn(0, sourceWidth - evenW)
        val top = ((sourceHeight - evenH) / 2).coerceIn(0, sourceHeight - evenH)
        return CropFrameRect(
            left = left,
            top = top,
            width = evenW,
            height = evenH,
            isCropped = evenW < sourceWidth || evenH < sourceHeight
        )
    }

    /**
     * Applies [CameraCropMode] to a captured [Bitmap] with zero distortion and zero unnecessary copies.
     *
     * In [CameraCropMode.FULL], returns [source] immediately without removing a single pixel.
     * Always runs strictly BEFORE overlay/timestamp rendering so metadata & stamps are never clipped.
     */
    fun applyCropModeToBitmap(
        source: Bitmap,
        cropMode: CameraCropMode,
        isLandscape: Boolean
    ): Bitmap {
        if (cropMode == CameraCropMode.FULL) return source
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source
        val rect = computeCropRectForMode(w, h, cropMode, isLandscape)
        if (!rect.isCropped) return source
        return try {
            Bitmap.createBitmap(source, rect.left, rect.top, rect.width, rect.height)
        } catch (e: Throwable) {
            Log.e(TAG, "Crop mode $cropMode failed (${rect.width}x${rect.height}): ${e.message}", e)
            source
        }
    }

    /**
     * Computes the exact pixel rectangle occupied by a `FIT_CENTER` camera preview stream inside
     * `[containerWidth x containerHeight]` for the active camera sensor's native [sensorLongShortRatio]
     * (e.g. `4f / 3f` for still photos or `16f / 9f` for video).
     */
    fun computePreviewViewportRect(
        containerWidth: Float,
        containerHeight: Float,
        sensorLongShortRatio: Float = 4f / 3f,
        isLandscape: Boolean
    ): FramingPreviewRect {
        if (containerWidth <= 1f || containerHeight <= 1f) {
            return FramingPreviewRect(0f, 0f, containerWidth.coerceAtLeast(0f), containerHeight.coerceAtLeast(0f), false, 1f)
        }
        val safeRatio = sensorLongShortRatio.coerceIn(1.0f, 2.4f)
        val displayedRatio = if (isLandscape) safeRatio else 1f / safeRatio
        val containerRatio = containerWidth / containerHeight

        val viewW: Float
        val viewH: Float
        if (containerRatio > displayedRatio) {
            viewH = containerHeight
            viewW = (containerHeight * displayedRatio).coerceAtMost(containerWidth)
        } else {
            viewW = containerWidth
            viewH = (containerWidth / displayedRatio).coerceAtMost(containerHeight)
        }
        val left = ((containerWidth - viewW) / 2f).coerceAtLeast(0f)
        val top = ((containerHeight - viewH) / 2f).coerceAtLeast(0f)
        return FramingPreviewRect(
            leftPx = left,
            topPx = top,
            widthPx = viewW,
            heightPx = viewH,
            isCropped = false,
            targetAspectRatio = displayedRatio
        )
    }

    /**
     * Computes the exact live viewfinder crop guide rectangle matching [computeCropRectForMode] inside
     * the `FIT_CENTER` displayed sensor viewport so what the user sees matches the saved file 1:1.
     */
    fun computeCropGuideOverlayRect(
        containerWidth: Float,
        containerHeight: Float,
        sensorLongShortRatio: Float = 4f / 3f,
        cropMode: CameraCropMode,
        isLandscape: Boolean
    ): FramingPreviewRect {
        val sensorViewport = computePreviewViewportRect(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            sensorLongShortRatio = sensorLongShortRatio,
            isLandscape = isLandscape
        )
        if (cropMode == CameraCropMode.FULL || sensorViewport.widthPx <= 1f || sensorViewport.heightPx <= 1f) {
            return sensorViewport.copy(isCropped = false)
        }

        // Compute crop rect in high-precision virtual sensor coordinates matching the exact displayed ratio
        val virtualShort = 1000
        val virtualLong = (virtualShort * sensorLongShortRatio.coerceIn(1.0f, 2.4f)).roundToInt().coerceAtLeast(virtualShort)
        val virtualW = if (isLandscape) virtualLong else virtualShort
        val virtualH = if (isLandscape) virtualShort else virtualLong

        val cropRect = computeCropRectForMode(
            sourceWidth = virtualW,
            sourceHeight = virtualH,
            cropMode = cropMode,
            isLandscape = isLandscape
        )
        if (!cropRect.isCropped) {
            return sensorViewport.copy(isCropped = false)
        }

        val scaleX = sensorViewport.widthPx / virtualW.toFloat()
        val scaleY = sensorViewport.heightPx / virtualH.toFloat()
        val outLeft = sensorViewport.leftPx + cropRect.left * scaleX
        val outTop = sensorViewport.topPx + cropRect.top * scaleY
        val outW = cropRect.width * scaleX
        val outH = cropRect.height * scaleY

        return FramingPreviewRect(
            leftPx = outLeft,
            topPx = outTop,
            widthPx = outW,
            heightPx = outH,
            isCropped = true,
            targetAspectRatio = if (outH > 0f) outW / outH else 1f
        )
    }

    /**
     * Computed edge-to-edge preview display transform that strictly decouples:
     * 1. Live full-screen edge-to-edge visual presentation (`previewScale` applied via GPU layer on
     *    a `FIT_CENTER` `PreviewView`, producing ZERO black letterbox/pillarbox bars in FULL mode), and
     * 2. Full-sensor uncropped `ImageCapture` / `VideoCapture` output (because `PreviewView.scaleType`
     *    stays `FIT_CENTER`, CameraX NEVER crops the captured JPEG/Video stream to the display ratio!).
     */
    data class EdgeToEdgePreviewTransform(
        val previewScale: Float,
        val visibleViewportRect: FramingPreviewRect,
        val cropGuideRect: FramingPreviewRect,
        val isEdgeToEdgeFilled: Boolean
    )

    /**
     * Computes the adaptive GPU preview scale and 1:1 crop guide rectangle for a container of
     * `[containerWidth x containerHeight]` displaying a native camera sensor of [sensorLongShortRatio]:
     *
     * - [CameraCropMode.FULL]: Scales uniformly about center by `max(W / vpW, H / vpH)` so the live
     *   viewfinder fills 100% edge-to-edge (ZERO black bars in both Portrait and Landscape) while the
     *   saved photograph/video preserves 100% of the uncropped native sensor resolution.
     * - [CameraCropMode.SEMI_CROP], [CameraCropMode.RECTANGULAR_CROP], [CameraCropMode.SQUARE_CROP]:
     *   Scales as close to edge-to-edge as possible while guaranteeing 100% of the active crop region
     *   remains cleanly visible inside `[containerWidth x containerHeight]` so the golden crop guide
     *   brackets match the saved output framing pixel-for-pixel.
     */
    fun computeEdgeToEdgePreviewTransform(
        containerWidth: Float,
        containerHeight: Float,
        sensorLongShortRatio: Float = 4f / 3f,
        cropMode: CameraCropMode,
        isLandscape: Boolean
    ): EdgeToEdgePreviewTransform {
        if (containerWidth <= 1f || containerHeight <= 1f) {
            val empty = FramingPreviewRect(0f, 0f, containerWidth.coerceAtLeast(0f), containerHeight.coerceAtLeast(0f), false, 1f)
            return EdgeToEdgePreviewTransform(
                previewScale = 1f,
                visibleViewportRect = empty,
                cropGuideRect = empty,
                isEdgeToEdgeFilled = false
            )
        }

        val baseSensorVp = computePreviewViewportRect(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            sensorLongShortRatio = sensorLongShortRatio,
            isLandscape = isLandscape
        )
        val baseGuide = computeCropGuideOverlayRect(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            sensorLongShortRatio = sensorLongShortRatio,
            cropMode = cropMode,
            isLandscape = isLandscape
        )

        val fillScale = max(
            containerWidth / baseSensorVp.widthPx.coerceAtLeast(1f),
            containerHeight / baseSensorVp.heightPx.coerceAtLeast(1f)
        ).coerceIn(1f, 3.0f)

        val activeScale = if (cropMode == CameraCropMode.FULL || !baseGuide.isCropped) {
            fillScale
        } else {
            val maxFitCropScale = min(
                containerWidth / baseGuide.widthPx.coerceAtLeast(1f),
                containerHeight / baseGuide.heightPx.coerceAtLeast(1f)
            )
            min(fillScale, maxFitCropScale).coerceAtLeast(1f)
        }

        val cx = containerWidth / 2f
        val cy = containerHeight / 2f

        fun scaleRectAboutCenter(rect: FramingPreviewRect, clampToContainer: Boolean): FramingPreviewRect {
            val scaledW = rect.widthPx * activeScale
            val scaledH = rect.heightPx * activeScale
            val rawLeft = cx + (rect.leftPx - cx) * activeScale
            val rawTop = cy + (rect.topPx - cy) * activeScale
            if (!clampToContainer) {
                return rect.copy(
                    leftPx = rawLeft,
                    topPx = rawTop,
                    widthPx = scaledW,
                    heightPx = scaledH
                )
            }
            val clampedLeft = rawLeft.coerceIn(0f, containerWidth)
            val clampedTop = rawTop.coerceIn(0f, containerHeight)
            val clampedRight = (rawLeft + scaledW).coerceIn(clampedLeft, containerWidth)
            val clampedBottom = (rawTop + scaledH).coerceIn(clampedTop, containerHeight)
            val clampedW = (clampedRight - clampedLeft).coerceAtLeast(0f)
            val clampedH = (clampedBottom - clampedTop).coerceAtLeast(0f)
            return rect.copy(
                leftPx = clampedLeft,
                topPx = clampedTop,
                widthPx = clampedW,
                heightPx = clampedH,
                targetAspectRatio = if (clampedH > 0f) clampedW / clampedH else rect.targetAspectRatio
            )
        }

        val visibleVp = scaleRectAboutCenter(baseSensorVp, clampToContainer = true)
        val scaledGuide = if (cropMode == CameraCropMode.FULL || !baseGuide.isCropped) {
            visibleVp.copy(isCropped = false)
        } else {
            scaleRectAboutCenter(baseGuide, clampToContainer = true).copy(isCropped = true)
        }

        val isFilled = visibleVp.widthPx >= containerWidth - 1.5f && visibleVp.heightPx >= containerHeight - 1.5f
        return EdgeToEdgePreviewTransform(
            previewScale = activeScale,
            visibleViewportRect = visibleVp,
            cropGuideRect = scaledGuide,
            isEdgeToEdgeFilled = isFilled
        )
    }

    /**
     * Maps a touch coordinate `(tapX, tapY)` on the edge-to-edge scaled screen back to the exact
     * unscaled `PreviewView` coordinate expected by `PreviewView.meteringPointFactory`.
     */
    fun mapScreenTapToUnscaledPreview(
        tapX: Float,
        tapY: Float,
        containerWidth: Float,
        containerHeight: Float,
        previewScale: Float
    ): Pair<Float, Float> {
        val safeScale = previewScale.takeIf { it.isFinite() && it > 0.01f } ?: 1f
        if (containerWidth <= 1f || containerHeight <= 1f || abs(safeScale - 1f) < 0.001f) {
            return Pair(tapX, tapY)
        }
        val cx = containerWidth / 2f
        val cy = containerHeight / 2f
        val unscaledX = (cx + (tapX - cx) / safeScale).coerceIn(0f, containerWidth)
        val unscaledY = (cy + (tapY - cy) / safeScale).coerceIn(0f, containerHeight)
        return Pair(unscaledX, unscaledY)
    }

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
