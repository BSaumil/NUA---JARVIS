package com.nua.assistant.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.nua.assistant.ai.PlannedStep
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.automation.displayNameFor
import com.nua.assistant.memory.MessageRole
import com.nua.assistant.security.BiometricGate
import com.nua.assistant.ui.components.NuaBottomBar
import com.nua.assistant.ui.nav.MemorySection
import com.nua.assistant.ui.nav.NuaDestination
import com.nua.assistant.ui.palette.CommandPaletteSheet
import com.nua.assistant.ui.palette.PaletteAction
import com.nua.assistant.ui.palette.buildPalette
import com.nua.assistant.ui.theme.NuaTheme
import com.nua.assistant.ui.theme.rememberCommitHaptic
import com.nua.assistant.security.requiresStepUpAuth
import com.nua.assistant.trust.AutonomyTier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuaScreen(viewModel: NuaViewModel) {
    val uiState by viewModel.uiState.collectAsState()
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
    val visionMonitors by viewModel.visionMonitors.collectAsState()
    val lastVisionResult by viewModel.lastVisionResult.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val testingApiConnection by viewModel.testingApiConnection.collectAsState()
    val nuaState by viewModel.nuaState.collectAsState()
    val latestInsight by viewModel.latestInsight.collectAsState()
    val nextBestAction by viewModel.nextBestAction.collectAsState()
    val skills = viewModel.skills
    val paletteQuery by viewModel.paletteQuery.collectAsState()
    val paletteMemories by viewModel.paletteMemories.collectAsState()
    var showPalette by remember { mutableStateOf(false) }
    // One destination enum replaces the pile of boolean show* flags this screen used to
    // navigate with — each new screen used to mean another flag and another early return,
    // and the user got no sense of where they were.
    var destination by remember { mutableStateOf(NuaDestination.HOME) }
    var memorySection by remember { mutableStateOf(MemorySection.SEARCH) }

    LaunchedEffect(destination) {
        when (destination) {
            NuaDestination.HOME -> viewModel.refreshNuaState()
            NuaDestination.ACT -> viewModel.refreshAct()
            NuaDestination.YOU -> {
                viewModel.refreshUsage()
                viewModel.refreshTrust()
                viewModel.refreshDiagnostics()
            }
            else -> Unit
        }
    }

    val state = nuaState
    // A dot on Home when something genuinely needs the user, and on Act when NUA has
    // failed at something recently — earned from real state, never decorative.
    val alerts = buildSet {
        if (state?.pendingTasks?.isNotEmpty() == true) add(NuaDestination.HOME)
        if (state?.recentMistakes?.isNotEmpty() == true) add(NuaDestination.ACT)
    }

    Scaffold(
        bottomBar = { NuaBottomBar(current = destination, onSelect = { destination = it }, alertOn = alerts) },
        floatingActionButton = {
            // The palette is reachable from every destination — that's what makes it a
            // palette rather than another screen you have to navigate to first.
            FloatingActionButton(
                onClick = { showPalette = true },
                containerColor = NuaTheme.colors.surfaceElevated,
                contentColor = NuaTheme.colors.brandIdentity,
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Open command palette")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (destination) {
                NuaDestination.HOME -> if (state == null) {
                    // Genuinely still loading — deliberately not fake zeroes.
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                } else {
                    CommandCentreScreen(
                        greeting = greetingFor(state),
                        state = state,
                        orbState = viewModel.currentOrbState(),
                        insight = latestInsight,
                        nextAction = nextBestAction,
                        onOrbTap = viewModel::onWakeWordDetected,
                        onOpenChat = { destination = NuaDestination.ASK },
                        onAskWhatNow = {
                            viewModel.whatShouldIDoNow()
                            destination = NuaDestination.ASK
                        },
                    )
                }

                NuaDestination.ASK -> ChatColumn(
                    uiState = uiState,
                    onInputChanged = viewModel::onInputChanged,
                    onSend = { viewModel.sendMessage() },
                    onMicTap = viewModel::onWakeWordDetected,
                    onImageCaptured = viewModel::describeImage,
                    lastVisionResult = lastVisionResult,
                    onRememberVision = viewModel::rememberLastVisionResult,
                    onMonitorVision = viewModel::startMonitoringLastVisionResult,
                    onDismissVision = viewModel::dismissLastVisionResult,
                )

                NuaDestination.MEMORY -> MemoryScreen(
                    section = memorySection,
                    onSectionChange = { memorySection = it },
                    searchQuery = secondBrainQuery,
                    onSearchQueryChange = viewModel::updateSecondBrainQuery,
                    searchResults = secondBrainResults,
                    timeline = timeline,
                    documents = documents,
                    onIngestDocument = viewModel::ingestDocument,
                    onAskDocuments = viewModel::askAboutDocuments,
                    onRemoveDocument = viewModel::removeDocument,
                )

                NuaDestination.ACT -> ActScreen(
                    skills = skills,
                    pendingTasks = state?.pendingTasks.orEmpty(),
                    recentActions = actionOutcomes,
                )

                NuaDestination.YOU -> SettingsScreen(
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
                    visionMonitors = visionMonitors,
                    onRecheckVisionMonitor = viewModel::recheckVisionMonitor,
                    onRemoveVisionMonitor = viewModel::removeVisionMonitor,
                    diagnostics = diagnostics,
                    testingApiConnection = testingApiConnection,
                    onRefreshDiagnostics = viewModel::refreshDiagnostics,
                    onTestApiConnection = viewModel::testApiConnection,
                    onBack = null,
                )
            }
        }
    }

    if (uiState.needsApiKey) {
        ApiKeyDialog(onSave = viewModel::saveApiKey)
    }

    if (showPalette) {
        CommandPaletteSheet(
            query = paletteQuery,
            onQueryChange = viewModel::updatePaletteQuery,
            entries = buildPalette(paletteQuery, viewModel.paletteSkills, paletteMemories),
            onChoose = { entry ->
                showPalette = false
                viewModel.updatePaletteQuery("")
                when (val chosen = entry.action) {
                    is PaletteAction.Navigate -> destination = chosen.destination
                    is PaletteAction.OpenMemory -> {
                        viewModel.updateSecondBrainQuery(chosen.query)
                        memorySection = MemorySection.SEARCH
                        destination = NuaDestination.MEMORY
                    }
                    // Both of these go through sendMessage, so routing, the autonomy
                    // tier, and the confirmation dialogs all still apply — the palette
                    // never becomes a second, less-guarded way to run an action.
                    is PaletteAction.RunSkill -> {
                        // The plain capability name, not the decorated row label — the
                        // classifier should see a clean utterance. A gated skill with no
                        // details yet ("Send a text") correctly falls through to chat so
                        // NUA can ask who and what, rather than half-firing.
                        viewModel.sendMessage(displayNameFor(chosen.action))
                        destination = NuaDestination.ASK
                    }
                    is PaletteAction.AskNua -> {
                        viewModel.sendMessage(chosen.utterance)
                        destination = NuaDestination.ASK
                    }
                }
            },
            onDismiss = { showPalette = false },
        )
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

    uiState.pendingSms?.let { pending ->
        SmsConfirmationDialog(
            pending = pending,
            onConfirm = viewModel::confirmPendingSms,
            onDismiss = viewModel::dismissPendingSms,
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
    lastVisionResult: LastVisionResult?,
    onRememberVision: () -> Unit,
    onMonitorVision: (subject: String, intervalDays: Int) -> Unit,
    onDismissVision: () -> Unit,
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

        lastVisionResult?.let { result ->
            VisionResultActionsRow(
                result = result,
                onRemember = onRememberVision,
                onMonitor = onMonitorVision,
                onDismiss = onDismissVision,
            )
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
internal fun createImageCaptureUri(context: Context): Uri {
    val imagesDir = java.io.File(context.cacheDir, "images").apply { mkdirs() }
    val file = java.io.File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** The "remember" / "monitor" / dismiss affordances offered after NUA understands a photo — deliberate actions, not automatic. */
@Composable
private fun VisionResultActionsRow(
    result: LastVisionResult,
    onRemember: () -> Unit,
    onMonitor: (subject: String, intervalDays: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var showMonitorDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = result.analysis.category.name.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemember) { Text("Remember") }
            TextButton(onClick = { showMonitorDialog = true }) { Text("Monitor") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }

    if (showMonitorDialog) {
        MonitorVisionDialog(
            onConfirm = { subject, intervalDays ->
                onMonitor(subject, intervalDays)
                showMonitorDialog = false
            },
            onDismiss = { showMonitorDialog = false },
        )
    }
}

@Composable
private fun MonitorVisionDialog(onConfirm: (subject: String, intervalDays: Int) -> Unit, onDismiss: () -> Unit) {
    var subject by remember { mutableStateOf("") }
    var intervalDays by remember { mutableStateOf(7) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monitor this") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "NUA can't take photos on its own, so this reminds you to snap a fresh one on schedule and reports what's changed.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    placeholder = { Text("e.g. \"the plant on the balcony\"") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Daily", 7 to "Weekly", 30 to "Monthly").forEach { (days, label) ->
                        FilterChip(selected = intervalDays == days, onClick = { intervalDays = days }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (subject.isNotBlank()) onConfirm(subject, intervalDays) },
                enabled = subject.isNotBlank(),
            ) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
    val gatedConfirm = rememberStepUpGatedAction(
        tier = AutonomyTier.T3,
        title = "Confirm reply",
        subtitle = "Verify it's you before NUA sends this reply.",
        action = onConfirm,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reply to ${pending.notification.title}?") },
        text = { Text("\"${pending.message}\"") },
        confirmButton = { TextButton(onClick = gatedConfirm) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** SMS permission is requested lazily, right here on first send attempt, rather than upfront at launch — most users never send a text via NUA. */
@Composable
private fun SmsConfirmationDialog(pending: NuaRouteResult.SmsProposed, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> onConfirm() }
    val requestPermissionThenSend: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            onConfirm()
        } else {
            permissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }
    val gatedConfirm = rememberStepUpGatedAction(
        tier = AutonomyTier.T3,
        title = "Confirm text",
        subtitle = "Verify it's you before NUA texts ${pending.contactName}.",
        action = requestPermissionThenSend,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text ${pending.contactName}?") },
        text = { Text("\"${pending.message}\"") },
        confirmButton = { TextButton(onClick = gatedConfirm) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlanConfirmationDialog(plan: TaskPlan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val gatedConfirm = rememberStepUpGatedAction(
        tier = AutonomyTier.T4,
        title = "Confirm plan",
        subtitle = "Verify it's you before NUA schedules this plan.",
        action = onConfirm,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plan.summary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.steps.forEach { step -> PlanStepRow(step) }
            }
        },
        confirmButton = { TextButton(onClick = gatedConfirm) { Text("Confirm plan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

/**
 * Wraps [action] behind a biometric/device-credential prompt when the tier requires
 * step-up ([requiresStepUpAuth]) and the device actually has one set up. Falls back to
 * calling [action] straight through otherwise — there's no FragmentActivity to prompt
 * from, or the device has no biometric/PIN enrolled, so gating would just block the user
 * rather than add security.
 */
@Composable
private fun rememberStepUpGatedAction(tier: AutonomyTier, title: String, subtitle: String, action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    // Haptic on commit only — these are the moments something actually changes in the
    // world. Buzzing on ordinary navigation too would stop it meaning anything.
    val confirmHaptic = rememberCommitHaptic()
    return {
        val activity = context.findFragmentActivity()
        val commit = {
            confirmHaptic()
            action()
        }
        if (requiresStepUpAuth(tier) && activity != null && BiometricGate.isAvailable(context)) {
            BiometricGate.authenticate(activity, title, subtitle) { success -> if (success) commit() }
        } else {
            commit()
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
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
