package dev.navisense.contracts

/**
 * Raw wire protocol record parsed from ESP32-S3 serial stream (PRD Section 14.1).
 * Example: V=1,SEQ=1021,UP_MS=102100,DIST_CM=83,VALID=1
 */
data class SensorWireRecord(
    val version: Int,
    val sequenceNumber: Long, // Unsigned 32-bit integer
    val deviceUptimeMs: Long, // Unsigned 32-bit integer
    val distanceCm: Int,      // 2..400 if valid; -1 if invalid
    val isValid: Boolean      // true if VALID=1
) {
    companion object {
        const val EXPECTED_VERSION = 1
        const val MIN_VALID_DISTANCE_CM = 2
        const val MAX_VALID_DISTANCE_CM = 400
        const val INVALID_DISTANCE_CM = -1
    }
}

/**
 * H3 Contract: Sensor event delivered by Rohan's USB adapter to Rishav's coordinator.
 */
data class SensorEvent(
    val connectionId: Long,
    val receiptMonotonicMs: Long,
    val wireRecord: SensorWireRecord,
    val sensorHealth: SensorHealth
) {
    /** Helper indicating if this reading is a fresh, valid emergency proximity danger (<= 100 cm). */
    val isCriticalClose: Boolean
        get() = wireRecord.isValid && wireRecord.distanceCm in 2..100
}
