package eu.sancris.cititor.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import eu.sancris.cititor.data.db.AppDatabase
import eu.sancris.cititor.data.db.CitireQueue
import eu.sancris.cititor.data.db.CitireQueueDao
import eu.sancris.cititor.work.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class QueueRepo(private val context: Context) {

    private val dao: CitireQueueDao = AppDatabase.get(context).citireQueueDao()

    val countFlow: Flow<Int> = dao.observaCount()
    val countDeRevizuitFlow: Flow<Int> = dao.observaCountDeRevizuit()
    val toateFlow: Flow<List<CitireQueue>> = dao.observaToate()
    val deRevizuitFlow: Flow<List<CitireQueue>> = dao.observaDeRevizuit()

    /**
     * Salveaza poza in storage privat al app-ului si o pune in coada.
     * Programeaza worker-ul de upload (nu e blocant).
     */
    suspend fun adaugaPentruRevizuit(
        fotografieTemp: File,
        serial: String,
        sesiuneId: Long,
        valoareDetectata: String?,
        debugInfo: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val queueDir = File(context.filesDir, "queue").apply { mkdirs() }
        val destinatie = File(queueDir, "${System.currentTimeMillis()}_${serial}.jpg")
        fotografieTemp.copyTo(destinatie, overwrite = true)
        fotografieTemp.delete()

        // NU programam worker-ul aici — asteptam ca user-ul sa confirme valoarea.
        dao.insereaza(
            CitireQueue(
                serial = serial,
                photoPath = destinatie.absolutePath,
                createdAt = System.currentTimeMillis(),
                sesiuneId = sesiuneId,
                status = eu.sancris.cititor.data.db.StatusCitire.NEEDS_REVIEW,
                valoareDetectata = valoareDetectata,
                debugInfo = debugInfo,
            ),
        )
    }

    /**
     * Marcheaza o citire ca revizuita: salveaza valoarea confirmata si o trimite
     * pe pipeline-ul de upload.
     */
    suspend fun confirmaCitire(id: Long, valoareConfirmata: String) {
        dao.confirma(id, valoareConfirmata)
        programeazaUpload()
    }

    /**
     * Roteste fisierul foto pe disc cu [gradeCw] grade clockwise. Valoare
     * tipica: 90, 180, 270 (ori multiplii).
     */
    suspend fun rotesteFisier(id: Long, gradeCw: Int) = withContext(Dispatchers.IO) {
        if (gradeCw % 360 == 0) return@withContext
        val citire = dao.gasesteId(id) ?: return@withContext
        val fisier = File(citire.photoPath)
        val original = android.graphics.BitmapFactory.decodeFile(fisier.absolutePath) ?: return@withContext
        val matrix = android.graphics.Matrix().apply { postRotate(gradeCw.toFloat()) }
        val rotated = android.graphics.Bitmap.createBitmap(
            original, 0, 0, original.width, original.height, matrix, true,
        )
        fisier.outputStream().use { rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
        rotated.recycle()
        if (!original.isRecycled) original.recycle()
    }

    /** Programeaza upload-ul automat (constraint UNMETERED). */
    private fun programeazaUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UPLOAD_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Sync now — fara constraint, foloseste orice retea disponibila. */
    fun sincronizeazaAcum() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UPLOAD_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    suspend fun resetareEsuata(id: Long) {
        dao.resetare(id)
        programeazaUpload()
    }

    suspend fun resetareToateEsuate() {
        dao.resetareToateEsuate()
        programeazaUpload()
    }

    suspend fun stergeCitire(id: Long) {
        dao.gasesteId(id)?.let { File(it.photoPath).delete() }
        dao.sterge(id)
    }

    companion object {
        const val UPLOAD_WORK_NAME = "upload_queue"
    }
}
