package dev.navisense.navigation

/**
 * Risk severity classification adhering to PRD Section 17.1.
 * Ordered by strict ascending severity: NONE < AWARENESS < SLOW < STOP.
 */
enum class RiskLevel(val severity: Int) {
    NONE(0),
    AWARENESS(1),
    SLOW(2),
    STOP(3);

    companion object {
        fun max(a: RiskLevel, b: RiskLevel): RiskLevel = if (a.severity >= b.severity) a else b
    }
}

/**
 * Independent risk producer source.
 */
enum class RiskSource {
    SENSOR,
    VISION
}
