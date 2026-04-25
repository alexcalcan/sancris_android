package eu.sancris.cititor.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object StatusCitire {
    const val PENDING = "pending"
    const val UPLOADING = "uploading"
    const val FAILED = "failed"
}

@Entity(tableName = "citire_queue")
data class CitireQueue(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serial: String,
    val photoPath: String,
    val createdAt: Long,
    val status: String = StatusCitire.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
)
