package com.example.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Standardized Camera2 hardware abstraction level across Android OEMs
 * (Samsung, Pixel, OnePlus, Xiaomi, Motorola, Vivo, Oppo, Realme, Nothing, Sony, etc.).
 */
enum class CameraHardwareLevel(val label: String) {
    LEVEL_3("LEVEL_3"),
    FULL("FULL"),
    LIMITED("LIMITED"),
    EXTERNAL("EXTERNAL"),
    LEGACY("LEGACY"),
    UNKNOWN("UNKNOWN")
}

/**
 * Unified capability-driven profile of the active Android camera sensor and lens array.
 * Populated strictly from live Camera2 / CameraX hardware queries — never hardcoded to any OEM model.
 */
data class DeviceCameraProfile(
    val cameraId: String? = null,
    val isFrontCamera: Boolean = false,
    val hardwareLevel: CameraHardwareLevel = CameraHardwareLevel.UNKNOWN,
    val sensorAspectRatio: Float = 4f / 3f,
    val optimalPhotoSize: Size? = null,
    val effectiveMegapixels: Int = 0,
    val photoResolutionBadge: String = "",
    val bestVideoResolution: String = "1920 x 1080",
    val videoResolutionBadge: String = "FHD",
    val hasFlashUnit: Boolean = true,
    val supportsOis: Boolean = false,
    val supportsEis: Boolean = false,
    val supportsContinuousAf: Boolean = true,
    val supportsZsl: Boolean = false,
    val supportedFocalLengthsMm: List<Float> = emptyList(),
    val physicalLensCount: Int = 1,
    val supportedFpsOptions: List<Int> = listOf(30),
    val evMinStops: Float = -2f,
    val evMaxStops: Float = 2f,
    val zoomCapabilities: CameraLensZoomCapabilities = CameraLensZoomCapabilities()
) {
    /** Compact native top-bar capability badge matching active capture mode (e.g. "12M" or "4K"). */
    fun activeModeBadge(isVideoMode: Boolean): String {
        return if (isVideoMode) videoResolutionBadge else photoResolutionBadge
    }
}

/**
 * Universal Multi-Device Android Camera Capability Detector.
 *
 * Inspects actual Camera2 + CameraX sensor, lens, zoom, resolution, stabilization, flash, and
 * exposure capabilities at runtime so CTECS Camera adapts gracefully across every Android device
 * without hardcoding device-specific zoom steps, lens counts, or unsupported modes.
 */
object CameraDeviceCapabilityManager {

    private const val TAG = "CameraDeviceCaps"

