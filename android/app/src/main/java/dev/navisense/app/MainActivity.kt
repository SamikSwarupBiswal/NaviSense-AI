package dev.navisense.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import dev.navisense.R
import dev.navisense.camera.CameraXAnalyzer
import dev.navisense.camera.DetectionOverlayView
import dev.navisense.contracts.AppMode
import dev.navisense.contracts.AppVisionMode
import dev.navisense.contracts.PathStatus
import dev.navisense.contracts.SensorEvent
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SensorWireRecord
import dev.navisense.contracts.SessionToken
import dev.navisense.inference.ModelMetadata
import dev.navisense.inference.PyTorchLiteInferenceBackend
import dev.navisense.inference.YoloModelRunner
import dev.navisense.usb.AndroidUsbCdcTransport
import dev.navisense.usb.SensorRecord
import dev.navisense.usb.UsbSensorAdapter
import dev.navisense.usb.SensorHealth as UsbSensorHealth
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Accessible UI Activity Shell for NaviSense AI MVP (PRD Section 13.6).
 * Features:
 * - Live CameraX viewfinder preview displayed in accessible high-contrast UI.
 * - Real-time USB CDC ultrasonic sensor integration over Type-C OTG (ESP32-S3).
 * - Minimum 48dp touch targets (large buttons for low vision).
 * - Immediate Stop button with no confirmation prompt.
 * - TalkBack announcements on all state transitions.
 * - Screen kept awake during active navigation.
 * - Multimodal haptic feedback for proximity warnings.
 * - 50 ms central watchdog tick loop.
 */
class MainActivity : AppCompatActivity(), SessionCoordinator.StateChangeListener {

    companion object {
        private const val TAG = "NaviSenseMainActivity"
        private const val ACTION_USB_PERMISSION = "dev.navisense.USB_PERMISSION"
        const val ESP32_VENDOR_ID = 0x303A
        const val ESP32_PRODUCT_ID = 0x1001
    }

    private lateinit var coordinator: SessionCoordinator
    private lateinit var hapticFeedback: IHapticFeedback

    private lateinit var viewFinder: PreviewView
    private lateinit var detectionOverlay: DetectionOverlayView
    private lateinit var tvSystemMode: TextView
    private lateinit var tvPathStatus: TextView
    private lateinit var tvSensorStatus: TextView
    private lateinit var btnStartWalking: Button
    private lateinit var btnSearchNearby: Button
    private lateinit var btnConfirmArrival: Button
    private lateinit var btnStop: Button

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraAnalyzer: CameraXAnalyzer? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var activeVisionRunner: YoloModelRunner? = null

