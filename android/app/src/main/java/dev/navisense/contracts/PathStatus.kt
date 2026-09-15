package dev.navisense.contracts

/**
 * Path state classification adhering to PRD Section 17.2.
 */
enum class PathStatus {
    /**
     * Insufficient evidence to establish clearance.
     * Applies in vision-only mode without obstacles, during startup, sensor no-echo, or model load.
     */
    UNKNOWN,

    /**
     * Explicitly verified clear corridor:
     * Requires sensor-assisted mode, valid distance > 150 cm continuously for 1s,
     * fresh usable camera results, no corridor track >= 0.40 confidence, and no held hazard.
     */
    CLEAR_OBSERVED,

    /**
     * Active vision or ultrasonic hazard detected within the walking corridor.
     */
    BLOCKED
}
