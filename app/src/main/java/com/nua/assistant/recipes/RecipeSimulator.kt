package com.nua.assistant.recipes

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.ConfirmationPolicy
import com.nua.assistant.automation.uaf.PlanStep

/** What running a compiled step would do right now, without ever actually doing it. */
enum class SimulatedStepStatus {
    /** No confirmation required, or a live autonomy grant already covers it — would run immediately. */
    WOULD_EXECUTE,

    /** Confirmation is required and nothing currently authorizes it — would pause, never silently skip. */
    WOULD_AWAIT_USER,

    /** No capability is registered for this step's action — would fail before reaching any adapter. */
    UNRESOLVED,
}

data class SimulatedStep(val step: PlanStep, val status: SimulatedStepStatus, val purpose: String?)

data class RecipeSimulation(val steps: List<SimulatedStep>, val unresolvedClauses: List<UnresolvedClause>)

/**
 * Pure, zero-side-effect preview of what running [recipe] would do right now — the NUA
 * Recipes directive's "simulation" stage. Never touches
 * [com.nua.assistant.automation.uaf.WorkflowExecutor], never calls an
 * [com.nua.assistant.automation.uaf.ActionAdapter] — there is no code path here that can
 * perform a real side effect. [wouldAutoApprove] is the caller's own already-computed
 * answer (from [com.nua.assistant.trust.TrustRepository]'s autonomy grant/contract
 * check — the same one `recipes/RecipeRepository.kt` consults for real at run time),
 * passed in rather than queried here, so this function stays free of any database access
 * and fully deterministic given its inputs.
 */
fun simulateRecipe(
    recipe: CompiledRecipe,
    descriptorFor: (NuaActionType) -> CapabilityDescriptor?,
    wouldAutoApprove: (NuaActionType) -> Boolean,
): RecipeSimulation {
    val steps = recipe.steps.map { step ->
        val descriptor = descriptorFor(step.action)
        val status = when {
            descriptor == null -> SimulatedStepStatus.UNRESOLVED
            descriptor.confirmation == ConfirmationPolicy.NONE_REQUIRED -> SimulatedStepStatus.WOULD_EXECUTE
            wouldAutoApprove(step.action) -> SimulatedStepStatus.WOULD_EXECUTE
            else -> SimulatedStepStatus.WOULD_AWAIT_USER
        }
        SimulatedStep(step, status, descriptor?.purpose)
    }
    return RecipeSimulation(steps, recipe.unresolvedClauses)
}
