package dev.navisense

import dev.navisense.voice.AlertPriority
import dev.navisense.voice.ITextToSpeechPlayer
import dev.navisense.voice.SpeechArbiter
import dev.navisense.voice.SpeechRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeTextToSpeechPlayer : ITextToSpeechPlayer {
    var currentlySpeaking = false
    var lastSpokenText: String? = null
    var stopCount = 0

    override fun speak(text: String, utteranceId: String): Boolean {
        currentlySpeaking = true
        lastSpokenText = text
        return true
    }

    override fun stop(): Boolean {
        currentlySpeaking = false
        stopCount++
        return true
    }

    override fun isSpeaking(): Boolean = currentlySpeaking
}

class SpeechArbiterTest {

    private lateinit var clock: FakeClock
    private lateinit var fakePlayer: FakeTextToSpeechPlayer
    private lateinit var arbiter: SpeechArbiter

    @Before
    fun setUp() {
        clock = FakeClock(1000L)
        fakePlayer = FakeTextToSpeechPlayer()
        arbiter = SpeechArbiter(ttsPlayer = fakePlayer, clock = clock)
    }

    @Test
    fun testPreemptionHigherPriorityInterruptsLower() {
        // 1. Speak Informational (level 6)
        val infoReq = SpeechRequest(
            utteranceId = "1",
            phrase = "Starting mobility",
            priority = AlertPriority.INFORMATIONAL,
            sessionGeneration = 1L,
            requestMonotonicMs = 1000L
        )
        val r1 = arbiter.speak(infoReq)
        assertTrue("Informational request accepted", r1)
        assertTrue("Player is speaking", fakePlayer.isSpeaking())

        // 2. Emergency STOP arrives while Informational is speaking -> must preempt
        val stopReq = SpeechRequest(
            utteranceId = "2",
            phrase = "Stop",
            priority = AlertPriority.STOP,
            sessionGeneration = 1L,
            requestMonotonicMs = 1050L
        )
        val r2 = arbiter.speak(stopReq)
        assertTrue("STOP preempts Informational", r2)
        assertEquals("Stop", fakePlayer.lastSpokenText)
        assertTrue("Player stopped prior utterance and started STOP", fakePlayer.stopCount >= 1)
    }

    @Test
    fun testLowerPriorityCannotPreemptHigherPriority() {
        // 1. Start speaking STOP
        val stopReq = SpeechRequest("1", "Stop", AlertPriority.STOP, 1L, 1000L)
        arbiter.speak(stopReq)
        assertTrue(fakePlayer.isSpeaking())

        // 2. SLOW arrives while STOP is speaking -> must NOT preempt
        val slowReq = SpeechRequest("2", "Caution", AlertPriority.SLOW, 1L, 1050L)
        val rSlow = arbiter.speak(slowReq)
        assertFalse("SLOW cannot preempt active STOP", rSlow)
        assertEquals("Stop", fakePlayer.lastSpokenText)
    }

    @Test
    fun testCooldownSuppressesRepetition() {
        // Awareness cooldown is 5000 ms
        val req1 = SpeechRequest("1", "Obstacle ahead", AlertPriority.AWARENESS, 1L, 1000L)
        assertTrue(arbiter.speak(req1))
        fakePlayer.currentlySpeaking = false // utterance finished

        // 2 seconds later (1000 + 2000 = 3000 ms)
        clock.set(3000L)
        val req2 = SpeechRequest("2", "Obstacle ahead", AlertPriority.AWARENESS, 1L, 3000L)
        assertFalse("Within cooldown window, repetition must be suppressed", arbiter.speak(req2))

        // After 5001 ms (1000 + 5001 = 6001 ms)
        clock.set(6001L)
        val req3 = SpeechRequest("3", "Obstacle ahead", AlertPriority.AWARENESS, 1L, 6001L)
        assertTrue("After cooldown window, utterance must be accepted", arbiter.speak(req3))
    }

    @Test
    fun testEscalationBypassesCooldown() {
        // SLOW spoken at 1000 ms
        val req1 = SpeechRequest("1", "Slow down", AlertPriority.SLOW, 1L, 1000L)
        assertTrue(arbiter.speak(req1))
        fakePlayer.currentlySpeaking = false

        // Escalation arrives at 1500 ms (within 3000 ms cooldown)
        clock.set(1500L)
        val reqEscalated = SpeechRequest("2", "Slow down", AlertPriority.SLOW, 1L, 1500L, isEscalation = true)
        assertTrue("Escalation must bypass cooldown", arbiter.speak(reqEscalated))
    }

