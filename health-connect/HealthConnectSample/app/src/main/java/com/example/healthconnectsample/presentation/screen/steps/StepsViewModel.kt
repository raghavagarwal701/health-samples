/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.healthconnectsample.presentation.screen.steps

import android.os.RemoteException
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.healthconnectsample.data.HealthConnectManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

data class StepsData(
    val date: ZonedDateTime = ZonedDateTime.now(),
    val steps: Long = 0,
    val distanceMeters: Double = 0.0,
    val calories: Double = 0.0
)

/** Per-exercise session summary shown inside a day card. */
data class ExerciseSummary(
    val title: String?,
    val type: Int,
    val durationMinutes: Long,
)

/** All activity data for a single day. */
data class DailyActivityData(
    val date: LocalDate,
    val steps: Long = 0,
    val distanceMeters: Double = 0.0,
    val caloriesBurned: Double = 0.0,
    val sleepMinutes: Long? = null,
    val heartRateAvg: Long? = null,
    val heartRateMin: Long? = null,
    val heartRateMax: Long? = null,
    val exercises: List<ExerciseSummary> = emptyList(),
)

class StepsViewModel(private val healthConnectManager: HealthConnectManager) : ViewModel() {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    var permissionsGranted = mutableStateOf(false)
        private set

    var uiState: UiState by mutableStateOf(UiState.Uninitialized)
        private set

    /** Today + past 7 days, newest first. */
    var dailyActivity: MutableState<List<DailyActivityData>> = mutableStateOf(listOf())
        private set

    // Legacy – kept so existing nav wiring still compiles
    var todayData = mutableStateOf(StepsData())
        private set
    var weeklyData: MutableState<List<StepsData>> = mutableStateOf(listOf())
        private set

    // ── Date navigation ──────────────────────────────────────────────────────
    val selectedDate: MutableState<LocalDate> = mutableStateOf(LocalDate.now())

    fun previousDay() {
        selectedDate.value = selectedDate.value.minusDays(1)
        ensureDateLoaded(selectedDate.value)
    }

