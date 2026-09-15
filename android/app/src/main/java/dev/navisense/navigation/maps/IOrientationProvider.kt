package dev.navisense.navigation.maps

/**
 * Orientation provider contract for decoupling device heading calculation from Android sensors.
 */
interface IOrientationProvider {
    /**
     * Starts listening for orientation updates.
     * @param onHeadingChanged Callback delivering smoothed Azimuth heading in degrees [0, 360).
     */
    fun startListening(onHeadingChanged: (azimuthDegrees: Float) -> Unit)

    /**
     * Stops sensor listeners to preserve battery.
     */
    fun stopListening()

    /**
     * Returns the latest known Azimuth heading in degrees [0, 360).
     */
    fun getCurrentHeading(): Float
}
