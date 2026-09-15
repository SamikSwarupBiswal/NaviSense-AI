package dev.navisense.app

import android.app.Application
import dev.navisense.contracts.IClock
import dev.navisense.contracts.SessionGeneration
import dev.navisense.contracts.SystemMonotonicClock

import dev.navisense.voice.AndroidTextToSpeechPlayer
import dev.navisense.voice.ISpeechArbiter
import dev.navisense.voice.SpeechArbiter

/**
 * Base Application class for NaviSense AI.
 * Provides singleton lifecycle instances of monotonic clock, session generation authority, and speech arbiter.
 */
class NaviSenseApp : Application() {

    lateinit var clock: IClock
        private set

    lateinit var sessionGeneration: SessionGeneration
        private set

    lateinit var speechArbiter: ISpeechArbiter
        private set

    lateinit var sessionCoordinator: SessionCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        clock = SystemMonotonicClock()
        sessionGeneration = SessionGeneration()
        val ttsPlayer = AndroidTextToSpeechPlayer(this)
        speechArbiter = SpeechArbiter(ttsPlayer = ttsPlayer, clock = clock)
        sessionCoordinator = SessionCoordinator(
            sessionGeneration = sessionGeneration,
            clock = clock,
            speechArbiter = speechArbiter
        )
    }
}
