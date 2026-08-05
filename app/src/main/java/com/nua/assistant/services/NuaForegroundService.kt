package com.nua.assistant.services

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

/** Broadcast when a wake phrase is detected; MainActivity listens for this to start a voice turn. */
const val ACTION_WAKE_WORD_DETECTED = "com.nua.assistant.ACTION_WAKE_WORD_DETECTED"

/** Extra on [ACTION_WAKE_WORD_DETECTED] carrying the id (WakePhrase.id) of the phrase that fired. */
const val EXTRA_WAKE_PHRASE_ID = "com.nua.assistant.EXTRA_WAKE_PHRASE_ID"

/**
 * Tier 1: wake-word listening via Porcupine — a foreground service with a persistent
 * notification while it's running, not accessibility automation. Listens for every
 * phrase in WakePhrases.ALL that's actually available (see [availableWakePhrases]):
 * "Jarvis" works immediately since it's a Porcupine built-in, the rest activate once
 * their trained model is dropped into assets/ (see app/src/main/assets/README.md).
 * No-ops (logs and stops itself) if no Picovoice access key is configured; this is
 * opt-in from the settings/onboarding flow, not something that silently fails.
 */
@AndroidEntryPoint
class NuaForegroundService : Service() {

    private var porcupineManager: PorcupineManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val activePhrases = availableWakePhrases(applicationContext)
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(activePhrases))
        startListening(activePhrases)
        return START_STICKY
    }

    private fun startListening(activePhrases: List<WakePhrase>) {
        if (porcupineManager != null) return

        if (BuildConfig.PICOVOICE_ACCESS_KEY.isBlank()) {
            Log.w(TAG, "No Picovoice access key configured; wake-word listening is disabled.")
            stopSelf()
            return
        }

        val builtIns = activePhrases.filter { it.source is WakePhraseSource.BuiltIn }
        val customs = activePhrases.filter { it.source is WakePhraseSource.CustomAsset }
        // Must match the order the two lists are handed to the builder below —
        // PorcupineManagerCallback's keywordIndex refers to a position in that combined order.
        val orderedActivePhrases = builtIns + customs

        if (orderedActivePhrases.isEmpty()) {
            Log.w(TAG, "No wake phrases available (no built-ins and no trained models in assets/).")
            stopSelf()
            return
        }

        try {
            val builder = PorcupineManager.Builder().setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY)
            if (builtIns.isNotEmpty()) {
                builder.setKeywords(builtIns.map { (it.source as WakePhraseSource.BuiltIn).keyword }.toTypedArray())
            }
            if (customs.isNotEmpty()) {
                builder.setKeywordPaths(customs.map { (it.source as WakePhraseSource.CustomAsset).assetFileName }.toTypedArray())
            }

            porcupineManager = builder.build(
                applicationContext,
                PorcupineManagerCallback { keywordIndex ->
                    val phrase = orderedActivePhrases.getOrNull(keywordIndex)
                    Log.i(TAG, "Wake phrase detected: ${phrase?.displayText ?: "unknown (index $keywordIndex)"}")
                    sendBroadcast(
                        Intent(ACTION_WAKE_WORD_DETECTED)
                            .putExtra(EXTRA_WAKE_PHRASE_ID, phrase?.id)
                            .setPackage(packageName),
                    )
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

    private fun buildNotification(activePhrases: List<WakePhrase>): Notification {
        val contentText = if (activePhrases.isEmpty()) {
            getString(R.string.foreground_service_notification_text_idle)
        } else {
            "${getString(R.string.foreground_service_notification_text_prefix)} ${activePhrases.joinToString(", ") { it.displayText }}"
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }
}
