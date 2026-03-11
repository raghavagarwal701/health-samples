/*
 * MealsScreen
 *
 * Layout:
 *  1. Date selector bar (← Today, DD MMM YYYY →)  – tap the label for a DatePickerDialog
 *  2. Macro summary card (🔥 kcal · 🥩 protein · 🍞 carbs · 🫙 fat)
 *  3. Scrollable meal log (one card per entry)
 *  4. FAB (+) at BottomCenter → opens camera
 *
 * Camera sub-screen (triggered by FAB):
 *  • Full-screen CameraX preview with shutter + gallery buttons
 *  • On analysis success: entry added to log, camera closes
 *  • On error: error card shown with a Retry / Close button
 */
package com.example.healthconnectsample.presentation.screen.meals

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.healthconnectsample.data.api.ProductInfoResponse
import com.example.healthconnectsample.data.api.ProductNutrimentsResponse
import com.example.healthconnectsample.data.api.MealItem
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val MEALS_TAG = "MealsScreen"

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@kotlin.OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MealsScreen(viewModel: MealsViewModel) {
    val cameraState by viewModel.cameraState
    val selectedDate by viewModel.selectedDate

    val context = LocalContext.current
    when (cameraState) {
        is MealCameraState.LogView -> {
            MealLogView(viewModel = viewModel, selectedDate = selectedDate)
        }
        is MealCameraState.InputMethodChooser -> {
            MealInputMethodChooser(
                onCamera = { viewModel.openCamera() },
                onClose = { viewModel.closeCamera() },
                viewModel = viewModel
            )
        }
        is MealCameraState.CameraOpen -> {
            val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
            LaunchedEffect(Unit) {
                if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
            }
            if (!cameraPermission.status.isGranted) {
                MealCameraPermissionNeeded(
                    onRequestPermission = { cameraPermission.launchPermissionRequest() },
                    onClose = { viewModel.closeCamera() }
                )
            } else {
                MealCameraView(
                    onPhotoTaken = { bytes, uri -> viewModel.onPhotoTaken(bytes, uri) },
                    onClose = viewModel::closeCamera,
                    viewModel = viewModel
                )
            }
        }
        is MealCameraState.PhotoPreview -> {
            val preview = cameraState as MealCameraState.PhotoPreview
            MealPhotoPreviewScreen(
                imageUri = preview.imageUri,
                onRetake = { viewModel.retakePhoto() },
                onConfirm = { note ->
                    viewModel.confirmPhoto(preview.imageBytes, preview.imageUri, note)
                }
            )
        }
        is MealCameraState.Analyzing -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Analysing your meal…", style = MaterialTheme.typography.h6)
                    Text(
                        "This may take a few seconds",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
        is MealCameraState.CameraError -> {
            val msg = (cameraState as MealCameraState.CameraError).message
            MealErrorView(message = msg, onRetry = viewModel::openCamera, onClose = viewModel::closeCamera)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Input method chooser (camera vs text)
// ─────────────────────────────────────────────────────────────────────────────

@kotlin.OptIn(ExperimentalMaterialApi::class)
@Composable
private fun MealInputMethodChooser(
    onCamera: () -> Unit,
    onClose: () -> Unit,
    viewModel: MealsViewModel,
) {
    var showTextDialog by remember { mutableStateOf(false) }

    if (showTextDialog) {
        MealTextInputDialog(
            onDismiss = { showTextDialog = false },
            onSubmit = { description ->
                showTextDialog = false
                viewModel.analyzeTextDescription(description)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        // Close button in top-end corner
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                tint = MaterialTheme.colors.onSurface
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Add a meal",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "How would you like to log it?",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            // Camera / image option
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = 4.dp,
                onClick = onCamera
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Take a photo",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Photograph your meal and let AI analyse it",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            // Text description option
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = 4.dp,
                onClick = { showTextDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF7B61FF).copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF7B61FF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Describe in text",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Type what you ate and AI will estimate nutrients",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Meal Log View (main content)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MealLogView(viewModel: MealsViewModel, selectedDate: LocalDate) {
    val entries = viewModel.entriesForDate(selectedDate)
    val viewingMeal by viewModel.viewingMeal

    if (viewingMeal != null) {
        MealDetailDialog(
            entry = viewingMeal!!,
            onClose = viewModel::closeMeal
        )
    }

    val totalKcal = viewModel.totalKcal(selectedDate)
    val totalProtein = viewModel.totalProtein(selectedDate)
    val totalCarbs = viewModel.totalCarbs(selectedDate)
    val totalFat = viewModel.totalFat(selectedDate)
    val context = LocalContext.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showInputChooser,
                backgroundColor = MaterialTheme.colors.primary,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add meal",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── 1. Date selector ──────────────────────────────────────────
            item {
                DateSelectorBar(
                    selectedDate = selectedDate,
                    onPrevious = viewModel::previousDay,
                    onNext = viewModel::nextDay,
                    onPickDate = { date -> viewModel.selectDate(date) },
                    context = context
                )
            }

            // ── 2. Macro summary card ─────────────────────────────────────
            item {
                MacroSummaryCard(
                    kcal = totalKcal,
                    protein = totalProtein,
                    carbs = totalCarbs,
                    fat = totalFat
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── 3. Meal entries ───────────────────────────────────────────
            if (entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🍽️",
                                fontSize = 52.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No meals logged yet",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap + to photograph a meal",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(entries) { index, entry ->
                    MealEntryCard(
                        entry = entry,
                        onClick = { viewModel.viewMeal(entry) },
                        onDelete = { viewModel.removeMeal(selectedDate, index) }
                    )
                }
            }

            // Bottom spacer so last card isn't hidden by FAB
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date selector bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DateSelectorBar(
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    context: Context
) {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    val label = when (selectedDate) {
        today -> "Today · ${selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        today.minusDays(1) -> "Yesterday · ${selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        else -> selectedDate.format(formatter)
    }

    Surface(
        color = MaterialTheme.colors.surface,
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous day",
                    tint = MaterialTheme.colors.primary
                )
            }

            TextButton(
                onClick = {
                    val cal = Calendar.getInstance().apply {
                        set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                    }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            onPickDate(LocalDate.of(year, month + 1, day))
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            // Disable the "next" arrow if already on today
            IconButton(
                onClick = onNext,
                enabled = selectedDate.isBefore(today)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next day",
                    tint = if (selectedDate.isBefore(today)) MaterialTheme.colors.primary else MaterialTheme.colors.primary.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Macro summary card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroSummaryCard(
    kcal: Double,
    protein: Double,
    carbs: Double,
    fat: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = 6.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Daily Totals",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroChip(emoji = "🔥", value = kcal, unit = "kcal", color = Color(0xFFFF6B35))
                MacroChip(emoji = "🥩", value = protein, unit = "g protein", color = Color(0xFF4CAF50))
                MacroChip(emoji = "🍞", value = carbs, unit = "g carbs", color = Color(0xFF2196F3))
                MacroChip(emoji = "🫙", value = fat, unit = "g fat", color = Color(0xFFFF9800))
            }
        }
    }
}

@Composable
private fun MacroChip(emoji: String, value: Double, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "%.0f".format(value),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color
        )
        Text(
            text = unit,
            fontSize = 10.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Meal entry card
// ─────────────────────────────────────────────────────────────────────────────

@kotlin.OptIn(ExperimentalMaterialApi::class)
@Composable
private fun MealEntryCard(entry: MealEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val product = entry.product
    val kcal = product.nutriments?.energyKcalPkg
    val timeStr = entry.timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        elevation = 3.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Meal thumbnail: show actual photo if available, fallback to emoji
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (entry.imageUri != null) {
                    AsyncImage(
                        model = entry.imageUri,
                        contentDescription = "Meal photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("🍽️", fontSize = 22.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.productName ?: "Meal",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2
                )
                Spacer(Modifier.height(2.dp))
                // Portion + categories
                val sub = listOfNotNull(
                    product.servingSize?.let { "~$it" },
                    product.categories
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (kcal != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF6B35).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "🔥 ${"%.0f".format(kcal)} kcal",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF6B35),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    // Protein / Carbs / Fat mini chips
                    product.nutriments?.let { n ->
                        MiniNutrientChip("P ${n.proteinsPkg?.let { "%.0f".format(it) } ?: "–"}g", Color(0xFF4CAF50))
                        Spacer(Modifier.width(4.dp))
                        MiniNutrientChip("C ${n.carbohydratesPkg?.let { "%.0f".format(it) } ?: "–"}g", Color(0xFF2196F3))
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.45f)
                )
                Spacer(Modifier.height(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colors.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniNutrientChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Camera view (full-screen, triggered by FAB)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MealCameraView(
    onPhotoTaken: (ByteArray, Uri?) -> Unit,
    onClose: () -> Unit,
    viewModel: MealsViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

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

    LaunchedEffect(previewView) {
        val cameraProvider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        // No barcode analysis needed here – only meal photos
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCaptureUseCase,
            )
        } catch (e: Exception) {
            Log.e(MEALS_TAG, "Camera binding failed", e)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onGalleryImagePicked(context, it) }
    }

    var showTextInputDialog by remember { mutableStateOf(false) }

    if (showTextInputDialog) {
        MealTextInputDialog(
            onDismiss = { showTextInputDialog = false },
            onSubmit = { description ->
                showTextInputDialog = false
                viewModel.analyzeTextDescription(description)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Close button – top-end corner, no text bar
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close camera",
                tint = Color.White
            )
        }

        // Bottom control bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(bottom = 32.dp, top = 20.dp)
        ) {
            // Capture – truly centred
            IconButton(
                onClick = { takeMealPhoto(context, imageCaptureUseCase, onPhotoTaken) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(Color(0xFF9E9E9E), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color(0xFFBDBDBD), CircleShape)
                )
            }

            // Gallery – smaller, left side, vertically centred with capture
            IconButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 40.dp)
                    .size(54.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Pick from gallery",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Write / text-only – smaller, right side, vertically centred with capture
            IconButton(
                onClick = { showTextInputDialog = true },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 40.dp)
                    .size(54.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Describe meal in text",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Photo Preview Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MealPhotoPreviewScreen(
    imageUri: String?,
    onRetake: () -> Unit,
    onConfirm: (note: String) -> Unit,
) {
    var noteText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Full-screen photo ──────────────────────────────────────────────
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Captured meal photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("🍽️", fontSize = 80.sp)
            }
        }

        // ── Top bar ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📸  Review your photo",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // ── Bottom panel: note field + action buttons ──────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f))
                    )
                )
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Optional note text field
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = {
                    Text(
                        "Add a note… (e.g. I only ate half)",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp
                    )
                },
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.White.copy(alpha = 0.6f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = Color.White,
                    backgroundColor = Color.Black.copy(alpha = 0.30f)
                ),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                singleLine = false
            )

            // Retake / Confirm buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ✗  Retake
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onRetake,
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0xFFE53935), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Retake photo",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Retake", color = Color.White, fontSize = 12.sp)
                }

                // ✓  Confirm
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onConfirm(noteText) },
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0xFF43A047), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm and analyse",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Send", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CameraX capture helper
// ─────────────────────────────────────────────────────────────────────────────

private fun takeMealPhoto(
    context: Context,
    imageCapture: ImageCapture,
    onPhotoTaken: (ByteArray, Uri?) -> Unit,
) {
    // Use filesDir (persistent) instead of cacheDir (can be cleared by OS)
    val tempFile = java.io.File.createTempFile("meals_", ".jpg", context.filesDir)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    // Do not delete tempFile if we want to view it later,
                    // just pass the Uri back.
                    val uri = Uri.fromFile(tempFile)
                    onPhotoTaken(tempFile.readBytes(), uri)
                } catch (e: Exception) {
                    Log.e(MEALS_TAG, "Failed to read saved photo", e)
                    tempFile.delete()
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e(MEALS_TAG, "Photo capture failed", exception)
                tempFile.delete()
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Error / Permission views
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MealErrorView(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colors.error
            )
            Spacer(Modifier.height(16.dp))
            Text("Something went wrong", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClose, shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel")
                }
                Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
                    Text("Try Again")
                }
            }
        }
    }
}

@Composable
private fun MealTextInputDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Describe what you ate") },
        text = {
            Column {
                Text(
                    "Type what you ate and how much — the AI will estimate your calories and nutrients.",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("e.g. 2 chapatis with dal and a glass of milk") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onSubmit(text) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Analyse")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun MealCameraPermissionNeeded(
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colors.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("Camera Permission Required", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(8.dp))
            Text(
                "Camera access is needed to photograph your meals.",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClose, shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel")
                }
                Button(onClick = onRequestPermission, shape = RoundedCornerShape(12.dp)) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Meal Detail Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MealDetailDialog(entry: MealEntry, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top image & close button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    if (entry.imageUri != null) {
                        AsyncImage(
                            model = entry.imageUri,
                            contentDescription = "Meal photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colors.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🍽️", fontSize = 64.sp)
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                // Details content
                Column(modifier = Modifier.padding(16.dp)) {
                    val product = entry.product
                    Text(
                        text = product.productName ?: "Meal",
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.Bold
                    )
                    
                    product.servingSize?.let {
                        Text(
                            text = "Estimated portion: $it",
                            style = MaterialTheme.typography.subtitle1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    product.categories?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Categories: $it",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    
                    // Nutrition Facts
                    product.nutriments?.let {
                        NutritionFactsCard(
                            nutriments = it,
                            isMealAnalysis = product.barcode == "MEAL_PHOTO",
                            servingSize = product.servingSize,
                        )
                    }

                    // Description
                    product.description?.let { desc ->
                        Spacer(Modifier.height(12.dp))
                        DescriptionCard(desc)
                    }

                    // Ingredients
                    product.ingredients?.takeIf { it.isNotEmpty() }?.let { ingr ->
                        Spacer(Modifier.height(12.dp))
                        IngredientsCard(ingr)
                    }

                    // Insights
                    product.insights?.let { insight ->
                        Spacer(Modifier.height(12.dp))
                        InsightsCard(insight)
                    }

                    // Items Breakdown
                    product.items?.takeIf { it.isNotEmpty() }?.let { items ->
                        Spacer(Modifier.height(12.dp))
                        ItemsBreakdownCard(items)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reused Nutrition Facts Card (similar to ProductScannerScreen)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NutritionFactsCard(
    nutriments: ProductNutrimentsResponse,
    isMealAnalysis: Boolean = false,
    servingSize: String? = null,
    productQuantity: Double? = null,
    quantityUnit: String? = null,
) {
    val totalLabel = when {
        isMealAnalysis && servingSize != null -> "Total ($servingSize)"
        isMealAnalysis -> "Total"
        productQuantity != null -> "for ${productQuantity}${quantityUnit ?: "g"}"
        else -> "total estimated"
    }
    Card(shape = RoundedCornerShape(12.dp), elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Nutrition Estimate", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                if (!isMealAnalysis) {
                    Text("per 100g", style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
                }
                Text(totalLabel, style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.primary, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Divider()
            if (isMealAnalysis) {
                MealNutrientRow("Energy", nutriments.energyKcalPkg, "kcal")
                MealNutrientRow("Fat", nutriments.fatPkg, "g")
                MealNutrientRow("Carbohydrates", nutriments.carbohydratesPkg, "g")
                MealNutrientRow("  Sugars", nutriments.sugarsPkg, "g")
                MealNutrientRow("Protein", nutriments.proteinsPkg, "g")
                MealNutrientRow("Fiber", nutriments.fiberPkg, "g")
                MealNutrientRow("Salt", nutriments.saltPkg, "g")
            } else {
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
}

@Composable
private fun MealNutrientRow(label: String, value: Double?, unit: String) {
    if (value == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.body2, modifier = Modifier.weight(1f))
        Text(
            text = "%.1f %s".format(value, unit),
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.width(96.dp),
            textAlign = TextAlign.End,
        )
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
            modifier = Modifier.width(96.dp),
            textAlign = TextAlign.End
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Meal Analysis Detail Cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DescriptionCard(description: String) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("About this Meal", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun IngredientsCard(ingredients: List<String>) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Ingredients", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            ingredients.forEach { ingredient ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("• ", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.primary)
                    Text(ingredient, style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(insights: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.08f),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text("💡", fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp, top = 2.dp))
            Column {
                Text("Health Insights", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(insights, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun ItemsBreakdownCard(items: List<MealItem>) {
    Card(shape = RoundedCornerShape(12.dp), elevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Breakdown by Item", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            // Header row
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Item", style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.8f))
                Text("kcal", style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                Text("Protein", style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                Text("Carbs", style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                Text("Fat", style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
            }
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1.8f)) {
                        Text(item.name, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium)
                        Text(item.estimatedQuantity, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f))
                    }
                    fun fmt(v: Double?) = if (v != null) "%.0f".format(v) else "–"
                    Text(fmt(item.nutrients.calories), style = MaterialTheme.typography.body2, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                    Text(fmt(item.nutrients.protein)  + "g", style = MaterialTheme.typography.body2, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                    Text(fmt(item.nutrients.carbohydrates) + "g", style = MaterialTheme.typography.body2, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                    Text(fmt(item.nutrients.fat) + "g", style = MaterialTheme.typography.body2, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
                }
            }
        }
    }
}
