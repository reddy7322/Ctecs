package com.example.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.overlay.VideoOverlayProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application-scoped video export manager that survives screen navigation, rotation,
 * and ViewModel lifecycle changes.
 *
 * Guarantees:
 * - Prevents duplicate concurrent exports for the same source file.
 * - Pre-checks available disk space before starting.
 * - Automatically picks the fastest valid method (Direct Copy -> Fast Remux -> Hardware GPU -> Safe Fallback).
 * - Reports real progress, real elapsed time, and realistic ETA (never fake progress).
 * - Supports safe cancellation with complete temp-file cleanup.
 * - Never deletes or modifies original gallery source videos.
 */
object VideoExportManager {

    private const val TAG = "VideoExportManager"
    private const val MEDIASTORE_BUFFER_SIZE = 256 * 1024 // 256 KB buffered stream copy

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _exportState = MutableStateFlow(VideoExportState())
    val exportState: StateFlow<VideoExportState> = _exportState.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeSourceKey: String? = null

    private val cancelRequested = AtomicBoolean(false)

    /**
     * Submits a video export request. Returns false if an export for the same source (or another
     * active export) is already in progress.
     */
    @Synchronized
    fun enqueueExport(
        context: Context,
        request: VideoExportRequest,
        onCompleted: ((Boolean, VideoExportState) -> Unit)? = null
    ): Boolean {
        val appCtx = context.applicationContext
        val sourceKey = request.sourceFile.absolutePath

        if (activeJob?.isActive == true) {
            if (activeSourceKey == sourceKey) {
                Log.i(TAG, "Ignoring duplicate export request for $sourceKey")
            } else {
                Log.i(TAG, "Export already in progress (${activeSourceKey}); ignoring concurrent request")
            }
            return false
        }

        cancelRequested.set(false)
        activeSourceKey = sourceKey

        val startState = VideoExportState(
            jobId = request.jobId.ifBlank { UUID.randomUUID().toString() },
            sourceKey = sourceKey,
            fileName = request.fileName,
            status = VideoExportStatus.PREPARING,
            method = VideoExportMethod.DIRECT_STREAM_COPY,
            progress = 0f,
            elapsedMs = 0L,
            estimatedRemainingMs = null,
            sourceSizeBytes = request.sourceFile.length().coerceAtLeast(0L)
        )
        _exportState.value = startState

        // Launch ForegroundService for reliable background execution when navigating away
        startForegroundServiceSafe(appCtx, request.fileName)

        activeJob = appScope.launch {
            val startClock = SystemClock.elapsedRealtime()
            var finalState = startState
            try {
                val validation = VideoOverlayProcessor.validateRecordedVideoFile(request.sourceFile)
                if (!validation.isValid) {
                    finalState = failExport(
                        startState,
                        startClock,
                        validation.errorReason ?: "Source video is missing, empty, or invalid."
                    )
                    cleanupFailedOutput(request)
                    onCompleted?.invoke(false, finalState)
                    return@launch
                }

                val plan = VideoExportStrategySelector.selectPlan(
                    displayWidth = validation.displayWidth,
                    displayHeight = validation.displayHeight,
                    sourceRotationDegrees = validation.rotationDegrees,
                    cropMode = request.cropMode,
                    hasRealtimeOverlay = request.hasRealtimeOverlay,
                    requireOverlayBurnIn = request.requireOverlayBurnIn,
                    rotationOverrideDegrees = request.rotationOverrideDegrees,
                    isContainerMp4 = request.isContainerMp4,
                    preserveAudio = request.preserveAudio,
                    sourceHasAudio = validation.hasAudioTrack
                )

                val sourceSize = request.sourceFile.length().coerceAtLeast(0L)
                val requiredBytes = VideoExportStrategySelector.estimateRequiredSpaceBytes(
                    sourceFileSizeBytes = sourceSize,
                    method = plan.method,
                    publishToMediaStore = request.publishToMediaStore
                )
                val availableBytes = queryAvailableStorageBytes(request.destinationFile.parentFile ?: appCtx.filesDir)
                if (availableBytes in 1 until requiredBytes) {
                    val needMb = (requiredBytes / (1024L * 1024L)).coerceAtLeast(1L)
                    val availMb = (availableBytes / (1024L * 1024L)).coerceAtLeast(0L)
                    finalState = failExport(
                        startState.copy(method = plan.method, sourceDurationMs = validation.durationMs),
                        startClock,
                        "Insufficient storage space for video export (need ~${needMb}MB, available ${availMb}MB)."
                    )
                    cleanupFailedOutput(request)
                    onCompleted?.invoke(false, finalState)
                    return@launch
                }

                _exportState.value = startState.copy(
                    status = VideoExportStatus.EXPORTING,
                    method = plan.method,
                    sourceDurationMs = validation.durationMs,
                    sourceSizeBytes = sourceSize
                )
                VideoExportForegroundService.updateNotification(appCtx, _exportState.value)

                val exportWeight = if (request.publishToMediaStore) 0.90f else 1.0f
                var lastUiNotifyMs = 0L
                val progressCallback: (Float) -> Unit = { rawProg ->
                    val scaled = (rawProg.coerceIn(0f, 1f) * exportWeight).coerceIn(0f, 0.99f)
                    val elapsed = SystemClock.elapsedRealtime() - startClock
                    val eta = VideoExportStrategySelector.computeRealisticRemainingMs(scaled, elapsed)
                    val updated = _exportState.value.copy(
                        status = VideoExportStatus.EXPORTING,
                        progress = scaled,
                        elapsedMs = elapsed,
                        estimatedRemainingMs = eta
                    )
                    _exportState.value = updated
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUiNotifyMs >= 350L) {
                        lastUiNotifyMs = now
                        VideoExportForegroundService.updateNotification(appCtx, updated)
                    }
                }

                val isCancelledCheck: () -> Boolean = { cancelRequested.get() }
                val actualMethod = arrayOf(plan.method)

                val exportedOk = VideoOverlayProcessor.executePlannedExport(
                    context = appCtx,
                    request = request,
                    validation = validation,
                    plan = plan,
                    onMethodChanged = { fallbackMethod ->
                        actualMethod[0] = fallbackMethod
                        _exportState.value = _exportState.value.copy(method = fallbackMethod)
                    },
                    isCancelled = isCancelledCheck,
                    onProgress = progressCallback
                )

                if (cancelRequested.get()) {
                    cleanupFailedOutput(request)
                    finalState = _exportState.value.copy(
                        status = VideoExportStatus.CANCELLED,
                        elapsedMs = SystemClock.elapsedRealtime() - startClock,
                        estimatedRemainingMs = null,
                        errorMessage = "Video export cancelled."
                    )
                    _exportState.value = finalState
                    VideoExportForegroundService.finishAndStop(appCtx, finalState)
                    onCompleted?.invoke(false, finalState)
                    return@launch
                }

                if (!exportedOk || !request.destinationFile.exists() || request.destinationFile.length() <= 0L) {
                    cleanupFailedOutput(request)
                    finalState = failExport(
                        _exportState.value.copy(method = actualMethod[0]),
                        startClock,
                        "Video export failed: unable to finalize playable MP4 output."
                    )
                    VideoExportForegroundService.finishAndStop(appCtx, finalState)
                    onCompleted?.invoke(false, finalState)
                    return@launch
                }

                // Single fast buffered publish to shared Movies/HighwayCamera MediaStore
                if (request.publishToMediaStore && !cancelRequested.get()) {
                    publishVideoToMediaStoreFast(
                        context = appCtx,
                        videoFile = request.destinationFile,
                        fileName = request.fileName,
                        timestampMs = request.defaultSnapshot?.timestamp ?: System.currentTimeMillis(),
                        isCancelled = isCancelledCheck,
                        onProgress = { pubProg ->
                            val totalProg = (0.90f + pubProg * 0.10f).coerceIn(0.90f, 0.99f)
                            val elapsed = SystemClock.elapsedRealtime() - startClock
                            _exportState.value = _exportState.value.copy(
                                progress = totalProg,
                                elapsedMs = elapsed,
                                estimatedRemainingMs = VideoExportStrategySelector.computeRealisticRemainingMs(totalProg, elapsed)
                            )
                        }
                    )
                }

                if (cancelRequested.get()) {
                    cleanupFailedOutput(request)
                    finalState = _exportState.value.copy(
                        status = VideoExportStatus.CANCELLED,
                        elapsedMs = SystemClock.elapsedRealtime() - startClock,
                        estimatedRemainingMs = null,
                        errorMessage = "Video export cancelled."
                    )
                    _exportState.value = finalState
                    VideoExportForegroundService.finishAndStop(appCtx, finalState)
                    onCompleted?.invoke(false, finalState)
                    return@launch
                }

                // Delete ONLY temporary raw camera cache files when explicitly requested (NEVER gallery originals!)
                if (request.deleteSourceAfterSuccess &&
                    request.sourceFile.exists() &&
                    request.sourceFile.absolutePath != request.destinationFile.absolutePath
                ) {
                    try { request.sourceFile.delete() } catch (_: Throwable) {}
                }

                val elapsedTotal = SystemClock.elapsedRealtime() - startClock
                val outputSize = request.destinationFile.length().coerceAtLeast(0L)
                finalState = _exportState.value.copy(
                    status = VideoExportStatus.COMPLETED,
                    method = actualMethod[0],
                    progress = 1f,
                    elapsedMs = elapsedTotal,
                    estimatedRemainingMs = 0L,
                    outputSizeBytes = outputSize,
                    outputFilePath = request.destinationFile.absolutePath,
                    errorMessage = null
                )
                _exportState.value = finalState
                Log.i(
                    TAG,
                    "Video export completed: method=${finalState.method.name}, duration=${finalState.sourceDurationMs}ms, " +
                        "src=${finalState.sourceSizeBytes}B, out=${finalState.outputSizeBytes}B, elapsed=${elapsedTotal}ms"
                )
                VideoExportForegroundService.finishAndStop(appCtx, finalState)
                onCompleted?.invoke(true, finalState)
            } catch (e: Throwable) {
                Log.e(TAG, "Unexpected video export failure: ${e.message}", e)
                cleanupFailedOutput(request)
                finalState = failExport(
                    _exportState.value,
                    startClock,
                    e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Video export failed."
                )
                VideoExportForegroundService.finishAndStop(appCtx, finalState)
                onCompleted?.invoke(false, finalState)
            } finally {
                synchronized(this@VideoExportManager) {
                    activeSourceKey = null
                    activeJob = null
                }
            }
        }
        return true
    }

    /**
     * Safely cancels the active video export and signals temp file cleanup.
     */
    fun cancelActiveExport(context: Context? = null) {
        if (activeJob?.isActive == true) {
            cancelRequested.set(true)
            val updated = _exportState.value.copy(
                status = VideoExportStatus.CANCELLED,
                estimatedRemainingMs = null,
                errorMessage = "Cancelling export..."
            )
            _exportState.value = updated
            context?.let { VideoExportForegroundService.finishAndStop(it.applicationContext, updated) }
        }
    }

    fun dismissCompletedOrFailedState() {
        val st = _exportState.value.status
        if (st == VideoExportStatus.COMPLETED || st == VideoExportStatus.FAILED || st == VideoExportStatus.CANCELLED) {
            _exportState.value = VideoExportState()
        }
    }

    private fun failExport(base: VideoExportState, startClock: Long, reason: String): VideoExportState {
        val failed = base.copy(
            status = VideoExportStatus.FAILED,
            elapsedMs = SystemClock.elapsedRealtime() - startClock,
            estimatedRemainingMs = null,
            errorMessage = reason
        )
        _exportState.value = failed
        return failed
    }

    private fun cleanupFailedOutput(request: VideoExportRequest) {
        try {
            if (request.destinationFile.exists() &&
                request.destinationFile.absolutePath != request.sourceFile.absolutePath
            ) {
                request.destinationFile.delete()
            }
        } catch (_: Throwable) {}
    }

    private fun queryAvailableStorageBytes(dir: File): Long {
        return try {
            val target = if (dir.exists()) dir else dir.parentFile ?: dir
            val stat = StatFs(target.absolutePath)
            stat.availableBytes
        } catch (_: Throwable) {
            -1L
        }
    }

    private fun startForegroundServiceSafe(appCtx: Context, fileName: String) {
        try {
            val intent = Intent(appCtx, VideoExportForegroundService::class.java).apply {
                action = VideoExportForegroundService.ACTION_START
                putExtra(VideoExportForegroundService.EXTRA_FILE_NAME, fileName)
            }
            ContextCompat.startForegroundService(appCtx, intent)
        } catch (e: Throwable) {
            // Gracefully continue in app-scoped IO coroutine if Android 12+ restricts background FGS start
            Log.w(TAG, "Background export FGS unavailable; continuing in app scope: ${e.message}")
        }
    }

    /**
     * Publishes the finalized MP4 once to MediaStore (`Movies/HighwayCamera`) using a 256KB buffer
     * without duplicating a redundant third public file copy.
     */
    private fun publishVideoToMediaStoreFast(
        context: Context,
        videoFile: File,
        fileName: String,
        timestampMs: Long,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
                    put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
                    put(MediaStore.Video.Media.DATE_TAKEN, timestampMs)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/HighwayCamera")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values) ?: return
                val totalBytes = videoFile.length().coerceAtLeast(1L)
                var copied = 0L

                val ok = try {
                    resolver.openOutputStream(uri)?.use { out ->
                        BufferedInputStream(FileInputStream(videoFile), MEDIASTORE_BUFFER_SIZE).use { input ->
                            val buf = ByteArray(MEDIASTORE_BUFFER_SIZE)
                            while (true) {
                                if (isCancelled()) return@use false
                                val read = input.read(buf)
                                if (read <= 0) break
                                out.write(buf, 0, read)
                                copied += read
                                onProgress((copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                            out.flush()
                            true
                        }
                    } ?: false
                } catch (e: Throwable) {
                    Log.w(TAG, "MediaStore stream write failed: ${e.message}")
                    false
                }

                if (ok && !isCancelled()) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } else {
                    try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
                }
            } else {
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "HighwayCamera"
                ).apply { mkdirs() }
                val publicFile = File(publicDir, fileName)
                if (!publicFile.exists() || publicFile.length() != videoFile.length()) {
                    HardwareVideoTranscoder.directStreamCopy(
                        inputFile = videoFile,
                        outputFile = publicFile,
                        allowAtomicMove = false,
                        isCancelled = isCancelled,
                        onProgress = onProgress
                    )
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(publicFile.absolutePath, videoFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "MediaStore video publish notice: ${e.message}")
        }
    }
}
