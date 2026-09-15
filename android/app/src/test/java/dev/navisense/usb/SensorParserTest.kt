package dev.navisense.usb

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

class SensorParserTest {

    private lateinit var parser: SensorParser

    @Before
    fun setUp() {
        parser = SensorParser()
    }

    @Test
    fun testValidRecordParsing() {
        val line = "V=1,SEQ=1001,UP_MS=100100,DIST_CM=83,VALID=1\n"
        var parsedRecord: SensorRecord? = null

        parser.feedBytes(line.toByteArray(StandardCharsets.US_ASCII), line.length, 5000L, {
            parsedRecord = it
        })

        assertNotNull(parsedRecord)
        assertEquals(1001L, parsedRecord?.sequence)
        assertEquals(100100L, parsedRecord?.uptimeMs)
        assertEquals(83, parsedRecord?.distanceCm)
        assertTrue(parsedRecord?.isValid == true)
        assertEquals(5000L, parsedRecord?.receiptMonotonicMs)
    }

    @Test
    fun testInvalidMeasurementRecord() {
        val line = "V=1,SEQ=1002,UP_MS=100200,DIST_CM=-1,VALID=0\r\n"
        var parsedRecord: SensorRecord? = null

        parser.feedBytes(line.toByteArray(StandardCharsets.US_ASCII), line.length, 5100L, {
            parsedRecord = it
        })

        assertNotNull(parsedRecord)
        assertEquals(1002L, parsedRecord?.sequence)
        assertEquals(-1, parsedRecord?.distanceCm)
        assertFalse(parsedRecord?.isValid == true)
    }

    @Test
    fun testOversizeLineDiscardedThroughNewline() {
        // Line exceeding 128 bytes
        val oversize = "V=1,SEQ=1003," + "A".repeat(150) + "\n"
        val nextValid = "V=1,SEQ=1004,UP_MS=100400,DIST_CM=50,VALID=1\n"

        val records = mutableListOf<SensorRecord>()
        val errors = mutableListOf<String>()

        parser.feedBytes(oversize.toByteArray(StandardCharsets.US_ASCII), oversize.length, 5200L, {
            records.add(it)
        }, { errors.add(it) })

        assertTrue(errors.any { it.contains("Oversize line exceeded") })
        assertEquals(0, records.size)

        // Ensure parser recovers on next valid line
        parser.feedBytes(nextValid.toByteArray(StandardCharsets.US_ASCII), nextValid.length, 5300L, {
            records.add(it)
        })

        assertEquals(1, records.size)
        assertEquals(1004L, records[0].sequence)
    }

    @Test
    fun testInconsistentRecordRejected() {
        // VALID=1 but DIST_CM is -1
        val badLine = "V=1,SEQ=1005,UP_MS=100500,DIST_CM=-1,VALID=1\n"
        var parsedRecord: SensorRecord? = null

        parser.feedBytes(badLine.toByteArray(StandardCharsets.US_ASCII), badLine.length, 5400L, {
            parsedRecord = it
        })

        assertNull(parsedRecord)
        assertEquals(1, parser.malformedRecords)
    }
}
