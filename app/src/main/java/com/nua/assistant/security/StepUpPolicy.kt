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

/**
 * Whether a new step-up `BiometricPrompt` may be started. A gated confirmation dialog can
 * trigger its confirm action from two independent places while still on screen — a
 * `LaunchedEffect` auto-triggering an auto-approved action, and a manual tap on the same
 * dialog's confirm button — and `BiometricPrompt` has no documented support for concurrent
 * sessions on one `Activity`. Callers must not start a second prompt while one is already
 * in flight for the same gated action.
 */
fun mayStartStepUp(promptAlreadyInFlight: Boolean): Boolean = !promptAlreadyInFlight
