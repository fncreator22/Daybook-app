package com.sr2ma.daybook.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.PassParser
import com.sr2ma.daybook.domain.ScanResult
import com.sr2ma.daybook.domain.model.PassCategory

/**
 * Full-screen camera viewfinder that detects barcodes.
 *
 * Architecture:
 *   - CameraX LifecycleCameraController drives the preview and analysis pipeline.
 *   - MlKitAnalyzer bridges CameraX to ML Kit BarcodeScanner + TextRecognizer.
 *   - On detection, PassParser.parse() turns raw ML Kit output into a ScanResult
 *     and [onScanResult] is called exactly once (further frames are suppressed).
 *   - Gallery button picks a static image and runs the same ML Kit client on it.
 *
 * Multi-barcode: if ML Kit returns more than one barcode in a frame,
 * PassParser.pickLargest() selects the one with the biggest bounding box (grilling Q1).
 */
@Composable
fun CameraScreen(
    onScanResult: (ScanResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Permission ────────────────────────────────────────────────────────────
    var permissionGranted by remember { mutableStateOf(false) }
    var permissionDeniedPermanently by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) permissionDeniedPermanently = true
    }

    LaunchedEffect(Unit) {
        val check = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (check == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Gallery picker (grilling Q2: also support static images) ─────────────
    var scanHandled by remember { mutableStateOf(false) }

    val handleUri: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            val scanner = BarcodeScanning.getClient()
            val inputImage = com.google.mlkit.vision.common.InputImage.fromFilePath(context, uri)
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val barcode = if (barcodes.size == 1) barcodes.first() else {
                            val areas = barcodes.map {
                                val r = it.boundingBox
                                Triple(it.rawValue ?: "", formatName(it.format), if (r != null) r.width() * r.height() else 0)
                            }
                            barcodes[PassParser.pickLargest(areas)]
                        }
                        val result = PassParser.parse(
                            barcodeValue = barcode.rawValue ?: "",
                            barcodeFormat = formatName(barcode.format),
                        )
                        onScanResult(result)
                    } else {
                        // OCR text fallback for non-barcode documents/passes
                        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        textRecognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                val text = visionText.text.trim()
                                if (text.isNotBlank()) {
                                    val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.take(40) ?: "Document"
                                    val result = ScanResult(
                                        barcodeValue = "",
                                        barcodeFormat = "DOCUMENT",
                                        ocrText = text,
                                        suggestedTitle = firstLine,
                                        suggestedCategory = PassCategory.DOCUMENT,
                                    )
                                    onScanResult(result)
                                }
                            }
                    }
                }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> handleUri(uri) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> handleUri(uri) }

    // ── Camera controller ────────────────────────────────────────────────────
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    if (permissionGranted) {
        val analyzer = remember {
            MlKitAnalyzer(
                listOf(barcodeScanner, textRecognizer),
                ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
                ContextCompat.getMainExecutor(context),
            ) { result ->
                if (scanHandled) return@MlKitAnalyzer
                val barcodes = result.getValue(barcodeScanner) ?: return@MlKitAnalyzer
                if (barcodes.isEmpty()) return@MlKitAnalyzer
                val ocrText = result.getValue(textRecognizer)?.text ?: ""

                val barcode = if (barcodes.size == 1) barcodes.first() else {
                    val areas = barcodes.map {
                        val r = it.boundingBox
                        Triple(it.rawValue ?: "", formatName(it.format), if (r != null) r.width() * r.height() else 0)
                    }
                    barcodes[PassParser.pickLargest(areas)]
                }
                val value = barcode.rawValue ?: return@MlKitAnalyzer
                scanHandled = true
                onScanResult(PassParser.parse(value, formatName(barcode.format), ocrText))
            }
        }

        LaunchedEffect(Unit) {
            cameraController.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
            cameraController.bindToLifecycle(lifecycleOwner)
        }

        DisposableEffect(Unit) {
            onDispose {
                cameraController.unbind()
                barcodeScanner.close()
                textRecognizer.close()
            }
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (permissionGranted) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { it.controller = cameraController }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (permissionDeniedPermanently) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.camera_permission_denied),
                        modifier = Modifier.padding(24.dp),
                    )
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            })
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    ) {
                        Text(stringResource(R.string.camera_permission_open_settings))
                    }
                }
            }

            // Toolbar: back button placed safely below status bar/camera notch
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 24.dp, start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(2.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Bottom controls: Cancel & Gallery
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    ),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        ),
                    ) {
                        Text("Files")
                    }

                    Button(
                        onClick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    ) {
                        Text(stringResource(R.string.wallet_gallery_button))
                    }
                }
            }
        }
    }
}

/** Converts a ML Kit Barcode.FORMAT_* int constant to a readable name string. */
private fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE      -> "QR_CODE"
    Barcode.FORMAT_PDF417       -> "PDF_417"
    Barcode.FORMAT_AZTEC        -> "AZTEC"
    Barcode.FORMAT_EAN_13       -> "EAN_13"
    Barcode.FORMAT_EAN_8        -> "EAN_8"
    Barcode.FORMAT_UPC_A        -> "UPC_A"
    Barcode.FORMAT_UPC_E        -> "UPC_E"
    Barcode.FORMAT_CODE_128     -> "CODE_128"
    Barcode.FORMAT_CODE_39      -> "CODE_39"
    Barcode.FORMAT_CODE_93      -> "CODE_93"
    Barcode.FORMAT_CODABAR      -> "CODABAR"
    Barcode.FORMAT_ITF          -> "ITF"
    Barcode.FORMAT_DATA_MATRIX  -> "DATA_MATRIX"
    else -> "UNKNOWN"
}
