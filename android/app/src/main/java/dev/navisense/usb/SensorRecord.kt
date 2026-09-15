package dev.navisense.usb

/**
 * NaviSense AI — PRD v1 Ultrasonic Sensor Record
 *
 * Authority: PRD v3.2 Section 14.1 (Wire Format)
 * Wire format: V=1,SEQ=<seq>,UP_MS=<uptime_ms>,DIST_CM=<dist>,VALID=<valid>\n
 */
data class SensorRecord(
    val rawLine: String,
    val sequence: Long,           // Unsigned 32-bit sequence (stored in Long to avoid signed overflow)
    val uptimeMs: Long,           // Unsigned 32-bit device uptime in ms at measurement completion
    val distanceCm: Int,          // 2..400 when valid; -1 when invalid/no echo/timeout
    val isValid: Boolean,         // true if 2 <= distanceCm <= 400; false otherwise
    val receiptMonotonicMs: Long  // Android SystemClock.elapsedRealtime() timestamp
) {
    /**
     * Checks if this record qualifies for immediate STOP consideration (<= 50 cm).
     * Per PRD Section 14.2: A fresh valid <= 50 cm record may trigger STOP immediately
     * without establishing full clearance or recovery.
     */
    val isImmediateStopCandidate: Boolean
        get() = isValid && distanceCm in 2..50
}
