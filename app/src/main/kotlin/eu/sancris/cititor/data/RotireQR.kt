package eu.sancris.cititor.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.BinaryBitmap
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

object RotireQR {

    /**
     * Indrepta poza:
     * 1. EXIF normalize (CameraX salveaza pixelii in sensor-orientation cu tag).
     * 2. ZXing decoding pentru a obtine pozitiile celor 3 finder patterns
     *    (TL, TR, BL ale QR-ului — orientation-bearing). Vectorul TL→TR
     *    determina rotatia ramasa.
     * 3. Crop la patrat centrat.
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

        val rotatieQr = detecteazaRotatieZxing(bitmap)
        if (rotatieQr == null) {
            log.append(" zx=miss")
        } else {
            log.append(" zx=$rotatieQr")
            if (rotatieQr != 0) {
                bitmap = applyRotation(bitmap, rotatieQr)
            }
        }

        // Crop la patrat centrat.
        val side = minOf(bitmap.width, bitmap.height)
        val cropX = (bitmap.width - side) / 2
        val cropY = (bitmap.height - side) / 2
        val squared = Bitmap.createBitmap(bitmap, cropX, cropY, side, side)
        if (squared != bitmap) {
            bitmap.recycle()
            bitmap = squared
        }
        log.append(" sq=${side}x${side}")

        fisier.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        runCatching {
            val exif = ExifInterface(fisier.absolutePath)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            exif.saveAttributes()
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
     * Foloseste ZXing pentru a decoda QR-ul si a extrage cele 3 finder patterns
     * (TL, TR, BL — orientation-bearing). Vectorul TL→TR determina rotatia
     * necesara ca QR-ul sa devina upright.
     *
     * ZXing's resultPoints pentru QR: [bottom-left, top-left, top-right]
     */
    private fun detecteazaRotatieZxing(bitmap: Bitmap): Int? {
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

        val rotatie = try {
            val pixels = IntArray(scanBitmap.width * scanBitmap.height)
            scanBitmap.getPixels(pixels, 0, scanBitmap.width, 0, 0, scanBitmap.width, scanBitmap.height)
            val source = RGBLuminanceSource(scanBitmap.width, scanBitmap.height, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            val reader = QRCodeReader()
            val result = reader.decode(binary)

            val points = result.resultPoints
            if (points.size < 3) {
                null
            } else {
                val tl = points[1]
                val tr = points[2]
                val dx = tr.x - tl.x
                val dy = tr.y - tl.y
                when {
                    abs(dx) > abs(dy) && dx > 0 -> 0       // upright
                    abs(dy) > abs(dx) && dy > 0 -> 270     // QR right -> down → rotate 90° CCW
                    abs(dx) > abs(dy) && dx < 0 -> 180
                    abs(dy) > abs(dx) && dy < 0 -> 90      // QR right -> up → rotate 90° CW
                    else -> 0
                }
            }
        } catch (e: NotFoundException) {
            null
        } catch (e: Throwable) {
            null
        } finally {
            if (scanBitmap != bitmap) scanBitmap.recycle()
        }

        return rotatie
    }
}
