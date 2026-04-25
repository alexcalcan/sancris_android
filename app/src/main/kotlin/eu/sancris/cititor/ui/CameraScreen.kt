package eu.sancris.cititor.ui

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import eu.sancris.cititor.camera.AnalizatorQR
import eu.sancris.cititor.data.Configurare
import eu.sancris.cititor.data.UploadRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

private sealed interface StareUpload {
    data object Inactiv : StareUpload
    data object Capturare : StareUpload
    data object Trimitere : StareUpload
    data class Succes(val id: Long) : StareUpload
    data class Eroare(val mesaj: String) : StareUpload
}

@Composable
fun CameraScreen(
    configurare: Configurare,
    onReConfigureaza: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val repo = remember(configurare) { UploadRepo(configurare) }

    var serialDetectat by remember { mutableStateOf<String?>(null) }
    var flashAprins by remember { mutableStateOf(false) }
    var stareUpload by remember { mutableStateOf<StareUpload>(StareUpload.Inactiv) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(flashAprins, camera) {
        camera?.cameraControl?.enableTorch(flashAprins)
    }

    // Reset feedback de succes/eroare dupa 2.5s.
    LaunchedEffect(stareUpload) {
        if (stareUpload is StareUpload.Succes || stareUpload is StareUpload.Eroare) {
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

        // Border verde cand serialul e detectat.
        if (serialDetectat != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .border(4.dp, Color(0xFF22C55E), RoundedCornerShape(20.dp))
            )
        }

        // Etichetă sus cu starea detecției.
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

            IconButton(
                onClick = onReConfigureaza,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Setări", tint = Color.White)
            }
        }

        // Feedback upload (toast-like).
        FeedbackOverlay(stareUpload)

        // Bottom bar: flash + shutter + spacer.
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
                    capturareasiTrimitere(
                        context = context,
                        imageCapture = imageCapture,
                        serial = serial,
                        repo = repo,
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
private fun BoxScope.FeedbackOverlay(stare: StareUpload) {
    val mesaj: String?
    val culoare: Color
    when (stare) {
        StareUpload.Inactiv -> { mesaj = null; culoare = Color.Transparent }
        StareUpload.Capturare -> { mesaj = "Capturare..."; culoare = Color.Black.copy(alpha = 0.7f) }
        StareUpload.Trimitere -> { mesaj = "Trimit..."; culoare = Color.Black.copy(alpha = 0.7f) }
        is StareUpload.Succes -> { mesaj = "Trimisă (#${stare.id})"; culoare = Color(0xFF166534).copy(alpha = 0.92f) }
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

private fun capturareasiTrimitere(
    context: Context,
    imageCapture: ImageCapture,
    serial: String,
    repo: UploadRepo,
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
                onStare(StareUpload.Trimitere)
                scope.launch {
                    val rezultat = withContext(Dispatchers.IO) { repo.trimitePoza(fisier, serial) }
                    fisier.delete()
                    rezultat
                        .onSuccess { id -> onStare(StareUpload.Succes(id)) }
                        .onFailure { e -> onStare(StareUpload.Eroare(e.message ?: "Eroare necunoscută")) }
                }
            }
        },
    )
}
