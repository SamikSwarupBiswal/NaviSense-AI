package dev.navisense.navigation

import dev.navisense.contracts.MobilePerceptionEvent
import dev.navisense.contracts.PathStatus
import dev.navisense.contracts.SensorEvent

/** Every asynchronous input accepted by the Samik-owned fusion reducer. */
sealed interface FusionInput {
    data class Sensor(val event: SensorEvent) : FusionInput
    data class Vision(val event: MobilePerceptionEvent) : FusionInput
    data class Watchdog(val nowMonotonicMs: Long) : FusionInput
}

enum class EvidenceAvailability {
    AVAILABLE,
    RECOVERING,
    INVALID,
    STALE,
    ABSENT
}

/** Immutable diagnostic state emitted after every atomic reducer transition. */
data class FusionState(
    val sensorRisk: RiskLevel,
    val visionRisk: RiskLevel,
    val combinedRisk: RiskLevel,
    val pathStatus: PathStatus,
    val sensorAvailability: EvidenceAvailability,
    val visionAvailability: EvidenceAvailability,
    val associatedObjectLabel: String?,
    val lastSensorReceiptMonotonicMs: Long?,
    val lastCameraDeliveryMonotonicMs: Long?,
    val evaluatedAtMonotonicMs: Long
)

/** Result plus immutable state for replay, diagnostics, and app integration. */
data class FusionTransition(
    val input: FusionInput,
    val accepted: Boolean,
    val state: FusionState,
    val result: RiskEvaluationResult
)
