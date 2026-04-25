package eu.sancris.cititor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.sancris.cititor.data.UpdateInfo

sealed interface UpdateUiState {
    val info: UpdateInfo

    data class Available(override val info: UpdateInfo) : UpdateUiState
    data class Downloading(override val info: UpdateInfo) : UpdateUiState
    data class NeedsPermission(override val info: UpdateInfo) : UpdateUiState
}

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Versiune nouă disponibilă") },
            text = {
                Text("Versiunea ${state.info.versionName} e disponibilă pentru instalare. Vrei să o instalezi acum?")
            },
            confirmButton = {
                TextButton(onClick = onInstall) { Text("Instalează") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Mai târziu") }
            },
        )
        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Se descarcă...") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Versiunea ${state.info.versionName}")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            },
            confirmButton = {},
        )
        is UpdateUiState.NeedsPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Permisiune necesară") },
            text = {
                Text("Pentru instalarea automată, activează \"Permite din această sursă\" în setările telefonului.")
            },
            confirmButton = {
                TextButton(onClick = onOpenSettings) { Text("Setări") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Anulează") }
            },
        )
    }
}
