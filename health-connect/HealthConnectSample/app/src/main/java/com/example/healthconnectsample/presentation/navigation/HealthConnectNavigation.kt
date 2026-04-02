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
package com.example.healthconnectsample.presentation.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.ScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.example.healthconnectsample.data.HealthConnectManager
import com.example.healthconnectsample.presentation.screen.SettingsScreen
import com.example.healthconnectsample.presentation.screen.WelcomeScreen

import com.example.healthconnectsample.presentation.screen.exercisesession.ExerciseSessionScreen
import com.example.healthconnectsample.presentation.screen.exercisesession.ExerciseSessionViewModel
import com.example.healthconnectsample.presentation.screen.exercisesession.ExerciseSessionViewModelFactory
import com.example.healthconnectsample.presentation.screen.exercisesessiondetail.ExerciseSessionDetailScreen
import com.example.healthconnectsample.presentation.screen.exercisesessiondetail.ExerciseSessionDetailViewModel
import com.example.healthconnectsample.presentation.screen.exercisesessiondetail.ExerciseSessionDetailViewModelFactory

import com.example.healthconnectsample.presentation.screen.privacypolicy.PrivacyPolicyScreen
import com.example.healthconnectsample.presentation.screen.recordlist.RecordType
import com.example.healthconnectsample.presentation.screen.steps.StepsScreen
import com.example.healthconnectsample.presentation.screen.steps.StepsViewModel
import com.example.healthconnectsample.presentation.screen.steps.StepsViewModelFactory
import com.example.healthconnectsample.presentation.screen.recordlist.RecordListScreen
import com.example.healthconnectsample.presentation.screen.recordlist.RecordListScreenViewModel
import com.example.healthconnectsample.presentation.screen.recordlist.RecordListViewModelFactory
import com.example.healthconnectsample.presentation.screen.recordlist.SeriesRecordsType
import com.example.healthconnectsample.presentation.screen.sleepsession.SleepSessionScreen
import com.example.healthconnectsample.presentation.screen.sleepsession.SleepSessionViewModel
import com.example.healthconnectsample.presentation.screen.sleepsession.SleepSessionViewModelFactory
import com.example.healthconnectsample.presentation.screen.chat.ChatScreen
import com.example.healthconnectsample.presentation.screen.chat.ChatViewModel
import com.example.healthconnectsample.presentation.screen.heartrate.HeartRateScreen
import com.example.healthconnectsample.presentation.screen.heartrate.HeartRateViewModel
import com.example.healthconnectsample.presentation.screen.heartrate.HeartRateViewModelFactory
import com.example.healthconnectsample.showExceptionSnackbar
import kotlinx.coroutines.launch

import com.example.healthconnectsample.data.ProfileRepository
import com.example.healthconnectsample.presentation.screen.profile.ProfileScreen
import com.example.healthconnectsample.presentation.screen.profile.ProfileViewModel
import com.example.healthconnectsample.presentation.screen.profile.ProfileViewModelFactory

import com.example.healthconnectsample.presentation.screen.productscanner.ProductScannerScreen
import com.example.healthconnectsample.presentation.screen.productscanner.ProductScannerViewModel
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import com.example.healthconnectsample.presentation.screen.home.HomeScreen
import com.example.healthconnectsample.presentation.screen.home.HomeViewModel
import com.example.healthconnectsample.presentation.screen.meals.MealEntry
import com.example.healthconnectsample.presentation.screen.meals.MealsScreen
import com.example.healthconnectsample.presentation.screen.meals.MealsViewModel
import com.example.healthconnectsample.presentation.screen.more.MoreScreen

/**
 * Provides the navigation in the app.
 */
