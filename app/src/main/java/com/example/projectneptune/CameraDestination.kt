package com.example.projectneptune

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ExecutorService

@Composable
fun CameraDestination(
    cameraExecutor: ExecutorService,
    modifier: Modifier = Modifier
){
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraPreview(cameraExecutor, modifier)
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required to use this feature.")
        }
    }
}

@Composable
fun CameraPreview(
    cameraExecutor: ExecutorService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }
    val imageCapture: ImageCapture = remember {
        ImageCapture.Builder()
            .build()
    }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    var capturedFile by remember { mutableStateOf<File?>(null) }
    var isCropping by remember { mutableStateOf(false) }
    var isIdentifying by remember { mutableStateOf(false) }
    var detectionResult by remember { mutableStateOf<String?>(null) }

    // Use a single effect to manage camera binding/unbinding. 
    // This unbinds the camera when a photo is being reviewed or cropped.
    LaunchedEffect(capturedFile) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        if (capturedFile == null) {
            val preview = CameraXPreview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraPreview", "Use case binding failed", exc)
            }
        } else {
            cameraProvider.unbindAll()
        }
    }

    // Ensure camera is released when this screen is no longer in the composition (tab switch)
    DisposableEffect(Unit) {
        onDispose {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error unbinding camera on dispose", e)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (capturedFile == null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            
            Button(
                onClick = {
                    takePhoto(context, imageCapture, cameraExecutor) { file ->
                        capturedFile = file
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Text("Capture")
            }
        } else if (isCropping) {
            CropScreen(
                file = capturedFile!!,
                onCropDone = { croppedFile ->
                    capturedFile = croppedFile
                    isCropping = false
                },
                onCancel = { isCropping = false }
            )
        } else {
            PhotoReviewScreen(
                file = capturedFile!!,
                onCrop = {
                    isCropping = true
                    detectionResult = null
                },
                onDetect = {
                    scope.launch {
                        isIdentifying = true
                        detectionResult = detectSpecies(context, capturedFile!!)
                        isIdentifying = false
                    }
                },
                onDiscard = {
                    capturedFile?.delete()
                    capturedFile = null
                    detectionResult = null
                }
            )
            
            detectionResult?.let { result ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp),
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (isIdentifying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) {}, // Block touches during detection
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Identifying...",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CropScreen(
    file: File,
    onCropDone: (File) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(file) { rotateBitmapIfRequired(file) } ?: return
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val displayWidth = constraints.maxWidth.toFloat()
        val displayHeight = constraints.maxHeight.toFloat()

        val scale = minOf(displayWidth / bitmap.width, displayHeight / bitmap.height)
        val viewWidth = bitmap.width * scale
        val viewHeight = bitmap.height * scale

        var cropRect by remember(viewWidth, viewHeight) {
            val size = minOf(viewWidth, viewHeight) * 0.8f
            mutableStateOf(Rect(Offset((viewWidth - size) / 2, (viewHeight - size) / 2), Size(size, size)))
        }

        Box(
            modifier = Modifier
                .size(
                    width = with(density) { viewWidth.toDp() },
                    height = with(density) { viewHeight.toDp() }
                )
                .align(Alignment.Center)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(viewWidth, viewHeight) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            
                            val handleSize = 40.dp.toPx()
                            val touchPoint = change.position
                            
                            val isBottomRight = (touchPoint.x - cropRect.right).let { it * it } + (touchPoint.y - cropRect.bottom).let { it * it } < handleSize * handleSize
                            
                            if (isBottomRight) {
                                val newWidth = (cropRect.width + dragAmount.x).coerceIn(50f, viewWidth - cropRect.left)
                                val newHeight = (cropRect.height + dragAmount.y).coerceIn(50f, viewHeight - cropRect.top)
                                cropRect = Rect(cropRect.topLeft, Size(newWidth, newHeight))
                            } else {
                                val newTopLeft = Offset(
                                    (cropRect.left + dragAmount.x).coerceIn(0f, viewWidth - cropRect.width),
                                    (cropRect.top + dragAmount.y).coerceIn(0f, viewHeight - cropRect.height)
                                )
                                cropRect = Rect(newTopLeft, cropRect.size)
                            }
                        }
                    }
            ) {
                drawRect(
                    color = Color.White,
                    topLeft = cropRect.topLeft,
                    size = cropRect.size,
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Draw a handle at the bottom right corner
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = cropRect.bottomRight
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Cancel")
            }
            Button(onClick = {
                val croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    (cropRect.left / scale).toInt(),
                    (cropRect.top / scale).toInt(),
                    (cropRect.width / scale).toInt(),
                    (cropRect.height / scale).toInt()
                )
                val croppedFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                FileOutputStream(croppedFile).use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                onCropDone(croppedFile)
            }) {
                Text("Confirm Crop")
            }
        }
    }
}

