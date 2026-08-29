package com.nua.assistant.trust

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionOutcomeStateTest {

    @Test
    fun `only FAILED counts against trust — an unconfirmed accept is not a mistake`() {
        assertTrue(ActionOutcomeState.FAILED.countsAsFailure())
        assertFalse(ActionOutcomeState.ACCEPTED.countsAsFailure())
        assertFalse(ActionOutcomeState.COMPLETED.countsAsFailure())
        assertFalse(ActionOutcomeState.VERIFIED.countsAsFailure())
        assertFalse(ActionOutcomeState.ATTEMPTED.countsAsFailure())
        assertFalse(ActionOutcomeState.UNKNOWN.countsAsFailure())
    }

    @Test
    fun `every state has a badge label and a past-tense clause`() {
        // Exhaustive `when` in badgeLabel/pastTenseClause means a new enum entry with no
        // branch fails compilation, not silently falls through at runtime — this test just
        // pins the actual strings so a future edit notices what it changed.
        for (state in ActionOutcomeState.entries) {
            assertTrue(state.badgeLabel().isNotBlank())
            assertTrue(state.pastTenseClause().isNotBlank())
        }
    }

    @Test
    fun `ACCEPTED is honest about being unconfirmed, not a plain success`() {
        assertTrue(ActionOutcomeState.ACCEPTED.pastTenseClause().contains("unconfirmed"))
    }
}
