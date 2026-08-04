package com.nua.assistant.services

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nua.assistant.BuildConfig
import com.nua.assistant.R
import dagger.hilt.android.AndroidEntryPoint

private const val TAG = "NuaForegroundService"
private const val NOTIFICATION_CHANNEL_ID = "nua_wake_word"
private const val FOREGROUND_NOTIFICATION_ID = 1001

/** Broadcast when the wake word is detected; MainActivity listens for this to start a voice turn. */
const val ACTION_WAKE_WORD_DETECTED = "com.nua.assistant.ACTION_WAKE_WORD_DETECTED"

/**
 * Tier 1: wake-word listening via Porcupine's built-in "Jarvis" keyword — a foreground
 * service with a persistent notification while it's running, not accessibility
 * automation. No-ops (logs and stops itself) if no Picovoice access key is configured;
 * this is opt-in from the settings/onboarding flow, not something that silently fails.
 */
@AndroidEntryPoint
class NuaForegroundService : Service() {

    private var porcupineManager: PorcupineManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification())
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (porcupineManager != null) return

        if (BuildConfig.PICOVOICE_ACCESS_KEY.isBlank()) {
            Log.w(TAG, "No Picovoice access key configured; wake-word listening is disabled.")
            stopSelf()
            return
        }

        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .build(
                    applicationContext,
                    PorcupineManagerCallback { _ ->
                        sendBroadcast(Intent(ACTION_WAKE_WORD_DETECTED).setPackage(packageName))
                    },
                )
            porcupineManager?.start()
        } catch (t: Exception) {
            Log.e(TAG, "Failed to start wake-word listening", t)
            stopSelf()
        }
    }

    override fun onDestroy() {
        porcupineManager?.stop()
        porcupineManager?.delete()
        porcupineManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.foreground_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.foreground_service_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
}
