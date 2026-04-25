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
import eu.sancris.cititor.data.UpdateChecker
import eu.sancris.cititor.data.UpdateInstaller
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ConfigurareRepo(context) }
    val configurare by repo.configurareFlow.collectAsState(initial = null)
    var updateState by remember { mutableStateOf<UpdateUiState?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
        }.getOrNull()?.let { info ->
            updateState = UpdateUiState.Available(info)
        }
    }

    if (configurare == null) {
        ProvisioningScreen(
            repo = repo,
            onConfigurat = { /* flow-ul din DataStore tranzitioneaza automat */ },
        )
    } else {
        CameraScreen(
            configurare = configurare!!,
            onLogout = { scope.launch { repo.sterge() } },
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
