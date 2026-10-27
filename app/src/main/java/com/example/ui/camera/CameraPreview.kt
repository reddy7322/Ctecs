package com.example.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CameraPreview(
    isFrontCamera: Boolean,
    isFlashOn: Boolean,
    zoomRatio: Float,
    exposureIndex: Int,
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        try {
            // Enable both image and video capture use cases
            controller.setEnabledUseCases(
                LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
            )
            controller.isPinchToZoomEnabled = true
            controller.isTapToFocusEnabled = true
        } catch (e: Exception) {
            Log.w("CameraPreview", "Setting use cases fallback", e)
        }
    }

    LaunchedEffect(isFrontCamera) {
        try {
            controller.cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
        } catch (e: Exception) {
            Log.w("CameraPreview", "Camera selector error", e)
        }
    }

    LaunchedEffect(isFlashOn) {
        try {
            controller.enableTorch(isFlashOn)
        } catch (ignored: Exception) {}
    }

    LaunchedEffect(zoomRatio) {
        try {
            controller.setZoomRatio(zoomRatio.coerceIn(0.5f, 10.0f))
        } catch (ignored: Exception) {}
    }

    LaunchedEffect(exposureIndex) {
        try {
            controller.cameraControl?.setExposureCompensationIndex(exposureIndex)
        } catch (ignored: Exception) {}
    }

    DisposableEffect(lifecycleOwner) {
        try {
            controller.bindToLifecycle(lifecycleOwner)
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to bind camera controller", e)
        }
        onDispose {
            try {
                controller.unbind()
            } catch (ignored: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Captures image from LifecycleCameraController and returns a correctly rotated Bitmap.
 */
fun capturePhoto(
    controller: LifecycleCameraController,
    context: Context,
    onSuccess: (Bitmap) -> Unit,
    onError: (Throwable) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(context)
    controller.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    val rotatedBitmap = if (rotationDegrees != 0 && rawBitmap != null) {
                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                    } else {
                        rawBitmap
                    }

                    image.close()

                    if (rotatedBitmap != null) {
                        onSuccess(rotatedBitmap)
                    } else {
                        onError(Exception("Decoded bitmap is null"))
                    }
                } catch (e: Exception) {
                    image.close()
                    onError(e)
                }
            }

            override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                onError(exception)
            }
        }
    )
}

/**
 * Manages video recording with start/pause/resume/stop lifecycle
 */
class VideoRecordingManager(private val context: Context) {
    private var activeRecording: Recording? = null

    fun startRecording(
        controller: LifecycleCameraController,
        recordAudio: Boolean,
        onEvent: (VideoRecordEvent) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            val outputDir = File(context.filesDir, "highway_videos").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val videoFile = File(outputDir, "VID_${timeStamp}.mp4")
            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            val executor = ContextCompat.getMainExecutor(context)
            val pendingRecording = controller.startRecording(
                outputOptions,
                AudioConfig.create(recordAudio),
                executor
            ) { event ->
                onEvent(event)
            }
            activeRecording = pendingRecording
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun pause() {
        try {
            activeRecording?.pause()
        } catch (ignored: Exception) {}
    }

    fun resume() {
        try {
            activeRecording?.resume()
        } catch (ignored: Exception) {}
    }

    fun stop() {
        try {
            activeRecording?.stop()
            activeRecording = null
        } catch (ignored: Exception) {}
    }
}
