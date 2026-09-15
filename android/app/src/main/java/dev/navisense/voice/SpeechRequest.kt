package dev.navisense.voice

/**
 * Strict speech priority hierarchy adhering to PRD Section 18.1.
 * Lower numerical value denotes higher priority (STOP = 1 is highest).
 */
enum class AlertPriority(val priorityLevel: Int) {
    STOP(1),
    SLOW(2),
    DIRECTIONAL(3),
    AWARENESS(4),
    HEALTH_UNKNOWN(5),
    INFORMATIONAL(6);

    fun hasHigherOrEqualPriorityThan(other: AlertPriority): Boolean =
        this.priorityLevel <= other.priorityLevel
}

/**
 * Encapsulated speech request submitted to the central speech arbiter.
 */
data class SpeechRequest(
    val utteranceId: String,
    val phrase: String,
    val priority: AlertPriority,
    val sessionGeneration: Long,
    val requestMonotonicMs: Long,
    val isEscalation: Boolean = false
)
