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
package com.example.healthconnectsample.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources.NotFoundException
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import com.example.healthconnectsample.R
import com.example.healthconnectsample.data.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.io.InvalidObjectException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlin.reflect.KClass

// The minimum android level that can use Health Connect
const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.O_MR1

/** Demonstrates reading and writing from Health Connect. */
class HealthConnectManager(private val context: Context) {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val healthConnectCompatibleApps by lazy {
        val intent = Intent("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE")

        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL
            )
        }

        packages.associate {
            val icon = try {
                context.packageManager.getApplicationIcon(it.activityInfo.packageName)
            } catch (e: NotFoundException) {
                null
            }
            val label = context.packageManager.getApplicationLabel(it.activityInfo.applicationInfo)
                .toString()
            it.activityInfo.packageName to
                    HealthConnectAppInfo(
                        packageName = it.activityInfo.packageName,
                        icon = icon,
                        appLabel = label
                    )
        }
    }

    var availability = mutableStateOf(SDK_UNAVAILABLE)
        private set

    fun checkAvailability() {
        availability.value = HealthConnectClient.getSdkStatus(context)
    }

    init {
        checkAvailability()
    }

    /**
     * Determines whether all the specified permissions are already granted. It is recommended to
     * call [PermissionController.getGrantedPermissions] first in the permissions flow, as if the
     * permissions are already granted then there is no need to request permissions via
     * [PermissionController.createRequestPermissionResultContract].
     */
    suspend fun hasAllPermissions(permissions: Set<String>): Boolean {
        return healthConnectClient.permissionController.getGrantedPermissions()
            .containsAll(permissions)
    }

    fun requestPermissionsActivityContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    suspend fun revokeAllPermissions() {
        healthConnectClient.permissionController.revokeAllPermissions()
    }

    /**
     * Obtains a list of [ExerciseSessionRecord]s in a specified time frame. An Exercise Session Record is a
     * period of time given to an activity, that would make sense to a user, e.g. "Afternoon run"
     * etc. It does not necessarily mean, however, that the user was *running* for that entire time,
     * more that conceptually, this was the activity being undertaken.
     */
    suspend fun readExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        val request = ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.readRecords(request)
        return response.records
    }

    /**
     * Writes an [ExerciseSessionRecord] to Health Connect, and additionally writes underlying data for
     * the session too, such as [StepsRecord], [DistanceRecord] etc.
     */
    suspend fun writeExerciseSession(
        start: ZonedDateTime,
        end: ZonedDateTime
    ): InsertRecordsResponse {
        return healthConnectClient.insertRecords(
            listOf(
                ExerciseSessionRecord(
                    metadata = Metadata.manualEntry(),
                    startTime = start.toInstant(),
                    startZoneOffset = start.offset,
                    endTime = end.toInstant(),
                    endZoneOffset = end.offset,
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                    title = "My Run #${Random.nextInt(0, 60)}"
                ),
                StepsRecord(
                    metadata = Metadata.manualEntry(),
                    startTime = start.toInstant(),
                    startZoneOffset = start.offset,
                    endTime = end.toInstant(),
                    endZoneOffset = end.offset,
                    count = (1000 + 1000 * Random.nextInt(3)).toLong()
                ),
                DistanceRecord(
                    metadata = Metadata.manualEntry(),
                    startTime = start.toInstant(),
                    startZoneOffset = start.offset,
                    endTime = end.toInstant(),
                    endZoneOffset = end.offset,
                    distance = Length.meters((1000 + 100 * Random.nextInt(20)).toDouble())
                ),
                TotalCaloriesBurnedRecord(
                    metadata = Metadata.manualEntry(),
                    startTime = start.toInstant(),
                    startZoneOffset = start.offset,
                    endTime = end.toInstant(),
                    endZoneOffset = end.offset,
                    energy = Energy.calories(140 + (Random.nextInt(20)) * 0.01)
                )
            ) + buildHeartRateSeries(start, end)
        )
    }

    /**
     * Deletes an [ExerciseSessionRecord] and underlying data.
     */
    suspend fun deleteExerciseSession(uid: String) {
        val exerciseSession = healthConnectClient.readRecord(ExerciseSessionRecord::class, uid)
        healthConnectClient.deleteRecords(
            ExerciseSessionRecord::class,
            recordIdsList = listOf(uid),
            clientRecordIdsList = emptyList()
        )
        val timeRangeFilter = TimeRangeFilter.between(
            exerciseSession.record.startTime,
            exerciseSession.record.endTime
        )
        val rawDataTypes: Set<KClass<out Record>> = setOf(
            HeartRateRecord::class,
            SpeedRecord::class,
            DistanceRecord::class,
            StepsRecord::class,
            TotalCaloriesBurnedRecord::class
        )
        rawDataTypes.forEach { rawType ->
            healthConnectClient.deleteRecords(rawType, timeRangeFilter)
        }
    }

    /**
     * Reads aggregated data and raw data for selected data types, for a given [ExerciseSessionRecord].
     */
    suspend fun readAssociatedSessionData(
        uid: String
    ): ExerciseSessionData {
        val exerciseSession = healthConnectClient.readRecord(ExerciseSessionRecord::class, uid)
        // Use the start time and end time from the session, for reading raw and aggregate data.
        val timeRangeFilter = TimeRangeFilter.between(
            startTime = exerciseSession.record.startTime,
            endTime = exerciseSession.record.endTime
        )
        val aggregateDataTypes = setOf(
            ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
            StepsRecord.COUNT_TOTAL,
            DistanceRecord.DISTANCE_TOTAL,
            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
            HeartRateRecord.BPM_AVG,
            HeartRateRecord.BPM_MAX,
            HeartRateRecord.BPM_MIN,
        )
        // Limit the data read to just the application that wrote the session. This may or may not
        // be desirable depending on the use case: In some cases, it may be useful to combine with
        // data written by other apps.
        val dataOriginFilter = setOf(exerciseSession.record.metadata.dataOrigin)
        val aggregateRequest = AggregateRequest(
            metrics = aggregateDataTypes,
            timeRangeFilter = timeRangeFilter,
            dataOriginFilter = dataOriginFilter)
        val aggregateData = healthConnectClient.aggregate(aggregateRequest)

        return ExerciseSessionData(
            uid = uid,
            totalActiveTime = aggregateData[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL],
            totalSteps = aggregateData[StepsRecord.COUNT_TOTAL],
            totalDistance = aggregateData[DistanceRecord.DISTANCE_TOTAL],
            totalEnergyBurned = aggregateData[TotalCaloriesBurnedRecord.ENERGY_TOTAL],
            minHeartRate = aggregateData[HeartRateRecord.BPM_MIN],
            maxHeartRate = aggregateData[HeartRateRecord.BPM_MAX],
            avgHeartRate = aggregateData[HeartRateRecord.BPM_AVG],
        )
    }

    /**
     * Deletes all existing sleep data.
     */
    suspend fun deleteAllSleepData() {
        val now = Instant.now()
        healthConnectClient.deleteRecords(SleepSessionRecord::class, TimeRangeFilter.before(now))
    }

    /**
     * Generates a week's worth of sleep data using a [SleepSessionRecord] to describe the overall
     * period of sleep, with multiple [SleepSessionRecord.Stage] periods which cover the entire
     * [SleepSessionRecord]. For the purposes of this sample, the sleep stage data is generated randomly.
     */
    suspend fun generateSleepData() {
        val records = mutableListOf<Record>()
        // Make yesterday the last day of the sleep data
        val lastDay = ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.DAYS)
        val notes = context.resources.getStringArray(R.array.sleep_notes_array)
        // Create 7 days-worth of sleep data
        for (i in 0..7) {
            val wakeUp = lastDay.minusDays(i.toLong())
                .withHour(Random.nextInt(7, 10))
                .withMinute(Random.nextInt(0, 60))
            val bedtime = wakeUp.minusDays(1)
                .withHour(Random.nextInt(19, 22))
                .withMinute(Random.nextInt(0, 60))
            val sleepSession = SleepSessionRecord(
                metadata = Metadata.manualEntry(),
                notes = notes[Random.nextInt(0, notes.size)],
                startTime = bedtime.toInstant(),
                startZoneOffset = bedtime.offset,
                endTime = wakeUp.toInstant(),
                endZoneOffset = wakeUp.offset,
                stages = generateSleepStages(bedtime, wakeUp)
            )
            records.add(sleepSession)
        }
        healthConnectClient.insertRecords(records)
    }

    /**
     * Reads sleep sessions for the previous seven days (from yesterday) to show a week's worth of
     * sleep data.
     *
     * In addition to reading [SleepSessionRecord]s, for each session, the duration is calculated to
     * demonstrate aggregation, and the underlying [SleepSessionRecord.Stage] data is also read.
     */
    suspend fun readSleepSessions(): List<SleepSessionData> {
        val lastDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS)
            .minusDays(1)
            .withHour(12)
        val firstDay = lastDay
            .minusDays(7)

        val sessions = mutableListOf<SleepSessionData>()
        val sleepSessionRequest = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(firstDay.toInstant(), lastDay.toInstant()),
            ascendingOrder = false
        )
        val sleepSessions = healthConnectClient.readRecords(sleepSessionRequest)
        sleepSessions.records.forEach { session ->
            val sessionTimeFilter = TimeRangeFilter.between(session.startTime, session.endTime)
            val durationAggregateRequest = AggregateRequest(
                metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                timeRangeFilter = sessionTimeFilter
            )
            val aggregateResponse = healthConnectClient.aggregate(durationAggregateRequest)
            sessions.add(
                SleepSessionData(
                    uid = session.metadata.id,
                    title = session.title,
                    notes = session.notes,
                    startTime = session.startTime,
                    startZoneOffset = session.startZoneOffset,
                    endTime = session.endTime,
                    endZoneOffset = session.endZoneOffset,
                    duration = aggregateResponse[SleepSessionRecord.SLEEP_DURATION_TOTAL],
                    stages = session.stages
                )
            )
        }
        return sessions
    }

    /**
     * Writes [WeightRecord] to Health Connect.
     */
    suspend fun writeWeightInput(weight: WeightRecord) {
        val records = listOf(weight)
        healthConnectClient.insertRecords(records)
    }

    /**
     * Reads in existing [WeightRecord]s.
     */
    suspend fun readWeightInputs(start: Instant, end: Instant): List<WeightRecord> {
        val request = ReadRecordsRequest(
            recordType = WeightRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.readRecords(request)
        return response.records
    }

    /**
     * Returns the weekly average of [WeightRecord]s.
     */
    suspend fun computeWeeklyAverage(start: Instant, end: Instant): Mass? {
        val request = AggregateRequest(
            metrics = setOf(WeightRecord.WEIGHT_AVG),
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.aggregate(request)
        return response[WeightRecord.WEIGHT_AVG]
    }

    /**
     * Deletes a [WeightRecord]s.
     */
    suspend fun deleteWeightInput(uid: String) {
        healthConnectClient.deleteRecords(
            WeightRecord::class,
            recordIdsList = listOf(uid),
            clientRecordIdsList = emptyList()
        )
    }

    /**
     * Obtains a changes token for the specified record types.
     */
    suspend fun getChangesToken(dataTypes: Set<KClass<out Record>>): String {
        val request = ChangesTokenRequest(dataTypes)
        return healthConnectClient.getChangesToken(request)
    }

    /**
     * Creates a [Flow] of change messages, using a changes token as a start point. The flow will
     * terminate when no more changes are available, and the final message will contain the next
     * changes token to use.
     */
    suspend fun getChanges(token: String): Flow<ChangesMessage> = flow {
        var nextChangesToken = token
        do {
            val response = healthConnectClient.getChanges(nextChangesToken)
            if (response.changesTokenExpired) {
                // As described here: https://developer.android.com/guide/health-and-fitness/health-connect/data-and-data-types/differential-changes-api
                // tokens are only valid for 30 days. It is important to check whether the token has
                // expired. As well as ensuring there is a fallback to using the token (for example
                // importing data since a certain date), more importantly, the app should ensure
                // that the changes API is used sufficiently regularly that tokens do not expire.
                throw IOException("Changes token has expired")
            }
            emit(ChangesMessage.ChangeList(response.changes))
            nextChangesToken = response.nextChangesToken
        } while (response.hasMore)
        emit(ChangesMessage.NoMoreChanges(nextChangesToken))
    }

    /** Creates a random sleep stage that spans the specified [start] to [end] time. */
    private fun generateSleepStages(
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<SleepSessionRecord.Stage> {
        val sleepStages = mutableListOf<SleepSessionRecord.Stage>()
        var stageStart = start
        while (stageStart < end) {
            val stageEnd = stageStart.plusMinutes(Random.nextLong(30, 120))
            val checkedEnd = if (stageEnd > end) end else stageEnd
            sleepStages.add(
                SleepSessionRecord.Stage(
                    stage = randomSleepStage(),
                    startTime = stageStart.toInstant(),
                    endTime = checkedEnd.toInstant()))
            stageStart = checkedEnd
        }
        return sleepStages
    }

    /**
     * Convenience function to fetch a time-based record and return series data based on the record.
     * Record types compatible with this function must be declared in the
     * [com.example.healthconnectsample.presentation.screen.recordlist.RecordType] enum.
     */
    suspend fun fetchSeriesRecordsFromUid(recordType: KClass<out Record>, uid: String, seriesRecordsType: KClass<out Record>): List<Record> {
        val recordResponse = healthConnectClient.readRecord(recordType, uid)
        // Use the start time and end time from the session, for reading raw and aggregate data.
        val timeRangeFilter =
            when (recordResponse.record) {
                // Change to use series record instead
                is ExerciseSessionRecord -> {
                    val record = recordResponse.record as ExerciseSessionRecord
                    TimeRangeFilter.between(startTime = record.startTime, endTime = record.endTime)
                }
                is SleepSessionRecord -> {
                    val record = recordResponse.record as SleepSessionRecord
                    TimeRangeFilter.between(startTime = record.startTime, endTime = record.endTime)
                }
                else -> {
                    throw InvalidObjectException("Record with unregistered data type returned")
                }
            }

        // Limit the data read to just the application that wrote the session. This may or may not
        // be desirable depending on the use case: In some cases, it may be useful to combine with
        // data written by other apps.
        val dataOriginFilter = setOf(recordResponse.record.metadata.dataOrigin)
        val request =
            ReadRecordsRequest(
                recordType = seriesRecordsType,
                dataOriginFilter = dataOriginFilter,
                timeRangeFilter = timeRangeFilter)
        return healthConnectClient.readRecords(request).records
    }

    private fun buildHeartRateSeries(
        sessionStartTime: ZonedDateTime,
        sessionEndTime: ZonedDateTime
    ): HeartRateRecord {
        val samples = mutableListOf<HeartRateRecord.Sample>()
        var time = sessionStartTime
        while (time.isBefore(sessionEndTime)) {
            samples.add(
                HeartRateRecord.Sample(
                    time = time.toInstant(), beatsPerMinute = (80 + Random.nextInt(80)).toLong()))
            time = time.plusSeconds(30)
        }
        return HeartRateRecord(
            metadata = Metadata.manualEntry(),
            startTime = sessionStartTime.toInstant(),
            startZoneOffset = sessionStartTime.offset,
            endTime = sessionEndTime.toInstant(),
            endZoneOffset = sessionEndTime.offset,
            samples = samples)
    }

    fun isFeatureAvailable(feature: Int): Boolean{
        return healthConnectClient
            .features
            .getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    // ===== Backend Integration Functions =====

    /**
     * Reads heart rate data for a given time range.
     * Returns a list of HR samples with timestamps.
     */
    suspend fun readHeartRateData(start: Instant, end: Instant): List<HeartRateRecord> {
        val request = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        return healthConnectClient.readRecords(request).records
    }

    /**
     * Reads HRV (RMSSD) data for a given time range.
     */
    suspend fun readHrvData(start: Instant, end: Instant): List<HeartRateVariabilityRmssdRecord> {
        val request = ReadRecordsRequest(
            recordType = HeartRateVariabilityRmssdRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        return healthConnectClient.readRecords(request).records
    }

    /**
     * Reads resting heart rate data for a given time range.
     */
    suspend fun readRestingHeartRate(start: Instant, end: Instant): List<RestingHeartRateRecord> {
        val request = ReadRecordsRequest(
            recordType = RestingHeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        return healthConnectClient.readRecords(request).records
    }

    /**
     * Reads aggregated steps data (total steps for a time range).
     */
    suspend fun readStepsAggregate(start: Instant, end: Instant): Long? {
        val request = AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.aggregate(request)
        return response[StepsRecord.COUNT_TOTAL]
    }

    /**
     * Reads aggregated distance (total distance for a time range).
     */
    suspend fun readDistanceAggregate(start: Instant, end: Instant): Length? {
        val request = AggregateRequest(
            metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.aggregate(request)
        return response[DistanceRecord.DISTANCE_TOTAL]
    }

    /**
     * Reads aggregated calories (total calories for a time range).
     */
    suspend fun readCaloriesAggregate(start: Instant, end: Instant): Energy? {
        val request = AggregateRequest(
            metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.aggregate(request)
        return response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]
    }

    /**
     * Master function: Collects and aggregates 7 days of health data into a
     * HealthDataPayload suitable for the Pulse Backend API.
     */
    /**
     * Master function: Collects and aggregates 7 days of health data into a
     * HealthDataPayload suitable for the Pulse Backend API.
     */
    suspend fun collectHealthDataForBackend(
        userProfile: UserProfilePayload? = null
    ): HealthDataPayload {
        val now = Instant.now()
        val sevenDaysAgo = now.minus(7, ChronoUnit.DAYS)
        val oneDayAgo = now.minus(1, ChronoUnit.DAYS)

        // --- Activity Summary ---
        val activitySummary = try {
            val totalSteps = readStepsAggregate(sevenDaysAgo, now)
            val totalDistance = readDistanceAggregate(sevenDaysAgo, now)
            val totalCalories = readCaloriesAggregate(sevenDaysAgo, now)

            val dailyAvgSteps = totalSteps?.let { it / 7 }
            val dailyAvgDistanceKm = totalDistance?.inKilometers?.let { it / 7 }
            val dailyAvgCalories = totalCalories?.inKilocalories?.let { it / 7 }

            // Calculate daily breakdown for the past 7 days
            val dailyActivity = (0..6).map { i ->
                val dayStart = ZonedDateTime.now().minusDays(i.toLong()).truncatedTo(ChronoUnit.DAYS).toInstant()
                val dayEnd = dayStart.plus(1, ChronoUnit.DAYS)
                
                val daySteps = readStepsAggregate(dayStart, dayEnd) ?: 0L
                val dayDistance = readDistanceAggregate(dayStart, dayEnd)?.inKilometers ?: 0.0
                val dayCalories = readCaloriesAggregate(dayStart, dayEnd)?.inKilocalories ?: 0.0

                mapOf(
                    "date" to dayStart.toString(),
                    "steps" to daySteps,
                    "distance_km" to dayDistance,
                    "calories" to dayCalories
                )
            }

            ActivitySummaryPayload(
                dailyAverages = mapOf(
                    "steps" to (dailyAvgSteps ?: 0L),
                    "distance_km" to (dailyAvgDistanceKm ?: 0.0),
                    "calories_burned" to (dailyAvgCalories ?: 0.0)
                ),
                pastSevenDaysActivity = dailyActivity
            )
        } catch (e: Exception) {
            null
        }

        // --- Sleep Summary ---
        val sleepSummary = try {
            val sleepSessions = readSleepSessions() // This gets last 7 days
            val sessionsData = sleepSessions.map { session ->
                val deepSleepDuration = session.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_DEEP }.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                val lightSleepDuration = session.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_LIGHT }.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                val awakeDuration = session.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE }.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                val remSleepDuration = session.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_REM }.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }

                mapOf<String, Any>(
                    "start_time" to session.startTime.toString(),
                    "end_time" to session.endTime.toString(),
                    "duration_minutes" to (session.duration?.toMinutes() ?: 0L),
                    "deep_sleep_minutes" to deepSleepDuration,
                    "light_sleep_minutes" to lightSleepDuration,
                    "awake_minutes" to awakeDuration,
                    "rem_sleep_minutes" to remSleepDuration
                )
            }

            val avgDurationMinutes = if (sleepSessions.isNotEmpty()) {
                sleepSessions.mapNotNull { it.duration?.toMinutes() }.average()
            } else 0.0

            SleepSummaryPayload(
                sleepSessions = sessionsData,
                sleepQuality = mapOf(
                    "average_duration_minutes" to avgDurationMinutes,
                    "total_sessions" to sleepSessions.size
                )
            )
        } catch (e: Exception) {
            null
        }

        // --- Heart Rate Summary ---
        val heartRateSummary = try {
            val hrRecords = readHeartRateData(sevenDaysAgo, now)
            val allSamples = hrRecords.flatMap { it.samples }
            val avgHr = if (allSamples.isNotEmpty()) {
                allSamples.map { it.beatsPerMinute }.average()
            } else null
            val minHr = allSamples.minOfOrNull { it.beatsPerMinute }?.toDouble()
            val maxHr = allSamples.maxOfOrNull { it.beatsPerMinute }?.toDouble()

            HeartRateSummaryPayload(
                minHr = minHr,
                maxHr = maxHr,
                averageHr = avgHr
            )
        } catch (e: Exception) {
            null
        }

        // --- HRV Summary --- (Unchanged for now, but keeping for completeness if needed)
        val hrvSummary = try {
            val hrvRecords = readHrvData(sevenDaysAgo, now)
            val avgHrv = if (hrvRecords.isNotEmpty()) {
                hrvRecords.map { it.heartRateVariabilityMillis }.average()
            } else null

            HrvSummaryPayload(
                averageHrv = avgHrv,
                hrvTrend = if (hrvRecords.size >= 2) {
                    val firstHalf = hrvRecords.take(hrvRecords.size / 2).map { it.heartRateVariabilityMillis }.average()
                    val secondHalf = hrvRecords.drop(hrvRecords.size / 2).map { it.heartRateVariabilityMillis }.average()
                    when {
                        secondHalf > firstHalf * 1.05 -> "improving"
                        secondHalf < firstHalf * 0.95 -> "declining"
                        else -> "stable"
                    }
                } else "insufficient_data",
                measurements = hrvRecords.takeLast(10).map { record ->
                    mapOf(
                        "hrv_ms" to record.heartRateVariabilityMillis,
                        "time" to record.time.toString()
                    )
                }
            )
        } catch (e: Exception) {
            null
        }

        // --- Exercise Summary ---
        val exerciseSummary = try {
            val exerciseSessions = readExerciseSessions(sevenDaysAgo, now)
            val sessionsData = exerciseSessions.map { session ->
                val sessionData = try {
                    readAssociatedSessionData(session.metadata.id)
                } catch (e: Exception) { null }
                
                val hrMin = sessionData?.minHeartRate ?: 0L
                val hrMax = sessionData?.maxHeartRate ?: 0L
                val hrAvg = sessionData?.avgHeartRate ?: 0L

                val hrStats = mapOf(
                     "min" to hrMin,
                     "max" to hrMax,
                     "avg" to hrAvg
                )

                mapOf<String, Any>(
                    "name" to (session.title ?: "Unnamed"),
                    "exercise_type" to getExerciseName(session.exerciseType),
                    "start_time" to session.startTime.toString(),
                    "duration_minutes" to (Duration.between(session.startTime, session.endTime).toMinutes()),
                    "distance_meters" to (sessionData?.totalDistance?.inMeters ?: 0.0),
                    "calories" to (sessionData?.totalEnergyBurned?.inKilocalories ?: 0.0),
                    "hr_stats" to hrStats
                )
            }

            val exerciseTypes = exerciseSessions.map {
                getExerciseName(it.exerciseType)
            }.distinct()

            ExerciseSummaryPayload(
                sessions = sessionsData,
                totalSessions = exerciseSessions.size,
                exerciseTypes = exerciseTypes
            )
        } catch (e: Exception) {
            null
        }

        // --- Weight (for user_data) ---
        val latestWeight = try {
            val weights = readWeightInputs(sevenDaysAgo, now)
            weights.lastOrNull()?.weight?.inKilograms
        } catch (e: Exception) { null }

        // Build final user profile, merging weight if available
        val finalUserProfile = userProfile?.copy(
            weightKg = latestWeight ?: userProfile.weightKg
        ) ?: UserProfilePayload(weightKg = latestWeight)

        return HealthDataPayload(
            activitySummary = activitySummary,
            sleepSummary = sleepSummary,
            heartRateSummary = heartRateSummary,
            hrvSummary = hrvSummary,
            exerciseSummary = exerciseSummary,
            userData = finalUserProfile
        )
    }

    // Represents the two types of messages that can be sent in a Changes flow.
    sealed class ChangesMessage {
        data class NoMoreChanges(val nextChangesToken: String) : ChangesMessage()

        data class ChangeList(val changes: List<Change>) : ChangesMessage()
    }
}

