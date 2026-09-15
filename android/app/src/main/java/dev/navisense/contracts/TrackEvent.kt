package dev.navisense.contracts

/**
 * Short-lived tracked object maintained across frames by Samik's tracker.
 */
data class TrackEvent(
    val trackId: Long,
    val classId: Int,
    val className: String,
    val confidence: Float,
    val box: NormalizedBoundingBox,
    val area: Float,
    val areaGrowthRate: Float,
    val isCorridorOverlap: Boolean,
    val isNearBottom: Boolean, // box bottom >= 0.85 and area >= 0.20
    val lastSeenMonotonicMs: Long
)
