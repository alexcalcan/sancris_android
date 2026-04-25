package eu.sancris.cititor.camera

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Live preview: detectie QR Sancris si extragere serial.
 *
 * (Auto-rotirea pozelor capturate se face cu ZXing in RotireQR pentru ca
 * ML Kit nu expune pozitiile reale ale finder pattern-urilor.)
 */
class AnalizatorQR(
    private val onSerialDetectat: (String) -> Unit,
    private val onNiciunQR: () -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    private val regexSerial = Regex("/c/([A-Za-z0-9_\\-]+)/?$")

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { coduri ->
                val serial = coduri
                    .asSequence()
                    .mapNotNull { it.rawValue }
                    .mapNotNull { text -> regexSerial.find(text)?.groupValues?.get(1) }
                    .firstOrNull()

                if (serial != null) onSerialDetectat(serial) else onNiciunQR()
            }
            .addOnFailureListener {
                onNiciunQR()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
