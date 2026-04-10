/*
 * Product Scanner screen — redesigned for Meal Photo Analysis + Barcode fallback.
 *
 * Primary mode (full-screen):
 *   - Live camera preview for photographing meals
 *   - [📷 Capture] shutter button  – CameraX ImageCapture → LLM analysis
 *   - [🖼 Gallery] button          – System Photo Picker → LLM analysis
 *   - Small barcode scanner overlay at bottom-right corner (always active)
 *
 * Result card is identical for both meal analysis and barcode scan results.
 */
package com.example.healthconnectsample.presentation.screen.productscanner

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.healthconnectsample.data.api.MealAnalysisPayloadResponse
import com.example.healthconnectsample.data.api.ProductInfoResponse
import com.example.healthconnectsample.data.api.ProductNutrimentsResponse
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "ProductScanner"

// ─────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────

@kotlin.OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProductScannerScreen(viewModel: ProductScannerViewModel) {
    val uiState by viewModel.uiState
    val context = LocalContext.current

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    if (!cameraPermission.status.isGranted) {
        CameraPermissionNeeded(
            shouldShowRationale = cameraPermission.status.shouldShowRationale,
            onRequestPermission = { cameraPermission.launchPermissionRequest() }
        )
        return
    }

    when (val state = uiState) {
        is ScannerUiState.Scanning -> {
            MealCameraView(
                onPhotoTaken = viewModel::onPhotoTaken,
                onBarcodeDetected = viewModel::onBarcodeDetected,
                onGalleryImagePicked = { uri, question ->
                    viewModel.onGalleryImagePicked(context, uri, question)
                }
            )
        }
        is ScannerUiState.LoadingBarcode -> LoadingView(message = "Looking up product…", sub = "Barcode: ${state.barcode}")
        is ScannerUiState.AnalyzingMeal -> LoadingView(message = "Analysing your meal…", sub = "This may take a few seconds")
        is ScannerUiState.ProductFound -> ProductDetailView(
            product = state.product,
            label = "Scan Another Product",
            onDone = viewModel::resetToScanning
        )
        is ScannerUiState.MealFound -> MealDetailView(
            meal = state.meal,
            askedQuestion = state.askedQuestion,
            questionAnswer = state.questionAnswer,
            mealImageBytes = state.mealImageBytes,
            label = "Analyse Another Meal",
            onDone = viewModel::resetToScanning
        )
        is ScannerUiState.NotFound -> NotFoundView(barcode = state.barcode, onScanAnother = viewModel::resetToScanning)
        is ScannerUiState.Error -> ErrorView(message = state.message, onRetry = viewModel::resetToScanning)
    }
}

// ─────────────────────────────────────────────
// Meal Camera view (primary mode)
// ─────────────────────────────────────────────

@Composable
private fun MealCameraView(
    onPhotoTaken: (ByteArray, String?) -> Unit,
    onBarcodeDetected: (String) -> Unit,
    onGalleryImagePicked: (Uri, String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // CameraX use-cases
    val imageCaptureUseCase = remember { ImageCapture.Builder().build() }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Bind Preview + ImageCapture + ImageAnalysis to the same camera
    LaunchedEffect(previewView) {
        val cameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), BarcodeAnalyzer(onBarcodeDetected)) }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCaptureUseCase,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    var showQuestionDialog by remember { mutableStateOf(false) }
    var questionText by remember { mutableStateOf("") }
    var pendingPhotoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingGalleryUri by remember { mutableStateOf<Uri?>(null) }

    // Gallery picker launcher (no READ_EXTERNAL_STORAGE needed)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            pendingGalleryUri = it
            showQuestionDialog = true
        }
    }

    fun dispatchAnalysis(question: String?) {
        val cleanQuestion = question?.trim()?.takeIf { it.isNotEmpty() }
        pendingPhotoBytes?.let { bytes ->
            onPhotoTaken(bytes, cleanQuestion)
        }
        pendingGalleryUri?.let { uri ->
            onGalleryImagePicked(uri, cleanQuestion)
        }
        pendingPhotoBytes = null
        pendingGalleryUri = null
        questionText = ""
        showQuestionDialog = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Live camera preview (full-screen)
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Guidance label
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📸  Point camera at your meal",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Bottom control bar: Gallery + Capture buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(bottom = 24.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "Pick from gallery",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Gallery", color = Color.White, fontSize = 11.sp)
                }

                // Shutter / Capture button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            takePhoto(context, imageCaptureUseCase) { imageBytes ->
                                pendingPhotoBytes = imageBytes
                                showQuestionDialog = true
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White, CircleShape)
                            .border(3.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Circle,
                            contentDescription = "Capture meal photo",
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Capture", color = Color.White, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barcode overlay hint
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Or hold a product barcode to the camera to scan",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }

        if (showQuestionDialog) {
            AskMealQuestionDialog(
                question = questionText,
                onQuestionChange = { questionText = it },
                onAnalyze = { dispatchAnalysis(questionText) },
                onSkipQuestion = { dispatchAnalysis(null) },
                onDismiss = {
                    pendingPhotoBytes = null
                    pendingGalleryUri = null
                    questionText = ""
                    showQuestionDialog = false
                }
            )
        }
    }
}

