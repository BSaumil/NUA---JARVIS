package com.nua.assistant.security

import com.nua.assistant.trust.AutonomyTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepUpPolicyTest {

    @Test
    fun `read-only, automatic, and notify-execute tiers do not require step-up`() {
        assertFalse(requiresStepUpAuth(AutonomyTier.T0))
        assertFalse(requiresStepUpAuth(AutonomyTier.T1))
        assertFalse(requiresStepUpAuth(AutonomyTier.T2))
    }

    @Test
    fun `ask-first tiers and above require step-up`() {
        assertTrue(requiresStepUpAuth(AutonomyTier.T3))
        assertTrue(requiresStepUpAuth(AutonomyTier.T4))
        assertTrue(requiresStepUpAuth(AutonomyTier.T5))
    }
}
