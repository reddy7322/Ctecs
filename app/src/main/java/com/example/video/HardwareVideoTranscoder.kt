package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.example.camera.CameraOutputFormatEngine
import com.example.camera.CameraQualityEngine
import com.example.model.CameraCropMode
import com.example.model.CaptureSnapshot
import com.example.overlay.OverlayRenderer
import com.example.overlay.VideoOverlayProcessor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * High-performance streaming video export engine supporting:
 * 1. [directStreamCopy]: Zero re-encoding chunked copy / atomic rename (instant for unmodified MP4s).
 * 2. [remuxAllTracks]: Zero re-encoding MediaExtractor -> MediaMuxer track copy preserving ALL video
 *    and audio tracks with optional MP4 orientation hint update.
 * 3. [transcodeWithHardwareGpu]: Streaming Hardware MediaCodec Decoder -> SurfaceTexture (OES) ->
 *    OpenGL ES 2.0 GPU shader (exact CropFrameRect UV crop + upright rotation + 1Hz dirty-cached
 *    overlay texture compositing) -> Hardware MediaCodec Encoder (createInputSurface) -> MediaMuxer
 *    with lossless direct multi-track audio passthrough and O(1) memory for short and long videos.
 */
object HardwareVideoTranscoder {

    private const val TAG = "HardwareVideoTranscoder"
    private const val STREAM_CHUNK_BYTES = 1024 * 1024 // 1 MB reusable streaming buffer

