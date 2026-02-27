/*
 * Retrofit service interface for the Pulse Backend API.
 */
package com.example.healthconnectsample.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
}
