package com.nua.assistant.geofencing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.nua.assistant.memory.GeofenceDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val NOTIFICATION_CHANNEL_ID = "nua_geofence"
private const val NOTIFICATION_ID_BASE = 3000

/**
 * Receives ENTER transitions from GeofencingClient (see GeofenceManager) and posts a
 * notification with the saved reminder text. Uses goAsync() + a background coroutine
 * since BroadcastReceiver.onReceive is synchronous but reading the geofence's message
 * needs a suspend Room query.
 */
@AndroidEntryPoint
class NuaGeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var geofenceDao: GeofenceDao

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val triggeringIds = event.triggeringGeofences?.mapNotNull { it.requestId?.toLongOrNull() }
        if (triggeringIds.isNullOrEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                triggeringIds.forEach { id ->
                    geofenceDao.getById(id)?.let { entity -> postNotification(context, entity.name, entity.message, id) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, name: String, message: String, id: Long) {
        createChannelIfNeeded(context)
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(name)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_BASE + id.toInt(), notification)
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Location reminders", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
