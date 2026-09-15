package dev.navisense.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import dev.navisense.R
import dev.navisense.camera.CameraXAnalyzer
import dev.navisense.contracts.AppMode
import dev.navisense.contracts.AppVisionMode
import dev.navisense.contracts.PathStatus
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SessionToken
import dev.navisense.inference.YoloModelRunner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Accessible UI Activity Shell for NaviSense AI MVP (PRD Section 13.6).
 * Features:
 * - Minimum 48dp touch targets (large buttons for low vision).
 * - Immediate Stop button with no confirmation prompt.
 * - TalkBack announcements on all state transitions.
 * - Screen kept awake during active navigation.
 * - CameraX ImageAnalysis producer lifecycle binding.
 * - USB device attach/detach state listener.
 * - 50 ms central watchdog tick loop.
 */
class MainActivity : AppCompatActivity(), SessionCoordinator.StateChangeListener {

    private lateinit var coordinator: SessionCoordinator

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

    private var mobilityRunner: YoloModelRunner? = null
    private var locateRunner: YoloModelRunner? = null

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            val app = application as? NaviSenseApp
            val now = app?.clock?.nowMonotonicMs() ?: System.currentTimeMillis()
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
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null && device.vendorId == ESP32_VENDOR_ID) {
                        coordinator.updateSensorHealth(SensorHealth.STREAMING)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    coordinator.updateSensorHealth(SensorHealth.DETACHED)
                }
            }
        }
    }

    companion object {
        const val ESP32_VENDOR_ID = 0x303A
        const val ESP32_PRODUCT_ID = 0x1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as NaviSenseApp
        coordinator = app.sessionCoordinator

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
            // Default demo object: keys
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
            coordinator.userStop()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            announce(getString(R.string.status_idle))
        }

        coordinator.addListener(this)
        updateUiState(coordinator.currentMode)

        // Initialize CameraX permission check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Register USB device broadcast receiver
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        watchdogHandler.post(watchdogRunnable)
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

        cameraAnalyzer?.stopSession()
        cameraAnalyzer?.close()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()

        coordinator.removeListener(this)
        coordinator.userStop()
    }

    private fun setupCamera() {
        val app = application as NaviSenseApp
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
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
                    }
                )
                cameraAnalyzer = analyzer

                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                coordinator.fatalPause()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onModeChanged(newMode: AppMode, token: SessionToken) {
        runOnUiThread {
            updateUiState(newMode)
            when (newMode) {
                AppMode.MOBILITY -> {
                    val runner = mobilityRunner ?: YoloModelRunner().also { mobilityRunner = it }
                    cameraAnalyzer?.startSession(
                        sessionGeneration = token.generation,
                        mode = AppVisionMode.MOBILITY,
                        runner = runner
                    )
                }
                AppMode.FINAL_SEARCH -> {
                    val runner = locateRunner ?: YoloModelRunner().also { locateRunner = it }
                    cameraAnalyzer?.startSession(
                        sessionGeneration = token.generation,
                        mode = AppVisionMode.LOCATE_SEARCH,
                        runner = runner,
                        targetClass = coordinator.activeTargetClass
                    )
                }
                AppMode.IDLE, AppMode.PAUSED, AppMode.FOUND -> {
                    cameraAnalyzer?.stopSession()
                }
                else -> {}
            }
        }
    }

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

    override fun onSensorHealthChanged(newHealth: SensorHealth) {
        runOnUiThread {
            when (newHealth) {
                SensorHealth.STREAMING -> {
                    tvSensorStatus.text = getString(R.string.status_sensor_ok)
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
