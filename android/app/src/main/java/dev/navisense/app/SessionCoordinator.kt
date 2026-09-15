package dev.navisense.app

import dev.navisense.contracts.AppMode
import dev.navisense.contracts.IClock
import dev.navisense.contracts.MobilePerceptionEvent
import dev.navisense.contracts.PathStatus
import dev.navisense.contracts.SearchEvent
import dev.navisense.contracts.SearchStatus
import dev.navisense.contracts.SensorEvent
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SessionGeneration
import dev.navisense.contracts.SessionToken
import dev.navisense.navigation.IRiskEngine
import dev.navisense.navigation.RiskEngine
import dev.navisense.navigation.RiskEvaluationResult
import dev.navisense.navigation.RiskLevel
import dev.navisense.voice.AlertPriority
import dev.navisense.voice.ISpeechArbiter
import dev.navisense.voice.SpeechRequest

/**
 * Central state authority for NaviSense AI adhering to PRD Section 13.5 & 14.
 * Owned and maintained by Rishav.
 */
class SessionCoordinator(
    val sessionGeneration: SessionGeneration,
    val clock: IClock,
    val riskEngine: IRiskEngine = RiskEngine(clock = clock),
    val speechArbiter: ISpeechArbiter? = null
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
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.speak(
            SpeechRequest(
                utteranceId = "start_mobility_$newGen",
                phrase = "Starting mobility",
                priority = AlertPriority.INFORMATIONAL,
                sessionGeneration = newGen,
                requestMonotonicMs = clock.nowMonotonicMs()
            )
        )
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
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.speak(
            SpeechRequest(
                utteranceId = "start_search_$newGen",
                phrase = "Searching for $targetClass",
                priority = AlertPriority.INFORMATIONAL,
                sessionGeneration = newGen,
                requestMonotonicMs = clock.nowMonotonicMs()
            )
        )
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
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.speak(
            SpeechRequest(
                utteranceId = "arrival_$newGen",
                phrase = "Arrived at zone. Searching for $target",
                priority = AlertPriority.INFORMATIONAL,
                sessionGeneration = newGen,
                requestMonotonicMs = clock.nowMonotonicMs()
            )
        )
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
     * - Immediately cancels audio playback (<= 250 ms).
     */
    @Synchronized
    fun userStop(): SessionToken {
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.IDLE
        activeTargetClass = null
        currentPathStatus = PathStatus.UNKNOWN
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.cancelAll()
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
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.cancelAll()
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        return token
    }

    @Synchronized
    fun onSensorEvent(event: SensorEvent) {
        if (currentMode == AppMode.IDLE || currentMode == AppMode.PAUSED) return
        val result = riskEngine.onSensorEvent(event)
        updateSensorHealth(event.sensorHealth)
        handleRiskEvaluation(result)
    }

    @Synchronized
    fun onPerceptionEvent(event: MobilePerceptionEvent) {
        if (!sessionGeneration.isValid(event.sessionGeneration)) return
        if (currentMode != AppMode.MOBILITY && currentMode != AppMode.FINAL_SEARCH) return
        val result = riskEngine.onPerceptionEvent(event)
        handleRiskEvaluation(result)
    }

    @Synchronized
    fun onSearchEvent(event: SearchEvent) {
        if (!sessionGeneration.isValid(event.sessionGeneration)) return
        if (currentMode != AppMode.FINAL_SEARCH) return

        if (event.status == SearchStatus.CONFIRMED) {
            onTargetFound()
            val dirStr = event.direction?.name?.lowercase() ?: "center"
            val phrase = "Target found $dirStr"
            speechArbiter?.speak(
                SpeechRequest(
                    utteranceId = "found_${clock.nowMonotonicMs()}",
                    phrase = phrase,
                    priority = AlertPriority.DIRECTIONAL,
                    sessionGeneration = sessionGeneration.get(),
                    requestMonotonicMs = clock.nowMonotonicMs()
                )
            )
        } else if (event.status == SearchStatus.TIMEOUT) {
            userStop()
            speechArbiter?.speak(
                SpeechRequest(
                    utteranceId = "timeout_${clock.nowMonotonicMs()}",
                    phrase = "Search timed out",
                    priority = AlertPriority.INFORMATIONAL,
                    sessionGeneration = sessionGeneration.get(),
                    requestMonotonicMs = clock.nowMonotonicMs()
                )
            )
        }
    }

    @Synchronized
    fun onWatchdogTick(currentTimeMonotonicMs: Long) {
        if (currentMode == AppMode.MOBILITY) {
            val result = riskEngine.onWatchdogTick(currentTimeMonotonicMs)
            handleRiskEvaluation(result)
        }
    }

    private fun handleRiskEvaluation(result: RiskEvaluationResult) {
        updatePathStatus(result.pathStatus)
        if (currentMode == AppMode.MOBILITY) {
            val now = clock.nowMonotonicMs()
            when (result.combinedRisk) {
                RiskLevel.STOP -> {
                    speechArbiter?.speak(
                        SpeechRequest(
                            utteranceId = "stop_$now",
                            phrase = "Stop",
                            priority = AlertPriority.STOP,
                            sessionGeneration = sessionGeneration.get(),
                            requestMonotonicMs = now,
                            isEscalation = result.isEscalation
                        )
                    )
                }
                RiskLevel.SLOW -> {
                    val phrase = if (result.associatedObjectLabel != null) {
                        "Caution, ${result.associatedObjectLabel} ahead"
                    } else {
                        "Caution, slow down"
                    }
                    speechArbiter?.speak(
                        SpeechRequest(
                            utteranceId = "slow_$now",
                            phrase = phrase,
                            priority = AlertPriority.SLOW,
                            sessionGeneration = sessionGeneration.get(),
                            requestMonotonicMs = now,
                            isEscalation = result.isEscalation
                        )
                    )
                }
                RiskLevel.AWARENESS -> {
                    speechArbiter?.speak(
                        SpeechRequest(
                            utteranceId = "aware_$now",
                            phrase = "Obstacle detected ahead",
                            priority = AlertPriority.AWARENESS,
                            sessionGeneration = sessionGeneration.get(),
                            requestMonotonicMs = now,
                            isEscalation = result.isEscalation
                        )
                    )
                }
                RiskLevel.NONE -> {}
            }
        }
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
