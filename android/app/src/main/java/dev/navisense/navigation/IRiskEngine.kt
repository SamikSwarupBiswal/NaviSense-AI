package dev.navisense.navigation

import dev.navisense.contracts.PathStatus
import dev.navisense.contracts.PerceptionFrameEvent
import dev.navisense.contracts.SensorEvent

/**
 * Result of a deterministic multi-source risk evaluation cycle (PRD Section 17).
 */
data class RiskEvaluationResult(
    val combinedRisk: RiskLevel,
    val sensorRisk: RiskLevel,
    val visionRisk: RiskLevel,
    val pathStatus: PathStatus,
    val primaryHazardSource: RiskSource?,
    val associatedObjectLabel: String?, // Visual label if exactly 1 track matches and sensor-camera sync <= 200 ms
    val isEscalation: Boolean,
    val timestampMonotonicMs: Long
)

/**
 * Deterministic Risk Engine interface owned by Rishav.
 */
interface IRiskEngine {
    /** Processes incoming ultrasonic sensor event. */
    fun onSensorEvent(event: SensorEvent): RiskEvaluationResult

    /** Processes incoming mobile camera perception event. */
    fun onPerceptionEvent(event: PerceptionFrameEvent): RiskEvaluationResult

    /** Periodic watchdog tick (called at least every 50 ms). */
    fun onWatchdogTick(currentTimeMonotonicMs: Long): RiskEvaluationResult

    /** Resets all source holds, clearance timers, and visual tracking histories. */
    fun reset()
}
