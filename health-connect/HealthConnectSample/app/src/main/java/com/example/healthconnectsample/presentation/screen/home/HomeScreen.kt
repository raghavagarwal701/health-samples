package com.example.healthconnectsample.presentation.screen.home

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthconnectsample.presentation.screen.meals.MealEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.UUID

private val BurnedColor   = Color(0xFF2E7D32)   // dark green – activity ring stat
private val ConsumedColor = Color(0xFFE65100)   // deep orange – food stat

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    permissions: Set<String>,
    permissionsGranted: Boolean,
    homeData: HomeData,
    uiState: HomeViewModel.UiState,
    selectedDate: LocalDate = LocalDate.now(),
    onPreviousDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    onPickDate: (LocalDate) -> Unit = {},
    onError: (Throwable?) -> Unit = {},
    onPermissionsResult: () -> Unit = {},
    onPermissionsLaunch: (Set<String>) -> Unit = {},
    onNavigateToMeals: () -> Unit = {},
    onNavigateToSteps: () -> Unit = {},
    recentMeals: List<MealEntry> = emptyList(),
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val errorId = remember { mutableStateOf(UUID.randomUUID()) }

    LaunchedEffect(uiState) {
        if (uiState is HomeViewModel.UiState.Uninitialized) {
            onPermissionsResult()
        }
        if (uiState is HomeViewModel.UiState.Error && errorId.value != uiState.uuid) {
            onError(uiState.exception)
            errorId.value = uiState.uuid
        }
    }

    if (uiState == HomeViewModel.UiState.Uninitialized) return

    val context = LocalContext.current
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Date selector bar ──────────────────────────────────────────────
        HomeDateSelectorBar(
            selectedDate = selectedDate,
            onPrevious = onPreviousDay,
            onNext = onNextDay,
            onPickDate = onPickDate,
            context = context,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        if (!permissionsGranted) {
            PermissionsBanner(onPermissionsLaunch = { onPermissionsLaunch(permissions) })
            Spacer(Modifier.height(12.dp))
        }

        // ── Calorie Ring Card ──────────────────────────────────────────────
        CalorieRingCard(homeData = homeData)

        Spacer(Modifier.height(16.dp))

        // ── Burned & Consumed row ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CalorieStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocalFireDepartment,
                iconTint = BurnedColor,
                label = "Burned",
                value = homeData.caloriesBurned,
                subtitle = "kcal from activity",
                onClick = onNavigateToSteps
            )
            CalorieStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Restaurant,
                iconTint = ConsumedColor,
                label = "Consumed",
                value = homeData.caloriesConsumed,
                subtitle = "kcal from meals",
                onClick = onNavigateToMeals
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Activity details card ──────────────────────────────────────────
        ActivityDetailsCard(homeData = homeData)

        // ── Recent meals ────────────────────────────────────────────────────
        if (recentMeals.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            RecentMealsCard(
                meals   = recentMeals,
                onViewAll = onNavigateToMeals,
            )
        }

        Spacer(Modifier.height(16.dp))

        } // end inner scrollable Column

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = Color(0xFF1A1A1A),
            contentColor = Color(0xFF3DDB85),
        )
        } // end Box
    } // end outer Column
}

