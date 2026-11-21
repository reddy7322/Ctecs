package com.example.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Hardware zoom and multi-lens capabilities for the currently active camera side
 * (rear or front). Strictly derived from real Camera2/CameraX hardware metadata;
 * never hardcodes or fabricates unsupported zoom ratios (0.5x, 0.6x, 10x, 100x, etc.).
 */
data class CameraLensZoomCapabilities(
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 10.0f,
    /**
     * Exact supported wider-FOV preset ratio to expose in the zoom control bar, or `null`
     * if the device does not have genuine ultra-wide / wider-FOV hardware on this camera side.
     *
     * Examples derived directly from hardware:
     * - Device reporting `minZoomRatio = 0.5f` -> `0.5f`
     * - Device reporting `minZoomRatio = 0.6f` -> `0.6f`
     * - Device reporting `minZoomRatio = 0.67f` -> `0.7f`
     * - Device with logical `1.0x` + bindable standalone ultra-wide ID -> optical focal ratio (e.g. `0.5f` or `0.6f`)
     * - Single-lens `1.0x` device -> `null`
     */
    val widePresetRatio: Float? = null,
    val usesStandalonePhysicalUltraWide: Boolean = false,
    val standaloneUltraWideCameraId: String? = null,
    val logicalWideCameraId: String? = null,
    val mainCameraId: String? = null,
    val physicalWideFocalLengthMm: Float? = null,
    val mainFocalLengthMm: Float? = null,
    val isLogicalMultiCamera: Boolean = false
) {
    val supportsUltraWide: Boolean
        get() = widePresetRatio != null

    val effectiveMinZoomRatio: Float
        get() = if (usesStandalonePhysicalUltraWide && widePresetRatio != null) {
            minOf(minZoomRatio, widePresetRatio)
        } else {
            minZoomRatio
        }
}

/**
 * Pure value representation of a physical/logical camera entry for deterministic,
 * JVM-testable hardware zoom & multi-lens resolution across all Android OEM HALs.
 */
data class CameraHardwareLensEntry(
    val cameraId: String,
    val lensFacing: Int,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
    val minFocalLengthMm: Float?,
    val isLogicalMultiCamera: Boolean,
    val physicalCameraIds: Set<String> = emptySet()
)

/**
 * Dynamic Hardware Camera Zoom & Multi-Lens Manager.
 *
 * Detects real device capabilities across:
 * 1. Logical multi-camera zoom ranges (`CONTROL_ZOOM_RATIO_RANGE` / CameraX `ZoomState.minZoomRatio..maxZoomRatio`),
 *    supporting any real range dynamically (e.g., `0.5x–100x`, `0.6x–10x`, `0.5x–30x`, `1x–4x`).
 * 2. Discrete standalone physical ultra-wide camera IDs exposed in `CameraManager` when the default
 *    logical rear camera starts at `1.0x` — strictly verified against CameraX `availableCameraInfos`
 *    (via `buildStrictCameraIdSelector`) so we never pretend a non-bindable camera switched lenses.
 */
object CameraUltraWideZoomManager {
    private const val TAG = "CameraUltraWideZoomMgr"
    private const val MAX_WIDE_THRESHOLD = 0.95f
    private const val WIDE_FOCAL_RATIO_THRESHOLD = 0.84f

