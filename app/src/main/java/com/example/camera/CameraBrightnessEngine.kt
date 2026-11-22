package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.camera.view.LifecycleCameraController
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Hardware camera exposure capabilities queried from CameraX / Camera2 for the active lens.
 * Strictly respects actual hardware exposure limits, ISO range, shutter speed range, and FPS.
 */
data class CaptureExposureCapabilities(
    val isExposureCompensationSupported: Boolean = true,
    val minEvIndex: Int = -12,
    val maxEvIndex: Int = 12,
    val evStep: Float = 1f / 3f,
    val supportsManualSensor: Boolean = false,
    val isoRange: IntRange? = null,
    val exposureTimeRangeNs: LongRange? = null,
    val supportsHighQualityNoiseReduction: Boolean = false,
    val supportedFpsOptions: List<Int> = listOf(30)
) {
    val hasUsableEvRange: Boolean
        get() = isExposureCompensationSupported && minEvIndex < maxEvIndex && evStep > 0f

    val minEvStops: Float
        get() = if (hasUsableEvRange) minEvIndex * evStep else -CameraBrightnessEngine.FALLBACK_MAX_EV_STOPS

    val maxEvStops: Float
        get() = if (hasUsableEvRange) maxEvIndex * evStep else CameraBrightnessEngine.FALLBACK_MAX_EV_STOPS
}

/**
 * Immutable snapshot of actual camera capture exposure (for live preview, photo capture, and video recording).
 * Never modifies Android screen brightness.
 */
data class CaptureExposureState(
    val isAutoExposure: Boolean = true,
    val manualNormalizedLevel: Float = CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL,
    val effectiveNormalizedLevel: Float = CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL,
    val targetEvIndex: Int = 0,
    val targetEvStops: Float = 0f,
    val capabilities: CaptureExposureCapabilities = CaptureExposureCapabilities()
) {
    val displayPercent: Int
        get() = CameraBrightnessEngine.normalizedToPercent(effectiveNormalizedLevel)

    val evFormatted: String
        get() = CameraBrightnessEngine.formatEvStops(targetEvStops)
}

/**
 * Computed FPS-safe manual ISO & shutter parameters used ONLY as a safe fallback on hardware that
 * supports MANUAL_SENSOR but does not expose a usable AE exposure compensation range.
 */
data class SafeManualSensorExposure(
    val iso: Int,
    val exposureTimeNs: Long
)

/**
 * Pure JVM-testable continuous camera capture exposure controller.
 *
 * Maps continuous finger movement (`0.0f..1.0f`, where `0.5f` = balanced `0.0 EV`) proportionally
 * to the camera hardware's full supported exposure range `[minEvIndex..maxEvIndex]` with:
 * - Zero dropped micro-movements (full continuous floating-point accumulation)
 * - Smooth transition from Auto Exposure to Manual on finger drag without sudden jump
 * - Safe frame-paced anti-jump ramp for rapid swipes & active video recording
 * - Full retention after finger stops or releases
 */
