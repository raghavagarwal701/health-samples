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
import com.example.healthconnectsample.data.api.MealAnalysisPayloadResponse
import com.example.healthconnectsample.data.api.ProductInfoResponse
import com.example.healthconnectsample.data.api.ProductNutrimentsResponse
import com.example.healthconnectsample.data.api.MealTextRequest
import com.example.healthconnectsample.data.api.RetrofitClient
import com.google.gson.Gson
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
import java.util.Locale

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
        val recentSearches: List<String> = emptyList(),
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

    /** FatSecret fallback text flow – lets AI analyze typed meal description. */
    data class FatSecretAiMealInput(
        val autocompleteQuery: String,
        val mealText: String,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : MealCameraState()
}

// ─────────────────────────────────────────────
// Persistence constants
// ─────────────────────────────────────────────

private const val PREFS_NAME = "pulse_meal_log"
private const val PREFS_KEY  = "meal_log_v2"
private const val FATSECRET_CACHE_PREFS = "pulse_fatsecret_cache"
private const val FATSECRET_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
private const val FATSECRET_RECENT_SEARCHES_KEY = "fatsecret_recent_searches"
private const val FATSECRET_RECENT_SEARCHES_LIMIT = 8

private data class FatSecretAutocompleteCacheEntry(
    val suggestions: List<String>,
    val savedAtMs: Long,
)

private data class FatSecretCacheEnvelope(
    val savedAtMs: Long,
    val payloadJson: String,
)

// ─────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────

class MealsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val fatSecretCachePrefs = application.getSharedPreferences(FATSECRET_CACHE_PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()
    private var fatSecretAutocompleteToken: Long = 0L
    private var fatSecretSearchToken: Long = 0L
    private var fatSecretFoodDetailToken: Long = 0L
    private var fatSecretAiAnalyzeToken: Long = 0L
    private var lastFatSecretTypedQuery: String = ""
    private val fatSecretAutocompleteCache = mutableMapOf<String, FatSecretAutocompleteCacheEntry>()
    private val fatSecretRecentSearches = loadFatSecretRecentSearches().toMutableList()
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

    private fun isFatSecretCacheFresh(savedAtMs: Long): Boolean {
        return System.currentTimeMillis() - savedAtMs <= FATSECRET_CACHE_TTL_MS
    }

    private fun fatSecretCacheKey(prefix: String, value: String): String {
        val normalized = value.trim().lowercase(Locale.US)
        return "$prefix:$normalized"
    }

    private fun saveFatSecretCache(cacheKey: String, payload: Any) {
        try {
            val envelope = FatSecretCacheEnvelope(
                savedAtMs = System.currentTimeMillis(),
                payloadJson = gson.toJson(payload),
            )
            fatSecretCachePrefs.edit().putString(cacheKey, gson.toJson(envelope)).apply()
        } catch (e: Exception) {
            android.util.Log.e("MealsVM", "saveFatSecretCache failed", e)
        }
    }

    private inline fun <reified T> loadFatSecretCache(cacheKey: String): T? {
        val raw = fatSecretCachePrefs.getString(cacheKey, null) ?: return null
        val envelope = runCatching { gson.fromJson(raw, FatSecretCacheEnvelope::class.java) }.getOrNull() ?: return null
        if (!isFatSecretCacheFresh(envelope.savedAtMs)) {
            fatSecretCachePrefs.edit().remove(cacheKey).apply()
            return null
        }
        return runCatching { gson.fromJson(envelope.payloadJson, T::class.java) }.getOrNull()
    }

    private fun loadFreshAutocompleteEntry(query: String): FatSecretAutocompleteCacheEntry? {
        val cacheKey = fatSecretCacheKey("autocomplete", query)
        val inMemory = fatSecretAutocompleteCache[cacheKey]
        if (inMemory != null) {
            if (isFatSecretCacheFresh(inMemory.savedAtMs)) {
                return inMemory
            }
            fatSecretAutocompleteCache.remove(cacheKey)
        }

        val persisted = loadFatSecretCache<FatSecretAutocompleteCacheEntry>(cacheKey)
        if (persisted != null) {
            fatSecretAutocompleteCache[cacheKey] = persisted
        }
        return persisted
    }

    private fun saveAutocompleteEntry(query: String, suggestions: List<String>) {
        val cacheKey = fatSecretCacheKey("autocomplete", query)
        val entry = FatSecretAutocompleteCacheEntry(
            suggestions = suggestions,
            savedAtMs = System.currentTimeMillis(),
        )
        fatSecretAutocompleteCache[cacheKey] = entry
        saveFatSecretCache(cacheKey, entry)
    }

    private fun loadFatSecretRecentSearches(): List<String> {
        val raw = fatSecretCachePrefs.getString(FATSECRET_RECENT_SEARCHES_KEY, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFatSecretRecentSearches() {
        val array = org.json.JSONArray()
        fatSecretRecentSearches.forEach { array.put(it) }
        fatSecretCachePrefs.edit().putString(FATSECRET_RECENT_SEARCHES_KEY, array.toString()).apply()
    }

    private fun recentSearchesSnapshot(): List<String> = fatSecretRecentSearches.toList()

    private fun addFatSecretRecentSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        fatSecretRecentSearches.removeAll { it.equals(normalized, ignoreCase = true) }
        fatSecretRecentSearches.add(0, normalized)
        if (fatSecretRecentSearches.size > FATSECRET_RECENT_SEARCHES_LIMIT) {
            fatSecretRecentSearches.subList(FATSECRET_RECENT_SEARCHES_LIMIT, fatSecretRecentSearches.size).clear()
        }
        saveFatSecretRecentSearches()
    }

    fun removeFatSecretRecentSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val removed = fatSecretRecentSearches.removeAll { it.equals(normalized, ignoreCase = true) }
        if (!removed) return
        saveFatSecretRecentSearches()

        val state = cameraState.value as? MealCameraState.FatSecretAutocompleteResults ?: return
        cameraState.value = state.copy(recentSearches = recentSearchesSnapshot())
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
                    if (body.status == "analyzed" && body.meal != null) {
                        addMeal(selectedDate.value, mealToProductInfo(body.meal), imageUri)
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
                    if (body.status == "analyzed" && body.meal != null) {
                        addMeal(selectedDate.value, mealToProductInfo(body.meal), null)
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
            recentSearches = recentSearchesSnapshot(),
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
                recentSearches = recentSearchesSnapshot(),
                isLoading = false,
                errorMessage = null,
                suppressAutoSearch = false
            )
            return
        }

        val cachedResult = loadFreshAutocompleteEntry(trimmedQuery)
        if (cachedResult != null) {
            cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                query = trimmedQuery,
                suggestions = cachedResult.suggestions,
                recentSearches = recentSearchesSnapshot(),
                isLoading = false,
                errorMessage = null,
                suppressAutoSearch = false
            )
            return
        }

        val requestToken = ++fatSecretAutocompleteToken
        val currentState = cameraState.value as? MealCameraState.FatSecretAutocompleteResults
        cameraState.value = MealCameraState.FatSecretAutocompleteResults(
            query = trimmedQuery,
            suggestions = currentState?.suggestions ?: emptyList(),
            recentSearches = recentSearchesSnapshot(),
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
                        saveAutocompleteEntry(trimmedQuery, body.suggestions)
                        cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                            query = trimmedQuery,
                            suggestions = body.suggestions,
                            recentSearches = recentSearchesSnapshot(),
                            isLoading = false,
                            errorMessage = null,
                            suppressAutoSearch = false
                        )
                    } else {
                        if ((cameraState.value as? MealCameraState.FatSecretAutocompleteResults)?.query != trimmedQuery) return@launch
                        val error = body.error ?: "No suggestions found"
                        cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                            query = trimmedQuery,
                            suggestions = emptyList(),
                            recentSearches = recentSearchesSnapshot(),
                            isLoading = false,
                            errorMessage = error,
                            suppressAutoSearch = false
                        )
                    }
                } else {
                    if ((cameraState.value as? MealCameraState.FatSecretAutocompleteResults)?.query != trimmedQuery) return@launch
                    val error = "Server error: ${response.code()}"
                    cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                        query = trimmedQuery,
                        suggestions = emptyList(),
                        recentSearches = recentSearchesSnapshot(),
                        isLoading = false,
                        errorMessage = error,
                        suppressAutoSearch = false
                    )
                }
            } catch (e: Exception) {
                if (requestToken != fatSecretAutocompleteToken) return@launch
                if ((cameraState.value as? MealCameraState.FatSecretAutocompleteResults)?.query != trimmedQuery) return@launch
                val error = e.localizedMessage ?: "Connection error"
                cameraState.value = MealCameraState.FatSecretAutocompleteResults(
                    query = trimmedQuery,
                    suggestions = emptyList(),
                    recentSearches = recentSearchesSnapshot(),
                    isLoading = false,
                    errorMessage = error,
                    suppressAutoSearch = false
                )
            }
        }
    }

    fun searchFatSecretMeals(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotBlank()) {
            addFatSecretRecentSearch(trimmedQuery)
        }
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

        val cachedSearch = loadFatSecretCache<com.example.healthconnectsample.data.api.FatSecretSearchResponse>(
            fatSecretCacheKey("search", trimmedQuery)
        )
        if (cachedSearch?.status == "success") {
            val newState = MealCameraState.FatSecretSearchResults(
                query = trimmedQuery,
                autocompleteQuery = autocompleteQuery,
                foods = cachedSearch.results,
                isLoading = false,
                errorMessage = null
            )
            lastFatSecretSearchResults = newState
            cameraState.value = newState
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
                        saveFatSecretCache(fatSecretCacheKey("search", trimmedQuery), body)
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
        val cachedResult = loadFreshAutocompleteEntry(targetQuery)

        cameraState.value = MealCameraState.FatSecretAutocompleteResults(
            query = targetQuery,
            suggestions = cachedResult?.suggestions ?: emptyList(),
            recentSearches = recentSearchesSnapshot(),
            isLoading = false,
            errorMessage = null,
            suppressAutoSearch = true
        )
    }

    fun openFatSecretAiMealInput(query: String) {
        val normalized = query.trim().ifBlank { lastFatSecretTypedQuery }
        if (normalized.isNotBlank()) {
            lastFatSecretTypedQuery = normalized
        }
        cameraState.value = MealCameraState.FatSecretAiMealInput(
            autocompleteQuery = normalized,
            mealText = normalized,
            isLoading = false,
            errorMessage = null
        )
    }

    fun backFromFatSecretAiMealInput(currentText: String) {
        backToFatSecretAutocomplete(currentText)
    }

    fun analyzeFatSecretAiMealText(description: String) {
        val currentState = cameraState.value as? MealCameraState.FatSecretAiMealInput ?: return
        val normalizedDescription = description.trim()
        if (normalizedDescription.isBlank()) {
            cameraState.value = currentState.copy(
                mealText = description,
                isLoading = false,
                errorMessage = "Please enter a meal description"
            )
            return
        }

        val requestToken = ++fatSecretAiAnalyzeToken
        cameraState.value = currentState.copy(
            mealText = description,
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.analyzeMealFromText(
                    MealTextRequest(description = normalizedDescription)
                )
                if (requestToken != fatSecretAiAnalyzeToken) return@launch

                if (response.isSuccessful) {
                    val body = response.body()!!
                    if (body.status == "analyzed" && body.meal != null) {
                        addMeal(selectedDate.value, mealToProductInfo(body.meal), null)
                        cameraState.value = MealCameraState.LogView
                    } else {
                        cameraState.value = MealCameraState.FatSecretAiMealInput(
                            autocompleteQuery = currentState.autocompleteQuery,
                            mealText = description,
                            isLoading = false,
                            errorMessage = body.error ?: "Analysis returned no data"
                        )
                    }
                } else {
                    cameraState.value = MealCameraState.FatSecretAiMealInput(
                        autocompleteQuery = currentState.autocompleteQuery,
                        mealText = description,
                        isLoading = false,
                        errorMessage = "Server error: ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                if (requestToken != fatSecretAiAnalyzeToken) return@launch
                cameraState.value = MealCameraState.FatSecretAiMealInput(
                    autocompleteQuery = currentState.autocompleteQuery,
                    mealText = description,
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Connection error"
                )
            }
        }
    }

    fun selectFatSecretFood(food: com.example.healthconnectsample.data.api.FatSecretFood) {
        lastFatSecretSearchResults = cameraState.value as? MealCameraState.FatSecretSearchResults
        val cacheKey = fatSecretCacheKey("food", food.foodId.toString())
        val cachedFood = loadFatSecretCache<com.example.healthconnectsample.data.api.FatSecretFoodResponse>(cacheKey)
        if (cachedFood?.status == "success" && cachedFood.food != null) {
            cameraState.value = MealCameraState.FatSecretFoodDetail(
                food = cachedFood.food,
                selectedServingIndex = defaultServingIndex(cachedFood.food),
                quantity = 1.0,
                isLoading = false,
                errorMessage = null
            )
            return
        }

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
                        saveFatSecretCache(cacheKey, body)
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

        val product = buildFatSecretMealProduct(
            food = currentState.food,
            serving = selectedServing,
            quantity = currentState.quantity
        )
        addMeal(selectedDate.value, product, null)
        cameraState.value = MealCameraState.LogView
    }

    private fun buildFatSecretMealProduct(
        food: com.example.healthconnectsample.data.api.FatSecretFood,
        serving: com.example.healthconnectsample.data.api.FatSecretServing,
        quantity: Double,
    ): ProductInfoResponse {
        val safeQuantity = quantity.coerceAtLeast(0.1)
        val scale: (Double?) -> Double? = { value -> value?.times(safeQuantity) }
        val servingLabel = serving.servingDescription.trim().ifEmpty { "1 serving" }
        val barcodeSuffix = serving.servingId?.toString() ?: "serving"
        val caloriesTotal = scale(serving.calories)
        val carbsTotal = scale(serving.carbohydrate)
        val proteinTotal = scale(serving.protein)
        val fatTotal = scale(serving.fat)
        val fiberTotal = scale(serving.fiber)
        val sugarTotal = scale(serving.sugar)
        val sodiumTotalMg = scale(serving.sodium)
        val saltTotalG = sodiumTotalMg?.let { (it / 1000.0) * 2.5 }

        return ProductInfoResponse(
            barcode = "fatsecret_${food.foodId}_${barcodeSuffix}_${System.currentTimeMillis()}",
            productName = food.foodName,
            brands = food.brandName,
            categories = food.foodType,
            servingSize = "$servingLabel (x$safeQuantity)",
            nutriscoreGrade = null,
            nutriments = ProductNutrimentsResponse(
                energyKcal100g = caloriesTotal,
                fat100g = fatTotal,
                carbohydrates100g = carbsTotal,
                sugars100g = sugarTotal,
                proteins100g = proteinTotal,
                fiber100g = fiberTotal,
                salt100g = saltTotalG,
                energyKcalPkg = caloriesTotal,
                fatPkg = fatTotal,
                carbohydratesPkg = carbsTotal,
                sugarsPkg = sugarTotal,
                proteinsPkg = proteinTotal,
                fiberPkg = fiberTotal,
                saltPkg = saltTotalG
            )
        )
    }

    fun closeFatSecretSearch() {
        fatSecretFoodDetailToken++
        fatSecretAiAnalyzeToken++
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

    private fun mealToProductInfo(meal: MealAnalysisPayloadResponse): ProductInfoResponse {
        val n = meal.nutritionalValue
        val calories = n.calories
        val fat = n.fat
        val carbs = n.carbohydrate
        val sugar = n.sugar
        val protein = n.protein
        val fiber = n.fiber
        val salt = n.sodium?.let { (it / 1000.0) * 2.5 }

        return ProductInfoResponse(
            barcode = "meal_ai_${System.currentTimeMillis()}",
            productName = meal.nameOfMeal,
            brands = "AI Meal Analysis",
            categories = "Meal",
            servingSize = meal.servingSize,
            nutriments = ProductNutrimentsResponse(
                energyKcal100g = calories,
                fat100g = fat,
                carbohydrates100g = carbs,
                sugars100g = sugar,
                proteins100g = protein,
                fiber100g = fiber,
                salt100g = salt,
                energyKcalPkg = calories,
                fatPkg = fat,
                carbohydratesPkg = carbs,
                sugarsPkg = sugar,
                proteinsPkg = protein,
                fiberPkg = fiber,
                saltPkg = salt
            )
        )
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
        entriesForDate(date).sumOf {
            it.product.nutriments?.energyKcalPkg ?: it.product.nutriments?.energyKcal100g ?: 0.0
        }

    fun totalProtein(date: LocalDate): Double =
        entriesForDate(date).sumOf {
            it.product.nutriments?.proteinsPkg ?: it.product.nutriments?.proteins100g ?: 0.0
        }

    fun totalCarbs(date: LocalDate): Double =
        entriesForDate(date).sumOf {
            it.product.nutriments?.carbohydratesPkg ?: it.product.nutriments?.carbohydrates100g ?: 0.0
        }

    fun totalFat(date: LocalDate): Double =
        entriesForDate(date).sumOf {
            it.product.nutriments?.fatPkg ?: it.product.nutriments?.fat100g ?: 0.0
        }

    // ── Factory ───────────────────────────────

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            MealsViewModel(application) as T
    }
}
