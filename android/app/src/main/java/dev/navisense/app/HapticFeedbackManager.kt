package dev.navisense.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Interface for haptic feedback in NaviSense AI.
 */
interface IHapticFeedback {
    fun triggerEmergencyStopVibration()
    fun triggerWarningVibration()
    fun cancel()
}

/**
 * Concrete HapticFeedbackManager utilizing Android's Vibrator / VibratorManager.
 * Provides multimodal sensory feedback for emergency STOP and looming collision warnings.
 */
class HapticFeedbackManager(context: Context) : IHapticFeedback {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Urgent dual-pulse vibration pattern for emergency STOP (<= 50cm or critical near-looming).
     */
    override fun triggerEmergencyStopVibration() {
        vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 200, 100, 300)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 200, 100, 300), -1)
        }
    }

    /**
     * Single alert pulse for approaching collision hazard (looming warning).
     */
    override fun triggerWarningVibration() {
        vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(150)
        }
    }

    /**
     * Immediately silences/cancels vibration.
     */
    override fun cancel() {
        vibrator?.cancel()
    }
}
