package dev.navisense

import dev.navisense.contracts.IClock

/**
 * Deterministic controllable clock for replay and unit tests (AC-10).
 */
class FakeClock(private var currentMs: Long = 1000L) : IClock {
    override fun nowMonotonicMs(): Long = currentMs
    override fun nowMonotonicNanos(): Long = currentMs * 1_000_000L

    fun advanceBy(deltaMs: Long) {
        require(deltaMs >= 0) { "Clock cannot move backwards" }
        currentMs += deltaMs
    }

    fun set(newMs: Long) {
        require(newMs >= currentMs) { "Clock cannot move backwards" }
        currentMs = newMs
    }
}
