package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import com.example.model.CameraAutofocusMode
import com.example.model.OutputQualityPreset
import com.example.model.VideoStabilizationMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Hardware capabilities detected dynamically from the active Camera2 sensor.
 */
data class CameraHardwareCapabilities(
    val supportsOis: Boolean = false,
    val supportsEis: Boolean = false,
    val supportsPreviewStabilization: Boolean = false,
    val supportsAutoFocus: Boolean = true,
    val supportsContinuousAfPicture: Boolean = true,
    val supportsContinuousAfVideo: Boolean = true,
    val supportsHighQualityNoiseReduction: Boolean = false,
    val supportsHighQualityEdge: Boolean = false,
    val supportedFpsOptions: List<Int> = listOf(30),
    val maxFps: Int = 30
) {
    val supportsAnyStabilization: Boolean
        get() = supportsOis || supportsEis || supportsPreviewStabilization

    val stabilizationHardwareSummary: String
        get() = when {
            supportsOis && (supportsEis || supportsPreviewStabilization) -> "OIS + EIS supported"
            supportsOis -> "Optical (OIS) supported"
            supportsPreviewStabilization -> "Preview EIS supported"
            supportsEis -> "Electronic (EIS) supported"
            else -> "Hardware stabilization unavailable"
        }
}

/**
 * Concrete Camera2 OIS/EIS register settings resolved for a [VideoStabilizationMode].
 */
data class ResolvedStabilizationConfig(
    val enableOis: Boolean,
    val videoStabilizationModeValue: Int,
    val activeLabel: String
)

/**
 * Scene photometric statistics measured via fast subsampling prior to adaptive photo enhancement.
 */
data class SceneAnalysisMetrics(
    val meanLuminance: Float,
    val shadowFraction: Float,
    val highlightClipFraction: Float,
    val estimatedNoiseLevel: Float
) {
    val isLowLight: Boolean
        get() = meanLuminance < 88f || shadowFraction > 0.45f

    val isVeryLowLight: Boolean
        get() = meanLuminance < 52f || shadowFraction > 0.65f

    val isHighContrast: Boolean
        get() = shadowFraction > 0.20f && highlightClipFraction > 0.08f
}

/**
 * Centralized Camera Quality, Autofocus, Stabilization & Adaptive Photo Enhancement Engine.
 *
 * Strictly independent of all timestamp, GPS, chainage, weather, logo, and minimap overlays.
 * Photo enhancement executes strictly BEFORE overlay burn-in so overlays stay crisp and unaltered.
 */
object CameraQualityEngine {

    private const val TAG = "CameraQualityEngine"

