package eu.sancris.cititor.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object StatusSesiune {
    const val ACTIVA = "activa"
    const val INCHISA = "inchisa"
}

@Entity(tableName = "sesiune")
data class Sesiune(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startedAt: Long,
    val status: String = StatusSesiune.ACTIVA,
)
