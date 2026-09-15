package dev.navisense.contracts

/**
 * Health and connection status of the USB ultrasonic sensor node.
 */
enum class SensorHealth {
    /** USB cable disconnected or permission denied. Vision-only mode active. */
    DETACHED,

    /** USB connected and actively delivering valid serial records within <= 300 ms receipt age. */
    STREAMING,

    /** USB connected but no accepted record received within 300 ms. */
    STALE,

    /** USB connected but sensor emits VALID=0 or corrupted wire records. */
    INVALID_DATA,

    /** Communication or hardware error requiring recovery sequence. */
    ERROR
}
