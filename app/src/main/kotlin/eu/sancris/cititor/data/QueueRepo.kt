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
    val toateFlow: Flow<List<CitireQueue>> = dao.observaToate()

    /**
     * Salveaza poza in storage privat al app-ului si o pune in coada.
     * Programeaza worker-ul de upload (nu e blocant).
     */
    suspend fun adaugaCitire(
        fotografieTemp: File,
        serial: String,
        sesiuneId: Long,
    ): Long = withContext(Dispatchers.IO) {
        val queueDir = File(context.filesDir, "queue").apply { mkdirs() }
        val destinatie = File(queueDir, "${System.currentTimeMillis()}_${serial}.jpg")
        fotografieTemp.copyTo(destinatie, overwrite = true)
        fotografieTemp.delete()

        val id = dao.insereaza(
            CitireQueue(
                serial = serial,
                photoPath = destinatie.absolutePath,
                createdAt = System.currentTimeMillis(),
                sesiuneId = sesiuneId,
            ),
        )
        programeazaUpload()
        id
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
