package dev.navisense

import dev.navisense.contracts.*
import dev.navisense.search.TargetSearchEngine
import org.junit.Assert.*
import org.junit.Test

class SearchRegressionTest {
    private val detection = DetectedObject(0, "keys", .9f, NormalizedRect(.1f,.2f,.3f,.4f))
    private fun frame(id: Long, detections: List<DetectedObject> = listOf(detection)) =
        MobilePerceptionEvent(1, AppVisionMode.LOCATE_SEARCH, id, 1000 + id * 100, 1000 + id * 100, 1, "locate", FrameQualityStatus.USABLE, detections)
    private fun engine() = TargetSearchEngine().apply { startSearch("keys", 1, 1000) }
    @Test fun repeatedFrameDoesNotConfirm() {
        val engine = engine()
        engine.processFrame(frame(1))
        repeat(5) { assertNull(engine.processFrame(frame(1))) }
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(frame(2))?.status)
        assertEquals(SearchStatus.CONFIRMED, engine.processFrame(frame(3))?.status)
    }
    @Test fun severalBoxesInOneFrameDoNotConfirm() {
        val engine = engine()
        engine.processFrame(frame(1, emptyList()))
        engine.processFrame(frame(2, emptyList()))
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(frame(3, List(3) { detection }))?.status)
    }
    @Test fun staleFutureModeAndModelAreRejected() {
        val engine = engine()
        engine.processFrame(frame(1))
        val candidate = frame(2)
        assertNull(engine.processFrame(candidate.copy(deliveryMonotonicMs = 1801)))
        assertNull(engine.processFrame(candidate.copy(captureMonotonicMs = 1201)))
        assertNull(engine.processFrame(candidate.copy(mode = AppVisionMode.MOBILITY)))
        assertNull(engine.processFrame(candidate.copy(modelIdentity = "other")))
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(candidate)?.status)
    }
    @Test fun geometryAndQualityInvalidateHistory() {
        val engine = engine()
        engine.processFrame(frame(1))
        engine.processFrame(frame(2))
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(frame(3).copy(geometryVersion = 2))?.status)
        engine.processFrame(frame(4).copy(geometryVersion = 2, qualityStatus = FrameQualityStatus.UNUSABLE))
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(frame(5).copy(geometryVersion = 2))?.status)
    }
    @Test fun searchCanContinueAfterSingleTimeout() {
        val engine = engine()
        assertEquals(SearchStatus.TIMEOUT, engine.processFrame(frame(150))?.status)
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(frame(151))?.status)
        engine.processFrame(frame(152))
        assertEquals(SearchStatus.CONFIRMED, engine.processFrame(frame(153))?.status)
    }
    @Test fun independentTargetsStaySeparate() {
        val engine = engine()
        val other = detection.copy(boundingBox = NormalizedRect(.7f,.2f,.9f,.4f))
        engine.processFrame(frame(1, listOf(detection, other)))
        engine.processFrame(frame(2, listOf(detection, other)))
        assertEquals(SearchStatus.MULTIPLE_CANDIDATES, engine.processFrame(frame(3, listOf(detection, other)))?.status)
    }

    @Test fun searchTargetNormalizationAndInvalidTarget() {
        assertEquals(SearchTarget.KEYS, SearchTarget.fromValue("keys"))
        assertEquals(SearchTarget.KEYS, SearchTarget.fromValue("Keys"))
        assertEquals(SearchTarget.KEYS, SearchTarget.fromValue("keychain"))
        assertEquals(SearchTarget.WALLET, SearchTarget.fromValue("wallet"))
        assertEquals(SearchTarget.WALLET, SearchTarget.fromValue("Purse"))
        assertNull(SearchTarget.fromValue("unknown_item"))

        val engine = TargetSearchEngine()
        engine.startSearch("keychain", 1L, 1000L)
        assertTrue(engine.isSearchActive)

        assertThrows(IllegalArgumentException::class.java) {
            engine.startSearch("chair", 2L, 1000L)
        }
    }

    @Test fun watchdogOnTickEmitsTimeoutWithoutFrames() {
        val engine = TargetSearchEngine(searchTimeoutMs = 15000L)
        engine.startSearch("keys", 1L, 1000L)

        // Before 15 seconds: null
        assertNull(engine.onTick(1000L + 5000L))
        assertNull(engine.onTick(1000L + 14999L))

        // At 15 seconds: TIMEOUT event emitted
        val event = engine.onTick(1000L + 15000L)
        assertNotNull(event)
        assertEquals(SearchStatus.TIMEOUT, event?.status)
        assertEquals("keys", event?.targetClass)

        // Subsequent onTick calls do not duplicate timeout
        assertNull(engine.onTick(1000L + 16000L))
    }

    @Test fun sessionCoordinatorNearbySearchLifecycle() {
        val fakeClock = FakeClock(1000L)
        val sessionGen = SessionGeneration(1L)
        val spokenPhrases = mutableListOf<String>()
        val mockSpeechArbiter = object : dev.navisense.voice.ISpeechArbiter {
            override fun speak(request: dev.navisense.voice.SpeechRequest): Boolean {
                spokenPhrases.add(request.phrase)
                return true
            }
            override fun cancelAll() {
                spokenPhrases.add("CANCEL_ALL")
            }
            override fun invalidateSession(newGeneration: Long) {}
        }

        val coordinator = dev.navisense.app.SessionCoordinator(
            sessionGeneration = sessionGen,
            clock = fakeClock,
            speechArbiter = mockSpeechArbiter
        )

        var observedSearchState: SearchUiState = SearchUiState.NONE
        coordinator.addListener(object : dev.navisense.app.SessionCoordinator.StateChangeListener {
            override fun onModeChanged(newMode: AppMode, token: SessionToken) {}
            override fun onPathStatusChanged(newStatus: PathStatus) {}
            override fun onSensorHealthChanged(newHealth: SensorHealth) {}
            override fun onSearchStateChanged(newState: SearchUiState, event: SearchEvent?) {
                observedSearchState = newState
            }
        })

        // 1. Start nearby search
        val token = coordinator.startNearbySearch("keys")
        assertEquals(AppMode.FINAL_SEARCH, coordinator.currentMode)
        assertEquals(SearchUiState.LOADING_MODEL, observedSearchState)
        assertEquals(SearchUiState.LOADING_MODEL, coordinator.currentSearchState)
        assertTrue(spokenPhrases.contains("Searching for keys"))

        // 2. Mark ready when model loaded
        assertTrue(coordinator.markNearbySearchReady(token.generation))
        assertEquals(SearchUiState.SEARCHING, observedSearchState)

        // 3. Confirm target found and give actionable camera direction.
        val confirmEvent = SearchEvent(
            sessionGeneration = token.generation,
            targetClass = "keys",
            status = SearchStatus.CONFIRMED,
            direction = TargetDirection.LEFT,
            candidateCount = 1,
            timestampMonotonicMs = 2000L
        )
        coordinator.onSearchEvent(confirmEvent)
        assertEquals(AppMode.FOUND, coordinator.currentMode)
        assertEquals(SearchUiState.FOUND, observedSearchState)
        assertTrue(spokenPhrases.contains("Keys detected on the left. Point the phone left."))

        // 4. Retry search
        coordinator.retryNearbySearch()
        assertEquals(AppMode.FINAL_SEARCH, coordinator.currentMode)
        assertEquals(SearchUiState.LOADING_MODEL, coordinator.currentSearchState)

        // 5. Fail search
        coordinator.failNearbySearch("Model error")
        assertEquals(AppMode.IDLE, coordinator.currentMode)
        assertEquals(SearchUiState.ERROR, observedSearchState)
        assertTrue(spokenPhrases.contains("Nearby search is unavailable."))
    }

    @Test fun confirmedSearchSpeaksEveryDirectionWithoutInventingCenter() {
        fun phraseFor(target: String, direction: TargetDirection?): String {
            val fakeClock = FakeClock(1000L)
            val phrases = mutableListOf<String>()
            val speech = object : dev.navisense.voice.ISpeechArbiter {
                override fun speak(request: dev.navisense.voice.SpeechRequest): Boolean {
                    phrases.add(request.phrase)
                    return true
                }
                override fun cancelAll() = Unit
                override fun invalidateSession(newGeneration: Long) = Unit
            }
            val coordinator = dev.navisense.app.SessionCoordinator(
                sessionGeneration = SessionGeneration(1L),
                clock = fakeClock,
                speechArbiter = speech
            )
            val token = coordinator.startNearbySearch(target)
            coordinator.markNearbySearchReady(token.generation)
            coordinator.onSearchEvent(
                SearchEvent(
                    sessionGeneration = token.generation,
                    targetClass = target,
                    status = SearchStatus.CONFIRMED,
                    direction = direction,
                    candidateCount = 1,
                    timestampMonotonicMs = 2000L
                )
            )
            return phrases.last()
        }

        assertEquals("Wallet detected on the left. Point the phone left.", phraseFor("wallet", TargetDirection.LEFT))
        assertEquals("Wallet detected straight ahead in the camera view.", phraseFor("wallet", TargetDirection.CENTER))
        assertEquals("Wallet detected on the right. Point the phone right.", phraseFor("wallet", TargetDirection.RIGHT))
        assertEquals("Wallet detected. Direction unavailable.", phraseFor("wallet", null))

        assertEquals("Keys detected on the left. Point the phone left.", phraseFor("keys", TargetDirection.LEFT))
        assertEquals("Keys detected straight ahead in the camera view.", phraseFor("keys", TargetDirection.CENTER))
        assertEquals("Keys detected on the right. Point the phone right.", phraseFor("keys", TargetDirection.RIGHT))
        assertEquals("Keys detected. Direction unavailable.", phraseFor("keys", null))
    }

    @Test
    fun testSearchNearbyVisibleInDistanceDoesNotConfirm() {
        val engine = TargetSearchEngine(reachBoxHeightThreshold = 0.12f)
        engine.startSearch("keys", 1L, 1000L)

        // Small bounding box far away: height = 0.05 < 0.12
        val farBox = NormalizedRect(0.45f, 0.45f, 0.55f, 0.50f)
        val farKeys = DetectedObject(0, "keys", 0.90f, farBox)
        fun farFrame(id: Long) = MobilePerceptionEvent(
            1L, AppVisionMode.LOCATE_SEARCH, id, 1000L + id * 100L, 1000L + id * 100L, 1, "locate",
            FrameQualityStatus.USABLE, listOf(farKeys)
        )

        engine.processFrame(farFrame(1))
        engine.processFrame(farFrame(2))
        val event = engine.processFrame(farFrame(3))

        assertNotNull(event)
        assertEquals("Should remain SEARCHING while far away", SearchStatus.SEARCHING, event?.status)
        assertEquals("Should identify 1 candidate", 1, event?.candidateCount)
        assertFalse("isCloseEnough must be false", event?.isCloseEnough == true)
        assertTrue("Search engine must remain active for user to walk closer", engine.isSearchActive)
    }

    @Test
    fun testSearchNearbyObstacleInPathAnnounced() {
        val engine = TargetSearchEngine(reachBoxHeightThreshold = 0.12f)
        engine.startSearch("keys", 1L, 1000L)

        // Target keys in distance: center x = 0.50, y from 0.30 to 0.36 (height = 0.06)
        val targetBox = NormalizedRect(0.47f, 0.30f, 0.53f, 0.36f)
        val keysDet = DetectedObject(0, "keys", 0.90f, targetBox)

        // Chair in between: horizontally spans 0.40 to 0.60 (corridor center 0.50), bottom = 0.70 (closer than keys bottom 0.36)
        val chairBox = NormalizedRect(0.40f, 0.45f, 0.60f, 0.70f)
        val chairDet = DetectedObject(2, "chair", 0.85f, chairBox)

        fun frameWithObstacle(id: Long) = MobilePerceptionEvent(
            1L, AppVisionMode.LOCATE_SEARCH, id, 1000L + id * 100L, 1000L + id * 100L, 1, "locate",
            FrameQualityStatus.USABLE, listOf(keysDet, chairDet)
        )

        engine.processFrame(frameWithObstacle(1))
        engine.processFrame(frameWithObstacle(2))
        val event = engine.processFrame(frameWithObstacle(3))

        assertNotNull(event)
        assertEquals(SearchStatus.SEARCHING, event?.status)
        assertEquals(1, event?.candidateCount)
        assertEquals("Chair", event?.obstacleInPath)
        assertFalse(event?.isCloseEnough == true)

        // Test SessionCoordinator speech for this event
        val fakeClock = FakeClock(2000L)
        val spoken = mutableListOf<String>()
        val speech = object : dev.navisense.voice.ISpeechArbiter {
            override fun speak(request: dev.navisense.voice.SpeechRequest): Boolean {
                spoken.add(request.phrase)
                return true
            }
            override fun cancelAll() = Unit
            override fun invalidateSession(newGeneration: Long) = Unit
        }
        val coordinator = dev.navisense.app.SessionCoordinator(
            sessionGeneration = SessionGeneration(1L),
            clock = fakeClock,
            speechArbiter = speech
        )
        val token = coordinator.startNearbySearch("keys")
        coordinator.markNearbySearchReady(token.generation)
        coordinator.onSearchEvent(event!!.copy(sessionGeneration = token.generation))

        assertTrue("Should warn about obstacle in between", spoken.contains("Keys detected ahead, but a Chair is in between."))
    }

    @Test
    fun testSearchNearbyTargetReachedWhenClose() {
        val engine = TargetSearchEngine(reachBoxHeightThreshold = 0.12f)
        engine.startSearch("keys", 1L, 1000L)

        // Large bounding box when close: height = 0.18 >= 0.12
        val closeBox = NormalizedRect(0.40f, 0.40f, 0.60f, 0.58f)
        val closeKeys = DetectedObject(0, "keys", 0.90f, closeBox)
        fun closeFrame(id: Long) = MobilePerceptionEvent(
            1L, AppVisionMode.LOCATE_SEARCH, id, 1000L + id * 100L, 1000L + id * 100L, 1, "locate",
            FrameQualityStatus.USABLE, listOf(closeKeys)
        )

        engine.processFrame(closeFrame(1))
        engine.processFrame(closeFrame(2))
        val event = engine.processFrame(closeFrame(3))

        assertNotNull(event)
        assertEquals(SearchStatus.CONFIRMED, event?.status)
        assertTrue("isCloseEnough must be true", event?.isCloseEnough == true)
        assertFalse("Search session finishes upon confirmation", engine.isSearchActive)

        // Test SessionCoordinator speech confirms reached
        val fakeClock = FakeClock(2000L)
        val spoken = mutableListOf<String>()
        val speech = object : dev.navisense.voice.ISpeechArbiter {
            override fun speak(request: dev.navisense.voice.SpeechRequest): Boolean {
                spoken.add(request.phrase)
                return true
            }
            override fun cancelAll() = Unit
            override fun invalidateSession(newGeneration: Long) = Unit
        }
        val coordinator = dev.navisense.app.SessionCoordinator(
            sessionGeneration = SessionGeneration(1L),
            clock = fakeClock,
            speechArbiter = speech
        )
        val token = coordinator.startNearbySearch("keys")
        coordinator.markNearbySearchReady(token.generation)
        coordinator.onSearchEvent(event!!.copy(sessionGeneration = token.generation))

        assertEquals(AppMode.FOUND, coordinator.currentMode)
        assertTrue("Should announce reached target", spoken.contains("Keys reached straight ahead."))
    }
}

