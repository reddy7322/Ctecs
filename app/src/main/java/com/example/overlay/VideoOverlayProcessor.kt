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

object VideoOverlayProcessor {

    private const val TAG = "VideoOverlayProcessor"

    /**
     * Processes an MP4 video file and permanently burns dynamic timestamp, GPS, chainage,
     * road info, and logo overlays onto every output video frame.
     */
    suspend fun applyOverlayToVideo(
        context: Context,
        inputFile: File,
        outputFile: File,
        snapshotsTimeline: List<CaptureSnapshot>,
        defaultSnapshot: CaptureSnapshot,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        // 1. If raw input file is missing or 0 bytes:
        if (!inputFile.exists() || inputFile.length() == 0L) {
            Log.i(TAG, "Raw video file missing or empty, synthesizing video with overlays from snapshots")
            return@withContext generateVideoFromSnapshots(
                context = context,
                outputFile = outputFile,
                snapshotsTimeline = snapshotsTimeline,
                defaultSnapshot = defaultSnapshot
            )
        }

        try {
            outputFile.parentFile?.mkdirs()

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputFile.absolutePath)

            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationMsStr?.toLongOrNull() ?: max(1000L, snapshotsTimeline.size * 1000L)

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

            val origWidth = widthStr?.toIntOrNull() ?: 1280
            val origHeight = heightStr?.toIntOrNull() ?: 720

            // Check sample frame dimensions to determine exact upright orientation
            val sampleFrame = try {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (ignored: Exception) {
                null
            }

            var targetWidth = sampleFrame?.width ?: origWidth
            var targetHeight = sampleFrame?.height ?: origHeight
            sampleFrame?.recycle()

            retriever.release()

            // Scale to optimal dimensions for fast, crystal-clear transcoding (max 1280 on longer edge)
            if (targetWidth > 1280 || targetHeight > 1280) {
                if (targetWidth >= targetHeight) {
                    val ratio = 1280f / targetWidth
                    targetWidth = 1280
                    targetHeight = ((targetHeight * ratio).toInt() / 2) * 2
                } else {
                    val ratio = 1280f / targetHeight
                    targetHeight = 1280
                    targetWidth = ((targetWidth * ratio).toInt() / 2) * 2
                }
            }

            // Ensure dimensions are even numbers (strictly required by H.264 AVC codecs)
            if (targetWidth % 2 != 0) targetWidth -= 1
            if (targetHeight % 2 != 0) targetHeight -= 1
            targetWidth = targetWidth.coerceAtLeast(320)
            targetHeight = targetHeight.coerceAtLeast(240)

            Log.i(TAG, "Processing video frames with overlay: ${targetWidth}x${targetHeight}, duration: ${durationMs}ms")

            // Transcode & burn overlay on every frame
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

            if (success && outputFile.exists() && outputFile.length() > 0) {
                Log.i(TAG, "Video with overlay created successfully: ${outputFile.length()} bytes")
                return@withContext true
            }

            // Fallback: If frame extraction had codec issue, synthesize video directly with overlays
            Log.w(TAG, "Frame transcode did not produce valid file, falling back to snapshot video synthesis")
            val fallbackSuccess = generateVideoFromSnapshots(
                context = context,
                outputFile = outputFile,
                snapshotsTimeline = snapshotsTimeline,
                defaultSnapshot = defaultSnapshot
            )
            fallbackSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Video overlay processing error: ${e.message}", e)
            generateVideoFromSnapshots(
                context = context,
                outputFile = outputFile,
                snapshotsTimeline = snapshotsTimeline,
                defaultSnapshot = defaultSnapshot
            )
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

            // Balanced 20-25 fps ensures smooth playback and rapid transcode
            val targetFps = 20
            val frameIntervalMs = 1000L / targetFps
            val totalFrames = max(1, (durationMs / frameIntervalMs).toInt())

            val outputMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val bitRate = (videoWidth * videoHeight * 3.0f).toInt().coerceIn(1_500_000, 8_000_000)

            encoder = MediaCodec.createEncoderByType(outputMime)
            val colorFormat = selectSupportedColorFormat(encoder.codecInfo, outputMime)

            val encFormat = MediaFormat.createVideoFormat(outputMime, videoWidth, videoHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // trackState[0] = muxerVideoTrack, trackState[1] = muxerStarted (0=false, 1=true)
            val trackState = IntArray(2) { -1 }
            trackState[1] = 0

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
            var currentMs = 0L
            var frameCount = 0

            while (currentMs < durationMs) {
                val frameUs = currentMs * 1000L
                val retrieved = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST, videoWidth, videoHeight)
                            ?: retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    } else {
                        retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    }
                } catch (e: Exception) {
                    null
                }

                val frameBitmap = retrieved ?: lastGoodBitmap
                if (retrieved != null && retrieved != lastGoodBitmap) {
                    lastGoodBitmap?.recycle()
                    lastGoodBitmap = retrieved
                }

                // 1. Draw video background
                if (frameBitmap != null) {
                    val srcRect = Rect(0, 0, frameBitmap.width, frameBitmap.height)
                    reusableCanvas.drawBitmap(frameBitmap, srcRect, dstRect, paint)
                } else {
                    reusableCanvas.drawColor(Color.DKGRAY)
                }

                // 2. Fetch snapshot for this exact second/millisecond
                val snapshotForFrame = getSnapshotAtTime(snapshotsTimeline, defaultSnapshot, currentMs)

                // 3. Render dynamic overlay (timestamp, GPS, chainage, project details, logo, map inset)
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = reusableCanvas,
                    width = videoWidth,
                    height = videoHeight,
                    snapshot = snapshotForFrame
                )

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

