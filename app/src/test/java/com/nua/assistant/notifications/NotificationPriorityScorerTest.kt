package com.nua.assistant.notifications

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeNotificationStatsProvider(private val rates: Map<String, Double> = emptyMap()) : NotificationStatsProvider {
    override fun quickResponseRate(packageName: String): Double = rates[packageName] ?: 0.0
}

class NotificationPriorityScorerTest {

    private fun entry(
        packageName: String = "com.example.app",
        category: String? = null,
        priority: Int = Notification.PRIORITY_DEFAULT,
    ) = NotificationEntry(
        key = "key",
        packageName = packageName,
        title = "title",
        text = "text",
        postTimeMillis = 0L,
        category = category,
        priority = priority,
    )

    @Test
    fun `a call notification needs attention`() {
        val scorer = NotificationPriorityScorer(FakeNotificationStatsProvider())
        val result = scorer.score(entry(category = Notification.CATEGORY_CALL))
        assertTrue(result.needsAttention)
    }

    @Test
    fun `a message notification needs attention`() {
        val scorer = NotificationPriorityScorer(FakeNotificationStatsProvider())
        val result = scorer.score(entry(category = Notification.CATEGORY_MESSAGE))
        assertTrue(result.needsAttention)
    }

    @Test
    fun `high declared priority needs attention`() {
        val scorer = NotificationPriorityScorer(FakeNotificationStatsProvider())
        val result = scorer.score(entry(priority = Notification.PRIORITY_HIGH))
        assertTrue(result.needsAttention)
    }

    @Test
    fun `a messaging app package name needs attention`() {
        val scorer = NotificationPriorityScorer(FakeNotificationStatsProvider())
        val result = scorer.score(entry(packageName = "com.whatsapp.messaging"))
        assertTrue(result.needsAttention)
    }

    @Test
    fun `high historical quick-response rate needs attention`() {
        val scorer = NotificationPriorityScorer(FakeNotificationStatsProvider(mapOf("com.example.app" to 0.75)))
        val result = scorer.score(entry())
        assertTrue(result.needsAttention)
    }

    @Test
    fun `a plain low-signal notification can wait`() {
        val scorer = NotificationPriorityScorer(FakeNotificationStatsProvider(mapOf("com.example.app" to 0.1)))
        val result = scorer.score(entry())
        assertFalse(result.needsAttention)
    }
}
