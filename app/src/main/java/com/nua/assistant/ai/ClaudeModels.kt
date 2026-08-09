package com.nua.assistant.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Model used for the main conversational turn — personality, chat replies. */
const val CLAUDE_MODEL_CONVERSATION = "claude-sonnet-5"

/**
 * Model used for cheap, structured, high-volume calls (intent classification, fact
 * extraction, notification scoring) where a small model is accurate enough and the
 * cost/latency of the flagship model isn't justified.
 */
const val CLAUDE_MODEL_UTILITY = "claude-haiku-4-5-20251001"

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ClaudeCacheControl(val type: String = "ephemeral")

/**
 * The system prompt as a single cacheable block. Every call marks it cache_control —
 * harmless on a miss (content changed since last call), a real latency/cost win on a
 * hit (the static utility prompts in IntentClassifier/FactExtractor especially, since
 * those are identical across calls where the main chat's system prompt varies with
 * facts/tone and hits less often).
 */
@Serializable
data class ClaudeSystemBlock(
    val type: String = "text",
    val text: String,
    @SerialName("cache_control") val cacheControl: ClaudeCacheControl = ClaudeCacheControl(),
)

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: List<ClaudeSystemBlock>? = null,
    val messages: List<ClaudeMessage>,
    val temperature: Double? = null,
    val stream: Boolean = false,
)

@Serializable
data class ClaudeContentBlock(
    val type: String,
    val text: String? = null,
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)

@Serializable
data class ClaudeResponse(
    val id: String = "",
    val role: String = "",
    val content: List<ClaudeContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: ClaudeUsage = ClaudeUsage(),
) {
    fun text(): String = content.firstOrNull { it.type == "text" }?.text.orEmpty()
}

@Serializable
data class ClaudeErrorBody(
    val type: String = "",
    val message: String = "",
)

@Serializable
data class ClaudeErrorEnvelope(
    val type: String = "",
    val error: ClaudeErrorBody = ClaudeErrorBody(),
)

// --- Streaming (Messages API server-sent events) ---

@Serializable
data class ClaudeStreamDelta(
    val type: String = "",
    val text: String = "",
)

@Serializable
data class ClaudeStreamEventBody(
    val type: String = "",
    val delta: ClaudeStreamDelta? = null,
)

sealed class ClaudeStreamEvent {
    data class TextDelta(val text: String) : ClaudeStreamEvent()
    data class Done(val fullText: String) : ClaudeStreamEvent()
    data class Error(val message: String) : ClaudeStreamEvent()
}
