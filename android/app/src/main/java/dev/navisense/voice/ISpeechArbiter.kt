package dev.navisense.voice

/**
 * Central Speech Arbiter interface adhering to PRD Section 18.
 *
 * All spoken guidance in the application passes through this arbiter.
 * Implements:
 * 1. Preemption: STOP (priority 1) interrupts and flushes lower-priority speech.
 * 2. Cooldowns: Awareness / Slow-down (5 seconds), persistent STOP (2 seconds).
 * 3. Session invalidation: Session Stop / pause flushes queued and active speech immediately.
 */
interface ISpeechArbiter {
    /**
     * Submits a speech request for consideration by the arbiter.
     * @return true if accepted for synthesis/playback, false if suppressed by cooldown/priority.
     */
    fun speak(request: SpeechRequest): Boolean

    /**
     * Immediately cancels and silences all current speech and flushes the queue.
     * Called on user Stop, pause, or session transition.
     */
    fun cancelAll()

    /**
     * Invalidates any speech belonging to an older session generation.
     */
    fun invalidateSession(newGeneration: Long)

    /**
     * Returns true if Text-to-Speech audio is currently playing.
     */
    val isSpeaking: Boolean
        get() = false
}
