package dev.navisense.voice

import dev.navisense.contracts.IClock
import dev.navisense.contracts.SystemMonotonicClock

/**
 * Pluggable Text-to-Speech playback abstraction for decoupling and deterministic unit testing.
 */
interface ITextToSpeechPlayer {
    fun speak(text: String, utteranceId: String): Boolean
    fun stop(): Boolean
    fun isSpeaking(): Boolean
}

/**
 * Concrete implementation of ISpeechArbiter adhering to PRD Section 18.
 *
 * Responsibilities:
 * 1. Strict priority hierarchy: STOP (1) > SLOW (2) > DIRECTIONAL (3) > AWARENESS (4) > HEALTH_UNKNOWN (5) > INFORMATIONAL (6).
 * 2. Immediate preemption: Higher priority interrupts active speech and cancels playback.
 * 3. Priority and repetition cooldowns: Prevents continuous repetitive nagging unless an escalation occurs.
 * 4. Cancellation & session invalidation: cancelAll() and invalidateSession() immediately silence speech <= 250 ms.
 */
class SpeechArbiter(
    private val ttsPlayer: ITextToSpeechPlayer,
    private val clock: IClock = SystemMonotonicClock()
) : ISpeechArbiter {

    companion object {
        const val STOP_COOLDOWN_MS = 2000L
        const val SLOW_COOLDOWN_MS = 3000L
        const val DIRECTIONAL_COOLDOWN_MS = 2000L
        const val AWARENESS_COOLDOWN_MS = 5000L
        const val HEALTH_UNKNOWN_COOLDOWN_MS = 5000L
        const val INFORMATIONAL_COOLDOWN_MS = 3000L
    }

    private var currentGeneration: Long = 0L
    private var currentSpeakingPriority: AlertPriority? = null
    private val lastSpokenTimestamps = mutableMapOf<String, Long>()

    override fun speak(request: SpeechRequest): Boolean {
        // 1. Session generation gating: stale callbacks are discarded immediately
        if (request.sessionGeneration < currentGeneration) {
            return false
        }

        val now = clock.nowMonotonicMs()

        // 2. Cooldown check per alert priority
        val cooldownMs = when (request.priority) {
            AlertPriority.STOP -> STOP_COOLDOWN_MS
            AlertPriority.SLOW -> SLOW_COOLDOWN_MS
            AlertPriority.DIRECTIONAL -> DIRECTIONAL_COOLDOWN_MS
            AlertPriority.AWARENESS -> AWARENESS_COOLDOWN_MS
            AlertPriority.HEALTH_UNKNOWN -> HEALTH_UNKNOWN_COOLDOWN_MS
            AlertPriority.INFORMATIONAL -> INFORMATIONAL_COOLDOWN_MS
        }

        val lastTime = lastSpokenTimestamps[request.phrase]
        val inCooldown = if (lastTime != null) (now - lastTime) < cooldownMs else false

        // Escalation bypasses cooldown for STOP and SLOW
        if (inCooldown && !request.isEscalation) {
            return false
        }

        // 3. Preemption check: If TTS is currently speaking, can we interrupt?
        if (ttsPlayer.isSpeaking()) {
            val activePriority = currentSpeakingPriority
            if (activePriority != null) {
                // Only preempt if the new request has STRICTLY higher priority (lower numerical level)
                if (request.priority.priorityLevel >= activePriority.priorityLevel) {
                    return false
                }
                // Preempt immediately
                ttsPlayer.stop()
            }
        }

        // 4. Playback execution
        val success = ttsPlayer.speak(request.phrase, request.utteranceId)
        if (success) {
            currentSpeakingPriority = request.priority
            lastSpokenTimestamps[request.phrase] = now
        }
        return success
    }

    override fun cancelAll() {
        ttsPlayer.stop()
        currentSpeakingPriority = null
    }

    override fun invalidateSession(newGeneration: Long) {
        currentGeneration = newGeneration
        cancelAll()
        lastSpokenTimestamps.clear()
    }
}
