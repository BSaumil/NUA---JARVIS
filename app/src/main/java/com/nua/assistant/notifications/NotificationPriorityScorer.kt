package com.nua.assistant.notifications

import android.app.Notification
import javax.inject.Inject
import javax.inject.Singleton

/** Package-name substrings for the apps people most commonly need to respond to promptly. */
private val PRIORITY_APP_HINTS = setOf("dialer", "messaging", "whatsapp", "sms", "telephony")

private const val QUICK_RESPONSE_RATE_THRESHOLD = 0.5

data class RankedNotification(
    val entry: NotificationEntry,
    val needsAttention: Boolean,
    val reason: String,
)

/**
 * Scores a single notification against a handful of independent urgency signals —
 * category, declared priority, whether it's from a call/messaging-shaped app, and
 * whether this app's notifications have historically been dismissed quickly (a proxy
 * for "the user actually responds to this one"). Any one signal is enough to flag it.
 */
@Singleton
class NotificationPriorityScorer @Inject constructor(
    private val statsProvider: NotificationStatsProvider,
) {

    fun score(entry: NotificationEntry): RankedNotification {
        val reasons = mutableListOf<String>()

        if (entry.category == Notification.CATEGORY_CALL) reasons += "it's a call"
        if (entry.category == Notification.CATEGORY_MESSAGE) reasons += "it's a message"
        if (entry.priority >= Notification.PRIORITY_HIGH) reasons += "marked high priority"
        if (PRIORITY_APP_HINTS.any { entry.packageName.contains(it, ignoreCase = true) }) {
            reasons += "from a messaging/phone app"
        }
        if (statsProvider.quickResponseRate(entry.packageName) > QUICK_RESPONSE_RATE_THRESHOLD) {
            reasons += "you usually respond to this app quickly"
        }

        return RankedNotification(
            entry = entry,
            needsAttention = reasons.isNotEmpty(),
            reason = reasons.firstOrNull() ?: "no urgency signals",
        )
    }
}
