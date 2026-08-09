package com.nua.assistant.briefing

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val UNIQUE_WORK_NAME = "nua_morning_briefing"

/**
 * Schedules MorningBriefingWorker as a one-time WorkRequest timed to the next
 * occurrence of the chosen hour:minute, rather than PeriodicWorkRequest — periodic
 * work's 15-minute flex window doesn't give reliable wall-clock time-of-day firing.
 * The worker re-schedules itself for the following day after each run.
 */
@Singleton
class BriefingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleStore: BriefingScheduleStore,
) {

    fun applySchedule(schedule: BriefingSchedule) {
        scheduleStore.set(schedule)
        if (schedule.enabled) scheduleNext(schedule.hour, schedule.minute) else cancel()
    }

    fun scheduleNext(hour: Int, minute: Int) {
        val request = OneTimeWorkRequestBuilder<MorningBriefingWorker>()
            .setInitialDelay(millisUntilNext(hour, minute), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