class CaptureExposureController(
    initialAutoExposure: Boolean = true,
    initialManualLevel: Float = CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL,
    initialCapabilities: CaptureExposureCapabilities = CaptureExposureCapabilities()
) {
    private val lock = Any()

    private var autoExposure: Boolean = initialAutoExposure
    private var manualLevel: Float = CameraBrightnessEngine.clampNormalizedExposure(initialManualLevel)
    private var capabilities: CaptureExposureCapabilities = initialCapabilities

    fun getState(): CaptureExposureState = synchronized(lock) { buildStateLocked() }

    fun updateCapabilities(newCapabilities: CaptureExposureCapabilities): CaptureExposureState = synchronized(lock) {
        capabilities = newCapabilities
        buildStateLocked()
    }

    fun setAutoExposure(enabled: Boolean): CaptureExposureState = synchronized(lock) {
        autoExposure = enabled
        buildStateLocked()
    }

    fun setManualNormalizedLevel(level: Float): CaptureExposureState = synchronized(lock) {
        manualLevel = CameraBrightnessEngine.clampNormalizedExposure(level)
        buildStateLocked()
    }

    /**
     * Applies continuous normalized drag movement (`-1f..+1f`) directly to capture exposure.
     * - Dragging UP (`+delta`) increases capture exposure toward hardware max.
     * - Dragging DOWN (`-delta`) decreases capture exposure toward hardware min.
     * - If Auto Exposure was active, smoothly starts from balanced neutral (`0.5f` / `0.0 EV`)
     *   and overrides Auto Exposure cleanly with zero jump.
     */
    fun applyContinuousDragDelta(deltaNormalized: Float): CaptureExposureState = synchronized(lock) {
        if (!deltaNormalized.isFinite()) return buildStateLocked()
        val base = if (autoExposure) {
            autoExposure = false
            CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL
        } else {
            manualLevel
        }
        manualLevel = CameraBrightnessEngine.clampNormalizedExposure(base + deltaNormalized)
        buildStateLocked()
    }

    /**
     * Syncs an explicit EV compensation index (e.g. from the existing EV slider) into normalized space
     * so all exposure controls remain synchronized.
     */
    fun setFromHardwareEvIndex(evIndex: Int): CaptureExposureState = synchronized(lock) {
        if (evIndex == 0 && autoExposure) {
            return buildStateLocked()
        }
        autoExposure = (evIndex == 0)
        manualLevel = CameraBrightnessEngine.mapHardwareEvIndexToNormalized(
            evIndex = evIndex,
            minEv = capabilities.minEvIndex,
            maxEv = capabilities.maxEvIndex
        )
        buildStateLocked()
    }

    private fun buildStateLocked(): CaptureExposureState {
        val effective = if (autoExposure) {
            CameraBrightnessEngine.NEUTRAL_EXPOSURE_LEVEL
        } else {
            manualLevel
        }

        val targetIndex = if (autoExposure) {
            0
        } else if (capabilities.hasUsableEvRange) {
            CameraBrightnessEngine.mapNormalizedToHardwareEvIndex(
                normalizedLevel = effective,
                minEv = capabilities.minEvIndex,
                maxEv = capabilities.maxEvIndex
            )
        } else {
            0
        }

        val targetStops = if (autoExposure) {
            0f
        } else if (capabilities.hasUsableEvRange) {
            targetIndex * capabilities.evStep
        } else {
            (effective - 0.5f) * 2f * CameraBrightnessEngine.FALLBACK_MAX_EV_STOPS
        }

        return CaptureExposureState(
            isAutoExposure = autoExposure,
            manualNormalizedLevel = manualLevel,
            effectiveNormalizedLevel = effective,
            targetEvIndex = targetIndex,
            targetEvStops = targetStops,
            capabilities = capabilities
        )
    }
}

/**
 * Smooth Continuous Photo & Video Capture Brightness / Exposure Engine.
 *
 * Strictly controls **actual camera sensor exposure** via CameraX & Camera2Interop so that
 * live preview, saved photos, and recorded videos all faithfully reflect the user's continuous
 * right-side gesture adjustment. Never touches Android screen brightness or draws preview scrims.
 */
object CameraBrightnessEngine {

    private const val TAG = "CaptureExposureEngine"

    /** Minimum dim capture exposure normalized bound (`0.0f` -> hardware `minEvIndex`). */
    const val MIN_EXPOSURE_LEVEL = 0.0f

    /** Balanced neutral capture exposure (`0.5f` -> `0.0 EV`). */
    const val NEUTRAL_EXPOSURE_LEVEL = 0.5f

    /** Maximum bright capture exposure normalized bound (`1.0f` -> hardware `maxEvIndex`). */
    const val MAX_EXPOSURE_LEVEL = 1.0f

    /** Fallback photometric EV span (`-3.0..+3.0` stops) only if a camera HAL reports no hardware EV range. */
    const val FALLBACK_MAX_EV_STOPS = 3.0f

