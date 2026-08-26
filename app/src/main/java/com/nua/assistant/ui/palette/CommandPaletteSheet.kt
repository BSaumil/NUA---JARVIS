package com.nua.assistant.ui.palette

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nua.assistant.ui.theme.NuaTheme

/**
 * The command palette (`#37`) — one surface for "get me to the thing" across destinations,
 * capabilities, and what NUA remembers. Ranking lives in the pure [buildPalette] so the
 * behaviour is unit-tested rather than eyeballed.
 *
 * A row that won't execute outright says so in its own title ("Ask NUA to send a text"),
 * because a palette that looks like it fires a T3 action in one tap — then opens a
 * confirmation instead — teaches the user to distrust the labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPaletteSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    entries: List<PaletteEntry>,
    onChoose: (PaletteEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NuaTheme.colors.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search NUA — screens, actions, memories") },
                singleLine = true,
            )
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.height(360.dp),
            ) {
                items(entries) { entry -> PaletteRow(entry, onChoose) }
            }
        }
    }
}

@Composable
private fun PaletteRow(entry: PaletteEntry, onChoose: (PaletteEntry) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChoose(entry) }
            .padding(vertical = 10.dp),
    ) {
        Text(entry.title, style = MaterialTheme.typography.bodyLarge, color = NuaTheme.colors.textPrimary)
        Text(
            text = entry.subtitle,
            style = MaterialTheme.typography.labelSmall,
            // A gated row is tinted with the identity colour, so "this needs your
            // confirmation" is visible before you tap, not after.
            color = if (entry.executesImmediately) NuaTheme.colors.textSecondary else NuaTheme.colors.brandIdentity,
        )
    }
}
