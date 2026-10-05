package com.nua.assistant.recipes

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.uaf.FailurePolicy
import com.nua.assistant.automation.uaf.PlanStep
import kotlinx.serialization.Serializable

/**
 * A [PlanStep] flattened to plain, directly-serializable fields — what a compiled recipe
 * actually persists (`RecipeEntity.stepsJson`). Deliberately not `PlanStep` itself: a
 * [PlanStep] carries an `AuthorizationProof`, a sealed class kotlinx.serialization would
 * need polymorphic config for, and a recipe must never persist authorization anyway — see
 * `recipes/RecipeCompiler.kt`'s own doc comment on why that's resolved fresh at every
 * run, never fabricated or carried over from a previous one.
 */
@Serializable
data class RecipeStepData(
    val id: String,
    val actionName: String,
    val parameters: Map<String, String> = emptyMap(),
    val failurePolicyName: String,
    val dependsOn: List<String> = emptyList(),
)

/** Pure: the at-rest form of a compiled step. */
fun PlanStep.toData(): RecipeStepData = RecipeStepData(
    id = id,
    actionName = action.name,
    parameters = parameters,
    failurePolicyName = failurePolicy.name,
    dependsOn = dependsOn,
)

/**
 * Pure: reconstructs a [PlanStep] from its persisted form with a fresh
 * `AuthorizationProof.NotRequired` — never a security decision by itself; the caller
 * (`recipes/RecipeRepository.kt`'s runRecipe) still resolves real authorization before
 * this step ever reaches `WorkflowExecutor`. Returns null, never a guess, for an
 * `actionName`/`failurePolicyName` that no longer names a real enum constant — e.g. a
 * `NuaActionType` removed since this recipe was compiled.
 */
fun RecipeStepData.toPlanStep(): PlanStep? {
    val action = runCatching { NuaActionType.valueOf(actionName) }.getOrNull() ?: return null
    val failurePolicy = runCatching { FailurePolicy.valueOf(failurePolicyName) }.getOrNull() ?: return null
    return PlanStep(id = id, action = action, parameters = parameters, dependsOn = dependsOn, failurePolicy = failurePolicy)
}