    /**
     * Queries real device Camera2 + CameraX zoom and ultra-wide capabilities for the
     * requested camera facing (`isFrontCamera`).
     *
     * When [isCurrentlyBoundToStandaloneUltraWide] is `true`, we avoid letting the standalone
     * ultra-wide sensor's smaller `ZoomState.maxZoomRatio` collapse the rear system's full
     * zoom range (e.g. keeping `0.6x–10x` intact while previewing on the `0.6x` sensor).
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun detectCapabilities(
        context: Context,
        cameraInfo: CameraInfo?,
        isFrontCamera: Boolean,
        fallbackMinZoom: Float = 1.0f,
        fallbackMaxZoom: Float = 10.0f,
        isCurrentlyBoundToStandaloneUltraWide: Boolean = false,
        bindableCameraIds: Set<String>? = null
    ): CameraLensZoomCapabilities {
        val zoomState = if (isCurrentlyBoundToStandaloneUltraWide) null else cameraInfo?.zoomState?.value
        val activeMinZoom = (zoomState?.minZoomRatio ?: fallbackMinZoom).coerceAtLeast(0.1f)
        val activeMaxZoom = (zoomState?.maxZoomRatio ?: fallbackMaxZoom).coerceAtLeast(activeMinZoom)
        val activeCameraId = if (isCurrentlyBoundToStandaloneUltraWide) {
            null
        } else {
            try {
                cameraInfo?.let { Camera2CameraInfo.from(it).cameraId }
            } catch (_: Throwable) {
                null
            }
        }

        val targetFacing = if (isFrontCamera) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }

        val entries = inspectCamera2HardwareLenses(context)
        return resolveCapabilities(
            entries = entries,
            targetFacing = targetFacing,
            activeCameraId = activeCameraId,
            activeCameraMinZoom = activeMinZoom,
            activeCameraMaxZoom = activeMaxZoom,
            bindableCameraIds = bindableCameraIds
        )
    }

    /**
     * Pure deterministic hardware resolver used at runtime and in unit tests.
     */
    fun resolveCapabilities(
        entries: List<CameraHardwareLensEntry>,
        targetFacing: Int,
        activeCameraId: String? = null,
        activeCameraMinZoom: Float = 1.0f,
        activeCameraMaxZoom: Float = 10.0f,
        bindableCameraIds: Set<String>? = null
    ): CameraLensZoomCapabilities {
        val sameFacingAll = entries.filter { it.lensFacing == targetFacing }
        val sameFacingBindable = if (bindableCameraIds != null) {
            sameFacingAll.filter { bindableCameraIds.contains(it.cameraId) }
                .ifEmpty { sameFacingAll }
        } else {
            sameFacingAll
        }

        val mainEntry = sameFacingBindable.firstOrNull { it.cameraId == activeCameraId }
            ?: sameFacingBindable.firstOrNull { it.isLogicalMultiCamera && it.minZoomRatio <= MAX_WIDE_THRESHOLD }
            ?: sameFacingBindable.firstOrNull { it.isLogicalMultiCamera }
            ?: sameFacingBindable.firstOrNull()

        // Check if ANY bindable camera on this facing natively supports zoom < 1.0x (logical multi-cam)
        val wideLogicalEntry = sameFacingBindable
            .filter { it.minZoomRatio <= MAX_WIDE_THRESHOLD }
            .minByOrNull { it.minZoomRatio }

        val effectiveLogicalEntry = wideLogicalEntry ?: mainEntry
        val hasRealHardwareRange = effectiveLogicalEntry != null
        val logicalMinZoom = if (hasRealHardwareRange) {
            minOf(
                activeCameraMinZoom.coerceAtLeast(0.1f),
                effectiveLogicalEntry!!.minZoomRatio.coerceAtLeast(0.1f)
            )
        } else {
            activeCameraMinZoom.coerceAtLeast(0.1f)
        }
        val logicalMaxZoom = if (hasRealHardwareRange) {
            maxOf(
                activeCameraMaxZoom,
                effectiveLogicalEntry!!.maxZoomRatio,
                mainEntry?.maxZoomRatio ?: 1.0f
            ).coerceAtLeast(logicalMinZoom)
        } else {
            activeCameraMaxZoom.coerceAtLeast(logicalMinZoom)
        }

        // Case 1: Logical multi-camera (or active camera) natively supports zoom < 1.0x.
        // Calling CameraControl.setZoomRatio(ratio) switches physical ultra-wide/wide/tele sensors
        // seamlessly with zero session restart or black frames.
        if (logicalMinZoom <= MAX_WIDE_THRESHOLD) {
            val widePreset = computeNativeWidePresetRatio(logicalMinZoom)
            return CameraLensZoomCapabilities(
                minZoomRatio = logicalMinZoom,
                maxZoomRatio = logicalMaxZoom,
                widePresetRatio = widePreset,
                usesStandalonePhysicalUltraWide = false,
                standaloneUltraWideCameraId = null,
                logicalWideCameraId = wideLogicalEntry?.cameraId,
                mainCameraId = mainEntry?.cameraId ?: activeCameraId,
                physicalWideFocalLengthMm = null,
                mainFocalLengthMm = mainEntry?.minFocalLengthMm,
                isLogicalMultiCamera = effectiveLogicalEntry?.isLogicalMultiCamera ?: (logicalMinZoom < 0.99f)
            )
        }

        // Case 2: Logical rear camera starts at 1.0x — look for a distinct bindable standalone
        // rear physical ultra-wide camera with a genuinely shorter focal length.
        val mainFocal = mainEntry?.minFocalLengthMm
        if (targetFacing == CameraCharacteristics.LENS_FACING_BACK &&
            mainEntry != null &&
            mainFocal != null &&
            mainFocal > 0f
        ) {
            val standaloneCandidate = sameFacingBindable
                .filter { candidate ->
                    candidate.cameraId != mainEntry.cameraId &&
                        candidate.minFocalLengthMm != null &&
                        candidate.minFocalLengthMm > 0.5f &&
                        candidate.minFocalLengthMm < mainFocal * WIDE_FOCAL_RATIO_THRESHOLD
                }
                .minByOrNull { candidate ->
                    candidate.minFocalLengthMm ?: Float.MAX_VALUE
                }

            if (standaloneCandidate != null && standaloneCandidate.minFocalLengthMm != null) {
                val rawEquiv = (standaloneCandidate.minFocalLengthMm / mainFocal).coerceIn(0.4f, 0.9f)
                val roundedEquiv = ((rawEquiv * 10f).roundToInt() / 10f).coerceIn(0.5f, 0.9f)
                return CameraLensZoomCapabilities(
                    minZoomRatio = logicalMinZoom,
                    maxZoomRatio = logicalMaxZoom,
                    widePresetRatio = roundedEquiv,
                    usesStandalonePhysicalUltraWide = true,
                    standaloneUltraWideCameraId = standaloneCandidate.cameraId,
                    logicalWideCameraId = null,
                    mainCameraId = mainEntry.cameraId,
                    physicalWideFocalLengthMm = standaloneCandidate.minFocalLengthMm,
                    mainFocalLengthMm = mainFocal,
                    isLogicalMultiCamera = false
                )
            }
        }

        // Case 3: Hardware has no ultra-wide lens on this side — never show fake 0.5x/0.6x.
        return CameraLensZoomCapabilities(
            minZoomRatio = logicalMinZoom,
            maxZoomRatio = logicalMaxZoom,
            widePresetRatio = null,
            usesStandalonePhysicalUltraWide = false,
            standaloneUltraWideCameraId = null,
            logicalWideCameraId = null,
            mainCameraId = mainEntry?.cameraId ?: activeCameraId,
            physicalWideFocalLengthMm = null,
            mainFocalLengthMm = mainEntry?.minFocalLengthMm,
            isLogicalMultiCamera = mainEntry?.isLogicalMultiCamera ?: false
        )
    }

