package com.example.healthconnectsample.presentation.screen.home

import android.app.Application
import android.content.Context
import android.os.RemoteException
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.healthconnectsample.data.HealthConnectManager
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

data class HomeData(
    val caloriesBurned: Double = 0.0,
    val caloriesConsumed: Double = 0.0,
    val steps: Long = 0,
    val distanceMeters: Double = 0.0,
) {
    val netBalance: Double get() = caloriesConsumed - caloriesBurned
    val isInSurplus: Boolean get() = netBalance > 0
}

class HomeViewModel(
    application: Application,
    private val healthConnectManager: HealthConnectManager
) : AndroidViewModel(application) {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    var permissionsGranted = mutableStateOf(false)
        private set

    var uiState = mutableStateOf<UiState>(UiState.Uninitialized)
        private set

    var homeData = mutableStateOf(HomeData())
        private set

    val permissionsLauncher = healthConnectManager.requestPermissionsActivityContract()

    private val prefs = application.getSharedPreferences("pulse_meal_log", Context.MODE_PRIVATE)

    fun initialLoad() {
        viewModelScope.launch {
            tryWithPermissionsCheck { loadTodayData() }
        }
    }

    private suspend fun loadTodayData() {
        val now = Instant.now()
        val zoneId = ZoneId.systemDefault()
        val todayStart = ZonedDateTime.ofInstant(now, zoneId).truncatedTo(ChronoUnit.DAYS)

        val steps = healthConnectManager.readStepsAggregate(todayStart.toInstant(), now) ?: 0L
        val distance = healthConnectManager.readDistanceAggregate(todayStart.toInstant(), now)
        val burned = healthConnectManager.readCaloriesAggregate(todayStart.toInstant(), now)

        val consumed = readTodayConsumedKcal()

        homeData.value = HomeData(
            caloriesBurned = burned?.inKilocalories ?: 0.0,
            caloriesConsumed = consumed,
            steps = steps,
            distanceMeters = distance?.inMeters ?: 0.0,
        )
    }

    /** Reads today's total kcal consumed from the shared meal-log SharedPreferences. */
    private fun readTodayConsumedKcal(): Double {
        val json = prefs.getString("meal_log_v2", null) ?: return 0.0
        return try {
            val daysArray = org.json.JSONArray(json)
            val todayStr = LocalDate.now().toString()
            for (i in 0 until daysArray.length()) {
                val dayObj = daysArray.getJSONObject(i)
                if (dayObj.getString("date") == todayStr) {
                    val entries = dayObj.getJSONArray("entries")
                    var total = 0.0
                    for (j in 0 until entries.length()) {
                        val entry = entries.getJSONObject(j)
                        val nutrObj = entry.optJSONObject("nutriments")
                        val kcal = nutrObj?.optDouble("energy_kcal_pkg")
                            ?.takeUnless { it.isNaN() } ?: 0.0
                        total += kcal
                    }
                    return total
                }
            }
            0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private suspend fun tryWithPermissionsCheck(block: suspend () -> Unit) {
        permissionsGranted.value = healthConnectManager.hasAllPermissions(permissions)
        uiState.value = try {
            if (permissionsGranted.value) block()
            UiState.Done
        } catch (e: RemoteException) {
            UiState.Error(e)
        } catch (e: SecurityException) {
            UiState.Error(e)
        } catch (e: IOException) {
            UiState.Error(e)
        } catch (e: IllegalStateException) {
            UiState.Error(e)
        }
    }

    sealed class UiState {
        object Uninitialized : UiState()
        object Done : UiState()
        data class Error(val exception: Throwable, val uuid: UUID = UUID.randomUUID()) : UiState()
    }

    class Factory(
        private val application: Application,
        private val healthConnectManager: HealthConnectManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(application, healthConnectManager) as T
    }
}
