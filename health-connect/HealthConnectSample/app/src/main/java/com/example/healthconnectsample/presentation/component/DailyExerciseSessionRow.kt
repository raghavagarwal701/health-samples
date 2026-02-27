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
package com.example.healthconnectsample.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.healthconnectsample.R
import com.example.healthconnectsample.data.ExerciseSession
import com.example.healthconnectsample.data.getExerciseName
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Creates a row to represent a day of exercise sessions.
 */
@Composable
fun DailyExerciseSessionRow(
    date: LocalDate,
    sessions: List<ExerciseSession>,
    onDeleteClick: (String) -> Unit,
    onDetailsClick: (String) -> Unit,
    startExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(startExpanded) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val formatter = DateTimeFormatter.ofPattern("EEE, d LLL")
            Text(
                text = date.format(formatter),
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.h6
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${sessions.size} sessions",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                if (sessions.isEmpty()) {
                    Text(
                        text = "No exercises recorded",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.body2,
                         color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    sessions.forEach { session ->
                         val appInfo = session.sourceAppInfo
                         ExerciseSessionRow(
                            start = session.startTime,
                            end = session.endTime,
                            uid = session.id,
                            name = session.title ?: getExerciseName(session.exerciseType),
                            sourceAppName = appInfo?.appLabel ?: stringResource(R.string.unknown_app),
                            sourceAppIcon = appInfo?.icon,
                            onDeleteClick = onDeleteClick,
                            onDetailsClick = onDetailsClick,
                            exerciseType = session.exerciseType
                        )
                    }
                }
            }
        }
    }
}

