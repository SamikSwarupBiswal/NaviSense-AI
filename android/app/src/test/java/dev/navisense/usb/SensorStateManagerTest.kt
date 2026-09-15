package dev.navisense.usb

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SensorStateManagerTest {

    private var simulatedTimeMs: Long = 10000L
    private lateinit var stateManager: SensorStateManager

    @Before
    fun setUp() {
        simulatedTimeMs = 10000L
        stateManager = SensorStateManager { simulatedTimeMs }
    }

    @Test
    fun testRecoveryRequiresThreeValidRecords() {
        stateManager.onConnected()
        assertEquals(SensorHealth.CONNECTING, stateManager.currentHealth)
        assertNull(stateManager.usableDistanceCm)

        // 1st record at t=10000
        stateManager.onRecordReceived(SensorRecord("...", 1, 100, 80, true, 10000L))
        assertEquals(SensorHealth.RECOVERING, stateManager.currentHealth)
        assertNull(stateManager.usableDistanceCm)

        // 2nd record at t=10100
        stateManager.onRecordReceived(SensorRecord("...", 2, 200, 80, true, 10100L))
        assertEquals(SensorHealth.RECOVERING, stateManager.currentHealth)
        assertNull(stateManager.usableDistanceCm)

        // 3rd record at t=10200 (span = 200 ms >= 150 ms)
        stateManager.onRecordReceived(SensorRecord("...", 3, 300, 80, true, 10200L))
        assertEquals(SensorHealth.HEALTHY, stateManager.currentHealth)
        assertEquals(80, stateManager.usableDistanceCm)
    }

    @Test
    fun testImmediateStopCandidateUnderFiftyCmDuringRecovery() {
        stateManager.onConnected()

        // 1st record with 30 cm (< 50 cm)
        stateManager.onRecordReceived(SensorRecord("...", 1, 100, 30, true, 10000L))
        assertEquals(SensorHealth.RECOVERING, stateManager.currentHealth)
        assertTrue(stateManager.isImmediateStopCandidate)
        assertNull(stateManager.usableDistanceCm) // Full recovery not established yet
    }

    @Test
    fun testFreshnessStalenessTimeout() {
        stateManager.onConnected()
        // Send 3 records to achieve HEALTHY
        stateManager.onRecordReceived(SensorRecord("...", 1, 100, 80, true, 10000L))
        stateManager.onRecordReceived(SensorRecord("...", 2, 200, 80, true, 10100L))
        stateManager.onRecordReceived(SensorRecord("...", 3, 300, 80, true, 10200L))
        assertEquals(SensorHealth.HEALTHY, stateManager.currentHealth)
        assertEquals(80, stateManager.usableDistanceCm)

        // Advance simulated time past 300 ms freshness limit (t = 10200 + 350 = 10550)
        simulatedTimeMs = 10550L
        val snapshot = stateManager.evaluateHealth()

        assertEquals(SensorHealth.STALE, snapshot.health)
        assertNull(snapshot.usableDistanceCm)
    }

    @Test
    fun testNewerInvalidRecordImmediatelyClearsDistance() {
        stateManager.onConnected()
        stateManager.onRecordReceived(SensorRecord("...", 1, 100, 75, true, 10000L))
        stateManager.onRecordReceived(SensorRecord("...", 2, 200, 75, true, 10100L))
        stateManager.onRecordReceived(SensorRecord("...", 3, 300, 75, true, 10200L))
        assertEquals(SensorHealth.HEALTHY, stateManager.currentHealth)

        // Send invalid measurement
        stateManager.onRecordReceived(SensorRecord("...", 4, 400, -1, false, 10300L))
        assertEquals(SensorHealth.DEGRADED_INVALID, stateManager.currentHealth)
        assertNull(stateManager.usableDistanceCm)
    }
}
