package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Surrounding ambient lighting band detected via the device ambient light sensor (or scene photometry).
 */
enum class AmbientLightZone(val label: String) {
    LOW_LIGHT("Low Light"),
    NORMAL_LIGHT("Normal Light"),
    HIGH_LIGHT("High Light")
}

/**
 * Immutable snapshot coordinating three logically separate camera brightness functions:
 * 1. Display Brightness ([effectiveDisplayBrightness]) — live camera preview visibility.
 * 2. Camera Exposure ([recommendedAutoExposureOffset]) — sensor exposure guidance in Auto mode.
 * 3. Photo/Video Processing ([lightZone], [ambientLux]) — actual lighting optimization for media output.
 */
data class CameraBrightnessState(
    val isAutoEnabled: Boolean = false,
    val manualBrightness: Float = CameraBrightnessEngine.DEFAULT_MANUAL_BRIGHTNESS,
    val effectiveDisplayBrightness: Float = CameraBrightnessEngine.DEFAULT_MANUAL_BRIGHTNESS,
    val ambientLux: Float? = null,
    val lightZone: AmbientLightZone = AmbientLightZone.NORMAL_LIGHT,
    val recommendedAutoExposureOffset: Int = 0,
    val hasLightSensor: Boolean = true
) {
    val displayPercent: Int
        get() = CameraBrightnessEngine.brightnessToPercent(effectiveDisplayBrightness)
}

/**
 * Stateful, thread-safe ambient-light brightness engine with adaptive thresholds,
 * hysteresis, and low-pass smoothing to prevent flickering or sudden jumps.
 *
 * Pure JVM-testable (no Android framework dependency required for core state transitions).
 */
