package eu.sancris.cititor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.sancris.cititor.data.SesiuneRepo
import eu.sancris.cititor.data.db.ContorDeCitit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContoareRamaseScreen(
    sesiuneRepo: SesiuneRepo,
    onBack: () -> Unit,
) {
    var tabIndex by remember { mutableStateOf(0) }
    val ramase by sesiuneRepo.observaRamase().collectAsState(initial = emptyList())
    val toate by sesiuneRepo.observaToate().collectAsState(initial = emptyList())
    val ramaseSeriale = remember(ramase) { ramase.map { it.serial }.toSet() }
    val scanate = remember(toate, ramaseSeriale) { toate.filterNot { it.serial in ramaseSeriale } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contoare sesiune") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Rămase (${ramase.size})") },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("Scanate (${scanate.size})") },
                )
            }

            val lista = if (tabIndex == 0) ramase else scanate
            val gol = lista.isEmpty()

            if (gol) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (tabIndex == 0) "Toate contoarele scanate!" else "Niciun contor scanat încă.",
                        fontSize = 16.sp,
                        color = Color.Gray,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    items(lista, key = { it.serial }) { c ->
                        CardContor(c, scanat = tabIndex == 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardContor(c: ContorDeCitit, scanat: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (scanat) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (scanat) Color(0xFF16A34A) else Color.Gray,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = c.descriere.ifBlank { c.serial },
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = c.serial,
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }
    }
}
