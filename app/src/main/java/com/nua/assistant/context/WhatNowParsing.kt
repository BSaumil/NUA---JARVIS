package com.nua.assistant.context

import com.nua.assistant.ai.extractJsonPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class WhatNowDto(
    val hasRecommendation: Boolean = false,
    val action: String = "",
    val reason: String = "",
    val confidence: Double = 0.7,
    val estimatedMinutes: Int? = null,
)

private val whatNowJson = Json { ignoreUnknownKeys = true }

/**
 * Maps Claude's raw text reply to a [WhatNowResult]. Pure — no network, no Android — so
 * it's directly unit-testable, unlike WhatNowAdvisor.recommend() which needs a real
 * ClaudeApiClient. A malformed or unparseable reply becomes [WhatNowResult.Unavailable],
 * never silently treated as "nothing needs attention" — those are different claims.
 */
fun parseWhatNowResponse(rawText: String): WhatNowResult {
    val dto = runCatching {
        whatNowJson.decodeFromString(WhatNowDto.serializer(), extractJsonPayload(rawText))
    }.getOrNull() ?: return WhatNowResult.Unavailable("Couldn't work that out right now.")

    if (!dto.hasRecommendation || dto.action.isBlank()) return WhatNowResult.NothingNeedsAttention

    return WhatNowResult.Recommendation(
        action = dto.action,
        reason = dto.reason,
        confidence = dto.confidence.toFloat().coerceIn(0f, 1f),
        estimatedMinutes = dto.estimatedMinutes,
    )
}

/** The chat-transcript rendering of a [WhatNowResult] — ASK stays prose-based even though
 *  the Command Centre card shows the structured fields separately. */
fun WhatNowResult.chatSummary(): String = when (this) {
    is WhatNowResult.Recommendation -> "$action — $reason"
    WhatNowResult.NothingNeedsAttention -> "Nothing unusual — you're clear."
    is WhatNowResult.Unavailable -> "Couldn't work that out right now — $message"
}
