package com.nua.assistant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nua.assistant.automation.NuaAccessibilityService
import com.nua.assistant.memory.UserFactEntity
import com.nua.assistant.services.WakePhrases
import com.nua.assistant.services.isAvailable
import com.nua.assistant.voice.NuaLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    facts: List<UserFactEntity>,
    onForgetFact: (Long) -> Unit,
    pinnedLanguage: NuaLanguage?,
    onLanguageSelected: (NuaLanguage?) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            item { WakeWordsCard() }
            item { Tier2StatusCard() }
            item { BatteryOptimizationCard() }
            item { Text("What NUA remembers", style = MaterialTheme.typography.titleMedium) }

            if (facts.isEmpty()) {
                item { Text("Nothing yet — facts are picked up naturally as you chat.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(facts, key = { it.id }) { fact -> FactRow(fact, onForget = { onForgetFact(fact.id) }) }
            }
        }
    }
}

@Composable
private fun FactRow(fact: UserFactEntity, onForget: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = fact.value, style = MaterialTheme.typography.bodyMedium)
                Text(text = fact.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
