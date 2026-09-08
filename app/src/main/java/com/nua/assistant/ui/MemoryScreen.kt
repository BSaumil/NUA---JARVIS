package com.nua.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nua.assistant.ai.SecondBrainResult
import com.nua.assistant.memory.DocumentEntity
import com.nua.assistant.timeline.TimelineEntry
import com.nua.assistant.ui.nav.MemorySection
import com.nua.assistant.ui.theme.NuaTheme
import android.net.Uri

/**
 * "Memory" — everything NUA knows, in one place. Search, Timeline, and Documents were
 * previously three separate top-level screens reached by three separate icons; they're
 * all views onto the same thing, so they're sections here rather than destinations of
 * their own. The bottom bar stays a map of five ideas instead of becoming a menu.
 */
@Composable
fun MemoryScreen(
    section: MemorySection,
    onSectionChange: (MemorySection) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SecondBrainResult>,
    timeline: List<TimelineEntry>,
    documents: List<DocumentEntity>,
    onIngestDocument: (uri: Uri, mimeType: String?, fileName: String) -> Unit,
    onAskDocuments: (documentIds: List<Long>, question: String) -> Unit,
    onRemoveDocument: (Long) -> Unit,
    factCount: Int,
    goalCount: Int,
    decisionCount: Int,
    dreamCount: Int,
    exportText: String,
    onResetConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SectionChips(section = section, onSectionChange = onSectionChange)
        when (section) {
            MemorySection.SEARCH -> SecondBrainSearchContent(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                results = searchResults,
                modifier = Modifier.fillMaxSize(),
            )

            MemorySection.TIMELINE -> TimelineContent(entries = timeline, modifier = Modifier.fillMaxSize())

            MemorySection.DOCUMENTS -> DocumentsContent(
                documents = documents,
                onIngest = onIngestDocument,
                onAsk = onAskDocuments,
                onRemove = onRemoveDocument,
                modifier = Modifier.fillMaxSize(),
            )

            MemorySection.PRIVACY -> PrivacyCentreContent(
                factCount = factCount,
                goalCount = goalCount,
                decisionCount = decisionCount,
                dreamCount = dreamCount,
                exportText = exportText,
                onResetConfirmed = onResetConfirmed,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SectionChips(section: MemorySection, onSectionChange: (MemorySection) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MemorySection.entries.forEach { entry ->
            FilterChip(
                selected = entry == section,
                onClick = { onSectionChange(entry) },
                label = { Text(entry.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NuaTheme.colors.surfaceElevated,
                    selectedLabelColor = NuaTheme.colors.brandIdentity,
                ),
            )
        }
    }
}
