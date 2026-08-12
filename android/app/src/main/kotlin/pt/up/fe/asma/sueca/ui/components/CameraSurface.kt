package pt.up.fe.asma.sueca.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import pt.up.fe.asma.sueca.ui.theme.Gold
import pt.up.fe.asma.sueca.vision.CardScanner

/**
 * The camera, the permission dance and an overlay slot, in one piece both camera screens use.
 */
@Composable
fun CameraSurface(
    scanner: CardScanner,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
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

    Box(modifier) {
        if (granted) {
            CameraPreview(scanner, Modifier.fillMaxSize())
            overlay()
        } else {
            PermissionPrompt { request.launch(Manifest.permission.CAMERA) }
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

                // 720p rather than the 480p default: a card's index corner is a small part of the
                // frame, and at 480p a pip lands on barely a dozen pixels, which is where most of
                // the misreadings come from.
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
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