    // USB Sensor Subsystem
    private lateinit var usbManager: UsbManager
    private var usbTransport: AndroidUsbCdcTransport? = null
    private var usbSensorAdapter: UsbSensorAdapter? = null
    private var activeUsbDevice: UsbDevice? = null

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            val app = application as? NaviSenseApp
            val now = app?.clock?.nowMonotonicMs() ?: SystemClock.elapsedRealtime()
            coordinator.onWatchdogTick(now)
            watchdogHandler.postDelayed(this, 50L)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupCamera()
        } else {
            announce(getString(R.string.status_sensor_unavailable))
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    Log.i(TAG, "USB Device Attached: ${device?.deviceName} (VID=0x${device?.vendorId?.toString(16)})")
                    device?.let { handleUsbDeviceAttached(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    Log.i(TAG, "USB Device Detached: ${device?.deviceName}")
                    if (device == null || device == activeUsbDevice) {
                        disconnectUsbSensor()
                    }
                }
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        Log.i(TAG, "USB Permission result: granted=$granted for ${device?.deviceName}")
                        if (granted && device != null) {
                            connectUsbSensor(device)
                        } else {
                            Log.w(TAG, "USB permission denied by user")
                            runOnUiThread {
                                tvSensorStatus.text = "Ultrasonic: Permission Denied"
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as NaviSenseApp
        coordinator = app.sessionCoordinator
        hapticFeedback = HapticFeedbackManager(this)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        viewFinder = findViewById(R.id.viewFinder)
        detectionOverlay = findViewById(R.id.detectionOverlay)
        tvSystemMode = findViewById(R.id.tvSystemMode)
        tvPathStatus = findViewById(R.id.tvPathStatus)
        tvSensorStatus = findViewById(R.id.tvSensorStatus)
        btnStartWalking = findViewById(R.id.btnStartWalking)
        btnSearchNearby = findViewById(R.id.btnSearchNearby)
        btnConfirmArrival = findViewById(R.id.btnConfirmArrival)
        btnStop = findViewById(R.id.btnStop)

        btnStartWalking.setOnClickListener {
            coordinator.startMobility()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            announce(getString(R.string.status_mobility))
        }

        btnSearchNearby.setOnClickListener {
            coordinator.startNearbySearch("keys")
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            announce(getString(R.string.phrase_stop_walking_searching))
        }

        btnConfirmArrival.setOnClickListener {
            val token = coordinator.confirmArrivalAtZone()
            if (token != null) {
                announce(getString(R.string.phrase_stop_walking_searching))
            }
        }

        // Critical safety button: immediate Stop without confirmation dialog
        btnStop.setOnClickListener {
            hapticFeedback.cancel()
            coordinator.userStop()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            announce(getString(R.string.status_idle))
        }

        coordinator.addListener(this)
        updateUiState(coordinator.currentMode)

        // Initialize CameraX permission check and open camera preview
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Register USB device broadcast receiver
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        // Probe already connected USB devices on startup
        checkConnectedUsbDevices()
    }

    override fun onResume() {
        super.onResume()
        watchdogHandler.post(watchdogRunnable)
        checkConnectedUsbDevices()
    }

    override fun onPause() {
        super.onPause()
        watchdogHandler.removeCallbacks(watchdogRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdogHandler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: IllegalArgumentException) {}

        disconnectUsbSensor()

        cameraAnalyzer?.stopSession()
        cameraAnalyzer?.close()
        releaseActiveVisionRunnerAsync()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()

        detectionOverlay.clearDetections()
        hapticFeedback.cancel()
        coordinator.removeListener(this)
        coordinator.userStop()
    }

    /**
     * Initializes CameraX with BOTH Viewfinder Preview and background ImageAnalysis analyzer.
     * Guarantees camera frames are visible to the user and processed by perception pipelines.
     */
    private fun setupCamera() {
        val app = application as NaviSenseApp
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()

                // 1. Live Preview Viewfinder
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }

                // 2. Real-time ImageAnalysis for YOLO perception
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val analyzer = CameraXAnalyzer(
                    clock = app.clock,
                    onPerceptionEvent = { event ->
                        coordinator.onPerceptionEvent(event)
                        detectionOverlay.setDetections(event.detections)
                    },
                    onSearchEvent = { event ->
                        coordinator.onSearchEvent(event)
                    }
                )
                cameraAnalyzer = analyzer
                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                Log.i(TAG, "CameraX preview and analyzer successfully bound to lifecycle")

                // Pre-activate local mobility vision model for live YOLO bounding boxes
                activateLocalModel(coordinator.sessionGeneration.createToken(AppMode.MOBILITY), AppVisionMode.MOBILITY, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraX", e)
                coordinator.fatalPause()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Checks connected USB devices for the ESP32-S3 node and initiates connection.
     */
    private fun checkConnectedUsbDevices() {
        if (usbSensorAdapter != null && usbTransport?.isOpen == true) {
            return
        }

        val deviceList = usbManager.deviceList
        Log.d(TAG, "Scanning USB devices: ${deviceList.size} found")
        for (device in deviceList.values) {
            if (isEsp32OrCdcDevice(device)) {
                Log.i(TAG, "Discovered candidate USB device: ${device.deviceName} (VID=0x${Integer.toHexString(device.vendorId)})")
                handleUsbDeviceAttached(device)
                break
            }
        }
    }

    private fun isEsp32OrCdcDevice(device: UsbDevice): Boolean {
        if (device.vendorId == ESP32_VENDOR_ID) return true
        // Also support common USB-to-UART bridges or CDC ACM classes
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_CDC_DATA ||
                iface.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_COMM) {
                return true
            }
        }
        return false
    }

    private fun handleUsbDeviceAttached(device: UsbDevice) {
        if (!isEsp32OrCdcDevice(device)) return

        if (usbManager.hasPermission(device)) {
            Log.i(TAG, "USB permission already granted for ${device.deviceName}. Connecting...")
            connectUsbSensor(device)
        } else {
            Log.i(TAG, "Requesting USB permission for ${device.deviceName}...")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    /**
     * Opens USB CDC transport, wires raw records to Coordinator and updates UI.
     */
    private fun connectUsbSensor(device: UsbDevice) {
        disconnectUsbSensor()

        try {
            val transport = AndroidUsbCdcTransport(usbManager, device)
            lateinit var adapter: UsbSensorAdapter
            adapter = UsbSensorAdapter(
                transport = transport,
                clock = { SystemClock.elapsedRealtime() },
                onRecordReceived = { record: SensorRecord ->
                    val wireRecord = SensorWireRecord(
                        version = 1,
                        sequenceNumber = record.sequence,
                        deviceUptimeMs = record.uptimeMs,
                        distanceCm = record.distanceCm,
                        isValid = record.isValid
                    )
                    val sensorHealth = when (adapter.currentHealth) {
                        UsbSensorHealth.HEALTHY -> SensorHealth.STREAMING
                        UsbSensorHealth.STALE -> SensorHealth.STALE
                        UsbSensorHealth.DEGRADED_INVALID -> SensorHealth.INVALID_DATA
                        UsbSensorHealth.DISCONNECTED -> SensorHealth.DETACHED
                        UsbSensorHealth.CONNECTING, UsbSensorHealth.RECOVERING -> SensorHealth.ERROR
                    }
                    val event = SensorEvent(
                        connectionId = device.deviceId.toLong(),
                        receiptMonotonicMs = record.receiptMonotonicMs,
                        wireRecord = wireRecord,
                        sensorHealth = sensorHealth
                    )
                    coordinator.onSensorEvent(event)

                    runOnUiThread {
                        if (record.isValid) {
                            tvSensorStatus.text = "Ultrasonic: ${record.distanceCm} cm"
                            if (record.isImmediateStopCandidate) {
                                hapticFeedback.triggerEmergencyStopVibration()
                            }
                        } else {
                            tvSensorStatus.text = "Ultrasonic: Invalid (out of range)"
                        }
                    }
                },
                onHealthChanged = { _, newHealth: UsbSensorHealth ->
                    val h = when (newHealth) {
                        UsbSensorHealth.HEALTHY, UsbSensorHealth.RECOVERING -> SensorHealth.STREAMING
                        UsbSensorHealth.DEGRADED_INVALID -> SensorHealth.INVALID_DATA
                        UsbSensorHealth.STALE -> SensorHealth.STALE
                        else -> SensorHealth.DETACHED
                    }
                    coordinator.updateSensorHealth(h)
                },
                onImmediateStopCandidate = { _ ->
                    hapticFeedback.triggerEmergencyStopVibration()
                }
            )

            adapter.start()
            this.usbTransport = transport
            this.usbSensorAdapter = adapter
            this.activeUsbDevice = device

            coordinator.updateSensorHealth(SensorHealth.STREAMING)
            runOnUiThread {
                tvSensorStatus.text = "Ultrasonic: Connected"
            }
            Log.i(TAG, "USB Ultrasonic sensor connected and adapter running successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect USB sensor", e)
            disconnectUsbSensor()
        }
    }

    private fun disconnectUsbSensor() {
        try {
            usbSensorAdapter?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping sensor adapter", e)
        }
        try {
            usbTransport?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing USB transport", e)
        }
        usbSensorAdapter = null
        usbTransport = null
        activeUsbDevice = null

        coordinator.updateSensorHealth(SensorHealth.DETACHED)
        runOnUiThread {
            tvSensorStatus.text = getString(R.string.status_sensor_unavailable)
        }
        Log.i(TAG, "USB Ultrasonic sensor disconnected")
    }

    override fun onModeChanged(newMode: AppMode, token: SessionToken) {
        runOnUiThread {
            updateUiState(newMode)
            when (newMode) {
                AppMode.MOBILITY -> {
                    activateLocalModel(token, AppVisionMode.MOBILITY, null)
                }
                AppMode.FINAL_SEARCH -> {
                    activateLocalModel(token, AppVisionMode.LOCATE_SEARCH, coordinator.activeTargetClass)
                }
                AppMode.IDLE -> {
                    // Keep mobility vision active for live camera bounding box preview
                    activateLocalModel(token, AppVisionMode.MOBILITY, null)
                }
                AppMode.PAUSED, AppMode.FOUND -> {
                    cameraAnalyzer?.stopSession()
                    releaseActiveVisionRunnerAsync()
                    detectionOverlay.clearDetections()
                }
                else -> {}
            }
        }
    }

    /** Loads and activates the packaged local YOLO model; no network path exists. */
    private fun activateLocalModel(token: SessionToken, mode: AppVisionMode, targetClass: String?) {
        val analyzer = cameraAnalyzer
        if (analyzer == null) {
            Log.e(TAG, "Cannot activate $mode before CameraX analyzer is ready")
            coordinator.fatalPause()
            return
        }
        analyzer.stopSession()
        cameraExecutor.execute {
            val startedAt = SystemClock.elapsedRealtime()
            var runner: YoloModelRunner? = null
            try {
                val configuration = when (mode) {
                    AppVisionMode.MOBILITY -> LocalModelConfiguration(
                        assetName = "models/mobility_smoke.ptl",
                        identity = "mobility-v0.2.0-finetuned-d76302b6",
                        labels = listOf("person", "chair", "table", "backpack", "bottle"),
                        confidenceThreshold = 0.40f,
                        expectedSha256 = "d76302b62ba357a5dfa7531f201a7d7141ce55b1c940d3c6eae3b19d573b4936"
                    )
                    AppVisionMode.LOCATE_SEARCH -> LocalModelConfiguration(
                        assetName = "models/locate_smoke.ptl",
                        identity = "locate-v0.2.0-finetuned-85a6d1cf",
                        labels = listOf("keys", "wallet"),
                        confidenceThreshold = 0.25f,
                        expectedSha256 = "85a6d1cfce3daf55abafa0a341f129426af493bcd5592a059dbe1e8eb60db23a"
                    )
                    AppVisionMode.OFF -> throw IllegalArgumentException("OFF has no local model")
                }
                val modelPath = PyTorchLiteInferenceBackend.copyAssetToCache(this, configuration.assetName)
                verifyFileSha256(modelPath, configuration.expectedSha256)
                val backend = PyTorchLiteInferenceBackend(
                    modelPath = modelPath,
                    numClasses = configuration.labels.size,
                    confThreshold = 0.25f,
                    iouThreshold = 0.45f
                )
                runner = YoloModelRunner(backend)
                check(runner.load(
                    ModelMetadata(
                        modelIdentity = configuration.identity,
                        mode = mode,
                        inputWidth = 640,
                        inputHeight = 640,
                        classLabels = configuration.labels,
                        confidenceThreshold = configuration.confidenceThreshold,
                        nmsIouThreshold = 0.45f,
                        modelHashSha256 = configuration.expectedSha256
                    )
                )) { "Local model load failed" }
                check(SystemClock.elapsedRealtime() - startedAt <= 5_000L) { "Local model load exceeded 5 seconds" }
                if (!coordinator.sessionGeneration.isValid(token.generation)) {
                    runner.close()
                    return@execute
                }
                synchronized(this@MainActivity) {
                    activeVisionRunner?.close()
                    activeVisionRunner = runner
                }
                val result = analyzer.startSession(token.generation, mode, runner, targetClass)
                check(result == CameraXAnalyzer.StartResult.Started) {
                    (result as? CameraXAnalyzer.StartResult.Rejected)?.reason ?: "Camera analyzer rejected model"
                }
                Log.i(TAG, "Activated local $mode model ${configuration.identity}")
            } catch (failure: Throwable) {
                runner?.close()
                Log.e(TAG, "Failed to activate local $mode model", failure)
                coordinator.fatalPause()
            }
        }
    }

    private fun verifyFileSha256(path: String, expected: String) {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        java.io.File(path).inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(expected, ignoreCase = true)) { "Packaged model hash mismatch" }
    }

    private fun releaseActiveVisionRunnerAsync() {
        val runner = synchronized(this) {
            activeVisionRunner.also { activeVisionRunner = null }
        }
        if (runner != null && !cameraExecutor.isShutdown) {
            cameraExecutor.execute { runner.close() }
        }
    }

    private data class LocalModelConfiguration(
        val assetName: String,
        val identity: String,
        val labels: List<String>,
        val confidenceThreshold: Float,
        val expectedSha256: String
    )

    override fun onPathStatusChanged(newStatus: PathStatus) {
        runOnUiThread {
            when (newStatus) {
                PathStatus.CLEAR_OBSERVED -> {
                    tvPathStatus.text = getString(R.string.status_path_clear)
                    tvPathStatus.setTextColor(getColor(R.color.status_clear))
                }
                PathStatus.BLOCKED -> {
                    tvPathStatus.text = getString(R.string.status_path_blocked)
                    tvPathStatus.setTextColor(getColor(R.color.status_stop))
                    hapticFeedback.triggerEmergencyStopVibration()
                }
                PathStatus.UNKNOWN -> {
                    tvPathStatus.text = getString(R.string.status_path_unknown)
                    tvPathStatus.setTextColor(getColor(R.color.status_unknown))
                }
            }
        }
    }

    override fun onSensorHealthChanged(newHealth: SensorHealth) {
        runOnUiThread {
            when (newHealth) {
                SensorHealth.STREAMING -> {
                    if (!tvSensorStatus.text.startsWith("Ultrasonic:")) {
                        tvSensorStatus.text = getString(R.string.status_sensor_ok)
                    }
                }
                else -> {
                    tvSensorStatus.text = getString(R.string.status_sensor_unavailable)
                }
            }
        }
    }

    private fun updateUiState(mode: AppMode) {
        when (mode) {
            AppMode.IDLE -> {
                tvSystemMode.text = getString(R.string.status_idle)
                btnStartWalking.visibility = View.VISIBLE
                btnSearchNearby.visibility = View.VISIBLE
                btnConfirmArrival.visibility = View.GONE
            }
            AppMode.MOBILITY -> {
                tvSystemMode.text = getString(R.string.status_mobility)
                btnStartWalking.visibility = View.GONE
                btnSearchNearby.visibility = View.GONE
                if (coordinator.activeTargetClass != null) {
                    btnConfirmArrival.visibility = View.VISIBLE
                } else {
                    btnConfirmArrival.visibility = View.GONE
                }
            }
            AppMode.FINAL_SEARCH -> {
                tvSystemMode.text = getString(R.string.status_final_search)
                btnStartWalking.visibility = View.GONE
                btnSearchNearby.visibility = View.GONE
                btnConfirmArrival.visibility = View.GONE
            }
            AppMode.FOUND -> {
                tvSystemMode.text = getString(R.string.status_found)
                btnStartWalking.visibility = View.VISIBLE
                btnSearchNearby.visibility = View.VISIBLE
                btnConfirmArrival.visibility = View.GONE
            }
            AppMode.PAUSED -> {
                tvSystemMode.text = getString(R.string.status_paused)
                btnStartWalking.visibility = View.VISIBLE
                btnSearchNearby.visibility = View.VISIBLE
                btnConfirmArrival.visibility = View.GONE
            }
            else -> {
                tvSystemMode.text = "Status: ${mode.name}"
            }
        }
    }

    private fun announce(text: String) {
        window.decorView.announceForAccessibility(text)
    }
}
