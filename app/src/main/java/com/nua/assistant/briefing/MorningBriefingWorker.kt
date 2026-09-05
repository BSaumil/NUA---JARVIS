package com.nua.assistant.briefing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nua.assistant.R
import com.nua.assistant.memory.MemoryDao
import com.nua.assistant.memory.MessageEntity
import com.nua.assistant.memory.MessageRole
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val NOTIFICATION_CHANNEL_ID = "nua_morning_briefing"
private const val NOTIFICATION_ID = 2001

/**
 * Runs the same MorningBriefing.generate() the chat flow uses, then surfaces it as a
 * notification and drops it into the conversation history as an assistant message, so
 * it's there the next time the user opens the app either way. Deliberately doesn't
 * speak it via TTS unprompted — playing audio from a background worker at, say, 7am
 * with no user interaction in progress is more surprising than helpful; a notification
 * is the standard, predictable way to deliver a proactive briefing.
 */
@HiltWorker
class MorningBriefingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val morningBriefing: MorningBriefing,
    private val memoryDao: MemoryDao,
    private val scheduleStore: BriefingScheduleStore,
    private val scheduler: BriefingScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val briefing = morningBriefing.generate()
        val briefingText = briefing.renderText()
        memoryDao.insertMessage(MessageEntity(role = MessageRole.ASSISTANT, content = briefingText))
        postNotification(briefingText)

        val schedule = scheduleStore.get()
        if (schedule.enabled) scheduler.scheduleNext(schedule.hour, schedule.minute)

        return Result.success()
    }

    private fun postNotification(text: String) {
        createChannelIfNeeded()
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            applicationContext.getString(R.string.morning_briefing_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