    fun clampNormalizedExposure(value: Float): Float {
        if (!value.isFinite()) return NEUTRAL_EXPOSURE_LEVEL
        return value.coerceIn(MIN_EXPOSURE_LEVEL, MAX_EXPOSURE_LEVEL)
    }

    fun normalizedToPercent(level: Float): Int {
        return (clampNormalizedExposure(level) * 100f).roundToInt().coerceIn(0, 100)
    }

    fun formatEvStops(evStops: Float): String {
        val rounded = (evStops * 10f).roundToInt() / 10f
        return when {
            abs(rounded) < 0.05f -> "0.0"
            rounded > 0f -> String.format(Locale.US, "+%.1f", rounded)
            else -> String.format(Locale.US, "%.1f", rounded)
        }
    }

    /**
     * Maps continuous `[0.0f..1.0f]` (`0.5f` = balanced `0`) proportionally onto hardware `[minEv..maxEv]`.
     * Handles both symmetric (e.g. `-12..+12`, `-24..+24`) and asymmetric (e.g. `-12..+8`) ranges cleanly.
     */
    fun mapNormalizedToHardwareEvIndex(
        normalizedLevel: Float,
        minEv: Int,
        maxEv: Int
    ): Int {
        if (minEv >= maxEv) return 0
        val clamped = clampNormalizedExposure(normalizedLevel)
        val signed = (clamped - NEUTRAL_EXPOSURE_LEVEL) * 2f // -1.0f..+1.0f
        return if (signed >= 0f) {
            (signed * maxEv.toFloat()).roundToInt().coerceIn(0, maxEv)
        } else {
            (signed * abs(minEv).toFloat()).roundToInt().coerceIn(minEv, 0)
        }
    }

    /**
     * Inverse mapping from hardware EV index `[minEv..maxEv]` back to normalized `[0.0f..1.0f]`.
     */
    fun mapHardwareEvIndexToNormalized(
        evIndex: Int,
        minEv: Int,
        maxEv: Int
    ): Float {
        if (minEv >= maxEv || evIndex == 0) return NEUTRAL_EXPOSURE_LEVEL
        return if (evIndex > 0 && maxEv > 0) {
            (NEUTRAL_EXPOSURE_LEVEL + (evIndex.toFloat() / maxEv.toFloat()) * 0.5f)
                .coerceIn(MIN_EXPOSURE_LEVEL, MAX_EXPOSURE_LEVEL)
        } else if (evIndex < 0 && minEv < 0) {
            (NEUTRAL_EXPOSURE_LEVEL - (abs(evIndex).toFloat() / abs(minEv).toFloat()) * 0.5f)
                .coerceIn(MIN_EXPOSURE_LEVEL, MAX_EXPOSURE_LEVEL)
        } else {
            NEUTRAL_EXPOSURE_LEVEL
        }
    }

    /**
     * Computes the next smooth hardware EV index step toward [targetEvIndex] so rapid gesture jumps
     * or Auto-resets transition fluidly without harsh single-frame flashes during video recording,
     * while normal/slow drags track 1:1 with zero perceived lag.
     */
    fun computeNextSmoothEvIndex(
        currentAppliedEv: Int,
        targetEvIndex: Int,
        isVideoRecording: Boolean,
        totalEvSpan: Int = 24
    ): Int {
        val delta = targetEvIndex - currentAppliedEv
        if (delta == 0) return targetEvIndex
        // Scale max step per ~16ms frame proportionally to hardware granularity
        val maxStepPerTick = when {
            isVideoRecording -> max(1, (totalEvSpan / 12).coerceIn(1, 2))
            else -> max(2, (totalEvSpan / 8).coerceIn(2, 4))
        }
        return when {
            abs(delta) <= maxStepPerTick -> targetEvIndex
            delta > 0 -> currentAppliedEv + maxStepPerTick
            else -> currentAppliedEv - maxStepPerTick
        }
    }

