package dev.navisense.navigation.maps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Android hardware compass implementation utilizing Rotation Vector sensor with
 * Accelerometer + Magnetometer fallback, incorporating circular low-pass filtering.
 */
class DeviceCompassProvider(context: Context) : IOrientationProvider, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    @Volatile
    private var currentHeading: Float = 0f

    private var headingCallback: ((Float) -> Unit)? = null
    private var isListening: Boolean = false

    // Accelerometer + Magnetometer fallback buffers
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasAccelerometerReading = false
    private var hasMagnetometerReading = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Circular low-pass filter factor (0 < alpha <= 1)
    private val filterAlpha = 0.25f
    private var sinSum = 0.0
    private var cosSum = 1.0

    @Synchronized
    override fun startListening(onHeadingChanged: (azimuthDegrees: Float) -> Unit) {
        if (isListening) return
        this.headingCallback = onHeadingChanged
        this.isListening = true

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            if (accelerometerSensor != null) {
                sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (magneticSensor != null) {
                sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    @Synchronized
    override fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        headingCallback = null
        isListening = false
        hasAccelerometerReading = false
        hasMagnetometerReading = false
    }

    override fun getCurrentHeading(): Float = currentHeading

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val rawAzimuthRadians = orientationAngles[0]
                updateFilteredHeading(rawAzimuthRadians)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                hasAccelerometerReading = true
                computeFallbackOrientation()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                hasMagnetometerReading = true
                computeFallbackOrientation()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun computeFallbackOrientation() {
        if (hasAccelerometerReading && hasMagnetometerReading) {
            val success = SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,
                magnetometerReading
            )
            if (success) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val rawAzimuthRadians = orientationAngles[0]
                updateFilteredHeading(rawAzimuthRadians)
            }
        }
    }

    /**
     * Circular low-pass filter preventing discontinuities between 359° and 0°.
     */
    private fun updateFilteredHeading(rawAzimuthRadians: Float) {
        val rawSin = sin(rawAzimuthRadians.toDouble())
        val rawCos = cos(rawAzimuthRadians.toDouble())

        sinSum = (1.0 - filterAlpha) * sinSum + filterAlpha * rawSin
        cosSum = (1.0 - filterAlpha) * cosSum + filterAlpha * rawCos

        val filteredRadians = atan2(sinSum, cosSum)
        var degrees = Math.toDegrees(filteredRadians).toFloat()
        if (degrees < 0) {
            degrees += 360f
        }
        degrees %= 360f

        currentHeading = degrees
        headingCallback?.invoke(degrees)
    }
}
