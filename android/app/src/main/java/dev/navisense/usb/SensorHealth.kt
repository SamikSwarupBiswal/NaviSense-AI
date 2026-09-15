package dev.navisense.usb

/**
 * Sensor health state according to PRD v3.2 Sections 14.1 & 14.2.
 */
enum class SensorHealth {
    /** USB connection is not established or permission denied */
    DISCONNECTED,

    /** USB connected; awaiting valid data stream */
    CONNECTING,

    /** Received valid records, but fewer than 3 advancing records or span < 150 ms */
    RECOVERING,

    /** Stream active, advancing, with fresh readings (age <= 300 ms) */
    HEALTHY,

    /** No fresh readings received within 300 ms */
    STALE,

    /** Sensor emitted invalid readings (VALID=0), out-of-range, or delay drift detected */
    DEGRADED_INVALID
}

/**
 * Sensor state snapshot provided to Rishav's Risk Engine.
 */
data class SensorState(
    val health: SensorHealth,
    val usableDistanceCm: Int?,
    val immediateStopCandidate: Boolean,
    val lastRecord: SensorRecord?,
    val sessionGeneration: Long
)
