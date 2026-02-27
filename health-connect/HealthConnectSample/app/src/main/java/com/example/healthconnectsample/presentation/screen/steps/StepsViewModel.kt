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
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.healthconnectsample.data.HealthConnectManager
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
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

class StepsViewModel(private val healthConnectManager: HealthConnectManager) : ViewModel() {

    // Define permissions for Steps, Distance, and Total Calories
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class), // Optional: if you want to write
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    )

    var permissionsGranted = mutableStateOf(false)
        private set

    var uiState: UiState by mutableStateOf(UiState.Uninitialized)
        private set

    var todayData = mutableStateOf(StepsData())
        private set

    var weeklyData: MutableState<List<StepsData>> = mutableStateOf(listOf())
        private set

    val permissionsLauncher = healthConnectManager.requestPermissionsActivityContract()

    fun initialLoad() {
        viewModelScope.launch {
            tryWithPermissionsCheck {
                readStepsData()
            }
        }
    }

    private suspend fun readStepsData() {
        val now = Instant.now()
        val zoneId = ZoneId.systemDefault()
        // Today's start time
        val todayStart = ZonedDateTime.ofInstant(now, zoneId).truncatedTo(ChronoUnit.DAYS)
        val todayEnd = now // Until now

        // Fetch Today's Data
        val todaySteps = healthConnectManager.readStepsAggregate(todayStart.toInstant(), todayEnd)
        val todayDistance = healthConnectManager.readDistanceAggregate(todayStart.toInstant(), todayEnd)
        val todayCalories = healthConnectManager.readCaloriesAggregate(todayStart.toInstant(), todayEnd)

        todayData.value = StepsData(
            date = todayStart,
            steps = todaySteps ?: 0,
            distanceMeters = todayDistance?.inMeters ?: 0.0,
            calories = todayCalories?.inKilocalories ?: 0.0
        )

        // Fetch Weekly Data (Last 7 days, excluding today for the list if desired, or including)
        // Let's get the past 7 days (yesterday backwards)
        val weeklyList = mutableListOf<StepsData>()
        for (i in 1..7) {
            val dayStart = todayStart.minusDays(i.toLong()) // Start of that day
            val dayEnd = dayStart.plusDays(1) // End of that day
            
            val steps = healthConnectManager.readStepsAggregate(dayStart.toInstant(), dayEnd.toInstant())
            val distance = healthConnectManager.readDistanceAggregate(dayStart.toInstant(), dayEnd.toInstant())
            val calories = healthConnectManager.readCaloriesAggregate(dayStart.toInstant(), dayEnd.toInstant())

            weeklyList.add(
                StepsData(
                    date = dayStart,
                    steps = steps ?: 0,
                    distanceMeters = distance?.inMeters ?: 0.0,
                    calories = calories?.inKilocalories ?: 0.0
                )
            )
        }
        weeklyData.value = weeklyList
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
            return StepsViewModel(
                healthConnectManager = healthConnectManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
