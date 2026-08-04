package com.nua.assistant.briefing

import com.nua.assistant.ai.CLAUDE_MODEL_CONVERSATION
import com.nua.assistant.ai.ClaudeApiClient
import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.calendar.CalendarReader
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.voice.NuaLanguage
import com.nua.assistant.weather.WeatherRepository
import com.nua.assistant.weather.WeatherSnapshot
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val BRIEFING_SYSTEM_PROMPT = """
    You are NUA giving the user their morning briefing. You're handed today's weather
    and calendar for the day, plus known facts about the user. Turn that into 2-4
    warm, plainspoken sentences — mention what actually matters (bad weather, a tight
    schedule, something colliding with a known preference), skip filler. Don't just
    read the data back as a list.
""".trimIndent()

/**
 * Assembles real facts (weather, today's calendar, known user_facts) and hands them to
 * Claude to phrase, rather than reading raw data back at the user. TaskPlanner reuses
 * this same "gather real context, then ask Claude to phrase/plan it" shape.
 */
@Singleton
class MorningBriefing @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val calendarReader: CalendarReader,
    private val memoryDao: MemoryDao,
    private val claudeApiClient: ClaudeApiClient,
) {

    suspend fun generate(triggerUtterance: String? = null, pinnedLanguage: NuaLanguage? = null): String {
        val weather = weatherRepository.currentSnapshot().getOrNull()
        val todayEvents = calendarReader.eventsBetween(startOfTodayMillis(), startOfTodayMillis() + ONE_DAY_MS)
        val facts = memoryDao.getAllFacts()

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val prompt = buildString {
            appendLine("Weather: ${weather?.let { "${it.condition}, currently ${it.currentTempC.toInt()}°C, high ${it.highTempC.toInt()}°C / low ${it.lowTempC.toInt()}°C, ${it.precipitationChancePercent}% chance of precipitation" } ?: "unavailable"}")
            appendLine("Today's calendar: ${if (todayEvents.isEmpty()) "nothing scheduled" else todayEvents.joinToString("; ") { "${it.title} at ${timeFormat.format(it.startTimeMillis)}" }}")
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
            is ClaudeResult.Failure -> fallbackBriefing(weather, todayEvents.size)
        }
    }

    private fun fallbackBriefing(weather: WeatherSnapshot?, eventCount: Int): String {
        val weatherPart = weather?.let { "It's ${it.condition} and ${it.currentTempC.toInt()}°C out." } ?: "I couldn't get a weather reading."
        val calendarPart = if (eventCount == 0) "Nothing on your calendar today." else "You've got $eventCount thing${if (eventCount == 1) "" else "s"} on today."
        return "$weatherPart $calendarPart"
    }

    private fun startOfTodayMillis(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private companion object {
        const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }
}
