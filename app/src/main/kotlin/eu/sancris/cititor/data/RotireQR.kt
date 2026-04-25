package eu.sancris.cititor.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs

object RotireQR {

    private val scanner = BarcodeScanning.getClient()

    /**
     * Detecteaza QR-ul in poza si rotește fișierul jpg pe disc astfel încât
     * QR-ul (și implicit contorul) să apară upright. Daca nu detecteaza QR
     * sau e deja upright, lasa fisierul neschimbat.
     *
     * Algoritm:
     * - corner[0] = top-left al QR-ului (din perspectiva QR-ului), corner[1] = top-right
     * - vectorul corner[0]→corner[1] in upright = orizontal-spre-dreapta (dx>0, dy~0)
     * - daca dy>0 (QR's right pointeaza in jos): rotim poza 90° CCW
     * - daca dx<0 (QR's right pointeaza la stanga): rotim 180°
     * - daca dy<0 (QR's right pointeaza in sus): rotim 90° CW
     */
    suspend fun rotesteDupaQR(fisier: File) = withContext(Dispatchers.IO) {
        val original = BitmapFactory.decodeFile(fisier.absolutePath) ?: return@withContext
        val image = InputImage.fromBitmap(original, 0)

        val barcodes = suspendCancellableCoroutine<List<Barcode>> { cont ->
            scanner.process(image)
                .addOnSuccessListener { cont.resume(it.toList()) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

        val qr = barcodes.firstOrNull { (it.cornerPoints?.size ?: 0) == 4 } ?: return@withContext
        val corners = qr.cornerPoints!!
        val dx = corners[1].x - corners[0].x
        val dy = corners[1].y - corners[0].y

        val rotatieCw = when {
            abs(dx) > abs(dy) && dx > 0 -> 0      // upright
            abs(dy) > abs(dx) && dy > 0 -> 270    // QR right -> down → rotate 90° CCW = 270° CW
            abs(dx) > abs(dy) && dx < 0 -> 180    // upside down
            abs(dy) > abs(dx) && dy < 0 -> 90     // QR right -> up → rotate 90° CW
            else -> 0
        }

        if (rotatieCw == 0) return@withContext

        val matrix = Matrix().apply { postRotate(rotatieCw.toFloat()) }
        val rotita = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

        fisier.outputStream().use { rotita.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        rotita.recycle()
        if (!original.isRecycled) original.recycle()
    }
}
