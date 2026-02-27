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
package com.example.healthconnectsample.presentation.screen.heartrate

import android.os.RemoteException
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
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

class HeartRateViewModel(private val healthConnectManager: HealthConnectManager) : ViewModel() {

    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class)
    )

    var permissionsGranted = mutableStateOf(false)
        private set

    var dailyHeartRates: MutableState<List<DailyHeartRateData>> = mutableStateOf(listOf())
        private set

    var uiState: UiState by mutableStateOf(UiState.Uninitialized)
        private set

    val permissionsLauncher = healthConnectManager.requestPermissionsActivityContract()

    fun initialLoad() {
        viewModelScope.launch {
            tryWithPermissionsCheck {
                readHeartRateData()
            }
        }
    }

    private suspend fun readHeartRateData() {
        val today = ZonedDateTime.now()
        val startOfToday = today.truncatedTo(ChronoUnit.DAYS)
        val sevenDaysAgo = startOfToday.minusDays(6)

        val dailyData = mutableListOf<DailyHeartRateData>()

        for (i in 0..6) {
            val dayStart = sevenDaysAgo.plusDays(i.toLong())
            val dayEnd = dayStart.plusDays(1)
            
            val records = healthConnectManager.readHeartRateData(dayStart.toInstant(), dayEnd.toInstant())
            val allSamples = records.flatMap { it.samples }

            if (allSamples.isNotEmpty()) {
                val min = allSamples.minOf { it.beatsPerMinute }
                val max = allSamples.maxOf { it.beatsPerMinute }
                val avg = allSamples.map { it.beatsPerMinute }.average().toLong()

                dailyData.add(
                    DailyHeartRateData(
                        date = dayStart,
                        min = min,
                        max = max,
                        avg = avg
                    )
                )
            } else {
                 dailyData.add(
                    DailyHeartRateData(
                        date = dayStart,
                        min = 0,
                        max = 0,
                        avg = 0
                    )
                )
            }
        }
        // Reverse to show newest first
        dailyHeartRates.value = dailyData.reversed()
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

data class DailyHeartRateData(
    val date: ZonedDateTime,
    val min: Long,
    val max: Long,
    val avg: Long
)

class HeartRateViewModelFactory(
    private val healthConnectManager: HealthConnectManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HeartRateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HeartRateViewModel(
                healthConnectManager = healthConnectManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