@Composable
fun PhotoReviewScreen(
    file: File,
    onCrop: () -> Unit,
    onDetect: () -> Unit,
    onDiscard: () -> Unit
) {
    val bitmap = remember(file) {
        rotateBitmapIfRequired(file)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Captured Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onDiscard,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Discard")
            }
            Button(
                onClick = { onCrop() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Crop")
            }
            Button(onClick = { onDetect() }) {
                Text("Detect")
            }
        }
    }
}

private fun rotateBitmapIfRequired(file: File): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val exif = try {
        ExifInterface(file.absolutePath)
    } catch (e: Exception) {
        return bitmap
    }
    
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        else -> return bitmap
    }
    
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    cameraExecutor: ExecutorService,
    onPhotoCaptured: (File) -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
        .format(System.currentTimeMillis())
    
    // Save to cache directory first for review
    val photoFile = File(context.cacheDir, "$name.jpg")

    val outputOptions = ImageCapture.OutputFileOptions
        .Builder(photoFile)
        .build()

    imageCapture.takePicture(
        outputOptions,
        cameraExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraPreview", "Photo capture failed: ${exc.message}", exc)
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d("CameraPreview", "Photo capture succeeded: ${photoFile.absolutePath}")
                ContextCompat.getMainExecutor(context).execute {
                    onPhotoCaptured(photoFile)
                }
            }
        }
    )
}

private suspend fun detectSpecies(context: Context, photoFile: File): String = withContext(Dispatchers.Default) {
    val speciesList = listOf(
        "Blue Mussel", "Butter Clam", "California Mussel", "Geoduck",
        "Littleneck Clam", "Manila Clam", "Northern Abalone", "Nuttall's Cockle",
        "Olympia Oyster", "Horse Clam", "Pacific Oyster", "Pink Scallop",
        "Purple Scallop", "Razor Clam", "Softshell Clam", "Spiny Scallop",
        "Varnish Clam", "Weathervane Scallop"
    )

    try {
        val ortEnv = OrtEnvironment.getEnvironment()
        val modelBytes = context.assets.open("50rounds.onnx").readBytes()
        val session = ortEnv.createSession(modelBytes)

        val bitmap = rotateBitmapIfRequired(photoFile) ?: return@withContext "Failed to load image"
        // Model expects 416x416 based on error message
        val inputSize = 416
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        
        val imgData = FloatBuffer.allocate(1 * 3 * inputSize * inputSize)
        imgData.rewind()
        
        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixel = pixels[i * inputSize + j]
                imgData.put(0 * inputSize * inputSize + i * inputSize + j, ((pixel shr 16) and 0xFF) / 255.0f)
                imgData.put(1 * inputSize * inputSize + i * inputSize + j, ((pixel shr 8) and 0xFF) / 255.0f)
                imgData.put(2 * inputSize * inputSize + i * inputSize + j, (pixel and 0xFF) / 255.0f)
            }
        }
        imgData.rewind()

        val inputName = session.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(ortEnv, imgData, longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
        
        val output = session.run(Collections.singletonMap(inputName, inputTensor))
        val resultContents = output.get(0).value as Array<Array<FloatArray>>
        
        val numClasses = speciesList.size
        val resultTensor = resultContents[0]
        val numPredictions = resultTensor[0].size // Dynamically get number of predictions
        val confidenceThreshold = 0.70f
        val detectedSet = mutableSetOf<String>()

        for (i in 0 until numPredictions) {
            var maxProb = 0f
            var maxIdx = -1
            for (j in 0 until numClasses) {
                // YOLO output format: [box_x, box_y, box_w, box_h, class0, class1, ...]
                // probabilities start at index 4
                val prob = resultTensor[j + 4][i]
                if (prob > maxProb) {
                    maxProb = prob
                    maxIdx = j
                }
            }
            if (maxProb > confidenceThreshold && maxIdx != -1) {
                detectedSet.add(speciesList[maxIdx])
            }
        }

        session.close()
        ortEnv.close()
        
        if (detectedSet.isEmpty()) {
            "No species detected."
        } else {
            "Detected: ${detectedSet.joinToString(", ")}"
        }
    } catch (e: Exception) {
        Log.e("Detection", "Error during inference", e)
        "Error: ${e.message}"
    }
}
