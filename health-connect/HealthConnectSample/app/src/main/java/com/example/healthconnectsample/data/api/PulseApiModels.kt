/*
 * Kotlin data classes mirroring the pulse_backend Pydantic models.
 * Used for serializing health data into the API request payload.
 */
package com.example.healthconnectsample.data.api

import com.google.gson.annotations.SerializedName

// ===== Health Data Models =====

data class ActivitySummaryPayload(
    @SerializedName("daily_averages") val dailyAverages: Map<String, Any>? = null,
    @SerializedName("past_seven_days_activity") val pastSevenDaysActivity: List<Map<String, Any>>? = null,
    // Removed specific fields not needed here or redundant
)

data class SleepSummaryPayload(
    @SerializedName("sleep_sessions") val sleepSessions: List<Map<String, Any>>? = null,
    @SerializedName("sleep_quality") val sleepQuality: Map<String, Any>? = null
)

data class HeartRateSummaryPayload(
    @SerializedName("min_hr") val minHr: Double? = null,
    @SerializedName("max_hr") val maxHr: Double? = null,
    @SerializedName("average_hr") val averageHr: Double? = null
)

data class HrvSummaryPayload(
    @SerializedName("average_hrv") val averageHrv: Double? = null,
    @SerializedName("hrv_trend") val hrvTrend: String? = null,
    @SerializedName("measurements") val measurements: List<Map<String, Any>>? = null
)

data class ExerciseSummaryPayload(
    @SerializedName("sessions") val sessions: List<Map<String, Any>>? = null,
    @SerializedName("total_sessions") val totalSessions: Int? = null,
    @SerializedName("exercise_types") val exerciseTypes: List<String>? = null
)

data class UserProfilePayload(
    val name: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    @SerializedName("height_cm") val heightCm: Double? = null,
    @SerializedName("weight_kg") val weightKg: Double? = null,
    val location: Map<String, Any>? = null,
    @SerializedName("medical_history") val medicalHistory: Map<String, Any>? = null,
    @SerializedName("lifestyle_preferences") val lifestylePreferences: Map<String, Any>? = null,
    val goals: Map<String, Any>? = null
)

// ===== Composite Health Data =====

data class HealthDataPayload(
    @SerializedName("activity_summary_for_llm") val activitySummary: ActivitySummaryPayload? = null,
    @SerializedName("sleep_summary_for_llm") val sleepSummary: SleepSummaryPayload? = null,
    @SerializedName("heart_rate_summary_for_llm") val heartRateSummary: HeartRateSummaryPayload? = null,
    @SerializedName("hrv_summary_for_llm") val hrvSummary: HrvSummaryPayload? = null,
    @SerializedName("exercise_summary_for_llm") val exerciseSummary: ExerciseSummaryPayload? = null,
    @SerializedName("user_data") val userData: UserProfilePayload? = null
)

// ===== Conversation =====

data class ConversationMessagePayload(
    val role: String,
    val content: String
)

// ===== API Request / Response =====

data class ChatRequest(
    val query: String,
    @SerializedName("health_data") val healthData: HealthDataPayload,
    @SerializedName("conversation_history") val conversationHistory: List<ConversationMessagePayload> = emptyList()
)

data class ToolCallResponse(
    val name: String,
    val timestamp: String
)

data class ChatResponse(
    val response: String,
    @SerializedName("tool_calls") val toolCalls: List<ToolCallResponse> = emptyList()
)

// ===== Health Check =====

data class HealthCheckResponse(
    val status: String,
    val service: String,
    val version: String
)