// ─────────────────────────────────────────────────────────────────────────────
// Date selector bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeDateSelectorBar(
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    context: Context,
) {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    val label = when (selectedDate) {
        today -> "Today · ${selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        today.minusDays(1) -> "Yesterday · ${selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        else -> selectedDate.format(formatter)
    }

    val barGreen = Color(0xFF3DDB85)

    Surface(color = Color(0xFF0A0A0A), elevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous day",
                    tint = barGreen,
                )
            }

            TextButton(
                onClick = {
                    val cal = Calendar.getInstance().apply {
                        set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                    }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            onPickDate(LocalDate.of(year, month + 1, day))
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH),
                    ).also { dialog ->
                        dialog.datePicker.maxDate = System.currentTimeMillis()
                        dialog.show()
                    }
                }
            ) {
                Text(
                    text = label,
                    color = barGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }

            IconButton(
                onClick = onNext,
                enabled = selectedDate.isBefore(today),
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next day",
                    tint = if (selectedDate.isBefore(today)) barGreen else barGreen.copy(alpha = 0.3f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Calorie summary card – stacked progress bars
// ─────────────────────────────────────────────────────────────────────────────

private val BarConsumedColor = Color(0xFF66BB6A) // green  – food consumed
private val BarBurnedColor   = Color(0xFFFF9800) // orange – calories burned
private val BarTrackColor    = Color(0xFF2A2A2A) // dark track for dark mode

@Composable
private fun CalorieRingCard(homeData: HomeData) {
    val consumed = homeData.caloriesConsumed
    val burned   = homeData.caloriesBurned
    val total    = consumed + burned
    val net      = homeData.netBalance  // consumed – burned

    // Fraction of the bar that is orange (burned), starting from left
    val burnedFraction = if (total > 0.0) (burned / total).toFloat().coerceIn(0f, 1f) else 0.5f

    val netColor = when {
        net > 50  -> Color(0xFFE53935)
        net < -50 -> Color(0xFF43A047)
        else      -> Color(0xFF546E7A)
    }
    val netLabel = when {
        net > 50  -> "Surplus"
        net < -50 -> "Deficit"
        else      -> "Balanced"
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        elevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // ── Title row ──────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "Calorie Balance",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colors.onSurface,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = netColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text       = "%s  %+.0f kcal".format(netLabel, net),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = netColor,
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Labels above bar ───────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.LocalFireDepartment,
                        contentDescription = "Burned",
                        tint               = BarBurnedColor,
                        modifier           = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text       = "Burned",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color      = BarBurnedColor,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = "Consumed",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color      = BarConsumedColor,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector        = Icons.Default.Restaurant,
                        contentDescription = "Consumed",
                        tint               = BarConsumedColor,
                        modifier           = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Tug-of-war bar ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(BarTrackColor)
            ) {
                // Burned portion – orange, from left
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(burnedFraction)
                        .clip(RoundedCornerShape(9.dp))
                        .background(BarBurnedColor)
                )
                // Consumed portion – green, from right
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(1f - burnedFraction)
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(9.dp))
                        .background(BarConsumedColor)
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── Values below bar ────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = "%.0f kcal".format(burned),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = BarBurnedColor,
                )
                Text(
                    text       = "%.0f kcal".format(consumed),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = BarConsumedColor,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recent meals card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentMealsCard(
    meals: List<MealEntry>,
    onViewAll: () -> Unit,
) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "Recent Meals",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colors.onSurface,
                )
                TextButton(onClick = onViewAll, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        text     = "See all",
                        fontSize = 12.sp,
                        color    = Color(0xFF3DDB85),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            meals.forEach { entry ->
                val product = entry.product
                val kcal    = product.nutriments?.energyKcalPkg

                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Icon circle
                    Box(
                        modifier         = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ConsumedColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint               = ConsumedColor,
                            modifier           = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    // Name + time
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = product.productName ?: product.brands ?: "Meal",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colors.onSurface,
                            maxLines   = 1,
                        )
                        Text(
                            text  = entry.timestamp.format(timeFormatter),
                            fontSize = 12.sp,
                            color = Color(0xFF9E9E9E),
                        )
                    }

                    // Calories
                    if (kcal != null) {
                        Text(
                            text       = "%.0f kcal".format(kcal),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = ConsumedColor,
                        )
                    }
                }

                if (entry != meals.last()) {
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.10f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual calorie stat card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalorieStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: Double,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(12.dp),
        elevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "%.0f".format(value),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = iconTint
            )
            Text(
                text = "kcal",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity details (steps + distance)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivityDetailsCard(homeData: HomeData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = "Steps",
                tint = BurnedColor,
                modifier = Modifier.size(32.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%,d".format(homeData.steps),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = BurnedColor
                )
                Text(
                    text = "Steps",
                    style = MaterialTheme.typography.caption
                )
            }
            Divider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.15f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.2f km".format(homeData.distanceMeters / 1000.0),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BurnedColor
                )
                Text(
                    text = "Distance",
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permissions banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionsBanner(onPermissionsLaunch: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Grant Health Connect permissions to see calories burned",
                style = MaterialTheme.typography.caption,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onPermissionsLaunch,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(text = "Grant", fontSize = 12.sp)
            }
        }
    }
}
