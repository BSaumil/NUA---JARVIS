package com.nua.assistant.automation.uaf

import com.nua.assistant.automation.NuaRouteResult
import com.nua.assistant.trust.lineage.LineageEntry
import com.nua.assistant.trust.lineage.LineageRecorder
import javax.inject.Inject
import javax.inject.Singleton

/** [runOneStep]'s full result — the [StepOutcome] plus what [WorkflowExecutor] needs to record it to the Flight Recorder. */
private data class StepExecution(
    val outcome: StepOutcome,
    val adapterType: ExecutionAdapterType?,
    val authorizationKind: String,
)

/**
 * Runs an [ActionPlan] to completion or to its next pause point (an unmet dependency
 * cycle rejection, an [FailurePolicy.ASK_USER] pause, a [FailurePolicy.STOP]/
 * [FailurePolicy.COMPENSATE] halt). Every step — including a compensation step — goes
 * through the exact same [isAuthorizationSufficient] check and the exact same
 * [ActionAdapter] dispatch as any other; nothing here is a second way to execute a
 * capability without its own descriptor's policy applying. Every step's execution is also
 * recorded to [lineageRecorder] — the Flight Recorder / Verifiable Agent Runtime directive
 * (Feature 9) — so a plan's full lineage (what ran, by which adapter, under what
 * authorization, with what outcome) is reconstructable after the fact, not just its final
 * [PlanRunState].
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
    private val lineageRecorder: LineageRecorder,
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
            val execution = runOneStep(step, context)
            state = state.withOutcome(execution.outcome)
            recordLineage(plan.id, step, execution)

            if (execution.outcome.state == StepOutcomeState.AWAITING_USER) return state
            if (execution.outcome.state == StepOutcomeState.SUCCEEDED) continue

            // FAILED or AUTHORIZATION_REFUSED.
            if (shouldCompensate(step)) {
                val compensationStep = plan.steps.first { it.id == step.compensationStepId }
                val compensationExecution = runOneStep(compensationStep, context)
                val recordedOutcome = if (compensationExecution.outcome.state == StepOutcomeState.SUCCEEDED) {
                    compensationExecution.outcome.copy(state = StepOutcomeState.COMPENSATED)
                } else {
                    compensationExecution.outcome
                }
                recordLineage(plan.id, compensationStep, compensationExecution.copy(outcome = recordedOutcome))
                return state.withOutcome(recordedOutcome)
            }
            if (shouldStopPlanAfterFailure(step)) return state
            // FailurePolicy.SKIP: keep going. Anything genuinely depending on this step
            // never becomes eligible (FAILED/AUTHORIZATION_REFUSED aren't dependency-
            // satisfying states), so the loop naturally leaves those steps unattempted
            // rather than running them against a failed prerequisite.
        }
    }

    private suspend fun recordLineage(runId: String, step: PlanStep, execution: StepExecution) {
        lineageRecorder.record(
            LineageEntry(
                runId = runId,
                stepId = step.id,
                action = step.action.name,
                adapterType = execution.adapterType?.name ?: "NONE",
                authorizationKind = execution.authorizationKind,
                outcomeState = execution.outcome.state.name,
                detail = execution.outcome.detail,
                timestampMillis = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun runOneStep(step: PlanStep, context: AdapterExecutionContext): StepExecution {
        val authorizationKind = step.authorizationProof::class.simpleName ?: "Unknown"
        val descriptor = capabilityRegistry.forAction(step.action)
            ?: return StepExecution(
                StepOutcome(step.id, outcomeForFailedStep(step), "No capability registered for ${step.action}"),
                adapterType = null,
                authorizationKind = authorizationKind,
            )

        if (!isAuthorizationSufficient(descriptor.confirmation, step.authorizationProof)) {
            return StepExecution(
                StepOutcome(step.id, outcomeForFailedStep(step, StepOutcomeState.AUTHORIZATION_REFUSED), "Authorization required but not supplied"),
                adapterType = null,
                authorizationKind = authorizationKind,
            )
        }

        val adapterType = adapterTypeFor(descriptor, step.authorizationProof)
        val adapter = adapters[adapterType]
            ?: return StepExecution(
                StepOutcome(step.id, outcomeForFailedStep(step), "No adapter registered for $adapterType"),
                adapterType = adapterType,
                authorizationKind = authorizationKind,
            )

        val result = runCatching { adapter.execute(descriptor, step.parameters, context) }.getOrNull()
        val succeeded = result is NuaRouteResult.ActionTaken && result.succeeded
        val message = (result as? NuaRouteResult.ActionTaken)?.message
        val outcome = StepOutcome(step.id, if (succeeded) StepOutcomeState.SUCCEEDED else outcomeForFailedStep(step), message)
        return StepExecution(outcome, adapterType, authorizationKind)
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