// ─────────────────────────────────────────────
// CameraX ImageCapture helper
// ─────────────────────────────────────────────

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onPhotoTaken: (ByteArray) -> Unit,
) {
    // Save to a temp file — guarantees a real JPEG (OnImageCapturedCallback planes[0]
    // returns raw sensor bytes which cannot be decoded as JPEG by the backend).
    val tempFile = java.io.File.createTempFile("meal_", ".jpg", context.cacheDir)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val bytes = tempFile.readBytes()
                    onPhotoTaken(bytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read saved photo", e)
                } finally {
                    tempFile.delete()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed", exception)
                tempFile.delete()
            }
        }
    )
}

// ─────────────────────────────────────────────
// ML Kit Barcode Analyzer (shared on the same camera)
// ─────────────────────────────────────────────

private class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { value ->
                            if (barcode.format in listOf(
                                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                                    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                                    Barcode.FORMAT_QR_CODE
                                )
                            ) {
                                onBarcodeDetected(value)
                            }
                        }
                    }
                }
                .addOnFailureListener { Log.e(TAG, "Barcode scan failed", it) }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }
}

// ─────────────────────────────────────────────
// Result Card (shared by barcode + meal flows)
// ─────────────────────────────────────────────

@Composable
private fun ProductDetailView(
    product: ProductInfoResponse,
    askedQuestion: String? = null,
    questionAnswer: String? = null,
    mealImageBytes: ByteArray? = null,
    label: String,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Display meal image at top if available
        mealImageBytes?.let { bytes ->
            val bitmap = remember(bytes) {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Analyzed meal",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (!askedQuestion.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.08f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your Question",
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = askedQuestion,
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Answer",
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = questionAnswer ?: "No answer was returned.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Product image (only for barcode results that have an image_url)
        product.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = product.productName ?: "Product image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Name & Brand/Portion
        Text(
            text = product.productName ?: "Unknown",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold
        )
        product.brands?.let { brand ->
            Text(
                text = brand,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Barcode / Meal-photo chip
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = if (product.barcode == "MEAL_PHOTO") "📸 Meal Photo Analysis"
                       else "Barcode: ${product.barcode}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
        }

        // Serving size chip
        product.servingSize?.let { sz ->
            Spacer(modifier = Modifier.height(2.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = "Serving: $sz",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // Nutri-Score (barcode results only)
        product.nutriscoreGrade?.let { grade ->
            Spacer(modifier = Modifier.height(8.dp))
            NutriScoreBadge(grade = grade)
        }

        // Categories
        product.categories?.let { cat ->
            Spacer(modifier = Modifier.height(12.dp))
            Text("Categories", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.SemiBold)
            Text(cat, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
        }

        // Nutrition Facts
        product.nutriments?.let { nutriments ->
            Spacer(modifier = Modifier.height(16.dp))
            NutritionFactsCard(
                nutriments = nutriments,
                productQuantity = product.servingQuantity ?: product.productQuantity,
                quantityUnit = product.productQuantityUnit
            )
        }

        // Ingredients (barcode only)
        product.ingredientsText?.let { ingredients ->
            Spacer(modifier = Modifier.height(16.dp))
            Text("Ingredients", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(ingredients, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
        ) {
            Icon(imageVector = Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MealDetailView(
    meal: MealAnalysisPayloadResponse,
    askedQuestion: String? = null,
    questionAnswer: String? = null,
    mealImageBytes: ByteArray? = null,
    label: String,
    onDone: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val nutrientRows = listOf(
        "calories" to (meal.nutritionalValue.calories?.let { "${String.format("%.1f", it)} kcal" } ?: "-"),
        "carbohydrate" to (meal.nutritionalValue.carbohydrate?.let { "${String.format("%.1f", it)} g" } ?: "-"),
        "protein" to (meal.nutritionalValue.protein?.let { "${String.format("%.1f", it)} g" } ?: "-"),
        "fat" to (meal.nutritionalValue.fat?.let { "${String.format("%.1f", it)} g" } ?: "-"),
        "saturated_fat" to (meal.nutritionalValue.saturatedFat?.let { "${String.format("%.1f", it)} g" } ?: "-"),
        "fiber" to (meal.nutritionalValue.fiber?.let { "${String.format("%.1f", it)} g" } ?: "-"),
        "sugar" to (meal.nutritionalValue.sugar?.let { "${String.format("%.1f", it)} g" } ?: "-"),
        "sodium" to (meal.nutritionalValue.sodium?.let { "${String.format("%.1f", it)} mg" } ?: "-"),
        "potassium" to (meal.nutritionalValue.potassium?.let { "${String.format("%.1f", it)} mg" } ?: "-"),
        "cholesterol" to (meal.nutritionalValue.cholesterol?.let { "${String.format("%.1f", it)} mg" } ?: "-"),
        "vitamin_a" to (meal.nutritionalValue.vitaminA?.let { "${String.format("%.1f", it)}" } ?: "-"),
        "vitamin_c" to (meal.nutritionalValue.vitaminC?.let { "${String.format("%.1f", it)} mg" } ?: "-"),
        "calcium" to (meal.nutritionalValue.calcium?.let { "${String.format("%.1f", it)} mg" } ?: "-"),
        "iron" to (meal.nutritionalValue.iron?.let { "${String.format("%.1f", it)} mg" } ?: "-"),
        "trans_fat" to (meal.nutritionalValue.transFat?.let { "${String.format("%.1f", it)} g" } ?: "-"),
        "added_sugars" to (meal.nutritionalValue.addedSugars?.let { "${String.format("%.1f", it)} g" } ?: "-"),
        "vitamin_d" to (meal.nutritionalValue.vitaminD?.let { "${String.format("%.1f", it)} mcg" } ?: "-"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        mealImageBytes?.let { bytes ->
            val bitmap = remember(bytes) {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Analyzed meal",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Text(
            text = meal.nameOfMeal,
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold
        )

        meal.servingSize?.let { sz ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
            ) {
                Text(
                    text = "Serving: $sz",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(12.dp), elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Nutritional Value", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                nutrientRows.forEach { (key, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(key.replace("_", " "), style = MaterialTheme.typography.body2)
                        Text(value, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.08f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reasoning", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(meal.reasoning, style = MaterialTheme.typography.body2)
            }
        }

        if (meal.webSourceLinks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(shape = RoundedCornerShape(12.dp), elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Web Sources", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    meal.webSourceLinks.forEach { link ->
                        TextButton(
                            onClick = { uriHandler.openUri(link) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(link, textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        }

        if (!askedQuestion.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.08f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your Question", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(askedQuestion, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Answer", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(questionAnswer ?: "No answer was returned.", style = MaterialTheme.typography.body2)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
        ) {
            Icon(imageVector = Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AskMealQuestionDialog(
    question: String,
    onQuestionChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onSkipQuestion: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask about this meal") },
        text = {
            Column {
                Text(
                    text = "Optional: add a question like 'Is this healthy?'",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = onQuestionChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Is this healthy?") },
                    singleLine = false,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAnalyze) {
                Text("Analyze")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkipQuestion) {
                    Text("Skip Question")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun NutriScoreBadge(grade: String) {
    val (bgColor, label) = when (grade.uppercase()) {
        "A" -> Color(0xFF1B7D3A) to "A – Excellent"
        "B" -> Color(0xFF85BB2F) to "B – Good"
        "C" -> Color(0xFFFECB02) to "C – Average"
        "D" -> Color(0xFFE9750F) to "D – Poor"
        "E" -> Color(0xFFE63F11) to "E – Bad"
        else -> Color.Gray to grade.uppercase()
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bgColor) {
        Text(
            text = "Nutri-Score: $label",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun NutritionFactsCard(
    nutriments: ProductNutrimentsResponse,
    productQuantity: Double?,
    quantityUnit: String?,
) {
    val pkgLabel = if (productQuantity != null) "per ${productQuantity}${quantityUnit ?: "g"}" else "per serving"
    Card(shape = RoundedCornerShape(12.dp), elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Nutrition Facts", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                Text("per 100g", style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
                Text(pkgLabel, style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.primary, modifier = Modifier.width(88.dp), textAlign = TextAlign.End)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Divider()
            NutrientRow("Energy", nutriments.energyKcal100g, nutriments.energyKcalPkg, "kcal")
            NutrientRow("Fat", nutriments.fat100g, nutriments.fatPkg, "g")
            NutrientRow("Carbohydrates", nutriments.carbohydrates100g, nutriments.carbohydratesPkg, "g")
            NutrientRow("  Sugars", nutriments.sugars100g, nutriments.sugarsPkg, "g")
            NutrientRow("Protein", nutriments.proteins100g, nutriments.proteinsPkg, "g")
            NutrientRow("Fiber", nutriments.fiber100g, nutriments.fiberPkg, "g")
            NutrientRow("Salt", nutriments.salt100g, nutriments.saltPkg, "g")
        }
    }
}

@Composable
private fun NutrientRow(label: String, per100g: Double?, perPkg: Double?, unit: String) {
    if (per100g == null && perPkg == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.body2, modifier = Modifier.weight(1f))
        Text(
            text = if (per100g != null) "%.1f %s".format(per100g, unit) else "–",
            style = MaterialTheme.typography.body2,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End
        )
        Text(
            text = if (perPkg != null) "%.1f %s".format(perPkg, unit) else "–",
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.width(88.dp),
            textAlign = TextAlign.End
        )
    }
}

// ─────────────────────────────────────────────
// State screens
// ─────────────────────────────────────────────

@Composable
private fun LoadingView(message: String, sub: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.h6)
            Text(sub, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun NotFoundView(barcode: String, onScanAnother: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(imageVector = Icons.Outlined.SearchOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Product Not Found", style = MaterialTheme.typography.h6)
            Text("Barcode $barcode was not found.", style = MaterialTheme.typography.body2, textAlign = TextAlign.Center, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onScanAnother, shape = RoundedCornerShape(12.dp)) { Text("Try Again") }
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(imageVector = Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colors.error)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Something went wrong", style = MaterialTheme.typography.h6)
            Text(message, style = MaterialTheme.typography.body2, textAlign = TextAlign.Center, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) { Text("Try Again") }
        }
    }
}

@Composable
private fun CameraPermissionNeeded(shouldShowRationale: Boolean, onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(imageVector = Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Camera Permission Required", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (shouldShowRationale)
                    "Camera access is needed for meal photo analysis and barcode scanning."
                else
                    "Please allow camera access to use this feature.",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermission, shape = RoundedCornerShape(12.dp)) { Text("Grant Permission") }
        }
    }
}
