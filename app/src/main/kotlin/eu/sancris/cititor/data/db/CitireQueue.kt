package eu.sancris.cititor.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object StatusCitire {
    const val NEEDS_REVIEW = "needs_review"
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
    val status: String = StatusCitire.NEEDS_REVIEW,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val sesiuneId: Long = 0,
    val valoareDetectata: String? = null,
    val valoareConfirmata: String? = null,
    val debugInfo: String? = null,
)
