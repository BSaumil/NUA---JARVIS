package com.nua.assistant.recipes

import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.KeywordIntentMatcher
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.ConfirmationPolicy
import com.nua.assistant.automation.uaf.FailurePolicy
import com.nua.assistant.automation.uaf.PlanStep

/**
 * One clause of a recipe description that couldn't be resolved to a real capability --
 * surfaced to the user verbatim rather than silently dropped, so a compiled recipe is
 * always a complete, honest account of what NUA understood and what it didn't.
 */
data class UnresolvedClause(val text: String)

data class CompiledRecipe(
    val steps: List<PlanStep>,
    val unresolvedClauses: List<UnresolvedClause>,
)

private val CLAUSE_SEPARATORS = Regex("""\s*(,|;|\band\b|\bthen\b)\s*""", RegexOption.IGNORE_CASE)

/** Pure: splits a free-text recipe description into individual clauses — deterministic
 *  tokenization, no LLM involved (see [compileRecipe]'s own doc comment for why). */
fun splitIntoClauses(description: String): List<String> =
    description.split(CLAUSE_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Compiles a free-text recipe description into a typed, inspectable [CompiledRecipe] —
 * the NUA Recipes directive's "parse → typed IR" stage (Feature 6). Deliberately
 * deterministic this round: each clause is resolved through the exact same
 * [KeywordIntentMatcher] the live router's own local-rules tier already uses (real,
 * tested, zero-network), rather than an LLM round trip — a clause it can't resolve is
 * reported in [CompiledRecipe.unresolvedClauses] verbatim, never silently dropped or
 * guessed at. LLM-assisted parsing for clauses a keyword split misses is a named next
 * slice — the same "local rules first, cloud fallback" shape `ai/mesh/ModelMesh.kt`'s
 * `classifyIntent` already formalizes for the main router; wiring Recipes to reuse it is
 * future work, not attempted here.
 *
 * Every resolved step's [PlanStep.failurePolicy] is [FailurePolicy.ASK_USER] when its
 * capability requires confirmation, [FailurePolicy.SKIP] otherwise — one step failing or
 * needing confirmation never silently blocks the rest of an otherwise-independent recipe,
 * and a step that genuinely needs a human is never quietly skipped. Steps carry no
 * dependencies on each other (a recipe's clauses are independent actions, not a
 * dependency graph) and no `AuthorizationProof` at compile time — that's resolved fresh
 * at run time against the real autonomy grant/contract state (see
 * `recipes/RecipeRepository.kt`), never fabricated here.
 *
 * [descriptorFor] is a plain function rather than
 * [com.nua.assistant.automation.uaf.CapabilityRegistry] itself, so this stays free of any
 * Hilt/Android dependency and fully unit-testable — the same "pass a lookup function, not
 * the whole registry" shape already used elsewhere in this codebase for pure/impure
 * separation.
 */
fun compileRecipe(description: String, descriptorFor: (NuaActionType) -> CapabilityDescriptor?): CompiledRecipe {
    val steps = mutableListOf<PlanStep>()
    val unresolved = mutableListOf<UnresolvedClause>()
    splitIntoClauses(description).forEachIndexed { index, clause ->
        val intent = KeywordIntentMatcher.match(clause)
        val descriptor = intent?.let { descriptorFor(it.action) }
        if (intent == null || descriptor == null) {
            unresolved += UnresolvedClause(clause)
        } else {
            steps += PlanStep(
                id = "step-$index",
                action = intent.action,
                parameters = intent.parameters,
                failurePolicy = failurePolicyFor(descriptor),
            )
        }
    }
    return CompiledRecipe(steps, unresolved)
}

/** Pure: a resolved step's default [FailurePolicy], from its own capability's
 *  confirmation requirement alone — a separately testable rule, since
 *  [com.nua.assistant.automation.KeywordIntentMatcher] happens not to resolve any
 *  confirmation-required action today (every keyword-matchable action is T0/T1,
 *  [ConfirmationPolicy.NONE_REQUIRED]), so [compileRecipe] alone can't exercise the
 *  [FailurePolicy.ASK_USER] branch against the real matcher — this function can. */
fun failurePolicyFor(descriptor: CapabilityDescriptor): FailurePolicy =
    if (descriptor.confirmation == ConfirmationPolicy.CONFIRM_BEFORE_EXECUTE) FailurePolicy.ASK_USER else FailurePolicy.SKIP
