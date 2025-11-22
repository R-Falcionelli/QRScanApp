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
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var processing = false

    // On déclare une variable 'camPermission'.
    // Elle va servir à demander la permission CAMERA à l'utilisateur.
    private val camPermission = registerForActivityResult(

        // On utilise ici un "contrat" fourni par Android :
        // ActivityResultContracts.RequestPermission()
        // → Android affichera le popup standard de permission caméra.
        ActivityResultContracts.RequestPermission()

        // ▼ ▼ ▼ À partir d'ici, on passe une LAMBDA ★
        // C’est la fonction qui sera exécutée APRÈS la réponse de l’utilisateur.
        // Le paramètre 'granted' contiendra TRUE (autorisé) ou FALSE (refusé).
    ) { granted ->

        // 'granted' == true → permission acceptée
        if (granted) {

            // On démarre réellement la caméra
            startCamera()
        }
        // Sinon : 'granted' == false → permission refusée
        else {

            // On affiche une boîte de dialogue expliquant le refus
            AlertDialog.Builder(this)
                .setTitle("Caméra")
                .setMessage("Permission refusée, impossible de scanner.")

                // Ici, on définit ce qui se passe quand on clique "OK"
                // La lambda prend 2 paramètres (dialog, boutonIndex)
                // mais on ne les utilise pas → on met "_"
                .setPositiveButton("OK") { _, _ ->

                    // On ferme l’activité de scan
                    finish()
                }
                .show()
        }
    }
// Fin de la lambda

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

                val strategy = ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(strategy)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
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