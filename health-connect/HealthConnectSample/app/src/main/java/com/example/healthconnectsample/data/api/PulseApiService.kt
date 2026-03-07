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
}
