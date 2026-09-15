package dev.navisense.app

import dev.navisense.contracts.AppMode
import dev.navisense.contracts.IClock
import dev.navisense.contracts.MobilePerceptionEvent
import dev.navisense.contracts.PathStatus
import dev.navisense.contracts.SearchEvent
import dev.navisense.contracts.SearchStatus
import dev.navisense.contracts.SearchTarget
import dev.navisense.contracts.SearchUiState
import dev.navisense.contracts.SensorEvent
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SessionGeneration
import dev.navisense.contracts.SessionToken
import dev.navisense.navigation.IRiskEngine
import dev.navisense.navigation.RiskEngine
import dev.navisense.navigation.RiskEvaluationResult
import dev.navisense.navigation.RiskLevel
import dev.navisense.navigation.maps.PedestrianNavigationEngine
import dev.navisense.navigation.maps.models.GeoPoint
import dev.navisense.navigation.maps.models.NavigationEngineStatus
import dev.navisense.navigation.maps.models.NavigationGuidance
import dev.navisense.navigation.maps.models.WalkingRoute
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
    val memoryClient: MemoryClientContract? = null,
    val navigationEngine: PedestrianNavigationEngine = PedestrianNavigationEngine(clock = clock)
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

    @Volatile
    var currentSearchState: SearchUiState = SearchUiState.NONE
        private set

    private val stateListeners = mutableListOf<StateChangeListener>()
    private var lastSearchGuidanceMs: Long = 0L
    private var lastSearchGuidanceObstacle: String? = null

    interface StateChangeListener {
        fun onModeChanged(newMode: AppMode, token: SessionToken)
        fun onPathStatusChanged(newStatus: PathStatus)
        fun onSensorHealthChanged(newHealth: SensorHealth)
        fun onRiskEvaluated(result: RiskEvaluationResult) {}
        fun onSearchStateChanged(newState: SearchUiState, event: SearchEvent? = null) {}
        fun onNavigationStatusUpdated(status: NavigationEngineStatus) {}
    }

    init {
        navigationEngine.addListener(object : PedestrianNavigationEngine.NavigationListener {
            override fun onGuidanceGenerated(guidance: NavigationGuidance) {
                if (currentMode == AppMode.OUTDOOR_WALKING) {
                    val now = clock.nowMonotonicMs()
                    speechArbiter?.speak(
                        SpeechRequest(
                            utteranceId = "nav_$now",
                            phrase = guidance.phrase,
                            priority = guidance.priority,
                            sessionGeneration = sessionGeneration.get(),
                            requestMonotonicMs = now
                        )
                    )
                }
            }

            override fun onStatusUpdated(status: NavigationEngineStatus) {
                if (currentMode == AppMode.OUTDOOR_WALKING) {
                    stateListeners.forEach { it.onNavigationStatusUpdated(status) }
                }
            }

            override fun onOffRouteDetected() {
                // Handled via guidance announcement
            }

            override fun onArrival() {
                // Handled via guidance announcement
            }
        })
    }

    private fun notifySearchStateChanged(newState: SearchUiState, event: SearchEvent? = null) {
        currentSearchState = newState
        stateListeners.forEach { it.onSearchStateChanged(newState, event) }
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
        currentSearchState = SearchUiState.NONE
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
        notifySearchStateChanged(SearchUiState.NONE)
        return token
    }

    /**
     * Starts outdoor walking navigation using Google Maps walking routes and GPS/compass.
     */
    @Synchronized
    fun startOutdoorWalking(route: WalkingRoute): SessionToken {
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.OUTDOOR_WALKING
        currentSearchState = SearchUiState.NONE
        activeTargetClass = route.destinationName
        activeTargetZone = null
        currentPathStatus = PathStatus.UNKNOWN
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        navigationEngine.startRoute(route)
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        notifySearchStateChanged(SearchUiState.NONE)
        return token
    }

    /**
     * Feeds GPS coordinate update to the pedestrian navigation engine.
     */
    @Synchronized
    fun onLocationUpdated(currentLocation: GeoPoint, accuracyMeters: Float = 5f) {
        if (currentMode == AppMode.OUTDOOR_WALKING) {
            navigationEngine.onLocationUpdated(currentLocation, accuracyMeters)
        }
    }

    /**
     * Feeds compass azimuth heading update [0, 360) to the pedestrian navigation engine.
     */
    @Synchronized
    fun onHeadingUpdated(azimuthDegrees: Float) {
        if (currentMode == AppMode.OUTDOOR_WALKING) {
            navigationEngine.onHeadingUpdated(azimuthDegrees)
        }
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
        currentSearchState = SearchUiState.LOADING_MODEL
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
        notifySearchStateChanged(SearchUiState.LOADING_MODEL)
        return token
    }

    /**
     * Confirms that the local Locate model has been loaded and the camera analyzer is actively scanning.
     */
    @Synchronized
    fun markNearbySearchReady(expectedGeneration: Long): Boolean {
        if (!sessionGeneration.isValid(expectedGeneration) || currentMode != AppMode.FINAL_SEARCH) {
            return false
        }
        notifySearchStateChanged(SearchUiState.SEARCHING)
        return true
    }

    /**
     * Reports an unrecoverable failure during nearby search (e.g. model loading error or permission denial).
     */
    @Synchronized
    fun failNearbySearch(reason: String? = null): SessionToken {
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.IDLE
        activeTargetClass = null
        activeTargetZone = null
        currentPathStatus = PathStatus.UNKNOWN
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.cancelAll()
        memoryClient?.cancelPending()
        speechArbiter?.speak(
            SpeechRequest(
                utteranceId = "search_fail_$newGen",
                phrase = "Nearby search is unavailable.",
                priority = AlertPriority.INFORMATIONAL,
                sessionGeneration = newGen,
                requestMonotonicMs = clock.nowMonotonicMs()
            )
        )
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        notifySearchStateChanged(SearchUiState.ERROR)
        return token
    }

    /**
     * Retries nearby search for the active target class.
     */
    @Synchronized
    fun retryNearbySearch(): SessionToken? {
        val target = activeTargetClass ?: return null
        return startNearbySearch(target)
    }

    /**
     * Starts outdoor/campus pedestrian map navigation with turn-by-turn guidance.
     */
    @Synchronized
    fun startMapNavigation(destinationName: String): SessionToken {
        val newGen = sessionGeneration.advance()
        currentMode = AppMode.MAP_NAVIGATION
        activeTargetClass = destinationName
        activeTargetZone = destinationName
        currentPathStatus = PathStatus.UNKNOWN
        riskEngine.reset()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.speak(
            SpeechRequest(
                utteranceId = "start_map_nav_$newGen",
                phrase = "Starting map directions to $destinationName",
                priority = AlertPriority.INFORMATIONAL,
                sessionGeneration = newGen,
                requestMonotonicMs = clock.nowMonotonicMs()
            )
        )
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        notifySearchStateChanged(SearchUiState.NONE)
        return token
    }

    /**
     * Speaks Gemini 1.5 Flash walking obstacle description with directional priority.
     */
    @Synchronized
    fun onGeminiNarrationReceived(narration: String) {
        if (currentMode != AppMode.MOBILITY && currentMode != AppMode.MAP_NAVIGATION) return
        val currentGen = sessionGeneration.get()
        speechArbiter?.speak(
            SpeechRequest(
                utteranceId = "gemini_narr_${clock.nowMonotonicMs()}",
                phrase = narration,
                priority = AlertPriority.DIRECTIONAL,
                sessionGeneration = currentGen,
                requestMonotonicMs = clock.nowMonotonicMs()
            )
        )
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
        currentSearchState = SearchUiState.LOADING_MODEL
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
        notifySearchStateChanged(SearchUiState.LOADING_MODEL)
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
        currentSearchState = SearchUiState.NONE
        riskEngine.reset()
        navigationEngine.stopNavigation()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.cancelAll()
        memoryClient?.cancelPending()
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        notifySearchStateChanged(SearchUiState.NONE)
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
        currentSearchState = SearchUiState.NONE
        riskEngine.reset()
        navigationEngine.stopNavigation()
        speechArbiter?.invalidateSession(newGen)
        speechArbiter?.cancelAll()
        memoryClient?.cancelPending()
        val token = SessionToken(newGen, currentMode)
        notifyModeChanged(token)
        notifySearchStateChanged(SearchUiState.NONE)
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
        // Locate detections in FinalSearch belong exclusively to TargetSearchEngine.
        // They must never drive Mobility corridor or visual-risk rules.
        if (currentMode != AppMode.MOBILITY && currentMode != AppMode.OUTDOOR_WALKING && currentMode != AppMode.MAP_NAVIGATION) return
        val result = riskEngine.onPerceptionEvent(event)
        handleRiskEvaluation(result)
    }

    @Synchronized
    fun onSearchEvent(event: SearchEvent) {
        if (!sessionGeneration.isValid(event.sessionGeneration)) return
        if (currentMode != AppMode.FINAL_SEARCH) return

        when (event.status) {
            SearchStatus.CONFIRMED -> {
                notifySearchStateChanged(SearchUiState.FOUND, event)
                onTargetFound()
                val targetName = SearchTarget.fromValue(event.targetClass)?.displayName ?: "Target"
                val phrase = if (event.isCloseEnough) {
                    when (event.direction) {
                        dev.navisense.contracts.TargetDirection.LEFT ->
                            "$targetName reached on the left."
                        dev.navisense.contracts.TargetDirection.CENTER ->
                            "$targetName reached straight ahead."
                        dev.navisense.contracts.TargetDirection.RIGHT ->
                            "$targetName reached on the right."
                        null -> "$targetName reached."
                    }
                } else {
                    when (event.direction) {
                        dev.navisense.contracts.TargetDirection.LEFT ->
                            "$targetName detected on the left. Point the phone left."
                        dev.navisense.contracts.TargetDirection.CENTER ->
                            "$targetName detected straight ahead in the camera view."
                        dev.navisense.contracts.TargetDirection.RIGHT ->
                            "$targetName detected on the right. Point the phone right."
                        null -> "$targetName detected. Direction unavailable."
                    }
                }
                speechArbiter?.speak(
                    SpeechRequest(
                        utteranceId = "found_${clock.nowMonotonicMs()}",
                        phrase = phrase,
                        priority = AlertPriority.DIRECTIONAL,
                        sessionGeneration = sessionGeneration.get(),
                        requestMonotonicMs = clock.nowMonotonicMs()
                    )
                )
            }
            SearchStatus.TIMEOUT -> {
                notifySearchStateChanged(SearchUiState.TIMED_OUT, event)
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
            SearchStatus.MULTIPLE_CANDIDATES -> {
                notifySearchStateChanged(SearchUiState.MULTIPLE_CANDIDATES, event)
            }
            SearchStatus.SEARCHING -> {
                notifySearchStateChanged(SearchUiState.SEARCHING, event)
                if (event.candidateCount > 0) {
                    val now = clock.nowMonotonicMs()
                    val targetName = SearchTarget.fromValue(event.targetClass)?.displayName ?: "Target"
                    val phrase = if (event.obstacleInPath != null) {
                        "$targetName detected ahead, but a ${event.obstacleInPath} is in between."
                    } else {
                        when (event.direction) {
                            dev.navisense.contracts.TargetDirection.LEFT ->
                                "$targetName visible in distance on the left. Move closer."
                            dev.navisense.contracts.TargetDirection.RIGHT ->
                                "$targetName visible in distance on the right. Move closer."
                            else ->
                                "$targetName visible in distance straight ahead. Move closer."
                        }
                    }
                    if (now - lastSearchGuidanceMs >= 3500L || lastSearchGuidanceObstacle != event.obstacleInPath) {
                        lastSearchGuidanceMs = now
                        lastSearchGuidanceObstacle = event.obstacleInPath
                        speechArbiter?.speak(
                            SpeechRequest(
                                utteranceId = "search_guidance_$now",
                                phrase = phrase,
                                priority = AlertPriority.DIRECTIONAL,
                                sessionGeneration = sessionGeneration.get(),
                                requestMonotonicMs = now
                            )
                        )
                    }
                }
            }
            SearchStatus.ERROR -> {
                notifySearchStateChanged(SearchUiState.ERROR, event)
            }
        }
    }

    @Synchronized
    fun onWatchdogTick(currentTimeMonotonicMs: Long) {
        if (currentMode == AppMode.MOBILITY || currentMode == AppMode.OUTDOOR_WALKING) {
            val result = riskEngine.onWatchdogTick(currentTimeMonotonicMs)
            handleRiskEvaluation(result)
        }
    }

    private fun handleRiskEvaluation(result: RiskEvaluationResult) {
        updatePathStatus(result.pathStatus)
        val now = clock.nowMonotonicMs()
        if (currentMode == AppMode.MOBILITY || currentMode == AppMode.OUTDOOR_WALKING) {
            val obstacleLabel = result.associatedObjectLabel?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                ?: result.visualObstacleLabel
            when (result.combinedRisk) {
                RiskLevel.STOP -> {
                    val phrase = if (obstacleLabel != null) {
                        "STOP. $obstacleLabel ahead."
                    } else {
                        "STOP."
                    }
                    speechArbiter?.speak(
                        SpeechRequest(
                            utteranceId = "stop_$now",
                            phrase = phrase,
                            priority = AlertPriority.STOP,
                            sessionGeneration = sessionGeneration.get(),
                            requestMonotonicMs = now,
                            isEscalation = result.isEscalation
                        )
                    )
                }
                RiskLevel.SLOW -> {
                    val phrase = if (obstacleLabel != null) {
                        "Slow down. $obstacleLabel ahead."
                    } else {
                        "Slow down. Obstacle ahead."
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
                    val phrase = obstacleLabel?.let { "$it ahead." } ?: "Obstacle ahead."
                    speechArbiter?.speak(
                        SpeechRequest(
                            utteranceId = "aware_$now",
                            phrase = phrase,
                            priority = AlertPriority.AWARENESS,
                            sessionGeneration = sessionGeneration.get(),
                            requestMonotonicMs = now,
                            isEscalation = result.isEscalation
                        )
                    )
                }
                RiskLevel.NONE -> {}
            }
        } else if ((currentMode == AppMode.FINAL_SEARCH || currentMode == AppMode.FOUND) &&
            result.sensorRisk == RiskLevel.STOP
        ) {
            // Search is stationary and Locate detections never drive Mobility risk,
            // but a fresh close ultrasonic record must retain STOP priority.
            speechArbiter?.speak(
                SpeechRequest(
                    utteranceId = "search_stop_$now",
                    phrase = "STOP.",
                    priority = AlertPriority.STOP,
                    sessionGeneration = sessionGeneration.get(),
                    requestMonotonicMs = now,
                    isEscalation = result.isEscalation
                )
            )
        }
        notifyRiskEvaluated(result)
    }

    private fun notifyRiskEvaluated(result: RiskEvaluationResult) {
        stateListeners.forEach { it.onRiskEvaluated(result) }
    }

    @Synchronized
    fun updatePathStatus(newStatus: PathStatus) {
        if (currentPathStatus != newStatus) {
            currentPathStatus = newStatus
            stateListeners.forEach { it.onPathStatusChanged(newStatus) }
            if (currentMode == AppMode.MOBILITY || currentMode == AppMode.OUTDOOR_WALKING) {
                val now = clock.nowMonotonicMs()
                val phrase = when (newStatus) {
                    PathStatus.CLEAR_OBSERVED -> "No obstacle detected ahead."
                    PathStatus.UNKNOWN -> "Cannot confirm the path is clear."
                    PathStatus.BLOCKED -> null
                }
                if (phrase != null) {
                    speechArbiter?.speak(
                        SpeechRequest(
                            utteranceId = "path_${newStatus.name.lowercase()}_$now",
                            phrase = phrase,
                            priority = AlertPriority.HEALTH_UNKNOWN,
                            sessionGeneration = sessionGeneration.get(),
                            requestMonotonicMs = now
                        )
                    )
                }
            }
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
