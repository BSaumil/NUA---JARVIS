package com.nua.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.ai.UsageSummary
import com.nua.assistant.automation.NuaAccessibilityService
import com.nua.assistant.briefing.BriefingSchedule
import com.nua.assistant.goals.GoalType
import com.nua.assistant.memory.ActionOutcomeEntity
import com.nua.assistant.memory.AutonomyPreferenceEntity
import com.nua.assistant.diagnostics.DiagnosticCheck
import com.nua.assistant.diagnostics.DiagnosticStatus
import com.nua.assistant.security.BiometricGate
import com.nua.assistant.security.encryptionAuditEntries
import com.nua.assistant.trust.badgeLabel
import com.nua.assistant.trust.countsAsFailure
import com.nua.assistant.memory.DecisionEntity
import com.nua.assistant.memory.DreamEntity
import com.nua.assistant.memory.GeofenceEntity
import com.nua.assistant.memory.GoalEntity
import com.nua.assistant.memory.GoalObservationEntity
import com.nua.assistant.memory.MemoryPrivacyLevel
import com.nua.assistant.memory.MemoryType
import com.nua.assistant.memory.TrustLedgerEntity
import com.nua.assistant.memory.UserFactEntity
import com.nua.assistant.memory.VisionMonitorEntity
import com.nua.assistant.services.WakePhrases
import com.nua.assistant.services.isAvailable
import com.nua.assistant.voice.NuaLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    facts: List<UserFactEntity>,
    onForgetFact: (Long) -> Unit,
    onForgetFactsByType: (MemoryType) -> Unit,
    onCorrectFact: (id: Long, newValue: String) -> Unit,
    onSetFactPrivacyLevel: (id: Long, level: MemoryPrivacyLevel) -> Unit,
    pinnedLanguage: NuaLanguage?,
    onLanguageSelected: (NuaLanguage?) -> Unit,
    briefingSchedule: BriefingSchedule,
    onBriefingScheduleChanged: (BriefingSchedule) -> Unit,
    voiceEnrolled: Boolean,
    enrollmentProgress: Int?,
    onRecordEnrollmentClip: () -> Unit,
    onResetVoiceEnrollment: () -> Unit,
    geofences: List<GeofenceEntity>,
    onAddGeofence: (name: String, latitude: Double, longitude: Double, message: String) -> Unit,
    onRemoveGeofence: (Long) -> Unit,
    usageThisMonth: UsageSummary?,
    trustScore: Int?,
    trustLedger: List<TrustLedgerEntity>,
    actionOutcomes: List<ActionOutcomeEntity>,
    autonomySuggestions: List<AutonomyPreferenceEntity>,
    onEnableAutoApprove: (NuaActionType) -> Unit,
    goals: List<GoalEntity>,
    goalObservations: List<GoalObservationEntity>,
    onAddGoal: (text: String, type: GoalType) -> Unit,
    onRemoveGoal: (Long) -> Unit,
    dreams: List<DreamEntity>,
    dreamConnections: Map<Long, List<String>>,
    decisions: List<DecisionEntity>,
    onAddDecision: (decision: String, reasoning: String?, facts: String?, unknowns: String?, constraints: String?, options: String?) -> Unit,
    onRecordDecisionOutcome: (id: Long, outcome: String) -> Unit,
    onRemoveDecision: (Long) -> Unit,
    visionMonitors: List<VisionMonitorEntity>,
    onRecheckVisionMonitor: (monitorId: Long, uri: Uri) -> Unit,
    onRemoveVisionMonitor: (Long) -> Unit,
    diagnostics: List<DiagnosticCheck>,
    testingApiConnection: Boolean,
    onRefreshDiagnostics: () -> Unit,
    onTestApiConnection: () -> Unit,
    onBack: (() -> Unit)?,
) {
    var selectedMemoryType by remember { mutableStateOf<MemoryType?>(null) }
    var selectedFact by remember { mutableStateOf<UserFactEntity?>(null) }
    val visibleFacts = if (selectedMemoryType == null) facts else facts.filter { it.memoryType == selectedMemoryType }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                // Null when Settings is a bottom-nav destination rather than a pushed
                // screen — a back arrow that goes nowhere is worse than no arrow.
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { LanguageCard(pinnedLanguage, onLanguageSelected) }
            item { BriefingScheduleCard(briefingSchedule, onBriefingScheduleChanged) }
            item { WakeWordsCard() }
            item { VoiceIdCard(voiceEnrolled, enrollmentProgress, onRecordEnrollmentClip, onResetVoiceEnrollment) }
            item { GeofenceCard(geofences, onAddGeofence, onRemoveGeofence) }
            item { UsageCard(usageThisMonth) }
            item { TrustCard(trustScore, trustLedger, autonomySuggestions, onEnableAutoApprove) }
            item { AuditTrailCard(actionOutcomes) }
            item { GoalsCard(goals, goalObservations, onAddGoal, onRemoveGoal) }
            item { DreamsCard(dreams, dreamConnections) }
            item { DecisionsCard(decisions, onAddDecision, onRecordDecisionOutcome, onRemoveDecision) }
            item { VisionMonitorsCard(visionMonitors, onRecheckVisionMonitor, onRemoveVisionMonitor) }
            item { DiagnosticsCard(diagnostics, testingApiConnection, onRefreshDiagnostics, onTestApiConnection) }
            item { SecurityCard() }
            item { Tier2StatusCard() }
            item { BatteryOptimizationCard() }
            item { Text("What NUA remembers", style = MaterialTheme.typography.titleMedium) }

            if (facts.isNotEmpty()) {
                item { MemoryTypeFilterRow(selected = selectedMemoryType, onSelect = { selectedMemoryType = it }) }
            }

            if (selectedMemoryType != null && visibleFacts.isNotEmpty()) {
                item {
                    TextButton(onClick = { onForgetFactsByType(selectedMemoryType!!) }) {
                        Text("Forget all ${visibleFacts.size} ${selectedMemoryType!!.name.lowercase()} facts")
                    }
                }
            }

            if (visibleFacts.isEmpty()) {
                val message = if (facts.isEmpty()) {
                    "Nothing yet — facts are picked up naturally as you chat."
                } else {
                    "No facts of this type."
                }
                item { Text(message, style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(visibleFacts, key = { it.id }) { fact ->
                    FactRow(fact, onTap = { selectedFact = fact }, onForget = { onForgetFact(fact.id) })
                }
            }
        }
    }

    selectedFact?.let { fact ->
        FactDetailDialog(
            fact = fact,
            onForget = {
                onForgetFact(fact.id)
                selectedFact = null
            },
            onCorrect = { newValue -> onCorrectFact(fact.id, newValue) },
            onSetPrivacyLevel = { level -> onSetFactPrivacyLevel(fact.id, level) },
            onDismiss = { selectedFact = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryTypeFilterRow(selected: MemoryType?, onSelect: (MemoryType?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        MemoryType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.name.lowercase().replaceFirstChar(Char::uppercase)) },
            )
        }
    }
}

