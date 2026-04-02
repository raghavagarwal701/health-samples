/*
 * Retrofit service interface for the Pulse Backend API.
 */
package com.example.healthconnectsample.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface PulseApiService {

    /**
     * Health check endpoint.
     * GET /api/health
     */
    @GET("api/health")
    suspend fun healthCheck(): Response<HealthCheckResponse>

    /**
     * Chat endpoint - sends health data + user query, returns AI response.
     * POST /api/chat
     */
    @POST("api/chat")
    suspend fun chat(@Body request: ChatRequest): Response<ChatResponse>

    /**
     * Product lookup by barcode via OpenFoodFacts.
     * GET /api/product/{barcode}
     */
    @GET("api/product/{barcode}")
    suspend fun getProduct(@Path("barcode") barcode: String): Response<ProductResponse>

    /**
     * Meal photo analysis via GPT-4o vision.
     * POST /api/meal/analyze   (multipart/form-data)
     *   - image (required): the meal photo bytes
     *   - note  (optional): user note, e.g. "I only ate half of this"
     * Returns the same ProductInfoResponse shape as getProduct so the result card
     * can be reused without any UI changes.
     */
    @Multipart
    @POST("api/meal/analyze")
    suspend fun analyzeMeal(
        @Part image: MultipartBody.Part,
        @Part("note") note: RequestBody? = null,
        @Part("question") question: RequestBody? = null,
    ): Response<MealAnalysisResponse>

    /**
     * Meal text-only analysis via GPT-4o.
     * POST /api/meal/analyze-text   (application/json)
     *   - description: plain-text description of what the user ate
     * Returns the same shape as analyzeMeal so the result card can be reused.
     */
    @POST("api/meal/analyze-text")
    suspend fun analyzeMealFromText(
        @Body request: MealTextRequest,
    ): Response<MealAnalysisResponse>

    // ===== FatSecret Integration =====

    /**
     * Get autocomplete suggestions for food search.
     * GET /api/fatsecret/autocomplete?expression=...&max_results=...
     */
    @GET("api/fatsecret/autocomplete")
    suspend fun autocompleteFatSecret(
        @retrofit2.http.Query("expression") expression: String,
        @retrofit2.http.Query("max_results") maxResults: Int = 10,
    ): Response<FatSecretAutocompleteResponse>

    /**
     * Search for foods by name with pagination.
     * GET /api/fatsecret/search?query=...&page_number=...&max_results=...
     */
    @GET("api/fatsecret/search")
    suspend fun searchFatSecret(
        @retrofit2.http.Query("query") query: String,
        @retrofit2.http.Query("page_number") pageNumber: Int = 0,
        @retrofit2.http.Query("max_results") maxResults: Int = 20,
    ): Response<FatSecretSearchResponse>

    /**
     * Get detailed food information with all serving options.
     * GET /api/fatsecret/food/{food_id}
     */
    @GET("api/fatsecret/food/{food_id}")
    suspend fun getFatSecretFood(
        @Path("food_id") foodId: Int,
    ): Response<FatSecretFoodResponse>

    /**
     * Calculate meal totals based on food + serving + quantity.
     * POST /api/fatsecret/add-preview
     */
    @POST("api/fatsecret/add-preview")
    suspend fun previewFatSecretMeal(
        @Body request: FatSecretMealAddRequest,
    ): Response<FatSecretMealAddPreviewResponse>
}
