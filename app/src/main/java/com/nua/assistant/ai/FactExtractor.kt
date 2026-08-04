package com.nua.assistant.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ExtractedFact(
    val key: String,
    val value: String,
    val category: String,
)

@Serializable
private data class ExtractedFactDto(
    val key: String = "",
    val value: String = "",
    val category: String = "other",
)

@Serializable
private data class FactExtractionResponse(
    val facts: List<ExtractedFactDto> = emptyList(),
)

private val EXTRACTION_SYSTEM_PROMPT = """
    You read one exchange from a conversation between a user and their personal
    assistant, NUA. Identify any durable fact worth remembering long-term: a stated
    preference, a routine, a name, a relationship, an allergy, a recurring
    commitment. Ignore anything one-off, already obvious, or not about the user.

    Reply with JSON only, no prose:
    {"facts": [{"key": "<short_snake_case_id>", "value": "<one sentence, third person, e.g. 'Prefers energetic music for workouts'>", "category": "preference|routine|name|relationship|other"}]}

    If there is nothing durable worth remembering, reply {"facts": []}. Don't invent
    facts that aren't actually stated.
""".trimIndent()

/**
 * Flags durable facts from a conversation turn and hands them back for the caller to
 * persist via MemoryDao.upsertFact. Deliberately does not call Claude on every turn —
 * [shouldConsider] gates it to turns that look likely to contain a fact, plus a
 * periodic sweep so slow-burn facts aren't missed entirely.
 */
@Singleton
class FactExtractor @Inject constructor(
    private val claudeApiClient: ClaudeApiClient,
    private val json: Json,
) {

    /** Pure gating predicate, kept static so it's unit-testable without constructing FactExtractor. */
    companion object {
        private val FACT_HINT_REGEX = Regex(
            """\b(my name is|call me|i'm allergic|i am allergic|i live|i work at|i work as|i drive a|i own a|i always|i usually|i never|i like|i love|i hate|i prefer|remind me|my (birthday|anniversary))\b""",
            RegexOption.IGNORE_CASE,
        )

        /** Fall back to checking one turn in this many even without a local keyword hint. */
        private const val OPPORTUNISTIC_TURN_INTERVAL = 4

        fun shouldConsider(userMessage: String, userTurnIndex: Int): Boolean {
            return FACT_HINT_REGEX.containsMatchIn(userMessage) ||
                userTurnIndex % OPPORTUNISTIC_TURN_INTERVAL == 0
        }
    }

    suspend fun extractFacts(userMessage: String, assistantReply: String): List<ExtractedFact> {
        val exchange = "User: $userMessage\nNUA: $assistantReply"
        val result = claudeApiClient.complete(
            userPrompt = exchange,
            system = EXTRACTION_SYSTEM_PROMPT,
            model = CLAUDE_MODEL_UTILITY,
            maxTokens = 400,
        )

        val text = (result as? ClaudeResult.Success)?.text ?: return emptyList()

        val response = runCatching {
            json.decodeFromString(FactExtractionResponse.serializer(), extractJsonPayload(text))
        }.getOrNull() ?: return emptyList()

        return response.facts
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .map { ExtractedFact(key = it.key, value = it.value, category = it.category) }
    }
}
