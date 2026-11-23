package com.example.video

import com.example.camera.CameraOutputFormatEngine
import com.example.model.CameraCropMode
import com.example.model.CaptureSnapshot
import java.io.File
import java.util.Locale

/**
 * Automatically selected video export execution strategy.
 * The router always chooses the fastest lossless method that satisfies every required transformation.
 */
enum class VideoExportMethod(val displayLabel: String) {
    /** Zero re-encoding: atomic move (for temp captures) or chunked FileChannel stream copy. */
    DIRECT_STREAM_COPY("Direct Copy"),

    /** Zero re-encoding: MediaExtractor -> MediaMuxer track remux (updates orientation/container, preserves all tracks). */
    FAST_REMUX("Fast Remux"),

    /** Hardware-accelerated MediaCodec + OpenGL ES 2.0 Surface pipeline (crop, rotation, and/or cached overlay burn-in). */
    HARDWARE_GPU_TRANSCODE("GPU Export"),

    /** Safe sequential MediaCodec fallback when hardware Surface/EGL encoding is unavailable on the device. */
    SOFTWARE_FALLBACK_TRANSCODE("Safe Export")
}

enum class VideoExportStatus {
    IDLE,
    PREPARING,
    EXPORTING,
    COMPLETED,
    CANCELLED,
    FAILED
}

/**
 * Observable export progress, timing, method, and outcome snapshot exposed to Camera, Gallery,
 * and the background ForegroundService notification.
 */
data class VideoExportState(
    val jobId: String = "",
    val sourceKey: String = "",
    val fileName: String = "",
    val status: VideoExportStatus = VideoExportStatus.IDLE,
    val method: VideoExportMethod = VideoExportMethod.DIRECT_STREAM_COPY,
    val progress: Float = 0f,
    val elapsedMs: Long = 0L,
    val estimatedRemainingMs: Long? = null,
    val sourceSizeBytes: Long = 0L,
    val outputSizeBytes: Long = 0L,
    val sourceDurationMs: Long = 0L,
    val outputFilePath: String? = null,
    val errorMessage: String? = null
) {
    val isActive: Boolean
        get() = status == VideoExportStatus.PREPARING || status == VideoExportStatus.EXPORTING

    val progressPercent: Int
        get() = (progress.coerceIn(0f, 1f) * 100f).toInt()

    fun formatElapsedAndEta(): String {
        val elapsedSec = (elapsedMs / 1000L).coerceAtLeast(0L)
        val elapsedText = formatSeconds(elapsedSec)
        val etaMs = estimatedRemainingMs
        return if (etaMs != null && etaMs > 0L && status == VideoExportStatus.EXPORTING) {
            "$elapsedText • ~${formatSeconds((etaMs / 1000L).coerceAtLeast(1L))} left"
        } else {
            elapsedText
        }
    }

    private fun formatSeconds(totalSeconds: Long): String {
        val mins = totalSeconds / 60L
        val secs = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }
}

/**
 * Immutable video export job specification.
 */
data class VideoExportRequest(
    val jobId: String = java.util.UUID.randomUUID().toString(),
    val sourceFile: File,
    val destinationFile: File,
    val fileName: String,
    val cropMode: CameraCropMode = CameraCropMode.FULL,
    val rotationOverrideDegrees: Int? = null,
    val hasRealtimeOverlay: Boolean = true,
    val requireOverlayBurnIn: Boolean = false,
    val preserveAudio: Boolean = true,
    val snapshotsTimeline: List<CaptureSnapshot> = emptyList(),
    val defaultSnapshot: CaptureSnapshot? = null,
    val publishToMediaStore: Boolean = true,
    val deleteSourceAfterSuccess: Boolean = false,
    val isContainerMp4: Boolean = true,
    val insertDatabaseRecord: Boolean = false
)

/**
 * Deterministic result of export strategy selection, testable purely on JVM.
 */
data class VideoExportPlan(
    val method: VideoExportMethod,
    val needsOverlayBurnIn: Boolean,
    val needsPixelCrop: Boolean,
    val needsOrientationMetadataUpdate: Boolean,
    val targetRotationMetadataDegrees: Int,
    val outputDisplayWidth: Int,
    val outputDisplayHeight: Int,
    val reason: String
)

/**
 * Pure, deterministic router that selects the fastest valid export method without ever
 * sacrificing requested overlays, crop transformations, orientation accuracy, or multi-track audio.
 */
object VideoExportStrategySelector {

    /** Minimum safety headroom in bytes to leave free on internal/external storage. */
    const val MIN_STORAGE_HEADROOM_BYTES: Long = 24L * 1024L * 1024L

