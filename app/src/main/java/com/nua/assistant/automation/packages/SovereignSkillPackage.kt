package com.nua.assistant.automation.packages

import com.nua.assistant.ai.NuaActionType
import kotlinx.serialization.Serializable

@Serializable
data class SovereignSkillPackageManifest(
    val id: String,
    val version: String,
    val name: String,
    val description: String,
    val author: String,
    val declaredActions: Set<String>,
    val recipeIds: List<Long>,
    val dependencies: Set<String> = emptySet(),
)

@Serializable
enum class SkillPackageSource { BUILT_IN, USER_AUTHORED, IMPORTED }

@Serializable
data class SkillPackageProvenance(
    val source: SkillPackageSource,
    val sourceUri: String? = null,
    val sourceCommit: String? = null,
    /** Set only by a verifier owned by NUA; package content cannot self-assert this. */
    val signatureVerified: Boolean = false,
    /** Explicit approval for packages the owner authored locally. */
    val userApproved: Boolean = false,
)

@Serializable
data class InstalledSkillPackage(
    val manifest: SovereignSkillPackageManifest,
    val provenance: SkillPackageProvenance,
    val enabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis(),
)

data class SkillPackageValidation(val valid: Boolean, val errors: List<String>)

private val PACKAGE_ID = Regex("""[a-z0-9][a-z0-9._-]{1,63}""")
private val VERSION = Regex("""[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9._-]+)?""")
const val MAX_SKILL_PACKAGE_DEPENDENCY_DEPTH = 5

fun validateSkillPackageManifest(manifest: SovereignSkillPackageManifest): SkillPackageValidation {
    val errors = mutableListOf<String>()
    if (!PACKAGE_ID.matches(manifest.id)) errors += "id must be 2-64 lowercase URL-safe characters"
    if (!VERSION.matches(manifest.version)) errors += "version must be semantic version X.Y.Z"
    if (manifest.name.isBlank()) errors += "name must not be blank"
    if (manifest.description.isBlank()) errors += "description must not be blank"
    if (manifest.author.isBlank()) errors += "author must not be blank"
    if (manifest.recipeIds.isEmpty()) errors += "at least one recipe is required"
    if (manifest.recipeIds.any { it <= 0 }) errors += "recipe ids must be positive"
    if (manifest.declaredActions.isEmpty()) errors += "declaredActions must be explicit and non-empty"
    manifest.declaredActions.forEach {
        if (runCatching { NuaActionType.valueOf(it) }.isFailure) errors += "unknown action: $it"
    }
    if (manifest.id in manifest.dependencies) errors += "package cannot depend on itself"
    return SkillPackageValidation(errors.isEmpty(), errors)
}

/**
 * Trust is external to the manifest. Imported content cannot make itself trusted by
 * setting a field in its own package file.
 */
fun provenanceAllowsActivation(provenance: SkillPackageProvenance): Boolean = when (provenance.source) {
    SkillPackageSource.BUILT_IN -> true
    SkillPackageSource.USER_AUTHORED -> provenance.userApproved
    SkillPackageSource.IMPORTED ->
        provenance.signatureVerified &&
            !provenance.sourceUri.isNullOrBlank() &&
            !provenance.sourceCommit.isNullOrBlank()
}

fun validateSkillPackageGraph(
    packages: Map<String, SovereignSkillPackageManifest>,
    maxDepth: Int = MAX_SKILL_PACKAGE_DEPENDENCY_DEPTH,
): SkillPackageValidation {
    val errors = mutableListOf<String>()
    packages.forEach { (id, manifest) ->
        manifest.dependencies.forEach { dependency ->
            if (dependency !in packages) errors += "$id has missing dependency $dependency"
        }
    }

    val visiting = mutableSetOf<String>()
    val visited = mutableSetOf<String>()

    fun dfs(id: String, depth: Int) {
        if (depth > maxDepth) {
            errors += "$id exceeds maximum dependency depth $maxDepth"
            return
        }
        if (id in visiting) {
            errors += "dependency cycle includes $id"
            return
        }
        if (id in visited) return
        val manifest = packages[id] ?: return
        visiting += id
        manifest.dependencies.forEach { dfs(it, depth + 1) }
        visiting -= id
        visited += id
    }

    packages.keys.forEach { dfs(it, 1) }

    // Parent packages may compose dependencies only when they explicitly declare every
    // capability those dependencies can exercise. No transitive privilege expansion.
    packages.forEach { (id, manifest) ->
        val declared = manifest.declaredActions
        manifest.dependencies.forEach { dependency ->
            val child = packages[dependency] ?: return@forEach
            val undeclared = child.declaredActions - declared
            if (undeclared.isNotEmpty()) {
                errors += "$id does not declare dependency capabilities: ${undeclared.sorted().joinToString()}"
            }
        }
    }

    return SkillPackageValidation(errors.isEmpty(), errors.distinct())
}

fun recipeActionEnvelopeSatisfied(
    manifest: SovereignSkillPackageManifest,
    actionsByRecipe: Map<Long, Set<NuaActionType>>,
): Boolean =
    manifest.recipeIds.all { id ->
        val actions = actionsByRecipe[id] ?: return@all false
        actions.isNotEmpty() && actions.all { it.name in manifest.declaredActions }
    }
