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
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import eu.sancris.cititor.data.SesiuneRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

private sealed interface StareUpload {
    data object Inactiv : StareUpload
    data object Capturare : StareUpload
    data class Salvat(val id: Long) : StareUpload
    data class Invalid(val serial: String) : StareUpload
    data class Eroare(val mesaj: String) : StareUpload
}

@Composable
fun CameraScreen(
    queueRepo: QueueRepo,
    sesiuneRepo: SesiuneRepo,
    onLogout: () -> Unit,
    onSesiuneNoua: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenContoareSesiune: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val countInQueue by queueRepo.countFlow.collectAsState(initial = 0)
    val countDeRevizuit by queueRepo.countDeRevizuitFlow.collectAsState(initial = 0)
    val deRevizuitList by queueRepo.deRevizuitFlow.collectAsState(initial = emptyList())
    val ultimulDebug = deRevizuitList.maxByOrNull { it.createdAt }?.debugInfo
    val sesiune by sesiuneRepo.activaFlow.collectAsState(initial = null)
    val scanate by sesiuneRepo.observaScanate().collectAsState(initial = 0)
    val total by sesiuneRepo.observaTotal().collectAsState(initial = 0)

    var serialDetectat by remember { mutableStateOf<String?>(null) }
    var qrRotatieLive by remember { mutableStateOf<Int?>(null) }
    var cornersLive by remember { mutableStateOf<eu.sancris.cititor.camera.CornersDetectate?>(null) }
    var flashAprins by remember { mutableStateOf(false) }
    var stareUpload by remember { mutableStateOf<StareUpload>(StareUpload.Inactiv) }
    var meniuDeschis by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(flashAprins, camera) {
        camera?.cameraControl?.enableTorch(flashAprins)
    }

    LaunchedEffect(stareUpload) {
        if (stareUpload is StareUpload.Salvat ||
            stareUpload is StareUpload.Eroare ||
            stareUpload is StareUpload.Invalid
        ) {
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
                                    onRotatieDetectata = { r -> qrRotatieLive = r },
                                    onCornersDetectate = { c -> cornersLive = c },
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

        cornersLive?.let { c ->
            CornersOverlay(c, modifier = Modifier.fillMaxSize())
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
                            text = { Text("Sesiune nouă") },
                            leadingIcon = {
                                Icon(Icons.Default.RestartAlt, contentDescription = null)
                            },
                            onClick = {
                                meniuDeschis = false
                                onSesiuneNoua()
                            },
                        )
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 100.dp, start = 12.dp, end = 12.dp)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = buildString {
                    append("DBG: live qr=")
                    append(qrRotatieLive?.let { "${it}°" } ?: "—")
                    ultimulDebug?.let { append(" | last: $it") }
                },
                color = Color.Yellow,
                fontSize = 11.sp,
            )
        }

        if (countDeRevizuit > 0) {
            BannerMergiMaiDeparte(
                count = countDeRevizuit,
                onClick = onOpenReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 140.dp, start = 24.dp, end = 24.dp)
                    .align(Alignment.BottomCenter),
            )
        }

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
                activ = serialDetectat != null && stareUpload is StareUpload.Inactiv && sesiune != null,
                onClick = {
                    val serial = serialDetectat ?: return@ButonShutter
                    val sesiuneActuala = sesiune ?: return@ButonShutter
                    stareUpload = StareUpload.Capturare
                    scope.launch {
                        if (!sesiuneRepo.esteValid(serial)) {
                            stareUpload = StareUpload.Invalid(serial)
                            return@launch
                        }
                        capturareasiSalvare(
                            context = context,
                            imageCapture = imageCapture,
                            serial = serial,
                            sesiuneId = sesiuneActuala.id,
                            qrRotatieHint = qrRotatieLive,
                            queueRepo = queueRepo,
                            onStare = { stareUpload = it },
                            scope = scope,
                        )
                    }
                },
            )

            CounterSesiune(
                scanate = scanate,
                total = total,
                onClick = onOpenContoareSesiune,
            )
        }
    }
}

@Composable
private fun CornersOverlay(
    detectie: eu.sancris.cititor.camera.CornersDetectate,
    modifier: Modifier = Modifier,
) {
    val culori = listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF3B82F6))
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val imgW = detectie.imageWidth.toFloat()
        val imgH = detectie.imageHeight.toFloat()
        // PreviewView FILL_CENTER (default): scale = max(view/img), excesul e cropat.
        val scale = maxOf(viewW / imgW, viewH / imgH)
        val displayedW = imgW * scale
        val displayedH = imgH * scale
        val offsetX = (viewW - displayedW) / 2f
        val offsetY = (viewH - displayedH) / 2f

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            detectie.corners.forEachIndexed { i, p ->
                val cx = offsetX + p.x * scale
                val cy = offsetY + p.y * scale
                drawCircle(
                    color = culori[i % 4],
                    radius = 22f,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                )
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 36f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    canvas.nativeCanvas.drawText("${i + 1}", cx + 24f, cy + 12f, paint)
                }
            }
        }
    }
}

@Composable
private fun BannerMergiMaiDeparte(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2563EB))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Mergi mai departe ($count poză(e) de revizuit) →",
            modifier = Modifier.align(Alignment.Center),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun CounterSesiune(scanate: Int, total: Int, onClick: () -> Unit) {
    val complet = total > 0 && scanate >= total
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (complet) Color(0xFF166534).copy(alpha = 0.92f)
                else Color.Black.copy(alpha = 0.7f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (total == 0) "—" else "$scanate/$total",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
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
        is StareUpload.Salvat -> { mesaj = "Salvată — revizuiește la final"; culoare = Color(0xFF166534).copy(alpha = 0.92f) }
        is StareUpload.Invalid -> { mesaj = "Contor invalid: ${stare.serial}\nNu e în lista sesiunii curente."; culoare = Color(0xFFB45309).copy(alpha = 0.92f) }
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
    sesiuneId: Long,
    qrRotatieHint: Int?,
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
                    val debug = runCatching { eu.sancris.cititor.data.RotireQR.rotesteDupaQR(fisier, qrRotatieHint) }
                        .getOrNull() ?: "rotire failed"
                    runCatching {
                        queueRepo.adaugaPentruRevizuit(fisier, serial, sesiuneId, valoareDetectata = null, debugInfo = debug)
                    }
                        .onSuccess { id -> onStare(StareUpload.Salvat(id)) }
                        .onFailure { e -> onStare(StareUpload.Eroare(e.message ?: "Salvare eșuată")) }
                }
            }
        },
    )
}
