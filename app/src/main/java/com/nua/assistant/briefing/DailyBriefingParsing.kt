package com.nua.assistant.briefing

import com.nua.assistant.ai.extractJsonPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class DailyBriefingDto(
    val nothingUnusual: Boolean = false,
    val today: String = "",
    val attention: List<String> = emptyList(),
    val contextChanges: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val opportunities: List<String> = emptyList(),
    val recommendation: String? = null,
)

private val dailyBriefingJson = Json { ignoreUnknownKeys = true }

/**
 * Maps Claude's raw text reply to a [DailyBriefing]. Pure — no network, no Android — so
 * it's directly unit-testable. Returns null on anything unparseable or too thin to trust
 * (no [today] and not flagged [DailyBriefingDto.nothingUnusual]); the caller falls back to
 * a deterministic, context-only briefing rather than showing an empty one — see
 * MorningBriefing.generate().
 */
fun parseDailyBriefingResponse(rawText: String): DailyBriefing? {
    val dto = runCatching {
        dailyBriefingJson.decodeFromString(DailyBriefingDto.serializer(), extractJsonPayload(rawText))
    }.getOrNull() ?: return null

    if (dto.nothingUnusual) return DailyBriefing.NothingUnusual
    if (dto.today.isBlank()) return null

    return DailyBriefing.Summary(
        today = dto.today,
        attention = dto.attention,
        contextChanges = dto.contextChanges,
        risks = dto.risks,
        opportunities = dto.opportunities,
        recommendation = dto.recommendation?.takeIf { it.isNotBlank() },
    )
}

/** The notification/chat rendering — both delivery surfaces are plain text today, so both
 *  share this rather than each re-formatting the sections themselves. */
fun DailyBriefing.renderText(): String = when (this) {
    is DailyBriefing.Summary -> buildString {
        appendLine(today)
        if (attention.isNotEmpty()) appendLine("Attention: " + attention.joinToString("; "))
        if (contextChanges.isNotEmpty()) appendLine("Context changes: " + contextChanges.joinToString("; "))
        if (risks.isNotEmpty()) appendLine("Risks: " + risks.joinToString("; "))
        if (opportunities.isNotEmpty()) appendLine("Opportunities: " + opportunities.joinToString("; "))
        if (recommendation != null) append("Recommendation: $recommendation")
    }.trim()
    DailyBriefing.NothingUnusual -> "Nothing unusual today — you're all clear."
}
