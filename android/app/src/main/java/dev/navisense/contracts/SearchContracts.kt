package dev.navisense.contracts

/**
 * Coarse image-space position of detected target relative to camera view.
 * PRD §20: box center x < 1/3 -> LEFT, x > 2/3 -> RIGHT, otherwise -> CENTER.
 */
enum class TargetDirection {
    LEFT,
    CENTER,
    RIGHT
}

/**
 * Lifecycle state of local target search.
 */
enum class SearchStatus {
    SEARCHING,
    CONFIRMED,
    MULTIPLE_CANDIDATES,
    TIMEOUT,
    ERROR
}

/**
 * Handoff H5: Target search event passed from Samik (Search Engine) to Rishav (Voice/Coordinator).
 */
data class SearchEvent(
    val sessionGeneration: Long,
    val targetClass: String,
    val status: SearchStatus,
    val direction: TargetDirection?,
    val candidateCount: Int,
    val timestampMonotonicMs: Long,
    val errorMessage: String? = null
)
