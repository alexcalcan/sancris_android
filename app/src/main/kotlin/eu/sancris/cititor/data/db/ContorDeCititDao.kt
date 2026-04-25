package eu.sancris.cititor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContorDeCititDao {

    @Query("SELECT * FROM contor_de_citit WHERE sesiuneId = :sesiuneId ORDER BY descriere ASC")
    fun observaPentruSesiune(sesiuneId: Long): Flow<List<ContorDeCitit>>

    @Query("SELECT COUNT(*) FROM contor_de_citit WHERE sesiuneId = :sesiuneId")
    fun observaCount(sesiuneId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM contor_de_citit WHERE sesiuneId = :sesiuneId")
    suspend fun countSync(sesiuneId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM contor_de_citit WHERE sesiuneId = :sesiuneId AND serial = :serial)")
    suspend fun existaInSesiune(sesiuneId: Long, serial: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insereazaToate(contoare: List<ContorDeCitit>)

    /**
     * Lista contoarelor inca nescanate in sesiune (nu apar in citire_queue cu acest sesiuneId).
     */
    @Query("""
        SELECT cdc.* FROM contor_de_citit cdc
        WHERE cdc.sesiuneId = :sesiuneId
          AND cdc.serial NOT IN (
            SELECT DISTINCT cq.serial FROM citire_queue cq WHERE cq.sesiuneId = :sesiuneId
          )
        ORDER BY cdc.descriere ASC
    """)
    fun observaRamase(sesiuneId: Long): Flow<List<ContorDeCitit>>

    @Query("""
        SELECT COUNT(DISTINCT cq.serial) FROM citire_queue cq
        INNER JOIN contor_de_citit cdc
          ON cdc.sesiuneId = cq.sesiuneId AND cdc.serial = cq.serial
        WHERE cq.sesiuneId = :sesiuneId
    """)
    fun observaScanate(sesiuneId: Long): Flow<Int>

    @Query("""
        SELECT COUNT(DISTINCT cq.serial) FROM citire_queue cq
        INNER JOIN contor_de_citit cdc
          ON cdc.sesiuneId = cq.sesiuneId AND cdc.serial = cq.serial
        WHERE cq.sesiuneId = :sesiuneId
    """)
    suspend fun scanatiCountSync(sesiuneId: Long): Int
}
