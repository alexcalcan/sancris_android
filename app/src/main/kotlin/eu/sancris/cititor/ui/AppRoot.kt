package eu.sancris.cititor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.sancris.cititor.data.ConfigurareRepo

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val repo = remember { ConfigurareRepo(context) }
    val configurare by repo.configurareFlow.collectAsState(initial = null)
    var fortareConfigurare by remember { mutableStateOf(false) }

    when {
        configurare == null || fortareConfigurare -> ProvisioningScreen(
            repo = repo,
            onConfigurat = { fortareConfigurare = false },
        )
        else -> CameraScreen(
            configurare = configurare!!,
            onReConfigureaza = { fortareConfigurare = true },
        )
    }
}
