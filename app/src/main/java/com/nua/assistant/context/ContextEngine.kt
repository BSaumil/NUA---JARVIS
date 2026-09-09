package com.nua.assistant.context

import com.nua.assistant.calendar.CalendarEvent
import com.nua.assistant.calendar.CalendarReader
import com.nua.assistant.decisions.DecisionRepository
import com.nua.assistant.goals.GoalRepository
import com.nua.assistant.goals.GoalType
import com.nua.assistant.memory.DecisionEntity
import com.nua.assistant.memory.GoalEntity
import com.nua.assistant.network.ConnectivityMonitor
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.notifications.NotificationSummary
import com.nua.assistant.weather.WeatherRepository
import com.nua.assistant.weather.WeatherSnapshot
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** How many recent decisions ride along in a snapshot — enough for "what was decided
 *  lately" without turning every prompt into a full Decision Journal dump. */
private const val RECENT_DECISIONS_IN_SNAPSHOT = 5

/**
 * NUA's "current situation," queried fresh each time rather than cached — formalizes
 * signals that MorningBriefing, WhatNowAdvisor, GoalReviewWorker, and DreamSynthesisWorker
 * each used to gather independently into one object those features can read instead of
 * re-implementing their own aggregation.
 */
data class ContextSnapshot(
    val timestampMillis: Long,
    val isOnline: Boolean,
    val weather: WeatherSnapshot?,
    /** Every event on today's calendar, not just the next one — see [nextEventToday] for that. */
    val eventsToday: List<CalendarEvent>,
    val notificationSummary: NotificationSummary,
    val activeGoals: List<GoalEntity>,
    val recentDecisions: List<DecisionEntity>,
    /** The saved place (a geofence's own name) the user is currently near, or null when
     *  unknown/not near any saved place — never raw coordinates, see CurrentPlaceResolver. */
    val currentPlace: String?,
) {
    val nextEventToday: CalendarEvent?
        get() = eventsToday.filter { it.startTimeMillis >= timestampMillis }.minByOrNull { it.startTimeMillis }

    /** Active goals the user tagged as recurring — the closest thing this app can
     *  honestly call a "routine": user-declared, not behaviourally inferred (this project
     *  doesn't do pattern-mining over historical activity, and won't fabricate a signal
     *  it can't actually detect). */
    val routines: List<GoalEntity>
        get() = activeGoals.filter { it.type == GoalType.ROUTINE }
}

/** A plain-English rendering of a snapshot, meant to be dropped straight into a Claude prompt. */
fun ContextSnapshot.describe(): String {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return buildString {
        appendLine("Current time: ${timeFormat.format(timestampMillis)}")
        appendLine("Connectivity: ${if (isOnline) "online" else "offline"}")
        appendLine(
            "Weather: " + (
                weather?.let { "${it.condition}, ${it.currentTempC.toInt()}°C, ${it.precipitationChancePercent}% chance of rain" }
                    ?: "unavailable"
                ),
        )
        appendLine(
            "Today's calendar: " + (
                if (eventsToday.isEmpty()) "nothing scheduled"
                else eventsToday.joinToString("; ") { "${it.title} at ${timeFormat.format(it.startTimeMillis)}" }
                ),
        )
        val attention = notificationSummary.needsAttention.size
        val canWait = notificationSummary.canWait.size
        appendLine("Notifications: $attention needing attention, $canWait that can wait")
        appendLine(
            "Active goals: " + (
                if (activeGoals.isEmpty()) "none set"
                else activeGoals.joinToString("; ") { "${it.text} (${it.type.name.lowercase()})" }
                ),
        )
        appendLine(
            "Recent decisions: " + (
                if (recentDecisions.isEmpty()) "none logged"
                else recentDecisions.joinToString("; ") { it.decision }
                ),
        )
        append("Current place: " + (currentPlace ?: "unknown"))
    }
}

@Singleton
class ContextEngine @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val calendarReader: CalendarReader,
    private val connectivityMonitor: ConnectivityMonitor,
    private val notificationRepository: NotificationRepository,
    private val goalRepository: GoalRepository,
    private val decisionRepository: DecisionRepository,
    private val currentPlaceResolver: CurrentPlaceResolver,
) {
    suspend fun currentSnapshot(): ContextSnapshot {
        val now = System.currentTimeMillis()
        val startOfDay = startOfTodayMillis()
        val todayEvents = calendarReader.eventsBetween(startOfDay, startOfDay + ONE_DAY_MS)

        return ContextSnapshot(
            timestampMillis = now,
            isOnline = connectivityMonitor.isOnline(),
            weather = weatherRepository.currentSnapshot().getOrNull(),
            eventsToday = todayEvents,
            notificationSummary = notificationRepository.summary(),
            activeGoals = goalRepository.activeGoals(),
            recentDecisions = decisionRepository.recent(RECENT_DECISIONS_IN_SNAPSHOT),
            currentPlace = currentPlaceResolver.currentPlaceName(),
        )
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
