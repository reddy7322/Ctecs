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
import kotlin.math.roundToInt

/**
 * Hardware zoom and ultra-wide lens capabilities for the currently active camera side
 * (rear or front). Strictly derived from real Camera2/CameraX hardware metadata; never
 * synthesizes or fakes an unsupported ultra-wide lens.
 */
data class CameraLensZoomCapabilities(
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 10.0f,
    /**
     * Exact supported wider-FOV preset ratio to expose in the zoom control bar, or `null`
     * if the device does not have genuine ultra-wide / wider-FOV hardware on this camera side.
     *
     * Rules:
     * - If logical multi-camera supports `0.6x` (`minZoomRatio <= 0.605f`), this is `0.6f`.
     * - If logical multi-camera has a wider FOV narrower than `0.6x` (e.g. `0.7x` or `0.8x`,
     *   within `0.61f..0.95f`), this is the nearest genuine supported wider ratio rounded to 0.1.
     * - If logical zoom starts at `1.0x` (`>= 0.99f`) AND a separate standalone physical
     *   ultra-wide rear camera ID exists, this is its optical equivalent ratio (clamped to
     *   `0.5f..0.8f`, typically `0.6f`), and [usesStandalonePhysicalUltraWide] is `true`.
     * - Otherwise `null` (no wider preset is shown).
     */
    val widePresetRatio: Float? = null,
    val usesStandalonePhysicalUltraWide: Boolean = false,
    val standaloneUltraWideCameraId: String? = null,
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
 * Pure value representation of a physical/logical camera entry for unit-testable
 * hardware ultra-wide resolution across single-lens, logical multi-camera, and
 * separate-ID multi-camera Android devices.
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
 * Hardware-backed 0.6x Ultra-Wide Zoom & Multi-Camera Lens Manager.
 *
 * Detects:
 * 1. Logical multi-camera zoom ranges (`CONTROL_ZOOM_RATIO_RANGE` / CameraX `ZoomState.minZoomRatio`),
 *    which seamlessly transition to the physical ultra-wide sensor on modern Pixel, Samsung,
 *    OnePlus, Xiaomi, and Motorola devices via `CameraControl.setZoomRatio(0.6f)` with zero
 *    camera restarts or black frames.
 * 2. Discrete standalone physical ultra-wide cameras exposed separately in `CameraManager`
 *    (common on older/budget OEM HALs where the ultra-wide sensor is not merged into the
 *    logical rear camera's zoom range).
 */
object CameraUltraWideZoomManager {
    private const val TAG = "CameraUltraWideZoomMgr"
    const val TARGET_ULTRA_WIDE_RATIO = 0.6f
    private const val MAX_WIDE_THRESHOLD = 0.95f
    private const val WIDE_FOCAL_RATIO_THRESHOLD = 0.82f

    /**
     * Queries real device Camera2 + CameraX zoom and ultra-wide capabilities for the
     * requested camera facing (`isFrontCamera`).
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun detectCapabilities(
        context: Context,
        cameraInfo: CameraInfo?,
        isFrontCamera: Boolean,
        fallbackMinZoom: Float = 1.0f,
        fallbackMaxZoom: Float = 10.0f
    ): CameraLensZoomCapabilities {
        val zoomState = cameraInfo?.zoomState?.value
        val activeMinZoom = (zoomState?.minZoomRatio ?: fallbackMinZoom).coerceAtLeast(0.1f)
        val activeMaxZoom = (zoomState?.maxZoomRatio ?: fallbackMaxZoom).coerceAtLeast(activeMinZoom)
        val activeCameraId = try {
            cameraInfo?.let { Camera2CameraInfo.from(it).cameraId }
        } catch (_: Throwable) {
            null
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
            activeCameraMaxZoom = activeMaxZoom
        )
    }

    /**
     * Pure deterministic resolver used both at runtime and in Robolectric/JVM unit tests.
     */
    fun resolveCapabilities(
        entries: List<CameraHardwareLensEntry>,
        targetFacing: Int,
        activeCameraId: String? = null,
        activeCameraMinZoom: Float = 1.0f,
        activeCameraMaxZoom: Float = 10.0f
    ): CameraLensZoomCapabilities {
        val sameFacing = entries.filter { it.lensFacing == targetFacing }
        val mainEntry = sameFacing.firstOrNull { it.cameraId == activeCameraId }
            ?: sameFacing.firstOrNull { it.isLogicalMultiCamera }
            ?: sameFacing.firstOrNull()

        val logicalMinZoom = minOf(
            activeCameraMinZoom.coerceAtLeast(0.1f),
            mainEntry?.minZoomRatio?.coerceAtLeast(0.1f) ?: activeCameraMinZoom.coerceAtLeast(0.1f)
        )
        val logicalMaxZoom = maxOf(
            activeCameraMaxZoom,
            mainEntry?.maxZoomRatio ?: activeCameraMaxZoom
        ).coerceAtLeast(logicalMinZoom)

        // Case 1: Active logical multi-camera (or wide-capable camera) supports zoom < 1.0x natively.
        // Using CameraX setZoomRatio switches lenses seamlessly without restarting the camera session.
        if (logicalMinZoom <= MAX_WIDE_THRESHOLD) {
            val widePreset = computeNearestWidePresetRatio(logicalMinZoom)
            return CameraLensZoomCapabilities(
                minZoomRatio = logicalMinZoom,
                maxZoomRatio = logicalMaxZoom,
                widePresetRatio = widePreset,
                usesStandalonePhysicalUltraWide = false,
                standaloneUltraWideCameraId = null,
                mainCameraId = mainEntry?.cameraId ?: activeCameraId,
                physicalWideFocalLengthMm = null,
                mainFocalLengthMm = mainEntry?.minFocalLengthMm,
                isLogicalMultiCamera = mainEntry?.isLogicalMultiCamera ?: (logicalMinZoom < 0.99f)
            )
        }

        // Case 2: Logical camera starts at 1.0x — check if OEM exposed a standalone physical
        // ultra-wide camera ID on the same side (strictly rear cameras; avoid telephoto/macro/depth).
        val mainFocal = mainEntry?.minFocalLengthMm
        if (targetFacing == CameraCharacteristics.LENS_FACING_BACK &&
            mainEntry != null &&
            mainFocal != null &&
            mainFocal > 0f
        ) {
            val standaloneCandidate = sameFacing
                .filter { candidate ->
                    candidate.cameraId != mainEntry.cameraId &&
                        !mainEntry.physicalCameraIds.contains(candidate.cameraId) &&
                        candidate.minFocalLengthMm != null &&
                        candidate.minFocalLengthMm > 0.5f &&
                        candidate.minFocalLengthMm < mainFocal * WIDE_FOCAL_RATIO_THRESHOLD
                }
                .minByOrNull { candidate ->
                    // Prefer the lens closest to 0.6x equivalent focal length
                    val equiv = (candidate.minFocalLengthMm ?: mainFocal) / mainFocal
                    abs(equiv - TARGET_ULTRA_WIDE_RATIO)
                }

            if (standaloneCandidate != null && standaloneCandidate.minFocalLengthMm != null) {
                val rawEquiv = (standaloneCandidate.minFocalLengthMm / mainFocal).coerceIn(0.4f, 0.9f)
                val roundedEquiv = ((rawEquiv * 10f).roundToInt() / 10f).coerceIn(0.5f, 0.9f)
                // If equivalent FOV is right around 0.55x..0.65x, expose clean 0.6x; otherwise exact nearest 0.1x
                val presetRatio = if (roundedEquiv in 0.5f..0.6f && abs(rawEquiv - TARGET_ULTRA_WIDE_RATIO) <= 0.08f) {
                    TARGET_ULTRA_WIDE_RATIO
                } else {
                    roundedEquiv
                }
                return CameraLensZoomCapabilities(
                    minZoomRatio = logicalMinZoom,
                    maxZoomRatio = logicalMaxZoom,
                    widePresetRatio = presetRatio,
                    usesStandalonePhysicalUltraWide = true,
                    standaloneUltraWideCameraId = standaloneCandidate.cameraId,
                    mainCameraId = mainEntry.cameraId,
                    physicalWideFocalLengthMm = standaloneCandidate.minFocalLengthMm,
                    mainFocalLengthMm = mainFocal,
                    isLogicalMultiCamera = false
                )
            }
        }

        // Case 3: Hardware has no ultra-wide lens on this side — never fake 0.6x.
        return CameraLensZoomCapabilities(
            minZoomRatio = logicalMinZoom,
            maxZoomRatio = logicalMaxZoom,
            widePresetRatio = null,
            usesStandalonePhysicalUltraWide = false,
            standaloneUltraWideCameraId = null,
            mainCameraId = mainEntry?.cameraId ?: activeCameraId,
            physicalWideFocalLengthMm = null,
            mainFocalLengthMm = mainEntry?.minFocalLengthMm,
            isLogicalMultiCamera = mainEntry?.isLogicalMultiCamera ?: false
        )
    }

    /**
     * Computes the exact wide preset ratio from a genuine hardware `minZoomRatio <= 0.95f`:
     * - Prefers exact `0.6x` whenever hardware supports `<= 0.605f` (covers 0.5x and 0.6x sensors).
     * - Otherwise uses the nearest genuine supported wider ratio rounded UP to 0.1 (e.g., 0.66 -> 0.7x,
     *   0.78 -> 0.8x) so the displayed preset is always actually reachable by the lens.
     */
    fun computeNearestWidePresetRatio(minZoomRatio: Float): Float? {
        if (minZoomRatio > MAX_WIDE_THRESHOLD) return null
        if (minZoomRatio <= TARGET_ULTRA_WIDE_RATIO + 0.005f) {
            return TARGET_ULTRA_WIDE_RATIO
        }
        val tenthsUp = kotlin.math.ceil((minZoomRatio * 10.0) - 1e-4).toInt() / 10f
        return tenthsUp.coerceIn(minZoomRatio, MAX_WIDE_THRESHOLD)
    }

    /**
     * Builds the ordered list of quick zoom preset ratios supported by the active hardware.
     * - Includes the supported wide preset (`0.6x`, or nearest genuine wider FOV) ONLY when supported.
     * - Always includes `1.0x` (Standard).
     * - Preserves existing zoom levels (`2x`, `3x`, `5x`, `8x`) where supported by `maxZoomRatio`.
     */
    fun resolveZoomPresets(
        minZoomRatio: Float,
        maxZoomRatio: Float,
        capabilities: CameraLensZoomCapabilities? = null
    ): List<Float> {
        val list = mutableListOf<Float>()
        val widePreset = capabilities?.widePresetRatio
            ?: computeNearestWidePresetRatio(minZoomRatio)

        if (widePreset != null && widePreset < 0.99f) {
            list.add(widePreset)
        }
        list.add(1.0f)

        val effectiveMax = maxOf(maxZoomRatio, capabilities?.maxZoomRatio ?: maxZoomRatio)
        if (effectiveMax >= 2.0f) list.add(2.0f)
        if (effectiveMax >= 3.0f) list.add(3.0f)
        if (effectiveMax >= 5.0f) list.add(5.0f)
        if (effectiveMax >= 8.0f) list.add(8.0f)
        return list
    }

    /**
     * Builds a CameraX [CameraSelector] that seamlessly targets the standalone physical
     * ultra-wide camera ID when required (and falls back gracefully to default rear/front
     * camera if the ID is unavailable).
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun buildCameraSelector(
        isFrontCamera: Boolean,
        useStandaloneUltraWide: Boolean,
        standaloneUltraWideCameraId: String?
    ): CameraSelector {
        if (isFrontCamera) return CameraSelector.DEFAULT_FRONT_CAMERA
        if (!useStandaloneUltraWide || standaloneUltraWideCameraId.isNullOrBlank()) {
            return CameraSelector.DEFAULT_BACK_CAMERA
        }
        return CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .addCameraFilter { cameraInfos ->
                val exact = cameraInfos.filter { info ->
                    try {
                        Camera2CameraInfo.from(info).cameraId == standaloneUltraWideCameraId
                    } catch (_: Throwable) {
                        false
                    }
                }
                exact.ifEmpty { cameraInfos.take(1) }
            }
            .build()
    }

    /**
     * Formats a zoom ratio cleanly for badges (`"0.6x"`, `"1x"`, `"2x"`, `"1.5x"`).
     */
    fun formatZoomBadge(ratio: Float): String {
        return if (ratio < 1.0f) {
            String.format(Locale.US, "%.1fx", ratio)
        } else if (abs(ratio - ratio.roundToInt().toFloat()) < 0.05f) {
            "${ratio.roundToInt()}x"
        } else {
            String.format(Locale.US, "%.1fx", ratio)
        }
    }

    /**
     * Formats a compact preset chip label (`"0.6"`, `"1"`, `"2"`, `"3"`, `"5"`, `"8"`).
     */
    fun formatPresetChipLabel(preset: Float): String {
        return if (preset < 1.0f) {
            String.format(Locale.US, "%.1f", preset)
        } else if (abs(preset - preset.roundToInt().toFloat()) < 0.05f) {
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
                    Log.w(TAG, "Skipping camera ID $cameraId during ultra-wide detection: ${e.message}")
                }
            }
            results
        } catch (e: Throwable) {
            Log.w(TAG, "Camera2 ultra-wide hardware inspection failed: ${e.message}")
            emptyList()
        }
    }
}