    /**
     * Computes the exact native wide preset ratio directly from real hardware `minZoomRatio <= 0.95f`.
     * Never forces `0.5x` when hardware minimum is `0.6x`, and never hides `0.5x` when hardware
     * genuinely supports `0.5x`.
     *
     * Examples:
     * - `0.50f` -> `0.5f`
     * - `0.51f` -> `0.5f` (within small sensor rounding tolerance; clamped to hardware min at apply time)
     * - `0.60f` -> `0.6f`
     * - `0.67f` -> `0.7f`
     * - `0.78f` -> `0.8f`
     */
    fun computeNativeWidePresetRatio(minZoomRatio: Float): Float? {
        if (!minZoomRatio.isFinite() || minZoomRatio > MAX_WIDE_THRESHOLD) return null
        val clampedMin = minZoomRatio.coerceAtLeast(0.2f)
        val nearestTenth = (clampedMin * 10f).roundToInt() / 10f
        return if (abs(nearestTenth - clampedMin) <= 0.025f) {
            nearestTenth.coerceIn(0.2f, MAX_WIDE_THRESHOLD)
        } else {
            val tenthsUp = ceil((clampedMin * 10.0) - 1e-4).toInt() / 10f
            tenthsUp.coerceIn(clampedMin, MAX_WIDE_THRESHOLD)
        }
    }

    /**
     * Backwards-compatible alias for [computeNativeWidePresetRatio].
     */
    fun computeNearestWidePresetRatio(minZoomRatio: Float): Float? =
        computeNativeWidePresetRatio(minZoomRatio)

