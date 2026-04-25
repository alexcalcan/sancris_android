package eu.sancris.cititor.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import eu.sancris.cititor.data.Configurare
import eu.sancris.cititor.data.ConfigurareRepo
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProvisioningScreen(
    repo: ConfigurareRepo,
    onConfigurat: () -> Unit,
) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val scope = rememberCoroutineScope()
    var modManual by remember { mutableStateOf(false) }
    var eroare by remember { mutableStateOf<String?>(null) }

    val onConfigurareDetectata: (Configurare) -> Unit = { config ->
        scope.launch {
            repo.salveaza(config)
            onConfigurat()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (modManual) {
            ConfigurareManualaUi(
                onSalveaza = onConfigurareDetectata,
                onAnuleaza = { modManual = false },
            )
        } else if (cameraPermission.status.isGranted) {
            ScannerProvisioningUi(
                repo = repo,
                onConfigurareDetectata = onConfigurareDetectata,
                onModManual = { modManual = true },
                onEroare = { eroare = it },
                eroare = eroare,
            )
        } else {
            CerePermisiuneCameraUi(onCere = { cameraPermission.launchPermissionRequest() })
        }
    }
}

@Composable
private fun CerePermisiuneCameraUi(onCere: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Aplicația are nevoie de cameră",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pentru a scana QR-ul de configurare și apoi codurile contoarelor.",
            color = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCere) { Text("Permite acces cameră") }
    }
}

@Composable
private fun ScannerProvisioningUi(
    repo: ConfigurareRepo,
    onConfigurareDetectata: (Configurare) -> Unit,
    onModManual: () -> Unit,
    onEroare: (String) -> Unit,
    eroare: String?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detectat by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                    )
                    val analiza = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { use ->
                            use.setAnalyzer(executor) { proxy ->
                                @Suppress("UnsafeOptInUsageError")
                                val media = proxy.image
                                if (media != null && !detectat) {
                                    val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                    scanner.process(img)
                                        .addOnSuccessListener { coduri ->
                                            val raw = coduri.firstNotNullOfOrNull { it.rawValue }
                                            if (raw != null) {
                                                val parsed = repo.parseazaUriConfigurare(raw)
                                                if (parsed != null && !detectat) {
                                                    detectat = true
                                                    onConfigurareDetectata(parsed)
                                                } else if (parsed == null) {
                                                    onEroare("QR scanat dar nu este de configurare Sancris")
                                                }
                                            }
                                        }
                                        .addOnCompleteListener { proxy.close() }
                                } else {
                                    proxy.close()
                                }
                            }
                        }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analiza,
                    )
                }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Scanează QR-ul de configurare",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Generat din /admin/cititoare în panoul Sancris",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
            )
            if (eroare != null) {
                Spacer(Modifier.height(12.dp))
                Text(eroare, color = Color(0xFFFCA5A5), fontSize = 13.sp)
            }
        }

        Button(
            onClick = onModManual,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
        ) { Text("Configurare manuală") }
    }
}

@Composable
private fun ConfigurareManualaUi(
    onSalveaza: (Configurare) -> Unit,
    onAnuleaza: () -> Unit,
) {
    var urlBaza by remember { mutableStateOf("https://clients.sancris.eu") }
    var token by remember { mutableStateOf("") }
    var nume by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Configurare manuală", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = urlBaza,
            onValueChange = { urlBaza = it },
            label = { Text("URL bază") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nume,
            onValueChange = { nume = it },
            label = { Text("Nume dispozitiv (opțional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAnuleaza) { Text("Anulează") }
            Button(
                onClick = {
                    if (urlBaza.isNotBlank() && token.isNotBlank()) {
                        onSalveaza(Configurare(urlBaza.trimEnd('/'), token.trim(), nume.trim()))
                    }
                },
            ) { Text("Salvează") }
        }
    }
}
