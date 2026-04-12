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
import com.example.healthconnectsample.data.SleepSessionData
import com.example.healthconnectsample.data.ProfileData
import com.example.healthconnectsample.data.ProfileRepository
import com.example.healthconnectsample.data.api.*
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

    private data class ActivityDayContext(
        val steps: Long?,
        val distanceKm: Double?,
        val caloriesBurned: Double?,
        val workoutMinutes: Double?,
        val exerciseSessions: List<ExerciseSessionPayload>,
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

    private fun inferActivityLevel(averageSteps: Double): String {
        return when {
            averageSteps >= 12000.0 -> "very_active"
            averageSteps >= 8500.0 -> "moderately_active"
            averageSteps >= 5000.0 -> "lightly_active"
            else -> "sedentary"
        }
    }

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    private fun avg(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        return values.sum() / values.size
    }

    private fun formatExerciseName(exerciseType: Int): String {
        return when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength Training"
            else -> "Unknown"
        }
    }

    private fun ExerciseSessionRecord.toChatPayload(): ExerciseSessionPayload {
        return ExerciseSessionPayload(
            title = title,
            exerciseName = formatExerciseName(exerciseType),
            startTime = startTime.toString(),
            endTime = endTime.toString(),
            durationMinutes = Duration.between(startTime, endTime).toMinutes(),
        )
    }

    private suspend fun buildActivityDayContext(
        date: LocalDate,
        zoneId: ZoneId,
        now: Instant,
    ): ActivityDayContext {
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val dayEnd = if (date == LocalDate.now(zoneId)) now else dayStart.plus(1, ChronoUnit.DAYS)

        val steps = runCatching { healthConnectManager.readStepsAggregate(dayStart, dayEnd) }.getOrNull()
        val distanceKm = runCatching { healthConnectManager.readDistanceAggregate(dayStart, dayEnd)?.inKilometers }.getOrNull()
        val caloriesBurned = runCatching { healthConnectManager.readCaloriesAggregate(dayStart, dayEnd)?.inKilocalories }.getOrNull()
        val exerciseSessions = runCatching { healthConnectManager.readExerciseSessions(dayStart, dayEnd) }
            .getOrDefault(emptyList())
            .map { it.toChatPayload() }
        val workoutMinutes = exerciseSessions.sumOf { it.durationMinutes ?: 0L }.toDouble()

        return ActivityDayContext(
            steps = steps,
            distanceKm = distanceKm,
            caloriesBurned = caloriesBurned,
            workoutMinutes = workoutMinutes,
            exerciseSessions = exerciseSessions,
        )
    }

    private fun buildSleepSummaryForDate(
        date: LocalDate,
        sleepByDate: Map<LocalDate, List<SleepSessionData>>,
    ): SleepSummaryPayload? {
        val sessions = sleepByDate[date].orEmpty()
        if (sessions.isEmpty()) return null

        val totalMinutes = sessions.sumOf { it.duration?.toMinutes() ?: 0L }.toDouble()
        val deepMinutes = sessions.sumOf { session ->
            session.stages
                .filter { it.stage == SleepSessionRecord.STAGE_TYPE_DEEP }
                .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        }.toDouble()
        val lightMinutes = sessions.sumOf { session ->
            session.stages
                .filter { it.stage == SleepSessionRecord.STAGE_TYPE_LIGHT }
                .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        }.toDouble()
        val remMinutes = sessions.sumOf { session ->
            session.stages
                .filter { it.stage == SleepSessionRecord.STAGE_TYPE_REM }
                .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        }.toDouble()
        val awakeMinutes = sessions.sumOf { session ->
            session.stages
                .filter { it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE }
                .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        }.toDouble()

        return SleepSummaryPayload(
            totalSleepHours = round2(totalMinutes / 60.0),
            deepSleepHours = round2(deepMinutes / 60.0),
            lightSleepHours = round2(lightMinutes / 60.0),
            remSleepHours = round2(remMinutes / 60.0),
            awakeHours = round2(awakeMinutes / 60.0),
        )
    }

    private fun buildDailyHistoryPayload(
        date: LocalDate,
        mealLogByDate: Map<LocalDate, MealDayContext>,
        activity: ActivityDayContext,
        sleepSummary: SleepSummaryPayload?,
    ): DailyHistoryPayload {
        val dayMeals = mealLogByDate[date]?.meals.orEmpty()
        val hasActivityData = (activity.steps ?: 0L) > 0L ||
            (activity.distanceKm ?: 0.0) > 0.0 ||
            (activity.caloriesBurned ?: 0.0) > 0.0 ||
            activity.exerciseSessions.isNotEmpty()

        return DailyHistoryPayload(
            activity = if (hasActivityData) {
                DailyActivityPayload(
                    steps = activity.steps,
                    distanceKm = activity.distanceKm,
                    caloriesBurned = activity.caloriesBurned,
                    workoutMinutes = activity.workoutMinutes,
                )
            } else {
                "no logs present"
            },
            exerciseSession = if (activity.exerciseSessions.isNotEmpty()) {
                activity.exerciseSessions
            } else {
                "no logs present"
            },
            sleepSession = sleepSummary ?: "no logs present",
            meals = if (dayMeals.isNotEmpty()) dayMeals else "no logs present",
        )
    }

    private suspend fun buildChatContext(
        profile: ProfileData,
        healthData: HealthDataPayload,
    ): ChatContextPayload {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val now = Instant.now()

        val mealLogByDate = parseMealLogByDate()

        val allSleepSessions = runCatching { healthConnectManager.readSleepSessions() }
            .getOrDefault(emptyList())
        val sleepByDate = allSleepSessions.groupBy { it.endTime.atZone(zoneId).toLocalDate() }

        val todayActivity = buildActivityDayContext(today, zoneId, now)
        val yesterday = today.minusDays(1)
        val yesterdayActivity = buildActivityDayContext(yesterday, zoneId, now)

        val historyToday = buildDailyHistoryPayload(
            date = today,
            mealLogByDate = mealLogByDate,
            activity = todayActivity,
            sleepSummary = buildSleepSummaryForDate(today, sleepByDate),
        )

        val yesterdayHistory = buildDailyHistoryPayload(
            date = yesterday,
            mealLogByDate = mealLogByDate,
            activity = yesterdayActivity,
            sleepSummary = buildSleepSummaryForDate(yesterday, sleepByDate),
        )

        val sevenDayDates = (0..6).map { today.minusDays(it.toLong()) }
        val sevenDayMealData = sevenDayDates.map { date ->
            mealLogByDate[date] ?: MealDayContext(
                meals = emptyList(),
                calories = 0.0,
                proteinG = 0.0,
                carbsG = 0.0,
                fatG = 0.0,
                micronutrients = emptyMap(),
            )
        }
        val sevenDayActivityData = sevenDayDates.map { date ->
            buildActivityDayContext(date, zoneId, now)
        }
        val sevenDaySleepSummaries = sevenDayDates.mapNotNull { date ->
            buildSleepSummaryForDate(date, sleepByDate)
        }

        val avgEnergyConsumed = avg(sevenDayMealData.map { it.calories })
        val avgEnergyExpended = avg(sevenDayActivityData.map { it.caloriesBurned ?: 0.0 })
        val avgProtein = avg(sevenDayMealData.map { it.proteinG })
        val avgCarbs = avg(sevenDayMealData.map { it.carbsG })
        val avgFat = avg(sevenDayMealData.map { it.fatG })
        val avgFiber = avg(sevenDayMealData.map { it.micronutrients["fiber_g"] ?: 0.0 })
        val avgSugar = avg(sevenDayMealData.map { it.micronutrients["sugar_g"] ?: 0.0 })
        val avgSodium = avg(sevenDayMealData.map { it.micronutrients["sodium_mg"] ?: 0.0 })

        val totalMealsLogged = sevenDayMealData.sumOf { it.meals.size }
        val hasMealLogs = totalMealsLogged > 0
        val hasActivityLogs = sevenDayActivityData.any {
            (it.steps ?: 0L) > 0L ||
                (it.distanceKm ?: 0.0) > 0.0 ||
                (it.caloriesBurned ?: 0.0) > 0.0 ||
                it.exerciseSessions.isNotEmpty()
        }
        val hasSleepLogs = sevenDaySleepSummaries.isNotEmpty()
        val averageSteps = (healthData.activitySummary?.dailyAverages?.get("steps") as? Number)?.toDouble()
            ?: avg(sevenDayActivityData.map { it.steps?.toDouble() ?: 0.0 })

        return ChatContextPayload(
            historyToday = historyToday,
            yesterdayHistory = yesterdayHistory,
            past7DayAverage = Past7DayAveragePayload(
                averageActivity = if (hasActivityLogs) {
                    AverageActivityPayload(
                        avgSteps = round2(avg(sevenDayActivityData.map { it.steps?.toDouble() ?: 0.0 })),
                        avgDistanceKm = round2(avg(sevenDayActivityData.map { it.distanceKm ?: 0.0 })),
                        avgCaloriesBurned = round2(avgEnergyExpended),
                        avgWorkoutMinutes = round2(avg(sevenDayActivityData.map { it.workoutMinutes ?: 0.0 })),
                    )
                } else {
                    "no logs present"
                },
                sleepDetail = if (hasSleepLogs) {
                    SleepSummaryPayload(
                        totalSleepHours = round2(avg(sevenDaySleepSummaries.map { it.totalSleepHours ?: 0.0 })),
                        deepSleepHours = round2(avg(sevenDaySleepSummaries.map { it.deepSleepHours ?: 0.0 })),
                        lightSleepHours = round2(avg(sevenDaySleepSummaries.map { it.lightSleepHours ?: 0.0 })),
                        remSleepHours = round2(avg(sevenDaySleepSummaries.map { it.remSleepHours ?: 0.0 })),
                        awakeHours = round2(avg(sevenDaySleepSummaries.map { it.awakeHours ?: 0.0 })),
                    )
                } else {
                    "no logs present"
                },
                meals = if (hasMealLogs) {
                    AverageMealsPayload(
                        avgMealsLoggedPerDay = round2(totalMealsLogged.toDouble() / 7.0),
                        avgCaloriesConsumedKcal = round2(avgEnergyConsumed),
                        avgMacros = MacroTotalsPayload(
                            proteinG = round2(avgProtein),
                            carbsG = round2(avgCarbs),
                            fatG = round2(avgFat),
                        ),
                        avgMicronutrients = mapOf(
                            "fiber_g" to round2(avgFiber),
                            "sugar_g" to round2(avgSugar),
                            "sodium_mg" to round2(avgSodium),
                        ),
                    )
                } else {
                    "no logs present"
                },
                heartRateSummary = healthData.heartRateSummary ?: "no logs present",
                sleepSummary = healthData.sleepSummary ?: "no logs present",
                hrvSummary = healthData.hrvSummary ?: "no logs present",
                exerciseSummary = healthData.exerciseSummary ?: "no logs present",
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