    /**
     * Computes FPS-safe manual ISO & shutter exposure time within strict hardware sensor bounds
     * (used ONLY if a device lacks AE exposure compensation support but exposes MANUAL_SENSOR).
     */
    fun computeSafeManualSensorExposure(
        normalizedLevel: Float,
        capabilities: CaptureExposureCapabilities,
        targetFps: Int
    ): SafeManualSensorExposure? {
        val isoRange = capabilities.isoRange ?: return null
        val timeRange = capabilities.exposureTimeRangeNs ?: return null
        if (!capabilities.supportsManualSensor) return null

        val safeFps = targetFps.coerceIn(15, 60)
        // Never allow shutter time longer than frame duration so selected FPS is maintained without motion blur
        val maxFrameShutterNs = (1_000_000_000L / safeFps.toLong()).coerceIn(timeRange.first, timeRange.last)
        val minShutterNs = max(timeRange.first, 250_000L).coerceAtMost(maxFrameShutterNs) // >= 1/4000s

        val clamped = clampNormalizedExposure(normalizedLevel)
        // Exponential exposure progression matches perceptual stops cleanly
        val shutterLogRatio = clamped.toDouble()
        val shutterNs = (minShutterNs.toDouble() *
            (maxFrameShutterNs.toDouble() / minShutterNs.toDouble().coerceAtLeast(1.0)).pow(shutterLogRatio)
            ).toLong().coerceIn(minShutterNs, maxFrameShutterNs)

        // Keep ISO conservative in mid/low levels to avoid noise; scale gently only when boosting brightness
        val maxPracticalIso = min(isoRange.last, max(isoRange.first, 3200))
        val isoRatio = clamped.toDouble().pow(1.25)
        val iso = (isoRange.first.toDouble() *
            (maxPracticalIso.toDouble() / isoRange.first.toDouble().coerceAtLeast(1.0)).pow(isoRatio)
            ).roundToInt().coerceIn(isoRange.first, maxPracticalIso)

        return SafeManualSensorExposure(iso = iso, exposureTimeNs = shutterNs)
    }

    /**
     * Queries actual hardware capture exposure capabilities from CameraX [LifecycleCameraController]
     * and Android Camera2 [CameraCharacteristics] for the active front/back lens.
     */
    fun queryCaptureExposureCapabilities(
        context: Context,
        controller: LifecycleCameraController?,
        isFrontCamera: Boolean
    ): CaptureExposureCapabilities {
        val hwCaps = CameraQualityEngine.queryHardwareCapabilities(
            context = context,
            isFrontCamera = isFrontCamera,
            cameraInfo = controller?.cameraInfo
        )

        var evSupported = false
        var minEv = 0
        var maxEv = 0
        var evStep = 0f

        // 1. Prefer live CameraX ExposureState when bound
        try {
            val exposureState = controller?.cameraInfo?.exposureState
            if (exposureState != null && exposureState.isExposureCompensationSupported) {
                val range = exposureState.exposureCompensationRange
                val step = exposureState.exposureCompensationStep
                if (range.lower < range.upper && step.denominator != 0 && step.numerator != 0) {
                    evSupported = true
                    minEv = range.lower
                    maxEv = range.upper
                    evStep = step.toFloat()
                }
            }
        } catch (ignored: Throwable) {}

        // 2. Query Camera2 characteristics for ISO range, shutter speed range, MANUAL_SENSOR, and EV fallback
        var supportsManualSensor = false
        var isoRange: IntRange? = null
        var timeRange: LongRange? = null

        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = CameraResolutionHelper.resolveActiveCameraId(
                context = context,
                isFrontCamera = isFrontCamera,
                cameraInfo = controller?.cameraInfo
            )

            if (manager != null && cameraId != null) {
                val chars = manager.getCameraCharacteristics(cameraId)
                if (!evSupported) {
                    val aeRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                    val aeStep = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                    if (aeRange != null && aeRange.lower < aeRange.upper &&
                        aeStep != null && aeStep.denominator != 0 && aeStep.numerator != 0
                    ) {
                        evSupported = true
                        minEv = aeRange.lower
                        maxEv = aeRange.upper
                        evStep = aeStep.toFloat()
                    }
                }

                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
                supportsManualSensor = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)

                val iso = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                if (iso != null && iso.lower < iso.upper) {
                    isoRange = iso.lower..iso.upper
                }

                val expTime = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                if (expTime != null && expTime.lower < expTime.upper) {
                    timeRange = expTime.lower..expTime.upper
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Camera2 exposure capability query fallback: ${e.message}")
        }

        return CaptureExposureCapabilities(
            isExposureCompensationSupported = evSupported,
            minEvIndex = minEv,
            maxEvIndex = maxEv,
            evStep = evStep,
            supportsManualSensor = supportsManualSensor,
            isoRange = isoRange,
            exposureTimeRangeNs = timeRange,
            supportsHighQualityNoiseReduction = hwCaps.supportsHighQualityNoiseReduction,
            supportedFpsOptions = hwCaps.supportedFpsOptions
        )
    }

