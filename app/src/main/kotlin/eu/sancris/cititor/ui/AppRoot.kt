package eu.sancris.cititor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.sancris.cititor.BuildConfig
import eu.sancris.cititor.data.ConfigurareRepo
import eu.sancris.cititor.data.QueueRepo
import eu.sancris.cititor.data.RezultatSesiuneNoua
import eu.sancris.cititor.data.SesiuneRepo
import eu.sancris.cititor.data.UpdateChecker
import eu.sancris.cititor.data.UpdateInstaller
import kotlinx.coroutines.launch
import java.io.File

private enum class Ecran { Camera, Queue, ContoareRamase, Review }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configRepo = remember { ConfigurareRepo(context) }
    val queueRepo = remember { QueueRepo(context) }
    val sesiuneRepo = remember { SesiuneRepo(context) }
    val configurare by configRepo.configurareFlow.collectAsState(initial = null)
    var ecran by remember { mutableStateOf(Ecran.Camera) }
    var updateState by remember { mutableStateOf<UpdateUiState?>(null) }
    var sesiuneStare by remember { mutableStateOf<StareSesiuneStart?>(null) }
    var sesiunePrima by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching {
            UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
        }.getOrNull()?.let { info ->
            updateState = UpdateUiState.Available(info)
        }
    }

    // La prima compose dupa ce avem configurare (si la fiecare reconfigurare), decide daca cerem dialog de sesiune.
    LaunchedEffect(configurare) {
        if (configurare == null) {
            sesiunePrima = true
        } else if (sesiunePrima) {
            sesiunePrima = false
            val snap = sesiuneRepo.snapshot()
            sesiuneStare = StareSesiuneStart.Decizie(
                areActiva = snap.areActiva && snap.total > 0,
                scanate = snap.scanate,
                total = snap.total,
            )
        }
    }

    fun pornesteSesiune() {
        sesiuneStare = StareSesiuneStart.Pornire
        scope.launch {
            when (val rez = sesiuneRepo.pornesteSesiuneNoua()) {
                is RezultatSesiuneNoua.Reusit -> sesiuneStare = null
                is RezultatSesiuneNoua.EroareRetea -> sesiuneStare = StareSesiuneStart.Eroare(rez.mesaj)
                RezultatSesiuneNoua.FaraConfigurare -> sesiuneStare = null
            }
        }
    }

    if (configurare == null) {
        ProvisioningScreen(
            repo = configRepo,
            onConfigurat = { /* flow-ul din DataStore tranzitioneaza automat */ },
        )
    } else when (ecran) {
        Ecran.Camera -> CameraScreen(
            queueRepo = queueRepo,
            sesiuneRepo = sesiuneRepo,
            onLogout = { scope.launch { configRepo.sterge() } },
            onSesiuneNoua = { sesiuneStare = StareSesiuneStart.Decizie(areActiva = false, scanate = 0, total = 0) },
            onOpenQueue = { ecran = Ecran.Queue },
            onOpenReview = { ecran = Ecran.Review },
            onOpenContoareSesiune = { ecran = Ecran.ContoareRamase },
        )
        Ecran.Queue -> QueueScreen(
            queueRepo = queueRepo,
            onBack = { ecran = Ecran.Camera },
        )
        Ecran.ContoareRamase -> ContoareRamaseScreen(
            sesiuneRepo = sesiuneRepo,
            onBack = { ecran = Ecran.Camera },
        )
        Ecran.Review -> ReviewScreen(
            queueRepo = queueRepo,
            onBack = { ecran = Ecran.Camera },
        )
    }

    sesiuneStare?.let { stare ->
        SesiuneStartDialog(
            stare = stare,
            onContinua = { sesiuneStare = null },
            onSesiuneNoua = ::pornesteSesiune,
            onDismiss = { sesiuneStare = null },
        )
    }

    updateState?.let { state ->
        UpdateDialog(
            state = state,
            onDismiss = { updateState = null },
            onOpenSettings = {
                UpdateInstaller.openInstallSourceSettings(context)
                updateState = null
            },
            onInstall = {
                val info = state.info
                if (!UpdateInstaller.canInstall(context)) {
                    updateState = UpdateUiState.NeedsPermission(info)
                } else {
                    updateState = UpdateUiState.Downloading(info)
                    scope.launch {
                        val apkFile = File(context.cacheDir, "update.apk").apply {
                            if (exists()) delete()
                        }
                        val ok = runCatching { UpdateChecker.downloadApk(info.apkUrl, apkFile) }.getOrDefault(false)
                        if (ok) {
                            UpdateInstaller.installApk(context, apkFile)
                        }
                        updateState = null
                    }
                }
            },
        )
    }
}