    fun nextDay() {
        if (selectedDate.value.isBefore(LocalDate.now())) {
            selectedDate.value = selectedDate.value.plusDays(1)
            ensureDateLoaded(selectedDate.value)
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        ensureDateLoaded(date)
    }

    private fun ensureDateLoaded(date: LocalDate) {
        if (dailyActivity.value.none { it.date == date }) {
            viewModelScope.launch {
                tryWithPermissionsCheck { loadForDate(date) }
            }
        }
    }

    val permissionsLauncher = healthConnectManager.requestPermissionsActivityContract()

    fun initialLoad() {
        viewModelScope.launch {
            permissionsGranted.value = healthConnectManager.hasAllPermissions(permissions)
            // Set Done immediately so the screen renders during data loading
            uiState = UiState.Done
            if (permissionsGranted.value) {
                try {
                    loadActivityData()
                } catch (e: RemoteException) {
                    uiState = UiState.Error(e)
                } catch (e: SecurityException) {
                    uiState = UiState.Error(e)
                } catch (e: IOException) {
                    uiState = UiState.Error(e)
                } catch (e: IllegalStateException) {
                    uiState = UiState.Error(e)
                }
            }
        }
    }

    private suspend fun loadActivityData() {
        val zoneId = ZoneId.systemDefault()
        val now = Instant.now()
        val todayStart = ZonedDateTime.ofInstant(now, zoneId).truncatedTo(ChronoUnit.DAYS)

        // Fetch all sleep sessions ONCE for the 8-day window instead of once per day
        val allSleepSessions = try {
            healthConnectManager.readSleepSessions()
        } catch (e: Exception) {
            emptyList()
        }

        // Fetch today + 7 previous days in parallel instead of sequentially
        val result: List<DailyActivityData> = coroutineScope {
            (0..7).map { i ->
                async {
                    val dayStart = todayStart.minusDays(i.toLong())
                    val dayEnd = if (i == 0) ZonedDateTime.ofInstant(now, zoneId) else dayStart.plusDays(1)
                    val localDate = dayStart.toLocalDate()

                    val steps = healthConnectManager.readStepsAggregate(dayStart.toInstant(), dayEnd.toInstant()) ?: 0L
                    val distance = healthConnectManager.readDistanceAggregate(dayStart.toInstant(), dayEnd.toInstant())?.inMeters ?: 0.0
                    val calories = healthConnectManager.readCaloriesAggregate(dayStart.toInstant(), dayEnd.toInstant())?.inKilocalories ?: 0.0

                    val hrRecords = try { healthConnectManager.readHeartRateData(dayStart.toInstant(), dayEnd.toInstant()) } catch (e: Exception) { emptyList() }
                    val hrSamples = hrRecords.flatMap { it.samples }

                    // Filter from the pre-fetched sleep list — no extra IPC call per day
                    val daySleepSessions = allSleepSessions.filter { s ->
                        s.endTime.atZone(zoneId).toLocalDate() == localDate
                    }
                    val sleepMins: Long? = if (daySleepSessions.isNotEmpty()) {
                        daySleepSessions.sumOf { s ->
                            Duration.between(s.startTime, s.endTime).toMinutes().coerceAtLeast(0)
                        }
                    } else null

                    val exercises: List<ExerciseSummary> = try {
                        healthConnectManager.readExerciseSessions(dayStart.toInstant(), dayEnd.toInstant())
                            .map { ex ->
                                ExerciseSummary(
                                    title = ex.title,
                                    type = ex.exerciseType,
                                    durationMinutes = Duration.between(ex.startTime, ex.endTime).toMinutes(),
                                )
                            }
                    } catch (e: Exception) { emptyList() }

                    DailyActivityData(
                        date = localDate,
                        steps = steps,
                        distanceMeters = distance,
                        caloriesBurned = calories,
                        sleepMinutes = sleepMins,
                        heartRateAvg = if (hrSamples.isNotEmpty()) hrSamples.map { it.beatsPerMinute }.average().toLong() else null,
                        heartRateMin = if (hrSamples.isNotEmpty()) hrSamples.minOf { it.beatsPerMinute } else null,
                        heartRateMax = if (hrSamples.isNotEmpty()) hrSamples.maxOf { it.beatsPerMinute } else null,
                        exercises = exercises,
                    )
                }
            }.awaitAll()
        }

        dailyActivity.value = result

        // Update legacy fields so any existing code still works
        result.firstOrNull()?.let { today ->
            todayData.value = StepsData(
                date = todayStart,
                steps = today.steps,
                distanceMeters = today.distanceMeters,
                calories = today.caloriesBurned
            )
        }
        weeklyData.value = result.drop(1).map { d ->
            StepsData(
                date = d.date.atStartOfDay(zoneId),
                steps = d.steps,
                distanceMeters = d.distanceMeters,
                calories = d.caloriesBurned,
            )
        }
    }

    /** Load data for a single date that is not yet in the cache. */
    private suspend fun loadForDate(date: LocalDate) {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now()
        val dayStart = date.atStartOfDay(zoneId)
        val dayEnd = if (date == today) ZonedDateTime.ofInstant(Instant.now(), zoneId) else dayStart.plusDays(1)

        val steps = healthConnectManager.readStepsAggregate(dayStart.toInstant(), dayEnd.toInstant()) ?: 0L
        val distance = healthConnectManager.readDistanceAggregate(dayStart.toInstant(), dayEnd.toInstant())?.inMeters ?: 0.0
        val calories = healthConnectManager.readCaloriesAggregate(dayStart.toInstant(), dayEnd.toInstant())?.inKilocalories ?: 0.0

        val hrRecords = try { healthConnectManager.readHeartRateData(dayStart.toInstant(), dayEnd.toInstant()) } catch (e: Exception) { emptyList() }
        val hrSamples = hrRecords.flatMap { it.samples }
        val hrAvg = if (hrSamples.isNotEmpty()) hrSamples.map { it.beatsPerMinute }.average().toLong() else null
        val hrMin = if (hrSamples.isNotEmpty()) hrSamples.minOf { it.beatsPerMinute } else null
        val hrMax = if (hrSamples.isNotEmpty()) hrSamples.maxOf { it.beatsPerMinute } else null

        val sleepMins: Long? = try {
            val sessions = healthConnectManager.readSleepSessions()
            val daySessions = sessions.filter { s -> s.endTime.atZone(zoneId).toLocalDate() == date }
            if (daySessions.isNotEmpty()) daySessions.sumOf { s ->
                Duration.between(s.startTime, s.endTime).toMinutes().coerceAtLeast(0)
            } else null
        } catch (e: Exception) { null }

        val exercises: List<ExerciseSummary> = try {
            healthConnectManager.readExerciseSessions(dayStart.toInstant(), dayEnd.toInstant())
                .map { ex -> ExerciseSummary(title = ex.title, type = ex.exerciseType, durationMinutes = Duration.between(ex.startTime, ex.endTime).toMinutes()) }
        } catch (e: Exception) { emptyList() }

        val newEntry = DailyActivityData(
            date = date,
            steps = steps,
            distanceMeters = distance,
            caloriesBurned = calories,
            sleepMinutes = sleepMins,
            heartRateAvg = hrAvg,
            heartRateMin = hrMin,
            heartRateMax = hrMax,
            exercises = exercises,
        )

        // Upsert into the list (replace existing or prepend)
        dailyActivity.value = listOf(newEntry) + dailyActivity.value.filter { it.date != date }
    }

    private suspend fun tryWithPermissionsCheck(block: suspend () -> Unit) {
        permissionsGranted.value = healthConnectManager.hasAllPermissions(permissions)
        uiState = try {
            if (permissionsGranted.value) {
                block()
            }
            UiState.Done
        } catch (remoteException: RemoteException) {
            UiState.Error(remoteException)
        } catch (securityException: SecurityException) {
            UiState.Error(securityException)
        } catch (ioException: IOException) {
            UiState.Error(ioException)
        } catch (illegalStateException: IllegalStateException) {
            UiState.Error(illegalStateException)
        }
    }

    sealed class UiState {
        object Uninitialized : UiState()
        object Done : UiState()
        data class Error(val exception: Throwable, val uuid: UUID = UUID.randomUUID()) : UiState()
    }
}

class StepsViewModelFactory(
    private val healthConnectManager: HealthConnectManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StepsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StepsViewModel(healthConnectManager = healthConnectManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
