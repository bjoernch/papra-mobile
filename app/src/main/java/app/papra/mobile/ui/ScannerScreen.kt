package app.papra.mobile.ui

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun ScannerScreen(
    onCancel: () -> Unit,
    onConfirm: (Bitmap, List<PointF>) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var corners by remember { mutableStateOf<List<PointF>>(emptyList()) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (previewBitmap == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            .setTargetResolution(Size(2000, 2000))
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture
                        val selector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        val capture = imageCapture ?: return@Button
                        val file = File(context.cacheDir, "scan-capture.jpg")
                        val output = ImageCapture.OutputFileOptions.Builder(file).build()
                        capture.takePicture(
                            output,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    val bitmap = loadBitmap(file)
                                    if (bitmap != null) {
                                        previewBitmap = bitmap
                                        corners = detectDocumentCorners(bitmap)
                                    } else {
                                        onError("Failed to capture image.")
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    onError("Capture failed.")
                                }
                            }
                        )
                    }) {
                        Text("Capture")
                    }
                }
            }
        } else {
            val bitmap = previewBitmap!!
            val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                val displayWidth = remember { mutableStateOf(0f) }
                val displayHeight = remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val pos = change.position
                                val w = displayWidth.value
                                val h = displayHeight.value
                                if (w <= 0f || h <= 0f) return@detectDragGestures
                                val current = corners
                                val hit = current.indexOfFirst {
                                    val dx = it.x * w - pos.x
                                    val dy = it.y * h - pos.y
                                    dx * dx + dy * dy < 30 * 30
                                }
                                if (hit >= 0) {
                                    val nx = (pos.x / w).coerceIn(0f, 1f)
                                    val ny = (pos.y / h).coerceIn(0f, 1f)
                                    corners = current.toMutableList().also { list ->
                                        list[hit] = PointF(nx, ny)
                                    }
                                }
                            }
                        }
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        displayWidth.value = size.width
                        displayHeight.value = size.height
                        if (corners.isNotEmpty()) {
                            val points = corners.map { Offset(it.x * size.width, it.y * size.height) }
                            drawLine(Color.Green, points[0], points[1], 4f)
                            drawLine(Color.Green, points[1], points[2], 4f)
                            drawLine(Color.Green, points[2], points[3], 4f)
                            drawLine(Color.Green, points[3], points[0], 4f)
                            points.forEach { p ->
                                drawCircle(Color.White, radius = 10f, center = p)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
                        previewBitmap = null
                        corners = emptyList()
                    }) {
                        Text("Retake")
                    }
                    Button(onClick = { onConfirm(bitmap, corners) }) {
                        Text("Use scan")
                    }
                }
            }
        }
    }
}

private fun loadBitmap(file: File): Bitmap? {
    return android.graphics.BitmapFactory.decodeFile(file.absolutePath)
}
