package com.example.healthconnectsample.presentation.screen.steps

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthconnectsample.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.UUID

private val StepsColor    = Color(0xFF1976D2)
private val DistColor     = Color(0xFF388E3C)
private val CalColor      = Color(0xFFE64A19)
private val SleepColor    = Color(0xFF7B1FA2)
private val HrColor       = Color(0xFFC62828)
private val ExerciseColor = Color(0xFF00838F)

@Composable
fun StepsScreen(
    permissions: Set<String>,
    permissionsGranted: Boolean,
    todayData: StepsData,
    weeklyData: List<StepsData>,
    uiState: StepsViewModel.UiState,
    selectedDate: LocalDate = LocalDate.now(),
    dailyActivity: List<DailyActivityData> = emptyList(),
    onPreviousDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    onPickDate: (LocalDate) -> Unit = {},
    onError: (Throwable?) -> Unit = {},
    onPermissionsResult: () -> Unit = {},
    onPermissionsLaunch: (Set<String>) -> Unit = {},
) {
    val errorId = rememberSaveable { mutableStateOf(UUID.randomUUID()) }

    LaunchedEffect(uiState) {
        if (uiState is StepsViewModel.UiState.Uninitialized) onPermissionsResult()
        if (uiState is StepsViewModel.UiState.Error && errorId.value != uiState.uuid) {
            onError(uiState.exception)
            errorId.value = uiState.uuid
        }
    }

    if (uiState == StepsViewModel.UiState.Uninitialized) return

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Date selector bar ────────────────────────────────────────────────
        ActivityDateSelectorBar(
            selectedDate = selectedDate,
            onPrevious = onPreviousDay,
            onNext = onNextDay,
            onPickDate = onPickDate,
            context = context,
        )

        if (!permissionsGranted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { onPermissionsLaunch(permissions) }) {
                    Text(stringResource(R.string.permissions_button_label))
                }
            }
            return@Column
        }

        // Find data for the selected date, falling back to synthesised legacy data for today
        val selectedData: DailyActivityData? = dailyActivity.find { it.date == selectedDate }
            ?: if (selectedDate == LocalDate.now() && dailyActivity.isEmpty()) {
                DailyActivityData(
                    date = LocalDate.now(),
                    steps = todayData.steps,
                    distanceMeters = todayData.distanceMeters,
                    caloriesBurned = todayData.calories,
                )
            } else null

        if (selectedData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏃", fontSize = 52.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No activity data for this day",
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        } else {
            ActivityDayDetail(data = selectedData)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date selector bar (mirrors MealsScreen's DateSelectorBar)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivityDateSelectorBar(
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

    Surface(color = MaterialTheme.colors.surface, elevation = 4.dp) {
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
                    tint = MaterialTheme.colors.primary,
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
                    color = MaterialTheme.colors.primary,
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
                    tint = if (selectedDate.isBefore(today)) MaterialTheme.colors.primary else MaterialTheme.colors.primary.copy(alpha = 0.3f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Full-day detail view (always expanded)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivityDayDetail(data: DailyActivityData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Metrics summary card ─────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Daily Activity",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MetricChip(
                            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                            color = StepsColor,
                            value = "%,d".format(data.steps),
                            label = "Steps",
                        )
                        MetricChip(
                            icon = Icons.Default.Map,
                            color = DistColor,
                            value = "%.2f km".format(data.distanceMeters / 1000),
                            label = "Distance",
                        )
                        MetricChip(
                            icon = Icons.Default.LocalFireDepartment,
                            color = CalColor,
                            value = "%.0f kcal".format(data.caloriesBurned),
                            label = "Burned",
                        )
                    }
                }
            }
        }

        // ── Sleep ────────────────────────────────────────────────────────
        if (data.sleepMinutes != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    elevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(SleepColor.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "Sleep",
                                tint = SleepColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Sleep",
                                style = MaterialTheme.typography.subtitle2,
                                fontWeight = FontWeight.Bold,
                                color = SleepColor,
                            )
                            Text(
                                formatSleep(data.sleepMinutes),
                                style = MaterialTheme.typography.body1,
                            )
                        }
                    }
                }
            }
        }

        // ── Heart rate ───────────────────────────────────────────────────
        if (data.heartRateAvg != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    elevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(HrColor.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Heart rate",
                                tint = HrColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Heart Rate",
                                style = MaterialTheme.typography.subtitle2,
                                fontWeight = FontWeight.Bold,
                                color = HrColor,
                            )
                            val hrText = "avg ${data.heartRateAvg} bpm" +
                                if (data.heartRateMin != null && data.heartRateMax != null)
                                    "  (${data.heartRateMin}–${data.heartRateMax})"
                                else ""
                            Text(hrText, style = MaterialTheme.typography.body1)
                        }
                    }
                }
            }
        }

        // ── Exercises ────────────────────────────────────────────────────
        if (data.exercises.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    elevation = 3.dp,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FitnessCenter,
                                contentDescription = "Exercises",
                                tint = ExerciseColor,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Exercises",
                                style = MaterialTheme.typography.subtitle2,
                                fontWeight = FontWeight.Bold,
                                color = ExerciseColor,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        data.exercises.forEach { ex ->
                            ExerciseRow(ex)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetricChip(icon: ImageVector, color: Color, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SectionRow(icon: ImageVector, color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.body2,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface
        )
    }
}

@Composable
private fun ExerciseRow(ex: ExerciseSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = ExerciseColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = ex.title ?: exerciseTypeName(ex.type),
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${ex.durationMinutes} min",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
    }
}

