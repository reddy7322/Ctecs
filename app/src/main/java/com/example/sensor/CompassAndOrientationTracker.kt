package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.OrientationEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CompassState(
    val azimuthDegrees: Float = 0f,
    val cardinalDirection: String = "N",
    val isAvailable: Boolean = false
) {
    val displayHeading: String
        get() = if (isAvailable) "${azimuthDegrees.toInt()}° $cardinalDirection" else "---"
}

enum class DeviceOrientation(val degrees: Int) {
    PORTRAIT(0),
    LANDSCAPE_LEFT(90),
    REVERSE_PORTRAIT(180),
    LANDSCAPE_RIGHT(270)
}

class CompassAndOrientationTracker(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _compassState = MutableStateFlow(CompassState())
    val compassState: StateFlow<CompassState> = _compassState.asStateFlow()

    private val _deviceOrientation = MutableStateFlow(DeviceOrientation.PORTRAIT)
    val deviceOrientation: StateFlow<DeviceOrientation> = _deviceOrientation.asStateFlow()

    private val _rotationDegrees = MutableStateFlow(0f)
    val rotationDegrees: StateFlow<Float> = _rotationDegrees.asStateFlow()

    private var isTracking = false

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return

            // Hysteresis deadband: only switch when firmly within quadrant to prevent flickering
            val current = _deviceOrientation.value
            val newOrientation = when {
                (orientation in 330..359 || orientation in 0..30) -> DeviceOrientation.PORTRAIT
                (orientation in 60..120) -> DeviceOrientation.LANDSCAPE_RIGHT
                (orientation in 150..210) -> DeviceOrientation.REVERSE_PORTRAIT
                (orientation in 240..300) -> DeviceOrientation.LANDSCAPE_LEFT
                else -> current
            }

            val targetAngle = when (newOrientation) {
                DeviceOrientation.PORTRAIT -> 0f
                DeviceOrientation.LANDSCAPE_RIGHT -> 270f
                DeviceOrientation.REVERSE_PORTRAIT -> 180f
                DeviceOrientation.LANDSCAPE_LEFT -> 90f
            }

            if (_deviceOrientation.value != newOrientation || _rotationDegrees.value != targetAngle) {
                _deviceOrientation.value = newOrientation
                _rotationDegrees.value = targetAngle
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        private val gravity = FloatArray(3)
        private val geomagnetic = FloatArray(3)
        private var hasGravity = false
        private var hasGeomagnetic = false

        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    val normalizedAzimuth = (azimuth + 360f) % 360f
                    updateCompass(normalizedAzimuth)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    hasGravity = true
                    computeOrientationFallback()
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    hasGeomagnetic = true
                    computeOrientationFallback()
                }
            }
        }

        private fun computeOrientationFallback() {
            if (hasGravity && hasGeomagnetic) {
                val r = FloatArray(9)
                val i = FloatArray(9)
                if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(r, orientation)
                    val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    val normalizedAzimuth = (azimuth + 360f) % 360f
                    updateCompass(normalizedAzimuth)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun updateCompass(azimuth: Float) {
        val cardinal = when (azimuth) {
            in 348.75f..360f, in 0f..11.25f -> "N"
            in 11.25f..33.75f -> "NNE"
            in 33.75f..56.25f -> "NE"
            in 56.25f..78.75f -> "ENE"
            in 78.75f..101.25f -> "E"
            in 101.25f..123.75f -> "ESE"
            in 123.75f..146.25f -> "SE"
            in 146.25f..168.75f -> "SSE"
            in 168.75f..191.25f -> "S"
            in 191.25f..213.75f -> "SSW"
            in 213.75f..236.25f -> "SW"
            in 236.25f..258.75f -> "WSW"
            in 258.75f..281.25f -> "W"
            in 281.25f..303.75f -> "WNW"
            in 303.75f..326.25f -> "NW"
            in 326.25f..348.75f -> "NNW"
            else -> "N"
        }
        _compassState.value = CompassState(
            azimuthDegrees = azimuth,
            cardinalDirection = cardinal,
            isAvailable = true
        )
    }

    fun startTracking() {
        if (isTracking) return
        isTracking = true

        try {
            if (orientationListener.canDetectOrientation()) {
                orientationListener.enable()
            }
        } catch (ignored: Exception) {}

        sensorManager?.let { sm ->
            try {
                val rotationVectorSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                if (rotationVectorSensor != null) {
                    sm.registerListener(sensorListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
                } else {
                    val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    val magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
                    if (accel != null && magnet != null) {
                        sm.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_UI)
                        sm.registerListener(sensorListener, magnet, SensorManager.SENSOR_DELAY_UI)
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        try {
            orientationListener.disable()
            sensorManager?.unregisterListener(sensorListener)
        } catch (ignored: Exception) {}
    }
}
