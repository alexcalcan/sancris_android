package eu.sancris.cititor.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.sancris.cititor.data.QueueRepo
import eu.sancris.cititor.data.db.CitireQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    queueRepo: QueueRepo,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val deRevizuit by queueRepo.deRevizuitFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Revizuire (${deRevizuit.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Înapoi")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (deRevizuit.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nimic de revizuit.", fontSize = 16.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack) { Text("Înapoi la cameră") }
                }
            }
        } else {
            val curent = deRevizuit.first()
            CardCitireRevizuire(
                citire = curent,
                ramase = deRevizuit.size,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                onConfirma = { valoare, rotatieCw ->
                    scope.launch {
                        if (rotatieCw != 0) queueRepo.rotesteFisier(curent.id, rotatieCw)
                        queueRepo.confirmaCitire(curent.id, valoare)
                    }
                },
                onSterge = {
                    scope.launch { queueRepo.stergeCitire(curent.id) }
                },
            )
        }
    }
}

@Composable
private fun CardCitireRevizuire(
    citire: CitireQueue,
    ramase: Int,
    modifier: Modifier = Modifier,
    onConfirma: (valoare: String, rotatieCw: Int) -> Unit,
    onSterge: () -> Unit,
) {
    var valoare by remember(citire.id) { mutableStateOf("") }
    var rotatie by remember(citire.id) { mutableStateOf(0) }
    var apasariRotire by remember(citire.id) { mutableStateOf(0) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = citire.serial,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    rotatie = (rotatie + 90) % 360
                    apasariRotire++
                },
            ) {
                Icon(
                    Icons.Default.Rotate90DegreesCw,
                    contentDescription = "Rotește 90°",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = buildString {
                    append("DBG: ")
                    append(citire.debugInfo ?: "—")
                    if (apasariRotire > 0) {
                        append(" | manual: ${apasariRotire}× (=${rotatie}°)")
                    }
                },
                color = Color.Yellow,
                fontSize = 11.sp,
            )
        }

        val bitmap by produceState<ImageBitmap?>(initialValue = null, citire.photoPath) {
            value = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(citire.photoPath)?.asImageBitmap() }.getOrNull()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Poză contor",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotatie.toFloat()),
                )
            } ?: Text("Se încarcă...", color = Color.White)
        }

        OutlinedTextField(
            value = valoare,
            onValueChange = { nou -> valoare = nou.filter { it.isDigit() } },
            label = { Text("Valoare contor") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onConfirma(valoare, rotatie) },
            enabled = valoare.isNotBlank() && valoare.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                text = if (ramase > 1) "Confirmă — următorul ($ramase)" else "Confirmă și termină",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }

        Button(
            onClick = onSterge,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Aruncă poza (n-o trimit)")
        }
    }
}
