package dev.navisense.contracts

/**
 * Monotonic clock abstraction for NaviSense AI.
 * Enables deterministic testing of timeouts, holds, and cooldowns without real-time sleeps.
 */
interface IClock {
    /** Monotonic time in milliseconds (equivalent to Android SystemClock.elapsedRealtime()). */
    fun nowMonotonicMs(): Long

    /** Monotonic time in nanoseconds for high-precision latency profiling. */
    fun nowMonotonicNanos(): Long
}
