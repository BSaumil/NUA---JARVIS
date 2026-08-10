package com.nua.assistant.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val QUICK_RESPONSE_WINDOW_MS = 30_000L

/** Tier 1: NotificationListenerService is an official Android API, opt-in via system settings. */
@AndroidEntryPoint
class NuaNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var statsStore: NotificationStatsStore

    private val postedAtByKey = mutableMapOf<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        val entries = activeNotifications?.mapNotNull { it.toEntryOrNull() } ?: emptyList()
        notificationRepository.replaceAll(entries)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val entry = sbn.toEntryOrNull() ?: return
        postedAtByKey[sbn.key] = System.currentTimeMillis()
        statsStore.recordPosted(entry.packageName)
        notificationRepository.upsert(entry)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        val postedAt = postedAtByKey.remove(sbn.key)
        if (postedAt != null && System.currentTimeMillis() - postedAt < QUICK_RESPONSE_WINDOW_MS) {
            statsStore.recordQuickDismiss(sbn.packageName)
        }
        notificationRepository.remove(sbn.key)
    }

    private fun StatusBarNotification.toEntryOrNull(): NotificationEntry? {
        if (packageName == applicationContext.packageName) return null // ignore NUA's own notifications
        return NotificationEntry(
            key = key,
            packageName = packageName,
            title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postTimeMillis = postTime,
            category = notification.category,
            priority = notification.priority,
            replyAction = notification.findReplyAction(),
        )
    }

    /** The first action with a free-text RemoteInput — e.g. WhatsApp/Messages' inline "Reply" action. */
    private fun Notification.findReplyAction(): NotificationReplyAction? {
        actions?.forEach { action ->
            val resultKey = action.remoteInputs?.firstOrNull { !it.resultKey.isNullOrBlank() }?.resultKey
            if (resultKey != null) {
                return NotificationReplyAction(pendingIntent = action.actionIntent, remoteInputResultKey = resultKey)
            }
        }
        return null
    }
}
