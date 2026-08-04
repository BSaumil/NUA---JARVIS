package com.nua.assistant.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "nua_notification_stats"

/**
 * Lightweight per-app interaction counters backing NotificationPriorityScorer's
 * "apps historically responded to quickly" signal. Plain SharedPreferences (not Room,
 * not encrypted) — this is just counts, nothing sensitive, and doesn't belong in the
 * messages/user_facts schema in memory/.
 */
@Singleton
class NotificationStatsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordPosted(packageName: String) {
        val key = countKey(packageName)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun recordQuickDismiss(packageName: String) {
        val key = quickDismissKey(packageName)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    /** Fraction of this app's notifications historically dismissed within the quick-response window. */
    fun quickResponseRate(packageName: String): Double {
        val total = prefs.getInt(countKey(packageName), 0)
        if (total == 0) return 0.0
        val quick = prefs.getInt(quickDismissKey(packageName), 0)
        return quick.toDouble() / total
    }

    private fun countKey(packageName: String) = "count_$packageName"
    private fun quickDismissKey(packageName: String) = "quick_$packageName"
}
