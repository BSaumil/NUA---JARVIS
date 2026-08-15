package com.nua.assistant.documents

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nua.assistant.R
import com.nua.assistant.memory.DocumentEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Locale

private const val NOTIFICATION_CHANNEL_ID = "nua_document_expiry"
private const val NOTIFICATION_ID_BASE = 4000
private const val REMINDER_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

/**
 * Runs daily and reminds the user about any document expiring within 30 days (or already
 * past its date) that hasn't been reminded about yet.
 */
@HiltWorker
class DocumentExpiryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val documentRepository: DocumentRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val due = documentRepository.documentsPendingExpiryReminder()
            .filter { doc -> doc.expiryDate != null && doc.expiryDate <= now + REMINDER_WINDOW_MS }
        if (due.isNotEmpty()) createChannelIfNeeded()
        due.forEach { document ->
            postReminder(document)
            documentRepository.markExpiryReminded(document.id)
        }
        return Result.success()
    }

    private fun postReminder(document: DocumentEntity) {
        val dateText = document.expiryDate?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it) }
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("${document.fileName} expires soon")
            .setContentText(if (dateText != null) "Due $dateText" else "Coming up soon")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_BASE + document.id.toInt(), notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            applicationContext.getString(R.string.document_expiry_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
