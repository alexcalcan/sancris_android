package eu.sancris.cititor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SesiuneDao {

    @Query("SELECT * FROM sesiune WHERE status = '${StatusSesiune.ACTIVA}' ORDER BY id DESC LIMIT 1")
    suspend fun gasesteActiva(): Sesiune?

    @Query("SELECT * FROM sesiune WHERE status = '${StatusSesiune.ACTIVA}' ORDER BY id DESC LIMIT 1")
    fun observaActiva(): Flow<Sesiune?>

    @Insert
    suspend fun creeaza(sesiune: Sesiune): Long

    @Query("UPDATE sesiune SET status = '${StatusSesiune.INCHISA}' WHERE status = '${StatusSesiune.ACTIVA}'")
    suspend fun inchideToateActive()
}
