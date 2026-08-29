package com.nua.assistant.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import com.nua.assistant.trust.ActionOutcomeState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends a reply through a notification's own quick-reply action — the same official
 * mechanism a wearable or Assistant uses, not accessibility automation. Only works for
 * notifications that expose a RemoteInput action in the first place (most messaging
 * apps do); see NuaNotificationListenerService for where that gets captured.
 */
@Singleton
class NotificationReplySender @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * [ActionOutcomeState.ACCEPTED], never COMPLETED, on the clean path: `PendingIntent.send()`
     * is fire-and-forget — a call that doesn't throw means the target app's action was
     * invoked, not that it actually posted the reply.
     */
    fun sendReply(action: NotificationReplyAction, text: String): ActionOutcomeState {
        return try {
            val fillInIntent = Intent()
            val resultsBundle = Bundle().apply { putCharSequence(action.remoteInputResultKey, text) }
            RemoteInput.addResultsToIntent(
                arrayOf(RemoteInput.Builder(action.remoteInputResultKey).build()),
                fillInIntent,
                resultsBundle,
            )
            action.pendingIntent.send(context, 0, fillInIntent)
            ActionOutcomeState.ACCEPTED
        } catch (e: PendingIntent.CanceledException) {
            ActionOutcomeState.FAILED
        }
    }
}