class AmbientBrightnessController(
    initialAutoEnabled: Boolean = false,
    initialManualBrightness: Float = CameraBrightnessEngine.DEFAULT_MANUAL_BRIGHTNESS,
    initialHasLightSensor: Boolean = true
) {
    private val lock = Any()

    private var autoEnabled: Boolean = initialAutoEnabled
    private var manualBrightness: Float = CameraBrightnessEngine.clampBrightness(initialManualBrightness)
    private var hasLightSensor: Boolean = initialHasLightSensor
    private var filteredLux: Float? = null
    private var currentZone: AmbientLightZone = AmbientLightZone.NORMAL_LIGHT
    private var currentAutoBrightness: Float = CameraBrightnessEngine.DEFAULT_MANUAL_BRIGHTNESS

    companion object {
        // Hysteresis thresholds (lux) preventing oscillation near boundaries
        const val LOW_LIGHT_ENTER_LUX = 35f
        const val LOW_LIGHT_EXIT_LUX = 60f
        const val HIGH_LIGHT_ENTER_LUX = 1_200f
        const val HIGH_LIGHT_EXIT_LUX = 820f
        const val EXTREME_SUNLIGHT_LUX = 10_000f

        // Smoothness & anti-flicker guards
        const val LUX_EMA_ALPHA = 0.28f
        const val BRIGHTNESS_DEADBAND = 0.015f
    }

    fun getState(): CameraBrightnessState = synchronized(lock) { buildStateLocked() }

    fun setHasLightSensor(available: Boolean): CameraBrightnessState = synchronized(lock) {
        hasLightSensor = available
        buildStateLocked()
    }

    fun setAutoEnabled(enabled: Boolean): CameraBrightnessState = synchronized(lock) {
        autoEnabled = enabled
        buildStateLocked()
    }

    fun setManualBrightness(level: Float): CameraBrightnessState = synchronized(lock) {
        manualBrightness = CameraBrightnessEngine.clampBrightness(level)
        buildStateLocked()
    }

    /**
     * Adjusts manual brightness by a fine-grained normalized delta (`-1f..+1f`) across the full
     * `[MIN_PRACTICAL_BRIGHTNESS..MAX_SUPPORTED_BRIGHTNESS]` range.
     * If Auto Brightness was active when the user manually dragged the right-side control,
     * smoothly hands off from the current effective display brightness to manual mode with zero jump.
     */
    fun applyManualDragDelta(deltaNormalized: Float): CameraBrightnessState = synchronized(lock) {
        val base = if (autoEnabled) {
            val currentEffective = if (hasLightSensor && filteredLux != null) {
                currentAutoBrightness
            } else {
                manualBrightness
            }
            autoEnabled = false
            currentEffective
        } else {
            manualBrightness
        }
        manualBrightness = CameraBrightnessEngine.clampBrightness(base + deltaNormalized)
        buildStateLocked()
    }

    /**
     * Processes a new ambient light sensor sample in lux (`>= 0f`).
     * Applies exponential smoothing, adaptive zone hysteresis, and deadband suppression
     * so display brightness and camera exposure remain rock-steady without flickering.
     */
    fun onAmbientLuxSample(rawLux: Float): CameraBrightnessState = synchronized(lock) {
        if (!rawLux.isFinite() || rawLux < 0f) return buildStateLocked()
        val clampedLux = rawLux.coerceIn(0f, 120_000f)

        // 1. Exponential smoothing in log-lux space (matches human perceptual luminance response)
        val prevLux = filteredLux
        val smoothedLux = if (prevLux == null) {
            clampedLux
        } else {
            val prevLog = ln((prevLux + 1f).toDouble())
            val curLog = ln((clampedLux + 1f).toDouble())
            val blendedLog = prevLog + (curLog - prevLog) * LUX_EMA_ALPHA
            (exp(blendedLog) - 1.0).toFloat().coerceAtLeast(0f)
        }
        filteredLux = smoothedLux

        // 2. Hysteresis light-zone classification (prevents frequent zone flipping)
        currentZone = when (currentZone) {
            AmbientLightZone.LOW_LIGHT -> when {
                smoothedLux >= HIGH_LIGHT_ENTER_LUX -> AmbientLightZone.HIGH_LIGHT
                smoothedLux > LOW_LIGHT_EXIT_LUX -> AmbientLightZone.NORMAL_LIGHT
                else -> AmbientLightZone.LOW_LIGHT
            }
            AmbientLightZone.NORMAL_LIGHT -> when {
                smoothedLux < LOW_LIGHT_ENTER_LUX -> AmbientLightZone.LOW_LIGHT
                smoothedLux > HIGH_LIGHT_ENTER_LUX -> AmbientLightZone.HIGH_LIGHT
                else -> AmbientLightZone.NORMAL_LIGHT
            }
            AmbientLightZone.HIGH_LIGHT -> when {
                smoothedLux < LOW_LIGHT_ENTER_LUX -> AmbientLightZone.LOW_LIGHT
                smoothedLux < HIGH_LIGHT_EXIT_LUX -> AmbientLightZone.NORMAL_LIGHT
                else -> AmbientLightZone.HIGH_LIGHT
            }
        }

        // 3. Target display brightness with deadband suppression
        val targetBrightness = CameraBrightnessEngine.luxToTargetDisplayBrightness(smoothedLux)
        if (prevLux == null || abs(targetBrightness - currentAutoBrightness) >= BRIGHTNESS_DEADBAND) {
            currentAutoBrightness = targetBrightness
        }

        buildStateLocked()
    }

    private fun buildStateLocked(): CameraBrightnessState {
        val effectiveDisplay = if (autoEnabled && hasLightSensor && filteredLux != null) {
            currentAutoBrightness
        } else {
            manualBrightness
        }

        // Subtle sensor exposure coordination ONLY in Auto mode; manual display brightness never
        // alters camera exposure so dimming/brightening the screen never ruins photo/video exposure!
        val autoEvOffset = if (autoEnabled && filteredLux != null) {
            val lux = filteredLux!!
            when {
                currentZone == AmbientLightZone.HIGH_LIGHT && lux >= EXTREME_SUNLIGHT_LUX -> -1
                else -> 0
            }
        } else {
            0
        }

        return CameraBrightnessState(
            isAutoEnabled = autoEnabled,
            manualBrightness = manualBrightness,
            effectiveDisplayBrightness = CameraBrightnessEngine.clampBrightness(effectiveDisplay),
            ambientLux = filteredLux,
            lightZone = currentZone,
            recommendedAutoExposureOffset = autoEvOffset,
            hasLightSensor = hasLightSensor
        )
    }
}

/**
 * Single lightweight lifecycle-bound Android ambient light sensor listener (`Sensor.TYPE_LIGHT`).
 * Unregisters immediately when Camera is paused/disposed or when Auto Brightness is turned OFF.
 */
class AmbientLightSensorMonitor(context: Context) {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    val isAvailable: Boolean
        get() = lightSensor != null

    private var activeListener: SensorEventListener? = null

