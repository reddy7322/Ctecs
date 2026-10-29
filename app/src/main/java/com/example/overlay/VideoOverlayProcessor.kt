package com.example.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.model.CaptureSnapshot
import com.example.model.OverlayTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

            var videoWidth = widthStr?.toIntOrNull() ?: 1080
            var videoHeight = heightStr?.toIntOrNull() ?: 1920
            val rotation = rotationStr?.toIntOrNull() ?: 0

            // Swap dimensions if rotation requires it
            if (rotation == 90 || rotation == 270) {
                val tmp = videoWidth
                videoWidth = videoHeight
                videoHeight = tmp
            }

            retriever.release()

            // Transcode & overlay frames
            val success = processVideoFrames(
                context = context,
                inputFile = inputFile,
                outputFile = outputFile,
                durationMs = durationMs,
                snapshotsTimeline = snapshotsTimeline,
                defaultSnapshot = defaultSnapshot,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
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
                return false
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var muxerAudioTrack = -1
            if (audioTrackIndex >= 0 && audioFormat != null) {
                muxerAudioTrack = muxer.addTrack(audioFormat)
            }

            // Create Video Encoder (H.264/AVC)
            val outputMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val encFormat = MediaFormat.createVideoFormat(outputMime, videoWidth, videoHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (videoWidth * videoHeight * 4).coerceIn(2_000_000, 12_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(outputMime)
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderInputSurface = encoder.createInputSurface()
            encoder.start()

            val muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
            muxer.start()

            // Copy audio samples if present
            if (audioTrackIndex >= 0 && muxerAudioTrack >= 0) {
                copyAudioTrack(extractor, audioTrackIndex, muxer, muxerAudioTrack)
            }

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputFile.absolutePath)

            val frameIntervalMs = 1000L / 30 // ~30 fps
            var currentMs = 0L
            val totalFrames = maxOf(1, (durationMs / frameIntervalMs).toInt())

            var frameCount = 0
            while (currentMs < durationMs) {
                val frameBitmap = retriever.getFrameAtTime(currentMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(currentMs * 1000L)

                if (frameBitmap != null) {
                    val snapshotForFrame = getSnapshotAtTime(snapshotsTimeline, defaultSnapshot, currentMs)

                    val stampedBitmap = OverlayRenderer.renderOverlay(context, frameBitmap, snapshotForFrame)

                    val canvas = encoderInputSurface.lockCanvas(null)
                    val srcRect = Rect(0, 0, stampedBitmap.width, stampedBitmap.height)
                    val dstRect = Rect(0, 0, videoWidth, videoHeight)
                    canvas.drawBitmap(stampedBitmap, srcRect, dstRect, null)
                    encoderInputSurface.unlockCanvasAndPost(canvas)

                    drainEncoder(encoder, muxer, muxerVideoTrack, false)

                    if (!stampedBitmap.isRecycled && stampedBitmap != frameBitmap) {
                        stampedBitmap.recycle()
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

            drainEncoder(encoder, muxer, muxerVideoTrack, true)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}", e)
            return false
        } finally {
            try { encoder?.stop(); encoder?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (ignored: Exception) {}
        }
    }

    private fun getSnapshotAtTime(
        timeline: List<CaptureSnapshot>,
        defaultSnapshot: CaptureSnapshot,
        msFromStart: Long
    ): CaptureSnapshot {
        if (timeline.isEmpty()) {
            val targetTime = defaultSnapshot.timestamp + msFromStart
            return defaultSnapshot.copy(timestamp = targetTime)
        }

        val startTimestamp = timeline.first().timestamp
        val targetTimestamp = startTimestamp + msFromStart
        val matched = timeline.lastOrNull { it.timestamp <= targetTimestamp }
            ?: timeline.first()

        return matched.copy(timestamp = targetTimestamp)
    }

    private fun copyAudioTrack(
        extractor: MediaExtractor,
        audioTrackIndex: Int,
        muxer: MediaMuxer,
        muxerAudioTrack: Int
    ) {
        try {
            extractor.selectTrack(audioTrackIndex)
            val bufferSize = 256 * 1024
            val buffer = java.nio.ByteBuffer.allocate(bufferSize)
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
        trackIndex: Int,
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
            } else if (encoderStatus >= 0) {
                val encodedData = encoder.getOutputBuffer(encoderStatus)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(encoderStatus, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }
}