@Composable
private fun FactDetailDialog(
    fact: UserFactEntity,
    onForget: () -> Unit,
    onCorrect: (String) -> Unit,
    onSetPrivacyLevel: (MemoryPrivacyLevel) -> Unit,
    onDismiss: () -> Unit,
) {
    var isEditing by remember(fact.id) { mutableStateOf(false) }
    var editValue by remember(fact.id) { mutableStateOf(fact.value) }
    val isSensitive = fact.privacyLevel == MemoryPrivacyLevel.SENSITIVE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (isEditing) {
                OutlinedTextField(value = editValue, onValueChange = { editValue = it }, singleLine = true)
            } else {
                Text(fact.value)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FactDetailRow("Category", fact.category)
                FactDetailRow("Memory type", fact.memoryType.name.lowercase())
                FactDetailRow("Why NUA remembers this", fact.source ?: "Not recorded")
                FactDetailRow("Confidence", "${(fact.confidence * 100).toInt()}%")
                FactDetailRow("Learned", relativeDaysAgo(fact.createdAt))
                FactDetailRow("Last updated", relativeDaysAgo(fact.updatedAt))
                FactDetailRow("Last used in conversation", fact.lastUsedAt?.let { relativeDaysAgo(it) } ?: "Never")
                if (!isEditing) {
                    TextButton(onClick = { onSetPrivacyLevel(if (isSensitive) MemoryPrivacyLevel.STANDARD else MemoryPrivacyLevel.SENSITIVE) }) {
                        Text(if (isSensitive) "Remove sensitive mark" else "Mark as sensitive")
                    }
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                TextButton(onClick = { onCorrect(editValue); isEditing = false }) { Text("Save") }
            } else {
                TextButton(onClick = onForget) { Text("Forget this") }
            }
        },
        dismissButton = {
            if (isEditing) {
                TextButton(onClick = { isEditing = false; editValue = fact.value }) { Text("Cancel") }
            } else {
                Row {
                    TextButton(onClick = { isEditing = true }) { Text("Correct this") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        },
    )
}

@Composable
private fun FactDetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FactRow(fact: UserFactEntity, onTap: () -> Unit, onForget: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onTap).padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = fact.value, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (fact.privacyLevel == MemoryPrivacyLevel.SENSITIVE) {
                        "${fact.category} · ${fact.memoryType.name.lowercase()} · sensitive"
                    } else {
                        "${fact.category} · ${fact.memoryType.name.lowercase()}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (fact.source != null) {
                    Text(
                        text = "Why: ${fact.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (fact.lastUsedAt != null) {
                    Text(
                        text = "Last used ${relativeDaysAgo(fact.lastUsedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onForget) {
                Icon(Icons.Filled.Delete, contentDescription = "Forget this")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageCard(pinnedLanguage: NuaLanguage?, onLanguageSelected: (NuaLanguage?) -> Unit) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Reply language", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Auto matches whatever language you write or speak in. Pinning one " +
                    "also sets the voice used for speech input and output.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = pinnedLanguage == null,
                    onClick = { onLanguageSelected(null) },
                    label = { Text("Auto") },
                )
                NuaLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = pinnedLanguage == language,
                        onClick = { onLanguageSelected(language) },
                        label = { Text(language.displayName) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BriefingScheduleCard(schedule: BriefingSchedule, onChanged: (BriefingSchedule) -> Unit) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Proactive morning briefing", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = { enabled -> onChanged(schedule.copy(enabled = enabled)) },
                )
            }
            Text(
                text = "Delivered as a notification (and saved to the conversation) at the time below — " +
                    "not read aloud unprompted.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (schedule.enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BRIEFING_HOUR_PRESETS.forEach { hour ->
                        FilterChip(
                            selected = schedule.hour == hour,
                            onClick = { onChanged(schedule.copy(hour = hour, minute = 0)) },
                            label = { Text(displayHour(hour)) },
                        )
                    }
                }
            }
        }
    }
}

