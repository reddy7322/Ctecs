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
    private var lastRenderedBearing: Int = Int.MIN_VALUE
    @Volatile
    private var lastRenderedRouteKey: String = ""
    @Volatile
    private var lastRenderedMiniMapSig: Int = 0
    @Volatile
    private var lastRenderedRetained: Boolean = false
    @Volatile
    private var lastRenderedWeatherSig: String = ""
    @Volatile
    private var lastRenderedCropMode: com.example.model.CameraCropMode? = null

    @Volatile
    private var sessionLastValidLocation: com.example.model.GeoPoint? = null
    @Volatile
    private var sessionLastValidBearing: Float? = null

    @Volatile
    private var isRecordingActive: Boolean = false
    @Volatile
    private var startRecordingRotation: Int = 0

    @Volatile
    private var effectInvalidated: Boolean = false

    private fun isRunningOnEmulator(): Boolean {
        val fp = android.os.Build.FINGERPRINT.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val hw = android.os.Build.HARDWARE.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        val brand = android.os.Build.BRAND.lowercase()
        val device = android.os.Build.DEVICE.lowercase()
        return fp.startsWith("generic") ||
            fp.startsWith("unknown") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            hw.contains("goldfish") ||
            hw.contains("ranchu") ||
            hw.contains("cutf") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            product.contains("simulator") ||
            (brand.startsWith("generic") && device.startsWith("generic"))
    }

    @Synchronized
    fun getOrCreateEffect(): OverlayEffect? {
        // On Android emulators (EGL_emulation / SwiftShader), CameraX OverlayEffect spawns a
        // secondary GL context that triggers EGL_BAD_ATTRIBUTE errors. Returning null keeps
        // video recording clean on emulators while real devices retain full real-time GPU overlay.
        if (isRunningOnEmulator()) {
            return null
        }
        if (currentEffect != null && !effectInvalidated) return currentEffect
        if (effectInvalidated) {
            release()
            effectInvalidated = false
        }

        try {
            val thread = HandlerThread("RealtimeVideoOverlayGLThread").apply { start() }
            glThread = thread
            val handler = Handler(thread.looper)
            glHandler = handler

            val errorListener = Consumer<Throwable> { error ->
                Log.e(TAG, "CameraX OverlayEffect error: ${error.message}", error)
                effectInvalidated = true
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

            // Retain last valid location/bearing smoothly if GPS is temporarily lost during recording
            if (baseSnapshot.location != null) {
                sessionLastValidLocation = baseSnapshot.location
            }
            if (baseSnapshot.bearing != null) {
                sessionLastValidBearing = baseSnapshot.bearing
            }
            val effectiveLastLoc = baseSnapshot.lastValidLocation ?: sessionLastValidLocation
            val effectiveLastBearing = baseSnapshot.lastValidBearing ?: sessionLastValidBearing
            val isRetained = baseSnapshot.location == null && effectiveLastLoc != null
            val effectiveBearingVal = baseSnapshot.bearing ?: effectiveLastBearing

            val currentChainage = baseSnapshot.displayChainage
            val locForCoords = baseSnapshot.location ?: effectiveLastLoc
            val currentCoords = locForCoords?.let {
                String.format(java.util.Locale.US, "%.5f_%.5f", it.latitude, it.longitude)
            } ?: ""
            val currentAccuracy = baseSnapshot.gpsAccuracy.toInt()
            val currentSpeed = (baseSnapshot.speedMps ?: -1f).toInt()
            val currentHeading = baseSnapshot.compassHeading
            val smartDir = baseSnapshot.matchResult.smartDirectionState
            val currentSide = if (smartDir != null) {
                "${smartDir.detectionState.name}_${smartDir.confirmedSide.orEmpty()}_${smartDir.isManualOverride}"
            } else {
                baseSnapshot.projectProfile.roadSide.name + "_" + baseSnapshot.matchResult.detectedSide
            }
            val currentActivity = baseSnapshot.projectProfile.activity
            val currentLane = baseSnapshot.projectProfile.lane
            val currentBearingInt = effectiveBearingVal?.toInt() ?: Int.MIN_VALUE
            val currentRouteKey = baseSnapshot.activeRoute?.let { "${it.name}_${it.alignments.size}_${it.markers.size}" } ?: ""
            val currentMiniMapSig = 31 * baseSnapshot.overlayConfig.showMapInset.hashCode() +
                baseSnapshot.overlayConfig.getEffectiveMiniMapConfig(isVideo = true).hashCode()
            val wObs = baseSnapshot.weatherObservation
            val currentWeatherSig = if (baseSnapshot.overlayConfig.showWeatherInfo) {
                "${baseSnapshot.locationName}|${wObs?.observedAtMs ?: 0L}|${wObs?.roundedTempC ?: "na"}|${wObs?.conditionLabel.orEmpty()}|${wObs?.humidityPercent ?: -1}|${wObs?.windSpeedKmh ?: -1.0}|${wObs?.windDirectionDegrees ?: -1}|${baseSnapshot.overlayConfig.weatherDisplayFormat.name}"
            } else {
                ""
            }

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
                    (renderHeight != lastRenderedHeight) ||
                    (currentBearingInt != lastRenderedBearing) ||
                    (currentRouteKey != lastRenderedRouteKey) ||
                    (currentMiniMapSig != lastRenderedMiniMapSig) ||
                    (isRetained != lastRenderedRetained) ||
                    (currentWeatherSig != lastRenderedWeatherSig) ||
                    (baseSnapshot.cropMode != lastRenderedCropMode)

            if (isDirty) {
                val canvas = frame.overlayCanvas
                // 1. Clear previous canvas buffer
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                // 2. Build updated snapshot with wall-clock millisecond timestamp and retained GPS
                val liveSnapshot = baseSnapshot.copy(
                    timestamp = nowMs,
                    deviceRotationDegrees = currentRotation,
                    lastValidLocation = effectiveLastLoc,
                    lastValidBearing = effectiveLastBearing,
                    isGpsRetained = isRetained,
                    isVideoCapture = true
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
                lastRenderedBearing = currentBearingInt
                lastRenderedRouteKey = currentRouteKey
                lastRenderedMiniMapSig = currentMiniMapSig
                lastRenderedRetained = isRetained
                lastRenderedWeatherSig = currentWeatherSig
                lastRenderedCropMode = baseSnapshot.cropMode
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
                drawOverlayWithinCropBounds(canvas, width, height, snapshot)
            }
            90 -> {
                // 90° clockwise: output display width is height, output display height is width
                canvas.translate(width.toFloat(), 0f)
                canvas.rotate(90f)
                drawOverlayWithinCropBounds(canvas, height, width, snapshot)
            }
            180 -> {
                // 180° reverse
                canvas.translate(width.toFloat(), height.toFloat())
                canvas.rotate(180f)
                drawOverlayWithinCropBounds(canvas, width, height, snapshot)
            }
            270 -> {
                // 270° clockwise (-90°): output display width is height, output display height is width
                canvas.translate(0f, height.toFloat())
                canvas.rotate(270f)
                drawOverlayWithinCropBounds(canvas, height, width, snapshot)
            }
            else -> {
                drawOverlayWithinCropBounds(canvas, width, height, snapshot)
            }
        }
        canvas.restore()
    }

    /**
     * Positions existing [OverlayRenderer] strictly within the active [com.example.model.CameraCropMode]
     * bounds so timestamps, GPS, chainage, LHS/RHS, weather, watermark, logo, and mini-map are 100% visible
     * and never clipped in both FULL and cropped video outputs.
     */
    private fun drawOverlayWithinCropBounds(
        canvas: android.graphics.Canvas,
        displayWidth: Int,
        displayHeight: Int,
        snapshot: CaptureSnapshot
    ) {
        val isLandscape = displayWidth >= displayHeight
        val cropRect = com.example.camera.CameraOutputFormatEngine.computeCropRectForMode(
            sourceWidth = displayWidth,
            sourceHeight = displayHeight,
            cropMode = snapshot.cropMode,
            isLandscape = isLandscape
        )
        if (cropRect.isCropped) {
            canvas.save()
            canvas.translate(cropRect.left.toFloat(), cropRect.top.toFloat())
            OverlayRenderer.drawOverlayOnCanvas(
                context = context,
                canvas = canvas,
                width = cropRect.width,
                height = cropRect.height,
                snapshot = snapshot,
                isVideoFrame = true
            )
            canvas.restore()
        } else {
            OverlayRenderer.drawOverlayOnCanvas(
                context = context,
                canvas = canvas,
                width = displayWidth,
                height = displayHeight,
                snapshot = snapshot,
                isVideoFrame = true
            )
        }
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
