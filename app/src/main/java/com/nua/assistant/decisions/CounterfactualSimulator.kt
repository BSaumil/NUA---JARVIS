package com.nua.assistant.decisions

import com.nua.assistant.ai.ClaudeResult
import com.nua.assistant.ai.mesh.InferenceTaskType
import com.nua.assistant.ai.mesh.ModelMesh
import com.nua.assistant.ai.mesh.PrivacySensitivity
import com.nua.assistant.ai.mesh.TaskContract
import com.nua.assistant.memory.DecisionEntity
import javax.inject.Inject
import javax.inject.Singleton

sealed class CounterfactualResult {
    data class Speculation(val text: String) : CounterfactualResult()
    data class Ineligible(val reason: String) : CounterfactualResult()
}

fun counterfactualIneligibilityReasonFor(decision: DecisionEntity): String? = when {
    decision.outcome.isNullOrBlank() ->
        "No outcome recorded yet for this decision — record what actually happened before exploring what else might have."
    decision.options.isNullOrBlank() ->
        "No alternatives were recorded for this decision — there's nothing else to speculate about."
    else -> null
}

private const val COUNTERFACTUAL_SYSTEM_PROMPT = """You are helping someone reflect on a past personal decision after they have told you what actually happened. Speculate honestly about what might plausibly have happened under alternatives they actually considered, grounded only in the facts, unknowns and constraints they recorded. Never invent a fact, outcome, or alternative. Every speculative claim must be explicitly framed as uncertainty. If the information is too thin to say anything useful, say so plainly."""

/**
 * Local-only reflection over the Decision Journal.
 *
 * Decision content is privacy-sensitive, so this deliberately uses HIGH + requiresOffline
 * and never records or permits cloud egress. If Gemini Nano/AICore is unavailable, the
 * simulator returns Ineligible rather than silently sending the decision to a cloud model.
 */
@Singleton
class CounterfactualSimulator @Inject constructor(
    private val modelMesh: ModelMesh,
) {
    suspend fun simulate(decision: DecisionEntity): CounterfactualResult {
        val ineligibleReason = counterfactualIneligibilityReasonFor(decision)
        if (ineligibleReason != null) return CounterfactualResult.Ineligible(ineligibleReason)

        val result = modelMesh.complete(
            contract = TaskContract(
                task = InferenceTaskType.REASONING,
                privacySensitivity = PrivacySensitivity.HIGH,
                requiresOffline = true,
                allowCloud = false,
            ),
            userPrompt = buildPrompt(decision),
            system = COUNTERFACTUAL_SYSTEM_PROMPT,
            maxTokens = 500,
        )
        return when (result) {
            is ClaudeResult.Success -> CounterfactualResult.Speculation(result.text)
            is ClaudeResult.Failure -> CounterfactualResult.Ineligible(
                "No private on-device model is currently available to reason about this decision.",
            )
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
