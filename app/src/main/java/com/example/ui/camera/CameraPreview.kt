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
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.core.CameraEffect
import androidx.camera.effects.OverlayEffect
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.model.CaptureSnapshot
import com.example.model.OverlayTemplate
import com.example.overlay.OverlayRenderer
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
    photoResolution: String = "1920 x 1080",
    videoResolution: String = "1920 x 1080",
    controller: LifecycleCameraController,
    currentSnapshotProvider: () -> CaptureSnapshot? = { null },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestSnapshotProvider by rememberUpdatedState(currentSnapshotProvider)

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

    // Configure Use Cases: Enable IMAGE_CAPTURE and VIDEO_CAPTURE so switching between
    // photo and video modes is instantaneous with no camera unbinding or lag.
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            controller.isPinchToZoomEnabled = true
            controller.isTapToFocusEnabled = true

            try {
                controller.setEnabledUseCases(
                    LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
                )
            } catch (e: Throwable) {
                try {
                    controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
                } catch (ignored: Exception) {}
            }
            try {
                val effect = OverlayEffect(
                    CameraEffect.VIDEO_CAPTURE,
                    0,
                    android.os.Handler(android.os.Looper.getMainLooper())
                ) { error ->
                    Log.w("CameraPreview", "OverlayEffect notice: ${error.message}")
                }
                effect.setOnDrawListener { frameData ->
                    try {
                        val canvas = frameData.overlayCanvas
                        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                        val snapshot = latestSnapshotProvider()
                        if (snapshot != null && snapshot.overlayConfig.template != OverlayTemplate.NONE && snapshot.overlayConfig.burnInTimestamp) {
                            OverlayRenderer.drawOverlayOnCanvas(
                                context = context,
                                canvas = canvas,
                                width = canvas.width,
                                height = canvas.height,
                                snapshot = snapshot.copy(timestamp = System.currentTimeMillis())
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("CameraPreview", "Frame overlay draw error: ${e.message}")
                    }
                    true
                }
                controller.setEffects(setOf(effect))
            } catch (ignored: Exception) {}
        } catch (e: Exception) {
            Log.w("CameraPreview", "Setting use cases fallback", e)
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

    LaunchedEffect(photoResolution, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            val photoParts = photoResolution.split("x", "×").mapNotNull { it.trim().toIntOrNull() }
            if (photoParts.size == 2) {
                val size = android.util.Size(photoParts[0], photoParts[1])
                controller.imageCaptureTargetSize = androidx.camera.view.CameraController.OutputSize(size)
            }
        } catch (e: Exception) {
            Log.w("CameraPreview", "Setting photo resolution error", e)
        }
    }

    LaunchedEffect(videoResolution, isVideoMode, hasCameraPermission) {
        if (!hasCameraPermission || !isVideoMode) return@LaunchedEffect
        try {
            controller.videoCaptureQualitySelector = buildSafeQualitySelector(videoResolution, controller.cameraInfo)
        } catch (e: Exception) {
            Log.w("CameraPreview", "Setting video quality error", e)
        }
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
 * Normalizes raw captured bitmap orientation based on CameraX image info rotation
 * and physical device sensor orientation, ensuring true landscape or portrait output.
 */
fun normalizeBitmapOrientation(
    rawBitmap: Bitmap,
    cameraXRotationDegrees: Int,
    deviceRotationDegrees: Int
): Bitmap {
    val initialRotated = if (cameraXRotationDegrees != 0) {
        try {
            val matrix = Matrix().apply { postRotate(cameraXRotationDegrees.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } catch (e: Exception) {
            rawBitmap
        }
    } else {
        rawBitmap
    }

    val isLandscape = deviceRotationDegrees == 90 || deviceRotationDegrees == 270

    // If device is in landscape but bitmap is portrait, rotate by 90 or 270
    if (isLandscape && initialRotated.width < initialRotated.height) {
        try {
            val matrix = Matrix().apply { postRotate(90f) }
            return Bitmap.createBitmap(initialRotated, 0, 0, initialRotated.width, initialRotated.height, matrix, true)
        } catch (e: Exception) {
            return initialRotated
        }
    } else if (!isLandscape && initialRotated.width > initialRotated.height) {
        try {
            val matrix = Matrix().apply { postRotate(90f) }
            return Bitmap.createBitmap(initialRotated, 0, 0, initialRotated.width, initialRotated.height, matrix, true)
        } catch (e: Exception) {
            return initialRotated
        }
    }

    return initialRotated
}

/**
 * Captures image from LifecycleCameraController and returns a correctly rotated Bitmap
 * that strictly adheres to the physical device orientation.
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

                    if (rawBitmap == null) {
                        image.close()
                        onError(Exception("Decoded bitmap is null"))
                        return
                    }

                    val normalizedBitmap = normalizeBitmapOrientation(
                        rawBitmap = rawBitmap,
                        cameraXRotationDegrees = rotationDegrees,
                        deviceRotationDegrees = deviceRotationDegrees
                    )

                    image.close()
                    onSuccess(normalizedBitmap)
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
 * Safely constructs a QualitySelector with ordered fallback to prevent
 * "No supported EncoderProfiles" errors on emulators and unsupported devices.
 */
fun buildSafeQualitySelector(
    videoResolution: String = "1920 x 1080",
    cameraInfo: androidx.camera.core.CameraInfo? = null
): androidx.camera.video.QualitySelector {
    val supported = if (cameraInfo != null) {
        try {
            androidx.camera.video.QualitySelector.getSupportedQualities(cameraInfo)
        } catch (ignored: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }

    // If device/emulator camera HAL does not report any EncoderProfiles,
    // return Recorder's DEFAULT_QUALITY_SELECTOR to let CameraX negotiate safe fallback internally
    if (supported.isEmpty()) {
        return androidx.camera.video.Recorder.DEFAULT_QUALITY_SELECTOR
    }

    val videoParts = videoResolution.split("x", "×").mapNotNull { it.trim().toIntOrNull() }
    val w = if (videoParts.size == 2) Math.max(videoParts[0], videoParts[1]) else 1920
    val preferredQuality = when {
        w >= 3840 -> androidx.camera.video.Quality.UHD
        w >= 1920 -> androidx.camera.video.Quality.FHD
        w >= 1280 -> androidx.camera.video.Quality.HD
        else -> androidx.camera.video.Quality.SD
    }

    val preferredList = when (preferredQuality) {
        androidx.camera.video.Quality.UHD -> listOf(
            androidx.camera.video.Quality.UHD,
            androidx.camera.video.Quality.FHD,
            androidx.camera.video.Quality.HD,
            androidx.camera.video.Quality.SD,
            androidx.camera.video.Quality.LOWEST
        )
        androidx.camera.video.Quality.FHD -> listOf(
            androidx.camera.video.Quality.FHD,
            androidx.camera.video.Quality.HD,
            androidx.camera.video.Quality.SD,
            androidx.camera.video.Quality.LOWEST
        )
        androidx.camera.video.Quality.HD -> listOf(
            androidx.camera.video.Quality.HD,
            androidx.camera.video.Quality.SD,
            androidx.camera.video.Quality.LOWEST
        )
        else -> listOf(
            androidx.camera.video.Quality.SD,
            androidx.camera.video.Quality.LOWEST
        )
    }

    val matching = preferredList.filter { supported.contains(it) }
    val finalQualityList = if (matching.isNotEmpty()) matching else supported

    return androidx.camera.video.QualitySelector.fromOrderedList(
        finalQualityList,
        androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(
            finalQualityList.last()
        )
    )
}

/**
 * Manages video recording with start/pause/resume/stop lifecycle
 */
class VideoRecordingManager(private val context: Context) {
    private var activeRecording: Recording? = null
    var currentRecordingFile: File? = null
        private set

    fun isRecordingActive(): Boolean = activeRecording != null

    fun startRecording(
        controller: LifecycleCameraController,
        recordAudio: Boolean,
        onEvent: (VideoRecordEvent, File?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            if (!controller.isVideoCaptureEnabled) {
                try {
                    controller.setEnabledUseCases(
                        LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
                    )
                } catch (e: Exception) {
                    try {
                        controller.setEnabledUseCases(LifecycleCameraController.VIDEO_CAPTURE)
                    } catch (e2: Exception) {
                        Log.w("VideoRecordingManager", "VIDEO_CAPTURE usecase enable failed: ${e2.message}")
                    }
                }
            }

            val tempDir = File(context.cacheDir, "temp_video").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val tempFile = File(tempDir, "RAW_${timeStamp}.mp4")
            currentRecordingFile = tempFile

            val executor = ContextCompat.getMainExecutor(context)
            val hasAudioPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val audioConfig = if (recordAudio && hasAudioPerm) {
                try {
                    AudioConfig.create(true)
                } catch (e: Exception) {
                    AudioConfig.AUDIO_DISABLED
                }
            } else {
                AudioConfig.AUDIO_DISABLED
            }

            val fileOptions = FileOutputOptions.Builder(tempFile).build()

            val recordListener = androidx.core.util.Consumer<VideoRecordEvent> { event ->
                val recFile = currentRecordingFile
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                }
                onEvent(event, recFile)
            }

            val pendingRecording = try {
                controller.startRecording(
                    fileOptions,
                    audioConfig,
                    executor,
                    recordListener
                )
            } catch (e: SecurityException) {
                Log.w("VideoRecordingManager", "Audio recording permission error, retrying without audio", e)
                controller.startRecording(
                    fileOptions,
                    AudioConfig.AUDIO_DISABLED,
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
        } catch (ignored: Exception) {}
        activeRecording = null
    }
}
