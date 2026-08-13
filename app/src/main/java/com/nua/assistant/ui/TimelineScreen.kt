package com.nua.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nua.assistant.timeline.TimelineEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A chronological feed of everything NUA has learned, noticed, or logged — facts, dreams, decisions, goal observations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(entries: List<TimelineEntry>, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timeline") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Nothing here yet — facts, dreams, decisions, and goal observations will show up as they happen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries) { entry -> TimelineEntryCard(entry) }
            }
        }
    }
}

private val timelineDateFormat = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())

@Composable
private fun TimelineEntryCard(entry: TimelineEntry) {
    val (label, text, detail) = when (entry) {
        is TimelineEntry.FactLearned -> Triple("Learned", entry.fact.value, entry.fact.category)
        is TimelineEntry.DreamSurfaced -> Triple("Dream", entry.dream.text, entry.dream.category.name.lowercase())
        is TimelineEntry.DecisionLogged -> Triple("Decision logged", entry.decision.decision, entry.decision.reasoning)
        is TimelineEntry.DecisionOutcomeRecorded -> Triple("Outcome recorded", entry.decision.decision, entry.decision.outcome)
        is TimelineEntry.GoalObservationNoted -> Triple("Goal observation", entry.observation.text, null)
    }

    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                timelineDateFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