private val BRIEFING_HOUR_PRESETS = listOf(5, 6, 7, 8, 9, 10)

private fun displayHour(hour24: Int): String {
    val period = if (hour24 < 12) "AM" else "PM"
    val hour12 = when (val h = hour24 % 12) { 0 -> 12; else -> h }
    return "$hour12:00 $period"
}

@Composable
private fun VoiceIdCard(
    enrolled: Boolean,
    enrollmentProgress: Int?,
    onRecordClip: () -> Unit,
    onReset: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Voice ID (experimental)", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Not used by anything yet — infrastructure for a future Tier 2 action to " +
                    "confirm it's really you before it acts. Needs a few short voice clips to enroll.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            when {
                enrolled -> TextButton(onClick = onReset) { Text("Forget voice enrollment") }
                enrollmentProgress != null -> Column {
                    Text("Enrolling: $enrollmentProgress% — keep recording clips", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = onRecordClip) { Text("Record another clip") }
                }
                else -> TextButton(onClick = onRecordClip) { Text("Start voice enrollment") }
            }
        }
    }
}

@Composable
private fun GeofenceCard(
    geofences: List<GeofenceEntity>,
    onAdd: (name: String, latitude: Double, longitude: Double, message: String) -> Unit,
    onRemove: (Long) -> Unit,
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        showAddDialog = true
    }

    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Location reminders", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "NUA notifies you when you arrive somewhere you've flagged — e.g. \"the pharmacy\" — " +
                    "using Android's official geofencing API. Needs location access, including \"Allow all the time\".",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            geofences.forEach { geofence ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(geofence.name, style = MaterialTheme.typography.bodyMedium)
                        Text(geofence.message, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { onRemove(geofence.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                }
            }
            TextButton(onClick = {
                if (hasLocationPermission(context)) {
                    showAddDialog = true
                } else {
                    val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        permissions += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            }) {
                Text("Add a location reminder")
            }
        }
    }

    if (showAddDialog) {
        AddGeofenceDialog(
            onConfirm = { name, lat, lon, message ->
                onAdd(name, lat, lon, message)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    return fine && background
}

@Composable
private fun AddGeofenceDialog(
    onConfirm: (name: String, latitude: Double, longitude: Double, message: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val latValue = latitude.toDoubleOrNull()
    val lonValue = longitude.toDoubleOrNull()
    val valid = name.isNotBlank() && message.isNotBlank() && latValue != null && lonValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New location reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Name, e.g. \"the pharmacy\"") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = latitude, onValueChange = { latitude = it }, placeholder = { Text("Latitude") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = longitude, onValueChange = { longitude = it }, placeholder = { Text("Longitude") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = message, onValueChange = { message = it }, placeholder = { Text("What NUA should say when you arrive") })
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(name, latValue!!, lonValue!!, message) }, enabled = valid) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UsageCard(usageThisMonth: UsageSummary?) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Claude API usage this month", style = MaterialTheme.typography.titleSmall)
            when {
                usageThisMonth == null -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                usageThisMonth.inputTokens == 0L && usageThisMonth.outputTokens == 0L ->
                    Text("Nothing logged yet.", style = MaterialTheme.typography.bodySmall)
                else -> {
                    Text(
                        text = "~$%.2f".format(usageThisMonth.totalCostUsd),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "${usageThisMonth.inputTokens} tokens in, ${usageThisMonth.outputTokens} out — " +
                            "estimated from published list pricing, not your actual bill.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustCard(
    trustScore: Int?,
    trustLedger: List<TrustLedgerEntity>,
    autonomySuggestions: List<AutonomyPreferenceEntity>,
    onEnableAutoApprove: (NuaActionType) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Trust", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "How much autonomy NUA has earned, based only on what actually happened — " +
                    "successful actions, failures, and proposals you've turned down.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (trustScore != null) "$trustScore%" else "Loading…",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (autonomySuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                autonomySuggestions.forEach { suggestion ->
                    val actionType = runCatching { NuaActionType.valueOf(suggestion.actionType) }.getOrNull()
                    if (actionType != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "You've approved ${displayActionType(actionType)} ${suggestion.approvedCount} times.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onEnableAutoApprove(actionType) }) { Text("Always allow") }
                        }
                    }
                }
            }

            if (trustLedger.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Recently", style = MaterialTheme.typography.labelMedium)
                trustLedger.take(5).forEach { entry ->
                    Text("• ${entry.description}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun relativeDaysAgo(timestampMillis: Long): String {
    val days = (System.currentTimeMillis() - timestampMillis) / (24 * 60 * 60 * 1000)
    return when {
        days <= 0 -> "today"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}

private fun displayActionType(actionType: NuaActionType): String = when (actionType) {
    NuaActionType.REPLY_TO_NOTIFICATION -> "replying to notifications"
    NuaActionType.PLAN_TASK -> "confirming task plans"
    NuaActionType.SMS_SEND -> "sending texts"
    else -> actionType.name.lowercase().replace('_', ' ')
}

@Composable
private fun AuditTrailCard(actionOutcomes: List<ActionOutcomeEntity>) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Recent actions", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Every action NUA has taken, what tier it's authorized at, and whether it worked.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (actionOutcomes.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Nothing logged yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                actionOutcomes.take(8).forEach { outcome ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(outcome.summary, style = MaterialTheme.typography.bodySmall)
                            Text(outcome.tier.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = if (outcome.wasRejection) "Declined" else outcome.outcomeState.badgeLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (outcome.outcomeState.countsAsFailure()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsCard(
    goals: List<GoalEntity>,
    observations: List<GoalObservationEntity>,
    onAdd: (text: String, type: GoalType) -> Unit,
    onRemove: (Long) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Goals", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Durable things you're trying to do, not one-off requests. Once a week, " +
                    "NUA checks each one against your current situation and says something only " +
                    "if it actually has something worth saying.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            goals.forEach { goal ->
                val latest = observations.filter { it.goalId == goal.id }.maxByOrNull { it.timestamp }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(goal.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            goal.type.name.lowercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (latest != null) {
                            Text(latest.text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { onRemove(goal.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove goal")
                    }
                }
            }
            TextButton(onClick = { showAddDialog = true }) { Text("Add a goal") }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onConfirm = { text, type ->
                onAdd(text, type)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddGoalDialog(onConfirm: (text: String, type: GoalType) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(GoalType.GOAL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("e.g. \"Get my mornings under control\"") },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalType.entries.forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(candidate.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text, type) }, enabled = text.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DreamsCard(dreams: List<DreamEntity>, dreamConnections: Map<Long, List<String>>) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("NUA Dreams", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "At most once a week, NUA looks across everything it knows for one thing " +
                    "worth pointing out — never a summary of what you already know.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (dreams.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Nothing yet — needs a bit more history to cross-reference.", style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                dreams.take(5).forEach { dream ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(dream.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            dream.category.name.lowercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val connections = dreamConnections[dream.id].orEmpty()
                        if (connections.isNotEmpty()) {
                            Text(
                                "Connected to: " + connections.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecisionsCard(
    decisions: List<DecisionEntity>,
    onAdd: (decision: String, reasoning: String?, facts: String?, unknowns: String?, constraints: String?, options: String?) -> Unit,
    onRecordOutcome: (id: Long, outcome: String) -> Unit,
    onRemove: (Long) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var outcomeTargetId by remember { mutableStateOf<Long?>(null) }

    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Decision Journal", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Decisions worth remembering why you made — and, later, how they turned out.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (decisions.isEmpty()) {
                Text("Nothing logged yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                decisions.forEach { decision ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                            Text(decision.decision, style = MaterialTheme.typography.bodyMedium)
                            if (decision.reasoning != null) {
                                Text(
                                    decision.reasoning,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DecisionDetailLine("Facts", decision.facts)
                            DecisionDetailLine("Unknowns", decision.unknowns)
                            DecisionDetailLine("Constraints", decision.constraints)
                            DecisionDetailLine("Options considered", decision.options)
                            if (decision.outcome != null) {
                                Text(
                                    "Outcome: ${decision.outcome}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                TextButton(onClick = { outcomeTargetId = decision.id }) { Text("Record outcome") }
                            }
                        }
                        IconButton(onClick = { onRemove(decision.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove decision")
                        }
                    }
                }
            }
            TextButton(onClick = { showAddDialog = true }) { Text("Log a decision") }
        }
    }

    if (showAddDialog) {
        AddDecisionDialog(
            onConfirm = { decision, reasoning, facts, unknowns, constraints, options ->
                onAdd(decision, reasoning, facts, unknowns, constraints, options)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    outcomeTargetId?.let { id ->
        RecordOutcomeDialog(
            onConfirm = { outcome ->
                onRecordOutcome(id, outcome)
                outcomeTargetId = null
            },
            onDismiss = { outcomeTargetId = null },
        )
    }
}

@Composable
private fun DecisionDetailLine(label: String, value: String?) {
    if (value == null) return
    Text(
        "$label: $value",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AddDecisionDialog(
    onConfirm: (decision: String, reasoning: String?, facts: String?, unknowns: String?, constraints: String?, options: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var decision by remember { mutableStateOf("") }
    var reasoning by remember { mutableStateOf("") }
    var showMoreDetail by remember { mutableStateOf(false) }
    var facts by remember { mutableStateOf("") }
    var unknowns by remember { mutableStateOf("") }
    var constraints by remember { mutableStateOf("") }
    var options by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a decision") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = decision,
                    onValueChange = { decision = it },
                    placeholder = { Text("e.g. \"Switching to a 4-day work week\"") },
                )
                OutlinedTextField(
                    value = reasoning,
                    onValueChange = { reasoning = it },
                    placeholder = { Text("Why (optional)") },
                )
                if (!showMoreDetail) {
                    TextButton(onClick = { showMoreDetail = true }) { Text("Add more detail") }
                } else {
                    OutlinedTextField(
                        value = facts,
                        onValueChange = { facts = it },
                        placeholder = { Text("Facts — what you actually knew (optional)") },
                    )
                    OutlinedTextField(
                        value = unknowns,
                        onValueChange = { unknowns = it },
                        placeholder = { Text("Unknowns — what you didn't know (optional)") },
                    )
                    OutlinedTextField(
                        value = constraints,
                        onValueChange = { constraints = it },
                        placeholder = { Text("Constraints — time, money, etc. (optional)") },
                    )
                    OutlinedTextField(
                        value = options,
                        onValueChange = { options = it },
                        placeholder = { Text("Other options you considered (optional)") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (decision.isNotBlank()) {
                        onConfirm(
                            decision,
                            reasoning.ifBlank { null },
                            facts.ifBlank { null },
                            unknowns.ifBlank { null },
                            constraints.ifBlank { null },
                            options.ifBlank { null },
                        )
                    }
                },
                enabled = decision.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RecordOutcomeDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var outcome by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How did it turn out?") },
        text = {
            OutlinedTextField(
                value = outcome,
                onValueChange = { outcome = it },
                placeholder = { Text("What happened") },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (outcome.isNotBlank()) onConfirm(outcome) }, enabled = outcome.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun VisionMonitorsCard(
    monitors: List<VisionMonitorEntity>,
    onRecheck: (monitorId: Long, uri: Uri) -> Unit,
    onRemove: (Long) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Vision monitors", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "NUA can't take photos on its own — \"monitor this\" after a photo means " +
                    "it'll remind you to snap a fresh one on schedule and tell you what's changed.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (monitors.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Nothing being watched yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                monitors.forEach { monitor ->
                    VisionMonitorRow(
                        monitor = monitor,
                        onRecheck = { uri -> onRecheck(monitor.id, uri) },
                        onRemove = { onRemove(monitor.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VisionMonitorRow(monitor: VisionMonitorEntity, onRecheck: (Uri) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingUri?.let(onRecheck)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(monitor.subject, style = MaterialTheme.typography.bodyMedium)
            val intervalLabel = if (monitor.intervalDays == 1) "day" else "${monitor.intervalDays} days"
            Text(
                text = "Every $intervalLabel · last checked ${relativeDaysAgo(monitor.lastCheckedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = {
            val uri = createImageCaptureUri(context)
            pendingUri = uri
            cameraLauncher.launch(uri)
        }) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = "Recheck now")
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Stop monitoring")
        }
    }
}

@Composable
private fun DiagnosticsCard(
    diagnostics: List<DiagnosticCheck>,
    testingApiConnection: Boolean,
    onRefresh: () -> Unit,
    onTestApiConnection: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Self-diagnostics", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh diagnostics")
                }
            }
            Text(
                text = "Real system-health checks, not \"something went wrong\" — each row explains exactly what's missing.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (diagnostics.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tap refresh to run diagnostics.", style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                diagnostics.forEach { check -> DiagnosticRow(check) }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onTestApiConnection, enabled = !testingApiConnection) {
                    if (testingApiConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (testingApiConnection) "Testing connection…" else "Test API connection")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(check: DiagnosticCheck) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(diagnosticStatusColor(check.status)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(check.category.label, style = MaterialTheme.typography.bodyMedium)
            Text(check.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun diagnosticStatusColor(status: DiagnosticStatus) = when (status) {
    DiagnosticStatus.OK -> MaterialTheme.colorScheme.primary
    DiagnosticStatus.WARNING -> MaterialTheme.colorScheme.tertiary
    DiagnosticStatus.ERROR -> MaterialTheme.colorScheme.error
    DiagnosticStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.outline
    DiagnosticStatus.INFO -> MaterialTheme.colorScheme.outline
}

@Composable
private fun SecurityCard() {
    val context = LocalContext.current
    val biometricAvailable = remember { BiometricGate.isAvailable(context) }
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Security", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (biometricAvailable) {
                    "Step-up authentication is set up on this device — NUA asks for your fingerprint, " +
                        "face, or device PIN before sending a text, replying to a notification, or " +
                        "confirming a multi-step plan."
                } else {
                    "No biometric or device-credential lock is set up, so step-up authentication is " +
                        "skipped for now — set a fingerprint, face, or PIN in system Settings to enable it."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("What's encrypted at rest", style = MaterialTheme.typography.labelLarge)
            encryptionAuditEntries().forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (entry.encrypted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                        Text(entry.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WakeWordsCard() {
    val context = LocalContext.current

    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Wake words", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Say any active phrase to start a voice turn. \"Needs setup\" ones are " +
                    "declared but waiting on a trained model — see app/src/main/assets/README.md.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            WakePhrases.ALL.forEach { phrase ->
                val active = remember(phrase) { phrase.isAvailable(context) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(phrase.displayText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (active) "Active" else "Needs setup",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Tier2StatusCard() {
    val context = LocalContext.current
    val enabled = rememberRefreshingOnResume { NuaAccessibilityService.isEnabled(context) }

    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Tier 2 automation", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (enabled) {
                    "Enabled. NUA can only use it for actions you confirm individually, in the moment."
                } else {
                    "Off — no official-API path is being bypassed. Enable only from Android Settings if a specific action needs it."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text(if (enabled) "Manage in Android Settings" else "Open Android Settings")
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCard() {
    val context = LocalContext.current
    val ignoringOptimizations = rememberRefreshingOnResume { isIgnoringBatteryOptimizations(context) }

    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Wake-word reliability", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (ignoringOptimizations) {
                    "Battery optimization is off for NUA — wake-word listening can keep running in the background."
                } else {
                    "Battery optimization may kill wake-word listening in the background on this device."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (!ignoringOptimizations) {
                TextButton(onClick = { requestIgnoreBatteryOptimizations(context) }) {
                    Text("Exempt NUA from battery optimization")
                }
            }
        }
    }
}

/** Re-runs [compute] whenever this screen resumes (e.g. returning from a system Settings screen). */
@Composable
private fun rememberRefreshingOnResume(compute: () -> Boolean): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var value by remember { mutableStateOf(compute()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) value = compute()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return value
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    context.startActivity(intent)
}
