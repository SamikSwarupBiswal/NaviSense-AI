package dev.navisense.contracts

/**
 * Single object candidate returned by stationary laptop memory service (PRD Section 13.3).
 */
data class MemoryCandidate(
    val observationId: Long,
    val cameraId: String,
    val cameraProfileVersion: Int,
    val area: String,
    val zone: String,
    val relativeX: Float,
    val relativeY: Float,
    val confidence: Float,
    val lastSeenIso: String,
    val ageSeconds: Int,
    val freshness: String // "recent" or "stale"
) {
    val isStale: Boolean get() = ageSeconds > 60 || freshness == "stale"
}

/**
 * Lookup status returned by laptop object memory service.
 */
enum class MemoryLookupStatus {
    FOUND,
    AMBIGUOUS,
    HISTORICAL_ONLY,
    NOT_FOUND,
    UNSUPPORTED,
    SERVICE_ERROR
}

/**
 * H4 Contract: Response payload delivered by Subham's memory client to Rishav's coordinator.
 */
data class MemoryResponse(
    val apiVersion: Int,
    val status: MemoryLookupStatus,
    val objectName: String,
    val serverTimeIso: String,
    val candidates: List<MemoryCandidate>,
    val message: String? = null,
    val requestDurationMs: Long = 0L
)
