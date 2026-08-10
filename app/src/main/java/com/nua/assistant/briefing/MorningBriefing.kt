package com.nua.assistant.briefing

import com.nua.assistant.ai.CLAUDE_MODEL_CONVERSATION
import com.nua.assistant.ai.ClaudeApiClient
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.context.ContextEngine
import com.nua.assistant.context.ContextSnapshot
import com.nua.assistant.context.describe
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

private val BRIEFING_SYSTEM_PROMPT = """
    You are NUA giving the user their morning briefing. You're handed their current
    situation (weather, today's calendar, notifications) plus known facts about them.
    Turn that into 2-4 warm, plainspoken sentences — mention what actually matters (bad
    weather, a tight schedule, something colliding with a known preference), skip
    filler. Don't just read the data back as a list.
""".trimIndent()

/**
 * Hands ContextEngine's snapshot plus known user_facts to Claude to phrase, rather than
 * reading raw data back at the user. TaskPlanner reuses this same "gather real context,
 * then ask Claude to phrase/plan it" shape.
 */
@Singleton
class MorningBriefing @Inject constructor(
    private val contextEngine: ContextEngine,
    private val memoryDao: MemoryDao,
    private val claudeApiClient: ClaudeApiClient,
) {

    suspend fun generate(triggerUtterance: String? = null, pinnedLanguage: NuaLanguage? = null): String {
        val snapshot = contextEngine.currentSnapshot()
        val facts = memoryDao.getAllFacts()

        val prompt = buildString {
            appendLine(snapshot.describe())
            appendLine("Known facts about the user: ${if (facts.isEmpty()) "none yet" else facts.joinToString("; ") { it.value }}")
            if (triggerUtterance != null) appendLine("User's own words when they asked for this, for you to notice what language to reply in: \"$triggerUtterance\"")
        }

        val system = buildString {
            appendLine(BRIEFING_SYSTEM_PROMPT)
            appendLine()
            appendLine(NuaLanguage.mirrorDirective())
            if (pinnedLanguage != null) appendLine(NuaLanguage.pinnedDirective(pinnedLanguage))
        }

        val result = claudeApiClient.complete(
            userPrompt = prompt,
            system = system,
            model = CLAUDE_MODEL_CONVERSATION,
            maxTokens = 300,
        )

        return when (result) {
            is ClaudeResult.Success -> result.text
            is ClaudeResult.Failure -> fallbackBriefing(snapshot)
        }
    }

    private fun fallbackBriefing(snapshot: ContextSnapshot): String {
        val weatherPart = snapshot.weather?.let { "It's ${it.condition} and ${it.currentTempC.toInt()}°C out." }
            ?: "I couldn't get a weather reading."
        val eventCount = snapshot.eventsToday.size
        val calendarPart = if (eventCount == 0) "Nothing on your calendar today." else "You've got $eventCount thing${if (eventCount == 1) "" else "s"} on today."
        return "$weatherPart $calendarPart"
    }
}
