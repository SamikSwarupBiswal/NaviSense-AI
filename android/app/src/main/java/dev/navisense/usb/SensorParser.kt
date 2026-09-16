package dev.navisense.usb

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Strict PRD Section 14.1 ASCII line parser.
 *
 * Requirements:
 * - Maximum line buffer: 128 bytes (excluding terminator).
 * - Oversized lines discarded through next newline.
 * - Accepts LF or CRLF.
 * - Format: V=1,SEQ=<seq>,UP_MS=<uptime_ms>,DIST_CM=<dist>,VALID=<0|1>
 * - Rejects unknown versions, invalid ranges, and inconsistent valid/dist pairings.
 */
class SensorParser {

    companion object {
        const val MAX_LINE_BYTES = 128
        private const val TWO_POW_32 = 1L shl 32
        private val RECORD_PATTERN = Pattern.compile("^V=1,SEQ=(\\d+),UP_MS=(\\d+),DIST_CM=(-?\\d+),VALID=([01])$")
    }

    private val lineBuffer = ByteArray(MAX_LINE_BYTES + 1)
    private var bufferLength = 0
    private var isDiscardingOversize = false

    // Diagnostic counters
    var totalLines: Long = 0
        private set
    var acceptedRecords: Long = 0
        private set
    var malformedRecords: Long = 0
        private set
    var oversizeDiscards: Long = 0
        private set

    /**
     * Feeds raw incoming bytes from the USB serial stream.
     * @param buffer Byte array containing incoming serial data
     * @param length Number of valid bytes in the buffer
     * @param receiptTimeMs Android monotonic timestamp (SystemClock.elapsedRealtime())
     * @param onRecordParsed Callback invoked for each successfully parsed SensorRecord
     * @param onError Callback invoked when a parsing error occurs
     */
    fun feedBytes(
        buffer: ByteArray,
        length: Int,
        receiptTimeMs: Long,
        onRecordParsed: (SensorRecord) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        for (i in 0 until length) {
            val b = buffer[i]

            if (isDiscardingOversize) {
                if (b == '\n'.code.toByte()) {
                    isDiscardingOversize = false
                    bufferLength = 0
                }
                continue
            }

            if (b == '\n'.code.toByte()) {
                totalLines++
                // Handle CRLF by trimming trailing \r
                var lineLen = bufferLength
                if (lineLen > 0 && lineBuffer[lineLen - 1] == '\r'.code.toByte()) {
                    lineLen--
                }

                if (lineLen > 0) {
                    val lineStr = String(lineBuffer, 0, lineLen, StandardCharsets.US_ASCII)
                    parseLine(lineStr, receiptTimeMs, onRecordParsed, onError)
                }
                bufferLength = 0
            } else {
                if (bufferLength < MAX_LINE_BYTES) {
                    lineBuffer[bufferLength++] = b
                } else {
                    // Line exceeded 128 bytes: discard through next newline
                    isDiscardingOversize = true
                    oversizeDiscards++
                    totalLines++
                    bufferLength = 0
                    onError?.invoke("Oversize line exceeded $MAX_LINE_BYTES bytes; discarded")
                }
            }
        }
    }

    private fun parseLine(
        line: String,
        receiptTimeMs: Long,
        onRecordParsed: (SensorRecord) -> Unit,
        onError: ((String) -> Unit)?
    ) {
        val matcher = RECORD_PATTERN.matcher(line)
        if (!matcher.matches()) {
            malformedRecords++
            onError?.invoke("Malformed record schema: '$line'")
            return
        }

        try {
            val seq = matcher.group(1)?.toLong() ?: throw NumberFormatException("Missing SEQ")
            val upMs = matcher.group(2)?.toLong() ?: throw NumberFormatException("Missing UP_MS")
            val distCm = matcher.group(3)?.toInt() ?: throw NumberFormatException("Missing DIST_CM")
            val validInt = matcher.group(4)?.toInt() ?: throw NumberFormatException("Missing VALID")
            val isValid = (validInt == 1)

            // Bounds check for unsigned 32-bit values
            if (seq < 0 || seq >= TWO_POW_32) {
                malformedRecords++
                onError?.invoke("Sequence out of 32-bit range: $seq")
                return
            }
            if (upMs < 0 || upMs >= TWO_POW_32) {
                malformedRecords++
                onError?.invoke("Uptime out of 32-bit range: $upMs")
                return
            }

            // Consistency checks (PRD Section 14.1)
            if (isValid) {
                if (distCm !in 2..400) {
                    malformedRecords++
                    onError?.invoke("Inconsistent record: VALID=1 but DIST_CM=$distCm not in 2..400")
                    return
                }
            } else {
                if (distCm != -1) {
                    malformedRecords++
                    onError?.invoke("Inconsistent record: VALID=0 but DIST_CM=$distCm != -1")
                    return
                }
            }

            acceptedRecords++
            val record = SensorRecord(line, seq, upMs, distCm, isValid, receiptTimeMs)
            onRecordParsed(record)

        } catch (e: Exception) {
            malformedRecords++
            onError?.invoke("Error parsing record numbers: ${e.message}")
        }
    }

    fun reset() {
        bufferLength = 0
        isDiscardingOversize = false
    }
}
