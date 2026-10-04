package com.nua.assistant.automation.uaf

/**
 * Evidence that a step's [ConfirmationPolicy] has been satisfied, supplied by whatever
 * caller assembled the [ActionPlan] — never fabricated inside the fabric itself. This is
 * the Universal Action Fabric's one authorization checkpoint: [WorkflowExecutor] refuses
 * to hand a step to any [ActionAdapter] unless [isAuthorizationSufficient] holds, so a new
 * adapter type can never become a way to skip the confirmation an existing skill already
 * requires — the same "single enforcement point" property
 * [com.nua.assistant.automation.SkillSandbox] already holds for direct dispatch.
 */
sealed class AuthorizationProof {
    /** The step's own [ConfirmationPolicy] is [ConfirmationPolicy.NONE_REQUIRED]; nothing to prove. */
    data object NotRequired : AuthorizationProof()

    /** The user explicitly confirmed this specific step, at [confirmedAtMillis]. */
    data class UserConfirmed(val confirmedAtMillis: Long) : AuthorizationProof()
}

/**
 * Pure: the single rule every step's authorization is checked against before any adapter
 * ever runs it. [ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE] accepts only a real
 * [AuthorizationProof.UserConfirmed] — [AuthorizationProof.NotRequired] is never sufficient
 * for it, so a plan step can't silently downgrade its own descriptor's policy by simply not
 * supplying proof.
 */
fun isAuthorizationSufficient(policy: ConfirmationPolicy, proof: AuthorizationProof): Boolean = when (policy) {
    ConfirmationPolicy.NONE_REQUIRED -> true
    ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE -> proof is AuthorizationProof.UserConfirmed
}
