package com.nua.assistant.ui

import com.nua.assistant.automation.NuaRouteResult

/** A routed proposal that needs the user's go-ahead before it runs — the T3/T4 half of [NuaRouteResult]. */
sealed class PendingProposal {
    data class Plan(val plan: com.nua.assistant.ai.TaskPlan) : PendingProposal()
    data class Reply(val proposed: NuaRouteResult.ReplyProposed) : PendingProposal()
    data class Sms(val proposed: NuaRouteResult.SmsProposed) : PendingProposal()
}

/** The slice of [NuaUiState] one proposal writes. */
data class PendingUiEffect(
    val pendingPlan: com.nua.assistant.ai.TaskPlan? = null,
    val pendingReply: NuaRouteResult.ReplyProposed? = null,
    val pendingSms: NuaRouteResult.SmsProposed? = null,
    val autoApprovedPending: Boolean = false,
)

/**
 * Pure: what a routed proposal should do to pending UI state, given whether its action
 * type has been auto-approved ("Always allow" in Settings).
 *
 * This function is the entire security-relevant surface of the auto-approve bypass found
 * in an architecture review after the command-palette remediation (`aaa5fe2`..`c8339d2`):
 * `sendMessage` used to call `executeConfirmed*` directly when auto-approved, which skipped
 * `pendingPlan`/`pendingReply`/`pendingSms` — and with them `rememberStepUpGatedAction`,
 * the only place biometric step-up is checked — entirely. A T3 SMS or T4 plan, once
 * auto-approved, executed with no identity check at all.
 *
 * [autoApproved] may only ever change how the confirmation prompt is presented (skip the
 * tap — see the `LaunchedEffect` in each `*ConfirmationDialog`), never whether the
 * corresponding pending field is populated. [CommandPaletteTest.kt] and
 * [PendingActionTest.kt] both assert this from different directions: the palette proves
 * nothing above T2 executes without going through this same pending/gated path, and this
 * function proves the path itself can never be short-circuited by auto-approval.
 */
fun pendingEffectFor(proposal: PendingProposal, autoApproved: Boolean): PendingUiEffect = when (proposal) {
    is PendingProposal.Plan -> PendingUiEffect(pendingPlan = proposal.plan, autoApprovedPending = autoApproved)
    is PendingProposal.Reply -> PendingUiEffect(pendingReply = proposal.proposed, autoApprovedPending = autoApproved)
    is PendingProposal.Sms -> PendingUiEffect(pendingSms = proposal.proposed, autoApprovedPending = autoApproved)
}