    fun start(onLux: (Float) -> Unit): Boolean {
        stop()
        val sm = sensorManager ?: return false
        val sensor = lightSensor ?: return false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val lux = event?.values?.firstOrNull() ?: return
                if (lux.isFinite() && lux >= 0f) {
                    onLux(lux)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        return try {
            val registered = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            if (registered) {
                activeListener = listener
            }
            registered
        } catch (e: Throwable) {
            Log.w("AmbientLightMonitor", "Failed to register ambient light sensor: ${e.message}")
            false
        }
    }

    fun stop() {
        val listener = activeListener ?: return
        activeListener = null
        try {
            sensorManager?.unregisterListener(listener)
        } catch (ignored: Throwable) {}
    }
}

/**
 * Smart Camera Brightness, Exposure & Light-Condition Optimization Engine.
 *
 * Coordinates three strictly separated layers:
 * 1. **Display Brightness** (`0.02f..1.0f`): Full practical dim to maximum device screen brightness.
 * 2. **Camera Exposure**: Smooth continuous AE, stable AWB, and ambient-light optimization (never
 *    corrupted by manual preview dimming/brightening).
 * 3. **Photo/Video Processing**: Scene-adaptive low-light denoise + highlight recovery running
 *    strictly BEFORE overlays so existing timestamp/watermark/EXIF overlays remain 100% untouched.
 */
object CameraBrightnessEngine {

    private const val TAG = "CameraBrightnessEngine"

    /** Lowest practical dim screen brightness (prevents complete blackout while enabling max dim). */
    const val MIN_PRACTICAL_BRIGHTNESS = 0.02f

    /** Maximum supported hardware display brightness (100% for bright outdoor sunlight visibility). */
    const val MAX_SUPPORTED_BRIGHTNESS = 1.00f

    /** Comfortable default manual preview brightness. */
    const val DEFAULT_MANUAL_BRIGHTNESS = 0.65f

    fun clampBrightness(value: Float): Float {
        if (!value.isFinite()) return DEFAULT_MANUAL_BRIGHTNESS
        return value.coerceIn(MIN_PRACTICAL_BRIGHTNESS, MAX_SUPPORTED_BRIGHTNESS)
    }

    /**
     * Maps `[MIN_PRACTICAL_BRIGHTNESS..MAX_SUPPORTED_BRIGHTNESS]` cleanly to `0..100`% display readout.
     */
    fun brightnessToPercent(brightness: Float): Int {
        val clamped = clampBrightness(brightness)
        val norm = (clamped - MIN_PRACTICAL_BRIGHTNESS) / (MAX_SUPPORTED_BRIGHTNESS - MIN_PRACTICAL_BRIGHTNESS)
        return (norm * 100f).roundToInt().coerceIn(0, 100)
    }

    /**
     * Maps ambient light in lux to comfortable, perceptually smooth display brightness (`0.08f..1.0f`):
     * - Dark / night (`0..35 lux`): `0.08f..0.28f` (comfortable low-glare dim preview)
     * - Normal indoor / overcast (`35..1200 lux`): `0.28f..0.72f` (clear balanced visibility)
     * - Bright outdoor (`1200..10,000 lux`): `0.72f..0.96f` (high clarity)
     * - Direct sunlight (`>= 10,000 lux`): `1.00f` (maximum supported display brightness)
     */
    fun luxToTargetDisplayBrightness(lux: Float): Float {
        val safeLux = lux.coerceIn(0f, 120_000f)
        val brightness = when {
            safeLux <= 2f -> 0.08f
            safeLux < 35f -> {
                val t = ln((safeLux / 2f).toDouble()) / ln(35.0 / 2.0)
                (0.08 + t * 0.20).toFloat()
            }
            safeLux < 1_200f -> {
                val t = ln((safeLux / 35f).toDouble()) / ln(1_200.0 / 35.0)
                (0.28 + t * 0.44).toFloat()
            }
            safeLux < 10_000f -> {
                val t = ln((safeLux / 1_200f).toDouble()) / ln(10_000.0 / 1_200.0)
                (0.72 + t * 0.24).toFloat()
            }
            else -> MAX_SUPPORTED_BRIGHTNESS
        }
        return clampBrightness(brightness)
    }

