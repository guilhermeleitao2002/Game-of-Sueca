package pt.up.fe.asma.sueca.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.up.fe.asma.sueca.engine.Card
import pt.up.fe.asma.sueca.ui.components.MiniCard
import pt.up.fe.asma.sueca.ui.components.Panel
import pt.up.fe.asma.sueca.ui.components.ScreenScaffold
import pt.up.fe.asma.sueca.ui.components.SectionLabel
import pt.up.fe.asma.sueca.ui.theme.Gold
import pt.up.fe.asma.sueca.ui.theme.Positive
import pt.up.fe.asma.sueca.vision.CardScanner
import pt.up.fe.asma.sueca.vision.ScanFrame
import kotlin.math.max

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onUse: (List<Card>) -> Unit,
    viewModel: ScanViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) { if (!granted) request.launch(Manifest.permission.CAMERA) }

    ScreenScaffold(title = "Scan cards", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
            ) {
                if (granted) {
                    CameraPreview(viewModel.scanner, Modifier.fillMaxSize())
                    DetectionOverlay(state.frame, Modifier.fillMaxSize())
                } else {
                    PermissionPrompt { request.launch(Manifest.permission.CAMERA) }
                }
            }

            Panel(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Found ${state.collected.size}")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::clear) { Text("Clear") }
                    }

                    if (state.collected.isEmpty()) {
                        Text(
                            text = "Hold the phone over the cards so the index corners are visible. " +
                                "Each card has to be seen a few frames in a row before it counts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.collected.forEach { card ->
                                MiniCard(card, width = 38.dp, onClick = { viewModel.remove(card) })
                            }
                        }
                        Text(
                            text = "Tap a card to throw it out.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = { onUse(state.collected) },
                        enabled = state.collected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use these ${state.collected.size} cards")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.PhotoCamera, null, Modifier.size(40.dp), tint = Gold)
        Spacer(Modifier.height(12.dp))
        Text("The scanner needs the camera", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Nothing leaves the phone: the model runs on device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Allow camera") }
    }
}

/** CameraX preview with the card scanner wired into the analysis stream. */
@Composable
private fun CameraPreview(scanner: CardScanner, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val bound = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Unbind before the view model gets a chance to close the scanner underneath CameraX.
    DisposableEffect(Unit) {
        onDispose { bound.value?.unbindAll() }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener({
                val provider = providerFuture.get()
                bound.value = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(scanner.executor, scanner) }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(viewContext))

            previewView
        },
    )
}

/**
 * Boxes over whatever the last frame recognised.
 *
 * The analysis frame and the preview rarely have the same aspect ratio, and the preview is
 * cropped to fill, so the boxes are mapped through the same fill-centre transform to end up
 * over the right cards.
 */
@Composable
private fun DetectionOverlay(frame: ScanFrame, modifier: Modifier = Modifier) {
    if (frame.imageWidth == 0 || frame.imageHeight == 0) return

    Canvas(modifier) {
        val scale = max(size.width / frame.imageWidth, size.height / frame.imageHeight)
        val drawnWidth = frame.imageWidth * scale
        val drawnHeight = frame.imageHeight * scale
        val offsetX = (size.width - drawnWidth) / 2f
        val offsetY = (size.height - drawnHeight) / 2f

        for (detection in frame.detections) {
            val left = offsetX + detection.bounds.left * drawnWidth
            val top = offsetY + detection.bounds.top * drawnHeight
            val right = offsetX + detection.bounds.right * drawnWidth
            val bottom = offsetY + detection.bounds.bottom * drawnHeight

            drawRoundRect(
                color = if (detection.confidence > 0.7) Positive else Gold,
                topLeft = Offset(left - 6f, top - 6f),
                size = Size((right - left) + 12f, (bottom - top) + 12f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                style = Stroke(width = 3f),
            )
        }
    }
}
