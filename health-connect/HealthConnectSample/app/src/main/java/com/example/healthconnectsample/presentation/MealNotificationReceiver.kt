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

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.healthconnectsample.R

/**
 * BroadcastReceiver that fires a meal reminder notification at the scheduled time.
 * The notification's tap action navigates the user directly to the Meals screen.
 */
class MealNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val mealType = intent.getStringExtra(EXTRA_MEAL_TYPE) ?: return

        val (title, message) = when (mealType) {
            MEAL_BREAKFAST -> "Breakfast time!" to "Log your breakfast to track your nutrition"
            MEAL_LUNCH     -> "Lunch time!" to "Don't forget to log your lunch"
            MEAL_SNACKS    -> "Snack time!" to "Log your snacks to stay on track"
            MEAL_DINNER    -> "Dinner time!" to "Log your dinner to complete today's nutrition"
            else           -> return
        }

        // Build the intent that will open MainActivity and navigate to the Meals screen
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, MEALS_ROUTE)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            mealType.hashCode(),
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(mealType.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID       = "meal_reminders"
        const val EXTRA_MEAL_TYPE  = "meal_type"
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val MEALS_ROUTE      = "meals_screen"

        const val MEAL_BREAKFAST = "breakfast"
        const val MEAL_LUNCH     = "lunch"
        const val MEAL_SNACKS    = "snacks"
        const val MEAL_DINNER    = "dinner"
    }
}
