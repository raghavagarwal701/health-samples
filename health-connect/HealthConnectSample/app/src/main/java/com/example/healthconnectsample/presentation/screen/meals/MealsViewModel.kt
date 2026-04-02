/*
 * MealsViewModel
 *
 * Manages:
 *  - selectedDate  – the date whose meal log is currently displayed (defaults to today).
 *  - mealLog       – in-memory map of LocalDate → list of MealEntry objects.
 *  - Camera scanning sub-state (mirrors ScannerUiState pattern) so the Meals FAB
 *    can open a camera view without a separate navigation destination.
 */
package com.example.healthconnectsample.presentation.screen.meals

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.healthconnectsample.data.api.ProductInfoResponse
import com.example.healthconnectsample.data.api.ProductNutrimentsResponse
import com.example.healthconnectsample.data.api.MealItem
import com.example.healthconnectsample.data.api.MealItemNutrients
import com.example.healthconnectsample.data.api.MealTextRequest
import com.example.healthconnectsample.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────

data class MealEntry(
    val product: ProductInfoResponse,
    val timestamp: LocalTime = LocalTime.now(),
    val imageUri: String? = null,
)

// ─────────────────────────────────────────────
// Camera sub-state (for the FAB-triggered camera)
// ─────────────────────────────────────────────

sealed class MealCameraState {
    /** Meal log is being shown – camera is closed. */
    object LogView : MealCameraState()

    /** Camera is open and scanning. */
    object CameraOpen : MealCameraState()

    /** Photo taken – waiting for user review + optional note before upload. */
    data class PhotoPreview(
        val imageBytes: ByteArray,
        val imageUri: String?,
    ) : MealCameraState()

    /** Photo has been sent to the API; waiting for result. */
    object Analyzing : MealCameraState()

    /** API returned an error. */
    data class CameraError(val message: String) : MealCameraState()

    /** Prompt user to choose between camera/image or text input. */
    object InputMethodChooser : MealCameraState()

    /** FatSecret autocomplete – showing suggestion strings only. */
    data class FatSecretAutocompleteResults(
        val query: String,
        val suggestions: List<String>,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val suppressAutoSearch: Boolean = false,
    ) : MealCameraState()

