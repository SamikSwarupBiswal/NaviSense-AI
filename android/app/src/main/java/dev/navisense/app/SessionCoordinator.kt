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
import dev.navisense.networking.LocateResult
import dev.navisense.networking.MemoryClientContract
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
    val speechArbiter: ISpeechArbiter? = null,
    val memoryClient: MemoryClientContract? = null
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

    @Volatile
    var activeTargetZone: String? = null
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
        activeTargetZone = null
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
        activeTargetZone = null
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
     * Starts guided target navigation toward a stationary memory candidate (PRD Section 13.3 & 13.4).
     * Begins walking assistance toward target zone with announcement:
     * "Last seen at {zone}. Obstacle assistance started."
     */
    @Synchronized
    fun startTargetGuidance(targetClass: String, zoneName: String): SessionToken {
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.MOBILITY
        activeTargetClass = targetClass
        activeTargetZone = zoneName
        currentPathStatus = PathStatus.UNKNOWN
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.speak(
            SpeechRequest(
                utteranceId = "target_guidance_$newGen",
                phrase = "Last seen at $zoneName. Obstacle assistance started.",
                priority = AlertPriority.DIRECTIONAL,
                sessionGeneration = newGen,
                requestMonotonicMs = clock.nowMonotonicMs()
            )
        )
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        return token
    }

    /**
     * Queries the stationary laptop memory service for an object location and triggers guidance if found.
     * Adheres to PRD Section 13.3 (refresh before Guide, session generation invalidation, and failure announcements).
     */
    suspend fun locateAndGuide(queryName: String): LocateResult {
        val client = memoryClient
            ?: return LocateResult.NetworkError(queryName, null, "Memory client not initialized")

        val currentGen = sessionGeneration.get()
        val result = client.locateObject(queryName, currentGen)

        // Session generation check: ignore stale callback if session changed during network call
        if (!sessionGeneration.isValid(currentGen)) {
            return LocateResult.NetworkError(queryName, null, "Session generation $currentGen superseded during lookup")
        }

        when (result) {
            is LocateResult.Found -> {
                val zoneLabel = result.candidate.zoneName ?: result.candidate.zoneId
                startTargetGuidance(
                    targetClass = result.canonicalName,
                    zoneName = zoneLabel
                )
            }
            is LocateResult.Ambiguous -> {
                speechArbiter?.speak(
                    SpeechRequest(
                        utteranceId = "ambiguous_${clock.nowMonotonicMs()}",
                        phrase = "Multiple candidates found for ${result.queryName}",
                        priority = AlertPriority.INFORMATIONAL,
                        sessionGeneration = sessionGeneration.get(),
                        requestMonotonicMs = clock.nowMonotonicMs()
                    )
                )
            }
            is LocateResult.Stale -> {
                val zoneLabel = result.candidate.zoneName ?: result.candidate.zoneId
                speechArbiter?.speak(
                    SpeechRequest(
                        utteranceId = "stale_${clock.nowMonotonicMs()}",
                        phrase = "Last seen ${result.ageSeconds.toInt()} seconds ago at $zoneLabel",
                        priority = AlertPriority.INFORMATIONAL,
                        sessionGeneration = sessionGeneration.get(),
                        requestMonotonicMs = clock.nowMonotonicMs()
                    )
                )
            }
            is LocateResult.HistoricalOnly -> {
                speechArbiter?.speak(
                    SpeechRequest(
                        utteranceId = "historical_${clock.nowMonotonicMs()}",
                        phrase = "Object was seen in previous scan. Location unconfirmed.",
                        priority = AlertPriority.INFORMATIONAL,
                        sessionGeneration = sessionGeneration.get(),
                        requestMonotonicMs = clock.nowMonotonicMs()
                    )
                )
            }
            is LocateResult.NotFound -> {
                speechArbiter?.speak(
                    SpeechRequest(
                        utteranceId = "not_found_${clock.nowMonotonicMs()}",
                        phrase = "${result.queryName} not found",
                        priority = AlertPriority.INFORMATIONAL,
                        sessionGeneration = sessionGeneration.get(),
                        requestMonotonicMs = clock.nowMonotonicMs()
                    )
                )
            }
            is LocateResult.Unsupported -> {
                speechArbiter?.speak(
                    SpeechRequest(
                        utteranceId = "unsupported_${clock.nowMonotonicMs()}",
                        phrase = "${result.queryName} is unsupported",
                        priority = AlertPriority.INFORMATIONAL,
                        sessionGeneration = sessionGeneration.get(),
                        requestMonotonicMs = clock.nowMonotonicMs()
                    )
                )
            }
            is LocateResult.NetworkError -> {
                speechArbiter?.speak(
                    SpeechRequest(
                        utteranceId = "net_err_${clock.nowMonotonicMs()}",
                        phrase = "Memory lookup failed",
                        priority = AlertPriority.INFORMATIONAL,
                        sessionGeneration = sessionGeneration.get(),
                        requestMonotonicMs = clock.nowMonotonicMs()
                    )
                )
            }
        }
        return result
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
     * - Cancels pending memory client requests.
     */
    @Synchronized
    fun userStop(): SessionToken {
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.IDLE
        activeTargetClass = null
        activeTargetZone = null
        currentPathStatus = PathStatus.UNKNOWN
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.cancelAll()
        memoryClient?.cancelPending()
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
        activeTargetClass = null
        activeTargetZone = null
        currentPathStatus = PathStatus.UNKNOWN
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.cancelAll()
        memoryClient?.cancelPending()
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
