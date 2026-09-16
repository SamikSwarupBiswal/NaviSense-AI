package dev.navisense.contracts

/** Searchable classes packaged in the Android Locate model. */
enum class SearchTarget(val canonicalName: String, val displayName: String) {
    KEYS("keys", "Keys"),
    WALLET("wallet", "Wallet");

    companion object {
        fun fromValue(value: String?): SearchTarget? = when (value?.trim()?.lowercase()) {
            "key", "keys", "keychain", "house keys", "car keys" -> KEYS
            "wallet", "purse", "billfold" -> WALLET
            else -> null
        }
    }
}

/** Detailed UI state inside the app's FINAL_SEARCH/FOUND modes. */
enum class SearchUiState {
    NONE,
    LOADING_MODEL,
    SEARCHING,
    MULTIPLE_CANDIDATES,
    FOUND,
    TIMED_OUT,
    ERROR
}

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
    val errorMessage: String? = null,
    val obstacleInPath: String? = null,
    val isCloseEnough: Boolean = false,
    val targetBoxHeight: Float = 0f
)
