package dev.navisense.map

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log

/**
 * Manages precise GPS positioning and hardware compass azimuth tracking.
 */
class LocationTracker(
    private val context: Context,
    private val onLocationChanged: (lat: Double, lon: Double, accuracyMeters: Float, bearingDegrees: Float) -> Unit
) : LocationListener, SensorEventListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var gravityValues: FloatArray? = null
    private var geomagneticValues: FloatArray? = null
    private var currentAzimuthDegrees: Float = 0f

    private var isTracking = false
    private var mockMode = false
    private var lastLat: Double = 12.8407 // Defaults to VIT Chennai Main Gate
    private var lastLon: Double = 80.1534

    @SuppressLint("MissingPermission")
    fun startTracking(enableGps: Boolean = true) {
        if (isTracking) return
        isTracking = true

        if (enableGps && locationManager != null) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L, // 1 second
                        1.0f,  // 1 meter
                        this,
                        Looper.getMainLooper()
                    )
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000L,
                        1.0f,
                        this,
                        Looper.getMainLooper()
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Location permission not granted. Falling back to mock/last known mode.", e)
            }
        }

        // Register compass sensors
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magneticField = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (accelerometer != null && magneticField != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        try {
            locationManager?.removeUpdates(this)
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping location tracker", e)
        }
    }

    fun setMockLocation(lat: Double, lon: Double, bearing: Float = currentAzimuthDegrees) {
        mockMode = true
        lastLat = lat
        lastLon = lon
        onLocationChanged(lat, lon, 1.0f, bearing)
    }

    override fun onLocationChanged(location: Location) {
        if (mockMode) return
        lastLat = location.latitude
        lastLon = location.longitude
        val bearing = if (location.hasBearing()) location.bearing else currentAzimuthDegrees
        onLocationChanged(location.latitude, location.longitude, location.accuracy, bearing)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> gravityValues = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> geomagneticValues = event.values.clone()
        }

        val g = gravityValues
        val m = geomagneticValues
        if (g != null && m != null) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, g, m)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                // Azimuth in radians -> degrees (-180 to 180) -> normalize to (0 to 360)
                val azimuthRad = orientation[0]
                var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                if (azimuthDeg < 0) azimuthDeg += 360f
                currentAzimuthDegrees = azimuthDeg
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    companion object {
        private const val TAG = "LocationTracker"
    }
}
