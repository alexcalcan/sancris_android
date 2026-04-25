package eu.sancris.cititor.camera

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.math.abs

data class CornersDetectate(
    val corners: List<android.graphics.Point>,
    val imageWidth: Int,
    val imageHeight: Int,
)

class AnalizatorQR(
    private val onSerialDetectat: (String) -> Unit,
    private val onRotatieDetectata: (Int) -> Unit,
    private val onCornersDetectate: (CornersDetectate?) -> Unit,
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

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { coduri ->
                val sancrisQr = coduri.firstOrNull { c ->
                    c.rawValue?.let { regexSerial.find(it) != null } == true
                }

                if (sancrisQr != null) {
                    val serial = sancrisQr.rawValue?.let { regexSerial.find(it)?.groupValues?.get(1) }
                    if (serial != null) onSerialDetectat(serial)

                    sancrisQr.cornerPoints?.takeIf { it.size == 4 }?.let { corners ->
                        val rotatie = computeQrRotation(corners, rotationDegrees)
                        onRotatieDetectata(rotatie)

                        // Dimensiunile imaginii in spatiul rotit (display-aligned).
                        val (w, h) = if (rotationDegrees == 90 || rotationDegrees == 270) {
                            imageProxy.height to imageProxy.width
                        } else {
                            imageProxy.width to imageProxy.height
                        }
                        onCornersDetectate(CornersDetectate(corners.toList(), w, h))
                    } ?: onCornersDetectate(null)
                } else {
                    onCornersDetectate(null)
                    onNiciunQR()
                }
            }
            .addOnFailureListener {
                onNiciunQR()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * cornerPoints sunt in spatiul ImageProxy (sensor). Display-ul roteste
     * imaginea cu [rotationDegrees] CW. Aplicam aceeasi rotatie pe vectorul
     * corners[0]→corners[1] ca sa-l aducem in display space, apoi calculam
     * rotatia necesara pentru ca QR-ul sa apara upright in display.
     */
    private fun computeQrRotation(corners: Array<android.graphics.Point>, rotationDegrees: Int): Int {
        val dxSensor = corners[1].x - corners[0].x
        val dySensor = corners[1].y - corners[0].y

        // Rotim vectorul cu rotationDegrees CW: (x,y) -> (-y, x) pt 90° CW.
        val (dx, dy) = when (rotationDegrees % 360) {
            0 -> dxSensor to dySensor
            90 -> -dySensor to dxSensor
            180 -> -dxSensor to -dySensor
            270 -> dySensor to -dxSensor
            else -> dxSensor to dySensor
        }

        return when {
            abs(dx) > abs(dy) && dx > 0 -> 0
            abs(dy) > abs(dx) && dy > 0 -> 270   // QR right -> down → rotate 90° CCW = 270° CW
            abs(dx) > abs(dy) && dx < 0 -> 180
            abs(dy) > abs(dx) && dy < 0 -> 90    // QR right -> up → rotate 90° CW
            else -> 0
        }
    }
}
