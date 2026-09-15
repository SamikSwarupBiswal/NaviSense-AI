package dev.navisense.usb

import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Interface representing a generic bidirectional or read-only USB serial transport.
 * Allows decoupling Android UsbDeviceConnection from unit testing and fixture replays.
 */
interface UsbTransport {
    fun open(): Boolean
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
    fun close()
    val isOpen: Boolean
}

/**
 * Stream-based USB transport implementation for fixture replay, mock streams, and testing.
 */
class StreamUsbTransport(private val inputStream: InputStream) : UsbTransport {
    private val openFlag = AtomicBoolean(true)

    override fun open(): Boolean = openFlag.get()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!openFlag.get()) return -1
        return try {
            inputStream.read(buffer, offset, length)
        } catch (e: Exception) {
            -1
        }
    }

    override fun close() {
        if (openFlag.compareAndSet(true, false)) {
            try {
                inputStream.close()
            } catch (_: Exception) {}
        }
    }

    override val isOpen: Boolean
        get() = openFlag.get()
}

/**
 * Complete Android USB Sensor Adapter.
 *
 * Authority: PRD v3.2 Section 14.1 & 14.2
 * Ownership: Rohan (Android Sensor Adapter - usb/)
 *
 * Responsibilities:
 * - Manages USB transport read lifecycle on background thread
 * - Bounded line parsing (128-byte limit, LF/CRLF) via [SensorParser]
 * - Monotonic receipt timestamps via injectable clock
 * - Sequence loss diagnostics (tracking missing SEQ numbers)
 * - 3-valid-packet recovery logic via [SensorStateManager]
 * - Immediate proximity STOP candidate dispatch (<= 50 cm)
 * - Watchdog health evaluation API for Rishav's Risk Engine
 */
class UsbSensorAdapter(
    private val transport: UsbTransport,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onRecordReceived: ((SensorRecord) -> Unit)? = null,
    private val onHealthChanged: ((oldHealth: SensorHealth, newHealth: SensorHealth) -> Unit)? = null,
    private val onImmediateStopCandidate: ((SensorRecord) -> Unit)? = null
) {
    companion object {
        const val READ_BUFFER_SIZE = 128
        private const val TWO_POW_32 = 1L shl 32
        private const val HALF_POW_32 = 1L shl 31
    }

    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null

    val parser = SensorParser()
    val stateManager = SensorStateManager(clock)

    // Sequence loss diagnostics
    private var lastObservedSeq: Long? = null
    val totalLostPackets = AtomicLong(0)
    val totalReceivedPackets = AtomicLong(0)

    /**
     * Starts the USB reading worker thread.
     */
    @Synchronized
    fun start() {
        if (isRunning.get()) return

        if (!transport.open()) {
            return
        }

        isRunning.set(true)
        stateManager.onConnected()
        parser.reset()
        lastObservedSeq = null

        workerThread = Thread({
            val buffer = ByteArray(READ_BUFFER_SIZE)
            while (isRunning.get() && transport.isOpen) {
                val bytesRead = transport.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    val receiptTime = clock()
                    val prevHealth = stateManager.currentHealth

                    parser.feedBytes(buffer, bytesRead, receiptTime, { record ->
                        totalReceivedPackets.incrementAndGet()
                        trackSequenceLoss(record.sequence)

                        stateManager.onRecordReceived(record)

                        onRecordReceived?.invoke(record)

                        if (record.isImmediateStopCandidate) {
                            onImmediateStopCandidate?.invoke(record)
                        }

                        val newHealth = stateManager.currentHealth
                        if (newHealth != prevHealth) {
                            onHealthChanged?.invoke(prevHealth, newHealth)
                        }
                    })
                } else if (bytesRead < 0) {
                    // Stream closed or error
                    break
                }
            }

            // Connection lost / closed
            val prevHealth = stateManager.currentHealth
            stateManager.onDisconnected()
            if (stateManager.currentHealth != prevHealth) {
                onHealthChanged?.invoke(prevHealth, stateManager.currentHealth)
            }
            isRunning.set(false)
        }, "UsbSensorAdapter-Worker").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Stops reading and closes transport.
     */
    @Synchronized
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return
        }
        transport.close()
        workerThread?.interrupt()
        workerThread = null
        stateManager.onDisconnected()
    }

    /**
     * Diagnostic sequence gap counter per PRD Section 14.1.
     */
    private fun trackSequenceLoss(currentSeq: Long) {
        val prev = lastObservedSeq
        if (prev != null) {
            val delta = (currentSeq - prev).mod(TWO_POW_32)
            if (delta in 2 until HALF_POW_32) {
                val lost = delta - 1
                totalLostPackets.addAndGet(lost)
            }
        }
        lastObservedSeq = currentSeq
    }

    /**
     * Periodically called by Rishav's Risk Engine (e.g. 50 ms watchdog loop).
     * Evaluates staleness and returns an immutable state snapshot.
     */
    fun evaluateHealth(): SensorState {
        val prevHealth = stateManager.currentHealth
        val snapshot = stateManager.evaluateHealth()
        if (snapshot.health != prevHealth) {
            onHealthChanged?.invoke(prevHealth, snapshot.health)
        }
        return snapshot
    }

    val currentHealth: SensorHealth
        get() = stateManager.currentHealth

    val usableDistanceCm: Int?
        get() = stateManager.usableDistanceCm

    val isImmediateStopCandidate: Boolean
        get() = stateManager.isImmediateStopCandidate
}
