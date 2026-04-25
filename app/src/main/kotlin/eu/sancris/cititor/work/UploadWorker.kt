package eu.sancris.cititor.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.sancris.cititor.data.ConfigurareRepo
import eu.sancris.cititor.data.UploadRepo
import eu.sancris.cititor.data.db.AppDatabase
import eu.sancris.cititor.data.db.StatusCitire
import kotlinx.coroutines.flow.first
import java.io.File

class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val configurare = ConfigurareRepo(applicationContext).configurareFlow.first()
            ?: return Result.failure() // niciun token configurat — degeaba retry

        val dao = AppDatabase.get(applicationContext).citireQueueDao()
        val deTrimis = dao.listaDeTrimis()
        if (deTrimis.isEmpty()) return Result.success()

        val repo = UploadRepo(configurare)
        var aveaErori = false

        for (citire in deTrimis) {
            dao.marcheazaStatus(citire.id, StatusCitire.UPLOADING)
            val fisier = File(citire.photoPath)
            if (!fisier.exists()) {
                dao.marcheazaEsec(citire.id, StatusCitire.FAILED, "Fisierul a disparut")
                aveaErori = true
                continue
            }

            val rezultat = repo.trimitePoza(fisier, citire.serial, citire.valoareConfirmata)
            rezultat
                .onSuccess {
                    fisier.delete()
                    dao.sterge(citire.id)
                }
                .onFailure { e ->
                    dao.marcheazaEsec(citire.id, StatusCitire.FAILED, e.message?.take(500))
                    aveaErori = true
                }
        }

        // Daca au fost erori, WorkManager va incerca din nou cu backoff exponential.
        return if (aveaErori) Result.retry() else Result.success()
    }
}
