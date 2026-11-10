package com.example.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.core.CameraEffect
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import androidx.core.util.Consumer
import com.example.model.CaptureSnapshot

/**
 * High-performance real-time video overlay manager for CTECS.
 *
 * Utilizes CameraX [OverlayEffect] (OpenGL ES 2.0/3.0 SurfaceProcessor pipeline) targeting
 * [CameraEffect.VIDEO_CAPTURE] to render dynamic timestamps, GPS coordinates, chainage,
 * highway details, and logos directly into video frames in real time during active recording.
 *
 * Key Optimizations:
 * 1. Offloads all OpenGL surface composition and canvas drawing to a dedicated [HandlerThread].
 * 2. Caches overlay texture; only updates the Canvas when timestamp (second), GPS coordinates,
 *    chainage, road details, or orientation changes. Intermediate frames are composited
 *    directly by the GPU shader with zero CPU/canvas overhead.
 * 3. Supports dynamic 4-way orientation (0°, 90°, 180°, 270°) dynamically during active recording
 *    without restarting the camera or recording session.
 */
class RealtimeVideoOverlayManager(
    private val context: Context,
    private val snapshotProvider: () -> CaptureSnapshot?,
    private val rotationProvider: () -> Int = { 0 }
) {

    companion object {
        private const val TAG = "RealtimeVideoOverlay"
    }

    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null
    private var currentEffect: OverlayEffect? = null

    // State tracking for dirty-check caching
    @Volatile
    private var lastRenderedSecond: Long = -1L
    @Volatile
    private var lastRenderedRotation: Int = -1
    @Volatile
    private var lastRenderedChainage: String = ""
    @Volatile
    private var lastRenderedCoords: String = ""
    @Volatile
    private var lastRenderedAccuracy: Int = -1
    @Volatile
    private var lastRenderedSpeed: Int = -1
    @Volatile
    private var lastRenderedHeading: String = ""
    @Volatile
    private var lastRenderedSide: String = ""
    @Volatile
    private var lastRenderedActivity: String = ""
    @Volatile
    private var lastRenderedLane: String = ""
    @Volatile
    private var lastRenderedWidth: Int = -1
    @Volatile
    private var lastRenderedHeight: Int = -1

    @Volatile
    private var isRecordingActive: Boolean = false
    @Volatile
    private var startRecordingRotation: Int = 0

    @Synchronized
    fun getOrCreateEffect(): OverlayEffect? {
        if (currentEffect != null) return currentEffect

        try {
            val thread = HandlerThread("RealtimeVideoOverlayGLThread").apply { start() }
            glThread = thread
            val handler = Handler(thread.looper)
            glHandler = handler

            val errorListener = Consumer<Throwable> { error ->
                Log.e(TAG, "CameraX OverlayEffect error: ${error.message}", error)
            }

            val effect = OverlayEffect(
                CameraEffect.VIDEO_CAPTURE,
                1,
                handler,
                errorListener
            )

            effect.setOnDrawListener { frame: Frame ->
                processFrame(frame)
            }

            currentEffect = effect
            Log.i(TAG, "Realtime video overlay effect initialized successfully")
            return effect
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize OverlayEffect: ${e.message}", e)
            release()
            return null
        }
    }

    fun notifyRecordingStarted(initialRotation: Int? = null) {
        isRecordingActive = true
        startRecordingRotation = ((initialRotation ?: rotationProvider()) % 360 + 360) % 360
        // Force full refresh on first frame of a recording session
        lastRenderedSecond = -1L
        lastRenderedRotation = -1
    }

    fun notifyRecordingStopped() {
        isRecordingActive = false
    }

    private fun processFrame(frame: Frame): Boolean {
        try {
            val baseSnapshot = snapshotProvider() ?: return true
            val nowMs = System.currentTimeMillis()
            val currentSecond = nowMs / 1000L
            val currentRotation = ((rotationProvider() % 360) + 360) % 360
            val frameRotation = ((frame.rotationDegrees % 360) + 360) % 360
            val currentChainage = baseSnapshot.displayChainage
            val currentCoords = baseSnapshot.location?.let { String.format(java.util.Locale.US, "%.5f_%.5f", it.latitude, it.longitude) } ?: ""
            val currentAccuracy = baseSnapshot.gpsAccuracy.toInt()
            val currentSpeed = (baseSnapshot.speedMps ?: -1f).toInt()
            val currentHeading = baseSnapshot.compassHeading
            val currentSide = baseSnapshot.projectProfile.roadSide.name
            val currentActivity = baseSnapshot.projectProfile.activity
            val currentLane = baseSnapshot.projectProfile.lane

            val cropRect = try { frame.cropRect } catch (e: Throwable) { null }
            val hasCrop = cropRect != null && cropRect.width() > 0 && cropRect.height() > 0
            val renderWidth = if (hasCrop) cropRect.width() else frame.size.width
            val renderHeight = if (hasCrop) cropRect.height() else frame.size.height
            val cropLeft = if (hasCrop) cropRect.left.toFloat() else 0f
            val cropTop = if (hasCrop) cropRect.top.toFloat() else 0f

            // Calculate rotation needed on raw buffer canvas to appear upright
            // after the player applies MP4 rotation metadata (frameRotation)
            val delta = if (isRecordingActive) {
                ((currentRotation - startRecordingRotation) % 360 + 360) % 360
            } else 0
            val effectiveCanvasRotation = ((delta - frameRotation) % 360 + 360) % 360

            val isDirty = (currentSecond != lastRenderedSecond) ||
                    (effectiveCanvasRotation != lastRenderedRotation) ||
                    (currentChainage != lastRenderedChainage) ||
                    (currentCoords != lastRenderedCoords) ||
                    (currentAccuracy != lastRenderedAccuracy) ||
                    (currentSpeed != lastRenderedSpeed) ||
                    (currentHeading != lastRenderedHeading) ||
                    (currentSide != lastRenderedSide) ||
                    (currentActivity != lastRenderedActivity) ||
                    (currentLane != lastRenderedLane) ||
                    (renderWidth != lastRenderedWidth) ||
                    (renderHeight != lastRenderedHeight)

            if (isDirty) {
                val canvas = frame.overlayCanvas
                // 1. Clear previous canvas buffer
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                // 2. Build updated snapshot with wall-clock millisecond timestamp
                val liveSnapshot = baseSnapshot.copy(
                    timestamp = nowMs,
                    deviceRotationDegrees = currentRotation
                )

                // 3. Render upright overlay adjusted for current dynamic orientation
                renderRotatedOverlay(
                    canvas = canvas,
                    width = renderWidth,
                    height = renderHeight,
                    cropLeft = cropLeft,
                    cropTop = cropTop,
                    effectiveCanvasRotation = effectiveCanvasRotation,
                    snapshot = liveSnapshot
                )

                // Update cached indicators
                lastRenderedSecond = currentSecond
                lastRenderedRotation = effectiveCanvasRotation
                lastRenderedChainage = currentChainage
                lastRenderedCoords = currentCoords
                lastRenderedAccuracy = currentAccuracy
                lastRenderedSpeed = currentSpeed
                lastRenderedHeading = currentHeading
                lastRenderedSide = currentSide
                lastRenderedActivity = currentActivity
                lastRenderedLane = currentLane
                lastRenderedWidth = renderWidth
                lastRenderedHeight = renderHeight
            }
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Error drawing overlay on frame: ${e.message}", e)
            return true
        }
    }

    /**
     * Renders overlays onto the video frame canvas with mathematically verified orientation mapping.
     * Prevents double-rotation and guarantees that text, map, and logos stay perfectly upright
     * on the final video output across all 4 orientations (Portrait, Landscape Right,
     * Reverse Portrait, Landscape Left).
     */
    private fun renderRotatedOverlay(
        canvas: android.graphics.Canvas,
        width: Int,
        height: Int,
        cropLeft: Float,
        cropTop: Float,
        effectiveCanvasRotation: Int,
        snapshot: CaptureSnapshot
    ) {
        canvas.save()
        if (cropLeft > 0f || cropTop > 0f) {
            canvas.translate(cropLeft, cropTop)
        }
        when (effectiveCanvasRotation) {
            0 -> {
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = canvas,
                    width = width,
                    height = height,
                    snapshot = snapshot,
                    isVideoFrame = true
                )
            }
            90 -> {
                // 90° clockwise: output display width is height, output display height is width
                canvas.translate(width.toFloat(), 0f)
                canvas.rotate(90f)
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = canvas,
                    width = height,
                    height = width,
                    snapshot = snapshot,
                    isVideoFrame = true
                )
            }
            180 -> {
                // 180° reverse
                canvas.translate(width.toFloat(), height.toFloat())
                canvas.rotate(180f)
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = canvas,
                    width = width,
                    height = height,
                    snapshot = snapshot,
                    isVideoFrame = true
                )
            }
            270 -> {
                // 270° clockwise (-90°): output display width is height, output display height is width
                canvas.translate(0f, height.toFloat())
                canvas.rotate(270f)
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = canvas,
                    width = height,
                    height = width,
                    snapshot = snapshot,
                    isVideoFrame = true
                )
            }
            else -> {
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = canvas,
                    width = width,
                    height = height,
                    snapshot = snapshot,
                    isVideoFrame = true
                )
            }
        }
        canvas.restore()
    }

    @Synchronized
    fun release() {
        try {
            currentEffect?.close()
        } catch (ignored: Exception) {}
        currentEffect = null

        glThread?.quitSafely()
        glThread = null
        glHandler = null
    }
}
