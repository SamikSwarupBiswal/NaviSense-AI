package dev.navisense.app

import android.app.Application
import dev.navisense.contracts.IClock
import dev.navisense.contracts.SessionGeneration
import dev.navisense.contracts.SystemMonotonicClock

/**
 * Base Application class for NaviSense AI.
 * Provides singleton lifecycle instances of monotonic clock and session generation authority.
 */
class NaviSenseApp : Application() {

    lateinit var clock: IClock
        private set

    lateinit var sessionGeneration: SessionGeneration
        private set

    lateinit var sessionCoordinator: SessionCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        clock = SystemMonotonicClock()
        sessionGeneration = SessionGeneration()
        sessionCoordinator = SessionCoordinator(sessionGeneration, clock)
    }
}
