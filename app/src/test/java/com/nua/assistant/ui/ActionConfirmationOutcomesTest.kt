package com.nua.assistant.ui

import com.nua.assistant.trust.ActionOutcomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionConfirmationOutcomesTest {

    // -------------------------------------------------------------------------------
    // smsConfirmationMessage / replyConfirmationMessage — the fire-and-forget cases:
    // ACCEPTED must never read as an unqualified "worked," and the two known FAILED
    // reasons (no permission, everything else) must stay distinguishable.
    // -------------------------------------------------------------------------------

    @Test
    fun `sms ACCEPTED reads as handed off, not confirmed delivered`() {
        val message = smsConfirmationMessage(ActionOutcomeState.ACCEPTED, hasPermission = true, contactName = "Sam")
        assertTrue(message.contains("Sam"))
        assertTrue("must not claim delivery was confirmed", message.contains("can only confirm"))
    }

    @Test
    fun `sms FAILED without permission names the real reason`() {
        val message = smsConfirmationMessage(ActionOutcomeState.FAILED, hasPermission = false, contactName = "Sam")
        assertTrue(message.contains("permission"))
    }

    @Test
    fun `sms FAILED with permission present is a generic failure, not a permission claim`() {
        val message = smsConfirmationMessage(ActionOutcomeState.FAILED, hasPermission = true, contactName = "Sam")
        assertTrue(message.contains("didn't go through"))
    }

    @Test
    fun `reply ACCEPTED reads as handed off, not confirmed delivered`() {
        val message = replyConfirmationMessage(ActionOutcomeState.ACCEPTED, notificationTitle = "Jamie")
        assertTrue(message.contains("Jamie"))
        assertTrue(message.contains("can only confirm"))
    }

    @Test
    fun `reply FAILED reads as not going through`() {
        val message = replyConfirmationMessage(ActionOutcomeState.FAILED, notificationTitle = "Jamie")
        assertTrue(message.contains("didn't go through"))
    }

    // -------------------------------------------------------------------------------
    // planConfirmationOutcome / planConfirmationMessage — the sharpest of the three
    // bugs this seam fixes: NuaViewModel.executeConfirmedPlan used to report "Done"
    // and record a success regardless of how many of a plan's reminders actually saved.
    // -------------------------------------------------------------------------------

    private fun ok(): Result<Long> = Result.success(1L)
    private fun bad(): Result<Long> = Result.failure(IllegalStateException("boom"))

    @Test
    fun `no reminders in the plan counts as completed, not failed`() {
        assertEquals(ActionOutcomeState.COMPLETED, planConfirmationOutcome(emptyList()))
        assertEquals("Done — that plan didn't need any reminders.", planConfirmationMessage(emptyList()))
    }

    @Test
    fun `every reminder saving is COMPLETED with an unqualified Done`() {
        assertEquals(ActionOutcomeState.COMPLETED, planConfirmationOutcome(listOf(ok(), ok())))
        assertEquals("Done — I've added reminders for that.", planConfirmationMessage(listOf(ok(), ok())))
    }

    @Test
    fun `every reminder failing is FAILED and says nothing was added`() {
        assertEquals(ActionOutcomeState.FAILED, planConfirmationOutcome(listOf(bad(), bad())))
        assertEquals("That plan's reminders didn't save — nothing was added.", planConfirmationMessage(listOf(bad(), bad())))
    }

    @Test
    fun `a partial batch is FAILED, not COMPLETED — this is the bug this seam fixes`() {
        // Before this fix, executeConfirmedPlan ignored TaskPlanner.confirmPlan's
        // per-reminder Result entirely and always reported "Done" / succeeded=true.
        val results = listOf(ok(), bad(), ok())
        assertEquals(ActionOutcomeState.FAILED, planConfirmationOutcome(results))
        assertEquals("Added 2 of 3 reminders — the rest didn't save.", planConfirmationMessage(results))
    }
}
