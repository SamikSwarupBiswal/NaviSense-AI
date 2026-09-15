package dev.navisense.search

import dev.navisense.contracts.*

/**
 * Record of candidate detections in a single processed frame.
 */
data class SearchFrameRecord(
    val timestampMonotonicMs: Long,
    val targetDetections: List<DetectedObject>
)

/**
 * Stationary target search engine enforcing PRD §20 confirmation and timeout rules.
 */
class TargetSearchEngine(
    val confirmationWindowMs: Long = 1000L,
    val searchTimeoutMs: Long = 15000L,
    val minConfirmationFrames: Int = 3,
    val minConfidence: Float = 0.60f,
    val matchIouThreshold: Float = 0.30f
) {
    private var currentSessionGen: Long = -1L
    private var currentTargetClass: String = ""
    private var searchStartMonotonicMs: Long = 0L
    private var isConfirmed: Boolean = false
    private var isTimeoutEmitted: Boolean = false

    private val recentFrames = ArrayDeque<SearchFrameRecord>()
    private var lastFrameId = Long.MIN_VALUE
    private var lastCapture = Long.MIN_VALUE
    private var lastDelivery = Long.MIN_VALUE
    private var geometry: Int? = null
    private var model: String? = null

    val isSearchActive: Boolean get() = currentSessionGen >= 0 && !isConfirmed

    /**
     * Starts or restarts a target search session.
     */
    fun startSearch(
        targetClass: String,
        sessionGeneration: Long,
        startMonotonicMs: Long
    ) {
        val normalizedTarget = SearchTarget.fromValue(targetClass)
            ?: throw IllegalArgumentException("Unsupported nearby-search target: $targetClass")
        currentSessionGen = sessionGeneration
        currentTargetClass = normalizedTarget.canonicalName
        searchStartMonotonicMs = startMonotonicMs
        isConfirmed = false
        isTimeoutEmitted = false
        recentFrames.clear()
        lastFrameId = Long.MIN_VALUE
        lastCapture = Long.MIN_VALUE
        lastDelivery = Long.MIN_VALUE
        geometry = null
        model = null
    }

    /**
     * Stops and cancels the active search session.
     */
    fun stopSearch() {
        currentSessionGen = -1L
        currentTargetClass = ""
        isConfirmed = false
        isTimeoutEmitted = false
        recentFrames.clear()
    }

    /**
     * Emits the single timeout event independently of camera frame delivery.
     * The caller invokes this from the app's monotonic watchdog.
     */
    fun onTick(currentTimeMs: Long): SearchEvent? {
        if (!isSearchActive || currentTimeMs < searchStartMonotonicMs) return null
        if (currentTimeMs - searchStartMonotonicMs < searchTimeoutMs || isTimeoutEmitted) return null
        isTimeoutEmitted = true
        recentFrames.clear()
        return SearchEvent(
            sessionGeneration = currentSessionGen,
            targetClass = currentTargetClass,
            status = SearchStatus.TIMEOUT,
            direction = null,
            candidateCount = 0,
            timestampMonotonicMs = currentTimeMs,
            errorMessage = "Target not found in view within 15 seconds"
        )
    }

    /**
     * Processes an incoming perception event against the target search state machine.
     */
    fun processFrame(event: MobilePerceptionEvent): SearchEvent? {
        // Invalidate work from older or cancelled session generations (PRD §13.5)
        if (event.sessionGeneration != currentSessionGen || currentSessionGen < 0) {
            return null
        }

        // Once confirmed, stationary Found mode remains active without repeated confirmation
        if (isConfirmed) {
            return null
        }

        val currentTimeMs = event.deliveryMonotonicMs
        onTick(currentTimeMs)?.let { return it }
        if (event.mode != AppVisionMode.LOCATE_SEARCH || event.modelIdentity.isBlank()) return null
        if (model != null && model != event.modelIdentity) return null
        if (event.frameId <= lastFrameId || event.captureMonotonicMs <= lastCapture || currentTimeMs < lastDelivery) return null
        if (event.captureMonotonicMs < searchStartMonotonicMs || event.captureMonotonicMs > currentTimeMs || currentTimeMs - event.captureMonotonicMs > 500L) return null
        lastFrameId = event.frameId
        lastCapture = event.captureMonotonicMs
        lastDelivery = currentTimeMs
        if (geometry != event.geometryVersion) recentFrames.clear()
        geometry = event.geometryVersion
        model = event.modelIdentity

        // Only evaluate frames that are usable
        if (event.qualityStatus != FrameQualityStatus.USABLE || event.errorMessage != null) {
            recentFrames.clear()
            return null
        }

        // Filter for target class with confidence >= 0.60
        val matchingDetections = event.detections.filter {
            it.label.equals(currentTargetClass, ignoreCase = true) && it.confidence.isFinite() && it.confidence in minConfidence..1f &&
                listOf(it.boundingBox.left, it.boundingBox.top, it.boundingBox.right, it.boundingBox.bottom).all { coordinate -> coordinate.isFinite() && coordinate in 0f..1f } && it.boundingBox.area > 0f
        }

        // Maintain sliding window of latest 5 processed frames
        if (recentFrames.size >= 5) {
            recentFrames.removeFirst()
        }
        recentFrames.addLast(
            SearchFrameRecord(
                timestampMonotonicMs = event.captureMonotonicMs,
                targetDetections = matchingDetections
            )
        )

        // Evaluate candidate clusters across frames within 1000 ms
        val cutoff = currentTimeMs - confirmationWindowMs
        val framesInWindow = recentFrames.filter { it.timestampMonotonicMs >= cutoff }

        if (framesInWindow.size < minConfirmationFrames) {
            return SearchEvent(
                sessionGeneration = currentSessionGen,
                targetClass = currentTargetClass,
                status = SearchStatus.SEARCHING,
                direction = null,
                candidateCount = 0,
                timestampMonotonicMs = currentTimeMs
            )
        }

        // Cluster detections across frames using IoU >= 0.30
        val clusters = mutableListOf<MutableList<DetectedObject>>()

        for (frame in framesInWindow) {
            val available = clusters.toMutableList()
            for (det in frame.targetDetections.sortedByDescending { it.confidence }) {
                val matchingCluster = available.filter { it.last().boundingBox.calculateIoU(det.boundingBox) >= matchIouThreshold }
                    .maxByOrNull { it.last().boundingBox.calculateIoU(det.boundingBox) }
                if (matchingCluster != null) {
                    available.remove(matchingCluster)
                    matchingCluster.add(det)
                } else {
                    clusters.add(mutableListOf(det))
                }
            }
        }

        // Find clusters that have detections in >= 3 distinct frames
        val qualifyingClusters = clusters.filter { it.size >= minConfirmationFrames }

        if (matchingDetections.isNotEmpty() || framesInWindow.size >= 2) {
            android.util.Log.d("NaviSenseSearch", "TargetSearchEngine: matched ${matchingDetections.size} for '$currentTargetClass' (conf >= $minConfidence), framesInWindow=${framesInWindow.size}/$minConfirmationFrames")
        }

        return when {
            qualifyingClusters.size == 1 -> {
                val confirmedCluster = qualifyingClusters.first()
                val latestDetection = confirmedCluster.last()
                val direction = calculateDirection(latestDetection.boundingBox.centerX)
                isConfirmed = true
                android.util.Log.i("NaviSenseSearch", "TargetSearchEngine: CONFIRMED target=$currentTargetClass direction=$direction at centerX=${latestDetection.boundingBox.centerX}")

                SearchEvent(
                    sessionGeneration = currentSessionGen,
                    targetClass = currentTargetClass,
                    status = SearchStatus.CONFIRMED,
                    direction = direction,
                    candidateCount = 1,
                    timestampMonotonicMs = currentTimeMs
                )
            }
            qualifyingClusters.size > 1 -> {
                SearchEvent(
                    sessionGeneration = currentSessionGen,
                    targetClass = currentTargetClass,
                    status = SearchStatus.MULTIPLE_CANDIDATES,
                    direction = null,
                    candidateCount = qualifyingClusters.size,
                    timestampMonotonicMs = currentTimeMs
                )
            }
            else -> {
                SearchEvent(
                    sessionGeneration = currentSessionGen,
                    targetClass = currentTargetClass,
                    status = SearchStatus.SEARCHING,
                    direction = null,
                    candidateCount = 0,
                    timestampMonotonicMs = currentTimeMs
                )
            }
        }
    }

    /**
     * Maps box center X to coarse direction per PRD §20:
     * x < 1/3 -> LEFT, x > 2/3 -> RIGHT, otherwise -> CENTER.
     */
    private fun calculateDirection(centerX: Float): TargetDirection {
        return when {
            centerX < 1.0f / 3.0f -> TargetDirection.LEFT
            centerX > 2.0f / 3.0f -> TargetDirection.RIGHT
            else -> TargetDirection.CENTER
        }
    }
}
