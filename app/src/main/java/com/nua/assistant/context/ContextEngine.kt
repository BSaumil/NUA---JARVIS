package com.nua.assistant.context

import com.nua.assistant.calendar.CalendarEvent
import com.nua.assistant.calendar.CalendarReader
import com.nua.assistant.network.ConnectivityMonitor
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.notifications.NotificationSummary
import com.nua.assistant.weather.WeatherRepository
import com.nua.assistant.weather.WeatherSnapshot
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NUA's "current situation," queried fresh each time rather than cached — formalizes
 * signals that MorningBriefing, geofencing, and Settings already gather independently
 * into one object other features (Goals, and later Dreams/the daily briefing) can read
 * instead of each re-implementing its own aggregation.
 */
data class ContextSnapshot(
    val timestampMillis: Long,
    val isOnline: Boolean,
    val weather: WeatherSnapshot?,
    val nextEventToday: CalendarEvent?,
    val notificationSummary: NotificationSummary,
)

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
            "Next calendar event today: " + (
                nextEventToday?.let { "${it.title} at ${timeFormat.format(it.startTimeMillis)}" } ?: "none"
                ),
        )
        val attention = notificationSummary.needsAttention.size
        val canWait = notificationSummary.canWait.size
        append("Notifications: $attention needing attention, $canWait that can wait")
    }
}

@Singleton
class ContextEngine @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val calendarReader: CalendarReader,
    private val connectivityMonitor: ConnectivityMonitor,
    private val notificationRepository: NotificationRepository,
) {
    suspend fun currentSnapshot(): ContextSnapshot {
        val now = System.currentTimeMillis()
        val startOfDay = startOfTodayMillis()
        val todayEvents = calendarReader.eventsBetween(now, startOfDay + ONE_DAY_MS)

        return ContextSnapshot(
            timestampMillis = now,
            isOnline = connectivityMonitor.isOnline(),
            weather = weatherRepository.currentSnapshot().getOrNull(),
            nextEventToday = todayEvents.minByOrNull { it.startTimeMillis },
            notificationSummary = notificationRepository.summary(),
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
