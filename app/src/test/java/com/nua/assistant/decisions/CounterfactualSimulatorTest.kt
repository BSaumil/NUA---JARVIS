package com.nua.assistant.decisions

import com.nua.assistant.memory.DecisionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun decision(outcome: String? = "it worked out", options: String? = "option B, option C") = DecisionEntity(
    decision = "chose option A",
    outcome = outcome,
    options = options,
)

class CounterfactualSimulatorTest {

    @Test
    fun `a decision with a recorded outcome and recorded alternatives is eligible`() {
        assertNull(counterfactualIneligibilityReasonFor(decision()))
    }

    @Test
    fun `a decision with no outcome recorded yet is ineligible -- nothing to contrast against`() {
        val reason = counterfactualIneligibilityReasonFor(decision(outcome = null))
        assertEquals(
            "No outcome recorded yet for this decision — record what actually happened before exploring what else might have.",
            reason,
        )
    }

    @Test
    fun `a decision with a blank outcome is treated the same as no outcome`() {
        assertEquals(
            "No outcome recorded yet for this decision — record what actually happened before exploring what else might have.",
            counterfactualIneligibilityReasonFor(decision(outcome = "   ")),
        )
    }

    @Test
    fun `a decision with no recorded alternatives is ineligible -- never fabricate an option nobody considered`() {
        val reason = counterfactualIneligibilityReasonFor(decision(options = null))
        assertEquals("No alternatives were recorded for this decision — there's nothing else to speculate about.", reason)
    }

    @Test
    fun `a decision with a blank options string is treated the same as no alternatives`() {
        assertEquals(
            "No alternatives were recorded for this decision — there's nothing else to speculate about.",
            counterfactualIneligibilityReasonFor(decision(options = "")),
        )
    }

    @Test
    fun `missing outcome is reported before missing options when both are absent, so the person fixes one thing at a time`() {
        val reason = counterfactualIneligibilityReasonFor(decision(outcome = null, options = null))
        assertEquals(
            "No outcome recorded yet for this decision — record what actually happened before exploring what else might have.",
            reason,
        )
    }
}
