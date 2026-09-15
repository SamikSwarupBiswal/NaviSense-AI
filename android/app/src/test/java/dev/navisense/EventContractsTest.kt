package dev.navisense

import dev.navisense.contracts.MemoryCandidate
import dev.navisense.contracts.NormalizedBoundingBox
import dev.navisense.contracts.SensorEvent
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SensorWireRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventContractsTest {

    @Test
    fun testSensorWireRecordValid() {
        val record = SensorWireRecord(
            version = 1,
            sequenceNumber = 1021L,
            deviceUptimeMs = 102100L,
            distanceCm = 83,
            isValid = true
        )
        assertEquals(1, record.version)
        assertEquals(1021L, record.sequenceNumber)
        assertEquals(83, record.distanceCm)
        assertTrue(record.isValid)
    }

    @Test
    fun testCriticalCloseDetection() {
        val closeRecord = SensorWireRecord(1, 100L, 5000L, 45, true)
        val event = SensorEvent(
            connectionId = 1L,
            receiptMonotonicMs = 1000L,
            wireRecord = closeRecord,
            sensorHealth = SensorHealth.STREAMING
        )
        assertTrue("Distance <= 50 cm must be recognized as critical close", event.isCriticalClose)

        val safeRecord = SensorWireRecord(1, 101L, 5100L, 80, true)
        val safeEvent = SensorEvent(
            connectionId = 1L,
            receiptMonotonicMs = 1100L,
            wireRecord = safeRecord,
            sensorHealth = SensorHealth.STREAMING
        )
        assertFalse("Distance > 50 cm is not critical close STOP", safeEvent.isCriticalClose)
    }

    @Test
    fun testMemoryCandidateStaleness() {
        val freshCandidate = MemoryCandidate(
            observationId = 1L,
            cameraId = "laptop_cam",
            cameraProfileVersion = 1,
            area = "hall",
            zone = "table",
            relativeX = 0.5f,
            relativeY = 0.5f,
            confidence = 0.9f,
            lastSeenIso = "2026-09-14T10:00:00.000Z",
            ageSeconds = 30,
            freshness = "recent"
        )
        assertFalse(freshCandidate.isStale)

        val staleCandidate = MemoryCandidate(
            observationId = 2L,
            cameraId = "laptop_cam",
            cameraProfileVersion = 1,
            area = "hall",
            zone = "table",
            relativeX = 0.5f,
            relativeY = 0.5f,
            confidence = 0.9f,
            lastSeenIso = "2026-09-14T09:00:00.000Z",
            ageSeconds = 65,
            freshness = "stale"
        )
        assertTrue(staleCandidate.isStale)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidBoundingBoxThrows() {
        // NaN coordinates must throw IllegalArgumentException per NormalizedRect invariant
        NormalizedBoundingBox(left = Float.NaN, top = 0.2f, right = 0.8f, bottom = 0.5f)
    }
}
