package com.example.healthconnectsample.presentation.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val BurnedColor = Color(0xFF2E7D32)      // dark green – activity
private val ConsumedColor = Color(0xFFE65100)    // deep orange – food
private val SurplusColor = Color(0xFFC62828)     // red – ate more than burned
private val DeficitColor = Color(0xFF1565C0)     // blue – burned more than ate
private val NeutralColor = Color(0xFF37474F)     // blue-grey

@Composable
fun HomeScreen(
    permissions: Set<String>,
    permissionsGranted: Boolean,
    homeData: HomeData,
    uiState: HomeViewModel.UiState,
    onError: (Throwable?) -> Unit = {},
    onPermissionsResult: () -> Unit = {},
    onPermissionsLaunch: (Set<String>) -> Unit = {},
    onNavigateToMeals: () -> Unit = {},
    onNavigateToSteps: () -> Unit = {},
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Text(
            text = "Today's Summary",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )
        Text(
            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        if (!permissionsGranted) {
            PermissionsBanner(onPermissionsLaunch = { onPermissionsLaunch(permissions) })
            Spacer(Modifier.height(12.dp))
        }

        // ── Net Balance Card ───────────────────────────────────────────────
        NetBalanceCard(homeData = homeData)

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

        Spacer(Modifier.height(16.dp))

        // ── Calorie bar ────────────────────────────────────────────────────
        if (homeData.caloriesBurned > 0 || homeData.caloriesConsumed > 0) {
            CalorieBalanceBar(homeData = homeData)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Net balance card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NetBalanceCard(homeData: HomeData) {
    val net = homeData.netBalance
    val (bgColor, statusText, statusDetail) = when {
        net > 50 -> Triple(
            SurplusColor,
            "Calorie Surplus",
            "You've eaten %.0f kcal more than you've burned".format(net)
        )
        net < -50 -> Triple(
            DeficitColor,
            "Calorie Deficit",
            "You've burned %.0f kcal more than you've eaten".format(-net)
        )
        else -> Triple(
            NeutralColor,
            "Balanced",
            "Your intake and expenditure are nearly equal"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = 6.dp,
        backgroundColor = bgColor
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusDetail,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Net: %+.0f kcal".format(net),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
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
// Visual calorie balance bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalorieBalanceBar(homeData: HomeData) {
    val total = homeData.caloriesBurned + homeData.caloriesConsumed
    if (total == 0.0) return
    val burnedFraction = (homeData.caloriesBurned / total).coerceIn(0.0, 1.0).toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Calorie Balance",
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔥 Burned",
                    style = MaterialTheme.typography.caption,
                    color = BurnedColor
                )
                Text(
                    text = "🍽 Consumed",
                    style = MaterialTheme.typography.caption,
                    color = ConsumedColor
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to BurnedColor,
                                burnedFraction to BurnedColor,
                                burnedFraction to ConsumedColor,
                                1f to ConsumedColor
                            )
                        )
                    )
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "%.0f kcal".format(homeData.caloriesBurned),
                    style = MaterialTheme.typography.caption,
                    color = BurnedColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "%.0f kcal".format(homeData.caloriesConsumed),
                    style = MaterialTheme.typography.caption,
                    color = ConsumedColor,
                    fontWeight = FontWeight.Medium
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