    /**
     * Queries real hardware capabilities (OIS, EIS, AF, Noise Reduction, Edge, 30/60 FPS)
     * from Camera2 [CameraCharacteristics] for the active lens.
     * Falls back gracefully on devices/emulators where Camera2 inspection is restricted.
     */
    fun queryHardwareCapabilities(
        context: Context,
        isFrontCamera: Boolean
    ): CameraHardwareCapabilities {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return CameraHardwareCapabilities()
            val targetFacing = if (isFrontCamera) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }
            var cameraId: String? = null
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == targetFacing) {
                    cameraId = id
                    break
                }
            }
            if (cameraId == null) {
                cameraId = cameraManager.cameraIdList.firstOrNull() ?: return CameraHardwareCapabilities()
            }
            val chars = cameraManager.getCameraCharacteristics(cameraId)

            val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            val supportsOis = oisModes?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true

            val eisModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            val supportsEis = eisModes?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true
            val supportsPreviewStab = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                eisModes?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) == true
            } else {
                false
            }

            val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            val supportsContinuousPic = afModes.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            val supportsContinuousVid = afModes.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            val supportsAf = supportsContinuousPic || supportsContinuousVid ||
                afModes.contains(CameraMetadata.CONTROL_AF_MODE_AUTO)

            val nrModes = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
            val supportsHqNr = nrModes?.contains(CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY) == true

            val edgeModes = chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
            val supportsHqEdge = edgeModes?.contains(CameraMetadata.EDGE_MODE_HIGH_QUALITY) == true

            val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val maxFps = fpsRanges?.maxOfOrNull { it.upper } ?: 30
            val fpsOptions = buildList {
                add(30)
                if (fpsRanges?.any { it.upper >= 60 } == true) {
                    add(60)
                }
            }

            return CameraHardwareCapabilities(
                supportsOis = supportsOis,
                supportsEis = supportsEis,
                supportsPreviewStabilization = supportsPreviewStab,
                supportsAutoFocus = supportsAf,
                supportsContinuousAfPicture = supportsContinuousPic,
                supportsContinuousAfVideo = supportsContinuousVid,
                supportsHighQualityNoiseReduction = supportsHqNr,
                supportsHighQualityEdge = supportsHqEdge,
                supportedFpsOptions = fpsOptions,
                maxFps = maxFps
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Hardware capability query fell back to safe defaults: ${e.message}")
            return CameraHardwareCapabilities()
        }
    }

    /**
     * Resolves the optimal OIS/EIS configuration for the user's [VideoStabilizationMode].
     * - [VideoStabilizationMode.OFF]: Disables both OIS and EIS cleanly.
     * - [VideoStabilizationMode.AUTO]: Prefers zero-crop Optical Stabilization (OIS) when available;
     *   enables Preview EIS / EIS when OIS is absent to avoid unnecessary cropping or resolution loss.
     * - [VideoStabilizationMode.ON]: Activates all supported hardware stabilization (OIS + best EIS).
     */
    fun resolveActiveStabilization(
        mode: VideoStabilizationMode,
        capabilities: CameraHardwareCapabilities,
        isVideoActive: Boolean
    ): ResolvedStabilizationConfig {
        if (mode == VideoStabilizationMode.OFF || !capabilities.supportsAnyStabilization) {
            return ResolvedStabilizationConfig(
                enableOis = false,
                videoStabilizationModeValue = CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
                activeLabel = "OFF"
            )
        }

        val bestEisMode = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && capabilities.supportsPreviewStabilization ->
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
            capabilities.supportsEis ->
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
            else ->
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
        }

        return when (mode) {
            VideoStabilizationMode.AUTO -> {
                if (capabilities.supportsOis) {
                    // OIS gives smooth stabilization with ZERO digital crop / resolution loss.
                    // In active video mode, also allow preview stabilization if supported.
                    val eisVal = if (isVideoActive &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        capabilities.supportsPreviewStabilization
                    ) {
                        CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                    } else {
                        CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                    }
                    ResolvedStabilizationConfig(
                        enableOis = true,
                        videoStabilizationModeValue = eisVal,
                        activeLabel = if (eisVal != CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF) "Auto (OIS+EIS)" else "Auto (OIS)"
                    )
                } else if (bestEisMode != CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF && isVideoActive) {
                    ResolvedStabilizationConfig(
                        enableOis = false,
                        videoStabilizationModeValue = bestEisMode,
                        activeLabel = "Auto (EIS)"
                    )
                } else {
                    ResolvedStabilizationConfig(
                        enableOis = false,
                        videoStabilizationModeValue = CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
                        activeLabel = "Auto"
                    )
                }
            }
            VideoStabilizationMode.ON -> {
                val eisVal = if (isVideoActive) bestEisMode else CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                val label = when {
                    capabilities.supportsOis && eisVal != CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> "OIS + EIS"
                    capabilities.supportsOis -> "OIS"
                    eisVal != CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> "EIS"
                    else -> "ON"
                }
                ResolvedStabilizationConfig(
                    enableOis = capabilities.supportsOis,
                    videoStabilizationModeValue = eisVal,
                    activeLabel = label
                )
            }
            VideoStabilizationMode.OFF -> ResolvedStabilizationConfig(
                enableOis = false,
                videoStabilizationModeValue = CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
                activeLabel = "OFF"
            )
        }
    }

    /**
     * Resolves the optimal CameraX [ImageCapture] capture mode for the selected [OutputQualityPreset].
     * Using [ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY] engages hardware multi-frame noise reduction
     * & HDR/Night fusion where supported by the device camera HAL.
     */
    fun resolveImageCaptureMode(preset: OutputQualityPreset): Int {
        return when (preset) {
            OutputQualityPreset.MAXIMUM,
            OutputQualityPreset.HIGH -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            OutputQualityPreset.BALANCED,
            OutputQualityPreset.SMALLER_SIZE -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    /**
     * Applies Camera2 hardware quality tuning (Continuous AF, Hardware Noise Reduction,
     * Natural Edge Enhancement, OIS/EIS Stabilization, and Target FPS Range) non-destructively
     * via [Camera2CameraControl] without rebinding use cases or disrupting active overlays.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun applyCamera2Tuning(
        cameraControl: CameraControl?,
        capabilities: CameraHardwareCapabilities,
        isVideoMode: Boolean,
        targetVideoFps: Int,
        stabilizationMode: VideoStabilizationMode,
        autofocusMode: CameraAutofocusMode
    ) {
        if (cameraControl == null) return
        try {
            val c2Control = Camera2CameraControl.from(cameraControl)
            val builder = CaptureRequestOptions.Builder()

            // 1. Smart Autofocus Mode (smooth continuous tracking vs tap-lock)
            if (capabilities.supportsAutoFocus) {
                val afMode = when (autofocusMode) {
                    CameraAutofocusMode.CONTINUOUS -> when {
                        isVideoMode && capabilities.supportsContinuousAfVideo ->
                            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                        capabilities.supportsContinuousAfPicture ->
                            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        else -> CameraMetadata.CONTROL_AF_MODE_AUTO
                    }
                    CameraAutofocusMode.TAP_LOCK -> CameraMetadata.CONTROL_AF_MODE_AUTO
                }
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, afMode)
            }

            // 2. Hardware Noise Reduction (high quality for photos & 30fps; fast for 60fps to prevent frame drops)
            if (capabilities.supportsHighQualityNoiseReduction) {
                val nrMode = if (isVideoMode && targetVideoFps >= 60) {
                    CameraMetadata.NOISE_REDUCTION_MODE_FAST
                } else {
                    CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
                }
                builder.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, nrMode)
            }

            // 3. Hardware Edge Preservation (natural clarity without oversharpening)
            if (capabilities.supportsHighQualityEdge) {
                val edgeMode = if (isVideoMode && targetVideoFps >= 60) {
                    CameraMetadata.EDGE_MODE_FAST
                } else {
                    CameraMetadata.EDGE_MODE_HIGH_QUALITY
                }
                builder.setCaptureRequestOption(CaptureRequest.EDGE_MODE, edgeMode)
            }

            // 4. Video & Optical Stabilization (OIS / EIS)
            val stab = resolveActiveStabilization(
                mode = stabilizationMode,
                capabilities = capabilities,
                isVideoActive = isVideoMode
            )
            if (capabilities.supportsOis) {
                builder.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    if (stab.enableOis) {
                        CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                    } else {
                        CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                    }
                )
            }
            if (capabilities.supportsEis || capabilities.supportsPreviewStabilization) {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    stab.videoStabilizationModeValue
                )
            }

            // 5. Target FPS Range (stable 30/60 FPS in video; flexible low-light range in photo mode)
            if (isVideoMode) {
                val effectiveFps = if (targetVideoFps >= 60 && capabilities.supportedFpsOptions.contains(60)) {
                    60
                } else {
                    30
                }
                val minFps = if (effectiveFps >= 60) 30 else 24
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(minFps, effectiveFps)
                )
            } else {
                // Allow camera AE to drop to 15fps in low light for brighter, cleaner low-noise stills
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(15, 30)
                )
            }

            c2Control.addCaptureRequestOptions(builder.build())
        } catch (e: Throwable) {
            Log.w(TAG, "Camera2 tuning skipped gracefully: ${e.message}")
        }
    }

    /**
     * Fast subsampled luminance & noise-floor analyzer (samples ~2000–4000 pixels, <3ms).
     */
    fun analyzeScene(bitmap: Bitmap): SceneAnalysisMetrics {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 2 || h <= 2) {
            return SceneAnalysisMetrics(128f, 0f, 0f, 0f)
        }
        val step = max(2, min(w, h) / 48)
        var count = 0
        var lumaSum = 0.0
        var shadowCount = 0
        var highlightCount = 0
        var localDiffSum = 0.0
        var diffCount = 0

        var y = step
        while (y < h - step) {
            var x = step
            while (x < w - step) {
                val c = bitmap.getPixel(x, y)
                val luma = ((c shr 16 and 0xFF) * 77 + (c shr 8 and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
                lumaSum += luma
                if (luma < 42) shadowCount++
                if (luma > 240) highlightCount++
                count++

                val right = bitmap.getPixel(x + 1, y)
                val lumaR = ((right shr 16 and 0xFF) * 77 + (right shr 8 and 0xFF) * 150 + (right and 0xFF) * 29) shr 8
                val diff = abs(luma - lumaR)
                // Only count flat/textured regions (diff < 28) toward sensor noise estimate, ignoring hard edges
                if (diff < 28) {
                    localDiffSum += diff
                    diffCount++
                }
                x += step
            }
            y += step
        }

        val safeCount = count.coerceAtLeast(1).toFloat()
        val safeDiffCount = diffCount.coerceAtLeast(1).toFloat()
        return SceneAnalysisMetrics(
            meanLuminance = (lumaSum / safeCount).toFloat(),
            shadowFraction = shadowCount / safeCount,
            highlightClipFraction = highlightCount / safeCount,
            estimatedNoiseLevel = (localDiffSum / safeDiffCount).toFloat()
        )
    }

    /**
     * Builds an adaptive 256-entry tone-mapping LUT that:
     * - Lifts underexposed shadows gently in low-light scenes without washing out blacks.
     * - Protects near-clipped highlights in harsh sunlight.
     * - Applies a subtle natural contrast curve in flat scenes while preserving natural skin/road tones.
     */
    fun buildAdaptiveToneLut(metrics: SceneAnalysisMetrics): IntArray {
        val lut = IntArray(256)
        val gamma = when {
            metrics.isVeryLowLight -> 0.80
            metrics.isLowLight -> 0.88
            metrics.meanLuminance > 195f && metrics.highlightClipFraction > 0.14f -> 1.09
            else -> 0.98
        }
        val contrastGain = when {
            metrics.isVeryLowLight -> 1.03 // keep gentle in very low light to avoid amplifying shadow noise
            metrics.isLowLight -> 1.05
            metrics.isHighContrast -> 0.98
            else -> 1.06 // subtle natural daylight punch
        }

        for (i in 0..255) {
            val norm = i / 255.0
            // 1. Adaptive exposure / shadow-highlight curve
            var v = norm.pow(gamma)
            // 2. Highlight roll-off protection when bright highlights are clipping
            if (metrics.highlightClipFraction > 0.08f && v > 0.78) {
                val excess = (v - 0.78) / 0.22
                v = 0.78 + 0.22 * (1.0 - exp(-1.6 * excess)) / (1.0 - exp(-1.6))
            }
            // 3. Subtle S-curve around mid-gray (0.5)
            val centered = (v - 0.5) * contrastGain + 0.5
            lut[i] = (centered * 255.0).roundToInt().coerceIn(0, 255)
        }
        return lut
    }

    /**
     * Applies intelligent photo quality enhancement & output-size optimization strictly BEFORE
     * overlays are drawn:
     * 1. Quality-preset intelligent downscale (only when preset caps long edge; MAXIMUM keeps full native resolution).
     * 2. Scene analysis (daylight vs low-light vs noisy).
     * 3. Adaptive exposure/tone balancing via fast 256-entry LUT.
     * 4. Edge-preserving low-light noise reduction (only when scene is low-light/noisy, preserving crisp edges).
     * 5. Gentle micro-detail clarity on high-confidence daylight edges (suppressed in shadows so noise is never amplified).
     *
     * Always safe under low memory: returns the original bitmap cleanly if allocation fails.
     */
    fun enhanceCapturedPhoto(
        source: Bitmap,
        qualityPreset: OutputQualityPreset
    ): Bitmap {
        if (source.isRecycled || source.width <= 4 || source.height <= 4) return source
        return try {
            // 1. Apply quality preset resolution cap (preserve 100% native resolution on MAXIMUM)
            val working = scaleBitmapForQualityPreset(source, qualityPreset)

            // 2. Fast photometric & noise analysis
            val metrics = analyzeScene(working)
            val lut = buildAdaptiveToneLut(metrics)

            val w = working.width
            val h = working.height
            val totalPixels = w * h

            // Guard memory on ultra-high-MP sensors (> 20MP): use fast row-band processing to avoid large allocations
            if (totalPixels > 20_000_000) {
                return applyRowBandToneEnhancement(working, lut)
            }

            val srcPixels = IntArray(totalPixels)
            working.getPixels(srcPixels, 0, w, 0, 0, w, h)
            val outPixels = IntArray(totalPixels)

            // Step A: Apply adaptive tone LUT
            for (i in 0 until totalPixels) {
                val p = srcPixels[i]
                val a = p and -0x1000000
                val r = lut[(p shr 16) and 0xFF]
                val g = lut[(p shr 8) and 0xFF]
                val b = lut[p and 0xFF]
                outPixels[i] = a or (r shl 16) or (g shl 8) or b
            }

            // Step B: Low-light edge-preserving denoising OR subtle daylight clarity
            val needsDenoise = metrics.isLowLight || metrics.estimatedNoiseLevel > 6.5f
            if (needsDenoise) {
                // Edge-preserving 5-tap cross bilateral filter (fast, zero ghosting, preserves real edges)
                val edgeThreshold = if (metrics.isVeryLowLight) 26 else 18
                val blendStrength = if (metrics.isVeryLowLight) 0.58f else 0.42f
                val temp = outPixels.copyOf()
                for (y in 1 until h - 1) {
                    val rowOffset = y * w
                    for (x in 1 until w - 1) {
                        val idx = rowOffset + x
                        val c = temp[idx]
                        val cr = (c shr 16) and 0xFF
                        val cg = (c shr 8) and 0xFF
                        val cb = c and 0xFF

                        val n1 = temp[idx - 1]
                        val n2 = temp[idx + 1]
                        val n3 = temp[idx - w]
                        val n4 = temp[idx + w]

                        var sumR = cr * 2
                        var sumG = cg * 2
                        var sumB = cb * 2
                        var weightSum = 2

                        val neighbors = intArrayOf(n1, n2, n3, n4)
                        for (n in neighbors) {
                            val nr = (n shr 16) and 0xFF
                            val ng = (n shr 8) and 0xFF
                            val nb = n and 0xFF
                            val diff = max(abs(cr - nr), max(abs(cg - ng), abs(cb - nb)))
                            if (diff <= edgeThreshold) {
                                sumR += nr
                                sumG += ng
                                sumB += nb
                                weightSum++
                            }
                        }

                        if (weightSum > 2) {
                            val avgR = sumR / weightSum
                            val avgG = sumG / weightSum
                            val avgB = sumB / weightSum
                            val finalR = (cr + ((avgR - cr) * blendStrength)).roundToInt().coerceIn(0, 255)
                            val finalG = (cg + ((avgG - cg) * blendStrength)).roundToInt().coerceIn(0, 255)
                            val finalB = (cb + ((avgB - cb) * blendStrength)).roundToInt().coerceIn(0, 255)
                            outPixels[idx] = (c and -0x1000000) or (finalR shl 16) or (finalG shl 8) or finalB
                        }
                    }
                }
            } else if (qualityPreset == OutputQualityPreset.MAXIMUM || qualityPreset == OutputQualityPreset.HIGH) {
                // Daylight subtle edge-aware clarity (corings out small diffs <= 6 so flat sky/asphalt stays noise-free)
                val temp = outPixels.copyOf()
                val amount = 0.22f
                for (y in 1 until h - 1) {
                    val rowOffset = y * w
                    for (x in 1 until w - 1) {
                        val idx = rowOffset + x
                        val c = temp[idx]
                        val cr = (c shr 16) and 0xFF
                        val cg = (c shr 8) and 0xFF
                        val cb = c and 0xFF
                        // Skip deep shadows so shadow noise is never amplified
                        if (cr + cg + cb < 105) continue

                        val n1 = temp[idx - 1]
                        val n2 = temp[idx + 1]
                        val n3 = temp[idx - w]
                        val n4 = temp[idx + w]

                        val avgR = (((n1 shr 16) and 0xFF) + ((n2 shr 16) and 0xFF) + ((n3 shr 16) and 0xFF) + ((n4 shr 16) and 0xFF)) shr 2
                        val avgG = (((n1 shr 8) and 0xFF) + ((n2 shr 8) and 0xFF) + ((n3 shr 8) and 0xFF) + ((n4 shr 8) and 0xFF)) shr 2
                        val avgB = ((n1 and 0xFF) + (n2 and 0xFF) + (n3 and 0xFF) + (n4 and 0xFF)) shr 2

                        val dr = cr - avgR
                        val dg = cg - avgG
                        val db = cb - avgB
                        val edgeMag = max(abs(dr), max(abs(dg), abs(db)))
                        // Coring gate: ignore micro-noise (<= 6), cap extreme halos (>= 48)
                        if (edgeMag in 7..48) {
                            val finalR = (cr + (dr * amount)).roundToInt().coerceIn(0, 255)
                            val finalG = (cg + (dg * amount)).roundToInt().coerceIn(0, 255)
                            val finalB = (cb + (db * amount)).roundToInt().coerceIn(0, 255)
                            outPixels[idx] = (c and -0x1000000) or (finalR shl 16) or (finalG shl 8) or finalB
                        }
                    }
                }
            }

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(outPixels, 0, w, 0, 0, w, h)
            if (working !== source && !working.isRecycled) {
                working.recycle()
            }
            result
        } catch (e: Throwable) {
            Log.w(TAG, "Photo enhancement skipped safely due to memory/runtime constraint: ${e.message}")
            source
        }
    }

    /**
     * Scales a photo down cleanly ONLY if its long edge exceeds the preset's [OutputQualityPreset.photoMaxLongEdgePx].
     * Preserves exact aspect ratio and keeps full native resolution on [OutputQualityPreset.MAXIMUM].
     */
    fun scaleBitmapForQualityPreset(source: Bitmap, qualityPreset: OutputQualityPreset): Bitmap {
        val maxEdge = qualityPreset.photoMaxLongEdgePx
        if (maxEdge <= 0) return source
        val w = source.width
        val h = source.height
        val currentLong = max(w, h)
        if (currentLong <= maxEdge) return source

        val scale = maxEdge.toDouble() / currentLong.toDouble()
        val targetW = ((w * scale).roundToInt() and 1.inv()).coerceAtLeast(2)
        val targetH = ((h * scale).roundToInt() and 1.inv()).coerceAtLeast(2)
        return try {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        } catch (e: Throwable) {
            source
        }
    }

    private fun applyRowBandToneEnhancement(source: Bitmap, lut: IntArray): Bitmap {
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val bandHeight = 64
        val rowBuf = IntArray(w * bandHeight)
        var y = 0
        while (y < h) {
            val rows = min(bandHeight, h - y)
            source.getPixels(rowBuf, 0, w, 0, y, w, rows)
            val count = w * rows
            for (i in 0 until count) {
                val p = rowBuf[i]
                val a = p and -0x1000000
                val r = lut[(p shr 16) and 0xFF]
                val g = lut[(p shr 8) and 0xFF]
                val b = lut[p and 0xFF]
                rowBuf[i] = a or (r shl 16) or (g shl 8) or b
            }
            result.setPixels(rowBuf, 0, w, 0, y, w, rows)
            y += rows
        }
        return result
    }
}
