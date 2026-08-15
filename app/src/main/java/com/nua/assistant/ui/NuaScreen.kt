package com.nua.assistant.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.nua.assistant.ai.PlannedStep
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.memory.MessageRole
import com.nua.assistant.notifications.NotificationSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuaScreen(viewModel: NuaViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val notificationSummary by viewModel.notificationSummary.collectAsState()
    val facts by viewModel.facts.collectAsState()
    val pinnedLanguage by viewModel.pinnedLanguage.collectAsState()
    val briefingSchedule by viewModel.briefingSchedule.collectAsState()
    val voiceEnrolled by viewModel.voiceEnrolled.collectAsState()
    val enrollmentProgress by viewModel.enrollmentProgress.collectAsState()
    val geofences by viewModel.geofences.collectAsState()
    val usageThisMonth by viewModel.usageThisMonth.collectAsState()
    val trustScore by viewModel.trustScore.collectAsState()
    val trustLedger by viewModel.trustLedger.collectAsState()
    val actionOutcomes by viewModel.actionOutcomes.collectAsState()
    val autonomySuggestions by viewModel.autonomySuggestions.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val goalObservations by viewModel.goalObservations.collectAsState()
    val dreams by viewModel.dreams.collectAsState()
    val decisions by viewModel.decisions.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val secondBrainQuery by viewModel.secondBrainQuery.collectAsState()
    val secondBrainResults by viewModel.secondBrainResults.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showSecondBrainSearch by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }

    if (showSecondBrainSearch) {
        SecondBrainSearchScreen(
            query = secondBrainQuery,
            onQueryChange = viewModel::updateSecondBrainQuery,
            results = secondBrainResults,
            onBack = { showSecondBrainSearch = false },
        )
        return
    }

    if (showTimeline) {
        TimelineScreen(entries = timeline, onBack = { showTimeline = false })
        return
    }

    if (showSettings) {
        LaunchedEffect(Unit) {
            viewModel.refreshUsage()
            viewModel.refreshTrust()
        }
        SettingsScreen(
            facts = facts,
            onForgetFact = viewModel::forgetFact,
            onForgetFactsByType = viewModel::forgetFactsByType,
            pinnedLanguage = pinnedLanguage,
            onLanguageSelected = viewModel::setPinnedLanguage,
            briefingSchedule = briefingSchedule,
            onBriefingScheduleChanged = viewModel::setBriefingSchedule,
            voiceEnrolled = voiceEnrolled,
            enrollmentProgress = enrollmentProgress,
            onRecordEnrollmentClip = viewModel::recordVoiceEnrollmentClip,
            onResetVoiceEnrollment = viewModel::resetVoiceEnrollment,
            geofences = geofences,
            onAddGeofence = viewModel::addGeofence,
            onRemoveGeofence = viewModel::removeGeofence,
            usageThisMonth = usageThisMonth,
            trustScore = trustScore,
            trustLedger = trustLedger,
            actionOutcomes = actionOutcomes,
            autonomySuggestions = autonomySuggestions,
            onEnableAutoApprove = viewModel::enableAutoApprove,
            goals = goals,
            goalObservations = goalObservations,
            onAddGoal = viewModel::addGoal,
            onRemoveGoal = viewModel::removeGoal,
            dreams = dreams,
            decisions = decisions,
            onAddDecision = viewModel::addDecision,
            onRecordDecisionOutcome = viewModel::recordDecisionOutcome,
            onRemoveDecision = viewModel::removeDecision,
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
                    IconButton(onClick = viewModel::whatShouldIDoNow, enabled = !uiState.isProcessing) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = "What should I do now?")
                    }
                    IconButton(onClick = { showSecondBrainSearch = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search your Second Brain")
                    }
                    IconButton(onClick = { showTimeline = true }) {
                        Icon(Icons.Filled.History, contentDescription = "Timeline")
                    }
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
                onImageCaptured = viewModel::describeImage,
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

    uiState.pendingReply?.let { pending ->
        ReplyConfirmationDialog(
            pending = pending,
            onConfirm = viewModel::confirmPendingReply,
            onDismiss = viewModel::dismissPendingReply,
        )
    }
}

@Composable
private fun ChatColumn(
    uiState: NuaUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicTap: () -> Unit,
    onImageCaptured: (Uri) -> Unit,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingImageUri?.let(onImageCaptured)
    }
    LaunchedEffect(uiState.messages.size, uiState.streamingReply) {
        val lastIndex = uiState.messages.size + (if (uiState.streamingReply != null) 1 else 0) - 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.messages) { message -> MessageBubble(message) }
            val streaming = uiState.streamingReply
            if (streaming != null) {
                item { MessageBubble(ChatMessage(MessageRole.ASSISTANT, streaming)) }
            } else if (uiState.isProcessing) {
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
            IconButton(onClick = {
                val uri = createImageCaptureUri(context)
                pendingImageUri = uri
                cameraLauncher.launch(uri)
            }) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Ask about a photo")
            }
            IconButton(onClick = onMicTap) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice input")
            }
            IconButton(onClick = onSend, enabled = uiState.inputText.isNotBlank() && !uiState.isProcessing) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

/** cacheDir/images/<timestamp>.jpg, exposed via FileProvider so the camera app can write to it. */
private fun createImageCaptureUri(context: Context): Uri {
    val imagesDir = java.io.File(context.cacheDir, "images").apply { mkdirs() }
    val file = java.io.File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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
private fun ReplyConfirmationDialog(pending: NuaRouteResult.ReplyProposed, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reply to ${pending.notification.title}?") },
        text = { Text("\"${pending.message}\"") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