    @Test
    fun testSessionInvalidationRejectsOlderGeneration() {
        arbiter.invalidateSession(2L)

        val oldGenReq = SpeechRequest("1", "Old generation", AlertPriority.INFORMATIONAL, 1L, 1000L)
        assertFalse("Request from old session generation must be rejected", arbiter.speak(oldGenReq))

        val newGenReq = SpeechRequest("2", "New generation", AlertPriority.INFORMATIONAL, 2L, 1000L)
        assertTrue("Request matching active session generation must be accepted", arbiter.speak(newGenReq))
    }

    @Test
    fun testCancelAllSilencesPlayback() {
        val req = SpeechRequest("1", "Speaking", AlertPriority.INFORMATIONAL, 1L, 1000L)
        arbiter.speak(req)
        assertTrue(fakePlayer.isSpeaking())

        arbiter.cancelAll()
        assertFalse("cancelAll must immediately silence playback", fakePlayer.isSpeaking())
    }

    @Test
    fun testTypedRefinementPreemptsGenericPlaceholderAtSamePriority() {
        val genericReq = SpeechRequest(
            utteranceId = "slow_gen",
            phrase = "Slow down. Obstacle ahead.",
            priority = AlertPriority.SLOW,
            sessionGeneration = 1L,
            requestMonotonicMs = 1000L,
            hazardEpisodeId = "ep_1",
            isRefinement = false
        )
        assertTrue("Generic warning accepted", arbiter.speak(genericReq))
        assertTrue(fakePlayer.isSpeaking())
        assertEquals("Slow down. Obstacle ahead.", fakePlayer.lastSpokenText)

        clock.set(1100L)
        val refinementReq = SpeechRequest(
            utteranceId = "slow_refined",
            phrase = "Slow down. Chair ahead.",
            priority = AlertPriority.SLOW,
            sessionGeneration = 1L,
            requestMonotonicMs = 1100L,
            hazardEpisodeId = "ep_1",
            isRefinement = true,
            refinementLabel = "Chair",
            expiresAtMonotonicMs = 1600L
        )
        assertTrue("Refinement preempts generic placeholder", arbiter.speak(refinementReq))
        assertEquals("Slow down. Chair ahead.", fakePlayer.lastSpokenText)
    }

    @Test
    fun testTypedRefinementOnlyDeliveredOncePerEpisode() {
        val genericReq = SpeechRequest(
            utteranceId = "slow_gen",
            phrase = "Slow down. Obstacle ahead.",
            priority = AlertPriority.SLOW,
            sessionGeneration = 1L,
            requestMonotonicMs = 1000L,
            hazardEpisodeId = "ep_1",
            isRefinement = false
        )
        arbiter.speak(genericReq)

        clock.set(1100L)
        val ref1 = SpeechRequest(
            utteranceId = "ref_1",
            phrase = "Slow down. Chair ahead.",
            priority = AlertPriority.SLOW,
            sessionGeneration = 1L,
            requestMonotonicMs = 1100L,
            hazardEpisodeId = "ep_1",
            isRefinement = true,
            refinementLabel = "Chair"
        )
        assertTrue("First refinement accepted", arbiter.speak(ref1))
        fakePlayer.currentlySpeaking = false

        // Attempt second refinement for same episode within cooldown window
        clock.set(1500L)
        val ref2 = SpeechRequest(
            utteranceId = "ref_2",
            phrase = "Slow down. Table ahead.",
            priority = AlertPriority.SLOW,
            sessionGeneration = 1L,
            requestMonotonicMs = 1500L,
            hazardEpisodeId = "ep_1",
            isRefinement = true,
            refinementLabel = "Table"
        )
        assertFalse("Second refinement for same episode must be suppressed", arbiter.speak(ref2))
    }

    @Test
    fun testExpiredRefinementIsDiscarded() {
        clock.set(1500L)
        val expiredReq = SpeechRequest(
            utteranceId = "expired_1",
            phrase = "Slow down. Chair ahead.",
            priority = AlertPriority.SLOW,
            sessionGeneration = 1L,
            requestMonotonicMs = 1000L,
            hazardEpisodeId = "ep_1",
            isRefinement = true,
            expiresAtMonotonicMs = 1400L // Already past
        )
        assertFalse("Expired refinement request must be discarded", arbiter.speak(expiredReq))
    }
}