    /**
     * Applies real-time camera sensor exposure using CameraX & Camera2Interop for live preview,
     * photo capture, and active video recording — with ZERO screen brightness changes, ZERO preview
     * filters, and ZERO recording interruptions or camera restarts.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun applyHardwareCaptureExposure(
        controller: LifecycleCameraController,
        cameraControl: CameraControl?,
        state: CaptureExposureState,
        appliedEvIndex: Int,
        isVideoMode: Boolean,
        isVideoRecording: Boolean,
        targetVideoFps: Int
    ) {
        val control = cameraControl ?: return
        val caps = state.capabilities

        // 1. Primary smooth hardware exposure path (works across preview, photo & active video recording!)
        //    Delegates exclusively to CameraX CameraControl.setExposureCompensationIndex so Camera2
        //    HDR scene mode, flash AE, and imageCaptureMode stay completely synchronized with ZERO rebinds.
        if (caps.hasUsableEvRange) {
            val clampedEv = appliedEvIndex.coerceIn(caps.minEvIndex, caps.maxEvIndex)
            try {
                control.setExposureCompensationIndex(clampedEv)
            } catch (e: Throwable) {
                Log.w(TAG, "CameraX EV update skipped safely: ${e.message}")
            }
            return
        }

        // 2. Fallback ONLY when device HAL lacks hardware EV compensation but exposes MANUAL_SENSOR
        if (!state.isAutoExposure && caps.supportsManualSensor && !isVideoRecording) {
            val manualSensor = computeSafeManualSensorExposure(
                normalizedLevel = state.effectiveNormalizedLevel,
                capabilities = caps,
                targetFps = if (isVideoMode) targetVideoFps else 30
            )
            if (manualSensor != null) {
                try {
                    val c2Control = Camera2CameraControl.from(control)
                    val options = CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                        .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualSensor.iso)
                        .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manualSensor.exposureTimeNs)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
                        .build()
                    c2Control.addCaptureRequestOptions(options)
                } catch (e: Throwable) {
                    Log.w(TAG, "Manual ISO/shutter fallback skipped safely: ${e.message}")
                }
            }
        } else if (state.isAutoExposure && caps.supportsManualSensor && !isVideoRecording) {
            try {
                val c2Control = Camera2CameraControl.from(control)
                val options = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                    .build()
                c2Control.addCaptureRequestOptions(options)
            } catch (ignored: Throwable) {}
        }
    }

    /**
     * Ensures the saved photograph faithfully retains the user's selected capture exposure while
     * preserving natural colours, sharpness, highlight/shadow integrity, and low-light noise reduction.
     *
     * Runs strictly BEFORE `OverlayRenderer.renderOverlay(...)` so all existing timestamp, GPS,
     * chainage, project, LHS/RHS, weather, logo, minimap, and EXIF metadata remain 100% untouched.
     */
    fun processCapturedPhotoForCaptureExposure(
        source: Bitmap,
        normalizedLevel: Float,
        evStops: Float,
        isAutoExposure: Boolean,
        hardwareEvApplied: Boolean
    ): Bitmap {
        if (source.isRecycled || source.width <= 4 || source.height <= 4) return source

        return try {
            val metrics = CameraQualityEngine.analyzeScene(source)

            // Case 1: Hardware EV already captured the user's selected exposure directly on the sensor!
            if (hardwareEvApplied) {
                if (isAutoExposure) {
                    // Auto Exposure mode: preserve original hardware-processed sensor detail, asphalt grain,
                    // and equipment edges 100% untouched — NEVER apply software blurring over night scenes!
                    return source
                }
                // Manual Exposure mode: NEVER undo darker/brighter manual exposure; only apply edge-preserving
                // noise reduction when the user explicitly boosted manual brightness strongly in noisy low-light.
                val userBoostedExposure = normalizedLevel > 0.62f || evStops > 0.6f
                if (userBoostedExposure && metrics.estimatedNoiseLevel > 6.5f) {
                    return applyEdgePreservingDenoiseOnly(source, strong = metrics.isVeryLowLight || metrics.estimatedNoiseLevel > 11f)
                }
                return source
            }

            // Case 2: Device HAL did NOT apply hardware EV (e.g., restricted HAL or emulator)
            // Apply an accurate photometric exposure-stop curve so saved photos STILL match the selected brightness!
            if (isAutoExposure || abs(evStops) < 0.08f) {
                return source
            }

            val w = source.width
            val h = source.height
            val totalPixels = w * h
            val lut = buildPhotometricExposureLut(evStops)

            if (totalPixels > 20_000_000) {
                return applyRowBandLut(source, lut)
            }

            val pixels = IntArray(totalPixels)
            source.getPixels(pixels, 0, w, 0, 0, w, h)

            // Denoise raw signal BEFORE boosting brightness in low-light so grain is never amplified
            if (evStops > 0.5f && (metrics.isLowLight || metrics.estimatedNoiseLevel > 5.5f)) {
                denoisePixelsInPlace(pixels, w, h, strong = metrics.isVeryLowLight || evStops > 1.5f)
            }

            for (i in 0 until totalPixels) {
                val p = pixels[i]
                val a = p and -0x1000000
                val r = lut[(p shr 16) and 0xFF]
                val g = lut[(p shr 8) and 0xFF]
                val b = lut[p and 0xFF]
                pixels[i] = a or (r shl 16) or (g shl 8) or b
            }

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(pixels, 0, w, 0, 0, w, h)
            result
        } catch (e: Throwable) {
            Log.w(TAG, "Capture exposure photo pass skipped safely: ${e.message}")
            source
        }
    }

