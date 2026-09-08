package eu.feg.ambient.ui.ticket

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import eu.feg.ambient.ui.components.IconTouchTarget
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import java.util.concurrent.Executors

private const val TAG = "ScanTicket"

/**
 * Step 18: a paper slip from the branch, onto the lock screen. PSK already scans tickets; this
 * is the same scan with a different destination. The camera is asked for here and only here —
 * a betting app that wants the camera at launch is a betting app people uninstall.
 */
@Composable
fun ScanTicketScreen(
    viewModel: ScanTicketViewModel,
    modifier: Modifier = Modifier,
    onOpenMyBets: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) viewModel.onPermissionDenied()
    }
    LaunchedEffect(Unit) { if (!granted) request.launch(Manifest.permission.CAMERA) }

    Column(modifier = modifier.fillMaxSize().background(psk.background)) {
        val scanning = state as? ScanState.Scanning
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconTouchTarget("Back", onClick = onBack, visualSize = 48.dp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = psk.textPrimary)
            }
            Text(
                text = "Scan a branch ticket",
                style = MaterialTheme.typography.titleLarge,
                color = psk.textPrimary,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (scanning != null && granted) {
                val on = scanning.torchOn
                IconTouchTarget(if (on) "Turn torch off" else "Turn torch on", viewModel::toggleTorch, visualSize = 48.dp) {
                    Icon(if (on) Icons.Filled.FlashOn else Icons.Filled.FlashOff, null, tint = psk.textPrimary)
                }
            }
        }
        // The rationale sits above the request, in one line, every time the screen opens.
        Text(
            text = "We use the camera only to read the barcode on your slip.",
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        when (val s = state) {
            is ScanState.Scanning -> ScanningContent(
                state = s,
                cameraAllowed = granted,
                onCode = viewModel::onCode,
                onManualCode = viewModel::onManualCode,
                onShowManual = viewModel::showManualEntry,
                onHideManual = viewModel::reset,
            )
            is ScanState.LookingUp -> LookingUp(s.code)
            is ScanState.Result -> ScanResultContent(
                result = s.result,
                onTrack = viewModel::trackOnLockScreen,
                onOpenMyBets = onOpenMyBets,
                onEnterManually = viewModel::showManualEntry,
                onScanAgain = viewModel::reset,
            )
            is ScanState.Tracked -> TrackedContent(onOpenMyBets = onOpenMyBets, onScanAgain = viewModel::reset)
        }
    }
}

@Composable
private fun ScanningContent(
    state: ScanState.Scanning,
    cameraAllowed: Boolean,
    onCode: (String, String) -> Unit,
    onManualCode: (String) -> Unit,
    onShowManual: () -> Unit,
    onHideManual: () -> Unit,
) {
    val psk = LocalPskColors.current
    Column(Modifier.fillMaxSize()) {
        if (cameraAllowed && !state.manualEntry) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraPreview(torchOn = state.torchOn, onCode = onCode, modifier = Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(3f)
                            .border(2.dp, psk.textPrimary, PskShapes.card)
                            .semantics { contentDescription = "Barcode scanning area" },
                    )
                    Text(
                        text = "Point at the barcode on your slip",
                        style = MaterialTheme.typography.bodyMedium,
                        color = psk.textPrimary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    state.lastCode?.let {
                        Text("Last read: " + it, style = MaterialTheme.typography.labelSmall, color = psk.textSecondary)
                    }
                }
            }
            Box(Modifier.padding(16.dp)) {
                PskChip("Enter ticket code instead", onClick = onShowManual)
            }
        } else {
            ManualEntry(
                permissionDenied = state.permissionDenied,
                onLookUp = onManualCode,
                onUseCamera = if (cameraAllowed) onHideManual else null,
            )
        }
    }
}

/**
 * CameraX preview plus an ML Kit analyser, bound to the screen's lifecycle so leaving the
 * screen releases the camera without anyone remembering to. Detections go out as plain
 * strings; the ViewModel decides which of them matter.
 */
@Composable
private fun CameraPreview(torchOn: Boolean, onCode: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode by rememberUpdatedState(onCode)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val scanner = BarcodeScanning.getClient()
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get().also { provider = it }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                // A slip does not move; the newest frame is the only one worth decoding.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image -> decode(scanner, image) { c, f -> latestOnCode(c, f) } }
            p.unbindAll()
            camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
            scanner.close()
        }
    }
    LaunchedEffect(camera, torchOn) { camera?.cameraControl?.enableTorch(torchOn) }
    AndroidView(factory = { previewView }, modifier = modifier.semantics { contentDescription = "Camera preview" })
}

@OptIn(ExperimentalGetImage::class)
private fun decode(scanner: BarcodeScanner, image: ImageProxy, onCode: (String, String) -> Unit) {
    val media = image.image
    if (media == null) {
        image.close()
        return
    }
    scanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
        .addOnSuccessListener { barcodes ->
            barcodes.forEach { barcode ->
                val raw = barcode.rawValue ?: return@forEach
                val format = formatName(barcode.format)
                // The spike: proof on logcat that a real slip decodes, before any UI existed.
                Log.i(TAG, "barcode " + format + " " + raw)
                onCode(raw, format)
            }
        }
        // Closed on completion, never earlier: the analyser waits for this proxy to be released.
        .addOnCompleteListener { image.close() }
}

private fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_CODE_128 -> "CODE_128"
    Barcode.FORMAT_QR_CODE -> "QR_CODE"
    else -> "OTHER"
}
