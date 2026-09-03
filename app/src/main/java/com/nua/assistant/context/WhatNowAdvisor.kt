package com.nua.assistant.context

import com.nua.assistant.ai.CLAUDE_MODEL_CONVERSATION
import com.nua.assistant.ai.ClaudeApiClient
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.goals.GoalRepository
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

private val WHAT_NOW_SYSTEM_PROMPT = """
    The user just asked what they should do right now. You're handed their current
    situation and any durable goals they've set. Recommend exactly one concrete next
    action if — and only if — something in the situation actually points to one: timing,
    a goal it serves, something it unblocks. If nothing does, say so honestly instead of
    inventing a plausible-sounding action just to have an answer.

    Reply with JSON only, no prose:
    {"hasRecommendation": true|false, "action": "<short, one sentence>", "reason": "<why this, why now — one short clause>", "confidence": <0-1>, "estimatedMinutes": <optional integer, omit if you can't estimate honestly>}

    When hasRecommendation is false, action/reason/confidence/estimatedMinutes are ignored
    — leave them empty or omitted.
""".trimIndent()

/**
 * Backs the "What should I do now?" entry point — the one-tap, zero-typing way into
 * everything ContextEngine and Goals already gather. No new intelligence, just a
 * dedicated prompt shape over data those two already produce. Returns a structured
 * [WhatNowResult] rather than free text so the UI can show reason/confidence/estimate
 * separately and distinguish "nothing needs attention" from "couldn't work it out."
 */
@Singleton
class WhatNowAdvisor @Inject constructor(
    private val contextEngine: ContextEngine,
    private val goalRepository: GoalRepository,
    private val claudeApiClient: ClaudeApiClient,
) {
    suspend fun recommend(pinnedLanguage: NuaLanguage? = null): WhatNowResult {
        val snapshot = contextEngine.currentSnapshot()
        val goals = goalRepository.activeGoals()

        val prompt = buildString {
            appendLine(snapshot.describe())
            appendLine("Active goals: " + if (goals.isEmpty()) "none set" else goals.joinToString("; ") { it.text })
        }

        val system = buildString {
            appendLine(WHAT_NOW_SYSTEM_PROMPT)
            appendLine()
            appendLine(NuaLanguage.mirrorDirective())
            if (pinnedLanguage != null) appendLine(NuaLanguage.pinnedDirective(pinnedLanguage))
        }

        val result = claudeApiClient.complete(
            userPrompt = prompt,
            system = system,
            model = CLAUDE_MODEL_CONVERSATION,
            maxTokens = 250,
        )

        return when (result) {
            is ClaudeResult.Success -> parseWhatNowResponse(result.text)
            is ClaudeResult.Failure -> WhatNowResult.Unavailable(result.message)
        }
    }
}
