package com.nua.assistant.recipes

import com.nua.assistant.ai.ClassifiedIntent
import com.nua.assistant.ai.NuaActionType
import com.nua.assistant.automation.KeywordIntentMatcher
import com.nua.assistant.automation.uaf.CapabilityDescriptor
import com.nua.assistant.automation.uaf.ConfirmationPolicy
import com.nua.assistant.automation.uaf.FailurePolicy
import com.nua.assistant.automation.uaf.PlanStep
import com.nua.assistant.security.UserUtterance

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
    splitIntoClausesDetailed(description).map { it.text }

/** One clause plus the literal separator word that introduced it ("then", "and", ",",
 *  ";"), lowercased, or null for the description's first clause. [compileRecipe] uses
 *  [precedingSeparator] to decide [PlanStep.dependsOn]: only "then" expresses real
 *  sequence ("do X, then Y" means Y happens *after* X); "," ";" "and" just list
 *  independent actions in one sentence, same as before this field existed. */
private data class DetailedClause(val text: String, val precedingSeparator: String?)

/** Pure: like [splitIntoClauses], but keeps the separator that introduced each clause —
 *  `String.split(Regex)` (what [splitIntoClauses] used before this existed) discards the
 *  delimiter entirely, which is fine for the clause text itself but loses exactly the
 *  "and" vs "then" distinction [compileRecipe] now needs. */
private fun splitIntoClausesDetailed(description: String): List<DetailedClause> {
    val clauses = mutableListOf<DetailedClause>()
    var cursor = 0
    var precedingSeparator: String? = null
    for (match in CLAUSE_SEPARATORS.findAll(description)) {
        val text = description.substring(cursor, match.range.first).trim()
        if (text.isNotEmpty()) clauses += DetailedClause(text, precedingSeparator)
        precedingSeparator = match.groupValues[1].lowercase()
        cursor = match.range.last + 1
    }
    val tail = description.substring(cursor).trim()
    if (tail.isNotEmpty()) clauses += DetailedClause(tail, precedingSeparator)
    return clauses
}

/**
 * Compiles a free-text recipe description into a typed, inspectable [CompiledRecipe] —
 * the NUA Recipes directive's "parse → typed IR" stage (Feature 6). Each clause is
 * resolved through the exact same [KeywordIntentMatcher] the live router's own
 * local-rules tier already uses (real, tested, zero-network) first; a clause it misses
 * falls through to [llmFallback] — [com.nua.assistant.ai.IntentClassifier.classify],
 * already routed through the Sovereign Model Mesh (`ai/mesh/ModelMesh.kt`), reused as-is
 * rather than inventing a second "text → action+parameters" prompt for the same shape —
 * the same "local rules first, cloud fallback" order `ModelMesh.classifyIntent` already
 * formalizes for the main router. [llmFallback] defaults to a no-op so every existing
 * caller (and every test that doesn't care about the fallback) stays exactly
 * deterministic; a clause neither resolves is reported in
 * [CompiledRecipe.unresolvedClauses] verbatim, never silently dropped or guessed at.
 *
 * Every resolved step's [PlanStep.failurePolicy] is [FailurePolicy.ASK_USER] when its
 * capability requires confirmation, [FailurePolicy.SKIP] otherwise — one step failing or
 * needing confirmation never silently blocks the rest of an otherwise-independent recipe,
 * and a step that genuinely needs a human is never quietly skipped. [PlanStep.dependsOn]
 * is set only when a clause was literally introduced by "then" — "do X, then Y" makes Y
 * depend on X's step; "X and Y"/"X, Y" stay independent, exactly as every clause already
 * was before dependency tracking existed. A clause that doesn't resolve to a step can't
 * be depended on; a "then" immediately after one is simply ignored, the same as a "then"
 * at the very start of the description. No step ever carries an `AuthorizationProof` at
 * compile time — that's resolved fresh at run time against the real autonomy
 * grant/contract state (see `recipes/RecipeRepository.kt`), never fabricated here.
 *
 * [descriptorFor] is a plain function rather than
 * [com.nua.assistant.automation.uaf.CapabilityRegistry] itself, so this stays free of any
 * Hilt/Android dependency and fully unit-testable — the same "pass a lookup function, not
 * the whole registry" shape already used elsewhere in this codebase for pure/impure
 * separation.
 */
suspend fun compileRecipe(
    description: String,
    descriptorFor: (NuaActionType) -> CapabilityDescriptor?,
    llmFallback: suspend (UserUtterance) -> ClassifiedIntent? = { null },
): CompiledRecipe {
    val steps = mutableListOf<PlanStep>()
    val unresolved = mutableListOf<UnresolvedClause>()
    var previousStepId: String? = null
    splitIntoClausesDetailed(description).forEachIndexed { index, clause ->
        // A recipe's description is the user's own typed/spoken text, written at the
        // moment they create the recipe -- the same provenance a live chat turn has, so
        // wrapping an unresolved clause in UserUtterance here is the same class of call
        // site NuaViewModel's own chat turn already is, never a document/vision/
        // notification body. See tools/injection_boundary_audit.py's allowlist.
        val intent = KeywordIntentMatcher.match(clause.text) ?: llmFallback(UserUtterance(clause.text))
        val descriptor = intent?.let { if (it.action == NuaActionType.CHAT) null else descriptorFor(it.action) }
        val dependsOn = if (clause.precedingSeparator == "then" && previousStepId != null) listOf(previousStepId!!) else emptyList()
        if (intent == null || descriptor == null) {
            unresolved += UnresolvedClause(clause.text)
        } else {
            val stepId = "step-$index"
            steps += PlanStep(
                id = stepId,
                action = intent.action,
                parameters = intent.parameters,
                dependsOn = dependsOn,
                failurePolicy = failurePolicyFor(descriptor),
            )
            previousStepId = stepId
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
