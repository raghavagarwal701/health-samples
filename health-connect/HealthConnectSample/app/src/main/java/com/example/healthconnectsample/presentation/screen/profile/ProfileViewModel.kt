package com.example.healthconnectsample.presentation.screen.profile

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.healthconnectsample.data.ProfileData
import com.example.healthconnectsample.data.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val profile = repository.getProfile()
        _uiState.update {
            it.copy(
                name = profile.name,
                age = profile.age,
                weight = profile.weight,
                height = profile.height,
                heightUnit = profile.heightUnit,
                country = profile.country,
                goal = profile.goal,
                bmi = calculateBmi(profile.height, profile.weight, profile.heightUnit)
            )
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun updateAge(age: String) {
        _uiState.update { it.copy(age = age) }
    }

    fun updateWeight(weight: String) {
        _uiState.update { 
            it.copy(
                weight = weight,
                bmi = calculateBmi(it.height, weight, it.heightUnit)
            ) 
        }
    }

    fun updateHeight(height: String) {
        _uiState.update { 
            it.copy(
                height = height,
                bmi = calculateBmi(height, it.weight, it.heightUnit)
            ) 
        }
    }

    fun updateHeightUnit(unit: String) {
        _uiState.update { 
            it.copy(
                heightUnit = unit,
                bmi = calculateBmi(it.height, it.weight, unit)
            ) 
        }
    }

    fun updateCountry(country: String) {
        _uiState.update { it.copy(country = country) }
    }

    fun updateGoal(goal: String) {
        _uiState.update { it.copy(goal = goal) }
    }

    fun saveProfile() {
        val state = _uiState.value
        repository.saveProfile(
            ProfileData(
                name = state.name,
                age = state.age,
                weight = state.weight,
                height = state.height,
                heightUnit = state.heightUnit,
                country = state.country,
                goal = state.goal
            )
        )
        _saveMessage.value = "Profile saved successfully"
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }

    private fun calculateBmi(height: String, weight: String, unit: String): String {
        val h = height.toDoubleOrNull()
        val w = weight.toDoubleOrNull()
        
        if (h == null || w == null || h <= 0 || w <= 0) return ""

        val heightInMeters = if (unit == "ft") {
             h * 0.3048
        } else {
             h / 100.0
        }

        val bmi = w / (heightInMeters * heightInMeters)
        return String.format("%.1f", bmi)
    }
}

data class ProfileUiState(
    val name: String = "",
    val age: String = "",
    val weight: String = "",
    val height: String = "",
    val heightUnit: String = "cm",
    val country: String = "",
    val goal: String = "",
    val bmi: String = ""
)

class ProfileViewModelFactory(
    private val repository: ProfileRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
