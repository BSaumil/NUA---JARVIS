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
    action — not a list, not options to weigh. Say what it is, and in one short clause
    why it's the right one right now (timing, a goal it serves, something it unblocks).
    If nothing in the situation points to an obvious next action, say so plainly instead
    of inventing one.
""".trimIndent()

/**
 * Backs the "What should I do now?" entry point — the one-tap, zero-typing way into
 * everything ContextEngine and Goals already gather. No new intelligence, just a
 * dedicated prompt shape over data those two already produce.
 */
@Singleton
class WhatNowAdvisor @Inject constructor(
    private val contextEngine: ContextEngine,
    private val goalRepository: GoalRepository,
    private val claudeApiClient: ClaudeApiClient,
) {
    suspend fun recommend(pinnedLanguage: NuaLanguage? = null): String {
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
            maxTokens = 200,
        )

        return when (result) {
            is ClaudeResult.Success -> result.text
            is ClaudeResult.Failure -> "Couldn't work that out right now — ${result.message}"
        }
    }
}
