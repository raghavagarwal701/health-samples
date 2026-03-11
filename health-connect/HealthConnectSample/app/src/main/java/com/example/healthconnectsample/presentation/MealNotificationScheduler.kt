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
package com.example.healthconnectsample.presentation

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules four daily meal-reminder alarms (breakfast, lunch, snacks, dinner) using
 * AlarmManager.setRepeating so they repeat every 24 hours without needing
 * SCHEDULE_EXACT_ALARM permission.
 *
 * Default times:
 *  - Breakfast : 08:00
 *  - Lunch     : 13:00
 *  - Snacks    : 16:00
 *  - Dinner    : 19:00
 */
object MealNotificationScheduler {

    private data class MealAlarm(val mealType: String, val hour: Int, val minute: Int)

    private val ALARMS = listOf(
        MealAlarm(MealNotificationReceiver.MEAL_BREAKFAST, 8,  0),
        MealAlarm(MealNotificationReceiver.MEAL_LUNCH,    13,  0),
        MealAlarm(MealNotificationReceiver.MEAL_SNACKS,   16,  0),
        MealAlarm(MealNotificationReceiver.MEAL_DINNER,   19,  0),
    )

    /** Creates the notification channel (safe to call multiple times). */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MealNotificationReceiver.CHANNEL_ID,
                "Meal Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders to log your meals"
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    /** Schedules (or re-schedules) all four daily meal alarms. */
    fun schedule(context: Context) {
        createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        ALARMS.forEach { alarm ->
            val pendingIntent = buildPendingIntent(context, alarm.mealType)

            val triggerAt = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE,      alarm.minute)
                set(Calendar.SECOND,      0)
                set(Calendar.MILLISECOND, 0)
                // If the time has already passed today, start tomorrow
                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
            }

            // setRepeating is inexact on API 19+ – no special permission required
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    }

    /** Cancels all four meal alarms. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ALARMS.forEach { alarm ->
            alarmManager.cancel(buildPendingIntent(context, alarm.mealType))
        }
    }

    private fun buildPendingIntent(context: Context, mealType: String): PendingIntent {
        val intent = Intent(context, MealNotificationReceiver::class.java).apply {
            putExtra(MealNotificationReceiver.EXTRA_MEAL_TYPE, mealType)
        }
        return PendingIntent.getBroadcast(
            context,
            mealType.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
