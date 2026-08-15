package com.nua.assistant.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nua.assistant.memory.DocumentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val WORD_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private val DOCUMENT_PICKER_MIME_TYPES = arrayOf("application/pdf", WORD_MIME_TYPE, "image/*")

/** PDF/Word/image ingestion, summaries, expiry dates, and targeted Q&A (including comparisons) over what NUA has read. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    documents: List<DocumentEntity>,
    onIngest: (uri: Uri, mimeType: String?, fileName: String) -> Unit,
    onAsk: (documentIds: List<Long>, question: String) -> Unit,
    onRemove: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var askTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onIngest(uri, context.contentResolver.getType(uri), displayNameFor(context, uri))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { pickerLauncher.launch(DOCUMENT_PICKER_MIME_TYPES) }) {
                        Icon(Icons.Filled.UploadFile, contentDescription = "Add a document")
                    }
                },
            )
        },
    ) { padding ->
        if (documents.isEmpty()) {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text(
                    "Nothing read yet. Tap the upload icon to add a PDF, Word document, or image.",
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
                items(documents, key = { it.id }) { document ->
                    DocumentCard(document, onAsk = { askTarget = document }, onRemove = { onRemove(document.id) })
                }
            }
        }
    }

    askTarget?.let { target ->
        AskDocumentDialog(
            target = target,
            others = documents.filter { it.id != target.id },
            onAsk = { selectedIds, question ->
                onAsk(selectedIds, question)
                askTarget = null
            },
            onDismiss = { askTarget = null },
        )
    }
}

private val documentDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@Composable
private fun DocumentCard(document: DocumentEntity, onAsk: () -> Unit, onRemove: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(document.fileName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        document.type.name.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove document")
                }
            }
            if (!document.summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(document.summary, style = MaterialTheme.typography.bodySmall)
            }
            document.expiryDate?.let { expiry ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Expires ${documentDateFormat.format(Date(expiry))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onAsk) { Text("Ask about this") }
        }
    }
}

@Composable
private fun AskDocumentDialog(
    target: DocumentEntity,
    others: List<DocumentEntity>,
    onAsk: (documentIds: List<Long>, question: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val includedIds = remember { mutableStateOf(setOf(target.id)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask about ${target.fileName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("e.g. \"What am I obligated to do?\"") },
                )
                if (others.isNotEmpty()) {
                    Text(
                        "Also include, to compare:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    others.forEach { other ->
                        Row {
                            Checkbox(
                                checked = other.id in includedIds.value,
                                onCheckedChange = { checked ->
                                    includedIds.value = if (checked) includedIds.value + other.id else includedIds.value - other.id
                                },
                            )
                            Text(other.fileName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (question.isNotBlank()) onAsk(includedIds.value.toList(), question) },
                enabled = question.isNotBlank(),
            ) { Text("Ask") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun displayNameFor(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)?.let { return it }
        }
    }
    return uri.lastPathSegment ?: "document"
}
