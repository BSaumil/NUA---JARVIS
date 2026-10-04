package com.nua.assistant.ui

import com.nua.assistant.ai.PlannedStep
import com.nua.assistant.ai.TaskPlan
import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.notifications.NotificationEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val PLAN = TaskPlan("Plan a trip", listOf(PlannedStep("Book flights", "for next week")))
private val NOTIFICATION = NotificationEntry(
    key = "k", packageName = "com.example", title = "Alex", text = "hey", postTimeMillis = 0, category = null, priority = 0,
)
private val REPLY = NuaRouteResult.ReplyProposed(NOTIFICATION, "On my way")
private val SMS = NuaRouteResult.SmsProposed("Alex", "+15551234567", "On my way")

private val ALL_PROPOSALS = listOf(PendingProposal.Plan(PLAN), PendingProposal.Reply(REPLY), PendingProposal.Sms(SMS))

class PendingActionTest {

    // -----------------------------------------------------------------------------------
    // The security property: auto-approval must never bypass the pending/step-up path.
    //
    // This is the exact defect an architecture review found after the command-palette
    // remediation — sendMessage used to call executeConfirmed* directly when a T3/T4
    // action's type was auto-approved, which meant NO pending* field was ever set, which
    // meant rememberStepUpGatedAction (the only place biometric step-up runs) never ran.
    // A step-up-gated action executed with no identity check at all.
    // -----------------------------------------------------------------------------------

    @Test
    fun `auto-approval never suppresses the pending field the confirmation dialog depends on`() {
        ALL_PROPOSALS.forEach { proposal ->
            listOf(true, false).forEach { autoApproved ->
                val effect = pendingEffectFor(proposal, autoApproved)
                val populated = listOfNotNull(effect.pendingPlan, effect.pendingReply, effect.pendingSms)
                assertEquals(
                    "proposal=$proposal autoApproved=$autoApproved: exactly one pending field must be set " +
                        "regardless of auto-approval, or step-up never runs",
                    1, populated.size,
                )
            }
        }
    }

    @Test
    fun `auto-approval only changes whether the tap is skipped, not what is pending`() {
        ALL_PROPOSALS.forEach { proposal ->
            val approved = pendingEffectFor(proposal, autoApproved = true)
            val manual = pendingEffectFor(proposal, autoApproved = false)
            assertEquals("$proposal: the pending payload itself must be identical either way",
                approved.copy(autoApprovedPending = false), manual)
        }
    }

    @Test
    fun `each proposal populates exactly its own field`() {
        val plan = pendingEffectFor(PendingProposal.Plan(PLAN), autoApproved = false)
        assertNotNull(plan.pendingPlan); assertNull(plan.pendingReply); assertNull(plan.pendingSms)

        val reply = pendingEffectFor(PendingProposal.Reply(REPLY), autoApproved = false)
        assertNull(reply.pendingPlan); assertNotNull(reply.pendingReply); assertNull(reply.pendingSms)

        val sms = pendingEffectFor(PendingProposal.Sms(SMS), autoApproved = false)
        assertNull(sms.pendingPlan); assertNull(sms.pendingReply); assertNotNull(sms.pendingSms)
    }

    @Test
    fun `autoApprovedPending mirrors the flag passed in, for every proposal type`() {
        ALL_PROPOSALS.forEach { proposal ->
            assertTrue(pendingEffectFor(proposal, autoApproved = true).autoApprovedPending)
            assertEquals(false, pendingEffectFor(proposal, autoApproved = false).autoApprovedPending)
        }
    }

    @Test
    fun `pendingEffectFor is pure`() {
        ALL_PROPOSALS.forEach { proposal ->
            assertEquals(pendingEffectFor(proposal, true), pendingEffectFor(proposal, true))
            assertEquals(pendingEffectFor(proposal, false), pendingEffectFor(proposal, false))
        }
    }

    // -----------------------------------------------------------------------------------
    // recipientFor -- the Contextual Autonomy Contracts (Feature 5) recipient dimension.
    // -----------------------------------------------------------------------------------

    @Test
    fun `recipientFor extracts the phone number for an Sms proposal`() {
        assertEquals("+15551234567", recipientFor(PendingProposal.Sms(SMS)))
    }

    @Test
    fun `recipientFor extracts the notification title for a Reply proposal`() {
        assertEquals("Alex", recipientFor(PendingProposal.Reply(REPLY)))
    }

    @Test
    fun `recipientFor is null for a Plan proposal -- no single recipient concept applies`() {
        assertNull(recipientFor(PendingProposal.Plan(PLAN)))
    }
}
