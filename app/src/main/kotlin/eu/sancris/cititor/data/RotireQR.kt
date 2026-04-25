package eu.sancris.cititor.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import androidx.exifinterface.media.ExifInterface
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
     * Indrepta poza in 2 pasi:
     * 1. EXIF normalize: aplica EXIF orientation pe pixeli, reseteaza tag.
     * 2. QR fine-tune: detecteaza QR-ul; daca e tilted, roteste pana e upright
     *    (corners[0]→corners[1] = orizontal-spre-dreapta).
     *
     * Returneaza string de debug.
     */
    suspend fun rotesteDupaQR(fisier: File, hintRotatieDinLive: Int? = null): String = withContext(Dispatchers.IO) {
        val log = StringBuilder()

        val rotatieExif = readExifRotation(fisier)
        log.append("exif=$rotatieExif")

        var bitmap = BitmapFactory.decodeFile(fisier.absolutePath)
            ?: return@withContext "$log decode=failed"
        log.append(" raw=${bitmap.width}x${bitmap.height}")

        if (rotatieExif != 0) {
            bitmap = applyRotation(bitmap, rotatieExif)
        }

        // Folosim hint-ul din live preview daca e disponibil — evita rescan
        // pe imagine mare.
        val rotatieQr = if (hintRotatieDinLive != null) {
            log.append(" qr=hint:${hintRotatieDinLive}")
            hintRotatieDinLive
        } else {
            val qr = scanQR(bitmap)
            if (qr == null) {
                log.append(" qr=miss")
                0
            } else {
                val corners = qr.cornerPoints
                if (corners == null || corners.size != 4) {
                    log.append(" qr=found,no-corners")
                    0
                } else {
                    val r = computeQrRotation(corners)
                    log.append(" qr=$r")
                    r
                }
            }
        }

        if (rotatieQr != 0) {
            bitmap = applyRotation(bitmap, rotatieQr)
        }

        if (rotatieExif != 0 || rotatieQr != 0) {
            fisier.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            runCatching {
                val exif = ExifInterface(fisier.absolutePath)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.saveAttributes()
            }
        }
        bitmap.recycle()

        log.toString()
    }

    private fun readExifRotation(fisier: File): Int = runCatching {
        val exif = ExifInterface(fisier.absolutePath)
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun applyRotation(src: Bitmap, gradeCw: Int): Bitmap {
        if (gradeCw % 360 == 0) return src
        val matrix = Matrix().apply { postRotate(gradeCw.toFloat()) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (rotated != src) src.recycle()
        return rotated
    }

    /**
     * Returneaza cele 4 corner points ale QR-ului in spatiul bitmap-ului dat
     * (re-scalate la dimensiunile lui), sau null daca nu detecteaza.
     */
    suspend fun extrageCorners(bitmap: Bitmap): List<Point>? = withContext(Dispatchers.IO) {
        val maxDim = 1280
        val maxLatura = maxOf(bitmap.width, bitmap.height)
        val scale = if (maxLatura > maxDim) maxDim.toFloat() / maxLatura else 1.0f
        val scanBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true,
            )
        } else {
            bitmap
        }

        val image = InputImage.fromBitmap(scanBitmap, 0)
        val barcodes = suspendCancellableCoroutine<List<Barcode>> { cont ->
            scanner.process(image)
                .addOnSuccessListener { cont.resume(it.toList()) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }
        if (scanBitmap != bitmap) scanBitmap.recycle()

        val qr = barcodes.firstOrNull { it.rawValue?.contains("sancris", ignoreCase = true) == true }
            ?: barcodes.firstOrNull()
        val corners = qr?.cornerPoints?.takeIf { it.size == 4 } ?: return@withContext null

        if (scale < 1.0f) {
            val inv = 1.0f / scale
            corners.map { Point((it.x * inv).toInt(), (it.y * inv).toInt()) }
        } else {
            corners.toList()
        }
    }

    private suspend fun scanQR(bitmap: Bitmap): Barcode? {
        // ML Kit pica pe bitmap-uri foarte mari cand QR-ul e rotit. Downscale.
        val maxDim = 1280
        val maxLatura = maxOf(bitmap.width, bitmap.height)
        val scanBitmap = if (maxLatura > maxDim) {
            val scale = maxDim.toFloat() / maxLatura
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true,
            )
        } else {
            bitmap
        }

        val image = InputImage.fromBitmap(scanBitmap, 0)
        val barcodes = suspendCancellableCoroutine<List<Barcode>> { cont ->
            scanner.process(image)
                .addOnSuccessListener { cont.resume(it.toList()) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

        if (scanBitmap != bitmap) scanBitmap.recycle()

        return barcodes.firstOrNull { it.rawValue?.contains("sancris", ignoreCase = true) == true }
            ?: barcodes.firstOrNull()
    }

    /**
     * QR upright: corners[0] (TL) → corners[1] (TR) = orizontal-spre-dreapta (dx>0, dy~0).
     *
     * Daca dy > 0 (TR e sub TL): QR rotit 90° CW in poza → rotim 90° CCW = 270° CW pentru fix.
     * Daca dy < 0 (TR e deasupra TL): QR rotit 90° CCW → rotim 90° CW.
     * Daca dx < 0: 180°.
     */
    private fun computeQrRotation(corners: Array<Point>): Int {
        val dx = corners[1].x - corners[0].x
        val dy = corners[1].y - corners[0].y
        return when {
            abs(dx) > abs(dy) && dx > 0 -> 0
            abs(dy) > abs(dx) && dy > 0 -> 270
            abs(dx) > abs(dy) && dx < 0 -> 180
            abs(dy) > abs(dx) && dy < 0 -> 90
            else -> 0
        }
    }
}
