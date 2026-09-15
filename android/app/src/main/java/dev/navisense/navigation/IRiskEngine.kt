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
    val timestampMonotonicMs: Long,
    val isApproachingHazard: Boolean = false,
    val expansionRate: Float? = null
)

/**
 * Samik-owned deterministic multimodal fusion/risk engine interface.
 * Rishav's coordinator consumes these decisions for app and speech behavior.
 */
interface IRiskEngine {
    /** Serializes and atomically reduces one sensor, vision, or watchdog input. */
    fun reduce(input: FusionInput): FusionTransition

    /** Processes incoming ultrasonic sensor event. */
    fun onSensorEvent(event: SensorEvent): RiskEvaluationResult =
        reduce(FusionInput.Sensor(event)).result

    /** Processes incoming mobile camera perception event. */
    fun onPerceptionEvent(event: PerceptionFrameEvent): RiskEvaluationResult =
        reduce(FusionInput.Vision(event)).result

    /** Periodic watchdog tick (called at least every 50 ms). */
    fun onWatchdogTick(currentTimeMonotonicMs: Long): RiskEvaluationResult =
        reduce(FusionInput.Watchdog(currentTimeMonotonicMs)).result

    /** Resets all source holds, clearance timers, and visual tracking histories. */
    fun reset()
}
