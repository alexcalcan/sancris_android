package eu.sancris.cititor.data

import android.content.Context
import eu.sancris.cititor.data.db.AppDatabase
import eu.sancris.cititor.data.db.ContorDeCitit
import eu.sancris.cititor.data.db.Sesiune
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

sealed interface RezultatSesiuneNoua {
    data class Reusit(val sesiuneId: Long, val numarContoare: Int) : RezultatSesiuneNoua
    data object FaraConfigurare : RezultatSesiuneNoua
    data class EroareRetea(val mesaj: String) : RezultatSesiuneNoua
}

data class SnapshotSesiune(
    val areActiva: Boolean,
    val scanate: Int,
    val total: Int,
)

class SesiuneRepo(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val sesiuneDao = db.sesiuneDao()
    private val contorDao = db.contorDeCititDao()
    private val configRepo = ConfigurareRepo(context)

    val activaFlow: Flow<Sesiune?> = sesiuneDao.observaActiva()

    /**
     * X = serial-uri din lista oficiala scanate in sesiune (DISTINCT).
     */
    fun observaScanate(): Flow<Int> = activaFlow.flatMapLatest { sesiune ->
        if (sesiune == null) flowOf(0) else contorDao.observaScanate(sesiune.id)
    }

    /** Y = total contoare manuale in sesiune. */
    fun observaTotal(): Flow<Int> = activaFlow.flatMapLatest { sesiune ->
        if (sesiune == null) flowOf(0) else contorDao.observaCount(sesiune.id)
    }

    fun observaRamase(): Flow<List<ContorDeCitit>> = activaFlow.flatMapLatest { sesiune ->
        if (sesiune == null) flowOf(emptyList()) else contorDao.observaRamase(sesiune.id)
    }

    fun observaToate(): Flow<List<ContorDeCitit>> = activaFlow.flatMapLatest { sesiune ->
        if (sesiune == null) flowOf(emptyList()) else contorDao.observaPentruSesiune(sesiune.id)
    }

    /**
     * @return true daca [serial] face parte din lista contoarelor sesiunii curente.
     */
    suspend fun esteValid(serial: String): Boolean {
        val sesiune = sesiuneDao.gasesteActiva() ?: return false
        return contorDao.existaInSesiune(sesiune.id, serial)
    }

    suspend fun gasesteActiva(): Sesiune? = sesiuneDao.gasesteActiva()

    suspend fun snapshot(): SnapshotSesiune {
        val activa = sesiuneDao.gasesteActiva() ?: return SnapshotSesiune(false, 0, 0)
        return SnapshotSesiune(
            areActiva = true,
            scanate = contorDao.scanatiCountSync(activa.id),
            total = contorDao.countSync(activa.id),
        )
    }

    /**
     * Inchide sesiunile active si fetch-uieste lista de contoare pentru o sesiune noua.
     * Necesita configurare valida + retea.
     */
    suspend fun pornesteSesiuneNoua(): RezultatSesiuneNoua = withContext(Dispatchers.IO) {
        val configurare = configRepo.configurareFlow.first()
            ?: return@withContext RezultatSesiuneNoua.FaraConfigurare

        val api = ApiBuilder.build(configurare.urlBaza)
        val raspuns = runCatching { api.contoareDeCitit(configurare.token) }
            .getOrElse { e ->
                return@withContext RezultatSesiuneNoua.EroareRetea(e.message ?: "Eroare retea")
            }

        if (!raspuns.isSuccessful) {
            return@withContext RezultatSesiuneNoua.EroareRetea("HTTP ${raspuns.code()}")
        }
        val lista = raspuns.body()?.contoare.orEmpty()

        sesiuneDao.inchideToateActive()
        val sesiuneId = sesiuneDao.creeaza(
            Sesiune(startedAt = System.currentTimeMillis()),
        )
        contorDao.insereazaToate(
            lista.map { ContorDeCitit(serial = it.serial, descriere = it.descriere, sesiuneId = sesiuneId) },
        )
        RezultatSesiuneNoua.Reusit(sesiuneId, lista.size)
    }
}
