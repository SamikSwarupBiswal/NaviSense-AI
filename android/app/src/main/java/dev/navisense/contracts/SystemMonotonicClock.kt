package dev.navisense.contracts

/**
 * Standard JVM/Android implementation of [IClock] using system monotonic time.
 */
class SystemMonotonicClock : IClock {
    override fun nowMonotonicMs(): Long {
        return System.nanoTime() / 1_000_000L
    }

    override fun nowMonotonicNanos(): Long {
        return System.nanoTime()
    }
}
