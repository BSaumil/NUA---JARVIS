package com.nua.assistant.security

import com.nua.assistant.trust.AutonomyTier

/**
 * Whether an action at this autonomy tier should be gated behind step-up authentication
 * (see [BiometricGate]) before it executes. T3+ actions already require an explicit,
 * in-the-moment user confirmation — step-up adds a second factor to those specifically,
 * rather than to T0-T2 actions that are read-only, trivially undoable, or already
 * execute-then-report.
 */
fun requiresStepUpAuth(tier: AutonomyTier): Boolean = when (tier) {
    AutonomyTier.T0, AutonomyTier.T1, AutonomyTier.T2 -> false
    AutonomyTier.T3, AutonomyTier.T4, AutonomyTier.T5 -> true
}
