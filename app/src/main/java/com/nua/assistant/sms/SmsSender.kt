package com.nua.assistant.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends a plain SMS via Android's built-in SmsManager — the official on-device API, no
 * OAuth, no carrier account of NUA's own. Only ever called after the user has explicitly
 * confirmed the exact recipient and text (see NuaViewModel.confirmPendingSms) — sending a
 * message on someone's behalf is sensitive, same as replying to a notification.
 */
@Singleton
class SmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun send(phoneNumber: String, message: String): Boolean {
        if (!hasPermission()) return false
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            true
        } catch (t: Exception) {
            false
        }
    }
}
