package com.example.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import android.view.Surface
import com.example.model.CaptureSnapshot
import com.example.model.OverlayTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

object VideoOverlayProcessor {

    private const val TAG = "VideoOverlayProcessor"

    /**
     * Processes an MP4 video file and embeds dynamic timestamp, GPS, chainage, and logo overlays
     * continuously onto the output video frames.
     */
    suspend fun applyOverlayToVideo(
        context: Context,
        inputFile: File,
        outputFile: File,
        snapshotsTimeline: List<CaptureSnapshot>,
        defaultSnapshot: CaptureSnapshot,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val overlayConfig = defaultSnapshot.overlayConfig
        // If template is NONE or burnInTimestamp is false, no burn-in needed
        if (overlayConfig.template == OverlayTemplate.NONE || !overlayConfig.burnInTimestamp) {
            try {
                inputFile.copyTo(outputFile, overwrite = true)
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy raw video file", e)
                return@withContext false
            }
        }

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputFile.absolutePath)

            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationMsStr?.toLongOrNull() ?: 1000L

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            var videoWidth = widthStr?.toIntOrNull() ?: 1280
            var videoHeight = heightStr?.toIntOrNull() ?: 720
            val rotation = rotationStr?.toIntOrNull() ?: 0

            // Check sample frame dimensions to determine exact upright orientation
            val sampleFrame = try {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (ignored: Exception) {
                null
            }

            var targetWidth = sampleFrame?.width ?: videoWidth
            var targetHeight = sampleFrame?.height ?: videoHeight
            sampleFrame?.recycle()

            // Ensure dimensions are even numbers (required by H.264 AVC codecs)
            if (targetWidth % 2 != 0) targetWidth -= 1
            if (targetHeight % 2 != 0) targetHeight -= 1

            retriever.release()

            // Transcode & overlay frames
            val success = processVideoFrames(
                context = context,
                inputFile = inputFile,
                outputFile = outputFile,
                durationMs = durationMs,
                snapshotsTimeline = snapshotsTimeline,
                defaultSnapshot = defaultSnapshot,
                videoWidth = targetWidth,
                videoHeight = targetHeight,
                onProgress = onProgress
            )

            if (!success) {
                Log.w(TAG, "Frame transcode encountered issue, maintaining original video recording")
                inputFile.copyTo(outputFile, overwrite = true)
                return@withContext true
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Video overlay processing error: ${e.message}", e)
            try {
                inputFile.copyTo(outputFile, overwrite = true)
            } catch (ignored: Exception) {}
            true
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
        onProgress: ((Float) -> Unit)?
    ): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var encoder: MediaCodec? = null
        var inputSurface: Surface? = null

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

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var muxerAudioTrack = -1
            if (audioTrackIndex >= 0 && audioFormat != null) {
                muxerAudioTrack = muxer.addTrack(audioFormat)
            }

            // Target 25-30 fps for smooth, high quality rendering
            val targetFps = 30
            val frameIntervalMs = 1000L / targetFps
            val totalFrames = max(1, (durationMs / frameIntervalMs).toInt())

            val outputMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val bitRate = (videoWidth * videoHeight * 3.5f).toInt().coerceIn(2_000_000, 14_000_000)

            // Try Surface input first, with fallback to YUV buffer input
            var useSurfaceInput = true
            val encFormatSurface = MediaFormat.createVideoFormat(outputMime, videoWidth, videoHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(outputMime)
            try {
                encoder.configure(encFormatSurface, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = encoder.createInputSurface()
                encoder.start()
            } catch (e: Exception) {
                Log.w(TAG, "Surface input encoder configuration failed, using YUV buffer encoder: ${e.message}")
                useSurfaceInput = false
                encoder.release()

                val encFormatYuv = MediaFormat.createVideoFormat(outputMime, videoWidth, videoHeight).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                encoder = MediaCodec.createEncoderByType(outputMime)
                encoder.configure(encFormatYuv, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()
            }

            // Track state for MediaMuxer
            // trackIndexHolder[0] = muxerVideoTrack, trackIndexHolder[1] = muxerStarted (0=false, 1=true)
            val trackState = IntArray(2) { -1 }
            trackState[1] = 0

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputFile.absolutePath)

            var currentMs = 0L
            var frameCount = 0

            // Reusable buffers for YUV fallback
            var reusableBitmap: Bitmap? = null
            var reusableCanvas: Canvas? = null
            var argbBuffer: IntArray? = null
            var yuvBuffer: ByteArray? = null

            if (!useSurfaceInput) {
                reusableBitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
                reusableCanvas = Canvas(reusableBitmap)
                argbBuffer = IntArray(videoWidth * videoHeight)
                yuvBuffer = ByteArray(videoWidth * videoHeight * 3 / 2)
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val dstRect = Rect(0, 0, videoWidth, videoHeight)

            while (currentMs < durationMs) {
                val frameUs = currentMs * 1000L
                val frameBitmap = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST, videoWidth, videoHeight)
                            ?: retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    } else {
                        retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    }
                } catch (e: Exception) {
                    null
                }

                if (frameBitmap != null) {
                    val snapshotForFrame = getSnapshotAtTime(snapshotsTimeline, defaultSnapshot, currentMs)

                    if (useSurfaceInput && inputSurface != null) {
                        try {
                            val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                inputSurface.lockHardwareCanvas()
                            } else {
                                inputSurface.lockCanvas(null)
                            }

                            val srcRect = Rect(0, 0, frameBitmap.width, frameBitmap.height)
                            canvas.drawBitmap(frameBitmap, srcRect, dstRect, paint)

                            // Render dynamic overlay on top of frame
                            OverlayRenderer.drawOverlayOnCanvas(
                                context = context,
                                canvas = canvas,
                                width = videoWidth,
                                height = videoHeight,
                                snapshot = snapshotForFrame
                            )

                            inputSurface.unlockCanvasAndPost(canvas)
                            drainEncoder(encoder, muxer, trackState, false)
                        } catch (e: Exception) {
                            Log.w(TAG, "Direct surface locking exception: ${e.message}")
                        }
                    } else if (reusableBitmap != null && reusableCanvas != null && argbBuffer != null && yuvBuffer != null) {
                        // Software YUV buffer fallback
                        val srcRect = Rect(0, 0, frameBitmap.width, frameBitmap.height)
                        reusableCanvas.drawBitmap(frameBitmap, srcRect, dstRect, paint)

                        OverlayRenderer.drawOverlayOnCanvas(
                            context = context,
                            canvas = reusableCanvas,
                            width = videoWidth,
                            height = videoHeight,
                            snapshot = snapshotForFrame
                        )

                        reusableBitmap.getPixels(argbBuffer, 0, videoWidth, 0, 0, videoWidth, videoHeight)
                        encodeYUV420SP(yuvBuffer, argbBuffer, videoWidth, videoHeight)

                        val inputIndex = encoder.dequeueInputBuffer(10_000L)
                        if (inputIndex >= 0) {
                            val inBuffer = encoder.getInputBuffer(inputIndex)
                            if (inBuffer != null) {
                                inBuffer.clear()
                                inBuffer.put(yuvBuffer)
                                encoder.queueInputBuffer(inputIndex, 0, yuvBuffer.size, frameUs, 0)
                            }
                        }
                        drainEncoder(encoder, muxer, trackState, false)
                    }

                    if (!frameBitmap.isRecycled) {
                        frameBitmap.recycle()
                    }
                }

                frameCount++
                if (onProgress != null && totalFrames > 0) {
                    onProgress((frameCount.toFloat() / totalFrames).coerceIn(0f, 1f))
                }

                currentMs += frameIntervalMs
            }

