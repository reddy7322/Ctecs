package com.example.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import com.example.model.CaptureSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

data class VideoValidationResult(
    val isValid: Boolean,
    val hasVideoTrack: Boolean = false,
    val hasAudioTrack: Boolean = false,
    val audioTrackCount: Int = 0,
    val durationMs: Long = 0L,
    val rawWidth: Int = 0,
    val rawHeight: Int = 0,
    val rotationDegrees: Int = 0,
    val frameRate: Float = 30f,
    val errorReason: String? = null
) {
    val displayWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) rawHeight else rawWidth

    val displayHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) rawWidth else rawHeight
}

object VideoOverlayProcessor {

    private const val TAG = "VideoOverlayProcessor"

    /**
     * Validates that [inputFile] exists, is non-empty, and contains a real playable video track
     * with positive duration and dimensions.
     */
    fun validateRecordedVideoFile(inputFile: File): VideoValidationResult {
        if (!inputFile.exists() || inputFile.length() <= 0L) {
            return VideoValidationResult(
                isValid = false,
                errorReason = "Recorded video file is missing or empty"
            )
        }

        var retriever: MediaMetadataRetriever? = null
        var rawWidth = 0
        var rawHeight = 0
        var durationMs = 0L
        var rotationDegrees = 0
        var hasVideo = false
        var hasAudio = false
        var audioTrackCount = 0
        var frameRate = 30f

        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputFile.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            rotationDegrees = ((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0) % 360 + 360) % 360
            hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null || (rawWidth > 0 && rawHeight > 0)
            hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
            if (hasAudio) audioTrackCount = 1
            val fpsMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
            if (fpsMeta != null && fpsMeta in 5f..120f) {
                frameRate = fpsMeta
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed for ${inputFile.name}: ${e.message}")
        } finally {
            try { retriever?.release() } catch (ignored: Exception) {}
        }

        // Secondary MediaExtractor inspection for accurate track counts and fallback metadata
        if (!hasVideo || rawWidth <= 0 || rawHeight <= 0 || durationMs <= 0L || !hasAudio) {
            var extractor: MediaExtractor? = null
            try {
                extractor = MediaExtractor()
                extractor.setDataSource(inputFile.absolutePath)
                var detectedAudioTracks = 0
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        hasVideo = true
                        if (rawWidth <= 0 && format.containsKey(MediaFormat.KEY_WIDTH)) {
                            rawWidth = format.getInteger(MediaFormat.KEY_WIDTH)
                        }
                        if (rawHeight <= 0 && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                            rawHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
                        }
                        if (durationMs <= 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                            durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                        }
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            val trackFps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                            if (trackFps in 5f..120f) frameRate = trackFps
                        }
                    } else if (mime.startsWith("audio/")) {
                        detectedAudioTracks++
                    }
                }
                if (detectedAudioTracks > 0) {
                    hasAudio = true
                    audioTrackCount = detectedAudioTracks
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaExtractor validation failed for ${inputFile.name}: ${e.message}")
            } finally {
                try { extractor?.release() } catch (ignored: Exception) {}
            }
        }

        val valid = hasVideo && durationMs > 0L && rawWidth > 0 && rawHeight > 0
        return VideoValidationResult(
            isValid = valid,
            hasVideoTrack = hasVideo,
            hasAudioTrack = hasAudio,
            audioTrackCount = audioTrackCount,
            durationMs = durationMs,
            rawWidth = rawWidth,
            rawHeight = rawHeight,
            rotationDegrees = rotationDegrees,
            frameRate = frameRate,
            errorReason = if (valid) null else "Invalid or unplayable video track (hasVideo=$hasVideo, durationMs=$durationMs, size=${rawWidth}x${rawHeight})"
        )
    }

    /**
     * Executes a pre-selected [com.example.video.VideoExportPlan] using the fastest lossless or
     * hardware-accelerated pipeline available, with clean automatic fallback and safe cancellation.
     */
    fun executePlannedExport(
        context: Context,
        request: com.example.video.VideoExportRequest,
        validation: VideoValidationResult,
        plan: com.example.video.VideoExportPlan,
        onMethodChanged: ((com.example.video.VideoExportMethod) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        val reportProgress: (Float) -> Unit = { p -> onProgress?.invoke(p.coerceIn(0f, 1f)) }
        val inputFile = request.sourceFile
        val outputFile = request.destinationFile

        if (isCancelled()) return false
        outputFile.parentFile?.mkdirs()

        return when (plan.method) {
            com.example.video.VideoExportMethod.DIRECT_STREAM_COPY -> {
                Log.i(TAG, "Executing DIRECT_STREAM_COPY (${validation.durationMs}ms, ${validation.displayWidth}x${validation.displayHeight}): ${plan.reason}")
                com.example.video.HardwareVideoTranscoder.directStreamCopy(
                    inputFile = inputFile,
                    outputFile = outputFile,
                    allowAtomicMove = request.deleteSourceAfterSuccess,
                    isCancelled = isCancelled,
                    onProgress = reportProgress
                )
            }

            com.example.video.VideoExportMethod.FAST_REMUX -> {
                Log.i(TAG, "Executing FAST_REMUX (${validation.durationMs}ms, rot=${plan.targetRotationMetadataDegrees}deg): ${plan.reason}")
                val remuxed = com.example.video.HardwareVideoTranscoder.remuxAllTracks(
                    inputFile = inputFile,
                    outputFile = outputFile,
                    durationMs = validation.durationMs,
                    orientationHintDegrees = plan.targetRotationMetadataDegrees,
                    preserveAudio = request.preserveAudio,
                    isCancelled = isCancelled,
                    onProgress = reportProgress
                )
                if (remuxed || isCancelled()) {
                    remuxed
                } else {
                    Log.w(TAG, "FAST_REMUX unavailable for ${inputFile.name}; falling back to HARDWARE_GPU_TRANSCODE")
                    onMethodChanged?.invoke(com.example.video.VideoExportMethod.HARDWARE_GPU_TRANSCODE)
                    executeGpuOrFallbackTranscode(
                        context = context,
                        request = request,
                        validation = validation,
                        plan = plan,
                        onMethodChanged = onMethodChanged,
                        isCancelled = isCancelled,
                        onProgress = reportProgress
                    )
                }
            }

            com.example.video.VideoExportMethod.HARDWARE_GPU_TRANSCODE,
            com.example.video.VideoExportMethod.SOFTWARE_FALLBACK_TRANSCODE -> {
                executeGpuOrFallbackTranscode(
                    context = context,
                    request = request,
                    validation = validation,
                    plan = plan,
                    onMethodChanged = onMethodChanged,
                    isCancelled = isCancelled,
                    onProgress = reportProgress
                )
            }
        }
    }

    private fun executeGpuOrFallbackTranscode(
        context: Context,
        request: com.example.video.VideoExportRequest,
        validation: VideoValidationResult,
        plan: com.example.video.VideoExportPlan,
        onMethodChanged: ((com.example.video.VideoExportMethod) -> Unit)?,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): Boolean {
        val snapshot = request.defaultSnapshot ?: return false
        val rawTargetW = plan.outputDisplayWidth.coerceAtLeast(16)
        val rawTargetH = plan.outputDisplayHeight.coerceAtLeast(16)
        val targetFps = validation.frameRate.roundToInt().coerceIn(15, 60)
        val sourceRotationForGpu = request.rotationOverrideDegrees ?: validation.rotationDegrees

        // Preserve full 4K/UHD up to 3840 long edge on Hardware GPU when source is >1080p,
        // and gracefully fall back to 1920p if the device encoder rejects >1080p surface sizes.
        val hwMaxLongEdge = if (max(rawTargetW, rawTargetH) > 1920) 3840 else 1920
        val (hwW, hwH) = computeSafeEvenDimensions(rawTargetW, rawTargetH, hwMaxLongEdge)

        Log.i(
            TAG,
            "Executing HARDWARE_GPU_TRANSCODE (crop=${request.cropMode.name}, drawOverlay=${plan.needsOverlayBurnIn}, " +
                "${validation.durationMs}ms, ${hwW}x${hwH} @ ${targetFps}fps)"
        )
        val hwSuccess = com.example.video.HardwareVideoTranscoder.transcodeWithHardwareGpu(
            context = context,
            inputFile = request.sourceFile,
            outputFile = request.destinationFile,
            durationMs = validation.durationMs,
            sourceRotationDegrees = sourceRotationForGpu,
            cropMode = request.cropMode,
            outputWidth = hwW,
            outputHeight = hwH,
            targetFps = targetFps,
            snapshotsTimeline = request.snapshotsTimeline,
            defaultSnapshot = snapshot,
            shouldDrawOverlay = plan.needsOverlayBurnIn,
            preserveAudio = request.preserveAudio,
            preferSoftwareEncoder = false,
            isCancelled = isCancelled,
            onProgress = onProgress
        )
        if (hwSuccess || isCancelled()) return hwSuccess

        // Retry Hardware GPU at safe 1080p cap if 4K/UHD exceeded device encoder macroblock limit
        val (safeW, safeH) = computeSafeEvenDimensions(rawTargetW, rawTargetH, 1920)
        if (safeW != hwW || safeH != hwH) {
            Log.w(TAG, "Hardware GPU transcode failed at ${hwW}x${hwH}; retrying at ${safeW}x${safeH}")
            val hw1080Success = com.example.video.HardwareVideoTranscoder.transcodeWithHardwareGpu(
                context = context,
                inputFile = request.sourceFile,
                outputFile = request.destinationFile,
                durationMs = validation.durationMs,
                sourceRotationDegrees = sourceRotationForGpu,
                cropMode = request.cropMode,
                outputWidth = safeW,
                outputHeight = safeH,
                targetFps = targetFps,
                snapshotsTimeline = request.snapshotsTimeline,
                defaultSnapshot = snapshot,
                shouldDrawOverlay = plan.needsOverlayBurnIn,
                preserveAudio = request.preserveAudio,
                preferSoftwareEncoder = false,
                isCancelled = isCancelled,
                onProgress = onProgress
            )
            if (hw1080Success || isCancelled()) return hw1080Success
        }

        // Software AVC Surface encoder fallback (still GPU-decoded & streaming — far faster than CPU YUV)
        onMethodChanged?.invoke(com.example.video.VideoExportMethod.SOFTWARE_FALLBACK_TRANSCODE)
        Log.w(TAG, "Hardware encoder failed; trying streaming Software AVC Surface fallback (${safeW}x${safeH})")
        val swSurfaceSuccess = com.example.video.HardwareVideoTranscoder.transcodeWithHardwareGpu(
            context = context,
            inputFile = request.sourceFile,
            outputFile = request.destinationFile,
            durationMs = validation.durationMs,
            sourceRotationDegrees = sourceRotationForGpu,
            cropMode = request.cropMode,
            outputWidth = safeW,
            outputHeight = safeH,
            targetFps = targetFps,
            snapshotsTimeline = request.snapshotsTimeline,
            defaultSnapshot = snapshot,
            shouldDrawOverlay = plan.needsOverlayBurnIn,
            preserveAudio = request.preserveAudio,
            preferSoftwareEncoder = true,
            isCancelled = isCancelled,
            onProgress = onProgress
        )
        if (swSurfaceSuccess || isCancelled()) return swSurfaceSuccess

        // Last-resort CPU fallback if EGL surface creation itself is unsupported on this device
        Log.w(TAG, "Streaming Surface fallback unavailable; using legacy CPU frame fallback (${safeW}x${safeH})")
        return processVideoFrames(
            context = context,
            inputFile = request.sourceFile,
            outputFile = request.destinationFile,
            durationMs = validation.durationMs,
            snapshotsTimeline = request.snapshotsTimeline,
            defaultSnapshot = snapshot,
            videoWidth = safeW,
            videoHeight = safeH,
            targetFps = targetFps,
            shouldDrawOverlay = plan.needsOverlayBurnIn,
            isCancelled = isCancelled,
            onProgress = onProgress
        )
    }

    private fun computeSafeEvenDimensions(rawWidth: Int, rawHeight: Int, maxLongEdge: Int): Pair<Int, Int> {
        val longEdge = max(rawWidth, rawHeight).coerceAtLeast(16)
        val scale = if (longEdge > maxLongEdge) maxLongEdge.toDouble() / longEdge.toDouble() else 1.0
        val w = ((rawWidth * scale).roundToInt() and 1.inv()).coerceAtLeast(16)
        val h = ((rawHeight * scale).roundToInt() and 1.inv()).coerceAtLeast(16)
        return w to h
    }

    /**
     * Finalizes or post-processes a real camera-recorded MP4 video file with dynamic overlays.
     *
     * Routes automatically through [com.example.video.VideoExportStrategySelector] (Direct Copy ->
     * Fast Remux -> Hardware GPU Streaming Surface -> Safe Fallback).
     * Never substitutes demo or simulated road footage when [inputFile] is missing, empty, or invalid.
     */
    suspend fun applyOverlayToVideo(
        context: Context,
        inputFile: File,
        outputFile: File,
        snapshotsTimeline: List<CaptureSnapshot>,
        defaultSnapshot: CaptureSnapshot,
        hasRealtimeOverlay: Boolean = true,
        onProgress: ((Float) -> Unit)? = null,
        validator: (File) -> VideoValidationResult = ::validateRecordedVideoFile
    ): Boolean = withContext(Dispatchers.IO) {
        val validation = validator(inputFile)
        if (!validation.isValid) {
            Log.e(TAG, "Refusing video export: ${validation.errorReason}")
            try {
                if (outputFile.exists() && outputFile.absolutePath != inputFile.absolutePath) {
                    outputFile.delete()
                }
            } catch (ignored: Exception) {}
            return@withContext false
        }

        try {
            val plan = com.example.video.VideoExportStrategySelector.selectPlan(
                displayWidth = validation.displayWidth,
                displayHeight = validation.displayHeight,
                sourceRotationDegrees = validation.rotationDegrees,
                cropMode = defaultSnapshot.cropMode,
                hasRealtimeOverlay = hasRealtimeOverlay,
                requireOverlayBurnIn = !hasRealtimeOverlay,
                rotationOverrideDegrees = null,
                isContainerMp4 = true,
                preserveAudio = true,
                sourceHasAudio = validation.hasAudioTrack
            )
            val request = com.example.video.VideoExportRequest(
                sourceFile = inputFile,
                destinationFile = outputFile,
                fileName = outputFile.name,
                cropMode = defaultSnapshot.cropMode,
                hasRealtimeOverlay = hasRealtimeOverlay,
                requireOverlayBurnIn = !hasRealtimeOverlay,
                snapshotsTimeline = snapshotsTimeline,
                defaultSnapshot = defaultSnapshot,
                deleteSourceAfterSuccess = inputFile.absolutePath != outputFile.absolutePath,
                publishToMediaStore = false
            )
            val processed = executePlannedExport(
                context = context,
                request = request,
                validation = validation,
                plan = plan,
                onProgress = onProgress
            )
            if (!processed) {
                try {
                    if (outputFile.exists() && outputFile.absolutePath != inputFile.absolutePath) {
                        outputFile.delete()
                    }
                } catch (ignored: Exception) {}
            } else if (inputFile.absolutePath != outputFile.absolutePath && inputFile.exists()) {
                try { inputFile.delete() } catch (ignored: Exception) {}
            }
            return@withContext processed && outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize recorded video: ${e.message}", e)
            try {
                if (outputFile.exists() && outputFile.absolutePath != inputFile.absolutePath) {
                    outputFile.delete()
                }
            } catch (ignored: Exception) {}
            return@withContext false
        }
    }

    private fun processVideoFrames(
        context: Context,
        inputFile: File,
        outputFile: File,
        durationMs: Long,
        snapshotsTimeline: List<CaptureSnapshot>,
        defaultSnapshot: CaptureSnapshot,
        videoWidth: Int,
        videoHeight: Int,
        targetFps: Int = 30,
        shouldDrawOverlay: Boolean = true,
        isCancelled: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)?
    ): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var encoder: MediaCodec? = null

        // trackState[0] = muxerVideoTrack, trackState[1] = muxerStarted (0=false, 1=true)
        val trackState = IntArray(2) { -1 }
        trackState[1] = 0

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                    audioTrackIndex = i
                    audioFormat = format
                }
            }

            if (videoTrackIndex < 0 || videoFormat == null) {
                Log.e(TAG, "No video track found in input file")
                return false
            }

            outputFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var muxerAudioTrack = -1
            if (audioTrackIndex >= 0 && audioFormat != null) {
                try {
                    muxerAudioTrack = muxer.addTrack(audioFormat)
                } catch (e: Exception) {
                    Log.w(TAG, "Audio track could not be added: ${e.message}")
                    muxerAudioTrack = -1
                }
            }

            val safeFps = targetFps.coerceIn(15, 60)
            val frameIntervalMs = 1000L / safeFps
            val totalFrames = max(1, (durationMs / frameIntervalMs).toInt())

            val outputMime = MediaFormat.MIMETYPE_VIDEO_AVC

            val bitRate = com.example.camera.CameraQualityEngine.resolveEffectiveVideoBitrate(
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                fps = safeFps,
                qualityPreset = defaultSnapshot.videoQualityPreset,
                sizeOptimizationEnabled = defaultSnapshot.videoOutputSizeOptimization
            )

            encoder = MediaCodec.createEncoderByType(outputMime)
            val colorFormat = selectSupportedColorFormat(encoder.codecInfo, outputMime)

            val encFormat = MediaFormat.createVideoFormat(outputMime, videoWidth, videoHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, safeFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputFile.absolutePath)


            // Reusable buffers for frame drawing and YUV encoding
            val reusableBitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
            val reusableCanvas = Canvas(reusableBitmap)
            val argbBuffer = IntArray(videoWidth * videoHeight)
            val yuvBuffer = ByteArray(videoWidth * videoHeight * 3 / 2)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val dstRect = Rect(0, 0, videoWidth, videoHeight)

            var lastGoodBitmap: Bitmap? = null
            var decodedRealFrameCount = 0
            var currentMs = 0L
            var frameCount = 0

            while (currentMs < durationMs) {
                if (isCancelled()) {
                    lastGoodBitmap?.recycle()
                    retriever.release()
                    reusableBitmap.recycle()
                    return false
                }
                val frameUs = currentMs * 1000L
                val retrieved = try {
                    if (defaultSnapshot.cropMode == com.example.model.CameraCropMode.FULL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST, videoWidth, videoHeight)
                            ?: retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    } else {
                        retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    }
                } catch (ignored: Exception) {
                    null
                }

                if (retrieved != null) {
                    decodedRealFrameCount++
                }
                val frameBitmap = retrieved ?: lastGoodBitmap
                if (retrieved != null && retrieved != lastGoodBitmap) {
                    lastGoodBitmap?.recycle()
                    lastGoodBitmap = retrieved
                }

                // Require actual decoded camera frame; never emit blank/demo fallback video
                if (frameBitmap == null) {
                    currentMs += frameIntervalMs
                    continue
                }

                // 1. Draw real decoded camera frame cropped cleanly to exact CameraCropMode without distortion
                val frameIsLandscape = frameBitmap.width >= frameBitmap.height
                val frameCrop = com.example.camera.CameraOutputFormatEngine.computeCropRectForMode(
                    sourceWidth = frameBitmap.width,
                    sourceHeight = frameBitmap.height,
                    cropMode = defaultSnapshot.cropMode,
                    isLandscape = frameIsLandscape
                )
                val srcRect = Rect(frameCrop.left, frameCrop.top, frameCrop.right, frameCrop.bottom)
                reusableCanvas.drawBitmap(frameBitmap, srcRect, dstRect, paint)

                // 2. Render dynamic overlay strictly when not already burnt in real time
                if (shouldDrawOverlay) {
                    val snapshotForFrame = getSnapshotAtTime(snapshotsTimeline, defaultSnapshot, currentMs)
                    OverlayRenderer.drawOverlayOnCanvas(
                        context = context,
                        canvas = reusableCanvas,
                        width = videoWidth,
                        height = videoHeight,
                        snapshot = snapshotForFrame,
                        isVideoFrame = true
                    )
                }

                // 4. Convert ARGB to YUV buffer and feed encoder
                reusableBitmap.getPixels(argbBuffer, 0, videoWidth, 0, 0, videoWidth, videoHeight)
                if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                    encodeYUV420Planar(yuvBuffer, argbBuffer, videoWidth, videoHeight)
                } else {
                    encodeYUV420SP(yuvBuffer, argbBuffer, videoWidth, videoHeight)
                }

                feedEncoder(encoder, yuvBuffer, frameUs)
                drainEncoder(encoder, muxer, trackState, false)

                frameCount++
                if (onProgress != null && totalFrames > 0) {
                    onProgress((frameCount.toFloat() / totalFrames).coerceIn(0f, 1f))
                }

                currentMs += frameIntervalMs
            }

            lastGoodBitmap?.recycle()
            retriever.release()
            reusableBitmap.recycle()

            if (decodedRealFrameCount <= 0) {
                Log.e(TAG, "Zero real camera frames decoded from ${inputFile.name}; aborting export")
                return false
            }

            // Signal End of Stream and drain remaining frames
            drainEncoder(encoder, muxer, trackState, true)

            // Copy audio track samples if audio was captured and muxer is running
            if (audioTrackIndex >= 0 && muxerAudioTrack >= 0 && trackState[1] == 1) {
                copyAudioTrack(extractor, audioTrackIndex, muxer, muxerAudioTrack)
            }

            return trackState[1] == 1
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}", e)
            return false
        } finally {
            try { encoder?.stop() } catch (ignored: Exception) {}
            try { encoder?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            try { if (trackState[1] == 1) muxer?.stop() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * Finds and interpolates the exact snapshot for this millisecond of active video recording.
     * Smoothly updates timestamp, GPS coordinates, chainage, speed, compass heading, and bearing frame-by-frame.
     */
    fun getSnapshotAtTime(
        timeline: List<CaptureSnapshot>,
        defaultSnapshot: CaptureSnapshot,
        msFromStart: Long
    ): CaptureSnapshot {
        val baseSnapshot = if (timeline.isNotEmpty()) timeline[0] else defaultSnapshot
        val baseTimestamp = baseSnapshot.timestamp
        val frameTimestamp = baseTimestamp + msFromStart

        if (timeline.isEmpty()) {
            val loc = defaultSnapshot.location
            val chainage = defaultSnapshot.matchResult.chainage
            val speed = defaultSnapshot.speedMps ?: 0f

            val newLoc = if (loc != null && speed > 0f) {
                val distMetres = (msFromStart / 1000.0) * speed
                val bRad = Math.toRadians((defaultSnapshot.bearing ?: defaultSnapshot.compassAzimuth).toDouble())
                val dLat = (distMetres * Math.cos(bRad)) / com.example.geo.GeoUtils.EARTH_RADIUS_METRES
                val dLng = (distMetres * Math.sin(bRad)) / (com.example.geo.GeoUtils.EARTH_RADIUS_METRES * Math.cos(Math.toRadians(loc.latitude)))
                com.example.model.GeoPoint(loc.latitude + Math.toDegrees(dLat), loc.longitude + Math.toDegrees(dLng), loc.altitude)
            } else loc

            val newMetres = if (chainage != null && speed > 0f) {
                chainage.metres + ((msFromStart / 1000.0) * speed).toLong()
            } else chainage?.metres

            val newMatch = if (newMetres != null) {
                defaultSnapshot.matchResult.copy(chainage = com.example.model.Chainage(newMetres))
            } else defaultSnapshot.matchResult

            return defaultSnapshot.copy(
                timestamp = frameTimestamp,
                location = newLoc,
                matchResult = newMatch
            )
        }

        if (timeline.size == 1) {
            val snap = timeline[0]
            val loc = snap.location
            val chainage = snap.matchResult.chainage
            val speed = snap.speedMps ?: 0f

            val newLoc = if (loc != null && speed > 0f) {
                val distMetres = (msFromStart / 1000.0) * speed
                val bRad = Math.toRadians((snap.bearing ?: snap.compassAzimuth).toDouble())
                val dLat = (distMetres * Math.cos(bRad)) / com.example.geo.GeoUtils.EARTH_RADIUS_METRES
                val dLng = (distMetres * Math.sin(bRad)) / (com.example.geo.GeoUtils.EARTH_RADIUS_METRES * Math.cos(Math.toRadians(loc.latitude)))
                com.example.model.GeoPoint(loc.latitude + Math.toDegrees(dLat), loc.longitude + Math.toDegrees(dLng), loc.altitude)
            } else loc

            val newMetres = if (chainage != null && speed > 0f) {
                chainage.metres + ((msFromStart / 1000.0) * speed).toLong()
            } else chainage?.metres

            val newMatch = if (newMetres != null) {
                snap.matchResult.copy(chainage = com.example.model.Chainage(newMetres))
            } else snap.matchResult

            return snap.copy(
                timestamp = frameTimestamp,
                location = newLoc,
                matchResult = newMatch
            )
        }

        val startTime = timeline[0].timestamp
        val targetEpoch = startTime + msFromStart

        // Find snapA (last snapshot with timestamp <= targetEpoch) and snapB
        var snapA = timeline[0]
        var snapB = timeline.last()
        var idxA = 0

        for (i in 0 until timeline.size - 1) {
            if (timeline[i].timestamp <= targetEpoch) {
                idxA = i
                snapA = timeline[i]
                snapB = timeline[i + 1]
            }
        }

        val timeA = snapA.timestamp
        val timeB = snapB.timestamp
        val durationMs = timeB - timeA

        if (durationMs <= 0L || targetEpoch < timeA) {
            return snapA.copy(timestamp = frameTimestamp)
        }

        if (targetEpoch >= timeB && idxA == timeline.size - 2) {
            val speed = snapB.speedMps ?: 0f
            val extraMs = targetEpoch - timeB
            val loc = snapB.location
            val chainage = snapB.matchResult.chainage

            val newLoc = if (loc != null && speed > 0f) {
                val distMetres = (extraMs / 1000.0) * speed
                val bRad = Math.toRadians((snapB.bearing ?: snapB.compassAzimuth).toDouble())
                val dLat = (distMetres * Math.cos(bRad)) / com.example.geo.GeoUtils.EARTH_RADIUS_METRES
                val dLng = (distMetres * Math.sin(bRad)) / (com.example.geo.GeoUtils.EARTH_RADIUS_METRES * Math.cos(Math.toRadians(loc.latitude)))
                com.example.model.GeoPoint(loc.latitude + Math.toDegrees(dLat), loc.longitude + Math.toDegrees(dLng), loc.altitude)
            } else loc

            val newMetres = if (chainage != null && speed > 0f) {
                chainage.metres + ((extraMs / 1000.0) * speed).toLong()
            } else chainage?.metres

            val newMatch = if (newMetres != null) {
                snapB.matchResult.copy(chainage = com.example.model.Chainage(newMetres))
            } else snapB.matchResult

            return snapB.copy(
                timestamp = frameTimestamp,
                location = newLoc,
                matchResult = newMatch
            )
        }

        val t = ((targetEpoch - timeA).toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()

        // Interpolate Location (Lat, Lng, Alt)
        val locA = snapA.location
        val locB = snapB.location
        val interpLoc = if (locA != null && locB != null) {
            val lat = locA.latitude + t * (locB.latitude - locA.latitude)
            val lng = locA.longitude + t * (locB.longitude - locA.longitude)
            val alt = locA.altitude + t * (locB.altitude - locA.altitude)
            com.example.model.GeoPoint(lat, lng, alt)
        } else locA ?: locB

        // Interpolate Accuracy
        val interpAcc = snapA.gpsAccuracy + t * (snapB.gpsAccuracy - snapA.gpsAccuracy)

        // Interpolate Speed
        val speedA = snapA.speedMps ?: 0f
        val speedB = snapB.speedMps ?: 0f
        val interpSpeed = speedA + t * (speedB - speedA)

        // Interpolate Bearing
        val bearingA = snapA.bearing
        val bearingB = snapB.bearing
        val interpBearing = if (bearingA != null && bearingB != null) {
            lerpAngle(bearingA, bearingB, t)
        } else bearingA ?: bearingB

        // Interpolate Compass Azimuth & Heading
        val interpAzimuth = lerpAngle(snapA.compassAzimuth, snapB.compassAzimuth, t)
        val interpHeading = formatHeading(interpAzimuth)

        // Interpolate Chainage Metres
        val cA = snapA.matchResult.chainage?.metres
        val cB = snapB.matchResult.chainage?.metres
        val interpMatch = if (cA != null && cB != null) {
            val m = (cA + t * (cB - cA)).toLong()
            snapA.matchResult.copy(chainage = com.example.model.Chainage(m))
        } else if (cA != null && interpSpeed > 0f) {
            val elapsedSec = (targetEpoch - timeA) / 1000.0
            val m = cA + (elapsedSec * interpSpeed).toLong()
            snapA.matchResult.copy(chainage = com.example.model.Chainage(m))
        } else snapA.matchResult

        return snapA.copy(
            timestamp = frameTimestamp,
            location = interpLoc,
            gpsAccuracy = interpAcc,
            bearing = interpBearing,
            matchResult = interpMatch,
            compassHeading = interpHeading,
            compassAzimuth = interpAzimuth,
            speedMps = interpSpeed
        )
    }

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = (b - a) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        var result = (a + diff * t) % 360f
        if (result < 0f) result += 360f
        return result
    }

    private fun formatHeading(azimuthDegrees: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((azimuthDegrees + 22.5f) % 360f / 45f).toInt().coerceIn(0, 7)
        return "${azimuthDegrees.toInt()}° ${directions[index]}"
    }


    private fun feedEncoder(encoder: MediaCodec, yuvData: ByteArray, presentationTimeUs: Long) {
        val timeoutUs = 10_000L
        var index = encoder.dequeueInputBuffer(timeoutUs)
        var retries = 0
        while (index < 0 && retries < 15) {
            try { Thread.sleep(2) } catch (ignored: Exception) {}
            index = encoder.dequeueInputBuffer(timeoutUs)
            retries++
        }
        if (index >= 0) {
            val inputBuffer = encoder.getInputBuffer(index)
            if (inputBuffer != null) {
                inputBuffer.clear()
                inputBuffer.put(yuvData)
                encoder.queueInputBuffer(index, 0, yuvData.size, presentationTimeUs, 0)
            }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        trackState: IntArray,
        endOfStream: Boolean
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L

        if (endOfStream) {
            try {
                val inputIndex = encoder.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            } catch (ignored: Exception) {}
        }

        var eosDrainRetries = 0
        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream || ++eosDrainRetries > 25) break
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (trackState[1] == 0) {
                    val newFormat = encoder.outputFormat
                    trackState[0] = muxer.addTrack(newFormat)
                    muxer.start()
                    trackState[1] = 1
                }
            } else if (encoderStatus >= 0) {
                val encodedData = encoder.getOutputBuffer(encoderStatus)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && trackState[1] == 1) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackState[0], encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(encoderStatus, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    private fun copyAudioTrack(
        extractor: MediaExtractor,
        audioTrackIndex: Int,
        muxer: MediaMuxer,
        muxerAudioTrack: Int
    ) {
        try {
            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val bufferSize = 256 * 1024
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                extractor.advance()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio track copy notice: ${e.message}")
        }
    }

    private fun selectSupportedColorFormat(codecInfo: MediaCodecInfo, mimeType: String): Int {
        return try {
            val caps = codecInfo.getCapabilitiesForType(mimeType)
            val formats = caps.colorFormats
            when {
                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                else ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            }
        } catch (e: Exception) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        }
    }

    /**
     * Encodes ARGB bitmap pixels to NV12 / YUV420SemiPlanar for hardware video encoding.
     */
    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0

        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[index++]
                R = (c shr 16) and 0xff
                G = (c shr 8) and 0xff
                B = c and 0xff

                Y = ((66 * R + 129 * G + 25 * B + 128) shr 8) + 16
                U = ((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128
                V = ((112 * R - 94 * G - 18 * B + 128) shr 8) + 128

                yuv420sp[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    yuv420sp[uvIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                    yuv420sp[uvIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                }
            }
        }
    }

    /**
     * Encodes ARGB bitmap pixels to I420 / YUV420Planar.
     */
    private fun encodeYUV420Planar(yuv420p: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + frameSize / 4

        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0

        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[index++]
                R = (c shr 16) and 0xff
                G = (c shr 8) and 0xff
                B = c and 0xff

                Y = ((66 * R + 129 * G + 25 * B + 128) shr 8) + 16
                U = ((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128
                V = ((112 * R - 94 * G - 18 * B + 128) shr 8) + 128

                yuv420p[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    yuv420p[uIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                    yuv420p[vIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                }
            }
        }
    }
}
