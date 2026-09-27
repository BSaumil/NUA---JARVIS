package com.nua.assistant.automation.uaf

import com.nua.assistant.automation.NuaRouteResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs an [ActionPlan] to completion or to its next pause point (an unmet dependency
 * cycle rejection, an [FailurePolicy.ASK_USER] pause, a [FailurePolicy.STOP]/
 * [FailurePolicy.COMPENSATE] halt). Every step — including a compensation step — goes
 * through the exact same [isAuthorizationSufficient] check and the exact same
 * [ActionAdapter] dispatch as any other; nothing here is a second way to execute a
 * capability without its own descriptor's policy applying.
 *
 * [run] takes [initialState] rather than owning mutable state itself: the caller is
 * responsible for persisting the returned [PlanRunState] and passing it back in to resume
 * after a process death, exactly the "recompute what's next from the last checkpoint"
 * contract [nextRunnableStep] documents.
 */
@Singleton
class WorkflowExecutor @Inject constructor(
    private val capabilityRegistry: CapabilityRegistry,
    private val adapters: Map<ExecutionAdapterType, @JvmSuppressWildcards ActionAdapter>,
) {
    suspend fun run(
        plan: ActionPlan,
        context: AdapterExecutionContext,
        initialState: PlanRunState = PlanRunState(plan.id),
    ): PlanRunState {
        checkNotNull(topologicalOrder(plan)) { "ActionPlan ${plan.id} contains a dependency cycle" }
        var state = initialState
        while (true) {
            val step = nextRunnableStep(plan, state) ?: return state
            val outcome = runOneStep(step, context)
            state = state.withOutcome(outcome)

            if (outcome.state == StepOutcomeState.AWAITING_USER) return state
            if (outcome.state == StepOutcomeState.SUCCEEDED) continue

            // FAILED or AUTHORIZATION_REFUSED.
            if (shouldCompensate(step)) {
                val compensationStep = plan.steps.first { it.id == step.compensationStepId }
                val compensationOutcome = runOneStep(compensationStep, context)
                val recorded = if (compensationOutcome.state == StepOutcomeState.SUCCEEDED) {
                    compensationOutcome.copy(state = StepOutcomeState.COMPENSATED)
                } else {
                    compensationOutcome
                }
                return state.withOutcome(recorded)
            }
            if (shouldStopPlanAfterFailure(step)) return state
            // FailurePolicy.SKIP: keep going. Anything genuinely depending on this step
            // never becomes eligible (FAILED/AUTHORIZATION_REFUSED aren't dependency-
            // satisfying states), so the loop naturally leaves those steps unattempted
            // rather than running them against a failed prerequisite.
        }
    }

    private suspend fun runOneStep(step: PlanStep, context: AdapterExecutionContext): StepOutcome {
        val descriptor = capabilityRegistry.forAction(step.action)
            ?: return StepOutcome(step.id, StepOutcomeState.FAILED, "No capability registered for ${step.action}")

        if (!isAuthorizationSufficient(descriptor.confirmation, step.authorizationProof)) {
            return StepOutcome(step.id, StepOutcomeState.AUTHORIZATION_REFUSED, "Authorization required but not supplied")
        }

        val adapterType = adapterTypeFor(descriptor, step.authorizationProof)
        val adapter = adapters[adapterType]
            ?: return StepOutcome(step.id, StepOutcomeState.FAILED, "No adapter registered for $adapterType")

        val result = runCatching { adapter.execute(descriptor, step.parameters, context) }.getOrNull()
        val succeeded = result is NuaRouteResult.ActionTaken && result.succeeded
        val message = (result as? NuaRouteResult.ActionTaken)?.message
        return StepOutcome(step.id, if (succeeded) StepOutcomeState.SUCCEEDED else outcomeForFailedStep(step), message)
    }

    /**
     * Once a sensitive step's authorization is already proven, prefer a fallback adapter
     * that actually performs the sanctioned mechanism over the preferred one — for
     * [com.nua.assistant.ai.NuaActionType.REPLY_TO_NOTIFICATION] specifically,
     * [LocalNativeAdapter] only *proposes* a reply (see [CapabilityDescriptor]'s doc
     * comment); re-running it after confirmation would re-propose instead of sending.
     */
    private fun adapterTypeFor(descriptor: CapabilityDescriptor, proof: AuthorizationProof): ExecutionAdapterType =
        if (descriptor.confirmation == ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE &&
            proof is AuthorizationProof.UserConfirmed &&
            descriptor.fallbackAdapters.isNotEmpty()
        ) {
            descriptor.fallbackAdapters.first()
        } else {
            descriptor.preferredAdapter
        }
}
