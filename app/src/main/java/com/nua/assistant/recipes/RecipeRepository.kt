package com.nua.assistant.recipes

import com.nua.assistant.ai.IntentClassifier
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.uaf.ActionPlan
import com.nua.assistant.automation.uaf.AdapterExecutionContext
import com.nua.assistant.automation.uaf.AuthorizationProof
import com.nua.assistant.automation.uaf.CapabilityRegistry
import com.nua.assistant.automation.uaf.ConfirmationPolicy
import com.nua.assistant.automation.uaf.PlanRunState
import com.nua.assistant.automation.uaf.PlanStep
import com.nua.assistant.automation.uaf.StepOutcomeState
import com.nua.assistant.automation.uaf.WorkflowExecutor
import com.nua.assistant.memory.RecipeDao
import com.nua.assistant.memory.RecipeEntity
import com.nua.assistant.memory.RecipeRunDao
import com.nua.assistant.memory.RecipeRunEntity
import com.nua.assistant.trust.TrustRepository
import com.nua.assistant.trust.finalAutoApproveDecision
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What [RecipeRepository.createRecipe] hands back — the persisted row id alongside the
 *  full compile result, so a caller can surface unresolved clauses immediately rather
 *  than making the user open the recipe again to discover gaps. */
data class CreatedRecipe(val recipeId: Long, val compiled: CompiledRecipe)

/**
 * Owns the NUA Recipes lifecycle (Feature 6): compile → persist → simulate → run. This is
 * the first real production caller of [WorkflowExecutor] — every other Universal Action
 * Fabric (Feature 1) caller so far has been its own test suite; recipes reuse that exact
 * runtime rather than inventing a second one, which is the whole point of building
 * Recipes as a compiler *on top of* the fabric instead of a parallel dispatch path (see
 * recipes/RecipeCompiler.kt's own doc comment).
 */