    /** FatSecret meal search – showing results. */
    data class FatSecretSearchResults(
        val query: String,
        val autocompleteQuery: String,
        val foods: List<com.example.healthconnectsample.data.api.FatSecretFood>,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : MealCameraState()

    /** FatSecret food detail – showing servings and input for quantity. */
    data class FatSecretFoodDetail(
        val food: com.example.healthconnectsample.data.api.FatSecretFood,
        val selectedServingIndex: Int? = null,
        val quantity: Double = 1.0,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : MealCameraState()

    /** FatSecret meal preview – showing calculated totals before adding. */
    data class FatSecretMealPreview(
        val foodName: String,
        val servingDescription: String,
        val quantity: Double,
        val totals: Map<String, Double>?,
    ) : MealCameraState()
}

// ─────────────────────────────────────────────
// Persistence constants
// ─────────────────────────────────────────────

private const val PREFS_NAME = "pulse_meal_log"
private const val PREFS_KEY  = "meal_log_v2"

// ─────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────

class MealsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var fatSecretAutocompleteToken: Long = 0L
    private var fatSecretSearchToken: Long = 0L
    private var fatSecretFoodDetailToken: Long = 0L
    private var lastFatSecretTypedQuery: String = ""
    private val fatSecretAutocompleteCache = mutableMapOf<String, Pair<List<String>, String?>>()
    private var lastFatSecretSearchResults: MealCameraState.FatSecretSearchResults? = null

    // ── Date ──────────────────────────────────
    // IMPORTANT: these must be declared BEFORE init so loadFromPrefs() can write to them
    val selectedDate: MutableState<LocalDate> = mutableStateOf(LocalDate.now())

    // ── Meal log (date → entries) ──────────────
    private val _mealLog: MutableState<Map<LocalDate, List<MealEntry>>> =
        mutableStateOf(emptyMap())

    init {
        loadFromPrefs()
    }

    /** Returns the entries for the currently selected date. */
    fun entriesForDate(date: LocalDate): List<MealEntry> =
        _mealLog.value[date] ?: emptyList()

    // ── Viewing Meal Detail ───────────────────
    val viewingMeal: MutableState<MealEntry?> = mutableStateOf(null)

    fun viewMeal(entry: MealEntry) {
        viewingMeal.value = entry
    }

    fun closeMeal() {
        viewingMeal.value = null
    }

    // ── Camera sub-state ──────────────────────
    val cameraState: MutableState<MealCameraState> = mutableStateOf(MealCameraState.LogView)

    // ── Date navigation ───────────────────────

    fun previousDay() {
        selectedDate.value = selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        selectedDate.value = selectedDate.value.plusDays(1)
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    // ── Camera controls ───────────────────────

    fun showInputChooser() {
        cameraState.value = MealCameraState.InputMethodChooser
    }

    fun openCamera() {
        cameraState.value = MealCameraState.CameraOpen
    }

    fun closeCamera() {
        cameraState.value = MealCameraState.LogView
    }

    // ── Photo taken via CameraX shutter ───────

    fun onPhotoTaken(imageBytes: ByteArray, imageUri: Uri?) {
        // Show preview instead of immediately uploading
        cameraState.value = MealCameraState.PhotoPreview(
            imageBytes = imageBytes,
            imageUri = imageUri?.toString(),
        )
    }

    /** User confirmed the preview – send photo + optional note to backend. */
    fun confirmPhoto(imageBytes: ByteArray, imageUri: String?, note: String) {
        analyzeMeal(imageBytes, "image/jpeg", imageUri, note.trim().ifEmpty { null })
    }

    /** User wants to retake – go back to live camera. */
    fun retakePhoto() {
        cameraState.value = MealCameraState.CameraOpen
    }

    // ── Gallery image picked ───────────────────

    fun onGalleryImagePicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // Save a local copy in internal files dir (cacheDir can be cleared by OS)
                val cachedUri = withContext(Dispatchers.IO) {
                    val tempFile = java.io.File.createTempFile("meal_gallery_", ".jpg", context.filesDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Uri.fromFile(tempFile).toString()
                }

                val jpegBytes = withContext(Dispatchers.IO) {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    } ?: return@withContext null
                    val out = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    bitmap.recycle()
                    out.toByteArray()
                }
                if (jpegBytes == null || jpegBytes.isEmpty()) {
                    cameraState.value =
                        MealCameraState.CameraError("Could not read image from gallery")
                    return@launch
                }
                // Show preview screen for gallery images too
                cameraState.value = MealCameraState.PhotoPreview(
                    imageBytes = jpegBytes,
                    imageUri = cachedUri,
                )
            } catch (e: Exception) {
                cameraState.value =
                    MealCameraState.CameraError(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    // ── Core analysis call ────────────────────

    private fun analyzeMeal(imageBytes: ByteArray, mimeType: String, imageUri: String?, note: String? = null) {
        cameraState.value = MealCameraState.Analyzing
        viewModelScope.launch {
            try {
                val requestBody = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("image", "meal.jpg", requestBody)
                val notePart = note?.let {
                    it.toRequestBody("text/plain".toMediaTypeOrNull())
                }
                val response = RetrofitClient.apiService.analyzeMeal(part, notePart)
                if (response.isSuccessful) {
                    val body = response.body()!!
                    if (body.status == "analyzed" && body.product != null) {
                        addMeal(selectedDate.value, body.product, imageUri)
                        cameraState.value = MealCameraState.LogView
                    } else {
                        cameraState.value =
                            MealCameraState.CameraError(body.error ?: "Analysis returned no data")
                    }
                } else {
                    cameraState.value =
                        MealCameraState.CameraError("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                cameraState.value =
                    MealCameraState.CameraError(e.localizedMessage ?: "Connection error")
            }
        }
    }

    /** Analyse a plain-text description of a meal (no photo required). */
    fun analyzeTextDescription(description: String) {
        cameraState.value = MealCameraState.Analyzing
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.analyzeMealFromText(
                    MealTextRequest(description = description.trim())
                )
                if (response.isSuccessful) {
                    val body = response.body()!!
                    if (body.status == "analyzed" && body.product != null) {
                        addMeal(selectedDate.value, body.product, null)
                        cameraState.value = MealCameraState.LogView
                    } else {
                        cameraState.value =
                            MealCameraState.CameraError(body.error ?: "Analysis returned no data")
                    }
                } else {
                    cameraState.value =
                        MealCameraState.CameraError("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                cameraState.value =
                    MealCameraState.CameraError(e.localizedMessage ?: "Connection error")
            }
        }
    }

    // ── FatSecret meal search ─────────────────

    fun showFatSecretSearch() {
        cameraState.value = MealCameraState.FatSecretAutocompleteResults(
            query = "",
            suggestions = emptyList(),
            isLoading = false,
            errorMessage = null,
            suppressAutoSearch = false
        )
    }

    fun autocompleteFatSecretMeals(query: String) {
        val trimmedQuery = query.trim()
        lastFatSecretTypedQuery = trimmedQuery

        if (trimmedQuery.length < 2) {
            cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                query = trimmedQuery,
                suggestions = emptyList(),
                isLoading = false,
                errorMessage = null,
                suppressAutoSearch = false
            )
            return
        }

        val cachedResult = fatSecretAutocompleteCache[trimmedQuery]
        if (cachedResult != null) {
            cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                query = trimmedQuery,
                suggestions = cachedResult.first,
                isLoading = false,
                errorMessage = cachedResult.second,
                suppressAutoSearch = false
            )
            return
        }

        val requestToken = ++fatSecretAutocompleteToken
        val currentState = cameraState.value as? MealCameraState.FatSecretAutocompleteResults
        cameraState.value = MealCameraState.FatSecretAutocompleteResults(
            query = trimmedQuery,
            suggestions = currentState?.suggestions ?: emptyList(),
            isLoading = true,
            errorMessage = null,
            suppressAutoSearch = false
        )

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.autocompleteFatSecret(
                    expression = trimmedQuery
                )
                if (requestToken != fatSecretAutocompleteToken) return@launch

                val activeState = cameraState.value as? MealCameraState.FatSecretAutocompleteResults
                if (activeState?.query != trimmedQuery) return@launch

                if (response.isSuccessful) {
                    val body = response.body()!!
                    if (body.status == "success") {
                        if ((cameraState.value as? MealCameraState.FatSecretAutocompleteResults)?.query != trimmedQuery) return@launch
                        fatSecretAutocompleteCache[trimmedQuery] = body.suggestions to null
                        cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                            query = trimmedQuery,
                            suggestions = body.suggestions,
                            isLoading = false,
                            errorMessage = null,
                            suppressAutoSearch = false
                        )
                    } else {
                        if ((cameraState.value as? MealCameraState.FatSecretAutocompleteResults)?.query != trimmedQuery) return@launch
                        val error = body.error ?: "No suggestions found"
                        fatSecretAutocompleteCache[trimmedQuery] = emptyList<String>() to error
                        cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                            query = trimmedQuery,
                            suggestions = emptyList(),
                            isLoading = false,
                            errorMessage = error,
                            suppressAutoSearch = false
                        )
                    }
                } else {
                    if ((cameraState.value as? MealCameraState.FatSecretAutocompleteResults)?.query != trimmedQuery) return@launch
                    val error = "Server error: ${response.code()}"
                    fatSecretAutocompleteCache[trimmedQuery] = emptyList<String>() to error
                    cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                        query = trimmedQuery,
                        suggestions = emptyList(),
                        isLoading = false,
                        errorMessage = error,
                        suppressAutoSearch = false
                    )
                }
            } catch (e: Exception) {
                if (requestToken != fatSecretAutocompleteToken) return@launch
                if ((cameraState.value as? MealCameraState.FatSecretAutocompleteResults)?.query != trimmedQuery) return@launch
                val error = e.localizedMessage ?: "Connection error"
                fatSecretAutocompleteCache[trimmedQuery] = emptyList<String>() to error
                cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                    query = trimmedQuery,
                    suggestions = emptyList(),
                    isLoading = false,
                    errorMessage = error,
                    suppressAutoSearch = false
                )
            }
        }
    }

    fun searchFatSecretMeals(query: String) {
        val trimmedQuery = query.trim()
        val currentState = cameraState.value as? MealCameraState.FatSecretSearchResults
        val autocompleteQuery = when (val state = cameraState.value) {
            is MealCameraState.FatSecretAutocompleteResults -> state.query
            is MealCameraState.FatSecretSearchResults -> state.autocompleteQuery
            else -> lastFatSecretTypedQuery
        }.trim()

        if (trimmedQuery.isEmpty()) {
            cameraState.value = MealCameraState.FatSecretSearchResults(
                query = "",
                autocompleteQuery = autocompleteQuery,
                foods = emptyList(),
                isLoading = false,
                errorMessage = null
            )
            return
        }

        val requestToken = ++fatSecretSearchToken
        cameraState.value = MealCameraState.FatSecretSearchResults(
            query = trimmedQuery,
            autocompleteQuery = autocompleteQuery,
            foods = currentState?.foods ?: emptyList(),
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.searchFatSecret(query = trimmedQuery)
                if (requestToken != fatSecretSearchToken) return@launch

                if (response.isSuccessful) {
                    val body = response.body()!!
                    if (body.status == "success") {
                        val newState = MealCameraState.FatSecretSearchResults(
                            query = trimmedQuery,
                            autocompleteQuery = autocompleteQuery,
                            foods = body.results,
                            isLoading = false,
                            errorMessage = null
                        )
                        lastFatSecretSearchResults = newState
                        cameraState.value = newState
                    } else {
                        val newState = MealCameraState.FatSecretSearchResults(
                            query = trimmedQuery,
                            autocompleteQuery = autocompleteQuery,
                            foods = emptyList(),
                            isLoading = false,
                            errorMessage = body.error ?: "No results found"
                        )
                        lastFatSecretSearchResults = newState
                        cameraState.value = newState
                    }
                } else {
                    val newState = MealCameraState.FatSecretSearchResults(
                        query = trimmedQuery,
                        autocompleteQuery = autocompleteQuery,
                        foods = emptyList(),
                        isLoading = false,
                        errorMessage = "Server error: ${response.code()}"
                    )
                    lastFatSecretSearchResults = newState
                    cameraState.value = newState
                }
            } catch (e: Exception) {
                if (requestToken != fatSecretSearchToken) return@launch
                val newState = MealCameraState.FatSecretSearchResults(
                    query = trimmedQuery,
                    autocompleteQuery = autocompleteQuery,
                    foods = emptyList(),
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Connection error"
                )
                lastFatSecretSearchResults = newState
                cameraState.value = newState
            }
        }
    }

    fun backToFatSecretAutocomplete(query: String) {
        fatSecretAutocompleteToken++
        val requestedQuery = query.trim()
        val targetQuery = if (requestedQuery.isNotBlank()) requestedQuery else lastFatSecretTypedQuery
        val cachedResult = fatSecretAutocompleteCache[targetQuery]

        cameraState.value = MealCameraState.FatSecretAutocompleteResults(
            query = targetQuery,
            suggestions = cachedResult?.first ?: emptyList(),
            isLoading = false,
            errorMessage = cachedResult?.second,
            suppressAutoSearch = true
        )
    }

    fun selectFatSecretFood(food: com.example.healthconnectsample.data.api.FatSecretFood) {
        lastFatSecretSearchResults = cameraState.value as? MealCameraState.FatSecretSearchResults
        val requestToken = ++fatSecretFoodDetailToken
        cameraState.value = MealCameraState.FatSecretFoodDetail(
            food = food,
            selectedServingIndex = defaultServingIndex(food),
            quantity = 1.0,
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getFatSecretFood(food.foodId)
                if (requestToken != fatSecretFoodDetailToken) return@launch

                if (response.isSuccessful) {
                    val body = response.body()
                    val detailedFood = body?.food
                    if (body?.status == "success" && detailedFood != null) {
                        cameraState.value = MealCameraState.FatSecretFoodDetail(
                            food = detailedFood,
                            selectedServingIndex = defaultServingIndex(detailedFood),
                            quantity = 1.0,
                            isLoading = false,
                            errorMessage = null
                        )
                    } else {
                        val error = body?.error ?: "Could not load food details"
                        cameraState.value = MealCameraState.FatSecretFoodDetail(
                            food = food,
                            selectedServingIndex = defaultServingIndex(food),
                            quantity = 1.0,
                            isLoading = false,
                            errorMessage = error
                        )
                    }
                } else {
                    cameraState.value = MealCameraState.FatSecretFoodDetail(
                        food = food,
                        selectedServingIndex = defaultServingIndex(food),
                        quantity = 1.0,
                        isLoading = false,
                        errorMessage = "Server error: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                if (requestToken != fatSecretFoodDetailToken) return@launch
                cameraState.value = MealCameraState.FatSecretFoodDetail(
                    food = food,
                    selectedServingIndex = defaultServingIndex(food),
                    quantity = 1.0,
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Connection error"
                )
            }
        }
    }

    fun updateFatSecretServingSelection(servingIndex: Int, quantity: Double) {
        val currentState = cameraState.value
        if (currentState is MealCameraState.FatSecretFoodDetail) {
            cameraState.value = currentState.copy(
                selectedServingIndex = servingIndex,
                quantity = quantity
            )
        }
    }

    fun confirmFatSecretMeal() {
        val currentState = cameraState.value
        if (currentState !is MealCameraState.FatSecretFoodDetail) return
        val selectedServing = currentState.food.servings.getOrNull(currentState.selectedServingIndex ?: -1)
        if (selectedServing == null) {
            cameraState.value = MealCameraState.CameraError("Please select a serving size")
            return
        }

        cameraState.value = MealCameraState.Analyzing
        viewModelScope.launch {
            try {
                val request = com.example.healthconnectsample.data.api.FatSecretMealAddRequest(
                    foodId = currentState.food.foodId,
                    servingDescription = selectedServing.servingDescription,
                    quantity = currentState.quantity
                )
                val response = RetrofitClient.apiService.previewFatSecretMeal(request)
                if (response.isSuccessful) {
                    val body = response.body()!!
                    if (body.status == "success") {
                        cameraState.value = MealCameraState.FatSecretMealPreview(
                            foodName = body.foodName ?: currentState.food.foodName,
                            servingDescription = body.servingDescription ?: "1 serving",
                            quantity = body.quantity ?: currentState.quantity,
                            totals = body.totals
                        )
                    } else {
                        cameraState.value = MealCameraState.CameraError(
                            body.error ?: "Could not calculate meal nutrition"
                        )
                    }
                } else {
                    cameraState.value = MealCameraState.CameraError("Server error: ${response.code()}")
                }
            } catch (e: Exception) {
                cameraState.value =
                    MealCameraState.CameraError(e.localizedMessage ?: "Connection error")
            }
        }
    }

    fun addFatSecretMeal(preview: MealCameraState.FatSecretMealPreview) {
        // Convert FatSecret meal to ProductInfoResponse format for consistent storage
        val product = ProductInfoResponse(
            barcode = "fatsecret_${System.currentTimeMillis()}",
            productName = preview.foodName,
            brands = null,
            categories = null,
            nutriscoreGrade = null,
            nutriments = preview.totals?.let { totals ->
                ProductNutrimentsResponse(
                    energyKcal100g = totals["calories"],
                    carbohydrates100g = totals["carbohydrate"],
                    proteins100g = totals["protein"],
                    fat100g = totals["fat"],
                    fiber100g = totals["fiber"],
                    salt100g = totals["sodium"]?.let { it / 1000 }, // Convert mg to g
                    sugars100g = totals["sugar"]
                )
            },
            description = "Serving: ${preview.servingDescription} (x${preview.quantity})"
        )
        addMeal(selectedDate.value, product, null)
        cameraState.value = MealCameraState.LogView
    }

    fun closeFatSecretSearch() {
        fatSecretFoodDetailToken++
        cameraState.value = MealCameraState.LogView
    }

    fun backFromFatSecretFoodDetail() {
        fatSecretFoodDetailToken++
        val searchState = lastFatSecretSearchResults
        cameraState.value = if (searchState != null) {
            searchState.copy(isLoading = false)
        } else {
            MealCameraState.LogView
        }
    }

    private fun defaultServingIndex(food: com.example.healthconnectsample.data.api.FatSecretFood): Int? {
        if (food.servings.isEmpty()) return null
        val defaultIndex = food.servings.indexOfFirst { it.isDefault == 1 }
        if (defaultIndex >= 0) return defaultIndex
        val derivedServingIndex = food.servings.indexOfFirst { it.servingId == 0 }
        return if (derivedServingIndex >= 0) derivedServingIndex else 0
    }

    // ── Meal log management ───────────────────

    fun addMeal(date: LocalDate, product: ProductInfoResponse, imageUri: String?) {
        val existing = _mealLog.value.toMutableMap()
        val list = existing.getOrDefault(date, emptyList()).toMutableList()
        list.add(MealEntry(product = product, imageUri = imageUri))
        existing[date] = list
        _mealLog.value = existing
        saveToPrefs()
    }

    fun removeMeal(date: LocalDate, index: Int) {
        val existing = _mealLog.value.toMutableMap()
        val list = existing.getOrDefault(date, emptyList()).toMutableList()
        if (index in list.indices) list.removeAt(index)
        existing[date] = list
        _mealLog.value = existing
        saveToPrefs()
    }

    // ── SharedPreferences persistence (org.json – no Gson TypeToken) ─────────

    private fun saveToPrefs() {
        try {
            val daysArray = org.json.JSONArray()
            _mealLog.value.forEach { (date, entries) ->
                val entriesArray = org.json.JSONArray()
                entries.forEach { e ->
                    val p = e.product
                    val entryObj = org.json.JSONObject().apply {
                        put("timestamp", e.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                        put("imageUri", e.imageUri ?: org.json.JSONObject.NULL)
                        put("barcode", p.barcode)
                        put("product_name", p.productName ?: "")
                        put("brands", p.brands ?: "")
                        put("categories", p.categories ?: "")
                        put("serving_size", p.servingSize ?: "")
                        put("ingredients_text", p.ingredientsText ?: "")
                        put("image_url", p.imageUrl ?: "")
                        put("nutriscore_grade", p.nutriscoreGrade ?: "")
                        put("product_quantity", p.productQuantity ?: org.json.JSONObject.NULL)
                        put("product_quantity_unit", p.productQuantityUnit ?: "")
                        put("serving_quantity", p.servingQuantity ?: org.json.JSONObject.NULL)
                        put("description", p.description ?: "")
                        put("insights", p.insights ?: "")
                        p.ingredients?.let { ingr ->
                            put("ingredients", org.json.JSONArray(ingr))
                        }
                        p.items?.let { itemList ->
                            val itemsArr = org.json.JSONArray()
                            itemList.forEach { item ->
                                itemsArr.put(org.json.JSONObject().apply {
                                    put("name", item.name)
                                    put("estimated_quantity", item.estimatedQuantity)
                                    put("nutrients", org.json.JSONObject().apply {
                                        put("calories", item.nutrients.calories ?: org.json.JSONObject.NULL)
                                        put("protein", item.nutrients.protein ?: org.json.JSONObject.NULL)
                                        put("carbohydrates", item.nutrients.carbohydrates ?: org.json.JSONObject.NULL)
                                        put("fat", item.nutrients.fat ?: org.json.JSONObject.NULL)
                                        put("sugar", item.nutrients.sugar ?: org.json.JSONObject.NULL)
                                        put("fiber", item.nutrients.fiber ?: org.json.JSONObject.NULL)
                                        put("sodium", item.nutrients.sodium ?: org.json.JSONObject.NULL)
                                    })
                                })
                            }
                            put("items", itemsArr)
                        }
                        p.nutriments?.let { n ->
                            put("nutriments", org.json.JSONObject().apply {
                                put("energy_kcal_100g", n.energyKcal100g ?: org.json.JSONObject.NULL)
                                put("fat_100g", n.fat100g ?: org.json.JSONObject.NULL)
                                put("carbohydrates_100g", n.carbohydrates100g ?: org.json.JSONObject.NULL)
                                put("sugars_100g", n.sugars100g ?: org.json.JSONObject.NULL)
                                put("proteins_100g", n.proteins100g ?: org.json.JSONObject.NULL)
                                put("fiber_100g", n.fiber100g ?: org.json.JSONObject.NULL)
                                put("salt_100g", n.salt100g ?: org.json.JSONObject.NULL)
                                put("energy_kcal_pkg", n.energyKcalPkg ?: org.json.JSONObject.NULL)
                                put("fat_pkg", n.fatPkg ?: org.json.JSONObject.NULL)
                                put("carbohydrates_pkg", n.carbohydratesPkg ?: org.json.JSONObject.NULL)
                                put("sugars_pkg", n.sugarsPkg ?: org.json.JSONObject.NULL)
                                put("proteins_pkg", n.proteinsPkg ?: org.json.JSONObject.NULL)
                                put("fiber_pkg", n.fiberPkg ?: org.json.JSONObject.NULL)
                                put("salt_pkg", n.saltPkg ?: org.json.JSONObject.NULL)
                            })
                        }
                    }
                    entriesArray.put(entryObj)
                }
                daysArray.put(org.json.JSONObject().apply {
                    put("date", date.toString())
                    put("entries", entriesArray)
                })
            }
            prefs.edit().putString(PREFS_KEY, daysArray.toString()).commit()
        } catch (e: Exception) {
            android.util.Log.e("MealsVM", "saveToPrefs failed", e)
        }
    }

    private fun loadFromPrefs() {
        val json = prefs.getString(PREFS_KEY, null) ?: return
        try {
            val daysArray = org.json.JSONArray(json)
            val log = mutableMapOf<LocalDate, List<MealEntry>>()
            for (i in 0 until daysArray.length()) {
                val dayObj = daysArray.getJSONObject(i)
                val date = LocalDate.parse(dayObj.getString("date"))
                val entriesArray = dayObj.getJSONArray("entries")
                val entries = mutableListOf<MealEntry>()
                for (j in 0 until entriesArray.length()) {
                    val e = entriesArray.getJSONObject(j)
                    val timestamp = LocalTime.parse(
                        e.getString("timestamp"),
                        DateTimeFormatter.ofPattern("HH:mm:ss")
                    )
                    val imageUri = if (e.isNull("imageUri")) null else e.optString("imageUri")
                    val nutrObj = e.optJSONObject("nutriments")
                    val nutriments = nutrObj?.let { n ->
                        fun dbl(key: String): Double? = n.optDouble(key).takeUnless { it.isNaN() }
                        ProductNutrimentsResponse(
                            energyKcal100g    = dbl("energy_kcal_100g"),
                            fat100g           = dbl("fat_100g"),
                            carbohydrates100g = dbl("carbohydrates_100g"),
                            sugars100g        = dbl("sugars_100g"),
                            proteins100g      = dbl("proteins_100g"),
                            fiber100g         = dbl("fiber_100g"),
                            salt100g          = dbl("salt_100g"),
                            energyKcalPkg     = dbl("energy_kcal_pkg"),
                            fatPkg            = dbl("fat_pkg"),
                            carbohydratesPkg  = dbl("carbohydrates_pkg"),
                            sugarsPkg         = dbl("sugars_pkg"),
                            proteinsPkg       = dbl("proteins_pkg"),
                            fiberPkg          = dbl("fiber_pkg"),
                            saltPkg           = dbl("salt_pkg"),
                        )
                    }
                    val product = ProductInfoResponse(
                        barcode              = e.optString("barcode", ""),
                        productName          = e.optString("product_name").ifEmpty { null },
                        brands               = e.optString("brands").ifEmpty { null },
                        categories           = e.optString("categories").ifEmpty { null },
                        servingSize          = e.optString("serving_size").ifEmpty { null },
                        ingredientsText      = e.optString("ingredients_text").ifEmpty { null },
                        imageUrl             = e.optString("image_url").ifEmpty { null },
                        nutriscoreGrade      = e.optString("nutriscore_grade").ifEmpty { null },
                        productQuantity      = e.optDouble("product_quantity").takeUnless { it.isNaN() },
                        productQuantityUnit  = e.optString("product_quantity_unit").ifEmpty { null },
                        servingQuantity      = e.optDouble("serving_quantity").takeUnless { it.isNaN() },
                        nutriments           = nutriments,
                        description          = e.optString("description").ifEmpty { null },
                        insights             = e.optString("insights").ifEmpty { null },
                        ingredients          = e.optJSONArray("ingredients")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        },
                        items                = e.optJSONArray("items")?.let { arr ->
                            (0 until arr.length()).map { idx ->
                                val itemObj = arr.getJSONObject(idx)
                                val nObj = itemObj.optJSONObject("nutrients")
                                fun nd(key: String) = nObj?.optDouble(key)?.takeUnless { it.isNaN() }
                                MealItem(
                                    name = itemObj.optString("name"),
                                    estimatedQuantity = itemObj.optString("estimated_quantity"),
                                    nutrients = MealItemNutrients(
                                        calories = nd("calories"),
                                        protein = nd("protein"),
                                        carbohydrates = nd("carbohydrates"),
                                        fat = nd("fat"),
                                        sugar = nd("sugar"),
                                        fiber = nd("fiber"),
                                        sodium = nd("sodium"),
                                    )
                                )
                            }
                        },
                    )
                    entries.add(MealEntry(product = product, timestamp = timestamp, imageUri = imageUri))
                }
                log[date] = entries
            }
            _mealLog.value = log
        } catch (e: Exception) {
            // Log but NEVER delete persisted data – keep it safe for the next attempt
            android.util.Log.e("MealsVM", "loadFromPrefs failed", e)
        }
    }

    // ── Computed totals for a date ─────────────

    fun totalKcal(date: LocalDate): Double =
        entriesForDate(date).sumOf { it.product.nutriments?.energyKcalPkg ?: 0.0 }

    fun totalProtein(date: LocalDate): Double =
        entriesForDate(date).sumOf { it.product.nutriments?.proteinsPkg ?: 0.0 }

    fun totalCarbs(date: LocalDate): Double =
        entriesForDate(date).sumOf { it.product.nutriments?.carbohydratesPkg ?: 0.0 }

    fun totalFat(date: LocalDate): Double =
        entriesForDate(date).sumOf { it.product.nutriments?.fatPkg ?: 0.0 }

    // ── Factory ───────────────────────────────

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            MealsViewModel(application) as T
    }
}
