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
        const val SLOW_COOLDOWN_MS = 5000L
        const val DIRECTIONAL_COOLDOWN_MS = 2000L
        const val AWARENESS_COOLDOWN_MS = 5000L
        const val HEALTH_UNKNOWN_COOLDOWN_MS = 10000L
        const val INFORMATIONAL_COOLDOWN_MS = 3000L
    }

    private var currentGeneration: Long = 0L
    private var currentSpeakingPriority: AlertPriority? = null
    private var currentSpeakingPhrase: String? = null
    private var currentSpeakingEpisodeId: String? = null
    private var currentSpeakingIsGeneric: Boolean = false
    private val deliveredRefinements = mutableSetOf<String>()
    private val lastSpokenTimestamps = mutableMapOf<String, Long>()

    override fun speak(request: SpeechRequest): Boolean {
        // 1. Session generation gating: stale callbacks are discarded immediately
        if (request.sessionGeneration < currentGeneration) {
            return false
        }

        val now = clock.nowMonotonicMs()

        // Expiration check: If request has an explicit expiry, discard if stale
        if (request.expiresAtMonotonicMs != null && now > request.expiresAtMonotonicMs) {
            return false
        }

        // Refinement uniqueness: Each hazard episode allows at most ONE semantic refinement
        if (request.isRefinement && request.hazardEpisodeId != null && deliveredRefinements.contains(request.hazardEpisodeId)) {
            return false
        }

        // 2. Cooldown check per alert priority
        val cooldownMs = when (request.priority) {
            AlertPriority.STOP -> STOP_COOLDOWN_MS
            AlertPriority.SLOW -> SLOW_COOLDOWN_MS
            AlertPriority.DIRECTIONAL -> DIRECTIONAL_COOLDOWN_MS
            AlertPriority.AWARENESS -> AWARENESS_COOLDOWN_MS
            AlertPriority.HEALTH_UNKNOWN -> HEALTH_UNKNOWN_COOLDOWN_MS
            AlertPriority.INFORMATIONAL -> INFORMATIONAL_COOLDOWN_MS
        }

        val isEligibleRefinement = request.isRefinement &&
            request.hazardEpisodeId != null &&
            !deliveredRefinements.contains(request.hazardEpisodeId)

        val lastTime = lastSpokenTimestamps[request.phrase]
        val inCooldown = if (lastTime != null) (now - lastTime) < cooldownMs else false

        // Escalation bypasses cooldown for STOP and SLOW; a fresh refinement for the hazard episode also bypasses cooldown
        if (inCooldown && !request.isEscalation && !isEligibleRefinement) {
            return false
        }

        // 3. Preemption check: If TTS is currently speaking, can we interrupt?
        if (ttsPlayer.isSpeaking()) {
            val activePriority = currentSpeakingPriority
            val activePhrase = currentSpeakingPhrase
            if (activePriority != null) {
                // Typed refinement preemption at same priority level
                val isTypedRefinement = request.priority == activePriority &&
                    request.hazardEpisodeId != null &&
                    request.hazardEpisodeId == currentSpeakingEpisodeId &&
                    currentSpeakingIsGeneric &&
                    isEligibleRefinement

                // Fallback for untyped legacy requests
                val isGenericActive = activePhrase == "STOP." || activePhrase?.contains("Obstacle") == true
                val isSpecificNew = !request.phrase.contains("Obstacle") && request.phrase != "STOP."
                val isLegacyRefinement = request.priority == activePriority && isGenericActive && isSpecificNew

                val isSameLevelRefinement = isTypedRefinement || (request.hazardEpisodeId == null && isLegacyRefinement)

                if (!isSameLevelRefinement && request.priority.priorityLevel >= activePriority.priorityLevel) {
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
            currentSpeakingPhrase = request.phrase
            currentSpeakingEpisodeId = request.hazardEpisodeId
            currentSpeakingIsGeneric = !request.isRefinement
            if (isEligibleRefinement) {
                deliveredRefinements.add(request.hazardEpisodeId!!)
            }
            lastSpokenTimestamps[request.phrase] = now
        }
        return success
    }

    override fun cancelAll() {
        ttsPlayer.stop()
        currentSpeakingPriority = null
        currentSpeakingPhrase = null
        currentSpeakingEpisodeId = null
        currentSpeakingIsGeneric = false
    }

    override fun invalidateSession(newGeneration: Long) {
        currentGeneration = newGeneration
        cancelAll()
        lastSpokenTimestamps.clear()
        deliveredRefinements.clear()
    }

    override val isSpeaking: Boolean
        get() = ttsPlayer.isSpeaking()
}