    fun inspectActiveCamera(
        context: Context,
        cameraInfo: CameraInfo?,
        isFrontCamera: Boolean,
        zoomCapabilities: CameraLensZoomCapabilities,
        exposureCapabilities: CaptureExposureCapabilities? = null
    ): DeviceCameraProfile {
        val hwQuality = CameraQualityEngine.queryHardwareCapabilities(
            context = context,
            isFrontCamera = isFrontCamera,
            cameraInfo = cameraInfo
        )
        val sensorRatio = CameraResolutionHelper.resolveNativeSensorAspectRatio(
            context = context,
            isFrontCamera = isFrontCamera,
            isVideoMode = false,
            cameraInfo = cameraInfo
        )
        val bestVideoRes = CameraResolutionHelper.resolveBestAutomaticVideoResolution(
            context = context,
            isFrontCamera = isFrontCamera,
            cameraInfo = cameraInfo
        )

        var cameraId: String? = null
        var hwLevelInt: Int? = null
        var jpegSizes: List<Size> = emptyList()
        var hasFlash = !isFrontCamera
        var focalLengths = mutableListOf<Float>()
        var physicalCount = 1
        var supportsZsl = false

        try {
            hasFlash = try {
                cameraInfo?.hasFlashUnit() ?: !isFrontCamera
            } catch (_: Throwable) {
                !isFrontCamera
            }
            supportsZsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && try {
                cameraInfo?.isZslSupported == true
            } catch (_: Throwable) {
                false
            }

            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraId = CameraResolutionHelper.resolveActiveCameraId(
                context = context,
                isFrontCamera = isFrontCamera,
                cameraInfo = cameraInfo
            )
            if (manager != null && cameraId != null) {
                val chars = manager.getCameraCharacteristics(cameraId)
                hwLevelInt = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (flashAvailable != null) {
                    hasFlash = flashAvailable
                }
                val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val rawJpeg = streamMap?.getOutputSizes(ImageFormat.JPEG)
                if (rawJpeg != null && rawJpeg.isNotEmpty()) {
                    jpegSizes = rawJpeg.toList()
                }
                val primaryFocals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                if (primaryFocals != null) {
                    primaryFocals.filter { it.isFinite() && it > 0.1f }.forEach { focalLengths.add(it) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val physicalIds = try {
                        chars.physicalCameraIds
                    } catch (_: Throwable) {
                        emptySet()
                    }
                    if (physicalIds.isNotEmpty()) {
                        physicalCount = physicalIds.size
                        for (pid in physicalIds) {
                            try {
                                val pChars = manager.getCameraCharacteristics(pid)
                                val pFocals = pChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                                pFocals?.filter { it.isFinite() && it > 0.1f }?.forEach { focalLengths.add(it) }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Hardware capability inspection fallback: ${t.message}")
        }

        val optimalPhotoSize = if (jpegSizes.isNotEmpty()) {
            CameraResolutionHelper.selectOptimalNativePhotoSizes(
                supportedSizes = jpegSizes,
                sensorLongShortRatio = sensorRatio
            ).firstOrNull()
        } else {
            null
        }

        val distinctFocals = focalLengths
            .map { (it * 100f).roundToInt() / 100f }
            .distinct()
            .sorted()

        return buildProfileFromSpecs(
            cameraId = cameraId,
            isFrontCamera = isFrontCamera,
            hardwareLevelInt = hwLevelInt,
            sensorAspectRatio = sensorRatio,
            optimalPhotoSize = optimalPhotoSize,
            bestVideoResolution = bestVideoRes,
            hasFlashUnit = hasFlash,
            supportsOis = hwQuality.supportsOis,
            supportsEis = hwQuality.supportsEis,
            supportsContinuousAf = hwQuality.supportsContinuousAfPicture || hwQuality.supportsContinuousAfVideo,
            supportsZsl = supportsZsl,
            supportedFocalLengthsMm = distinctFocals,
            physicalLensCount = max(physicalCount, distinctFocals.size.coerceAtLeast(1)),
            supportedFpsOptions = hwQuality.supportedFpsOptions,
            evMinStops = exposureCapabilities?.minEvStops ?: -2f,
            evMaxStops = exposureCapabilities?.maxEvStops ?: 2f,
            zoomCapabilities = zoomCapabilities
        )
    }

    /**
     * Pure JVM-testable profile builder used by both live hardware inspection and multi-device unit tests.
     */
    fun buildProfileFromSpecs(
        cameraId: String?,
        isFrontCamera: Boolean,
        hardwareLevelInt: Int?,
        sensorAspectRatio: Float,
        optimalPhotoSize: Size?,
        bestVideoResolution: String,
        hasFlashUnit: Boolean,
        supportsOis: Boolean,
        supportsEis: Boolean,
        supportsContinuousAf: Boolean,
        supportsZsl: Boolean,
        supportedFocalLengthsMm: List<Float>,
        physicalLensCount: Int,
        supportedFpsOptions: List<Int>,
        evMinStops: Float = -2f,
        evMaxStops: Float = 2f,
        zoomCapabilities: CameraLensZoomCapabilities = CameraLensZoomCapabilities()
    ): DeviceCameraProfile {
        val hwLevel = mapHardwareLevel(hardwareLevelInt)
        val mp = computeEffectiveMegapixels(optimalPhotoSize)
        return DeviceCameraProfile(
            cameraId = cameraId,
            isFrontCamera = isFrontCamera,
            hardwareLevel = hwLevel,
            sensorAspectRatio = sensorAspectRatio.coerceIn(1.0f, 2.4f),
            optimalPhotoSize = optimalPhotoSize,
            effectiveMegapixels = mp,
            photoResolutionBadge = formatMegapixelBadge(mp),
            bestVideoResolution = bestVideoResolution,
            videoResolutionBadge = formatVideoResolutionBadge(bestVideoResolution),
            hasFlashUnit = hasFlashUnit,
            supportsOis = supportsOis,
            supportsEis = supportsEis,
            supportsContinuousAf = supportsContinuousAf,
            supportsZsl = supportsZsl,
            supportedFocalLengthsMm = supportedFocalLengthsMm.filter { it.isFinite() && it > 0.1f }.distinct().sorted(),
            physicalLensCount = physicalLensCount.coerceAtLeast(1),
            supportedFpsOptions = supportedFpsOptions.ifEmpty { listOf(30) },
            evMinStops = min(evMinStops, 0f),
            evMaxStops = max(evMaxStops, 0f),
            zoomCapabilities = zoomCapabilities
        )
    }

    fun mapHardwareLevel(levelInt: Int?): CameraHardwareLevel {
        return when (levelInt) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> CameraHardwareLevel.LEVEL_3
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> CameraHardwareLevel.FULL
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> CameraHardwareLevel.LIMITED
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> CameraHardwareLevel.EXTERNAL
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> CameraHardwareLevel.LEGACY
            else -> CameraHardwareLevel.UNKNOWN
        }
    }

    fun computeEffectiveMegapixels(size: Size?): Int {
        if (size == null || size.width <= 0 || size.height <= 0) return 0
        val pixels = size.width.toLong() * size.height.toLong()
        return (pixels.toDouble() / 1_000_000.0).roundToInt().coerceAtLeast(1)
    }

    fun formatMegapixelBadge(megapixels: Int): String {
        return if (megapixels > 0) "${megapixels}M" else ""
    }

    fun formatVideoResolutionBadge(resolutionLabel: String): String {
        val norm = resolutionLabel.trim().uppercase()
        return when {
            norm.contains("3840") || norm.contains("2160") || norm.contains("4K") || norm.contains("UHD") -> "4K"
            norm.contains("1920") || norm.contains("1080") || norm.contains("FHD") -> "FHD"
            norm.contains("1280") || norm.contains("720") || norm.contains("HD") -> "HD"
            else -> "FHD"
        }
    }
}
