package com.nua.assistant.ui

import com.nua.assistant.trust.ActionOutcomeState

/**
 * What confirmPendingSms/Reply/Plan report to the user and to the Trust Engine, pulled
 * out of NuaViewModel so it's unit-testable on the JVM — NuaViewModel itself can't be
 * constructed outside Android (see TrustUiControllerTest's doc comment for why).
 *
 * Before this extraction, all three confirm functions collapsed "the platform accepted a
 * fire-and-forget send request" (SmsSender/NotificationReplySender) or "some, but not all,
 * of a plan's reminders were created" (TaskPlanner.confirmPlan) into a flat succeeded/
 * failed boolean — a plan where 1 of 3 reminders saved was reported to the user as "Done"
 * and recorded in the Trust Engine's audit trail as an unqualified success.
 */

/** Shown instead of actually resending when TrustRepository.wasRecentlyExecuted finds this
 *  exact SMS/reply was already sent within the idempotency window — see trust/IdempotencyKey.kt. */
const val DUPLICATE_SMS_SUPPRESSED_MESSAGE = "Already sent that a moment ago — not sending it twice."
const val DUPLICATE_REPLY_SUPPRESSED_MESSAGE = "Already sent that reply a moment ago — not sending it twice."
const val DUPLICATE_PLAN_SUPPRESSED_MESSAGE = "Already confirmed that plan a moment ago — not adding its reminders twice."

fun smsConfirmationMessage(outcome: ActionOutcomeState, hasPermission: Boolean, contactName: String): String =
    when {
        outcome == ActionOutcomeState.ACCEPTED ->
            "Sent — texted $contactName. (NUA can only confirm it was handed off, not that it was delivered.)"
        !hasPermission -> "Couldn't send that — NUA doesn't have permission to send texts yet."
        else -> "That text didn't go through."
    }

fun replyConfirmationMessage(outcome: ActionOutcomeState, notificationTitle: String): String =
    when (outcome) {
        ActionOutcomeState.ACCEPTED ->
            "Sent — replied to $notificationTitle. (NUA can only confirm it was handed off, not that it was delivered.)"
        else -> "That reply didn't go through — the notification may have been dismissed."
    }

/** [results] is one entry per reminder TaskPlanner.confirmPlan tried to create. */
fun planConfirmationOutcome(results: List<Result<Long>>): ActionOutcomeState =
    if (results.isEmpty() || results.all { it.isSuccess }) ActionOutcomeState.COMPLETED else ActionOutcomeState.FAILED

fun planConfirmationMessage(results: List<Result<Long>>): String {
    if (results.isEmpty()) return "Done — that plan didn't need any reminders."
    val succeeded = results.count { it.isSuccess }
    return when (succeeded) {
        results.size -> "Done — I've added reminders for that."
        0 -> "That plan's reminders didn't save — nothing was added."
        else -> "Added $succeeded of ${results.size} reminders — the rest didn't save."
    }
}
