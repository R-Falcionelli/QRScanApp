package com.example.qrlookup

import androidx.annotation.OptIn
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.ExperimentalGetImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode

class QrAnalyzer(private val onQrDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
    // On force ML Kit à ne regarder QUE les QR codes
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    private val scanner = BarcodeScanning.getClient()
    private var fired = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (fired) return@addOnSuccessListener
                for (b in barcodes) {
                    val v = b.rawValue
                    if (!v.isNullOrBlank()) {
                        fired = true
                        onQrDetected(v)
                        break
                    }
                }
            }
            .addOnFailureListener { it.printStackTrace() }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}