@Composable
fun HealthConnectNavigation(
    navController: NavHostController,
    healthConnectManager: HealthConnectManager,
    profileRepository: ProfileRepository,
    scaffoldState: ScaffoldState,
    startDestination: String = Screen.HomeScreen.route,
    onMealsBottomBarVisibilityChange: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIn(animationSpec = tween(300)) { IntOffset(it.width, 0) } + fadeIn(tween(300))
        },
        exitTransition = {
            slideOut(animationSpec = tween(300)) { IntOffset(-it.width / 3, 0) } + fadeOut(tween(300))
        },
        popEnterTransition = {
            slideIn(animationSpec = tween(300)) { IntOffset(-it.width / 3, 0) } + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOut(animationSpec = tween(300)) { IntOffset(it.width, 0) } + fadeOut(tween(300))
        }
    ) {
        val availability by healthConnectManager.availability
        composable(Screen.HomeScreen.route) {
            val context = LocalContext.current
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    application = context.applicationContext as Application,
                    healthConnectManager = healthConnectManager
                )
            )
            // Share the same MealsViewModel instance (persists across tabs)
            val mealsViewModel: MealsViewModel = viewModel(
                factory = MealsViewModel.Factory(context.applicationContext as Application)
            )
            val permissionsGranted by viewModel.permissionsGranted
            val homeData by viewModel.homeData
            val selectedDate by viewModel.selectedDate
            val permissions = viewModel.permissions
            val onPermissionsResult = { viewModel.initialLoad() }

            // Reload whenever the home screen comes back into view (e.g. after adding a meal)
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.initialLoad()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            val permissionsLauncher =
                rememberLauncherForActivityResult(viewModel.permissionsLauncher) {
                    onPermissionsResult()
                }
            HomeScreen(
                permissionsGranted = permissionsGranted,
                permissions = permissions,
                homeData = homeData,
                selectedDate = selectedDate,
                onPreviousDay = { viewModel.previousDay() },
                onNextDay = { viewModel.nextDay() },
                onPickDate = { date -> viewModel.selectDate(date) },
                uiState = viewModel.uiState.value,
                onError = { exception ->
                    showExceptionSnackbar(scaffoldState, scope, exception)
                },
                onPermissionsResult = { viewModel.initialLoad() },
                onPermissionsLaunch = { values -> permissionsLauncher.launch(values) },
                onNavigateToMeals = { navController.navigate(Screen.MealsScreen.route) },
                onNavigateToSteps = { navController.navigate(Screen.Steps.route) },
                recentMeals = mealsViewModel.entriesForDate(selectedDate).takeLast(2),
                isRefreshing = viewModel.isRefreshing.value,
                onRefresh = { viewModel.refresh() },
            )
        }
        composable(Screen.WelcomeScreen.route) {
            WelcomeScreen(
                healthConnectAvailability = availability,
                onResumeAvailabilityCheck = {
                    healthConnectManager.checkAvailability()
                }
            )
        }
        composable(Screen.Profile.route) {
            val viewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModelFactory(
                    repository = profileRepository
                )
            )
            ProfileScreen(viewModel = viewModel)
        }
        composable(
            route = Screen.PrivacyPolicy.route,
            deepLinks = listOf(
                navDeepLink {
                    action = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"
                }
            )
        ) {
            PrivacyPolicyScreen()
        }
        composable(Screen.SettingsScreen.route){
            SettingsScreen { scope.launch { healthConnectManager.revokeAllPermissions() } }
        }
        composable(Screen.ExerciseSessions.route) {
            val viewModel: ExerciseSessionViewModel = viewModel(
                factory = ExerciseSessionViewModelFactory(
                    healthConnectManager = healthConnectManager
                )
            )
            val permissionsGranted by viewModel.permissionsGranted
            val sessionsList by viewModel.sessionsList
            val permissions = viewModel.permissions
            val backgroundReadAvailable by viewModel.backgroundReadAvailable
            val backgroundReadGranted by viewModel.backgroundReadGranted
            val onPermissionsResult = {viewModel.initialLoad()}
            val permissionsLauncher =
                rememberLauncherForActivityResult(viewModel.permissionsLauncher) {
                onPermissionsResult()}
            ExerciseSessionScreen(
                permissionsGranted = permissionsGranted,
                permissions = permissions,
                backgroundReadAvailable = backgroundReadAvailable,
                backgroundReadGranted = backgroundReadGranted,
                sessionsList = sessionsList,
                uiState = viewModel.uiState,
                onInsertClick = {
                    viewModel.insertExerciseSession()
                },
                onDetailsClick = { uid ->
                    navController.navigate(Screen.ExerciseSessionDetail.route + "/" + uid)
                },
                onDeleteClick = { uid ->
                    viewModel.deleteExerciseSession(uid)
                },
                onError = { exception ->
                    showExceptionSnackbar(scaffoldState, scope, exception)
                },
                onPermissionsResult = {
                    viewModel.initialLoad()
                },
                onPermissionsLaunch = { values ->
                    permissionsLauncher.launch(values)}
            )
        }
        composable(Screen.ExerciseSessionDetail.route + "/{$UID_NAV_ARGUMENT}") {
            val uid = it.arguments?.getString(UID_NAV_ARGUMENT)!!
            val viewModel: ExerciseSessionDetailViewModel = viewModel(
                factory = ExerciseSessionDetailViewModelFactory(
                    uid = uid,
                    healthConnectManager = healthConnectManager
                )
            )
            val permissionsGranted by viewModel.permissionsGranted
            val sessionMetrics by viewModel.sessionMetrics
            val permissions = viewModel.permissions
            val onPermissionsResult = {viewModel.initialLoad()}
            val permissionsLauncher =
                rememberLauncherForActivityResult(viewModel.permissionsLauncher) {
                onPermissionsResult()}
            ExerciseSessionDetailScreen(
                permissions = permissions,
                permissionsGranted = permissionsGranted,
                sessionMetrics = sessionMetrics,
                uiState = viewModel.uiState,
                onDetailsClick = { recordType, recordId, seriesRecordsType ->
                    navController.navigate(Screen.RecordListScreen.route + "/" + recordType + "/"+ recordId + "/" + seriesRecordsType)
                },
                onError = { exception ->
                    showExceptionSnackbar(scaffoldState, scope, exception)
                },
                onPermissionsResult = {
                    viewModel.initialLoad()
                },
                onPermissionsLaunch = { values ->
                    permissionsLauncher.launch(values)}
            )
        }
        composable(Screen.RecordListScreen.route + "/{$RECORD_TYPE}" + "/{$UID_NAV_ARGUMENT}" + "/{$SERIES_RECORDS_TYPE}") {
            val uid = it.arguments?.getString(UID_NAV_ARGUMENT)!!
            val recordTypeString = it.arguments?.getString(RECORD_TYPE)!!
            val seriesRecordsTypeString = it.arguments?.getString(SERIES_RECORDS_TYPE)!!
            val viewModel: RecordListScreenViewModel = viewModel(
                factory = RecordListViewModelFactory(
                    uid = uid,
                    recordTypeString = recordTypeString,
                    seriesRecordsTypeString = seriesRecordsTypeString,
                    healthConnectManager = healthConnectManager
                )
            )
            val permissionsGranted by viewModel.permissionsGranted
            val recordList = viewModel.recordList
            val permissions = viewModel.permissions
            val onPermissionsResult = {viewModel.initialLoad()}
            val permissionsLauncher =
                rememberLauncherForActivityResult(viewModel.permissionsLauncher) {
                    onPermissionsResult()}
            RecordListScreen(
                uid = uid,
                permissions = permissions,
                permissionsGranted = permissionsGranted,
                recordType = RecordType.valueOf(recordTypeString),
                seriesRecordsType = SeriesRecordsType.valueOf(seriesRecordsTypeString),
                recordList = recordList,
                uiState = viewModel.uiState,
                onPermissionsResult = {
                    viewModel.initialLoad()
                },
                onPermissionsLaunch = { values ->
                    permissionsLauncher.launch(values)}
            )
        }
        composable(Screen.SleepSessions.route) {
            val viewModel: SleepSessionViewModel = viewModel(
                factory = SleepSessionViewModelFactory(
                    healthConnectManager = healthConnectManager
                )
            )
            val permissionsGranted by viewModel.permissionsGranted
            val sessionsList by viewModel.sessionsList
            val permissions = viewModel.permissions
            val onPermissionsResult = {viewModel.initialLoad()}
            val permissionsLauncher =
                rememberLauncherForActivityResult(viewModel.permissionsLauncher) {
                onPermissionsResult()}
            SleepSessionScreen(
                permissionsGranted = permissionsGranted,
                permissions = permissions,
                sessionsList = sessionsList,
                uiState = viewModel.uiState,
                onInsertClick = {
                    viewModel.generateSleepData()
                },
                onError = { exception ->
                    showExceptionSnackbar(scaffoldState, scope, exception)
                },
                onPermissionsResult = {
                    viewModel.initialLoad()
                },
                onPermissionsLaunch = { values ->
                    permissionsLauncher.launch(values)}
            )
        }

        composable(Screen.Steps.route) {
            val viewModel: StepsViewModel = viewModel(
                factory = StepsViewModelFactory(
                    healthConnectManager = healthConnectManager
                )
            )
            val permissionsGranted by viewModel.permissionsGranted
            val todayData by viewModel.todayData
            val weeklyData by viewModel.weeklyData
            val dailyActivity by viewModel.dailyActivity
            val selectedDate by viewModel.selectedDate
            val permissions = viewModel.permissions
            val onPermissionsResult = { viewModel.initialLoad() }
            val permissionsLauncher =
                rememberLauncherForActivityResult(viewModel.permissionsLauncher) {
                    onPermissionsResult()
                }
            StepsScreen(
                permissionsGranted = permissionsGranted,
                permissions = permissions,
                todayData = todayData,
                weeklyData = weeklyData,
                dailyActivity = dailyActivity,
                selectedDate = selectedDate,
                onPreviousDay = { viewModel.previousDay() },
                onNextDay = { viewModel.nextDay() },
                onPickDate = { date -> viewModel.selectDate(date) },
                uiState = viewModel.uiState,
                onError = { exception ->
                    showExceptionSnackbar(scaffoldState, scope, exception)
                },
                onPermissionsResult = {
                    viewModel.initialLoad()
                },
                onPermissionsLaunch = { values ->
                    permissionsLauncher.launch(values)
                }
            )
        }
        composable(Screen.ChatScreen.route) {
            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(
                    healthConnectManager = healthConnectManager,
                    profileRepository = profileRepository
                )
            )
            ChatScreen(viewModel = viewModel)
        }
        composable(Screen.HeartRate.route) {
            val viewModel: HeartRateViewModel = viewModel(
                factory = HeartRateViewModelFactory(
                    healthConnectManager = healthConnectManager
                )
            )
            val permissionsGranted by viewModel.permissionsGranted
            val dailyHeartRates by viewModel.dailyHeartRates
            val permissions = viewModel.permissions
            val onPermissionsResult = {viewModel.initialLoad()}
            val permissionsLauncher =
                rememberLauncherForActivityResult(viewModel.permissionsLauncher) {
                    onPermissionsResult()}
            HeartRateScreen(
                permissionsGranted = permissionsGranted,
                permissions = permissions,
                dailyHeartRates = dailyHeartRates,
                uiState = viewModel.uiState,
                onError = { exception ->
                    showExceptionSnackbar(scaffoldState, scope, exception)
                },
                onPermissionsResult = {
                    viewModel.initialLoad()
                },
                onPermissionsLaunch = { values ->
                    permissionsLauncher.launch(values)}
            )
        }
        composable(Screen.ProductScanner.route) {
            val viewModel: ProductScannerViewModel = viewModel(
                factory = ProductScannerViewModel.Factory()
            )
            ProductScannerScreen(viewModel = viewModel)
        }
        composable(Screen.MealsScreen.route) {
            val context = LocalContext.current
            val viewModel: MealsViewModel = viewModel(
                factory = MealsViewModel.Factory(context.applicationContext as Application)
            )
            MealsScreen(
                viewModel = viewModel,
                onBottomBarVisibilityChange = onMealsBottomBarVisibilityChange
            )
        }
        composable(Screen.MoreScreen.route) {
            MoreScreen(
                onNavigateTo = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
