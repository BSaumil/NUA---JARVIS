package com.nua.assistant.briefing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "nua_briefing_schedule"
private const val KEY_ENABLED = "enabled"
private const val KEY_HOUR = "hour"
private const val KEY_MINUTE = "minute"

data class BriefingSchedule(val enabled: Boolean, val hour: Int, val minute: Int)

@Singleton
class BriefingScheduleStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): BriefingSchedule = BriefingSchedule(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        hour = prefs.getInt(KEY_HOUR, DEFAULT_HOUR),
        minute = prefs.getInt(KEY_MINUTE, 0),
    )

    fun set(schedule: BriefingSchedule) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, schedule.enabled)
            .putInt(KEY_HOUR, schedule.hour)
            .putInt(KEY_MINUTE, schedule.minute)
            .apply()
    }

    private companion object {
        const val DEFAULT_HOUR = 7
    }
}