    /**
     * Builds a smooth photometric exposure LUT for [evStops] (`-3.0..+3.0`) with natural highlight
     * shoulder roll-off and shadow toe protection so colors remain natural without harsh clipping.
     */
    fun buildPhotometricExposureLut(evStops: Float): IntArray {
        val lut = IntArray(256)
        val clampedStops = evStops.coerceIn(-FALLBACK_MAX_EV_STOPS, FALLBACK_MAX_EV_STOPS)
        // Perceptually calibrated exposure gain in sRGB space
        val gain = 2.0.pow(clampedStops.toDouble() * 0.55)

        for (i in 0..255) {
            val srgb = i / 255.0
            // Convert approximate sRGB -> linear, scale by exposure gain, soft-roll highlights, return to sRGB
            val linear = srgb.pow(2.2)
            var exposed = linear * gain
            if (exposed > 0.75) {
                // Filmic highlight roll-off prevents harsh blown-out whites when raising brightness
                val excess = exposed - 0.75
                exposed = 0.75 + 0.25 * (1.0 - exp(-excess / 0.35))
            }
            val outSrgb = exposed.coerceIn(0.0, 1.0).pow(1.0 / 2.2)
            lut[i] = (outSrgb * 255.0).roundToInt().coerceIn(0, 255)
        }
        return lut
    }

