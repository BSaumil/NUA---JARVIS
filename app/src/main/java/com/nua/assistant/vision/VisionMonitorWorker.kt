package com.nua.assistant.vision

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nua.assistant.R
import com.nua.assistant.memory.VisionMonitorEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val NOTIFICATION_CHANNEL_ID = "nua_vision_monitor"
private const val NOTIFICATION_ID_BASE = 3000

/**
 * Runs daily and reminds the user about any vision monitor whose interval has elapsed.
 * NUA can't take photos on its own — capture is delegated to the system camera app — so
 * "monitoring" here means prompting the user for a fresh photo to compare against the
 * recorded baseline, never autonomous surveillance. Monitors only exist because the user
 * explicitly asked to watch something (see NuaViewModel.startMonitoringLastVisionResult),
 * so this worker itself needs no separate permission gate.
 */
@HiltWorker
class VisionMonitorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val visionMonitorRepository: VisionMonitorRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val due = visionMonitorRepository.due()
        if (due.isNotEmpty()) {
            createChannelIfNeeded()
            due.forEach { monitor -> postReminder(monitor) }
        }
        return Result.success()
    }

    private fun postReminder(monitor: VisionMonitorEntity) {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Time to check on ${monitor.subject}")
            .setContentText("Snap a new photo and NUA will tell you what's changed.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_BASE + monitor.id.toInt(), notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            applicationContext.getString(R.string.vision_monitor_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
