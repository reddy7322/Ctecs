package com.example.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.CamcorderProfile
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.HighlightYellow
import com.example.ui.theme.PrimaryBackground
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryText
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
    isVideoMode: Boolean,
    rotationDegrees: Int = 0,
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Refresh permission status when activity resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Bind to Lifecycle ONLY when camera permission is granted
    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        if (hasCameraPermission) {
            try {
                controller.bindToLifecycle(lifecycleOwner)
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to bind camera controller to lifecycle", e)
            }
        }
        onDispose {
            try {
                controller.unbind()
            } catch (ignored: Exception) {}
        }
    }

    // Configure Use Cases: In Photo mode, enable IMAGE_CAPTURE.
    // In Video mode, reliably enable VIDEO_CAPTURE.
    LaunchedEffect(isVideoMode, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            controller.isPinchToZoomEnabled = true
            controller.isTapToFocusEnabled = true

            if (isVideoMode) {
                try {
                    controller.setEnabledUseCases(LifecycleCameraController.VIDEO_CAPTURE)
                } catch (e: Throwable) {
                    try {
                        controller.setEnabledUseCases(
                            LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
                        )
                    } catch (e2: Throwable) {
                        Log.w("CameraPreview", "Failed to set VIDEO_CAPTURE use cases", e2)
                    }
                }
            } else {
                controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            }
        } catch (e: Exception) {
            Log.w("CameraPreview", "Setting use cases fallback", e)
            try {
                controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            } catch (ignored: Exception) {}
        }
    }

    // Safe Camera Selector
    LaunchedEffect(isFrontCamera, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val targetSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            if (controller.hasCamera(targetSelector)) {
                controller.cameraSelector = targetSelector
            } else if (controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            } else if (controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
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

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.controller = controller
                        // PERFORMANCE uses SurfaceView which eliminates TextureView EGL_BAD_ATTRIBUTE emulation errors
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Elegant fallback UI when camera permission is requested or pending
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimaryBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = HighlightYellow,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        color = PrimaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Highway Timestamp Camera Pro requires camera access to capture survey photos with real-time chainage and GPS stamps.",
                        color = SecondaryText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HighlightYellow,
                            contentColor = PrimaryBackground
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant Camera Permission", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Captures image from LifecycleCameraController and returns a correctly rotated Bitmap.
 */
fun capturePhoto(
    controller: LifecycleCameraController,
    context: Context,
    deviceRotationDegrees: Int = 0,
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

                    // Ensure bitmap is correctly rotated to match orientation
                    val finalRotation = if (deviceRotationDegrees != 0) {
                        (rotationDegrees + deviceRotationDegrees) % 360
                    } else {
                        rotationDegrees
                    }

                    val rotatedBitmap = if (finalRotation != 0 && rawBitmap != null) {
                        val matrix = Matrix().apply { postRotate(finalRotation.toFloat()) }
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

    fun isRecordingActive(): Boolean = activeRecording != null

    fun startRecording(
        controller: LifecycleCameraController,
        recordAudio: Boolean,
        onEvent: (VideoRecordEvent) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            if (!controller.isVideoCaptureEnabled) {
                try {
                    controller.setEnabledUseCases(LifecycleCameraController.VIDEO_CAPTURE)
                } catch (e: Exception) {
                    onError(IllegalStateException("Video recording is not supported on this camera profile."))
                    return
                }
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "VID_${timeStamp}.mp4"

            val executor = ContextCompat.getMainExecutor(context)
            val hasAudioPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val audioConfig = if (recordAudio && hasAudioPerm) {
                AudioConfig.create(true)
            } else {
                AudioConfig.create(false)
            }

            val recordListener = androidx.core.util.Consumer<VideoRecordEvent> { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                    if (!event.hasError()) {
                        try {
                            val uri = event.outputResults.outputUri
                            if (uri.scheme == "file") {
                                uri.path?.let { p ->
                                    android.media.MediaScannerConnection.scanFile(
                                        context,
                                        arrayOf(p),
                                        arrayOf("video/mp4")
                                    ) { _, _ -> }
                                }
                            }
                        } catch (ignored: Exception) {}
                    }
                }
                onEvent(event)
            }

            val pendingRecording = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(android.provider.MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/HighwayCamera")
                }
                val mediaStoreOptions = androidx.camera.video.MediaStoreOutputOptions.Builder(
                    context.contentResolver,
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(contentValues).build()

                controller.startRecording(
                    mediaStoreOptions,
                    audioConfig,
                    executor,
                    recordListener
                )
            } else {
                val outputDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "HighwayCamera"
                ).apply { mkdirs() }
                val videoFile = File(outputDir, fileName)
                val fileOptions = FileOutputOptions.Builder(videoFile).build()

                controller.startRecording(
                    fileOptions,
                    audioConfig,
                    executor,
                    recordListener
                )
            }
            activeRecording = pendingRecording
        } catch (e: Throwable) {
            activeRecording = null
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
