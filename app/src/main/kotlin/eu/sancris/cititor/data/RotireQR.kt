package eu.sancris.cititor.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RotireQR {

    /**
     * Aplica EXIF orientation pe pixelii fisierului si reseteaza tag-ul EXIF
     * la NORMAL. Returneaza un string de debug cu ce a facut.
     */
    suspend fun rotesteDupaQR(fisier: File): String = withContext(Dispatchers.IO) {
        val rotatie = readExifRotation(fisier)

        // Citire dimensiuni initiale pentru debug.
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(fisier.absolutePath, opts)
        val w = opts.outWidth
        val h = opts.outHeight

        if (rotatie == 0) {
            return@withContext "exif=0 raw=${w}x${h}"
        }

        val raw = BitmapFactory.decodeFile(fisier.absolutePath)
            ?: return@withContext "exif=$rotatie decode=failed"
        val matrix = Matrix().apply { postRotate(rotatie.toFloat()) }
        val rotita = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)

        fisier.outputStream().use { out ->
            rotita.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (rotita != raw) raw.recycle()
        rotita.recycle()

        runCatching {
            val exif = ExifInterface(fisier.absolutePath)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            exif.saveAttributes()
        }

        "exif=$rotatie raw=${w}x${h} aplicat"
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
}
