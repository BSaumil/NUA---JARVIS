package com.nua.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nua.assistant.ai.PlannedStep
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.memory.MessageRole
import com.nua.assistant.notifications.NotificationSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuaScreen(viewModel: NuaViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val notificationSummary by viewModel.notificationSummary.collectAsState()
    val facts by viewModel.facts.collectAsState()
    val pinnedLanguage by viewModel.pinnedLanguage.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            facts = facts,
            onForgetFact = viewModel::forgetFact,
            pinnedLanguage = pinnedLanguage,
            onLanguageSelected = viewModel::setPinnedLanguage,
            onBack = { showSettings = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NUA") },
                actions = {
                    NotificationSummaryChip(notificationSummary)
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            ChatColumn(
                uiState = uiState,
                onInputChanged = viewModel::onInputChanged,
                onSend = { viewModel.sendMessage() },
                onMicTap = viewModel::onWakeWordDetected,
            )
        }
    }

    if (uiState.needsApiKey) {
        ApiKeyDialog(onSave = viewModel::saveApiKey)
    }

    uiState.pendingPlan?.let { plan ->
        PlanConfirmationDialog(
            plan = plan,
            onConfirm = viewModel::confirmPendingPlan,
            onDismiss = viewModel::dismissPendingPlan,
        )
    }
}

@Composable
private fun ChatColumn(
    uiState: NuaUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicTap: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.messages) { message -> MessageBubble(message) }
            if (uiState.isProcessing) {
                item { CircularProgressIndicator(modifier = Modifier.padding(8.dp)) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask NUA anything...") },
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onMicTap) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice input")
            }
            IconButton(onClick = onSend, enabled = uiState.inputText.isNotBlank() && !uiState.isProcessing) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(text = message.content, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun NotificationSummaryChip(summary: NotificationSummary) {
    if (summary.needsAttention.isEmpty() && summary.canWait.isEmpty()) return
    AssistChip(onClick = {}, label = { Text(summary.spokenSummary) })
}

@Composable
private fun ApiKeyDialog(onSave: (String) -> Unit) {
    var apiKey by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { /* required before NUA can talk to Claude at all */ },
        title = { Text("Add your Claude API key") },
        text = {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = { Text("sk-ant-...") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (apiKey.isNotBlank()) onSave(apiKey) }, enabled = apiKey.isNotBlank()) {
                Text("Save")
            }
        },
    )
}

@Composable
private fun PlanConfirmationDialog(plan: TaskPlan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plan.summary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.steps.forEach { step -> PlanStepRow(step) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm plan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun PlanStepRow(step: PlannedStep) {
    Column {
        Text(text = step.title, style = MaterialTheme.typography.titleSmall)
        Text(text = step.detail, style = MaterialTheme.typography.bodySmall)
        if (step.suggestedReminder != null) {
            Text(
                text = "Reminder: ${step.suggestedReminder.title}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
