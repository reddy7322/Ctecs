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
    private var lastRenderedWidth: Int = -1
    @Volatile
    private var lastRenderedHeight: Int = -1

    @Volatile
    private var isRecordingActive: Boolean = false

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

    fun notifyRecordingStarted() {
        isRecordingActive = true
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
            val currentRotation = rotationProvider()
            val currentChainage = baseSnapshot.displayChainage
            val currentCoords = baseSnapshot.location?.let { "${it.latitude.toInt()}_${it.longitude.toInt()}" } ?: ""
            val currentAccuracy = baseSnapshot.gpsAccuracy.toInt()

            val frameSize = frame.size
            val width = frameSize.width
            val height = frameSize.height

            val isDirty = (currentSecond != lastRenderedSecond) ||
                    (currentRotation != lastRenderedRotation) ||
                    (currentChainage != lastRenderedChainage) ||
                    (currentCoords != lastRenderedCoords) ||
                    (currentAccuracy != lastRenderedAccuracy) ||
                    (width != lastRenderedWidth) ||
                    (height != lastRenderedHeight)

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
                    width = width,
                    height = height,
                    deviceRotation = currentRotation,
                    snapshot = liveSnapshot
                )

                // Update cached indicators
                lastRenderedSecond = currentSecond
                lastRenderedRotation = currentRotation
                lastRenderedChainage = currentChainage
                lastRenderedCoords = currentCoords
                lastRenderedAccuracy = currentAccuracy
                lastRenderedWidth = width
                lastRenderedHeight = height
            }
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Error drawing overlay on frame: ${e.message}", e)
            return true
        }
    }

    /**
     * Renders overlays onto the video frame canvas with dynamic 4-way orientation adjustments.
     * Ensures all text, badges, chainage, and logos stay perfectly upright when device is
     * rotated during active recording.
     */
    private fun renderRotatedOverlay(
        canvas: android.graphics.Canvas,
        width: Int,
        height: Int,
        deviceRotation: Int,
        snapshot: CaptureSnapshot
    ) {
        val naturalIsPortrait = (width <= height)
        // Normalize rotation angle
        val normalizedRotation = ((deviceRotation % 360) + 360) % 360

        // Compute relative rotation angle to keep overlays upright
        val effectiveRotation = if (naturalIsPortrait) {
            normalizedRotation
        } else {
            // For landscape buffers (width > height), base orientation is 0°
            (normalizedRotation + 270) % 360
        }

        canvas.save()
        when (effectiveRotation) {
            0 -> {
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = canvas,
                    width = width,
                    height = height,
                    snapshot = snapshot
                )
            }
            90 -> {
                // Landscape Right (90° clockwise)
                canvas.translate(width.toFloat(), 0f)
                canvas.rotate(90f)
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = canvas,
                    width = height,
                    height = width,
                    snapshot = snapshot
                )
            }
            180 -> {
                // Reverse Portrait (180°)
                canvas.translate(width.toFloat(), height.toFloat())
                canvas.rotate(180f)
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = canvas,
                    width = width,
                    height = height,
                    snapshot = snapshot
                )
            }
            270 -> {
                // Landscape Left (270°)
                canvas.translate(0f, height.toFloat())
                canvas.rotate(270f)
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = canvas,
                    width = height,
                    height = width,
                    snapshot = snapshot
                )
            }
            else -> {
                OverlayRenderer.drawOverlayOnCanvas(
                    context = context,
                    canvas = canvas,
                    width = width,
                    height = height,
                    snapshot = snapshot
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