    /**
     * Zero re-encoding stream copy with real progress and cancellation checks.
     * Uses atomic rename when [allowAtomicMove] is true and files share the same filesystem.
     */
    fun directStreamCopy(
        inputFile: File,
        outputFile: File,
        allowAtomicMove: Boolean,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): Boolean {
        if (isCancelled()) return false
        if (!inputFile.exists() || inputFile.length() <= 0L) return false
        outputFile.parentFile?.mkdirs()

        if (inputFile.absolutePath == outputFile.absolutePath) {
            onProgress(1f)
            return true
        }

        if (allowAtomicMove && inputFile.renameTo(outputFile)) {
            onProgress(1f)
            return outputFile.exists() && outputFile.length() > 0L
        }

        val totalBytes = inputFile.length().coerceAtLeast(1L)
        val tempOut = File(outputFile.parentFile, ".tmp_copy_${System.nanoTime()}_${outputFile.name}")
        return try {
            FileInputStream(inputFile).use { fis ->
                FileOutputStream(tempOut).use { fos ->
                    val buffer = ByteArray(STREAM_CHUNK_BYTES)
                    var copied = 0L
                    while (true) {
                        if (isCancelled()) {
                            return false
                        }
                        val read = fis.read(buffer)
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                        copied += read
                        onProgress((copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f))
                    }
                    fos.fd.sync()
                }
            }
            if (isCancelled()) {
                tempOut.delete()
                return false
            }
            if (outputFile.exists()) outputFile.delete()
            val moved = tempOut.renameTo(outputFile) || run {
                tempOut.copyTo(outputFile, overwrite = true)
                tempOut.delete()
                true
            }
            if (moved && outputFile.exists() && outputFile.length() > 0L) {
                onProgress(1f)
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Direct stream copy failed: ${e.message}", e)
            try { tempOut.delete() } catch (_: Throwable) {}
            false
        }
    }

    /**
     * Zero re-encoding multi-track container remux via [MediaExtractor] -> [MediaMuxer].
     * Preserves all compatible video and audio tracks and applies [orientationHintDegrees] cleanly.
     */
    fun remuxAllTracks(
        inputFile: File,
        outputFile: File,
        durationMs: Long,
        orientationHintDegrees: Int,
        preserveAudio: Boolean,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): Boolean {
        if (isCancelled()) return false
        outputFile.parentFile?.mkdirs()
        val tempOut = File(outputFile.parentFile, ".tmp_remux_${System.nanoTime()}_${outputFile.name}")
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor = MediaExtractor().apply { setDataSource(inputFile.absolutePath) }
            muxer = MediaMuxer(tempOut.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(((orientationHintDegrees % 360) + 360) % 360)

            val trackMap = mutableMapOf<Int, Int>()
            var maxBufferSize = STREAM_CHUNK_BYTES

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                val isVideo = mime.startsWith("video/")
                val isAudio = mime.startsWith("audio/")
                if (!isVideo && !(isAudio && preserveAudio)) continue
                try {
                    val dstIdx = muxer.addTrack(format)
                    trackMap[i] = dstIdx
                    extractor.selectTrack(i)
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxBufferSize = max(maxBufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                } catch (trackErr: Throwable) {
                    Log.w(TAG, "Skipping unsupported remux track $i ($mime): ${trackErr.message}")
                }
            }

            if (trackMap.isEmpty()) return false

            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(maxBufferSize.coerceIn(256 * 1024, 4 * 1024 * 1024))
            val info = MediaCodec.BufferInfo()
            val durationUs = (durationMs * 1000L).coerceAtLeast(1L)
            var lastReportedPts = 0L

            while (true) {
                if (isCancelled()) return false
                val srcTrack = extractor.sampleTrackIndex
                if (srcTrack < 0) break
                val dstTrack = trackMap[srcTrack]
                if (dstTrack != null) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                    info.flags = mapExtractorFlagsToCodecFlags(extractor.sampleFlags)
                    muxer.writeSampleData(dstTrack, buffer, info)
                    if (info.presentationTimeUs - lastReportedPts >= 200_000L) {
                        lastReportedPts = info.presentationTimeUs
                        onProgress((info.presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 0.99f))
                    }
                }
                if (!extractor.advance()) break
            }

            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null

            if (isCancelled()) {
                tempOut.delete()
                return false
            }
            if (outputFile.exists()) outputFile.delete()
            val finalized = tempOut.renameTo(outputFile) || run {
                tempOut.copyTo(outputFile, overwrite = true)
                tempOut.delete()
                true
            }
            if (finalized && outputFile.exists() && outputFile.length() > 0L) {
                onProgress(1f)
                return true
            }
            return false
        } catch (e: Throwable) {
            Log.e(TAG, "Fast remux failed: ${e.message}", e)
            return false
        } finally {
            try { extractor?.release() } catch (_: Throwable) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            if (tempOut.exists() && (!outputFile.exists() || outputFile.length() == 0L)) {
                try { tempOut.delete() } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Hardware-accelerated streaming GPU transcoder:
     * `MediaExtractor -> MediaCodec Decoder -> SurfaceTexture (OES) -> GLES 2.0 Crop/Rotate/Overlay Shader ->
     * MediaCodec Encoder (Input Surface) -> MediaMuxer` + lossless direct multi-track audio stream copy.
     *
     * Runs in constant O(1) memory regardless of video length and uploads overlay textures only when dirty (~1Hz).
     */
    fun transcodeWithHardwareGpu(
        context: Context,
        inputFile: File,
        outputFile: File,
        durationMs: Long,
        sourceRotationDegrees: Int,
        cropMode: CameraCropMode,
        outputWidth: Int,
        outputHeight: Int,
        targetFps: Int,
        snapshotsTimeline: List<CaptureSnapshot>,
        defaultSnapshot: CaptureSnapshot,
        shouldDrawOverlay: Boolean,
        preserveAudio: Boolean,
        preferSoftwareEncoder: Boolean = false,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): Boolean {
        if (isCancelled()) return false
        outputFile.parentFile?.mkdirs()
        val tempOut = File(outputFile.parentFile, ".tmp_gpu_${System.nanoTime()}_${outputFile.name}")

        var videoExtractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var eglPipeline: EglTranscodeSurfacePipeline? = null
        var overlayBitmap: Bitmap? = null
        var overlayCanvas: Canvas? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(inputFile.absolutePath) }
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            val audioTrackIndices = mutableListOf<Int>()
            val audioFormats = mutableListOf<MediaFormat>()

            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i
                    videoFormat = fmt
                } else if (mime.startsWith("audio/") && preserveAudio) {
                    audioTrackIndices.add(i)
                    audioFormats.add(fmt)
                }
            }

            if (videoTrackIndex < 0 || videoFormat == null) return false
            videoExtractor.selectTrack(videoTrackIndex)

            val rawSrcW = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val rawSrcH = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val srcRot = ((sourceRotationDegrees % 360) + 360) % 360
            val dispW = if (srcRot == 90 || srcRot == 270) rawSrcH else rawSrcW
            val dispH = if (srcRot == 90 || srcRot == 270) rawSrcW else rawSrcH

            val cropRect = CameraOutputFormatEngine.computeCropRectForMode(
                sourceWidth = dispW,
                sourceHeight = dispH,
                cropMode = cropMode,
                isLandscape = dispW >= dispH
            )
            // Normalized UV crop region in upright display coordinates [0..1]
            val uvLeft = cropRect.left.toFloat() / dispW.coerceAtLeast(1).toFloat()
            val uvTop = cropRect.top.toFloat() / dispH.coerceAtLeast(1).toFloat()
            val uvRight = cropRect.right.toFloat() / dispW.coerceAtLeast(1).toFloat()
            val uvBottom = cropRect.bottom.toFloat() / dispH.coerceAtLeast(1).toFloat()

            val encW = (outputWidth and 1.inv()).coerceAtLeast(16)
            val encH = (outputHeight and 1.inv()).coerceAtLeast(16)
            val safeFps = targetFps.coerceIn(15, 60)
            val bitRate = CameraQualityEngine.resolveEffectiveVideoBitrate(
                videoWidth = encW,
                videoHeight = encH,
                fps = safeFps,
                qualityPreset = defaultSnapshot.videoQualityPreset,
                sizeOptimizationEnabled = defaultSnapshot.videoOutputSizeOptimization
            )

            val encFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encW, encH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, safeFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = createAvcEncoder(preferSoftware = preferSoftwareEncoder)
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderInputSurface = encoder.createInputSurface()
            encoder.start()

            // Set up EGL + GLES 2.0 SurfaceTexture pipeline on this worker thread
            eglPipeline = EglTranscodeSurfacePipeline(
                encoderSurface = encoderInputSurface,
                outputWidth = encW,
                outputHeight = encH,
                sourceRotationDegrees = srcRot,
                uvLeft = uvLeft,
                uvTop = uvTop,
                uvRight = uvRight,
                uvBottom = uvBottom,
                enableOverlay = shouldDrawOverlay
            )
            eglPipeline.makeCurrent()

            val decoderMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
            decoder = MediaCodec.createDecoderByType(decoderMime)
            // Clear rotation-degrees on decoder format so our GL shader performs the exact upright rotation cleanly
            val decConfigFormat = MediaFormat(videoFormat)
            try {
                if (decConfigFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                    decConfigFormat.setInteger(MediaFormat.KEY_ROTATION, 0)
                }
            } catch (_: Throwable) {}
            decoder.configure(decConfigFormat, eglPipeline.decoderSurface, null, 0)
            decoder.start()

            muxer = MediaMuxer(tempOut.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // Output video frames are already rotated upright in GL, so orientation hint is strictly 0°
            muxer.setOrientationHint(0)

            val muxerAudioTrackMap = mutableListOf<Pair<Int, Int>>()
            for (idx in audioTrackIndices.indices) {
                try {
                    val mIdx = muxer.addTrack(audioFormats[idx])
                    muxerAudioTrackMap.add(audioTrackIndices[idx] to mIdx)
                } catch (e: Throwable) {
                    Log.w(TAG, "Audio track ${audioTrackIndices[idx]} skipped: ${e.message}")
                }
            }

            if (shouldDrawOverlay) {
                overlayBitmap = Bitmap.createBitmap(encW, encH, Bitmap.Config.ARGB_8888)
                overlayCanvas = Canvas(overlayBitmap)
            }

            val durationUs = (durationMs * 1000L).coerceAtLeast(1L)
            val decoderBufferInfo = MediaCodec.BufferInfo()
            val encoderBufferInfo = MediaCodec.BufferInfo()
            var muxerVideoTrack = -1
            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            var decodedFrames = 0
            var lastOverlaySecond = Long.MIN_VALUE
            var lastProgressMs = 0L

            while (!encoderDone) {
                if (isCancelled()) return false

                // 1. Feed compressed video samples to hardware decoder
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = videoExtractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val ptsUs = videoExtractor.sampleTime.coerceAtLeast(0L)
                                val flags = mapExtractorFlagsToCodecFlags(videoExtractor.sampleFlags)
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, ptsUs, flags)
                                videoExtractor.advance()
                            }
                        }
                    }
                }

                // 2. Drain decoded frame -> SurfaceTexture -> GLES crop/rotate/overlay -> Encoder Surface
                var drainDecoder = !decoderDone
                while (drainDecoder) {
                    val decStatus = decoder.dequeueOutputBuffer(decoderBufferInfo, 10_000L)
                    when {
                        decStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            drainDecoder = false
                        }
                        decStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            // Decoder output format confirmed
                        }
                        decStatus >= 0 -> {
                            val isEos = (decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            val doRender = decoderBufferInfo.size > 0
                            val ptsUs = decoderBufferInfo.presentationTimeUs.coerceAtLeast(0L)
                            decoder.releaseOutputBuffer(decStatus, doRender)

                            if (doRender) {
                                if (eglPipeline.awaitNewImage(2500L)) {
                                    // Refresh cached 1Hz overlay texture only when timestamp second changes
                                    if (shouldDrawOverlay && overlayBitmap != null && overlayCanvas != null) {
                                        val frameMs = ptsUs / 1000L
                                        val frameSec = frameMs / 1000L
                                        if (frameSec != lastOverlaySecond) {
                                            lastOverlaySecond = frameSec
                                            overlayCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                            val snap = VideoOverlayProcessor.getSnapshotAtTime(
                                                timeline = snapshotsTimeline,
                                                defaultSnapshot = defaultSnapshot,
                                                msFromStart = frameMs
                                            )
                                            OverlayRenderer.drawOverlayOnCanvas(
                                                context = context,
                                                canvas = overlayCanvas,
                                                width = encW,
                                                height = encH,
                                                snapshot = snap,
                                                isVideoFrame = true
                                            )
                                            eglPipeline.uploadOverlayBitmap(overlayBitmap)
                                        }
                                    }
                                    eglPipeline.drawFrameAndSwap(ptsUs * 1000L)
                                    decodedFrames++
                                    val nowMs = System.currentTimeMillis()
                                    if (nowMs - lastProgressMs >= 120L) {
                                        lastProgressMs = nowMs
                                        val videoWeight = if (muxerAudioTrackMap.isNotEmpty()) 0.92f else 0.99f
                                        onProgress(((ptsUs.toFloat() / durationUs.toFloat()) * videoWeight).coerceIn(0f, videoWeight))
                                    }
                                }
                            }

                            if (isEos) {
                                decoderDone = true
                                drainDecoder = false
                                encoder.signalEndOfInputStream()
                            }
                        }
                    }

                    // 3. Interleave encoder output draining so encoder buffers never stall
                    while (true) {
                        val encStatus = encoder.dequeueOutputBuffer(encoderBufferInfo, 0L)
                        if (encStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            break
                        } else if (encStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (!muxerStarted) {
                                muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        } else if (encStatus >= 0) {
                            val outBuf = encoder.getOutputBuffer(encStatus)
                            if (outBuf != null) {
                                if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    encoderBufferInfo.size = 0
                                }
                                if (encoderBufferInfo.size > 0 && muxerStarted && muxerVideoTrack >= 0) {
                                    outBuf.position(encoderBufferInfo.offset)
                                    outBuf.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                                    muxer.writeSampleData(muxerVideoTrack, outBuf, encoderBufferInfo)
                                }
                            }
                            encoder.releaseOutputBuffer(encStatus, false)
                            if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoderDone = true
                                break
                            }
                        }
                    }
                }

                // Drain remaining encoder output after decoder EOS
                if (decoderDone && !encoderDone) {
                    val encStatus = encoder.dequeueOutputBuffer(encoderBufferInfo, 10_000L)
                    if (encStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    } else if (encStatus >= 0) {
                        val outBuf = encoder.getOutputBuffer(encStatus)
                        if (outBuf != null) {
                            if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                encoderBufferInfo.size = 0
                            }
                            if (encoderBufferInfo.size > 0 && muxerStarted && muxerVideoTrack >= 0) {
                                outBuf.position(encoderBufferInfo.offset)
                                outBuf.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                                muxer.writeSampleData(muxerVideoTrack, outBuf, encoderBufferInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(encStatus, false)
                        if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    }
                }
            }

            if (decodedFrames <= 0 || !muxerStarted) {
                Log.w(TAG, "Hardware GPU transcode decoded $decodedFrames frames (muxerStarted=$muxerStarted)")
                return false
            }

            // 4. Lossless direct copy of ALL audio tracks preserving original timestamps
            if (muxerAudioTrackMap.isNotEmpty() && !isCancelled()) {
                copyAudioTracksDirect(
                    inputFile = inputFile,
                    audioTrackPairs = muxerAudioTrackMap,
                    muxer = muxer,
                    isCancelled = isCancelled
                )
            }

            if (isCancelled()) return false

            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null

            if (outputFile.exists()) outputFile.delete()
            val finalized = tempOut.renameTo(outputFile) || run {
                tempOut.copyTo(outputFile, overwrite = true)
                tempOut.delete()
                true
            }
            if (finalized && outputFile.exists() && outputFile.length() > 0L) {
                onProgress(1f)
                return true
            }
            return false
        } catch (e: Throwable) {
            Log.w(TAG, "Hardware GPU transcode failed (preferSoftware=$preferSoftwareEncoder): ${e.message}", e)
            return false
        } finally {
            try { overlayBitmap?.recycle() } catch (_: Throwable) {}
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            try { eglPipeline?.release() } catch (_: Throwable) {}
            try { videoExtractor?.release() } catch (_: Throwable) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
            if (tempOut.exists() && (!outputFile.exists() || outputFile.length() == 0L)) {
                try { tempOut.delete() } catch (_: Throwable) {}
            }
        }
    }

    private fun createAvcEncoder(preferSoftware: Boolean): MediaCodec {
        if (preferSoftware) {
            try {
                val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                val swName = list.codecInfos.firstOrNull { info ->
                    info.isEncoder &&
                        info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } &&
                        (info.name.startsWith("c2.android.", ignoreCase = true) ||
                            info.name.startsWith("OMX.google.", ignoreCase = true))
                }?.name
                if (swName != null) {
                    return MediaCodec.createByCodecName(swName)
                }
            } catch (_: Throwable) {}
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    }

    private fun copyAudioTracksDirect(
        inputFile: File,
        audioTrackPairs: List<Pair<Int, Int>>,
        muxer: MediaMuxer,
        isCancelled: () -> Boolean
    ) {
        var audioExtractor: MediaExtractor? = null
        try {
            audioExtractor = MediaExtractor().apply { setDataSource(inputFile.absolutePath) }
            val trackMap = audioTrackPairs.toMap()
            audioTrackPairs.forEach { (srcIdx, _) -> audioExtractor.selectTrack(srcIdx) }
            audioExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val buffer = ByteBuffer.allocateDirect(512 * 1024)
            val info = MediaCodec.BufferInfo()
            while (!isCancelled()) {
                val srcTrack = audioExtractor.sampleTrackIndex
                if (srcTrack < 0) break
                val dstTrack = trackMap[srcTrack]
                if (dstTrack != null) {
                    buffer.clear()
                    val size = audioExtractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = audioExtractor.sampleTime.coerceAtLeast(0L)
                    info.flags = mapExtractorFlagsToCodecFlags(audioExtractor.sampleFlags)
                    muxer.writeSampleData(dstTrack, buffer, info)
                }
                if (!audioExtractor.advance()) break
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Direct audio track copy notice: ${e.message}")
        } finally {
            try { audioExtractor?.release() } catch (_: Throwable) {}
        }
    }

    private fun mapExtractorFlagsToCodecFlags(extractorFlags: Int): Int {
        var codecFlags = 0
        if ((extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if ((extractorFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return codecFlags
    }
}

/**
 * Self-contained EGL 1.4 + OpenGL ES 2.0 Surface pipeline for zero-copy GPU crop, upright rotation,
 * and alpha-blended overlay compositing into a [MediaCodec] encoder input [Surface].
 */
private class EglTranscodeSurfacePipeline(
    private val encoderSurface: Surface,
    private val outputWidth: Int,
    private val outputHeight: Int,
    sourceRotationDegrees: Int,
    uvLeft: Float,
    uvTop: Float,
    uvRight: Float,
    uvBottom: Float,
    private val enableOverlay: Boolean
) : SurfaceTexture.OnFrameAvailableListener {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var oesTextureId: Int = 0
    private var overlayTextureId: Int = 0
    private var hasOverlayTexture: Boolean = false

    private var videoProgram: Int = 0
    private var overlayProgram: Int = 0

    private val surfaceTexture: SurfaceTexture
    val decoderSurface: Surface

    private val frameSyncLock = Object()
    private var frameAvailable = false

    private val stMatrix = FloatArray(16)
    private val rotMatrix = FloatArray(16)
    private val videoVertexBuffer: FloatBuffer
    private val overlayVertexBuffer: FloatBuffer

    init {
        initEgl()
        makeCurrent()

        val texIds = IntArray(2)
        GLES20.glGenTextures(2, texIds, 0)
        oesTextureId = texIds[0]
        overlayTextureId = texIds[1]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        videoProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_OES)
        overlayProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D)

        // Compute upright display UV quad with exact CropFrameRect inset (origin top-left in display space;
        // GL texture Y=0 is bottom so displayTop maps to (1 - uvTop) and displayBottom maps to (1 - uvBottom))
        val glU0 = uvLeft.coerceIn(0f, 1f)
        val glU1 = uvRight.coerceIn(glU0, 1f)
        val glVBottom = (1f - uvBottom).coerceIn(0f, 1f)
        val glVTop = (1f - uvTop).coerceIn(glVBottom, 1f)

        // Apply source rotation around center (0.5, 0.5) so portrait/landscape sensors render upright
        Matrix.setIdentityM(rotMatrix, 0)
        val normRot = ((sourceRotationDegrees % 360) + 360) % 360
        if (normRot != 0) {
            Matrix.translateM(rotMatrix, 0, 0.5f, 0.5f, 0f)
            Matrix.rotateM(rotMatrix, 0, normRot.toFloat(), 0f, 0f, 1f)
            Matrix.translateM(rotMatrix, 0, -0.5f, -0.5f, 0f)
        }

        val videoQuad = floatArrayOf(
            // X, Y, U, V (triangle strip: BL, BR, TL, TR)
            -1f, -1f, glU0, glVBottom,
             1f, -1f, glU1, glVBottom,
            -1f,  1f, glU0, glVTop,
             1f,  1f, glU1, glVTop
        )
        videoVertexBuffer = allocateFloatBuffer(videoQuad)

        // Overlay bitmap has top-left origin -> flip V so text & minimap render upright
        val overlayQuad = floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        )
        overlayVertexBuffer = allocateFloatBuffer(overlayQuad)

        surfaceTexture = SurfaceTexture(oesTextureId)
        surfaceTexture.setOnFrameAvailableListener(this)
        decoderSurface = Surface(surfaceTexture)
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("Unable to get EGL14 display")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1, // EGL_RECORDABLE_ANDROID
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] <= 0) {
            throw RuntimeException("Unable to find RGB888+recordable EGLConfig")
        }
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) throw RuntimeException("Unable to create EGLContext")

        val surfAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], encoderSurface, surfAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) throw RuntimeException("Unable to create EGLSurface")
    }

    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        synchronized(frameSyncLock) {
            frameAvailable = true
            frameSyncLock.notifyAll()
        }
    }

    fun awaitNewImage(timeoutMs: Long): Boolean {
        synchronized(frameSyncLock) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!frameAvailable) {
                val wait = deadline - System.currentTimeMillis()
                if (wait <= 0L) return false
                try {
                    frameSyncLock.wait(wait)
                } catch (_: InterruptedException) {
                    return false
                }
            }
            frameAvailable = false
        }
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(stMatrix)
        return true
    }

    fun uploadOverlayBitmap(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        hasOverlayTexture = true
    }

    fun drawFrameAndSwap(presentationTimeNs: Long) {
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 1. Draw decoded video frame with SurfaceTexture transform * upright rotation * exact UV crop
        val combinedTexMatrix = FloatArray(16)
        Matrix.multiplyMM(combinedTexMatrix, 0, stMatrix, 0, rotMatrix, 0)

        GLES20.glDisable(GLES20.GL_BLEND)
        drawTexturedQuad(
            program = videoProgram,
            vertexBuffer = videoVertexBuffer,
            texMatrix = combinedTexMatrix,
            textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            textureId = oesTextureId
        )

        // 2. Alpha-composite cached 1Hz overlay texture cleanly on top
        if (enableOverlay && hasOverlayTexture) {
            val identity = FloatArray(16)
            Matrix.setIdentityM(identity, 0)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            drawTexturedQuad(
                program = overlayProgram,
                vertexBuffer = overlayVertexBuffer,
                texMatrix = identity,
                textureTarget = GLES20.GL_TEXTURE_2D,
                textureId = overlayTextureId
            )
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun drawTexturedQuad(
        program: Int,
        vertexBuffer: FloatBuffer,
        texMatrix: FloatArray,
        textureTarget: Int,
        textureId: Int
    ) {
        GLES20.glUseProgram(program)
        val aPos = GLES20.glGetAttribLocation(program, "aPosition")
        val aTex = GLES20.glGetAttribLocation(program, "aTexCoord")
        val uTexMat = GLES20.glGetUniformLocation(program, "uTexMatrix")

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)

        GLES20.glUniformMatrix4fv(uTexMat, 1, false, texMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, textureId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    fun release() {
        try { decoderSurface.release() } catch (_: Throwable) {}
        try { surfaceTexture.release() } catch (_: Throwable) {}
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        } catch (_: Throwable) {}
        try { encoderSurface.release() } catch (_: Throwable) {}
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun allocateFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("GL program link failed: $log")
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("GL shader compile failed: $log")
        }
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        private const val FRAGMENT_SHADER_2D = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}
