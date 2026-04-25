package eu.sancris.cititor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.sancris.cititor.data.QueueRepo
import eu.sancris.cititor.data.db.CitireQueue
import eu.sancris.cititor.data.db.StatusCitire
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    queueRepo: QueueRepo,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toate by queueRepo.toateFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coada de trimitere") },
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
        if (toate.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Niciun upload în așteptare",
                    fontSize = 16.sp,
                    color = Color.Gray,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(toate, key = { it.id }) { citire ->
                    CardCitire(
                        citire = citire,
                        onRetry = { scope.launch { queueRepo.resetareEsuata(citire.id) } },
                        onDelete = { scope.launch { queueRepo.stergeCitire(citire.id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun CardCitire(
    citire: CitireQueue,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val (eticheta, culoare) = when (citire.status) {
        StatusCitire.PENDING -> "În așteptare" to Color(0xFF6B7280)
        StatusCitire.UPLOADING -> "Se trimite..." to Color(0xFF2563EB)
        StatusCitire.FAILED -> "Eșuat" to Color(0xFFB91C1C)
        else -> citire.status to Color(0xFF6B7280)
    }

    val data = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(citire.createdAt))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = citire.serial,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = data,
                fontSize = 12.sp,
                color = Color.Gray,
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(culoare.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = eticheta,
                    fontSize = 11.sp,
                    color = culoare,
                    fontWeight = FontWeight.Medium,
                )
            }
            citire.lastError?.takeIf { citire.status == StatusCitire.FAILED }?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = Color(0xFF991B1B),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (citire.status == StatusCitire.FAILED) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = "Reîncearcă")
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Șterge")
        }
    }
}
