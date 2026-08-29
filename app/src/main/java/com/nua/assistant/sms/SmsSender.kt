package com.nua.assistant.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.nua.assistant.trust.ActionOutcomeState
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

    /**
     * [ActionOutcomeState.ACCEPTED], never COMPLETED, on the clean path:
     * `sendMultipartTextMessage` is fire-and-forget across a process boundary — a call that
     * doesn't throw means the OS accepted the request, not that the SMS was delivered.
     */
    fun send(phoneNumber: String, message: String): ActionOutcomeState {
        if (!hasPermission()) return ActionOutcomeState.FAILED
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            ActionOutcomeState.ACCEPTED
        } catch (t: Exception) {
            ActionOutcomeState.FAILED
        }
    }
}
