package dev.navisense.app

import dev.navisense.contracts.AppMode
import dev.navisense.contracts.IClock
import dev.navisense.contracts.PathStatus
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SessionGeneration
import dev.navisense.contracts.SessionToken

/**
 * Central state authority for NaviSense AI adhering to PRD Section 13.5 & 14.
 * Owned and maintained by Rishav.
 */
class SessionCoordinator(
    val sessionGeneration: SessionGeneration,
    val clock: IClock
) {
    @Volatile
    var currentMode: AppMode = AppMode.IDLE
        private set

    @Volatile
    var currentPathStatus: PathStatus = PathStatus.UNKNOWN
        private set

    @Volatile
    var currentSensorHealth: SensorHealth = SensorHealth.DETACHED
        private set

    @Volatile
    var activeTargetClass: String? = null
        private set

    private val stateListeners = mutableListOf<StateChangeListener>()

    interface StateChangeListener {
        fun onModeChanged(newMode: AppMode, token: SessionToken)
        fun onPathStatusChanged(newStatus: PathStatus)
        fun onSensorHealthChanged(newHealth: SensorHealth)
    }

    @Synchronized
    fun addListener(listener: StateChangeListener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener)
        }
    }

    @Synchronized
    fun removeListener(listener: StateChangeListener) {
        stateListeners.remove(listener)
    }

    /**
     * Starts standalone indoor walking navigation (PRD Section 13.2).
     * Does NOT require laptop, target, or network.
     */
    @Synchronized
    fun startMobility(): SessionToken {
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.MOBILITY
        activeTargetClass = null
        currentPathStatus = PathStatus.UNKNOWN
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        return token
    }

    /**
     * Starts direct nearby target search (PRD Section 20).
     */
    @Synchronized
    fun startNearbySearch(targetClass: String): SessionToken {
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.FINAL_SEARCH
        activeTargetClass = targetClass
        currentPathStatus = PathStatus.UNKNOWN
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        return token
    }

    /**
     * Confirms arrival at stationary target zone and switches to FinalSearch.
     */
    @Synchronized
    fun confirmArrivalAtZone(): SessionToken? {
        val target = activeTargetClass ?: return null
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.FINAL_SEARCH
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        return token
    }

    /**
     * Target detected during stationary search; switches to FOUND.
     */
    @Synchronized
    fun onTargetFound(): SessionToken? {
        if (currentMode != AppMode.FINAL_SEARCH) return null
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.FOUND
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        return token
    }

    /**
     * Immediate cancellation adhering to PRD Section 13.5:
     * - Advances generation to invalidate all active callbacks immediately.
     * - Resets mode to IDLE.
     * - Clears target.
     * - Resets path status to UNKNOWN.
     */
    @Synchronized
    fun userStop(): SessionToken {
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.IDLE
        activeTargetClass = null
        currentPathStatus = PathStatus.UNKNOWN
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        return token
    }

    /**
     * Transitions system to PAUSED on fatal subsystem error (camera stall, TTS failure, etc.).
     */
    @Synchronized
    fun fatalPause(): SessionToken {
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.PAUSED
        currentPathStatus = PathStatus.UNKNOWN
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        return token
    }

    @Synchronized
    fun updatePathStatus(newStatus: PathStatus) {
        if (currentPathStatus != newStatus) {
            currentPathStatus = newStatus
            stateListeners.forEach { it.onPathStatusChanged(newStatus) }
        }
    }

    @Synchronized
    fun updateSensorHealth(newHealth: SensorHealth) {
        if (currentSensorHealth != newHealth) {
            currentSensorHealth = newHealth
            stateListeners.forEach { it.onSensorHealthChanged(newHealth) }
        }
    }

    private fun notifyModeChanged(token: SessionToken) {
        stateListeners.forEach { it.onModeChanged(token.mode, token) }
    }
}