            retriever.release()
            reusableBitmap?.recycle()

            // Signal End of Stream and drain remaining frames
            drainEncoder(encoder, muxer, trackState, true)

            // Copy audio track samples if present
            if (audioTrackIndex >= 0 && muxerAudioTrack >= 0 && trackState[1] == 1) {
                copyAudioTrack(extractor, audioTrackIndex, muxer, muxerAudioTrack)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}", e)
            return false
        } finally {
            try { encoder?.stop() } catch (ignored: Exception) {}
            try { encoder?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            try { muxer?.stop() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * Finds the exact snapshot for this millisecond of active video recording.
     * Accurately supports Pause and Resume by mapping each recorded active second
     * to its live snapshot without displaying stale or frozen timestamps.
     */
    private fun getSnapshotAtTime(
        timeline: List<CaptureSnapshot>,
        defaultSnapshot: CaptureSnapshot,
        msFromStart: Long
    ): CaptureSnapshot {
        if (timeline.isEmpty()) {
            val targetTime = defaultSnapshot.timestamp + msFromStart
            return defaultSnapshot.copy(timestamp = targetTime)
        }

        // Each element in timeline represents 1 recorded second of active video
        val secondIndex = (msFromStart / 1000L).toInt().coerceIn(0, timeline.size - 1)
        val activeSnapshot = timeline[secondIndex]

        // Dynamic clock precision: update timestamp to reflect millisecond progress within the second
        val msOffset = msFromStart % 1000L
        val frameClockTime = activeSnapshot.timestamp + msOffset

        return activeSnapshot.copy(timestamp = frameClockTime)
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
                encoder.signalEndOfInputStream()
            } catch (ignored: Exception) {}
        }

        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
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
}