private fun formatSleep(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun exerciseTypeName(type: Int): String = when (type) {
    0  -> "Other"
    2  -> "Badminton"
    4  -> "Baseball"
    5  -> "Basketball"
    8  -> "Biking"
    9  -> "Biking (stationary)"
    10 -> "Boot camp"
    11 -> "Boxing"
    13 -> "Cricket"
    14 -> "Cross country skiing"
    15 -> "Cross training"
    16 -> "Curling"
    17 -> "Dancing"
    19 -> "Elliptical"
    20 -> "Fencing"
    21 -> "Football (American)"
    22 -> "Football (Australian)"
    23 -> "Football (soccer)"
    24 -> "Frisbee"
    25 -> "Golf"
    26 -> "Guided breathing"
    27 -> "Gymnastics"
    28 -> "Handball"
    29 -> "High intensity interval training"
    30 -> "Hiking"
    31 -> "Ice hockey"
    32 -> "Ice skating"
    33 -> "Martial arts"
    34 -> "Paddle sports"
    35 -> "Paragliding"
    36 -> "Pilates"
    37 -> "Racquetball"
    38 -> "Rock climbing"
    39 -> "Roller hockey"
    40 -> "Rowing"
    41 -> "Rowing (machine)"
    42 -> "Rugby"
    43 -> "Running"
    44 -> "Running (treadmill)"
    45 -> "Sailing"
    46 -> "Scuba diving"
    47 -> "Skating"
    48 -> "Skiing"
    49 -> "Snowboarding"
    50 -> "Snowshoeing"
    51 -> "Soccer"
    52 -> "Softball"
    53 -> "Squash"
    54 -> "Stair climbing"
    55 -> "Stair climbing (machine)"
    56 -> "Strength training"
    57 -> "Stretching"
    58 -> "Surfing"
    59 -> "Swimming"
    60 -> "Swimming (open water)"
    61 -> "Table tennis"
    62 -> "Tennis"
    63 -> "Volleyball"
    64 -> "Walking"
    65 -> "Water polo"
    66 -> "Weightlifting"
    67 -> "Wheelchair"
    68 -> "Yoga"
    else -> "Exercise #$type"
}

// ─────────────────────────────────────────────────────────────────────────────
// Legacy composables kept so that any other callers still compile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StepsSummaryCard(data: StepsData) {
    Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${data.steps}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
            Text("Steps", style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun DailyStepsRow(data: StepsData) {
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    Card(elevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(data.date.format(formatter), style = MaterialTheme.typography.body1, fontWeight = FontWeight.Medium)
            Column(horizontalAlignment = Alignment.End) {
                Text("${data.steps} steps", style = MaterialTheme.typography.body2, fontWeight = FontWeight.Bold)
                Text("%.1f km, %.0f cal".format(data.distanceMeters / 1000, data.calories), style = MaterialTheme.typography.caption)
            }
        }
    }
}
