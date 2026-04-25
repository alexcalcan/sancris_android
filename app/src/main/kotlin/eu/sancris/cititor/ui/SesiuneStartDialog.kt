package eu.sancris.cititor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed interface StareSesiuneStart {
    data class Decizie(val areActiva: Boolean, val scanate: Int, val total: Int) : StareSesiuneStart
    data object Pornire : StareSesiuneStart
    data class Eroare(val mesaj: String) : StareSesiuneStart
}

@Composable
fun SesiuneStartDialog(
    stare: StareSesiuneStart,
    onContinua: () -> Unit,
    onSesiuneNoua: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (stare) {
        is StareSesiuneStart.Decizie -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (stare.areActiva) "Sesiune activă" else "Începe scanarea") },
            text = {
                Text(
                    if (stare.areActiva) {
                        "Există o sesiune cu ${stare.scanate}/${stare.total} contoare scanate. " +
                            "Vrei să o continui sau să începi una nouă?"
                    } else {
                        "Pornim o sesiune nouă de scanare? Avem nevoie de conexiune la internet ca să descărcăm lista de contoare."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onSesiuneNoua) { Text("Sesiune nouă") }
            },
            dismissButton = if (stare.areActiva) {
                { TextButton(onClick = onContinua) { Text("Continuă") } }
            } else null,
        )

        is StareSesiuneStart.Pornire -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Pornesc sesiunea...") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Descarc lista de contoare de la server.")
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {},
        )

        is StareSesiuneStart.Eroare -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Conexiune eșuată") },
            text = {
                Text(
                    "Nu am putut sincroniza lista de contoare:\n${stare.mesaj}\n\n" +
                        "Conectează telefonul la internet și încearcă din nou.",
                )
            },
            confirmButton = {
                TextButton(onClick = onSesiuneNoua) { Text("Încearcă din nou") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Închide") }
            },
        )
    }
}
