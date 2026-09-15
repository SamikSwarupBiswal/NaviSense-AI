package dev.navisense.usb

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UsbSensorAdapterTest {

    @Test
    fun testStreamProcessingAndRecovery() {
        val streamData = (
            "V=1,SEQ=100,UP_MS=1000,DIST_CM=80,VALID=1\n" +
            "V=1,SEQ=101,UP_MS=1100,DIST_CM=78,VALID=1\n" +
            "V=1,SEQ=102,UP_MS=1200,DIST_CM=75,VALID=1\n"
        ).toByteArray(StandardCharsets.US_ASCII)

        var time = 1000L
        val receivedRecords = mutableListOf<SensorRecord>()
        val healthChanges = mutableListOf<Pair<SensorHealth, SensorHealth>>()
        val latch = CountDownLatch(3)

        val transport = StreamUsbTransport(ByteArrayInputStream(streamData))
        val adapter = UsbSensorAdapter(
            transport = transport,
            clock = {
                val t = time
                time += 100L // Advance 100 ms each read
                t
            },
            onRecordReceived = {
                receivedRecords.add(it)
                latch.countDown()
            },
            onHealthChanged = { old, new ->
                healthChanges.add(old to new)
            }
        )

        adapter.start()
        assertTrue(latch.await(2, TimeUnit.SECONDS))

        // Wait a tiny moment for thread to exit stream
        Thread.sleep(50)

        assertEquals(3, receivedRecords.size)
        assertEquals(100L, receivedRecords[0].sequence)
        assertEquals(101L, receivedRecords[1].sequence)
        assertEquals(102L, receivedRecords[2].sequence)
        assertEquals(0L, adapter.totalLostPackets.get())

        adapter.stop()
    }

    @Test
    fun testSequenceLossDiagnostics() {
        // Gap between 200 and 205 (4 lost packets: 201, 202, 203, 204)
        val streamData = (
            "V=1,SEQ=200,UP_MS=2000,DIST_CM=60,VALID=1\n" +
            "V=1,SEQ=205,UP_MS=2500,DIST_CM=55,VALID=1\n"
        ).toByteArray(StandardCharsets.US_ASCII)

        val latch = CountDownLatch(2)
        val transport = StreamUsbTransport(ByteArrayInputStream(streamData))
        val adapter = UsbSensorAdapter(
            transport = transport,
            clock = { 5000L },
            onRecordReceived = { latch.countDown() }
        )

        adapter.start()
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        Thread.sleep(50)

        assertEquals(4L, adapter.totalLostPackets.get())
        adapter.stop()
    }

    @Test
    fun testImmediateStopCandidateCallback() {
        val streamData = (
            "V=1,SEQ=300,UP_MS=3000,DIST_CM=40,VALID=1\n"
        ).toByteArray(StandardCharsets.US_ASCII)

        val stopLatch = CountDownLatch(1)
        var stopRecord: SensorRecord? = null

        val transport = StreamUsbTransport(ByteArrayInputStream(streamData))
        val adapter = UsbSensorAdapter(
            transport = transport,
            clock = { 10000L },
            onImmediateStopCandidate = {
                stopRecord = it
                stopLatch.countDown()
            }
        )

        adapter.start()
        assertTrue(stopLatch.await(2, TimeUnit.SECONDS))

        assertNotNull(stopRecord)
        assertEquals(40, stopRecord?.distanceCm)
        assertTrue(stopRecord?.isImmediateStopCandidate == true)

        adapter.stop()
    }
}
