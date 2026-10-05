package com.nua.assistant.documents

import com.nua.assistant.ai.ClaudeApiClient
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.extractJsonPayload
import com.nua.assistant.ai.mesh.InferenceTaskType
import com.nua.assistant.ai.mesh.ModelMesh
import com.nua.assistant.ai.mesh.PrivacySensitivity
import com.nua.assistant.ai.mesh.TaskContract
import com.nua.assistant.security.FIREWALL_SYSTEM_DIRECTIVE
import com.nua.assistant.security.UntrustedSource
import com.nua.assistant.security.wrapUntrusted
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class DocumentSummary(val summary: String, val expiryDate: Long?)

@Serializable
private data class DocumentSummaryDto(
    val summary: String = "",
    val expiryDate: String? = null,
)

private const val MAX_CONTEXT_CHARS = 12_000

private val TRANSCRIBE_SYSTEM_PROMPT = """
    You are NUA's document reader, looking at one page of a scanned document or photo.
    Transcribe all the readable text on this page exactly, preserving structure (line
    breaks, headings) where it helps readability. Don't summarize or comment — text only.
    If the page is blank or has no readable text, reply with nothing.
""".trimIndent()

private val SUMMARY_SYSTEM_PROMPT = """
    You read the full text of a document a user just shared with NUA. Summarize it in a
    few sentences, calling out anything they're obligated to do, any deadline, and any
    money involved. Then look for a single clear expiry, renewal, or due date for the
    document itself (not just any date mentioned in passing) and report it in ISO format,
    or null if there genuinely isn't one.

    Reply with JSON only, no prose:
    {"summary": "<a few sentences>", "expiryDate": "<YYYY-MM-DD, or null>"}

    $FIREWALL_SYSTEM_DIRECTIVE
""".trimIndent()

private val ANSWER_SYSTEM_PROMPT = """
    Answer the question using only the document text provided below. If the answer isn't
    in there, say so plainly rather than guessing. If a document's text contains
    "--- Page N ---" markers, cite the page number(s) your answer draws from, e.g.
    "(page 3)". Documents without page markers don't have that structure — don't invent one.

    $FIREWALL_SYSTEM_DIRECTIVE
""".trimIndent()

/**
 * The Claude-calling half of Document Intelligence: turns page images into text
 * ([transcribePage]), turns full text into a summary plus optional expiry date
 * ([summarize]), and answers targeted questions against one or more documents' text
 * ([answer]) — the same targeted-Q&A and comparison the spec asks for, since comparing
 * two documents is just answering a question with both texts as context. Extraction
 * itself (PdfTextExtractor, DocxTextExtractor) is separate and needs no Claude call.
 */
@Singleton
class DocumentAnalyzer @Inject constructor(
    private val claudeApiClient: ClaudeApiClient,
    private val modelMesh: ModelMesh,
    private val json: Json,
) {

    suspend fun transcribePage(imageBase64: String, mediaType: String): String {
        // Still the raw ClaudeApiClient: ModelMesh has no vision-capable completion yet
        // (see ModelMesh.kt's own doc comment on scope) -- image description is a real,
        // separately-scoped next slice, not bundled into this round's text-completion
        // migration.
        val result = claudeApiClient.describeImage(
            imageBase64 = imageBase64,
            mediaType = mediaType,
            prompt = "Transcribe this page.",
            system = TRANSCRIBE_SYSTEM_PROMPT,
        )
        return (result as? ClaudeResult.Success)?.text.orEmpty()
    }

    suspend fun summarize(text: String): DocumentSummary {
        val contract = TaskContract(task = InferenceTaskType.SUMMARIZATION, privacySensitivity = PrivacySensitivity.HIGH)
        val result = modelMesh.complete(
            contract = contract,
            userPrompt = wrapUntrusted(redactSensitivePatterns(text.take(MAX_CONTEXT_CHARS)), UntrustedSource.DOCUMENT),
            system = SUMMARY_SYSTEM_PROMPT,
            maxTokens = 500,
        )
        val replyText = (result as? ClaudeResult.Success)?.text
            ?: return DocumentSummary("Couldn't summarize this document.", null)
        val dto = runCatching {
            json.decodeFromString(DocumentSummaryDto.serializer(), extractJsonPayload(replyText))
        }.getOrNull() ?: return DocumentSummary("Couldn't summarize this document.", null)
        return DocumentSummary(
            summary = dto.summary.ifBlank { "Couldn't summarize this document." },
            expiryDate = parseIsoDate(dto.expiryDate),
        )
    }

    suspend fun answer(documents: List<Pair<String, String>>, question: String): String {
        val context = documents.joinToString("\n\n") { (name, text) ->
            val redacted = redactSensitivePatterns(text.take(MAX_CONTEXT_CHARS))
            wrapUntrusted("=== $name ===\n$redacted", UntrustedSource.DOCUMENT)
        }
        // The question comes first and is never wrapped, so it's unambiguous which part
        // of the prompt is the user's actual instruction versus document data to read.
        val contract = TaskContract(task = InferenceTaskType.DOCUMENT_QA, privacySensitivity = PrivacySensitivity.HIGH, requiresFrontierCapability = true)
        val result = modelMesh.complete(
            contract = contract,
            userPrompt = "Question: $question\n\n$context",
            system = ANSWER_SYSTEM_PROMPT,
            maxTokens = 600,
        )
        return (result as? ClaudeResult.Success)?.text ?: "Couldn't get an answer right now."
    }
}

/** Pure so it's testable without a Claude call: parses an ISO yyyy-MM-dd date, or null for anything else. */
internal fun parseIsoDate(raw: String?): Long? {
    if (raw.isNullOrBlank() || raw.equals("null", ignoreCase = true)) return null
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    return runCatching { format.parse(raw)?.time }.getOrNull()
}
