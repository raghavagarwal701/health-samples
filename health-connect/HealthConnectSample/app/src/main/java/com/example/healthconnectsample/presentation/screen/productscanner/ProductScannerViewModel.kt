/*
 * ViewModel for the Product Scanner screen.
 * Manages both barcode scanning and meal photo analysis state.
 * Barcode scanning → /api/product/{barcode}
 * Meal photo (camera shutter or gallery pick) → /api/meal/analyze
 */
package com.example.healthconnectsample.presentation.screen.productscanner

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.healthconnectsample.data.api.ProductInfoResponse
import com.example.healthconnectsample.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/**
 * All possible UI states for the Product Scanner / Meal Photo screen.
 */
sealed class ScannerUiState {
    /** Camera is active – shows meal viewfinder + Gallery and Capture buttons. */
    object Scanning : ScannerUiState()

    /** A barcode was detected; looking up product in backend. */
    data class LoadingBarcode(val barcode: String) : ScannerUiState()

    /** A meal photo was taken / picked; sending to LLM for analysis. */
    object AnalyzingMeal : ScannerUiState()

    /** Product found via barcode scan. */
    data class ProductFound(val product: ProductInfoResponse) : ScannerUiState()

    /** Meal analysed successfully – same result card as ProductFound. */
    data class MealFound(val product: ProductInfoResponse) : ScannerUiState()

    /** Barcode not found in OpenFoodFacts. */
    data class NotFound(val barcode: String) : ScannerUiState()

    /** Any error (network, parse, API). */
    data class Error(val message: String) : ScannerUiState()
}

class ProductScannerViewModel : ViewModel() {

    val uiState: MutableState<ScannerUiState> = mutableStateOf(ScannerUiState.Scanning)

    /** Track the last scanned barcode to avoid duplicate rapid-fire lookups. */
    private var lastScannedBarcode: String? = null

    // ─────────────────────────────────────────────
    // Barcode flow (unchanged)
    // ─────────────────────────────────────────────

    /**
     * Called when ML Kit detects a barcode. De-duplicates rapid scans.
     */
    fun onBarcodeDetected(barcode: String) {
        if (barcode == lastScannedBarcode) return
        val state = uiState.value
        if (state is ScannerUiState.LoadingBarcode || state is ScannerUiState.AnalyzingMeal) return

        lastScannedBarcode = barcode
        lookupProduct(barcode)
    }

    private fun lookupProduct(barcode: String) {
        uiState.value = ScannerUiState.LoadingBarcode(barcode)
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getProduct(barcode)
                if (response.isSuccessful) {
                    val body = response.body()!!
                    uiState.value = if (body.status == "found" && body.product != null) {
                        ScannerUiState.ProductFound(body.product)
                    } else {
                        ScannerUiState.NotFound(barcode)
                    }
                } else {
                    uiState.value = ScannerUiState.Error("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                uiState.value = ScannerUiState.Error(
                    "Connection error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    // ─────────────────────────────────────────────
    // Meal photo flow
    // ─────────────────────────────────────────────

    /**
     * Called when the user taps the shutter button (CameraX ImageCapture result).
     * [imageBytes] is raw JPEG bytes from the captured image.
     */
    fun onPhotoTaken(imageBytes: ByteArray) {
        analyzeMeal(imageBytes, "image/jpeg")
    }

    /**
     * Called when the user picks an image from the gallery (System Photo Picker URI).
     * Decodes the URI into a Bitmap first, then re-compresses to JPEG so the backend
     * always receives a valid JPEG regardless of the original format (HEIC, WEBP, PNG…).
     */
    fun onGalleryImagePicked(context: Context, uri: Uri) {
        uiState.value = ScannerUiState.AnalyzingMeal
        viewModelScope.launch {
            try {
                val jpegBytes = withContext(Dispatchers.IO) {
                    // Decode via BitmapFactory – handles JPEG, PNG, WEBP, HEIC, etc.
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                    if (bitmap == null) return@withContext null

                    // Re-compress to JPEG so Pillow on the backend can always open it
                    val out = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    bitmap.recycle()
                    out.toByteArray()
                }

                if (jpegBytes == null || jpegBytes.isEmpty()) {
                    uiState.value = ScannerUiState.Error("Could not read image from gallery")
                    return@launch
                }
                analyzeMeal(jpegBytes, "image/jpeg")
            } catch (e: Exception) {
                uiState.value = ScannerUiState.Error(
                    "Gallery error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    private fun analyzeMeal(imageBytes: ByteArray, mimeType: String) {
        uiState.value = ScannerUiState.AnalyzingMeal
        viewModelScope.launch {
            try {
                val requestBody = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("image", "meal.jpg", requestBody)

                val response = RetrofitClient.apiService.analyzeMeal(part)
                if (response.isSuccessful) {
                    val body = response.body()!!
                    uiState.value = if (body.status == "analyzed" && body.product != null) {
                        ScannerUiState.MealFound(body.product)
                    } else {
                        ScannerUiState.Error(body.error ?: "Meal analysis returned no data")
                    }
                } else {
                    uiState.value = ScannerUiState.Error("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                uiState.value = ScannerUiState.Error(
                    "Connection error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    // ─────────────────────────────────────────────
    // Reset
    // ─────────────────────────────────────────────

    /** Reset scanner to camera/capture mode. */
    fun resetToScanning() {
        lastScannedBarcode = null
        uiState.value = ScannerUiState.Scanning
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProductScannerViewModel() as T
        }
    }
}
