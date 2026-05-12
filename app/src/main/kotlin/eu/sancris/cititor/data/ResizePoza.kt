package eu.sancris.cititor.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Reduce poza inainte de upload — economisim banda mobila + spatiu pe server.
 *
 * Strategie:
 *  1) decodez cu `inSampleSize` (factor de scaling care evita OOM pe poze 12 MP)
 *  2) aplic Matrix.postScale pentru fine-tuning la dimensiunea tinta
 *  3) corectez rotatia din EXIF (camera Android salveaza poza in landscape +
 *     orientation tag, fara rotatie efectiva pe pixeli)
 *  4) re-encode JPEG la `calitate` setata
 *
 * Pentru OCR de contor (citit numere mari), 1600px latime + quality 80 sunt
 * mai mult decat suficient. Tipic: 12 MP / 3 MB → 1.5 MP / 200-400 KB.
 */
object ResizePoza {
    private const val LATIME_TINTA = 1600
    private const val CALITATE_JPEG = 80

    /**
     * Returneaza un fisier nou cu poza redusa (sau acelasi fisier daca era deja
     * sub LATIME_TINTA si quality compress nu reduce > 5%). Apelantul e
     * responsabil sa-l stearga dupa upload.
     */
    fun scalat(original: File): File {
        // 1) Citesc doar dimensiunile fara a aloca bitmap
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(original.absolutePath, bounds)
        val wOrig = bounds.outWidth
        val hOrig = bounds.outHeight
        if (wOrig <= 0 || hOrig <= 0) {
            return original  // nu pot decoda — las upload-ul cu originalul
        }

        // 2) inSampleSize: cel mai mic put 2 care aduce sub LATIME_TINTA × 2
        val maxDim = max(wOrig, hOrig)
        var sample = 1
        while (maxDim / (sample * 2) >= LATIME_TINTA * 2) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = BitmapFactory.decodeFile(original.absolutePath, opts) ?: return original

        // 3) Scale fin la LATIME_TINTA pe latura mare
        val curMax = max(bitmap.width, bitmap.height)
        if (curMax > LATIME_TINTA) {
            val factor = LATIME_TINTA.toFloat() / curMax
            val nw = (bitmap.width * factor).toInt()
            val nh = (bitmap.height * factor).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        // 4) Aplic rotatia din EXIF (altfel poza ramane sideways la incarcare)
        val unghiRotatie = citesteUnghiRotatie(original)
        if (unghiRotatie != 0) {
            val matrix = Matrix().apply { postRotate(unghiRotatie.toFloat()) }
            val rotit = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotit !== bitmap) bitmap.recycle()
            bitmap = rotit
        }

        // 5) Encode JPEG in fisier nou
        val destinatie = File(original.parentFile, "resized_${original.nameWithoutExtension}.jpg")
        FileOutputStream(destinatie).use { os ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, CALITATE_JPEG, os)
        }
        bitmap.recycle()

        // 6) Decid: dacă rezultatul e cel puțin 20% mai mic, păstrez. Altfel folosesc
        //   originalul (e poză deja mică, n-are sens efortul).
        val raport = destinatie.length().toDouble() / original.length()
        return if (raport < 0.8) destinatie else {
            destinatie.delete()
            original
        }
    }

    private fun citesteUnghiRotatie(file: File): Int {
        return try {
            when (ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Throwable) {
            0
        }
    }
}
