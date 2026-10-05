package com.nua.assistant.decisions

import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.mesh.InferenceTaskType
import com.nua.assistant.ai.mesh.ModelMesh
import com.nua.assistant.ai.mesh.PrivacySensitivity
import com.nua.assistant.ai.mesh.TaskContract
import com.nua.assistant.memory.DecisionEntity
import com.nua.assistant.security.egress.DataCategory
import com.nua.assistant.security.egress.DataEgressGateway
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counterfactual Decision Simulator — Feature 4 of the 5-Year Standalone Master
 * Directive. The directive notes this depends on the Temporal World Model (Feature 3)
 * existing first; investigated honestly, the real dependency only holds for a richer
 * version that reasons over a graph of commitments and interval-valid relationships —
 * neither has any persisted data yet (`docs/TEMPORAL_WORLD_MODEL_RFC.md` §6, gated on
 * the version-21 migration). What a counterfactual can honestly run against *today* is
 * `memory/DecisionEntity` — the Decision Journal already captures exactly the structured
 * inputs a counterfactual needs (options actually considered, facts/unknowns/constraints
 * named at the time, the outcome that actually happened) — so this implements the real
 * feature against that real data, rather than waiting on a dependency that turned out to
 * be narrower than the directive's note implied.
 *
 * Deliberately never claims to know what actually would have happened — [simulate]'s
 * whole output is explicitly framed as speculation, bounded to the facts/constraints the
 * decision itself recorded, never fabricating information the user never gave it. This is
 * the same "named honestly rather than glossed over" standard [DecisionEntity.unknowns]
 * already holds the Decision Journal to.
 */
sealed class CounterfactualResult {
    data class Speculation(val text: String) : CounterfactualResult()

    /** Never ran a model call — either the decision doesn't have enough structure to
     *  speculate against, or the call itself failed. Distinguishing the two in [reason]'s
     *  text, not a separate state: either way, the caller has nothing to show but why. */
    data class Ineligible(val reason: String) : CounterfactualResult()
}

/**
 * Pure: why [decision] can't be simulated, or null when it can. Requires both a recorded
 * outcome (there's nothing to contrast a counterfactual against before one exists — the
 * whole point is "here's what happened; what about the alternative?") and at least one
 * real alternative in [DecisionEntity.options] (never fabricate alternatives nobody
 * actually considered).
 */
fun counterfactualIneligibilityReasonFor(decision: DecisionEntity): String? = when {
    decision.outcome.isNullOrBlank() ->
        "No outcome recorded yet for this decision — record what actually happened before exploring what else might have."
    decision.options.isNullOrBlank() ->
        "No alternatives were recorded for this decision — there's nothing else to speculate about."
    else -> null
}

private const val COUNTERFACTUAL_SYSTEM_PROMPT = """You are helping someone reflect on a past personal decision, after they've told you \
what actually happened. Speculate honestly about what might plausibly have happened under \
an alternative they actually considered, grounded only in the facts/constraints they \
recorded — never invent a fact, outcome, or alternative they didn't give you. Every \
speculative claim must read as speculation (e.g. "might have," "could plausibly"), never \
as a stated fact about an alternate timeline nobody can actually know. If the recorded \
information is too thin to say anything useful, say that plainly instead of guessing."""

@Singleton
class CounterfactualSimulator @Inject constructor(
    private val modelMesh: ModelMesh,
) {
    suspend fun simulate(decision: DecisionEntity): CounterfactualResult {
        val ineligibleReason = counterfactualIneligibilityReasonFor(decision)
        if (ineligibleReason != null) return CounterfactualResult.Ineligible(ineligibleReason)

        DataEgressGateway.recordEgress(DataCategory.DECISION_CONTENT, itemCount = 1, purpose = "counterfactual decision simulation")
        val contract = TaskContract(task = InferenceTaskType.REASONING, privacySensitivity = PrivacySensitivity.HIGH, requiresFrontierCapability = true)
        val result = modelMesh.complete(
            contract = contract,
            userPrompt = buildPrompt(decision),
            system = COUNTERFACTUAL_SYSTEM_PROMPT,
            maxTokens = 500,
        )
        return when (result) {
            is ClaudeResult.Success -> CounterfactualResult.Speculation(result.text)
            is ClaudeResult.Failure -> CounterfactualResult.Ineligible("Couldn't reach a model to reason about this right now.")
        }
    }

    private fun buildPrompt(decision: DecisionEntity): String = buildString {
        appendLine("Decision made: ${decision.decision}")
        decision.reasoning?.let { appendLine("Reasoning at the time: $it") }
        decision.facts?.let { appendLine("Known facts at the time: $it") }
        decision.unknowns?.let { appendLine("What wasn't known at the time: $it") }
        decision.constraints?.let { appendLine("Constraints: $it") }
        appendLine("Alternatives actually considered: ${decision.options}")
        appendLine("What actually happened: ${decision.outcome}")
        appendLine()
        append("For each alternative above that wasn't chosen, briefly speculate what might plausibly have happened instead.")
    }
}