    /**
     * Dynamically builds the ordered list of quick zoom preset ratios strictly from the device's
     * real supported `[effectiveMinZoom, effectiveMaxZoom]` range.
     *
     * Adapts automatically to any hardware:
     * - `0.5x–100x` -> `[0.5, 1, 2, 5, 10, 30, 100]`
     * - `0.5x–30x`  -> `[0.5, 1, 2, 5, 10, 30]`
     * - `0.6x–10x`  -> `[0.6, 1, 2, 5, 10]`
     * - `1.0x–8.0x` -> `[1, 2, 5, 8]`
     * - `1.0x–4.0x` -> `[1, 2, 4]`
     * - `1.0x–1.0x` -> `[1]`
     */
    fun resolveZoomPresets(
        minZoomRatio: Float,
        maxZoomRatio: Float,
        capabilities: CameraLensZoomCapabilities? = null
    ): List<Float> {
        val list = mutableListOf<Float>()
        val widePreset = capabilities?.widePresetRatio
            ?: computeNativeWidePresetRatio(minZoomRatio)

        if (widePreset != null && widePreset < 0.99f) {
            list.add(widePreset)
        }
        list.add(1.0f)

        val effectiveMax = maxOf(
            maxZoomRatio,
            capabilities?.maxZoomRatio ?: maxZoomRatio
        ).coerceAtLeast(1.0f)

        val candidates: List<Float> = when {
            effectiveMax >= 60.0f -> listOf(2.0f, 5.0f, 10.0f, 30.0f, floor(effectiveMax).coerceAtLeast(50.0f))
            effectiveMax >= 25.0f -> listOf(2.0f, 5.0f, 10.0f, floor(effectiveMax))
            effectiveMax >= 10.0f -> listOf(2.0f, 5.0f, 10.0f) + if (effectiveMax >= 19.5f) listOf(floor(effectiveMax)) else emptyList()
            effectiveMax >= 5.0f -> listOf(2.0f, 5.0f) + if (effectiveMax >= 7.5f) listOf(floor(effectiveMax)) else emptyList()
            effectiveMax >= 3.0f -> listOf(2.0f, floor(effectiveMax))
            effectiveMax >= 2.0f -> listOf(2.0f)
            effectiveMax >= 1.45f -> listOf(((effectiveMax * 10f).toInt() / 10f))
            else -> emptyList()
        }

        for (step in candidates) {
            if (step <= effectiveMax + 0.05f && list.none { abs(it - step) < 0.15f }) {
                list.add(step.coerceAtMost(effectiveMax))
            }
        }
        return list
    }

    /**
     * Computes the exact hardware zoom ratio to send to `CameraController.setZoomRatio(...)`:
     * - When using a standalone physical ultra-wide lens for `requestedZoom < 1.0f`, maps
     *   `requestedZoom` linearly relative to the ultra-wide lens's native FOV (`1.0f` at `widePresetRatio`,
     *   increasing smoothly as the user pinches toward `1.0f`).
     * - Otherwise clamps `requestedZoom` strictly into the bound camera's real supported range
     *   `[hardwareMinZoom, hardwareMaxZoom]`.
     */
    fun computeTargetHardwareZoomRatio(
        requestedZoom: Float,
        hardwareMinZoom: Float,
        hardwareMaxZoom: Float,
        useStandaloneUltraWide: Boolean,
        widePresetRatio: Float?
    ): Float {
        val safeMin = hardwareMinZoom.takeIf { it.isFinite() && it > 0f } ?: 1.0f
        val safeMax = hardwareMaxZoom.takeIf { it.isFinite() && it >= safeMin } ?: safeMin
        val safeRequested = requestedZoom.takeIf { it.isFinite() && it > 0f } ?: 1.0f

        if (useStandaloneUltraWide && safeRequested < 0.995f) {
            val nativeWide = (widePresetRatio ?: 0.6f).coerceIn(0.3f, 0.95f)
            val relativeOnUltraWideSensor = (safeRequested / nativeWide).coerceAtLeast(1.0f)
            return relativeOnUltraWideSensor.coerceIn(safeMin, safeMax)
        }
        return safeRequested.coerceIn(safeMin, safeMax)
    }

    /**
     * Logarithmic slider helpers so wide zoom ranges (e.g. `0.5x..100x`) give smooth,
     * uniform tactile control across ultra-wide (`0.5x–1x`), standard (`1x–5x`), and tele (`5x–100x`).
     */
    fun zoomToSliderFraction(zoom: Float, minZoom: Float, maxZoom: Float): Float {
        val safeMin = minZoom.coerceAtLeast(0.1f)
        val safeMax = maxZoom.coerceAtLeast(safeMin + 0.01f)
        val clamped = zoom.coerceIn(safeMin, safeMax)
        val denom = ln(safeMax / safeMin)
        if (!denom.isFinite() || denom <= 1e-6f) return 0f
        return (ln(clamped / safeMin) / denom).coerceIn(0f, 1f)
    }

    fun sliderFractionToZoom(fraction: Float, minZoom: Float, maxZoom: Float): Float {
        val safeMin = minZoom.coerceAtLeast(0.1f)
        val safeMax = maxZoom.coerceAtLeast(safeMin + 0.01f)
        val f = fraction.coerceIn(0f, 1f)
        return (safeMin * (safeMax / safeMin).toDouble().pow(f.toDouble())).toFloat()
            .coerceIn(safeMin, safeMax)
    }

