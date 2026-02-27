/*
 * ViewModel for the Chat screen.
 * Manages conversation state, collects health data from Health Connect,
 * and sends requests to the Pulse Backend API.
 */
package com.example.healthconnectsample.presentation.screen.chat

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.healthconnectsample.data.HealthConnectManager
import com.example.healthconnectsample.data.ProfileData
import com.example.healthconnectsample.data.ProfileRepository
import com.example.healthconnectsample.data.api.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: String,   // "user" or "assistant"
    val content: String,
    val toolCalls: List<ToolCallResponse> = emptyList()
)

class ChatViewModel(
    private val healthConnectManager: HealthConnectManager,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val messages: MutableState<List<ChatMessage>> = mutableStateOf(emptyList())
    val isLoading: MutableState<Boolean> = mutableStateOf(false)
    val errorMessage: MutableState<String?> = mutableStateOf(null)
    val isBackendReachable: MutableState<Boolean?> = mutableStateOf(null)

    init {
        checkBackendHealth()
    }

    /**
     * Check if the backend is reachable.
     */
    fun checkBackendHealth() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.healthCheck()
                isBackendReachable.value = response.isSuccessful
            } catch (e: Exception) {
                isBackendReachable.value = false
            }
        }
    }

    /**
     * Send a message to the backend with health data.
     */
    fun sendMessage(query: String) {
        if (query.isBlank()) return

        // Add user message to the list
        val userMessage = ChatMessage(role = "user", content = query)
        messages.value = messages.value + userMessage
        isLoading.value = true
        errorMessage.value = null

        viewModelScope.launch {
            try {
                // 1. Get user profile from repository
                val profileData = profileRepository.getProfile()
                val userProfilePayload = mapProfileToPayload(profileData)

                // 2. Collect health data from Health Connect
                val healthData = try {
                    healthConnectManager.collectHealthDataForBackend(userProfilePayload)
                } catch (e: Exception) {
                    // If health data collection fails, send with minimal data
                    HealthDataPayload(userData = userProfilePayload)
                }

                // 3. Build conversation history from previous messages
                val conversationHistory = messages.value
                    .dropLast(1) // exclude the message we just added
                    .map { msg ->
                        ConversationMessagePayload(
                            role = msg.role,
                            content = msg.content
                        )
                    }

                // 4. Create API request
                val chatRequest = ChatRequest(
                    query = query,
                    healthData = healthData,
                    conversationHistory = conversationHistory
                )

                // 5. Call the backend
                val response = RetrofitClient.apiService.chat(chatRequest)

                if (response.isSuccessful) {
                    val chatResponse = response.body()!!
                    val assistantMessage = ChatMessage(
                        role = "assistant",
                        content = chatResponse.response,
                        toolCalls = chatResponse.toolCalls
                    )
                    messages.value = messages.value + assistantMessage
                } else {
                    errorMessage.value = "Backend error: ${response.code()} - ${response.errorBody()?.string()}"
                }

            } catch (e: Exception) {
                errorMessage.value = "Connection error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun mapProfileToPayload(profile: ProfileData): UserProfilePayload {
        val heightCm = profile.height.toDoubleOrNull()?.let { h ->
            if (profile.heightUnit == "ft") h * 30.48 else h
        }

        return UserProfilePayload(
            name = profile.name.takeIf { it.isNotBlank() } ?: "User",
            age = profile.age.toIntOrNull(),
            weightKg = profile.weight.toDoubleOrNull(),
            heightCm = heightCm,
            location = if (profile.country.isNotBlank()) mapOf("country" to profile.country) else null,
            goals = if (profile.goal.isNotBlank()) mapOf("primary_goal" to profile.goal) else null
        )
    }

    /**
     * Clear the conversation history.
     */
    fun clearConversation() {
        messages.value = emptyList()
        errorMessage.value = null
    }

    class Factory(
        private val healthConnectManager: HealthConnectManager,
        private val profileRepository: ProfileRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(healthConnectManager, profileRepository) as T
        }
    }
}
