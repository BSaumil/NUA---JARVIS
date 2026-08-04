package com.nua.assistant.notifications

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NotificationEntry(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTimeMillis: Long,
    val category: String?,
    val priority: Int,
)

data class NotificationSummary(
    val needsAttention: List<RankedNotification>,
    val canWait: List<RankedNotification>,
) {
    /** e.g. "Two need attention, eight can wait." — plain phrasing, not a raw count dump. */
    val spokenSummary: String
        get() {
            if (needsAttention.isEmpty() && canWait.isEmpty()) return "You're all caught up, no notifications."
            val parts = buildList {
                if (needsAttention.isNotEmpty()) add("${needsAttention.size} need${if (needsAttention.size == 1) "s" else ""} attention")
                if (canWait.isNotEmpty()) add("${canWait.size} can wait")
            }
            return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
        }
}

/**
 * Holds the currently active notifications (fed by NuaNotificationListenerService) and
 * ranks them via NotificationPriorityScorer instead of just listing everything with
 * equal weight.
 */
@Singleton
class NotificationRepository @Inject constructor(
    private val priorityScorer: NotificationPriorityScorer,
) {

    private val _notifications = MutableStateFlow<List<NotificationEntry>>(emptyList())
    val notifications: StateFlow<List<NotificationEntry>> = _notifications.asStateFlow()

    fun upsert(entry: NotificationEntry) {
        _notifications.update { current -> current.filterNot { it.key == entry.key } + entry }
    }

    fun remove(key: String) {
        _notifications.update { current -> current.filterNot { it.key == key } }
    }

    fun replaceAll(entries: List<NotificationEntry>) {
        _notifications.value = entries
    }

    fun summary(): NotificationSummary {
        val ranked = _notifications.value
            .sortedByDescending { it.postTimeMillis }
            .map { priorityScorer.score(it) }
        val (needsAttention, canWait) = ranked.partition { it.needsAttention }
        return NotificationSummary(needsAttention = needsAttention, canWait = canWait)
    }
}
