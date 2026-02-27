package com.example.healthconnectsample.presentation.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.healthconnectsample.R

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(saveMessage) {
        saveMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearSaveMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(id = R.string.profile_screen),
            style = androidx.compose.material.MaterialTheme.typography.h4
        )

        OutlinedTextField(
            value = uiState.name,
            onValueChange = { viewModel.updateName(it) },
            label = { Text(stringResource(id = R.string.profile_name)) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.age,
            onValueChange = { viewModel.updateAge(it) },
            label = { Text(stringResource(id = R.string.profile_age)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.height,
                onValueChange = { viewModel.updateHeight(it) },
                label = { Text("Height") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )

            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material.RadioButton(
                    selected = uiState.heightUnit == "cm",
                    onClick = { viewModel.updateHeightUnit("cm") }
                )
                Text("cm")
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material.RadioButton(
                    selected = uiState.heightUnit == "ft",
                    onClick = { viewModel.updateHeightUnit("ft") }
                )
                Text("ft")
            }
        }

        OutlinedTextField(
            value = uiState.weight,
            onValueChange = { viewModel.updateWeight(it) },
            label = { Text(stringResource(id = R.string.profile_weight)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.bmi.isNotEmpty()) {
            Text(
                text = "BMI: ${uiState.bmi}",
                style = androidx.compose.material.MaterialTheme.typography.h6,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        OutlinedTextField(
            value = uiState.country,
            onValueChange = { viewModel.updateCountry(it) },
            label = { Text(stringResource(id = R.string.profile_country)) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.goal,
            onValueChange = { viewModel.updateGoal(it) },
            label = { Text(stringResource(id = R.string.profile_goal)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(
            onClick = { viewModel.saveProfile() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id = R.string.profile_save))
        }
    }
}
