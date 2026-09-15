package dev.navisense.camera

/**
 * Establishes a monotonic mapping from CameraX image timestamps to the app clock.
 *
 * Camera implementations are allowed to use a different monotonic origin from the
 * process clock. The first frame anchors the two domains; later frames must advance
 * in source order and remain no more than [maxCaptureAgeMs] behind delivery.
 */
class CameraTimestampMapper(
    private val maxCaptureAgeMs: Long = 500L,
    private val futureToleranceMs: Long = 5L
) {
    sealed interface Mapping {
        data class Usable(val captureMonotonicMs: Long) : Mapping
        data class Rejected(
            val reason: String,
            val mappedCaptureMonotonicMs: Long? = null
        ) : Mapping
    }

    private var sourceAnchorNanos: Long? = null
    private var localAnchorNanos: Long? = null
    private var lastSourceNanos: Long? = null

    init {
        require(maxCaptureAgeMs >= 0L)
        require(futureToleranceMs >= 0L)
    }

    fun map(sourceTimestampNanos: Long, observedLocalNanos: Long): Mapping {
        if (sourceTimestampNanos <= 0L || observedLocalNanos <= 0L) {
            return Mapping.Rejected("Camera timestamp is missing or invalid")
        }

        val previousSource = lastSourceNanos
        if (previousSource != null && sourceTimestampNanos <= previousSource) {
            return Mapping.Rejected("Camera timestamp is duplicate or out of order")
        }
        lastSourceNanos = sourceTimestampNanos

        val sourceAnchor = sourceAnchorNanos
        val localAnchor = localAnchorNanos
        val mappedNanos = if (sourceAnchor == null || localAnchor == null) {
            sourceAnchorNanos = sourceTimestampNanos
            localAnchorNanos = observedLocalNanos
            observedLocalNanos
        } else {
            val sourceDelta = sourceTimestampNanos - sourceAnchor
            try {
                Math.addExact(localAnchor, sourceDelta)
            } catch (_: ArithmeticException) {
                return Mapping.Rejected("Camera timestamp mapping overflowed")
            }
        }

        val futureToleranceNanos = futureToleranceMs * NANOS_PER_MILLISECOND
        if (mappedNanos > observedLocalNanos + futureToleranceNanos) {
            return Mapping.Rejected(
                reason = "Camera timestamp mapping moved into the future",
                mappedCaptureMonotonicMs = mappedNanos / NANOS_PER_MILLISECOND
            )
        }

        val ageNanos = observedLocalNanos - mappedNanos
        if (ageNanos > maxCaptureAgeMs * NANOS_PER_MILLISECOND) {
            return Mapping.Rejected(
                reason = "Camera frame is stale (${ageNanos / NANOS_PER_MILLISECOND} ms old)",
                mappedCaptureMonotonicMs = mappedNanos / NANOS_PER_MILLISECOND
            )
        }

        return Mapping.Usable(mappedNanos / NANOS_PER_MILLISECOND)
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