    /**
     * Builds a STRICT CameraX [CameraSelector] matching ONLY the requested Camera2 ID.
     * Returns an empty list inside the filter if that exact ID is not exposed by CameraX,
     * so `controller.hasCamera(selector)` accurately reports `false` instead of silently
     * binding the standard 1.0x camera!
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun buildStrictCameraIdSelector(
        lensFacing: Int,
        cameraId: String
    ): CameraSelector {
        return CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter { info ->
                    try {
                        Camera2CameraInfo.from(info).cameraId == cameraId
                    } catch (_: Throwable) {
                        false
                    }
                }
            }
            .build()
    }

    /**
     * Builds a CameraX [CameraSelector] that targets:
     * - Front camera when `isFrontCamera == true`
     * - Strict standalone physical ultra-wide camera ID when `useStandaloneUltraWide == true`
     * - Preferred logical multi-camera ID (when a specific back camera exposes `< 1.0x` zoom)
     * - Otherwise `CameraSelector.DEFAULT_BACK_CAMERA`
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun buildCameraSelector(
        isFrontCamera: Boolean,
        useStandaloneUltraWide: Boolean,
        standaloneUltraWideCameraId: String?,
        logicalWideCameraId: String? = null
    ): CameraSelector {
        if (isFrontCamera) return CameraSelector.DEFAULT_FRONT_CAMERA
        if (useStandaloneUltraWide && !standaloneUltraWideCameraId.isNullOrBlank()) {
            return buildStrictCameraIdSelector(
                lensFacing = CameraSelector.LENS_FACING_BACK,
                cameraId = standaloneUltraWideCameraId
            )
        }
        if (!logicalWideCameraId.isNullOrBlank()) {
            return buildStrictCameraIdSelector(
                lensFacing = CameraSelector.LENS_FACING_BACK,
                cameraId = logicalWideCameraId
            )
        }
        return CameraSelector.DEFAULT_BACK_CAMERA
    }

    /**
     * Formats a zoom ratio cleanly for badges (`"0.5x"`, `"0.6x"`, `"1x"`, `"2x"`, `"10x"`, `"100x"`, `"1.5x"`).
     */
    fun formatZoomBadge(ratio: Float): String {
        return if (ratio < 1.0f) {
            String.format(Locale.US, "%.1fx", ratio)
        } else if (ratio >= 10f || abs(ratio - ratio.roundToInt().toFloat()) < 0.05f) {
            "${ratio.roundToInt()}x"
        } else {
            String.format(Locale.US, "%.1fx", ratio)
        }
    }

    /**
     * Formats a compact preset chip label (`"0.5"`, `"0.6"`, `"1"`, `"2"`, `"5"`, `"10"`, `"30"`, `"100"`).
     */
    fun formatPresetChipLabel(preset: Float): String {
        return if (preset < 1.0f) {
            String.format(Locale.US, "%.1f", preset)
        } else if (preset >= 10f || abs(preset - preset.roundToInt().toFloat()) < 0.05f) {
            "${preset.roundToInt()}"
        } else {
            String.format(Locale.US, "%.1f", preset)
        }
    }

    private fun inspectCamera2HardwareLenses(context: Context): List<CameraHardwareLensEntry> {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return emptyList()
            val results = mutableListOf<CameraHardwareLensEntry>()
            for (cameraId in manager.cameraIdList) {
                try {
                    val chars = manager.getCameraCharacteristics(cameraId)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
                    val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                        ?: IntArray(0)
                    val isLogical = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
                    val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        try {
                            chars.physicalCameraIds
                        } catch (_: Throwable) {
                            emptySet()
                        }
                    } else {
                        emptySet()
                    }
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val minFocal = focalLengths?.minOrNull()

                    var minZoom = 1.0f
                    var maxZoom = (chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f)
                        .coerceAtLeast(1.0f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                        if (zoomRange != null) {
                            minZoom = zoomRange.lower.coerceAtLeast(0.1f)
                            maxZoom = zoomRange.upper.coerceAtLeast(minZoom)
                        }
                    }

                    results.add(
                        CameraHardwareLensEntry(
                            cameraId = cameraId,
                            lensFacing = facing,
                            minZoomRatio = minZoom,
                            maxZoomRatio = maxZoom,
                            minFocalLengthMm = minFocal,
                            isLogicalMultiCamera = isLogical,
                            physicalCameraIds = physicalIds
                        )
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Skipping camera ID $cameraId during zoom capability detection: ${e.message}")
                }
            }
            results
        } catch (e: Throwable) {
            Log.w(TAG, "Camera2 hardware zoom inspection failed: ${e.message}")
            emptyList()
        }
    }
}
