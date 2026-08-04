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
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<ClaudeMessage>,
    val temperature: Double? = null,
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
