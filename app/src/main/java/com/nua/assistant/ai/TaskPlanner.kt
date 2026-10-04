package com.nua.assistant.ai

import com.nua.assistant.calendar.CalendarReader
import com.nua.assistant.voice.NuaLanguage
import com.nua.assistant.weather.WeatherRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SuggestedReminder(val title: String, val whenMillis: Long)

data class PlannedStep(
    val title: String,
    val detail: String,
    val suggestedReminder: SuggestedReminder? = null,
)

data class TaskPlan(
    val summary: String,
    val steps: List<PlannedStep>,
)

@Serializable
private data class PlannedStepDto(
    val title: String = "",
    val detail: String = "",
    val reminderTitle: String? = null,
    /** Hours from now the reminder should fire, e.g. 3.5. Omitted if this step needs no reminder. */
    val reminderOffsetHours: Double? = null,
)

@Serializable
private data class TaskPlanDto(
    val summary: String = "",
    val steps: List<PlannedStepDto> = emptyList(),
)

private val PLANNER_SYSTEM_PROMPT = """
    The user described an upcoming activity. You're given real weather and calendar
    context — use it, don't ignore it (e.g. rain in the forecast should show up in the
    plan). Propose a short, practical plan: 2-5 concrete steps. A step MAY suggest a
    reminder if timing matters (packing the night before, leaving by a certain time);
    most steps don't need one.

    Reply with JSON only, no prose:
    {"summary": "<one sentence>", "steps": [{"title": "<short>", "detail": "<one sentence>", "reminderTitle": "<optional>", "reminderOffsetHours": <optional number, hours from now>}]}
""".trimIndent()

/**
 * Gathers real weather/calendar context the same way MorningBriefing does, then asks
 * Claude to propose a short plan. [propose] never writes anything — it only returns a
 * plan for the UI to show the user; creating the plan's actual calendar reminders once
 * the user confirms is
 * [com.nua.assistant.automation.uaf.PlanConfirmationAdapter]'s job, reached only through
 * [com.nua.assistant.automation.uaf.WorkflowExecutor] with a real
 * [com.nua.assistant.automation.uaf.AuthorizationProof.UserConfirmed] — not a method on
 * this class, so there's exactly one place in the codebase that turns a proposed plan
 * into real calendar writes.
 */
@Singleton
class TaskPlanner @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val calendarReader: CalendarReader,
    private val claudeApiClient: ClaudeApiClient,
    private val json: Json,
) {

    suspend fun propose(activityDescription: String, pinnedLanguage: NuaLanguage? = null): TaskPlan? {
        val weather = weatherRepository.currentSnapshot().getOrNull()
        val upcomingEvents = calendarReader.eventsForTomorrow()

        val prompt = buildString {
            appendLine("Activity: $activityDescription")
            appendLine(
                "Weather: " +
                    (
                        weather?.let {
                            "${it.condition}, high ${it.highTempC.toInt()}°C / low ${it.lowTempC.toInt()}°C, ${it.precipitationChancePercent}% chance of precipitation"
                        } ?: "unavailable"
                        ),
            )
            appendLine("Tomorrow's calendar: ${if (upcomingEvents.isEmpty()) "clear" else upcomingEvents.joinToString("; ") { it.title }}")
        }

        val system = buildString {
            appendLine(PLANNER_SYSTEM_PROMPT)
            appendLine()
            appendLine(NuaLanguage.mirrorDirective())
            if (pinnedLanguage != null) appendLine(NuaLanguage.pinnedDirective(pinnedLanguage))
        }

        val result = claudeApiClient.complete(
            userPrompt = prompt,
            system = system,
            model = CLAUDE_MODEL_CONVERSATION,
            maxTokens = 700,
        )

        val text = (result as? ClaudeResult.Success)?.text ?: return null
        val dto = runCatching {
            json.decodeFromString(TaskPlanDto.serializer(), extractJsonPayload(text))
        }.getOrNull() ?: return null

        if (dto.steps.isEmpty()) return null

        val now = System.currentTimeMillis()
        return TaskPlan(
            summary = dto.summary,
            steps = dto.steps.map { step ->
                val reminder = if (step.reminderTitle != null && step.reminderOffsetHours != null) {
                    SuggestedReminder(
                        title = step.reminderTitle,
                        whenMillis = now + (step.reminderOffsetHours * 3_600_000L).toLong(),
                    )
                } else {
                    null
                }
                PlannedStep(title = step.title, detail = step.detail, suggestedReminder = reminder)
            },
        )
    }
}
