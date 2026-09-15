package dev.navisense.contracts

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe atomic session generation coordinator adhering to PRD Section 13.5.
 *
 * User Stop, pause, and mode transitions invalidate old asynchronous work and speech callbacks.
 * Any callback tagged with a superseded generation number must be immediately discarded.
 */
class SessionGeneration(initialGeneration: Long = 1L) {
    private val counter = AtomicLong(initialGeneration)

    /** Gets current active generation counter. */
    fun get(): Long = counter.get()

    /**
     * Atomically advances the session generation.
     * All callbacks bearing prior generations become invalid immediately.
     */
    fun advance(): Long = counter.incrementAndGet()

    /**
     * Verifies if a given generation token is still current and valid.
     */
    fun isValid(generation: Long): Boolean = counter.get() == generation

    /**
     * Creates a lightweight immutable snapshot token for the current session and mode.
     */
    fun createToken(mode: AppMode): SessionToken = SessionToken(counter.get(), mode)
}

/**
 * Immutable token passed to asynchronous workers (camera, inference, USB, HTTP).
 */
data class SessionToken(
    val generation: Long,
    val mode: AppMode
)
