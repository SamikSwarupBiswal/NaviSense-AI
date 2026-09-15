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
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.navisense.R
import dev.navisense.camera.CameraXAnalyzer
import dev.navisense.camera.DetectionOverlayView
import dev.navisense.contracts.AppMode
import dev.navisense.contracts.AppVisionMode
import dev.navisense.contracts.PathStatus
import dev.navisense.navigation.maps.DeviceCompassProvider
import dev.navisense.navigation.maps.GoogleRoutesService
import dev.navisense.navigation.maps.models.GeoPoint
import dev.navisense.navigation.maps.models.NavigationEngineStatus
import dev.navisense.navigation.maps.models.WalkingRoute
import dev.navisense.voice.VoiceDestinationRecognizer
import kotlinx.coroutines.launch
import dev.navisense.contracts.SensorEvent
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SensorWireRecord
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.navisense.contracts.SearchEvent
import dev.navisense.contracts.SearchTarget
import dev.navisense.contracts.SearchUiState
import dev.navisense.contracts.SessionToken
import dev.navisense.inference.ModelMetadata
import dev.navisense.inference.PyTorchLiteInferenceBackend
import dev.navisense.inference.TfliteGpuLocateBackend
import dev.navisense.inference.YoloModelRunner
import dev.navisense.navigation.RiskEvaluationResult
import dev.navisense.navigation.RiskLevel
import dev.navisense.usb.AndroidUsbCdcTransport
import dev.navisense.usb.SensorRecord
import dev.navisense.usb.UsbSensorAdapter
import dev.navisense.usb.SensorHealth as UsbSensorHealth
import dev.navisense.voice.AlertPriority
import dev.navisense.voice.SpeechRequest
import dev.navisense.voice.VoiceCommand
import dev.navisense.voice.VoiceCommandManager
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
    private lateinit var tvVoiceStatus: TextView
    private lateinit var btnStartWalking: Button
    private lateinit var btnSearchNearby: Button
    private lateinit var btnVoiceCommand: Button
    private lateinit var btnConfirmArrival: Button
    private lateinit var btnStop: Button
    private lateinit var btnVoiceWalkingNav: Button

    // Pedestrian Maps & Orientation Subsystem
    private lateinit var compassProvider: DeviceCompassProvider
    private lateinit var routesService: GoogleRoutesService
    private var voiceRecognizer: VoiceDestinationRecognizer? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var lastKnownLocation: GeoPoint = GeoPoint(12.8406, 80.1534)

    private var voiceCommandManager: VoiceCommandManager? = null

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
            cameraAnalyzer?.onWatchdogTick(now)
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

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceRecognition()
            speakVoiceFeedback(getString(R.string.voice_cmd_opened))
        } else {
            tvVoiceStatus.text = "Voice Control: Mic Permission Denied"
        }
    }

    private val requestNavPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (locationGranted && audioGranted) {
            startVoiceNavigationFlow()
        } else {
            announce("Location and microphone permissions are required for voice navigation.")
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
        detectionOverlay.visibility = View.GONE
        tvSystemMode = findViewById(R.id.tvSystemMode)
        tvPathStatus = findViewById(R.id.tvPathStatus)
        tvSensorStatus = findViewById(R.id.tvSensorStatus)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)
        btnStartWalking = findViewById(R.id.btnStartWalking)
        btnSearchNearby = findViewById(R.id.btnSearchNearby)
        btnVoiceCommand = findViewById(R.id.btnVoiceCommand)
        btnVoiceWalkingNav = findViewById(R.id.btnVoiceWalkingNav)
        btnConfirmArrival = findViewById(R.id.btnConfirmArrival)
        btnStop = findViewById(R.id.btnStop)

        compassProvider = DeviceCompassProvider(this)
        routesService = GoogleRoutesService()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        voiceRecognizer = VoiceDestinationRecognizer(
            context = this,
            onDestinationParsed = { destination ->
                onDestinationReceived(destination)
            },
            onError = { errorMsg ->
                announce(errorMsg)
            }
        )

        btnVoiceCommand.setOnClickListener {
            toggleVoiceRecognition()
        }

        btnVoiceWalkingNav.setOnClickListener {
            checkNavPermissionsAndStart()
        }

        btnStartWalking.setOnClickListener {
            coordinator.startMobility()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            announce(getString(R.string.status_mobility))
        }

        btnSearchNearby.setOnClickListener {
            showSearchTargetDialog()
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
            stopOutdoorNavigationSensors()
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

        // Initialize voice commands and recognition
        initVoiceRecognition()
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

        stopOutdoorNavigationSensors()
        voiceRecognizer?.destroy()
        detectionOverlay.clearDetections()
        hapticFeedback.cancel()
        voiceCommandManager?.destroy()
        voiceCommandManager = null
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
                    },
                    onSearchEvent = { event ->
                        coordinator.onSearchEvent(event)
                    },
                    onOverlayDetections = { _ ->
                        // Detection boxes removed from live camera preview per user request
                    }
                )
                cameraAnalyzer = analyzer
                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                Log.i(TAG, "CameraX preview and analyzer successfully bound to lifecycle")

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
                    // Emergency stop haptics unified through RiskEvaluationResult in onRiskEvaluated
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
                AppMode.IDLE, AppMode.PAUSED -> {
                    cameraAnalyzer?.stopSession()
                    releaseActiveVisionRunnerAsync()
                    detectionOverlay.clearDetections()
                }
                AppMode.FOUND -> {}
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
                        // Render/export contract candidates start at 0.25. The risk engine
                        // independently enforces the PRD's >= 0.40 qualification threshold.
                        confidenceThreshold = 0.25f,
                        expectedSha256 = "d76302b62ba357a5dfa7531f201a7d7141ce55b1c940d3c6eae3b19d573b4936"
                    )
                    AppVisionMode.LOCATE_SEARCH -> LocalModelConfiguration(
                        assetName = "models/locate_model.tflite",
                        identity = "locate-v0.2.0-tflite-gpu-bf3968aa",
                        labels = listOf("keys", "wallet"),
                        confidenceThreshold = 0.25f,
                        expectedSha256 = "bf3968aadba9b7cd30c4e58adcd7a95c453e4c02f52c90e2d5608a52a6487bc5"
                    )
                    AppVisionMode.OFF -> throw IllegalArgumentException("OFF has no local model")
                }
                val backend = if (mode == AppVisionMode.LOCATE_SEARCH) {
                    val modelPath = TfliteGpuLocateBackend.copyAssetToCache(this, configuration.assetName)
                    verifyFileSha256(modelPath, configuration.expectedSha256)
                    TfliteGpuLocateBackend(
                        modelFile = java.io.File(modelPath),
                        numClasses = configuration.labels.size,
                        confThreshold = 0.25f,
                        iouThreshold = 0.45f,
                        requireGpu = true
                    )
                } else {
                    val modelPath = PyTorchLiteInferenceBackend.copyAssetToCache(this, configuration.assetName)
                    verifyFileSha256(modelPath, configuration.expectedSha256)
                    PyTorchLiteInferenceBackend(
                        modelPath = modelPath,
                        numClasses = configuration.labels.size,
                        confThreshold = 0.25f,
                        iouThreshold = 0.45f
                    )
                }
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
                if (mode == AppVisionMode.LOCATE_SEARCH) {
                    check(coordinator.markNearbySearchReady(token.generation)) {
                        "Search session was cancelled before becoming ready"
                    }
                }
                Log.i(TAG, "Activated local $mode model ${configuration.identity}")
            } catch (failure: Throwable) {
                runner?.close()
                Log.e(TAG, "Failed to activate local $mode model", failure)
                if (mode == AppVisionMode.LOCATE_SEARCH) {
                    coordinator.failNearbySearch(failure.message)
                } else {
                    coordinator.fatalPause()
                }
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
                }
                PathStatus.UNKNOWN -> {
                    tvPathStatus.text = getString(R.string.status_path_unknown)
                    tvPathStatus.setTextColor(getColor(R.color.status_unknown))
                }
            }
        }
    }

    override fun onRiskEvaluated(result: RiskEvaluationResult) {
        runOnUiThread {
            if (coordinator.currentMode == AppMode.MOBILITY || coordinator.currentMode == AppMode.OUTDOOR_WALKING) {
                when (result.combinedRisk) {
                    RiskLevel.STOP -> {
                        if (result.isEscalation) {
                            hapticFeedback.triggerEmergencyStopVibration()
                        }
                    }
                    RiskLevel.SLOW -> {
                        if (result.isApproachingHazard && result.isEscalation) {
                            hapticFeedback.triggerWarningVibration()
                        }
                    }
                    else -> {}
                }
            } else if ((coordinator.currentMode == AppMode.FINAL_SEARCH || coordinator.currentMode == AppMode.FOUND) &&
                result.sensorRisk == RiskLevel.STOP && result.isEscalation
            ) {
                hapticFeedback.triggerEmergencyStopVibration()
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

    override fun onSearchStateChanged(newState: SearchUiState, event: SearchEvent?) {
        runOnUiThread {
            val target = SearchTarget.fromValue(coordinator.activeTargetClass)
            when (newState) {
                SearchUiState.LOADING_MODEL -> {
                    tvSystemMode.text = getString(
                        R.string.status_search_loading,
                        target?.displayName ?: getString(R.string.search_target_generic)
                    )
                }
                SearchUiState.SEARCHING -> {
                    tvSystemMode.text = getString(
                        R.string.status_searching_for,
                        target?.displayName ?: getString(R.string.search_target_generic)
                    )
                }
                SearchUiState.MULTIPLE_CANDIDATES -> {
                    tvSystemMode.text = getString(
                        R.string.status_search_multiple,
                        target?.displayName ?: getString(R.string.search_target_generic)
                    )
                }
                SearchUiState.FOUND -> {
                    val targetName = target?.displayName ?: getString(R.string.search_target_generic)
                    tvSystemMode.text = when (event?.direction) {
                        dev.navisense.contracts.TargetDirection.LEFT -> getString(
                            R.string.status_search_found_left,
                            targetName
                        )
                        dev.navisense.contracts.TargetDirection.CENTER -> getString(
                            R.string.status_search_found_center,
                            targetName
                        )
                        dev.navisense.contracts.TargetDirection.RIGHT -> getString(
                            R.string.status_search_found_right,
                            targetName
                        )
                        null -> getString(R.string.status_search_found_direction_unknown, targetName)
                    }
                }
                SearchUiState.TIMED_OUT -> {
                    cameraAnalyzer?.stopSession()
                    releaseActiveVisionRunnerAsync()
                    tvSystemMode.text = getString(
                        R.string.status_search_timed_out,
                        target?.displayName ?: getString(R.string.search_target_generic)
                    )
                    showSearchTimeoutDialog(target)
                }
                SearchUiState.ERROR -> tvSystemMode.text = getString(R.string.status_search_error)
                SearchUiState.NONE -> Unit
            }
        }
    }

    private fun showSearchTargetDialog() {
        val targets = arrayOf(SearchTarget.KEYS, SearchTarget.WALLET)
        val labels = targets.map { it.displayName }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_target_dialog_title)
            .setItems(labels) { _, which ->
                val target = targets[which]
                coordinator.startNearbySearch(target.canonicalName)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showSearchTimeoutDialog(target: SearchTarget?) {
        if (isFinishing || isDestroyed || coordinator.currentSearchState != SearchUiState.TIMED_OUT) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_timeout_dialog_title)
            .setMessage(
                getString(
                    R.string.search_timeout_dialog_message,
                    target?.displayName ?: getString(R.string.search_target_generic)
                )
            )
            .setPositiveButton(R.string.action_retry) { _, _ -> coordinator.retryNearbySearch() }
            .setNeutralButton(R.string.action_choose_another) { _, _ ->
                coordinator.userStop()
                showSearchTargetDialog()
            }
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                coordinator.userStop()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            .show()
    }

    override fun onNavigationStatusUpdated(status: NavigationEngineStatus) {
        runOnUiThread {
            tvSystemMode.text = "Walking to ${status.destinationName} (${status.totalRemainingDistanceMeters.toInt()}m left)"
            tvPathStatus.text = status.currentInstruction
        }
    }

    private fun checkNavPermissionsAndStart() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (fineLocation && audio) {
            startVoiceNavigationFlow()
        } else {
            requestNavPermissionsLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    private fun startVoiceNavigationFlow() {
        announce("Please say your destination.")
        voiceRecognizer?.startListening()
    }

    private fun onDestinationReceived(destination: String) {
        announce("Calculating walking route to $destination.")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                val origin = if (loc != null) GeoPoint(loc.latitude, loc.longitude) else lastKnownLocation
                lastKnownLocation = origin
                fetchAndStartWalkingRoute(origin, destination)
            }.addOnFailureListener {
                fetchAndStartWalkingRoute(lastKnownLocation, destination)
            }
        } else {
            fetchAndStartWalkingRoute(lastKnownLocation, destination)
        }
    }

    private fun fetchAndStartWalkingRoute(origin: GeoPoint, destination: String) {
        lifecycleScope.launch {
            val geocodeResult = routesService.geocodeDestination(destination)
            val targetPoint = geocodeResult.getOrDefault(GeoPoint(origin.latitude + 0.0005, origin.longitude + 0.0005))

            val routeResult = routesService.computeWalkingRoute(origin, targetPoint, destination)
            routeResult.onSuccess { route ->
                coordinator.startOutdoorWalking(route)
                startOutdoorNavigationSensors()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }.onFailure { err ->
                announce("Unable to find walking route: ${err.message}")
            }
        }
    }

    private fun startOutdoorNavigationSensors() {
        compassProvider.startListening { azimuth ->
            coordinator.onHeadingUpdated(azimuth)
        }
        startLocationTracking()
    }

    private fun stopOutdoorNavigationSensors() {
        compassProvider.stopListening()
        stopLocationTracking()
    }

    private fun startLocationTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(1.0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val point = GeoPoint(loc.latitude, loc.longitude)
                lastKnownLocation = point
                coordinator.onLocationUpdated(point, loc.accuracy)
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
    }

    private fun stopLocationTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    private fun updateUiState(mode: AppMode) {
        when (mode) {
            AppMode.IDLE -> {
                tvSystemMode.text = getString(R.string.status_idle)
                btnStartWalking.visibility = View.VISIBLE
                btnSearchNearby.visibility = View.VISIBLE
                btnVoiceWalkingNav.visibility = View.VISIBLE
                btnConfirmArrival.visibility = View.GONE
                stopOutdoorNavigationSensors()
            }
            AppMode.MOBILITY -> {
                tvSystemMode.text = getString(R.string.status_mobility)
                btnStartWalking.visibility = View.GONE
                btnSearchNearby.visibility = View.GONE
                btnVoiceWalkingNav.visibility = View.GONE
                if (coordinator.activeTargetClass != null) {
                    btnConfirmArrival.visibility = View.VISIBLE
                } else {
                    btnConfirmArrival.visibility = View.GONE
                }
            }
            AppMode.OUTDOOR_WALKING -> {
                tvSystemMode.text = getString(R.string.status_outdoor_nav)
                btnStartWalking.visibility = View.GONE
                btnSearchNearby.visibility = View.GONE
                btnVoiceWalkingNav.visibility = View.GONE
                btnConfirmArrival.visibility = View.GONE
            }
            AppMode.FINAL_SEARCH -> {
                tvSystemMode.text = when (coordinator.currentSearchState) {
                    SearchUiState.LOADING_MODEL -> getString(
                        R.string.status_search_loading,
                        SearchTarget.fromValue(coordinator.activeTargetClass)?.displayName
                            ?: getString(R.string.search_target_generic)
                    )
                    SearchUiState.TIMED_OUT -> getString(
                        R.string.status_search_timed_out,
                        SearchTarget.fromValue(coordinator.activeTargetClass)?.displayName
                            ?: getString(R.string.search_target_generic)
                    )
                    else -> getString(R.string.status_final_search)
                }
                btnStartWalking.visibility = View.GONE
                btnSearchNearby.visibility = View.GONE
                btnVoiceWalkingNav.visibility = View.GONE
                btnConfirmArrival.visibility = View.GONE
            }
            AppMode.FOUND -> {
                if (coordinator.currentSearchState != SearchUiState.FOUND) {
                    tvSystemMode.text = getString(R.string.status_found)
                }
                btnStartWalking.visibility = View.VISIBLE
                btnSearchNearby.visibility = View.VISIBLE
                btnVoiceWalkingNav.visibility = View.VISIBLE
                btnConfirmArrival.visibility = View.GONE
            }
            AppMode.PAUSED -> {
                tvSystemMode.text = getString(R.string.status_paused)
                btnStartWalking.visibility = View.VISIBLE
                btnSearchNearby.visibility = View.VISIBLE
                btnVoiceWalkingNav.visibility = View.VISIBLE
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        speakVoiceFeedback(getString(R.string.voice_cmd_opened))
        startVoiceRecognition()
    }

    private fun initVoiceRecognition() {
        val app = application as NaviSenseApp
        voiceCommandManager = VoiceCommandManager(
            context = this,
            isTtsSpeakingProvider = { app.speechArbiter.isSpeaking },
            onCommandRecognized = { command ->
                handleVoiceCommand(command)
            },
            onStateChanged = { isListening ->
                runOnUiThread {
                    if (isListening) {
                        tvVoiceStatus.text = getString(R.string.voice_cmd_listening)
                    } else {
                        tvVoiceStatus.text = "Voice Control: Standby"
                    }
                }
            }
        )

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
            speakVoiceFeedback(getString(R.string.voice_cmd_opened))
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceRecognition() {
        voiceCommandManager?.startListening()
    }

    private fun toggleVoiceRecognition() {
        val mgr = voiceCommandManager ?: return
        mgr.startListening()
        speakVoiceFeedback(getString(R.string.voice_cmd_listening))
    }

    private fun handleVoiceCommand(command: VoiceCommand) {
        runOnUiThread {
            when (command) {
                is VoiceCommand.FindTarget -> {
                    val target = command.target
                    val phrase = if (target == "wallet") {
                        getString(R.string.voice_cmd_searching_wallet)
                    } else {
                        getString(R.string.voice_cmd_searching_keys)
                    }
                    speakVoiceFeedback(phrase)
                    coordinator.startNearbySearch(target)
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                is VoiceCommand.StartWalking -> {
                    speakVoiceFeedback(getString(R.string.voice_cmd_walking_started))
                    coordinator.startMobility()
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                is VoiceCommand.NavigateTo -> {
                    onDestinationReceived(command.destination)
                }
                is VoiceCommand.Stop -> {
                    speakVoiceFeedback(getString(R.string.voice_cmd_stopped))
                    hapticFeedback.cancel()
                    coordinator.userStop()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                is VoiceCommand.ConfirmArrival -> {
                    val token = coordinator.confirmArrivalAtZone()
                    if (token != null) {
                        speakVoiceFeedback(getString(R.string.voice_cmd_arrival_confirmed))
                    }
                }
                is VoiceCommand.Help -> {
                    speakVoiceFeedback(getString(R.string.voice_cmd_help))
                }
                is VoiceCommand.AppStatus -> {
                    val status = "${tvSystemMode.text}. ${tvPathStatus.text}."
                    speakVoiceFeedback(status)
                }
                is VoiceCommand.Unknown -> {
                    Log.d(TAG, "Unrecognized voice command: ${command.rawText}")
                }
            }
        }
    }

    private fun speakVoiceFeedback(phrase: String) {
        val app = application as? NaviSenseApp ?: return
        val now = app.clock.nowMonotonicMs()
        app.speechArbiter.speak(
            SpeechRequest(
                utteranceId = "voice_cmd_$now",
                phrase = phrase,
                priority = AlertPriority.INFORMATIONAL,
                sessionGeneration = coordinator.sessionGeneration.get(),
                requestMonotonicMs = now
            )
        )
        announce(phrase)
    }
}