    /**
     * Applies non-destructive Camera2 AE / AWB / Noise-Reduction / FPS lighting optimization
     * for live preview, photo capture, and active video recording without interrupting recording
     * or altering existing overlays, output formats, autofocus, or stabilization settings.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun applyLightConditionCameraTuning(
        controller: LifecycleCameraController,
        cameraControl: CameraControl?,
        capabilities: CameraHardwareCapabilities,
        lightZone: AmbientLightZone,
        isVideoMode: Boolean,
        targetVideoFps: Int,
        autoBrightnessEnabled: Boolean,
        recommendedAutoEvOffset: Int,
        userExposureIndex: Int
    ) {
        try {
            // 1. Engage multi-frame quality capture in low-light or high-light photo scenes where supported
            if (!isVideoMode) {
                val captureMode = when (lightZone) {
                    AmbientLightZone.LOW_LIGHT,
                    AmbientLightZone.HIGH_LIGHT -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    AmbientLightZone.NORMAL_LIGHT -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                }
                if (controller.imageCaptureMode != captureMode) {
                    controller.imageCaptureMode = captureMode
                }
            }
        } catch (ignored: Throwable) {}

        val control = cameraControl ?: return

        // 2. Coordinate sensor exposure index cleanly:
        // - Manual preview brightness NEVER modifies sensor EV (protects captured media!).
        // - In Auto mode, apply gentle anti-overexposure highlight guard in harsh sunlight,
        //   combined with any explicit user EV offset from the EV slider.
        try {
            val exposureState = controller.cameraInfo?.exposureState
            if (exposureState != null && exposureState.isExposureCompensationSupported) {
                val range = exposureState.exposureCompensationRange
                if (range.lower < range.upper) {
                    val targetEv = if (autoBrightnessEnabled) {
                        (userExposureIndex + recommendedAutoEvOffset).coerceIn(range.lower, range.upper)
                    } else {
                        userExposureIndex.coerceIn(range.lower, range.upper)
                    }
                    control.setExposureCompensationIndex(targetEv)
                }
            }
        } catch (ignored: Throwable) {}

        // 3. Camera2 CaptureRequestOptions: smooth continuous AE, stable AWB, low-light noise reduction
        try {
            val c2Control = Camera2CameraControl.from(control)
            val builder = CaptureRequestOptions.Builder()

            // Always maintain continuous auto-exposure & stable auto white balance (no sudden flickers)
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CameraMetadata.CONTROL_AE_MODE_ON
            )
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_AUTO
            )

            // Supported hardware noise reduction (prioritize HQ in low-light; preserve 60fps smoothness in video)
            if (capabilities.supportsHighQualityNoiseReduction) {
                val nrMode = when {
                    isVideoMode && targetVideoFps >= 60 -> CameraMetadata.NOISE_REDUCTION_MODE_FAST
                    lightZone == AmbientLightZone.LOW_LIGHT -> CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
                    else -> CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
                }
                builder.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, nrMode)
            }

            // Smooth exposure FPS ranges (allow lower floor in photo low-light for brighter cleaner frames;
            // keep stable video FPS floor so recording stays smooth and uninterrupted)
            if (isVideoMode) {
                val maxFps = if (targetVideoFps >= 60 && capabilities.supportedFpsOptions.contains(60)) 60 else 30
                val minFps = when {
                    maxFps >= 60 -> 30
                    lightZone == AmbientLightZone.LOW_LIGHT -> 20 // slight light-gathering headroom without stutter
                    else -> 24
                }
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(minFps, maxFps)
                )
            } else {
                val minFps = if (lightZone == AmbientLightZone.LOW_LIGHT) 10 else 15
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(minFps, 30)
                )
            }

            c2Control.addCaptureRequestOptions(builder.build())
        } catch (e: Throwable) {
            Log.w(TAG, "Camera2 light-condition tuning skipped gracefully: ${e.message}")
        }
    }

    /**
     * Optimizes the final captured photo bitmap strictly according to ACTUAL scene & ambient lighting
     * (NEVER copying the screen display brightness!).
     *
     * Runs strictly BEFORE `OverlayRenderer.renderOverlay(...)` so all existing timestamp, GPS,
     * location, project, chainage, LHS/RHS, lane, weather, watermark, logo, minimap, and EXIF
     * metadata remain 100% untouched.
     *
     * - **Low-light**: Gentle shadow recovery + edge-preserving luminance & chroma noise reduction
     *   (reduces grain & color noise while keeping crisp natural road/text textures without plastic smoothing).
     * - **High-light**: Highlight roll-off protection against overexposure + shadow detail preservation
     *   + natural colors without washed-out appearance.
     * - **Normal-light**: Preserves natural colors and clean textures with zero artificial distortion.
     */
    fun optimizeCapturedPhotoForLighting(
        source: Bitmap,
        ambientLux: Float? = null,
        ambientZone: AmbientLightZone? = null
    ): Bitmap {
        if (source.isRecycled || source.width <= 4 || source.height <= 4) return source

        return try {
            val metrics = CameraQualityEngine.analyzeScene(source)

            // Combine actual image photometry with ambient light hint (image photometry always guards
            // against misclassifying e.g. a bright screen/sign photographed in a dark room)
            val isEffectiveLowLight = metrics.isLowLight ||
                (ambientZone == AmbientLightZone.LOW_LIGHT && metrics.meanLuminance < 112f) ||
                (ambientLux != null && ambientLux < 35f && metrics.meanLuminance < 112f)

            val isEffectiveHighLight = (metrics.meanLuminance > 182f && metrics.highlightClipFraction > 0.08f) ||
                metrics.highlightClipFraction > 0.16f ||
                (ambientZone == AmbientLightZone.HIGH_LIGHT && metrics.highlightClipFraction > 0.06f)

            // In clean normal light with low noise & no clipping, keep natural sensor output untouched!
            if (!isEffectiveLowLight && !isEffectiveHighLight && metrics.estimatedNoiseLevel <= 6.5f) {
                return source
            }

            val w = source.width
            val h = source.height
            val totalPixels = w * h

            val lut = buildLightingToneLut(
                metrics = metrics,
                isLowLight = isEffectiveLowLight,
                isHighLight = isEffectiveHighLight
            )

            // Memory guard for ultra-high-MP images (> 20MP)
            if (totalPixels > 20_000_000) {
                return applyRowBandLut(source, lut)
            }

            val pixels = IntArray(totalPixels)
            source.getPixels(pixels, 0, w, 0, 0, w, h)

            // 1. Low-light edge-preserving luminance + chroma noise reduction FIRST (ISP-correct order:
            //    denoise raw dark signal BEFORE lifting shadows so grain is never amplified!)
            if (isEffectiveLowLight || metrics.estimatedNoiseLevel > 7.0f) {
                val snapshot = pixels.copyOf()
                val lumaThreshold = if (metrics.isVeryLowLight) 24 else 18
                val chromaThreshold = if (metrics.isVeryLowLight) 34 else 24
                val lumaBlend = if (metrics.isVeryLowLight) 0.58f else 0.45f
                val chromaBlend = if (metrics.isVeryLowLight) 0.68f else 0.55f

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

                        // Unrolled 4-neighbor edge-preserving accumulation (zero array allocation per pixel)
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

            // 2. Apply lighting-adaptive exposure & highlight/shadow protection LUT
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
            Log.w(TAG, "Light-condition photo optimization skipped safely: ${e.message}")
            source
        }
    }

    /**
     * Builds a 256-entry luminance tone curve tuned for actual low-light vs high-light conditions.
     */
    fun buildLightingToneLut(
        metrics: SceneAnalysisMetrics,
        isLowLight: Boolean,
        isHighLight: Boolean
    ): IntArray {
        val lut = IntArray(256)
        val gamma = when {
            isLowLight && metrics.isVeryLowLight -> 0.82
            isLowLight -> 0.90
            isHighLight -> 1.08
            else -> 1.0
        }
        val contrast = when {
            isLowLight -> 1.03
            isHighLight -> 1.02
            else -> 1.0
        }

        for (i in 0..255) {
            val norm = i / 255.0
            var v = norm.pow(gamma)

            // High-light highlight shoulder compression to preserve sky/cloud/concrete detail
            if (isHighLight && v > 0.72) {
                val excess = (v - 0.72) / 0.28
                v = 0.72 + 0.28 * (1.0 - exp(-1.85 * excess)) / (1.0 - exp(-1.85))
            }

            // Shadow detail preservation in high-contrast sunlight
            if (isHighLight && metrics.shadowFraction > 0.12f && v < 0.25) {
                v += (0.25 - v) * 0.12
            }

            val shaped = (v - 0.5) * contrast + 0.5
            lut[i] = (shaped * 255.0).roundToInt().coerceIn(0, 255)
        }
        return lut
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
