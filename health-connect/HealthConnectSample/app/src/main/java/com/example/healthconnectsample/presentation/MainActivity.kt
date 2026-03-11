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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/**
 * The entry point into the sample.
 */
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force dark status bar and navigation bar
        window.statusBarColor = android.graphics.Color.parseColor("#0A0A0A")
        window.navigationBarColor = android.graphics.Color.parseColor("#0A0A0A")
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val healthConnectManager = (application as BaseApplication).healthConnectManager
        val profileRepository = (application as BaseApplication).profileRepository

        setContent {
            HealthConnectApp(
                healthConnectManager = healthConnectManager,
                profileRepository = profileRepository,
                initialRoute = resolveInitialRoute(intent)
            )
        }
    }

    /** When the user taps a meal notification the activity is brought to front via FLAG_ACTIVITY_SINGLE_TOP. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** Returns the route the app should open to, based on the launching intent. */
    private fun resolveInitialRoute(intent: Intent?): String {
        val navigateTo = intent?.getStringExtra(MealNotificationReceiver.EXTRA_NAVIGATE_TO)
        return if (navigateTo == MealNotificationReceiver.MEALS_ROUTE) {
            MealNotificationReceiver.MEALS_ROUTE
        } else {
            "home_screen"
        }
    }
}