    private fun applyEdgePreservingDenoiseOnly(source: Bitmap, strong: Boolean): Bitmap {
        val w = source.width
        val h = source.height
        val totalPixels = w * h
        if (totalPixels > 20_000_000) return source
        val pixels = IntArray(totalPixels)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        denoisePixelsInPlace(pixels, w, h, strong = strong)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun denoisePixelsInPlace(pixels: IntArray, w: Int, h: Int, strong: Boolean) {
        val snapshot = pixels.copyOf()
        val lumaThreshold = if (strong) 24 else 18
        val chromaThreshold = if (strong) 34 else 24
        val lumaBlend = if (strong) 0.56f else 0.42f
        val chromaBlend = if (strong) 0.66f else 0.52f

        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = snapshot[idx]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                val cLuma = (cr * 77 + cg * 150 + cb * 29) shr 8

                var sumLuma = cLuma * 2
                var wLuma = 2
                var sumCrDiff = (cr - cLuma) * 2
                var sumCbDiff = (cb - cLuma) * 2
                var wChroma = 2

                val n0 = snapshot[idx - 1]
                val n1 = snapshot[idx + 1]
                val n2 = snapshot[idx - w]
                val n3 = snapshot[idx + w]

                for (k in 0..3) {
                    val n = when (k) {
                        0 -> n0
                        1 -> n1
                        2 -> n2
                        else -> n3
                    }
                    val nr = (n shr 16) and 0xFF
                    val ng = (n shr 8) and 0xFF
                    val nb = n and 0xFF
                    val nLuma = (nr * 77 + ng * 150 + nb * 29) shr 8
                    val lumaDiff = abs(cLuma - nLuma)

                    if (lumaDiff <= lumaThreshold) {
                        sumLuma += nLuma
                        wLuma++
                    }
                    if (lumaDiff <= chromaThreshold) {
                        sumCrDiff += (nr - nLuma)
                        sumCbDiff += (nb - nLuma)
                        wChroma++
                    }
                }

                val finalLuma = if (wLuma > 2) {
                    val avgLuma = sumLuma.toFloat() / wLuma.toFloat()
                    cLuma + (avgLuma - cLuma) * lumaBlend
                } else {
                    cLuma.toFloat()
                }

                val cCr = (cr - cLuma).toFloat()
                val cCb = (cb - cLuma).toFloat()
                val finalCr = if (wChroma > 2) {
                    val avgCr = sumCrDiff.toFloat() / wChroma.toFloat()
                    cCr + (avgCr - cCr) * chromaBlend
                } else {
                    cCr
                }
                val finalCb = if (wChroma > 2) {
                    val avgCb = sumCbDiff.toFloat() / wChroma.toFloat()
                    cCb + (avgCb - cCb) * chromaBlend
                } else {
                    cCb
                }

                val outR = (finalLuma + finalCr).roundToInt().coerceIn(0, 255)
                val outB = (finalLuma + finalCb).roundToInt().coerceIn(0, 255)
                val outG = ((finalLuma - 0.299f * outR - 0.114f * outB) / 0.587f).roundToInt().coerceIn(0, 255)
                pixels[idx] = (c and -0x1000000) or (outR shl 16) or (outG shl 8) or outB
            }
        }
    }

    private fun applyRowBandLut(source: Bitmap, lut: IntArray): Bitmap {
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val bandHeight = 64
        val buf = IntArray(w * bandHeight)
        var y = 0
        while (y < h) {
            val rows = min(bandHeight, h - y)
            source.getPixels(buf, 0, w, 0, y, w, rows)
            val count = w * rows
            for (i in 0 until count) {
                val p = buf[i]
                val a = p and -0x1000000
                val r = lut[(p shr 16) and 0xFF]
                val g = lut[(p shr 8) and 0xFF]
                val b = lut[p and 0xFF]
                buf[i] = a or (r shl 16) or (g shl 8) or b
            }
            result.setPixels(buf, 0, w, 0, y, w, rows)
            y += rows
        }
        return result
    }
}
