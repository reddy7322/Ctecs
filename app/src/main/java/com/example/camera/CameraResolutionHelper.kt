package com.example.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.util.Log
import android.util.Size
import java.util.Locale

data class CameraResolution(
    val width: Int,
    val height: Int,
    val label: String,           // e.g. "4000 × 3000"
    val megapixelsText: String,  // e.g. "12.0 MP"
    val isMax: Boolean = false,
    val category: String = "Photo", // "Photo" or "Video"
    val tag: String = ""         // e.g. "4K UHD", "1080p FHD"
) {
    val key: String get() = "$width x $height"

    val displayTitle: String
        get() = if (megapixelsText.isNotBlank()) {
            "$label ($megapixelsText${if (isMax) " • Max" else ""})"
        } else if (tag.isNotBlank()) {
            "$label ($tag)"
        } else {
            label
        }
}

object CameraResolutionHelper {

    private const val TAG = "CameraResolutionHelper"

    /**
     * Retrieves all genuinely supported still-photo output resolutions from the active camera.
     * Deduplicates orientation variants, calculates megapixels, sorts descending by pixel count,
     * and tags the max available resolution.
     */
    fun getSupportedPhotoResolutions(context: Context, isFrontCamera: Boolean): List<CameraResolution> {
        val rawSizes = queryCamera2Sizes(context, isFrontCamera, isVideo = false)
        if (rawSizes.isEmpty()) {
            return getDefaultPhotoFallbackList()
        }

        // Normalize dimensions (maxWidth x minHeight) to deduplicate orientation flips
        val uniqueSizes = rawSizes.map { size ->
            val w = Math.max(size.width, size.height)
            val h = Math.min(size.width, size.height)
            Size(w, h)
        }.distinctBy { "${it.width}x${it.height}" }
            .sortedByDescending { it.width.toLong() * it.height }

        val maxPixels = uniqueSizes.firstOrNull()?.let { it.width.toLong() * it.height } ?: 0L

        return uniqueSizes.map { size ->
            val w = size.width
            val h = size.height
            val pixels = w.toLong() * h
            val mp = pixels / 1_000_000.0
            val mpText = String.format(Locale.US, "%.1f MP", mp)
            val isMax = (pixels == maxPixels && maxPixels > 0)

            CameraResolution(
                width = w,
                height = h,
                label = "$w × $h",
                megapixelsText = mpText,
                isMax = isMax,
                category = "Photo"
            )
        }
    }

    /**
     * Retrieves all genuinely supported video recording resolutions from the active camera & encoder capabilities.
     * Deduplicates orientation variants, sorts descending by pixel count, and assigns standard tags (4K, FHD, HD, SD).
     */
    fun getSupportedVideoResolutions(context: Context, isFrontCamera: Boolean): List<CameraResolution> {
        val rawSizes = queryCamera2Sizes(context, isFrontCamera, isVideo = true)
        if (rawSizes.isEmpty()) {
            return getDefaultVideoFallbackList()
        }

        val uniqueSizes = rawSizes.map { size ->
            val w = Math.max(size.width, size.height)
            val h = Math.min(size.width, size.height)
            Size(w, h)
        }.distinctBy { "${it.width}x${it.height}" }
            .sortedByDescending { it.width.toLong() * it.height }

        val maxPixels = uniqueSizes.firstOrNull()?.let { it.width.toLong() * it.height } ?: 0L

        return uniqueSizes.map { size ->
            val w = size.width
            val h = size.height
            val pixels = w.toLong() * h
            val tag = when {
                w >= 3840 && h >= 2160 -> "4K UHD • 30fps"
                w >= 2560 && h >= 1440 -> "2K QHD • 30fps"
                w >= 1920 && h >= 1080 -> "1080p FHD • 30/60fps"
                w >= 1280 && h >= 720 -> "720p HD • 30fps"
                else -> "SD • 30fps"
            }
            val isMax = (pixels == maxPixels && maxPixels > 0)

            CameraResolution(
                width = w,
                height = h,
                label = "$w × $h",
                megapixelsText = "",
                isMax = isMax,
                category = "Video",
                tag = tag
            )
        }
    }

    private fun queryCamera2Sizes(context: Context, isFrontCamera: Boolean, isVideo: Boolean): List<Size> {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
            val targetFacing = if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK

            var matchedCameraId: String? = null
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == targetFacing) {
                    matchedCameraId = id
                    break
                }
            }

            if (matchedCameraId == null) {
                matchedCameraId = cameraManager.cameraIdList.firstOrNull()
            }

            if (matchedCameraId != null) {
                val characteristics = cameraManager.getCameraCharacteristics(matchedCameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                if (map != null) {
                    val sizes = if (isVideo) {
                        map.getOutputSizes(MediaRecorder::class.java)
                            ?: map.getOutputSizes(android.graphics.SurfaceTexture::class.java)
                    } else {
                        map.getOutputSizes(ImageFormat.JPEG)
                            ?: map.getOutputSizes(android.graphics.SurfaceTexture::class.java)
                    }
                    if (sizes != null && sizes.isNotEmpty()) {
                        return sizes.toList()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query Camera2 output sizes", e)
        }
        return emptyList()
    }

    private fun getDefaultPhotoFallbackList(): List<CameraResolution> {
        return listOf(
            CameraResolution(4000, 3000, "4000 × 3000", "12.0 MP", isMax = true),
            CameraResolution(3264, 2448, "3264 × 2448", "8.0 MP"),
            CameraResolution(2560, 1920, "2560 × 1920", "4.9 MP"),
            CameraResolution(2400, 1080, "2400 × 1080", "2.6 MP"),
            CameraResolution(1920, 1440, "1920 × 1440", "2.8 MP"),
            CameraResolution(1920, 1080, "1920 × 1080", "2.1 MP"),
            CameraResolution(1920, 864, "1920 × 864", "1.7 MP"),
            CameraResolution(1440, 1080, "1440 × 1080", "1.6 MP"),
            CameraResolution(1088, 1088, "1088 × 1088", "1.2 MP"),
            CameraResolution(1280, 720, "1280 × 720", "0.9 MP"),
            CameraResolution(960, 720, "960 × 720", "0.7 MP"),
            CameraResolution(720, 480, "720 × 480", "0.3 MP"),
            CameraResolution(640, 480, "640 × 480", "0.3 MP"),
            CameraResolution(640, 360, "640 × 360", "0.2 MP"),
            CameraResolution(352, 288, "352 × 288", "0.1 MP")
        )
    }

    private fun getDefaultVideoFallbackList(): List<CameraResolution> {
        return listOf(
            CameraResolution(3840, 2160, "3840 × 2160", "", isMax = true, category = "Video", tag = "4K UHD • 30fps"),
            CameraResolution(1920, 1080, "1920 × 1080", "", isMax = false, category = "Video", tag = "1080p FHD • 30/60fps"),
            CameraResolution(1280, 720, "1280 × 720", "", isMax = false, category = "Video", tag = "720p HD • 30fps"),
            CameraResolution(720, 480, "720 × 480", "", isMax = false, category = "Video", tag = "480p SD • 30fps"),
            CameraResolution(640, 480, "640 × 480", "", isMax = false, category = "Video", tag = "VGA • 30fps")
        )
    }
}
