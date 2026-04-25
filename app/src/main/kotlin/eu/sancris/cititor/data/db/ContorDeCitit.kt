package eu.sancris.cititor.data.db

import androidx.room.Entity

@Entity(tableName = "contor_de_citit", primaryKeys = ["serial", "sesiuneId"])
data class ContorDeCitit(
    val serial: String,
    val descriere: String,
    val sesiuneId: Long,
)
