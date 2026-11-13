package com.nua.assistant.automation.operators

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nua.assistant.automation.uaf.AdapterExecutionContext
import com.nua.assistant.automation.uaf.StepOutcomeState
import com.nua.assistant.recipes.RecipeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Persistent autonomous operator tick. It cannot dispatch an action directly: the only
 * execution entry point is RecipeRepository -> WorkflowExecutor, so existing T0-T5,
 * autonomy, biometric/confirmation, adapter and Flight Recorder invariants remain intact.
 */
@HiltWorker
class SovereignOperatorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val store: SovereignOperatorStore,
    private val recipeRepository: RecipeRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(OPERATOR_ID_INPUT) ?: return Result.failure()
        val manifest = store.get(id) ?: return Result.failure()
        val validation = validateOperator(manifest)
        if (!validation.valid) {
            store.recordHealth(id, "INVALID_MANIFEST", validation.errors.joinToString())
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val gate = operatorGate(manifest, store.state(id), now)
        if (!gate.allowed) {
            store.recordHealth(id, "RATE_LIMITED", gate.reason)
            return Result.success()
        }

        val recipeActions = recipeRepository.actionsForRecipe(manifest.recipeId)
        if (recipeActions == null) {
            store.recordHealth(id, "MISSING_RECIPE", "recipe ${manifest.recipeId} no longer exists")
            return Result.success()
        }
        if (!isActionEnvelopeSatisfied(manifest, recipeActions)) {
            store.recordHealth(
                id,
                "CAPABILITY_REFUSED",
                "persisted recipe exceeds the operator's declared allowedActions",
            )
            return Result.success()
        }

        val result = recipeRepository.runRecipe(
            manifest.recipeId,
            AdapterExecutionContext(
                originalUtterance = "Scheduled Sovereign Operator: ${manifest.name}",
                pinnedLanguage = null,
            ),
        )
        if (result == null) {
            store.recordRun(id, now, "MISSING_RECIPE", "recipe disappeared before execution")
            return Result.success()
        }

        val states = result.outcomes.values.map { it.state }
        val outcome = when {
            states.any { it == StepOutcomeState.AUTHORIZATION_REFUSED || it == StepOutcomeState.AWAITING_USER } ->
                "AWAITING_AUTHORIZATION"
            states.any { it == StepOutcomeState.FAILED } -> "FAILED"
            states.isEmpty() -> "NO_STEPS"
            else -> "SUCCEEDED"
        }
        store.recordRun(id, now, outcome)
        return Result.success()
    }
}
