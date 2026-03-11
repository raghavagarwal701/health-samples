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

import android.annotation.SuppressLint
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarHost
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.healthconnectsample.data.HealthConnectManager
import com.example.healthconnectsample.data.ProfileRepository
import com.example.healthconnectsample.presentation.navigation.HealthConnectNavigation
import com.example.healthconnectsample.presentation.navigation.Screen
import com.example.healthconnectsample.presentation.theme.HealthConnectTheme

const val TAG = "Health Connect sample"

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun HealthConnectApp(
    healthConnectManager: HealthConnectManager,
    profileRepository: ProfileRepository,
    initialRoute: String = Screen.HomeScreen.route
) {
    HealthConnectTheme {
        val scaffoldState = rememberScaffoldState()
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        Scaffold(
            scaffoldState = scaffoldState,
            bottomBar = {
                val bottomNavRoutes = listOf(
                    Screen.HomeScreen.route,
                    Screen.Steps.route,
                    Screen.MealsScreen.route,
                    Screen.MoreScreen.route,
                )
                // Only show bottom bar on the four main screens
                if (currentRoute in bottomNavRoutes) {
                    BottomNavigation(
                        backgroundColor = Color(0xFF0A0A0A),
                        contentColor    = Color(0xFF3DDB85),
                        elevation       = 8.dp,
                    ) {
                        val selectedColor   = Color(0xFF3DDB85)
                        val unselectedColor = Color(0xFF555555)
                        BottomNavigationItem(
                            selected = currentRoute == Screen.HomeScreen.route,
                            onClick = {
                                navController.navigate(Screen.HomeScreen.route) {
                                    popUpTo(Screen.HomeScreen.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            selectedContentColor   = selectedColor,
                            unselectedContentColor = unselectedColor,
                        )
                        BottomNavigationItem(
                            selected = currentRoute == Screen.Steps.route,
                            onClick = {
                                navController.navigate(Screen.Steps.route) {
                                    popUpTo(Screen.HomeScreen.route)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(Icons.Default.DirectionsWalk, contentDescription = "Activity") },
                            label = { Text("Activity") },
                            selectedContentColor   = selectedColor,
                            unselectedContentColor = unselectedColor,
                        )
                        BottomNavigationItem(
                            selected = currentRoute == Screen.MealsScreen.route,
                            onClick = {
                                navController.navigate(Screen.MealsScreen.route) {
                                    popUpTo(Screen.HomeScreen.route)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(Icons.Default.Restaurant, contentDescription = "Meals") },
                            label = { Text("Meals") },
                            selectedContentColor   = selectedColor,
                            unselectedContentColor = unselectedColor,
                        )
                        BottomNavigationItem(
                            selected = currentRoute == Screen.MoreScreen.route,
                            onClick = {
                                navController.navigate(Screen.MoreScreen.route) {
                                    popUpTo(Screen.HomeScreen.route)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "More") },
                            label = { Text("More") },
                            selectedContentColor   = selectedColor,
                            unselectedContentColor = unselectedColor,
                        )
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(it) { data -> Snackbar(snackbarData = data) }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                HealthConnectNavigation(
                    healthConnectManager = healthConnectManager,
                    profileRepository = profileRepository,
                    navController = navController,
                    scaffoldState = scaffoldState,
                    startDestination = initialRoute
                )
            }
        }
    }
}