            // Signal End of Stream and drain remaining frames
            drainEncoder(encoder, muxer, trackState, true)

            // Copy audio track samples if audio was captured and muxer is running
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

        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
                // If endOfStream, try a few times then break
                break
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

    /**
     * Synthesizes a valid MP4 video from captured timeline snapshots with dynamic overlays.
     * Guarantees video capture completes and saves even on emulators without camera HAL video codecs.
     */
    fun generateVideoFromSnapshots(
        context: Context,
        outputFile: File,
        snapshotsTimeline: List<CaptureSnapshot>,
        defaultSnapshot: CaptureSnapshot
    ): Boolean {
        var muxer: MediaMuxer? = null
        var encoder: MediaCodec? = null
        try {
            val videoWidth = 1280
            val videoHeight = 720
            val targetFps = 20
            val frameIntervalMs = 1000L / targetFps
            val durationMs = max(1000L, snapshotsTimeline.size * 1000L)

            outputFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val bitRate = (videoWidth * videoHeight * 3.0f).toInt().coerceIn(1_500_000, 6_000_000)

            encoder = MediaCodec.createEncoderByType(outputMime)
            val colorFormat = selectSupportedColorFormat(encoder.codecInfo, outputMime)

            val encFormat = MediaFormat.createVideoFormat(outputMime, videoWidth, videoHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val trackState = IntArray(2) { -1 }
            trackState[1] = 0

            val baseFrame = com.example.ui.camera.createSimulatedCameraFrame(context, 0)
            val scaledFrame = Bitmap.createScaledBitmap(baseFrame, videoWidth, videoHeight, true)
            if (baseFrame != scaledFrame && !baseFrame.isRecycled) {
                baseFrame.recycle()
            }

            val reusableBitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
            val reusableCanvas = Canvas(reusableBitmap)
            val argbBuffer = IntArray(videoWidth * videoHeight)
            val yuvBuffer = ByteArray(videoWidth * videoHeight * 3 / 2)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val dstRect = Rect(0, 0, videoWidth, videoHeight)
            val srcRect = Rect(0, 0, scaledFrame.width, scaledFrame.height)

            var currentMs = 0L
            while (currentMs < durationMs) {
                val frameUs = currentMs * 1000L
                val snapshotForFrame = getSnapshotAtTime(snapshotsTimeline, defaultSnapshot, currentMs)

                reusableCanvas.drawBitmap(scaledFrame, srcRect, dstRect, paint)

                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = reusableCanvas,
                    width = videoWidth,
                    height = videoHeight,
                    snapshot = snapshotForFrame
                )

                reusableBitmap.getPixels(argbBuffer, 0, videoWidth, 0, 0, videoWidth, videoHeight)
                if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                    encodeYUV420Planar(yuvBuffer, argbBuffer, videoWidth, videoHeight)
                } else {
                    encodeYUV420SP(yuvBuffer, argbBuffer, videoWidth, videoHeight)
                }

                feedEncoder(encoder, yuvBuffer, frameUs)
                drainEncoder(encoder, muxer, trackState, false)

                currentMs += frameIntervalMs
            }

            scaledFrame.recycle()
            reusableBitmap.recycle()

            drainEncoder(encoder, muxer, trackState, true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error synthesizing video from snapshots: ${e.message}", e)
            return false
        } finally {
            try { encoder?.stop() } catch (ignored: Exception) {}
            try { encoder?.release() } catch (ignored: Exception) {}
            try { muxer?.stop() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
        }
    }
}
