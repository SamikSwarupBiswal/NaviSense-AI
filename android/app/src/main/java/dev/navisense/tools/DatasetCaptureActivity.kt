package dev.navisense.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import dev.navisense.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Interactive Dataset Capture Tool for walking path obstacles.
 * Captures synchronized JPEG frames and normalized YOLO .txt annotations
 * for Mobility YOLO model fine-tuning and AC-02 evaluation.
 */
class DatasetCaptureActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvStats: TextView
    private lateinit var btnCapture: Button

    private lateinit var btnCatChair: Button
    private lateinit var btnCatPerson: Button
    private lateinit var btnCatTable: Button
    private lateinit var btnCatBackpack: Button
    private lateinit var btnCatBottle: Button
    private lateinit var btnCatClear: Button

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    // Active selected class ID (-1 for Clear Path / negative frame)
    private var selectedClassId: Int = 1  // Default to Chair
    private var selectedClassName: String = "chair"

    // Counters
    private var countTotal = 0
    private var countChair = 0
    private var countPerson = 0
    private var countTable = 0
    private var countBackpack = 0
    private var countBottle = 0
    private var countClear = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required for capture", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dataset_capture)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = findViewById(R.id.previewView)
        tvStats = findViewById(R.id.tvStats)
        btnCapture = findViewById(R.id.btnCapture)

        btnCatChair = findViewById(R.id.btnCatChair)
        btnCatPerson = findViewById(R.id.btnCatPerson)
        btnCatTable = findViewById(R.id.btnCatTable)
        btnCatBackpack = findViewById(R.id.btnCatBackpack)
        btnCatBottle = findViewById(R.id.btnCatBottle)
        btnCatClear = findViewById(R.id.btnCatClear)

        setupCategoryButtons()

        btnCapture.setOnClickListener {
            captureFrame()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupCategoryButtons() {
        val buttons = listOf(
            btnCatPerson to (0 to "person"),
            btnCatChair to (1 to "chair"),
            btnCatTable to (2 to "table"),
            btnCatBackpack to (3 to "backpack"),
            btnCatBottle to (4 to "bottle"),
            btnCatClear to (-1 to "clear")
        )

        fun updateSelection(clsId: Int, name: String) {
            selectedClassId = clsId
            selectedClassName = name
            for ((btn, pair) in buttons) {
                if (pair.first == clsId) {
                    btn.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_orange_dark)
                } else {
                    btn.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
                }
            }
        }

        for ((btn, pair) in buttons) {
            btn.setOnClickListener {
                updateSelection(pair.first, pair.second)
            }
        }

        updateSelection(1, "chair")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Failed to bind camera: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureFrame() {
        val capture = imageCapture ?: return

        val baseDir = getExternalFilesDir("captured_dataset") ?: filesDir
        val imagesDir = File(baseDir, "images").apply { mkdirs() }
        val labelsDir = File(baseDir, "labels").apply { mkdirs() }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val photoFile = File(imagesDir, "walk_${timestamp}.jpg")
        val labelFile = File(labelsDir, "walk_${timestamp}.txt")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Write YOLO annotation
                    if (selectedClassId >= 0) {
                        // Bounding box centered inside the walking corridor: cx=0.50, cy=0.65, w=0.35, h=0.50
                        labelFile.writeText("${selectedClassId} 0.500000 0.650000 0.350000 0.500000\n")
                    } else {
                        // Empty file for negative / clear path frame per PRD §29 (AC-02)
                        labelFile.writeText("")
                    }

                    runOnUiThread {
                        triggerHapticFeedback()
                        countTotal++
                        when (selectedClassId) {
                            0 -> countPerson++
                            1 -> countChair++
                            2 -> countTable++
                            3 -> countBackpack++
                            4 -> countBottle++
                            -1 -> countClear++
                        }
                        updateStatsDisplay()
                        Toast.makeText(
                            this@DatasetCaptureActivity,
                            "Saved: ${photoFile.name} [${selectedClassName.uppercase()}]",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@DatasetCaptureActivity,
                            "Capture failed: ${exc.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    private fun updateStatsDisplay() {
        tvStats.text = "Total: $countTotal | Chair: $countChair | Person: $countPerson | Table: $countTable | Bag: $countBackpack | Clear: $countClear"
    }

    private fun triggerHapticFeedback() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
