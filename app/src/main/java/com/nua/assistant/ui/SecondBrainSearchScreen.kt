package com.nua.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nua.assistant.ai.SecondBrainResult

/** Dedicated natural-language search over what NUA remembers (facts) and has noticed (dreams). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondBrainSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SecondBrainResult>,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Second Brain") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search what NUA knows and has noticed...") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                query.isBlank() -> Text(
                    text = "Search facts NUA remembers and dreams it's noticed about you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                results.isEmpty() -> Text(
                    text = "No matches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { result -> SecondBrainResultCard(result) }
                }
            }
        }
    }
}

@Composable
private fun SecondBrainResultCard(result: SecondBrainResult) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            when (result) {
                is SecondBrainResult.FactHit -> {
                    Text(
                        text = "Fact",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = result.fact.value, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = result.fact.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is SecondBrainResult.DreamHit -> {
                    Text(
                        text = "Dream",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = result.dream.text, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = result.dream.category.name.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
