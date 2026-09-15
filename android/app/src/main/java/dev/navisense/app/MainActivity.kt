package dev.navisense.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.navisense.R
import dev.navisense.contracts.AppMode
import dev.navisense.contracts.PathStatus
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SessionToken

/**
 * Accessible UI Activity Shell for NaviSense AI MVP (PRD Section 13.6).
 * Features:
 * - Minimum 48dp touch targets (large buttons for low vision).
 * - Immediate Stop button with no confirmation prompt.
 * - TalkBack announcements on all state transitions.
 * - Screen kept awake during active navigation.
 */
class MainActivity : AppCompatActivity(), SessionCoordinator.StateChangeListener {

    private lateinit var coordinator: SessionCoordinator
    private lateinit var hapticFeedback: IHapticFeedback

    private lateinit var tvSystemMode: TextView
    private lateinit var tvPathStatus: TextView
    private lateinit var tvSensorStatus: TextView
    private lateinit var btnStartWalking: Button
    private lateinit var btnSearchNearby: Button
    private lateinit var btnConfirmArrival: Button
    private lateinit var btnStop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val app = application as NaviSenseApp
        coordinator = app.sessionCoordinator
        hapticFeedback = HapticFeedbackManager(this)

        tvSystemMode = findViewById(R.id.tvSystemMode)
        tvPathStatus = findViewById(R.id.tvPathStatus)
        tvSensorStatus = findViewById(R.id.tvSensorStatus)
        btnStartWalking = findViewById(R.id.btnStartWalking)
        btnSearchNearby = findViewById(R.id.btnSearchNearby)
        btnConfirmArrival = findViewById(R.id.btnConfirmArrival)
        btnStop = findViewById(R.id.btnStop)

        btnStartWalking.setOnClickListener {
            val token = coordinator.startMobility()
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            announce(getString(R.string.status_mobility))
        }

        btnSearchNearby.setOnClickListener {
            // Default demo object: keys
            val token = coordinator.startNearbySearch("keys")
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            announce(getString(R.string.phrase_stop_walking_searching))
        }

        btnConfirmArrival.setOnClickListener {
            val token = coordinator.confirmArrivalAtZone()
            if (token != null) {
                announce(getString(R.string.phrase_stop_walking_searching))
            }
        }

        // Critical safety button: immediate Stop without dialog
        btnStop.setOnClickListener {
            hapticFeedback.cancel()
            coordinator.userStop()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            announce(getString(R.string.status_idle))
        }

        coordinator.addListener(this)
        updateUiState(coordinator.currentMode)
    }

    override fun onDestroy() {
        super.onDestroy()
        hapticFeedback.cancel()
        coordinator.removeListener(this)
        coordinator.userStop()
    }

    override fun onModeChanged(newMode: AppMode, token: SessionToken) {
        runOnUiThread {
            updateUiState(newMode)
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
