package com.example.qrlookup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var processing = false

    private val camPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else {
            AlertDialog.Builder(this)
                .setTitle("Caméra")
                .setMessage("Permission refusée, impossible de scanner.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)

        previewView = findViewById(R.id.previewView)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Demande la permission caméra, puis on démarre
        camPermission.launch(android.Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(cameraExecutor!!, QrAnalyzer { result ->
                            if (!processing) {
                                processing = true
                                onQrScanned(result)
                            }
                        })
                    }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analysis
                )
            } catch (e: Exception) {
                Log.e("QR", "startCamera failed", e)
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onQrScanned(value: String) {
        val data = Intent().apply { putExtra("SCANNED_CODE", value) }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
    }
}