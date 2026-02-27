/*
 * Chat screen composable for interacting with the Pulse Backend.
 * Displays conversation messages, input field, and backend status.
 */
package com.example.healthconnectsample.presentation.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun ChatScreen(
    viewModel: ChatViewModel
) {
    val messages by viewModel.messages
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage
    val isBackendReachable by viewModel.isBackendReachable
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Backend status bar
        BackendStatusBar(
            isReachable = isBackendReachable,
            onRetry = { viewModel.checkBackendHealth() },
            onClear = { viewModel.clearConversation() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Error message
        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f)
            ) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyStateMessage()
                }
            }
            items(messages) { message ->
                ChatBubble(message = message)
            }
            if (isLoading) {
                item {
                    LoadingIndicator()
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about your health...") },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !isLoading) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            keyboardController?.hide()
                        }
                    }
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isLoading) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                        keyboardController?.hide()
                    }
                },
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank() && !isLoading)
                        MaterialTheme.colors.primary
                    else
                        Color.Gray
                )
            }
        }
    }
}

@Composable
fun BackendStatusBar(
    isReachable: Boolean?,
    onRetry: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when (isReachable) {
                    true -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                    false -> Color(0xFFF44336).copy(alpha = 0.1f)
                    null -> Color.Gray.copy(alpha = 0.1f)
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    when (isReachable) {
                        true -> Color(0xFF4CAF50)
                        false -> Color(0xFFF44336)
                        null -> Color.Gray
                    }
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = when (isReachable) {
                true -> "Pulse Backend Connected"
                false -> "Backend Unreachable"
                null -> "Checking backend..."
            },
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Retry",
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Clear conversation",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = if (isUser) "You" else "Pulse AI",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp
            ),
            backgroundColor = if (isUser)
                MaterialTheme.colors.primary.copy(alpha = 0.15f)
            else
                MaterialTheme.colors.surface,
            elevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        text = message.content,
                        fontSize = 14.sp
                    )
                } else {
                    MarkdownText(
                        markdown = message.content,
                        fontSize = 14.sp
                    )
                }
                // Show tool calls if any
                if (message.toolCalls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tools used:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    message.toolCalls.forEach { tc ->
                        Text(
                            text = "• ${tc.name}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "💬",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Health Copilot",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ask me about your health data!\nI can analyze your steps, sleep, heart rate, exercise, and more.",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Try: \"How are my steps looking?\"",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.primary
        )
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Analyzing your health data...",
            fontSize = 13.sp,
            color = Color.Gray
        )
    }
}
