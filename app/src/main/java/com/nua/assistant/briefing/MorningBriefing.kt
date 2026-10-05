package com.nua.assistant.briefing

import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.mesh.InferenceTaskType
import com.nua.assistant.ai.mesh.ModelMesh
import com.nua.assistant.ai.mesh.PrivacySensitivity
import com.nua.assistant.ai.mesh.TaskContract
import com.nua.assistant.context.ContextEngine
import com.nua.assistant.context.ContextSnapshot
import com.nua.assistant.context.describe
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.voice.NuaLanguage
import javax.inject.Inject
import javax.inject.Singleton

private val BRIEFING_SYSTEM_PROMPT = """
    You are NUA giving the user their daily briefing. You're handed their current
    situation (weather, today's calendar, notifications) plus known facts about them.
    Structure your reply into sections, but only put something in a section if it's
    genuinely true — no filler, no restating the raw data as if it were an insight, no
    invented risks or opportunities just to fill every section:

    - today: one or two warm, plainspoken sentences on the shape of the day.
    - attention: short items that actually need the user's attention today (empty list
      if none).
    - contextChanges: anything that changed from normal/expected — weather turning bad,
      a new or cancelled event (empty if nothing changed).
    - risks: concrete risks today's schedule or conditions create (empty if none).
    - opportunities: a genuine opportunity worth naming (empty if none).
    - recommendation: one concrete suggestion, or omit entirely if nothing rises to that
      level.

    If nothing about today is unusual, set nothingUnusual to true instead of stretching
    routine facts into sections that don't deserve them — an honest "nothing unusual" is
    more useful than padding.

    Reply with JSON only, no prose:
    {"nothingUnusual": true|false, "today": "<1-2 sentences>", "attention": ["..."], "contextChanges": ["..."], "risks": ["..."], "opportunities": ["..."], "recommendation": "<optional>"}
""".trimIndent()

/**
 * Hands ContextEngine's snapshot plus known user_facts to Claude to phrase into
 * structured sections, rather than reading raw data back at the user or collapsing
 * everything into one paragraph. TaskPlanner reuses this same "gather real context, then
 * ask Claude to phrase/plan it" shape.
 */
@Singleton
class MorningBriefing @Inject constructor(
    private val contextEngine: ContextEngine,
    private val memoryDao: MemoryDao,
    private val modelMesh: ModelMesh,
) {

    suspend fun generate(triggerUtterance: String? = null, pinnedLanguage: NuaLanguage? = null): DailyBriefing {
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

        val contract = TaskContract(task = InferenceTaskType.SUMMARIZATION, privacySensitivity = PrivacySensitivity.MEDIUM, requiresFrontierCapability = true)
        val result = modelMesh.complete(
            contract = contract,
            userPrompt = prompt,
            system = system,
            maxTokens = 400,
        )

        val parsed = (result as? ClaudeResult.Success)?.text?.let(::parseDailyBriefingResponse)
        return parsed ?: fallbackBriefing(snapshot)
    }

    /** Offline/Claude-failure-safe: built entirely from data already fetched for the
     *  prompt, no second network call. Used whenever Claude fails or replies with
     *  something unparseable — an honest, if plainer, briefing beats none at all. */
    private fun fallbackBriefing(snapshot: ContextSnapshot): DailyBriefing.Summary {
        val weatherPart = snapshot.weather?.let { "It's ${it.condition} and ${it.currentTempC.toInt()}°C out." }
            ?: "I couldn't get a weather reading."
        val eventCount = snapshot.eventsToday.size
        val calendarPart = if (eventCount == 0) "Nothing on your calendar today." else "You've got $eventCount thing${if (eventCount == 1) "" else "s"} on today."
        return DailyBriefing.Summary(today = "$weatherPart $calendarPart")
    }
}
