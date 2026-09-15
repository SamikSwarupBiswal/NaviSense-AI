package dev.navisense.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production Android USB CDC Transport for communicating with ESP32-S3 over USB-C OTG.
 * Implements standard USB CDC-ACM wire communication adhering to PRD §14.
 *
 * Owned by Rohan / Rishav.
 */
class AndroidUsbCdcTransport(
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice
) : UsbTransport {

    companion object {
        private const val TAG = "UsbCdcTransport"
        const val BAUD_RATE = 115200

        // USB CDC Control Transfer Requests
        private const val CDC_SET_LINE_CODING = 0x20
        private const val CDC_SET_CONTROL_LINE_STATE = 0x22
        private const val CDC_REQ_TYPE = 0x21 // Class-specific interface request
    }

    private var connection: UsbDeviceConnection? = null
    private var dataInterface: UsbInterface? = null
    private var controlInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private val openFlag = AtomicBoolean(false)

    override fun open(): Boolean {
        if (openFlag.get()) return true

        Log.d(TAG, "Opening USB device: ${usbDevice.deviceName} (VID=0x${Integer.toHexString(usbDevice.vendorId)}, PID=0x${Integer.toHexString(usbDevice.productId)})")
        val conn = usbManager.openDevice(usbDevice) ?: run {
            Log.e(TAG, "Failed to open UsbDeviceConnection (permission missing or device busy)")
            return false
        }

        var foundDataIface: UsbInterface? = null
        var foundControlIface: UsbInterface? = null
        var foundEpIn: UsbEndpoint? = null
        var foundEpOut: UsbEndpoint? = null

        // Scan all interfaces for CDC control and CDC data endpoints
        for (i in 0 until usbDevice.interfaceCount) {
            val iface = usbDevice.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM || iface.interfaceClass == 2) {
                foundControlIface = iface
            }

            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN && foundEpIn == null) {
                        foundEpIn = ep
                        foundDataIface = iface
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT && foundEpOut == null) {
                        foundEpOut = ep
                    }
                }
            }
        }

        // Fallback: If no interface with bulk IN was identified via standard class, check all
        if (foundDataIface == null || foundEpIn == null) {
            for (i in 0 until usbDevice.interfaceCount) {
                val iface = usbDevice.getInterface(i)
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_IN) {
                        foundDataIface = iface
                        foundEpIn = ep
                        break
                    }
                }
                if (foundDataIface != null) break
            }
        }

        if (foundDataIface == null || foundEpIn == null) {
            Log.e(TAG, "No usable USB Bulk IN endpoint found on device")
            conn.close()
            return false
        }

        // Claim control interface if distinct
        if (foundControlIface != null && foundControlIface != foundDataIface) {
            try {
                conn.claimInterface(foundControlIface, true)
            } catch (e: Exception) {
                Log.w(TAG, "Notice: could not claim control interface: ${e.message}")
            }
        }

        if (!conn.claimInterface(foundDataIface, true)) {
            Log.e(TAG, "Failed to claim USB data interface ${foundDataIface.id}")
            conn.close()
            return false
        }

        // 1. Configure CDC Line Coding: 115200 baud, 1 stop bit, no parity, 8 data bits
        val lineCoding = byteArrayOf(
            0x00.toByte(), 0xC2.toByte(), 0x01.toByte(), 0x00.toByte(), // Baud rate: 115200
            0x00.toByte(),                                              // Stop bits: 1
            0x00.toByte(),                                              // Parity: None
            0x08.toByte()                                               // Data bits: 8
        )
        val ctrlId = foundControlIface?.id ?: 0
        try {
            conn.controlTransfer(CDC_REQ_TYPE, CDC_SET_LINE_CODING, 0, ctrlId, lineCoding, lineCoding.size, 500)
            if (ctrlId != foundDataIface.id) {
                conn.controlTransfer(CDC_REQ_TYPE, CDC_SET_LINE_CODING, 0, foundDataIface.id, lineCoding, lineCoding.size, 500)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Warning: SET_LINE_CODING failed or ignored: ${e.message}")
        }

        // 2. Assert DTR and RTS (Value = 0x0003: DTR=1, RTS=1)
        // Crucial for ESP32-S3 native USB CDC to initiate data transmission to host
        try {
            conn.controlTransfer(CDC_REQ_TYPE, CDC_SET_CONTROL_LINE_STATE, 0x0003, ctrlId, null, 0, 500)
            if (ctrlId != foundDataIface.id) {
                conn.controlTransfer(CDC_REQ_TYPE, CDC_SET_CONTROL_LINE_STATE, 0x0003, foundDataIface.id, null, 0, 500)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Warning: SET_CONTROL_LINE_STATE failed or ignored: ${e.message}")
        }

        this.connection = conn
        this.controlInterface = foundControlIface
        this.dataInterface = foundDataIface
        this.endpointIn = foundEpIn
        this.endpointOut = foundEpOut
        this.openFlag.set(true)
        Log.i(TAG, "USB CDC transport opened successfully on interface ${foundDataIface.id}, IN ep=${foundEpIn.address}")
        return true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val conn = connection ?: return -1
        val ep = endpointIn ?: return -1
        if (!openFlag.get()) return -1

        val tempBuffer = if (offset == 0 && length == buffer.size) {
            buffer
        } else {
            ByteArray(length)
        }

        val transferred = conn.bulkTransfer(ep, tempBuffer, length, 200)
        return when {
            transferred > 0 -> {
                if (tempBuffer !== buffer) {
                    System.arraycopy(tempBuffer, 0, buffer, offset, transferred)
                }
                transferred
            }
            transferred == 0 -> {
                0
            }
            else -> {
                // transferred < 0: indicates read timeout or communication error
                // If the transport is still intentionally open and connection alive,
                // return 0 (timeout - no bytes ready yet) so UsbSensorAdapter doesn't terminate!
                if (openFlag.get() && connection != null) {
                    0
                } else {
                    -1
                }
            }
        }
    }

    override fun close() {
        if (openFlag.compareAndSet(true, false)) {
            Log.d(TAG, "Closing USB CDC transport")
            try {
                controlInterface?.let { connection?.releaseInterface(it) }
                dataInterface?.let { connection?.releaseInterface(it) }
                connection?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing USB connection", e)
            }
            connection = null
            controlInterface = null
            dataInterface = null
            endpointIn = null
            endpointOut = null
        }
    }

    override val isOpen: Boolean
        get() = openFlag.get()
}