    fun selectPlan(
        displayWidth: Int,
        displayHeight: Int,
        sourceRotationDegrees: Int,
        cropMode: CameraCropMode,
        hasRealtimeOverlay: Boolean,
        requireOverlayBurnIn: Boolean,
        rotationOverrideDegrees: Int? = null,
        isContainerMp4: Boolean = true,
        preserveAudio: Boolean = true,
        sourceHasAudio: Boolean = false
    ): VideoExportPlan {
        val safeW = (displayWidth and 1.inv()).coerceAtLeast(16)
        val safeH = (displayHeight and 1.inv()).coerceAtLeast(16)
        val isLandscape = safeW >= safeH
        val cropRect = CameraOutputFormatEngine.computeCropRectForMode(
            sourceWidth = safeW,
            sourceHeight = safeH,
            cropMode = cropMode,
            isLandscape = isLandscape
        )

        val needsOverlay = requireOverlayBurnIn || !hasRealtimeOverlay
        val needsCrop = cropRect.isCropped
        val normalizedSrcRot = ((sourceRotationDegrees % 360) + 360) % 360
        val normalizedTargetRot = rotationOverrideDegrees?.let { ((it % 360) + 360) % 360 } ?: normalizedSrcRot
        val needsRotationMeta = normalizedTargetRot != normalizedSrcRot
        val needsStripAudio = !preserveAudio && sourceHasAudio

        return when {
            needsOverlay || needsCrop -> {
                VideoExportPlan(
                    method = VideoExportMethod.HARDWARE_GPU_TRANSCODE,
                    needsOverlayBurnIn = needsOverlay,
                    needsPixelCrop = needsCrop,
                    needsOrientationMetadataUpdate = false,
                    targetRotationMetadataDegrees = 0,
                    outputDisplayWidth = (cropRect.width and 1.inv()).coerceAtLeast(16),
                    outputDisplayHeight = (cropRect.height and 1.inv()).coerceAtLeast(16),
                    reason = buildString {
                        if (needsOverlay) append("overlay burn-in ")
                        if (needsCrop) append("crop=${cropMode.name}")
                    }.trim()
                )
            }
            !isContainerMp4 || needsRotationMeta || needsStripAudio -> {
                VideoExportPlan(
                    method = VideoExportMethod.FAST_REMUX,
                    needsOverlayBurnIn = false,
                    needsPixelCrop = false,
                    needsOrientationMetadataUpdate = needsRotationMeta,
                    targetRotationMetadataDegrees = normalizedTargetRot,
                    outputDisplayWidth = safeW,
                    outputDisplayHeight = safeH,
                    reason = buildString {
                        if (!isContainerMp4) append("mp4 container remux ")
                        if (needsRotationMeta) append("orientation=${normalizedTargetRot}° ")
                        if (needsStripAudio) append("audio strip")
                    }.trim()
                )
            }
            else -> {
                VideoExportPlan(
                    method = VideoExportMethod.DIRECT_STREAM_COPY,
                    needsOverlayBurnIn = false,
                    needsPixelCrop = false,
                    needsOrientationMetadataUpdate = false,
                    targetRotationMetadataDegrees = normalizedSrcRot,
                    outputDisplayWidth = safeW,
                    outputDisplayHeight = safeH,
                    reason = "unmodified MP4 with live overlays — zero re-encoding"
                )
            }
        }
    }

    /**
     * Estimates realistic required free disk bytes before starting export so low-storage
     * devices fail cleanly before writing corrupt partial files.
     */
    fun estimateRequiredSpaceBytes(
        sourceFileSizeBytes: Long,
        method: VideoExportMethod,
        publishToMediaStore: Boolean
    ): Long {
        val base = sourceFileSizeBytes.coerceAtLeast(1L * 1024L * 1024L)
        val workingMultiplier = when (method) {
            VideoExportMethod.DIRECT_STREAM_COPY -> if (publishToMediaStore) 2.1 else 1.15
            VideoExportMethod.FAST_REMUX -> if (publishToMediaStore) 2.2 else 1.25
            VideoExportMethod.HARDWARE_GPU_TRANSCODE,
            VideoExportMethod.SOFTWARE_FALLBACK_TRANSCODE -> if (publishToMediaStore) 2.5 else 1.5
        }
        return (base * workingMultiplier).toLong() + MIN_STORAGE_HEADROOM_BYTES
    }

    /**
     * Computes realistic ETA (milliseconds remaining) only when real progress is meaningful
     * (>= 4% progress and >= 600ms elapsed). Never synthesizes fake countdowns.
     */
    fun computeRealisticRemainingMs(progress: Float, elapsedMs: Long): Long? {
        if (progress >= 0.995f) return 0L
        if (progress < 0.04f || elapsedMs < 600L) return null
        val estimatedTotalMs = (elapsedMs.toDouble() / progress.toDouble())
        val remaining = (estimatedTotalMs - elapsedMs.toDouble()).toLong()
        return remaining.coerceIn(0L, 6L * 3600L * 1000L)
    }
}
