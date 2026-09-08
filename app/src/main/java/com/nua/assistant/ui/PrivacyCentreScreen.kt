package com.nua.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Dangerous runtime permissions worth showing the user, in the order MainActivity requests them. */
private val TRACKED_PERMISSIONS = listOf(
    "Microphone (voice commands)" to Manifest.permission.RECORD_AUDIO,
    "Calendar" to Manifest.permission.READ_CALENDAR,
    "Contacts" to Manifest.permission.READ_CONTACTS,
    "Send texts" to Manifest.permission.SEND_SMS,
    "Location" to Manifest.permission.ACCESS_COARSE_LOCATION,
    "Notifications" to Manifest.permission.POST_NOTIFICATIONS,
)

/**
 * "What NUA knows, what it does with it, and how to make it stop" — the consolidated view
 * the per-fact detail dialog and scattered Settings cards (Security, Trust, Audit Trail)
 * don't add up to on their own. Doesn't duplicate their content; links the ideas together
 * and adds the two things nothing else in Settings has: an actual data export, and a real
 * "delete everything" action.
 */
@Composable
fun PrivacyCentreContent(
    factCount: Int,
    goalCount: Int,
    decisionCount: Int,
    dreamCount: Int,
    exportText: String,
    onResetConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { WhatNuaKnowsCard(factCount, goalCount, decisionCount, dreamCount) }
        item { LocalVsCloudCard() }
        item { PermissionsCard() }
        item { ExportCard(exportText) }
        item { ResetCard(onReset = { showResetConfirm = true }) }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "This permanently deletes everything NUA has stored on this device — facts, " +
                        "goals, decisions, dreams, conversation history, your saved API key, and " +
                        "your voice profile. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onResetConfirmed(); showResetConfirm = false }) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WhatNuaKnowsCard(factCount: Int, goalCount: Int, decisionCount: Int, dreamCount: Int) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("What NUA knows", style = MaterialTheme.typography.titleSmall)
            Text(
                "$factCount fact${if (factCount == 1) "" else "s"}, $goalCount goal${if (goalCount == 1) "" else "s"}, " +
                    "$decisionCount decision${if (decisionCount == 1) "" else "s"}, $dreamCount dream${if (dreamCount == 1) "" else "s"} " +
                    "stored on this device. Each fact's own detail view (in Settings) shows why NUA " +
                    "remembers it, how confident it is, and lets you correct or forget it individually.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LocalVsCloudCard() {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Local vs. sent to Claude", style = MaterialTheme.typography.titleSmall)
            Text(
                "Everything NUA stores lives on this device (see Security in Settings for what's " +
                    "additionally encrypted). When you ask NUA something, only the facts relevant to " +
                    "that specific message — not your whole memory — are included in the request sent " +
                    "to Anthropic's Claude API to generate a reply. Nothing is sent anywhere else.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PermissionsCard() {
    val context = LocalContext.current
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            TRACKED_PERMISSIONS.forEach { (label, permission) ->
                val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ExportCard(exportText: String) {
    val context = LocalContext.current
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Export your data", style = MaterialTheme.typography.titleSmall)
            Text(
                "A plain-text copy of every fact, goal, decision, and dream — yours to save or share " +
                    "wherever you want.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, exportText)
                }
                context.startActivity(Intent.createChooser(intent, "Export NUA data"))
            }) { Text("Export") }
        }
    }
}

@Composable
private fun ResetCard(onReset: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Delete everything", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Text(
                "Permanently erases everything NUA has stored on this device. There's no undo — " +
                    "export first if you want to keep a copy.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onReset) { Text("Delete everything", color = MaterialTheme.colorScheme.error) }
        }
    }
}
