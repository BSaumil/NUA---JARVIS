package com.nua.assistant.state

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.nua.assistant.calendar.CalendarReader
import com.nua.assistant.documents.DocumentRepository
import com.nua.assistant.goals.GoalRepository
import com.nua.assistant.network.ConnectivityMonitor
import com.nua.assistant.notifications.NotificationRepository
import com.nua.assistant.trust.TrustRepository
import com.nua.assistant.trust.countsAsFailure
import com.nua.assistant.vision.VisionMonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** A goal observation older than this isn't "recent" any more and stops being surfaced. */
private const val RECENT_OBSERVATION_WINDOW_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Gathers the real signals behind [NuaState] and hands them to the pure [deriveNuaState]
 * — the same split `SelfDiagnosticsRepository` uses, so the interesting logic stays
 * JVM-testable.
 *
 * Everything here reads something that already exists: active goals, the trust score
 * earned from real action outcomes, ranked notifications, today's calendar, documents
 * approaching expiry, vision monitors that are due, and recent goal observations. No
 * dimension is synthesised to fill the dashboard out.
 */
@Singleton
class NuaStateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val goalRepository: GoalRepository,
    private val trustRepository: TrustRepository,
    private val notificationRepository: NotificationRepository,
    private val calendarReader: CalendarReader,
    private val documentRepository: DocumentRepository,
    private val visionMonitorRepository: VisionMonitorRepository,
    private val connectivityMonitor: ConnectivityMonitor,
) {

    suspend fun currentState(): NuaState = deriveNuaState(gatherInputs())

    private suspend fun gatherInputs(): NuaStateInputs {
        val now = System.currentTimeMillis()
        val summary = notificationRepository.summary()

        // eventsBetween returns an empty list both when the calendar is empty and when
        // READ_CALENDAR isn't granted, so permission is checked separately — otherwise
        // "can't see your calendar" would silently render as "your day is clear".
        val calendarReadable = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

        val remainingToday = if (calendarReadable) {
            runCatching { calendarReader.eventsBetween(now, endOfToday(now)) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val currentlyInEvent = if (!calendarReadable) {
            null
        } else {
            runCatching { calendarReader.eventsBetween(now - 1, now + 1).isNotEmpty() }.getOrNull()
        }

        val failures = runCatching {
            trustRepository.recentOutcomes()
                .filter { it.outcomeState.countsAsFailure() && !it.wasRejection }
                .map { it.summary }
        }.getOrDefault(emptyList())

        val goals = runCatching { goalRepository.activeGoals() }.getOrDefault(emptyList())

        val observations = runCatching {
            goals.mapNotNull { goal -> goalRepository.latestObservation(goal.id) }
                .filter { now - it.timestamp <= RECENT_OBSERVATION_WINDOW_MILLIS }
                .map { it.text }
        }.getOrDefault(emptyList())

        val expiring = runCatching {
            documentRepository.documentsPendingExpiryReminder().map { "${it.fileName} is expiring" }
        }.getOrDefault(emptyList())

        val monitorsDue = runCatching {
            visionMonitorRepository.due().map { "Recheck ${it.subject}" }
        }.getOrDefault(emptyList())

        return NuaStateInputs(
            activeGoals = goals.map { it.text },
            trustScore = runCatching { trustRepository.scoreSnapshot() }.getOrNull(),
            notificationsNeedingAttention = summary.needsAttention.map { it.entry.title.ifBlank { it.entry.packageName } },
            notificationsCanWait = summary.canWait.size,
            eventsRemainingToday = remainingToday.size,
            currentlyInEvent = currentlyInEvent,
            recentFailures = failures,
            documentsExpiringSoon = expiring,
            visionMonitorsDue = monitorsDue,
            recentGoalObservations = observations,
            isOffline = !connectivityMonitor.isOnline(),
        )
    }

    private fun endOfToday(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
