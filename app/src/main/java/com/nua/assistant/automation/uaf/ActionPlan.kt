package com.nua.assistant.automation.uaf

import com.nua.assistant.ai.NuaActionType

/** What happens to the rest of a plan when one step doesn't succeed. */
enum class FailurePolicy {
    /** No later step runs; the plan ends where it is. */
    STOP,

    /** This step is recorded as failed; independent later steps still run. */
    SKIP,

    /** [PlanStep.compensationStepId] runs, then the plan stops. */
    COMPENSATE,

    /** The run pauses in [StepOutcomeState.AWAITING_USER] rather than failing outright. */
    ASK_USER,
}

/**
 * One node in a plan's dependency DAG. [dependsOn] names other steps' [id]s that must
 * reach [StepOutcomeState.SUCCEEDED] (or [StepOutcomeState.SKIPPED], which counts as
 * "cleared, not blocking") before this one is eligible to run.
 */
data class PlanStep(
    val id: String,
    val action: NuaActionType,
    val parameters: Map<String, String> = emptyMap(),
    val dependsOn: List<String> = emptyList(),
    val authorizationProof: AuthorizationProof = AuthorizationProof.NotRequired,
    val failurePolicy: FailurePolicy = FailurePolicy.STOP,
    val compensationStepId: String? = null,
)

data class ActionPlan(val id: String, val steps: List<PlanStep>)

enum class StepOutcomeState {
    PENDING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    COMPENSATED,
    AUTHORIZATION_REFUSED,
    AWAITING_USER,
}

/** A state a step can be left in without the run engine ever touching it again. */
private val TERMINAL_STATES = setOf(
    StepOutcomeState.SUCCEEDED,
    StepOutcomeState.SKIPPED,
    StepOutcomeState.COMPENSATED,
)

/** A state that satisfies a dependent step's [PlanStep.dependsOn] requirement. */
private val DEPENDENCY_SATISFYING_STATES = setOf(StepOutcomeState.SUCCEEDED, StepOutcomeState.SKIPPED)

data class StepOutcome(val stepId: String, val state: StepOutcomeState, val detail: String? = null)

/**
 * The plan's whole checkpoint — everything [nextRunnableStep] needs to decide what to do
 * next, and everything a persistence layer needs to save/reload to resume after process
 * death. Recomputing "what's next" from this snapshot alone (rather than from any
 * in-memory loop state) is what makes resume safe: the executor can be killed after any
 * single step commits its outcome and restarted with no special resume path.
 */
data class PlanRunState(val planId: String, val outcomes: Map<String, StepOutcome> = emptyMap())

/** Pure: the checkpoint after recording one more step's [outcome] — the only state mutation this whole engine has. */
fun PlanRunState.withOutcome(outcome: StepOutcome): PlanRunState = copy(outcomes = outcomes + (outcome.stepId to outcome))

/** Pure: true once every step in [plan] has reached a [TERMINAL_STATES] outcome, or the run is stuck awaiting the user. */
fun isPlanComplete(plan: ActionPlan, state: PlanRunState): Boolean =
    plan.steps.all { state.outcomes[it.id]?.state in TERMINAL_STATES }

/** Pure: true once any step is paused pending an [FailurePolicy.ASK_USER] resolution. */
fun isPlanAwaitingUser(plan: ActionPlan, state: PlanRunState): Boolean =
    plan.steps.any { state.outcomes[it.id]?.state == StepOutcomeState.AWAITING_USER }

/**
 * Pure: a stable dependency order for [plan]'s steps (Kahn's algorithm), or null if
 * [plan] contains a dependency cycle — a plan that can never be executed. Used only to
 * validate a plan before it's ever run; [nextRunnableStep] re-derives eligibility from
 * [PlanRunState] directly rather than walking this list, so it stays correct across resume.
 */
fun topologicalOrder(plan: ActionPlan): List<PlanStep>? {
    val byId = plan.steps.associateBy { it.id }
    val remaining = plan.steps.toMutableList()
    val resolved = mutableSetOf<String>()
    val ordered = mutableListOf<PlanStep>()
    while (remaining.isNotEmpty()) {
        val ready = remaining.filter { step -> step.dependsOn.all { it in resolved || it !in byId } }
        if (ready.isEmpty()) return null // cycle, or a dependency naming a nonexistent step
        ready.forEach { resolved += it.id }
        ordered += ready
        remaining.removeAll(ready)
    }
    return ordered
}

/**
 * Pure: the next step [WorkflowExecutor] should attempt, or null when nothing is currently
 * eligible — either the plan is complete, stuck awaiting the user, or (a plan validated by
 * [topologicalOrder] beforehand should never hit this) every remaining step is blocked by
 * an unresolved dependency. Steps are offered in [ActionPlan.steps] order among those
 * simultaneously eligible, so execution is deterministic given the same plan and state.
 */
fun nextRunnableStep(plan: ActionPlan, state: PlanRunState): PlanStep? {
    if (isPlanAwaitingUser(plan, state)) return null
    return plan.steps.firstOrNull { step ->
        state.outcomes[step.id] == null &&
            step.dependsOn.all { state.outcomes[it]?.state in DEPENDENCY_SATISFYING_STATES }
    }
}

/**
 * Pure: what a step's own [FailurePolicy] means once it's failed — not a decision the
 * executor re-derives ad hoc, so [WorkflowExecutorTest] can assert it directly without a
 * fake adapter/coroutine in the loop.
 */
fun outcomeForFailedStep(step: PlanStep): StepOutcomeState = when (step.failurePolicy) {
    FailurePolicy.STOP -> StepOutcomeState.FAILED
    FailurePolicy.SKIP -> StepOutcomeState.FAILED
    FailurePolicy.COMPENSATE -> StepOutcomeState.FAILED
    FailurePolicy.ASK_USER -> StepOutcomeState.AWAITING_USER
}

/** Pure: whether a failed step (already recorded via [outcomeForFailedStep]) should halt the whole plan. */
fun shouldStopPlanAfterFailure(step: PlanStep): Boolean = step.failurePolicy == FailurePolicy.STOP

/** Pure: whether a failed step should trigger its named compensation step next. */
fun shouldCompensate(step: PlanStep): Boolean = step.failurePolicy == FailurePolicy.COMPENSATE && step.compensationStepId != null
