package eu.sancris.cititor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CitireQueueDao {

    @Query("SELECT * FROM citire_queue ORDER BY createdAt DESC")
    fun observaToate(): Flow<List<CitireQueue>>

    @Query("SELECT COUNT(*) FROM citire_queue")
    fun observaCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM citire_queue WHERE status = '${StatusCitire.NEEDS_REVIEW}'")
    fun observaCountDeRevizuit(): Flow<Int>

    @Query("SELECT * FROM citire_queue WHERE status = '${StatusCitire.NEEDS_REVIEW}' ORDER BY createdAt ASC")
    fun observaDeRevizuit(): Flow<List<CitireQueue>>

    @Query("SELECT * FROM citire_queue WHERE status IN ('${StatusCitire.PENDING}', '${StatusCitire.FAILED}') ORDER BY createdAt ASC")
    suspend fun listaDeTrimis(): List<CitireQueue>

    @Query("UPDATE citire_queue SET status = '${StatusCitire.PENDING}', valoareConfirmata = :valoare, lastError = NULL WHERE id = :id")
    suspend fun confirma(id: Long, valoare: String)

    @Query("SELECT * FROM citire_queue WHERE id = :id")
    suspend fun gasesteId(id: Long): CitireQueue?

    @Insert
    suspend fun insereaza(citire: CitireQueue): Long

    @Query("UPDATE citire_queue SET status = :status, retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun marcheazaEsec(id: Long, status: String = StatusCitire.FAILED, error: String?)

    @Query("UPDATE citire_queue SET status = :status WHERE id = :id")
    suspend fun marcheazaStatus(id: Long, status: String)

    @Query("UPDATE citire_queue SET status = '${StatusCitire.PENDING}', lastError = NULL WHERE id = :id")
    suspend fun resetare(id: Long)

    @Query("UPDATE citire_queue SET status = '${StatusCitire.PENDING}', lastError = NULL WHERE status = '${StatusCitire.FAILED}'")
    suspend fun resetareToateEsuate()

    @Query("DELETE FROM citire_queue WHERE id = :id")
    suspend fun sterge(id: Long)
}
