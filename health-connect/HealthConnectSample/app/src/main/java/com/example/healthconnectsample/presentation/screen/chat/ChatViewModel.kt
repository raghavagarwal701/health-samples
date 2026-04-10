/*
 * ViewModel for the Chat screen.
 * Manages conversation state, collects health data from Health Connect,
 * and sends requests to the Pulse Backend API.
 */
package com.example.healthconnectsample.presentation.screen.chat

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.healthconnectsample.data.HealthConnectManager
import com.example.healthconnectsample.data.ProfileData
import com.example.healthconnectsample.data.ProfileRepository
import com.example.healthconnectsample.data.api.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ChatMessage(
    val role: String,   // "user" or "assistant"
    val content: String,
    val toolCalls: List<ToolCallResponse> = emptyList()
)

class ChatViewModel(
    private val healthConnectManager: HealthConnectManager,
    private val profileRepository: ProfileRepository,
    appContext: Context,
) : ViewModel() {

    private val mealLogPrefs = appContext.getSharedPreferences("pulse_meal_log", Context.MODE_PRIVATE)

    val messages: MutableState<List<ChatMessage>> = mutableStateOf(emptyList())
    val isLoading: MutableState<Boolean> = mutableStateOf(false)
    val errorMessage: MutableState<String?> = mutableStateOf(null)
    val isBackendReachable: MutableState<Boolean?> = mutableStateOf(null)

    init {
        checkBackendHealth()
    }

    /**
     * Check if the backend is reachable.
     */
    fun checkBackendHealth() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.healthCheck()
                isBackendReachable.value = response.isSuccessful
            } catch (e: Exception) {
                isBackendReachable.value = false
            }
        }
    }

    /**
     * Send a message to the backend with health data.
     */
    fun sendMessage(query: String) {
        if (query.isBlank()) return

        // Add user message to the list
        val userMessage = ChatMessage(role = "user", content = query)
        messages.value = messages.value + userMessage
        isLoading.value = true
        errorMessage.value = null

        viewModelScope.launch {
            try {
                // 1. Get user profile from repository
                val profileData = profileRepository.getProfile()
                val userProfilePayload = mapProfileToPayload(profileData)

                // 2. Collect health data from Health Connect
                val healthData = try {
                    healthConnectManager.collectHealthDataForBackend(userProfilePayload)
                } catch (e: Exception) {
                    // If health data collection fails, send with minimal data
                    HealthDataPayload(userData = userProfilePayload)
                }

                // 3. Build structured chat context (today + 3-4 days + 7-day summary + profile)
                val chatContext = buildChatContext(
                    profile = profileData,
                    healthData = healthData,
                )

                // 4. Build conversation history from previous messages
                val conversationHistory = messages.value
                    .dropLast(1) // exclude the message we just added
                    .map { msg ->
                        ConversationMessagePayload(
                            role = msg.role,
                            content = msg.content
                        )
                    }

                // 5. Create API request
                val chatRequest = ChatRequest(
                    query = query,
                    chatContext = chatContext,
                    healthData = healthData,
                    conversationHistory = conversationHistory
                )

                // 6. Call the backend
                val response = RetrofitClient.apiService.chat(chatRequest)

                if (response.isSuccessful) {
                    val chatResponse = response.body()!!
                    val assistantMessage = ChatMessage(
                        role = "assistant",
                        content = chatResponse.response,
                        toolCalls = chatResponse.toolCalls
                    )
                    messages.value = messages.value + assistantMessage
                } else {
                    errorMessage.value = "Backend error: ${response.code()} - ${response.errorBody()?.string()}"
                }

            } catch (e: Exception) {
                errorMessage.value = "Connection error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun mapProfileToPayload(profile: ProfileData): UserProfilePayload {
        val heightCm = profile.height.toDoubleOrNull()?.let { h ->
            if (profile.heightUnit == "ft") h * 30.48 else h
        }

        return UserProfilePayload(
            name = profile.name.takeIf { it.isNotBlank() } ?: "User",
            age = profile.age.toIntOrNull(),
            weightKg = profile.weight.toDoubleOrNull(),
            heightCm = heightCm,
            location = if (profile.country.isNotBlank()) mapOf("country" to profile.country) else null,
            goals = if (profile.goal.isNotBlank()) mapOf("primary_goal" to profile.goal) else null
        )
    }

    private data class MealDayContext(
        val meals: List<ChatMealEntryPayload>,
        val calories: Double,
        val proteinG: Double,
        val carbsG: Double,
        val fatG: Double,
        val micronutrients: Map<String, Double>,
    )

    private fun parseMealLogByDate(): Map<LocalDate, MealDayContext> {
        val raw = mealLogPrefs.getString("meal_log_v2", null) ?: return emptyMap()
        return try {
            val jsonDays = org.json.JSONArray(raw)
            val result = mutableMapOf<LocalDate, MealDayContext>()

            for (i in 0 until jsonDays.length()) {
                val dayObj = jsonDays.getJSONObject(i)
                val date = runCatching { LocalDate.parse(dayObj.getString("date")) }.getOrNull() ?: continue
                val entries = dayObj.optJSONArray("entries") ?: org.json.JSONArray()

                val meals = mutableListOf<ChatMealEntryPayload>()
                var caloriesTotal = 0.0
                var proteinTotal = 0.0
                var carbsTotal = 0.0
                var fatTotal = 0.0
                var fiberTotal = 0.0
                var sugarTotal = 0.0
                var sodiumTotal = 0.0

                for (j in 0 until entries.length()) {
                    val entry = entries.getJSONObject(j)
                    val name = entry.optString("product_name", "Meal").ifBlank { "Meal" }
                    val nutriments = entry.optJSONObject("nutriments")

                    fun readDouble(primary: String, fallback: String): Double {
                        val primaryValue = nutriments?.optDouble(primary)?.takeUnless { it.isNaN() }
                        val fallbackValue = nutriments?.optDouble(fallback)?.takeUnless { it.isNaN() }
                        return primaryValue ?: fallbackValue ?: 0.0
                    }

                    val kcal = readDouble("energy_kcal_pkg", "energy_kcal_100g")
                    val protein = readDouble("proteins_pkg", "proteins_100g")
                    val carbs = readDouble("carbohydrates_pkg", "carbohydrates_100g")
                    val fat = readDouble("fat_pkg", "fat_100g")
                    val fiber = readDouble("fiber_pkg", "fiber_100g")
                    val sugar = readDouble("sugars_pkg", "sugars_100g")
                    val sodium = readDouble("salt_pkg", "salt_100g") * 400.0

                    caloriesTotal += kcal
                    proteinTotal += protein
                    carbsTotal += carbs
                    fatTotal += fat
                    fiberTotal += fiber
                    sugarTotal += sugar
                    sodiumTotal += sodium

                    meals += ChatMealEntryPayload(
                        name = name,
                        calories = kcal,
                        macros = MacroTotalsPayload(
                            proteinG = protein,
                            carbsG = carbs,
                            fatG = fat,
                        ),
                        micronutrients = mapOf(
                            "fiber_g" to fiber,
                            "sugar_g" to sugar,
                            "sodium_mg" to sodium,
                        )
                    )
                }

                result[date] = MealDayContext(
                    meals = meals,
                    calories = caloriesTotal,
                    proteinG = proteinTotal,
                    carbsG = carbsTotal,
                    fatG = fatTotal,
                    micronutrients = mapOf(
                        "fiber_g" to fiberTotal,
                        "sugar_g" to sugarTotal,
                        "sodium_mg" to sodiumTotal,
                    )
                )
            }

            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun trendDirection(olderAvg: Double, newerAvg: Double): String {
        if (olderAvg == 0.0 && newerAvg == 0.0) return "stable"
        if (olderAvg == 0.0) return "up"
        val ratio = (newerAvg - olderAvg) / olderAvg
        return when {
            ratio > 0.05 -> "up"
            ratio < -0.05 -> "down"
            else -> "stable"
        }
    }

    private fun inferActivityLevel(averageSteps: Double): String {
        return when {
            averageSteps >= 12000.0 -> "very_active"
            averageSteps >= 8500.0 -> "moderately_active"
            averageSteps >= 5000.0 -> "lightly_active"
            else -> "sedentary"
        }
    }

    private suspend fun buildChatContext(
        profile: ProfileData,
        healthData: HealthDataPayload,
    ): ChatContextPayload {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val now = Instant.now()

        val mealLogByDate = parseMealLogByDate()
        val todayMeals = mealLogByDate[today]

        val todayStart = today.atStartOfDay(zoneId).toInstant()
        val todaySteps = runCatching { healthConnectManager.readStepsAggregate(todayStart, now) }.getOrNull()
        val todayCaloriesBurned = runCatching {
            healthConnectManager.readCaloriesAggregate(todayStart, now)?.inKilocalories
        }.getOrNull()
        val todayWorkoutMinutes = runCatching {
            healthConnectManager.readExerciseSessions(todayStart, now)
                .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                .toDouble()
        }.getOrNull()

        val recentDates = (1..4).map { today.minusDays(it.toLong()) }
        val last34DailyTotals = recentDates.map { date ->
            val day = mealLogByDate[date]
            DailyNutrientTotalPayload(
                date = date.toString(),
                calories = day?.calories ?: 0.0,
                macros = MacroTotalsPayload(
                    proteinG = day?.proteinG ?: 0.0,
                    carbsG = day?.carbsG ?: 0.0,
                    fatG = day?.fatG ?: 0.0,
                ),
                mealsSummary = day?.meals?.joinToString(separator = ", ") { it.name }?.takeIf { it.isNotBlank() },
                micronutrients = day?.micronutrients,
            )
        }

        val sevenDayDates = (0..6).map { today.minusDays(it.toLong()) }
        val sevenDayData = sevenDayDates.map { date ->
            mealLogByDate[date] ?: MealDayContext(
                meals = emptyList(),
                calories = 0.0,
                proteinG = 0.0,
                carbsG = 0.0,
                fatG = 0.0,
                micronutrients = emptyMap(),
            )
        }

        fun avg(values: List<Double>) = if (values.isEmpty()) 0.0 else values.average()

        val avgCalories = avg(sevenDayData.map { it.calories })
        val avgProtein = avg(sevenDayData.map { it.proteinG })
        val avgCarbs = avg(sevenDayData.map { it.carbsG })
        val avgFat = avg(sevenDayData.map { it.fatG })
        val avgFiber = avg(sevenDayData.map { it.micronutrients["fiber_g"] ?: 0.0 })
        val avgSugar = avg(sevenDayData.map { it.micronutrients["sugar_g"] ?: 0.0 })
        val avgSodium = avg(sevenDayData.map { it.micronutrients["sodium_mg"] ?: 0.0 })

        val olderWindow = sevenDayData.takeLast(3)
        val newerWindow = sevenDayData.take(3)

        val avgOlderCalories = avg(olderWindow.map { it.calories })
        val avgOlderProtein = avg(olderWindow.map { it.proteinG })
        val avgOlderCarbs = avg(olderWindow.map { it.carbsG })
        val avgOlderFat = avg(olderWindow.map { it.fatG })
        val avgNewerCalories = avg(newerWindow.map { it.calories })
        val avgNewerProtein = avg(newerWindow.map { it.proteinG })
        val avgNewerCarbs = avg(newerWindow.map { it.carbsG })
        val avgNewerFat = avg(newerWindow.map { it.fatG })

        val averageSteps = (healthData.activitySummary?.dailyAverages?.get("steps") as? Number)?.toDouble()
            ?: (todaySteps?.toDouble() ?: 0.0)

        return ChatContextPayload(
            today = TodayChatContextPayload(
                meals = todayMeals?.meals ?: emptyList(),
                calories = todayMeals?.calories ?: 0.0,
                macros = MacroTotalsPayload(
                    proteinG = todayMeals?.proteinG ?: 0.0,
                    carbsG = todayMeals?.carbsG ?: 0.0,
                    fatG = todayMeals?.fatG ?: 0.0,
                ),
                activity = TodayActivityContextPayload(
                    steps = todaySteps,
                    workoutMinutes = todayWorkoutMinutes,
                    caloriesBurned = todayCaloriesBurned,
                ),
            ),
            last34Days = LastFewDaysContextPayload(
                dailyTotals = last34DailyTotals,
            ),
            last7DaysSummary = SevenDaySummaryContextPayload(
                avgCalories = avgCalories,
                avgMacros = MacroTotalsPayload(
                    proteinG = avgProtein,
                    carbsG = avgCarbs,
                    fatG = avgFat,
                ),
                micronutrientAverages = mapOf(
                    "fiber_g" to avgFiber,
                    "sugar_g" to avgSugar,
                    "sodium_mg" to avgSodium,
                ),
                trends = TrendSummaryPayload(
                    calories = trendDirection(avgOlderCalories, avgNewerCalories),
                    protein = trendDirection(avgOlderProtein, avgNewerProtein),
                    carbs = trendDirection(avgOlderCarbs, avgNewerCarbs),
                    fat = trendDirection(avgOlderFat, avgNewerFat),
                    micronutrients = mapOf(
                        "fiber_g" to trendDirection(
                            avg(olderWindow.map { it.micronutrients["fiber_g"] ?: 0.0 }),
                            avg(newerWindow.map { it.micronutrients["fiber_g"] ?: 0.0 })
                        ),
                        "sodium_mg" to trendDirection(
                            avg(olderWindow.map { it.micronutrients["sodium_mg"] ?: 0.0 }),
                            avg(newerWindow.map { it.micronutrients["sodium_mg"] ?: 0.0 })
                        )
                    ),
                ),
            ),
            userProfile = ChatUserProfilePayload(
                weightKg = profile.weight.toDoubleOrNull(),
                heightCm = profile.height.toDoubleOrNull()?.let { if (profile.heightUnit == "ft") it * 30.48 else it },
                goal = profile.goal.ifBlank { null },
                activityLevel = inferActivityLevel(averageSteps),
            ),
        )
    }

    /**
     * Clear the conversation history.
     */
    fun clearConversation() {
        messages.value = emptyList()
        errorMessage.value = null
    }

    class Factory(
        private val healthConnectManager: HealthConnectManager,
        private val profileRepository: ProfileRepository,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(healthConnectManager, profileRepository, appContext) as T
        }
    }
}
