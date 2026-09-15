package dev.navisense.usb

import java.util.concurrent.atomic.AtomicLong

/**
 * Manages sensor connection state, freshness, recovery, and watchdog health evaluation.
 * Authority: PRD v3.2 Section 14.2 (Freshness and Reconnection)
 */
class SensorStateManager(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TWO_POW_32 = 1L shl 32
        private const val HALF_POW_32 = 1L shl 31

        const val FRESHNESS_LIMIT_MS = 300L
        const val RECOVERY_MIN_SPAN_MS = 150L
        const val RECOVERY_MAX_GAP_MS = 300L
        const val MAX_DELAY_DRIFT_MS = 200L
        const val IMMEDIATE_STOP_THRESHOLD_CM = 100
    }

    private val sessionGeneration = AtomicLong(0)

    @Volatile
    var currentHealth: SensorHealth = SensorHealth.DISCONNECTED
        private set

    @Volatile
    var usableDistanceCm: Int? = null
        private set

    @Volatile
    var isImmediateStopCandidate: Boolean = false
        private set

    @Volatile
    var lastRecord: SensorRecord? = null
        private set

    // Recovery tracking
    private val recoveryRecords = mutableListOf<SensorRecord>()
    private var recoveryBaselineReceiptMs: Long? = null
    private var recoveryBaselineUptimeMs: Long? = null

    // Sequence / Uptime tracking
    private var prevSequence: Long? = null
    private var prevUptimeMs: Long? = null

    /**
     * Called when a USB device is attached and permission is granted.
     */
    fun onConnected() {
        sessionGeneration.incrementAndGet()
        currentHealth = SensorHealth.CONNECTING
        usableDistanceCm = null
        isImmediateStopCandidate = false
        lastRecord = null
        resetRecovery()
        prevSequence = null
        prevUptimeMs = null
    }

    /**
     * Called when the USB device is detached or permission lost.
     */
    fun onDisconnected() {
        currentHealth = SensorHealth.DISCONNECTED
        usableDistanceCm = null
        isImmediateStopCandidate = false
        lastRecord = null
        resetRecovery()
    }

    private fun resetRecovery() {
        recoveryRecords.clear()
        recoveryBaselineReceiptMs = null
        recoveryBaselineUptimeMs = null
    }

    /**
     * Ingests a validated SensorRecord from the parser.
     * @param record The parsed sensor record
     */
    @Synchronized
    fun onRecordReceived(record: SensorRecord) {
        val now = record.receiptMonotonicMs

        // Check Sequence & Uptime progression (PRD 14.1)
        val prevSeq = prevSequence
        val prevUp = prevUptimeMs

        if (prevSeq != null && prevUp != null) {
            val seqDelta = (record.sequence - prevSeq).mod(TWO_POW_32)
            val upDelta = (record.uptimeMs - prevUp).mod(TWO_POW_32)

            if (seqDelta == 0L) {
                // Duplicate record: ignore without refreshing time
                return
            }

            if (seqDelta >= HALF_POW_32 || upDelta >= HALF_POW_32) {
                // Backwards jump: reopen stream as new session
                onConnected()
                return
            }
        }

        prevSequence = record.sequence
        prevUptimeMs = record.uptimeMs
        lastRecord = record

        // Immediate STOP candidate evaluation (PRD 14.2)
        // A fresh valid reading <= 50 cm triggers STOP candidate immediately
        isImmediateStopCandidate = record.isValid && (record.distanceCm in 2..IMMEDIATE_STOP_THRESHOLD_CM)

        // Invalid record handling: newer invalid record immediately clears distance
        if (!record.isValid) {
            usableDistanceCm = null
            resetRecovery()
            currentHealth = SensorHealth.DEGRADED_INVALID
            return
        }

        // Recovery handling: require 3 consecutive valid records spanning >= 150 ms
        if (currentHealth != SensorHealth.HEALTHY) {
            if (recoveryRecords.isNotEmpty()) {
                val gap = now - recoveryRecords.last().receiptMonotonicMs
                if (gap > RECOVERY_MAX_GAP_MS) {
                    resetRecovery()
                }
            }

            recoveryRecords.add(record)
            currentHealth = SensorHealth.RECOVERING

            if (recoveryRecords.size >= 3) {
                val span = now - recoveryRecords.first().receiptMonotonicMs
                if (span >= RECOVERY_MIN_SPAN_MS) {
                    // Successfully recovered!
                    currentHealth = SensorHealth.HEALTHY
                    usableDistanceCm = record.distanceCm
                    recoveryBaselineReceiptMs = now
                    recoveryBaselineUptimeMs = record.uptimeMs
                    return
                }
            }

            // Still recovering: distance not yet usable (unless STOP candidate)
            usableDistanceCm = null
            return
        }

        // Delay Progression Tracking (PRD 14.2)
        val baselineReceipt = recoveryBaselineReceiptMs
        val baselineUptime = recoveryBaselineUptimeMs
        if (baselineReceipt != null && baselineUptime != null) {
            val elapsedReceipt = now - baselineReceipt
            val elapsedDevice = (record.uptimeMs - baselineUptime).mod(TWO_POW_32)

            if (elapsedReceipt > elapsedDevice + MAX_DELAY_DRIFT_MS) {
                // Increasing delay detected: invalidate stream
                usableDistanceCm = null
                resetRecovery()
                currentHealth = SensorHealth.DEGRADED_INVALID
                return
            }
        }

        // Normal healthy update
        usableDistanceCm = record.distanceCm
    }

    /**
     * Watchdog evaluation function called periodically (e.g. every 50 ms) by Rishav's Risk Engine.
     * Evaluates staleness even when no new bytes are received.
     */
    @Synchronized
    fun evaluateHealth(): SensorState {
        val now = clock()
        val rec = lastRecord

        if (currentHealth != SensorHealth.DISCONNECTED && rec != null) {
            val age = now - rec.receiptMonotonicMs
            if (age > FRESHNESS_LIMIT_MS) {
                usableDistanceCm = null
                isImmediateStopCandidate = false
                if (currentHealth == SensorHealth.HEALTHY) {
                    currentHealth = SensorHealth.STALE
                }
            }
        }

        return SensorState(
            health = currentHealth,
            usableDistanceCm = usableDistanceCm,
            immediateStopCandidate = isImmediateStopCandidate,
            lastRecord = rec,
            sessionGeneration = sessionGeneration.get()
        )
    }
}
