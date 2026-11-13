package com.nua.assistant.automation.packages

import com.nua.assistant.automation.uaf.AdapterExecutionContext
import com.nua.assistant.automation.uaf.PlanRunState
import com.nua.assistant.recipes.RecipeRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SkillPackageInstallResult {
    data class Installed(val packageId: String) : SkillPackageInstallResult
    data class Refused(val reasons: List<String>) : SkillPackageInstallResult
}

sealed interface SkillPackageRunResult {
    data class Completed(val state: PlanRunState) : SkillPackageRunResult
    data class Refused(val reason: String) : SkillPackageRunResult
}

/**
 * Portable skill packages without portable executable code.
 *
 * A package is a signed/approved declaration over existing NUA recipes and capabilities.
 * It cannot ship a shell script, dynamically load a class, call an ActionAdapter, or
 * bypass WorkflowExecutor. That keeps portability while preserving NUA's action boundary.
 */
@Singleton
class SovereignSkillPackageManager @Inject constructor(
    private val store: SovereignSkillPackageStore,
    private val recipes: RecipeRepository,
) {
    suspend fun install(
        manifest: SovereignSkillPackageManifest,
        provenance: SkillPackageProvenance,
    ): SkillPackageInstallResult {
        val errors = mutableListOf<String>()
        errors += validateSkillPackageManifest(manifest).errors
        if (!provenanceAllowsActivation(provenance)) {
            errors += "package provenance is not trusted for activation"
        }

        val graph = store.list().associate { it.manifest.id to it.manifest }.toMutableMap()
        graph[manifest.id] = manifest
        errors += validateSkillPackageGraph(graph).errors

        val actions = mutableMapOf<Long, Set<com.nua.assistant.ai.NuaActionType>>()
        manifest.recipeIds.forEach { id ->
            val recipeActions = recipes.actionsForRecipe(id)
            if (recipeActions == null) errors += "recipe $id does not exist"
            else actions[id] = recipeActions
        }
        if (errors.isEmpty() && !recipeActionEnvelopeSatisfied(manifest, actions)) {
            errors += "one or more recipes exceed the package's declared capability envelope"
        }

        if (errors.isNotEmpty()) return SkillPackageInstallResult.Refused(errors.distinct())

        store.put(InstalledSkillPackage(manifest = manifest, provenance = provenance))
        return SkillPackageInstallResult.Installed(manifest.id)
    }

    fun list(): List<InstalledSkillPackage> = store.list()

    fun setEnabled(id: String, enabled: Boolean): Boolean = store.setEnabled(id, enabled)

    fun uninstall(id: String) = store.delete(id)

    suspend fun runRecipe(
        packageId: String,
        recipeId: Long,
        context: AdapterExecutionContext,
    ): SkillPackageRunResult {
        val installed = store.get(packageId)
            ?: return SkillPackageRunResult.Refused("package is not installed")
        if (!installed.enabled) return SkillPackageRunResult.Refused("package is disabled")
        if (!provenanceAllowsActivation(installed.provenance)) {
            return SkillPackageRunResult.Refused("package trust is no longer sufficient")
        }
        if (recipeId !in installed.manifest.recipeIds) {
            return SkillPackageRunResult.Refused("recipe is not declared by this package")
        }

        // Revalidate the package's complete recipe envelope, not only the selected
        // recipe. A multi-recipe package must remain safe as a whole, and validating a
        // one-entry map against manifest.recipeIds would incorrectly refuse every run.
        val currentActions = mutableMapOf<Long, Set<com.nua.assistant.ai.NuaActionType>>()
        for (declaredRecipeId in installed.manifest.recipeIds) {
            val actions = recipes.actionsForRecipe(declaredRecipeId)
                ?: return SkillPackageRunResult.Refused("declared recipe $declaredRecipeId no longer exists")
            currentActions[declaredRecipeId] = actions
        }
        if (!recipeActionEnvelopeSatisfied(installed.manifest, currentActions)) {
            return SkillPackageRunResult.Refused("recipe capabilities drifted outside the package declaration")
        }

        val result = recipes.runRecipe(recipeId, context)
            ?: return SkillPackageRunResult.Refused("recipe disappeared before execution")
        return SkillPackageRunResult.Completed(result)
    }
}
