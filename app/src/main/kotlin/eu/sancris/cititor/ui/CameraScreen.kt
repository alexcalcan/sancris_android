package eu.sancris.cititor.ui

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import eu.sancris.cititor.BuildConfig
import eu.sancris.cititor.camera.AnalizatorQR
import eu.sancris.cititor.data.QueueRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

private sealed interface StareUpload {
    data object Inactiv : StareUpload
    data object Capturare : StareUpload
    data class Salvat(val id: Long) : StareUpload
    data class Eroare(val mesaj: String) : StareUpload
}

@Composable
fun CameraScreen(
    queueRepo: QueueRepo,
    onLogout: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val countInQueue by queueRepo.countFlow.collectAsState(initial = 0)

    var serialDetectat by remember { mutableStateOf<String?>(null) }
    var flashAprins by remember { mutableStateOf(false) }
    var stareUpload by remember { mutableStateOf<StareUpload>(StareUpload.Inactiv) }
    var meniuDeschis by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(flashAprins, camera) {
        camera?.cameraControl?.enableTorch(flashAprins)
    }

    LaunchedEffect(stareUpload) {
        if (stareUpload is StareUpload.Salvat || stareUpload is StareUpload.Eroare) {
            delay(2500)
            stareUpload = StareUpload.Inactiv
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                    val analiza = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { use ->
                            use.setAnalyzer(
                                executor,
                                AnalizatorQR(
                                    onSerialDetectat = { s -> serialDetectat = s },
                                    onNiciunQR = { /* pastram ultimul serial detectat — nu reseteaza */ },
                                ),
                            )
                        }
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analiza,
                        imageCapture,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        if (serialDetectat != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .border(4.dp, Color(0xFF22C55E), RoundedCornerShape(20.dp))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (serialDetectat != null) Color(0xFF166534).copy(alpha = 0.92f)
                        else Color.Black.copy(alpha = 0.6f)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = serialDetectat?.let { "QR detectat: $it" } ?: "Caut QR contor...",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (countInQueue > 0) {
                    QueueBadge(count = countInQueue, onClick = onOpenQueue)
                    Spacer(modifier = Modifier.size(8.dp))
                }

                Box {
                    IconButton(onClick = { meniuDeschis = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Setări", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = meniuDeschis,
                        onDismissRequest = { meniuDeschis = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sincronizează acum") },
                            leadingIcon = {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                            },
                            onClick = {
                                meniuDeschis = false
                                queueRepo.sincronizeazaAcum()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            },
                            onClick = {
                                meniuDeschis = false
                                onLogout()
                            },
                        )
                        HorizontalDivider()
                        Text(
                            text = "V${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_DATE}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = Color.Gray,
                        )
                    }
                }
            }
        }

        FeedbackOverlay(stareUpload)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                .align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ButonFlash(flashAprins) { flashAprins = !flashAprins }

            ButonShutter(
                activ = serialDetectat != null && stareUpload is StareUpload.Inactiv,
                onClick = {
                    val serial = serialDetectat ?: return@ButonShutter
                    stareUpload = StareUpload.Capturare
                    capturareasiSalvare(
                        context = context,
                        imageCapture = imageCapture,
                        serial = serial,
                        queueRepo = queueRepo,
                        onStare = { stareUpload = it },
                        scope = scope,
                    )
                },
            )

            Box(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun QueueBadge(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFFB45309))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = "$count în așteptare",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun BoxScope.FeedbackOverlay(stare: StareUpload) {
    val mesaj: String?
    val culoare: Color
    when (stare) {
        StareUpload.Inactiv -> { mesaj = null; culoare = Color.Transparent }
        StareUpload.Capturare -> { mesaj = "Capturare..."; culoare = Color.Black.copy(alpha = 0.7f) }
        is StareUpload.Salvat -> { mesaj = "Salvată local — se trimite când e WiFi"; culoare = Color(0xFF166534).copy(alpha = 0.92f) }
        is StareUpload.Eroare -> { mesaj = stare.mesaj; culoare = Color(0xFF991B1B).copy(alpha = 0.92f) }
    }
    if (mesaj != null) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(12.dp))
                .background(culoare)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(mesaj, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ButonFlash(aprins: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (aprins) Color(0xFFEAB308) else Color.Black.copy(alpha = 0.5f)),
    ) {
        Icon(
            imageVector = if (aprins) Icons.Default.FlashOn else Icons.Default.FlashOff,
            contentDescription = "Flash",
            tint = Color.White,
        )
    }
}

@Composable
private fun ButonShutter(activ: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(if (activ) Color.White else Color.White.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = { if (activ) onClick() },
            enabled = activ,
            modifier = Modifier.size(70.dp),
        ) {
            Icon(
                Icons.Default.Camera,
                contentDescription = "Fă poza",
                tint = if (activ) Color.Black else Color.Gray,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

private fun capturareasiSalvare(
    context: Context,
    imageCapture: ImageCapture,
    serial: String,
    queueRepo: QueueRepo,
    onStare: (StareUpload) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val fisier = File(context.cacheDir, "citire_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(fisier).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                onStare(StareUpload.Eroare("Capturare eșuată: ${exception.message}"))
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                scope.launch {
                    runCatching { queueRepo.adaugaCitire(fisier, serial) }
                        .onSuccess { id -> onStare(StareUpload.Salvat(id)) }
                        .onFailure { e -> onStare(StareUpload.Eroare(e.message ?: "Salvare eșuată")) }
                }
            }
        },
    )
}