@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val recipeRunDao: RecipeRunDao,
    private val capabilityRegistry: CapabilityRegistry,
    private val workflowExecutor: WorkflowExecutor,
    private val trustRepository: TrustRepository,
    private val intentClassifier: IntentClassifier,
    private val json: Json,
) {
    /** Compiles [description] against the real capability registry and persists the
     *  result, including any unresolved clauses — a recipe is saved exactly as understood,
     *  never silently completed with a guess. A clause the deterministic keyword matcher
     *  misses falls through to [IntentClassifier.classify] (the Sovereign Model Mesh's
     *  "cloud fallback" tier, reused as-is -- see RecipeCompiler.kt's own doc comment on
     *  why this isn't a second LLM prompt). */
    suspend fun createRecipe(name: String, description: String): CreatedRecipe {
        val compiled = compileRecipe(description, capabilityRegistry::forAction, intentClassifier::classify)
        val stepsJson = json.encodeToString(compiled.steps.map { it.toData() })
        val id = recipeDao.insert(
            RecipeEntity(
                name = name,
                description = description,
                stepsJson = stepsJson,
                unresolvedClauseCount = compiled.unresolvedClauses.size,
            ),
        )
        return CreatedRecipe(id, compiled)
    }

    suspend fun listRecipes(): List<RecipeEntity> = recipeDao.getAll()

    /** Live view of every recipe, newest first — what a Recipes screen's list observes
     *  directly rather than re-polling [listRecipes]. */
    fun observeRecipes(): Flow<List<RecipeEntity>> = recipeDao.observeAll()

    suspend fun deleteRecipe(id: Long) = recipeDao.deleteById(id)

    /** Re-compiles [newDescription] and overwrites [recipeId]'s stored steps/unresolved
     *  count in place — the same compile path [createRecipe] uses, so an edited recipe is
     *  held to exactly the same "saved as understood, never completed with a guess"
     *  standard as a brand-new one. Keeps the same id (and so the same run history) rather
     *  than delete-and-recreate, which would orphan [recentRuns] from the recipe they
     *  actually ran against. */
    suspend fun editRecipe(recipeId: Long, name: String, newDescription: String): CompiledRecipe? {
        val existing = recipeDao.getById(recipeId) ?: return null
        val compiled = compileRecipe(newDescription, capabilityRegistry::forAction, intentClassifier::classify)
        val stepsJson = json.encodeToString(compiled.steps.map { it.toData() })
        recipeDao.update(
            existing.copy(
                name = name,
                description = newDescription,
                stepsJson = stepsJson,
                unresolvedClauseCount = compiled.unresolvedClauses.size,
            ),
        )
        return compiled
    }

    suspend fun recentRuns(recipeId: Long, limit: Int = 20): List<RecipeRunEntity> =
        recipeRunDao.recentForRecipe(recipeId, limit)

    /** Zero-side-effect preview of what running this recipe would do right now — see
     *  recipes/RecipeSimulator.kt. Never calls [runRecipe]. */
    suspend fun simulate(recipeId: Long): RecipeSimulation? {
        val recipe = recipeDao.getById(recipeId) ?: return null
        val compiled = compiledFrom(recipe)
        // wouldAutoApprove is suspend (it queries TrustRepository) but simulateRecipe
        // itself must stay a plain, non-suspend pure function -- resolved for every
        // distinct action up front, into a plain set simulateRecipe's callback can check
        // synchronously, rather than passing a suspend call where a pure one belongs.
        val autoApprovedActions = compiled.steps.map { it.action }.distinct().filter { wouldAutoApprove(it) }.toSet()
        return simulateRecipe(compiled, capabilityRegistry::forAction) { action -> action in autoApprovedActions }
    }

    /**
     * Runs a persisted recipe for real, through [WorkflowExecutor] — the same enforcement
     * point every other Universal Action Fabric caller goes through, so a recipe can never
     * bypass authorization: each step's [AuthorizationProof] is resolved fresh, right here,
     * from the real autonomy-grant/contract state ([wouldAutoApprove]), never carried over
     * from a previous run or fabricated by the compiler. A step this doesn't authorize is
     * handed [AuthorizationProof.NotRequired] regardless of whether it actually needs
     * confirmation — [WorkflowExecutor] itself is what refuses it
     * ([com.nua.assistant.automation.uaf.isAuthorizationSufficient]), the same fail-closed
     * check every other caller is subject to, not a second copy of that rule here.
     */
    suspend fun runRecipe(recipeId: Long, context: AdapterExecutionContext): PlanRunState? {
        val recipe = recipeDao.getById(recipeId) ?: return null
        val compiled = compiledFrom(recipe)
        val startedAt = System.currentTimeMillis()
        val authorizedSteps = compiled.steps.map { step -> step.copy(authorizationProof = proofFor(step)) }
        val plan = ActionPlan(id = "recipe-$recipeId-$startedAt", steps = authorizedSteps)
        val result = workflowExecutor.run(plan, context)
        recordRun(recipeId, startedAt, plan, result)
        return result
    }

    private suspend fun proofFor(step: PlanStep): AuthorizationProof {
        val descriptor = capabilityRegistry.forAction(step.action) ?: return AuthorizationProof.NotRequired
        if (descriptor.confirmation != ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE) return AuthorizationProof.NotRequired
        return if (wouldAutoApprove(step.action)) {
            AuthorizationProof.UserConfirmed(System.currentTimeMillis())
        } else {
            AuthorizationProof.NotRequired
        }
    }

    /** True when either the legacy unscoped grant or a live (non-shadow) Contextual
     *  Autonomy Contract currently permits [action] to run without asking — decided by
     *  the same shared pure rule (`trust/AutonomyContract.kt`'s finalAutoApproveDecision)
     *  `ui/NuaViewModel.kt`'s applyPendingEffect uses for the manual chat path, so a
     *  recipe can never get a looser standard than a live request via independent drift. */
    private suspend fun wouldAutoApprove(action: NuaActionType): Boolean {
        val legacyGrantActive = trustRepository.isAutoApproved(action)
        val (contract, decision) = trustRepository.contractDecisionFor(action, recipient = null)
        return finalAutoApproveDecision(legacyGrantActive, contract, decision)
    }

    private suspend fun recordRun(recipeId: Long, startedAt: Long, plan: ActionPlan, result: PlanRunState) {
        val states = plan.steps.map { result.outcomes[it.id]?.state }
        recipeRunDao.insert(
            RecipeRunEntity(
                recipeId = recipeId,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                succeededSteps = states.count { it == StepOutcomeState.SUCCEEDED },
                failedSteps = states.count { it == StepOutcomeState.FAILED || it == StepOutcomeState.AUTHORIZATION_REFUSED },
                awaitingUserSteps = states.count { it == StepOutcomeState.AWAITING_USER },
            ),
        )
    }

    private fun compiledFrom(recipe: RecipeEntity): CompiledRecipe {
        val stepsData = json.decodeFromString<List<RecipeStepData>>(recipe.stepsJson)
        return CompiledRecipe(steps = stepsData.mapNotNull { it.toPlanStep() }, unresolvedClauses = emptyList())
    }
}
