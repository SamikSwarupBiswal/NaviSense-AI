package dev.navisense.navigation

import dev.navisense.contracts.*
import dev.navisense.tracking.ActiveTrack
import dev.navisense.tracking.VisualTracker
import kotlin.math.abs

/**
 * Deterministic multi-source Risk Engine adhering to PRD v3.2 Section 17.
 *
 * Implements:
 * 1. Independent ultrasonic proximity rules with 1.0s de-escalation hold and +15cm margin (PRD §17.1, §17.3).
 * 2. Visual corridor evaluation with short-lived tracking and qualification (PRD §17.1, §17.4).
 * 3. Optical Expansion / Looming Detection (PRD §17.1):
 *    Expansion Rate = (current_area - prev_area) / (prev_area * dt)
 *    Triggers SLOW ("<Label> approaching") or STOP on rapid approach.
 * 4. Staleness watchdogs for sensor (500 ms) and camera (1000 ms) (PRD §17.2).
 * 5. Path clearance evaluation for BLOCKED, CLEAR_OBSERVED, and UNKNOWN (PRD §17.2).
 */
class RiskEngine(
    val corridor: WalkingCorridor = WalkingCorridor(),
    val tracker: VisualTracker = VisualTracker(matchIouThreshold = 0.30f, trackExpiryMs = 500L),
    val loomingThresholdRate: Float = 0.50f, // 50% expansion per second (~25% over 0.5s)
    val clock: IClock = SystemMonotonicClock()
) : IRiskEngine {

    private var lastSensorEvent: SensorEvent? = null
    private var lastPerceptionEvent: PerceptionFrameEvent? = null

    private var heldSensorRisk: RiskLevel = RiskLevel.NONE
    private var sensorReleaseStartTimeMs: Long? = null

    private var heldVisionRisk: RiskLevel = RiskLevel.NONE
    private var visionReleaseStartTimeMs: Long? = null

    private var clearanceStartTimeMs: Long? = null
    private var previousCombinedRisk: RiskLevel = RiskLevel.NONE

    private var lastPrimaryHazardSource: RiskSource? = null
    private var lastAssociatedObjectLabel: String? = null
    private var lastIsApproachingHazard: Boolean = false
    private var lastExpansionRate: Float? = null

    @Synchronized
    override fun onSensorEvent(event: SensorEvent): RiskEvaluationResult {
        lastSensorEvent = event
        val currentMonotonicMs = event.receiptMonotonicMs

        // 1. Evaluate raw ultrasonic risk
        val rawSensorRisk = if (event.wireRecord.isValid && event.wireRecord.distanceCm in 2..400) {
            when {
                event.wireRecord.distanceCm <= 50 -> RiskLevel.STOP
                event.wireRecord.distanceCm <= 100 -> RiskLevel.SLOW
                event.wireRecord.distanceCm <= 150 -> RiskLevel.AWARENESS
                else -> RiskLevel.NONE
            }
        } else {
            // Disconnected or invalid echo: vision-only mode, no ultrasonic hazard
            RiskLevel.NONE
        }

        // 2. Apply ultrasonic escalation & de-escalation release rules (PRD §17.3)
        if (rawSensorRisk.severity > heldSensorRisk.severity) {
            // Immediate escalation
            heldSensorRisk = rawSensorRisk
            sensorReleaseStartTimeMs = null
        } else if (rawSensorRisk.severity < heldSensorRisk.severity) {
            // De-escalation requires distance above active boundary + 15 cm continuously for 1.0s
            val requiredClearanceDistanceCm = when (heldSensorRisk) {
                RiskLevel.STOP -> 50 + 15      // > 65 cm
                RiskLevel.SLOW -> 100 + 15     // > 115 cm
                RiskLevel.AWARENESS -> 150 + 15 // > 165 cm
                RiskLevel.NONE -> 0
            }

            val isDistanceSafe = event.wireRecord.isValid && event.wireRecord.distanceCm > requiredClearanceDistanceCm
            if (isDistanceSafe) {
                if (sensorReleaseStartTimeMs == null) {
                    sensorReleaseStartTimeMs = currentMonotonicMs
                } else if (currentMonotonicMs - sensorReleaseStartTimeMs!! >= 1000L) {
                    // Release timer satisfied: step down directly to current raw level
                    heldSensorRisk = rawSensorRisk
                    sensorReleaseStartTimeMs = null
                }
            } else {
                // Violating reading resets release timer
                sensorReleaseStartTimeMs = null
            }
        } else {
            sensorReleaseStartTimeMs = null
        }

        return buildResult(currentMonotonicMs)
    }

    @Synchronized
    override fun onPerceptionEvent(event: PerceptionFrameEvent): RiskEvaluationResult {
        lastPerceptionEvent = event
        val currentMonotonicMs = event.captureMonotonicMs

        // 1. Frame quality check (PRD §17.2)
        if (event.qualityStatus == FrameQualityStatus.UNUSABLE) {
            clearanceStartTimeMs = null
            return buildResult(currentMonotonicMs)
        }

        // 2. Update visual tracker
        val trackedDetections = tracker.update(event.detections, currentMonotonicMs, event.geometryVersion)

        // Reset clearance if any tentative detection >= 0.40 exists in corridor
        for (det in trackedDetections) {
            if (det.confidence >= 0.40f && corridor.isCorridorObstacle(det.boundingBox)) {
                clearanceStartTimeMs = null
                break
            }
        }

        // 3. Evaluate qualified corridor tracks
        var rawVisionRisk = RiskLevel.NONE
        var primaryTrack: ActiveTrack? = null
        var qualifiedCorridorTrackCount = 0
        var isApproaching = false
        var maxExpansionRate: Float? = null

        val activeTracks = tracker.currentActiveTracks
        for (track in activeTracks) {
            if (!track.isQualified(currentMonotonicMs, windowMs = 1000L)) continue
            if (!corridor.isCorridorObstacle(track.currentBox)) continue

            qualifiedCorridorTrackCount++
            primaryTrack = track

            val isNearBottom = track.currentBox.bottom >= 0.85f && track.currentBox.area >= 0.20f
            val expansion = track.computeExpansionRate(currentMonotonicMs, targetIntervalMs = 500L, toleranceMs = 150L)
            val trackApproaching = expansion != null && expansion >= loomingThresholdRate

            if (expansion != null) {
                if (maxExpansionRate == null || expansion > maxExpansionRate) {
                    maxExpansionRate = expansion
                }
            }

            val trackRisk = when {
                // PRD §17.1 Rule 7: Near corridor track growing >= 25% over 0.5s -> STOP
                isNearBottom && trackApproaching -> {
                    isApproaching = true
                    RiskLevel.STOP
                }
                // Optical expansion looming warning in corridor -> SLOW
                trackApproaching -> {
                    isApproaching = true
                    RiskLevel.SLOW
                }
                // Near bottom corridor track -> SLOW
                isNearBottom -> RiskLevel.SLOW
                // Persistent corridor track -> AWARENESS
                else -> RiskLevel.AWARENESS
            }

            if (trackRisk.severity > rawVisionRisk.severity) {
                rawVisionRisk = trackRisk
            }
        }

        lastIsApproachingHazard = isApproaching
        lastExpansionRate = maxExpansionRate

        // 4. Vision hold and release policy (PRD §17.3)
        if (rawVisionRisk.severity > heldVisionRisk.severity) {
            heldVisionRisk = rawVisionRisk
            visionReleaseStartTimeMs = null
        } else if (rawVisionRisk.severity < heldVisionRisk.severity) {
            if (visionReleaseStartTimeMs == null) {
                visionReleaseStartTimeMs = currentMonotonicMs
            } else if (currentMonotonicMs - visionReleaseStartTimeMs!! >= 1000L) {
                heldVisionRisk = rawVisionRisk
                visionReleaseStartTimeMs = null
            }
        } else {
            visionReleaseStartTimeMs = null
        }

        // Single corridor track association under PRD §17.4
        val sensor = lastSensorEvent
        val syncAllowed = sensor != null &&
                sensor.wireRecord.isValid &&
                abs(sensor.receiptMonotonicMs - currentMonotonicMs) <= 200L

        lastAssociatedObjectLabel = if (qualifiedCorridorTrackCount == 1 && primaryTrack != null && syncAllowed) {
            if (isApproaching) "${primaryTrack.label} approaching" else primaryTrack.label
        } else if (isApproaching && primaryTrack != null) {
            "${primaryTrack.label} approaching"
        } else if (isApproaching) {
            "Obstacle approaching"
        } else {
            null
        }

        return buildResult(currentMonotonicMs)
    }

    @Synchronized
    override fun onWatchdogTick(currentTimeMonotonicMs: Long): RiskEvaluationResult {
        // Sensor staleness: no usable sensor event for > 500 ms
        val sensorStale = lastSensorEvent == null ||
                (currentTimeMonotonicMs - lastSensorEvent!!.receiptMonotonicMs > 500L)

        if (sensorStale) {
            // Drop held sensor severity on sustained loss, but cannot claim clear
            heldSensorRisk = RiskLevel.NONE
            sensorReleaseStartTimeMs = null
            clearanceStartTimeMs = null
        }

        // Camera staleness: no usable camera frame for > 1000 ms (PRD §17.2)
        val cameraStale = lastPerceptionEvent == null ||
                (currentTimeMonotonicMs - lastPerceptionEvent!!.captureMonotonicMs > 1000L)

        if (cameraStale) {
            heldVisionRisk = RiskLevel.NONE
            visionReleaseStartTimeMs = null
            clearanceStartTimeMs = null
        }

        return buildResult(currentTimeMonotonicMs)
    }

    @Synchronized
    override fun reset() {
        lastSensorEvent = null
        lastPerceptionEvent = null
        heldSensorRisk = RiskLevel.NONE
        sensorReleaseStartTimeMs = null
        heldVisionRisk = RiskLevel.NONE
        visionReleaseStartTimeMs = null
        clearanceStartTimeMs = null
        previousCombinedRisk = RiskLevel.NONE
        lastPrimaryHazardSource = null
        lastAssociatedObjectLabel = null
        lastIsApproachingHazard = false
        lastExpansionRate = null
        tracker.reset()
    }

    private fun buildResult(currentMonotonicMs: Long): RiskEvaluationResult {
        val combinedRisk = RiskLevel.max(heldSensorRisk, heldVisionRisk)

        val primarySource = when {
            heldSensorRisk.severity > heldVisionRisk.severity -> RiskSource.SENSOR
            heldVisionRisk.severity > heldSensorRisk.severity -> RiskSource.VISION
            combinedRisk != RiskLevel.NONE -> RiskSource.SENSOR // Sensor priority on tie
            else -> null
        }
        lastPrimaryHazardSource = primarySource

        val pathStatus = evaluatePathStatus(combinedRisk, currentMonotonicMs)
        val isEscalation = combinedRisk.severity > previousCombinedRisk.severity
        previousCombinedRisk = combinedRisk

        return RiskEvaluationResult(
            combinedRisk = combinedRisk,
            sensorRisk = heldSensorRisk,
            visionRisk = heldVisionRisk,
            pathStatus = pathStatus,
            primaryHazardSource = primarySource,
            associatedObjectLabel = lastAssociatedObjectLabel,
            isEscalation = isEscalation,
            timestampMonotonicMs = currentMonotonicMs,
            isApproachingHazard = lastIsApproachingHazard,
            expansionRate = lastExpansionRate
        )
    }

    private fun evaluatePathStatus(combinedRisk: RiskLevel, currentMonotonicMs: Long): PathStatus {
        if (combinedRisk != RiskLevel.NONE) {
            clearanceStartTimeMs = null
            return PathStatus.BLOCKED
        }

        // PRD §17.2: CLEAR_OBSERVED requirements
        val sensor = lastSensorEvent
        val sensorFreshAndClear = sensor != null &&
                sensor.wireRecord.isValid &&
                sensor.wireRecord.distanceCm > 150 &&
                (currentMonotonicMs - sensor.receiptMonotonicMs <= 500L)

        val camera = lastPerceptionEvent
        val cameraFreshAndUsable = camera != null &&
                camera.qualityStatus == FrameQualityStatus.USABLE &&
                (currentMonotonicMs - camera.captureMonotonicMs <= 1000L)

        val noCorridorHazard = heldVisionRisk == RiskLevel.NONE

        if (sensorFreshAndClear && cameraFreshAndUsable && noCorridorHazard) {
            if (clearanceStartTimeMs == null) {
                clearanceStartTimeMs = currentMonotonicMs
                return PathStatus.UNKNOWN
            } else if (currentMonotonicMs - clearanceStartTimeMs!! >= 1000L) {
                return PathStatus.CLEAR_OBSERVED
            } else {
                return PathStatus.UNKNOWN
            }
        } else {
            clearanceStartTimeMs = null
            return PathStatus.UNKNOWN
        }
    }
}
