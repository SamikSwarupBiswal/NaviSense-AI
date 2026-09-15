package dev.navisense.networking

import dev.navisense.contracts.BoundingBox

/**
 * Result of querying the stationary laptop object memory service.
 * Enforces PRD Section 23/24 specifications.
 */
sealed class LocateResult {
    data class Found(
        val queryName: String,
        val canonicalName: String,
        val candidate: MemoryCandidate,
        val ageSeconds: Double,
        val observationTimeIso: String,
        val scanId: String,
        val cameraProfileId: String
    ) : LocateResult()

    data class Ambiguous(
        val queryName: String,
        val canonicalName: String,
        val candidates: List<MemoryCandidate>,
        val ageSeconds: Double,
        val scanId: String,
        val cameraProfileId: String
    ) : LocateResult()

    data class Stale(
        val queryName: String,
        val canonicalName: String,
        val candidate: MemoryCandidate,
        val ageSeconds: Double,
        val observationTimeIso: String,
        val scanId: String,
        val cameraProfileId: String
    ) : LocateResult()

    data class HistoricalOnly(
        val queryName: String,
        val canonicalName: String,
        val candidates: List<MemoryCandidate>,
        val ageSeconds: Double,
        val cameraProfileId: String
    ) : LocateResult()

    data class NotFound(
        val queryName: String,
        val canonicalName: String?
    ) : LocateResult()

    data class Unsupported(
        val queryName: String
    ) : LocateResult()

    data class NetworkError(
        val queryName: String,
        val statusCode: Int?,
        val message: String
    ) : LocateResult()
}

/**
 * Candidate object returned from memory query.
 */
data class MemoryCandidate(
    val instanceId: String,
    val zoneId: String,
    val zoneName: String?,
    val box: BoundingBox,
    val confidence: Float
)

/**
 * Contract for the Android client communicating with the Laptop Locate REST service.
 * Owned by Subham.
 */
interface MemoryClientContract {
    companion object {
        const val MAX_TIMEOUT_MS: Long = 2000L
        const val MAX_RESPONSE_BYTES: Long = 65536L // 64 KiB
        const val STALE_THRESHOLD_SECONDS: Double = 60.0
    }

    /**
     * Query laptop memory for the last-seen location of an object.
     * 
     * @param queryName Raw object name requested by user
     * @param sessionGeneration Current active session generation
     * @return LocateResult representing outcome, or NetworkError if timed out / unreachable
     */
    suspend fun locateObject(queryName: String, sessionGeneration: Long): LocateResult

    /**
     * Check health and reachability of the laptop service.
     */
    suspend fun checkHealth(): Boolean

    /**
     * Cancel any pending in-flight request.
     */
    fun cancelPending()
}
